import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterPvp} from '../src/roster60-pvp.js';
import {roster60MatchPoolPolicyVersion as version} from '../src/progression/roster60-rank.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const normalXp=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
type Mode='normal'|'rank';type User={id:string;ownedId:string};

suite('일반게임·랭크 환생 횟수 근접 우선과 큐 이력 고정',()=>{
 let db:PrismaClient,svc:RosterPvp,now:number;const owners=new Set<string>();const request=()=>randomUUID();
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(()=>{now=Date.now();svc=new RosterPvp(db,()=>catalog,()=>policy,()=>now,()=>normalXp,async()=>60);});
 afterEach(async()=>{
  const ids=[...owners],rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]},select:{id:true}}),roomIds=rooms.map(r=>r.id);
  await db.rankedSettlement.deleteMany({where:{roomId:{in:roomIds}}});await db.rankedMatch.deleteMany({where:{roomId:{in:roomIds}}});await db.casualHumanSettlement.deleteMany({where:{roomId:{in:roomIds}}});
  await db.roster60PvpMatch.deleteMany({where:{id:{in:roomIds}}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});await db.progressionState.deleteMany({where:{playerId:{in:ids}}});
  // Unknown lifetime-count fixtures create legacy success proof with a
  // non-cascading Player FK; remove it before deleting its account.
  await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});
  await db.rankedProfile.deleteMany({where:{playerId:{in:ids}}});await db.casualHumanRating.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});owners.clear();
 });
 afterAll(async()=>{await db.$disconnect();});
 async function user(mode:Mode,count:number|null):Promise<User>{
  const p=await db.player.create({data:{identity:'rebirth-priority:'+request()}});owners.add(p.id);
  const totalXp=mode==='rank'?BigInt(catalog.characters.find((c:any)=>c.id==='u01').growth[59].totalXp):0n;
  const u=await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',totalXp,source:request()}});
  if(count!==null&&count>0)await db.roster60RebirthAccount.create({data:{playerId:p.id,successes:count}});
  if(count===null)await db.auditEvent.create({data:{playerId:p.id,kind:'REINCARNATION',detail:{test:'unknown lifetime count'}}});
  await svc.party(p.id,[u.id],mode);return {id:p.id,ownedId:u.id};
 }
 async function stage(a:User,mode:Mode,rating=1000,tierIndex=0){
  expect((await svc.join(a.id,{requestId:request(),mode}) as any).status).toBe('waiting');
  await db.roster60PvpQueue.update({where:{playerId:a.id},data:{rating,tierIndex,matchPoolPolicyVersion:'test-staged'}});now++;
 }
 async function release(){await db.roster60PvpQueue.updateMany({where:{playerId:{in:[...owners]}},data:{matchPoolPolicyVersion:version}});}
 async function opponent(a:User){const value:any=await svc.current(a.id);expect(value.status).toBe('matched');const room=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:value.battle.battleId}});return {id:room.player0Id===a.id?room.player1Id:room.player0Id,room,battle:value.battle};}

 for(const mode of ['normal','rank'] as const){
  it(`${mode}: 같은 풀에서 횟수 거리가 MMR보다 먼저이며 다른 횟수도 허용한다`,async()=>{
   const a=await user(mode,4),far=await user(mode,12),near=await user(mode,3);await stage(a,mode);await stage(far,mode,1000);await stage(near,mode,1080);await release();
   const result=await opponent(a);expect(result.id).toBe(near.id);const pin=JSON.parse(result.room.snapshot).matchPool;expect(pin.version).toBe(version);expect(pin.players.map((p:any)=>p.count).sort()).toEqual([3,4]);
   expect(JSON.stringify(result.battle)).not.toContain('historyStamp');expect(JSON.stringify(result.battle)).not.toContain('countSource');
  });
  it(`${mode}: 횟수가 더 가까워도 현재 MMR 범위 밖의 후보를 당겨오지 않는다`,async()=>{
   const a=await user(mode,4),far=await user(mode,12),near=await user(mode,4);await stage(a,mode);await stage(far,mode,1010);await stage(near,mode,1300);await release();expect((await opponent(a)).id).toBe(far.id);
  });
 }
 it('랭크의 tier 대기 확장 전 제한을 횟수 우선으로 건너뛰지 않는다',async()=>{
  const a=await user('rank',4),far=await user('rank',12),near=await user('rank',4);await stage(a,'rank');await stage(far,'rank',1010);await stage(near,'rank',1000,5);await release();expect((await opponent(a)).id).toBe(far.id);
 });
 it('횟수 미상 계정의 오래된 선택 차례는 기존 MMR로 상대를 찾아 막히지 않는다',async()=>{
  const a=await user('normal',null),nearMmr=await user('normal',99),farMmr=await user('normal',1);await stage(a,'normal');await stage(nearMmr,'normal',1000);await stage(farMmr,'normal',1090);await release();expect((await opponent(a)).id).toBe(nearMmr.id);
 });
 it('횟수 미상 후보뿐이어도 known 계정과 같은 환생 풀에서 매칭한다',async()=>{
  const a=await user('normal',4),b=await user('normal',null);await stage(a,'normal');await stage(b,'normal');await release();expect((await opponent(a)).id).toBe(b.id);
 });
 it('동일 횟수 거리에서는 먼저 기다린 후보를 고르고 모든 핀을 재큐에 보존한다',async()=>{
  const a=await user('normal',4),older=await user('normal',3),later=await user('normal',5);await stage(a,'normal');await stage(older,'normal');await stage(later,'normal');await release();
  const result=await opponent(a);expect(result.id).toBe(older.id);const pin=JSON.parse(result.room.snapshot).matchPool.players.find((p:any)=>p.playerId===a.id);
  await svc.accept(a.id,{requestId:request(),battleId:result.room.id,revision:result.room.revision,accept:true});await svc.accept(older.id,{requestId:request(),battleId:result.room.id,revision:result.room.revision,accept:false});
  expect(await db.roster60PvpQueue.findUnique({where:{playerId:a.id}})).toMatchObject({reincarnationCount:4,reincarnationCountSource:pin.countSource,reincarnationHistoryStamp:pin.historyStamp,matchPoolPolicyVersion:version});
 });
 it('known 횟수의 대기 중 증가를 감지하여 사람·AI를 만들지 않고 재입장을 안내한다',async()=>{
  const a=await user('normal',4);await svc.join(a.id,{requestId:request(),mode:'normal'});await db.roster60RebirthAccount.update({where:{playerId:a.id},data:{successes:5}});now+=30_000;
  expect(await svc.current(a.id)).toMatchObject({status:'waiting',matchPoolCondition:'MATCH_POOL_HISTORY_CHANGED',matchReincarnationCount:4,cancelAllowed:true});expect(await db.roster60PvpMatch.count({where:{player0Id:a.id}})).toBe(0);
  await svc.cancel(a.id,{requestId:request()});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('AVAILABLE');
 });
 it('unknown 횟수가 그대로여도 혼재 계정 counter 증가를 수락 전에 감지하고 보상 없이 중단한다',async()=>{
  const a=await user('normal',null),b=await user('normal',2);await db.roster60RebirthAccount.create({data:{playerId:a.id,successes:3}});await stage(a,'normal');await stage(b,'normal');await release();const result=await opponent(a);
  expect(JSON.parse(result.room.snapshot).matchPool.players.find((p:any)=>p.playerId===a.id)).toMatchObject({count:null,countSource:'MIXED_LEGACY_HISTORY'});await db.roster60RebirthAccount.update({where:{playerId:a.id},data:{successes:4}});
  const finished:any=await svc.accept(a.id,{requestId:request(),battleId:result.room.id,revision:result.room.revision,accept:true});expect(finished).toMatchObject({status:'FINISHED',settlement:{reason:'SERVER_ABORT',xpAwarded:0,rating:null,matchPoolCondition:'MATCH_POOL_HISTORY_CHANGED'}});
  const other:any=await svc.current(b.id);expect(other.battle.settlement.matchPoolCondition).toBe('MATCH_POOL_OPPONENT_CHANGED');expect(JSON.stringify(other)).not.toContain(a.id);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('AVAILABLE');
 });
 it('이전 count 없는 큐 version은 사람·AI 신규 매칭에서 제외되고 취소할 수 있다',async()=>{
  const a=await user('normal',4),b=await user('normal',5);await stage(a,'normal');await stage(b,'normal');await release();
  await db.roster60PvpQueue.update({where:{playerId:a.id},data:{matchPoolPolicyVersion:'account-rebirth-history-normal-rank-v1',reincarnationCount:null,reincarnationCountSource:null,reincarnationHistoryStamp:null}});
  expect((await svc.current(b.id) as any).status).toBe('waiting');now+=30_000;expect(await svc.current(a.id)).toMatchObject({status:'waiting',matchPoolCondition:'MATCH_POOL_QUEUE_POLICY_PENDING',cancelAllowed:true});
  expect(await db.roster60PvpMatch.count({where:{OR:[{player0Id:a.id},{player1Id:a.id}]}})).toBe(0);await svc.cancel(a.id,{requestId:request()});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('AVAILABLE');
 });
 it('이미 만든 v1 방은 soft count 우선순위 정책을 소급하지 않는다',async()=>{
  const a=await user('normal',4),b=await user('normal',5);await stage(a,'normal');await stage(b,'normal');await release();const result=await opponent(a),snapshot=JSON.parse(result.room.snapshot);snapshot.matchPool={version:'account-rebirth-history-normal-rank-v1',players:snapshot.matchPool.players.map((p:any)=>({playerId:p.playerId,pool:p.pool}))};
  await db.roster60PvpMatch.update({where:{id:result.room.id},data:{snapshot:JSON.stringify(snapshot)}});await svc.accept(a.id,{requestId:request(),battleId:result.room.id,revision:result.room.revision,accept:true});await svc.accept(b.id,{requestId:request(),battleId:result.room.id,revision:result.room.revision,accept:true});expect((await svc.current(a.id) as any).battle.status).toBe('ACTIVE');
 });
});
