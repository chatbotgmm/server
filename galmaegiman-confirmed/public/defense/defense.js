// 갈매미맨 랜덤 디펜스 2판: 화면·입력(규칙은 engine.js). 논리 좌표 540×960을 화면 크기에 맞춰 그린다.
// 조작(사용자 요청 2026-11-08): 갈매미는 끌어서만 옮긴다 · 누르면 고르기 · 빈 곳을 끌면 여러 마리 고르기(고른 무리는 하나를 끌면 함께) · 다른 곳을 누르면 고르기 풀림.
import * as E from './engine.js?v=73c7dd2bf1ca';

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
const UNIT_R = 24;   // 갈매미 누르기 반지름
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
const hit = (x, y, w, h, fn) => hits.push({ x: x + OX, y, w, h, fn });
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
let best = 0;
try { best = Number(localStorage.getItem(BEST_KEY)) || 0; } catch { best = 0; }
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

function start(seed) {
  G = seed === undefined ? E.newGame() : E.newGame(seed >>> 0); scene = 'play'; modal = null; speed = 1; selected = []; lastNews = 0;
  musicPlay();   // 누른 순간 안에서 재생 시작
}
function musicButton(x, y) {
  button(x, y, 46, 40, '♪', () => { modal = { kind: 'sound' }; musicPlay(); }, 'normal');
  if (!music.on) { ctx.strokeStyle = C.bad; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.moveTo(x + 12, y + 30); ctx.lineTo(x + 34, y + 10); ctx.stroke(); }
}
function saveBest() {
  const w = G.over === 'win' ? E.RULES.waves + 1 : G.wave;
  if (w > best) { best = w; try { localStorage.setItem(BEST_KEY, String(best)); } catch { /* 저장 못 해도 진행 */ } }
}

// ── 입력 ──
let down = null;
function toLogical(ev) { const r = cv.getBoundingClientRect(); return { x: (ev.clientX - r.left) / scale, y: (ev.clientY - r.top) / scale }; }
const inField = (p) => p.x >= FIELD.x && p.y >= FIELD.y && p.x < FIELD.x + FIELD.w && p.y < FIELD.y + FIELD.h;
function unitAt(p) {   // 위에 그려진(나중) 것부터
  const list = E.fieldUnits(G);
  for (let i = list.length - 1; i >= 0; i--) { const u = list[i]; if (Math.hypot(u.x - p.x, u.y - 14 - p.y) <= UNIT_R) return u; }
  return null;
}
// 게임 시간이 흐르는가: 강화 창은 열어도 멈추지 않는다(사용자 요청)
const NONPAUSE = new Set(['upgrade', 'mission', 'pick', 'book', 'sound', 'storage', 'peer']);   // 일시정지(Ⅱ)·결과 창만 멈춤
const running = () => scene === 'play' && G && !G.over && (!modal || NONPAUSE.has(modal.kind));
const playing = () => scene === 'play' && G && !G.over && (!modal || (landscape && NONPAUSE.has(modal.kind)));
// 스크롤할 목록: 가로 배치는 오른쪽 창(창이 없으면 늘 떠 있는 조합법), 세로 배치는 열린 창
const sideBook = { kind: 'book', scroll: 0, scrollable: true, side: true };
function scrollerAt(p) {
  if (landscape) return p.x >= SIDE_X ? (modal ? (modal.scrollable ? modal : null) : (scene === 'play' && !coop() ? sideBook : null)) : null;
  return modal?.scrollable ? modal : null;
}
cv.addEventListener('pointerdown', (ev) => {
  const p = toLogical(ev);
  cv.setPointerCapture?.(ev.pointerId);
  const u = playing() && inField(p) ? unitAt(p) : null;
  const sc = scrollerAt(p);
  down = { x: p.x, y: p.y, x0: p.x, y0: p.y, unit: u, field: playing() && inField(p), scroller: sc, scroll0: sc?.scroll ?? 0, moved: false };
});
cv.addEventListener('pointermove', (ev) => {
  if (!down) return;
  const p = toLogical(ev);
  if (Math.hypot(p.x - down.x0, p.y - down.y0) > 10) down.moved = true;
  down.x = p.x; down.y = p.y;
  if (down.scroller && down.moved) down.scroller.scroll = Math.max(0, Math.min(down.scroller.maxScroll ?? 0, down.scroll0 - (p.y - down.y0)));
});
cv.addEventListener('pointerup', (ev) => {
  if (!down) return;
  const p = toLogical(ev), d = down;
  down = null;
  if (d.field && playing()) {
    if (d.unit && d.moved) {   // 끌어서 옮기기(고른 무리에 있으면 무리째)
      const group = selected.includes(d.unit.uid) && selected.length > 1 ? selUnits() : [d.unit];
      const dx = p.x - d.x0, dy = p.y - d.y0;
      for (const u of group) E.moveUnit(G, u.uid, u.tx + dx, u.ty + dy, true);
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
  for (let k = hits.length - 1; k >= 0; k--) {
    const h = hits[k];
    if (p.x >= h.x && p.y >= h.y && p.x < h.x + h.w && p.y < h.y + h.h) { h.fn(p); return; }
  }
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
    if (modal?.kind === 'pause') setPause(false); else if (modal) modal = null; else setPause(true);
    ev.preventDefault();
  }
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
function goalColors() {
  const out = {};
  G.goals.forEach((gid, k) => { const r = E.recipeFor(gid); if (r) for (const id of new Set(r.in)) (out[id] ??= []).push(GOAL_COLOR[k]); });
  return out;
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
    if (G.t < e.slowUntil) { ctx.beginPath(); ctx.arc(e.x, e.y + 2, sz / 3, 0, Math.PI * 2); ctx.strokeStyle = '#9fe8ff'; ctx.lineWidth = 1.5; ctx.stroke(); }
    const bw = e.boss ? 70 : 32, bx = e.x - bw / 2, by = e.y - sz * 0.75 - 6;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, e.hp / e.max), 5, e.boss ? '#ff6b6b' : '#6fd08a', null, 2);
  }
  // 갈매미(아래쪽부터 그려 겹침이 자연스럽게)
  const gc = goalColors(), selSet = new Set(selected);
  const list = [...E.fieldUnits(G)].sort((a, b) => a.y - b.y);
  for (const u of list) {
    const info = E.UNIT[u.id], col = GRADE_COLOR[info.grade];
    ctx.beginPath(); ctx.ellipse(u.x, u.y, 20, 7, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
    ctx.lineWidth = selSet.has(u.uid) ? 3 : 2; ctx.strokeStyle = selSet.has(u.uid) ? '#ffffff' : col; ctx.stroke();
    (gc[u.id] ?? []).forEach((c, k) => { ctx.beginPath(); ctx.ellipse(u.x, u.y, 24 + k * 4, 9 + k * 2, 0, 0, Math.PI * 2); ctx.strokeStyle = c; ctx.lineWidth = 2.5; ctx.stroke(); });
    drawFit(unitImg(u.id), u.x - 24, u.y - 50, 48, 50);
    if (G.t - u.born < 0.5) { ctx.globalAlpha = 1 - (G.t - u.born) / 0.5; ctx.beginPath(); ctx.arc(u.x, u.y - 20, 30, 0, Math.PI * 2); ctx.fillStyle = 'rgba(243,216,158,.35)'; ctx.fill(); ctx.globalAlpha = 1; }
  }
  // 겹친 갈매미 수(같은 자리 14px 안)
  const stacks = [];
  for (const u of list) { const st = stacks.find((q) => Math.abs(q.x - u.x) < 14 && Math.abs(q.y - u.y) < 14); if (st) { st.n++; st.x = u.x; st.y = u.y; } else stacks.push({ x: u.x, y: u.y, n: 1 }); }
  for (const st of stacks) if (st.n > 1) { box(st.x + 10, st.y - 54, 30, 20, 'rgba(7,19,28,.9)', C.goldEdge, 8, 1.5); text(`×${st.n}`, st.x + 25, st.y - 43.5, 13, C.goldEdge, 'center'); }
  // 끌기 표시
  if (down?.moved && down.field) {
    if (down.unit) {
      const group = selected.includes(down.unit.uid) && selected.length > 1 ? selUnits() : [down.unit];
      for (const u of group) drawFit(unitImg(u.id), u.x - 24 + down.x - down.x0, u.y - 50 + down.y - down.y0, 48, 50, 0.6);
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
  if (wi.boss) text(G.bossDone === G.wave ? '보스 처치! 다음 웨이브까지 ' + left + '초' : `보스를 ${left}초 안에 잡기`, 20, 58, 15, G.bossDone === G.wave ? C.good : left <= 12 ? C.bad : '#ffc2a8');
  else text(`다음 웨이브 ${left}초`, 20, 58, 15, C.muted);
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
  const msub = G.missionDone >= E.REGIONS.length ? '모두 완료' : away ? `${away}마리 탐사 중` : `${nextR.ch}장 가능`;
  button(22 + 2 * (b4 + 6), by, b4, 58, '외부탐사', () => { modal = { kind: 'mission' }; }, 'normal', true, msub);
  const st = E.storedUnits(G).length;
  button(22 + 3 * (b4 + 6), by, b4, 58, '창고', () => { modal = { kind: 'storage', scroll: 0, scrollable: true }; }, 'normal', true, `${st}마리${G.autoStore ? '·자동' : ''}`);
  button(22 + 4 * (b4 + 6), by, b4, 58, '조합법', () => openBook(), 'normal', true, `${E.RECIPES.length}개`);
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
    hit(x, gy, gw, 50, () => { if (ready) { const nu = E.combine(G, r); if (nu) { selected = [nu.uid]; say(`${E.UNIT[gid].name} 완성!`); } } else openBook(gid); });
  }
  fitText('끌기=옮기기 · 누르기=고르기 · 빈 곳 끌기=여러 마리 고르기', 22, H - 26, W - 44, 14, C.muted);
}
function have(r, cnt) { const left = { ...cnt }; let n = 0; for (const id of r.in) if ((left[id] ?? 0) > 0) { left[id]--; n++; } return n; }
function drawUnitPanel(y0, u) {
  const info = E.UNIT[u.id], k = E.kit(u.id);
  drawFit(unitImg(u.id), 18, y0 + 8, 68, 70);
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
  const by = H - 8 - 56, b4 = (W - 44 - 24) / 5;
  button(22, by, b4, 46, list.length > 2 ? `더보기+${list.length - 2}` : '조합법', () => openBook(null, u.id), 'normal');
  button(22 + (b4 + 6), by, b4, 46, '모으기', () => { selected = E.gather(G, u.uid); say(`같은 ${E.UNIT[u.id].name} ${selected.length}마리를 모았어요.`); }, 'normal');
  button(22 + 2 * (b4 + 6), by, b4, 46, '창고로', () => { E.store(G, [u.uid]); selected = []; say(`${E.UNIT[u.id].name}을(를) 창고에 넣었어요(조합 재료로는 쓰여요).`); }, 'normal');
  button(22 + 3 * (b4 + 6), by, b4, 46, '탐사', () => sendSelected([u]), 'normal', E.missionOpen(G));
  button(22 + 4 * (b4 + 6), by, b4, 46, `판매+${k.sell}`, () => { E.sell(G, u.uid); selected = []; }, 'danger');
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
  const b3 = (W - 44 - 12) / 3;
  button(22, by, b3, 46, `탐사 보내기 (${sel.length})`, () => sendSelected(sel), 'normal', E.missionOpen(G));
  button(22 + (b3 + 6), by, b3, 46, `창고로 (${sel.length})`, () => { const n = E.store(G, sel.map((u) => u.uid)); selected = []; say(`${n}마리를 창고에 넣었어요.`); }, 'normal');
  button(22 + 2 * (b3 + 6), by, b3, 46, '고르기 해제', () => { selected = []; }, 'quiet');
}
function sendSelected(list) {
  const n = E.sendAway(G, list.map((u) => u.uid));
  if (n) { say(`${n}마리를 외부탐사 「${E.REGIONS[G.missionDone].name}」에 보냈어요.`); selected = []; }
}
// 조합법 한 줄
function recipeRow(r, ok, x, y, w, h, cnt, keepUid = 0) {
  const out = E.UNIT[r.out], gi = G.goals.indexOf(r.out);
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
  if (!modal.side) button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
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
  fitText('처음부터 1장을 보낼 수 있고, 앞 장을 끝내면 다음 장이 열려요. 보낸 동안은 수비에서 빠져요.', x + 18, y + 64, w - 36, 13, C.muted);
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
  text(away.length ? `탐사 중 ${away.length}마리` : '아직 보낸 갈매미가 없어요', W / 2, cy + 214, 14, away.length ? C.good : C.muted, 'center');
  // 보낸 갈매미
  const ay = cy + 240;
  away.slice(0, 20).forEach((u, i) => { const xx = x + 14 + (i % 10) * 48, yy = ay + Math.floor(i / 10) * 48; box(xx, yy, 44, 44, 'rgba(11,30,42,.85)', GRADE_COLOR[E.UNIT[u.id].grade], 6, 1.5); drawFit(unitImg(u.id), xx + 2, yy + 2, 40, 40); });
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
  text(`웨이브 ${G.wave} · 처치 ${G.kills} · 경고 ${G.warn}`, W / 2, y + 84, 15, C.muted, 'center');
  button(x + 30, y + 118, w - 60, 56, '계속하기', () => { modal = null; }, 'primary');
  button(x + 30, y + 186, w - 60, 48, '처음부터 다시', () => start(), 'normal');
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
  text(`최고 기록 ${best > E.RULES.waves ? '클리어' : `${best}웨이브`}`, W / 2, y + 226, 15, C.gold, 'center');
  button(x + 30, y + 266, w - 60, 58, '다시 하기', () => start(), 'primary');
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
  text(`최고 기록 ${best > E.RULES.waves ? '클리어' : `${best}웨이브`}`, W / 2, y + 432, 15, C.gold, 'center');
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
  box(28, 492, W - 56, 290, C.panel, C.edge, 12);
  [
    '· 위습을 누르면 그 등급 갈매미를 무작위로 불러요.',
    '  처음 6개 + 웨이브마다 2개. 스토리를 깨면 선택 위습(원하는 갈매미).',
    '· 조합법대로 합쳐 강하게! ☆목표면 재료에 색 테두리.',
    '· 타격수=물딜, 술사=마딜, 고체력·기습=방깍·스턴·마방깍 보조.',
    '· 갈매미는 끌어서 아무 데나, 겹쳐 둬도 돼요. 빈 곳을 끌면 여러 마리.',
    `· 적 ${E.RULES.limit}마리 넘으면 1초마다 경고, ${E.RULES.warnMax}회 넘으면 패배.`,
    '· 보스는 그 라운드 안에 못 잡으면 바로 패배.',
  ].forEach((l, k) => fitText(l, 44, 522 + k * 36, W - 88, 15, C.text));
  text(`최고 기록: ${best ? (best > E.RULES.waves ? '클리어' : `${best}웨이브`) : '아직 없음'}`, W / 2, 808, 16, C.gold, 'center');
  button(40, 834, (W - 92) / 2, 72, '혼자 하기', () => start(), 'primary');
  button(52 + (W - 92) / 2, 834, (W - 92) / 2, 72, '친구랑 같이하기', () => { scene = 'lobby'; lobby.mode = 'menu'; lobby.msg = ''; lobby.note = ''; musicPlay(); }, 'normal', true, '코드로 방 들어가기');
  fitText('로그인 없이 하는 미니 게임 · 기록은 이 기기에만 남아요', W / 2, 930, W - 40, 14, C.muted, 'center');
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
  text('커먼 자동 창고', x + 18, y + 98, 17, C.text);
  fitText('켜면 판의 커먼과 새로 부른 커먼이 바로 창고로', x + 18, y + 120, w - 200, 12, C.muted);
  button(x + w - 18 - 120, y + 82, 120, 44, G.autoStore ? '켬' : '끔', () => { const n = E.setAutoStore(G, !G.autoStore); say(G.autoStore ? `커먼 자동 창고 켬${n ? ` (${n}마리 넣음)` : ''}` : '커먼 자동 창고 끔'); }, G.autoStore ? 'primary' : 'normal');
  const groups = [];
  for (const u of list) { let gr = groups.find((q) => q.id === u.id); if (!gr) groups.push(gr = { id: u.id, uids: [] }); gr.uids.push(u.uid); }
  groups.sort((a, b) => (E.tierOf(a.id) - E.tierOf(b.id)) || a.id.localeCompare(b.id));
  const top = y + 140, viewH = h - 150 - 56, rowH = 62, total = groups.length * rowH;
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
  hit(bx - 10, by - 6, bw + 20, 48, (p) => setVolume((p.x - bx) / bw));
  fitText('막대를 누르거나 −/+로 조절해요 · 설정은 이 기기에 기억돼요', x + w / 2, y + h - 40, w - 40, 13, C.muted, 'center');
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
  Object.assign(net, { code: '', token: '', seat: -1, ver: -1, round: 0, peer: null, prev: null, prevMap: null, peerAt: 0, prevAt: 0, peerRaw: '', peerOn: false, peerEver: false, peerGone: false, pend: null, err: '' });
}
async function makeRoom() {
  if (lobby.wait) return;
  lobby.wait = true; lobby.msg = ''; lobby.note = '';
  try { const j = await roomApi('', {}); resetNet(); Object.assign(net, { code: j.code, token: j.token, seat: 0 }); lobby.mode = 'host'; netTick(); }
  catch (e) { lobby.msg = e.message; }
  finally { lobby.wait = false; }
}
async function joinRoom(code) {
  if (lobby.wait) return;
  if (!/^[0-9]{6}$/.test(code)) { lobby.msg = '코드는 숫자 6자리예요.'; return; }
  lobby.wait = true; lobby.msg = ''; lobby.note = '';
  try { const j = await roomApi('/join', { code }); resetNet(); Object.assign(net, { code: j.code, token: j.token, seat: 1 }); lobby.mode = 'guest'; netTick(); }
  catch (e) { lobby.msg = e.message; }
  finally { lobby.wait = false; }
}
function typeDigit(d) {
  if (lobby.input.length >= 6 || lobby.wait) return;
  lobby.input += d; lobby.msg = '';
  if (lobby.input.length === 6) joinRoom(lobby.input);   // 6자리를 다 누르면 바로 들어가기
}
function leaveRoom(toTitle = true) {
  if (net.token) fetch('/defense/room/leave', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: net.code, token: net.token }), keepalive: true }).catch(() => {});
  resetNet();
  if (toTitle) { scene = 'title'; modal = null; G = null; lobby.mode = 'menu'; lobby.input = ''; lobby.msg = ''; lobby.note = ''; }
}
window.addEventListener('pagehide', () => { if (net.token) leaveRoom(false); });
function roomGone() {   // 방이 닫힘(방장이 나갔거나 오래 비어 있었음)
  const wasPlaying = coop() && scene === 'play' && G && !G.over;
  resetNet();
  if (scene === 'lobby') { lobby.mode = 'menu'; lobby.msg = '방이 닫혔어요. 새 방을 만들거나 코드를 다시 입력해 주세요.'; }
  else if (wasPlaying) { if (modal?.kind === 'pause') modal = null; say('친구 방이 닫혀 혼자 계속해요.'); }
}
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
    a: E.awayUnits(g).length, st: E.storedUnits(g).length, up: g.upg,
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
    }
  }
}
function applyCtl(c) {
  if (num(c.round) > net.round) {   // 방장이 시작함: 둘이 같은 씨앗으로 새 판
    net.round = num(c.round); net.peer = net.prev = net.prevMap = null; net.peerRaw = '';
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
    const x = num(u[0]), y = num(u[1]);
    ctx.beginPath(); ctx.ellipse(x, y, 20, 7, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
    ctx.lineWidth = 2; ctx.strokeStyle = GRADE_COLOR[E.UNIT[u[2]].grade]; ctx.stroke();
    drawFit(unitImg(u[2]), x - 24, y - 50, 48, 50);
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
function drawPeerFull() {
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
  button(tx, y0 + PEER_H - 62, tw, 44, '친구 판 크게 보기', () => { modal = { kind: 'peer' }; }, 'normal');
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
    if (scene === 'title') { drawTitle(); if (modal?.kind === 'sound') drawSound(); } else drawLobby();
    ctx.restore(); OX = 0;
  } else {
    drawBackground(); drawField(); drawHud(); drawBottom();
    if (landscape) {   // 오른쪽 창: 열린 창, 없으면 조합법(같이하기면 친구 판)
      ctx.save(); ctx.translate(SIDE_X, 0); OX = SIDE_X;
      if (modal && modal.kind !== 'peer') { drawModal(); if (coop()) drawPeerBar(); }
      else if (coop()) {
        drawPeerFull();
        if (modal?.kind === 'peer') button(W - 150, 684, 128, 44, '내 결과 보기', () => { modal = null; }, 'primary');
      } else { modal = sideBook; drawBook(); modal = null; }
      ctx.restore(); OX = 0;
    } else { if (stripOn) drawPeerStrip(); drawModal(); }
  }
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
  if (q && /^[0-9]{6}$/.test(q)) { scene = 'lobby'; lobby.mode = 'enter'; lobby.input = q; joinRoom(q); try { history.replaceState(null, '', '/defense'); } catch { /* 주소 정리 못 해도 진행 */ } }
}
(async () => {
  try { await Promise.race([document.fonts.load('20px "Workbench"', '갈매미'), new Promise((r) => setTimeout(r, 2500))]); } catch { /* 기본 글꼴 */ }
  document.getElementById('boot')?.remove();
  for (const u of E.UNITS) unitImg(u.id);
  requestAnimationFrame(frame);
})();

// 시험용(자동 캡처에서 상태를 만들 때만 씀)
window.__defense = { E, get G() { return G; }, get music() { return music; }, start, net, lobby, get scene() { return scene; }, get speed() { return speed; }, get modal() { return modal; }, set selected(v) { selected = v; }, get selected() { return selected; }, openBook, set modal(v) { modal = v; }, set peerSnap(v) { net.peer = v; net.peerAt = performance.now(); } };
