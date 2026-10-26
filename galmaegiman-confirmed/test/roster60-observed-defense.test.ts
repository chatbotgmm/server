import {describe,it,expect} from 'vitest';
import {skillElements,type Defense} from '../src/battle/roster60-rules.js';
import type {Fighter} from '../src/roster60-battle.js';
import {defenseProfileKey,resolvedDefenseObservation,addDefenseObservation,battleKnowledge,observedOpponentIntel,publicDefenseSafe,type DefenseSnapshot} from '../src/battle/roster60-observed-defense.js';
const target=()=>({id:'enemy',speciesId:'u02',name:'enemy',level:1,hp:100,mp:10,stats:{hp:100,mp:10,speed:2,durability:0,statusResistance:0},attack:{id:'a',name:'a',element:'NORMAL',mpCost:0,power:1},skills:[],suitability:Object.fromEntries(skillElements.map(e=>[e,0])),defense:Object.fromEntries(skillElements.map(e=>[e,'normal']))}) as unknown as Fighter;
function context(enemy=target()){
 const catalog={approved:true,version:'unit-test',characters:[{id:enemy.speciesId,defense:{...enemy.defense}}]} as any;
 const snapshot:DefenseSnapshot={version:'observed-defense-v1',profiles:{[enemy.speciesId]:defenseProfileKey(enemy.speciesId,enemy.defense)!},initial:{self:{}}};
 return {catalog,snapshot,knowledge:battleKnowledge(snapshot,[],'self')};
}
describe('actual-hit defense knowledge policy',()=>{
 it('never reveals defenses from creation or turn count; turn-one stats remain local',()=>{
  const enemy=target(),c=context(enemy);
  for(const turn of [0,1,8,100]){
   const intel=observedOpponentIntel(enemy,turn,c.snapshot,c.knowledge,c.catalog);
   expect(intel.knownDefenseCount).toBe(0);expect(Object.values(intel.defenses).every(v=>v==='unknown')).toBe(true);
   expect(intel.statsKnown).toBe(turn>=1);expect(intel.stats).toEqual(turn>=1?enemy.stats:null);
  }
 });
 it.each(['weak','normal','resist','immune','reflect'] as Defense[])('observes NORMAL %s including zero damage and reflection against original defender',reaction=>{
  const enemy=target();enemy.defense.NORMAL=reaction;const c=context(enemy);
  const observation=resolvedDefenseObservation(enemy,'NORMAL',{defense:reaction,target:reaction==='reflect'?'attacker':'defender',damage:0},'self',c.snapshot,c.catalog)!;
  expect(observation.speciesId).toBe(enemy.speciesId);expect(observation.profileKey).toBe(c.snapshot.profiles.u02);
  addDefenseObservation(c.knowledge,observation,c.snapshot);
  expect(observedOpponentIntel(enemy,0,c.snapshot,c.knowledge,c.catalog)).toMatchObject({knownDefenseCount:1,defenses:{NORMAL:reaction,FIRE:'unknown'}});
 });
 it('rejects miss, cancel, unexecuted, invalid and inconsistent hit results',()=>{
  const enemy=target(),c=context(enemy),hit={defense:'normal',target:'defender',damage:0};
  for(const override of [{miss:true},{missed:true},{cancelled:true},{hit:false},{executed:false},{outcome:'miss'},{outcome:'cancel'},{defense:'unknown'},{target:'attacker'},{damage:-1},{damage:NaN}]){
   expect(resolvedDefenseObservation(enemy,'NORMAL',{...hit,...override},'self',c.snapshot,c.catalog)).toBeNull();
  }
 });
 it('keeps a hit/table mismatch battle-local and stops mixing base knowledge for that target context',()=>{
  const enemy=target(),c=context(enemy);c.knowledge.base.u02={WATER:'normal'};
  const observation=resolvedDefenseObservation(enemy,'FIRE',{defense:'immune',target:'defender',damage:0},'self',c.snapshot,c.catalog)!;
  expect(observation.profileKey).toBeUndefined();expect(observation.baseConflict).toBe(true);
  addDefenseObservation(c.knowledge,observation,c.snapshot);
  expect(observedOpponentIntel(enemy,1,c.snapshot,c.knowledge,c.catalog)).toMatchObject({defenseKnowledgeScope:'battle-observed',knownDefenseCount:1,defenses:{FIRE:'immune',WATER:'unknown'}});
  expect(resolvedDefenseObservation(enemy,'WATER',{defense:'normal',target:'defender',damage:1},'self',c.snapshot,c.catalog,c.knowledge)?.profileKey).toBeUndefined();
 });
 it('partitions transient knowledge by current profile, species and opponent identity',()=>{
  const enemy=target(),c=context(enemy);enemy.defense.FIRE='reflect';
  const observation=resolvedDefenseObservation(enemy,'FIRE',{defense:'reflect',target:'attacker',damage:0},'self',c.snapshot,c.catalog)!;
  expect(observation.profileKey).toBeUndefined();addDefenseObservation(c.knowledge,observation,c.snapshot);
  expect(observedOpponentIntel(enemy,0,c.snapshot,c.knowledge,c.catalog)).toMatchObject({defenseKnowledgeScope:'battle-observed',knownDefenseCount:1,defenses:{FIRE:'reflect',NORMAL:'unknown'}});
  for(const replacement of [{...enemy,id:'different-enemy'},{...enemy,speciesId:'u03'},{...enemy,defense:{...enemy.defense,FIRE:'normal' as const}}]){
   expect(observedOpponentIntel(replacement,0,c.snapshot,c.knowledge,c.catalog).knownDefenseCount).toBe(0);
  }
 });
 it('does not persist provenance-free or outdated catalog profiles and never imports another viewer knowledge',()=>{
  const enemy=target(),c=context(enemy),hit={defense:'normal',target:'defender',damage:1};
  expect(resolvedDefenseObservation(enemy,'NORMAL',hit,'self',undefined,c.catalog)?.profileKey).toBeUndefined();
  c.catalog.characters[0].defense.NORMAL='weak';
  const observation=resolvedDefenseObservation(enemy,'NORMAL',hit,'self',c.snapshot,c.catalog)!;
  expect(observation.profileKey).toBeUndefined();
  const rounds=[{events:[{defenseObservation:{...observation,viewer:'other'}}]}];
  expect(observedOpponentIntel(enemy,1,c.snapshot,battleKnowledge(c.snapshot,rounds,'self'),c.catalog).knownDefenseCount).toBe(0);
 });
 it('strips raw tables from public own/preview/archived DTOs without altering skills, suitability, stats or actual reactions',()=>{
  const enemy=target(),value={fighter:enemy,reserves:[enemy],event:{defense:'immune'},intel:{version:'opponent-intel-v1',completedTurns:0,defenses:enemy.defense,stats:enemy.stats,statsKnown:true}};
  const safe=publicDefenseSafe(value);
  expect(safe.fighter.defense).toBeUndefined();expect(safe.reserves[0].defense).toBeUndefined();expect(safe.fighter.suitability).toEqual(enemy.suitability);
  expect(safe.fighter.stats).toEqual(enemy.stats);expect(safe.fighter.attack).toEqual(enemy.attack);expect(safe.event.defense).toBe('immune');
  expect(safe.intel).toMatchObject({version:'opponent-intel-v2',knownDefenseCount:0,statsKnown:false,stats:null});
  expect(Object.values(safe.intel.defenses).every(v=>String(v)==='unknown')).toBe(true);expect(value.fighter.defense).toEqual(enemy.defense);
 });
});
