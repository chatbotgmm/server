// 수비대 대결 시험판: 새 전투 엔진(src/battle)을 그대로 묶어 브라우저에서 돌림(빌드: scripts/build-play.mjs → dist/duel/duel.js)
// 규칙·유닛·스토리는 서버가 data/battle.yaml·data/story.yaml을 검사해 보내 줌(/duel/data.json). 기술 효과는 그림 없이 코드로 그림
// 스토리 진행(레벨·경험치·깬 스테이지)은 시험판이라 이 브라우저에만 저장(카톡 계정 연결 전)
import { rulesFrom } from '../../src/battle/core.ts';
import { createBattle, choose, act, pending, legalActions, pickAi, aiChoose, activeOf } from '../../src/battle/engine.ts';
import { addXp, battleXp, xpToNext } from '../../src/battle/growth.ts';

const K = new URLSearchParams(location.search).get('k') || '';
const app = document.getElementById('app'), modal = document.getElementById('modal');
const COLOR = { STONE: '#c9b48a', GOLD: '#f0b93a', FIRE: '#f26b3a', ICE: '#8fdcf5', BLOSSOM: '#f7a3c6', SEA: '#3d94e6', SHADOW: '#8a6bb8', STAR: '#ffe36e', THREAD: '#c3c6dc', GRAVITY: '#5b63c9' };
const ROLE = { ATTACK: '공격', DEFENSE: '수비', UTILITY: '유틸' };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const q = `?k=${encodeURIComponent(K)}`;
const chip = a => `<span class="chip" style="background:${COLOR[a]}">${esc(R.attrName(a))}</span>`;
const shuffle = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };

let R, UNITS, STORY, ART, b, busy = false, freeLevel = 30, freePick = [];
let view;          // 화면에 보이는 상태(엔진은 한 턴을 한꺼번에 계산하므로, 기록을 한 줄씩 보여 주며 체력을 따라 줄임)
let onEnd = null;  // 대결이 끝나면 부를 함수(자유 대결 / 스토리)
let hintNow = ''; // 스테이지 도움말(첫 증강체 창에도 보여 줌)

// ── 그림: 최상위는 앞·뒤 그림, 빌런은 그림이 있으면 그것, 없으면 코드로 그린 임시 그림 ──
const sprite = (id, side) => id.startsWith('V') ? villainArt(id) : `/duel/sprites/${id}_s0_${side}.png${q}`;
function villainArt(id) {
  if (ART.includes(id)) return `/duel/sprites/${id}_front.png${q}`;
  const v = R.villains.get(id), c = { V1: '#d9805a', V2: '#5b4a78', V3: '#e98fa6', V4: '#bfe3f2', V5: '#c4573f', V6: '#8e3b46' }[id] ?? '#d9805a';
  let svg;
  if (id === 'V12') svg = `<ellipse cx="100" cy="130" rx="52" ry="46" fill="#f4f1ea" stroke="#5a4636" stroke-width="4"/><path d="M58 120 Q30 150 52 172 L84 150Z" fill="#3a3340" stroke="#5a4636" stroke-width="4"/>
    <circle cx="112" cy="78" r="34" fill="#f4f1ea" stroke="#5a4636" stroke-width="4"/><path d="M80 66 Q112 34 144 66 L140 74 Q112 52 84 74Z" fill="#d63a3a" stroke="#5a4636" stroke-width="3"/>
    <path d="M70 82 L46 90 L72 96Z" fill="#f2a33a" stroke="#5a4636" stroke-width="3"/><circle cx="100" cy="82" r="5" fill="#222"/><path d="M92 72 L108 78" stroke="#222" stroke-width="4"/>
    <path d="M86 172 L80 192 M116 172 L120 192" stroke="#f2a33a" stroke-width="7" stroke-linecap="round"/>`;
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

// ── 저장(시험판: 이 브라우저에만) ──
const SAVE_KEY = 'gmm-duel-story-v1';
function load() {
  let s = null; try { s = JSON.parse(localStorage.getItem(SAVE_KEY) || 'null'); } catch { s = null; }
  if (!s || typeof s !== 'object') s = {};
  s.units ??= {}; s.team ??= []; s.cleared ??= [];
  for (const u of UNITS) s.units[u.id] ??= { level: STORY.start.level, xp: 0 };
  s.team = s.team.filter(id => UNITS.some(u => u.id === id)).slice(0, 3);
  return s;
}
let SAVE;
const persist = () => { try { localStorage.setItem(SAVE_KEY, JSON.stringify(SAVE)); } catch { /* 저장 안 돼도 진행은 가능 */ } };
const myRec = (id, i) => { const u = UNITS.find(x => x.id === id), s = SAVE.units[id]; return { uid: `a${i}`, id, name: u.name, tier: u.tier, level: s.level, xp: s.xp, stage: 0, evoAugs: [] }; };

async function boot() {
  try {
    const r = await fetch(`/duel/data.json${q}`);
    const j = await r.json();
    if (!r.ok) throw new Error(j.error || `불러오기 실패(${r.status})`);
    R = rulesFrom(j.rules); STORY = j.story; ART = j.villainArt ?? [];
    UNITS = j.units.map(u => ({ ...u, def: R.units.get(u.id) }));
    SAVE = load();
    menu();
  } catch (e) {
    app.innerHTML = `<header><h1>수비대 대결</h1></header><div class="err">${esc(e.message)}</div>`;
  }
}

function menu() {
  modal.hidden = true;
  app.innerHTML = `<header><h1>수비대 대결</h1><small>시험판</small></header>
  <p class="hint">스토리는 이 브라우저에 진행이 저장돼요(카톡 계정 연결 전 시험판).</p>
  <div class="menu"><button class="big" id="st">스토리 모드</button><button class="ghost" id="fr">자유 대결 (무작위 상대)</button></div>`;
  app.querySelector('#st').onclick = storyScreen;
  app.querySelector('#fr').onclick = () => pickScreen('free');
}

// ── 편성(자유 대결: 레벨 선택 / 스토리: 저장된 레벨) ──
function pickScreen(mode) {
  modal.hidden = true;
  const story = mode === 'story';
  let picked = story ? [...SAVE.team] : freePick;
  app.innerHTML = `<header><button class="back" id="bk">‹</button><h1>${story ? '스토리 팀 편성' : '자유 대결'}</h1></header>
  <p class="hint">갈매미 3마리를 고르세요. 처음 고른 갈매미가 먼저 나가요.${story ? ' 시험판이라 최상위 33종을 모두 쓸 수 있어요(실제 게임은 카톡에서 조합한 원형만).' : ' 상대는 같은 레벨의 무작위 3마리예요.'}</p>
  ${story ? '' : `<div class="bar"><span class="role">레벨</span><div class="seg" id="lv">${[10, 30, 50].map(l => `<button data-l="${l}" class="${l === freeLevel ? 'on' : ''}">${l}</button>`).join('')}</div></div>`}
  <div class="grid">${UNITS.map(u => `<button class="card" data-id="${u.id}"><img loading="lazy" src="/thumbs/${u.id}.jpg" alt=""><b>${esc(u.name)}</b>${chip(u.def.attr)}<span class="role">${ROLE[u.def.role]}${story ? ` · Lv${SAVE.units[u.id].level}` : ''}</span></button>`).join('')}</div>
  <div class="foot"><div class="in"><button class="ghost" id="rnd">무작위</button><button class="big" id="go">${story ? '이 팀으로' : '대결 시작'}</button></div></div>`;
  const paint = () => {
    app.querySelectorAll('.card').forEach(c => {
      const i = picked.indexOf(c.dataset.id); c.classList.toggle('on', i >= 0);
      c.querySelector('.n')?.remove(); if (i >= 0) c.insertAdjacentHTML('beforeend', `<span class="n">${i + 1}</span>`);
    });
    app.querySelector('#go').disabled = picked.length !== 3;
  };
  app.querySelectorAll('.card').forEach(c => c.onclick = () => {
    const id = c.dataset.id, i = picked.indexOf(id);
    if (i >= 0) picked.splice(i, 1); else if (picked.length < 3) picked.push(id);
    paint();
  });
  app.querySelectorAll('#lv button').forEach(x => x.onclick = () => { freeLevel = +x.dataset.l; app.querySelectorAll('#lv button').forEach(y => y.classList.toggle('on', y === x)); });
  app.querySelector('#rnd').onclick = () => { picked.splice(0, 3, ...shuffle(UNITS.map(u => u.id)).slice(0, 3)); paint(); };
  app.querySelector('#bk').onclick = story ? storyScreen : menu;
  app.querySelector('#go').onclick = () => {
    if (story) { SAVE.team = [...picked]; persist(); storyScreen(); }
    else { freePick = picked; freeBattle(); }
  };
  paint();
}

function freeBattle() {
  const rec = (id, i, side) => { const u = UNITS.find(x => x.id === id); return { uid: `${side}${i}`, id, name: u.name, tier: u.tier, level: freeLevel, xp: 0, stage: 0, evoAugs: [] }; };
  const foe = shuffle(UNITS.map(u => u.id)).slice(0, 3);
  startBattle(freePick.map((id, i) => rec(id, i, 'a')), foe.map((id, i) => rec(id, i, 'b')), won => {
    const xp = battleXp(R, freeLevel, won);
    return { text: `실제 게임이라면 출전한 갈매미마다 경험치 ${xp}을 받아요(자유 대결은 저장하지 않아요).`, buttons: [['같은 팀으로 다시', freeBattle], ['편성으로', () => pickScreen('free')]] };
  });
}

// ── 스토리 ──
const stages = () => STORY.chapters.flatMap(c => c.stages.map(s => ({ ...s, chapter: c })));
const unlocked = id => { const all = stages(), i = all.findIndex(s => s.id === id); return i === 0 || SAVE.cleared.includes(all[i - 1].id); };

function storyScreen() {
  modal.hidden = true;
  const team = SAVE.team.map(id => UNITS.find(u => u.id === id));
  app.innerHTML = `<header><button class="back" id="bk">‹</button><h1>스토리</h1><small>${SAVE.cleared.length}/${stages().length} 실적</small></header>
  <section class="teambar">${team.length === 3 ? team.map(u => { const s = SAVE.units[u.id], need = xpToNext(R, s.level);
      return `<div class="mini"><img src="${sprite(u.id, 'front')}" alt=""><b>${esc(u.name)}</b><span>Lv${s.level} ${chip(u.def.attr)}</span><i class="xp"><i style="width:${Math.min(100, s.xp / need * 100)}%"></i></i></div>`; }).join('')
    : '<p class="hint" style="margin:0">먼저 팀 3마리를 정해 주세요.</p>'}<button class="ghost" id="ed">${team.length === 3 ? '편성 바꾸기' : '팀 정하기'}</button></section>
  ${STORY.chapters.map(c => `<section class="chap"><h2>${esc(c.title)}</h2>${c.intro ? `<p class="hint" style="margin:0 0 8px">${esc(c.intro)}</p>` : ''}
    ${c.stages.map(s => { const done = SAVE.cleared.includes(s.id), open = unlocked(s.id);
      const boss = s.enemy.some(e => R.villains.get(e.id).boss);
      return `<button class="stage ${done ? 'done' : ''}" data-s="${s.id}" ${open && team.length === 3 ? '' : 'disabled'}><span class="no">${s.id}</span><span class="tt">${esc(s.title)}${boss ? ' <em>보스</em>' : ''}<small>${s.enemy.map(e => `${esc(R.villains.get(e.id).name)} Lv${e.level}`).join(' · ')}</small></span><span class="mk">${done ? '✓' : open ? '도전' : '잠김'}</span></button>`; }).join('')}</section>`).join('')}
  <p class="hint">진 대결도 경험치를 조금 받아요. 깬 스테이지는 다시 도전해 레벨을 올릴 수 있어요.</p>
  <div style="padding:0 16px 24px"><button class="ghost" id="reset" style="width:100%">스토리 진행 처음부터(이 브라우저)</button></div>`;
  app.querySelector('#bk').onclick = menu;
  app.querySelector('#ed').onclick = () => pickScreen('story');
  app.querySelector('#reset').onclick = () => { if (confirm('레벨·경험치·깬 스테이지를 모두 지울까요?')) { try { localStorage.removeItem(SAVE_KEY); } catch { /* 무시 */ } SAVE = load(); storyScreen(); } };
  app.querySelectorAll('[data-s]').forEach(x => x.onclick = () => playStage(stages().find(s => s.id === x.dataset.s)));
}

// 대사: 한 줄씩, 화면을 누르면 다음
function talk(lines) {
  return new Promise(resolve => {
    if (!lines?.length) return resolve();
    const lead = UNITS.find(u => u.id === SAVE.team[0]);
    let i = 0;
    const draw = () => {
      const l = lines[i], v = /^V\d+$/.test(l.who) ? R.villains.get(l.who) : null;
      const who = l.who === 'N' ? '' : l.who === 'ME' ? lead.name : v ? v.name : l.who;
      const pic = l.who === 'ME' ? sprite(lead.id, 'front') : v ? sprite(l.who, 'front') : '';
      modal.innerHTML = `<div class="sheet talk ${l.who === 'N' ? 'narr' : ''}">${pic ? `<img src="${pic}" alt="">` : ''}<div>${who ? `<b>${esc(who)}</b>` : ''}<p>${esc(l.say)}</p></div>
        <div class="tk"><button class="ghost" id="skip">건너뛰기</button><span>${i + 1}/${lines.length} · 누르면 다음</span></div></div>`;
      modal.querySelector('#skip').onclick = e => { e.stopPropagation(); done(); };
    };
    const done = () => { modal.onclick = null; modal.hidden = true; resolve(); };
    modal.onclick = () => { i++; if (i >= lines.length) done(); else draw(); };
    modal.hidden = false; draw();
  });
}

async function playStage(st) {
  await talk(st.before);
  const foe = st.enemy.map((e, i) => { const v = R.villains.get(e.id); return { uid: `b${i}`, id: e.id, name: v.name, tier: 'LIMITED', level: e.level, xp: 0, stage: 0, evoAugs: [] }; });
  const mine = SAVE.team.map(myRec);
  startBattle(mine, foe, won => {
    const avg = st.enemy.reduce((a, e) => a + e.level, 0) / st.enemy.length, xp = battleXp(R, avg, won);
    const ups = [];
    for (const r of mine) {
      const before = r.level; addXp(R, r, xp); SAVE.units[r.id] = { level: r.level, xp: r.xp };
      if (r.level > before) ups.push(`${r.name} Lv${before}→${r.level}`);
    }
    const first = won && !SAVE.cleared.includes(st.id);
    if (first) SAVE.cleared.push(st.id);
    persist();
    const last = st.chapter.stages.at(-1).id === st.id;
    const after = async () => {
      if (won) { await talk(st.after); if (first && last) await talk(st.chapter.clear); }
      else await talk(st.lose ?? [{ who: 'N', say: '수비조가 물러났어요. 레벨을 올리거나 편성·상성을 바꿔 다시 도전해 보세요.' }]);
      storyScreen();
    };
    return { text: `출전한 갈매미마다 경험치 +${xp}${ups.length ? ` · 레벨 업! ${ups.join(', ')}` : ''}${first && last && /^\d+$/.test(st.chapter.id) ? ` · ${st.chapter.id}단계 승진 심사가 열렸어요(승진 기능은 준비 중)` : ''}`,
      buttons: won ? [['다음으로', after]] : [['다시 도전', () => playStage(st)], ['스토리로', after]] };
  }, st.hint);
}

// ── 대결 ──
function startBattle(mine, foe, end, hint) {
  onEnd = end; hintNow = hint || '';
  b = createBattle(R, mine, foe, (Math.random() * 2 ** 31) | 0);
  const boss = foe.some(f => R.villains.get(f.id)?.boss);
  app.innerHTML = `<div id="arena"><div class="plat foe"></div><div class="plat me"></div>
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
    const f = shown(s), img = document.getElementById(`m${s}`), src = sprite(f.rec.id, s === 0 ? 'back' : 'front');
    if (img.dataset.src !== src) { img.src = src; img.dataset.src = src; }
    img.style.opacity = view[s].down[view[s].active] ? 0 : 1;
    const hp = view[s].hp[view[s].active], pct = Math.max(0, hp / f.maxHp * 100);
    const col = pct > 50 ? 'var(--hp)' : pct > 20 ? 'var(--hpmid)' : 'var(--hplow)';
    const st = Object.keys(f.status).map(k => `<span class="st">${({ burn: '화상', freeze: '빙결', sleep: '잠', bind: '묶임', rage: '폭주' })[k]}</span>`).join('');
    const augs = b.sides[s].augs.map(id => R.battleAug.get(id).name).join(' · ');
    document.getElementById(`b${s}`).innerHTML = `<div class="nm"><span>${esc(f.name)}</span><em>Lv${f.rec.level}</em></div>
      <div class="hpb"><i style="width:${pct}%;background:${col}"></i></div>
      <div class="row"><span>${chip(f.attr)}${f.extraAttr ? chip(f.extraAttr) : ''}${st}</span>${s === 0 ? `<small>${Math.max(0, Math.round(hp))}/${f.maxHp}</small>` : ''}</div>
      <div class="row"><div class="dots">${view[s].down.map((d, i) => `<i class="${d ? 'down' : ''}" title="${esc(b.sides[s].fighters[i].name)}"></i>`).join('')}</div></div>
      ${augs ? `<div class="augs">${esc(augs)}</div>` : ''}`;
  }
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

function chooseSheet(p) {
  let aug = null, to = null;
  const me = activeOf(b, 0), foe = activeOf(b, 1);
  const alive = b.sides[0].fighters.map((f, i) => [f, i]).filter(([f]) => f.hp > 0);
  const draw = () => {
    modal.innerHTML = `<div class="sheet">
      ${p.augments ? `<h2>전투 증강체 고르기</h2><p class="sub">상대 선두: ${esc(foe.name)} ${chip(foe.attr)} · 내 선두: ${esc(me.name)} ${chip(me.attr)}<br>상대도 하나를 골라요.</p>${hintNow && b.turn === 1 ? `<p class="sub">💡 ${esc(hintNow)}</p>` : ''}
        ${p.augments.map(id => { const a = R.battleAug.get(id); return `<button class="opt ${aug === id ? 'on' : ''}" data-a="${id}"><b>${esc(a.name)}</b><span>${esc(a.desc)}</span></button>`; }).join('')}` : ''}
      ${p.needSwitch ? `<h2>다음에 나갈 갈매미</h2>${alive.map(([f, i]) => `<button class="opt ${to === i ? 'on' : ''}" data-t="${i}"><b>${esc(f.name)}</b><span>${R.attrName(f.attr)} · ${ROLE[f.role]} · 체력 ${f.hp}/${f.maxHp}</span></button>`).join('')}` : ''}
      <button class="big" id="ok" ${(p.augments && !aug) || (p.needSwitch && to === null) ? 'disabled' : ''}>확인</button></div>`;
    modal.querySelectorAll('[data-a]').forEach(x => x.onclick = () => { aug = x.dataset.a; draw(); });
    modal.querySelectorAll('[data-t]').forEach(x => x.onclick = () => { to = +x.dataset.t; draw(); });
    modal.querySelector('#ok').onclick = async () => {
      modal.hidden = true;
      const from = b.log.length;
      choose(b, 0, { ...(aug ? { augment: aug } : {}), ...(to !== null ? { switchTo: to } : {}) });
      if (b.phase === 'choose') aiChoose(b, 1);
      await play(from); flow();
    };
  };
  modal.onclick = null; modal.hidden = false; draw();
}

function actionPanel() {
  const f = activeOf(b, 0), legal = legalActions(b, 0), s = b.sides[0];
  const can = a => legal.some(x => x.kind === a.kind && (a.kind === 'skill' ? x.skillId === a.skillId : x.to === a.to));
  const panel = document.getElementById('panel');
  panel.innerHTML = f.skills.map(k => {
    const cd = f.cds[k.id] > 0 ? `${f.cds[k.id]}턴 뒤` : k.cd ? `재사용 ${k.cd}턴` : '';
    return `<button class="sk" data-k="${k.id}" style="--c:${COLOR[k.attr ?? f.attr]}" ${can({ kind: 'skill', skillId: k.id }) ? '' : 'disabled'}>
      <div class="t"><span>${esc(k.name)}</span><small>${k.power ? `${R.attrName(k.attr ?? f.attr)} · 위력 ${k.power}${k.hits > 1 ? `×${k.hits}` : ''}` : '보조'}${cd ? ` · ${cd}` : ''}</small></div><p>${esc(k.desc)}</p></button>`;
  }).join('') + `<div class="sw">${s.fighters.map((x, i) => i === s.active ? '' : `<button data-s="${i}" ${can({ kind: 'switch', to: i }) ? '' : 'disabled'}><img src="${sprite(x.rec.id, 'front')}" alt=""><span>${esc(x.name)}<br><small>${x.hp > 0 ? `교체 · ${Math.round(x.hp / x.maxHp * 100)}%` : '전투불가'}</small></span></button>`).join('')}</div>`;
  panel.querySelectorAll('[data-k]').forEach(x => x.onclick = () => turn({ kind: 'skill', skillId: x.dataset.k }));
  panel.querySelectorAll('[data-s]').forEach(x => x.onclick = () => turn({ kind: 'switch', to: +x.dataset.s }));
}

async function turn(mine) {
  if (busy) return; busy = true;
  document.getElementById('panel').innerHTML = '';
  const from = b.log.length;
  try { act(b, [mine, pickAi(b, 1)]); } catch (e) { say(e.message); busy = false; return actionPanel(); }
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
    if ((m = line.match(/^(.+)의 (.+)!$/))) {
      const side = [0, 1].find(s => shown(s).name === m[1] && s !== lastAtk) ?? [0, 1].find(s => shown(s).name === m[1]) ?? lastAtk;
      lastAtk = side; lastSkill = shown(side).skills.find(k => k.name === m[2]);
      lunge(side); wait = 450;
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
      view[t].down[view[t].active] = true; faint(t); wait = 900;
    } else if (line.includes('모두 막아 냈어요')) { burst(1 - lastAtk, '#ffffff', 10, 60); }
    else if (line.includes('상태가 됐어요')) { const t = [0, 1].find(s => line.startsWith(shown(s).name)); if (t !== undefined) burst(t, '#b07cff', 12, 50); }
    else if (line.includes('증강체 「')) { banner(line.replace(/^\[.+?\] /, '')); wait = 1000; }
    else if (line.includes('다시 일어났어요')) { view = snap(); render(); }
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
function faint(s) { document.getElementById(`m${s}`).animate([{ transform: 'translateY(0)', opacity: 1 }, { transform: 'translateY(30px)', opacity: 0 }], { duration: 600, fill: 'forwards' }); }
function popIn(s) { const img = document.getElementById(`m${s}`); img.getAnimations().forEach(a => a.cancel()); img.animate([{ transform: 'scale(.5)', opacity: 0 }, { transform: 'scale(1.05)', opacity: 1 }, { transform: 'scale(1)', opacity: 1 }], { duration: 420, easing: 'ease-out' }); }
function banner(text) {
  const d = document.createElement('div'); d.className = 'banner'; d.textContent = text;
  document.getElementById('fx').append(d);
  d.animate([{ opacity: 0, transform: 'scale(.8)' }, { opacity: 1, transform: 'scale(1)', offset: .2 }, { opacity: 1, offset: .8 }, { opacity: 0 }], { duration: 1000 }).onfinish = () => d.remove();
}

function result() {
  const won = b.winner === 0, r = onEnd(won);
  document.getElementById('panel').innerHTML = `<div class="sheet" style="border-radius:14px"><h2>${b.winner === null ? '무승부' : won ? '승리!' : '패배…'}</h2>
    <p class="sub">${b.turn - 1}턴 · ${esc(r.text)}</p>
    <div class="sw">${r.buttons.map(([t], i) => `<button class="ghost" data-r="${i}" style="justify-content:center">${esc(t)}</button>`).join('')}</div></div>`;
  document.querySelectorAll('[data-r]').forEach(x => x.onclick = () => r.buttons[+x.dataset.r][1]());
}

boot();
