import { Prisma, type PrismaClient, type RankedProfile, type RankedMatch, type CasualHumanRating } from '@prisma/client';
import {
  DEFAULT_RANKED_CONFIG, applyRankedResult, assertRankedSnapshot, eloDelta,
  initialRankedSnapshot, rankedMatchmakingMmr, unchangedRankedResult, validateRankedConfig,
  type RankedConfig, type RankedSnapshot, type RankedChange,
} from './rating.js';

type Tx = Prisma.TransactionClient;
export type MatchWinner = 0 | 1 | null;
export type FinishReason = 'BATTLE' | 'SURRENDER' | 'TIMEOUT' | 'DOUBLE_TIMEOUT' | 'SERVER_ABORT';
type HumanPair = {
  roomId: string;
  player0Id: string;
  player1Id: string;
  participant0Kind: 'HUMAN';
  participant1Kind: 'HUMAN';
};
type Finish = {roomId: string; winner: MatchWinner; reason: FinishReason; now?: Date};
export type RankedReceipt = {
  formatVersion: 1;
  configVersion: number;
  roomId: string;
  winner: MatchWinner;
  reason: FinishReason;
  voided: boolean;
  finishedAt: string;
  players: [RankedChange & {playerId: string}, RankedChange & {playerId: string}];
  replayed: boolean;
};
type CasualSnapshot = {mmr: number; games: number; wins: number; losses: number; draws: number};
export type CasualReceipt = {
  formatVersion: 1;
  roomId: string;
  winner: MatchWinner;
  reason: FinishReason;
  voided: boolean;
  players: [{playerId: string; before: CasualSnapshot; after: CasualSnapshot; lpDelta: 0; mmrDelta: number}, {playerId: string; before: CasualSnapshot; after: CasualSnapshot; lpDelta: 0; mmrDelta: number}];
  replayed: boolean;
};

export class RankedConcurrentWriteError extends Error {
  readonly code = 'RANKED_CONCURRENT_WRITE';
  constructor() { super('랭크 상태가 다른 요청에서 변경됐습니다. 전체 트랜잭션을 다시 실행하세요.'); }
}

const reasons = new Set<FinishReason>(['BATTLE', 'SURRENDER', 'TIMEOUT', 'DOUBLE_TIMEOUT', 'SERVER_ABORT']);
const isVoided = (reason: FinishReason) => reason === 'DOUBLE_TIMEOUT' || reason === 'SERVER_ABORT';
const round = (n: number) => Math.round(n * 1_000_000) / 1_000_000;
const scoreFor = (winner: MatchWinner, index: 0 | 1): 0 | 0.5 | 1 => winner === null ? 0.5 : winner === index ? 1 : 0;

function assertFinish(winner: unknown, reason: unknown): asserts reason is FinishReason {
  if (winner !== null && winner !== 0 && winner !== 1) throw new Error('잘못된 승자');
  if (!reasons.has(reason as FinishReason)) throw new Error('잘못된 종료 사유');
  if (isVoided(reason as FinishReason) && winner !== null) throw new Error('무효 경기는 승자를 가질 수 없습니다.');
  if ((reason === 'SURRENDER' || reason === 'TIMEOUT') && winner === null) throw new Error('단일 기권/시간초과에는 승자가 필요합니다.');
}

function assertHumanPair(pair: HumanPair): void {
  if (typeof pair.roomId !== 'string' || !pair.roomId || pair.roomId.length > 160
      || typeof pair.player0Id !== 'string' || !pair.player0Id
      || typeof pair.player1Id !== 'string' || !pair.player1Id
      || pair.player0Id === pair.player1Id
      || pair.participant0Kind !== 'HUMAN' || pair.participant1Kind !== 'HUMAN') {
    throw new Error('레이팅은 서로 다른 인증된 사람 두 명의 서버 경기만 허용합니다.');
  }
}

async function assertPlayersExist(tx: Tx, ids: readonly string[]): Promise<void> {
  const count = await tx.player.count({where: {id: {in: [...new Set(ids)]}}});
  if (count !== new Set(ids).size) throw new Error('존재하지 않는 플레이어입니다.');
}

function snapshot(row: RankedProfile): RankedSnapshot {
  return {
    tierIndex: row.tierIndex, lp: row.lp, promotionActive: row.promotionActive,
    promotionWins: row.promotionWins, promotionLosses: row.promotionLosses,
    rankedMmr: row.rankedMmr, casualPriorOffset: row.casualPriorOffset,
    games: row.games, wins: row.wins, losses: row.losses, draws: row.draws,
  };
}

function casualSnapshot(row: CasualHumanRating): CasualSnapshot {
  return {mmr: row.mmr, games: row.games, wins: row.wins, losses: row.losses, draws: row.draws};
}

function assertCasualSnapshot(row: CasualHumanRating): void {
  const c = DEFAULT_RANKED_CONFIG;
  if (!Number.isFinite(row.mmr) || row.mmr < c.mmrMin || row.mmr > c.mmrMax
      || [row.games, row.wins, row.losses, row.draws].some(n => !Number.isSafeInteger(n) || n < 0)
      || row.games !== row.wins + row.losses + row.draws) throw new Error('손상된 일반전 레이팅입니다.');
}

function configOf(match: RankedMatch): RankedConfig {
  return validateRankedConfig(JSON.parse(match.configJson) as RankedConfig);
}

function serializeConfig(config: RankedConfig): string {
  // 입력 객체 키 순서가 달라도 같은 설정은 같은 문자열입니다.
  const keys = Object.keys(DEFAULT_RANKED_CONFIG).sort() as (keyof RankedConfig)[];
  return JSON.stringify(Object.fromEntries(keys.map(key => [key, config[key]])));
}

function readRankedReceipt(text: string, roomId: string): RankedReceipt {
  const receipt = JSON.parse(text) as RankedReceipt;
  if (receipt.formatVersion !== 1 || receipt.roomId !== roomId || !Array.isArray(receipt.players) || receipt.players.length !== 2) {
    throw new Error('손상되거나 알 수 없는 랭크 정산 영수증입니다.');
  }
  assertFinish(receipt.winner, receipt.reason);
  return {...receipt, replayed: true};
}

export async function ensureRankedProfile(tx: Tx, playerId: string,
  config: RankedConfig = DEFAULT_RANKED_CONFIG): Promise<RankedProfile> {
  validateRankedConfig(config);
  await assertPlayersExist(tx, [playerId]);
  const found = await tx.rankedProfile.findUnique({where: {playerId}});
  if (found) { assertRankedSnapshot(snapshot(found), config); return found; }
  const casual = await tx.casualHumanRating.findUnique({where: {playerId}});
  if (casual) assertCasualSnapshot(casual);
  // 기존 Player의 rankPoint/rankWins/rankLosses를 절대 읽거나 이관하지 않습니다.
  return tx.rankedProfile.create({data: {playerId, ...initialRankedSnapshot(casual ?? undefined, config)}});
}

async function ensureCasualProfile(tx: Tx, playerId: string): Promise<CasualHumanRating> {
  await assertPlayersExist(tx, [playerId]);
  const found = await tx.casualHumanRating.findUnique({where: {playerId}});
  if (found) { assertCasualSnapshot(found); return found; }
  return tx.casualHumanRating.create({data: {playerId, mmr: DEFAULT_RANKED_CONFIG.mmrBase}});
}

export async function matchmakingRating(tx: Tx, playerId: string, mode: 'normal' | 'ranked'): Promise<number> {
  if (mode === 'normal') return (await ensureCasualProfile(tx, playerId)).mmr;
  if (mode === 'ranked') {
    const profile = await ensureRankedProfile(tx, playerId);
    const current = snapshot(profile);
    // 로비 조회가 프로필을 먼저 만들었어도 첫 랭크 경기 전까지는 최신 일반전 사전값을 사용합니다.
    // 첫 랭크 경기 정산 이후 games > 0이면 동결하며 LP/승급전/기본 랭크 MMR은 건드리지 않습니다.
    if (profile.games === 0) {
      const casual = await tx.casualHumanRating.findUnique({where: {playerId}});
      if (casual) assertCasualSnapshot(casual);
      const offset = initialRankedSnapshot(casual ?? undefined).casualPriorOffset;
      if (offset !== profile.casualPriorOffset) {
        const changed = await tx.rankedProfile.updateMany({
          where: {playerId, version: profile.version, games: 0},
          data: {casualPriorOffset: offset, version: {increment: 1}},
        });
        if (changed.count !== 1) throw new RankedConcurrentWriteError();
        current.casualPriorOffset = offset;
      }
    }
    return rankedMatchmakingMmr(current);
  }
  throw new Error('알 수 없는 매칭 모드');
}

/** 서버가 매칭한 두 사람을 등록. 반드시 실시간 방 생성과 같은 tx 안에서 호출합니다. */
export async function registerRankedMatch(tx: Tx, input: HumanPair & {config?: RankedConfig}): Promise<RankedMatch> {
  assertHumanPair(input);
  const config = validateRankedConfig(input.config ?? DEFAULT_RANKED_CONFIG);
  const configJson = serializeConfig(config);
  const existing = await tx.rankedMatch.findUnique({where: {roomId: input.roomId}});
  if (existing) {
    if (existing.player0Id !== input.player0Id || existing.player1Id !== input.player1Id
        || existing.participant0Kind !== 'HUMAN' || existing.participant1Kind !== 'HUMAN'
        || existing.configJson !== configJson) throw new Error('이미 다른 참가자/설정으로 등록된 방입니다.');
    return existing;
  }
  if (await tx.casualHumanSettlement.findUnique({where: {roomId: input.roomId}})) throw new Error('일반전 방을 랭크전으로 바꿀 수 없습니다.');
  await assertPlayersExist(tx, [input.player0Id, input.player1Id]);
  await ensureRankedProfile(tx, input.player0Id, config);
  await ensureRankedProfile(tx, input.player1Id, config);
  return tx.rankedMatch.create({data: {
    roomId: input.roomId, player0Id: input.player0Id, player1Id: input.player1Id,
    participant0Kind: 'HUMAN', participant1Kind: 'HUMAN', configJson,
  }});
}

async function updateRanked(tx: Tx, row: RankedProfile, after: RankedSnapshot): Promise<void> {
  const changed = await tx.rankedProfile.updateMany({
    where: {playerId: row.playerId, version: row.version},
    data: {...after, version: {increment: 1}},
  });
  if (changed.count !== 1) throw new RankedConcurrentWriteError();
}

/** 클라이언트 요청에서 winner를 받지 말고 서버 엔진의 확정 결과만 전달하세요. */
export async function finishAndSettleRankedMatch(tx: Tx, input: Finish): Promise<RankedReceipt> {
  assertFinish(input.winner, input.reason);
  const match = await tx.rankedMatch.findUniqueOrThrow({where: {roomId: input.roomId}});
  if (match.state === 'FINISHED') {
    if (match.winner !== input.winner || match.reason !== input.reason) throw new Error('이미 확정된 경기 결과와 다릅니다.');
  } else if (match.state === 'ACTIVE') {
    const updated = await tx.rankedMatch.updateMany({
      where: {roomId: input.roomId, state: 'ACTIVE', version: match.version},
      data: {state: 'FINISHED', winner: input.winner, reason: input.reason, finishedAt: input.now ?? new Date(), version: {increment: 1}},
    });
    if (updated.count !== 1) throw new RankedConcurrentWriteError();
  } else throw new Error('정산할 수 없는 경기 상태');
  // 위 terminal 갱신, 두 프로필, 영수증이 하나의 외부 tx 안에서 함께 commit됩니다.
  return settleTerminalRankedMatch(tx, input.roomId);
}

export async function settleTerminalRankedMatch(tx: Tx, roomId: string): Promise<RankedReceipt> {
  const match = await tx.rankedMatch.findUniqueOrThrow({where: {roomId}});
  assertHumanPair({...match, participant0Kind: match.participant0Kind as 'HUMAN', participant1Kind: match.participant1Kind as 'HUMAN'});
  if (match.state !== 'FINISHED' || !match.finishedAt) throw new Error('서버가 종료를 확정하지 않은 경기입니다.');
  assertFinish(match.winner, match.reason);
  const winner = match.winner as MatchWinner;
  const reason = match.reason;
  const existing = await tx.rankedSettlement.findUnique({where: {roomId}});
  if (existing) {
    const receipt = readRankedReceipt(existing.receiptJson, roomId);
    if (receipt.winner !== winner || receipt.reason !== reason
        || receipt.players[0].playerId !== match.player0Id || receipt.players[1].playerId !== match.player1Id) {
      throw new Error('경기와 정산 영수증이 일치하지 않습니다.');
    }
    return receipt;
  }
  const config = configOf(match);
  await assertPlayersExist(tx, [match.player0Id, match.player1Id]);
  // 고유 roomId를 먼저 점유합니다. 이후 오류가 나면 '{}'도 함께 rollback됩니다.
  await tx.rankedSettlement.create({data: {roomId, receiptJson: '{}'}});
  const p0 = await tx.rankedProfile.findUniqueOrThrow({where: {playerId: match.player0Id}});
  const p1 = await tx.rankedProfile.findUniqueOrThrow({where: {playerId: match.player1Id}});
  const s0 = snapshot(p0), s1 = snapshot(p1);
  assertRankedSnapshot(s0, config); assertRankedSnapshot(s1, config);
  const voided = isVoided(reason);
  const c0 = voided ? unchangedRankedResult(s0) : applyRankedResult(s0, s1, scoreFor(winner, 0), config);
  const c1 = voided ? unchangedRankedResult(s1) : applyRankedResult(s1, s0, scoreFor(winner, 1), config);
  if (!voided) {
    await updateRanked(tx, p0, c0.after);
    await updateRanked(tx, p1, c1.after);
  }
  const receipt: RankedReceipt = {
    formatVersion: 1, configVersion: config.version, roomId, winner, reason, voided,
    finishedAt: match.finishedAt.toISOString(),
    players: [{playerId: p0.playerId, ...c0}, {playerId: p1.playerId, ...c1}], replayed: false,
  };
  await tx.rankedSettlement.update({where: {roomId}, data: {receiptJson: JSON.stringify(receipt)}});
  return receipt;
}

/** AI 방에서는 호출하지 않습니다. 전달된 종류도 런타임에 다시 검증합니다. */
export async function settleCasualHuman(tx: Tx, input: HumanPair & Finish): Promise<CasualReceipt> {
  assertHumanPair(input); assertFinish(input.winner, input.reason);
  if (await tx.rankedMatch.findUnique({where: {roomId: input.roomId}})) throw new Error('랭크 방을 일반전으로 정산할 수 없습니다.');
  const existing = await tx.casualHumanSettlement.findUnique({where: {roomId: input.roomId}});
  if (existing) {
    const receipt = JSON.parse(existing.receiptJson) as CasualReceipt;
    if (receipt.formatVersion !== 1 || receipt.roomId !== input.roomId || receipt.winner !== input.winner || receipt.reason !== input.reason
        || existing.player0Id !== input.player0Id || existing.player1Id !== input.player1Id
        || receipt.players?.[0]?.playerId !== input.player0Id || receipt.players?.[1]?.playerId !== input.player1Id) {
      throw new Error('이미 정산한 일반전의 참가자/결과가 다릅니다.');
    }
    return {...receipt, replayed: true};
  }
  await assertPlayersExist(tx, [input.player0Id, input.player1Id]);
  await tx.casualHumanSettlement.create({data: {
    roomId: input.roomId, player0Id: input.player0Id, player1Id: input.player1Id,
    winner: input.winner, receiptJson: '{}', createdAt: input.now ?? new Date(),
  }});
  const p0 = await ensureCasualProfile(tx, input.player0Id);
  const p1 = await ensureCasualProfile(tx, input.player1Id);
  const before0 = casualSnapshot(p0), before1 = casualSnapshot(p1);
  const voided = isVoided(input.reason);
  const c = DEFAULT_RANKED_CONFIG;
  const next = (self: CasualSnapshot, other: CasualSnapshot, index: 0 | 1): CasualSnapshot => {
    if (voided) return {...self};
    const score = scoreFor(input.winner, index);
    return {
      mmr: round(Math.max(c.mmrMin, Math.min(c.mmrMax, self.mmr + eloDelta(self.mmr, other.mmr, score, c)))),
      games: self.games + 1, wins: self.wins + (score === 1 ? 1 : 0),
      losses: self.losses + (score === 0 ? 1 : 0), draws: self.draws + (score === 0.5 ? 1 : 0),
    };
  };
  const after0 = next(before0, before1, 0), after1 = next(before1, before0, 1);
  if (!voided) {
    for (const [row, after] of [[p0, after0], [p1, after1]] as const) {
      const result = await tx.casualHumanRating.updateMany({where: {playerId: row.playerId, version: row.version}, data: {...after, version: {increment: 1}}});
      if (result.count !== 1) throw new RankedConcurrentWriteError();
    }
  }
  const receipt: CasualReceipt = {
    formatVersion: 1, roomId: input.roomId, winner: input.winner, reason: input.reason, voided,
    players: [{playerId: p0.playerId, before: before0, after: after0, lpDelta: 0, mmrDelta: round(after0.mmr - before0.mmr)},
      {playerId: p1.playerId, before: before1, after: after1, lpDelta: 0, mmrDelta: round(after1.mmr - before1.mmr)}], replayed: false,
  };
  await tx.casualHumanSettlement.update({where: {roomId: input.roomId}, data: {receiptJson: JSON.stringify(receipt)}});
  return receipt;
}

export async function recordCasualHumanMatch(tx: Tx, input: HumanPair & {winner: MatchWinner; now?: Date}): Promise<CasualReceipt> {
  return settleCasualHuman(tx, {...input, reason: 'BATTLE'});
}

/** 서비스도 같은 방식으로 전체 방 트랜잭션을 재시도할 수 있습니다. */
export async function withRankedTransaction<T>(db: PrismaClient, work: (tx: Tx) => Promise<T>): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    try { return await db.$transaction(work, {isolationLevel: Prisma.TransactionIsolationLevel.Serializable, maxWait: 5000, timeout: 10000}); }
    catch (error) {
      const code = error && typeof error === 'object' && 'code' in error ? String(error.code) : '';
      // P2002: 신규 프로필/고유 영수증의 경합. 재실행 시 같은 방의 저장 영수증을 읽습니다.
      if (attempt >= 4 || !['RANKED_CONCURRENT_WRITE', 'P2034', 'P1008', 'P2028', 'P2002'].includes(code)) throw error;
      await new Promise(resolve => setTimeout(resolve, 20 * 2 ** attempt + Math.floor(Math.random() * 20)));
    }
  }
}

export async function repairUnsettledRankedMatches(db: PrismaClient, limit = 100): Promise<{repaired: string[]; failed: {roomId: string; error: string}[]}> {
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 1000) throw new Error('복구 한도는 1~1000');
  // LEFT JOIN으로 아직 정산되지 않은 terminal만 찾습니다. 이미 끝난 방의 반복 스캔을 피합니다.
  const pending = await db.$queryRaw<{roomId: string}[]>(Prisma.sql`
    SELECT m."roomId" FROM "RankedMatch" m
    LEFT JOIN "RankedSettlement" s ON s."roomId" = m."roomId"
    WHERE m."state" = 'FINISHED' AND s."roomId" IS NULL
    ORDER BY m."finishedAt" ASC, m."roomId" ASC LIMIT ${limit}
  `);
  const repaired: string[] = [], failed: {roomId: string; error: string}[] = [];
  for (const {roomId} of pending) {
    try { await withRankedTransaction(db, tx => settleTerminalRankedMatch(tx, roomId)); repaired.push(roomId); }
    catch (error) { failed.push({roomId, error: error instanceof Error ? error.message : String(error)}); }
  }
  return {repaired, failed};
}

export async function rankSummary(tx: Tx, playerId: string) {
  const row = await ensureRankedProfile(tx, playerId);
  const c = DEFAULT_RANKED_CONFIG;
  const names = await tx.player.findUniqueOrThrow({where: {id: playerId}, select: {nickname: true}});
  // 삭제된 Player의 잔여 프로필은 공개 랭킹에서 제외합니다. 기존 스키마와의 FK 충돌은 피합니다.
  const rows = await tx.$queryRaw<{playerId: string; nickname: string | null; tierIndex: number | bigint; lp: number | bigint; wins: number | bigint; losses: number | bigint}[]>(Prisma.sql`
    SELECT r."playerId", p."nickname", r."tierIndex", r."lp", r."wins", r."losses"
    FROM "RankedProfile" r INNER JOIN "Player" p ON p."id" = r."playerId"
    WHERE r."games" > 0
    ORDER BY r."tierIndex" DESC, r."lp" DESC, r."wins" DESC, r."playerId" ASC LIMIT 20
  `);
  const rankRows = await tx.$queryRaw<{ahead: bigint | number}[]>(Prisma.sql`
    SELECT COUNT(*) AS "ahead" FROM "RankedProfile" r INNER JOIN "Player" p ON p."id" = r."playerId"
    WHERE r."games" > 0 AND (r."tierIndex" > ${row.tierIndex}
      OR (r."tierIndex" = ${row.tierIndex} AND r."lp" > ${row.lp})
      OR (r."tierIndex" = ${row.tierIndex} AND r."lp" = ${row.lp} AND r."wins" > ${row.wins})
      OR (r."tierIndex" = ${row.tierIndex} AND r."lp" = ${row.lp} AND r."wins" = ${row.wins} AND r."playerId" < ${row.playerId}))
  `);
  // SQLite raw query의 정수 매핑은 드라이버에 따라 bigint일 수 있습니다.
  // JSON 응답에는 검증한 안전한 number만 내보냅니다. Prisma 모델 조회값은 Int → number입니다.
  const publicInteger = (value: number | bigint, field: string): number => {
    const n = Number(value);
    if (!Number.isSafeInteger(n) || n < 0) throw new Error(`공개 랭킹의 ${field} 값이 안전한 정수가 아닙니다.`);
    return n;
  };
  const top = rows.map((r, i) => {
    const tierIndex = publicInteger(r.tierIndex, 'tierIndex');
    if (tierIndex >= c.tiers.length) throw new Error('공개 랭킹의 티어가 현재 설정 범위 밖입니다.');
    return {rank: i + 1, playerId: r.playerId, nickname: r.nickname ?? '이름 없는 갈매미',
      point: publicInteger(r.lp, 'lp'), tier: c.tiers[tierIndex], tierIndex,
      wins: publicInteger(r.wins, 'wins'), losses: publicInteger(r.losses, 'losses')};
  });
  const ahead = publicInteger(rankRows[0]?.ahead ?? 0, 'ahead');
  if (!Number.isSafeInteger(ahead + 1)) throw new Error('공개 순위가 안전한 정수 범위를 넘었습니다.');
  return {
    me: {
      point: row.lp, tier: c.tiers[row.tierIndex], tierIndex: row.tierIndex, nickname: names.nickname,
      wins: row.wins, losses: row.losses, draws: row.draws, rank: row.games > 0 ? ahead + 1 : null,
      promotion: {active: row.promotionActive, wins: row.promotionWins, losses: row.promotionLosses,
        requiredWins: Math.floor(c.promotionBestOf / 2) + 1, maxGames: c.promotionBestOf},
    },
    win: c.winLp, lose: c.lossLp,
    top,
    tiers: c.tiers.map((name, index) => ({name, index, promotionLp: index === c.tiers.length - 1 ? null : c.promotionLp})),
  };
}
