import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import Fastify from 'fastify';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterPvp} from '../src/roster60-pvp.js';
import {registerRoster60} from '../src/roster60-routes.js';
import {instanceGrowthCurve} from '../src/battle/roster60-rules.js';
import type {AccountLevelReader} from '../src/progression/roster60-rank.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const battlePolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const normalXpPolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));

suite('랭크 계정·개체 60과 영구 환생 풀의 격리 저장 경계',()=>{
 let db:PrismaClient,svc:RosterPvp,now:number;
 const owners=new Set<string>(),accountLevels=new Map<string,number|null>(),legacyDefinitions=new Set<string>();
 const request=()=>randomUUID();
 const reader:AccountLevelReader=async(_tx,playerId)=>accountLevels.get(playerId)??null;
 const service=(accountLevelReader:AccountLevelReader=reader)=>new RosterPvp(db,()=>catalog,()=>battlePolicy,()=>now,()=>normalXpPolicy,accountLevelReader);
 type User={id:string;ids:string[]};
 async function user(levels=[60],accountLevel:number|null=60):Promise<User>{
  const p=await db.player.create({data:{identity:'rank-admission:'+request()}});owners.add(p.id);accountLevels.set(p.id,accountLevel);
  const ids:string[]=[];for(const [index,level] of levels.entries()){
   const definition=catalog.characters[index];const unit=await db.roster60Owned.create({data:{playerId:p.id,speciesId:definition.id,totalXp:BigInt(definition.growth[level-1].totalXp),source:request()}});ids.push(unit.id);
  }
  await svc.party(p.id,ids);return {id:p.id,ids};
 }
 async function join(a:User){return svc.join(a.id,{requestId:request(),mode:'rank'}) as Promise<any>;}
 async function pair(a:User,b:User){expect((await join(a)).status).toBe('waiting');const result=await join(b);expect(result.status).toBe('matched');return result.battle;}
 async function rebirthHistory(a:User){await db.roster60RebirthAccount.upsert({where:{playerId:a.id},create:{playerId:a.id,successes:1},update:{successes:1}});}
 async function rawAccount(a:User){return db.roster60Owned.findMany({where:{playerId:a.id},orderBy:{id:'asc'}});}
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');
  db=new PrismaClient({datasources:{db:{url}}});
 });
 beforeEach(()=>{now=Date.now();svc=service();});
 afterEach(async()=>{
  const ids=[...owners];
  const rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]},select:{id:true}}),roomIds=rooms.map(r=>r.id);
  await db.rankedSettlement.deleteMany({where:{roomId:{in:roomIds}}});await db.rankedMatch.deleteMany({where:{roomId:{in:roomIds}}});
  await db.roster60PvpMatch.deleteMany({where:{id:{in:roomIds}}});
  await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});
  await db.progressionReceipt.deleteMany({where:{playerId:{in:ids}}});await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});
  await db.rankedProfile.deleteMany({where:{playerId:{in:ids}}});await db.casualHumanRating.deleteMany({where:{playerId:{in:ids}}});
  await db.ownedCharacter.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});
  await db.characterDefinition.deleteMany({where:{id:{in:[...legacyDefinitions]}}});owners.clear();accountLevels.clear();legacyDefinitions.clear();
 });
 afterAll(async()=>{await db.$disconnect();});

 it('계정 레벨 reader 미설정은 높은 개체가 있어도 랭크 적격으로 만들지 않고 잠금 전에 거절한다',async()=>{
  const a=await user([60],null);svc=new RosterPvp(db,()=>catalog,()=>battlePolicy,()=>now,()=>normalXpPolicy);
  const options:any=await svc.options(a.id);expect(options.rankEligibility).toMatchObject({eligible:false,reason:'ACCOUNT_LEVEL_POLICY_PENDING',accountLevel:null,requiredAccountLevel:60,requiredUnitLevel:60,scope:'rank-only'});
  await expect(join(a)).rejects.toMatchObject({code:'ACCOUNT_LEVEL_POLICY_PENDING'});
  expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('인증 경로는 클라이언트 계정 레벨·환생 풀 주장을 거절하고 시험 신규 계정의 방범대장 1을 판정한다',async()=>{
  const a=await user(),previous=process.env.ROSTER60_PROFILE;process.env.ROSTER60_PROFILE='five-stat-device-test-v1';const app=Fastify();registerRoster60(app,db,process.cwd(),r=>r.headers.authorization===a.id?a.id:null,()=>true);
  try{
   const path='/my/api/native/roster60/pvp/';expect((await app.inject({url:path+'options'})).statusCode).toBe(401);
   const headers={authorization:a.id};
   const forged=await app.inject({method:'POST',url:path+'join',headers,payload:{requestId:request(),mode:'rank',accountLevel:60,reincarnationPool:'FIRST_LIFE'}});expect(forged.statusCode).toBe(400);
   const options=await app.inject({url:path+'options',headers});expect(options.json().rankEligibility).toMatchObject({eligible:false,accountLevel:1,reason:'RANK_ACCOUNT_LEVEL_REQUIRED'});
   const denied=await app.inject({method:'POST',url:path+'join',headers,payload:{requestId:request(),mode:'rank'}});expect(denied.statusCode).toBe(409);expect(denied.json().error).toBe('RANK_ACCOUNT_LEVEL_REQUIRED');
   expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
  }finally{await app.close();if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;}
 });

 it('방범대장 정책이 명시적으로 미설정된 인증 fixture는 계정 레벨을 추정하지 않고 503으로 보류한다',async()=>{
  const a=await user(),previous=process.env.ROSTER60_PROFILE;process.env.ROSTER60_PROFILE='five-stat-device-test-v1';const app=Fastify();
  registerRoster60(app,db,process.cwd(),r=>r.headers.authorization===a.id?a.id:null,()=>true,undefined,undefined,()=>null);
  try{
   const path='/my/api/native/roster60/pvp/',headers={authorization:a.id};const options=await app.inject({url:path+'options',headers});expect(options.json().rankEligibility).toMatchObject({eligible:false,accountLevel:null,reason:'ACCOUNT_LEVEL_POLICY_PENDING'});
   const denied=await app.inject({method:'POST',url:path+'join',headers,payload:{requestId:request(),mode:'rank'}});expect(denied.statusCode).toBe(503);expect(denied.json().error).toBe('ACCOUNT_LEVEL_POLICY_PENDING');
   expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
  }finally{await app.close();if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;}
 });

 it.each([[59,[60]],[60,[59]],[60,[60,59]]] as [number,number[]][])('계정 %i와 편성 %j의 미달은 실제 개체 레벨로 판정하고 기존 편성을 바꾸지 않는다',async(accountLevel,levels)=>{
  const a=await user(levels,accountLevel),before=await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}}),options:any=await svc.options(a.id);
  expect(options.rankEligibility.eligible).toBe(accountLevel>=60);expect(options.units.map((unit:any)=>unit.level).sort((x:number,y:number)=>x-y)).toEqual([...levels].sort((x,y)=>x-y));
  expect(options.units.filter((unit:any)=>!unit.rankEligible)).toHaveLength(levels.filter(level=>level<60).length);
  await expect(join(a)).rejects.toMatchObject({code:accountLevel<60?'RANK_ACCOUNT_LEVEL_REQUIRED':'RANK_UNIT_LEVEL_REQUIRED'});
  expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect(await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}})).toEqual(before);expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it('계정 60과 모든 개체 60은 입장하지만 실제 랭크 전투의 기존 정규화 30은 유지한다',async()=>{
  const a=await user([60,60]),b=await user([60,60]);const eligibility:any=await svc.options(a.id);expect(eligibility.rankEligibility.eligible).toBe(true);
  expect(eligibility.units.every((u:any)=>u.rankEligible&&u.level===60)).toBe(true);const room=await pair(a,b);
  expect(room.state.fighters[0].level).toBe(30);expect(room.state.reserves.every((u:any)=>u.level===30)).toBe(true);
  await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});await svc.accept(b.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});
  expect((await svc.current(a.id) as any).battle.status).toBe('ACTIVE');expect((await rawAccount(a)).every(u=>u.totalXp===BigInt(catalog.characters.find((c:any)=>c.id===u.speciesId).growth[59].totalXp))).toBe(true);
 });

 it('환생 후 레벨 1은 계정 이력이 있어도 막고 다시 60이 되면 입장을 허용한다',async()=>{
  const a=await user([60]),b=await user([60]);await rebirthHistory(a);await rebirthHistory(b);
  await db.roster60Owned.update({where:{id:a.ids[0]},data:{reincarnation:1,totalXp:0n}});
  let options:any=await svc.options(a.id);expect(options.rankEligibility.eligible).toBe(true);expect(options.units[0]).toMatchObject({level:1,rankEligible:false});await expect(join(a)).rejects.toMatchObject({code:'RANK_UNIT_LEVEL_REQUIRED'});
  const definition=catalog.characters[0],curve=instanceGrowthCurve(definition.growth,1,definition.reincarnationXpPolicy);await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:BigInt(curve[59].totalXp)}});
  options=await svc.options(a.id);expect(options.rankEligibility.eligible).toBe(true);expect(options.units[0]).toMatchObject({level:60,rankEligible:true});expect((await pair(a,b)).mode).toBe('rank');
 });

 it('환생 계정은 현재 편성에 환생 개체가 없어도 이력 계정끼리만 만나고 비환생 풀과 합치지 않는다',async()=>{
  const first=await user(),reborn=await user(),otherReborn=await user();await rebirthHistory(reborn);await rebirthHistory(otherReborn);
  expect((await rawAccount(reborn)).every(u=>u.reincarnation===0)).toBe(true);
  const firstOptions:any=await svc.options(first.id),rebornOptions:any=await svc.options(reborn.id);expect(firstOptions.rankEligibility.pool).not.toBe(rebornOptions.rankEligibility.pool);
  expect((await join(first)).status).toBe('waiting');expect((await join(reborn)).status).toBe('waiting');const matched=await join(otherReborn);expect(matched.status).toBe('matched');
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:matched.battle.battleId}});expect(new Set([row.player0Id,row.player1Id])).toEqual(new Set([reborn.id,otherReborn.id]));expect((await svc.current(first.id) as any).status).toBe('waiting');
 });

 it('현재 보유 행과 독립된 성공 영수증만 남아 있어도 환생 계정 분류를 보존한다',async()=>{
  const a=await user(),b=await user(),c=await user();
  await db.roster60RebirthReceipt.create({data:{playerId:a.id,requestId:request(),fingerprint:'server-success',result:JSON.stringify({completed:true,preview:{next:{reincarnation:1}}})}});
  await db.progressionReceipt.create({data:{playerId:b.id,requestId:request(),fingerprint:JSON.stringify({action:'reincarnate',payload:{value:'old-consumed-owned'}}),result:JSON.stringify({ok:true,reincarnation:1})}});
  await db.auditEvent.create({data:{playerId:c.id,kind:'REINCARNATION',detail:{sourceOwnedId:1,ownedId:2}}});
  for(const person of [a,b,c])expect((await svc.options(person.id) as any).rankEligibility.pool).toBe('REBORN');
  expect((await rawAccount(a)).every(u=>u.reincarnation===0)).toBe(true);expect((await pair(a,b)).mode).toBe('rank');
 });

 it('현재 출전 개체는 비환생이고 소비된 다른 60종 개체의 환생 이력만 남아도 환생 풀을 유지한다',async()=>{
  const a=await user(),b=await user();await rebirthHistory(b);
  await db.roster60Owned.create({data:{playerId:a.id,speciesId:'u02',reincarnation:1,totalXp:0n,status:'CONSUMED',source:request()}});
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:a.id}})).toBeNull();expect(await db.roster60RebirthReceipt.count({where:{playerId:a.id}})).toBe(0);
  expect((await rawAccount(a)).filter(u=>a.ids.includes(u.id)).every(u=>u.reincarnation===0&&u.status==='AVAILABLE')).toBe(true);
  expect((await svc.options(a.id) as any).rankEligibility.pool).toBe('REBORN');expect((await pair(a,b)).mode).toBe('rank');
 });

 it.each(['reincarnation','obtainedVia'] as const)('소비된 기존 개체의 %s 근거만 남아도 현재 60종 편성에 상관없이 환생 계정이다',async proof=>{
  const a=await user(),b=await user();await rebirthHistory(b);const previous=await db.characterDefinition.aggregate({_min:{position:true}});
  const definition=await db.characterDefinition.create({data:{id:'rank-history-'+request(),name:'이력 시험 갈매미',rarity:'COMMON',position:(previous._min.position??0)-1,quote:'격리 시험용',imageUrl:'/test-only.png'}});legacyDefinitions.add(definition.id);
  await db.ownedCharacter.create({data:{playerId:a.id,characterId:definition.id,status:'CONSUMED',reincarnation:proof==='reincarnation'?1:0,obtainedVia:proof==='obtainedVia'?'REINCARNATION':'TEST',consumedAt:new Date(now)}});
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:a.id}})).toBeNull();expect(await db.progressionReceipt.count({where:{playerId:a.id}})).toBe(0);expect(await db.auditEvent.count({where:{playerId:a.id,kind:'REINCARNATION'}})).toBe(0);
  expect((await rawAccount(a)).every(u=>u.reincarnation===0)).toBe(true);expect((await svc.options(a.id) as any).rankEligibility.pool).toBe('REBORN');expect((await pair(a,b)).mode).toBe('rank');
 });

 it('랭크 모드 편성 확정에서 미달·타계정·빈 편성을 거절하고 기존 편성을 보존한다',async()=>{
  const a=await user([60,59]),b=await user(),saved=await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}});
  await expect(svc.party(a.id,a.ids,'rank')).rejects.toMatchObject({code:'RANK_UNIT_LEVEL_REQUIRED'});
  await expect(svc.party(a.id,[b.ids[0]],'rank')).rejects.toMatchObject({code:'UNIT_NOT_FOUND'});
  await expect(svc.party(a.id,[],'rank')).rejects.toMatchObject({code:'INVALID_PARTY'});
  expect(await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}})).toEqual(saved);
  expect((await svc.party(a.id,[a.ids[0]],'rank')).partyIds).toEqual([a.ids[0]]);
 });

 it('일반게임의 같은 영구 환생 풀은 계정 reader 미설정과 낮은 개체를 랭크 제한으로 막지 않는다',async()=>{
  const a=await user([1],null),b=await user([1],null);await rebirthHistory(a);await rebirthHistory(b);svc=new RosterPvp(db,()=>catalog,()=>battlePolicy,()=>now,()=>normalXpPolicy);
  expect((await svc.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');const joined:any=await svc.join(b.id,{requestId:request(),mode:'normal'});expect(joined.status).toBe('matched');expect(joined.battle.mode).toBe('normal');expect(joined.battle.state.fighters[0].level).toBe(1);
 });

 it('대기 중 환생 이력이나 계정 레벨이 바뀐 큐는 다른 적격 계정과 잘못 매칭되지 않으며 취소할 수 있다',async()=>{
  for(const change of ['rebirth','level'] as const){
   const a=await user(),b=await user();expect((await join(a)).status).toBe('waiting');if(change==='rebirth')await rebirthHistory(a);else accountLevels.set(a.id,59);
   expect((await join(b)).status).toBe('waiting');expect(await db.roster60PvpMatch.count({where:{OR:[{player0Id:a.id},{player1Id:a.id}]}})).toBe(0);
   await svc.cancel(a.id,{requestId:request()});await svc.cancel(b.id,{requestId:request()});expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
  }
 });

 it('예전 분류 없는 랭크 큐는 임의 비환생 기본값으로 매칭하지 않고 취소·재입장을 안내한다',async()=>{
  const a=await user(),b=await user();await join(a);
  await db.roster60PvpQueue.update({where:{playerId:a.id},data:{rankPolicyVersion:null,reincarnationPool:null,rankAdmissionSnapshot:null}});
  expect((await join(b)).status).toBe('waiting');expect(await svc.current(a.id)).toMatchObject({status:'waiting',rankCondition:'RANK_QUEUE_POLICY_PENDING',cancelAllowed:true});
  expect(await db.roster60PvpMatch.count({where:{OR:[{player0Id:a.id},{player1Id:a.id}]}})).toBe(0);await svc.cancel(a.id,{requestId:request()});expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });

 it.each(['account','unit','pool'] as const)('수락 전에 %s 자격이 바뀌면 랭크 손실 없이 방을 중단하고 대원을 해제한다',async change=>{
  const a=await user(),b=await user(),room=await pair(a,b);
  if(change==='account')accountLevels.set(a.id,59);else if(change==='unit')await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:BigInt(catalog.characters[0].growth[58].totalXp)}});else await rebirthHistory(a);
  const result:any=await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});expect(result.status).toBe('FINISHED');expect(result.settlement).toMatchObject({reason:'SERVER_ABORT',xpAwarded:0});
  expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);expect((await rawAccount(b)).every(u=>u.status==='AVAILABLE')).toBe(true);expect(await db.roster60PvpQueue.count({where:{playerId:{in:[a.id,b.id]}}})).toBe(0);expect(await db.rankedMatch.count({where:{roomId:room.battleId}})).toBe(0);
 });

 it('다른 쪽 거절로 재큐할 때 바뀐 계정 자격을 재검사하여 미달 계정을 다시 잠그지 않는다',async()=>{
  const a=await user(),b=await user(),room=await pair(a,b);await svc.accept(a.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:true});
  accountLevels.set(a.id,59);await svc.accept(b.id,{requestId:request(),battleId:room.battleId,revision:room.revision,accept:false});
  expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);expect((await rawAccount(a)).every(u=>u.status==='AVAILABLE')).toBe(true);
 });
});
