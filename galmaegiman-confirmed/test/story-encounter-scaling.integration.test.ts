import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {RosterStory} from '../src/story/service.js';
import {RosterBattle,fighter} from '../src/roster60-battle.js';
import {storyVillainFighter} from '../src/story/villains.js';
import {storyContent} from '../src/story/content.js';
import type {StoryContent,StoryEncounter} from '../src/story/types.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const profile=(name:string)=>JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/'+name,'utf8'));
const catalog=profile('catalog.json'),policy=profile('battle-policy.json');
const request=()=>randomUUID();
const plain=(encounter:StoryEncounter)=>encounter.villainProfileId
 ?storyVillainFighter(catalog,encounter.villainProfileId,encounter.level,'enemy')
 :fighter(catalog,encounter.speciesId,encounter.level,'enemy');

suite('real story encounter scaling and durable battle snapshots',()=>{
 let db:PrismaClient;const owners:string[]=[];
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();});

 async function checkpoint(content:StoryContent,encounterId:string){
  const encounter=content.encounters.find(e=>e.id===encounterId)!;
  const quest=content.quests.find(q=>q.objective.encounterIds.includes(encounterId))!;
  const player=await db.player.create({data:{identity:'encounter-scale:'+request()}});owners.push(player.id);
  const def=catalog.characters.find((u:any)=>u.id==='u01');
  const owned=await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u01',totalXp:BigInt(def.growth[59].totalXp),source:'encounter-scale:'+request()}});
  const service=new RosterStory(db,()=>catalog,()=>policy,content);
  await service.state(player.id);
  const row=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:player.id}}),progress=JSON.parse(row.state);
  // Skip earlier playthrough in this disposable fixture; keep real enemy data,
  // levels, creation transactions and snapshot/read/receipt paths intact.
  const completed=new Set<string>();const visit=(id:string)=>{if(completed.has(id))return;const q=content.quests.find(q=>q.id===id)!;q.unlockAfter.forEach(visit);completed.add(id);};quest.unlockAfter.forEach(visit);
  progress.completed=[...completed];progress.accepted[quest.id]={token:request(),wins:quest.objective.encounterIds.indexOf(encounterId),battles:[]};
  await db.roster60StoryAccount.update({where:{playerId:player.id},data:{state:JSON.stringify(progress)}});
  const command={requestId:request(),questId:quest.id,ownedIds:[owned.id]};
  return {owner:player.id,ownedId:owned.id,service,encounter,command};
 }

 it('keeps the current black and distinct villain multipliers separate',()=>{
  const black=storyContent.encounters.filter(e=>e.kind==='black'),villains=storyContent.encounters.filter(e=>e.kind==='villain');
  expect(black).toHaveLength(22);expect(villains).toHaveLength(5);
  expect(black.filter(e=>e.hpMultiplier===1.1&&e.powerMultiplier===1.1)).toHaveLength(21);
  expect(black.filter(e=>e.hpMultiplier===1.3&&e.powerMultiplier===1.3).map(e=>e.id)).toEqual(['RESIDENT_GUARD_01_02']);
  expect(black.some(e=>e.hpMultiplier===1.5||e.powerMultiplier===1.5)).toBe(false);
  expect(villains.map(e=>e.id)).toEqual(['STORY_08_01','STORY_09_01','STORY_11_01','STORY_18_01','STORY_20_01']);
  expect(villains.every(e=>e.hpMultiplier===1.2&&e.powerMultiplier===1.2)).toBe(true);
 });

 for(const encounter of storyContent.encounters)it('applies only HP and attack/skill power once for '+encounter.id,async()=>{
  const f=await checkpoint(structuredClone(storyContent),encounter.id),created:any=await f.service.createBattle(f.owner,f.command);
  expect(await f.service.createBattle(f.owner,f.command)).toEqual(created);
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}}),enemy=JSON.parse(row.state).fighters[1],base=plain(encounter);
  expect(enemy.hp).toBe(Math.round(base.hp*(encounter.hpMultiplier??1)));
  expect(enemy.stats).toEqual({...base.stats,hp:enemy.hp});
  expect(enemy.mp).toBe(base.mp);expect(enemy.defense).toEqual(base.defense);expect(enemy.suitability).toEqual(base.suitability);
  const scale=(skill:any)=>({...skill,power:Math.round(skill.power*(encounter.powerMultiplier??1))});
  expect(enemy.attack).toEqual(scale(base.attack));expect(enemy.skills).toEqual(base.skills.map(scale));
  expect(enemy.artVariant).toBe(encounter.kind==='black'?'black':undefined);
  expect((await db.roster60Battle.count({where:{playerId:f.owner}}))).toBe(1);
  const reader=new RosterBattle(db,()=>catalog,()=>policy);
  expect(await reader.read(f.owner,created.battleId)).toEqual(created);
  expect((await reader.active(f.owner)).battles).toEqual([created]);
  expect(await db.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}})).toMatchObject({state:row.state,snapshot:row.snapshot,revision:row.revision});
 });

 it('lowers the separate level-23 Dolbi to HP488 while keeping MP59 and all other base stats',()=>{
  const encounter=storyContent.encounters.find(e=>e.id==='STORY_09_01')!,base=plain(encounter);
  expect(encounter).toMatchObject({kind:'villain',mode:'story',villainProfileId:'guide',level:23,hpMultiplier:1.2,powerMultiplier:1.2});
  expect(base.stats).toEqual({hp:407,mp:59,durability:34,statusResistance:18,speed:28});
  expect(Math.round(base.hp*1.2)).toBe(488);expect(base.mp).toBe(59);
 });

 for(const [id,oldHp,newHp] of [
  ['STORY_08_01',540,432],['STORY_09_01',611,488],['STORY_11_01',728,582],
  ['STORY_18_01',1130,904],['STORY_20_01',1140,912]
 ] as const)it('preserves the original 1.5 snapshot through reconnect/action/retries and creates new 1.2 '+id,async()=>{
  const oldContent=structuredClone(storyContent);
  for(const e of oldContent.encounters.filter(e=>e.kind==='villain')){e.hpMultiplier=1.5;e.powerMultiplier=1.5;}
  const old=await checkpoint(oldContent,id),created:any=await old.service.createBattle(old.owner,old.command);
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}}),oldEnemy=JSON.parse(row.state).fighters[1];
  const current=storyContent.encounters.find(e=>e.id===id)!,base=plain(current);
  expect(oldEnemy.stats).toEqual({...base.stats,hp:oldHp});expect(oldEnemy.hp).toBe(oldHp);expect(oldEnemy.mp).toBe(base.mp);
  expect(oldEnemy.attack.power).toBe(24);
  expect(oldEnemy.skills.map((s:any)=>s.power)).toEqual(base.skills.map(s=>Math.round(s.power*1.5)));
  const reconnected=new PrismaClient({datasources:{db:{url:process.env.TEST_DB_URL!}}});
  try{
   const restartedStory=new RosterStory(reconnected,()=>catalog,()=>policy,structuredClone(storyContent)),reader=new RosterBattle(reconnected,()=>catalog,()=>policy);
   expect(await restartedStory.createBattle(old.owner,old.command)).toEqual(created);
   expect(await reader.read(old.owner,created.battleId)).toEqual(created);
   expect((await reader.active(old.owner)).battles).toEqual([created]);
   expect(await reconnected.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}})).toMatchObject({state:row.state,snapshot:row.snapshot,revision:row.revision});
   const action={requestId:request(),battleId:created.battleId,revision:created.revision,action:{kind:'focus' as const}},acted:any=await reader.action(old.owner,action);
   expect(await reader.action(old.owner,action)).toEqual(acted);
   const actedRow=await reconnected.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}}),after=JSON.parse(actedRow.state).fighters[1];
   expect(after.stats).toEqual(oldEnemy.stats);expect(after.attack).toEqual(oldEnemy.attack);expect(after.skills).toEqual(oldEnemy.skills);expect(actedRow.snapshot).toBe(row.snapshot);
   expect(await restartedStory.createBattle(old.owner,old.command)).toEqual(created);
   expect(await reconnected.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}})).toMatchObject({state:actedRow.state,snapshot:row.snapshot,revision:actedRow.revision});
   expect(await reader.read(old.owner,created.battleId)).toEqual(acted);
   expect((await reader.active(old.owner)).battles).toEqual(acted.status==='ACTIVE'||acted.status==='FINISHED'?[acted]:[]);
   expect(await reconnected.roster60Battle.count({where:{playerId:old.owner}})).toBe(1);
  }finally{await reconnected.$disconnect();}
  const fresh=await checkpoint(structuredClone(storyContent),id),newBattle:any=await fresh.service.createBattle(fresh.owner,fresh.command);
  const newRow=await db.roster60Battle.findUniqueOrThrow({where:{id:newBattle.battleId}}),newEnemy=JSON.parse(newRow.state).fighters[1];
  expect(newEnemy.stats).toEqual({...base.stats,hp:newHp});expect(newEnemy.hp).toBe(newHp);expect(newEnemy.mp).toBe(base.mp);
  expect(newEnemy.attack.power).toBe(19);expect(newEnemy.skills.map((s:any)=>s.power)).toEqual(base.skills.map(s=>Math.round(s.power*1.2)));
  expect(newEnemy.skills.map((s:any)=>({id:s.id,element:s.element,mpCost:s.mpCost,learnLevel:s.learnLevel}))).toEqual(oldEnemy.skills.map((s:any)=>({id:s.id,element:s.element,mpCost:s.mpCost,learnLevel:s.learnLevel})));
  expect(newEnemy.defense).toEqual(oldEnemy.defense);expect(newEnemy.suitability).toEqual(oldEnemy.suitability);
  const oldSnapshot=JSON.parse(row.snapshot),newSnapshot=JSON.parse(newRow.snapshot);
  expect(newSnapshot.encounter).toEqual(oldSnapshot.encounter);
  expect(newSnapshot.policy).toEqual(oldSnapshot.policy);
  expect(newSnapshot.story.dropAmount).toBe(oldSnapshot.story.dropAmount);expect(newSnapshot.story.maxBalance).toBe(oldSnapshot.story.maxBalance);
  expect(newSnapshot.story.kind).toBe('villain');expect(newSnapshot.story.mode).toBe('story');
 });
});
