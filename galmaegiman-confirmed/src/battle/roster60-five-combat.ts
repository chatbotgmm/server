import {createHash} from 'node:crypto';
import {resolveElementDamage,type Defense} from './roster60-rules.js';
export type FiveCombatPolicy={model:'linear-level-hyperbolic-v1';version:string;levelSlope:number;durabilityScale:number;resistanceScale:number};
function nonnegative(n:number,label:string){if(!Number.isFinite(n)||n<0)throw Error(`INVALID_${label}`);}
export function validateFiveCombatPolicy(p:FiveCombatPolicy){
 if(!p||typeof p.version!=='string'||!p.version.trim())throw Error('FIVE_COMBAT_POLICY_REQUIRED');
 if(p.model!=='linear-level-hyperbolic-v1')throw Error('FIVE_COMBAT_MODEL_REQUIRED');
 nonnegative(p.levelSlope,'LEVEL_SLOPE');
 for(const k of ['durabilityScale','resistanceScale'] as const)if(!Number.isFinite(p[k])||p[k]<=0)throw Error(`INVALID_${k}`);
}
// Candidate family, coefficients supplied by an explicitly selected catalog policy.
// The curve family itself remains a balance proposal; no default coefficients.
export function fiveStatAttack(input:{power:number;level:number;durability:number;suitability:number;defense:Defense;attackerHp:number;defenderHp:number},p:FiveCombatPolicy,reflectionDamage?:(baseDamage:number,suitabilityMultiplier:number)=>number){
 validateFiveCombatPolicy(p);nonnegative(input.power,'POWER');nonnegative(input.durability,'DURABILITY');
 if(!Number.isInteger(input.level)||input.level<1||input.level>99)throw Error('INVALID_LEVEL');
 const levelMultiplier=1+p.levelSlope*(input.level-1);
 // Divide first to avoid overflowing D+K for finite, extreme D.
 const durabilityMultiplier=1/(1+input.durability/p.durabilityScale);
 const baseDamage=input.power*levelMultiplier*durabilityMultiplier;
 if(!Number.isFinite(baseDamage))throw Error('INVALID_BASE_DAMAGE');
 // Reflection detail is not implicitly selected by the five-stat principles.
 if(input.defense==='reflect'&&!reflectionDamage)throw Error('REFLECTION_POLICY_REQUIRED');
 return {...resolveElementDamage({...input,baseDamage},reflectionDamage),levelMultiplier,durabilityMultiplier,policyVersion:p.version};
}
export function statusChance(baseChance:number,resistance:number,p:FiveCombatPolicy){
 validateFiveCombatPolicy(p);nonnegative(resistance,'STATUS_RESISTANCE');
 if(!Number.isFinite(baseChance)||baseChance<0||baseChance>1)throw Error('INVALID_STATUS_CHANCE');
 return baseChance/(1+resistance/p.resistanceScale); // duration is deliberately absent
}
export type TurnOrderDecision={version:'five-speed-v1';battleId:string;turn:number;fighters:[string,string];speeds:[number,number];order:[string,string];tie:boolean};
// seed is server-owned and must be persisted with the battle before accepting actions.
// Pure/reproducible: retry never calls RNG and cannot reroll. No legacy priority field.
export function decideTurnOrder(battleId:string,turn:number,fighters:[{id:string;baseSpeed:number;modifiers:readonly number[]},{id:string;baseSpeed:number;modifiers:readonly number[]}],serverSeed:string):TurnOrderDecision{
 if(!battleId||!serverSeed||!Number.isSafeInteger(turn)||turn<0||!fighters[0].id||!fighters[1].id||fighters[0].id===fighters[1].id)throw Error('INVALID_TURN_IDENTITY');
 const sorted=[...fighters].sort((a,b)=>a.id.localeCompare(b.id));
 const speeds=sorted.map(f=>{
  nonnegative(f.baseSpeed,'SPEED');if(!f.modifiers.every(Number.isFinite))throw Error('INVALID_SPEED_MODIFIER');
  const v=f.modifiers.reduce((a,b)=>a+b,f.baseSpeed);if(!Number.isFinite(v))throw Error('INVALID_FINAL_SPEED');return Math.max(0,v);
 }) as [number,number];
 const tie=speeds[0]===speeds[1];
 const first=tie?createHash('sha256').update(JSON.stringify([serverSeed,battleId,turn,sorted.map(f=>f.id)])).digest()[0]%2:speeds[0]>speeds[1]?0:1;
 return {version:'five-speed-v1',battleId,turn,fighters:[sorted[0].id,sorted[1].id],speeds,order:[sorted[first].id,sorted[1-first].id],tie};
}
export function reuseTurnOrder(saved:TurnOrderDecision,battleId:string,turn:number){
 if(saved.version!=='five-speed-v1'||saved.battleId!==battleId||saved.turn!==turn)throw Error('TURN_DECISION_MISMATCH');
 return structuredClone(saved);
}
