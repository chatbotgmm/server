import {createHmac} from 'node:crypto';
import {skillElements,type SkillElement,type Defense} from './roster60-rules.js';
import type {Fighter} from '../roster60-battle.js';

// User-approved reveal cadence (2 initially, +2 per completed turn); not combat/balance policy.
export const opponentIntelTuning = Object.freeze({initialDefenses:2,defensesPerCompletedTurn:2,statsAfterCompletedTurns:1});
export type OpponentIntel = {
 version:'opponent-intel-v1';speciesId:string;name:string;level:number;completedTurns:number;
 statsKnown:boolean;stats:Fighter['stats']|null;
 defenses:Record<SkillElement,Defense|'unknown'>;knownDefenseCount:number;totalDefenseCount:number;
};
/** Deterministic keyed ranking: polling, retries, reconnect and forced replacement cannot reroll. */
export function opponentIntel(enemy:Fighter,completedTurns:number,seed:string,battleId:string,viewer:string):OpponentIntel {
 const turns=Math.max(0,Math.floor(completedTurns));
 const identity=enemy.ownedId??enemy.speciesId;
 const ranked=[...skillElements].sort((a,b)=>{
  const rank=(element:string)=>createHmac('sha256',seed).update(JSON.stringify(['opponent-intel-v1',battleId,viewer,identity,element])).digest('hex');
  return rank(a).localeCompare(rank(b));
 });
 const count=Math.min(skillElements.length,opponentIntelTuning.initialDefenses+turns*opponentIntelTuning.defensesPerCompletedTurn);
 const known=new Set(ranked.slice(0,count)),statsKnown=turns>=opponentIntelTuning.statsAfterCompletedTurns;
 return {version:'opponent-intel-v1',speciesId:enemy.speciesId,name:enemy.name,level:enemy.level,completedTurns:turns,
  statsKnown,stats:statsKnown?{...enemy.stats}:null,
  defenses:Object.fromEntries(skillElements.map(e=>[e,known.has(e)?enemy.defense[e]:'unknown'])) as OpponentIntel['defenses'],
  knownDefenseCount:count,totalDefenseCount:skillElements.length};
}
/** Whitelisted public opponent: full combat values stay only in server persistence. */
export function opponentFighter(enemy:Fighter,intel:OpponentIntel) {
 return {id:'enemy',speciesId:enemy.speciesId,name:enemy.name,level:enemy.level,hp:enemy.hp,mp:enemy.mp,
  stats:intel.statsKnown?{...enemy.stats}:{hp:enemy.stats.hp,mp:enemy.stats.mp},
  attack:{...enemy.attack},skills:enemy.skills.map(s=>({...s}))};
}

export type EnemyEncounter={id:string;speciesId:string;level:number;tutorial:boolean;mode?:'story'|'duel'|'adventure';enemySpeciesIds?:string[];fixedEnemy?:boolean};
/** Never touches human matchmaking. Encounter level/rewards remain unchanged. */
export function selectEnemySpecies(encounter:EnemyEncounter,encounters:readonly EnemyEncounter[],seed:string):string {
 if(encounter.tutorial||encounter.mode==='story'||encounter.fixedEnemy||!encounter.mode)return encounter.speciesId;
 const pool=[...new Set(encounter.enemySpeciesIds??encounters.filter(e=>e.mode===encounter.mode&&!e.tutorial&&!e.fixedEnemy).map(e=>e.speciesId))].sort();
 if(!pool.length)throw Error('ENEMY_POOL_EMPTY');
 // Stable hash ranking avoids modulo bias and does not depend on client-provided array order.
 return pool.sort((a,b)=>{
  const rank=(species:string)=>createHmac('sha256',seed).update(JSON.stringify(['random-ai-enemy-v1',encounter.id,species])).digest('hex');
  return rank(a).localeCompare(rank(b));
 })[0];
}
