import {test} from 'node:test';
import assert from 'node:assert/strict';
import {realpathSync} from 'node:fs';
import {localContext,localPlan,localApply} from '../scripts/manual/menu-reopen/local-reopen.mjs';
import {features} from '../scripts/manual/menu-reopen/reopen.mjs';
function fixture(admins=[{id:'admin'}]){
 const states=new Map(['a','b'].map(id=>[id,{version:'menu-access-v1',revision:0,features:Object.fromEntries(features.map(f=>[f,{mode:'auto',enabled:false}]))}]));const writes=[];
 const ids=[{id:'a'},{id:'b'}];
 const db={$queryRawUnsafe:async()=>[{name:'main',file:import.meta.filename}],player:{findMany:async q=>q.where?admins:ids},appMeta:{findUnique:async()=>null}};
 const service={read:async id=>structuredClone(states.get(id)),change:async(actor,b)=>{assert.equal(actor,'admin');const s=states.get(b.targetPlayerId);assert.equal(s.revision,b.expectedRevision);s.revision++;s.features[b.featureId]={enabled:true,mode:b.mode};writes.push(b);return {...b,revision:s.revision};}};
 const opt={serviceDir:'/service',databaseFile:realpathSync(import.meta.filename)};
 return {db,service,opt,writes,ids};
}
test('local dry run reads all players; records existing actor and operator request provenance; apply uses existing service',async()=>{
 const f=fixture(),c=await localContext(f.db,f.service,f.opt),p=await localPlan(f.db,c);
 assert.equal(f.writes.length,0);assert.equal(p.accountCount,2);assert.equal(p.actorPlayerId,'admin');assert.equal(p.appMetaBefore.length,2);
 assert.deepEqual(await localApply(c,p,2),{accounts:2,changed:16,verified:true});assert.ok(f.writes.every(x=>x.requestId.startsWith('ec2-shell:')));
});
test('zero or multiple existing admins refuse guessing; explicit eligible selection works',async()=>{
 for(const a of [[],[{id:'admin'},{id:'second'}]]){const f=fixture(a);await assert.rejects(localContext(f.db,f.service,f.opt),/EXACTLY_ONE/);}
 const f=fixture([{id:'admin'},{id:'second'}]);assert.equal((await localContext(f.db,f.service,{...f.opt,adminPlayerId:'admin'})).actor,'admin');
 await assert.rejects(localContext(f.db,f.service,{...f.opt,adminPlayerId:'missing'}),/ADMIN_NOT_ELIGIBLE/);
});
test('wrong connected database stops before account queries',async()=>{
 const f=fixture();await assert.rejects(localContext(f.db,f.service,{...f.opt,databaseFile:'/wrong'}),/DATABASE_PATH_MISMATCH/);
});
test('actor, row count, directory, database, and account set are bound to saved plan',async()=>{
 const f=fixture(),c=await localContext(f.db,f.service,f.opt),p=await localPlan(f.db,c);
 for(const patch of [{actorPlayerId:'other'},{accountCount:3},{serviceDir:'/wrong'},{databaseFile:'/wrong'}])await assert.rejects(localApply(c,{...p,...patch},2),/LOCAL_PLAN_SCOPE_CHANGED/);
 await assert.rejects(localApply(c,p,1),/LOCAL_PLAN_SCOPE_CHANGED/);assert.equal(f.writes.length,0);
 f.ids.push({id:'new'});await assert.rejects(localApply(c,p,2),/ACCOUNT_SET_CHANGED/);assert.equal(f.writes.length,0);
});
test('inconsistent AppMeta snapshot refuses creating recovery plan',async()=>{
 const f=fixture(),c=await localContext(f.db,f.service,f.opt);f.db.appMeta.findUnique=async()=>({value:JSON.stringify({revision:1,overrides:{}})});
 await assert.rejects(localPlan(f.db,c),/PLAN_CHANGED_DURING_SNAPSHOT/);
});
test('unmarked request provenance refuses mutation',async()=>{
 const f=fixture(),c=await localContext(f.db,f.service,f.opt),p=await localPlan(f.db,c);p.accounts[0].requests.shop='not-operator';
 await assert.rejects(localApply(c,p,2),/LOCAL_PLAN_PROVENANCE_INVALID/);assert.equal(f.writes.length,0);
});
