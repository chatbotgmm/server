import {test} from 'node:test';
import assert from 'node:assert/strict';
import {features,plan,apply,client,validateIds,validateState} from '../scripts/manual/menu-reopen/reopen.mjs';
function fixture(){
 const states=new Map(['a','b','untargeted'].map(id=>[id,{version:'menu-access-v1',revision:0,features:Object.fromEntries(features.map((f,i)=>[f,{mode:i===0?'force_lock':'auto',enabled:false}]))}]));
 const writes=[];const api=async(path,body)=>{
  if(!body)return structuredClone(states.get(new URLSearchParams(path.slice(1)).get('playerId')));
  const s=states.get(body.targetPlayerId);assert.equal(s.revision,body.expectedRevision);assert.equal(body.mode,'force_unlock');
  s.features[body.featureId]={mode:body.mode,enabled:true};s.revision++;writes.push(body);
  return {targetPlayerId:body.targetPlayerId,featureId:body.featureId,mode:body.mode,revision:s.revision};
 };return {states,writes,api};
}
test('plan is read only; apply reopens auto and force_lock for all eight, only listed accounts',async()=>{
 const f=fixture(),p=await plan(f.api,['a','b']);assert.equal(f.writes.length,0);
 assert.deepEqual(await apply(f.api,p),{accounts:2,changed:16,verified:true});
 assert.equal(f.states.get('untargeted').revision,0);assert.equal(new Set(f.writes.map(x=>x.requestId)).size,16);
});
test('already open menus are no-ops and rerun fresh plan is idempotent',async()=>{
 const f=fixture();await apply(f.api,await plan(f.api,['a']));
 assert.deepEqual(await apply(f.api,await plan(f.api,['a'])),{accounts:1,changed:0,verified:true});assert.equal(f.writes.length,8);
});
test('entire set preflight detects stale last account before any write',async()=>{
 const f=fixture(),p=await plan(f.api,['a','b']);f.states.get('b').revision++;
 await assert.rejects(apply(f.api,p),/STALE_PLAN/);assert.equal(f.writes.length,0);
});
test('changed mode at same revision also refuses overwrite',async()=>{
 const f=fixture(),p=await plan(f.api,['a']);f.states.get('a').features.shop.mode='force_lock';
 await assert.rejects(apply(f.api,p),/STALE_PLAN/);assert.equal(f.writes.length,0);
});
test('conflict or uncertain mutation stops without retries',async()=>{
 const f=fixture(),p=await plan(f.api,['a']);let calls=0;
 await assert.rejects(apply(async(path,body)=>{if(body){calls++;throw Error('HTTP_409_STOP_AND_REVIEW');}return f.api(path);},p),/HTTP_409/);assert.equal(calls,1);
});
test('final verification rejects a silently still locked feature',async()=>{
 const f=fixture(),p=await plan(f.api,['a']);
 await assert.rejects(apply(async(path,body)=>{const r=await f.api(path,body);if(!body&&r.revision===8)r.features.rebirth.enabled=false;return r;},p),/VERIFY_FAILED/);
});
test('invalid lists, unknown server states, malformed plans rejected',async()=>{
 for(const ids of [[],['a','a'],[''],[1]])assert.throws(()=>validateIds(ids),/INVALID/);
 assert.throws(()=>validateState({}),/INVALID/);
 const f=fixture(),p=await plan(f.api,['a']);delete p.accounts[0].requests.shop;await assert.rejects(apply(f.api,p),/INVALID_PLAN/);assert.equal(f.writes.length,0);
});
test('HTTPS origin only, no redirect credentials or unsafe URL',()=>{
 for(const u of ['http://example.test','https://u:p@example.test','https://example.test/path','https://example.test?secret=x'])assert.throws(()=>client(u,'s'),/HTTPS/);
 assert.throws(()=>client('https://example.test',''),/ADMIN/);
});
test('authenticated API uses no redirects and does not expose error bodies',async()=>{
 let options;const api=client('https://example.test','s',async(url,o)=>{options=o;return {ok:false,status:403,json:()=>({secret:'DO_NOT_PRINT'})};});
 await assert.rejects(api('?playerId=a'),/^Error: HTTP_403_STOP_AND_REVIEW$/);
 assert.equal(options.redirect,'error');assert.equal(options.headers['x-gmm'],'1');assert.equal(options.headers['x-gmm-s'],'s');
});
test('network errors are sanitized and marked uncertain without retry',async()=>{
 let n=0;const api=client('https://example.test','s',async()=>{n++;throw Error('secret');});
 await assert.rejects(api('',{}),/^Error: NETWORK_OUTCOME_UNKNOWN_STOP_AND_REVIEW$/);assert.equal(n,1);
});
test('unexpected receipt halts subsequent changes',async()=>{
 const f=fixture(),p=await plan(f.api,['a']);let n=0;
 await assert.rejects(apply(async(path,body)=>{if(body){n++;return {revision:1};}return f.api(path);},p),/INVALID_RECEIPT/);assert.equal(n,1);
});
