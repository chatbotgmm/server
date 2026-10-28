import {publicBattleAppearance,type BattleArtVariant} from './roster60-appearance.js';
/** Display-only snapshots. Never copy owned IDs, reserves, pending actions or combat policy. */
type FighterView = {artVariant?:BattleArtVariant;speciesId:string;name:string;level:number;hp:number;mp:number;stats:{hp:number;mp:number}};
type SkillView = {id:string;name:string;element:string;mpCost:number};
export type PresentedFighter = FighterView & {id:'self'|'enemy'};
export type Presentation = {version:1;before:PresentedFighter[];after:PresentedFighter[];skill?:SkillView};

export function capturePresentation(fighters:readonly FighterView[]):PresentedFighter[] {
 return fighters.map((f,i)=>({id:i===0?'self':'enemy',...publicBattleAppearance(f),speciesId:f.speciesId,name:f.name,level:f.level,
  hp:f.hp,mp:f.mp,stats:{hp:f.stats.hp,mp:f.stats.mp}}));
}

export function presentStep(before:PresentedFighter[],fighters:readonly FighterView[],skill?:SkillView):Presentation {
 return {version:1,before:capturePresentation(before),after:capturePresentation(fighters),
  ...(skill?{skill:{id:skill.id,name:skill.name,element:skill.element,mpCost:skill.mpCost}}:{})};
}

/** Shared PvP stores p0,p1; each viewer receives self,enemy without exposing private identities. */
export function presentationForViewer(value:Presentation|undefined,side:0|1):Presentation|undefined {
 if(!value||value.version!==1)return undefined;
 const order=(pair:PresentedFighter[])=>capturePresentation([pair[side],pair[1-side]]);
 return {...presentStep(order(value.before),order(value.after),value.skill)};
}

