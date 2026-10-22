// 서버의 생성 시점 전투 규칙에 고정한다. 앱 버전·클라이언트 시각으로 추정하지 않는다.
export const CURRENT_COMBAT_RULES_EPOCH = 'gmm-combat-20261021-v3';
export const PREVIOUS_COMBAT_RECORD_LABEL = '이전 버전 전투기록';
export const PREVIOUS_COMBAT_RECORD_REASON = '전투 규칙이 변경되어 재생할 수 없습니다';
// 문자열 대소 비교가 아닌 명시적 허용 목록. 알 수 없는 미래 값도 차단한다.
const replayEpochs: ReadonlySet<string> = new Set([CURRENT_COMBAT_RULES_EPOCH]);
export function canReplayCombat(epoch: unknown): boolean {
  return typeof epoch === 'string' && replayEpochs.has(epoch);
}
export function publicCombatReplayState(b: {combatRulesEpoch?: unknown}) {
  const replayAvailable = canReplayCombat(b.combatRulesEpoch);
  return {
    combatRulesEpoch: typeof b.combatRulesEpoch === 'string' ? b.combatRulesEpoch : null,
    replayAvailable,
    replayLabel: replayAvailable ? null : PREVIOUS_COMBAT_RECORD_LABEL,
    replayBlockedReason: replayAvailable ? null : PREVIOUS_COMBAT_RECORD_REASON,
  };
}
// 진행 중인 구 전투는 고정 엔진으로 마무리한다. 종료 후 전체 과거 동작은 내보내지 않는다.
// DB/전투 객체는 수정하지 않고 결과·최종 현재 상태 DTO만 남긴다.
export function exposeCombatHistory(b: {combatRulesEpoch?: unknown; phase: string}): boolean {
  return b.phase !== 'over' || canReplayCombat(b.combatRulesEpoch);
}
