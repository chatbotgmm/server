import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {RosterPvp} from '../src/roster60-pvp.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {fighter} from '../src/roster60-battle.js';
import type {NormalXpPolicy} from '../src/progression/normal-xp.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const originalCatalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const originalBattle=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const originalXp=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8')) as NormalXpPolicy;
const skillIds=['learning-l1','learning-l5','learning-l10','learning-l24','learning-l40'];
const skillLevels=[1,5,10,24,40];

// These compact XP points and encounter pools are arithmetic fixtures only, not
// approved balance values or a replacement production growth/learning policy.
// Default reincarnation XP remains the real 70% requirement through level 60.
const anchors:[number,number][]=[[1,0],[5,100],[10,200],[24,300],[40,400],[60,450],[99,1000]];
function fixtureXp(level:number){
 for(let i=1;i<anchors.length;i++){
  const [fromLevel,fromXp]=anchors[i-1],[toLevel,toXp]=anchors[i];
  if(level<=toLevel)return fromXp+Math.floor((level-fromLevel)*(toXp-fromXp)/(toLevel-fromLevel));
 }
 throw Error('FIXTURE_LEVEL_OUTSIDE_CURVE');
}

suite('normal XP service settlement, learned skills and manual reincarnation (synthetic progression)',()=>{
 let db:PrismaClient,service:RosterPvp,rebirth:RosterReincarnation,catalog:any,battlePolicy:any,xpPolicy:NormalXpPolicy,now:number;
 const owners=new Set<string>();
 const request=()=>randomUUID();
 type Actor={playerId:string;ownedId:string};
 type Completed={battleId:string;command:{requestId:string;battleId:string;revision:number;action:{kind:'skill';skillId:string}};result:any};
 const restart=()=>new RosterPvp(db,()=>catalog,()=>battlePolicy,()=>now,()=>xpPolicy);

 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;
  if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');
  db=new PrismaClient({datasources:{db:{url}}});
 });
 beforeEach(()=>{
  catalog=structuredClone(originalCatalog);catalog.version='normal-xp-learning-isolated-fixture';
  const unit=catalog.characters.find((u:any)=>u.id==='u45');
  unit.growth=Array.from({length:99},(_,index)=>({totalXp:fixtureXp(index+1),maxMp:unit.combatGrowth[index].mp}));
  unit.skills=skillLevels.map((learnLevel,index)=>({id:skillIds[index],name:'학습 경계 시험 '+learnLevel,element:'NORMAL',mpCost:0,power:1,learnLevel}));
  delete unit.reincarnationXpPolicy;
  battlePolicy=structuredClone(originalBattle);battlePolicy.catalogVersion=catalog.version;
  const source=battlePolicy.encounters.find((e:any)=>e.mode==='duel'&&!e.tutorial);
  battlePolicy.encounters=[{...source,id:'learning-duel-fixture',mode:'duel',tutorial:false,speciesId:'u02',enemySpeciesIds:['u02'],level:1,winXp:100,lossXp:0}];
  xpPolicy={...structuredClone(originalXp),version:'normal-xp-learning-fixture-v2',catalogVersion:catalog.version};
  expect(xpPolicy).toMatchObject({rulesVersion:'normal-xp-v2',outcomePercent:{win:100,loss:50},capOverflow:'discard-overflow'});
  now=Date.now();service=restart();rebirth=new RosterReincarnation(db,()=>catalog);
 });
 afterEach(async()=>{
  if(!db)return;const ids=[...owners];
  await db.roster60PvpMatch.deleteMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]}});
  await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});
  await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});
  await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});
  await db.dailyProgress.deleteMany({where:{playerId:{in:ids}}});
  await db.player.deleteMany({where:{id:{in:ids}}});owners.clear();
 });
 afterAll(async()=>{if(db)await db.$disconnect();});

 async function actor(totalXp=0n,reincarnation=0):Promise<Actor>{
  const player=await db.player.create({data:{identity:'normal-learning-fixture:'+request(),peanut:5}});owners.add(player.id);
  const unit=await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u45',totalXp,reincarnation,source:'learning-fixture:'+request()}});
  await service.party(player.id,[unit.id]);return {playerId:player.id,ownedId:unit.id};
 }
 async function projection(actor:Actor){
  const owned=await db.roster60Owned.findUniqueOrThrow({where:{id:actor.ownedId}});
  const option=(await service.options(actor.playerId)).units.find(u=>u.ownedId===actor.ownedId);
  expect(option).toBeDefined();
  return {owned,option:option!,fighter:fighter(catalog,owned.speciesId,option!.level,owned.id,owned.reincarnation)};
 }
 async function expectLearning(actor:Actor,level:number,totalXp:bigint,skills:string[],reincarnation=0){
  const view=await projection(actor);
  expect(view.owned).toMatchObject({playerId:actor.playerId,totalXp,reincarnation,status:'AVAILABLE'});
  expect(view.option.level).toBe(level);expect(view.fighter.level).toBe(level);
  expect(view.fighter.skills.map(s=>s.id)).toEqual(skills);
  return view;
 }
 async function win(actor:Actor,unlearnedSkill?:string):Promise<Completed>{
  const before=await projection(actor);
  expect((await service.join(actor.playerId,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');
  now+=30_000;const current:any=await service.current(actor.playerId);
  expect(current.battle).toMatchObject({mode:'normal',opponentKind:'AI',status:'ACCEPTING'});
  const accepted:any=await service.accept(actor.playerId,{requestId:request(),battleId:current.battle.battleId,revision:current.battle.revision,accept:true});
  expect(accepted.state.fighters[0].level).toBe(before.option.level);
  expect(accepted.state.fighters[0].skills.map((s:any)=>s.id)).toEqual(before.fighter.skills.map(s=>s.id));
  if(unlearnedSkill)await expect(service.action(actor.playerId,{requestId:request(),battleId:accepted.battleId,revision:accepted.revision,action:{kind:'skill',skillId:unlearnedSkill}})).rejects.toMatchObject({code:'SKILL_NOT_EQUIPPED'});
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:accepted.battleId}}),state=JSON.parse(row.state);
  expect(row.player0Id).toBe(actor.playerId);expect(state.botSide).toBe(1);
  const chosen=before.fighter.skills[before.fighter.skills.length-1].id;
  // Only damage resolution is shortened in this disposable battle snapshot.
  // XP is never written by this helper: the real terminal settlement must grant it.
  state.parties[0][0].stats.speed=1000;state.parties[1][0].stats.speed=1;
  state.parties[0][0].skills.find((s:any)=>s.id===chosen).power=1_000_000;
  state.parties[1][0].defense.NORMAL='normal';state.parties[1][0].hp=1;
  state.pending[1]={kind:'attack'};
  await db.roster60PvpMatch.update({where:{id:row.id},data:{state:JSON.stringify(state)}});
  const command={requestId:request(),battleId:row.id,revision:accepted.revision,action:{kind:'skill' as const,skillId:chosen}};
  const result:any=await service.action(actor.playerId,command);
  expect(result.state.outcome).toBe('win');expect(result.settlement.reason).toBe('BATTLE');
  expect(result.settlement.xpDeferred).toBe(0);
  await service.settle(actor.playerId,{requestId:request(),battleId:row.id,revision:result.revision});
  expect((await service.current(actor.playerId)).status).toBe('idle');
  return {battleId:row.id,command,result};
 }
 async function ledger(actor:Actor,battleId:string){
  const rows=await db.roster60NormalXpLedger.findMany({where:{playerId:actor.playerId,roomId:battleId}});
  expect(rows).toHaveLength(1);expect(rows[0].ownedId).toBe(actor.ownedId);return rows[0];
 }

 it('real normal settlements cross 1/5/10/24/40 without dropping skills during multi-level gains',async()=>{
  const hero=await actor();await expectLearning(hero,1,0n,skillIds.slice(0,1));
  const stages=[{level:5,xp:100n,count:2},{level:10,xp:200n,count:3},{level:24,xp:300n,count:4},{level:40,xp:400n,count:5}];
  for(const [index,stage] of stages.entries()){
   const completed=await win(hero,index===0?'learning-l5':undefined);
   expect(completed.result.settlement).toMatchObject({xpRequested:100,xpAwarded:100,xpDeferred:0,xpDiscarded:0});
   expect(await ledger(hero,completed.battleId)).toMatchObject({requestedXp:100,appliedXp:100,deferredXp:0});
   service=restart();await expectLearning(hero,stage.level,stage.xp,skillIds.slice(0,stage.count));
   expect(await service.action(hero.playerId,completed.command)).toEqual(completed.result);
   await expectLearning(hero,stage.level,stage.xp,skillIds.slice(0,stage.count));
  }
 },30000);

 it('caps discard overflow, require manual rebirth, reset to level one and reopen skills from new XP only',async()=>{
  const hero=await actor(400n);await expectLearning(hero,40,400n,skillIds);
  const capped=await win(hero);
  expect(capped.result.settlement).toMatchObject({xpRequested:100,xpAwarded:50,xpDeferred:0,xpDiscarded:50});
  expect(await ledger(hero,capped.battleId)).toMatchObject({startingXp:'400',endingXp:'450',requestedXp:100,appliedXp:50,deferredXp:0,status:'CAP_CLAMPED'});
  await expectLearning(hero,60,450n,skillIds);
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:hero.playerId}})).toBeNull();
  const atCap=await win(hero);
  expect(atCap.result.settlement).toMatchObject({xpRequested:100,xpAwarded:0,xpDeferred:0,xpDiscarded:100});
  await expectLearning(hero,60,450n,skillIds);
  const ready=(await rebirth.preview(hero.playerId)).candidates.find(u=>u.ownedId===hero.ownedId) as any;
  expect(ready).toMatchObject({eligible:true,requiredLevel:60});
  expect(ready.preview.next).toMatchObject({level:1,totalXp:0,reincarnation:1});
  await expectLearning(hero,60,450n,skillIds); // Preview itself never resets the unit.
  const confirmation={ownedId:hero.ownedId,requestId:request(),catalogVersion:catalog.version,previewFingerprint:ready.previewFingerprint};
  const reset=await rebirth.confirm(hero.playerId,confirmation);
  expect(await rebirth.confirm(hero.playerId,confirmation)).toEqual(reset);
  expect(await db.roster60RebirthReceipt.count({where:{playerId:hero.playerId}})).toBe(1);
  await expectLearning(hero,1,0n,skillIds.slice(0,1),1);
  service=restart();expect(await service.action(hero.playerId,capped.command)).toEqual(capped.result);
  expect(await service.action(hero.playerId,atCap.command)).toEqual(atCap.result);
  await expectLearning(hero,1,0n,skillIds.slice(0,1),1);
  // A 75-XP source is another explicit arithmetic fixture. The actual default
  // 70% requirement yields these levels; no old discarded XP is a balance.
  battlePolicy.encounters[0].winXp=75;
  const reopened=[{level:5,xp:75n,count:2},{level:11,xp:150n,count:3},{level:26,xp:225n,count:4},{level:42,xp:300n,count:5}];
  for(const [index,stage] of reopened.entries()){
   const completed=await win(hero,index===0?'learning-l5':undefined);
   expect(completed.result.settlement).toMatchObject({xpRequested:75,xpAwarded:75,xpDeferred:0,xpDiscarded:0});
   await expectLearning(hero,stage.level,stage.xp,skillIds.slice(0,stage.count),1);
  }
  expect(await ledger(hero,capped.battleId)).toMatchObject({endingXp:'450',appliedXp:50,deferredXp:0});
  expect(await ledger(hero,atCap.battleId)).toMatchObject({endingXp:'450',appliedXp:0,deferredXp:0});
  expect((await db.roster60RebirthAccount.findUniqueOrThrow({where:{playerId:hero.playerId}})).successes).toBe(1);
 },30000);

 it('reborn 98 to 99 grants only the remaining point and never stores excess or skips learned skills',async()=>{
  // The synthetic curve's Lv99 threshold after real 70%-through-60 rounding is 894.
  const hero=await actor(893n,1);await expectLearning(hero,98,893n,skillIds,1);
  const capped=await win(hero);
  expect(capped.result.settlement).toMatchObject({xpRequested:100,xpAwarded:1,xpDeferred:0,xpDiscarded:99});
  expect(await ledger(hero,capped.battleId)).toMatchObject({startingXp:'893',endingXp:'894',appliedXp:1,deferredXp:0,status:'CAP_CLAMPED'});
  const end=await expectLearning(hero,99,894n,skillIds,1);expect(end.option.maxLevel).toBe(99);
  const repeated=await win(hero);expect(repeated.result.settlement).toMatchObject({xpAwarded:0,xpDeferred:0,xpDiscarded:100});
  service=restart();await expectLearning(hero,99,894n,skillIds,1);
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:hero.playerId}})).toBeNull();
 },30000);
});
