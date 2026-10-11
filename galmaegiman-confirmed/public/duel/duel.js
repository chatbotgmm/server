// 수비대 대결 시험판: 새 전투 엔진(src/battle)을 그대로 묶어 브라우저에서 돌림(빌드: scripts/build-play.mjs → dist/duel/duel.js)
// 규칙·유닛·스토리는 서버가 data/battle.yaml·data/story.yaml을 검사해 보내 줌(/duel/data.json). 기술 효과는 그림 없이 코드로 그림
// 스토리 진행(레벨·경험치·배운 기술·승진·깬 스테이지): 「내 갈매미」에 로그인돼 있으면 계정에, 아니면 이 브라우저에 저장
// 로그인돼 있으면 「내 갈매미」 전투 편성을 스토리 팀으로 쓸 수 있고, 스토리 대결에서 가진 아이템을 쓸 수 있음
import { rulesFrom } from '../../src/battle/core.ts';
import { createBattle, choose, rerollAugment, act, pending, legalActions, pickAi, aiChoose, activeOf, itemProblem } from '../../src/battle/engine.ts';
import { bgm, sfx, isMuted, toggleMute, getVol, setVol } from './sound.js';
import { addXp, battleXp, canEvolve, evoChoices, learnChances, learnOffers, learnSkill, slotSkills, statsOf, xpToNext } from '../../src/battle/growth.ts';

// 앱: 화면은 앱 안, 서버는 GMM_BASE, 열쇠는 「내 갈매미」에서 받아 둔 것(GMM_DUEL_KEY), 로그인은 토큰 머리글
const BASE = window.GMM_BASE || '', APP = window.gmmApp || null;
let K = new URLSearchParams(location.search).get('k') || '';
const app = document.getElementById('app'), modal = document.getElementById('modal');
const COLOR = { STONE: '#c9b48a', GOLD: '#f0b93a', FIRE: '#f26b3a', ICE: '#8fdcf5', BLOSSOM: '#f7a3c6', SEA: '#3d94e6', SHADOW: '#8a6bb8', STAR: '#ffe36e', THREAD: '#c3c6dc', GRAVITY: '#5b63c9' };
const ROLE = { ATTACK: '공격', DEFENSE: '수비', UTILITY: '유틸' };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
let q = `?k=${encodeURIComponent(K)}`;
// 게임 이름(바꾸려면 여기만)
const GAME = { name: '갈매미맨 수비대', sub: '항구를 지켜라' };
const chip = a => `<span class="chip" style="background:${COLOR[a]}">${esc(R.attrName(a))}</span>`;
const shuffle = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };

let R, UNITS, STORY, ART, NAMES = {}, BG = [], ICONS = [], EVO = new Set(), b, busy = false, freeLevel = 30, freePick = [];
// 게임 안 확인 창(안드로이드·브라우저 기본 팝업 대신): ask(글, {ok, cancel, danger}) → true/false, tell(글) → 확인만
const gdlg = (text, o = {}) => new Promise(res => {
  const w = document.createElement('div'); w.className = 'gdlg';
  w.innerHTML = `<div class="gdlg-box" role="dialog" aria-modal="true"><p></p><div class="gdlg-row">${o.alert ? '' : `<button class="gdlg-no">${o.cancel ?? '취소'}</button>`}<button class="gdlg-ok ${o.danger ? 'danger' : ''}">${o.ok ?? '확인'}</button></div></div>`;
  w.querySelector('p').textContent = text;
  const end = v => { w.remove(); document.removeEventListener('keydown', key); res(v); };
  const key = e => { if (e.key === 'Escape') end(false); if (e.key === 'Enter') end(true); };
  w.querySelector('.gdlg-ok').onclick = () => end(true); w.querySelector('.gdlg-no')?.addEventListener('click', () => end(false));
  w.onclick = e => { if (e.target === w && !o.alert) end(false); };
  document.addEventListener('keydown', key); document.body.appendChild(w); w.querySelector('.gdlg-ok').focus();
});
const ask = (text, o) => gdlg(text, o), tell = text => gdlg(text, { alert: true });
const josaEul = w => { const c = (w || '').charCodeAt(w.length - 1); return c >= 0xac00 && c <= 0xd7a3 && (c - 0xac00) % 28 ? '을' : '를'; };
let ACC = null;      // 로그인한 계정: { nickname, battleTeam, owned, items }(로그인 안 했으면 null)
let fightId = null;  // 지금 스토리 대결의 서버 표(아이템 쓰기용)
// 「내 갈매미」 API(같은 사이트 쿠키로 로그인 확인)
const myApi = async (path, body) => {
  const h = { 'x-gmm': '1', ...(APP && APP.token() ? { 'x-gmm-app': '1', 'x-gmm-s': APP.token() } : {}) };
  const r = await fetch(`${BASE}/my/api/${path}`, body === undefined ? { headers: h } : { method: 'POST', headers: { 'content-type': 'application/json', ...h }, body: JSON.stringify(body) });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) { const e = new Error(j.error || `오류(${r.status})`); e.status = r.status; throw e; }
  return j;
};
let view;          // 화면에 보이는 상태(엔진은 한 턴을 한꺼번에 계산하므로, 기록을 한 줄씩 보여 주며 체력을 따라 줄임)
let onEnd = null;  // 대결이 끝나면 부를 함수(자유 대결 / 스토리)
let hintNow = ''; // 스테이지 도움말(첫 증강체 창에도 보여 줌)

// ── 그림: 최상위는 앞·뒤 그림, 빌런은 그림이 있으면 그것, 없으면 코드로 그린 임시 그림 ──
// 승진 단계 그림(public/battle/sprites/D1_s3_back.webp)이 있으면 그것, 없으면 원형 그림(+빛 효과)
// 몸 크기 맞춤(v0.9.98): 원형 그림은 상징물(머리 위 별·성채·가발 등)까지 그림 높이를 같게 맞춰서 상징물이 큰 갈매미는 몸이 작아 보였음.
// 눈 높이(발에서 눈까지 / 그림 높이)를 재서 보통(0.68)보다 낮은 갈매미만 그만큼 크게. 줄이지는 않음(다리가 긴 I7·I8 등은 원래 큰 것)
const BODY = { D1: 1.19, D3: 1.36, D4: 1.24, D6: 1.7, D8: 1.19, T1: 1.51, T3: 1.51, T4: 1.08, T6: 1.24, T7: 1.1, E2: 1.1, E4: 1.17, E5: 1.7, E6: 1.05, E7: 1.8, I2: 1.17, I3: 1.8, I4: 1.24, I5: 1.1, I6: 1.17 };
const bodySz = (id, stage = 0) => (stage && EVO.has(`${id}_s${stage}_front`)) ? 1 : (BODY[id] ?? 1);   // 승진 그림이 있으면 그 그림 기준(아직 없음)
const sprite = (id, side, stage = 0) => id.startsWith('V') ? villainArt(id) : `${BASE}/duel/sprites/${id}_s${stage && EVO.has(`${id}_s${stage}_${side}`) ? stage : 0}_${side}.webp${q}`;
function villainArt(id) {
  if (ART.includes(id)) return `${BASE}/duel/sprites/${id}_front.webp${q}`;
  // 블랙 갈매미맨: 그림(V17)이 있으면 거울·땅콩 단계도 그 그림에 색만 바꿔 씀, 없으면 원형(look) 그림을 검게 칠해 씀
  const vd = R.data.villains[id];
  if (vd?.tint && ART.includes('V17')) return `${BASE}/duel/sprites/V17_front.webp${q}`;
  if (vd?.look) return sprite(vd.look, 'front');
  const v = R.villains.get(id), c = { V1: '#d9805a', V2: '#5b4a78', V3: '#e98fa6', V4: '#bfe3f2', V5: '#c4573f', V6: '#8e3b46' }[id] ?? '#d9805a';
  let svg;
  const GULL = { V12: ['#f4f1ea', 'band'], V19: ['#3b3742', 'band red'], V13: ['#3b3742', 'goggle'], V14: ['#3b3742', 'squint'], V15: ['#2f2b35', 'patch medal'], V16: ['#262330', 'red'], V20: ['#f4f1ea', 'bag'] };
  if (GULL[id]) {   // 끼룩단: 갈매기(까맣게 물든 정도·소품이 다름)
    const [body, kit] = GULL[id], has = k => kit.includes(k), ln = body === '#f4f1ea' ? '#5a4636' : '#15121a';
    svg = `<ellipse cx="100" cy="130" rx="${has('medal') ? 60 : 52}" ry="${has('medal') ? 52 : 46}" fill="${body}" stroke="${ln}" stroke-width="4"/><path d="M58 120 Q30 150 52 172 L84 150Z" fill="#3a3340" stroke="${ln}" stroke-width="4"/>
      <circle cx="112" cy="78" r="34" fill="${body}" stroke="${ln}" stroke-width="4"/>
      ${has('band') ? `<path d="M80 66 Q112 34 144 66 L140 74 Q112 52 84 74Z" fill="#d63a3a" stroke="${ln}" stroke-width="3"/>` : ''}
      <path d="M70 82 L46 90 L72 96Z" fill="#f2a33a" stroke="${ln}" stroke-width="3"/>
      ${has('goggle') ? '<circle cx="104" cy="80" r="11" fill="#9fd8f2" stroke="#222" stroke-width="4"/><path d="M115 78 L146 72" stroke="#222" stroke-width="4"/>'
        : has('patch') ? '<circle cx="104" cy="80" r="10" fill="#111"/><path d="M90 66 L146 92" stroke="#111" stroke-width="3"/>'
        : has('squint') ? '<path d="M94 82 L110 82" stroke="#fff" stroke-width="4"/>'
        : `<circle cx="100" cy="82" r="5" fill="${has('red') ? '#ff3b3b' : body === '#f4f1ea' ? '#222' : '#eee'}"/><path d="M92 72 L108 78" stroke="${body === '#f4f1ea' ? '#222' : '#eee'}" stroke-width="4"/>`}
      ${has('medal') ? '<circle cx="104" cy="140" r="12" fill="#f0b93a" stroke="#8a6a1a" stroke-width="3"/><path d="M98 118 L104 128 L110 118" stroke="#d63a3a" stroke-width="4" fill="none"/>' : ''}
      ${has('bag') ? '<rect x="118" y="120" width="40" height="30" rx="5" fill="#5b8bd9" stroke="#2c4a80" stroke-width="3"/><path d="M118 124 L92 104" stroke="#2c4a80" stroke-width="3"/>' : ''}
      <path d="M86 172 L80 192 M116 172 L120 192" stroke="#f2a33a" stroke-width="7" stroke-linecap="round"/>`;
  }
  else if (['V7', 'V8', 'V9', 'V10', 'V11'].includes(id)) {   // 랍매미: 집게 달린 붉은 랍스타
    const lc = { V7: '#d9483b', V8: '#b5533c', V9: '#d9783b', V10: '#c23a3a', V11: '#8e2f3f' }[id];
    svg = `<path d="M70 70 Q40 20 20 18 M130 70 Q160 20 180 18" stroke="#5a2a22" stroke-width="3" fill="none"/>
      <ellipse cx="100" cy="125" rx="40" ry="55" fill="${lc}" stroke="#5a2a22" stroke-width="4"/>
      <path d="M72 150 L60 190 M88 160 L84 194 M112 160 L116 194 M128 150 L140 190" stroke="#5a2a22" stroke-width="6" stroke-linecap="round"/>
      <path d="M62 100 Q30 90 26 60" stroke="${lc}" stroke-width="12" fill="none"/><path d="M138 100 Q170 90 174 60" stroke="${lc}" stroke-width="12" fill="none"/>
      <path d="M8 58 Q26 20 44 52 Q30 48 26 62Z" fill="${lc}" stroke="#5a2a22" stroke-width="4"/><path d="M192 58 Q174 20 156 52 Q170 48 174 62Z" fill="${lc}" stroke="#5a2a22" stroke-width="4"/>
      <circle cx="86" cy="92" r="9" fill="#fff"/><circle cx="114" cy="92" r="9" fill="#fff"/><circle cx="88" cy="94" r="4" fill="#222"/><circle cx="112" cy="94" r="4" fill="#222"/>
      <rect x="84" y="118" width="32" height="20" rx="3" fill="#f4f1ea" stroke="#5a2a22" stroke-width="2"/>
      ${id === 'V10' ? '<path d="M68 70 L132 70 L120 50 L80 50Z" fill="#222"/>' : ''}${id === 'V9' ? '<rect x="76" y="140" width="48" height="26" rx="4" fill="#f0b93a" stroke="#5a2a22" stroke-width="2"/>' : ''}`;
  }
  else if (id === 'V4') svg = [[70, 150], [110, 150], [150, 152], [90, 115], [130, 115], [110, 82]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="27" fill="${c}" fill-opacity=".75" stroke="#6a8fa3" stroke-width="3"/><circle cx="${x - 7}" cy="${y - 4}" r="4" fill="#3a4a55"/><circle cx="${x + 7}" cy="${y - 4}" r="4" fill="#3a4a55"/><path d="M${x - 8} ${y - 24} L${x - 3} ${y - 36} L${x + 2} ${y - 25} L${x + 7} ${y - 35} L${x + 10} ${y - 23}" fill="#fff8e7" stroke="#b9a27a" stroke-width="2"/>`).join('');
  else {
    const legs = Array.from({ length: 8 }, (_, i) => { const x = 58 + i * 12, ex = 30 + i * 20; return `<path d="M${x} 120 Q${(x + ex) / 2 + (i % 2 ? 10 : -10)} 160 ${ex} 190" stroke="${c}" stroke-width="13" fill="none" stroke-linecap="round"/>`; }).join('');
    svg = `${legs}<ellipse cx="100" cy="88" rx="56" ry="52" fill="${c}" stroke="#4a2e2a" stroke-width="4"/>
      <polygon points="76,44 84,12 94,40 102,8 110,40 120,14 126,46" fill="#fff8e7" stroke="#b9a27a" stroke-width="3"/>
      <circle cx="82" cy="92" r="11" fill="#fff"/><circle cx="118" cy="92" r="11" fill="#fff"/><circle cx="84" cy="94" r="5" fill="#222"/><circle cx="116" cy="94" r="5" fill="#222"/>
      <path d="M92 112 Q100 118 108 112" stroke="#4a2e2a" stroke-width="3" fill="none"/>
      ${id === 'V2' ? '<circle cx="60" cy="130" r="6" fill="#222"/><circle cx="148" cy="138" r="5" fill="#222"/><circle cx="104" cy="150" r="4" fill="#222"/>' : ''}
      ${id === 'V3' ? '<circle cx="70" cy="70" r="6" fill="#fbd3dc"/><circle cx="130" cy="70" r="6" fill="#fbd3dc"/><circle cx="100" cy="60" r="5" fill="#fbd3dc"/>' : ''}
      ${id === 'V5' ? '<polygon points="94,124 106,124 112,160 100,172 88,160" fill="#2b4c8c" stroke="#1b2c50" stroke-width="2"/>' : ''}
      ${id === 'V6' ? '<circle cx="100" cy="128" r="9" fill="#ffe36e"/><circle cx="100" cy="128" r="18" fill="#ffe36e" fill-opacity=".3"/>' : ''}`;
  }
  void v;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">${svg}</svg>`)}`;
}

// 물리·마법: 자동(역할기·합동기)은 그 유닛의 더 높은 공격으로
const catOf = (k, st) => k.cat === 'mag' ? '마법' : k.cat === 'phys' ? '물리' : st && st.matk > st.patk ? '마법' : '물리';
const catTag = (k, st) => k.power ? `<i class="cat ${catOf(k, st) === '마법' ? 'mag' : 'phys'}">${catOf(k, st)}</i>` : '';
// 기술 설명에서 이미 따로 보여 준 「위력」·「속성 공격」 부분은 빼고
const restDesc = k => k.id.startsWith('sub-') ? '' : k.desc.replace(/^위력 \d+(으로 \d+번)?(, )?/, '');
// 능력치 표: 체력·물리공격·물리방어·마법공격·마법방어·속도(주 공격은 강조)
function statGrid(st) {
  const main = st.matk > st.patk ? 'matk' : 'patk';
  return `<div class="stats">${[['hp', '❤️', '체력'], ['patk', '⚔️', '물리공격'], ['pdef', '🛡️', '물리방어'], ['matk', '🔮', '마법공격'], ['mdef', '✨', '마법방어'], ['spd', '💨', '속도']]
    .map(([k, i, t]) => `<div class="${k === main ? 'main' : ''}"><small>${i} ${t}</small><b>${st[k]}</b></div>`).join('')}</div>`;
}
// 그림을 못 불러오면 깨진 그림 표시 대신 그냥 비워 둠
addEventListener('error', e => { if (e.target?.tagName === 'IMG') e.target.style.visibility = 'hidden'; }, true);
addEventListener('load', e => { if (e.target?.tagName === 'IMG') e.target.style.visibility = ''; }, true);
// 대사에 얼굴이 있는 마을 사람(이름 → 그림 public/battle/sprites/HALL_front.webp)
const NPC = { '마을 회관 방송': 'HALL' };
// 아이템·증강체 그림(public/battle/icons/potion.png): 없으면 글자 그림(이모지)
const ico = (id, emoji) => ICONS.includes(id) ? `<img class="ico" src="${BASE}/duel/icons/${id}.png" alt="">` : emoji;
// 장소별 움직이는 배경(그림 없이 코드로): 구름·갈매기·물 반짝임·먹물·거품·금가루·깃털·불티. 같은 장소면 늘 같은 배치(seed)
const AMB = {
  title: [['gull', 3], ['mote', 16], ['wfeather', 7]],
  harbor: [['cloud', 4], ['gull', 3], ['glint', 10]], hall: [['cloud', 4], ['gull', 2]], laundry: [['cloud', 3], ['gull', 2], ['cloth', 6]],
  ink: [['drop', 14], ['paper', 5]], deep: [['bubble', 16], ['ray', 3]], market: [['dust', 18], ['cloud', 2]],
  strait: [['cloud', 5], ['spray', 12], ['glint', 6]], sky: [['cloud', 6], ['feather2', 10]], nest: [['feather2', 8], ['ember', 14]],
};
function ambient(scene, painted = false) {   // painted: 그림이 깔린 화면 → 그림에 이미 있는 구름·물 반짝임·물보라는 빼고(얼룩처럼 보여서)
  let t = 7; for (const ch of scene || 'harbor') t = (t * 31 + ch.charCodeAt(0)) >>> 0;
  const r = () => { t = (t * 1103515245 + 12345) >>> 0; return (t >>> 8) / 16777216; };
  return `<div class="amb" aria-hidden="true">${(AMB[scene] ?? AMB.harbor).filter(([k]) => !painted || !['cloud', 'glint', 'spray'].includes(k)).flatMap(([k, n]) => Array.from({ length: n }, () =>
    `<i class="a-${k}" style="--x:${(r() * 100).toFixed(1)}%;--y:${(r() * 100).toFixed(1)}%;--s:${(0.6 + r() * 0.8).toFixed(2)};--d:${(6 + r() * 14).toFixed(1)}s;--dl:-${(r() * 20).toFixed(1)}s"></i>`)).join('')}</div>`;
}
const bgStyle = scene => BG.includes(scene) ? `background:url(${BASE}/duel/bg/${scene}.jpg${q}) center/cover` : '';
const tintOf = id => { const t = R.data.villains?.[id]?.tint; return !t ? '' : ART.includes('V17') ? (t === 'shadow' ? '' : `tint-${t}-art`) : `tint-${t}`; };   // 진짜 그림이 있으면 가볍게만
const setTint = (img, id) => { img.classList.remove('tint-shadow', 'tint-mirror', 'tint-peanut', 'tint-mirror-art', 'tint-peanut-art'); const t = tintOf(id); if (t) img.classList.add(t); };

// ── 저장: 로그인돼 있으면 계정(서버), 아니면 이 브라우저 ──
const SAVE_KEY = 'gmm-duel-story-v1';
const local = () => { try { return JSON.parse(localStorage.getItem(SAVE_KEY) || 'null'); } catch { return null; } };
function load(s = local()) {
  s = s && typeof s === 'object' ? JSON.parse(JSON.stringify(s)) : {};
  s.units ??= {}; s.team ??= []; s.cleared ??= [];
  for (const u of UNITS) s.units[u.id] ??= { level: STORY.start.level, xp: 0 };
  s.team = s.team.filter(id => UNITS.some(u => u.id === id)).slice(0, 3);
  return s;
}
let SAVE;
let saveTimer = null, saveFail = false;
const persist = () => {
  if (!ACC) { try { localStorage.setItem(SAVE_KEY, JSON.stringify(SAVE)); } catch { /* 저장 안 돼도 진행은 가능 */ } return; }
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => myApi('story', { data: { units: SAVE.units, team: SAVE.team, cleared: SAVE.cleared } }).then(() => { saveFail = false; }).catch(() => { saveFail = true; const h = document.getElementById('savewarn'); if (h) h.hidden = false; }), 400);
};
const progressed = s => !!s && (s.cleared?.length > 0 || Object.values(s.units ?? {}).some(u => u.xp > 0 || u.level > STORY.start.level));
const myRec = (id, i) => { const u = UNITS.find(x => x.id === id), s = SAVE.units[id]; return { uid: `a${i}`, id, name: u.name, tier: u.tier, level: s.level, xp: s.xp, stage: s.stage ?? 0, evoAugs: [...(s.evoAugs ?? [])], learned: [...(s.learned ?? [])], learnUsed: s.learnUsed ?? 0 }; };
const keep = r => { SAVE.units[r.id] = { level: r.level, xp: r.xp, learned: r.learned ?? [], learnUsed: r.learnUsed ?? 0, stage: r.stage ?? 0, evoAugs: r.evoAugs ?? [] }; };
// 승진(진화): 깬 부(1·2·3부) 수 = 열린 승진 단계. 표창 후보는 유닛·단계마다 항상 같음(다시 열어도 안 바뀜)
const partsCleared = () => STORY.chapters.filter(c => /^\d+$/.test(c.id) && SAVE.cleared.includes(c.stages.at(-1).id)).length;
const STARS = ['', '★', '★★', '★★★'];
function seededRand(seed) { let h = 2166136261; for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619); let t = h >>> 0;
  return () => { t = (t + 0x6d2b79f5) | 0; let x = Math.imul(t ^ (t >>> 15), t | 1); x ^= x + Math.imul(x ^ (x >>> 7), x | 61); return ((x ^ (x >>> 14)) >>> 0) / 4294967296; }; }
const promoChoices = rec => evoChoices(R, rec, seededRand(`${rec.id}:evo:${rec.stage}`));
const promoNeed = rec => { const lv = R.data.growth.evolveLevel[rec.stage]; return rec.stage >= 3 ? '' : `Lv${lv} + ${rec.stage + 1}부 완료`; };
// 시너지: 이름과, 팀에서 같은 시너지 2마리+면 생기는 합동기
const synName = id => { const k = R.unitDef(id)?.syn; return k ? R.data.synergies[k].name : ''; };
function teamCombos(ids) {
  const by = {}; for (const id of ids) { const k = R.unitDef(id)?.syn; if (k) (by[k] ??= []).push(id); }
  return Object.entries(by).filter(([, v]) => v.length >= 2).map(([k, v]) => ({ k, skill: R.data.synergies[k].skill.name, names: v.map(id => UNITS.find(u => u.id === id).name) }));
}

document.title = GAME.name;
// ⚙ 설정: 소리 켜기·끄기, 로그아웃, 계정 삭제를 한곳에(첫 화면은 오른쪽 아래, 다른 화면은 오른쪽 위에 떠 있음)
function muteBtn(show = true) {
  let m = document.getElementById('mute');
  if (!show) { m?.remove(); return; }
  if (m) return;
  m = document.createElement('button'); m.id = 'mute'; m.className = 'mute'; m.setAttribute('aria-label', '설정'); m.textContent = '⚙️';
  m.onclick = e => { e.stopPropagation(); settingsSheet(); };
  document.body.append(m);
}
function settingsSheet() {
  const prev = modal.innerHTML, wasHidden = modal.hidden, prevClass = modal.className, prevClick = modal.onclick;
  const close = () => { if (wasHidden) { modal.hidden = true; modal.innerHTML = ''; } else { modal.innerHTML = prev; modal.className = prevClass; modal.onclick = prevClick; } };
  let coupMsg = '';
  const draw = () => {
    modal.className = ''; modal.hidden = false;
    modal.innerHTML = `<div class="sheet set"><h2>⚙️ 설정<button class="xbtn" id="sX" aria-label="닫기">✕</button></h2><div class="scols"><div class="scol">
      <div class="setrow"><span>🔊 소리</span><button class="tog ${isMuted() ? '' : 'on'}" id="sSnd">${isMuted() ? '꺼짐' : '켜짐'}</button></div>
      <div class="setrow vol ${isMuted() ? 'off' : ''}"><span>🎵 배경음악</span><input type="range" min="0" max="100" step="5" value="${getVol('music')}" id="vMusic" aria-label="배경음악 음량"><b id="vMusicN">${getVol('music')}</b></div>
      <div class="setrow vol ${isMuted() ? 'off' : ''}"><span>💥 효과음</span><input type="range" min="0" max="100" step="5" value="${getVol('sfx')}" id="vSfx" aria-label="효과음 음량"><b id="vSfxN">${getVol('sfx')}</b></div></div><div class="scol">
      ${ACC ? `<div class="setrow"><span>👤 ${esc(ACC.nickname || '내 계정')}</span><button class="ghost" id="sOut">로그아웃</button></div>
      <div class="setrow coup"><span>🎟 쿠폰</span><input id="sCode" placeholder="쿠폰 코드" autocomplete="off" maxlength="60"><button class="ghost" id="sCoup">받기</button></div>
      ${coupMsg ? `<p class="coupmsg">${esc(coupMsg)}</p>` : ''}
      <details class="setdel"><summary>계정 삭제(탈퇴)</summary>
        <p class="sub">갈매미·도감·재화·아이템·탐험·스토리 진행이 <b>모두 지워지고 되돌릴 수 없어요.</b></p>
        <input id="sDelc" placeholder="확인을 위해 탈퇴 라고 적어 주세요" autocomplete="off"><button class="big wide" id="sDel" disabled>계정 삭제</button></details>` : ''}
      </div></div><button class="ghost wide" id="sClose">닫기</button></div>`;
    modal.querySelector('#sSnd').onclick = () => { toggleMute(); draw(); };
    for (const [id, k] of [['vMusic', 'music'], ['vSfx', 'sfx']]) {
      const r = modal.querySelector('#' + id), n = modal.querySelector(`#${id}N`);
      r.oninput = () => { if (isMuted()) { toggleMute(); modal.querySelectorAll('.setrow.vol').forEach(x => x.classList.remove('off')); const t = modal.querySelector('#sSnd'); t.classList.add('on'); t.textContent = '켜짐'; } setVol(k, +r.value); n.textContent = r.value; };
      if (k === 'sfx') r.onchange = () => sfx('hit');   // 손을 떼면 효과음 크기를 들려줌
    }
    modal.querySelector('#sClose').onclick = close; modal.querySelector('#sX').onclick = close;
    modal.querySelector('#sOut')?.addEventListener('click', async () => {
      if (!(await ask('로그아웃할까요?', { ok: '로그아웃' }))) return;
      await myApi('logout', {}).catch(() => {}); APP?.setToken(null); ACC = null; titleScreen(false);
    });
    const cb = modal.querySelector('#sCoup'), ci = modal.querySelector('#sCode');
    if (cb) {
      const go = async () => {
        if (!ci.value.trim()) { ci.focus(); return; }
        cb.disabled = true;
        try { coupMsg = (await myApi('coupon', { code: ci.value })).result; sfx('win'); }
        catch (e) { coupMsg = e.status === 429 ? '너무 많이 시도했어요. 잠시 뒤에 다시 해 주세요.' : e.message; }
        draw();
      };
      cb.onclick = go; ci.onkeydown = e => { if (e.key === 'Enter') go(); };
    }
    const c = modal.querySelector('#sDelc'), d = modal.querySelector('#sDel');
    if (c) {
      c.oninput = () => { d.disabled = c.value.trim() !== '탈퇴'; };
      d.onclick = async () => {
        if (!(await ask('정말 계정을 삭제할까요? 되돌릴 수 없어요.', { ok: '삭제', danger: true }))) return;
        d.disabled = true;
        try { await myApi('account/delete', { confirm: '탈퇴' }); APP?.setToken(null); ACC = null; await tell('계정을 삭제했어요. 그동안 고마웠어요.'); titleScreen(false); }
        catch (e) { await tell(e.message); d.disabled = false; }
      };
    }
  };
  modal.onclick = null; draw();
}
document.addEventListener('click', e => { if (e.target.closest && e.target.closest('button:not(#mute)')) sfx('tap'); }, true);
async function boot() {
  try {
    // 앱: 로그인한 계정으로 게임 열쇠를 받음(로그인 전이면 로그인 화면으로)
    if (!K && APP) {
      if (!APP.token()) { titleScreen(false); return; }
      try { K = (await myApi('duel-key')).k; q = `?k=${encodeURIComponent(K)}`; }
      catch (e) { if (e.status === 401) { APP.setToken(null); location.href = 'my.html?next=game'; return; } throw e; }
    }
    const r = await fetch(`${BASE}/duel/data.json${q}`);
    const j = await r.json();
    if (!r.ok) throw new Error(j.error || `불러오기 실패(${r.status})`);
    R = rulesFrom(j.rules); STORY = j.story; ART = j.villainArt ?? []; NAMES = j.names ?? {}; BG = j.bgArt ?? []; ICONS = j.iconArt ?? []; EVO = new Set(j.stageArt ?? []);
    UNITS = j.units.map(u => ({ ...u, def: R.units.get(u.id) }));
    // 로그인돼 있으면 계정 진행을 불러옴. 계정에 아직 없고 이 브라우저에 진행이 있으면 계정으로 옮김
    try { ACC = await myApi('story'); } catch { ACC = null; }
    if (ACC) {
      if (ACC.data) SAVE = load(ACC.data);
      else { SAVE = load(progressed(local()) ? local() : null); if (progressed(local())) persist(); }
      if (SAVE.team.length !== 3 && ACC.battleTeam.length === 3) { SAVE.team = [...ACC.battleTeam]; persist(); }
    } else SAVE = load();
    titleScreen(!!ACC);
  } catch (e) {
    app.innerHTML = `<header><h1>수비대 대결</h1></header><div class="err">${esc(e.message)}</div>`;
  }
}

// 첫 화면: 대표 그림 + 큰 게임 이름 + 버튼 하나(로그인 전 「로그인」, 로그인 뒤 「게임 시작」 → 로비)
function titleScreen(loggedIn) {
  modal.hidden = true; muteBtn(false); if (R) bgm('title');
  const art = APP ? 'title.jpg' : BG.includes('key') ? `${BASE}/duel/bg/key.jpg${q}` : '';
  const login = APP ? 'my.html?next=game' : '/my?next=duel&g=1';
  // 배경: 그림이 천천히 움직이고(카메라) 빛 줄기가 지나감 + 반짝이·흰 깃털·갈매기
  app.innerHTML = `<div class="title"><div class="tbg" style="${art ? `background-image:url(${art})` : ''}"></div><div class="tray"></div>${ambient('title', true)}
    <div class="tlogo"><b><span class="stroke">${esc(GAME.name)}</span><span class="fill">${esc(GAME.name)}</span></b><small class="tsub">— ${esc(GAME.sub)} —</small></div>
    ${loggedIn ? '<button class="tgo" id="tgo">▶ 게임 시작</button>' : `<a class="tgo" href="${login}">로그인</a>`}
    <div class="tcorner">${loggedIn && ACC ? `<span class="tacc">${esc(ACC.nickname || '내 계정')}</span>` : ''}<button class="tset" id="tset" aria-label="설정">⚙️</button></div></div>`;
  app.querySelector('#tgo')?.addEventListener('click', menu);
  app.querySelector('#tset').addEventListener('click', settingsSheet);
}
function menu() {
  try { sessionStorage.setItem('gmm-k', K); } catch { /* 무시 */ }
  modal.hidden = true; bgm('title'); muteBtn();
  // 로비(v1.0, 한국 수집형 게임 로비 구조): 위 줄 프로필·재화 / 왼쪽 작은 입구(스토리·자유 대결) / 가운데 대표 갈매미(누르면 한마디, 팀 셋을 돌아가며) / 오른쪽 이야기 칸·큰 이어하기 / 아래 메뉴. 할 일이 있으면 빨간 점
  const my = h => APP ? `my.html#${h}` : `/my?g=1#${h}`;
  const bg = BG.includes('harbor') ? `background-image:url(${BASE}/duel/bg/harbor.jpg${q})` : '';
  const team = SAVE.team.length === 3 ? SAVE.team : [];
  const lvl = team.length ? Math.round(team.reduce((a, id) => a + (SAVE.units[id]?.level ?? 1), 0) / team.length) : 0;
  const storyDot = team.some(id => { const r = myRec(id, 0); return learnChances(R, r) > 0 || canEvolve(R, r, partsCleared()); });
  app.innerHTML = `<div class="ts lobby" style="${bg};${BG.includes('key') ? `--key:url(${BASE}/duel/bg/key.jpg${q})` : ''}"><div class="lbg" aria-hidden="true"></div>${ambient('harbor', true)}
    <header class="ltop"><div class="lprof"><i>${ico('u-profile', '🪪')}</i><span><b>${esc(ACC?.nickname || '수비조장')}</b><small>${lvl ? `수비대 Lv${lvl} · 실적 ${SAVE.cleared.length}/${stages().length}` : '아직 팀이 없어요'}</small></span></div><div class="lcur" id="lcur"></div></header>
    <aside class="lside">${ACC ? `<button id="td"><i>${ico('u-attend', '📅')}</i><span>출석체크</span></button>` : ''}<button id="st"><i>${ico('u-story', '📖')}</i><span>스토리</span>${storyDot ? '<em class="dot"></em>' : ''}</button></aside>
    <div class="lhero" id="lhero">${team.length ? `<div class="lplat"></div><img id="lheroImg" alt=""><div class="lbub" id="lbub" hidden></div><div class="lname" id="lname"></div>` : '<button class="lnoteam" id="lnoteam">🐣 팀 3마리를 정하면<br>여기에 대표 갈매미가 서요</button>'}</div>
    <div class="lstory">${nowCard()}</div>
    <nav class="dock">${[['draw', '🥚', '뽑기'], ['craft', '🧩', '조합'], ['units', '🪺', '갈매미'], ['party', '🧭', '탐험대'], ['shop', '🛒', '상점'], ['account', '👤', '계정']].map(([h, i, t]) => `<a href="${my(h)}" data-h="${h}"><i class="${ICONS.includes(`m-${h}`) ? 'art' : ''}">${ico(`m-${h}`, i)}</i><span>${t}</span></a>`).join('')}</nav></div>`;
  app.querySelector('#st').onclick = storyScreen;
  app.querySelector('#td')?.addEventListener('click', todaySheet);
  if (ACC) myApi('daily').then(d => { if (!d.attend.today || d.missions.some(m => !m.done && m.value >= m.goal)) app.querySelector('#td')?.insertAdjacentHTML('beforeend', '<em class="dot"></em>'); }).catch(() => {});
  app.querySelectorAll('.dock a[data-h]').forEach(a => a.onclick = e => { e.preventDefault(); openMy(a.dataset.h); });
  app.querySelector('#lnoteam')?.addEventListener('click', () => pickScreen('story'));
  app.querySelector('#go')?.addEventListener('click', () => { sfx('tap'); modeScreen(); });
  // 대표 갈매미: 누르면 한마디, 다시 누르면 다음 팀원
  if (team.length) {
    let at = 0, hide = null;
    const show = (talk) => {
      const id = team[at], u = UNITS.find(x => x.id === id), st = SAVE.units[id]?.stage ?? 0, img = app.querySelector('#lheroImg');
      img.src = sprite(id, 'front', st); img.style.setProperty('--sz', bodySz(id, st)); img.className = st ? `st-${st}` : '';
      app.querySelector('#lname').innerHTML = `<b>${esc(u.name)}</b><small>Lv${SAVE.units[id]?.level ?? 1}${st ? ` ${STARS[st]}` : ''} · ${chip(u.def.attr)}</small>`;
      const bub = app.querySelector('#lbub');
      if (talk && (u.quote || u.tagline)) { bub.textContent = u.quote || u.tagline; bub.hidden = false; bub.style.top = `${Math.max(0, img.offsetTop - bub.offsetHeight * 0.55)}px`; bub.style.left = `${img.offsetLeft + img.offsetWidth * 0.55}px`; bub.style.right = 'auto'; bub.classList.remove('pop');   // 말풍선은 머리 높이 옆에(큰 화면에서 붕 뜨지 않게)
         void bub.offsetWidth; bub.classList.add('pop'); clearTimeout(hide); hide = setTimeout(() => { bub.hidden = true; }, 3200); }
    };
    show(false);
    app.querySelector('#lhero').onclick = () => { if (!app.querySelector('#lbub').hidden) at = (at + 1) % team.length; show(true); sfx('tap'); };
  }
  // 재화·빨간 점(로그인했으면 서버에서): 뽑기권 받을 수 있음 · 탐험 끝남
  if (ACC) myApi('me').then(me => {
    const cur = app.querySelector('#lcur'); if (!cur) return;
    cur.innerHTML = [[ico('u-snack', '🍤'), me.snack, '새우깡'], [ico('u-peanut', '🥜'), me.peanut, '땅콩'], [ico('u-ticket', '🎫'), me.credits, '뽑기권']].map(([i, n, t]) => `<span title="${t}">${i}<b>${Number(n).toLocaleString('ko-KR')}</b></span>`).join('');
    const now = Date.now(), dot = h => app.querySelector(`.dock a[data-h="${h}"]`)?.insertAdjacentHTML('beforeend', '<em class="dot"></em>');
    if (new Date(me.claimReadyAt).getTime() <= now) dot('draw');
    if (me.trip && new Date(me.trip.endsAt).getTime() <= now) dot('party');
  }).catch(() => {});
}
// 스토리 이어하기(다음 스테이지 바로, 팀이 없으면 편성부터)
function continueStory() {
  const st = nextStage();
  if (!st) return storyScreen();
  const go = () => (unlocked(st.id) ? playStage(st) : storyScreen());   // 승진이 필요한 부면 목록으로
  if (SAVE.team.length !== 3) return pickScreen('story', go);
  go();
}
// 플레이 → 모드 고르기(v0.11.0): 왼쪽 캠페인(스토리·모험), 오른쪽 PvP(일반·랭크), 아래 돌아가기
function modeScreen() {
  modal.hidden = true; bgm('title'); muteBtn();
  const st = nextStage(), bg = BG.includes('harbor') ? `background-image:url(${BASE}/duel/bg/harbor.jpg${q})` : '';
  app.innerHTML = `<div class="modes" style="${bg}"><div class="lbg" aria-hidden="true"></div>${ambient('harbor', true)}
    <h1 class="mtitle">게임 모드</h1>
    <div class="mrow">
      <section class="mcard camp"><div class="mhead"><i>${ico('mode-camp', '🗺️')}</i><span><b>캠페인</b><small>이야기를 따라 항구를 지키고, 상위 갈매미를 찾아 모험</small></span></div>
        <button class="mbtn main" id="mStory"><i>${ico('u-story', '📖')}</i><span><b>스토리</b><small>${st ? `${esc(st.id)} ${esc(st.title)}` : '모든 이야기 완료 · 다시 보기'}</small></span></button>
        <button class="mbtn" id="mAdv" disabled><i>${ico('mode-adv', '🧭')}</i><span><b>모험 · 상위 갈매미 포획</b><small>준비 중 · 다음 업데이트</small></span></button>
        <button class="mlink" id="mList">스토리 목록</button></section>
      <section class="mcard pvp"><div class="mhead"><i>${ico('mode-pvp', '⚔️')}</i><span><b>PvP</b><small>다른 수비대의 「전투 편성」 팀과 대결 · 모두 같은 레벨</small></span></div>
        <div class="mrank" id="mRank">${ACC ? '랭크 불러오는 중…' : '로그인하면 PvP를 할 수 있어요'}</div>
        <button class="mbtn" id="mNormal" ${ACC ? '' : 'disabled'}><i>${ico('mode-normal', '🤝')}</i><span><b>일반게임</b><small>점수 없이 가볍게</small></span></button>
        <button class="mbtn" id="mRankGo" ${ACC ? '' : 'disabled'}><i>${ico('mode-rank', '🏆')}</i><span><b>랭크게임</b><small id="mRankSub">이기면 점수가 올라요</small></span></button>
        <div class="mlinks"><button class="mlink" id="mTop" ${ACC ? '' : 'disabled'}>순위표</button><button class="mlink" id="mFree">연습(자유 대결)</button></div></section>
    </div>
    <button class="mback" id="mBack">‹ 돌아가기</button></div>`;
  app.querySelector('#mStory').onclick = continueStory;
  app.querySelector('#mList').onclick = storyScreen;
  app.querySelector('#mFree').onclick = () => pickScreen('free');
  app.querySelector('#mBack').onclick = menu;
  app.querySelector('#mNormal').onclick = () => pvpStart('normal');
  app.querySelector('#mRankGo').onclick = () => pvpStart('rank');
  app.querySelector('#mTop').onclick = pvpTop;
  if (ACC) myApi('pvp').then(d => {
    const el = app.querySelector('#mRank'); if (!el) return;
    el.innerHTML = `${tierIco(d, d.me.tier)}<b class="tier">${esc(d.me.tier)}</b><span>${d.me.point.toLocaleString('ko-KR')}점 · ${d.me.wins}승 ${d.me.losses}패${d.me.rank ? ` · ${d.me.rank}위` : ''}</span>`;
    app.querySelector('#mRankSub').textContent = `이기면 +${d.win} · 지면 -${d.lose} · Lv${d.level}로 맞춤`;
    if (!d.team) el.insertAdjacentHTML('beforeend', '<em class="mwarn">「전투 편성」에서 3마리를 먼저 정해요</em>');
  }).catch(() => { const el = app.querySelector('#mRank'); if (el) el.textContent = '랭크 정보를 불러오지 못했어요'; });
}
// 티어 배지 그림(icons/tier-1.png ~ tier-6.png, 낮은 티어부터). 그림이 없으면 빈칸
const tierIco = (d, name) => { const n = (d.tiers ?? []).indexOf(name) + 1; return n ? `<span class="tico">${ico(`tier-${n}`, '')}</span>` : ''; };
// PvP 순위표(상위 20)
async function pvpTop() {
  modal.className = ''; modal.hidden = false; modal.innerHTML = '<div class="sheet today"><p class="sub">불러오는 중…</p></div>';
  let d; try { d = await myApi('pvp'); } catch (e) { modal.hidden = true; return tell(e.message); }
  modal.innerHTML = `<div class="sheet today ptop"><h2>🏆 랭크 순위<button class="xbtn" id="ptX" aria-label="닫기">✕</button></h2>
    <p class="sub">내 랭크: ${esc(d.me.tier)} · ${d.me.point}점${d.me.rank ? ` · ${d.me.rank}위` : ' · 아직 랭크게임을 안 했어요'}</p>
    <div class="ptl">${d.top.length ? d.top.map(r => `<div class="ptr ${r.me ? 'me' : ''}"><em>${r.rank}</em><b>${esc(r.name)}</b><span>${tierIco(d, r.tier)}${esc(r.tier)}</span><small>${r.point}점 · ${r.wins}승 ${r.losses}패</small></div>`).join('') : '<p class="sub">아직 랭크게임을 한 사람이 없어요. 첫 번째가 되어 보세요!</p>'}</div></div>`;
  modal.querySelector('#ptX').onclick = () => { modal.hidden = true; };
  modal.onclick = e => { if (e.target === modal) modal.hidden = true; };
}
// PvP 대전: 서버가 상대·시드·양쪽 팀(같은 레벨)을 정함 → 내가 고른 것을 기록 → 끝나면 서버가 다시 돌려 승패 확인
let pvp = null;   // { match, mode, log }
async function pvpStart(mode) {
  let m;
  try { m = (await myApi('pvp/match', { mode })).result; }
  catch (e) {
    if (/전투 편성/.test(e.message)) { if (await ask(`${e.message}\n지금 편성할까요?`, { ok: '전투 편성으로' })) openMy('battle'); return; }
    return tell(e.message);
  }
  // 상대 소개(VS)
  modal.className = ''; modal.hidden = false;
  const row = recs => recs.map(r => `<img src="${sprite(r.id, 'front', 0)}" class="${tintOf(r.id)}" alt="" title="${esc(r.name)}">`).join('');
  modal.innerHTML = `<div class="sheet pvs"><small class="pvs-mode">${mode === 'rank' ? '🏆 랭크게임' : '🤝 일반게임'} · 모두 Lv${m.mine[0].level}</small>
    <div class="pvs-row"><div class="pvs-side me"><b>${esc(ACC?.nickname || '내 수비대')}</b><div class="pvs-team">${row(m.mine)}</div></div><em class="pvs-vs">VS</em>
    <div class="pvs-side foe"><b>${esc(m.foeName)}</b>${m.foeTier ? `<small>${esc(m.foeTier)}</small>` : m.bot ? '<small>상대가 없어 연습 팀과 붙어요</small>' : ''}<div class="pvs-team">${row(m.foe)}</div></div></div>
    <button class="big wide" id="pvsGo">대전 시작</button>${mode === 'rank' ? '<p class="sub">랭크는 중간에 나가면 패배로 처리돼요.</p>' : ''}</div>`;
  modal.querySelector('#pvsGo').onclick = () => {
    modal.hidden = true; sceneNow = 'harbor'; fightId = null;
    startBattle(m.mine, m.foe, won => {
      const sent = pvp; pvp = null;
      myApi('pvp/result', { match: sent.match, log: sent.log }).then(r => {
        const t = r.result, el = document.querySelector('#panel .sheet .sub');
        if (el) el.textContent = t.mode === 'rank' ? `랭크 ${t.change >= 0 ? '+' : ''}${t.change}점 → ${t.point}점 (${t.tier}) · ${t.wins}승 ${t.losses}패` : '일반게임은 점수가 없어요. 랭크게임에서 실력을 겨뤄 봐요!';
      }).catch(e => { const el = document.querySelector('#panel .sheet .sub'); if (el) el.textContent = `결과를 확인하지 못했어요: ${e.message}`; });
      return { text: mode === 'rank' ? '결과 확인 중…' : '결과 확인 중…', buttons: [['한 판 더', () => pvpStart(mode)], ['모드 고르기로', modeScreen]] };
    }, '', { retry: () => pvpStart(mode), back: modeScreen }, m.seed);
    pvp = { match: m.match, mode, log: [] };
  };
}
// 로비 「출석체크」: 출석(하루 한 번 뽑기권, 날·주가 갈수록 커짐) + 오늘의 미션(카톡과 같은 진행, 채우면 바로 지급)
async function todaySheet() {
  modal.className = ''; modal.hidden = false; modal.innerHTML = '<div class="sheet today"><p class="sub">불러오는 중…</p></div>';
  let d; try { d = await myApi('daily'); } catch (e) { modal.innerHTML = `<div class="sheet today"><p class="sub">${esc(e.message)}</p><button class="ghost wide" id="tdX">닫기</button></div>`; modal.querySelector('#tdX').onclick = () => { modal.hidden = true; }; return; }
  const a = d.attend, got = a.today ? a.dayInWeek : a.dayInWeek - 1;   // 이번 주에 이미 받은 칸 수
  const done = d.missions.filter(m => m.done).length;
  modal.innerHTML = `<div class="sheet today"><h2>📅 출석체크<button class="xbtn" id="tdX" aria-label="닫기">✕</button></h2><div class="scols"><div class="scol">
      <div class="att"><b>${a.week}주째${a.streak ? ` · ${a.streak}일 연속` : ''}</b><small class="att-note">날이 갈수록, 주가 갈수록 보상이 커져요 · 하루 빠지면 1일째부터</small>
        <div class="att-week">${a.rewards.map((t, k) => { const n = k + 1, st = n <= got ? 'on' : n === got + 1 && !a.today ? 'now' : ''; return `<div class="att-d ${st} ${n === 7 ? 'big' : ''}"><small>${n}일</small><span>${n <= got ? '✅' : n === 7 ? '🎁' : '🎟'}</span><em>${t}</em></div>`; }).join('')}</div>
        <button class="big wide" id="tdAtt" ${a.today ? 'disabled' : ''}>${a.today ? '오늘 출석 완료 · 내일 또 와요' : `출석하고 🎟 ${a.next}장 받기`}</button></div></div>
    <div class="scol"><div class="msn"><b>오늘의 미션 ${done}/${d.missions.length}</b><small>채우는 순간 바로 받아요 · 밤 12시 초기화</small>
      ${d.missions.map(m => `<div class="msn-r ${m.done ? 'ok' : ''}"><span>${m.done ? '✅' : '🎯'}</span><div><b>${esc(m.label)}</b><i><i style="width:${Math.round(m.value / m.goal * 100)}%"></i></i></div><em>${m.goal > 1 ? `${m.value}/${m.goal}` : m.done ? '완료' : ''} · 🎟 ${m.tickets}</em></div>`).join('')}</div></div></div></div>`;
  modal.querySelector('#tdX').onclick = () => { modal.hidden = true; menu(); };
  modal.querySelector('#tdAtt').onclick = async () => {
    try { const r = await myApi('attend', {}); sfx('win'); await tell(`출석 완료! ${r.result.streak}일 연속\n🎟 뽑기권 ${r.result.tickets}장${r.result.dayInWeek === 7 ? ' · 이번 주 큰 보상!' : ''}`); todaySheet(); }
    catch (e) { await tell(e.message); }
  };
}
// 「내 갈매미」(뽑기·조합·탐험·상점…)를 페이지 이동 없이 게임 안 창으로(로비는 뒤에 그대로, 닫으면 재화·빨간 점 새로)
function openMy(h) {
  document.querySelector('.myov')?.remove();
  const url = APP ? `my.html#${h}` : `/my?g=1#${h}`;
  const ov = document.createElement('div'); ov.className = 'myov';
  ov.innerHTML = `<div class="myov-load">불러오는 중…</div><iframe src="${url}" title="내 갈매미"></iframe>`;
  const fr = ov.querySelector('iframe');
  fr.onload = () => ov.classList.add('ready');
  const onMsg = e => { if (e.source === fr.contentWindow && e.data?.gmm === 'close') { removeEventListener('message', onMsg); ov.classList.add('out'); setTimeout(() => { ov.remove(); menu(); }, 180); } };
  addEventListener('message', onMsg);
  document.body.appendChild(ov); sfx('tap'); muteBtn(false);
}
// 다음에 할 스테이지: 열린 것 중 아직 안 깬 첫 스테이지(없으면 잠긴 다음 스테이지, 다 깼으면 없음)
function nextStage() {
  const all = stages();
  return all.find(s => unlocked(s.id) && !SAVE.cleared.includes(s.id)) ?? all.find(s => !SAVE.cleared.includes(s.id)) ?? null;
}
// 첫 화면 내 수비대(스토리 팀 셋, 승진 그림 반영). 팀이 없으면 비워 둠
function squad() {
  const team = SAVE.team.length === 3 ? SAVE.team : [];
  if (!team.length) return '<div class="squad empty"><span>스토리를 시작하면 내 수비대가 여기 서요</span></div>';
  return `<div class="squad">${team.map((id, i) => `<img src="${sprite(id, 'front', SAVE.units[id]?.stage ?? 0)}" style="animation-delay:${i * .4}s" alt="">`).join('')}</div>`;
}
// 가로 로비: 이야기 칸 양옆에 내 수비대(왼쪽 선두, 오른쪽 둘)
function sideSquad() {
  const team = SAVE.team.length === 3 ? SAVE.team : [];
  if (!team.length) return '';
  const im = (id, i) => `<img src="${sprite(id, 'front', SAVE.units[id]?.stage ?? 0)}" class="${i ? '' : 'flip'}" style="--sz:${bodySz(id, SAVE.units[id]?.stage ?? 0)};animation-delay:${i * .4}s" alt="">`;
  return `<div class="sqL">${im(team[0], 0)}</div><div class="sqR">${im(team[1], 1)}${im(team[2], 2)}</div>`;
}
// 지난 이야기 한 줄: 마지막으로 깬 스테이지의 끝 해설(없으면 다음 이야기의 첫 해설·장 소개)
function recap() {
  const all = stages(), last = [...all].reverse().find(s => SAVE.cleared.includes(s.id)), next = nextStage();
  const say = l => (l?.say ?? '').replace(/^\(.*?\)\s*/, '');
  if (last) { const n = [...(last.after ?? [])].reverse().find(l => l.who === 'N') ?? (last.after ?? []).at(-1); return n ? `지난 이야기 · ${say(n)}` : ''; }
  if (next) return next.chapter.intro || say((next.before ?? []).find(l => l.who === 'N'));
  return '';
}
// 첫 화면 「지금 이야기」: 부·스테이지·진행률 한 줄 + 큰 이어하기 버튼
function nowCard() {
  const all = stages(), st = nextStage(), done = SAVE.cleared.length, pct = Math.round(done / all.length * 100);
  const play = '<button class="play" id="go"><span>▶ 플레이</span></button>';   // 플레이 → 모드 고르기(캠페인 / PvP)
  if (!st) return `<div class="now"><small>모든 이야기를 끝냈어요</small><b>🎖 수비대장 취임</b><div class="prog"><i style="width:100%"></i><em>${done}/${all.length}</em></div></div>${play}`;
  const c = st.chapter;
  const lock = !unlocked(st.id) && c.needStage ? `<p class="need">🔒 팀 전원 ${c.needStage}단계 승진하면 열려요 (${STARS[c.needStage]})</p>` : '';
  const rc = recap();
  return `<div class="now"><small>${esc(c.title)}</small><b>${esc(st.id)} ${esc(st.title)}</b>${rc ? `<p class="recap">${esc(rc)}</p>` : ''}${lock}<div class="prog"><i style="width:${pct}%"></i><em>${done}/${all.length}</em></div></div>
    ${play}`;
}

// ── 편성(자유 대결: 레벨 선택 / 스토리: 저장된 레벨) ──
function pickScreen(mode, then) {   // then: 스토리 팀을 정한 뒤 바로 할 일(이어하기)
  modal.hidden = true; bgm('title');
  const story = mode === 'story';
  let picked = story ? [...SAVE.team] : freePick;
  app.innerHTML = `<header><button class="back" id="bk">‹ 뒤로</button><h1>${story ? '스토리 팀 편성' : '자유 대결'}</h1></header>
  <p class="hint">갈매미 3마리를 고르세요. 처음 고른 갈매미가 먼저 나가요.${story ? ' 시험판이라 최상위 33종을 모두 쓸 수 있어요(실제 게임은 가진 갈매미만).' : ' 상대는 같은 레벨의 무작위 3마리예요.'}</p>
  ${story ? '' : `<div class="bar"><span class="role">레벨</span><div class="seg" id="lv">${[10, 30, 50].map(l => `<button data-l="${l}" class="${l === freeLevel ? 'on' : ''}">${l}</button>`).join('')}</div></div>`}
  ${story && ACC ? `<p class="hint" style="margin-top:-6px"><img class="gi" src="/gull.png" alt=""> 표시는 내가 가진 갈매미예요. 셋 다 가진 갈매미로 고르면 「내 갈매미」 전투 편성도 같이 바뀌어요.${ACC.battleTeam.length === 3 ? ' <button class="ghost" id="mine" style="display:inline-block;padding:4px 10px">내 전투 편성 불러오기</button>' : ''}</p>` : ''}
  <div class="grid">${(story && ACC ? [...UNITS].sort((x, y) => ACC.owned.includes(y.id) - ACC.owned.includes(x.id)) : UNITS).map(u => `<button class="card" data-id="${u.id}"><img loading="lazy" src="${BASE}/thumbs/${u.id}.jpg" alt=""><b>${story && ACC?.owned.includes(u.id) ? '<img class="gi" src="/gull.png" alt=""> ' : ''}${esc(u.name)}</b>${chip(u.def.attr)}<span class="role">${ROLE[u.def.role]}${story ? ` · Lv${SAVE.units[u.id].level}` : ''}</span><span class="syn">${esc(synName(u.id))}</span></button>`).join('')}</div>
  <div class="foot"><div class="combo" id="combo"></div><div class="in"><button class="ghost" id="rnd">무작위</button><button class="big" id="go">${story ? '이 팀으로' : '대결 시작'}</button></div></div>`;
  const paint = () => {
    app.querySelectorAll('.card').forEach(c => {
      const i = picked.indexOf(c.dataset.id); c.classList.toggle('on', i >= 0);
      c.querySelector('.n')?.remove(); if (i >= 0) c.insertAdjacentHTML('beforeend', `<span class="n">${i + 1}</span>`);
    });
    app.querySelector('#go').disabled = picked.length !== 3;
    const c = teamCombos(picked), box = app.querySelector('#combo');
    box.innerHTML = c.length ? c.map(x => `🤝 합동기 「${esc(x.skill)}」 · ${x.names.map(esc).join(' + ')}`).join('<br>') : '같은 시너지 2마리를 넣으면 합동기가 생겨요';
  };
  app.querySelectorAll('.card').forEach(c => c.onclick = () => {
    const id = c.dataset.id, i = picked.indexOf(id);
    if (i >= 0) picked.splice(i, 1); else if (picked.length < 3) picked.push(id);
    paint();
  });
  app.querySelectorAll('#lv button').forEach(x => x.onclick = () => { freeLevel = +x.dataset.l; app.querySelectorAll('#lv button').forEach(y => y.classList.toggle('on', y === x)); });
  app.querySelector('#rnd').onclick = () => { picked.splice(0, 3, ...shuffle(UNITS.map(u => u.id)).slice(0, 3)); paint(); };
  app.querySelector('#mine')?.addEventListener('click', () => { picked.splice(0, 3, ...ACC.battleTeam); paint(); });
  app.querySelector('#bk').onclick = story ? storyScreen : menu;
  app.querySelector('#go').onclick = () => {
    if (story) {
      SAVE.team = [...picked]; persist();
      if (ACC && picked.every(id => ACC.owned.includes(id)) && picked.join() !== ACC.battleTeam.join())
        myApi('battle-team', { ids: picked }).then(r => { ACC.battleTeam = r.battleTeam; }).catch(() => {});
      then ? then() : storyScreen();
    }
    else { freePick = picked; freeBattle(); }
  };
  paint();
}

function freeBattle() {
  sceneNow = 'harbor'; fightId = null;
  const rec = (id, i, side) => {
    const u = UNITS.find(x => x.id === id), r = { uid: `${side}${i}`, id, name: u.name, tier: u.tier, level: freeLevel, xp: 0, stage: 0, evoAugs: [] };
    while (learnChances(R, r) > 0) { const o = learnOffers(R, r); learnSkill(R, r, o.length ? o[Math.floor(Math.random() * o.length)].id : null, slotSkills(R, r)[0]?.id); }   // 그 레벨까지 배울 기술을 무작위로
    return r;
  };
  const foe = shuffle(UNITS.map(u => u.id)).slice(0, 3);
  startBattle(freePick.map((id, i) => rec(id, i, 'a')), foe.map((id, i) => rec(id, i, 'b')), won => {
    const xp = battleXp(R, freeLevel, won);
    return { text: `실제 게임이라면 출전한 갈매미마다 경험치 ${xp}을 받아요(자유 대결은 저장하지 않아요).`, buttons: [['같은 팀으로 다시', freeBattle], ['편성으로', () => pickScreen('free')]] };
  }, '', { retry: freeBattle, back: () => pickScreen('free') });
}

// ── 스토리 ──
const stages = () => STORY.chapters.flatMap(c => c.stages.map(s => ({ ...s, chapter: c })));
const teamStageOk = need => !need || (SAVE.team.length === 3 && SAVE.team.every(id => (SAVE.units[id].stage ?? 0) >= need));
const unlocked = id => { const all = stages(), i = all.findIndex(s => s.id === id); return (i === 0 || SAVE.cleared.includes(all[i - 1].id)) && teamStageOk(all[i].chapter.needStage); };

function storyScreen() {
  modal.hidden = true; bgm('title');
  const team = SAVE.team.map(id => UNITS.find(u => u.id === id));
  app.innerHTML = `<header><button class="back" id="bk">‹ 뒤로</button><h1>스토리</h1><small>${SAVE.cleared.length}/${stages().length} 실적</small></header>
  <section class="teambar">${team.length === 3 ? team.map(u => { const s = SAVE.units[u.id], need = xpToNext(R, s.level);
      const r = myRec(u.id, 0), n = learnChances(R, r), up = canEvolve(R, r, partsCleared());
      return `<button class="mini" data-u="${u.id}"><img src="${sprite(u.id, 'front', r.stage)}" class="st-${r.stage}" alt=""><b>${esc(u.name)}</b><span>Lv${s.level} ${chip(u.def.attr)}${r.stage ? `<em class="stars">${STARS[r.stage]}</em>` : ''}</span><i class="xp"><i style="width:${Math.min(100, s.xp / need * 100)}%"></i></i>${up ? '<em class="learn promo">⬆ 승진 가능</em>' : n ? `<em class="learn">✨ 기술 배우기 ${n}</em>` : '<small>기술·승진 보기</small>'}</button>`; }).join('')
      + (team.length === 3 ? `<p class="combo">${teamCombos(SAVE.team).map(x => `🤝 합동기 「${esc(x.skill)}」`).join(' · ') || '합동기 없음(같은 시너지 2마리면 생겨요)'}</p>` : '')
    : '<p class="hint" style="margin:0">먼저 팀 3마리를 정해 주세요.</p>'}<button class="ghost" id="ed">${team.length === 3 ? '편성 바꾸기' : '팀 정하기'}</button></section>
  ${STORY.chapters.map(c => { const sc = c.stages[0].scene || 'harbor', cl = c.stages.filter(s => SAVE.cleared.includes(s.id)).length, open = c.stages.some(s => unlocked(s.id)), all = cl === c.stages.length;
    return `<section class="chap"><div class="chead sc-${sc}" style="${bgStyle(sc)}"><div class="in"><h2>${esc(c.title)}</h2><span class="pn">${all ? '✓ 완료' : `${cl}/${c.stages.length}`}</span>${c.intro ? `<p>${esc(c.intro)}</p>` : ''}${c.needStage ? `<p class="need ${teamStageOk(c.needStage) ? 'ok' : ''}">${teamStageOk(c.needStage) ? '✓' : '🔒'} 팀 전원 ${c.needStage}단계 승진 필요 (${STARS[c.needStage]})</p>` : ''}</div></div>
    <details ${open && !all ? 'open' : ''}><summary>${open ? '스테이지 보기' : '스테이지 미리 보기'} (${c.stages.length})</summary>
    ${c.stages.map(s => { const done = SAVE.cleared.includes(s.id), open = unlocked(s.id);
      const boss = s.enemy.some(e => R.villains.get(e.id).boss);
      return `<button class="stage ${done ? 'done' : ''}" data-s="${s.id}" ${open && team.length === 3 ? '' : 'disabled'}><span class="no">${s.id}</span><span class="tt">${esc(s.title)}${boss ? ' <em>보스</em>' : ''}<small>${s.enemy.map(e => `${esc(R.villains.get(e.id).name)} Lv${e.level}`).join(' · ')}</small></span><span class="mk">${done ? '✓' : open ? '도전' : '잠김'}</span></button>`; }).join('')}</details></section>`; }).join('')}
  <p class="hint">진 대결도 경험치를 조금 받아요. 깬 스테이지는 다시 도전해 레벨을 올릴 수 있어요.</p>
  <p class="hint" id="savewarn" style="color:#ff8a7a" ${saveFail ? '' : 'hidden'}>⚠ 계정에 저장하지 못했어요. 인터넷이 연결되면 다음 진행 때 다시 저장해요.</p>
  <div style="padding:0 16px 24px"><button class="ghost" id="reset" style="width:100%">스토리 진행 처음부터(${ACC ? '계정' : '이 브라우저'})</button></div>`;
  app.querySelector('#bk').onclick = menu;
  app.querySelector('#ed').onclick = () => pickScreen('story');
  app.querySelectorAll('[data-u]').forEach(x => x.onclick = () => skillSheet(x.dataset.u));
  app.querySelector('#reset').onclick = async () => {
    if (!(await ask('레벨·경험치·배운 기술·승진·깬 스테이지를 모두 지울까요?', { ok: '처음부터', danger: true }))) return;
    if (ACC) { try { await myApi('story', { data: null }); } catch (e) { await tell(e.message); return; } SAVE = load(null); }
    else { try { localStorage.removeItem(SAVE_KEY); } catch { /* 무시 */ } SAVE = load(); }
    storyScreen();
  };
  app.querySelectorAll('[data-s]').forEach(x => x.onclick = () => playStage(stages().find(s => s.id === x.dataset.s)));
}

// 기술 창: 지금 기술(기본 4개 + 배운 기술) 보기, 배울 기회가 있으면 후보 3개 중 1개(칸이 차면 하나와 바꿈) 또는 건너뛰기
function skillSheet(id) {
  const u = UNITS.find(x => x.id === id), def = R.unitDef(id), rec = myRec(id, 0), st = statsOf(R, rec);
  const base = [{ ...R.basicSkill(def.attr), ...(def.basic ? { name: def.basic } : {}) }, def.signature];   // 고정
  const mine = slotSkills(R, rec);   // 바꿀 수 있는 칸(역할기·보조 속성·배운 기술)
  const offers = learnOffers(R, rec), tier = R.data.learn.levels[rec.learnUsed ?? 0], full = mine.length >= R.data.learn.slots;
  const nextLv = R.data.learn.levels.find(l => l > rec.level);
  let pick = null, rep = null;
  const icon = (k, attr) => glyphOf(k.name) ?? GLYPH[attr] ?? '✨';
  const card = (k, attr, data, on, note = '') => `<button class="aug ${on ? 'on' : ''}" ${data}><span class="ic">${icon(k, attr)}</span><span class="tx"><b>${esc(k.name)}${note}</b><small>${k.power ? `${esc(R.attrName(attr))} · ${catOf(k, st)} · 위력 ${k.power}${k.hits > 1 ? `×${k.hits}` : ''}` : '보조 기술'}${k.cd ? ` · 재사용 ${k.cd}턴` : ''}</small><span>${esc(k.desc)}</span></span><span class="ck">${on ? '✓' : ''}</span></button>`;
  const draw = () => {
    modal.innerHTML = `<div class="sheet pick">
      <div class="who-head"><img src="${sprite(id, 'front', rec.stage)}" class="st-${rec.stage}" alt=""><div><b>${esc(u.name)} ${rec.stage ? `<em class="stars">${STARS[rec.stage]}</em>` : ''}</b><small>Lv${rec.level} · ${rec.stage ? `${rec.stage}단계 승진` : '원형'} · ${esc(R.attrName(def.attr))} · ${ROLE[def.role]}${synName(id) ? ` · ${esc(synName(id))}` : ''}</small></div></div>
      ${statGrid(st)}
      <h2>승진 <small>${rec.stage >= 3 ? '최고 단계' : `다음: ${promoNeed(rec)}`}</small></h2>
      ${rec.evoAugs.length ? rec.evoAugs.map(a => { const e = R.evoAug.get(a); return `<div class="skrow"><span>🎖️</span><b>${esc(e.name)}</b><small>${esc(e.desc)}</small></div>`; }).join('') : '<p class="sub">아직 받은 직무 표창이 없어요. 승진할 때 표창 3개 중 1개를 받아요.</p>'}
      ${canEvolve(R, rec, partsCleared()) ? `<button class="big wide" id="promo">⬆ ${rec.stage + 1}단계 승진하기</button>` : ''}
      <h2>고정 기술 <small>잊을 수 없어요</small></h2>${base.map(k => `<div class="skrow"><span>${icon(k, k.attr ?? def.attr)}</span><b>${esc(k.name)} ${catTag(k, st)}</b><small>${esc(k.desc)}</small></div>`).join('')}
      <h2>바꿀 수 있는 기술 <small>${mine.length}/${R.data.learn.slots}칸 · 새 기술을 배우면 하나를 잊어요</small></h2>${mine.length ? mine.map(k => `<div class="skrow"><span>${icon(k, k.attr ?? def.attr)}</span><b>${esc(k.name)} ${catTag(k, st)}</b><small>${esc(k.desc)}</small></div>`).join('') : '<p class="sub">아직 없어요</p>'}
      ${offers.length ? `<h2>✨ 새 기술 배우기 <small>Lv${tier} 기회 · ${learnChances(R, rec)}번 남음</small></h2>
        ${offers.map(k => card(k, k.attr ?? def.attr, `data-p="${k.id}"`, pick === k.id)).join('')}
        ${pick && full ? `<h2>잊을 기술 고르기 <small>이 중 하나를 잊고 새로 배워요</small></h2>${mine.map(k => card(k, k.attr ?? def.attr, `data-r="${k.id}"`, rep === k.id)).join('')}` : ''}
        <div class="sw"><button class="ghost" id="skipL" style="justify-content:center">이번엔 안 배우기</button><button class="big" id="doL" ${!pick || (full && !rep) ? 'disabled' : ''}>${pick ? `「${esc(offers.find(o => o.id === pick).name)}」 배우기` : '기술을 골라요'}</button></div>`
        : `<p class="sub">${nextLv ? `다음 배울 기회: Lv${nextLv}` : '모든 기회를 썼어요'}</p>`}
      <button class="ghost wide" id="closeL">닫기</button></div>`;
    modal.querySelectorAll('[data-p]').forEach(x => x.onclick = () => { pick = x.dataset.p; rep = null; draw(); });
    modal.querySelectorAll('[data-r]').forEach(x => x.onclick = () => { rep = x.dataset.r; draw(); });
    modal.querySelector('#closeL').onclick = () => { modal.hidden = true; storyScreen(); };
    modal.querySelector('#promo')?.addEventListener('click', () => promoSheet(id));
    const after = () => { keep(rec); persist(); learnChances(R, rec) > 0 ? skillSheet(id) : (modal.hidden = true, storyScreen()); };
    modal.querySelector('#doL')?.addEventListener('click', () => { learnSkill(R, rec, pick, rep ?? undefined); after(); });
    modal.querySelector('#skipL')?.addEventListener('click', async () => { if (await ask('이번 기회를 건너뛸까요? 다시 받을 수 없어요.', { ok: '건너뛰기' })) { learnSkill(R, rec, null); after(); } });
  };
  modal.onclick = null; modal.className = ''; modal.hidden = false; draw();
}

// 승진: 직무 표창 3개 중 1개 → 단계 +1(능력치 배율 상승). 단계별 그림이 생기기 전까지는 별·빛으로 표시
function promoSheet(id) {
  const rec = myRec(id, 0), u = UNITS.find(x => x.id === id), choices = promoChoices(rec);
  let pick = null;
  const draw = () => {
    modal.innerHTML = `<div class="sheet pick"><h2>⬆ ${esc(u.name)} ${rec.stage + 1}단계 승진</h2>
      <p class="sub">직무 표창을 하나 골라요. 같은 원형도 표창에 따라 전문 분야가 갈려요. (한 번 고르면 바꿀 수 없어요)</p>
      ${choices.map(e => `<button class="aug ${pick === e.id ? 'on' : ''}" data-e="${e.id}"><span class="ic">🎖️</span><span class="tx"><b>${esc(e.name)}</b><span>${esc(e.desc)}</span></span><span class="ck">${pick === e.id ? '✓' : ''}</span></button>`).join('')}
      <div class="sw"><button class="ghost" id="cancelP" style="justify-content:center">나중에</button><button class="big" id="doP" ${pick ? '' : 'disabled'}>${pick ? `「${esc(R.evoAug.get(pick).name)}」 받고 승진` : '표창을 골라요'}</button></div></div>`;
    modal.querySelectorAll('[data-e]').forEach(x => x.onclick = () => { pick = x.dataset.e; draw(); });
    modal.querySelector('#cancelP').onclick = () => skillSheet(id);
    modal.querySelector('#doP').onclick = async () => {
      rec.stage += 1; rec.evoAugs.push(pick); keep(rec); persist();
      modal.innerHTML = `<div class="promo-fx"><img src="${sprite(id, 'front', rec.stage)}" class="st-${rec.stage}" alt=""><b>승진!</b><span>${esc(u.name)} · ${rec.stage}단계 ${STARS[rec.stage]}</span><small>직무 표창 「${esc(R.evoAug.get(pick).name)}」</small></div>`;
      modal.querySelector('.promo-fx').animate([{ opacity: 0, transform: 'scale(.8)' }, { opacity: 1, transform: 'scale(1.04)', offset: .4 }, { opacity: 1, transform: 'none' }], { duration: 700, fill: 'forwards' });
      await sleep(1800); skillSheet(id);
    };
  };
  modal.onclick = null; modal.className = ''; modal.hidden = false; draw();
}

// 대사: 비주얼노벨처럼 전체 화면. 내 갈매미는 왼쪽, 빌런은 오른쪽(말하는 쪽만 밝게). 글자가 하나씩 나오고,
// 누르면 그 줄을 바로 다 보여 주고, 한 번 더 누르면 다음 줄. 「」 안의 말은 금색으로 강조
const hl = t => esc(t).replace(/「([^」]+)」/g, '<em class="q">「$1」</em>');
function talk(lines, opt = {}) {
  return new Promise(resolve => {
    lines = [...(lines ?? [])];
    if (opt.tip) lines.push({ who: 'TIP', say: opt.tip });
    if (!lines.length) return resolve();
    bgm('story');
    const lead = UNITS.find(u => u.id === SAVE.team[0]);
    let i = 0, typing = null, full = '';
    const foeId = lines.find(l => /^[VDTEIN]\d+$/.test(l.who))?.who;
    modal.className = 'scene-on';
    modal.innerHTML = `<div class="scene sc-${opt.scene || 'harbor'}">${bgStyle(opt.scene || 'harbor') ? `<div class="kb" style="${bgStyle(opt.scene || 'harbor')}"></div>` : ''}${ambient(opt.scene || 'harbor', !!bgStyle(opt.scene || 'harbor'))}
      ${opt.title ? `<div class="place">${esc(opt.title)}</div>` : ''}
      <img class="por me" id="pMe" src="${sprite(lead.id, 'front', SAVE.units[lead.id]?.stage ?? 0)}" style="--sz:${bodySz(lead.id, SAVE.units[lead.id]?.stage ?? 0)}" alt="">
      <img class="por foe" id="pFoe" alt="" ${foeId ? '' : 'hidden'}>
      <div class="dbox"><div class="who" id="who"></div><p class="say" id="say"></p>
        <div class="tk"><button class="ghost" id="skip">건너뛰기</button><span id="pg"></span></div></div>
      ${opt.title ? `<div class="titlecard" id="card"><small>${esc(opt.sub || '')}</small><b>${esc(opt.title)}</b></div>` : ''}</div>`;
    const $ = id => modal.querySelector(`#${id}`);
    const draw = () => {
      const l = lines[i], v = /^V\d+$/.test(l.who) ? R.villains.get(l.who) : null, ally = /^[DTEIN]\d+$/.test(l.who) ? l.who : null;
      const npc = NPC[l.who] && ART.includes(NPC[l.who]) ? NPC[l.who] : null;
      const pic = npc ? `${BASE}/duel/sprites/${npc}_front.webp${q}` : v ? sprite(l.who, 'front') : ally ? (R.units.has(ally) ? sprite(ally, 'front') : `${BASE}/thumbs/${ally}.jpg`) : null;
      if (pic) { const p = $('pFoe'); p.hidden = false; if (p.dataset.src !== pic) { p.src = pic; p.dataset.src = pic; { const pid = v ? l.who : ally; p.style.setProperty('--sz', !npc && pid && R.units.has(pid) ? bodySz(pid) : 1); } p.classList.toggle('bossp', !!v?.boss); p.classList.toggle('card', !!ally && !R.units.has(ally)); setTint(p, l.who); } }
      const speaker = l.who === 'ME' ? 'me' : v ? 'foe' : ally || npc ? 'ally' : '';
      $('pMe').classList.toggle('on', speaker === 'me'); $('pFoe').classList.toggle('on', speaker === 'foe' || speaker === 'ally');
      const box = modal.querySelector('.dbox');
      box.className = `dbox ${l.who === 'N' ? 'narr' : l.who === 'TIP' ? 'tip' : speaker || 'other'}`;
      $('who').textContent = l.who === 'N' ? '' : l.who === 'TIP' ? '💡 대결 팁' : l.who === 'ME' ? lead.name : v ? v.name : ally ? (NAMES[ally] ?? ally) : l.who;
      $('pg').textContent = `${i + 1} / ${lines.length}  ▼`;
      full = hl(l.say); const plain = l.say; let n = 0;
      clearInterval(typing); $('say').textContent = '';
      typing = setInterval(() => { n += 1; $('say').textContent = plain.slice(0, n); if (n >= plain.length) { clearInterval(typing); typing = null; $('say').innerHTML = full; } }, 28);
    };
    const done = () => { clearInterval(typing); modal.onclick = null; modal.hidden = true; modal.className = ''; resolve(); };
    $('skip').onclick = e => { e.stopPropagation(); done(); };
    modal.onclick = () => {
      if ($('card')) { $('card').remove(); return; }
      if (typing) { clearInterval(typing); typing = null; $('say').innerHTML = full; return; }
      i++; if (i >= lines.length) done(); else draw();
    };
    modal.hidden = false; draw();
    if ($('card')) setTimeout(() => { $('card')?.classList.add('fade'); setTimeout(() => $('card')?.remove(), 650); }, 1300);
  });
}

const sceneTitle = st => ({ title: `${st.id} ${st.title}`, sub: st.chapter.title, scene: st.scene });
async function playStage(st) {
  await talk(st.before, { ...sceneTitle(st), tip: st.hint });
  fight(st);
}
function fight(st) {
  sceneNow = st.scene || 'harbor';
  const foe = st.enemy.map((e, i) => { const v = R.villains.get(e.id); return { uid: `b${i}`, id: e.id, name: v.name, tier: 'LIMITED', level: e.level, xp: 0, stage: e.stage ?? st.chapter.needStage ?? 0, evoAugs: [] }; });
  const mine = SAVE.team.map(myRec);
  fightId = null;
  if (ACC) myApi('story/battle', {}).then(r => { fightId = r.result.battle; ACC.items = r.result.items; if (b && b.phase === 'action' && !busy) actionPanel(); }).catch(() => {});
  startBattle(mine, foe, won => {
    const avg = st.enemy.reduce((a, e) => a + e.level, 0) / st.enemy.length, xp = battleXp(R, avg, won);
    const ups = [];
    for (const r of mine) {
      const before = r.level; addXp(R, r, xp); keep(r);
      if (r.level > before) ups.push(`${r.name} Lv${before}→${r.level}`);
    }
    const canLearn = mine.some(r => learnChances(R, r) > 0);
    const first = won && !SAVE.cleared.includes(st.id);
    if (first) SAVE.cleared.push(st.id);
    persist();
    const last = st.chapter.stages.at(-1).id === st.id;
    const after = async () => {
      if (won) { await talk(st.after, { scene: st.scene }); if (first && last) await talk(st.chapter.clear, { title: `${st.chapter.title} 끝`, sub: '실적 달성', scene: 'hall' }); }
      else await talk(st.lose ?? [{ who: 'N', say: '수비조가 물러났어요. 레벨을 올리거나 편성·상성을 바꿔 다시 도전해 보세요.' }], { scene: st.scene });
      storyScreen();
    };
    return { text: `출전한 갈매미마다 경험치 +${xp}${ups.length ? ` · 레벨 업! ${ups.join(', ')}` : ''}${canLearn ? ' · ✨ 새 기술을 배울 수 있어요(스토리 화면에서 갈매미를 눌러요)' : ''}${mine.some(r => canEvolve(R, r, partsCleared())) ? ' · ⬆ 승진할 수 있는 갈매미가 있어요' : ''}${first && last && /^\d+$/.test(st.chapter.id) ? ` · ${st.chapter.id}단계 승진 심사가 열렸어요(Lv${R.data.growth.evolveLevel[+st.chapter.id - 1]}부터 승진)` : ''}`,
      buttons: won ? [['다음으로', after]] : [['다시 도전', () => fight(st)], ['스토리로', after]] };
  }, st.hint, { retry: () => fight(st), back: storyScreen });
}

// ── 대결 ──
let fleeTo = null; // 도망치기: { retry, back }
let sceneNow = '';
function startBattle(mine, foe, end, hint, flee, seed) {   // seed: PvP는 서버가 준 시드(서버가 같은 대결을 다시 돌려 확인)
  onEnd = end; hintNow = hint || ''; fleeTo = flee; if (seed === undefined) pvp = null;
  b = createBattle(R, mine, foe, seed ?? ((Math.random() * 2 ** 31) | 0));
  const boss = foe.some(f => R.villains.get(f.id)?.boss);
  bgm(boss ? 'boss' : 'battle');
  app.innerHTML = `<div id="arena" class="sc-${sceneNow || 'harbor'}">${ambient(sceneNow || 'harbor')}<div class="ground"></div><div class="haze"></div><div class="plat foe"></div><div class="plat me"></div>
    <img class="mon foe ${boss ? 'boss' : ''}" id="m1" alt=""><img class="mon me" id="m0" alt="">
    <div class="box foe" id="b1"></div><div class="box me" id="b0"></div><div id="fx"></div></div>
    <div id="msg"></div><div id="panel"></div>`;
  view = snap();
  render(); say(hint ? `💡 ${hint}` : b.log.at(-1));
  flow();
}
const snap = () => b.sides.map(s => ({ active: s.active, hp: s.fighters.map(f => f.hp), down: s.fighters.map(f => f.hp <= 0) }));
const shown = s => b.sides[s].fighters[view[s].active];
const say = t => { document.getElementById('msg').textContent = t || ''; };

function render() {
  for (const s of [0, 1]) {
    const f = shown(s), img = document.getElementById(`m${s}`), src = sprite(f.rec.id, s === 0 ? 'back' : 'front', f.rec.stage);
    if (img.dataset.src !== src) { img.src = src; img.dataset.src = src; setTint(img, f.rec.id); img.style.setProperty('--sz', f.rec.id.startsWith('V') ? 1 : bodySz(f.rec.id, f.rec.stage)); }
    img.style.opacity = view[s].down[view[s].active] ? 0 : 1;
    const hp = view[s].hp[view[s].active], pct = Math.max(0, hp / f.maxHp * 100);
    const col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
    const st = Object.keys(f.status).map(k => `<span class="st">${({ burn: '화상', freeze: '빙결', sleep: '잠', bind: '묶임', rage: '폭주' })[k]}</span>`).join('');
    const augs = b.sides[s].augs.map(id => `<span>${ico(id, AUG_ICON[id]?.[0] ?? '')}${esc(R.battleAug.get(id).name)}</span>`).join('');
    const box = document.getElementById(`b${s}`), downNow = view[s].down[view[s].active];
    box.classList.toggle('down', downNow);
    img.classList.remove('st-1', 'st-2', 'st-3'); if (f.rec.stage && !tintOf(f.rec.id)) img.classList.add(`st-${f.rec.stage}`);
    box.innerHTML = `<div class="nm"><span>${esc(f.name)}${f.rec.stage ? ` <em class="stars">${STARS[f.rec.stage]}</em>` : ''}</span>${downNow ? '<em class="ko">전투불가</em>' : `<em>Lv${f.rec.level}</em>`}</div>
      <div class="hpb"><i style="width:${pct}%;background:${col}"></i></div>
      <div class="row"><span>${chip(f.attr)}${f.extraAttr ? chip(f.extraAttr) : ''}${st}</span>${s === 0 ? `<small>${Math.max(0, Math.round(hp))}/${f.maxHp}</small>` : ''}</div>
      <div class="row"><div class="dots">${view[s].down.map((d, i) => `<i class="${d ? 'down' : ''}" title="${esc(b.sides[s].fighters[i].name)}"></i>`).join('')}</div></div>
      ${augs ? `<div class="augs">${augs}</div>` : ''}${s === 1 ? `<button class="finfo" ${b.turn > 1 ? '' : 'disabled'} title="${b.turn > 1 ? '상대 정보' : '한 턴이 지나면 볼 수 있어요'}">🔍 ${b.turn > 1 ? '정보' : '1턴 뒤'}</button>` : ''}`;
    if (s === 1) box.querySelector('.finfo').onclick = e => { e.stopPropagation(); if (b.turn > 1 && b.phase === 'action' && !busy) foeInfo(); };
  }
}
// 상대 정보(한 턴이 지난 뒤): 능력치·역할·기술·증강체. 숫자는 레벨·승진이 반영된 이번 전투 값
function foeInfo() {
  const f = activeOf(b, 1), hp = Math.max(0, Math.round(view[1].hp[view[1].active]));
  const st = { hp: f.maxHp, ...Object.fromEntries(['patk', 'pdef', 'matk', 'mdef', 'spd'].map(k => [k, Math.round(f.base[k])])) };
  const me = activeOf(b, 0), faster = st.spd > me.base.spd ? '상대가 더 빨라요(먼저 움직여요)' : st.spd < me.base.spd ? '내가 더 빨라요' : '속도가 같아요';
  const skills = f.skills.map(k => `<div class="fi-k"><b>${esc(k.name)}</b><small>${k.power ? `${chip(k.attr ?? f.attr)} ${catTag(k, f.base)} 위력 ${k.power}${k.hits > 1 ? `×${k.hits}` : ''}` : '보조 기술'}${k.cd ? ` · 재사용 ${k.cd}턴` : ''}</small></div>`).join('');
  const augs = b.sides[1].augs.map(id => `<div class="fi-k"><b>${ico(id, AUG_ICON[id]?.[0] ?? '✨')} ${esc(R.battleAug.get(id).name)}</b><small>${esc(R.battleAug.get(id).desc)}</small></div>`).join('');
  modal.className = ''; modal.hidden = false;
  modal.innerHTML = `<div class="sheet finfo-sheet"><h2>🔍 ${esc(f.name)} <small>Lv${f.rec.level} · ${R.attrName(f.attr)}${f.extraAttr ? `+${R.attrName(f.extraAttr)}` : ''} · ${ROLE[f.role] ?? ''}</small></h2>
    <p class="sub">체력 ${hp} / ${f.maxHp} · ${faster} (내 속도 ${Math.round(me.base.spd)})</p>${statGrid(st)}
    <h2>기술</h2><div class="fi-grid">${skills}</div>${augs ? `<h2>증강체</h2><div class="fi-grid">${augs}</div>` : ''}<button class="ghost wide" id="fiClose">닫기</button></div>`;
  modal.querySelector('#fiClose').onclick = () => { modal.hidden = true; };
  modal.onclick = e => { if (e.target === modal) modal.hidden = true; };
}

// 고르기·행동 차례 정하기
async function flow() {
  render();
  if (b.phase === 'over') return result();
  if (b.phase === 'choose') {
    const p = pending(b, 0);
    if (p.augments || p.needSwitch) return chooseSheet(p);
    const from = b.log.length; aiChoose(b, 1); await play(from);
    return flow();
  }
  actionPanel();
}

// 증강체: 그림 표시와 분류(어떤 상황에 쓰는지)
const AUG_ICON = { reverse: ['🔄', '상성 뒤집기'], weakguard: ['🛡️', '상성 방어'], focus: ['🎯', '상성 공격'], neutral: ['🌫️', '상성 없애기'], camo: ['🦎', '속성 바꾸기'], dual: ['➕', '공격 속성 추가'],
  counter: ['↩️', '반격'], learn: ['📖', '피해 줄이기'], first: ['⏱️', '선공'], bulwark: ['🧱', '방어'], firstaid: ['🩹', '회복'], freeswitch: ['🔀', '교체'],
  lastone: ['🔥', '역전'], strip: ['✂️', '약화'], rustle: ['🍤', '멈추기'], peanut: ['🥜', '공격'],
  sharp: ['🦅', '급소'], swift: ['💨', '속도'], thick: ['🪶', '방어'], overtime: ['💪', '공격'], drain: ['🍗', '흡수'], thorns: ['🌵', '반사'], pierce: ['📌', '관통'], lunch: ['🍱', '지속 회복'] };
const FLIP = ['camo', 'reverse', 'weakguard', 'neutral', 'dual', 'counter'];   // 상성이 불리할 때 쓸 만한 것
const multTag = m => m > 1 ? `<b class="good">×${m} 유리</b>` : m < 1 ? `<b class="bad">×${m} 불리</b>` : '<b>×1 보통</b>';

function chooseSheet(p) {
  let aug = null, to = null, enter = true;   // enter: 처음 열 때만 카드가 떠오름(고를 때마다 다시 그려도 반복 안 함)
  const me = activeOf(b, 0), foe = activeOf(b, 1);
  const alive = b.sides[0].fighters.map((f, i) => [f, i]).filter(([f]) => f.hp > 0);
  const atk = R.typeMult(me.attr, foe.attr), def = R.typeMult(foe.attr, me.attr), bad = atk < 1 || def > 1;
  const face = f => sprite(f.rec.id, 'front', f.rec.stage), faceCls = f => tintOf(f.rec.id);
  const draw = () => {
    const vs = p.augments ? `<div class="vs">
        <div class="side"><img src="${face(me)}" class="flip" alt=""><b>${esc(me.name)}</b>${chip(me.attr)}</div>
        <div class="mid"><small>내 공격</small>${multTag(atk)}<small>상대 공격</small>${multTag(def)}</div>
        <div class="side"><img src="${face(foe)}" class="${faceCls(foe)}" alt=""><b>${esc(foe.name)}</b>${chip(foe.attr)}</div></div>` : '';
    const rr = b.sides[0].rerolled ?? [];
    // 확인 버튼 글자: 고른 것은 이름으로, 아직 안 고른 것은 무엇을 고를지(둘 다 있으면 「 · 」로 이음)
    const okLabel = () => [p.augments && (aug ? `「${esc(R.battleAug.get(aug).name)}」 선택` : '증강체를 골라요'),
      p.needSwitch && (to !== null ? `${esc(b.sides[0].fighters[to].name)} 출전` : '나갈 갈매미를 골라요')].filter(Boolean).join(' · ');
    modal.innerHTML = `<div class="sheet pick ${p.augments ? 'full' : ''} ${p.augments && p.needSwitch ? 'both' : ''} ${enter ? 'enter' : ''}">
      ${p.augments ? `<h2>전투 증강체</h2><p class="sub">${b.turn === 1 ? '대결 시작' : '전투불가 발생'} · 3개 중 하나를 골라요. 마음에 안 들면 칸마다 한 번씩 🎲 다시 뽑을 수 있어요.</p>${vs}
        ${hintNow && b.turn === 1 ? `<div class="tipline">💡 ${hl(hintNow)}</div>` : ''}
        ${p.augments.map(id => { const a = R.battleAug.get(id), [ic, kind] = AUG_ICON[id] ?? ['✨', ''], rec = bad && FLIP.includes(id);
          return `<div class="augc"><button class="aug ${aug === id ? 'on' : ''}" data-a="${id}"><span class="ic">${ico(id, ic)}</span><span class="tx"><b>${esc(a.name)}${rec ? '<em>지금 추천</em>' : ''}</b><small>${esc(kind)}</small><span>${esc(a.desc)}</span></span><span class="ck">${aug === id ? '✓' : ''}</span></button>
            <button class="ghost reroll" data-r="${p.augments.indexOf(id)}" ${rr[p.augments.indexOf(id)] ? 'disabled' : ''}>${rr[p.augments.indexOf(id)] ? '다시 뽑기 끝' : '🎲 다시 뽑기'}</button></div>`; }).join('')}` : ''}
      ${p.needSwitch ? `<h2>다음에 나갈 갈매미</h2><p class="sub">상대: ${esc(foe.name)} ${chip(foe.attr)}</p>
        ${alive.map(([f, i]) => { const m = R.typeMult(f.attr, foe.attr), pct = Math.round(f.hp / f.maxHp * 100);
          return `<button class="aug ${to === i ? 'on' : ''}" data-t="${i}"><img class="ic" src="${face(f)}" alt=""><span class="tx"><b>${esc(f.name)}</b><small>${R.attrName(f.attr)} · ${ROLE[f.role]} · 상대에게 ${m > 1 ? '유리' : m < 1 ? '불리' : '보통'}</small><i class="hpmini"><i style="width:${pct}%"></i></i></span><span class="ck">${to === i ? '✓' : ''}</span></button>`; }).join('')}` : ''}
      <button class="big wide" id="ok" ${(p.augments && !aug) || (p.needSwitch && to === null) ? 'disabled' : ''}>${okLabel()}</button></div>`;
    enter = false;
    modal.querySelectorAll('[data-a]').forEach(x => x.onclick = () => { aug = x.dataset.a; draw(); });
    modal.querySelectorAll('[data-r]').forEach(x => x.onclick = () => {
      const i = +x.dataset.r, old = p.augments[i];
      try { rerollAugment(b, 0, i); if (pvp) pvp.log.push({ r: i }); } catch { x.disabled = true; x.textContent = '다른 증강체가 없어요'; return; }
      p.augments = b.sides[0].offers; if (aug === old) aug = null; sfx('aug'); draw();
      modal.querySelectorAll('.augc')[i]?.classList.add('rolled');
    });
    modal.querySelectorAll('[data-t]').forEach(x => x.onclick = () => { to = +x.dataset.t; draw(); });
    modal.querySelector('#ok').onclick = async () => {
      modal.hidden = true; modal.className = '';
      const from = b.log.length;
      const pick = { ...(aug ? { augment: aug } : {}), ...(to !== null ? { switchTo: to } : {}) };
      choose(b, 0, pick); if (pvp) pvp.log.push({ c: pick });
      if (b.phase === 'choose') aiChoose(b, 1);
      await play(from); flow();
    };
  };
  modal.onclick = null; modal.className = p.augments ? 'full' : ''; modal.hidden = false; draw();
}

function actionPanel() {
  const f = activeOf(b, 0), legal = legalActions(b, 0), s = b.sides[0];
  const can = a => legal.some(x => x.kind === a.kind && (a.kind === 'skill' ? x.skillId === a.skillId : x.to === a.to));
  const panel = document.getElementById('panel');
  const foe = activeOf(b, 1);
  // 기술 4개(기본·역할·고유·보조 속성)를 2×2로. 지금 상대에게 유리/불리를 같이 보여 줌
  const combo = f.skills.find(k => k.id.startsWith('combo-')), ready = combo && can({ kind: 'skill', skillId: combo.id });
  panel.innerHTML = `${combo ? `<button class="combo-btn" data-k="${combo.id}" ${ready ? '' : 'disabled'}><small>🤝 합동기 · ${esc(R.data.synergies[combo.id.slice(6)].name)} · 대결당 1번</small><b>${esc(combo.name)}</b><span>${esc(combo.desc)}${ready ? '' : b.sides[0].comboUsed ? ' (이미 씀)' : ' (같은 시너지 동료가 있어야 해요)'}</span></button>` : ''}<div class="skills">${f.skills.filter(k => !k.id.startsWith('combo-')).map(k => {
    const at = k.attr ?? f.attr, m = k.power ? R.typeMult(at, foe.attr) : 1, wait = f.cds[k.id] > 0;
    const kind = k.id.startsWith('sub-') ? '보조 속성' : k.id.startsWith('learn-') ? '배운 기술' : k.id === 'sig' ? '고유기' : k.id.startsWith('role-') ? '역할기' : '기본';
    return `<button class="sk" data-k="${k.id}" style="--c:${COLOR[at]}" ${can({ kind: 'skill', skillId: k.id }) ? '' : 'disabled'}>
      <span class="kd">${kind}${wait ? ` · ${f.cds[k.id]}턴 뒤` : k.cd ? ` · 재사용 ${k.cd}턴` : ''}</span><b>${esc(k.name)}</b>
      <span class="ln">${k.power ? `${chip(at)}${catTag(k, f.base)}<small>위력 ${k.power}${k.hits > 1 ? `×${k.hits}` : ''}</small>${m > 1 ? '<em class="good">▲유리</em>' : m < 1 ? '<em class="bad">▼불리</em>' : ''}` : '<small>보조 기술</small>'}</span>
      ${restDesc(k) ? `<p>${esc(restDesc(k))}</p>` : ''}</button>`;
  }).join('')}</div><div class="sw">${s.fighters.map((x, i) => i === s.active ? '' : `<button data-s="${i}" ${can({ kind: 'switch', to: i }) ? '' : 'disabled'}><img src="${sprite(x.rec.id, 'front', x.rec.stage)}" alt=""><span>${esc(x.name)}<br><small>${x.hp > 0 ? `교체 · ${Math.round(x.hp / x.maxHp * 100)}%` : '전투불가'}</small></span></button>`).join('')}</div>`;
  panel.querySelectorAll('[data-k]').forEach(x => x.onclick = () => turn({ kind: 'skill', skillId: x.dataset.k }));
  if (fightId) { const n = s.itemsUsed, max = R.data.itemRules.perBattle, have = ACC.items.reduce((a, i) => a + i.count, 0);
    panel.insertAdjacentHTML('beforeend', `<div class="fleerow"><button class="flee" id="bag" ${n >= max || !have ? 'disabled' : ''}>🎒 아이템 ${n}/${max} <small>(가진 ${have})</small></button></div>`);
    panel.querySelector('#bag').onclick = itemSheet; }
  (panel.querySelector('.fleerow') ?? panel.appendChild(Object.assign(document.createElement('div'), { className: 'fleerow' }))).insertAdjacentHTML('beforeend', '<button class="flee" id="flee">🏃 도망치기</button>');
  panel.querySelectorAll('[data-s]').forEach(x => x.onclick = () => turn({ kind: 'switch', to: +x.dataset.s }));
  panel.querySelector('#flee').onclick = runAway;
}
// 아이템: 가진 것 중 하나(다시 출근 깃털은 일으킬 갈매미도 고름). 계정에서 1개 빼고 → 그 턴 행동으로 씀
function itemSheet() {
  if (busy) return;
  const mine = id => ACC.items.find(i => i.id === id)?.count ?? 0, down = b.sides[0].fighters.map((f, i) => [f, i]).filter(([f]) => f.hp <= 0);
  let pick = null, target = null;
  const draw = () => {
    const it = pick && R.data.items[pick];
    modal.innerHTML = `<div class="sheet pick"><h2>🎒 아이템 <small>대결 한 번에 ${R.data.itemRules.perBattle}개까지 · 쓰면 이번 턴 행동을 써요</small></h2>
      ${Object.entries(R.data.items).map(([id, d]) => { const why = mine(id) ? itemProblem(b, 0, id) : '없어요'; return `<button class="aug ${pick === id ? 'on' : ''}" data-i="${id}" ${why ? 'disabled' : ''}><span class="ic">${ico(id, d.revive ? '🪶' : d.cleanse && !d.heal ? '💧' : '🩹')}</span><span class="tx"><b>${esc(d.name)} <em>${mine(id)}개</em></b><span>${esc(d.desc)}${why && mine(id) ? ` (${esc(why)})` : ''}</span></span><span class="ck">${pick === id ? '✓' : ''}</span></button>`; }).join('')}
      ${it?.revive ? `<h2>다시 일으킬 갈매미</h2>${down.map(([f, i]) => `<button class="aug ${target === i ? 'on' : ''}" data-t="${i}"><img class="ic" src="${sprite(f.rec.id, 'front', f.rec.stage)}" alt=""><span class="tx"><b>${esc(f.name)}</b></span><span class="ck">${target === i ? '✓' : ''}</span></button>`).join('')}` : ''}
      <p class="sub" id="ierr"></p>
      <div class="sw"><button class="ghost" id="ix" style="justify-content:center">닫기</button><button class="big" id="iu" ${!pick || (it?.revive && target === null) ? 'disabled' : ''}>${it ? `「${esc(it.name)}」 쓰기` : '아이템을 골라요'}</button></div></div>`;
    modal.querySelectorAll('[data-i]').forEach(x => x.onclick = () => { pick = x.dataset.i; target = null; draw(); });
    modal.querySelectorAll('[data-t]').forEach(x => x.onclick = () => { target = +x.dataset.t; draw(); });
    modal.querySelector('#ix').onclick = () => { modal.hidden = true; };
    modal.querySelector('#iu').onclick = async () => {
      modal.querySelector('#iu').disabled = true;
      try {
        const r = await myApi('items/use', { battle: fightId, id: pick });
        const row = ACC.items.find(i => i.id === pick); if (row) row.count = r.result.left;
        modal.hidden = true;
        turn({ kind: 'item', item: pick, ...(target !== null ? { target } : {}) });
      } catch (e) { modal.querySelector('#ierr').textContent = e.message; modal.querySelector('#iu').disabled = false; }
    };
  };
  modal.onclick = null; modal.className = ''; modal.hidden = false; draw();
}
// 도망치기: 경험치 없음, 깬 것으로 치지 않음
async function runAway() {
  if (busy || !(await ask(pvp?.mode === 'rank' ? '랭크게임에서 나갈까요? 패배로 처리돼요.' : pvp ? '대전에서 나갈까요?' : '대결에서 도망칠까요? 경험치를 받지 못해요.', { ok: pvp ? '나가기' : '도망치기' }))) return;
  pvp = null;
  b.phase = 'over';
  document.getElementById('m0').animate([{ transform: 'translateX(0)', opacity: 1 }, { transform: 'translateX(-120px)', opacity: 0 }], { duration: 500, fill: 'forwards' });
  say('수비조가 재빨리 물러났어요!');
  document.getElementById('panel').innerHTML = `<div class="sheet" style="border-radius:14px"><h2>도망쳤어요</h2>
    <p class="sub">경험치는 없어요. 편성이나 상성을 바꿔 다시 도전해 보세요.</p>
    <div class="sw"><button class="ghost" id="fr" style="justify-content:center">다시 도전</button><button class="ghost" id="fb" style="justify-content:center">돌아가기</button></div></div>`;
  document.getElementById('fr').onclick = fleeTo.retry;
  document.getElementById('fb').onclick = fleeTo.back;
}

async function turn(mine) {
  if (busy) return; busy = true;
  document.getElementById('panel').innerHTML = '';
  const from = b.log.length;
  try { act(b, [mine, pickAi(b, 1)]); if (pvp) pvp.log.push({ a: mine }); } catch (e) { say(e.message); busy = false; return actionPanel(); }
  await play(from);
  busy = false; flow();
}

// ── 기록을 한 줄씩 보여 주며 효과 ──
async function play(from) {
  let lastAtk = 0, lastSkill = null;
  const lines = b.log.slice(from);
  for (const line of lines) {
    say(line);
    let m, wait = 700;
    if (line.startsWith('합동기 「')) { comboFlash(line.match(/「(.+?)」/)?.[1] ?? ''); wait = 1300; }   // 「…의 …!」 모양이라 공격 문장보다 먼저
    else if ((m = line.match(/^(.+)의 (.+)!$/))) {
      const side = [0, 1].find(s => shown(s).name === m[1] && s !== lastAtk) ?? [0, 1].find(s => shown(s).name === m[1]) ?? lastAtk;
      lastAtk = side; lastSkill = shown(side).skills.find(k => k.name === m[2]);
      lunge(side);
      if (m[2] === '다섯 번째 목소리') await fiveVoices();   // 앙~ 갈매미맨이야의 고유기: 세는 소리 연출
      if (lastSkill?.power) await shoot(side, lastSkill.attr ?? shown(side).attr, lastSkill.hits ?? 1, glyphOf(lastSkill.name));
      else if (lastSkill) aura(side, lastSkill.attr ?? shown(side).attr);
      wait = lastSkill?.power ? 120 : 600;
    } else if ((m = line.match(/화상으로 (\d+) 피해/))) {
      const t = [0, 1].find(s => line.startsWith(shown(s).name)) ?? 0; hit(t, +m[1], COLOR.FIRE, false, false);
    } else if ((m = line.match(/ (\d+) 피해를 입었어요\.(.*)$/)) && !line.startsWith('역습')) {
      const t = 1 - lastAtk, big = m[2].includes('굉장');
      hit(t, +m[1], COLOR[lastSkill?.attr ?? shown(lastAtk).attr], big, m[2].includes('급소'));
      if (m[2].includes('별로')) wait = 800;
    } else if ((m = line.match(/^(?:반사|역습)! .* (\d+) 피해/))) {
      hit(lastAtk, +m[1], '#ffffff', false, false);
    } else if (line.includes('나왔어요!')) {
      for (const s of [0, 1]) if (view[s].active !== b.sides[s].active && line.includes(b.sides[s].fighters[b.sides[s].active].name)) {
        view[s].active = b.sides[s].active; render(); popIn(s);
      }
    } else if (line.includes('전투불가가 됐어요')) {
      const t = [0, 1].find(s => line.startsWith(shown(s).name) && !view[s].down[view[s].active]) ?? 0;
      view[t].down[view[t].active] = true; faint(t); wait = 1500;
    } else if (line.includes('모두 막아 냈어요')) { burst(1 - lastAtk, '#ffffff', 10, 60); }
    else if (line.includes('상태가 됐어요')) { const t = [0, 1].find(s => line.startsWith(shown(s).name)); if (t !== undefined) burst(t, '#b07cff', 12, 50); }
    else if (line.includes('증강체 「') || line.includes('] 아이템 「')) { banner(line.replace(/^\[.+?\] /, '')); wait = 1000; }
    else if ((m = line.match(/^(.+)의 체력이 (\d+) 회복됐어요/))) {
      sfx('heal');
      const t = [0, 1].find(s => shown(s).name === m[1]) ?? 0; view[t].hp[view[t].active] = Math.min(shown(t).maxHp, view[t].hp[view[t].active] + +m[2]); burst(t, '#7ce3a0', 14, 60);
    } else if (line.includes('정신을 차렸어요')) { const t = [0, 1].find(s => line.startsWith(shown(s).name)) ?? 0; burst(t, '#8fdcf5', 12, 50); }
    else if (line.includes('다시 일어났어요')) {
      sfx('heal');
      // 뒤에 있던 갈매미가 일어남: 그 갈매미만 지금 체력으로(나와 있는 갈매미의 이번 턴 피해는 줄마다 따로 보여 줌)
      for (const s of [0, 1]) b.sides[s].fighters.forEach((f, i) => { if (view[s].down[i] && f.hp > 0) { view[s].down[i] = false; view[s].hp[i] = f.hp; } });
      render();
    }
    render();
    await sleep(wait);
  }
  view = snap(); render();
}

const center = s => { const a = document.getElementById('arena').getBoundingClientRect(), r = document.getElementById(`m${s}`).getBoundingClientRect(); return [r.left - a.left + r.width / 2, r.top - a.top + r.height * 0.55]; };
function lunge(s) {
  const dx = s === 0 ? 28 : -28, dy = s === 0 ? -18 : 12;
  document.getElementById(`m${s}`).animate([{ transform: 'translate(0,0)' }, { transform: `translate(${dx}px,${dy}px)` }, { transform: 'translate(0,0)' }], { duration: 320, easing: 'ease-out' });
}
function hit(t, n, color, big, crit) {
  sfx(crit ? 'crit' : big ? 'big' : 'hit');
  view[t].hp[view[t].active] = Math.max(0, view[t].hp[view[t].active] - n);
  const img = document.getElementById(`m${t}`);
  img.animate([{ transform: 'translateX(0)', filter: 'brightness(1)' }, { transform: 'translateX(-8px)', filter: 'brightness(2.2)' }, { transform: 'translateX(7px)' }, { transform: 'translateX(-4px)' }, { transform: 'translateX(0)', filter: 'brightness(1)' }], { duration: 380 });
  burst(t, color, big ? 26 : 14, big ? 120 : 80);
  const [x, y] = center(t), fx = document.getElementById('fx'), d = document.createElement('div');
  d.className = 'dmg'; d.textContent = (crit ? '급소! ' : '') + n; d.style.left = `${x - 20}px`; d.style.top = `${y - 40}px`; if (big) d.style.color = '#ffe36e';
  fx.append(d); d.animate([{ transform: 'translateY(0)', opacity: 1 }, { transform: 'translateY(-36px)', opacity: 0 }], { duration: 900, easing: 'ease-out' }).onfinish = () => d.remove();
  if (big) document.getElementById('arena').animate([{ transform: 'translate(0,0)' }, { transform: 'translate(4px,-3px)' }, { transform: 'translate(-4px,3px)' }, { transform: 'translate(0,0)' }], { duration: 260 });
}
function burst(t, color, n, dist) {
  const [x, y] = center(t), fx = document.getElementById('fx');
  for (let i = 0; i < n; i++) {
    const p = document.createElement('div'), ang = Math.random() * Math.PI * 2, r = dist * (0.5 + Math.random() * 0.6), sz = 6 + Math.random() * 8;
    p.className = 'pt'; p.style.cssText = `left:${x}px;top:${y}px;width:${sz}px;height:${sz}px;background:${color};box-shadow:0 0 8px ${color}`;
    fx.append(p);
    p.animate([{ transform: 'translate(-50%,-50%) scale(1)', opacity: 1 }, { transform: `translate(${Math.cos(ang) * r - sz / 2}px,${Math.sin(ang) * r - sz / 2}px) scale(.3)`, opacity: 0 }], { duration: 500 + Math.random() * 250, easing: 'cubic-bezier(.2,.7,.3,1)' }).onfinish = () => p.remove();
  }
}
// 쓰러짐: 짧게 멈칫(화면이 살짝 어두워짐) → 하얀 실루엣 → 깃털·빛 조각으로 흩어지며 위로 사라짐. 이름 칸은 회색 + 작은 「전투불가」
function faint(s) {
  sfx('faint');
  const img = document.getElementById(`m${s}`), arena = document.getElementById('arena'), fx = document.getElementById('fx');
  img.getAnimations().forEach(a => a.cancel());
  const veil = document.createElement('div'); veil.className = 'veil'; fx.append(veil);
  veil.animate([{ opacity: 0 }, { opacity: 1, offset: .15 }, { opacity: 1, offset: .6 }, { opacity: 0 }], { duration: 1500 }).onfinish = () => veil.remove();
  arena.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.025)', offset: .12 }, { transform: 'scale(1.025)', offset: .3 }, { transform: 'scale(1)' }], { duration: 900, easing: 'ease-out' });
  img.animate([
    { filter: 'brightness(1)', transform: 'none', opacity: 1 },
    { filter: 'brightness(0) invert(1)', transform: 'none', opacity: 1, offset: .18 },
    { filter: 'brightness(0) invert(1)', transform: 'scale(1.02)', opacity: 1, offset: .35 },
    { filter: 'brightness(0) invert(1) blur(2px)', transform: 'scaleX(.9) scaleY(.75) translateY(6%)', opacity: .55, offset: .7 },
    { filter: 'brightness(0) invert(1) blur(4px)', transform: 'scaleX(.8) scaleY(.4) translateY(14%)', opacity: 0 },
  ], { duration: 1400, easing: 'ease-in', fill: 'forwards' });
  const a = arena.getBoundingClientRect(), r = img.getBoundingClientRect();
  const cx = r.left - a.left + r.width / 2, top = r.top - a.top, h = r.height;
  setTimeout(() => {
    for (let i = 0; i < 22; i++) {
      const feather = i % 3 !== 0, p = document.createElement('div');
      p.className = feather ? 'feather' : 'mote';
      const x = cx + (Math.random() - .5) * r.width * .8, y = top + h * (.25 + Math.random() * .6);
      p.style.cssText = `left:${x}px;top:${y}px`;
      fx.append(p);
      const dx = (Math.random() - .5) * 60, rise = 50 + Math.random() * 90, rot = (Math.random() - .5) * 300;
      p.animate([{ transform: 'translate(-50%,-50%) rotate(0) scale(1)', opacity: 0 }, { opacity: .95, offset: .15 },
        { transform: `translate(calc(-50% + ${dx}px),calc(-50% - ${rise}px)) rotate(${rot}deg) scale(${feather ? .8 : .3})`, opacity: 0 }],
      { duration: 1300 + Math.random() * 700, delay: Math.random() * 250, easing: 'cubic-bezier(.2,.6,.3,1)' }).onfinish = () => p.remove();
    }
  }, 420);
}
// 속성별로 날아가는 효과(그림 없이 글자 그림 + 빛 꼬리)
const GLYPH = { STONE: '🪨', GOLD: '🪙', FIRE: '🔥', ICE: '❄️', BLOSSOM: '🌸', SEA: '💧', SHADOW: '🌑', STAR: '⭐', THREAD: '🧵', GRAVITY: '🌀' };
// 기술 이름으로 정해지는 그림(기본기): 똥폭탄 💩, 먹물 ⚫, 집게 🦞
const glyphOf = name => name.includes('똥') ? '💩' : name.includes('먹물') ? '⚫' : name.includes('집게') ? '🦞' : null;
function shoot(from, attr, hits, glyph) {
  const [x0, y0] = center(from), [x1, y1] = center(1 - from), fx = document.getElementById('fx'), n = Math.min(5, Math.max(2, hits + 1));
  return new Promise(res => {
    for (let i = 0; i < n; i++) {
      const p = document.createElement('div'); p.className = 'shot'; p.textContent = glyph ?? GLYPH[attr] ?? '✨';
      p.style.cssText = `left:${x0}px;top:${y0}px;filter:drop-shadow(0 0 6px ${COLOR[attr]})`;
      fx.append(p);
      const arc = (i - (n - 1) / 2) * 26, mx = (x0 + x1) / 2 - x0 + arc, my = (y0 + y1) / 2 - y0 - 40 - Math.abs(arc);
      p.animate([{ transform: 'translate(-50%,-50%) scale(.5) rotate(0)', opacity: 0 }, { opacity: 1, offset: .1 },
        { transform: `translate(calc(-50% + ${mx}px),calc(-50% + ${my}px)) scale(1.1) rotate(180deg)`, offset: .5 },
        { transform: `translate(calc(-50% + ${x1 - x0}px),calc(-50% + ${y1 - y0}px)) scale(1.3) rotate(360deg)`, opacity: 1 }],
      { duration: 420, delay: i * 70, easing: 'ease-in' }).onfinish = () => { p.remove(); if (i === n - 1) res(); };
    }
  });
}
// 보조 기술(강화·회복 등): 자기 주위로 빛과 글자 그림이 솟아오름
function aura(s, attr) {
  const [x, y] = center(s), fx = document.getElementById('fx');
  burst(s, COLOR[attr], 10, 50);
  for (let i = 0; i < 5; i++) {
    const p = document.createElement('div'); p.className = 'shot'; p.textContent = GLYPH[attr] ?? '✨';
    p.style.cssText = `left:${x + (i - 2) * 22}px;top:${y + 20}px;font-size:18px`;
    fx.append(p);
    p.animate([{ transform: 'translate(-50%,0)', opacity: 0 }, { opacity: 1, offset: .3 }, { transform: 'translate(-50%,-70px)', opacity: 0 }], { duration: 700, delay: i * 60 }).onfinish = () => p.remove();
  }
}
function popIn(s) { const img = document.getElementById(`m${s}`); img.getAnimations().forEach(a => a.cancel()); img.animate([{ transform: 'scale(.5)', opacity: 0 }, { transform: 'scale(1.05)', opacity: 1 }, { transform: 'scale(1)', opacity: 1 }], { duration: 420, easing: 'ease-out' }); }
// 「다섯 번째 목소리」: 갈매미 한 마리.. 두 마리.. 세 마리.. 네 마리.. (정적) 다섯 마리... 앙~ 갈매미맨이야~
async function fiveVoices() {
  const fx = document.getElementById('fx'), arena = document.getElementById('arena');
  const dim = document.createElement('div'); dim.className = 'five'; fx.append(dim);
  dim.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 300, fill: 'forwards' });
  const say = async (t, ms, cls = '') => {
    const d = document.createElement('div'); d.className = `five-line ${cls}`; d.textContent = t; dim.append(d);
    d.animate([{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], { duration: 350, fill: 'forwards' });
    await sleep(ms);
  };
  for (const n of ['한', '두', '세', '네']) await say(`갈매미 ${n} 마리..`, 520);
  await sleep(900);                                   // (정적)
  await say('갈매미 다섯 마리...', 1000, 'slow');
  dim.innerHTML = '';
  const big = document.createElement('div'); big.className = 'five-big'; big.textContent = '앙~ 갈매미맨이야~'; dim.append(big);
  arena.animate([{ filter: 'brightness(1)' }, { filter: 'brightness(2.2) saturate(1.4)' }, { filter: 'brightness(1)' }], { duration: 700 });
  big.animate([{ opacity: 0, transform: 'scale(.4)' }, { opacity: 1, transform: 'scale(1.12)', offset: .35 }, { opacity: 1, transform: 'scale(1)' }], { duration: 700, fill: 'forwards' });
  await sleep(1300);
  dim.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 300 }).onfinish = () => dim.remove();
  await sleep(250);
}
function comboFlash(name) {
  sfx('combo');
  const a = document.getElementById('arena'), d = document.createElement('div'); d.className = 'comboflash'; d.innerHTML = `<small>🤝 합동기</small><b>${esc(name)}</b>`;
  document.getElementById('fx').append(d);
  a.animate([{ filter: 'brightness(1)' }, { filter: 'brightness(1.8) saturate(1.4)' }, { filter: 'brightness(1)' }], { duration: 600 });
  d.animate([{ opacity: 0, transform: 'scale(.6)' }, { opacity: 1, transform: 'scale(1.08)', offset: .2 }, { opacity: 1, transform: 'scale(1)', offset: .8 }, { opacity: 0 }], { duration: 1300 }).onfinish = () => d.remove();
}
function banner(text) {
  sfx('aug');
  const d = document.createElement('div'); d.className = 'banner'; d.textContent = text;
  document.getElementById('fx').append(d);
  d.animate([{ opacity: 0, transform: 'scale(.8)' }, { opacity: 1, transform: 'scale(1)', offset: .2 }, { opacity: 1, offset: .8 }, { opacity: 0 }], { duration: 1000 }).onfinish = () => d.remove();
}

function result() {
  const won = b.winner === 0, r = onEnd(won);
  bgm(null); sfx(won ? 'win' : 'lose');
  document.getElementById('panel').innerHTML = `<div class="sheet" style="border-radius:14px"><h2>${b.winner === null ? '무승부' : won ? '승리!' : '패배…'}</h2>
    <p class="sub">${b.turn - 1}턴 · ${esc(r.text)}</p>
    <div class="sw">${r.buttons.map(([t], i) => `<button class="${i ? 'ghost' : 'big'}" data-r="${i}" style="justify-content:center">${esc(t)}</button>`).join('')}</div></div>`;
  document.querySelectorAll('[data-r]').forEach(x => x.onclick = () => r.buttons[+x.dataset.r][1]());
}

boot();
