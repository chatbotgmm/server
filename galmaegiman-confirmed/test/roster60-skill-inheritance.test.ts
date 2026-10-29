import {readFileSync} from 'node:fs';
import {describe,expect,it} from 'vitest';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import {skillElements,type SkillElement} from '../src/battle/roster60-rules.js';
import {fighter} from '../src/roster60-battle.js';
import {fiveRebirthPolicy} from '../src/battle/roster60-five-stats.js';
import {equippedSkills,inheritancePreview,readSkillInheritance,skillInheritanceVersion,type DefinedSkill,type StoredSkillInheritance} from '../src/roster60-skill-inheritance.js';

// Deliberately small arithmetic fixtures; no product art, prices or balance are replaced.
const move=(id:string,learnLevel=1,element:SkillElement='FIRE'):DefinedSkill=>({id,name:id,element,mpCost:4,power:24,learnLevel});
function unit(id:string,skills:DefinedSkill[]){
 const combatGrowth=Array.from({length:99},(_,i)=>({hp:100+i,mp:50+i,statusResistance:20+i,durability:10+i,speed:10+i}));
 return {id,name:id,rarity:'COMMON',stats:combatGrowth[0],levelOneStats:combatGrowth[0],combatGrowth,
  attack:move('ordinary-attack',1,'NORMAL'),skills,
  suitability:Object.fromEntries(skillElements.map(key=>[key,0])) as any,
  defense:Object.fromEntries(skillElements.map(key=>[key,'normal'])) as any,
  growth:combatGrowth.map((stats,index)=>({totalXp:index*100,maxMp:stats.mp}))};
}
function catalog(...characters:ReturnType<typeof unit>[]):RosterCatalog{
 return {approved:true,version:'inheritance-unit-only',statSchema:'gmm-five-stats-v1',rebirthPolicy:fiveRebirthPolicy,characters};
}
function stored(partial:Partial<StoredSkillInheritance>={}):StoredSkillInheritance{
 return {version:skillInheritanceVersion,replaceSlot:0,skillId:'high',sourceSpeciesId:'u02',sourceSlot:0,requiredLevel:40,catalogVersion:'inheritance-unit-only',...partial};
}

describe('owned skill inheritance projection',()=>{
 it('offers only actually unlocked material skills and never the separate ordinary attack',()=>{
  const c=catalog(unit('u02',[move('low'),move('high',40)]),unit('u09',[move('target')]));
  const before=structuredClone(c),materials=[{id:'owned-source',speciesId:'u02',level:39}];
  const locked=inheritancePreview(c,'u09',1,materials);
  expect(locked.view.candidates.map(value=>value.skillId)).toEqual(['low']);
  expect(locked.view.status).toBe('selection-required');expect(locked.stored).toBeNull();
  expect(()=>inheritancePreview(c,'u09',1,materials,{skillId:'ordinary-attack',replaceSlot:0})).toThrow('INVALID_INHERITANCE_SKILL');
  const unlocked=inheritancePreview(c,'u09',1,[{...materials[0],level:40}]);
  expect(unlocked.view.candidates.map(value=>value.skillId)).toEqual(['low','high']);
  expect(unlocked.view.candidates.some(value=>value.skillId==='ordinary-attack')).toBe(false);
  expect(c).toEqual(before);
 });

 it('deduplicates by ID using a real minimum-level origin and stable owned-ID ties',()=>{
  const c=catalog(unit('u02',[move('shared',40)]),unit('u03',[move('shared',5)]),unit('u09',[move('target')]));
  const materials=[{id:'z-low',speciesId:'u03',level:50},{id:'a-high',speciesId:'u02',level:50},{id:'a-low',speciesId:'u03',level:50}];
  for(const order of [materials,[...materials].reverse(),[materials[1],materials[2],materials[0]]]){
   const candidates=inheritancePreview(c,'u09',1,order).view.candidates;
   expect(candidates).toHaveLength(1);
   expect(candidates[0]).toMatchObject({skillId:'shared',requiredLevel:5,source:{ownedId:'a-low',speciesId:'u03',sourceSpeciesId:'u03',sourceSlot:0,materialSkillSlot:0}});
  }
 });

 it('cannot open a level-40 move through a level-one target slot and keeps six skills plus an attack',()=>{
  const target=unit('u09',Array.from({length:6},(_,i)=>move('target-'+i)));
  const c=catalog(unit('u02',[move('high',40)]),target),before=structuredClone(c);
  const preview=inheritancePreview(c,'u09',39,[{id:'source',speciesId:'u02',level:40}],{skillId:'high',replaceSlot:0});
  expect(preview.view.selected).toMatchObject({requiredLevel:40,replaceSlot:0,unlockedAtResultLevel:false,replacedSkill:{requiredLevel:1}});
  expect(preview.stored).toMatchObject({requiredLevel:40,sourceSpeciesId:'u02',sourceSlot:0});
  const raw=JSON.stringify(preview.stored),locked=fighter(c,'u09',39,'self',0,raw),open=fighter(c,'u09',40,'self',0,raw);
  expect(locked.skills.some(value=>value.id==='high')).toBe(false);
  expect(open.skills.map(value=>value.id)).toEqual(['high','target-1','target-2','target-3','target-4','target-5']);
  expect(open.skills).toHaveLength(6);expect(open.attack.id).toBe('ordinary-attack');
  expect(fighter(c,'u09',1,'reborn-self',1,raw).skills.some(value=>value.id==='high')).toBe(false);
  expect(c).toEqual(before);
 });

 it('allows an already-present ID only in its matching slot without duplicating it or hiding an unlock change',()=>{
  const c=catalog(unit('u02',[move('shared',40)]),unit('u09',[move('target'),move('shared',1)]));
  const materials=[{id:'source',speciesId:'u02',level:40}],candidate=inheritancePreview(c,'u09',5,materials).view.candidates[0];
  expect(candidate.allowedReplaceSlots).toEqual([1]);
  expect(()=>inheritancePreview(c,'u09',5,materials,{skillId:'shared',replaceSlot:0})).toThrow('DUPLICATE_INHERITANCE_SKILL');
  const preview=inheritancePreview(c,'u09',5,materials,{skillId:'shared',replaceSlot:1});
  expect(preview.view.selected).toMatchObject({kindUnchanged:true,requiredLevelChanged:true,requiredLevel:40,unlockedAtResultLevel:false});
  const effective=equippedSkills(c,'u09',JSON.stringify(preview.stored));
  expect(effective.map(value=>value.skill.id)).toEqual(['target','shared']);
  expect(effective[1].skill.learnLevel).toBe(40);
 });

 it('reads combat values from the current source while keeping the acquired unlock floor',()=>{
  const source=unit('u02',[move('high',40)]),c=catalog(source,unit('u09',[move('target')])),raw=JSON.stringify(stored());
  source.skills[0].mpCost=9;source.skills[0].power=55;source.skills[0].learnLevel=50;
  expect(equippedSkills(c,'u09',raw)[0].skill).toMatchObject({mpCost:9,power:55,learnLevel:50});
  source.skills[0].learnLevel=5;
  expect(equippedSkills(c,'u09',raw)[0].skill.learnLevel).toBe(40);
 });

 it('carries the original source and its lock through a second craft instead of the intermediate low slot',()=>{
  const c=catalog(unit('u02',[move('high',40)]),unit('u09',[move('intermediate',1)]),unit('u10',[move('next-target',1)]));
  const previous=JSON.stringify(stored());
  const preview=inheritancePreview(c,'u10',3,[{id:'intermediate-owned',speciesId:'u09',level:40,skillInheritanceJson:previous}],{skillId:'high',replaceSlot:0});
  expect(preview.view.selected).toMatchObject({requiredLevel:40,unlockedAtResultLevel:false,source:{ownedId:'intermediate-owned',speciesId:'u09',sourceSpeciesId:'u02',sourceSlot:0}});
  expect(preview.stored).toMatchObject({skillId:'high',sourceSpeciesId:'u02',requiredLevel:40});
  expect(equippedSkills(c,'u10',JSON.stringify(preview.stored))[0].skill.learnLevel).toBe(40);
 });

 it('accepts null legacy state but rejects malformed metadata, invalid slots, injected values and invalid levels',()=>{
  expect(readSkillInheritance()).toBeNull();expect(readSkillInheritance(null)).toBeNull();expect(readSkillInheritance(JSON.stringify(stored()))).toEqual(stored());
  const invalid:unknown[]=['not-json','null','[]','{}',JSON.stringify({...stored(),extra:'injected'}),
   ...[{version:'future-version'},{replaceSlot:-1},{replaceSlot:6},{replaceSlot:.5},{sourceSlot:-1},{sourceSlot:6},
    {requiredLevel:0},{requiredLevel:100},{requiredLevel:1.5},{requiredLevel:'40'},{catalogVersion:''},{skillId:''}].map(change=>JSON.stringify({...stored(),...change}))];
  for(const value of invalid)expect(()=>readSkillInheritance(value as string),'invalid '+value).toThrow('INHERITANCE_STATE_INVALID');
 });

 it('rejects missing or changed source data and a stored override outside the actual target slots',()=>{
  const c=catalog(unit('u02',[move('high',40)]),unit('u09',[move('target')]));
  expect(()=>equippedSkills(c,'u09',JSON.stringify(stored({sourceSpeciesId:'u60'})))).toThrow('INHERITANCE_CATALOG_INVALID');
  expect(()=>equippedSkills(c,'u09',JSON.stringify(stored({skillId:'not-high'})))).toThrow('INHERITANCE_SOURCE_CHANGED');
  expect(()=>equippedSkills(c,'u09',JSON.stringify(stored({sourceSlot:5})))).toThrow('INHERITANCE_SOURCE_CHANGED');
  expect(()=>equippedSkills(c,'u09',JSON.stringify(stored({replaceSlot:5})))).toThrow('INHERITANCE_STATE_INVALID');
 });

 it('fails closed if persisted metadata would create duplicate equipped IDs',()=>{
  const c=catalog(unit('u02',[move('high',40)]),unit('u09',[move('target'),move('high',1)]));
  expect(()=>equippedSkills(c,'u09',JSON.stringify(stored({replaceSlot:0})))).toThrow('INHERITANCE_STATE_INVALID');
  expect(equippedSkills(c,'u09',JSON.stringify(stored({replaceSlot:1}))).map(value=>value.skill.id)).toEqual(['target','high']);
 });

 it('rejects a shared ID with conflicting authoritative combat values rather than choosing a hidden variant',()=>{
  const first=unit('u02',[move('shared',1)]),second=unit('u03',[move('shared',5)]),c=catalog(first,second,unit('u09',[move('target')]));
  second.skills[0].power++;
  expect(()=>inheritancePreview(c,'u09',1,[{id:'a',speciesId:'u02',level:10},{id:'b',speciesId:'u03',level:10}])).toThrow('INHERITANCE_SKILL_AMBIGUOUS');
  expect(inheritancePreview(c,'u09',1,[{id:'a',speciesId:'u02',level:10},{id:'b',speciesId:'u03',level:4}]).view.candidates).toHaveLength(1);
 });

 it('does not invent a replacement when there is no equipped target slot or no actually unlocked candidate',()=>{
  const c=catalog(unit('u02',[move('high',40)]),unit('u09',[]),unit('u10',[move('target')]));
  expect(()=>inheritancePreview(c,'u09',1,[{id:'a',speciesId:'u02',level:40}])).toThrow('INHERITANCE_POLICY_UNAVAILABLE');
  expect(()=>inheritancePreview(c,'u10',1,[{id:'a',speciesId:'u02',level:39}])).toThrow('INHERITANCE_POLICY_UNAVAILABLE');
  expect(()=>inheritancePreview(c,'u10',1,[{id:'a',speciesId:'u02',level:40}],{skillId:'high',replaceSlot:6})).toThrow('INVALID_INHERITANCE_SLOT');
 });

 it('ignores client-supplied combat and unlock numbers when deriving a valid choice',()=>{
  const c=catalog(unit('u02',[move('high',40)]),unit('u09',[move('target',1)]));
  const preview=inheritancePreview(c,'u09',1,[{id:'a',speciesId:'u02',level:40}],{skillId:'high',replaceSlot:0,requiredLevel:1,mpCost:0,power:9999} as any);
  expect(preview.view.selected).toMatchObject({requiredLevel:40,mpCost:4,power:24,unlockedAtResultLevel:false});
  expect(preview.stored).not.toHaveProperty('mpCost');expect(preview.stored).not.toHaveProperty('power');
 });

 it('keeps the real u37 recipe usable when every currently learned material move already exists in the result',()=>{
  const real=JSON.parse(readFileSync(new URL('../data/test-profiles/five-stat-device-test-v1/catalog.json',import.meta.url),'utf8')) as RosterCatalog;
  const recipe=real.recipes!.find(value=>value.resultId==='u37')!;
  const materials=recipe.ingredients.flatMap((ingredient,i)=>Array.from({length:ingredient.count},(_,j)=>({id:'real-source-'+i+'-'+j,speciesId:ingredient.unitId,level:ingredient.minLevel})));
  const before=structuredClone(real),preview=inheritancePreview(real,'u37',20,materials);
  expect(preview.view.candidates.length).toBeGreaterThan(0);
  const defaultIds=real.characters.find(value=>value.id==='u37')!.skills.map(value=>value.id);
  for(const candidate of preview.view.candidates){
   expect(defaultIds).toContain(candidate.skillId);expect(candidate.allowedReplaceSlots).toHaveLength(1);
   const selected=inheritancePreview(real,'u37',20,materials,{skillId:candidate.skillId,replaceSlot:candidate.allowedReplaceSlots[0]});
   expect(selected.view.selected?.kindUnchanged).toBe(true);
   expect(equippedSkills(real,'u37',JSON.stringify(selected.stored)).map(value=>value.skill.id)).toEqual(defaultIds);
  }
  expect(real).toEqual(before);
 });
});
