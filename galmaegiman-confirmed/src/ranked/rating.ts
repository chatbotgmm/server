// 수치는 승인 전 잠정 기본값. 배포 전 정책을 확정하고 설정 버전을 올립니다.
export type RankedConfig = {
  version: number;
  tiers: readonly string[];
  promotionLp: number;
  promotionBestOf: number;
  promotionFailureLp: number;
  demotionLp: number;
  winLp: number;
  lossLp: number;
  tierGapStep: number;
  tierGapCap: number;
  mmrBase: number;
  mmrK: number;
  mmrScale: number;
  mmrMin: number;
  mmrMax: number;
  casualPriorWeight: number;
  casualPriorMaxGap: number;
  casualPriorConfidenceGames: number;
  casualPriorFadeGames: number;
};

export const DEFAULT_RANKED_CONFIG: Readonly<RankedConfig> = Object.freeze({
  version: 1,
  tiers: Object.freeze(['Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond', 'Master']),
  promotionLp: 100,
  promotionBestOf: 3,
  promotionFailureLp: 75,
  demotionLp: 75,
  winLp: 20,
  lossLp: 15,
  tierGapStep: 3,
  tierGapCap: 2,
  mmrBase: 1000,
  mmrK: 32,
  mmrScale: 400,
  mmrMin: 0,
  mmrMax: 4000,
  casualPriorWeight: 0.25,
  casualPriorMaxGap: 200,
  casualPriorConfidenceGames: 20,
  casualPriorFadeGames: 20,
});

export type RankedSnapshot = {
  tierIndex: number;
  lp: number;
  promotionActive: boolean;
  promotionWins: number;
  promotionLosses: number;
  rankedMmr: number;
  casualPriorOffset: number;
  games: number;
  wins: number;
  losses: number;
  draws: number;
};

export type PromotionChange = 'NONE' | 'STARTED' | 'WON_GAME' | 'LOST_GAME' | 'PROMOTED' | 'FAILED' | 'DEMOTED';
export type RankedChange = {
  before: RankedSnapshot;
  after: RankedSnapshot;
  lpDelta: number;
  mmrDelta: number;
  promotionChange: PromotionChange;
};

const clamp = (n: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, n));
const rounded = (n: number) => Math.round(n * 1_000_000) / 1_000_000;

export function validateRankedConfig(config: RankedConfig): RankedConfig {
  const integerFields = ['version', 'promotionLp', 'promotionBestOf', 'promotionFailureLp', 'demotionLp',
    'winLp', 'lossLp', 'tierGapStep', 'tierGapCap', 'casualPriorConfidenceGames', 'casualPriorFadeGames'] as const;
  for (const key of integerFields) {
    if (!Number.isSafeInteger(config[key]) || config[key] < 0) throw new Error(`잘못된 랭크 설정: ${key}`);
  }
  for (const key of ['mmrBase', 'mmrK', 'mmrScale', 'mmrMin', 'mmrMax', 'casualPriorWeight', 'casualPriorMaxGap'] as const) {
    if (!Number.isFinite(config[key])) throw new Error(`잘못된 랭크 설정: ${key}`);
  }
  if (!Array.isArray(config.tiers) || config.tiers.length < 2 || config.tiers[0] !== 'Bronze'
      || config.tiers.some(t => typeof t !== 'string' || t.length === 0) || new Set(config.tiers).size !== config.tiers.length
      || config.version < 1 || config.promotionLp < 1 || config.promotionBestOf < 1 || config.promotionBestOf % 2 !== 1
      || config.promotionFailureLp >= config.promotionLp || config.demotionLp >= config.promotionLp
      || config.winLp <= config.tierGapStep * config.tierGapCap || config.lossLp <= config.tierGapStep * config.tierGapCap
      || config.mmrMin >= config.mmrMax || config.mmrBase < config.mmrMin || config.mmrBase > config.mmrMax
      || config.mmrK <= 0 || config.mmrScale <= 0 || config.casualPriorMaxGap < 0 || config.casualPriorMaxGap > 200
      || config.casualPriorWeight < 0 || config.casualPriorWeight > 0.25
      || config.casualPriorConfidenceGames < 1 || config.casualPriorFadeGames < 1) {
    throw new Error('랭크 설정의 범위 또는 상호 조건이 올바르지 않습니다.');
  }
  return config;
}

export function assertRankedSnapshot(s: RankedSnapshot, c: RankedConfig = DEFAULT_RANKED_CONFIG): void {
  for (const key of ['tierIndex', 'lp', 'promotionWins', 'promotionLosses', 'games', 'wins', 'losses', 'draws'] as const) {
    if (!Number.isSafeInteger(s[key]) || s[key] < 0) throw new Error(`잘못된 랭크 상태: ${key}`);
  }
  const needed = Math.floor(c.promotionBestOf / 2) + 1;
  if (s.tierIndex >= c.tiers.length || !Number.isFinite(s.rankedMmr) || !Number.isFinite(s.casualPriorOffset)
      || s.rankedMmr < c.mmrMin || s.rankedMmr > c.mmrMax || s.games !== s.wins + s.losses + s.draws
      || (s.promotionActive && (s.tierIndex === c.tiers.length - 1 || s.lp !== c.promotionLp
        || s.promotionWins >= needed || s.promotionLosses >= needed))
      || (!s.promotionActive && (s.promotionWins !== 0 || s.promotionLosses !== 0))
      || (!s.promotionActive && s.tierIndex < c.tiers.length - 1 && s.lp >= c.promotionLp)) {
    throw new Error('랭크 상태가 설정 또는 누적 기록과 맞지 않습니다. 자동 수정하지 않습니다.');
  }
}

/** 과거 Player.rankPoint/AI 전적은 입력으로 받지 않습니다. 새 사람 일반전 기록만 사용합니다. */
export function casualPriorOffset(mmr: number, games: number, c: RankedConfig = DEFAULT_RANKED_CONFIG): number {
  if (!Number.isFinite(mmr) || !Number.isSafeInteger(games) || games < 0) throw new Error('잘못된 일반전 레이팅');
  const confidence = clamp(games / c.casualPriorConfidenceGames, 0, 1);
  return rounded(clamp(mmr - c.mmrBase, -c.casualPriorMaxGap, c.casualPriorMaxGap) * c.casualPriorWeight * confidence);
}

export function initialRankedSnapshot(casual?: {mmr: number; games: number}, c: RankedConfig = DEFAULT_RANKED_CONFIG): RankedSnapshot {
  validateRankedConfig(c);
  return {
    tierIndex: 0, lp: 0, promotionActive: false, promotionWins: 0, promotionLosses: 0,
    rankedMmr: c.mmrBase, casualPriorOffset: casual ? casualPriorOffset(casual.mmr, casual.games, c) : 0,
    games: 0, wins: 0, losses: 0, draws: 0,
  };
}

/** 첫 랭크 정산 후 동결된 사전값은 랭크 20경기 후 완전히 사라집니다. */
export function rankedMatchmakingMmr(s: RankedSnapshot, c: RankedConfig = DEFAULT_RANKED_CONFIG): number {
  const fade = clamp(1 - s.games / c.casualPriorFadeGames, 0, 1);
  return rounded(clamp(s.rankedMmr + s.casualPriorOffset * fade, c.mmrMin, c.mmrMax));
}

export function eloDelta(selfMmr: number, otherMmr: number, score: 0 | 0.5 | 1, c: RankedConfig = DEFAULT_RANKED_CONFIG): number {
  const expected = 1 / (1 + 10 ** ((otherMmr - selfMmr) / c.mmrScale));
  return rounded(c.mmrK * (score - expected));
}

/** LP는 오직 승패와 양쪽의 기존 티어 차이를 사용합니다. MMR/일반전 기록은 참조하지 않습니다. */
export function lpForResult(selfTier: number, otherTier: number, won: boolean, c: RankedConfig = DEFAULT_RANKED_CONFIG): number {
  const gap = clamp(otherTier - selfTier, -c.tierGapCap, c.tierGapCap);
  return won ? c.winLp + gap * c.tierGapStep : -(c.lossLp - gap * c.tierGapStep);
}

export function applyRankedResult(self: RankedSnapshot, other: RankedSnapshot, score: 0 | 0.5 | 1,
  c: RankedConfig = DEFAULT_RANKED_CONFIG): RankedChange {
  validateRankedConfig(c);
  assertRankedSnapshot(self, c);
  assertRankedSnapshot(other, c);
  const before = {...self};
  const after = {...self, games: self.games + 1};
  after.rankedMmr = rounded(clamp(self.rankedMmr + eloDelta(rankedMatchmakingMmr(self, c), rankedMatchmakingMmr(other, c), score, c), c.mmrMin, c.mmrMax));
  let promotionChange: PromotionChange = 'NONE';
  if (score === 0.5) {
    after.draws++;
  } else {
    const won = score === 1;
    if (won) after.wins++; else after.losses++;
    if (self.promotionActive) {
      if (won) { after.promotionWins++; promotionChange = 'WON_GAME'; }
      else { after.promotionLosses++; promotionChange = 'LOST_GAME'; }
      const needed = Math.floor(c.promotionBestOf / 2) + 1;
      if (after.promotionWins >= needed || after.promotionLosses >= needed) {
        const promoted = after.promotionWins >= needed;
        after.tierIndex += promoted ? 1 : 0;
        after.lp = promoted ? 0 : c.promotionFailureLp;
        after.promotionActive = false;
        after.promotionWins = after.promotionLosses = 0;
        promotionChange = promoted ? 'PROMOTED' : 'FAILED';
      }
    } else if (!won && self.lp === 0 && self.tierIndex > 0) {
      after.tierIndex--;
      after.lp = c.demotionLp;
      promotionChange = 'DEMOTED';
    } else {
      after.lp = Math.max(0, self.lp + lpForResult(self.tierIndex, other.tierIndex, won, c));
      if (after.lp >= c.promotionLp && self.tierIndex < c.tiers.length - 1) {
        after.lp = c.promotionLp;
        after.promotionActive = true;
        after.promotionWins = after.promotionLosses = 0;
        promotionChange = 'STARTED';
      }
    }
  }
  assertRankedSnapshot(after, c);
  return {before, after, lpDelta: after.lp - before.lp, mmrDelta: rounded(after.rankedMmr - before.rankedMmr), promotionChange};
}

export function unchangedRankedResult(s: RankedSnapshot): RankedChange {
  return {before: {...s}, after: {...s}, lpDelta: 0, mmrDelta: 0, promotionChange: 'NONE'};
}
