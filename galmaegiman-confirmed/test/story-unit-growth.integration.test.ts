import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {createHash,randomUUID} from 'node:crypto';
import {mkdirSync,writeFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {RosterStory} from '../src/story/service.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterBattle,fighter} from '../src/roster60-battle.js';
import {storyVillainFighter} from '../src/story/villains.js';
import {growthAt} from '../src/battle/roster60-rules.js';
import {decideTurnOrder} from '../src/battle/roster60-five-combat.js';
import {commanderLevelAt,readCommanderState,validateCommanderPolicy} from '../src/progression/commander.js';
import {chooseStoryGrowthAction,loadStoryGrowthInputs,proposeStoryUnitXpPolicy,storyGrowthOrder} from '../scripts/simulate-story-growth.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('original story campaign growth through real service actions and normal returns',()=>{
 let db:PrismaClient,inputs:ReturnType<typeof loadStoryGrowthInputs>,output:string;
 const owners:string[]=[],request=()=>randomUUID();
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated disposable campaign DB only');
  db=new PrismaClient({datasources:{db:{url}}});inputs=loadStoryGrowthInputs();output=process.env.STORY_GROWTH_EVIDENCE??join(dirname(url.slice('file:'.length)),'story-unit-growth');mkdirSync(output,{recursive:true});
  expect(inputs.unitPolicy).toEqual(proposeStoryUnitXpPolicy(inputs.catalog,inputs.content).policy);
  expect(inputs.content.encounters).toHaveLength(27);expect(inputs.content.encounters.every(e=>e.winXp===0&&e.lossXp===0)).toBe(true);
 });
 afterAll(async()=>{if(db){await db.playerItem.deleteMany({where:{playerId:{in:owners}}});await db.dailyProgress.deleteMany({where:{playerId:{in:owners}}});await db.auditEvent.deleteMany({where:{playerId:{in:owners}}});await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();}});

 it.each([['guaranteed-choice-two',['u01','u02']],['explicit-three-member-sample',['u01','u02','u03']]] as const)('%s completes the unchanged campaign without repeat quests, extra battles or item grants',async(label,ids)=>{
  const {catalog,content,battlePolicy,unitPolicy}=inputs,commanderPolicy=validateCommanderPolicy(inputs.commanderPolicy),contentBefore=JSON.stringify(content),catalogBefore=JSON.stringify(catalog);
  const player=await db.player.create({data:{identity:'story-growth:'+request()}});owners.push(player.id);
  const owned:{id:string;speciesId:string}[]=[];for(const [i,speciesId] of ids.entries())owned.push(await db.roster60Owned.create({data:{id:player.id+':sample-'+i,playerId:player.id,speciesId,totalXp:0n,source:i===0?'campaign-fixture-permanent-starter':i===1?'campaign-fixture-guaranteed-common-choice':'campaign-fixture-explicit-third-owner'}}));
  const story=new RosterStory(db,()=>catalog,()=>battlePolicy,content,()=>commanderPolicy,()=>unitPolicy),battle=new RosterBattle(db,()=>catalog,()=>battlePolicy),adventure=new RosterAdventure(db,()=>catalog,fighter,undefined,()=>commanderPolicy);
  const report:any={label,speciesIds:ids,initialUnitXp:0,reincarnation:0,ownershipCondition:ids.length===2?'starter-and-guaranteed-common-choice':'explicit-third-owned-fixture',productionBalanceApproved:false,testValues:true,
   sourceHashes:{catalog:createHash('sha256').update(catalogBefore).digest('hex'),content:createHash('sha256').update(contentBefore).digest('hex'),unitPolicy:createHash('sha256').update(JSON.stringify(unitPolicy)).digest('hex')},
   policyVersion:unitPolicy.version,itemsGranted:0,extraNormalBattles:0,repeatedQuestCompletions:0,quests:[],battles:[],returns:[],status:'RUNNING'};
  const save=()=>writeFileSync(join(output,label+'.json'),JSON.stringify(report,null,2)+'\n');
  const levels=async()=>Promise.all(owned.map(async u=>{const row=await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}}),definition=catalog.characters.find(d=>d.id===u.speciesId)!;return {ownedId:u.id,speciesId:u.speciesId,totalXp:row.totalXp.toString(),level:growthAt(Number(row.totalXp),definition.growth).level};}));
  async function dialog(phase:'intro'|'return',questId:string){let s:any=await story.state(player.id);s=await story.open(player.id,{requestId:request(),revision:s.revision,questId,phase});while(s.scene&&!s.scene.last)s=await story.advance(player.id,{requestId:request(),revision:s.revision,cursor:s.scene.cursor});return s;}
  try{
   for(const quest of storyGrowthOrder(content)){
    let s:any=await story.state(player.id);expect(s.quests.find((q:any)=>q.id===quest.id)?.status,quest.id+' must unlock through actual prerequisites/Gang').toBe('AVAILABLE');
    s=await dialog('intro',quest.id);s=await story.accept(player.id,{requestId:request(),revision:s.revision,questId:quest.id});
    const questRecord:any={questId:quest.id,kind:quest.kind,unitLevelsBefore:await levels()};report.quests.push(questRecord);
    for(const encounterId of quest.objective.encounterIds){
     const encounter=content.encounters.find(e=>e.id===encounterId)!;s=await story.state(player.id);const active=s.quests.find((q:any)=>q.id===quest.id),run:any=active.adventureId?await adventure.read(player.id,active.adventureId):null;
     // Carry HP in a multi-fight adventure; only select a living leader from the public own-party DTO.
     const living=run?.party?.find((f:any)=>f.hp>0)?.ownedId,partyOrder=living?[living,...owned.map(u=>u.id).filter(id=>id!==living)]:owned.map(u=>u.id);
     const before=await levels();let view:any=await story.createBattle(player.id,{requestId:request(),questId:quest.id,ownedIds:partyOrder});
     const raw=await db.roster60Battle.findUniqueOrThrow({where:{id:view.battleId}}),rawState=JSON.parse(raw.state),seed='story-growth-db-v1:'+label+':'+encounterId;
     const expectedEnemy=encounter.villainProfileId?storyVillainFighter(catalog,encounter.villainProfileId,encounter.level,'enemy'):fighter(catalog,encounter.speciesId,encounter.level,'enemy');
     expectedEnemy.stats.hp=Math.round(expectedEnemy.stats.hp*(encounter.hpMultiplier??1));expectedEnemy.hp=expectedEnemy.stats.hp;for(const skill of [expectedEnemy.attack,...expectedEnemy.skills])skill.power=Math.round(skill.power*(encounter.powerMultiplier??1));
     expect(rawState.fighters[1].level).toBe(encounter.level);expect(rawState.fighters[1].stats).toEqual(expectedEnemy.stats);expect(rawState.fighters[1].hp).toBe(expectedEnemy.hp);expect(rawState.fighters[1].attack).toEqual(expectedEnemy.attack);expect(rawState.fighters[1].skills).toEqual(expectedEnemy.skills);
     // Only the server test seed/tie decision changes. HP, power, levels, defenses and objectives remain original.
     rawState.decision=decideTurnOrder(raw.id,rawState.turn,rawState.fighters.map((f:any)=>({id:f.id,baseSpeed:f.stats.speed,modifiers:[]})),seed);
     await db.roster60Battle.update({where:{id:raw.id},data:{seed,state:JSON.stringify(rawState)}});view=await battle.read(player.id,raw.id);
     const trace:any={questId:quest.id,encounterId,mode:encounter.mode,battleId:view.battleId,seed,before,enemyLevel:encounter.level,enemyHp:expectedEnemy.hp,actions:[]};report.battles.push(trace);
     for(let n=0;view.status==='ACTIVE'&&n<battlePolicy.maxRounds*4;n++){
      const action=chooseStoryGrowthAction(view,battlePolicy.calculation);trace.actions.push({revision:view.revision,turn:view.state.turn,actor:view.state.fighters[0].ownedId,action});
      view=await battle.action(player.id,{requestId:request(),battleId:view.battleId,revision:view.revision,action});
     }
     trace.outcome=view.state.outcome;trace.turns=view.state.turn;trace.events=view.state.events;trace.party=[view.state.fighters[0],...view.state.reserves];
     if(view.state.outcome!=='win'){report.status='BLOCKED';report.blocker={questId:quest.id,encounterId,outcome:view.state.outcome,unitLevels:before};save();}
     expect(view.state.outcome,JSON.stringify(report.blocker??{encounterId})).toBe('win');
     const command={requestId:request(),battleId:view.battleId,revision:view.revision},settled:any=await battle.settle(player.id,command);
     expect(await battle.settle(player.id,command)).toEqual(settled);await battle.settle(player.id,{...command,requestId:request()});
     if(encounter.mode==='adventure')expect(await levels()).toEqual(before); // Escrow does not level units between fights.
     trace.afterBattleSettlement=await levels();save();
    }
    s=await story.state(player.id);const q=s.quests.find((q:any)=>q.id===quest.id);
    if(q.adventureId){const run:any=await adventure.read(player.id,q.adventureId),command={requestId:request(),adventureId:run.adventureId,revision:run.revision},returned:any=await adventure.return(player.id,command);expect(returned.commanderReward.xpAwarded).toBe(0);expect(await adventure.return(player.id,command)).toEqual(returned);report.returns.push({questId:quest.id,adventureId:run.adventureId,commanderXp:0,unitLevels:await levels()});}
    s=await dialog('return',quest.id);s=await story.advance(player.id,{requestId:request(),revision:s.revision,cursor:s.scene.cursor});expect(s.quests.find((q:any)=>q.id===quest.id)?.status).toBe('COMPLETED');questRecord.unitLevelsAfter=await levels();save();
   }
   const state=await story.state(player.id),commander=await readCommanderState(db,player.id,commanderPolicy),main=await db.commanderXpLedger.aggregate({where:{playerId:player.id,eventKind:'STORY_QUEST'},_sum:{appliedXp:true}}),side=await db.commanderXpLedger.aggregate({where:{playerId:player.id,eventKind:'RESIDENT_QUEST'},_sum:{appliedXp:true}});
   expect(report.battles).toHaveLength(27);expect(report.returns).toHaveLength(17);expect(state.history).toHaveLength(25);expect(state.statue.hatched).toBe(true);
   expect(main._sum.appliedXp).toBe(4200);expect(side._sum.appliedXp).toBe(1800);expect(commanderLevelAt(BigInt(main._sum.appliedXp!),commanderPolicy).level).toBe(43);expect(commander.totalXp).toBe('6000');expect(commander.level).toBe(61);
   report.finalUnits=await levels();expect(report.finalUnits.every((u:any)=>u.level===60)).toBe(true);expect(JSON.stringify(content)).toBe(contentBefore);expect(JSON.stringify(catalog)).toBe(catalogBefore);
   report.status='COMPLETED';report.commander=commander;save();
  }catch(error){if(report.status!=='BLOCKED'){report.status='FAILED';report.error=String(error);}save();throw error;}
 },150000);
});
