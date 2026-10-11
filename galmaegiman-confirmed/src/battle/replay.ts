// 대결 다시 돌리기(PvP 랭크 검증): 같은 시드·같은 팀에 내가 고른 것만 차례대로 넣으면 상대(AI) 선택까지 똑같이 나옴.
// 화면(public/duel/duel.js)도 이 순서로 엔진을 부르고, 고를 때마다 같은 모양으로 기록해 서버에 보냄
import { aiChoose, act, choose, pending, pickAi, rerollAugment, type Action, type Battle } from './engine.js';

export type PvpEvent = { r: number } | { c: { augment?: string; switchTo?: number } } | { a: Action };

// 내 쪽이 고를 것이 없으면 상대만 고름(화면의 flow()와 같음)
function aiTurn(b: Battle) {
  while (b.phase === 'choose') {
    const p = pending(b, 0);
    if (p.augments || p.needSwitch) return;
    aiChoose(b, 1);
  }
}
export function applyEvent(b: Battle, e: PvpEvent) {
  aiTurn(b);
  if (b.phase === 'over') throw new Error('이미 끝난 대결');
  if ('r' in e) { if (!Number.isInteger(e.r)) throw new Error('잘못된 다시 뽑기'); rerollAugment(b, 0, e.r); }
  else if ('c' in e) { choose(b, 0, e.c); if (b.phase === 'choose') aiChoose(b, 1); }
  else if ('a' in e) { if (e.a.kind === 'item') throw new Error('PvP에서는 아이템을 쓸 수 없음'); act(b, [e.a, pickAi(b, 1)]); }
  else throw new Error('알 수 없는 기록');
  aiTurn(b);
}
// 기록 전체를 넣고 승자(0 = 나, 1 = 상대, null = 아직 안 끝남)
export function replay(b: Battle, events: PvpEvent[]) {
  for (const e of events) { if (b.phase === 'over') break; applyEvent(b, e); }
  return b.winner;
}
