import { describe, expect, it } from 'vitest';
import { loadBattleRules } from '../src/battle/load.js';
import { attrKeys, rulesFrom } from '../src/battle/core.js';
import { loadContent } from '../src/content.js';
import { slotSkills, learnOffers, promoSkills } from '../src/battle/growth.js';
const R = loadBattleRules(), content = loadContent();
const rec = (id, learned = ['@', 'basic', 'role', 'sub']) => ({ uid: `profiles-${id}`, id, name: id, tier: (content.map.get(id)?.rarity ?? 'LIMITED'), level: 50, xp: 0, stage: 1, evoAugs: [], learned, learnUsed: 0, learnPick: 0 });
const mechanics = (s) => JSON.stringify({ hits: s.hits ?? 1, crit: s.crit ?? false, priority: s.priority ?? false, pierce: s.pierce ?? 0, drain: s.drain ?? 0, bonus: s.bonus, effects: s.effects });
describe('조합 가치와 속성별 기술 구성', () => {
    it('33종의 비용이 등급 대신 실제 조합식을 따르고 모든 공격기의 위력에 반영된다', () => {
        expect(Object.keys(R.data.recipeValues)).toHaveLength(33);
        expect(R.recipeValue('I2')).toBe(78);
        expect(R.recipeBonus('I2')).toBe(24);
        for (const id of R.units.keys()) {
            const record = rec(id), before = JSON.stringify(record);
            for (const s of [...slotSkills(R, record), ...promoSkills(R, record), ...learnOffers(R, record)]) {
                if (s.power) {
                    expect(s.recipeValue).toBe(R.recipeValue(id));
                    expect(s.recipeBonusPct).toBe(R.recipeBonus(id));
                    expect(s.power).toBe(Math.round(s.basePower * (1 + R.recipeBonus(id) / 100)));
                }
                else {
                    expect(s.recipeBonusPct).toBeUndefined();
                    expect(s.basePower).toBeUndefined();
                }
            }
            expect(JSON.stringify(record)).toBe(before);
        }
    });
    it('같은 학습 기술은 조합 가치가 클수록 강하고 보정은 반복 호출해도 중첩되지 않는다', () => {
        const ordered = [...R.units.keys()].sort((a, b) => R.recipeValue(a) - R.recipeValue(b));
        const base = R.learnSkill('quick', 'STONE'), powers = ordered.map(id => R.unitPower(id, base).power);
        expect(powers[0]).toBe(40);
        expect(powers.at(-1)).toBe(50);
        expect(powers).toEqual([...powers].sort((a, b) => a - b));
        const stronger = R.unitPower('I2', base);
        expect(R.unitPower('I2', stronger)).toEqual(stronger);
        expect(base.power).toBe(40);
        expect(stronger.desc).toContain('위력 50');
    });
    it('기본기 10종은 이름·속성·위력을 지워도 실제 공격 방식이 서로 다르다', () => {
        const basics = attrKeys.map(a => R.elementalSkill('basic', a));
        expect(new Set(basics.map(mechanics)).size).toBe(10);
        for (const s of basics) {
            expect(s.id).toBe(`basic-${s.attr}`);
            expect(s.cd).toBe(0);
            expect(s.desc.length).toBeGreaterThan(0);
        }
    });
    it('준비 공격과 후속 공격은 실제 상태·약화 조건으로 연결된다', () => {
        for (const [attr, kind, condition] of [['FIRE', 'status', 'burned'], ['ICE', 'buff', 'slowed'], ['THREAD', 'status', 'bound'], ['STONE', 'buff', 'weakened']]) {
            const basic = R.elementalSkill('basic', attr), follow = R.elementalSkill('attack', attr);
            expect(basic.effects.some(e => e.kind === kind)).toBe(true);
            expect(follow.bonus?.when).toBe(condition);
        }
        expect(R.elementalSkill('basic', 'GOLD')?.pierce).toBe(25);
        expect(R.elementalSkill('basic', 'BLOSSOM')?.drain).toBe(20);
        expect(R.elementalSkill('basic', 'SEA')?.effects).toEqual([{ kind: 'heal', pct: 4, target: 'bench' }]);
        expect(R.elementalSkill('basic', 'GRAVITY')?.bonus?.when).toBe('slower');
        expect(R.elementalSkill('basic', 'SHADOW')?.bonus?.when).toBe('finisher');
        expect(R.elementalSkill('basic', 'STAR')?.hits).toBe(2);
    });
    it('기존 기술 칸 ID를 유지하고 고유기 회복률·상태 확률·지속시간을 보정하지 않는다', () => {
        for (const id of R.units.keys()) {
            const actual = slotSkills(R, rec(id, ['@', 'sig']))[0], original = R.unitDef(id).signature;
            expect(actual.id).toBe('sig');
            expect(actual.effects).toEqual(original.effects);
            expect(actual.hits).toBe(original.hits);
            expect(actual.priority).toBe(original.priority);
            expect(actual.crit).toBe(original.crit);
        }
        expect(slotSkills(R, rec('D1', ['quick'])).map(s => s.id)).toEqual(['basic-STONE', 'sig', 'learn-quick', 'role-DEFENSE']);
        expect(slotSkills(R, rec('I2', ['@', 'sig']))[0].effects).toEqual([{ kind: 'heal', pct: 30, target: 'self' }]);
    });
    it('빌런의 수치·기본기 이름·기술은 플레이어용 보정과 분리한다', () => {
        for (const id of R.villains.keys()) {
            const f = rec(id), list = slotSkills(R, f);
            expect(list[0].power).toBe(R.data.basicPower);
            expect(R.recipeValue(id)).toBeUndefined();
            expect(R.recipeBonus(id)).toBe(0);
            expect(list.every(s => s.recipeValue === undefined)).toBe(true);
        }
        expect(slotSkills(R, rec('V1'))[0].name).toBe('먹물 뿌리기');
        expect(slotSkills(R, rec('V7'))[0].name).toBe('집게 꼬집기');
    });
    it('서버에서 보내는 JSON만으로 브라우저도 같은 조합 보정·기술·설명을 만든다', () => {
        const browser = rulesFrom(JSON.parse(JSON.stringify(R.data)));
        for (const id of R.units.keys())
            expect(slotSkills(browser, rec(id))).toEqual(slotSkills(R, rec(id)));
    });
    it('공용 공격을 배워 기본기를 잊어도 자기 속성 특성이 남고 고정 속성 기술에는 섞이지 않는다', () => {
        expect(R.learnSkill('quick', 'STONE')?.effects).toContainEqual({ kind: 'buff', stat: 'def', pct: -10, turns: 2, target: 'foe' });
        expect(R.learnSkill('peck', 'FIRE')?.effects).toContainEqual({ kind: 'status', status: 'burn', chance: 40, turns: 3 });
        expect(R.learnSkill('quick', 'GOLD')?.pierce).toBe(25);
        expect(R.learnSkill('quick', 'BLOSSOM')?.drain).toBe(20);
        expect(R.learnSkill('quick', 'SHADOW')?.bonus?.when).toBe('finisher');
        expect(R.learnSkill('quick', 'GRAVITY')?.bonus?.when).toBe('slower');
        expect(R.learnSkill('quick', 'STAR')).toMatchObject({ power: 22, hits: 2, priority: true });
        expect(R.learnSkill('peck', 'STAR')).toMatchObject({ power: 22, hits: 3 });
        expect(R.learnSkill('petal', 'FIRE')?.effects).toEqual([]);
        expect(R.learnSkill('judgment', 'BLOSSOM')?.drain).toBeUndefined();
        expect(R.learnSkill('preen', 'FIRE')?.effects).toEqual([{ kind: 'heal', pct: 25, target: 'self' }]);
    });
    it('범용 강공격은 시작·마무리 상황에서 최대위력을 내며 매턴 무조건130·160을 반복하지 않는다', () => {
        expect(R.learnSkill('rush', 'STONE')).toMatchObject({ power: 100, bonus: { when: 'opening', pct: 30 }, cd: 0 });
        expect(R.learnSkill('finisher', 'STONE')).toMatchObject({ power: 100, bonus: { when: 'finisher', pct: 60 }, cd: 0 });
    });
    it('카드는 짧은 핵심 요약을 쓰고 전체 설명·중요 불이익은 보존한다', () => {
        const fire = R.elementalSkill('attack', 'FIRE');
        expect(fire.summary).toBe('화상 적 +45%');
        expect(fire.desc).toContain('화상 상태인 상대에게 위력 +45%');
        expect(R.elementalSkill('basic', 'GOLD')?.summary).toBe('방어 25% 관통');
        expect(R.elementalSkill('basic', 'BLOSSOM')?.summary).toBe('피해 20% 흡수');
        expect(R.elementalSkill('basic', 'SEA')?.summary).toBe('대기 아군 4% 회복');
        expect(R.unitDef('E1')?.signature.summary).toContain('교체 불가');
        expect(R.unitDef('D7')?.signature.summary?.indexOf('속도 -20%')).toBe(2);
    });
});
