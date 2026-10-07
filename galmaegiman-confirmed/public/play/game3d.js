// 갈매미맨: 출근길 — 2.5D 시험판 (3D 무대 + 2D 캐릭터 그림)
// 규칙은 2D 시험판(game.js)과 같고, 화면만 Three.js로 그립니다. 빌드: scripts/build-play.mjs → dist/play/game3d.js
// 그림: public/play/sprites/<이름>.png 이 있으면 그 그림, 없으면 임시 도형. 캐릭터 그림은 "오른쪽을 보는" 모습 기준.
import * as THREE from 'three';

// ── 단위: 1 = 2D 시험판의 100px. 발바닥 y = 0 이 땅 ──
const ARENA = 30, VIEW = 9.6, WORLD = ARENA + VIEW;
const GRAVITY = 14;
const ANIMS = {
  player_idle: [['player_idle_1', 'player_idle_2'], 3, true],
  player_walk: [['player_walk_1', 'player_walk_2', 'player_walk_3', 'player_walk_4'], 10, true],
  player_jump: [['player_jump'], 1, false],
  player_attack: [['player_attack_1', 'player_attack_2', 'player_attack_3'], 16, false],
  player_hurt: [['player_hurt'], 1, false],
  kkiruk_walk: [['kkiruk_walk_1', 'kkiruk_walk_2'], 6, true],
  kkiruk_hurt: [['kkiruk_hurt'], 1, false],
  diver_fly: [['diver_fly_1', 'diver_fly_2'], 8, true],
  diver_dive: [['diver_dive'], 1, false],
  diver_hurt: [['diver_hurt'], 1, false],
  boss_idle: [['boss_idle_1', 'boss_idle_2'], 4, true],
  boss_dash: [['boss_dash'], 1, false],
  boss_throw: [['boss_throw'], 1, false],
  boss_hurt: [['boss_hurt'], 1, false],
};
// 화면 키(단위)와 몸통 충돌 상자(폭, 높이)
const SIZE = { player: [1.15, 0.5, 0.8], walker: [0.84, 0.5, 0.6], diver: [0.76, 0.5, 0.5], boss: [1.9, 1.0, 1.35], item: [0.4, 0.4, 0.4], feather: [0.14, 0.25, 0.15] };
const PLATFORMS = [[7, 1.1, 1.92], [12.5, 1.4, 2.56], [19, 1.2, 1.92], [24.5, 1.5, 2.56]]; // [왼쪽 x, 높이, 폭]
const WAVES = [
  { at: 2.5, spawn: [['walker', 9]] },
  { at: 6, spawn: [['walker', 12], ['walker', 13]] },
  { at: 9.5, spawn: [['diver', 14.5]] },
  { at: 14, spawn: [['walker', 19], ['diver', 19.5]] },
  { at: 18.5, spawn: [['walker', 23], ['walker', 24], ['diver', 23.5]] },
  { at: 23.5, spawn: [['diver', 27.5], ['walker', 28], ['diver', 28.5]] },
];

// ── 임시 도형(그림이 없을 때). 2D 시험판과 같은 그림을 캔버스에 그립니다 ──
function painter(ctx) {
  let fill = '#000', alpha = 1, stroke = '#000', width = 1;
  const hex = c => '#' + c.toString(16).padStart(6, '0');
  const F = () => { ctx.globalAlpha = alpha; ctx.fillStyle = fill; ctx.fill(); ctx.globalAlpha = 1; };
  const S = () => { ctx.lineWidth = width; ctx.strokeStyle = stroke; ctx.lineJoin = 'round'; ctx.stroke(); };
  const ell = (x, y, w, h) => { ctx.beginPath(); ctx.ellipse(x, y, w / 2, h / 2, 0, 0, Math.PI * 2); };
  const tri = (a, b, c, d, e, f) => { ctx.beginPath(); ctx.moveTo(a, b); ctx.lineTo(c, d); ctx.lineTo(e, f); ctx.closePath(); };
  const poly = pts => { ctx.beginPath(); pts.forEach((p, i) => i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)); ctx.closePath(); };
  const g = {
    fillStyle(c, a = 1) { fill = hex(c); alpha = a; }, lineStyle(w, c) { width = w; stroke = hex(c); },
    fillEllipse(...a) { ell(...a); F(); }, strokeEllipse(...a) { ell(...a); S(); },
    fillCircle(x, y, r) { ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); F(); }, strokeCircle(x, y, r) { ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); S(); },
    fillRect(x, y, w, h) { ctx.beginPath(); ctx.rect(x, y, w, h); F(); }, strokeRect(x, y, w, h) { ctx.beginPath(); ctx.rect(x, y, w, h); S(); },
    fillRoundedRect(x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x, y, w, h, r); F(); }, strokeRoundedRect(x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x, y, w, h, r); S(); },
    fillTriangle(...a) { tri(...a); F(); }, strokeTriangle(...a) { tri(...a); S(); },
    lineBetween(a, b, c, d) { ctx.beginPath(); ctx.moveTo(a, b); ctx.lineTo(c, d); S(); },
    fillPoints(p) { poly(p); F(); }, strokePoints(p) { poly(p); S(); },
  };
  return g;
}
const C = { line: 0x4b3f33, white: 0xfdfbf6, fluff: 0xece6da, crest: 0xd8c08a, stone: 0xcdbf9f, stoneDark: 0x9a8d72, beak: 0xe2b04a, blush: 0xf4a6a6, leg: 0xd9a066 };
const V = (arr) => arr.map(([x, y]) => ({ x, y }));
function drawGull(g, o) {
  const cx = 50, cy = 60 + (o.bob || 0), st = o.step || 0;
  g.lineStyle(3, C.line);
  g.fillStyle(C.leg); g.fillRoundedRect(cx - 14 + st * 5, cy + 22, 9, 14, 3); g.fillRoundedRect(cx + 6 - st * 5, cy + 22, 9, 14, 3);
  const wing = (pts) => { g.fillStyle(C.stone); g.fillPoints(pts); g.strokePoints(pts); g.lineStyle(2, C.stoneDark); g.lineBetween(pts[0].x, pts[0].y, pts[2].x, pts[2].y); g.lineStyle(3, C.line); };
  if (!o.wingFwd) wing(V([[cx - 6, cy - 20], [cx - 42, cy - 40 + (o.flap || 0)], [cx - 46, cy + 2], [cx - 20, cy + 16]]));
  g.fillStyle(C.white); g.fillEllipse(cx, cy, 64, 60); g.strokeEllipse(cx, cy, 64, 60);
  g.fillStyle(C.fluff); g.fillEllipse(cx - 2, cy + 14, 36, 20);
  g.fillStyle(C.crest);
  [[cx - 16, cy - 24, cx - 9, cy - 46, cx - 2, cy - 26], [cx - 5, cy - 28, cx + 4, cy - 54, cx + 13, cy - 28], [cx + 8, cy - 26, cx + 19, cy - 44, cx + 22, cy - 20]]
    .forEach(t => { g.fillTriangle(...t); g.strokeTriangle(...t); });
  if (o.eye === 'x') { g.lineStyle(3, 0x222222); g.lineBetween(cx + 10, cy - 10, cx + 18, cy - 2); g.lineBetween(cx + 18, cy - 10, cx + 10, cy - 2); g.lineStyle(3, C.line); }
  else if (o.eye === '-') { g.lineStyle(3, 0x222222); g.lineBetween(cx + 9, cy - 6, cx + 19, cy - 6); g.lineStyle(3, C.line); }
  else { g.fillStyle(0x222222); g.fillCircle(cx + 14, cy - 6, 4.5); g.fillStyle(0xffffff); g.fillCircle(cx + 15.5, cy - 7.5, 1.5); }
  g.fillStyle(C.blush, 0.85); g.fillEllipse(cx + 19, cy + 5, 12, 7);
  g.fillStyle(C.beak); g.fillTriangle(cx + 24, cy - 4, cx + 37, cy + 1, cx + 24, cy + 6); g.strokeTriangle(cx + 24, cy - 4, cx + 37, cy + 1, cx + 24, cy + 6);
  if (o.wingFwd) wing(V([[cx + 4, cy - 14], [cx + 44 * o.wingFwd, cy - 34], [cx + 48 * o.wingFwd, cy + 4], [cx + 14, cy + 14]]));
  else { g.fillStyle(0xf3eee4); g.fillEllipse(cx - 6, cy + 6, 16, 26); g.strokeEllipse(cx - 6, cy + 6, 16, 26); }
}
function drawKkiruk(g, o) {
  const s = o.s || 1, ox = o.ox || 0, oy = o.oy || 0;
  const p = (x, y) => [ox + x * s, oy + y * s];
  const tri = (a, b, c, fill) => { g.fillStyle(fill); g.fillTriangle(...p(...a), ...p(...b), ...p(...c)); g.strokeTriangle(...p(...a), ...p(...b), ...p(...c)); };
  g.lineStyle(2.5 * s, 0x2f3640);
  if (!o.fly) { g.fillStyle(0xe58a3a); g.fillRect(...p(34 + (o.step || 0) * 3, 66), 4 * s, 14 * s); g.fillRect(...p(48 - (o.step || 0) * 3, 66), 4 * s, 14 * s); }
  const wy = o.wingUp ? -26 : o.dive ? -6 : 10;
  tri([30, 46], [8, 46 + wy], [50, 54], 0x8f99a4);
  g.fillStyle(0xf2f4f6); g.fillEllipse(...p(42, 54), 46 * s, 30 * s); g.strokeEllipse(...p(42, 54), 46 * s, 30 * s);
  g.fillStyle(0xffffff); g.fillCircle(...p(60, 34), 15 * s); g.strokeCircle(...p(60, 34), 15 * s);
  g.fillStyle(0xd23b3b); g.fillRect(...p(46, 21), 28 * s, 8 * s); tri([46, 22], [34, 16], [36, 30], 0xd23b3b);
  if (o.hurt) { g.lineStyle(2.5 * s, 0x222222); g.lineBetween(...p(62, 31), ...p(68, 37)); g.lineBetween(...p(68, 31), ...p(62, 37)); }
  else { g.fillStyle(0x222222); g.fillCircle(...p(65, 34), 3 * s); g.lineStyle(2.5 * s, 0x222222); g.lineBetween(...p(58, 28), ...p(69, 30)); }
  if (o.boss) { g.fillStyle(0x222222); g.fillEllipse(...p(57, 34), 9 * s, 8 * s); g.lineBetween(...p(48, 26), ...p(70, 40)); g.fillStyle(0xf2c14e); g.fillCircle(...p(46, 62), 6 * s); }
  g.lineStyle(2.5 * s, 0x2f3640);
  tri([72, 32], [88, 37], [72, 41], 0xf2c14e);
  g.fillStyle(0xd23b3b); g.fillCircle(...p(84, 38), 1.6 * s);
}
const PLACEHOLDERS = {
  player_idle_1: [100, 100, g => drawGull(g, {})], player_idle_2: [100, 100, g => drawGull(g, { bob: 2, eye: '-' })],
  player_walk_1: [100, 100, g => drawGull(g, { step: -1, bob: -2 })], player_walk_2: [100, 100, g => drawGull(g, { step: 0 })],
  player_walk_3: [100, 100, g => drawGull(g, { step: 1, bob: -2 })], player_walk_4: [100, 100, g => drawGull(g, { step: 0 })],
  player_jump: [100, 100, g => drawGull(g, { bob: -4, flap: -8 })],
  player_attack_1: [100, 100, g => drawGull(g, { wingFwd: 0.6 })], player_attack_2: [100, 100, g => drawGull(g, { wingFwd: 1 })], player_attack_3: [100, 100, g => drawGull(g, { wingFwd: 0.8, bob: 1 })],
  player_hurt: [100, 100, g => drawGull(g, { eye: 'x', bob: 2 })],
  kkiruk_walk_1: [90, 90, g => drawKkiruk(g, { step: -1 })], kkiruk_walk_2: [90, 90, g => drawKkiruk(g, { step: 1, wingUp: true })], kkiruk_hurt: [90, 90, g => drawKkiruk(g, { hurt: true })],
  diver_fly_1: [90, 90, g => drawKkiruk(g, { fly: true, wingUp: true })], diver_fly_2: [90, 90, g => drawKkiruk(g, { fly: true })],
  diver_dive: [90, 90, g => drawKkiruk(g, { fly: true, dive: true })], diver_hurt: [90, 90, g => drawKkiruk(g, { fly: true, hurt: true })],
  boss_idle_1: [180, 180, g => drawKkiruk(g, { s: 2, boss: true })], boss_idle_2: [180, 180, g => drawKkiruk(g, { s: 2, boss: true, wingUp: true, oy: 2 })],
  boss_dash: [180, 180, g => drawKkiruk(g, { s: 2, boss: true, dive: true })], boss_throw: [180, 180, g => drawKkiruk(g, { s: 2, boss: true, wingUp: true })], boss_hurt: [180, 180, g => drawKkiruk(g, { s: 2, boss: true, hurt: true })],
  item_snack: [36, 42, g => { g.fillStyle(0xf6d7a7); g.lineStyle(2, C.line); g.fillRoundedRect(3, 4, 30, 36, 6); g.strokeRoundedRect(3, 4, 30, 36, 6); g.fillStyle(0xf08a6b); g.fillEllipse(18, 24, 16, 10); g.fillStyle(0xe8c48a); g.fillRect(3, 4, 30, 5); }],
  fx_feather: [30, 12, g => { g.fillStyle(0xffffff); g.lineStyle(2, 0x6b7380); g.fillEllipse(15, 6, 28, 9); g.strokeEllipse(15, 6, 28, 9); g.lineBetween(2, 6, 28, 6); }],
  fx_hit: [48, 48, g => { g.fillStyle(0xffe27a); const pts = []; for (let i = 0; i < 16; i++) { const r = i % 2 ? 9 : 23, a = i / 16 * Math.PI * 2; pts.push({ x: 24 + Math.cos(a) * r, y: 24 + Math.sin(a) * r }); } g.fillPoints(pts); }],
  tile_ground: [64, 70, g => { g.fillStyle(0xb8ad97); g.fillRect(0, 0, 64, 70); g.fillStyle(0xd8cfb9); g.fillRect(0, 0, 64, 10); g.fillStyle(0x7fa35a); g.fillRect(6, 0, 14, 4); g.fillRect(40, 0, 10, 3); g.lineStyle(2, 0x8f8470); g.strokeRect(1, 10, 31, 30); g.strokeRect(32, 10, 31, 30); g.strokeRect(-15, 40, 31, 30); g.strokeRect(16, 40, 31, 30); g.strokeRect(47, 40, 31, 30); }],
  tile_platform: [64, 22, g => { g.fillStyle(0x9b6b43); g.fillRect(0, 0, 64, 22); g.fillStyle(0xb98552); g.fillRect(0, 0, 64, 5); g.lineStyle(2, 0x6e4a2c); g.lineBetween(32, 4, 32, 22); g.strokeRect(1, 1, 62, 20); }],
  bg_far: [1024, 540, g => {
    [0x86c3ec, 0x97cdf0, 0xa9d7f3, 0xbbe1f6, 0xcde9f8, 0xdff1fa].forEach((c, i) => { g.fillStyle(c); g.fillRect(0, i * 60, 1024, 60); });
    g.fillStyle(0xffffff, 0.9); [[160, 80, 120, 34], [210, 70, 90, 40], [620, 120, 140, 36], [680, 108, 90, 40], [900, 60, 100, 30]].forEach(([x, y, w, h]) => g.fillEllipse(x, y, w, h));
    g.fillStyle(0xb7c6d6); g.fillEllipse(320, 380, 520, 160); g.fillRect(250, 250, 30, 90); g.fillRect(330, 230, 40, 110); g.fillRect(420, 270, 26, 70); g.fillTriangle(250, 250, 265, 225, 280, 250); g.fillTriangle(330, 230, 350, 200, 370, 230);
    g.fillStyle(0x4f9fd1); g.fillRect(0, 360, 1024, 180); g.fillStyle(0x76b8e0); for (let y = 380; y < 540; y += 26) for (let x = (y / 26 % 2) * 40; x < 1024; x += 90) g.fillRect(x, y, 34, 3);
  }],
  bg_mid: [1024, 540, g => {
    g.fillStyle(0xa89f8e); g.lineStyle(3, 0x857c6c);
    [[80, 300], [430, 280], [800, 310]].forEach(([x, y]) => { g.fillRect(x, y, 150, 470 - y); g.strokeRect(x, y, 150, 470 - y); g.fillStyle(0x6f8fa8); g.fillEllipse(x + 75, 470, 90, 192); g.fillStyle(0xa89f8e); });
    g.fillStyle(0x7a5a3a); g.fillRect(300, 330, 10, 140); g.fillRect(660, 340, 10, 130); g.fillStyle(0xf2d27a); g.fillCircle(305, 326, 10); g.fillCircle(665, 336, 10);
  }],
  bg_near: [1024, 540, g => {
    g.lineStyle(3, 0x5e4630);
    [[60, 420, 70, 50], [140, 440, 50, 30], [520, 425, 60, 45], [880, 415, 80, 55]].forEach(([x, y, w, h]) => { g.fillStyle(0x8d6a45); g.fillRect(x, y, w, h); g.strokeRect(x, y, w, h); g.lineBetween(x, y, x + w, y + h); });
    g.fillStyle(0x6c7a86); g.fillRoundedRect(330, 418, 40, 52, 8); g.fillRoundedRect(372, 430, 34, 40, 8);
  }],
};

// 아직 없는 동작 그림은 받은 그림으로 대신(임시 도형과 섞여 보이지 않게)
const FALLBACK = {
  player_walk_1: 'player_idle_1', player_walk_2: 'player_idle_2', player_walk_3: 'player_idle_1', player_walk_4: 'player_idle_2',
  player_attack_1: 'player_idle_1', player_attack_2: 'player_jump', player_attack_3: 'player_idle_1',
  kkiruk_hurt: 'kkiruk_walk_1', diver_dive: 'diver_fly_2', diver_hurt: 'diver_fly_1',
  boss_idle_2: 'boss_idle_1', boss_dash: 'boss_idle_1', boss_throw: 'boss_idle_2', boss_hurt: 'boss_idle_1',
};
const spriteFile = (key, available) => available.has(key) ? key : available.has(FALLBACK[key]) ? FALLBACK[key] : null;

// ── 그림 불러오기: 있는 그림은 파일로, 없는 그림은 임시 도형으로 ──
const TEX = {};
async function loadTextures(available) {
  const loader = new THREE.TextureLoader();
  await Promise.all(Object.keys(PLACEHOLDERS).map(async key => {
    let tex = null;
    const file = spriteFile(key, available);
    if (file) tex = await loader.loadAsync(`/play/sprites/${file}.png`).catch(() => null);
    if (!tex) {
      const [w, h, fn] = PLACEHOLDERS[key];
      const cv = document.createElement('canvas'); cv.width = w * 2; cv.height = h * 2;
      const ctx = cv.getContext('2d'); ctx.scale(2, 2); fn(painter(ctx));
      tex = new THREE.CanvasTexture(cv);
    }
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = 4;
    TEX[key] = tex;
  }));
}
const aspectOf = key => TEX[key].image.width / TEX[key].image.height;

// ── 화면(HTML) ──
const $ = id => document.getElementById(id);
const ui = {
  hearts(hp, max) { $('hearts').innerHTML = Array.from({ length: max }, (_, i) => `<i class="${i < hp ? 'on' : ''}"></i>`).join(''); },
  score(n) { $('score').textContent = `점수 ${n}`; },
  boss(ratio) { $('boss').hidden = ratio === null; if (ratio !== null) $('bossbar').style.width = `${Math.max(0, ratio) * 100}%`; },
  banner(title, sub) {
    const b = $('banner'); b.innerHTML = `<b>${title}</b><span>${sub}</span>`; b.className = 'show';
    clearTimeout(ui.bt); ui.bt = setTimeout(() => { b.className = ''; }, 2000);
  },
  pop(text, sx, sy, color = '#fff') {
    const d = document.createElement('div'); d.className = 'pop'; d.textContent = text; d.style.left = `${sx}px`; d.style.top = `${sy}px`; d.style.color = color;
    document.body.appendChild(d); setTimeout(() => d.remove(), 700);
  },
};

// ── 입력: 키보드 + 화면 버튼(손가락이 버튼 사이를 미끄러져도 따라감) ──
const input = { down: {}, pressed: {} };
const KEYMAP = { ArrowLeft: 'left', KeyA: 'left', ArrowRight: 'right', KeyD: 'right', ArrowUp: 'jump', Space: 'jump', KeyW: 'jump', KeyK: 'jump', KeyX: 'jump', KeyZ: 'attack', KeyJ: 'attack' };
const keyState = {}; let touchState = {};
addEventListener('keydown', e => { const k = KEYMAP[e.code]; if (!k) return; e.preventDefault(); if (!keyState[k]) input.pressed[k] = true; keyState[k] = true; });
addEventListener('keyup', e => { const k = KEYMAP[e.code]; if (k) keyState[k] = false; });
function readTouches(e) {
  const now = {};
  for (const t of e.touches) { const el = document.elementFromPoint(t.clientX, t.clientY)?.closest('[data-k]'); if (el) now[el.dataset.k] = true; }
  for (const k of Object.keys(now)) if (!touchState[k]) input.pressed[k] = true;
  touchState = now;
  document.querySelectorAll('#pad [data-k]').forEach(b => b.classList.toggle('on', !!now[b.dataset.k]));
}
for (const ev of ['touchstart', 'touchmove', 'touchend', 'touchcancel']) $('pad').addEventListener(ev, e => { e.preventDefault(); readTouches(e); }, { passive: false });
const held = k => !!keyState[k] || !!touchState[k];
const tapped = k => { const v = !!input.pressed[k]; input.pressed[k] = false; return v; };

// ── 3D 무대 ──
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
$('game').appendChild(renderer.domElement);
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x9fd3f2);
const camera = new THREE.PerspectiveCamera(35, 16 / 9, 0.1, 200);
const CAM = { y: 2.2, look: 1.3, dist: 8, minWidth: 10.4 }; // minWidth: 보스 구역(9.6)이 다 보이는 너비
scene.add(new THREE.HemisphereLight(0xffffff, 0x8a7a66, 1.6));
const sun = new THREE.DirectionalLight(0xfff1d6, 1.6); sun.position.set(-4, 8, 6); scene.add(sun);

function resize() {
  const w = innerWidth, h = innerHeight; renderer.setSize(w, h); camera.aspect = w / h;
  // 화면이 좁아도 가로로 최소 minWidth 만큼은 보이도록 카메라를 뒤로
  const t = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  CAM.dist = Math.max(8, CAM.minWidth / 2 / (t * camera.aspect));
  camera.updateProjectionMatrix();
}
addEventListener('resize', resize);

function repeatTex(key, repeatX, repeatY = 1) {
  const t = TEX[key].clone(); t.needsUpdate = true; t.wrapS = THREE.RepeatWrapping; t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeatX, repeatY); t.colorSpace = THREE.SRGBColorSpace; return t;
}
// 그림 한 장을 판에 붙여 세움. 아래 끝(baseY)을 기준으로, 가로는 반복
function layer(key, z, height, baseY, opts = {}) {
  const tileW = height * aspectOf(key), width = WORLD + 120;
  const mat = new THREE.MeshBasicMaterial({ map: repeatTex(key, width / tileW), transparent: true, alphaTest: 0.02, depthWrite: !opts.see });
  const m = new THREE.Mesh(new THREE.PlaneGeometry(width, height), mat);
  m.position.set(WORLD / 2, baseY + height / 2, z); scene.add(m); return m;
}
function buildStage() {
  // 먼 배경(하늘·섬·바다): 그림 속 바다 선(위에서 2/3)이 수면 높이에 오게
  layer('bg_far', -40, 34, -0.3 - 34 / 3);
  // 바다(바닥 아래로 이어지는 물)
  const sea = new THREE.Mesh(new THREE.PlaneGeometry(WORLD + 200, 80), new THREE.MeshLambertMaterial({ color: 0x4f9fd1 }));
  sea.rotation.x = -Math.PI / 2; sea.position.set(WORLD / 2, -0.35, -40); scene.add(sea);
  // 중간(돌 아치·가로등)과 가까운(상자·통) 배경: 그림 아래 87% 선이 땅·수면에 오게
  layer('bg_mid', -9, 9, -0.35 - 9 * 0.13);
  layer('bg_near', -2.6, 4, -4 * 0.13);
  // 땅(돌 부두): 윗면과 앞면에 바닥 그림
  const tileU = 0.64;
  const top = new THREE.MeshLambertMaterial({ map: repeatTex('tile_ground', (WORLD + 10) / tileU, 4.5 / (tileU * 70 / 64)) });
  const front = new THREE.MeshLambertMaterial({ map: repeatTex('tile_ground', (WORLD + 10) / tileU, 1.2 / 0.7) });
  const ground = new THREE.Mesh(new THREE.BoxGeometry(WORLD + 10, 1.2, 4.5), [front, front, top, front, front, front]);
  ground.position.set(WORLD / 2, -0.6, -0.75); scene.add(ground);
  // 발판(나무)
  for (const [x, h, w] of PLATFORMS) {
    const mat = new THREE.MeshLambertMaterial({ map: repeatTex('tile_platform', w / 0.64) });
    const b = new THREE.Mesh(new THREE.BoxGeometry(w, 0.22, 1.2), mat); b.position.set(x + w / 2, h - 0.11, 0); scene.add(b);
  }
  // 보스 구역 표시: 돌 기둥 두 개
  for (const x of [ARENA - 0.3, WORLD + 0.3]) {
    const c = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.34, 3.2, 12), new THREE.MeshLambertMaterial({ color: 0xbdb39f }));
    c.position.set(x, 1.6, -1.2); scene.add(c);
  }
}

// ── 캐릭터(그림 판) ──
const SHADOW = new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.25, depthWrite: false });
const SHADOW_GEO = new THREE.CircleGeometry(0.5, 24);
const entities = [];
let zSlot = 0;
function makeEntity(kind, x, y, anim, sizeKey) {
  const [h, bw, bh] = SIZE[sizeKey];
  const mat = new THREE.MeshBasicMaterial({ map: TEX[ANIMS[anim] ? ANIMS[anim][0][0] : anim], transparent: true, alphaTest: 0.1 });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), mat);
  const shadow = new THREE.Mesh(SHADOW_GEO, SHADOW); shadow.rotation.x = -Math.PI / 2;
  // 같은 평면에 겹쳐 깜빡이지 않게 조금씩 앞뒤로
  const z = kind === 'player' ? 0.08 : kind === 'fx' ? 0.12 : -0.04 + (zSlot++ % 6) * 0.01;
  scene.add(mesh); scene.add(shadow);
  const e = { kind, x, y, vx: 0, vy: 0, h, bw, bh, z, mesh, shadow, facing: 1, anim, animAt: 0, onGround: false, gravity: true, alive: true, flash: 0, tint: null, opacity: 1, spin: 0 };
  entities.push(e); return e;
}
function removeEntity(e) { e.alive = false; scene.remove(e.mesh); scene.remove(e.shadow); e.mesh.material.dispose(); const i = entities.indexOf(e); if (i >= 0) entities.splice(i, 1); }
function play(e, anim, restart = false) { if (e.anim !== anim || restart) { e.anim = anim; e.animAt = clock; } }
function frameKey(e) {
  const a = ANIMS[e.anim]; if (!a) return e.anim;
  const [frames, rate, loop] = a; let i = Math.floor((clock - e.animAt) * rate);
  i = loop ? i % frames.length : Math.min(i, frames.length - 1); return frames[i];
}
const box = e => ({ x0: e.x - e.bw / 2, x1: e.x + e.bw / 2, y0: e.y, y1: e.y + e.bh });
const overlap = (a, b) => a.x0 < b.x1 && a.x1 > b.x0 && a.y0 < b.y1 && a.y1 > b.y0;

// ── 게임 상태 ──
let clock = 0, state = 'title', player, boss, waves, score, startedAt, camX = 5, shake = 0, arenaLocked = false;
const now = () => clock;

function toScreen(x, y) {
  const v = new THREE.Vector3(x, y, 0).project(camera);
  return [(v.x + 1) / 2 * innerWidth, (1 - v.y) / 2 * innerHeight];
}
function popAt(e, text, color) { const [sx, sy] = toScreen(e.x, e.y + e.h); ui.pop(text, sx, sy, color); }
function hitFx(x, y) {
  const fx = makeEntity('fx', x, y, 'fx_hit', 'item'); fx.gravity = false; fx.life = 0.22; fx.grow = true;
}

function startStage() {
  for (const e of [...entities]) removeEntity(e);
  player = makeEntity('player', 1.4, 0, 'player_idle', 'player');
  Object.assign(player, { hp: 5, maxHp: 5, inv: 0, hurt: 0, atkUntil: 0, atkReady: 0 });
  boss = null; arenaLocked = false; score = 0; startedAt = clock; camX = 5; waves = WAVES.map(w => ({ ...w }));
  ui.hearts(5, 5); ui.score(0); ui.boss(null);
  ui.banner('1-1 정문 부두 출근길', '끼룩단을 물리치고 출근하자!');
  state = 'play';
}

function spawn(kind, x) {
  const e = kind === 'walker' ? makeEntity('walker', x, 0, 'kkiruk_walk', 'walker') : makeEntity('diver', x, 2.8, 'diver_fly', 'diver');
  Object.assign(e, { hp: kind === 'walker' ? 2 : 1, stun: 0, mode: 'hover', modeUntil: 0, baseY: 2.6 + Math.random() * 0.4, facing: -1 });
  if (kind === 'diver') e.gravity = false;
}
function spawnBoss() {
  arenaLocked = true;
  boss = makeEntity('boss', WORLD - 1.8, 5, 'boss_idle', 'boss');
  Object.assign(boss, { hp: 14, maxHp: 14, stun: 0, mode: 'walk', modeUntil: clock + 1.8, step: 0, facing: -1 });
  ui.boss(1); ui.banner('끼룩단 행동대장 등장!', '돌진과 깃털 던지기를 조심해요');
}

function hurtPlayer(fromX) {
  const p = player;
  if (state !== 'play' || clock < p.inv) return;
  p.hp -= 1; p.inv = clock + 1.1; p.hurt = clock + 0.35;
  p.vx = (p.x < fromX ? -1 : 1) * 3; p.vy = 3.2; play(p, 'player_hurt', true);
  shake = 0.25; ui.hearts(p.hp, p.maxHp);
  if (p.hp <= 0) finish(false);
}
function attack() {
  const p = player;
  if (clock < p.atkReady || clock < p.hurt) return;
  p.atkReady = clock + 0.36; p.atkUntil = clock + 0.22; play(p, 'player_attack', true);
  p.atkHitAt = clock + 0.08;
}
function resolveAttack() {
  const p = player; p.atkHitAt = 0;
  const r = { x0: p.facing > 0 ? p.x + 0.05 : p.x - 0.95, x1: p.facing > 0 ? p.x + 0.95 : p.x - 0.05, y0: p.y - 0.1, y1: p.y + 0.95 };
  let hit = false;
  for (const e of [...entities]) {
    if (!e.alive || e.dead) continue;
    if (e.kind === 'feather' && overlap(r, box(e))) { removeEntity(e); hit = true; continue; }
    if (['walker', 'diver', 'boss'].includes(e.kind) && overlap(r, box(e))) { damage(e, p.facing); hit = true; }
  }
  if (hit) shake = Math.max(shake, 0.08);
}
function damage(e, dir) {
  e.hp -= 1; e.flash = clock + 0.12;
  hitFx(e.x - dir * 0.1, e.y + e.bh * 0.5); popAt(e, '퍽!');
  if (e.kind === 'boss') {
    ui.boss(e.hp / e.maxHp);
    if (e.hp <= 0) return kill(e);
    if (e.mode !== 'dash') { e.vx = dir * 1.2; play(e, 'boss_hurt'); e.stun = clock + 0.2; }
    return;
  }
  e.stun = clock + 0.3; e.vx = dir * 2.6; e.vy = e.kind === 'walker' ? 2.2 : 0.6;
  play(e, e.kind === 'walker' ? 'kkiruk_hurt' : 'diver_hurt');
  if (e.hp <= 0) kill(e);
}
function kill(e) {
  e.dead = true; e.gravity = false; e.vx = 0; e.vy = 2.5; e.spin = e.facing > 0 ? 9 : -9; e.life = 0.65;
  score += e.kind === 'boss' ? 1000 : 100; ui.score(score);
  if (e.kind === 'boss') { shake = 0.5; ui.boss(0); setTimeout(() => finish(true), 900); return; }
  if (Math.random() < 0.3) { const it = makeEntity('item', e.x, e.y + 0.3, 'item_snack', 'item'); it.vy = 2; }
}
function throwFeathers(b) {
  for (let i = 0; i < 3; i++) {
    const f = makeEntity('feather', b.x + b.facing * 0.4, b.y + 1.2, 'fx_feather', 'feather');
    f.vx = THREE.MathUtils.clamp((player.x - b.x) * 0.9, -5.2, 5.2) + (i - 1) * 1.1; f.vy = 3.8 + i * 0.6; f.gravityScale = 1;
  }
}

function think(e) {
  const p = player, dx = p.x - e.x;
  if (clock < e.stun) return;
  if (e.kind === 'walker') {
    play(e, 'kkiruk_walk'); e.vx = Math.abs(dx) < 6.5 ? Math.sign(dx) * 0.95 : 0; e.facing = dx < 0 ? -1 : 1;
  } else if (e.kind === 'diver') {
    if (e.mode === 'hover') {
      play(e, 'diver_fly'); e.facing = dx < 0 ? -1 : 1;
      e.vx = Math.abs(dx) < 7 ? Math.sign(dx) * 0.8 : 0; e.vy = (e.baseY + Math.sin(clock * 3.3) * 0.12 - e.y) * 3;
      if (Math.abs(dx) < 1.7 && clock > e.modeUntil) {
        e.mode = 'dive'; e.modeUntil = clock + 0.8; play(e, 'diver_dive');
        const v = new THREE.Vector2(dx, p.y + 0.4 - e.y).normalize().multiplyScalar(4); e.vx = v.x; e.vy = v.y;
      }
    } else if (e.mode === 'dive') {
      if (clock > e.modeUntil || e.y < 0.4) { e.mode = 'rise'; e.vx = 0; e.vy = 2.2; play(e, 'diver_fly'); }
    } else if (e.y >= e.baseY) { e.mode = 'hover'; e.modeUntil = clock + 0.9; }
  } else if (e.kind === 'boss') {
    const rage = e.hp <= e.maxHp / 2;
    if (e.mode === 'walk') {
      play(e, 'boss_idle'); e.facing = dx < 0 ? -1 : 1; e.vx = Math.sign(dx) * (rage ? 1.1 : 0.75);
      if (clock > e.modeUntil) {
        e.step += 1; e.vx = 0;
        if (e.step % 2) { e.mode = 'warn'; e.modeUntil = clock + (rage ? 0.38 : 0.55); e.tint = 0xfff1a0; }
        else { e.mode = 'throw'; e.modeUntil = clock + 0.7; play(e, 'boss_throw'); e.throwAt = clock + 0.25; }
      }
    } else if (e.mode === 'warn') {
      if (clock > e.modeUntil) { e.tint = null; e.mode = 'dash'; e.modeUntil = clock + 0.65; play(e, 'boss_dash'); e.vx = e.facing * (rage ? 6 : 5); }
    } else if (e.mode === 'dash') {
      if (clock > e.modeUntil || e.wall) { if (e.wall) shake = 0.3; e.vx = 0; e.mode = 'walk'; e.modeUntil = clock + (rage ? 1.1 : 1.7); }
    } else if (e.mode === 'throw') {
      if (e.throwAt && clock > e.throwAt) { e.throwAt = 0; throwFeathers(e); }
      if (clock > e.modeUntil) { e.mode = 'walk'; e.modeUntil = clock + (rage ? 1.1 : 1.7); }
    }
  }
}

// 움직임·땅·발판(위에서만 밟히는 한쪽 발판)·벽
function move(e, dt) {
  if (e.gravity) e.vy -= GRAVITY * dt;
  const prevY = e.y;
  e.x += e.vx * dt; e.y += e.vy * dt; e.onGround = false; e.wall = false;
  if (e.dead || e.kind === 'fx') return;
  const lo = arenaLocked && (e.kind === 'player' || e.kind === 'boss') ? ARENA : 0, hi = WORLD;
  if (e.x - e.bw / 2 < lo) { e.x = lo + e.bw / 2; e.vx = Math.max(0, e.vx); e.wall = true; }
  if (e.x + e.bw / 2 > hi) { e.x = hi - e.bw / 2; e.vx = Math.min(0, e.vx); e.wall = true; }
  if (e.kind === 'feather') { if (e.y <= 0) removeEntity(e); return; }
  if (!e.gravity) { if (e.y < 0) e.y = 0; return; }
  if (e.y <= 0) { e.y = 0; e.vy = 0; e.onGround = true; }
  if (e.vy <= 0 && e.kind !== 'diver') for (const [x, h, w] of PLATFORMS) {
    if (e.x > x && e.x < x + w && prevY >= h - 0.001 && e.y <= h) { e.y = h; e.vy = 0; e.onGround = true; }
  }
}

function update(dt) {
  const p = player;
  if (state === 'play') {
    const left = held('left'), right = held('right'), jump = tapped('jump'), atk = tapped('attack');
    if (clock >= p.hurt) {
      const speed = clock < p.atkUntil && p.onGround ? 0.9 : 2.3;
      if (left && !right) { p.vx = -speed; p.facing = -1; } else if (right && !left) { p.vx = speed; p.facing = 1; } else p.vx = 0;
      if (jump && p.onGround) p.vy = 6;
      if (atk) attack();
      if (clock >= p.atkUntil) play(p, !p.onGround ? 'player_jump' : p.vx ? 'player_walk' : 'player_idle');
    }
    if (p.atkHitAt && clock >= p.atkHitAt) resolveAttack();
    while (waves.length && p.x > waves[0].at) for (const [kind, x] of waves.shift().spawn) spawn(kind, x);
    if (!boss && p.x > ARENA + 0.8) spawnBoss();
    for (const e of entities) if (['walker', 'diver', 'boss'].includes(e.kind) && !e.dead) think(e);
  } else { input.pressed = {}; }

  for (const e of [...entities]) {
    if (!e.alive) continue;
    if (state === 'play' || e.dead || e.kind === 'fx') move(e, dt);
    if (e.life !== undefined) { e.life -= dt; if (e.life <= 0) { removeEntity(e); continue; } }
  }
  if (state === 'play') for (const e of [...entities]) {
    if (!e.alive || e.dead) continue;
    if (['walker', 'diver', 'boss'].includes(e.kind) && overlap(box(p), box(e))) hurtPlayer(e.x);
    else if (e.kind === 'feather' && overlap(box(p), box(e))) { removeEntity(e); hurtPlayer(e.x); }
    else if (e.kind === 'item' && overlap(box(p), box(e))) { removeEntity(e); p.hp = Math.min(p.maxHp, p.hp + 1); ui.hearts(p.hp, p.maxHp); popAt(p, '+1 회복!', '#9ff0a0'); }
  }
  if (state === 'play' && p.y < -3) { p.hp = 1; hurtPlayer(p.x); }
}

function render(dt) {
  for (const e of entities) {
    const key = frameKey(e), tex = TEX[key];
    if (e.mesh.material.map !== tex) { e.mesh.material.map = tex; e.mesh.material.needsUpdate = true; }
    let h = e.h, w = h * aspectOf(key);
    if (e.grow) { const k = 1.6 - e.life / 0.22; w *= k; h *= k; e.mesh.material.opacity = Math.max(0, e.life / 0.22); }
    e.mesh.scale.set(w * (e.facing < 0 ? -1 : 1), h, 1);
    e.mesh.position.set(e.x, e.y + h / 2, e.z);
    if (e.kind === 'feather') e.mesh.rotation.z = Math.atan2(e.vy, e.vx);
    if (e.dead) { e.mesh.rotation.z += e.spin * dt; e.mesh.material.opacity = Math.max(0, e.life / 0.65); }
    const c = e.mesh.material.color;
    if (clock < e.flash) c.setRGB(1, 0.6, 0.6); else if (e.tint) c.set(e.tint); else c.setRGB(1, 1, 1);
    e.mesh.visible = !(e === player && clock < e.inv && Math.floor(clock * 12) % 2);
    // 그림자: 발밑 땅(또는 발판)에 붙고, 높이 뜰수록 작고 옅게
    const floor = PLATFORMS.reduce((f, [x, ph, pw]) => (e.x > x && e.x < x + pw && e.y >= ph - 0.01 ? Math.max(f, ph) : f), 0);
    const lift = Math.max(0, e.y - floor), s = Math.max(0.3, 1 - lift * 0.18) * e.bw * 1.4;
    e.shadow.visible = e.kind !== 'fx' && !e.dead;
    e.shadow.scale.set(s, s * 0.45, 1); e.shadow.position.set(e.x, floor + 0.01, e.z);
    e.shadow.material.opacity = 0.25;
  }
  // 카메라: 플레이어를 따라가고, 보스전에서는 보스 구역에 고정
  const half = CAM.dist * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * camera.aspect;
  const target = arenaLocked ? ARENA + VIEW / 2 : THREE.MathUtils.clamp((player?.x ?? 5) + 1.2, half - 0.6, WORLD - half + 0.6);
  camX += (target - camX) * Math.min(1, dt * (arenaLocked ? 2.5 : 6));
  shake = Math.max(0, shake - dt);
  const sx = shake ? (Math.random() - 0.5) * shake * 0.6 : 0, sy = shake ? (Math.random() - 0.5) * shake * 0.6 : 0;
  camera.position.set(camX + sx, CAM.y + sy, CAM.dist);
  camera.lookAt(camX + sx * 0.5, CAM.look, 0);
  renderer.render(scene, camera);
}

function finish(win) {
  if (state !== 'play') return; state = win ? 'clear' : 'over';
  const sec = Math.round(clock - startedAt);
  const bonus = win ? Math.max(0, 300 - sec) * 5 + player.hp * 100 : 0;
  score += bonus; ui.score(score);
  $('screen').innerHTML = `<h1>${win ? '출근 완료!' : '오늘은 지각…'}</h1>
<p>${win ? `걸린 시간 ${sec}초 · 남은 체력 ${player.hp}<br>점수 ${score} (보너스 ${bonus})` : `점수 ${score}<br>갈매미는 무거워서 쉽게 쓰러지지 않아요. 다시!`}</p>
<button id="again">다시 하기</button><a href="/">홈페이지로</a>`;
  setTimeout(() => { $('screen').hidden = false; $('again').onclick = begin; }, 400);
}
function begin() { $('screen').hidden = true; startStage(); }
addEventListener('keydown', e => { if ((e.code === 'Enter' || e.code === 'KeyR') && state !== 'play' && state !== 'title') begin(); });

function titleScreen() {
  const touch = matchMedia('(pointer: coarse)').matches;
  $('pad').hidden = !touch;
  $('screen').innerHTML = `<h1>갈매미맨: 출근길</h1><p class="sub">2.5D 시험판 · 1-1 정문 부두</p>
<button id="go">${touch ? '눌러서 시작' : '시작 (Enter)'}</button>
<p class="help">${touch ? '◀ ▶ 걷기 · 점프 · 공격' : '←→ 걷기 · ↑/Space 점프 · Z/J 공격'}</p>`;
  $('screen').hidden = false;
  const go = async () => {
    removeEventListener('keydown', onKey);
    if (touch && document.documentElement.requestFullscreen && !document.fullscreenElement) {
      try { await document.documentElement.requestFullscreen(); await screen.orientation?.lock?.('landscape'); } catch { /* 지원 안 하는 기기는 그냥 진행 */ }
    }
    begin();
  };
  const onKey = e => { if (e.code === 'Enter' || e.code === 'Space') go(); };
  $('go').onclick = go; addEventListener('keydown', onKey);
  // 시작 화면 뒤에서 무대를 천천히 보여 줌
  player = makeEntity('player', 4, 0, 'player_idle', 'player');
  const k = makeEntity('walker', 6.2, 0, 'kkiruk_walk', 'walker'); k.facing = -1;
}

let last = performance.now();
function loop(t) {
  const dt = Math.min(1 / 30, (t - last) / 1000); last = t; clock += dt;
  update(dt); render(dt);
  requestAnimationFrame(loop);
}

async function main() {
  const font = document.fonts ? Promise.race([document.fonts.load('32px Jua'), new Promise(r => setTimeout(r, 1500))]) : Promise.resolve();
  const list = await fetch('/play/sprites.json').then(r => (r.ok ? r.json() : [])).catch(() => []);
  await Promise.all([font, loadTextures(new Set(list))]);
  resize(); buildStage(); titleScreen();
  if (/[?&]test\b/.test(location.search)) window.__gmm = { get player() { return player; }, get boss() { return boss; }, get state() { return state; }, get score() { return score; }, entities, set waves(v) { waves = v; }, ui };
  requestAnimationFrame(loop);
}
main();
