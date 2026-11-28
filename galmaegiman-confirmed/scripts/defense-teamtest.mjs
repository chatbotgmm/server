// 정해진 팀 시험(2026-11-16 사용자 기준: 전설 환산 9·10·11마리, 조합이 맞으면 깨고 안 맞으면 못 깸).
// 41웨이브 시작에 팀을 세우고(강화 모두 10단계, 스토리는 다 깬 것으로, 증강체 없음) 60웨이브까지 돌림.
// 사용: node scripts/defense-teamtest.mjs <engine.js> <난이도> <판 수> [규칙 JSON]  · 환경 TEAM=good|noslow|nostun|nocut  EQ=9|10|11
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
const E = await import(pathToFileURL(resolve(process.argv[2])).href);
const DIFF = process.argv[3] ?? 'artisan', N = +(process.argv[4] ?? 20);
Object.assign(E.RULES, JSON.parse(process.argv[5] ?? '{}'));
const TEAM = process.env.TEAM ?? 'good', EQ = +(process.env.EQ ?? 11);
const small = Math.round(80 * (E.RULES.mshredAll ?? 0.35)) + 1;
const fam = (id, type) => { const k = E.kit(id); return { slow: !!k.slow, stun: !!(k.stun || k.astun), cut: type === 'phys' ? !!k.shred : ((k.mshred ?? 0) > small || !!k.amp) }; };
const POOL = E.UNITS.filter((u) => E.tierOf(u.id) >= 4 && u.role !== E.SPECIAL_ROLE).map((u) => u.id);
const eqOf = (id) => (E.tierOf(id) >= 6 ? 2.5 : 1);
const dps = (id) => { const k = E.kit(id); return k.dmg / k.gap; };
function makeTeam(rng, type) {
  const ban = TEAM === 'noslow' ? 'slow' : TEAM === 'nostun' ? 'stun' : TEAM === 'nocut' ? 'cut' : null;
  const ok = (id) => !ban || !fam(id, type)[ban];
  const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const tops = shuffle(POOL.filter((id) => E.tierOf(id) >= 6 && E.kit(id).type === type && ok(id)));
  const legs = shuffle(POOL.filter((id) => E.tierOf(id) < 6 && ok(id)));
  const team = []; let eq = 0;
  for (const id of tops.slice(0, 2)) if (eq + 2.5 <= EQ) { team.push(id); eq += 2.5; }
  // 전설: 조화 팀은 주력 쪽 깎기 ≥ 55웨이브 방어×1.2 · 이감 합 ≥ 0.8 · 스턴 유닛 ≥ 2를 먼저 채우고, 나머지는 주력 딜러
  // 갈래를 뺀 팀(noslow 등)은 그 갈래 유틸이 있는 유닛을 아예 안 쓰고 나머지 조건은 같게 채움
  const g0 = E.newGame(1, 'normal', DIFF), armor = type === 'phys' ? E.armorAt(g0, 55) : E.mrAt(g0, 55);
  const val = (id) => { const k = E.kit(id); return { cut: (type === 'phys' ? k.shred : k.mshred) ?? 0, slow: k.slow ?? 0, stun: k.stun || k.astun ? 1 : 0 }; };
  const sum = (f) => team.reduce((a, id) => a + val(id)[f], 0);
  const goal = { cut: armor * 1.2, slow: 0.8, stun: 2 };
  while (eq + 1 <= EQ) {
    const cand = legs.filter((id) => !team.includes(id));
    const miss = ['cut', 'slow', 'stun'].filter((f) => f !== ban && sum(f) < goal[f]);
    let pick = miss.length ? cand.filter((id) => val(id)[miss[0]] > 0).sort((a, b) => val(b)[miss[0]] - val(a)[miss[0]])[Math.floor(rng() * 2)] : null;
    if (!pick) pick = cand.filter((id) => E.kit(id).type === type).sort((a, b) => dps(b) - dps(a))[Math.floor(rng() * 3)] ?? cand[0];
    if (!pick) break; team.push(pick); eq += 1;
  }
  // 하위 유닛 채우기(실제 판처럼): FILL="커먼,언커먼,레어,에픽" 수(기본 25,5,4,10: 달인 봇 판 55웨이브 평균). 뺀 갈래 유틸이 있는 에픽은 빼기
  const fill = (process.env.FILL ?? '25,5,4,10').split(',').map(Number), low = [];
  fill.forEach((n, t) => { const c = shuffle(E.UNITS.filter((u) => E.tierOf(u.id) === t && u.role !== E.SPECIAL_ROLE && (t < 3 || ok(u.id))).map((u) => u.id)); for (let i = 0; i < n && c.length; i++) low.push(c[i % c.length]); });
  return { team, eq, low };
}
const SPOTS = [[110, 200], [430, 200], [430, 565], [110, 565], [270, 200], [270, 565], [110, 382], [430, 382], [190, 290], [350, 290], [190, 475], [350, 475], [270, 382]];
const res = [];
for (let s = 1; s <= N; s++) {
  const type = s % 2 ? 'mag' : 'phys';
  let x = s * 9301 + 49297; const rng = () => ((x = (x * 9301 + 49297) % 233280) / 233280);
  const { team, eq, low } = makeTeam(rng, type);
  const g = E.newGame(s, 'normal', DIFF);
  g.missionDone = E.REGIONS.length; g.upg = g.upg.map(() => E.RULES.upgMax);
  g.wave = 40; g.bossDone = 40; g.waveT = 1e6; g.spawnLeft = 0; g.enemies = []; g.units = [];
  g.units = []; g.wisps.COMMON = 999;   // 소환으로 유닛 틀을 만듦(위습이 모자라면 소환이 실패해 같은 유닛을 덮어쓰던 것 고침)
  const all = [...team, ...low];
  all.forEach((id, i) => { E.summon(g, 'COMMON'); const u = g.units[g.units.length - 1]; u.id = id; const p = i < SPOTS.length ? SPOTS[i] : [110 + ((i * 53) % 320), 200 + ((i * 97) % 365)]; u.x = u.tx = p[0]; u.y = u.ty = p[1]; u.lock = true; });
  g.units = g.units.slice(-all.length); if (g.units.length !== all.length || g.units.some((u, i) => u.id !== all[i])) throw new Error('유닛 세우기 실패');
  g.augs = (process.env.AUGS ?? 'type,fury').split(',').filter(Boolean).map((a) => (a === 'type' ? (type === 'phys' ? 'phys' : 'mag') : a));   // 증강체 2개(봇에서 흔한 것)
  g.wisps = Object.fromEntries(Object.keys(g.wisps).map((k) => [k, 0]));
  const h = { cut: 0, slow: 0, stun: 0 }; for (const id of team) { const k = E.kit(id); h.cut += (type === 'phys' ? k.shred : k.mshred) ?? 0; h.slow += k.slow ?? 0; if (k.stun || k.astun) h.stun++; }
  while (!g.over && g.t < 4000) { E.step(g, 0.05); if (g.choice) E.chooseReward(g, 0); if (g.augOffer) g.augOffer = null; }
  res.push({ s, type, eq, team: team.map((id) => E.UNIT[id].name), win: g.over === 'win', left: g.leftover, wave: g.wave, why: (g.reason ?? '').slice(0, 16), cut: Math.round(h.cut), armor: type === 'phys' ? E.armorAt(g, 55) : E.mrAt(g, 55), slow: +h.slow.toFixed(2), stun: h.stun });
}
const wins = res.filter((r) => r.win);
console.log(`${DIFF} TEAM=${TEAM} EQ=${EQ}: 깬 판 ${wins.length}/${N} · 남은 적 [${wins.map((r) => r.left).sort((a, b) => a - b).join(',')}] · 진 웨이브 [${res.filter((r) => !r.win).map((r) => r.wave).sort((a, b) => a - b).join(',')}]`);
if (process.env.V === '1') for (const r of res) console.log(`  ${r.type} 환산${r.eq} 깎기${r.cut}/방어${r.armor} 이감${r.slow} 스턴${r.stun} → ${r.win ? '깸 남은 ' + r.left : '짐 ' + r.wave + ' ' + r.why} | ${r.team.join(',')}`);
