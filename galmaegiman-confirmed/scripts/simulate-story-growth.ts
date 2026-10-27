import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {RosterBattle,fighter,type BattleAction,type BattlePolicy,type Fighter} from '../src/roster60-battle.js';
import {storyVillainFighter} from '../src/story/villains.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import type {StoryContent,StoryEncounter,StoryQuest} from '../src/story/types.js';
import {growthAt,focusMp} from '../src/battle/roster60-rules.js';
import {fiveStatAttack,decideTurnOrder} from '../src/battle/roster60-five-combat.js';
import {captureDefenseSnapshot} from '../src/battle/roster60-observed-defense.js';

const root=fileURLToPath(new URL('../',import.meta.url));
type Policy={approved:true;version:string;profileKind:'user-authorized-test-baseline';productionBalanceApproved:false;testValues:true;catalogVersion:string;contentVersion:string;
 scope:'accepted-quest-battles-only';distribution:'equal-actually-entered';loss:'zero-existing-story';surrender:'zero';draw:'zero';capOverflow:'discard-overflow';questCompletionUnitXp:'none';rewards:Record<string,{winXp:number;lossXp:0}>;notes?:string[]};
export function loadStoryGrowthInputs(){const read=(name:string)=>JSON.parse(readFileSync(resolve(root,name),'utf8'));return {
 catalog:read('data/test-profiles/five-stat-device-test-v1/catalog.json') as RosterCatalog,
 battlePolicy:read('data/test-profiles/five-stat-device-test-v1/battle-policy.json') as BattlePolicy,
 content:read('data/story/story-mode-v1.json') as StoryContent,
 commanderPolicy:read('data/test-profiles/five-stat-device-test-v1/commander-policy.json'),
 unitPolicy:read('data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json') as Policy};}

/** Main order is unchanged; each existing resident quest is played at its first prerequisite unlock. */
export function storyGrowthOrder(content:StoryContent):StoryQuest[]{
 const result:StoryQuest[]=[],completed=new Set<string>();
 const add=(q:StoryQuest)=>{if(q.unlockAfter.some(id=>!completed.has(id)))throw Error('STORY_PREREQUISITE_ORDER:'+q.id);result.push(q);completed.add(q.id);};
 for(const q of content.quests.filter(q=>q.kind==='main')){add(q);for(const side of content.quests.filter(q=>q.kind==='resident'))if(!completed.has(side.id)&&side.unlockAfter.every(id=>completed.has(id)))add(side);}
 if(result.length!==content.quests.length)throw Error('STORY_UNREACHABLE_RESIDENT');return result;
}

/** Reproducible proposal, not a runtime reward formula. Only the resulting explicit JSON is configured. */
export function proposeStoryUnitXpPolicy(catalog:RosterCatalog,content:StoryContent){
 const order=storyGrowthOrder(content),curve=catalog.characters.find(u=>u.id==='u01')!.growth,rewards:Policy['rewards']={},budget:any[]=[];let target=1;
 for(let i=0;i<order.length;i++){
  const q=order[i],encounters=q.objective.encounterIds.map(id=>content.encounters.find(e=>e.id===id)!);
  if(!encounters.length){budget.push({questId:q.id,encounters:[],levelBefore:target,levelAfter:target,partyXp:0});continue;}
  const next=order.slice(i+1).flatMap(q=>q.objective.encounterIds).map(id=>content.encounters.find(e=>e.id===id)!)[0];
  const before=target;target=next?Math.min(59,Math.max(target+1,next.level+3)):60;
  const eachTotal=curve[target-1].totalXp-curve[before-1].totalXp;
  encounters.forEach((e,n)=>{rewards[e.id]={winXp:3*(Math.floor(eachTotal/encounters.length)+(n<eachTotal%encounters.length?1:0)),lossXp:0};});
  budget.push({questId:q.id,encounters:encounters.map(e=>e.id),levelBefore:before,levelAfter:target,partyXp:eachTotal*3,
   payout:encounters[0].mode==='adventure'?'after-normal-return':'battle-settlement'});
 }
 if(Object.keys(rewards).length!==content.encounters.length)throw Error('STORY_REWARD_COVERAGE');
 const policy:Policy={approved:true,version:'story-unit-xp-campaign-test-v1',profileKind:'user-authorized-test-baseline',productionBalanceApproved:false,testValues:true,
  catalogVersion:catalog.version,contentVersion:content.version,scope:'accepted-quest-battles-only',distribution:'equal-actually-entered',loss:'zero-existing-story',surrender:'zero',draw:'zero',capOverflow:'discard-overflow',questCompletionUnitXp:'none',rewards,
  notes:[
   'All amounts and the +3-level reward pacing buffer are isolated test proposals, not production balance approval.',
   'Uses the original non-reincarnated u01 growth thresholds: three actually entered sample units share 655359 XP, 218453 each at level 60. This is not a promise that all owned units reach 60.',
   'The three-member sample starts u01/u02/u03 at level 1 with zero XP as an explicit ownership fixture; the third member is not a guaranteed onboarding grant.',
   'Also check the guaranteed-ownership u01 plus common-choice u02 sample with the same rewards; two entered units receive larger shares and may reach the cap earlier.',
   'After each rewarding quest, target the next actual encounter level plus 3, advance at least one level when equal, reach level 59 before the last boss and level 60 after it.',
   'Two-fight adventures split a quest reward into multiples of three; HP carries between fights and unit levels change only on normal return. No mid-adventure XP level-up is assumed.',
   'Enemy HP, power, level, defense, encounter order, prerequisites and Gang gates are unchanged. No repeat quest, extra normal battle, random drop, healing item or automatic reincarnation is budgeted.',
   'Main commander events remain 4200 XP/level 43 and the existing four residents add 1800, total 6000/level 61, independently of this unit XP policy.'
  ]};
 return {policy,budget};
}

/** Public-state strategy: enter each living sample member, then use observed reactions and own affordable skills. */
export function chooseStoryGrowthAction(view:any,calculation:BattlePolicy['calculation']):BattleAction{
 const state=view.state,self=state.fighters[0],enemy=state.fighters[1],reserves=state.reserves??[],entered=new Set((state.participation??[]).map((p:any)=>p.ownedId));
 const alive=reserves.filter((f:any)=>f.hp>0);
 if(self.hp<=0){const to=alive.find((f:any)=>!entered.has(f.ownedId))??alive.sort((a:any,b:any)=>b.hp-a.hp)[0];if(!to)throw Error('NO_LIVING_RESERVE');return {kind:'switch',targetOwnedId:to.ownedId};}
 const unentered=alive.find((f:any)=>!entered.has(f.ownedId));if(unentered)return {kind:'switch',targetOwnedId:unentered.ownedId};
 const known=state.opponentIntel?.defenses??{},durability=state.opponentIntel?.stats?.durability??0;
 const score=(skill:any,unit=self)=>{const reaction=known[skill.element],defense=reaction&&reaction!=='unknown'?reaction:'normal';const hit=fiveStatAttack({power:skill.power,level:unit.level,durability,suitability:unit.suitability[skill.element],defense,attackerHp:unit.hp,defenderHp:enemy.hp},calculation,(base,m)=>base*m);return hit.reflected?-hit.damage:hit.damage;};
 // Amortize the observable MP cost over focus turns instead of always buying the largest single hit.
 const efficiency=(skill:any,unit=self)=>score(skill,unit)/(1+skill.mpCost/Math.max(1,Math.floor(unit.stats.mp/4)));
 const skills=[self.attack,...self.skills],available=skills.filter((s:any)=>s.mpCost<=self.mp).sort((a:any,b:any)=>efficiency(b)-efficiency(a)||a.mpCost-b.mpCost||a.id.localeCompare(b.id));
 const best=available[0],afterFocus=focusMp(self.mp,self.stats.mp).mp;
 if(!best||score(best)<=0){const useful=alive.find((f:any)=>[f.attack,...f.skills].some((s:any)=>s.mpCost<=f.mp&&score(s,f)>0));if(useful)return {kind:'switch',targetOwnedId:useful.ownedId};}
 const candidates=alive.map((unit:any)=>({unit,best:[unit.attack,...unit.skills].filter((s:any)=>s.mpCost<=unit.mp).sort((a:any,b:any)=>efficiency(b,unit)-efficiency(a,unit))[0]})).filter((x:any)=>x.best);
 const alternative=candidates.sort((a:any,b:any)=>efficiency(b.best,b.unit)-efficiency(a.best,a.unit))[0];
 const horizon=best&&score(best)>0?Math.ceil(enemy.hp/score(best)):100;
 // A switch costs a real turn. Require a sustained public damage advantage, with hysteresis against swapping every cast.
 if(alternative&&best&&horizon>=3&&efficiency(alternative.best,alternative.unit)>Math.max(1,efficiency(best))*1.5&&
  efficiency(alternative.best,alternative.unit)*(horizon-1)>efficiency(best)*horizon)return {kind:'switch',targetOwnedId:alternative.unit.ownedId};
 const strongest=available.slice().sort((a:any,b:any)=>score(b)-score(a)||a.mpCost-b.mpCost)[0];
 if(strongest&&score(strongest)>=enemy.hp)return strongest===self.attack?{kind:'attack'}:{kind:'skill',skillId:strongest.id};
 const observedEnemyHits=(state.events??[]).flatMap((r:any)=>r.events??[]).filter((e:any)=>e.actor==='enemy'&&e.kind==='skill'&&e.target==='defender'&&e.damage>=0&&e.presentation?.before?.[0]?.speciesId===self.speciesId);
 const pastHits=observedEnemyHits.map((e:any)=>e.damage);
 const observedHit=pastHits.length?Math.max(...pastHits):0,remainingOwnActions=observedHit?Math.max(1,Math.ceil(self.hp/observedHit)):100;
 const enemyAffordable=[enemy.attack,...enemy.skills].filter((s:any)=>s.mpCost<=enemy.mp);
 const incoming=enemyAffordable.map((s:any)=>observedEnemyHits.filter((e:any)=>e.skillId===s.id&&e.presentation.before[0].hp>e.damage).map((e:any)=>e.damage));
 const safeKnownFocus=state.opponentIntel?.statsKnown&&self.stats.speed>state.opponentIntel.stats.speed&&incoming.length>0&&incoming.every((hits:any[])=>hits.length>0)&&self.hp>Math.max(...incoming.flat());
 // No tie/seed prediction: only strictly faster self, proved affordable incoming damage, and an observed-element kill.
 if(safeKnownFocus&&skills.some((s:any)=>s.mpCost>self.mp&&s.mpCost<=afterFocus&&known[s.element]&&known[s.element]!=='unknown'&&score(s)>=enemy.hp))return {kind:'focus'};
 // Spend MP before an observed lethal window instead of dying with a large unused pool.
 if(strongest&&score(strongest)>0&&(remainingOwnActions<=2||self.mp>=strongest.mpCost*remainingOwnActions))return strongest===self.attack?{kind:'attack'}:{kind:'skill',skillId:strongest.id};
 if(skills.some((s:any)=>s.mpCost>self.mp&&s.mpCost<=afterFocus&&score(s)>2*(best?score(best):0)))return {kind:'focus'};
 if(!best)return {kind:'focus'};
 return best===self.attack?{kind:'attack'}:{kind:'skill',skillId:best.id};
}

/** Exact RosterBattle.action transitions over a small in-memory repository. No PrismaClient/DB/network. */
async function actionFight(catalog:RosterCatalog,policy:BattlePolicy,encounter:StoryEncounter,party:Fighter[],seed:string,observations:Map<string,any>){
 const id='simulation:'+encounter.id,owner='isolated-simulation',enemy=encounter.villainProfileId?storyVillainFighter(catalog,encounter.villainProfileId,encounter.level,'enemy'):fighter(catalog,encounter.speciesId,encounter.level,'enemy');
 enemy.stats.hp=Math.round(enemy.stats.hp*(encounter.hpMultiplier??1));enemy.hp=enemy.stats.hp;
 for(const sk of [enemy.attack,...enemy.skills])sk.power=Math.round(sk.power*(encounter.powerMultiplier??1));
 const active=party.find(f=>f.hp>0);if(!active)return {outcome:'loss',reason:'NO_LIVING_ADVENTURE_START',trace:[],party};
 const fighters:[Fighter,Fighter]=[{...active,id:'self'},enemy],reserves=party.filter(f=>f.ownedId!==active.ownedId);
 const state={version:'five-battle-v1',rulesVersion:'party-actions-v2',mode:encounter.mode,turn:0,fighters,reserves,inventory:[],outcome:null,events:[],participation:[{ownedId:active.ownedId,kind:'start',turn:0}],
  decision:decideTurnOrder(id,0,fighters.map(f=>({id:f.id,baseSpeed:f.stats.speed,modifiers:[]})) as any,seed)};
 let row:any={id,playerId:owner,ownedId:active.ownedId,seed,revision:0,status:'ACTIVE',state:JSON.stringify(state),settlement:null,snapshot:JSON.stringify({policy,encounter,items:{}})};
 const receipts=new Map<string,any>();
 const tx:any={appMeta:{upsert:async()=>({key:'realtime-pvp-write-lock',value:'1'})},roster60BattleReceipt:{findUnique:async({where}:any)=>receipts.get(where.playerId_requestId.requestId)??null,create:async({data}:any)=>{receipts.set(data.requestId,structuredClone(data));return data;}},
  roster60Battle:{findFirst:async({where}:any)=>where.id===id&&where.playerId===owner?structuredClone(row):null,updateMany:async({where,data}:any)=>{if(where.id!==id||where.revision!==row.revision||where.status!==row.status)return {count:0};row={...row,...data,revision:row.revision+(data.revision?.increment??0)};return {count:1};}},playerItem:{findMany:async()=>[]},
  roster60ObservedDefense:{findMany:async({where}:any)=>[...observations.values()].filter(r=>r.playerId===where.playerId&&where.speciesId.in.includes(r.speciesId)),upsert:async({create}:any)=>{observations.set([create.playerId,create.speciesId,create.profileKey,create.element].join(':'),structuredClone(create));return create;}}};
 const observedDefense=await captureDefenseSnapshot(tx,catalog,{self:{playerId:owner,targets:[enemy]}});row.snapshot=JSON.stringify({...JSON.parse(row.snapshot),observedDefense});
 const db:any={...tx,$transaction:async(run:any)=>run(tx)},service=new RosterBattle(db,()=>catalog,()=>policy),trace:any[]=[];
 let view:any=await service.read(owner,id);
 for(let n=0;view.status==='ACTIVE'&&n<policy.maxRounds*4;n++){
  const action=chooseStoryGrowthAction(view,policy.calculation);trace.push({revision:view.revision,turn:view.state.turn,actor:view.state.fighters[0].ownedId,action});
  view=await service.action(owner,{requestId:'action-'+n,battleId:id,revision:view.revision,action});
 }
 const final=JSON.parse(row.state);return {outcome:final.outcome,turns:final.turn,trace,party:[final.fighters[0],...final.reserves],entered:[...new Set(final.participation.map((p:any)=>p.ownedId))] as string[],seed,events:final.events};
}

export async function simulateStoryCampaign(speciesIds:string[],inputs=loadStoryGrowthInputs(),options:{returnAfterEachWin?:boolean}={}){
 const {catalog,content,battlePolicy,unitPolicy}=inputs,order=storyGrowthOrder(content),owned=speciesIds.map(id=>({ownedId:'sample-'+id,speciesId:id,totalXp:0})),completed=new Set<string>();
 const rows:any[]=[],observations=new Map<string,any>(),returnPolicy=options.returnAfterEachWin?'native-return-after-each-victory':'continuous-quest-run';let gang=0,adventureReturns=0;
 for(const quest of order){
  const gate=quest.darkGangLifetimeRequired??content.policy.mainLifetimeGates[quest.id]??0;
  if(quest.unlockAfter.some(id=>!completed.has(id))||gang<gate)return {status:'BLOCKED',reason:'SERVER_PREREQUISITE_OR_GANG',questId:quest.id,rows};
  let party:Fighter[]=owned.map(u=>({...fighter(catalog,u.speciesId,growthAt(u.totalXp,catalog.characters.find(c=>c.id===u.speciesId)!.growth).level,'self'),ownedId:u.ownedId}));
  const pending=new Map<string,number>();
  for(const encounterId of quest.objective.encounterIds){
   const encounter=content.encounters.find(e=>e.id===encounterId)!,before=owned.map(u=>({id:u.speciesId,xp:u.totalXp,level:growthAt(u.totalXp,catalog.characters.find(c=>c.id===u.speciesId)!.growth).level}));
   const result=await actionFight(catalog,battlePolicy,encounter,party,'story-growth-v1:'+speciesIds.join(',')+':'+encounterId,observations);
   rows.push({questId:quest.id,encounterId,enemyLevel:encounter.level,mode:encounter.mode,before,...result});
   if(result.outcome!=='win')return {status:'BLOCKED',reason:'ACTUAL_ACTION_'+result.outcome,questId:quest.id,encounterId,rows,finalUnits:before,adventureReturns,returnPolicy};
   if(encounter.kind==='black')gang+=content.policy.darkGangPerWin;
   const entered=[...(result.entered??[])].sort(),base=unitPolicy.rewards[encounterId].winXp;
   entered.forEach((id,i)=>pending.set(id,(pending.get(id)??0)+Math.floor(base/entered.length)+(i<base%entered.length?1:0)));
   party=result.party.map((f:Fighter)=>({...f,mp:f.stats.mp}));
   if(encounter.mode==='story')for(const u of owned){const d=catalog.characters.find(c=>c.id===u.speciesId)!;u.totalXp=Math.min(d.growth[59].totalXp,u.totalXp+(pending.get(u.ownedId)??0));pending.delete(u.ownedId);}
   if(encounter.mode==='adventure'&&options.returnAfterEachWin){
    adventureReturns++;for(const u of owned){const d=catalog.characters.find(c=>c.id===u.speciesId)!;u.totalXp=Math.min(d.growth[59].totalXp,u.totalXp+(pending.get(u.ownedId)??0));pending.delete(u.ownedId);}
    // NativeStoryBattle returns after each settled win; re-departure creates a fresh run with authoritative post-return levels/HP.
    party=owned.map(u=>({...fighter(catalog,u.speciesId,growthAt(u.totalXp,catalog.characters.find(c=>c.id===u.speciesId)!.growth).level,'self'),ownedId:u.ownedId}));
   }
  }
  if(!options.returnAfterEachWin&&quest.objective.encounterIds.some(id=>content.encounters.find(e=>e.id===id)!.mode==='adventure')){
   adventureReturns++;for(const u of owned){const d=catalog.characters.find(c=>c.id===u.speciesId)!;u.totalXp=Math.min(d.growth[59].totalXp,u.totalXp+(pending.get(u.ownedId)??0));}
  }
  completed.add(quest.id);
 }
 return {status:'COMPLETED',quests:completed.size,encounters:rows.length,adventureReturns,returnPolicy,gang,rows,finalUnits:owned.map(u=>({...u,level:growthAt(u.totalXp,catalog.characters.find(c=>c.id===u.speciesId)!.growth).level})),
  scope:'Real RosterBattle.action transitions over an in-memory repository; this is not database campaign integration or a production balance approval.'};
}

async function main(){
 const read=(name:string)=>JSON.parse(readFileSync(resolve(root,name),'utf8')),catalog=read('data/test-profiles/five-stat-device-test-v1/catalog.json'),content=read('data/story/story-mode-v1.json');
 if(process.argv.includes('--write-proposal')){const proposal=proposeStoryUnitXpPolicy(catalog,content);writeFileSync(resolve(root,'data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json'),JSON.stringify(proposal.policy,null,2)+'\n');console.log(JSON.stringify({proposal:true,budget:proposal.budget},null,2));return;}
 const inputs=loadStoryGrowthInputs(),runs=[],teams=process.argv.find(a=>a.startsWith('--teams='))?.slice('--teams='.length).split(';').map(t=>t.split(','))??[['u01','u02'],['u01','u02','u03']];for(const ids of teams)runs.push(await simulateStoryCampaign(ids,inputs,{returnAfterEachWin:process.argv.includes('--return-each-win')}));
 const output={testValues:true,productionBalanceApproved:false,realDatabaseUsed:false,sourceCatalog:inputs.catalog.version,contentVersion:inputs.content.version,policyVersion:inputs.unitPolicy.version,
  budget:proposeStoryUnitXpPolicy(inputs.catalog,inputs.content).budget,runs};
 const target=process.argv.find(a=>a.startsWith('--output='))?.slice('--output='.length);if(target)writeFileSync(resolve(target),JSON.stringify(output,null,2)+'\n');
 console.log(JSON.stringify(output.runs.map(r=>({status:r.status,reason:(r as any).reason,questId:(r as any).questId,encounterId:(r as any).encounterId,encounters:r.rows.length,finalUnits:r.finalUnits})),null,2));
 if(runs.some(r=>r.status!=='COMPLETED'))process.exitCode=1;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url))await main();
