import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterPvp} from '../src/roster60-pvp.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const battlePolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const normalXpPolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
const poolVersion='account-rebirth-history-count-normal-rank-v2';

suite('일반게임·랭크의 영구 환생 계정 풀과 기존 방 보존 경계',()=>{
 let db:PrismaClient,svc:RosterPvp,now:number;const owners=new Set<string>();
 const request=()=>randomUUID();
 type User={id:string;ids:string[];startingXp:bigint[]};
 async function user(reborn=false):Promise<User>{
  const p=await db.player.create({data:{identity:'match-pool:'+request()}});owners.add(p.id);
  const unit=await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',totalXp:0n,source:request()}});
  if(reborn)await history(p.id);await svc.party(p.id,[unit.id]);return {id:p.id,ids:[unit.id],startingXp:[unit.totalXp]};
 }
 async function history(playerId:string){await db.roster60RebirthAccount.upsert({where:{playerId},create:{playerId,successes:1},update:{successes:1}});}
 async function join(a:User){return svc.join(a.id,{requestId:request(),mode:'normal'}) as Promise<any>;}
 async function pair(a:User,b:User){expect((await join(a)).status).toBe('waiting');const result=await join(b);expect(result.status).toBe('matched');return result.battle;}
 async function units(a:User){return db.roster60Owned.findMany({where:{playerId:a.id},orderBy:{id:'asc'}});}
 async function roomCount(a:User){return db.roster60PvpMatch.count({where:{OR:[{player0Id:a.id},{player1Id:a.id}]}});}
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});
 });
 beforeEach(()=>{now=Date.now();svc=new RosterPvp(db,()=>catalog,()=>battlePolicy,()=>now,()=>normalXpPolicy);});
 afterEach(async()=>{
  const ids=[...owners],rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]},select:{id:true}}),roomIds=rooms.map(r=>r.id);
  await db.rankedSettlement.deleteMany({where:{roomId:{in:roomIds}}});await db.rankedMatch.deleteMany({where:{roomId:{in:roomIds}}});await db.casualHumanSettlement.deleteMany({where:{roomId:{in:roomIds}}});
  await db.roster60PvpMatch.deleteMany({where:{id:{in:roomIds}}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});await db.progressionState.deleteMany({where:{playerId:{in:ids}}});
  await db.rankedProfile.deleteMany({where:{playerId:{in:ids}}});await db.casualHumanRating.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});owners.clear();
 });
 afterAll(async()=>{await db.$disconnect();});

 it('낮은 개체·미정 계정 레벨도 같은 환생 이력 계정끼리 일반게임에 참가하지만 다른 풀은 합치지 않는다',async()=>{
  const first=await user(),reborn=await user(true),otherReborn=await user(true);
  expect((await join(first)).status).toBe('waiting');expect((await join(reborn)).status).toBe('waiting');const result=await join(otherReborn);expect(result.status).toBe('matched');
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:result.battle.battleId}}),snapshot=JSON.parse(row.snapshot);
  expect(new Set([row.player0Id,row.player1Id])).toEqual(new Set([reborn.id,otherReborn.id]));expect(result.battle.state.fighters[0].level).toBe(1);expect(snapshot.matchPool).toMatchObject({version:poolVersion});
  expect(snapshot.matchPool.players.every((p:any)=>p.pool==='REBORN')).toBe(true);expect((await svc.current(first.id) as any).status).toBe('waiting');
 });

 it('비환생·환생 각 풀의 단독 대기는 30초 서버 AI 흐름을 유지하고 가짜 AI 계정에 풀 근거를 만들지 않는다',async()=>{
  const first=await user(),reborn=await user(true);await join(first);await join(reborn);now+=29_999;
  expect((await svc.current(first.id) as any).status).toBe('waiting');expect((await svc.current(reborn.id) as any).status).toBe('waiting');now++;
  for(const [a,pool] of [[first,'FIRST_LIFE'],[reborn,'REBORN']] as const){
   const result:any=await svc.current(a.id);expect(result.status).toBe('matched');expect(result.battle).toMatchObject({mode:'normal',opponentKind:'AI'});
   const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:result.battle.battleId}}),snapshot=JSON.parse(row.snapshot);expect(snapshot.matchPool).toEqual({version:poolVersion,players:[{playerId:a.id,pool,count:pool==='FIRST_LIFE'?0:1,countSource:pool==='FIRST_LIFE'?'NO_REBIRTH_HISTORY':'ROSTER60_ACCOUNT_SUCCESSES',historyStamp:expect.stringMatching(/^[a-f0-9]{64}$/)}]});
   expect(await db.roster60RebirthAccount.count({where:{playerId:row.player1Id}})).toBe(0);await svc.accept(a.id,{requestId:request(),battleId:row.id,revision:row.revision,accept:true});expect((await svc.current(a.id) as any).battle.status).toBe('ACTIVE');
  }
 });

 it('소비된 환생 개체만 남은 계정도 현재 편성의 비환생 외형으로 다른 풀에 들어가지 않는다',async()=>{
  const a=await user(),b=await user(true);await db.roster60Owned.create({data:{playerId:a.id,speciesId:'u02',reincarnation:1,status:'CONSUMED',source:request()}});
  expect((await units(a)).filter(u=>a.ids.includes(u.id)).every(u=>u.reincarnation===0)).toBe(true);const room=await pair(a,b);
  expect(JSON.parse((await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room.battleId}})).snapshot).matchPool.players.every((p:any)=>p.pool==='REBORN')).toBe(true);
 });

 it('XP 계획만 있고 풀 고정값이 없는 기존 일반게임 큐는 새 사람·AI 방을 만들지 않고 취소할 수 있다',async()=>{
  const a=await user(),b=await user();await join(a);await db.roster60PvpQueue.update({where:{playerId:a.id},data:{matchPoolPolicyVersion:null,reincarnationPool:null}});
  expect((await join(b)).status).toBe('waiting');now+=30_000;expect(await svc.current(a.id)).toMatchObject({status:'waiting',matchPoolCondition:'MATCH_POOL_QUEUE_POLICY_PENDING',cancelAllowed:true});expect(await roomCount(a)).toBe(0);
  await svc.cancel(a.id,{requestId:request()});expect((await units(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('대기 중 이력이 바뀌면 고정 풀과 다른 신규 상대·30초 AI에 붙이지 않고 취소·재입장을 안내한다',async()=>{
  const a=await user(),b=await user();await join(a);await history(a.id);expect((await join(b)).status).toBe('waiting');
  expect(await svc.current(a.id)).toMatchObject({status:'waiting',matchPoolCondition:'MATCH_POOL_CLASS_CHANGED',cancelAllowed:true});now+=30_000;expect((await svc.current(a.id) as any).status).toBe('waiting');expect(await roomCount(a)).toBe(0);
  await svc.cancel(a.id,{requestId:request()});expect((await units(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('손상된 과거 환생 근거는 비환생으로 보완하지 않고 일반게임 큐 잠금 전에 분류를 보류한다',async()=>{
  const a=await user();await db.roster60RebirthReceipt.create({data:{playerId:a.id,requestId:request(),fingerprint:'test-only-uncertain',result:'not-json'}});
  await expect(join(a)).rejects.toMatchObject({code:'MATCH_POOL_POLICY_PENDING'});expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await units(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('사람 일반게임의 수락 전 이력이 바뀌면 양쪽 XP·평점을 지급하지 않고 대원만 해제한다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);await history(a.id);const result:any=await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});
  expect(result.status).toBe('FINISHED');expect(result.settlement).toMatchObject({reason:'SERVER_ABORT',xpAwarded:0,rating:null,matchPoolCondition:'MATCH_POOL_CLASS_CHANGED'});
  const other:any=(await svc.current(b.id) as any).battle;expect(other.settlement.xpAwarded).toBe(0);expect(other.settlement.matchPoolCondition).toBe('MATCH_POOL_OPPONENT_CHANGED');
  expect((await units(a)).every(u=>u.status==='AVAILABLE'&&u.totalXp===0n)).toBe(true);expect((await units(b)).every(u=>u.status==='AVAILABLE'&&u.totalXp===0n)).toBe(true);expect(await db.casualHumanSettlement.count({where:{roomId:room.battleId}})).toBe(0);
  expect((await db.roster60NormalXpLedger.findMany({where:{roomId:room.battleId}})).every(row=>row.appliedXp===0)).toBe(true);expect(await db.roster60StoryBattleAward.count({where:{playerId:{in:[a.id,b.id]}}})).toBe(0);
 });

 it('AI 일반게임도 수락 직전 이력을 다시 확인하여 잘못된 풀의 전투를 시작하지 않는다',async()=>{
  const a=await user();await join(a);now+=30_000;const matched:any=await svc.current(a.id);await history(a.id);
  const result:any=await svc.accept(a.id,{requestId:request(),battleId:matched.battle.battleId,revision:matched.battle.revision,accept:true});expect(result.status).toBe('FINISHED');expect(result.settlement).toMatchObject({reason:'SERVER_ABORT',xpAwarded:0,matchPoolCondition:'MATCH_POOL_CLASS_CHANGED'});expect((await units(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('다른 쪽 거절 후 정상 재큐는 풀 고정값을 보존하고 이력이 바뀐 계정을 다시 잠그지 않는다',async()=>{
  for(const changed of [false,true]){
   const a=await user(),b=await user(),room=await pair(a,b);await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});if(changed)await history(a.id);
   await svc.accept(b.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:false});const queue=await db.roster60PvpQueue.findUnique({where:{playerId:a.id}});
   if(changed){expect(queue).toBeNull();expect((await units(a)).every(u=>u.status==='AVAILABLE')).toBe(true);}
   else{expect(queue).toMatchObject({matchPoolPolicyVersion:poolVersion,reincarnationPool:'FIRST_LIFE'});await svc.cancel(a.id,{requestId:request()});}
  }
 });

 it('이미 존재하던 matchPool 없는 일반게임 방의 수락 계약을 새 분류로 소급 취소하지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b),row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room.battleId}}),snapshot=JSON.parse(row.snapshot);delete snapshot.matchPool;
  await db.roster60PvpMatch.update({where:{id:room.battleId},data:{snapshot:JSON.stringify(snapshot)}});await history(a.id);
  await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});await svc.accept(b.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});expect((await svc.current(a.id) as any).battle.status).toBe('ACTIVE');
 });
});
