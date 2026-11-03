import { fiveStatAttack } from './roster60-five-combat.js';
const clamp = (n, min, max) => Math.max(min, Math.min(max, n));
const other = (s) => (1 - s);
const fp = (n, pct) => Math.round(n * pct / 100);
const alive = (f) => f.hp > 0;
function unit(f) {
    const v = f;
    v.augmentEffects ??= { extra: {}, mods: [], guards: [] };
    return v.augmentEffects.extra.current ??= { mods: [], guards: [], shields: {}, hitBy: {}, risks: {} };
}
function side(a, s) { return a.runtime[s].current ??= { effects: {} }; }
const def = (a, id) => { const d = a.catalog.find(d => d.id === id); if (!d)
    throw Error('CURRENT_AUGMENT_DEFINITION_MISSING'); return d; };
const param = (a, id, key, fallback = 0) => def(a, id).p[key] ?? fallback;
function owned(a, s, id) { return a.owned[s].includes(id); }
function effect(a, s, id) { return side(a, s).effects[id]; }
function active(a, s, id, turn) { const v = effect(a, s, id); return owned(a, s, id) && !!v && (v.uses === undefined || v.uses > 0) && (v.until === undefined || v.until >= turn); }
function spend(a, s, id) { const v = effect(a, s, id); if (v?.uses !== undefined && v.uses > 0)
    v.uses--; }
function event(parties, events, source, target, amount, reason, kind = 'augment-effect') {
    const targetSide = (parties[0].includes(target) ? 0 : 1);
    events.push({ kind, side: source, targetSide, targetSlot: parties[targetSide].indexOf(target), amount, reason });
}
function hurt(f, n) { const before = f.hp; f.hp = Math.max(0, f.hp - Math.max(0, Math.floor(n))); if (!f.hp)
    unit(f).shields = {}; return before - f.hp; }
function cost(f, pct) { const n = Math.floor(f.stats.hp * pct / 100); if (f.hp <= n)
    return false; f.hp -= n; return true; }
function mod(f, source, stat, pct, until) { const u = unit(f); u.mods = u.mods.filter(v => !(v.source === source && v.stat === stat)); u.mods.push({ source, stat, pct, until }); }
function stat(f, k, turn, positive = true, extra = 0) { const values = unit(f).mods.filter(v => v.stat === k && v.until >= turn).map(v => v.pct), negative = values.filter(v => v < 0).reduce((n, v) => n + v, 0), up = !positive ? 0 : k === 'speed' ? Math.max(0, extra, ...values) : values.filter(v => v > 0).reduce((n, v) => n + v, extra); return f.stats[k] * (1 + clamp(up + negative, k === 'speed' ? -100 : -50, 100) / 100); }
export function currentSync(a, parties) { for (const s of [0, 1]) {
    side(a, s).aliveCount = parties[s].filter(alive).length;
    for (const f of parties[s])
        unit(f).ownerSide = s;
} }
function guard(f, source, pct, until) { const u = unit(f); u.guards = u.guards.filter(v => v.source !== source); u.guards.push({ source, pct, until }); }
function guardKeep(f, turn) { return Math.max(.4, unit(f).guards.filter(v => v.until >= turn).reduce((v, g) => v * (1 - g.pct / 100), 1)); }
function shield(a, parties, s, f, n, until, reason, events) {
    if (!alive(f) || n <= 0)
        return 0;
    const u = unit(f), current = Object.values(u.shields).reduce((sum, x) => sum + x.amount, 0), amount = Math.max(0, Math.min(Math.round(n), Math.floor(f.stats.hp * .2) - current));
    if (!amount)
        return 0;
    const before = u.shields[reason];
    u.shields[reason] = { amount: (before?.amount ?? 0) + amount, until: Math.max(before?.until ?? until, until) };
    event(parties, events, s, f, amount, reason, 'augment-shield');
    return amount;
}
function absorb(f, n, turn) { const u = unit(f); let left = n; for (const [id, v] of Object.entries(u.shields).sort((a, b) => a[1].until - b[1].until || a[0].localeCompare(b[0]))) {
    if (v.until < turn) {
        delete u.shields[id];
        continue;
    }
    const take = Math.min(left, v.amount);
    left -= take;
    v.amount -= take;
    if (v.amount <= 0)
        delete u.shields[id];
    if (left <= 0)
        break;
} return left; }
function heal(a, parties, s, f, n, turn, reason, events, overflow = true) {
    if (!alive(f) || n <= 0)
        return 0;
    const requested = Math.max(0, Math.round(n)), before = f.hp;
    f.hp = Math.min(f.stats.hp, f.hp + requested);
    const recovered = f.hp - before;
    if (recovered)
        event(parties, events, s, f, recovered, reason, 'augment-heal');
    const excess = requested - recovered;
    if (overflow && excess > 0 && active(a, s, 'overflowguard', turn)) {
        const added = shield(a, parties, s, f, excess, turn + param(a, 'overflowguard', 'turns', 2) - 1, 'overflowguard', events);
        if (added > 0)
            spend(a, s, 'overflowguard');
    }
    return recovered;
}
function restoreMp(a, parties, s, f, pct, reason, events) { const before = f.mp; f.mp = Math.min(f.stats.mp, f.mp + fp(f.stats.mp, pct)); event(parties, events, s, f, f.mp - before, reason, 'augment-mp'); }
export function currentEligible(a, parties, s) {
    const team = parties[s].filter(alive), foes = parties[other(s)].filter(alive);
    return a.catalog.filter(d => {
        if (['handoffguard', 'relief', 'bloodrelay', 'baton', 'freeswitch', 'sharedburden'].includes(d.id) && team.length < 2)
            return false;
        if (d.id === 'firstaid' && team.every(f => f.hp === f.stats.hp))
            return false;
        if (d.id === 'mortgage' && team[0]?.hp > team[0]?.stats.hp * .65)
            return false;
        if (d.id === 'underdog' && !team.some(f => foes.some(e => e.level - f.level >= 10)))
            return false;
        if (d.id === 'rainbow' && !team.some(f => new Set([f.attack, ...f.skills].map(k => k.element)).size >= 3))
            return false;
        if (d.id === 'dual' && !team.some(f => new Set([f.attack, ...f.skills].map(k => k.element)).size >= 2))
            return false;
        if (d.id === 'coldbargain' && !owned(a, s, 'drain') && !owned(a, s, 'triage'))
            return false;
        return true;
    }).map(d => d.id);
}
export function currentApply(a, parties, s, id, turn) {
    const p = def(a, id).p, r = side(a, s), events = [], leader = parties[s][0];
    currentSync(a, parties);
    const acquiredDuration = ['bulwark', 'swift', 'thick', 'lunch', 'learn', 'resetline', 'neutral', 'rustle', 'glasswing', 'lonecharge', 'quietstorm', 'rainbow'];
    r.effects[id] = { ...(p.uses === undefined ? {} : { uses: p.uses }), ...(!acquiredDuration.includes(id) || p.turns === undefined ? {} : { until: turn + p.turns - 1 }) };
    if (id === 'first')
        r.first = true;
    if (id === 'firstaid')
        for (const f of parties[s])
            heal(a, parties, s, f, fp(f.stats.hp, p.pct), turn, id, events);
    if (id === 'bulwark')
        guard(leader, id, p.pct, turn + p.turns - 1);
    if (id === 'swift' || id === 'thick')
        for (const f of parties[s].filter(alive))
            mod(f, id, id === 'swift' ? 'speed' : 'durability', p.pct, turn + p.turns - 1);
    if (id === 'rustle') {
        // Deterministic modifier strength, not a new global status probability.
        const foe = parties[other(s)][0];
        mod(foe, id, 'speed', -p.pct / (1 + foe.stats.statusResistance / p.resistanceScale), turn + p.turns - 1);
    }
    if (id === 'rinse' || id === 'resetline') {
        for (const f of (id === 'rinse' ? [leader] : parties[s]).filter(alive)) {
            unit(f).mods = unit(f).mods.filter(m => m.pct >= 0);
            if (id === 'rinse')
                restoreMp(a, parties, s, f, p.mpPct, id, events);
            else
                mod(f, id, 'speed', p.pct, turn + p.turns - 1);
        }
    }
    if (id === 'strip') {
        const u = unit(parties[other(s)][0]);
        const rank = { durability: 1, speed: 2, statusResistance: 3 };
        const candidates = [...u.guards.map((g, i) => ({ kind: 'guard', i, pct: g.pct, until: g.until, rank: 0 })), ...u.mods.map((m, i) => ({ kind: 'mod', i, pct: m.pct, until: m.until, rank: rank[m.stat] })).filter(m => m.pct > 0)];
        candidates.sort((x, y) => y.pct - x.pct || x.rank - y.rank || y.until - x.until);
        const first = candidates[0];
        if (first) {
            if (first.kind === 'guard')
                u.guards.splice(first.i, 1);
            else
                u.mods.splice(first.i, 1);
        }
    }
    if (id === 'neutral')
        for (const t of [0, 1])
            side(a, t).neutralUntil = turn + p.turns - 1;
    if (id === 'hostileweather' && ![0, 1].some(t => side(a, t).weatherUsed)) {
        for (const t of [0, 1]) {
            side(a, t).weatherUsed = true;
            side(a, t).weatherUntil = turn + p.turns - 1;
        }
    }
    if (id === 'firstshell')
        for (const f of parties[s])
            unit(f).firstshell = true;
    if (id === 'learn')
        for (const f of parties[s])
            unit(f).hitBy = {};
    if (id === 'mortgage') {
        heal(a, parties, s, leader, fp(leader.stats.hp, p.healPct), turn, id, events);
        unit(leader).mortgage = { amount: Math.floor(leader.stats.hp * p.costPct / 100), due: turn + 1 };
    }
    if (id === 'glasswing' || id === 'lonecharge')
        unit(leader).risks[id] = { pct: p.takenPct ?? p.incomingPct, until: turn + p.turns - 1 };
    return events;
}
export function currentSpeed(a, f) { const s = unit(f).ownerSide; const last = s !== undefined && owned(a, s, 'lastone') && side(a, s).aliveCount === 1 ? param(a, 'lastone', 'pct', 20) : 0; return stat(f, 'speed', Number.NEGATIVE_INFINITY, true, last); }
export function currentOrder(a, base, parties, skills, turn) {
    currentSync(a, parties);
    const priorities = [0, 1].map(s => { if (!skills[s])
        return 0; let p = 0; if (side(a, s).first) {
        side(a, s).first = false;
        spend(a, s, 'first');
        p = param(a, 'first', 'priority', 1);
    } if (active(a, s, 'slowhammer', turn))
        p = param(a, 'slowhammer', 'priority', -3); return p; });
    if (!priorities.some(Boolean))
        return base;
    const ids = parties.map(p => p[0].id), rank = (id) => priorities[ids.indexOf(id)] ?? 0;
    return { ...base, order: [...base.order].sort((l, r) => rank(r) - rank(l)), augmentOrder: { version: 'roster60-current-priority-v1', priorities, baseOrder: [...base.order] } };
}
export function currentFocus(a, parties, s, turn) {
    const events = [];
    let changed = false;
    if (active(a, s, 'breach', turn) && !effect(a, s, 'breach').ready) {
        effect(a, s, 'breach').ready = true;
        changed = true;
    }
    if (active(a, s, 'quietstorm', turn)) {
        const e = effect(a, s, 'quietstorm');
        e.count = (e.count ?? 0) + 1;
        if (e.count >= param(a, 'quietstorm', 'count', 2))
            e.ready = true;
        changed = true;
    }
    if (effect(a, s, 'rainbow'))
        effect(a, s, 'rainbow').elements = [];
    if (changed)
        event(parties, events, s, parties[s][0], 0, 'focus-charge');
    return events;
}
export function currentItem(a, s) { if (effect(a, s, 'rainbow'))
    effect(a, s, 'rainbow').elements = []; }
/** Modern owned-effect overlay. Base coefficients, critical rules and turn rules stay untouched. */
export function currentAttack(a, parties, s, skill, policy, turn) {
    currentSync(a, parties);
    const t = other(s), actor = parties[s][0], target = parties[t][0], u = unit(actor), v = unit(target), events = [];
    const actorBefore = actor.hp, targetBefore = target.hp, baseDefense = target.defense[skill.element], special = baseDefense === 'immune' || baseDefense === 'reflect';
    const use = (id) => active(a, s, id, turn), defend = (id) => active(a, t, id, turn), p = (id, key, fallback = 0) => param(a, id, key, fallback);
    const sealed = use('sealedduel');
    if (sealed)
        spend(a, s, 'sealedduel');
    let powerPct = 0, piercePct = 0, effectiveLevel = actor.level, noHealing = sealed, ignoreShield = false;
    const consumed = [], add = (id, pct) => { powerPct += pct; consumed.push(id); };
    const pay = (id) => { const before = actor.hp; if (!cost(actor, p(id, 'costPct')))
        return false; event(parties, events, s, actor, before - actor.hp, id, 'augment-cost'); return true; };
    let ordinary = baseDefense === 'weak' ? 1.5 : baseDefense === 'resist' ? .5 : 1;
    if (!special) {
        if ([0, 1].some(x => (side(a, x).neutralUntil ?? -1) >= turn))
            ordinary = 1;
        else if ([0, 1].some(x => (side(a, x).weatherUntil ?? -1) >= turn))
            ordinary = ordinary > 1 ? p('hostileweather', 'weakMultiplier', 2.2) : ordinary < 1 ? p('hostileweather', 'resistMultiplier', .6) : 1;
    }
    if (!sealed) {
        for (const id of ['sharp', 'aim', 'overtime'])
            if (use(id))
                add(id, p(id, 'pct'));
        if (use('reverse') && actor.hp / actor.stats.hp < target.hp / target.stats.hp)
            add('reverse', p('reverse', 'pct'));
        if (use('dual')) {
            const e = effect(a, s, 'dual');
            if (!(e.elements ?? []).includes(skill.element)) {
                e.elements ??= [];
                e.elements.push(skill.element);
                add('dual', p('dual', 'pct'));
            }
        }
        if (use('peanut') && actor.name.includes('갈매미맨'))
            powerPct += p('peanut', 'pct');
        if (use('lastone') && parties[s].filter(alive).length === 1)
            powerPct += p('lastone', 'pct');
        if (use('pierce')) {
            piercePct += p('pierce', 'pct');
            consumed.push('pierce');
        }
        if (use('exploit') && v.mods.some(m => m.stat === 'speed' && m.pct < 0 && m.until >= turn)) {
            piercePct += p('exploit', 'pct');
            consumed.push('exploit');
        }
        if (use('response') && side(a, t).castTurn === turn)
            add('response', p('response', 'pct'));
        if (use('relaytype') && side(a, s).lastAttack !== undefined && side(a, s).lastAttack !== skill.element)
            add('relaytype', p('relaytype', 'pct'));
        if (use('chase') && v.switchedTurn === turn)
            add('chase', p('chase', 'pct'));
        if (u.incomingPower && u.incomingPower.until >= turn) {
            powerPct += u.incomingPower.pct;
            delete u.incomingPower;
        }
        if (use('breach') && effect(a, s, 'breach').ready) {
            ignoreShield = true;
            consumed.push('breach');
        }
        if (use('quietstorm') && effect(a, s, 'quietstorm').ready) {
            add('quietstorm', p('quietstorm', 'pct'));
            noHealing = true;
        }
        if (use('redline') && pay('redline')) {
            add('redline', p('redline', 'pct'));
            noHealing = true;
        }
        if (use('underdog') && target.level - actor.level >= p('underdog', 'levelGap', 10) && pay('underdog')) {
            consumed.push('underdog');
        }
        if (use('glasswing'))
            add('glasswing', p('glasswing', 'pct'));
        if (use('lonecharge')) {
            add('lonecharge', p('lonecharge', 'pct'));
            piercePct += p('lonecharge', 'piercePct');
        }
        if (use('equalfield')) {
            effectiveLevel = Math.min(99, actor.level + Math.min(p('equalfield', 'levelGap', 15), Math.max(0, target.level - actor.level)));
            if (!special)
                ordinary = p('equalfield', 'multiplier');
            consumed.push('equalfield');
        }
        if (use('blindspot') && !special && ordinary < 1 && pay('blindspot')) {
            ordinary = p('blindspot', 'multiplier', 2);
            consumed.push('blindspot');
        }
        if (use('typebridge') && !special && ordinary < 1) {
            ordinary = p('typebridge', 'multiplier', 1);
            consumed.push('typebridge');
        }
        if (use('doubleedge'))
            noHealing = true;
        if (use('lastmatch') && parties[s].filter(alive).length === 1 && actorBefore <= fp(actor.stats.hp, p('lastmatch', 'thresholdPct', 30)) && pay('lastmatch')) {
            add('lastmatch', p('lastmatch', 'pct'));
            noHealing = true;
        }
        if (use('rainbow')) {
            const e = effect(a, s, 'rainbow');
            e.elements ??= [];
            if (e.elements.includes(skill.element))
                e.elements = [skill.element];
            else
                e.elements.push(skill.element);
            if (e.elements.length >= p('rainbow', 'distinctCount', 3))
                add('rainbow', p('rainbow', 'pct'));
        }
        if (use('coldbargain') && (owned(a, s, 'drain') || owned(a, s, 'triage'))) {
            add('coldbargain', p('coldbargain', 'pct'));
            noHealing = true;
        }
        if (use('shatterprice') && (u.guards.some(g => g.until >= turn) || u.mods.some(m => m.pct > 0 && m.until >= turn) || Object.values(u.shields).some(v => v.amount > 0 && v.until >= turn))) {
            u.guards = [];
            u.shields = {};
            u.mods = u.mods.filter(m => m.pct <= 0);
            piercePct += p('shatterprice', 'pct');
            consumed.push('shatterprice');
        }
        if (use('slowhammer'))
            add('slowhammer', p('slowhammer', 'pct'));
        if (use('siegebreak') && target.hp >= fp(target.stats.hp, p('siegebreak', 'thresholdPct', 80))) {
            add('siegebreak', p('siegebreak', 'pct'));
            u.risks.siegebreak = { pct: p('siegebreak', 'incomingPct', 30), until: turn + p('siegebreak', 'turns', 2) - 1 };
        }
        if (use('sharedburden')) {
            const bench = parties[s].slice(1).filter(alive);
            if (bench.length && bench.every(f => f.hp > Math.floor(f.stats.hp * p('sharedburden', 'costPct', 10) / 100))) {
                for (const f of bench) {
                    const amount = Math.floor(f.stats.hp * p('sharedburden', 'costPct', 10) / 100);
                    hurt(f, amount);
                    event(parties, events, s, f, amount, 'sharedburden', 'augment-cost');
                }
                add('sharedburden', p('sharedburden', 'pct'));
            }
        }
        if (!special && ordinary > 1 && owned(a, s, 'focus'))
            ordinary = p('focus', 'multiplier', 1.9);
    }
    if (!sealed && !special && ordinary > 1 && defend('weakguard'))
        ordinary = 1;
    const recipient = baseDefense === 'reflect' ? actor : target, recipientSide = baseDefense === 'reflect' ? s : t, recipientUnit = unit(recipient);
    const recipientEnabled = !sealed || recipient === actor;
    let keep = recipientEnabled ? guardKeep(recipient, turn) : 1;
    if (recipientEnabled && recipientUnit.firstshell && active(a, recipientSide, 'firstshell', turn)) {
        keep *= 1 - param(a, 'firstshell', 'pct', 30) / 100;
        recipientUnit.firstshell = false;
    }
    if (recipientEnabled && active(a, recipientSide, 'learn', turn)) {
        keep *= 1 - Math.min(param(a, 'learn', 'capPct', 16), (recipientUnit.hitBy[skill.element] ?? 0) * param(a, 'learn', 'pct', 8)) / 100;
    }
    if (recipientEnabled && active(a, recipientSide, 'camo', turn) && recipientUnit.lastReceived === skill.element) {
        keep *= 1 - param(a, 'camo', 'pct', 25) / 100;
        spend(a, recipientSide, 'camo');
    }
    const cracked = recipientEnabled && active(a, recipientSide, 'crackedarmor', turn);
    if (cracked) {
        keep *= 1 - param(a, 'crackedarmor', 'pct', 60) / 100;
        spend(a, recipientSide, 'crackedarmor');
    }
    let risk = Object.values(recipientUnit.risks).filter(v => v.until >= turn).reduce((sum, v) => sum + v.pct, 0);
    if (active(a, recipientSide, 'peanut', turn) && recipient.name.includes('갈매미맨'))
        risk += param(a, 'peanut', 'incomingPct', 15);
    const lastDurability = !sealed && defend('lastone') && parties[t].filter(alive).length === 1 ? p('lastone', 'pct', 20) : 0;
    const effectiveDurability = Math.max(0, stat(target, 'durability', turn, !sealed, lastDurability) * (1 - clamp(piercePct, 0, 70) / 100));
    const ordinaryChanged = !special && ordinary !== (baseDefense === 'weak' ? 1.5 : baseDefense === 'resist' ? .5 : 1);
    const modifiedPower = powerPct > 0 ? Math.round(skill.power * (1 + clamp(powerPct, 0, 100) / 100)) : skill.power;
    const guardedPower = modifiedPower * Math.max(.4, keep) * (ordinaryChanged ? ordinary : 1);
    // Add the owned percentage after multiplication: 100 + 100*15/100 is exactly
    // 115, whereas 100*(1+.15) can be 114.999... before the core's single floor.
    const finalPower = guardedPower + guardedPower * clamp(risk, 0, 100) / 100;
    const hit = { ...fiveStatAttack({ power: finalPower, level: effectiveLevel, durability: effectiveDurability, suitability: actor.suitability[skill.element], defense: ordinaryChanged ? 'normal' : baseDefense, attackerHp: Number.MAX_SAFE_INTEGER, defenderHp: Number.MAX_SAFE_INTEGER }, policy, (b, f) => b * f), ...(ordinaryChanged ? { augmentTypeMultiplier: ordinary } : {}) };
    if (ordinaryChanged)
        hit.defense = ordinary > 1 ? 'weak' : ordinary < 1 ? 'resist' : 'normal';
    const bypassShield = (ignoreShield && recipient === target) || !recipientEnabled;
    const actual = hurt(recipient, bypassShield ? hit.damage : absorb(recipient, hit.damage, turn));
    hit.damage = actual;
    if (cracked)
        recipientUnit.risks.crackedarmor = { pct: param(a, 'crackedarmor', 'incomingPct', 25), until: turn + param(a, 'crackedarmor', 'turns', 2) - 1 };
    if (!alive(recipient))
        recipientUnit.shields = {};
    if (actual > 0) {
        recipientUnit.hitBy[skill.element] = (recipientUnit.hitBy[skill.element] ?? 0) + 1;
        recipientUnit.lastReceived = skill.element;
    }
    for (const id of new Set(consumed))
        spend(a, s, id);
    side(a, s).castTurn = turn;
    side(a, s).lastAttack = skill.element;
    if (!sealed && actual > 0 && recipient === target) {
        let extraBudget = fp(target.stats.hp, 18), healingBudget = fp(actor.stats.hp, 15);
        const extra = (id, n) => { const amount = hurt(target, Math.min(extraBudget, n)); extraBudget -= amount; if (amount)
            event(parties, events, s, target, amount, id, 'augment-damage'); return amount; };
        if (consumed.includes('underdog'))
            extra('underdog', fp(actual, p('underdog', 'pct', 35)));
        if (use('doubleedge')) {
            const amount = extra('doubleedge', fp(actual, p('doubleedge', 'pct', 30)));
            const recoil = hurt(actor, fp(amount, p('doubleedge', 'recoilPct', 50)));
            spend(a, s, 'doubleedge');
            if (recoil)
                event(parties, events, s, actor, recoil, 'doubleedge', 'augment-damage');
        }
        const healing = (id, n) => { const amount = Math.min(n, healingBudget); healingBudget -= amount; return heal(a, parties, s, actor, amount, turn, id, events); };
        if (!noHealing && alive(actor) && use('drain')) {
            healing('drain', Math.min(fp(actual, p('drain', 'pct', 12)), fp(actor.stats.hp, p('drain', 'capPct', 4))));
            spend(a, s, 'drain');
        }
        if (!noHealing && alive(actor) && use('triage') && actorBefore <= fp(actor.stats.hp, p('triage', 'thresholdPct', 50))) {
            healing('triage', Math.min(fp(actual, p('triage', 'pct', 25)), fp(actor.stats.hp, p('triage', 'capPct', 10))));
            spend(a, s, 'triage');
        }
        if (alive(target) && defend('counter') && hit.defense === 'weak') {
            const amount = hurt(actor, Math.min(fp(actual, p('counter', 'pct', 10)), fp(actor.stats.hp, p('counter', 'capPct', 4))));
            spend(a, t, 'counter');
            if (amount)
                event(parties, events, t, actor, amount, 'counter', 'augment-damage');
        }
        if (defend('thorns')) {
            const amount = hurt(actor, Math.min(fp(actual, p('thorns', 'pct', 10)), fp(actor.stats.hp, p('thorns', 'capPct', 4))));
            spend(a, t, 'thorns');
            if (amount)
                event(parties, events, t, actor, amount, 'thorns', 'augment-damage');
        }
        if (alive(target) && defend('rescue') && targetBefore > fp(target.stats.hp, p('rescue', 'thresholdPct', 30)) && target.hp <= fp(target.stats.hp, p('rescue', 'thresholdPct', 30))) {
            v.mods = v.mods.filter(m => m.pct >= 0);
            spend(a, t, 'rescue');
            shield(a, parties, t, target, fp(target.stats.hp, p('rescue', 'pct', 15)), turn + p('rescue', 'turns', 2) - 1, 'rescue', events);
        }
    }
    currentSync(a, parties);
    return { hit, events };
}
export function currentSwitch(a, parties, s, out, incoming, voluntary, turn) {
    currentSync(a, parties);
    const events = [], ready = (id) => active(a, s, id, turn), p = (id, k, n = 0) => param(a, id, k, n);
    if (voluntary) {
        unit(out).switchedTurn = turn;
        unit(incoming).switchedTurn = turn;
        if (ready('handoffguard')) {
            guard(incoming, 'handoffguard', p('handoffguard', 'pct', 12), turn);
            spend(a, s, 'handoffguard');
        }
        if (ready('relief') && incoming.hp < incoming.stats.hp) {
            heal(a, parties, s, incoming, fp(incoming.stats.hp, p('relief', 'pct', 8)), turn, 'relief', events);
            spend(a, s, 'relief');
        }
        if (ready('bloodrelay') && cost(out, p('bloodrelay', 'costPct', 20))) {
            unit(incoming).incomingPower = { pct: p('bloodrelay', 'pct', 60), until: turn + 1 };
            spend(a, s, 'bloodrelay');
        }
    }
    else {
        unit(incoming).forcedTurn = turn;
        if (ready('baton')) {
            guard(incoming, 'baton', p('baton', 'pct', 35), turn);
            spend(a, s, 'baton');
        }
    }
    if (effect(a, s, 'rainbow'))
        effect(a, s, 'rainbow').elements = [];
    return events;
}
export function currentHeal(a, parties, s, f, amount, turn) { if (effect(a, s, 'rainbow'))
    effect(a, s, 'rainbow').elements = []; return heal(a, parties, s, f, amount, turn, 'item', []); }
export function currentEndTurn(a, parties, turn) {
    const events = [];
    for (const s of [0, 1]) {
        if (active(a, s, 'lunch', turn))
            heal(a, parties, s, parties[s][0], fp(parties[s][0].stats.hp, param(a, 'lunch', 'pct', 3)), turn, 'lunch', events);
        for (const f of parties[s]) {
            const u = unit(f);
            if (u.mortgage && u.mortgage.due <= turn) {
                const n = hurt(f, u.mortgage.amount);
                event(parties, events, s, f, n, 'mortgage', 'augment-cost');
                delete u.mortgage;
            }
            u.mods = u.mods.filter(m => m.until > turn);
            u.guards = u.guards.filter(g => g.until > turn);
            for (const [k, v] of Object.entries(u.shields))
                if (v.until <= turn)
                    delete u.shields[k];
            for (const [k, v] of Object.entries(u.risks))
                if (v.until <= turn)
                    delete u.risks[k];
        }
        if (parties[s][0].hp <= 0 && active(a, s, 'slowhammer', turn))
            spend(a, s, 'slowhammer');
    }
    currentSync(a, parties);
    return events;
}
