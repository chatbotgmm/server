import {afterAll,afterEach,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {awardCommanderXp,commanderAccountLevelReader,commanderPolicyFingerprint,readCommanderState,validateCommanderPolicy,type CommanderEvent,type CommanderPolicy} from '../src/progression/commander.js';
import {evaluateRoster60Rank} from '../src/progression/roster60-rank.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {serializeRoster60Write} from '../src/roster60-write-queue.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const policy=validateCommanderPolicy(JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/commander-policy.json','utf8')));
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const request=()=>randomUUID();
const event=(change:Partial<CommanderEvent>={}):CommanderEvent=>({kind:'NORMAL_PLAY',eventId:request(),outcome:'win',reason:'BATTLE',...change});
const pause=()=>new Promise(resolve=>setTimeout(resolve,10));

suite('방범대장 계정 XP 원자 정산과 저장 경계',()=>{
 let db:PrismaClient,other:PrismaClient;const owners=new Set<string>();
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});other=new PrismaClient({datasources:{db:{url}}});});
 afterEach(async()=>{await db.player.deleteMany({where:{id:{in:[...owners]}}});owners.clear();});
 afterAll(async()=>{await Promise.all([db.$disconnect(),other.$disconnect()]);});
 async function player(){const p=await db.player.create({data:{identity:'commander:'+request()}});owners.add(p.id);return p.id;}
 async function award(playerId:string,input:CommanderEvent,config:CommanderPolicy|null=policy,client=db){
  // Match the caller contract used by RosterPvp: acquire SQLite's writer before
  // the first ledger read. Two Prisma clients still race the same durable event;
  // this avoids a read-to-write lock upgrade deadlock unrelated to XP arithmetic.
  return serializeRoster60Write(client,async()=>{
   for(let attempt=0;;attempt++)try{return await client.$transaction(async tx=>{
    await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
    return awardCommanderXp(tx,playerId,input,config);
   },{timeout:15_000,maxWait:10_000});}catch(e){if(attempt>=20||!['P2002','P2034','P1008','P2028','COMMANDER_CONCURRENT_WRITE'].includes((e as any).code))throw e;await pause();}
  });
 }
 async function owned(playerId:string,level=60){const d=catalog.characters.find((c:any)=>c.id==='u45');return db.roster60Owned.create({data:{playerId,speciesId:d.id,totalXp:BigInt(d.growth[level-1].totalXp),source:request()}});}

 it('정책 미설정은 기존 플레이를 막지 않고 계정·원장을 만들지 않으며 유닛 최고값도 복사하지 않는다',async()=>{
  const id=await player();await owned(id,60);expect(await award(id,event(),null)).toMatchObject({status:'POLICY_PENDING',xpAwarded:0,level:null});
  expect(await db.commanderAccount.findUnique({where:{playerId:id}})).toBeNull();expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(0);
  expect(await readCommanderState(db,id,policy)).toMatchObject({level:1,totalXp:'0',initialized:false});expect(await commanderAccountLevelReader(()=>null)(db,id)).toBeNull();
 });
 it('같은 사건의 반복·다른 요청과 다른 Prisma 재접속에서도 계정 한 번만 지급한다',async()=>{
  const id=await player(),input=event(),first=await award(id,input);expect(first).toMatchObject({xpAwarded:policy.rewards.normalWin,replayed:false});
  for(const client of [db,other,db])expect(await award(id,input,null,client)).toMatchObject({xpAwarded:0,recordedXp:policy.rewards.normalWin,replayed:true});
  expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp).toBe(BigInt(policy.rewards.normalWin));expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(1);
 });
 it('두 Prisma의 중복 동시 완료는 유일 원장 한 건과 한 번의 계정 증가로 수렴한다',async()=>{
  const id=await player(),input=event();const all=await Promise.all(Array.from({length:6},(_,i)=>award(id,input,policy,i%2?other:db)));
  expect(all.reduce((sum,r)=>sum+r.xpAwarded,0)).toBe(policy.rewards.normalWin);expect(all.filter(r=>!r.replayed)).toHaveLength(1);
  expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(1);expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).revision).toBe(1);
 });
 it('서로 다른 사건 동시 정산은 합을 잃지 않고 계정별로 독립한다',async()=>{
  const a=await player(),b=await player();await Promise.all([award(a,event(),policy,db),award(a,event({outcome:'loss'}),policy,other),award(b,event(),policy,other)]);
  expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:a}})).totalXp).toBe(BigInt(policy.rewards.normalWin+policy.rewards.normalLoss));expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:b}})).totalXp).toBe(BigInt(policy.rewards.normalWin));
 });
 it('같은 사건 ID의 다른 결과를 거절하며 0 결정도 뒤늦은 보상으로 바꾸지 않는다',async()=>{
  const id=await player(),input=event({reason:'SURRENDER'});expect(await award(id,input)).toMatchObject({status:'RECORDED_ZERO',xpAwarded:0});
  await expect(award(id,{...input,reason:'BATTLE'})).rejects.toMatchObject({code:'COMMANDER_EVENT_CONFLICT'});expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp).toBe(0n);expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(1);
 });
 it('원장 쓰기 실패는 계정 초기화와 XP 증가까지 원자 롤백하고 재시도로 정상 확정한다',async()=>{
  const id=await player(),input=event();await db.$executeRawUnsafe('CREATE TRIGGER fail_commander_test BEFORE INSERT ON CommanderXpLedger BEGIN SELECT RAISE(ABORT,\'commander rollback test\'); END');
  try{await expect(award(id,input)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_commander_test');}
  expect(await db.commanderAccount.findUnique({where:{playerId:id}})).toBeNull();expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(0);expect(await award(id,input)).toMatchObject({xpAwarded:policy.rewards.normalWin});
 });
 it('실제 개체 환생은 방범대장 레벨과 누적 계정 XP를 유지하고 개체 60 재입장 제한만 적용한다',async()=>{
  const id=await player(),unit=await owned(id);await db.commanderAccount.create({data:{playerId:id,totalXp:5900n,policyVersion:policy.version,policyFingerprint:commanderPolicyFingerprint(policy)}});
  const reader=commanderAccountLevelReader(()=>policy);expect((await evaluateRoster60Rank(db,id,[unit.id],catalog,reader,{allowedStatuses:['AVAILABLE']})).eligible).toBe(true);
  const service=new RosterReincarnation(db,()=>catalog),candidate=(await service.preview(id)).candidates.find(c=>c.ownedId===unit.id) as any;expect(candidate.eligible).toBe(true);
  await service.confirm(id,{requestId:request(),ownedId:unit.id,catalogVersion:catalog.version,previewFingerprint:candidate.previewFingerprint});
  expect(await readCommanderState(other,id,policy)).toMatchObject({level:60,totalXp:'5900'});expect((await db.roster60Owned.findUniqueOrThrow({where:{id:unit.id}})).totalXp).toBe(0n);
  expect(await evaluateRoster60Rank(db,id,[unit.id],catalog,reader,{allowedStatuses:['AVAILABLE']})).toMatchObject({eligible:false,reason:'RANK_UNIT_LEVEL_REQUIRED',accountLevel:60});
 });
 it('본편과 주민 의뢰는 각 완료 한 번이며 연결 모험은 0·독립 승리 귀환은 별도 한 사건이다',async()=>{
  const id=await player(),main:CommanderEvent={kind:'STORY_QUEST',eventId:policy.story.contentVersion+':STORY_01',outcome:'completed'},side:CommanderEvent={kind:'RESIDENT_QUEST',eventId:policy.story.contentVersion+':RESIDENT_BLACKSMITH_01',outcome:'completed'};
  const linked=event({kind:'ADVENTURE_RETURN',outcome:'completed',reason:'WIN_RETURN',linkedQuestId:'STORY_01'}),independent=event({kind:'ADVENTURE_RETURN',outcome:'completed',reason:'WIN_RETURN'});
  for(const input of [main,side,linked,independent]){await award(id,input);expect((await award(id,input)).xpAwarded).toBe(0);}
  expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp).toBe(BigInt(policy.rewards.storyQuest+policy.rewards.residentQuest+policy.rewards.independentAdventureReturn));expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(4);
 });
 it('99 곡선 밖 XP는 누적 유지하고 정책 변경을 자동 재계산·백필하지 않는다',async()=>{
  const id=await player();await db.commanderAccount.create({data:{playerId:id,totalXp:10_000n,policyVersion:policy.version,policyFingerprint:commanderPolicyFingerprint(policy)}});await award(id,event());expect(await readCommanderState(other,id,policy)).toMatchObject({level:99,totalXp:String(10_000+policy.rewards.normalWin),nextLevelXp:null,status:'CURVE_EXTENSION_PENDING'});
  const changed={...policy,version:policy.version+':new'};expect(await commanderAccountLevelReader(()=>changed)(db,id)).toBeNull();await expect(award(id,event(),changed)).rejects.toMatchObject({code:'COMMANDER_ACCOUNT_POLICY_MISMATCH'});
  expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp).toBe(BigInt(10_000+policy.rewards.normalWin));
 });
 it('동일 버전 semantic 지문은 객체 키 순서에 안정적이며 실보상 변경은 원장·계정을 바꾸지 않는다',async()=>{
  const id=await player(),first=await award(id,event()),reordered=Object.fromEntries(Object.entries(policy).reverse()) as CommanderPolicy;reordered.rewards=Object.fromEntries(Object.entries(policy.rewards).reverse()) as typeof policy.rewards;
  expect(await readCommanderState(other,id,reordered)).toMatchObject({level:first.level,totalXp:first.totalXp,status:'READY'});
  const row=await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}}),ledger=await db.commanderXpLedger.findFirstOrThrow({where:{playerId:id}});expect(row.policyFingerprint).toBe(commanderPolicyFingerprint(policy));expect(ledger.policyFingerprint).toBe(row.policyFingerprint);expect(JSON.stringify(first)).not.toContain('policyFingerprint');
  const changed=structuredClone(policy);changed.rewards.normalWin++;changed.rewards.normalProvenTimeoutSurvivor++;expect(await readCommanderState(other,id,changed)).toMatchObject({level:null,status:'POLICY_MISMATCH'});await expect(award(id,event(),changed)).rejects.toMatchObject({code:'COMMANDER_ACCOUNT_POLICY_MISMATCH'});
  expect(await db.commanderXpLedger.count({where:{playerId:id}})).toBe(1);expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp).toBe(row.totalXp);
 });
});
