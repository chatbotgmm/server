// 전투·외근 밸런스 계산: npx tsx scripts/battle-balance.ts
// 1) AI끼리 무작위 3:3 대결을 많이 돌려 속성·역할별 승률을 봄  2) 외근 스카우트 기대값 표
import { loadContent } from '../src/content.js';
import { loadBattleRules } from '../src/battle/load.js';
import { evoChoices, learnChances, learnOffers, learnSkill, maxStageAt, slotSkills } from '../src/battle/growth.js';
import { createBattle, act, pickAi, aiChoose } from '../src/battle/engine.js';
import { scoutChance, expectedAttempts } from '../src/battle/scout.js';
const R = loadBattleRules();
const unitDefs = [...R.units.values()], scoutRules = R.data.scout;
const attrName = (a) => R.attrName(a), roleName = (r) => R.data.roles[r].name;
const content = loadContent();
const name = (id) => content.map.get(id).name;
const tierOf = (id) => content.map.get(id).rarity;
let seed = 12345;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function rec(id, level, i) {
    const r = { uid: `u${i}`, id, name: name(id), tier: tierOf(id), level, xp: 0, stage: maxStageAt(R, level), evoAugs: [] };
    for (let s = 0; s < r.stage; s++) {
        const c = evoChoices(R, r, rnd);
        if (c[0])
            r.evoAugs.push(c[Math.floor(rnd() * c.length)].id);
    }
    // 레벨업 기술: 기회마다 후보 중 무작위 1개(칸이 차면 오래된 것과 바꿈)
    while (learnChances(R, r) > 0) {
        const o = learnOffers(R, r);
        learnSkill(R, r, o.length ? o[Math.floor(rnd() * o.length)].id : null, slotSkills(R, r)[0]?.id);
    }
    return r;
}
function run(b) {
    for (let guard = 0; guard < 400 && b.phase !== 'over'; guard++) {
        if (b.phase === 'choose') {
            aiChoose(b, 0);
            aiChoose(b, 1);
            continue;
        }
        act(b, [pickAi(b, 0), pickAi(b, 1)]);
    }
    return b;
}
const N = Number(process.argv[2] ?? 3000), LEVEL = Number(process.argv[3] ?? 30);
const wins = new Map();
let turns = 0, draws = 0, timeouts = 0;
for (let n = 0; n < N; n++) {
    const pick = () => { const ids = new Set(); while (ids.size < 3)
        ids.add(unitDefs[Math.floor(rnd() * unitDefs.length)].id); return [...ids]; };
    const A = pick(), B = pick();
    const b = run(createBattle(R, A.map((id, i) => rec(id, LEVEL, i)), B.map((id, i) => rec(id, LEVEL, i + 3)), Math.floor(rnd() * 1e9)));
    turns += b.turn;
    if (b.phase !== 'over')
        timeouts++;
    else if (b.winner === null)
        draws++;
    for (const [team, side] of [[A, 0], [B, 1]])
        for (const id of team) {
            const w = wins.get(id) ?? [0, 0];
            w[1]++;
            if (b.winner === side)
                w[0]++;
            wins.set(id, w);
        }
}
console.log(`\n## AI 대결 ${N}판 (레벨 ${LEVEL}, 무작위 3:3) — 평균 ${(turns / N).toFixed(1)}턴, 무승부 ${draws}, 시간 초과 ${timeouts}`);
const byKey = (k) => { const m = new Map(); for (const [id, [w, g]] of wins) {
    const key = k(id);
    const v = m.get(key) ?? [0, 0];
    v[0] += w;
    v[1] += g;
    m.set(key, v);
} return [...m].sort((a, b) => b[1][0] / b[1][1] - a[1][0] / a[1][1]); };
const pct = ([w, g]) => `${(w / g * 100).toFixed(1)}%`;
console.log('속성별 승률:', byKey(id => attrName(unitDefs.find(u => u.id === id).attr)).map(([k, v]) => `${k} ${pct(v)}`).join(', '));
console.log('역할별 승률:', byKey(id => roleName(unitDefs.find(u => u.id === id).role)).map(([k, v]) => `${k} ${pct(v)}`).join(', '));
const units = [...wins].sort((a, b) => b[1][0] / b[1][1] - a[1][0] / a[1][1]);
console.log('유닛 승률 상위 5:', units.slice(0, 5).map(([id, v]) => `${name(id)} ${pct(v)}`).join(', '));
console.log('유닛 승률 하위 5:', units.slice(-5).map(([id, v]) => `${name(id)} ${pct(v)}`).join(', '));
// 외근 기대값
const WIN = 0.6;
console.log(`\n## 외근 스카우트 기대값 (외근 대결 승률 ${WIN * 100}% 가정, 하루 무료 ${scoutRules.freeOutingsPerDay}번)`);
console.log('| 등급 | 만날 확률 | 진화 | 명함 | 성공률 | 천장 | 기대 시도 | 기대 외근 수 | 무료만 쓸 때 일수 |');
console.log('|---|---|---|---|---|---|---|---|---|');
for (const t of ['LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL'])
    for (const stage of [0, 2])
        for (const cards of [0, 3]) {
            const p = scoutChance(R, t, stage, cards, 0), e = expectedAttempts(p, scoutRules.pity[t]);
            const outings = e / (scoutRules.encounter[t] / 100 * WIN);
            console.log(`| ${t} | ${scoutRules.encounter[t]}% | ${stage} | ${cards} | ${p.toFixed(1)}% | ${scoutRules.pity[t]}번째 | ${e.toFixed(2)} | ${outings.toFixed(1)} | ${(outings / scoutRules.freeOutingsPerDay).toFixed(1)}일 |`);
        }
