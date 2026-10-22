// 공개 선두 확인 후 자기 선두 속성을 1회 선택한다. 선택은 해당 전투 유닛에 유지한다.
// snapshot은 서버 저장 상태로만 전달하며 클라이언트 입력으로 만들면 안 된다.
import {attrKeys,type Attr} from './core.js';
export const FREESTYLE_SELECTION_ENABLED=true;
export interface FreestyleSnapshot {
  selectionId:string;phaseId:string;side:0|1;targetKey:string;opponentAttribute:Attr;
  chosen?:Attr;
}
export interface FreestyleChoice {selectionId:string;phaseId:string;targetKey:string;attribute:Attr}
export function publicFreestyleSelection(snapshot:FreestyleSnapshot){
  return {kind:'own-attribute' as const,selectionId:snapshot.selectionId,phaseId:snapshot.phaseId,
    targetKey:snapshot.targetKey,opponentAttribute:snapshot.opponentAttribute,attributes:[...attrKeys],
    ...(snapshot.chosen?{chosen:snapshot.chosen}:{})};
}
// 순수 검증: 동일 재전송은 idempotent, 다른 재전송·오래된 phase·다른 대상은 거부한다.
// 실제 저장은 기존 명령 CAS 트랜잭션 안에서 snapshot 및 결정 로그와 함께 해야 한다.
export function validateFreestyleChoice(snapshot:FreestyleSnapshot,side:0|1,phaseId:string,input:unknown){
  if(!input||typeof input!=='object'||Array.isArray(input))throw new Error('속성 선택 형식 오류');
  const p=input as Record<string,unknown>;
  if(Object.keys(p).some(k=>!['selectionId','phaseId','targetKey','attribute'].includes(k)))throw new Error('알 수 없는 속성 선택 필드');
  if(snapshot.side!==side||snapshot.phaseId!==phaseId||p.phaseId!==snapshot.phaseId||p.selectionId!==snapshot.selectionId||p.targetKey!==snapshot.targetKey)throw new Error('만료되었거나 다른 대상의 속성 선택');
  if(typeof p.attribute!=='string'||!attrKeys.includes(p.attribute as Attr))throw new Error('알 수 없는 속성');
  const attribute=p.attribute as Attr;
  if(snapshot.chosen&&snapshot.chosen!==attribute)throw new Error('이미 속성을 선택했어요');
  return {attribute,duplicate:snapshot.chosen===attribute};
}
