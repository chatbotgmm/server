import { describe, expect, it } from 'vitest';
import { loadContent } from '../src/content.js';
import { commonRecipeValues, recipePowerBonus } from '../src/battle/recipe-value.js';
const character = (id, rarity) => ({
    id, name: id, rarity, position: 0, quote: '', tagline: '', imageUrl: '', introduction: '', reason: '',
});
const recipe = (resultId, ...materials) => ({ resultId, materials, story: '', hidden: false });
const policy = { baseline: 30, perCommonPct: 0.5, capPct: 25 };
function fixture() {
    return {
        characters: [character('C1', 'COMMON'), character('C2', 'COMMON'), character('U1', 'UNCOMMON'), character('S1', 'SPECIAL')],
        recipes: [recipe('U1', 'C1', 'C1', 'C2'), recipe('S1', 'U1', 'U1', 'C2')],
    };
}
const expectedCombatValues = {
    D1: 35, D2: 40, D3: 34, D4: 35, D5: 36, D6: 36, D7: 31, D8: 30,
    T1: 49, T2: 58, T3: 49, T4: 56, T5: 50, T6: 53, T7: 51, T8: 48,
    E1: 50, E2: 56, E3: 50, E4: 55, E5: 51, E6: 56, E7: 48, E8: 54,
    I1: 65, I2: 78, I3: 67, I4: 73, I5: 68, I6: 71, I7: 61, I8: 69, I9: 70,
};
describe('흔함 원재료에 따른 조합가치', () => {
    it('중복 재료의 개수와 중간 단계 조합 수량을 모두 더한다', () => {
        expect(commonRecipeValues(fixture(), ['C1', 'U1', 'S1'])).toEqual({ C1: 1, U1: 3, S1: 7 });
    });
    it('요청하지 않은 종의 무조합 특수 획득 규칙은 전투종 계산을 막지 않는다', () => {
        const content = fixture();
        content.characters.push(character('N1', 'NEUTRAL'));
        content.characters.push(character('H1', 'HIDDEN'));
        content.recipes.push(recipe('H1', 'N1', 'C1'));
        expect(commonRecipeValues(content, new Set(['S1']))).toEqual({ S1: 7 });
        expect(commonRecipeValues(content, [])).toEqual({});
    });
    it('중성 갈매미와 부산물을 원가 0으로 묵살하지 않고 환산 불가를 알린다', () => {
        const content = fixture();
        content.characters.push(character('N1', 'NEUTRAL'), character('H1', 'HIDDEN'));
        content.recipes.push(recipe('H1', 'N1', 'C1'));
        expect(() => commonRecipeValues(content, ['N1'])).toThrow(/N1.*NEUTRAL.*조합식/);
        expect(() => commonRecipeValues(content, ['H1'])).toThrow(/N1.*조합식/);
    });
    it('누락된 요청종과 재료를 명확히 알려 잘못된 값을 만들지 않는다', () => {
        expect(() => commonRecipeValues(fixture(), ['missing'])).toThrow(/알 수 없는 갈매미 missing/);
        const content = fixture();
        content.recipes[0].materials.push('missing');
        expect(() => commonRecipeValues(content, ['S1'])).toThrow(/S1 → U1 → missing/);
    });
    it('직접·간접 순환을 경로와 함께 거부한다', () => {
        const direct = fixture();
        direct.recipes[0].materials = ['U1'];
        expect(() => commonRecipeValues(direct, ['U1'])).toThrow(/조합식 순환 U1 → U1/);
        const indirect = fixture();
        indirect.recipes[0].materials = ['S1'];
        expect(() => commonRecipeValues(indirect, ['S1'])).toThrow(/조합식 순환 S1 → U1 → S1/);
    });
    it('빈 조합·모호한 복수 조합·안전 정수 초과를 거부한다', () => {
        const empty = fixture();
        empty.recipes[0].materials = [];
        expect(() => commonRecipeValues(empty, ['U1'])).toThrow(/재료가 비어/);
        const multiple = fixture();
        multiple.recipes.push(recipe('U1', 'C1', 'C2'));
        expect(() => commonRecipeValues(multiple, ['U1'])).toThrow(/조합식이 여러 개/);
        const oversized = { characters: [character('C1', 'COMMON')], recipes: [] };
        let previous = 'C1';
        for (let i = 0; i < 54; i++) {
            const id = `big-${i}`;
            oversized.characters.push(character(id, 'SPECIAL'));
            oversized.recipes.push(recipe(id, previous, previous));
            previous = id;
        }
        expect(() => commonRecipeValues(oversized, [previous])).toThrow(/안전한 정수 범위/);
    });
    it('히든·뽑기 제외 여부는 구조가치를 바꾸지 않고 입력도 변경하지 않는다', () => {
        const content = fixture();
        content.characters[2].noGacha = true;
        content.characters[3].rarity = 'HIDDEN';
        content.recipes[1].hidden = true;
        const before = structuredClone(content);
        expect(commonRecipeValues(content, ['S1'])).toEqual({ S1: 7 });
        expect(content).toEqual(before);
    });
    it('실제 최상위 33종의 재귀 환산값과 등급 내 가격 차이를 유지한다', () => {
        const content = loadContent();
        const ids = content.characters.filter(c => ['LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL'].includes(c.rarity)).map(c => c.id);
        const actual = commonRecipeValues(content, ids);
        expect(ids).toHaveLength(33);
        expect(actual).toEqual(expectedCombatValues);
        expect(Math.min(...Object.values(actual))).toBe(30);
        expect(Math.max(...Object.values(actual))).toBe(78);
        expect(actual.D2).toBeGreaterThan(actual.D8);
        expect(recipePowerBonus(actual.D2, policy)).toBeGreaterThan(recipePowerBonus(actual.D8, policy));
        expect(actual.T8).toBe(actual.E7);
    });
});
describe('조합가치 위력 증가율', () => {
    it('흔함 30마리 기준, 마리당 0.5%로 늘고 최대 25%까지 제한한다', () => {
        expect(recipePowerBonus(1, policy)).toBe(0);
        expect(recipePowerBonus(30, policy)).toBe(0);
        expect(recipePowerBonus(31, policy)).toBe(0.5);
        expect(recipePowerBonus(40, policy)).toBe(5);
        expect(recipePowerBonus(78, policy)).toBe(24);
        expect(recipePowerBonus(80, policy)).toBe(25);
        expect(recipePowerBonus(1000, policy)).toBe(25);
    });
    it('같은 가치는 같은 보정, 더 높은 가치는 같거나 더 높은 보정을 갖는다', () => {
        const values = Object.values(expectedCombatValues).sort((a, b) => a - b);
        for (let i = 1; i < values.length; i++) {
            expect(recipePowerBonus(values[i], policy)).toBeGreaterThanOrEqual(recipePowerBonus(values[i - 1], policy));
        }
    });
    it('정책을 바꿔도 하한과 상한을 지키고 잘못된 숫자를 거부한다', () => {
        expect(recipePowerBonus(78, { baseline: 0, perCommonPct: 0, capPct: 25 })).toBe(0);
        expect(recipePowerBonus(78, { baseline: 30, perCommonPct: 1, capPct: 10 })).toBe(10);
        for (const value of [NaN, Infinity, -1])
            expect(() => recipePowerBonus(value, policy)).toThrow(/유한한 숫자/);
        expect(() => recipePowerBonus(78, { ...policy, baseline: -1 })).toThrow(/유한한 숫자/);
        expect(() => recipePowerBonus(78, { ...policy, perCommonPct: Infinity })).toThrow(/유한한 숫자/);
        expect(() => recipePowerBonus(78, { ...policy, capPct: NaN })).toThrow(/유한한 숫자/);
    });
});
