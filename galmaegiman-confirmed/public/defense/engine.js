// 갈매미맨 랜덤 디펜스 2판: 게임 규칙(화면과 분리). 브라우저와 Node 시험에서 같이 쓴다.
// - 위습: 처음 커먼 랜덤 위습 5개 + 웨이브마다 1개. 위습 하나로 그 등급 갈매미 1마리를 무작위로 부른다.
//   위습 등급은 커먼·언커먼·레어·에픽(보스·외부탐사 보상), 전설은 일부 갈매미의 「위습 찾기」 재주로만.
// - 물딜(타격수)·마딜(술사). 고체력·기습은 딜이 낮은 대신 방깍·스턴·이속감소 / 마방깍·마딜증폭.
// - 갈매미는 판 어디에나 둘 수 있고 겹쳐도 된다(수 제한 없음). 조합은 게임 속 조합법 52개(히든 포함).
// - 웨이브마다 35마리. 길 위의 적이 70마리를 넘은 동안 1초마다 경고 1회(누적), 20회를 넘으면 패배.
// - 10웨이브마다 보스. 그 라운드 안에 못 잡으면 바로 패배. 중후반엔 외부탐사(스토리 지역)에 갈매미를 보내 보상 위습.
import { UNITS, RECIPES } from './data.js?v=5e9fd463c893';

export const GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'];
export const GRADE_NAME = { COMMON: '커먼', UNCOMMON: '언커먼', RARE: '레어', EPIC: '에픽', LEGEND: '전설', HIDDEN: '히든', LIMITED: '제한', TRANSCEND: '초월', IMMORTAL: '불멸', ETERNAL: '영원' };
export const WISP_GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGEND'];
export const UNIT = Object.fromEntries(UNITS.map((u) => [u.id, u]));
export { UNITS, RECIPES };
export const gradeTier = (g) => GRADES.indexOf(g);
export const tierOf = (id) => gradeTier(UNIT[id].grade);

// ── 판 모양(논리 좌표 540×960) ──
export const W = 540, H = 960;
export const FIELD = { x: 14, y: 100, w: 512, h: 564 };     // 갈매미를 둘 수 있는 곳
export const LOOP = { x0: 46, y0: 134, x1: 494, y1: 630 };  // 적이 도는 길(중심선)
export const SPAWN = { x: 270, y: 382 };                    // 새로 부른 갈매미가 나타나는 곳
const LW = LOOP.x1 - LOOP.x0, LH = LOOP.y1 - LOOP.y0, PERIM = 2 * (LW + LH);

// ── 규칙 수치(시험 봇으로 맞춤) ──
export const RULES = {
  waves: 60, waveLen: 40,   // 60웨이브(사용자 요청: 40에서 끝내지 말고 더)
  perWave: 35, spawnGap: 0.75, limit: 70, warnMax: 20,
  bossEvery: 10, startWisps: 6, wispPerWave: 2,   // 처음 커먼 위습 6개, 웨이브마다 2개(사용자 요청 2026-11-08)
  hpBase: 55, hpGrowth: 1.16, hpLinear: 25, bossMul: [3.5, 3, 3, 2, 2, 2],   // 보스 체력 = 그 웨이브 적 체력 × (10·20·30·40)
  // 적 체력 기준점 [웨이브, 체력](사이는 곱으로 이음). 1~10웨이브는 「초반이 많이 어렵다」는 의견으로 ×0.7. 사용자 요청(2026-11-08): 3웨이브쯤부터 커먼 풀강이 아니면 쌓이고,
  // 5웨이브쯤부터는 커먼만으로는 못 버티게. 등급별로 조합하는 봇이 버티는 한계를 재서 맞춤
  hpAnchors: [[1, 146], [2, 280], [3, 426], [4, 627], [5, 952], [6, 1120], [7, 1232], [8, 1400], [10, 1624], [12, 4160], [15, 6720], [20, 11700], [25, 25920], [30, 43200], [35, 115200], [40, 172800], [45, 304000], [50, 560000], [55, 1040000], [60, 1920000]],   // 위습 웨이브당 2개로 늘린 뒤 다시 맞춤
  armorBase: 4, armorPer: 2.4,
  // 등급별 기본 공격력(주력 기준 한 번 때릴 때)과 판매값(새우)
  dmg: [20, 90, 380, 1500, 5500, 7500, 18000, 50000, 140000, 400000],   // 등급마다 약 4배: 조합하면 확실히 세지게
  sell: [5, 15, 40, 90, 200, 250, 400, 800, 1600, 3200],
  // 처치 새우: 웨이브가 갈수록 많이(사용자 요청). 1마리당 killGold + killGoldGrow×웨이브(반올림)
  // 처치 새우 = killGold + killGoldGrow×웨이브 + killGoldSq×웨이브² (후반일수록 많이: 후반 강화 비용에 맞춤)
  killGold: 1, killGoldGrow: 0.6, killGoldSq: 0.02,
  startUncommon: 1,   // 처음 언커먼 위습 1개(사용자 요청)
  startGold: 40,   // 처음 새우: 커먼·언커먼 강화 5단계까지(5+6+7+9+10=37) 바로 가능(사용자 요청)
  // 등급별 강화(사용자 요청 2026-11-08): 커먼·언커먼·레어·에픽·전설 이상 5칸, 한 단계마다 그 등급 공격력 +14%
  // 칸: 커먼·언커먼(묶음) · 레어 · 에픽 · 전설 · 히든 · 제한 · 초월 · 불멸 · 영원 (사용자 요청 2026-11-08)
  // 칸마다 최대 upgMax단계(풀강). 비용 = upgBase × upgGrowth^단계. 등급이 높을수록 훨씬 비쌈(초반과 상위 강화 비용이 비슷하지 않게)
  // 칸: 커먼·언커먼 · 레어 · 에픽 · 전설·히든(묶음) · 제한 · 초월 · 불멸 · 영원 (8칸)
  upgPct: 0.15, upgMax: 10, upgGrowth: 1.2, upgBase: [5, 20, 60, 150, 600, 600, 600, 600],   // 제한·초월·불멸·영원은 같은 가격(사용자 요청)
  bossReward: { 10: { RARE: 1 }, 20: { EPIC: 1 }, 30: { EPIC: 2 }, 40: { EPIC: 2 }, 50: { EPIC: 3 } },
  // 선택 위습(원하는 갈매미를 골라 소환): 처음 0개, 스토리(외부탐사)를 깰 때마다 받음(REGIONS의 pick). 보스는 랜덤 위습만
  startPick: {}, bossPick: {},
  // 외부탐사 장별 대장 체력(사용자 요청: 10웨이브쯤 2장은 에픽 1마리를 보내야 깨지는 정도)
  // 3·4장: 에픽으로 3웨이브쯤 · 5·6장: 전설이 있어야 · 7장: 상위(히든 이상) 1마리는 있어야(사용자 요청)
  guardHp: [20000, 150000, 500000, 1000000, 3000000, 5000000, 15000000],
  guardMul: 12,   // 외부탐사 대장 체력 = (지역 level+2 웨이브)의 적 체력 × 이 값
};

// 외부탐사: 갈매미맨 메인 스토리 7장 지역. 처음부터 1장을 보낼 수 있고, 앞 장을 끝내면 다음 장이 열린다(사용자 요청 2026-11-08).
// 보낸 갈매미가 그곳의 그림자 대장을 쓰러뜨리면 보상 위습(점점 높게). level은 대장 세기 기준 웨이브(열리는 시점과 무관).
export const REGIONS = [
  { ch: 1, name: '돌이 된 태초 갈매미', place: '마을 석상 터', img: 'identity-statue', look: 'u01', level: 8, reward: { UNCOMMON: 2 }, pick: { UNCOMMON: 1 }, gold: 150 },   // 레어 하나에 빠진 언커먼을 골라 에픽을 만들 수 있게
  { ch: 2, name: '인간들이 기억하는 갈매미', place: '인간의 기록 장소', img: 'identity-records', look: 'u06', level: 12, reward: { RARE: 1, UNCOMMON: 1 }, pick: { UNCOMMON: 1 }, gold: 400 },
  { ch: 3, name: '사라진 기록', place: '인간의 기록 장소', img: 'identity-records', look: 'u07', level: 16, reward: { RARE: 2 }, pick: { UNCOMMON: 1 }, gold: 800 },
  { ch: 4, name: '말하지 않는 노인', place: '은신처', img: 'identity-hq', look: 'u23', level: 20, reward: { EPIC: 1 }, pick: { RARE: 1 }, gold: 1500 },
  { ch: 5, name: '죽은 적이 없는 친구', place: '기억 속 작업장', img: 'identity-records', look: 'u14', level: 24, reward: { EPIC: 1, RARE: 1 }, pick: { RARE: 1 }, gold: 3000 },
  { ch: 6, name: '함께 돌아오는 길', place: '마을 방어 준비', img: 'identity-hq', look: 'u28', level: 28, reward: { EPIC: 2 }, pick: { EPIC: 1 }, gold: 5000 },
  { ch: 7, name: '최초의 균열', place: '석상 앞', img: 'identity-statue', look: 'u31', level: 32, reward: { EPIC: 2, RARE: 1 }, pick: { EPIC: 1 }, gold: 9000 },
];

// 위습 찾기 재주: 상위 갈매미만(사용자 요청 2026-11-08). 적을 쓰러뜨리면 확률로 위습(전설 위습은 여기서만)
// 황금 계열의 상위: 거장(전설) · 금각(히든) · 가루다(초월) · 리치(불멸) · 여명(영원)
export const WISP_SKILL = {
  u33: { p: 0.06, table: { RARE: 0.5, EPIC: 0.4, LEGEND: 0.1 } },
  u43: { p: 0.07, table: { RARE: 0.4, EPIC: 0.45, LEGEND: 0.15 } },
  u49: { p: 0.07, table: { EPIC: 0.75, LEGEND: 0.25 } },
  u56: { p: 0.08, table: { EPIC: 0.65, LEGEND: 0.35 } },
  u57: { p: 0.08, table: { EPIC: 0.5, LEGEND: 0.5 } },
};

const ELEM = { 불: 'FIRE', 물: 'WATER', 얼음: 'ICE', 번개: 'LIGHTNING', 자연: 'NATURE', 성: 'HOLY', 성술: 'HOLY', 어둠: 'DARK' };
export const ELEM_NAME = { FIRE: '불', WATER: '물', ICE: '얼음', LIGHTNING: '번개', NATURE: '자연', HOLY: '성', DARK: '어둠', NORMAL: '무' };
// 유틸 세기: 커먼~레어는 아주 작게, 에픽부터 의미 있게(사용자 요청 2026-11-08)
const SHRED = (t) => (t < 3 ? [2, 4, 7][t] : 20 + 15 * (t - 3));            // 방깍·마방깍: 커먼 2 · 언커먼 4 · 레어 7 · 에픽 20 · 전설 35 …
const AMP = (t) => (t < 3 ? [0.02, 0.03, 0.05][t] : 0.15 + 0.05 * (t - 3));  // 마딜증폭: 커먼 2% … 에픽 15% · 전설 20% …
// 이속감소는 갈매미마다 따로 걸려 모두 더한다(사용자 요청 2026-11-08: 20%씩 쌓여 100%가 되면 많이 느리게).
// 합계는 100%까지, 실제 속도 = 1 − 0.85×합계(합계 100%면 원래 속도의 15%로 기어감, 완전히 멈추는 건 스턴).
export const SLOW_MAX = 1, SLOW_EFFECT = 0.85;
export const slowMul = (total) => 1 - SLOW_EFFECT * Math.min(SLOW_MAX, total);
const SLOW = (t) => (t < 3 ? [0.06, 0.08, 0.12][t] : Math.min(0.5, 0.25 + 0.04 * (t - 3)));  // 이속감소
const STUN = (t) => (t < 3 ? [0.03, 0.05, 0.08][t] : Math.min(0.35, 0.18 + 0.03 * (t - 3))); // 스턴 확률
// 범위 스턴(전설 이상 보조): 공격마다 확률로 맞은 적 둘레 70 안 모두 스턴. 초월 이상은 20%(사용자 요청: 라인이 안 밀리게)
const ASTUN = (t) => (t < 4 ? 0 : t >= 7 ? 0.2 : [0.12, 0.14, 0.16][t - 4]);

// 갈매미 한 마리의 공격 방식
export function kit(id) {
  const u = UNIT[id], t = tierOf(id), role = u.role, elem = ELEM[role.split(' ')[0]] ?? 'NORMAL';
  const tank = role.startsWith('고체력'), ambush = role.includes('기습'), mage = role.endsWith('술사');
  const type = mage ? 'mag' : 'phys';
  let k;
  if (tank) k = { job: '보조', gap: 1.1, mul: 0.35, range: 150, stun: STUN(t), shred: SHRED(t), ...(ASTUN(t) ? { astun: ASTUN(t) } : {}) };
  else if (ambush && mage) k = { job: '보조', gap: 0.6, mul: 0.3, range: 170, mshred: SHRED(t), amp: AMP(t) };
  else if (ambush) k = { job: '보조', gap: 0.5, mul: 0.3, range: 160, shred: SHRED(t), slow: SLOW(t) };
  else if (role.endsWith('근접술사')) k = { job: '주력', gap: 0.9, mul: 0.75, range: 140, area: 70 };
  else if (mage) k = { job: '주력', gap: 1.1, mul: 1.4, range: 230 };
  else k = { job: '주력', gap: 0.8, mul: 1, range: 170 };
  if (k.job === '주력') {   // 주력의 속성 덤(작게)
    if (elem === 'FIRE') k.splash = 0.4;
    if (elem === 'LIGHTNING') k.chain = 2;
    if (elem === 'ICE') k.slow = SLOW(t) * 0.8;
    if (elem === 'WATER') k.slow = SLOW(t) * 0.5;
    if (elem === 'NATURE') k.poison = 0.25;
    if (elem === 'DARK') { if (mage) k.mshred = SHRED(t) * 0.5; else k.shred = SHRED(t) * 0.5; }
    if (elem === 'HOLY') k.holy = 1.5;
  }
  return { ...k, type, elem, tier: t, dmg: Math.round(RULES.dmg[t] * k.mul), sell: RULES.sell[t], wisp: WISP_SKILL[id] ?? null };
}
// 화면용 짧은 설명
export function kitTips(k) {
  const out = [];
  if (k.shred) out.push(`방깍 ${Math.round(k.shred)}`);
  if (k.mshred) out.push(`마방깍 ${Math.round(k.mshred)}`);
  if (k.amp) out.push(`마딜증폭 ${Math.round(k.amp * 100)}%`);
  if (k.stun) out.push(`스턴 ${Math.round(k.stun * 100)}%`);
  if (k.astun) out.push(`범위 스턴 ${Math.round(k.astun * 100)}%`);
  if (k.slow) out.push(`이속감소 ${Math.round(k.slow * 100)}%(겹침)`);
  if (k.area) out.push('주변 범위');
  if (k.splash) out.push('불꽃 튐');
  if (k.chain) out.push('번개 연쇄');
  if (k.poison) out.push('독');
  if (k.holy) out.push('보스 추가 피해');
  if (k.wisp) out.push('위습 찾기');
  return out;
}

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
export const clampField = (x, y) => ({ x: Math.max(FIELD.x + 18, Math.min(FIELD.x + FIELD.w - 18, x)), y: Math.max(FIELD.y + 26, Math.min(FIELD.y + FIELD.h - 8, y)) });

export function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const BY_GRADE = Object.fromEntries(GRADES.map((g) => [g, UNITS.filter((u) => u.grade === g).map((u) => u.id)]));

// 웨이브 정보: 일반 → 갑옷(방어력 높음) → 결계(마법방어 높음) 순서, 10웨이브마다 보스
export function waveInfo(w) {
  const boss = w % RULES.bossEvery === 0;
  // 적 체력: 초반(hpEarlyN웨이브까지)은 hpEarly배씩 가파르게, 그 뒤는 hpGrowth배씩
  const nE = RULES.hpEarlyN ?? 0, eW = Math.min(w - 1, nE), lW = Math.max(0, w - 1 - nE);
  let hp = Math.round(RULES.hpBase * Math.pow(RULES.hpEarly ?? RULES.hpGrowth, eW) * Math.pow(RULES.hpGrowth, lW) * (1 + w / RULES.hpLinear));
  // 기준점 표가 있으면 그 사이를 곱으로 이어 쓴다(등급별 봇이 버티는 한계에 맞춘 값)
  const A = RULES.hpAnchors;
  if (A && A.length) {
    let k = A.findIndex(([aw]) => aw >= w);
    if (k < 0) hp = Math.round(A[A.length - 1][1]);
    else if (k === 0 || A[k][0] === w) hp = Math.round(A[k][1]);
    else { const [w0, h0] = A[k - 1], [w1, h1] = A[k]; hp = Math.round(h0 * Math.pow(h1 / h0, (w - w0) / (w1 - w0))); }
  }
  const base = RULES.armorBase + RULES.armorPer * w;
  const kind = boss ? 'boss' : ['결계', '일반', '갑옷'][w % 3];
  const armor = Math.round(base * (kind === '갑옷' ? 2 : kind === '결계' ? 0.5 : kind === 'boss' ? 1.5 : 1));
  const mr = Math.round(base * (kind === '결계' ? 2 : kind === '갑옷' ? 0.5 : kind === 'boss' ? 1.5 : 1));
  const pool = UNITS.filter((u) => gradeTier(u.grade) <= Math.min(9, Math.floor((w - 1) / 4)));
  const look = boss ? ['u33', 'u38', 'u52', 'u60', 'u57', 'u58'][Math.min(5, w / RULES.bossEvery - 1)] : pool[(w * 7) % pool.length].id;
  return { boss, kind, armor, mr, look, baseHp: hp, hp: boss ? hp * RULES.bossMul[Math.min(RULES.bossMul.length, w / RULES.bossEvery) - 1] : hp,
    speed: boss ? 34 : 56 + Math.min(30, w), gold: Math.round((boss ? 30 : 1) * (RULES.killGold + RULES.killGoldGrow * w + (RULES.killGoldSq ?? 0) * w * w)) };
}
export function regionGuard(k) {
  const r = REGIONS[k], wi = waveInfo(r.level + 2);
  // 보스 웨이브여도 보스 배수는 쓰지 않는다(예전엔 1장 대장이 보스 체력×25가 되어 깰 수 없었음)
  const hp = RULES.guardHp?.[k] ?? Math.round(wi.baseHp * RULES.guardMul);
  return { hp, armor: Math.round(wi.armor * 1.3), mr: Math.round(wi.mr * 1.3) };
}

export function newGame(seed = (Math.random() * 2 ** 32) >>> 0) {
  return {
    seed, rand: rng(seed), t: 0, gold: RULES.startGold ?? 0, kills: 0, upg: Array(8).fill(0),
    wave: 0, waveT: 0, spawnLeft: 0, spawnT: 0,
    warn: 0, warnAcc: 0,
    wisps: { COMMON: RULES.startWisps, UNCOMMON: RULES.startUncommon ?? 0, RARE: 0, EPIC: 0, LEGEND: 0 },
    picks: { COMMON: 0, UNCOMMON: 0, RARE: 0, EPIC: 0, ...RULES.startPick },
    enemies: [], units: [], nextId: 1, nextUid: 1,
    goals: [], mission: null, missionDone: 0, autoStore: false, missionDmg: [], peerDmg: null,
    over: null, reason: '', fx: [], news: [],
  };
}
const news = (g, msg) => { g.news.push({ msg, t: g.t }); if (g.news.length > 6) g.news.shift(); };
const addWisps = (g, bag) => { for (const [k, v] of Object.entries(bag)) g.wisps[k] = (g.wisps[k] ?? 0) + v; };
export const wispText = (bag) => Object.entries(bag).map(([k, v]) => `${GRADE_NAME[k]} 위습 ${v}개`).join(' · ');
export const pickText = (bag) => Object.entries(bag).map(([k, v]) => `${GRADE_NAME[k]} 선택 위습 ${v}개`).join(' · ');
export const PICK_GRADES = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC'];
export const unitsOfGrade = (grade) => BY_GRADE[grade] ?? [];

// 판에서 싸우는 갈매미(탐사 중·창고 제외)
export const fieldUnits = (g) => g.units.filter((u) => !u.away && !u.stored);
// 창고: 판에서 빠져 공격은 안 하지만 조합 재료로는 쓰인다(사용자 요청 2026-11-08)
export const storedUnits = (g) => g.units.filter((u) => u.stored);
const nearSpawn = (g, u) => { const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 40; const p = clampField(SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.x = u.tx = p.x; u.y = u.ty = p.y; };
export function store(g, uids) {
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && !u.away && !u.stored) { u.stored = true; n++; } }
  return n;
}
export function unstore(g, uids) {
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && u.stored) { u.stored = false; nearSpawn(g, u); u.cd = 0.3; n++; } }
  return n;
}
// 커먼 자동 창고: 켜면 판의 커먼을 창고로, 이후 새로 부른 커먼도 바로 창고로
export function setAutoStore(g, on) {
  g.autoStore = !!on;
  return on ? store(g, fieldUnits(g).filter((u) => UNIT[u.id].grade === 'COMMON').map((u) => u.uid)) : 0;
}
const autoStoreNew = (g, u) => { if (g.autoStore && UNIT[u.id].grade === 'COMMON') u.stored = true; };
// 같은 갈매미 모으기: 판의 같은 종류를 한 자리로
export function gather(g, uid) {
  const t = unitById(g, uid);
  if (!t || t.away || t.stored) return [];
  const list = fieldUnits(g).filter((u) => u.id === t.id);
  for (const u of list) { u.tx = t.tx; u.ty = t.ty; }
  return list.map((u) => u.uid);
}
export const unitById = (g, uid) => g.units.find((u) => u.uid === uid) ?? null;

function addUnit(g, id, x, y) {
  const p = clampField(x, y);
  const u = { uid: g.nextUid++, id, x: p.x, y: p.y, tx: p.x, ty: p.y, cd: 0.2 + g.rand() * 0.3, born: g.t, away: false };
  g.units.push(u);
  return u;
}

// 위습 하나로 그 등급 갈매미 1마리
export function summon(g, grade = 'COMMON') {
  if (g.over) return { ok: false, why: '' };
  if (!(g.wisps[grade] > 0)) return { ok: false, why: `${GRADE_NAME[grade]} 위습이 없어요.` };
  g.wisps[grade]--;
  const pool = BY_GRADE[grade], id = pool[Math.floor(g.rand() * pool.length)];
  const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 34;
  const u = addUnit(g, id, SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r);
  autoStoreNew(g, u);
  g.fx.push({ kind: 'combine', x: u.x, y: u.y, t: g.t });
  return { ok: true, unit: u };
}
// 선택 위습: 그 등급의 원하는 갈매미 1마리
export function summonPick(g, id) {
  const grade = UNIT[id]?.grade;
  if (g.over || !grade) return { ok: false, why: '' };
  if (!(g.picks[grade] > 0)) return { ok: false, why: `${GRADE_NAME[grade]} 선택 위습이 없어요.` };
  g.picks[grade]--;
  const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 34;
  const u = addUnit(g, id, SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r);
  autoStoreNew(g, u);
  g.fx.push({ kind: 'combine', x: u.x, y: u.y, t: g.t });
  return { ok: true, unit: u };
}
export function sell(g, uid) {
  const k = g.units.findIndex((u) => u.uid === uid);
  if (k < 0 || g.over) return false;
  g.gold += kit(g.units[k].id).sell; g.units.splice(k, 1);
  return true;
}
// 옮기기: 목표 자리로 걸어간다(걷는 중에도 공격). now=true면 바로 그 자리
export const WALK = 320;
export function moveUnit(g, uid, x, y, now = false) {
  const u = unitById(g, uid);
  if (!u || u.away) return false;
  const p = clampField(x, y); u.tx = p.x; u.ty = p.y;
  if (now) { u.x = p.x; u.y = p.y; }
  return true;
}
// 등급별 강화 칸: 0 커먼 · 1 언커먼 · 2 레어 · 3 에픽 · 4 전설 이상(전설·히든·제한·초월·불멸·영원)
export const UPG_NAME = ['커먼·언커먼', '레어', '에픽', '전설·히든', '제한', '초월', '불멸', '영원'];
export const UPG_COUNT = UPG_NAME.length;
// 등급(0 커먼 … 9 영원) → 강화 칸: 커먼·언커먼 0 · 레어 1 · 에픽 2 · 전설·히든 3 · 제한 4 · 초월 5 · 불멸 6 · 영원 7 (사용자 요청: 전설·히든 한 번에)
export const upgGroup = (tier) => [0, 0, 1, 2, 3, 3, 4, 5, 6, 7][tier];
// 강화 비용: 단계마다 upgGrowth배(곱으로 늘어 낮은 등급만 계속 강화해 버티는 것을 막음)
export const upgradeCost = (g, gi) => Math.round(RULES.upgBase[gi] * Math.pow(RULES.upgGrowth ?? 1, g.upg[gi]) + (RULES.upgGrowth ? 0 : RULES.upgStep[gi] * g.upg[gi]));
export const upgMul = (g, tier) => 1 + RULES.upgPct * g.upg[upgGroup(tier)];
export const upgMaxed = (g, gi) => g.upg[gi] >= RULES.upgMax;
export function upgrade(g, gi) {
  const cost = upgradeCost(g, gi);
  if (g.over) return { ok: false, why: '' };
  if (upgMaxed(g, gi)) return { ok: false, why: '이미 최대 단계(풀강)예요.' };
  if (g.gold < cost) return { ok: false, why: `새우가 모자라요(${cost} 필요).` };
  g.gold -= cost; g.upg[gi]++;
  return { ok: true };
}

// 조합: 판(탐사 중 제외)에 재료가 다 있으면 쓸 갈매미 uid 목록(keep을 먼저)
export function recipeUnits(g, r, keepUid = 0) {
  const avail = [...g.units].sort((a, b) => (a.away - b.away)), used = new Set(), out = [], need = [...r.in];   // 판·창고·스토리(외부탐사) 것 모두 재료(판 것을 먼저 씀)
  if (keepUid) {
    const ku = avail.find((u) => u.uid === keepUid);
    const k = ku ? need.indexOf(ku.id) : -1;
    if (k < 0) return null;
    need.splice(k, 1); used.add(ku.uid); out.push(ku.uid);
  }
  for (const id of need) {
    const f = avail.find((u) => !used.has(u.uid) && u.id === id);
    if (!f) return null;
    used.add(f.uid); out.push(f.uid);
  }
  return out;
}
export function combine(g, r, keepUid = 0) {
  if (g.over) return null;
  const uids = recipeUnits(g, r, keepUid);
  if (!uids) return null;
  // 조합 결과는 판 가운데(소환되는 곳)에 나온다(사용자 요청: 어디 생겼는지 헷갈리지 않게)
  const a = g.rand() * Math.PI * 2, rr = 10 + g.rand() * 30, x = SPAWN.x + Math.cos(a) * rr, y = SPAWN.y + Math.sin(a) * rr;
  g.units = g.units.filter((u) => !uids.includes(u.uid));
  const nu = addUnit(g, r.out, x, y);
  g.fx.push({ kind: 'combine', x, y, t: g.t });
  return nu;
}
export const recipesUsing = (id) => RECIPES.filter((r) => r.in.includes(id));
export const recipeFor = (id) => RECIPES.find((r) => r.out === id) ?? null;
export function countOnField(g) {
  const c = {};
  for (const u of g.units) c[u.id] = (c[u.id] ?? 0) + 1;   // 창고·스토리 것 포함(조합 재료)
  return c;
}
export const GOAL_MAX = 3;
export function toggleGoal(g, out) {
  const k = g.goals.indexOf(out);
  if (k >= 0) { g.goals.splice(k, 1); return false; }
  if (g.goals.length >= GOAL_MAX) g.goals.shift();
  g.goals.push(out);
  return true;
}

// ── 외부탐사 ──
export const missionOpen = (g) => g.missionDone < REGIONS.length;
function ensureMission(g) {
  if (!missionOpen(g)) return null;
  if (!g.mission || g.mission.k !== g.missionDone) {
    const gd = regionGuard(g.missionDone);
    g.mission = { k: g.missionDone, hp: gd.hp, max: gd.hp, armor: gd.armor, mr: gd.mr, boss: true, shred: 0, shredUntil: 0, mshred: 0, mshredUntil: 0, amp: 0, ampUntil: 0, x: 0, y: 0 };
  }
  return g.mission;
}
export const missionTarget = (g) => ensureMission(g);
// 같이하기(사용자 요청 2026-11-08: 외부탐사는 둘이서 같이): 두 사람이 같은 장의 대장을 함께 친다.
// 장마다 내가 준 피해(missionDmg)를 친구에게 보내고, 친구가 준 피해(peerDmg)를 받아 대장 체력 = 최대 − 내 피해 − 친구 피해.
// 장별로 쌓아 두므로 한쪽이 먼저 깨도 마지막 피해가 다른 쪽에 전해져 둘 다 같은 장을 깬다. 보상은 각자 전부 받는다.
export function setPeerMission(g, arr) { g.peerDmg = Array.isArray(arr) ? arr.map((v) => Math.max(0, Number(v) || 0)) : g.peerDmg; }
export const missionShare = (g) => { const k = g.missionDone; return { mine: g.missionDmg[k] ?? 0, peer: g.peerDmg?.[k] ?? 0 }; };
export function sendAway(g, uids) {
  if (!ensureMission(g)) return 0;
  let n = 0;
  for (const uid of uids) { const u = unitById(g, uid); if (u && !u.away && !u.stored) { u.away = true; u.cd = 0.3; n++; } }
  return n;
}
export function recall(g, uids = null) {
  let n = 0;
  for (const u of g.units) if (u.away && (!uids || uids.includes(u.uid))) {
    u.away = false; const a = g.rand() * Math.PI * 2, r = 10 + g.rand() * 40;
    const p = clampField(SPAWN.x + Math.cos(a) * r, SPAWN.y + Math.sin(a) * r); u.x = u.tx = p.x; u.y = u.ty = p.y; n++;
  }
  return n;
}
export const awayUnits = (g) => g.units.filter((u) => u.away);

// ── 싸움 ──
function hurt(g, e, raw, k, uid) {
  if (e.hp <= 0) return;
  const now = g.t;
  let m;
  if (k.type === 'phys') {
    const A = Math.max(0, e.armor - (now < e.shredUntil ? e.shred : 0));
    m = (100 / (100 + A)) * upgMul(g, k.tier);
  } else {
    const M = Math.max(0, e.mr - (now < e.mshredUntil ? e.mshred : 0));
    m = (100 / (100 + M)) * (1 + (now < e.ampUntil ? e.amp : 0)) * upgMul(g, k.tier);
  }
  if (k.holy && e.boss) m *= k.holy;
  e.hp -= raw * m; e.by = uid;
}
function debuff(g, e, k, uid = 0) {
  const now = g.t;
  if (k.shred) { e.shred = now < e.shredUntil ? Math.max(e.shred, k.shred) : k.shred; e.shredUntil = now + 3; }
  if (k.mshred) { e.mshred = now < e.mshredUntil ? Math.max(e.mshred, k.mshred) : k.mshred; e.mshredUntil = now + 3; }
  if (k.amp) { e.amp = now < e.ampUntil ? Math.max(e.amp, k.amp) : k.amp; e.ampUntil = now + 3; }
  if (k.slow && e.speed) {   // 같은 갈매미는 다시 맞혀도 시간만 늘고, 다른 갈매미 것은 더해진다
    (e.slows ??= {})[uid] = { v: k.slow, until: now + 1.5 };
    e.slowUntil = now + 1.5;
  }
  if (k.stun && e.speed && g.rand() < k.stun) e.stunUntil = now + (e.boss ? 0.12 : 0.5);
  if (k.astun && e.speed && g.rand() < k.astun) for (const o of g.enemies) if (o.hp > 0 && Math.hypot(o.x - e.x, o.y - e.y) <= 70) o.stunUntil = Math.max(o.stunUntil, now + (o.boss ? 0.15 : 0.7));
  if (k.poison) { e.poison = Math.max(now < e.poisonUntil ? e.poison : 0, k.dmg * k.poison); e.poisonK = k; e.poisonUntil = now + 2; }
}
function attack(g, u, k) {
  // 공격 대상: 사거리 안에서 가장 가까운 적 먼저(사용자 요청 2026-11-08)
  let best = null, bestD = Infinity;
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    const dx = e.x - u.x, dy = e.y - u.y, d2 = dx * dx + dy * dy;
    if (d2 > k.range * k.range || d2 >= bestD) continue;
    best = e; bestD = d2;
  }
  if (!best) return false;
  const hits = [best];
  if (k.area || k.splash) {
    const r = k.area ?? 50;
    for (const e of g.enemies) if (e !== best && e.hp > 0 && Math.hypot(e.x - best.x, e.y - best.y) <= r) hits.push(e);
  }
  hits.forEach((e, i) => { hurt(g, e, i === 0 || k.area ? k.dmg : k.dmg * k.splash, k, u.uid); debuff(g, e, k, u.uid); });
  if (k.chain) {
    const near = g.enemies.filter((e) => e !== best && e.hp > 0).map((e) => [e, Math.hypot(e.x - best.x, e.y - best.y)]).filter(([, d]) => d <= 90).sort((a, b) => a[1] - b[1]).slice(0, k.chain);
    for (const [e] of near) { hurt(g, e, k.dmg * 0.5, k, u.uid); g.fx.push({ kind: 'zap', x0: best.x, y0: best.y, x1: e.x, y1: e.y, t: g.t }); }
  }
  g.fx.push({ kind: 'shot', x0: u.x, y0: u.y, x1: best.x, y1: best.y, type: k.type, job: k.job, elem: k.elem, t: g.t });
  return true;
}

function startWave(g) {
  if (g.wave >= RULES.waves) return;
  if (g.wave > 0) addWisps(g, { COMMON: RULES.wispPerWave });
  g.wave++; g.waveT = 0;
  const wi = waveInfo(g.wave);
  g.spawnLeft = wi.boss ? 1 : RULES.perWave; g.spawnT = 0;
}
function spawn(g) {
  const wi = waveInfo(g.wave);
  g.enemies.push({ id: g.nextId++, hp: wi.hp, max: wi.hp, s: 0, speed: wi.speed, boss: wi.boss, look: wi.look, gold: wi.gold, armor: wi.armor, mr: wi.mr,
    shred: 0, shredUntil: 0, mshred: 0, mshredUntil: 0, amp: 0, ampUntil: 0, slow: 0, slowUntil: 0, stunUntil: 0, poison: 0, poisonUntil: 0, x: LOOP.x0, y: LOOP.y0, by: 0 });
}
function rollWisp(g, skill) {
  if (g.rand() >= skill.p) return null;
  let r = g.rand();
  for (const [grade, p] of Object.entries(skill.table)) { if ((r -= p) < 0) return grade; }
  return Object.keys(skill.table).pop();
}

// 한 걸음(dt초)
export function step(g, dt) {
  if (g.over) return;
  g.t += dt;
  if (g.wave === 0) startWave(g);
  const wi = waveInfo(g.wave);
  if (g.spawnLeft > 0) {
    g.spawnT -= dt;
    if (g.spawnT <= 0) { spawn(g); g.spawnLeft--; g.spawnT = RULES.spawnGap; }
  }
  for (const e of g.enemies) {
    if (e.hp <= 0) continue;
    e.slow = 0;
    if (e.slows) { for (const id in e.slows) { const sl = e.slows[id]; if (g.t >= sl.until) delete e.slows[id]; else e.slow += sl.v; } }
    e.slow = Math.min(SLOW_MAX, e.slow);   // 합계(화면에 보여 줌)
    const v = g.t < e.stunUntil ? 0 : e.speed * slowMul(e.slow);
    e.s += v * dt;
    const p = pathPos(e.s); e.x = p.x; e.y = p.y;
    if (g.t < e.poisonUntil) hurt(g, e, e.poison * dt, e.poisonK, e.by);
  }
  const byUid = new Map(g.units.map((u) => [u.uid, u]));
  if (g.peerDmg) ensureMission(g);   // 같이하기: 내가 아무도 안 보냈어도 친구 피해로 깰 수 있게
  const ms = g.mission && missionOpen(g) && g.mission.k === g.missionDone ? g.mission : null;
  for (const u of g.units) {
    if (u.stored) continue;   // 창고: 싸우지 않음
    if (!u.away && (u.x !== u.tx || u.y !== u.ty)) {
      const dx = u.tx - u.x, dy = u.ty - u.y, d = Math.hypot(dx, dy), m = WALK * dt;
      if (d <= m) { u.x = u.tx; u.y = u.ty; } else { u.x += dx / d * m; u.y += dy / d * m; }
    }
    u.cd -= dt;
    if (u.cd > 0) continue;
    const k = kit(u.id);
    if (u.away) {
      if (!ms) { u.cd = 0.2; continue; }
      const before = ms.hp;
      hurt(g, ms, k.dmg * (k.area ? 1.2 : 1), k, u.uid); debuff(g, ms, k, u.uid);
      g.missionDmg[ms.k] = (g.missionDmg[ms.k] ?? 0) + Math.max(0, before - ms.hp);
      u.cd = k.gap; continue;
    }
    u.cd = attack(g, u, k) ? k.gap : 0.1;
  }
  if (ms) ms.hp = ms.max - (g.missionDmg[ms.k] ?? 0) - (g.peerDmg?.[ms.k] ?? 0);
  // 처치
  let bossDown = false;
  g.enemies = g.enemies.filter((e) => {
    if (e.hp > 0) return true;
    g.gold += e.gold; g.kills++;
    if (e.boss) bossDown = true;
    const killer = byUid.get(e.by), sk = killer ? WISP_SKILL[killer.id] : null;
    if (sk) { const w = rollWisp(g, sk); if (w) { addWisps(g, { [w]: 1 }); news(g, `${UNIT[killer.id].name}이(가) ${GRADE_NAME[w]} 위습을 찾았어요!`); g.fx.push({ kind: 'wisp', x: e.x, y: e.y, grade: w, t: g.t }); } }
    return false;
  });
  // 외부탐사 대장 쓰러뜨림
  if (ms && ms.hp <= 0) {
    const r = REGIONS[ms.k];
    addWisps(g, r.reward);
    if (r.gold) g.gold += r.gold;
    if (r.pick) for (const [k, v] of Object.entries(r.pick)) g.picks[k] = (g.picks[k] ?? 0) + v;
    news(g, `${g.peerDmg ? '친구와 함께 ' : ''}외부탐사 「${r.name}」 성공! ${wispText(r.reward)}${r.pick ? ` · ${pickText(r.pick)}` : ''}${r.gold ? ` · 새우 ${r.gold}` : ''}${ms.k + 1 < REGIONS.length ? ` · ${ms.k + 2}장이 열렸어요` : ''}`);
    recall(g);
    g.missionDone++; g.mission = null;
  }
  g.fx = g.fx.filter((f) => g.t - f.t < 0.8);
  // 경고: 70마리를 넘은 동안 1초마다 1회(누적)
  if (g.enemies.length > RULES.limit) {
    g.warnAcc += dt;
    while (g.warnAcc >= 1) { g.warnAcc -= 1; g.warn++; }
    if (g.warn > RULES.warnMax) { g.over = 'lose'; g.reason = `경고가 ${RULES.warnMax}회를 넘었어요(길 위의 적 ${RULES.limit}마리 초과).`; return; }
  } else g.warnAcc = 0;
  // 보스·웨이브 진행
  if (wi.boss && bossDown) {
    if (g.wave >= RULES.waves) { g.over = 'win'; g.reason = `${RULES.waves}웨이브 보스까지 모두 막았어요!`; return; }
    const rw = RULES.bossReward[g.wave];
    if (rw) addWisps(g, rw);
    const rp = RULES.bossPick[g.wave];
    if (rp) for (const [k, v] of Object.entries(rp)) g.picks[k] = (g.picks[k] ?? 0) + v;
    if (rw || rp) news(g, `보스 처치! ${[rw && wispText(rw), rp && pickText(rp)].filter(Boolean).join(' · ')}`);
    g.bossDone = g.wave;
  }
  g.waveT += dt;
  if (g.waveT >= RULES.waveLen) {
    if (wi.boss && g.bossDone !== g.wave) { g.over = 'lose'; g.reason = `${g.wave}웨이브 보스를 라운드 안에 잡지 못했어요.`; return; }
    if (g.wave >= RULES.waves) return;
    startWave(g);
  }
}
export const nextWaveIn = (g) => Math.max(0, RULES.waveLen - g.waveT);
