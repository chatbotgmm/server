import { assertKnownAugmentModel } from './roster60-current-augment-gate.js';
import { CURRENT_AUGMENT_VERSION, roster60CurrentCatalog } from './battle/roster60-current-augment-catalog.js';
import { currentSync, currentEligible, currentApply, currentSpeed, currentOrder, currentAttack, currentSwitch, currentHeal, currentEndTurn, currentFocus, currentItem } from './battle/roster60-current-augment-runtime.js';
import { createHash } from 'node:crypto';
import { RosterError } from './roster60-onboarding.js';
import { fiveStatAttack } from './battle/roster60-five-combat.js';
import { augmentCatalogV2 } from './battle/augment-catalog-v2.js';
import { balancedSixProfile, createAugmentSequence } from './battle/augment-sequence.js';
import { createOfferRound, rerollOffer } from './battle/augment-offers.js';
import { applyNewAugment, beginAugmentCast, finishAugmentCast, finishAugmentDefense, augmentOverflow, absorbAugmentShield, onAugmentSwitch, endAugmentTurn, augmentPriority, augmentWeatherMultiplier } from './battle/augment-effects.js';
export const rosterAugmentSupportedIds = Object.freeze('focus weakguard neutral typebridge blindspot hostileweather equalfield first slowhammer rustle rinse resetline rescue breach quietstorm freeswitch bulwark firstaid swift overtime drain thorns lunch learn peanut strip handoffguard relief response relaytype triage firstshell chase baton overflowguard redline underdog glasswing doubleedge lastmatch bloodrelay rainbow mortgage coldbargain siegebreak sharedburden sealedduel'.split(' '));
const supported = new Set(rosterAugmentSupportedIds);
export const rosterAugmentUnmappedIds = Object.freeze(augmentCatalogV2.augments.filter(a => !supported.has(a.id)).map(a => a.id));
const fail = (code, status = 409) => { throw new RosterError(status, code); };
export function validateRosterAugmentPolicy(value) {
    if (value === undefined)
        return;
    if (value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).join() === 'version' && value.version === 3) {
        if (roster60CurrentCatalog.augments.length !== 60 || new Set(roster60CurrentCatalog.augments.map(a => a.id)).size !== 60)
            fail('ROSTER60_AUGMENT_POLICY_INVALID', 503);
        return;
    }
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).join() !== 'version' || value.version !== 2)
        fail('ROSTER60_AUGMENT_POLICY_INVALID', 503);
    // Never silently filter the sixty-card catalog or reweight an unsupported tier.
    if (rosterAugmentUnmappedIds.length)
        fail('ROSTER60_AUGMENT_MAPPING_REQUIRED', 503);
}
const key = (f) => f.ownedId ?? f.id;
function fs(f) { return f.augmentEffects ??= (({ extra: {}, mods: [], guards: [] })); }
const utilitySkills = (f) => [f.attack, ...f.skills].filter(k => !k.power && Array.isArray(k.effects) && k.effects.length > 0);
const canonical = (v) => JSON.stringify(v);
function checkedCatalog(catalog) {
    if (catalog.version !== 2 || !catalog.augments.length || new Set(catalog.augments.map(a => a.id)).size !== catalog.augments.length)
        fail('ROSTER60_AUGMENT_POLICY_INVALID', 503);
    for (const a of catalog.augments) {
        const original = augmentCatalogV2.augments.find(x => x.id === a.id);
        if (!original || canonical(original) !== canonical(a))
            fail('ROSTER60_AUGMENT_CATALOG_CHANGED', 503);
        if (!supported.has(a.id))
            fail('ROSTER60_AUGMENT_MAPPING_REQUIRED', 503);
    }
}
function random(a) { let t = (a.rng = (a.rng + 0x6d2b79f5) | 0); t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }
const definition = (a, id) => a.catalog.find(x => x.id === id) ?? fail('AUGMENT_NOT_OFFERED');
const owned = (a, s, id) => a.owned[s].includes(id);
const legacyActive = (a, s, id, turn) => owned(a, s, id) && (!a.legacy[s][id] || ((a.legacy[s][id].uses === undefined || a.legacy[s][id].uses > 0) && (a.legacy[s][id].until === undefined || turn + 1 <= a.legacy[s][id].until)));
function spend(a, s, id) { const v = a.legacy[s][id]; if (v?.uses !== undefined && v.uses > 0)
    v.uses--; }
/** Pure fixture callers may supply exact original entries. Production callers use the full catalog only. */
export function createRosterAugments(parties, seed, turn, catalog = augmentCatalogV2) {
    checkedCatalog(catalog);
    if (!seed || !Number.isSafeInteger(turn) || turn < 0 || parties.some(p => !p.length))
        fail('ROSTER60_AUGMENT_STATE_INVALID');
    const a = { version: 'roster60-augment-v2', rng: createHash('sha256').update(seed).digest().readInt32LE(), sequence: [], roundIndex: 0, phase: 'action', phaseId: '', selected: [null, null], owned: [[], []], runtime: [{}, {}], legacy: [{}, {}], catalog: structuredClone(catalog.augments) };
    a.sequence = createAugmentSequence(balancedSixProfile, () => random(a));
    offerRosterAugments(a, parties, turn);
    return a;
}
export function createCurrentRosterAugments(parties, seed, turn) {
    if (!seed || !Number.isSafeInteger(turn) || turn < 0 || parties.some(p => !p.length))
        fail('ROSTER60_AUGMENT_STATE_INVALID');
    const a = { version: CURRENT_AUGMENT_VERSION, rng: createHash('sha256').update(seed).digest().readInt32LE(), sequence: [], roundIndex: 0, phase: 'action', phaseId: '', selected: [null, null], owned: [[], []], runtime: [{}, {}], legacy: [{}, {}], catalog: structuredClone(roster60CurrentCatalog.augments) };
    a.sequence = createAugmentSequence(balancedSixProfile, () => random(a));
    currentSync(a, parties);
    offerRosterAugments(a, parties, turn);
    return a;
}
export function createRosterAugmentsForPolicy(parties, seed, turn, policy) { validateRosterAugmentPolicy(policy); return policy.version === 3 ? createCurrentRosterAugments(parties, seed, turn) : createRosterAugments(parties, seed, turn); }
function eligible(a, parties, s) {
    assertKnownAugmentModel(a);
    if (a.version === CURRENT_AUGMENT_VERSION)
        return currentEligible(a, parties, s);
    const team = parties[s].filter(f => f.hp > 0), foes = parties[1 - s].filter(f => f.hp > 0), leaders = parties[s][0].hp > 0 ? [parties[s][0]] : team;
    return a.catalog.filter(v => { switch (v.id) {
        case 'rinse': return leaders.some(f => Object.values(f.augmentEffects?.status ?? {}).some(n => typeof n === 'number' && n > 0));
        case 'resetline': return team.some(f => (f.augmentEffects?.mods ?? []).some(m => m.pct < 0 && m.turns > 0));
        case 'breach': return team.some(f => utilitySkills(f).length > 0);
        case 'quietstorm': return team.some(f => new Set(utilitySkills(f).map(k => k.id)).size >= 2);
        case 'firstaid': return team.some(f => f.hp < f.stats.hp);
        case 'handoffguard':
        case 'relief':
        case 'bloodrelay':
        case 'baton': return team.length >= 2;
        case 'equalfield': return team.some(f => foes.some(e => e.level > f.level));
        case 'underdog': return team.some(f => foes.some(e => e.level - f.level >= 10));
        case 'rainbow': return team.some(f => new Set([f.attack, ...f.skills].filter(k => k.power > 0).map(k => k.element)).size >= 3);
        case 'mortgage': return leaders.some(f => f.hp <= f.stats.hp * .65);
        case 'coldbargain': return owned(a, s, 'drain') || owned(a, s, 'triage');
        case 'sharedburden': return leaders.some(f => { const bench = team.filter(x => x !== f); return bench.length > 0 && bench.every(x => x.hp > x.stats.hp * .1); });
        default: return true;
    } }).map(x => x.id);
}
export function offerRosterAugments(a, parties, turn) {
    assertKnownAugmentModel(a);
    if (a.phase === 'choose')
        fail('AUGMENT_SELECTION_REQUIRED');
    const tier = a.sequence[a.roundIndex];
    if (!tier) {
        delete a.round;
        return;
    }
    const result = createOfferRound(a.catalog, a.owned, [eligible(a, parties, 0), eligible(a, parties, 1)], { silver: tier === 'silver' ? 1 : 0, gold: tier === 'gold' ? 1 : 0, prismatic: tier === 'prismatic' ? 1 : 0 }, () => random(a), (a.roundIndex % 2));
    a.roundIndex++;
    a.selected = [null, null];
    a.phaseId = `${a.version}:${a.roundIndex}:${turn}`;
    if (result.kind === 'exhausted') {
        delete a.round;
        a.phase = 'action';
        return;
    }
    a.round = result.round;
    a.phase = 'choose';
}
export function publicRosterAugments(a, side) { assertKnownAugmentModel(a); if (!a)
    return undefined; return { version: a.version, ...(a.version === CURRENT_AUGMENT_VERSION ? { modelVersion: CURRENT_AUGMENT_VERSION } : {}), phase: a.phase, phaseId: a.phaseId, round: a.roundIndex, tier: a.round?.tier, offers: a.phase === 'choose' && a.selected[side] === null ? a.round.offers[side].map(id => structuredClone(definition(a, id))) : [], rerolled: a.round ? [...a.round.rerolled[side]] : [], selected: a.selected[side], waitingForOpponent: a.phase === 'choose' && a.selected[side] !== null, owned: [...a.owned[side]], ...(a.version === CURRENT_AUGMENT_VERSION ? { ownedDefinitions: a.owned[side].map(id => structuredClone(definition(a, id))) } : {}), opponentOwned: [...a.owned[1 - side]] }; }
export function rerollRosterAugment(a, parties, side, phaseId, slot) {
    assertKnownAugmentModel(a);
    if (a.phase !== 'choose' || phaseId !== a.phaseId || a.selected[side] !== null || !a.round)
        fail('AUGMENT_PHASE_MISMATCH');
    try {
        const r = rerollOffer(a.round, side, slot, a.catalog, a.owned, eligible(a, parties, side), () => random(a));
        if (r.kind === 'exhausted')
            fail('AUGMENT_POOL_EXHAUSTED');
    }
    catch (e) {
        if (e instanceof RosterError)
            throw e;
        fail('AUGMENT_REROLL_UNAVAILABLE');
    }
}
function facade(a, parties, turn, actorSide) {
    const map = new Map(), back = new Map();
    const convert = (f, side) => {
        const st = fs(f);
        const next = { side, rec: { level: f.level, uid: key(f), id: f.speciesId }, name: f.name, maxHp: f.stats.hp, passive: {}, protect: false, skills: [f.attack, ...f.skills].map(k => ({ id: k.id, name: k.name, power: k.power, attr: k.element, effects: Array.isArray(k.effects) ? k.effects : [] })) };
        Object.defineProperties(next, { immune: { get: () => st.statusImmunityTurns ?? 0, set: (v) => { st.statusImmunityTurns = v; }, enumerable: true }, status: { get: () => st.status ??= {}, set: (v) => { st.status = v; }, enumerable: true }, hp: { get: () => f.hp, set: (v) => { f.hp = v; }, enumerable: true }, augment: { get: () => st.extra, set: (v) => { st.extra = v; }, enumerable: true }, mods: { get: () => st.mods, set: (v) => { st.mods = v; }, enumerable: true }, guards: { get: () => st.guards, set: (v) => { st.guards = v; }, enumerable: true } });
        map.set(f, next);
        back.set(next, f);
        return next;
    };
    const b = { turn: turn + 1, R: { augmentVersion: 2, battleAug: new Map(a.catalog.map(x => [x.id, x])) }, augmentOffers: { version: 2 }, sides: parties.map((party, s) => ({ fighters: party.map(f => convert(f, s)), active: 0, augs: a.owned[s], augment: a.runtime[s], legacyAug: a.legacy[s], get neutral() { return a.runtime[s].neutral ?? 0; }, set neutral(v) { a.runtime[s].neutral = v; } })) };
    const events = [];
    const targetRef = (f) => ({ side: actorSide ?? f.side, targetSide: f.side, targetSlot: parties[f.side].indexOf(back.get(f)) });
    const io = { heal: (f, amount, reason) => { if (f.hp <= 0 || amount <= 0 || b.augmentCast?.disableHealing || (b.augmentCast?.disableAugmentHealing && ['drain', 'triage'].includes(reason)))
            return; if (b.augmentCast && reason === 'triage') {
            amount = Math.min(amount, Math.max(0, Math.floor(f.maxHp * .15) - (b.augmentCast.newHealRequested ?? 0)));
            b.augmentCast.newHealRequested = (b.augmentCast.newHealRequested ?? 0) + amount;
        } const before = f.hp; f.hp = Math.min(f.maxHp, f.hp + Math.max(0, Math.round(amount))); augmentOverflow(b, f, Math.max(0, Math.round(amount)), f.hp - before); if (f.hp !== before)
            events.push({ kind: 'augment-heal', ...targetRef(f), amount: f.hp - before, reason }); }, hurt: (f, amount, reason) => { const before = f.hp; f.hp = Math.max(0, f.hp - Math.max(0, amount)); if (f.hp === 0 && f.augment) {
            delete f.augment.shield;
            delete f.augment.shieldUntil;
        } events.push({ kind: 'augment-damage', ...targetRef(f), amount: before - f.hp, reason }); return before - f.hp; }, status: (f, reason) => { events.push({ kind: 'augment-status', ...targetRef(f), amount: 0, reason }); } };
    return { b, map, io, events };
}
/** Pure owned-effect hook, also used by selection. It neither grants ownership nor bypasses offered-ID checks. */
export function applyRosterAugmentEffect(a, parties, side, id, turn) {
    assertKnownAugmentModel(a);
    if (!owned(a, side, id))
        fail('AUGMENT_NOT_OWNED');
    if (a.version === CURRENT_AUGMENT_VERSION) {
        currentApply(a, parties, side, id, turn);
        return;
    }
    if (!supported.has(id))
        fail('ROSTER60_AUGMENT_MAPPING_REQUIRED', 503);
    const p = definition(a, id).p, { b, io } = facade(a, parties, turn, side), me = b.sides[side].fighters[0];
    if (applyNewAugment(b, side, id, io))
        return;
    if (p.uses !== undefined || id === 'lunch' || id === 'learn')
        a.legacy[side][id] = { ...(p.uses !== undefined ? { uses: p.uses } : {}), ...(p.turns ? { until: b.turn + p.turns - 1 } : {}) };
    if (id === 'neutral')
        b.sides[0].neutral = b.sides[1].neutral = p.turns;
    if (id === 'first')
        a.runtime[side].goFirst = true;
    if (id === 'rustle') {
        const foe = b.sides[1 - side].fighters[0];
        if (!foe.immune) {
            foe.status.sleep = 1;
            io.status(foe, id);
        }
    }
    if (id === 'bulwark')
        fs(parties[side][0]).guards.push({ pct: p.pct, turns: p.turns, augment: true });
    if (id === 'firstaid')
        for (const f of b.sides[side].fighters)
            if (f.hp > 0)
                io.heal(f, Math.round(f.maxHp * p.pct / 100), id);
    if (id === 'learn')
        for (const f of parties[side])
            fs(f).extra.hitBy = {};
    if (id === 'strip') {
        const target = fs(parties[1 - side][0]), effects = [...target.guards.map((value, index) => ({ value, index, guard: true, rank: 0 })), ...target.mods.map((value, index) => ({ value, index, guard: false, rank: 3 })).filter(x => x.value.pct > 0)];
        effects.sort((x, y) => y.value.pct - x.value.pct || x.rank - y.rank || y.value.turns - x.value.turns);
        const top = effects[0];
        if (top) {
            if (top.guard)
                target.guards.splice(top.index, 1);
            else
                target.mods.splice(top.index, 1);
        }
    }
    if (id === 'swift')
        for (const f of parties[side].filter(f => f.hp > 0)) {
            const st = fs(f), best = Math.max(p.pct, ...st.mods.filter(m => m.pct > 0).map(m => m.pct));
            if (best === p.pct) {
                st.mods = st.mods.filter(m => m.pct <= 0);
                st.mods.push({ stat: 'spd', pct: p.pct, turns: p.turns, augment: true });
            }
        }
}
export function chooseRosterAugment(a, parties, side, phaseId, id, turn) {
    assertKnownAugmentModel(a);
    if (a.phase !== 'choose' || phaseId !== a.phaseId || !a.round)
        fail('AUGMENT_PHASE_MISMATCH');
    if (a.selected[side] !== null) {
        if (a.selected[side] === id)
            return;
        fail('AUGMENT_ALREADY_SELECTED');
    }
    if (!a.round.offers[side].includes(id))
        fail('AUGMENT_NOT_OFFERED');
    if (a.version !== CURRENT_AUGMENT_VERSION && !supported.has(id))
        fail('ROSTER60_AUGMENT_MAPPING_REQUIRED', 503);
    if (parties[side][0].hp <= 0)
        fail('FORCED_SWITCH_REQUIRED');
    a.selected[side] = id;
    if (a.selected.every(x => x !== null)) {
        for (const s of [0, 1]) {
            const pick = a.selected[s];
            a.owned[s].push(pick);
            applyRosterAugmentEffect(a, parties, s, pick, turn);
        }
        a.phase = 'action';
    }
}
/** Exact neutral-match legacy preference; no removed creature-attribute heuristic is fabricated. */
export function automaticRosterAugment(a, parties, side, turn) { assertKnownAugmentModel(a); if (a.phase !== 'choose' || a.selected[side] !== null || parties[side][0].hp <= 0)
    return; const offers = a.round.offers[side], prefer = ['focus', 'dual', 'overtime', 'pierce', 'first', 'peanut', 'strip', 'sharp', 'bulwark']; chooseRosterAugment(a, parties, side, a.phaseId, prefer.find(id => offers.includes(id)) ?? offers[0], turn); }
/** The legacy authenticated human contract requires an explicit incoming skill, never a new AI fallback. */
export function rosterFreeSwitchFollowup(a, side, party, targetOwnedId, then) {
    assertKnownAugmentModel(a);
    const incoming = party.slice(1).find(f => f.ownedId === targetOwnedId && f.hp > 0);
    if (!incoming)
        fail('INVALID_SWITCH');
    if (party[0].hp <= 0) {
        if (then !== undefined)
            fail('FORCED_SWITCH_FOLLOWUP');
        return undefined;
    }
    if (!a || !owned(a, side, 'freeswitch')) {
        if (then !== undefined)
            fail('AUGMENT_FREE_SWITCH_REQUIRED');
        return undefined;
    }
    if (then === undefined)
        fail('AUGMENT_FOLLOWUP_REQUIRED');
    const skill = [incoming.attack, ...incoming.skills].find(k => k.id === then);
    if (!skill)
        fail('SKILL_NOT_EQUIPPED');
    if (skill.mpCost > incoming.mp)
        fail('INSUFFICIENT_MP');
    return skill;
}
export function rosterAugmentSleeping(a, f) { assertKnownAugmentModel(a); return !!a && a.version !== CURRENT_AUGMENT_VERSION && (fs(f).status?.sleep ?? 0) > 0; }
/** Versioned owned exceptions layer. The base speed/tie decision and its seed remain unchanged. */
export function rosterAugmentOrder(a, base, parties, skills, turn) {
    assertKnownAugmentModel(a);
    if (!a)
        return base;
    if (a.version === CURRENT_AUGMENT_VERSION)
        return currentOrder(a, base, parties, skills, turn);
    const { b, map } = facade(a, parties, turn);
    let present = false;
    const priorities = [0, 1].map(side => {
        const skill = skills[side];
        if (!skill)
            return 0;
        const first = !!a.runtime[side].goFirst;
        if (first) {
            present = true;
            a.runtime[side].goFirst = false;
            spend(a, side, 'first');
        }
        const slow = augmentPriority(b, map.get(parties[side][0]), { id: skill.id, name: skill.name, power: skill.power, effects: [], cd: 0, desc: '' });
        if (slow !== undefined)
            present = true;
        return slow ?? (first ? definition(a, 'first').p.priority : 0);
    });
    if (!present)
        return base;
    const ids = parties.map(p => p[0].id), rank = (id) => { const index = ids.indexOf(id); return index < 0 ? 0 : priorities[index]; };
    const ordered = [...base.order].sort((left, right) => rank(right) - rank(left));
    return { ...base, order: ordered, augmentOrder: { version: 'legacy-augment-priority-v2', priorities, baseOrder: [...base.order] } };
}
export function rosterAugmentSpeed(a, f) { assertKnownAugmentModel(a); return !a ? f.stats.speed : a.version === CURRENT_AUGMENT_VERSION ? currentSpeed(a, f) : fs(f).mods.reduce((v, m) => m.turns > 0 ? v * (1 + m.pct / 100) : v, f.stats.speed); }
export function rosterAugmentAttack(a, parties, side, skill, policy, turn) {
    assertKnownAugmentModel(a);
    if (a?.version === CURRENT_AUGMENT_VERSION) {
        if (a.phase !== 'action')
            fail('AUGMENT_SELECTION_REQUIRED');
        return currentAttack(a, parties, side, skill, policy, turn);
    }
    const actor = parties[side][0], target = parties[1 - side][0];
    const originalDefense = target.defense[skill.element];
    let effectiveDefense = originalDefense, effectiveLevel = actor.level;
    const base = (power, uncapped = false) => fiveStatAttack({ power, level: effectiveLevel, durability: target.stats.durability, suitability: actor.suitability[skill.element], defense: effectiveDefense, attackerHp: uncapped ? Number.MAX_SAFE_INTEGER : actor.hp, defenderHp: uncapped ? Number.MAX_SAFE_INTEGER : target.hp }, policy, (damage, fit) => damage * fit);
    if (!a) {
        const hit = base(skill.power);
        if (hit.target === 'attacker')
            actor.hp -= hit.damage;
        else
            target.hp -= hit.damage;
        return { hit, events: [] };
    }
    if (a.phase !== 'action')
        fail('AUGMENT_SELECTION_REQUIRED');
    // Reflected augmented damage still needs an explicit cross-model contract. Immune remains zero.
    if (skill.power > 0 && target.defense[skill.element] === 'reflect')
        fail('ROSTER60_AUGMENT_REFLECTION_MAPPING_REQUIRED', 503);
    const { b, map, io, events } = facade(a, parties, turn, side), user = map.get(actor), foe = map.get(target);
    const neutral = b.sides.some(s => s.neutral > 0), weather = b.sides.some(s => (s.augment?.hostileweatherUntil ?? 0) >= b.turn);
    const move = { id: skill.id, name: skill.name, power: skill.power, attr: skill.element, effects: [], cd: 0, desc: '' };
    const remainingType = ['typebridge', 'blindspot', 'equalfield'].some(id => owned(a, side, id) && (a.runtime[side].uses?.[id] ?? 0) > 0);
    let changesImmunity = false;
    if (skill.power > 0 && originalDefense === 'immune' && remainingType && !neutral && !weather) {
        // Reuse the exact cast ordering on an isolated copy: sealing and earlier HP
        // costs may suppress an otherwise owned override. Preview never spends real state.
        const copy = structuredClone({ a, parties }), preview = facade(copy.a, copy.parties, turn, side);
        changesImmunity = beginAugmentCast(preview.b, preview.map.get(copy.parties[side][0]), preview.map.get(copy.parties[1 - side][0]), move, move.attr, 0, preview.io).typeMultiplier !== undefined;
    }
    // No implicit conversion of modern immunity/reflection into the old strong/weak system.
    if (skill.power > 0 && originalDefense === 'immune' && (neutral || weather || changesImmunity))
        fail('ROSTER60_AUGMENT_ELEMENT_MAPPING_REQUIRED', 503);
    const baseType = originalDefense === 'weak' ? 1.5 : originalDefense === 'resist' ? .5 : originalDefense === 'immune' ? 0 : 1;
    const rawType = augmentWeatherMultiplier(b, baseType);
    const cast = beginAugmentCast(b, user, foe, move, skill.element, rawType, io);
    b.augmentCast = cast;
    if (!cast.direct)
        return { hit: base(0), events };
    let typeMultiplier = cast.typeMultiplier ?? rawType;
    if (!cast.sealed && typeMultiplier > 1 && owned(a, side, 'focus'))
        typeMultiplier = definition(a, 'focus').p.value;
    if (!cast.ignoreAugmentDefense && typeMultiplier > 1 && owned(a, (1 - side), 'weakguard'))
        typeMultiplier = 1;
    const typeChanged = typeMultiplier !== baseType;
    if (typeChanged)
        effectiveDefense = 'normal';
    effectiveLevel += cast.attackLevelBonus;
    const bonus = Math.min(100, cast.powerPct + (!cast.sealed && legacyActive(a, side, 'overtime', turn) ? definition(a, 'overtime').p.pct : 0));
    let keep = cast.ignoreGuards || cast.ignoreAugmentDefense ? 1 : fs(target).guards.reduce((v, g) => g.turns > 0 ? v * (1 - g.pct / 100) : v, 1);
    if (!cast.ignoreAugmentDefense && legacyActive(a, (1 - side), 'learn', turn)) {
        const p = definition(a, 'learn').p;
        keep *= 1 - Math.min(p.max, p.step * (fs(target).extra.hitBy?.[skill.element] ?? 0)) / 100;
    }
    keep *= 1 - cast.guardPct / 100;
    const outgoingPeanut = !cast.sealed && owned(a, side, 'peanut') && actor.name.includes('갈매미맨') ? 100 + definition(a, 'peanut').p.atk : 100;
    const incomingPeanut = owned(a, (1 - side), 'peanut') && target.name.includes('갈매미맨') ? 100 + definition(a, 'peanut').p.taken : 100;
    const hit = { ...base(Math.round(skill.power * (1 + bonus / 100)) * outgoingPeanut * incomingPeanut / (100 * 100) * Math.max(.4, keep) * (1 + cast.incomingPlusPct / 100) * (typeChanged ? typeMultiplier : 1), true), ...(typeChanged ? { augmentTypeMultiplier: typeMultiplier } : {}) };
    if (typeChanged)
        hit.defense = typeMultiplier > 1 ? 'weak' : typeMultiplier < 1 ? 'resist' : 'normal';
    const before = target.hp, amount = cast.ignoreAugmentDefense ? hit.damage : absorbAugmentShield(b, foe, hit.damage);
    target.hp = Math.max(0, target.hp - amount);
    if (!target.hp) {
        delete fs(target).extra.shield;
        delete fs(target).extra.shieldUntil;
    }
    hit.damage = before - target.hp;
    const hits = fs(target).extra.hitBy ??= {};
    hits[skill.element] = (hits[skill.element] ?? 0) + 1;
    if (!cast.sealed)
        spend(a, side, 'overtime');
    if (!cast.disableHealing && !cast.disableAugmentHealing && legacyActive(a, side, 'drain', turn) && actor.hp > 0 && hit.damage > 0) {
        const p = definition(a, 'drain').p;
        io.heal(user, Math.min(Math.round(user.maxHp * p.maxHpCapPct / 100), Math.round(hit.damage * p.pct / 100)), 'drain');
        spend(a, side, 'drain');
    }
    finishAugmentCast(b, user, foe, cast, hit.damage, io);
    if (!cast.ignoreAugmentDefense && legacyActive(a, (1 - side), 'thorns', turn) && hit.damage > 0) {
        const p = definition(a, 'thorns').p;
        io.hurt(user, Math.min(Math.round(user.maxHp * p.maxHpCapPct / 100), Math.round(hit.damage * p.pct / 100)), 'thorns');
        spend(a, (1 - side), 'thorns');
    }
    finishAugmentDefense(b, foe, cast, io);
    return { hit, events };
}
export function switchRosterAugment(a, parties, side, out, incoming, voluntary, turn) { assertKnownAugmentModel(a); if (!a)
    return; if (a.version === CURRENT_AUGMENT_VERSION)
    return currentSwitch(a, parties, side, out, incoming, voluntary, turn); const { b, map, io, events } = facade(a, parties, turn, side); onAugmentSwitch(b, map.get(out), map.get(incoming), voluntary, io); return events; }
export function healRosterAugment(a, parties, side, target, amount, turn) { assertKnownAugmentModel(a); if (!a) {
    const before = target.hp;
    target.hp = Math.min(target.stats.hp, target.hp + amount);
    return target.hp - before;
} if (a.version === CURRENT_AUGMENT_VERSION)
    return currentHeal(a, parties, side, target, amount, turn); const { map, io } = facade(a, parties, turn, side), before = target.hp; io.heal(map.get(target), amount, 'item'); return target.hp - before; }
export function endRosterAugmentTurn(a, parties, turn) { assertKnownAugmentModel(a); if (!a)
    return []; if (a.version === CURRENT_AUGMENT_VERSION)
    return currentEndTurn(a, parties, turn); const { b, io, events } = facade(a, parties, turn); for (const s of [0, 1]) {
    const active = b.sides[s].fighters[0];
    if (active.hp > 0 && legacyActive(a, s, 'lunch', turn))
        io.heal(active, Math.round(active.maxHp * definition(a, 'lunch').p.pct / 100), 'regen');
    for (const f of parties[s]) {
        const st = fs(f);
        st.mods = st.mods.filter(m => --m.turns > 0);
        st.guards = st.guards.filter(g => --g.turns > 0);
        if ((st.statusImmunityTurns ?? 0) > 0)
            st.statusImmunityTurns--;
        if (st.status)
            for (const k of Object.keys(st.status))
                if (--st.status[k] <= 0)
                    delete st.status[k];
    }
    if ((a.runtime[s].neutral ?? 0) > 0)
        a.runtime[s].neutral--;
} endAugmentTurn(b, io); return events; }
export function rosterAugmentFocus(a, parties, side, turn) { assertKnownAugmentModel(a); return a?.version === CURRENT_AUGMENT_VERSION ? currentFocus(a, parties, side, turn) : []; }
export function rosterAugmentItem(a, side) { assertKnownAugmentModel(a); if (a?.version === CURRENT_AUGMENT_VERSION)
    currentItem(a, side); }
