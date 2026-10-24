import {it,expect} from 'vitest';import {readFileSync} from 'node:fs';
import {validateFiveStatCatalog,fiveRebirthStats} from '../src/battle/roster60-five-stats.js';
import {validateRosterDefinition,instanceGrowthCurve,focusMp} from '../src/battle/roster60-rules.js';
const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
it('test profile validates all 60 species/32 skills without approving workbook or old six-stat drafts',()=>{
 validateFiveStatCatalog(c);expect(c.characters).toHaveLength(60);expect(c.skillLibrary).toHaveLength(32);expect(c.productionBalanceApproved).toBe(false);
 expect(JSON.parse(readFileSync('design/roster60-review/workbook-complete-proposal.json','utf8')).approved).toBe(false);
 for(const u of c.characters){validateRosterDefinition(u);expect(u.combatGrowth).toHaveLength(99);expect(u.skills.length).toBeLessThanOrEqual(6);expect(u.attack.mpCost).toBe(0);expect(u.growth[19].maxMp).toBe(u.combatGrowth[19].mp);}
});
it('approved rebirth only changes original HP/MP/durability, never compounds growth or speed/resistance',()=>{
 for(const u of c.characters)for(let n=0;n<=5;n++){
  const actual=fiveRebirthStats(u.levelOneStats,u.combatGrowth[59],n);
  for(const key of ['speed','statusResistance'])expect(actual[key as keyof typeof actual]).toBe(u.combatGrowth[59][key]);
  for(const key of ['hp','mp','durability'])expect(actual[key as keyof typeof actual]-u.combatGrowth[59][key]).toBe(Math.floor(u.levelOneStats[key]*n/10+.5));
 }
 expect(()=>fiveRebirthStats(c.characters[0].stats,c.characters[0].stats,6)).toThrow('REINCARNATION_LIMIT');
});
it('59→60 discount,60→61 normal and full-MP focus never adds excess MP',()=>{
 const u=c.characters[0],normal=u.growth,reborn=instanceGrowthCurve(normal,1);
 expect(reborn[59].totalXp-reborn[58].totalXp).toBe(Math.ceil((normal[59].totalXp-normal[58].totalXp)*.7));
 expect(reborn[60].totalXp-reborn[59].totalXp).toBe(normal[60].totalXp-normal[59].totalXp);
 expect(focusMp(u.stats.mp,u.stats.mp)).toEqual({mp:u.stats.mp,recovered:0});
});
