import {describe, expect, it} from 'vitest';
import {DEFAULT_RANKED_CONFIG as config, applyRankedResult, casualPriorOffset, initialRankedSnapshot,
  lpForResult, rankedMatchmakingMmr, validateRankedConfig, type RankedSnapshot} from '../src/ranked/rating.js';

const fresh = (patch: Partial<RankedSnapshot> = {}): RankedSnapshot => ({...initialRankedSnapshot(), ...patch});
const win = (s: RankedSnapshot) => applyRankedResult(s, fresh({tierIndex: s.tierIndex}), 1).after;
const lose = (s: RankedSnapshot) => applyRankedResult(s, fresh({tierIndex: s.tierIndex}), 0).after;

describe('일반전과 분리된 랭크 정책', () => {
  it('아무리 일반전 MMR이 높아도 모두 Bronze 0 LP로 시작한다', () => {
    const s = initialRankedSnapshot({mmr: 4000, games: 1000});
    expect(s.tierIndex).toBe(0); expect(s.lp).toBe(0); expect(s.rankedMmr).toBe(1000);
    expect(s.casualPriorOffset).toBe(50); expect(rankedMatchmakingMmr(s)).toBe(1050);
  });
  it('일반전 사전값은 20경기 후 0이고 중간에 다른 일반전을 해도 다시 채워지지 않는다', () => {
    const s = initialRankedSnapshot({mmr: 3000, games: 100});
    expect(rankedMatchmakingMmr({...s, games: 10})).toBe(1025);
    expect(rankedMatchmakingMmr({...s, games: 20})).toBe(1000);
    expect(rankedMatchmakingMmr({...s, games: 100})).toBe(1000);
    expect(casualPriorOffset(3000, 0)).toBe(0);
    expect(casualPriorOffset(-5000, 100)).toBe(-50);
  });
  it('동급 +20/-15, 상위 상대일수록 더 얻고 덜 잃되 차이 보정은 제한한다', () => {
    expect(lpForResult(1, 1, true)).toBe(20);
    expect(lpForResult(1, 1, false)).toBe(-15);
    expect(lpForResult(1, 2, true)).toBe(23);
    expect(lpForResult(1, 2, false)).toBe(-12);
    expect(lpForResult(0, 5, true)).toBe(26);
    expect(lpForResult(0, 5, false)).toBe(-9);
    expect(lpForResult(5, 0, true)).toBe(14);
    expect(lpForResult(5, 0, false)).toBe(-21);
  });
  it('MMR과 일반전 사전값이 달라도 LP는 승패와 티어만으로 결정한다', () => {
    const a = applyRankedResult(fresh({lp: 30, rankedMmr: 500}), fresh({rankedMmr: 3000}), 1);
    const b = applyRankedResult(fresh({lp: 30, rankedMmr: 3000, casualPriorOffset: 50}), fresh({rankedMmr: 500}), 1);
    expect(a.lpDelta).toBe(20); expect(b.lpDelta).toBe(20); expect(a.mmrDelta).toBeGreaterThan(b.mmrDelta);
  });
  it('100 LP 도달 경기는 승급전 1승으로 세지 않는다', () => {
    const s = win(fresh({lp: 90}));
    expect(s.lp).toBe(100); expect(s.tierIndex).toBe(0);
    expect(s.promotionActive).toBe(true); expect(s.promotionWins).toBe(0);
  });
  it('승급전은 두 번 이기면 다음 티어 0 LP', () => {
    const start = fresh({lp: 100, promotionActive: true});
    const s = win(lose(win(start)));
    expect(s.tierIndex).toBe(1); expect(s.lp).toBe(0); expect(s.promotionActive).toBe(false);
    expect(s.promotionWins).toBe(0); expect(s.promotionLosses).toBe(0);
  });
  it('승급전 두 번 패배하면 잠정 실패 LP 75로 종료', () => {
    const s = lose(win(lose(fresh({lp: 100, promotionActive: true}))));
    expect(s.tierIndex).toBe(0); expect(s.lp).toBe(75); expect(s.promotionActive).toBe(false);
  });
  it('무승부는 승급전의 승패/LP를 소비하지 않는다', () => {
    const s = fresh({lp: 100, promotionActive: true, promotionWins: 1});
    const result = applyRankedResult(s, fresh(), 0.5);
    expect(result.after.lp).toBe(100); expect(result.after.promotionWins).toBe(1);
    expect(result.after.promotionLosses).toBe(0); expect(result.after.draws).toBe(1);
  });
  it('Bronze는 0 아래로 내려가지 않고 상위 티어는 0 도달 다음 패배에서 강등한다', () => {
    expect(lose(fresh()).lp).toBe(0); expect(lose(fresh()).tierIndex).toBe(0);
    const zero = lose(fresh({tierIndex: 1, lp: 5}));
    expect(zero.tierIndex).toBe(1); expect(zero.lp).toBe(0);
    const down = lose(zero);
    expect(down.tierIndex).toBe(0); expect(down.lp).toBe(75);
  });
  it('최상위 티어는 승급전 없이 LP를 누적한다', () => {
    const s = win(fresh({tierIndex: 5, lp: 999}));
    expect(s.lp).toBe(1019); expect(s.promotionActive).toBe(false); expect(s.tierIndex).toBe(5);
  });
  it('정책 값을 한곳에서 바꿀 수 있고 잘못된 설정은 거절한다', () => {
    const custom = {...config, promotionLp: 200, promotionBestOf: 5, promotionFailureLp: 150};
    expect(applyRankedResult(fresh({lp: 190}), fresh(), 1, custom).after.lp).toBe(200);
    expect(() => validateRankedConfig({...config, promotionBestOf: 2})).toThrow();
    expect(() => validateRankedConfig({...config, casualPriorWeight: 1})).toThrow();
    expect(() => validateRankedConfig({...config, promotionFailureLp: 100})).toThrow();
  });
});
