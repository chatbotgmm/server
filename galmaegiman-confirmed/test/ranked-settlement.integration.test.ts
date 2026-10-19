import {afterAll, beforeAll, beforeEach, describe, expect, it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {finishAndSettleRankedMatch, matchmakingRating, rankSummary, registerRankedMatch,
  repairUnsettledRankedMatches, settleCasualHuman, settleTerminalRankedMatch,
  withRankedTransaction} from '../src/ranked/settlement.js';

// 기존 scripts/test-db.mjs가 만든 전용 임시 DB에서만 활성화합니다.
const suite = process.env.TEST_DB_URL ? describe : describe.skip;
suite('실제 SQLite: 랭크 원자성·재시도·일반전 격리', () => {
  let db: PrismaClient, second: PrismaClient;
  let player0Id: string, player1Id: string;
  const createdPlayers: string[] = [];
  const roomIds: string[] = [];
  const nextRoom = () => { const id = `ranked-test-${randomUUID()}`; roomIds.push(id); return id; };
  const pair = (roomId: string) => ({roomId, player0Id, player1Id, participant0Kind: 'HUMAN' as const, participant1Kind: 'HUMAN' as const});
  const register = (roomId: string) => withRankedTransaction(db, tx => registerRankedMatch(tx, pair(roomId)));
  const finish = (roomId: string, winner: 0 | 1 | null = 0) => withRankedTransaction(db, tx => finishAndSettleRankedMatch(tx, {roomId, winner, reason: 'BATTLE'}));
  const profiles = () => db.rankedProfile.findMany({where: {playerId: {in: [player0Id, player1Id]}}, orderBy: {playerId: 'asc'}});
  beforeAll(() => {
    const url = process.env.TEST_DB_URL!;
    if (!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url)) throw new Error('격리된 테스트 DB가 아니므로 중단합니다.');
    db = new PrismaClient({datasources: {db: {url}}});
    second = new PrismaClient({datasources: {db: {url}}});
  });
  beforeEach(async () => {
    player0Id = (await db.player.create({data: {identity: `ranked-test-a-${randomUUID()}`, rankPoint: 3000, rankWins: 99}})).id;
    player1Id = (await db.player.create({data: {identity: `ranked-test-b-${randomUUID()}`, rankPoint: 500}})).id;
    createdPlayers.push(player0Id, player1Id);
  });
  afterAll(async () => {
    if (!db) return;
    await db.$executeRawUnsafe('DROP TRIGGER IF EXISTS "ranked_test_second_player_abort"');
    await db.$transaction([
      db.rankedSettlement.deleteMany({where: {roomId: {in: roomIds}}}),
      db.rankedMatch.deleteMany({where: {roomId: {in: roomIds}}}),
      db.casualHumanSettlement.deleteMany({where: {roomId: {in: roomIds}}}),
      db.rankedProfile.deleteMany({where: {playerId: {in: createdPlayers}}}),
      db.casualHumanRating.deleteMany({where: {playerId: {in: createdPlayers}}}),
      db.player.deleteMany({where: {id: {in: createdPlayers}}}),
    ]);
    await db.$disconnect(); await second.$disconnect();
  });

  it('기존 점수는 유지하고 신규 랭크는 모두 Bronze 0', async () => {
    const roomId = nextRoom(); await register(roomId);
    expect((await profiles()).every(p => p.lp === 0 && p.tierIndex === 0 && p.games === 0)).toBe(true);
    const legacy = await db.player.findUniqueOrThrow({where: {id: player0Id}});
    expect(legacy.rankPoint).toBe(3000); expect(legacy.rankWins).toBe(99);
  });
  it('한 번의 정산이 양쪽을 함께 반영하고 같은 호출은 원래 영수증을 반환', async () => {
    const roomId = nextRoom(); await register(roomId);
    const first = await finish(roomId); const beforeRetry = await profiles();
    const retry = await finish(roomId);
    expect(first.replayed).toBe(false); expect(retry.replayed).toBe(true);
    expect(retry.players).toEqual(first.players); expect(await profiles()).toEqual(beforeRetry);
    expect(first.players[0].after.lp).toBe(20); expect(first.players[1].after.lp).toBe(0);
    expect(first.players[0].after.wins).toBe(1); expect(first.players[1].after.losses).toBe(1);
    expect(await db.rankedSettlement.count({where: {roomId}})).toBe(1);
  });
  it('두 개의 DB 클라이언트가 동시에 끝내도 정확히 한 번 점수를 준다', async () => {
    const roomId = nextRoom(); await register(roomId);
    const results = await Promise.all([
      finish(roomId),
      withRankedTransaction(second, tx => finishAndSettleRankedMatch(tx, {roomId, winner: 0, reason: 'BATTLE'})),
    ]);
    expect(results.every(r => r.players[0].after.wins === 1)).toBe(true);
    expect((await profiles()).every(p => p.games === 1)).toBe(true);
    expect(await db.rankedSettlement.count({where: {roomId}})).toBe(1);
  });
  it('첫 플레이어 갱신 뒤 두 번째 DB 쓰기가 실패하면 terminal·두 점수·영수증 모두 rollback', async () => {
    const roomId = nextRoom(); await register(roomId);
    const before = await profiles();
    // server-generated UUID만 사용. 테스트 DB의 일회성 실패 주입입니다.
    if (!/^[0-9a-f-]+$/i.test(player1Id)) throw new Error('예상하지 못한 UUID');
    await db.$executeRawUnsafe(`CREATE TRIGGER "ranked_test_second_player_abort" BEFORE UPDATE ON "RankedProfile"
      WHEN NEW."playerId" = '${player1Id}' BEGIN SELECT RAISE(ABORT, 'forced-second-player-failure'); END`);
    try { await expect(finish(roomId)).rejects.toThrow(); }
    finally { await db.$executeRawUnsafe('DROP TRIGGER "ranked_test_second_player_abort"'); }
    expect(await profiles()).toEqual(before);
    expect((await db.rankedMatch.findUniqueOrThrow({where: {roomId}})).state).toBe('ACTIVE');
    expect(await db.rankedSettlement.count({where: {roomId}})).toBe(0);
    expect((await finish(roomId)).players[0].after.wins).toBe(1);
  });
  it('호출자 트랜잭션이 마지막에 실패해도 전부 rollback', async () => {
    const roomId = nextRoom(); await register(roomId); const before = await profiles();
    await expect(db.$transaction(async tx => {
      await finishAndSettleRankedMatch(tx, {roomId, winner: 0, reason: 'BATTLE'});
      throw new Error('room-save-failure');
    })).rejects.toThrow('room-save-failure');
    expect(await profiles()).toEqual(before);
    expect(await db.rankedSettlement.count({where: {roomId}})).toBe(0);
  });
  it('다른 승자/다른 참가자로 재사용한 방 ID는 거절', async () => {
    const roomId = nextRoom(); await register(roomId); await finish(roomId);
    await expect(finish(roomId, 1)).rejects.toThrow('이미 확정');
    await expect(withRankedTransaction(db, tx => registerRankedMatch(tx, {...pair(roomId), player0Id: player1Id, player1Id: player0Id}))).rejects.toThrow();
  });
  it('AI·자기 자신·미존재 계정은 랭크 등록 및 일반전 MMR 정산 불가', async () => {
    const roomId = nextRoom();
    await expect(withRankedTransaction(db, tx => registerRankedMatch(tx, {...pair(roomId), participant1Kind: 'AI' as 'HUMAN'}))).rejects.toThrow();
    await expect(withRankedTransaction(db, tx => registerRankedMatch(tx, {...pair(roomId), player1Id: player0Id}))).rejects.toThrow();
    await expect(withRankedTransaction(db, tx => registerRankedMatch(tx, {...pair(roomId), player1Id: 'missing-player'}))).rejects.toThrow();
    await expect(withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), participant1Kind: 'AI' as 'HUMAN', winner: 0, reason: 'BATTLE'}))).rejects.toThrow();
    expect(await db.rankedMatch.count({where: {roomId}})).toBe(0);
  });
  it('DB에 잘못 저장된 AI 랭크 방도 정산에서 다시 거절', async () => {
    const roomId = nextRoom(); await register(roomId);
    await db.rankedMatch.update({where: {roomId}, data: {participant1Kind: 'AI'}});
    await expect(finish(roomId)).rejects.toThrow();
    expect((await profiles()).every(p => p.games === 0)).toBe(true);
  });
  it('양쪽 시간초과/서버중단은 LP·MMR·게임 수·승급전을 변경하지 않는다', async () => {
    for (const reason of ['DOUBLE_TIMEOUT', 'SERVER_ABORT'] as const) {
      const roomId = nextRoom(); await register(roomId); const before = await profiles();
      const receipt = await withRankedTransaction(db, tx => finishAndSettleRankedMatch(tx, {roomId, winner: null, reason}));
      expect(receipt.voided).toBe(true); expect(await profiles()).toEqual(before);
      expect(await db.rankedSettlement.count({where: {roomId}})).toBe(1);
    }
  });
  it('사람 일반전은 별도 MMR만 올리고 기존 점수와 랭크 LP는 그대로', async () => {
    const rankRoom = nextRoom(); await register(rankRoom); const before = await profiles();
    const roomId = nextRoom();
    const receipt = await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), winner: 0, reason: 'BATTLE'}));
    expect(receipt.players[0].after.mmr).toBe(1016); expect(receipt.players[1].after.mmr).toBe(984);
    expect(await profiles()).toEqual(before);
    const retry = await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), winner: 0, reason: 'BATTLE'}));
    expect(retry.replayed).toBe(true); expect(retry.players).toEqual(receipt.players);
    expect((await db.player.findUniqueOrThrow({where: {id: player0Id}})).rankPoint).toBe(3000);
  });
  it('일반전 사전값은 첫 랭크 경기 정산 후 동결한다', async () => {
    const roomId = nextRoom();
    await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), winner: 0, reason: 'BATTLE'}));
    const value = await withRankedTransaction(db, tx => matchmakingRating(tx, player0Id, 'ranked'));
    expect(value).toBe(1000.2);
    const profile = await db.rankedProfile.findUniqueOrThrow({where: {playerId: player0Id}});
    expect(profile.lp).toBe(0); expect(profile.tierIndex).toBe(0);
    const rankedId = nextRoom(); await register(rankedId); await finish(rankedId);
    const frozenRating = await withRankedTransaction(db, tx => matchmakingRating(tx, player0Id, 'ranked'));
    const secondRoom = nextRoom();
    await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(secondRoom), winner: 0, reason: 'BATTLE'}));
    expect(await withRankedTransaction(db, tx => matchmakingRating(tx, player0Id, 'ranked'))).toBe(frozenRating);
    expect((await db.rankedProfile.findUniqueOrThrow({where: {playerId: player0Id}})).casualPriorOffset).toBe(profile.casualPriorOffset);
  });
  it('로비에서 0 사전값 프로필을 만든 뒤 일반전을 해도 첫 랭크 매칭은 최신 사전값을 쓴다', async () => {
    await withRankedTransaction(db, tx => rankSummary(tx, player0Id));
    const before = await db.rankedProfile.findUniqueOrThrow({where: {playerId: player0Id}});
    expect(before.casualPriorOffset).toBe(0);
    const roomId = nextRoom();
    await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), winner: 0, reason: 'BATTLE'}));
    expect(await withRankedTransaction(db, tx => matchmakingRating(tx, player0Id, 'ranked'))).toBe(1000.2);
    const after = await db.rankedProfile.findUniqueOrThrow({where: {playerId: player0Id}});
    expect(after.casualPriorOffset).toBe(0.2); expect(after.version).toBe(before.version + 1);
    expect(after).toMatchObject({tierIndex: 0, lp: 0, rankedMmr: 1000, games: 0,
      promotionActive: false, promotionWins: 0, promotionLosses: 0});
    // 변하지 않은 사전값의 재조회는 version을 올리지 않습니다.
    await withRankedTransaction(db, tx => matchmakingRating(tx, player0Id, 'ranked'));
    expect((await db.rankedProfile.findUniqueOrThrow({where: {playerId: player0Id}})).version).toBe(after.version);
  });
  it('일반전/랭크전 간 같은 방 ID 중복 정산을 거절', async () => {
    const roomId = nextRoom(); await register(roomId);
    await expect(withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(roomId), winner: 0, reason: 'BATTLE'}))).rejects.toThrow();
    const casualId = nextRoom();
    await withRankedTransaction(db, tx => settleCasualHuman(tx, {...pair(casualId), winner: 0, reason: 'BATTLE'}));
    await expect(register(casualId)).rejects.toThrow();
  });
  it('저장된 terminal을 재시작 후 복구하고 미종료 방은 정산하지 않는다', async () => {
    const roomId = nextRoom(); await register(roomId);
    await expect(withRankedTransaction(db, tx => settleTerminalRankedMatch(tx, roomId))).rejects.toThrow('종료');
    // 이전 버전/장애 복구 경로를 위한 내구성 있는 terminal fixture.
    await db.rankedMatch.update({where: {roomId}, data: {state: 'FINISHED', winner: 1, reason: 'BATTLE', finishedAt: new Date()}});
    const recovered = await repairUnsettledRankedMatches(second);
    expect(recovered.repaired).toContain(roomId); expect(recovered.failed).toEqual([]);
    expect((await db.rankedProfile.findUniqueOrThrow({where: {playerId: player1Id}})).wins).toBe(1);
    expect((await repairUnsettledRankedMatches(second)).repaired).not.toContain(roomId);
  });
  it('공개 랭킹은 새 프로필로 만들고 MMR/초기 사전값을 노출하지 않는다', async () => {
    const summary = await withRankedTransaction(db, tx => rankSummary(tx, player0Id));
    expect(summary.me.point).toBe(0); expect(summary.me.tier).toBe('Bronze');
    expect(summary.me.rank).toBeNull(); expect(summary.top.some(p => p.playerId === player0Id)).toBe(false);
    expect(summary.me.promotion.requiredWins).toBe(2); expect(summary.me.promotion.maxGames).toBe(3);
    expect(JSON.stringify(summary)).not.toContain('rankedMmr'); expect(JSON.stringify(summary)).not.toContain('casualPriorOffset');
  });
});
