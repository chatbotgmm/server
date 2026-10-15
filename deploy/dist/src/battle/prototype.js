export const battleTiers = ['COMMON', 'UNCOMMON', 'SPECIAL', 'RARE', 'LEGEND'];
// 흔함 출발 원형 → 출신 동네. 기본(C1)과 거인·소인은 거의 모든 조합에 섞여 들어가서 비중을 낮춤
const origin = { C1: ['BASIC', .3], C2: ['GOLD', 1], C3: ['DARK', 1], C4: ['SEA', 1], C5: ['BLOSSOM', 1], C6: ['COSMOS', 1], C7: ['BASIC', .3], C8: ['BASIC', .3], C9: ['OUTFIT', 1] };
// 계산 결과가 이름과 안 맞는 유닛은 여기서 직접 고칩니다
const overrides = { L19: ['OUTFIT', 'SEA'] };
const tierMult = { COMMON: 1, UNCOMMON: 1.15, SPECIAL: 1.32, RARE: 1.52, LEGEND: 1.75 };
// 동네별 기본 능력치(체력·공격·방어·속도)
const baseStats = {
    BASIC: [110, 20, 14, 10], GOLD: [100, 18, 19, 8], DARK: [86, 22, 9, 16], SEA: [118, 19, 12, 9], BLOSSOM: [96, 17, 12, 12], COSMOS: [86, 25, 9, 12], OUTFIT: [104, 18, 15, 11],
};
// 같은 유닛은 언제나 같은 값이 나오도록 ID로 만든 작은 흔들림(±8%)
function jitter(id, salt) {
    let h = 2166136261 ^ salt;
    for (const ch of id)
        h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
    return 0.92 + ((h >>> 0) % 1000) / 1000 * 0.16;
}
export function battleRoster(content) {
    const recipeOf = new Map(content.recipes.map(r => [r.resultId, r.materials]));
    const memo = new Map();
    const tally = (id) => {
        const hit = memo.get(id);
        if (hit)
            return hit;
        const t = {};
        if (origin[id])
            t[origin[id][0]] = origin[id][1];
        else
            for (const m of recipeOf.get(id) ?? [])
                for (const [k, v] of Object.entries(tally(m)))
                    t[k] = (t[k] ?? 0) + v;
        memo.set(id, t);
        return t;
    };
    return content.characters.filter(c => battleTiers.includes(c.rarity)).map(c => {
        const ranked = Object.entries(tally(c.id)).sort((a, b) => b[1] - a[1]);
        const total = ranked.reduce((s, [, v]) => s + v, 0);
        const types = overrides[c.id] ?? (ranked.length ? [ranked[0][0], ...(ranked[1] && ranked[1][1] >= total * 0.3 ? [ranked[1][0]] : [])] : ['BASIC']);
        const [hp, atk, def, spd] = baseStats[types[0]];
        const m = tierMult[c.rarity];
        return { id: c.id, name: c.name, tier: c.rarity, types,
            hp: Math.round(hp * m * jitter(c.id, 1)), atk: Math.round(atk * m * jitter(c.id, 2)), def: Math.round(def * m * jitter(c.id, 3)), spd: Math.round(spd * jitter(c.id, 4) * (1 + (m - 1) * 0.3)) };
    });
}
