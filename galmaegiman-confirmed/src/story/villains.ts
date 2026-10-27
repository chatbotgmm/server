import type {RosterCatalog} from '../roster60-onboarding.js';
import {RosterError} from '../roster60-onboarding.js';
import {fighter,type Fighter} from '../roster60-battle.js';
import {skillElements,type SkillElement} from '../battle/roster60-rules.js';
import profiles from '../../data/story/villains-test-v1.json' with {type:'json'};

/** 스토리 전용 시험용 공격 프로필. 첫깃 기본형은 어떠한 전투 카탈로그에도 추가하지 않는다. */
export const storyVillainProfiles=profiles;

type StoryVillainSkill={id:string;name:string;element:SkillElement;power:number;mpCost:number;learnLevel:number};
type StoryVillainDocument={productionBalanceApproved:false;statSchema:'gmm-five-stats-v1';namesAreProposals:true;numericValuesAreTestValues:true;profiles:{id:string;name:string;baselineSpeciesId:string;combatAllowed:boolean;classification:'unique-villain'|'human-villain';skills:StoryVillainSkill[]}[]};
const record=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value);
function invalid():never {throw new RosterError(503,'STORY_VILLAIN_PROFILE_INVALID');}
/** 향후 데이터 편집도 현재 엔진에 없는 효과를 조용히 통과시키지 않는다. */
export function validateStoryVillainProfiles(input:unknown):asserts input is StoryVillainDocument {
 if(!record(input)||input.productionBalanceApproved!==false||input.statSchema!=='gmm-five-stats-v1'||input.namesAreProposals!==true||input.numericValuesAreTestValues!==true||!Array.isArray(input.profiles)||!input.profiles.length)invalid();
 const ids=new Set<string>();
 for(const profile of input.profiles){
  if(!record(profile)||typeof profile.id!=='string'||!/^[a-z_]+$/.test(profile.id)||ids.has(profile.id)||typeof profile.name!=='string'||!profile.name||typeof profile.baselineSpeciesId!=='string'||!/^u(0[1-9]|[1-5][0-9]|60)$/.test(profile.baselineSpeciesId)||typeof profile.combatAllowed!=='boolean'||!['unique-villain','human-villain'].includes(String(profile.classification))||!Array.isArray(profile.skills)||profile.skills.length!==4||Object.keys(profile).some(key=>!['id','name','baselineSpeciesId','combatAllowed','classification','skills'].includes(key)))invalid();
  ids.add(profile.id);const skills=new Set<string>();
  for(const skill of profile.skills){
   if(!record(skill)||typeof skill.id!=='string'||!skill.id||skills.has(skill.id)||typeof skill.name!=='string'||!skill.name||!skillElements.includes(skill.element as SkillElement)||typeof skill.power!=='number'||!Number.isFinite(skill.power)||skill.power<=0||skill.power>10000||!Number.isSafeInteger(skill.mpCost)||Number(skill.mpCost)<0||Number(skill.mpCost)>10000||!Number.isInteger(skill.learnLevel)||Number(skill.learnLevel)<1||Number(skill.learnLevel)>60||Object.keys(skill).some(key=>!['id','name','element','power','mpCost','learnLevel'].includes(key)))invalid();
   skills.add(skill.id);
  }
  if(profile.skills[0].mpCost!==0||profile.skills[0].learnLevel!==1)invalid();
 }
}

/** 실제 전투 경계에서는 승인된 원본 ID로 성장 검증 후, 적 한 개체만 전용 공격으로 교체한다. */
export function storyVillainFighter(baseCatalog:RosterCatalog,profileId:string,level:number,id:string):Fighter {
 validateStoryVillainProfiles(profiles);
 const profile=profiles.profiles.find(p=>p.id===profileId);
 if(!profile||!profile.combatAllowed)throw new RosterError(503,'STORY_VILLAIN_PROFILE_PENDING');
 const enemy=fighter(baseCatalog,profile.baselineSpeciesId,level,id);
 enemy.speciesId='story_'+profile.id;enemy.name=profile.name;
 enemy.attack=structuredClone(profile.skills[0]) as Fighter['attack'];
 enemy.skills=structuredClone(profile.skills.slice(1).filter(s=>s.learnLevel<=level)) as Fighter['skills'];
 return enemy;
}
