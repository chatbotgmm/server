// 랜덤 디펜스 화려한 효과(2026-11-15 사용자 요청): pixelfx(MIT, vendor/pixelfx — github.com/patch-gg/pixelfx)에서 고른 효과만 묶어
// public/defense/pixfx.js 한 파일로 만든다(scripts/web/build-pixfx.sh). 판 위에 투명한 도트 층을 하나 두고 그 위에 효과를 그린다.
// 큰 순간에만 쓴다(고유 기술·보스 처치·조합 완성·위습 찾기·외부탐사 성공·전설 이상이 보스를 칠 때) — 기본 공격 전부에 쓰면 휴대폰에서 느려짐.
import { createFrame, clearFrame, createFx, composite } from '../../vendor/pixelfx/src/core/index.js';
// 2026-11-15 사용자 요청(판이 위에서 내려다보는 2.5D): 옆에서 본 효과(바닥이 가로 한 줄인 plasma·big·thorns·icespikes·timestop·snow·smite·feather)를 빼고
// 바닥 타원 위에 서는 효과로 바꿈(fire-ring·frost-nova·thunderclap·holy-pillar·implosion·magic-circle-cast·rune-circle·meteor)
import * as crit from '../../vendor/pixelfx/src/fx/impacts/crit-hit.js';
import * as spark from '../../vendor/pixelfx/src/fx/impacts/hit-spark.js';
import * as zap from '../../vendor/pixelfx/src/fx/lightning/zap-hit.js';
import * as strike from '../../vendor/pixelfx/src/fx/lightning/lightning-strike.js';
import * as darkx from '../../vendor/pixelfx/src/fx/shadow/dark-explosion.js';
import * as starfall from '../../vendor/pixelfx/src/fx/arcane/star-fall.js';
import * as collect from '../../vendor/pixelfx/src/fx/pickups/collect-burst.js';
import * as quest from '../../vendor/pixelfx/src/fx/ui/quest-complete.js';
import * as healaoe from '../../vendor/pixelfx/src/fx/holy/heal-aoe.js';
import * as portal from '../../vendor/pixelfx/src/fx/spawn/spawn-portal.js';
import * as firering from '../../vendor/pixelfx/src/fx/fire/fire-ring.js';
import * as frostnova from '../../vendor/pixelfx/src/fx/ice/frost-nova.js';
import * as thunderclap from '../../vendor/pixelfx/src/fx/lightning/thunderclap.js';
import * as holypillar from '../../vendor/pixelfx/src/fx/holy/holy-pillar.js';
import * as implosion from '../../vendor/pixelfx/src/fx/explosions/implosion.js';
import * as magiccircle from '../../vendor/pixelfx/src/fx/arcane/magic-circle-cast.js';
import * as runecircle from '../../vendor/pixelfx/src/fx/arcane/rune-circle.js';
import * as meteor from '../../vendor/pixelfx/src/fx/fire/meteor.js';
import * as firework from '../../vendor/pixelfx/src/fx/explosions/firework-burst.js';
// 보스 처치 축하(2026-11-15 사용자 요청: 빛기둥·불꽃놀이는 공격처럼 보여 폭발형 마법딜러 고유 기술로 옮김) — 공격처럼 안 보이는 색종이·금별
// (coin-collect는 동전 세는 칸이 붙어 판 밖으로 삐져나와 뺌 — 검토에서 찾음)
import * as confetti from '../../vendor/pixelfx/src/fx/ui/confetti.js';
import * as powerstar from '../../vendor/pixelfx/src/fx/pickups/power-star.js';


const LIB = { crit, spark, zap, strike, darkx, starfall, collect, quest, healaoe, portal, firering, frostnova, thunderclap, holypillar, implosion, magiccircle, runecircle, meteor, firework, confetti, powerstar };
// 속성 색(어두운 것 → 밝은 것). 효과의 palette를 바꿔 같은 모양을 속성 색으로
export const PALETTE = {
  FIRE: ['#2a0800', '#6a1800', '#b83a08', '#ec6a1c', '#ffa040', '#ffd890', '#fff6e0'],
  WATER: ['#04122a', '#0a2c66', '#1458b0', '#2c8ae8', '#64b8ff', '#b4e0ff', '#f0faff'],
  ICE: ['#0a1a2a', '#1c4060', '#3a7aa8', '#6ab4dc', '#a8e0f4', '#dcf6ff', '#ffffff'],
  LIGHTNING: ['#1a1400', '#4a3a00', '#9a7a00', '#e0b818', '#ffe14a', '#fff2a0', '#ffffff'],
  NATURE: ['#061a06', '#0e3a10', '#1e6a1c', '#3ca02c', '#78d050', '#c0f090', '#f4ffe0'],
  HOLY: ['#2a2008', '#5a4614', '#a08030', '#d8b860', '#ffe8a0', '#fff6d8', '#ffffff'],
  DARK: ['#0e0418', '#2a0c48', '#4c1a84', '#7a34c0', '#b070f0', '#dcb4ff', '#f6ecff'],
  NORMAL: ['#1a1008', '#4a2c14', '#8a5a2a', '#c88c48', '#f0bc78', '#ffe0b0', '#fff8ec'],
};
export const NAMES = Object.keys(LIB);
// 효과 하나가 한 프레임에 쓰는 시간(ms, 2026-11-15 헤드리스 크롬에서 잼). 동시에 떠 있는 합이 budget을 넘으면 새 효과를 건너뜀(force면 오래된 것을 밀어냄)
export const COST = { darkx: 2.5, strike: 1.8, healaoe: 1.35, portal: 1.1, starfall: 0.9, quest: 0.75, zap: 0.7, crit: 0.4, collect: 0.4, spark: 0.15,
  // 아래 9개(2026-11-15 추가): node에서 338×425 도트판에 1.4초 동안 update+draw 평균(ms). 기존 효과를 같은 방법으로 재면 크롬 값의 0.77~1.9배(darkx 3.25·strike 2.13·healaoe 0.71)라 어림값
  firering: 1.0, frostnova: 1.1, thunderclap: 1.2, holypillar: 2.2, implosion: 1.4, magiccircle: 1.4, runecircle: 1.6, meteor: 2.3, firework: 1.2,
  confetti: 0.6, powerstar: 1.4 };   // 같은 방법으로 잼(2026-11-15 다시 잰 holypillar는 2.19라 2.2로 올림)
// 효과마다 기본 색 수가 달라(7~9색) 속성 색 7개를 그 수에 맞게 늘이거나 줄임
function fitPalette(src, n) { if (!n || n === src.length) return src; return Array.from({ length: n }, (_, i) => src[Math.round(i * (src.length - 1) / (n - 1))]); }

// 도트 층: 판 영역(w×h 논리 좌표)을 px로 나눈 해상도. spawn(이름, 판 좌표 x, y, {elem, dur, palette, …})
export function createLayer(w, h, px = 2) {
  const fw = Math.ceil(w / px), fh = Math.ceil(h / px);
  const frame = createFrame(fw, fh), img = new ImageData(fw, fh);
  const cv = document.createElement('canvas'); cv.width = fw; cv.height = fh;
  const cx = cv.getContext('2d');
  let list = [];
  return {
    max: 6,          // 동시에 6개까지
    budget: 6,       // 동시에 떠 있는 효과 비용 합(ms) 상한
    count: () => list.length,
    cost: () => list.reduce((a, it) => a + it.cost, 0),
    clear() { list = []; },
    spawn(name, x, y, o = {}) {
      const mod = LIB[name]; if (!mod) return false;
      const cost = COST[name] ?? 1;
      if (o.force) { while (list.length && (list.length >= this.max || this.cost() + cost > Math.max(this.budget, cost))) list.shift(); }   // 꼭 보여야 하는 것: 오래된 것을 밀어냄
      else if (list.length >= this.max || this.cost() + cost > this.budget) return false;   // 무거우면 건너뜀(게임이 느려지지 않게)
      const opts = { x: x / px, y: y / px, seed: (Math.random() * 1e6) | 0, ...o };
      const mo = mod.meta?.options ?? {};
      if (o.elem && PALETTE[o.elem]) {   // 속성 색: 주 색과 보조 색(accent가 있는 효과) 모두
        opts.palette = fitPalette(PALETTE[o.elem], mo.palette?.default?.length);
        if (mo.accent) opts.accent = PALETTE[o.elem].slice(1, 6);
      }
      if (o.reach != null) { if (mo.reach) opts.reach = Math.max(12, o.reach / px); else delete opts.reach; }   // 퍼지는 넓이(판 좌표) → 도트 좌표. 기술 범위와 같은 넓이로
      delete opts.elem; delete opts.dur; delete opts.force;
      list.push({ fx: createFx(mod, opts), stopAt: o.dur ?? 1.4, cost });   // 연기 꼬리가 긴 효과도 dur초에서 끊어 짧고 강하게
      return true;
    },
    update(dt) {
      for (const it of list) { it.fx.update(dt); if (it.fx.t >= it.stopAt) it.fx.stop(); }
      list = list.filter((it) => !it.fx.done());
    },
    draw(ctx, dx, dy, dw, dh) {
      if (!list.length) return;
      clearFrame(frame);
      for (const it of list) it.fx.draw(frame);
      composite(frame, img.data, null);
      cx.putImageData(img, 0, 0);
      const keep = ctx.imageSmoothingEnabled;
      ctx.imageSmoothingEnabled = false;   // 도트가 번지지 않게
      ctx.drawImage(cv, dx, dy, dw, dh);
      ctx.imageSmoothingEnabled = keep;
    },
  };
}
