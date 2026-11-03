import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {RosterStory} from '../src/story/service.js';
import {RosterBattle,fighter} from '../src/roster60-battle.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {growthAt,instanceGrowthCurve} from '../src/battle/roster60-rules.js';
import {readCommanderState} from '../src/progression/commander.js';
import type {StoryUnitXpPolicy} from '../src/progression/story-unit-xp.js';
import Fastify from 'fastify';
import {registerRoster60} from '../src/roster60-routes.js';
import {RosterOwned} from '../src/roster60-owned.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const load=(file:string)=>JSON.parse(readFileSync(file,'utf8'));
const originalCatalog=load('data/test-profiles/five-stat-device-test-v1/catalog.json');
const basePolicy=load('data/test-profiles/five-stat-device-test-v1/battle-policy.json');
const originalContent=load('data/story/story-mode-v1.json');
const commanderPolicy=load('data/test-profiles/five-stat-device-test-v1/commander-policy.json');
const request=()=>randomUUID();

/** Small enemy/one-line scenes isolate settlement only. The separate campaign
 * test must retain the original enemy levels/stats, gates and full curve. */
suite('accepted quest unit XP settlement and account-event separation',()=>{
 let db:PrismaClient;const owners:string[]=[];
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{await db.dailyProgress.deleteMany({where:{playerId:{in:owners}}});await db.auditEvent.deleteMany({where:{playerId:{in:owners}}});await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();});
 function fixture(mode:'story'|'adventure',amount=101,enabled=true){
  const catalog=structuredClone(originalCatalog),content=structuredClone(originalContent);
  content.scenes=content.scenes.map((s:any)=>({...s,lines:[s.lines[0]]}));
  const e=content.encounters.find((e:any)=>e.id==='STORY_01_01');Object.assign(e,{speciesId:'u07',mode,kind:'ordinary',level:1,hpMultiplier:1,powerMultiplier:1});
  const enemy=catalog.characters.find((u:any)=>u.id==='u07');enemy.levelOneStats.hp=10;enemy.combatGrowth[0].hp=10;enemy.attack.power=0;enemy.skills=[];for(const key of Object.keys(enemy.defense))enemy.defense[key]='normal';
  let active:StoryUnitXpPolicy|null=enabled?{approved:true,version:'story-unit-arithmetic-test-v1',profileKind:'user-authorized-test-baseline',productionBalanceApproved:false,testValues:true,catalogVersion:catalog.version,contentVersion:content.version,scope:'accepted-quest-battles-only',distribution:'equal-actually-entered',loss:'zero-existing-story',surrender:'zero',draw:'zero',capOverflow:'discard-overflow',questCompletionUnitXp:'none',rewards:Object.fromEntries(content.encounters.map((e:any)=>[e.id,{winXp:amount,lossXp:0}]))}:null;
  const story=()=>new RosterStory(db,()=>catalog,()=>basePolicy,content,()=>commanderPolicy,()=>active);
  return {catalog,content,story,battle:()=>new RosterBattle(db,()=>catalog,()=>basePolicy),adventure:()=>new RosterAdventure(db,()=>catalog,fighter,undefined,()=>commanderPolicy),set:(p:StoryUnitXpPolicy|null)=>active=p,get:()=>structuredClone(active)};
 }
 async function actor(count=1,start=0n,cycle=0){const p=await db.player.create({data:{identity:'story-unit:'+request(),peanut:5}});owners.push(p.id);const units=[];for(let i=0;i<count;i++)units.push(await db.roster60Owned.create({data:{id:'story-unit-'+request()+'-'+i,playerId:p.id,speciesId:'u45',totalXp:start,reincarnation:cycle,source:'story-unit-fixture-'+i+'-'+request()}}));return {id:p.id,units};}
 async function accept(f:ReturnType<typeof fixture>,id:string){const s=f.story();let v:any=await s.state(id);v=await s.open(id,{requestId:request(),revision:v.revision,questId:'STORY_01',phase:'intro'});while(!v.scene.last)v=await s.advance(id,{requestId:request(),revision:v.revision,cursor:v.scene.cursor});return s.accept(id,{requestId:request(),revision:v.revision,questId:'STORY_01'});}
 async function create(f:ReturnType<typeof fixture>,a:Awaited<ReturnType<typeof actor>>){await accept(f,a.id);return f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id)}) as Promise<any>;}
 async function win(f:ReturnType<typeof fixture>,id:string,b:any){for(let i=0;b.status==='ACTIVE'&&i<100;i++)b=await f.battle().action(id,{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'attack'}});expect(b.state.outcome).toBe('win');return b;}
 async function settle(f:ReturnType<typeof fixture>,id:string,b:any,requestId=request()){return f.battle().settle(id,{requestId,battleId:b.battleId,revision:b.revision}) as Promise<any>;}
 async function returnRun(f:ReturnType<typeof fixture>,id:string,b:any){if(!b.state.adventureId)return null;const r:any=await f.adventure().read(id,b.state.adventureId);return f.adventure().return(id,{requestId:request(),adventureId:r.adventureId,revision:r.revision});}
 async function complete(f:ReturnType<typeof fixture>,id:string){const s=f.story();let v:any=await s.state(id);v=await s.open(id,{requestId:request(),revision:v.revision,questId:'STORY_01',phase:'return'});const command={requestId:request(),revision:v.revision,cursor:v.scene.cursor};const done:any=await s.advance(id,command);expect(await s.advance(id,command)).toEqual(done);return done;}
 const xp=async(id:string)=>(await db.roster60Owned.findUniqueOrThrow({where:{id}})).totalXp;

 it('the configured campaign amount persists for exact same-species owned IDs and a fresh owned-detail reader',async()=>{
  const configured=load('data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json') as StoryUnitXpPolicy;
  const f=fixture('story');f.set(structuredClone(configured));
  const definition=f.catalog.characters.find((unit:any)=>unit.id==='u45'),startingXp=BigInt(definition.growth[14].totalXp);
  const a=await actor(3,startingXp),other=await actor(1,startingXp),before=await Promise.all(a.units.map(unit=>xp(unit.id)));
  let b:any=await create(f,a);
  b=await f.battle().action(a.id,{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'switch',targetOwnedId:a.units[1].id}});
  b=await win(f,a.id,b);
  expect(await Promise.all(a.units.map(unit=>xp(unit.id)))).toEqual(before);
  const requestId=request(),paid:any=await settle(f,a.id,b,requestId);
  expect(await settle(f,a.id,b,requestId)).toEqual(paid);expect(await settle(f,a.id,b)).toEqual(paid);
  // 배포 정책 v2: 이긴 순간 나와 있던 units[1]은 전액, 출전했던 units[0]과 미출전 units[2]는 절반(내림).
  expect(configured.distribution).toBe('finisher-full-rest-half');
  const amount=configured.rewards.STORY_01_01.winXp,finisher=a.units[1].id,total=amount+2*Math.floor(amount/2),entered=a.units.slice(0,2).map(unit=>unit.id).sort();
  const expected=Object.fromEntries(a.units.map(unit=>[unit.id,startingXp+BigInt(unit.id===finisher?amount:Math.floor(amount/2))]));
  expect(paid.settlement.policyVersion).toBe(configured.version);expect(paid.settlement.xpAwarded).toBe(total);
  expect(paid.settlement.unitXp.units.reduce((sum:number,unit:any)=>sum+unit.appliedXp,0)).toBe(total);
  const fresh=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});
  try{
   const reader=new RosterOwned(fresh,()=>f.catalog),reopened=[];
   for(const unit of a.units){
    const current:any=await reader.read(a.id,unit.id),persisted=await fresh.roster60Owned.findUniqueOrThrow({where:{id:unit.id}});
    expect(persisted.totalXp).toBe(expected[unit.id]);expect(current.unit.ownedId).toBe(unit.id);
    expect(String(current.unit.growth.totalXp)).toBe(expected[unit.id].toString());
    expect(current.unit.level).toBe(growthAt(Number(expected[unit.id]),definition.growth).level);
    expect(persisted.reincarnation).toBe(0);reopened.push({ownedId:unit.id,speciesId:unit.speciesId,beforeTotalXp:startingXp.toString(),afterTotalXp:persisted.totalXp.toString(),level:current.unit.level,xpProgress:paid.settlement.unitXp.units.find((reward:any)=>reward.ownedId===unit.id)?.xpProgress});
   }
   expect((await fresh.roster60Owned.findUniqueOrThrow({where:{id:other.units[0].id}})).totalXp).toBe(startingXp);
   await expect(reader.read(other.id,a.units[0].id)).rejects.toMatchObject({code:'UNIT_NOT_FOUND'});
   expect(await fresh.roster60StoryBattleAward.count({where:{playerId:a.id,battleId:b.battleId}})).toBe(1);
   const directory=process.env.XP_GRANT_AUDIT_OUT;
   if(directory){mkdirSync(directory,{recursive:true});writeFileSync(join(directory,'configured-owned-id-grant.json'),JSON.stringify({scope:'isolated SQLite and minimal enemy fixture; original configured XP amount and unit growth; no operating data',policyVersion:configured.version,sourceEncounter:'STORY_01_01',configuredWinXp:amount,settlementXpAwarded:paid.settlement.xpAwarded,enteredOwnedIds:entered,unenteredOwnedId:a.units[2].id,foreignOwnerUnitUnchanged:true,replayedRequestAndBattleDoNotDuplicate:true,freshPrismaOwnedDetailMatches:true,units:reopened},null,2)+'\n');}
  }finally{await fresh.$disconnect();}
 });

 for(const mode of ['story','adventure'] as const){
  it(mode+': equal entered shares, untouched reserve and distinct quest/account event survive all replays',async()=>{
   const f=fixture(mode),a=await actor(3);let b:any=await create(f,a);
   b=await f.battle().action(a.id,{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'switch',targetOwnedId:a.units[1].id}});b=await win(f,a.id,b);
   const cmd=request(),paid=await settle(f,a.id,b,cmd);expect(await settle(f,a.id,b,cmd)).toEqual(paid);expect(await settle(f,a.id,b)).toEqual(paid);
   if(mode==='adventure'){
    expect(await Promise.all(a.units.map(u=>xp(u.id)))).toEqual([0n,0n,0n]);
    expect(paid.settlement.unitXp.units.every((unit:any)=>unit.xpProgress===undefined)).toBe(true);
    await expect(f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id),adventureId:paid.state.adventureId})).rejects.toMatchObject({code:'STORY_OBJECTIVE_AWAITING_RETURN'});
   }
   const returned:any=await returnRun(f,a.id,paid);const sorted=a.units.slice(0,2).map(u=>u.id).sort();
   for(const ownedId of sorted){const progress=mode==='story'?paid.settlement.unitXp.units.find((unit:any)=>unit.ownedId===ownedId).xpProgress:returned.settlement.xpProgressByOwnedId[ownedId];expect(progress.before.totalXp).toBe('0');expect(progress.after.totalXp).toBe(String(ownedId===sorted[0]?51:50));const expected=growthAt(Number(progress.after.totalXp),f.catalog.characters.find((u:any)=>u.id==='u45').growth);expect(progress.after.level).toBe(expected.level);expect(progress.after.nextLevelTotalXp).toBe(expected.xpToNext===null?null:String(expected.totalXp+expected.xpToNext));}
   expect(await xp(sorted[0])).toBe(51n);expect(await xp(sorted[1])).toBe(50n);expect(await xp(a.units[2].id)).toBe(0n);
   const done=await complete(f,a.id);expect(done.commanderReward.xpAwarded).toBe(200);expect((await readCommanderState(db,a.id,commanderPolicy)).totalXp).toBe('200');
   await settle(f,a.id,b);await returnRun(f,a.id,paid);expect(await xp(sorted[0])).toBe(51n);expect(await xp(sorted[1])).toBe(50n);
   expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'STORY_QUEST'}})).toBe(1);
   expect(await db.roster60StoryBattleAward.count({where:{playerId:a.id,battleId:b.battleId}})).toBe(1);
  });
  it(mode+': an old null or missing acceptance pins the current policy for a new battle, including concurrent restart/replay',async()=>{
   const f=fixture(mode,101,false),a=await actor();await accept(f,a.id);
   if(mode==='adventure'){const row=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}}),state=JSON.parse(row.state);delete state.accepted.STORY_01.unitXpPolicy;await db.roster60StoryAccount.update({where:{playerId:a.id},data:{state:JSON.stringify(state)}});}
   f.set(fixture(mode).get()!);
   const command={requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]},fresh=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});
   let b:any;
   try{const restarted=new RosterStory(fresh,()=>f.catalog,()=>basePolicy,f.content,()=>commanderPolicy,()=>f.get()),results=await Promise.all([f.story().createBattle(a.id,command),restarted.createBattle(a.id,command)]);expect(results[0]).toEqual(results[1]);b=results[0];}finally{await fresh.$disconnect();}
   const pinned=JSON.parse((await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}})).snapshot);expect(pinned.story.unitXpPolicy.rewards.STORY_01_01.winXp).toBe(101);
   const current=f.get()!;for(const reward of Object.values(current.rewards))reward.winXp=9999;f.set(current);
   expect(await f.story().createBattle(a.id,command)).toEqual(b);
   b=await win(f,a.id,b);const paid=await settle(f,a.id,b);if(mode==='adventure')expect(await xp(a.units[0].id)).toBe(0n);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);
   await settle(f,a.id,b);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);
   const g=fixture(mode),other=await actor();await accept(g,other.id);const changed=g.get()!;for(const reward of Object.values(changed.rewards))reward.winXp=9999;g.set(changed);
   let c:any=await g.story().createBattle(other.id,{requestId:request(),questId:'STORY_01',ownedIds:[other.units[0].id]});c=await win(g,other.id,c);await returnRun(g,other.id,await settle(g,other.id,c));expect(await xp(other.units[0].id)).toBe(101n);
  });
  it(mode+': a still-unconfigured new reservation keeps its original zero reward',async()=>{
   const f=fixture(mode,101,false),a=await actor(),b=await win(f,a.id,await create(f,a)),row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}});
   expect(JSON.parse(row.snapshot).story.unitXpPolicy).toBeNull();expect(JSON.parse(row.snapshot).storyUnitXp).toBeUndefined();
   const paid=await settle(f,a.id,b);expect(paid.settlement.xpAwarded).toBe(0);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(0n);
  });
  it(mode+': late activation preserves active, finished and settled old-zero snapshots/receipts and pays only the next battle',async()=>{
   const f=fixture(mode,101,false),a=await actor(),q=f.content.quests.find((q:any)=>q.id==='STORY_01'),second=f.content.encounters.find((e:any)=>e.id==='STORY_02_01');
   Object.assign(second,{speciesId:'u07',mode,kind:'ordinary',level:1,hpMultiplier:1,powerMultiplier:1});delete second.villainProfileId;q.objective={kind:'adventure_win',wins:2,encounterIds:['STORY_01_01','STORY_02_01']};
   await accept(f,a.id);const originalCommand={requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]};let old:any=await f.story().createBattle(a.id,originalCommand),oldCreate=structuredClone(old);
   const before=await db.roster60Battle.findUniqueOrThrow({where:{id:old.battleId}}),oldReceipt=await db.roster60StoryReceipt.findUniqueOrThrow({where:{playerId_requestId:{playerId:a.id,requestId:originalCommand.requestId}}});
   f.set(fixture(mode).get()!);
   // Simulate a process stopping after the inner battle commit but before the
   // workflow's final receipt write. Recovery must keep that old battle at zero.
   const progress=JSON.parse((await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}})).state);
   await db.roster60StoryReceipt.update({where:{playerId_requestId:{playerId:a.id,requestId:originalCommand.requestId}},data:{result:JSON.stringify({pending:true,contentVersion:f.content.version,questId:'STORY_01',acceptToken:progress.accepted.STORY_01.token,encounterId:'STORY_01_01',...(old.state.adventureId?{adventureId:old.state.adventureId}:{})})}});
   expect(await f.story().createBattle(a.id,originalCommand)).toEqual(oldCreate);expect(JSON.parse((await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}})).state).accepted.STORY_01.unitXpPolicy).toBeNull();
   await expect(f.story().createBattle(a.id,{...originalCommand,requestId:request()})).rejects.toMatchObject({code:'STORY_BATTLE_ACTIVE'});
   expect(await f.story().createBattle(a.id,originalCommand)).toEqual(oldCreate);old=await win(f,a.id,old);
   await expect(f.story().createBattle(a.id,{...originalCommand,requestId:request()})).rejects.toMatchObject({code:'STORY_BATTLE_ACTIVE'});
   const settlementRequest=request(),oldPaid=await settle(f,a.id,old,settlementRequest);expect(oldPaid.settlement.xpAwarded).toBe(0);expect(await xp(a.units[0].id)).toBe(0n);
   let next:any=await f.story().createBattle(a.id,{...originalCommand,requestId:request()});const newSnapshot=JSON.parse((await db.roster60Battle.findUniqueOrThrow({where:{id:next.battleId}})).snapshot);expect(newSnapshot.story.unitXpPolicy.rewards.STORY_02_01.winXp).toBe(101);
   f.set(null);next=await win(f,a.id,next);const paid=await settle(f,a.id,next);if(mode==='adventure')expect(await xp(a.units[0].id)).toBe(0n);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);
   expect(await f.story().createBattle(a.id,originalCommand)).toEqual(oldCreate);expect(await settle(f,a.id,old,settlementRequest)).toEqual(oldPaid);expect(await settle(f,a.id,old)).toEqual(oldPaid);expect(await xp(a.units[0].id)).toBe(101n);
   expect((await db.roster60Battle.findUniqueOrThrow({where:{id:old.battleId}})).snapshot).toBe(before.snapshot);
   expect((await db.roster60StoryReceipt.findUniqueOrThrow({where:{playerId_requestId:{playerId:a.id,requestId:originalCommand.requestId}}})).result).toBe(oldReceipt.result);
  });
  it(mode+': an invalid current policy rolls back a new reservation and the same request recovers with a valid policy',async()=>{
   const f=fixture(mode,101,false),a=await actor();await accept(f,a.id);const invalid=fixture(mode).get()!;invalid.catalogVersion='wrong-catalog';f.set(invalid);
   const command={requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]};await expect(f.story().createBattle(a.id,command)).rejects.toMatchObject({code:'STORY_UNIT_XP_POLICY_INVALID'});
   const row=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}}),state=JSON.parse(row.state);expect(state.accepted.STORY_01.unitXpPolicy).toBeNull();expect(state.departure).toBeUndefined();
   expect(await db.roster60StoryReceipt.count({where:{playerId:a.id,requestId:command.requestId}})).toBe(0);expect(await db.roster60Battle.count({where:{playerId:a.id}})).toBe(0);expect(await xp(a.units[0].id)).toBe(0n);
   f.set(fixture(mode).get()!);let b:any=await f.story().createBattle(a.id,command);f.set(null);b=await win(f,a.id,b);await returnRun(f,a.id,await settle(f,a.id,b));expect(await xp(a.units[0].id)).toBe(101n);
  });
  it(mode+': two Prisma clients settle the same event concurrently without duplicating XP or quest wins',async()=>{
   const f=fixture(mode),a=await actor(),b=await win(f,a.id,await create(f,a)),otherDb=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});
   try{
    const other=new RosterBattle(otherDb,()=>f.catalog,()=>basePolicy),cmd={requestId:request(),battleId:b.battleId,revision:b.revision};
    const results=await Promise.all([f.battle().settle(a.id,cmd),other.settle(a.id,cmd)]);expect(results[0]).toEqual(results[1]);
    await Promise.all([settle(f,a.id,b),other.settle(a.id,{...cmd,requestId:request()})]);await returnRun(f,a.id,results[0]);expect(await xp(a.units[0].id)).toBe(101n);
    const state:any=await f.story().state(a.id);expect(state.quests.find((q:any)=>q.id==='STORY_01').objective.wins).toBe(1);expect(await db.roster60StoryBattleAward.count({where:{playerId:a.id,battleId:b.battleId}})).toBe(1);
   }finally{await otherDb.$disconnect();}
  });
  it(mode+': a member-state failure rolls back all unit and quest awards, then the exact command can recover',async()=>{
   const f=fixture(mode),a=await actor(2),b=await win(f,a.id,await create(f,a)),cmd={requestId:request(),battleId:b.battleId,revision:b.revision};
   await db.roster60Owned.update({where:{id:a.units[1].id},data:{totalXp:1n}});
   await expect(f.battle().settle(a.id,cmd)).rejects.toMatchObject({code:'UNIT_STATE_CHANGED'});
   expect(await xp(a.units[0].id)).toBe(0n);expect((await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}})).status).toBe('FINISHED');expect(await db.roster60StoryBattleAward.count({where:{battleId:b.battleId}})).toBe(0);
   const s:any=await f.story().state(a.id);expect(s.quests.find((q:any)=>q.id==='STORY_01').objective.wins).toBe(0);
   await db.roster60Owned.update({where:{id:a.units[1].id},data:{totalXp:0n}});const paid:any=await f.battle().settle(a.id,cmd);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);expect(await xp(a.units[1].id)).toBe(0n);
  });
  it(mode+': reaching the unit cap discards overflow and does not bank, auto-rebirth or pay twice',async()=>{
   const f=fixture(mode),def=f.catalog.characters.find((u:any)=>u.id==='u45'),cap=BigInt(def.growth[59].totalXp),a=await actor(1,cap-1n);
   const b=await win(f,a.id,await create(f,a)),paid=await settle(f,a.id,b);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(cap);await complete(f,a.id);
   const r=new RosterReincarnation(db,()=>f.catalog),preview:any=await r.preview(a.id),p=preview.candidates.find((p:any)=>p.ownedId===a.units[0].id);expect(p.eligible).toBe(true);
   await r.confirm(a.id,{requestId:request(),ownedId:a.units[0].id,catalogVersion:f.catalog.version,previewFingerprint:p.previewFingerprint});expect(await xp(a.units[0].id)).toBe(0n);
   await settle(f,a.id,b);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(0n);expect((await readCommanderState(db,a.id,commanderPolicy)).totalXp).toBe('200');
   const owned=await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}});expect(owned.reincarnation).toBe(1);expect(fighter(f.catalog,owned.speciesId,1,owned.id,1).skills.map(s=>s.id)).toEqual(def.skills.filter((s:any)=>(s.learnLevel??1)<=1).map((s:any)=>s.id));
  });
  it(mode+': multiple gained levels learn every eligible skill while a reincarnated unit uses its own 99-level cap',async()=>{
   const f=fixture(mode,1000000),a=await actor();let b=await win(f,a.id,await create(f,a));await returnRun(f,a.id,await settle(f,a.id,b));
   const def=f.catalog.characters.find((u:any)=>u.id==='u45');expect(await xp(a.units[0].id)).toBe(BigInt(def.growth[59].totalXp));
   const learned=fighter(f.catalog,'u45',60,a.units[0].id).skills.map(s=>s.id);expect(learned).toEqual(def.skills.filter((s:any)=>(s.learnLevel??1)<=60).map((s:any)=>s.id));
   const curve=instanceGrowthCurve(def.growth,1,def.reincarnationXpPolicy),cap=BigInt(curve[98].totalXp),reborn=await actor(1,cap-1n,1);b=await win(f,reborn.id,await create(f,reborn));await returnRun(f,reborn.id,await settle(f,reborn.id,b));expect(await xp(reborn.units[0].id)).toBe(cap);expect(growthAt(Number(cap),def.growth,1,def.reincarnationXpPolicy).level).toBe(99);
  });
  for(const terminal of ['loss','escape','draw'] as const)it(mode+': '+terminal+' preserves the existing zero story payout and cannot advance the objective',async()=>{
   const f=fixture(mode),a=await actor(),b:any=await create(f,a);const row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}}),state=JSON.parse(row.state);
   // Server-terminal snapshot fixture isolates payout, not battle difficulty.
   state.outcome=terminal==='escape'?'escaped':terminal;state.surrendered=terminal==='escape';if(terminal==='loss')state.fighters[0].hp=0;
   await db.roster60Battle.update({where:{id:b.battleId},data:{state:JSON.stringify(state),status:'FINISHED'}});const paid=await settle(f,a.id,{...b,status:'FINISHED'});expect(await xp(a.units[0].id)).toBe(0n);
   if(mode==='adventure'){
    const run:any=await f.adventure().read(a.id,b.state.adventureId);expect(Object.values(run.pendingRewards.xp).every(n=>n===0)).toBe(true);
    if(run.status==='ACTIVE')await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(0n);
   }
   const s:any=await f.story().state(a.id);expect(s.quests.find((q:any)=>q.id==='STORY_01').objective.wins).toBe(0);expect((await readCommanderState(db,a.id,commanderPolicy)).totalXp).toBe('0');
  });
 }
 it('late activation cannot repeat an already-won legacy adventure objective while return is pending',async()=>{
  const f=fixture('adventure',101,false),a=await actor(),old=await win(f,a.id,await create(f,a)),paid=await settle(f,a.id,old);expect(await xp(a.units[0].id)).toBe(0n);f.set(fixture('adventure').get()!);
  const command={requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]};await expect(f.story().createBattle(a.id,command)).rejects.toMatchObject({code:'STORY_OBJECTIVE_AWAITING_RETURN'});
  expect(await db.roster60Battle.count({where:{playerId:a.id}})).toBe(1);expect(await db.roster60StoryReceipt.count({where:{playerId:a.id,requestId:command.requestId}})).toBe(0);expect(JSON.parse((await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}})).state).accepted.STORY_01.unitXpPolicy).toBeNull();
  await returnRun(f,a.id,paid);await complete(f,a.id);expect(await xp(a.units[0].id)).toBe(0n);expect(await db.roster60StoryBattleAward.count({where:{playerId:a.id}})).toBe(1);
 });
 for(const activation of ['before','after'] as const)it('a failed pre-battle adventure link resumes once with policy activation '+activation+' the reservation',async()=>{
  const f=fixture('adventure',101,false),a=await actor();await accept(f,a.id);if(activation==='before')f.set(fixture('adventure').get()!);
  const command={requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]};
  await db.$executeRawUnsafe(`CREATE TRIGGER xp_link_test_failure BEFORE UPDATE ON Roster60StoryAccount WHEN NEW.playerId='${a.id}' AND instr(NEW.state, '"adventureId"')>0 BEGIN SELECT RAISE(ABORT, 'xp-link-test-only-failure'); END`);
  try{await expect(f.story().createBattle(a.id,command)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER xp_link_test_failure');}
  const oldRun=await db.roster60Adventure.findFirstOrThrow({where:{playerId:a.id}}),row=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}}),progress=JSON.parse(row.state);
  expect(progress.departure.requestId).toBe(command.requestId);expect(!!progress.accepted.STORY_01.unitXpPolicy).toBe(activation==='before');expect(await db.roster60Battle.count({where:{playerId:a.id}})).toBe(0);expect(await xp(a.units[0].id)).toBe(0n);
  if(activation==='after')f.set(fixture('adventure').get()!);else f.set(null);
  const fresh=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});let b:any;
  try{b=await new RosterStory(fresh,()=>f.catalog,()=>basePolicy,f.content,()=>commanderPolicy,()=>f.get()).createBattle(a.id,command);}finally{await fresh.$disconnect();}
  expect(b.state.adventureId).toBe(oldRun.id);expect(await f.story().createBattle(a.id,command)).toEqual(b);expect(await db.roster60Adventure.count({where:{playerId:a.id}})).toBe(1);expect(await db.roster60Battle.count({where:{playerId:a.id}})).toBe(1);
  b=await win(f,a.id,b);const paid=await settle(f,a.id,b);expect(await xp(a.units[0].id)).toBe(0n);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);await returnRun(f,a.id,paid);expect(await xp(a.units[0].id)).toBe(101n);
 });
 it('two encounters in one adventure accumulate only actual shares and clamp each unit across pending rewards',async()=>{
  const f=fixture('adventure'),a=await actor(3),q=f.content.quests.find((q:any)=>q.id==='STORY_01'),second=f.content.encounters.find((e:any)=>e.id==='STORY_02_01');
  Object.assign(second,{speciesId:'u07',mode:'adventure',kind:'ordinary',level:1,hpMultiplier:1,powerMultiplier:1});q.objective={kind:'adventure_win',wins:2,encounterIds:['STORY_01_01','STORY_02_01']};
  const cap=BigInt(f.catalog.characters.find((u:any)=>u.id==='u45').growth[59].totalXp);await db.roster60Owned.update({where:{id:a.units[0].id},data:{totalXp:cap-1n}});
  let b:any=await create(f,a);await settle(f,a.id,await win(f,a.id,b));
  b=await f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id)});
  b=await f.battle().action(a.id,{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'switch',targetOwnedId:a.units[1].id}});const paid=await settle(f,a.id,await win(f,a.id,b));
  expect(await xp(a.units[0].id)).toBe(cap-1n);expect(await xp(a.units[1].id)).toBe(0n);await returnRun(f,a.id,paid);
  const rank=a.units.slice(0,2).map(u=>u.id).sort().indexOf(a.units[1].id),expected=rank===0?51n:50n;
  expect(await xp(a.units[0].id)).toBe(cap);expect(await xp(a.units[1].id)).toBe(expected);expect(await xp(a.units[2].id)).toBe(0n);await complete(f,a.id);
  expect((await readCommanderState(db,a.id,commanderPolicy)).totalXp).toBe('200');expect(await db.roster60StoryBattleAward.count({where:{playerId:a.id}})).toBe(2);
 });
 it('completed quests and their independent material battles do not inherit the accepted-quest XP policy',async()=>{
  const f=fixture('adventure'),a=await actor(),b=await win(f,a.id,await create(f,a)),paid=await settle(f,a.id,b);await returnRun(f,a.id,paid);await complete(f,a.id);const before=await xp(a.units[0].id);
  // Independent material selection accepts black adventure encounters only.
  f.content.encounters.find((e:any)=>e.id==='STORY_01_01').kind='black';
  let next:any=await f.story().createBattle(a.id,{requestId:request(),encounterId:'STORY_01_01',ownedIds:[a.units[0].id]});next=await win(f,a.id,next);await returnRun(f,a.id,await settle(f,a.id,next));expect(await xp(a.units[0].id)).toBe(before);
  await expect(f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]})).rejects.toMatchObject({code:'STORY_QUEST_NOT_ACTIVE'});
 });
 it('authenticated profile routes load and pin server policy while rejecting client XP fields',async()=>{
  const a=await actor(),oldProfile=process.env.ROSTER60_PROFILE,oldStory=process.env.ROSTER60_STORY_PROFILE,app=Fastify();
  process.env.ROSTER60_PROFILE='five-stat-device-test-v1';process.env.ROSTER60_STORY_PROFILE='story-device-test-v1';
  try{
   registerRoster60(app,db,process.cwd(),r=>r.headers.authorization===a.id?a.id:null,()=>true);
   const prefix='/my/api/native/roster60/',headers={authorization:a.id};
   const post=async(path:string,payload:any)=>{const r=await app.inject({method:'POST',url:prefix+path,headers,payload});expect(r.statusCode,r.body).toBe(200);return r.json();};
   const state=await app.inject({url:prefix+'story/state',headers});expect(state.statusCode).toBe(200);let s=state.json();
   s=await post('story/open',{requestId:request(),revision:s.revision,questId:'STORY_01',phase:'intro'});
   while(!s.scene.last)s=await post('story/advance',{requestId:request(),revision:s.revision,cursor:s.scene.cursor});
   s=await post('story/accept',{requestId:request(),revision:s.revision,questId:'STORY_01'});
   const rejected=await app.inject({method:'POST',url:prefix+'story/battle/create',headers,payload:{requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id],xp:99999}});expect(rejected.statusCode).toBe(400);
   const b=await post('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]});
   const row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}}),snap=JSON.parse(row.snapshot),configured=load('data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json');
   expect(snap.story.unitXpPolicy.version).toBe(configured.version);expect(snap.story.unitXpPolicy.rewards.STORY_01_01).toEqual(configured.rewards.STORY_01_01);expect(await xp(a.units[0].id)).toBe(0n);
   expect((await app.inject({url:prefix+'story/state',headers:{authorization:'other'}})).statusCode).toBe(401);
  }finally{await app.close();if(oldProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=oldProfile;if(oldStory===undefined)delete process.env.ROSTER60_STORY_PROFILE;else process.env.ROSTER60_STORY_PROFILE=oldStory;}
 });
});
