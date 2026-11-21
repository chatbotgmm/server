// 운영 기본(V2 증강·현재 전투 규칙)을 지원하는 클라이언트가 보내는 머리글.
// 웹 public/game/game.js 의 api() 머리글과 같은 값이다. 인증이 아니라 화면 호환 선언이다.
import {CURRENT_COMBAT_RULES_EPOCH} from '../../src/battle/combat-epoch.js';
import type {Attr} from '../../src/battle/core.js';
import type {FreestyleChoice} from '../../src/battle/freestyle-selection.js';
export const V2_CLIENT_HEADERS={'x-gmm-augment-version':'2','x-gmm-combat-epoch':CURRENT_COMBAT_RULES_EPOCH} as const;
// V2의 「자유자재」(choose-own-attribute) 증강을 고르면 행동 전에 내 속성 선택 단계가 한 번 더 온다.
// 엔진은 속성 선택을 다른 선택(augment·switchTo)과 함께 받지 않는다(src/battle/engine.ts validateFreestyle).
// 선택지 순서는 전투 난수로 정해지므로, 첫 선택지를 고르는 테스트는 이 단계를 처리해야 결과가 매번 같다.
type AttributeSelection={selectionId:string;phaseId:string;targetKey:string;attributes:readonly Attr[]};
export function freestyleChoose(team:{attributeSelection?:AttributeSelection|null}):{freestyle:FreestyleChoice}|undefined{
  const s=team.attributeSelection;
  return s?{freestyle:{selectionId:s.selectionId,phaseId:s.phaseId,targetKey:s.targetKey,attribute:s.attributes[0]}}:undefined;
}
