// 갈매미맨: 출근길 — 횡스크롤 전투 시험판 (1-1 정문 부두)
// 그림: public/play/sprites/<이름>.png 이 있으면 그 그림을, 없으면 임시 도형을 씁니다.
// 모든 캐릭터 그림은 "오른쪽을 보는" 모습 기준입니다(왼쪽은 좌우 반전).
(() => {
  'use strict';
  const W = 960, H = 540, GROUND = 470, ARENA = 3000, WORLD = ARENA + W;
  const FONT = 'Jua, sans-serif';

  // 애니메이션 = 그림 이름 목록. 그림 파일 이름이 곧 이 이름입니다(docs/PLAY-SPRITE-PROMPTS.md 참고).
  const ANIMS = {
    player_idle: { frames: ['player_idle_1', 'player_idle_2'], rate: 3, repeat: -1 },
    player_walk: { frames: ['player_walk_1', 'player_walk_2', 'player_walk_3', 'player_walk_4'], rate: 10, repeat: -1 },
    player_jump: { frames: ['player_jump'], rate: 1, repeat: 0 },
    player_attack: { frames: ['player_attack_1', 'player_attack_2', 'player_attack_3'], rate: 16, repeat: 0 },
    player_hurt: { frames: ['player_hurt'], rate: 1, repeat: 0 },
    kkiruk_walk: { frames: ['kkiruk_walk_1', 'kkiruk_walk_2'], rate: 6, repeat: -1 },
    kkiruk_hurt: { frames: ['kkiruk_hurt'], rate: 1, repeat: 0 },
    diver_fly: { frames: ['diver_fly_1', 'diver_fly_2'], rate: 8, repeat: -1 },
    diver_dive: { frames: ['diver_dive'], rate: 1, repeat: 0 },
    diver_hurt: { frames: ['diver_hurt'], rate: 1, repeat: 0 },
    boss_idle: { frames: ['boss_idle_1', 'boss_idle_2'], rate: 4, repeat: -1 },
    boss_dash: { frames: ['boss_dash'], rate: 1, repeat: 0 },
    boss_throw: { frames: ['boss_throw'], rate: 1, repeat: 0 },
    boss_hurt: { frames: ['boss_hurt'], rate: 1, repeat: 0 },
  };
  const SINGLES = ['item_snack', 'fx_feather', 'fx_hit', 'bg_far', 'bg_mid', 'bg_near', 'tile_ground', 'tile_platform', 'hud_heart', 'hud_heart_empty'];
  // 화면에 보이는 키(px). 실제 그림 크기와 상관없이 이 키로 맞춥니다.
  const SIZE = { player: 100, kkiruk: 84, diver: 76, boss: 190, item_snack: 40, fx_feather: 30 };

  const PLATFORMS = [[700, 360, 3], [1250, 330, 4], [1900, 350, 3], [2450, 320, 4]];
  const WAVES = [
    { at: 250, spawn: [['walker', 900]] },
    { at: 600, spawn: [['walker', 1200], ['walker', 1300]] },
    { at: 950, spawn: [['diver', 1450]] },
    { at: 1400, spawn: [['walker', 1900], ['diver', 1950]] },
    { at: 1850, spawn: [['walker', 2300], ['walker', 2400], ['diver', 2350]] },
    { at: 2350, spawn: [['diver', 2750], ['walker', 2800], ['diver', 2850]] },
  ];

  let available = new Set();
  // 아직 없는 동작 그림은 받은 그림으로 대신(임시 도형과 섞여 보이지 않게)
  const FALLBACK = {
    player_walk_1: 'player_idle_1', player_walk_2: 'player_idle_2', player_walk_3: 'player_idle_1', player_walk_4: 'player_idle_2',
    player_attack_1: 'player_idle_1', player_attack_2: 'player_jump', player_attack_3: 'player_idle_1',
    kkiruk_hurt: 'kkiruk_walk_1', diver_dive: 'diver_fly_2', diver_hurt: 'diver_fly_1',
    boss_idle_2: 'boss_idle_1', boss_dash: 'boss_idle_1', boss_throw: 'boss_idle_2', boss_hurt: 'boss_idle_1',
  };
  const spriteFile = (key, available) => available.has(key) ? key : available.has(FALLBACK[key]) ? FALLBACK[key] : null;

  // ── 임시 도형 그림 ──────────────────────────────────────────
  const C = { line: 0x4b3f33, white: 0xfdfbf6, fluff: 0xece6da, crest: 0xd8c08a, stone: 0xcdbf9f, stoneDark: 0x9a8d72, beak: 0xe2b04a, blush: 0xf4a6a6, leg: 0xd9a066 };

  function drawGull(g, o) {
    const cx = 50, cy = 60 + (o.bob || 0), st = o.step || 0;
    g.lineStyle(3, C.line);
    // 다리
    g.fillStyle(C.leg);
    g.fillRoundedRect(cx - 14 + st * 5, cy + 22, 9, 14, 3);
    g.fillRoundedRect(cx + 6 - st * 5, cy + 22, 9, 14, 3);
    const wing = (pts) => { g.fillStyle(C.stone); g.fillPoints(pts, true); g.strokePoints(pts, true, true); g.lineStyle(2, C.stoneDark); g.lineBetween(pts[0].x, pts[0].y, pts[2].x, pts[2].y); g.lineStyle(3, C.line); };
    const P = (arr) => arr.map(([x, y]) => new Phaser.Math.Vector2(x, y));
    if (!o.wingFwd) wing(P([[cx - 6, cy - 20], [cx - 42, cy - 40 + (o.flap || 0)], [cx - 46, cy + 2], [cx - 20, cy + 16]]));
    // 몸
    g.fillStyle(C.white); g.fillEllipse(cx, cy, 64, 60); g.strokeEllipse(cx, cy, 64, 60);
    g.fillStyle(C.fluff); g.fillEllipse(cx - 2, cy + 14, 36, 20);
    // 볏(돌)
    g.fillStyle(C.crest);
    [[cx - 16, cy - 24, cx - 9, cy - 46, cx - 2, cy - 26], [cx - 5, cy - 28, cx + 4, cy - 54, cx + 13, cy - 28], [cx + 8, cy - 26, cx + 19, cy - 44, cx + 22, cy - 20]]
      .forEach(t => { g.fillTriangle(...t); g.strokeTriangle(...t); });
    // 눈·볼·부리
    if (o.eye === 'x') { g.lineStyle(3, 0x222222); g.lineBetween(cx + 10, cy - 10, cx + 18, cy - 2); g.lineBetween(cx + 18, cy - 10, cx + 10, cy - 2); g.lineStyle(3, C.line); }
    else if (o.eye === '-') { g.lineStyle(3, 0x222222); g.lineBetween(cx + 9, cy - 6, cx + 19, cy - 6); g.lineStyle(3, C.line); }
    else { g.fillStyle(0x222222); g.fillCircle(cx + 14, cy - 6, 4.5); g.fillStyle(0xffffff); g.fillCircle(cx + 15.5, cy - 7.5, 1.5); }
    g.fillStyle(C.blush, 0.85); g.fillEllipse(cx + 19, cy + 5, 12, 7);
    g.fillStyle(C.beak); g.fillTriangle(cx + 24, cy - 4, cx + 37, cy + 1, cx + 24, cy + 6); g.strokeTriangle(cx + 24, cy - 4, cx + 37, cy + 1, cx + 24, cy + 6);
    if (o.wingFwd) wing(P([[cx + 4, cy - 14], [cx + 44 * o.wingFwd, cy - 34], [cx + 48 * o.wingFwd, cy + 4], [cx + 14, cy + 14]]));
    else { g.fillStyle(0xf3eee4); g.fillEllipse(cx - 6, cy + 6, 16, 26); g.strokeEllipse(cx - 6, cy + 6, 16, 26); }
  }

  // 끼룩단(진짜로 나는 갈매기). s = 크기 배율, boss = 행동대장 장식
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
    // 빨간 두건
    g.fillStyle(0xd23b3b); g.fillRect(...p(46, 21), 28 * s, 8 * s); tri([46, 22], [34, 16], [36, 30], 0xd23b3b);
    // 눈·눈썹
    if (o.hurt) { g.lineStyle(2.5 * s, 0x222222); g.lineBetween(...p(62, 31), ...p(68, 37)); g.lineBetween(...p(68, 31), ...p(62, 37)); }
    else { g.fillStyle(0x222222); g.fillCircle(...p(65, 34), 3 * s); g.lineStyle(2.5 * s, 0x222222); g.lineBetween(...p(58, 28), ...p(69, 30)); }
    if (o.boss) { g.fillStyle(0x222222); g.fillEllipse(...p(57, 34), 9 * s, 8 * s); g.lineBetween(...p(48, 26), ...p(70, 40)); g.fillStyle(0xf2c14e); g.fillCircle(...p(46, 62), 6 * s); }
    g.lineStyle(2.5 * s, 0x2f3640);
    tri([72, 32], [88, 37], [72, 41], 0xf2c14e);
    g.fillStyle(0xd23b3b); g.fillCircle(...p(84, 38), 1.6 * s);
  }

  function drawPlaceholders(scene) {
    const make = (key, w, h, fn) => {
      if (scene.textures.exists(key)) return;
      const g = scene.add.graphics(); fn(g); g.generateTexture(key, w, h); g.destroy();
    };
    make('player_idle_1', 100, 100, g => drawGull(g, {}));
    make('player_idle_2', 100, 100, g => drawGull(g, { bob: 2, eye: '-' }));
    [-1, 0, 1, 0].forEach((st, i) => make(`player_walk_${i + 1}`, 100, 100, g => drawGull(g, { step: st, bob: i % 2 ? 0 : -2 })));
    make('player_jump', 100, 100, g => drawGull(g, { bob: -4, flap: -8 }));
    make('player_attack_1', 100, 100, g => drawGull(g, { wingFwd: 0.6 }));
    make('player_attack_2', 100, 100, g => drawGull(g, { wingFwd: 1 }));
    make('player_attack_3', 100, 100, g => drawGull(g, { wingFwd: 0.8, bob: 1 }));
    make('player_hurt', 100, 100, g => drawGull(g, { eye: 'x', bob: 2 }));
    make('kkiruk_walk_1', 90, 90, g => drawKkiruk(g, { step: -1 }));
    make('kkiruk_walk_2', 90, 90, g => drawKkiruk(g, { step: 1, wingUp: true }));
    make('kkiruk_hurt', 90, 90, g => drawKkiruk(g, { hurt: true }));
    make('diver_fly_1', 90, 90, g => drawKkiruk(g, { fly: true, wingUp: true }));
    make('diver_fly_2', 90, 90, g => drawKkiruk(g, { fly: true }));
    make('diver_dive', 90, 90, g => drawKkiruk(g, { fly: true, dive: true }));
    make('diver_hurt', 90, 90, g => drawKkiruk(g, { fly: true, hurt: true }));
    make('boss_idle_1', 180, 180, g => drawKkiruk(g, { s: 2, boss: true }));
    make('boss_idle_2', 180, 180, g => drawKkiruk(g, { s: 2, boss: true, wingUp: true, oy: 2 }));
    make('boss_dash', 180, 180, g => drawKkiruk(g, { s: 2, boss: true, dive: true }));
    make('boss_throw', 180, 180, g => drawKkiruk(g, { s: 2, boss: true, wingUp: true }));
    make('boss_hurt', 180, 180, g => drawKkiruk(g, { s: 2, boss: true, hurt: true }));
    make('item_snack', 36, 42, g => { g.fillStyle(0xf6d7a7); g.lineStyle(2, C.line); g.fillRoundedRect(3, 4, 30, 36, 6); g.strokeRoundedRect(3, 4, 30, 36, 6); g.fillStyle(0xf08a6b); g.fillEllipse(18, 24, 16, 10); g.fillStyle(0xe8c48a); g.fillRect(3, 4, 30, 5); });
    make('fx_feather', 30, 12, g => { g.fillStyle(0xffffff); g.lineStyle(2, 0x6b7380); g.fillEllipse(15, 6, 28, 9); g.strokeEllipse(15, 6, 28, 9); g.lineBetween(2, 6, 28, 6); });
    make('fx_hit', 48, 48, g => { g.fillStyle(0xffe27a); const pts = []; for (let i = 0; i < 16; i++) { const r = i % 2 ? 9 : 23, a = i / 16 * Math.PI * 2; pts.push(new Phaser.Math.Vector2(24 + Math.cos(a) * r, 24 + Math.sin(a) * r)); } g.fillPoints(pts, true); });
    make('hud_heart', 26, 24, g => { g.fillStyle(0xe2554f); g.fillCircle(7, 8, 7); g.fillCircle(19, 8, 7); g.fillTriangle(0, 10, 26, 10, 13, 24); });
    make('hud_heart_empty', 26, 24, g => { g.fillStyle(0x5b5b66); g.fillCircle(7, 8, 7); g.fillCircle(19, 8, 7); g.fillTriangle(0, 10, 26, 10, 13, 24); });
    make('tile_ground', 64, 70, g => { g.fillStyle(0xb8ad97); g.fillRect(0, 0, 64, 70); g.fillStyle(0xd8cfb9); g.fillRect(0, 0, 64, 10); g.fillStyle(0x7fa35a); g.fillRect(6, 0, 14, 4); g.fillRect(40, 0, 10, 3); g.lineStyle(2, 0x8f8470); g.strokeRect(1, 10, 31, 30); g.strokeRect(32, 10, 31, 30); g.strokeRect(-15, 40, 31, 30); g.strokeRect(16, 40, 31, 30); g.strokeRect(47, 40, 31, 30); });
    make('tile_platform', 64, 22, g => { g.fillStyle(0x9b6b43); g.fillRect(0, 0, 64, 22); g.fillStyle(0xb98552); g.fillRect(0, 0, 64, 5); g.lineStyle(2, 0x6e4a2c); g.lineBetween(32, 4, 32, 22); g.strokeRect(1, 1, 62, 20); });
    make('bg_far', 1024, 540, g => {
      [0x86c3ec, 0x97cdf0, 0xa9d7f3, 0xbbe1f6, 0xcde9f8, 0xdff1fa].forEach((c, i) => { g.fillStyle(c); g.fillRect(0, i * 60, 1024, 60); });
      g.fillStyle(0xffffff, 0.9); [[160, 80, 120, 34], [210, 70, 90, 40], [620, 120, 140, 36], [680, 108, 90, 40], [900, 60, 100, 30]].forEach(([x, y, w, h]) => g.fillEllipse(x, y, w, h));
      g.fillStyle(0xb7c6d6); g.fillEllipse(320, 380, 520, 160); g.fillRect(250, 250, 30, 90); g.fillRect(330, 230, 40, 110); g.fillRect(420, 270, 26, 70); g.fillTriangle(250, 250, 265, 225, 280, 250); g.fillTriangle(330, 230, 350, 200, 370, 230);
      g.fillStyle(0x4f9fd1); g.fillRect(0, 360, 1024, 180); g.fillStyle(0x76b8e0); for (let y = 380; y < 540; y += 26) for (let x = (y / 26 % 2) * 40; x < 1024; x += 90) g.fillRect(x, y, 34, 3);
    });
    make('bg_mid', 1024, 540, g => {
      g.fillStyle(0xa89f8e); g.lineStyle(3, 0x857c6c);
      [[80, 300], [430, 280], [800, 310]].forEach(([x, y]) => { g.fillRect(x, y, 150, 470 - y); g.strokeRect(x, y, 150, 470 - y); g.fillStyle(0x6f8fa8); g.fillEllipse(x + 75, 470, 90, 120 * 2 * 0.8); g.fillStyle(0xa89f8e); });
      g.fillStyle(0x7a5a3a); g.fillRect(300, 330, 10, 140); g.fillRect(660, 340, 10, 130); g.fillStyle(0xf2d27a); g.fillCircle(305, 326, 10); g.fillCircle(665, 336, 10);
    });
    make('bg_near', 1024, 540, g => {
      g.lineStyle(3, 0x5e4630);
      [[60, 420, 70, 50], [140, 440, 50, 30], [520, 425, 60, 45], [880, 415, 80, 55]].forEach(([x, y, w, h]) => { g.fillStyle(0x8d6a45); g.fillRect(x, y, w, h); g.strokeRect(x, y, w, h); g.lineBetween(x, y, x + w, y + h); });
      g.fillStyle(0x6c7a86); g.fillRoundedRect(330, 418, 40, 52, 8); g.fillRoundedRect(372, 430, 34, 40, 8);
    });
  }

  function defineAnims(scene) {
    for (const [key, a] of Object.entries(ANIMS)) {
      if (scene.anims.exists(key)) continue;
      scene.anims.create({ key, frames: a.frames.map(k => ({ key: k })), frameRate: a.rate, repeat: a.repeat });
    }
  }

  // 그림 원본 크기와 상관없이 화면 키를 맞추고, 몸통 충돌 상자를 그림 안쪽으로
  function fit(sprite, h, body = [0.5, 0.78]) {
    const f = sprite.frame; sprite.setScale(h / f.height);
    if (sprite.body) { sprite.body.setSize(f.width * body[0], f.height * body[1]); sprite.body.setOffset(f.width * (1 - body[0]) / 2, f.height * (1 - body[1])); }
    return sprite;
  }

  // ── 화면 터치 버튼 ──────────────────────────────────────────
  class Pad {
    constructor(scene) {
      this.scene = scene; this.touch = scene.sys.game.device.input.touch;
      this.btns = [
        { id: 'left', x: 92, y: H - 82, r: 50, label: '◀' },
        { id: 'right', x: 222, y: H - 82, r: 50, label: '▶' },
        { id: 'jump', x: W - 240, y: H - 76, r: 52, label: '점프' },
        { id: 'attack', x: W - 100, y: H - 118, r: 64, label: '공격' },
      ];
      this.state = {};
      if (!this.touch) return;
      scene.input.addPointer(3);
      for (const b of this.btns) {
        b.g = scene.add.circle(b.x, b.y, b.r, 0xffffff, 0.22).setStrokeStyle(3, 0xffffff, 0.6).setScrollFactor(0).setDepth(200);
        scene.add.text(b.x, b.y, b.label, { fontFamily: FONT, fontSize: b.label.length > 1 ? '24px' : '30px', color: '#ffffff' }).setOrigin(0.5).setScrollFactor(0).setDepth(201).setAlpha(0.9);
      }
    }
    update() {
      const now = {};
      if (this.touch) for (const p of this.scene.input.manager.pointers) {
        if (!p.isDown) continue;
        for (const b of this.btns) if (Phaser.Math.Distance.Between(p.x, p.y, b.x, b.y) < b.r * 1.25) now[b.id] = true;
      }
      for (const b of this.btns) { b.pressed = !this.state[b.id] && !!now[b.id]; if (b.g) b.g.setFillStyle(0xffffff, now[b.id] ? 0.45 : 0.22); }
      this.state = now;
    }
    down(id) { return !!this.state[id]; }
    pressed(id) { return !!this.btns.find(b => b.id === id).pressed; }
  }

  // ── 시작 화면 ──────────────────────────────────────────────
  class Title extends Phaser.Scene {
    constructor() { super('title'); }
    preload() {
      const keys = new Set([...available, ...Object.keys(FALLBACK)]);
      for (const k of keys) { const f = spriteFile(k, available); if (f) this.load.image(k, `/play/sprites/${f}.png`); }
    }
    create() {
      drawPlaceholders(this); defineAnims(this);
      this.add.image(0, 0, 'bg_far').setOrigin(0).setDisplaySize(W, H);
      this.add.rectangle(0, 0, W, H, 0x10202f, 0.35).setOrigin(0);
      fit(this.add.sprite(W / 2 - 120, 330, 'player_idle_1').play('player_idle'), 170);
      fit(this.add.sprite(W / 2 + 140, 330, 'kkiruk_walk_1').play('kkiruk_walk'), 120).setFlipX(true);
      this.add.text(W / 2, 110, '갈매미맨: 출근길', { fontFamily: FONT, fontSize: '64px', color: '#ffffff', stroke: '#1b2a3a', strokeThickness: 8 }).setOrigin(0.5);
      this.add.text(W / 2, 170, '시험판 · 1-1 정문 부두', { fontFamily: FONT, fontSize: '26px', color: '#ffe9a8', stroke: '#1b2a3a', strokeThickness: 5 }).setOrigin(0.5);
      const touch = this.sys.game.device.input.touch;
      this.add.text(W / 2, 450, touch ? '화면을 눌러 시작' : '아무 키나 눌러 시작', { fontFamily: FONT, fontSize: '32px', color: '#ffffff', stroke: '#1b2a3a', strokeThickness: 6 }).setOrigin(0.5);
      this.add.text(W / 2, 500, touch ? '◀ ▶ 걷기 · 점프 · 공격' : '←→ 걷기 · ↑/Space 점프 · Z/J 공격', { fontFamily: FONT, fontSize: '20px', color: '#dfe8f0' }).setOrigin(0.5);
      const go = () => {
        if (touch && !this.scale.isFullscreen) { try { this.scale.startFullscreen(); } catch (e) { /* 지원 안 하는 기기는 그냥 진행 */ } }
        this.scene.start('stage');
      };
      this.input.once('pointerdown', go);
      this.input.keyboard.once('keydown', go);
    }
  }

  // ── 스테이지 ───────────────────────────────────────────────
  class Stage extends Phaser.Scene {
    constructor() { super('stage'); }
    create() {
      drawPlaceholders(this); defineAnims(this);
      this.physics.world.setBounds(0, 0, WORLD, H + 200);
      this.cameras.main.setBounds(0, 0, WORLD, H);
      this.started = this.time.now; this.score = 0; this.over = false;
      this.waves = WAVES.map(w => ({ ...w }));

      // 배경(겹겹이 다른 속도로)
      this.layers = [['bg_far', 0.15], ['bg_mid', 0.4], ['bg_near', 0.7]].map(([k, f], i) => {
        const t = this.textures.get(k).getSourceImage();
        const s = H / t.height;
        const ts = this.add.tileSprite(0, 0, W / s + 2, t.height, k).setOrigin(0).setScale(s).setScrollFactor(0).setDepth(-30 + i);
        return { ts, f, s };
      });

      // 땅·발판
      this.solids = this.physics.add.staticGroup();
      this.add.tileSprite(0, GROUND, WORLD, 70, 'tile_ground').setOrigin(0).setDepth(0);
      const ground = this.add.zone(WORLD / 2, GROUND + 35, WORLD, 70); this.physics.add.existing(ground, true); this.solids.add(ground);
      this.platforms = this.physics.add.staticGroup();
      for (const [x, y, n] of PLATFORMS) {
        this.add.tileSprite(x, y, 64 * n, 22, 'tile_platform').setOrigin(0).setDepth(1);
        const z = this.add.zone(x + 32 * n, y + 11, 64 * n, 22); this.physics.add.existing(z, true);
        z.body.checkCollision.down = z.body.checkCollision.left = z.body.checkCollision.right = false;
        this.platforms.add(z);
      }

      // 플레이어
      this.player = fit(this.physics.add.sprite(140, GROUND - 80, 'player_idle_1').setDepth(12), SIZE.player);
      this.player.setCollideWorldBounds(true);
      Object.assign(this.player, { hp: 5, maxHp: 5, facing: 1, inv: 0, hurt: 0, atkUntil: 0, atkReady: 0 });
      this.physics.add.collider(this.player, this.solids);
      this.physics.add.collider(this.player, this.platforms);
      this.cameras.main.startFollow(this.player, true, 0.12, 0.12, -120, 0);

      this.enemies = this.physics.add.group();
      this.physics.add.collider(this.enemies, this.solids);
      this.physics.add.collider(this.enemies, this.platforms, null, (e) => e.kind !== 'diver');
      this.physics.add.overlap(this.player, this.enemies, (p, e) => { if (!e.dead) this.hurtPlayer(e.x); });
      this.items = this.physics.add.group();
      this.physics.add.collider(this.items, this.solids);
      this.physics.add.collider(this.items, this.platforms);
      this.physics.add.overlap(this.player, this.items, (p, it) => this.pickUp(it));
      this.feathers = this.physics.add.group();
      this.physics.add.overlap(this.player, this.feathers, (p, f) => { f.destroy(); this.hurtPlayer(f.x); });
      this.physics.add.collider(this.feathers, this.solids, (f) => f.destroy());

      // 입력
      this.keys = this.input.keyboard.addKeys('LEFT,RIGHT,UP,SPACE,Z,J,A,D,W,K,X,R,ENTER');
      this.pad = new Pad(this);

      // 화면 위 정보
      this.hearts = [];
      for (let i = 0; i < this.player.maxHp; i++) this.hearts.push(this.add.image(24 + i * 32, 26, 'hud_heart').setOrigin(0, 0.5).setScrollFactor(0).setDepth(200).setDisplaySize(26, 24));
      this.scoreText = this.add.text(W - 20, 14, '점수 0', { fontFamily: FONT, fontSize: '26px', color: '#ffffff', stroke: '#1b2a3a', strokeThickness: 5 }).setOrigin(1, 0).setScrollFactor(0).setDepth(200);
      this.banner('1-1 정문 부두 출근길', '끼룩단을 물리치고 출근하자!');
    }

    banner(title, sub) {
      const t = this.add.text(W / 2, 150, title, { fontFamily: FONT, fontSize: '46px', color: '#ffffff', stroke: '#1b2a3a', strokeThickness: 7 }).setOrigin(0.5).setScrollFactor(0).setDepth(210);
      const s = this.add.text(W / 2, 200, sub, { fontFamily: FONT, fontSize: '24px', color: '#ffe9a8', stroke: '#1b2a3a', strokeThickness: 5 }).setOrigin(0.5).setScrollFactor(0).setDepth(210);
      this.tweens.add({ targets: [t, s], alpha: 0, delay: 1600, duration: 600, onComplete: () => { t.destroy(); s.destroy(); } });
    }

    spawn(kind, x) {
      const key = kind === 'walker' ? 'kkiruk_walk_1' : 'diver_fly_1';
      const e = fit(this.enemies.create(x, kind === 'walker' ? GROUND - 60 : 190, key).setDepth(10), kind === 'walker' ? SIZE.kkiruk : SIZE.diver, [0.6, 0.7]);
      Object.assign(e, { kind, hp: kind === 'walker' ? 2 : 1, stun: 0, mode: 'hover', modeUntil: 0, baseY: 170 + Math.random() * 40 });
      if (kind === 'diver') e.body.setAllowGravity(false);
      e.play(kind === 'walker' ? 'kkiruk_walk' : 'diver_fly');
      e.setFlipX(true);
      return e;
    }

    spawnBoss() {
      this.bossStarted = true;
      this.cameras.main.stopFollow();
      this.tweens.add({ targets: this.cameras.main, scrollX: ARENA, duration: 700, ease: 'Sine.easeInOut' });
      const wall = this.add.zone(ARENA - 10, H / 2, 20, H); this.physics.add.existing(wall, true); this.solids.add(wall);
      const b = fit(this.enemies.create(ARENA + W - 180, 60, 'boss_idle_1').setDepth(10), SIZE.boss, [0.55, 0.72]);
      Object.assign(b, { kind: 'boss', hp: 14, maxHp: 14, stun: 0, mode: 'walk', modeUntil: this.time.now + 1800, step: 0 });
      b.setCollideWorldBounds(true); b.play('boss_idle'); b.setFlipX(true);
      this.boss = b;
      this.bossBar = this.add.rectangle(W / 2 - 200, 58, 400, 16, 0xd23b3b).setOrigin(0, 0.5).setScrollFactor(0).setDepth(201);
      this.add.rectangle(W / 2, 58, 404, 20).setStrokeStyle(3, 0xffffff).setScrollFactor(0).setDepth(202);
      this.add.text(W / 2, 30, '끼룩단 행동대장', { fontFamily: FONT, fontSize: '22px', color: '#ffffff', stroke: '#1b2a3a', strokeThickness: 5 }).setOrigin(0.5).setScrollFactor(0).setDepth(202);
      this.banner('끼룩단 행동대장 등장!', '돌진과 깃털 던지기를 조심해요');
    }

    pop(x, y, text, color = '#ffffff') {
      const t = this.add.text(x, y, text, { fontFamily: FONT, fontSize: '26px', color, stroke: '#1b2a3a', strokeThickness: 5 }).setOrigin(0.5).setDepth(30);
      this.tweens.add({ targets: t, y: y - 40, alpha: 0, duration: 650, onComplete: () => t.destroy() });
    }

    hurtPlayer(fromX) {
      const p = this.player, now = this.time.now;
      if (this.over || now < p.inv) return;
      p.hp -= 1; p.inv = now + 1100; p.hurt = now + 350;
      p.setVelocity((p.x < fromX ? -1 : 1) * 300, -320);
      p.play('player_hurt', true);
      this.cameras.main.shake(140, 0.008);
      this.tweens.add({ targets: p, alpha: 0.3, yoyo: true, repeat: 4, duration: 100, onComplete: () => p.setAlpha(1) });
      this.updateHearts();
      if (p.hp <= 0) this.finish(false);
    }

    updateHearts() { this.hearts.forEach((h, i) => h.setTexture(i < this.player.hp ? 'hud_heart' : 'hud_heart_empty').setDisplaySize(26, 24)); }

    pickUp(it) {
      if (it.taken) return; it.taken = true; it.destroy();
      const p = this.player; p.hp = Math.min(p.maxHp, p.hp + 1); this.updateHearts();
      this.pop(p.x, p.y - 60, '+1 회복!', '#9ff0a0');
    }

    attack() {
      const p = this.player, now = this.time.now;
      if (now < p.atkReady || now < p.hurt) return;
      p.atkReady = now + 360; p.atkUntil = now + 220;
      p.play('player_attack', true);
      this.time.delayedCall(80, () => {
        if (this.over) return;
        const r = new Phaser.Geom.Rectangle(p.x + (p.facing > 0 ? 10 : -95), p.y - 55, 85, 90);
        let hit = false;
        for (const e of this.enemies.getChildren()) {
          if (e.dead || !Phaser.Geom.Intersects.RectangleToRectangle(r, e.getBounds())) continue;
          this.damage(e, p.facing); hit = true;
        }
        for (const f of this.feathers.getChildren()) if (Phaser.Geom.Intersects.RectangleToRectangle(r, f.getBounds())) { f.destroy(); hit = true; }
        if (hit) { this.cameras.main.shake(60, 0.004); }
      });
    }

    damage(e, dir) {
      const now = this.time.now;
      e.hp -= 1;
      const fx = this.add.image(e.x - dir * 10, e.y - 10, 'fx_hit').setDepth(25).setScale(0.6);
      this.tweens.add({ targets: fx, scale: 1.3, alpha: 0, duration: 220, onComplete: () => fx.destroy() });
      this.pop(e.x, e.y - 50, '퍽!');
      e.setTint(0xffb0b0); this.time.delayedCall(120, () => e.active && e.clearTint());
      if (e.kind === 'boss') {
        this.bossBar.width = 400 * Math.max(0, e.hp) / e.maxHp;
        if (e.hp <= 0) return this.kill(e);
        if (e.mode !== 'dash') { e.setVelocityX(dir * 120); e.play('boss_hurt', true); e.stun = now + 200; }
        return;
      }
      e.stun = now + 300;
      e.setVelocity(dir * 260, e.kind === 'walker' ? -220 : -60);
      e.play(e.kind === 'walker' ? 'kkiruk_hurt' : 'diver_hurt', true);
      if (e.hp <= 0) this.kill(e);
    }

    kill(e) {
      e.dead = true; e.body.enable = false;
      this.score += e.kind === 'boss' ? 1000 : 100;
      this.scoreText.setText(`점수 ${this.score}`);
      this.tweens.add({ targets: e, y: e.y - 80, angle: e.flipX ? -200 : 200, alpha: 0, duration: 650, onComplete: () => e.destroy() });
      if (e.kind === 'boss') { this.cameras.main.shake(300, 0.012); this.time.delayedCall(900, () => this.finish(true)); return; }
      if (Math.random() < 0.3) {
        const it = fit(this.items.create(e.x, e.y - 20, 'item_snack').setDepth(5), SIZE.item_snack, [0.9, 0.9]);
        it.setVelocity(0, -200);
      }
    }

    throwFeathers(b) {
      const p = this.player;
      for (let i = 0; i < 3; i++) {
        const f = fit(this.feathers.create(b.x + (b.flipX ? -40 : 40), b.y - 50, 'fx_feather').setDepth(15), 14, [0.9, 0.9]);
        const dx = p.x - b.x;
        f.setVelocity(Phaser.Math.Clamp(dx * 0.9, -520, 520) + (i - 1) * 110, -380 - i * 60);
      }
    }

    thinkEnemy(e, now) {
      const p = this.player, dx = p.x - e.x;
      if (now < e.stun) return;
      if (e.kind === 'walker') {
        if (e.anims.currentAnim?.key !== 'kkiruk_walk') e.play('kkiruk_walk');
        e.setVelocityX(Math.abs(dx) < 650 ? Math.sign(dx) * 95 : 0);
        e.setFlipX(dx < 0);
      } else if (e.kind === 'diver') {
        if (e.mode === 'hover') {
          if (e.anims.currentAnim?.key !== 'diver_fly') e.play('diver_fly');
          e.setVelocity(Math.abs(dx) < 700 ? Math.sign(dx) * 80 : 0, (e.baseY + Math.sin(now / 300) * 12 - e.y) * 3);
          e.setFlipX(dx < 0);
          if (Math.abs(dx) < 170 && now > e.modeUntil) {
            e.mode = 'dive'; e.modeUntil = now + 800; e.play('diver_dive');
            const v = new Phaser.Math.Vector2(dx, p.y - e.y).normalize().scale(400); e.setVelocity(v.x, v.y);
          }
        } else if (e.mode === 'dive') {
          if (now > e.modeUntil || e.y > GROUND - 40) { e.mode = 'rise'; e.setVelocity(0, -220); e.play('diver_fly'); }
        } else if (e.y <= e.baseY) { e.mode = 'hover'; e.modeUntil = now + 900; }
      } else if (e.kind === 'boss') this.thinkBoss(e, now, dx);
    }

    thinkBoss(b, now, dx) {
      const rage = b.hp <= b.maxHp / 2;
      if (b.mode === 'walk') {
        if (b.anims.currentAnim?.key !== 'boss_idle') b.play('boss_idle');
        b.setFlipX(dx < 0); b.setVelocityX(Math.sign(dx) * (rage ? 110 : 75));
        if (now > b.modeUntil) {
          b.step += 1; b.setVelocityX(0);
          if (b.step % 2) { b.mode = 'warn'; b.modeUntil = now + (rage ? 380 : 550); b.setTint(0xfff1a0); }
          else { b.mode = 'throw'; b.modeUntil = now + 700; b.play('boss_throw'); this.time.delayedCall(250, () => !b.dead && this.throwFeathers(b)); }
        }
      } else if (b.mode === 'warn') {
        if (now > b.modeUntil) { b.clearTint(); b.mode = 'dash'; b.modeUntil = now + 650; b.play('boss_dash'); b.setVelocityX((b.flipX ? -1 : 1) * (rage ? 600 : 500)); }
      } else if (b.mode === 'dash') {
        if (now > b.modeUntil || b.body.blocked.left || b.body.blocked.right) {
          if (b.body.blocked.left || b.body.blocked.right) this.cameras.main.shake(150, 0.01);
          b.setVelocityX(0); b.mode = 'walk'; b.modeUntil = now + (rage ? 1100 : 1700);
        }
      } else if (b.mode === 'throw' && now > b.modeUntil) { b.mode = 'walk'; b.modeUntil = now + (rage ? 1100 : 1700); }
    }

    finish(win) {
      if (this.over) return; this.over = true;
      this.physics.pause();
      const sec = Math.round((this.time.now - this.started) / 1000);
      const bonus = win ? Math.max(0, 300 - sec) * 5 + this.player.hp * 100 : 0;
      this.score += bonus;
      this.scoreText.setText(`점수 ${this.score}`);
      this.add.rectangle(0, 0, W, H, 0x0b1622, 0.6).setOrigin(0).setScrollFactor(0).setDepth(300);
      this.add.text(W / 2, 170, win ? '출근 완료!' : '오늘은 지각…', { fontFamily: FONT, fontSize: '64px', color: win ? '#ffe9a8' : '#ffffff', stroke: '#1b2a3a', strokeThickness: 8 }).setOrigin(0.5).setScrollFactor(0).setDepth(301);
      this.add.text(W / 2, 260, win ? `걸린 시간 ${sec}초 · 남은 체력 ${this.player.hp}\n점수 ${this.score} (보너스 ${bonus})` : `점수 ${this.score}\n갈매미는 무거워서 쉽게 쓰러지지 않아요. 다시!`,
        { fontFamily: FONT, fontSize: '26px', color: '#ffffff', align: 'center', lineSpacing: 8 }).setOrigin(0.5).setScrollFactor(0).setDepth(301);
      const again = this.add.text(W / 2, 380, '다시 하기', { fontFamily: FONT, fontSize: '34px', color: '#1b2a3a', backgroundColor: '#ffe9a8', padding: { x: 28, y: 12 } }).setOrigin(0.5).setScrollFactor(0).setDepth(301).setInteractive({ useHandCursor: true });
      const home = this.add.text(W / 2, 455, '홈페이지로', { fontFamily: FONT, fontSize: '22px', color: '#dfe8f0' }).setOrigin(0.5).setScrollFactor(0).setDepth(301).setInteractive({ useHandCursor: true });
      this.time.delayedCall(500, () => {
        again.once('pointerdown', () => this.scene.restart());
        home.once('pointerdown', () => { window.location.href = '/'; });
        this.input.keyboard.once('keydown-ENTER', () => this.scene.restart());
        this.input.keyboard.once('keydown-R', () => this.scene.restart());
      });
    }

    update() {
      const now = this.time.now, p = this.player, k = this.keys, pad = this.pad;
      for (const l of this.layers) l.ts.tilePositionX = this.cameras.main.scrollX * l.f / l.s;
      if (this.over) return;
      pad.update();

      const left = k.LEFT.isDown || k.A.isDown || pad.down('left');
      const right = k.RIGHT.isDown || k.D.isDown || pad.down('right');
      const jump = Phaser.Input.Keyboard.JustDown(k.UP) || Phaser.Input.Keyboard.JustDown(k.SPACE) || Phaser.Input.Keyboard.JustDown(k.W) || Phaser.Input.Keyboard.JustDown(k.K) || Phaser.Input.Keyboard.JustDown(k.X) || pad.pressed('jump');
      const atk = Phaser.Input.Keyboard.JustDown(k.Z) || Phaser.Input.Keyboard.JustDown(k.J) || pad.pressed('attack');
      const onGround = p.body.blocked.down || p.body.touching.down;

      if (now >= p.hurt) {
        const speed = now < p.atkUntil && onGround ? 90 : 230;
        if (left && !right) { p.setVelocityX(-speed); p.facing = -1; }
        else if (right && !left) { p.setVelocityX(speed); p.facing = 1; }
        else p.setVelocityX(0);
        p.setFlipX(p.facing < 0);
        if (jump && onGround) p.setVelocityY(-600);
        if (atk) this.attack();
        if (now >= p.atkUntil) p.play(!onGround ? 'player_jump' : p.body.velocity.x ? 'player_walk' : 'player_idle', true);
      }
      if (p.y > H + 100) { p.hp = 1; this.hurtPlayer(p.x); }

      while (this.waves.length && p.x > this.waves[0].at) for (const [kind, x] of this.waves.shift().spawn) this.spawn(kind, x);
      for (const e of this.enemies.getChildren()) if (!e.dead) this.thinkEnemy(e, now);
      for (const f of this.feathers.getChildren()) f.rotation = Math.atan2(f.body.velocity.y, f.body.velocity.x);
      if (!this.bossStarted && p.x > ARENA + 80) this.spawnBoss();
    }
  }

  function boot(list) {
    available = new Set(list);
    const game = new Phaser.Game({
      type: Phaser.AUTO, parent: 'game', width: W, height: H, backgroundColor: '#1b2a3a',
      scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
      physics: { default: 'arcade', arcade: { gravity: { y: 1400 }, debug: /[?&]debug\b/.test(location.search) } },
      input: { activePointers: 4 },
      scene: [Title, Stage],
    });
    if (/[?&]test\b/.test(location.search)) window.__gmm = game;
  }

  // 글꼴을 기다린 뒤(최대 1.5초) 있는 그림 목록을 받아 시작
  const font = document.fonts ? Promise.race([document.fonts.load('32px Jua'), new Promise(r => setTimeout(r, 1500))]) : Promise.resolve();
  font.then(() => fetch('/play/sprites.json')).then(r => r.ok ? r.json() : []).catch(() => []).then(boot);
})();
