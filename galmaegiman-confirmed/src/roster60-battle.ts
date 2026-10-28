import {restoreBlackStoryAppearance,type BattleArtVariant} from './battle/roster60-appearance.js';
import {createStoryUnitXpPlan,storyUnitXpPlan,calculateStoryUnitXp,type StoryUnitXpMember} from './progression/story-unit-xp.js';
import {serializeRoster60Write} from './roster60-write-queue.js';
import {opponentFighter,selectEnemySpecies} from './battle/roster60-opponent-intel.js';
import {settleStoryBattle} from './story/settlement.js';
import {captureDefenseSnapshot,battleKnowledge,observedOpponentIntel,resolvedDefenseObservation,addDefenseObservation,persistDefenseObservations,publicDefenseObservation,publicDefenseSafe,safeKnowledgeCatalog,type DefenseSnapshot,type DefenseObservation} from './battle/roster60-observed-defense.js';
import {capturePresentation,presentStep} from './battle/roster60-presentation.js';
import {adventureEscapePolicy,adventureEscapeSucceeds,settleAdventureBattle} from './roster60-adventure.js';
import {loadBattleRules} from './battle/load.js';
import {createHash,randomBytes,randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient} from '@prisma/client';
import {RosterError,verifyRosterTutorial,type RosterCatalog} from './roster60-onboarding.js';
import {growthAt,focusMp,spendMp,validateRosterDefinition,type SkillElement,type ElementProfile,type Defense} from './battle/roster60-rules.js';
import {validateFiveStats,fiveRebirthStats,fiveRebirthPolicy,type FiveStats} from './battle/roster60-five-stats.js';
import {validateFiveCombatPolicy,fiveStatAttack,decideTurnOrder,reuseTurnOrder,type FiveCombatPolicy,type TurnOrderDecision} from './battle/roster60-five-combat.js';
export type BattlePolicy={approved:boolean;version:string;catalogVersion:string;calculation:FiveCombatPolicy;reflection:'return-post-durability';enemyStrategy:'first-affordable'|'best-affordable';maxRounds:number;encounters:{id:string;speciesId:string;level:number;tutorial:boolean;winXp:number;lossXp:number;mode?:'story'|'duel'|'adventure';enemySpeciesIds?:string[];fixedEnemy?:boolean}[]};
type Skill={id:string;name:string;element:SkillElement;mpCost:number;power:number};
export type Fighter={artVariant?:BattleArtVariant;ownedId?:string;id:string;speciesId:string;name:string;level:number;stats:FiveStats;hp:number;mp:number;attack:Skill;skills:Skill[];suitability:ElementProfile<number>;defense:ElementProfile<Defense>};
export type State={version:'five-battle-v1';turn:number;fighters:[Fighter,Fighter];decision:TurnOrderDecision;outcome:'win'|'loss'|'draw'|'escaped'|null;events:unknown[];adventureId?:string;reserves?:Fighter[];mode?:'story'|'duel'|'adventure';surrendered?:boolean;rulesVersion?:'party-actions-v2';inventory?:{itemId:string;count:number}[];participation?:{ownedId:string;kind:'start'|'switch'|'forced-switch';turn:number}[]};
export type BattleAction={kind:'attack'|'skill'|'focus'|'switch'|'item'|'escape';skillId?:string;targetOwnedId?:string;itemId?:string};
export type StoryBattleCreateHook=(tx:Prisma.TransactionClient,context:{battleId:string;playerId:string;encounter:BattlePolicy['encounters'][number];enemy:Fighter;adventureId?:string})=>Promise<{story:unknown;enemy?:Fighter}>;
export type StoryEnemyFactory=(c:RosterCatalog,speciesId:string,level:number,id:string)=>Fighter;
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const hash=(v:unknown)=>createHash('sha256').update(JSON.stringify(v)).digest('hex');
export function validatePolicy(p:BattlePolicy|null,c:RosterCatalog|null):asserts p is BattlePolicy{
 if(p?.approved!==true||c?.approved!==true||c.statSchema!=='gmm-five-stats-v1'||p.catalogVersion!==c.version)return fail('BATTLE_POLICY_PENDING',503);
 try{validateFiveCombatPolicy(p.calculation);}catch{return fail('BATTLE_POLICY_INVALID',503);}
 if(typeof p.version!=='string'||!p.version||p.reflection!=='return-post-durability'||!['first-affordable','best-affordable'].includes(p.enemyStrategy)||!Number.isSafeInteger(p.maxRounds)||p.maxRounds<1||p.maxRounds>1000||!Array.isArray(p.encounters))return fail('BATTLE_POLICY_INVALID',503);
 for(const e of p.encounters)if((e.mode!==undefined&&!['story','duel','adventure'].includes(e.mode))||!e.id||typeof e.tutorial!=='boolean'||!Number.isInteger(e.level)||e.level<1||e.level>60||[e.winXp,e.lossXp].some(n=>!Number.isSafeInteger(n)||n<0))return fail('BATTLE_POLICY_INVALID',503);
 for(const e of p.encounters)if((e.fixedEnemy!==undefined&&typeof e.fixedEnemy!=='boolean')||(e.enemySpeciesIds!==undefined&&(!Array.isArray(e.enemySpeciesIds)||!e.enemySpeciesIds.length||new Set(e.enemySpeciesIds).size!==e.enemySpeciesIds.length||e.enemySpeciesIds.some(id=>!c.characters.some(u=>u.id===id)))))return fail('BATTLE_POLICY_INVALID',503);
 if(new Set(p.encounters.map(e=>e.id)).size!==p.encounters.length)return fail('BATTLE_POLICY_INVALID',503);
}
export function fighter(c:RosterCatalog,speciesId:string,level:number,id:string,cycle=0):Fighter{
 const matches=c.characters.filter(x=>x.id===speciesId);const u=matches.length===1?matches[0]:undefined;if(!u)return fail('BATTLE_UNIT_DATA_PENDING',503);
 try{validateRosterDefinition(u);}catch{return fail('BATTLE_UNIT_DATA_INVALID',503);}
 const stats=(u as typeof u&{combatGrowth?:FiveStats[]}).combatGrowth?.[level-1];
 try{validateFiveStats(stats);}catch{return fail('BATTLE_GROWTH_PENDING',503);}
 if(stats!.mp!==u.growth[level-1]?.maxMp)return fail('BATTLE_MP_GROWTH_MISMATCH',503);
 for(const sk of [u.attack,...u.skills] as Skill[]){
  if(!Number.isFinite(sk.power)||sk.power<0||'effects' in sk||'priority' in sk)return fail('BATTLE_SKILL_POLICY_PENDING',503);
 }
 return {id,speciesId,name:u.name,level,stats:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle),hp:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle).hp,mp:fiveRebirthStats((u as typeof u&{levelOneStats:FiveStats}).levelOneStats??stats!,stats!,cycle).mp,attack:structuredClone(u.attack as Skill),skills:structuredClone((u.skills as (Skill&{learnLevel?:number})[]).filter(s=>(s.learnLevel??1)<=level)),suitability:structuredClone(u.suitability),defense:structuredClone(u.defense)};
}
/** Owned growth is authoritative for formation previews; ranked normalization happens only on match entry. */
export function formationUnit(c:RosterCatalog,u:{id:string;speciesId:string;totalXp:bigint;reincarnation:number}){
 const def=c.characters.find(d=>d.id===u.speciesId);if(!def)return fail('BATTLE_UNIT_DATA_PENDING',503);
 if(u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
 const growth=growthAt(Number(u.totalXp),def.growth,u.reincarnation,def.reincarnationXpPolicy);
 const f=fighter(c,u.speciesId,growth.level,u.id,u.reincarnation);
 return {ownedId:u.id,speciesId:u.speciesId,name:f.name,rarity:def.rarity,level:growth.level,maxLevel:growth.maxLevel,stats:f.stats};
}

function order(id:string,state:Pick<State,'turn'|'fighters'>,seed:string){const f=(i:number)=>({id:state.fighters[i].id,baseSpeed:state.fighters[i].stats.speed,modifiers:[] as number[]});return decideTurnOrder(id,state.turn,[f(0),f(1)],seed);}
function dto(row:{id:string;revision:number;status:string;state:string;settlement:string|null;snapshot?:string;seed:string},catalog:RosterCatalog|null){
 const s=JSON.parse(row.state) as State,snapshot=row.snapshot?JSON.parse(row.snapshot):null;const policy=snapshot?.policy;
 restoreBlackStoryAppearance(s,snapshot?.story);
 const intel=observedOpponentIntel(s.fighters[1],s.turn,snapshot?.observedDefense,battleKnowledge(snapshot?.observedDefense,s.events,'self'),catalog);
 const {speeds,...publicDecision}=s.decision;
 const events=s.events.map((round:any)=>({...round,events:round.events.map((event:any)=>{
  const {defenseObservation,intelBefore,intelAfter,...visible}=event;
  return {...visible,...(event.actor==='self'&&defenseObservation?{observedDefense:publicDefenseObservation(defenseObservation)}:{}),
   ...(event.presentation?{presentation:{...event.presentation,...(intelBefore?{opponentIntelBefore:intelBefore}:{}),...(intelAfter?{opponentIntelAfter:intelAfter}:{})}}:{})};
 })}));
 const publicState={...s,events,opponentIntel:intel,fighters:[publicDefenseSafe(s.fighters[0]),opponentFighter(s.fighters[1],intel)],reserves:(s.reserves??[]).map(publicDefenseSafe),decision:{...publicDecision,...(intel.statsKnown?{speeds}:{})}};
 return {balanceProfile:policy?.version??null,testBaseline:policy?.profileKind==='user-authorized-test-baseline',battleId:row.id,revision:row.revision,status:row.status,state:publicState,settlement:row.settlement?JSON.parse(row.settlement):null,itemDefinitions:policy&&row.snapshot?JSON.parse(row.snapshot).items??{}:{},disabledCommands:{item:s.fighters[0].hp<=0?'FORCED_SWITCH_REQUIRED':s.rulesVersion?'':'ITEM_POLICY_PENDING',switch:s.rulesVersion?'':'SWITCH_POLICY_PENDING',escape:s.fighters[0].hp<=0?'FORCED_SWITCH_REQUIRED':['story','duel','adventure'].includes(s.mode??'')?'':'ESCAPE_POLICY_PENDING'}};
}
export class RosterBattle {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>BattlePolicy|null,private storyCreate?:StoryBattleCreateHook,private storyEnemy?:StoryEnemyFactory){}
 private view(row:Parameters<typeof dto>[0]){return dto(row,safeKnowledgeCatalog(this.catalog));}
 private async request(playerId:string,requestId:string,payload:unknown,run:(tx:Prisma.TransactionClient)=>Promise<unknown>){
  const fingerprint=hash(payload);const once=()=>this.db.$transaction(async tx=>{
   await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
   const prior=await tx.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});
   if(prior){if(prior.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');return publicDefenseSafe(JSON.parse(prior.result));}
   const result=await run(tx);await tx.roster60BattleReceipt.create({data:{playerId,requestId,fingerprint,result:JSON.stringify(result)}});return result;
  });
  return serializeRoster60Write(this.db,async()=>{try{return await once();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return once();throw e;}});
 }
 async options(playerId:string){
  const c=this.catalog(),p=this.policy();validatePolicy(p,c);
  const rows=await this.db.roster60Owned.findMany({where:{playerId,status:'AVAILABLE',locked:false},orderBy:{id:'asc'},take:1000});
  return {units:rows.map(u=>formationUnit(c!,u)),encounters:p.encounters.map(e=>({id:e.id,name:c!.characters.find(u=>u.id===e.speciesId)?.name??e.speciesId,tutorial:e.tutorial,mode:e.mode??null})),catalogVersion:c!.version};
 }
 async create(playerId:string,b:{requestId:string;ownedId:string;encounterId:string;reserveIds?:string[];adventureId?:string}){
  return this.request(playerId,b.requestId,['create',b.ownedId,b.encounterId,b.reserveIds??[],b.adventureId??null],async tx=>{
   const c=this.catalog(),p=this.policy();validatePolicy(p,c);
   const encounter=p.encounters.find(e=>e.id===b.encounterId);if(!encounter)return fail('ENCOUNTER_NOT_ALLOWED');
   const run=b.adventureId?await tx.roster60Adventure.findFirst({where:{id:b.adventureId,playerId,status:'ACTIVE'}}):null;
   if(encounter.mode==='adventure'&&!run)return fail('ADVENTURE_REQUIRED');
   if(b.adventureId&&(!run||encounter.mode!=='adventure'))return fail('ADVENTURE_NOT_FOUND',404);
   if(run?.activeBattleId)return fail('ADVENTURE_BATTLE_ACTIVE');
   if(run&&run.catalogVersion!==c!.version)return fail('CATALOG_CHANGED');
   const runState=run?JSON.parse(run.state):null,sourceStatus=run?'ADVENTURE':'AVAILABLE';
   if(runState){const ids=[b.ownedId,...(b.reserveIds??[])];if(ids.length!==runState.party.length||runState.party.some((f:Fighter)=>!ids.includes(f.ownedId!)))return fail('INVALID_PARTY');const active=runState.party.find((f:Fighter)=>f.ownedId===b.ownedId);if(!active?.hp)return fail('ACTIVE_UNIT_FAINTED');}
   const owned=await tx.roster60Owned.findFirst({where:{id:b.ownedId,playerId}});if(!owned)return fail('UNIT_NOT_FOUND',404);
   if(owned.reincarnation&&c!.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);
   if(owned.status!==sourceStatus||owned.locked)return fail('UNIT_UNAVAILABLE');
   if(owned.totalXp<0n||owned.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
   const def=c!.characters.find(u=>u.id===owned.speciesId);if(!def)return fail('BATTLE_UNIT_DATA_PENDING',503);
   const level=growthAt(Number(owned.totalXp),def.growth,owned.reincarnation,def.reincarnationXpPolicy).level;
   // Unknown cap-overflow policy must not trap a unit in an un-settleable battle.
   try{growthAt(Number(owned.totalXp)+(run?JSON.parse(run.pendingRewards).xp[owned.id]??0:0)+Math.max(encounter.winXp,encounter.lossXp),def.growth,owned.reincarnation,def.reincarnationXpPolicy);}catch(e){return fail((e as Error).message);}
   // Tutorial uses any eligible owned party member; starter grants remain an onboarding concern.
   const ids=[b.ownedId,...(b.reserveIds??[])];if(ids.length>6||new Set(ids).size!==ids.length)return fail('INVALID_PARTY',400);
   const reserves:Fighter[]=[];const members=[{ownedId:owned.id,startingXp:owned.totalXp.toString()}];
   let unitXpMembers:StoryUnitXpMember[]=[{ownedId:owned.id,playerId,startingXp:owned.totalXp.toString(),growth:def.growth,reincarnation:owned.reincarnation,reincarnationXpPolicy:def.reincarnationXpPolicy}];
   for(const reserveId of ids.slice(1)){
    const unit=await tx.roster60Owned.findFirst({where:{id:reserveId,playerId}});if(!unit)return fail('UNIT_NOT_FOUND',404);
    if(unit.status!==sourceStatus||unit.locked)return fail('UNIT_UNAVAILABLE');
    if(unit.reincarnation&&c!.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);
    if(unit.totalXp<0n||unit.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
    const d=c!.characters.find(u=>u.id===unit.speciesId);if(!d)return fail('BATTLE_UNIT_DATA_PENDING',503);
    const lv=growthAt(Number(unit.totalXp),d.growth,unit.reincarnation,d.reincarnationXpPolicy).level;
    // Any reserve may enter: preflight the full award so normal return cannot be trapped by growth limits.
    if(run)try{const next=Number(unit.totalXp)+(JSON.parse(run.pendingRewards).xp[unit.id]??0)+encounter.winXp;if(!Number.isSafeInteger(next))return fail('INVALID_XP');growthAt(next,d.growth,unit.reincarnation,d.reincarnationXpPolicy);}catch(e){return fail((e as Error).message);}
    reserves.push({...fighter(c!,unit.speciesId,lv,'self',unit.reincarnation),ownedId:unit.id});members.push({ownedId:unit.id,startingXp:unit.totalXp.toString()});
    unitXpMembers.push({ownedId:unit.id,playerId,startingXp:unit.totalXp.toString(),growth:d.growth,reincarnation:unit.reincarnation,reincarnationXpPolicy:d.reincarnationXpPolicy});
   }
   const id=randomUUID(),seed=randomBytes(32).toString('hex');
   const f:[Fighter,Fighter]=[fighter(c!,owned.speciesId,level,'self',owned.reincarnation),(this.storyEnemy??fighter)(c!,selectEnemySpecies(encounter,p.encounters,seed),encounter.level,'enemy')];
   f[0].ownedId=owned.id;
   const storyExtension=this.storyCreate?await this.storyCreate(tx,{battleId:id,playerId,encounter,enemy:f[1],adventureId:run?.id}):null;
   if(storyExtension?.enemy)f[1]=storyExtension.enemy;
   const storyContext=storyExtension?.story as any;
   if(runState&&storyContext?.unitXpPolicy){
    unitXpMembers=unitXpMembers.map(member=>{const saved=runState.members.find((m:any)=>m.ownedId===member.ownedId);if(!saved||saved.startingXp!==member.startingXp||saved.reincarnation!==member.reincarnation)return fail('UNIT_STATE_CHANGED');return {...member,growth:saved.growth,reincarnationXpPolicy:saved.reincarnationXpPolicy};});
   }
   const unitXpPlan=createStoryUnitXpPlan(playerId,id,storyContext,encounter,c!.version,unitXpMembers);
   if(runState)for(const next of [f[0],...reserves]){const prior=runState.party.find((x:Fighter)=>x.ownedId===next.ownedId);if(!prior)return fail('INVALID_PARTY');next.hp=prior.hp;}
   const items=loadBattleRules().data.items;
   const observedDefense=await captureDefenseSnapshot(tx,c!,{self:{playerId,targets:[f[1]]}});
   const state:State={participation:[{ownedId:owned.id,kind:'start',turn:0}],rulesVersion:'party-actions-v2',adventureId:run?.id,reserves,mode:encounter.mode,inventory:await tx.playerItem.findMany({where:{playerId},select:{itemId:true,count:true}}),version:'five-battle-v1',turn:0,fighters:f,decision:order(id,{turn:0,fighters:f},seed),outcome:null,events:[]};
   const lock=await tx.roster60Owned.updateMany({where:{id:owned.id,playerId,status:sourceStatus,locked:false,totalXp:owned.totalXp},data:{status:'BATTLE'}});if(lock.count!==1)return fail('UNIT_UNAVAILABLE');
   for(const member of members.slice(1)){const locked=await tx.roster60Owned.updateMany({where:{id:member.ownedId,playerId,status:sourceStatus,locked:false,totalXp:BigInt(member.startingXp)},data:{status:'BATTLE'}});if(locked.count!==1)return fail('UNIT_UNAVAILABLE');}
   const row=await tx.roster60Battle.create({data:{id,playerId,ownedId:owned.id,seed,snapshot:JSON.stringify({policy:p,encounter,items,members,observedDefense,...(storyExtension?{story:storyExtension.story}:{}),...(unitXpPlan?{storyUnitXp:unitXpPlan}:{}),adventureId:run?.id,escapePolicy:adventureEscapePolicy,xpRecipient:run?'adventure-participation-100-reserve-50-v1':'initial-owned-existing-v1',growth:def.growth,startingXp:owned.totalXp.toString()}),state:JSON.stringify(state)}});
   if(run){const claimed=await tx.roster60Adventure.updateMany({where:{id:run.id,playerId,revision:run.revision,status:'ACTIVE',activeBattleId:null},data:{activeBattleId:id,revision:{increment:1}}});if(claimed.count!==1)return fail('ADVENTURE_STATE_CHANGED');}
   return this.view(row);
  });
 }
 async read(playerId:string,id:string){const row=await this.db.roster60Battle.findFirst({where:{id,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);return this.view(row);}
 async active(playerId:string){return {battles:(await this.db.roster60Battle.findMany({where:{playerId,status:{in:['ACTIVE','FINISHED']}},orderBy:{createdAt:'desc'},take:20})).map(row=>this.view(row))};}
 async action(playerId:string,b:{requestId:string;battleId:string;revision:number;action:BattleAction}){
  return this.request(playerId,b.requestId,['action',b.battleId,b.revision,b.action.kind,b.action.skillId??null,b.action.targetOwnedId??null,b.action.itemId??null],async tx=>{
   const row=await tx.roster60Battle.findFirst({where:{id:b.battleId,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);
   if(row.revision!==b.revision)return fail('STALE_REVISION');if(row.status!=='ACTIVE')return fail('BATTLE_FINISHED');
   const state=JSON.parse(row.state) as State,snap=JSON.parse(row.snapshot) as {policy:BattlePolicy;items?:ReturnType<typeof loadBattleRules>['data']['items'];observedDefense?:DefenseSnapshot};
   const knowledge=battleKnowledge(snap.observedDefense,state.events,'self'),observations:DefenseObservation[]=[];
   const currentCatalog=safeKnowledgeCatalog(this.catalog);
   const intel=()=>observedOpponentIntel(state.fighters[1],state.turn,snap.observedDefense,knowledge,currentCatalog);
   if(state.fighters[0].hp<=0&&b.action.kind!=='switch')return fail('FORCED_SWITCH_REQUIRED');
   const events:unknown[]=[];let actionConsumed=false;
   const commandBefore=capturePresentation(state.fighters);
   const commandIntel=intel();
   if(b.action.kind==='escape'){
    if(!['story','duel','adventure'].includes(state.mode??''))return fail('ESCAPE_POLICY_PENDING');
    const adventure=state.mode==='adventure';
    if(adventure&&state.fighters[0].hp<=0)return fail('FORCED_SWITCH_POLICY_PENDING');
    const escaped=adventure&&adventureEscapeSucceeds(row.seed,row.id,state.turn);
    if(!adventure||escaped){
     state.surrendered=!adventure;state.outcome=adventure?'escaped':'loss';state.events.push({turn:state.turn,events:[{actor:'self',kind:adventure?'escape-success':'surrender',presentation:presentStep(commandBefore,state.fighters),intelBefore:commandIntel,intelAfter:intel()}]});
     const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'ACTIVE'},data:{state:JSON.stringify(state),revision:{increment:1},status:'FINISHED'}});if(changed.count!==1)return fail('STALE_REVISION');
     return this.view({...row,state:JSON.stringify(state),revision:row.revision+1,status:'FINISHED'});
    }
    actionConsumed=true;events.push({actor:'self',kind:'escape-failed',presentation:presentStep(commandBefore,state.fighters),intelBefore:commandIntel,intelAfter:intel()});
   }
   if(b.action.kind==='switch'){
    const forced=state.fighters[0].hp<=0;
    const ix=state.reserves?.findIndex(f=>f.ownedId===b.action.targetOwnedId)??-1;
    if(ix<0||!state.reserves![ix].hp)return fail('INVALID_SWITCH');
    const outgoing=state.fighters[0];state.participation??=[{ownedId:row.ownedId,kind:'start',turn:0}];state.fighters[0]=state.reserves![ix];state.reserves![ix]=outgoing;actionConsumed=true;
    state.participation.push({ownedId:state.fighters[0].ownedId!,kind:forced?'forced-switch':'switch',turn:state.turn});
    events.push({actor:'self',kind:forced?'forced-switch':'switch',from:outgoing.ownedId,to:state.fighters[0].ownedId,presentation:presentStep(commandBefore,state.fighters),intelBefore:commandIntel,intelAfter:intel()});
    if(forced){state.decision=order(row.id,state,row.seed);state.events.push({turn:state.turn,events});const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'ACTIVE'},data:{state:JSON.stringify(state),revision:{increment:1}}});if(changed.count!==1)return fail('STALE_REVISION');return this.view({...row,state:JSON.stringify(state),revision:row.revision+1});}
   }
   if(b.action.kind==='item'){
    const itemId=b.action.itemId??'',it=Object.prototype.hasOwnProperty.call(snap.items??{},itemId)?snap.items![itemId]:null;if(!it)return fail('INVALID_ITEM');
    const active=state.fighters[0],target=it.revive?[active,...(state.reserves??[])].find(f=>f.ownedId===b.action.targetOwnedId):active;
    if(!target||(!it.revive&&b.action.targetOwnedId&&b.action.targetOwnedId!==active.ownedId))return fail('INVALID_ITEM_TARGET');
    if(it.revive?target.hp>0:active.hp<=0)return fail('INVALID_ITEM_TARGET');
    if(!it.revive&&!it.heal)return fail('NO_STATUS_TO_CLEANSE');
    if(it.heal&&active.hp>=active.stats.hp)return fail('HP_FULL');
    if(active.hp<=0&&target!==active)return fail('ACTIVE_UNIT_FAINTED');
    const used=await tx.playerItem.updateMany({where:{playerId,itemId,count:{gt:0}},data:{count:{decrement:1}}});if(used.count!==1)return fail('ITEM_UNAVAILABLE');
    const before=target.hp;target.hp=it.revive?Math.round(target.stats.hp*it.revive/100):Math.min(target.stats.hp,target.hp+Math.round(target.stats.hp*it.heal!/100));
    events.push({actor:'self',kind:'item',itemId,targetOwnedId:target.ownedId,recovered:target.hp-before,presentation:presentStep(commandBefore,state.fighters),intelBefore:commandIntel,intelAfter:intel()});actionConsumed=true;
   }
   const self=state.fighters[0],enemy=state.fighters[1];
   const playerSkill=b.action.kind==='attack'?self.attack:b.action.kind==='skill'?self.skills.find(s=>s.id===b.action.skillId):null;
   if(!actionConsumed&&self.hp<=0)return fail('ACTIVE_UNIT_FAINTED');
   if(!actionConsumed&&b.action.kind!=='focus'&&!playerSkill)return fail('SKILL_NOT_EQUIPPED');
   if(playerSkill&&playerSkill.mpCost>self.mp)return fail('INSUFFICIENT_MP');
   // AI rule is explicit in pinned server policy, never a client action.
   let enemySkill=[enemy.attack,...enemy.skills].find(s=>s.mpCost<=enemy.mp);
   if(snap.policy.enemyStrategy==='best-affordable'){
    const score=(sk:Skill)=>fiveStatAttack({power:sk.power,level:enemy.level,durability:self.stats.durability,suitability:enemy.suitability[sk.element],defense:self.defense[sk.element],attackerHp:enemy.hp,defenderHp:self.hp},snap.policy.calculation,(b,f)=>b*f);
    const usable=[enemy.attack,...enemy.skills].filter(sk=>sk.mpCost<=enemy.mp);
    usable.sort((a,b)=>{const x=score(a),y=score(b);return (y.reflected?-y.damage:y.damage)-(x.reflected?-x.damage:x.damage)||a.mpCost-b.mpCost;});
    enemySkill=usable[0];
    const bestNow=enemySkill?score(enemySkill).damage:0;
    const afterFocus=focusMp(enemy.mp,enemy.stats.mp).mp;
    if(enemy.skills.some(sk=>sk.mpCost>enemy.mp&&sk.mpCost<=afterFocus&&!score(sk).reflected&&score(sk).damage>2*bestNow))enemySkill=undefined;
   }
   const decision=reuseTurnOrder(state.decision,row.id,state.turn);
   for(const who of decision.order){
    if(who==='self'&&actionConsumed)continue;
    const actor=who==='self'?self:enemy,target=who==='self'?enemy:self;if(!actor.hp||!target.hp)continue;
    const skill=who==='self'?playerSkill:enemySkill;
    const stepBefore=capturePresentation(state.fighters);
    const intelBefore=intel();
    if(!skill){const next=focusMp(actor.mp,actor.stats.mp);actor.mp=next.mp;events.push({actor:who,kind:'focus',recovered:next.recovered,presentation:presentStep(stepBefore,state.fighters),intelBefore,intelAfter:intel()});continue;}
    actor.mp=spendMp(actor.mp,skill.mpCost);
    const hit=fiveStatAttack({power:skill.power,level:actor.level,durability:target.stats.durability,suitability:actor.suitability[skill.element],defense:target.defense[skill.element],attackerHp:actor.hp,defenderHp:target.hp},snap.policy.calculation,(base,fit)=>base*fit);
    if(hit.target==='attacker')actor.hp-=hit.damage;else target.hp-=hit.damage;
    const defenseObservation=who==='self'?resolvedDefenseObservation(target,skill.element,hit,'self',snap.observedDefense,currentCatalog,knowledge):null;
    if(defenseObservation){observations.push(defenseObservation);addDefenseObservation(knowledge,defenseObservation,snap.observedDefense);}
    events.push({actor:who,kind:'skill',skillId:skill.id,...hit,presentation:presentStep(stepBefore,state.fighters,skill),...(defenseObservation?{defenseObservation}:{}),intelBefore,intelAfter:intel()});
   }
   state.events.push({turn:state.turn,decision,events});state.turn++;
   state.outcome=self.hp<=0&&!(state.reserves??[]).some(f=>f.hp>0)?'loss':enemy.hp<=0?'win':state.turn>=snap.policy.maxRounds?'draw':null;
   if(!state.outcome)state.decision=order(row.id,state,row.seed);
   state.inventory=await tx.playerItem.findMany({where:{playerId},select:{itemId:true,count:true}});
   const status=state.outcome?'FINISHED':'ACTIVE';
   const updated=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'ACTIVE'},data:{state:JSON.stringify(state),revision:{increment:1},status}});
   if(updated.count!==1)return fail('STALE_REVISION');
   await persistDefenseObservations(tx,{self:playerId},observations);
   return this.view({...row,state:JSON.stringify(state),revision:row.revision+1,status});
  });
 }
 async settle(playerId:string,b:{requestId:string;battleId:string;revision:number}){
  return this.request(playerId,b.requestId,['settle',b.battleId,b.revision],async tx=>{
   const row=await tx.roster60Battle.findFirst({where:{id:b.battleId,playerId}});if(!row)return fail('BATTLE_NOT_FOUND',404);
   if(row.status==='SETTLED')return this.view(row); // same battle never awards twice, even with a new request ID
   if(row.revision!==b.revision)return fail('STALE_REVISION');if(row.status!=='FINISHED')return fail('BATTLE_NOT_FINISHED');
   const state=JSON.parse(row.state) as State,snap=JSON.parse(row.snapshot);
   if(snap.adventureId){
    const adventureSettlement=await settleAdventureBattle(tx,row,state,snap);
    const storyReward=await settleStoryBattle(tx,row,state,snap);
    const settlement={...adventureSettlement,...(storyReward?{story:storyReward}:{})};
    const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'FINISHED'},data:{status:'SETTLED',revision:{increment:1},settlement:JSON.stringify(settlement)}});if(changed.count!==1)return fail('STALE_REVISION');
    return this.view({...row,revision:row.revision+1,status:'SETTLED',settlement:JSON.stringify(settlement)});
   }
   const unitPlan=storyUnitXpPlan(row,snap);
   if(unitPlan){
    const reward=calculateStoryUnitXp(unitPlan,row,state),storyReward=await settleStoryBattle(tx,row,state,snap),primary=reward.units.find(unit=>unit.ownedId===row.ownedId)!;
    const settlement={outcome:state.outcome,xpAwarded:reward.xpAccepted,xpDiscarded:reward.xpDiscarded,xpDeferred:0,xpByOwnedId:reward.xpByOwnedId,totalXp:primary.endingXp,policyVersion:unitPlan.policy.version,unitXp:{...reward,delivery:'immediate'},...(storyReward?{story:storyReward}:{})};
    const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'FINISHED'},data:{status:'SETTLED',revision:{increment:1},settlement:JSON.stringify(settlement)}});if(changed.count!==1)return fail('STALE_REVISION');
    for(const unit of reward.units){const member=unitPlan.members.find(m=>m.ownedId===unit.ownedId)!;const released=await tx.roster60Owned.updateMany({where:{id:unit.ownedId,playerId,status:'BATTLE',totalXp:BigInt(member.startingXp),reincarnation:member.reincarnation},data:{status:'AVAILABLE',totalXp:BigInt(unit.endingXp)}});if(released.count!==1)return fail('UNIT_STATE_CHANGED');}
    await tx.player.update({where:{id:playerId},data:{revision:{increment:1}}});return this.view({...row,revision:row.revision+1,status:'SETTLED',settlement:JSON.stringify(settlement)});
   }
   const xp=state.surrendered?0:state.outcome==='win'?snap.encounter.winXp:state.outcome==='loss'?snap.encounter.lossXp:0;
   const owned=await tx.roster60Owned.findFirst({where:{id:row.ownedId,playerId,status:'BATTLE'}});if(!owned)return fail('UNIT_UNAVAILABLE');
   if(owned.totalXp.toString()!==snap.startingXp)return fail('UNIT_STATE_CHANGED');
   const nextXp=owned.totalXp+BigInt(xp);if(nextXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
   try{growthAt(Number(nextXp),snap.growth,owned.reincarnation);}catch(e){return fail((e as Error).message);}
   const storyReward=await settleStoryBattle(tx,row,state,snap);
   const settlement={outcome:state.outcome,xpAwarded:xp,totalXp:nextXp.toString(),policyVersion:snap.policy.version,...(storyReward?{story:storyReward}:{})};
   const changed=await tx.roster60Battle.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'FINISHED'},data:{status:'SETTLED',revision:{increment:1},settlement:JSON.stringify(settlement)}});if(changed.count!==1)return fail('STALE_REVISION');
   const released=await tx.roster60Owned.updateMany({where:{id:owned.id,playerId,status:'BATTLE',totalXp:owned.totalXp},data:{status:'AVAILABLE',totalXp:nextXp}});if(released.count!==1)return fail('UNIT_STATE_CHANGED');
   for(const member of (snap.members??[]).filter((m:{ownedId:string})=>m.ownedId!==row.ownedId)){const released=await tx.roster60Owned.updateMany({where:{id:member.ownedId,playerId,status:'BATTLE',totalXp:BigInt(member.startingXp)},data:{status:'AVAILABLE'}});if(released.count!==1)return fail('UNIT_STATE_CHANGED');}
   await tx.player.update({where:{id:playerId},data:{revision:{increment:1}}});
   if(state.outcome==='win'&&snap.encounter.tutorial)await verifyRosterTutorial(tx,playerId,row.id);
   return this.view({...row,revision:row.revision+1,status:'SETTLED',settlement:JSON.stringify(settlement)});
  });
 }
}

