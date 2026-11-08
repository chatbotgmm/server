// 갈매미맨 랜덤 디펜스 2판: 화면·입력(규칙은 engine.js). 논리 좌표 540×960을 화면 크기에 맞춰 그린다.
// 조작(사용자 요청 2026-11-08): 갈매미는 끌어서만 옮긴다 · 누르면 고르기 · 빈 곳을 끌면 여러 마리 고르기(고른 무리는 하나를 끌면 함께) · 다른 곳을 누르면 고르기 풀림.
import * as E from './engine.js?v=df1dc3b8f3a1';

const { W, H, FIELD } = E;
const ART = '/game60/assets';
const C = {
  bg: '#07131c', panel: 'rgba(16,36,51,.95)', header: '#132c3b', text: '#f5ead0', muted: '#b0bec4',
  gold: '#d9bb7f', goldEdge: '#f3d89e', edge: '#917a4c', line: '#5b675d', ink: '#192e39', bad: '#ff9a8a', good: '#9fe3a8',
  lane: 'rgba(214,190,140,.22)', laneEdge: 'rgba(243,216,158,.35)', phys: '#ffb36b', mag: '#8fc8ff',
};
const GRADE_COLOR = { COMMON: '#9aa5ad', UNCOMMON: '#62c370', RARE: '#4d9de0', EPIC: '#a77be8', LEGEND: '#f0a03c', HIDDEN: '#3fd0c0', LIMITED: '#ef5a5a', TRANSCEND: '#ff8fc7', IMMORTAL: '#f3d36b', ETERNAL: '#ffffff' };
const GOAL_COLOR = ['#4fd1e8', '#f07ad8', '#a6e05a'];
const KIND_NAME = { 일반: '일반', 갑옷: '갑옷(방어력↑)', 결계: '결계(마법방어↑)', boss: '보스' };
const FONT = '"Workbench","Noto Sans KR","Apple SD Gothic Neo","Malgun Gothic",sans-serif';
const BEST_KEY = 'gmm-defense-best';
const MUSIC_KEY = 'gmm-defense-music';
const VOLUME_KEY = 'gmm-defense-volume';
// 배경음악(사용자 제공 곡을 mp3로 바꿈). 주소의 ?v=는 scripts/web/stamp-defense.py가 붙인다
const BGM_URL = '/defense/bgm.mp3?v=f7211afdc4a1';
const UNIT_R = 24;   // 갈매미 누르기 반지름(커먼 기준, 등급 크기만큼 커짐)
// 등급별 크기(사용자 요청 2026-11-08: 상위일수록 크게 — 보상 느낌). 커먼·언커먼·레어·에픽·전설·히든·제한·초월·불멸·영원
const GRADE_SIZE = [1, 1.06, 1.13, 1.22, 1.34, 1.4, 1.5, 1.6, 1.7, 1.82];
const usz = (id) => GRADE_SIZE[E.tierOf(id)] ?? 1;
const BASE_SPEED = 1.5;   // 기본 게임 속도(사용자 요청 2026-11-08: 더 빠르게). 시간이 통째로 빨라져 난이도는 같음

// ── 화면 준비 ──
const cv = document.getElementById('game');
const ctx = cv.getContext('2d');
let scale = 1, dpr = 1;
// 화면 배치: 휴대폰·태블릿 세로는 540×960 한 줄, PC·태블릿 가로는 1100×960(왼쪽 게임, 오른쪽 창)
const SIDE_X = 560;          // 가로 배치에서 오른쪽 창이 시작하는 곳
let landscape = false, VW = W, VH = H;
const PEER_H = 208;          // 세로 화면 같이하기: 아래에 붙는 친구 판 줄 높이
let stripOn = false;         // 친구 판 줄을 붙였는가(frame이 정함)
let OX = 0;                  // 지금 그리는 곳의 가로 위치(오른쪽 창을 그릴 때 누르기 영역에 더함)
function fit() {
  dpr = Math.min(window.devicePixelRatio || 1, 3);
  landscape = window.innerWidth / window.innerHeight > 0.9;
  VW = landscape ? SIDE_X + W : W;
  VH = stripOn && !landscape ? H + PEER_H : H;
  scale = Math.min(window.innerWidth / VW, window.innerHeight / VH);
  cv.style.width = `${Math.floor(VW * scale)}px`;
  cv.style.height = `${Math.floor(VH * scale)}px`;
  cv.width = Math.floor(VW * scale * dpr);
  cv.height = Math.floor(VH * scale * dpr);
}
window.addEventListener('resize', fit);
fit();

// ── 그림 ──
const imgs = {};
function img(src) {
  if (!imgs[src]) { const i = new Image(); i.decoding = 'async'; i.src = src; imgs[src] = i; }
  const i = imgs[src];
  return i.complete && i.naturalWidth ? i : null;
}
const unitImg = (id) => img(`${ART}/units/${id}-front.webp`);
const shades = {};
function shade(id) {
  if (shades[id]) return shades[id];
  const i = unitImg(id);
  if (!i) return null;
  const c = document.createElement('canvas');
  c.width = 96; c.height = Math.round(96 * i.naturalHeight / i.naturalWidth);
  const x = c.getContext('2d');
  x.drawImage(i, 0, 0, c.width, c.height);
  x.globalCompositeOperation = 'source-atop';
  x.fillStyle = 'rgba(42,20,66,.92)';
  x.fillRect(0, 0, c.width, c.height);
  shades[id] = c;
  return c;
}
function drawFit(im, x, y, w, h, alpha = 1) {
  if (!im) return;
  const iw = im.naturalWidth || im.width, ih = im.naturalHeight || im.height;
  const s = Math.min(w / iw, h / ih), dw = iw * s, dh = ih * s;
  ctx.globalAlpha = alpha;
  ctx.drawImage(im, x + (w - dw) / 2, y + h - dh, dw, dh);
  ctx.globalAlpha = 1;
}

// ── 그리기 도구 ──
function rr(x, y, w, h, r) { ctx.beginPath(); if (ctx.roundRect) ctx.roundRect(x, y, w, h, r); else ctx.rect(x, y, w, h); }
function box(x, y, w, h, fill, stroke = null, r = 8, lw = 1.5) {
  rr(x, y, w, h, r);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
  if (stroke) { ctx.lineWidth = lw; ctx.strokeStyle = stroke; ctx.stroke(); }
}
function text(s, x, y, size = 18, color = C.text, align = 'left', base = 'middle') {
  ctx.font = `${size}px ${FONT}`; ctx.fillStyle = color; ctx.textAlign = align; ctx.textBaseline = base;
  ctx.fillText(s, x, y);
}
function fitText(s, x, y, maxW, size = 18, color = C.text, align = 'left') {
  let sz = size;
  ctx.font = `${sz}px ${FONT}`;
  while (sz > 11 && ctx.measureText(s).width > maxW) { sz -= 1; ctx.font = `${sz}px ${FONT}`; }
  text(s, x, y, sz, color, align);
}
function wrap(s, x, y, maxW, size = 15, color = C.muted, lineH = size * 1.45) {
  ctx.font = `${size}px ${FONT}`;
  let line = '', yy = y;
  for (const w of s.split(' ')) {
    const t = line ? `${line} ${w}` : w;
    if (ctx.measureText(t).width > maxW && line) { text(line, x, yy, size, color, 'left', 'top'); line = w; yy += lineH; } else line = t;
  }
  if (line) { text(line, x, yy, size, color, 'left', 'top'); yy += lineH; }
  return yy - y;
}

let hits = [];
// fn은 그 창 안의 좌표를 받는다(가로 화면 오른쪽 창이면 SIDE_X를 뺀 값). drag면 누른 채 끌어도 계속 불림(볼륨 막대)
// 2026-11-08: 전에는 화면 전체 좌표를 넘겨 가로 화면에서 볼륨 막대가 누른 곳과 다르게(늘 끝까지) 움직였음
const hit = (x, y, w, h, fn, drag = false) => { const ox = OX; hits.push({ x: x + ox, y, w, h, fn: (p) => fn({ x: p.x - ox, y: p.y }), drag }); };
const hitAt = (p) => { for (let k = hits.length - 1; k >= 0; k--) { const h = hits[k]; if (p.x >= h.x && p.y >= h.y && p.x < h.x + h.w && p.y < h.y + h.h) return h; } return null; };
function button(x, y, w, h, label, fn, style = 'normal', enabled = true, sub = '', subColor = null) {
  const S = {
    primary: ['#f3d89e', '#c9a463', C.ink, '#fff3cf'],
    normal: ['#163447', null, C.text, C.gold],
    quiet: ['rgba(11,30,42,.6)', null, C.muted, C.line],
    danger: ['#5a2418', null, '#ffd9cf', '#b4482a'],
  }[style];
  if (style === 'primary' && enabled) { const g = ctx.createLinearGradient(0, y, 0, y + h); g.addColorStop(0, S[0]); g.addColorStop(1, S[1]); box(x, y, w, h, g, S[3], 10, 2); }
  else box(x, y, w, h, enabled ? S[0] : '#1b2a33', enabled ? S[3] : '#33434d', 10, 1.5);
  const col = enabled ? S[2] : '#6f7f88';
  if (sub) { fitText(label, x + w / 2, y + h / 2 - 10, w - 12, 17, col, 'center'); fitText(sub, x + w / 2, y + h / 2 + 12, w - 12, 14, enabled ? (subColor ?? (style === 'primary' ? '#3d2f1a' : C.muted)) : '#5b6a73', 'center'); }
  else fitText(label, x + w / 2, y + h / 2, w - 12, 17, col, 'center');
  if (enabled) hit(x, y, w, h, fn);
}
function chip(s, x, y, color, size = 14) {
  ctx.font = `${size}px ${FONT}`;
  const w = ctx.measureText(s).width + 14;
  box(x, y, w, size + 8, 'rgba(0,0,0,.35)', color, 6, 1.5);
  text(s, x + w / 2, y + (size + 8) / 2 + 0.5, size, color, 'center');
  return w;
}

// ── 상태 ──
let G = null, scene = 'title', modal = null, speed = 1;
let selected = [];   // 고른 갈매미 uid
let toast = { msg: '', until: 0 };
let lastNews = 0;
// 기기 저장(이어하기·같이하기 다시 접속·처음 안내). 막힌 브라우저에서도 게임은 진행
const ls = { get(k) { try { return localStorage.getItem(k); } catch { return null; } }, set(k, v) { try { localStorage.setItem(k, v); } catch { /* 저장 못 해도 진행 */ } }, del(k) { try { localStorage.removeItem(k); } catch { /* 진행 */ } } };
const SAVE_KEY = 'gmm-defense-save', SAVE_META_KEY = 'gmm-defense-save-meta', ROOM_KEY = 'gmm-defense-room', ROOM_SAVE_KEY = 'gmm-defense-room-save', TUT_KEY = 'gmm-defense-tutorial';
const bestKey = (mode) => (mode && mode !== 'normal' ? `${BEST_KEY}-${mode}` : BEST_KEY);
const bestOf = (mode) => Number(ls.get(bestKey(mode))) || 0;
const bestText = (b) => (b ? (b > E.RULES.waves ? '클리어' : `${b}웨이브`) : '아직 없음');
let best = bestOf('normal');
let resumeMeta = null;   // 이어할 판 {wave, mode}
try { resumeMeta = JSON.parse(ls.get(SAVE_META_KEY) ?? 'null'); } catch { resumeMeta = null; }
// ── 배경음악: 누르기(시작하기·♪) 뒤에만 재생(브라우저 자동 재생 막힘), 일시정지·다른 앱이면 멈춤 ──
const music = { on: true, vol: 0.45, el: null };
try { music.on = localStorage.getItem(MUSIC_KEY) !== 'off'; const v = Number(localStorage.getItem(VOLUME_KEY)); if (localStorage.getItem(VOLUME_KEY) !== null && v >= 0 && v <= 1) music.vol = v; } catch { /* 기본값 */ }
function musicEl() {
  if (!music.el) { music.el = new Audio(BGM_URL); music.el.loop = true; music.el.volume = music.vol; music.el.preload = 'auto'; }
  return music.el;
}
function setVolume(v) {
  music.vol = Math.round(Math.max(0, Math.min(1, v)) * 20) / 20;   // 5% 단위
  if (music.el) music.el.volume = music.vol;
  try { localStorage.setItem(VOLUME_KEY, String(music.vol)); } catch { /* 저장 못 해도 진행 */ }
}
function musicPlay() { if (music.on) musicEl().play().catch(() => {}); }
function musicToggle() {
  music.on = !music.on;
  try { localStorage.setItem(MUSIC_KEY, music.on ? 'on' : 'off'); } catch { /* 저장 못 해도 진행 */ }
  if (music.on) musicPlay(); else music.el?.pause();
}
function musicSync() {   // 매 장면: 들어야 할 때만 재생
  if (!music.el) return;
  const want = music.on && !document.hidden && (modal?.kind === 'sound' || (scene === 'play' && modal?.kind !== 'pause'));
  if (want && music.el.paused) music.el.play().catch(() => {});
  else if (!want && !music.el.paused) music.el.pause();
}
const say = (msg) => { toast = { msg, until: performance.now() + 2400 }; };
const selUnits = () => selected.map((uid) => E.unitById(G, uid)).filter((u) => u && !u.away);

function start(seed, mode = 'normal') {
  G = E.newGame(seed === undefined ? undefined : seed >>> 0, mode); scene = 'play'; modal = null; speed = 1; selected = []; lastNews = 0; info = null;
  musicPlay();   // 누른 순간 안에서 재생 시작
  tut = !inRoom() && mode === 'normal' && !ls.get(TUT_KEY) ? { step: 0, n0: G.units.length, t: performance.now() } : null;
}
// ── 이어하기(사용자 요청 2026-11-08): 3초마다·화면을 떠날 때 판을 기기에 저장 ──
function saveNow() {
  if (scene !== 'play' || !G || G.over) return;
  if (coop()) ls.set(ROOM_SAVE_KEY, JSON.stringify({ code: net.code, round: net.round, gs: net.giftSent, gr: net.giftRecv, save: E.saveGame(G) }));
  else if (!inRoom()) { ls.set(SAVE_KEY, E.saveGame(G)); resumeMeta = { wave: G.wave, mode: G.mode }; ls.set(SAVE_META_KEY, JSON.stringify(resumeMeta)); }
}
function clearSave() { ls.del(SAVE_KEY); ls.del(SAVE_META_KEY); resumeMeta = null; }
function resume() {
  const g = E.loadGame(ls.get(SAVE_KEY) ?? '');
  if (!g) { clearSave(); say('이어할 판을 읽지 못했어요.'); return; }
  G = g; scene = 'play'; speed = 1; selected = []; lastNews = 0; info = null; tut = null;
  modal = { kind: 'pause' };   // 잠깐 멈춘 채로 시작(판을 살펴볼 수 있게)
  musicPlay();
}
setInterval(saveNow, 3000);
document.addEventListener('visibilitychange', () => { if (document.hidden) saveNow(); });
window.addEventListener('pagehide', saveNow);
function musicButton(x, y) {
  button(x, y, 46, 40, '♪', () => { modal = { kind: 'sound' }; musicPlay(); }, 'normal');
  if (!music.on) { ctx.strokeStyle = C.bad; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.moveTo(x + 12, y + 30); ctx.lineTo(x + 34, y + 10); ctx.stroke(); }
}
function saveBest() {   // 모드마다 따로
  const w = G.over === 'win' ? E.RULES.waves + 1 : G.wave;
  if (w > bestOf(G.mode)) ls.set(bestKey(G.mode), String(w));
  best = bestOf('normal');
  if (coop()) ls.del(ROOM_SAVE_KEY); else if (!inRoom()) clearSave();   // 끝난 판은 이어하기에서 지움
}

// ── 입력 ──
let down = null;
let moveCount = 0;   // 끌어서 옮긴 횟수(처음 안내)
function toLogical(ev) { const r = cv.getBoundingClientRect(); return { x: (ev.clientX - r.left) / scale, y: (ev.clientY - r.top) / scale }; }
const inField = (p) => p.x >= FIELD.x && p.y >= FIELD.y && p.x < FIELD.x + FIELD.w && p.y < FIELD.y + FIELD.h;
function unitAt(p) {   // 위에 그려진(나중) 것부터
  const list = E.fieldUnits(G);
  for (let i = list.length - 1; i >= 0; i--) { const u = list[i], z = usz(u.id); if (Math.hypot(u.x - p.x, u.y - 14 * z - p.y) <= UNIT_R * z) return u; }
  return null;
}
// 게임 시간이 흐르는가: 강화 창은 열어도 멈추지 않는다(사용자 요청)
const NONPAUSE = new Set(['upgrade', 'mission', 'pick', 'book', 'sound', 'storage', 'peer', 'meter']);   // 일시정지(Ⅱ)·결과 창만 멈춤
const running = () => scene === 'play' && G && !G.over && (!modal || NONPAUSE.has(modal.kind));
const playing = () => scene === 'play' && G && !G.over && (!info || landscape) && (!modal || (landscape && NONPAUSE.has(modal.kind)));
// 스크롤할 목록: 가로 배치는 오른쪽 창, 세로 배치는 열린 창
function scrollerAt(p) {
  if (landscape) return p.x >= SIDE_X ? (modal?.scrollable ? modal : null) : null;
  return modal?.scrollable ? modal : null;
}
cv.addEventListener('pointerdown', (ev) => {
  const p = toLogical(ev);
  cv.setPointerCapture?.(ev.pointerId);
  const top = hitAt(p), dragFn = top?.drag ? top.fn : null;
  if (dragFn) { dragFn(p); down = { x: p.x, y: p.y, x0: p.x, y0: p.y, dragFn, moved: false }; return; }
  const u = playing() && inField(p) ? unitAt(p) : null;
  const sc = scrollerAt(p);
  down = { x: p.x, y: p.y, x0: p.x, y0: p.y, unit: u, field: playing() && inField(p), scroller: sc, scroll0: sc?.scroll ?? 0, moved: false };
});
cv.addEventListener('pointermove', (ev) => {
  if (!down) return;
  const p = toLogical(ev);
  if (Math.hypot(p.x - down.x0, p.y - down.y0) > 10) down.moved = true;
  down.x = p.x; down.y = p.y;
  if (down.dragFn) { down.dragFn(p); return; }
  if (down.scroller && down.moved) down.scroller.scroll = Math.max(0, Math.min(down.scroller.maxScroll ?? 0, down.scroll0 - (p.y - down.y0)));
});
cv.addEventListener('pointerup', (ev) => {
  if (!down) return;
  const p = toLogical(ev), d = down;
  down = null;
  if (d.dragFn) { d.dragFn(p); return; }
  if (d.field && playing()) {
    if (d.unit && d.moved) {   // 끌어서 옮기기(고른 무리에 있으면 무리째, 고정한 갈매미는 그 자리에)
      const group = selected.includes(d.unit.uid) && selected.length > 1 ? selUnits() : [d.unit];
      const dx = p.x - d.x0, dy = p.y - d.y0;
      let moved = 0;
      for (const u of group) if (E.moveUnit(G, u.uid, u.tx + dx, u.ty + dy, true)) moved++;
      if (!moved) say('고정한 갈매미예요. 「고정 풀기」를 누르면 옮길 수 있어요.'); else moveCount++;
      if (!selected.includes(d.unit.uid)) selected = [d.unit.uid];
      return;
    }
    if (d.unit) { selected = selected.length === 1 && selected[0] === d.unit.uid ? [] : [d.unit.uid]; return; }
    if (d.moved) {   // 빈 곳을 끌어 여러 마리 고르기
      const x0 = Math.min(d.x0, p.x), x1 = Math.max(d.x0, p.x), y0 = Math.min(d.y0, p.y), y1 = Math.max(d.y0, p.y);
      selected = E.fieldUnits(G).filter((u) => u.x >= x0 && u.x <= x1 && u.y - 14 >= y0 && u.y - 14 <= y1).map((u) => u.uid);
      if (selected.length) say(`${selected.length}마리를 골랐어요. 그중 하나를 끌면 함께 움직여요.`);
      return;
    }
    // 빈 곳을 누르면 고르기 풀림(옮기기는 끌기로만)
    if (selected.length) { selected = []; return; }
  }
  if (d.moved && d.scroller) return;
  const h = hitAt(p);
  if (h) h.fn(p);
});
cv.addEventListener('pointercancel', () => { down = null; });
cv.addEventListener('wheel', (ev) => { const sc = scrollerAt(toLogical(ev)); if (sc) { sc.scroll = Math.max(0, Math.min(sc.maxScroll ?? 0, sc.scroll + ev.deltaY)); ev.preventDefault(); } }, { passive: false });
window.addEventListener('keydown', (ev) => {
  if (scene === 'lobby' && lobby.mode === 'enter') {
    if (/^[0-9]$/.test(ev.key)) typeDigit(ev.key);
    else if (ev.key === 'Backspace') lobby.input = lobby.input.slice(0, -1);
    else if (ev.key === 'Enter') joinRoom(lobby.input);
    return;
  }
  if (scene !== 'play' || !G || G.over) return;
  if (ev.key === ' ' || ev.key === 'Escape') {
    if (info) info = null; else if (modal?.kind === 'pause') setPause(false); else if (modal) modal = null; else setPause(true);
    ev.preventDefault();
    return;
  }
  // PC 단축키(사용자 요청 2026-11-08): 1~5 위습 · U 강화 · M 외부탐사 · S 창고 · B 조합법 · P 선택위습 · D 피해 순위 · L 위치 고정
  if (ev.ctrlKey || ev.metaKey || ev.altKey || modal?.kind === 'pause') return;
  const k = ev.key.toLowerCase(), wi = '12345'.indexOf(ev.key);
  if (wi >= 0) { doSummon(E.WISP_GRADES[wi]); return; }
  const open = (kind, extra = {}) => { modal = modal?.kind === kind ? null : { kind, ...extra }; info = null; };
  if (k === 'u') open('upgrade');
  else if (k === 'm') open('mission');
  else if (k === 's') open('storage', { scroll: 0, scrollable: true });
  else if (k === 'b') { if (modal?.kind === 'book') modal = null; else openBook(); }
  else if (k === 'p') open('pick', { grade: E.PICK_GRADES.find((gr) => G.picks[gr] > 0) ?? 'COMMON' });
  else if (k === 'd') open('meter', { scroll: 0, scrollable: true });
  else if (k === 'l') { const sel = selUnits(); if (sel.length) { const on = !sel.every((u) => u.lock); E.setLock(G, sel.map((u) => u.uid), on); say(on ? `${sel.length}마리 위치 고정` : `${sel.length}마리 고정 풀기`); } }
});
document.addEventListener('visibilitychange', () => { if (document.hidden && playing()) setPause(true); });

function doSummon(grade) { const r = E.summon(G, grade); if (!r.ok && r.why) say(r.why); }   // 연달아 누를 수 있게 고르지 않음

// ── 판 ──
function drawBackground() {
  ctx.fillStyle = C.bg; ctx.fillRect(0, 0, VW, VH);
  const bg = img(`${ART}/ui/bg-harbor.webp`);
  if (bg) { const s = Math.max(VW / bg.naturalWidth, VH / bg.naturalHeight); ctx.globalAlpha = 0.5; ctx.drawImage(bg, (VW - bg.naturalWidth * s) / 2, (VH - bg.naturalHeight * s) / 2, bg.naturalWidth * s, bg.naturalHeight * s); ctx.globalAlpha = 1; }
  ctx.fillStyle = 'rgba(7,19,28,.55)'; ctx.fillRect(0, 0, VW, VH);
}
// ☆목표의 재료 테두리: 바로 아래 재료 + 지금 없는 재료를 만들 하위 재료 중 가진 것까지(사용자 요청 2026-11-08: 하위 재료 연결)
function goalColors() {
  const out = {};
  G.goals.forEach((gid, k) => {
    const r = E.recipeFor(gid); if (!r) return;
    const ids = new Set(r.in);
    const walk = (n) => { if (n.have) ids.add(n.id); n.kids.forEach(walk); };
    walk(needTree(gid, E.countOnField(G)));
    for (const id of ids) (out[id] ??= []).push(GOAL_COLOR[k]);
  });
  return out;
}
// 재료 피라미드: 가진 갈매미(판·창고·탐사)를 쓰고, 없는 재료는 그 재료의 조합법으로 내려가며 펼친다
function needTree(id, cnt) {
  const pool = { ...cnt };
  const node = (c, depth) => {
    if ((pool[c] ?? 0) > 0) { pool[c]--; return { id: c, have: true, kids: [] }; }
    const r = depth < 8 ? E.recipeFor(c) : null;
    return { id: c, have: false, kids: r ? r.in.map((x) => node(x, depth + 1)) : [] };
  };
  const r = E.recipeFor(id);
  return { id, have: false, top: true, kids: r ? r.in.map((x) => node(x, 1)) : [] };
}
function drawLanes() {
  const L = E.LOOP, lane = 40;
  box(FIELD.x, FIELD.y, FIELD.w, FIELD.h, 'rgba(7,19,28,.5)', C.edge, 12, 1.5);
  ctx.lineWidth = lane; ctx.strokeStyle = C.lane; ctx.lineJoin = 'round';
  ctx.strokeRect(L.x0, L.y0, L.x1 - L.x0, L.y1 - L.y0);
  ctx.lineWidth = 1.5; ctx.strokeStyle = C.laneEdge;
  ctx.strokeRect(L.x0 - lane / 2, L.y0 - lane / 2, L.x1 - L.x0 + lane, L.y1 - L.y0 + lane);
  ctx.strokeRect(L.x0 + lane / 2, L.y0 + lane / 2, L.x1 - L.x0 - lane, L.y1 - L.y0 - lane);
  text('▶', L.x0 + 2, L.y0, 16, C.goldEdge, 'center');
  // 나타나는 곳
  ctx.beginPath(); ctx.arc(E.SPAWN.x, E.SPAWN.y, 34, 0, Math.PI * 2); ctx.strokeStyle = 'rgba(243,216,158,.25)'; ctx.setLineDash([5, 6]); ctx.stroke(); ctx.setLineDash([]);
}
// 판 위 갈매미 한 마리(그림자 고리 + 그림). 등급이 높을수록 크게
function drawUnitShape(id, x, y, ring, lw, lock = false) {
  const z = usz(id);
  ctx.beginPath(); ctx.ellipse(x, y, 20 * z, 7 * z, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
  ctx.lineWidth = lw; ctx.strokeStyle = ring; ctx.stroke();
  const im = unitImg(id), gl = E.tierOf(id) >= 4 ? glow(id) : null;
  if (im && gl) {   // 전설 이상: 그림 둘레가 등급 색으로 빛남(천천히 밝아졌다 어두워짐)
    const bw = 48 * z, bh = 50 * z, sc = Math.min(bw / im.naturalWidth, bh / im.naturalHeight), dw = im.naturalWidth * sc, dh = im.naturalHeight * sc;
    const dx = x - bw / 2 + (bw - dw) / 2, dy = y - bh + bh - dh, f = dw / gl.w;
    ctx.globalAlpha = 0.7 + 0.3 * Math.sin(performance.now() / 420 + x * 0.05);
    ctx.drawImage(gl.c, dx - gl.pad * f, dy - gl.pad * f, gl.c.width * f, gl.c.height * f);
    ctx.globalAlpha = 1;
  }
  drawFit(im, x - 24 * z, y - 50 * z, 48 * z, 50 * z);
  if (lock) drawLockMark(x + 22 * z, y - 2);
}
const glows = {};
function glow(id) {   // 빛 테두리 그림을 한 번만 만들어 둠
  if (glows[id]) return glows[id];
  const i = unitImg(id);
  if (!i) return null;
  const t = E.tierOf(id), pad = 16, w = 96, h = Math.round(96 * i.naturalHeight / i.naturalWidth);
  const c = document.createElement('canvas'); c.width = w + pad * 2; c.height = h + pad * 2;
  const x = c.getContext('2d');
  x.shadowColor = GRADE_COLOR[E.UNIT[id].grade]; x.shadowBlur = 9 + (t - 4) * 1.5;
  for (let k = 0; k < 3; k++) x.drawImage(i, pad, pad, w, h);
  x.shadowBlur = 0; x.globalCompositeOperation = 'destination-out'; x.drawImage(i, pad, pad, w, h);   // 그림 자체는 빼고 둘레 빛만
  glows[id] = { c, pad, w };
  return glows[id];
}
function drawLockMark(x, y) {   // 위치 고정 자물쇠(어두운 동그라미 위에)
  ctx.beginPath(); ctx.arc(x, y - 2, 11, 0, Math.PI * 2); ctx.fillStyle = 'rgba(7,19,28,.92)'; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = C.goldEdge; ctx.stroke();
  ctx.beginPath(); ctx.arc(x, y - 5, 3.6, Math.PI, 0); ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 2; ctx.stroke();
  box(x - 5.5, y - 5, 11, 8.5, C.goldEdge, null, 2);
}
function drawField() {
  drawLanes();
  // 고른 갈매미 사거리(한 마리일 때)
  const sel = selUnits();
  if (sel.length === 1) {
    const k = E.kit(sel[0].id);
    ctx.beginPath(); ctx.arc(sel[0].x, sel[0].y - 14, k.range, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(243,216,158,.07)'; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(243,216,158,.55)'; ctx.stroke();
  }
  // 적
  for (const e of G.enemies) {
    const sz = e.boss ? 76 : 38, sh = shade(e.look);
    if (sh) drawFit(sh, e.x - sz / 2, e.y - sz * 0.75, sz, sz);
    if (G.t < e.stunUntil) text('★', e.x, e.y - sz * 0.75 - 14, 12, '#ffe14a', 'center');
    if (e.slow > 0) { ctx.beginPath(); ctx.arc(e.x, e.y + 2, sz / 3, 0, Math.PI * 2); ctx.strokeStyle = '#9fe8ff'; ctx.lineWidth = 1.5 + 2.5 * e.slow; ctx.stroke(); }   // 이속감소가 많이 쌓일수록 굵게
    const bw = e.boss ? 70 : 32, bx = e.x - bw / 2, by = e.y - sz * 0.75 - 6;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, e.hp / e.max), 5, e.boss ? '#ff6b6b' : '#6fd08a', null, 2);
  }
  // 갈매미(아래쪽부터 그려 겹침이 자연스럽게)
  const gc = goalColors(), selSet = new Set(selected);
  const list = [...E.fieldUnits(G)].sort((a, b) => a.y - b.y);
  for (const u of list) {
    const info = E.UNIT[u.id], col = GRADE_COLOR[info.grade], z = usz(u.id);
    drawUnitShape(u.id, u.x, u.y, selSet.has(u.uid) ? '#ffffff' : col, selSet.has(u.uid) ? 3 : 2, u.lock);
    (gc[u.id] ?? []).forEach((c, k) => { ctx.beginPath(); ctx.ellipse(u.x, u.y, (24 + k * 4) * z, (9 + k * 2) * z, 0, 0, Math.PI * 2); ctx.strokeStyle = c; ctx.lineWidth = 2.5; ctx.stroke(); });
    if (G.t - u.born < 0.5) { ctx.globalAlpha = 1 - (G.t - u.born) / 0.5; ctx.beginPath(); ctx.arc(u.x, u.y - 20 * z, 30 * z, 0, Math.PI * 2); ctx.fillStyle = 'rgba(243,216,158,.35)'; ctx.fill(); ctx.globalAlpha = 1; }
  }
  // 겹친 갈매미 수(같은 자리 14px 안)
  const stacks = [];
  for (const u of list) { const z = usz(u.id), st = stacks.find((q) => Math.abs(q.x - u.x) < 14 && Math.abs(q.y - u.y) < 14); if (st) { st.n++; st.x = u.x; st.y = u.y; st.z = Math.max(st.z, z); } else stacks.push({ x: u.x, y: u.y, n: 1, z }); }
  for (const st of stacks) if (st.n > 1) { const by = st.y - 50 * st.z - 4; box(st.x + 10 * st.z, by, 30, 20, 'rgba(7,19,28,.9)', C.goldEdge, 8, 1.5); text(`×${st.n}`, st.x + 10 * st.z + 15, by + 10.5, 13, C.goldEdge, 'center'); }
  // 끌기 표시
  if (down?.moved && down.field) {
    if (down.unit) {
      const group = selected.includes(down.unit.uid) && selected.length > 1 ? selUnits() : [down.unit];
      for (const u of group) { if (u.lock) continue; const z = usz(u.id); drawFit(unitImg(u.id), u.x - 24 * z + down.x - down.x0, u.y - 50 * z + down.y - down.y0, 48 * z, 50 * z, 0.6); }
    } else {
      box(Math.min(down.x0, down.x), Math.min(down.y0, down.y), Math.abs(down.x - down.x0), Math.abs(down.y - down.y0), 'rgba(243,216,158,.08)', C.goldEdge, 2, 1.5);
    }
  }
  // 효과
  for (const f of G.fx) {
    const age = G.t - f.t;
    if (f.kind === 'shot' && age < 0.14) {
      ctx.globalAlpha = 1 - age / 0.14; ctx.strokeStyle = f.type === 'phys' ? C.phys : C.mag; ctx.lineWidth = f.job === '보조' ? 1.5 : 2.5;
      ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 24); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke();
      ctx.beginPath(); ctx.arc(f.x1, f.y1 - 10, f.job === '보조' ? 3 : 4.5, 0, Math.PI * 2); ctx.fillStyle = ctx.strokeStyle; ctx.fill(); ctx.globalAlpha = 1;
    } else if (f.kind === 'zap' && age < 0.2) {
      ctx.strokeStyle = '#ffe14a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 10); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke();
    } else if (f.kind === 'combine' || f.kind === 'move') {
      const a = Math.max(0, 1 - age / 0.6); ctx.globalAlpha = a;
      ctx.beginPath(); ctx.arc(f.x, f.y - (f.kind === 'move' ? 0 : 20), (f.kind === 'move' ? 8 : 20) + (1 - a) * 30, 0, Math.PI * 2);
      ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 2.5; ctx.stroke(); ctx.globalAlpha = 1;
    } else if (f.kind === 'wisp') {
      const a = Math.max(0, 1 - age / 0.8); ctx.globalAlpha = a;
      text('✦', f.x, f.y - 20 - (1 - a) * 30, 22, GRADE_COLOR[f.grade], 'center'); ctx.globalAlpha = 1;
    }
  }
}

// ── 위 정보 줄 ──
function drawHud() {
  box(8, 6, W - 16, 88, C.header, C.edge, 10);
  const wi = E.waveInfo(Math.max(1, G.wave)), left = Math.ceil(E.nextWaveIn(G));
  text(`웨이브 ${G.wave}/${E.RULES.waves}`, 20, 28, 20, C.text);
  chip(KIND_NAME[wi.kind], 150, 17, wi.boss ? '#ff8a7a' : wi.kind === '갑옷' ? C.phys : wi.kind === '결계' ? C.mag : C.muted, 13);
  const line2 = wi.boss ? (G.bossDone === G.wave ? '보스 처치! 다음 웨이브까지 ' + left + '초' : `보스를 ${left}초 안에 잡기`) : `다음 웨이브 ${left}초`;
  text(line2, 20, 58, 15, wi.boss ? (G.bossDone === G.wave ? C.good : left <= 12 ? C.bad : '#ffc2a8') : C.muted);
  if (G.wave < E.RULES.waves) {   // 다음 웨이브 종류 미리 보기(사용자 요청 2026-11-08)
    const nx = E.waveInfo(G.wave + 1);
    ctx.font = `15px ${FONT}`;
    const lx = 20 + ctx.measureText(line2).width + 10;
    fitText(`▸ 다음 ${nx.boss ? '보스!' : KIND_NAME[nx.kind]}`, lx, 58, W - 30 - lx, 14, nx.boss ? '#ff8a7a' : nx.kind === '갑옷' ? C.phys : nx.kind === '결계' ? C.mag : C.muted);
  }
  // 적 수·경고
  const n = G.enemies.length, lim = E.RULES.limit, ratio = Math.min(1, n / lim);
  text(`적 ${n}/${lim}`, 20, 82, 14, n > lim ? C.bad : C.text);
  box(92, 77, 150, 10, 'rgba(0,0,0,.5)', C.line, 5, 1);
  box(92, 77, 150 * ratio, 10, n > lim ? '#e0533f' : ratio > 0.7 ? '#e8b04a' : '#6fd08a', null, 5);
  text(`경고 ${G.warn}/${E.RULES.warnMax}`, 256, 82, 14, G.warn ? C.bad : C.muted);
  // 새우·강화
  const sh = img(`${ART}/menu/currency-shrimp.webp`);
  if (sh) drawFit(sh, 340, 64, 22, 22);
  text(`${G.gold}`, 366, 76, 16, C.goldEdge);
  button(W - 216, 12, 46, 40, '피해', () => { modal = modal?.kind === 'meter' ? null : { kind: 'meter', scroll: 0, scrollable: true }; }, 'normal');
  musicButton(W - 164, 12);
  button(W - 112, 12, 46, 40, 'Ⅱ', () => setPause(true), 'normal');
  button(W - 60, 12, 46, 40, `×${speed}`, () => setSpeed(speed === 1 ? 2 : speed === 2 ? 3 : 1), 'normal');
  // 새 소식(위습 획득·탐사 결과)
  const nw = G.news[G.news.length - 1];
  if (nw && G.t - nw.t < 3.5) {
    ctx.font = `15px ${FONT}`;
    const tw = Math.min(W - 40, ctx.measureText(nw.msg).width + 28);
    box((W - tw) / 2, FIELD.y + 6, tw, 32, 'rgba(19,44,59,.95)', C.goldEdge, 8, 1.5);
    fitText(nw.msg, W / 2, FIELD.y + 22, tw - 16, 15, C.goldEdge, 'center');
  }
}

// ── 아래 판 ──
function drawBottom() {
  const y0 = 672;
  box(8, y0, W - 16, H - y0 - 8, C.panel, C.edge, 12);
  const sel = selUnits();
  if (sel.length === 1) drawUnitPanel(y0, sel[0]);
  else if (sel.length > 1) drawGroupPanel(y0, sel);
  else drawMainPanel(y0);
}
function drawMainPanel(y0) {
  // 위습
  text('위습 (눌러서 소환)', 22, y0 + 18, 14, C.gold);
  const ws = E.WISP_GRADES, bw = (W - 44 - 4 * 6) / 5;
  ws.forEach((gr, k) => {
    const n = G.wisps[gr] ?? 0, x = 22 + k * (bw + 6), y = y0 + 32;
    box(x, y, bw, 58, n ? 'rgba(11,30,42,.9)' : 'rgba(11,30,42,.4)', n ? GRADE_COLOR[gr] : '#33434d', 10, n ? 2 : 1);
    fitText(E.GRADE_NAME[gr], x + bw / 2, y + 18, bw - 8, 15, n ? GRADE_COLOR[gr] : '#5b6a73', 'center');
    text(`×${n}`, x + bw / 2, y + 41, 19, n ? C.text : '#5b6a73', 'center');
    if (n) hit(x, y, bw, 58, () => doSummon(gr));
  });
  // 강화·탐사·조합법
  const by = y0 + 100, b4 = (W - 44 - 24) / 5;
  const picks = E.PICK_GRADES.reduce((a, gr) => a + (G.picks[gr] ?? 0), 0);
  button(22, by, b4, 58, '선택위습', () => { modal = { kind: 'pick', grade: E.PICK_GRADES.find((gr) => G.picks[gr] > 0) ?? 'COMMON' }; }, picks ? 'primary' : 'normal', true, picks ? `${picks}개 있음` : '없음');
  const canUp = G.upg.some((_, gi) => !E.upgMaxed(G, gi) && G.gold >= E.upgradeCost(G, gi));
  button(22 + (b4 + 6), by, b4, 58, '강화', () => { modal = { kind: 'upgrade' }; }, 'normal', true, canUp ? '강화 가능' : `새우 ${G.gold}`, canUp ? C.good : null);
  const open = E.missionOpen(G), away = E.awayUnits(G).length, nextR = E.REGIONS[G.missionDone];
  const msub = E.modeOf(G).noMission ? '금지 모드' : G.missionDone >= E.REGIONS.length ? '모두 완료' : away ? `${away}마리 탐사 중` : `${nextR.ch}장 가능`;
  button(22 + 2 * (b4 + 6), by, b4, 58, '외부탐사', () => { modal = { kind: 'mission' }; }, 'normal', true, msub);
  const st = E.storedUnits(G).length;
  button(22 + 3 * (b4 + 6), by, b4, 58, '창고', () => { modal = { kind: 'storage', scroll: 0, scrollable: true }; }, 'normal', true, `${st}마리${Object.values(G.autoGrades ?? {}).some(Boolean) ? '·자동' : ''}`);
  const rc = readyCount();
  button(22 + 4 * (b4 + 6), by, b4, 58, '조합법', () => openBook(), rc ? 'primary' : 'normal', true, rc ? `조합 가능 ${rc}` : `${E.RECIPES.length}개`, rc ? '#1f5a3c' : null);
  if (rc) { const bx2 = 22 + 4 * (b4 + 6) + b4 - 4, by2 = by + 4; ctx.beginPath(); ctx.arc(bx2, by2, 11, 0, Math.PI * 2); ctx.fillStyle = '#e0533f'; ctx.fill(); ctx.lineWidth = 2; ctx.strokeStyle = '#07131c'; ctx.stroke(); text(String(rc), bx2, by2 + 0.5, 13, '#fff', 'center'); }
  // 조합 목표
  const gy = y0 + 170, gw = (W - 44 - 12) / 3, cnt = E.countOnField(G);
  for (let k = 0; k < E.GOAL_MAX; k++) {
    const x = 22 + k * (gw + 6), gid = G.goals[k];
    if (!gid) { box(x, gy, gw, 50, 'rgba(11,30,42,.4)', 'rgba(145,122,76,.35)', 8, 1); text(k === 0 ? '조합 목표 ☆' : '비어 있음', x + gw / 2, gy + 25, 14, '#6f7f88', 'center'); hit(x, gy, gw, 50, () => openBook()); continue; }
    const r = E.recipeFor(gid), ready = !!E.recipeUnits(G, r);
    box(x, gy, gw, 50, 'rgba(11,30,42,.85)', GOAL_COLOR[k], 8, 2.5);
    drawFit(unitImg(gid), x + 3, gy + 3, 40, 44);
    fitText(E.UNIT[gid].name, x + 46, gy + 16, gw - 50, 15, C.text);
    text(ready ? '조합 가능!' : `재료 ${have(r, cnt)}/${r.in.length}`, x + 46, gy + 36, 13, ready ? C.good : C.muted);
    hit(x, gy, gw, 50, () => { if (ready) { const nu = E.combine(G, r); if (nu) { selected = [nu.uid]; say(`${E.UNIT[gid].name} 완성!`); } } else showInfo(gid); });
  }
  fitText(landscape ? '단축키  1~5 위습 · U 강화 · M 탐사 · S 창고 · B 조합법 · P 선택 · D 피해 · L 고정' : '끌기=옮기기 · 누르기=고르기 · 빈 곳 끌기=여러 마리 고르기', 22, H - 26, W - 44, 14, C.muted);
}
// 지금 조합할 수 있는 조합법 수(0.5초마다 다시 셈)
let readyCache = { t: 0, n: 0, g: null };
function readyCount() {
  const now = performance.now();
  if (readyCache.g !== G || now - readyCache.t > 500) readyCache = { t: now, g: G, n: E.RECIPES.filter((r) => E.recipeUnits(G, r)).length };
  return readyCache.n;
}
function have(r, cnt) { const left = { ...cnt }; let n = 0; for (const id of r.in) if ((left[id] ?? 0) > 0) { left[id]--; n++; } return n; }
function drawUnitPanel(y0, u) {
  const info = E.UNIT[u.id], k = E.kit(u.id);
  drawFit(unitImg(u.id), 18, y0 + 8, 68, 70);
  hit(18, y0 + 8, 68, 70, () => showInfo(u.id));
  fitText(info.name, 94, y0 + 22, 150, 21, C.text);
  ctx.font = `21px ${FONT}`;
  let cx = 94 + Math.min(150, ctx.measureText(info.name).width) + 8;
  cx += chip(E.GRADE_NAME[info.grade], cx, y0 + 11, GRADE_COLOR[info.grade]) + 6;
  chip(`${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx, y0 + 11, k.type === 'phys' ? C.phys : C.mag);
  const up = E.upgMul(G, k.tier);
  fitText(`공격 ${Math.round(k.dmg * up)} · ${k.gap <= 0.6 ? '빠름' : k.gap >= 1.1 ? '느림' : '보통'} · 사거리 ${k.range}`, 94, y0 + 50, W - 210, 15, C.muted);
  fitText(E.kitTips(k).join(' · ') || '기본 공격', 94, y0 + 72, W - 210, 15, k.type === 'phys' ? C.phys : C.mag);
  button(W - 112, y0 + 10, 90, 40, '닫기', () => { selected = []; }, 'quiet');
  const list = E.recipesUsing(u.id).map((r) => ({ r, ok: !!E.recipeUnits(G, r, u.uid) })).sort((a, b) => (b.ok - a.ok) || (E.tierOf(a.r.out) - E.tierOf(b.r.out)));
  text(list.length ? '이 갈매미로 만들 수 있는 것' : '이 갈매미가 들어가는 조합법이 없어요', 22, y0 + 100, 15, C.gold);
  const cnt = E.countOnField(G);
  list.slice(0, 2).forEach(({ r, ok }, i) => recipeRow(r, ok, 18, y0 + 112 + i * 50, W - 36, 46, cnt, u.uid));
  const by = H - 8 - 56, b6 = (W - 44 - 30) / 6, bx = (i) => 22 + i * (b6 + 6);
  button(bx(0), by, b6, 46, list.length > 2 ? `더보기+${list.length - 2}` : '조합법', () => openBook(null, u.id), 'normal');
  button(bx(1), by, b6, 46, '모으기', () => { selected = E.gather(G, u.uid); say(`같은 ${E.UNIT[u.id].name} ${selected.length}마리를 모았어요(고정한 것은 그 자리).`); }, 'normal');
  button(bx(2), by, b6, 46, u.lock ? '고정 풀기' : '위치 고정', () => { E.setLock(G, [u.uid], !u.lock); say(u.lock ? '위치를 고정했어요. 끌어도 안 움직여요.' : '고정을 풀었어요.'); }, u.lock ? 'primary' : 'normal');
  button(bx(3), by, b6, 46, '창고로', () => { E.store(G, [u.uid]); selected = []; say(`${E.UNIT[u.id].name}을(를) 창고에 넣었어요(조합 재료로는 쓰여요).`); }, 'normal');
  button(bx(4), by, b6, 46, '탐사', () => sendSelected([u]), 'normal', E.missionOpen(G));
  button(bx(5), by, b6, 46, `판매+${k.sell}`, () => { E.sell(G, u.uid); selected = []; }, 'danger');
}
function drawGroupPanel(y0, sel) {
  let p = 0, m = 0;
  for (const u of sel) { const k = E.kit(u.id); if (k.type === 'phys') p++; else m++; }
  text(`갈매미 ${sel.length}마리 고름`, 22, y0 + 24, 20, C.text);
  text(`물딜 ${p} · 마딜 ${m}`, 22, y0 + 52, 15, C.muted);
  button(W - 112, y0 + 10, 90, 40, '해제', () => { selected = []; }, 'quiet');
  const per = 10, sz = 46;
  sel.slice(0, per * 2).forEach((u, i) => {
    const x = 22 + (i % per) * (sz + 4), y = y0 + 72 + Math.floor(i / per) * (sz + 4);
    box(x, y, sz, sz, 'rgba(11,30,42,.85)', GRADE_COLOR[E.UNIT[u.id].grade], 6, 1.5);
    drawFit(unitImg(u.id), x + 2, y + 2, sz - 4, sz - 4);
  });
  if (sel.length > per * 2) text(`외 ${sel.length - per * 2}마리`, W - 30, y0 + 176, 14, C.muted, 'right');
  fitText('고른 갈매미 중 하나를 끌면 함께 움직여요 · 빈 곳을 누르면 고르기 풀림', 22, y0 + 198, W - 44, 14, C.muted);
  const by = H - 8 - 56;
  const b4 = (W - 44 - 18) / 4, allLock = sel.every((u) => u.lock);
  button(22, by, b4, 46, `탐사 보내기 (${sel.length})`, () => sendSelected(sel), 'normal', E.missionOpen(G));
  button(22 + (b4 + 6), by, b4, 46, `창고로 (${sel.length})`, () => { const n = E.store(G, sel.map((u) => u.uid)); selected = []; say(`${n}마리를 창고에 넣었어요.`); }, 'normal');
  button(22 + 2 * (b4 + 6), by, b4, 46, allLock ? `고정 풀기 (${sel.length})` : `위치 고정 (${sel.length})`, () => { const n = E.setLock(G, sel.map((u) => u.uid), !allLock); say(allLock ? `${n}마리 고정을 풀었어요.` : `${n}마리 위치를 고정했어요.`); }, allLock ? 'primary' : 'normal');
  button(22 + 3 * (b4 + 6), by, b4, 46, '고르기 해제', () => { selected = []; }, 'quiet');
}
function sendSelected(list) {
  const n = E.sendAway(G, list.map((u) => u.uid));
  if (n) { say(`${n}마리를 외부탐사 「${E.REGIONS[G.missionDone].name}」에 보냈어요.`); selected = []; }
}
// 조합법 한 줄
function recipeRow(r, ok, x, y, w, h, cnt, keepUid = 0) {
  const out = E.UNIT[r.out], gi = G.goals.indexOf(r.out);
  hit(x, y, w, h, () => showInfo(r.out));   // 조합 버튼 말고 줄을 누르면 간단 설명(사용자 요청 2026-11-08)
  box(x, y, w, h, ok ? 'rgba(43,122,98,.25)' : 'rgba(11,30,42,.7)', gi >= 0 ? GOAL_COLOR[gi] : ok ? '#2b7a62' : 'rgba(145,122,76,.4)', 8, gi >= 0 ? 2.5 : 1.5);
  drawFit(unitImg(r.out), x + 4, y + 2, 40, h - 4);
  fitText(out.name, x + 50, y + 15, 92, 16, GRADE_COLOR[out.grade]);
  text(`${E.GRADE_NAME[out.grade]}${r.hidden ? ' · 히든 조합' : ''}`, x + 50, y + 34, 12, C.muted);
  const left = { ...cnt };
  let cx = x + 150;
  const chipMax = (x + w - 112 - cx) / r.in.length - 4;
  for (const id of r.in) {
    const hasIt = (left[id] ?? 0) > 0;
    if (hasIt) left[id]--;
    ctx.font = `14px ${FONT}`;
    const t = E.UNIT[id].name, tw = Math.min(chipMax, ctx.measureText(t).width + 12);
    hit(cx, y + h / 2 - 12, tw, 24, () => showInfo(id));
    box(cx, y + h / 2 - 12, tw, 24, hasIt ? 'rgba(111,208,138,.18)' : 'rgba(0,0,0,.3)', hasIt ? '#6fd08a' : '#46555f', 6, 1);
    fitText(t, cx + tw / 2, y + h / 2, tw - 6, 14, hasIt ? C.good : '#8b9aa3', 'center');
    cx += tw + 4;
  }
  const bx = x + w - 108;
  button(bx, y + 5, 40, h - 10, gi >= 0 ? '★' : '☆', () => { const on = E.toggleGoal(G, r.out); say(on ? `목표: ${out.name} (재료에 색 테두리)` : '목표에서 뺐어요'); }, 'quiet');
  button(bx + 46, y + 5, 58, h - 10, '조합', () => { const nu = E.combine(G, r, keepUid); if (nu) { selected = [nu.uid]; say(`${out.name} 완성!`); } }, 'primary', ok);
}

// ── 모달 ──
function openBook(focus = null, using = null) { modal = { kind: 'book', scroll: 0, scrollable: true, focus, using }; }
function shadeAll(a = 0.72) { ctx.fillStyle = `rgba(3,9,14,${a})`; ctx.fillRect(0, 0, W, H); hit(0, 0, W, H, () => {}); }
function drawBook() {
  shadeAll(0.35);   // 게임은 계속 흐름(위 정보 줄이 보이게)
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  fitText(modal.using ? `${E.UNIT[modal.using].name}이(가) 들어가는 조합법` : `조합법 ${E.RECIPES.length}개`, x + 18, y + 30, w - 140, 20, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText('☆ 목표 = 판의 재료 갈매미에 같은 색 테두리', x + 18, y + 64, w - 36, 14, C.muted);
  const top = y + 82, viewH = h - 92, cnt = E.countOnField(G);
  let rows = E.RECIPES.filter((r) => !modal.using || r.in.includes(modal.using));
  rows = rows.map((r) => ({ r, ok: !!E.recipeUnits(G, r) })).sort((a, b) => (E.tierOf(a.r.out) - E.tierOf(b.r.out)) || (b.ok - a.ok));
  const items = [];
  let lastG = '';
  for (const it of rows) { const g = E.UNIT[it.r.out].grade; if (g !== lastG) { items.push({ head: g }); lastG = g; } items.push(it); }
  const rowH = (it) => (it.head ? 30 : 52);
  const total = items.reduce((a, it) => a + rowH(it), 0);
  modal.maxScroll = Math.max(0, total - viewH);
  if (modal.focus && !modal.focused) { let yy = 0; for (const it of items) { if (it.r?.out === modal.focus) break; yy += rowH(it); } modal.scroll = Math.min(modal.maxScroll, Math.max(0, yy - 60)); modal.focused = true; }
  ctx.save(); rr(x + 6, top, w - 12, viewH, 8); ctx.clip();
  let yy = top - modal.scroll;
  const before = hits.length;
  for (const it of items) {
    const rh = rowH(it);
    if (yy + rh >= top && yy <= top + viewH) {
      if (it.head) { text(E.GRADE_NAME[it.head], x + 20, yy + 17, 15, GRADE_COLOR[it.head]); ctx.fillStyle = 'rgba(145,122,76,.35)'; ctx.fillRect(x + 80, yy + 17, w - 110, 1); }
      else recipeRow(it.r, it.ok, x + 12, yy + 3, w - 24, rh - 6, cnt);
    }
    yy += rh;
  }
  ctx.restore();
  hits = hits.filter((hh, k) => k < before || (hh.y >= top && hh.y + hh.h <= top + viewH));
  if (modal.maxScroll > 0) { const bh = Math.max(40, viewH * viewH / total), by2 = top + (viewH - bh) * (modal.scroll / modal.maxScroll); box(x + w - 8, by2, 4, bh, 'rgba(243,216,158,.5)', null, 2); }
}
function drawMission() {
  shadeAll(0.35);
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('외부탐사', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(coop() ? '같이하기: 친구와 같은 대장을 함께 쳐요. 둘의 피해가 합쳐지고, 깨면 둘 다 보상을 받아요.' : '앞 장을 끝내면 다음 장이 열려요 · 보낸 동안은 수비에서 빠져요 · ★탐사 특기 갈매미는 피해 ×3~6', x + 18, y + 64, w - 36, 13, coop() ? C.gold : C.muted);
  if (E.modeOf(G).noMission) { text('외부탐사 금지 모드예요', W / 2, y + 200, 20, C.bad, 'center'); fitText('이 모드에서는 스토리를 보낼 수 없어요. 판 수비만으로 버텨 보세요.', W / 2, y + 236, w - 40, 14, C.muted, 'center'); return; }
  // 지역 목록
  const ly = y + 84;
  E.REGIONS.forEach((r, k) => {
    const yy = ly + k * 40, done = k < G.missionDone, cur = k === G.missionDone, open = cur && E.missionOpen(G);
    box(x + 12, yy, w - 24, 34, cur ? 'rgba(43,122,98,.2)' : 'rgba(11,30,42,.6)', cur ? (open ? '#2b7a62' : C.edge) : 'rgba(145,122,76,.3)', 6, cur ? 2 : 1);
    fitText(`${r.ch}장 ${r.name}`, x + 24, yy + 17, 220, 15, done ? C.muted : C.text);
    fitText(done ? '완료 ✓' : open ? '진행 가능' : '잠김', x + 250, yy + 17, 80, 13, done ? C.good : open ? C.goldEdge : '#6f7f88');
    const rw = Object.entries(r.reward).map(([g, n]) => `${E.GRADE_NAME[g]}${n}`).join('·') + (r.pick ? ' + 선택 ' + Object.entries(r.pick).map(([g, n]) => `${E.GRADE_NAME[g]}${n}`).join('·') : '');
    fitText(rw, x + w - 24, yy + 17, 160, 13, C.gold, 'right');
  });
  // 지금 지역
  const cy = ly + E.REGIONS.length * 40 + 10;
  if (G.missionDone >= E.REGIONS.length) { text('모든 지역을 되찾았어요!', W / 2, cy + 80, 20, C.goldEdge, 'center'); return; }
  const r = E.REGIONS[G.missionDone], open = E.missionOpen(G);
  const place = img(`${ART}/village/${r.img}.webp`);
  box(x + 12, cy, w - 24, 230, 'rgba(7,19,28,.8)', C.edge, 10);
  if (place) { ctx.save(); rr(x + 12, cy, w - 24, 230, 10); ctx.clip(); drawFit(place, x + 12, cy - 30, w - 24, 230, 0.28); ctx.restore(); }
  box(x + 18, cy + 8, 230, 28, 'rgba(7,19,28,.85)', null, 6);
  fitText(`${r.ch}장 · ${r.place}`, x + 26, cy + 22, 216, 15, C.goldEdge);
  const rwAll = [E.wispText(r.reward), r.pick ? E.pickText(r.pick) : '', r.gold ? `새우 ${r.gold}` : ''].filter(Boolean).join(' · ');
  box(x + 18, cy + 40, w - 36, 24, 'rgba(7,19,28,.85)', null, 6);
  fitText(`보상: ${rwAll}`, x + 26, cy + 52, w - 52, 13, C.gold);
  const t = E.missionTarget(G), sh = shade(r.look);
  if (sh) drawFit(sh, W / 2 - 50, cy + 68, 100, 94);
  box(x + 40, cy + 166, w - 80, 12, 'rgba(0,0,0,.6)', C.line, 6, 1);
  box(x + 40, cy + 166, (w - 80) * Math.max(0, t.hp / t.max), 12, '#ff6b6b', null, 6);
  box(x + 24, cy + 184, w - 48, 40, 'rgba(7,19,28,.88)', null, 8);
  fitText(`그림자 대장 체력 ${Math.max(0, Math.ceil(t.hp)).toLocaleString()} · 방어력 ${t.armor} · 마법방어 ${t.mr}`, W / 2, cy + 196, w - 60, 14, C.text, 'center');
  const away = E.awayUnits(G);
  if (coop()) {
    const sh2 = E.missionShare(G), pct = (v) => `${Math.round(100 * v / t.max)}%`;
    fitText(`내 갈매미 ${away.length}마리 · 내 피해 ${pct(sh2.mine)} · 친구 피해 ${pct(sh2.peer)}`, W / 2, cy + 214, w - 60, 14, sh2.mine + sh2.peer > 0 ? C.good : C.muted, 'center');
  } else text(away.length ? `탐사 중 ${away.length}마리` : '아직 보낸 갈매미가 없어요', W / 2, cy + 214, 14, away.length ? C.good : C.muted, 'center');
  // 보낸 갈매미
  const ay = cy + 240;
  away.slice(0, 20).forEach((u, i) => { const xx = x + 14 + (i % 10) * 48, yy = ay + Math.floor(i / 10) * 48; box(xx, yy, 44, 44, 'rgba(11,30,42,.85)', GRADE_COLOR[E.UNIT[u.id].grade], 6, 1.5); drawFit(unitImg(u.id), xx + 2, yy + 2, 40, 40); if (E.EXPLORE[u.id]) text('★', xx + 37, yy + 9, 13, C.goldEdge, 'center'); });
  // 판에 있는 탐사 특기 갈매미 안내(보내면 좋은 것)
  const sp = E.fieldUnits(G).filter((u) => E.EXPLORE[u.id]);
  if (!away.length) fitText(sp.length ? `판에 탐사 특기 ${sp.length}마리: ${[...new Set(sp.map((u) => `${E.UNIT[u.id].name}(×${E.EXPLORE[u.id]})`))].join(' · ')}` : `탐사 특기: ${Object.entries(E.EXPLORE).map(([id, m]) => `${E.UNIT[id].name}×${m}`).join(' · ')}`, W / 2, ay + 18, w - 30, 13, sp.length ? C.goldEdge : C.muted, 'center');
  const by = y + h - 64, sel = selUnits();
  button(x + 14, by, (w - 40) / 2, 50, sel.length ? `고른 ${sel.length}마리 보내기` : '판에서 먼저 고르기', () => { if (sel.length) { sendSelected(sel); } else modal = null; }, sel.length ? 'primary' : 'normal');
  button(x + 26 + (w - 40) / 2, by, (w - 40) / 2, 50, '모두 불러오기', () => { const n = E.recall(G); say(`${n}마리가 돌아왔어요.`); }, 'normal', away.length > 0);
}
// 등급별 강화 창: 5칸(커먼·언커먼·레어·에픽·전설 이상)
function drawUpgrade() {
  shadeAll(0.3);   // 게임은 계속 흐름(위 정보 줄이 보이게 아래쪽에)
  const x = 20, y = 100, w = W - 40, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('등급별 강화', x + 18, y + 30, 22, C.text);
  text('게임은 멈추지 않아요', x + 150, y + 31, 13, C.bad);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(`한 단계마다 그 등급 공격력 +${Math.round(E.RULES.upgPct * 100)}% · 칸마다 ${E.RULES.upgMax}단계까지(풀강) · 높은 등급일수록 비싸요`, x + 18, y + 64, w - 36, 14, C.muted);
  const sh = img(`${ART}/menu/currency-shrimp.webp`);
  if (sh) drawFit(sh, x + 18, y + 80, 24, 24);
  text(`가진 새우 ${G.gold}`, x + 48, y + 93, 17, C.goldEdge);
  const have = Array(E.UPG_COUNT).fill(0);
  for (const u of E.fieldUnits(G)) have[E.upgGroup(E.tierOf(u.id))]++;
  const color = ['UNCOMMON', 'RARE', 'EPIC', 'LEGEND', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'];
  for (let gi = 0; gi < E.UPG_COUNT; gi++) {
    const yy = y + 112 + gi * 84, cost = E.upgradeCost(G, gi), col = GRADE_COLOR[color[gi]];
    box(x + 14, yy, w - 28, 70, 'rgba(11,30,42,.85)', col, 10, 2);
    text(E.UPG_NAME[gi], x + 30, yy + 22, 19, col);
    const maxed = E.upgMaxed(G, gi);
    fitText(`${G.upg[gi]}/${E.RULES.upgMax}단계 · 공격력 +${Math.round(G.upg[gi] * E.RULES.upgPct * 100)}% · 판에 ${have[gi]}마리`, x + 30, yy + 50, w - 230, 15, maxed ? C.goldEdge : C.text);
    if (maxed) button(x + w - 28 - 150, yy + 8, 150, 54, '최대(풀강)', () => {}, 'normal', false);
    else button(x + w - 28 - 150, yy + 8, 150, 54, '강화', () => { const r = E.upgrade(G, gi); if (!r.ok && r.why) say(r.why); }, 'primary', G.gold >= cost, `새우 ${cost}`);
  }
}
function drawPause() {
  shadeAll();
  if (coop()) {
    const x = 60, y = 300, w = W - 120, h = 300;
    box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
    text('잠시 멈춤', W / 2, y + 44, 24, C.text, 'center');
    text(`웨이브 ${G.wave} · 처치 ${G.kills} · 경고 ${G.warn}`, W / 2, y + 82, 15, C.muted, 'center');
    fitText('같이하기 중에는 친구 판도 함께 멈춰요', W / 2, y + 110, w - 30, 14, C.gold, 'center');
    button(x + 30, y + 136, w - 60, 56, '계속하기 (둘 다)', () => setPause(false), 'primary');
    button(x + 30, y + 208, w - 60, 48, '방 나가기', () => leaveRoom(), 'quiet');
    return;
  }
  const x = 60, y = 300, w = W - 120, h = 320;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('잠시 멈춤', W / 2, y + 44, 24, C.text, 'center');
  text(`${G.mode !== 'normal' ? `[${E.modeOf(G).name}] ` : ''}웨이브 ${G.wave} · 처치 ${G.kills} · 경고 ${G.warn}`, W / 2, y + 84, 15, C.muted, 'center');
  button(x + 30, y + 118, w - 60, 56, '계속하기', () => { modal = null; }, 'primary');
  button(x + 30, y + 186, w - 60, 48, '처음부터 다시', () => start(undefined, G.mode), 'normal');
  button(x + 30, y + 244, w - 60, 48, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
}
function drawOver() {
  shadeAll(0.75);
  if (coop()) return drawOverCoop();
  const x = 40, y = 250, w = W - 80, h = 420, win = G.over === 'win';
  box(x, y, w, h, C.panel, win ? C.goldEdge : '#b4482a', 14, 2);
  text(win ? '방어 성공!' : '방어 실패', W / 2, y + 48, 28, win ? C.goldEdge : C.bad, 'center');
  ctx.save(); ctx.beginPath(); ctx.rect(x + 20, y + 70, w - 40, 64); ctx.clip(); wrap(G.reason, x + 30, y + 78, w - 60, 15, C.text); ctx.restore();
  text(win ? `${E.RULES.waves}웨이브 클리어` : `${G.wave}웨이브`, W / 2, y + 160, 22, C.text, 'center');
  fitText(`처치 ${G.kills} · 외부탐사 ${G.missionDone}곳 · 강화 ${G.upg.join('/')}`, W / 2, y + 196, w - 40, 15, C.muted, 'center');
  text(`${G.mode !== 'normal' ? `[${E.modeOf(G).name}] ` : ''}최고 기록 ${bestText(bestOf(G.mode))}`, W / 2, y + 226, 15, C.gold, 'center');
  button(x + 30, y + 266, w - 60, 58, '다시 하기', () => start(undefined, G.mode), 'primary');
  button(x + 30, y + 336, w - 60, 48, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
}
function drawOverCoop() {
  const x = 40, y = 230, w = W - 80, h = 470, win = G.over === 'win';
  box(x, y, w, h, C.panel, win ? C.goldEdge : '#b4482a', 14, 2);
  text(win ? '방어 성공!' : '방어 실패', W / 2, y + 46, 28, win ? C.goldEdge : C.bad, 'center');
  ctx.save(); ctx.beginPath(); ctx.rect(x + 20, y + 66, w - 40, 64); ctx.clip(); wrap(G.reason, x + 30, y + 74, w - 60, 15, C.text); ctx.restore();
  text(win ? `${E.RULES.waves}웨이브 클리어` : `${G.wave}웨이브`, W / 2, y + 150, 22, C.text, 'center');
  const P = net.peer;
  const friend = !net.peerOn ? (net.peerGone ? '친구가 방을 나갔어요' : '친구 연결이 끊겼어요') : !P ? '친구 판을 받는 중…' : P.ov ? `친구: ${P.ov === 'win' ? '클리어' : `${num(P.w)}웨이브에서 끝`}` : `친구는 아직 ${num(P.w)}웨이브에서 버티는 중`;
  fitText(friend, W / 2, y + 186, w - 40, 16, P?.ov === 'win' ? C.goldEdge : C.gold, 'center');
  button(x + 30, y + 214, w - 60, 50, '친구 판 보기', () => { modal = { kind: 'peer' }; }, 'normal', !!P);
  if (net.seat === 0) {
    const ok = net.peerOn && !!P?.ov;
    button(x + 30, y + 276, w - 60, 62, '같이 다시 하기', () => sendCtl({ start: true }), 'primary', ok, ok ? '둘이 함께 새 판을 시작해요' : '친구 판이 끝나면 누를 수 있어요');
  } else fitText('방장이 「같이 다시 하기」를 누르면 함께 시작해요', W / 2, y + 307, w - 40, 15, C.muted, 'center');
  button(x + 30, y + 352, w - 60, 48, '방 나가기', () => leaveRoom(), 'quiet');
  text(`최고 기록 ${bestText(best)}`, W / 2, y + 432, 15, C.gold, 'center');
}
function drawTitleBack() {   // 화면 전체 너비
  drawBackground();
  const vil = img(`${ART}/village/identity-coast-panorama.webp`);
  if (vil) { const s = Math.max(W * 1.6, VW) / vil.naturalWidth; ctx.globalAlpha = 0.9; ctx.drawImage(vil, (VW - vil.naturalWidth * s) / 2, 120, vil.naturalWidth * s, vil.naturalHeight * s); ctx.globalAlpha = 1; }
  ctx.fillStyle = 'rgba(7,19,28,.35)'; ctx.fillRect(0, 0, VW, VH);
}
function drawTitle() {
  text('갈매미맨', W / 2, 86, 30, C.gold, 'center');
  text('랜덤 디펜스', W / 2, 134, 46, C.goldEdge, 'center');
  ['u01', 'u38', 'u60', 'u33', 'u05'].forEach((id, k) => drawFit(unitImg(id), 40 + k * 94, 360, 90, 110));
  box(28, 492, W - 56, 234, C.panel, C.edge, 12);
  [
    '· 위습을 누르면 갈매미가 나와요.',
    '· 갈매미는 끌어서 옮기고, 누르면 정보·버튼이 나와요.',
    '· 조합법대로 모아 「조합」하면 더 센 갈매미!',
    '· ☆목표를 찍으면 필요한 재료에 색 테두리가 생겨요.',
    `· 길 위의 적이 ${E.RULES.limit}마리를 넘지 않게 막으세요.`,
    '· 보스는 그 웨이브 안에 꼭 잡아야 해요.',
  ].forEach((l, k) => fitText(l, 44, 520 + k * 36, W - 88, 16, C.text));
  box(28, 734, W - 56, 44, 'rgba(243,216,158,.12)', C.gold, 10, 1.5);
  fitText(`♪ 게임 속 배경음악이 나와요. 오른쪽 위 ♪에서 음량을 조절하세요${music.on ? '' : ' (지금 꺼짐)'}`, W / 2, 756, W - 80, 14, C.goldEdge, 'center');
  const half = (W - 92) / 2;
  button(40, 786, half, 40, '도전 모드', () => { modal = { kind: 'modes' }; }, 'normal');
  if (resumeMeta) button(52 + half, 786, half, 40, `이어하기 · ${resumeMeta.wave}웨이브${resumeMeta.mode && resumeMeta.mode !== 'normal' ? ` (${E.MODES[resumeMeta.mode]?.name ?? ''})` : ''}`, resume, 'primary');
  else button(52 + half, 786, half, 40, '이어할 판 없음', () => {}, 'normal', false);
  button(40, 834, (W - 92) / 2, 72, '혼자 하기', () => start(), 'primary');
  button(52 + (W - 92) / 2, 834, (W - 92) / 2, 72, '친구랑 같이하기', () => { scene = 'lobby'; lobby.mode = 'menu'; lobby.msg = ''; lobby.note = ''; musicPlay(); }, 'normal', true, '코드로 방 들어가기');
  fitText(`최고 기록 ${bestText(best)} · 로그인 없이 하는 미니 게임 · 기록·이어하기는 이 기기에만`, W / 2, 930, W - 40, 14, C.muted, 'center');
  musicButton(W - 62, 14);
  fitText(music.on ? `배경음악 ${Math.round(music.vol * 100)}%` : '배경음악 끔', W - 72, 34, 120, 13, C.muted, 'right');
}
// 소리 설정 창: 배경음악 켜기·끄기, 음량(막대를 누르거나 −/+)
// 선택 위습 창: 등급 칸을 고르고, 그 등급 갈매미를 눌러 소환
function drawPick() {
  shadeAll(0.35);
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('선택 위습', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText('원하는 갈매미를 눌러 소환해요 · 스토리(외부탐사)를 깰 때마다 받아요', x + 18, y + 64, w - 36, 13, C.muted);
  const tw = (w - 24 - 18) / 4;
  E.PICK_GRADES.forEach((gr, k) => {
    const tx = x + 12 + k * (tw + 6), on = modal.grade === gr, n = G.picks[gr] ?? 0;
    box(tx, y + 82, tw, 52, on ? 'rgba(243,216,158,.14)' : 'rgba(11,30,42,.8)', on ? C.goldEdge : GRADE_COLOR[gr], 8, on ? 2.5 : 1.5);
    fitText(E.GRADE_NAME[gr], tx + tw / 2, y + 100, tw - 8, 15, GRADE_COLOR[gr], 'center');
    text(`×${n}`, tx + tw / 2, y + 121, 15, n ? C.text : '#6f7f88', 'center');
    hit(tx, y + 82, tw, 52, () => { modal.grade = gr; });
  });
  const gr = modal.grade, n = G.picks[gr] ?? 0, list = E.unitsOfGrade(gr);
  const cw = (w - 24 - 18) / 4, ch = 168;
  list.forEach((id, i) => {
    const cx = x + 12 + (i % 4) * (cw + 6), cy = y + 148 + Math.floor(i / 4) * (ch + 8), u = E.UNIT[id], k = E.kit(id);
    box(cx, cy, cw, ch, n ? 'rgba(11,30,42,.9)' : 'rgba(11,30,42,.5)', n ? GRADE_COLOR[gr] : '#33434d', 10, n ? 2 : 1);
    drawFit(unitImg(id), cx + 8, cy + 6, cw - 16, 96, n ? 1 : 0.45);
    fitText(u.name, cx + cw / 2, cy + 116, cw - 10, 16, n ? C.text : '#6f7f88', 'center');
    fitText(`${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx + cw / 2, cy + 138, cw - 10, 13, k.type === 'phys' ? C.phys : C.mag, 'center');
    fitText(E.kitTips(k)[0] ?? '기본 공격', cx + cw / 2, cy + 156, cw - 10, 12, C.muted, 'center');
    if (n) hit(cx, cy, cw, ch, () => { const r = E.summonPick(G, id); if (r.ok) { say(`${u.name} 소환!`); if (!(G.picks[gr] > 0)) modal = null; } else if (r.why) say(r.why); });
  });
  if (!n) fitText(`${E.GRADE_NAME[gr]} 선택 위습이 없어요`, W / 2, y + h - 30, w - 40, 15, C.muted, 'center');
}
// 창고: 같은 갈매미끼리 묶어 보여 주고 꺼내기·팔기. 창고 것도 조합 재료로 쓰인다
function drawStorage() {
  shadeAll(0.3);
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  const list = E.storedUnits(G);
  text(`창고 ${list.length}마리`, x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText('창고 갈매미는 싸우지 않지만 조합 재료로는 쓰여요 · 게임은 멈추지 않아요', x + 18, y + 62, w - 36, 13, C.muted);
  // 등급별 한 번에 넣기 + 자동(새로 부른 갈매미가 바로 창고로)
  text('등급별 한 번에 넣기', x + 18, y + 90, 15, C.gold);
  fitText('고정한 건 그대로 · 자동 = 새로 부른 것 바로 창고로', x + w - 18, y + 91, w - 190, 12, C.muted, 'right');
  const gw = (w - 24 - 5 * 6) / 6;
  E.STORE_GROUPS.forEach((q, i) => {
    const gx = x + 12 + i * (gw + 6), n = E.storeGroupUnits(G, q.key).length, col = GRADE_COLOR[q.grades[0]], on = !!G.autoGrades?.[q.key];
    button(gx, y + 104, gw, 50, `${q.name}`, () => { const m = E.storeGroup(G, q.key); say(m ? `${q.name} ${m}마리를 창고에 넣었어요.` : `판에 넣을 ${q.name}이(가) 없어요.`); }, 'normal', n > 0, `넣기 ${n}`, n ? col : null);
    if (q.auto) button(gx, y + 158, gw, 30, on ? '자동 켬' : '자동 끔', () => { const m = E.setAutoGrade(G, q.key, !on); say(!on ? `${q.name} 자동 창고 켬${m ? ` (${m}마리 넣음)` : ''}` : `${q.name} 자동 창고 끔`); }, on ? 'primary' : 'quiet');
    else fitText('자동 없음', gx + gw / 2, y + 173, gw - 6, 11, '#6f7f88', 'center');
  });
  const groups = [];
  for (const u of list) { let gr = groups.find((q) => q.id === u.id); if (!gr) groups.push(gr = { id: u.id, uids: [] }); gr.uids.push(u.uid); }
  groups.sort((a, b) => (E.tierOf(a.id) - E.tierOf(b.id)) || a.id.localeCompare(b.id));
  const top = y + 198, viewH = h - 208 - 56, rowH = 62, total = groups.length * rowH;
  modal.maxScroll = Math.max(0, total - viewH);
  if (!groups.length) fitText('창고가 비어 있어요. 갈매미를 고르고 「창고로」를 누르세요.', W / 2, top + 60, w - 40, 15, C.muted, 'center');
  ctx.save(); rr(x + 6, top, w - 12, viewH, 8); ctx.clip();
  const before = hits.length;
  groups.forEach((gr, i) => {
    const yy = top + i * rowH - modal.scroll;
    if (yy + rowH < top || yy > top + viewH) return;
    const u = E.UNIT[gr.id], k = E.kit(gr.id);
    box(x + 12, yy + 3, w - 24, rowH - 6, 'rgba(11,30,42,.85)', GRADE_COLOR[u.grade], 8, 1.5);
    drawFit(unitImg(gr.id), x + 16, yy + 6, 46, rowH - 12);
    fitText(`${u.name} ×${gr.uids.length}`, x + 68, yy + 22, 150, 16, C.text);
    text(`${E.GRADE_NAME[u.grade]} · 판매 ${k.sell}`, x + 68, yy + 43, 12, C.muted);
    const bx = x + w - 12 - 4 - 3 * 82;
    button(bx, yy + 9, 76, rowH - 18, '1 꺼내기', () => { E.unstore(G, [gr.uids[0]]); }, 'normal');
    button(bx + 82, yy + 9, 76, rowH - 18, '모두', () => { E.unstore(G, gr.uids); say(`${u.name} ${gr.uids.length}마리를 꺼냈어요.`); }, 'normal');
    button(bx + 164, yy + 9, 76, rowH - 18, `1 팔기`, () => { E.sell(G, gr.uids[0]); }, 'danger');
  });
  ctx.restore();
  hits = hits.filter((hh, k) => k < before || (hh.y >= top && hh.y + hh.h <= top + viewH));
  if (modal.maxScroll > 0) { const bh = Math.max(40, viewH * viewH / total), by2 = top + (viewH - bh) * (modal.scroll / modal.maxScroll); box(x + w - 8, by2, 4, bh, 'rgba(243,216,158,.5)', null, 2); }
  button(x + 18, y + h - 52, w - 36, 42, '모두 꺼내기', () => { const n = E.unstore(G, list.map((u) => u.uid)); say(`${n}마리를 꺼냈어요.`); }, 'normal', list.length > 0);
}
// 도전 모드 고르기(첫 화면)
function drawModes() {
  shadeAll();
  const x = 28, y = 170, w = W - 56, keys = Object.keys(E.MODES), h = 120 + keys.length * 104;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('도전 모드', x + 20, y + 34, 24, C.text);
  button(x + w - 96, y + 14, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText('규칙을 바꿔 도전해요 · 최고 기록은 모드마다 따로 남아요', x + 20, y + 72, w - 40, 14, C.muted);
  keys.forEach((k, i) => {
    const m = E.MODES[k], yy = y + 96 + i * 104;
    box(x + 14, yy, w - 28, 92, 'rgba(11,30,42,.85)', k === 'normal' ? C.edge : C.gold, 10, 1.5);
    text(m.name, x + 30, yy + 26, 19, k === 'normal' ? C.text : C.goldEdge);
    fitText(m.desc, x + 30, yy + 54, w - 200, 14, C.muted);
    fitText(`최고 기록 ${bestText(bestOf(k))}`, x + 30, yy + 76, w - 200, 13, C.gold);
    button(x + w - 28 - 110, yy + 20, 110, 52, '시작', () => start(undefined, k), 'primary');
  });
}
function drawSound() {
  shadeAll();
  const x = 50, y = 300, w = W - 100, h = 330;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('소리 설정', x + 20, y + 32, 22, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  text('배경음악', x + 24, y + 96, 18, C.text);
  button(x + w - 24 - 120, y + 74, 120, 44, music.on ? '켬' : '끔', musicToggle, music.on ? 'primary' : 'normal');
  text(`음량 ${Math.round(music.vol * 100)}%`, x + 24, y + 160, 18, music.on ? C.text : C.muted);
  const bx = x + 24 + 56, bw = w - 48 - 112, by = y + 196;
  button(x + 24, by - 4, 48, 44, '−', () => setVolume(music.vol - 0.05), 'normal');
  button(x + w - 24 - 48, by - 4, 48, 44, '+', () => setVolume(music.vol + 0.05), 'normal');
  box(bx, by + 12, bw, 12, 'rgba(0,0,0,.5)', C.line, 6, 1);
  box(bx, by + 12, bw * music.vol, 12, music.on ? C.gold : '#6f7f88', null, 6);
  ctx.beginPath(); ctx.arc(bx + bw * music.vol, by + 18, 11, 0, Math.PI * 2); ctx.fillStyle = music.on ? C.goldEdge : '#8b9aa3'; ctx.fill();
  hit(bx - 14, by - 10, bw + 28, 56, (p) => setVolume((p.x - bx) / bw), true);   // 누르거나 끌어서
  fitText('막대를 누르거나 끌어서, 또는 −/+로 조절해요 · 이 기기에 기억돼요', x + w / 2, y + h - 40, w - 40, 13, C.muted, 'center');
}

// 가로 화면에서 연 창이 없을 때 오른쪽: 안내만(창처럼 보이지 않게 흐리게)
function drawSideEmpty() {
  box(14, 100, W - 28, H - 108, 'rgba(11,30,42,.35)', 'rgba(145,122,76,.35)', 14, 1.5);
  text('열린 창 없음', W / 2, 430, 20, C.muted, 'center');
  fitText('아래 판의 선택위습·강화·외부탐사·창고·조합법을 누르면 여기에 열려요', W / 2, 470, W - 80, 14, '#8b9aa3', 'center');
  fitText('창을 연 채로도 판의 갈매미를 옮길 수 있어요', W / 2, 498, W - 80, 14, '#8b9aa3', 'center');
}
// ── 갈매미 간단 설명(사용자 요청 2026-11-08): 조합법 줄·재료·목표 칸·고른 갈매미 그림을 누르면 뜸, 게임은 계속 흐름 ──
// 유틸(이속감소·스턴·방깍 …)과 「지금 필요한 하위 재료」를 피라미드(위=만들 것, 아래로 재료)로 한 번에 보여 준다
let info = null;
function showInfo(id, fromCard = false) {   // 설명 안에서 재료를 누르면 그 재료로 파고들고 「뒤로」로 돌아옴
  if (!E.UNIT[id]) return;
  info = { id, stack: fromCard && info ? [...info.stack, info.id] : [] };
}
// 재료 나무(사용자 요청 2026-11-08: 피라미드 화면 개편): 위에 만들 것, 각 재료는 자기 바로 아래에 선으로 이어짐.
// 너무 넓으면 보이는 단 수를 줄이고, 더 아래가 있는 재료는 「+」(누르면 그 재료 기준으로 펼침)
function treeLayout(root, availW) {
  const fit = (md) => {
    const vis = (n, d) => { n.d = d; n.show = d < md && !n.have ? n.kids : []; n.more = !n.have && n.kids.length > 0 && !n.show.length; n.leaves = n.show.length ? n.show.reduce((a, c) => a + vis(c, d + 1), 0) : 1; return n.leaves; };
    return vis(root, 0);
  };
  let md = 3;
  while (md > 1 && fit(md) * 46 > availW) md--;
  fit(md);
  let depth = 0;
  const walk = (n) => { depth = Math.max(depth, n.d); n.show.forEach(walk); };
  walk(root);
  return depth;
}
function drawCheck(cx, cy, r, fill, mark) {   // 동그란 작은 표시: ✓ · ! · +
  ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fillStyle = fill; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = '#07131c'; ctx.stroke();
  ctx.strokeStyle = '#07131c'; ctx.lineWidth = 2.2; ctx.beginPath();
  if (mark === 'v') { ctx.moveTo(cx - r * 0.45, cy); ctx.lineTo(cx - r * 0.1, cy + r * 0.4); ctx.lineTo(cx + r * 0.5, cy - r * 0.4); ctx.stroke(); }
  else if (mark === '+') { ctx.moveTo(cx - r * 0.5, cy); ctx.lineTo(cx + r * 0.5, cy); ctx.moveTo(cx, cy - r * 0.5); ctx.lineTo(cx, cy + r * 0.5); ctx.stroke(); }
  else { ctx.moveTo(cx, cy - r * 0.5); ctx.lineTo(cx, cy + r * 0.15); ctx.stroke(); ctx.beginPath(); ctx.arc(cx, cy + r * 0.48, 1.4, 0, Math.PI * 2); ctx.fillStyle = '#07131c'; ctx.fill(); }
}
function drawTree(root, x0, y0, w, h) {
  const depth = treeLayout(root, w);
  const rowH = Math.min(118, h / (depth + 1)), R = [30, 25, 21, 19];
  const place = (n, a, b) => { n.cx = (a + b) / 2; n.cy = y0 + 34 + n.d * rowH; let s = a; for (const c of n.show) { const e = s + (b - a) * c.leaves / n.leaves; place(c, s, e); s = e; } n.span = b - a; };
  place(root, x0, x0 + w);
  const lines = (n) => {   // 부모 아래 → 가로 → 자식 위
    const r = R[n.d], top = n.cy + r + 18, my = (top + n.cy + rowH - (R[n.d + 1] ?? 19)) / 2;
    for (const c of n.show) {
      ctx.strokeStyle = c.have ? 'rgba(111,208,138,.55)' : 'rgba(243,216,158,.45)'; ctx.lineWidth = 1.8;
      ctx.beginPath(); ctx.moveTo(n.cx, top); ctx.lineTo(n.cx, my); ctx.lineTo(c.cx, my); ctx.lineTo(c.cx, c.cy - R[c.d]); ctx.stroke();
      lines(c);
    }
  };
  lines(root);
  const nodes = (n) => {
    const u = E.UNIT[n.id], col = GRADE_COLOR[u.grade], r = Math.min(R[n.d], n.span / 2 - 4);
    ctx.beginPath(); ctx.arc(n.cx, n.cy, r, 0, Math.PI * 2);
    ctx.fillStyle = n.have ? 'rgba(43,122,98,.45)' : 'rgba(11,30,42,.96)'; ctx.fill();
    ctx.lineWidth = n.top ? 3 : 2.2; ctx.strokeStyle = n.top ? C.goldEdge : col; ctx.stroke();
    ctx.save(); ctx.beginPath(); ctx.arc(n.cx, n.cy, r - 1.5, 0, Math.PI * 2); ctx.clip();
    drawFit(unitImg(n.id), n.cx - r * 0.95, n.cy - r * 0.95, r * 1.9, r * 1.85, n.have || n.top ? 1 : 0.75);
    ctx.restore();
    const br = Math.max(6.5, r * 0.36), bx = n.cx + r * 0.72, by = n.cy - r * 0.72;
    if (n.have) drawCheck(bx, by, br, '#6fd08a', 'v');
    else if (n.more) drawCheck(bx, by, br, C.goldEdge, '+');
    else if (!n.top && !n.kids.length) drawCheck(bx, by, br, '#ff9a8a', '!');
    fitText(u.name, n.cx, n.cy + r + 10, Math.max(40, n.span - 4), n.d === 0 ? 14 : 12, n.have ? C.good : n.top ? C.goldEdge : C.text, 'center');
    if (!n.top) hit(n.cx - n.span / 2, n.cy - r, n.span, r * 2 + 18, () => showInfo(n.id, true));
    n.show.forEach(nodes);
  };
  nodes(root);
}
function drawInfo() {
  const id = info.id, u = E.UNIT[id], k = E.kit(id), col = GRADE_COLOR[u.grade], r = E.recipeFor(id);
  ctx.fillStyle = 'rgba(3,9,14,.62)'; ctx.fillRect(0, 0, W, H);
  hit(0, 0, W, H, () => { info = null; });   // 바깥을 누르면 닫힘
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, col, 14, 2);
  hit(x, y, w, h, () => {});
  drawFit(unitImg(id), x + 14, y + 12, 80, 86);
  fitText(u.name, x + 106, y + 28, w - 220, 24, C.text);
  let cx = x + 106;
  cx += chip(E.GRADE_NAME[u.grade], cx, y + 50, col) + 6;
  chip(`${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx, y + 50, k.type === 'phys' ? C.phys : C.mag);
  fitText(`공격 ${Math.round(k.dmg * E.upgMul(G, k.tier)).toLocaleString()} · ${k.gap <= 0.6 ? '빠름' : k.gap >= 1.1 ? '느림' : '보통'}(${k.gap}초) · 사거리 ${k.range}`, x + 106, y + 92, w - 120, 14, C.muted);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { info = null; }, 'quiet');
  if (info.stack.length) { const back = info.stack[info.stack.length - 1]; button(x + w - 96, y + 56, 82, 36, '← 뒤로', () => { info = { id: back, stack: info.stack.slice(0, -1) }; }, 'normal'); }
  // 유틸
  text('유틸·특수', x + 16, y + 124, 15, C.gold);
  const tips = E.kitTips(k);
  if (tips.length) chipRow(tips.map((t) => [t, k.type === 'phys' ? C.phys : C.mag]), x + 16, y + 138, x + w - 12, 14);
  else text('기본 공격만 해요', x + 16, y + 150, 14, C.muted);
  // 재료 피라미드
  const py = y + 200;
  ctx.fillStyle = 'rgba(145,122,76,.35)'; ctx.fillRect(x + 12, py - 14, w - 24, 1);
  if (!r) {
    fitText(`조합법이 없어요 · ${E.GRADE_NAME[u.grade]} 위습으로 소환해요`, W / 2, py + 30, w - 40, 15, C.muted, 'center');
  } else {
    text('재료 나무', x + 16, py + 6, 15, C.gold);
    // 표시 설명
    let lx = x + 100;
    for (const [fill, mark, t] of [['#6fd08a', 'v', '가진 것'], ['#ff9a8a', '!', '위습으로 소환'], [C.goldEdge, '+', '누르면 더 아래']]) { drawCheck(lx, py + 6, 7, fill, mark); text(t, lx + 11, py + 7, 12, C.muted); ctx.font = `12px ${FONT}`; lx += 28 + ctx.measureText(t).width; }
    drawTree(needTree(id, E.countOnField(G)), x + 10, py + 18, w - 20, y + h - 146 - (py + 18));
    // 맨 아래: 없는 기본 재료(조합법이 없는 것)
    const base = {};
    const walk = (n) => { if (!n.have && !n.kids.length && !n.top) base[n.id] = (base[n.id] ?? 0) + 1; n.kids.forEach(walk); };
    walk(needTree(id, E.countOnField(G)));
    const ready = !!E.recipeUnits(G, r), miss = Object.entries(base).map(([b, n]) => `${E.UNIT[b].name}×${n}`).join(' · ');
    box(x + 12, y + h - 132, w - 24, 50, 'rgba(243,216,158,.08)', 'rgba(243,216,158,.3)', 8, 1);
    ctx.save(); ctx.beginPath(); ctx.rect(x + 12, y + h - 132, w - 24, 50); ctx.clip();
    if (ready) fitText('재료가 모두 있어요! 바로 조합할 수 있어요.', W / 2, y + h - 107, w - 40, 15, C.good, 'center');
    else if (miss) wrap(`더 필요한 기본 재료(위습으로 소환): ${miss}`, x + 22, y + h - 127, w - 44, 13, C.goldEdge, 19);
    else fitText('하위 재료는 다 있어요. 아래 단부터 조합해 올라가세요.', W / 2, y + h - 107, w - 40, 14, C.goldEdge, 'center');
    ctx.restore();
    const gi = G.goals.indexOf(id), bw = (w - 40) / 2;
    button(x + 14, y + h - 76, bw, 56, gi >= 0 ? '★ 목표에서 빼기' : '☆ 목표로', () => { const on = E.toggleGoal(G, id); say(on ? `목표: ${u.name} (하위 재료까지 색 테두리)` : '목표에서 뺐어요'); }, gi >= 0 ? 'primary' : 'normal');
    button(x + 26 + bw, y + h - 76, bw, 56, '조합', () => { const nu = E.combine(G, r); if (nu) { selected = [nu.uid]; say(`${u.name} 완성!`); info = null; } }, 'primary', ready);
  }
}
// 피해 순위표(사용자 요청 2026-11-08): 갈매미마다 이번 판·이번 웨이브에 준 피해(외부탐사 대장 포함)
const fmtN = (n) => (n >= 1e8 ? `${(n / 1e8).toFixed(1)}억` : n >= 1e4 ? `${(n / 1e4).toFixed(1)}만` : `${Math.round(n)}`);
function drawMeter() {
  shadeAll(0.3);
  const x = 14, y = 100, w = W - 28, h = H - 108;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('피해 순위', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const rows = G.units.map((u) => ({ u, d: G.dmg?.[u.uid] ?? 0, dw: G.dmgWave?.[u.uid] ?? 0 })).filter((r) => r.d > 0).sort((a, b) => b.d - a.d);
  const total = rows.reduce((a, r) => a + r.d, 0), top = rows[0]?.d ?? 1;
  fitText(`이번 판 전체 ${fmtN(total)} · 외부탐사 대장 피해 포함 · 판매한 갈매미는 빠져요 · 게임은 계속 흘러요`, x + 18, y + 62, w - 36, 12, C.muted);
  const lt = y + 80, viewH = h - 90, rowH = 54;
  modal.maxScroll = Math.max(0, rows.length * rowH - viewH);
  if (!rows.length) fitText('아직 피해를 준 갈매미가 없어요.', W / 2, lt + 60, w - 40, 15, C.muted, 'center');
  ctx.save(); rr(x + 6, lt, w - 12, viewH, 8); ctx.clip();
  rows.forEach((r, i) => {
    const yy = lt + i * rowH - modal.scroll;
    if (yy + rowH < lt || yy > lt + viewH) return;
    const info2 = E.UNIT[r.u.id], col = GRADE_COLOR[info2.grade];
    box(x + 12, yy + 3, w - 24, rowH - 6, 'rgba(11,30,42,.85)', i < 3 ? C.goldEdge : 'rgba(145,122,76,.35)', 8, i < 3 ? 2 : 1);
    text(`${i + 1}`, x + 30, yy + rowH / 2, 15, i < 3 ? C.goldEdge : C.muted, 'center');
    drawFit(unitImg(r.u.id), x + 42, yy + 6, 40, rowH - 12);
    fitText(`${info2.name}${r.u.away ? ' (탐사)' : r.u.stored ? ' (창고)' : ''}`, x + 88, yy + 18, 150, 15, col);
    const bx = x + 88, bw = w - 24 - 88 - 110;
    box(bx, yy + 31, bw, 9, 'rgba(0,0,0,.45)', null, 4);
    box(bx, yy + 31, bw * r.d / top, 9, col, null, 4);
    fitText(`${fmtN(r.d)} · ${Math.round(100 * r.d / total)}%`, x + w - 22, yy + 18, 104, 14, C.text, 'right');
    fitText(`이번 웨이브 ${fmtN(r.dw)}`, x + w - 22, yy + 37, 104, 12, C.muted, 'right');
  });
  ctx.restore();
}
// ── 처음 하는 사람 안내(사용자 요청 2026-11-08): 첫 판에서만, 위습 부르기 → 끌어 옮기기 → 조합법 → 설명 ──
let tut = null;
function tutDone() { tut = null; ls.set(TUT_KEY, '1'); }
function drawTutorial() {
  const now = performance.now();
  if (tut.step === 0 && G.units.length > tut.n0) { tut.step = 1; tut.mv = moveCount; }
  if (tut.step === 1 && moveCount > tut.mv) tut.step = 2;
  if (tut.step === 2 && modal?.kind === 'book') { tut.step = 3; tut.t = now; }
  if (tut.step === 3 && (info || now - tut.t > 9000)) { tutDone(); return; }
  if (info || (modal && !(tut.step === 3 && modal.kind === 'book'))) return;   // 다른 창을 가리지 않게(조합법 단계만 창 위에)
  const b5 = (W - 44 - 4 * 6) / 5, b4 = (W - 44 - 24) / 5, newest = E.fieldUnits(G).slice(-1)[0];
  const steps = [
    { msg: '① 커먼 위습을 눌러 갈매미를 불러 보세요', tx: 22 + b5 / 2, ty: 672 + 32, by: 560 },
    { msg: '② 갈매미를 끌어서 길(적이 지나가는 곳) 옆으로 옮겨 보세요', tx: newest?.x ?? W / 2, ty: (newest?.y ?? 400) - 56, by: (newest?.y ?? 400) > 400 ? 150 : 470 },
    { msg: '③ 위습을 더 불러 조합법대로 모으면 더 센 갈매미! 「조합법」을 눌러 보세요', tx: 22 + 4 * (b4 + 6) + b4 / 2, ty: 772, by: 640 },
    { msg: '④ 조합법 줄을 누르면 설명과 재료 나무가 나와요. 이제 직접 해 보세요!', tx: null, ty: null, by: H - 96 },
  ];
  const st = steps[tut.step], bw = W - 60, bh = 74, bx = 30;
  let by = st.by;
  ctx.save();
  if (tut.step === 3 && landscape) { ctx.translate(SIDE_X, 0); OX = SIDE_X; }
  box(bx, by, bw, bh, 'rgba(19,44,59,.97)', C.goldEdge, 12, 2);
  ctx.save(); ctx.beginPath(); ctx.rect(bx + 10, by + 6, bw - 120, bh - 12); ctx.clip(); wrap(st.msg, bx + 14, by + 12, bw - 130, 15, C.text, 22); ctx.restore();
  button(bx + bw - 100, by + 18, 88, 38, '안내 끄기', tutDone, 'quiet');
  if (st.tx != null) {   // 가리키는 화살표
    const ay = st.ty > by ? by + bh : by, dir = st.ty > by ? 1 : -1, ax = Math.max(bx + 20, Math.min(bx + bw - 20, st.tx));
    const bob = Math.sin(now / 220) * 4;
    ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(st.tx, st.ty - dir * (10 - bob)); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(st.tx, st.ty + dir * bob); ctx.lineTo(st.tx - 9, st.ty - dir * (14 - bob)); ctx.lineTo(st.tx + 9, st.ty - dir * (14 - bob)); ctx.closePath(); ctx.fillStyle = C.goldEdge; ctx.fill();
  }
  ctx.restore(); OX = 0;
}
function drawModal() {
  if (modal?.kind === 'book') drawBook();
  else if (modal?.kind === 'mission') drawMission();
  else if (modal?.kind === 'upgrade') drawUpgrade();
  else if (modal?.kind === 'sound') drawSound();
  else if (modal?.kind === 'pick') drawPick();
  else if (modal?.kind === 'storage') drawStorage();
  else if (modal?.kind === 'pause') drawPause();
  else if (modal?.kind === 'over') drawOver();
  else if (modal?.kind === 'peer') drawPeerModal();
  else if (modal?.kind === 'meter') drawMeter();
}
// ── 친구랑 같이하기(사용자 요청 2026-11-08: 코드를 입력해 방에 들어가고, 친구 화면을 옆에 실시간으로) ──
// 각자 자기 판을 하고, 0.25초마다 자기 판 모습(짧은 상태 글)을 서버(/defense/room)에 보내며 친구 판 모습을 받아 그린다.
// 시작(방장)·속도·일시정지는 둘이 함께 바뀐다. 서버는 전달만 하고 게임 판정은 각자 화면에서 한다.
const net = { code: '', token: '', seat: -1, ver: -1, round: 0, peer: null, prev: null, prevMap: null, peerAt: 0, prevAt: 0, peerRaw: '', peerOn: false, peerEver: false, peerGone: false, pend: null, busy: false, err: '', last: 0 };
const lobby = { mode: 'menu', input: '', msg: '', note: '', wait: false };
const inRoom = () => !!net.token;
const coop = () => inRoom() && net.round > 0;
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
async function roomApi(path, body) {
  const r = await fetch(`/defense/room${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body ?? {}), cache: 'no-store' });
  let j = {};
  try { j = await r.json(); } catch { j = {}; }
  if (!r.ok) { const e = new Error(j.error || `연결 오류(${r.status})`); e.status = r.status; throw e; }
  return j;
}
function resetNet() {
  Object.assign(net, { code: '', token: '', seat: -1, ver: -1, round: 0, peer: null, prev: null, prevMap: null, peerAt: 0, prevAt: 0, peerRaw: '', peerOn: false, peerEver: false, peerGone: false, pend: null, err: '', giftSent: 0, giftRecv: 0 });
}
async function makeRoom() {
  if (lobby.wait) return;
  lobby.wait = true; lobby.msg = ''; lobby.note = '';
  try { const j = await roomApi('', {}); resetNet(); Object.assign(net, { code: j.code, token: j.token, seat: 0 }); saveRoom(); lobby.mode = 'host'; netTick(); }
  catch (e) { lobby.msg = e.message; }
  finally { lobby.wait = false; }
}
async function joinRoom(code) {
  if (lobby.wait) return;
  if (!/^[0-9]{6}$/.test(code)) { lobby.msg = '코드는 숫자 6자리예요.'; return; }
  lobby.wait = true; lobby.msg = ''; lobby.note = '';
  try { const j = await roomApi('/join', { code }); resetNet(); Object.assign(net, { code: j.code, token: j.token, seat: 1 }); saveRoom(); lobby.mode = 'guest'; netTick(); }
  catch (e) { lobby.msg = e.message; }
  finally { lobby.wait = false; }
}
function typeDigit(d) {
  if (lobby.input.length >= 6 || lobby.wait) return;
  lobby.input += d; lobby.msg = '';
  if (lobby.input.length === 6) joinRoom(lobby.input);   // 6자리를 다 누르면 바로 들어가기
}
// 다시 접속(사용자 요청 2026-11-08): 방 열쇠와 같이하기 판을 기기에 두고, 새로고침해도 같은 자리·같은 판으로 돌아온다
function saveRoom() { ls.set(ROOM_KEY, JSON.stringify({ code: net.code, token: net.token, seat: net.seat })); }
function forgetRoom() { ls.del(ROOM_KEY); ls.del(ROOM_SAVE_KEY); }
async function tryRejoin() {
  let c = null;
  try { c = JSON.parse(ls.get(ROOM_KEY) ?? 'null'); } catch { c = null; }
  if (!c?.code || !c?.token) return false;
  try {
    const j = await roomApi('/sync', { code: c.code, token: c.token });
    resetNet(); Object.assign(net, { code: c.code, token: c.token, seat: c.seat });
    const round = num(j.ctl?.round);
    let sv = null;
    try { sv = JSON.parse(ls.get(ROOM_SAVE_KEY) ?? 'null'); } catch { sv = null; }
    const g = round > 0 && sv && sv.code === c.code && sv.round === round ? E.loadGame(sv.save) : null;
    if (g) { G = g; net.round = round; net.giftSent = num(sv.gs); net.giftRecv = num(sv.gr); scene = 'play'; modal = null; selected = []; info = null; say('같이하기 판으로 돌아왔어요.'); }
    else if (round > 0) say('같이하기 판 기록이 없어 새 판으로 시작해요.');
    else { scene = 'lobby'; lobby.mode = c.seat === 0 ? 'host' : 'guest'; }
    onSync(j);
    return true;
  } catch (e) { if (e.status === 404) forgetRoom(); return false; }
}
function leaveRoom(toTitle = true) {
  if (net.token) fetch('/defense/room/leave', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: net.code, token: net.token }), keepalive: true }).catch(() => {});
  resetNet(); forgetRoom();
  if (toTitle) { scene = 'title'; modal = null; G = null; lobby.mode = 'menu'; lobby.input = ''; lobby.msg = ''; lobby.note = ''; }
}
// 화면을 닫아도 방에서 바로 나가지 않는다(새로고침하면 다시 접속). 8초 넘게 소식이 없으면 친구 화면에 「연결 끊김」
function roomGone() {   // 방이 닫힘(방장이 나갔거나 오래 비어 있었음)
  const wasPlaying = coop() && scene === 'play' && G && !G.over;
  resetNet(); forgetRoom();
  if (scene === 'lobby') { lobby.mode = 'menu'; lobby.msg = '방이 닫혔어요. 새 방을 만들거나 코드를 다시 입력해 주세요.'; }
  else if (wasPlaying) { if (modal?.kind === 'pause') modal = null; say('친구 방이 닫혀 혼자 계속해요.'); }
}
// 새우 보내기(사용자 요청 2026-11-08): 보낸 합계를 내 판 모습에 실어 보내고, 친구 화면이 늘어난 만큼 받는다
const GIFT = 50;
const canGift = () => coop() && G && !G.over && G.gold >= GIFT && net.peerOn && net.peer && !net.peer.ov;
function sendGift() { if (!canGift()) return; G.gold -= GIFT; net.giftSent += GIFT; say(`친구에게 새우 ${GIFT}을(를) 보냈어요.`); netTick(); }
function sendCtl(c) { net.pend = { ...(net.pend ?? {}), ...c }; netTick(); }
function setPause(on) {
  if (!G || G.over) return;
  modal = on ? { kind: 'pause' } : null;
  if (coop()) sendCtl({ paused: on });
}
function setSpeed(v) { speed = v; if (coop()) sendCtl({ speed: v }); }
// 내 판 모습: 적(번호·자리·체력%·보스·모습), 판 위 갈매미(자리·종류), 숫자 몇 개
function snapOf(g) {
  const r = Math.round;
  return JSON.stringify({
    r: net.round, w: g.wave, wn: g.warn, g: g.gold, k: g.kills, md: g.missionDone, ov: g.over || '', nx: Math.max(0, Math.ceil(E.nextWaveIn(g))),
    a: E.awayUnits(g).length, st: E.storedUnits(g).length, up: g.upg, gs: net.giftSent, mdg: g.missionDmg.map((v) => Math.round(v ?? 0)),
    e: g.enemies.slice(0, 150).map((e) => [e.id, r(e.x), r(e.y), r(100 * Math.max(0, e.hp) / e.max), e.boss ? 1 : 0, e.look]),
    u: E.fieldUnits(g).slice(0, 160).map((u) => [r(u.x), r(u.y), u.id]),
  });
}
async function netTick() {
  if (!net.token || net.busy) return;
  net.busy = true; net.last = performance.now();
  const tok = net.token, body = { code: net.code, token: tok };
  if (net.pend) { body.ctl = net.pend; net.pend = null; }
  if (scene === 'play' && G && coop()) body.snap = snapOf(G);
  try {
    const j = await roomApi('/sync', body);
    if (net.token === tok) { net.err = ''; onSync(j); }
  } catch (e) {
    if (net.token === tok) {
      if (e.status === 404) roomGone();
      else { net.err = '연결이 불안정해요…'; if (body.ctl) net.pend = { ...body.ctl, ...(net.pend ?? {}) }; }
    }
  } finally { net.busy = false; }
}
setInterval(() => { if (net.token && performance.now() - net.last >= (scene === 'play' ? 240 : 900)) netTick(); }, 50);
function onSync(j) {
  const c = j.ctl;
  if (c && c.ver !== net.ver) { net.ver = c.ver; applyCtl(c); }
  const p = j.peer;
  net.peerOn = !!p?.on;
  if (net.peerOn) { net.peerEver = true; net.peerGone = false; } else if (!p && net.peerEver) net.peerGone = true;
  if (p?.snap && p.snap !== net.peerRaw) {
    net.peerRaw = p.snap;
    let s = null;
    try { s = JSON.parse(p.snap); } catch { s = null; }
    if (s && typeof s === 'object' && s.r === net.round) {   // 지난 판 모습은 버림
      net.prev = net.peer; net.prevAt = net.peerAt; net.peer = s; net.peerAt = performance.now();
      net.prevMap = new Map((Array.isArray(net.prev?.e) ? net.prev.e : []).filter(Array.isArray).map((e) => [e[0], e]));
      if (G && scene === 'play') {
        E.setPeerMission(G, s.mdg);   // 외부탐사를 둘이서 같이
        const gift = Math.min(1e7, num(s.gs) - net.giftRecv);   // 친구가 보낸 새우(보낸 합계가 늘어난 만큼)
        if (gift > 0) { net.giftRecv += gift; if (!G.over) { G.gold += gift; say(`친구가 새우 ${gift}을(를) 보냈어요!`); } }
      }
    }
  }
}
function applyCtl(c) {
  if (num(c.round) > net.round) {   // 방장이 시작함: 둘이 같은 씨앗으로 새 판
    net.round = num(c.round); net.peer = net.prev = net.prevMap = null; net.peerRaw = ''; net.giftSent = 0; net.giftRecv = 0;
    start(num(c.seed)); lobby.mode = net.seat === 0 ? 'host' : 'guest';
  }
  if (!coop() || scene !== 'play' || !G) return;
  speed = [1, 2, 3].includes(c.speed) ? c.speed : 1;
  if (G.over) return;
  if (c.paused && modal?.kind !== 'pause') modal = { kind: 'pause' };
  else if (!c.paused && modal?.kind === 'pause') modal = null;
}
async function shareLink() {
  const url = `${location.origin}/defense?room=${net.code}`;
  lobby.note = '';
  try { if (navigator.share) { await navigator.share({ title: '갈매미맨 랜덤 디펜스 같이하기', text: `방 코드 ${net.code}`, url }); return; } }
  catch (e) { if (e?.name === 'AbortError') return; }
  try { await navigator.clipboard.writeText(url); lobby.note = '링크를 복사했어요. 친구에게 보내 주세요.'; }
  catch { lobby.note = url; }
}

// 대기실(첫 화면과 같은 틀)
function codeBoxes(s, y) {
  const bw = 58, gap = 8, mid = 16, x0 = (W - (6 * bw + 5 * gap + mid)) / 2;
  for (let k = 0; k < 6; k++) {
    const x = x0 + k * (bw + gap) + (k >= 3 ? mid : 0);
    box(x, y, bw, 72, 'rgba(11,30,42,.92)', s[k] ? C.goldEdge : C.edge, 10, 2);
    if (s[k]) text(s[k], x + bw / 2, y + 38, 38, C.goldEdge, 'center');
  }
}
function keypad(y) {
  const kw = 124, kh = 60, gap = 10, x0 = (W - (3 * kw + 2 * gap)) / 2;
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', '지우기', '0', '들어가기'].forEach((key, i) => {
    const x = x0 + (i % 3) * (kw + gap), yy = y + Math.floor(i / 3) * (kh + gap);
    if (key === '지우기') button(x, yy, kw, kh, key, () => { lobby.input = lobby.input.slice(0, -1); lobby.msg = ''; }, 'quiet');
    else if (key === '들어가기') button(x, yy, kw, kh, lobby.wait ? '들어가는 중' : key, () => joinRoom(lobby.input), 'primary', lobby.input.length === 6 && !lobby.wait);
    else button(x, yy, kw, kh, key, () => typeDigit(key), 'normal');
  });
}
function drawLobby() {
  text('친구랑 같이하기', W / 2, 92, 36, C.goldEdge, 'center');
  box(28, 132, W - 56, 196, C.panel, C.edge, 12);
  [
    '· 6자리 코드로 친구 방에 들어가요(로그인 없음, 2명).',
    '· 각자 자기 판을 하고, 친구 판이 실시간으로 보여요.',
    '  가로 화면은 오른쪽, 세로 화면은 아래에 보여요.',
    '· 시작은 방장이 하고, 속도·일시정지는 둘이 함께 바뀌어요.',
    '· 방장이 나가면 방이 닫혀요.',
  ].forEach((l, k) => fitText(l, 44, 160 + k * 34, W - 88, 15, C.text));
  const y = 356;
  if (lobby.mode === 'menu') {
    button(60, y, W - 120, 76, lobby.wait ? '방 만드는 중…' : '방 만들기', makeRoom, 'primary', !lobby.wait, '코드를 친구에게 알려 주세요');
    button(60, y + 94, W - 120, 76, '코드 입력해서 들어가기', () => { lobby.mode = 'enter'; lobby.input = ''; lobby.msg = ''; }, 'normal', true, '친구가 알려 준 6자리');
    button(60, y + 188, W - 120, 56, '뒤로', () => { scene = 'title'; lobby.msg = ''; }, 'quiet');
  } else if (lobby.mode === 'enter') {
    box(28, y - 16, W - 56, 124, C.panel, C.edge, 12);   // 글자가 배경 그림에 묻히지 않게
    text('친구가 알려 준 방 코드', W / 2, y + 4, 17, C.gold, 'center');
    codeBoxes(lobby.input, y + 24);
    keypad(y + 120);
    button(60, y + 412, W - 120, 52, '뒤로', () => { lobby.mode = 'menu'; lobby.msg = ''; }, 'quiet');
  } else {
    const host = lobby.mode === 'host';
    box(28, y - 16, W - 56, 160, C.panel, C.edge, 12);
    text(host ? '내 방 코드' : '들어간 방', W / 2, y + 4, 17, C.gold, 'center');
    codeBoxes(net.code, y + 24);
    const st = net.peerOn ? (host ? '친구가 들어왔어요!' : '방장과 연결됐어요. 방장이 시작하면 바로 시작해요.') : (host ? '친구를 기다리는 중… 코드나 링크를 보내 주세요.' : '방장을 기다리는 중…');
    fitText(st, W / 2, y + 126, W - 60, 17, net.peerOn ? C.good : C.muted, 'center');
    if (host) {
      button(60, y + 152, W - 120, 56, '링크 보내기', shareLink, 'normal', true, '열면 바로 이 방에 들어와요');
      button(60, y + 224, W - 120, 76, '시작하기', () => sendCtl({ start: true }), 'primary', net.peerOn, net.peerOn ? '둘이 함께 시작해요' : '친구가 들어오면 누를 수 있어요');
    }
    button(60, y + 316, W - 120, 52, '방 나가기', () => leaveRoom(), 'quiet');
  }
  if (lobby.msg || net.err) fitText(lobby.msg || net.err, W / 2, 878, W - 40, 15, C.bad, 'center');
  else if (lobby.note) fitText(lobby.note, W / 2, 878, W - 40, 15, C.good, 'center');
  fitText('방·판 모습은 서버 메모리에만 잠깐 있고 저장하지 않아요', W / 2, 930, W - 40, 13, C.muted, 'center');
}

// 친구 판 그리기: 받은 두 모습 사이를 이어 그려 부드럽게(0.25초 늦게 보임)
function drawPeerField(P) {
  drawLanes();
  const gap = Math.max(120, Math.min(1000, net.peerAt - net.prevAt || 250));
  const f = Math.min(1, (performance.now() - net.peerAt) / gap);
  for (const e of Array.isArray(P.e) ? P.e : []) {
    if (!Array.isArray(e)) continue;
    let x = num(e[1]), y = num(e[2]);
    const q = net.prevMap?.get(e[0]);
    if (q && Math.hypot(num(q[1]) - x, num(q[2]) - y) < 160) { x = num(q[1]) + (x - num(q[1])) * f; y = num(q[2]) + (y - num(q[2])) * f; }
    const boss = e[4] === 1, sz = boss ? 76 : 38, sh = E.UNIT[e[5]] ? shade(e[5]) : null;
    if (sh) drawFit(sh, x - sz / 2, y - sz * 0.75, sz, sz);
    else { ctx.beginPath(); ctx.arc(x, y - sz / 3, sz / 3, 0, Math.PI * 2); ctx.fillStyle = 'rgba(42,20,66,.92)'; ctx.fill(); }
    const bw = boss ? 70 : 32, bx = x - bw / 2, by = y - sz * 0.75 - 6;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, Math.min(100, num(e[3]))) / 100, 5, boss ? '#ff6b6b' : '#6fd08a', null, 2);
  }
  const units = (Array.isArray(P.u) ? P.u : []).filter((u) => Array.isArray(u) && E.UNIT[u[2]]).sort((a, b) => num(a[1]) - num(b[1]));
  for (const u of units) {
    drawUnitShape(u[2], num(u[0]), num(u[1]), GRADE_COLOR[E.UNIT[u[2]].grade], 2);
  }
  if (P.ov) {
    const win = P.ov === 'win', cy = FIELD.y + FIELD.h / 2;
    box(FIELD.x + 60, cy - 46, FIELD.w - 120, 92, 'rgba(7,19,28,.92)', win ? C.goldEdge : '#b4482a', 12, 2);
    text(win ? '친구 방어 성공!' : '친구 방어 실패', W / 2, cy - 14, 26, win ? C.goldEdge : C.bad, 'center');
    text(win ? `${E.RULES.waves}웨이브 클리어` : `${num(P.w)}웨이브`, W / 2, cy + 22, 18, C.text, 'center');
  }
}
const peerState = () => (net.peerOn ? ['● 실시간', C.good] : net.peerGone ? ['● 친구가 나감', C.bad] : ['● 연결 끊김', C.bad]);
function peerCount(P) {
  const cnt = {};
  for (const u of Array.isArray(P.u) ? P.u : []) if (Array.isArray(u) && E.UNIT[u[2]]) { const g = E.UNIT[u[2]].grade; cnt[g] = (cnt[g] ?? 0) + 1; }
  return cnt;
}
function chipRow(items, x, y, maxX, size = 14) {   // 넘치면 다음 줄
  let cx = x, cy = y;
  for (const [s, col] of items) {
    ctx.font = `${size}px ${FONT}`;
    const w = ctx.measureText(s).width + 14;
    if (cx + w > maxX && cx > x) { cx = x; cy += size + 14; }
    cx += chip(s, cx, cy, col, size) + 6;
  }
  return cy + size + 8;
}
// 친구 판 전체(가로 화면 오른쪽 창·크게 보기): 위 정보 줄 + 판 + 친구 상황
function drawPeerFull(withButtons = false) {
  const P = net.peer, [stText, stCol] = peerState();
  box(8, 6, W - 16, 88, C.header, C.edge, 10);
  chip('친구 판', 20, 15, C.goldEdge, 14);
  text(stText, W - 24, 27, 14, stCol, 'right');
  if (P) {
    text(`웨이브 ${num(P.w)}/${E.RULES.waves}`, 104, 27, 19, C.text);
    text(P.ov ? (P.ov === 'win' ? '방어 성공!' : '방어 실패') : `다음 웨이브 ${num(P.nx)}초`, 20, 56, 15, P.ov ? (P.ov === 'win' ? C.goldEdge : C.bad) : C.muted);
    const n = Array.isArray(P.e) ? P.e.length : 0, lim = E.RULES.limit, ratio = Math.min(1, n / lim);
    text(`적 ${n}/${lim}`, 20, 82, 14, n > lim ? C.bad : C.text);
    box(92, 77, 150, 10, 'rgba(0,0,0,.5)', C.line, 5, 1);
    box(92, 77, 150 * ratio, 10, n > lim ? '#e0533f' : ratio > 0.7 ? '#e8b04a' : '#6fd08a', null, 5);
    text(`경고 ${num(P.wn)}/${E.RULES.warnMax}`, 256, 82, 14, num(P.wn) ? C.bad : C.muted);
    const sh = img(`${ART}/menu/currency-shrimp.webp`);
    if (sh) drawFit(sh, 340, 64, 22, 22);
    text(`${num(P.g)}`, 366, 76, 16, C.goldEdge);
  } else text(net.peerOn ? '친구 판을 받는 중…' : '친구를 기다리는 중…', 104, 27, 17, C.muted);
  ctx.save(); ctx.beginPath(); ctx.rect(FIELD.x, FIELD.y, FIELD.w, FIELD.h); ctx.clip();
  if (P) drawPeerField(P); else drawLanes();
  ctx.restore();
  const y0 = 672;
  box(8, y0, W - 16, H - y0 - 8, C.panel, C.edge, 12);
  text('친구 상황', 22, y0 + 24, 17, C.gold);
  if (P) {
    fitText(`처치 ${num(P.k)} · 외부탐사 ${num(P.md)}곳 · 탐사 중 ${num(P.a)}마리 · 창고 ${num(P.st)}마리`, 22, y0 + 56, W - 44, 15, C.text);
    text('판 위 갈매미', 22, y0 + 86, 14, C.muted);
    const cnt = peerCount(P);
    const gy = chipRow(Object.keys(GRADE_COLOR).filter((g) => cnt[g]).map((g) => [`${E.GRADE_NAME[g]} ${cnt[g]}`, GRADE_COLOR[g]]), 22, y0 + 98, W - 22);
    text('등급별 강화', 22, gy + 12, 14, C.muted);
    const up = Array.isArray(P.up) ? P.up : [];
    chipRow(E.UPG_NAME.map((nm, gi) => [`${nm} ${num(up[gi])}`, num(up[gi]) >= E.RULES.upgMax ? C.goldEdge : C.muted]), 22, gy + 24, W - 22, 13);
  }
  fitText('친구 판은 보기만 해요 · 0.25초마다 새로 받아요', 22, H - 24, W - 44, 13, C.muted);
  if (withButtons) button(W - 22 - 150, 672 + 8, 150, 40, `새우 ${GIFT} 보내기`, sendGift, 'normal', canGift());
}
// 가로 화면에서 창을 연 동안: 오른쪽 위에 친구 소식 한 줄
function drawPeerBar() {
  const P = net.peer, [stText, stCol] = peerState();
  box(8, 6, W - 16, 88, C.header, C.edge, 10);
  chip('친구 판', 20, 15, C.goldEdge, 14);
  text(stText, 110, 27, 14, stCol);
  fitText(P ? (P.ov ? `${P.ov === 'win' ? '방어 성공' : '방어 실패'} · ${num(P.w)}웨이브` : `웨이브 ${num(P.w)} · 적 ${Array.isArray(P.e) ? P.e.length : 0}/${E.RULES.limit} · 경고 ${num(P.wn)}/${E.RULES.warnMax}`) : '친구 판을 받는 중…', 20, 66, W - 200, 16, C.text);
  if (modal && (NONPAUSE.has(modal.kind) || modal.kind === 'over')) button(W - 162, 22, 140, 56, '친구 판 보기', () => { modal = modal.kind === 'over' ? { kind: 'peer' } : null; }, 'normal');
}
// 세로 화면: 아래에 붙는 친구 판 줄(누르면 크게)
function drawPeerStrip() {
  const y0 = H + 2, P = net.peer, [stText, stCol] = peerState();
  box(8, y0, W - 16, PEER_H - 10, C.panel, C.edge, 12);
  const s = (PEER_H - 26) / FIELD.h, fw = FIELD.w * s, fh = FIELD.h * s, fx = 14, fy = y0 + 8;
  ctx.save(); ctx.beginPath(); ctx.rect(fx, fy, fw, fh); ctx.clip(); ctx.translate(fx, fy); ctx.scale(s, s); ctx.translate(-FIELD.x, -FIELD.y);
  if (P) drawPeerField(P); else drawLanes();
  ctx.restore();
  hit(fx, fy, fw, fh, () => { modal = { kind: 'peer' }; });
  const tx = fx + fw + 14, tw = W - 22 - tx;
  text('친구 판', tx, y0 + 22, 18, C.goldEdge);
  text(stText, W - 24, y0 + 22, 13, stCol, 'right');
  if (!P) fitText(net.peerOn ? '친구 판을 받는 중…' : '친구를 기다리는 중…', tx, y0 + 58, tw, 15, C.muted);
  else {
    text(P.ov ? `${P.ov === 'win' ? '방어 성공!' : '방어 실패'} · ${num(P.w)}웨이브` : `웨이브 ${num(P.w)}/${E.RULES.waves}`, tx, y0 + 52, 16, P.ov ? (P.ov === 'win' ? C.goldEdge : C.bad) : C.text);
    const n = Array.isArray(P.e) ? P.e.length : 0, lim = E.RULES.limit, ratio = Math.min(1, n / lim);
    text(`적 ${n}/${lim}`, tx, y0 + 78, 14, n > lim ? C.bad : C.text);
    box(tx + 74, y0 + 73, tw - 74, 10, 'rgba(0,0,0,.5)', C.line, 5, 1);
    box(tx + 74, y0 + 73, (tw - 74) * ratio, 10, n > lim ? '#e0533f' : ratio > 0.7 ? '#e8b04a' : '#6fd08a', null, 5);
    fitText(`경고 ${num(P.wn)}/${E.RULES.warnMax} · 새우 ${num(P.g)}`, tx, y0 + 103, tw, 14, num(P.wn) ? C.bad : C.muted);
    fitText(`처치 ${num(P.k)} · 탐사 ${num(P.md)}곳 · 창고 ${num(P.st)}`, tx, y0 + 126, tw, 13, C.muted);
  }
  const hw = (tw - 6) / 2;
  button(tx, y0 + PEER_H - 62, hw, 44, '크게 보기', () => { modal = { kind: 'peer' }; }, 'normal');
  button(tx + hw + 6, y0 + PEER_H - 62, hw, 44, `새우 ${GIFT} 보내기`, sendGift, 'normal', canGift());
}
// 세로 화면 크게 보기(게임은 계속 흐름)
function drawPeerModal() {
  shadeAll(0.85);
  const s = 0.86;
  ctx.save(); ctx.translate((W - W * s) / 2, 18); ctx.scale(s, s);
  ctx.fillStyle = C.bg; ctx.fillRect(4, 2, W - 8, H - 4);   // 아래 내 판이 비쳐 보이지 않게
  drawPeerFull(); ctx.restore();
  button(W / 2 - 110, H - 82, 220, 58, G?.over ? '내 결과 보기' : '닫기', () => { modal = null; }, 'primary');
}

// ── 반복 ──
let last = performance.now(), acc = 0;
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (running()) {
    acc += dt * speed * BASE_SPEED;
    while (acc >= 0.05) { E.step(G, 0.05); acc -= 0.05; }
    selected = selected.filter((uid) => { const u = E.unitById(G, uid); return u && !u.away; });
  }
  if (scene === 'play' && G?.over && modal?.kind !== 'over' && modal?.kind !== 'peer') { saveBest(); modal = { kind: 'over' }; }
  const wantStrip = coop() && scene === 'play';
  if (wantStrip !== stripOn) { stripOn = wantStrip; fit(); }
  musicSync();
  hits = [];
  ctx.setTransform(scale * dpr, 0, 0, scale * dpr, 0, 0);
  if (scene === 'title' || scene === 'lobby') {
    drawTitleBack();
    const tx = (VW - W) / 2;   // 가로 배치면 가운데
    ctx.save(); ctx.translate(tx, 0); OX = tx;
    if (scene === 'title') { drawTitle(); if (modal?.kind === 'sound') drawSound(); else if (modal?.kind === 'modes') drawModes(); } else drawLobby();
    ctx.restore(); OX = 0;
  } else {
    drawBackground(); drawField(); drawHud(); drawBottom();
    if (landscape) {   // 오른쪽 창: 열린 창, 없으면 조합법(같이하기면 친구 판)
      ctx.save(); ctx.translate(SIDE_X, 0); OX = SIDE_X;
      if (modal && modal.kind !== 'peer') { drawModal(); if (coop()) drawPeerBar(); }
      else if (coop()) {
        drawPeerFull(true);
        if (modal?.kind === 'peer') button(W - 150, 684, 128, 44, '내 결과 보기', () => { modal = null; }, 'primary');
      } else drawSideEmpty();
      if (info) drawInfo();   // 닫기를 누르면 오른쪽 창도 비어야 함(사용자 요청 2026-11-08: 전에는 조합법이 늘 떠 있어 닫히지 않는 것처럼 보였음)
      ctx.restore(); OX = 0;
    } else { if (stripOn) drawPeerStrip(); drawModal(); if (info) drawInfo(); }
  }
  if (tut && scene === 'play' && G && !G.over) drawTutorial();
  if (performance.now() < toast.until && toast.msg) {
    ctx.font = `15px ${FONT}`;
    const tw = Math.min(W - 40, ctx.measureText(toast.msg).width + 32);
    box((W - tw) / 2, 618, tw, 40, 'rgba(19,44,59,.96)', C.goldEdge, 10, 1.5);
    fitText(toast.msg, W / 2, 638, tw - 20, 15, C.text, 'center');
  }
  requestAnimationFrame(frame);
}
// /defense?room=123456 으로 열면 그 방에 바로 들어간다(방장이 보낸 링크)
{
  const q = new URLSearchParams(location.search).get('room');
  if (q && /^[0-9]{6}$/.test(q)) { forgetRoom(); scene = 'lobby'; lobby.mode = 'enter'; lobby.input = q; joinRoom(q); try { history.replaceState(null, '', '/defense'); } catch { /* 주소 정리 못 해도 진행 */ } }
  else tryRejoin();
}
(async () => {
  try { await Promise.race([document.fonts.load('20px "Workbench"', '갈매미'), new Promise((r) => setTimeout(r, 2500))]); } catch { /* 기본 글꼴 */ }
  document.getElementById('boot')?.remove();
  for (const u of E.UNITS) unitImg(u.id);
  requestAnimationFrame(frame);
})();

// 시험용(자동 캡처에서 상태를 만들 때만 씀)
window.__defense = { E, get G() { return G; }, get music() { return music; }, start, net, lobby, get scene() { return scene; }, get speed() { return speed; }, get modal() { return modal; }, set selected(v) { selected = v; }, get selected() { return selected; }, openBook, set modal(v) { modal = v; }, get info() { return info; }, showInfo, set peerSnap(v) { net.peer = v; net.peerAt = performance.now(); } };
