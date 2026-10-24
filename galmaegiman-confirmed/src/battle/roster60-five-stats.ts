import {validateFiveCombatPolicy,type FiveCombatPolicy} from './roster60-five-combat.js';
// Current product contract. Old six-stat drafts are archival, not convertible defaults.
export const fiveStatSchema = 'gmm-five-stats-v1' as const;
export const fiveStatKeys = ['hp','mp','durability','statusResistance','speed'] as const;
export type FiveStats = Record<typeof fiveStatKeys[number],number>;
export function validateFiveStats(value:unknown):asserts value is FiveStats {
  if(!value||typeof value!=='object'||Array.isArray(value))throw Error('FIVE_STATS_REQUIRED');
  const stats=value as Record<string,unknown>;
  if(Object.keys(stats).sort().join()!==[...fiveStatKeys].sort().join())throw Error('FIVE_STATS_REQUIRED');
  for(const key of fiveStatKeys)if(!Number.isSafeInteger(stats[key])||(stats[key] as number)<(key==='hp'?1:0))throw Error('INVALID_FIVE_STATS');
}
// Applied at the actual product catalog boundary before grants/crafting/previews.
// No formulas, resistance cap, or legacy-stat conversion is inferred here.
export function validateFiveStatCatalog(value:unknown){
  const c=value as {statSchema?:unknown;characters?:unknown;combatPolicy?:FiveCombatPolicy};
  if(!c||c.statSchema!==fiveStatSchema)throw Error('FIVE_STAT_CATALOG_REQUIRED');
  if(!Array.isArray(c.characters)||!c.characters.length)throw Error('FIVE_STAT_CATALOG_INVALID');
  if(c.combatPolicy!==undefined)validateFiveCombatPolicy(c.combatPolicy);
  for(const unit of c.characters){
    validateFiveStats(unit.stats);validateFiveStats(unit.levelOneStats);
    if(unit.growth?.[0]?.maxMp!==unit.levelOneStats.mp)throw Error('BASE_MP_MISMATCH');
  }
}
