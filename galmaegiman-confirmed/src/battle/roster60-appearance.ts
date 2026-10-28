/** Cosmetic only. Never derives defenses, combat stats, or player identity. */
export type BattleArtVariant = 'black';
const blackSpecies = new Set(['u01','u02','u06','u07','u14','u23','u28','u31']);
export function publicBattleAppearance(fighter:{speciesId:string;artVariant?:unknown}):{artVariant?:BattleArtVariant} {
 return fighter.artVariant==='black'&&blackSpecies.has(fighter.speciesId)?{artVariant:'black'}:{};
}

/** Compatibility for saved battles predating the cosmetic field. Context is server persistence,
 * not a request body, nickname or enemy element. Only the matching enemy slot is decorated. */
export function restoreBlackStoryAppearance(state:any,story:unknown):void {
 if(!story||typeof story!=='object')return;
 const context=story as Record<string,unknown>;
 if(context.version!=='story-battle-v1'||context.kind!=='black'||context.mode!=='adventure'
    ||typeof context.speciesId!=='string'||!blackSpecies.has(context.speciesId))return;
 const decorate=(pair:any)=>{
  const enemy=Array.isArray(pair)?pair[1]:null;
  if(enemy&&enemy.speciesId===context.speciesId)enemy.artVariant='black';
 };
 decorate(state?.fighters);
 for(const round of Array.isArray(state?.events)?state.events:[])
  for(const event of Array.isArray(round?.events)?round.events:[]){
   if(event?.presentation?.version===1){decorate(event.presentation.before);decorate(event.presentation.after);}
  }
}
