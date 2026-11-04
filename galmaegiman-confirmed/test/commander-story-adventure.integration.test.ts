import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterStory} from '../src/story/service.js';
import type {StoryContent} from '../src/story/types.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterBattle,fighter,type BattlePolicy} from '../src/roster60-battle.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import {readCommanderState,validateCommanderPolicy,type CommanderPolicy} from '../src/progression/commander.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('commander XP uses authoritative quest completion and independent adventure return events',()=>{
 let db:PrismaClient,catalog:RosterCatalog,battlePolicy:BattlePolicy,config:CommanderPolicy,content:StoryContent;
 const owners:string[]=[],request=()=>randomUUID();
 const load=(file:string)=>JSON.parse(readFileSync(file,'utf8'));
 const story=(c=sceneContent(),policy:()=>CommanderPolicy|null=()=>config)=>new RosterStory(db,()=>catalog,()=>battlePolicy,c,policy);
 const adventure=(policy:()=>CommanderPolicy|null=()=>config)=>new RosterAdventure(db,()=>catalog,fighter,undefined,policy);
 const battle=()=>new RosterBattle(db,()=>catalog,()=>battlePolicy);
 // These scene-only objectives isolate completion/account accounting, not combat difficulty or story pacing.
 function sceneContent(){const c=structuredClone(content);c.policy.mainLifetimeGates={};c.scenes=c.scenes.map(s=>({...s,lines:[s.lines[0]]}));c.quests=c.quests.map(q=>({...q,darkGangLifetimeRequired:0,objective:{kind:'scene',wins:0,encounterIds:[]}}));return c;}
 async function user(count=1){const p=await db.player.create({data:{identity:'commander-story:'+request()}});owners.push(p.id);const units=[];for(let i=0;i<count;i++)units.push(await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',source:request()}}));return {id:p.id,units};}
 async function open(s:RosterStory,id:string,questId:string,phase:'intro'|'return'|'replay'){const state=await s.state(id);return s.open(id,{requestId:request(),revision:state.revision,questId,phase});}
 async function finishScene(s:RosterStory,id:string){let state:any=await s.state(id);while(state.scene){if(state.scene.phase==='intro'&&state.scene.last)break;state=await s.advance(id,{requestId:request(),revision:state.revision,cursor:state.scene.cursor});}return state;}
 async function accept(s:RosterStory,id:string,questId:string){await open(s,id,questId,'intro');const state=await finishScene(s,id);return s.accept(id,{requestId:request(),revision:state.revision,questId});}
 async function complete(s:RosterStory,id:string,questId:string){await accept(s,id,questId);await open(s,id,questId,'return');return finishScene(s,id);}
 async function start(id:string,ownedIds:string[],service=adventure()):Promise<any>{return service.start(id,{requestId:request(),ownedIds});}
 async function returnRun(id:string,runId:string,service=adventure()):Promise<any>{const run:any=await service.read(id,runId);return service.return(id,{requestId:request(),adventureId:runId,revision:run.revision});}
 async function win(id:string,view:any){let result=view;for(let i=0;result.status==='ACTIVE'&&i<100;i++)result=await battle().action(id,{requestId:request(),battleId:result.battleId,revision:result.revision,action:{kind:'attack'}});expect(result.state.outcome).toBe('win');return battle().settle(id,{requestId:request(),battleId:result.battleId,revision:result.revision});}
 async function clearIndependent(id:string,run:any){return win(id,await battle().create(id,{requestId:request(),encounterId:'commander-independent',ownedId:run.party[0].ownedId,reserveIds:run.party.slice(1).map((f:any)=>f.ownedId),adventureId:run.adventureId}));}
 const commander=(id:string)=>readCommanderState(db,id,config);
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable test DB only');
  db=new PrismaClient({datasources:{db:{url}}});catalog=load('data/test-profiles/five-stat-device-test-v1/catalog.json');battlePolicy=load('data/test-profiles/five-stat-device-test-v1/battle-policy.json');
  config=validateCommanderPolicy(load('data/test-profiles/five-stat-device-test-v1/commander-policy.json'));content=load('data/story/story-mode-v1.json');
  expect(content.version).toBe(config.story.contentVersion);
  const enemy=catalog.characters.find(c=>c.id==='u07')!;Object.assign((enemy as any).levelOneStats,{hp:1});for(const stats of (enemy as any).combatGrowth)stats.hp=1;/* 자유탐험 적은 내 평균 ±2 레벨 */(enemy as any).attack.power=1;enemy.skills=[];
  for(const element of Object.keys(enemy.defense))enemy.defense[element as keyof typeof enemy.defense]='normal';
  battlePolicy.encounters=[{id:'commander-independent',speciesId:'u07',level:1,tutorial:false,mode:'adventure',winXp:11,lossXp:0}];
 });
 afterAll(async()=>{
  if(!db)return;await db.playerItem.deleteMany({where:{playerId:{in:owners}}});await db.auditEvent.deleteMany({where:{playerId:{in:owners}}});await db.dailyProgress.deleteMany({where:{playerId:{in:owners}}});await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();
 });

 it('awards a main/resident completion once across receipt replay, new requests, scene replay and reconnect',async()=>{
  const a=await user(),s=story(),questId=config.story.mainQuestIds[0];await accept(s,a.id,questId);let state:any=await open(s,a.id,questId,'return');
  const command={requestId:request(),revision:state.revision,cursor:state.scene.cursor},paid:any=await s.advance(a.id,command);
  expect(paid.commanderReward).toMatchObject({status:'APPLIED',xpAwarded:config.rewards.storyQuest});expect(paid.commander.totalXp).toBe(String(config.rewards.storyQuest));
  expect(await s.advance(a.id,command)).toEqual(paid);await expect(s.advance(a.id,{...command,requestId:request()})).rejects.toMatchObject({code:'STORY_STALE_REVISION'});
  await open(s,a.id,questId,'replay');await finishScene(s,a.id);expect((await story().state(a.id)).commander.totalXp).toBe(String(config.rewards.storyQuest));
  expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'STORY_QUEST',eventId:content.version+':'+questId}})).toBe(1);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}})).totalXp).toBe(0n);
  // Resident unlocks are server content; completion must use the resident event kind.
  const residentContent=sceneContent(),resident=residentContent.quests.find(q=>q.kind==='resident')!;resident.unlockAfter=[];
  await complete(story(residentContent),a.id,resident.id);
  expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'RESIDENT_QUEST',eventId:content.version+':'+resident.id}})).toBe(1);
  expect((await commander(a.id)).totalXp).toBe(String(config.rewards.storyQuest+config.rewards.residentQuest));
 });
 it('accounts for the existing 21 main and four resident quest events without adding quest-linked adventure XP',async()=>{
  const a=await user(),s=story();
  for(const questId of config.story.mainQuestIds)await complete(s,a.id,questId);
  const main=await commander(a.id);expect(main.totalXp).toBe(String(config.story.mainQuestIds.length*config.rewards.storyQuest));
  expect(main.level).toBe(config.growth.filter(p=>p.totalXp<=Number(main.totalXp)).at(-1)!.level);
  for(const questId of config.story.residentQuestIds)await complete(s,a.id,questId);
  const total=config.story.mainQuestIds.length*config.rewards.storyQuest+config.story.residentQuestIds.length*config.rewards.residentQuest;
  const final=await commander(a.id);expect(final.totalXp).toBe(String(total));expect(final.level).toBe(config.growth.filter(p=>p.totalXp<=total).at(-1)!.level);
  expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(config.story.mainQuestIds.length+config.story.residentQuestIds.length);
 },20000);
 it('marks a quest-linked run on the server and records zero return XP before awarding the quest once',async()=>{
  const a=await user(),c=sceneContent(),q=c.quests.find(q=>q.id===config.story.mainQuestIds[0])!;
  const encounter=c.encounters.find(e=>e.id===content.quests.find(source=>source.id===q.id)!.objective.encounterIds[0])??c.encounters[0];
  Object.assign(encounter,{speciesId:'u07',level:1,kind:'black',mode:'adventure',winXp:11,hpMultiplier:1,powerMultiplier:1});
  q.objective={kind:'adventure_win',wins:1,encounterIds:[encounter.id]};let active=structuredClone(config);const s=story(c,()=>active);await accept(s,a.id,q.id);
  active={...structuredClone(config),version:config.version+'-later',rewards:{...config.rewards,storyQuest:config.rewards.storyQuest+17}};
  const view:any=await s.createBattle(a.id,{requestId:request(),questId:q.id,ownedIds:[a.units[0].id]});
  const runId=view.state.adventureId,marked=await db.roster60Adventure.findUniqueOrThrow({where:{id:runId}});expect(JSON.parse(marked.state).storyQuestId).toBe(q.id);expect(JSON.parse(marked.state).commanderPolicy.version).toBe(config.version);
  const settled:any=await win(a.id,view);await battle().settle(a.id,{requestId:request(),battleId:view.battleId,revision:settled.revision});
  const after=await db.roster60Adventure.findUniqueOrThrow({where:{id:runId}});expect(JSON.parse(after.state).commanderReturnEvidence.wins).toBe(1);
  const returned:any=await returnRun(a.id,runId);expect(returned.commanderReward).toMatchObject({status:'RECORDED_ZERO',reason:'QUEST_LINKED_ADVENTURE_ZERO',xpAwarded:0});
  expect(returned.commander.totalXp).toBe('0');expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}})).totalXp).toBe(11n);
  await open(s,a.id,q.id,'return');const completed=await finishScene(s,a.id);expect(completed.commanderReward.xpAwarded).toBe(config.rewards.storyQuest);
  await returnRun(a.id,runId);expect((await commander(a.id)).totalXp).toBe(String(config.rewards.storyQuest));
  expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'ADVENTURE_RETURN',eventId:runId,appliedXp:0}})).toBe(1);
  // The same unlocked encounter without a quest reservation is an independent material run.
  active=config;
  const farming:any=await s.createBattle(a.id,{requestId:request(),encounterId:encounter.id,ownedIds:[a.units[0].id]});
  const material=await db.roster60Adventure.findUniqueOrThrow({where:{id:farming.state.adventureId}});expect(JSON.parse(material.state).storyQuestId).toBeUndefined();
  await win(a.id,farming);expect((await returnRun(a.id,material.id)).commanderReward.xpAwarded).toBe(config.rewards.independentAdventureReturn);
  expect((await commander(a.id)).totalXp).toBe(String(config.rewards.storyQuest+config.rewards.independentAdventureReturn));
 });
 it('gives one independent account base award per cleared return while preserving separate unit participation XP',async()=>{
  const a=await user(2),run=await start(a.id,a.units.map(u=>u.id));await clearIndependent(a.id,run);
  const ready:any=await adventure().read(a.id,run.adventureId),command={requestId:request(),adventureId:run.adventureId,revision:ready.revision};
  const paid:any=await adventure().return(a.id,command);expect(paid.commanderReward).toMatchObject({status:'APPLIED',xpAwarded:config.rewards.independentAdventureReturn,reason:'INDEPENDENT_ADVENTURE_COMPLETED'});
  expect(await adventure().return(a.id,command)).toEqual(paid);await returnRun(a.id,run.adventureId);
  expect((await commander(a.id)).totalXp).toBe(String(config.rewards.independentAdventureReturn));
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}})).totalXp).toBe(11n);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[1].id}})).totalXp).toBe(5n);
  expect(await db.commanderXpLedger.count({where:{playerId:a.id,eventKind:'ADVENTURE_RETURN',eventId:run.adventureId}})).toBe(1);
  const single=await start(a.id,[a.units[0].id]);await clearIndependent(a.id,single);await returnRun(a.id,single.adventureId);
  expect((await commander(a.id)).totalXp).toBe(String(config.rewards.independentAdventureReturn*2));
 });
 it('serializes two independently cleared returns across Prisma clients and awards each account event once',async()=>{
  const a=await user(2),left=await start(a.id,[a.units[0].id]),right=await start(a.id,[a.units[1].id]);
  await clearIndependent(a.id,left);await clearIndependent(a.id,right);
  const readyLeft:any=await adventure().read(a.id,left.adventureId),readyRight:any=await adventure().read(a.id,right.adventureId);
  const first={requestId:request(),adventureId:left.adventureId,revision:readyLeft.revision},second={requestId:request(),adventureId:right.adventureId,revision:readyRight.revision};
  const otherDb=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}}),other=new RosterAdventure(otherDb,()=>catalog,fighter,undefined,()=>config);
  try{
   const results:any[]=await Promise.all([adventure().return(a.id,first),other.return(a.id,second)]);
   expect(results.map(r=>r.commanderReward.xpAwarded)).toEqual([config.rewards.independentAdventureReturn,config.rewards.independentAdventureReturn]);
   expect((await commander(a.id)).totalXp).toBe(String(config.rewards.independentAdventureReturn*2));
   const ledger=await db.commanderXpLedger.findMany({where:{playerId:a.id,eventKind:'ADVENTURE_RETURN'}});
   expect(ledger).toHaveLength(2);expect(ledger.map(e=>e.eventId).sort()).toEqual([left.adventureId,right.adventureId].sort());
   expect(ledger.reduce((sum,e)=>sum+e.appliedXp,0)).toBe(config.rewards.independentAdventureReturn*2);
   expect(await Promise.all([other.return(a.id,first),adventure().return(a.id,second)])).toEqual(results);
   await Promise.all([returnRun(a.id,left.adventureId,other),returnRun(a.id,right.adventureId)]);
   expect((await commander(a.id)).totalXp).toBe(String(config.rewards.independentAdventureReturn*2));
   expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(2);
   for(const unit of a.units)expect((await db.roster60Owned.findUniqueOrThrow({where:{id:unit.id}})).totalXp).toBe(11n);
  }finally{await otherDb.$disconnect();}
 },15000);
 it('does not infer a clear from unit escrow, client-shaped flags, party size or an immediate return',async()=>{
  const a=await user(),run=await start(a.id,[a.units[0].id]);
  // A server test-only escrow fixture demonstrates that delivery of unit XP is not proof of a clear.
  await db.roster60Adventure.update({where:{id:run.adventureId},data:{pendingRewards:JSON.stringify({xp:{[a.units[0].id]:1}})}});
  const returned:any=await adventure().return(a.id,{requestId:request(),adventureId:run.adventureId,revision:run.revision,wins:99,storyQuestId:null,commanderXp:99999} as any);
  expect(returned.commanderReward).toMatchObject({status:'RECORDED_ZERO',xpAwarded:0,reason:'ADVENTURE_NO_CLEAR_ZERO'});expect(returned.commander.totalXp).toBe('0');
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}})).totalXp).toBe(1n);
  const row=await db.commanderXpLedger.findUniqueOrThrow({where:{playerId_eventKind_eventId:{playerId:a.id,eventKind:'ADVENTURE_RETURN',eventId:run.adventureId}}});expect(row.reason).toBe('NO_CLEAR_RETURN');
 });
 it('does not reconstruct legacy clear evidence from historical unit totals',async()=>{
  const a=await user(),run=await start(a.id,[a.units[0].id]),row=await db.roster60Adventure.findUniqueOrThrow({where:{id:run.adventureId}}),saved=JSON.parse(row.state);delete saved.commanderReturnEvidence;
  await db.roster60Adventure.update({where:{id:row.id},data:{state:JSON.stringify(saved)}});
  expect((await returnRun(a.id,row.id)).commanderReward).toMatchObject({xpAwarded:0,reason:'ADVENTURE_NO_CLEAR_ZERO'});
  expect((await db.commanderXpLedger.findUniqueOrThrow({where:{playerId_eventKind_eventId:{playerId:a.id,eventKind:'ADVENTURE_RETURN',eventId:row.id}}})).reason).toBe('CLEAR_EVIDENCE_MISSING');
 });
 it('does not block unconfigured play, creates no commander rows and never backfills completed events on later configuration',async()=>{
  const a=await user(),s=story(sceneContent(),()=>null),done=await complete(s,a.id,config.story.mainQuestIds[0]);
  expect(done.commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0,level:null});expect(done.commander).toMatchObject({status:'POLICY_PENDING',level:null,totalXp:null});
  const runs=adventure(()=>null),run=await start(a.id,[a.units[0].id],runs);await clearIndependent(a.id,run);
  expect((await returnRun(a.id,run.adventureId,runs)).commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0});
  expect(await db.commanderAccount.count({where:{playerId:a.id}})).toBe(0);expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(0);
  const configured=story();await open(configured,a.id,config.story.mainQuestIds[0],'replay');await finishScene(configured,a.id);await returnRun(a.id,run.adventureId);
  expect((await commander(a.id)).totalXp).toBe('0');expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(0);
 });
 it('pins configured quest and adventure contracts before the active policy changes',async()=>{
  const a=await user();let current:CommanderPolicy=structuredClone(config);const s=story(sceneContent(),()=>current),questId=config.story.mainQuestIds[0];
  await accept(s,a.id,questId);current.version+='-later';current.rewards.storyQuest+=17;await open(s,a.id,questId,'return');
  expect((await finishScene(s,a.id)).commanderReward.xpAwarded).toBe(config.rewards.storyQuest);
  const b=await user();current=structuredClone(config);const runs=adventure(()=>current),run=await start(b.id,[b.units[0].id],runs);current.version+='-later';current.rewards.independentAdventureReturn+=23;await clearIndependent(b.id,run);
  expect((await returnRun(b.id,run.adventureId,runs)).commanderReward.xpAwarded).toBe(config.rewards.independentAdventureReturn);
  expect((await commander(a.id)).totalXp).toBe(String(config.rewards.storyQuest));expect((await commander(b.id)).totalXp).toBe(String(config.rewards.independentAdventureReturn));
 });
 it('pins null on acceptance/departure and treats missing legacy policy fields as null',async()=>{
  const a=await user();let current:CommanderPolicy|null=null;const s=story(sceneContent(),()=>current),questId=config.story.mainQuestIds[0];
  await accept(s,a.id,questId);current=config;await open(s,a.id,questId,'return');expect((await finishScene(s,a.id)).commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0});
  current=null;const runs=adventure(()=>current),run=await start(a.id,[a.units[0].id],runs);current=config;await clearIndependent(a.id,run);
  expect((await returnRun(a.id,run.adventureId,runs)).commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0});
  const legacy=await user(),legacyStory=story();await accept(legacyStory,legacy.id,questId);const saved=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:legacy.id}}),progress=JSON.parse(saved.state);delete progress.accepted[questId].commanderPolicy;
  await db.roster60StoryAccount.update({where:{playerId:legacy.id},data:{state:JSON.stringify(progress)}});await open(legacyStory,legacy.id,questId,'return');
  expect((await finishScene(legacyStory,legacy.id)).commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0});
  const oldRun=await start(legacy.id,[legacy.units[0].id]);await clearIndependent(legacy.id,oldRun);const stored=await db.roster60Adventure.findUniqueOrThrow({where:{id:oldRun.adventureId}}),old=JSON.parse(stored.state);delete old.commanderPolicy;
  await db.roster60Adventure.update({where:{id:stored.id},data:{state:JSON.stringify(old)}});expect((await returnRun(legacy.id,stored.id)).commanderReward).toMatchObject({status:'POLICY_PENDING',xpAwarded:0});
  expect(await db.commanderXpLedger.count({where:{playerId:{in:[a.id,legacy.id]}}})).toBe(0);expect(await db.commanderAccount.count({where:{playerId:{in:[a.id,legacy.id]}}})).toBe(0);
 });
 it('rolls commander award, completion and receipt back together when the domain write fails',async()=>{
  const a=await user(),s=story(),questId=config.story.mainQuestIds[0];await accept(s,a.id,questId);const ready:any=await open(s,a.id,questId,'return'),command={requestId:request(),revision:ready.revision,cursor:ready.scene.cursor};
  await db.$executeRawUnsafe(`CREATE TRIGGER commander_story_fail BEFORE UPDATE ON Roster60StoryAccount WHEN NEW.playerId='${a.id}' BEGIN SELECT RAISE(ABORT,'commander test rollback'); END`);
  try{await expect(s.advance(a.id,command)).rejects.toThrow();expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(0);expect(await db.commanderAccount.count({where:{playerId:a.id}})).toBe(0);expect((await s.state(a.id)).revision).toBe(ready.revision);expect(await db.roster60StoryReceipt.findUnique({where:{playerId_requestId:{playerId:a.id,requestId:command.requestId}}})).toBeNull();}
  finally{await db.$executeRawUnsafe('DROP TRIGGER commander_story_fail');}
  const paid:any=await s.advance(a.id,command);expect(paid.commanderReward.xpAwarded).toBe(config.rewards.storyQuest);
 });
 it('rolls account/unit awards back with a failed return and rejects a foreign account',async()=>{
  const a=await user(),other=await user(),run=await start(a.id,[a.units[0].id]);await clearIndependent(a.id,run);const ready:any=await adventure().read(a.id,run.adventureId),command={requestId:request(),adventureId:run.adventureId,revision:ready.revision};
  await expect(adventure().return(other.id,command)).rejects.toMatchObject({code:'ADVENTURE_NOT_FOUND'});
  await db.$executeRawUnsafe(`CREATE TRIGGER commander_return_fail BEFORE UPDATE ON Roster60Adventure WHEN NEW.id='${run.adventureId}' AND NEW.status='RETURNED' BEGIN SELECT RAISE(ABORT,'commander test rollback'); END`);
  try{await expect(adventure().return(a.id,command)).rejects.toThrow();expect(await db.commanderXpLedger.count({where:{playerId:a.id}})).toBe(0);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.units[0].id}})).totalXp).toBe(0n);expect((await adventure().read(a.id,run.adventureId)).status).toBe('ACTIVE');}
  finally{await db.$executeRawUnsafe('DROP TRIGGER commander_return_fail');}
  const paid:any=await adventure().return(a.id,command);expect(paid.commanderReward.xpAwarded).toBe(config.rewards.independentAdventureReturn);
 });
 it('keeps the commander account and story progress through a real owned-unit reincarnation',async()=>{
  const a=await user(),s=story();await complete(s,a.id,config.story.mainQuestIds[0]);const before=await commander(a.id),storyBefore=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}});
  const definition=catalog.characters.find(u=>['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'].includes(u.rarity))!;
  const unit=await db.roster60Owned.create({data:{playerId:a.id,speciesId:definition.id,source:request(),totalXp:BigInt(definition.growth[59].totalXp)}}),rebirth=new RosterReincarnation(db,()=>catalog);
  const options=await rebirth.preview(a.id),candidate=options.candidates.find((c:any)=>c.ownedId===unit.id) as any;expect(candidate.eligible).toBe(true);
  await rebirth.confirm(a.id,{ownedId:unit.id,requestId:request(),catalogVersion:catalog.version,previewFingerprint:candidate.previewFingerprint});
  expect(await commander(a.id)).toEqual(before);expect((await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.id}})).state).toBe(storyBefore.state);
  expect(await db.roster60Owned.findUniqueOrThrow({where:{id:unit.id}})).toMatchObject({reincarnation:1,totalXp:0n});
 });
});
