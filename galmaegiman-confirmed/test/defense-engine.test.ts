import { describe, it, expect, beforeAll } from 'vitest';

// 랜덤 디펜스 게임 규칙(public/defense/engine.js) 자동 시험: 규칙을 바꿀 때 기존 규칙이 깨지지 않게
// engine.js는 화면에서 쓰는 순수 JS 모듈이라 형 정보 없이 불러온다
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let E: any;
beforeAll(async () => { const p = '../public/defense/engine.js'; E = await import(p); });

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const put = (g: any, id: string, extra: Record<string, unknown> = {}) => { const u = { uid: g.nextUid++, id, x: 200, y: 300, tx: 200, ty: 300, cd: 9, born: 0, away: false, ...extra }; g.units.push(u); return u; };
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const run = (g: any, sec: number) => { for (let i = 0; i < sec / 0.05; i++) E.step(g, 0.05); };
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const snap = (g: any) => JSON.stringify({ ...g, rand: g.rand.s, fx: [] });

describe('랜덤 디펜스 엔진', () => {
  it('시작: 커먼 위습 6 · 언커먼 1 · 새우 40, 1웨이브 바로 시작', () => {
    const g = E.newGame(1);
    expect(g.wisps.COMMON).toBe(6); expect(g.wisps.UNCOMMON).toBe(1); expect(g.gold).toBe(40);
    run(g, 0.1); expect(g.wave).toBe(1);
  });

  it('같은 씨앗·같은 행동이면 같은 결과', () => {
    const a = E.newGame(42), b = E.newGame(42);
    for (const g of [a, b]) { run(g, 5); E.summon(g, 'COMMON'); run(g, 20); }
    expect(snap(a)).toBe(snap(b));
  });

  it('저장 → 불러오기 후에도 똑같이 이어짐(난수 상태 포함), 버전이 다르면 버림', () => {
    const a = E.newGame(7, 'hard'); run(a, 20); E.summon(a, 'COMMON'); E.summon(a, 'UNCOMMON');
    const b = E.loadGame(E.saveGame(a));
    expect(b.mode).toBe('hard');
    run(a, 30); run(b, 30); E.summon(a, 'COMMON'); E.summon(b, 'COMMON');
    expect(snap(b)).toBe(snap(a));
    expect(E.loadGame(JSON.stringify({ v: -1, g: {} }))).toBeNull();
    expect(E.loadGame('not json')).toBeNull();
  });

  it('위습 소환: 그 등급 갈매미, 위습이 없으면 실패', () => {
    const g = E.newGame(3); g.units = [];
    const r = E.summon(g, 'UNCOMMON');
    expect(r.ok).toBe(true); expect(E.UNIT[r.unit.id].grade).toBe('UNCOMMON');
    expect(E.summon(g, 'UNCOMMON').ok).toBe(false);
  });

  it('용병모집: 모집권 4개, 레어 30 · 에픽 40 · 실패 30(2000번, ±4%)', () => {
    const g = E.newGame(9); g.tickets = 3;
    expect(E.recruit(g).ok).toBe(false);
    g.tickets = 4 * 2000; const c = { RARE: 0, EPIC: 0, fail: 0 };
    for (let i = 0; i < 2000; i++) { const r = E.recruit(g); if (r.fail) c.fail++; else c[r.grade as 'RARE' | 'EPIC']++; }
    expect(g.tickets).toBe(0);
    expect(Math.abs(c.RARE / 2000 - 0.3)).toBeLessThan(0.04);
    expect(Math.abs(c.EPIC / 2000 - 0.4)).toBeLessThan(0.04);
    expect(Math.abs(c.fail / 2000 - 0.3)).toBeLessThan(0.04);
  });

  it('판매 보너스: 레어 모집권 1 · 에픽 모집권 1 + 커먼 위습 2 · 언커먼 약 30%', () => {
    const g = E.newGame(5); g.units = [];
    let t = g.tickets; E.sell(g, put(g, 'u20').uid); expect(g.tickets - t).toBe(1);
    t = g.tickets; const w = g.wisps.COMMON; E.sell(g, put(g, 'u29').uid); expect(g.tickets - t).toBe(1); expect(g.wisps.COMMON - w).toBe(2);
    t = g.tickets; for (let i = 0; i < 1000; i++) E.sell(g, put(g, 'u12').uid);
    expect(Math.abs((g.tickets - t) / 1000 - 0.3)).toBeLessThan(0.05);
  });

  it('조합: 재료가 있으면 결과 1마리, 재료는 사라짐', () => {
    const g = E.newGame(2); g.units = [];
    const r = E.recipeFor('u09');   // 노움 = 미니 + 미니
    for (const id of r.in) put(g, id);
    const nu = E.combine(g, r);
    expect(nu.id).toBe('u09'); expect(g.units.map((u: { id: string }) => u.id)).toEqual(['u09']);
    expect(E.combine(g, r)).toBeNull();
  });

  it('위치 고정: 끌기·모으기에 안 움직이고, 풀면 움직임', () => {
    const g = E.newGame(2); g.units = [];
    const a = put(g, 'u01', { x: 150, y: 300, tx: 150, ty: 300 }), b = put(g, 'u01', { x: 350, y: 400, tx: 350, ty: 400 });
    E.setLock(g, [a.uid], true);
    expect(E.moveUnit(g, a.uid, 250, 250, true)).toBe(false);
    E.gather(g, b.uid); expect([a.tx, a.ty]).toEqual([150, 300]);
    E.setLock(g, [a.uid], false);
    expect(E.moveUnit(g, a.uid, 250, 250, true)).toBe(true);
  });

  it('배치는 길 안쪽 사각형 안으로만', () => {
    const p = E.clampField(0, 0), q = E.clampField(9999, 9999);
    expect(p.x).toBe(E.INNER.x0); expect(p.y).toBe(E.INNER.y0); expect(q.x).toBe(E.INNER.x1); expect(q.y).toBe(E.INNER.y1);
  });

  it('방깍은 갈매미마다 더해지고(같은 갈매미는 한 번), 피해가 늘어남', () => {
    const mk = (ids: string[]) => {
      const g = E.newGame(4); g.units = []; g.enemies = [];
      ids.forEach((id, k) => put(g, id, { x: 84 + k, y: 184, tx: 84 + k, ty: 184, cd: 0 }));   // 적이 서 있는 길 시작점(46,134) 가까이
      return g;
    };
    const one = mk(['u27']), two = mk(['u27', 'u27']);
    for (const g of [one, two]) { g.wave = 40; g.bossDone = 40; g.waveT = 0; run(g, 0.05); }
    // 적 하나를 갈매미 앞에 두고 방깍 합계 확인
    for (const g of [one, two]) { g.enemies = [{ id: 1, hp: 1e12, max: 1e12, s: 0, speed: 0, boss: false, look: 'e:v1', gold: 0, armor: 200, mr: 200, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 46, y: 134, by: 0 }]; run(g, 3); }
    const s1 = E.shredNow(one.enemies[0], one.t).shred, s2 = E.shredNow(two.enemies[0], two.t).shred;
    expect(s1).toBe(E.kit('u27').shred); expect(s2).toBe(E.kit('u27').shred * 2);
    expect(E.defMul(100)).toBeGreaterThan(E.defMul(200));
  });

  it('이속감소는 겹치고 합계 100%까지, 100%면 원래 속도의 15%', () => {
    expect(E.slowMul(0)).toBe(1);
    expect(E.slowMul(1)).toBeCloseTo(0.15);
    expect(E.slowMul(2)).toBeCloseTo(0.15);
  });

  it('상위 마릿수 제한은 없음(topCap 0)', () => {
    const g = E.newGame(1); g.units = [];
    ['u57', 'u56', 'u58'].forEach((id) => put(g, id));
    const r = E.RECIPES.find((q: { out: string }) => E.tierOf(q.out) >= 6);
    r.in.forEach((id: string) => put(g, id));
    expect(E.combine(g, r)).not.toBeNull();
  });

  it('스토리 8장을 50웨이브까지 못 깨면 패배, 깼거나 탐사 금지 모드면 계속', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const toWave51 = (g: any) => { g.units = []; g.wave = 49; g.bossDone = 50; g.waveT = 0; g.spawnLeft = 0; while (!g.over && g.wave < 51 && g.t < 200) { E.step(g, 0.05); g.enemies = []; if (g.wave === 50) g.bossDone = 50; } return g; };
    const lose = toWave51(E.newGame(1));
    expect(lose.over).toBe('lose'); expect(lose.reason).toContain('스토리');
    const done = E.newGame(1); done.missionDone = E.REGIONS.length; toWave51(done);
    expect(done.over).toBeFalsy(); expect(done.wave).toBe(51);
    const nostory = toWave51(E.newGame(1, 'nostory'));
    expect(nostory.over).toBeFalsy();
  });

  it('보스는 5웨이브마다, 잡으면 커먼 선택 위습 2개(모집권은 10웨이브 보스만)', () => {
    expect([1, 4, 5, 9, 10, 15, 60].map((w) => E.waveInfo(w).boss)).toEqual([false, false, true, false, true, true, true]);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const killBoss = (g: any, w: number) => {
      g.units = []; g.wave = w; g.waveT = 1; g.spawnLeft = 0; g.missionDone = E.REGIONS.length;
      g.enemies = [{ id: 1, hp: 0, max: 1, s: 0, speed: 0, boss: true, look: 'e:v5', gold: 0, armor: 0, mr: 0, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 46, y: 134, by: 0 }];
      E.step(g, 0.05);
    };
    const g = E.newGame(3); const p0 = g.picks.COMMON, t0 = g.tickets ?? 0;
    killBoss(g, 5);
    expect(g.bossDone).toBe(5); expect(g.picks.COMMON).toBe(p0 + 2); expect(g.tickets ?? 0).toBe(t0);
    killBoss(g, 10);
    expect(g.picks.COMMON).toBe(p0 + 4); expect(g.tickets).toBe(t0 + 1);
  });
  it('방어는 40웨이브까지 완만(45 이하), 그 뒤 가파르게', () => {
    expect(E.defAt(40)).toBeLessThanOrEqual(45);
    expect(E.defAt(20)).toBeLessThan(30);
    expect(E.defAt(50) - E.defAt(40)).toBeGreaterThan(100);
    expect(E.defAt(60)).toBe(305);
  });
  it('도전 모드: 하드 체력 ×1.5 · 강화 금지 · 외부탐사 금지', () => {
    const h = E.newGame(1, 'hard'); run(h, 1);
    expect(h.enemies[0].max).toBeCloseTo(E.waveInfo(1).hp * 1.5);
    const n = E.newGame(1, 'noupg'); n.gold = 1e6; expect(E.upgrade(n, 0).ok).toBe(false);
    const s = E.newGame(1, 'nostory'); expect(E.missionOpen(s)).toBe(false);
  });

  it('외부탐사: 대장을 쓰러뜨리면 보상(위습·선택 위습·모집권·새우)과 다음 장', () => {
    const g = E.newGame(1); g.units = [];
    const u = put(g, 'u29', { cd: 0 }); E.sendAway(g, [u.uid]);
    const r = E.REGIONS[0], w0 = { ...g.wisps }, t0 = g.tickets, g0 = g.gold;
    E.missionTarget(g); g.missionDmg[0] = 1e12; run(g, 0.1);
    expect(g.missionDone).toBe(1);
    for (const [k, v] of Object.entries(r.reward)) expect(g.wisps[k] - (w0 as Record<string, number>)[k]).toBe(v);
    expect(g.tickets - t0).toBe(r.ticket); expect(g.gold - g0).toBeGreaterThanOrEqual(r.gold);
    expect(g.units[0].away).toBe(false);   // 깨면 돌아옴
  });

  it('같이하기 외부탐사: 친구 피해가 더해져 같은 장을 깸', () => {
    const g = E.newGame(1); g.units = [];
    E.setPeerMission(g, [E.missionTarget(g).max]);
    run(g, 0.1);
    expect(g.missionDone).toBe(1);
  });
});
