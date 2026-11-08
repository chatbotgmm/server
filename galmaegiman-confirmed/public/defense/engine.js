// 갈매미맨 랜덤 디펜스: 게임 규칙(화면과 분리). 브라우저와 Node 시험에서 같이 쓴다.
// 판: 가운데 5×6칸에 갈매미를 두고, 둘레 길을 도는 그림자 갈매미를 막는다.
// 소환은 커먼 8종 중 무작위. 강해지려면 게임 속 조합법(공개분)대로 합친다. 히든 갈매미는 보스 보상.
import { UNITS, RECIPES } from './data.js';

export const GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'];
export const GRADE_NAME = { COMMON: '커먼', UNCOMMON: '언커먼', RARE: '레어', EPIC: '에픽', LEGEND: '전설', HIDDEN: '히든', LIMITED: '제한', TRANSCEND: '초월', IMMORTAL: '불멸', ETERNAL: '영원' };
export const UNIT = Object.fromEntries(UNITS.map((u) => [u.id, u]));
export { UNITS, RECIPES };

// ── 판 모양(논리 좌표 540×960) ──
export const W = 540, H = 960;
export const COLS = 5, ROWS = 6, SLOTS = COLS * ROWS;
export const LOOP = { x0: 40, y0: 120, x1: 500, y1: 640 };   // 적이 도는 길(중심선)
export const GRID = { x: 78, y: 156, w: 384, h: 448 };        // 갈매미 칸
export const CELL_W = GRID.w / COLS, CELL_H = GRID.h / ROWS;
const LW = LOOP.x1 - LOOP.x0, LH = LOOP.y1 - LOOP.y0, PERIM = 2 * (LW + LH);

// ── 규칙 수치 ──
export const RULES = {
  waves: 40, waveLen: 26, perWave: 16, spawnGap: 0.6, limit: 70,
  bossEvery: 10, bossTime: 60, startGold: 120,
  summonBase: 20, summonStep: 1, summonMax: 50,
  // 등급별 기본 공격력(한 번 때릴 때)·판매값. 조합 결과가 재료 합보다 확실히 세지도록 등급마다 약 3배
  dmg: [12, 40, 130, 400, 1200, 1800, 3600, 10000, 28000, 80000],
  sell: [10, 25, 55, 120, 260, 300, 520, 1000, 2000, 4000],
  hpBase: 40, hpGrowth: 1.175, hpLinear: 20, bossMul: [12, 12, 10, 11],   // 보스 체력 = 그 웨이브 적 체력 × (10·20·30·40 순서)
  killGold: 3, killGoldPer: 4, waveBonus: 10, waveBonusPer: 2,
  upgBase: 60, upgStep: 40, upgPct: 0.1,   // 공격력 강화: 한 단계마다 모든 갈매미 +10%
};

export const gradeTier = (g) => GRADES.indexOf(g);
export const tierOf = (id) => gradeTier(UNIT[id].grade);

// 역할 → 공격 방식, 첫 낱말 → 속성
const STYLE = {
  타격수: { gap: 0.8, mul: 1, range: 150, kind: 'single' },
  기습수: { gap: 0.45, mul: 0.6, range: 130, kind: 'single', crit: 0.25 },
  기습술사: { gap: 0.45, mul: 0.6, range: 130, kind: 'single', crit: 0.25 },
  집중술사: { gap: 1.1, mul: 1.5, range: 220, kind: 'single' },
  근접술사: { gap: 0.9, mul: 0.8, range: 110, kind: 'area', area: 64 },
};
const ELEM = { 불: 'FIRE', 물: 'WATER', 얼음: 'ICE', 번개: 'LIGHTNING', 자연: 'NATURE', 성: 'HOLY', 성술: 'HOLY', 어둠: 'DARK' };
export const ELEM_NAME = { FIRE: '불', WATER: '물', ICE: '얼음', LIGHTNING: '번개', NATURE: '자연', HOLY: '성', DARK: '어둠', NORMAL: '무' };
export const ELEM_TIP = {
  FIRE: '맞은 적 둘레에도 불꽃 피해', WATER: '맞은 적을 조금 느리게', ICE: '맞은 적을 많이 느리게', LIGHTNING: '옆의 적 2마리에게 번개가 튐',
  NATURE: '맞은 적에게 독(시간 지나며 피해)', HOLY: '보스에게 큰 피해', DARK: '맞은 적이 잠깐 피해를 더 받음', NORMAL: '',
};

export function unitStats(id) {
  const u = UNIT[id], t = tierOf(id), words = u.role.split(' ');
  const last = words[words.length - 1];
  const tank = u.role.startsWith('고체력');
  const st = tank ? { gap: 1.2, mul: 1.4, range: 120, kind: 'single', stun: 0.15 } : (STYLE[last] ?? STYLE.타격수);
  const elem = ELEM[words[0]] ?? 'NORMAL';
  return { ...st, elem, dmg: Math.round(RULES.dmg[t] * st.mul), sell: RULES.sell[t], tier: t };
}

// 길 위 거리 s → 좌표(위 → 오른쪽 → 아래 → 왼쪽, 시계 방향)
export function pathPos(s) {
  let d = ((s % PERIM) + PERIM) % PERIM;
  if (d < LW) return { x: LOOP.x0 + d, y: LOOP.y0 };
  d -= LW;
  if (d < LH) return { x: LOOP.x1, y: LOOP.y0 + d };
  d -= LH;
  if (d < LW) return { x: LOOP.x1 - d, y: LOOP.y1 };
  d -= LW;
  return { x: LOOP.x0, y: LOOP.y1 - d };
}
export function slotCenter(i) {
  const c = i % COLS, r = Math.floor(i / COLS);
  return { x: GRID.x + (c + 0.5) * CELL_W, y: GRID.y + (r + 0.5) * CELL_H };
}

// 시드 있는 난수(시험 재현용)
export function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

const COMMONS = UNITS.filter((u) => u.grade === 'COMMON').map((u) => u.id);
const HIDDENS = UNITS.filter((u) => u.grade === 'HIDDEN').map((u) => u.id);

// 웨이브별 적 체력·속도·그림(앞쪽 웨이브는 낮은 등급 그림자)
export function waveInfo(w) {
  const boss = w % RULES.bossEvery === 0;
  const hp = Math.round(RULES.hpBase * Math.pow(RULES.hpGrowth, w - 1) * (1 + w / RULES.hpLinear));
  const pool = UNITS.filter((u) => gradeTier(u.grade) <= Math.min(9, Math.floor((w - 1) / 4)));
  const look = boss ? ['u33', 'u38', 'u52', 'u60'][Math.min(3, w / RULES.bossEvery - 1)] : pool[(w * 7) % pool.length].id;
  return { boss, hp: boss ? hp * RULES.bossMul[Math.min(RULES.bossMul.length, w / RULES.bossEvery) - 1] : hp, speed: boss ? 36 : 58 + Math.min(30, w), look, gold: boss ? 150 + w * 5 : RULES.killGold + Math.floor(w / RULES.killGoldPer) };
}

export function newGame(seed = (Math.random() * 2 ** 32) >>> 0) {
  return {
    seed, rand: rng(seed), t: 0, gold: RULES.startGold, summons: 0, kills: 0, upg: 0,
    wave: 0, waveT: 0, spawnLeft: 0, spawnT: 0, bossDeadline: 0,
    enemies: [], units: Array(SLOTS).fill(null), nextId: 1,
    goals: [], reward: null, over: null, reason: '', fx: [], log: [],
  };
}

export const summonCost = (g) => Math.min(RULES.summonMax, RULES.summonBase + RULES.summonStep * g.summons);
export const upgradeCost = (g) => RULES.upgBase + RULES.upgStep * g.upg;
export function upgrade(g) {
  const cost = upgradeCost(g);
  if (g.over || g.reward) return { ok: false, why: '' };
  if (g.gold < cost) return { ok: false, why: `새우가 모자라요(${cost} 필요).` };
  g.gold -= cost; g.upg++;
  return { ok: true };
}
export const freeSlots = (g) => g.units.map((u, i) => (u ? -1 : i)).filter((i) => i >= 0);

function place(g, i, id) { g.units[i] = { id, cd: 0.2 + g.rand() * 0.3, born: g.t }; }

export function summon(g) {
  const cost = summonCost(g), free = freeSlots(g);
  if (g.over || g.reward) return { ok: false, why: '' };
  if (!free.length) return { ok: false, why: '빈칸이 없어요. 합치거나 팔아 주세요.' };
  if (g.gold < cost) return { ok: false, why: `새우가 모자라요(${cost} 필요).` };
  g.gold -= cost; g.summons++;
  const i = free[Math.floor(g.rand() * free.length)], id = COMMONS[Math.floor(g.rand() * COMMONS.length)];
  place(g, i, id);
  return { ok: true, slot: i, id };
}

export function sell(g, i) {
  const u = g.units[i];
  if (!u || g.over || g.reward) return false;
  g.gold += unitStats(u.id).sell; g.units[i] = null;
  return true;
}

export function move(g, a, b) {
  if (a === b || g.over) return false;
  [g.units[a], g.units[b]] = [g.units[b], g.units[a]];
  return true;
}

// 조합법 r을 지금 판으로 만들 수 있으면 쓸 칸 목록(keep 칸을 먼저 씀), 아니면 null
export function recipeSlots(g, r, keep = -1) {
  const used = new Set(), out = [];
  const need = [...r.in];
  if (keep >= 0 && g.units[keep]) {
    const k = need.indexOf(g.units[keep].id);
    if (k < 0) return null;
    need.splice(k, 1); used.add(keep); out.push(keep);
  }
  for (const id of need) {
    let found = -1;
    for (let i = 0; i < SLOTS; i++) if (!used.has(i) && g.units[i]?.id === id) { found = i; break; }
    if (found < 0) return null;
    used.add(found); out.push(found);
  }
  return out;
}
export function combine(g, r, keep = -1) {
  if (g.over || g.reward) return null;
  const slots = recipeSlots(g, r, keep);
  if (!slots) return null;
  for (const i of slots) g.units[i] = null;
  place(g, slots[0], r.out);
  g.fx.push({ kind: 'combine', slot: slots[0], t: g.t });
  return slots[0];
}
export const recipesUsing = (id) => RECIPES.filter((r) => r.in.includes(id));
export const recipeFor = (id) => RECIPES.find((r) => r.out === id) ?? null;
export function countOnBoard(g) {
  const c = {};
  for (const u of g.units) if (u) c[u.id] = (c[u.id] ?? 0) + 1;
  return c;
}

// 조합 목표(최대 3개): 재료 칸에 목표 색 테두리
export const GOAL_MAX = 3;
export function toggleGoal(g, out) {
  const k = g.goals.indexOf(out);
  if (k >= 0) { g.goals.splice(k, 1); return false; }
  if (g.goals.length >= GOAL_MAX) g.goals.shift();
  g.goals.push(out);
  return true;
}

export function chooseReward(g, k) {
  if (!g.reward) return false;
  const id = g.reward[k], free = freeSlots(g);
  if (!id) return false;
  if (!free.length) { g.gold += unitStats(id).sell; g.log.push(`빈칸이 없어 ${UNIT[id].name}을(를) 새우로 바꿨어요.`); }
  else place(g, free[Math.floor(g.rand() * free.length)], id);
  g.reward = null;
  startWave(g);
  return true;
}

function startWave(g) {
  if (g.wave >= RULES.waves) { g.over = 'win'; g.reason = `${RULES.waves}웨이브를 모두 막았어요!`; return; }
  if (g.wave > 0) g.gold += RULES.waveBonus + RULES.waveBonusPer * g.wave;   // 웨이브를 넘길 때마다 보너스
  g.wave++; g.waveT = 0;
  const wi = waveInfo(g.wave);
  g.spawnLeft = wi.boss ? 1 : RULES.perWave; g.spawnT = 0;
  if (wi.boss) g.bossDeadline = g.t + RULES.bossTime;
}

function spawn(g) {
  const wi = waveInfo(g.wave);
  g.enemies.push({ id: g.nextId++, hp: wi.hp, max: wi.hp, s: 0, speed: wi.speed, boss: wi.boss, look: wi.look, gold: wi.gold,
    slowUntil: 0, slow: 0, stunUntil: 0, breakUntil: 0, poison: 0, poisonUntil: 0, x: LOOP.x0, y: LOOP.y0 });
}

function hurt(g, e, dmg, src) {
  if (e.hp <= 0) return;
  let d = dmg;
  if (g.t < e.breakUntil) d *= 1.2;
  if (src === 'HOLY' && e.boss) d *= 1.6;
  e.hp -= d;
}

function fire(g, i, u, st) {
  const c = slotCenter(i);
  let best = null;
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    const dx = e.x - c.x, dy = e.y - c.y;
    if (dx * dx + dy * dy > st.range * st.range) continue;
    if (!best || e.s > best.s) best = e;   // 가장 멀리 간 적부터
  }
  if (!best) return false;
  let dmg = st.dmg * (1 + RULES.upgPct * g.upg);
  const crit = st.crit && g.rand() < st.crit;
  if (crit) dmg *= 2;
  const hits = [best];
  if (st.kind === 'area' || st.elem === 'FIRE') {
    const r = st.kind === 'area' ? st.area : 50;
    for (const e of g.enemies) if (e !== best && e.hp > 0 && Math.hypot(e.x - best.x, e.y - best.y) <= r) hits.push(e);
  }
  hits.forEach((e, k) => hurt(g, e, k === 0 || st.kind === 'area' ? dmg : dmg * 0.5, st.elem));
  if (st.elem === 'LIGHTNING') {
    const near = g.enemies.filter((e) => e !== best && e.hp > 0).map((e) => [e, Math.hypot(e.x - best.x, e.y - best.y)]).filter(([, d]) => d <= 90).sort((a, b) => a[1] - b[1]).slice(0, 2);
    for (const [e] of near) { hurt(g, e, dmg * 0.6, st.elem); g.fx.push({ kind: 'zap', x0: best.x, y0: best.y, x1: e.x, y1: e.y, t: g.t }); }
  }
  for (const e of hits) {
    if (st.elem === 'WATER') { e.slow = Math.max(e.slow, 0.2); e.slowUntil = g.t + 1; }
    if (st.elem === 'ICE') { e.slow = Math.max(e.slow, 0.35); e.slowUntil = g.t + 1.2; }
    if (st.elem === 'NATURE') { e.poison = Math.max(e.poison, dmg * 0.2); e.poisonUntil = g.t + 2; }
    if (st.elem === 'DARK') e.breakUntil = g.t + 2;
  }
  if (st.stun && g.rand() < st.stun) best.stunUntil = g.t + (best.boss ? 0.15 : 0.5);
  g.fx.push({ kind: 'shot', slot: i, x1: best.x, y1: best.y, elem: st.elem, crit, t: g.t });
  return true;
}

// 한 걸음(dt초). 화면은 이 함수만 부른다
export function step(g, dt) {
  if (g.over || g.reward) return;
  g.t += dt;
  if (g.wave === 0) startWave(g);
  const wi = waveInfo(g.wave);
  // 생성
  if (g.spawnLeft > 0) {
    g.spawnT -= dt;
    if (g.spawnT <= 0) { spawn(g); g.spawnLeft--; g.spawnT = RULES.spawnGap; }
  }
  // 이동·독
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    if (g.t >= e.slowUntil) e.slow = 0;
    const v = g.t < e.stunUntil ? 0 : e.speed * (1 - e.slow);
    e.s += v * dt;
    const p = pathPos(e.s); e.x = p.x; e.y = p.y;
    if (g.t < e.poisonUntil) e.hp -= e.poison * dt;
  }
  // 갈매미 공격
  for (let i = 0; i < SLOTS; i++) {
    const u = g.units[i];
    if (!u) continue;
    u.cd -= dt;
    if (u.cd > 0) continue;
    const st = unitStats(u.id);
    u.cd = fire(g, i, u, st) ? st.gap : 0.1;
  }
  // 처치
  let bossDown = false;
  g.enemies = g.enemies.filter((e) => {
    if (e.hp > 0) return true;
    g.gold += e.gold; g.kills++;
    if (e.boss) bossDown = true;
    return false;
  });
  g.fx = g.fx.filter((f) => g.t - f.t < 0.6);
  // 패배 조건
  if (g.enemies.length > RULES.limit) { g.over = 'lose'; g.reason = `길 위의 적이 ${RULES.limit}마리를 넘었어요.`; return; }
  if (wi.boss) {
    if (bossDown) {
      if (g.wave >= RULES.waves) { g.over = 'win'; g.reason = `${RULES.waves}웨이브 보스까지 모두 막았어요!`; return; }
      const pool = [...HIDDENS], pick = [];
      while (pick.length < 3 && pool.length) pick.push(pool.splice(Math.floor(g.rand() * pool.length), 1)[0]);
      g.reward = pick;
      return;
    }
    if (g.t > g.bossDeadline) { g.over = 'lose'; g.reason = `보스를 ${RULES.bossTime}초 안에 잡지 못했어요.`; return; }
  } else {
    g.waveT += dt;
    if (g.waveT >= RULES.waveLen) startWave(g);
  }
}

export const nextWaveIn = (g) => (waveInfo(g.wave).boss ? Math.max(0, g.bossDeadline - g.t) : Math.max(0, RULES.waveLen - g.waveT));
