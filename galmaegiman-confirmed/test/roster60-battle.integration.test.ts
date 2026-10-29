import {beforeAll,afterAll,it,expect,describe} from 'vitest';
import Fastify from 'fastify';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,mkdirSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';import {join} from 'node:path';import {randomUUID} from 'node:crypto';
import {registerRoster60} from '../src/roster60-routes.js';
import {skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authenticated persisted five-stat battle (synthetic server policy only)',()=>{
 let db:PrismaClient,root:string,player:string,other:string,app:ReturnType<typeof Fastify>;
 const write=(name:string,data:unknown)=>writeFileSync(join(root,'data',name),JSON.stringify(data));
 const unit=(id:string,hp:number)=>({id,name:id==='u01'?'갈매미맨':'미니',rarity:'COMMON',stats:{hp,mp:10,durability:0,statusResistance:0,speed:1},levelOneStats:{hp,mp:10,durability:0,statusResistance:0,speed:1},combatGrowth:[{hp,mp:10,durability:0,statusResistance:0,speed:1},{hp,mp:11,durability:0,statusResistance:0,speed:1}],growth:[{totalXp:0,maxMp:10},{totalXp:100,maxMp:11}],attack:{id:'attack',name:'공격',element:'NORMAL',mpCost:0,power:2},skills:Array.from({length:6},(_,i)=>({id:`skill${i}`,name:`시험${i}`,element:'NORMAL',mpCost:i===5?5:8,power:i===5?100:1})),suitability:Object.fromEntries(skillElements.map(e=>[e,0])),defense:Object.fromEntries(skillElements.map(e=>[e,'normal']))});
 const catalog={statSchema:'gmm-five-stats-v1',approved:true,version:'TEST_ONLY_NOT_PRODUCT',characters:[unit('u01',100),unit('u02',10)]};
 const policy={approved:true,version:'TEST_ONLY_NOT_PRODUCT',catalogVersion:catalog.version,calculation:{model:'linear-level-hyperbolic-v1',version:'test',levelSlope:.04,durabilityScale:100,resistanceScale:100},reflection:'return-post-durability',enemyStrategy:'first-affordable',maxRounds:20,encounters:[{id:'test-tutorial',speciesId:'u02',level:1,tutorial:true,winXp:10,lossXp:0}]};
 function server(){app=Fastify();registerRoster60(app,db,root,r=>r.headers.authorization===player?player:r.headers.authorization===other?other:null,()=>true);}
 async function post(path:string,payload:unknown,owner=player){return app.inject({method:'POST',url:'/my/api/native/roster60/battle/'+path,headers:{authorization:owner},payload:payload as any});}
 async function owned(owner=player){return db.roster60Owned.create({data:{playerId:owner,speciesId:'u01',source:randomUUID()}});}
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');
  db=new PrismaClient({datasources:{db:{url}}});player=(await db.player.create({data:{identity:randomUUID()}})).id;other=(await db.player.create({data:{identity:randomUUID()}})).id;
  root=mkdtempSync(join(tmpdir(),'battle-policy-'));mkdirSync(join(root,'data'));write('roster60-approved.json',catalog);write('roster60-battle-policy.json',policy);server();
 });
 afterAll(async()=>{await app.close();await db.player.deleteMany({where:{id:{in:[player,other]}}});await db.$disconnect();rmSync(root,{recursive:true,force:true});});
 it('formation uses owned XP and the same five stats as battle without another owner data',async()=>{
  const u=await owned();await db.roster60Owned.update({where:{id:u.id},data:{totalXp:100n}});const foreign=await owned(other);
  const r=await app.inject({method:'GET',url:'/my/api/native/roster60/battle/options',headers:{authorization:player}});
  expect(r.statusCode).toBe(200);const units=r.json().units;expect(units.find((v:any)=>v.ownedId===u.id)).toMatchObject({level:2,stats:{hp:100,mp:11,durability:0,statusResistance:0,speed:1}});expect(units.some((v:any)=>v.ownedId===foreign.id)).toBe(false);
 });
 it('gates missing policy and unauthorized or foreign owned IDs without consuming unit',async()=>{
  const u=await owned();write('roster60-battle-policy.json',{...policy,approved:false});
  expect((await post('create',{requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'})).json().error).toBe('BATTLE_POLICY_PENDING');write('roster60-battle-policy.json',policy);
  expect((await post('create',{requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'},other)).statusCode).toBe(404);
  expect((await post('create',{requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'},'no-login')).statusCode).toBe(401);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).status).toBe('AVAILABLE');
 });
 it('persists seed/order, reconnects, fixes duplicate results, synchronizes MP, and settles once',async()=>{
  const u=await owned();await db.roster60State.create({data:{playerId:player,starterId:u.id}});
  const req={requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'};
  const first=await post('create',req);expect(first.statusCode).toBe(200);let view=first.json();const id=view.battleId;
  expect(view.state.fighters[0].mp).toBe(10);expect(first.body).not.toContain('seed');expect((await post('create',req)).json()).toEqual(view);
  expect((await post('create',{...req,encounterId:'tampered'})).json().error).toBe('REQUEST_ID_REUSED');
  const stored=await db.roster60Battle.findUniqueOrThrow({where:{id}});expect(stored.seed.length).toBe(64);
  await app.close();server();expect((await post('read',{battleId:id})).json()).toEqual(view);
  expect((await post('read',{battleId:id},other)).statusCode).toBe(404);
  const action={requestId:randomUUID(),battleId:id,revision:0,action:{kind:'skill',skillId:'skill0'}};
  view=(await post('action',action)).json();expect(view.revision).toBe(1);expect(view.state.fighters[0].mp).toBe(2);
  expect((await post('action',action)).json()).toEqual(view);
  expect((await post('action',{...action,action:{kind:'focus'}})).json().error).toBe('REQUEST_ID_REUSED');
  expect((await post('action',{...action,requestId:randomUUID()})).json().error).toBe('STALE_REVISION');
  expect((await post('action',{...action,requestId:randomUUID(),revision:1})).json().error).toBe('INSUFFICIENT_MP');
  expect((await post('read',{battleId:id})).json()).toEqual(view);
  for(const mp of [7,10]){view=(await post('action',{requestId:randomUUID(),battleId:id,revision:view.revision,action:{kind:'focus'}})).json();expect(view.state.fighters[0].mp).toBe(mp);}
  const kill={requestId:randomUUID(),battleId:id,revision:view.revision,action:{kind:'skill',skillId:'skill5'}};
  view=(await post('action',kill)).json();expect(view.status).toBe('FINISHED');expect(view.state.outcome).toBe('win');expect(view.state.fighters[0].mp).toBe(5);
  // Pinned policy means removal/replacement cannot change a retry or settlement.
  write('roster60-battle-policy.json',{approved:false});expect((await post('action',kill)).json()).toEqual(view);
  const settle={requestId:randomUUID(),battleId:id,revision:view.revision};const settled=(await post('settle',settle)).json();expect(settled.status).toBe('SETTLED');expect(settled.settlement.xpAwarded).toBe(10);
  expect(settled.settlement.xpProgress).toEqual({before:{level:1,totalXp:'0',levelStartTotalXp:'0',nextLevelTotalXp:'100',maxLevel:60},after:{level:1,totalXp:'10',levelStartTotalXp:'0',nextLevelTotalXp:'100',maxLevel:60}});
  expect((await post('settle',settle)).json()).toEqual(settled);expect((await post('settle',{...settle,requestId:randomUUID()})).json()).toEqual(settled);
  const after=await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}});expect(after.totalXp).toBe(10n);expect(after.status).toBe('AVAILABLE');
  expect((await db.roster60State.findUniqueOrThrow({where:{playerId:player}})).tutorialProof).toBe(id);
  const completed=await app.inject({method:'POST',url:'/my/api/native/roster60/complete',headers:{authorization:player},payload:{}});expect(completed.statusCode).toBe(200);
  const legacy={...settled.settlement};delete legacy.xpProgress;
  await db.roster60Battle.update({where:{id},data:{settlement:JSON.stringify(legacy)}});
  const oldReplay=(await post('settle',{...settle,requestId:randomUUID()})).json();expect(oldReplay.settlement).toEqual(legacy);expect(oldReplay.settlement.xpProgress).toBeUndefined();expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(10n);
  write('roster60-battle-policy.json',policy);
 });
 it('concurrent actions advance once; failed settlement rolls back every write',async()=>{
  const u=await owned();let v=(await post('create',{requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'})).json();
  const action=()=>({requestId:randomUUID(),battleId:v.battleId,revision:0,action:{kind:'focus'}});
  expect((await post('action',action(),other)).statusCode).toBe(404);
  const responses=await Promise.all([post('action',action()),post('action',action())]);expect(responses.map(r=>r.statusCode).sort()).toEqual([200,409]);
  v=(await post('read',{battleId:v.battleId})).json();expect(v.revision).toBe(1);
  v=(await post('action',{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action:{kind:'skill',skillId:'skill5'}})).json();expect(v.status).toBe('FINISHED');
  const settlement={requestId:randomUUID(),battleId:v.battleId,revision:v.revision};
  expect((await post('settle',settlement,other)).statusCode).toBe(404);
  // Failure injected only into this disposable test DB; abort after session CAS.
  await db.$executeRawUnsafe(`CREATE TRIGGER test_battle_settle_failure BEFORE UPDATE ON Roster60Owned WHEN NEW.id='${u.id}' AND NEW.status='AVAILABLE' BEGIN SELECT RAISE(ABORT, 'test-only-settlement-failure'); END`);
  try{
   expect((await post('settle',settlement)).statusCode).toBe(500);
   expect((await post('read',{battleId:v.battleId})).json()).toEqual(v);
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);
   expect(await db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:player,requestId:settlement.requestId}}})).toBeNull();
  }finally{await db.$executeRawUnsafe('DROP TRIGGER test_battle_settle_failure');}
  expect((await post('settle',settlement)).json().settlement.xpAwarded).toBe(10);
 });
 it('serializes two creates for one owned unit and rejects client seed/HP payloads',async()=>{
  const u=await owned(),make=()=>({requestId:randomUUID(),ownedId:u.id,encounterId:'test-tutorial'});
  expect((await post('create',{...make(),seed:'client-seed'})).statusCode).toBe(400);
  const res=await Promise.all([post('create',make()),post('create',make())]);expect(res.map(r=>r.statusCode).sort()).toEqual([200,409]);
  expect(await db.roster60Battle.count({where:{ownedId:u.id}})).toBe(1);
 });
});
