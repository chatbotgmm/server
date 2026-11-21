// 갈매미맨 랜덤 디펜스 전용 2.5D 효과(2026-11-15 사용자: 「코드 분석해서 새롭게 디벨롭해도 돼」).
// pixelfx(MIT)의 효과 틀(meta + create → update/draw)과 그리기 도구(Painter: dot·ellipse·glow)를 그대로 쓰되,
// 옆에서 본 바닥 한 줄 대신 「판 바닥 = 납작한 타원(세로 0.62배, 판의 기술 범위 표시와 같은 비율)」 위에서 움직이게 새로 짬.
// 알갱이는 바닥 좌표(gx, gy)와 높이(z)를 따로 가지며 화면에는 (gx, gy×0.62 − z)로 그림 → 바닥에 퍼지고 튀어 오르고 떨어지는 것이 2.5D로 보임.
import { Rng, fit, clamp, TAU, WHITE } from '../../vendor/pixelfx/src/core/index.js';

const FLAT = 0.62;
const sx = (q) => q.gx, sy = (q) => q.gy * FLAT - q.z;   // 바닥·높이 → 화면(효과 원점 기준)
const easeOut = (u) => 1 - Math.pow(1 - clamp(u), 3);

// 바닥 타원 고리(두께 w). half: 0 전체, 1 앞쪽(아래 반)만, -1 뒤쪽만
function eRing(p, r, w, c, a, half = 0) {
  if (r <= 0.5 || a <= 0) return;
  const n = Math.max(24, Math.ceil(r * 3.2));
  for (let i = 0; i < n; i++) {
    const ang = (i / n) * TAU, s = Math.sin(ang);
    if ((half > 0 && s < 0) || (half < 0 && s >= 0)) continue;
    for (let k = 0; k < w; k += 0.8) p.dot(Math.cos(ang) * (r - k), s * (r - k) * FLAT, c, a);
  }
}
// 알갱이 묶음: 바닥 위를 날고(gx, gy), 높이 z는 중력으로 떨어져 바닥(0)에서 튐
class Bits25 {
  constructor() { this.l = []; }
  add(q) { q.age = 0; q.z ??= 0; q.vz ??= 0; q.g ??= 0; q.drag ??= 0; this.l.push(q); return q; }
  update(dt) {
    for (let i = this.l.length - 1; i >= 0; i--) {
      const q = this.l[i];
      if ((q.age += dt) >= q.life) { this.l.splice(i, 1); continue; }
      const d = 1 - q.drag * dt;
      q.vx *= d; q.vy *= d;
      q.gx += q.vx * dt; q.gy += q.vy * dt;
      q.vz -= q.g * dt; q.z += q.vz * dt;
      if (q.g > 0 && q.z < 0) { q.z = 0; q.vz = Math.abs(q.vz) * (q.bounce ?? 0.25); q.vx *= 0.6; q.vy *= 0.6; }
      if (q.spin) q.a = (q.a ?? 0) + q.spin * dt;
    }
  }
  k(q) { return clamp(1 - q.age / q.life); }
}

// ── 1) 자연 폭발: 바닥에 초록 물결이 퍼지며 둘레에서 풀잎·덩굴이 솟고, 잎이 사방으로 흩날림 ──
export const bloom = {
  meta: { name: 'GMM bloom', duration: 1.5, options: { palette: ['#061a06', '#0e3a10', '#1e6a1c', '#3ca02c', '#78d050', '#c0f090', '#f4ffe0'], reach: 60, seed: 3 } },
  create(o) {
    const C = fit(o.palette, 7), R = new Rng(o.seed), RE = o.reach, bits = new Bits25(), blades = [];
    for (let i = 0; i < 22; i++) { const a = R.range(0, TAU), q = R.range(0.55, 1); blades.push({ a, q, h: R.range(6, 14) * (1.15 - q * 0.4), lean: R.range(-3, 3), t0: 0.12 + 0.4 * easeInv(q) }); }
    let t = 0, popped = false;
    function easeInv(q) { return 1 - Math.cbrt(1 - q); }
    return {
      update(dt) {
        t += dt;
        if (!popped && t > 0.12) {
          popped = true;
          for (let i = 0; i < 26; i++) { const a = R.range(0, TAU), sp = R.range(40, 110) * RE / 60; bits.add({ gx: 0, gy: 0, z: 6, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, vz: R.range(40, 90), g: 120, drag: 1.4, life: R.range(0.9, 1.3), c: R.pick([C[4], C[5], C[3]]), a: R.range(0, TAU), spin: R.range(-9, 9), kind: 'leaf' }); }
        }
        bits.update(dt);
      },
      draw(p) {
        const u = easeOut((t - 0.12) / 0.45), r = RE * u, out = 1 - clamp((t - 1.0) / 0.5);
        if (t < 0.2) p.glow(0, -6, 18, C[5], 0.8 * (1 - t / 0.2));
        if (t > 0.12) {
                    eRing(p, r, 3, C[5], 0.95 * out * (1 - u * 0.3));
          eRing(p, r * 0.7, 1.6, C[4], 0.6 * out);
          p.glow(0, 0, r * 0.9, C[4], 0.25 * out);
        }
        for (const b of blades) {   // 바닥 둘레에서 솟는 풀잎(앞쪽이 아래, 뒤쪽이 위에 그려짐)
          const g = clamp((t - b.t0) / 0.12); if (g <= 0) continue;
          const x = Math.cos(b.a) * RE * b.q, y = Math.sin(b.a) * RE * b.q * FLAT, h = b.h * (g < 1 ? g * 1.15 : 1) * out;
          for (let k = 0; k <= h; k += 0.6) { const v = k / Math.max(1, h); p.dot(x + b.lean * v * v, y - k, v > 0.75 ? C[5] : v > 0.35 ? C[4] : C[3], out); }
        }
        for (const q of bits.l) { const k = bits.k(q), x = sx(q), y = sy(q), ca = Math.cos(q.a), sa = Math.sin(q.a);   // 흩날리는 잎(돌며)
          for (let d = -2; d <= 2; d += 0.7) p.dot(x + ca * d, y + sa * d * 0.6, Math.abs(d) < 0.8 ? C[6] : q.c, k);
          if (q.z > 0.5) p.dot(q.gx, q.gy * FLAT, C[1], 0.35 * k); }   // 바닥 그림자
      },
    };
  },
};

// ── 2) 땅 울림(기본 속성 폭발): 바닥 충격파 + 방사형 금 + 튀어 오르는 돌 + 먼지 ──
export const quake = {
  meta: { name: 'GMM quake', duration: 1.3, options: { palette: ['#1a1008', '#4a2c14', '#8a5a2a', '#c88c48', '#f0bc78', '#ffe0b0', '#fff8ec'], reach: 60, seed: 5 } },
  create(o) {
    const C = fit(o.palette, 7), R = new Rng(o.seed), RE = o.reach, rocks = new Bits25(), dust = new Bits25(), cracks = [];
    for (let i = 0; i < 9; i++) { const a = (i / 9) * TAU + R.range(-0.25, 0.25), L = R.range(0.45, 0.85) * RE, pts = [[0, 0]]; let x = 0, y = 0; for (let s = 1; s <= 5; s++) { const aa = a + R.range(-0.35, 0.35); x += Math.cos(aa) * L / 5; y += Math.sin(aa) * L / 5; pts.push([x, y]); } cracks.push(pts); }
    let t = 0, hit = false;
    return {
      update(dt) {
        t += dt;
        if (!hit) {
          hit = true;
          for (let i = 0; i < 14; i++) { const a = R.range(0, TAU), sp = R.range(30, 90) * RE / 60, s = R.range(1.2, 2.6); rocks.add({ gx: Math.cos(a) * 4, gy: Math.sin(a) * 4, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, vz: R.range(50, 110), g: 260, bounce: 0.3, drag: 0.8, life: R.range(0.8, 1.2), s, c: R.pick([C[2], C[3], C[1]]) }); }
        }
        const fr = RE * easeOut(t / 0.4);
        if (t < 0.4 && R.chance(0.8)) { const a = R.range(0, TAU); dust.add({ gx: Math.cos(a) * fr, gy: Math.sin(a) * fr, vx: Math.cos(a) * 12, vy: Math.sin(a) * 12, z: 1, vz: R.range(6, 16), life: R.range(0.6, 0.9), r: R.range(2.5, 4.5) }); }
        rocks.update(dt); dust.update(dt);
      },
      draw(p) {
        const u = easeOut(t / 0.4), out = 1 - clamp((t - 0.8) / 0.5);
        if (t < 0.12) p.glow(0, 0, 26, C[5], 0.9 * (1 - t / 0.12));
        for (const pts of cracks) {   // 바닥 금: 처음엔 밝게 빛나고 점점 어두운 금으로
          const n = Math.ceil(pts.length * clamp(t / 0.18));
          for (let i = 1; i < n; i++) { const [x0, y0] = pts[i - 1], [x1, y1] = pts[i]; p.line(x0, y0 * FLAT, x1, y1 * FLAT, t < 0.3 ? C[5] : C[1], out); }
        }
        if (t < 0.55) { eRing(p, RE * u, 3.5 * (1 - u) + 1, C[4], 1 - u * 0.6); p.glowRing(0, 0, RE * u * 0.8, 6, C[4], 0.3 * (1 - u)); }
        for (const q of dust.l) { const k = dust.k(q); p.disc(sx(q), sy(q), q.r * (1.4 - k * 0.4), C[3], 0.35 * k); }
        for (const q of rocks.l) { const k = Math.min(1, rocks.k(q) * 3); if (q.z > 0.5) p.dot(q.gx, q.gy * FLAT, C[0], 0.4 * k); p.ellipse(sx(q), sy(q) - q.s / 2, q.s, q.s * 0.8, (d) => (d < 0.5 ? C[4] : q.c), k); }
      },
    };
  },
};

// ── 3) 속성 장판: 바닥 원 + 둘레 룬 + 속성마다 다른 알갱이(불씨·서리·물결·그림자·번개·잎·먼지) ──
export const field = {
  meta: { name: 'GMM field', duration: 3.2, options: { palette: ['#140a26', '#2b1252', '#481c88', '#6b2cc0', '#9a48ec', '#c684ff', '#ecc8ff'], reach: 50, seed: 7, kind: 'NORMAL' } },
  create(o) {
    const C = fit(o.palette, 7), R = new Rng(o.seed), RE = o.reach, K = o.kind, bits = new Bits25(), rip = [];
    let t = 0;
    return {
      update(dt) {
        t += dt;
        const on = t < 2.8, rate = RE / 50;
        const inside = () => { const a = R.range(0, TAU), q = Math.sqrt(R.next()) * RE * 0.92; return [Math.cos(a) * q, Math.sin(a) * q]; };
        if (on && R.chance(Math.min(1, 0.8 * rate))) {
          const [gx, gy] = inside();
          if (K === 'FIRE') bits.add({ gx, gy, z: 0, vx: 0, vy: 0, vz: R.range(18, 34), life: R.range(0.6, 1.0), c: R.pick([C[5], C[4], C[6]]), kind: 'ember', ph: R.range(0, TAU) });
          else if (K === 'ICE') bits.add({ gx, gy, z: R.range(0, 10), vx: 0, vy: 0, vz: R.range(-2, 4), life: R.range(0.4, 0.7), kind: 'glint' });
          else if (K === 'WATER') { if (R.chance(0.5)) rip.push({ gx, gy, age: 0, life: R.range(0.6, 0.9), r: R.range(5, 10) }); }
          else if (K === 'DARK') bits.add({ gx, gy, z: 0, vx: 0, vy: 0, vz: R.range(10, 20), life: R.range(0.8, 1.2), kind: 'wisp', ph: R.range(0, TAU) });
          else if (K === 'LIGHTNING') { if (R.chance(0.9)) bits.add({ gx, gy, z: 0, vx: 0, vy: 0, life: 0.16, kind: 'arc', a: R.range(0, TAU), L: R.range(6, 12), seed: R.range(0, 99) | 0 }); }
          else if (K === 'NATURE') bits.add({ gx, gy, z: 0, vx: R.range(-6, 6), vy: R.range(-6, 6), vz: R.range(10, 22), life: R.range(0.8, 1.2), kind: 'leaf', a: R.range(0, TAU), spin: R.range(-6, 6), c: R.pick([C[4], C[5]]) });
          else bits.add({ gx, gy, z: 0, vx: R.range(-5, 5), vy: R.range(-5, 5), vz: R.range(4, 9), life: R.range(0.6, 0.9), kind: 'dust', r: R.range(2, 3.5) });
        }
        for (let i = rip.length - 1; i >= 0; i--) if ((rip[i].age += dt) >= rip[i].life) rip.splice(i, 1);
        bits.update(dt);
      },
      draw(p) {
        const inn = easeOut(t / 0.25), out = 1 - clamp((t - 2.8) / 0.4), pulse = 0.8 + 0.2 * Math.sin(t * 6), r = RE * inn;
                eRing(p, r, 2, C[5], 0.9 * out); eRing(p, r * 0.92, 1, C[4], 0.45 * out * pulse);
        const ticks = 12, rot = t * 0.8;   // 둘레 룬 표식(천천히 돎)
        for (let i = 0; i < ticks; i++) { const a = rot + (i / ticks) * TAU, x = Math.cos(a) * r * 0.84, y = Math.sin(a) * r * 0.84 * FLAT; p.dot(x, y, C[6], out); p.dot(x + 1, y, C[5], out * 0.8); p.dot(x, y - 1, C[5], out * 0.6); }
        p.glow(0, 0, r, C[4], 0.22 * out * pulse); p.glow(r * 0.5, 0, r * 0.6, C[4], 0.1 * out); p.glow(-r * 0.5, 0, r * 0.6, C[4], 0.1 * out);   // 바닥 채움은 빛으로만(반투명 점은 바둑판 무늬가 됨 — 검토)
        for (const w of rip) { const k = 1 - w.age / w.life; eRingAt(p, w.gx, w.gy, w.r * (1.2 - k), C[5], 0.8 * k * out); }   // 물: 작은 물결
        for (const q of bits.l) {
          const k = bits.k(q) * out, x = sx(q), y = sy(q);
          if (q.kind === 'ember') { const wob = Math.sin(t * 9 + q.ph) * 1.2; const ec = k > 0.6 ? C[6] : q.c; p.dot(x + wob, y, ec, k); p.dot(x + wob + 1, y, ec, k); p.dot(x + wob, y + 1, q.c, k); p.dot(x + wob + 1, y + 1, C[3], k * 0.7); p.glow(x + wob, y, 5, C[4], 0.4 * k); }
          else if (q.kind === 'glint') { const s = k > 0.5 ? 2 : 1; p.dot(x, y, WHITE, k); for (let d = 1; d <= s; d++) { p.dot(x + d, y, C[5], k); p.dot(x - d, y, C[5], k); p.dot(x, y + d, C[5], k); p.dot(x, y - d, C[5], k); } }
          else if (q.kind === 'wisp') { const wob = Math.sin(t * 4 + q.ph + q.z * 0.2) * 2.5; p.disc(x + wob, y, 3 * k + 1, C[2], 0.6 * k); p.dot(x + wob, y, C[5], k); p.dot(x + wob + 1, y, C[4], k); }
          else if (q.kind === 'arc') { let ax = q.gx, ay = q.gy * FLAT; const R2 = new Rng(q.seed); for (let s = 0; s < 4; s++) { const nx = ax + Math.cos(q.a) * q.L / 4 + R2.range(-1.5, 1.5), ny = ay + Math.sin(q.a) * q.L / 4 * FLAT + R2.range(-1.5, 1.5); p.line(ax, ay, nx, ny, s % 2 ? C[6] : WHITE, 1); ax = nx; ay = ny; } p.glow(q.gx, q.gy * FLAT, 8, C[5], 0.5); }
          else if (q.kind === 'leaf') { const ca = Math.cos(q.a); for (let d = -2.2; d <= 2.2; d += 0.6) { p.dot(x + ca * d, y + Math.sin(q.a) * d * 0.5, Math.abs(d) < 0.7 ? C[6] : q.c, k); p.dot(x + ca * d, y + Math.sin(q.a) * d * 0.5 + 1, q.c, k * 0.6); } }
          else p.disc(x, y, q.r * (1.3 - k * 0.3), C[3], 0.3 * k);
        }
      },
    };
  },
};
function eRingAt(p, cx, cy, r, c, a) { const n = Math.max(12, Math.ceil(r * 3)); for (let i = 0; i < n; i++) { const ang = (i / n) * TAU; p.dot(cx + Math.cos(ang) * r, cy * FLAT + Math.sin(ang) * r * FLAT, c, a); } }

// ── 4) 묶기: 바닥 마법진이 그려지고 둘레 여섯 곳에서 사슬이 솟아 가운데 위로 조여 듦 ──
export const bind = {
  meta: { name: 'GMM bind', duration: 1.5, options: { palette: ['#0a1a2a', '#1c4060', '#3a7aa8', '#6ab4dc', '#a8e0f4', '#dcf6ff', '#ffffff'], reach: 50, seed: 9 } },
  create(o) {
    const C = fit(o.palette, 7), RE = o.reach, N = 6;
    let t = 0;
    return {
      update(dt) { t += dt; },
      draw(p) {
        const draw = clamp(t / 0.3), out = 1 - clamp((t - 1.15) / 0.35), r = RE;
        // 마법진: 고리가 한 바퀴 그려지고, 안쪽에 육각 별
        const n = Math.ceil(r * 3.2 * draw);
        for (let i = 0; i < n; i++) { const a = (i / (r * 3.2)) * TAU - Math.PI / 2; p.dot(Math.cos(a) * r, Math.sin(a) * r * FLAT, C[5], out); p.dot(Math.cos(a) * (r - 3), Math.sin(a) * (r - 3) * FLAT, C[3], out * 0.8); }
        if (draw >= 1) for (let i = 0; i < N; i++) { const a0 = (i / N) * TAU + t * 0.6, a1 = a0 + (2 / N) * TAU; p.line(Math.cos(a0) * (r - 3), Math.sin(a0) * (r - 3) * FLAT, Math.cos(a1) * (r - 3), Math.sin(a1) * (r - 3) * FLAT, C[4], out * 0.8); }
        p.glow(0, 0, r, C[4], 0.22 * out);
        // 사슬: 둘레 → 가운데 위(높이 18)로, 고리 하나씩 번갈아 밝게
        const g = easeOut((t - 0.25) / 0.35);
        if (g > 0) for (let i = 0; i < N; i++) {
          const a = (i / N) * TAU + 0.3, x0 = Math.cos(a) * r, y0 = Math.sin(a) * r * FLAT, x1 = 0, y1 = -18, links = 7;
          for (let l = 0; l < links * g; l++) { const v = l / links, x = x0 + (x1 - x0) * v, y = y0 + (y1 - y0) * v - Math.sin(v * Math.PI) * 6, lc = l % 2 ? C[4] : C[6];
            p.ellipse(x, y, l % 2 ? 1.2 : 2, l % 2 ? 2 : 1.2, lc, out); }
          p.glow(x0, y0, 6, C[5], 0.5 * out);
        }
        if (g >= 1 && t < 0.75) p.glow(0, -18, 12, WHITE, 0.6 * (1 - (t - 0.6) / 0.15));
      },
    };
  },
};

// ── 5) 보스 처치 축하: 금빛 물결 세 겹이 바닥에 퍼지고, 빛 알갱이가 나선으로 솟아 위에서 반짝임(공격처럼 내리꽂지 않음) ──
export const victory = {
  meta: { name: 'GMM victory', duration: 2.0, options: { palette: ['#3b2a08', '#7a5a14', '#c49a2e', '#f0c84a', '#ffe28a', '#fff4c8', '#ffffff'], reach: 70, seed: 11 } },
  create(o) {
    const C = fit(o.palette, 7), R = new Rng(o.seed), RE = o.reach, motes = new Bits25();
    let t = 0;
    return {
      update(dt) {
        t += dt;
        if (t < 1.2 && R.chance(0.9)) { const a = R.range(0, TAU), q = R.range(0.2, 0.9) * RE; motes.add({ gx: Math.cos(a) * q, gy: Math.sin(a) * q, z: 0, vx: -Math.sin(a) * 18, vy: Math.cos(a) * 18, vz: R.range(30, 60), drag: 0.6, life: R.range(0.9, 1.3), c: R.pick([C[4], C[5], C[6]]) }); }
        motes.update(dt);
      },
      draw(p) {
        const out = 1 - clamp((t - 1.5) / 0.5);
        for (let i = 0; i < 3; i++) { const u = clamp((t - i * 0.22) / 0.8); if (u <= 0 || u >= 1) continue; const r = RE * easeOut(u); eRing(p, r, 2.5 * (1 - u) + 1, i ? C[4] : C[5], 1 - u); }
        eRing(p, RE * 0.35, 1, C[5], 0.6 * out);
        p.glow(0, -10, 34, C[4], 0.35 * out);
        for (const q of motes.l) { const k = motes.k(q) * out; p.dot(sx(q), sy(q), q.c, k); if (k > 0.6) p.glow(sx(q), sy(q), 3, C[5], 0.4 * k); }
        // 위에서 반짝이는 큰 별(네 갈래, 커졌다 작아짐)
        const s = Math.sin(clamp((t - 0.35) / 0.9) * Math.PI) * 11;
        if (s > 0.5) { const y = -46; for (let d = -s; d <= s; d += 0.5) { const w = 1 - Math.abs(d) / s; p.dot(d, y, w > 0.5 ? WHITE : C[5], out); p.dot(0, y + d, w > 0.5 ? WHITE : C[5], out); } p.glow(0, y, s * 2, C[5], 0.7 * out); }
      },
    };
  },
};
