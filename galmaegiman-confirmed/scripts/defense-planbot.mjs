// 랜덤 디펜스 밸런스 확인용 계획형 봇(2026-11-10). 예: node scripts/defense-planbot.mjs public/defense/engine.js 20 {} master mix
// 계획형 봇: 시작할 때 물리/마법 조합을 정하고, 「만들 목록」의 목표를 하나씩 — 그 목표 재료 나무에 있는 것만 조합한다.
// 사용: node simplan.mjs <engine.js> [판 수] [규칙 JSON] [난이도] [phys|mag|mix]
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const E = await import(pathToFileURL(resolve(process.argv[2] ?? 'public/defense/engine.js')).href);
const N = +(process.argv[3] ?? 20);
Object.assign(E.RULES, JSON.parse(process.argv[4] ?? '{}'));
const DIFF = process.argv[5] ?? 'master';
const STYLE = process.argv[6] ?? 'mix';
const NOTOP = process.env.NOTOP_BEFORE_CH6 === '1';   // 6장(초월 갈매미맨) 전에는 초불영제를 만들지 않음(버티기 확인용)
const DIFFX = JSON.parse(process.argv[7] ?? '{}'); for (const [k, v] of Object.entries(DIFFX)) Object.assign(E.DIFFS[k], v);   // 난이도 배수 시험용
const WISH = {
  // 에픽 → 전설·히든(스턴·이감·깎기 포함, 2026-11-10부터 u61~u80도 전설·히든) → 최상위(초불영제)
  mag: ['u29', 'u26', 'u32', 'u35', 'u40', 'u34', 'u38', 'u41', 'u43', 'u44', 'u42', 'u62', 'u63', 'u69', 'u73', 'u74', 'u80', 'u46', 'u59', 'u58', 'u57', 'u54', 'u60', 'u55', 'u53', 'u48', 'u49'],
  phys: ['u25', 'u31', 'u28', 'u27', 'u36', 'u33', 'u37', 'u39', 'u61', 'u66', 'u70', 'u71', 'u72', 'u78', 'u77', 'u45', 'u52', 'u50', 'u47', 'u56', 'u51'],
};
// BOTWISH=tops(2026-11-11 패배 원인 조사): 에픽 3개 뒤 곧바로 초불영제(그 재료 나무 전체)를 노림 — 예전 목록은 전설·히든 14~15종을 먼저 만들어 초불영제까지 못 감
if (process.env.BOTWISH === 'tops') for (const style of ['mag', 'phys']) {
  const tops = E.UNITS.filter((u) => E.tierOf(u.id) >= 6 && u.role !== E.SPECIAL_ROLE && (style === 'mag') === u.role.endsWith('술사')).map((u) => u.id);
  WISH[style] = [...WISH[style].slice(0, 3), ...tops];
}
const SPOTS = [[110, 200], [430, 200], [430, 565], [110, 565], [270, 200], [270, 565], [110, 382], [430, 382]];
const recipeOf = new Map(E.RECIPES.map((r) => [r.out, r]));
const ownedMap = (g) => { const m = {}; for (const u of g.units) if (!u.away) m[u.id] = (m[u.id] ?? 0) + 1; return m; };
// 목표 하나를 만들기 위해 더 만들어야 할 중간 재료(allowed)와 모자란 바닥 재료(leaves)
function expand(goal, have, allowed, leaves, top = true) {
  if (!top && (have[goal] ?? 0) > 0) { have[goal]--; return; }
  const r = recipeOf.get(goal);
  if (!r) { leaves[goal] = (leaves[goal] ?? 0) + 1; return; }
  allowed.add(goal);
  for (const i of r.in) expand(i, have, allowed, leaves, false);
}
function bot(g, st) {
  if (g.choice) E.chooseReward(g, 0);   // 6장 보상: 초월 갈매미맨(초월 등급을 만들 수 있게)
  const have = ownedMap(g);
  // 지금 목표: 목록에서 아직 없는 것 2개
  const keyOk = (id) => !(recipeOf.get(id)?.in.includes(E.TRANSCEND_KEY)) || (have[E.TRANSCEND_KEY] ?? 0) > 0;   // 초월은 초월 갈매미맨이 있어야
  const topOk = (id) => !(NOTOP && E.tierOf(id) >= 6 && g.missionDone < 6);
  const goals = st.wish.filter((id) => !(have[id] > 0) && keyOk(id) && topOk(id)).slice(0, 2);
  const allowed = new Set(), leaves = {}, h2 = { ...have };
  for (const gl of goals) expand(gl, h2, allowed, leaves);
  // 선택 위습: 목표에 모자란 바닥 재료로. 언커먼·레어 선택 위습은 목표 나무에서 아직 없는 그 등급 중간 재료로(2026-11-11: 예전엔 그 등급 첫 유닛을 뽑아 버림)
  for (const gr of E.PICK_GRADES) while (g.picks[gr] > 0) {
    const mid = [...allowed].find((x) => E.UNIT[x].grade === gr && !(have[x] > 0) && E.unitsOfGrade(gr).includes(x));
    const id = mid ?? Object.keys(leaves).find((x) => E.UNIT[x].grade === gr && leaves[x] > 0) ?? E.unitsOfGrade(gr)[0];
    if (mid) { allowed.delete(mid); have[mid] = 1; }
    const r = E.summonPick(g, id); if (!r.ok) break; if (leaves[id]) leaves[id]--;
    const s = SPOTS[st.n++ % SPOTS.length]; E.moveUnit(g, r.unit.uid, s[0], s[1], true);
  }
  for (const gr of E.WISP_GRADES) while (E.summon(g, gr).ok) { const u = g.units[g.units.length - 1]; const s = SPOTS[st.n++ % SPOTS.length]; E.moveUnit(g, u.uid, s[0], s[1], true); }
  // 조합: 목표 재료 나무에 있는 것만(낮은 등급부터)
  for (let k = 0; k < 30; k++) {
    const cand = [...allowed].map((id) => recipeOf.get(id)).filter((r) => r && E.recipeUnits(g, r)).sort((a, b) => E.tierOf(a.out) - E.tierOf(b.out));
    if (!cand.length) break;
    const nu = E.combine(g, cand[0]); if (!nu) break;
    const sp = SPOTS[st.n++ % SPOTS.length]; E.moveUnit(g, nu.uid, sp[0], sp[1], true);
    allowed.delete(cand[0].out);
    if (goals.includes(cand[0].out)) break;   // 목표 완성 → 다음 판단에서 새 목표
  }
  // 판이 붐비면 목표에 안 쓰이는 낮은 등급을 팔아 새우·모집권
  const field = E.fieldUnits(g);
  if (field.length > 55) {
    const need = new Set(Object.keys(leaves));
    const sellable = field.filter((u) => E.tierOf(u.id) <= 2 && !need.has(u.id) && !allowed.has(u.id)).sort((a, b) => E.tierOf(a.id) - E.tierOf(b.id));
    for (const u of sellable.slice(0, field.length - 50)) E.sell(g, u.uid);
  }
  while ((g.tickets ?? 0) >= E.RULES.recruitCost) { const r = E.recruit(g); if (!r.ok) break; }
  // 강화: 판 공격력이 가장 큰 칸부터(값 대비)
  for (let i = 0; i < 12; i++) {
    const d = Array(E.UPG_COUNT).fill(0); for (const u of E.fieldUnits(g)) { const k = E.kit(u.id); d[E.upgGroup(k.tier)] += k.dmg / k.gap; }
    let bi = -1, bv = 0; for (let gi = 0; gi < E.UPG_COUNT; gi++) { if (E.upgMaxed(g, gi)) continue; const v = d[gi] / E.upgradeCost(g, gi); if (v > bv) { bv = v; bi = gi; } }
    if (bi < 0 || !E.upgrade(g, bi).ok) break;
  }
  // 외부탐사(목표 재료는 안 보냄). 2026-11-10부터 보낸 갈매미가 장을 깨도 남으므로, 남은 대장 체력을 150초 안에 못 잡을 만큼이면 더 보냄
  if (E.missionOpen(g) && g.enemies.length < 25 && (st.nextSend ?? 0) <= g.t) {
    const t = E.missionTarget(g);
    const edps = (u) => { const k = E.kit(u.id); return (k.exDmg || k.dmg) * (k.explore || 1) / k.gap * 100 / (100 + (k.type === 'phys' ? t.armor : t.mr)); };
    const have = E.awayUnits(g).reduce((a, u) => a + edps(u), 0), needDps = t.hp / 150 - have;
    if (needDps > 0) {
      const list = E.fieldUnits(g).filter((u) => !allowed.has(u.id) && !(leaves[u.id] > 0)).map((u) => [u, edps(u)]).sort((a, b) => b[1] - a[1]);
      const total = E.fieldUnits(g).reduce((a, u) => a + edps(u), 0);
      let sum = 0; const send = [];
      for (const [u, dd] of list) { if (sum >= needDps) break; send.push(u.uid); sum += dd; }
      if (sum >= needDps && sum <= total * 0.6) E.sendAway(g, send);
      else if (sum > 0 && sum <= total * 0.4 && have > 0) E.sendAway(g, send);   // 다 못 채워도 조금씩 보탬
    }
    st.nextSend = g.t + 10;
  }
  if (E.awayUnits(g).length && g.enemies.length > 50) E.recall(g);
}
const res = [], reasons = {}, ch6s = [], ends = []; let tops = 0;
for (let s = 1; s <= N; s++) {
  const g = E.newGame(s, 'normal', DIFF); let ch6 = 0, end = 0; const style = STYLE === 'mix' ? (s % 2 ? 'mag' : 'phys') : STYLE;
  const st = { n: 0, wish: WISH[style].filter((id) => E.UNIT[id]) }; let acc = 0;
  while (!g.over && g.t < 4000) { E.step(g, 0.05); acc += 0.05; if (acc >= 0.5) { acc = 0; bot(g, st); } if (!ch6 && g.missionDone >= 6) ch6 = g.wave; if (!end && g.missionDone >= E.REGIONS.length) end = g.wave; }
  ch6s.push(ch6); ends.push(end);
  res.push(g.over === 'win' ? 61 : g.wave);
  const key = g.over + ':' + g.reason.slice(0, 10); reasons[key] = (reasons[key] ?? 0) + 1;
  if (process.env.DIAG === '1' && g.over === 'lose') {   // 진 순간의 판 상태(2026-11-11 패배 원인 조사)
    const f = E.fieldUnits(g), by = {}; for (const u of f) { const t = E.UNIT[u.id].role === E.SPECIAL_ROLE ? 'key' : E.tierOf(u.id); by[t] = (by[t] ?? 0) + 1; }
    let sh = 0, msh = 0, dps = 0, slow = 0; for (const u of f) { const k = E.kit(u.id); sh += k.shred ?? 0; msh += k.mshred ?? 0; slow += k.slow ?? 0; dps += k.dmg / k.gap * E.upgMul(g, k.tier); }
    const boss = g.enemies.find((e) => e.boss);
    console.log('DIAG ' + JSON.stringify({ seed: s, style, wave: g.wave, why: g.reason.slice(0, 14), bossLeft: boss ? +(boss.hp / boss.max).toFixed(2) : null, field: by, away: E.awayUnits(g).length, stored: E.storedUnits(g).length,
      wisps: g.wisps, picks: g.picks, gold: g.gold, tickets: g.tickets, upg: g.upg, shred: Math.round(sh), mshred: Math.round(msh), armor: E.armorAt(g, g.wave), mr: E.mrAt(g, g.wave), slow: +slow.toFixed(2), dps: Math.round(dps), story: g.missionDone, enemies: g.enemies.length, warn: g.warn, goals: st.wish.filter((id) => !f.some((u) => u.id === id)).slice(0, 2) }));
  }
  tops += g.units.filter((u) => E.tierOf(u.id) >= 6 && E.UNIT[u.id].role !== E.SPECIAL_ROLE).length;   // 2026-11-11: 초월 갈매미맨(재료)은 빼고 셈(예전엔 섞여 있었음)
}
const hist = {}; res.forEach((w) => (hist[w] = (hist[w] ?? 0) + 1));
const wins = res.filter((w) => w === 61).length;
const c6 = ch6s.filter(Boolean), ce = ends.filter(Boolean); console.log(`${DIFF} ${STYLE}${NOTOP ? ' [6장 전 초불영제 없음]' : ''}: 6장 깬 웨이브 ${c6.length ? Math.round(c6.reduce((a, b) => a + b, 0) / c6.length) : '-'}(${c6.length}/${N}판) | 마지막 ${E.REGIONS.length}장 깬 웨이브 ${ce.length ? Math.round(ce.reduce((a, b) => a + b, 0) / ce.length) : '-'}(${ce.length}/${N}판, 늦게 ${ce.length ? Math.max(...ce) : '-'}) | 깬 판 ${wins}/${N} | 끝난 웨이브 ${JSON.stringify(hist)} | 최상위 평균 ${(tops / N).toFixed(1)} | ${JSON.stringify(reasons)}`);
