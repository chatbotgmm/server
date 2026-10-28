import {createHash,randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient,Roster60StoryAccount} from '@prisma/client';
import {RosterError,type RosterCatalog} from '../roster60-onboarding.js';
import {RosterAdventure} from '../roster60-adventure.js';
import {RosterBattle,fighter,validatePolicy,type BattlePolicy,type StoryBattleCreateHook} from '../roster60-battle.js';
import {storyContent,storySpeakerNames} from './content.js';
import {storyVillainFighter} from './villains.js';
import {initialStoryProgress,type StoryContent,type StoryProgress,type StoryQuest,type StoryEncounter,type SceneProgress} from './types.js';
import type {StoryBattleContext} from './settlement.js';
import {awardCommanderXp,readCommanderState,type CommanderPolicy,type CommanderXpResult} from '../progression/commander.js';
import {serializeRoster60Write} from '../roster60-write-queue.js';
import {validateStoryUnitXpPolicy,type StoryUnitXpPolicy} from '../progression/story-unit-xp.js';

type Tx=Prisma.TransactionClient;
type QuestView={id:string;title:string;kind:'main'|'resident';chapter:number;status:'AVAILABLE'|'ACTIVE'|'READY'|'COMPLETED';recommendedLevel:[number,number];npcId?:string;objective:{kind:'adventure_win'|'scene';wins:number;requiredWins:number};canDepart:boolean;adventureId:string|null;battleId:string|null};
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const fingerprint=(input:unknown)=>createHash('sha256').update(JSON.stringify(input)).digest('hex');
const operationId=(id:string,kind:string)=>'story-'+fingerprint([id,kind]);
const progressOf=(row:Roster60StoryAccount)=>JSON.parse(row.state) as StoryProgress;
const checkpoint=(scene:SceneProgress)=>scene.questId+':'+scene.phase;

export function validateStoryContent(c:StoryContent):StoryContent{
 if(!c||!c.version||c.productionBalanceApproved!==false||c.policy?.thresholdsAreTestValues!==true||!Number.isSafeInteger(c.policy.darkGangPerWin)||c.policy.darkGangPerWin<1||c.policy.darkGangPerWin>1000||!Number.isSafeInteger(c.policy.maxBalance)||c.policy.maxBalance<1||c.policy.maxBalance>2_000_000_000)return fail('STORY_CONTENT_INVALID',503);
 for(const list of [c.quests,c.scenes,c.encounters,c.residents])if(!Array.isArray(list)||new Set(list.map(x=>x.id)).size!==list.length)return fail('STORY_CONTENT_INVALID',503);
 for(const scene of c.scenes)if(!scene.id||!scene.background||!scene.lines?.length||scene.lines.some(line=>typeof line.text!=='string'||!line.text||line.text.length>1000||typeof line.speakerId!=='string'))return fail('STORY_CONTENT_INVALID',503);
 for(const encounter of c.encounters){
  if(!encounter.id||!Number.isInteger(encounter.level)||encounter.level<1||encounter.level>60||!['black','villain','ordinary'].includes(encounter.kind)||!['adventure','story'].includes(encounter.mode)||encounter.kind==='black'&&(!/^u(0[1-9]|[1-5][0-9]|60)$/.test(encounter.speciesId)||!!encounter.villainProfileId)||(encounter.winXp??0)<0||(encounter.lossXp??0)!==0)return fail('STORY_CONTENT_INVALID',503);
  for(const n of [encounter.hpMultiplier??1,encounter.powerMultiplier??1])if(!Number.isFinite(n)||n<1||n>1.5)return fail('STORY_CONTENT_INVALID',503);
 }
 for(const quest of c.quests){
  if(!quest.id||!['main','resident'].includes(quest.kind)||quest.unlockAfter.some(id=>!c.quests.some(q=>q.id===id))||!c.scenes.some(s=>s.id===quest.acceptScene)||!c.scenes.some(s=>s.id===quest.returnScene)||!Number.isSafeInteger(quest.objective.wins)||quest.objective.wins<0||quest.objective.encounterIds.some(id=>!c.encounters.some(e=>e.id===id))||quest.objective.kind==='adventure_win'&&(!quest.objective.encounterIds.length||quest.objective.wins!==quest.objective.encounterIds.length)||quest.objective.kind==='scene'&&(quest.objective.wins!==0||quest.objective.encounterIds.length))return fail('STORY_CONTENT_INVALID',503);
  for(const stage of [quest.statueStageAfter,quest.statueStageOnAccept])if(stage!==undefined&&(!Number.isInteger(stage)||stage<1||stage>5))return fail('STORY_CONTENT_INVALID',503);
  for(const bundle of [quest.fragmentBundle,quest.fragmentBundleOnAccept])if(bundle&&(!bundle.id||!Number.isSafeInteger(bundle.count)||bundle.count<1||bundle.count>1000))return fail('STORY_CONTENT_INVALID',503);
  if(quest.darkGangLifetimeRequired!==undefined&&(!Number.isSafeInteger(quest.darkGangLifetimeRequired)||quest.darkGangLifetimeRequired<0||quest.darkGangLifetimeRequired>c.policy.maxBalance))return fail('STORY_CONTENT_INVALID',503);
 }
 for(const [questId,amount] of Object.entries(c.policy.mainLifetimeGates))if(!c.quests.some(q=>q.id===questId)||!Number.isSafeInteger(amount)||amount<0||amount>c.policy.maxBalance)return fail('STORY_CONTENT_INVALID',503);
 return c;
}

/** Account story service; owned-unit levels, rank and enhancement rules are deliberately independent. */
export class RosterStory{
 readonly content:StoryContent;
 private adventure:RosterAdventure;
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>BattlePolicy|null,content:StoryContent=storyContent,private commanderPolicy:()=>CommanderPolicy|null=()=>null,private unitXpPolicy:()=>StoryUnitXpPolicy|null=()=>null){this.content=validateStoryContent(content);this.adventure=new RosterAdventure(db,catalog,fighter,undefined,commanderPolicy);}
 private async transaction<T>(run:(tx:Tx)=>Promise<T>):Promise<T>{return serializeRoster60Write(this.db,async()=>{const transaction=()=>this.db.$transaction(async tx=>{
  // Claim the shared SQLite writer before domain reads; PvP and adventure use the same lock.
  await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
  return run(tx);
 });try{return await transaction();}catch(e){if(['P2002','P2034','COMMANDER_CONCURRENT_WRITE'].includes((e as {code?:string}).code??''))return transaction();throw e;}});}
 private async account(tx:Tx,id:string){return tx.roster60StoryAccount.upsert({where:{playerId:id},create:{playerId:id,contentVersion:this.content.version,state:JSON.stringify(initialStoryProgress())},update:{}});}
 private quest(id:string){return this.content.quests.find(q=>q.id===id)??fail('STORY_QUEST_NOT_FOUND',404);}
 private unlocked(q:StoryQuest,p:StoryProgress,lifetime:number){return q.unlockAfter.every(id=>p.completed.includes(id))&&lifetime>=(q.darkGangLifetimeRequired??this.content.policy.mainLifetimeGates[q.id]??0);}
 private async status(tx:Tx,row:Roster60StoryAccount,q:StoryQuest){
  const p=progressOf(row);if(p.completed.includes(q.id))return 'COMPLETED' as const;
  const active=p.accepted[q.id];if(!active)return this.unlocked(q,p,row.darkGangLifetime)?'AVAILABLE' as const:'LOCKED' as const;
  if(q.objective.kind==='scene')return 'READY' as const;
  if(active.wins<q.objective.wins)return 'ACTIVE' as const;
  if(active.adventureId){const run=await tx.roster60Adventure.findFirst({where:{id:active.adventureId,playerId:row.playerId}});if(run?.status!=='RETURNED')return 'ACTIVE' as const;}
  return 'READY' as const;
 }
 private sceneData(scene:SceneProgress){const q=this.quest(scene.questId),intro=this.content.scenes.find(s=>s.id===q.acceptScene),outro=this.content.scenes.find(s=>s.id===q.returnScene);if(!intro||!outro)return fail('STORY_SCENE_NOT_FOUND',503);return scene.phase==='replay'?{...intro,id:'replay:'+q.id,lines:[...intro.lines,...outro.lines]}:scene.phase==='return'?outro:intro;}
 private async dto(tx:Tx,row:Roster60StoryAccount){
  if(row.contentVersion!==this.content.version)return fail('STORY_CONTENT_CHANGED');
  const p=progressOf(row),quests:QuestView[]=[];
  for(const q of this.content.quests){const status=await this.status(tx,row,q);if(status==='LOCKED')continue;const active=p.accepted[q.id];let activeBattleId:string|null=null;if(active?.battles.length){const latest=await tx.roster60Battle.findFirst({where:{id:{in:active.battles},playerId:row.playerId,status:{in:['ACTIVE','FINISHED']}},orderBy:{createdAt:'desc'}});activeBattleId=latest?.id??null;}
   quests.push({id:q.id,title:q.title,kind:q.kind,chapter:q.chapter,status,recommendedLevel:q.recommendedLevel,npcId:q.npcId,objective:{kind:q.objective.kind,wins:active?.wins??0,requiredWins:q.objective.wins},canDepart:status==='ACTIVE'&&q.objective.kind==='adventure_win'&&(active?.wins??0)<q.objective.wins&&!activeBattleId,adventureId:active?.adventureId??null,battleId:activeBattleId});}
  const currentQuest=quests.find(q=>q.kind==='main'&&q.status!=='COMPLETED')??null;
  const residents=this.content.residents.map(npc=>{const q=this.content.quests.find(q=>q.npcId===npc.id),visible=q?quests.find(v=>v.id===q.id):null;return {id:npc.id,name:npc.name,role:npc.role,status:visible?.status??'LOCKED',questId:visible?.id??null,dailyDialogue:npc.dailyDialogue,proposal:true};});
  const scene=p.scene?this.sceneData(p.scene):null,line=scene?.lines[p.scene!.cursor];
  const adventures=this.content.encounters.filter(e=>e.kind==='black'&&e.mode==='adventure'&&this.content.quests.some(q=>q.objective.encounterIds.includes(e.id)&&(p.accepted[q.id]||p.completed.includes(q.id)))).map(e=>({id:e.id,name:'흑화 갈매미 모험',level:e.level}));
  const pendingDeparture=p.departure?{operation:p.departure.questId?'battle/create':'adventure/create',body:{requestId:p.departure.requestId,...(p.departure.questId?{questId:p.departure.questId}:{encounterId:p.departure.encounterId}),ownedIds:p.departure.ownedIds,...(p.departure.requestedAdventureId?{adventureId:p.departure.requestedAdventureId}:{})}}:null;
  const materialRun=p.materialAdventureId?await tx.roster60Adventure.findFirst({where:{id:p.materialAdventureId,playerId:row.playerId,status:'ACTIVE'}}):null;
  const materialAdventure=materialRun?{adventureId:materialRun.id,status:materialRun.status,activeBattleId:materialRun.activeBattleId,revision:materialRun.revision}:null;
  const commander=await readCommanderState(tx,row.playerId,this.commanderPolicy());
  return {contentVersion:row.contentVersion,revision:row.revision,testValues:true,productionBalanceApproved:false,levelPolicy:'RECOMMENDATION_ONLY',commander,statue:{stage:p.statueStage,fragments:Object.values(p.fragments).reduce((sum,n)=>sum+n,0),hatched:p.hatched,visualAssetStatus:'APPROVED_SHEET_NOT_MATERIALIZED'},currentQuest,quests,residents,history:quests.filter(q=>q.status==='COMPLETED').map(q=>({questId:q.id,title:q.title})),adventures,pendingDeparture,materialAdventure,nextStoryHint:!currentQuest&&!p.hatched?'다음 이야기의 준비로, 모험에서 얻은 재료 기록을 확인하세요.':null,scene:p.scene&&scene&&line?{id:scene.id,questId:p.scene.questId,phase:p.scene.phase,cursor:p.scene.cursor,line:{...line,speaker:storySpeakerNames[line.speakerId]??line.speakerId,speakerName:storySpeakerNames[line.speakerId]??line.speakerId},last:p.scene.cursor===scene.lines.length-1,background:scene.background,camera:scene.camera??null}:null};
 }
 async state(playerId:string){return this.transaction(async tx=>this.dto(tx,await this.account(tx,playerId)));}
 private async mutate(playerId:string,b:{requestId:string;revision:number},payload:unknown,run:(tx:Tx,row:Roster60StoryAccount,p:StoryProgress)=>Promise<CommanderXpResult|void>){return this.transaction(async tx=>{
  const hash=fingerprint(payload),old=await tx.roster60StoryReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:b.requestId}}});if(old){if(old.fingerprint!==hash)return fail('REQUEST_ID_REUSED');return JSON.parse(old.result);}
  const row=await this.account(tx,playerId);if(row.revision!==b.revision)return fail('STORY_STALE_REVISION');if(row.contentVersion!==this.content.version)return fail('STORY_CONTENT_CHANGED');const p=progressOf(row);
  const commanderReward=await run(tx,row,p);const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');
  const result={...await this.dto(tx,{...row,state:JSON.stringify(p),revision:row.revision+1}),...(commanderReward?{commanderReward}:{})};await tx.roster60StoryReceipt.create({data:{playerId,requestId:b.requestId,fingerprint:hash,result:JSON.stringify(result)}});return result;
 });}
 async open(playerId:string,b:{requestId:string;revision:number;questId:string;phase:'intro'|'return'|'replay'}){return this.mutate(playerId,b,['open',b.questId,b.phase,b.revision],async(tx,row,p)=>{
  const q=this.quest(b.questId),status=await this.status(tx,row,q);
  if(b.phase==='intro'&&status!=='AVAILABLE'||b.phase==='return'&&status!=='READY'||b.phase==='replay'&&status!=='COMPLETED')return fail('STORY_SCENE_LOCKED');
  if(p.scene&&(p.scene.questId!==q.id||p.scene.phase!==b.phase))return fail('STORY_SCENE_ACTIVE');
  const next:SceneProgress={questId:q.id,phase:b.phase,cursor:0},data=this.sceneData(next);next.cursor=Math.min(p.checkpoints[checkpoint(next)]??0,data.lines.length-1);p.scene=next;
 });}
 async advance(playerId:string,b:{requestId:string;revision:number;cursor:number}){return this.mutate(playerId,b,['advance',b.cursor,b.revision],async(tx,row,p)=>{
  if(!p.scene||p.scene.cursor!==b.cursor)return fail('STORY_SCENE_STALE');const scene=this.sceneData(p.scene),q=this.quest(p.scene.questId);
  if(p.scene.cursor<scene.lines.length-1){p.scene.cursor++;p.checkpoints[checkpoint(p.scene)]=p.scene.cursor;return;}
  if(!p.readScenes.includes(scene.id))p.readScenes.push(scene.id);
  if(p.scene.phase==='intro')return fail('STORY_ACCEPT_REQUIRED');
  let commanderReward:CommanderXpResult|undefined;
  if(p.scene.phase==='return'){
   if(await this.status(tx,row,q)!=='READY')return fail('STORY_OBJECTIVE_INCOMPLETE');
   if(!p.completed.includes(q.id)){p.completed.push(q.id);if(q.fragmentBundle)p.fragments[q.fragmentBundle.id]=q.fragmentBundle.count;if(q.statueStageAfter)p.statueStage=Math.max(p.statueStage,q.statueStageAfter);if(q.id==='STORY_21')p.hatched=true;
    commanderReward=await awardCommanderXp(tx,playerId,{kind:q.kind==='resident'?'RESIDENT_QUEST':'STORY_QUEST',eventId:this.content.version+':'+q.id,outcome:'completed',linkedQuestId:q.id},p.accepted[q.id]?.commanderPolicy??null);}
   delete p.accepted[q.id];
  }
  delete p.checkpoints[checkpoint(p.scene)];p.scene=null;
  return commanderReward;
 });}
 async accept(playerId:string,b:{requestId:string;revision:number;questId:string}){return this.mutate(playerId,b,['accept',b.questId,b.revision],async(tx,row,p)=>{
  const q=this.quest(b.questId);if(await this.status(tx,row,q)!=='AVAILABLE'||p.scene?.phase!=='intro'||p.scene.questId!==q.id||p.scene.cursor!==this.sceneData(p.scene).lines.length-1)return fail('STORY_ACCEPT_NOT_READY');
  const unitPolicy=this.unitXpPolicy();if(unitPolicy!==null)validateStoryUnitXpPolicy(unitPolicy,this.catalog()?.version??'',this.content.version,[...new Set(this.content.quests.flatMap(q=>q.objective.encounterIds))]);
  p.accepted[q.id]={token:randomUUID(),wins:0,battles:[],commanderPolicy:structuredClone(this.commanderPolicy()),unitXpPolicy:structuredClone(unitPolicy)};if(q.fragmentBundleOnAccept)p.fragments[q.fragmentBundleOnAccept.id]=q.fragmentBundleOnAccept.count;if(q.statueStageOnAccept)p.statueStage=Math.max(p.statueStage,q.statueStageOnAccept);
  const scene=this.sceneData(p.scene);if(!p.readScenes.includes(scene.id))p.readScenes.push(scene.id);delete p.checkpoints[checkpoint(p.scene)];p.scene=null;
 });}
 async close(playerId:string,b:{requestId:string;revision:number}){return this.mutate(playerId,b,['close',b.revision],async(_tx,_row,p)=>{if(p.scene){p.checkpoints[checkpoint(p.scene)]=p.scene.cursor;p.scene=null;}});}

 /** No consumption endpoint exists. A later approved enhancement service must explicitly integrate here. */
 async materials(playerId:string){const row=await this.transaction(tx=>this.account(tx,playerId));return {currency:'dark-shrimp-gang',balance:row.darkGangBalance,lifetimeEarned:row.darkGangLifetime,statueFragmentsSeparate:true,enhancement:{enabled:false,eligibilityPolicy:'PENDING',automaticConsumption:false},preservationScope:'ACCOUNT_INDEPENDENT_OF_OWNED_REINCARNATION'};}

 private battleHook(requestId:string,questId:string|undefined,acceptToken:string|undefined,encounter:StoryEncounter):StoryBattleCreateHook{return async(tx,ctx)=>{
  const row=await this.account(tx,ctx.playerId),p=progressOf(row);if(row.contentVersion!==this.content.version)return fail('STORY_CONTENT_CHANGED');
  if(p.departure?.requestId!==requestId)return fail('STORY_DEPARTURE_CHANGED');
  if(questId){const q=this.quest(questId),accepted=p.accepted[questId];if(!accepted||accepted.token!==acceptToken||await this.status(tx,row,q)!=='ACTIVE')return fail('STORY_QUEST_NOT_ACTIVE');if(accepted.unitXpPolicy&&accepted.wins>=q.objective.wins)return fail('STORY_OBJECTIVE_AWAITING_RETURN');const expected=q.objective.encounterIds[Math.min(accepted.wins,q.objective.encounterIds.length-1)];if(expected!==encounter.id)return fail('STORY_ENCOUNTER_LOCKED');
   const active=await tx.roster60Battle.findFirst({where:{id:{in:accepted.battles},playerId:ctx.playerId,status:{in:['ACTIVE','FINISHED']}}});if(active)return fail('STORY_BATTLE_ACTIVE');accepted.battles.push(ctx.battleId);if(ctx.adventureId)accepted.adventureId=ctx.adventureId;
  }else if(!this.content.quests.some(q=>q.objective.encounterIds.includes(encounter.id)&&(p.accepted[q.id]||p.completed.includes(q.id))))return fail('STORY_ENCOUNTER_LOCKED');
  const story:StoryBattleContext={version:'story-battle-v1',contentVersion:this.content.version,encounterId:encounter.id,speciesId:encounter.speciesId,kind:encounter.kind,mode:encounter.mode,questId,acceptToken,dropAmount:this.content.policy.darkGangPerWin,maxBalance:this.content.policy.maxBalance,unitXpPolicy:questId?p.accepted[questId]?.unitXpPolicy??null:null};
  const enemy=structuredClone(ctx.enemy);if(encounter.kind==='black')enemy.artVariant='black';enemy.name=(encounter.kind==='black'?'흑화 ':'')+enemy.name;enemy.stats.hp=Math.round(enemy.stats.hp*(encounter.hpMultiplier??1));enemy.hp=enemy.stats.hp;
  for(const skill of [enemy.attack,...enemy.skills])skill.power=Math.round(skill.power*(encounter.powerMultiplier??1));
  delete p.departure;
  const changed=await tx.roster60StoryAccount.updateMany({where:{playerId:ctx.playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');
  return {story,enemy};
 };}

 async createBattle(playerId:string,b:{requestId:string;questId?:string;encounterId?:string;ownedIds:string[];adventureId?:string}){
  if(!b.requestId||!b.ownedIds.length||b.ownedIds.length>6||new Set(b.ownedIds).size!==b.ownedIds.length)return fail('INVALID_PARTY',400);
  const inputHash=fingerprint(['battle',b.questId??null,b.encounterId??null,b.ownedIds,b.adventureId??null]),battleRequestId=operationId(b.requestId,'battle');
  // One account departure reservation prevents two different parties from starting orphan runs.
  // Internal battle receipts take precedence over pending workflow state after any later return.
  const intent=await this.transaction(async tx=>{
   const old=await tx.roster60StoryReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:b.requestId}}});
   if(old&&old.fingerprint!==inputHash)return fail('REQUEST_ID_REUSED');
   const prior=old?JSON.parse(old.result):null;
   if(prior&&!prior.pending)return prior;
   const row=await this.account(tx,playerId),p=progressOf(row);
   const confirmed=old?await tx.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:battleRequestId}}}):null;
   if(confirmed){
    const result=JSON.parse(confirmed.result);await tx.roster60StoryReceipt.update({where:{playerId_requestId:{playerId,requestId:b.requestId}},data:{result:confirmed.result}});
    if(p.departure?.requestId===b.requestId){delete p.departure;const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');}
    return result;
   }
   if(prior&&prior.contentVersion!==this.content.version||row.contentVersion!==this.content.version)return fail('STORY_CONTENT_CHANGED');
   if(p.departure&&p.departure.requestId!==b.requestId)return fail('STORY_DEPARTURE_PENDING');
   // A complete battle result must be settled before another story departure can begin.
   const active=await tx.roster60Battle.findMany({where:{playerId,status:{in:['ACTIVE','FINISHED']}},select:{snapshot:true}});
   if(active.some(r=>JSON.parse(r.snapshot).story))return fail('STORY_BATTLE_ACTIVE');
   let quest:StoryQuest|undefined,encounter:StoryEncounter|undefined,token:string|undefined;
   if(b.questId){quest=this.quest(b.questId);if(await this.status(tx,row,quest)!=='ACTIVE'||quest.objective.kind!=='adventure_win')return fail('STORY_QUEST_NOT_ACTIVE');const accepted=p.accepted[quest.id];if(accepted.unitXpPolicy&&accepted.wins>=quest.objective.wins)return fail('STORY_OBJECTIVE_AWAITING_RETURN');token=accepted.token;encounter=this.content.encounters.find(e=>e.id===quest!.objective.encounterIds[Math.min(accepted.wins,quest!.objective.encounterIds.length-1)]);}
   else{encounter=this.content.encounters.find(e=>e.id===b.encounterId&&e.kind==='black'&&e.mode==='adventure');if(!encounter||!this.content.quests.some(q=>q.objective.encounterIds.includes(encounter!.id)&&(p.accepted[q.id]||p.completed.includes(q.id))))return fail('STORY_ENCOUNTER_LOCKED');}
   if(!encounter||prior&&prior.encounterId!==encounter.id)return fail('STORY_ENCOUNTER_LOCKED');
   const pending=prior??{pending:true,contentVersion:this.content.version,questId:quest?.id,acceptToken:token,encounterId:encounter.id};
   p.departure={requestId:b.requestId,questId:b.questId,encounterId:encounter.id,ownedIds:[...b.ownedIds],requestedAdventureId:b.adventureId};
   const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');
   if(!old)await tx.roster60StoryReceipt.create({data:{playerId,requestId:b.requestId,fingerprint:inputHash,result:JSON.stringify(pending)}});return pending;
  });
  if(!intent.pending)return intent;
  let started=false,linked=false;
  try{
   const encounter=this.content.encounters.find(e=>e.id===intent.encounterId)??fail('STORY_CONTENT_CHANGED');
   const base=this.catalog(),basePolicy=this.policy();if(!base||!basePolicy)return fail('BATTLE_POLICY_PENDING',503);
   const policy:BattlePolicy={...basePolicy,encounters:[{id:encounter.id,speciesId:encounter.speciesId,level:encounter.level,tutorial:false,winXp:encounter.winXp??0,lossXp:0,mode:encounter.mode,fixedEnemy:true}]};
   validatePolicy(policy,base);
   // Validate the private enemy before locking a party in a new adventure.
   if(encounter.villainProfileId)storyVillainFighter(base,encounter.villainProfileId,encounter.level,'enemy');else fighter(base,encounter.speciesId,encounter.level,'enemy');
   let adventureId=intent.adventureId??b.adventureId;
   if(encounter.mode==='adventure'&&!adventureId){
    const account=await this.db.roster60StoryAccount.findUniqueOrThrow({where:{playerId}}),progress=progressOf(account),accepted=intent.questId?progress.accepted[intent.questId]:null;
    const linkedRun=accepted?.adventureId??(!intent.questId?progress.materialAdventureId:undefined);
    if(linkedRun){const old=await this.db.roster60Adventure.findFirst({where:{id:linkedRun,playerId,status:'ACTIVE'}});if(old)adventureId=old.id;}
    if(!adventureId){const run=await this.adventure.start(playerId,{requestId:operationId(b.requestId,'adventure'),ownedIds:b.ownedIds}) as {adventureId:string;status:string};adventureId=run.adventureId;started=run.status==='ACTIVE';}
   }
   if(adventureId){await this.transaction(async tx=>{
    const run=await tx.roster60Adventure.findFirst({where:{id:adventureId,playerId,status:'ACTIVE'}});if(!run)return fail('ADVENTURE_NOT_FOUND',404);
    started=true;
    const row=await this.account(tx,playerId),p=progressOf(row);if(p.departure?.requestId!==b.requestId)return fail('STORY_DEPARTURE_CHANGED');
    // Only the validated server quest reservation can mark a run as quest-linked.
    if(intent.questId){const accepted=p.accepted[intent.questId];if(!accepted||accepted.token!==intent.acceptToken)return fail('STORY_QUEST_NOT_ACTIVE');const state=JSON.parse(run.state),pin=accepted.commanderPolicy??null;if(state.storyQuestId&&state.storyQuestId!==intent.questId)return fail('ADVENTURE_STORY_CONTEXT_CHANGED');
     if(state.storyQuestId!==intent.questId||JSON.stringify(state.commanderPolicy??null)!==JSON.stringify(pin)){state.storyQuestId=intent.questId;state.commanderPolicy=structuredClone(pin);const changed=await tx.roster60Adventure.updateMany({where:{id:run.id,playerId,revision:run.revision,status:'ACTIVE'},data:{state:JSON.stringify(state),revision:{increment:1}}});if(changed.count!==1)return fail('ADVENTURE_STATE_CHANGED');}}
    if(intent.questId){const accepted=p.accepted[intent.questId];if(!accepted||accepted.token!==intent.acceptToken)return fail('STORY_QUEST_NOT_ACTIVE');if(accepted.adventureId!==adventureId){accepted.adventureId=adventureId;const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');}}
    else if(p.materialAdventureId!==adventureId){p.materialAdventureId=adventureId;const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');}
    await tx.roster60StoryReceipt.update({where:{playerId_requestId:{playerId,requestId:b.requestId}},data:{result:JSON.stringify({...intent,adventureId})}});
   });linked=true;}
   const battle=new RosterBattle(this.db,()=>base,()=>policy,this.battleHook(b.requestId,intent.questId,intent.acceptToken,encounter),encounter.villainProfileId?(c,_species,level,id)=>storyVillainFighter(c,encounter.villainProfileId!,level,id):undefined);
   const result=await battle.create(playerId,{requestId:battleRequestId,ownedId:b.ownedIds[0],reserveIds:b.ownedIds.slice(1),encounterId:encounter.id,...(adventureId?{adventureId}:{})});
   await this.db.roster60StoryReceipt.update({where:{playerId_requestId:{playerId,requestId:b.requestId}},data:{result:JSON.stringify(result)}});return result;
  }catch(error){
   // An uncertain final workflow write must recover the already committed battle, never start again.
   const confirmed=await this.db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:battleRequestId}}});
   if(confirmed){await this.db.roster60StoryReceipt.update({where:{playerId_requestId:{playerId,requestId:b.requestId}},data:{result:confirmed.result}});return JSON.parse(confirmed.result);}
   // A known failed create keeps any linked adventure available for normal return/retry.
   // If a run started but could not be linked, retain its durable intent for another device to resume.
   const invalidRun=error instanceof RosterError&&error.code==='ADVENTURE_NOT_FOUND';
   if(!started||linked||invalidRun)await this.transaction(async tx=>{const row=await this.account(tx,playerId),p=progressOf(row);if(p.departure?.requestId===b.requestId){delete p.departure;const changed=await tx.roster60StoryAccount.updateMany({where:{playerId,revision:row.revision},data:{state:JSON.stringify(p),revision:{increment:1}}});if(changed.count!==1)return fail('STORY_STALE_REVISION');}});
   throw error;
  }
 }
}

