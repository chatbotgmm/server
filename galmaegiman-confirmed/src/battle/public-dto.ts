import {publicFreestyleSelection} from './freestyle-selection.js';
// 공개 DTO는 전투에 고정된 규칙에서 필요한 값만 복사한다.
import type {Battle,Fighter} from './engine.js';
export function publicAugment(b:Battle,id:string,rerolled?:boolean){
  const a=b.R.battleAug.get(id);if(!a)throw new Error('전투 규칙에 없는 증강체');
  return {id:a.id,name:a.name,desc:a.desc,p:{...a.p},version:a.version??1,
    ...(a.tier?{tier:a.tier}:{}),...(rerolled===undefined?{}:{rerolled})};
}
export function publicAugmentState(b:Battle){return {augmentVersion:b.R.augmentVersion,effectsVersion:b.effectsVersion??1,...(b.phase==='choose'&&b.sides.some(s=>s.freestyle&&!s.freestyle.chosen)?{choiceKind:'own-attribute' as const}:{})};}
export function publicShield(b:Battle,f:Fighter){
  const active=b.effectsVersion===2&&b.augmentOffers?.version===2&&Number.isFinite(f.augment?.shieldUntil)&&f.augment!.shieldUntil>=b.turn;
  const shield=active&&Number.isFinite(f.augment?.shield)?Math.max(0,f.augment!.shield):0;
  return {shield,shieldTurns:shield>0?Math.max(0,f.augment!.shieldUntil-b.turn+1):0};
}


// 자기 쪽 보류 선택만 공개한다. 상대 선택은 동시 해결 전 공개하지 않는다.
export function publicAttributeSelection(b:Battle,side:0|1){const s=b.sides[side].freestyle;return s&&!s.chosen?publicFreestyleSelection(s):null;}
