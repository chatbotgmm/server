import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import Fastify from 'fastify';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,mkdirSync,readFileSync,rmSync,unlinkSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {RosterPvp} from '../src/roster60-pvp.js';
import {registerRoster60} from '../src/roster60-routes.js';
import type {NormalXpPolicy} from '../src/progression/normal-xp.js';
import {initialStoryProgress} from '../src/story/types.js';
import {REALTIME_LIMITS} from '../src/pvp/service.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('일반게임 정상 완료 XP의 소유권·정산·저장 경계',()=>{
 let db:PrismaClient,app:ReturnType<typeof Fastify>,root:string,svc:RosterPvp,now:number,catalog:any,battlePolicy:any,xpPolicy:NormalXpPolicy|null;
 const originalCatalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 const originalBattle=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
 const originalXp=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8')) as NormalXpPolicy;
 const owners=new Set<string>(),priorProfile=process.env.ROSTER60_PROFILE,priorStory=process.env.ROSTER60_STORY_PROFILE;
 const request=()=>randomUUID();
 const service=(client=db)=>new RosterPvp(client,()=>catalog,()=>battlePolicy,()=>now,()=>xpPolicy,async()=>60);
 const save=(name:string,value:unknown)=>writeFileSync(join(root,'data',name),JSON.stringify(value));
 type User={id:string;ids:string[];starting:bigint[]};
 async function user(levels=[1]):Promise<User>{const p=await db.player.create({data:{identity:'normal-xp:'+request()}});owners.add(p.id);const ids:string[]=[],starting:bigint[]=[];for(const [i,level] of levels.entries()){const def=catalog.characters.find((u:any)=>u.id==='u'+String(i+1).padStart(2,'0')),xp=BigInt(def.growth[level-1].totalXp);const u=await db.roster60Owned.create({data:{playerId:p.id,speciesId:def.id,totalXp:xp,source:request()}});ids.push(u.id);starting.push(xp);}await svc.party(p.id,ids);return {id:p.id,ids,starting};}
 async function pair(a:User,b:User,mode:'normal'|'rank'='normal'){
  expect((await svc.join(a.id,{requestId:request(),mode}) as any).status).toBe('waiting');const q:any=await svc.join(b.id,{requestId:request(),mode});expect(q.status).toBe('matched');
  await svc.accept(a.id,{requestId:request(),battleId:q.battle.battleId,revision:q.battle.revision,accept:true});await svc.accept(b.id,{requestId:request(),battleId:q.battle.battleId,revision:q.battle.revision,accept:true});return q.battle.battleId as string;
 }
 async function ai(a:User){
  expect((await svc.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');now+=30_000;const q:any=await svc.current(a.id);expect(q.battle).toMatchObject({mode:'normal',opponentKind:'AI'});await svc.accept(a.id,{requestId:request(),battleId:q.battle.battleId,revision:q.battle.revision,accept:true});return q.battle.battleId as string;
 }
 async function view(id:string){return (await svc.current(id) as any).battle;}
 async function rows(roomId:string){return db.roster60NormalXpLedger.findMany({where:{roomId},orderBy:{ownedId:'asc'}});}
 async function waitConnectedTurn(roomId:string,players:User[]){
  // 인증된 상태 조회만 유지한다. 턴 명령 없이 원래 제한 시각에 자동 입력 처리를 검사한다.
  const deadline=(await db.roster60PvpMatch.findUniqueOrThrow({where:{id:roomId},select:{deadline:true}})).deadline.getTime();
  expect(now).toBeLessThan(deadline);
  while(now+10_000<deadline){
   now+=10_000;
   for(const player of players){const observed:any=await svc.current(player.id);expect(observed.battle.status).toBe('ACTIVE');expect(observed.battle.presence.status).toBe('CONNECTED');}
  }
  now=deadline+1;
 }
 async function delta(u:User,index=0){return (await db.roster60Owned.findUniqueOrThrow({where:{id:u.ids[index]}})).totalXp-u.starting[index];}
 async function primeWin(roomId:string,winnerId:string){
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:roomId}}),s=JSON.parse(row.state),side=row.player0Id===winnerId?0:1,other=1-side;
  // 테스트 DB에서 결과를 짧게 확정한다. 별도 ATK 스탯이나 신규 전투 효과는 만들지 않는다.
  s.parties[side][0].stats.speed=1000;s.parties[other][0].stats.speed=1;s.parties[side][0].attack={...s.parties[side][0].attack,element:'NORMAL',mpCost:0,power:1_000_000};
  s.parties[other][0].defense.NORMAL='normal';s.parties[other][0].hp=1;for(const reserve of s.parties[other].slice(1))reserve.hp=0;
  if(s.botSide===1)s.pending[1]={kind:'attack'};
  await db.roster60PvpMatch.update({where:{id:roomId},data:{state:JSON.stringify(s)}});return row;
 }
 async function natural(roomId:string,winner:User,loser?:User){
  await primeWin(roomId,winner.id);const v=await view(winner.id),attack={requestId:request(),battleId:roomId,revision:v.revision,action:{kind:'attack' as const}};let result:any=await svc.action(winner.id,attack);let finalOwner=winner.id,finalAction:any=attack;
  if(loser){finalOwner=loser.id;finalAction={requestId:request(),battleId:roomId,revision:v.revision,action:{kind:'focus' as const}};result=await svc.action(loser.id,finalAction);}
  return {winner:await view(winner.id),loser:loser?await view(loser.id):null,receipt:result,finalOwner,finalAction};
 }
 async function ack(id:string,battle:any){const body={requestId:request(),battleId:battle.battleId,revision:battle.revision};const first=await svc.settle(id,body);expect(await svc.settle(id,body)).toEqual(first);await svc.settle(id,{...body,requestId:request()});return first as any;}
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});root=mkdtempSync(join(tmpdir(),'normal-xp-api-'));mkdirSync(join(root,'data'));
  delete process.env.ROSTER60_PROFILE;delete process.env.ROSTER60_STORY_PROFILE;
 });
 beforeEach(()=>{
  catalog=structuredClone(originalCatalog);battlePolicy=structuredClone(originalBattle);xpPolicy=structuredClone(originalXp);now=Date.now();svc=service();save('roster60-approved.json',catalog);save('roster60-battle-policy.json',battlePolicy);save('roster60-normal-xp-policy.json',xpPolicy);
  app=Fastify();registerRoster60(app,db,root,r=>owners.has(String(r.headers.authorization))?String(r.headers.authorization):null,()=>true);
 });
 afterEach(async()=>{
  await app.close();const ids=[...owners];await db.roster60PvpMatch.deleteMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});await db.playerItem.deleteMany({where:{playerId:{in:ids}}});await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});await db.dailyProgress.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});owners.clear();
 });
 afterAll(async()=>{await db.$disconnect();rmSync(root,{recursive:true,force:true});if(priorProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=priorProfile;if(priorStory===undefined)delete process.env.ROSTER60_STORY_PROFILE;else process.env.ROSTER60_STORY_PROFILE=priorStory;});

 it('명시 정책 없이는 일반게임 입장 전에 닫히며 클라이언트 XP 입력과 미정 랭크계정레벨을 거절한다',async()=>{
  const a=await user();unlinkSync(join(root,'data/roster60-normal-xp-policy.json'));
  const post=(path:string,body:any)=>app.inject({method:'POST',url:'/my/api/native/roster60/'+path,headers:{authorization:a.id},payload:body});
  expect((await post('pvp/join',{requestId:request(),mode:'normal',xpAwarded:9999})).statusCode).toBe(400);
  const denied=await post('pvp/join',{requestId:request(),mode:'normal'});expect(denied.statusCode).toBe(503);expect(denied.json().error).toBe('NORMAL_XP_POLICY_PENDING');expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).status).toBe('AVAILABLE');expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);
  const rank=await post('pvp/join',{requestId:request(),mode:'rank'});expect(rank.statusCode).toBe(503);expect(rank.json()).toMatchObject({error:'ACCOUNT_LEVEL_POLICY_PENDING'});expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await post('pvp/cancel',{requestId:request()})).statusCode).toBe(200);expect(await delta(a)).toBe(0n);expect(await db.roster60NormalXpLedger.count({where:{playerId:a.id}})).toBe(0);
 });

 it('사람 일반게임의 정상 승리·정상 패배는 각각 양의 XP를 한 번 지급하고 본인 단위 DTO만 공개한다',async()=>{
  const a=await user([1,1]),b=await user([1,1]),room=await pair(a,b);const done=await natural(room,a,b);
  expect(done.winner.state.outcome).toBe('win');expect(done.loser.state.outcome).toBe('loss');expect(done.winner.settlement).toMatchObject({xpAwarded:60,xpDeferred:0});expect(done.loser.settlement).toMatchObject({xpAwarded:30,xpDeferred:0});
  expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);expect(await delta(a,1)).toBe(0n);expect(await delta(b,1)).toBe(0n);
  for(const [u,opponent,battle] of [[a,b,done.winner],[b,a,done.loser]] as const){
   expect(battle.settlement.normalXp.units.map((unit:any)=>unit.ownedId).sort()).toEqual([...u.ids].sort());const visible=JSON.stringify(battle.settlement);for(const id of opponent.ids)expect(visible).not.toContain(id);expect(visible).not.toContain('startingXp');expect(visible).not.toContain('growth');await ack(u.id,battle);
  }
  expect(await svc.action(done.finalOwner,done.finalAction)).toEqual(done.receipt);expect(await rows(room)).toHaveLength(4);expect((await rows(room)).filter(r=>r.status==='NOT_ENTERED')).toHaveLength(2);expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);
 });

 it('AI와 사람의 동일 결과·동일 시작 레벨은 같은 XP 기준을 쓰고 가짜 AI 계정에는 기록하지 않는다',async()=>{
  battlePolicy.encounters=battlePolicy.encounters.filter((e:any)=>e.mode==='duel'&&e.level===1).map((e:any)=>({...e,enemySpeciesIds:['u07']}));
  const winner=await user(),winRoom=await ai(winner),win=await natural(winRoom,winner);expect(win.winner.settlement).toMatchObject({xpAwarded:60,rating:null});expect(await delta(winner)).toBe(60n);await ack(winner.id,win.winner);
  const loser=await user(),lossRoom=await ai(loser),row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:lossRoom}}),s=JSON.parse(row.state);s.parties[0][0].hp=1;s.parties[0][0].stats.speed=1;s.parties[1][0].stats.speed=1000;s.parties[1][0].attack={...s.parties[1][0].attack,power:1_000_000,mpCost:0};s.pending[1]={kind:'attack'};await db.roster60PvpMatch.update({where:{id:lossRoom},data:{state:JSON.stringify(s)}});
  const v=await view(loser.id),loss:any=await svc.action(loser.id,{requestId:request(),battleId:lossRoom,revision:v.revision,action:{kind:'focus'}});expect(loss.state.outcome).toBe('loss');expect(loss.settlement).toMatchObject({xpAwarded:30,rating:null});expect(await delta(loser)).toBe(30n);await ack(loser.id,loss);
  for(const id of [winRoom,lossRoom]){const decisions=await rows(id);expect(decisions).toHaveLength(1);expect(decisions[0].playerId).not.toMatch(/^AI:/);expect(decisions[0].ownedId).not.toMatch(/^AI:/);}expect(await db.player.count({where:{id:{startsWith:'AI:'}}})).toBe(0);
 });

 it('도주·항복은 정상 패배로 바뀌지 않고 XP 0이며 랭크 정산에는 일반게임 XP를 넣지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b),v=await view(a.id);const escaped:any=await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'escape'}});expect(escaped.settlement).toMatchObject({reason:'SURRENDER',xpAwarded:0});expect(await delta(a)).toBe(0n);expect(await delta(b)).toBe(0n);expect((await rows(room)).every(r=>r.status==='SURRENDER_ZERO'&&r.requestedXp===0&&r.appliedXp===0)).toBe(true);await ack(a.id,escaped);await ack(b.id,await view(b.id));
  const c=await user([60]),d=await user([60]),rankRoom=await pair(c,d,'rank'),rank=await natural(rankRoom,c,d);expect(rank.winner.settlement.xpAwarded).toBe(0);expect(rank.winner.settlement.normalXp).toBeUndefined();expect(rank.loser.settlement.normalXp).toBeUndefined();expect(await rows(rankRoom)).toHaveLength(0);expect(await delta(c)).toBe(0n);expect(await delta(d)).toBe(0n);
 });

 it('실제 자발 교체·강제 교체로 출전한 개체에게 계정총량을 균등분배하고 미출전 예비는 제외한다',async()=>{
  const a=await user([1,5,10,1]),b=await user(),room=await pair(a,b);let v=await view(a.id);
  await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:a.ids[1]}});await svc.action(b.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'focus'}});
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}}),s=JSON.parse(row.state),side=row.player0Id===a.id?0:1;s.parties[side][0].hp=0;await db.roster60PvpMatch.update({where:{id:room},data:{state:JSON.stringify(s)}});v=await view(a.id);
  await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:a.ids[2]}});const done=await natural(room,a,b);
  expect(done.winner.settlement.xpByOwnedId).toEqual({[a.ids[0]]:200,[a.ids[1]]:200,[a.ids[2]]:200,[a.ids[3]]:0});expect(done.winner.settlement.xpRequested).toBe(600);for(const [i,xp] of [200n,200n,200n,0n].entries())expect(await delta(a,i)).toBe(xp);
  expect((await rows(room)).find(r=>r.ownedId===a.ids[3])).toMatchObject({status:'NOT_ENTERED',appliedXp:0,requestedXp:0});
 });

 it('입장 때 고정한 양과 성장 곡선으로 정산하며 정책 파일 제거·같은 버전 수치 교체가 과거 방을 바꾸지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);xpPolicy=null;unlinkSync(join(root,'data/roster60-normal-xp-policy.json'));battlePolicy=structuredClone(battlePolicy);battlePolicy.encounters.forEach((e:any)=>{if(e.mode==='duel'){e.winXp=999999;e.lossXp=999999;}});catalog=structuredClone(catalog);catalog.characters[0].growth[1].totalXp=1;
  const done=await natural(room,a,b);expect(done.winner.settlement.xpAwarded).toBe(60);expect(done.loser.settlement.xpAwarded).toBe(30);expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);await ack(a.id,done.winner);await ack(b.id,done.loser);
 });

 it('대기열 입장 뒤 정책이 없어져도 이미 고정한 AI 경기 XP를 적용하며 새 입장은 닫는다',async()=>{
  battlePolicy.encounters=battlePolicy.encounters.filter((e:any)=>e.mode==='duel'&&e.level===1).map((e:any)=>({...e,enemySpeciesIds:['u07']}));const a=await user();expect((await svc.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');
  xpPolicy=null;unlinkSync(join(root,'data/roster60-normal-xp-policy.json'));battlePolicy=structuredClone(battlePolicy);battlePolicy.encounters[0].winXp=999999;const b=await user();await expect(svc.join(b.id,{requestId:request(),mode:'normal'})).rejects.toMatchObject({code:'NORMAL_XP_POLICY_PENDING'});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:b.ids[0]}})).status).toBe('AVAILABLE');
  now+=30_000;const matched:any=await svc.current(a.id);expect(matched.battle.opponentKind).toBe('AI');await svc.accept(a.id,{requestId:request(),battleId:matched.battle.battleId,revision:matched.battle.revision,accept:true});const done=await natural(matched.battle.battleId,a);expect(done.winner.settlement.xpAwarded).toBe(60);expect(await delta(a)).toBe(60n);expect((await rows(matched.battle.battleId))[0].requestedXp).toBe(60);
 });

 it('현재 카탈로그가 누락되거나 읽기 예외여도 고정된 방의 조회·행동·XP 정산과 다른 대기자 취소·tick은 유지된다',async()=>{
  for(const kind of ['missing','throws'] as const){
   svc=service();const a=await user(),b=await user(),room=await pair(a,b),waiter=await user();await svc.join(waiter.id,{requestId:request(),mode:'normal'});let reads=0;
   svc=new RosterPvp(db,()=>{reads++;if(kind==='missing')return null;throw Error('normal-xp-catalog-read-test-only');},()=>battlePolicy,()=>now,()=>xpPolicy,async()=>60);
   expect((await view(a.id)).status).toBe('ACTIVE');expect(await svc.current(waiter.id)).toMatchObject({status:'waiting',normalXpCondition:'NORMAL_XP_CATALOG_CHANGED',cancelAllowed:true});
   await waitConnectedTurn(room,[a,b]);await svc.tick();expect(JSON.parse((await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}})).state).turn).toBe(1);expect((await svc.cancel(waiter.id,{requestId:request()}) as any).status).toBe('idle');expect((await db.roster60Owned.findUniqueOrThrow({where:{id:waiter.ids[0]}})).status).toBe('AVAILABLE');
   const done=await natural(room,a,b);expect(done.winner.settlement).toMatchObject({reason:'BATTLE',xpAwarded:60,normalXp:{status:'APPLIED'}});expect(done.loser.settlement.xpAwarded).toBe(30);expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);await ack(a.id,done.winner);await ack(b.id,done.loser);expect(await rows(room)).toHaveLength(2);expect(reads).toBeGreaterThan(0);
  }
 },15000);

 it('대기 중 카탈로그 버전이 바뀌면 잠기는 새 방을 만들지 않고 취소로 개체를 돌려준다',async()=>{
  const c=await user(),d=await user(),otherRoom=await pair(c,d);const a=await user();await svc.join(a.id,{requestId:request(),mode:'normal'});catalog={...catalog,version:catalog.version+'-changed'};battlePolicy={...battlePolicy,catalogVersion:catalog.version};await waitConnectedTurn(otherRoom,[c,d]);
  expect(await svc.current(a.id)).toMatchObject({status:'waiting',normalXpCondition:'NORMAL_XP_CATALOG_CHANGED',cancelAllowed:true});expect(await db.roster60PvpMatch.count({where:{player0Id:a.id}})).toBe(0);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).status).toBe('PVP_QUEUE');await svc.tick();expect(JSON.parse((await db.roster60PvpMatch.findUniqueOrThrow({where:{id:otherRoom}})).state).turn).toBeGreaterThan(0);await svc.cancel(a.id,{requestId:request()});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).status).toBe('AVAILABLE');expect(await db.roster60NormalXpLedger.count({where:{playerId:a.id}})).toBe(0);
 });

 it('고정 XP 계획이 없는 과거 대기열로 새 방을 만들지 않고 취소·재입장을 안내한다',async()=>{
  const a=await user();await svc.join(a.id,{requestId:request(),mode:'normal'});await db.roster60PvpQueue.update({where:{playerId:a.id},data:{normalXpSnapshot:null}});now+=30_000;
  expect(await svc.current(a.id)).toMatchObject({status:'waiting',normalXpCondition:'NORMAL_XP_POLICY_PENDING',cancelAllowed:true});expect(await db.roster60PvpMatch.count({where:{player0Id:a.id}})).toBe(0);await svc.cancel(a.id,{requestId:request()});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).status).toBe('AVAILABLE');expect((await svc.join(a.id,{requestId:request(),mode:'normal'}) as any).normalXpCondition).toBeNull();expect((await db.roster60PvpQueue.findUniqueOrThrow({where:{playerId:a.id}})).normalXpSnapshot).not.toBeNull();
 });

 it('이전 버전 큐가 검색 한도보다 많아도 SQL에서 먼저 제외하여 정상 큐의 AI 매칭을 막지 않는다',async()=>{
  const a=await user();await svc.join(a.id,{requestId:request(),mode:'normal'});const prefix='normal-xp-stale-fixture:'+request()+':',oldVersion=catalog.version+'-old';
  // 큐 테이블의 검색 경계만 검사하는 임시 행이다. 실제 계정·개체나 AI 플레이어를 만들지 않는다.
  const stale=Array.from({length:REALTIME_LIMITS.maxWaiting+1},(_,i)=>({playerId:prefix+i,mode:'normal',rating:1000,tierIndex:0,ownedIds:'[]',queuedAt:new Date(now-31_000),leaseUntil:new Date(now+120_000),normalXpCatalogVersion:oldVersion,normalXpSnapshot:JSON.stringify({playerId:prefix+i,policy:{...originalXp,catalogVersion:oldVersion},members:[]})}));
  try{await db.roster60PvpQueue.createMany({data:stale});now+=30_000;const matched:any=await svc.current(a.id);expect(matched.battle).toMatchObject({mode:'normal',opponentKind:'AI'});expect(await db.roster60PvpMatch.count({where:{player0Id:a.id}})).toBe(1);expect(await db.roster60PvpQueue.count({where:{playerId:{startsWith:prefix}}})).toBe(REALTIME_LIMITS.maxWaiting+1);}
  finally{await db.roster60PvpQueue.deleteMany({where:{playerId:{startsWith:prefix}}});}
 });

 it('6개 실제 출전은 홀수 총량을 ID순으로 균등분배하고 재접속·중복정산에도 늘어나지 않는다',async()=>{
  const source=battlePolicy.encounters.find((e:any)=>e.mode==='duel'&&!e.tutorial&&e.level===1);source.winXp=61;source.lossXp=7;
  const a=await user([1,1,1,1,1,1]),b=await user(),room=await pair(a,b);
  for(let index=1;index<a.ids.length;index++){
   const v=await view(a.id);await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:a.ids[index]}});await svc.action(b.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'focus'}});
  }
  const before=await rows(room),v=await view(a.id);await expect(svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:b.ids[0]}})).rejects.toMatchObject({code:'INVALID_SWITCH'});expect(await rows(room)).toEqual(before);
  svc=service();const done=await natural(room,a,b),ordered=[...a.ids].sort(),grants=ordered.map(id=>done.winner.settlement.xpByOwnedId[id]);expect(grants).toEqual([11,10,10,10,10,10]);expect(done.winner.settlement).toMatchObject({xpAwarded:61,xpRequested:61,xpDeferred:0,xpDiscarded:0});expect(done.loser.settlement.xpAwarded).toBe(30);
  for(const [index,id] of a.ids.entries())expect(await delta(a,index)).toBe(id===ordered[0]?11n:10n);await ack(a.id,done.winner);await ack(b.id,done.loser);expect(await svc.action(done.finalOwner,done.finalAction)).toEqual(done.receipt);expect(await rows(room)).toHaveLength(7);
 });

 it('한 참가자의 상한 초과분은 버리고 다른 참가자의 균등 몫에 다시 나누지 않는다',async()=>{
  const a=await user([59,1]),b=await user(),cap=BigInt(catalog.characters.find((u:any)=>u.id==='u01').growth[59].totalXp);a.starting[0]=cap-5n;await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:a.starting[0]}});const room=await pair(a,b),v=await view(a.id);
  await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:a.ids[1]}});await svc.action(b.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'focus'}});const done=await natural(room,a,b);
  expect(done.winner.settlement).toMatchObject({xpRequested:3600,xpAwarded:1805,xpDiscarded:1795,xpDeferred:0,normalXp:{status:'CAP_CLAMPED'}});expect(done.winner.settlement.xpByOwnedId).toEqual({[a.ids[0]]:5,[a.ids[1]]:1800});expect(await delta(a,0)).toBe(5n);expect(await delta(a,1)).toBe(1800n);expect((await rows(room)).filter(r=>r.playerId===a.id).every(r=>r.requestedXp===1800&&r.deferredXp===0)).toBe(true);
 });

 it('고정된 v1 큐는 원천과 성장곡선을 보존하되 새 비율·균등·상한 규칙으로 AI 정산한다',async()=>{
  const a=await user([1,1]);await svc.join(a.id,{requestId:request(),mode:'normal'});const queue=await db.roster60PvpQueue.findUniqueOrThrow({where:{playerId:a.id}}),plan=JSON.parse(queue.normalXpSnapshot!);for(const field of ['rulesVersion','outcomePercent','distribution','poolBasis','remainder'])delete plan.policy[field];plan.policy.version='normal-completion-xp-test-v1';plan.policy.eligibleReason='BATTLE';plan.policy.timeout='undecided-no-award';plan.policy.disconnect='undecided-no-award';plan.policy.capOverflow='pending-policy-ledger';plan.policy.lossFallback={numerator:1,denominator:2,minimum:1};for(const m of plan.members)m.lossXp=20;
  await db.roster60PvpQueue.update({where:{playerId:a.id},data:{normalXpSnapshot:JSON.stringify(plan)}});xpPolicy=null;unlinkSync(join(root,'data/roster60-normal-xp-policy.json'));battlePolicy.encounters.forEach((e:any)=>{if(e.mode==='duel'){e.winXp=999999;e.lossXp=1;}});now+=30_000;svc=service();const matched:any=await svc.current(a.id),room=matched.battle.battleId;await svc.accept(a.id,{requestId:request(),battleId:room,revision:matched.battle.revision,accept:true});const v=await view(a.id);await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'switch',targetOwnedId:a.ids[1]}});const done=await natural(room,a);
  expect(done.winner.settlement).toMatchObject({xpRequested:60,xpAwarded:60,xpDeferred:0,xpDiscarded:0,normalXp:{policyVersion:'normal-completion-xp-test-v1:confirmed-v2'}});expect(done.winner.settlement.xpByOwnedId).toEqual({[a.ids[0]]:30,[a.ids[1]]:30});expect((await rows(room)).every(r=>r.policyVersion==='normal-completion-xp-test-v1:confirmed-v2')).toBe(true);await ack(a.id,done.winner);expect(await svc.action(done.finalOwner,done.finalAction)).toEqual(done.receipt);
 });

 it('미정산 v1 방의 양수 lossXp20도 새50%30으로 바꾸되 원천win60은 바꾸지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b),row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}}),snapshot=JSON.parse(row.snapshot);for(const plan of snapshot.normalXp.players){for(const field of ['rulesVersion','outcomePercent','distribution','poolBasis','remainder'])delete plan.policy[field];plan.policy.version='normal-completion-xp-test-v1';plan.policy.eligibleReason='BATTLE';plan.policy.timeout='undecided-no-award';plan.policy.disconnect='undecided-no-award';plan.policy.capOverflow='pending-policy-ledger';plan.policy.lossFallback={numerator:1,denominator:2,minimum:1};for(const m of plan.members)m.lossXp=20;}
  await db.roster60PvpMatch.update({where:{id:room},data:{snapshot:JSON.stringify(snapshot)}});svc=service();const done=await natural(room,a,b);expect(done.winner.settlement.xpAwarded).toBe(60);expect(done.loser.settlement.xpAwarded).toBe(30);expect(done.loser.settlement.normalXp.policyVersion).toBe('normal-completion-xp-test-v1:confirmed-v2');await ack(a.id,done.winner);await ack(b.id,done.loser);expect(await delta(b)).toBe(30n);
 });

 it('최대 레벨 초과 XP는 버리고 보류없이 해제하며 나중에 소급 지급하거나 자동 환생하지 않는다',async()=>{
  const a=await user([60]),b=await user(),room=await pair(a,b),done=await natural(room,a,b);const original=(await rows(room)).find(r=>r.playerId===a.id)!;expect(original).toMatchObject({status:'CAP_CLAMPED',appliedXp:0,deferredXp:0,requestedXp:3600});expect(done.winner.settlement).toMatchObject({xpAwarded:0,xpDeferred:0,xpDiscarded:3600,normalXp:{status:'CAP_CLAMPED',deferredIsAutomaticReward:false}});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).reincarnation).toBe(0);expect(await delta(a)).toBe(0n);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).status).toBe('AVAILABLE');
  const lower=BigInt(catalog.characters[0].growth[58].totalXp);await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:lower}});await ack(a.id,done.winner);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).totalXp).toBe(lower);expect((await rows(room)).find(r=>r.playerId===a.id)).toEqual(original);
 });

 it('서로 다른 DB 클라이언트의 같은 KO 요청 재시도도 계정·방·개체별 지급을 한 번만 확정한다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);await primeWin(room,a.id);const v=await view(a.id);await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'attack'}});const otherDb=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});
  try{const other=service(otherDb),body={requestId:request(),battleId:room,revision:v.revision,action:{kind:'focus' as const}};const both=await Promise.all([svc.action(b.id,body),other.action(b.id,body)]);expect(both[0]).toEqual(both[1]);expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);expect(await rows(room)).toHaveLength(2);}
  finally{await otherDb.$disconnect();}
 });

 it('XP 이력 쓰기 실패는 전투·잔액·개체·양쪽 지급을 함께 롤백하고 같은 요청으로 복구한다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);await primeWin(room,a.id);const v=await view(a.id);await svc.action(a.id,{requestId:request(),battleId:room,revision:v.revision,action:{kind:'attack'}});const before=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}}),beforePlayers=await db.player.findMany({where:{id:{in:[a.id,b.id]}},orderBy:{id:'asc'}}),beforeRatings=await db.casualHumanRating.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}}),body={requestId:request(),battleId:room,revision:v.revision,action:{kind:'focus' as const}};
  await db.$executeRawUnsafe(`CREATE TRIGGER normal_xp_test_failure BEFORE INSERT ON Roster60NormalXpLedger WHEN NEW.roomId='${room}' BEGIN SELECT RAISE(ABORT, 'normal-xp-test-only-failure'); END`);
  try{await expect(svc.action(b.id,body)).rejects.toThrow();expect(await rows(room)).toHaveLength(0);expect(await delta(a)).toBe(0n);expect(await delta(b)).toBe(0n);expect(await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}})).toEqual(before);expect(await db.player.findMany({where:{id:{in:[a.id,b.id]}},orderBy:{id:'asc'}})).toEqual(beforePlayers);expect(await db.casualHumanRating.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}})).toEqual(beforeRatings);expect(await db.roster60Owned.count({where:{id:{in:[...a.ids,...b.ids]},status:'PVP'}})).toBe(2);expect(await db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:b.id,requestId:body.requestId}}})).toBeNull();}
  finally{await db.$executeRawUnsafe('DROP TRIGGER normal_xp_test_failure');}
  const done:any=await svc.action(b.id,body);expect(done.state.outcome).toBe('loss');expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);expect(await rows(room)).toHaveLength(2);
 });

 it('기존 방에 고정 XP 계획이 없으면 새 설정을 소급하지 않고 미정 무승부·타임아웃도 확정 XP로 확대하지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b),row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}}),snapshot=JSON.parse(row.snapshot);delete snapshot.normalXp;await db.roster60PvpMatch.update({where:{id:room},data:{snapshot:JSON.stringify(snapshot)}});const done=await natural(room,a,b);expect(done.winner.settlement).toMatchObject({xpAwarded:0,normalXp:{status:'LEGACY_POLICY_UNCONFIGURED'}});expect(await delta(a)).toBe(0n);expect(await delta(b)).toBe(0n);
  const legacy=await user(),configured=await user(),mixedRoom=await pair(legacy,configured),mixed=JSON.parse((await db.roster60PvpMatch.findUniqueOrThrow({where:{id:mixedRoom}})).snapshot);mixed.normalXp.players=mixed.normalXp.players.filter((p:any)=>p.playerId!==legacy.id);await db.roster60PvpMatch.update({where:{id:mixedRoom},data:{snapshot:JSON.stringify(mixed)}});const mixedDone=await natural(mixedRoom,legacy,configured);expect(mixedDone.winner.settlement).toMatchObject({xpAwarded:0,normalXp:{status:'LEGACY_POLICY_UNCONFIGURED'}});expect(mixedDone.loser.settlement).toMatchObject({xpAwarded:30,normalXp:{status:'APPLIED'}});expect(await delta(legacy)).toBe(0n);expect(await delta(configured)).toBe(30n);
  const c=await user(),d=await user();battlePolicy={...battlePolicy,maxRounds:1};const drawRoom=await pair(c,d),v=await view(c.id);await svc.action(c.id,{requestId:request(),battleId:drawRoom,revision:v.revision,action:{kind:'focus'}});const draw:any=await svc.action(d.id,{requestId:request(),battleId:drawRoom,revision:v.revision,action:{kind:'focus'}});expect(draw.settlement).toMatchObject({xpAwarded:0,normalXp:{status:'DRAW_POLICY_PENDING'}});expect(await delta(c)).toBe(0n);expect(await delta(d)).toBe(0n);
  battlePolicy={...battlePolicy,maxRounds:originalBattle.maxRounds};const e=await user(),f=await user(),timeoutRoom=await pair(e,f);for(let i=0;i<3;i++){await waitConnectedTurn(timeoutRoom,[e,f]);await svc.tick();}const timeout=await view(e.id);expect(timeout.settlement).toMatchObject({reason:'DOUBLE_TIMEOUT',xpAwarded:0,normalXp:{status:'TERMINAL_POLICY_PENDING'}});expect(JSON.parse((await db.roster60PvpMatch.findUniqueOrThrow({where:{id:timeoutRoom}})).state).misses).toEqual([3,3]);expect(await rows(timeoutRoom)).toHaveLength(2);expect(await delta(e)).toBe(0n);expect(await delta(f)).toBe(0n);
 });

 it.each([1,2])('%i번째 미입력 자동 행동에서 KO가 나도 정상 완료 XP나 새우Gang으로 바뀌지 않는다',async miss=>{
  const a=await user(),b=await user();for(const id of [a.id,b.id])await db.roster60StoryAccount.create({data:{playerId:id,contentVersion:'normal-xp-timeout-fixture',state:JSON.stringify(initialStoryProgress()),darkGangBalance:3,darkGangLifetime:3}});const before=await db.roster60StoryAccount.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}}),room=await pair(a,b);
  for(let i=1;i<miss;i++){await waitConnectedTurn(room,[a,b]);await svc.tick();expect((await view(a.id)).status).toBe('ACTIVE');}
  await primeWin(room,a.id);await waitConnectedTurn(room,[a,b]);await svc.tick();const outcome=await view(a.id);expect(outcome.state.outcome).toBe('win');expect(outcome.settlement).toMatchObject({reason:'BATTLE',xpAwarded:0,normalXp:{status:'TIMEOUT_RESOLUTION_POLICY_PENDING'}});expect((await view(b.id)).settlement.xpAwarded).toBe(0);expect(await delta(a)).toBe(0n);expect(await delta(b)).toBe(0n);expect((await rows(room)).every(r=>r.requestedXp===0&&r.appliedXp===0)).toBe(true);expect(await db.roster60StoryAccount.findMany({where:{playerId:{in:[a.id,b.id]}},orderBy:{playerId:'asc'}})).toEqual(before);expect(await db.roster60StoryBattleAward.count({where:{playerId:{in:[a.id,b.id]}}})).toBe(0);
 });

 it('앞선 미입력 턴이 있어도 마지막 턴을 양쪽이 수동 완료하면 정상 승패 XP를 지급한다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);await waitConnectedTurn(room,[a,b]);await svc.tick();expect((await view(a.id)).status).toBe('ACTIVE');const done=await natural(room,a,b);expect(done.winner.settlement).toMatchObject({reason:'BATTLE',xpAwarded:60,normalXp:{status:'APPLIED'}});expect(done.loser.settlement.xpAwarded).toBe(30);expect(await delta(a)).toBe(60n);expect(await delta(b)).toBe(30n);
 });
});
