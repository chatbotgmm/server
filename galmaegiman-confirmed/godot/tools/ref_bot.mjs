// 비교 시험용 기준 기록: 웹 판 엔진(engine.js)으로 아주 단순한 봇을 돌려 5초마다 판 상태를 JSON 줄로 쓴다.
// 고도 판(scripts/test/compare_test.gd)이 같은 봇을 같은 시드로 돌려 이 기록과 한 줄씩 비교한다.
// 사용: node godot/tools/ref_bot.mjs <시드> <난이도> <최대 초> <출력 파일>
import { writeFileSync } from 'node:fs';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const here = dirname(fileURLToPath(import.meta.url));
const E = await import(pathToFileURL(resolve(here, '../../public/defense/engine.js')).href);
const [seed, diff, maxT, out] = [+process.argv[2], process.argv[3] ?? 'master', +(process.argv[4] ?? 600), process.argv[5]];
Object.assign(E.DIFFS[diff], JSON.parse(process.argv[6] ?? '{}'));   // 시험용 난이도 바꾸기(예: {"line":0.1} — 봇이 60웨이브까지 가서 최상위·고유 기술·13장까지 거치게)
const bits = (x) => { const b = Buffer.alloc(8); b.writeDoubleLE(x); return b.toString('hex'); };   // 실수는 64비트 그대로 비교(고도 JSON 읽기가 마지막 자리를 틀릴 수 있어서)
function bot(E, g) {   // compare_test.gd의 bot()과 한 줄씩 같아야 함
  if (g.choice) E.chooseReward(g, 0);
  for (const gr of E.WISP_GRADES) while (g.wisps[gr] > 0) E.summon(g, gr);
  for (let n = 0; n < 60; n++) {
    let made = false;
    for (const r of E.RECIPES) if (E.recipeUnits(g, r)) { E.combine(g, r); made = true; break; }
    if (!made) break;
  }
  while (g.tickets >= E.RULES.recruitCost) E.recruit(g);
  for (let gi = 0; gi < 8; gi++) if (!E.upgMaxed(g, gi) && g.gold >= E.upgradeCost(g, gi)) E.upgrade(g, gi);
  // 나머지 행동도 거치게(2026-11-14): 선택 위습 · 리롤 · 판매 · 창고 넣기/빼기 · 옮기기 · 모으기 · 고정 · 일부 불러오기
  for (const gr of E.PICK_GRADES) while (g.picks[gr] > 0) E.summonPick(g, E.unitsOfGrade(gr)[g.wave % E.unitsOfGrade(gr).length]);
  if (g.rerolls > 0) { const u = g.units.find((x) => E.canReroll(g, x)); if (u) E.rerollUnit(g, u.uid); }
  const f = E.fieldUnits(g);
  if (f.length > 40) { const u = f.find((x) => E.tierOf(x.id) <= 1); if (u) E.sell(g, u.uid); }
  const step = Math.round(g.t * 2);
  if (step % 40 === 0) E.storeGroup(g, 'COMMON');
  if (step % 40 === 20) E.unstore(g, E.storedUnits(g).slice(0, 3).map((u) => u.uid));
  if (step % 30 === 5 && f.length) { const u = f[step % f.length]; E.moveUnit(g, u.uid, 100 + (step % 300), 200 + (step % 350), step % 2 === 0); }
  if (step % 50 === 7 && f.length) E.gather(g, f[0].uid);
  if (step % 70 === 9 && f.length > 1) E.setLock(g, [f[1].uid], step % 140 === 9);
  if (step % 90 === 11 && E.awayUnits(g).length) E.recall(g, [E.awayUnits(g)[0].uid]);
  if (process.env.BOT_TOPS === '1' && g.wave >= 3 && step % 20 === 0 && E.topCount(g) < 12) {   // 시험용: 고유 기술·위습 찾기 갈매미를 차례로 하나씩 불러 모든 기술을 거치게
    const ids = Object.keys(E.SIGNATURE), id = ids[(step / 20) % ids.length], gr = E.UNIT[id].grade;
    g.picks[gr] = (g.picks[gr] ?? 0) + 1; E.summonPick(g, id);
  }
  if (E.missionOpen(g) && E.awayUnits(g).length < 2) {
    let best = null;
    for (const u of E.fieldUnits(g)) { const t = E.tierOf(u.id); if (t >= 3 && E.UNIT[u.id].role !== E.SPECIAL_ROLE && (!best || t > E.tierOf(best.id))) best = u; }
    if (best) E.sendAway(g, [best.uid]);
  }
}
function snap(E, g) {
  let ehp = 0; for (const e of g.enemies) ehp += e.hp;
  return { t: Math.round(g.t * 100) / 100, wave: g.wave, gold: bits(g.gold), kills: g.kills, warn: g.warn, en: g.enemies.length, ehp: bits(ehp), units: g.units.map((u) => u.id).join(','),
    md: g.missionDone, mhp: bits(g.mission ? g.mission.hp : -1), rs: g.rand.s, tickets: g.tickets, upg: g.upg.join(','), wisps: Object.values(g.wisps).join(','), picks: Object.values(g.picks).join(','), over: g.over ?? '' };
}
const g = E.newGame(seed, 'normal', diff), lines = [];
const cov = { sig: new Set(), tops: new Set(), zones: 0, wispFind: 0, maxUnits: 0 };   // 이 판이 거친 규칙(시험 범위 확인용)
for (let i = 1; !g.over && g.t < maxT; i++) {
  E.step(g, 0.05);
  for (const f of g.fx) { if (f.kind === 'sig') cov.sig.add(f.sk); if (f.kind === 'wisp') cov.wispFind++; }
  for (const u of g.units) if (E.tierOf(u.id) >= E.TOP_TIER) cov.tops.add(u.id);
  cov.zones = Math.max(cov.zones, g.zones.length); cov.maxUnits = Math.max(cov.maxUnits, g.units.length);
  if (i % 10 === 0) bot(E, g);
  if (i % 100 === 0) lines.push(JSON.stringify(snap(E, g)));
}
lines.push(JSON.stringify(snap(E, g)));
writeFileSync(out, lines.join('\n') + '\n');
console.log(`기준 기록 ${lines.length}줄 · 끝 ${g.over ?? '진행 중'} 웨이브 ${g.wave} · 탐사 ${g.missionDone}장 · 최상위 ${cov.tops.size}종 · 고유 기술 ${[...cov.sig].join('/') || '없음'} · 위습 찾기 ${cov.wispFind > 0 ? '있음' : '없음'} · ${g.reason}`);
