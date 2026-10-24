import {it,expect} from 'vitest';
import {fiveStatAttack,statusChance,decideTurnOrder,reuseTurnOrder,validateFiveCombatPolicy} from '../src/battle/roster60-five-combat.js';
// Structural test coefficients, not approved balance.
const p={model:'linear-level-hyperbolic-v1' as const,version:'test-only',levelSlope:0.04,durabilityScale:100,resistanceScale:100};
const input={power:100,level:1,durability:0,suitability:0,defense:'normal' as const,attackerHp:10000,defenderHp:10000};
it('level scales power; durability reduces every attack with diminishing added mitigation',()=>{
 expect(fiveStatAttack({...input,level:20},p).damage).toBeGreaterThan(fiveStatAttack(input,p).damage);
 const damages=[0,100,200].map(d=>fiveStatAttack({...input,durability:d},p).damage);
 expect(damages).toEqual([100,50,33]);expect(damages[0]-damages[1]).toBeGreaterThan(damages[1]-damages[2]);
});
it('rejects negative and invalid durability; handles finite extremes without negative damage',()=>{
 for(const d of [-1,NaN,Infinity])expect(()=>fiveStatAttack({...input,durability:d},p)).toThrow();
 expect(fiveStatAttack({...input,durability:Number.MAX_VALUE},p).damage).toBe(0);
 expect(fiveStatAttack({...input,suitability:-100},p).damage).toBe(0);
 expect(fiveStatAttack({...input,defense:'immune',suitability:100},p).damage).toBe(0);
 expect(()=>fiveStatAttack({...input,defense:'reflect'},p)).toThrow('REFLECTION_POLICY_REQUIRED');
 const reflected=fiveStatAttack({...input,defense:'reflect'},p,(base,fit)=>base*fit);expect(reflected.target).toBe('attacker');expect(reflected.damage).toBe(100);expect(reflected.canReflectAgain).toBe(false);
});
it('status resistance changes chance only, with bounds and no invented cap',()=>{
 expect(statusChance(1,100,p)).toBe(.5);expect(statusChance(0,100,p)).toBe(0);
 expect(statusChance(1,Number.MAX_VALUE,p)).toBeGreaterThanOrEqual(0);
 for(const chance of [-1,1.1,NaN])expect(()=>statusChance(chance,1,p)).toThrow();
 for(const resist of [-1,Infinity,NaN])expect(()=>statusChance(.5,resist,p)).toThrow();
 expect(()=>validateFiveCombatPolicy({...p,durabilityScale:0})).toThrow();
});
it('final speed includes supplied modifiers, no extra action or inherited priority',()=>{
 const f:[any,any]=[{id:'a',baseSpeed:10,modifiers:[2]},{id:'b',baseSpeed:9,modifiers:[4]}];
 expect(decideTurnOrder('battle',1,f,'server-owned-seed').order).toEqual(['b','a']);
});
it('tie decisions are reproducible across retries and input ordering and saved decision is reused',()=>{
 const f:[any,any]=[{id:'a',baseSpeed:10,modifiers:[]},{id:'b',baseSpeed:10,modifiers:[]}];
 const first=decideTurnOrder('battle',1,f,'server-owned-seed');
 expect(decideTurnOrder('battle',1,[f[1],f[0]],'server-owned-seed')).toEqual(first);
 expect(reuseTurnOrder(first,'battle',1)).toEqual(first);
 expect(()=>reuseTurnOrder(first,'battle',2)).toThrow();
 const winners=new Set(Array.from({length:100},(_,i)=>decideTurnOrder('battle',i,f,'server-owned-seed').order[0]));expect(winners.size).toBe(2);
});
