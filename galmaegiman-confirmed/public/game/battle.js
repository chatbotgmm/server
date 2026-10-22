// 웹 게임: 매칭(#match) · 실시간 방(수락·준비) · 증강체 · 전투(#battle). 스토리·연습은 /my/api/native/battle/*, 일반전·랭크전은 /my/api/pvp/*.
// - 30초 표시는 서버가 실제로 내 입력을 받는 동안만(deadline - 동기화한 서버 시각). 메뉴 이동·재접속·화면 크기 변경으로 다시 세지 않는다.
// - 랭크전은 어떤 경우에도 AI로 바꾸지 않는다(서버 규칙). 일반전 AI 상대는 서버가 정한 경우만, 화면에 AI라고 표시한다.
// - 제출 뒤 성공 표시는 서버 응답 기준. 연결이 불확실하면 같은 행동을 자동으로 다시 보내지 않고 상태를 다시 받아 확인한다.
// - 시간이 끝나도 화면이 임의로 공격하지 않는다(입력 잠금 + 서버 결과 대기).
(() => {
  'use strict';
  const S = window.GMM, { esc, fmt } = S;
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const view = $('#view'), app = $('#app');
  const rid = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`);
  const STATUS = { burn: '화상', freeze: '빙결', sleep: '잠', bind: '묶임', rage: '땅콩 폭주' };
  const ATTR = { STONE: '돌', GOLD: '황금', FIRE: '불', ICE: '얼음', BLOSSOM: '꽃', SEA: '바다', SHADOW: '그림자', STAR: '별', THREAD: '실', GRAVITY: '중력' };
  const TIER = { silver: '실버', gold: '골드', prismatic: '프리즘' };
  const replayAllowed = s => s.replayAvailable === true && s.combatRulesEpoch === S.combatEpoch;
  const MODE = { normal: '일반전', rank: '랭크전', story: '스토리', practice: '연습' };
  const mmss = ms => { const s = Math.max(0, Math.ceil(ms / 1000)); return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`; };
  let poll = null, tick = null;
  const stop = () => { clearTimeout(poll); poll = null; clearInterval(tick); tick = null; };
  addEventListener('hashchange', stop);

  // 서버 시각 맞추기: 응답의 serverNow와 받은 순간의 내 시계 차이
  const clock = { off: 0, set(serverNow) { if (typeof serverNow === 'number') this.off = serverNow - Date.now(); }, now() { return Date.now() + this.off; } };
  function countdown(el, deadline, onExpire) {
    clearInterval(tick);
    if (!el) return;
    if (!deadline) { el.hidden = true; return; }
    const draw = () => { const left = deadline - clock.now(); el.hidden = false; el.textContent = left > 0 ? `내 선택 ${mmss(left)}` : '시간이 끝났어요 · 서버 결과를 기다려요'; el.classList.toggle('late', left <= 10_000); if (left <= 0) { clearInterval(tick); onExpire && onExpire(); } };
    draw(); tick = setInterval(draw, 250);
  }

  // ── 매칭 ──
  const LORE = () => { const u = (S.state?.me?.units || []).filter(x => x.tagline); return u.length ? u : null; };
  async function match(params) {
    S.title('매칭', ''); app.dataset.place = ''; app.dataset.screen = 'inner';
    const mode = params.get('mode') === 'rank' ? 'rank' : 'normal';
    let q = null, cancelling = false, starting = false, loreAt = -1;
    view.innerHTML = `<section class="match"><div class="m-card" aria-live="polite">
      <h1 data-head>${MODE[mode]} · 상대 찾는 중</h1><div class="m-seal" aria-hidden="true"><img src="/site/a/team.webp" alt="" width="96" height="96"></div>
      <p class="m-time" data-time>탐색 준비 중…</p><p class="m-note">${mode === 'rank' ? '랭크전은 사람끼리만 겨뤄요.' : '30초 넘게 상대가 없으면 서버 규칙에 따라 AI 상대와 이어질 수 있어요.'}</p>
      <div class="m-acts"><button class="btn ghost lg" type="button" data-cancel disabled>매칭 취소</button></div><p class="err" role="alert"></p></div>
      <button class="lore" type="button" data-lore ${LORE() ? '' : 'hidden'}>항구 이야기 ›</button><p class="lore-line" data-loreline aria-live="polite"></p></section>`;
    const err = $('.err', view), cancel = $('[data-cancel]', view), time = $('[data-time]', view);
    $('[data-lore]', view).onclick = () => { const l = LORE(); if (!l) return; loreAt = (loreAt + 1) % l.length; $('[data-loreline]', view).textContent = `${l[loreAt].name}: ${l[loreAt].tagline}`; };
    const showQueue = () => {
      clearInterval(tick);
      if (!q || q.status !== 'waiting') return;
      const since = q.queuedAt || clock.now();
      const draw = () => { time.textContent = `탐색 ${mmss(clock.now() - since)}`; };
      draw(); tick = setInterval(draw, 500);
    };
    const settle = r => {
      clock.set(r.serverNow); q = r;
      if (r.status === 'matched' && r.roomId) { stop(); S.battle = { kind: 'realtime', roomId: r.roomId, mode: r.mode || mode }; location.hash = '#battle'; return true; }
      if (r.status === 'waiting') { cancel.disabled = cancelling; showQueue(); return false; }
      if (r.status === 'cancelled' || r.status === 'expired' || r.status === 'idle') {
        stop(); time.textContent = r.status === 'expired' ? '탐색 시간이 끝났어요.' : r.notice || '매칭을 멈췄어요.';
        $('[data-head]', view).textContent = `${MODE[mode]} · 매칭 멈춤`;
        $('.m-acts', view).innerHTML = '<button class="btn primary lg" type="button" data-again>다시 찾기</button><a class="btn ghost lg" href="#play">플레이로</a>';
        $('[data-again]', view).onclick = () => { location.hash = `#match?mode=${mode}&t=${Date.now()}`; };
        return true;
      }
      return false;
    };
    const loop = async () => {
      try { if (settle(await S.api('pvp/queue/current'))) return; }
      catch (x) { if (x.status === 401) return; err.textContent = x.status === 0 ? '연결이 불안정해요. 다시 연결하는 중…' : x.message; }
      if (location.hash.startsWith('#match')) poll = setTimeout(loop, 2500);
    };
    cancel.onclick = async () => {
      if (cancelling || !q?.attemptId) return;
      cancelling = true; cancel.disabled = true; cancel.textContent = '취소하는 중…'; clearTimeout(poll);
      try {
        const r = await S.api('pvp/queue/cancel', { attemptId: q.attemptId, token: q.token, requestId: rid() });
        if (r.status === 'cancelled') { stop(); S.toast('매칭을 취소했어요.'); location.hash = '#play'; return; }
        settle(r);   // 취소보다 매칭이 먼저 되면 서버 결과(매칭)를 따른다
      } catch (x) { err.textContent = x.message; cancelling = false; cancel.disabled = false; cancel.textContent = '매칭 취소'; loop(); }
    };
    // 이미 대기·방이 있으면 이어서(재접속), 없으면 새로 시작
    try {
      const cur = await S.api('pvp/queue/current'); clock.set(cur.serverNow);
      if (cur.status === 'waiting' || cur.status === 'matched') { if (!settle(cur)) loop(); return; }
      if (starting) return; starting = true;
      const a = await S.api('pvp/queue/attempt', { mode, protocol: 3 });
      const j = await S.api('pvp/queue/join', { attemptId: a.attemptId, token: a.token });
      if (!settle(j)) poll = setTimeout(loop, 2500);
    } catch (x) {
      if (x.status === 401) return;
      time.textContent = ''; err.textContent = x.message;
      $('.m-acts', view).innerHTML = '<a class="btn ghost lg" href="#play">플레이로</a><a class="btn ghost lg" href="#team?mode=pvp">대전 편성</a>';
    }
  }

  // ── 전투 화면 ──
  // 상태: S.battle = {kind:'native', snap} | {kind:'realtime', roomId, room(view), snap}
  const ui = { pane: 'root', item: null, target: null, sw: null, aug: null, sending: false, note: '' };
  const me = s => s.teams[0], foe = s => s.teams[1];
  const active = t => t.fighters[t.active];
  const canAct = () => { const b = S.battle; if (!b?.snap || b.snap.phase === 'over' || ui.sending) return false; if (b.kind === 'native' && b.snap.phase === 'choose' && b.snap.chooseDeadline && b.snap.chooseDeadline <= clock.now()) return false; return b.kind === 'native' ? true : !!(b.room?.canSubmit && !b.room?.mySubmitted); };
  const deadline = () => { const b = S.battle; if (!b?.snap) return null; if (b.kind === 'native') return b.snap.phase === 'choose' ? b.snap.chooseDeadline || null : null; return canAct() ? b.room?.deadline || null : null; };
  const isPve = () => S.battle?.kind === 'native';

  async function load() {
    const b = S.battle;
    if (b?.kind === 'realtime') { const r = await S.api(`pvp/room/current?roomId=${encodeURIComponent(b.roomId)}`); applyRoom(r); return; }
    const cur = await S.api('native/battle/current');
    if (cur.session) { cur.inventory = cur.inventory || S.state?.items || []; S.battle = { kind: 'native', snap: cur }; clock.set(cur.serverNow); return; }
    // 실시간 방이 남아 있으면 이어서
    const q = await S.api('pvp/queue/current'); clock.set(q.serverNow);
    if (q.status === 'matched' && q.roomId) { S.battle = { kind: 'realtime', roomId: q.roomId, mode: q.mode }; return load(); }
    S.battle = null; if (cur.notice) S.toast(cur.notice);
  }
  function applyRoom(r) {
    clock.set(r.serverNow);
    const b = S.battle && S.battle.kind === 'realtime' ? S.battle : (S.battle = { kind: 'realtime', roomId: r.roomId });
    const phaseChanged = b.room?.phaseId !== r.phaseId;
    b.room = r; b.mode = r.mode; b.snap = r.battle || null;
    if (phaseChanged) Object.assign(ui, { pane: 'root', item: null, target: null, sw: null, aug: null, note: '' });
  }
  async function battle() {
    S.title('', ''); app.dataset.screen = 'battle'; app.dataset.place = 'battle';
    // 화면에 들어올 때마다 서버 상태로 다시 맞춘다(다른 탭·재접속·끝난 전투). 방금 시작한 전투는 받은 응답 그대로
    const fresh = S.battle?.kind === 'native' && S.battle.at && Date.now() - S.battle.at < 3000;
    if (S.battle?.snap && !fresh) { draw(); }
    else if (!fresh) view.innerHTML = '<div class="state" role="status"><span class="spin"></span>전투 정보를 받는 중…</div>';
    if (!fresh) {
      try { await load(); } catch (x) { if (x.status !== 401) view.innerHTML = `<section class="soon"><p class="err" role="alert">${esc(x.message)}</p><a class="btn ghost" href="#home">항구로</a></section>`; return; }
    }
    if (!location.hash.startsWith('#battle')) return;
    if (!S.battle) { view.innerHTML = '<section class="soon"><h1>진행 중인 전투가 없어요</h1><a class="btn primary" href="#play">플레이</a><a class="btn ghost" href="#home">항구로</a></section>'; return; }
    draw(); schedule();
  }
  // 실시간: 내 입력이 없거나 상대를 기다리면 상태를 주기적으로 다시 받는다. 스토리는 서버가 바로 답하므로 마감 확인 때만
  function schedule() {
    clearTimeout(poll);
    const b = S.battle; if (!b || !location.hash.startsWith('#battle')) return;
    if (b.kind === 'realtime') {
      if (b.room?.phase === 'over' && b.room?.result) return;
      poll = setTimeout(async () => {
        const before = `${b.room?.phaseId}:${b.room?.revision}:${ui.note}`;
        try { applyRoom(await S.api(`pvp/room/current?roomId=${encodeURIComponent(b.roomId)}`)); ui.note = ''; }
        catch (x) { if (x.status === 401) return; ui.note = x.status === 0 ? '연결이 끊겼어요. 다시 연결하는 중…' : `${x.message} · 다시 확인하는 중…`; }
        // 서버 상태가 그대로면 다시 그리지 않는다(고르던 슬롯·패널을 지우지 않게). 시계는 countdown이 따로 움직임
        if (location.hash.startsWith('#battle')) { if (before !== `${b.room?.phaseId}:${b.room?.revision}:${ui.note}`) draw(); schedule(); }
      }, canAct() ? 3000 : 1500);
    }
  }
  const rev = () => (S.battle?.kind === 'realtime' ? `${S.battle.room?.phaseId}:${S.battle.room?.revision}:${S.battle.room?.mySubmitted}` : S.battle?.snap?.revision);
  async function refetchAfterExpiry() {
    try {
      await load();
      // 연결이 끊긴 채 보낸 선택: 서버 상태가 그대로면 접수되지 않은 것
      if (ui.lostRev !== undefined) { ui.note = rev() === ui.lostRev ? '보낸 선택은 접수되지 않았어요. 다시 골라 주세요.' : ''; ui.lostRev = undefined; }
    } catch { if (ui.lostRev !== undefined) { setTimeout(refetchAfterExpiry, 2500); return; } }
    if (location.hash.startsWith('#battle')) { draw(); schedule(); }
  }

  // 제출: 서버 응답으로만 상태를 바꾼다
  async function send(kind, payload) {
    const b = S.battle; if (!b || ui.sending) return;
    ui.sending = true; ui.note = ''; draw();
    try {
      if (b.kind === 'native') {
        const body = { session: b.snap.session, revision: b.snap.revision, [kind]: payload };
        const snap = await S.api('native/battle/turn', body); clock.set(snap.serverNow);
        snap.inventory = snap.inventory || b.snap.inventory; b.snap = snap;   // 턴 응답에는 보유 수가 없어 시작 때 값을 유지(화면에 「전투 시작 때」로 표시)
      } else if (kind === 'leave') {
        applyRoom(await S.api('pvp/room/surrender', { roomId: b.roomId, requestId: rid() }));
      } else {
        const command = kind === 'action' ? { action: payload } : kind === 'choose' ? { choose: payload } : kind === 'reroll' ? { reroll: payload } : kind === 'prepare' ? { prepare: payload } : { accept: payload };
        b.pendingReq = b.pendingReq && b.pendingReq.fp === JSON.stringify(command) ? b.pendingReq : { id: rid(), fp: JSON.stringify(command) };
        applyRoom(await S.api('pvp/room/command', { roomId: b.roomId, phaseId: b.room.phaseId, requestId: b.pendingReq.id, command }));
        b.pendingReq = null;
      }
      if (kind !== 'reroll') Object.assign(ui, { pane: 'root', item: null, target: null, sw: null, aug: null });
    } catch (x) {
      if (x.status === 401) return;
      ui.note = x.status === 0 ? '연결을 확인하는 중이에요. 보낸 선택이 접수됐는지 다시 확인할게요.' : x.message;
      if (x.status === 0) { ui.lostRev = rev(); setTimeout(refetchAfterExpiry, 1500); }
    } finally { ui.sending = false; }
    if (S.battle?.snap?.phase === 'over' && S.battle.kind === 'native') { S.state && (S.state.activeBattle = false); S.loadState().catch(() => {}); }
    draw(); schedule();
  }

  // ── 그리기 ──
  function draw() {
    const b = S.battle; if (!b) return;
    if (b.kind === 'realtime' && b.room && !b.snap) return drawRoom(b.room);
    const s = b.snap; if (!s) return;
    const offers = me(s).offers || [];
    // A local draft belongs only to this exact server selection scope/revision.
    const selectionKey = JSON.stringify([s.session || b.roomId, s.revision, b.room?.phaseId, b.room?.revision, me(s).attributeSelection]);
    if (ui.selectionKey !== selectionKey) { ui.selectionKey = selectionKey; ui.attribute = null; ui.aug = null; ui.sw = null; }
    S.title('', ''); app.dataset.screen = 'battle';   // 전투·증강체: 머리줄·하단 메뉴 없이
    if (s.phase === 'choose' && offers.length && canAct() && !ui.aug) { app.dataset.place = 'augments'; return drawAugment(s); }
    app.dataset.place = 'battle';
    view.innerHTML = `<section class="battle">
      <div class="stage" aria-label="전투 무대">
        <div class="hp foe-hp"></div><div class="actor-slot foe" data-standee></div>
        <div class="actor-slot mine" data-standee></div><div class="hp my-hp"></div><div class="fx" aria-hidden="true"></div><p class="banner" hidden></p>
        ${s.matchInfo ? `<p class="vs">${esc(MODE[s.mode] || '')} · ${esc(s.matchInfo.foeName || '')}${s.matchInfo.bot ? ' <b class="ai">AI 상대</b>' : ''}</p>` : `<p class="vs">${esc(MODE[s.mode] || '')}</p>`}
      </div>
      <div class="cmd"><div class="cmd-head"><b data-pane-title></b><span class="timer" data-timer hidden></span></div><div class="cmd-body"></div>
        <p class="log" aria-live="polite">${esc(replayAllowed(s) ? (s.log || []).slice(-2).join(' ') : s.replayBlockedReason || '이전 버전 전투기록 · 재생할 수 없어요.')}</p><p class="err" role="alert">${esc(ui.note)}</p></div></section>`;
    hpPanel($('.foe-hp', view), active(foe(s)), false);
    hpPanel($('.my-hp', view), active(me(s)), true);
    S.standee($('.actor-slot.foe', view), S.sprite(active(foe(s)).image), `상대 ${active(foe(s)).name}`);
    S.standee($('.actor-slot.mine', view), S.sprite(active(me(s)).image), `내 ${active(me(s)).name}`);
    $('.actor-slot.foe', view).classList.toggle('down', active(foe(s)).hp <= 0); $('.actor-slot.mine', view).classList.toggle('down', active(me(s)).hp <= 0);   // 쓰러진 대원은 무대에서 빠진 모습
    // 전투가 끝난 턴은 연출을 다 보여 준 뒤에 결과를 띄운다(입력이 필요 없는 때라 잠가도 됨)
    const key = s.session || b.roomId, holdResult = replayAllowed(s) && s.phase === 'over' && b.seenKey === key && (s.displayEvents || []).some(e => e && e.id > (b.seen || 0));
    if (holdResult) { $('[data-pane-title]', view).textContent = '전투 중'; $('.cmd-body', view).innerHTML = '<div class="waiting" role="status"><span class="spin"></span>마지막 장면을 보여 주는 중…</div>'; }
    else pane(s);
    countdown($('[data-timer]', view), deadline(), refetchAfterExpiry);
    const stageNow = $('.stage', view);
    replay(s).finally(() => { if (holdResult && document.body.contains(stageNow)) pane(s); });
  }

  // ── 전투 연출: 서버가 보낸 displayEvents 중 아직 안 본 것만 무대에서 재생한다(서버 규칙·결과는 바꾸지 않음) ──
  // 명령 패널은 이미 새 상태로 그려져 있어 입력을 잠그지 않는다. 화면을 다시 그리면 재생을 멈추고 최종 상태를 보여 준다.
  // 처음 들어온 전투(재접속 포함)는 지난 기록을 다시 재생하지 않는다. 움직임 줄이기면 재생하지 않는다.
  const GLYPH = { STONE: '🪨', GOLD: '🪙', FIRE: '🔥', ICE: '❄️', BLOSSOM: '🌸', SEA: '💧', SHADOW: '🌑', STAR: '⭐', THREAD: '🧵', GRAVITY: '🌀' };
  const TINT = { STONE: '#c9b48a', GOLD: '#f2c94c', FIRE: '#ff7a3d', ICE: '#8fdcf5', BLOSSOM: '#ff9ccf', SEA: '#4fb3ff', SHADOW: '#8a6bd1', STAR: '#ffe36e', THREAD: '#e8a0ff', GRAVITY: '#9aa7ff' };
  const wait = ms => new Promise(r => setTimeout(r, ms));
  let playToken = 0;
  async function replay(s) {
    const b = S.battle, token = ++playToken;
    if (!replayAllowed(s)) { b.seenKey = s.session || b.roomId; b.seen = s.displayEventSeq || 0; return; }
    const evs = (s.displayEvents || []).filter(e => e && typeof e.id === 'number');
    const last = evs.length ? evs[evs.length - 1].id : (s.displayEventSeq || 0);
    const key = s.session || b.roomId;
    if (b.seenKey !== key) { b.seenKey = key; b.seen = last; return; }   // 처음 보는 전투: 지난 기록은 재생하지 않음
    const fresh = evs.filter(e => e.id > (b.seen || 0)); b.seen = Math.max(b.seen || 0, last);
    const calm = document.documentElement.classList.contains('calm') || matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!fresh.length || calm) return;
    const stage = $('.stage', view), fx = $('.fx', view), slotEl = side => $(side === 0 ? '.actor-slot.mine' : '.actor-slot.foe', view), hpEl = side => $(side === 0 ? '.my-hp' : '.foe-hp', view);
    const team = side => s.teams[side];
    // 시작 모습: 교체가 있으면 교체 전 대원, 체력은 이번에 처음 바뀌기 전 값
    const shownSlot = [0, 1].map(side => { const sw = fresh.find(e => e.kind === 'switch' && e.actor?.side === side); return sw ? sw.actor.slot : team(side).active; });
    const hp = [0, 1].map(side => team(side).fighters.map((f, slot) => { const e = fresh.find(x => x.target?.side === side && x.target?.slot === slot && typeof x.hpBefore === 'number'); return e ? e.hpBefore : f.hp; }));
    const paint = side => { const f = team(side).fighters[shownSlot[side]]; if (f) hpPanel(hpEl(side), { ...f, hp: hp[side][shownSlot[side]] }, side === 0); };
    const show = side => { const f = team(side).fighters[shownSlot[side]]; if (f) S.standee(slotEl(side), S.sprite(f.image), `${side === 0 ? '내' : '상대'} ${f.name}`); };
    for (const side of [0, 1]) { slotEl(side).classList.toggle('down', hp[side][shownSlot[side]] <= 0); if (shownSlot[side] !== team(side).active) show(side); paint(side); }
    const center = side => { const a = stage.getBoundingClientRect(), r = slotEl(side).getBoundingClientRect(); return [r.left - a.left + r.width / 2, r.top - a.top + r.height * 0.5]; };
    const banner = $('.banner', view), log = $('.log', view);
    const say = t => { if (t && log) log.textContent = t; };
    const pop = (side, text, cls) => { const [x, y] = center(side), d = document.createElement('div'); d.className = `dmg ${cls || ''}`; d.textContent = text; d.style.left = `${x}px`; d.style.top = `${y - 30}px`; fx.append(d);
      d.animate([{ transform: 'translate(-50%,0)', opacity: 1 }, { transform: 'translate(-50%,-44px)', opacity: 0 }], { duration: 950, easing: 'ease-out' }).onfinish = () => d.remove(); };
    const burst = (side, color, n = 12, dist = 70) => { const [x, y] = center(side); for (let i = 0; i < n; i++) { const p = document.createElement('div'), ang = Math.random() * Math.PI * 2, r = dist * (0.5 + Math.random() * 0.6); p.className = 'pt'; p.style.cssText = `left:${x}px;top:${y}px;background:${color};box-shadow:0 0 8px ${color}`; fx.append(p);
      p.animate([{ transform: 'translate(-50%,-50%) scale(1)', opacity: 1 }, { transform: `translate(calc(-50% + ${Math.cos(ang) * r}px),calc(-50% + ${Math.sin(ang) * r}px)) scale(.3)`, opacity: 0 }], { duration: 550 + Math.random() * 250, easing: 'cubic-bezier(.2,.7,.3,1)' }).onfinish = () => p.remove(); } };
    const shoot = (from, attr, hits) => new Promise(res => { const [x0, y0] = center(from), [x1, y1] = center(1 - from), n = Math.min(4, Math.max(1, hits || 1) + 1);
      for (let i = 0; i < n; i++) { const p = document.createElement('div'); p.className = 'shot'; p.textContent = GLYPH[attr] || '✨'; p.style.left = `${x0}px`; p.style.top = `${y0}px`; fx.append(p);
        const arc = (i - (n - 1) / 2) * 24, mx = (x1 - x0) / 2 + arc, my = (y1 - y0) / 2 - 40 - Math.abs(arc);
        p.animate([{ transform: 'translate(-50%,-50%) scale(.5)', opacity: 0 }, { opacity: 1, offset: .1 }, { transform: `translate(calc(-50% + ${mx}px),calc(-50% + ${my}px)) scale(1.1)`, offset: .5 }, { transform: `translate(calc(-50% + ${x1 - x0}px),calc(-50% + ${y1 - y0}px)) scale(1.3)`, opacity: 1 }],
          { duration: 420, delay: i * 70, easing: 'ease-in' }).onfinish = () => { p.remove(); if (i === n - 1) res(); }; } });
    const live = () => token === playToken && document.body.contains(stage);
    stage.classList.add('playing');
    for (const e of fresh) {
      if (!live()) return;
      const t = e.target?.side, a = e.actor?.side;
      if (e.kind === 'skill' && a !== undefined) {
        say(e.text);
        const dx = a === 0 ? 26 : -26, dy = a === 0 ? -16 : 12;
        slotEl(a).animate([{ transform: 'translateX(-50%)' }, { transform: `translate(calc(-50% + ${dx}px),${dy}px)` }, { transform: 'translateX(-50%)' }], { duration: 320, easing: 'ease-out' });
        if (e.power && t !== undefined && t !== a) await shoot(a, e.attr, e.hit); else { burst(a, TINT[e.attr] || '#fff', 10, 50); await wait(450); }
      } else if (e.kind === 'damage' && t !== undefined) {
        say(e.text);
        if (e.target.slot === shownSlot[t]) {
          hp[t][e.target.slot] = e.hpAfter ?? hp[t][e.target.slot]; paint(t);
          if (e.reason === 'protect' || !e.amount) { burst(t, '#ffffff', 10, 60); pop(t, '막음', 'calm'); }
          else {
            const big = (e.multiplier || 1) > 1;
            slotEl(t).animate([{ transform: 'translateX(-50%)', filter: 'brightness(1)' }, { transform: 'translateX(calc(-50% - 8px))', filter: 'brightness(2)' }, { transform: 'translateX(calc(-50% + 7px))' }, { transform: 'translateX(-50%)', filter: 'brightness(1)' }], { duration: 380 });
            burst(t, TINT[e.attr] || '#fff', big ? 22 : 12, big ? 110 : 75);
            pop(t, `${e.crit ? '급소! ' : ''}${fmt(e.amount)}`, big ? 'big' : (e.multiplier || 1) < 1 ? 'weak' : '');
            if (big) stage.animate([{ transform: 'translate(0,0)' }, { transform: 'translate(4px,-3px)' }, { transform: 'translate(-4px,3px)' }, { transform: 'translate(0,0)' }], { duration: 260 });
          }
        }
        await wait(560);
      } else if ((e.kind === 'heal' || e.kind === 'revive') && t !== undefined) {
        say(e.text); hp[t][e.target.slot] = e.hpAfter ?? hp[t][e.target.slot];
        if (e.target.slot === shownSlot[t]) { paint(t); burst(t, '#7ce3a0', 14, 60); pop(t, `+${fmt(e.amount || 0)}`, 'heal'); }
        await wait(520);
      } else if (e.kind === 'faint' && t !== undefined) {
        say(e.text);
        if (e.target.slot === shownSlot[t]) { burst(t, '#ffffff', 18, 90); slotEl(t).animate([{ opacity: 1, filter: 'brightness(1)' }, { opacity: 1, filter: 'brightness(0) invert(1)', offset: .25 }, { opacity: 0, filter: 'brightness(0) invert(1) blur(3px)' }], { duration: 900, fill: 'forwards' }); }
        await wait(950);
      } else if (e.kind === 'switch' && t !== undefined) {
        say(e.text); slotEl(t).getAnimations().forEach(x => x.cancel());
        shownSlot[t] = e.target.slot; slotEl(t).classList.remove('down'); show(t); paint(t);
        slotEl(t).animate([{ opacity: 0, transform: 'translateX(-50%) scale(.85)' }, { opacity: 1, transform: 'translateX(-50%) scale(1)' }], { duration: 380, easing: 'ease-out' });
        await wait(520);
      } else if ((e.kind === 'item' || e.kind === 'augment' || e.kind === 'status') && e.text) {
        say(e.text); banner.textContent = e.text; banner.hidden = false;
        banner.animate([{ opacity: 0, transform: 'translate(-50%,8px)' }, { opacity: 1, transform: 'translate(-50%,0)' }], { duration: 220 });
        await wait(e.kind === 'status' ? 650 : 900); banner.hidden = true;
      }
    }
    if (!live()) return;
    stage.classList.remove('playing');
    // 마지막은 서버가 준 최종 상태 그대로
    for (const side of [0, 1]) { if (shownSlot[side] !== team(side).active) { shownSlot[side] = team(side).active; show(side); } slotEl(side).getAnimations().forEach(x => x.cancel()); slotEl(side).classList.toggle('down', active(team(side)).hp <= 0); hpPanel(hpEl(side), active(team(side)), side === 0); }
    say((s.log || []).slice(-2).join(' '));
  }
  function hpPanel(el, f, mine) {
    const pct = f.maxHp ? Math.max(0, Math.round(f.hp / f.maxHp * 100)) : 0, st = Object.keys(f.status || {}).filter(k => f.status[k]);
    el.innerHTML = `<div class="hp-top"><b>${esc(f.name)}</b><span class="lv">Lv ${fmt(f.level)}</span><button class="attr" type="button" data-chart aria-label="${esc(f.attr)} 속성 · 상성표 열기">${esc(f.attr)}</button></div>
      <div class="hpbar" role="meter" aria-valuemin="0" aria-valuemax="${f.maxHp}" aria-valuenow="${f.hp}" aria-label="${mine ? '내' : '상대'} 체력"><i class="${pct <= 25 ? 'low' : pct <= 50 ? 'mid' : ''}" style="width:${pct}%"></i></div>
      <div class="hp-bot"><span>${st.map(k => `<em>${esc(STATUS[k] || k)}</em>`).join('')}${f.shield > 0 ? `<em>보호막 ${fmt(f.shield)}</em>` : ''}</span><span class="num">${fmt(f.hp)} / ${fmt(f.maxHp)}</span></div>`;
    $('[data-chart]', el).onclick = e => typeChart(e.currentTarget);
  }
  const btn = (attrs, label, sub = '', cls = '') => `<button type="button" class="cbtn ${cls}" ${attrs}><b>${label}</b>${sub ? `<small>${sub}</small>` : ''}</button>`;
  function pane(s) {
    const body = $('.cmd-body', view), title = $('[data-pane-title]', view), t = me(s), f = active(t), legal = t.legal || [], act = canAct();
    const legalSkill = id => b => legal.some(a => a.kind === 'skill' && a.skillId === id);
    const can = { skill: id => act && s.phase === 'action' && (isPve() || legal.length === 0 || legalSkill(id)()) , switchTo: i => act && t.fighters[i] && i !== t.active && t.fighters[i].hp > 0 && (s.phase !== 'action' || isPve() || legal.some(a => a.kind === 'switch' && a.to === i)) };
    if (s.phase === 'over') {
      const r = s.result || (S.battle.room && S.battle.room.result) || {};
      const won = r.won ?? (s.winner === 0), draw0 = s.winner === null && r.winner == null && !r.won;
      title.textContent = '전투 결과';
      body.innerHTML = `<div class="result"><h2>${draw0 ? '무승부' : won ? '승리' : '패배'}</h2><div class="info">
        ${r.left ? '<p>전투에서 물러났어요.</p>' : ''}${r.xp ? `<p>경험치 +${fmt(r.xp)}</p>` : ''}${(r.ups || []).map(u => `<p>${esc(u)}</p>`).join('')}
        ${(r.growth || []).filter(g => g.learn?.length || g.promo?.length || g.evolve?.length).map(g => `<p class="grow-note">✨ ${esc(g.name)}: ${g.evolve?.length ? '승진할 수 있어요' : '새 기술을 배울 수 있어요'} <a href="#team?u=${encodeURIComponent(g.key)}" data-done>성장하기</a></p>`).join('')}
        ${r.dialog?.length || r.chapterClear?.length ? '<button class="btn ghost sm" type="button" data-after>이야기 보기</button>' : ''}
        ${r.rank ? `<p>${esc(r.rank.tier?.name || r.rank.tier || '')} · LP ${r.rank.lpDelta >= 0 ? '+' : ''}${fmt(r.rank.lpDelta)}</p>` : ''}
        ${r.reason && !['BATTLE'].includes(r.reason) ? `<p class="muted">${esc({ SURRENDER: '항복으로 끝났어요.', TIMEOUT: '시간 초과로 끝났어요.', DOUBLE_TIMEOUT: '양쪽 모두 시간 초과로 끝났어요.', SERVER_ABORT: '서버 사정으로 중단됐어요.' }[r.reason] || r.reason)}</p>` : ''}
        </div><div class="row"><a class="btn primary lg" href="#home" data-done>항구로</a><a class="btn ghost lg" href="#play" data-done>플레이</a></div></div>`;
      $$('[data-done]', body).forEach(a => a.addEventListener('click', () => { S.battle = null; }));
      // 스토리: 승리 뒤 이야기(after)와 장 클리어 이야기. 처음 결과를 볼 때 한 번 자동으로 열고, 버튼으로 다시 볼 수 있음
      const story = [...(r.dialog || []), ...(r.chapterClear || [])], ab = $('[data-after]', body);
      if (ab) ab.onclick = () => S.dialog(story, '이야기');
      if (story.length && !S.battle.afterShown) { S.battle.afterShown = true; setTimeout(() => S.dialog(story, '이야기'), 300); }
      if (S.battle.kind === 'native' && s.mode === 'story') $('.row', body).insertAdjacentHTML('beforeend', '<a class="btn ghost lg" href="#story" data-done>스토리</a>'), $$('[data-done]', body).forEach(a => a.addEventListener('click', () => { S.battle = null; }));
      return;
    }
    if (!act) {
      title.textContent = ui.sending ? '보내는 중' : '상대를 기다리는 중';
      body.innerHTML = `<div class="waiting" role="status"><span class="spin"></span>${ui.sending ? '선택을 보내고 있어요…' : S.battle.room?.mySubmitted ? '선택을 보냈어요. 상대 선택을 기다려요.' : '서버 결과를 기다려요.'}</div>`;
      return;
    }
    if (s.phase === 'choose' && t.attributeSelection) {
      const a = t.attributeSelection;
      const valid = a.kind === 'own-attribute' && a.targetKey === f.key && typeof a.selectionId === 'string' && a.selectionId.length > 0 && typeof a.phaseId === 'string' && a.phaseId.length > 0
        && Array.isArray(a.attributes) && a.attributes.length === 10 && new Set(a.attributes).size === 10 && a.attributes.every(k => Object.hasOwn(ATTR, k));
      title.textContent = '자유자재 · 내 선두 속성';
      if (!valid) { body.innerHTML = '<p class="err">속성 선택 정보를 다시 받아야 해요.</p>'; return; }
      body.innerHTML = `<div class="attribute-picker"><p>상대 선두 ${esc(active(foe(s)).name)} · ${esc(ATTR[a.opponentAttribute] || a.opponentAttribute)}</p><p>내 선두 <b>${esc(f.name)}</b>의 속성을 골라 확정해 주세요. 이 전투에 유지돼요.</p>
        <div class="attribute-grid" role="group" aria-label="내 선두 속성">${a.attributes.map(k => btn(`data-attribute="${k}" aria-pressed="${ui.attribute === k}"`, esc(ATTR[k]))).join('')}</div>
        <p aria-live="polite">${ui.attribute ? `${esc(ATTR[ui.attribute])} 선택 · 아직 확정하지 않았어요` : '선택한 속성 없음'}</p>
        <div class="grid2">${btn(`data-attribute-confirm ${!ui.attribute ? 'disabled' : ''}`, '속성 확정', '', 'primary')}${btn('data-attribute-cancel', '선택 취소')}</div></div>`;
      body.onclick = e => {
        const pick = e.target.closest('[data-attribute]');
        if (pick && !pick.disabled && a.attributes.includes(pick.dataset.attribute)) { ui.attribute = pick.dataset.attribute; pane(s); $(`[data-attribute="${ui.attribute}"]`, body)?.focus(); return; }
        if (e.target.closest('[data-attribute-cancel]')) { ui.attribute = null; pane(s); $('[data-attribute-cancel]', body)?.focus(); return; }
        if (e.target.closest('[data-attribute-confirm]') && a.attributes.includes(ui.attribute) && canAct())
          send('choose', { freestyle: { selectionId: a.selectionId, phaseId: a.phaseId, targetKey: a.targetKey, attribute: ui.attribute } });
      };
      return;
    }
    // 강제 교체(기절 등): 뒤로 없음, Esc도 막음
    if (t.needSwitch && s.phase === 'choose') {
      title.textContent = '교체할 대원 고르기';
      body.innerHTML = `<div class="list">${t.fighters.map((x, i) => btn(`data-sw="${i}" ${can.switchTo(i) ? '' : 'disabled'} aria-pressed="${ui.sw === i}"`, esc(x.name), x.hp <= 0 ? '기절' : i === t.active ? '출전 중' : `체력 ${fmt(x.hp)} / ${fmt(x.maxHp)}`)).join('')}</div>
        <p class="hint">지금 대원이 쓰러졌어요. 다음 대원을 골라야 해요.</p><div class="row">${btn(`data-go ${ui.sw == null ? 'disabled' : ''}`, '교체', '', 'primary')}</div>`;
      body.onclick = e => { const x = e.target.closest('[data-sw]'); if (x && !x.disabled) { ui.sw = Number(x.dataset.sw); pane(s); } if (e.target.closest('[data-go]') && ui.sw != null) send('choose', { ...(ui.aug ? { augment: ui.aug } : {}), switchTo: ui.sw }); };
      return;
    }
    if (s.phase === 'choose') {   // 증강체를 고른 뒤(강제 교체 없음)
      title.textContent = '증강체'; body.innerHTML = `<div class="waiting">${btn('data-go', '선택 보내기', '', 'primary')}</div>`;
      body.onclick = e => { if (e.target.closest('[data-go]')) send('choose', { augment: ui.aug }); };
      return;
    }
    const back = btn('data-back', '뒤로');
    if (ui.pane === 'root') {
      title.textContent = '명령';
      body.innerHTML = `<div class="grid2 root">${btn('data-p="attack"', '<img src="/site/a/b-icon-attack.webp" alt="" width="40" height="40">공격', '', 'attack')}${btn('data-p="item"', '<img src="/site/a/b-icon-item.webp" alt="" width="40" height="40">아이템')}${btn('data-p="switch"', '<img src="/site/a/b-icon-switch.webp" alt="" width="40" height="40">교체')}${btn('data-p="leave"', `<img src="/site/a/b-icon-surrender.webp" alt="" width="40" height="40">${isPve() ? '도망' : '항복'}`)}</div>`;
      body.onclick = e => { const x = e.target.closest('[data-p]'); if (x) { ui.pane = x.dataset.p; pane(s); $('.cmd-body button', view)?.focus(); } };
    } else if (ui.pane === 'attack') {
      title.textContent = '공격';
      const skills = f.skills.filter(k => !String(k.id).startsWith('combo-')), combo = f.skills.find(k => String(k.id).startsWith('combo-'));
      body.innerHTML = `<div class="grid2 skills">${[0, 1, 2, 3].map(i => { const k = skills[i]; if (!k) return '<div class="cbtn none"><b>빈 기술 칸</b></div>';
          const mult = k.preview?.multiplier, why = k.reason || (!can.skill(k.id) ? '지금은 쓸 수 없어요' : '');
          return btn(`data-skill="${esc(k.id)}" ${why ? 'disabled' : ''}`, esc(k.name), esc([k.preview?.power ? `위력 ${k.preview.power}` : '', mult > 1 ? '효과 좋음' : mult < 1 ? '효과 약함' : '', why || k.summary || ''].filter(Boolean).join(' · ')), 'skill'); }).join('')}</div>
        <div class="grid2 sub">${combo ? btn(`data-skill="${esc(combo.id)}" ${combo.reason || !can.skill(combo.id) ? 'disabled' : ''}`, `합동기 · ${esc(combo.name)}`, esc(combo.reason || combo.summary || '')) : btn('disabled', '합동기', '같은 계열 동료가 있어야 해요')}${back}</div>`;
      body.onclick = e => { const x = e.target.closest('[data-skill]'); if (x && !x.disabled) send('action', { kind: 'skill', skillId: x.dataset.skill }); if (e.target.closest('[data-back]')) { ui.pane = 'root'; pane(s); } };
    } else if (ui.pane === 'item') {
      title.textContent = '아이템';
      const inv = new Map((s.inventory || []).map(x => [x.id, x.count]));
      const items = (s.items || []).filter(x => (inv.get(x.id) || 0) > 0 || x.id === ui.item);
      const it = items.find(x => x.id === ui.item);
      const needTarget = it && it.revive, targets = needTarget ? t.fighters.map((x, i) => ({ x, i })).filter(o => o.x.hp <= 0) : [];
      body.innerHTML = `${items.length ? `<div class="grid2 items">${items.slice(0, 4).map(x => btn(`data-item="${esc(x.id)}" aria-pressed="${ui.item === x.id}"`, `<img src="/duel/icons/${esc(x.id)}.png" alt="" width="32" height="32" onerror="this.remove()">${esc(x.name)}`, `${fmt(inv.get(x.id) || 0)}개${isPve() ? '(전투 시작 때)' : ''}`)).join('')}</div>` : '<p class="hint">쓸 수 있는 아이템이 없어요.</p>'}
        ${it ? `<div class="pick"><b>${esc(it.name)}</b><p>${esc(it.desc)}</p>${needTarget ? `<div class="row">${targets.map(o => btn(`data-target="${o.i}" aria-pressed="${ui.target === o.i}"`, esc(o.x.name), '기절')).join('') || '<p class="hint">다시 일으킬 대원이 없어요.</p>'}</div>` : ''}
          <p class="why">${esc(it.reason || `쓰면 이번 턴 행동을 써요 · 전투당 ${fmt(s.itemLimit)}개까지 (사용 ${fmt(t.itemsUsed || 0)})`)}</p></div>` : ''}
        <div class="grid2 sub">${btn(`data-use ${!it || it.reason || (needTarget && ui.target == null) ? 'disabled' : ''}`, '사용', '', 'primary')}${back}</div>`;
      body.onclick = e => {
        const x = e.target.closest('[data-item]'); if (x) { ui.item = x.dataset.item; ui.target = null; pane(s); return; }
        const tg = e.target.closest('[data-target]'); if (tg) { ui.target = Number(tg.dataset.target); pane(s); return; }
        if (e.target.closest('[data-use]') && it && !it.reason) send('action', { kind: 'item', item: it.id, ...(needTarget ? { target: ui.target } : {}) });
        if (e.target.closest('[data-back]')) { ui.pane = 'root'; ui.item = null; pane(s); }
      };
    } else if (ui.pane === 'switch') {
      title.textContent = '교체';
      body.innerHTML = `<div class="list">${t.fighters.map((x, i) => btn(`data-sw="${i}" ${can.switchTo(i) ? '' : 'disabled'} aria-pressed="${ui.sw === i}"`, esc(x.name), x.hp <= 0 ? '기절' : i === t.active ? '출전 중' : `체력 ${fmt(x.hp)} / ${fmt(x.maxHp)} · ${esc(x.attr)}`)).join('')}</div>
        <p class="hint">교체하면 이번 턴 행동을 써요.</p><div class="grid2 sub">${btn(`data-go ${ui.sw == null ? 'disabled' : ''}`, '교체', '', 'primary')}${back}</div>`;
      body.onclick = e => { const x = e.target.closest('[data-sw]'); if (x && !x.disabled) { ui.sw = Number(x.dataset.sw); pane(s); return; } if (e.target.closest('[data-go]') && ui.sw != null) send('action', { kind: 'switch', to: ui.sw }); if (e.target.closest('[data-back]')) { ui.pane = 'root'; ui.sw = null; pane(s); } };
    } else if (ui.pane === 'leave') {
      title.textContent = isPve() ? '도망' : '항복';
      const rank = S.battle.mode === 'rank' || s.mode === 'rank';
      body.innerHTML = `<div class="pick"><b>${isPve() ? '전투에서 물러날까요?' : '항복할까요?'}</b><p>${isPve() ? '전투가 끝나고 패배로 처리돼요.' : rank ? '상대 승리로 끝나고 랭크 결과에 반영돼요.' : '상대 승리로 끝나요.'}</p></div>
        <div class="grid2 sub">${btn('data-yes', isPve() ? '물러나기' : '항복하기', '', 'danger')}${back}</div>`;
      body.onclick = e => { if (e.target.closest('[data-yes]')) send('leave', true); if (e.target.closest('[data-back]')) { ui.pane = 'root'; pane(s); } };
    }
  }
  // Esc: 일반 패널은 뒤로, 강제 교체·모달 중에는 무시
  addEventListener('keydown', e => {
    if (e.key !== 'Escape' || !location.hash.startsWith('#battle') || !$('#chart').hidden) return;
    const s = S.battle?.snap; if (s && me(s).attributeSelection) { ui.attribute = null; pane(s); $('[data-attribute]', view)?.focus(); return; } if (!s || me(s).needSwitch || ui.pane === 'root') return;
    ui.pane = 'root'; pane(s);
  });

  // ── 증강체: 후보 3개를 동시에, 카드 전체가 선택 대상. 확정은 서버 접수 뒤 ──
  function drawAugment(s) {
    const t = me(s), offers = t.offers;
    let pick = null;
    view.innerHTML = `<section class="augment"><div class="a-head"><h1>증강체 선택${TIER[offers[0]?.tier] ? ` · ${TIER[offers[0].tier]}` : ''}</h1><p class="muted">함께 출정할 장비를 하나 골라요.</p><span class="timer" data-timer hidden></span></div>
      <div class="a-cards" role="radiogroup" aria-label="증강체 후보">${offers.map((o, i) => `<button type="button" class="a-card" data-tier="${esc(o.tier || '')}" role="radio" aria-checked="false" data-a="${i}">
        <img src="/duel/icons/${esc(o.icon)}.png" alt="" width="96" height="96" onerror="this.style.visibility='hidden'"><span class="a-txt"><b>${esc(o.name)}</b>${TIER[o.tier] ? `<small class="tier-label">${TIER[o.tier]}</small>` : ''}<span>${esc(o.desc)}</span></span></button>`).join('')}</div>
      <div class="a-rerolls">${offers.map((o, i) => `<button class="btn ghost sm" type="button" data-r="${i}" ${o.rerolled ? 'disabled' : ''}>${o.rerolled ? '바꾼 후보' : `${i + 1}번 후보 바꾸기`}</button>`).join('')}</div>
      <div class="cta"><button class="btn primary lg wide" type="button" data-ok disabled>선택 확정</button><p class="err" role="alert">${esc(ui.note)}</p></div></section>`;
    const ok = $('[data-ok]', view);
    $('.a-cards', view).onclick = e => { const c = e.target.closest('[data-a]'); if (!c) return; pick = Number(c.dataset.a); $$('.a-card', view).forEach(x => x.setAttribute('aria-checked', String(x === c))); ok.disabled = false; };
    $('.a-cards', view).onkeydown = e => { if (!['ArrowRight', 'ArrowLeft', 'ArrowDown', 'ArrowUp'].includes(e.key)) return; e.preventDefault(); const cs = $$('.a-card', view), i = cs.indexOf(document.activeElement), n = cs[(i + (e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : cs.length - 1)) % cs.length]; n.focus(); n.click(); };
    $('.a-rerolls', view).onclick = e => { const r = e.target.closest('[data-r]'); if (r && !r.disabled) send('reroll', Number(r.dataset.r)); };
    ok.onclick = () => {
      if (pick == null) return;
      const id = offers[pick].id;
      if (t.needSwitch) { ui.aug = id; draw(); return; }   // 강제 교체가 같이 필요하면 고른 뒤 함께 보냄
      ok.disabled = true; ok.textContent = '보내는 중…'; ok.setAttribute('aria-busy', 'true');
      send('choose', { augment: id });
    };
    countdown($('[data-timer]', view), deadline(), refetchAfterExpiry);
  }

  // ── 실시간 방: 수락 · 준비(편성 부품 재사용) · 매칭 실패 ──
  function drawRoom(r) {
    const ai = r.opponentKind === 'AI';
    if (r.phase === 'accept') {
      S.title('매칭', ''); app.dataset.place = '';
      view.innerHTML = `<section class="match"><div class="m-card"><h1>${MODE[r.mode]} · 상대를 찾았어요</h1>${ai ? '<p class="ai-note"><b class="ai">AI 상대</b> 사람 상대가 없어 서버가 AI 상대를 정했어요. 일반 MMR에 반영되지 않아요.</p>' : ''}
        <p class="timer" data-timer hidden></p><div class="m-acts">${r.mySubmitted ? '<p role="status">수락했어요. 상대를 기다려요.</p>' : '<button class="btn primary lg" type="button" data-yes>수락</button><button class="btn ghost lg" type="button" data-no>거절</button>'}</div><p class="err" role="alert">${esc(ui.note)}</p></div></section>`;
      const yes = $('[data-yes]', view), no = $('[data-no]', view);
      if (yes) yes.onclick = () => { yes.disabled = no.disabled = true; send('accept', true); };
      if (no) no.onclick = () => { yes.disabled = no.disabled = true; send('accept', false); };
      countdown($('[data-timer]', view), r.canSubmit && !r.mySubmitted ? r.deadline : null, refetchAfterExpiry);
      return;
    }
    if (r.phase === 'prepare') {
      S.title('전투 편성', ''); app.dataset.place = 'team'; app.dataset.screen = 'inner';
      const p = r.preparation, own = S.state?.units || [];
      // 후보: 서버가 준 목록(available). 일반전은 개체 키(o…), 랭크전은 종 ID. 기술·레벨은 내 정보에서 찾아 보여 준다
      // 일반전 후보 id는 개체 키(o…, characterId 따로), 랭크전은 종 ID. 서버에 보낼 값은 항상 후보 id
      const cands = (p.available || []).map(a => { const sp = a.characterId || a.id, mine = own.find(u => u.key === a.id) || own.find(u => u.id === sp);
        return { ...(mine || {}), key: a.id, id: sp, name: a.name, level: a.level ?? mine?.level ?? '', stage: a.stage ?? mine?.stage ?? 0, attr: mine?.attr || '' }; });
      const board = S.teamBoard({ mode: 'ready', team: (p.selectedIds || []).slice(0, 3), candidates: cands, focus: (p.selectedIds || [])[0], busy: !r.canSubmit, submit: keys => send('prepare', { team: keys, ready: true }) });
      const timer = $('[data-timer]', view);
      if (r.mySubmitted) { $('[data-go]', view).disabled = true; $('[data-why]', view).textContent = '준비 완료. 상대를 기다려요.'; }
      if (ai) $('.cta', view).insertAdjacentHTML('afterbegin', '<p class="ai-note"><b class="ai">AI 상대</b></p>');
      countdown(timer, r.canSubmit && !r.mySubmitted ? r.deadline : null, refetchAfterExpiry);
      void board; return;
    }
    // 수락 거절·시간 초과 등으로 전투 없이 끝남
    S.title('매칭', ''); app.dataset.place = '';
    const why = { MATCH_DECLINED: '대전이 성사되지 않았어요(거절).', MATCH_TIMEOUT: '수락 시간이 끝났어요.' }[r.result?.reason] || '대전이 끝났어요.';
    view.innerHTML = `<section class="match"><div class="m-card"><h1>${esc(why)}</h1>${r.result?.requeued ? '<p>상대가 응답하지 않아 다시 매칭 대기열로 돌아갔어요.</p>' : ''}
      <div class="m-acts">${r.result?.requeued ? `<a class="btn primary lg" href="#match?mode=${r.mode}">매칭 화면으로</a>` : ''}<a class="btn ghost lg" href="#play" data-done>플레이로</a></div></div></section>`;
    $$('[data-done]', view).forEach(a => a.addEventListener('click', () => { S.battle = null; }));
  }

  // ── 상성표: 속성 아이콘으로만 여는 정보 창. 닫기·포커스 복귀, 배경 입력 막음, 서버 시간은 계속 흐름 ──
  const chart = document.createElement('div');
  chart.id = 'chart'; chart.className = 'sheet-wrap'; chart.hidden = true;
  chart.innerHTML = '<div class="sheet wide" role="dialog" aria-modal="true" aria-labelledby="chart-title"><div class="sheet-head"><h2 id="chart-title">속성 상성표</h2><button class="icon-btn close" type="button" aria-label="닫기">×</button></div><div class="sheet-body"></div></div>';
  document.body.append(chart);
  let chartBack = null;
  const closeChart = () => { chart.hidden = true; app.inert = false; if (chartBack) chartBack.focus(); };
  chart.addEventListener('click', e => { if (e.target === chart || e.target.closest('.close')) closeChart(); });
  chart.addEventListener('keydown', e => { if (e.key === 'Escape') { e.stopPropagation(); closeChart(); } if (e.key === 'Tab') { e.preventDefault(); $('.close', chart).focus(); } });
  function typeChart(from) {
    const tc = S.state?.typeChart, at = S.state?.attributes;
    if (!tc) { S.toast('상성표 정보를 받지 못했어요.'); return; }
    const name = k => at?.[k]?.name || k;
    $('.sheet-body', chart).innerHTML = `<p class="muted">공격하는 속성이 왼쪽, 그 속성에 약한 상대가 오른쪽이에요. 강한 상대에게는 피해가 커져요.</p>
      <table class="chart"><thead><tr><th scope="col">공격 속성</th><th scope="col">강한 상대</th></tr></thead><tbody>${Object.entries(tc).map(([k, v]) => `<tr><th scope="row">${esc(name(k))}</th><td>${v.strong.map((x, i) => `<b>${esc(name(x))}</b><small>${esc(v.why?.[i] || '')}</small>`).join('')}</td></tr>`).join('')}</tbody></table>`;
    chartBack = from; chart.hidden = false; app.inert = true; $('.close', chart).focus();
  }

  Object.assign(S.routes, { match, battle });
})();
