import {describe,it,expect,beforeAll,afterAll} from 'vitest';
import {PrismaClient} from '@prisma/client';import {readFileSync,mkdtempSync,writeFileSync,mkdirSync,rmSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';import {randomUUID} from 'node:crypto';import Fastify from 'fastify';
import {registerRoster60} from '../src/roster60-routes.js';import {adventureEscapeSucceeds} from '../src/roster60-adventure.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authenticated adventure escrow and escape',()=>{
 let db:PrismaClient,app:ReturnType<typeof Fastify>,root:string,player:string,other:string;const prior=process.env.ROSTER60_PROFILE;
 async function post(path:string,body:any,who=player){return app.inject({method:'POST',url:'/my/api/native/roster60/'+path,headers:{authorization:who},payload:body});}
 async function ok(path:string,body:any){const r=await post(path,body);expect(r.statusCode,r.body).toBe(200);return r.json();}
 async function start(){const u=await db.roster60Owned.create({data:{playerId:player,speciesId:'u01',source:randomUUID()}});const b={requestId:randomUUID(),ownedIds:[u.id]};const run=await ok('adventure/start',b);expect((await ok('adventure/start',b))).toEqual(run);return {u,run};}
 async function battle(run:any,encounterId='adventure-test'){return ok('battle/create',{requestId:randomUUID(),ownedId:run.party[0].ownedId,reserveIds:run.party.slice(1).map((f:any)=>f.ownedId),adventureId:run.adventureId,encounterId});}
 async function act(v:any,action:any){return ok('battle/action',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action});}
 async function settle(v:any){return ok('battle/settle',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision});}
 async function read(run:any){return ok('adventure/read',{adventureId:run.adventureId});}
 async function seed(v:any,success:boolean){let n=0;while(adventureEscapeSucceeds('test-'+n,v.battleId,v.state.turn)!==success)n++;await db.roster60Battle.update({where:{id:v.battleId},data:{seed:'test-'+n}});}
 beforeAll(async()=>{delete process.env.ROSTER60_PROFILE;const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated only');db=new PrismaClient({datasources:{db:{url}}});player=(await db.player.create({data:{identity:randomUUID(),credits:321}})).id;other=(await db.player.create({data:{identity:randomUUID()}})).id;root=mkdtempSync(join(tmpdir(),'adventure-api-'));mkdirSync(join(root,'data'));
 const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8')),p=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
 const enemy=c.characters.find((u:any)=>u.id==='u07');enemy.combatGrowth[0].hp=1;enemy.levelOneStats.hp=1;enemy.attack.power=1;enemy.skills=[];
 p.encounters=[{id:'adventure-test',speciesId:'u07',level:1,tutorial:false,mode:'adventure',winXp:10,lossXp:7},{id:'adventure-odd',speciesId:'u07',level:1,tutorial:false,mode:'adventure',winXp:11,lossXp:0}];writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(c));writeFileSync(join(root,'data/roster60-battle-policy.json'),JSON.stringify(p));app=Fastify();registerRoster60(app,db,root,r=>[player,other].includes(String(r.headers.authorization))?String(r.headers.authorization):null,()=>true);
 });
 afterAll(async()=>{if(prior===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=prior;await app.close();await db.playerItem.deleteMany({where:{playerId:{in:[player,other]}}});await db.player.deleteMany({where:{id:{in:[player,other]}}});await db.$disconnect();rmSync(root,{recursive:true,force:true});});
 it('escrows victory XP, preserves it on escape, rejects in-battle return and pays once outside',async()=>{
  let {u,run}=await start();let v=await battle(run);run=await read(run);
  expect((await post('adventure/return',{requestId:randomUUID(),adventureId:run.adventureId,revision:run.revision})).json().error).toBe('RETURN_ONLY_OUTSIDE_BATTLE');
  v=await act(v,{kind:'attack'});expect(v.state.outcome).toBe('win');let result=await settle(v);expect(result.settlement.xpEscrowed).toBe(10);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);
  run=await read(run);v=await battle(run);await seed(v,true);const body={requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'escape'}};v=await ok('battle/action',body);expect(v.state.outcome).toBe('escaped');expect(v.state.turn).toBe(0);expect(await ok('battle/action',body)).toEqual(v);expect((await post('battle/action',{...body,requestId:randomUUID()})).statusCode).toBe(409);
  result=await settle(v);expect(result.settlement.pendingRewards.xp[u.id]).toBe(10);run=await read(run);const ret={requestId:randomUUID(),adventureId:run.adventureId,revision:run.revision};const returned=await ok('adventure/return',ret);expect(returned.status).toBe('RETURNED');expect(await ok('adventure/return',ret)).toEqual(returned);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(10n);
 });
 it('failed escape runs enemy once without player action; receipt and ownership cannot reroll',async()=>{
  const {run}=await start();let v=await battle(run);await seed(v,false);const before=v.state.fighters[0];const b={requestId:randomUUID(),battleId:v.battleId,revision:0,action:{kind:'escape'}};v=await ok('battle/action',b);expect(v.state.turn).toBe(1);expect(v.state.fighters[0].mp).toBe(before.mp);expect(v.state.fighters[1].hp).toBe(1);const events=v.state.events[0].events;expect(events.filter((e:any)=>e.actor==='enemy')).toHaveLength(1);expect(events.filter((e:any)=>e.actor==='self').map((e:any)=>e.kind)).toEqual(['escape-failed']);expect(await ok('battle/action',b)).toEqual(v);expect((await post('battle/action',b,other)).statusCode).toBe(404);expect((await post('battle/action',{...b,action:{kind:'attack'}})).json().error).toBe('REQUEST_ID_REUSED');
 });
 it('total defeat discards only this run escrow and does not touch existing XP or currency',async()=>{
  const {u,run}=await start();let v=await battle(run);v=await act(v,{kind:'attack'});await settle(v);await db.roster60Adventure.update({where:{id:run.adventureId},data:{pendingRewards:JSON.stringify({xp:{[u.id]:10},currencies:{snack:8},items:{potion:2}})}});v=await battle(await read(run));const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}});const state=JSON.parse(row.state);state.fighters[0].hp=1;state.fighters[1].attack.power=100000;await db.roster60Battle.update({where:{id:v.battleId},data:{state:JSON.stringify(state)}});await seed(v,false);v=await act(v,{kind:'escape'});expect(v.state.outcome).toBe('loss');await settle(v);const lost=await read(run);expect(lost.status).toBe('LOST');expect(lost.pendingRewards.xp).toEqual({});expect(lost.settlement.lostRewards.xp[u.id]).toBe(10);expect(lost.settlement.lostRewards.currencies.snack).toBe(8);expect(lost.settlement.lostRewards.items.potion).toBe(2);expect((await db.player.findUniqueOrThrow({where:{id:player}})).credits).toBe(321);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);expect((await ok('adventure/return',{requestId:randomUUID(),adventureId:run.adventureId,revision:lost.revision})).status).toBe('LOST');
 });
 it('concurrent return pays once and transaction failure retains escrow and locks',async()=>{
  const {u,run}=await start();await settle(await act(await battle(run),{kind:'attack'}));const ready=await read(run),b={requestId:randomUUID(),adventureId:run.adventureId,revision:ready.revision};
  await db.$executeRawUnsafe("CREATE TRIGGER adventure_fail BEFORE UPDATE ON Roster60Adventure WHEN NEW.status = 'RETURNED' BEGIN SELECT RAISE(ABORT, 'test rollback'); END");
  try{expect((await post('adventure/return',b)).statusCode).toBeGreaterThanOrEqual(400);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);expect((await read(run)).pendingRewards.xp[u.id]).toBe(10);}finally{await db.$executeRawUnsafe('DROP TRIGGER adventure_fail');}
  const results=await Promise.all([post('adventure/return',b),post('adventure/return',b)]);expect(results.map(r=>r.statusCode)).toEqual([200,200]);expect(results[0].json()).toEqual(results[1].json());expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(10n);
  expect((await post('adventure/read',{adventureId:run.adventureId},other)).statusCode).toBe(404);
 });
 it('settles server-owned currency and item escrow exactly once and preserves it on capacity failure',async()=>{
  const {run}=await start();const before=await db.player.findUniqueOrThrow({where:{id:player}});await db.playerItem.upsert({where:{playerId_itemId:{playerId:player,itemId:'potion'}},create:{playerId:player,itemId:'potion',count:20},update:{count:20}});
  await db.roster60Adventure.update({where:{id:run.adventureId},data:{pendingRewards:JSON.stringify({xp:{},currencies:{credits:3,snack:7,peanut:1},items:{potion:2}})}});
  const b={requestId:randomUUID(),adventureId:run.adventureId,revision:0};expect((await post('adventure/return',b)).json().error).toBe('REWARD_CAPACITY_REQUIRED');expect((await db.player.findUniqueOrThrow({where:{id:player}})).credits).toBe(before.credits);expect((await read(run)).pendingRewards.items.potion).toBe(2);
  await db.playerItem.update({where:{playerId_itemId:{playerId:player,itemId:'potion'}},data:{count:1}});const paid=await ok('adventure/return',b);expect(await ok('adventure/return',b)).toEqual(paid);const after=await db.player.findUniqueOrThrow({where:{id:player}});expect(after.credits).toBe(before.credits+3);expect(after.snack).toBe(before.snack+7);expect(after.peanut).toBe(before.peanut+1);expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:player,itemId:'potion'}}})).count).toBe(3);
 });
 it('normal return reserves item capacity for another active battle refund and rolls back all rewards',async()=>{
  const {u,run}=await start();const before=await db.player.findUniqueOrThrow({where:{id:player}});
  await db.playerItem.upsert({where:{playerId_itemId:{playerId:player,itemId:'potion'}},create:{playerId:player,itemId:'potion',count:19},update:{count:19}});
  // Separate battle owns this escrow; it must remain untouched by adventure return.
  const roomId=randomUUID(),phaseId='roster-v1:0';await db.realtimeItemEscrow.create({data:{roomId,phaseId,side:0,playerId:player,itemId:'potion',state:'HELD'}});
  try{
   await db.roster60Adventure.update({where:{id:run.adventureId},data:{pendingRewards:JSON.stringify({xp:{[u.id]:10},currencies:{snack:7},items:{potion:1}})}});
   const request={requestId:randomUUID(),adventureId:run.adventureId,revision:0};
   expect((await post('adventure/return',request)).json().error).toBe('REWARD_CAPACITY_REQUIRED');
   expect((await db.player.findUniqueOrThrow({where:{id:player}})).snack).toBe(before.snack);
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);
   expect((await read(run)).pendingRewards.items.potion).toBe(1);expect((await read(run)).status).toBe('ACTIVE');
   expect((await db.realtimeItemEscrow.findUniqueOrThrow({where:{roomId_phaseId_side:{roomId,phaseId,side:0}}})).state).toBe('HELD');
   // The other battle consumes its item: capacity becomes available, same failed request can now commit once.
   await db.realtimeItemEscrow.update({where:{roomId_phaseId_side:{roomId,phaseId,side:0}},data:{state:'CONSUMED'}});
   const paid=await ok('adventure/return',request);expect(await ok('adventure/return',request)).toEqual(paid);
   expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:player,itemId:'potion'}}})).count).toBe(20);
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(10n);
   expect((await db.player.findUniqueOrThrow({where:{id:player}})).snack).toBe(before.snack+7);
  }finally{await db.realtimeItemEscrow.deleteMany({where:{roomId}});}
 });
 it('records voluntary/forced entries per battle, floors reserve XP, escrows each share and settles once',async()=>{
  const units=[];for(let i=0;i<4;i++)units.push(await db.roster60Owned.create({data:{playerId:player,speciesId:'u01',source:randomUUID()}}));
  const ids=units.map(u=>u.id);let run=await ok('adventure/start',{requestId:randomUUID(),ownedIds:ids}),v=await battle(run,'adventure-odd');
  expect(v.state.participation).toEqual([{ownedId:ids[0],kind:'start',turn:0}]);
  const change={requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'switch',targetOwnedId:ids[1]}};
  v=await ok('battle/action',change);expect(await ok('battle/action',change)).toEqual(v);expect(v.state.participation).toHaveLength(2);
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}}),state=JSON.parse(row.state);state.fighters[0].hp=0;await db.roster60Battle.update({where:{id:v.battleId},data:{state:JSON.stringify(state)}});
  v=await act(v,{kind:'switch',targetOwnedId:ids[2]});expect(v.state.participation.at(-1).kind).toBe('forced-switch');
  v=await act(v,{kind:'switch',targetOwnedId:ids[0]});v=await act(v,{kind:'switch',targetOwnedId:ids[2]});v=await act(v,{kind:'attack'});
  const request={requestId:randomUUID(),battleId:v.battleId,revision:v.revision};const settled=await ok('battle/settle',request);
  const first={[ids[0]]:11,[ids[1]]:11,[ids[2]]:11,[ids[3]]:5};expect(settled.settlement.xpByOwnedId).toEqual(first);
  expect(await ok('battle/settle',request)).toEqual(settled);expect(await ok('battle/settle',{...request,requestId:randomUUID()})).toEqual(settled);
  run=await read(run);expect(run.pendingRewards.xp).toEqual(first);expect((await db.roster60Owned.findMany({where:{id:{in:ids}}})).every(u=>u.totalXp===0n)).toBe(true);
  v=await battle(run,'adventure-odd');expect(v.state.participation).toEqual([{ownedId:ids[2],kind:'start',turn:0}]);await settle(await act(v,{kind:'attack'}));
  run=await read(run);const totals={[ids[0]]:16,[ids[1]]:16,[ids[2]]:22,[ids[3]]:10};expect(run.pendingRewards.xp).toEqual(totals);
  const ret={requestId:randomUUID(),adventureId:run.adventureId,revision:run.revision};const returned=await ok('adventure/return',ret);expect(await ok('adventure/return',ret)).toEqual(returned);
  for(const u of units)expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(BigInt(totals[u.id]));
 });
 it('loses all participant and reserve escrow together on total defeat',async()=>{
  const units=[];for(let i=0;i<2;i++)units.push(await db.roster60Owned.create({data:{playerId:player,speciesId:'u01',source:randomUUID()}}));
  const run=await ok('adventure/start',{requestId:randomUUID(),ownedIds:units.map(u=>u.id)});await settle(await act(await battle(run,'adventure-odd'),{kind:'attack'}));
  let v=await battle(await read(run));const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}}),state=JSON.parse(row.state);state.fighters[0].hp=1;state.reserves[0].hp=0;state.fighters[1].attack.power=100000;await db.roster60Battle.update({where:{id:v.battleId},data:{state:JSON.stringify(state)}});await seed(v,false);
  v=await act(v,{kind:'escape'});expect(v.state.outcome).toBe('loss');await settle(v);const lost=await read(run);expect(lost.pendingRewards.xp).toEqual({});expect(lost.settlement.lostRewards.xp).toEqual({[units[0].id]:11,[units[1].id]:5});
  for(const u of units){const actual=await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}});expect(actual.totalXp).toBe(0n);expect(actual.status).toBe('AVAILABLE');}
 });
 it('uses a deterministic 70 percent server draw, not request IDs',()=>{let successes=0;for(let i=0;i<10000;i++){const a=adventureEscapeSucceeds(String(i),'fixed-battle',1);expect(adventureEscapeSucceeds(String(i),'fixed-battle',1)).toBe(a);successes+=Number(a);}expect(successes).toBeGreaterThan(6800);expect(successes).toBeLessThan(7200);});
});
