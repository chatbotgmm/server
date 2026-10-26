import {createHmac} from 'node:crypto';
import {skillElements,type SkillElement,type Defense} from './roster60-rules.js';
import type {Fighter} from '../roster60-battle.js';

// Stats remain battle-local. Defense knowledge comes exclusively from actual attacks.
export const opponentIntelTuning = Object.freeze({statsAfterCompletedTurns:1});
export type OpponentIntel = {
 version:'opponent-intel-v2';speciesId:string;name:string;level:number;completedTurns:number;
 defenseKnowledgeScope:'base-observed'|'battle-observed'|'unverified';
 statsKnown:boolean;stats:Fighter['stats']|null;
 defenses:Record<SkillElement,Defense|'unknown'>;knownDefenseCount:number;totalDefenseCount:number;
};
export function opponentIntel(enemy:Fighter,completedTurns:number,known:Partial<Record<SkillElement,Defense>>={},scope:OpponentIntel['defenseKnowledgeScope']='unverified'):OpponentIntel {
 const turns=Math.max(0,Math.floor(completedTurns));
 const count=skillElements.filter(e=>known[e]!==undefined).length,statsKnown=turns>=opponentIntelTuning.statsAfterCompletedTurns;
 return {version:'opponent-intel-v2',speciesId:enemy.speciesId,name:enemy.name,level:enemy.level,completedTurns:turns,defenseKnowledgeScope:scope,
  statsKnown,stats:statsKnown?{...enemy.stats}:null,
  defenses:Object.fromEntries(skillElements.map(e=>[e,known[e]??'unknown'])) as OpponentIntel['defenses'],
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
