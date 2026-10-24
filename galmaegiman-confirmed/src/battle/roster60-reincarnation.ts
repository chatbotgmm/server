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

export const reincarnationStatKeys=['hp','mp','patk','matk','pdef','mdef'] as const;
export type BaseSixStats=Record<typeof reincarnationStatKeys[number],number>;
// Compute from ordinary species level-one base ONLY. Nearest integer, halves up.
// Returning a projection rather than incrementing stored stats prevents compounding.
export function firstReincarnationStats(base:BaseSixStats){
 if(Object.keys(base).sort().join()!==[...reincarnationStatKeys].sort().join())throw Error('SIX_BASE_STATS_REQUIRED');
 const after={} as BaseSixStats;
 for(const k of reincarnationStatKeys){
  if(!Number.isSafeInteger(base[k])||base[k]<0)throw Error('INVALID_BASE_STAT');
  const n=(BigInt(base[k])*110n+50n)/100n;
  if(n>BigInt(Number.MAX_SAFE_INTEGER))throw Error('INVALID_BASE_STAT');after[k]=Number(n);
 }
 return after;
}
export function previewFirstReincarnation(input:{totalXp:number;reincarnation:number;baseLevelOneStats:BaseSixStats;curve:GrowthPoint[];suitability:unknown;defense:unknown}){
 if(input.reincarnation!==0)throw Error('REPEAT_REINCARNATION_POLICY_REQUIRED');
 const after=firstReincarnationStats(input.baseLevelOneStats);
 const plan=previewReincarnation(input,{approved:true,version:'first-six-stats-10pct-v1',bonusFor:()=>Object.fromEntries(reincarnationStatKeys.map(k=>[k,after[k]-input.baseLevelOneStats[k]]))});
 return {...plan,suitability:structuredClone(input.suitability),defense:structuredClone(input.defense)};
}
