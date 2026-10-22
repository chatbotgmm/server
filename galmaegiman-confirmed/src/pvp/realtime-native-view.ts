import {publicCombatReplayState,exposeCombatHistory} from '../battle/combat-epoch.js';
import {publicAugment,publicAugmentState,publicShield,publicAttributeSelection} from '../battle/public-dto.js';
// V2 MANUAL INTEGRATION DRAFT; not built, executed, or deployed.
// Supersedes the separately frozen v1 mapper only when integrating the v2 bundle.
// Place at galmaegiman-confirmed/src/pvp/realtime-native-view.ts.
// Source contract: chatbotgmm/server @ 838f7ebd, native.ts and battle/engine.ts.
// Only completed engine transitions may reach this mapper. Do not apply one
// player's pending choice to Battle before the other player's choice arrives.
import {existsSync} from 'node:fs';
import {legalActions, itemProblem, skillPreview, skillProblem, STRUGGLE,
  type Battle, type BattleEvent, type BattleFighterRef, type Fighter} from '../battle/engine.js';
import {statusKeys, statusName, type Attr, type Rules, type Skill} from '../battle/core.js';

import type {Content} from '../content.js';
import {topTiers} from '../battle/core.js';

type Side = 0 | 1;
export interface RealtimeNativeInventoryItem {id: string; count: number}

// ownedIds must come from the authenticated viewer's ownership query.
// This mapper cannot authorize ownership. It only filters the public catalog.
export function mapRealtimePreparationCandidates(content: Content, ownedIds: readonly string[]) {
  return [...new Set(ownedIds)].flatMap(id => {
    const character = content.map.get(id);
    if (!character || !topTiers.some(tier=>tier===character.rarity)) return [];
    return [{id: character.id, name: character.name, rarity: character.rarity,
      image: `/thumbs/${character.id}.jpg`}];
  });
}
export interface RealtimeNativeResult {
  // Already viewer-oriented by the room service. Never remap this a second time.
  winner: Side | null;
  reason: string;
  settlement: string;
  rank?: unknown; // A dedicated public rank DTO supplied by the service, not a DB row.
}
export interface RealtimeNativeRoom {
  roomId: string;
  mode: 'normal' | 'rank';
  opponentKind?: 'AI' | 'HUMAN';
  phaseId: string;
  revision: number;
  deadline: number | null;
  serverNow: number;
  canSubmit: boolean;
  mySubmitted: boolean;
  opponentReady: boolean;
  result: RealtimeNativeResult | null;
}
export interface RealtimeNativeInput {
  battle: Battle;
  viewerSide: Side;
  sessionId: string;
  names: [string, string]; // Engine/global side order, NOT viewer order.
  room: RealtimeNativeRoom;
  // Own authoritative inventory, after reservations, supplied on EVERY response.
  // Never pass foe rows or a whole player/DB record. Missing input fails closed.
  inventory?: readonly RealtimeNativeInventoryItem[];
}
export interface NativeFighterEffect {
  id: string; label: string; valueText: string; remainingTurns: number | null;
  kind: 'buff' | 'debuff' | 'guard' | 'regen' | 'status'; attrCode?: Attr;
}

// Same presentation calculations as native.ts; no dependency on route closures.
function fighterEffects(R: Rules, f: Fighter): NativeFighterEffect[] {
  const pct = (n: number) => `${n > 0 ? '+' : ''}${n}%`, st = R.data.status;
  const statusValue = {
    burn: `턴마다 최대 체력 ${st.burnDamage}% 피해 · 공격 ${pct(st.burnAtk)}`,
    freeze: '행동 불가', sleep: '행동 불가', bind: `속도 ${pct(st.bindSpd)}`,
    rage: `공격 ${pct(st.rageAtk)} · 방어 ${pct(st.rageDef)}`,
  };
  const effects: NativeFighterEffect[] = statusKeys.filter(k => (f.status[k] ?? 0) > 0)
    .map(k => ({id: `status:${k}`, label: statusName[k], valueText: statusValue[k],
      remainingTurns: f.status[k]!, kind: 'status'}));
  const statName = {atk: '공격', def: '방어', spd: '속도'};
  for (const [i, m] of f.mods.entries()) if (m.turns > 0 && m.pct !== 0)
    effects.push({id: `mod:${m.stat}:${i}`, label: `${statName[m.stat]} ${m.pct > 0 ? '강화' : '약화'}`,
      valueText: pct(m.pct), remainingTurns: m.turns, kind: m.pct > 0 ? 'buff' : 'debuff'});
  for (const [i, g] of f.guards.entries()) if (g.turns > 0)
    effects.push({id: `guard:${i}`, label: '받는 피해 감소', valueText: `-${g.pct}%`, remainingTurns: g.turns, kind: 'guard'});
  for (const [i, r] of f.regens.entries()) if (r.turns > 0)
    effects.push({id: `regen:${i}`, label: '재생', valueText: `턴마다 최대 체력 ${r.pct}% 회복`, remainingTurns: r.turns, kind: 'regen'});
  if (f.endure > 0) effects.push({id: 'endure', label: '버티기', valueText: '체력이 1 아래로 떨어지지 않음', remainingTurns: f.endure, kind: 'guard'});
  if (f.immune > 0) effects.push({id: 'immune', label: '상태이상 면역', valueText: '새로운 상태이상 차단', remainingTurns: f.immune, kind: 'guard'});
  if (f.protect) effects.push({id: 'protect', label: '완전 방어', valueText: '이번 턴 공격 차단', remainingTurns: 1, kind: 'guard'});
  if (f.lockAtk) effects.push({id: 'lockAtk', label: '교체 불가', valueText: `공격 ${pct(f.lockAtk)}`, remainingTurns: null, kind: 'buff'});
  if (f.extraAttr) effects.push({id: 'extraAttr', label: '이중 속성', valueText: R.attrName(f.extraAttr), remainingTurns: null, kind: 'buff', attrCode: f.extraAttr});
  return effects;
}

const bodyScale: Record<string, number> = {D1:1.19,D3:1.36,D4:1.24,D6:1.7,D8:1.19,T1:1.51,T3:1.51,T4:1.08,T6:1.24,T7:1.1,E2:1.1,E4:1.17,E5:1.7,E6:1.05,E7:1.8,I2:1.17,I3:1.8,I4:1.24,I5:1.1,I6:1.17};
function image(R: Rules, root: string, id: string, side: 'front' | 'back', stage: number, seen = new Set<string>()): string {
  if (seen.has(id)) throw new Error('Cyclic villain sprite fallback');
  seen.add(id);
  if (id.startsWith('V')) {
    const v = R.data.villains[id];
    if (v?.tint && existsSync(`${root}/public/battle/sprites/V17_front.webp`)) return '/duel/sprites/V17_front.webp';
    if (!existsSync(`${root}/public/battle/sprites/${id}_front.webp`) && v?.look) return image(R, root, v.look, side, stage, seen);
    return `/duel/sprites/${id}_front.webp`;
  }
  if(!R.units.has(id)&&!R.villains.has(id))return `/thumbs/${id}.jpg`;
  const actualStage = existsSync(`${root}/public/battle/sprites/${id}_s${stage}_${side}.webp`) ? stage : 0;
  return `/duel/sprites/${id}_s${actualStage}_${side}.webp`;
}
function battleFor(b: Battle, side: Side, slot: number): Battle {
  // Do not reindex raw engine sides: engine calculations read Fighter.side.
  return {...b, sides: b.sides.map((s, i) => i === side ? {...s, active: slot} : s) as Battle['sides']};
}
function matchup(b: Battle, side: Side, slot: number) {
  const copy = battleFor(b, side, slot);
  const move: Skill = {id: 'basic-preview', name: '상성 확인', power: 1, cd: 0, effects: [], desc: ''};
  return {attack: skillPreview(copy, side, move), defense: skillPreview(copy, (1 - side) as Side, move)};
}
// Read-only projection of engine doSwitch at this pinned source revision.
// Re-audit if doSwitch changes. Used by BOTH DTO and command validation so the
// advertised follow-up cannot disagree with the server's pre-act validation.
export function realtimeSwitchFollowups(b: Battle, side: Side, to: number) {
  if (b.phase !== 'action' || !b.sides[side].augs.includes('freeswitch') ||
      !legalActions(b, side).some(a => a.kind === 'switch' && a.to === to)) return [];
  const copy: Battle = {...b, sides: structuredClone(b.sides)};
  const s = copy.sides[side], out = s.fighters[s.active], incoming = s.fighters[to];
  if (out.passive.handoff) incoming.hp = Math.min(incoming.maxHp,
    incoming.hp + Math.round(incoming.maxHp * out.passive.handoff / 100));
  s.active = to;
  return legalActions(copy, side).flatMap(a => {
    if (a.kind !== 'skill') return [];
    const skill = a.skillId === STRUGGLE.id ? {...STRUGGLE, attr: incoming.attr} :
      incoming.skills.find(sk => sk.id === a.skillId);
    if (!skill) return [];
    return [{...structuredClone(skill), preview: skillPreview(copy, side, skill),
      reason: skillProblem(copy, side, skill)}];
  });
}
function viewerText(text: string, viewerSide: Side): string {
  if (viewerSide === 0) return text;
  // Swap only the engine's perspective markers, never prose or unit names.
  if (text === '승리!') return '패배…';
  if (text === '패배…') return '승리!';
  return text.replace(/^\[(내 팀|상대)\]/, (_, side: string) => side === '내 팀' ? '[상대]' : '[내 팀]');
}
function viewerRef(ref: BattleFighterRef, viewerSide: Side): BattleFighterRef {
  return {side: ref.side === viewerSide ? 0 : 1, slot: ref.slot, key: ref.key, id: ref.id};
}
function viewerEvent(e: BattleEvent, viewerSide: Side): BattleEvent {
  // Allow-list the display event contract. No pending command or internal RNG.
  return {id: e.id, turn: e.turn, kind: e.kind,
    ...(e.actor ? {actor: viewerRef(e.actor, viewerSide)} : {}),
    ...(e.target ? {target: viewerRef(e.target, viewerSide)} : {}),
    skillId: e.skillId, skillName: e.skillName, attr: e.attr, power: e.power,
    absorbed: e.absorbed, shieldBefore: e.shieldBefore, shieldAfter: e.shieldAfter,
    reason: e.reason, amount: e.amount, hpBefore: e.hpBefore, hpAfter: e.hpAfter,
    maxHp: e.maxHp, crit: e.crit, multiplier: e.multiplier, hit: e.hit,
    status: e.status ? {...e.status} : undefined, extraAttr: e.extraAttr,
    text: viewerText(e.text, viewerSide), logIndex: e.logIndex};
}

export function createRealtimeNativeMapper(assetRoot: string) {
  return (input: RealtimeNativeInput) => mapRealtimeNativeBattle({...input, assetRoot});
}

export function mapRealtimeNativeBattle(input: RealtimeNativeInput & {assetRoot: string}) {
  const {battle: b, viewerSide, sessionId, names, room, assetRoot: root} = input;
  const R = b.R;
  const order: [Side, Side] = [viewerSide, (1 - viewerSide) as Side];
  const exposeHistory = exposeCombatHistory(b);
  const history = exposeHistory ? b.log.map(line => viewerText(line, viewerSide)) : [];
  const canSubmit = room.canSubmit && !room.mySubmitted && b.phase !== 'over';
  const counts = new Map<string, number>();
  for (const row of input.inventory ?? []) {
    if (!(row.id in R.data.items) || !Number.isSafeInteger(row.count) || row.count < 0 ||
        counts.has(row.id)) throw new Error('Invalid authoritative inventory DTO');
    counts.set(row.id, row.count);
  }
  const inventory = [...counts].map(([id, count]) => ({id, count}));
  const items = Object.entries(R.data.items).map(([id, definition]) => ({
    id, ...structuredClone(definition),
    reason: input.inventory === undefined ? '아이템 정보를 다시 불러와 주세요.' :
      !canSubmit || b.phase !== 'action' ? '지금은 아이템을 사용할 수 없어요.' :
      (counts.get(id) ?? 0) <= 0 ? '아이템이 없어요.' : itemProblem(b, viewerSide, id),
  }));
  const teams = order.map((side, relativeSide) => {
    const s = b.sides[side], own = relativeSide === 0;
    return {
      active: s.active, comboUsed: s.comboUsed,
      augments: s.augs.map(id => publicAugment(b,id)),
      // Opponent pending choices are never exposed. Empty arrays retain the
      // old NativeBattle parser contract; booleans do not reveal choice payloads.
      needSwitch: own ? s.needSwitch : false,
      attributeSelection: own ? publicAttributeSelection(b, side) : null,
      offers: own && b.phase === 'choose' ? (s.offers ?? []).map((id, index) => ({
        ...publicAugment(b,id,s.rerolled[index]??false),
        icon: existsSync(`${root}/public/battle/icons/${id}.png`) ? id : 'u-gift',
        rerolled: s.rerolled[index] ?? false,
      })) : [],
      fighters: s.fighters.map((f, slot) => {
        const previewBattle = battleFor(b, side, slot);
        const skills = own ? [...f.skills] : [];
        if (own && b.phase === 'action' && slot === s.active &&
            legalActions(b, side).some(a => a.kind === 'skill' && a.skillId === STRUGGLE.id) &&
            !skills.some(sk => sk.id === STRUGGLE.id)) skills.push({...STRUGGLE, attr: f.attr});
        return {
          key: f.rec.uid, temporary:f.rec.temporary??false, expiresAt:f.rec.expiresAt??null, id: f.rec.ownedCharacterId??f.rec.id, combatDefinitionId:f.rec.id, name: f.name, level: f.rec.level, stage: f.rec.stage,
          role: f.role, base: {...f.base}, tint: R.data.villains[f.rec.id]?.tint ?? null,
          realTintArt: !!R.data.villains[f.rec.id]?.tint && existsSync(`${root}/public/battle/sprites/V17_front.webp`),
          bodyScale: f.rec.stage && existsSync(`${root}/public/battle/sprites/${f.rec.id}_s${f.rec.stage}_front.webp`) ? 1 : bodyScale[f.rec.id] ?? 1,
          matchup: matchup(b, side, slot), image: image(R, root, f.rec.ownedCharacterId??f.rec.id, own ? 'back' : 'front', f.rec.stage),
          hp: f.hp, maxHp: f.maxHp, ...publicShield(b,f), attr: R.attrName(f.attr), attrCode: f.attr,
          extraAttr: f.extraAttr ? R.attrName(f.extraAttr) : null,
          status: {...f.status}, effects: fighterEffects(R, f),
          skills: skills.map(sk => ({...structuredClone(sk),
            preview: skillPreview(previewBattle, side, sk), reason: skillProblem(previewBattle, side, sk)})),
        };
      }),
      legal: own && canSubmit && b.phase === 'action' ? legalActions(b, side).map(a => ({...a})) : [],
      switchFollowups: own && canSubmit && b.phase === 'action' && s.augs.includes('freeswitch')
        ? Object.fromEntries(legalActions(b, side).flatMap(a => a.kind === 'switch'
          ? [[String(a.to), realtimeSwitchFollowups(b, side, a.to)]] : [])) : {},
      itemsUsed: s.itemsUsed,
    };
  });
  // Build a fresh output. Never spread Battle, SideState, Fighter, or room state.
  return {
    ...publicCombatReplayState(b), ...publicAugmentState(b), session: sessionId, revision: room.revision, mode: room.mode,
    phase: b.phase, turn: b.turn,
    winner: b.winner === null ? null : b.winner === viewerSide ? 0 : 1,
    serverNow: room.serverNow, chooseDeadline: b.phase === 'choose' ? room.deadline : null,
    scene: 'harbor', hint: '', boss: false,
    teams, history, log: history.slice(-50),
    displayEventVersion: 1, displayEventSeq: b.eventSeq,
    displayEvents: exposeHistory ? b.events.map(e => viewerEvent(e, viewerSide)) : [],
    displayLogStart: Math.max(0, history.length - 50),
    items, inventory, itemLimit: R.data.itemRules.perBattle,
    matchInfo: {mode: room.mode, level: b.sides[viewerSide].fighters[0].rec.level,
      mineName: names[viewerSide], foeName: names[order[1]], foeTier: null, bot: room.opponentKind === 'AI'},
    result: room.result ? {...structuredClone(room.result), won: room.result.winner === 0, xp: 0} : null,
    room: {roomId: room.roomId, mode: room.mode, phaseId: room.phaseId,
      revision: room.revision, deadline: room.deadline, serverNow: room.serverNow,
      canSubmit, mySubmitted: room.mySubmitted, opponentReady: room.opponentReady,
      result: room.result ? structuredClone(room.result) : null},
  };
}
export type RealtimeNativeBattle = ReturnType<typeof mapRealtimeNativeBattle>;



