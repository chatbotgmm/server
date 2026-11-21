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
// BOTWISH=mixed(2026-11-15 사용자 요청): 에픽 3개 → 최상위 2종 → 멈추지 않고 나머지 전설·히든 목록을 계속 만듦
if (process.env.BOTWISH === 'mixed') for (const style of ['mag', 'phys']) {
  const tops = E.UNITS.filter((u) => E.tierOf(u.id) >= 6 && u.role !== E.SPECIAL_ROLE && (style === 'mag') === u.role.endsWith('술사')).map((u) => u.id);
  WISH[style] = [...WISH[style].slice(0, 3), ...tops.slice(0, 2), ...WISH[style].slice(3)];
}
// BOTWISH=poison(2026-11-16 독 조합 확인): 에픽 3개 → 독 최상위 → 독 전설·히든 → 나머지
if (process.env.BOTWISH === 'poison') for (const style of ['mag', 'phys']) {
  const pz = (t0, t1) => E.UNITS.filter((u) => u.role !== E.SPECIAL_ROLE && E.tierOf(u.id) >= t0 && E.tierOf(u.id) <= t1 && E.kit(u.id).poison).map((u) => u.id);
  const base = WISH[style].filter((id) => E.tierOf(id) < 6);
  WISH[style] = [...base.slice(0, 3), ...pz(6, 9).slice(0, 2), ...pz(4, 5), ...base.slice(3)];
}
// 전설 환산(2026-11-15 사용자 기준 「전설 10마리 정도면 클리어」): 전설·히든 1, 최상위 2.5(재료 전설 2~3마리)
const legEq = (g) => E.fieldUnits(g).concat(E.awayUnits(g)).reduce((a, u) => { const t = E.tierOf(u.id); return a + (E.UNIT[u.id].role === E.SPECIAL_ROLE ? 0 : t >= 6 ? 2.5 : t >= 4 ? 1 : 0); }, 0);
const eqLog = [], compLog = [], synLog = [], synEl = {};
// 조합 조화도(2026-11-16 사용자 기준: 이감·스턴·깎기 조건이 맞는 팀인지). 주력 공격 쪽(초당 피해가 큰 쪽) 깎기 합 ÷ 그 웨이브 방어, 이감 합(1까지), 스턴 가진 유닛 수
const harmony = (g) => { const f = E.fieldUnits(g); let pd = 0, md = 0, sh = 0, msh = 0, sl = 0, st = 0;
  for (const u of f) { const k = E.kit(u.id); const d = k.dmg / k.gap * E.upgMul(g, k.tier); if (k.type === 'phys') pd += d; else md += d; sh += k.shred ?? 0; msh += k.mshred ?? 0; sl += k.slow ?? 0; if (k.stun || k.astun) st++; }
  const phys = pd >= md, cut = (phys ? sh : msh) / Math.max(1, phys ? E.armorAt(g, g.wave) : E.mrAt(g, g.wave));
  const tiers = {}; for (const u of f) { const t = E.tierOf(u.id); tiers[t] = (tiers[t] ?? 0) + 1; }
  return { cut: +cut.toFixed(2), slow: +Math.min(1, sl).toFixed(2), stun: st, tiers, augs: (g.augs ?? []).join('+') }; };
// BAN=slow|stun|cut(2026-11-16 조합 조화 확인): 그 갈래 유틸을 가진 전설 이상은 만들지 않음(조합이 안 맞는 팀)
const BAN = process.env.BAN;
const famOf = (id) => { const k = E.kit(id), small = Math.round(80 * (E.RULES.mshredAll ?? 0.35)) + 1;
  return { slow: !!k.slow, stun: !!(k.stun || k.astun), cut: k.type === 'phys' ? !!k.shred : ((k.mshred ?? 0) > small || !!k.amp) }; };
if (BAN) for (const style of ['mag', 'phys']) WISH[style] = WISH[style].filter((id) => !E.UNIT[id] || E.tierOf(id) < 4 || !famOf(id)[BAN]);
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
  if (g.choice) E.chooseReward(g, 0);
  if (g.augOffer && E.chooseAug && process.env.NOAUG !== '1') {   // 증강체(2026-11-15): 봇 방식에 맞는 것부터. NOAUG=1이면 고르지 않음(전·후 비교용)
    const pref = [st.style === 'phys' ? 'phys' : 'mag', 'fury', 'rift', 'shred', 'haste', 'boss', 'exec', 'storm', 'legend', 'chrono', 'wisp', 'upg', 'explore', 'slow', 'stun', 'gold', 'warn', 'supply', 'phys', 'mag'];
    const o = g.augOffer.opts; let best = 0, bi = 99; o.forEach((id, i) => { const r = pref.indexOf(id); if (r >= 0 && r < bi) { bi = r; best = i; } }); E.chooseAug(g, best);
  }   // 6장 보상: 초월 갈매미맨(초월 등급을 만들 수 있게)
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
  if (E.missionOpen(g) && (E.enemyCount ? E.enemyCount(g) : g.enemies.length) < 25 && (st.nextSend ?? 0) <= g.t) {
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
  if (E.awayUnits(g).length && (E.enemyCount ? E.enemyCount(g) : g.enemies.length) > 50) E.recall(g);
}
const res = [], reasons = {}, ch6s = [], ends = []; let tops = 0;
const SEED0 = +(process.env.SEED0 ?? 0);   // 여러 개를 나눠 돌릴 때 시드 시작점
for (let s = SEED0 + 1; s <= SEED0 + N; s++) {
  const g = E.newGame(s, 'normal', DIFF); if (process.env.BIAS) { g.bias = process.env.BIAS; if (E.topOpenFor) g.topOpen = E.topOpenFor(s, g.bias); } let ch6 = 0, end = 0; const style = STYLE === 'mix' ? (s % 2 ? 'mag' : 'phys') : STYLE;
  let wish = WISH[style];
  if (process.env.TOPPICK === 'random') {   // 판마다 최상위 2종을 무작위로(시드로 정함) → 유닛별 집계가 되게
    let x = s * 7919 + 13; const r = () => ((x = (x * 9301 + 49297) % 233280) / 233280);
    const tops = E.UNITS.filter((u) => E.tierOf(u.id) >= 6 && u.role !== E.SPECIAL_ROLE && (style === 'mag') === u.role.endsWith('술사')).map((u) => u.id);
    for (let i = tops.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [tops[i], tops[j]] = [tops[j], tops[i]]; }
    const base = WISH[style].filter((id) => E.tierOf(id) < 6);
    wish = [...base.slice(0, 3), ...tops.slice(0, 2), ...base.slice(3)];
  }
  if (process.env.SYNBOT === '1') {   // 2026-11-16: 판마다 속성 하나를 정해 그 속성 에픽·전설을 먼저 노림(시너지 확인용)
    const els = ['FIRE', 'WATER', 'ICE', 'LIGHTNING', 'NATURE', 'HOLY', 'DARK', 'NORMAL'], el = els[s % els.length];
    const mine = E.UNITS.filter((u) => u.role !== E.SPECIAL_ROLE && E.tierOf(u.id) >= 3 && E.tierOf(u.id) <= 5 && E.kit(u.id).elem === el).sort((a, b) => E.tierOf(a.id) - E.tierOf(b.id)).map((u) => u.id);
    wish = [...mine.slice(0, 5), ...wish.filter((id) => !mine.slice(0, 5).includes(id))];
  }
  const st = { n: 0, style, wish: wish.filter((id) => E.UNIT[id] && (!E.topIsOpen || E.topIsOpen(g, id))) };   // 2026-11-21: 이번 판에 닫힌 최상위는 노리지 않음 let acc = 0;
  let eq40 = null, eq55 = null, hm55 = null;
  while (!g.over && g.t < 4000) { E.step(g, 0.05); acc += 0.05; if (acc >= 0.5) { acc = 0; bot(g, st); } if (!ch6 && g.missionDone >= 6) ch6 = g.wave; if (!end && g.missionDone >= E.REGIONS.length) end = g.wave; if (eq40 == null && g.wave >= 40) eq40 = legEq(g); if (eq55 == null && g.wave >= 55) { eq55 = legEq(g); hm55 = harmony(g); } }
  compLog.push({ win: g.over === 'win', wave: g.over === 'win' ? 61 : g.wave, left: g.leftover ?? null, ids: [...new Set(E.fieldUnits(g).concat(E.awayUnits(g)).filter((u) => E.tierOf(u.id) >= 4 && E.UNIT[u.id].role !== E.SPECIAL_ROLE).map((u) => u.id))] });
  if (process.env.SYN === '1') { const sv = E.synergyOf(g); synLog.push(E.SYN_ELEMS.map((el) => sv[el].lv).reduce((a, b) => Math.max(a, b), 0) + ':' + E.SYN_ELEMS.filter((el) => sv[el].lv).length); const el = ['FIRE', 'WATER', 'ICE', 'LIGHTNING', 'NATURE', 'HOLY', 'DARK', 'NORMAL'][s % 8]; (synEl[el] ??= []).push([sv[el].lv, g.over === 'win' ? 61 : g.wave]); }
  eqLog.push({ eq40, eqEnd: legEq(g), eq55, hm55, win: g.over === 'win', wave: g.wave, left: g.leftover ?? null });
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

if (process.env.EQ === '1') {   // 전설 환산(55웨이브 시점, 전설·히든 1 · 최상위 2.5)별: 깬 판 · 남은 적(깬 판) · 진 웨이브
  const band = (v) => v == null ? '55 전 패배' : v < 8 ? '~7' : v < 9 ? '8' : v < 10 ? '9' : v < 11 ? '10' : v < 12 ? '11' : '12+';
  const b = {}; for (const r of eqLog) { const k = band(r.eq55); (b[k] ??= { n: 0, w: 0, left: [], lw: [] }); b[k].n++; if (r.win) { b[k].w++; b[k].left.push(r.left); } else b[k].lw.push(r.wave); }
  for (const [k, v] of Object.entries(b)) console.log(`환산 ${k}: 깬 판 ${v.w}/${v.n} · 남은 적 [${v.left.sort((x, y) => x - y).join(',')}] · 진 웨이브 [${v.lw.sort((x, y) => x - y).join(',')}]`);
  if (process.env.HM === '1') for (const r of eqLog.filter((r) => r.eq55 != null).sort((a, b) => a.eq55 - b.eq55)) console.log(`HM 환산 ${r.eq55} · 깎기 ${r.hm55.cut} · 이감 ${r.hm55.slow} · 스턴 ${r.hm55.stun} → ${r.win ? '깸 남은 ' + r.left : '짐 ' + r.wave} · 등급별 ${JSON.stringify(r.hm55.tiers)} · 증강 ${r.hm55.augs}`);
  console.log(`평균 환산 40웨이브 ${(eqLog.filter((r) => r.eq40 != null).reduce((a, r) => a + r.eq40, 0) / Math.max(1, eqLog.filter((r) => r.eq40 != null).length)).toFixed(1)} · 끝 ${(eqLog.reduce((a, r) => a + r.eqEnd, 0) / eqLog.length).toFixed(1)}`);
}

if (process.env.COMP === '1') {   // 2026-11-16 사용자 제안: 판 끝 조합 기록 → 유닛별 들어간 판·깬 비율·평균 끝 웨이브(최상위·전설). 진 판 조합도 출력
  const by = {}; for (const r of compLog) for (const id of r.ids) { const b = (by[id] ??= { n: 0, w: 0, wv: 0 }); b.n++; if (r.win) b.w++; b.wv += r.wave; }
  const all = compLog.length, wAll = compLog.filter((r) => r.win).length;
  console.log(`전체 깬 비율 ${(wAll / all * 100).toFixed(0)}% (${wAll}/${all})`);
  const rows = Object.entries(by).filter(([, b]) => b.n >= 3).map(([id, b]) => ({ id, name: E.UNIT[id].name, tier: E.tierOf(id), n: b.n, wr: b.w / b.n, wv: b.wv / b.n })).sort((a, b) => b.wr - a.wr || b.wv - a.wv);
  for (const r of rows) console.log(`COMP ${r.tier >= 6 ? '최상위' : '전설'} ${r.name}(${r.id}) 들어간 판 ${r.n} · 깬 비율 ${(r.wr * 100).toFixed(0)}% · 평균 끝 웨이브 ${r.wv.toFixed(1)}`);
  if (process.env.LOSECOMP === '1') for (const r of compLog.filter((r) => !r.win)) console.log(`LOSE ${r.wave} | ${r.ids.map((id) => E.UNIT[id].name).join(',')}`);
}

if (process.env.SYN === '1') { const c = {}; for (const k of synLog) c[k] = (c[k] ?? 0) + 1; console.log('시너지(최고 단계:켜진 속성 수) ' + JSON.stringify(c)); }
if (process.env.SYN === '1') for (const [el, a] of Object.entries(synEl)) console.log(`SYNEL ${el} 단계 [${a.map((x) => x[0]).join(',')}] 끝 웨이브 평균 ${(a.reduce((t, x) => t + x[1], 0) / a.length).toFixed(1)} 깸 ${a.filter((x) => x[1] === 61).length}/${a.length}`);
