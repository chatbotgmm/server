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

  it('이감·마방깍은 사거리 안 적 모두에게(때리지 않아도), 벗어나면 곧 풀림', () => {
    const slowId = E.UNITS.map((u: { id: string }) => u.id).find((id: string) => E.kit(id).slow && !E.kit(id).mshred)!;
    const mshId = E.UNITS.map((u: { id: string }) => u.id).find((id: string) => E.kit(id).mshred)!;
    const g = E.newGame(5); g.units = []; g.wave = 3; g.waveT = 1; g.spawnLeft = 0;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const add = (id: string) => { const u: any = { uid: g.units.length + 100, id, x: 100, y: 134, tx: 100, ty: 134, cd: 99, lock: true }; g.units.push(u); return u; };
    add(slowId); add(mshId);   // cd 99: 공격하지 않음
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const enemy = (id: number, x: number): any => ({ id, hp: 1e9, max: 1e9, s: 0, speed: 1, boss: false, look: 'e:v1', gold: 0, armor: 50, mr: 50, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x, y: 134, by: 0 });
    g.enemies = [enemy(1, 110), enemy(2, 140), enemy(3, 900)];
    E.step(g, 0.05);
    const [a, b, far] = g.enemies;
    for (const e of [a, b]) { expect(Object.keys(e.slows ?? {}).length).toBe(1); expect(E.shredNow(e, g.t).mshred).toBe(E.kit(mshId).mshred); }
    expect(far.slows ?? {}).toEqual({}); expect(E.shredNow(far, g.t).mshred).toBe(0);
    g.units = []; for (let i = 0; i < 10; i++) E.step(g, 0.05);   // 갈매미가 없어지면 0.3초 뒤 풀림
    expect(E.shredNow(a, g.t).mshred).toBe(0);
  });
  it('전설·히든에 이감·스턴 갈매미가 각각 여럿(선택지)', () => {
    for (const grade of ['LEGEND', 'HIDDEN']) {
      const ks = E.UNITS.filter((u: { grade: string }) => u.grade === grade).map((u: { id: string }) => E.kit(u.id));
      expect(ks.filter((k: { slow?: number }) => k.slow).length).toBeGreaterThanOrEqual(3);
      expect(ks.filter((k: { stun?: number; astun?: number }) => k.stun || k.astun).length).toBeGreaterThanOrEqual(2);
    }
  });
  it('효과는 에픽 이상만, 방깍·마방깍은 하나만, 마방깍은 에픽 이상 모든 등급에(2026-11-10)', () => {
    const UTIL = ['shred', 'mshred', 'amp', 'slow', 'stun', 'astun', 'poison'];
    for (const u of E.UNITS) {
      const k = E.kit(u.id);
      if (E.tierOf(u.id) < 3) for (const key of UTIL) expect(k[key], `${u.name} ${key}`).toBeFalsy();   // 커먼·언커먼·레어는 공격만
      expect(!!(k.shred && k.mshred), u.name).toBe(false);                                              // 둘 다는 없음
      if (k.shred && !k.mshred) expect(k.type === 'phys' || !k.mshred).toBe(true);
    }
    for (const g of ['EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'])
      expect(E.UNITS.filter((u: { grade: string; id: string }) => u.grade === g && E.kit(u.id).mshred).length, g).toBeGreaterThanOrEqual(1);
    expect(E.kitTips(E.kit('u32')).some((t: string) => t.startsWith('주변 마방깍'))).toBe(true);
    // 마법 딜러(술사 주력)는 에픽 이상이면 모두 마방깍
    for (const u of E.UNITS) { const k = E.kit(u.id); if (E.tierOf(u.id) >= 3 && k.type === 'mag' && k.job === '주력') expect(k.mshred, u.name).toBeGreaterThan(0); }
  });
  it('스턴: 맞힐 때 확률로 1~2초(에픽 1·전설 1.25·히든 1.5·초불영제 2), 보스는 1/4', () => {
    expect([3, 4, 5, 6, 9].map((t) => E.STUN_TIME(t))).toEqual([1, 1.25, 1.5, 2, 2]);
    for (const u of E.UNITS) { const k = E.kit(u.id); if (k.stun || k.astun) { expect(k.stunTime).toBeGreaterThanOrEqual(1); expect(k.stunTime).toBeLessThanOrEqual(2); } }
    const g = E.newGame(2); g.units = []; g.rand = Object.assign(() => 0, { s: 0 });   // 확률 굴림이 늘 성공
    const k = E.kit('u33');   // 거장(전설, 스턴)
    const mk = (boss: boolean) => ({ id: 1, hp: 1e9, max: 1e9, s: 0, speed: 50, boss, look: 'e:v1', gold: 0, armor: 0, mr: 0, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 200, y: 134, by: 0 });
    const e = mk(false), b = mk(true); g.enemies = [e, b];
    E.debuffForTest(g, e, k, 1); E.debuffForTest(g, b, k, 1);
    expect(e.stunUntil - g.t).toBeCloseTo(1.25); expect(b.stunUntil - g.t).toBeCloseTo(1.25 * E.BOSS_STUN);
  });
  it('40웨이브부터 일반 적 체력·이동속도가 더 오름(보스는 그대로)', () => {
    const r = E.RULES, base = (w: number) => { const s = { ...r }; r.lateFrom = 999; const v = E.waveInfo(w); Object.assign(r, s); return v; };
    expect(E.waveInfo(39).hp).toBe(base(39).hp);
    expect(E.waveInfo(51).hp / base(51).hp).toBeCloseTo(1 + r.lateHpPer * 11);
    expect(E.waveInfo(51).speed / base(51).speed).toBeCloseTo(1 + r.lateSpdPer * 11);
    expect(E.waveInfo(50).hp).toBe(base(50).hp);   // 보스
  });
  it('20웨이브부터 보스 체력은 마방깍 없이 마법만으로는 못 잡을 만큼(기준 조합 실측으로 정한 배수)', () => {
    for (const w of [20, 30, 40, 50, 60]) expect(E.RULES.bossMul[w]).toBeGreaterThanOrEqual(3.7);
    E.RULES.simNoMshred = false;
  });
  it('최상위는 재료 유틸을 물려받음: 물딜은 방깍(마방깍 없음), 마딜은 마방깍·증폭(방깍 없음), 이감·스턴은 둘 다', () => {
    for (const u of E.UNITS.filter((x: { id: string; role: string }) => E.tierOf(x.id) >= 6 && x.role !== E.SPECIAL_ROLE)) {
      const k = E.kit(u.id), r = E.recipeFor(u.id);
      const ing = r.in.map((i: string) => E.kit(i));
      const mx = (key: string) => Math.max(0, ...ing.map((q: Record<string, number>) => q[key] ?? 0));
      if (k.type === 'phys') { expect(k.inherited ?? []).not.toContain('mshred'); expect(k.inherited ?? []).not.toContain('amp'); if (mx('shred')) expect(k.shred).toBeGreaterThanOrEqual(Math.round(mx('shred') * E.INHERIT) - 1); }
      else { expect(k.inherited ?? []).not.toContain('shred'); if (mx('mshred')) expect(k.mshred).toBeGreaterThanOrEqual(Math.round(mx('mshred') * E.INHERIT) - 1); }
      if (mx('slow')) expect(k.slow).toBeGreaterThanOrEqual(mx('slow') - 1e-9);   // 이감·스턴은 재료 값 그대로
      if (mx('stun')) expect(k.stun).toBeGreaterThanOrEqual(mx('stun') - 1e-9);
    }
  });
  it('조합 비용(2026-11-10 기준, 커먼 1·언커먼 2 환산): 레어 3~5 · 에픽 ≈ 레어×3 · 전설·히든 ≈ 에픽×3 · 초불영제 = 전설·히든×2~3', () => {
    const val = (id: string): number => { const t = E.tierOf(id); if (id === E.TRANSCEND_KEY) return 0; if (t === 0) return 1; if (t === 1) return 2; return E.recipeFor(id).in.reduce((a: number, i: string) => a + val(i), 0); };
    const ids = (lo: number, hi: number) => E.UNITS.filter((u: { id: string; role: string }) => E.tierOf(u.id) >= lo && E.tierOf(u.id) <= hi && u.role !== E.SPECIAL_ROLE).map((u: { id: string }) => u.id);
    const avg = (l: string[]) => l.reduce((a, i) => a + val(i), 0) / l.length;
    for (const id of ids(2, 2)) { expect(val(id), id).toBeGreaterThanOrEqual(3); expect(val(id), id).toBeLessThanOrEqual(5); }
    const ep = avg(ids(3, 3)) / avg(ids(2, 2)), lh = avg(ids(4, 5)) / avg(ids(3, 3));
    expect(ep).toBeGreaterThan(2.8); expect(ep).toBeLessThan(3.2);
    expect(lh).toBeGreaterThan(2.8); expect(lh).toBeLessThan(3.2);
    for (const id of ids(6, 9)) { const k = val(id) / avg(ids(4, 5)); expect(k, id).toBeGreaterThanOrEqual(2); expect(k, id).toBeLessThanOrEqual(3.01); }
    for (let n = 61; n <= 80; n++) expect(['LEGEND', 'HIDDEN'], `u${n}`).toContain(E.UNIT[`u${n}`].grade);   // 새 20종은 전설·히든
  });
  it('커먼은 오른쪽 안쪽 8곳에 종류마다 제자리, 커먼을 창고에 넣으면 언커먼이 그 자리로(다른 등급은 가운데)', () => {
    const g = E.newGame(3); g.units = [];
    const commons = E.UNITS.filter((u: { grade: string }) => u.grade === 'COMMON').map((u: { id: string }) => u.id);
    const uncommons = E.UNITS.filter((u: { grade: string }) => u.grade === 'UNCOMMON').map((u: { id: string }) => u.id);
    expect(E.COMMON_SPOTS.length).toBe(8); expect(commons.length).toBe(8); expect(uncommons.length).toBe(8);
    g.picks.COMMON = 8;
    commons.forEach((id: string, i: number) => { const u = E.summonPick(g, id).unit; expect([u.x, u.y], id).toEqual([E.COMMON_SPOTS[i].x, E.COMMON_SPOTS[i].y]); });
    g.wisps.COMMON = 20;
    for (let i = 0; i < 20; i++) { const u = E.summon(g, 'COMMON').unit, s = E.COMMON_SPOTS[commons.indexOf(u.id)]; expect([u.x, u.y]).toEqual([s.x, s.y]); }   // 같은 종류는 같은 자리
    g.wisps.UNCOMMON = 30; const un = []; for (let i = 0; i < 30; i++) un.push(E.summon(g, 'UNCOMMON').unit);
    expect(un.every((u: { x: number; y: number }) => Math.hypot(u.x - E.SPAWN.x, u.y - E.SPAWN.y) < 45)).toBe(true);   // 언커먼은 가운데에 나옴
    un[0].lock = true; const lx = un[0].x;
    E.storeGroup(g, 'COMMON');
    expect(E.fieldUnits(g).some((u: { id: string }) => commons.includes(u.id))).toBe(false);
    for (const u of un.slice(1)) { const s = E.COMMON_SPOTS[uncommons.indexOf(u.id)]; expect([u.x, u.y]).toEqual([s.x, s.y]); }
    expect(un[0].x).toBe(lx);   // 위치 고정한 것은 그대로
  });
  it('초불영제는 같은 등급: 기본 공격력·판매가·크기 같음, 조합법은 전설·히든 2~3 + 에픽(초월은 + 초월 갈매미맨), 최상위 재료 없음', () => {
    const tops = E.UNITS.filter((u: { id: string; role: string }) => E.tierOf(u.id) >= 6 && u.role !== E.SPECIAL_ROLE);
    const dm = new Set(tops.map((u: { id: string }) => E.RULES.dmg[Math.min(E.tierOf(u.id), 9)]));
    expect(new Set([6, 7, 8, 9].map((t) => E.RULES.dmg[t])).size).toBe(1); expect(dm.size).toBe(1);
    for (const u of tops) {
      const r = E.recipeFor(u.id), gr = (i: string) => E.UNIT[i].grade;
      const lh = r.in.filter((i: string) => ['LEGEND', 'HIDDEN'].includes(gr(i))).length;
      expect(lh, u.name).toBeGreaterThanOrEqual(2); expect(lh, u.name).toBeLessThanOrEqual(3);
      expect(r.in.filter((i: string) => !['LEGEND', 'HIDDEN', 'EPIC'].includes(gr(i)) && i !== E.TRANSCEND_KEY).length, u.name).toBe(0);
      expect(r.in.includes(E.TRANSCEND_KEY), u.name).toBe(u.grade === 'TRANSCEND');
      expect(r.in.filter((i: string) => E.tierOf(i) >= 6 && i !== E.TRANSCEND_KEY).length, u.name).toBe(0);
    }
  });
  it('초월 갈매미맨: 공격하지 않고 팔 수 없음, 외부탐사 6장 보상, 초월을 조합하면 사라짐', () => {
    expect(E.kit(E.TRANSCEND_KEY).dmg).toBe(0);
    expect(E.REGIONS[5].unit).toBe(E.TRANSCEND_KEY);
    const g = E.newGame(2);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const add = (id: string, i: number): any => { const u = { uid: 700 + i, id, x: 200, y: 300, tx: 200, ty: 300, cd: 0, born: 0 }; g.units.push(u); return u; };
    g.units = []; const key = add(E.TRANSCEND_KEY, 0);
    g.enemies = [{ id: 1, hp: 1e9, max: 1e9, s: 0, speed: 0, boss: false, look: 'e:v1', gold: 0, armor: 0, mr: 0, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 205, y: 300, by: 0 }];
    for (let i = 0; i < 20; i++) E.step(g, 0.05);
    expect(g.enemies[0].hp).toBe(1e9);                     // 공격하지 않음
    expect(E.sell(g, key.uid)).toBe(false);                 // 팔 수 없음
    const r = E.recipeFor('u49');                           // 가루다(초월)
    r.in.filter((i: string) => i !== E.TRANSCEND_KEY).forEach((id: string, i: number) => add(id, i + 1));
    expect(E.combine(g, r)).toBeTruthy();
    expect(g.units.some((u: { id: string }) => u.id === E.TRANSCEND_KEY)).toBe(false);   // 재료로 사라짐
    expect(E.recipeUnits(g, r)).toBeFalsy();
  });
  it('6장 보상은 고르기: 초월 갈매미맨 / 전설 위습 1 / 에픽 선택 위습 2 중 하나(기본 보상은 그대로)', () => {
    const g = E.newGame(5); g.units = []; g.enemies = []; g.missionDone = 5;
    E.step(g, 0.05);   // 첫 웨이브 시작
    E.missionTarget(g); g.missionDmg[5] = g.mission.max;   // 대장에게 체력만큼 피해(쌓인 피해로 체력을 계산함)
    const ep = g.wisps.EPIC ?? 0;
    E.step(g, 0.05);
    expect(g.missionDone).toBe(6); expect(g.choice).toEqual({ k: 5 });
    expect(g.units.some((u: { id: string }) => u.id === E.TRANSCEND_KEY)).toBe(false);   // 고르기 전에는 안 줌
    expect(g.wisps.EPIC).toBe(ep + 1);                                                     // 기본 보상(에픽 위습 1)은 받음
    const lg = g.wisps.LEGEND ?? 0;
    expect(E.chooseReward(g, 1)).toBe(true); expect(g.wisps.LEGEND).toBe(lg + 1); expect(g.choice).toBeNull();
    expect(E.chooseReward(g, 0)).toBe(false);                                              // 한 번만
    const g2 = E.newGame(5); g2.units = []; g2.choice = { k: 5 }; const pe = g2.picks.EPIC ?? 0;
    E.chooseReward(g2, 2); expect(g2.picks.EPIC).toBe(pe + 2);
    const g3 = E.newGame(5); g3.units = []; g3.choice = { k: 5 };
    E.chooseReward(g3, 0); expect(g3.units.filter((u: { id: string }) => u.id === E.TRANSCEND_KEY).length).toBe(1);
  });
  it('탐사 특기: 새 전설·히든(고체력 ×6·근접술사 ×3), 그 재료로 만든 초불영제는 (배수−1)×0.5를 물려받음', () => {
    for (const id of ['u66', 'u76']) expect(E.kit(id).explore, id).toBe(6);
    for (const id of ['u63', 'u69', 'u75']) expect(E.kit(id).explore, id).toBe(3);
    for (const u of E.UNITS.filter((x: { id: string; role: string }) => E.tierOf(x.id) >= 6 && x.role !== E.SPECIAL_ROLE)) {
      const m = Math.max(0, ...E.recipeFor(u.id).in.map((i: string) => E.EXPLORE[i] ?? 0));
      expect(E.kit(u.id).explore || 0, u.name).toBe(m > 1 ? 1 + (m - 1) * E.EXPLORE_INHERIT : 0);
    }
    const heir = E.UNITS.find((x: { id: string; role: string }) => E.tierOf(x.id) >= 6 && x.role !== E.SPECIAL_ROLE && E.kit(x.id).exploreFrom);   // 물려받은 초불영제 하나
    expect(heir).toBeTruthy();
    expect(E.kitTips(E.kit(heir.id)).some((t: string) => t.includes('물려받음') && t.includes('탐사'))).toBe(true);
  });
  it('전설·히든은 모두 적어도 한 초불영제의 재료(만들어도 위로 안 이어지는 것 없음, 2026-11-10)', () => {
    const tops = E.RECIPES.filter((r: { out: string }) => E.tierOf(r.out) >= 6);
    for (const u of E.UNITS.filter((x: { id: string }) => [4, 5].includes(E.tierOf(x.id))))
      expect(tops.some((r: { in: string[] }) => r.in.includes(u.id)), u.name).toBe(true);
  });
  it('강화하면 공격력과 공격 속도가 함께 오름', () => {
    const g = E.newGame(1), k = E.kit('u01');
    expect(E.gapOf(g, k)).toBeCloseTo(k.gap);
    g.upg[0] = 10;
    expect(E.upgMul(g, k.tier)).toBeCloseTo(1 + 10 * E.RULES.upgPct);
    expect(E.gapOf(g, k)).toBeCloseTo(k.gap / (1 + 10 * E.RULES.upgSpd));
  });
  it('10웨이브마다 끝나고 30초 쉬는 시간(적이 더 나오지 않음)', () => {
    expect(E.waveLenOf(9)).toBe(E.RULES.waveLen); expect(E.waveLenOf(10)).toBe(E.RULES.waveLen + 30); expect(E.waveLenOf(60)).toBe(E.RULES.waveLen);
    const g = E.newGame(2); g.units = []; g.wave = 10; g.bossDone = 10; g.waveT = E.RULES.waveLen - 0.01; g.spawnLeft = 0; g.enemies = []; g.missionDone = E.REGIONS.length;
    E.step(g, 0.05);
    expect(g.wave).toBe(10); expect(E.resting(g)).toBe(true); expect(Math.ceil(E.nextWaveIn(g))).toBe(30);
    while (g.wave === 10 && g.t < 100) { E.step(g, 0.05); expect(g.enemies.length).toBe(0); }
    expect(g.wave).toBe(11);
  });
  it('난이도: 체력·방어 배수, 달인까지 40웨이브에 전설 위습 1개(장인은 없음)', () => {
    const order = ['intro', 'mid', 'high', 'master', 'artisan'];
    expect(E.DIFF_ORDER).toEqual(order);
    const hp = order.map((d) => { const g = E.newGame(9, 'normal', d); g.wave = 1; g.waveT = 0; g.spawnLeft = 1; g.spawnT = 0; E.step(g, 0.01); return g.enemies[0].max; });
    for (let i = 1; i < hp.length; i++) expect(hp[i]).toBeGreaterThan(hp[i - 1]);
    expect(hp[3]).toBeCloseTo(E.waveInfo(1).hp);   // 달인 = 예전 규칙
    // 보스 체력·방어·마법방어도 난이도마다 따로(보스는 라인과 다른 배수)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const boss = order.map((d) => { const g: any = E.newGame(9, 'normal', d); g.wave = 5; g.waveT = 0; g.spawnLeft = 1; g.spawnT = 0; E.step(g, 0.01); return g.enemies[0]; });
    for (let i = 1; i < boss.length; i++) { expect(boss[i].max).toBeGreaterThan(boss[i - 1].max); expect(boss[i].armor).toBeGreaterThanOrEqual(boss[i - 1].armor); expect(boss[i].mr).toBeGreaterThanOrEqual(boss[i - 1].mr); }
    expect(boss[3].max).toBeCloseTo(E.waveInfo(5).hp);
    for (const d of order) { const D = E.DIFFS[d]; for (const key of ['line', 'boss', 'armor', 'mr']) expect(typeof D[key]).toBe('number'); }
    expect(E.DIFFS.intro.boss).not.toBe(E.DIFFS.intro.line);
    expect(E.newGame(1).diff).toBe('master'); expect(E.newGame(1, 'normal', 'x').diff).toBe('master');
    for (const [d, want] of [['master', 1], ['intro', 1], ['artisan', 0]] as const) {
      const g = E.newGame(4, 'normal', d); g.units = []; g.wave = 39; g.waveT = E.RULES.waveLen - 0.01; g.spawnLeft = 0; g.enemies = []; g.missionDone = E.REGIONS.length;
      const l0 = g.wisps.LEGEND; E.step(g, 0.05);
      expect(g.wave).toBe(40); expect(g.wisps.LEGEND - l0).toBe(want);
    }
    const sv = E.loadGame(E.saveGame(E.newGame(1, 'normal', 'high')));
    expect(sv.diff).toBe('high');
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

  it('보스는 5웨이브마다, 잡으면 커먼 선택 위습(RULES.bossPickEach)(모집권은 10웨이브 보스만)', () => {
    expect([1, 4, 5, 9, 10, 15, 60].map((w) => E.waveInfo(w).boss)).toEqual([false, false, true, false, true, true, true]);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const killBoss = (g: any, w: number) => {
      g.units = []; g.wave = w; g.waveT = 1; g.spawnLeft = 0; g.missionDone = E.REGIONS.length;
      g.enemies = [{ id: 1, hp: 0, max: 1, s: 0, speed: 0, boss: true, look: 'e:v5', gold: 0, armor: 0, mr: 0, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 46, y: 134, by: 0 }];
      E.step(g, 0.05);
    };
    const g = E.newGame(3); const p0 = g.picks.COMMON, t0 = g.tickets ?? 0;
    killBoss(g, 5);
    const pe = E.RULES.bossPickEach.COMMON;
    expect(g.bossDone).toBe(5); expect(g.picks.COMMON).toBe(p0 + pe); expect(g.tickets ?? 0).toBe(t0);
    killBoss(g, 10);
    expect(g.picks.COMMON).toBe(p0 + 2 * pe); expect(g.tickets).toBe(t0 + 1);
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
    expect(g.units[0].away).toBe(true);    // 2026-11-10: 깨도 다음 장 탐사를 계속(마지막 장만 돌아옴)
    g.missionDone = E.REGIONS.length - 1; E.missionTarget(g); g.missionDmg[E.REGIONS.length - 1] = 1e12; run(g, 0.1);
    expect(g.missionDone).toBe(E.REGIONS.length); expect(g.units[0].away).toBe(false);   // 8장을 깨면 돌아옴
  });
  it('60웨이브 보스를 잡아도 남은 적을 처리하는 시간, 끝나면 남은 적 수 기록(0마리면 완벽)', () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const setup = (left: number): any => { const g = E.newGame(4); g.units = []; g.wave = 60; g.waveT = 5; g.spawnLeft = 0; g.missionDone = E.REGIONS.length;
      const en = (boss: boolean, i: number) => ({ id: 10 + i, hp: boss ? 0 : 1e12, max: 1, s: i * 30, speed: 0, boss, look: 'e:v1', gold: 0, armor: 0, mr: 0, amp: 0, ampUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: 46, y: 134, by: 0 });
      g.enemies = [en(true, 0), ...Array.from({ length: left }, (_, i) => en(false, i + 1))]; return g; };
    const g = setup(3); E.step(g, 0.05);
    expect(g.over).toBeFalsy(); expect(g.finaleAt).toBeDefined();                      // 바로 끝나지 않음
    for (let i = 0; i < (E.RULES.finaleLen + 1) / 0.5 && !g.over; i++) E.step(g, 0.5);
    expect(g.over).toBe('win'); expect(g.leftover).toBe(3);
    const p = setup(0); E.step(p, 0.05); E.step(p, 0.05);
    expect(p.over).toBe('win'); expect(p.leftover).toBe(0); expect(p.reason).toContain('완벽');
  });

  it('같이하기 외부탐사: 친구 피해가 더해져 같은 장을 깸', () => {
    const g = E.newGame(1); g.units = [];
    E.setPeerMission(g, [E.missionTarget(g).max]);
    run(g, 0.1);
    expect(g.missionDone).toBe(1);
  });
});
