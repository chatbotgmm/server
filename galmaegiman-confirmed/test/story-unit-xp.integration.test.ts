import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
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

 for(const mode of ['story','adventure'] as const){
  it(mode+': equal entered shares, untouched reserve and distinct quest/account event survive all replays',async()=>{
   const f=fixture(mode),a=await actor(3);let b:any=await create(f,a);
   b=await f.battle().action(a.id,{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'switch',targetOwnedId:a.units[1].id}});b=await win(f,a.id,b);
   const cmd=request(),paid=await settle(f,a.id,b,cmd);expect(await settle(f,a.id,b,cmd)).toEqual(paid);expect(await settle(f,a.id,b)).toEqual(paid);
   if(mode==='adventure'){
    expect(await Promise.all(a.units.map(u=>xp(u.id)))).toEqual([0n,0n,0n]);
    await expect(f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:a.units.map(u=>u.id),adventureId:paid.state.adventureId})).rejects.toMatchObject({code:'STORY_OBJECTIVE_AWAITING_RETURN'});
   }
   await returnRun(f,a.id,paid);const sorted=a.units.slice(0,2).map(u=>u.id).sort();
   expect(await xp(sorted[0])).toBe(51n);expect(await xp(sorted[1])).toBe(50n);expect(await xp(a.units[2].id)).toBe(0n);
   const done=await complete(f,a.id);expect(done.commanderReward.xpAwarded).toBe(200);expect((await readCommanderState(db,a.id,commanderPolicy)).totalXp).toBe('200');
   await settle(f,a.id,b);await returnRun(f,a.id,paid);expect(await xp(sorted[0])).toBe(51n);expect(await xp(sorted[1])).toBe(50n);
   expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'STORY_QUEST'}})).toBe(1);
   expect(await db.roster60StoryBattleAward.count({where:{playerId:a.id,battleId:b.battleId}})).toBe(1);
  });
  it(mode+': an unconfigured accepted quest stays zero after later activation; a configured quest pins its amount',async()=>{
   const f=fixture(mode,101,false),a=await actor();await accept(f,a.id);const later=fixture(mode).get()!;f.set(later);
   let b:any=await f.story().createBattle(a.id,{requestId:request(),questId:'STORY_01',ownedIds:[a.units[0].id]});b=await win(f,a.id,b);await returnRun(f,a.id,await settle(f,a.id,b));expect(await xp(a.units[0].id)).toBe(0n);
   const g=fixture(mode),other=await actor();await accept(g,other.id);const changed=g.get()!;for(const reward of Object.values(changed.rewards))reward.winXp=9999;g.set(changed);
   let c:any=await g.story().createBattle(other.id,{requestId:request(),questId:'STORY_01',ownedIds:[other.units[0].id]});c=await win(g,other.id,c);await returnRun(g,other.id,await settle(g,other.id,c));expect(await xp(other.units[0].id)).toBe(101n);
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
