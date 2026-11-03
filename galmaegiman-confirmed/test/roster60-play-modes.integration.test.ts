import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,cpSync,readFileSync,writeFileSync,unlinkSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
type Actor={id:string;label:string;headers:Record<string,string>;ownedIds:string[]};
type Transport='http'|'inject';
type Reply={status:number;body:any};
suite('normal/rank: real localhost HTTP, ordinary login and disposable SQLite',()=>{
 let db:PrismaClient,app:Awaited<ReturnType<typeof buildApp>>|undefined,game:GameService,root:string,baseUrl:string;
 let catalog:any,policy:any,actors:Actor[],caseIds:string[];const rankAccountLevels=new Map<string,number>();
 const content=loadContent(),secret='localhost-play-modes-test-secret-not-production-2026';
 const previousProfile=process.env.ROSTER60_PROFILE;
 const trace:{transport:string;actor:string;method:string;path:string;status:number}[]=[],completed:any[]=[];
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('fresh disposable SQLite only');
  db=new PrismaClient({datasources:{db:{url}}});delete process.env.ROSTER60_PROFILE;
 });
 async function open(){
  game=new GameService(db,content);
  app=await buildApp(db,game,{secret,root,baseUrl:async()=>'',roster60AccountLevelReader:async(_tx,id)=>rankAccountLevels.get(id)??null});
  baseUrl=await app.listen({host:'127.0.0.1',port:0});
 }
 async function restart(){await app!.close();await open();}
 beforeEach(async()=>{
  actors=[];caseIds=[];rankAccountLevels.clear();root=mkdtempSync(join(tmpdir(),'gmm-http-play-modes-'));cpSync('data',join(root,'data'),{recursive:true});
  // Explicit test-only files under /tmp. No product catalog, environment file or policy is promoted.
  catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
  policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
  // Short natural KOs in this fixture only; no battle rows are edited after gameplay starts.
  for(const [species,speed] of [['u01',100000],['u02',1]] as const){
   const unit=catalog.characters.find((u:any)=>u.id===species);
   unit.stats.speed=speed;unit.levelOneStats.speed=speed;for(const stats of unit.combatGrowth)stats.speed=speed;
   unit.attack={...unit.attack,element:'NORMAL',mpCost:0,power:species==='u01'?100000:1};unit.suitability.NORMAL=0;unit.defense.NORMAL='normal';
   if(species==='u02'){unit.stats.hp=50;unit.levelOneStats.hp=50;for(const stats of unit.combatGrowth)stats.hp=50;}
  }
  writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(catalog));
  writeFileSync(join(root,'data/roster60-battle-policy.json'),JSON.stringify(policy));
  writeFileSync(join(root,'data/roster60-normal-xp-policy.json'),readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));await open();
 });
 afterEach(async()=>{
  if(app){await app.close();app=undefined;}
  if(caseIds.length){
   const rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:caseIds}},{player1Id:{in:caseIds}}]},select:{id:true}}),roomIds=rooms.map(r=>r.id);
   await db.rankedSettlement.deleteMany({where:{roomId:{in:roomIds}}});await db.rankedMatch.deleteMany({where:{roomId:{in:roomIds}}});
   await db.casualHumanSettlement.deleteMany({where:{OR:[{player0Id:{in:caseIds}},{player1Id:{in:caseIds}}]}});
   await db.roster60PvpMatch.deleteMany({where:{id:{in:roomIds}}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:caseIds}}});
   await db.roster60PvpParty.deleteMany({where:{playerId:{in:caseIds}}});await db.realtimeActivity.deleteMany({where:{playerId:{in:caseIds}}});
   await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:caseIds}}});
   for(const id of caseIds)await game.webDeleteAccount(id);
  }
  if(root)rmSync(root,{recursive:true,force:true});
 });
 afterAll(async()=>{
  if(previousProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previousProfile;
  if(db)await db.$disconnect();
  const destination=process.env.PLAY_MODES_EVIDENCE_DIR;
  if(destination){if(destination!=='/workspace/scratch/fifth-verification/server')throw Error('unexpected test evidence destination');
   writeFileSync(join(destination,'http-trace.json'),JSON.stringify({ordinaryLogin:true,mockedHandlers:false,trace,completed},null,2));}
 });
 async function request(actor:Actor|undefined,path:string,payload?:unknown,transport:Transport='inject',headers?:Record<string,string>):Promise<Reply>{
  const method=payload===undefined?'GET':'POST',url=path.startsWith('/')?path:'/my/api/native/roster60/pvp/'+path;
  const sent=headers??actor?.headers??{};
  let status:number,body:any;
  if(transport==='http'){
   const response=await fetch(baseUrl+url,{method,headers:{...sent,...(payload===undefined?{}:{'content-type':'application/json'})},...(payload===undefined?{}:{body:JSON.stringify(payload)}),redirect:'error'});
   status=response.status;body=await response.json();
  }else{const response=await app!.inject({method,url,headers:sent,...(payload===undefined?{}:{payload:payload as any})});status=response.statusCode;body=response.json();}
  // No login code, session token, request headers or private owned IDs in evidence.
  trace.push({transport,actor:actor?.label??'unauthenticated',method,path:url,status});return {status,body};
 }
 async function ok(actor:Actor,path:string,payload?:unknown,transport:Transport='inject'){
  const result=await request(actor,path,payload,transport);expect(result.status,path+':'+JSON.stringify(result.body)).toBe(200);return result.body;
 }
 async function pvpTerminalDiagnostic(view:any){
  const room=await db.roster60PvpMatch.findUnique({where:{id:view.battleId},select:{status:true,revision:true,deadline:true,presence:true}});
  const monitor=await db.appMeta.findUnique({where:{key:'roster60-presence-monitor-v1'}});
  return JSON.stringify({status:view.status,outcome:view.state?.outcome,turn:view.state?.turn,reason:view.settlement?.reason,terminationKind:view.settlement?.terminationKind,serverNow:view.serverNow,observedAt:Date.now(),room,monitor:monitor?.value});
 }
 async function user(label:string,count:number,species='u01',transport:Transport='inject'){
  const identity='play-mode-http-'+randomUUID();await game.handle(identity,'내정보');
  const player=await db.player.findUniqueOrThrow({where:{identity}});caseIds.push(player.id);
  const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
  const login=await request(undefined,'/my/api/login',{code},transport,{'x-gmm':'1','x-gmm-app':'1'});
  expect(login.status).toBe(200);expect(typeof login.body.token==='string').toBe(true);
  const actor:Actor={id:player.id,label,headers:{'x-gmm':'1','x-gmm-s':login.body.token},ownedIds:[]};actors.push(actor);
  const definition=catalog.characters.find((u:any)=>u.id===species);
  for(let i=0;i<count;i++)actor.ownedIds.push((await db.roster60Owned.create({data:{playerId:actor.id,speciesId:species,totalXp:definition.growth[7].totalXp,source:'http-fixture-'+i}})).id);
  expect((await ok(actor,'/my/api/who',undefined,transport)).nickname).toBeDefined();return actor;
 }
 async function prepareRank(actor:Actor,transport:Transport='inject'){
  // This internal reader is a server fixture, never a client header or a bypass of unit admission.
  rankAccountLevels.set(actor.id,60);
  for(const unit of await db.roster60Owned.findMany({where:{playerId:actor.id,id:{in:actor.ownedIds}}})){
   const definition=catalog.characters.find((d:any)=>d.id===unit.speciesId)!;
   await db.roster60Owned.update({where:{id:unit.id},data:{totalXp:BigInt(definition.growth[59].totalXp)}});
  }
  const options=await ok(actor,'options',undefined,transport),units=options.units.filter((u:any)=>actor.ownedIds.includes(u.ownedId));
  expect(options.rankEligibility).toMatchObject({accountLevel:60,eligible:true});expect(units).toHaveLength(actor.ownedIds.length);
  expect(units.every((u:any)=>u.level===60&&u.rankEligible)).toBe(true);
 }
 async function pair(mode:'normal'|'rank',size:number,transport:Transport){
  const a=await user('A',size,'u01',transport),b=await user('B',size,'u02',transport);
  for(const actor of [a,b]){
   if(mode==='rank')await prepareRank(actor,transport);
   const options=await ok(actor,'options',undefined,transport);expect(options.units.filter((u:any)=>actor.ownedIds.includes(u.ownedId)).every((u:any)=>u.level===(mode==='rank'?60:8))).toBe(true);
   expect((await ok(actor,'party',{ownedIds:actor.ownedIds},transport)).partyIds).toEqual(actor.ownedIds);
  }
  expect((await ok(a,'join',{requestId:randomUUID(),mode},transport)).status).toBe('waiting');
  const joined=await ok(b,'join',{requestId:randomUUID(),mode},transport),battleId=joined.battle.battleId;
  expect(joined.mode).toBe(mode);expect(joined.battle.status).toBe('ACCEPTING');
  await ok(a,'accept',{requestId:randomUUID(),battleId,revision:0,accept:true},transport);
  if(transport==='http'){
   await restart();const accepted=(await ok(a,'current',undefined,transport)).battle,pending=(await ok(b,'current',undefined,transport)).battle;
   expect(accepted.status).toBe('ACCEPTING');expect(accepted.accepted).toBe(true);expect(pending.accepted).toBe(false);
  }
  const battle=await ok(b,'accept',{requestId:randomUUID(),battleId,revision:0,accept:true},transport);
  expect(battle.status).toBe('ACTIVE');return {a,b,battle,battleId};
 }
 it.each([1,2,3])('sets %i actual owned party members and queues/cancels both modes through authenticated routes',async size=>{
  const actor=await user('party-'+size,size);
  expect((await ok(actor,'party',{ownedIds:actor.ownedIds})).partyIds).toHaveLength(size);
  for(const mode of ['normal','rank'] as const){
   if(mode==='rank')await prepareRank(actor);
   const requestId=randomUUID(),waiting=await ok(actor,'join',{requestId,mode});expect(waiting.status).toBe('waiting');expect(waiting.mode).toBe(mode);
   expect(await db.roster60Owned.count({where:{id:{in:actor.ownedIds},status:'PVP_QUEUE'}})).toBe(size);
   expect(await ok(actor,'join',{requestId,mode})).toEqual(waiting);
   if(size===1)await restart();expect((await ok(actor,'current')).status).toBe('waiting');expect((await ok(actor,'cancel',{requestId:randomUUID()})).status).toBe('idle');
   expect(await db.roster60Owned.count({where:{id:{in:actor.ownedIds},status:'AVAILABLE'}})).toBe(size);
  }
  completed.push({case:'party-range',size,modes:['normal','rank'],transport:'inject',restartedWaiting:size===1});
 });
 it.each([4,5,6])('rejects new %i-member parties through authenticated routes without deleting owned units',async size=>{
  const actor=await user('oversized-'+size,size);
  const rejected=await request(actor,'party',{ownedIds:actor.ownedIds});expect(rejected.status).toBe(400);expect(rejected.body.error).toBe('INVALID_PARTY');
  expect(await db.roster60PvpParty.findUnique({where:{playerId:actor.id}})).toBeNull();
  expect(await db.roster60Owned.count({where:{playerId:actor.id,status:'AVAILABLE'}})).toBe(size);
 });
 it.each([['normal',1],['rank',3]] as const)('%s reaches natural KO, durable recovery and exactly-once rating/settlement over localhost HTTP (party %i)',async(mode,size)=>{
  const transport:Transport='http',{a,b,battleId}=await pair(mode,size,transport);
  const ownXp=await db.roster60Owned.findMany({where:{playerId:{in:[a.id,b.id]}},select:{id:true,totalXp:true},orderBy:{id:'asc'}});
  let current=(await ok(a,'current',undefined,transport)).battle;
  expect(current.state.fighters[0].level).toBe(mode==='rank'?30:8);expect(current.state.reserves).toHaveLength(size-1);
  let recovered=false;
  while(current.status==='ACTIVE'){
   const revision=current.revision,turn=current.state.turn,command={requestId:randomUUID(),battleId,revision,action:{kind:'attack'}};
   const pending=await ok(a,'action',command,transport);expect(pending.waitingForOpponent).toBe(true);
   expect(await ok(a,'action',command,transport)).toEqual(pending);
   if(!recovered){
    await restart();const restored=(await ok(a,'current',undefined,transport)).battle,opponent=(await ok(b,'current',undefined,transport)).battle;
    expect(restored.battleId).toBe(battleId);expect(restored.mode).toBe(mode);expect(restored.waitingForOpponent).toBe(true);expect(restored.revision).toBe(revision);
    expect(opponent.waitingForOpponent).toBe(false);expect(opponent.state.pending).toBeUndefined();expect(opponent.seed).toBeUndefined();
    for(const id of b.ownedIds)expect(JSON.stringify(restored)).not.toContain(id);
    recovered=true;
   }
   const response=await ok(b,'action',{requestId:randomUUID(),battleId,revision,action:{kind:'focus'}},transport);
   expect(response.state.turn).toBe(turn+1);current=(await ok(a,'current',undefined,transport)).battle;
   if(response.status==='ACTIVE'){
    expect(response.state.fighters[0].hp).toBe(0);const next=response.state.reserves.find((u:any)=>u.hp>0);
    const switched=await ok(b,'action',{requestId:randomUUID(),battleId,revision:response.revision,action:{kind:'switch',targetOwnedId:next.ownedId}},transport);
    expect(switched.state.turn).toBe(response.state.turn);current=(await ok(a,'current',undefined,transport)).battle;
   }
   expect(current.state.turn).toBeLessThanOrEqual(size);
  }
  const terminalDiagnostic=current.state.outcome==='win'?undefined:await pvpTerminalDiagnostic(current);
  expect(current.status,terminalDiagnostic).toBe('FINISHED');expect(current.state.outcome,terminalDiagnostic).toBe('win');expect(current.state.turn).toBe(size);expect(current.settlement.reason).toBe('BATTLE');
  expect(current.settlement.xpAwarded).toBe(mode==='normal'?600:0);expect(current.state.opponentIntel.defenses.NORMAL).toBe('normal');
  const collection=(await ok(a,'/my/api/native/roster60/collection',undefined,transport)).characters.find((u:any)=>u.id==='u02');
  expect(collection.defense).toBeUndefined();expect(collection.defenseKnowledge.defenses.NORMAL).toBe('normal');
  expect(await db.roster60ObservedDefense.count({where:{playerId:a.id,speciesId:'u02',element:'NORMAL'}})).toBe(1);
  expect(await db.roster60ObservedDefense.count({where:{playerId:b.id}})).toBe(0);
  const rows=await db.roster60Owned.findMany({where:{playerId:{in:[a.id,b.id]}},select:{id:true,totalXp:true},orderBy:{id:'asc'}});
  // 배포 정책 v3: 레벨8 단독 출전 승자는 마무리 개체로 기존 레벨10 원천 600 전액, 진 편은 0이다.
  expect(rows).toEqual(mode==='rank'?ownXp:ownXp.map(u=>({...u,totalXp:u.totalXp+BigInt(a.ownedIds.includes(u.id)?600:0)})));
  const rating=mode==='rank'?await db.rankedProfile.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}}):await db.casualHumanRating.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}});
  const winner=rating.find(r=>r.playerId===a.id)!,loser=rating.find(r=>r.playerId===b.id)!;
  expect(winner.games).toBe(1);expect(winner.wins).toBe(1);expect(loser.games).toBe(1);expect(loser.losses).toBe(1);
  if(mode==='rank'){
   expect((winner as any).rankedMmr).toBeGreaterThan(1000);expect((loser as any).rankedMmr).toBeLessThan(1000);
   expect(await db.rankedSettlement.count({where:{roomId:battleId}})).toBe(1);expect(await db.casualHumanSettlement.count({where:{roomId:battleId}})).toBe(0);
   expect(await db.rankedMatch.findUniqueOrThrow({where:{roomId:battleId}})).toMatchObject({participant0Kind:'HUMAN',participant1Kind:'HUMAN',state:'FINISHED',reason:'BATTLE'});
  }else{
   expect((winner as any).mmr).toBeGreaterThan(1000);expect((loser as any).mmr).toBeLessThan(1000);
   expect(await db.casualHumanSettlement.count({where:{roomId:battleId}})).toBe(1);expect(await db.rankedSettlement.count({where:{roomId:battleId}})).toBe(0);
   expect(await db.rankedProfile.count({where:{playerId:{in:[a.id,b.id]}}})).toBe(0);
  }
  await restart();const finished=(await ok(a,'current',undefined,transport)).battle;expect(finished.battleId).toBe(battleId);expect(finished.state.outcome).toBe('win');
  const settle={requestId:randomUUID(),battleId,revision:finished.revision},settled=await ok(a,'settle',settle,transport);
  expect(settled.status).toBe('SETTLED');expect(await ok(a,'settle',settle,transport)).toEqual(settled);
  await ok(a,'settle',{...settle,requestId:randomUUID()},transport);expect((await ok(a,'current',undefined,transport)).status).toBe('idle');
  expect((await ok(b,'current',undefined,transport)).battle.status).toBe('FINISHED');await ok(b,'settle',{...settle,requestId:randomUUID()},transport);
  expect((await ok(b,'current',undefined,transport)).status).toBe('idle');
  const after=mode==='rank'?await db.rankedProfile.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}}):await db.casualHumanRating.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}});expect(after).toEqual(rating);
  expect(await db.roster60Owned.findMany({where:{playerId:{in:[a.id,b.id]}},select:{id:true,totalXp:true},orderBy:{id:'asc'}})).toEqual(rows);
  expect(await db.roster60Owned.count({where:{playerId:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(size*2);
  completed.push({case:'natural-battle-through-http',mode,size,rounds:finished.state.turn,restartedAccepting:true,restartedPending:true,restartedTerminal:true,settlementExactlyOnce:true,ratings:{winnerGames:winner.games,winnerWins:winner.wins,loserGames:loser.games,loserLosses:loser.losses}});
 },30000);
 it('keeps real auth, strict modes, owned party boundaries, foreign rooms and stale/pending rejection',async()=>{
  expect((await request(undefined,'options')).status).toBe(403);expect((await request(undefined,'options',undefined,'inject',{'x-gmm':'1'})).status).toBe(401);
  const {a,b,battle,battleId}=await pair('normal',2,'inject'),foreign=await user('foreign',7,'u02');
  expect((await request(a,'options',undefined,'inject',{'x-gmm':'1','x-gmm-s':'forged-session'})).status).toBe(401);
  for(const mode of ['casual','ranked','story','invalid'])expect((await request(a,'join',{requestId:randomUUID(),mode})).status).toBe(400);
  expect((await request(foreign,'party',{ownedIds:[]})).status).toBe(400);expect((await request(foreign,'party',{ownedIds:foreign.ownedIds})).status).toBe(400);
  expect((await request(foreign,'party',{ownedIds:[foreign.ownedIds[0],foreign.ownedIds[0]]})).status).toBe(400);
  expect((await request(foreign,'party',{ownedIds:[a.ownedIds[0]]})).body.error).toBe('UNIT_NOT_FOUND');
  expect((await request(foreign,'action',{requestId:randomUUID(),battleId,revision:battle.revision,action:{kind:'attack'}})).status).toBe(404);
  expect((await request(foreign,'accept',{requestId:randomUUID(),battleId,revision:battle.revision,accept:true})).status).toBe(404);
  expect((await request(foreign,'settle',{requestId:randomUUID(),battleId,revision:battle.revision})).status).toBe(404);
  const command={requestId:randomUUID(),battleId,revision:battle.revision,action:{kind:'focus'}};await ok(a,'action',command);
  expect((await request(a,'action',{...command,requestId:randomUUID(),revision:0})).body.error).toBe('STALE_REVISION');
  expect((await request(a,'action',{...command,requestId:randomUUID()})).body.error).toBe('ACTION_ALREADY_SUBMITTED');
  expect((await request(a,'action',{...command,action:{kind:'attack'}})).body.error).toBe('REQUEST_ID_REUSED');
  expect((await request(a,'settle',{requestId:randomUUID(),battleId,revision:battle.revision})).body.error).toBe('BATTLE_NOT_FINISHED');
  await ok(b,'action',{...command,requestId:randomUUID()});const active=(await ok(a,'current')).battle;
  await ok(a,'action',{requestId:randomUUID(),battleId,revision:active.revision,action:{kind:'escape'}});
 });
 it('keeps missing/unapproved battle policy pending for normal and rank instead of inventing a fallback',async()=>{
  const actor=await user('policy',1);await ok(actor,'party',{ownedIds:actor.ownedIds});
  unlinkSync(join(root,'data/roster60-battle-policy.json'));await restart();
  for(const mode of ['normal','rank']){
   const result=await request(actor,'join',{requestId:randomUUID(),mode});expect(result.status).toBe(503);expect(result.body.error).toBe('BATTLE_POLICY_PENDING');
  }
  expect((await request(actor,'options')).body.error).toBe('BATTLE_POLICY_PENDING');
  writeFileSync(join(root,'data/roster60-battle-policy.json'),JSON.stringify({...policy,approved:false}));
  const unapproved=await request(actor,'join',{requestId:randomUUID(),mode:'rank'});expect(unapproved.status).toBe(503);expect(unapproved.body.error).toBe('BATTLE_POLICY_PENDING');
  expect(await db.roster60PvpQueue.count({where:{playerId:actor.id}})).toBe(0);expect(await db.realtimeActivity.count({where:{playerId:actor.id}})).toBe(0);
  expect(await db.roster60Owned.count({where:{id:{in:actor.ownedIds},status:'AVAILABLE'}})).toBe(1);
  completed.push({case:'pending-policy',missingAndUnapproved:true,modes:['normal','rank'],status:503,productionFallback:false});
 });
});
