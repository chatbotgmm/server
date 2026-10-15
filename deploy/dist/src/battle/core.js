export const attrKeys = ['STONE', 'GOLD', 'FIRE', 'ICE', 'BLOSSOM', 'SEA', 'SHADOW', 'STAR', 'THREAD', 'GRAVITY'];
export const roleKeys = ['ATTACK', 'DEFENSE', 'UTILITY'];
export const topTiers = ['LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL'];
export const statusKeys = ['burn', 'freeze', 'sleep', 'bind', 'rage'];
export const statusName = { burn: '화상', freeze: '빙결', sleep: '잠', bind: '묶임', rage: '땅콩 폭주' };
const statName = { atk: '공격', def: '방어', spd: '속도' };
export const catName = { phys: '물리', mag: '마법', auto: '자동' };
export const battleAugIds = ['reverse', 'weakguard', 'focus', 'neutral', 'camo', 'dual', 'counter', 'learn', 'first', 'bulwark', 'firstaid', 'freeswitch', 'lastone', 'strip', 'rustle', 'peanut', 'sharp', 'swift', 'thick', 'overtime', 'drain', 'thorns', 'pierce', 'lunch'];
// ── 자동 설명 ──
const fmt = (n) => `${n > 0 ? '+' : ''}${n}%`;
function effectText(e, attrName) {
    void attrName;
    switch (e.kind) {
        case 'buff': return `${e.turns}턴 동안 ${e.target === 'self' ? '자신' : e.target === 'team' ? '아군 전체' : '상대'} ${statName[e.stat]} ${fmt(e.pct)}`;
        case 'status': return `${e.chance >= 100 ? '' : `${e.chance}% 확률로 `}상대 ${statusName[e.status]}(${e.turns}턴)`;
        case 'heal': return `${e.target === 'self' ? '자신' : '대기 아군'} 체력 ${e.pct}% 회복`;
        case 'guard': return `${e.turns}턴 동안 받는 피해 -${e.pct}%`;
        case 'endure': return `${e.turns}턴 동안 체력이 1 아래로 안 떨어짐`;
        case 'protect': return '이번 턴 공격을 모두 막음';
        case 'regen': return `${e.turns}턴 동안 ${e.target === 'team' ? '아군 모두' : '자신'} 턴마다 체력 ${e.pct}% 회복`;
        case 'cleanse': return `아군 상태 이상을 풀고 ${e.immuneTurns}턴 동안 막음`;
        case 'revive': return `쓰러진 아군 하나를 체력 ${e.pct}%로 일으킴(전투당 1번)`;
        case 'strip': return '상대 강화를 모두 지움';
        case 'recharge': return '다음 턴은 쉼';
        case 'lockSelf': return `이후 교체할 수 없는 대신 공격 +${e.atkPct}%`;
    }
}
export function skillDesc(s, attrName) {
    const parts = [];
    if (s.priority)
        parts.push('무조건 먼저');
    if (s.power)
        parts.push(`위력 ${s.power}${s.hits ? `으로 ${s.hits}번` : ''}`);
    if (s.crit)
        parts.push('반드시 급소');
    for (const e of s.effects)
        parts.push(effectText(e, attrName));
    return parts.join(', ') || '효과 없음';
}
function toEffect(e) {
    if ('buff' in e)
        return { kind: 'buff', stat: e.buff, pct: e.pct, turns: e.turns, target: e.to };
    if ('status' in e)
        return { kind: 'status', status: e.status, chance: e.chance, turns: e.turns };
    if ('heal' in e)
        return { kind: 'heal', pct: e.heal, target: e.to };
    if ('guard' in e)
        return { kind: 'guard', pct: e.guard, turns: e.turns };
    if ('endure' in e)
        return { kind: 'endure', turns: e.endure };
    if ('regen' in e)
        return { kind: 'regen', pct: e.regen, turns: e.turns, target: e.to };
    if ('cleanse' in e)
        return { kind: 'cleanse', immuneTurns: e.cleanse };
    if ('revive' in e)
        return { kind: 'revive', pct: e.revive };
    if ('strip' in e)
        return { kind: 'strip' };
    if ('recharge' in e)
        return { kind: 'recharge' };
    if ('lockSelf' in e)
        return { kind: 'lockSelf', atkPct: e.lockSelf };
    return { kind: 'protect' };
}
const passiveText = {
    pierce: v => `상대 방어 ${v}% 무시`, execute: v => `체력 30% 이하인 상대에게 피해 +${v}%`, critPlus: v => `급소 확률 +${v}%p`, opener: v => `전투 첫 공격 피해 +${v}%`,
    lifesteal: v => `준 피해의 ${v}% 회복`, reflect: v => `받은 피해의 ${v}%를 되돌려 줌`, cut: v => `받는 피해 -${v}%`, regen: v => `턴이 끝날 때 체력 ${v}% 회복`,
    lastStand: () => '전투당 한 번, 쓰러질 피해를 체력 1로 버팀', statusResist: v => `상태 이상에 걸릴 확률 -${v}%`, statusPlus: v => `상태 이상 확률 +${v}%p`,
    durationPlus: v => `주는 강화·약화가 ${v}턴 더 감`, sigCd: v => `고유기 재사용 ${v}턴 줄어듦`, handoff: v => `교체되어 나갈 때 들어오는 아군 체력 ${v}% 회복`,
    hpPct: v => `최대 체력 +${v}%`, atkPct: v => `물리·마법 공격 +${v}%`, defPct: v => `물리·마법 방어 +${v}%`, spdPct: v => `속도 +${v}%`,
};
function augDesc(id, p, attrNm) {
    void attrNm;
    switch (id) {
        case 'reverse': return '이번 전투 동안 내 공격의 상성이 뒤집혀요(강함 ↔ 약함)';
        case 'weakguard': return '내가 받는 상성 피해(강함)가 보통 피해가 돼요';
        case 'focus': return `내 상성 공격 배율이 ×${p.value}가 돼요`;
        case 'neutral': return `${p.turns}턴 동안 양쪽 모든 상성이 ×1.0이 돼요`;
        case 'camo': return '지금 나와 있는 내 갈매미의 속성이 상대에게 강한 속성으로 바뀌어요';
        case 'dual': return '지금 나와 있는 내 갈매미가 상대 약점을 찌르는 공격 속성을 하나 더 얻어요';
        case 'counter': return `상성이 불리한 공격을 받으면 위력 ${p.power}으로 바로 반격해요`;
        case 'learn': return `같은 속성에 맞을 때마다 그 속성 피해가 ${p.step}%씩 줄어요(최대 ${p.max}%)`;
        case 'first': return '다음 턴에는 무조건 내가 먼저 움직여요';
        case 'bulwark': return `지금 나와 있는 내 갈매미가 ${p.turns}턴 동안 받는 피해 -${p.pct}%`;
        case 'firstaid': return `쓰러지지 않은 아군 모두 체력 ${p.pct}% 회복`;
        case 'freeswitch': return '이번 전투 동안 교체가 턴을 쓰지 않아요';
        case 'lastone': return `아군이 하나만 남으면 그 갈매미의 공격·방어·속도 +${p.pct}%`;
        case 'strip': return `상대 강화를 모두 지우고 상대 선두 공격 -${p.pct}%(${p.turns}턴)`;
        case 'rustle': return '상대 선두가 새우gang 소리에 1턴 멈춰요';
        case 'peanut': return `내 갈매미맨들의 공격 +${p.atk}%, 대신 받는 피해 +${p.taken}%`;
        case 'sharp': return `내 갈매미들의 급소 확률 +${p.pct}%`;
        case 'swift': return `내 갈매미들의 속도 +${p.pct}%(먼저 움직이기 쉬워요)`;
        case 'thick': return `내 갈매미들의 물리·마법 방어 +${p.pct}%`;
        case 'overtime': return `내 갈매미들의 물리·마법 공격 +${p.pct}%`;
        case 'drain': return `내가 준 피해의 ${p.pct}%만큼 체력 회복`;
        case 'thorns': return `나를 때린 상대가 준 피해의 ${p.pct}%를 돌려받아요`;
        case 'pierce': return `내 공격이 상대 방어를 ${p.pct}% 무시해요`;
        case 'lunch': return `턴이 끝날 때마다 나와 있는 내 갈매미 체력 ${p.pct}% 회복`;
    }
    return '';
}
// 검사를 마친 데이터로 규칙 만들기(브라우저는 서버가 검사한 데이터를 받아 이것만 씀)
export function rulesFrom(d) {
    // 상성: 강점 2·약점 2, 자기 자신 금지
    for (const a of attrKeys) {
        const s = d.chart[a].strong;
        if (s[0] === s[1] || s.includes(a))
            throw new Error(`상성 오류 ${a}: 같은 속성이 겹치거나 자기 자신에게 강함`);
        const weakTo = attrKeys.filter(x => d.chart[x].strong.includes(a)).length;
        if (weakTo !== 2)
            throw new Error(`상성 오류 ${a}: 약점이 ${weakTo}개(2개여야 함)`);
    }
    const attrName = (a) => d.attributes[a].name;
    const typeMult = (atk, def) => d.chart[atk].strong.includes(def) ? d.multipliers.strong : d.chart[def].strong.includes(atk) ? d.multipliers.weak : 1;
    const toCat = (c) => c === '물리' ? 'phys' : c === '마법' ? 'mag' : c === '자동' ? 'auto' : undefined;
    const mkSkill = (id, s, a) => {
        if (s.power && !s.cat)
            throw new Error(`기술 「${s.name}」: 공격 기술은 cat(물리·마법·자동)을 정해야 해요`);
        const base = { name: s.name.replaceAll('{속성}', attrName(a)), cd: 0, effects: (s.effects ?? []).map(toEffect).filter(e => e.kind !== 'recharge'),
            ...(s.power ? { power: s.power, attr: s.attr ?? a, cat: toCat(s.cat) } : {}), ...(s.hits ? { hits: s.hits } : {}), ...(s.priority ? { priority: true } : {}), ...(s.crit ? { crit: true } : {}) };
        return { id, ...base, desc: s.desc ?? skillDesc(base, attrName) };
    };
    const basicSkill = (a, cat) => ({ id: `basic-${a}`, name: d.attributes[a].basic, attr: a, power: d.basicPower, cat: cat ?? toCat(d.attributes[a].basicCat), cd: 0, effects: [], desc: `위력 ${d.basicPower}` });
    const roleSkill = (r, a) => mkSkill(`role-${r}`, d.roles[r].skill, a);
    // 보조 속성 기술: 자기 속성이 아닌 공격(상성이 불리할 때 꺼내 씀)
    const subSkill = (a) => ({ id: `sub-${a}`, name: d.attributes[a].sub, attr: a, power: d.subSkill.power, cat: toCat(d.attributes[a].subCat), cd: 0, effects: [], desc: `${attrName(a)} 속성 공격 · 위력 ${d.subSkill.power}` });
    const synOf = new Map();
    for (const [k, v] of Object.entries(d.synergies))
        for (const m of v.members)
            synOf.set(m, k);
    const units = new Map(Object.entries(d.units).map(([id, u]) => [id, { id, attr: u.attr, sub: u.sub, syn: synOf.get(id), role: u.role, stats: u.stats, signature: mkSkill('sig', u.skill, u.attr) }]));
    // 스토리 빌런: 공격·방어는 역할 기본값 × power, 체력은 × hp(없으면 power). 졸병 1 미만, 보스는 hp를 크게
    const villains = new Map(Object.entries(d.villains).map(([id, v]) => [id, { id, name: v.name, basic: v.basic, basicCat: toCat(v.basicCat), mirror: v.mirror, attr: v.attr, sub: v.sub, role: v.role, power: v.power, hpMult: v.hp ?? v.power, boss: v.boss ?? false, signature: mkSkill('sig', v.skill, v.attr) }]));
    const unitDef = (id) => units.get(id) ?? villains.get(id);
    // 시너지 합동기(같은 시너지 2마리+, 팀당 1번)·레벨업으로 배우는 기술. 속성을 안 적은 기술은 쓰는 유닛의 속성
    const comboSkill = (k, a) => d.synergies[k] ? mkSkill(`combo-${k}`, d.synergies[k].skill, a) : undefined;
    const learnSkill = (id, a) => { const s = d.learn.skills[id]; if (!s)
        return undefined; const { level, roles, ...sk } = s; void level; void roles; return mkSkill(`learn-${id}`, sk, a); };
    const learnables = Object.entries(d.learn.skills).map(([id, s]) => ({ id, level: s.level, roles: s.roles }));
    const battleAugments = battleAugIds.map(id => {
        const raw = d.battleAugments[id];
        const p = Object.fromEntries(Object.entries(raw).filter(([, v]) => typeof v === 'number'));
        return { id, name: raw.name, desc: raw.desc ?? augDesc(id, p, ''), p };
    });
    const evoAugments = Object.entries(d.evoAugments).map(([id, a]) => {
        const { name, roles, desc, ...passive } = a;
        // 예전 재사용 감소 표창은 공격 보정으로 이어받아 무효 표창이 되지 않게 함.
        if (passive.sigCd) {
            passive.atkPct = (passive.atkPct ?? 0) + passive.sigCd * 5;
            delete passive.sigCd;
        }
        const text = Object.entries(passive).map(([k, v]) => passiveText[k](Number(v))).join(', ');
        return { id, name, roles, passive: passive, desc: desc ?? text };
    });
    return { data: d, attrName, typeMult, basicSkill, roleSkill, subSkill, comboSkill, learnSkill, learnables, units, villains, unitDef, battleAugments, battleAug: new Map(battleAugments.map(a => [a.id, a])),
        evoAugments, evoAug: new Map(evoAugments.map(a => [a.id, a])) };
}
