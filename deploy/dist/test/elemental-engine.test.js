import { describe, expect, it } from 'vitest';
import { loadContent } from '../src/content.js';
import { act, activeOf, createBattle, pickAi, skillPreview } from '../src/battle/engine.js';
import { loadBattleRules } from '../src/battle/load.js';
const R = loadBattleRules(), content = loadContent();
const record = (uid) => ({ uid, id: 'D1', name: content.map.get('D1').name, tier: 'LIMITED', level: 30, xp: 0, stage: 0, evoAugs: [] });
function ready() {
    const b = createBattle(R, [record('me')], [record('foe')], 17);
    b.phase = 'action';
    for (const s of b.sides) {
        s.offers = null;
        s.augs = [];
        for (const f of s.fighters) {
            f.passive = {};
            f.maxHp = f.hp = 1000;
            f.scale = 1;
            f.attr = 'STONE';
            f.base = { patk: 100, pdef: 100, matk: 100, mdef: 100, spd: 100 };
        }
    }
    activeOf(b, 1).base.spd = 1;
    activeOf(b, 1).status.sleep = 100;
    return b;
}
const move = (extra = {}) => ({ id: 'element-test', name: '속성 시험', attr: 'STONE', power: 40, cat: 'phys', crit: true, cd: 0, effects: [], desc: '', ...extra });
const conditional = (when, extra = {}) => move({ bonus: { when, pct: 50 }, ...extra });
function cast(b, skill) { activeOf(b, 0).skills = [skill]; act(b, [{ kind: 'skill', skillId: skill.id }, { kind: 'skill', skillId: activeOf(b, 1).skills[0].id }]); }
const damageEvents = (b) => b.events.filter(e => e.kind === 'damage' && e.reason === 'skill' && e.actor?.side === 0);
const healEvents = (b) => b.events.filter(e => e.kind === 'heal' && e.reason === 'lifesteal' && e.actor?.side === 0);
const boosted = (b, when) => skillPreview(b, 0, conditional(when)).bonusActive;
describe('속성 기술의 조건부 위력', () => {
    it.each([
        ['burned', (f) => { f.status.burn = 1; }, (f) => { f.status.burn = 0; }],
        ['bound', (f) => { f.status.bind = 1; }, (f) => { f.status.bind = 0; }],
        ['slowed', (f) => { f.mods = [{ stat: 'spd', pct: -20, turns: 1 }]; }, (f) => { f.mods = [{ stat: 'spd', pct: -20, turns: 0 }]; }],
        ['weakened', (f) => { f.mods = [{ stat: 'def', pct: -20, turns: 1 }]; }, (f) => { f.mods = [{ stat: 'def', pct: -20, turns: 0 }]; }],
    ])('%s는 현재 유효한 상대 상태만 판정한다', (when, apply, expire) => {
        const b = ready(), foe = activeOf(b, 1);
        expect(boosted(b, when)).toBe(false);
        apply(foe);
        expect(boosted(b, when)).toBe(true);
        expire(foe);
        expect(boosted(b, when)).toBe(false);
    });
    it('속도 감소 조건은 묶임도 포함하지만 속도 강화·다른 능력치 약화는 포함하지 않는다', () => {
        const b = ready(), foe = activeOf(b, 1);
        foe.status.bind = 1;
        expect(boosted(b, 'slowed')).toBe(true);
        foe.status.bind = 0;
        foe.mods = [{ stat: 'spd', pct: 20, turns: 1 }, { stat: 'atk', pct: -20, turns: 1 }];
        expect(boosted(b, 'slowed')).toBe(false);
        expect(boosted(b, 'weakened')).toBe(false);
    });
    it.each([
        ['lowHp', 'me', 500, 501], ['finisher', 'foe', 400, 401], ['opening', 'foe', 900, 899],
    ])('%s는 체력 경계값을 포함한다', (when, who, on, off) => {
        const b = ready(), f = activeOf(b, who === 'me' ? 0 : 1);
        f.hp = on;
        expect(boosted(b, when)).toBe(true);
        f.hp = off;
        expect(boosted(b, when)).toBe(false);
    });
    it('후공 조건은 현재 속도와 상태·증강체를 반영하고 같은 속도에는 적용하지 않는다', () => {
        const b = ready(), me = activeOf(b, 0), foe = activeOf(b, 1);
        foe.base.spd = 100;
        expect(boosted(b, 'slower')).toBe(false);
        foe.base.spd = 110;
        expect(boosted(b, 'slower')).toBe(true);
        b.sides[0].augs = ['swift'];
        expect(boosted(b, 'slower')).toBe(false);
        me.status.bind = 2;
        expect(boosted(b, 'slower')).toBe(true);
        b.sides[0].augs = [];
        me.status.bind = 0;
        foe.base.spd = 90;
        me.mods = [{ stat: 'spd', pct: -20, turns: 2 }];
        expect(boosted(b, 'slower')).toBe(true);
    });
    it('미리보기는 정수 위력·기본 위력·조건 정보를 제공하고 난수나 전투를 바꾸지 않는다', () => {
        const b = ready(), foe = activeOf(b, 1);
        foe.status.burn = 2;
        const skill = conditional('burned', { power: 41, bonus: { when: 'burned', pct: 25 }, attr: 'FIRE', cat: 'mag' });
        const before = JSON.stringify(b), rng = b.rng;
        expect(skillPreview(b, 0, skill)).toMatchObject({ power: 51, basePower: 41, bonusActive: true, bonusPct: 25, attr: 'FIRE', category: 'mag' });
        expect(JSON.stringify(b)).toBe(before);
        expect(b.rng).toBe(rng);
        expect(skill.power).toBe(41);
        foe.status.burn = 0;
        expect(skillPreview(b, 0, skill)).toMatchObject({ power: 41, basePower: 41, bonusActive: false, bonusPct: 0 });
        expect(skillPreview(b, 0, move({ power: undefined }))).toMatchObject({ power: 0, basePower: 0, bonusActive: false, bonusPct: 0, multiplier: 1 });
    });
    it.each([['opening', 900, 60], ['finisher', 410, 40]])('%s 다단 공격은 시전 시작 위력을 끝까지 유지한다', (when, hp, power) => {
        const b = ready(), foe = activeOf(b, 1);
        foe.hp = hp;
        const skill = conditional(when, { hits: 3 });
        const preview = skillPreview(b, 0, skill);
        expect(preview.power).toBe(power);
        cast(b, skill);
        const hits = damageEvents(b);
        expect(hits).toHaveLength(3);
        expect(hits.map(e => e.power)).toEqual([power, power, power]);
        expect(b.events.find(e => e.kind === 'skill' && e.actor?.side === 0)?.power).toBe(power);
        expect(foe.hp).toBeLessThan(hp);
        expect(skill.power).toBe(40);
        if (when === 'opening') {
            expect(foe.hp).toBeLessThan(900);
            expect(b.log.filter(l => l.includes('조건 강화'))).toHaveLength(1);
            expect(b.log.find(l => l.includes('조건 강화'))).toContain('위력 40 → 60');
        }
        else {
            expect(foe.hp).toBeLessThan(400);
            expect(b.log.some(l => l.includes('조건 강화'))).toBe(false);
        }
    });
    it('조합 보정 위력을 기준으로 조건 보너스를 적용하고 조합 전 메타데이터와 혼동하지 않는다', () => {
        const b = ready(), id = [...R.units.keys()].find(id => R.recipeBonus(id) > 0);
        expect(id).toBeDefined();
        const skill = R.unitPower(id, conditional('opening')), preview = skillPreview(b, 0, skill);
        expect(skill.basePower).toBe(40);
        expect(preview.basePower).toBe(skill.power);
        expect(preview.basePower).toBeGreaterThan(40);
        expect(preview.power).toBe(Math.round(skill.power * 1.5));
        cast(b, skill);
        expect(damageEvents(b)[0].power).toBe(preview.power);
    });
    it('추가 합동기는 사용하는 갈매미의 조합 가치로 한 번 보정한다', () => {
        const [syn, def] = Object.entries(R.data.synergies).find(([, s]) => s.members.some(id => R.recipeBonus(id) > 0));
        const team = def.members.slice(0, 2).map(id => ({ ...record(id), id, name: content.map.get(id).name, tier: content.map.get(id).rarity }));
        const b = createBattle(R, team, [record('foe')], 17);
        for (const f of b.sides[0].fighters) {
            const expected = R.unitPower(f.rec.id, R.comboSkill(syn, f.attr));
            expect(f.skills.find(s => s.id === `combo-${syn}`)).toEqual(expected);
        }
    });
    it('같은 기술의 부가 효과는 첫 타격의 조건을 소급하여 발동시키지 않는다', () => {
        const b = ready();
        cast(b, conditional('bound', { hits: 3, effects: [{ kind: 'status', status: 'bind', chance: 100, turns: 2 }] }));
        expect(damageEvents(b).map(e => e.power)).toEqual([40, 40, 40]);
        expect(activeOf(b, 1).status.bind).toBeGreaterThan(0);
    });
});
describe('속성 기술 관통과 실제 피해 흡혈', () => {
    it.each(['phys', 'mag'])('%s 기술 관통은 패시브·증강체 관통의 남은 방어 배율에 곱해진다', cat => {
        const pierced = ready(), equivalent = ready();
        for (const b of [pierced, equivalent]) {
            activeOf(b, 0).passive.pierce = 20;
            b.sides[0].augs = ['pierce'];
        }
        activeOf(equivalent, 1).base.pdef = activeOf(equivalent, 1).base.mdef = 70;
        cast(pierced, move({ cat, pierce: 30 }));
        cast(equivalent, move({ cat }));
        expect(damageEvents(pierced).map(e => e.amount)).toEqual(damageEvents(equivalent).map(e => e.amount));
        const plain = ready();
        activeOf(plain, 0).passive.pierce = 20;
        plain.sides[0].augs = ['pierce'];
        cast(plain, move({ cat }));
        expect(damageEvents(pierced)[0].amount).toBeGreaterThan(damageEvents(plain)[0].amount);
    });
    it('다단 흡혈은 실제 체력 감소의 합계로 한 번 회복하며 오버킬을 회복량에 더하지 않는다', () => {
        const b = ready(), me = activeOf(b, 0), foe = activeOf(b, 1);
        me.hp = 500;
        foe.hp = 100;
        cast(b, move({ power: 50, hits: 3, drain: 50 }));
        const hits = damageEvents(b), actual = hits.reduce((sum, e) => sum + e.amount, 0);
        expect(hits.length).toBeGreaterThan(1);
        expect(actual).toBe(100);
        expect(foe.hp).toBe(0);
        expect(healEvents(b)).toHaveLength(1);
        expect(healEvents(b)[0]).toMatchObject({ amount: 50, hpBefore: 500, hpAfter: 550, skillId: 'element-test', power: 50 });
    });
    it('단타 오버킬·회복 상한은 실제 회복 이벤트에 정확히 반영한다', () => {
        const b = ready();
        activeOf(b, 0).hp = 500;
        activeOf(b, 1).hp = 2;
        cast(b, move({ power: 1000, drain: 50 }));
        expect(healEvents(b)[0]).toMatchObject({ amount: 1, hpBefore: 500, hpAfter: 501 });
        const capped = ready();
        activeOf(capped, 0).hp = 999;
        cast(capped, move({ drain: 50 }));
        expect(healEvents(capped)[0]).toMatchObject({ amount: 1, hpAfter: 1000 });
    });
    it('보호·체력 1 버티기로 실제 피해가 없으면 흡혈하지 않는다', () => {
        const protectedBattle = ready();
        activeOf(protectedBattle, 0).hp = 500;
        activeOf(protectedBattle, 1).protect = true;
        cast(protectedBattle, conditional('opening', { drain: 50 }));
        expect(protectedBattle.events.find(e => e.reason === 'protect')).toMatchObject({ amount: 0, power: 60 });
        expect(healEvents(protectedBattle)).toEqual([]);
        expect(activeOf(protectedBattle, 0).hp).toBe(500);
        const endured = ready();
        activeOf(endured, 0).hp = 500;
        activeOf(endured, 1).hp = 1;
        activeOf(endured, 1).endure = 2;
        cast(endured, move({ drain: 50, hits: 3 }));
        expect(damageEvents(endured).map(e => e.amount)).toEqual([0, 0, 0]);
        expect(healEvents(endured)).toEqual([]);
    });
    it('보호는 방어 감소·감속을 막으면서 공격자의 강화와 대기 아군 회복은 유지한다', () => {
        const b = ready(), me = activeOf(b, 0), foe = activeOf(b, 1), bench = { ...structuredClone(me), rec: record('bench'), hp: 800 };
        b.sides[0].fighters.push(bench);
        foe.protect = true;
        cast(b, move({ effects: [
                { kind: 'buff', stat: 'def', pct: -10, turns: 2, target: 'foe' },
                { kind: 'buff', stat: 'spd', pct: -15, turns: 2, target: 'foe' },
                { kind: 'buff', stat: 'atk', pct: 20, turns: 2, target: 'self' },
                { kind: 'heal', pct: 10, target: 'bench' },
            ] }));
        expect(b.events.find(e => e.reason === 'protect')).toMatchObject({ amount: 0 });
        expect(foe.hp).toBe(1000);
        expect(foe.mods).toEqual([]);
        expect(me.mods).toContainEqual({ stat: 'atk', pct: 20, turns: 1 });
        expect(bench.hp).toBe(900);
    });
    it('이미 전투불가인 공격자는 공격하거나 흡혈하지 않고 반사로 쓰러져도 다시 살아나지 않는다', () => {
        const down = ready();
        activeOf(down, 0).hp = 0;
        cast(down, move({ drain: 50 }));
        expect(healEvents(down)).toEqual([]);
        expect(activeOf(down, 0).hp).toBe(0);
        const reflected = ready();
        activeOf(reflected, 0).hp = 1;
        activeOf(reflected, 1).passive.reflect = 500;
        cast(reflected, move({ drain: 50 }));
        expect(activeOf(reflected, 0).hp).toBe(0);
        const healing = healEvents(reflected)[0], reflection = reflected.events.find(e => e.reason === 'reflect');
        expect(healing.id).toBeLessThan(reflection.id);
        expect(reflected.events.filter(e => e.id > reflection.id && e.kind === 'heal' && e.target?.side === 0)).toEqual([]);
    });
    it('추가 필드가 없거나 0이고 조건이 꺼진 기술은 기존 피해·난수·회복을 바꾸지 않는다', () => {
        const plain = ready(), inactive = ready();
        for (const b of [plain, inactive]) {
            activeOf(b, 0).hp = 500;
            activeOf(b, 0).passive.lifesteal = 20;
            b.sides[0].augs = ['drain'];
        }
        cast(plain, move());
        cast(inactive, conditional('burned', { pierce: 0, drain: 0 }));
        const state = (b) => ({ rng: b.rng, log: b.log, events: b.events, sides: b.sides.map(s => s.fighters.map(f => ({ hp: f.hp, status: f.status, hitBy: f.hitBy }))) });
        expect(state(inactive)).toEqual(state(plain));
    });
});
describe('상대 AI의 현재 기술 평가', () => {
    it('조건을 만족했을 때만 추가 위력을 평가한다', () => {
        const b = ready(), me = activeOf(b, 0);
        me.skills = [move({ id: 'plain', power: 50 }), conditional('burned', { id: 'conditional' })];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'plain' });
        activeOf(b, 1).status.burn = 2;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'conditional' });
    });
    it('역상성·무속성·약점 보호를 실제 공격 배율과 동일하게 평가한다', () => {
        const b = ready(), me = activeOf(b, 0), foe = activeOf(b, 1);
        me.attr = 'GOLD';
        foe.attr = 'SEA';
        me.skills = [move({ id: 'strong', attr: 'STAR', power: 40 }), move({ id: 'neutral', attr: 'GOLD', power: 45 })];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
        b.sides[0].augs = ['reverse'];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'neutral' });
        b.sides[0].augs = [];
        b.sides[0].neutral = 2;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'neutral' });
        b.sides[0].neutral = 0;
        b.sides[1].augs = ['weakguard'];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'neutral' });
    });
    it('대기 아군 회복은 실제 살아 있는 회복 대상과 회복 제한을 확인한다', () => {
        const b = ready(), me = activeOf(b, 0);
        me.hp = 100;
        me.skills = [move({ id: 'support', power: 42, effects: [{ kind: 'heal', pct: 4, target: 'bench' }] }), move({ id: 'strong', power: 100 })];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
        const bench = { ...structuredClone(me), rec: record('bench'), hp: 100 };
        b.sides[0].fighters.push(bench);
        me.hp = me.maxHp;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'support' });
        bench.hp = bench.maxHp;
        me.hp = 100;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
        bench.hp = 0;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
        bench.hp = 100;
        me.healUntil = b.turn;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
    });
    it('회복 제한 중인 자기 회복 공격은 사용할 수 있는 공격 위력으로 평가한다', () => {
        const b = ready(), me = activeOf(b, 0);
        me.hp = 400;
        me.skills = [move({ id: 'self-heal', power: 20, effects: [{ kind: 'heal', pct: 25, target: 'self' }] }), move({ id: 'strong', power: 80 })];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'self-heal' });
        me.healUntil = b.turn;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'strong' });
    });
    it('공격과 회복이 함께 있는 기술의 공격 점수를 회복 점수로 덮어쓰지 않는다', () => {
        const b = ready(), me = activeOf(b, 0);
        me.skills = [move({ id: 'weak', power: 20 }), move({ id: 'attack-heal', power: 80, effects: [{ kind: 'heal', pct: 10, target: 'self' }] })];
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'attack-heal' });
        me.hp = 400;
        expect(pickAi(b, 0)).toEqual({ kind: 'skill', skillId: 'attack-heal' });
    });
});
