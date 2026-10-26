import {createHash} from 'node:crypto';
import type {Prisma,PrismaClient} from '@prisma/client';
import type {RosterCatalog} from '../roster60-onboarding.js';
import type {Fighter} from '../roster60-battle.js';
import {skillElements,type SkillElement,type Defense} from './roster60-rules.js';
import {opponentIntel} from './roster60-opponent-intel.js';

export const observedDefenseVersion='observed-defense-v1' as const;
export type KnownDefenses=Partial<Record<SkillElement,Defense>>;
type SpeciesKnowledge=Record<string,KnownDefenses>;
export type DefenseSnapshot={version:typeof observedDefenseVersion;profiles:Record<string,string>;initial:Record<string,SpeciesKnowledge>};
export type DefenseObservation={viewer:string;speciesId:string;element:SkillElement;reaction:Defense;targetKey:string;profileKey?:string;baseConflict?:boolean};
export type DefenseKnowledge={base:SpeciesKnowledge;battle:Record<string,KnownDefenses>;conflicts:Record<string,boolean>};
const reactions=new Set(['weak','normal','resist','immune','reflect']);
const digest=(value:unknown)=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
/** Internal only: no profile key, private fighter identity or complete table leaves the server. */
export function defenseProfileKey(speciesId:string,profile:unknown):string|null {
 if(!profile||typeof profile!=='object'||Object.keys(profile).length!==skillElements.length)return null;
 const values=skillElements.map(element=>(profile as Record<string,unknown>)[element]);
 if(values.some(value=>!reactions.has(String(value))))return null;
 return digest([observedDefenseVersion,speciesId,values]);
}
function targetKey(target:Fighter){return digest(['battle-observed-v1',target.speciesId,target.ownedId??target.id,skillElements.map(element=>target.defense?.[element])]);}
function baseKey(c:RosterCatalog|null,speciesId:string){const unit=c?.approved?c.characters.find(u=>u.id===speciesId):undefined;return unit?defenseProfileKey(speciesId,unit.defense):null;}
export function safeKnowledgeCatalog(read:()=>RosterCatalog|null){try{return read();}catch{return null;}}
export async function captureDefenseSnapshot(tx:Prisma.TransactionClient,c:RosterCatalog,viewers:Record<string,{playerId:string;targets:Fighter[]}>):Promise<DefenseSnapshot>{
 const profiles:Record<string,string>={},initial:DefenseSnapshot['initial']={};
 for(const {targets} of Object.values(viewers))for(const target of targets){const key=baseKey(c,target.speciesId);if(key&&key===defenseProfileKey(target.speciesId,target.defense))profiles[target.speciesId]=key;}
 for(const [viewer,{playerId,targets}] of Object.entries(viewers)){
  initial[viewer]={};const ids=[...new Set(targets.map(t=>t.speciesId).filter(id=>profiles[id]))];
  if(!ids.length)continue;
  const rows=await tx.roster60ObservedDefense.findMany({where:{playerId,speciesId:{in:ids}},select:{speciesId:true,profileKey:true,element:true,reaction:true}});
  for(const row of rows)if(profiles[row.speciesId]===row.profileKey&&skillElements.includes(row.element as SkillElement)&&reactions.has(row.reaction)){
   (initial[viewer][row.speciesId]??={})[row.element as SkillElement]=row.reaction as Defense;
  }
 }
 return {version:observedDefenseVersion,profiles,initial};
}
export function battleKnowledge(snapshot:DefenseSnapshot|undefined,rounds:readonly any[],viewer:string):DefenseKnowledge {
 const knowledge:DefenseKnowledge={base:structuredClone(snapshot?.initial?.[viewer]??{}),battle:{},conflicts:{}};
 for(const round of rounds)for(const event of round.events??[]){const observation=event.defenseObservation as DefenseObservation|undefined;if(observation?.viewer===viewer)addDefenseObservation(knowledge,observation,snapshot);}
 return knowledge;
}
export function addDefenseObservation(knowledge:DefenseKnowledge,observation:DefenseObservation,snapshot:DefenseSnapshot|undefined){
 (knowledge.battle[observation.targetKey]??={})[observation.element]=observation.reaction;
 if(observation.baseConflict)knowledge.conflicts[observation.targetKey]=true;
 if(observation.profileKey&&snapshot?.profiles[observation.speciesId]===observation.profileKey)(knowledge.base[observation.speciesId]??={})[observation.element]=observation.reaction;
}
/** Called only after an executed attack resolves. Miss/cancel/non-hit results are never knowledge. */
export function resolvedDefenseObservation(target:Fighter,element:SkillElement,hit:unknown,viewer:string,snapshot:DefenseSnapshot|undefined,c:RosterCatalog|null,knowledge?:DefenseKnowledge):DefenseObservation|null {
 if(!skillElements.includes(element)||!hit||typeof hit!=='object')return null;
 const result=hit as Record<string,unknown>;
 if(result.miss===true||result.missed===true||result.cancelled===true||result.hit===false||result.executed===false||(result.outcome!==undefined&&result.outcome!=='hit'))return null;
 if(!reactions.has(String(result.defense))||!Number.isFinite(result.damage)||Number(result.damage)<0)return null;
 const reaction=result.defense as Defense;
 if(result.target!==(reaction==='reflect'?'attacker':'defender'))return null;
 const actual=defenseProfileKey(target.speciesId,target.defense),pinned=snapshot?.profiles[target.speciesId];
 const baseConflict=reaction!==target.defense[element];
 const verified=!baseConflict&&!knowledge?.conflicts[targetKey(target)]&&actual&&pinned===actual&&baseKey(c,target.speciesId)===actual;
 return {viewer,speciesId:target.speciesId,element,reaction,targetKey:targetKey(target),...(verified?{profileKey:actual}:{}),...(baseConflict?{baseConflict:true}:{})};
}
export async function persistDefenseObservations(tx:Prisma.TransactionClient,players:Record<string,string>,observations:readonly DefenseObservation[]){
 for(const observation of observations){
  if(!observation.profileKey)continue;
  const playerId=players[observation.viewer];if(!playerId)continue;
  const {speciesId,profileKey,element,reaction}=observation;
  await tx.roster60ObservedDefense.upsert({where:{playerId_speciesId_profileKey_element:{playerId,speciesId,profileKey,element}},create:{playerId,speciesId,profileKey,element,reaction},update:{reaction}});
 }
}
export function observedOpponentIntel(target:Fighter,turn:number,snapshot:DefenseSnapshot|undefined,knowledge:DefenseKnowledge,c:RosterCatalog|null=null){
 const actual=defenseProfileKey(target.speciesId,target.defense),verified=!!actual&&snapshot?.profiles[target.speciesId]===actual&&baseKey(c,target.speciesId)===actual&&!knowledge.conflicts[targetKey(target)];
 const local=knowledge.battle[targetKey(target)]??{};
 const known=verified?{...(knowledge.base[target.speciesId]??{}),...local}:local;
 return opponentIntel(target,turn,known,verified?'base-observed':Object.keys(local).length?'battle-observed':'unverified');
}
export function publicDefenseObservation(observation:DefenseObservation|undefined){return observation?{speciesId:observation.speciesId,element:observation.element,reaction:observation.reaction}:undefined;}
export function defenseKnowledgeProjection(speciesId:string,known:KnownDefenses){
 const defenses=Object.fromEntries(skillElements.map(element=>[element,known[element]??'unknown']));
 return {version:observedDefenseVersion,speciesId,defenses,knownDefenseCount:Object.keys(known).length,totalDefenseCount:skillElements.length};
}
export async function collectionDefenseKnowledge(db:PrismaClient,playerId:string,c:RosterCatalog){
 const rows=await db.roster60ObservedDefense.findMany({where:{playerId},select:{speciesId:true,profileKey:true,element:true,reaction:true}}),known:SpeciesKnowledge={};
 for(const row of rows)if(baseKey(c,row.speciesId)===row.profileKey&&skillElements.includes(row.element as SkillElement)&&reactions.has(row.reaction))(known[row.speciesId]??={})[row.element as SkillElement]=row.reaction as Defense;
 return known;
}
/** Public boundary also covers archived receipts and own/onboarding/adventure DTOs. */
export function publicDefenseSafe<T>(value:T):T {
 if(Array.isArray(value))return value.map(publicDefenseSafe) as T;
 if(!value||typeof value!=='object'||value instanceof Date)return value;
 let object=value as Record<string,unknown>;
 if(object.version==='opponent-intel-v1'){
  const turns=Math.max(0,Math.floor(Number(object.completedTurns)||0));
  object={...object,version:'opponent-intel-v2',defenseKnowledgeScope:'unverified',defenses:Object.fromEntries(skillElements.map(e=>[e,'unknown'])),knownDefenseCount:0,
   statsKnown:turns>=1,stats:turns>=1?object.stats:null};
 }
 return Object.fromEntries(Object.entries(object).filter(([key,entry])=>key!=='defense'||!entry||typeof entry!=='object').map(([key,entry])=>[key,publicDefenseSafe(entry)])) as T;
}
