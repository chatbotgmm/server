// 기준 레벨: 내 전투 유닛 중 레벨 높은 3마리의 평균(내림)
export function referenceLevel(team) {
    const top = team.map(u => u.level).sort((a, b) => b - a).slice(0, 3);
    return top.length ? Math.floor(top.reduce((s, v) => s + v, 0) / top.length) : 1;
}
export function levelRange(r, ref) { const b = r.data.scout.levelBand, max = r.data.growth.maxLevel; return [Math.max(1, ref - b), Math.min(max, ref + b)]; }
// 성공률(%) = 기본 × 진화 보정 × (1 + cardBonus × 명함), 최대 cap. 천장에 닿으면 100
export function scoutChance(r, tier, stage, cards, failsSoFar) {
    const s = r.data.scout;
    if (failsSoFar + 1 >= s.pity[tier])
        return 100;
    const k = Math.max(0, Math.min(s.maxCards, Math.floor(cards)));
    return Math.min(s.cap, s.base[tier] * s.stageFactor[stage] * (1 + s.cardBonus * k));
}
// 성공까지 기대 시도 수(천장 포함): Σ_{i=0}^{N-1} (1-p)^i
export function expectedAttempts(p, pityAt) {
    const q = 1 - p / 100;
    let e = 0;
    for (let i = 0; i < pityAt; i++)
        e += q ** i;
    return e;
}
// 스카우트 성공: 원본 상태를 그대로 복사(새 고유번호만 붙임). 원래 주인의 유닛은 그대로 남음
export function scoutCopy(src, newUid) {
    return { uid: newUid, id: src.id, name: src.name, tier: src.tier, level: src.level, xp: src.xp, stage: src.stage, evoAugs: [...src.evoAugs] };
}
