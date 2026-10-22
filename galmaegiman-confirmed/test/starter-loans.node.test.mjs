import {test} from 'node:test';
import assert from 'node:assert/strict';
import {starterSelection,starterLoanState,claimStarterLoans,STARTER_LOAN_OPTIONS,STARTER_LOAN_DURATION_MS,activeLoanWhere} from '../src/starter-loans.ts';
const content={map:new Map(STARTER_LOAN_OPTIONS.map(([a,id])=>[id,{name:id,rarity:id.startsWith('T')?'TRANSCEND':'LIMITED'}]))};
const rules={unitDef:id=>({attr:STARTER_LOAN_OPTIONS.find(x=>x[1]===id)?.[0],role:'UTILITY'}),attrName:a=>a};
function fixture(){
  let next=1;const grants=new Map(),owned=[],audits=[];
  const tx={starterLoanLevel:{findMany:async()=>[],create:async({data})=>data},starterLoanGrant:{findUnique:async({where})=>grants.get(where.playerId),create:async({data})=>{assert(!grants.has(data.playerId));const g={id:'receipt-'+data.playerId,...data};grants.set(data.playerId,g);return g;}},ownedCharacter:{create:async({data})=>{const u={id:next++,...data};owned.push(u);return u;}},auditEvent:{create:async({data})=>audits.push(data)}};
  const p={id:'owner-a',gameTutorial:9,starterLoanEligibleAt:new Date(0),adminMode:false};
  return {tx,p,grants,owned,audits};
}
test('ten distinct attributes and no permanent starter duplicate',()=>{assert.equal(STARTER_LOAN_OPTIONS.length,10);assert.equal(new Set(STARTER_LOAN_OPTIONS.map(x=>x[0])).size,10);assert(!STARTER_LOAN_OPTIONS.some(x=>x[1]==='D1'));});
test('exactly two valid distinct choices, order canonical',()=>{assert.deepEqual(starterSelection(['T1','D2']),['D2','T1']);for(const v of [null,[],['D2'],['D2','D2'],['D1','D2'],['D2','T1','T2'],{ids:['D2','T1']}])assert.throws(()=>starterSelection(v));});
test('grant uses exact owned IDs and no collection/reward service, same-selection replay immutable',async()=>{const {tx,p,owned,audits}=fixture();const now=new Date(1_000);const a=await claimStarterLoans(tx,p,content,rules,['T1','D2'],now);const b=await claimStarterLoans(tx,p,content,rules,['D2','T1'],new Date(900_000));assert.deepEqual(a.receipt,b.receipt);assert.equal(owned.length,2);assert.equal(audits.length,1);assert.equal(a.receipt.expiresAt,1_000+15*24*60*60*1_000);assert.deepEqual(a.receipt.ownedIds,owned.map(x=>x.id));assert(owned.every(x=>x.locked&&x.obtainedVia==='TEMPORARY_STARTER'));await assert.rejects(()=>claimStarterLoans(tx,p,content,rules,['T2','D2'],now));});
test('owner isolation, no entitlement from client completion alone',async()=>{const {tx,p,owned}=fixture();await claimStarterLoans(tx,p,content,rules,['T1','D2'],new Date(0));const other={...p,id:'owner-b',starterLoanEligibleAt:null};assert.equal((await starterLoanState(tx,other,content,rules,new Date(1))).state,'ineligible');await assert.rejects(()=>claimStarterLoans(tx,other,content,rules,['D3','D4'],new Date(1)));assert.equal(owned.length,2);});
test('expiry exact boundary, replay after expiry never renews',async()=>{const {tx,p}=fixture();const first=await claimStarterLoans(tx,p,content,rules,['T1','D2'],new Date(0));assert.equal((await starterLoanState(tx,p,content,rules,new Date(STARTER_LOAN_DURATION_MS-1))).state,'claimed');assert.equal((await starterLoanState(tx,p,content,rules,new Date(STARTER_LOAN_DURATION_MS))).state,'expired');const replay=await claimStarterLoans(tx,p,content,rules,['T1','D2'],new Date(STARTER_LOAN_DURATION_MS+1));assert.equal(replay.state,'expired');assert.deepEqual(replay.receipt,first.receipt);assert.deepEqual(activeLoanWhere(new Date(123)),{OR:[{loanExpiresAt:null},{loanExpiresAt:{gt:new Date(123)}}]});});
test('admin mode and incomplete tutorial cannot claim',async()=>{for(const change of [{adminMode:true},{gameTutorial:5}]){const {tx,p,owned}=fixture();await assert.rejects(()=>claimStarterLoans(tx,{...p,...change},content,rules,['T1','D2'],new Date(0)));assert.equal(owned.length,0);}});

test('expiry cleanup removes exact owned refs but keeps permanent copy and captured battle',async()=>{
  const {pruneExpiredLoanReferences}=await import('../src/starter-loans.ts');
  const captured={uid:'o1',id:'D2',temporary:true};
  let saved={data:JSON.stringify({team:['o1','o2','o3'],units:{o1:{xp:5},o2:{xp:10}},hp:{o1:{f:.5},o2:{f:1}}})};
  let lineup={teamJson:'["o1","o2","D2","D3"]'};const p={id:'a',battleTeam:'D2,D3'};
  const tx={ownedCharacter:{findMany:async({where})=>where.loanExpiresAt?[{id:1,characterId:'D2'},{id:3,characterId:'D3'}]:[{characterId:'D2'}]},storySave:{findUnique:async()=>saved,update:async({data})=>saved=data},player:{update:async()=>{}},realtimeLineup:{findUnique:async()=>lineup,update:async({data})=>lineup=data}};
  await pruneExpiredLoanReferences(tx,p,new Date(123));
  assert.deepEqual(JSON.parse(saved.data).team,['o2']);assert.deepEqual(Object.keys(JSON.parse(saved.data).units),['o2']);assert.equal(p.battleTeam,'D2');assert.deepEqual(JSON.parse(lineup.teamJson),['o2','D2']);assert.deepEqual(captured,{uid:'o1',id:'D2',temporary:true});
});

test('candidate attributes and upper tiers match pinned real content',async()=>{
  const {readFileSync}=await import('node:fs');
  const units=JSON.parse(readFileSync(new URL('../data/units.json',import.meta.url),'utf8')).units;
  const yaml=readFileSync(new URL('../data/battle.yaml',import.meta.url),'utf8');
  for(const [attr,id] of STARTER_LOAN_OPTIONS){const u=units.find(u=>u.id===id);assert(u);assert(['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(u.tier));assert.match(yaml,new RegExp(`  ${id}: \\{ attr: ${attr},`));}
});
test('source guards cover enrollment, completion, destruction and expedition independently of locks',async()=>{
  const {readFileSync}=await import('node:fs');const game=readFileSync(new URL('../src/game.ts',import.meta.url),'utf8'),progression=readFileSync(new URL('../src/progression.ts',import.meta.url),'utf8');
  assert.match(game,/starterLoanEnrolled:!old/);assert.match(game,/next===9&&p.starterLoanEnrolled&&!p.starterLoanEligibleAt/);
  assert.match(game,/cur.gameTutorial>=1&&cur.gameTutorial<=2&&next>=3/);
  assert.match(game,/locked:!locked,loanExpiresAt:null/);assert.match(game,/status:'AVAILABLE',loanExpiresAt:null,characterId:\{in:this.topIds\}/);
  for(const s of [game,progression])assert(!s.includes("status:'AVAILABLE',locked:false}"));
  assert.match(game,/tx.starterLoanGrant,tx.progressionReceipt/);
  assert.match(game,/const now=this.clock\(\);\s+await pruneExpiredLoanReferences/);
});

test('serialized concurrent retries have one receipt; conflicting concurrent choices fail',async()=>{
  const {tx,p,owned}=fixture();let tail=Promise.resolve();
  const run=ids=>{const task=tail.then(()=>claimStarterLoans(tx,p,content,rules,ids,new Date(100)));tail=task.catch(()=>{});return task;};
  const results=await Promise.allSettled([run(['T1','D2']),run(['D2','T1']),run(['D3','D4'])]);
  assert.equal(results[0].status,'fulfilled');assert.equal(results[1].status,'fulfilled');assert.equal(results[2].status,'rejected');assert.equal(owned.length,2);assert.deepEqual(results[0].value.receipt,results[1].value.receipt);
});
test('endpoint derives owner from authenticated session, not submitted player ID',async()=>{
  const {readFileSync}=await import('node:fs');const my=readFileSync(new URL('../src/my.ts',import.meta.url),'utf8');
  const get=my.slice(my.indexOf("app.get('/my/api/starter-loans'"),my.indexOf("act<{nickname?:unknown}>"));
  assert.match(get,/if\(!guard\(req,reply\)\)return/);assert.match(get,/const id=session\(req\);if\(!id\)return reply.code\(401\)/);assert.match(get,/webClaimStarterLoans\(id,b.ids\)/);assert(!get.includes('playerId'));
});
