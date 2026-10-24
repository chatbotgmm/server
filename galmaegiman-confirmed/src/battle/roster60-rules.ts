// New product rules. No legacy creature attributes or review fixture balance defaults.
export const roster60Epoch = 'gmm-roster60-v1';
export const skillElements = ['NORMAL','FIRE','WATER','LIGHTNING','ICE','NATURE','HOLY','DARK'] as const;
export type SkillElement = typeof skillElements[number];
export type Defense = 'weak'|'normal'|'resist'|'immune'|'reflect';
export type ElementProfile<T> = Record<SkillElement,T>;
export type SkillDefinition = {id:string;name:string;element:SkillElement;mpCost:number};
export type GrowthPoint = {totalXp:number;maxMp:number};
export type RosterDefinition = {
  id:string;name:string;skills:SkillDefinition[];attack:SkillDefinition;
  suitability:ElementProfile<number>;defense:ElementProfile<Defense>;growth:GrowthPoint[];
};
function integer(n:number,label:string) {
  if(!Number.isSafeInteger(n)||n<0)throw new Error(`INVALID_${label}`);
}
function finite(n:number,label:string) {
  if(!Number.isFinite(n))throw new Error(`INVALID_${label}`);
}
export function validateGrowth(curve:readonly GrowthPoint[]) {
  if(!curve.length||curve[0].totalXp!==0)throw new Error('INVALID_GROWTH_ORIGIN');
  curve.forEach((p,i)=>{
    integer(p.totalXp,'XP');integer(p.maxMp,'MAX_MP');
    if(i&&(p.totalXp<=curve[i-1].totalXp||p.maxMp<curve[i-1].maxMp))throw new Error('INVALID_GROWTH_ORDER');
  });
}
export function validateRosterDefinition(unit:RosterDefinition) {
  if(!/^u(0[1-9]|[1-5][0-9]|60)$/.test(unit.id)||!unit.name.trim())throw new Error('INVALID_ROSTER_ID_OR_NAME');
  if(unit.id==='u01'&&unit.name!=='갈매미맨')throw new Error('INVALID_U01_NAME');
  if('attr' in unit||'element' in unit||'extraAttr' in unit)throw new Error('CREATURE_ELEMENT_REMOVED');
  if(unit.skills.length>6)throw new Error('TOO_MANY_SKILLS');
  if(new Set(unit.skills.map(s=>s.id)).size!==unit.skills.length)throw new Error('DUPLICATE_SKILL');
  for(const skill of [unit.attack,...unit.skills]){
    if(!skill.id||!skill.name||!skillElements.includes(skill.element))throw new Error('INVALID_SKILL');
    integer(skill.mpCost,'MP_COST');
  }
  for(const profile of [unit.suitability,unit.defense]){
    if(Object.keys(profile).sort().join()!==[...skillElements].sort().join())throw new Error('INVALID_ELEMENT_PROFILE');
  }
  for(const element of skillElements){
    finite(unit.suitability[element],'SUITABILITY');
    if(!['weak','normal','resist','immune','reflect'].includes(unit.defense[element]))throw new Error('INVALID_DEFENSE');
  }
  validateGrowth(unit.growth);
}
export function growthAt(totalXp:number,curve:readonly GrowthPoint[]) {
  integer(totalXp,'XP');validateGrowth(curve);
  let index=0;
  while(index+1<curve.length&&curve[index+1].totalXp<=totalXp)index++;
  return {level:index+1,totalXp,xp:totalXp-curve[index].totalXp,maxMp:curve[index].maxMp,
    xpToNext:index+1<curve.length?curve[index+1].totalXp-totalXp:null};
}
// Use this same result for preview and transactional result creation. Keep XP at level cap.
export function combinationGrowth(materialTotalXp:readonly number[],curve:readonly GrowthPoint[]) {
  if(!materialTotalXp.length)throw new Error('MATERIALS_REQUIRED');
  let sum=0n;
  for(const xp of materialTotalXp){integer(xp,'XP');sum+=BigInt(xp);}
  return growthAt(Number(sum/BigInt(materialTotalXp.length)),curve);
}
export function startMp(totalXp:number,curve:readonly GrowthPoint[]) {
  const {maxMp}=growthAt(totalXp,curve);return {mp:maxMp,maxMp};
}
export function focusMp(mp:number,maxMp:number) {
  integer(mp,'MP');integer(maxMp,'MAX_MP');if(mp>maxMp)throw new Error('MP_EXCEEDS_MAX');
  // Floor recovery, including 0 for maxMp<4. Addition cannot overflow safe integers.
  const recovered=Math.min(maxMp-mp,Math.floor(maxMp/4));
  return {mp:mp+recovered,recovered};
}
export function spendMp(mp:number,cost:number) {
  integer(mp,'MP');integer(cost,'MP_COST');if(cost>mp)throw new Error('INSUFFICIENT_MP');return mp-cost;
}
export function resolveElementDamage(input:{baseDamage:number;suitability:number;defense:Defense;attackerHp:number;defenderHp:number},
  reflectionDamage?: (baseDamage:number,suitabilityMultiplier:number)=>number) {
  const {baseDamage,suitability,defense,attackerHp,defenderHp}=input;
  finite(baseDamage,'BASE_DAMAGE');finite(suitability,'SUITABILITY');
  integer(attackerHp,'HP');integer(defenderHp,'HP');
  if(baseDamage<0)throw new Error('INVALID_BASE_DAMAGE');
  const multiplier=Math.max(0,1+suitability*.1);
  let raw:number;
  switch(defense){
    case 'immune':raw=0;break;
    case 'reflect':
      if(!reflectionDamage)throw new Error('REFLECTION_POLICY_REQUIRED');
      raw=reflectionDamage(baseDamage,multiplier);break;
    case 'weak':case 'normal':case 'resist':
      raw=baseDamage*multiplier*({weak:1.5,normal:1,resist:.5}[defense]);break;
    default:throw new Error('INVALID_DEFENSE');
  }
  finite(raw,'RESOLVED_DAMAGE');if(raw<0)throw new Error('INVALID_RESOLVED_DAMAGE');
  const target=defense==='reflect'?'attacker':'defender';
  return {target,damage:Math.min(target==='attacker'?attackerHp:defenderHp,Math.floor(raw)),
    suitabilityMultiplier:multiplier,defense,extraTurn:false as const,
    reflected:defense==='reflect',canReflectAgain:false as const};
}
