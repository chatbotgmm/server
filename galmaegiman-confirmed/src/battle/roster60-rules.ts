// New product rules. No legacy creature attributes or review fixture balance defaults.
export const roster60Epoch = 'gmm-roster60-v1';
export const skillElements = ['NORMAL','FIRE','WATER','LIGHTNING','ICE','NATURE','HOLY','DARK'] as const;
export type SkillElement = typeof skillElements[number];
export type Defense = 'weak'|'normal'|'resist'|'immune'|'reflect';
export type ElementProfile<T> = Record<SkillElement,T>;
export type SkillDefinition = {id:string;name:string;element:SkillElement;mpCost:number};
export type ReincarnationXpPolicy = {earlyRequiredPercent:number;throughLevel:number};
// Confirmed product policy: target levels 2..60 discounted; 61..99 normal.
export const confirmedReincarnationXpPolicy:Readonly<ReincarnationXpPolicy> = Object.freeze({earlyRequiredPercent:70,throughLevel:60});
export type GrowthPoint = {totalXp:number;maxMp:number};
export type RosterDefinition = {
  id:string;name:string;skills:SkillDefinition[];attack:SkillDefinition;
  suitability:ElementProfile<number>;defense:ElementProfile<Defense>;growth:GrowthPoint[];reincarnationXpPolicy?:ReincarnationXpPolicy;
};
function integer(n:number,label:string) {
  if(!Number.isSafeInteger(n)||n<0)throw new Error(`INVALID_${label}`);
}
function finite(n:number,label:string) {
  if(!Number.isFinite(n))throw new Error(`INVALID_${label}`);
}
// The argument is server-owned per-instance state, never a client eligibility claim.
export function rosterMaxLevel(reincarnation=0):60|99 {
  integer(reincarnation,'REINCARNATION');return reincarnation>0?99:60;
}
export function validateRosterLevel(level:number,reincarnation=0) {
  integer(level,'LEVEL');if(level<1||level>rosterMaxLevel(reincarnation))throw new Error('LEVEL_CAP_EXCEEDED');
}
export function validateGrowth(curve:readonly GrowthPoint[]) {
  if(!curve.length||curve.length>99||curve[0].totalXp!==0)throw new Error('INVALID_GROWTH_ORIGIN');
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
// Discount required XP only; earned/current-cycle cumulative XP is unchanged.
// Per-level ceil rounds fractional requirements up and keeps positive transitions.
export function instanceGrowthCurve(curve:readonly GrowthPoint[],reincarnation=0,policy?:ReincarnationXpPolicy):GrowthPoint[] {
 validateGrowth(curve);rosterMaxLevel(reincarnation);
 if(!reincarnation)return curve.map(p=>({...p}));
 policy ??= confirmedReincarnationXpPolicy;
 if(!Number.isSafeInteger(policy.earlyRequiredPercent)||policy.earlyRequiredPercent<1||policy.earlyRequiredPercent>100||!Number.isSafeInteger(policy.throughLevel)||policy.throughLevel<2||policy.throughLevel>99)throw Error('INVALID_REINCARNATION_XP_POLICY');
 let totalXp=0;return curve.map((p,i)=>{
  if(i){const delta=BigInt(p.totalXp-curve[i-1].totalXp),percent=i<policy.throughLevel?policy.earlyRequiredPercent:100;
   totalXp+=Number((delta*BigInt(percent)+99n)/100n);}
  return {totalXp,maxMp:p.maxMp};
 });
}
export function growthAt(totalXp:number,curve:readonly GrowthPoint[],reincarnation=0,policy?:ReincarnationXpPolicy) {
  curve=instanceGrowthCurve(curve,reincarnation,policy);
  integer(totalXp,'XP');validateGrowth(curve);
  const maxLevel=rosterMaxLevel(reincarnation);
  // Reject unapproved overflow handling instead of discarding/banking/converting XP.
  if(curve.length>=maxLevel&&totalXp>curve[maxLevel-1].totalXp)throw new Error('CAP_XP_POLICY_REQUIRED');
  let index=0;
  while(index+1<Math.min(curve.length,maxLevel)&&curve[index+1].totalXp<=totalXp)index++;
  return {level:index+1,maxLevel,reincarnation,totalXp,xp:totalXp-curve[index].totalXp,maxMp:curve[index].maxMp,
    xpToNext:index+1<Math.min(curve.length,maxLevel)?curve[index+1].totalXp-totalXp:null};
}
// Same result for preview and creation; overflow policy remains explicitly gated.
export function combinationGrowth(materialTotalXp:readonly number[],curve:readonly GrowthPoint[]) {
  if(!materialTotalXp.length)throw new Error('MATERIALS_REQUIRED');
  let sum=0n;
  for(const xp of materialTotalXp){integer(xp,'XP');sum+=BigInt(xp);}
  return growthAt(Number(sum/BigInt(materialTotalXp.length)),curve);
}
export function startMp(totalXp:number,curve:readonly GrowthPoint[],reincarnation=0,policy?:ReincarnationXpPolicy) {
  const {maxMp}=growthAt(totalXp,curve,reincarnation,policy);return {mp:maxMp,maxMp};
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
