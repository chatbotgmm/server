// 갈매미맨 랜덤 디펜스 2판: 화면·입력(규칙은 engine.js). 논리 좌표 540×960을 화면 크기에 맞춰 그린다.
// 조작(사용자 요청 2026-11-08): 갈매미는 끌어서만 옮긴다 · 누르면 고르기 · 빈 곳을 끌면 여러 마리 고르기(고른 무리는 하나를 끌면 함께) · 다른 곳을 누르면 고르기 풀림.
import * as E from './engine.js?v=1bfebf470284';

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
const SFX_KEY = 'gmm-defense-sfx';
const FX_KEY = 'gmm-defense-fxlite';   // 공격 효과 줄이기(느린 휴대폰용)
// 「화려한 효과」 끔(2026-11-10 사용자 요청: 성능이 낮은 기기에서도): 공격 효과·꾸밈·빛 테두리·움직이는 장식을 줄이고,
// 캔버스 배율도 1.5까지만(프레임은 그대로 60 — 2026-11-10 사용자: 30프레임은 끊겨 보임)
let fxLite = false, fxChosen = false;
try { const v = localStorage.getItem(FX_KEY); fxLite = v === '1'; fxChosen = v !== null; } catch { /* 진행 */ }
function setFxLite(on) { fxLite = on; fxChosen = true; try { localStorage.setItem(FX_KEY, on ? '1' : '0'); } catch { /* 진행 */ } fit(); }
// 배경음악(사용자 제공 곡을 mp3로 바꿈). 주소의 ?v=는 scripts/web/stamp-defense.py가 붙인다
const BGM_URL = '/defense/bgm.mp3?v=f7211afdc4a1';
const UNIT_R = 24;   // 갈매미 누르기 반지름(커먼 기준, 등급 크기만큼 커짐)
// 등급별 크기(사용자 요청 2026-11-08: 상위일수록 크게 — 보상 느낌). 커먼·언커먼·레어·에픽·전설·히든·제한·초월·불멸·영원
const GRADE_SIZE = E.UNIT_SIZE;   // 초불영제는 같은 크기(겹침 간격 계산과 같은 표 — engine.js)
const usz = (id) => GRADE_SIZE[E.tierOf(id)] ?? 1;
const BASE_SPEED = 1.5;   // 기본 게임 속도(사용자 요청 2026-11-08: 더 빠르게). 시간이 통째로 빨라져 난이도는 같음

// ── 화면 준비 ──
const cv = document.getElementById('game');
const TOUCH = (() => { try { return matchMedia('(pointer: coarse)').matches; } catch { return false; } })();
let ctx = cv.getContext('2d');   // let: 잘 안 바뀌는 그림을 따로 그려 둘 때 잠깐 바꿔 씀(cachedLayer)
let scale = 1, dpr = 1;
// 화면 배치: 휴대폰·태블릿 세로는 540×960 한 줄, PC·태블릿 가로는 1100×960(왼쪽 게임, 오른쪽 창)
// 가로 배치(사용자 요청 2026-11-08: 전투 화면을 크게, 아래 메뉴는 오른쪽으로): 왼쪽에 위 정보 줄+판(논리 0~680)을 세로 가득 키우고(LS배),
// 오른쪽(SIDE_X부터 540폭)에 메뉴(아래)와 창을 둔다
const LS = 960 / 680;        // 왼쪽 판 확대 배율(약 1.41)
const SIDE_X = 772;          // 오른쪽 열이 시작하는 곳(540×LS ≈ 762 + 간격)
let landscape = false, VW = W, VH = H;
const PEER_H = 208;          // 세로 화면 같이하기: 아래에 붙는 친구 판 줄 높이
let stripOn = false;         // 친구 판 줄을 붙였는가(frame이 정함)
// 넓은 가로 화면(2026-11-11 검토: 긴 휴대폰에서 화면 가운데 61%만 쓰고 글자가 아주 작음): 오른쪽을 「창」 칸과 「메뉴」 칸 둘로 나누고 둘 다 WK배로 키움.
// 화면 비율이 약 2:1 이상일 때만(그보다 좁으면 예전처럼 한 칸). 같이하기 중에는 친구 판이 있어 예전 배치
let BOARD_X = 0;             // 판이 시작하는 가로 위치. 2026-11-12: 판 가운데(창 칸 | 판 | 메뉴 칸)를 해 봤으나 「메뉴와 조합창이 나뉘어 불편」 → 다시 판 왼쪽, 창·메뉴는 오른쪽에 나란히
let wide = false, WK = 1, AX = 772, AY = 0, BX = 772, BYT = 0, coopOn = false;
let fullTitle = true;        // 첫 화면·대기실은 가로 화면 폭 전체(밝은 배경 양옆에 검은 띠가 안 보이게, frame이 정함)
let PBOT = H;                // 아래 메뉴 판의 바닥(세로 화면에서 휴대폰이 길면 늘어남)
let OY = 0;                  // 지금 그리는 곳의 세로 위치(첫 화면을 가운데로 내릴 때)
let OX = 0;                  // 지금 그리는 곳의 가로 위치(오른쪽 창을 그릴 때 누르기 영역에 더함)
let OS = 1;                  // 지금 그리는 곳의 배율(가로 배치 왼쪽 판은 LS)
// 자동 화질(2026-11-10 사용자 의견: 렉): 게임 중 프레임이 계속 느리면(중앙값 22ms 넘음) 캔버스 배율을 3 → 2 → 1.5로 한 단계씩 낮춤(올리지는 않음)
let dprCap = 3;
const perf = { t: [], at: 0 };
// 성능 표시(2026-11-10 렉 원인 찾기용): 설정에서 켜면 화면 왼쪽 아래에 초당 프레임·그리기·계산 시간(최근 평균)
const PERF_KEY = 'gmm-defense-perf';
const perfShow = { on: false, fps: 0, draw: 0, sim: 0 };
try { perfShow.on = localStorage.getItem(PERF_KEY) === '1'; } catch { /* 진행 */ }
const ema = (a, v) => (a ? a * 0.92 + v * 0.08 : v);
function perfTick(ms) {
  if (!(scene === 'play' && running()) || ms > 250) return;   // 멈춤·창 전환 뒤 첫 프레임은 빼고
  perf.t.push(ms);
  if (perf.t.length < 120) return;
  const med = [...perf.t].sort((a, b) => a - b)[60]; perf.t = [];
  const cur = Math.min(window.devicePixelRatio || 1, dprCap);
  if (med > 22 && cur > 1.5) { dprCap = cur > 2 ? 2 : 1.5; fit(); }
  else if (med > 24 && !fxLite && !fxChosen) { fxLite = true; fxChosen = true; try { localStorage.setItem(FX_KEY, '1'); } catch { /* 진행 */ } fit(); say('화면이 느려서 화려한 효과를 껐어요. ♪ 설정에서 다시 켤 수 있어요.'); }
}
function fit() {
  dpr = Math.min(window.devicePixelRatio || 1, dprCap, fxLite ? 1.5 : 3);
  landscape = window.innerWidth / window.innerHeight > 0.9;
  const wk = Math.min((Math.min(H * window.innerWidth / window.innerHeight, 2400) - SIDE_X - 16) / (2 * W), H / 664);
  wide = false && landscape && !coopOn && wk >= 1.05; WK = wide ? wk : 1;   // 2026-11-12 사용자 의견: 3분할(판 | 창 | 메뉴)보다 예전 2분할(판 | 창·메뉴 한 칸)이 더 좋음 → 넓은 화면 배치 끔(코드는 남김)
  VW = wide ? Math.round(SIDE_X + 2 * W * WK + 16) : landscape ? SIDE_X + W : W;
  if (landscape && fullTitle) VW = Math.max(VW, Math.min(2400, Math.round(H * window.innerWidth / window.innerHeight)));
  // 세로 화면(사용자 요청 2026-11-08: 휴대폰 위아래 빈 곳 없이): 화면이 더 길면 그만큼 세로를 늘려(최대 +300) 아래 메뉴를 크게
  const base = stripOn && !landscape ? H + PEER_H : H;
  VH = landscape ? H : Math.round(Math.max(base, Math.min(VW * window.innerHeight / window.innerWidth, base + 300)));
  PBOT = wide ? Math.min(672 + 540, Math.floor(672 + H / WK - 8)) : landscape ? H : VH - (stripOn ? PEER_H : 0);
  BOARD_X = 0;
  if (wide) { AX = SIDE_X; AY = Math.round((H - 664 * WK) / 2); BX = Math.round(SIDE_X + W * WK + 8); BYT = Math.round((H - (PBOT - 672) * WK) / 2 - 672 * WK); }   // 두 칸 모두 세로 가운데
  // 화질(사용자 지적 2026-11-08): 화면 크기(정수 px)를 먼저 정하고 캔버스 픽셀은 그 × 기기 배율로 맞춘다.
  // 전에는 둘을 따로 내림해 일부 기기에서 화면 전체가 한 번 더 늘어나 흐려질 수 있었음
  const cssW = Math.floor(VW * Math.min(window.innerWidth / VW, window.innerHeight / VH));
  const cssH = Math.floor(VH * Math.min(window.innerWidth / VW, window.innerHeight / VH));
  cv.style.width = `${cssW}px`;
  cv.style.height = `${cssH}px`;
  cv.width = Math.round(cssW * dpr);
  cv.height = Math.round(cssH * dpr);
  scale = cssW / VW;
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
// 무료 효과 그림(2026-11-21 사용자 허락: 무료 에셋 다듬어 쓰기): Kenney Particle Pack(CC0) → 흰색+투명도로 줄인 것(assets-src/defense-fx/LICENSE.txt).
// 색을 입힌 사본을 한 번만 만들어 두고(이름·색마다) 'lighter'로 겹쳐 빛나게 그림
let FXART = null; import('./fxart.js?v=8134389ddc0c').then((m) => { FXART = m.FX; }).catch(() => { /* 그림 없이 진행 */ });
const fxTintC = new Map();
const hexRGB = (h) => { const m = /^#?([0-9a-f]{6})$/i.exec(h ?? ''); if (!m) return '255,255,255'; const n = parseInt(m[1], 16); return `${n >> 16},${(n >> 8) & 255},${n & 255}`; };
function fxImg(name, rgb) {
  const key = name + '|' + rgb; let c = fxTintC.get(key); if (c) return c;
  const src = FXART?.[name]; if (!src) return null; const im = img(src); if (!im) return null;
  c = document.createElement('canvas'); c.width = im.naturalWidth; c.height = im.naturalHeight; const x = c.getContext('2d');
  x.drawImage(im, 0, 0); x.globalCompositeOperation = 'source-in'; x.fillStyle = `rgb(${rgb})`; x.fillRect(0, 0, c.width, c.height);
  fxTintC.set(key, c); return c;
}
function fxSprite(name, x, y, size, rgb, alpha = 1, rot = 0, sy = 1, add = true) {
  const c = fxImg(name, rgb); if (!c || alpha <= 0) return;
  ctx.save(); ctx.globalAlpha = Math.min(1, alpha); if (add) ctx.globalCompositeOperation = 'lighter';
  ctx.translate(x, y); ctx.scale(1, sy); if (rot) ctx.rotate(rot); ctx.drawImage(c, -size / 2, -size / 2, size, size); ctx.restore();
}
const unitImg = (id) => img(`${ART}/units/${E.UNIT[id]?.form ?? id}-front.webp`);   // 변신 폼은 원래 그림
// 적 그림(사용자 요청 2026-11-08: 끼룩단·쭈매미·흑화 갈매미맨, 색 입히지 않음). e:v1 → enemies/v1, b:u01 → black/u01, 예전 저장의 갈매미 id는 그림자 색
const LOOK_RE = /^(e|b):([a-z0-9]{1,6})$/;
function enemyImg(look) {
  const m = LOOK_RE.exec(String(look ?? ''));
  if (m) return img(`${ART}/${m[1] === 'e' ? 'enemies' : 'black'}/${m[2]}.webp`);
  return E.UNIT[look] ? shade(look) : null;
}
const shades = {};
function shade(id) {
  if (shades[id]) return shades[id];
  const i = unitImg(id);
  if (!i) return null;
  const c = document.createElement('canvas');
  c.width = 256; c.height = Math.round(256 * i.naturalHeight / i.naturalWidth);   // 보스·대장도 고해상도 화면에서 흐리지 않게(전에는 96px)
  const x = c.getContext('2d');
  x.imageSmoothingQuality = 'high';
  x.drawImage(i, 0, 0, c.width, c.height);
  x.globalCompositeOperation = 'source-atop';
  x.fillStyle = 'rgba(42,20,66,.92)';
  x.fillRect(0, 0, c.width, c.height);
  shades[id] = c;
  return c;
}
// 줄인 그림 저장(2026-11-10 렉 줄이기): 380px쯤 되는 원본을 매 프레임 50~80px로 고품질 축소하던 것을, 실제 화면 픽셀 크기로 한 번 줄여 두고 다시 씀.
// 크기는 8px 단위로 묶어 저장(조금 커지거나 작아지는 움직임에도 새로 만들지 않게), 너무 많아지면 오래된 것부터 버림
const sprites = new Map();
function sprite(im, dw, dh) {
  const tr = ctx.getTransform(), sc = Math.hypot(tr.a, tr.b);
  const pw = Math.ceil(dw * sc / 8) * 8, ph = Math.max(1, Math.round(pw * dh / dw));
  const iw = im.naturalWidth || im.width;
  if (!iw || pw >= iw * 0.8) return im;   // 거의 원본 크기면 그대로
  const key = `${im.src ?? ''}|${pw}`;
  let c = sprites.get(key);
  if (!c) {
    c = document.createElement('canvas'); c.width = pw; c.height = ph;
    const x = c.getContext('2d'); x.imageSmoothingEnabled = true; x.imageSmoothingQuality = 'high'; x.drawImage(im, 0, 0, pw, ph);
    sprites.set(key, c);
    if (sprites.size > 400) sprites.delete(sprites.keys().next().value);
  }
  return c;
}
function drawFit(im, x, y, w, h, alpha = 1) {
  if (!im) return;
  const iw = im.naturalWidth || im.width, ih = im.naturalHeight || im.height;
  if (!iw) return;
  const s = Math.min(w / iw, h / ih), dw = iw * s, dh = ih * s;
  ctx.globalAlpha = alpha;
  ctx.drawImage(im.src ? sprite(im, dw, dh) : im, x + (w - dw) / 2, y + h - dh, dw, dh);
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
const hit = (x, y, w, h, fn, drag = false) => { const ox = OX, os = OS, oy = OY; hits.push({ x: x * os + ox, y: y * os + oy, w: w * os, h: h * os, fn: (p) => fn({ x: (p.x - ox) / os, y: (p.y - oy) / os }), drag }); };
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
  if (sub && h < 48) {   // 낮은 버튼: 이름과 부가 글을 한 줄에(글자가 칸 밖으로 넘치지 않게)
    fitText(label, x + w * 0.3, y + h / 2, w * 0.5, 16, col, 'center');
    fitText(sub, x + w * 0.72, y + h / 2, w * 0.48, 13, enabled ? (subColor ?? (style === 'primary' ? '#3d2f1a' : C.muted)) : '#5b6a73', 'center');
  } else if (sub) { fitText(label, x + w / 2, y + h / 2 - 10, w - 12, 17, col, 'center'); fitText(sub, x + w / 2, y + h / 2 + 12, w - 12, 14, enabled ? (subColor ?? (style === 'primary' ? '#3d2f1a' : C.muted)) : '#5b6a73', 'center'); }
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
const recKey = (mode = 'normal', diff = 'master') => `${mode || 'normal'}${diff && diff !== 'master' ? `.${diff}` : ''}`;   // 'normal' · 'hard' · 'normal.intro' …
const bestKey = (mode, diff) => { const rk = recKey(mode, diff); return rk === 'normal' ? BEST_KEY : `${BEST_KEY}-${rk}`; };
const bestOf = (mode, diff) => Number(ls.get(bestKey(mode, diff))) || 0;
const DIFF_KEY = 'gmm-defense-diff';
let selDiff = 'master';   // 시작 화면에서 고른 난이도(2026-11-09 사용자 요청, 지금까지의 규칙 = 달인)
try { const d = localStorage.getItem(DIFF_KEY); if (d && E.DIFFS[d]) selDiff = d; } catch { /* 진행 */ }
const diffName = (d) => E.DIFFS[d]?.name ?? '달인';
const bestText = (b) => (b ? (b > E.RULES.waves ? '클리어' : `${b}웨이브`) : '아직 없음');
let best = bestOf('normal', selDiff);
let resumeMeta = null;   // 이어할 판 {wave, mode}
try { resumeMeta = JSON.parse(ls.get(SAVE_META_KEY) ?? 'null'); } catch { resumeMeta = null; }
// ── 배경음악: 누르기(시작하기·♪) 뒤에만 재생(브라우저 자동 재생 막힘), 일시정지·다른 앱이면 멈춤 ──
const music = { on: false, vol: 0.45, el: null };   // 2026-11-11 유저 피드백(시작하자마자 음악이 나옴): 기본 꺼짐, 한 번 켜면 그 설정을 기억
try { music.on = localStorage.getItem(MUSIC_KEY) === 'on'; const v = Number(localStorage.getItem(VOLUME_KEY)); if (localStorage.getItem(VOLUME_KEY) !== null && v >= 0 && v <= 1) music.vol = v; } catch { /* 기본값 */ }
function musicEl() {
  if (!music.el) { music.el = new Audio(BGM_URL); music.el.loop = true; music.el.volume = music.vol; music.el.preload = 'auto'; }
  return music.el;
}
// ── 효과음(사용자 요청 2026-11-08): 소리 파일 없이 브라우저에서 짧은 음을 만들어 냄. 첫 누르기 뒤에만(자동 재생 막힘) ──
const sfxState = { vol: 0.5, ctx: null, last: {} };
try { const v = Number(localStorage.getItem(SFX_KEY)); if (localStorage.getItem(SFX_KEY) !== null && v >= 0 && v <= 1) sfxState.vol = v; } catch { /* 기본값 */ }
function sfxCtx() {
  if (!sfxState.ctx) { const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return null; sfxState.ctx = new AC(); }
  if (sfxState.ctx.state === 'suspended') sfxState.ctx.resume().catch(() => {});
  return sfxState.ctx;
}
// 모든 효과음의 공용 출력(2026-11-15 사용자: 공격 소리 중 스피커가 터지는 듯한 소리) — 여러 소리가 겹쳐 1을 넘으면 찢어지므로
// 압축기(목소리가 커지면 눌러 줌)를 거쳐 내보냄. 시험용으로 다른 AudioContext(오프라인)에도 따로 만듦
const sfxBus = new WeakMap();
function sfxOut(ac) {
  let b = sfxBus.get(ac);
  if (!b) { const c = ac.createDynamicsCompressor(); c.threshold.value = -20; c.knee.value = 12; c.ratio.value = 10; c.attack.value = 0.002; c.release.value = 0.12;
    const g = ac.createGain(); g.gain.value = 0.9; c.connect(g).connect(ac.destination); b = c; sfxBus.set(ac, b); }
  return b;
}
// [주파수, 시작(초), 길이(초), 파형] 묶음
const SFX = {
  summon: [[660, 0, 0.08, 'triangle'], [990, 0.06, 0.1, 'triangle']],
  combine: [[523, 0, 0.12, 'triangle'], [784, 0.09, 0.14, 'triangle'], [1047, 0.18, 0.2, 'sine']],
  boss: [[110, 0, 0.5, 'sawtooth'], [82, 0.25, 0.6, 'sawtooth']],
  warn: [[880, 0, 0.09, 'square']],
  recruitOk: [[587, 0, 0.1, 'triangle'], [880, 0.08, 0.12, 'triangle'], [1175, 0.17, 0.22, 'sine']],
  recruitFail: [[300, 0, 0.15, 'sine'], [220, 0.12, 0.25, 'sine']],
  story: [[523, 0, 0.12, 'triangle'], [659, 0.1, 0.12, 'triangle'], [784, 0.2, 0.12, 'triangle'], [1047, 0.3, 0.3, 'sine']],
  signal: [[1320, 0, 0.08, 'sine'], [1760, 0.09, 0.12, 'sine']],
  wisp: [[1568, 0, 0.1, 'sine'], [2093, 0.07, 0.14, 'sine']],
};
function sfx(name) {
  if (!(sfxState.vol > 0)) return;
  const now0 = performance.now();
  if (now0 - (sfxState.last[name] ?? 0) < 60) return;   // 같은 소리가 한꺼번에 겹치지 않게
  sfxState.last[name] = now0;
  const ac = sfxCtx(); if (!ac) return;
  const t0 = ac.currentTime, vol = sfxState.vol * (name === 'boss' ? 0.18 : 0.12);
  for (const [f, at, dur, type] of SFX[name] ?? []) {
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = type; o.frequency.value = f;
    g.gain.setValueAtTime(0, t0 + at); g.gain.linearRampToValueAtTime(vol, t0 + at + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t0 + at + dur);
    o.connect(g).connect(sfxOut(ac)); o.start(t0 + at); o.stop(t0 + at + dur + 0.02);
  }
}
// 기본 공격 소리(2026-11-15 사용자 요청): 쏠 때마다 속성·종류별로 아주 짧게(0.03~0.11초). 많이 쏘므로 초당 14개(전설 이상은 20개)까지,
// 같은 소리는 0.055초 간격, 음 높이를 ±8% 흔들어 기관총처럼 들리지 않게. 소리 파일 없이 브라우저에서 만듦(흰 소리 + 거르개 + 짧은 음)
const ATK_KEY = 'gmm-defense-atk';
const atkSfx = { on: true, t: 0, n: 0, last: {}, buf: null };
try { atkSfx.on = localStorage.getItem(ATK_KEY) !== '0'; } catch { /* 기본 켬 */ }
function noiseBuf(ac) {
  if (!atkSfx.buf) { const b = ac.createBuffer(1, Math.floor(ac.sampleRate * 0.4), ac.sampleRate), d = b.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1; atkSfx.buf = b; }
  return atkSfx.buf;
}
function atkTone(ac, t, f0, f1, dur, type, v) {   // 파형은 sine·triangle만(네모·톱니파는 휴대폰 스피커에서 찢어지는 소리 — 2026-11-15 사용자 지적)
  const o = ac.createOscillator(), g = ac.createGain(); o.type = type === 'sine' ? 'sine' : 'triangle'; o.frequency.setValueAtTime(f0, t); if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + dur);
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(v, t + 0.008); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);   // 시작 0.004 → 0.008초(톡 튀는 소리 줄임)
  o.connect(g).connect(sfxOut(ac)); o.start(t); o.stop(t + dur + 0.02);
}
function atkHiss(ac, t, dur, ft, f0, f1, q, v) {   // 흰 소리를 거르개로 깎아 「휙·쉭·화르륵·지직」
  const s = ac.createBufferSource(), bq = ac.createBiquadFilter(), g = ac.createGain();
  s.buffer = noiseBuf(ac); s.playbackRate.value = 0.9 + Math.random() * 0.2;
  bq.type = ft; bq.frequency.setValueAtTime(f0, t); if (f1 !== f0) bq.frequency.exponentialRampToValueAtTime(f1, t + dur); bq.Q.value = q;
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(v, t + 0.008); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(bq).connect(g).connect(sfxOut(ac)); s.start(t, Math.random() * 0.3); s.stop(t + dur + 0.02);
}
function atkSound(f) {
  if (!atkSfx.on || !(sfxState.vol > 0) || document.hidden || !sfxState.ctx) return;   // 첫 누르기로 소리가 열린 뒤에만
  const now = performance.now(), big = f.tier >= 4;
  if (now - atkSfx.t > 1000) { atkSfx.t = now; atkSfx.n = 0; }
  if (atkSfx.n >= (big ? 20 : 14)) return;
  const key = f.near && f.type === 'phys' ? 'SLASH' : !f.elem || f.elem === 'NORMAL' ? (f.type === 'phys' ? 'ARROW' : 'ORB') : f.elem;
  if (now - (atkSfx.last[key] ?? 0) < 55) return;
  atkSfx.last[key] = now; atkSfx.n++;
  const ac = sfxCtx(); if (!ac) return;
  atkVoice(ac, key, ac.currentTime + 0.005, 0.92 + Math.random() * 0.16, sfxState.vol * 0.05 * (f.job === '보조' ? 0.6 : 1) * (big ? 1.35 : 1));
}
// 속성별 소리 한 번(ac: 소리판, t: 시작, p: 음 높이 배수, v: 크기). 2026-11-15 손봄: 200Hz 아래 붕붕 소리·네모/톱니파 빼고, 흰 소리는 작게·너무 높은 쇳소리 거름
function atkVoice(ac, key, t, p, v) {
  if (key === 'ARROW') { atkHiss(ac, t, 0.05, 'bandpass', 2600 * p, 1500 * p, 1.4, v * 0.8); atkTone(ac, t, 1200 * p, 800 * p, 0.03, 'triangle', v * 0.3); }   // 휙
  else if (key === 'SLASH') atkHiss(ac, t, 0.08, 'bandpass', 3500 * p, 1400 * p, 1.1, v * 0.85);   // 쉭
  else if (key === 'ORB') atkTone(ac, t, 900 * p, 520 * p, 0.06, 'sine', v * 0.8);   // 퓻
  else if (key === 'FIRE') { atkHiss(ac, t, 0.09, 'lowpass', 1800 * p, 500, 0.7, v * 0.9); atkTone(ac, t, 330 * p, 220 * p, 0.07, 'sine', v * 0.35); }   // 화르륵
  else if (key === 'ICE') { atkTone(ac, t, 2200 * p, 2080 * p, 0.07, 'sine', v * 0.45); atkTone(ac, t + 0.012, 3000 * p, 2900 * p, 0.05, 'sine', v * 0.22); }   // 팅
  else if (key === 'WATER') atkTone(ac, t, 380 * p, 900 * p, 0.06, 'sine', v * 0.8);   // 뽁
  else if (key === 'LIGHTNING') { atkHiss(ac, t, 0.06, 'bandpass', 3000 * p, 2200 * p, 1.6, v * 0.75); atkTone(ac, t, 1600 * p, 1100 * p, 0.04, 'triangle', v * 0.2); }   // 지직
  else if (key === 'HOLY') { atkTone(ac, t, 1320 * p, 1320 * p, 0.11, 'triangle', v * 0.4); atkTone(ac, t, 1980 * p, 1980 * p, 0.09, 'sine', v * 0.25); }   // 띵
  else if (key === 'DARK') { atkTone(ac, t, 300 * p, 200 * p, 0.09, 'sine', v * 0.5); atkHiss(ac, t, 0.08, 'lowpass', 800 * p, 300, 0.8, v * 0.5); }   // 웅
  else if (key === 'NATURE') { atkTone(ac, t, 620 * p, 540 * p, 0.05, 'triangle', v * 0.55); atkHiss(ac, t, 0.04, 'bandpass', 1800 * p, 1800 * p, 2, v * 0.35); }   // 톡
}
// 적 처치 소리(2026-11-15 사용자 요청): 짧은 「퍽」(흰 소리 + 떨어지는 낮은 음), 0.07초 간격·초당 10개까지, 여러 마리면 조금 크게. 보스는 큰 소리
const killSfx = { t: 0, n: 0, last: 0 };
function killSound(n, boss = false) {
  if (!atkSfx.on || !(sfxState.vol > 0) || document.hidden || !sfxState.ctx) return;
  const now = performance.now();
  if (!boss) { if (now - killSfx.t > 1000) { killSfx.t = now; killSfx.n = 0; } if (killSfx.n >= 10 || now - killSfx.last < 70) return; killSfx.n++; killSfx.last = now; }
  const ac = sfxCtx(); if (!ac) return;
  const t = ac.currentTime + 0.005, p = 0.9 + Math.random() * 0.2, v = sfxState.vol * 0.05 * Math.min(1.6, 1 + (n - 1) * 0.15);
  if (boss) { atkHiss(ac, t, 0.35, 'lowpass', 1400, 200, 0.7, sfxState.vol * 0.09); atkTone(ac, t, 330, 140, 0.4, 'sine', sfxState.vol * 0.07); atkTone(ac, t + 0.08, 660, 990, 0.25, 'triangle', sfxState.vol * 0.035); return; }   // 쿵 + 반짝
  atkHiss(ac, t, 0.07, 'bandpass', 1100 * p, 500 * p, 1.2, v * 0.9); atkTone(ac, t, 520 * p, 240 * p, 0.06, 'sine', v * 0.55);   // 퍽
}
function setAtkSfx(on) { atkSfx.on = on; try { localStorage.setItem(ATK_KEY, on ? '1' : '0'); } catch { /* 진행 */ } }
function setSfxVolume(v) { sfxState.vol = Math.round(Math.max(0, Math.min(1, v)) * 20) / 20; try { localStorage.setItem(SFX_KEY, String(sfxState.vol)); } catch { /* 진행 */ } }
// 판 상태가 바뀐 것을 보고 소리: 보스 등장·경고·외부탐사 성공·위습 찾음
const sfxWatch = { g: null, wave: 0, warn: 0, md: 0, kills: 0, boss: 0, fx: new WeakSet() };
// ── 전설 이상 갈매미 대사(2026-11-15 사용자 요청: 「전설 이상 유닛은 중간중간 대사 넣기」) ──
// 싸우는 중 9~16초마다 판 위 전설 이상 한 마리가 한마디(역할·속성·최상위에 따라), 그리고 보스 등장·보스 처치·처음 만들어짐·경고에 한마디.
// 말풍선은 머리 위 2.6초, 동시에 2개까지. 판 시간(G.t)으로 세서 일시정지면 멈춤. 규칙(engine.js)과는 상관없음
const TALK = {
  phys: ['이 부리 맛 좀 봐라!', '한 놈도 못 지나간다!', '끼룩! 다음!', '날개가 근질근질하군.', '더 센 놈 없어?', '줄 서! 하나씩 상대해 주지.'],
  mag: ['바닷바람이여, 힘을 빌려 다오.', '주문 준비 끝.', '끼룩… 집중, 집중.', '마력이 넘쳐흐른다!', '이 정도쯤이야.', '파도의 주문을 받아라!'],
  sup: ['내가 받쳐 줄게!', '다들 힘내! 끼룩!', '느려져라~ 느려져라~', '뒤는 맡겨!', '조금만 더 버텨!'],
  FIRE: ['다 태워 주마!', '뜨거운 맛 좀 봐!'], ICE: ['얼어붙어라.', '차갑게 식혀 주지.'], WATER: ['파도에 휩쓸려라!', '물보라 간다!'],
  LIGHTNING: ['찌릿하지?', '번개보다 빠르게!'], NATURE: ['숲의 힘을 보여 주마!', '뿌리째 묶어 주지.'], HOLY: ['빛이 함께하길.', '눈부시지?'], DARK: ['어둠 속으로…', '그림자가 너를 부른다.'],
  top: ['이 판은 내가 접수한다.', '전설? 난 그 위야.', '선장님, 맡겨만 주십시오.', '모두 물러서. 내가 간다.'],
  boss: ['보스다! 진형 유지!', '큰 놈이 온다… 끼룩!', '저 녀석부터 잡자!'],
  bossDown: ['해냈다! 끼룩끼룩!', '역시 우리 팀!', '봤지? 이게 갈매미다!'],
  born: ['드디어 내 차례군.', '부르셨습니까, 선장님?', '기다리느라 목 빠지는 줄 알았어.', '이제 진짜 싸움이다.'],
  warn: ['길이 꽉 찼어! 서둘러!', '이대로면 위험해!', '막아! 더 넘어오면 안 돼!'],
};
const talk = { list: [], next: 0, g: null, wave: 0, boss: 0, warn: 0, seen: new Set(), elist: [], enext: 0, low: new Set(), born: new Set(), crowd: 0 };
// 적 대사(2026-11-15 사용자 요청: 적도 중간중간 대사, 심심하지 않게). 말풍선은 어두운 붉은색(아군과 구별)
const ETALK = {
  line: ['이 섬은 우리가 접수한다!', '끼룩단 돌격!', '비켜라, 갈매미들!', '쭈~ 쭈~ 길을 내놔!', '방범대? 하나도 안 무섭다!', '물길은 우리 거다!', '오늘이야말로 초소를 넘는다!'],
  dark: ['흑화의 힘을 보여 주마…', '빛은 곧 꺼진다.', '너희도 곧 우리처럼 될 거야.', '어둠이 섬을 덮는다…'],
  boss: ['이 바다는 이제 내 것이다!', '초소째 삼켜 주마!', '감히 날 막겠다고?', '모두 쓸어버려라!'],
  low: ['크윽… 아직 끝나지 않았다!', '이, 이럴 수가…', '물러서지 마라, 부하들아!'],
  crowd: ['우리가 더 많다!', '길이 꽉 찼군, 크크크.', '막을 수 있으면 막아 봐!'],
};
function esay(e, msg) { if (!e || !msg) return; talk.elist = talk.elist.filter((b) => b.eid !== e.id); if (talk.elist.length >= 2) talk.elist.shift(); talk.elist.push({ eid: e.id, msg, t: G.t }); }
const talkPick = (arr, k) => arr[Math.floor(Math.abs(Math.sin(k * 12.9898) * 43758.5453) % 1 * arr.length)];
function talkLegend() { return E.fieldUnits(G).filter((u) => E.tierOf(u.id) >= 4 && E.UNIT[u.id].role !== E.SPECIAL_ROLE); }
function say2(u, msg) {   // 말풍선 하나(같은 갈매미가 말하는 중이면 바꿈, 2개 넘으면 가장 오래된 것을 지움)
  if (!u || !msg) return;
  talk.list = talk.list.filter((b) => b.uid !== u.uid);
  if (talk.list.length >= 2) talk.list.shift();
  talk.list.push({ uid: u.uid, msg, t: G.t });
}
function talkTick() {
  if (scene !== 'play' || !G || G.over) return;
  if (talk.g !== G) { Object.assign(talk, { g: G, list: [], next: G.t + 15, wave: G.wave, boss: G.bossDone ?? 0, warn: G.warn, seen: new Set(E.fieldUnits(G).map((u) => u.uid)), elist: [], enext: G.t + 12, low: new Set(), born: new Set(G.enemies.map((e) => e.id)), crowd: 0 }); return; }
  talk.list = talk.list.filter((b) => G.t - b.t < 2.6);
  // 적 대사: 보스 등장·보스 체력 30% 아래·적이 많이 쌓임·가끔 도발
  talk.elist = talk.elist.filter((b) => G.t - b.t < 2.6);
  { const ek = Math.floor(G.t * 10), alive = G.enemies.filter((e) => e.hp > 0);
    for (const e of alive) if (!talk.born.has(e.id)) { talk.born.add(e.id); if (e.boss) { esay(e, talkPick(ETALK.boss, ek + e.id)); talk.enext = G.t + 6; } }
    for (const e of alive) if (e.boss && !talk.low.has(e.id) && e.hp < e.max * 0.3) { talk.low.add(e.id); esay(e, talkPick(ETALK.low, ek + e.id)); talk.enext = G.t + 6; }
    if (alive.length > 40 && G.t - talk.crowd > 45) { talk.crowd = G.t; esay(alive[Math.floor(Math.abs(Math.sin(ek)) * alive.length) % alive.length], talkPick(ETALK.crowd, ek)); talk.enext = G.t + 6; }
    if (G.t >= talk.enext && alive.length) {
      const pool = alive.filter((e) => !e.boss && e.y > FIELD.y + 40), e = pool.length ? pool[Math.floor(Math.abs(Math.sin(ek * 3.7)) * pool.length) % pool.length] : null;   // 맨 위(정보 줄 가까이)는 피함
      if (e) esay(e, talkPick(String(e.look).startsWith('b:') ? ETALK.dark : ETALK.line, ek + e.id));
      talk.enext = G.t + 20 + Math.abs(Math.sin(ek)) * 12;   // 2026-11-15 사용자 요청: 대사 빈도 낮춤(8~14초 → 20~32초)
    }
    if (talk.born.size > 400) talk.born = new Set(alive.map((e) => e.id)); }
  const leg = talkLegend(); if (!leg.length) { for (const u of E.fieldUnits(G)) talk.seen.add(u.uid); return; }
  const k = Math.floor(G.t * 10), any = () => leg[Math.floor(Math.abs(Math.sin(k * 7.13)) * leg.length) % leg.length];
  for (const u of leg) if (!talk.seen.has(u.uid)) { talk.seen.add(u.uid); if (G.t - u.born < 1.5) { say2(u, talkPick(TALK.born, k + u.uid)); talk.next = Math.max(talk.next, G.t + 6); } }   // 처음 만들어짐
  for (const u of E.fieldUnits(G)) talk.seen.add(u.uid);
  if (G.wave !== talk.wave) { talk.wave = G.wave; if (E.waveInfo(G.wave).boss) { say2(any(), talkPick(TALK.boss, k)); talk.next = G.t + 7; } }
  if ((G.bossDone ?? 0) !== talk.boss) { talk.boss = G.bossDone ?? 0; say2(any(), talkPick(TALK.bossDown, k + 3)); talk.next = G.t + 7; }
  if (G.warn > talk.warn) { if (G.warn % 10 === 1) say2(any(), talkPick(TALK.warn, k + 5)); talk.warn = G.warn; }
  if (G.t >= talk.next) {   // 가끔 한마디
    const u = any(), kt = E.kit(u.id), top = E.tierOf(u.id) >= E.TOP_TIER;
    const pool = [...(kt.job === '보조' ? TALK.sup : kt.type === 'phys' ? TALK.phys : TALK.mag), ...(TALK[kt.elem] ?? []), ...(top ? TALK.top : [])];
    say2(u, talkPick(pool, k + u.uid)); talk.next = G.t + 22 + (Math.abs(Math.sin(k)) * 14);   // 2026-11-15 사용자 요청: 대사 빈도 낮춤(9~16초 → 22~36초)
  }
}
function drawTalk() {
  if (talk.elist.length) {   // 적 말풍선(어두운 붉은색)
    const byId = new Map(G.enemies.map((e) => [e.id, e]));
    for (const b of talk.elist) {
      const e = byId.get(b.eid); if (!e || e.hp <= 0) continue;
      const age = G.t - b.t, a = Math.min(1, age / 0.15, (2.6 - age) / 0.3); if (a <= 0) continue;
      ctx.font = `13px ${FONT}`;
      const sz = e.boss ? 84 : 44, tw = Math.min(210, ctx.measureText(b.msg).width) + 16, bh = 24;
      const bx = Math.max(FIELD.x + 4, Math.min(FIELD.x + FIELD.w - tw - 4, e.x - tw / 2)), by = Math.max(FIELD.y + 4, e.y - sz * 0.75 - 12 - bh - 6);
      ctx.globalAlpha = a;
      box(bx, by, tw, bh, 'rgba(58,16,20,.95)', 'rgba(255,120,110,.85)', 10, 1.5);
      ctx.beginPath(); const tx = Math.max(bx + 10, Math.min(bx + tw - 10, e.x)); ctx.moveTo(tx - 5, by + bh - 1); ctx.lineTo(tx + 5, by + bh - 1); ctx.lineTo(tx, by + bh + 6); ctx.closePath(); ctx.fillStyle = 'rgba(58,16,20,.95)'; ctx.fill();
      fitText(b.msg, bx + tw / 2, by + bh / 2, tw - 12, 13, '#ffd2c8', 'center');
      ctx.globalAlpha = 1;
    }
  }
  if (!talk.list.length) return;
  const byUid = new Map(E.fieldUnits(G).map((u) => [u.uid, u]));
  for (const b of talk.list) {
    const u = byUid.get(b.uid); if (!u) continue;
    const age = G.t - b.t, a = Math.min(1, age / 0.15, (2.6 - age) / 0.3); if (a <= 0) continue;
    ctx.font = `13px ${FONT}`;
    const z = usz(u.id), tw = Math.min(200, ctx.measureText(b.msg).width) + 16, bh = 24;
    const bx = Math.max(FIELD.x + 4, Math.min(FIELD.x + FIELD.w - tw - 4, u.x - tw / 2)), by = Math.max(FIELD.y + 4, u.y - 58 * z - bh - 8 - (1 - Math.min(1, age / 0.15)) * 6);
    ctx.globalAlpha = a;
    box(bx, by, tw, bh, 'rgba(250,244,228,.96)', 'rgba(145,122,76,.9)', 10, 1.5);
    ctx.beginPath(); const tx = Math.max(bx + 10, Math.min(bx + tw - 10, u.x)); ctx.moveTo(tx - 5, by + bh - 1); ctx.lineTo(tx + 5, by + bh - 1); ctx.lineTo(tx, by + bh + 6); ctx.closePath(); ctx.fillStyle = 'rgba(250,244,228,.96)'; ctx.fill();   // 꼬리
    fitText(b.msg, bx + tw / 2, by + bh / 2, tw - 12, 13, '#3b2614', 'center');
    ctx.globalAlpha = 1;
  }
}
function sfxTick() {
  if (scene !== 'play' || !G) return;
  if (sfxWatch.g !== G) { Object.assign(sfxWatch, { g: G, wave: G.wave, warn: G.warn, md: G.missionDone, kills: G.kills, boss: G.bossDone ?? 0 }); return; }
  if (!G.biasShown && G.bias && G.wave >= 1) { G.biasShown = true; biasAlarm(); }   // 판 성향 알림(한 번)
  if (G.kills > sfxWatch.kills) { killSound(G.kills - sfxWatch.kills); sfxWatch.kills = G.kills; }   // 적 처치 소리(2026-11-15)
  if ((G.bossDone ?? 0) !== sfxWatch.boss) { sfxWatch.boss = G.bossDone ?? 0; if (sfxWatch.boss) killSound(0, true); }
  if (G.wave !== sfxWatch.wave) { if (E.waveInfo(G.wave).boss) { sfx('boss'); bossAlarm(); } else if (G.wave === (E.RULES.riftFrom ?? 999) + 1) { sfx('warn'); riftAlarm(); }
    if (G.wave === E.RULES.hardLimitFrom) { if (E.waveInfo(G.wave).boss) bossWarn.next = 'hard'; else { sfx('warn'); hardAlarm(); } }
    if (G.wave > 1 && E.weatherAt(G, G.wave) !== E.weatherAt(G, G.wave - 1)) { if (bossWarn.wave === G.wave && performance.now() - bossWarn.at < BOSS_WARN_MS) bossWarn.next ??= 'weather'; else weatherAlarm(); }   // 날씨 바뀜(다른 알림이 떠 있으면 그 뒤에)
    sfxWatch.wave = G.wave; }   // 45웨이브는 보스 웨이브라 보스 경고 뒤에 이어서
  if (G.warn > sfxWatch.warn) sfx('warn');
  sfxWatch.warn = G.warn;
  if (G.missionDone > sfxWatch.md) sfx('story');
  sfxWatch.md = G.missionDone;
  for (const f of G.fx) if ((f.kind === 'wisp' || f.kind === 'shot') && !sfxWatch.fx.has(f)) { sfxWatch.fx.add(f); if (f.kind === 'wisp') sfx('wisp'); else atkSound(f); }   // 기본 공격 소리
}
// 보스 등장(2026-11-12 사용자 요청: 위험하니 보조 창을 내리고 판을 보여 주기 + 빨간 경고)
// 일시정지·결과·보상 고르기·규칙처럼 사용자가 일부러 연 큰 창은 그대로 두고, 판을 가리는 보조 창만 닫음
const BOSS_CLOSE = new Set(['shop', 'book', 'upgrade', 'mission', 'more', 'meter', 'storage', 'recruit', 'sound', 'signal', 'peer']);
const bossWarn = { at: -1e9, wave: 0 };
const BOSS_WARN_MS = 2600;
function hardAlarm() {   // 45웨이브: 적 50마리 넘으면 즉시 패배 알림(2026-11-15)
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'hard';
}
function augAlarm(id) {   // 증강체를 고르면 알림(2026-11-15 사용자 요청: 균열·보스 경고처럼). 창은 닫지 않음
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'aug'; bossWarn.aug = id;
}
function biasAlarm() {   // 판 성향 알림(판 시작)
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'bias';
}
function weatherAlarm() {   // 날씨가 바뀌면 알림(2026-11-16). 창은 닫지 않고 띠만
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'weather';
}
function riftAlarm() {   // 균열 저항 시작 알림(2026-11-15 사용자 요청: 보스 경고처럼 한 번). 창은 닫지 않고 띠만
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'rift';
}
function bossAlarm() {
  bossWarn.at = performance.now(); bossWarn.wave = G.wave; bossWarn.kind = 'boss';
  info = null;
  if (modal && BOSS_CLOSE.has(modal.kind)) modal = null;
}
function drawBossWarn() {
  let age = performance.now() - bossWarn.at;
  if (age > BOSS_WARN_MS && bossWarn.next === 'hard' && G && !G.over) { bossWarn.next = null; hardAlarm(); sfx('warn'); age = 0; }   // 보스 경고가 끝나면 즉시 패배 알림
  if (age > BOSS_WARN_MS && bossWarn.next === 'weather' && G && !G.over) { bossWarn.next = null; weatherAlarm(); age = 0; }   // 앞 알림이 끝나면 날씨 알림
  if (age > BOSS_WARN_MS || !G || G.over) return;
  const fade = Math.min(1, age / 150, (BOSS_WARN_MS - age) / 500), pulse = 0.55 + 0.45 * Math.abs(Math.sin(age / 190));
  if (bossWarn.kind === 'hard') {   // 즉시 패배 한도: 주황·빨강
    ctx.save(); ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
    const gr = ctx.createRadialGradient(VW / 2, VH / 2, Math.min(VW, VH) * 0.3, VW / 2, VH / 2, Math.hypot(VW, VH) / 2);
    gr.addColorStop(0, 'rgba(255,120,30,0)'); gr.addColorStop(1, `rgba(255,120,30,${0.4 * pulse * fade})`);
    ctx.fillStyle = gr; ctx.fillRect(0, 0, VW, VH);
    ctx.lineWidth = 6; ctx.strokeStyle = `rgba(255,150,70,${0.8 * pulse * fade})`; ctx.strokeRect(3, 3, VW - 6, VH - 6);
    ctx.restore();
    ctx.save();
    if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
    ctx.globalAlpha = fade;
    const bw = W - 60, bh = 100, bx = 30, by = 326;
    box(bx, by, bw, bh, 'rgba(60,24,6,.93)', `rgba(255,160,80,${0.6 + 0.4 * pulse})`, 12, 3);
    hazard(bx + 14, by + 8, bw - 28, 6);
    fitText(`⚠ 길 위 적 ${E.hardLimitOf(G)}마리 넘으면 즉시 패배!`, W / 2, by + 44, bw - 30, 24, '#ffb070', 'center');
    fitText(`${E.RULES.hardLimitFrom}웨이브부터는 경고 없이 바로 끝나요 · 위 상황판 「적 N/${E.hardLimitOf(G)}」을 지켜보세요`, W / 2, by + 76, bw - 30, 14, '#ffe2c8', 'center');
    ctx.restore();
    return;
  }
  if (bossWarn.kind === 'bias') {   // 판 성향(갑옷형·결계형)
    const bi = E.BIAS[G.bias]; if (!bi) return;
    ctx.save(); if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
    ctx.globalAlpha = fade;
    const bw = W - 60, bh = G.topOpen ? 116 : 92, bx = 30, by = 330, cl = { armor: '143,183,255', mr: '255,178,122', split: '143,224,138' }[G.bias] ?? '220,220,220';
    box(bx, by, bw, bh, 'rgba(10,26,40,.93)', `rgba(${cl},${0.55 + 0.35 * pulse})`, 12, 3);
    fitText(`이번 판은 ${bi.name}!`, W / 2, by + 38, bw - 30, 24, `rgb(${cl})`, 'center');
    fitText(bi.desc, W / 2, by + 68, bw - 30, 15, '#e3eef5', 'center');
    if (G.topOpen) fitText(`이번 판 열린 최상위 ${G.topOpen.length}종(등급마다 절반) — 조합법 창에서 확인`, W / 2, by + 94, bw - 30, 14, '#ffd76a', 'center');   // 2026-11-21
    ctx.restore(); return;
  }
  if (bossWarn.kind === 'weather') {   // 날씨 바뀜: 하늘색 띠(가장자리 빛 없이 — 자주 뜨므로 가볍게)
    const wx = E.weatherOf(G);
    ctx.save();
    if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
    ctx.globalAlpha = fade;
    const bw = W - 60, bh = 92, bx = 30, by = 330;
    box(bx, by, bw, bh, 'rgba(8,34,52,.93)', `rgba(143,216,255,${0.55 + 0.35 * pulse})`, 12, 3);
    fitText(`날씨가 바뀌었어요: ${wx.name}`, W / 2, by + 38, bw - 30, 23, '#8fd8ff', 'center');
    fitText(`${wx.desc} · ${E.WEATHER_LEN}웨이브 동안`, W / 2, by + 68, bw - 30, 15, '#d8f1ff', 'center');
    ctx.restore();
    return;
  }
  if (bossWarn.kind === 'aug') {   // 증강체 획득: 금빛으로 같은 모양
    const a = E.AUGS[bossWarn.aug]; if (!a) return;
    ctx.save(); ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
    const gr = ctx.createRadialGradient(VW / 2, VH / 2, Math.min(VW, VH) * 0.3, VW / 2, VH / 2, Math.hypot(VW, VH) / 2);
    gr.addColorStop(0, 'rgba(255,200,90,0)'); gr.addColorStop(1, `rgba(255,200,90,${0.3 * pulse * fade})`);
    ctx.fillStyle = gr; ctx.fillRect(0, 0, VW, VH);
    ctx.lineWidth = 6; ctx.strokeStyle = `rgba(255,220,140,${0.75 * pulse * fade})`; ctx.strokeRect(3, 3, VW - 6, VH - 6);
    ctx.restore();
    ctx.save();
    if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
    ctx.globalAlpha = fade;
    const bw = W - 60, bh = 96, bx = 30, by = 328;
    box(bx, by, bw, bh, 'rgba(48,32,8,.93)', `rgba(243,216,158,${0.6 + 0.4 * pulse})`, 12, 3);
    fitText(`✦ 증강체 「${a.name}」`, W / 2, by + 34, bw - 30, 28, C.goldEdge, 'center');
    fitText(a.desc, W / 2, by + 70, bw - 30, 15, '#fff1d0', 'center');
    ctx.restore();
    return;
  }
  if (bossWarn.kind === 'rift') {   // 균열 저항: 보라색으로 같은 모양
    ctx.save(); ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
    const gr = ctx.createRadialGradient(VW / 2, VH / 2, Math.min(VW, VH) * 0.3, VW / 2, VH / 2, Math.hypot(VW, VH) / 2);
    gr.addColorStop(0, 'rgba(170,90,255,0)'); gr.addColorStop(1, `rgba(170,90,255,${0.38 * pulse * fade})`);
    ctx.fillStyle = gr; ctx.fillRect(0, 0, VW, VH);
    ctx.lineWidth = 6; ctx.strokeStyle = `rgba(200,150,255,${0.8 * pulse * fade})`; ctx.strokeRect(3, 3, VW - 6, VH - 6);
    ctx.restore();
    ctx.save();
    if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
    ctx.globalAlpha = fade;
    const bw = W - 60, bh = 104, bx = 30, by = 324;
    box(bx, by, bw, bh, 'rgba(32,10,58,.92)', `rgba(201,163,255,${0.6 + 0.4 * pulse})`, 12, 3);
    fitText('⚠ 균열 저항 시작!', W / 2, by + 32, bw - 30, 28, '#c9a3ff', 'center');
    fitText(`일반 적이 전설·히든 이하 공격을 웨이브마다 ${Math.round((E.RULES.riftPer ?? 0) * 100)}%씩 덜 받아요(최대 ${Math.round((E.RULES.riftMax ?? 0) * 100)}%)`, W / 2, by + 64, bw - 30, 14, '#ead9ff', 'center');
    fitText('초불영제 피해와 보스는 그대로예요', W / 2, by + 86, bw - 30, 13, '#cbb6ea', 'center');
    ctx.restore();
    return;
  }
  // 화면 가장자리 빨간 번쩍임
  ctx.save(); ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
  const gr = ctx.createRadialGradient(VW / 2, VH / 2, Math.min(VW, VH) * 0.3, VW / 2, VH / 2, Math.hypot(VW, VH) / 2);
  gr.addColorStop(0, 'rgba(255,40,40,0)'); gr.addColorStop(1, `rgba(255,40,40,${0.42 * pulse * fade})`);
  ctx.fillStyle = gr; ctx.fillRect(0, 0, VW, VH);
  ctx.lineWidth = 6; ctx.strokeStyle = `rgba(255,90,80,${0.8 * pulse * fade})`; ctx.strokeRect(3, 3, VW - 6, VH - 6);
  ctx.restore();
  // 판 가운데 띠
  ctx.save();
  if (landscape) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); }
  ctx.globalAlpha = fade;
  const bw = W - 60, bh = 92, bx = 30, by = 330;
  box(bx, by, bw, bh, 'rgba(60,8,10,.9)', `rgba(255,107,107,${0.6 + 0.4 * pulse})`, 12, 3);
  fitText('⚠ 보스 등장!', W / 2, by + 34, bw - 30, 30, '#ff6b6b', 'center');
  { const bp = E.BOSS_PAT_INFO[E.BOSS_PAT[(Math.round(bossWarn.wave / E.RULES.bossEvery) - 1) % E.BOSS_PAT.length]];
    fitText(`웨이브 ${bossWarn.wave} · 방어 ${E.armorAt(G, bossWarn.wave)} · ${E.baseLenOf(bossWarn.wave)}초 안에 잡기 · 보스 기술 「${bp.name}」 ${bp.desc}${bossWarn.wave >= (E.RULES.lateBossFrom ?? 999) ? ` · ${{ armor: '갑옷 보스(방어 ×2.2) — 마법으로', mr: '결계 보스(마방 ×2.2) — 물리로', split: '철벽 보스(방어·마방 ×2.2) — 독으로' }[G.bias] ?? ''}` : ''}`, W / 2, by + 70, bw - 30, 15, '#ffd2c8', 'center'); }
  ctx.restore();
}
// 한 번에 조합(2026-11-15 사용자 요청): 바로 아래 재료가 없어도 기본 재료가 다 있으면 중간 재료부터 차례로 조합
function chainPlan(r) {   // 조합할 순서(아래부터, 마지막이 r). 기본 재료가 모자라면 null
  const pool = E.countOnField(G), steps = [];
  const need = (c, depth) => {
    if ((pool[c] ?? 0) > 0) { pool[c]--; return true; }
    const rr = depth < 8 ? E.recipeFor(c) : null;
    if (!rr || E.tierOf(c) >= E.TOP_TIER) return false;   // 상위(제한~영원)는 중간 재료로 몰래 만들지 않음(마리 수 제한)
    for (const x of rr.in) if (!need(x, depth + 1)) return false;
    steps.push(rr); return true;
  };
  for (const x of r.in) if (!need(x, 1)) return null;
  steps.push(r); return steps;
}
const canChain = (r) => !G.over && !E.topBlocked(G, r) && !!chainPlan(r);
function combineChain(r, keep = 0) {   // 바로 되면 그대로, 아니면 중간 재료(효과 없이) → 마지막만 효과와 함께
  if (canMake(r, keep)) return combineNow(r, keep);
  const plan = canChain(r) ? chainPlan(r) : null; if (!plan) return null;
  for (const st of plan.slice(0, -1)) if (!E.combine(G, st)) return null;
  const nu = combineNow(r); if (nu) nu.chain = plan.length - 1;
  return nu;
}
function combineNow(r, keep = 0) {
  const nu = E.combine(G, r, keep);
  if (nu) { sfx('combine'); const t = E.tierOf(nu.id); pfx('collect', nu.x, nu.y - 20, { dur: 1.2 }); if (t >= E.TOP_TIER) pfx('portal', nu.x, nu.y + 4, { dur: 1.6 }); if (t >= 4) pfx('starfall', nu.x, nu.y - 10, { dur: 1.6, elem: t >= E.TOP_TIER ? 'HOLY' : undefined }); }
  return nu;
}
// ── 화려한 효과(2026-11-15 사용자 요청): pixelfx(MIT) 도트 효과를 판 위 투명한 층에 그림(pixfx.js, scripts/web/build-pixfx.sh로 묶음) ──
// 큰 순간에만: 고유 기술 · 보스 처치 · 조합 완성 · 위습 찾기 · 외부탐사 성공 · 전설 이상이 보스를 칠 때. 「화려한 효과」를 끄면 나오지 않음
let PFX = null, pfxLayer = null;
const pfxSeen = new WeakSet(), pfxWatch = { g: null, boss: 0, md: 0, bossAt: null };
import('./pixfx.js?v=071157c1acc6').then((m) => { PFX = m; pfxLayer = m.createLayer(FIELD.w, FIELD.h, 1.6); }).catch(() => { /* 효과 없이 진행 */ });
function pfx(name, x, y, o = {}) { return !!(pfxLayer && !fxLite) && pfxLayer.spawn(name, x - FIELD.x, y - FIELD.y, o); }   // 띄웠으면 true(무거워 건너뛰면 false)
// 고유 기술 폭발(nova)·장판(field)은 속성마다 다른 효과: [효과, 속성 색 입힘]
// 2.5D(2026-11-15 사용자 요청): 바닥 타원 위에서 터지는 효과로. [효과, 속성 색을 입힐지]
const PFX_NOVA = { FIRE: ['firering', false], ICE: ['frostnova', false], WATER: ['frostnova', true], LIGHTNING: ['thunderclap', true], HOLY: ['holypillar', false], DARK: ['implosion', false], NATURE: ['gmmbloom', true], NORMAL: ['gmmquake', true] };   // gmm*: 직접 만든 2.5D 효과
const PFX_FIELD = { HOLY: ['healaoe', false] };   // 나머지는 직접 만든 속성 장판(gmmfield: 불씨·서리·물결·그림자·번개·잎·먼지)
function pfxTick(dt) {   // 판 상태가 바뀐 것을 보고 효과를 띄움
  if (!pfxLayer || scene !== 'play' || !G) return;
  if (pfxWatch.g !== G) { Object.assign(pfxWatch, { g: G, boss: G.bossDone ?? 0, md: G.missionDone }); pfxLayer.clear(); for (const f of G.fx) pfxSeen.add(f); return; }
  const boss = G.enemies.find((e) => e.boss); if (boss) pfxWatch.bossAt = { x: boss.x, y: boss.y };
  if ((G.bossDone ?? 0) !== pfxWatch.boss) { pfxWatch.boss = G.bossDone ?? 0; const p = pfxWatch.bossAt; if (p) { pfx('gmmvictory', p.x, p.y, { dur: 2.0, force: true, reach: 70 }); pfx('confetti', p.x, p.y - 90, { dur: 1.0, force: true }); } }   // 보스 처치: 축하(바닥 금빛 물결·솟는 빛 알갱이·위의 큰 별 + 색종이) — 공격 효과와 다르게. 색종이는 바닥 한 줄에 쌓이기 전(1초)에 끊음
  if (G.missionDone > pfxWatch.md) { pfxWatch.md = G.missionDone; pfx('quest', W / 2, FIELD.y + 230, { dur: 2.2, force: true }); }
  for (const f of G.fx) {
    if (pfxSeen.has(f)) continue; pfxSeen.add(f);
    if (f.kind === 'sig') {
      const el = f.elem ?? 'NORMAL', pts = f.pts ?? [];
      if (f.sk === 'smash') { if (!pfx('gmmquake', f.x1, f.y1, { dur: 1.3, elem: el, reach: f.r ?? 46 })) pfx('crit', f.x1, f.y1 - 6, { dur: 0.9, elem: el }); }   // 강타: 땅이 울리며 금이 가고 돌이 튐(직접 만든 2.5D, 무거우면 예전 별)
      else if (f.sk === 'execute') pfx('firering', f.x1, f.y1, { dur: 0.9, elem: 'DARK', reach: f.r ?? 46 });   // 2026-11-15: 예전 darkx는 맞은 자리보다 100쯤 위에서 크게 터져 「효과만 있다」로 보임 → 맞은 자리에서 실제 피해 범위(r)만큼 퍼지는 어두운 고리
      else if (f.sk === 'nova' && f.type === 'mag') { pfx('holypillar', f.x1, f.y1, { dur: 1.3, elem: el === 'HOLY' ? undefined : el }); pfx('firework', f.x1, f.y1 - 50, { dur: 1.2, elem: el }); }   // 폭발형 마법딜러(우로보로스·귀해): 하늘에서 빛기둥 + 위에서 터지는 불꽃(2026-11-15 사용자 선택)
      else if (f.sk === 'nova') { const nv = PFX_NOVA[el] ?? ['firering', true]; pfx(nv[0], f.x1, f.y1, { dur: 1.2, elem: nv[1] ? el : undefined, reach: f.r ?? 80 }); }   // 기술 범위만큼 퍼짐
      else if (f.sk === 'bind') pfx('gmmbind', f.x1, f.y1, { dur: 1.5, elem: el, reach: f.r ?? 70 });   // 바닥 마법진 + 둘레에서 솟아 조이는 사슬
      else if (f.sk === 'field') { const fd = PFX_FIELD[el] ?? ['gmmfield', true]; pfx(fd[0], f.x1, f.y1, fd[0] === 'gmmfield' ? { dur: 3.0, elem: el, kind: el, reach: f.r ?? 80 } : { dur: 1.6 }); if (el === 'NATURE') pfx('gmmbloom', f.x1, f.y1, { dur: 1.5, reach: f.r ?? 80 }); }   // 자연 장판은 처음에 풀잎이 솟는 꽃핌   // 장판은 3초 동안(엔진 장판 시간)
      else if (f.sk === 'storm') pts.slice(0, 2).forEach(([x, y]) => pfx('strike', x, y, { dur: 0.8 }));   // 낙뢰는 2개까지(무거움)
      else if (f.sk === 'bounce') pts.slice(0, 3).forEach(([x, y]) => pfx('zap', x, y - 8, { dur: 0.7, elem: el }));
      else if (f.sk === 'pierce') pfx('zap', f.x1, f.y1 - 8, { dur: 0.7, elem: el });
    } else if (f.kind === 'wisp') pfx('collect', f.x, f.y - 16, { dur: 1.2 });
    else if (f.kind === 'num' && (f.crit || f.tier >= 6)) pfx('spark', f.x, f.y - 14, { dur: 0.5 });   // 반짝임은 치명타·최상위만(숫자가 많아져 가볍게)
  }
  if (running()) pfxLayer.update(dt);
}
function drawPfx() { if (pfxLayer && !fxLite) pfxLayer.draw(ctx, FIELD.x, FIELD.y, FIELD.w, FIELD.h); }
function setVolume(v) {
  music.vol = Math.round(Math.max(0, Math.min(1, v)) * 20) / 20;   // 5% 단위
  if (music.el) music.el.volume = music.vol;
  try { localStorage.setItem(VOLUME_KEY, String(music.vol)); } catch { /* 저장 못 해도 진행 */ }
}
function musicPlay() { if (music.on) musicEl().play().catch(() => {}); }
window.addEventListener('pointerdown', () => musicPlay(), { once: true });   // 브라우저는 첫 누르기 전 소리를 막음 → 처음 누를 때 음악 시작(켬일 때만)
function musicToggle() {
  music.on = !music.on;
  try { localStorage.setItem(MUSIC_KEY, music.on ? 'on' : 'off'); } catch { /* 저장 못 해도 진행 */ }
  if (music.on) musicPlay(); else music.el?.pause();
}
function musicSync() {   // 매 장면: 들어야 할 때만 재생
  if (!music.el) return;
  const want = music.on && !document.hidden && modal?.kind !== 'pause';   // 2026-11-15 사용자 요청: 시작·로비 화면에서도 음악이 이어서(일시정지 창에서만 멈춤)
  if (want && music.el.paused) music.el.play().catch(() => {});
  else if (!want && !music.el.paused) music.el.pause();
}
const say = (msg) => { toast = { msg, until: performance.now() + 2400 }; };
const selUnits = () => selected.map((uid) => E.unitById(G, uid)).filter((u) => u && !u.away);

function start(seed, mode = 'normal', diff = selDiff) {
  shake.until = 0;
  G = E.newGame(seed === undefined ? undefined : seed >>> 0, mode, diff); scene = 'play'; modal = null; speed = 1; selected = []; lastNews = 0; info = null;
  musicPlay();   // 누른 순간 안에서 재생 시작
  tut = !inRoom() && mode === 'normal' && !ls.get(TUT_KEY) ? { step: 0, n0: G.units.length, t: performance.now() } : null;
  if (!inRoom() && !tut) modal = { kind: 'intro', t: performance.now() };   // 2026-11-21 사용자 요청: 바로 시작하지 말고 이번 판 소개(멈춘 채, 「시작」을 누르면 진행)
}
// ── 이어하기(사용자 요청 2026-11-08): 3초마다·화면을 떠날 때 판을 기기에 저장 ──
function saveNow() {
  if (scene !== 'play' || !G || G.over) return;
  if (coop()) ls.set(ROOM_SAVE_KEY, JSON.stringify({ code: net.code, round: net.round, gs: net.giftSent, gr: net.giftRecv, save: E.saveGame(G) }));
  else if (!inRoom()) { ls.set(SAVE_KEY, E.saveGame(G)); resumeMeta = { wave: G.wave, mode: G.mode, diff: G.diff, at: Date.now() }; ls.set(SAVE_META_KEY, JSON.stringify(resumeMeta)); cloudSave(false); }
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
document.addEventListener('visibilitychange', () => { if (document.hidden) { saveNow(); cloudSave(true); } });
window.addEventListener('pagehide', () => { saveNow(); cloudSave(true); });
// ── 계정 저장(2026-11-09): 로그인돼 있으면 최고 기록·이어하기 판을 계정에도(/my/api/defense). 안 돼 있으면 지금처럼 이 기기에만 ──
const cloud = { on: false, can: false, nick: null, upAt: 0, upWave: 0 };   // can: 서버에 계정 저장 기능이 있음(401·200), 없으면(404) 안내도 숨김
function cloudPost(body, keepalive = false) {
  if (!cloud.on) return Promise.resolve(null);
  return fetch('/my/api/defense', { method: 'POST', headers: { 'content-type': 'application/json', 'x-gmm': '1' }, credentials: 'same-origin', keepalive: keepalive && JSON.stringify(body).length < 60000, body: JSON.stringify(body) })
    .then((r) => { if (r.status === 401) cloud.on = false; return r.ok ? r.json() : null; }).catch(() => null);
}
function cloudSave(force) {   // 웨이브가 바뀌었거나 30초 지났을 때(떠날 때는 바로)
  if (!cloud.on || !resumeMeta || scene !== 'play' || !G || G.over || coop() || inRoom()) return;
  const now = Date.now();
  if (!force && G.wave === cloud.upWave && now - cloud.upAt < 30000) return;
  const data = ls.get(SAVE_KEY); if (!data) return;
  cloud.upAt = now; cloud.upWave = G.wave;
  cloudPost({ save: { data, wave: Math.max(1, resumeMeta.wave), mode: resumeMeta.mode ?? 'normal', at: resumeMeta.at ?? now } }, force);
}
async function cloudInit() {
  let me = null;
  try { const r = await fetch('/my/api/defense', { headers: { 'x-gmm': '1' }, credentials: 'same-origin', cache: 'no-store' }); cloud.can = r.ok || r.status === 401; me = r.ok ? await r.json() : null; } catch { me = null; }
  if (!me || typeof me !== 'object') return;
  cloud.on = true; cloud.nick = me.nickname ?? null;
  // 최고 기록: 모드마다 큰 값으로 합치고, 이 기기 기록이 더 크면 계정에도 올림
  const up = {};
  for (const m of Object.keys(E.MODES)) for (const d of E.DIFF_ORDER) {
    const rk = recKey(m, d), mine = bestOf(m, d), theirs = Number(me.best?.[rk]) || 0;
    if (theirs > mine) ls.set(bestKey(m, d), String(theirs)); else if (mine > theirs) up[rk] = mine;
  }
  best = bestOf('normal', selDiff);
  // 이어하기: 더 나중에 저장한 쪽을 씀(계정 판은 읽을 수 있을 때만)
  const sv = me.save, localAt = resumeMeta?.at ?? 0;
  const svg = sv && sv.at > localAt ? E.loadGame(sv.data) : null;
  if (svg) { ls.set(SAVE_KEY, sv.data); resumeMeta = { wave: sv.wave, mode: sv.mode, diff: svg.diff, at: sv.at }; ls.set(SAVE_META_KEY, JSON.stringify(resumeMeta)); }
  const body = Object.keys(up).length ? { best: up } : {};
  let ex = null; try { ex = me.extra ? JSON.parse(me.extra) : null; } catch { ex = null; }
  if (mergeExtra(ex) || (!me.extra && (hist.length || clearCount() || cosmetic.slots.some(Boolean)))) body.extra = JSON.stringify(extraBlob());   // 합친 게 다르면 다시 올림
  if (resumeMeta && ls.get(SAVE_KEY) && (!sv || localAt > sv.at)) body.save = { data: ls.get(SAVE_KEY), wave: Math.max(1, resumeMeta.wave), mode: resumeMeta.mode ?? 'normal', at: localAt || Date.now() };
  if (Object.keys(body).length) cloudPost(body);
}
cloudInit();
function musicButton(x, y) {
  button(x, y, 46, 40, '♪', () => { modal = { kind: 'sound' }; musicPlay(); }, 'normal');
  if (!music.on) { ctx.strokeStyle = C.bad; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.moveTo(x + 12, y + 30); ctx.lineTo(x + 34, y + 10); ctx.stroke(); }
}
function drawCompStrip(comp, x, y, w, sz = 34) {   // 조합 유닛 그림 줄(등급 색 고리 + ×마리 수), 넘치면 +N
  if (!comp?.length) { text('기록된 갈매미가 없어요', x, y + sz / 2, 13, C.muted); return; }
  const step = sz + 4, fit = Math.max(1, Math.floor(w / step));
  const shown = comp.length > fit ? comp.slice(0, fit - 1) : comp;
  shown.forEach(([id, n], i) => {
    const cx = x + i * step + sz / 2, cy = y + sz / 2, u = E.UNIT[id]; if (!u) return;
    ctx.beginPath(); ctx.arc(cx, cy, sz / 2, 0, Math.PI * 2); ctx.fillStyle = 'rgba(11,30,42,.9)'; ctx.fill();
    ctx.lineWidth = E.tierOf(id) >= 6 ? 2.5 : 1.6; ctx.strokeStyle = GRADE_COLOR[u.grade]; ctx.stroke();
    ctx.save(); ctx.beginPath(); ctx.arc(cx, cy, sz / 2 - 1.5, 0, Math.PI * 2); ctx.clip(); drawFit(unitImg(id), cx - sz / 2, cy - sz / 2, sz, sz); ctx.restore();
    if (n > 1) { box(cx + sz / 2 - 16, cy + sz / 2 - 12, 18, 13, 'rgba(7,19,28,.92)', null, 4); text(`×${n}`, cx + sz / 2 - 7, cy + sz / 2 - 5.5, 10, C.goldEdge, 'center'); }
  });
  if (comp.length > shown.length) text(`+${comp.length - shown.length}`, x + shown.length * step + sz / 2, y + sz / 2, 13, C.muted, 'center');
}
// ── 판 기록(2026-11-10 사용자 요청: 최고 기록뿐 아니라 그때 쓴 조합 유닛도) ──
const HIST_KEY = 'gmm-defense-history', BESTCOMP_KEY = 'gmm-defense-bestcomp';
const readJson = (k, d) => { try { const v = JSON.parse(ls.get(k) ?? 'null'); return v ?? d; } catch { return d; } };
let hist = readJson(HIST_KEY, []), bestComp = readJson(BESTCOMP_KEY, {});
function compOf(g) {   // 판의 갈매미(창고·탐사 포함) 종류별 마리 수, 높은 등급부터 16종
  const c = {};
  for (const u of g.units) { if (E.UNIT[u.id]?.role === E.SPECIAL_ROLE) continue; c[u.id] = (c[u.id] ?? 0) + 1; }
  return Object.entries(c).sort((a, b) => (E.tierOf(b[0]) - E.tierOf(a[0])) || (b[1] - a[1])).slice(0, 16);
}
function recordRun(w) {
  const rec = { at: Date.now(), diff: G.diff, mode: G.mode, wave: w, win: G.over === 'win', coop: coop(), upg: [...G.upg], comp: compOf(G), ...(G.leftover != null ? { left: G.leftover } : {}) };
  hist = [rec, ...hist].slice(0, 30); ls.set(HIST_KEY, JSON.stringify(hist));
  const k = recKey(G.mode, G.diff), old = bestComp[k];
  if (!old || w > old.wave) { bestComp[k] = rec; ls.set(BESTCOMP_KEY, JSON.stringify(bestComp)); }
  G.lastRec = rec;
}
function saveBest() {   // 모드마다 따로
  if (G.lastRec) return;   // 한 판에 한 번만(같이하기에서 친구 판을 보고 돌아와도 다시 기록하지 않음)
  const w = G.over === 'win' ? E.RULES.waves + 1 : G.wave;
  recordRun(w);
  if (G.over === 'win') addClear(G.diff);
  if (G.gold > 0 && !coop()) { stats.bank = (stats.bank ?? 0) + Math.floor(G.gold); ls.set(STATS_KEY, JSON.stringify(stats)); G.bankNews = `새우 ${fmtN(Math.floor(G.gold))} 저금 · 모은 새우 ${fmtN(stats.bank)}`; }   // 2026-11-16 새우 저금
  if (w > bestOf(G.mode, G.diff)) ls.set(bestKey(G.mode, G.diff), String(w));
  best = bestOf('normal', selDiff);
  const solo = !coop() && !inRoom();
  if (coop()) ls.del(ROOM_SAVE_KEY); else if (solo) clearSave();   // 끝난 판은 이어하기에서 지움
  cloudPost({ best: { [recKey(G.mode, G.diff)]: Math.min(E.RULES.waves + 1, w) }, extra: JSON.stringify(extraBlob()), ...(solo ? { save: null } : {}) }, true);   // 판이 끝나면 바로 계정에(최고 기록·판 기록·클리어 수·포인트, 혼자 판이면 계정 이어하기도 지움)
}

// ── 꾸미기(2026-11-10 사용자 요청: 클리어 횟수를 채우면 원하는 유닛에 날개·오라를 달고 색을 고름) ──
// 클리어 횟수는 달인·장인만 센다(2026-11-10 사용자 요청). 장인도 횟수는 1번이지만 칸 열기(보상)에는 2번으로 친다. 모드·같이하기 포함.
// 능력치는 그대로, 판 위 그림만 바뀐다. 이 기기에만 저장
const STATS_KEY = 'gmm-defense-stats', COS_KEY = 'gmm-defense-cosmetic';
const COS_SLOTS = [1, 3, 5, 10, 15, 20, 30, 50];   // 칸이 열리는 보상 클리어 수(달인 1 · 장인 2)
const CLEAR_PTS = { master: 1, artisan: 2 };   // 이 밖의 난이도(입문~상급) 클리어는 세지 않음
const COS_WINGS = [['', '없음'], ['feather', '깃털']];   // 요정 날개는 어울리지 않아 뺌(2026-11-10 사용자 의견) → 「두름」 오라로
const COS_AURAS = [['', '없음'], ['glow', '빛'], ['flame', '불꽃'], ['wrap', '두름']];   // 두름: 몸 테두리를 따라 빛이 감쌈
const COS_COLORS = [['#ffd66b', '금빛'], ['#ff5a5a', '빨강'], ['#ff8fc7', '분홍'], ['#b07bff', '보라'], ['#5aa8ff', '파랑'], ['#6fe8ff', '하늘'], ['#6fe08a', '초록'], ['#ffffff', '흰색'], ['rainbow', '무지개']];
const RAINBOW_DIFF = 'artisan';   // 무지개 색은 장인 클리어로 열림
// 꾸미기 늘리기(2026-11-11 사용자 요청): 공격 연출·처치 효과·발밑(칸마다), 판 테마·저장 칸(전체). 값 뒤 숫자는 여는 포인트(없으면 바로)
const COS_SHOTS = [['', '기본'], ['star', '별'], ['heart', '하트'], ['feather', '깃털'], ['orb', '구슬']];
const COS_KILLS = [['', '없음'], ['sparkle', '별가루'], ['feather', '깃털'], ['bubble', '물방울'], ['petal', '꽃잎']];
const COS_BASES = [['', '없음'], ['circle', '마법진'], ['flower', '꽃'], ['wave', '파도']];
const COS_THEMES = [['', '기본'], ['night', '밤'], ['snow', '눈'], ['sunset', '노을']];
const COS_ITEM_COST = { 'shot:feather': 2, 'shot:orb': 2, 'kill:feather': 2, 'kill:bubble': 2, 'kill:petal': 2, 'base:flower': 2, 'base:wave': 2, 'theme:night': 2, 'theme:snow': 3, 'theme:sunset': 3 };
const COS_PRESETS = 3;   // 저장 칸 수
let stats = readJson(STATS_KEY, null);
if (!stats || typeof stats.byDiff !== 'object' || !stats.byDiff) {   // 처음: 지금까지 남은 기록(최근 판·클리어한 최고 기록)으로 시작
  const wins = hist.filter((r) => r.win), byDiff = {};
  for (const r of wins) byDiff[r.diff] = (byDiff[r.diff] ?? 0) + 1;
  for (const d of E.DIFF_ORDER) {   // 최고 기록이 클리어인 모드 수만큼은 깼다고 봄(최근 판 기록은 30판까지만 남음)
    const modes = Object.keys(E.MODES).filter((m) => bestOf(m, d) > E.RULES.waves).length;
    if (modes > (byDiff[d] ?? 0)) byDiff[d] = modes;
  }
  stats = { byDiff, seen: 0 };
  ls.set(STATS_KEY, JSON.stringify(stats));
}
// 최상위 유닛별 클리어 횟수(2026-11-16 사용자 요청: 첫 화면에서 볼 수 있게). 처음엔 남아 있는 판 기록(최근 30판)으로 채움
const isTopId = (id) => !!E.UNIT[id] && E.UNIT[id].role !== E.SPECIAL_ROLE && E.tierOf(id) >= E.TOP_TIER;
if (!stats.topClears || typeof stats.topClears !== 'object') {
  const tc = {}; for (const r of hist) if (r?.win) for (const [id] of r.comp ?? []) if (isTopId(id)) { (tc[r.diff] ??= {})[id] = (tc[r.diff][id] ?? 0) + 1; }
  stats.topClears = tc; ls.set(STATS_KEY, JSON.stringify(stats));
}
let cosmetic = readJson(COS_KEY, {});
if (!Array.isArray(cosmetic.slots)) cosmetic = { slots: [] };
for (const c of cosmetic.slots) if (c?.wing === 'fairy') { c.wing = ''; if (!c.aura) c.aura = 'wrap'; }   // 예전 요정 날개 → 두름 오라
const clearCount = () => Object.keys(CLEAR_PTS).reduce((a, d) => a + (stats.byDiff[d] ?? 0), 0);   // 화면에 보이는 클리어 횟수
const clearPts = () => Object.entries(CLEAR_PTS).reduce((a, [d, k]) => a + k * (stats.byDiff[d] ?? 0), 0);   // 칸 열기에 쓰는 수
// 꾸미기 포인트(2026-11-10 사용자 요청): 60웨이브 보스 뒤 남은 적까지 0마리로 끝내면(완벽 클리어) 달인 1점·장인 2점(클리어 수와 같은 기준).
// 잠긴 칸 하나 열기 3점, 무지개 색 열기 5점. 번 것(earned)과 쓴 것(spent)을 따로 두어 기기끼리 합칠 때 큰 값으로 합침
const COS_COST = { slot: 3, rainbow: 5 };
const BANK_PER_PT = 50000;   // 모은 새우 → 꾸미기 포인트 1점(2026-11-16)
const pointsLeft = () => Math.max(0, (stats.earned ?? 0) - (stats.spent ?? 0));
const cosOpen = () => Math.min(COS_SLOTS.length, COS_SLOTS.filter((n) => clearPts() >= n).length + (stats.boughtSlots ?? 0));
const rainbowOpen = () => (stats.byDiff?.[RAINBOW_DIFF] ?? 0) > 0 || !!stats.boughtRainbow;
function buyCos(kind) {
  const cost = COS_COST[kind];
  if (kind === 'slot' && cosOpen() >= COS_SLOTS.length) return say('모든 칸이 열려 있어요.');
  if (kind === 'rainbow' && rainbowOpen()) return;
  if (pointsLeft() < cost) return say(`포인트 ${cost}점이 필요해요(지금 ${pointsLeft()}점). 남은 적 0마리로 클리어하면 받아요(달인 1·장인 2점).`);
  stats.spent = (stats.spent ?? 0) + cost;
  if (kind === 'slot') stats.boughtSlots = (stats.boughtSlots ?? 0) + 1; else stats.boughtRainbow = true;
  stats.at = Date.now(); ls.set(STATS_KEY, JSON.stringify(stats)); cosRebuild(); cloudSaveExtra();
  say(kind === 'slot' ? `포인트 ${cost}점으로 꾸미기 칸을 열었어요.` : `포인트 ${cost}점으로 무지개 색을 열었어요.`);
}
// 항목 열림: 값이 없는 것은 바로, 밤 테마는 달인 이상 1번 클리어로도 열림, 나머지는 포인트(stats.bought에 기록, 기기끼리 합칠 때 합집합)
const itemOpen = (key) => !COS_ITEM_COST[key] || !!stats.bought?.[key] || (key === 'theme:night' && clearCount() >= 1);
function buyItem(key, name) {
  const cost = COS_ITEM_COST[key] ?? 0;
  if (itemOpen(key)) return true;
  if (pointsLeft() < cost) { say(`「${name}」은(는) 포인트 ${cost}점으로 열려요(지금 ${pointsLeft()}점). 남은 적 0마리로 클리어하면 받아요.${key === 'theme:night' ? ' 달인 이상을 한 번 클리어해도 열려요.' : ''}`); return false; }
  stats.spent = (stats.spent ?? 0) + cost; stats.bought = { ...(stats.bought ?? {}), [key]: true };
  stats.at = Date.now(); ls.set(STATS_KEY, JSON.stringify(stats)); cloudSaveExtra(); say(`포인트 ${cost}점으로 「${name}」을(를) 열었어요.`);
  return true;
}
const COS_MIN_TIER = 4;   // 전설·히든·초불영제만 꾸밀 수 있음
const cosAllowed = (id) => !!E.UNIT[id] && E.tierOf(id) >= COS_MIN_TIER && E.UNIT[id].role !== E.SPECIAL_ROLE;
cosmetic.slots = cosmetic.slots.map((c) => (c?.id && !cosAllowed(c.id) ? null : c));   // 예전에 고른 낮은 등급 칸은 비움
let cosById = {};
function cosRebuild() {   // 열린 칸의 꾸밈만 유닛별로(칸이 닫혀 있거나 비어 있으면 그리지 않음)
  cosById = {};
  cosmetic.slots.slice(0, cosOpen()).forEach((c) => { if (c?.id && cosAllowed(c.id) && (c.wing || c.aura || c.base || c.shot || c.kill)) cosById[c.id] = c; });
}
cosRebuild();
function presetSave(i) { (cosmetic.presets ??= [])[i] = { slots: JSON.parse(JSON.stringify(cosmetic.slots)), theme: cosmetic.theme ?? '', at: Date.now() }; cosSave(); say(`꾸미기를 ${i + 1}번 저장 칸에 저장했어요.`); }
function presetLoad(i) {
  const p = cosmetic.presets?.[i]; if (!p) return say(`${i + 1}번 저장 칸이 비어 있어요.`);
  cosmetic.slots = (p.slots ?? []).map((c) => (c?.id && cosAllowed(c.id) ? { ...c } : null)); cosmetic.theme = p.theme ?? ''; cosSave(); say(`${i + 1}번 저장 칸의 꾸미기를 불러왔어요.`);
}
function cosSave() { cosmetic.at = Date.now(); ls.set(COS_KEY, JSON.stringify(cosmetic)); cosRebuild(); cloudSaveExtra(); }
// ── 꾸미기·기록·클리어 수·포인트도 계정에(2026-11-10 사용자 요청). 한 덩어리(extra)로 올리고, 받을 때 이 기기 것과 합침 ──
function extraBlob() { return { v: 1, stats, cosmetic, hist, bestComp }; }
let extraTimer = 0;
function cloudSaveExtra(now = false) {   // 바뀌면 2초 뒤에 한 번(여러 번 바꿔도 한 번), now면 바로
  if (!cloud.on) return;
  clearTimeout(extraTimer);
  const go = () => cloudPost({ extra: JSON.stringify(extraBlob()) }, now);
  if (now) go(); else extraTimer = setTimeout(go, 2000);
}
function mergeExtra(x) {   // 계정에 있던 것(x)과 이 기기 것을 합침: 클리어 수·포인트는 큰 값, 기록은 합치기, 꾸미기는 나중에 바꾼 쪽
  if (!x || typeof x !== 'object') return false;
  const before = JSON.stringify(extraBlob());
  const xs = x.stats ?? {}, bd = { ...(stats.byDiff ?? {}) };
  for (const [d, n] of Object.entries(xs.byDiff ?? {})) if (Number(n) > (bd[d] ?? 0)) bd[d] = Number(n);
  const mx = (k) => Math.max(Number(stats[k]) || 0, Number(xs[k]) || 0);
  const tcm = JSON.parse(JSON.stringify(stats.topClears ?? {}));   // 최상위 클리어 횟수: 난이도·유닛마다 큰 값
  for (const [d, o] of Object.entries(xs.topClears ?? {})) for (const [id, n] of Object.entries(o ?? {})) if (isTopId(id) && Number(n) > (tcm[d]?.[id] ?? 0)) (tcm[d] ??= {})[id] = Number(n);
  stats = { ...stats, topClears: tcm, byDiff: bd, earned: mx('earned'), spent: mx('spent'), boughtSlots: mx('boughtSlots'), seen: mx('seen'), bank: mx('bank'), boughtRainbow: !!(stats.boughtRainbow || xs.boughtRainbow), bought: { ...(xs.bought ?? {}), ...(stats.bought ?? {}) } };
  if (x.cosmetic && Array.isArray(x.cosmetic.slots) && (Number(x.cosmetic.at) || 0) > (Number(cosmetic.at) || 0)) cosmetic = { at: x.cosmetic.at, slots: x.cosmetic.slots.map((c) => (c?.id && cosAllowed(c.id) ? c : null)), theme: x.cosmetic.theme ?? '', presets: Array.isArray(x.cosmetic.presets) ? x.cosmetic.presets : [] };
  if (Array.isArray(x.hist)) { const seen = new Set(hist.map((r) => r.at)); hist = [...hist, ...x.hist.filter((r) => r && !seen.has(r.at))].sort((a, b) => b.at - a.at).slice(0, 30); }
  for (const [k, r] of Object.entries(x.bestComp ?? {})) if (r && (!bestComp[k] || r.wave > bestComp[k].wave)) bestComp[k] = r;
  ls.set(STATS_KEY, JSON.stringify(stats)); ls.set(COS_KEY, JSON.stringify(cosmetic)); ls.set(HIST_KEY, JSON.stringify(hist)); ls.set(BESTCOMP_KEY, JSON.stringify(bestComp)); cosRebuild();
  return JSON.stringify(extraBlob()) !== before;
}
function addClear(diff) {
  const before = cosOpen(), rb = rainbowOpen();
  stats.byDiff = { ...stats.byDiff, [diff]: (stats.byDiff?.[diff] ?? 0) + 1 };
  { const tc = { ...(stats.topClears ?? {}) }, d = { ...(tc[diff] ?? {}) }; for (const id of new Set(G.units.map((u) => u.id).filter(isTopId))) d[id] = (d[id] ?? 0) + 1; tc[diff] = d; stats.topClears = tc; }   // 이번 클리어 판의 최상위(종류마다 1번)
  if (G.leftover === 0 && CLEAR_PTS[diff]) { stats.earned = (stats.earned ?? 0) + CLEAR_PTS[diff]; G.pointNews = `완벽 클리어! 꾸미기 포인트 +${CLEAR_PTS[diff]}(지금 ${pointsLeft()}점)`; }
  stats.at = Date.now();
  ls.set(STATS_KEY, JSON.stringify(stats)); cosRebuild();
  // 판 끝 창 안에 한 줄로(알림 띠는 창의 버튼을 가림 — 검토에서 찾음)
  if (!CLEAR_PTS[diff]) G.cosNews = '꾸미기 클리어 횟수는 달인부터 세요';
  else if (cosOpen() > before) G.cosNews = `꾸미기 칸 ${cosOpen()}번째 열림! 시작 화면 「꾸미기」`;
  else if (!rb && rainbowOpen()) G.cosNews = '꾸미기 무지개 색 열림! 시작 화면 「꾸미기」';
}

// ── 입력 ──
let down = null;
let moveCount = 0;   // 끌어서 옮긴 횟수(처음 안내)
function toLogical(ev) { const r = cv.getBoundingClientRect(); return { x: (ev.clientX - r.left) * VW / r.width, y: (ev.clientY - r.top) * VH / r.height }; }
const inField = (p) => p.x >= FIELD.x && p.y >= FIELD.y && p.x < FIELD.x + FIELD.w && p.y < FIELD.y + FIELD.h;
function unitAt(p) {   // 위에 그려진(나중) 것부터
  const list = E.fieldUnits(G);
  for (let i = list.length - 1; i >= 0; i--) { const u = list[i], z = usz(u.id); if (Math.hypot(u.x - p.x, u.y - 14 * z - p.y) <= UNIT_R * z) return u; }
  return null;
}
// 게임 시간이 흐르는가: 강화 창은 열어도 멈추지 않는다(사용자 요청)
const NONPAUSE = new Set(['shop', 'upgrade', 'mission', 'pick', 'book', 'sound', 'storage', 'peer', 'meter', 'recruit', 'signal', 'choice', 'more']);   // 2026-11-21: 증강체(aug)는 멈춤(같이하기만 흐름)
const BOARD_MODAL = new Set(['intro', 'aug']);   // 판 위에 뜨는 창(TFT처럼)   // 일시정지(Ⅱ)·결과 창만 멈춤
const running = () => scene === 'play' && G && !G.over && (!modal || NONPAUSE.has(modal.kind) || (modal.kind === 'aug' && coop()));
const playing = () => scene === 'play' && G && !G.over && (!info || landscape) && (!modal || (landscape && NONPAUSE.has(modal.kind)));
// 스크롤할 목록: 가로 배치는 오른쪽 창, 세로 배치는 열린 창
function scrollerAt(p) {
  if (landscape) return p.x >= SIDE_X ? (modal?.scrollable ? modal : null) : null;
  return modal?.scrollable ? modal : null;
}
// 화면 좌표 → 판 좌표(가로 배치 왼쪽 판은 LS배로 그려짐)
const toBoard = (p) => (landscape ? { x: (p.x - BOARD_X) / LS, y: p.y / LS } : p);
const onBoard = (p) => !landscape || (p.x >= BOARD_X && p.x < BOARD_X + SIDE_X);
cv.addEventListener('pointerdown', (ev) => {
  if (!sfxState.ctx) sfxCtx();   // 첫 누르기에서 소리 준비
  const p = toLogical(ev), bp = toBoard(p);
  cv.setPointerCapture?.(ev.pointerId);
  const top = hitAt(p), dragFn = top?.drag ? top.fn : null;
  if (dragFn) { dragFn(p); down = { x: p.x, y: p.y, x0: p.x, y0: p.y, sy0: p.y, dragFn, moved: false }; return; }
  const field = playing() && onBoard(p) && inField(bp);
  const u = field ? unitAt(bp) : null;
  const sc = scrollerAt(p);
  down = { x: bp.x, y: bp.y, x0: bp.x, y0: bp.y, sy0: p.y, unit: u, field, scroller: sc, scroll0: sc?.scroll ?? 0, moved: false };
});
cv.addEventListener('pointermove', (ev) => {
  if (!down) return;
  const p = toLogical(ev), bp = down.dragFn ? p : toBoard(p);
  if (Math.hypot(bp.x - down.x0, bp.y - down.y0) > 10) down.moved = true;
  down.x = bp.x; down.y = bp.y;
  if (down.dragFn) { down.dragFn(p); return; }
  // 끌기를 실시간으로(2026-11-12 사용자 요청): 끄는 동안 갈매미(고른 무리면 무리째)가 손가락을 바로 따라옴
  if (down.field && down.unit && down.moved && playing()) {
    if (!down.start) { const group = selected.includes(down.unit.uid) && selected.length > 1 ? selUnits() : [down.unit]; down.start = group.filter((u) => !u.lock).map((u) => [u.uid, u.tx, u.ty]); E.holdUnits(G, down.start.map(([uid]) => uid), true); }   // 집어 듦: 공격 멈춤
    const dx = bp.x - down.x0, dy = bp.y - down.y0, mvx = bp.x - (down.lx ?? down.x0); down.lx = bp.x;
    for (const [uid, x, y] of down.start) { E.moveUnit(G, uid, x + dx, y + dy, true); if (Math.abs(mvx) > 1.5) { const u = E.unitById(G, uid); if (u) u.dragDir = mvx > 0 ? 1 : -1; } }   // 끄는 방향 기억(그림만)
    return;
  }
  if (down.scroller && down.moved) down.scroller.scroll = Math.max(0, Math.min(down.scroller.maxScroll ?? 0, down.scroll0 - (p.y - down.sy0) / (wide ? WK : 1)));
});
cv.addEventListener('pointerup', (ev) => {
  if (!down) return;
  const sp = toLogical(ev), d = down;
  down = null;
  if (d.dragFn) { d.dragFn(sp); return; }
  const p = d.field ? toBoard(sp) : sp;
  if (d.field && playing()) {
    if (d.unit && d.moved) {   // 끌어서 옮기기(고른 무리에 있으면 무리째, 고정한 갈매미는 그 자리에)
      const dx = p.x - d.x0, dy = p.y - d.y0;
      let moved = 0;
      for (const [uid, x, y] of d.start ?? []) if (E.moveUnit(G, uid, x + dx, y + dy, true)) moved++;   // 끄는 동안 이미 따라왔음, 마지막 자리로 맞춤
      E.holdUnits(G, (d.start ?? []).map(([uid]) => uid), false);   // 내려놓음: 쿵 주저앉음(잠깐 행동 불능)
      if (!moved) say('고정한 갈매미예요. 「고정 풀기」를 누르면 옮길 수 있어요.'); else moveCount++;
      const sn = (d.start ?? []).map(([uid]) => E.unitById(G, uid)).find((u) => u?.snap);   // 같은 갈매미에 붙음: 고리 + 소리
      if (sn) { G.fx.push({ kind: 'move', x: sn.tx, y: sn.ty, t: G.t }); G.fx.push({ kind: 'move', x: sn.tx, y: sn.ty, t: G.t - 0.15 }); sfx('summon'); }
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
  const h = hitAt(sp);
  if (h) h.fn(sp);
});
cv.addEventListener('pointercancel', () => { if (down?.start && G) E.holdUnits(G, down.start.map(([uid]) => uid), false); down = null; });
cv.addEventListener('wheel', (ev) => { const sc = scrollerAt(toLogical(ev)); if (sc) { sc.scroll = Math.max(0, Math.min(sc.maxScroll ?? 0, sc.scroll + ev.deltaY)); ev.preventDefault(); } }, { passive: false });
window.addEventListener('keydown', (ev) => {
  if (document.activeElement === searchEl) { if (ev.key === 'Escape') searchEl.blur(); return; }   // 검색 입력 중에는 단축키 안 씀
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
  else if (k === 'r') open('recruit');
  else if (k === 'l') { const sel = selUnits(); if (sel.length) { const on = !sel.every((u) => u.lock); E.setLock(G, sel.map((u) => u.uid), on); say(on ? `${sel.length}마리 위치 고정` : `${sel.length}마리 고정 풀기`); } }
});
document.addEventListener('visibilitychange', () => { if (document.hidden && playing()) setPause(true); });

function doSummon(grade) { const r = E.summon(G, grade); if (!r.ok && r.why) say(r.why); else if (r.ok) sfx('summon'); }   // 연달아 누를 수 있게 고르지 않음

// ── 판 ──
function drawBackground() {
  // 배경 그림 없이 단색(사용자 요청 2026-11-08: 뒤 배경이 보여 눈이 아픔)
  ctx.fillStyle = C.bg; ctx.fillRect(0, 0, VW, VH);
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
// 목표(☆) 나무에 든 갈매미(가진 것·없는 것 모두) → 목표 색들. 조합법 줄·재료 칸에 같은 색 동그라미(사용자 요청 2026-11-08)
let goalNeedCache = { t: 0, g: null, map: {} };
function goalNeed() {
  const now = performance.now();
  if (goalNeedCache.g === G && now - goalNeedCache.t < 400) return goalNeedCache.map;
  const map = {};
  G.goals.forEach((gid, k) => {
    const walk = (n) => { if (!n.top) { const a = (map[n.id] ??= []); if (!a.includes(GOAL_COLOR[k])) a.push(GOAL_COLOR[k]); } n.kids.forEach(walk); };
    walk(needTree(gid, E.countOnField(G)));
  });
  goalNeedCache = { t: now, g: G, map };
  return map;
}
function goalDots(cols, x, y, r = 4.5) {   // 목표 색 동그라미(여러 목표면 나란히)
  cols.forEach((c, i) => { ctx.beginPath(); ctx.arc(x - i * (r * 2 + 2), y, r, 0, Math.PI * 2); ctx.fillStyle = c; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = '#07131c'; ctx.stroke(); });
}
// 재료 피라미드: 가진 갈매미(판·창고·탐사)를 쓰고, 없는 재료는 그 재료의 조합법으로 내려가며 펼친다
// 재료 모인 정도(2026-11-15 사용자 요청: 바로 아래 재료만 세면 드라이어드가 40%로 나와 실제보다 낮음).
// 재료 나무(needTree)와 같은 순서로 판의 갈매미를 써 가며, 각 재료를 「기본 재료(조합법 없는 것) 몇 개짜리인가」로 무게를 매겨 셈
const baseCost = new Map();
function costOf(c, depth = 0) {
  if (baseCost.has(c)) return baseCost.get(c);
  const r = depth < 8 ? E.recipeFor(c) : null, v = r ? r.in.reduce((a, x) => a + costOf(x, depth + 1), 0) : 1;
  baseCost.set(c, v); return v;
}
let progCache = { t: 0, g: null, m: new Map() };
function progOf(r, cnt) {   // { got, total } — 기본 재료 기준
  const now = performance.now(), sig = `${G.units.length}|${G.nextUid}`;   // 갈매미가 생기거나 없어지면 바로 다시 셈
  if (progCache.g !== G || progCache.sig !== sig || now - progCache.t > 300) progCache = { t: now, g: G, sig, m: new Map() };
  const hit0 = progCache.m.get(r); if (hit0) return hit0;
  const pool = { ...cnt };
  const node = (c, depth) => {
    if ((pool[c] ?? 0) > 0) { pool[c]--; return costOf(c); }
    const rr = depth < 8 ? E.recipeFor(c) : null;
    return rr ? rr.in.reduce((a, x) => a + node(x, depth + 1), 0) : 0;
  };
  const got = r.in.reduce((a, x) => a + node(x, 1), 0), total = r.in.reduce((a, x) => a + costOf(x), 0);
  const res = { got, total }; progCache.m.set(r, res); return res;
}
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
// 잘 안 바뀌는 그림(판·길·사거리 원)을 한 번 그려 두고 붙이기(2026-11-10 렉 원인: 매 프레임 판 전체 칠하기·점선 원이 비쌌음).
// 지금 배율(가로 화면 확대·기기 배율)마다 따로 저장, key가 바뀌면(갈매미가 움직이면) 새로 그림
const layers = new Map();
function cachedLayer(key, x, y, w, h, draw) {
  const tr = ctx.getTransform(), sc = Math.hypot(tr.a, tr.b), k = `${key}|${sc.toFixed(3)}`;
  let c = layers.get(k);
  if (!c) {
    for (const old of layers.keys()) if (old.startsWith(key.split(':')[0] + ':') && old.endsWith(`|${sc.toFixed(3)}`)) layers.delete(old);   // 같은 종류의 예전 그림은 버림
    c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(w * sc)); c.height = Math.max(1, Math.ceil(h * sc));
    const c2 = c.getContext('2d'); c2.setTransform(sc, 0, 0, sc, -x * sc, -y * sc);
    const keep = ctx; ctx = c2; try { draw(); } finally { ctx = keep; }
    layers.set(k, c); if (layers.size > 16) layers.delete(layers.keys().next().value);
  }
  ctx.drawImage(c, x, y, w, h);
}
// 판 테마(꾸미기 2026-11-11): 바닥·길 색만 바꿈(글자·적·갈매미 색은 그대로라 읽기 쉬움). 눈·밤은 가벼운 날씨
const THEMES = { '': { bg: '#0c1c27', lane: C.lane, edge: C.laneEdge }, night: { bg: '#080f1f', lane: 'rgba(130,150,230,.2)', edge: 'rgba(175,195,255,.38)' },
  snow: { bg: '#15232e', lane: 'rgba(230,242,255,.26)', edge: 'rgba(245,250,255,.5)' }, sunset: { bg: '#24141d', lane: 'rgba(255,170,120,.22)', edge: 'rgba(255,205,150,.42)' } };
const themeOf = () => THEMES[cosmetic.theme] ?? THEMES[''];
function drawLanes() { cachedLayer('lanes:' + (cosmetic.theme ?? ''), FIELD.x - 2, FIELD.y - 2, FIELD.w + 4, FIELD.h + 8, drawLanesRaw); }   // 아래 4는 철제 테두리 그림자
function drawWeather() {   // 판 위 날씨(효과 끔이면 없음)
  const th = cosmetic.theme; if (fxLite || (th !== 'snow' && th !== 'night')) return;
  const t = performance.now() / 1000;
  ctx.save(); ctx.beginPath(); ctx.rect(FIELD.x, FIELD.y, FIELD.w, FIELD.h); ctx.clip();
  for (let k = 0; k < 26; k++) {
    const sx = FIELD.x + ((k * 97) % FIELD.w);
    if (th === 'snow') { const y = FIELD.y + ((t * (18 + (k % 5) * 6) + k * 53) % FIELD.h), x = sx + Math.sin(t * 0.8 + k) * 8; ctx.beginPath(); ctx.arc(x, y, 1.2 + (k % 3) * 0.6, 0, Math.PI * 2); ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.fill(); }
    else { const y = FIELD.y + ((k * 61) % FIELD.h), al = 0.25 + 0.35 * Math.max(0, Math.sin(t * 1.4 + k * 1.7)); ctx.beginPath(); ctx.arc(sx, y, 1.1, 0, Math.PI * 2); ctx.fillStyle = `rgba(220,230,255,${al.toFixed(2)})`; ctx.fill(); }
  }
  ctx.restore();
}
function drawLanesRaw() {
  // 섬과 물가(2026-11-15 사용자 요청: 적 길은 물가, 우리가 선 곳은 섬·땅 위 · 참고 그림 「갈매미맨 항만 방범대」: 돌 부두 섬·푸른 물길·순환 화살표·바위). 판 바깥은 깊은 바다, 길(적이 지나는 고리)은 얕은 물,
  // 안쪽은 한 단 높은 섬(모래 해변 + 풀밭, 아래쪽 앞면으로 흙 절벽이 보임). 2.5D: 위(먼 쪽) 어둡게·아래 밝게. 한 번 그려 두고 붙임(cachedLayer)
  const L = E.LOOP, lane = 40, F = FIELD, CL = 8;   // CL: 섬 절벽 높이
  const ox0 = L.x0 - lane / 2, oy0 = L.y0 - lane / 2, ox1 = L.x1 + lane / 2, oy1 = L.y1 + lane / 2;   // 물길 바깥 가장자리
  const ix0 = L.x0 + lane / 2, iy0 = L.y0 + lane / 2, ix1 = L.x1 - lane / 2, iy1 = L.y1 - lane / 2;   // 섬(물길 안쪽 가장자리)
  ctx.save(); ctx.beginPath(); ctx.rect(F.x, F.y, F.w, F.h); ctx.clip();
  // 깊은 바다(판 전체)
  let g = ctx.createLinearGradient(0, F.y, 0, F.y + F.h); g.addColorStop(0, '#0b2a3a'); g.addColorStop(1, '#114055'); ctx.fillStyle = g; ctx.fillRect(F.x, F.y, F.w, F.h);
  // 얕은 물길(적이 지나는 고리): 바깥 둥근 사각형 − 섬
  rr(ox0, oy0, ox1 - ox0, oy1 - oy0, 18); g = ctx.createLinearGradient(0, oy0, 0, oy1); g.addColorStop(0, '#1d5f8a'); g.addColorStop(1, '#2c86b0'); ctx.fillStyle = g; ctx.fill();
  // 물결 반짝임(짧은 흰 호, 자리 고정)
  let sn = 0; const rnd = () => { sn++; const v = Math.sin(sn * 12.9898) * 43758.5453; return v - Math.floor(v); };   // 장식 자리(늘 같게)
  ctx.lineWidth = 1.2;
  const ripple = (x, y) => { const w = 5 + rnd() * 6; ctx.strokeStyle = `rgba(190,240,250,${(0.12 + rnd() * 0.16).toFixed(2)})`; ctx.beginPath(); ctx.moveTo(x - w, y); ctx.quadraticCurveTo(x, y - 2.5, x + w, y); ctx.stroke(); };
  for (let x = ox0 + 10; x < ox1 - 6; x += 22) { ripple(x + rnd() * 8, L.y0 + (rnd() - 0.5) * 22); ripple(x + rnd() * 8, L.y1 + (rnd() - 0.5) * 22); }
  for (let y = oy0 + 24; y < oy1 - 10; y += 22) { ripple(L.x0 + (rnd() - 0.5) * 22, y + rnd() * 8); ripple(L.x1 + (rnd() - 0.5) * 22, y + rnd() * 8); }
  // 바깥 물가: 모래 띠 + 하얀 거품선(물길 바깥 가장자리)
  ctx.lineWidth = 5; ctx.strokeStyle = 'rgba(120,132,140,.7)'; rr(ox0 - 3, oy0 - 3, ox1 - ox0 + 6, oy1 - oy0 + 6, 20); ctx.stroke();   // 바깥 방파제 돌 띠
  ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(235,250,255,.45)'; ctx.setLineDash([7, 5]); rr(ox0 + 2, oy0 + 2, ox1 - ox0 - 4, oy1 - oy0 - 4, 16); ctx.stroke(); ctx.setLineDash([]);
  // 섬 = 돌 부두(2026-11-15 사용자 참고 그림: 해안 초소·방파제): 절벽 앞면(아래로 CL) → 그림자 → 돌 테두리 → 돌 바닥 타일
  ctx.fillStyle = 'rgba(0,0,0,.35)'; rr(ix0 + 3, iy0 + CL + 4, ix1 - ix0, iy1 - iy0, 10); ctx.fill();   // 물에 비친 그림자
  ctx.fillStyle = '#3c454d'; rr(ix0, iy0 + CL, ix1 - ix0, iy1 - iy0, 10); ctx.fill();   // 돌 옹벽 앞면
  ctx.fillStyle = 'rgba(0,0,0,.25)'; for (let x = ix0 + 14; x < ix1 - 8; x += 22) ctx.fillRect(x, iy1 + 1, 1.5, CL - 1);   // 옹벽 돌 이음
  ctx.fillStyle = '#7d8890'; rr(ix0, iy0, ix1 - ix0, iy1 - iy0, 10); ctx.fill();   // 돌 테두리(연석)
  const gx0 = ix0 + 8, gy0 = iy0 + 8, gx1 = ix1 - 8, gy1 = iy1 - 8, TS = 46;
  g = ctx.createLinearGradient(0, gy0, 0, gy1); g.addColorStop(0, '#4a545c'); g.addColorStop(1, '#5f6a72');   // 돌 바닥(갈매미가 잘 보이게 차분한 회색)
  ctx.fillStyle = g; ctx.fillRect(gx0, gy0, gx1 - gx0, gy1 - gy0);
  ctx.save(); ctx.beginPath(); ctx.rect(gx0, gy0, gx1 - gx0, gy1 - gy0); ctx.clip();
  for (let y = gy0, row = 0; y < gy1; y += TS, row++) for (let x = gx0 - (row % 2 ? TS / 2 : 0); x < gx1; x += TS) {   // 엇갈린 큰 돌판: 위·왼쪽 밝은 모서리, 아래·오른쪽 어두운 줄눈
    const v = rnd();
    ctx.fillStyle = v > 0.66 ? 'rgba(255,255,255,.035)' : v < 0.33 ? 'rgba(0,0,0,.06)' : 'rgba(0,0,0,0)'; ctx.fillRect(x + 1, y + 1, TS - 2, TS - 2);
    ctx.fillStyle = 'rgba(255,255,255,.07)'; ctx.fillRect(x + 1, y + 1, TS - 2, 1.5); ctx.fillRect(x + 1, y + 1, 1.5, TS - 2);
    ctx.fillStyle = 'rgba(10,15,20,.4)'; ctx.fillRect(x, y + TS - 1.5, TS, 1.5); ctx.fillRect(x + TS - 1.5, y, 1.5, TS);
    if (rnd() > 0.8) { ctx.strokeStyle = 'rgba(10,15,20,.25)'; ctx.lineWidth = 1; ctx.beginPath(); const cx = x + 8 + rnd() * (TS - 16), cy = y + 8 + rnd() * (TS - 16); ctx.moveTo(cx, cy); ctx.lineTo(cx + 6, cy + 4); ctx.lineTo(cx + 9, cy + 2); ctx.stroke(); }   // 금
  }
  g = ctx.createLinearGradient(0, gy0, 0, gy0 + 18); g.addColorStop(0, 'rgba(0,0,0,.22)'); g.addColorStop(1, 'rgba(0,0,0,0)'); ctx.fillStyle = g; ctx.fillRect(gx0, gy0, gx1 - gx0, 18);   // 먼 쪽(위) 그늘
  ctx.restore();
  ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(220,230,235,.3)'; rr(ix0 + 0.75, iy0 + 0.75, ix1 - ix0 - 1.5, iy1 - iy0 - 1.5, 10); ctx.stroke();   // 연석 위 빛
  ctx.lineWidth = 1; ctx.strokeStyle = 'rgba(10,15,20,.45)'; ctx.strokeRect(gx0, gy0, gx1 - gx0, gy1 - gy0);
  // ── 방범대 장식(2026-11-15 사용자 요청: 캔버스로, 어색하지 않게). 갈매미를 가리지 않게 낮은 것·바닥 칠만, 오른쪽 커먼 자리(x 445)는 비움 ──
  // 바닥 칠: 흐린 스텐실 글씨
  ctx.save(); ctx.globalAlpha = 0.16; ctx.font = `bold 22px ${FONT}`; ctx.textAlign = 'center'; ctx.fillStyle = '#e8d9a8'; ctx.fillText('제3초소 · 방범 구역', (gx0 + gx1) / 2 - 30, gy1 - 34); ctx.restore();
  // 연석 네 모서리 경고 칠(노랑/검정)
  ctx.save(); rr(ix0, iy0, ix1 - ix0, iy1 - iy0, 10); ctx.clip();
  ctx.fillStyle = stripe('#d4a017', '#1e293b', 5, -1);
  for (const [cx, cy] of [[ix0, iy0], [ix1, iy0], [ix0, iy1], [ix1, iy1]]) { ctx.fillRect(cx - 34, cy - 8, 68, 16); ctx.fillRect(cx - 8, cy - 34, 16, 68); }
  ctx.restore();
  // 계류 말뚝(연석 위, 쇠기둥 + 노란 머리) + 앞쪽 연석엔 늘어진 밧줄
  const bollard = (x, y) => { ctx.beginPath(); ctx.ellipse(x + 1, y + 3, 5, 2, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
    ctx.fillStyle = '#2b3238'; ctx.fillRect(x - 3, y - 7, 6, 9); ctx.beginPath(); ctx.ellipse(x, y - 7, 4, 2, 0, 0, Math.PI * 2); ctx.fillStyle = '#e0b030'; ctx.fill(); };
  const bx = [ix0 + 60, ix0 + 140, ix0 + 220, ix0 + 300], posts = [];
  for (const x of bx) { posts.push([x, iy1 - 3]); bollard(x, iy0 + 4); }
  for (const y of [iy0 + 90, iy0 + 210, iy0 + 330]) bollard(ix0 + 4, y);
  ctx.strokeStyle = 'rgba(200,170,110,.75)'; ctx.lineWidth = 1.4;
  for (let k = 1; k < posts.length; k++) { const [x0, y0] = posts[k - 1], [x1, y1] = posts[k]; ctx.beginPath(); ctx.moveTo(x0, y0 - 6); ctx.quadraticCurveTo((x0 + x1) / 2, y0 + 2, x1, y1 - 6); ctx.stroke(); }
  posts.forEach(([x, y]) => bollard(x, y));
  // 왼쪽 위 구석: 나무 상자 더미 + 작은 신호탑(불빛은 drawWater에서 깜빡)
  const crate = (x, y, w, h) => { ctx.fillStyle = 'rgba(0,0,0,.3)'; ctx.fillRect(x + 2, y + h, w, 3); ctx.fillStyle = '#7a5530'; ctx.fillRect(x, y, w, h); ctx.fillStyle = '#9a6c3c'; ctx.fillRect(x, y, w, 3);
    ctx.strokeStyle = '#4e351c'; ctx.lineWidth = 1; ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1); ctx.beginPath(); ctx.moveTo(x + 2, y + 4); ctx.lineTo(x + w - 2, y + h - 2); ctx.moveTo(x + w - 2, y + 4); ctx.lineTo(x + 2, y + h - 2); ctx.stroke(); };
  crate(gx0 + 30, gy0 + 6, 16, 14); crate(gx0 + 46, gy0 + 9, 14, 12); crate(gx0 + 36, gy0 - 4, 14, 11);
  { const tx = gx0 + 12, ty = gy0 + 24;   // 신호탑: 철골 다리 + 등
    ctx.beginPath(); ctx.ellipse(tx + 2, ty + 2, 8, 3, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
    ctx.strokeStyle = '#3a434a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(tx - 6, ty); ctx.lineTo(tx - 2, ty - 24); ctx.moveTo(tx + 6, ty); ctx.lineTo(tx + 2, ty - 24); ctx.stroke();
    ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(tx - 5, ty - 6); ctx.lineTo(tx + 4, ty - 14); ctx.moveTo(tx + 5, ty - 6); ctx.lineTo(tx - 4, ty - 14); ctx.stroke();
    ctx.fillStyle = '#2b3238'; ctx.fillRect(tx - 5, ty - 30, 10, 6); }
  // 왼쪽 아래 구석: 모래주머니 벽 + 빈 철제 표지판
  for (let r = 0; r < 2; r++) for (let k = 0; k < 4 - r; k++) { const x = gx0 + 8 + k * 11 + r * 5.5, y = gy1 - 14 - r * 6; ctx.beginPath(); ctx.ellipse(x, y, 6.5, 4, 0, 0, Math.PI * 2); ctx.fillStyle = r ? '#a18b5c' : '#8f7a4f'; ctx.fill(); ctx.strokeStyle = 'rgba(60,45,25,.6)'; ctx.lineWidth = 1; ctx.stroke(); }
  { const sx = gx0 + 62, sy = gy1 - 30; ctx.fillStyle = '#2b3238'; ctx.fillRect(sx + 12, sy + 12, 2.5, 14); ctx.fillStyle = 'rgba(0,0,0,.3)'; ctx.fillRect(sx + 2, sy + 26, 26, 3);
    ctx.fillStyle = '#334155'; ctx.fillRect(sx, sy, 28, 14); ctx.fillStyle = '#64748b'; ctx.fillRect(sx, sy, 28, 2); ctx.fillStyle = '#0f172a'; ctx.fillRect(sx, sy + 12, 28, 2);
    ctx.font = `bold 8px ${FONT}`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillStyle = '#f59e0b'; ctx.fillText('제3초소', sx + 14, sy + 7); ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic'; }
  // 물가 바위(바깥 가장자리 몇 곳)
  const rock = (x, y, r) => { ctx.beginPath(); ctx.ellipse(x + 2, y + r * 0.5, r * 1.2, r * 0.45, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.3)'; ctx.fill();
    ctx.beginPath(); ctx.ellipse(x, y, r, r * 0.75, 0, 0, Math.PI * 2); ctx.fillStyle = '#5d676e'; ctx.fill(); ctx.beginPath(); ctx.ellipse(x - r * 0.25, y - r * 0.3, r * 0.55, r * 0.35, 0, 0, Math.PI * 2); ctx.fillStyle = '#8b959b'; ctx.fill();
    ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(235,250,255,.35)'; ctx.beginPath(); ctx.ellipse(x, y + r * 0.35, r * 1.25, r * 0.5, 0, 0.2, Math.PI - 0.2); ctx.stroke(); };
  for (const [x, y, r] of [[ox0 + 6, oy0 + 60, 6], [ox1 - 6, oy0 + 140, 7], [ox0 + 5, oy1 - 120, 6], [ox1 - 7, oy1 - 50, 5], [ox0 + 150, oy0 + 5, 5], [ox1 - 120, oy1 - 4, 6]]) rock(x, y, r);
  // 흐름 표시: 물길 가운데 빛나는 선 + 반시계 방향 화살표(참고 그림의 순환 화살표)
  ctx.save(); ctx.shadowColor = 'rgba(150,235,255,.9)'; ctx.shadowBlur = 8; ctx.lineWidth = 2.2; ctx.strokeStyle = 'rgba(200,245,255,.55)';
  rr(L.x0, L.y0, L.x1 - L.x0, L.y1 - L.y0, 12); ctx.stroke();
  ctx.fillStyle = 'rgba(220,250,255,.8)';
  const arrow = (x, y, ang) => { ctx.save(); ctx.translate(x, y); ctx.rotate(ang); ctx.beginPath(); ctx.moveTo(7, 0); ctx.lineTo(-5, -6); ctx.lineTo(-2, 0); ctx.lineTo(-5, 6); ctx.closePath(); ctx.fill(); ctx.restore(); };
  for (const q of [0.3, 0.7]) { arrow(L.x0, L.y0 + (L.y1 - L.y0) * q, Math.PI / 2); arrow(L.x1, L.y1 - (L.y1 - L.y0) * q, -Math.PI / 2); arrow(L.x0 + (L.x1 - L.x0) * q, L.y1, 0); arrow(L.x1 - (L.x1 - L.x0) * q, L.y0, Math.PI); }   // 왼쪽 ↓ · 아래 → · 오른쪽 ↑ · 위 ←
  ctx.restore();
  // 섬 둘레 물거품(섬과 물이 만나는 곳)
  ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(235,250,255,.35)'; ctx.setLineDash([4, 6]); rr(ix0 - 3, iy0 - 3, ix1 - ix0 + 6, iy1 - iy0 + CL + 6, 12); ctx.stroke(); ctx.setLineDash([]);
  // 판 테마: 위에 색만 살짝 덮음(밤·눈·노을)
  const tint = { night: 'rgba(10,20,60,.38)', snow: 'rgba(230,240,255,.18)', sunset: 'rgba(255,130,70,.14)' }[cosmetic.theme];
  if (tint) { ctx.fillStyle = tint; ctx.fillRect(F.x, F.y, F.w, F.h); }
  // 가장자리 어둡게
  g = ctx.createRadialGradient(F.x + F.w / 2, F.y + F.h * 0.55, F.h * 0.35, F.x + F.w / 2, F.y + F.h * 0.55, F.h * 0.75); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, 'rgba(0,0,0,.25)');
  ctx.fillStyle = g; ctx.fillRect(F.x, F.y, F.w, F.h);
  ctx.restore();
  steelFrame(F.x, F.y, F.w, F.h);   // 항만 방범대: 철제 컨테이너 테두리 + 리벳
  text('▼', L.x0, L.y0 + 2, 16, C.goldEdge, 'center');   // 반시계 방향: 아래로 출발
  // 나타나는 곳·커먼 6곳 점선 고리는 2026-11-15 사용자 요청(「굳이 안 그려도 될 듯」)으로 뺌
}
// 물길 반짝임(매 장면, 가볍게): 물 위를 천천히 흐르는 작은 빛(화려한 효과 끔이면 없음)
function drawWater() {
  { // 신호탑 불빛(1.2초 켜짐·꺼짐, 사이렌과 같은 박자) — 효과 끔이어도 그림(가벼움)
    const L0 = E.LOOP, tx = L0.x0 + 20 + 8 + 12, ty = L0.y0 + 20 + 8 + 24 - 30, on = Math.floor(performance.now() / 600) % 2 === 0;
    ctx.fillStyle = on ? '#ef4444' : '#7f1d1d'; ctx.fillRect(tx - 3, ty - 4, 6, 4);
    if (on && !fxLite) { const g = ctx.createRadialGradient(tx, ty - 2, 0, tx, ty - 2, 14); g.addColorStop(0, 'rgba(239,68,68,.55)'); g.addColorStop(1, 'rgba(239,68,68,0)'); ctx.fillStyle = g; ctx.fillRect(tx - 14, ty - 16, 28, 28); }
  }
  if (fxLite) return;
  { // 부표: 물길 바깥 모서리 넷, 천천히 출렁
    const L1 = E.LOOP, t = performance.now() / 1000, o = 16;
    [[L1.x0 - o, L1.y0 - o], [L1.x1 + o, L1.y0 - o], [L1.x0 - o, L1.y1 + o], [L1.x1 + o, L1.y1 + o]].forEach(([x, y], k) => {
      const b = Math.sin(t * 1.6 + k * 1.3) * 1.5;
      ctx.beginPath(); ctx.ellipse(x, y + 4, 7, 2.5, 0, 0, Math.PI * 2); ctx.strokeStyle = 'rgba(235,250,255,.4)'; ctx.lineWidth = 1; ctx.stroke();
      ctx.fillStyle = '#d63b2f'; ctx.fillRect(x - 4, y - 6 + b, 8, 9); ctx.fillStyle = '#f2efe6'; ctx.fillRect(x - 4, y - 3 + b, 8, 3);
      ctx.fillStyle = '#2b3238'; ctx.fillRect(x - 0.75, y - 11 + b, 1.5, 5); ctx.fillStyle = Math.floor(t * 1.2 + k) % 2 ? '#ffd76a' : '#7a5a14'; ctx.fillRect(x - 1.5, y - 12.5 + b, 3, 2);
    });
  }
  const L = E.LOOP, P = 2 * ((L.x1 - L.x0) + (L.y1 - L.y0)), t = performance.now() / 1000;
  ctx.save(); ctx.lineWidth = 1.4;
  for (let k = 0; k < 14; k++) {
    const p = E.pathPos(((k / 14) * P + t * 18) % P), off = Math.sin(k * 2.3) * 12, a = 0.18 + 0.14 * Math.sin(t * 2 + k);
    const horiz = Math.abs(p.y - L.y0) < 1 || Math.abs(p.y - L.y1) < 1, x = p.x + (horiz ? 0 : off), y = p.y + (horiz ? off : 0);
    ctx.strokeStyle = `rgba(210,250,255,${a.toFixed(2)})`; ctx.beginPath(); ctx.moveTo(x - 5, y); ctx.quadraticCurveTo(x, y - 2.5, x + 5, y); ctx.stroke();
  }
  ctx.restore();
}
// 판 위 갈매미 한 마리(그림자 고리 + 그림). 등급이 높을수록 크게
// 손으로 집기 애니메이션 시트(2026-11-16, 제미나이 그림): 256×256 칸 6개, 발이 칸 위에서 224. 시트가 있는 유닛만(없으면 코드로 흔들기·눌림)
// hold: 「집혀 올려짐」 칸 수(반복) · land: 「내려놓기」 칸 번호(0부터) — 처음(쿵·주저앉음) → 버티는 동안 반복 → 끝(일어남)
const ANIM_SHEETS = {
  u01: { hold: 6, land: { intro: [0, 1], loop: [2], outro: [3, 4, 5] } },   // 버티는 동안 3칸 하나만(사용자 요청), 4칸(고개 흔들기)은 일어나기 앞에
  u45: { hold: 12, land: { intro: [0, 1, 3, 4], loop: [5], outro: [7, 8, 9] } },   // 타이탄(2026-11-16): 버티는 동안 6칸 하나만(사용자 요청)
};
const animImg = (id, kind) => (ANIM_SHEETS[id] ? img(`${ART}/units/${id}-${kind}.webp`) : null);
function drawUnitShape(id, x0, y0, ring, lw, lock = false, ox = 0, oy = 0, zm = 1, flip = false, anim = null) {   // anim: { kind: 'hold'|'land', frame, p }   // flip: 오른쪽을 봄(공격 방향)   // ox·oy·zm: 공격 동작(그림만 움직임, 발밑 고리는 제자리)
  let z = usz(id);
  ctx.beginPath(); ctx.ellipse(x0, y0, 20 * z, 7 * z, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
  ctx.lineWidth = lw; ctx.strokeStyle = ring; ctx.stroke();
  const x = x0 + ox, y = y0 + oy; z *= zm;
  const im = unitImg(id), sheet = anim ? animImg(id, anim.kind) : null, gl = E.tierOf(id) >= 4 && !fxLite && !anim ? glow(id) : null;   // 화려한 효과 끔이면 빛 테두리 없음
  if (flip) { ctx.save(); ctx.translate(x, 0); ctx.scale(-1, 1); ctx.translate(-x, 0); }   // 그림만 좌우로 뒤집음(발밑 고리·자물쇠는 그대로)
  if (anim && sheet) {   // 시트 한 칸(칸 높이 256 중 몸 약 204 → 평소 그림 높이 50·z에 맞춤)
    const S = 50 * z * 256 / 204;
    if (anim.rot) { const py = y - 50 * z; ctx.save(); ctx.translate(x, py); ctx.rotate(anim.rot); ctx.translate(-x, -py); }   // 집힌 머리 위를 축으로 대롱대롱
    ctx.drawImage(sheet, anim.frame * 256, 0, 256, 256, x - S / 2, y - S * 224 / 256, S, S);
    if (anim.rot) ctx.restore();
    if (flip) ctx.restore();
    if (lock) drawLockMark(x0 + 22 * usz(id), y0 - 2);
    return;
  }
  if (anim) {   // 시트 없는 유닛: 들면 좌우로 버둥, 내려놓으면 납작했다 펴짐
    ctx.save(); ctx.translate(x, y);
    if (anim.kind === 'hold') { ctx.translate(0, -50 * z); ctx.rotate(anim.rot ?? 0); ctx.translate(0, 50 * z); }
    else { const q = Math.min(1, (anim.e ?? 0) / 0.35), sx = 1 + 0.18 * (1 - q), sy = 1 - 0.22 * (1 - q); ctx.scale(sx, sy); }
    ctx.translate(-x, -y);
    drawFit(im, x - 24 * z, y - 50 * z, 48 * z, 50 * z);
    ctx.restore();
    if (flip) ctx.restore();
    if (anim.kind === 'land' && anim.frame >= 2 && anim.frame <= 3) for (let i = 0; i < 3; i++) {   // 시트 없는 유닛: 버티는 동안 머리 위에 별이 돎
      const an = performance.now() / 260 + i * 2.1, sx = x + Math.cos(an) * 13 * z, sy = y - 54 * z + Math.sin(an) * 4 * z, r = 3.6 * z;
      ctx.beginPath(); for (let k = 0; k < 10; k++) { const rr = k % 2 ? r * 0.45 : r, a2 = -Math.PI / 2 + k * Math.PI / 5; ctx.lineTo(sx + Math.cos(a2) * rr, sy + Math.sin(a2) * rr); } ctx.closePath(); ctx.fillStyle = '#ffd76a'; ctx.fill();
    }
    if (lock) drawLockMark(x0 + 22 * usz(id), y0 - 2);
    return;
  }
  if (im && gl) {   // 전설 이상: 그림 둘레가 등급 색으로 빛남(천천히 밝아졌다 어두워짐)
    const bw = 48 * z, bh = 50 * z, sc = Math.min(bw / im.naturalWidth, bh / im.naturalHeight), dw = im.naturalWidth * sc, dh = im.naturalHeight * sc;
    const dx = x - bw / 2 + (bw - dw) / 2, dy = y - bh + bh - dh, f = dw / gl.w;
    ctx.globalAlpha = 0.7 + 0.3 * Math.sin(performance.now() / 420 + x * 0.05);
    ctx.drawImage(gl.c, dx - gl.pad * f, dy - gl.pad * f, gl.c.width * f, gl.c.height * f);
    ctx.globalAlpha = 1;
  }
  drawFit(im, x - 24 * z, y - 50 * z, 48 * z, 50 * z);
  if (flip) ctx.restore();
  if (lock) drawLockMark(x0 + 22 * usz(id), y0 - 2);
}
// 내려앉는 칸 고르기(2026-11-16 사용자 요청): 1·2칸(쿵·주저앉음) → 어지러운 3·4칸을 번갈아 반복하며 버팀 → 끝에 5·6칸(일어남). e: 지난 초, T: 전체 초
const LAND_F = 0.12, LAND_DEF = { intro: [0, 1], loop: [2, 3], outro: [4, 5] };
function landFrame(e, T, id) {
  const L = ANIM_SHEETS[id]?.land ?? LAND_DEF, fi = Math.min(LAND_F, 0.36 / L.intro.length), fo = Math.min(LAND_F, 0.32 / L.outro.length);   // 칸이 많으면 한 칸을 짧게(처음 0.36초·끝 0.32초 안)
  if (e < fi * L.intro.length) return L.intro[Math.floor(e / fi)];
  const left = T - e;
  if (left <= fo * L.outro.length) return L.outro[Math.min(L.outro.length - 1, L.outro.length - 1 - Math.floor(left / fo))];
  return L.loop[Math.floor((e - fi * L.intro.length) / 0.16) % L.loop.length];
}
// 내려놓을 때 쿵(2026-11-16 사용자 요청): 납작한 충격 고리 두 겹 + 사방으로 튀었다 가라앉는 먼지. q 0→1
function landFx(x, y, z, q) {
  const a = 1 - q;
  ctx.beginPath(); ctx.ellipse(x, y, (14 + q * 30) * z, (5 + q * 11) * z, 0, 0, Math.PI * 2); ctx.strokeStyle = `rgba(235,225,200,${0.75 * a})`; ctx.lineWidth = 3 * a + 1; ctx.stroke();
  ctx.beginPath(); ctx.ellipse(x, y, (8 + q * 18) * z, (3 + q * 6) * z, 0, 0, Math.PI * 2); ctx.strokeStyle = `rgba(255,255,255,${0.5 * a})`; ctx.lineWidth = 1.5; ctx.stroke();
  for (let i = 0; i < 8; i++) {
    const an = (i / 8) * Math.PI * 2 + 0.3, d = (10 + q * 26) * z, h = Math.sin(q * Math.PI) * 9 * z * (0.6 + (i % 3) * 0.2);
    const px = x + Math.cos(an) * d, py = y + Math.sin(an) * d * 0.38 - h, r = (6 - q * 3) * z * (0.8 + (i % 2) * 0.4);
    ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.fillStyle = `rgba(196,182,156,${0.95 * a})`; ctx.fill();   // 먼지(회색 바닥에서도 보이게 모래빛)
    ctx.beginPath(); ctx.arc(px - r * 0.3, py - r * 0.3, r * 0.55, 0, Math.PI * 2); ctx.fillStyle = `rgba(250,244,230,${0.85 * a})`; ctx.fill();   // 먼지 밝은 쪽
  }
  if (q < 0.3) { ctx.beginPath(); ctx.ellipse(x, y - 4 * z, 22 * z, 9 * z, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(255,250,235,${0.45 * (1 - q / 0.3)})`; ctx.fill(); }   // 닿는 순간 섬광
}
// 적이 쓰러질 때(2026-11-16): 맞은 반대쪽으로 튕겨 오르며 돌다가 작아지고, 바닥에 연기 고리·조각. q 0→1
function drawEnemyDeath(f, q) {
  const sh = enemyImg(f.look), sz = f.boss ? 84 : 44, dir = f.sx != null && f.sx > f.x ? -1 : 1, a = 1 - q;
  if (q < 0.3) { const g2 = ctx.createRadialGradient(f.x, f.y - sz * 0.35, 0, f.x, f.y - sz * 0.35, sz * (0.5 + q * 1.5)); g2.addColorStop(0, `rgba(255,250,230,${0.9 * (1 - q / 0.3)})`); g2.addColorStop(1, 'rgba(255,220,150,0)'); ctx.fillStyle = g2; ctx.beginPath(); ctx.arc(f.x, f.y - sz * 0.35, sz * (0.5 + q * 1.5), 0, Math.PI * 2); ctx.fill(); }   // 섬광 폭발
  if (sh) {
    const x = f.x + dir * q * sz * 0.8, y = f.y - Math.sin(q * Math.PI) * sz * 0.75, s2 = 1 - q * 0.6;
    ctx.save(); ctx.globalAlpha = Math.min(1, a * 1.4); ctx.translate(x, y - sz * 0.3); ctx.rotate(dir * q * 2.6); ctx.scale(s2, s2);
    drawFit(sh, -sz / 2, -sz * 0.45, sz, sz);
    ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.6 * a; drawFit(sh, -sz / 2, -sz * 0.45, sz, sz);
    ctx.restore();
  }
  ctx.beginPath(); ctx.ellipse(f.x, f.y, sz * (0.25 + q * 0.7), sz * (0.1 + q * 0.25), 0, 0, Math.PI * 2); ctx.strokeStyle = `rgba(240,230,205,${0.85 * a})`; ctx.lineWidth = 4 * a + 0.5; ctx.stroke();   // 바닥 충격 고리
  for (let i = 0; i < 10; i++) {   // 튀는 조각(어두운 테두리로 바닥에서도 보이게)
    const an = i * 0.628 + f.x * 0.01, d = sz * (0.15 + q * (0.6 + (i % 3) * 0.15)), hgt = Math.sin(q * Math.PI) * (10 + (i % 4) * 5) * (f.boss ? 1.6 : 1), r = (4.5 - q * 2.8) * (f.boss ? 1.6 : 1);
    const px = f.x + Math.cos(an) * d, py = f.y + Math.sin(an) * d * 0.4 - hgt;
    ctx.beginPath(); ctx.arc(px, py, r + 1.2, 0, Math.PI * 2); ctx.fillStyle = `rgba(70,50,30,${0.6 * a})`; ctx.fill();
    ctx.beginPath(); ctx.arc(px, py, r, 0, Math.PI * 2); ctx.fillStyle = i % 2 ? `rgba(255,225,150,${a})` : `rgba(230,215,190,${a})`; ctx.fill();
  }
}
// 꾸밈 그리기: 날개·오라 아래쪽은 갈매미 뒤(cosBack), 반짝이·불티는 앞(cosFront). x·y는 발밑, z는 크기
const rainbowHue = (seed) => (performance.now() / 14 + seed * 37) % 360;
function hueRGB(deg) { const hue = deg / 60, f = (n) => { const k = (n + hue) % 6; return Math.round(255 * (1 - 0.62 * Math.max(0, Math.min(k, 4 - k, 1)))); }; return [f(5), f(3), f(1)]; }
function cosRGB(c, seed = 0) {   // [r,g,b] — 무지개는 시간에 따라 색이 돎
  if (c.color !== 'rainbow') { const h = parseInt((c.color ?? '#ffd66b').slice(1), 16); return [(h >> 16) & 255, (h >> 8) & 255, h & 255]; }
  return hueRGB(rainbowHue(seed));
}
// 두름 오라: 갈매미 그림 둘레 빛(그 색으로 한 번만 만들어 둠, 무지개는 12가지 색을 만들어 섞어 그림)
const wraps = {};
function wrapGlow(id, col) {
  const key = `${id}|${col}`;
  if (wraps[key]) return wraps[key];
  const i = unitImg(id);
  if (!i || !i.naturalWidth) return null;
  // 크기를 줄여 둠(빛은 원래 번져 있어 작아도 티가 안 남 — 2026-11-10 렉 줄이기: 128 → 88)
  const pad = 24, w = 88, h = Math.round(88 * i.naturalHeight / i.naturalWidth);
  const c = document.createElement('canvas'); c.width = w + pad * 2; c.height = h + pad * 2;
  const x = c.getContext('2d');
  // 그 색으로 칠한 꽉 찬 몸 모양 + 번진 빛(갈매미 뒤에 그리므로 몸 부분은 그림에 가려지고 둘레만 보임 — 위로 늘려도 구멍이 안 비침)
  const m = document.createElement('canvas'); m.width = w; m.height = h;
  const mx = m.getContext('2d'); mx.drawImage(i, 0, 0, w, h); mx.globalCompositeOperation = 'source-in'; mx.fillStyle = `rgb(${col})`; mx.fillRect(0, 0, w, h);
  x.shadowColor = `rgb(${col})`; x.shadowBlur = 12;
  for (let k = 0; k < 5; k++) x.drawImage(m, pad, pad);   // 겹쳐 그려 진하게(몸에 붙은 빛을 한 장으로 — 매 프레임 두 번 그리던 것을 한 번으로)
  return (wraps[key] = { c, pad, w });
}
function drawWrap(id, c, x, y, z, seed, p) {
  const im = unitImg(id); if (!im || !im.naturalWidth) return;
  const bw = 48 * z, bh = 50 * z, sc = Math.min(bw / im.naturalWidth, bh / im.naturalHeight), dw = im.naturalWidth * sc, dh = im.naturalHeight * sc;
  const dx = x - dw / 2, dy = y - dh, cx = x, cy = dy + dh / 2;
  const layers = [];   // [그림, 진하기]
  if (c.color === 'rainbow') layers.push([wrapGlow(id, hueRGB(Math.floor(rainbowHue(seed) / 22.5) % 16 * 22.5).join(',')), 1]);   // 무지개: 16가지 색 중 가까운 것(섞어 그리면 두 배로 무거움)
  else layers.push([wrapGlow(id, cosRGB(c).join(',')), 1]);
  const t = performance.now() / 1000, by = dy + dh;   // 발밑 기준으로 위로 늘여 타오르게
  // [가로 배율, 세로 배율, 가로 흔들림, 위로 뜸, 진하기]: 몸에 붙은 진한 빛 둘 + 위로 일렁이며 올라가는 겉불 셋(효과 줄이기면 겉불 없음)
  const parts = [[1, 1, 0, 0, 0.7 + 0.3 * p]];
  if (!fxLite) for (let k = 0; k < 2; k++) {   // 겉불 둘(셋 → 둘)
    const w = Math.sin(t * (7 + k * 1.7) + k * 2.1 + seed), v = Math.sin(t * (11 + k * 2.3) + k * 1.3 + seed * 0.7);
    parts.push([1.06 + 0.05 * k + 0.02 * w, 1.1 + 0.1 * k + 0.06 * v, w * (1.2 + k * 0.6) * z, (2 + 3 * k + 2 * v) * z, (0.5 - 0.12 * k) * (0.8 + 0.2 * v)]);
  }
  ctx.save(); ctx.globalCompositeOperation = 'lighter';
  for (const [g, a] of layers) {
    if (!g || a <= 0) continue;
    const f = dw / g.w, gw = g.c.width * f, gh = g.c.height * f, gx = dx - g.pad * f, gy = dy - g.pad * f;
    for (const [sx, sy, ox, up, al] of parts) {
      const X = cx + (gx - cx) * sx + ox, Y = by + (gy - by) * sy - up;
      ctx.globalAlpha = Math.max(0, al) * a; ctx.drawImage(g.c, X, Y, gw * sx, gh * sy);
    }
  }
  ctx.restore();
}
const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;
const light = (c, k) => c.map((v) => Math.round(v + (255 - v) * k));
function cosWing(style, col, cx, cy, s, side, flap) {
  ctx.save(); ctx.translate(cx, cy); ctx.scale(side * s, s); ctx.rotate(-flap);
  const lt = light(col, 0.55);
  {   // 깃털: 위로 휜 날개, 아래 가장자리는 깃털 끝 물결
    const g = ctx.createLinearGradient(0, 0, 46, -30); g.addColorStop(0, rgba(lt, 0.97)); g.addColorStop(1, rgba(col, 0.6));
    const tip = [[48, -34], [45, -21], [39, -11], [31, -3], [21, 3], [10, 5], [0, 2]];
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.bezierCurveTo(6, -24, 26, -40, 48, -34);
    for (let k = 1; k < tip.length; k++) { const [ax, ay] = tip[k - 1], [bx, by] = tip[k]; ctx.quadraticCurveTo((ax + bx) / 2 + 5, (ay + by) / 2 + 5, bx, by); }
    ctx.closePath(); ctx.fillStyle = g; ctx.fill(); ctx.lineWidth = 1.4 / s; ctx.strokeStyle = rgba(light(col, 0.85), 0.95); ctx.stroke();
    ctx.beginPath(); for (let k = 1; k < tip.length - 1; k++) { ctx.moveTo(tip[k][0] * 0.35, tip[k][1] * 0.35 - 4); ctx.lineTo(tip[k][0] * 0.9, tip[k][1] * 0.9); }
    ctx.lineWidth = 1 / s; ctx.strokeStyle = rgba(col, 0.55); ctx.stroke();
  }
  ctx.restore();
}
function cosBase(c, col, x, y, z, t) {   // 발밑 무늬(꾸미기 2026-11-11): 마법진·꽃·파도
  ctx.save(); ctx.translate(x, y); ctx.scale(1, 0.38); ctx.lineWidth = 2 / 0.6;
  if (c.base === 'circle') {
    const R = 30 * z, a = t * 0.8; ctx.strokeStyle = rgba(col, 0.75);
    ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.stroke(); ctx.beginPath(); ctx.arc(0, 0, R * 0.72, 0, Math.PI * 2); ctx.stroke();
    ctx.beginPath(); for (let k = 0; k < 6; k++) { const an = a + k * Math.PI / 3, b = a + (k + 2) * Math.PI / 3; ctx.moveTo(Math.cos(an) * R * 0.72, Math.sin(an) * R * 0.72); ctx.lineTo(Math.cos(b) * R * 0.72, Math.sin(b) * R * 0.72); } ctx.stroke();
  } else if (c.base === 'flower') {
    const R = 26 * z; ctx.fillStyle = rgba(light(col, 0.3), 0.45); ctx.strokeStyle = rgba(col, 0.8);
    for (let k = 0; k < 6; k++) { const an = t * 0.4 + k * Math.PI / 3; ctx.beginPath(); ctx.ellipse(Math.cos(an) * R * 0.62, Math.sin(an) * R * 0.62, R * 0.42, R * 0.2, an, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); }
  } else if (c.base === 'wave') {
    for (let k = 0; k < 3; k++) { const q = (t * 0.6 + k / 3) % 1; ctx.beginPath(); ctx.arc(0, 0, (14 + 26 * q) * z, 0, Math.PI * 2); ctx.strokeStyle = rgba(col, 0.8 * (1 - q)); ctx.stroke(); }
  }
  ctx.restore();
}
function cosBack(c, x, y, z, seed = 0) {
  const t = performance.now() / 1000, col = cosRGB(c, seed), still = fxLite;
  if (c.base) cosBase(c, col, x, y, z, still ? 0 : t);
  if (c.aura) {   // 발밑 빛 웅덩이(숨 쉬듯 커졌다 작아짐)
    const p = still ? 0.5 : 0.5 + 0.5 * Math.sin(t * 2.4 + seed);
    const r = (26 + 6 * p) * z, g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, rgba(col, c.aura === 'flame' ? 0.6 : c.aura === 'wrap' ? 0.4 : 0.5)); g.addColorStop(1, rgba(col, 0));
    ctx.save(); ctx.translate(x, y); ctx.scale(1, 0.38); ctx.translate(-x, -y);
    ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fillStyle = g; ctx.fill(); ctx.restore();
    if (c.aura === 'wrap') drawWrap(c.id, c, x, y, z, seed, p);
    else if (c.aura === 'glow') {   // 몸을 감싸는 옅은 빛 기둥
      const g2 = ctx.createLinearGradient(0, y, 0, y - 62 * z); g2.addColorStop(0, rgba(col, 0.28 + 0.12 * p)); g2.addColorStop(1, rgba(col, 0));
      ctx.fillStyle = g2; ctx.beginPath(); ctx.ellipse(x, y - 28 * z, 24 * z, 36 * z, 0, 0, Math.PI * 2); ctx.fill();
    } else {   // 불꽃: 몸 뒤로 너울거리는 혀 셋
      for (let k = 0; k < 3; k++) {
        const ph = t * 3.2 + k * 2.1 + seed, hgt = (40 + 12 * Math.sin(ph)) * z, bx = x + (k - 1) * 13 * z, sw = 4 * z * Math.sin(ph * 1.3);
        const g3 = ctx.createLinearGradient(0, y, 0, y - hgt); g3.addColorStop(0, rgba(light(col, 0.3), 0.55)); g3.addColorStop(1, rgba(col, 0));
        ctx.beginPath(); ctx.moveTo(bx - 10 * z, y - 2 * z); ctx.quadraticCurveTo(bx - 9 * z + sw, y - hgt * 0.6, bx + sw * 1.5, y - hgt); ctx.quadraticCurveTo(bx + 9 * z + sw, y - hgt * 0.6, bx + 10 * z, y - 2 * z);
        ctx.fillStyle = g3; ctx.fill();
      }
    }
  }
  if (c.wing) {   // 등 뒤 날개 한 쌍(천천히 퍼덕임)
    const flap = still ? 0.05 : 0.12 * Math.sin(t * 3.6 + seed), s = 0.62 * z, cy = y - 34 * z;
    cosWing(c.wing, col, x - 7 * z, cy, s, -1, flap); cosWing(c.wing, col, x + 7 * z, cy, s, 1, flap);
  }
}
function cosFront(c, x, y, z, seed = 0) {
  if (!c.aura || fxLite) return;
  const t = performance.now() / 1000, col = cosRGB(c, seed);
  if (c.aura === 'glow') for (let k = 0; k < 4; k++) {   // 몸 둘레를 도는 반짝이
    const a = t * 1.6 + k * Math.PI / 2 + seed, sx = x + Math.cos(a) * 24 * z, sy = y - 26 * z + Math.sin(a) * 9 * z + Math.sin(t * 3 + k) * 6 * z;
    const r = (1.8 + 1.2 * Math.sin(t * 5 + k)) * z;
    ctx.beginPath(); ctx.moveTo(sx, sy - r * 2.4); ctx.lineTo(sx + r * 0.7, sy - r * 0.7); ctx.lineTo(sx + r * 2.4, sy); ctx.lineTo(sx + r * 0.7, sy + r * 0.7); ctx.lineTo(sx, sy + r * 2.4); ctx.lineTo(sx - r * 0.7, sy + r * 0.7); ctx.lineTo(sx - r * 2.4, sy); ctx.lineTo(sx - r * 0.7, sy - r * 0.7); ctx.closePath();
    ctx.fillStyle = rgba(light(col, 0.6), 0.95); ctx.fill();
  } else if (c.aura === 'wrap') {   // 두름: 몸 둘레를 타고 오르는 빛 알갱이
    ctx.save(); ctx.globalCompositeOperation = 'lighter';
    for (let k = 0; k < 6; k++) {
      const ph = (t * 0.6 + k / 6 + seed * 0.07) % 1, side = k % 2 ? 1 : -1, sx = x + side * (12 + 9 * Math.sin(ph * Math.PI)) * z, sy = y - 4 * z - ph * 54 * z, a = Math.sin(ph * Math.PI);
      // 그러데이션을 매번 만들지 않고 동그라미 둘(옅은 바깥 + 밝은 속)
      ctx.fillStyle = rgba(col, 0.25 * a); ctx.beginPath(); ctx.arc(sx, sy, 4.5 * z, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = rgba(light(col, 0.7), 0.9 * a); ctx.beginPath(); ctx.arc(sx, sy, 1.8 * z, 0, Math.PI * 2); ctx.fill();
    }
    ctx.restore();
  } else for (let k = 0; k < 4; k++) {   // 불꽃: 위로 날리는 불티
    const ph = (t * 0.9 + k * 0.25 + seed * 0.13) % 1, sx = x + (((k * 37 + Math.floor(t * 0.9 + k * 0.25)) % 5) - 2) * 7 * z, sy = y - 8 * z - ph * 56 * z;
    ctx.beginPath(); ctx.arc(sx, sy, (2.4 - ph * 1.4) * z, 0, Math.PI * 2); ctx.fillStyle = rgba(light(col, 0.5), 0.9 * (1 - ph)); ctx.fill();
  }
}
const glows = {};
function glow(id) {   // 빛 테두리 그림을 한 번만 만들어 둠
  if (glows[id]) return glows[id];
  const i = unitImg(id);
  if (!i) return null;
  const t = E.tierOf(id), pad = 32, w = 192, h = Math.round(192 * i.naturalHeight / i.naturalWidth);   // 빛 테두리도 2배 해상도
  const c = document.createElement('canvas'); c.width = w + pad * 2; c.height = h + pad * 2;
  const x = c.getContext('2d');
  x.imageSmoothingQuality = 'high';
  x.shadowColor = GRADE_COLOR[E.UNIT[id].grade]; x.shadowBlur = (9 + (t - 4) * 1.5) * 2;
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
// 화면 흔들림(상위 등급이 보스를 칠 때 아주 작게)
const shake = { until: 0, mag: 0 };
function drawField() {
  // 2026-11-10 버그 수정(사용자: 한 번 클리어하고 나면 판이 안 보임): 흔들림 끝 시각이 지난 판의 판 시간(G.t) 그대로 남아,
  // 새 판(G.t가 0부터)에서 「남은 시간 ÷ 0.12」배로 판을 수천 px 밀어냈음 → 0.2초보다 길게 남았으면 지난 판 값이므로 지우고, 거리도 mag 이하로
  if (shake.until - G.t > 0.2) shake.until = 0;
  const sh = !fxLite && G.t < shake.until;
  if (sh) { ctx.save(); const r = Math.min(shake.mag, shake.mag * (shake.until - G.t) / 0.12); ctx.translate((Math.random() - 0.5) * 2 * r, (Math.random() - 0.5) * 2 * r); }
  drawFieldInner();
  if (sh) ctx.restore();
}
function drawFieldInner() {
  drawLanes(); drawWater(); drawWeather();
  // 고른 갈매미 사거리(한 마리일 때)
  const sel = selUnits();
  if (sel.length === 1) {
    const k = E.kit(sel[0].id);
    ctx.beginPath(); ctx.arc(sel[0].x, sel[0].y - 14, k.range, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(243,216,158,.07)'; ctx.fill(); ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(243,216,158,.55)'; ctx.stroke();
  }
  // 이감·마방깍 갈매미 범위(범위 안 적 모두에게 걸림, 옅게)
  if (!fxLite) {   // 갈매미가 움직일 때만 새로 그림(점선 원 수십 개를 매 프레임 그리던 것)
    const au = E.fieldUnits(G).filter((u) => { const k = E.kit(u.id); return k.slow || k.mshred || k.haste || k.might || k.weaken; });
    if (au.length) cachedLayer('aura:' + au.map((u) => `${u.id}@${Math.round(u.x)},${Math.round(u.y)}`).join(';'), FIELD.x, FIELD.y, FIELD.w, FIELD.h, () => {
      ctx.save(); ctx.beginPath(); ctx.rect(FIELD.x, FIELD.y, FIELD.w, FIELD.h); ctx.clip();
      for (const u of au) {
        const k = E.kit(u.id);
        if (k.slow || k.mshred) { ctx.beginPath(); ctx.arc(u.x, u.y - 8, k.range, 0, Math.PI * 2);
          ctx.setLineDash([4, 7]); ctx.lineWidth = 1.2; ctx.strokeStyle = k.slow ? 'rgba(159,232,255,.16)' : 'rgba(143,200,255,.13)'; ctx.stroke(); ctx.setLineDash([]); }
        // 주변 버프(초록: 공격력·공격속도+)·패널티(빨강: 공격력−) 범위(2026-11-11)
        if (k.haste || k.might) { ctx.beginPath(); ctx.arc(u.x, u.y - 8, E.BUFF_R, 0, Math.PI * 2); ctx.fillStyle = 'rgba(111,224,138,.05)'; ctx.fill(); ctx.setLineDash([3, 5]); ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(111,224,138,.5)'; ctx.stroke(); ctx.setLineDash([]); }
        if (k.weaken) { ctx.beginPath(); ctx.arc(u.x, u.y - 8, E.WEAKEN_R, 0, Math.PI * 2); ctx.fillStyle = 'rgba(255,110,96,.05)'; ctx.fill(); ctx.setLineDash([3, 5]); ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,110,96,.55)'; ctx.stroke(); ctx.setLineDash([]); }
      }
      ctx.restore();
    });
  }
  // 적
  for (const e of G.enemies) {
    const sz = e.boss ? 84 : 44 * Math.pow(0.8, e.gen ?? 0), sh = enemyImg(e.look);   // 분열한 적은 작게
    // 맞는 표현(2026-11-16 사용자 요청: 더 액션감 있게 → 「약하다」로 한 번 더 세게): 때린 쪽 반대로 크게 튕겼다 돌아옴(등급 높을수록 멀리, 보스는 조금)
    // + 처음 0.05초 멈칫 흔들림 + 크게 납작해졌다 펴짐 + 진한 번쩍임 + 테두리 있는 금빛 타격 불꽃·충격 고리
    const ha = G.t - (e.hitT ?? -9), HT = 0.22, hq = !fxLite && ha < HT ? 1 - ha / HT : 0, bm = e.boss ? 0.4 : 1;
    let kx = 0, ky = 0;
    if (hq > 0) { const dx = e.x - (e.hsx ?? e.x - 1), dy = e.y - (e.hsy ?? e.y), dl = Math.hypot(dx, dy) || 1, pw = (6 + (e.hitK ?? 0) * 1.6) * bm * hq * hq; kx = dx / dl * pw; ky = dy / dl * pw * 0.6;
      if (ha < 0.05) { kx += (((e.id * 7 + Math.floor(ha * 400)) % 3) - 1) * 2.2; ky += (((e.id * 3 + Math.floor(ha * 400)) % 3) - 1) * 1.2; } }
    const sqx = 1 + 0.28 * hq * bm, sqy = 1 - 0.22 * hq * bm;
    if (sh) { if (hq > 0) { ctx.save(); ctx.translate(e.x + kx, e.y + ky); ctx.scale(sqx, sqy); ctx.translate(-(e.x + kx), -(e.y + ky)); }
      drawFit(sh, e.x - sz / 2 + kx, e.y - sz * 0.75 + ky, sz, sz);
      if (hq > 0.4) { ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = Math.min(1, 1.6 * (hq - 0.4) / 0.6); drawFit(sh, e.x - sz / 2 + kx, e.y - sz * 0.75 + ky, sz, sz); drawFit(sh, e.x - sz / 2 + kx, e.y - sz * 0.75 + ky, sz, sz); ctx.restore(); }   // 하얗게 번쩍(두 겹, 처음 0.13초)
      if (hq > 0) ctx.restore(); }
    if (hq > 0.3) { const cx = e.x + kx * 0.4, cy = e.y - sz * 0.35, q2 = (hq - 0.3) / 0.7, r0 = sz * 0.1, r1 = sz * (0.22 + 0.4 * (1 - q2)), sc2 = e.boss ? 1.4 : 1;
      ctx.beginPath(); ctx.ellipse(cx, cy + sz * 0.1, sz * (0.25 + 0.4 * (1 - q2)), sz * (0.1 + 0.16 * (1 - q2)), 0, 0, Math.PI * 2); ctx.strokeStyle = `rgba(255,236,170,${0.8 * q2})`; ctx.lineWidth = 3 * q2 + 1; ctx.stroke();   // 충격 고리
      ctx.lineCap = 'round';
      for (let pass = 0; pass < 2; pass++) {   // 타격 불꽃: 어두운 테두리 → 금빛 줄기(밝은 그림 위에서도 보이게)
        ctx.strokeStyle = pass ? `rgba(255,${200 + Math.round(40 * q2)},90,${q2})` : `rgba(60,30,10,${0.7 * q2})`; ctx.lineWidth = (pass ? 3 : 5.5) * sc2;
        for (let i = 0; i < 6; i++) { const an = i * 1.0472 + e.id * 0.7 + (e.hitT ?? 0) * 13, rr1 = r1 * (i % 2 ? 0.7 : 1); ctx.beginPath(); ctx.moveTo(cx + Math.cos(an) * r0, cy + Math.sin(an) * r0); ctx.lineTo(cx + Math.cos(an) * rr1, cy + Math.sin(an) * rr1); ctx.stroke(); }
      }
      ctx.beginPath(); ctx.arc(cx, cy, sz * 0.12 * q2 * sc2, 0, Math.PI * 2); ctx.fillStyle = `rgba(255,255,240,${q2})`; ctx.fill();   // 가운데 섬광
    }
    if (e.boss && G.t < (e.shieldUntil ?? 0)) { const pu = 0.6 + 0.4 * Math.sin(G.t * 12); ctx.beginPath(); ctx.ellipse(e.x, e.y - sz * 0.35, sz * 0.62, sz * 0.58, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(120,220,255,${0.18 * pu})`; ctx.fill(); ctx.strokeStyle = `rgba(160,235,255,${0.85 * pu})`; ctx.lineWidth = 3; ctx.stroke(); }   // 보스 방어막
    if (e.boss && G.t < (e.dashUntil ?? 0)) { const P2 = E.pathPos(Math.max(0, e.s - 30)), dx = e.x - P2.x, dy = e.y - P2.y, dl = Math.hypot(dx, dy) || 1; ctx.strokeStyle = 'rgba(255,120,90,.7)'; ctx.lineWidth = 3; for (let k = -1; k <= 1; k++) { const ox2 = -dy / dl * k * 14, oy2 = dx / dl * k * 14; ctx.beginPath(); ctx.moveTo(e.x - dx / dl * 20 + ox2, e.y - sz * 0.3 - dy / dl * 20 + oy2); ctx.lineTo(e.x - dx / dl * 60 + ox2, e.y - sz * 0.3 - dy / dl * 60 + oy2); ctx.stroke(); } }   // 보스 돌진 꼬리
    if (!fxLite && G.t < (e.poisonUntil ?? 0)) for (let b = 0; b < 2; b++) { const ph = ((G.t * 1.6 + b * 0.5 + e.id * 0.13) % 1); ctx.beginPath(); ctx.arc(e.x - 8 + b * 14, e.y - sz * 0.3 - ph * 22, 2.5 + b, 0, Math.PI * 2); ctx.fillStyle = `rgba(120,220,110,${0.7 * (1 - ph)})`; ctx.fill(); }
    if (e.slow > 0) { ctx.beginPath(); ctx.arc(e.x, e.y + 2, sz / 3, 0, Math.PI * 2); ctx.strokeStyle = '#9fe8ff'; ctx.lineWidth = 1.5 + 2.5 * e.slow; ctx.stroke(); }   // 이속감소가 많이 쌓일수록 굵게
  }
  // 갈매미(아래쪽부터 그려 겹침이 자연스럽게)
  const gc = goalColors(), selSet = new Set(selected), synAll = E.RULES.synergy ? E.synergyOf(G) : null;
  const list = [...E.fieldUnits(G)].sort((a, b) => a.y - b.y);
  const lastShot = new Map();   // 갈매미마다 가장 최근 공격(동작용)
  for (const f of G.fx) if (f.kind === 'shot' && f.uid) { const q = lastShot.get(f.uid); if (!q || q.t < f.t) lastShot.set(f.uid, f); }
  for (const u of list) {
    const info = E.UNIT[u.id], col = GRADE_COLOR[info.grade], z = usz(u.id);
    let ox = 0, oy = 0, zm = 1;
    const f = !fxLite && lastShot.get(u.uid), a = f ? G.t - f.t : 9;
    if (f && a < 0.18) {   // 근접: 적 쪽으로 돌진했다 돌아옴 · 원거리: 살짝 뒤로 반동 · 둘 다 잠깐 커짐
      const dx = f.x1 - f.x0, dy = f.y1 - f.y0, d = Math.hypot(dx, dy) || 1, w = Math.sin(Math.PI * a / 0.18);
      const m = f.near ? Math.min(14, d * 0.25) : -3;
      ox = dx / d * m * w; oy = dy / d * m * w; zm = 1 + 0.07 * w;
    }
    // 시너지 장판(2026-11-21 사용자 요청: 더 간단하게 — 바닥에 기호): 시너지가 켜진 속성 갈매미 발밑에 그 속성 색 마법진. 1단계 오각, 2단계 이상 팔각, 3단계는 바깥 고리
    if (synAll && E.tierOf(u.id) >= 3 && info.role !== E.SPECIAL_ROLE) {
      const el = E.kit(u.id).elem, sv = synAll[el];
      if (sv?.lv > 0) { const rgb = ELEM_COL[el] ?? '220,220,220', rt = fxLite ? 0 : G.t * 0.6, sz = 100 * z, pl = fxLite ? 1 : 0.85 + 0.15 * Math.sin(G.t * 3 + u.uid), nm = sv.lv >= 2 ? 'magic_02' : 'magic_01';
        ctx.beginPath(); ctx.ellipse(u.x, u.y + 2, sz * 0.42, sz * 0.42 * FLAT, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(${rgb},.18)`; ctx.fill();   // 바닥 물들임
        fxSprite(nm, u.x, u.y + 2, sz, rgb, pl, rt, FLAT, false); fxSprite(nm, u.x, u.y + 2, sz, rgb, 0.6 * pl, rt, FLAT);   // 또렷하게 + 빛
        if (sv.lv >= 3) fxSprite('circle_02', u.x, u.y + 2, sz * 1.15, rgb, 0.8 * pl, 0, FLAT); }
    }
    const cos = fxLite ? null : cosById[u.id];   // 화려한 효과 끔이면 꾸밈도 숨김(가볍게)
    if (cos) cosBack(cos, u.x + ox, u.y + oy, z * zm, u.uid);
    // 공격 방향 보기(2026-11-15 사용자 요청): 기본은 왼쪽, 오른쪽 적을 치면 오른쪽을 봤다가 공격이 끊기면 돌아옴(공격 기록이 0.8초 남으므로 0.8초 뒤)
    const lf = lastShot.get(u.uid), face = !!lf && G.t - lf.t < 0.8 && lf.x1 > lf.x0 + 4;
    // 손으로 집기(2026-11-16): 들고 있으면 떠서 파닥임(반복), 내려놓으면 쿵 주저앉는 동작(한 번)
    const LT = E.RULES.landTime ?? 0.8, landP = u.landUntil > G.t ? 1 - (u.landUntil - G.t) / LT : -1;
    // 2026-11-16 사용자 의견(나는 것 같음): 빠르게 버둥(45ms) + 집힌 머리 위를 축으로 흔들림·잔떨림, 끄는 방향을 봄
    const tn = performance.now(), sw = Math.sin(tn / 130 + u.uid) * 0.16 + Math.sin(tn / 23 + u.uid * 3) * 0.035;
    const anim = u.held ? { kind: 'hold', frame: Math.floor(tn / 45) % (ANIM_SHEETS[u.id]?.hold ?? 6), p: 0, rot: sw } : landP >= 0 ? { kind: 'land', frame: landFrame(landP * LT, LT, u.id), p: landP, e: landP * LT } : null;
    if (u.held) { oy -= 9 * z; ox = 0; zm = 1; }
    const dragFace = u.held || landP >= 0 ? false : null;   // 2026-11-16 사용자 요청: 들고 있을 때(파닥임)·내려앉을 때는 늘 왼쪽을 봄
    if (landP >= 0 && landP * LT < 0.35 && !fxLite) landFx(u.x, u.y, z, landP * LT / 0.35);   // 쿵: 바닥 충격 고리·먼지(앞 0.35초)
    drawUnitShape(u.id, u.x, u.y, selSet.has(u.uid) ? '#ffffff' : col, selSet.has(u.uid) ? 3 : 2, u.lock, ox, oy, zm, dragFace ?? (face && !anim), anim);
    if (cos) cosFront(cos, u.x + ox, u.y + oy, z * zm, u.uid);
    // 이름표(2026-11-15 사용자 요청): 상위(제한~영원)는 늘, 그 아래 등급은 웨이브가 지나며 차차 흐려짐
    // 커먼 5→10웨이브 · 언커먼 10→15 · 레어 15→20 · 에픽 20→25 · 전설·히든 25→30웨이브에 사라짐(배울 때만 도움, 후반엔 판이 깔끔하게)
    { const t = E.tierOf(u.id), top = t >= E.TOP_TIER, end = [10, 15, 20, 25, 30, 30][t] ?? 30, na = top ? 1 : Math.max(0, Math.min(1, (end - G.wave) / 5));
      if (na > 0) { const fs = top ? 13 : 11, ny = u.y + 8 * z + (top ? 9 : 8); ctx.font = `${fs}px ${FONT}`; const nw = ctx.measureText(info.name).width + 10;
        ctx.globalAlpha = na; box(u.x - nw / 2, ny - fs * 0.7, nw, fs * 1.4, 'rgba(7,19,28,.72)', null, 7); text(info.name, u.x, ny, fs, col, 'center'); ctx.globalAlpha = 1; } }
    // 시너지 표시(2026-11-21 사용자 요청): 시너지에 세는 갈매미(에픽 이상)에 속성 배지. 켜진 속성은 색을 채우고 단계 점, 안 켜진 속성은 흐린 테두리만
    (gc[u.id] ?? []).forEach((c, k) => { ctx.beginPath(); ctx.ellipse(u.x, u.y, (24 + k * 4) * z, (9 + k * 2) * z, 0, 0, Math.PI * 2); ctx.strokeStyle = c; ctx.lineWidth = 2.5; ctx.stroke(); });
    if (G.t - u.born < 0.5) { ctx.globalAlpha = 1 - (G.t - u.born) / 0.5; ctx.beginPath(); ctx.arc(u.x, u.y - 20 * z, 30 * z, 0, Math.PI * 2); ctx.fillStyle = 'rgba(243,216,158,.35)'; ctx.fill(); ctx.globalAlpha = 1; }
  }
  // 적 체력 칸·▼·★: 갈매미보다 위에 그림(2026-11-10 사용자 지적: 윗줄 적 체력 칸이 가려짐). 늘 적 위쪽에 두고(사용자: 아래로 가면 헷갈림),
  // 위쪽 정보 줄에 가릴 만큼 올라가면 판 맨 위에 붙임
  for (const e of G.enemies) {
    const sz = e.boss ? 84 : 44, bw = e.boss ? 70 : 32, bx = e.x - bw / 2;
    let by = e.y - sz * 0.75 - 6;
    const below = by < FIELD.y - 2;
    if (below) by = FIELD.y - 2;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, e.hp / e.max), 5, e.boss ? '#ff6b6b' : '#6fd08a', null, 2);
    if (!fxLite) {   // 방깍(주황)·마방깍(파랑)이 걸려 있으면 체력 칸 옆에 ▼
      const on = (m) => m && Object.values(m).some((q) => G.t < q.until);
      const mx = e.x + bw / 2 + 3, my = by + 2;
      if (on(e.shreds)) text('▼', mx, my, 9, C.phys, 'left');
      if (on(e.mshreds)) text('▼', mx + 9, my, 9, C.mag, 'left');
    }
    if (G.t < e.stunUntil) text('★', e.x, below ? by + 14 : by - 8, 12, '#ffe14a', 'center');
  }
  // 겹친 갈매미 수(같은 자리 14px 안)
  const stacks = [];
  for (const u of list) { const z = usz(u.id), st = stacks.find((q) => Math.abs(q.x - u.x) < 14 && Math.abs(q.y - u.y) < 14); if (st) { st.n++; st.x = u.x; st.y = u.y; st.z = Math.max(st.z, z); } else stacks.push({ x: u.x, y: u.y, n: 1, z }); }
  for (const st of stacks) if (st.n > 1) { const by = st.y - 50 * st.z - 4; box(st.x + 10 * st.z, by, 30, 20, 'rgba(7,19,28,.9)', C.goldEdge, 8, 1.5); text(`×${st.n}`, st.x + 10 * st.z + 15, by + 10.5, 13, C.goldEdge, 'center'); }
  // 끌기 표시
  if (down?.moved && down.field) {
    if (down.unit) {
      // 끌기 실시간(2026-11-12): 갈매미가 직접 따라오므로 반투명 미리 보기는 그리지 않음
    } else {
      box(Math.min(down.x0, down.x), Math.min(down.y0, down.y), Math.abs(down.x - down.x0), Math.abs(down.y - down.y0), 'rgba(243,216,158,.08)', C.goldEdge, 2, 1.5);
    }
  }
  // 장판(초불영제 고유 기술): 남은 시간 동안 바닥에 깔림
  for (const z of G.zones ?? []) {
    const left = z.until - G.t, a = Math.min(1, left / 0.4), col = ELEM_COL[z.elem] ?? '200,220,255', pulse = fxLite ? 0 : Math.sin(G.t * 6) * 3;
    ctx.beginPath(); ctx.ellipse(z.x, z.y - 6, z.r + pulse, (z.r + pulse) * 0.62, 0, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(${col},${0.16 * a})`; ctx.fill(); ctx.lineWidth = 2; ctx.strokeStyle = `rgba(${col},${0.6 * a})`; ctx.stroke();
  }
  // 효과
  sigLabels = []; cosUid = new Map();
  if (!fxLite) for (const u of G.units) { const c = cosById[u.id]; if (c && (c.shot || c.kill)) cosUid.set(u.uid, c); }
  for (const f of G.fx) {
    const age = G.t - f.t;
    if (f.kind === 'shot') {
      if (fxLite) { if (age < 0.14) {   // 줄인 효과: 예전 직선
        ctx.globalAlpha = 1 - age / 0.14; ctx.strokeStyle = f.type === 'phys' ? C.phys : C.mag; ctx.lineWidth = f.job === '보조' ? 1.5 : 2.5;
        ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 24); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke(); ctx.globalAlpha = 1; } }
      else { drawShot(f, age); if (f.near && f.type === 'phys' && age < 0.15) fxSprite('slash_02', f.x1, f.y1 - 14, f.tier >= 6 ? 70 : 50, '255,226,180', 1 - age / 0.15, Math.atan2(f.y1 - f.y0, f.x1 - f.x0) - Math.PI / 2); }   // 근접 물리: 베기 자국
    } else if (f.kind === 'num' && age < 0.75) {   // 2026-11-21: 화려한 효과를 꺼도 숫자는 보임(글자만이라 가벼움)   // 보스 피해 숫자(전설 이상)
      // 2026-11-21: 모든 레어 이상 공격의 피해 숫자, 치명타는 크고 붉게 「치명!」, 보스는 체력 칸 위·일반 적은 머리 위
      const a = 1 - age / 0.75, big = f.tier >= 6, cr = !!f.crit, pop = age < 0.08 ? 1 + (0.08 - age) * 6 : 1;
      ctx.globalAlpha = Math.min(1, a * 1.6);
      const sq = f.n ?? 0, nx = Math.min(W - 30, Math.max(30, f.x + ((sq * 23) % 40) - 20)), ny = f.y - (f.boss ? 92 : 50) - (sq % 3) * 10 - age * 40;
      const fs = Math.round((cr ? 21 : big ? 18 : 15) * pop), col = cr ? '#ff6b4a' : big ? '#ffd76a' : '#fff1d6';
      ctx.font = `${fs}px ${FONT}`; ctx.lineWidth = 3; ctx.lineJoin = 'round'; ctx.strokeStyle = 'rgba(7,19,28,.9)'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.strokeText(cr ? `${fmtN(f.v)}!` : fmtN(f.v), nx, ny);   // 테두리(읽기 쉽게, 2026-11-21: 13→15px)
      text(cr ? `${fmtN(f.v)}!` : fmtN(f.v), nx, ny, fs, col, 'center');
      if (cr && age < 0.3) text('치명', nx, ny - fs * 0.85, 11, '#ffd0c4', 'center');
      if (cr && age < 0.25 && !fxLite) fxSprite('star_07', f.x, f.y - (f.boss ? 40 : 22), 60 + age * 160, '255,140,90', 1 - age / 0.25, age * 3);   // 치명타 섬광
      ctx.globalAlpha = 1;
    } else if (f.kind === 'boom' && age < 0.28) {   // 범위 공격(채운 원)·스플래시(고리)
      const a = 1 - age / 0.28, rr2 = f.r * (0.55 + 0.45 * (1 - a));
      const col = f.elem === 'FIRE' ? '255,140,70' : f.type === 'phys' ? '255,179,107' : '143,200,255';
      ctx.beginPath(); ctx.ellipse(f.x, f.y, rr2, rr2 * FLAT, 0, 0, Math.PI * 2);   // 2.5D: 바닥에 납작하게
      ctx.fillStyle = `rgba(${col},${(f.area ? 0.26 : 0.14) * a})`; ctx.fill();
      ctx.lineWidth = f.area ? 2.5 : 2; ctx.strokeStyle = `rgba(${col},${0.85 * a})`; ctx.stroke();
      if (!fxLite && f.area) fxSprite('scorch_02', f.x, f.y, rr2 * 2.3, col, 0.7 * a, f.x, FLAT);   // 범위 공격: 폭발 자국
    } else if (f.kind === 'bomb' && age < 0.8) {   // 갈매미맨 폭격: 판 전체 섬광 + 적마다 폭발 고리
      const a = 1 - age / 0.8;
      if (age < 0.25) { ctx.fillStyle = `rgba(255,236,190,${0.55 * (1 - age / 0.25)})`; ctx.fillRect(FIELD.x, FIELD.y, FIELD.w, FIELD.h); }
      for (const e of G.enemies) { const r = (12 + age * 70) * (e.boss ? 1.6 : 1); ctx.beginPath(); ctx.arc(e.x, e.y - 14, r, 0, Math.PI * 2); ctx.fillStyle = `rgba(255,150,60,${0.35 * a})`; ctx.fill(); ctx.strokeStyle = `rgba(255,230,160,${0.9 * a})`; ctx.lineWidth = 3; ctx.stroke(); }
      if (!fxLite) { let q = 0; for (const e of G.enemies) { if (q++ > 30) break; fxSprite('smoke_10', e.x, e.y - 14, (40 + age * 110) * (e.boss ? 1.6 : 1), '255,170,90', 0.8 * a, q); if (age < 0.3) fxSprite('scorch_02', e.x, e.y - 14, 70 * (e.boss ? 1.6 : 1), '255,230,160', 1 - age / 0.3, q * 2); } }
      if (age < 0.5) { ctx.globalAlpha = 1 - age / 0.5; text('갈매미맨 폭격!', W / 2, FIELD.y + 120 - age * 40, 34, '#ffd76a', 'center'); ctx.globalAlpha = 1; }
    } else if (f.kind === 'summon' && age < 0.6) {   // 보스가 부하를 부름: 보라 고리
      const a = 1 - age / 0.6; groundRing(f.x, f.y, 20 + age * 60, '186,120,255', 0.9 * a, 3, 0.2);
    } else if (f.kind === 'kill' && age < 0.6) {   // 쓰러짐(모든 적) + 꾸민 갈매미가 쓰러뜨리면 처치 효과
      if (!fxLite && age < 0.42) drawEnemyDeath(f, age / 0.42);
      if (!fxLite && age < 0.45) fxSprite('smoke_05', f.x, f.y - (f.boss ? 30 : 14), (f.boss ? 90 : 46) + age * 80, f.boss ? '255,200,140' : '230,236,240', 0.45 * (1 - age / 0.45), f.x, 1, false);   // 연기 한 줌
      const cc = cosUid.get(f.uid); if (cc?.kill) drawKillFx(cc, f, age);
    } else if (f.kind === 'sig' && age < 0.75) {
      drawSig(f, age);
    } else if (f.kind === 'zap' && age < 0.2) {
      if (fxLite) { ctx.strokeStyle = '#ffe14a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 10); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke(); }
      else {   // 번개 연쇄: 번개 줄기 그림을 두 점 사이로 늘려 그림
        const dx = f.x1 - f.x0, dy = f.y1 - f.y0, d = Math.hypot(dx, dy) || 1, c = fxImg('spark_05', '255,232,120');
        if (c) { ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 1 - age / 0.2; ctx.translate((f.x0 + f.x1) / 2, (f.y0 + f.y1) / 2 - 10); ctx.rotate(Math.atan2(dy, dx) + Math.PI / 2); ctx.drawImage(c, -22, -d / 2 - 8, 44, d + 16); ctx.restore(); }
      }
    } else if (f.kind === 'combine' || f.kind === 'move') {
      const a = Math.max(0, 1 - age / 0.6); ctx.globalAlpha = a;
      const rm = (f.kind === 'move' ? 8 : 20) + (1 - a) * 30; ctx.beginPath(); if (f.kind === 'move') ctx.ellipse(f.x, f.y, rm, rm * FLAT, 0, 0, Math.PI * 2); else ctx.arc(f.x, f.y - 20, rm, 0, Math.PI * 2);   // 옮긴 자리는 바닥 타원
      ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 2.5; ctx.stroke(); ctx.globalAlpha = 1;
      if (f.kind === 'combine' && !fxLite && age < 0.6) { fxSprite('circle_03', f.x, f.y - 20, 40 + age * 180, '255,215,130', 1 - age / 0.6); fxSprite('light_01', f.x, f.y - 20, 90, '255,235,180', 0.8 * (1 - age / 0.6)); }   // 조합: 빛 고리
    } else if (f.kind === 'wisp') {
      const a = Math.max(0, 1 - age / 0.8);
      if (!fxLite && a > 0) { const gc2 = hexRGB(GRADE_COLOR[f.grade]); fxSprite('twirl_01', f.x, f.y - 20 - (1 - a) * 30, 54 + age * 30, gc2, a, -age * 9); fxSprite('star_06', f.x, f.y - 20 - (1 - a) * 30, 40, gc2, a * 0.8); }   // 위습: 소용돌이 + 별빛
      ctx.globalAlpha = a;
      text('✦', f.x, f.y - 20 - (1 - a) * 30, 22, GRADE_COLOR[f.grade], 'center'); ctx.globalAlpha = 1;
    }
  }
  drawPfx();   // 화려한 효과(도트 층)
  drawTalk();   // 전설 이상 갈매미 대사(말풍선)
}

// ── 공격 연출(2026-11-09 사용자 요청: 공격 모션이 단조로움) ──
// 속성마다 다른 투사체가 날아가고(근접은 베기), 닿으면 불꽃 파편. 전설 이상은 크게, 제한 이상은 보스를 칠 때 화면이 살짝 흔들림
// 초불영제 고유 기술 연출(2026-11-11): 기술 틀마다 모양이 다름. 나간 갈매미 머리 위에 기술 이름
function drawKillFx(cc, f, age) {   // 별가루·깃털·물방울·꽃잎(0.6초)
  const col = cosRGB(cc, f.uid), q = age / 0.6, a = 1 - q, x = f.x, y = f.y - 16;
  ctx.save(); ctx.globalAlpha = a;
  for (let k = 0; k < 6; k++) {
    const an = k * Math.PI / 3 + (f.uid % 7) * 0.3, d = 6 + 30 * q;
    if (cc.kill === 'sparkle') cosShot('star', col, x + Math.cos(an) * d, y + Math.sin(an) * d, 0, 3.4 * (1 - q * 0.5), age * 8);
    else if (cc.kill === 'feather') { if (k < 4) cosShot('feather', light(col, 0.4), x + Math.cos(an) * d * 0.7 + Math.sin(age * 9 + k) * 5, y - 8 + 26 * q, an, 3.6, 0); }
    else if (cc.kill === 'bubble') { if (k < 5) { ctx.beginPath(); ctx.arc(x + (k - 2) * 7 + Math.sin(age * 10 + k) * 3, y - 34 * q * (0.6 + k * 0.1), 2.5 + k % 3, 0, Math.PI * 2); ctx.strokeStyle = rgba(light(col, 0.5), 0.95); ctx.lineWidth = 1.5; ctx.stroke(); } }
    else { ctx.save(); ctx.translate(x + Math.cos(an) * d, y + Math.sin(an) * d + 10 * q); ctx.rotate(an + age * 6); ctx.beginPath(); ctx.ellipse(0, 0, 4.5, 2.2, 0, 0, Math.PI * 2); ctx.fillStyle = rgba(light(col, 0.35), 0.95); ctx.fill(); ctx.restore(); }
  }
  ctx.restore();
}
let sigLabels = [];   // 이번 화면에 쓴 기술 이름 자리(겹침 확인)
function drawSig(f, age) {
  const col = ELEM_COL[f.elem] ?? (f.type === 'phys' ? '255,179,107' : '143,200,255'), a = Math.max(0, 1 - age / 0.45);
  ctx.save(); ctx.lineCap = 'round';
  if (a > 0) {
    ctx.strokeStyle = `rgba(${col},${0.95 * a})`; ctx.fillStyle = `rgba(${col},${0.22 * a})`;
    if (f.sk === 'pierce') { ctx.lineWidth = 6 * a + 2; ctx.strokeStyle = `rgba(${col},${0.3 * a})`; ctx.beginPath(); ctx.moveTo(f.x0, f.y0); ctx.lineTo(f.x1, f.y1); ctx.stroke(); ctx.strokeStyle = `rgba(${col},${0.95 * a})`;   // 바닥에 그은 자국
      ctx.lineWidth = 12 * a + 3; ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 24); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke(); ctx.strokeStyle = `rgba(255,255,255,${0.8 * a})`; ctx.lineWidth = 3; ctx.stroke(); }
    else if (f.sk === 'bounce' || f.sk === 'storm') {
      for (const [x, y] of f.pts ?? []) { if (f.sk === 'storm') { ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(x - 6, y - 120); ctx.lineTo(x + 6, y - 60); ctx.lineTo(x - 4, y - 50); ctx.lineTo(x, y - 8); ctx.stroke(); } if (f.sk === 'storm' && f.r) { ctx.save(); ctx.beginPath(); ctx.rect(FIELD.x, FIELD.y, FIELD.w, FIELD.h); ctx.clip(); const rr = f.r * (0.55 + 0.45 * (1 - a)); groundRing(x, y, rr, col, 0.95 * a, 3, 0.22); groundRing(x, y, rr * 0.55, '255,255,255', 0.5 * a, 1.5); ctx.restore(); } else groundRing(x, y, 16 * (1.4 - a), col, 0.9 * a, 2.5, 0.3); }   // 맞은 자리 바닥(범위 낙뢰는 실제 반경)
      if (f.sk === 'bounce' && f.pts?.length > 1) { ctx.lineWidth = 3; ctx.beginPath(); f.pts.forEach(([x, y], i) => (i ? ctx.lineTo(x, y - 12) : ctx.moveTo(x, y - 12))); ctx.stroke(); }
    } else if (f.sk === 'nova' || f.sk === 'bind' || f.sk === 'field') {
      // 2.5D: 바닥에 퍼지는 납작한 원(기술 범위 너비) + 안쪽 고리가 뒤따라옴
      const R0 = f.r ?? 80, rr = R0 * (0.5 + 0.5 * (1 - a)), cy = f.y1;
      groundRing(f.x1, cy, rr, col, 0.95 * a, f.sk === 'bind' ? 5 : 3, 0.24);
      if (f.sk === 'nova') groundRing(f.x1, cy, rr * 0.6, col, 0.6 * a, 2);
      if (f.sk === 'bind') for (let i = 0; i < 6; i++) { const an = i * Math.PI / 3 + age * 3; ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(f.x1 + Math.cos(an) * rr, cy + Math.sin(an) * rr * FLAT); ctx.lineTo(f.x1 + Math.cos(an) * rr * 0.35, cy + Math.sin(an) * rr * 0.35 * FLAT); ctx.stroke(); }
    } else {   // smash·execute: 대상에 큰 별·베기
      const R = 26 + 18 * (1 - a); ctx.lineWidth = 5;
      if (f.r) { groundRing(f.x1, f.y1, f.r * (0.6 + 0.4 * (1 - a)), col, 0.85 * a, 2.5, 0.18); ctx.strokeStyle = `rgba(${col},${0.95 * a})`; ctx.fillStyle = `rgba(${col},${0.22 * a})`; ctx.lineWidth = 5; }   // 실제로 피해가 들어가는 범위
      if (f.sk === 'execute') { ctx.beginPath(); ctx.moveTo(f.x1 - R, f.y1 - 10 - R); ctx.lineTo(f.x1 + R, f.y1 - 10 + R); ctx.moveTo(f.x1 + R, f.y1 - 10 - R); ctx.lineTo(f.x1 - R, f.y1 - 10 + R); ctx.stroke(); }
      else { ctx.beginPath(); for (let i = 0; i < 8; i++) { const an = i * Math.PI / 4, rr = i % 2 ? R * 0.45 : R; ctx.lineTo(f.x1 + Math.cos(an) * rr, f.y1 + Math.sin(an) * rr * FLAT); } ctx.closePath(); ctx.fill(); ctx.stroke(); }   // 내려친 자리 바닥에 갈라진 별
    }
  }
  if (!fxLite) {   // 기술 이름(0.75초). 검토(2026-11-11): 옆 갈매미 이름과 겹쳐 → 겹치면 위로 비켜 쓰고, 그래도 겹치면 건너뜀
    ctx.font = `14px ${FONT}`; const tw = ctx.measureText(f.name).width + 8, base = f.y0 - 76 - age * 16;
    for (let k = 0; k < 3; k++) {
      const ty = base - k * 18, r = { x: f.x0 - tw / 2, y: ty - 9, w: tw, h: 18 };
      if (sigLabels.some((q) => r.x < q.x + q.w && q.x < r.x + r.w && r.y < q.y + q.h && q.y < r.y + r.h)) continue;
      sigLabels.push(r); ctx.globalAlpha = Math.min(1, (0.75 - age) / 0.25);
      text(f.name, f.x0 + 1.5, ty + 1.5, 14, 'rgba(7,19,28,.95)', 'center'); text(f.name, f.x0, ty, 14, '#ffe9a8', 'center');
      break;
    }
  }
  ctx.restore(); ctx.globalAlpha = 1;
}
const ELEM_COL = { FIRE: '255,140,70', WATER: '90,170,255', ICE: '190,240,255', LIGHTNING: '255,225,74', NATURE: '120,220,110', HOLY: '255,240,190', DARK: '186,120,255', NORMAL: '255,200,140' };
let cosUid = new Map();   // 이번 화면: 갈매미 번호 → 꾸밈(공격 연출·처치 효과용)
function cosShot(st, col, x, y, ang, r, spin) {   // 꾸민 투사체 모양
  ctx.save(); ctx.translate(x, y); ctx.fillStyle = rgba(col, 0.95); ctx.strokeStyle = rgba(light(col, 0.7), 0.95); ctx.lineWidth = 1.2;
  if (st === 'star') { ctx.rotate(spin); ctx.beginPath(); for (let i = 0; i < 10; i++) { const a = i * Math.PI / 5 - Math.PI / 2, q = i % 2 ? r * 0.45 : r; ctx.lineTo(Math.cos(a) * q, Math.sin(a) * q); } ctx.closePath(); ctx.fill(); ctx.stroke(); }
  else if (st === 'heart') { ctx.rotate(ang + Math.PI / 2); ctx.beginPath(); ctx.moveTo(0, r * 0.8); ctx.bezierCurveTo(-r * 1.4, -r * 0.2, -r * 0.5, -r * 1.2, 0, -r * 0.4); ctx.bezierCurveTo(r * 0.5, -r * 1.2, r * 1.4, -r * 0.2, 0, r * 0.8); ctx.fill(); ctx.stroke(); }
  else if (st === 'feather') { ctx.rotate(ang); ctx.beginPath(); ctx.ellipse(0, 0, r * 1.4, r * 0.5, 0, 0, Math.PI * 2); ctx.fill(); ctx.beginPath(); ctx.moveTo(-r * 1.6, 0); ctx.lineTo(r * 1.4, 0); ctx.stroke(); }
  else { ctx.beginPath(); ctx.arc(0, 0, r * 1.7, 0, Math.PI * 2); ctx.fillStyle = rgba(col, 0.25); ctx.fill(); ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fillStyle = rgba(light(col, 0.5), 0.98); ctx.fill(); }
  ctx.restore();
}
function drawShot(f, age) {
  const sx = f.x0, sy = f.y0 - 24, tx = f.x1, ty = f.y1 - 10, dx = tx - sx, dy = ty - sy, dist = Math.hypot(dx, dy) || 1;
  const cc = cosUid.get(f.uid);
  if (cc?.shot) {   // 꾸민 공격 연출: 원래 투사체 대신(근접은 대상 앞에서 터짐)
    const T = f.near ? 0.12 : Math.max(0.1, Math.min(0.24, dist / 900)), col = cosRGB(cc, f.uid);
    if (age < T) {
      const p = f.near ? 1 : age / T, r = (f.tier >= 6 ? 6.5 : 5.2);
      for (let i = 2; i >= 1; i--) { const q = Math.max(0, p - i * 0.14); if (!f.near) { ctx.globalAlpha = 0.35; cosShot(cc.shot, col, sx + dx * q, sy + dy * q, Math.atan2(dy, dx), r * 0.7, age * 14); } }
      ctx.globalAlpha = f.near ? 1 - age / T : 1; cosShot(cc.shot, col, sx + dx * p, sy + dy * p, Math.atan2(dy, dx), r * (f.near ? 1.4 : 1), age * 14); ctx.globalAlpha = 1;
    }
    return;
  }
  // 기본 공격 연출 2판(2026-11-15 사용자 요청: 기본 공격도 시각 효과가 필요). 속성마다 날아가는 꼬리와 맞을 때 반응이 다름.
  // 2.5D: 바닥 점(발밑)을 따라가며 높이를 더함(물리 화살은 높게, 마법은 낮게 포물선), 맞으면 파편이 높이를 갖고 튀었다 떨어짐. 많이 쏘므로 캔버스로 가볍게 그림
  // 2026-11-16 사용자 요청: 하위도 효과를 쓰되 등급이 오를수록 화려하게 — 단계 lv 0 커먼·언커먼 · 1 레어 · 2 에픽 · 3 전설·히든 · 4 최상위
  const lv = f.tier >= 6 ? 4 : f.tier >= 4 ? 3 : f.tier >= 3 ? 2 : f.tier >= 2 ? 1 : 0, big = lv >= 3;
  const sc = (f.job === '보조' ? 0.75 : 1) * [1.6, 1.72, 1.85, 2.0, 2.2][lv], col = ELEM_COL[f.elem] ?? (f.type === 'phys' ? '255,179,107' : '143,200,255');
  const T = f.near ? 0.07 : Math.max(0.08, Math.min(0.22, dist / 950));   // 날아가는 시간
  const seed = (f.t * 1000 + f.x0) % 97, rnd = (i) => ((Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453) % 1 + 1) % 1;   // 공격마다 같은 난수(화면마다 같게)
  const gd = Math.hypot(f.x1 - f.x0, f.y1 - f.y0), arcH = f.type === 'phys' ? Math.min(46, gd * 0.28) : Math.min(24, gd * 0.14);
  const at = (q) => { const gx = f.x0 + (f.x1 - f.x0) * q, gy = f.y0 + (f.y1 - f.y0) * q; return [gx, gy - (24 + (10 - 24) * q) - arcH * 4 * q * (1 - q), gx, gy]; };
  const ang = Math.atan2(dy, dx), el = f.near && f.type === 'phys' ? 'SLASH' : f.elem === 'NORMAL' || !f.elem ? (f.type === 'phys' ? 'ARROW' : 'ORB') : f.elem;
  const dot = (x, y, r, c, a) => { if (a <= 0 || r <= 0) return; ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fillStyle = `rgba(${c},${a})`; ctx.fill(); };
  const star4 = (x, y, s, c, a) => { if (a <= 0) return; ctx.beginPath(); ctx.moveTo(x, y - s); ctx.lineTo(x + s * 0.28, y - s * 0.28); ctx.lineTo(x + s, y); ctx.lineTo(x + s * 0.28, y + s * 0.28); ctx.lineTo(x, y + s); ctx.lineTo(x - s * 0.28, y + s * 0.28); ctx.lineTo(x - s, y); ctx.lineTo(x - s * 0.28, y - s * 0.28); ctx.closePath(); ctx.fillStyle = `rgba(${c},${a})`; ctx.fill(); };
  // 맞은 자리에서 튀는 파편: i번째가 q(0~1)일 때 화면 위치. 바닥으로 퍼지며(납작) 높이는 포물선
  const flyOut = (i, q, sp, up, n) => { const a = (i / n) * Math.PI * 2 + rnd(i) * 0.8, v = sp * (0.6 + rnd(i + 9) * 0.6) * sc, z = up * sc * (0.7 + rnd(i + 3) * 0.6) * (q * 2 - q * q * 1.6);
    return [tx + Math.cos(a) * v * q, f.y1 - 8 + Math.sin(a) * v * q * FLAT - Math.max(0, z)]; };
  if (age < T) {
    const p = age / T, [x, y, gx, gy] = at(p);
    if (el === 'SLASH') {   // 근접 물리: 몸통을 가로로 휘두르는 베기 + 칼끝 불꽃
      const a0 = Math.PI * (0.15 + p * 0.5), a1 = Math.PI * (0.95 + p * 0.9);
      ctx.beginPath(); ctx.ellipse(tx, ty, 18 * sc, 18 * sc * FLAT, 0, a0, a1); ctx.strokeStyle = `rgba(${col},0.92)`; ctx.lineWidth = 3.4 * sc; ctx.lineCap = 'round'; ctx.stroke();
      ctx.strokeStyle = 'rgba(255,255,255,.8)'; ctx.lineWidth = 1.2 * sc; ctx.stroke();
      ctx.beginPath(); ctx.ellipse(tx, ty, 13 * sc, 13 * sc * FLAT, 0, a0 - 0.3, a1 - 0.3); ctx.strokeStyle = `rgba(${col},0.35)`; ctx.lineWidth = 2 * sc; ctx.stroke(); ctx.lineCap = 'butt';
      star4(tx + Math.cos(a1) * 18 * sc, ty + Math.sin(a1) * 18 * sc * FLAT, 4 * sc, '255,255,255', 0.9);
    } else if (el === 'LIGHTNING') {   // 번개: 하늘에서 내리꽂힘 + 갈래 + 맞은 자리 번쩍
      const top = f.y1 - 120, pts = [[tx + ((seed % 9) - 4) * 3, top]];
      for (let i = 1; i < 6; i++) { const q = i / 6; pts.push([tx + (((seed * (i + 3)) % 13) - 6) * 1.8 * (1 - q), top + (ty - top) * q]); }
      pts.push([tx, ty]);
      ctx.beginPath(); pts.forEach(([px, py], i) => (i ? ctx.lineTo(px, py) : ctx.moveTo(px, py)));
      ctx.strokeStyle = `rgba(${col},${0.35 - p * 0.2})`; ctx.lineWidth = 6 * sc; ctx.stroke();   // 번개 둘레 빛
      ctx.strokeStyle = `rgba(${col},${0.95 - p * 0.4})`; ctx.lineWidth = 2.4 * sc; ctx.stroke(); ctx.strokeStyle = `rgba(255,255,255,${0.85 - p * 0.4})`; ctx.lineWidth = 1; ctx.stroke();
      const [bx, by] = pts[2]; ctx.beginPath(); ctx.moveTo(bx, by); ctx.lineTo(bx + 10 * sc, by + 12); ctx.lineTo(bx + 7 * sc, by + 22); ctx.strokeStyle = `rgba(${col},${0.7 - p * 0.4})`; ctx.lineWidth = 1.4 * sc; ctx.stroke();   // 갈래
      groundRing(tx, f.y1, 10 * sc * (0.6 + p), col, 0.9 - p * 0.5, 1.8, 0.25);
      dot(tx, ty, 9 * sc * (1 - p * 0.5), '255,255,240', 0.5 * (1 - p)); dot(sx, sy, 4 * sc * (1 - p), col, 0.8);
    } else if (el === 'HOLY') {   // 성: 빛줄기 + 줄기를 따라 반짝이 + 대상 발밑이 밝아짐
      ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(sx + dx * p, sy + dy * p); ctx.strokeStyle = `rgba(${col},0.35)`; ctx.lineWidth = 7 * sc; ctx.stroke();
      ctx.strokeStyle = 'rgba(255,255,255,0.9)'; ctx.lineWidth = 2 * sc; ctx.stroke();
      for (let i = 0; i < 3; i++) { const q = p * (0.3 + i * 0.25); star4(sx + dx * q + (rnd(i) - 0.5) * 8, sy + dy * q - 4 - age * 30, 3 * sc, '255,250,220', 0.9 - p * 0.4); }
      groundRing(tx, f.y1, 8 * sc * p, col, 0.6 * p, 1.5, 0.3);
    } else {
      ctx.beginPath(); ctx.ellipse(gx, gy, 4.5 * sc, 4.5 * sc * FLAT, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.28)'; ctx.fill();   // 바닥 그림자
      // 꼬리: 같은 포물선 위 지나온 자리
      if (el === 'FIRE') for (let i = 12; i >= 1; i--) { const [qx, qy] = at(Math.max(0, p - i * 0.028)), k = 1 - i / 13; dot(qx + (rnd(i) - 0.5) * 3, qy - i * 0.5 + (rnd(i + 5) - 0.5) * 2, (1.6 + k * 3.4) * sc, k > 0.6 ? '255,220,120' : k > 0.3 ? '255,140,60' : '200,60,30', 0.25 + 0.5 * k); }   // 불꽃 꼬리(노랑→빨강, 살짝 위로)
      else if (el === 'WATER') for (let i = 7; i >= 1; i--) { const [qx, qy] = at(Math.max(0, p - i * 0.04)); dot(qx, qy + i * 0.8, (1.2 + (7 - i) * 0.4) * sc, col, 0.22 + 0.08 * (7 - i)); if (i % 3 === 1) dot(qx - 0.6, qy + i * 0.8 - 0.6, 0.7 * sc, '255,255,255', 0.6); }   // 물방울(떨어지듯)
      else if (el === 'ICE') for (let i = 3; i >= 1; i--) { const [qx, qy] = at(Math.max(0, p - i * 0.09)); star4(qx + (rnd(i) - 0.5) * 6, qy + (rnd(i + 2) - 0.5) * 6, (1.8 + (3 - i) * 0.6) * sc, '220,250,255', 0.4 + 0.15 * (3 - i)); }   // 서리 반짝이
      else if (el === 'DARK') for (let i = 0; i < 3; i++) { const a = age * 22 + i * 2.1, [qx, qy] = at(Math.max(0, p - 0.08)); dot(qx + Math.cos(a) * 6 * sc, qy + Math.sin(a) * 6 * sc * FLAT, 2 * sc, '120,60,190', 0.55); }   // 감도는 그림자
      else if (el === 'NATURE') for (let i = 2; i >= 1; i--) { const [qx, qy] = at(Math.max(0, p - i * 0.14)); ctx.save(); ctx.translate(qx, qy); ctx.rotate(age * 20 + i); ctx.beginPath(); ctx.ellipse(0, 0, 3.5 * sc, 1.5 * sc, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},0.5)`; ctx.fill(); ctx.restore(); }
      else if (el === 'ARROW') { const [qx, qy] = at(Math.max(0, p - 0.25)); ctx.beginPath(); ctx.moveTo(qx, qy); ctx.lineTo(x, y); ctx.strokeStyle = 'rgba(255,240,210,.35)'; ctx.lineWidth = 1.5 * sc; ctx.stroke(); }   // 바람 자국
      else for (let i = 2; i >= 1; i--) { const [qx, qy] = at(Math.max(0, p - i * 0.12)); dot(qx, qy, (2.5 + (2 - i)) * sc, col, 0.22 + 0.12 * (2 - i)); }
      // 2026-11-16 사용자 요청: 예전 최상위 효과를 커먼(가장 낮은 단계) 기본으로, 단계가 오를수록 겹을 더함
      dot(x, y, 8 * sc * (1 + lv * 0.12), col, 0.16 + lv * 0.03);   // 탄 둘레 빛무리(모든 단계)
      if (lv >= 3) dot(x, y, 4.5 * sc, '255,255,255', 0.18 + (lv - 3) * 0.1);   // 전설 이상: 하얀 속빛
      for (let i = 1; i <= 2 + lv; i++) { const [qx, qy] = at(Math.max(0, p - i * 0.06)); star4(qx + (rnd(i + 20) - 0.5) * 10, qy + (rnd(i + 30) - 0.5) * 10, Math.max(0.6, (2.6 - i * 0.3)) * sc * 0.62, lv >= 4 ? '255,236,150' : '255,250,220', Math.max(0.12, 0.8 - i * 0.12)); }   // 반짝이 꼬리(단계마다 길어짐, 최상위는 금빛)
      const [nx, ny] = at(Math.min(1, p + 0.04)), ang2 = Math.atan2(ny - y, nx - x);   // 포물선 방향으로 돌림
      ctx.save(); ctx.translate(x, y); ctx.rotate(ang2);
      if (el === 'ICE') { ctx.beginPath(); ctx.moveTo(9 * sc, 0); ctx.lineTo(0, 3.5 * sc); ctx.lineTo(-7 * sc, 0); ctx.lineTo(0, -3.5 * sc); ctx.closePath(); ctx.fillStyle = `rgba(${col},0.95)`; ctx.fill(); ctx.strokeStyle = 'rgba(255,255,255,.95)'; ctx.lineWidth = 1; ctx.stroke(); }
      else if (el === 'NATURE') { ctx.rotate(age * 30); ctx.beginPath(); ctx.ellipse(0, 0, 6 * sc, 2.6 * sc, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},0.95)`; ctx.fill(); ctx.beginPath(); ctx.moveTo(-5 * sc, 0); ctx.lineTo(5 * sc, 0); ctx.strokeStyle = 'rgba(40,110,40,.8)'; ctx.lineWidth = 0.8; ctx.stroke(); }
      else if (el === 'ARROW') { ctx.beginPath(); ctx.moveTo(-9 * sc, 0); ctx.lineTo(6 * sc, 0); ctx.strokeStyle = `rgba(${col},0.95)`; ctx.lineWidth = 2.4 * sc; ctx.stroke(); ctx.beginPath(); ctx.moveTo(7 * sc, 0); ctx.lineTo(2 * sc, 2.6 * sc); ctx.lineTo(2 * sc, -2.6 * sc); ctx.closePath(); ctx.fillStyle = `rgba(${col},1)`; ctx.fill(); ctx.beginPath(); ctx.moveTo(-9 * sc, 0); ctx.lineTo(-11 * sc, 2.4 * sc); ctx.moveTo(-9 * sc, 0); ctx.lineTo(-11 * sc, -2.4 * sc); ctx.strokeStyle = 'rgba(255,255,255,.8)'; ctx.lineWidth = 1; ctx.stroke(); }   // 화살(깃 달림)
      else if (el === 'FIRE') { dot(0, 0, 8 * sc, '255,120,40', 0.25); dot(0, 0, 5 * sc, '255,150,60', 0.9); dot(0.8 * sc, -0.8 * sc, 2.6 * sc, '255,240,180', 0.95); }   // 불덩이(겉 주황·속 노랑)
      else if (el === 'WATER') { dot(0, 0, 6.5 * sc, col, 0.25); dot(0, 0, 4.2 * sc, col, 0.95); dot(-1.4 * sc, -1.4 * sc, 1.3 * sc, '255,255,255', 0.9); }   // 물방울 구슬(반짝임)
      else if (el === 'DARK') { dot(0, 0, 7.5 * sc, '150,90,230', 0.3); dot(0, 0, 4.6 * sc, '40,10,70', 0.95); ctx.lineWidth = 1.4; ctx.strokeStyle = `rgba(${col},0.95)`; ctx.stroke(); }   // 어둠 구슬(검은 속·보라 테)
      else { const r = 4 * sc; dot(0, 0, r * 1.8, col, 0.22); dot(0, 0, r, col, 0.98); dot(0, 0, r * 0.45, '255,255,255', 0.85); }
      ctx.restore();
    }
    return;
  }
  // 맞음: 속성마다 다른 반응(게임 시간 0.4초, 전설 이상 0.48초 — 기본 1.5배속이라 실제로는 0.27·0.32초) + 발밑 고리. 효과는 0.8초 뒤 지워짐(날아가는 시간 최대 0.22 + 0.48 안)
  const a2 = age - T, D = [0.46, 0.48, 0.5, 0.53, 0.56][lv];
  if (a2 >= D) return;
  const q = a2 / D, fa = 1 - q, n = [8, 9, 11, 13, 16][lv];
  groundRing(tx, f.y1, (10 + q * 30) * sc * 0.62, col, 0.7 * fa, 2 + lv * 0.3);   // 바닥 고리(모든 단계)
  if (lv >= 1) groundRing(tx, f.y1, (6 + q * 16) * sc * 0.62, '255,255,255', 0.45 * fa, 1.2);   // 레어 이상: 안쪽 흰 고리
  if (lv >= 2 && q < 0.6) { ctx.strokeStyle = `rgba(${col},${0.8 * (1 - q / 0.6)})`; ctx.lineWidth = 1.6; for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4 + rnd(i + 40) * 0.3, r0 = (5 + q * 14) * sc * 0.6, r1 = r0 + 8 * sc * (1 - q); ctx.beginPath(); ctx.moveTo(tx + Math.cos(a) * r0, ty + Math.sin(a) * r0 * FLAT); ctx.lineTo(tx + Math.cos(a) * r1, ty + Math.sin(a) * r1 * FLAT); ctx.stroke(); } }   // 에픽 이상: 사방으로 튀는 빛줄기
  if (lv >= 3 && q < 0.7) { const h = (40 + lv * 10) * sc * 0.6 * (1 - q * 0.4), w = 5 * sc * (1 - q / 0.7); const gr = ctx.createLinearGradient(0, f.y1 - h, 0, f.y1); gr.addColorStop(0, `rgba(${col},0)`); gr.addColorStop(1, `rgba(${col},${0.5 * (1 - q / 0.7)})`); ctx.fillStyle = gr; ctx.fillRect(tx - w, f.y1 - h, w * 2, h); }   // 전설 이상: 솟는 빛기둥
  if (lv >= 4) { groundRing(tx, f.y1, (16 + q * 46) * sc * 0.62, '255,236,150', 0.6 * fa, 2.6); for (let i = 0; i < 4; i++) { const a = q * 7 + i * Math.PI / 2, r = (10 + q * 18) * sc * 0.6; star4(tx + Math.cos(a) * r, ty + Math.sin(a) * r * FLAT, 3 * sc * 0.7 * fa, '255,236,150', fa); } }   // 최상위: 금빛 충격파·도는 별
  if (q < 0.5) { const s4 = (8 + q * 26) * sc * 0.6 * (1 + lv * 0.1); star4(tx, ty, s4, col, 0.85 * (1 - q * 2)); star4(tx, ty, s4 * 0.5, '255,255,255', 0.9 * (1 - q * 2)); }   // 맞은 자리 별빛(모든 단계, 단계마다 큼)
  if (q < 0.25) dot(tx, ty, (6 + q * 20) * sc, el === 'DARK' ? '150,90,230' : '255,250,230', 0.5 * (1 - q / 0.25));   // 맞는 순간 번쩍
  if (el === 'FIRE') { for (let i = 0; i < n + 1; i++) { const [px, py] = flyOut(i, q, 22, 26, n + 1); dot(px, py, (2.2 - q * 1.4) * sc, q < 0.4 ? '255,220,120' : '255,120,50', fa); } groundRing(tx, f.y1, (5 + q * 9) * sc, '120,40,20', 0.5 * fa, 1, 0.5); }   // 불씨가 튀고 바닥이 그을림
  else if (el === 'WATER') { for (let i = 0; i < n; i++) { const [px, py] = flyOut(i, q, 18, 30, n); dot(px, py, 1.8 * sc, col, fa); dot(px - 0.5, py - 0.5, 0.7 * sc, '255,255,255', fa); } groundRing(tx, f.y1, (4 + q * 14) * sc, col, 0.7 * fa, 1.4); groundRing(tx, f.y1, (2 + q * 8) * sc, col, 0.5 * fa, 1); }   // 물이 튀고 물결 두 겹
  else if (el === 'ICE') { for (let i = 0; i < n; i++) { const [px, py] = flyOut(i, q, 20, 18, n); ctx.save(); ctx.translate(px, py); ctx.rotate(i + q * 8); ctx.beginPath(); ctx.moveTo(3 * sc, 0); ctx.lineTo(0, 1.4 * sc); ctx.lineTo(-3 * sc, 0); ctx.lineTo(0, -1.4 * sc); ctx.closePath(); ctx.fillStyle = `rgba(220,250,255,${fa})`; ctx.fill(); ctx.restore(); } star4(tx, ty, (5 + q * 4) * sc, '230,252,255', fa); groundRing(tx, f.y1, (5 + q * 10) * sc, col, 0.6 * fa, 1.4, 0.3); }   // 얼음 조각이 깨져 흩어짐 + 서리
  else if (el === 'LIGHTNING') { ctx.strokeStyle = `rgba(${col},${fa})`; ctx.lineWidth = 1.4; for (let i = 0; i < 4; i++) { const a = i * 1.57 + rnd(i) * 0.8, r1 = (4 + q * 12) * sc; ctx.beginPath(); ctx.moveTo(tx + Math.cos(a) * 3, ty + Math.sin(a) * 3); ctx.lineTo(tx + Math.cos(a + 0.4) * r1 * 0.6, ty + Math.sin(a + 0.4) * r1 * 0.6); ctx.lineTo(tx + Math.cos(a) * r1, ty + Math.sin(a) * r1); ctx.stroke(); } groundRing(tx, f.y1, (6 + q * 10) * sc, col, 0.7 * fa, 1.6); }   // 사방으로 튀는 작은 전기
  else if (el === 'HOLY') { const h = 34 * sc * (1 - q * 0.3), w = 6 * sc * fa; ctx.fillStyle = `rgba(255,245,200,${0.35 * fa})`; ctx.fillRect(tx - w, f.y1 - h, w * 2, h); for (let i = 0; i < 3; i++) star4(tx + (rnd(i) - 0.5) * 14, f.y1 - 10 - q * 30 - i * 8, 2.6 * sc, '255,250,220', fa); groundRing(tx, f.y1, (6 + q * 8) * sc, col, 0.8 * fa, 1.6, 0.35); }   // 작은 빛기둥 + 솟는 반짝이
  else if (el === 'DARK') { groundRing(tx, f.y1, (16 - q * 13) * sc, '150,90,230', 0.8 * fa, 2, 0.25); for (let i = 0; i < 4; i++) { const a = i * 1.57 + q * 6, r = (14 - q * 12) * sc; dot(tx + Math.cos(a) * r, ty + Math.sin(a) * r * FLAT, 1.8 * sc, '120,60,190', fa); } }   // 빨려 드는 그림자
  else if (el === 'NATURE') { for (let i = 0; i < n; i++) { const [px, py] = flyOut(i, q, 18, 22, n); ctx.save(); ctx.translate(px, py); ctx.rotate(i * 1.3 + q * 10); ctx.beginPath(); ctx.ellipse(0, 0, 3 * sc, 1.3 * sc, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},${fa})`; ctx.fill(); ctx.restore(); } groundRing(tx, f.y1, (5 + q * 9) * sc, col, 0.5 * fa, 1.2); }   // 잎이 흩날림
  else {   // 화살·근접·마법 구슬: 불꽃 파편 + (화살·근접) 흙먼지 / (구슬) 반짝이
    if (q < 0.45) { const qq = q / 0.45; ctx.strokeStyle = `rgba(${col},${0.9 * (1 - qq)})`; ctx.lineWidth = big ? 2.2 : 1.6; for (let i = 0; i < n; i++) { const an = ang + Math.PI + (i - (n - 1) / 2) * 0.55 + ((seed + i * 17) % 7 - 3) * 0.05, r0 = 3 + qq * 6, r1 = r0 + (big ? 9 : 6) * (1 - qq * 0.5); ctx.beginPath(); ctx.moveTo(tx + Math.cos(an) * r0, ty + Math.sin(an) * r0); ctx.lineTo(tx + Math.cos(an) * r1, ty + Math.sin(an) * r1); ctx.stroke(); } }
    if (el === 'ORB') for (let i = 0; i < 4; i++) { const [px, py] = flyOut(i, q, 16, 20, 4); star4(px, py, 2.4 * sc, '255,255,255', fa); }
    else for (let i = 0; i < 3; i++) dot(tx + (rnd(i) - 0.5) * 16 * sc, f.y1 - 2 - q * 6, (2.5 + q * 4) * sc, '170,150,120', 0.3 * fa);   // 흙먼지
    groundRing(tx, f.y1, ((big ? 8 : 5) + q * (big ? 16 : 10)), col, 0.7 * fa, big ? 2 : 1.4);
  }
  if (f.tier >= 6 && f.boss && a2 < 0.02 && shake.until < G.t + 0.05) { shake.until = G.t + 0.12; shake.mag = f.tier >= 8 ? 3 : 2; }
}
// 2.5D 바닥 효과: 바닥(발밑) 높이에 납작한 타원. 판의 기술 범위 표시(0.62)와 같은 비율
const FLAT = 0.62;
function groundRing(x, y, r, col, a, lw = 2, fill = 0) {
  if (a <= 0 || r <= 0) return;
  ctx.beginPath(); ctx.ellipse(x, y, r, r * FLAT, 0, 0, Math.PI * 2);
  if (fill) { ctx.fillStyle = `rgba(${col},${fill * a})`; ctx.fill(); }
  ctx.strokeStyle = `rgba(${col},${a})`; ctx.lineWidth = lw; ctx.stroke();
}
// ── 위 정보 줄 ──
function drawHud() {
  fenceBox(8, 6, W - 16, 88); siren(14, 3, 8); siren(W - 22, 3, 8);   // 항만 방범대 상황판 + 양쪽 위 경고등(테두리 위 — 글자·단추와 겹치지 않게, 검토)
  const wi = E.waveInfo(Math.max(1, G.wave)), left = Math.ceil(E.nextWaveIn(G));
  text(`웨이브 ${G.wave}/${E.RULES.waves}`, 20, 28, 20, C.text);
  // 방어(방어력=마법방어력)와 내 판의 방깍·마방깍 합계(사용자 요청 2026-11-08: 후반엔 깎는 조합이 있어야 깸)
  const armorNow = E.armorAt(G, Math.max(1, G.wave)), mrNow = E.mrAt(G, Math.max(1, G.wave));
  const cw = chip(`${wi.boss ? '보스 ' : ''}방어 ${armorNow}${mrNow !== armorNow ? `·마${mrNow}` : ''}`, 150, 17, wi.boss ? '#ff8a7a' : C.muted, 13);
  let shSum = 0, mshSum = 0, slSum = 0, stSum = 0;
  for (const u of E.fieldUnits(G)) { const k = E.kit(u.id); shSum += k.shred ?? 0; mshSum += k.mshred ?? 0; slSum += k.slow ?? 0; stSum += (k.stun ?? 0) + (k.astun ?? 0); }
  const okCol = (v, a = armorNow) => (v >= a * 0.7 ? C.good : v >= a * 0.35 ? C.goldEdge : C.bad);
  ctx.font = `12px ${FONT}`;
  const sx = 150 + cw + 8, maxW = W - 224 - sx;
  if (maxW > 40) {
    const t1 = `깎인 방어 ${armorNow}→${Math.max(0, Math.round(armorNow - shSum))}`, t2 = ` · 마방 ${mrNow}→${Math.max(0, Math.round(mrNow - mshSum))}`;   // 2026-11-19 사용자 요청: 깎은 뒤 남는 방어 수치로
    const tw = ctx.measureText(t1 + t2).width, sc2 = Math.min(1, maxW / tw), fs = Math.max(10, Math.floor(12 * sc2));
    text(t1, sx, 28, fs, okCol(shSum));
    ctx.font = `${fs}px ${FONT}`;
    text(t2, sx + ctx.measureText(t1).width, 28, fs, okCol(mshSum, mrNow));
  }
  const rest = E.resting(G);
  const line2 = rest ? `쉬는 시간 · 다음 웨이브까지 ${left}초` : wi.boss ? (G.bossDone === G.wave ? '보스 처치! 다음 웨이브까지 ' + left + '초' : `보스를 ${Math.ceil(Math.max(0, E.baseLenOf(G.wave) - G.waveT))}초 안에 잡기`) : `다음 웨이브 ${left}초`;
  text(line2, 20, 58, 15, rest ? C.good : wi.boss ? (G.bossDone === G.wave ? C.good : left <= 12 ? C.bad : '#ffc2a8') : C.muted);
  ctx.font = `15px ${FONT}`;
  let lx = 20 + ctx.measureText(line2).width + 10;
  const wx = E.weatherOf(G);   // 날씨(2026-11-16): 맑음이 아니면 이름을 옆에
  if (wx !== E.WEATHERS.calm) { text(`· ${wx.name}`, lx - 4, 58, 15, '#8fd8ff'); ctx.font = `15px ${FONT}`; lx += ctx.measureText(`· ${wx.name}`).width + 8; }
  if (G.wave < E.RULES.waves) {   // 다음 웨이브 종류 미리 보기(사용자 요청 2026-11-08)
    const nx = E.waveInfo(G.wave + 1); nx.armor = E.armorAt(G, G.wave + 1);
    const rf = nx.boss ? 0 : E.riftAt(G.wave + 1);   // 균열 저항(41웨이브부터 라인 적이 전설·히든 이하 피해를 덜 받음)
    fitText(`▸ 다음 ${nx.boss ? `보스! 방어 ${nx.armor}` : `방어 ${nx.armor}`}${rf ? ` · 균열 저항 ${Math.round(rf * 100)}%` : ''}`, lx, 58, W - 30 - lx, 14, nx.boss ? '#ff8a7a' : rf ? '#c9a3ff' : nx.armor > armorNow ? C.goldEdge : C.muted);
  }
  // 적 수·경고
  const hard = E.hardLimitOn(G), n = E.enemyCount(G), lim = hard ? E.hardLimitOf(G) : E.RULES.limit, ratio = Math.min(1, n / lim);   // 45웨이브부터 /50(넘으면 즉시 패배)
  text(`적 ${n}/${lim}`, 20, 82, 14, n > lim || (hard && ratio > 0.8) ? C.bad : C.text);
  box(92, 77, 150, 10, 'rgba(0,0,0,.5)', hard ? '#b4482a' : C.line, 5, 1);
  box(92, 77, 150 * ratio, 10, n > lim || (hard && ratio > 0.8) ? '#e0533f' : ratio > 0.7 ? '#e8b04a' : '#6fd08a', null, 5);
  if (hard) fitText('넘으면 즉시 패배', 256, 82, 78, 12, C.bad);   // 45웨이브부터: 경고 대신(새우 칸과 겹치지 않게 짧게 — 검토)
  else text(`경고 ${G.warn}/${E.warnMaxOf(G)}`, 256, 82, 14, G.warn ? C.bad : C.muted);   // 증강체로 한도가 바뀜
  // 새우·강화
  const sh = img(`${ART}/menu/currency-shrimp.webp`);
  if (sh) drawFit(sh, 340, 64, 22, 22);
  text(`${G.gold}`, 366, 76, 16, C.goldEdge);
  // 판의 이감·스턴 합계(2026-11-10 사용자 요청). 이감은 겹쳐 최대 100%, 스턴은 정보 창의 스턴%·범위 스턴%를 더한 값
  { ctx.font = `16px ${FONT}`; const gx = 366 + ctx.measureText(`${G.gold}`).width + 12, sl = Math.round(Math.min(E.SLOW_MAX, slSum) * 100), st = Math.round(stSum * 100);
    const t1 = `이감 ${sl}%`, t2 = ` · 스턴 ${st}%`, mw = W - 16 - gx;
    if (mw > 50) { ctx.font = `12px ${FONT}`; const fs = Math.max(9, Math.floor(12 * Math.min(1, mw / ctx.measureText(t1 + t2).width)));
      text(t1, gx, 77, fs, sl >= 60 ? C.good : sl >= 30 ? C.goldEdge : C.muted); ctx.font = `${fs}px ${FONT}`;
      text(t2, gx + ctx.measureText(t1).width, 77, fs, st >= 30 ? C.good : st > 0 ? C.goldEdge : C.muted); } }
  // 「피해」 버튼은 아래 「더보기」로 옮김(2026-11-11 버튼 줄이기). 단축키 D는 그대로
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
// 아래 메뉴 줄 배치: 늘어난 높이(ex)를 위습·버튼·목표 칸 높이로 나눔(가로 화면은 ex = 0)
function panelLayout(y0 = 672) {
  const ex = Math.max(0, PBOT - H);
  // 위습 줄은 낮게, 버튼 6개는 3개씩 두 줄(사용자 요청 2026-11-08: 조합 목표 칸 빼고 버튼 줄 나눔)
  // 디자인(사용자 요청 2026-11-08: 판 크기는 그대로, 여백이 어색하지 않게): 버튼은 104까지만 키우고, 남는 높이는 줄 사이 간격으로 고르게
  const top = y0 + 30, avail = PBOT - 16 - top;
  // 사용자 선택(2026-11-08): 버튼은 조금 줄이고 위습 칸을 크게
  const wh = Math.min(110, 60 + ex * 0.3), bh = Math.max(56, Math.min(92, (avail - wh - 18) / 2));
  const spare = Math.max(0, avail - wh - 18 - 2 * bh), gap = spare / 3;   // 위·가운데(구분선)·아래에 고르게
  const wy = top + gap * 0.9, by = wy + wh + 10 + gap * 1.05;
  return { ex, wy, wh, by, bh, by2: by + bh + 8, labelY: y0 + 18 + gap * 0.7, divY: wy + wh + 5 + gap * 0.52, gap };
}
// ── 항만 방범대 스타일(2026-11-15 사용자 제공 스타일셋 v2.0: 네온 대신 묵직한 밀리터리) ──
// harbor-fence-border(빗금 강판 + 철제 이중 테두리 + 아래 그림자) · siren-light(1.2초 켜짐/꺼짐 두 단계) · military-panel(위·왼쪽 밝고 아래·오른쪽 어두운 철판 + 리벳) · hazard-stripe(노랑/검정 빗금)
const stripePat = new Map();
function stripe(a, b, sz, dir = 1) {   // 빗금 무늬(45°·−45°), 크기 sz마다 두 색
  const k = `${a}|${b}|${sz}|${dir}`; let p = stripePat.get(k);
  if (!p) { const c = document.createElement('canvas'); c.width = c.height = sz * 2; const x = c.getContext('2d'); x.fillStyle = a; x.fillRect(0, 0, sz * 2, sz * 2); x.fillStyle = b;
    x.beginPath(); if (dir > 0) { x.moveTo(sz, 0); x.lineTo(sz * 2, 0); x.lineTo(0, sz * 2); x.lineTo(0, sz); x.closePath(); x.moveTo(sz * 2, sz); x.lineTo(sz * 2, sz * 2); x.lineTo(sz, sz * 2); x.closePath(); }
    else { x.moveTo(0, 0); x.lineTo(sz, 0); x.lineTo(sz * 2, sz); x.lineTo(sz * 2, sz * 2); x.closePath(); x.moveTo(0, sz); x.lineTo(sz, sz * 2); x.lineTo(0, sz * 2); x.closePath(); }
    x.fill(); p = ctx.createPattern(c, 'repeat'); stripePat.set(k, p); }
  return p;
}
function fenceBox(x, y, w, h) {   // 상단 상황판: 빗금 강판 + 철제 테두리 4 + 안쪽 2·4 + 아래 그림자 4
  ctx.fillStyle = '#0d1117'; ctx.fillRect(x, y + 4, w, h);
  ctx.fillStyle = stripe('#1e293b', '#0f172a', 10); ctx.fillRect(x, y, w, h);
  ctx.fillStyle = 'rgba(15,23,42,.55)'; ctx.fillRect(x, y, w, h);   // 글자가 읽히게 빗금을 눌러 줌
  ctx.lineWidth = 4; ctx.strokeStyle = '#2a3439'; ctx.strokeRect(x + 2, y + 2, w - 4, h - 4);
  ctx.lineWidth = 2; ctx.strokeStyle = '#4a5568'; ctx.strokeRect(x + 5, y + 5, w - 10, h - 10);
  ctx.lineWidth = 2; ctx.strokeStyle = '#1a202c'; ctx.strokeRect(x + 7, y + 7, w - 14, h - 14);
}
function siren(x, y, s = 10) {   // 초소 경고등: 1.2초에 켜짐·꺼짐 두 단계(부드럽게 바뀌지 않음)
  const on = Math.floor(performance.now() / 600) % 2 === 0;
  if (on) { ctx.save(); ctx.shadowColor = '#ef4444'; ctx.shadowBlur = 8; ctx.fillStyle = '#ef4444'; ctx.fillRect(x, y, s, s); ctx.restore(); ctx.fillStyle = 'rgba(252,165,165,.7)'; ctx.fillRect(x + 2, y + 2, s - 4, s - 4); }
  else { ctx.fillStyle = '#7f1d1d'; ctx.fillRect(x, y, s, s); ctx.fillStyle = '#991b1b'; ctx.fillRect(x + 2, y + 2, s - 4, s - 4); }
  ctx.lineWidth = 1; ctx.strokeStyle = '#0f172a'; ctx.strokeRect(x - 0.5, y - 0.5, s + 1, s + 1);
}
function steelFrame(x, y, w, h, t = 3) {   // 철제 컨테이너 테두리(안은 그대로): 위·왼쪽 #64748b, 아래·오른쪽 #0f172a, 아래 그림자, 네 모서리 리벳
  ctx.fillStyle = 'rgba(0,0,0,.4)'; ctx.fillRect(x, y + h, w, 4);
  ctx.fillStyle = '#64748b'; ctx.fillRect(x, y, w, t); ctx.fillRect(x, y, t, h);
  ctx.fillStyle = '#0f172a'; ctx.fillRect(x, y + h - t, w, t); ctx.fillRect(x + w - t, y, t, h);
  ctx.fillStyle = '#94a3b8'; for (const [rx, ry] of [[x + 5, y + 5], [x + w - 11, y + 5], [x + 5, y + h - 11], [x + w - 11, y + h - 11]]) { ctx.fillRect(rx, ry, 6, 6); ctx.fillStyle = '#475569'; ctx.fillRect(rx + 3, ry + 3, 3, 3); ctx.fillStyle = '#94a3b8'; }
}
function hazard(x, y, w, h = 10) {   // 경고 테이프: 노랑/검정 −45° 빗금 12 + 위아래 검은 선 2
  ctx.fillStyle = stripe('#f59e0b', '#1e293b', 12, -1); ctx.fillRect(x, y, w, h);
  ctx.fillStyle = '#0f172a'; ctx.fillRect(x, y - 2, w, 2); ctx.fillRect(x, y + h, w, 2);
}
// 판 테두리 장식(게임 UI 모서리 그림)
function decorCorners(x, y, w, h, s = 46) {
  const im = (n) => img(`${ART}/ui/decor-corner-${n}.webp`), r = 216 / 256;
  ctx.globalAlpha = 0.85;
  const tl = im('tl'), tr = im('tr'), bl = im('bl'), br = im('br');
  if (tl) ctx.drawImage(tl, x - 4, y - 4, s, s * r);
  if (tr) ctx.drawImage(tr, x + w - s + 4, y - 4, s, s * r);
  if (bl) ctx.drawImage(bl, x - 4, y + h - s * r + 4, s, s * r);
  if (br) ctx.drawImage(br, x + w - s + 4, y + h - s * r + 4, s, s * r);
  ctx.globalAlpha = 1;
}
function colA(fn) { ctx.save(); ctx.translate(AX, AY); ctx.scale(WK, WK); OX = AX; OY = AY; OS = WK; try { fn(); } finally { ctx.restore(); OX = 0; OY = 0; OS = 1; } }   // 넓은 화면의 창 칸
function colB(fn) { ctx.save(); ctx.translate(BX, BYT); ctx.scale(WK, WK); OX = BX; OY = BYT; OS = WK; try { fn(); } finally { ctx.restore(); OX = 0; OY = 0; OS = 1; } }   // 메뉴 칸
function drawBottom() {
  const y0 = 672;
  box(8, y0, W - 16, PBOT - y0 - 8, C.panel, C.edge, 12);
  decorCorners(8, y0, W - 16, PBOT - y0 - 8);
  hazard(40, y0 - 6, W - 80, 4);   // 위 창(전술창)과 아래 메뉴 사이 경고 테이프(가로 화면엔 위습·버튼 사이 줄이 없어 여기에도)
  const sel = selUnits();
  if (sel.length === 1) drawUnitPanel(y0, sel[0]);
  else if (sel.length > 1) drawGroupPanel(y0, sel);
  else drawMainPanel(y0);
}
// 아래 메뉴 버튼(사용자 지적 2026-11-08: 칸만 커져 여백이 많음): 그림 + 이름 + 상태. 칸이 높으면 그림 위·글자 아래, 낮으면 그림 왼쪽
const MENU_ICON = ['menu/icon-draw', 'menu/icon-team', 'ui/icon-goldenstrike', 'menu/icon-explore', 'menu/icon-shop', 'menu/icon-combine'];
function menuButton(x, y, w, h, icon, label, fn, style, sub, subCol) {
  const prim = style === 'primary';
  if (prim) { const g = ctx.createLinearGradient(0, y, 0, y + h); g.addColorStop(0, '#f3d89e'); g.addColorStop(1, '#c9a463'); box(x, y, w, h, g, '#fff3cf', 12, 2); }
  else box(x, y, w, h, '#163447', C.gold, 12, 1.5);
  const im = img(`${ART}/${icon}.webp`), tc = prim ? C.ink : C.text, sc = subCol ?? (prim ? '#3d2f1a' : C.muted);
  if (h >= 96) {   // 세로로 큰 칸
    const is = Math.min(h * 0.46, w * 0.5, 64), gap = Math.max(4, (h - is - 52) / 3);
    drawFit(im, x + (w - is) / 2, y + gap, is, is);
    fitText(label, x + w / 2, y + gap + is + gap * 0.6 + 12, w - 12, 19, tc, 'center');
    fitText(sub, x + w / 2, y + gap + is + gap * 0.6 + 34, w - 12, 14, sc, 'center');
  } else {   // 낮은 칸
    const is = Math.min(h - 14, 44), tx = x + 10 + is + 8;
    drawFit(im, x + 10, y + (h - is) / 2, is, is);
    fitText(label, tx, y + h / 2 - 10, x + w - tx - 6, 17, tc);
    fitText(sub, tx, y + h / 2 + 12, x + w - tx - 6, 13, sc);
  }
  hit(x, y, w, h, fn);
}
function drawMainPanel(y0) {
  // 위습
  const ws = E.WISP_GRADES, bw = (W - 44 - 4 * 6) / 5, PL = panelLayout(y0);
  text('위습', 22, PL.labelY, 14, C.gold);
  ws.forEach((gr, k) => {   // 위습 칸: 있으면 등급 색으로 은은하게 빛나는 ✦
    const n = G.wisps[gr] ?? 0, x = 22 + k * (bw + 6), y = PL.wy, h = PL.wh, col = GRADE_COLOR[gr];
    box(x, y, bw, h, n ? 'rgba(11,30,42,.92)' : 'rgba(11,30,42,.45)', n ? col : '#33434d', 10, n ? 2 : 1);
    if (n && h < 90) {
      const cx = x + bw * 0.3, cy = y + h / 2, rg = ctx.createRadialGradient(cx, cy, 2, cx, cy, h * 0.42);
      rg.addColorStop(0, col + '99'); rg.addColorStop(1, col + '00');
      ctx.fillStyle = rg; ctx.fillRect(x + 2, y + 2, bw * 0.6, h - 4);
    }
    if (h >= 90) {   // 큰 칸: ✦ 위, 이름·개수 아래
      if (n) { const rg2 = ctx.createRadialGradient(x + bw / 2, y + h * 0.33, 2, x + bw / 2, y + h * 0.33, h * 0.38); rg2.addColorStop(0, col + '99'); rg2.addColorStop(1, col + '00'); ctx.fillStyle = rg2; ctx.fillRect(x + 2, y + 2, bw - 4, h * 0.66); }
      text('✦', x + bw / 2, y + h * 0.33, Math.min(40, h * 0.36), n ? col : '#3d4c55', 'center');
      fitText(E.GRADE_NAME[gr], x + bw / 2, y + h * 0.66, bw - 8, 14, n ? col : '#5b6a73', 'center');
      text(`×${n}`, x + bw / 2, y + h * 0.86, 20, n ? C.text : '#5b6a73', 'center');
    } else {
      text('✦', x + bw * 0.3, y + h / 2 + 1, Math.min(30, h * 0.42), n ? col : '#3d4c55', 'center');
      fitText(E.GRADE_NAME[gr], x + bw * 0.72, y + h * 0.32, bw * 0.5, 13, n ? col : '#5b6a73', 'center');
      text(`${n}`, x + bw * 0.72, y + h * 0.68, Math.min(24, h * 0.32), n ? C.text : '#5b6a73', 'center');
    }
    if (n) hit(x, y, bw, h, () => doSummon(gr));
  });
  if (PL.gap > 14) {   // 위습과 버튼 사이 장식 구분선
    const dy = PL.divY, cx = W / 2;
    hazard(28, dy - 4, W - 56, 8);   // 항만 방범대 경고 테이프(2026-11-15 사용자 스타일셋)
  }
  // 아래 버튼 4개(2026-11-11 유저 피드백 「버튼이 너무 많음」: 6개 → 4개, 두 개씩 두 줄): 조합법 · 강화 / 외부탐사 · 더보기(선택위습·용병모집·창고·피해)
  const b2 = (W - 44 - 6) / 2, BH = PL.bh, bx = (i) => 22 + (i % 2) * (b2 + 6), byy = (i) => (i < 2 ? PL.by : PL.by2);
  const btn = (i, icon, label, fn, style, sub, subCol) => menuButton(bx(i), byy(i), b2, BH, icon, label, fn, style, sub, subCol);
  const rc = readyCount();
  btn(0, 'menu/icon-combine', '조합법', () => openBook(), rc ? 'primary' : 'normal', rc ? `조합 가능 ${rc}` : `${E.RECIPES.length}개`, rc ? '#1f5a3c' : null);
  if (rc) badge(bx(0) + b2 - 6, PL.by + 6, rc);
  const canUp = G.upg.some((_, gi) => !E.upgMaxed(G, gi) && G.gold >= E.upgradeCost(G, gi));
  btn(1, 'ui/icon-goldenstrike', '강화', () => { modal = { kind: 'upgrade' }; }, 'normal', canUp ? '강화 가능' : `새우 ${G.gold}`, canUp ? C.good : null);
  const away = E.awayUnits(G).length, nextR = E.REGIONS[G.missionDone];
  const msub = E.modeOf(G).noMission ? '금지 모드' : G.missionDone >= E.REGIONS.length ? '모두 완료' : away ? `${away}마리 탐사 중` : `${nextR.ch}장 가능`;
  btn(2, 'menu/icon-explore', '외부탐사', () => { modal = { kind: 'mission' }; }, 'normal', msub);
  const mc = moreCount();
  btn(3, 'menu/icon-draw', '더보기', () => { modal = { kind: 'more' }; }, mc ? 'primary' : 'normal', moreSub(), mc ? '#3d2f1a' : null);
  if (mc) badge(bx(3) + b2 - 6, PL.by2 + 6, mc);
}
function badge(x, y, n) { ctx.beginPath(); ctx.arc(x, y, 11, 0, Math.PI * 2); ctx.fillStyle = '#e0533f'; ctx.fill(); ctx.lineWidth = 2; ctx.strokeStyle = '#07131c'; ctx.stroke(); text(String(n), x, y + 0.5, 13, '#fff', 'center'); }
const pickCount = () => E.PICK_GRADES.reduce((a, gr) => a + (G.picks[gr] ?? 0), 0);
const moreCount = () => (pickCount() ? 1 : 0) + ((G.tickets ?? 0) >= E.RULES.recruitCost ? 1 : 0) + (G.choice ? 1 : 0) + (G.augOffer ? 1 : 0);   // 할 수 있는 것 수(빨간 점)
const moreSub = () => [G.augOffer && '증강체 고르기', G.choice && '보상 고르기', (G.rerolls ?? 0) > 0 && `리롤권 ${G.rerolls}`, pickCount() && `선택위습 ${pickCount()}`, (G.tickets ?? 0) >= E.RULES.recruitCost && '모집 가능'].filter(Boolean).join(' · ') || `창고 ${E.storedUnits(G).length}마리`;
function drawMore() {   // 「더보기」: 선택위습 · 용병모집 · 창고 · 피해 순위
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('더보기', x + 18, y + 30, 20, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const picks = pickCount(), tk = G.tickets ?? 0, st = E.storedUnits(G).length;
  const items = [
    ['menu/icon-draw', '선택위습', () => { modal = { kind: 'pick', grade: E.PICK_GRADES.find((gr) => G.picks[gr] > 0) ?? 'COMMON' }; }, picks ? 'primary' : 'normal', picks ? `${picks}개 있음` : '없음'],
    ['menu/icon-team', '용병모집', () => { modal = { kind: 'recruit' }; }, tk >= E.RULES.recruitCost ? 'primary' : 'normal', `모집권 ${tk}`],
    ['menu/icon-shop', '창고', () => { modal = { kind: 'storage', scroll: 0, scrollable: true }; }, 'normal', `${st}마리${Object.values(G.autoGrades ?? {}).some(Boolean) ? '·자동' : ''}`],
    ['ui/icon-goldenstrike', '피해 순위', () => { modal = { kind: 'meter', scroll: 0, scrollable: true }; }, 'normal', '갈매미별 피해'],
    ['menu/currency-shrimp', '새우 상점', () => { modal = { kind: 'shop' }; }, 'normal', E.shopOpen(G) ? `새우 ${fmtN(G.gold)}` : `${E.RULES.shopFrom}웨이브부터`],   // 2026-11-19: 세로 화면에서도
    ...(E.skillOn(G) ? [['ui/icon-goldenstrike', '갈매미맨 폭격', () => { if (E.useSkill(G)) { sfx('boss'); modal = null; } else say(E.skillLeft(G) > 0 ? `${Math.ceil(E.skillLeft(G))}초 뒤에 쓸 수 있어요` : `새우 ${fmtN(E.skillCost(G))}이(가) 필요해요`); }, E.skillLeft(G) <= 0 && G.gold >= E.skillCost(G) ? 'primary' : 'normal', E.skillLeft(G) > 0 ? `${Math.ceil(E.skillLeft(G))}초 뒤` : `새우 ${fmtN(E.skillCost(G))}`]] : []),
  ];
  // 맨 위: 미뤄 둔 증강체·보상 고르기, 가진 증강체(2026-11-15)
  let cy0 = 0;
  if (G.augOffer) { button(x + 12, y + 64, w - 24, 64, `${G.augOffer.wave}웨이브 증강체 고르기`, () => { modal = { kind: 'aug' }; }, 'primary', true, G.augOffer.opts.map((id) => E.AUGS[id].name).join(' · ')); cy0 += 76; }
  else if (G.augs?.length) { fitText(`증강체: ${G.augs.map((id) => E.AUGS[id].name).join(' · ')}`, x + 18, y + 72, w - 36, 14, C.goldEdge); cy0 += 24; }
  if (G.choice) button(x + 12, y + 64 + cy0, w - 24, 64, `${E.REGIONS[G.choice.k].ch}장 보상 고르기`, () => { modal = { kind: 'choice' }; }, 'primary', true, '초월 갈매미맨 · 전설 위습 1 · 에픽 선택 위습 2 중 하나');
  if (G.choice) cy0 += 76;
  const rowsN = Math.ceil(items.length / 2), gw = (w - 36) / 2, gh = Math.min(96, (h - 80 - 12 * (rowsN - 1) - cy0) / rowsN);
  items.forEach(([icon, nm, fn, style, sub], i) => menuButton(x + 12 + (i % 2) * (gw + 12), y + 64 + cy0 + Math.floor(i / 2) * (gh + 12), gw, gh, icon, nm, fn, style, sub));
}
// 지금 조합할 수 있는 조합법 수(0.5초마다 다시 셈)
let readyCache = { t: 0, n: 0, g: null };
function readyCount() {
  const now = performance.now();
  if (readyCache.g !== G || now - readyCache.t > 500) readyCache = { t: now, g: G, n: E.RECIPES.filter((r) => canMake(r)).length };
  return readyCache.n;
}
// 조합할 수 있는가: 재료가 있고, 상위(제한~영원)면 2마리 제한에 걸리지 않을 때(사용자 요청 2026-11-08)
const canMake = (r, keep = 0) => !!E.recipeUnits(G, r, keep) && !E.topBlocked(G, r);
function have(r, cnt) { const left = { ...cnt }; let n = 0; for (const id of r.in) if ((left[id] ?? 0) > 0) { left[id]--; n++; } return n; }
// 시너지 배지(2026-11-21): 속성 첫 글자 동그라미. 켜진 속성은 색을 채우고 아래에 단계 점, 안 켜지면 흐린 테두리만
function synBadge(bx, by, el, sv, sc = 1) {
  const on = sv?.lv > 0, rgb = ELEM_COL[el] ?? '220,220,220', r = (on ? 9 : 7) * sc;
  ctx.beginPath(); ctx.arc(bx, by, r, 0, Math.PI * 2); ctx.fillStyle = on ? `rgb(${rgb})` : 'rgba(7,19,28,.7)'; ctx.fill();
  ctx.lineWidth = on ? 2 : 1.5; ctx.strokeStyle = on ? '#07131c' : `rgba(${rgb},.75)`; ctx.stroke();
  text(E.SYN_NAME[el].slice(0, 1), bx, by + 0.5, (on ? 11 : 9) * sc, on ? '#07131c' : `rgba(${rgb},.9)`, 'center');
  if (on) for (let q = 0; q < sv.lv; q++) { ctx.beginPath(); ctx.arc(bx - (sv.lv - 1) * 3.5 * sc + q * 7 * sc, by + r + 4 * sc, 2.2 * sc, 0, Math.PI * 2); ctx.fillStyle = `rgb(${rgb})`; ctx.fill(); }
}
function drawUnitPanel(y0, u) {
  const info = E.UNIT[u.id], k = E.kit(u.id);
  drawFit(unitImg(u.id), 18, y0 + 8, 68, 70);
  hit(18, y0 + 8, 68, 70, () => showInfo(u.id));
  fitText(info.name, 94, y0 + 22, 150, 21, C.text);
  ctx.font = `21px ${FONT}`;
  let cx = 94 + Math.min(150, ctx.measureText(info.name).width) + 8;
  cx += chip(E.GRADE_NAME[info.grade], cx, y0 + 11, GRADE_COLOR[info.grade]) + 6;
  cx += chip(k.special ? '조합 재료' : `${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx, y0 + 11, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag) + 6;
  if (!k.special && E.RULES.synergy) {   // 2026-11-21: 시너지 칩(에픽 이상만 셈)
    const sv = E.synergyOf(G)[k.elem], nm = E.SYN_NAME[k.elem];
    const st = k.tier < 3 ? `${nm} · 시너지 안 셈` : sv.lv ? `${nm} ${sv.lv}단계·${sv.n}종` : `${nm} ${sv.n}/2종`, lim = (E.tierOf(u.id) >= 3 && u.got ? W - 228 : W - 118);
    if (k.tier >= 3) synBadge(78, y0 + 66, k.elem, sv, 1.15);   // 그림 오른쪽 아래(좁은 화면에서도 보이게)
    ctx.font = `14px ${FONT}`; if (cx + ctx.measureText(st).width + 14 <= lim) chip(st, cx, y0 + 11, k.tier >= 3 && sv.lv ? `rgb(${ELEM_COL[k.elem]})` : C.muted);
  }
  const up = E.upgMul(G, k.tier), gp = E.gapOf(G, k);
  if (k.special) fitText('공격하지 않아요 · 초월 등급 조합 재료', 94, y0 + 50, W - 210, 15, C.muted);
  else fitText(`공격 ${Math.round(k.dmg * up)} · ${gp * (E.RULES.atkSpeed ?? 1) <= 0.6 ? '빠름' : gp * (E.RULES.atkSpeed ?? 1) >= 1.1 ? '느림' : '보통'}(${gp.toFixed(2)}초) · 사거리 ${k.range}`, 94, y0 + 50, W - 210, 15, C.muted);
  tipLines(E.kitTips(k), 94, y0, W - 210, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag);
  button(W - 112, y0 + 10, 90, 40, '닫기', () => { selected = []; }, 'quiet');
  // 에픽 리롤(2026-11-12): 보상으로 받은 에픽만, 리롤권이 있을 때
  if (E.tierOf(u.id) >= 3 && u.got && info.role !== E.SPECIAL_ROLE) {   // 에픽 이상(2026-11-12)
    const ok = E.canReroll(G, u);
    button(W - 222, y0 + 10, 104, 40, u.rerolled ? '리롤 끝' : `리롤 (${G.rerolls ?? 0})`, () => { if (u.rerolled) return say('한 번 리롤한 갈매미는 다시 리롤할 수 없어요.'); const was = info.name, nu = E.rerollUnit(G, u.uid); if (nu) say(`리롤: ${was} → ${E.UNIT[nu.id].name}`); else say('리롤권이 없어요. 10웨이브 보스마다 1개 받아요.'); }, ok ? 'primary' : 'normal');   // 누르면 다른 에픽으로(리롤권 1개)
  }
  const list = E.recipesUsing(u.id).map((r) => ({ r, ok: canMake(r, u.uid) })).sort((a, b) => (b.ok - a.ok) || (E.tierOf(a.r.out) - E.tierOf(b.r.out)));
  const sgOn = !list.length && E.tierOf(u.id) >= E.TOP_TIER && E.SIGNATURE[u.id];   // 2026-11-19: 최상위는 조합법 자리에 고유 기술 버튼
  text(sgOn ? '고유 기술 직접 쓰기' : list.length ? '이 갈매미로 만들 수 있는 것' : '이 갈매미가 들어가는 조합법이 없어요', 22, y0 + 100, 15, C.gold);
  const cnt = E.countOnField(G);
  // 버튼 6개는 3개씩 두 줄(사용자 요청 2026-11-08: 휴대폰에서 작음), 조합법 줄은 남는 높이만큼
  const ex = Math.max(0, PBOT - H), BH = Math.min(70, 44 + ex * 0.15), b6 = (W - 44 - 12) / 3;
  const by2 = PBOT - 16 - BH, by1 = by2 - 6 - BH, rowsN = Math.max(1, Math.min(4, Math.floor((by1 - 6 - (y0 + 112)) / 50)));
  // 2026-11-11 사용자 지적(태초가 들어가는 마딜이 없다고 보임 — 칸이 한 줄뿐이라 둘째 조합법이 「조합법 +1」 뒤에 숨음):
  // 줄 수보다 조합법이 많으면 작은 카드(그림·이름·물딜/마딜)로 나란히 보여 줌. 누르면 그 조합법 자리로 조합법 창을 엶
  if (sgOn) {
    const sg = E.SIGNATURE[u.id], left = E.sigCastLeft(G, u), ok = E.canCast(G, u), wa = E.WISP_ACTIVE[u.id], fm = !!E.FORM_OF[u.id] || !!wa, bw3 = fm ? (W - 36) * 0.58 : W - 36, yy = y0 + 112, bh3 = Math.max(44, Math.min(66, by1 - 8 - yy));
    button(18, yy, bw3, bh3, `「${sg.name}」 쓰기`, () => { const r = E.castSig(G, u.uid); if (r === true) sfx('boss'); else if (r === 'none') say('사거리 안에 적이 없어요.'); else if (left > 0) say(`${Math.ceil(left)}초 뒤에 쓸 수 있어요.`); }, ok ? 'primary' : 'normal', ok, left > 0 ? `${Math.ceil(left)}초 뒤에 다시 쓸 수 있어요` : (fm ? `위력 ×${E.RULES.sigCastMul} · ${E.RULES.sigCastCd}초` : `지금 쓰면 위력 ×${E.RULES.sigCastMul} · 쿨타임 ${E.RULES.sigCastCd}초(갈매미마다 따로)`));
    if (left > 0) box(18, yy + bh3 - 6, bw3 * (1 - left / E.RULES.sigCastCd), 6, C.gold, null, 3);
    if (wa) {   // 2026-11-21 사용자 요청: 가루다 위습 찾기는 눌러서(실패 있음, 전설은 아주 드묾)
      const wl = E.wispCastLeft(G, u), wx = 18 + bw3 + 8, ww = W - 36 - bw3 - 8;
      button(wx, yy, ww, bh3, '위습 찾기', () => { const r = E.wispCast(G, u.uid); if (!r) { if (wl > 0) say(`${Math.ceil(wl)}초 뒤에 쓸 수 있어요.`); return; } if (r.ok) { sfx('wisp'); say(`${E.GRADE_NAME[r.grade]} 위습을 찾았어요!`); } else say('위습 찾기 실패… 다음 기회에.'); }, E.canWispCast(G, u) ? 'primary' : 'normal', E.canWispCast(G, u), wl > 0 ? `${Math.ceil(wl)}초 뒤` : `성공 ${Math.round(wa.ok * 100)}% · ${wa.cd}초`);
      if (wl > 0) box(wx, yy + bh3 - 6, ww * (1 - wl / wa.cd), 6, C.gold, null, 3);
    } else if (fm) { const c = E.transformCost(), to = E.UNIT[E.FORM_OF[u.id]]; button(18 + bw3 + 8, yy, W - 36 - bw3 - 8, bh3, '각성 변신', () => { const r = E.transformUnit(G, u.uid); if (!r.ok) say(r.why); else sfx('boss'); }, 'normal', G.gold >= c, `새우 ${fmtN(c)} · 한 번 · 마법→물리`); }   // 2026-11-21
    else if (E.kit(u.id).goldScale) fitText(`패시브: 가진 새우 1000마다 공격력 +1% · 지금 +${Math.round(Math.min(E.RULES.goldScaleCap, G.gold / 1000 * 0.01) * 100)}%`, 22, yy + bh3 + 22, W - 44, 14, C.goldEdge);
  }
  const shown = sgOn ? 0 : list.length <= rowsN ? (list.forEach(({ r, ok }, i) => recipeRow(r, ok, 18, y0 + 112 + i * 50, W - 36, 46, cnt, u.uid)), list.length)
    : recipeCards(list, 18, y0 + 112, W - 36, 46, rowsN, u.id);
  const bx = (i) => 22 + (i % 3) * (b6 + 6), byOf = (i) => (i < 3 ? by1 : by2);
  button(bx(0), byOf(0), b6, BH, list.length > shown ? `조합법 +${list.length - shown}` : '조합법', () => openBook(null, u.id), 'normal');
  button(bx(1), byOf(1), b6, BH, '모으기', () => { selected = E.gather(G, u.uid); say(`같은 ${E.UNIT[u.id].name} ${selected.length}마리를 모았어요(고정한 것은 그 자리).`); }, 'normal');
  button(bx(2), byOf(2), b6, BH, u.lock ? '고정 풀기' : '위치 고정', () => { E.setLock(G, [u.uid], !u.lock); say(u.lock ? '위치를 고정했어요. 끌어도 안 움직여요.' : '고정을 풀었어요.'); }, u.lock ? 'primary' : 'normal');
  button(bx(3), byOf(3), b6, BH, '창고로', () => { E.store(G, [u.uid]); selected = []; say(`${E.UNIT[u.id].name}을(를) 창고에 넣었어요(조합 재료로는 쓰여요).`); }, 'normal');
  button(bx(4), byOf(4), b6, BH, '탐사', () => sendSelected([u]), 'normal', E.missionOpen(G));
  if (k.special || k.tier >= E.TOP_TIER) button(bx(5), byOf(5), b6, BH, '판매 불가', () => {}, 'normal', false);   // 초월 갈매미맨·초불영제(2026-11-11 사용자 요청: 상위 판매 버튼 제거)
  else button(bx(5), byOf(5), b6, BH, `판매+${k.sell}`, () => { E.sell(G, u.uid); selected = []; }, 'danger');
}
function drawGroupPanel(y0, sel) {
  let p = 0, m = 0;
  for (const u of sel) { const k = E.kit(u.id); if (k.type === 'phys') p++; else m++; }
  text(`갈매미 ${sel.length}마리 고름`, 22, y0 + 24, 20, C.text);
  text(`물딜 ${p} · 마딜 ${m}`, 22, y0 + 52, 15, C.muted);
  button(W - 112, y0 + 10, 90, 40, '해제', () => { selected = []; }, 'quiet');
  const ex = Math.max(0, PBOT - H), BH = 46 + ex * 0.25, by = PBOT - 18 - BH;
  // 그림 크게(누르기 쉽게, 사용자 요청 2026-11-08): 두 줄이 들어가는 만큼 크게(최대 84), 칸 수는 줄여도 됨
  const avail = by - 8 - (y0 + 62), sz = Math.max(60, Math.min(84, Math.floor(avail / 2) - 8));
  const per = Math.floor((W - 44 + 8) / (sz + 8)), rows = Math.max(1, Math.floor((avail + 8) / (sz + 8))), shown = per * rows;
  sel.slice(0, shown).forEach((u, i) => {
    const x = 22 + (i % per) * (sz + 8), y = y0 + 62 + Math.floor(i / per) * (sz + 8);
    box(x, y, sz, sz, 'rgba(11,30,42,.85)', GRADE_COLOR[E.UNIT[u.id].grade], 6, 1.5);
    drawFit(unitImg(u.id), x + 2, y + 2, sz - 4, sz - 4);
    hit(x, y, sz, sz, () => { selected = [u.uid]; });   // 누르면 그 갈매미만 고르기
  });
  if (sel.length > shown) text(`외 ${sel.length - shown}마리`, W - 124, y0 + 30, 14, C.muted, 'right');
  const b4 = (W - 44 - 18) / 4, allLock = sel.every((u) => u.lock);
  button(22, by, b4, BH, `탐사 보내기 (${sel.length})`, () => sendSelected(sel), 'normal', E.missionOpen(G));
  button(22 + (b4 + 6), by, b4, BH, `창고로 (${sel.length})`, () => { const n = E.store(G, sel.map((u) => u.uid)); selected = []; say(`${n}마리를 창고에 넣었어요.`); }, 'normal');
  button(22 + 2 * (b4 + 6), by, b4, BH, allLock ? `고정 풀기 (${sel.length})` : `위치 고정 (${sel.length})`, () => { const n = E.setLock(G, sel.map((u) => u.uid), !allLock); say(allLock ? `${n}마리 고정을 풀었어요.` : `${n}마리 위치를 고정했어요.`); }, allLock ? 'primary' : 'normal');
  button(22 + 3 * (b4 + 6), by, b4, BH, '고르기 해제', () => { selected = []; }, 'quiet');
}
function sendSelected(list) {
  const n = E.sendAway(G, list.map((u) => u.uid));
  if (n) { say(`${n}마리를 외부탐사 「${E.REGIONS[G.missionDone].name}」에 보냈어요.`); selected = []; }
}
// 조합법 한 줄
// 특기 줄(2026-11-10 검토: 한 줄에 다 넣으려고 9px까지 줄어 휴대폰에서 읽기 어려웠음): 15px 한 줄에 들어가면 한 줄,
// 아니면 두 줄로 고르게 나눠(특기 단위로 끊음) 두 줄 모두 같은 크기 13~11px. 그래도 넘치면 11px에서 「…」(전체는 그림을 눌러 보는 정보 창에)
function tipLines(tips, x, y0, maxW, color) {
  const all = tips.join(' · ') || '기본 공격';
  ctx.font = `15px ${FONT}`; if (ctx.measureText(all).width <= maxW || tips.length < 2) { clipText(all, x, y0 + 72, maxW, 15, color); return; }
  ctx.font = `13px ${FONT}`; const wd = (l) => ctx.measureText(l.join(' · ')).width;
  let cut = 1; if (!tips[0].startsWith('고유 기술')) for (let i = 1; i < tips.length; i++) if (Math.max(wd(tips.slice(0, i)), wd(tips.slice(i))) < Math.max(wd(tips.slice(0, cut)), wd(tips.slice(cut)))) cut = i;   // 고유 기술은 첫 줄에 따로(뒤 패널티가 잘리지 않게)
  const l1 = tips.slice(0, cut).join(' · '), l2 = tips.slice(cut).join(' · ');
  let sz = 13; for (; sz > 11; sz--) { ctx.font = `${sz}px ${FONT}`; if (Math.max(ctx.measureText(l1).width, ctx.measureText(l2).width) <= maxW) break; }
  clipText(l1, x, y0 + 66, maxW, sz, color, 11); clipText(l2, x, y0 + 84, maxW, sz, color, 11);
}
function clipText(t, x, y, maxW, size, color, min = 9) {   // 왼쪽 정렬: min px(기본 9)까지 줄이고 그래도 길면 「…」
  for (let sz = size; sz >= min; sz--) { ctx.font = `${sz}px ${FONT}`; if (ctx.measureText(t).width <= maxW) { text(t, x, y, sz, color); return; } }
  let s = t; ctx.font = `${min}px ${FONT}`; while (s.length > 1 && ctx.measureText(s + '…').width > maxW) s = s.slice(0, -1);
  text(s + '…', x, y, min, color);
}
function wrapItems(head, items, cx, y, maxW, size, color, maxLines = 3) {   // 가운데 정렬로 「머리 + 항목 · 항목 …」을 항목 단위로 줄 나눔, 마지막 줄만 넘치면 줄임
  ctx.font = `${size}px ${FONT}`; const lines = []; let cur = head;
  for (const it of items) { const t = cur === head ? cur + it : `${cur} · ${it}`; if (ctx.measureText(t).width > maxW && cur !== head) { lines.push(cur); cur = it; } else cur = t; }
  lines.push(cur);
  const shown = lines.slice(0, maxLines); if (lines.length > maxLines) shown[maxLines - 1] = lines.slice(maxLines - 1).join(' · ');
  shown.forEach((l, i) => chipLabel(l, cx, y + i * (size + 5), maxW, size, color));
}
function chipLabel(t, cx, cy, maxW, size, color) {   // 좁은 재료 칸: 9px까지 줄이고, 그래도 길면 「…」(옆 칸과 겹치지 않게)
  for (let sz = size; sz >= 9; sz--) { ctx.font = `${sz}px ${FONT}`; if (ctx.measureText(t).width <= maxW) { text(t, cx, cy, sz, color, 'center'); return; } }
  let s = t; ctx.font = `9px ${FONT}`; while (s.length > 1 && ctx.measureText(s + '…').width > maxW) s = s.slice(0, -1);
  text(s + '…', cx, cy, 9, color, 'center');
}
function recipeCards(list, x, y, w, h, rows, using) {   // 작은 조합법 카드: 줄마다 왼쪽부터 채우고, 들어간 개수를 돌려줌
  let n = 0;
  for (let row = 0; row < rows && n < list.length; row++) {
    let cx = x; const cy = y + row * (h + 4);
    while (n < list.length) {
      const { r, ok } = list[n], out = E.UNIT[r.out], k = E.kit(r.out);
      const shut = !E.topIsOpen(G, r.out), sub = `${k.type === 'phys' ? '물딜' : '마딜'} · ${shut ? '이번 판 닫힘' : ok ? '조합 가능' : E.GRADE_NAME[out.grade]}`;
      ctx.font = `14px ${FONT}`; const nw = ctx.measureText(out.name).width; ctx.font = `12px ${FONT}`;
      const cw = Math.min(190, Math.max(96, h + 12 + Math.max(nw, ctx.measureText(sub).width)));
      if (cx + cw > x + w + 0.5 && cx > x) break;
      const ww = Math.min(cw, x + w - cx);
      box(cx, cy, ww, h, ok ? 'rgba(43,122,98,.25)' : 'rgba(11,30,42,.7)', ok ? '#2b7a62' : GRADE_COLOR[out.grade], 8, ok ? 2 : 1.5);
      drawFit(unitImg(r.out), cx + 3, cy + 3, h - 6, h - 6);
      fitText(out.name, cx + h + 2, cy + 15, ww - h - 8, 14, GRADE_COLOR[out.grade]);
      fitText(sub, cx + h + 2, cy + 33, ww - h - 8, 12, shut ? C.bad : ok ? C.good : k.type === 'phys' ? C.phys : C.mag);
      if (shut) box(cx, cy, ww, h, 'rgba(7,19,28,.45)', null, 8);
      hit(cx, cy, ww, h, () => openBook(r.out, using));
      cx += ww + 6; n++;
    }
  }
  return n;
}
function recipeRow(r, ok, x, y, w, h, cnt, keepUid = 0) {
  const out = E.UNIT[r.out], gi = G.goals.indexOf(r.out);
  hit(x, y, w, h, () => showInfo(r.out));   // 조합 버튼 말고 줄을 누르면 간단 설명(사용자 요청 2026-11-08)
  box(x, y, w, h, ok ? 'rgba(43,122,98,.25)' : 'rgba(11,30,42,.7)', gi >= 0 ? GOAL_COLOR[gi] : ok ? '#2b7a62' : 'rgba(145,122,76,.4)', 8, gi >= 0 ? 2.5 : 1.5);
  drawFit(unitImg(r.out), x + 4, y + 2, 40, h - 4);
  // 줄 높이 52 이상(조합법 창): 이름·등급·게이지 세 줄. 낮은 줄(정보 창 목록): 이름·게이지 두 줄
  const tall = h >= 52, ny = tall ? y + 14 : y + 14;
  fitText(out.name, x + 50, ny, 92, 16, GRADE_COLOR[out.grade]);
  const gn = goalNeed();
  if (gn[r.out]) { ctx.font = `16px ${FONT}`; goalDots(gn[r.out], x + 50 + Math.min(92, ctx.measureText(out.name).width) + 8 + (gn[r.out].length - 1) * 12, ny, 5); }   // 이름 바로 옆   // 목표에 필요한 중간 재료를 만드는 줄
  const shut = !E.topIsOpen(G, r.out);   // 2026-11-21: 이번 판에 닫힌 최상위
  if (tall) fitText(shut ? '이번 판 닫힘' : `${E.GRADE_NAME[out.grade]}${r.hidden ? ' · 히든 조합' : ''}`, x + 50, y + 31, 92, 12, shut ? C.bad : C.muted);
  // 재료 모인 정도 게이지(사용자 요청 2026-11-15): 금빛 = 진행(색 규칙), 다 모이면 초록
  // 기본 재료 기준(아래 재료까지). 기본 재료가 다 모이면 100%·초록(2026-11-15 사용자 요청) — 「조합」이 중간 재료부터 한 번에 만듦
  { const pg = progOf(r, cnt), direct = have(r, cnt) === r.in.length, n = pg.got;
    const fr = direct ? 1 : pg.got / pg.total, gy = tall ? y + h - 13 : y + h - 15, gw = 54;
    box(x + 50, gy, gw, 7, 'rgba(0,0,0,.5)', 'rgba(145,122,76,.55)', 3.5, 1);
    if (n > 0) box(x + 50, gy, Math.max(7, gw * fr), 7, fr >= 1 ? C.good : C.gold, null, 3.5);   // 다 모이면 초록(2026-11-15 사용자 요청)
    text(`${fr >= 1 ? 100 : Math.min(99, Math.floor(fr * 100))}%`, x + 142, gy + 4, 13, fr >= 1 ? C.good : n > 0 ? C.gold : C.muted, 'right'); }
  const left = { ...cnt };
  // 재료가 5개 이상(초불영제)이면 두 줄로 나눠 칸을 넓게(2026-11-10: 재료 6~7개라 이름이 너무 잘림)
  const x0 = x + 150, lines = r.in.length > 4 ? 2 : 1, perLine = Math.ceil(r.in.length / lines), ch = lines === 2 ? 19 : 24, fs = lines === 2 ? 12 : 14;
  const chipMax = (x + w - 112 - x0) / perLine - 4;
  r.in.forEach((id, idx) => {
    const ln = Math.floor(idx / perLine), cy0 = lines === 2 ? y + h / 2 - ch - 1 + ln * (ch + 2) : y + h / 2 - ch / 2;
    if (idx % perLine === 0) recipeRow.cx = x0;
    const cx = recipeRow.cx;
    const hasIt = (left[id] ?? 0) > 0;
    if (hasIt) left[id]--;
    ctx.font = `${fs}px ${FONT}`;
    const t = E.UNIT[id].name, tw = Math.min(chipMax, ctx.measureText(t).width + 12);
    hit(cx, cy0, tw, ch, () => showInfo(id));
    // 재료 칸: 테두리·글자 = 그 재료의 등급 색(사용자 요청 2026-11-08), 가진 것은 초록 바탕
    const gc = GRADE_COLOR[E.UNIT[id].grade];
    box(cx, cy0, tw, ch, hasIt ? 'rgba(111,208,138,.22)' : 'rgba(0,0,0,.35)', gc, 6, hasIt ? 2 : 1.3);
    chipLabel(t, cx + tw / 2, cy0 + ch / 2, tw - 6, fs, gc);
    if (gn[id]) goalDots(gn[id], cx + tw - 4, cy0 + 1, lines === 2 ? 3.5 : 4.5);   // 목표 재료면 그 목표 색 동그라미
    recipeRow.cx = cx + tw + 4;
  });
  const bx = x + w - 108;
  button(bx, y + 5, 40, h - 10, gi >= 0 ? '★' : '☆', () => { const on = E.toggleGoal(G, r.out); say(on ? `목표: ${out.name} (재료에 색 테두리)` : '목표에서 뺐어요'); }, 'quiet');
  const capped = E.topBlocked(G, r);
  button(bx + 46, y + 5, 58, h - 10, capped ? (E.topBlockWhy(G, r).includes('닫힌') ? '닫힘' : '불가') : '조합', () => { const nu = combineChain(r, keepUid); if (nu) { selected = [nu.uid]; say(`${out.name} 완성!${nu.chain ? ` (중간 조합 ${nu.chain}번 포함)` : ''}`); delete nu.chain; } }, 'primary', ok || canChain(r));
  if (shut) box(x, y, w - 112, h, 'rgba(7,19,28,.45)', null, 8);   // 닫힌 최상위는 흐리게
}

// ── 모달 ──
// 역할(사용자 요청 2026-11-08: 등급별 말고 역할별로도). 한 갈매미가 여러 역할일 수 있음
const ROLES = [['all', '전체'], ['phys', '물리딜러'], ['mag', '마법딜러'], ['shred', '방깍'], ['mshred', '마방깍'], ['slow', '이감'], ['stun', '스턴'], ['explore', '탐사'], ['etc', '기타']];
function rolesOf(id) {
  const k = E.kit(id), r = [];
  if (k.job === '주력') r.push(k.type === 'phys' ? 'phys' : 'mag');
  if (k.shred) r.push('shred');
  if (k.mshred) r.push('mshred');
  if (k.slow) r.push('slow');
  if (k.stun || k.astun) r.push('stun');
  if (k.explore) r.push('explore');   // 탐사 특기(물려받은 상위 포함, 2026-11-12 사용자 요청)
  if (!r.length || (k.job !== '주력' && !k.shred && !k.mshred && !k.slow && !k.stun && !k.astun && !k.explore)) r.push('etc');
  return r;
}
let bookRoles = new Set();   // 역할 칩 여러 개(2026-11-10 사용자 요청: 이감+방깍처럼 둘 다 가진 것 찾기). 비어 있으면 전체, 여러 개면 모두 가진 것만
// 조합법 찾기(사용자 요청 2026-11-08): 만들 수 있는 것만 · 재료 많이 모인 순 · 이름 검색(캔버스 위에 실제 입력칸을 겹쳐 띄움)
const bookOpt = { ready: false, near: false, q: '' };
let bookSearchRect = null;
const searchEl = document.createElement('input');
searchEl.type = 'search'; searchEl.placeholder = '이름 검색'; searchEl.autocomplete = 'off'; searchEl.enterKeyHint = 'search';
Object.assign(searchEl.style, { position: 'fixed', display: 'none', zIndex: 5, boxSizing: 'border-box', border: '1.5px solid #917a4c', borderRadius: '8px', background: '#0b1e2a', color: '#f5ead0', padding: '0 10px', outline: 'none', fontFamily: 'inherit' });
searchEl.addEventListener('input', () => { bookOpt.q = searchEl.value.trim(); if (modal?.kind === 'book') modal.scroll = 0; });
document.body.appendChild(searchEl);
function placeSearch() {   // 매 장면: 조합법 창이 열려 있을 때만 입력칸을 그 자리에
  const R = modal?.kind === 'book' && scene === 'play' && !info ? bookSearchRect : null;
  if (!R) { if (searchEl.style.display !== 'none') { searchEl.style.display = 'none'; searchEl.blur(); } return; }
  const c = cv.getBoundingClientRect(), k = c.width / VW;
  Object.assign(searchEl.style, { display: 'block', left: `${c.left + R.x * k}px`, top: `${c.top + R.y * k}px`, width: `${R.w * k}px`, height: `${R.h * k}px`, fontSize: `${Math.max(12, 15 * k * (R.k ?? 1))}px` });
}
function openBook(focus = null, using = null) { modal = { kind: 'book', scroll: 0, scrollable: true, focus, using }; }
// 가로 화면: 창은 오른쪽 위(「이번 웨이브 피해」 자리, 0~664)에 열고 아래 메뉴는 그대로 보이게(사용자 요청 2026-11-08).
// 일시정지·결과·친구 판 크게 보기만 오른쪽 열 전체
const FULL_MODAL = new Set(['pause', 'over', 'peer']);
const topModal = () => scene === 'play' && modal && !FULL_MODAL.has(modal.kind);   // 세로 화면도 아래 메뉴 유지(사용자 요청 2026-11-08)
const MENU_Y = 664;
function modalRect() {
  if (landscape && (topModal() || (info && scene === 'play'))) return { x: 14, y: 8, w: W - 28, h: MENU_Y - 12 };
  if (!landscape && topModal()) return { x: 14, y: 100, w: W - 28, h: MENU_Y - 104 };   // 세로: 위 정보 줄 아래 ~ 메뉴 위
  return { x: 14, y: 100, w: W - 28, h: (landscape ? H : PBOT) - 108 };
}
function shadeAll(a = 0.72) {
  const y0 = topModal() && !landscape ? 96 : 0, hh = topModal() ? MENU_Y : landscape ? H : PBOT;
  ctx.fillStyle = `rgba(3,9,14,${a})`;
  if (wide && scene === 'play' && !topModal() && OS === 1 && OX > 0) { ctx.fillRect(SIDE_X - OX, 0, VW - SIDE_X, H); hit(SIDE_X - OX, 0, VW - SIDE_X, H, () => {}); return; }   // 넓은 화면의 일시정지·결과 창: 오른쪽 전체
  ctx.fillRect(0, y0, W, hh - y0); hit(0, y0, W, hh - y0, () => {});
}
// 상위 조합식 웹페이지를 새 탭으로(팝업이 막히면 이 탭에서 이동 — 판은 이어하기로 저장됨)
function openRecipePage() { const w = window.open('/defense/recipes', '_blank'); if (w) w.opener = null; else { saveNow(); location.href = '/defense/recipes'; } }   // 'noopener'를 주면 늘 null이 돌아와 쓰지 않음
function drawBook() {
  shadeAll(0.35);   // 게임은 계속 흐름(위 정보 줄이 보이게)
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  fitText(modal.using ? `${E.UNIT[modal.using].name} 재료` : '조합법', x + 18, y + 30, 120, 20, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  bookSearchRect = { x: OX + (x + 144) * OS, y: OY + (y + 12) * OS, w: (w - 144 - 108) * OS, h: 36 * OS, k: OS };   // 검색 입력칸 자리(실제 입력칸은 placeSearch가 겹침)
  box(x + 144, y + 12, w - 144 - 108, 36, '#0b1e2a', C.edge, 8, 1.5);
  // 보기 옵션 2개(「조합식 전체 보기」는 2026-11-12 사용자 요청으로 뺌)
  const tw2 = (w - 24 - 6) / 2;
  [['ready', '만들 수 있는 것만'], ['near', '재료 많이 모인 순']].forEach(([key, nm], i) => {
    const on = bookOpt[key], bx = x + 12 + i * (tw2 + 6);
    box(bx, y + 90, tw2, 28, on ? 'rgba(111,208,138,.16)' : 'rgba(11,30,42,.8)', on ? '#6fd08a' : 'rgba(145,122,76,.45)', 8, on ? 2 : 1);
    fitText(`${on ? '✓ ' : ''}${nm}`, bx + tw2 / 2, y + 104, tw2 - 8, 13, on ? C.good : C.muted, 'center');
    hit(bx, y + 90, tw2, 28, () => { bookOpt[key] = !bookOpt[key]; modal.scroll = 0; });
  });
  // 역할 칩: 누르면 켜고 끔(여러 개면 모두 가진 결과만), 「전체」는 모두 끔
  const cw = (w - 24 - (ROLES.length - 1) * 4) / ROLES.length;
  ROLES.forEach(([key, nm], i) => {
    const on = key === 'all' ? !bookRoles.size : bookRoles.has(key), bx = x + 12 + i * (cw + 4);
    box(bx, y + 54, cw, 30, on ? 'rgba(243,216,158,.18)' : 'rgba(11,30,42,.8)', on ? C.goldEdge : 'rgba(145,122,76,.45)', 8, on ? 2 : 1);
    fitText(nm, bx + cw / 2, y + 69, cw - 6, 13, on ? C.goldEdge : C.muted, 'center');
    hit(bx, y + 54, cw, 30, () => { if (key === 'all') bookRoles.clear(); else if (bookRoles.has(key)) bookRoles.delete(key); else bookRoles.add(key); modal.scroll = 0; });
  });
  const top = y + 126, viewH = h - 136, cnt = E.countOnField(G), q = bookOpt.q;
  let rows = E.RECIPES.filter((r) => (!modal.using || r.in.includes(modal.using)) && (!bookRoles.size || [...bookRoles].every((k) => rolesOf(r.out).includes(k)))
    && (!q || E.UNIT[r.out].name.includes(q) || r.in.some((id) => E.UNIT[id].name.includes(q))));
  rows = rows.map((r) => { const pg = progOf(r, cnt); return { r, ok: canMake(r), frac: have(r, cnt) === r.in.length ? 1 : pg.got / pg.total }; });   // 게이지와 같은 값으로 정렬
  if (bookOpt.ready) rows = rows.filter((it) => it.ok);
  const items = [];
  if (bookOpt.near) {   // 재료가 많이 모인 순(등급 묶음 없이)
    rows.sort((a, b) => (b.frac - a.frac) || (E.tierOf(b.r.out) - E.tierOf(a.r.out)));
    items.push(...rows);
  } else {
    rows.sort((a, b) => (E.tierOf(a.r.out) - E.tierOf(b.r.out)) || (E.topIsOpen(G, b.r.out) - E.topIsOpen(G, a.r.out)) || (b.ok - a.ok));   // 닫힌 최상위는 그 등급 맨 아래
    let lastG = '';
    for (const it of rows) { const g = E.UNIT[it.r.out].grade; if (g !== lastG) { items.push({ head: g }); lastG = g; } items.push(it); }
  }
  if (!items.length) fitText(q ? `「${q}」에 맞는 조합법이 없어요` : '해당하는 조합법이 없어요', W / 2, top + 50, w - 40, 15, C.muted, 'center');
  const rowH = (it) => (it.head ? 30 : 60);   // 게이지 줄이 들어가 52 → 60(2026-11-15)
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
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('외부탐사', x + 18, y + 30, 22, C.text);
  text(`완료 ${G.missionDone}/${E.REGIONS.length}장`, x + 120, y + 31, 15, G.missionDone ? C.good : C.muted);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(`${coop() ? '친구와 함께 · ' : ''}${E.RULES.storyDeadline}웨이브까지 ${E.REGIONS.length}장 · ★특기 피해 ×2~6`, x + 18, y + 64, w - 36, 13, E.storyLeft(G) && G.wave >= E.RULES.storyDeadline - 10 ? C.bad : C.muted);
  if (E.modeOf(G).noMission) { text('외부탐사 금지 모드예요', W / 2, y + 200, 20, C.bad, 'center'); fitText('이 모드에서는 스토리를 보낼 수 없어요. 판 수비만으로 버텨 보세요.', W / 2, y + 236, w - 40, 14, C.muted, 'center'); return; }
  // 지역 목록: 지금 장과 다음 장만(사용자 요청 2026-11-08: 목록이 길어 갈매미 칸을 늘리려고)
  const ly = y + 84, cmp = h < 800, lh = cmp ? 32 : 40;   // cmp: 가로 화면 위쪽 창(낮음)
  const shown = [G.missionDone, G.missionDone + 1].filter((k) => k < E.REGIONS.length);
  if (!shown.length) shown.push(E.REGIONS.length - 1);
  shown.forEach((k, i) => {
    const r = E.REGIONS[k];
    const yy = ly + i * lh, done = k < G.missionDone, cur = k === G.missionDone, open = cur && E.missionOpen(G);
    box(x + 12, yy, w - 24, lh - 6, cur ? 'rgba(43,122,98,.2)' : 'rgba(11,30,42,.6)', cur ? (open ? '#2b7a62' : C.edge) : 'rgba(145,122,76,.3)', 6, cur ? 2 : 1);
    const my = yy + (lh - 6) / 2;
    fitText(`${r.ch}장 ${r.name}`, x + 24, my, 220, cmp ? 14 : 15, done ? C.muted : C.text);
    fitText(done ? '완료 ✓' : open ? '진행 가능' : '잠김', x + 250, my, 80, 13, done ? C.good : open ? C.goldEdge : '#6f7f88');
    const rw = Object.entries(r.reward).map(([g, n]) => `${E.GRADE_NAME[g]}${n}`).join('·') + (r.pick ? ' + 선택 ' + Object.entries(r.pick).map(([g, n]) => `${E.GRADE_NAME[g]}${n}`).join('·') : '') + (r.ticket ? ` + 모집권${r.ticket}` : '') + (r.choice ? ' + 골라 받기' : r.unit ? ' + 초월★' : '');   // 초월★ = 초월 갈매미맨(아래 칸에 전체 이름)
    { const mw = (x + w - 24) - (x + 250 + 80 + 6); let sz = 13; ctx.font = `${sz}px ${FONT}`; while (sz > 9 && ctx.measureText(rw).width > mw) { sz--; ctx.font = `${sz}px ${FONT}`; } text(rw, x + w - 24, my, sz, C.gold, 'right'); }   // 「진행 가능」과 겹치지 않게
  });
  // 지금 지역
  const cy = ly + shown.length * lh + (cmp ? 4 : 10), PB = cmp ? 180 : 230, q = (v) => Math.round(v * PB / 230);   // 지금 지역 칸(낮은 창이면 줄임)
  if (G.missionDone >= E.REGIONS.length) { text('모든 지역을 되찾았어요!', W / 2, cy + 80, 20, C.goldEdge, 'center'); return; }
  const r = E.REGIONS[G.missionDone], open = E.missionOpen(G);
  const place = img(`${ART}/village/${r.img}.webp`);
  box(x + 12, cy, w - 24, PB, 'rgba(7,19,28,.8)', C.edge, 10);
  if (place) { ctx.save(); rr(x + 12, cy, w - 24, PB, 10); ctx.clip(); drawFit(place, x + 12, cy - 30, w - 24, PB, 0.28); ctx.restore(); }
  box(x + 18, cy + 6, 230, 26, 'rgba(7,19,28,.85)', null, 6);
  fitText(`${r.ch}장 · ${r.place}`, x + 26, cy + 19, 216, 15, C.goldEdge);
  const rwAll = [E.wispText(r.reward), r.pick ? E.pickText(r.pick) : '', r.ticket ? `모집권 ${r.ticket}` : '', r.gold ? `새우 ${r.gold}` : '', r.choice ? '+ 골라 받기(초월 갈매미맨·전설 위습 1·에픽 선택 2)' : r.unit ? E.UNIT[r.unit].name : ''].filter(Boolean).join(' · ');
  box(x + 18, cy + q(40), w - 36, 24, 'rgba(7,19,28,.85)', null, 6);
  clipText(`보상: ${rwAll}`, x + 26, cy + q(40) + 12, w - 52, 13, C.gold);   // 길면 줄이고 그래도 넘치면 「…」(검토에서 넘침 찾음)
  const t = E.missionTarget(G), sh = enemyImg(r.look);
  if (sh) drawFit(sh, W / 2 - q(50), cy + q(68), q(100), q(94));
  box(x + 40, cy + q(166), w - 80, 12, 'rgba(0,0,0,.6)', C.line, 6, 1);
  box(x + 40, cy + q(166), (w - 80) * Math.max(0, t.hp / t.max), 12, '#ff6b6b', null, 6);
  box(x + 24, cy + PB - 46, w - 48, 40, 'rgba(7,19,28,.88)', null, 8);
  fitText(`그림자 대장 체력 ${Math.max(0, Math.ceil(t.hp)).toLocaleString()} · 방어력 ${t.armor} · 마법방어 ${t.mr}`, W / 2, cy + PB - 34, w - 60, 14, C.text, 'center');
  const away = E.awayUnits(G);
  if (coop()) {
    const sh2 = E.missionShare(G), pct = (v) => `${Math.round(100 * v / t.max)}%`;
    fitText(`내 갈매미 ${away.length}마리 · 내 피해 ${pct(sh2.mine)} · 친구 피해 ${pct(sh2.peer)}`, W / 2, cy + PB - 16, w - 60, 14, sh2.mine + sh2.peer > 0 ? C.good : C.muted, 'center');
  } else fitText(away.length ? `탐사 중 ${away.length}마리` : '보낸 갈매미 없음', W / 2, cy + PB - 16, w - 60, 14, away.length ? C.good : C.muted, 'center');
  // 보낸 갈매미(낮은 창이면 한 줄만)
  const ay = cy + PB + (cmp ? 6 : 10);
  const AS = 74, aper = Math.floor((w - 28 + 6) / (AS + 6));   // 그림 크게(누르기 쉽게)
  const arows = Math.max(1, Math.floor((y + h - 64 - 8 - ay) / (AS + 6)));   // 남는 높이만큼 여러 줄
  if (away.length > aper * arows) text(`외 ${away.length - aper * arows}마리`, x + w - 20, y + h - 76, 13, C.muted, 'right');
  away.slice(0, aper * arows).forEach((u, i) => { const xx = x + 14 + (i % aper) * (AS + 6), yy = ay + Math.floor(i / aper) * (AS + 6); box(xx, yy, AS, AS, 'rgba(11,30,42,.85)', GRADE_COLOR[E.UNIT[u.id].grade], 6, 1.5); drawFit(unitImg(u.id), xx + 2, yy + 2, AS - 4, AS - 4); if (E.EXPLORE[u.id]) text('★', xx + AS - 8, yy + 10, 14, C.goldEdge, 'center');
    hit(xx, yy, AS, AS, () => { if (E.recall(G, [u.uid])) say(`${E.UNIT[u.id].name}이(가) 판으로 돌아왔어요.`); });   // 누르면 그 갈매미만 불러오기
  });
  // 판에 있는 탐사 특기 갈매미 안내(보내면 좋은 것)
  // 탐사 특기: 정해진 것 + 그 재료로 만든 초불영제(물려받음). 길면 줄이고 「…」(2026-11-10 특기가 늘어남)
  const sp = E.fieldUnits(G).filter((u) => E.kit(u.id).explore);
  // 2026-11-11 검토: 한 줄에 다 넣으려다 뒤가 「…」로 잘림 → 특기 단위로 줄을 나눔(최대 3줄)
  if (!away.length) wrapItems(sp.length ? `판에 탐사 특기 ${sp.length}마리: ` : `탐사 특기 ${Object.keys(E.EXPLORE).length}종: `,
    sp.length ? [...new Set(sp.map((u) => `${E.UNIT[u.id].name}(×${E.kit(u.id).explore})`))] : [...Object.entries(E.EXPLORE).map(([id, m]) => `${E.UNIT[id].name}×${m}`), '이들로 만든 초불영제도'],
    W / 2, ay + 18, w - 30, 13, sp.length ? C.goldEdge : C.muted, 3);
  const by = y + h - 64, sel = selUnits();
  button(x + 14, by, (w - 40) / 2, 50, sel.length ? `고른 ${sel.length}마리 보내기` : '판에서 먼저 고르기', () => { if (sel.length) { sendSelected(sel); } else modal = null; }, sel.length ? 'primary' : 'normal');
  button(x + 26 + (w - 40) / 2, by, (w - 40) / 2, 50, '모두 불러오기', () => { const n = E.recall(G); say(`${n}마리가 돌아왔어요.`); }, 'normal', away.length > 0);
}
// 등급별 강화 창: 5칸(커먼·언커먼·레어·에픽·전설 이상)
function drawUpgrade() {
  shadeAll(0.3);   // 게임은 계속 흐름(위 정보 줄이 보이게 아래쪽에)
  const R0 = modalRect(), x = R0.x + 6, y = R0.y, w = R0.w - 12, h = R0.h;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('등급별 강화', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const sh = img(`${ART}/menu/currency-shrimp.webp`);
  if (sh) drawFit(sh, x + 18, y + 56, 24, 24);
  text(`가진 새우 ${G.gold}`, x + 48, y + 69, 17, C.goldEdge);
  const have = Array(E.UPG_COUNT).fill(0);
  for (const u of E.fieldUnits(G)) have[E.upgGroup(E.tierOf(u.id))]++;
  const color = ['UNCOMMON', 'RARE', 'EPIC', 'LEGEND', 'LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL'];
  const rowH = Math.min(84, (h - 96) / E.UPG_COUNT), bh = rowH - 8;   // 창 높이에 맞춰 줄 높이
  for (let gi = 0; gi < E.UPG_COUNT; gi++) {
    const yy = y + 88 + gi * rowH, cost = E.upgradeCost(G, gi), col = GRADE_COLOR[color[gi]];
    box(x + 14, yy, w - 28, bh, 'rgba(11,30,42,.85)', col, 10, 2);
    text(E.UPG_NAME[gi], x + 30, yy + bh * 0.32, bh < 62 ? 17 : 19, col);
    const maxed = E.upgMaxed(G, gi);
    fitText(`${G.upg[gi]}/${E.RULES.upgMax}단계 · 공격력 +${Math.round(G.upg[gi] * E.RULES.upgPct * 100)}% · 공속 +${Math.round(G.upg[gi] * (E.RULES.upgSpd ?? 0) * 100)}% · 판에 ${have[gi]}마리`, x + 30, yy + bh * 0.72, w - 230, bh < 62 ? 13 : 15, maxed ? C.goldEdge : C.text);
    if (maxed) button(x + w - 28 - 150, yy + 6, 150, bh - 12, '최대(풀강)', () => {}, 'normal', false);
    else button(x + w - 28 - 150, yy + 6, 150, bh - 12, '강화', () => { const r = E.upgrade(G, gi); if (!r.ok && r.why) say(r.why); }, 'primary', G.gold >= cost, `새우 ${cost}`);
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
  text(`[${diffName(G.diff)}${G.mode !== 'normal' ? ` · ${E.modeOf(G).name}` : ''}] 웨이브 ${G.wave} · 처치 ${G.kills} · 경고 ${G.warn}`, W / 2, y + 84, 15, C.muted, 'center');
  button(x + 30, y + 118, w - 60, 56, '계속하기', () => { modal = null; }, 'primary');
  button(x + 30, y + 186, w - 60, 48, '처음부터 다시', () => start(undefined, G.mode, G.diff), 'normal');
  button(x + 30, y + 244, w - 60, 48, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
}
// 졌을 때 무엇이 모자랐는지(2026-11-11 검토: 패배 창이 원인을 알려 주지 않음). 진 순간의 판 상태에서 뽑아 가장 중요한 것부터 3개까지
function loseHints() {
  const out = [], f = E.fieldUnits(G), boss = G.enemies.find((e) => e.boss), armor = E.armorAt(G, G.wave), mr = E.mrAt(G, G.wave);
  let sh = 0, msh = 0, phys = 0, mag = 0;
  for (const u of f) { const k = E.kit(u.id); sh += k.shred ?? 0; msh += k.mshred ?? 0; if (k.type === 'phys') phys += k.dmg / k.gap; else mag += k.dmg / k.gap; }
  if (G.reason.includes('경고')) out.push('길 위의 적이 너무 많이 쌓였어요 → 범위·스플래시·연쇄 공격(라인 정리형)을 늘려요');
  if (boss) out.push(`보스 체력이 ${Math.max(1, Math.round(100 * boss.hp / boss.max))}% 남았어요`);
  if (boss && phys >= mag && sh < armor * 0.5) out.push(`방깍이 모자라요(보스 방어 ${armor} · 내 방깍 ${Math.round(sh)})`);
  if (boss && mag > phys && msh < mr * 0.5) out.push(`마방깍이 모자라요(보스 마법방어 ${mr} · 내 마방깍 ${Math.round(msh)})`);
  const away = E.awayUnits(G).length; if (away) out.push(`탐사에 ${away}마리가 나가 있었어요 → 보스 웨이브에는 불러와요`);
  const canUp = G.upg.findIndex((_, gi) => !E.upgMaxed(G, gi) && G.gold >= E.upgradeCost(G, gi));
  if (canUp >= 0) out.push(`새우 ${G.gold.toLocaleString('ko-KR')}을(를) 안 썼어요 → 강화할 수 있었어요`);
  const ws = E.WISP_GRADES.reduce((a, g) => a + (G.wisps[g] ?? 0), 0) + pickCount(); if (ws >= 3) out.push(`안 쓴 위습이 ${ws}개 있었어요`);
  if (G.reason.includes('스토리')) out.push('외부탐사를 50웨이브 전에 끝내야 해요 → 탐사 특기 갈매미를 보내요');
  return out.slice(0, 3);
}
function drawOver() {
  shadeAll(0.75);
  if (coop()) return drawOverCoop();
  const win = G.over === 'win', hints = win ? [] : (G.loseHints ??= loseHints()), hh = hints.length ? 16 + hints.length * 22 : 0;
  const x = 40, y = 240 - hh / 2, w = W - 80, h = 446 + hh;
  box(x, y, w, h, C.panel, win ? C.goldEdge : '#b4482a', 14, 2);
  text(win ? '방어 성공!' : '방어 실패', W / 2, y + 48, 28, win ? C.goldEdge : C.bad, 'center');
  ctx.save(); ctx.beginPath(); ctx.rect(x + 20, y + 70, w - 40, 64); ctx.clip(); wrap(G.reason, x + 30, y + 78, w - 60, 15, C.text); ctx.restore();
  text(win ? `${E.RULES.waves}웨이브 클리어${G.leftover != null ? ` · 남은 적 ${G.leftover}마리` : ''}` : `${G.wave}웨이브`, W / 2, y + 160, 22, win && G.leftover === 0 ? C.goodText ?? C.good : C.text, 'center');
  fitText(G.pointNews ?? `처치 ${G.kills} · 외부탐사 ${G.missionDone}곳 · 강화 ${G.upg.join('/')}`, W / 2, y + 196, w - 40, 15, G.pointNews ? C.good : C.muted, 'center');
  text(`[${diffName(G.diff)}${G.mode !== 'normal' ? ` · ${E.modeOf(G).name}` : ''}] 최고 기록 ${bestText(bestOf(G.mode, G.diff))}`, W / 2, y + 226, 15, C.gold, 'center');
  if (hh) { box(x + 20, y + 242, w - 40, hh - 6, 'rgba(180,72,42,.14)', 'rgba(255,154,133,.5)', 8, 1); hints.forEach((t, i) => fitText(`· ${t}`, x + 30, y + 258 + i * 22, w - 60, 14, '#ffd9cf')); }
  text('이번 조합', x + 24, y + 256 + hh, 13, C.muted);
  if (G.cosNews || G.bankNews) fitText(G.cosNews ?? G.bankNews, x + w - 24, y + 256 + hh, w - 130, 13, C.good, 'right');
  drawCompStrip(G.lastRec?.comp ?? compOf(G), x + 24, y + 268 + hh, w - 48, 34);
  button(x + 30, y + 318 + hh, w - 60, 54, '다시 하기', () => start(undefined, G.mode, G.diff), 'primary');
  button(x + 30, y + 382 + hh, w - 60, 44, '시작 화면으로', () => { scene = 'title'; modal = null; }, 'quiet');
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
  if (G.cosNews) fitText(G.cosNews, W / 2, y + 454, w - 40, 13, C.good, 'center');
}
function drawTitleBack() {   // 화면 전체 너비
  // 2026-11-11 사용자 의견(첫 화면이 너무 어둡고 칙칙함): 밝은 하늘·바다(먼 바다 그림) 위에 마을. 같이하기 대기실은 글자가 많아 조금 어둡게
  const sky = ctx.createLinearGradient(0, 0, 0, VH); sky.addColorStop(0, '#6fbdf0'); sky.addColorStop(0.45, '#bfe6fb'); sky.addColorStop(1, '#2f9fd6');
  ctx.fillStyle = sky; ctx.fillRect(0, 0, VW, VH);
  const sea = img(`${ART}/village/10-distant-bay.webp`);
  if (sea) { const s = Math.max(VW / sea.naturalWidth, VH / sea.naturalHeight); ctx.drawImage(sea, (VW - sea.naturalWidth * s) / 2, VH - sea.naturalHeight * s, sea.naturalWidth * s, sea.naturalHeight * s); }
  // 마을 그림은 뺌(2026-11-11 사용자 의견: 바다 위에 떠 있는 것처럼 보여 어색함)
  if (scene === 'lobby') { ctx.fillStyle = 'rgba(7,19,28,.55)'; ctx.fillRect(0, 0, VW, VH); }
  // 떠오르는 빛 알갱이(2026-11-10 사용자 의견: 첫 화면이 허전함). 효과 줄이기면 움직이지 않음
  const t = fxLite ? 0 : performance.now() / 1000;
  for (let k = 0; k < 28; k++) {
    const a = (k * 0.618034) % 1, sp = 14 + (k % 5) * 6, x = a * VW + Math.sin(t * 0.4 + k * 1.7) * 18, y = VH + 20 - ((t * sp + k * 97) % (VH + 40));
    const r = 1.4 + (k % 3) * 0.8, al = 0.25 + 0.25 * Math.sin(t * 1.3 + k);
    ctx.fillStyle = `rgba(255,236,190,${(al * 0.35).toFixed(3)})`; ctx.beginPath(); ctx.arc(x, y, r * 3, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = `rgba(255,236,190,${(al + 0.3).toFixed(3)})`; ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.fill();
  }
}
// 첫 화면 갈매미 줄: 꾸민 갈매미가 있으면 먼저(날개·오라 그대로), 나머지는 기본. 가운데가 가장 크고 숨 쉬듯 살짝 움직임
function drawTitleParade() {
  const ids = [...Object.keys(cosById)];
  for (const d of ['u59', 'u38', 'u45', 'u33', 'u40']) if (ids.length < 5 && !ids.includes(d)) ids.push(d);
  const order = ids.slice(0, 5).sort((a, b) => E.tierOf(b) - E.tierOf(a));   // 가장 높은 등급이 가운데
  const place = [order[3], order[1], order[0], order[2], order[4]];
  const t = fxLite ? 0 : performance.now() / 1000;
  place.forEach((id, k) => {
    if (!id) return;
    const mid = k === 2, sc = (mid ? 1.95 : 1.4) / usz(id), x = 70 + k * 100, y = 668 + (mid ? 10 : 0) + Math.sin(t * 1.8 + k * 1.3) * 2.5, z = usz(id);   // 2026-11-11: 규칙 상자를 뺀 자리에 크게(시작 버튼 바로 위)
    ctx.save(); ctx.translate(x, y); ctx.scale(sc, sc);
    const c = cosById[id];
    if (c) cosBack(c, 0, 0, z, k);
    drawUnitShape(id, 0, 0, 'rgba(243,216,158,.45)', 1.5);
    if (c) cosFront(c, 0, 0, z, k);
    ctx.restore();
  });
}
function titleText(s, x, y, size, top, bottom) {   // 금빛 그러데이션 + 은은한 빛
  ctx.save(); ctx.font = `${size}px ${FONT}`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  const g = ctx.createLinearGradient(0, y - size / 2, 0, y + size / 2); g.addColorStop(0, top); g.addColorStop(1, bottom);
  ctx.lineJoin = 'round'; ctx.shadowColor = 'rgba(8,30,52,.55)'; ctx.shadowBlur = 10; ctx.shadowOffsetY = 3;
  ctx.lineWidth = Math.max(5, size * 0.16); ctx.strokeStyle = '#12324d'; ctx.strokeText(s, x, y);   // 짙은 남색 테두리(밝은 하늘 위에서도 읽히게)
  ctx.shadowBlur = 0; ctx.shadowOffsetY = 0; ctx.fillStyle = g; ctx.fillText(s, x, y);
  ctx.restore();
}
// 지금 이용자 수(2026-11-11 사용자 요청: 첫 화면 오른쪽 아래에 아주 작게). 이 탭의 무작위 번호로 30초마다 신호(화면이 보일 때만). 서버가 없거나 실패하면 안 보임
let onlineN = 0;
const ONLINE_ID = (() => { try { let v = sessionStorage.getItem('gmm-defense-online'); if (!v || !/^[0-9a-f]{24}$/.test(v)) { v = [...crypto.getRandomValues(new Uint8Array(12))].map((b) => b.toString(16).padStart(2, '0')).join(''); sessionStorage.setItem('gmm-defense-online', v); } return v; } catch { return [...crypto.getRandomValues(new Uint8Array(12))].map((b) => b.toString(16).padStart(2, '0')).join(''); } })();
async function onlinePing() {
  if (document.visibilityState !== 'visible') return;
  try {
    const r = await fetch('/defense/online', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ id: ONLINE_ID }) });
    const j = r.ok ? await r.json() : null; onlineN = Number.isFinite(j?.online) ? j.online : 0;
  } catch { onlineN = 0; }
}
onlinePing(); setInterval(onlinePing, 30_000); document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') onlinePing(); });
function drawOnline() {
  if (onlineN <= 0) return;
  ctx.font = `11px ${FONT}`; const t = `지금 ${onlineN.toLocaleString('ko-KR')}명`, tw = ctx.measureText(t).width;
  const x = VW - 12, y = VH - 12;   // 화면 전체의 오른쪽 아래 구석(가로 화면이면 가운데 메뉴 칸 밖)
  box(x - tw - 18, y - 9, tw + 24, 18, 'rgba(9,26,40,.7)', null, 9);
  ctx.beginPath(); ctx.arc(x - tw - 8, y, 3, 0, Math.PI * 2); ctx.fillStyle = '#6fd08a'; ctx.fill();
  text(t, x, y, 11, '#dfe8ec', 'right');
}
function drawTitle() {
  titleText('갈매미맨', W / 2, 86, 30, '#f7e3b0', '#c9a463');
  titleText('랜덤 디펜스', W / 2, 136, 50, '#fff6d8', '#e0b866');
  // 제목 아래 장식 줄(가운데 마름모)
  ctx.strokeStyle = 'rgba(243,216,158,.5)'; ctx.lineWidth = 1.5; ctx.beginPath(); ctx.moveTo(W / 2 - 150, 172); ctx.lineTo(W / 2 - 12, 172); ctx.moveTo(W / 2 + 12, 172); ctx.lineTo(W / 2 + 150, 172); ctx.stroke();
  ctx.save(); ctx.translate(W / 2, 172); ctx.rotate(Math.PI / 4); ctx.fillStyle = C.goldEdge; ctx.fillRect(-4.5, -4.5, 9, 9); ctx.restore();
  box(28, 566, W - 56, 126, 'rgba(255,250,235,.82)', '#e9cf8f', 16, 2);   // 갈매미 행렬 받침(밝은 배경에 묻히지 않게)
  drawTitleParade();
  // 난이도(혼자 하기·도전 모드에 적용, 같이하기는 달인)
  box(28, 196, W - 56, 156, 'rgba(11,30,42,.9)', C.edge, 12);
  text('난이도', 44, 222, 17, C.gold);
  const dd = E.DIFFS[selDiff];
  const dw = (W - 56 - 24 - 4 * 6) / 5;
  E.DIFF_ORDER.forEach((d, i) => button(40 + i * (dw + 6), 238, dw, 52, E.DIFFS[d].name, () => { selDiff = d; best = bestOf('normal', d); try { localStorage.setItem(DIFF_KEY, d); } catch { /* 진행 */ } }, d === selDiff ? 'primary' : 'normal'));
  fitText(`라인 체력 ×${dd.line} · 보스 체력 ×${dd.boss} · 방어 ×${dd.armor} · 마법방어 ×${dd.mr}`, W / 2, 312, W - 80, 15, C.text, 'center');
  fitText(dd.legend40 ? '40웨이브에 전설 위습 1개' : '40웨이브 전설 위습 보상 없음', W / 2, 336, W - 80, 14, dd.legend40 ? C.goldEdge : C.muted, 'center');
  // 2026-11-11 유저 피드백(버튼이 너무 많음): 규칙 6줄·음악 안내 띠는 빼고(메뉴 「게임 방법」으로), 도전 모드는 메뉴로, 이어하기는 이어할 판이 있을 때만
  if (resumeMeta) button(40, 708, W - 80, 44, `이어하기 · ${resumeMeta.wave}웨이브 (${[diffName(resumeMeta.diff), resumeMeta.mode && resumeMeta.mode !== 'normal' ? E.MODES[resumeMeta.mode]?.name : ''].filter(Boolean).join('·')})`, resume, 'normal');
  button(40, 764, (W - 92) / 2, 72, '혼자 하기', () => start(), 'primary');
  button(52 + (W - 92) / 2, 764, (W - 92) / 2, 72, '친구랑 같이하기', () => { scene = 'lobby'; lobby.mode = 'menu'; lobby.msg = ''; lobby.note = ''; musicPlay(); }, 'normal', true, '코드로 방 들어가기');
  box(28, 848, W - 56, 32, 'rgba(9,26,40,.78)', null, 16);   // 밝은 바다 위에서 읽히게
  if (cloud.on) fitText(`최고 기록 ${bestText(best)} · ${cloud.nick ? `${cloud.nick}님 ` : ''}계정에 저장 중(다른 기기에서도 이어하기)`, W / 2, 864, W - 40, 14, C.good, 'center');
  else if (!cloud.can) fitText(`최고 기록 ${bestText(best)} · 기록·이어하기는 이 기기에만`, W / 2, 864, W - 40, 14, C.muted, 'center');
  else {
    fitText(`최고 기록 ${bestText(best)} · 로그인하면 다른 기기에서도 이어할 수 있어요 ›`, W / 2, 864, W - 40, 14, C.goldEdge, 'center');
    hit(20, 846, W - 40, 36, () => { saveNow(); location.href = '/login?next=/defense'; });
  }
  musicButton(W - 62, 14);
  button(14, 14, 92, 40, '≡ 메뉴', () => { modal = { kind: 'tmenu' }; }, 'normal');   // 기록·꾸미기·도전 모드·조합식·게임 방법(2026-11-11 버튼 줄이기)
  const lg = cloud.can && !cloud.on;
  if (lg) button(112, 14, 84, 40, '로그인', () => { saveNow(); location.href = '/login?next=/defense'; }, 'primary');
  if (cosOpen() > (stats.seen ?? 0)) { ctx.beginPath(); ctx.arc(100, 18, 6, 0, Math.PI * 2); ctx.fillStyle = C.bad; ctx.fill(); }   // 꾸미기 새 칸이 열렸으면 메뉴에 빨간 점
  box(W - 196, 22, 128, 24, 'rgba(9,26,40,.7)', null, 12); fitText(music.on ? `배경음악 ${Math.round(music.vol * 100)}%` : '배경음악 끔', W - 76, 34, 112, 13, '#e6eef2', 'right');
}
// 소리 설정 창: 배경음악 켜기·끄기, 음량(막대를 누르거나 −/+)
// 선택 위습 창: 등급 칸을 고르고, 그 등급 갈매미를 눌러 소환
function drawPick() {
  shadeAll(0.35);
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('선택 위습', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
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
    const tip = E.kitTips(k).find((t) => !t.includes('형(')) ?? (k.job === '주력' ? (k.line ? '라인 정리형' : '단일 공격형') : '기본 공격');   // 칸이 좁아 짧게
    fitText(tip, cx + cw / 2, cy + 156, cw - 10, 12, C.muted, 'center');
    if (n) hit(cx, cy, cw, ch, () => { const r = E.summonPick(G, id); if (r.ok) { say(`${u.name} 소환!`); if (!(G.picks[gr] > 0)) modal = null; } else if (r.why) say(r.why); });
  });
  if (!n) fitText(`${E.GRADE_NAME[gr]} 선택 위습이 없어요`, W / 2, y + h - 30, w - 40, 15, C.muted, 'center');
}
// 창고: 같은 갈매미끼리 묶어 보여 주고 꺼내기·팔기. 창고 것도 조합 재료로 쓰인다
function drawStorage() {
  shadeAll(0.3);
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  const list = E.storedUnits(G);
  text(`창고 ${list.length}마리`, x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  // 등급별 한 번에 넣기 + 자동(새로 부른 갈매미가 바로 창고로)
  text('등급별 한 번에 넣기', x + 18, y + 90, 15, C.gold);
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
    const noSell = k.special || k.tier >= E.TOP_TIER;   // 초월 갈매미맨·초불영제는 팔 수 없음
    text(`${E.GRADE_NAME[u.grade]} · ${noSell ? '판매 불가' : `판매 ${k.sell}`}`, x + 68, yy + 43, 12, C.muted);
    const bx = x + w - 12 - 4 - 3 * 82;
    button(bx, yy + 9, 76, rowH - 18, '1 꺼내기', () => { E.unstore(G, [gr.uids[0]]); }, 'normal');
    button(bx + 82, yy + 9, 76, rowH - 18, '모두', () => { E.unstore(G, gr.uids); say(`${u.name} ${gr.uids.length}마리를 꺼냈어요.`); }, 'normal');
    if (noSell) button(bx + 164, yy + 9, 76, rowH - 18, '판매 불가', () => {}, 'normal', false);
    else button(bx + 164, yy + 9, 76, rowH - 18, `1 팔기`, () => { E.sell(G, gr.uids[0]); }, 'danger');
  });
  ctx.restore();
  hits = hits.filter((hh, k) => k < before || (hh.y >= top && hh.y + hh.h <= top + viewH));
  if (modal.maxScroll > 0) { const bh = Math.max(40, viewH * viewH / total), by2 = top + (viewH - bh) * (modal.scroll / modal.maxScroll); box(x + w - 8, by2, 4, bh, 'rgba(243,216,158,.5)', null, 2); }
  button(x + 18, y + h - 52, w - 36, 42, '모두 꺼내기', () => { const n = E.unstore(G, list.map((u) => u.uid)); say(`${n}마리를 꺼냈어요.`); }, 'normal', list.length > 0);
}
// 도전 모드 고르기(첫 화면)
function drawRecords() {   // 난이도별 최고 기록(그때 조합) + 최근 판
  shadeAll();
  const x = 20, y = 70, w = W - 40, h = Math.min(VH - 100, 860);
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('기록', x + 18, y + 32, 24, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const day = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
  const res = (r) => (r.win ? '클리어' : `${r.wave}웨이브`);
  let yy = y + 66;
  text('난이도별 최고 기록 · 그때 조합', x + 18, yy, 15, C.gold); yy += 14;
  for (const d of E.DIFF_ORDER) {
    const r = bestComp[recKey('normal', d)];
    box(x + 12, yy, w - 24, 62, 'rgba(11,30,42,.8)', 'rgba(145,122,76,.35)', 8, 1);
    text(diffName(d), x + 24, yy + 18, 15, C.text);
    clipText(r ? `${res(r)} · ${day(r.at)}` : '아직 없음', x + 24, yy + 42, 72, 12, r ? C.goldEdge : C.muted);
    if (r) drawCompStrip(r.comp, x + 100, yy + 13, w - 124, 36);
    yy += 68;
  }
  yy += 10; text('최근 판', x + 18, yy, 15, C.gold); yy += 14;
  const room = Math.max(0, Math.floor((y + h - 12 - yy) / 62));
  if (!hist.length) text('끝난 판이 아직 없어요', x + 24, yy + 24, 14, C.muted);
  hist.slice(0, room).forEach((r) => {
    box(x + 12, yy, w - 24, 56, 'rgba(11,30,42,.6)', 'rgba(145,122,76,.25)', 8, 1);
    clipText(`${day(r.at)} ${diffName(r.diff)}${r.mode !== 'normal' ? `·${E.MODES[r.mode]?.name ?? ''}` : ''}${r.coop ? '·같이' : ''}`, x + 24, yy + 18, 72, 12, C.text);
    text(res(r), x + 24, yy + 38, 13, r.win ? C.good : C.muted);
    drawCompStrip(r.comp, x + 100, yy + 11, w - 124, 34);
    yy += 62;
  });
}
const COS_TABS = [['전설', ['LEGEND']], ['히든', ['HIDDEN']], ['초불영제', ['LIMITED', 'TRANSCEND', 'IMMORTAL', 'ETERNAL']]];   // 꾸미기는 전설·히든·초불영제만(2026-11-10 사용자 요청)
function openCustomize() {
  const t = cosmetic.slots[0]?.id ? COS_TABS.findIndex((q) => q[1].includes(E.UNIT[cosmetic.slots[0].id]?.grade)) : 0;
  modal = { kind: 'cosmetic', slot: 0, tab: Math.max(0, t) };
  stats.seen = cosOpen(); ls.set(STATS_KEY, JSON.stringify(stats));
}
function drawCustomize() {   // 꾸미기: 칸 고르기 → 유닛 고르기 → 날개·오라·색
  shadeAll();
  const x = 20, y = 70, w = W - 40, h = Math.min(VH - 100, 860), open = cosOpen(), pts = clearPts(), next = COS_SLOTS.find((n) => n > pts);
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('꾸미기', x + 18, y + 32, 24, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(`달인·장인 클리어 ${clearCount()}번 · 보상 ${pts}번(장인은 2번) · 칸 ${open}/${COS_SLOTS.length}${next ? ` · 다음 칸 ${next}번` : ''} · 포인트 ${pointsLeft()}점 · 모은 새우 ${fmtN(stats.bank ?? 0)}`, x + 18, y + 66, w - 36 - 190, 14, C.muted);
  button(x + w - 18 - 180, y + 50, 180, 34, `새우 ${fmtN(BANK_PER_PT)} → 1점`, () => { if ((stats.bank ?? 0) < BANK_PER_PT) return say(`모은 새우가 ${fmtN(BANK_PER_PT)} 있어야 해요(지금 ${fmtN(stats.bank ?? 0)}). 판이 끝날 때 남은 새우가 모여요.`); stats.bank -= BANK_PER_PT; stats.earned = (stats.earned ?? 0) + 1; ls.set(STATS_KEY, JSON.stringify(stats)); cloudSaveExtra(); say(`꾸미기 포인트 +1(지금 ${pointsLeft()}점)`); }, 'normal', (stats.bank ?? 0) >= BANK_PER_PT);   // 2026-11-16 새우 저금 → 포인트
  // 칸 8개
  const sw = (w - 24 - 7 * 6) / 8;
  COS_SLOTS.forEach((need, i) => {
    const sx = x + 12 + i * (sw + 6), sy = y + 82, on = i < open, cur = modal.slot === i, c = cosmetic.slots[i];
    box(sx, sy, sw, 62, on ? (cur ? 'rgba(243,216,158,.16)' : 'rgba(11,30,42,.85)') : 'rgba(11,30,42,.45)', cur ? C.goldEdge : on ? C.edge : '#33434d', 8, cur ? 2.5 : 1.2);
    if (!on) {   // 잠긴 칸: 클리어로 열거나, 첫 잠긴 칸은 포인트로 열기(누르면)
      drawLockMark(sx + sw / 2, sy + 22); fitText(`${need}번`, sx + sw / 2, sy + 42, sw - 6, 11, '#7d8c94', 'center');
      if (i === open) { fitText(`${COS_COST.slot}점`, sx + sw / 2, sy + 55, sw - 6, 11, pointsLeft() >= COS_COST.slot ? C.good : '#7d8c94', 'center'); hit(sx, sy, sw, 62, () => buyCos('slot')); }
      return;
    }
    if (c?.id) { drawFit(unitImg(c.id), sx + 5, sy + 4, sw - 10, 40); fitText(c.wing || c.aura ? '꾸밈' : '없음', sx + sw / 2, sy + 52, sw - 6, 11, c.wing || c.aura ? C.goldEdge : C.muted, 'center'); }
    else { text('+', sx + sw / 2, sy + 26, 24, C.gold, 'center'); fitText('빈 칸', sx + sw / 2, sy + 50, sw - 6, 12, C.muted, 'center'); }
    hit(sx, sy, sw, 62, () => { modal.slot = i; const id = cosmetic.slots[i]?.id; if (id) modal.tab = Math.max(0, COS_TABS.findIndex((q) => q[1].includes(E.UNIT[id].grade))); });
  });
  const slot = modal.slot < open ? modal.slot : -1, cur = slot >= 0 ? cosmetic.slots[slot] : null;
  const set = (k, v) => { cosmetic.slots[slot] = { ...cur, [k]: v }; cosSave(); };
  // 미리 보기
  const px = x + 12, py = y + 156, pw = 196, ph = 214;
  // 잠긴 항목 미리 보기(2026-11-11 사용자 요청): 누르면 여기 입혀 보이고 「열기」 버튼
  const pv = modal.prev, pvCost = pv ? COS_ITEM_COST[`${pv.pre}:${pv.v}`] : 0;
  if (pv && itemOpen(`${pv.pre}:${pv.v}`)) modal.prev = null;
  const thPrev = pv?.pre === 'theme' ? THEMES[pv.v] : null;
  box(px, py, pw, ph, thPrev ? thPrev.bg : 'rgba(7,19,28,.85)', C.edge, 10, 1.2);
  if (thPrev) {   // 판 테마 미리 보기: 길 한 토막
    ctx.save(); ctx.beginPath(); ctx.rect(px + 2, py + 2, pw - 4, ph - 4); ctx.clip();
    ctx.lineWidth = 40; ctx.strokeStyle = thPrev.lane; ctx.strokeRect(px + 30, py + 40, pw - 60, ph - 80); ctx.lineWidth = 1.5; ctx.strokeStyle = thPrev.edge;
    ctx.strokeRect(px + 10, py + 20, pw - 20, ph - 40); ctx.strokeRect(px + 50, py + 60, pw - 100, ph - 120); ctx.restore();
  }
  const shown = cur?.id && pv && pv.pre !== 'theme' ? { ...cur, [pv.field]: pv.v } : cur;
  if (cur?.id) {
    const z = usz(cur.id), k = 1.75 / z, fx = px + pw / 2 - (shown.shot || shown.kill ? 26 : 0), fy = py + ph - 46;
    ctx.save(); ctx.translate(fx, fy); ctx.scale(k, k);
    cosBack(shown, 0, 0, z, slot); drawUnitShape(cur.id, 0, 0, GRADE_COLOR[E.UNIT[cur.id].grade], 2); cosFront(shown, 0, 0, z, slot);
    ctx.restore();
    if (shown.shot || shown.kill) {   // 공격 연출·처치 효과: 오른쪽 과녁을 1.3초마다 쏨
      const tx = px + pw - 30, ty = fy - 54, ph2 = (performance.now() / 1300) % 1, col = cosRGB(shown, slot);
      ctx.beginPath(); ctx.arc(tx, ty, 12, 0, Math.PI * 2); ctx.strokeStyle = 'rgba(255,255,255,.35)'; ctx.lineWidth = 2; ctx.stroke();
      ctx.beginPath(); ctx.arc(tx, ty, 5, 0, Math.PI * 2); ctx.fillStyle = 'rgba(255,120,100,.6)'; ctx.fill();
      const sx = fx + 10, sy = fy - 70;
      if (ph2 < 0.45 && shown.shot) { const q = ph2 / 0.45; cosShot(shown.shot, col, sx + (tx - sx) * q, sy + (ty - sy) * q, Math.atan2(ty - sy, tx - sx), 6, q * 8); }
      else if (ph2 >= 0.45 && shown.kill) drawKillFx(shown, { x: tx, y: ty + 16, uid: slot + 1 }, (ph2 - 0.45) * 1.1);
    }
    fitText(E.UNIT[cur.id].name, fx, py + ph - 20, pw - 16, 15, C.text, 'center');
    button(px + pw - 66, py + 6, 60, 30, '비우기', () => { cosmetic.slots[slot] = null; modal.prev = null; cosSave(); }, 'quiet');
  } else if (!thPrev) fitText(open ? '아래에서 전설·히든·초불영제를 골라요' : '달인 이상 첫 클리어에 칸이 열려요', px + pw / 2, py + ph / 2, pw - 16, 14, C.muted, 'center');
  if (pv) {
    const can = pointsLeft() >= pvCost;
    button(px + 6, py + 6, 104, 30, `열기 ${pvCost}점`, () => { if (!buyItem(`${pv.pre}:${pv.v}`, pv.name)) return; if (pv.pre === 'theme') cosmetic.theme = pv.v; else if (cur?.id) cosmetic.slots[slot] = { ...cur, [pv.field]: pv.v }; modal.prev = null; cosSave(); }, can ? 'primary' : 'normal');
    fitText(`「${pv.name}」 미리 보기${can ? '' : ` · 포인트 ${pointsLeft()}점`}`, px + 10, py + 48, pw - 20, 12, can ? C.goldEdge : '#9aa9b0');
  }
  // 오른쪽: 탭(모양 · 효과 · 판·저장) — 2026-11-11 꾸미기 늘리기
  const ox = px + pw + 12, ow = x + w - 12 - ox, en = !!cur?.id, et = modal.etab ?? 'look';
  const ETABS = [['look', '모양'], ['fx', '효과'], ['board', '판·저장']], tbw = (ow - 12) / 3;
  ETABS.forEach(([k, nm], i) => button(ox + i * (tbw + 6), py, tbw, 30, nm, () => { modal.etab = k; modal.prev = null; }, et === k ? 'primary' : 'quiet'));
  const row = (label, list, key, yy, pre, on = en, cur2 = cur, setter = set) => {   // pre: 포인트 잠금 이름(shot·kill·base·theme)
    // 검토(2026-11-11): 버튼에 「구슬 2점」을 넣으면 휴대폰에서 글자가 아주 작아짐 → 버튼은 이름 + 자물쇠, 값은 항목 이름 옆에
    const locked = pre ? list.filter(([v]) => !itemOpen(`${pre}:${v}`)) : [];
    const costs = [...new Set(locked.map(([v]) => COS_ITEM_COST[`${pre}:${v}`]))];
    text(label, ox, yy, 15, on ? C.gold : '#6f7f88');
    if (locked.length) { ctx.font = `15px ${FONT}`; fitText(costs.length === 1 ? `🔒 ${locked.map((q) => q[1]).join('·')} ${costs[0]}점` : `🔒 ${locked.map(([v, n]) => `${n} ${COS_ITEM_COST[`${pre}:${v}`]}점`).join(' · ')}`, ox + ctx.measureText(label).width + 10, yy, ow - ctx.measureText(label).width - 10, 12, '#9aa9b0'); }
    const bw = (ow - 6 * (list.length - 1)) / list.length;
    list.forEach(([v, name], i) => {
      const k = pre ? `${pre}:${v}` : '', lock = k && !itemOpen(k), bx = ox + i * (bw + 6);
      const pvOn = lock && modal.prev?.pre === pre && modal.prev?.v === v;
      button(bx, yy + 12, bw, 32, name, () => { if (lock) { modal.prev = pvOn ? null : { pre, field: key, v, name }; return; } modal.prev = null; setter(key, v); }, ((cur2?.[key] ?? '') === v && on && !lock) || pvOn ? 'primary' : lock ? 'quiet' : 'normal', on);
      if (lock && on) drawLockMark(bx + bw - 3, yy + 11);   // 오른쪽 위 모서리(글자를 가리지 않게)
    });
  };
  if (et === 'look') {
    row('날개', COS_WINGS, 'wing', py + 44);
    row('오라', COS_AURAS, 'aura', py + 98);
    text('색', ox, py + 154, 15, en ? C.gold : '#6f7f88');
    const cs = Math.min(30, (ow - 8 * 5) / 9);
    COS_COLORS.forEach(([v, name], i) => {
      const cx = ox + i * (cs + 5) + cs / 2, cy = py + 182, lock = v === 'rainbow' && !rainbowOpen(), on = en && (cur.color ?? '#ffd66b') === v;
      ctx.beginPath(); ctx.arc(cx, cy, cs / 2, 0, Math.PI * 2);
      if (v === 'rainbow') { const g = ctx.createConicGradient ? ctx.createConicGradient(0, cx, cy) : null; if (g) { ['#ff5a5a', '#ffd66b', '#6fe08a', '#6fe8ff', '#5aa8ff', '#b07bff', '#ff5a5a'].forEach((q, k) => g.addColorStop(k / 6, q)); ctx.fillStyle = g; } else ctx.fillStyle = '#ff8fc7'; }
      else ctx.fillStyle = v;
      ctx.globalAlpha = en && !lock ? 1 : 0.35; ctx.fill(); ctx.globalAlpha = 1;
      ctx.lineWidth = on ? 3 : 1.2; ctx.strokeStyle = on ? '#ffffff' : 'rgba(0,0,0,.5)'; ctx.stroke();
      if (lock) drawLockMark(cx, cy + 2);
      if (en) hit(cx - cs / 2 - 2, cy - cs / 2 - 4, cs + 4, cs + 8, () => { if (lock) { if (pointsLeft() >= COS_COST.rainbow) buyCos('rainbow'); else say(`무지개 색은 장인을 한 번 클리어하거나 포인트 ${COS_COST.rainbow}점으로 열려요(지금 ${pointsLeft()}점).`); } else set('color', v); });
    });
    fitText(en ? `${COS_COLORS.find((q) => q[0] === (cur.color ?? '#ffd66b'))?.[1] ?? ''}${rainbowOpen() ? '' : ' · 무지개는 장인 클리어로 열림'}` : '', ox, py + 208, ow, 12, C.muted);
  } else if (et === 'fx') {   // 공격 연출·처치 효과·발밑(색은 모양 탭의 색)
    row('공격 연출', COS_SHOTS, 'shot', py + 44, 'shot');
    row('처치 효과', COS_KILLS, 'kill', py + 98, 'kill');
    row('발밑', COS_BASES, 'base', py + 152, 'base');
    fitText(en ? '🔒 잠긴 것도 눌러서 미리 볼 수 있어요 · 색은 「모양」 탭의 색' : '먼저 칸과 갈매미를 골라요', ox, py + 208, ow, 12, C.muted);
  } else {   // 판 테마·저장 칸(칸과 상관없이 전체)
    row('판 테마', COS_THEMES, 'theme', py + 44, 'theme', true, cosmetic, (k, v) => { cosmetic.theme = v; cosSave(); });
    text('저장 칸(꾸민 칸 8개 + 판 테마를 통째로)', ox, py + 104, 14, C.gold);
    const bw3 = (ow - 12) / 3;
    for (let i = 0; i < COS_PRESETS; i++) {
      const bx3 = ox + i * (bw3 + 6), has = !!cosmetic.presets?.[i];
      button(bx3, py + 118, bw3, 36, `${i + 1} 저장`, () => presetSave(i), 'normal');
      button(bx3, py + 160, bw3, 36, has ? `${i + 1} 불러오기` : `${i + 1} 비어 있음`, () => presetLoad(i), has ? 'primary' : 'normal', has);
    }
    fitText('🔒 잠긴 테마도 눌러서 미리 볼 수 있어요', ox, py + 208, ow, 12, C.muted);
  }
  // 유닛 고르기: 등급 칸 → 그림
  const tw = (w - 24 - 2 * 6) / 3, ty = py + ph + 14;
  COS_TABS.forEach(([name, grades], i) => {
    const on = modal.tab === i;
    box(x + 12 + i * (tw + 6), ty, tw, 36, on ? 'rgba(243,216,158,.14)' : 'rgba(11,30,42,.8)', on ? C.goldEdge : GRADE_COLOR[grades[0]], 8, on ? 2.5 : 1.2);
    fitText(name, x + 12 + i * (tw + 6) + tw / 2, ty + 18, tw - 6, 14, GRADE_COLOR[grades[0]], 'center');
    hit(x + 12 + i * (tw + 6), ty, tw, 36, () => { modal.tab = i; });
  });
  const ids = E.UNITS.filter((u) => COS_TABS[modal.tab][1].includes(u.grade) && u.role !== E.SPECIAL_ROLE).map((u) => u.id);
  const cw = (w - 24 - 6 * 6) / 7, ch = 60, gy = ty + 46;
  ids.forEach((id, i) => {
    const cx = x + 12 + (i % 7) * (cw + 6), cy = gy + Math.floor(i / 7) * (ch + 6), mine = cur?.id === id;
    const other = cosmetic.slots.findIndex((c, k) => k !== slot && k < open && c?.id === id);
    box(cx, cy, cw, ch, mine ? 'rgba(243,216,158,.2)' : 'rgba(11,30,42,.85)', mine ? C.goldEdge : GRADE_COLOR[E.UNIT[id].grade], 8, mine ? 2.5 : 1.2);
    drawFit(unitImg(id), cx + 4, cy + 3, cw - 8, ch - 6, slot >= 0 && other < 0 ? 1 : 0.45);
    if (other >= 0) { box(cx + cw - 26, cy + 3, 23, 16, 'rgba(7,19,28,.92)', null, 4); text(`${other + 1}칸`, cx + cw - 14.5, cy + 11.5, 10, C.goldEdge, 'center'); }
    hit(cx, cy, cw, ch, () => {
      if (slot < 0) { say(open ? '위에서 열린 칸을 먼저 골라요.' : '달인 이상을 처음 클리어하면 꾸미기 칸이 열려요.'); return; }
      if (other >= 0) { say(`${E.UNIT[id].name}은(는) ${other + 1}번째 칸에서 꾸미고 있어요.`); return; }
      cosmetic.slots[slot] = { wing: 'feather', aura: '', color: '#ffd66b', ...(cur ?? {}), id }; cosSave();
    });
  });
}
const TITLE_RULES = () => [
  '· 위습을 누르면 갈매미가 나와요.',
  '· 갈매미는 끌어서 옮기고, 누르면 정보·버튼이 나와요.',
  '· 조합법대로 모아 「조합」하면 더 센 갈매미!',
  '· ☆목표를 찍으면 필요한 재료에 색 테두리가 생겨요.',
  `· 길 위의 적이 ${E.RULES.limit}마리를 넘지 않게 막으세요.`,
  '· 보스는 그 웨이브 안에! 후반엔 방깍·마방깍·이감·스턴이 꼭 필요해요.',
  '· 배경음악은 처음엔 꺼져 있어요. 오른쪽 위 ♪에서 켜요.',
];
function drawTitleMenu() {   // 첫 화면 「≡ 메뉴」
  shadeAll();
  const x = 40, w = W - 80, items = [['기록', '지난 판 기록·조합', () => { modal = { kind: 'records' }; }], ['최상위 기록', '최상위 유닛별 클리어 횟수', () => { modal = { kind: 'tops', td: 'all' }; }], ['꾸미기', '클리어 보상: 날개·오라·효과', openCustomize, cosOpen() > (stats.seen ?? 0)],
    ['도전 모드', `규칙을 바꿔 도전(난이도 ${diffName(selDiff)})`, () => { modal = { kind: 'modes' }; }], ['조합식 ↗', '상위 조합식 웹페이지(새 탭)', openRecipePage], ['게임 방법', '처음 하는 분을 위한 안내', () => { modal = { kind: 'rules' }; }]];
  const h = 92 + items.length * 70, y = Math.max(70, (VH0() - h) / 2);
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('메뉴', x + 20, y + 32, 22, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  items.forEach(([nm, sub, fn, dot], i) => {
    button(x + 16, y + 70 + i * 70, w - 32, 60, nm, fn, 'normal', true, sub);
    if (dot) { ctx.beginPath(); ctx.arc(x + w - 26, y + 80 + i * 70, 6, 0, Math.PI * 2); ctx.fillStyle = C.bad; ctx.fill(); }
  });
}
function drawTopClears() {   // 최상위 유닛별 클리어 횟수(난이도 고르기: 전체·달인·장인 등)
  shadeAll();
  const x = 24, w = W - 48, y = 60, td = modal.td ?? 'all', tc = stats.topClears ?? {}, h = 160 + Math.ceil(E.UNITS.filter((u) => isTopId(u.id)).length / 2) * 46;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('최상위 기록', x + 20, y + 32, 22, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = { kind: 'tmenu' }; }, 'quiet');
  const tabs = [['all', '전체'], ...E.DIFF_ORDER.filter((d) => d === 'master' || d === 'artisan' || Object.keys(tc[d] ?? {}).length).map((d) => [d, diffName(d)])];
  const tw = Math.min(96, (w - 40) / tabs.length - 6);
  tabs.forEach(([k, nm], i) => button(x + 20 + i * (tw + 6), y + 62, tw, 38, nm, () => { modal.td = k; }, td === k ? 'primary' : 'normal'));
  const cnt = (id) => (td === 'all' ? Object.values(tc).reduce((a, o) => a + (o?.[id] ?? 0), 0) : tc[td]?.[id] ?? 0);
  const tops = E.UNITS.filter((u) => isTopId(u.id)).map((u) => u.id).sort((a, b) => cnt(b) - cnt(a) || E.tierOf(b) - E.tierOf(a));
  const total = td === 'all' ? Object.values(stats.byDiff ?? {}).reduce((a, n) => a + n, 0) : stats.byDiff?.[td] ?? 0;
  fitText(`클리어 ${total}번 · 클리어한 판에 있던 최상위를 종류마다 1번씩 셈`, x + 20, y + 124, w - 40, 14, C.muted);
  const cols = 2, cw = (w - 40) / cols, rh = 46, top0 = y + 144, rows = Math.ceil(tops.length / cols), max = Math.max(1, ...tops.map(cnt));
  tops.forEach((id, i) => {
    const cx = x + 20 + Math.floor(i / rows) * cw, cy = top0 + (i % rows) * rh, u = E.UNIT[id], n = cnt(id);
    box(cx, cy, cw - 8, rh - 6, 'rgba(11,30,42,.85)', n ? GRADE_COLOR[u.grade] : C.line, 8, n ? 1.5 : 1);
    ctx.save(); ctx.beginPath(); ctx.arc(cx + 22, cy + 20, 16, 0, Math.PI * 2); ctx.clip(); drawFit(unitImg(id), cx + 6, cy + 4, 32, 32); ctx.restore();
    fitText(u.name, cx + 44, cy + 16, cw - 120, 15, n ? C.text : C.muted);
    fitText(E.GRADE_NAME[u.grade], cx + 44, cy + 33, 60, 12, GRADE_COLOR[u.grade]);
    box(cx + 92, cy + 27, (cw - 170) * n / max, 6, n ? C.gold : 'transparent', null, 3);
    text(n ? `${n}번` : '-', cx + cw - 18, cy + 21, 15, n ? C.goldEdge : C.muted, 'right');
  });
}
function drawRules() {
  shadeAll();
  const lines = TITLE_RULES(), x = 28, w = W - 56, h = 100 + lines.length * 40, y = Math.max(70, (VH0() - h) / 2);
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('게임 방법', x + 20, y + 32, 22, C.text);
  button(x + w - 96, y + 12, 82, 40, '닫기', () => { modal = { kind: 'tmenu' }; }, 'quiet');
  lines.forEach((l, k) => fitText(l, x + 18, y + 84 + k * 40, w - 36, 16, C.text));
}
const VH0 = () => 960;   // 첫 화면 기준 높이
function drawModes() {
  shadeAll();
  const x = 28, y = 170, w = W - 56, keys = Object.keys(E.MODES), h = 120 + keys.length * 104;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('도전 모드', x + 20, y + 34, 24, C.text);
  button(x + w - 96, y + 14, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(`난이도 ${diffName(selDiff)} · 규칙을 바꿔 도전해요 · 최고 기록은 모드·난이도마다 따로`, x + 20, y + 72, w - 40, 14, C.muted);
  keys.forEach((k, i) => {
    const m = E.MODES[k], yy = y + 96 + i * 104;
    box(x + 14, yy, w - 28, 92, 'rgba(11,30,42,.85)', k === 'normal' ? C.edge : C.gold, 10, 1.5);
    text(m.name, x + 30, yy + 26, 19, k === 'normal' ? C.text : C.goldEdge);
    fitText(m.desc, x + 30, yy + 54, w - 200, 14, C.muted);
    fitText(`최고 기록 ${bestText(bestOf(k, selDiff))}`, x + 30, yy + 76, w - 200, 13, C.gold);
    button(x + w - 28 - 110, yy + 20, 110, 52, '시작', () => start(undefined, k), 'primary');
  });
}
function drawSound() {
  shadeAll();
  // 「공격 소리」 줄이 들어가 546 → 620(2026-11-15). 판 위에서 열면(아래 메뉴가 보이는 상태) 위에서부터 열어 아래 메뉴에 가리지 않게(검토: 성능 표시·공격 소리 줄이 메뉴 뒤로 숨음)
  const top = topModal(), x = 50, y = top ? 12 : 220, w = W - 100, h = top ? MENU_Y - 20 : 620;
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('소리·효과 설정', x + 20, y + 32, 22, C.text);
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
  // 효과음 음량
  const sy = by + 80;
  text('화려한 효과', x + 24, sy + 82, 18, C.text);
  button(x + w - 24 - 120, sy + 60, 120, 44, fxLite ? '끔' : '켬', () => setFxLite(!fxLite), fxLite ? 'normal' : 'primary');
  fitText('끄면 공격 효과·꾸밈·빛 테두리·움직이는 장식을 줄이고', x + 24, sy + 128, w - 48, 13, C.muted);
  fitText('화면 해상도를 조금 낮춰 가볍게 그려요(렉이 있는 기기용). 게임 규칙은 같아요.', x + 24, sy + 150, w - 48, 13, C.muted);
  text('성능 표시', x + 24, sy + 200, 18, C.text);
  button(x + w - 24 - 120, sy + 178, 120, 44, perfShow.on ? '켬' : '끔', () => { perfShow.on = !perfShow.on; try { localStorage.setItem(PERF_KEY, perfShow.on ? '1' : '0'); } catch { /* 진행 */ } }, perfShow.on ? 'primary' : 'normal');
  fitText('왼쪽 아래에 초당 프레임·그리기·계산 시간이 나와요(렉 원인 찾기용)', x + 24, sy + 236, w - 48, 13, C.muted);
  text('공격·처치 소리', x + 24, sy + 286, 18, C.text);
  button(x + w - 24 - 120, sy + 264, 120, 44, atkSfx.on ? '켬' : '끔', () => { setAtkSfx(!atkSfx.on); sfxCtx(); if (atkSfx.on) atkSound({ type: 'phys', tier: 2, job: '주력', elem: 'NORMAL' }); }, atkSfx.on ? 'primary' : 'normal');
  fitText('갈매미가 쏠 때 속성별 짧은 소리·적이 쓰러질 때 소리(효과음 음량을 따라요)', x + 24, sy + 322, w - 48, 13, C.muted);
  text(`효과음 ${Math.round(sfxState.vol * 100)}%`, x + 24, sy - 18, 18, sfxState.vol ? C.text : C.muted);
  button(x + 24, sy - 4, 48, 44, '−', () => { setSfxVolume(sfxState.vol - 0.05); sfx('summon'); }, 'normal');
  button(x + w - 24 - 48, sy - 4, 48, 44, '+', () => { setSfxVolume(sfxState.vol + 0.05); sfx('summon'); }, 'normal');
  box(bx, sy + 12, bw, 12, 'rgba(0,0,0,.5)', C.line, 6, 1);
  box(bx, sy + 12, bw * sfxState.vol, 12, C.gold, null, 6);
  ctx.beginPath(); ctx.arc(bx + bw * sfxState.vol, sy + 18, 11, 0, Math.PI * 2); ctx.fillStyle = C.goldEdge; ctx.fill();
  hit(bx - 14, sy - 10, bw + 28, 56, (p) => setSfxVolume((p.x - bx) / bw), true);
}

// 가로 화면에서 연 창이 없을 때 오른쪽: 안내만(창처럼 보이지 않게 흐리게)
function drawToast() {   // 짧은 알림(판 아래쪽 가운데)
  if (!(performance.now() < toast.until && toast.msg)) return;
  ctx.font = `15px ${FONT}`;
  const tw = Math.min(W - 40, ctx.measureText(toast.msg).width + 32);
  box((W - tw) / 2, 618, tw, 40, 'rgba(19,44,59,.96)', C.goldEdge, 10, 1.5);
  fitText(toast.msg, W / 2, 638, tw - 20, 15, C.text, 'center');
}
// 가로 화면 오른쪽 위(창이 없을 때): 이번 웨이브 피해 순위 미니판 + 안내
function drawSideTop() {
  box(8, 6, W - 16, 658, 'rgba(16,36,51,.82)', C.edge, 12);
  // ① 시너지(2026-11-16 사용자 요청: TFT처럼 한눈에) — 판에 있는 에픽 이상을 속성별 종류 수로, 2·4·6종이면 그 속성 피해 +10·22·36%
  text('시너지', 22, 28, 17, C.gold);
  const bi = E.BIAS[G.bias];
  if (bi) { const bt = `이번 판 ${bi.name} · ${G.bias === 'armor' ? '마법' : G.bias === 'mr' ? '물리' : '독'} 유리`; ctx.font = `12px ${FONT}`; const bw2 = ctx.measureText(bt).width + 16; const bcl = { armor: ['#8fb7ff', '#bcd3ff'], mr: ['#ffb27a', '#ffd0a8'], split: ['#8fe08a', '#c8f5c0'] }[G.bias] ?? ['#aaa', '#ddd']; box(W - 22 - bw2, 16, bw2, 24, 'rgba(7,19,28,.8)', bcl[0], 8, 1.5); text(bt, W - 22 - bw2 / 2, 28, 12, bcl[1], 'center'); }
  const syn = E.synergyOf(G), list = E.SYN_ELEMS.filter((el) => syn[el].n > 0).sort((a, b) => syn[b].lv - syn[a].lv || syn[b].n - syn[a].n).slice(0, 6);
  if (!list.length) fitText('에픽 이상 같은 속성을 2종 모으면 속성마다 다른 시너지가 켜져요', 22, 60, W - 44, 13, C.muted);
  list.forEach((el, i) => {
    const sv = syn[el], y = 44 + i * 40, col = `rgb(${ELEM_COL[el] ?? '220,220,220'})`, on = sv.lv > 0, nx = E.SYN_STEPS.find((x) => sv.n < x);
    box(16, y, W - 32, 36, on ? 'rgba(11,30,42,.95)' : 'rgba(11,30,42,.5)', on ? col : 'rgba(145,122,76,.35)', 8, on ? 2 : 1);
    ctx.beginPath(); for (let k = 0; k < 6; k++) { const a = Math.PI / 6 + k * Math.PI / 3; ctx.lineTo(38 + Math.cos(a) * 13, y + 18 + Math.sin(a) * 13); } ctx.closePath();   // 육각 표
    ctx.fillStyle = on ? col : 'rgba(80,90,100,.8)'; ctx.fill(); text(String(sv.n), 38, y + 19, 14, on ? '#10202c' : '#cfd6da', 'center');
    text(E.SYN_NAME[el], 62, y + 18, 15, on ? col : '#9aa9b0');
    E.SYN_STEPS.forEach((st, k) => { const px = 120 + k * 52; box(px, y + 10, 44, 16, sv.n >= st ? col : 'rgba(0,0,0,.4)', 'rgba(255,255,255,.15)', 5, 1); text(`${st}종`, px + 22, y + 18, 11, sv.n >= st ? '#10202c' : '#7d8c94', 'center'); });
    { const fx = E.SYN_FX[el]; fitText(on ? `${fx.text(sv.bonus)}${nx ? ` · ${nx}종이면 ${fx.text(fx.v[sv.lv]).replace(/^.*?([+−]\d+%)$/, '$1')}` : ''}` : `${nx}종이면 ${fx.text(fx.v[0])}`, 284, y + 18, W - 300, 12, on ? C.text : '#8b9aa3'); }
  });
  // ② 이번 웨이브 피해(줄여서 위 4)
  const my = 44 + Math.max(1, list.length) * 40 + 14;
  text('이번 웨이브 피해', 22, my, 15, C.gold);
  const rows = E.fieldUnits(G).concat(E.awayUnits(G)).map((u) => ({ u, d: G.dmgWave?.[u.uid] ?? 0 })).filter((r) => r.d > 0).sort((a, b) => b.d - a.d).slice(0, Math.max(2, Math.floor((548 - my - 16) / 38)));
  const top = rows[0]?.d ?? 1;
  if (!rows.length) fitText('아직 이번 웨이브에 피해를 준 갈매미가 없어요', 22, my + 28, W - 44, 13, C.muted);
  rows.forEach((r, i) => {
    const y = my + 14 + i * 38, col = GRADE_COLOR[E.UNIT[r.u.id].grade];
    drawFit(unitImg(r.u.id), 18, y, 32, 34);
    fitText(`${E.UNIT[r.u.id].name}${r.u.away ? ' (탐사)' : ''}`, 56, y + 11, 180, 13, col);
    box(56, y + 22, W - 56 - 110, 7, 'rgba(0,0,0,.45)', null, 4);
    box(56, y + 22, (W - 56 - 110) * r.d / top, 7, col, null, 4);
    fitText(fmtN(r.d), W - 24, y + 18, 84, 13, C.text, 'right');
  });
  // ③ 갈매미맨 폭격(증강체 「갈매미맨 호출」이 있을 때만, 새우를 냄) · 새우 상점
  const by = 560;
  if (E.skillOn(G)) {
    const left = E.skillLeft(G), cost = E.skillCost(G), ready = left <= 0 && !G.over && G.gold >= cost;
    button(16, by, W - 32 - 150, 92, '갈매미맨 폭격', () => { if (E.useSkill(G)) sfx('boss'); }, ready ? 'primary' : 'normal', ready, left > 0 ? `${Math.ceil(left)}초 뒤 · 새우 ${fmtN(cost)}` : `새우 ${fmtN(cost)} · 일반 적 체력 ${Math.round(E.RULES.skillPct * 100)}%`);
    if (left > 0) box(16, by + 86, (W - 32 - 150) * (1 - left / E.RULES.skillCd), 6, C.gold, null, 3);
    button(W - 16 - 142, by, 142, 92, '새우 상점', () => { modal = { kind: 'shop' }; }, 'normal', true, E.shopOpen(G) ? `새우 ${fmtN(G.gold)}` : `${E.RULES.shopFrom}웨이브부터`);
  } else button(16, by, W - 32, 92, '새우 상점', () => { modal = { kind: 'shop' }; }, 'normal', true, E.shopOpen(G) ? `새우 ${fmtN(G.gold)} · 무작위 위습·리롤권` : `${E.RULES.shopFrom}웨이브부터 열려요 · 무작위 위습·리롤권`);
}
// 새우 상점(2026-11-16): 무작위 위습·리롤권. 살 때마다 값이 오름. 판이 끝나면 남은 새우는 저금(첫 화면 꾸미기에서 포인트로)
function drawShop() {
  shadeAll(0.3);
  const R0 = modalRect(), x = R0.x + 6, y = R0.y, w = R0.w - 12, h = Math.min(R0.h, 420);
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('새우 상점', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  fitText(`가진 새우 ${fmtN(G.gold)} · 무작위로 나와요 · 살수록 비싸져요 · 판이 끝나면 남은 새우는 저금돼요`, x + 18, y + 66, w - 36, 13, C.muted);
  Object.entries(E.SHOP).forEach(([id, it], i) => {
    const yy = y + 92 + i * 100, pr = E.shopPrice(G, id);
    box(x + 14, yy, w - 28, 88, 'rgba(11,30,42,.85)', C.edge, 10, 1.5);
    text(it.name, x + 30, yy + 30, 18, C.text);
    fitText(id === 'reroll' ? '에픽 한 마리를 다른 에픽으로 바꿔요' : `산 횟수 ${G.shopN?.[id] ?? 0}번`, x + 30, yy + 60, w - 230, 13, C.muted);
    const op = E.shopOpen(G); button(x + w - 28 - 160, yy + 14, 160, 60, op ? '사기' : `${E.RULES.shopFrom}웨이브부터`, () => { const r = E.shopBuy(G, id); if (!r.ok) say(r.why); else { sfx('wisp'); say(`${it.name}을(를) 샀어요(새우 ${r.price})`); } }, 'primary', op && G.gold >= pr, `새우 ${fmtN(pr)}`);
  });
}
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
// 재료 나무 이름: 칸이 좁으면 9px까지 줄이고, 그래도 길면 띄어쓰기에서 두 줄, 띄어쓰기가 없으면 「…」(긴 이름끼리 겹치지 않게)
function treeLabel(name, cx, y, maxW, size, color) {
  const fits = (t, sz) => { ctx.font = `${sz}px ${FONT}`; return ctx.measureText(t).width <= maxW; };
  for (let sz = size; sz >= 9; sz--) if (fits(name, sz)) { text(name, cx, y, sz, color, 'center'); return; }
  const sp = name.indexOf(' ') >= 0 ? [...name].map((c, i) => (c === ' ' ? i : -1)).filter((i) => i > 0).sort((a, b) => Math.abs(a - name.length / 2) - Math.abs(b - name.length / 2))[0] : -1;
  if (sp > 0) {
    const a = name.slice(0, sp), b = name.slice(sp + 1);
    let sz = size; while (sz > 9 && !(fits(a, sz) && fits(b, sz))) sz--;
    text(a, cx, y - sz * 0.15, sz, color, 'center'); text(b, cx, y + sz * 1.05, sz, color, 'center'); return;
  }
  let t = name; while (t.length > 1 && !fits(t + '…', 9)) t = t.slice(0, -1);
  text(t + '…', cx, y, 9, color, 'center');
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
    else if (E.UNIT[n.id].role === E.SPECIAL_ROLE) { ctx.beginPath(); ctx.arc(bx, by, br, 0, Math.PI * 2); ctx.fillStyle = GRADE_COLOR.TRANSCEND; ctx.fill(); text('6', bx, by + 0.5, Math.round(br * 1.5), '#07131c', 'center'); }   // 외부탐사 6장 보상
    else if (!n.top && !n.kids.length) drawCheck(bx, by, br, '#ff9a8a', '!');
    treeLabel(u.name, n.cx, n.cy + r + 10, n.d === 0 ? Math.max(60, n.span - 4) : n.span - 4, n.d === 0 ? 14 : 12, n.have ? C.good : n.top ? C.goldEdge : C.text);
    if (!n.top) hit(n.cx - n.span / 2, n.cy - r, n.span, r * 2 + 18, () => showInfo(n.id, true));
    n.show.forEach(nodes);
  };
  nodes(root);
}
function drawInfo() {
  const id = info.id, u = E.UNIT[id], k = E.kit(id), col = GRADE_COLOR[u.grade], r = E.recipeFor(id);
  const sh0 = landscape ? MENU_Y : PBOT;   // 가로 화면은 아래 메뉴를 가리지 않게
  ctx.fillStyle = 'rgba(3,9,14,.62)'; ctx.fillRect(0, 0, W, sh0);
  hit(0, 0, W, sh0, () => { info = null; });   // 바깥을 누르면 닫힘
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, col, 14, 2);
  hit(x, y, w, h, () => {});
  drawFit(unitImg(id), x + 14, y + 12, 80, 86);
  fitText(u.name, x + 106, y + 28, w - 220, 24, C.text);
  let cx = x + 106;
  cx += chip(E.GRADE_NAME[u.grade], cx, y + 50, col) + 6;
  chip(k.special ? '조합 재료' : `${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx, y + 50, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag);
  if (k.special) fitText('공격하지 않아요 · 판에 두거나 창고에 넣어도 조합 재료로 쓰여요', x + 106, y + 92, w - 120, 14, C.muted);
  else fitText(`공격 ${Math.round(k.dmg * E.upgMul(G, k.tier)).toLocaleString()} · ${k.gap * (E.RULES.atkSpeed ?? 1) <= 0.6 ? '빠름' : k.gap * (E.RULES.atkSpeed ?? 1) >= 1.1 ? '느림' : '보통'}(${k.gap}초) · 사거리 ${k.range}`, x + 106, y + 92, w - 120, 14, C.muted);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { info = null; }, 'quiet');
  if (info.stack.length) { const back = info.stack[info.stack.length - 1]; button(x + w - 96, y + 56, 82, 36, '← 뒤로', () => { info = { id: back, stack: info.stack.slice(0, -1) }; }, 'normal'); }
  // 유틸
  text('유틸·특수', x + 16, y + 124, 15, C.gold);
  // 이 갈매미로 만들 수 있는 것(2026-11-12 사용자 요청): 누르면 조합법 창을 「○○ 재료」로 열어 줌
  const usedIn = E.RECIPES.filter((q) => q.in.includes(id)).length;
  if (usedIn) button(x + w - 186, y + 100, 172, 32, `이걸로 만드는 것 ${usedIn}`, () => { info = null; openBook(null, id); }, 'normal');
  const tips = E.kitTips(k);
  let tipEnd = y + 170;
  if (tips.length) tipEnd = chipRow(tips.map((t) => [t, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag]), x + 16, y + 138, x + w - 12, tips.length > 6 ? 12 : 14);
  else text('기본 공격만 해요', x + 16, y + 150, 14, C.muted);
  // 재료 피라미드(특성 칩이 여러 줄이면 그만큼 아래에서 시작)
  const py = Math.max(y + 200, tipEnd + 26);
  ctx.fillStyle = 'rgba(145,122,76,.35)'; ctx.fillRect(x + 12, py - 14, w - 24, 1);
  // 선택 위습으로 바로 소환(사용자 요청 2026-11-09): 그 등급 선택 위습으로 고를 수 있는 갈매미면 버튼
  const pickable = E.PICK_GRADES.includes(u.grade) && E.unitsOfGrade(u.grade).includes(id), pn = G.picks[u.grade] ?? 0;
  const pickBtn = (bx, bw) => button(bx, y + h - 76, bw, 56, '선택위습 사용', () => {
    const res = E.summonPick(G, id);
    if (res.ok) { selected = [res.unit.uid]; say(`${u.name} 소환! (${E.GRADE_NAME[u.grade]} 선택 위습 ${G.picks[u.grade]}개 남음)`); } else if (res.why) say(res.why);
  }, 'primary', pn > 0, `${E.GRADE_NAME[u.grade]} ${pn}개`);
  if (!r) {
    fitText(u.role === E.SPECIAL_ROLE ? '외부탐사 6장 보상으로 받아요 · 초월 등급 조합에 재료로 쓰면 사라져요' : `조합법이 없어요 · ${E.GRADE_NAME[u.grade]} 위습으로 소환해요`, W / 2, py + 30, w - 40, 15, C.muted, 'center');
    if (pickable) pickBtn(x + 14, w - 28);
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
    const ready = canMake(r), miss = Object.entries(base).map(([b, n]) => `${E.UNIT[b].name}×${n}`).join(' · ');
    box(x + 12, y + h - 132, w - 24, 50, 'rgba(243,216,158,.08)', 'rgba(243,216,158,.3)', 8, 1);
    ctx.save(); ctx.beginPath(); ctx.rect(x + 12, y + h - 132, w - 24, 50); ctx.clip();
    if (ready) fitText('재료가 모두 있어요! 바로 조합할 수 있어요.', W / 2, y + h - 107, w - 40, 15, C.good, 'center');
    else if (E.topBlocked(G, r) && E.recipeUnits(G, r)) fitText(`재료는 모두 있지만 ${E.topBlockWhy(G, r)}.`, W / 2, y + h - 107, w - 40, 13, C.bad, 'center');
    else if (miss) wrap(`더 필요: ${miss}`, x + 22, y + h - 127, w - 44, 13, C.goldEdge, 19);
    else fitText('하위 재료는 다 있어요. 「조합」을 누르면 중간 재료부터 한 번에 만들어요.', W / 2, y + h - 107, w - 40, 14, C.good, 'center');
    ctx.restore();
    const gi = G.goals.indexOf(id), nb = pickable ? 3 : 2, bw = (w - 28 - 12 * (nb - 1)) / nb;
    button(x + 14, y + h - 76, bw, 56, gi >= 0 ? '★ 목표에서 빼기' : '☆ 목표로', () => { const on = E.toggleGoal(G, id); say(on ? `목표: ${u.name} (하위 재료까지 색 테두리)` : '목표에서 뺐어요'); }, gi >= 0 ? 'primary' : 'normal');
    if (E.topBlocked(G, r) && !E.recipeUnits(G, r)) fitText(`${E.topBlockWhy(G, r)}.`, W / 2, y + h - 142, w - 30, 13, C.bad, 'center');
    if (pickable) pickBtn(x + 26 + bw, bw);
    button(x + 14 + (nb - 1) * (bw + 12), y + h - 76, bw, 56, '조합', () => { const nu = combineChain(r); if (nu) { selected = [nu.uid]; say(`${u.name} 완성!${nu.chain ? ` (중간 조합 ${nu.chain}번 포함)` : ''}`); delete nu.chain; if (info.stack.length) { const back = info.stack[info.stack.length - 1]; info = { id: back, stack: info.stack.slice(0, -1) }; } } }, 'primary', ready || canChain(r));   // 창을 닫지 않음: 재료에서 들어왔으면 위 목표로 돌아감
  }
}
// 피해 순위표(사용자 요청 2026-11-08): 갈매미마다 이번 판·이번 웨이브에 준 피해(외부탐사 대장 포함)
const fmtN = (n) => (n >= 1e8 ? `${(n / 1e8).toFixed(1)}억` : n >= 1e4 ? `${(n / 1e4).toFixed(1)}만` : `${Math.round(n)}`);
function drawMeter() {
  shadeAll(0.3);
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('피해 순위', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const rows = G.units.map((u) => ({ u, d: G.dmg?.[u.uid] ?? 0, dw: G.dmgWave?.[u.uid] ?? 0 })).filter((r) => r.d > 0).sort((a, b) => b.d - a.d);
  const total = rows.reduce((a, r) => a + r.d, 0), top = rows[0]?.d ?? 1;
  fitText(`전체 ${fmtN(total)}`, x + 18, y + 62, w - 36, 13, C.muted);
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
  const b5 = (W - 44 - 4 * 6) / 5, b4 = (W - 44 - 24) / 5, newest = E.fieldUnits(G).slice(-1)[0], PL = panelLayout();
  const steps = [
    { msg: '① 커먼 위습을 눌러 갈매미를 불러 보세요', tx: 22 + b5 / 2, ty: PL.wy, by: 560 },
    { msg: '② 갈매미를 끌어서 길(적이 지나가는 곳) 옆으로 옮겨 보세요', tx: newest?.x ?? W / 2, ty: (newest?.y ?? 400) - 56, by: (newest?.y ?? 400) > 400 ? 150 : 470 },
    { msg: '③ 위습을 더 불러 조합법대로 모으면 더 센 갈매미! 「조합법」을 눌러 보세요', tx: 22 + (W - 44 - 6) / 4, ty: PL.by, by: 640 },   // 2026-11-11: 조합법은 아래 메뉴 첫 줄 왼쪽
    { msg: '④ 조합법 줄을 누르면 설명과 재료 나무가 나와요. 이제 직접 해 보세요!', tx: null, ty: null, by: H - 96 },
  ];
  const st = steps[tut.step], bw = W - 60, bh = 74, bx = 30;
  let by = st.by;
  ctx.save();
  if (landscape) {   // 가로 배치: 메뉴·조합법은 오른쪽 열, 갈매미 끌기는 왼쪽 큰 판
    if (tut.step === 1) { ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); OS = LS; OX = BOARD_X; } else if (wide) { ctx.translate(BX, BYT); ctx.scale(WK, WK); OX = BX; OY = BYT; OS = WK; } else { ctx.translate(SIDE_X, 0); OX = SIDE_X; }
  }
  box(bx, by, bw, bh, 'rgba(19,44,59,.97)', C.goldEdge, 12, 2);
  ctx.save(); ctx.beginPath(); ctx.rect(bx + 10, by + 6, bw - 120, bh - 12); ctx.clip(); wrap(st.msg, bx + 14, by + 12, bw - 130, 15, C.text, 22); ctx.restore();
  button(bx + bw - 100, by + 18, 88, 38, '안내 끄기', tutDone, 'quiet');
  if (st.tx != null) {   // 가리키는 화살표
    const ay = st.ty > by ? by + bh : by, dir = st.ty > by ? 1 : -1, ax = Math.max(bx + 20, Math.min(bx + bw - 20, st.tx));
    const bob = Math.sin(now / 220) * 4;
    ctx.strokeStyle = C.goldEdge; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(ax, ay); ctx.lineTo(st.tx, st.ty - dir * (10 - bob)); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(st.tx, st.ty + dir * bob); ctx.lineTo(st.tx - 9, st.ty - dir * (14 - bob)); ctx.lineTo(st.tx + 9, st.ty - dir * (14 - bob)); ctx.closePath(); ctx.fillStyle = C.goldEdge; ctx.fill();
  }
  ctx.restore(); OX = 0; OS = 1; OY = 0;
}
// 용병모집 창(사용자 요청 2026-11-08): 모집권 4개 → 레어 30% · 에픽 40% · 실패 30%
function drawRecruit() {
  shadeAll(0.3);
  const { x, y, w, h } = modalRect();
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text('용병모집', x + 18, y + 30, 22, C.text);
  button(x + w - 96, y + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const tk = G.tickets ?? 0, cost = E.RULES.recruitCost, odds = E.RULES.recruitOdds;
  text(`모집권 ${tk}`, W / 2, y + 96, 30, C.goldEdge, 'center');
  const fail = Math.round(100 * (1 - Object.values(odds).reduce((a, v) => a + v, 0)));
  const items = [...Object.entries(odds).map(([g, p]) => [`${E.GRADE_NAME[g]} ${Math.round(p * 100)}%`, GRADE_COLOR[g]]), [`실패 ${fail}%`, C.muted]];
  ctx.font = `15px ${FONT}`;
  const tw = items.reduce((a, [t]) => a + ctx.measureText(t).width + 20, 0) - 6;
  let cx = (W - tw) / 2;
  for (const [t, c] of items) cx += chip(t, cx, y + 128, c, 15) + 6;
  const bh = Math.min(96, h * 0.18);
  button(x + 40, y + 176, w - 80, bh, '모집하기', () => {
    const r = E.recruit(G);
    if (!r.ok) { if (r.why) say(r.why); return; }
    sfx(r.fail ? 'recruitFail' : 'recruitOk');
    modal.last = r.fail ? { fail: true, t: performance.now() } : { id: r.unit.id, grade: r.grade, t: performance.now() };
  }, 'primary', tk >= cost, `모집권 ${cost}개`);
  // 결과
  const L = modal.last, ry = y + 176 + bh + 24;
  if (L) {
    const fresh = performance.now() - L.t < 500 ? 1 + 0.15 * (1 - (performance.now() - L.t) / 500) : 1;
    box(x + 40, ry, w - 80, Math.min(220, h - (ry - y) - 20), 'rgba(11,30,42,.85)', L.fail ? '#46555f' : GRADE_COLOR[L.grade], 12, 2);
    if (L.fail) text('모집 실패…', W / 2, ry + 60, 22, C.muted, 'center');
    else {
      const s2 = 110 * fresh;
      drawFit(unitImg(L.id), W / 2 - s2 / 2, ry + 10, s2, s2);
      text(`${E.UNIT[L.id].name}`, W / 2, ry + 136, 20, GRADE_COLOR[L.grade], 'center');
      text(`${E.GRADE_NAME[L.grade]} · 판 가운데에 나왔어요`, W / 2, ry + 162, 13, C.muted, 'center');
    }
  }
}
function drawModal() {
  if (modal?.kind === 'book') drawBook();
  else if (modal?.kind === 'mission') drawMission();
  else if (modal?.kind === 'upgrade') drawUpgrade();
  else if (modal?.kind === 'shop') drawShop();
  else if (modal?.kind === 'sound') drawSound();
  else if (modal?.kind === 'pick') drawPick();
  else if (modal?.kind === 'more') drawMore();
  else if (modal?.kind === 'storage') drawStorage();
  else if (modal?.kind === 'pause') drawPause();
  else if (modal?.kind === 'over') drawOver();
  else if (modal?.kind === 'peer') drawPeerModal();
  else if (modal?.kind === 'meter') drawMeter();
  else if (modal?.kind === 'recruit') drawRecruit();
  else if (modal?.kind === 'signal') drawSignalPicker();
  else if (modal?.kind === 'choice') drawChoice();
  else if (modal?.kind === 'aug' || modal?.kind === 'intro') drawBoardModal();
}
// 고르는 보상(6장, 2026-11-10 사용자 요청: 초월 갈매미맨 대신 다른 보상도). 게임은 계속 흐름.
// 2026-11-11 사용자 요청: 선택을 미룰 수 있게 — 「나중에」를 누르면 닫히고, 아래 「더보기」에서 언제든 다시 열어 받음
function drawChoice() {
  if (!G?.choice) { modal = null; return; }
  shadeAll(0.45);
  const { x, y, w, h } = modalRect(), r = E.REGIONS[G.choice.k], top = E.UNITS.filter((u) => u.grade === 'TRANSCEND' && u.role !== E.SPECIAL_ROLE).map((u) => u.name).join('·');
  box(x, y, w, h, C.panel, C.goldEdge, 14, 2);
  text(`${r.ch}장 보상 고르기`, x + 18, y + 30, 22, C.text);
  fitText('하나만 받을 수 있어요. 미뤄 두고 「더보기」에서 나중에 받아도 돼요.', x + 18, y + 60, w - 36, 14, C.muted);
  button(x + w - 110, y + 10, 96, 40, '나중에', () => { G.choiceLater = true; modal = null; say('보상은 아래 「더보기」에서 언제든 받을 수 있어요.'); }, 'quiet');
  const desc = (c) => (c.unit ? [`초월 등급 ${top.split('·').length}종의 조합 재료`, '공격하지 않고 팔 수 없음, 조합하면 사라짐']
    : c.wisps ? ['누르면 전설 갈매미 1마리(무작위)', '바로 전력이 필요할 때'] : ['원하는 에픽 2마리를 골라 소환', '조합 재료를 맞출 때']);
  const ch = Math.min(150, (h - 96 - 2 * 12) / 3);
  r.choice.forEach((c, i) => {
    const cy = y + 84 + i * (ch + 12), col = c.unit ? GRADE_COLOR.TRANSCEND : c.wisps ? GRADE_COLOR.LEGEND : GRADE_COLOR.EPIC;
    box(x + 12, cy, w - 24, ch, 'rgba(11,30,42,.9)', col, 12, 2);
    if (c.unit) drawFit(unitImg(c.unit), x + 22, cy + 10, 100, ch - 20);
    else { const g = c.wisps ? 'LEGEND' : 'EPIC', n = Object.values(c.wisps ?? c.picks)[0]; ctx.beginPath(); ctx.arc(x + 72, cy + ch / 2, 36, 0, Math.PI * 2); ctx.fillStyle = 'rgba(7,19,28,.9)'; ctx.fill(); ctx.lineWidth = 3; ctx.strokeStyle = GRADE_COLOR[g]; ctx.stroke(); text(c.wisps ? '위습' : '선택', x + 72, cy + ch / 2 - 8, 15, GRADE_COLOR[g], 'center'); text(`×${n}`, x + 72, cy + ch / 2 + 14, 17, C.text, 'center'); }
    fitText(E.choiceText(c), x + 136, cy + 30, w - 136 - 24 - 110, 18, col);
    desc(c).forEach((l, k) => fitText(l, x + 136, cy + 60 + k * 22, w - 136 - 24 - 110, 13, C.muted));
    button(x + w - 24 - 100, cy + ch / 2 - 24, 100, 48, '받기', () => { if (E.chooseReward(G, i)) { G.choiceLater = false; modal = null; say(`${E.choiceText(c)}을(를) 받았어요.`); } }, 'primary');
  });
}
// 증강체 고르기(2026-11-15 사용자 요청: 흐름이 바뀌는 20·40웨이브 보스 처치 뒤 한 번씩). 게임은 계속 흐르고, 「나중에」로 미룰 수 있음
// 판 위 창(2026-11-21 사용자 요청: TFT처럼 판 위에) — 판 소개·증강체 고르기. 판 좌표(FIELD)에 그림(가로는 판 확대 안, 세로는 그대로)
function drawBoardModal() {
  hit(0, 0, W, H, () => {});   // 아래 판·메뉴 누르기 막음
  ctx.fillStyle = 'rgba(3,9,14,.62)'; ctx.fillRect(0, 0, W, H);
  if (modal?.kind === 'intro') drawIntro(); else drawAug();
}
// 검은 그림자(보스 미리 보기): 그림을 검게 칠한 사본(한 번만 만듦)
const maskC = new Map();
function blackMask(im, key) {
  let c = maskC.get(key); if (c) return c;
  c = document.createElement('canvas'); c.width = im.naturalWidth; c.height = im.naturalHeight; const x = c.getContext('2d');
  x.drawImage(im, 0, 0); x.globalCompositeOperation = 'source-in'; x.fillStyle = '#05080b'; x.fillRect(0, 0, c.width, c.height);
  maskC.set(key, c); return c;
}
// 이번 판 소개(2026-11-21 사용자 요청): 판 성향 · 닫힌(밴) 최상위 그림 · 후반 「???」 보스 검은 그림자 · 시작 버튼
function drawIntro() {
  const x = FIELD.x + 6, w = FIELD.w - 12, y = FIELD.y - 24, h = FIELD.h + 24, bi = E.BIAS[G.bias], cl = { armor: '143,183,255', mr: '255,178,122', split: '143,224,138' }[G.bias] ?? '220,220,220';
  const age = (performance.now() - (modal.t ?? 0)) / 1000, ap = Math.min(1, age / 0.35);
  ctx.globalAlpha = ap;
  box(x, y, w, h, 'rgba(10,26,40,.96)', C.goldEdge, 16, 2.5);
  text('이번 판 소개', x + w / 2, y + 30, 24, C.gold, 'center');
  fitText(`${E.DIFFS[G.diff]?.name ?? ''} 난이도 · ${E.RULES.waves}웨이브`, x + w / 2, y + 56, w - 30, 13, C.muted, 'center');
  // ① 판 성향
  let yy = y + 74;
  box(x + 14, yy, w - 28, 62, `rgba(${cl},.12)`, `rgba(${cl},.8)`, 12, 2);
  fitText(`판 성향: ${bi?.name ?? '-'}`, x + 30, yy + 20, w - 60, 18, `rgb(${cl})`);
  fitText(bi?.desc ?? '', x + 30, yy + 44, w - 60, 13, C.text);
  // ② 닫힌 최상위(밴)
  yy += 76;
  const shut = G.topOpen ? E.UNITS.filter((u) => E.tierOf(u.id) >= E.TOP_TIER && E.RECIPES.some((r) => r.out === u.id) && !E.topIsOpen(G, u.id)) : [];
  text(shut.length ? `이번 판 밴(닫힌 최상위) ${shut.length}종` : '이번 판은 최상위가 모두 열려 있어요', x + 18, yy + 10, 16, shut.length ? '#ff9a8a' : C.good);
  if (shut.length) {
    const per = 7, cs = Math.floor((w - 28 - (per - 1) * 6) / per), rows = Math.ceil(shut.length / per);
    shut.forEach((u, i) => {
      const cx = x + 14 + (i % per) * (cs + 6), cy = yy + 24 + Math.floor(i / per) * (cs + 20);
      box(cx, cy, cs, cs, 'rgba(7,19,28,.9)', GRADE_COLOR[u.grade], 8, 1.5);
      ctx.globalAlpha = ap * 0.55; drawFit(unitImg(u.id), cx + 3, cy + 3, cs - 6, cs - 6); ctx.globalAlpha = ap;
      ctx.strokeStyle = 'rgba(255,107,107,.85)'; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(cx + 8, cy + 8); ctx.lineTo(cx + cs - 8, cy + cs - 8); ctx.moveTo(cx + cs - 8, cy + 8); ctx.lineTo(cx + 8, cy + cs - 8); ctx.stroke();
      fitText(u.name, cx + cs / 2, cy + cs + 9, cs + 4, 11, GRADE_COLOR[u.grade], 'center');
    });
    yy += 24 + rows * (cs + 20);
  } else yy += 26;
  // ③ ??? 보스(검은 그림자)
  yy += 8;
  const look = E.waveInfo(E.RULES.waves).look, bim = enemyImg(look), bh = Math.max(90, Math.min(230, y + h - 78 - yy - 30)), tx = x + 34 + bh + 24, tw = x + w - 24 - tx;
  box(x + 14, yy, w - 28, bh + 30, 'rgba(20,6,10,.85)', 'rgba(255,107,107,.7)', 12, 2);
  if (bim) { const mk = blackMask(bim, look), pulse = 0.5 + 0.5 * Math.sin(age * 3);
    ctx.save(); ctx.shadowColor = `rgba(${cl},${0.5 + 0.4 * pulse})`; ctx.shadowBlur = 18; drawFit(mk, x + 24, yy + 10, bh + 10, bh + 10); ctx.restore(); }
  text('???', tx, yy + 34, 30, '#ff8a7a');
  fitText(`${E.RULES.lateBossFrom}웨이브부터 이 판 성향에 맞춘`, tx, yy + 66, tw, 13, C.text); fitText('강한 보스가 나타나요', tx, yy + 86, tw, 13, C.text);
  fitText({ armor: '단단한 갑옷 — 마법을 준비하세요', mr: '두꺼운 결계 — 물리를 준비하세요', split: '철벽 — 독을 준비하세요' }[G.bias] ?? '', tx, yy + 114, tw, 13, '#ffb3a8');
  // 시작
  button(x + w / 2 - 110, y + h - 66, 220, 52, '시작하기', () => { modal = null; G.biasShown = true; biasAlarm(); sfx('story'); }, 'primary');
  ctx.globalAlpha = 1;
}
// 증강체 고르기(2026-11-21 사용자 요청: 고를 때 게임 멈춤, TFT처럼 판 위에 카드 셋). 「판 보기」로 잠깐 숨겨 판을 볼 수 있음
function drawAug() {
  if (!G?.augOffer) { modal = null; return; }
  const o = G.augOffer, col = o.wave >= 40 ? GRADE_COLOR.LEGEND : GRADE_COLOR.EPIC;
  if (modal.peek) {   // 판 보기: 위에 작은 띠만
    ctx.fillStyle = 'rgba(3,9,14,.0)';
    button(W / 2 - 120, FIELD.y + 8, 240, 50, '증강체 고르기로 돌아가기', () => { modal.peek = false; }, 'primary');
    return;
  }
  text(`${o.wave}웨이브 돌파!`, W / 2, FIELD.y + 26, 16, C.muted, 'center');
  text('증강체를 고르세요', W / 2, FIELD.y + 54, 26, col, 'center');
  fitText(`게임이 멈춰 있어요 · 이 판이 끝날 때까지 계속 적용${G.augs?.length ? ` · 지금: ${G.augs.map((id) => E.AUGS[id].name).join(', ')}` : ''}`, W / 2, FIELD.y + 80, FIELD.w - 20, 12, C.muted, 'center');
  const n = o.opts.length, gap = 10, cw = Math.floor((FIELD.w - 8 - gap * (n - 1)) / n), ch = 300, cy = FIELD.y + 100;
  o.opts.forEach((id, i) => {
    const a = E.AUGS[id], cx = FIELD.x + 4 + i * (cw + gap), rb = !!a.rb, ec = rb ? '#ff7a6a' : col;
    box(cx, cy, cw, ch, rb ? 'rgba(48,16,20,.96)' : 'rgba(11,30,42,.96)', ec, 14, 2.5);
    hit(cx, cy, cw, ch, () => { if (E.chooseAug(G, i)) { modal = null; augAlarm(id); sfx('story'); } });
    if (rb) { box(cx + cw / 2 - 42, cy + 10, 84, 20, 'rgba(255,122,106,.18)', '#ff7a6a', 6, 1); text('룰 브레이커', cx + cw / 2, cy + 20, 12, '#ffb0a4', 'center'); }
    const ix = cx + cw / 2, iy = cy + 78;
    if (!fxLite) fxSprite('magic_02', ix, iy, 110, hexRGB(ec), 0.5, performance.now() / 1500);
    ctx.beginPath(); ctx.arc(ix, iy, 36, 0, Math.PI * 2); ctx.fillStyle = 'rgba(7,19,28,.95)'; ctx.fill(); ctx.lineWidth = 3; ctx.strokeStyle = ec; ctx.stroke();
    text(a.name.slice(0, 1), ix, iy + 1, 30, ec, 'center');
    fitText(a.name, ix, cy + 140, cw - 16, 17, ec, 'center');
    wrapCenter(a.desc, ix, cy + 168, cw - 18, 13, C.text, 6);
    box(cx + 12, cy + ch - 50, cw - 24, 38, 'rgba(243,216,158,.14)', C.goldEdge, 10, 1.5); text('고르기', ix, cy + ch - 31, 15, C.gold, 'center');
  });
  const by = cy + ch + 14;
  button(W / 2 - 170, by, 160, 46, o.rerolled ? '리롤 끝' : '다시 뽑기', () => { if (E.rerollAug(G)) sfx('wisp'); }, 'normal', !o.rerolled);
  button(W / 2 + 10, by, 160, 46, '판 보기', () => { modal.peek = true; }, 'quiet');
}
// 가운데 맞춤 줄바꿈(글자 단위, 최대 줄 수)
function wrapCenter(s, cx, y, maxW, size, color, maxLines = 4) {
  ctx.font = `${size}px ${FONT}`; const lines = []; let cur = '';
  for (const chh of String(s)) { if (ctx.measureText(cur + chh).width > maxW && cur) { lines.push(cur); cur = chh.trimStart(); } else cur += chh; }
  if (cur) lines.push(cur);
  lines.slice(0, maxLines).forEach((ln, k) => text(ln, cx, y + k * (size + 5), size, color, 'center'));
}
// ── 친구랑 같이하기(사용자 요청 2026-11-08: 코드를 입력해 방에 들어가고, 친구 화면을 옆에 실시간으로) ──
// 각자 자기 판을 하고, 0.25초마다 자기 판 모습(짧은 상태 글)을 서버(/defense/room)에 보내며 친구 판 모습을 받아 그린다.
// 시작(방장)·속도·일시정지는 둘이 함께 바뀐다. 서버는 전달만 하고 게임 판정은 각자 화면에서 한다.
const net = { code: '', token: '', seat: -1, ver: -1, round: 0, peer: null, prev: null, prevMap: null, peerAt: 0, prevAt: 0, peerRaw: '', peerOn: false, peerEver: false, peerGone: false, verBad: false, pend: null, busy: false, err: '', last: 0 };
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
  Object.assign(net, { code: '', token: '', seat: -1, ver: -1, round: 0, peer: null, prev: null, prevMap: null, peerAt: 0, prevAt: 0, peerRaw: '', peerOn: false, peerEver: false, peerGone: false, verBad: false, pend: null, err: '', giftSent: 0, giftRecv: 0, sigSeq: 0, sigKind: 0, sigSeen: 0 });
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
// 신호(사용자 요청 2026-11-08): 짧은 말을 친구 화면에 잠깐 띄움. 판 모습에 [번호, 종류]를 실어 보냄
const SIGNALS = [['!', '도와줘!'], ['★', '보스 온다!'], ['➜', '탐사 보내자'], ['♥', '좋아요!']];
let sigShow = null;
function sendSignal(k) { if (!coop()) return; net.sigSeq++; net.sigKind = k; say(`신호 보냄: ${SIGNALS[k][1]}`); netTick(); }
function drawSignalPicker() {
  shadeAll(0.3);
  const { x, y, w, h } = modalRect(), hh = Math.min(h, 300), yy = y + (h - hh) / 2;
  box(x, yy, w, hh, C.panel, C.goldEdge, 14, 2);
  text('친구에게 신호', x + 18, yy + 30, 20, C.text);
  button(x + w - 96, yy + 10, 82, 40, '닫기', () => { modal = null; }, 'quiet');
  const bw = (w - 36) / 2, bh = (hh - 90) / 2;
  SIGNALS.forEach(([ic, msg], i) => button(x + 12 + (i % 2) * (bw + 12), yy + 62 + Math.floor(i / 2) * (bh + 10), bw, bh, `${ic}  ${msg}`, () => { sendSignal(i); modal = null; }, i === 0 ? 'danger' : 'normal'));
}
function drawVerWarn() {   // 같이하기 중 친구와 게임 판이 다름(판 위쪽)
  if (!coop() || !net.verBad || !net.peerOn) return;
  box(FIELD.x + 10, FIELD.y + 8, FIELD.w - 20, 34, 'rgba(110,30,24,.95)', '#ff9a85', 8, 2);
  fitText('친구와 게임 판이 달라요 · 둘 다 새로고침해 주세요', FIELD.x + FIELD.w / 2, FIELD.y + 25, FIELD.w - 40, 15, '#fff3cf', 'center');
}
function drawSignalBanner() {   // 받은 신호(판 위쪽 가운데)
  drawVerWarn();
  if (!sigShow || performance.now() > sigShow.until) return;
  const a = Math.min(1, (sigShow.until - performance.now()) / 400);
  ctx.globalAlpha = a;
  ctx.font = `20px ${FONT}`;
  const tw = Math.min(W - 60, ctx.measureText(`친구: ${sigShow.msg}`).width + 70);
  box((W - tw) / 2, FIELD.y + 50, tw, 50, 'rgba(90,36,24,.95)', '#ffb08f', 12, 2.5);
  text(`${sigShow.icon}  친구: ${sigShow.msg}`, W / 2, FIELD.y + 75, 20, '#fff3cf', 'center');
  ctx.globalAlpha = 1;
}
function sendCtl(c) { net.pend = { ...(net.pend ?? {}), ...c }; netTick(); }
function setPause(on) {
  if (!G || G.over) return;
  modal = on ? { kind: 'pause' } : null;
  if (coop()) sendCtl({ paused: on });
}
function setSpeed(v) { speed = v; if (coop()) sendCtl({ speed: v }); }
// 내 판 모습: 적(번호·자리·체력%·보스·모습), 판 위 갈매미(자리·종류), 숫자 몇 개
// 같이하기 판 확인(2026-11-11): 두 사람의 게임 파일이 다르면(한쪽만 새로고침) 규칙이 달라 판이 어긋남 → 판 모습에 버전을 실어 비교.
// 버전 = 이 파일 주소의 지문(?v=…). 규칙(engine·data)이 바뀌면 이 파일의 지문도 바뀜. 예전 화면은 버전을 안 보내므로 「다름」으로 봄
const GAME_VER = (() => { try { return new URL(import.meta.url).searchParams.get('v') || 'dev'; } catch { return 'dev'; } })();
function cosForPeer(g) {
  const ids = new Set(E.fieldUnits(g).map((u) => u.id)), out = {};
  for (const [id, c] of Object.entries(cosById)) if (ids.has(id) && (c.wing || c.aura || c.base)) out[id] = [c.wing || '', c.aura || '', c.color || '', c.base || ''];
  return out;
}
const okOf = (list, v) => (list.some((q) => q[0] === v) ? v : '');
function peerCos(P, id) {   // 친구가 보낸 꾸밈(받은 값은 목록에 있는 것만 씀)
  const a = P.cs?.[id]; if (!Array.isArray(a) || fxLite) return null;
  const c = { id, wing: okOf(COS_WINGS, a[0]), aura: okOf(COS_AURAS, a[1]), color: okOf(COS_COLORS, a[2]) || '#ffd66b', base: okOf(COS_BASES, a[3]) };
  return c.wing || c.aura || c.base ? c : null;
}
function snapOf(g) {
  const r = Math.round;
  return JSON.stringify({
    v: GAME_VER, r: net.round, w: g.wave, wn: g.warn, g: g.gold, k: g.kills, md: g.missionDone, ov: g.over || '', nx: Math.max(0, Math.ceil(E.nextWaveIn(g))),
    a: E.awayUnits(g).length, st: E.storedUnits(g).length, up: g.upg, gs: net.giftSent, sg: [net.sigSeq, net.sigKind], mdg: g.missionDmg.map((v) => Math.round(v ?? 0)),
    e: g.enemies.slice(0, 150).map((e) => [e.id, r(e.x), r(e.y), r(100 * Math.max(0, e.hp) / e.max), e.boss ? 1 : 0, e.look]),
    u: E.fieldUnits(g).slice(0, 160).map((u) => [r(u.x), r(u.y), u.id]),
    cs: cosForPeer(g),   // 꾸미기를 친구 화면에도(2026-11-11)
  });
}
async function netTick() {
  if (!net.token || net.busy) return;
  net.busy = true; net.last = performance.now();
  const tok = net.token, body = { code: net.code, token: tok };
  if (net.pend) { body.ctl = net.pend; net.pend = null; }
  if (scene === 'play' && G && coop()) body.snap = snapOf(G);
  else body.snap = JSON.stringify({ v: GAME_VER, r: -1 });   // 대기실: 버전만
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
    if (s && typeof s === 'object') net.verBad = s.v !== GAME_VER;
    if (s && typeof s === 'object' && s.r === net.round) {   // 지난 판 모습은 버림
      net.prev = net.peer; net.prevAt = net.peerAt; net.peer = s; net.peerAt = performance.now();
      net.prevMap = new Map((Array.isArray(net.prev?.e) ? net.prev.e : []).filter(Array.isArray).map((e) => [e[0], e]));
      if (G && scene === 'play') {
        E.setPeerMission(G, s.mdg);   // 외부탐사를 둘이서 같이
        if (Array.isArray(s.sg) && num(s.sg[0]) > net.sigSeen) {   // 친구가 보낸 신호
          net.sigSeen = num(s.sg[0]); const k = SIGNALS[num(s.sg[1])];
          if (k) { sigShow = { msg: k[1], icon: k[0], until: performance.now() + 3500 }; sfx('signal'); }
        }
        const gift = Math.min(1e7, num(s.gs) - net.giftRecv);   // 친구가 보낸 새우(보낸 합계가 늘어난 만큼)
        if (gift > 0) { net.giftRecv += gift; if (!G.over) { G.gold += gift; say(`친구가 새우 ${gift}을(를) 보냈어요!`); } }
      }
    }
  }
}
function applyCtl(c) {
  if (num(c.round) > net.round) {   // 방장이 시작함: 둘이 같은 씨앗으로 새 판
    net.round = num(c.round); net.peer = net.prev = net.prevMap = null; net.peerRaw = ''; net.giftSent = 0; net.giftRecv = 0; net.sigSeq = 0; net.sigSeen = 0;
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
    const bad = net.peerOn && net.verBad;
    fitText(bad ? '친구와 게임 판이 달라요. 둘 다 새로고침해 주세요' : st, W / 2, y + 126, W - 60, 17, bad ? C.bad : net.peerOn ? C.good : C.muted, 'center');
    if (host) {
      button(60, y + 152, W - 120, 56, '링크 보내기', shareLink, 'normal', true, '열면 바로 이 방에 들어와요');
      button(60, y + 224, W - 120, 76, '시작하기', () => sendCtl({ start: true }), 'primary', net.peerOn && !bad, bad ? '판이 같아야 시작할 수 있어요' : net.peerOn ? '둘이 함께 시작해요' : '친구가 들어오면 누를 수 있어요');
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
    const boss = e[4] === 1, sz = boss ? 84 : 44, sh = enemyImg(e[5]);
    if (sh) drawFit(sh, x - sz / 2, y - sz * 0.75, sz, sz);
    else { ctx.beginPath(); ctx.arc(x, y - sz / 3, sz / 3, 0, Math.PI * 2); ctx.fillStyle = 'rgba(42,20,66,.92)'; ctx.fill(); }
    const bw = boss ? 70 : 32, bx = x - bw / 2, by = y - sz * 0.75 - 6;
    box(bx, by, bw, 5, 'rgba(0,0,0,.6)', null, 2);
    box(bx, by, bw * Math.max(0, Math.min(100, num(e[3]))) / 100, 5, boss ? '#ff6b6b' : '#6fd08a', null, 2);
  }
  const units = (Array.isArray(P.u) ? P.u : []).filter((u) => Array.isArray(u) && E.UNIT[u[2]]).sort((a, b) => num(a[1]) - num(b[1]));
  for (const u of units) {
    const c = peerCos(P, u[2]), z = usz(u[2]);
    if (c) cosBack(c, num(u[0]), num(u[1]), z, num(u[0]) + num(u[1]));
    drawUnitShape(u[2], num(u[0]), num(u[1]), GRADE_COLOR[E.UNIT[u[2]].grade], 2);
    if (c) cosFront(c, num(u[0]), num(u[1]), z, num(u[0]) + num(u[1]));
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
function drawPeerFull(withButtons = false, topOnly = false) {
  const P = net.peer, [stText, stCol] = peerState();
  fenceBox(8, 6, W - 16, 88);
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
  if (topOnly) { if (withButtons) { button(W - 22 - 150, FIELD.y + 8, 150, 38, `새우 ${GIFT} 보내기`, sendGift, 'normal', canGift()); button(W - 22 - 150, FIELD.y + 52, 150, 38, '신호 보내기', () => { modal = { kind: 'signal' }; }, 'normal', coop()); } return; }
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
  if (withButtons) { button(W - 22 - 150, 672 + 8, 150, 40, `새우 ${GIFT} 보내기`, sendGift, 'normal', canGift()); button(W - 22 - 150 - 130, 672 + 8, 122, 40, '신호', () => { modal = { kind: 'signal' }; }, 'normal', coop()); }
}
// 가로 화면에서 창을 연 동안: 오른쪽 위에 친구 소식 한 줄
function drawPeerBar() {
  const P = net.peer, [stText, stCol] = peerState();
  fenceBox(8, 6, W - 16, 88);
  chip('친구 판', 20, 15, C.goldEdge, 14);
  text(stText, 110, 27, 14, stCol);
  fitText(P ? (P.ov ? `${P.ov === 'win' ? '방어 성공' : '방어 실패'} · ${num(P.w)}웨이브` : `웨이브 ${num(P.w)} · 적 ${Array.isArray(P.e) ? P.e.length : 0}/${E.RULES.limit} · 경고 ${num(P.wn)}/${E.RULES.warnMax}`) : '친구 판을 받는 중…', 20, 66, W - 200, 16, C.text);
  if (modal && (NONPAUSE.has(modal.kind) || modal.kind === 'over')) button(W - 162, 22, 140, 56, '친구 판 보기', () => { modal = modal.kind === 'over' ? { kind: 'peer' } : null; }, 'normal');
}
// 세로 화면: 아래에 붙는 친구 판 줄(누르면 크게)
function drawPeerStrip() {
  const y0 = PBOT + 2, P = net.peer, [stText, stCol] = peerState();
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
  const hw = (tw - 12) / 3;
  button(tx, y0 + PEER_H - 62, hw, 44, '크게', () => { modal = { kind: 'peer' }; }, 'normal');
  button(tx + hw + 6, y0 + PEER_H - 62, hw, 44, `새우 ${GIFT}`, sendGift, 'normal', canGift());
  button(tx + 2 * (hw + 6), y0 + PEER_H - 62, hw, 44, '신호', () => { modal = { kind: 'signal' }; }, 'normal', coop());
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
let seenModal = null;
function frame(now) {
  perfTick(now - last);
  if (perfShow.on && now > last) perfShow.fps = ema(perfShow.fps, 1000 / (now - last));
  const tSim0 = performance.now();
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (running()) {
    acc += dt * speed * BASE_SPEED;
    while (acc >= 0.05) { E.step(G, 0.05); acc -= 0.05; }
    selected = selected.filter((uid) => { const u = E.unitById(G, uid); return u && !u.away; });
  }
  pfxTick(dt * speed * BASE_SPEED);
  if (perfShow.on) perfShow.sim = ema(perfShow.sim, performance.now() - tSim0);
  if (scene === 'play' && G?.over && modal?.kind !== 'over' && modal?.kind !== 'peer') { saveBest(); modal = { kind: 'over' }; }
  else if (scene === 'play' && G?.choice && !G.choiceLater && !G.over && modal?.kind !== 'choice' && (!modal || modal.kind === 'mission')) { modal = { kind: 'choice' }; info = null; }   // 6장 보상 고르기
  else if (scene === 'play' && G?.augOffer && !G.over && modal?.kind !== 'aug' && (!modal || modal.kind === 'mission')) { modal = { kind: 'aug' }; info = null; }   // 증강체 고르기(다른 창을 보고 있으면 닫은 뒤에)
  // 설명 창이 열린 채 다른 창(선택위습·강화 …)을 열면 설명 창을 닫는다(사용자 지적 2026-11-08: 새 창이 설명 창 뒤에 열렸음)
  if (modal !== seenModal) { if (info && modal) info = null; seenModal = modal; }
  const wantStrip = coop() && scene === 'play';
  if (wantStrip !== stripOn || coop() !== coopOn || (scene !== 'play') !== fullTitle) { stripOn = wantStrip; coopOn = coop(); fullTitle = scene !== 'play'; fit(); }
  musicSync(); sfxTick(); talkTick(); placeSearch();
  hits = [];
  const tDraw0 = performance.now();
  ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
  ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';   // 그림 줄일 때 고품질(기본은 낮음이라 거칠었음)
  if (scene === 'title' || scene === 'lobby') {
    drawTitleBack();
    const tx = (VW - W) / 2, ty = landscape ? 0 : Math.round((VH - H) / 2);   // 가로 배치면 가로 가운데, 세로가 길면 세로 가운데
    ctx.save(); ctx.translate(tx, ty); OX = tx; OY = ty;
    if (scene === 'title') { drawTitle(); if (modal?.kind === 'sound') drawSound(); else if (modal?.kind === 'modes') drawModes(); else if (modal?.kind === 'records') drawRecords(); else if (modal?.kind === 'tops') drawTopClears(); else if (modal?.kind === 'cosmetic') drawCustomize(); else if (modal?.kind === 'tmenu') drawTitleMenu(); else if (modal?.kind === 'rules') drawRules(); } else drawLobby();
    ctx.restore(); OX = 0; OY = 0;
    if (scene === 'title') drawOnline();   // 지금 이용자 수(화면 오른쪽 아래, 아주 작게)
  } else {
    drawBackground();
    if (landscape) {
      // 왼쪽: 위 정보 줄 + 판을 크게
      ctx.save(); ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); OS = LS; OX = BOARD_X;
      drawField(); drawHud(); drawSignalBanner(); drawToast();
      const bm = BOARD_MODAL.has(modal?.kind) ? modal : null; if (bm) drawBoardModal();
      ctx.restore(); OS = 1; OX = 0;
      if (bm) modal = null;   // 오른쪽 칸은 평소 모습(아래에서 어둡게 덮고 누르기 막음)
      // 오른쪽: 창이 열리면 창(열 전체), 없으면 위에 피해 미니판(같이하기면 친구 판) + 아래 메뉴
      if (wide) {   // 넓은 화면: 창 칸(A) + 메뉴 칸(B), 일시정지·결과 창은 오른쪽 가운데에 원래 크기로
        if (topModal()) { colA(drawModal); colB(drawBottom); }
        else if (modal) { const fx = Math.round(SIDE_X + (VW - SIDE_X - W) / 2); ctx.save(); ctx.translate(fx, 0); OX = fx; drawModal(); ctx.restore(); OX = 0; }
        else { colA(drawSideTop); colB(drawBottom); }
        if (info) colA(drawInfo);
        if (bm) { modal = bm; ctx.fillStyle = 'rgba(3,9,14,.55)'; ctx.fillRect(SIDE_X, 0, VW - SIDE_X, VH); hit(SIDE_X, 0, VW - SIDE_X, VH, () => {}); }
      } else {
      ctx.save(); ctx.translate(SIDE_X, 0); OX = SIDE_X;
      if (topModal()) { drawModal(); drawBottom(); }
      else if (modal && modal.kind !== 'peer') { drawModal(); if (coop()) drawPeerBar(); }
      else if (modal?.kind === 'peer') { drawPeerFull(true); button(W - 150, 684, 128, 44, '내 결과 보기', () => { modal = null; }, 'primary'); }
      else { if (coop()) drawPeerFull(true, true); else drawSideTop(); drawBottom(); }
      if (info) drawInfo();
      ctx.restore(); OX = 0;
      if (bm) { modal = bm; ctx.fillStyle = 'rgba(3,9,14,.55)'; ctx.fillRect(SIDE_X, 0, VW - SIDE_X, VH); hit(SIDE_X, 0, VW - SIDE_X, VH, () => {}); }
      }
    } else { drawField(); drawHud(); drawBottom(); if (stripOn) drawPeerStrip(); drawModal(); if (info) drawInfo(); drawSignalBanner(); drawToast(); }
  }
  if (scene === 'play' && !BOARD_MODAL.has(modal?.kind)) drawBossWarn();   // 판 위 창(소개·증강체)이 열려 있으면 경고 띠는 가림 방지로 안 그림
  if (tut && scene === 'play' && G && !G.over) drawTutorial();
  if (scene !== 'play') drawToast();
  if (perfShow.on) {
    perfShow.draw = ema(perfShow.draw, performance.now() - tDraw0);
    const t = `FPS ${perfShow.fps.toFixed(0)} · 그리기 ${perfShow.draw.toFixed(1)}ms · 계산 ${perfShow.sim.toFixed(1)}ms · 배율 ${dpr}${G ? ` · 적 ${G.enemies.length} · 갈매미 ${E.fieldUnits(G).length} · 효과 ${G.fx.length}` : ''}`;
    ctx.font = `12px ${FONT}`; const tw = ctx.measureText(t).width + 12;
    ctx.fillStyle = 'rgba(0,0,0,.7)'; ctx.fillRect(4, VH - 22, tw, 18); text(t, 10, VH - 13, 12, '#9fe3a8');
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
  MENU_ICON.forEach((f) => img(`${ART}/${f}.webp`));
  ['tl', 'tr', 'bl', 'br'].forEach((n) => img(`${ART}/ui/decor-corner-${n}.webp`));
  for (const [, band] of E.ENEMY_LOOK) band.forEach(enemyImg);
  E.BOSS_LOOK.forEach(enemyImg); E.REGIONS.forEach((r) => enemyImg(r.look));
  requestAnimationFrame(frame);
})();

// 시험용(자동 캡처에서 상태를 만들 때만 씀)
window.__defense = { E, esay: (e, m) => esay(e, m), say2: (u, m) => say2(u, m), talk, atkSound: (f) => atkSound(f), atkVoice: (ac, k, t, p, v) => atkVoice(ac, k, t, p, v), sfxOut: (ac) => sfxOut(ac), SFX, atkSfx, sfxOpen: () => sfxCtx(), combineChain: (r) => combineChain(r), canChain: (r) => canChain(r), progOf: (r, c) => progOf(r, c), costOf: (id) => costOf(id), pfx: (n, x, y, o) => pfx(n, x, y, o), get pfxReady() { return !!pfxLayer; }, get G() { return G; }, set scene(v) { scene = v; }, get dpr() { return dpr; }, get stats() { return stats; }, get cosmetic() { return cosmetic; }, cosSave, addClear, get music() { return music; }, start, net, lobby, get scene() { return scene; }, get speed() { return speed; }, get modal() { return modal; }, set selected(v) { selected = v; }, get selected() { return selected; }, openBook, set modal(v) { modal = v; }, get info() { return info; }, showInfo, sendSignal, get sigShow() { return sigShow; }, set peerSnap(v) { net.peer = v; net.peerAt = performance.now(); } };
