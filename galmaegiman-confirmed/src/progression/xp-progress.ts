import {growthAt,type GrowthPoint,type ReincarnationXpPolicy} from '../battle/roster60-rules.js';

export type XpProgressPoint={level:number;totalXp:string;levelStartTotalXp:string;nextLevelTotalXp:string|null;maxLevel:number};
export type XpProgress={before:XpProgressPoint;after:XpProgressPoint};
type PinnedGrowth={startingXp:string;growth:readonly GrowthPoint[];reincarnation:number;reincarnationXpPolicy?:ReincarnationXpPolicy};

/** Presentation only. Use the same pinned per-instance curve and growthAt boundaries as settlement.
 * Older receipts/short legacy curves remain valid without this optional display data. */
export function unitXpProgress(member:PinnedGrowth,endingXp:string,startingXp=member.startingXp):XpProgress|undefined {
 try {
  const point=(value:string):XpProgressPoint=>{
   if(!/^\d+$/.test(value)||BigInt(value)>BigInt(Number.MAX_SAFE_INTEGER))throw Error('UNAVAILABLE_XP_PROGRESS');
   const grown=growthAt(Number(value),member.growth,member.reincarnation,member.reincarnationXpPolicy);
   if(grown.xpToNext===null&&grown.level<grown.maxLevel)throw Error('INCOMPLETE_XP_PROGRESS');
   return {level:grown.level,totalXp:grown.totalXp.toString(),levelStartTotalXp:(grown.totalXp-grown.xp).toString(),nextLevelTotalXp:grown.xpToNext===null?null:(grown.totalXp+grown.xpToNext).toString(),maxLevel:grown.maxLevel};
  };
  return {before:point(startingXp),after:point(endingXp)};
 } catch {return undefined;}
}
