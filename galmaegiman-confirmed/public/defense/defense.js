// 갈매미맨 랜덤 디펜스: 화면·입력(규칙은 engine.js). 논리 좌표 540×960을 화면 크기에 맞춰 그린다.
import * as E from './engine.js';

const { W, H } = E;
const ART = '/game60/assets';
const C = {
  bg: '#07131c', panel: 'rgba(16,36,51,.94)', header: '#132c3b', slot: '#0b1e2a', text: '#f5ead0', muted: '#b0bec4',
  gold: '#d9bb7f', goldEdge: '#f3d89e', edge: '#917a4c', line: '#5b675d', ink: '#192e39', bad: '#ff9a8a', good: '#9fe3a8',
  lane: 'rgba(214,190,140,.22)', laneEdge: 'rgba(243,216,158,.35)',
};
const GRADE_COLOR = { COMMON: '#9aa5ad', UNCOMMON: '#62c370', RARE: '#4d9de0', EPIC: '#a77be8', LEGEND: '#f0a03c', HIDDEN: '#3fd0c0', LIMITED: '#ef5a5a', TRANSCEND: '#ff8fc7', IMMORTAL: '#f3d36b', ETERNAL: '#ffffff' };
const ELEM_COLOR = { FIRE: '#ff7a3c', WATER: '#4aa8ff', ICE: '#9fe8ff', LIGHTNING: '#ffe14a', NATURE: '#6ee06e', HOLY: '#fff2a8', DARK: '#b07cff', NORMAL: '#f5ead0' };
const GOAL_COLOR = ['#4fd1e8', '#f07ad8', '#a6e05a'];
const FONT = '"Workbench","Noto Sans KR","Apple SD Gothic Neo","Malgun Gothic",sans-serif';
const BEST_KEY = 'gmm-defense-best';

// ── 화면 준비 ──
const cv = document.getElementById('game');
const ctx = cv.getContext('2d');
let scale = 1, dpr = 1;
function fit() {
  dpr = Math.min(window.devicePixelRatio || 1, 3);
  scale = Math.min(window.innerWidth / W, window.innerHeight / H);
  cv.style.width = `${Math.floor(W * scale)}px`;
  cv.style.height = `${Math.floor(H * scale)}px`;
  cv.width = Math.floor(W * scale * dpr);
  cv.height = Math.floor(H * scale * dpr);
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
function shade(id) {   // 적: 갈매미 그림을 어두운 보라 그림자로
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
function rr(x, y, w, h, r) {
  ctx.beginPath();
  if (ctx.roundRect) ctx.roundRect(x, y, w, h, r); else ctx.rect(x, y, w, h);
}
function box(x, y, w, h, fill, stroke = null, r = 8, lw = 1.5) {
  rr(x, y, w, h, r);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
  if (stroke) { ctx.lineWidth = lw; ctx.strokeStyle = stroke; ctx.stroke(); }
}
function text(s, x, y, size = 18, color = C.text, align = 'left', base = 'middle', weight = '') {
  ctx.font = `${weight} ${size}px ${FONT}`;
  ctx.fillStyle = color; ctx.textAlign = align; ctx.textBaseline = base;
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
  const words = s.split(' ');
  let line = '', yy = y;
  for (const w of words) {
    const t = line ? `${line} ${w}` : w;
    if (ctx.measureText(t).width > maxW && line) { text(line, x, yy, size, color, 'left', 'top'); line = w; yy += lineH; } else line = t;
  }
  if (line) { text(line, x, yy, size, color, 'left', 'top'); yy += lineH; }
  return yy - y;
}

// ── 누르기 영역(그릴 때마다 다시 등록) ──
let hits = [];
const hit = (x, y, w, h, fn, opt = {}) => hits.push({ x, y, w, h, fn, ...opt });
function button(x, y, w, h, label, fn, style = 'normal', enabled = true, sub = '') {
  const S = {
    primary: ['linear', '#f3d89e', '#c9a463', C.ink, '#fff3cf'],
    normal: ['flat', '#163447', null, C.text, C.gold],
    quiet: ['flat', 'rgba(11,30,42,.6)', null, C.muted, C.line],
    danger: ['flat', '#5a2418', null, '#ffd9cf', '#b4482a'],
  }[style];
  if (S[0] === 'linear' && enabled) { const g = ctx.createLinearGradient(0, y, 0, y + h); g.addColorStop(0, S[1]); g.addColorStop(1, S[2]); box(x, y, w, h, g, S[4], 10, 2); }
  else box(x, y, w, h, enabled ? S[1] : '#1b2a33', enabled ? S[4] : '#33434d', 10, 1.5);
  const col = enabled ? S[3] : '#6f7f88';
  if (sub) { fitText(label, x + w / 2, y + h / 2 - 9, w - 16, 18, col, 'center'); fitText(sub, x + w / 2, y + h / 2 + 13, w - 16, 15, enabled ? (style === 'primary' ? '#3d2f1a' : C.muted) : '#5b6a73', 'center'); }
  else fitText(label, x + w / 2, y + h / 2, w - 16, 18, col, 'center');
  if (enabled) hit(x, y, w, h, fn);
}
function gradeChip(g, x, y) {
  ctx.font = `14px ${FONT}`;
  const t = E.GRADE_NAME[g], w = ctx.measureText(t).width + 14;
  box(x, y, w, 22, 'rgba(0,0,0,.35)', GRADE_COLOR[g], 6, 1.5);
  text(t, x + w / 2, y + 11.5, 14, GRADE_COLOR[g], 'center');
  return w;
}

// ── 상태 ──
let G = null;          // 게임 상태(engine)
let scene = 'title';   // title | play
let sel = -1;          // 고른 칸
let modal = null;      // null | {kind:'book'|'pause'|'over'|'help', scroll}
let speed = 1, paused = false;
let toast = { msg: '', until: 0 };
let best = 0;
try { best = Number(localStorage.getItem(BEST_KEY)) || 0; } catch { best = 0; }
const say = (msg) => { toast = { msg, until: performance.now() + 2200 }; };

function start() {
  G = E.newGame();
  scene = 'play'; sel = -1; modal = null; speed = 1; paused = false;
}
function saveBest() {
  const w = G.over === 'win' ? E.RULES.waves + 1 : G.wave;
  if (w > best) { best = w; try { localStorage.setItem(BEST_KEY, String(best)); } catch { /* 저장 못 해도 진행 */ } }
}

// ── 입력 ──
let down = null;   // {x,y,slot,scroll0,moved}
function toLogical(ev) {
  const r = cv.getBoundingClientRect();
  return { x: (ev.clientX - r.left) / scale, y: (ev.clientY - r.top) / scale };
}
function slotAt(p) {
  const { x, y, w, h } = E.GRID;
  if (p.x < x || p.y < y || p.x >= x + w || p.y >= y + h) return -1;
  return Math.floor((p.y - y) / E.CELL_H) * E.COLS + Math.floor((p.x - x) / E.CELL_W);
}
cv.addEventListener('pointerdown', (ev) => {
  const p = toLogical(ev);
  cv.setPointerCapture?.(ev.pointerId);
  const playing = scene === 'play' && !modal && G && !G.reward && !G.over;
  down = { ...p, x0: p.x, y0: p.y, slot: playing ? slotAt(p) : -1, scroll0: modal?.scroll ?? 0, moved: false };
});
cv.addEventListener('pointermove', (ev) => {
  if (!down) return;
  const p = toLogical(ev);
  if (Math.hypot(p.x - down.x0, p.y - down.y0) > 10) down.moved = true;
  down.x = p.x; down.y = p.y;
  if (modal?.scrollable && down.moved) modal.scroll = Math.max(0, Math.min(modal.maxScroll ?? 0, down.scroll0 - (p.y - down.y0)));
});
cv.addEventListener('pointerup', (ev) => {
  if (!down) return;
  const p = toLogical(ev), d = down;
  down = null;
  // 갈매미 끌어 옮기기
  if (d.moved && d.slot >= 0 && G.units[d.slot]) {
    const to = slotAt(p);
    if (to >= 0 && to !== d.slot) { E.move(G, d.slot, to); sel = to; }
    return;
  }
  if (d.moved && modal?.scrollable) return;
  for (let k = hits.length - 1; k >= 0; k--) {
    const h = hits[k];
    if (p.x >= h.x && p.y >= h.y && p.x < h.x + h.w && p.y < h.y + h.h) { h.fn(p); return; }
  }
});
cv.addEventListener('pointercancel', () => { down = null; });
cv.addEventListener('wheel', (ev) => {
  if (modal?.scrollable) { modal.scroll = Math.max(0, Math.min(modal.maxScroll ?? 0, modal.scroll + ev.deltaY)); ev.preventDefault(); }
}, { passive: false });
window.addEventListener('keydown', (ev) => {
  if (scene !== 'play') return;
  if (ev.key === ' ' || ev.key === 'Escape') { modal = modal ? null : { kind: 'pause' }; ev.preventDefault(); }
  if ((ev.key === 's' || ev.key === 'S') && !modal) doSummon();
});
document.addEventListener('visibilitychange', () => { if (document.hidden && scene === 'play' && G && !G.over && !modal) modal = { kind: 'pause' }; });

function doSummon() {
  const r = E.summon(G);
  if (!r.ok && r.why) say(r.why);
}
function tapSlot(i) {
  if (G.units[i]) { sel = sel === i ? -1 : i; return; }
  if (sel >= 0) { E.move(G, sel, i); sel = i; return; }
  sel = -1;
}

// ── 그리기: 판 ──
function drawBackground() {
  ctx.fillStyle = C.bg; ctx.fillRect(0, 0, W, H);
  const bg = img(`${ART}/ui/bg-harbor.webp`);
  if (bg) { const s = Math.max(W / bg.naturalWidth, H / bg.naturalHeight); ctx.globalAlpha = 0.5; ctx.drawImage(bg, (W - bg.naturalWidth * s) / 2, (H - bg.naturalHeight * s) / 2, bg.naturalWidth * s, bg.naturalHeight * s); ctx.globalAlpha = 1; }
  ctx.fillStyle = 'rgba(7,19,28,.55)'; ctx.fillRect(0, 0, W, H);
}
function drawField() {
  const L = E.LOOP, lane = 42;
  box(12, 98, W - 24, 566, 'rgba(7,19,28,.55)', C.edge, 12, 1.5);
  // 길
  ctx.lineWidth = lane; ctx.strokeStyle = C.lane; ctx.lineJoin = 'round';
  ctx.strokeRect(L.x0, L.y0, L.x1 - L.x0, L.y1 - L.y0);
  ctx.lineWidth = 1.5; ctx.strokeStyle = C.laneEdge;
  ctx.strokeRect(L.x0 - lane / 2, L.y0 - lane / 2, L.x1 - L.x0 + lane, L.y1 - L.y0 + lane);
  ctx.strokeRect(L.x0 + lane / 2, L.y0 + lane / 2, L.x1 - L.x0 - lane, L.y1 - L.y0 - lane);
  // 출발 표시
  text('▶', L.x0 + 2, L.y0, 16, C.goldEdge, 'center');
  // 칸
  const goalOf = goalIngredientColors();
  for (let i = 0; i < E.SLOTS; i++) {
    const c = E.slotCenter(i), x = c.x - E.CELL_W / 2 + 3, y = c.y - E.CELL_H / 2 + 3, w = E.CELL_W - 6, h = E.CELL_H - 6;
    const u = G.units[i];
    box(x, y, w, h, u ? 'rgba(11,30,42,.88)' : 'rgba(11,30,42,.45)', u ? GRADE_COLOR[E.UNIT[u.id].grade] : 'rgba(145,122,76,.35)', 8, u ? 2 : 1);
    if (u) {
      const dragging = down?.moved && down.slot === i;
      drawFit(unitImg(u.id), x + 4, y + 2, w - 8, h - 8, dragging ? 0.35 : 1);
      const cols = goalOf[u.id];
      if (cols) cols.forEach((col, k) => box(x - 2 - k * 3, y - 2 - k * 3, w + 4 + k * 6, h + 4 + k * 6, null, col, 10, 2.5));
      const fresh = G.t - u.born < 0.4;
      if (fresh) box(x, y, w, h, 'rgba(243,216,158,.35)', null, 8);
    }
    if (i === sel) box(x - 1, y - 1, w + 2, h + 2, null, '#ffffff', 9, 3);
    hit(x, y, w, h, () => tapSlot(i));
  }
  // 고른 갈매미 사거리
  if (sel >= 0 && G.units[sel]) {
    const st = E.unitStats(G.units[sel].id), c = E.slotCenter(sel);
    ctx.beginPath(); ctx.arc(c.x, c.y, st.range, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(243,216,158,.08)'; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(243,216,158,.6)'; ctx.stroke();
  }
  // 끌고 있는 갈매미
  if (down?.moved && down.slot >= 0 && G.units[down.slot]) drawFit(unitImg(G.units[down.slot].id), down.x - 34, down.y - 40, 68, 68, 0.9);
  // 적
  for (const e of G.enemies) {
    const sz = e.boss ? 76 : 40, sh = shade(e.look);
    if (sh) drawFit(sh, e.x - sz / 2, e.y - sz * 0.75, sz, sz);
    else { ctx.beginPath(); ctx.arc(e.x, e.y, sz / 3, 0, Math.PI * 2); ctx.fillStyle = '#2a1442'; ctx.fill(); }
    if (G.t < e.slowUntil) { ctx.beginPath(); ctx.arc(e.x, e.y + 4, sz / 3, 0, Math.PI * 2); ctx.strokeStyle = '#9fe8ff'; ctx.lineWidth = 1.5; ctx.stroke(); }
    const bw = e.boss ? 70 : 34, bx = e.x - bw / 2, by = e.y - sz * 0.75 - 7;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, e.hp / e.max), 5, e.boss ? '#ff6b6b' : '#6fd08a', null, 2);
  }
  // 공격 효과
  for (const f of G.fx) {
    const a = 1 - (G.t - f.t) / 0.6;
    if (f.kind === 'shot' && G.t - f.t < 0.15) {
      const c = E.slotCenter(f.slot);
      ctx.globalAlpha = 1 - (G.t - f.t) / 0.15; ctx.strokeStyle = ELEM_COLOR[f.elem]; ctx.lineWidth = f.crit ? 3 : 2;
      ctx.beginPath(); ctx.moveTo(c.x, c.y - 10); ctx.lineTo(f.x1, f.y1 - 8); ctx.stroke();
      ctx.beginPath(); ctx.arc(f.x1, f.y1 - 8, f.crit ? 6 : 4, 0, Math.PI * 2); ctx.fillStyle = ELEM_COLOR[f.elem]; ctx.fill();
      ctx.globalAlpha = 1;
    } else if (f.kind === 'zap' && G.t - f.t < 0.2) {
      ctx.strokeStyle = ELEM_COLOR.LIGHTNING; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 8); ctx.lineTo(f.x1, f.y1 - 8); ctx.stroke();
    } else if (f.kind === 'combine') {
      const c = E.slotCenter(f.slot);
      ctx.globalAlpha = Math.max(0, a); ctx.beginPath(); ctx.arc(c.x, c.y, 20 + (1 - a) * 40, 0, Math.PI * 2); ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 3; ctx.stroke(); ctx.globalAlpha = 1;
    }
  }
}
// 목표별 재료 갈매미 → 테두리 색 목록
function goalIngredientColors() {
  const out = {};
  G.goals.forEach((gid, k) => {
    const r = E.recipeFor(gid);
    if (!r) return;
    for (const id of new Set(r.in)) (out[id] ??= []).push(GOAL_COLOR[k]);
  });
  return out;
}

// ── 위 정보 줄 ──
function drawHud() {
  box(8, 6, W - 16, 86, C.header, C.edge, 10);
  const wi = E.waveInfo(Math.max(1, G.wave)), left = Math.ceil(E.nextWaveIn(G));
  text(`웨이브 ${G.wave}/${E.RULES.waves}`, 22, 30, 21, C.text);
  if (wi.boss) text(`보스 ${left}초 안에 잡기`, 22, 66, 16, left <= 15 ? C.bad : '#ffc2a8');
  else text(`다음 웨이브 ${left}초`, 22, 66, 16, C.muted);
  // 적 수
  const n = G.enemies.length, lim = E.RULES.limit, ratio = Math.min(1, n / lim);
  const bx = 200, bw = 150;
  text(`적 ${n}/${lim}`, bx, 30, 17, ratio > 0.7 ? C.bad : C.text);
  box(bx, 52, bw, 12, 'rgba(0,0,0,.5)', C.line, 6, 1);
  box(bx, 52, bw * ratio, 12, ratio > 0.7 ? '#e0533f' : ratio > 0.45 ? '#e8b04a' : '#6fd08a', null, 6);
  text(`처치 ${G.kills}`, bx, 78, 14, C.muted);
  // 일시정지·배속
  button(W - 116, 14, 48, 40, paused || modal?.kind === 'pause' ? '▶' : 'Ⅱ', () => { modal = { kind: 'pause' }; }, 'normal');
  button(W - 62, 14, 48, 40, `×${speed}`, () => { speed = speed === 1 ? 2 : 1; }, 'normal');
  // 새우
  const sh = img(`${ART}/menu/currency-shrimp.webp`);
  if (sh) drawFit(sh, W - 118, 58, 24, 24);
  text(`${G.gold}`, W - 90, 71, 18, C.goldEdge);
}

// ── 아래 판: 고른 갈매미 / 기본 ──
function drawBottom() {
  const y0 = 672;
  box(8, y0, W - 16, H - y0 - 8, C.panel, C.edge, 12);
  if (sel >= 0 && G.units[sel]) drawUnitPanel(y0);
  else drawMainPanel(y0);
}
function drawMainPanel(y0) {
  // 조합 목표
  text('조합 목표', 24, y0 + 22, 15, C.gold);
  text(G.goals.length ? '같은 색 테두리 = 그 목표의 재료' : '조합법에서 ☆로 목표를 정해 보세요', 104, y0 + 22, 14, C.muted);
  const cnt = E.countOnBoard(G), gw = (W - 48 - 16) / 3;
  for (let k = 0; k < E.GOAL_MAX; k++) {
    const x = 24 + k * (gw + 8), y = y0 + 38, h = 64, gid = G.goals[k];
    if (!gid) { box(x, y, gw, h, 'rgba(11,30,42,.4)', 'rgba(145,122,76,.35)', 8, 1); text('비어 있음', x + gw / 2, y + h / 2, 14, '#6f7f88', 'center'); hit(x, y, gw, h, () => openBook()); continue; }
    const r = E.recipeFor(gid), have = needHave(r, cnt), ready = !!E.recipeSlots(G, r);
    box(x, y, gw, h, 'rgba(11,30,42,.85)', GOAL_COLOR[k], 8, 2.5);
    drawFit(unitImg(gid), x + 4, y + 4, 48, 56);
    fitText(E.UNIT[gid].name, x + 56, y + 20, gw - 60, 17, C.text);
    text(ready ? '조합 가능!' : `재료 ${have}/${r.in.length}`, x + 56, y + 44, 15, ready ? C.good : C.muted);
    hit(x, y, gw, h, () => { if (ready) { E.combine(G, r); say(`${E.UNIT[gid].name} 완성!`); } else openBook(gid); });
  }
  // 버튼
  const by = y0 + 118, cost = E.summonCost(G), ucost = E.upgradeCost(G);
  const free = E.freeSlots(G).length;
  button(24, by, 236, 74, '소환', doSummon, 'primary', G.gold >= cost && free > 0, free ? `새우 ${cost} · 커먼 1마리` : '빈칸 없음');
  button(270, by, 120, 74, '공격력 강화', () => { const r = E.upgrade(G); if (!r.ok && r.why) say(r.why); else if (r.ok) say(`강화 ${G.upg}단계: 모든 갈매미 공격력 +${Math.round(G.upg * E.RULES.upgPct * 100)}%`); }, 'normal', G.gold >= ucost, `${G.upg}단계 · 새우 ${ucost}`);
  button(400, by, 116, 74, '조합법', () => openBook(), 'normal', true, `${E.RECIPES.length}개`);
  wrap('칸을 누르면 정보·조합, 끌면 자리 바꾸기. 적이 70마리를 넘거나 보스를 60초 안에 못 잡으면 끝나요.', 24, by + 88, W - 48, 15, C.muted);
}
function needHave(r, cnt) {
  const left = { ...cnt };
  let have = 0;
  for (const id of r.in) if ((left[id] ?? 0) > 0) { left[id]--; have++; }
  return have;
}
function drawUnitPanel(y0) {
  const u = G.units[sel], info = E.UNIT[u.id], st = E.unitStats(u.id);
  drawFit(unitImg(u.id), 22, y0 + 10, 72, 72);
  fitText(info.name, 104, y0 + 24, 170, 22, C.text);
  const gx = 104 + Math.min(170, (ctx.measureText(info.name).width || 0)) + 8;
  gradeChip(info.grade, Math.min(gx, W - 200), y0 + 14);
  const dmg = Math.round(st.dmg * (1 + E.RULES.upgPct * G.upg));
  fitText(`${info.role} · 공격 ${dmg} · ${st.gap < 0.6 ? '빠름' : st.gap > 1 ? '느림' : '보통'} · 사거리 ${st.range}`, 104, y0 + 54, W - 128, 15, C.muted);
  fitText(E.ELEM_TIP[st.elem] || (st.stun ? '가끔 적을 잠깐 멈춤' : st.crit ? '가끔 두 배 피해' : '기본 공격'), 104, y0 + 78, W - 128, 15, ELEM_COLOR[st.elem]);
  button(W - 112, y0 + 12, 88, 40, '닫기', () => { sel = -1; }, 'quiet');
  // 이 갈매미가 들어가는 조합법(만들 수 있는 것 먼저)
  const list = E.recipesUsing(u.id).map((r) => ({ r, ok: !!E.recipeSlots(G, r, sel) })).sort((a, b) => (b.ok - a.ok) || (E.tierOf(a.r.out) - E.tierOf(b.r.out)));
  text(list.length ? '이 갈매미로 만들 수 있는 것' : '이 갈매미가 들어가는 공개 조합법이 없어요', 24, y0 + 102, 15, C.gold);
  const cnt = E.countOnBoard(G);
  list.slice(0, 2).forEach(({ r, ok }, k) => recipeRow(r, ok, 20, y0 + 114 + k * 52, W - 40, 48, cnt, sel));
  const by = H - 8 - 58;
  if (list.length > 2) button(24, by, 190, 46, `조합법 더 보기 (+${list.length - 2})`, () => openBook(null, u.id), 'normal');
  button(W - 24 - 160, by, 160, 46, `판매 +${st.sell}`, () => { E.sell(G, sel); sel = -1; }, 'danger');
}
// 조합법 한 줄: 결과 그림·이름, 재료(있으면 밝게), ☆목표, 조합
function recipeRow(r, ok, x, y, w, h, cnt, keep = -1) {
  const out = E.UNIT[r.out], gi = G.goals.indexOf(r.out);
  box(x, y, w, h, ok ? 'rgba(43,122,98,.25)' : 'rgba(11,30,42,.7)', gi >= 0 ? GOAL_COLOR[gi] : ok ? '#2b7a62' : 'rgba(145,122,76,.4)', 8, gi >= 0 ? 2.5 : 1.5);
  drawFit(unitImg(r.out), x + 4, y + 2, 40, h - 4);
  fitText(out.name, x + 50, y + 15, 96, 17, GRADE_COLOR[out.grade]);
  text(E.GRADE_NAME[out.grade], x + 50, y + 34, 14, C.muted);
  // 재료
  const left = { ...cnt };
  let cx = x + 150;
  ctx.font = `15px ${FONT}`;
  const chipMax = (x + w - 112 - cx) / r.in.length - 4;
  for (const id of r.in) {
    const has = (left[id] ?? 0) > 0;
    if (has) left[id]--;
    ctx.font = `15px ${FONT}`;
    const t = E.UNIT[id].name, tw = Math.min(chipMax, ctx.measureText(t).width + 12);
    box(cx, y + h / 2 - 13, tw, 26, has ? 'rgba(111,208,138,.18)' : 'rgba(0,0,0,.3)', has ? '#6fd08a' : '#46555f', 6, 1);
    fitText(t, cx + tw / 2, y + h / 2, tw - 6, 15, has ? C.good : '#8b9aa3', 'center');
    cx += tw + 4;
  }
  // 버튼
  const bx = x + w - 108;
  button(bx, y + 6, 40, h - 12, gi >= 0 ? '★' : '☆', () => { const on = E.toggleGoal(G, r.out); say(on ? `목표: ${out.name}` : '목표에서 뺐어요'); }, 'quiet');
  button(bx + 46, y + 6, 58, h - 12, '조합', () => { const at = E.combine(G, r, keep); if (at != null) { sel = at; say(`${out.name} 완성!`); } }, 'primary', ok);
}

// ── 모달 ──
function openBook(focus = null, using = null) { modal = { kind: 'book', scroll: 0, scrollable: true, focus, using }; }
function shadeAll(alpha = 0.7) { ctx.fillStyle = `rgba(3,9,14,${alpha})`; ctx.fillRect(0, 0, W, H); hit(0, 0, W, H, () => {}); }
function drawBook() {
  shadeAll();
  const x = 14, y = 40, w = W - 28, h = H - 80;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text(modal.using ? `${E.UNIT[modal.using].name}이(가) 들어가는 조합법` : '조합법 (공개분)', x + 18, y + 30, 20, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText('☆ 목표 = 판의 재료에 같은 색 테두리 · 히든은 보스 보상', x + 18, y + 64, w - 36, 14, C.muted);
  const top = y + 82, viewH = h - 92, cnt = E.countOnBoard(G);
  let rows = E.RECIPES.filter((r) => !modal.using || r.in.includes(modal.using));
  rows = rows.map((r) => ({ r, ok: !!E.recipeSlots(G, r) })).sort((a, b) => (E.tierOf(a.r.out) - E.tierOf(b.r.out)) || (b.ok - a.ok));
  // 등급 머리글 + 줄
  const items = [];
  let lastG = '';
  for (const it of rows) { const g = E.UNIT[it.r.out].grade; if (g !== lastG) { items.push({ head: g }); lastG = g; } items.push(it); }
  const rowH = (it) => (it.head ? 30 : 52);
  const total = items.reduce((a, it) => a + rowH(it), 0);
  modal.maxScroll = Math.max(0, total - viewH);
  if (modal.focus && !modal.focused) {   // 목표 칸에서 열면 그 조합법으로
    let yy = 0; for (const it of items) { if (it.r?.out === modal.focus) break; yy += rowH(it); }
    modal.scroll = Math.min(modal.maxScroll, Math.max(0, yy - 60)); modal.focused = true;
  }
  ctx.save(); rr(x + 6, top, w - 12, viewH, 8); ctx.clip();
  let yy = top - modal.scroll;
  const hitsBefore = hits.length;
  for (const it of items) {
    const rh = rowH(it);
    if (yy + rh >= top && yy <= top + viewH) {
      if (it.head) { text(E.GRADE_NAME[it.head], x + 20, yy + 17, 15, GRADE_COLOR[it.head]); ctx.fillStyle = 'rgba(145,122,76,.35)'; ctx.fillRect(x + 80, yy + 17, w - 110, 1); }
      else recipeRow(it.r, it.ok, x + 12, yy + 3, w - 24, rh - 6, cnt);
    }
    yy += rh;
  }
  ctx.restore();
  // 보이는 곳 밖 버튼은 누를 수 없게
  hits = hits.filter((hh, k) => k < hitsBefore || (hh.y >= top && hh.y + hh.h <= top + viewH));
  // 스크롤 막대
  if (modal.maxScroll > 0) { const bh = Math.max(40, viewH * viewH / total), by2 = top + (viewH - bh) * (modal.scroll / modal.maxScroll); box(x + w - 8, by2, 4, bh, 'rgba(243,216,158,.5)', null, 2); }
}
function drawReward() {
  shadeAll(0.75);
  const x = 20, y = 170, w = W - 40, h = 560;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text(`${G.wave}웨이브 보스 처치!`, W / 2, y + 40, 24, C.goldEdge, 'center');
  text('히든 갈매미 하나를 골라 데려가요', W / 2, y + 74, 16, C.muted, 'center');
  const cw = (w - 48) / 3;
  G.reward.forEach((id, k) => {
    const cx = x + 16 + k * (cw + 8), cy = y + 100, ch = 360, st = E.unitStats(id), u = E.UNIT[id];
    box(cx, cy, cw, ch, 'rgba(11,30,42,.9)', GRADE_COLOR.HIDDEN, 10, 2);
    drawFit(unitImg(id), cx + 8, cy + 10, cw - 16, 150);
    fitText(u.name, cx + cw / 2, cy + 184, cw - 12, 20, C.text, 'center');
    fitText(u.role, cx + cw / 2, cy + 212, cw - 12, 15, C.muted, 'center');
    fitText(`공격 ${st.dmg}`, cx + cw / 2, cy + 236, cw - 12, 14, C.text, 'center');
    ctx.save(); ctx.beginPath(); ctx.rect(cx + 6, cy + 252, cw - 12, 60); ctx.clip();
    wrap(E.ELEM_TIP[st.elem] || '기본 공격', cx + 10, cy + 252, cw - 20, 14, ELEM_COLOR[st.elem]);
    ctx.restore();
    button(cx + 8, cy + ch - 50, cw - 16, 42, '데려가기', () => { E.chooseReward(G, k); say(`${u.name} 합류!`); }, 'primary');
  });
  text('고르면 다음 웨이브가 시작돼요', W / 2, y + h - 30, 15, C.muted, 'center');
}
function drawPause() {
  shadeAll();
  const x = 60, y = 300, w = W - 120, h = 320;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('잠시 멈춤', W / 2, y + 44, 24, C.text, 'center');
  text(`웨이브 ${G.wave} · 처치 ${G.kills} · 강화 ${G.upg}단계`, W / 2, y + 84, 15, C.muted, 'center');
  button(x + 30, y + 118, w - 60, 56, '계속하기', () => { modal = null; }, 'primary');
  button(x + 30, y + 186, w - 60, 48, '처음부터 다시', () => start(), 'normal');
  button(x + 30, y + 244, w - 60, 48, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
}
function drawOver() {
  shadeAll(0.75);
  const x = 40, y = 250, w = W - 80, h = 420, win = G.over === 'win';
  box(x, y, w, h, C.panel, win ? C.goldEdge : '#b4482a', 14, 2);
  text(win ? '방어 성공!' : '방어 실패', W / 2, y + 48, 28, win ? C.goldEdge : C.bad, 'center');
  ctx.save(); ctx.beginPath(); ctx.rect(x + 20, y + 70, w - 40, 60); ctx.clip();
  wrap(G.reason, x + 30, y + 78, w - 60, 15, C.text);
  ctx.restore();
  const reached = win ? `${E.RULES.waves}웨이브 클리어` : `${G.wave}웨이브`;
  text(reached, W / 2, y + 160, 22, C.text, 'center');
  text(`처치 ${G.kills} · 강화 ${G.upg}단계 · 소환 ${G.summons}번`, W / 2, y + 196, 15, C.muted, 'center');
  text(`최고 기록 ${best > E.RULES.waves ? '클리어' : `${best}웨이브`}`, W / 2, y + 226, 15, C.gold, 'center');
  button(x + 30, y + 266, w - 60, 58, '다시 하기', () => start(), 'primary');
  button(x + 30, y + 336, w - 60, 48, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
}

// ── 시작 화면 ──
function drawTitle() {
  drawBackground();
  const vil = img(`${ART}/village/identity-coast-panorama.webp`);
  if (vil) { const s = W / vil.naturalWidth * 1.6; ctx.globalAlpha = 0.9; ctx.drawImage(vil, (W - vil.naturalWidth * s) / 2, 120, vil.naturalWidth * s, vil.naturalHeight * s); ctx.globalAlpha = 1; }
  ctx.fillStyle = 'rgba(7,19,28,.35)'; ctx.fillRect(0, 0, W, H);
  text('갈매미맨', W / 2, 92, 30, C.gold, 'center');
  text('랜덤 디펜스', W / 2, 140, 46, C.goldEdge, 'center');
  ['u01', 'u38', 'u60', 'u33', 'u05'].forEach((id, k) => drawFit(unitImg(id), 40 + k * 94, 400, 90, 110));
  box(28, 540, W - 56, 236, C.panel, C.edge, 12);
  const lines = [
    '· 「소환」으로 커먼 갈매미를 무작위로 불러요.',
    '· 게임 속 조합법대로 합치면 더 높은 등급이 돼요.',
    '· 조합법에서 ☆로 목표를 정하면 재료에 색 테두리가 생겨요.',
    '· 10웨이브마다 보스! 잡으면 히든 갈매미를 골라 얻어요.',
    `· ${E.RULES.waves}웨이브까지 막으면 클리어.`,
  ];
  lines.forEach((l, k) => fitText(l, 46, 572 + k * 40, W - 92, 16, C.text));
  text(`최고 기록: ${best ? (best > E.RULES.waves ? '클리어' : `${best}웨이브`) : '아직 없음'}`, W / 2, 808, 16, C.gold, 'center');
  button(60, 836, W - 120, 72, '시작하기', () => start(), 'primary');
  fitText('로그인 없이 하는 미니 게임 · 기록은 이 기기에만 남아요', W / 2, 932, W - 40, 14, C.muted, 'center');
}

// ── 반복 ──
let last = performance.now(), acc = 0;
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (scene === 'play' && G && !modal && !G.over) {
    acc += dt * speed;
    while (acc >= 0.05) { E.step(G, 0.05); acc -= 0.05; }
    if (sel >= 0 && !G.units[sel]) sel = -1;
  }
  if (scene === 'play' && G?.over && modal?.kind !== 'over') { saveBest(); modal = { kind: 'over' }; }
  hits = [];
  ctx.setTransform(scale * dpr, 0, 0, scale * dpr, 0, 0);
  if (scene === 'title') drawTitle();
  else {
    drawBackground(); drawField(); drawHud(); drawBottom();
    if (G.reward && !modal) drawReward();
    if (modal?.kind === 'book') drawBook();
    else if (modal?.kind === 'pause') drawPause();
    else if (modal?.kind === 'over') drawOver();
  }
  if (performance.now() < toast.until && toast.msg) {
    ctx.font = `15px ${FONT}`;
    const tw = Math.min(W - 40, ctx.measureText(toast.msg).width + 32);
    box((W - tw) / 2, 600, tw, 40, 'rgba(19,44,59,.96)', C.goldEdge, 10, 1.5);
    fitText(toast.msg, W / 2, 620, tw - 20, 15, C.text, 'center');
  }
  requestAnimationFrame(frame);
}

// 글꼴을 받은 뒤 시작(캔버스는 글꼴이 늦게 오면 기본 글꼴로 그림)
(async () => {
  try { await Promise.race([document.fonts.load(`20px "Workbench"`, '갈매미'), new Promise((r) => setTimeout(r, 2500))]); } catch { /* 기본 글꼴로 진행 */ }
  document.getElementById('boot')?.remove();
  for (const u of E.UNITS) unitImg(u.id);   // 그림 미리 받기
  requestAnimationFrame(frame);
})();

// 시험용(자동 캡처에서 상태를 만들 때만 씀)
window.__defense = { E, get G() { return G; }, start, set sel(v) { sel = v; }, openBook, set modal(v) { modal = v; } };
