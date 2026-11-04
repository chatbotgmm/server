import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import Fastify from 'fastify';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterBattle,fighter,type BattlePolicy} from '../src/roster60-battle.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterStory} from '../src/story/service.js';
import {RosterOwned} from '../src/roster60-owned.js';
import {registerRoster60} from '../src/roster60-routes.js';
import {instanceGrowthCurve} from '../src/battle/roster60-rules.js';
import {commanderPolicyReader} from '../src/progression/commander.js';
import {pacedUnitWin,averageLevelWin,type UnitXpPacing} from '../src/progression/unit-xp-pacing.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const load=(path:string)=>JSON.parse(readFileSync(path,'utf8'));
const originalCatalog=load('data/test-profiles/five-stat-device-test-v1/catalog.json');
const originalBattle=load('data/test-profiles/five-stat-device-test-v1/battle-policy.json');
// 「승리 2번에 1레벨·출전 개체 균등」 예전 판을 검증한다(이미 고정된 전투는 이 판으로 정산). 새 배포 판은 test/finisher-split.test.ts에서 검증한다.
const pacing=load('test/fixtures/unit-xp-pacing-policy-two-wins.json') as UnitXpPacing;
const content=load('data/story/story-mode-v1.json');
const storyUnitPolicy=load('data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json');
const request=()=>randomUUID();
suite('new battle reward pacing, real persisted settlement and existing receipt boundaries',()=>{
 let db:PrismaClient,catalog:any,policy:BattlePolicy;const players=new Set<string>();
 const originalProfile=process.env.ROSTER60_PROFILE,originalStory=process.env.ROSTER60_STORY_PROFILE;
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(()=>{catalog=structuredClone(originalCatalog);policy=structuredClone(originalBattle);for(const unit of catalog.characters){if(unit.id==='u45')unit.attack.power=1_000_000;if(unit.id==='u07'){unit.attack.power=0;unit.skills=[];for(const stats of unit.combatGrowth)stats.hp=1;unit.levelOneStats.hp=1;}}});
 afterEach(async()=>{await db.dailyProgress.deleteMany({where:{playerId:{in:[...players]}}});await db.auditEvent.deleteMany({where:{playerId:{in:[...players]}}});await db.player.deleteMany({where:{id:{in:[...players]}}});players.clear();if(originalProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=originalProfile;if(originalStory===undefined)delete process.env.ROSTER60_STORY_PROFILE;else process.env.ROSTER60_STORY_PROFILE=originalStory;});
 afterAll(async()=>{await db.$disconnect();});
 const battle=()=>new RosterBattle(db,()=>catalog,()=>policy);
 const adventure=()=>new RosterAdventure(db,()=>catalog,fighter,undefined,commanderPolicyReader(process.cwd(),'five-stat-device-test-v1'));
 async function actor(level=15,count=3,cycle=0,start?:bigint){const player=await db.player.create({data:{identity:'pacing-integration:'+request()}});players.add(player.id);const def=catalog.characters.find((u:any)=>u.id==='u45'),curve=instanceGrowthCurve(def.growth,cycle),xp=start??BigInt(curve[level-1].totalXp);const units=[];for(let i=0;i<count;i++)units.push(await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u45',totalXp:xp,reincarnation:cycle,source:request()}}));return {id:player.id,units,start:xp};}
 async function create(a:Awaited<ReturnType<typeof actor>>,adventureId?:string){return battle().create(a.id,{requestId:request(),ownedId:a.units[0].id,reserveIds:a.units.slice(1).map(u=>u.id),encounterId:policy.encounters[0].id,...(adventureId?{adventureId}:{})}) as Promise<any>;}
 // Only terminal combat facts are shortened in this disposable DB fixture. No XP,
 // reward policy, growth threshold, receipt or settlement is fabricated by it.
 async function terminal(a:Awaited<ReturnType<typeof actor>>,b:any,outcome:'win'|'loss'|'escaped'='win',entered=a.units.map(u=>u.id)){
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}}),state=JSON.parse(row.state);
  state.outcome=outcome;state.surrendered=outcome==='escaped';state.participation=entered.map(ownedId=>({ownedId,kind:'switch',turn:0}));
  if(outcome==='loss')for(const unit of [state.fighters[0],...state.reserves])unit.hp=0;
  await db.roster60Battle.update({where:{id:row.id},data:{state:JSON.stringify(state),status:'FINISHED'}});
  return battle().read(a.id,row.id) as Promise<any>;
 }
 async function settle(a:Awaited<ReturnType<typeof actor>>,b:any){const command={requestId:request(),battleId:b.battleId,revision:b.revision},result:any=await battle().settle(a.id,command);expect(await battle().settle(a.id,command)).toEqual(result);expect(await battle().settle(a.id,{...command,requestId:request()})).toEqual(result);return result;}
 const storedXp=async(a:Awaited<ReturnType<typeof actor>>)=>Promise.all(a.units.map(async u=>(await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp));
 function encounter(mode:'duel'|'adventure'='duel',level=15,winXp=600,lossXp=200){policy.encounters=[{id:'pacing-source',speciesId:'u07',level,tutorial:false,mode,winXp,lossXp}];}

 it('preserves an already active old battle while the next single PvE uses the new floor and retains its initial-owned recipient rule',async()=>{
  encounter('duel',40,2400,800);const old=await actor(40),oldBattle=await create(old),snapshot=(await db.roster60Battle.findUniqueOrThrow({where:{id:oldBattle.battleId}})).snapshot;
  policy={...policy,xpPacing:structuredClone(pacing)};const oldPaid=await settle(old,await terminal(old,oldBattle));
  expect(oldPaid.settlement.xpAwarded).toBe(2400);expect(await storedXp(old)).toEqual([old.start+2400n,old.start,old.start]);
  expect((await db.roster60Battle.findUniqueOrThrow({where:{id:oldBattle.battleId}})).snapshot).toBe(snapshot);
  const next=await actor(40),nextBattle=await create(next),pinned=JSON.parse((await db.roster60Battle.findUniqueOrThrow({where:{id:nextBattle.battleId}})).snapshot);
  expect(pinned.unitXpPacing).toMatchObject({policy:{version:pacing.version},recipientMode:'initial-owned',requireSurvivor:false,enemyLevel:40});
  delete policy.xpPacing;const paid=await settle(next,await terminal(next,nextBattle));expect(paid.settlement.xpAwarded).toBe(2530);
  expect(await storedXp(next)).toEqual([next.start+2530n,next.start,next.start]);
  const fresh=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});try{const view:any=await new RosterOwned(fresh,()=>catalog).read(next.id,next.units[0].id);expect(view.unit.growth.totalXp).toBe(Number(next.start)+2530);expect(await new RosterBattle(fresh,()=>catalog,()=>policy).settle(next.id,{requestId:request(),battleId:nextBattle.battleId,revision:paid.revision})).toEqual(paid);}finally{await fresh.$disconnect();}
 });

 it('keeps direct practice loss at its old one-third ratio and escape zero, without adding a story loss policy',async()=>{
  encounter('duel',24,600,200);policy.xpPacing=structuredClone(pacing);
  for(const [outcome,expected] of [['win',1176],['loss',392],['escaped',0]] as const){const a=await actor(24),b=await create(a),paid=await settle(a,await terminal(a,b,outcome));expect(paid.settlement.xpAwarded).toBe(expected);expect(await storedXp(a)).toEqual([a.start+BigInt(expected),a.start,a.start]);}
  encounter('duel',1,60,20);const high=await actor(59),paid=await settle(high,await terminal(high,await create(high)));expect(paid.settlement.xpAwarded).toBe(60); // Client-selected low practice cannot borrow Lv59 quota.
 });

 it.each([0,1])('ordinary adventure caps its pending reward once at cycle %s and only persists it on normal return',async cycle=>{
  const def=catalog.characters.find((u:any)=>u.id==='u45'),curve=instanceGrowthCurve(def.growth,cycle),cap=BigInt(curve[(cycle?99:60)-1].totalXp);
  encounter('adventure',60,60,0);policy.xpPacing=structuredClone(pacing);const a=await actor(cycle?98:59,3,cycle,cap-1n),run:any=await adventure().start(a.id,{requestId:request(),ownedIds:a.units.map(u=>u.id)}),b=await create(a,run.adventureId),paid=await settle(a,await terminal(a,b));
  expect(paid.settlement).toMatchObject({xpAwarded:0,xpEscrowed:3,unitXp:{delivery:'pending-return',xpAccepted:3}});expect(paid.settlement.xpDiscarded).toBeGreaterThan(0);expect(await storedXp(a)).toEqual(a.units.map(()=>cap-1n));
  const before:any=await adventure().read(a.id,run.adventureId),command={requestId:request(),adventureId:run.adventureId,revision:before.revision},returned:any=await adventure().return(a.id,command);expect(await adventure().return(a.id,command)).toEqual(returned);expect(await storedXp(a)).toEqual(a.units.map(()=>cap));
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:a.id}})).toBeNull();expect((await db.roster60Owned.findMany({where:{playerId:a.id}})).every(u=>u.reincarnation===cycle)).toBe(true);
  const cappedRun:any=await adventure().start(a.id,{requestId:request(),ownedIds:a.units.map(u=>u.id)}),capped=await settle(a,await terminal(a,await create(a,cappedRun.adventureId)));expect(capped.settlement).toMatchObject({xpEscrowed:0,xpAwarded:0});
 });

 it('keeps ordinary adventure reserve half-source XP and whole-party defeat discards pending rewards',async()=>{
  encounter('adventure',15,600,0);policy.xpPacing=structuredClone(pacing);const a=await actor(),run:any=await adventure().start(a.id,{requestId:request(),ownedIds:a.units.map(u=>u.id)});
  const paid=await settle(a,await terminal(a,await create(a,run.adventureId),'win',[a.units[0].id]));expect(paid.settlement.xpByOwnedId).toEqual({[a.units[0].id]:600,[a.units[1].id]:300,[a.units[2].id]:300});
  const lost=await settle(a,await terminal(a,await create(a,run.adventureId),'loss'));expect(lost.settlement.adventureStatus).toBe('LOST');expect(lost.settlement.pendingRewards).toEqual({xp:{}});expect(await storedXp(a)).toEqual(a.units.map(()=>a.start));
 });

 it('new accepted-quest and independent black material battles share the general floor, retain zero story losses and deliver on return',async()=>{
  policy.xpPacing=structuredClone(pacing);const campaign=structuredClone(content);campaign.scenes=campaign.scenes.map((s:any)=>({...s,lines:[s.lines[0]]}));const first=campaign.encounters.find((e:any)=>e.id==='STORY_01_01');Object.assign(first,{speciesId:'u07',level:15,hpMultiplier:1,powerMultiplier:1});
  const commander=commanderPolicyReader(process.cwd(),'five-stat-device-test-v1'),story=()=>new RosterStory(db,()=>catalog,()=>policy,campaign,commander,()=>storyUnitPolicy);
  const a=await actor();let journal:any=await story().state(a.id);journal=await story().open(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01',phase:'intro'});await story().accept(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01'});
  for(const quest of [true,false]){const b:any=await story().createBattle(a.id,{requestId:request(),...(quest?{questId:'STORY_01'}:{encounterId:'STORY_01_01'}),ownedIds:a.units.map(u=>u.id)}),paid=await settle(a,await terminal(a,b));expect(paid.settlement).toMatchObject({xpAwarded:0,unitXp:{delivery:'pending-return'}});expect(paid.settlement.xpEscrowed).toBe(1743);const r:any=await adventure().read(a.id,b.state.adventureId),returned:any=await adventure().return(a.id,{requestId:request(),adventureId:r.adventureId,revision:r.revision});expect(returned.settlement.commanderReward.xpAwarded).toBe(quest?0:150);}
  expect(await storedXp(a)).toEqual(a.units.map(()=>a.start+1162n));
  for(const outcome of ['loss','escaped'] as const){const b:any=await story().createBattle(a.id,{requestId:request(),encounterId:'STORY_01_01',ownedIds:a.units.map(u=>u.id)}),paid=await settle(a,await terminal(a,b,outcome));expect(paid.settlement.xpEscrowed).toBe(0);expect(await storedXp(a)).toEqual(a.units.map(()=>a.start+1162n));if(outcome==='escaped'){const r:any=await adventure().read(a.id,b.state.adventureId),returned:any=await adventure().return(a.id,{requestId:request(),adventureId:r.adventureId,revision:r.revision});expect(returned.settlement.commanderReward.xpAwarded).toBe(0);}}
 });

 it('11-03 판(1.5승 최소·마무리 전액, 이미 고정된 전투): 모험은 마무리 유닛이 max(원천, 적정 레벨 최소)를 받고 나머지는 절반, 낮은 적은 적 레벨 기준, 전멸은 0',async()=>{
  const deployed=load('test/fixtures/unit-xp-pacing-policy-finisher-keep-higher.json') as UnitXpPacing,def=catalog.characters.find((u:any)=>u.id==='u45');
  const floorAt=(a:Awaited<ReturnType<typeof actor>>,enemyLevel:number)=>pacedUnitWin({startingXp:a.start.toString(),growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy},enemyLevel,deployed);
  for(const [enemyLevel,winXp] of [[15,10],[15,5000],[5,10]] as const){
   encounter('adventure',enemyLevel,winXp,0);policy.xpPacing=structuredClone(deployed);const a=await actor(),run:any=await adventure().start(a.id,{requestId:request(),ownedIds:a.units.map(u=>u.id)});
   // 자유탐험 적은 정책 레벨(enemyLevel)이 아니라 내 평균(15) ±2로 나온다. 고정된 11-03 판은 실제 적 레벨로 최소값을 잰다.
   const created=await create(a,run.adventureId),actual=created.state.fighters[1].level;expect(actual).toBeGreaterThanOrEqual(13);expect(actual).toBeLessThanOrEqual(17);
   const paid=await settle(a,await terminal(a,created,'win',[a.units[0].id])),w=Math.max(winXp,floorAt(a,actual));
   // 1.5승 최소 = ceil(다음 레벨 필요 XP×2/3). 적이 낮으면 적 레벨 기준이라 최소가 작다.
   expect(floorAt(a,actual)).toBe(Math.ceil((instanceGrowthCurve(def.growth,0)[Math.min(15,actual)].totalXp-instanceGrowthCurve(def.growth,0)[Math.min(15,actual)-1].totalXp)*2/3));
   expect(paid.settlement.xpByOwnedId).toEqual({[a.units[0].id]:w,[a.units[1].id]:Math.floor(w/2),[a.units[2].id]:Math.floor(w/2)});expect(paid.settlement.xpPolicy).toBe(deployed.version);
   const lost=await settle(a,await terminal(a,await create(a,run.adventureId),'loss'));expect(lost.settlement.adventureStatus).toBe('LOST');expect(await storedXp(a)).toEqual(a.units.map(()=>a.start));
  }
 });

 it('11-03 판: 수락한 의뢰 전투는 마무리 유닛이 max(스토리 보상, 적정 레벨 최소), 나머지는 절반을 귀환 때 받는다',async()=>{
  const deployed=load('test/fixtures/unit-xp-pacing-policy-finisher-keep-higher.json') as UnitXpPacing,def=catalog.characters.find((u:any)=>u.id==='u45');
  policy.xpPacing=structuredClone(deployed);const campaign=structuredClone(content);campaign.scenes=campaign.scenes.map((s:any)=>({...s,lines:[s.lines[0]]}));const first=campaign.encounters.find((e:any)=>e.id==='STORY_01_01');Object.assign(first,{speciesId:'u07',level:15,hpMultiplier:1,powerMultiplier:1});
  const commander=commanderPolicyReader(process.cwd(),'five-stat-device-test-v1'),story=()=>new RosterStory(db,()=>catalog,()=>policy,campaign,commander,()=>storyUnitPolicy);
  const a=await actor();let journal:any=await story().state(a.id);journal=await story().open(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01',phase:'intro'});await story().accept(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01'});
  const b:any=await story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id)}),paid=await settle(a,await terminal(a,b,'win',[a.units[0].id]));
  const w=Math.max(storyUnitPolicy.rewards.STORY_01_01.winXp,pacedUnitWin({startingXp:a.start.toString(),growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy},15,deployed));
  expect(paid.settlement).toMatchObject({xpAwarded:0,xpEscrowed:w+2*Math.floor(w/2),unitXp:{delivery:'pending-return'}});
  const r:any=await adventure().read(a.id,b.state.adventureId);await adventure().return(a.id,{requestId:request(),adventureId:r.adventureId,revision:r.revision});
  expect(await storedXp(a)).toEqual([a.start+BigInt(w),a.start+BigInt(Math.floor(w/2)),a.start+BigInt(Math.floor(w/2))]);
 });

 it('통일 판(배포): 자유탐험 적은 내 평균 ±2레벨, 보상은 전투 전원 평균 레벨 기준으로 마무리 전액·나머지 절반', async()=>{
  const unified=load('data/test-profiles/five-stat-device-test-v1/unit-xp-pacing-policy.json') as UnitXpPacing,def=catalog.characters.find((u:any)=>u.id==='u45');
  expect(unified).toMatchObject({difficulty:'battle-average-level',existingRewards:'replace'});
  const unit={growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy};const seen=new Set<number>();
  for(let i=0;i<4;i++){
   // 정책의 적 레벨(1)과 원천(999)은 자유탐험에서 쓰지 않는다.
   encounter('adventure',1,999,0);policy.xpPacing=structuredClone(unified);const a=await actor(15),run:any=await adventure().start(a.id,{requestId:request(),ownedIds:a.units.map(u=>u.id)});
   const b=await create(a,run.adventureId),enemyLevel=b.state.fighters[1].level;seen.add(enemyLevel);expect(enemyLevel).toBeGreaterThanOrEqual(13);expect(enemyLevel).toBeLessThanOrEqual(17);
   const snap=JSON.parse((await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}})).snapshot);expect(snap.unitXpPacing.battleLevels).toEqual([15,15,15,enemyLevel]);
   const paid=await settle(a,await terminal(a,b,'win',[a.units[0].id])),w=averageLevelWin(unit,Math.floor((45+enemyLevel)/4),unified);
   expect(paid.settlement.xpByOwnedId).toEqual({[a.units[0].id]:w,[a.units[1].id]:Math.floor(w/2),[a.units[2].id]:Math.floor(w/2)});expect(paid.settlement.xpPolicy).toBe(unified.version);
  }
  expect(seen.size).toBeGreaterThanOrEqual(1);
 });

 it('통일 판: 수락한 의뢰 전투도 스토리 고정 보상 대신 전투 전원 평균 레벨 기준 금액을 쓴다', async()=>{
  const unified=load('data/test-profiles/five-stat-device-test-v1/unit-xp-pacing-policy.json') as UnitXpPacing,def=catalog.characters.find((u:any)=>u.id==='u45');
  policy.xpPacing=structuredClone(unified);const campaign=structuredClone(content);campaign.scenes=campaign.scenes.map((s:any)=>({...s,lines:[s.lines[0]]}));const first=campaign.encounters.find((e:any)=>e.id==='STORY_01_01');Object.assign(first,{speciesId:'u07',level:15,hpMultiplier:1,powerMultiplier:1});
  const commander=commanderPolicyReader(process.cwd(),'five-stat-device-test-v1'),story=()=>new RosterStory(db,()=>catalog,()=>policy,campaign,commander,()=>storyUnitPolicy);
  const a=await actor();let journal:any=await story().state(a.id);journal=await story().open(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01',phase:'intro'});await story().accept(a.id,{requestId:request(),revision:journal.revision,questId:'STORY_01'});
  const b:any=await story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id)});expect(b.state.fighters[1].level).toBe(15);
  const paid=await settle(a,await terminal(a,b,'win',[a.units[0].id])),w=averageLevelWin({growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy},15,unified);
  expect(w).not.toBe(storyUnitPolicy.rewards.STORY_01_01.winXp);expect(paid.settlement).toMatchObject({xpAwarded:0,xpEscrowed:w+2*Math.floor(w/2),unitXp:{delivery:'pending-return'}});
  const r:any=await adventure().read(a.id,b.state.adventureId);await adventure().return(a.id,{requestId:request(),adventureId:r.adventureId,revision:r.revision});
  expect(await storedXp(a)).toEqual([a.start+BigInt(w),a.start+BigInt(Math.floor(w/2)),a.start+BigInt(Math.floor(w/2))]);
 });

 it('통일 판: 연습전도 편성 전원에게 마무리 전액·나머지 절반, 패배는 0', async()=>{
  const unified=load('data/test-profiles/five-stat-device-test-v1/unit-xp-pacing-policy.json') as UnitXpPacing,def=catalog.characters.find((u:any)=>u.id==='u45');
  encounter('duel',40,2400,800);policy.xpPacing=structuredClone(unified);const a=await actor(40),b=await create(a);expect(b.state.fighters[1].level).toBe(40);
  const paid=await settle(a,await terminal(a,b,'win',[a.units[0].id])),w=averageLevelWin({growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy},40,unified);
  expect(paid.settlement.xpByOwnedId).toEqual({[a.units[0].id]:w,[a.units[1].id]:Math.floor(w/2),[a.units[2].id]:Math.floor(w/2)});
  const loser=await actor(40),lost=await settle(loser,await terminal(loser,await create(loser),'loss'));expect(lost.settlement.xpAwarded).toBe(0);expect(await storedXp(loser)).toEqual(loser.units.map(()=>loser.start));
 });

 it('the real profile reader and authenticated routes pin both new schedules, while client XP fields remain rejected',async()=>{
  process.env.ROSTER60_PROFILE='five-stat-device-test-v1';delete process.env.ROSTER60_STORY_PROFILE;const a=await actor(40,1),app=Fastify(),deployed=load('data/test-profiles/five-stat-device-test-v1/unit-xp-pacing-policy.json') as UnitXpPacing;expect(deployed).toMatchObject({winsPerLevel:1.5,distribution:'finisher-full-rest-half'});registerRoster60(app,db,process.cwd(),r=>r.headers.authorization===a.id?a.id:null,()=>true);
  const post=(path:string,payload:any)=>app.inject({method:'POST',url:'/my/api/native/roster60/'+path,headers:{authorization:a.id},payload});
  try{
   const denied=await post('battle/create',{requestId:request(),ownedId:a.units[0].id,encounterId:'training-40',xpAwarded:99999});expect(denied.statusCode).toBe(400);
   const created=await post('battle/create',{requestId:request(),ownedId:a.units[0].id,encounterId:'training-40'});expect(created.statusCode).toBe(200);const b=created.json(),row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}});expect(JSON.parse(row.snapshot).unitXpPacing.policy.version).toBe(deployed.version);
   const finished=await terminal(a,b),paid=await post('battle/settle',{requestId:request(),battleId:b.battleId,revision:finished.revision});expect(paid.statusCode).toBe(200);expect(paid.json().settlement.xpAwarded).toBe(Math.ceil(5060*2/3));
   expect((await post('pvp/party',{ownedIds:a.units.map(u=>u.id)})).statusCode).toBe(200);expect((await post('pvp/join',{requestId:request(),mode:'normal'})).statusCode).toBe(200);const queue=await db.roster60PvpQueue.findUniqueOrThrow({where:{playerId:a.id}});expect(JSON.parse(queue.normalXpSnapshot!).policy.xpPacing.version).toBe(deployed.version);expect(JSON.parse(queue.commanderXpSnapshot!).policy.rewardSchedule.rewards.normalWin).toBe(150);
   await post('pvp/cancel',{requestId:request()});
  }finally{await app.close();}
 });
});
