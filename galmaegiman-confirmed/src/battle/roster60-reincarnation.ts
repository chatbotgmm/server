import {rosterMaxLevel,validateGrowth} from './roster60-rules.js';
import type {GrowthPoint} from './roster60-rules.js';
export type ReincarnationBonusPolicy={approved:true;version:string;bonusFor:(nextCycle:number)=>Record<string,number>};
// Preview only: no grant endpoint until bonus values and repeat policy are approved.
// totalXp is current growth-cycle cumulative XP, NOT lifetime XP. The old cycle is
// returned for audit only and must never feed growthAt after reset or crafting XP.
export function previewReincarnation(input:{totalXp:number;reincarnation:number;baseLevelOneStats:Record<string,number>;curve:GrowthPoint[]},policy?:ReincarnationBonusPolicy){
 rosterMaxLevel(input.reincarnation);validateGrowth(input.curve);
 if(!Number.isSafeInteger(input.totalXp)||input.totalXp<0)throw Error('INVALID_XP');
 if(!policy?.approved||!policy.version)throw Error('REINCARNATION_BONUS_POLICY_REQUIRED');
 const cycle=input.reincarnation+1;rosterMaxLevel(cycle);
 const bonus=policy.bonusFor(cycle),base=input.baseLevelOneStats,keys=Object.keys(base);
 if(!keys.length||keys.sort().join()!==Object.keys(bonus).sort().join())throw Error('INVALID_REINCARNATION_BONUS');
 const after:Record<string,number>={};let improved=false;
 for(const key of keys){
  if(!Number.isFinite(base[key])||base[key]<0||!Number.isFinite(bonus[key])||bonus[key]<0)throw Error('INVALID_REINCARNATION_BONUS');
  after[key]=base[key]+bonus[key];if(!Number.isFinite(after[key]))throw Error('INVALID_REINCARNATION_BONUS');
  improved ||= bonus[key]>0;
 }
 if(!improved)throw Error('REINCARNATION_MUST_IMPROVE_LEVEL_ONE');
 return {policyVersion:policy.version,previousCycleTotalXp:input.totalXp,
  baselineLevelOneStats:{...base},afterLevelOneStats:after,
  next:{level:1,maxLevel:rosterMaxLevel(cycle),totalXp:0,reincarnation:cycle,permanentBonusStats:{...bonus}}};
}
