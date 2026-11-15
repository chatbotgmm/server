// 갈매미맨 랜덤 디펜스 2판: 화면·입력(규칙은 engine.js). 논리 좌표 540×960을 화면 크기에 맞춰 그린다.
// 조작(사용자 요청 2026-11-08): 갈매미는 끌어서만 옮긴다 · 누르면 고르기 · 빈 곳을 끌면 여러 마리 고르기(고른 무리는 하나를 끌면 함께) · 다른 곳을 누르면 고르기 풀림.
import * as E from './engine.js?v=1786d790303c';

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
const GRADE_SIZE = [1, 1.06, 1.13, 1.22, 1.34, 1.4, 1.65, 1.65, 1.65, 1.65];   // 초불영제는 같은 크기
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
const unitImg = (id) => img(`${ART}/units/${id}-front.webp`);
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
    o.connect(g).connect(ac.destination); o.start(t0 + at); o.stop(t0 + at + dur + 0.02);
  }
}
function setSfxVolume(v) { sfxState.vol = Math.round(Math.max(0, Math.min(1, v)) * 20) / 20; try { localStorage.setItem(SFX_KEY, String(sfxState.vol)); } catch { /* 진행 */ } }
// 판 상태가 바뀐 것을 보고 소리: 보스 등장·경고·외부탐사 성공·위습 찾음
const sfxWatch = { g: null, wave: 0, warn: 0, md: 0, fx: new WeakSet() };
function sfxTick() {
  if (scene !== 'play' || !G) return;
  if (sfxWatch.g !== G) { Object.assign(sfxWatch, { g: G, wave: G.wave, warn: G.warn, md: G.missionDone }); return; }
  if (G.wave !== sfxWatch.wave) { if (E.waveInfo(G.wave).boss) { sfx('boss'); bossAlarm(); } sfxWatch.wave = G.wave; }
  if (G.warn > sfxWatch.warn) sfx('warn');
  sfxWatch.warn = G.warn;
  if (G.missionDone > sfxWatch.md) sfx('story');
  sfxWatch.md = G.missionDone;
  for (const f of G.fx) if (f.kind === 'wisp' && !sfxWatch.fx.has(f)) { sfxWatch.fx.add(f); sfx('wisp'); }
}
// 보스 등장(2026-11-12 사용자 요청: 위험하니 보조 창을 내리고 판을 보여 주기 + 빨간 경고)
// 일시정지·결과·보상 고르기·규칙처럼 사용자가 일부러 연 큰 창은 그대로 두고, 판을 가리는 보조 창만 닫음
const BOSS_CLOSE = new Set(['book', 'upgrade', 'mission', 'more', 'meter', 'storage', 'recruit', 'sound', 'signal', 'peer']);
const bossWarn = { at: -1e9, wave: 0 };
const BOSS_WARN_MS = 2600;
function bossAlarm() {
  bossWarn.at = performance.now(); bossWarn.wave = G.wave;
  info = null;
  if (modal && BOSS_CLOSE.has(modal.kind)) modal = null;
}
function drawBossWarn() {
  const age = performance.now() - bossWarn.at;
  if (age > BOSS_WARN_MS || !G || G.over) return;
  const fade = Math.min(1, age / 150, (BOSS_WARN_MS - age) / 500), pulse = 0.55 + 0.45 * Math.abs(Math.sin(age / 190));
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
  fitText(`웨이브 ${bossWarn.wave} · 방어 ${E.armorAt(G, bossWarn.wave)} · ${E.RULES.waveLen}초 안에 잡아야 해요`, W / 2, by + 70, bw - 30, 15, '#ffd2c8', 'center');
  ctx.restore();
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
import('./pixfx.js?v=3e15b1b7250b').then((m) => { PFX = m; pfxLayer = m.createLayer(FIELD.w, FIELD.h, 1.6); }).catch(() => { /* 효과 없이 진행 */ });
function pfx(name, x, y, o = {}) { if (pfxLayer && !fxLite) pfxLayer.spawn(name, x - FIELD.x, y - FIELD.y, o); }
// 고유 기술 폭발(nova)·장판(field)은 속성마다 다른 효과: [효과, 속성 색 입힘]
const PFX_NOVA = { FIRE: ['plasma', true], ICE: ['snow', false], NATURE: ['thorns', false], HOLY: ['smite', false], DARK: ['darkx', false], WATER: ['plasma', true], LIGHTNING: ['plasma', false], NORMAL: ['plasma', true] };
const PFX_FIELD = { NATURE: ['thorns', false], ICE: ['icespikes', false], WATER: ['icespikes', true], HOLY: ['healaoe', false] };
function pfxTick(dt) {   // 판 상태가 바뀐 것을 보고 효과를 띄움
  if (!pfxLayer || scene !== 'play' || !G) return;
  if (pfxWatch.g !== G) { Object.assign(pfxWatch, { g: G, boss: G.bossDone ?? 0, md: G.missionDone }); pfxLayer.clear(); for (const f of G.fx) pfxSeen.add(f); return; }
  const boss = G.enemies.find((e) => e.boss); if (boss) pfxWatch.bossAt = { x: boss.x, y: boss.y };
  if ((G.bossDone ?? 0) !== pfxWatch.boss) { pfxWatch.boss = G.bossDone ?? 0; const p = pfxWatch.bossAt; if (p) pfx('big', p.x, p.y, { dur: 1.6, force: true }); }
  if (G.missionDone > pfxWatch.md) { pfxWatch.md = G.missionDone; pfx('quest', W / 2, FIELD.y + 230, { dur: 2.2, force: true }); }
  for (const f of G.fx) {
    if (pfxSeen.has(f)) continue; pfxSeen.add(f);
    if (f.kind === 'sig') {
      const el = f.elem ?? 'NORMAL', pts = f.pts ?? [];
      if (f.sk === 'smash') pfx('crit', f.x1, f.y1 - 6, { dur: 0.9, elem: el });
      else if (f.sk === 'execute') pfx('darkx', f.x1, f.y1, { dur: 1.0 });
      else if (f.sk === 'nova') { const nv = PFX_NOVA[el] ?? ['plasma', true]; pfx(nv[0], f.x1, f.y1 - 4, { dur: 1.2, elem: nv[1] ? el : undefined }); }
      else if (f.sk === 'bind') pfx('timestop', f.x1, f.y1, { dur: 1.2 });
      else if (f.sk === 'field') { const fd = PFX_FIELD[el] ?? ['healaoe', true]; pfx(fd[0], f.x1, f.y1, { dur: 1.6, elem: fd[1] ? el : undefined }); }
      else if (f.sk === 'storm') pts.slice(0, 2).forEach(([x, y]) => pfx('strike', x, y, { dur: 0.8 }));   // 낙뢰는 2개까지(무거움)
      else if (f.sk === 'bounce') pts.slice(0, 3).forEach(([x, y]) => pfx('zap', x, y - 8, { dur: 0.7, elem: el }));
      else if (f.sk === 'pierce') pfx('zap', f.x1, f.y1 - 8, { dur: 0.7, elem: el });
    } else if (f.kind === 'wisp') pfx('collect', f.x, f.y - 16, { dur: 1.2 });
    else if (f.kind === 'num') pfx('spark', f.x, f.y - 14, { dur: 0.5 });
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

function start(seed, mode = 'normal', diff = selDiff) {
  shake.until = 0;
  G = E.newGame(seed === undefined ? undefined : seed >>> 0, mode, diff); scene = 'play'; modal = null; speed = 1; selected = []; lastNews = 0; info = null;
  musicPlay();   // 누른 순간 안에서 재생 시작
  tut = !inRoom() && mode === 'normal' && !ls.get(TUT_KEY) ? { step: 0, n0: G.units.length, t: performance.now() } : null;
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
let cosmetic = readJson(COS_KEY, {});
if (!Array.isArray(cosmetic.slots)) cosmetic = { slots: [] };
for (const c of cosmetic.slots) if (c?.wing === 'fairy') { c.wing = ''; if (!c.aura) c.aura = 'wrap'; }   // 예전 요정 날개 → 두름 오라
const clearCount = () => Object.keys(CLEAR_PTS).reduce((a, d) => a + (stats.byDiff[d] ?? 0), 0);   // 화면에 보이는 클리어 횟수
const clearPts = () => Object.entries(CLEAR_PTS).reduce((a, [d, k]) => a + k * (stats.byDiff[d] ?? 0), 0);   // 칸 열기에 쓰는 수
// 꾸미기 포인트(2026-11-10 사용자 요청): 60웨이브 보스 뒤 남은 적까지 0마리로 끝내면(완벽 클리어) 달인 1점·장인 2점(클리어 수와 같은 기준).
// 잠긴 칸 하나 열기 3점, 무지개 색 열기 5점. 번 것(earned)과 쓴 것(spent)을 따로 두어 기기끼리 합칠 때 큰 값으로 합침
const COS_COST = { slot: 3, rainbow: 5 };
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
  stats = { ...stats, byDiff: bd, earned: mx('earned'), spent: mx('spent'), boughtSlots: mx('boughtSlots'), seen: mx('seen'), boughtRainbow: !!(stats.boughtRainbow || xs.boughtRainbow), bought: { ...(xs.bought ?? {}), ...(stats.bought ?? {}) } };
  if (x.cosmetic && Array.isArray(x.cosmetic.slots) && (Number(x.cosmetic.at) || 0) > (Number(cosmetic.at) || 0)) cosmetic = { at: x.cosmetic.at, slots: x.cosmetic.slots.map((c) => (c?.id && cosAllowed(c.id) ? c : null)), theme: x.cosmetic.theme ?? '', presets: Array.isArray(x.cosmetic.presets) ? x.cosmetic.presets : [] };
  if (Array.isArray(x.hist)) { const seen = new Set(hist.map((r) => r.at)); hist = [...hist, ...x.hist.filter((r) => r && !seen.has(r.at))].sort((a, b) => b.at - a.at).slice(0, 30); }
  for (const [k, r] of Object.entries(x.bestComp ?? {})) if (r && (!bestComp[k] || r.wave > bestComp[k].wave)) bestComp[k] = r;
  ls.set(STATS_KEY, JSON.stringify(stats)); ls.set(COS_KEY, JSON.stringify(cosmetic)); ls.set(HIST_KEY, JSON.stringify(hist)); ls.set(BESTCOMP_KEY, JSON.stringify(bestComp)); cosRebuild();
  return JSON.stringify(extraBlob()) !== before;
}
function addClear(diff) {
  const before = cosOpen(), rb = rainbowOpen();
  stats.byDiff = { ...stats.byDiff, [diff]: (stats.byDiff?.[diff] ?? 0) + 1 };
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
const NONPAUSE = new Set(['upgrade', 'mission', 'pick', 'book', 'sound', 'storage', 'peer', 'meter', 'recruit', 'signal', 'choice', 'more']);   // 일시정지(Ⅱ)·결과 창만 멈춤
const running = () => scene === 'play' && G && !G.over && (!modal || NONPAUSE.has(modal.kind));
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
    if (!down.start) { const group = selected.includes(down.unit.uid) && selected.length > 1 ? selUnits() : [down.unit]; down.start = group.filter((u) => !u.lock).map((u) => [u.uid, u.tx, u.ty]); }
    const dx = bp.x - down.x0, dy = bp.y - down.y0;
    for (const [uid, x, y] of down.start) E.moveUnit(G, uid, x + dx, y + dy, true);
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
  const h = hitAt(sp);
  if (h) h.fn(sp);
});
cv.addEventListener('pointercancel', () => { down = null; });
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
function drawLanes() { cachedLayer('lanes:' + (cosmetic.theme ?? ''), FIELD.x - 2, FIELD.y - 2, FIELD.w + 4, FIELD.h + 4, drawLanesRaw); }
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
  const L = E.LOOP, lane = 40, th = themeOf();
  box(FIELD.x, FIELD.y, FIELD.w, FIELD.h, th.bg, C.edge, 12, 1.5);
  ctx.lineWidth = lane; ctx.strokeStyle = th.lane; ctx.lineJoin = 'round';
  ctx.strokeRect(L.x0, L.y0, L.x1 - L.x0, L.y1 - L.y0);
  ctx.lineWidth = 1.5; ctx.strokeStyle = th.edge;
  ctx.strokeRect(L.x0 - lane / 2, L.y0 - lane / 2, L.x1 - L.x0 + lane, L.y1 - L.y0 + lane);
  ctx.strokeRect(L.x0 + lane / 2, L.y0 + lane / 2, L.x1 - L.x0 - lane, L.y1 - L.y0 - lane);
  text('▼', L.x0, L.y0 + 2, 16, C.goldEdge, 'center');   // 반시계 방향: 아래로 출발
  // 나타나는 곳
  ctx.beginPath(); ctx.arc(E.SPAWN.x, E.SPAWN.y, 34, 0, Math.PI * 2); ctx.strokeStyle = 'rgba(243,216,158,.25)'; ctx.setLineDash([5, 6]); ctx.stroke(); ctx.setLineDash([]);
  // 커먼이 나오는 6곳(오른쪽 길 안쪽): 발밑에 옅은 고리
  ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(154,165,173,.35)'; ctx.setLineDash([3, 4]);
  for (const sp of E.COMMON_SPOTS) { ctx.beginPath(); ctx.ellipse(sp.x, sp.y, 22, 8, 0, 0, Math.PI * 2); ctx.stroke(); }
  ctx.setLineDash([]);
}
// 판 위 갈매미 한 마리(그림자 고리 + 그림). 등급이 높을수록 크게
function drawUnitShape(id, x0, y0, ring, lw, lock = false, ox = 0, oy = 0, zm = 1) {   // ox·oy·zm: 공격 동작(그림만 움직임, 발밑 고리는 제자리)
  let z = usz(id);
  ctx.beginPath(); ctx.ellipse(x0, y0, 20 * z, 7 * z, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill();
  ctx.lineWidth = lw; ctx.strokeStyle = ring; ctx.stroke();
  const x = x0 + ox, y = y0 + oy; z *= zm;
  const im = unitImg(id), gl = E.tierOf(id) >= 4 && !fxLite ? glow(id) : null;   // 화려한 효과 끔이면 빛 테두리 없음
  if (im && gl) {   // 전설 이상: 그림 둘레가 등급 색으로 빛남(천천히 밝아졌다 어두워짐)
    const bw = 48 * z, bh = 50 * z, sc = Math.min(bw / im.naturalWidth, bh / im.naturalHeight), dw = im.naturalWidth * sc, dh = im.naturalHeight * sc;
    const dx = x - bw / 2 + (bw - dw) / 2, dy = y - bh + bh - dh, f = dw / gl.w;
    ctx.globalAlpha = 0.7 + 0.3 * Math.sin(performance.now() / 420 + x * 0.05);
    ctx.drawImage(gl.c, dx - gl.pad * f, dy - gl.pad * f, gl.c.width * f, gl.c.height * f);
    ctx.globalAlpha = 1;
  }
  drawFit(im, x - 24 * z, y - 50 * z, 48 * z, 50 * z);
  if (lock) drawLockMark(x0 + 22 * usz(id), y0 - 2);
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
  drawLanes(); drawWeather();
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
    const sz = e.boss ? 84 : 44, sh = enemyImg(e.look);
    const ha = G.t - (e.hitT ?? -9), jit = !fxLite && ha < 0.07 ? (((e.id * 7) % 3) - 1) * 1.5 : 0;   // 맞은 순간 살짝 떨림
    if (sh) drawFit(sh, e.x - sz / 2 + jit, e.y - sz * 0.75, sz, sz);
    if (sh && !fxLite && ha < 0.08) { ctx.save(); ctx.globalCompositeOperation = 'lighter'; ctx.globalAlpha = 0.45 * (1 - ha / 0.08); drawFit(sh, e.x - sz / 2 + jit, e.y - sz * 0.75, sz, sz); ctx.restore(); }   // 하얗게 번쩍
    if (!fxLite && G.t < (e.poisonUntil ?? 0)) for (let b = 0; b < 2; b++) { const ph = ((G.t * 1.6 + b * 0.5 + e.id * 0.13) % 1); ctx.beginPath(); ctx.arc(e.x - 8 + b * 14, e.y - sz * 0.3 - ph * 22, 2.5 + b, 0, Math.PI * 2); ctx.fillStyle = `rgba(120,220,110,${0.7 * (1 - ph)})`; ctx.fill(); }
    if (e.slow > 0) { ctx.beginPath(); ctx.arc(e.x, e.y + 2, sz / 3, 0, Math.PI * 2); ctx.strokeStyle = '#9fe8ff'; ctx.lineWidth = 1.5 + 2.5 * e.slow; ctx.stroke(); }   // 이속감소가 많이 쌓일수록 굵게
  }
  // 갈매미(아래쪽부터 그려 겹침이 자연스럽게)
  const gc = goalColors(), selSet = new Set(selected);
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
    const cos = fxLite ? null : cosById[u.id];   // 화려한 효과 끔이면 꾸밈도 숨김(가볍게)
    if (cos) cosBack(cos, u.x + ox, u.y + oy, z * zm, u.uid);
    drawUnitShape(u.id, u.x, u.y, selSet.has(u.uid) ? '#ffffff' : col, selSet.has(u.uid) ? 3 : 2, u.lock, ox, oy, zm);
    if (cos) cosFront(cos, u.x + ox, u.y + oy, z * zm, u.uid);
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
      else drawShot(f, age);
    } else if (f.kind === 'num' && age < 0.75 && !fxLite) {   // 보스 피해 숫자(전설 이상)
      const a = 1 - age / 0.75, big = f.tier >= 6;
      ctx.globalAlpha = Math.min(1, a * 1.6);
      const sq = f.n ?? 0, nx = Math.min(W - 30, Math.max(30, f.x + ((sq * 23) % 60) - 30)), ny = f.y - 92 - (sq % 4) * 15 - age * 40;   // 겹치지 않게 흩어 뜨리고 체력 칸 위에서
      text(fmtN(f.v), nx + 1.5, ny + 1.5, big ? 19 : 15, 'rgba(7,19,28,.9)', 'center');
      text(fmtN(f.v), nx, ny, big ? 19 : 15, big ? '#ffd76a' : '#fff1d6', 'center');
      ctx.globalAlpha = 1;
    } else if (f.kind === 'boom' && age < 0.28) {   // 범위 공격(채운 원)·스플래시(고리)
      const a = 1 - age / 0.28, rr2 = f.r * (0.55 + 0.45 * (1 - a));
      const col = f.elem === 'FIRE' ? '255,140,70' : f.type === 'phys' ? '255,179,107' : '143,200,255';
      ctx.beginPath(); ctx.arc(f.x, f.y - 10, rr2, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(${col},${(f.area ? 0.26 : 0.14) * a})`; ctx.fill();
      ctx.lineWidth = f.area ? 2.5 : 2; ctx.strokeStyle = `rgba(${col},${0.85 * a})`; ctx.stroke();
    } else if (f.kind === 'kill' && age < 0.6) {   // 꾸민 갈매미가 쓰러뜨림: 처치 효과
      const cc = cosUid.get(f.uid); if (cc?.kill) drawKillFx(cc, f, age);
    } else if (f.kind === 'sig' && age < 0.75) {
      drawSig(f, age);
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
  drawPfx();   // 화려한 효과(도트 층)
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
    if (f.sk === 'pierce') { ctx.lineWidth = 12 * a + 3; ctx.beginPath(); ctx.moveTo(f.x0, f.y0 - 24); ctx.lineTo(f.x1, f.y1 - 10); ctx.stroke(); ctx.strokeStyle = `rgba(255,255,255,${0.8 * a})`; ctx.lineWidth = 3; ctx.stroke(); }
    else if (f.sk === 'bounce' || f.sk === 'storm') {
      for (const [x, y] of f.pts ?? []) { if (f.sk === 'storm') { ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(x - 6, y - 120); ctx.lineTo(x + 6, y - 60); ctx.lineTo(x - 4, y - 50); ctx.lineTo(x, y - 8); ctx.stroke(); } ctx.beginPath(); ctx.arc(x, y - 10, 14 * (1.4 - a), 0, Math.PI * 2); ctx.fill(); }
      if (f.sk === 'bounce' && f.pts?.length > 1) { ctx.lineWidth = 3; ctx.beginPath(); f.pts.forEach(([x, y], i) => (i ? ctx.lineTo(x, y - 12) : ctx.moveTo(x, y - 12))); ctx.stroke(); }
    } else if (f.sk === 'nova' || f.sk === 'bind' || f.sk === 'field') {
      const rr = (f.r ?? 80) * (0.5 + 0.5 * (1 - a));
      ctx.lineWidth = f.sk === 'bind' ? 5 : 3; ctx.beginPath(); ctx.arc(f.x1, f.y1 - 10, rr, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      if (f.sk === 'bind') for (let i = 0; i < 6; i++) { const an = i * Math.PI / 3 + age * 3; ctx.beginPath(); ctx.moveTo(f.x1 + Math.cos(an) * rr, f.y1 - 10 + Math.sin(an) * rr); ctx.lineTo(f.x1 + Math.cos(an) * rr * 0.35, f.y1 - 10 + Math.sin(an) * rr * 0.35); ctx.stroke(); }
    } else {   // smash·execute: 대상에 큰 별·베기
      const R = 26 + 18 * (1 - a); ctx.lineWidth = 5;
      if (f.sk === 'execute') { ctx.beginPath(); ctx.moveTo(f.x1 - R, f.y1 - 10 - R); ctx.lineTo(f.x1 + R, f.y1 - 10 + R); ctx.moveTo(f.x1 + R, f.y1 - 10 - R); ctx.lineTo(f.x1 - R, f.y1 - 10 + R); ctx.stroke(); }
      else { ctx.beginPath(); for (let i = 0; i < 8; i++) { const an = i * Math.PI / 4, rr = i % 2 ? R * 0.45 : R; ctx.lineTo(f.x1 + Math.cos(an) * rr, f.y1 - 10 + Math.sin(an) * rr); } ctx.closePath(); ctx.fill(); ctx.stroke(); }
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
  const big = f.tier >= 4, sc = (f.job === '보조' ? 0.75 : 1) * (big ? 1.4 : 1), col = ELEM_COL[f.elem] ?? (f.type === 'phys' ? '255,179,107' : '143,200,255');
  const T = f.near ? 0.07 : Math.max(0.08, Math.min(0.22, dist / 950));   // 날아가는 시간
  const ang = Math.atan2(dy, dx), seed = (f.t * 1000 + f.x0) % 97;
  if (age < T) {
    const p = age / T, x = sx + dx * p, y = sy + dy * p;
    if (f.near && f.type === 'phys') {   // 근접 물리: 적 앞에서 칼날 호
      ctx.beginPath(); ctx.arc(tx - Math.cos(ang) * 6, ty - Math.sin(ang) * 6, 14 * sc, ang - 1.1 + p * 0.6, ang + 0.2 + p * 0.9);
      ctx.strokeStyle = `rgba(${col},${0.9})`; ctx.lineWidth = 3.2 * sc; ctx.lineCap = 'round'; ctx.stroke(); ctx.lineCap = 'butt';
    } else if (f.elem === 'LIGHTNING') {   // 번개: 지그재그(바로 닿음)
      ctx.beginPath(); ctx.moveTo(sx, sy);
      for (let i = 1; i < 6; i++) { const q = i / 6, off = (((seed * (i + 3)) % 13) - 6) * 1.6; ctx.lineTo(sx + dx * q - Math.sin(ang) * off, sy + dy * q + Math.cos(ang) * off); }
      ctx.lineTo(tx, ty); ctx.strokeStyle = `rgba(${col},${0.95 - p * 0.4})`; ctx.lineWidth = 2.2 * sc; ctx.stroke();
    } else if (f.elem === 'HOLY') {   // 성: 빛줄기
      ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(x, y); ctx.strokeStyle = `rgba(${col},0.35)`; ctx.lineWidth = 7 * sc; ctx.stroke();
      ctx.strokeStyle = 'rgba(255,255,255,0.9)'; ctx.lineWidth = 2 * sc; ctx.stroke();
    } else {
      // 꼬리(불·어둠·물은 길게, 나머지는 짧게)
      const tail = f.elem === 'FIRE' || f.elem === 'DARK' ? 3 : f.elem === 'WATER' ? 2 : 1;
      for (let i = tail; i >= 1; i--) { const q = Math.max(0, p - i * 0.12); ctx.beginPath(); ctx.arc(sx + dx * q, sy + dy * q, (3 + (tail - i)) * sc * 0.8, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},${0.18 + 0.12 * (tail - i)})`; ctx.fill(); }
      ctx.save(); ctx.translate(x, y); ctx.rotate(ang);
      if (f.elem === 'ICE') { ctx.beginPath(); ctx.moveTo(8 * sc, 0); ctx.lineTo(0, 3.5 * sc); ctx.lineTo(-6 * sc, 0); ctx.lineTo(0, -3.5 * sc); ctx.closePath(); ctx.fillStyle = `rgba(${col},0.95)`; ctx.fill(); ctx.strokeStyle = 'rgba(255,255,255,.9)'; ctx.lineWidth = 1; ctx.stroke(); }
      else if (f.elem === 'NATURE') { ctx.rotate(age * 30); ctx.beginPath(); ctx.ellipse(0, 0, 6 * sc, 2.6 * sc, 0, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},0.95)`; ctx.fill(); }
      else if (f.type === 'phys' && f.elem === 'NORMAL') { ctx.beginPath(); ctx.moveTo(-9 * sc, 0); ctx.lineTo(6 * sc, 0); ctx.strokeStyle = `rgba(${col},0.95)`; ctx.lineWidth = 2.4 * sc; ctx.stroke(); ctx.beginPath(); ctx.moveTo(7 * sc, 0); ctx.lineTo(2 * sc, 2.6 * sc); ctx.lineTo(2 * sc, -2.6 * sc); ctx.closePath(); ctx.fillStyle = `rgba(${col},1)`; ctx.fill(); }   // 화살
      else { const r = (f.elem === 'FIRE' ? 5 : f.elem === 'DARK' ? 4.6 : 4) * sc; ctx.beginPath(); ctx.arc(0, 0, r * 1.7, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},0.22)`; ctx.fill(); ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fillStyle = `rgba(${col},0.98)`; ctx.fill(); if (f.elem === 'DARK') { ctx.lineWidth = 1.4; ctx.strokeStyle = 'rgba(40,10,70,.9)'; ctx.stroke(); } }
      ctx.restore();
    }
    return;
  }
  const a2 = age - T;
  if (a2 < 0.13) {   // 닿음: 불꽃 파편
    const q = a2 / 0.13, n = big ? 7 : 5;
    ctx.strokeStyle = `rgba(${col},${0.9 * (1 - q)})`; ctx.lineWidth = big ? 2.2 : 1.6;
    for (let i = 0; i < n; i++) { const an = ang + Math.PI + (i - (n - 1) / 2) * 0.55 + ((seed + i * 17) % 7 - 3) * 0.05, r0 = 3 + q * 6, r1 = r0 + (big ? 9 : 6) * (1 - q * 0.5); ctx.beginPath(); ctx.moveTo(tx + Math.cos(an) * r0, ty + Math.sin(an) * r0); ctx.lineTo(tx + Math.cos(an) * r1, ty + Math.sin(an) * r1); ctx.stroke(); }
    if (f.tier >= 6 && f.boss && a2 < 0.02 && shake.until < G.t + 0.05) { shake.until = G.t + 0.12; shake.mag = f.tier >= 8 ? 3 : 2; }
  }
}
// ── 위 정보 줄 ──
function drawHud() {
  box(8, 6, W - 16, 88, C.header, C.edge, 10);
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
    const t1 = `깎기 물${Math.round(shSum)}`, t2 = ` · 마${Math.round(mshSum)}`;
    const tw = ctx.measureText(t1 + t2).width, sc2 = Math.min(1, maxW / tw), fs = Math.max(10, Math.floor(12 * sc2));
    text(t1, sx, 28, fs, okCol(shSum));
    ctx.font = `${fs}px ${FONT}`;
    text(t2, sx + ctx.measureText(t1).width, 28, fs, okCol(mshSum, mrNow));
  }
  const rest = E.resting(G);
  const line2 = rest ? `쉬는 시간 · 다음 웨이브까지 ${left}초` : wi.boss ? (G.bossDone === G.wave ? '보스 처치! 다음 웨이브까지 ' + left + '초' : `보스를 ${Math.ceil(Math.max(0, E.RULES.waveLen - G.waveT))}초 안에 잡기`) : `다음 웨이브 ${left}초`;
  text(line2, 20, 58, 15, rest ? C.good : wi.boss ? (G.bossDone === G.wave ? C.good : left <= 12 ? C.bad : '#ffc2a8') : C.muted);
  if (G.wave < E.RULES.waves) {   // 다음 웨이브 종류 미리 보기(사용자 요청 2026-11-08)
    const nx = E.waveInfo(G.wave + 1); nx.armor = E.armorAt(G, G.wave + 1);
    ctx.font = `15px ${FONT}`;
    const lx = 20 + ctx.measureText(line2).width + 10;
    const rf = nx.boss ? 0 : E.riftAt(G.wave + 1);   // 균열 저항(41웨이브부터 라인 적이 전설·히든 이하 피해를 덜 받음)
    fitText(`▸ 다음 ${nx.boss ? `보스! 방어 ${nx.armor}` : `방어 ${nx.armor}`}${rf ? ` · 균열 저항 ${Math.round(rf * 100)}%` : ''}`, lx, 58, W - 30 - lx, 14, nx.boss ? '#ff8a7a' : rf ? '#c9a3ff' : nx.armor > armorNow ? C.goldEdge : C.muted);
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
    const lg = ctx.createLinearGradient(40, 0, W - 40, 0); lg.addColorStop(0, 'rgba(217,187,127,0)'); lg.addColorStop(0.5, 'rgba(217,187,127,.75)'); lg.addColorStop(1, 'rgba(217,187,127,0)');
    ctx.fillStyle = lg; ctx.fillRect(40, dy - 0.75, W - 80, 1.5);
    ctx.save(); ctx.translate(cx, dy); ctx.rotate(Math.PI / 4); box(-5, -5, 10, 10, C.gold, null, 2); ctx.restore();
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
const moreCount = () => (pickCount() ? 1 : 0) + ((G.tickets ?? 0) >= E.RULES.recruitCost ? 1 : 0) + (G.choice ? 1 : 0);   // 할 수 있는 것 수(빨간 점)
const moreSub = () => [G.choice && '보상 고르기', (G.rerolls ?? 0) > 0 && `리롤권 ${G.rerolls}`, pickCount() && `선택위습 ${pickCount()}`, (G.tickets ?? 0) >= E.RULES.recruitCost && '모집 가능'].filter(Boolean).join(' · ') || `창고 ${E.storedUnits(G).length}마리`;
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
  ];
  const cy0 = G.choice ? 76 : 0;   // 미뤄 둔 보상 고르기(맨 위)
  if (G.choice) button(x + 12, y + 64, w - 24, 64, `${E.REGIONS[G.choice.k].ch}장 보상 고르기`, () => { modal = { kind: 'choice' }; }, 'primary', true, '초월 갈매미맨 · 전설 위습 1 · 에픽 선택 위습 2 중 하나');
  const gw = (w - 36) / 2, gh = Math.min(96, (h - 80 - 12 - cy0) / 2);
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
function drawUnitPanel(y0, u) {
  const info = E.UNIT[u.id], k = E.kit(u.id);
  drawFit(unitImg(u.id), 18, y0 + 8, 68, 70);
  hit(18, y0 + 8, 68, 70, () => showInfo(u.id));
  fitText(info.name, 94, y0 + 22, 150, 21, C.text);
  ctx.font = `21px ${FONT}`;
  let cx = 94 + Math.min(150, ctx.measureText(info.name).width) + 8;
  cx += chip(E.GRADE_NAME[info.grade], cx, y0 + 11, GRADE_COLOR[info.grade]) + 6;
  chip(k.special ? '조합 재료' : `${k.type === 'phys' ? '물딜' : '마딜'} ${k.job}`, cx, y0 + 11, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag);
  const up = E.upgMul(G, k.tier), gp = E.gapOf(G, k);
  if (k.special) fitText('공격하지 않아요 · 초월 등급 조합 재료', 94, y0 + 50, W - 210, 15, C.muted);
  else fitText(`공격 ${Math.round(k.dmg * up)} · ${gp * (E.RULES.atkSpeed ?? 1) <= 0.6 ? '빠름' : gp * (E.RULES.atkSpeed ?? 1) >= 1.1 ? '느림' : '보통'}(${gp.toFixed(2)}초) · 사거리 ${k.range}`, 94, y0 + 50, W - 210, 15, C.muted);
  tipLines(E.kitTips(k), 94, y0, W - 210, k.special ? C.goldEdge : k.type === 'phys' ? C.phys : C.mag);
  button(W - 112, y0 + 10, 90, 40, '닫기', () => { selected = []; }, 'quiet');
  // 에픽 리롤(2026-11-12): 보상으로 받은 에픽만, 리롤권이 있을 때
  if (E.tierOf(u.id) >= 3 && u.got && info.role !== E.SPECIAL_ROLE) {   // 에픽 이상(2026-11-12)
    const ok = E.canReroll(G, u);
    button(W - 222, y0 + 10, 104, 40, `리롤 (${G.rerolls ?? 0})`, () => { const was = info.name, nu = E.rerollUnit(G, u.uid); if (nu) say(`리롤: ${was} → ${E.UNIT[nu.id].name}`); else say('리롤권이 없어요. 10웨이브 보스마다 1개 받아요.'); }, ok ? 'primary' : 'normal');   // 누르면 다른 에픽으로(리롤권 1개)
  }
  const list = E.recipesUsing(u.id).map((r) => ({ r, ok: canMake(r, u.uid) })).sort((a, b) => (b.ok - a.ok) || (E.tierOf(a.r.out) - E.tierOf(b.r.out)));
  text(list.length ? '이 갈매미로 만들 수 있는 것' : '이 갈매미가 들어가는 조합법이 없어요', 22, y0 + 100, 15, C.gold);
  const cnt = E.countOnField(G);
  // 버튼 6개는 3개씩 두 줄(사용자 요청 2026-11-08: 휴대폰에서 작음), 조합법 줄은 남는 높이만큼
  const ex = Math.max(0, PBOT - H), BH = Math.min(70, 44 + ex * 0.15), b6 = (W - 44 - 12) / 3;
  const by2 = PBOT - 16 - BH, by1 = by2 - 6 - BH, rowsN = Math.max(1, Math.min(4, Math.floor((by1 - 6 - (y0 + 112)) / 50)));
  // 2026-11-11 사용자 지적(태초가 들어가는 마딜이 없다고 보임 — 칸이 한 줄뿐이라 둘째 조합법이 「조합법 +1」 뒤에 숨음):
  // 줄 수보다 조합법이 많으면 작은 카드(그림·이름·물딜/마딜)로 나란히 보여 줌. 누르면 그 조합법 자리로 조합법 창을 엶
  const shown = list.length <= rowsN ? (list.forEach(({ r, ok }, i) => recipeRow(r, ok, 18, y0 + 112 + i * 50, W - 36, 46, cnt, u.uid)), list.length)
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
      const sub = `${k.type === 'phys' ? '물딜' : '마딜'} · ${ok ? '조합 가능' : E.GRADE_NAME[out.grade]}`;
      ctx.font = `14px ${FONT}`; const nw = ctx.measureText(out.name).width; ctx.font = `12px ${FONT}`;
      const cw = Math.min(190, Math.max(96, h + 12 + Math.max(nw, ctx.measureText(sub).width)));
      if (cx + cw > x + w + 0.5 && cx > x) break;
      const ww = Math.min(cw, x + w - cx);
      box(cx, cy, ww, h, ok ? 'rgba(43,122,98,.25)' : 'rgba(11,30,42,.7)', ok ? '#2b7a62' : GRADE_COLOR[out.grade], 8, ok ? 2 : 1.5);
      drawFit(unitImg(r.out), cx + 3, cy + 3, h - 6, h - 6);
      fitText(out.name, cx + h + 2, cy + 15, ww - h - 8, 14, GRADE_COLOR[out.grade]);
      fitText(sub, cx + h + 2, cy + 33, ww - h - 8, 12, ok ? C.good : k.type === 'phys' ? C.phys : C.mag);
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
  fitText(out.name, x + 50, y + 15, 92, 16, GRADE_COLOR[out.grade]);
  const gn = goalNeed();
  if (gn[r.out]) { ctx.font = `16px ${FONT}`; goalDots(gn[r.out], x + 50 + Math.min(92, ctx.measureText(out.name).width) + 8 + (gn[r.out].length - 1) * 12, y + 15, 5); }   // 이름 바로 옆   // 목표에 필요한 중간 재료를 만드는 줄
  text(`${E.GRADE_NAME[out.grade]}${r.hidden ? ' · 히든 조합' : ''}`, x + 50, y + 34, 12, C.muted);
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
  button(bx + 46, y + 5, 58, h - 10, capped ? `상위${E.RULES.topCap}` : '조합', () => { const nu = combineNow(r, keepUid); if (nu) { selected = [nu.uid]; say(`${out.name} 완성!`); } }, 'primary', ok);
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
  rows = rows.map((r) => ({ r, ok: canMake(r), frac: have(r, cnt) / r.in.length }));
  if (bookOpt.ready) rows = rows.filter((it) => it.ok);
  const items = [];
  if (bookOpt.near) {   // 재료가 많이 모인 순(등급 묶음 없이)
    rows.sort((a, b) => (b.frac - a.frac) || (E.tierOf(b.r.out) - E.tierOf(a.r.out)));
    items.push(...rows);
  } else {
    rows.sort((a, b) => (E.tierOf(a.r.out) - E.tierOf(b.r.out)) || (b.ok - a.ok));
    let lastG = '';
    for (const it of rows) { const g = E.UNIT[it.r.out].grade; if (g !== lastG) { items.push({ head: g }); lastG = g; } items.push(it); }
  }
  if (!items.length) fitText(q ? `「${q}」에 맞는 조합법이 없어요` : '해당하는 조합법이 없어요', W / 2, top + 50, w - 40, 15, C.muted, 'center');
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
  if (G.cosNews) fitText(G.cosNews, x + w - 24, y + 256 + hh, w - 130, 13, C.good, 'right');
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
  fitText(`달인·장인 클리어 ${clearCount()}번 · 보상 ${pts}번(장인은 2번) · 칸 ${open}/${COS_SLOTS.length}${next ? ` · 다음 칸 ${next}번` : ''} · 포인트 ${pointsLeft()}점`, x + 18, y + 66, w - 36, 14, C.muted);
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
  const x = 40, w = W - 80, items = [['기록', '지난 판 기록·조합', () => { modal = { kind: 'records' }; }], ['꾸미기', '클리어 보상: 날개·오라·효과', openCustomize, cosOpen() > (stats.seen ?? 0)],
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
  const x = 50, y = 220, w = W - 100, h = 546;
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
  text('이번 웨이브 피해', 22, 30, 17, C.gold);
  const rows = E.fieldUnits(G).concat(E.awayUnits(G)).map((u) => ({ u, d: G.dmgWave?.[u.uid] ?? 0 })).filter((r) => r.d > 0).sort((a, b) => b.d - a.d).slice(0, 8);
  const top = rows[0]?.d ?? 1;
  if (!rows.length) fitText('아직 이번 웨이브에 피해를 준 갈매미가 없어요', W / 2, 90, W - 60, 14, C.muted, 'center');
  rows.forEach((r, i) => {
    const y = 52 + i * 50, col = GRADE_COLOR[E.UNIT[r.u.id].grade];
    drawFit(unitImg(r.u.id), 18, y, 40, 44);
    fitText(`${E.UNIT[r.u.id].name}${r.u.away ? ' (탐사)' : ''}`, 64, y + 14, 180, 14, col);
    box(64, y + 27, W - 64 - 120, 8, 'rgba(0,0,0,.45)', null, 4);
    box(64, y + 27, (W - 64 - 120) * r.d / top, 8, col, null, 4);
    fitText(fmtN(r.d), W - 24, y + 22, 90, 14, C.text, 'right');
  });
  const hy = 470;
  ctx.fillStyle = 'rgba(145,122,76,.35)'; ctx.fillRect(22, hy, W - 44, 1);
  if (!TOUCH) fitText('단축키 1~5 위습 · U 강화 · M 탐사 · S 창고 · B 조합법 · P 선택 · D 피해 · L 고정', 22, hy + 30, W - 44, 12, C.muted);   // 터치 기기에는 필요 없음(2026-11-11 검토)
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
    else if (E.topBlocked(G, r) && E.recipeUnits(G, r)) fitText(`재료는 모두 있지만 상위(제한~영원)는 ${E.RULES.topCap}마리까지예요. 하나를 팔거나 재료로 쓰면 만들 수 있어요.`, W / 2, y + h - 107, w - 40, 13, C.bad, 'center');
    else if (miss) wrap(`더 필요: ${miss}`, x + 22, y + h - 127, w - 44, 13, C.goldEdge, 19);
    else fitText('하위 재료는 다 있어요. 아래 단부터 조합해 올라가세요.', W / 2, y + h - 107, w - 40, 14, C.goldEdge, 'center');
    ctx.restore();
    const gi = G.goals.indexOf(id), nb = pickable ? 3 : 2, bw = (w - 28 - 12 * (nb - 1)) / nb;
    button(x + 14, y + h - 76, bw, 56, gi >= 0 ? '★ 목표에서 빼기' : '☆ 목표로', () => { const on = E.toggleGoal(G, id); say(on ? `목표: ${u.name} (하위 재료까지 색 테두리)` : '목표에서 뺐어요'); }, gi >= 0 ? 'primary' : 'normal');
    if (E.topBlocked(G, r) && !E.recipeUnits(G, r)) fitText(`상위(제한~영원)는 ${E.RULES.topCap}마리까지예요(지금 ${E.topCount(G)}마리).`, W / 2, y + h - 142, w - 30, 13, C.bad, 'center');
    if (pickable) pickBtn(x + 26 + bw, bw);
    button(x + 14 + (nb - 1) * (bw + 12), y + h - 76, bw, 56, '조합', () => { const nu = combineNow(r); if (nu) { selected = [nu.uid]; say(`${u.name} 완성!`); if (info.stack.length) { const back = info.stack[info.stack.length - 1]; info = { id: back, stack: info.stack.slice(0, -1) }; } } }, 'primary', ready);   // 창을 닫지 않음: 재료에서 들어왔으면 위 목표로 돌아감
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
  box(8, 6, W - 16, 88, C.header, C.edge, 10);
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
  // 설명 창이 열린 채 다른 창(선택위습·강화 …)을 열면 설명 창을 닫는다(사용자 지적 2026-11-08: 새 창이 설명 창 뒤에 열렸음)
  if (modal !== seenModal) { if (info && modal) info = null; seenModal = modal; }
  const wantStrip = coop() && scene === 'play';
  if (wantStrip !== stripOn || coop() !== coopOn || (scene !== 'play') !== fullTitle) { stripOn = wantStrip; coopOn = coop(); fullTitle = scene !== 'play'; fit(); }
  musicSync(); sfxTick(); placeSearch();
  hits = [];
  const tDraw0 = performance.now();
  ctx.setTransform(cv.width / VW, 0, 0, cv.height / VH, 0, 0);
  ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';   // 그림 줄일 때 고품질(기본은 낮음이라 거칠었음)
  if (scene === 'title' || scene === 'lobby') {
    drawTitleBack();
    const tx = (VW - W) / 2, ty = landscape ? 0 : Math.round((VH - H) / 2);   // 가로 배치면 가로 가운데, 세로가 길면 세로 가운데
    ctx.save(); ctx.translate(tx, ty); OX = tx; OY = ty;
    if (scene === 'title') { drawTitle(); if (modal?.kind === 'sound') drawSound(); else if (modal?.kind === 'modes') drawModes(); else if (modal?.kind === 'records') drawRecords(); else if (modal?.kind === 'cosmetic') drawCustomize(); else if (modal?.kind === 'tmenu') drawTitleMenu(); else if (modal?.kind === 'rules') drawRules(); } else drawLobby();
    ctx.restore(); OX = 0; OY = 0;
    if (scene === 'title') drawOnline();   // 지금 이용자 수(화면 오른쪽 아래, 아주 작게)
  } else {
    drawBackground();
    if (landscape) {
      // 왼쪽: 위 정보 줄 + 판을 크게
      ctx.save(); ctx.translate(BOARD_X, 0); ctx.scale(LS, LS); OS = LS; OX = BOARD_X;
      drawField(); drawHud(); drawSignalBanner(); drawToast();
      ctx.restore(); OS = 1; OX = 0;
      // 오른쪽: 창이 열리면 창(열 전체), 없으면 위에 피해 미니판(같이하기면 친구 판) + 아래 메뉴
      if (wide) {   // 넓은 화면: 창 칸(A) + 메뉴 칸(B), 일시정지·결과 창은 오른쪽 가운데에 원래 크기로
        if (topModal()) { colA(drawModal); colB(drawBottom); }
        else if (modal) { const fx = Math.round(SIDE_X + (VW - SIDE_X - W) / 2); ctx.save(); ctx.translate(fx, 0); OX = fx; drawModal(); ctx.restore(); OX = 0; }
        else { colA(drawSideTop); colB(drawBottom); }
        if (info) colA(drawInfo);
      } else {
      ctx.save(); ctx.translate(SIDE_X, 0); OX = SIDE_X;
      if (topModal()) { drawModal(); drawBottom(); }
      else if (modal && modal.kind !== 'peer') { drawModal(); if (coop()) drawPeerBar(); }
      else if (modal?.kind === 'peer') { drawPeerFull(true); button(W - 150, 684, 128, 44, '내 결과 보기', () => { modal = null; }, 'primary'); }
      else { if (coop()) drawPeerFull(true, true); else drawSideTop(); drawBottom(); }
      if (info) drawInfo();
      ctx.restore(); OX = 0;
      }
    } else { drawField(); drawHud(); drawBottom(); if (stripOn) drawPeerStrip(); drawModal(); if (info) drawInfo(); drawSignalBanner(); drawToast(); }
  }
  if (scene === 'play') drawBossWarn();
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
window.__defense = { E, pfx: (n, x, y, o) => pfx(n, x, y, o), get pfxReady() { return !!pfxLayer; }, get G() { return G; }, set scene(v) { scene = v; }, get dpr() { return dpr; }, get stats() { return stats; }, get cosmetic() { return cosmetic; }, cosSave, addClear, get music() { return music; }, start, net, lobby, get scene() { return scene; }, get speed() { return speed; }, get modal() { return modal; }, set selected(v) { selected = v; }, get selected() { return selected; }, openBook, set modal(v) { modal = v; }, get info() { return info; }, showInfo, sendSignal, get sigShow() { return sigShow; }, set peerSnap(v) { net.peer = v; net.peerAt = performance.now(); } };
