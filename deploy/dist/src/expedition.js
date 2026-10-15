// 파견대(최상위 유닛 ID, 중복 허용)로 시너지와 보상 계획을 계산합니다. 확률 판정은 roll()에서 합니다.
export function planExpedition(content, unitIds) {
    const e = content.economy.expedition;
    if (!unitIds.length || unitIds.length > e.maxParty)
        throw new Error(`탐험은 1~${e.maxParty}마리까지 보낼 수 있습니다.`);
    const groups = new Map();
    const same = new Map();
    for (const id of unitIds) {
        const c = content.map.get(id);
        if (!c?.synergy)
            throw new Error('최상위 유닛만 탐험을 보낼 수 있습니다.');
        groups.set(c.synergy, (groups.get(c.synergy) ?? 0) + 1);
        same.set(id, (same.get(id) ?? 0) + 1);
    }
    const plan = { size: unitIds.length, minutes: e.minutes, baseSnacks: unitIds.length * e.snackPerUnit, snackPercent: 0, flatPerUnit: 0,
        doublePercent: 0, commonPercent: 0, uncommonPercent: 0, ticketPercent: 0, synergies: [] };
    for (const [key, count] of groups) {
        const level = count >= e.levels[1] ? 2 : count >= e.levels[0] ? 1 : 0;
        if (!level)
            continue;
        const i = level - 1, fx = e.effects;
        plan.synergies.push({ key, label: fx[key].label, count, level });
        if (key === 'BASIC')
            plan.snackPercent += fx.BASIC.snackPercent[i];
        if (key === 'GOLD')
            plan.doublePercent += fx.GOLD.doublePercent[i];
        if (key === 'DARK')
            plan.minutes = Math.min(plan.minutes, fx.DARK.minutes[i]);
        if (key === 'SEA')
            plan.commonPercent += fx.SEA.commonPercent[i];
        if (key === 'BLOSSOM')
            plan.uncommonPercent += fx.BLOSSOM.uncommonPercent[i];
        if (key === 'COSMOS')
            plan.ticketPercent += fx.COSMOS.ticketPercent[i];
        if (key === 'OUTFIT')
            plan.flatPerUnit += fx.OUTFIT.snackPerUnit[i];
    }
    // 갈매미 카운트: 가장 많이 겹친 같은 유닛 하나만 적용합니다.
    const [unitId, count] = [...same].sort((a, b) => b[1] - a[1])[0];
    const steps = Object.entries(e.countBonusPercent).map(([n, p]) => [Number(n), p]).sort((a, b) => a[0] - b[0]);
    const step = steps.filter(([n]) => count >= n).at(-1);
    if (step) {
        plan.countBonus = { unitId, count, percent: step[1] };
        plan.snackPercent += step[1];
    }
    plan.synergies.sort((a, b) => b.count - a.count);
    return plan;
}
export function expectedSnacks(plan) {
    return Math.round(plan.baseSnacks * (1 + plan.snackPercent / 100)) + plan.flatPerUnit * plan.size;
}
export function rollExpedition(content, plan, roll) {
    const pick = (tier) => { const pool = content.characters.filter(c => c.rarity === tier); return pool[roll(pool.length)].id; };
    const chance = (percent) => percent > 0 && roll(100) < percent;
    let snacks = expectedSnacks(plan);
    const doubled = chance(plan.doublePercent);
    if (doubled)
        snacks *= 2;
    return { snacks, doubled,
        ...(chance(plan.commonPercent) ? { commonId: pick('COMMON') } : {}),
        ...(chance(plan.uncommonPercent) ? { uncommonId: pick('UNCOMMON') } : {}),
        tickets: chance(plan.ticketPercent) ? 1 : 0 };
}
