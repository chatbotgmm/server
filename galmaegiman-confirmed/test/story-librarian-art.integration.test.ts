import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {createHash,randomUUID} from 'node:crypto';
import {mkdirSync,readFileSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {RosterStory} from '../src/story/service.js';
import {RosterBattle} from '../src/roster60-battle.js';
import {storyContent} from '../src/story/content.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const profile=(name:string)=>JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/'+name,'utf8'));
const catalog=profile('catalog.json'),policy=profile('battle-policy.json');
const quest=storyContent.quests.find(row=>row.id==='RESIDENT_LIBRARIAN_01')!;
const digest=(file:string)=>createHash('sha256').update(readFileSync(file)).digest('hex');
const request=()=>randomUUID();

suite('actual librarian quest battle art DTO at the original level and profile',()=>{
 let db:PrismaClient;const owners:string[]=[];
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();});

 for(const [checkpointWins,speciesId] of ['u14','u23'].entries())it('creates and restores '+speciesId+' black art for the real librarian checkpoint '+checkpointWins,async()=>{
  expect(quest.title).toBe('책 사이의 이름');expect(quest.npcId).toBe('librarian');
  const encounter=storyContent.encounters.find(row=>row.id===quest.objective.encounterIds[checkpointWins])!;
  expect(encounter).toMatchObject({speciesId,level:44,kind:'black',mode:'adventure'});
  const player=await db.player.create({data:{identity:'librarian-art:'+request()}});owners.push(player.id);
  const definition=catalog.characters.find((row:any)=>row.id==='u01');
  const owned=await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u01',totalXp:BigInt(definition.growth[59].totalXp),source:'librarian-art:'+request()}});
  const story=new RosterStory(db,()=>catalog,()=>policy),battle=new RosterBattle(db,()=>catalog,()=>policy);
  await story.state(player.id);
  const account=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:player.id}}),progress=JSON.parse(account.state);
  // A saved checkpoint fixture skips prior playthrough only. Content, enemy level/stats, gates and creation code are unchanged.
  const predecessors=new Set<string>();const visit=(id:string)=>{if(predecessors.has(id))return;const row=storyContent.quests.find(row=>row.id===id)!;row.unlockAfter.forEach(visit);predecessors.add(id);};quest.unlockAfter.forEach(visit);
  progress.completed=[...predecessors];
  await db.roster60StoryAccount.update({where:{playerId:player.id},data:{state:JSON.stringify(progress)}});
  let state:any=await story.state(player.id);expect(state.quests.find((row:any)=>row.id===quest.id).status).toBe('AVAILABLE');
  state=await story.open(player.id,{requestId:request(),revision:state.revision,questId:quest.id,phase:'intro'});
  while(!state.scene.last)state=await story.advance(player.id,{requestId:request(),revision:state.revision,cursor:state.scene.cursor});
  await story.accept(player.id,{requestId:request(),revision:state.revision,questId:quest.id});
  if(checkpointWins){const saved=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:player.id}}),checkpoint=JSON.parse(saved.state);checkpoint.accepted[quest.id].wins=checkpointWins;await db.roster60StoryAccount.update({where:{playerId:player.id},data:{state:JSON.stringify(checkpoint)}});}
  const command={requestId:request(),questId:quest.id,ownedIds:[owned.id]},created:any=await story.createBattle(player.id,command);
  expect(await story.createBattle(player.id,command)).toEqual(created);
  const enemy=created.state.fighters[1];expect(enemy).toMatchObject({speciesId,level:44,artVariant:'black'});expect(created.state.mode).toBe('adventure');
  expect(enemy).not.toHaveProperty('defense');expect(enemy).not.toHaveProperty('ownedId');
  const persisted=await db.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}}),snapshot=JSON.parse(persisted.snapshot);
  expect(snapshot.story).toMatchObject({questId:quest.id,encounterId:encounter.id,speciesId,kind:'black'});
  expect(await battle.read(player.id,created.battleId)).toEqual(created);
  const originalState=JSON.parse(persisted.state);delete originalState.fighters[1].artVariant;
  await db.roster60Battle.update({where:{id:created.battleId},data:{state:JSON.stringify(originalState)}});
  expect((await battle.read(player.id,created.battleId)).state.fighters[1]).toEqual(enemy);
  const destination=process.env.GMM_LIBRARIAN_ART_FIXTURE_DIR;
  if(destination){mkdirSync(destination,{recursive:true});writeFileSync(join(destination,encounter.id+'.json'),JSON.stringify({recording:{questId:quest.id,title:quest.title,encounterId:encounter.id,checkpointWins,contentVersion:storyContent.version,catalogVersion:catalog.version,contentSha256:digest('data/story/story-mode-v1.json'),catalogSha256:digest('data/test-profiles/five-stat-device-test-v1/catalog.json'),service:'RosterStory.createBattle',priorPlaythrough:'server-owned-checkpoint-fixture'},battle:created},null,2)+'\n');}
 });
});
