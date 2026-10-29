import type {RosterCatalog} from './roster60-onboarding.js';
import {RosterError} from './roster60-onboarding.js';
import {skillElements,validateRosterDefinition,type SkillDefinition} from './battle/roster60-rules.js';

export const skillInheritanceVersion='roster60-skill-inheritance-v1';
export type DefinedSkill=SkillDefinition&{power:number;learnLevel?:number};
export type InheritanceChoice={skillId:string;replaceSlot:number};
export type StoredSkillInheritance={version:typeof skillInheritanceVersion;replaceSlot:number;skillId:string;sourceSpeciesId:string;sourceSlot:number;requiredLevel:number;catalogVersion:string};
export type SkillOrigin={sourceSpeciesId:string;sourceSlot:number;requiredLevel:number};
export type EquippedSkill={slot:number;skill:DefinedSkill;origin:SkillOrigin};
export type InheritanceMaterial={id:string;speciesId:string;level:number;skillInheritanceJson?:string|null};
export type InheritanceCandidate={skillId:string;name:string;element:DefinedSkill['element'];mpCost:number;power:number;requiredLevel:number;source:{ownedId:string;speciesId:string;name:string;level:number;sourceSpeciesId:string;sourceName:string;sourceSlot:number;materialSkillSlot:number};allowedReplaceSlots:number[]};
const fail=(code:string,status=503):never=>{throw new RosterError(status,code);};
const level=(n:unknown)=>Number.isSafeInteger(n)&&Number(n)>=1&&Number(n)<=99;
const slot=(n:unknown)=>Number.isSafeInteger(n)&&Number(n)>=0&&Number(n)<6;

function definition(c:RosterCatalog,id:string){
 const matches=c.characters.filter(value=>value.id===id);if(matches.length!==1)return fail('INHERITANCE_CATALOG_INVALID');
 try{validateRosterDefinition(matches[0]);}catch{return fail('INHERITANCE_CATALOG_INVALID');}
 return matches[0];
}
function skill(value:SkillDefinition):DefinedSkill{
 const result=value as DefinedSkill;
 if(!result.id||!result.name||!skillElements.includes(result.element)||!Number.isSafeInteger(result.mpCost)||result.mpCost<0||!Number.isFinite(result.power)||result.power<0||!level(result.learnLevel??1)||'effects' in result||'priority' in result)return fail('INHERITANCE_SKILL_POLICY_INVALID');
 return {...result,learnLevel:result.learnLevel??1};
}
export function readSkillInheritance(raw?:string|null):StoredSkillInheritance|null{
 if(raw==null)return null;
 let data:any;try{data=JSON.parse(raw);}catch{return fail('INHERITANCE_STATE_INVALID');}
 const keys=['version','replaceSlot','skillId','sourceSpeciesId','sourceSlot','requiredLevel','catalogVersion'];
 if(!data||Array.isArray(data)||Object.keys(data).length!==keys.length||Object.keys(data).some(key=>!keys.includes(key))||data.version!==skillInheritanceVersion||!slot(data.replaceSlot)||!slot(data.sourceSlot)||typeof data.skillId!=='string'||!data.skillId||typeof data.sourceSpeciesId!=='string'||!level(data.requiredLevel)||typeof data.catalogVersion!=='string'||!data.catalogVersion)return fail('INHERITANCE_STATE_INVALID');
 return data;
}

/** An owned override never mutates the species definition or its separate ordinary attack. */
export function equippedSkills(c:RosterCatalog,speciesId:string,raw?:string|null):EquippedSkill[]{
 const unit=definition(c,speciesId);
 const entries=unit.skills.map((value,index)=>{const actual=skill(value);return {slot:index,skill:actual,origin:{sourceSpeciesId:speciesId,sourceSlot:index,requiredLevel:actual.learnLevel!}};});
 const inherited=readSkillInheritance(raw);if(!inherited)return entries;
 if(inherited.replaceSlot>=entries.length)return fail('INHERITANCE_STATE_INVALID');
 const origin=definition(c,inherited.sourceSpeciesId),source=origin.skills[inherited.sourceSlot];
 if(!source||source.id!==inherited.skillId)return fail('INHERITANCE_SOURCE_CHANGED');
 const actual=skill(source),requiredLevel=Math.max(inherited.requiredLevel,actual.learnLevel!);
 // Acquired source requirements are a floor. A lower target slot never opens a high-level move.
 entries[inherited.replaceSlot]={slot:inherited.replaceSlot,skill:{...actual,learnLevel:requiredLevel},origin:{sourceSpeciesId:inherited.sourceSpeciesId,sourceSlot:inherited.sourceSlot,requiredLevel}};
 if(new Set(entries.map(value=>value.skill.id)).size!==entries.length)return fail('INHERITANCE_STATE_INVALID');
 return entries;
}

function display(value:DefinedSkill){return {skillId:value.id,name:value.name,element:value.element,mpCost:value.mpCost,power:value.power,requiredLevel:value.learnLevel??1};}
/** Only actually unlocked material skills enter the pool; ordinary attacks do not. */
export function inheritancePreview(c:RosterCatalog,resultId:string,resultLevel:number,materials:InheritanceMaterial[],choice?:InheritanceChoice){
 const target=equippedSkills(c,resultId),resultSkillSlots=target.map(entry=>({slot:entry.slot,...display(entry.skill),unlockedAtResultLevel:entry.origin.requiredLevel<=resultLevel}));
 const sources=materials.flatMap(material=>equippedSkills(c,material.speciesId,material.skillInheritanceJson).filter(entry=>entry.origin.requiredLevel<=material.level).map(entry=>({material,entry})));
 // Stable min-source selection is a verified real origin, never the target slot's unlock level.
 sources.sort((a,b)=>a.entry.origin.requiredLevel-b.entry.origin.requiredLevel||a.material.id.localeCompare(b.material.id)||a.entry.slot-b.entry.slot);
 const candidates:InheritanceCandidate[]=[],seen=new Map<string,DefinedSkill>();
 for(const {material,entry} of sources){
  const prior=seen.get(entry.skill.id);
  if(prior){if(JSON.stringify(display({...prior,learnLevel:1}))!==JSON.stringify(display({...entry.skill,learnLevel:1})))return fail('INHERITANCE_SKILL_AMBIGUOUS');continue;}
  seen.set(entry.skill.id,entry.skill);
  const matching=target.filter(value=>value.skill.id===entry.skill.id).map(value=>value.slot);
  candidates.push({...display(entry.skill),source:{ownedId:material.id,speciesId:material.speciesId,name:definition(c,material.speciesId).name,level:material.level,sourceSpeciesId:entry.origin.sourceSpeciesId,sourceName:definition(c,entry.origin.sourceSpeciesId).name,sourceSlot:entry.origin.sourceSlot,materialSkillSlot:entry.slot},allowedReplaceSlots:matching.length?matching:target.map(value=>value.slot)});
 }
 if(!candidates.length||!target.length)return fail('INHERITANCE_POLICY_UNAVAILABLE',409);
 let selected=null,stored:StoredSkillInheritance|null=null;
 if(choice){
  if(typeof choice.skillId!=='string'||!slot(choice.replaceSlot))return fail('INVALID_INHERITANCE_SLOT',400);
  const candidate=candidates.find(value=>value.skillId===choice.skillId);if(!candidate)return fail('INVALID_INHERITANCE_SKILL',409);
  const replacedSkill=resultSkillSlots[choice.replaceSlot];if(!replacedSkill)return fail('INVALID_INHERITANCE_SLOT',409);
  if(!candidate.allowedReplaceSlots.includes(choice.replaceSlot))return fail('DUPLICATE_INHERITANCE_SKILL',409);
  selected={...candidate,replaceSlot:choice.replaceSlot,replacedSkill,unlockedAtResultLevel:candidate.requiredLevel<=resultLevel,kindUnchanged:replacedSkill.skillId===candidate.skillId,requiredLevelChanged:replacedSkill.requiredLevel!==candidate.requiredLevel};
  stored={version:skillInheritanceVersion,replaceSlot:choice.replaceSlot,skillId:candidate.skillId,sourceSpeciesId:candidate.source.sourceSpeciesId,sourceSlot:candidate.source.sourceSlot,requiredLevel:candidate.requiredLevel,catalogVersion:c.version};
  equippedSkills(c,resultId,JSON.stringify(stored));
 }
 return {view:{version:skillInheritanceVersion,status:selected?'selected' as const:'selection-required' as const,candidates,resultSkillSlots,selected},stored};
}
