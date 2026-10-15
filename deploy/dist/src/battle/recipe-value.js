/** 획득 경로와 관계없이 조합식 원재료를 흔함 마릿수로 환산해요. */
export function commonRecipeValues(content, unitIds) {
    const characters = new Map(content.characters.map(character => [character.id, character]));
    const recipes = new Map();
    for (const recipe of content.recipes) {
        const alternatives = recipes.get(recipe.resultId) ?? [];
        alternatives.push(recipe);
        recipes.set(recipe.resultId, alternatives);
    }
    const values = new Map();
    const path = [];
    const visiting = new Set();
    const valueOf = (id) => {
        const cached = values.get(id);
        if (cached !== undefined)
            return cached;
        const character = characters.get(id);
        if (!character)
            throw new Error(`흔함 환산: 알 수 없는 갈매미 ${[...path, id].join(' → ')}`);
        if (visiting.has(id))
            throw new Error(`흔함 환산: 조합식 순환 ${[...path, id].join(' → ')}`);
        if (character.rarity === 'COMMON') {
            values.set(id, 1);
            return 1;
        }
        const alternatives = recipes.get(id) ?? [];
        if (!alternatives.length)
            throw new Error(`흔함 환산: ${id} (${character.rarity})의 조합식이 없어 원재료 수를 계산할 수 없어요.`);
        if (alternatives.length !== 1)
            throw new Error(`흔함 환산: ${id}의 조합식이 여러 개여서 원재료 기준이 모호해요.`);
        const recipe = alternatives[0];
        if (!recipe.materials.length)
            throw new Error(`흔함 환산: ${id}의 조합 재료가 비어 있어요.`);
        visiting.add(id);
        path.push(id);
        let total = 0;
        try {
            // Recipe.materials는 이미 수량만큼 펼쳐진 배열이에요. 같은 재료도 매번 더해요.
            for (const material of recipe.materials) {
                total += valueOf(material);
                if (!Number.isSafeInteger(total) || total <= 0)
                    throw new Error(`흔함 환산: ${id}의 원재료 수가 안전한 정수 범위를 벗어났어요.`);
            }
        }
        finally {
            path.pop();
            visiting.delete(id);
        }
        values.set(id, total);
        return total;
    };
    // 요청한 종과 그 재료만 계산해 다른 도감 항목의 특수 획득 규칙과 섞지 않아요.
    return Object.fromEntries([...unitIds].map(id => [id, valueOf(id)]));
}
export function recipePowerBonus(value, policy) {
    if (![value, policy.baseline, policy.perCommonPct, policy.capPct].every(n => Number.isFinite(n) && n >= 0)) {
        throw new Error('조합 위력 보정: 원재료 수·기준·증가율·상한은 0 이상의 유한한 숫자여야 해요.');
    }
    return Math.min(policy.capPct, Math.max(0, (value - policy.baseline) * policy.perCommonPct));
}
