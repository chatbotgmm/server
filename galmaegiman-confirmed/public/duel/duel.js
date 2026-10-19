// 수비대 대결 시험판: 새 전투 엔진(src/battle)을 그대로 묶어 브라우저에서 돌림(빌드: scripts/build-play.mjs → dist/duel/duel.js)
// 규칙·유닛·스토리는 서버가 data/battle.yaml·data/story.yaml을 검사해 보내 줌(/duel/data.json). 기술 효과는 그림 없이 코드로 그림
// 스토리 진행(레벨·경험치·배운 기술·승진·깬 스테이지): 「내 갈매미」에 로그인돼 있으면 계정에, 아니면 이 브라우저에 저장
// 로그인돼 있으면 「내 갈매미」 전투 편성을 스토리 팀으로 쓸 수 있고, 스토리 대결에서 가진 아이템을 쓸 수 있음
import { rulesFrom } from '../../src/battle/core.ts';
import { createBattle, choose, rerollAugment, act, pending, legalActions, pickAi, aiChoose, skillProblem, skillPreview, activeOf, itemProblem } from '../../src/battle/engine.ts';
import { bgm, sfx, isMuted, toggleMute, getVol, setVol, isChMuted, setChMute } from './sound.js';
import { addXp, battleXp, canEvolve, evoChoices, innateLearned, learnChances, learnLeft, learnOffers, learnPromo, learnSkill, promoSkills, slotSkills, statsOf, xpToNext } from '../../src/battle/growth.ts';

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
const restDesc = k => k.id.startsWith('sub-') && !k.effects?.length && !k.bonus && !k.pierce && !k.drain && !k.crit && !k.priority ? '' : k.desc.replace(/위력 \d+(으로 \d+번)?(, )?/, '');
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
// 그림 가장자리가 잘린 악당 그림(원본 자체가 잘림, v0.14.9 검사): 잘린 쪽만 부드럽게 흐리게 해서 직선으로 잘린 티가 안 나게. 새 그림을 받으면 지우기
const CUT = { V15: 'L' };   // V5·V6·V8·V10·V11·V14는 v0.14.9에 새 그림(잘림 없음)으로 바꿈
const EDGE = { L: 'to right', R: 'to left', T: 'to bottom', B: 'to top' };
const cutMask = (img, id) => { const c = CUT[id] ?? '', m = [...c].map(k => `linear-gradient(${EDGE[k]},transparent,#000 24%)`).join(',');
  img.style.webkitMaskImage = img.style.maskImage = m; img.style.webkitMaskComposite = c.length > 1 ? 'source-in' : ''; img.style.maskComposite = c.length > 1 ? 'intersect' : ''; };
const setTint = (img, id) => { cutMask(img, id); img.classList.remove('tint-shadow', 'tint-mirror', 'tint-peanut', 'tint-mirror-art', 'tint-peanut-art'); const t = tintOf(id); if (t) img.classList.add(t); };

// ── 저장: 로그인돼 있으면 계정(서버), 아니면 이 브라우저 ──
const SAVE_KEY = 'gmm-duel-story-v1';
const local = () => { try { return JSON.parse(localStorage.getItem(SAVE_KEY) || 'null'); } catch { return null; } };
// 열쇠(v0.16.2): 계정 = 개체마다 「o번호」(OwnedCharacter.id) — 같은 종류도 마리마다 레벨·기술이 따로.
//   종류 id 열쇠는 시험판(아직 없는 갈매미)·로그인 전에만. 예전 저장(종류 id)은 그 종류의 가장 오래된 개체로 옮김
let OWN = {};   // 'o12' → 'D1'
const spOf = k => (k && k[0] === 'o' ? OWN[k] : k);
const okKey = k => !!spOf(k) && UNITS.some(u => u.id === spOf(k));
const myKeys = sp => Object.keys(OWN).filter(k => OWN[k] === sp);   // 이 종류의 내 개체(오래된 순)
const keyFor = sp => myKeys(sp).find(k => !SAVE?.team.includes(k)) ?? myKeys(sp)[0] ?? sp;
// 가진 개체 목록 다시 받기(뽑기·조합 뒤). 팀에 종류 열쇠로 들어간 게 내 개체가 생겼으면 개체로 바꿈
async function refreshOwn() {
  try { const j = await myApi('story'); ACC.mine = j.mine; ACC.owned = [...new Set([...ACC.owned, ...j.owned])]; setOwn(j.mine);
    let ch = false; SAVE.team = SAVE.team.map(t => { if (t[0] === 'o' || !myKeys(t).length) return t; ch = true; return keyFor(t); });
    if (ch) { SAVE.team = [...new Set(SAVE.team)]; persist(); } } catch { /* 다음에 */ }
}
const setOwn = mine => { OWN = Object.fromEntries((mine ?? []).map(m => [`o${m.oid}`, m.id])); };
const pristine = u => !u || (u.level <= STORY.start.level && !u.xp && !(u.stage > 0) && !(u.learned ?? []).length && !(u.learnUsed > 0));
function load(s = local()) {
  s = s && typeof s === 'object' ? JSON.parse(JSON.stringify(s)) : {};
  s.units ??= {}; s.team ??= []; s.cleared ??= [];
  for (const k of Object.keys(s.units)) if (k[0] !== 'o' && pristine(s.units[k])) delete s.units[k];   // 손대지 않은 기본 기록(예전엔 33종 모두 만들어 둠)
  if (ACC) for (const k of Object.keys(s.units)) {   // 예전 종류 열쇠 → 가장 오래된(아직 기록 없는) 개체로
    if (k[0] === 'o') continue;
    const to = myKeys(k).find(o => !s.units[o]);
    if (!to) continue;
    s.units[to] = s.units[k]; delete s.units[k]; s.moved = true;
    s.team = s.team.map(t => (t === k ? to : t));
    if (s.hp?.[k]) { s.hp[to] = s.hp[k]; delete s.hp[k]; }
  }
  if (ACC) s.team = s.team.map(t => (t[0] !== 'o' && myKeys(t).length ? keyFor(t) : t));
  s.team = [...new Set(s.team.filter(okKey))].slice(0, 3);
  for (const k of Object.keys(s.units)) if (!okKey(k)) delete s.units[k];   // 조합·판매로 없어진 개체
  return s;
}
// 기록이 없으면 지금 만듦: 타고나는 기술(똥폭탄 + 무작위 1~2개, 열쇠로 정해져 늘 같음)
const unitRec = k => (SAVE.units[k] ??= { level: STORY.start.level, xp: 0, learned: innateLearned(R, spOf(k), k), learnUsed: 0, learnPick: 0, stage: 0, evoAugs: [] });
let SAVE;
let saveTimer = null, saveFail = false;
const persist = () => {
  if (!ACC) { try { localStorage.setItem(SAVE_KEY, JSON.stringify(SAVE)); } catch { /* 저장 안 돼도 진행은 가능 */ } return; }
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => myApi('story', { data: { units: SAVE.units, team: SAVE.team, cleared: SAVE.cleared, ...(SAVE.hp ? { hp: SAVE.hp } : {}) } }).then(() => { saveFail = false; }).catch(() => { saveFail = true; const h = document.getElementById('savewarn'); if (h) h.hidden = false; }), 400);
};
const progressed = s => !!s && (s.cleared?.length > 0 || Object.values(s.units ?? {}).some(u => u.xp > 0 || u.level > STORY.start.level));
const myRec = (key, i) => { const id = spOf(key), u = UNITS.find(x => x.id === id), s = unitRec(key); return { uid: `a${i}`, id, key, name: u.name, tier: u.tier, level: s.level, xp: s.xp, stage: s.stage ?? 0, evoAugs: [...(s.evoAugs ?? [])], learned: [...(s.learned ?? [])], learnUsed: s.learnUsed ?? 0, learnPick: s.learnPick ?? 0 }; };
// 스토리 체력(v0.14.7): 전투가 끝난 체력 비율이 이어지고, 시간이 지나면 차오름(0→가득 storyHp.fullMinutes분). 체력 0이면 전투에 못 나감
const HP_FULL_MS = () => (R.data.storyHp?.fullMinutes ?? 60) * 60000;
const hpFrac = id => { const h = SAVE.hp?.[id]; return h ? Math.min(1, h.f + (Date.now() - h.at) / HP_FULL_MS()) : 1; };
const setHp = (id, f) => { (SAVE.hp ??= {})[id] = { f: Math.round(Math.max(0, Math.min(1, f)) * 1e4) / 1e4, at: Date.now() }; };
const hpMax = id => statsOf(R, myRec(id, 0)).hp;
const hpNow = id => Math.floor(hpMax(id) * hpFrac(id));
const hpWaitMin = id => Math.ceil((1 - hpFrac(id)) * HP_FULL_MS() / 60000);
const hpBar = id => { const f = hpFrac(id), pct = Math.round(f * 100), col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
  return `<i class="hpb ${hpNow(id) < 1 ? 'zero' : ''}" title="체력 ${hpNow(id)}/${hpMax(id)}"><i style="width:${pct}%;background:${col}"></i></i><small class="hpt">${hpNow(id) < 1 ? `기절 · ${hpWaitMin(id)}분 뒤 출근` : `체력 ${hpNow(id)}/${hpMax(id)}${pct < 100 ? ` · 가득까지 ${hpWaitMin(id)}분` : ''}`}</small>`; };
const keep = r => { SAVE.units[r.key ?? r.id] = { level: r.level, xp: r.xp, learned: r.learned ?? [], learnUsed: r.learnUsed ?? 0, learnPick: r.learnPick ?? 0, stage: r.stage ?? 0, evoAugs: r.evoAugs ?? [] }; };
// 승진(진화): 깬 부(1·2·3부) 수 = 열린 승진 단계. 표창 후보는 유닛·단계마다 항상 같음(다시 열어도 안 바뀜)
const partsCleared = () => STORY.chapters.filter(c => /^\d+$/.test(c.id) && SAVE.cleared.includes(c.stages.at(-1).id)).length;
const STARS = ['', '★', '★★', '★★★'];
function seededRand(seed) { let h = 2166136261; for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619); let t = h >>> 0;
  return () => { t = (t + 0x6d2b79f5) | 0; let x = Math.imul(t ^ (t >>> 15), t | 1); x ^= x + Math.imul(x ^ (x >>> 7), x | 61); return ((x ^ (x >>> 14)) >>> 0) / 4294967296; }; }
const promoChoices = rec => evoChoices(R, rec, seededRand(`${rec.key ?? rec.id}:evo:${rec.stage}`));
const promoNeed = rec => { const lv = R.data.growth.evolveLevel[rec.stage]; return rec.stage >= 3 ? '' : `Lv${lv} + ${rec.stage + 1}부 완료`; };
// 시너지: 이름과, 팀에서 같은 시너지 2마리+면 생기는 합동기
const synName = id => { const k = R.unitDef(id)?.syn; return k ? R.data.synergies[k].name : ''; };
function teamCombos(keys) {   // 같은 종류 2마리는 시너지 1마리로 셈
  const by = {}; for (const id of new Set(keys.map(spOf))) { const k = R.unitDef(id)?.syn; if (k) (by[k] ??= []).push(id); }
  return Object.entries(by).filter(([, v]) => v.length >= 2).map(([k, v]) => ({ k, skill: R.data.synergies[k].skill.name, names: v.map(id => UNITS.find(u => u.id === id).name) }));
}

document.title = GAME.name;
// ⚙ 설정: 소리 켜기·끄기, 로그아웃, 계정 삭제를 한곳에(첫 화면은 오른쪽 아래, 다른 화면은 오른쪽 위에 떠 있음)
function muteBtn(show = true) {
  let m = document.getElementById('mute');
  if (!show) { m?.remove(); return; }
  if (m) return;
  m = document.createElement('button'); m.id = 'mute'; m.className = 'mute'; m.setAttribute('aria-label', '설정'); if (ICONS.includes('u-settings')) { m.classList.add('img'); m.innerHTML = `<img src="${BASE}/duel/icons/u-settings.png" alt="">`; } else m.textContent = '⚙️';
  m.onclick = e => { e.stopPropagation(); settingsSheet(); };
  document.body.append(m);
}
// 스피커 그림(켜짐: 소리 물결, 꺼짐: X)
const spk = off => `<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M4 9h4l5-4v14l-5-4H4z" fill="currentColor" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>${off ? '<path d="M16 9l5 6M21 9l-5 6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" fill="none"/>' : '<path d="M16 9.2a4 4 0 0 1 0 5.6M18.6 6.6a7.6 7.6 0 0 1 0 10.8" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/>'}</svg>`;
function settingsSheet() {
  const prev = modal.innerHTML, wasHidden = modal.hidden, prevClass = modal.className, prevClick = modal.onclick;
  const close = () => { if (wasHidden) { modal.hidden = true; modal.innerHTML = ''; } else { modal.innerHTML = prev; modal.className = prevClass; modal.onclick = prevClick; } };
  let coupMsg = '';
  const draw = () => {
    modal.className = ''; modal.hidden = false;
    modal.innerHTML = `<div class="sheet set"><h2>⚙️ 설정<button class="xbtn" id="sX" aria-label="닫기">✕</button></h2><div class="scols"><div class="scol">
      <div class="setrow"><span>🔊 소리</span><button class="tog ${isMuted() ? '' : 'on'}" id="sSnd">${isMuted() ? '꺼짐' : '켜짐'}</button></div>
      ${[['vMusic', 'music', '🎵 배경음악'], ['vSfx', 'sfx', '💥 효과음']].map(([id, k, lb]) => { const m = isChMuted(k); return `<div class="setrow vol ${isMuted() ? 'off' : ''} ${m ? 'chm' : ''}"><span>${lb}</span><input type="range" min="0" max="100" step="5" value="${getVol(k)}" id="${id}" aria-label="${lb.slice(3)} 음량"><b id="${id}N">${getVol(k)}</b><button class="chmute ${m ? 'on' : ''}" data-ch="${k}" aria-pressed="${m}" aria-label="${lb.slice(3)} ${m ? '음소거 풀기' : '음소거'}">${spk(m)}</button></div>`; }).join('')}</div><div class="scol">
      ${ACC ? `<div class="setrow"><span>👤 ${esc(ACC.nickname || '내 계정')}</span><button class="ghost" id="sOut">로그아웃</button></div>
      <div class="setrow coup"><span>🎟 쿠폰</span><input id="sCode" placeholder="쿠폰 코드" autocomplete="off" maxlength="60"><button class="ghost" id="sCoup">받기</button></div>
      ${coupMsg ? `<p class="coupmsg">${esc(coupMsg)}</p>` : ''}
      ${TUT ? '<div class="setrow"><span>📖 튜토리얼</span><button class="ghost" id="sTut">다시 보기</button></div>' : ''}
      <details class="setdel"><summary>계정 삭제(탈퇴)</summary>
        <p class="sub">갈매미·도감·재화·아이템·탐험·스토리 진행이 <b>모두 지워지고 되돌릴 수 없어요.</b></p>
        <input id="sDelc" placeholder="확인을 위해 탈퇴 라고 적어 주세요" autocomplete="off"><button class="big wide" id="sDel" disabled>계정 삭제</button></details>` : ''}
      </div></div><button class="ghost wide" id="sClose">닫기</button></div>`;
    modal.querySelector('#sSnd').onclick = () => { toggleMute(); draw(); };
    for (const [id, k] of [['vMusic', 'music'], ['vSfx', 'sfx']]) {
      const r = modal.querySelector('#' + id), n = modal.querySelector(`#${id}N`);
      r.oninput = () => { if (isMuted()) { toggleMute(); modal.querySelectorAll('.setrow.vol').forEach(x => x.classList.remove('off')); const t = modal.querySelector('#sSnd'); t.classList.add('on'); t.textContent = '켜짐'; } if (isChMuted(k)) { setChMute(k, false); chUi(k); } setVol(k, +r.value); n.textContent = r.value; };
      if (k === 'sfx') r.onchange = () => sfx('hit');   // 손을 떼면 효과음 크기를 들려줌
    }
    // 줄마다 음소거: 음량 값은 그대로 두고 소리만 끔 → 풀면 원래 음량(0이었으면 50으로)
    const chUi = k => { const b = modal.querySelector(`[data-ch="${k}"]`), m = isChMuted(k), lb = k === 'music' ? '배경음악' : '효과음';
      b.classList.toggle('on', m); b.setAttribute('aria-pressed', m); b.setAttribute('aria-label', `${lb} ${m ? '음소거 풀기' : '음소거'}`); b.innerHTML = spk(m); b.closest('.setrow').classList.toggle('chm', m); };
    modal.querySelectorAll('[data-ch]').forEach(b => b.onclick = () => { const k = b.dataset.ch, m = !isChMuted(k);
      if (!m && isMuted()) { toggleMute(); modal.querySelectorAll('.setrow.vol').forEach(x => x.classList.remove('off')); const t = modal.querySelector('#sSnd'); t.classList.add('on'); t.textContent = '켜짐'; }
      setChMute(k, m);
      if (!m && getVol(k) === 0) { setVol(k, 50); const id = k === 'music' ? 'vMusic' : 'vSfx'; modal.querySelector('#' + id).value = 50; modal.querySelector(`#${id}N`).textContent = 50; }
      chUi(k); if (!m && k === 'sfx') sfx('hit'); });
    modal.querySelector('#sClose').onclick = close; modal.querySelector('#sX').onclick = close;
    modal.querySelector('#sTut')?.addEventListener('click', () => { TUT = { step: 1, seen: TUT.seen, replay: true }; modal.hidden = true; modal.innerHTML = ''; document.querySelector('.myov')?.remove(); menu(); });
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
// 불러오는 중 화면(index.html에 처음부터 있음): 실제로 끝낸 단계 수만큼 막대를 채움(열쇠 → 게임 자료 → 계정 진행)
const loadStep = (n, msg) => { const f = document.getElementById('ldFill'), t = document.getElementById('ldMsg'); if (f) f.style.width = `${Math.round(n / 3 * 100)}%`; if (t && msg) t.textContent = msg; };
async function boot() {
  try {
    // 앱: 로그인한 계정으로 게임 열쇠를 받음(로그인 전이면 로그인 화면으로)
    if (!K && APP) {
      if (!APP.token()) { titleScreen(false); return; }
      try { K = (await myApi('duel-key')).k; q = `?k=${encodeURIComponent(K)}`; }
      catch (e) { if (e.status === 401) { APP.setToken(null); location.href = 'my.html?next=game'; return; } throw e; }
    }
    loadStep(1, '게임 자료 받는 중…');
    const r = await fetch(`${BASE}/duel/data.json${q}`);
    const j = await r.json();
    if (!r.ok) throw new Error(j.error || `불러오기 실패(${r.status})`);
    R = rulesFrom(j.rules); STORY = j.story; ART = j.villainArt ?? []; NAMES = j.names ?? {}; BG = j.bgArt ?? []; ICONS = j.iconArt ?? []; EVO = new Set(j.stageArt ?? []);
    UNITS = j.units.map(u => ({ ...u, def: R.units.get(u.id) }));
    // 로그인돼 있으면 계정 진행을 불러옴. 계정에 아직 없고 이 브라우저에 진행이 있으면 계정으로 옮김
    loadStep(2, '내 수비대 불러오는 중…');
    try { ACC = await myApi('story'); } catch { ACC = null; }
    if (ACC) { try { TUT = await myApi('tutorial'); if (TUT.step === 1 && !TUT.seen.includes('tut')) { TUT.seen.push('tut'); myApi('tutorial', { step: 1, seen: 'tut' }).catch(() => {}); } } catch { TUT = null; } }   // 스토리 저장보다 먼저 물어야 새 유저로 판정됨
    loadStep(3, '준비 완료!');
    if (ACC) {
      setOwn(ACC.mine);
      if (ACC.data) { SAVE = load(ACC.data); if (SAVE.moved) { delete SAVE.moved; persist(); } }   // 예전 저장을 개체로 옮겼으면 바로 저장(편성 개체 보호도 같이)
      else { SAVE = load(progressed(local()) ? local() : null); if (progressed(local())) persist(); }
      if (!SAVE.team.length && ACC.battleTeam.length) { for (const sp of ACC.battleTeam) SAVE.team.push(keyFor(sp)); persist(); }   // 스토리는 1~3마리(v0.15.0), 개체로
    } else SAVE = load();
    titleScreen(!!ACC);
    const fieldItem = new URLSearchParams(location.search).get('fieldItem');
    if (fieldItem && R.data.items[fieldItem]?.heal) { menu(); healSheet(fieldItem, menu); const url = new URL(location.href); url.searchParams.delete('fieldItem'); history.replaceState(null, '', url); }
    if (TUT) setInterval(tutTick, 350);
  } catch (e) {
    app.innerHTML = `<header><h1>수비대 대결</h1></header><div class="err">${esc(e.message)}</div>`;
  }
}

// 첫 화면(v0.14.5, galmaegiman-title-clean): 배경 1 + 로고·부제 + 캐릭터 4 + 버튼 2(로그인·게임 시작, 글자는 그림에 있음 → 코드로 다시 쓰지 않음)
// 좌표는 1961×802 설계 기준(layout-example.json을 참고해 캐릭터·버튼이 서로 안 겹치게 조금 옮김, 그림의 투명 여백은 잘라 냄 → 누르는 칸 = 보이는 버튼 크기).
// 크기 s = min(화면 높이/802, 화면 너비/1961)로 전부 같은 비율로 줄이고, 남는 가로는 왼쪽 무리·가운데·오른쪽 무리 사이로, 남는 세로는 위(로고)·아래(캐릭터·버튼) 사이로 나눔.
// [x, y, 너비, 높이, 가로 무리(0 왼쪽·1 가운데·2 오른쪽), 세로(0~1: 남는 세로 중 얼마나 내려갈지)]
const T2 = {
  logo: [596.5, 26.5, 777.1, 285.4, 1, 0.3], sub: [807.9, 287.9, 344.1, 107.5, 1, 0.3],
  guardian: [62.8, 232.8, 644.4, 458.2, 0, 1], octopus: [1264.3, 612.3, 195.3, 159.6, 2, 1], lobster: [1344.8, 326.8, 400.4, 325.9, 2, 1], gull: [1758.8, 480.8, 192.3, 148.1, 2, 1],
  start: [818.4, 663.4, 438.2, 126.0, 1, 1], login: [834.3, 663.4, 406.4, 126.0, 1, 1],   // 로그인은 게임 시작과 같은 자리(같은 높이, 가운데 맞춤)
};
const t2at = k => { const [x, y, w, h, gx, gy] = T2[k]; return `--x:${x};--y:${y};--w:${w};--h:${h};--gx:${gx};--gy:${gy}`; };
function titleScreen(loggedIn) {
  modal.hidden = true; muteBtn(false); if (R) bgm('title');
  const IN = APP ? 'intro' : `${BASE}/duel/intro`;
  const login = APP ? 'my.html?next=game' : '/my?next=duel&g=1';   // 로그인 화면(로그인하면 게임으로 돌아옴) — 기존 흐름 그대로
  const img = (k, f, alt = '', cls = '') => `<img class="t2p ${cls}" src="${IN}/t2_${f}.webp" alt="${alt}" style="${t2at(k)}" draggable="false">`;
  // 가운데 한 자리: 로그인 전 「로그인」(로그인 화면으로, 로그인하면 게임으로 돌아옴) → 로그인 뒤 「게임 시작」(로비). 오른쪽 아래: 로그인 뒤에만 계정(이름은 코드 글자)
  const main = loggedIn ? `<button class="t2p t2btn tgo" id="tgo" style="${t2at('start')}" aria-label="게임 시작"><img src="${IN}/t2_start.webp" alt="" draggable="false"></button>`
    : `<a class="t2p t2btn tgo" id="tlogin" href="${login}" style="${t2at('login')}" aria-label="로그인"><img src="${IN}/t2_login.webp" alt="" draggable="false"></a>`;
  const acc = loggedIn && ACC ? `<button class="t2acc" id="tacc" aria-label="계정 정보 ${esc(ACC.nickname || '내 계정')}"><i aria-hidden="true">${ico('u-profile', '👤')}</i><span>${esc(ACC.nickname || '내 계정')}</span></button>` : '';
  app.innerHTML = `<div class="ttl9 t2"><img class="t2bg" src="${IN}/t2_bg.webp" alt="" draggable="false">
    ${img('guardian', 'guardian', '', 'ch c1')}${img('lobster', 'lobster', '', 'ch c2')}${img('octopus', 'octopus', '', 'ch c3')}${img('gull', 'gull', '', 'ch c4')}
    ${img('logo', 'logo', '갈매미맨 수비대')}${img('sub', 'sub', '항구를 지켜라')}${main}${acc}
    <p class="t2rot" role="note">📱 가로로 돌려 주세요</p>
    <button class="tset t2set" id="tset" aria-label="설정"><img src="${IN}/title_settings_button_base.webp" alt=""><img class="g" src="${IN}/title_settings_gear.webp" alt=""></button></div>`;
  roomParallax(app.querySelector('.t2'), app.querySelector('.t2'));   // 첫 화면도 끌면 배경·캐릭터가 서로 다르게 움직임(v0.14.9), 로고·버튼은 그대로
  app.querySelector('#tgo')?.addEventListener('click', () => { ROOM = null; menu(); });   // 첫 화면에서 들어갈 때마다 방을 새로 고름
  app.querySelector('#tset').addEventListener('click', settingsSheet);
  app.querySelector('#tacc')?.addEventListener('click', () => openMy('account', () => titleScreen(loggedIn)));   // 계정 창을 닫으면 첫 화면으로(로비 아님)
}
let ROOM = null;   // 로비 방(무작위)
function menu() {
  try { sessionStorage.setItem('gmm-k', K); } catch { /* 무시 */ }
  modal.hidden = true; bgm('title'); muteBtn();
  // 로비(v0.12.0, 8번 시안): 그림 배경(다락방) 위에 내 정보·재화만 작게, 아래 나무 팻말 메뉴 5개 + 오른쪽 큰 「플레이」.
  // 대표 갈매미는 로비에서 빼고 「수비대 → 전투 편성」 단상에서(배경 그림의 갈매미는 장식)
  const my = h => APP ? `my.html#${h}` : `/my?g=1#${h}`;
  const bg = BG.includes('attic') ? `background-image:url(${BASE}/duel/bg/attic.jpg${q})` : BG.includes('harbor') ? `background-image:url(${BASE}/duel/bg/harbor.jpg${q})` : '';
  const team = SAVE.team;
  const lvl = team.length ? Math.round(team.reduce((a, id) => a + (SAVE.units[id]?.level ?? 1), 0) / team.length) : 0;
  const storyDot = team.some(id => { const r = myRec(id, 0); return learnChances(R, r) > 0 || canEvolve(R, r, partsCleared()); });
  const st = nextStage();
  // 메뉴 팻말: 그림(btn-*.png, 글자 포함)이 있으면 그림, 없으면 같은 모양을 코드로(조합은 아직 그림이 없음)
  // 메뉴(v0.12.7): 글자는 그림이 아닌 실제 글자(크기·높이 통일), 그림은 아이콘만. 조합 = 깃털·잎 메달
  const MENU = [['party', '탐험', 'mode-camp', '🧭', 'mint'], ['draw', '뽑기', 'u-draw', '🥚', 'pink'], ['units', '수비대', 'mode-normal', '🛡️', 'lilac'], ['craft', '조합', 'u-emblem', '🧩', 'sky'], ['shop', '상점', 'u-shop', '🏪', 'peach']];
  // 방 그림이 레이어로 있으면(배경·받침대·갈매미맨, 같은 원점·같은 크기) 겹쳐 놓고 갈매미맨만 살짝 숨 쉬게(v0.13.0)
  // 방은 4가지(다락방·오두막·카페·온실) 중 무작위(v0.14.7): 첫 화면에서 들어올 때마다 새로 고르고, 메뉴 창을 닫고 돌아올 땐 그대로
  const rooms = ['attic', 'cabin', 'cafe', 'greenhouse'].filter(r => BG.includes(r + 'base'));
  if (!ROOM || !rooms.includes(ROOM)) ROOM = rooms.length ? rooms[Math.floor(Math.random() * rooms.length)] : null;
  const room = ROOM ? `<div class="h8room rm-${ROOM}" aria-hidden="true">${['base.jpg', 'plat.webp', 'char.webp'].map((f, i) => `<i class="rl r${i}" style="background-image:url(${BASE}/duel/bg/${ROOM}${f}${q})"></i>`).join('')}</div>` : '';
  app.innerHTML = `<div class="home8" style="${bg}">${room}
    <header class="h8top"><button class="lprof" id="lprof" aria-haspopup="menu"><i>${ico('u-profile', '🪪')}</i><span><b>${esc(ACC?.nickname || '수비조장')}</b><small>${lvl ? `수비대 Lv${lvl} · 실적 ${SAVE.cleared.length}/${stages().length}` : '아직 팀이 없어요'}</small></span><em class="caret">▾</em></button><div class="lcur" id="lcur"></div></header>
    ${ACC ? `${ICONS.includes('btn-attend') ? `<button class="h8att img" id="td" aria-label="출석체크"><img src="${BASE}/duel/icons/btn-attend.png" alt=""></button>` : `<button class="h8att" id="td"><i>${ico('u-attend', '📅')}</i><span>출석체크</span></button>`}` : ''}
    <div class="h8bot"><nav class="h8menu">${MENU.map(([h, t, ic, e, c]) => `<a href="${my(h)}" data-h="${h}" class="h8b ${c}"><i>${ico(ic, e)}</i><span>${t}</span></a>`).join('')}</nav>
      <div class="h8go"><div class="h8stage"><small>${st ? '다음 이야기' : '이야기'}</small><b>${st ? `${esc(st.id)} ${esc(st.title)}` : '모든 이야기 완료'}</b></div>
      <button class="h8play ${ICONS.includes('btn-play') ? 'img' : ''}" id="go" aria-label="플레이">${ICONS.includes('btn-play') ? `<img src="${BASE}/duel/icons/btn-play.png" alt="">` : '<b>▶ 플레이</b>'}</button></div></div></div>`;
  roomParallax(app.querySelector('.home8'));
  app.querySelector('#td')?.addEventListener('click', todaySheet);
  app.querySelector('#lprof').onclick = e => { e.stopPropagation(); profMenu(); };
  if (ACC) myApi('daily').then(d => { if (!d.attend.today || d.missions.some(m => !m.done && m.value >= m.goal)) app.querySelector('#td')?.insertAdjacentHTML('beforeend', '<em class="dot"></em>'); }).catch(() => {});
  app.querySelectorAll('.h8menu a[data-h]').forEach(a => a.onclick = e => { e.preventDefault(); sfx('tap'); openMy(a.dataset.h); });
  app.querySelector('#go').addEventListener('click', () => { sfx('tap'); modeScreen(); });
  if (storyDot) app.querySelector('#go').insertAdjacentHTML('beforeend', '<em class="dot"></em>');   // 배울 기술·승진이 있으면(스토리 목록에서 함)
  // 재화·빨간 점(로그인했으면 서버에서): 뽑기권 받을 수 있음 · 탐험 끝남
  if (ACC) myApi('me').then(me => {
    const cur = app.querySelector('#lcur'); if (!cur) return;
    cur.innerHTML = [[ico('u-snack', '🍤'), me.snack, '새우gang'], [ico('u-peanut', '🥜'), me.peanut, '땅콩'], [ico('u-ticket', '🎫'), me.credits, '뽑기권']].map(([i, n, t]) => `<button class="cur" title="${t} · 상점으로" aria-label="${t} ${Number(n).toLocaleString('ko-KR')} · 상점으로"><span class="pill">${i}<b>${Number(n).toLocaleString('ko-KR')}</b><em class="plus" aria-hidden="true">+</em></span></button>`).join('');
    cur.querySelectorAll('.cur').forEach(x => x.onclick = () => openMy('shop'));   // 재화를 누르면 상점
    const now = Date.now(), dot = h => app.querySelector(`.h8menu a[data-h="${h}"]`)?.insertAdjacentHTML('beforeend', '<em class="dot"></em>');
    if (new Date(me.claimReadyAt).getTime() <= now) dot('draw');
    if (me.trip && new Date(me.trip.endsAt).getTime() <= now) dot('party');
  }).catch(() => {});
}
// 로비 방 패럴랙스(v0.14.4): 버튼이 아닌 빈 곳을 끌면 레이어마다 다르게 움직임(배경 < 받침대 < 갈매미맨), 손을 떼면 제자리로. UI는 그대로
function roomParallax(home, room = home?.querySelector('.h8room'), onTap = null) {   // onTap(x, y): 끌지 않고 톡 눌렀을 때   // room: --px·--py를 받을 곳(로비는 방, 첫 화면은 화면 전체)
  if (!home || !room) return;
  let drag = null, x = 0, y = 0, raf = 0;
  const clamp = v => Math.max(-1, Math.min(1, v));
  const put = (nx, ny) => { x = nx; y = ny; if (!raf) raf = requestAnimationFrame(() => { raf = 0; room.style.setProperty('--px', x.toFixed(3)); room.style.setProperty('--py', y.toFixed(3)); }); };
  home.addEventListener('pointerdown', e => {
    if (drag || e.button > 0 || e.target.closest('button,a,input,select,label,.pmenu')) return;
    drag = { id: e.pointerId, cx: e.clientX, cy: e.clientY, x, y }; room.classList.add('drag');
    try { home.setPointerCapture(e.pointerId); } catch { /* 무시 */ }
  });
  home.addEventListener('pointermove', e => {
    if (!drag || e.pointerId !== drag.id) return;
    put(clamp(drag.x + (e.clientX - drag.cx) / (innerWidth * 0.45)), clamp(drag.y + (e.clientY - drag.cy) / (innerHeight * 0.45)));
  });
  const end = e => { if (!drag || e.pointerId !== drag.id) return; const tap = e.type === 'pointerup' && Math.hypot(e.clientX - drag.cx, e.clientY - drag.cy) < 10; drag = null; room.classList.remove('drag'); put(0, 0); if (tap) onTap?.(e.clientX, e.clientY); };
  home.addEventListener('pointerup', end); home.addEventListener('pointercancel', end);
}
// 로비 갈매미맨을 톡 누르면 왼쪽에 말풍선 팁(v0.15.0, 갈매미 말투). 방마다 그림 속 갈매미맨 자리(2640×1080 원본 좌표)
const ROOM_GULL = { attic: [1633, 195, 2420, 758], cabin: [1348, 208, 2111, 719], cafe: [1609, 310, 2269, 765], greenhouse: [1343, 241, 2128, 781] };
const GULL_TIPS = () => [
  '끼룩! 상대 속성에 강한 기술은 <b>1.5배</b>야. 기술 칸의 <b>▲유리</b>를 찾아봐!',
  '속성마다 <b>강한 상대 2개, 약한 상대 2개</b>가 있어. 상대 속성을 먼저 보자, 끼룩.',
  `체력은 전투가 끝나도 그대로야. <b>${R.data.storyHp?.fullMinutes ?? 60}분</b>이면 가득 차고, 치료약으로 바로 채울 수도 있어!`,
  '같은 시너지 갈매미 <b>2마리</b>를 같이 데려가면 <b>합동기</b>가 생겨. 대결당 한 번뿐이니 아껴 써!',
  '출석은 날마다 보상이 커져. 하루 빠지면 <b>1일째부터</b>니까 매일 들러 줘, 끼룩!',
  '전투를 시작하면 <b>증강체 3개</b> 중 하나를 골라. 마음에 안 들면 칸마다 한 번씩 다시 뽑을 수 있어.',
  '레벨이 오르면 <b>새 기술</b>을 배울 수 있어. 스토리 화면에서 갈매미를 눌러 봐!',
  '진 대결도 경험치를 조금 줘. 깬 스테이지는 다시 도전해서 레벨을 올려도 돼, 끼룩.',
];
let tipAt = -1;
function gullTip(x, y) {
  const room = document.querySelector('.h8room'), bb = ROOM_GULL[ROOM]; if (!room || !bb) return;
  const R0 = room.getBoundingClientRect(), s = Math.max(R0.width / 2640, R0.height / 1080), ox = R0.left + (R0.width - 2640 * s) / 2, oy = R0.top + (R0.height - 1080 * s) / 2;
  const g = { l: ox + bb[0] * s, t: oy + bb[1] * s, r: ox + bb[2] * s, b: oy + bb[3] * s };
  if (x < g.l || x > g.r || y < g.t || y > g.b) return;   // 갈매미맨을 누를 때만
  document.querySelector('.gtip')?.remove();
  const tips = GULL_TIPS(); tipAt = (tipAt + 1 + Math.floor(Math.random() * (tips.length - 1))) % tips.length;
  const d = document.createElement('div'); d.className = 'gtip'; d.innerHTML = tips[tipAt]; document.body.append(d); sfx('tap');
  d.style.left = `${Math.max(8, g.l - d.offsetWidth - 10)}px`; d.style.top = `${Math.max(56, Math.min(innerHeight - d.offsetHeight - 90, g.t + (g.b - g.t) * 0.12))}px`;
  if (g.l - d.offsetWidth - 10 < 8) d.classList.add('below');
  const close = () => { d.remove(); removeEventListener('pointerdown', close, true); };
  setTimeout(() => addEventListener('pointerdown', close, true), 50); setTimeout(close, 6000);
}
// ── 게임 튜토리얼(v0.15.0, 새 유저만·계정에 단계 저장) ──
// 1 출석 → 2 첫 뽑기(끝나면 태초의 갈매미맨 지급) → 3 전투 편성(1마리) → 4 첫 전투(속성 상성·공격·체력) → 5 끝. 9 = 끝·건너뜀
// 화면 상태를 0.35초마다 보고 지금 단계에서 누를 곳 하나만 밝게(나머지는 덮개가 막음). 끝난 뒤엔 처음 보는 화면에서 짧은 말풍선 한 번
let TUT = null;
const tutGo = step => {
  const from = TUT.step; TUT.step = step;
  const got = step >= 3 && from <= 2 && ACC && !ACC.owned.includes('D1');
  if (got) ACC.owned.push('D1');   // 태초의 갈매미맨이 들어옴(개체 번호는 서버가 단계를 저장한 뒤 다시 받음)
  if ((step === 4 || step === 9) && !SAVE.team.length && ACC?.owned.includes('D1')) { SAVE.team = [keyFor('D1')]; persist(); if (!ACC.battleTeam.length) ACC.battleTeam = ['D1']; }   // 편성을 마쳤거나 건너뛰면 스토리 팀도 이 한 마리로
  if (!TUT.replay) myApi('tutorial', { step }).then(() => { if (got) refreshOwn(); }).catch(() => {});
  else if (got) refreshOwn();
};
const tutFrame = () => { const f = document.querySelector('.myov.ready iframe'); try { return f?.contentDocument?.body ? f : null; } catch { return null; } };
const tutFind = sel => {
  let el, off = { x: 0, y: 0 };
  if (sel.startsWith('f:')) { const f = tutFrame(); if (!f) return null; el = f.contentDocument.querySelector(sel.slice(2)); const a = f.getBoundingClientRect(); off = { x: a.left, y: a.top }; }
  else el = document.querySelector(sel);
  if (!el) return null; const r = el.getBoundingClientRect(); if (!r.width || !r.height) return null;
  return { el, r: { x: off.x + r.left, y: off.y + r.top, w: r.width, h: r.height } };
};
const tutSay = (t, d) => `<b>${t}</b>${d}`;
function tutTypeCard() {   // 지금 상대와 내 기술로 속성 상성 설명(데이터 그대로)
  const me = activeOf(b, 0), foe = activeOf(b, 1), name = a => R.attrName(a);
  const rows = [...new Set(me.skills.filter(k => k.power).map(k => k.attr ?? me.attr))].map(a => { const m = R.typeMult(a, foe.attr), ch = R.data.chart[a], i = ch?.strong.indexOf(foe.attr) ?? -1;
    const why = i >= 0 ? ch.why[i] : (() => { const c = R.data.chart[foe.attr], j = c?.strong.indexOf(a) ?? -1; return j >= 0 ? c.why[j] : ''; })();
    return `<tr><td>${chip(a)} 기술</td><td>→ ${esc(foe.name)}</td><td class="${m > 1 ? 'good' : m < 1 ? 'bad' : ''}">${m > 1 ? `▲유리 ×${m}` : m < 1 ? `▼불리 ×${m}` : '보통 ×1'}</td><td class="why">${esc(why)}</td></tr>`; }).join('');
  const strong = Object.entries(R.data.chart).filter(([, c]) => c.strong.includes(foe.attr)).map(([a]) => a);
  return `<h3>⚔️ 속성 상성</h3><p>상대 <b>${esc(foe.name)}</b> ${chip(foe.attr)} · 내 <b>${esc(me.name)}</b> ${chip(me.attr)}</p><table>${rows}</table>
    <p class="tsub">${chip(foe.attr)}에 강한 속성: ${strong.map(chip).join(' ')} · 속성마다 강한 상대 2개, 약한 상대 2개예요. 기술 칸의 <b class="good">▲유리</b>를 찾아요.</p><button class="big" data-tc="type">알겠어요</button>`;
}
function tutPlan() {
  const st = TUT.step, f = tutFrame(), fd = f?.contentDocument, hash = f ? f.contentWindow.location.hash : '';
  const free = modal.hidden && !document.querySelector('.gdlg');
  const lobby = !document.querySelector('.myov') && document.querySelector('.home8') && free;
  if (TUT.card) return { key: 'card-' + TUT.card.k, card: TUT.card.html };
  if ([...document.querySelectorAll('.dbox')].some(e => e.getClientRects().length)) return null;   // 대사 보는 중엔 덮개 없음(닫힌 창에 남은 대사 칸은 무시)
  if (st === 1) return lobby ? { key: '1', t: '#td', say: tutSay('① 출석체크', '매일 들르면 뽑기권을 줘요. 날마다 보상이 커지고, 7일째엔 선물 상자! 눌러서 받아 봐요.'), hit: () => tutGo(2) } : null;
  if (st === 2) {
    if (lobby) return { key: '2a', t: '.h8menu a[data-h="draw"]', say: tutSay('② 첫 뽑기', '「뽑기」로 가요. 가입 선물로 받은 뽑기권이 있어요!') };
    if (!fd) return null;
    if (fd.querySelector('.reveal')) { TUT.drew = true; return null; }   // 알 열기·결과 보는 중
    if (TUT.drew) { tutGo(3); TUT.card = { k: 'd1', html: `<h3>🎁 첫 뽑기 보상</h3><img class="rcard" src="${BASE}/images/D1.png" alt="태초의 갈매미맨"><p><b>태초의 갈매미맨</b> ${chip(R.unitDef('D1').attr)}</p><p class="tsub">모든 갈매미의 원형이에요. ${TUT.replay ? '(다시 보기라 새로 주지는 않아요)' : '수비대에 들어왔어요!'} 이 한 마리로 첫 전투에 나가요.</p><button class="big" data-tc="ok">받기</button>` }; return tutPlan(); }
    if (/^#draw/.test(hash) || fd.querySelector('.dgo')) return { key: '2b', t: 'f:.dgo', say: tutSay('② 첫 뽑기', '「1회 뽑기」를 눌러요. 알이 나오면 눌러서 열어요.') };
    return fd.querySelector('#gback') ? { key: '2x', t: 'f:#gback', say: '로비로 돌아가서 「뽑기」로 가요.' } : null;
  }
  if (st === 3) {
    if (lobby) return { key: '3a', t: '.h8menu a[data-h="units"]', say: tutSay('③ 전투 편성', '「수비대」에서 전투에 나갈 갈매미를 정해요.') };
    if (!fd) return null;
    if (fd.querySelector('.btgrid')) {
      const on = fd.querySelector('.btgrid .u.on'), save = fd.querySelector('#save:not([disabled])');
      if (!on) return { key: '3c', t: fd.querySelector('.btgrid .u[data-id="D1"]') ? 'f:.btgrid .u[data-id="D1"]' : 'f:.btgrid .u', say: tutSay('③ 전투 편성', '갈매미를 눌러 단상에 올려요. <b>1마리만 있어도 출전</b>할 수 있어요! 이름 옆 칩은 <b>속성</b>이에요.') };
      if (save) return { key: '3d', t: 'f:#save', say: tutSay('③ 전투 편성', '「편성 저장」을 눌러요.') };
      if (fd.querySelector('.savest.clean')) { tutGo(4); return tutPlan(); }
      return null;
    }
    if (fd.querySelector('.tabs.gseg [data-t="battle"]')) return { key: '3b', t: 'f:.tabs.gseg [data-t="battle"]', say: tutSay('③ 전투 편성', '「전투 편성」을 눌러요.') };
    return fd.querySelector('#gback') ? { key: '3x', t: 'f:#gback', say: '로비로 돌아가서 「수비대」로 가요.' } : null;
  }
  if (st === 4) {
    if (SAVE.cleared.includes('P-1') && !document.querySelector('#arena')) { tutGo(5); return tutPlan(); }
    if (fd) return fd.querySelector('#gback') ? { key: '4x', t: 'f:#gback', say: tutSay('④ 첫 전투', '로비로 돌아가요.') } : null;
    if (lobby) return { key: '4a', t: '#go', say: tutSay('④ 첫 전투', '「플레이」를 눌러요.') };
    if (document.querySelector('.modes') && free) return { key: '4b', t: '#mStory', say: tutSay('④ 첫 전투', '「스토리」에서 챕터를 골라요.') };
    if (document.querySelector('.story') && free) { const p0 = document.querySelector('[data-s="P-1"]'); return { key: p0 ? '4s2' : '4s1', t: p0 ? '[data-s="P-1"]' : '.pcard[data-p="0"]', say: tutSay('④ 첫 전투', p0 ? '첫 스테이지 「P-1」을 눌러요.' : '「프롤로그」를 눌러요.') }; }
    if (!document.querySelector('#arena') || !b) return null;
    if (!modal.hidden && modal.querySelector('[data-a]') && !TUT.augSeen) return { key: '4c', say: tutSay('증강체', '전투를 시작할 때 3개 중 하나를 골라요. 이번 전투에만 쓰는 특별한 힘이에요. 하나 고르고 아래 버튼을 눌러요.'), top: true, light: true };
    if (!modal.hidden) { if (modal.querySelector('[data-a]')) return null; TUT.augSeen = true; return null; }
    if (b.phase === 'over') return !TUT.hpSeen && document.querySelector('#panel [data-r]') ? { key: '4g', t: '#b0', hand: false, say: tutSay('체력', '전투가 끝나도 체력은 그대로예요. 시간이 지나면 차오르고, 치료약으로도 회복해요.') + '<button class="big tok" data-tc="hp">알겠어요</button>' } : null;
    if (document.querySelector('.bmenu')) {
      TUT.augSeen = true;
      if (!TUT.typeSeen) return { key: '4d', card: tutTypeCard() };
      if (!TUT.attacked) return { key: '4e', t: '#bAtk', say: tutSay('④ 공격', '「공격」을 누르면 기술이 나와요.') };
      if (!TUT.hpSeen && b.turn > 1) return { key: '4g', t: '#b0', hand: false, say: tutSay('체력', '전투가 끝나도 체력은 그대로예요. 시간이 지나면 차오르고, 치료약으로도 회복해요.') + '<button class="big tok" data-tc="hp">알겠어요</button>' };
      return null;
    }
    if (document.querySelector('#panel .sk') && !TUT.attacked) { const best = [...document.querySelectorAll('#panel .sk:not([disabled])')].find(x => x.querySelector('.good')); return { key: '4f', t: best ? `#panel .sk[data-k="${best.dataset.k}"]` : '#panel .sk:not([disabled])', say: tutSay('④ 기술 고르기', best ? '<b class="good">▲유리</b> 기술은 1.5배로 세요. 눌러 봐요!' : '기술을 하나 골라요. <b class="good">▲유리</b>가 붙은 기술이 1.5배로 세요.'), hit: () => { TUT.attacked = true; } }; }
    return null;
  }
  if (st === 5) return (lobby || (document.querySelector('.story') && free)) ? { key: 'done', card: `<h3>🎉 신입 연수 끝!</h3><p>이제 자유롭게 출근해요.<br>처음 보는 화면에선 짧게 또 알려 줄게요.</p><p class="tsub">설정에서 언제든 다시 볼 수 있어요.</p><button class="big" data-tc="end">좋아요</button>` } : null;
  return null;
}
// 끝난 뒤: 처음 보는 화면 안내(한 번만, 튜토리얼을 한 유저만)
const TUT_HINT = { craft: tutSay('조합', '재료 갈매미가 모이면 「만들기」로 새 갈매미를 깨워요. 「재료로 섞기」로 직접 섞어 볼 수도 있어요.'), party: tutSay('탐험', '갈매미를 탐험에 보내면 시간이 지난 뒤 보상을 가지고 돌아와요.'), shop: tutSay('상점', '새우gang으로 뽑기권과 아이템을 살 수 있어요.'), modes: tutSay('모드', '왼쪽은 스토리, 오른쪽은 다른 수비조장과 겨루는 PvP예요. PvP는 3마리 편성이 필요해요.'), story: tutSay('스토리', '부를 고르면 스테이지가 나와요. 깬 스테이지도 다시 도전해 레벨을 올릴 수 있어요.') };
function tutHint() {
  if (TUT.replay || !TUT.seen.includes('tut') || document.querySelector('.thint')) return;
  const f = tutFrame(), hash = f ? f.contentWindow.location.hash.slice(1).split(/[=&]/)[0] : '';
  const key = f ? hash : document.querySelector('.modes') ? 'modes' : document.querySelector('.story') && modal.hidden ? 'story' : '';
  if (!TUT_HINT[key] || TUT.seen.includes(key)) return;
  TUT.seen.push(key); myApi('tutorial', { step: 9, seen: key }).catch(() => {});
  const d = document.createElement('div'); d.className = 'thint'; d.innerHTML = `<img src="${APP ? 'intro' : `${BASE}/duel/intro`}/t2_guardian.webp" alt=""><div>${TUT_HINT[key]}</div>`;
  d.onclick = () => d.remove(); document.body.append(d); setTimeout(() => d.remove(), 9000);
}
function tutTick() {
  if (!TUT) return;
  if (TUT.step >= 9 || !document.querySelector('.home8, .myov, .modes, .story, #arena, .dbox')) { document.querySelector('.tut')?.remove(); if (TUT.step >= 9) tutHint(); return; }
  const v = tutPlan(); let o = document.querySelector('.tut');
  if (!v) { o?.remove(); return; }
  const hit = v.t ? tutFind(v.t) : null;
  if (v.t && !hit) { o?.remove(); return; }
  if (!o || o.dataset.key !== v.key) {
    o?.remove(); o = document.createElement('div'); o.className = `tut ${v.card ? 'card' : ''} ${v.light ? 'light' : ''}`; o.dataset.key = v.key;
    o.innerHTML = `${v.card ? '<i class="dm full"></i>' : v.light ? '' : '<i class="dm t"></i><i class="dm b"></i><i class="dm l"></i><i class="dm r"></i>'}${hit ? `<i class="hole"></i>${v.hand === false ? '' : '<i class="hand">👆</i>'}` : ''}
      <div class="ttop"><div class="tdots">${[1, 2, 3, 4, 5].map(i => `<i class="${i < TUT.step ? 'done' : i === TUT.step ? 'on' : ''}"></i>`).join('')}</div></div><button class="tskip">건너뛰기 ›</button>
      ${v.card ? `<div class="tcard">${v.card}</div>` : `<div class="tguide ${v.top ? 'top' : ''}"><img src="${APP ? 'intro' : `${BASE}/duel/intro`}/t2_guardian.webp" alt=""><div class="tsay">${v.say}</div></div>`}`;
    document.body.append(o);
    o.querySelector('.tskip').onclick = async () => { if (await ask('튜토리얼을 건너뛸까요? 설정에서 다시 볼 수 있어요.', { ok: '건너뛰기' })) { tutGo(9); TUT.card = null; o.remove(); } };
    o.querySelectorAll('[data-tc]').forEach(x => x.onclick = () => { const k = x.dataset.tc; if (k === 'type') TUT.typeSeen = true; if (k === 'hp') TUT.hpSeen = true; if (k === 'ok') TUT.card = null; if (k === 'end') { TUT.card = null; tutGo(9); } o.remove(); tutTick(); });
    if (hit && v.hit) hit.el.addEventListener('click', v.hit, { once: true });
  }
  if (hit) {   // 위치는 매번 맞춤(화면이 움직여도)
    const p = 6, r = { x: hit.r.x - p, y: hit.r.y - p, w: hit.r.w + 2 * p, h: hit.r.h + 2 * p }, set = (sel, css) => { const e = o.querySelector(sel); if (e) Object.assign(e.style, css); };
    set('.hole', { left: `${r.x}px`, top: `${r.y}px`, width: `${r.w}px`, height: `${r.h}px` });
    set('.hand', { left: `${r.x + r.w * 0.55}px`, top: `${r.y + r.h * 0.5}px` });
    set('.dm.t', { left: 0, top: 0, width: '100%', height: `${Math.max(0, r.y)}px` }); set('.dm.b', { left: 0, top: `${r.y + r.h}px`, width: '100%', bottom: 0 });
    set('.dm.l', { left: 0, top: `${r.y}px`, width: `${Math.max(0, r.x)}px`, height: `${r.h}px` }); set('.dm.r', { left: `${r.x + r.w}px`, top: `${r.y}px`, right: 0, height: `${r.h}px` });
    const sk = o.querySelector('.tskip'); if (sk) sk.classList.toggle('alt', r.x + r.w > innerWidth - 170 && r.y + r.h > innerHeight - 70);   // 오른쪽 아래가 밝힌 곳이면 왼쪽 아래로
    const g = o.querySelector('.tguide');   // 말풍선은 밝힌 곳과 안 겹치게 위·아래 중 넓은 쪽
    if (g && !v.top) { const below = r.y + r.h / 2 < innerHeight / 2; Object.assign(g.style, below ? { top: `${Math.min(innerHeight - g.offsetHeight - 8, r.y + r.h + 10)}px`, bottom: 'auto' } : { bottom: `${Math.min(innerHeight - g.offsetHeight - 8, innerHeight - r.y + 10)}px`, top: 'auto' });
      g.style.left = `${Math.max(8, Math.min(innerWidth - g.offsetWidth - 8, r.x + r.w / 2 - g.offsetWidth / 2))}px`; }
  }
}
// 프로필(왼쪽 위 이름)을 누르면 작은 메뉴: 내 정보 + 계정·랭크·설정
function profMenu() {
  const old = document.querySelector('.pmenu'); if (old) { old.remove(); return; }
  const login = APP ? 'my.html?next=game' : '/my?next=duel&g=1';
  const m = document.createElement('div'); m.className = 'pmenu'; m.setAttribute('role', 'menu');
  m.innerHTML = ACC ? `<div class="pm-head"><b>${esc(ACC.nickname || '내 계정')}</b><small id="pmRank">랭크 불러오는 중…</small></div>
      <button role="menuitem" data-m="account"><i>${ico('m-account', '👤')}</i>계정 관리</button>
      <button role="menuitem" data-m="rank"><i>${ico('mode-rank', '🏆')}</i>랭크 순위</button>
      <button role="menuitem" data-m="set"><i>${ico('u-settings', '⚙️')}</i>설정</button>`
    : `<div class="pm-head"><b>로그인 전</b><small>로그인하면 진행이 계정에 저장돼요</small></div><a role="menuitem" class="pm-a" href="${login}"><i>${ico('u-profile', '🔑')}</i>로그인</a><button role="menuitem" data-m="set"><i>⚙️</i>설정</button>`;
  document.body.appendChild(m); sfx('tap');
  const r = document.querySelector('#lprof')?.getBoundingClientRect(); if (r) { const z = parseFloat(getComputedStyle(m).zoom) || 1; m.style.top = `${(r.bottom + 12) / z}px`; m.style.left = `${r.left / z}px`; }   // 큰 화면 배율(zoom)만큼 나눔   // 이름표 바로 아래
  const close = () => { m.remove(); removeEventListener('pointerdown', out, true); };
  const out = e => { if (!m.contains(e.target) && !e.target.closest('#lprof')) close(); };
  addEventListener('pointerdown', out, true);
  m.querySelectorAll('[data-m]').forEach(x => x.onclick = () => { close(); ({ account: () => openMy('account'), rank: pvpTop, set: settingsSheet })[x.dataset.m](); });
  if (ACC) myApi('pvp').then(d => { const el = m.querySelector('#pmRank'); if (el) el.innerHTML = `${tierIco(d, d.me.tier)}${esc(d.me.tier)} · ${d.me.point}점 · ${d.me.wins}승 ${d.me.losses}패`; }).catch(() => { const el = m.querySelector('#pmRank'); if (el) el.textContent = ''; });
}
// 스토리 이어하기(다음 스테이지 바로, 팀이 없으면 편성부터)
function continueStory() {
  const st = nextStage();
  if (!st) return storyScreen();
  const go = () => (unlocked(st.id) ? playStage(st) : storyScreen());   // 승진이 필요한 부면 목록으로
  if (!SAVE.team.length) return pickScreen('story', go);
  go();
}
// 플레이 → 모드 고르기(v0.11.0): 왼쪽 캠페인(스토리·모험), 오른쪽 PvP(일반·랭크), 아래 돌아가기
function modeScreen() {
  modal.hidden = true; bgm('title'); muteBtn();
  const st = nextStage(), bg = BG.includes('cabin') ? `background-image:url(${BASE}/duel/bg/cabin.jpg${q})` : BG.includes('harbor') ? `background-image:url(${BASE}/duel/bg/harbor.jpg${q})` : '';   // 배 선실(출항 준비)
  app.innerHTML = `<div class="modes" style="${bg}"><div class="lbg" aria-hidden="true"></div>${ambient('harbor', true)}
    <h1 class="mtitle">게임 모드</h1>
    <div class="mrow">
      <section class="mcard camp"><div class="mhead"><i>${ico('mode-camp', '🗺️')}</i><span><b>캠페인</b><small>이야기를 따라 항구를 지키고, 상위 갈매미를 찾아 모험</small></span></div>
        <button class="mbtn main" id="mStory"><i>${ico('u-story', '📖')}</i><span><b>스토리</b><small>챕터를 골라 이야기 시작 · 기술·승진 관리</small><small>${st ? `다음 이야기 · ${esc(st.id)} ${esc(st.title)}` : '모든 이야기 완료 · 다시 보기'}</small></span></button>
        <button class="mbtn" id="mAdv" disabled><i>${ico('mode-adv', '🧭')}</i><span><b>모험 · 상위 갈매미 포획</b><small>준비 중 · 다음 업데이트</small></span></button>
        </section>
      <section class="mcard pvp"><div class="mhead"><i>${ico('mode-pvp', '⚔️')}</i><span><b>PvP</b><small>다른 수비대의 「전투 편성」 팀과 대결 · 모두 같은 레벨</small></span></div>
        <div class="mrank" id="mRank">${ACC ? '랭크 불러오는 중…' : '로그인하면 PvP를 할 수 있어요'}</div>
        <button class="mbtn" id="mNormal" ${ACC ? '' : 'disabled'}><i>${ico('mode-normal', '🤝')}</i><span><b>일반게임</b><small>점수 없이 가볍게</small></span></button>
        <button class="mbtn" id="mRankGo" ${ACC ? '' : 'disabled'}><i>${ico('mode-rank', '🏆')}</i><span><b>랭크게임</b><small id="mRankSub">이기면 점수가 올라요</small></span></button>
        <button class="mbtn" id="mFree"><i>${ico('mode-free', '🎯')}</i><span><b>연습 · 자유 대결</b><small>갈매미와 레벨을 골라 가볍게 연습해요</small></span></button>
        <div class="mlinks"><button class="mlink" id="mTop" ${ACC ? '' : 'disabled'}>순위표</button></div></section>
    </div>
    <button class="mback ${ICONS.includes('btn-back') ? 'img' : ''}" id="mBack" aria-label="돌아가기">${ICONS.includes('btn-back') ? `<img src="${BASE}/duel/icons/btn-back.png" alt="">` : '‹ 돌아가기'}</button></div>`;
  app.querySelector('#mStory').onclick = () => { PART = null; storyScreen(); };
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
  const done = d.missions.filter(m => m.done).length, tk = ico('u-ticket', '🎟');
  // 출석 칸 상태(v0.12.7): 받음(지난 날) · 오늘 받을 수 있음 · 오늘 받음 · 잠김(아직 안 온 날)
  const dayState = n => n < got || (n === got && !a.today) ? 'got' : n === got && a.today ? 'today-got' : n === got + 1 && !a.today ? 'today' : 'lock';
  const DAYTXT = { got: '받음', 'today-got': '✓ 받음', today: '오늘', lock: '잠김' };
  // 미션 상태: 진행 중 · 달성(보상은 달성 순간 자동 지급, 카톡과 같은 규칙) · 받음
  const mState = m => m.done ? 'done' : m.value >= m.goal ? 'ready' : 'run';
  const MTXT = { done: '✓ 받음', ready: '지급 중', run: '진행 중' };
  modal.innerHTML = `<div class="sheet today"><h2><span class="tdh">${ico('u-attend', '📅')} 출석체크</span><button class="xbtn" id="tdX" aria-label="닫기">✕</button></h2><div class="scols"><div class="scol">
      <div class="att"><div class="att-h"><b>${a.week}주째${a.streak ? ` · ${a.streak}일 연속` : ''}</b><small class="att-note">날이 갈수록, 주가 갈수록 보상이 커져요 · 하루 빠지면 1일째부터</small></div>
        <div class="att-week">${a.rewards.map((t, k) => { const n = k + 1, st = dayState(n); return `<div class="att-d ${st} ${n === 7 ? 'big' : ''}" aria-label="${n}일 ${DAYTXT[st]} 뽑기권 ${t}장"><small>${n}일</small><span class="ai">${n === 7 ? ico('u-gift', '🎁') : tk}</span><em>${t}</em><i class="as">${DAYTXT[st]}</i></div>`; }).join('')}</div>
        <div class="att-now ${a.today ? 'got' : 'ready'}"><span class="ai">${tk}</span><div><small>${a.today ? '내일 받을 보상' : '오늘 받을 보상'}</small><b>${!a.today ? `뽑기권 ${a.next}장` : a.rewards[a.dayInWeek] !== undefined ? `뽑기권 ${a.rewards[a.dayInWeek]}장` : '다음 주 1일째 · 더 크게'}</b></div></div>
        <button class="big wide" id="tdAtt" ${a.today ? 'disabled' : ''}>${a.today ? '✓ 오늘 출석 완료 · 내일 또 와요' : `출석하고 뽑기권 ${a.next}장 받기`}</button></div></div>
    <div class="scol"><div class="msn"><div class="msn-h"><b>오늘의 미션 <em>${done}/${d.missions.length}</em></b><small>달성하면 보상이 바로 들어와요 · 밤 12시 초기화</small></div>
      <div class="msn-list">${d.missions.map(m => { const st = mState(m), pct = Math.min(100, Math.round(m.value / m.goal * 100)); return `<div class="msn-r ${st}"><div class="mt"><b>${esc(m.label)}</b><i class="bar"><i style="width:${pct}%"></i></i></div>
        <span class="mv">${Math.min(m.value, m.goal)}/${m.goal}</span><span class="mr">${tk}<b>${m.tickets}</b></span><span class="ms">${MTXT[st]}</span></div>`; }).join('')}</div></div></div></div></div>`;
  modal.querySelector('#tdX').onclick = () => { modal.hidden = true; menu(); };
  modal.querySelector('#tdAtt').onclick = async () => {
    try { const r = await myApi('attend', {}); sfx('win'); await tell(`출석 완료! ${r.result.streak}일 연속\n🎟 뽑기권 ${r.result.tickets}장${r.result.dayInWeek === 7 ? ' · 이번 주 큰 보상!' : ''}`); todaySheet(); }
    catch (e) { await tell(e.message); }
  };
}
// 「내 갈매미」(뽑기·조합·탐험·상점…)를 페이지 이동 없이 게임 안 창으로(로비는 뒤에 그대로, 닫으면 재화·빨간 점 새로)
function openMy(h, onClose = menu) {   // onClose: 창을 닫은 뒤 갈 화면(기본 로비)
  document.querySelector('.myov')?.remove();
  const url = APP ? `my.html#${h}` : `/my?g=1#${h}`;
  const ov = document.createElement('div'); ov.className = 'myov';
  ov.innerHTML = `<div class="myov-load">불러오는 중…</div><iframe src="${url}" title="내 갈매미"></iframe>`;
  const fr = ov.querySelector('iframe');
  fr.onload = () => ov.classList.add('ready');
  const onMsg = async e => {
    if (e.source !== fr.contentWindow || !['close', 'field-item'].includes(e.data?.gmm)) return;
    const item = e.data.gmm === 'field-item' ? e.data.id : null;
    if (item && !R.data.items[item]?.heal) return;
    removeEventListener('message', onMsg); ov.classList.add('out');
    setTimeout(async () => {
      ov.remove(); onClose();
      if (item) { try { const data = await myApi('story'); ACC.items = data.items; healSheet(item, onClose); } catch (error) { tell(error.message); } }
    }, 180);
  };
  addEventListener('message', onMsg);
  document.body.appendChild(ov); sfx('tap'); muteBtn(false);
}
// 다음에 할 스테이지: 열린 것 중 아직 안 깬 첫 스테이지(없으면 잠긴 다음 스테이지, 다 깼으면 없음)
function nextStage() {
  const all = stages();
  return all.find(s => unlocked(s.id) && !SAVE.cleared.includes(s.id)) ?? all.find(s => !SAVE.cleared.includes(s.id)) ?? null;
}
// 가로 로비: 이야기 칸 양옆에 내 수비대(왼쪽 선두, 오른쪽 둘)
function sideSquad() {
  const team = SAVE.team.length === 3 ? SAVE.team : [];
  if (!team.length) return '';
  const im = (k, i) => { const id = spOf(k), st = SAVE.units[k]?.stage ?? 0; return `<img src="${sprite(id, 'front', st)}" class="${i ? '' : 'flip'}" style="--sz:${bodySz(id, st)};animation-delay:${i * .4}s" alt="">`; };
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

// 편성 후보(v0.16.2): 스토리+로그인 = 내 개체(같은 종류도 마리마다 따로, 기술이 다름) 먼저, 그다음 아직 없는 종류(시험판). 그 밖엔 종류
const pickList = story => {
  if (!story || !ACC) return UNITS.map(u => [u.id, u]);
  const mine = Object.keys(OWN).map(k => [k, UNITS.find(u => u.id === OWN[k])]).filter(([, u]) => u);
  return [...mine, ...UNITS.filter(u => !myKeys(u.id).length).map(u => [u.id, u])];
};
// ── 편성(자유 대결: 레벨 선택 / 스토리: 저장된 레벨) ──
function pickScreen(mode, then, fresh) {   // then: 스토리 팀을 정한 뒤 바로 할 일(이어하기)
  modal.hidden = true; bgm('title');
  const story = mode === 'story';
  if (story && ACC && !fresh) return refreshOwn().then(() => pickScreen(mode, then, true));   // 뽑기·조합으로 바뀐 내 개체 목록부터
  let picked = story ? [...SAVE.team] : freePick;
  app.innerHTML = `<header class="pkh"><button class="back" id="bk">‹ 뒤로</button><h1>${story ? '스토리 팀 편성' : '자유 대결'}</h1>${story && ACC?.battleTeam.length === 3 ? '<button class="ghost pkmine" id="mine">내 전투 편성 불러오기</button>' : ''}</header>
  <p class="hint">${story ? '갈매미 1~3마리를 고르세요' : '갈매미 3마리를 고르세요'}. 처음 고른 갈매미가 먼저 나가요.${story ? ' 시험판이라 최상위 33종을 모두 쓸 수 있어요(실제 게임은 가진 갈매미만).' : ' 상대는 같은 레벨의 무작위 3마리예요.'}</p>
  ${story ? '' : `<div class="bar"><span class="role">레벨</span><div class="seg" id="lv">${[10, 30, 50].map(l => `<button data-l="${l}" class="${l === freeLevel ? 'on' : ''}">${l}</button>`).join('')}</div></div>`}
  ${story && ACC ? `<p class="hint" style="margin-top:-6px"><img class="gi" src="/gull.png" alt=""> 표시는 내가 가진 갈매미예요. 같은 종류도 마리마다 레벨·기술이 따로예요(#번호). 편성하거나 키운 갈매미는 조합·판매 재료로 안 쓰이게 보호돼요.</p>` : ''}
  <div class="grid pickrow">${pickList(story).map(([k, u]) => { const mineK = k[0] === 'o', r = story ? myRec(k, 0) : null, same = mineK && myKeys(u.id).length > 1;
    return `<button class="card" data-id="${k}"><img loading="lazy" src="${BASE}/thumbs/${u.id}.jpg" alt=""><b>${story && mineK ? '<img class="gi" src="/gull.png" alt=""> ' : ''}${esc(u.name)}${same ? ` <em class="no">#${myKeys(u.id).indexOf(k) + 1}</em>` : ''}</b>${chip(u.def.attr)}<span class="role">${ROLE[u.def.role]}${story ? ` · Lv${r.level}` : ''}</span>${story ? `<span class="sks">${slotSkills(R, r).map(x => esc(x.name)).join(' · ')}</span>` : `<span class="syn">${esc(synName(u.id))}</span>`}</button>`; }).join('')}</div>
  <div class="foot"><div class="combo" id="combo"></div><div class="in"><button class="ghost" id="rnd">무작위</button><button class="big" id="go">${story ? '이 팀으로' : '대결 시작'}</button></div></div>`;
  // 가로 화면(v0.14.6): 카드를 한 줄(높은 화면은 두 줄)로 늘어놓고 옆으로 넘김. 카드 크기는 남은 높이에 맞춤
  const g = app.querySelector('.grid.pickrow');
  const fitPick = () => {
    if (!g.isConnected) { removeEventListener('resize', fitPick); return; }
    if (!matchMedia('(orientation:landscape)').matches) { g.style.removeProperty('--cw'); return; }
    const H = g.clientHeight - 14, rows = H > 440 ? 2 : 1, ch = (H - (rows - 1) * 10) / rows;
    g.style.setProperty('--rows', rows); g.style.setProperty('--cw', `${Math.floor(Math.max(90, Math.min(ch - 74, 168)))}px`);
  };
  requestAnimationFrame(fitPick); addEventListener('resize', fitPick);
  g.addEventListener('wheel', e => { if (g.scrollWidth > g.clientWidth && Math.abs(e.deltaY) > Math.abs(e.deltaX)) { g.scrollLeft += e.deltaY; e.preventDefault(); } }, { passive: false });
  const paint = () => {
    app.querySelectorAll('.card').forEach(c => {
      const i = picked.indexOf(c.dataset.id); c.classList.toggle('on', i >= 0);
      c.querySelector('.n')?.remove(); if (i >= 0) c.insertAdjacentHTML('beforeend', `<span class="n">${i + 1}</span>`);
    });
    app.querySelector('#go').disabled = story ? !picked.length : picked.length !== 3;   // 스토리는 1~3마리, 자유 대결은 3마리
    const c = teamCombos(picked), box = app.querySelector('#combo');
    box.innerHTML = c.length ? c.map(x => `🤝 합동기 「${esc(x.skill)}」 · ${x.names.map(esc).join(' + ')}`).join('<br>') : '같은 시너지 2마리를 넣으면 합동기가 생겨요';
  };
  app.querySelectorAll('.card').forEach(c => c.onclick = () => {
    const id = c.dataset.id, i = picked.indexOf(id);
    if (i >= 0) picked.splice(i, 1); else if (picked.length < 3) picked.push(id);
    paint();
  });
  app.querySelectorAll('#lv button').forEach(x => x.onclick = () => { freeLevel = +x.dataset.l; app.querySelectorAll('#lv button').forEach(y => y.classList.toggle('on', y === x)); });
  app.querySelector('#rnd').onclick = () => { picked.splice(0, 3, ...shuffle(pickList(story).map(([k]) => k)).slice(0, 3)); paint(); };
  app.querySelector('#mine')?.addEventListener('click', () => { const ks = []; for (const sp of ACC.battleTeam) ks.push(myKeys(sp).find(k => !ks.includes(k)) ?? sp); picked.splice(0, 3, ...ks); paint(); });
  app.querySelector('#bk').onclick = story ? storyScreen : menu;
  app.querySelector('#go').onclick = () => {
    if (story) {
      SAVE.team = [...picked]; persist();
      const sps = [...new Set(picked.map(spOf))];   // 「내 갈매미」 전투 편성은 종류 3개(PvP)
      if (ACC && sps.length === 3 && sps.every(id => ACC.owned.includes(id)) && sps.join() !== ACC.battleTeam.join())
        myApi('battle-team', { ids: sps }).then(r => { ACC.battleTeam = r.battleTeam; }).catch(() => {});
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
    // 그 레벨까지 배울 기술을 무작위로 1개씩(역할기·보조 속성·예전에 배운 기술 칸과 바꿈. 기본기·고유기는 남겨 둠)
    while (learnChances(R, r) > 0) {
      const o = learnOffers(R, r), used = r.learnUsed ?? 0, rep = slotSkills(R, r).find(k => !k.id.startsWith('basic-') && k.id !== 'sig')?.id;
      learnSkill(R, r, o.length && rep ? o[Math.floor(Math.random() * o.length)].id : null, rep);
      if ((r.learnUsed ?? 0) === used) learnSkill(R, r, null);
    }
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
const teamStageOk = need => !need || (SAVE.team.length > 0 && SAVE.team.every(id => (unitRec(id).stage ?? 0) >= need));
const unlocked = id => { const all = stages(), i = all.findIndex(s => s.id === id); return (i === 0 || (SAVE.cleared.includes(all[i - 1].id) || SAVE.cleared.includes(id) || all.slice(i + 1).some(st => SAVE.cleared.includes(st.id)))) && teamStageOk(all[i].chapter.needStage); };

function storyScreen() {
  modal.hidden = true; bgm('title');
  const team = SAVE.team.map(k => ({ ...UNITS.find(u => u.id === spOf(k)), key: k }));   // 개체(열쇠) + 종류 정보
  // 가로(v0.11.1): 왼쪽 팀(글자 크게, 기술·승진 버튼) · 오른쪽 장 목록(그 안에서만 넘김)
  app.innerHTML = `<div class="story"><header>${PART === null ? '<button class="back" id="bk">‹ 뒤로</button>' : ''}<h1>스토리</h1><small>${SAVE.cleared.length}/${stages().length} 실적</small>${team.length === 3 && ACC && team.some(u => hpFrac(u.key) < 1) ? '<button class="ghost edtop heal" id="heal">🩹 치료약</button>' : ''}<button class="ghost edtop" id="ed">${team.length ? '편성 바꾸기' : '팀 정하기'}</button></header>
  <aside class="steam"><section class="teambar">${team.length ? team.map(u => { const s = unitRec(u.key), need = xpToNext(R, s.level);
      const r = myRec(u.key, 0), n = learnChances(R, r), up = canEvolve(R, r, partsCleared());
      return `<button class="mini" data-u="${u.key}"><img src="${sprite(u.id, 'front', r.stage)}" class="st-${r.stage}" alt=""><b>${esc(u.name)}</b><span>Lv${s.level} ${chip(u.def.attr)}${r.stage ? `<em class="stars">${STARS[r.stage]}</em>` : ''}</span>${hpBar(u.key)}<small class="xpinfo">경험치 ${s.xp}/${need}</small>${up ? '<em class="learn promo">⬆ 승진 가능</em>' : promoSkills(R, r).length ? '<em class="learn promo">🎖️ 전용 기술</em>' : n ? `<em class="learn">✨ 기술 배우기 ${n}</em>` : '<small>기술·승진 보기 ›</small>'}</button>`; }).join('')
      + (team.length ? `<p class="combo">${teamCombos(SAVE.team).map(x => `🤝 합동기 「${esc(x.skill)}」`).join(' · ') || '합동기 없음(같은 시너지 2마리면 생겨요)'}</p>` : '')
    : '<p class="hint" style="margin:0">먼저 팀을 정해 주세요(1~3마리). 위 「팀 정하기」를 눌러요.</p>'}</section></aside>
  <main class="schap ${PART === null ? 'parts' : 'part'}">${PART === null ? partList() : partView(STORY.chapters[PART])}</main></div>`;   // 부 화면: 뒤로(부 목록으로)는 오른쪽 아래
  // 처음엔 부(部) 버튼만 가로로(v0.14.7) → 누르면 그 부의 스테이지 → 위 「‹ 뒤로」는 부 목록으로
  app.querySelector('#bk').onclick = () => { if (PART !== null) { PART = null; storyScreen(); } else menu(); };
  const sch = app.querySelector('.schap');
  if (PART === null) {
    const cur = STORY.chapters.findIndex(c => c.stages.some(s => unlocked(s.id) && !SAVE.cleared.includes(s.id))), cards = [...sch.querySelectorAll('.pcard')];
    if (cur > 0 && cards[cur]) sch.scrollLeft = cards[cur].offsetLeft - sch.offsetLeft - 4;
    sch.addEventListener('wheel', e => { if (sch.scrollWidth > sch.clientWidth && Math.abs(e.deltaY) > Math.abs(e.deltaX)) { sch.scrollLeft += e.deltaY; e.preventDefault(); } }, { passive: false });
    sch.querySelectorAll('[data-p]').forEach(x => x.onclick = () => { PART = +x.dataset.p; storyScreen(); });
  }
  app.querySelector('#ed').onclick = () => pickScreen('story');
  app.querySelector('#heal')?.addEventListener('click', healSheet);
  // 체력이 차오르는 걸 보이게 1분마다 팀 칸만 다시 그림
  clearInterval(storyHpTimer); storyHpTimer = setInterval(() => { if (!app.querySelector('.story')) return clearInterval(storyHpTimer); if (modal.hidden) storyScreen(); }, 60000);
  app.querySelectorAll('[data-u]').forEach(x => x.onclick = () => skillSheet(x.dataset.u));
  dragOrder(app.querySelector('.teambar'));
  app.querySelector('#reset')?.addEventListener('click', async () => {
    if (!(await ask('레벨·경험치·배운 기술·승진·깬 스테이지를 모두 지울까요?', { ok: '처음부터', danger: true }))) return;
    if (ACC) { try { await myApi('story', { data: null }); } catch (e) { await tell(e.message); return; } SAVE = load(null); }
    else { try { localStorage.removeItem(SAVE_KEY); } catch { /* 무시 */ } SAVE = load(); }
    PART = null; storyScreen();
  });
  app.querySelectorAll('[data-s]').forEach(x => x.onclick = () => playStage(stages().find(s => s.id === x.dataset.s)));
}
let PART = null, storyHpTimer = 0;
// 편성대 끌어서 순서 바꾸기(v0.15.4): 칸을 누른 채 움직이면 끌기, 놓은 자리대로 SAVE.team 순서(맨 앞 = 먼저 나감)를 바꾸고 계정에 저장.
// 움직이지 않고 떼면 원래처럼 기술·승진 창. 가로 화면(세로 줄)·세로 화면(가로 줄) 모두 칸 가운데 위치로 순서를 정함
function dragOrder(bar) {
  if (!bar || SAVE.team.length < 2) return;
  const minis = () => [...bar.querySelectorAll('.mini[data-u]')];
  const row = () => { const [a, b] = minis().map(x => x.getBoundingClientRect()); return Math.abs(a.top - b.top) < a.height / 2; };
  for (const el of minis()) el.addEventListener('pointerdown', e => {
    if (e.button > 0) return;
    const sx = e.clientX, sy = e.clientY, horiz = row(); let on = false;
    const pos = x => { const r = x.getBoundingClientRect(); return horiz ? r.left + r.width / 2 : r.top + r.height / 2; };
    const move = ev => {
      const dx = ev.clientX - sx, dy = ev.clientY - sy;
      if (!on && Math.hypot(dx, dy) > 8) { on = true; el.classList.add('drag'); try { el.setPointerCapture(ev.pointerId); } catch { /* 무시 */ } }
      if (!on) return;
      ev.preventDefault();
      el.style.transform = horiz ? `translateX(${dx}px)` : `translateY(${dy}px)`;
      const me = pos(el);
      for (const x of minis()) if (x !== el) { const r = x.getBoundingClientRect(); x.classList.toggle('drop', horiz ? me > r.left && me < r.right : me > r.top && me < r.bottom); }
    };
    const up = () => {
      removeEventListener('pointermove', move); removeEventListener('pointerup', up); removeEventListener('pointercancel', up);
      if (!on) return;
      // 끌고 난 뒤 따라오는 클릭(기술 창)은 막음
      const eat = ev => { ev.stopPropagation(); ev.preventDefault(); };
      addEventListener('click', eat, true); setTimeout(() => removeEventListener('click', eat, true), 350);
      const order = minis().map(x => ({ id: x.dataset.u, p: pos(x) })).sort((a, b) => a.p - b.p).map(x => x.id);
      if (order.join() !== SAVE.team.join()) { SAVE.team = order; persist(); sfx('tap'); }
      storyScreen();
    };
    addEventListener('pointermove', move, { passive: false }); addEventListener('pointerup', up); addEventListener('pointercancel', up);
  });
}
// 전투 밖 치료(v0.14.7): 체력 회복 아이템(치료약·큰 치료약)을 골라 팀 갈매미 하나에 씀. 아이템은 서버에서 1개 빠지고, 체력은 스토리 저장에 반영
function healSheet(initial = null, returnTo = storyScreen) {
  if (!ACC) return tell('로그인하면 치료약을 쓸 수 있어요.');
  if (!SAVE.team.length) return pickScreen('story', () => healSheet(initial, returnTo));
  const healers = Object.entries(R.data.items).filter(([, d]) => d.heal), have = id => ACC.items.find(i => i.id === id)?.count ?? 0;
  let pick = typeof initial === 'string' && have(initial) && R.data.items[initial]?.heal ? initial : healers.find(([id]) => have(id))?.[0] ?? null, to = SAVE.team.find(id => hpFrac(id) < 1) ?? null;
  const draw = () => {
    if (pick && !have(pick)) pick = null;
    if (to && hpFrac(to) >= 1) to = null;
    modal.innerHTML = `<div class="sheet pick heals"><h2>🩹 치료약 쓰기 <small>시간이 지나도 차올라요(0→가득 ${R.data.storyHp?.fullMinutes ?? 60}분)</small></h2>
      ${healers.map(([id, d]) => `<button class="aug ${pick === id ? 'on' : ''}" data-i="${id}" ${have(id) ? '' : 'disabled'}><span class="ic">${ico(id, '🩹')}</span><span class="tx"><b>${esc(d.name)} <em>${have(id)}개</em></b><span>체력 ${d.heal}% 회복${have(id) ? '' : ' · 상점에서 살 수 있어요'}</span></span><span class="ck">${pick === id ? '✓' : ''}</span></button>`).join('')}
      <h2>누구에게</h2>${SAVE.team.map(id => { const u = UNITS.find(x => x.id === spOf(id)), full = hpFrac(id) >= 1;
        return `<button class="aug ${to === id ? 'on' : ''}" data-t="${id}" ${full ? 'disabled' : ''}><img class="ic" src="${sprite(spOf(id), 'front', unitRec(id).stage ?? 0)}" alt=""><span class="tx"><b>${esc(u.name)}</b>${hpBar(id)}</span><span class="ck">${to === id ? '✓' : ''}</span></button>`; }).join('')}
      <p class="sub" id="herr"></p>
      <div class="sw"><button class="ghost" id="hx" style="justify-content:center">닫기</button><button class="big" id="hu" ${pick && to ? '' : 'disabled'}>${pick && to ? `${esc(R.data.items[pick].name)} 쓰기` : '아이템과 갈매미를 골라요'}</button></div></div>`;
    modal.querySelectorAll('[data-i]').forEach(x => x.onclick = () => { pick = x.dataset.i; draw(); });
    modal.querySelectorAll('[data-t]').forEach(x => x.onclick = () => { to = x.dataset.t; draw(); });
    modal.querySelector('#hx').onclick = () => { modal.hidden = true; returnTo(); };
    modal.querySelector('#hu').onclick = async () => {
      modal.querySelector('#hu').disabled = true;
      try {
        const r = await myApi('items/use-field', { id: pick });
        const row = ACC.items.find(i => i.id === pick); if (row) row.count = r.result.left;
        setHp(to, hpFrac(to) + r.result.heal / 100); persist(); sfx('heal');
        if (!have(pick)) pick = healers.find(([id]) => have(id))?.[0] ?? null;
        if (hpFrac(to) >= 1) to = SAVE.team.find(id => hpFrac(id) < 1) ?? null;
        draw();
      } catch (e) { modal.querySelector('#herr').textContent = e.message; modal.querySelector('#hu').disabled = false; }
    };
  };
  modal.onclick = null; modal.className = ''; modal.hidden = false; draw();
}   // 스토리에서 지금 열어 둔 부(번호), null이면 부 목록
const chapInfo = c => { const cl = c.stages.filter(s => SAVE.cleared.includes(s.id)).length; return { sc: c.stages[0].scene || 'harbor', cl, open: c.stages.some(s => unlocked(s.id)), all: cl === c.stages.length }; };
const needLine = c => c.needStage ? `<p class="need ${teamStageOk(c.needStage) ? 'ok' : ''}">${teamStageOk(c.needStage) ? '✓' : '🔒'} 팀 전원 ${c.needStage}단계 승진 필요 (${STARS[c.needStage]})</p>` : '';
function partList() {
  return STORY.chapters.map((c, i) => { const { sc, cl, open, all } = chapInfo(c);
    return `<button class="pcard chead sc-${sc} ${open ? '' : 'locked'} ${all ? 'done' : ''}" data-p="${i}" style="${bgStyle(sc)}"><div class="in"><h2>${esc(c.title)}</h2><span class="pn">${all ? '✓ 완료' : open ? `${cl}/${c.stages.length}` : '🔒 잠김'}</span>${c.intro ? `<p>${esc(c.intro)}</p>` : ''}${needLine(c)}<span class="pgo">${open ? (all ? '다시 보기 ›' : '들어가기 ›') : '미리 보기 ›'}</span></div></button>`; }).join('')
    + `<section class="send"><p class="hint">진 대결도 경험치를 조금 받아요. 깬 스테이지는 다시 도전해 레벨을 올릴 수 있어요.</p>
  <p class="hint" id="savewarn" style="color:#ff8a7a" ${saveFail ? '' : 'hidden'}>⚠ 계정에 저장하지 못했어요. 인터넷이 연결되면 다음 진행 때 다시 저장해요.</p>
  <button class="ghost" id="reset" style="width:100%">스토리 진행 처음부터(${ACC ? '계정' : '이 브라우저'})</button></section>`;
}
function partView(c) {
  const team3 = SAVE.team.length > 0, { sc, cl, open, all } = chapInfo(c);
  return `<div class="chead pv sc-${sc}" style="${bgStyle(sc)}"><div class="in"><h2>${esc(c.title)}</h2><span class="pn">${all ? '✓ 완료' : `${cl}/${c.stages.length}`}</span>${c.intro ? `<p>${esc(c.intro)}</p>` : ''}${needLine(c)}</div></div>
    <div class="stl ${open ? '' : 'pre'}">${open ? '' : '<p class="stpre">미리 보기 · 앞 부를 깨면 열려요</p>'}${c.stages.map(s => { const done = SAVE.cleared.includes(s.id), op = unlocked(s.id), boss = s.enemy.some(e => R.villains.get(e.id).boss);
      return `<button class="stage ${done ? 'done' : ''}" data-s="${s.id}" ${op && team3 ? '' : 'disabled'}><span class="no">${s.id}</span><span class="tt">${esc(s.title)}${boss ? ' <em>보스</em>' : ''}<small>${s.enemy.map(e => `${esc(R.villains.get(e.id).name)} Lv${e.level}`).join(' · ')}</small></span><span class="mk">${done ? '✓' : op ? '도전' : '잠김'}</span></button>`; }).join('')}</div>
    <div class="pfoot"><button class="ghost pback" id="bk">‹ 뒤로</button></div>`;   // 부 목록으로: 오른쪽 아래
}

// 기술 창(v0.15.9): 한 화면(세로 넘김 없음). 왼쪽 = 갈매미·능력치·승진, 오른쪽 = 기술 4칸(2×2).
// 배울 기회가 있으면 「새 기술 배우기」 → 팝업: ① 배울 기술(그 레벨에 새로 열린 것) → ② 잊을 칸(4칸 중 하나) → 배우기. 한 기회에 최대 2개
function skillSheet(key, open = null) {   // open: 'promo'면 승진 전용 기술 팝업부터   // key: 개체 열쇠(o번호) 또는 종류 id
  const id = spOf(key), u = UNITS.find(x => x.id === id), def = R.unitDef(id), rec = myRec(key, 0), st = statsOf(R, rec);
  const kindOf = k => k.id.startsWith('basic-') ? '기본기' : k.id === 'sig' ? '전용 기술' : k.id.startsWith('role-') ? '역할기' : k.id.startsWith('sub-') ? '보조 속성' : '일반 기술';
  const icon = (k, attr) => glyphOf(k.name) ?? GLYPH[attr] ?? '✨';
  const meta = k => `${k.power ? `${esc(R.attrName(k.attr ?? def.attr))} · ${catOf(k, st)} · 위력 ${k.power}${k.hits > 1 ? `×${k.hits}` : ''}` : '보조 기술'}${k.cd ? ` · 재사용 ${k.cd}턴` : ''}`;
  const skc = (k, data = '', on = false, tag = '') => `<button class="skc ${on ? 'on' : ''}" ${data} ${data ? '' : 'tabindex="-1"'}><span class="ic">${icon(k, k.attr ?? def.attr)}</span><span class="tx"><em>${tag || kindOf(k)}</em><b>${esc(k.name)}</b><small>${meta(k)}</small><span class="ds">${esc(restDesc(k) || k.desc || '')}</span></span></button>`;
  let pop = null, pick = null, rep = null, via = 'learn';   // pop: null | 'learn' | 'promo' | 'forget', via: 잊을 칸을 고른 뒤 무엇으로 배우나
  const draw = () => {
    const mine = slotSkills(R, rec), promos = promoSkills(R, rec), offers = via === 'promo' ? promos : learnOffers(R, rec), left = learnLeft(R, rec), per = R.data.learn.perChance ?? 1;
    const tier = R.data.learn.levels[rec.learnUsed ?? 0], nextLv = R.data.learn.levels.find(l => l > rec.level), full = mine.length >= R.data.learn.slots;
    const promoBtn = promos.length ? `<button class="big promo-b" id="openP">🎖️ 승진 전용 기술</button>` : '';
    const learnBtn = learnOffers(R, rec).length ? `<button class="big" id="openL">✨ 새 기술 배우기 <small>Lv${tier} · ${left}개 더</small></button>` : `<p class="sub">${nextLv ? `다음 배울 기회: Lv${nextLv}` : '모든 배울 기회를 썼어요'}</p>`;
    const popHtml = !pop ? '' : `<div class="skpop" id="pop"><div class="pbox">
      ${pop === 'promo' ? `<h2>🎖️ 승진 전용 기술 <small>${rec.stage}단계까지 열린 ${esc(u.name)} 전용 기술 · 배울 기회는 안 써요</small></h2>
        <div class="skg off">${promos.map(k => skc(k, `data-p="${k.id}"`, pick === k.id, '승진 전용')).join('')}</div>
        <div class="sw"><button class="ghost" id="popX2">나중에</button><button class="big" id="nextL" ${pick ? '' : 'disabled'}>${pick ? (full ? '다음: 잊을 기술 고르기 ›' : `「${esc(promos.find(o => o.id === pick).name)}」 배우기`) : '배울 기술을 골라요'}</button></div>`
      : pop === 'learn' ? `<h2>✨ 새 기술 배우기 <small>Lv${tier}에 새로 열린 기술 · 이번에 ${left}개 더(최대 ${per}개)</small></h2>
        <div class="skg off">${offers.map(k => skc(k, `data-p="${k.id}"`, pick === k.id, '새 기술')).join('')}</div>
        <div class="sw"><button class="ghost" id="skipL">${left < per ? '그만 배우기' : '이번엔 안 배우기'}</button><button class="big" id="nextL" ${pick ? '' : 'disabled'}>${pick ? (full ? '다음: 잊을 기술 고르기 ›' : `「${esc(offers.find(o => o.id === pick).name)}」 배우기`) : '배울 기술을 골라요'}</button></div>`
      : `<h2>잊을 기술 고르기 <small>「${esc(offers.find(o => o.id === pick)?.name ?? '')}」 대신 잊을 칸 하나</small></h2>
        <div class="skg">${mine.map(k => skc(k, `data-r="${k.id}"`, rep === k.id)).join('')}</div>
        <div class="sw"><button class="ghost" id="backL">‹ 다시 고르기</button><button class="big" id="doL" ${rep ? '' : 'disabled'}>${rep ? `「${esc(mine.find(k => k.id === rep).name)}」 잊고 배우기` : '잊을 기술을 골라요'}</button></div>`}
      <button class="pclose" id="popX" aria-label="닫기">✕</button></div></div>`;
    modal.innerHTML = `<div class="sheet skv">
      <aside class="skl"><div class="who-head"><img src="${sprite(id, 'front', rec.stage)}" class="st-${rec.stage}" alt=""><div><b>${esc(u.name)} ${rec.stage ? `<em class="stars">${STARS[rec.stage]}</em>` : ''}</b><small>Lv${rec.level} · ${rec.stage ? `${rec.stage}단계 승진` : '원형'} · ${esc(R.attrName(def.attr))} · ${ROLE[def.role]}${synName(id) ? ` · ${esc(synName(id))}` : ''}</small></div></div>
        ${statGrid(st)}
        ${R.recipeValue(id) !== undefined ? `<p class="sub">조합 가치 · 흔함 ${R.recipeValue(id)}마리 · 공격기 위력 +${R.recipeBonus(id)}%</p>` : ''}
        <div class="promo"><b>승진</b> <small>${rec.stage >= 3 ? '최고 단계' : `다음: ${promoNeed(rec)}`}</small>${rec.evoAugs.length ? `<div class="evo">${rec.evoAugs.map(a => { const e = R.evoAug.get(a); return `<span title="${esc(e.desc)}">🎖️ ${esc(e.name)}</span>`; }).join('')}</div>` : ''}
        ${canEvolve(R, rec, partsCleared()) ? `<button class="big wide" id="promo">⬆ ${rec.stage + 1}단계 승진하기</button>` : ''}</div></aside>
      <main class="skr"><div class="skh"><h2>기술 <small>${mine.length}/${R.data.learn.slots}칸 · 모두 바꿀 수 있어요</small></h2><div class="skbtn">${promoBtn}${learnBtn}</div></div>
        <div class="skg">${mine.map(k => skc(k)).join('')}${Array.from({ length: Math.max(0, R.data.learn.slots - mine.length) }, () => `<div class="skc empty"><span class="ic">＋</span><span class="tx"><em>빈칸</em><b>비어 있어요</b><small>${nextLv || offers.length ? '레벨업·승진으로 배워서 채워요' : '전용 기술·새 기술로 채워요'}</small></span></div>`).join('')}</div></main>
      <button class="pclose" id="closeL" aria-label="닫기">✕</button>${popHtml}</div>`;
    const $ = s => modal.querySelector(s);
    $('#closeL').onclick = () => { modal.hidden = true; modal.className = ''; storyScreen(); };
    $('#promo')?.addEventListener('click', () => promoSheet(key));
    $('#openL')?.addEventListener('click', () => { pop = 'learn'; via = 'learn'; pick = null; rep = null; draw(); });
    $('#openP')?.addEventListener('click', () => { pop = 'promo'; via = 'promo'; pick = null; rep = null; draw(); });
    $('#popX2')?.addEventListener('click', () => { pop = null; via = 'learn'; draw(); });
    $('#popX')?.addEventListener('click', () => { pop = null; draw(); });
    modal.querySelectorAll('[data-p]').forEach(x => x.onclick = () => { pick = x.dataset.p; draw(); });
    modal.querySelectorAll('[data-r]').forEach(x => x.onclick = () => { rep = x.dataset.r; draw(); });
    const save = () => { keep(rec); persist(); pick = null; rep = null; const was = via; via = 'learn'; pop = was === 'learn' && learnOffers(R, rec).length ? 'learn' : null; draw(); };
    const doLearn = () => (via === 'promo' ? learnPromo(R, rec, pick, rep ?? undefined) : learnSkill(R, rec, pick, rep ?? undefined));
    $('#nextL')?.addEventListener('click', () => { if (full) { pop = 'forget'; rep = null; draw(); } else { doLearn(); sfx('aug'); save(); } });
    $('#backL')?.addEventListener('click', () => { pop = via; draw(); });
    $('#doL')?.addEventListener('click', () => { doLearn(); sfx('aug'); save(); });
    $('#skipL')?.addEventListener('click', async () => { if (await ask(left < per ? `이번 기회를 끝낼까요? 남은 ${left}개는 다시 받을 수 없어요.` : '이번 기회를 건너뛸까요? 다시 받을 수 없어요.', { ok: left < per ? '끝내기' : '건너뛰기' })) { learnSkill(R, rec, null); save(); } });
  };
  if (open === 'promo' && promoSkills(R, rec).length) { pop = 'promo'; via = 'promo'; }
  modal.onclick = null; modal.className = 'full'; modal.hidden = false; draw();
}

// 승진: 직무 표창 3개 중 1개 → 단계 +1(능력치 배율 상승). 단계별 그림이 생기기 전까지는 별·빛으로 표시
function promoSheet(key) {
  const id = spOf(key), rec = myRec(key, 0), u = UNITS.find(x => x.id === id), choices = promoChoices(rec);
  let pick = null;
  const draw = () => {
    modal.innerHTML = `<div class="sheet pick"><h2>⬆ ${esc(u.name)} ${rec.stage + 1}단계 승진</h2>
      <p class="sub">직무 표창을 하나 골라요. 같은 원형도 표창에 따라 전문 분야가 갈려요. (한 번 고르면 바꿀 수 없어요)</p>
      ${choices.map(e => `<button class="aug ${pick === e.id ? 'on' : ''}" data-e="${e.id}"><span class="ic">🎖️</span><span class="tx"><b>${esc(e.name)}</b><span>${esc(e.desc)}</span></span><span class="ck">${pick === e.id ? '✓' : ''}</span></button>`).join('')}
      <div class="sw"><button class="ghost" id="cancelP" style="justify-content:center">나중에</button><button class="big" id="doP" ${pick ? '' : 'disabled'}>${pick ? `「${esc(R.evoAug.get(pick).name)}」 받고 승진` : '표창을 골라요'}</button></div></div>`;
    modal.querySelectorAll('[data-e]').forEach(x => x.onclick = () => { pick = x.dataset.e; draw(); });
    modal.querySelector('#cancelP').onclick = () => skillSheet(key);
    modal.querySelector('#doP').onclick = async () => {
      rec.stage += 1; rec.evoAugs.push(pick); keep(rec); persist();
      modal.innerHTML = `<div class="promo-fx"><img src="${sprite(id, 'front', rec.stage)}" class="st-${rec.stage}" alt=""><b>승진!</b><span>${esc(u.name)} · ${rec.stage}단계 ${STARS[rec.stage]}</span><small>직무 표창 「${esc(R.evoAug.get(pick).name)}」</small></div>`;
      modal.querySelector('.promo-fx').animate([{ opacity: 0, transform: 'scale(.8)' }, { opacity: 1, transform: 'scale(1.04)', offset: .4 }, { opacity: 1, transform: 'none' }], { duration: 700, fill: 'forwards' });
      await sleep(1800); skillSheet(key, 'promo');   // 승진 전용 기술 팝업으로
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
    const leadKey = SAVE.team[0], lead = UNITS.find(u => u.id === spOf(leadKey));
    let i = 0, typing = null, full = '';
    const foeId = lines.find(l => /^[VDTEIN]\d+$/.test(l.who))?.who;
    modal.className = 'scene-on';
    const UI = APP ? 'intro' : `${BASE}/duel/intro`, deep = (opt.scene || 'harbor') === 'deep';
    modal.innerHTML = `<div class="scene sc-${opt.scene || 'harbor'} ${deep ? 'bg2' : ''}">${deep ? `<div class="kb" style="background:url(${BASE}/duel/bg/deepstory.webp${q}) center/cover"></div>` : bgStyle(opt.scene || 'harbor') ? `<div class="kb" style="${bgStyle(opt.scene || 'harbor')}"></div>` : ''}${ambient(opt.scene || 'harbor', !!bgStyle(opt.scene || 'harbor'))}
      ${opt.title ? `<div class="place">${esc(opt.title)}</div>` : ''}
      <img class="por me" id="pMe" src="${sprite(lead.id, 'front', SAVE.units[leadKey]?.stage ?? 0)}" style="--sz:${bodySz(lead.id, SAVE.units[leadKey]?.stage ?? 0)}" alt="">
      <img class="por foe" id="pFoe" alt="" ${foeId ? '' : 'hidden'}>
      <div class="dbox"><div class="who" id="who"></div><p class="say" id="say"></p>
        <div class="tk"><span id="pg"></span><button class="dnext" id="dnext" aria-label="다음"><img src="${UI}/b3_button_next.webp" alt=""><img class="lb" src="${UI}/b3_label_next.webp" alt="" style="--x:45.5%;--y:50%;--w:34"></button></div></div>
      <button class="dskip" id="skip" aria-label="건너뛰기"><img src="${UI}/b3_button_skip.webp" alt=""><img class="lb" src="${UI}/b3_label_skip.webp" alt="" style="--x:50%;--y:50%;--w:56"></button>
      ${opt.title ? `<div class="titlecard" id="card"><small>${esc(opt.sub || '')}</small><b>${esc(opt.title)}</b></div>` : ''}</div>`;
    const $ = id => modal.querySelector(`#${id}`);
    // 첫 줄이 해설이어도 상대(악당)를 미리 세워 둠(v0.15.4 시안: 해설 중에도 두 인물이 보임)
    if (foeId && /^V\d+$/.test(foeId)) { const p = $('pFoe'), v = R.villains.get(foeId), src = sprite(foeId, 'front'); p.src = src; p.dataset.src = src; p.style.setProperty('--sz', 1); p.classList.toggle('bossp', !!v?.boss); setTint(p, foeId); }
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
      $('pg').textContent = `${i + 1} / ${lines.length}`;
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
  if (SAVE.team.some(id => hpNow(id) < 1)) return fight(st);   // 체력 0이 있으면 대사 없이 바로 안내(fight가 막음)
  await talk(st.before, { ...sceneTitle(st), tip: st.hint });
  fight(st);
}
function fight(st) {
  sceneNow = st.scene || 'harbor';
  const foe = st.enemy.map((e, i) => { const v = R.villains.get(e.id); return { uid: `b${i}`, id: e.id, name: v.name, tier: 'LIMITED', level: e.level, xp: 0, stage: e.stage ?? st.chapter.needStage ?? 0, evoAugs: [] }; });
  const down = SAVE.team.filter(id => hpNow(id) < 1);
  if (down.length) { tell(`체력이 0인 갈매미는 나갈 수 없어요: ${down.map(id => UNITS.find(u => u.id === spOf(id)).name).join(', ')}. 시간이 지나면 회복되고, 스토리 화면에서 치료약을 쓸 수도 있어요.`).then(storyScreen); return; }
  const mine = SAVE.team.map(myRec);
  fightId = null;
  if (ACC) myApi('story/battle', {}).then(r => { fightId = r.result.battle; ACC.items = r.result.items; if (b && b.phase === 'action' && !busy) actionPanel(); }).catch(() => {});
  const saveHp = () => { b.sides[0].fighters.forEach(f => setHp(f.rec.key ?? f.rec.id, f.hp / f.maxHp)); persist(); };
  startBattle(mine, foe, won => {
    saveHp();
    const avg = st.enemy.reduce((a, e) => a + e.level, 0) / st.enemy.length, xp = battleXp(R, avg, won);
    const ups = [];
    for (const r of mine) {
      const before = r.level; addXp(R, r, xp); keep(r); if (r.level > before) setHp(r.key ?? r.id, 1);
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
  }, st.hint, { retry: () => fight(st), back: storyScreen, save: saveHp });
  b.sides[0].fighters.forEach(f => { f.hp = Math.max(1, Math.min(f.maxHp, Math.floor(f.maxHp * hpFrac(f.rec.key ?? f.rec.id)))); });   // 남은 체력으로 시작
  view = snap(); render();
}

// ── 대결 ──
let fleeTo = null; // 도망치기: { retry, back }
let sceneNow = '';
function startBattle(mine, foe, end, hint, flee, seed) {   // seed: PvP는 서버가 준 시드(서버가 같은 대결을 다시 돌려 확인)
  onEnd = end; hintNow = hint || ''; fleeTo = flee; if (seed === undefined) pvp = null;
  b = createBattle(R, mine, foe, seed ?? ((Math.random() * 2 ** 31) | 0));
  const boss = foe.some(f => R.villains.get(f.id)?.boss);
  const fieldBg = sceneNow === 'deep' ? 'deepbattleb.webp' : BG.includes(sceneNow || 'harbor') ? `${sceneNow || 'harbor'}.jpg` : null;
  bgm(boss ? 'boss' : 'battle');
  app.innerHTML = `<div id="arena" class="sc-${sceneNow || 'harbor'}">${ambient(sceneNow || 'harbor', !!fieldBg)}<div class="ground"></div><div class="haze"></div><div class="plat foe"></div><div class="plat me"></div>
    <img class="mon foe ${boss ? 'boss' : ''}" id="m1" alt=""><img class="mon me" id="m0" alt="">
    <div class="box foe" id="b1"></div><div class="box me" id="b0"></div><div id="fx"></div></div>
    <div id="msg" role="log" aria-live="polite"></div><div id="panel"></div>`;
  if (fieldBg) {   // 시안처럼 장면 일러스트를 명령판 뒤까지 연결합니다. 해저는 전투 전용 그림을 사용합니다.
    app.querySelector('#arena').classList.add('bg2');
    app.insertAdjacentHTML('afterbegin', `<div class="bbg" aria-hidden="true" style="background-image:url(${BASE}/duel/bg/${fieldBg}${q})"></div>`);
  }
  for (const [k, side] of [[0, 'back'], [1, 'front']]) for (const f of b.sides[k].fighters) new Image().src = sprite(f.rec.id, side, f.rec.stage);   // 교체 때 바로 보이게 양 팀 그림을 미리 받아 둠(v0.15.9)
  view = snap();
  render(); say(hint ? `💡 ${hint}` : b.log.at(-1));
  flow();
}
const snap = () => b.sides.map(s => ({ active: s.active, hp: s.fighters.map(f => f.hp), down: s.fighters.map(f => f.hp <= 0) }));
const shown = s => b.sides[s].fighters[view[s].active];
// 전투 기록(v0.15.5): 지난 줄은 흐리게 한 줄, 지금 줄은 「나/상대」 표시와 함께. [내 팀]/[상대] 머리말이나 「이름의 …」로 누구 쪽인지 앎
const logTag = t => {
  let m = t.match(/^\[(내 팀|상대)\] (.*)$/s), side = null, rest = t;
  if (m) { side = m[1] === '상대' ? 1 : 0; rest = m[2]; }
  else if (b && view) { try { side = [0, 1].find(s => t.startsWith(`${shown(s).name}의 `) || t.startsWith(`${shown(s).name}(이)`) || t.startsWith(`${shown(s).name}이 `) || t.startsWith(`${shown(s).name}가 `)) ?? null; } catch { side = null; } }
  return `${side === null ? '' : `<em class="tg ${side ? 'foe' : 'me'}">${side ? '상대' : '나'}</em>`}${esc(rest)}`;
};
const say = t => {
  const el = document.getElementById('msg'); if (!el) return;
  const prev = el.querySelector('.lc')?.dataset.t ?? '';
  el.innerHTML = `<div class="loglines">${prev && prev !== t ? `<span class="lp">${logTag(prev)}</span>` : ''}<span class="lc" data-t="${esc(t || '')}">${logTag(t || '')}</span></div>`;
};
// 전투 캐릭터 자리(v0.15.4, 가로 화면): 그림의 투명 여백을 뺀 「실제 그림 영역」을 재서, 정해 둔 칸 안에 비율 그대로 맞춤.
// 같은 너비를 모두에게 주지 않고 그림마다 실제 영역으로 계산 → 아군은 날개·발이 화면 안에, 상대는 조금 크게. 발(그림 아래)은 칸 바닥 가운데
const OPAQ = new Map();
function opaqueBox(img) {
  const key = img.currentSrc || img.src;
  if (OPAQ.has(key)) return OPAQ.get(key);
  let r = { x0: 0, y0: 0, x1: 1, y1: 1 };
  try {
    const W = img.naturalWidth, H = img.naturalHeight, k = Math.min(1, 160 / Math.max(W, H)), w = Math.max(1, Math.round(W * k)), h = Math.max(1, Math.round(H * k));
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    const g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(img, 0, 0, w, h);
    const d = g.getImageData(0, 0, w, h).data; let x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (d[(y * w + x) * 4 + 3] > 24) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    if (x1 >= x0) r = { x0: x0 / w, y0: y0 / h, x1: (x1 + 1) / w, y1: (y1 + 1) / h };
  } catch { /* 그림을 못 읽으면 그림 전체를 영역으로 */ }
  OPAQ.set(key, r); return r;
}
// 칸: 전장(#arena) 크기에 대한 비율 [왼, 위, 오른, 아래]. 아군 = 왼쪽 아래(내 정보창 왼쪽), 상대 = 오른쪽 가운데(상대 정보창·턴 표시 아래)
const MON_BOX = { 0: [0.04, 0.36, 0.44, 0.91], 1: [0.55, 0.23, 0.93, 0.65] };
function placeMon(s) {
  const img = document.getElementById(`m${s}`), arena = document.getElementById('arena');
  if (!img || !arena) return;
  const st = img.style, keys = ['left', 'top', 'width', 'height', 'right', 'bottom', 'maxWidth', 'maxHeight'];
  const plat = arena.querySelector(`.plat.${s ? 'foe' : 'me'}`);
  if (!matchMedia('(orientation:landscape)').matches || !img.naturalWidth) { for (const k of keys) { st[k] = ''; if (plat) plat.style[k] = ''; } return; }
  const A = arena.getBoundingClientRect(), [l, t, r, bt] = MON_BOX[s], o = opaqueBox(img);
  // 몸집(--sz)은 0.8~1배로만 반영, 보스가 아닌 상대는 0.9배
  const k = Math.max(0.8, Math.min(1, parseFloat(st.getPropertyValue('--sz')) || 1)) * (s === 1 && !img.classList.contains('boss') ? 0.9 : 1);
  // 상대 칸 위쪽은 턴 표시(🔍) 아래로: 낮은 화면에서도 겹치지 않게
  const pill = s === 1 && arena.querySelector('.finfo')?.getBoundingClientRect(), gear = s === 1 && document.querySelector('.mute')?.getBoundingClientRect();
  const top = Math.max(t * A.height, pill && pill.height ? pill.bottom - A.top + 8 : 0, gear && gear.height && gear.left < A.right ? gear.bottom - A.top + 6 : 0);
  // 큰 글자·긴 증강체로 내 상태창이 위로 커져도 상대 그림이 그 아래로 들어가지 않게 한다.
  const meBox = s === 1 && document.getElementById('b0')?.getBoundingClientRect();
  const by = Math.min(bt * A.height, meBox && meBox.height ? meBox.top - A.top - A.height * .02 : Infinity);
  const RW = (r - l) * A.width * k, RH = Math.max(1, by - top) * k, a = ((o.x1 - o.x0) * img.naturalWidth) / ((o.y1 - o.y0) * img.naturalHeight);
  const vw = Math.min(RW, RH * a), vh = vw / a, ew = vw / (o.x1 - o.x0), eh = vh / (o.y1 - o.y0), cx = (l + r) / 2 * A.width;
  Object.assign(st, { left: `${cx - (o.x0 + o.x1) / 2 * ew}px`, top: `${by - o.y1 * eh}px`, width: `${ew}px`, height: `${eh}px`, right: 'auto', bottom: 'auto', maxWidth: 'none', maxHeight: 'none' });
  if (plat) Object.assign(plat.style, { left: `${cx - vw * 0.55}px`, width: `${vw * 1.1}px`, top: `${by - vw * 0.08}px`, height: `${vw * 0.16}px`, right: 'auto', bottom: 'auto' });
}
addEventListener('resize', () => { if (document.getElementById('arena')) { placeMon(0); placeMon(1); } });

function render() {
  closeAugmentInfo?.();
  for (const s of [0, 1]) {
    const f = shown(s), img = document.getElementById(`m${s}`), src = sprite(f.rec.id, s === 0 ? 'back' : 'front', f.rec.stage);
    // 그림이 바뀔 때(교체): 새 그림을 다 받을 때까지 숨김. 안 그러면 브라우저가 받는 동안 이전 갈매미 그림을 그대로 보여 줌(v0.15.9)
    if (img.dataset.src !== src) { img.style.visibility = 'hidden'; img.onload = () => { if (img.dataset.src === src) { img.style.visibility = ''; placeMon(s); } }; img.onerror = () => { img.style.visibility = ''; }; img.src = src; img.dataset.src = src; if (img.complete && img.naturalWidth) img.style.visibility = ''; setTint(img, f.rec.id); img.style.setProperty('--sz', f.rec.id.startsWith('V') ? 1 : bodySz(f.rec.id, f.rec.stage)); }
    img.style.opacity = view[s].down[view[s].active] ? 0 : 1;
    const hp = view[s].hp[view[s].active], pct = Math.max(0, hp / f.maxHp * 100);
    const col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
    const st = Object.keys(f.status).map(k => `<span class="st">${({ burn: '화상', freeze: '빙결', sleep: '잠', bind: '묶임', rage: '폭주' })[k]}</span>`).join('');
    const augs = b.sides[s].augs.map(id => `<button class="aug-chip ${augKind(id)}" data-aug-info="${id}" aria-label="${esc(R.battleAug.get(id).name)} 설명">${ico(id, AUG_ICON[id]?.[0] ?? '✨')}<span>${esc(R.battleAug.get(id).name)}</span></button>`).join('');
    const box = document.getElementById(`b${s}`), downNow = view[s].down[view[s].active];
    box.classList.toggle('down', downNow);
    img.classList.remove('st-1', 'st-2', 'st-3'); if (f.rec.stage && !tintOf(f.rec.id)) img.classList.add(`st-${f.rec.stage}`);
    box.innerHTML = `<div class="nm"><span>${esc(f.name)}${f.rec.stage ? ` <em class="stars">${STARS[f.rec.stage]}</em>` : ''}</span>${downNow ? '<em class="ko">전투불가</em>' : `<em>Lv${f.rec.level}</em>`}</div>
      <div class="hpr"><div class="hpb" role="meter" aria-label="${esc(f.name)} 체력" aria-valuemin="0" aria-valuemax="${f.maxHp}" aria-valuenow="${Math.max(0, Math.round(hp))}"><i style="width:${pct}%;background:${col}"></i></div></div>
      <div class="row"><span class="chips">${chip(f.attr)}${f.extraAttr ? chip(f.extraAttr) : ''}${st}</span><small class="hpn">${s === 0 ? `${Math.max(0, Math.round(hp))}/${f.maxHp}` : `${Math.ceil(pct)}%`}</small></div>
      <div class="dots" aria-label="남은 갈매미 ${view[s].down.filter(d => !d).length}마리">${view[s].down.map((d, i) => `<i class="${d ? 'down' : ''}" title="${esc(b.sides[s].fighters[i].name)}"></i>`).join('')}</div>
      ${augs ? `<div class="augs">${augs}</div>` : ''}${s === 1 ? `<button class="finfo" ${b.turn > 1 ? '' : 'disabled'} title="${b.turn > 1 ? '상대 정보' : '한 턴이 지나면 볼 수 있어요'}">🔍 ${b.turn > 1 ? '정보' : '1턴 뒤'}</button>` : ''}`;
    if (s === 1) box.querySelector('.finfo').onclick = e => { e.stopPropagation(); if (b.turn > 1 && b.phase === 'action' && !busy) foeInfo(); };
    box.querySelectorAll('[data-aug-info]').forEach(button => button.onclick = e => { e.stopPropagation(); augmentInfo(button, button.dataset.augInfo); });
  }
  // 상태창 높이가 바뀐 뒤 위치를 계산해야 큰 글자·여러 증강체도 반영된다.
  for (const s of [0, 1]) placeMon(s);
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
const AUG_KINDS = {
  attack: ['focus', 'dual', 'first', 'lastone', 'peanut', 'sharp', 'overtime', 'drain', 'pierce'],
  defense: ['weakguard', 'counter', 'learn', 'bulwark', 'firstaid', 'thick', 'thorns', 'lunch'],
};
const augKind = id => Object.entries(AUG_KINDS).find(([, ids]) => ids.includes(id))?.[0] ?? 'utility';
const augKindName = id => ({ attack: '공격', defense: '수비', utility: '유틸' })[augKind(id)];
let closeAugmentInfo = null;
function augmentInfo(anchor, id) {
  closeAugmentInfo?.();
  const a = R.battleAug.get(id); if (!a) return;
  const tip = document.createElement('div'); tip.className = `augment-info ${augKind(id)}`;
  tip.setAttribute('role', 'dialog'); tip.setAttribute('aria-label', `${a.name} 설명`);
  const category = AUG_ICON[id]?.[1] ?? '';
  tip.innerHTML = `<button class="aug-tip-close" aria-label="닫기">✕</button><small>${augKindName(id)}${category && category !== augKindName(id) ? ` · ${esc(category)}` : ''}</small><b>${esc(a.name)}</b><p>${esc(a.desc)}</p>`;
  document.body.append(tip);
  const r = anchor.getBoundingClientRect(), width = tip.offsetWidth, height = tip.offsetHeight;
  tip.style.left = `${Math.max(8, Math.min(innerWidth - width - 8, r.left))}px`;
  tip.style.top = `${Math.max(8, Math.min(innerHeight - height - 8, r.bottom + height + 8 < innerHeight ? r.bottom + 8 : r.top - height - 8))}px`;
  const close = () => { tip.remove(); document.removeEventListener('pointerdown', outside, true); document.removeEventListener('keydown', key); removeEventListener('resize', close); closeAugmentInfo = null; };
  const outside = e => { if (!tip.contains(e.target) && !anchor.contains(e.target)) close(); };
  const key = e => { if (e.key === 'Escape') { close(); if (anchor.isConnected) anchor.focus(); } };
  tip.querySelector('button').onclick = () => { close(); if (anchor.isConnected) anchor.focus(); };
  document.addEventListener('pointerdown', outside, true); document.addEventListener('keydown', key); addEventListener('resize', close); closeAugmentInfo = close;
}
const multTag = m => m > 1 ? `<b class="good">×${m} 유리</b>` : m < 1 ? `<b class="bad">×${m} 불리</b>` : '<b>×1 보통</b>';

function chooseSheet(p) {
  let aug = null, to = null, enter = true, step = p.augments && p.needSwitch ? 'aug' : null;   // 둘 다 고를 때: 증강체 먼저 → 다음 갈매미(v0.14.1)   // enter: 처음 열 때만 카드가 떠오름(고를 때마다 다시 그려도 반복 안 함)
  const me = activeOf(b, 0), foe = activeOf(b, 1);
  const alive = b.sides[0].fighters.map((f, i) => [f, i]).filter(([f]) => f.hp > 0);
  const atk = R.typeMult(me.attr, foe.attr), def = R.typeMult(foe.attr, me.attr), bad = atk < 1 || def > 1;
  const face = f => sprite(f.rec.id, 'front', f.rec.stage), faceCls = f => tintOf(f.rec.id);
  const draw = () => {
    const showAug = p.augments && step !== 'sw', showSw = p.needSwitch && step !== 'aug';
    modal.className = showAug ? 'full' : '';
    const vs = p.augments ? `<div class="vs">
        <div class="side"><img src="${face(me)}" class="flip" alt=""><b>${esc(me.name)}</b>${chip(me.attr)}</div>
        <div class="mid"><small>내 공격</small>${multTag(atk)}<small>상대 공격</small>${multTag(def)}</div>
        <div class="side"><img src="${face(foe)}" class="${faceCls(foe)}" alt=""><b>${esc(foe.name)}</b>${chip(foe.attr)}</div></div>` : '';
    const rr = b.sides[0].rerolled ?? [];
    // 확인 버튼 글자: 고른 것은 이름으로, 아직 안 고른 것은 무엇을 고를지(둘 다 있으면 「 · 」로 이음)
    const okLabel = () => step === 'aug' ? (aug ? `「${esc(R.battleAug.get(aug).name)}」 선택 · 다음: 나갈 갈매미 ›` : '증강체를 골라요')
      : [showAug && (aug ? `「${esc(R.battleAug.get(aug).name)}」 선택` : '증강체를 골라요'), showSw && (to !== null ? `${esc(b.sides[0].fighters[to].name)} 출전` : '나갈 갈매미를 골라요')].filter(Boolean).join(' · ');
    modal.innerHTML = `<div class="sheet pick ${showAug ? 'full' : ''} ${enter ? 'enter' : ''}">
      ${showAug ? `${step === 'aug' ? '<p class="steps">① 증강체 고르기 → ② 다음 갈매미</p>' : ''}<h2>전투 증강체</h2><p class="sub">${b.turn === 1 ? '대결 시작' : '전투불가 발생'} · 3개 중 하나를 골라요. 마음에 안 들면 칸마다 한 번씩 🎲 다시 뽑을 수 있어요.</p>${vs}
        ${hintNow && b.turn === 1 ? `<div class="tipline">💡 ${hl(hintNow)}</div>` : ''}
        ${p.augments.map(id => { const a = R.battleAug.get(id), [ic, kind] = AUG_ICON[id] ?? ['✨', ''], rec = bad && FLIP.includes(id);
          return `<div class="augc ${augKind(id)}"><button class="aug ${aug === id ? 'on' : ''}" data-a="${id}"><span class="ic">${ico(id, ic)}</span><span class="tx"><b>${esc(a.name)}${rec ? '<em>지금 추천</em>' : ''}</b><small>${augKindName(id)}${kind && kind !== augKindName(id) ? ` · ${esc(kind)}` : ''}</small><span>${esc(a.desc)}</span></span><span class="ck">${aug === id ? '✓' : ''}</span></button>
            <button class="ghost reroll" data-r="${p.augments.indexOf(id)}" ${rr[p.augments.indexOf(id)] ? 'disabled' : ''}>${rr[p.augments.indexOf(id)] ? '다시 뽑기 끝' : '🎲 다시 뽑기'}</button></div>`; }).join('')}` : ''}
      ${showSw ? `${step === 'sw' ? `<p class="steps"><button class="ghost" id="backAug">‹ 증강체 다시 고르기</button><span>✓ 증강체 「${esc(R.battleAug.get(aug).name)}」 → ② 다음 갈매미</span></p>` : ''}<h2>다음에 나갈 갈매미</h2><p class="sub">상대: ${esc(foe.name)} ${chip(foe.attr)}</p>
        ${alive.map(([f, i]) => { const m = R.typeMult(f.attr, foe.attr), pct = Math.round(f.hp / f.maxHp * 100);
          return `<button class="aug ${to === i ? 'on' : ''}" data-t="${i}"><img class="ic" src="${face(f)}" alt=""><span class="tx"><b>${esc(f.name)}</b><small>${R.attrName(f.attr)} · ${ROLE[f.role]} · 상대에게 ${m > 1 ? '유리' : m < 1 ? '불리' : '보통'}</small><i class="hpmini"><i style="width:${pct}%"></i></i></span><span class="ck">${to === i ? '✓' : ''}</span></button>`; }).join('')}` : ''}
      <button class="big wide" id="ok" ${(showAug && !aug) || (showSw && to === null) ? 'disabled' : ''}>${okLabel()}</button></div>`;
    enter = false;
    modal.querySelectorAll('[data-a]').forEach(x => x.onclick = () => { aug = x.dataset.a; draw(); });
    modal.querySelectorAll('[data-r]').forEach(x => x.onclick = () => {
      const i = +x.dataset.r, old = p.augments[i];
      try { rerollAugment(b, 0, i); if (pvp) pvp.log.push({ r: i }); } catch { x.disabled = true; x.textContent = '다른 증강체가 없어요'; return; }
      p.augments = b.sides[0].offers; if (aug === old) aug = null; sfx('aug'); draw();
      modal.querySelectorAll('.augc')[i]?.classList.add('rolled');
    });
    modal.querySelectorAll('[data-t]').forEach(x => x.onclick = () => { to = +x.dataset.t; draw(); });
    modal.querySelector('#backAug')?.addEventListener('click', () => { step = 'aug'; enter = false; draw(); });
    modal.querySelector('#ok').onclick = async () => {
      if (step === 'aug') { step = 'sw'; draw(); return; }   // 둘 다면 다음 갈매미 고르기로(엔진에는 마지막에 한 번에)
      modal.hidden = true; modal.className = '';
      const from = b.log.length;
      const pick = { ...(aug ? { augment: aug } : {}), ...(to !== null ? { switchTo: to } : {}) };
      choose(b, 0, pick); if (pvp) pvp.log.push({ c: pick });
      if (b.phase === 'choose') aiChoose(b, 1);
      await play(from); flow();
    };
  };
  modal.onclick = null; modal.hidden = false; draw();
}

// 행동 고르기(v0.14.8, 포켓몬처럼): 먼저 큰 버튼 4개(공격·아이템·교체·도망) → 공격을 누르면 기술, 교체를 누르면 바꿀 갈매미(속성·체력·상성)
// 한 장의 UI 시트를 고정 좌표로 표시한다. 이미지를 비율대로 보여 주고 글자는 HTML로 둔다.
const commandArt = (ui, x, y) => `<span class="bart" aria-hidden="true" style="--ax:${x};--ay:${y};--my:${-(y + 324) / 64 * 100}%"><span class="arttop"><img src="${ui}/b5_command_buttons.webp" alt=""></span><span class="artmid"><img src="${ui}/b5_command_buttons.webp" alt=""></span><span class="artbottom"><img src="${ui}/b5_command_buttons.webp" alt=""></span></span>`;
function actionPanel(pane = 'menu') {
  const f = activeOf(b, 0), legal = legalActions(b, 0), s = b.sides[0];
  const can = a => legal.some(x => x.kind === a.kind && (a.kind === 'skill' ? x.skillId === a.skillId : x.to === a.to));
  const panel = document.getElementById('panel');
  const foe = activeOf(b, 1);
  const bench = s.fighters.map((x, i) => [x, i]).filter(([, i]) => i !== s.active), swaps = bench.filter(([, i]) => can({ kind: 'switch', to: i })).length;
  const items = fightId ? { n: s.itemsUsed, max: R.data.itemRules.perBattle, have: ACC.items.reduce((a, i) => a + i.count, 0) } : null;
  const itemOk = items && items.n < items.max && items.have;
  const back = '<button class="ghost bback" id="bback">‹ 뒤로</button>';
  if (pane === 'fight') {
    // 기술 4칸(무엇을 넣었든)을 2×2로. 모두 재사용 대기면 몸통 박치기 버튼. 지금 상대에게 유리/불리를 같이 보여 줌
    const combo = f.skills.find(k => k.id.startsWith('combo-')), ready = combo && can({ kind: 'skill', skillId: combo.id });
    panel.innerHTML = `<div class="bhead">${back}<b>⚔️ 기술 고르기</b></div>${combo ? `<button class="combo-btn" data-k="${combo.id}" title="${esc(combo.desc)}" ${ready ? '' : 'disabled'}><small>🤝 합동기 · ${esc(R.data.synergies[combo.id.slice(6)].name)} · 대결당 1번</small><b>${esc(combo.name)}</b><span>${esc(combo.desc)}${ready ? '' : b.sides[0].comboUsed ? ' (이미 씀)' : ' (같은 시너지 동료가 있어야 해요)'}</span></button>` : ''}<div class="skills">${f.skills.filter(k => !k.id.startsWith('combo-')).map(k => {
      const preview = skillPreview(b, 0, k), at = preview.attr, m = preview.multiplier, wait = skillProblem(b, 0, k);
      const kind = k.id.startsWith('sub-') ? '보조 속성' : k.id.startsWith('learn-') ? '일반 기술' : k.id === 'sig' ? '전용 기술' : k.id.startsWith('role-') ? '역할기' : '기본기';
      return `<button class="sk" data-k="${k.id}" style="--c:${COLOR[at]}" ${can({ kind: 'skill', skillId: k.id }) ? '' : 'disabled'}>
        <span class="kd">${preview.attrName}${preview.bonusActive ? ' · 발동' : ''}${wait ? ' · 적용 중' : ''}</span><b>${esc(k.name)}</b>
        <span class="ln">${k.power ? `<small>위력 ${preview.power}${k.hits > 1 ? `×${k.hits}` : ''}</small>${m > 1 ? '<em class="good">▲유리</em>' : m < 1 ? '<em class="bad">▼불리</em>' : ''}` : '<small>보조 기술</small>'}</span>
        ${(k.summary ?? restDesc(k)) ? `<p>${esc(k.summary ?? restDesc(k))}</p>` : ''}</button>`;
    }).join('')}</div>${can({ kind: 'skill', skillId: 'struggle' }) ? '<button class="sk struggle" data-k="struggle" style="margin-top:6px;width:100%"><span class="kd">쓸 기술이 없을 때</span><b>몸통 박치기</b><span class="ln"><small>위력 30</small></span></button>' : ''}`;
    panel.querySelectorAll('[data-k]').forEach(x => x.onclick = () => turn({ kind: 'skill', skillId: x.dataset.k }));
  } else if (pane === 'swap') {
    panel.innerHTML = `<div class="bhead">${back}<b>🔁 교체</b><small>상대: ${esc(foe.name)} ${chip(foe.attr)}</small></div><div class="swl">${bench.map(([x, i]) => {
      const pct = Math.max(0, Math.round(x.hp / x.maxHp * 100)), atk = R.typeMult(x.attr, foe.attr), def = R.typeMult(foe.attr, x.attr), col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
      return `<button class="swc" data-s="${i}" ${can({ kind: 'switch', to: i }) ? '' : 'disabled'}><img src="${sprite(x.rec.id, 'front', x.rec.stage)}" class="${tintOf(x.rec.id)}" alt=""><span class="tx"><b>${esc(x.name)}</b><span class="at">${chip(x.attr)}<small>${ROLE[x.role] ?? ''}</small></span>
        <i class="hpb"><i style="width:${pct}%;background:${col}"></i></i><small>${x.hp > 0 ? `체력 ${Math.max(0, x.hp)}/${x.maxHp}` : '전투불가'}${x.hp > 0 ? ` · 공격 ${atk > 1 ? '<em class="good">유리</em>' : atk < 1 ? '<em class="bad">불리</em>' : '보통'} · 방어 ${def > 1 ? '<em class="bad">불리</em>' : def < 1 ? '<em class="good">유리</em>' : '보통'}` : ''}</small></span></button>`; }).join('')}</div>`;
    panel.querySelectorAll('[data-s]').forEach(x => x.onclick = () => turn({ kind: 'switch', to: +x.dataset.s }));
  } else {
    const UI = APP ? 'intro' : `${BASE}/duel/intro`;   // 모바일 그림 버튼: 재질·아이콘은 시트, 제목·안내는 실제 글꼴
    panel.innerHTML = `<div class="bmenu img">
      <button class="bm atk" id="bAtk" aria-label="공격" title="기술 ${f.skills.filter(k => can({ kind: 'skill', skillId: k.id })).length}개">${commandArt(UI, 42, 74)}<b class="bt">공격</b></button>
      <button class="bm bag" id="bag" aria-label="아이템" ${itemOk ? '' : 'disabled'} title="${items ? `${items.n}/${items.max} · 가진 ${items.have}` : pvp ? '대전에선 못 써요' : '불러오는 중'}">${commandArt(UI, 638, 74)}<b class="bt">아이템</b></button>
      <button class="bm sw" id="bSw" aria-label="교체" ${swaps ? '' : 'disabled'} title="${swaps ? `${swaps}마리 대기` : '바꿀 갈매미 없음'}">${commandArt(UI, 42, 639)}<b class="bt">교체</b></button>
      <button class="bm run" id="flee" aria-label="${pvp ? '나가기' : '도망가기'}" title="${pvp?.mode === 'rank' ? '패배로 처리' : pvp ? '대전 끝내기' : '경험치 없음'}">${commandArt(UI, 638, 639)}<b class="bt">${pvp ? '나가기' : '도망가기'}</b></button></div>`;
    panel.querySelector('#bAtk').onclick = () => actionPanel('fight');
    panel.querySelector('#bSw').onclick = () => actionPanel('swap');
    panel.querySelector('#bag').onclick = itemSheet;
    panel.querySelector('#flee').onclick = runAway;
  }
  panel.querySelector('#bback')?.addEventListener('click', () => actionPanel('menu'));
}
// 아이템: 가진 것 중 하나(다시 출근 깃털은 일으킬 갈매미도 고름). 계정에서 1개 빼고 → 그 턴 행동으로 씀
function itemSheet() {
  if (busy) return;
  const mine = id => ACC.items.find(i => i.id === id)?.count ?? 0, down = b.sides[0].fighters.map((f, i) => [f, i]).filter(([f]) => f.hp <= 0);
  let pick = null, target = null;
  const draw = () => {
    const it = pick && R.data.items[pick];
    // 읽기 쉽게(v0.14.9): 이름 크게 · 효과는 짧게 한 줄 · 못 쓰는 이유는 따로 · 2×2
    const eff = d => d.heal ? `체력 ${d.heal}% 회복` : d.revive ? `쓰러진 갈매미를 체력 ${d.revive}%로` : d.cleanse ? '상태 이상 풀기' : esc(d.desc);
    const left = R.data.itemRules.perBattle - b.sides[0].itemsUsed;
    modal.innerHTML = `<div class="sheet pick items"><div class="ihead"><h2>🎒 아이템</h2><p>이번 대결에 <b>${left}개</b> 더 쓸 수 있어요 · 쓰면 이번 턴 행동이 끝나요</p></div>
      <div class="igrid">${Object.entries(R.data.items).map(([id, d]) => { const n = mine(id), why = n ? itemProblem(b, 0, id) : '가진 게 없어요';
        return `<button class="itm ${pick === id ? 'on' : ''}" data-i="${id}" ${why ? 'disabled' : ''}><span class="ic">${ico(id, d.revive ? '🪶' : d.cleanse && !d.heal ? '💧' : '🩹')}</span><span class="tx"><b>${esc(d.name)}</b><span class="ef">${eff(d)}</span>${why ? `<small class="why">${esc(why)}</small>` : ''}</span><em class="cnt">×${n}</em></button>`; }).join('')}</div>
      ${it?.revive ? `<p class="isub">다시 일으킬 갈매미</p><div class="igrid">${down.map(([f, i]) => `<button class="itm ${target === i ? 'on' : ''}" data-t="${i}"><img class="ic" src="${sprite(f.rec.id, 'front', f.rec.stage)}" alt=""><span class="tx"><b>${esc(f.name)}</b><span class="ef">${chip(f.attr)}</span></span></button>`).join('')}</div>` : ''}
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
  fleeTo?.save?.();   // 스토리: 도망쳐도 남은 체력은 이어짐
  b.phase = 'over';
  document.getElementById('m0').animate([{ transform: 'translateX(0)', opacity: 1 }, { transform: 'translateX(-120px)', opacity: 0 }], { duration: 500, fill: 'forwards' });
  say('수비조가 재빨리 물러났어요!');
  // 도망친 뒤(v0.14.9): 우리 팀 남은 체력을 보여 주고, 큰 버튼 두 개(다시 도전 · 돌아가기). 스토리면 체력이 이어진다는 것도 알려 줌
  const story = !!fleeTo?.save, low = b.sides[0].fighters.some(f => f.hp / f.maxHp < 0.3);
  document.getElementById('panel').innerHTML = `<div class="fled"><div class="fh"><i>🏃</i><div><b>도망쳤어요</b><small>경험치는 없어요${story ? ' · 남은 체력은 다음 전투로 이어져요' : ''}</small></div></div>
    <div class="fteam">${b.sides[0].fighters.map(f => { const pct = Math.max(0, Math.round(f.hp / f.maxHp * 100)), col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
      return `<div class="fm"><img src="${sprite(f.rec.id, 'front', f.rec.stage)}" alt=""><span><b>${esc(f.name)}</b><i class="hpb"><i style="width:${pct}%;background:${col}"></i></i><small>${f.hp > 0 ? `체력 ${Math.max(0, f.hp)}/${f.maxHp}` : '쓰러짐'}</small></span></div>`; }).join('')}</div>
    ${story && low ? '<p class="ftip">💡 체력이 적어요. 스토리 화면 위 「🩹 치료약」으로 회복하거나 잠시 쉬었다 와요.</p>' : ''}
    <div class="fbtns"><button class="big" id="fr">다시 도전</button><button class="ghost" id="fb">${story ? '스토리로' : '돌아가기'}</button></div></div>`;
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
    else if ((m = line.match(/화상으로 (\d+) 피해/))) {
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
    } else if ((m = line.match(/^(.+)의 (.+)!$/))) {   // 공격 문장은 맨 끝에서: 「태초의 갈매미맨은 들어가고 …」처럼 이름에 「의 」가 든 교체·피해·쓰러짐 문장을 공격으로 잘못 읽지 않게(v0.15.9)
      const side = [0, 1].find(s => shown(s).name === m[1] && s !== lastAtk) ?? [0, 1].find(s => shown(s).name === m[1]) ?? lastAtk;
      lastAtk = side; lastSkill = shown(side).skills.find(k => k.name === m[2]);
      lunge(side);
      if (m[2] === '다섯 번째 목소리') await fiveVoices();   // 앙~ 갈매미맨이야의 고유기: 세는 소리 연출
      if (lastSkill?.power) await shoot(side, lastSkill.attr ?? shown(side).attr, lastSkill.hits ?? 1, glyphOf(lastSkill.name));
      else if (lastSkill) aura(side, lastSkill.attr ?? shown(side).attr);
      wait = lastSkill?.power ? 120 : 600;
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
// 나오는 효과는 새 그림을 받은 뒤에(v0.15.9)
function popIn(s) { const img = document.getElementById(`m${s}`); if (!img.complete) { img.addEventListener('load', () => popIn(s), { once: true }); return; } img.getAnimations().forEach(a => a.cancel()); img.animate([{ transform: 'scale(.5)', opacity: 0 }, { transform: 'scale(1.05)', opacity: 1 }, { transform: 'scale(1)', opacity: 1 }], { duration: 420, easing: 'ease-out' }); }
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
