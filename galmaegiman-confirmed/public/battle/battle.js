// 갈매미맨: 수비대 대결 — 포켓몬식 3:3 턴제 배틀 + 증강체(롤토체스식 3개 중 1개) 로그라이트 시험판
// 유닛 능력치·타입은 서버(/battle/roster.json, src/battle.ts)가 등급·출신 동네로 계산합니다.
// 시험판: 내 컬렉션 대신 시작할 때 후보 8마리 중 3마리를 고릅니다(나중에 카톡 계정 연결).

const STAGES = 7;
const TYPES = {
  BASIC: { name: '기본', color: '#9a8d72', move: '돌머리 박치기', skill: 'sleep' },
  GOLD: { name: '황금', color: '#c9972a', move: '잔돈 던지기', skill: 'shield' },
  DARK: { name: '암흑', color: '#5b4876', move: '그늘 할퀴기', skill: 'ambush' },
  SEA: { name: '바다', color: '#2f8fd0', move: '물보라', skill: 'wave' },
  BLOSSOM: { name: '벚꽃', color: '#de7f9f', move: '꽃잎 바람', skill: 'nap' },
  COSMOS: { name: '우주', color: '#3b4fa8', move: '별똥 박치기', skill: 'meteor' },
  OUTFIT: { name: '의복', color: '#d0703a', move: '리본 채찍', skill: 'dressup' },
};
// 특기(동네마다 하나, 첫째 타입 것을 씀)
const SKILLS = {
  sleep: { name: '갈매미 한 마리…', desc: '상대가 세다가 잠들어 1턴 쉼', cd: 3 },
  shield: { name: '도금 방패', desc: '2턴 동안 받는 피해 절반', cd: 3 },
  ambush: { name: '그늘 기습', desc: '위력 85, 무조건 먼저 공격', cd: 2, power: 85, first: true },
  wave: { name: '해일 몸통박치기', desc: '위력 110', cd: 3, power: 110 },
  nap: { name: '꽃잎 낮잠', desc: '체력 40% 회복', cd: 3 },
  meteor: { name: '운석 낙하', desc: '위력 150', cd: 4, power: 150 },
  dressup: { name: '단단히 차려입기', desc: '3턴 동안 공격 +40%', cd: 4 },
};
// 상성: 왼쪽이 오른쪽에 강함(×1.5), 반대로 맞으면 ×0.67. 기본은 상성 없음
const CHART = { SEA: 'GOLD', GOLD: 'OUTFIT', OUTFIT: 'BLOSSOM', BLOSSOM: 'DARK', DARK: 'COSMOS', COSMOS: 'SEA' };
const WHY = {
  SEA: '무거운 금은 바다에 가라앉아요', GOLD: '금 장식 앞에서 옷이 초라해져요', OUTFIT: '옷으로 꽃가루를 막아요',
  BLOSSOM: '꽃이 피면 그늘이 밝아져요', DARK: '어둠이 별빛을 삼켜요', COSMOS: '달이 바닷물을 끌어당겨요',
};
const TEAM_NAME = { BASIC: '정문 부두 훈련조', GOLD: '금빛 시장 경비대', DARK: '그늘 골목 밤근무조', SEA: '해저 2층 수비대', BLOSSOM: '벚꽃 언덕 낮잠조', COSMOS: '별 관측대 야간조', OUTFIT: '빨래터 수비대' };
const TIER_NAME = { COMMON: '흔함', UNCOMMON: '안흔함', SPECIAL: '특별', RARE: '희귀', LEGEND: '전설' };

// 증강체: 1·3·5번째 전투 뒤에 3개 중 1개
const AUGMENTS = [
  { id: 'snack', tier: 'silver', name: '새우gang 간식', desc: '전투가 끝날 때마다 체력 25% 더 회복' },
  { id: 'stonehead', tier: 'silver', name: '돌머리', desc: '모두 방어 +20%' },
  { id: 'stamp', tier: 'silver', name: '출근 도장', desc: '전투 첫 턴에는 무조건 내가 먼저' },
  { id: 'market', tier: 'silver', name: '금빛 시장', desc: '치명타 확률 +20%' },
  { id: 'rustle', tier: 'silver', name: '바스락 소리', desc: '전투 시작 때 상대 선두가 1턴 멈춤' },
  { id: 'rookie', tier: 'silver', name: '신입 연수', desc: '흔함·안흔함 갈매미 모든 능력치 +30%' },
  { id: 'photo', tier: 'silver', name: '단체 사진', desc: '모두 최대 체력 +15%' },
  { id: 'overtime', tier: 'gold', name: '야근 수당', desc: '체력이 절반 이하인 갈매미 공격 +35%' },
  { id: 'transfer', tier: 'gold', name: '인사 발령', desc: '교체해도 턴을 쓰지 않음' },
  { id: 'neighbors', tier: 'gold', name: '동네 친구', desc: '같은 동네(첫째 타입)가 2마리 이상이면 그 갈매미들 공격·방어 +20%' },
  { id: 'barnacle', tier: 'gold', name: '따개비 방패', desc: '전투마다 각 갈매미가 처음 받는 공격은 피해 0' },
  { id: 'peanut', tier: 'gold', name: '땅콩 떼기', desc: '특기 재사용 대기 1턴 줄어듦' },
  { id: 'moss', tier: 'gold', name: '이끼 간지럼', desc: '내 공격이 맞으면 20% 확률로 상대가 웃느라 1턴 쉼' },
  { id: 'typhoon', tier: 'prism', name: '큰 태풍', desc: '내 공격 피해 +30%, 받는 피해 +10%' },
  { id: 'five', tier: 'prism', name: '다섯 번째 목소리', desc: '전투마다 다섯 번째 공격은 위력 3배' },
  { id: 'revive', tier: 'prism', name: '다시 출근', desc: '한 번, 쓰러진 갈매미가 체력 50%로 일어남' },
];
const AUG_TIER = { silver: '실버', gold: '골드', prism: '프리즘' };

// ── 상태 ──
let ROSTER = [];
const run = { stage: 1, team: [], augs: new Set(), reviveUsed: false, wins: 0 };
let B = null; // 지금 전투
const $ = (s) => document.querySelector(s);
const app = () => $('#app');
const SPEED = /[?&]fast\b/.test(location.search) ? 0.05 : 1; // 시험용 빠른 진행
const sleep = (ms) => new Promise(r => setTimeout(r, ms * SPEED));
const pickRandom = (arr, n) => { const a = [...arr]; const out = []; while (a.length && out.length < n) out.push(a.splice(Math.floor(Math.random() * a.length), 1)[0]); return out; };
const weighted = (weights) => { const t = Object.values(weights).reduce((a, b) => a + b, 0); let r = Math.random() * t; for (const [k, w] of Object.entries(weights)) { if ((r -= w) < 0) return k; } return Object.keys(weights)[0]; };
const has = (id) => run.augs.has(id);
// 받침에 맞는 조사: j('운석', '이가') → 운석이, j('심해', '이가') → 심해가
const j = (w, pair) => { const c = w.charCodeAt(w.length - 1); const batchim = c >= 0xac00 && c <= 0xd7a3 && (c - 0xac00) % 28 > 0; return esc(w) + (batchim ? pair[0] : pair[1]); };
const esc = (s) => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const typeChips = (types) => types.map(t => `<span class="chip" style="background:${TYPES[t].color}">${TYPES[t].name}</span>`).join(' ');
const med = (id, cls = '') => `<div class="med ${cls}"><img src="/thumbs/${id}.jpg" alt=""></div>`;

// ── 갈매미 한 마리(전투용) ──
function makeFighter(u, side, scale = 1) {
  const f = { ...u, side, scale, cd: 0, sleep: 0, laugh: 0, shield: 0, atkUp: 0, barnacle: false };
  f.maxHp = maxHpOf(f); f.hp = f.maxHp; return f;
}
function maxHpOf(f) {
  let m = f.scale;
  if (f.side === 'me' && has('photo')) m *= 1.15;
  if (f.side === 'me' && has('rookie') && (f.tier === 'COMMON' || f.tier === 'UNCOMMON')) m *= 1.3;
  return Math.round(baseHp(f) * m);
}
function baseHp(f) { return ROSTER.find(r => r.id === f.id).hp; }
// 증강체를 얻은 뒤 체력 비율은 그대로 두고 최대 체력만 다시 계산
function refreshMaxHp() { for (const f of run.team) { const ratio = f.hp / f.maxHp; f.maxHp = maxHpOf(f); f.hp = Math.max(f.hp > 0 ? 1 : 0, Math.round(f.maxHp * ratio)); } }
function friends(f) { const team = f.side === 'me' ? run.team : B.foes; return team.filter(x => x.types[0] === f.types[0]).length >= 2; }
function statAtk(f) {
  let a = f.atk * f.scale;
  if (f.atkUp > 0) a *= 1.4;
  if (f.side === 'me') {
    if (has('rookie') && (f.tier === 'COMMON' || f.tier === 'UNCOMMON')) a *= 1.3;
    if (has('overtime') && f.hp <= f.maxHp / 2) a *= 1.35;
    if (has('neighbors') && friends(f)) a *= 1.2;
  }
  return a;
}
function statDef(f) {
  let d = f.def * f.scale;
  if (f.side === 'me') {
    if (has('stonehead')) d *= 1.2;
    if (has('rookie') && (f.tier === 'COMMON' || f.tier === 'UNCOMMON')) d *= 1.3;
    if (has('neighbors') && friends(f)) d *= 1.2;
  }
  return d;
}
function statSpd(f) { return f.spd * (f.side === 'me' && has('rookie') && (f.tier === 'COMMON' || f.tier === 'UNCOMMON') ? 1.3 : 1); }
function typeMult(atkType, defTypes) {
  let m = 1;
  for (const d of defTypes) { if (CHART[atkType] === d) m *= 1.5; else if (CHART[d] === atkType) m *= 0.67; }
  return m;
}
const movesOf = (f) => [
  ...f.types.map(t => ({ kind: 'attack', type: t, name: TYPES[t].move, power: 50 })),
  { kind: 'skill', type: f.types[0], skill: TYPES[f.types[0]].skill, ...SKILLS[TYPES[f.types[0]].skill] },
];
const skillCd = (f, s) => Math.max(1, s.cd - (f.side === 'me' && has('peanut') ? 1 : 0));

// ── 화면: 시작 ──
function title() {
  app().innerHTML = `
  <div class="center" style="margin-top:6vh">${med('C1')}</div>
  <h1 class="center">수비대 대결</h1>
  <p class="center">갈매미 3마리로 팀을 꾸려 일곱 번의 대결을 이겨 내요.<br>이길 때마다 상대 갈매미를 데려올 수 있고,<br>중간중간 <b style="color:var(--gold)">증강체</b>를 하나씩 골라 팀을 강하게 만들어요.</p>
  <button class="btn" id="go">시작하기</button>
  <button class="btn ghost" id="chart">타입 상성 보기</button>
  <p class="center" style="font-size:14px">시험판: 지금은 후보 중에서 고르고, 나중에는 카톡에서 모은 내 갈매미로 싸워요.</p>`;
  document.querySelector('.center .med').style.cssText = 'width:150px;height:150px;margin:0 auto';
  $('#go').onclick = draft;
  $('#chart').onclick = () => chartScreen(title);
}
function chartScreen(back) {
  app().innerHTML = `<h2>타입 상성</h2><p>왼쪽 타입의 공격은 오른쪽 타입에게 1.5배, 거꾸로 맞으면 0.67배예요. 기본 타입은 상성이 없어요. 타입이 둘인 갈매미는 두 타입 상성을 모두 받아요.</p>
  <table class="chart">${Object.entries(CHART).map(([a, b]) => `<tr><td>${typeChips([a])} → ${typeChips([b])}</td><td>${WHY[a]}</td></tr>`).join('')}</table>
  <h3>동네별 특기</h3><table class="chart">${Object.entries(TYPES).map(([k, t]) => `<tr><td>${typeChips([k])}</td><td>${SKILLS[t.skill].name}: ${SKILLS[t.skill].desc}</td></tr>`).join('')}</table>
  <button class="btn" id="back">돌아가기</button>`;
  $('#back').onclick = back;
}

// ── 화면: 팀 고르기 ──
function draft() {
  const byTier = (t) => ROSTER.filter(r => r.tier === t);
  const cands = []; const used = new Set();
  while (cands.length < 8) {
    const pool = byTier(weighted({ COMMON: 30, UNCOMMON: 30, SPECIAL: 22, RARE: 13, LEGEND: 5 })).filter(r => !used.has(r.id));
    if (!pool.length) continue;
    const u = pool[Math.floor(Math.random() * pool.length)]; used.add(u.id); cands.push(u);
  }
  const sel = new Set();
  const render = () => {
    app().innerHTML = `<h2>팀 고르기 <small style="font-size:16px;color:var(--sub)">${sel.size}/3</small></h2>
    <p>함께 출근할 갈매미 3마리를 골라요. 타입이 겹치지 않으면 상성에 강하고, 겹치면 '동네 친구' 증강체와 잘 맞아요.</p>
    <div class="grid">${cands.map((u, i) => `<button class="pick ${sel.has(i) ? 'sel' : ''}" data-i="${i}">${med(u.id)}<span>${esc(u.name)}</span><span><span class="tier ${u.tier}">${TIER_NAME[u.tier]}</span> ${typeChips(u.types)}</span><small>체력 ${u.hp} · 공격 ${u.atk} · 방어 ${u.def} · 속도 ${u.spd}</small></button>`).join('')}</div>
    <button class="btn" id="ok" ${sel.size === 3 ? '' : 'disabled'}>이 팀으로 출근</button>`;
    app().querySelectorAll('.pick').forEach(b => b.onclick = () => { const i = +b.dataset.i; if (sel.has(i)) sel.delete(i); else if (sel.size < 3) sel.add(i); render(); });
    $('#ok').onclick = () => {
      Object.assign(run, { stage: 1, augs: new Set(), reviveUsed: false, wins: 0 });
      run.team = [...sel].map(i => makeFighter(cands[i], 'me'));
      startBattle();
    };
  };
  render();
}

// ── 상대 팀 ──
function enemyTeam(stage) {
  const weights = stage <= 1 ? { COMMON: 60, UNCOMMON: 40 } : stage <= 3 ? { COMMON: 15, UNCOMMON: 45, SPECIAL: 40 } : stage === 4 ? { SPECIAL: 60, RARE: 40 }
    : stage <= 6 ? { UNCOMMON: 10, SPECIAL: 40, RARE: 40, LEGEND: 10 } : { LEGEND: 100 };
  const scale = 0.78 + 0.06 * (stage - 1);
  const used = new Set(run.team.map(f => f.id)); const out = [];
  while (out.length < 3) {
    const pool = ROSTER.filter(r => r.tier === weighted(weights) && !used.has(r.id));
    if (!pool.length) continue;
    const u = pool[Math.floor(Math.random() * pool.length)]; used.add(u.id); out.push(makeFighter(u, 'foe', scale));
  }
  return out;
}

// ── 전투 ──
function startBattle() {
  const foes = enemyTeam(run.stage);
  const name = run.stage === STAGES ? '섬의 전설' : `${TEAM_NAME[foes[0].types[0]]}${run.stage === 4 ? ' 팀장조' : ''}`;
  B = { foes, me: run.team.findIndex(f => f.hp > 0), foe: 0, turn: 1, attacks: 0, busy: false, name };
  for (const f of [...run.team, ...foes]) Object.assign(f, { cd: 0, sleep: 0, laugh: 0, shield: 0, atkUp: 0, barnacle: f.side === 'me' && has('barnacle') });
  drawBattle();
  (async () => {
    await say(`${run.stage === STAGES ? '마지막 대결! ' : ''}${j(name, '이가')} 대결을 걸어왔어요!`);
    if (has('rustle')) { foeNow().sleep = 1; await say(`어디선가 바스락… ${j(foeNow().name, '이가')} 얼어붙었어요!`); }
    menu();
  })();
}
const meNow = () => run.team[B.me];
const foeNow = () => B.foes[B.foe];

function drawBattle() {
  app().innerHTML = `
  <div class="top"><span>${run.stage}/${STAGES} 대결</span><span class="spacer"></span><span class="augs">${[...run.augs].map(id => `<span>${AUGMENTS.find(a => a.id === id).name}</span>`).join('') || '<span>증강체 없음</span>'}</span></div>
  <div class="field" id="field">
    <div class="panel foe" id="pFoe"></div>
    <div class="slot foe" id="sFoe"></div>
    <div class="slot me" id="sMe"></div>
    <div class="panel me" id="pMe"></div>
  </div>
  <div class="log" id="log"></div>
  <div class="acts" id="acts"></div>
  <div class="bench" id="bench"></div>`;
  refresh(true);
}
function panel(f) {
  const r = Math.max(0, f.hp / f.maxHp), col = r > 0.5 ? 'var(--hp)' : r > 0.2 ? 'var(--mid)' : 'var(--low)';
  const st = [f.sleep ? '쿨쿨' : '', f.laugh ? '웃음' : '', f.shield ? '방패' : '', f.atkUp ? '공격↑' : ''].filter(Boolean).join(' · ');
  return `<div class="nm">${esc(f.name)} <span class="tier ${f.tier}">${TIER_NAME[f.tier]}</span></div><div>${typeChips(f.types)}</div>
  <div class="bar"><i style="width:${r * 100}%;background:${col}"></i></div><div class="hpt"><span>${Math.max(0, f.hp)}/${f.maxHp}</span><span class="st">${st}</span></div>`;
}
function refresh(enter = false) {
  $('#pFoe').innerHTML = panel(foeNow()); $('#pMe').innerHTML = panel(meNow());
  if (enter || $('#sFoe').dataset.id !== foeNow().id) { $('#sFoe').innerHTML = med(foeNow().id, 'enter'); $('#sFoe').dataset.id = foeNow().id; }
  if (enter || $('#sMe').dataset.id !== meNow().id) { $('#sMe').innerHTML = med(meNow().id, 'enter'); $('#sMe').dataset.id = meNow().id; }
  $('#bench').innerHTML = run.team.map((f, i) => `<div class="bm ${i === B.me ? 'on' : ''} ${f.hp <= 0 ? 'down' : ''}">${med(f.id)}<div style="flex:1;min-width:0"><div style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(f.name)}</div><div class="bar"><i style="width:${Math.max(0, f.hp / f.maxHp) * 100}%"></i></div></div></div>`).join('');
}
async function say(text, ms = 750) { $('#log').innerHTML = text; await sleep(ms); }
function floatText(side, text, color = '#fff') {
  const slot = $(side === 'me' ? '#sMe' : '#sFoe'); const d = document.createElement('div');
  d.className = 'float'; d.textContent = text; d.style.color = color;
  const fr = $('#field').getBoundingClientRect(), sr = slot.getBoundingClientRect();
  d.style.left = `${sr.left - fr.left + sr.width / 2 - 20}px`; d.style.top = `${sr.top - fr.top + 10}px`;
  $('#field').appendChild(d); setTimeout(() => d.remove(), 800);
}
function anim(side, cls) { const m = $(side === 'me' ? '#sMe .med' : '#sFoe .med'); if (!m) return; m.classList.remove(cls); void m.offsetWidth; m.classList.add(cls); }

function menu(forceSwitch = false) {
  const f = meNow();
  const acts = $('#acts');
  if (forceSwitch) { $('#log').innerHTML = '다음으로 내보낼 갈매미를 골라요.'; return switchMenu(true); }
  $('#log').innerHTML = `${j(f.name, '은는')} 무엇을 할까요?`;
  acts.innerHTML = movesOf(f).map((m, i) => {
    const vs = typeMult(m.type, foeNow().types);
    const hint = m.kind === 'attack' || m.power ? (vs > 1 ? ' · 효과 좋음' : vs < 1 ? ' · 효과 별로' : '') : '';
    const cdText = m.kind === 'skill' ? (f.cd > 0 ? ` · ${f.cd}턴 뒤` : ` · 대기 ${skillCd(f, m)}턴`) : '';
    return `<button class="act" data-i="${i}" ${m.kind === 'skill' && f.cd > 0 ? 'disabled' : ''}><b>${m.name}</b><small>${TYPES[m.type].name}${m.kind === 'attack' ? ' · 위력 50' : ` · ${m.desc}`}${cdText}${hint}</small></button>`;
  }).join('') + `<button class="act" id="sw"><b>교체</b><small>${has('transfer') ? '인사 발령: 턴을 쓰지 않음' : '대기 중인 갈매미와 바꿔요'}</small></button>`;
  acts.querySelectorAll('[data-i]').forEach(b => b.onclick = () => round({ kind: 'move', move: movesOf(f)[+b.dataset.i] }));
  $('#sw').onclick = () => switchMenu(false);
}
function switchMenu(forced) {
  const acts = $('#acts');
  acts.innerHTML = run.team.map((f, i) => `<button class="act" data-i="${i}" ${f.hp <= 0 || i === B.me ? 'disabled' : ''}><b>${esc(f.name)}</b><small>${typeChips(f.types)} ${Math.max(0, f.hp)}/${f.maxHp}</small></button>`).join('')
    + (forced ? '' : '<button class="act" id="cancel"><b>취소</b><small>돌아가기</small></button>');
  acts.querySelectorAll('[data-i]').forEach(b => b.onclick = async () => {
    const i = +b.dataset.i;
    if (forced) { B.me = i; refresh(); await say(`가라, ${esc(meNow().name)}!`); return menu(); }
    if (has('transfer')) { B.me = i; refresh(); await say(`인사 발령! ${esc(meNow().name)} 출근!`, 600); return menu(); }
    round({ kind: 'switch', to: i });
  });
  if (!forced) $('#cancel').onclick = () => menu();
}

function enemyChoice(f) {
  const ms = movesOf(f); const sk = ms.find(m => m.kind === 'skill');
  if (f.cd === 0) {
    if (sk.skill === 'nap' && f.hp < f.maxHp * 0.55) return sk;
    if (sk.skill === 'shield' && f.hp < f.maxHp * 0.7 && !f.shield) return sk;
    if (['wave', 'meteor', 'ambush', 'sleep', 'dressup'].includes(sk.skill) && Math.random() < 0.5) return sk;
  }
  const attacks = ms.filter(m => m.kind === 'attack');
  return attacks.sort((a, b) => typeMult(b.type, meNow().types) - typeMult(a.type, meNow().types))[0];
}

async function round(action) {
  $('#acts').innerHTML = '';
  const me = meNow(), foe = foeNow();
  const foeMove = enemyChoice(foe);
  if (action.kind === 'switch') {
    B.me = action.to; refresh(); await say(`${esc(me.name)}, 돌아와! 가라, ${esc(meNow().name)}!`);
    await act(foeNow(), meNow(), foeMove);
  } else {
    // 먼저 하는 순서: 출근 도장(첫 턴) > 선공 기술 > 속도
    const myPri = B.turn === 1 && has('stamp') ? 2 : action.move.first ? 1 : 0, foePri = foeMove.first ? 1 : 0;
    const myFirst = myPri !== foePri ? myPri > foePri : statSpd(me) >= statSpd(foe);
    const order = myFirst ? [[me, foe, action.move], [foe, me, foeMove]] : [[foe, me, foeMove], [me, foe, action.move]];
    for (const [a, d, m] of order) {
      if (a.hp <= 0 || d.hp <= 0) continue;
      await act(a, d, m);
      if (await checkFaints()) return;
    }
  }
  if (await checkFaints()) return;
  // 한 턴 끝: 재사용 대기·상태 줄이기
  for (const f of [meNow(), foeNow()]) { if (f.cd > 0) f.cd--; if (f.shield > 0) f.shield--; if (f.atkUp > 0) f.atkUp--; }
  B.turn++; refresh(); menu();
}

async function act(a, d, m) {
  const who = esc(a.name);
  if (a.sleep > 0) { a.sleep--; refresh(); return say(`${j(a.name, '은는')} 쿨쿨 자고 있어요…`); }
  if (a.laugh > 0) { a.laugh--; refresh(); return say(`${j(a.name, '은는')} 이끼가 간지러워 웃느라 움직이지 못해요!`); }
  if (m.kind === 'skill') a.cd = skillCd(a, m) + 1; // 이번 턴 끝에 1 줄어듦
  await say(`${who}의 ${m.name}!`, 500);
  if (m.kind === 'skill' && !m.power) {
    if (m.skill === 'sleep') { d.sleep = 1; refresh(); return say(`"갈매미 한 마리, 두 마리…" ${j(d.name, '이가')} 세다가 잠들었어요!`); }
    if (m.skill === 'shield') { a.shield = 2; refresh(); return say(`${j(a.name, '이가')} 도금 방패를 들었어요!`); }
    if (m.skill === 'nap') { const h = Math.round(a.maxHp * 0.4); a.hp = Math.min(a.maxHp, a.hp + h); floatText(a.side, `+${h}`, '#9ff0a0'); refresh(); return say(`${j(a.name, '이가')} 꽃잎 위에서 낮잠을 자고 기운을 찾았어요!`); }
    if (m.skill === 'dressup') { a.atkUp = 3; refresh(); return say(`${j(a.name, '이가')} 옷을 단단히 차려입었어요! 공격이 올랐어요!`); }
  }
  // 공격
  let power = m.power ?? 50;
  if (a.side === 'me') { B.attacks++; if (has('five') && B.attacks % 5 === 0) { power *= 3; await say('갈매미 하나, 둘, 셋, 넷… 다섯 번째에, 그 목소리가 들려요!', 700); } }
  const mult = typeMult(m.type, d.types);
  const crit = Math.random() < 0.0625 + (a.side === 'me' && has('market') ? 0.2 : 0);
  let dmg = ((statAtk(a) / statDef(d)) * power * 0.5 + 2) * mult * (0.85 + Math.random() * 0.15) * (crit ? 1.5 : 1);
  if (d.shield) dmg *= 0.5;
  if (has('typhoon')) dmg *= a.side === 'me' ? 1.3 : 1.1;
  dmg = Math.max(1, Math.round(dmg));
  if (d.side === 'me' && d.barnacle) { d.barnacle = false; dmg = 0; }
  anim(a.side, a.side === 'me' ? 'lunge-me' : 'lunge-foe');
  await sleep(180);
  d.hp -= dmg; anim(d.side, 'hit'); floatText(d.side, dmg ? `-${dmg}` : '0', crit ? '#ffe27a' : '#fff'); refresh();
  if (!dmg) await say('따개비 방패가 공격을 막았어요!', 650);
  if (crit && dmg) await say('급소에 맞았어요!', 550);
  if (dmg && mult > 1) await say('효과가 굉장해요!', 600); else if (dmg && mult < 1) await say('효과가 별로예요…', 600);
  if (a.side === 'me' && dmg && d.hp > 0 && has('moss') && Math.random() < 0.2) { d.laugh = 1; refresh(); await say(`${j(d.name, '이가')} 이끼 때문에 간지러워 웃음이 터졌어요!`, 650); }
}

// 쓰러짐 처리. 전투가 끝나거나 교체 화면으로 넘어가면 true
async function checkFaints() {
  const foe = foeNow(), me = meNow();
  if (foe.hp <= 0) {
    anim('foe', 'faint'); await say(`${j(foe.name, '이가')} 쓰러졌어요!`);
    const next = B.foes.findIndex(f => f.hp > 0);
    if (next < 0) { await win(); return true; }
    B.foe = next; refresh(); await say(`${j(B.name, '은는')} ${j(foeNow().name, '을를')} 내보냈어요!`);
  }
  if (me.hp <= 0) {
    if (has('revive') && !run.reviveUsed) {
      run.reviveUsed = true; me.hp = Math.round(me.maxHp / 2); refresh();
      await say(`${j(me.name, '이가')} 쓰러지는 순간… "다시 출근!" 체력 절반으로 일어났어요!`, 1000); return false;
    }
    anim('me', 'faint'); await say(`${j(me.name, '이가')} 쓰러졌어요…`);
    if (!run.team.some(f => f.hp > 0)) { await lose(); return true; }
    menu(true); return true;
  }
  return false;
}

async function win() {
  run.wins++;
  await say(`${j(B.name, '을를')} 이겼어요!`, 900);
  if (run.stage === STAGES) return result(true);
  // 회복: 기본 50%(+간식 25%), 쓰러진 갈매미는 체력 30%로 깨어남
  const heal = 0.5 + (has('snack') ? 0.25 : 0);
  for (const f of run.team) f.hp = f.hp <= 0 ? Math.round(f.maxHp * 0.3) : Math.min(f.maxHp, f.hp + Math.round(f.maxHp * heal));
  const defeated = B.foes;
  run.stage++;
  const next = () => ([2, 4, 6].includes(run.stage) ? augmentScreen(startBattle) : startBattle());
  scoutScreen(defeated, next);
}

// ── 스카우트: 이긴 상대 갈매미를 데려오기(포켓몬 잡기 느낌) ──
function scoutScreen(defeated, next) {
  let pick = null;
  const render = () => {
    app().innerHTML = `<h2>스카우트</h2><p>방금 이긴 상대 중 한 마리를 팀에 데려올 수 있어요. 데려오면 지금 팀에서 한 마리가 떠나요.</p>
    <div class="grid">${defeated.map((u, i) => `<button class="pick ${pick === i ? 'sel' : ''}" data-i="${i}">${med(u.id)}<span>${esc(u.name)}</span><span><span class="tier ${u.tier}">${TIER_NAME[u.tier]}</span> ${typeChips(u.types)}</span></button>`).join('')}</div>
    ${pick === null ? '<button class="btn ghost" id="skip">데려오지 않기</button>' : `<h3>누구와 바꿀까요?</h3><div class="grid">${run.team.map((f, i) => `<button class="pick" data-t="${i}">${med(f.id)}<span>${esc(f.name)}</span><small>${Math.max(0, f.hp)}/${f.maxHp}</small></button>`).join('')}</div><button class="btn ghost" id="back">다시 고르기</button>`}`;
    app().querySelectorAll('[data-i]').forEach(b => b.onclick = () => { pick = +b.dataset.i; render(); });
    app().querySelectorAll('[data-t]').forEach(b => b.onclick = () => {
      const u = ROSTER.find(r => r.id === defeated[pick].id);
      run.team[+b.dataset.t] = makeFighter(u, 'me'); next();
    });
    $('#skip')?.addEventListener('click', next);
    $('#back')?.addEventListener('click', () => { pick = null; render(); });
  };
  render();
}

// ── 증강체 고르기 ──
function augmentScreen(next) {
  const tier = run.stage === 2 ? ['silver', 'silver', 'gold'] : run.stage === 4 ? ['gold', 'gold', 'gold'] : ['gold', 'prism', 'prism'];
  const offered = [];
  for (const t of tier) {
    const pool = AUGMENTS.filter(a => a.tier === t && !run.augs.has(a.id) && !offered.includes(a));
    if (pool.length) offered.push(pool[Math.floor(Math.random() * pool.length)]);
  }
  app().innerHTML = `<h2>증강체 고르기</h2><p>셋 중 하나를 골라요. 이번 출근이 끝날 때까지 계속 적용돼요.</p>
  ${offered.map((a, i) => `<button class="aug ${a.tier}" data-i="${i}"><small>${AUG_TIER[a.tier]}</small><b>${a.name}</b><span>${a.desc}</span></button>`).join('')}`;
  app().querySelectorAll('[data-i]').forEach(b => b.onclick = () => { run.augs.add(offered[+b.dataset.i].id); refreshMaxHp(); next(); });
}

async function lose() { await say('모두 쓰러졌어요… 오늘은 여기까지.', 1000); result(false); }
function result(won) {
  app().innerHTML = `<div class="center" style="margin-top:4vh">${run.team.map(f => med(f.id)).join('')}</div>
  <h1 class="center">${won ? '전 대결 승리!' : '오늘은 지각…'}</h1>
  <p class="center">${won ? '섬의 전설까지 이겨 냈어요. 내일도 출근해요!' : `${run.stage}번째 대결에서 멈췄어요. (승리 ${run.wins}번)`}</p>
  <div class="augs" style="justify-content:center">${[...run.augs].map(id => `<span>${AUGMENTS.find(a => a.id === id).name}</span>`).join('')}</div>
  <button class="btn" id="again">다시 출근하기</button><a class="btn ghost center" href="/" style="text-decoration:none">홈페이지로</a>`;
  app().querySelectorAll('.center .med').forEach(m => { m.style.cssText = 'width:84px;height:84px;display:inline-block;margin:0 4px'; });
  $('#again').onclick = draft;
}

// ── 시작 ──
const font = document.fonts ? Promise.race([document.fonts.load('20px Jua'), sleep(1500)]) : Promise.resolve();
Promise.all([fetch('/battle/roster.json').then(r => r.json()), font]).then(([roster]) => {
  ROSTER = roster;
  if (/[?&]test\b/.test(location.search)) window.__battle = { run, get B() { return B; }, ROSTER, typeMult };
  title();
}).catch(() => { app().innerHTML = '<p>갈매미 정보를 불러오지 못했어요. 새로고침해 주세요.</p>'; });
