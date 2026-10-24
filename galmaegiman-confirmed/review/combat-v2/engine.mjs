// Review-only authoritative 1v1 command processor. No production routes or DB.
export const EPOCH = 'review-roster60-combat-v1';
export const ELEMENTS = Object.freeze(['NORMAL','FIRE','WATER','LIGHTNING','ICE','NATURE','HOLY','DARK']);
const MULTIPLIER = Object.freeze({weak: 1.5, normal: 1, resist: 0.5, immune: 0});
const copy = structuredClone;
const fail = code => { throw new Error(code); };
const number = x => typeof x === 'number' && Number.isFinite(x);
const integer = x => Number.isSafeInteger(x) && x >= 0;

function skill(s) {
  if (!s || typeof s.id !== 'string' || !s.id || typeof s.name !== 'string'
      || !ELEMENTS.includes(s.element) || !integer(s.mpCost)) fail('INVALID_SKILL');
  return copy(s);
}
function fighter(f, balance) {
  if (!f || typeof f.id !== 'string' || !integer(f.level) || f.level < 1
      || !integer(f.maxHp) || f.maxHp < 1 || !Array.isArray(f.skills) || f.skills.length > 6) fail('INVALID_FIGHTER');
  const skills = f.skills.map(skill), attack = skill(f.attack);
  if (new Set(skills.map(s => s.id)).size !== skills.length) fail('DUPLICATE_SKILL');
  for (const e of ELEMENTS) {
    if (!number(f.suitability?.[e]) || !['weak','normal','resist','immune','reflect'].includes(f.defense?.[e])) fail('PROFILE_REQUIRED');
  }
  const maxMp = balance.maxMp(copy(f));
  if (!integer(maxMp) || maxMp < 1) fail('MP_GROWTH_REQUIRED');
  // No creature element field is retained. Every battle starts at full HP/MP.
  return {id:f.id, name:f.name, level:f.level, maxHp:f.maxHp, hp:f.maxHp,
    maxMp, mp:maxMp, suitability:copy(f.suitability), defense:copy(f.defense), attack, skills};
}

export function createCombat({battleId, players, fighters, firstSide, balance}) {
  if (typeof battleId !== 'string' || !battleId || players?.length !== 2 || new Set(players).size !== 2
      || players.some(p => typeof p !== 'string' || !p) || fighters?.length !== 2
      || ![0,1].includes(firstSide) || typeof balance?.maxMp !== 'function'
      || typeof balance.baseDamage !== 'function' || typeof balance.reflectDamage !== 'function'
      || typeof balance.version !== 'string' || !balance.version) fail('EXPLICIT_REVIEW_CONFIG_REQUIRED');
  let state = {battleId, epoch:EPOCH, balanceVersion:balance.version, revision:0, turn:1,
    activeSide:firstSide, phase:'action', winner:null, fighters:fighters.map(f => fighter(f,balance)), events:[]};
  const receipts = new Map(), frames = [copy(state)];
  const reason = (s, side, cost = 0) => s.phase === 'over' ? '전투 종료'
    : s.activeSide !== side ? '상대 행동 중' : s.fighters[side].mp < cost ? 'MP 부족' : '';
  function snapshot(s, side) {
    const self = s.fighters[side];
    const command = (kind,label,why='') => ({kind,label,enabled:!why,reason:why});
    return {epoch:EPOCH, battleId, revision:s.revision, turn:s.turn, phase:s.phase,
      activeSide:s.activeSide, viewerSide:side, winner:s.winner, self:copy(self), foe:copy(s.fighters[1-side]),
      commands:[command('attack','공격',reason(s,side,self.attack.mpCost)),command('skills','기술',reason(s,side)),
        command('focus','집중',reason(s,side)),command('item','아이템','아이템 계약 미연결'),
        command('switch','교체','현재 1대1 · 교체 계약 미연결'),command('escape','도망치기','도망 판정 미연결')],
      skills:self.skills.map(sk => ({...copy(sk),enabled:!reason(s,side,sk.mpCost),reason:reason(s,side,sk.mpCost)})),
      events:copy(s.events)};
  }
  function viewer(auth) {
    const side = players.indexOf(auth?.playerId);
    if (side < 0) fail('UNAUTHORIZED');
    return side;
  }
  return {
    snapshot(auth) { return snapshot(state,viewer(auth)); },
    command(auth, input) {
      const side = viewer(auth);
      const keys = input?.kind === 'skill' ? ['battleId','requestId','expectedRevision','kind','skillId']
        : ['battleId','requestId','expectedRevision','kind'];
      if (!input || Object.keys(input).sort().join() !== keys.sort().join()
          || input.battleId !== battleId || typeof input.requestId !== 'string' || !input.requestId
          || !integer(input.expectedRevision)) fail('INVALID_COMMAND');
      const payload = JSON.stringify([input.battleId,input.expectedRevision,input.kind,input.skillId ?? null]);
      const receiptKey = `${side}:${input.requestId}`;
      if (receipts.has(receiptKey)) {
        const old = receipts.get(receiptKey);
        if (old.payload !== payload) fail('IDEMPOTENCY_CONFLICT');
        return copy(old.response);
      }
      if (input.expectedRevision !== state.revision) fail('STALE_REVISION');
      if (state.phase === 'over') fail('BATTLE_OVER');
      if (state.activeSide !== side) fail('NOT_YOUR_TURN');
      if (!['attack','skill','focus'].includes(input.kind)) fail('ACTION_NOT_CONNECTED');
      const next = copy(state), a = next.fighters[side], d = next.fighters[1-side];
      const event = {seq:next.revision+1, kind:input.kind, actor:side, mpBefore:a.mp};
      if (input.kind === 'focus') {
        // Explicit review rounding: floor recovered MP, then cap to max.
        a.mp = Math.min(a.maxMp, a.mp + Math.floor(a.maxMp * .25));
        event.mpRecovered = a.mp - event.mpBefore;
      } else {
        const sk = input.kind === 'attack' ? a.attack : a.skills.find(s => s.id === input.skillId);
        if (!sk) fail('UNKNOWN_SKILL');
        if (a.mp < sk.mpCost) fail('INSUFFICIENT_MP');
        const base = balance.baseDamage(copy(a),copy(d),copy(sk));
        if (!number(base) || base < 0) fail('INVALID_DAMAGE_POLICY');
        // Clamp only the invalid negative-damage outcome, not suitability balance range.
        const fit = Math.max(0,1 + .1 * a.suitability[sk.element]);
        const defense = d.defense[sk.element];
        let raw = defense === 'reflect' ? balance.reflectDamage({baseDamage:base,suitabilityMultiplier:fit,
          attacker:copy(a),defender:copy(d),skill:copy(sk)}) : base * fit * MULTIPLIER[defense];
        if (!number(raw) || raw < 0) fail('INVALID_DAMAGE_POLICY');
        const target = defense === 'reflect' ? a : d;
        const damage = Math.min(target.hp,Math.floor(raw));
        event.skillId = sk.id; event.element = sk.element; event.suitabilityMultiplier = fit;
        event.defense = defense; event.target = defense === 'reflect' ? side : 1-side;
        event.hpBefore = target.hp; target.hp -= damage; event.hpAfter = target.hp; event.damage = damage;
        // One resolved return hit only; no recursive skill/effect invocation.
        a.mp -= sk.mpCost;
      }
      event.mpAfter = a.mp;
      next.events.push(event); next.revision++; next.turn++;
      next.activeSide = 1-side; // Weakness and focus never grant an extra action.
      if (a.hp === 0 || d.hp === 0) { next.phase='over'; next.winner=a.hp===0?1-side:side; }
      const response = snapshot(next,side);
      state=next; frames.push(copy(next)); receipts.set(receiptKey,{payload,response:copy(response)});
      return response;
    },
    replay(auth) { viewer(auth); return {epoch:EPOCH,balanceVersion:balance.version,frames:copy(frames)}; },
  };
}
