import { describe, expect, it } from 'vitest';
import { attrKeys, buildRules, roleKeys } from '../src/battle/rules.js';
import { loadBattleRules } from '../src/battle/load.js';
import { innateLearned, learnOffers, slotSkills } from '../src/battle/growth.js';
import { act, activeOf, createBattle, skillProblem } from '../src/battle/engine.js';
const R = loadBattleRules();
const originalIds = ['quick', 'peck', 'sand', 'growl', 'preen', 'snack', 'flame', 'frost', 'wrap', 'brace', 'pump', 'cheer', 'pierce', 'grit', 'block', 'steal', 'rest', 'ambush', 'rush', 'meteor', 'armor', 'quake', 'finisher', 'flag', 'comeback', 'blizzard'];
const additions = ['petal', 'tide', 'judgment', 'gravity'];
const record = (id = 'D1', extra = {}) => ({ uid: `catalog-${id}`, id, name: id, tier: 'LIMITED', level: 50, xp: 0, stage: 0, evoAugs: [], ...extra });
function training(skillId) {
    const b = createBattle(R, [record()], [record('D2')], 19);
    b.phase = 'action';
    for (const side of b.sides) {
        side.offers = null;
        side.augs = [];
    }
    const me = activeOf(b, 0), foe = activeOf(b, 1), move = R.learnSkill(skillId, 'STONE');
    me.skills = [move];
    me.base.spd = 1000;
    // 상대가 쓰러지거나 방어 효과를 쓰지 않아 각 타격과 약화 지속을 확인할 수 있어요.
    for (const f of [me, foe]) {
        f.passive = {};
        f.maxHp = f.hp = 100000;
    }
    const response = { id: 'catalog-response', name: '훈련 응수', attr: 'STONE', cat: 'phys', power: 1, cd: 0, effects: [], desc: '' };
    foe.skills = [response];
    const cast = () => act(b, [{ kind: 'skill', skillId: move.id }, { kind: 'skill', skillId: response.id }]);
    return { b, me, foe, move, cast };
}
describe('속성별 학습 기술 개편', () => {
    it('기존 저장 키 26개가 사라지지 않고 구형·현재 저장 모두 같은 전투 기술 ID로 읽힌다', () => {
        expect(Object.keys(R.data.learn.skills).slice(0, originalIds.length)).toEqual(originalIds);
        for (const id of originalIds) {
            for (const learned of [[id], ['@', 'basic', id]]) {
                expect(slotSkills(R, record('D1', { learned })).map(s => s.id)).toContain(`learn-${id}`);
            }
        }
        expect(R.learnSkill('quick', 'STONE')?.name).toBe('일섬 돌파');
        expect(R.learnSkill('peck', 'STONE')?.name).toBe('쇄도하는 깃날');
    });
    it('새 속성 학습기는 10속성을 모두 다루며 각 역할의 해당 레벨 후보에서 잘리지 않는다', () => {
        expect(new Set(Object.values(R.data.learn.skills).filter(s => s.attr).map(s => s.attr))).toEqual(new Set(attrKeys));
        expect(R.learnables).toHaveLength(30);
        for (const role of roleKeys) {
            const id = [...R.units.values()].find(u => u.role === role).id;
            for (const [index, level] of R.data.learn.levels.entries()) {
                const rec = record(id, { level, learnUsed: index, learned: ['@', 'basic'] });
                const eligible = R.learnables.filter(s => s.level === level && (!s.roles || s.roles.includes(role)));
                const offers = learnOffers(R, rec).map(s => s.id);
                expect(eligible.length).toBeLessThanOrEqual(R.data.learn.offers);
                expect(offers).toEqual(eligible.map(s => `learn-${s.id}`));
                for (const key of additions.filter(key => R.data.learn.skills[key].level === level))
                    expect(offers).toContain(`learn-${key}`);
            }
        }
    });
    it('신규 기술은 Lv10 선천 후보를 바꾸지 않아 기존 33종의 같은 개체 씨앗이 유지된다', () => {
        const previous = structuredClone(R.data);
        for (const key of additions)
            delete previous.learn.skills[key];
        const before = buildRules(previous);
        expect(R.data.learn.levels).toEqual([10, 20, 30, 40, 50]);
        expect(R.data.learn.perChance).toBe(2);
        for (const id of R.units.keys())
            for (let i = 0; i < 16; i++) {
                const seed = `saved-owned-${i}`;
                expect(innateLearned(R, id, seed)).toEqual(innateLearned(before, id, seed));
            }
    });
    it('낙화 난무는 꽃 속성 3타를 개별 판정하고 실제 체력 감소량을 남긴다', () => {
        const { b, move, cast } = training('petal');
        cast();
        const hits = b.events.filter(e => e.kind === 'damage' && e.skillId === move.id);
        expect(hits).toHaveLength(3);
        expect(hits.map(e => e.hit)).toEqual([1, 2, 3]);
        for (const hit of hits) {
            expect(hit.attr).toBe('BLOSSOM');
            expect(hit.amount).toBe(hit.hpBefore - hit.hpAfter);
        }
    });
    it('금익의 심판은 고정 황금 속성과 확정 급소를 쓰되 공격 뒤 휴식 없이 다시 쓸 수 있다', () => {
        const { b, move, cast } = training('judgment');
        cast();
        expect(skillProblem(b, 0, move)).toBeNull();
        cast();
        const hits = b.events.filter(e => e.kind === 'damage' && e.skillId === move.id);
        expect(hits).toHaveLength(2);
        for (const hit of hits)
            expect(hit).toMatchObject({ attr: 'GOLD', crit: true });
    });
    it('해일·중력 약화는 반복 공격에도 중첩·무한 갱신하지 않고 정해진 턴에 끝난다', () => {
        for (const [key, stat, pct] of [['tide', 'def', -15], ['gravity', 'spd', -25]]) {
            const { foe, move, b, cast } = training(key);
            cast();
            expect(foe.mods).toEqual([{ stat, pct, turns: 1 }]);
            expect(skillProblem(b, 0, move)).toBeNull();
            cast();
            expect(foe.mods).toEqual([]);
            cast();
            expect(foe.mods).toEqual([{ stat, pct, turns: 1 }]);
            expect(move.effects.every(e => e.kind === 'buff')).toBe(true);
        }
    });
});
