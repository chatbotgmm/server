// PC 웹게임(60종, roster60) 1단계: 로그인 · 마을 · 내 갈매미 · 모집 · 스토리(대화·편성·전투·정산·귀환)
// 서버는 앱과 같은 기능(/my/api/native/roster60/*)을 쓴다. 금액·확률·승패·경험치는 모두 서버가 정하고, 화면은 보여 주기만 한다.
// 앱과 같은 「현재 증강(current-v3)」 전투를 처리하므로 같은 판 표시 머리글을 보낸다(권한이 아니라 호환 표시).
const HEAD = {'x-gmm': '1', 'x-gmm-roster60-augment-capability': 'current-v3', 'x-gmm-roster60-augment-model': 'roster60-current-v1'};
const A = '/game60/assets/';
const stage = document.getElementById('stage'), screen = document.getElementById('screen');
const toastBox = document.getElementById('toast');

// ── 화면 크기: 960×540을 창에 맞춰 통째로 확대·축소 ──
function fit() {
  const w = Math.max(960, Math.min(1280, Math.round(540 * innerWidth / innerHeight))), s = Math.min(innerWidth / w, innerHeight / 540);
  stage.style.width = w + 'px'; stage.style.transform = `translate(-50%,-50%) scale(${s})`;
}
addEventListener('resize', fit); fit();

// ── 서버 호출 ──
class ApiError extends Error { constructor(status, code, msg) { super(msg || code); this.status = status; this.code = code; } }
async function api(path, body) {
  const init = {method: body ? 'POST' : 'GET', credentials: 'same-origin', headers: body ? {...HEAD, 'content-type': 'application/json'} : HEAD};
  if (body) init.body = JSON.stringify(body);
  let r; try { r = await fetch('/my/api/' + path, init); } catch { throw new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.'); }
  let j = {}; try { j = await r.json(); } catch {}
  if (r.status === 401) throw new ApiError(401, 'LOGIN', '로그인이 필요해요.');
  if (!r.ok) throw new ApiError(r.status, j.code || j.error || 'ERROR', j.message || errorText(j.code || j.error));
  return j;
}
const R = p => 'native/roster60/' + p;
const rid = () => (crypto.randomUUID ? crypto.randomUUID() : 'w' + Date.now().toString(36) + Math.random().toString(36).slice(2));
const ERR = {
  INSUFFICIENT_CREDITS: '지원서가 부족해요.', RECRUIT_SESSION_STALE: '모집 결과가 바뀌었어요. 다시 불러왔어요.', BATTLE_STALE: '전투 상태가 바뀌었어요. 다시 불러왔어요.',
  FORCED_SWITCH_REQUIRED: '쓰러진 갈매미를 먼저 교체해 주세요.', STORY_SCENE_STALE: '이야기 진행이 바뀌었어요. 다시 불러왔어요.', PARTY_INVALID: '편성을 다시 확인해 주세요.',
  BATTLE_NOT_FINISHED: '전투가 아직 끝나지 않았어요.', NOT_ENOUGH_MP: 'MP가 부족해요.', ROSTER60_AUGMENT_CLIENT_UPDATE_REQUIRED: '이 전투는 지금 화면에서 이어갈 수 없어요.',
};
const errorText = code => ERR[code] || `요청을 처리하지 못했어요${code ? ` (${code})` : ''}.`;
function toast(msg) { toastBox.textContent = msg; toastBox.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => { toastBox.hidden = true; }, 2800); }
async function guard(run) {
  try { return await run(); }
  catch (e) { if (e.status === 401) { showLogin(); return; } toast(e.message || errorText(e.code)); console.warn(e); }
}

// ── 공통 표시 ──
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const GRADE = {COMMON: '커먼', UNCOMMON: '언커먼', RARE: '레어', EPIC: '에픽', LEGEND: '전설', HIDDEN: '히든', LIMITED: '제한', TRANSCEND: '초월', IMMORTAL: '불멸', ETERNAL: '영원'};
const ELEM = {NORMAL: '일반', FIRE: '불', WATER: '물', LIGHTNING: '번개', ICE: '얼음', NATURE: '자연', HOLY: '성', DARK: '어둠'};
const STAT = [['hp', 'HP'], ['mp', 'MP'], ['durability', '내구'], ['statusResistance', '저항'], ['speed', '속도']];
const BLACK = new Set(['u01', 'u02', 'u06', 'u07', 'u14', 'u23', 'u28', 'u31']);
// 앱과 같은 임시 빌런 그림 별칭(StoryBattleArt)
const ALIAS = {story_record: 'u01', story_guide: 'u02', story_engineer: 'u22', story_escort: 'u03', story_dark_first: 'u31'};
const front = id => `${A}units/${ALIAS[id] || id}-front.webp`;
const rear = id => `${A}units/${ALIAS[id] || id}-rear.webp`;
function foeArt(f) { const base = ALIAS[f.speciesId] || f.speciesId; return (f.artVariant === 'black' || ALIAS[f.speciesId]) && BLACK.has(base) ? `${A}black/${base}.webp` : front(f.speciesId); }
const bg = file => `<div class="bg" style="background-image:url('${A}${file}')"></div>`;
function top(title, sub = '', right = '', back = 'village') {
  return `<header class="top">${back ? `<button class="back" data-go="${back}"><img src="${A}ui/icon-back.webp" alt="">뒤로</button>` : ''}<h1>${esc(title)}</h1><span class="sub">${esc(sub)}</span><span class="grow"></span>${right}</header>`;
}
const credits = () => `<span class="chip" title="지원서"><img src="${A}menu/currency-application.webp" alt="">지원서 ${me.credits ?? '-'}</span>`;
function render(html, wire) { const keep = {}; screen.querySelectorAll('[data-keep]').forEach(el => { keep[el.dataset.keep] = el.scrollTop; }); screen.innerHTML = html; screen.querySelectorAll('[data-keep]').forEach(el => { if (keep[el.dataset.keep]) el.scrollTop = keep[el.dataset.keep]; }); screen.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => go(b.dataset.go))); wire?.(); }
const on = (sel, ev, fn) => screen.querySelectorAll(sel).forEach(el => el.addEventListener(ev, e => fn(el, e)));
let busy = false;
async function once(run) { if (busy) return; busy = true; try { await guard(run); } finally { busy = false; } }

// ── 상태 ──
const me = {credits: null, nickname: null};
async function refreshMe() { const w = await api('who'); me.credits = w.credits; me.nickname = w.nickname; }

// ── 화면 이동 ──
const SCREENS = {};
function go(name, arg) { guard(() => SCREENS[name](arg)); }

// ── 로그인 ──
function showLogin() {
  render(`${bg('village/identity-coast-panorama.webp')}<div class="center"><div class="card">
<h2>갈매미맨 방범대</h2><p>앱과 같은 계정으로 이어서 해요. 로그인 방법을 골라 주세요.</p>
<div class="row"><a class="btn primary" href="/login?next=/game60&from=/">로그인하기</a><a class="btn" href="/">홈페이지로</a></div>
</div></div>`);
}

// ── 시작: 로그인 확인 → 첫 갈매미 지급(처음 한 번) → 마을 ──
SCREENS.boot = async () => {
  try { await refreshMe(); } catch (e) { if (e.status === 401) return showLogin(); throw e; }
  const st = await api(R('state'));
  if (!st.started) {
    const s = await api(R('start'), {});
    toast(`방범대에 온 것을 환영해요! 첫 동료 ${s.starter?.name ?? ''}가 합류했어요.`);
  }
  go('village');
};

// ── 마을(로비): 갈매미 건물 = 메뉴 ──
SCREENS.village = async () => {
  const story = await api(R('story/state')).catch(() => null);
  const cmd = story?.commander;
  const B = (cls, img, w, x, title, sub, to, disabled = false) => `<button class="bld ${cls}" ${disabled ? 'disabled title="2단계에서 열려요"' : ''} data-go="${to}"><img src="${A}village/${img}.webp" alt="" style="width:${w}px"><span class="plate">${title}<small>${sub}</small></span></button>`;
  render(`<div class="village"><div class="pano" style="background-image:url('${A}village/identity-coast-panorama.webp')"></div><div class="shade"></div>
<div class="blds">${B('', 'identity-hq', 250, 12, '동료의 거리', '모집 · 내 갈매미', 'recruit')}
${B('', 'identity-records', 230, 262, '기억의 정원', '도감 · 우편 (준비 중)', 'village', true)}
${B('', 'identity-ship', 230, 488, '푸른 출항길', '스토리', 'story')}
${B('', 'identity-market', 230, 716, '바람의 시장', '시장 (준비 중)', 'village', true)}</div>
</div>
<div class="hud"><div class="who"><b>${esc(me.nickname || '방범대장')}</b><span>${cmd ? `방범대장 Lv.${cmd.level}` : '방범대'}</span></div><span style="flex:1"></span>${credits()}<a class="btn quiet" href="/" style="min-height:40px">홈페이지</a></div>
<nav class="dock" aria-label="마을 메뉴">
<button data-go="story"><img src="${A}menu/icon-explore.webp" alt="">스토리</button>
<button data-go="recruit"><img src="${A}menu/icon-draw.webp" alt="">모집</button>
<button data-go="units"><img src="${A}menu/icon-team.webp" alt="">내 갈매미</button>
<button disabled title="2단계에서 열려요"><img src="${A}menu/icon-combine.webp" alt="">조합</button>
<button disabled title="2단계에서 열려요"><img src="${A}menu/icon-collection.webp" alt="">도감</button>
</nav>`);
};

// ── 내 갈매미: 목록 + 상세 ──
// ── 정렬·같은 갈매미 묶기(내 갈매미·편성 공용). 고른 값은 이 브라우저에 기억 ──
// 종족은 서버 목록에 없어 방범대 등록증 v10 자료(art/gpt/registration-v10/manifest.json)의 종족을 쓴다(검토안, 전투 시너지 확정 아님)
const TRIBE = {u01:'태초',u02:'태초',u03:'거인',u04:'수목',u05:'태초',u06:'정령',u07:'정령',u08:'망령',u09:'태초',u10:'정령',u11:'태초',u12:'해인',u13:'태초',u14:'정령',u15:'요정',u16:'정령',u17:'태초',u18:'요정',u19:'거인',u20:'해인',u21:'거인',u22:'야수',u23:'태초',u24:'용족',u25:'태초',u26:'수목',u27:'야수',u28:'해인',u29:'태초',u30:'야수',u31:'망령',u32:'용족',u33:'태초',u34:'수목',u35:'해인',u36:'야수',u37:'야수',u38:'천익',u39:'태초',u40:'요정',u41:'정령',u42:'망령',u43:'태초',u44:'수목',u45:'거인',u46:'야수',u47:'거인',u48:'정령',u49:'천익',u50:'야수',u51:'수목',u52:'망령',u53:'천익',u54:'정령',u55:'용족',u56:'태초',u57:'천익',u58:'거인',u59:'망령',u60:'용족'};
const TRIBE_ORDER = ['태초', '거인', '수목', '정령', '망령', '해인', '요정', '야수', '용족', '천익'];
const GRADE_RANK = Object.fromEntries(['ETERNAL', 'IMMORTAL', 'TRANSCEND', 'LIMITED', 'HIDDEN', 'LEGEND', 'EPIC', 'RARE', 'UNCOMMON', 'COMMON'].map((g, i) => [g, i]));
const SORTS = [['level', '레벨순'], ['tribe', '종족순'], ['name', '이름순'], ['grade', '등급순']];
const pref = {get: (k, d) => { try { return localStorage.getItem('g60-' + k) ?? d; } catch { return d; } }, set: (k, v) => { try { localStorage.setItem('g60-' + k, v); } catch {} }};
const byName = (a, b) => a.name.localeCompare(b.name, 'ko');
const rank = u => GRADE_RANK[u.rarity] ?? 99, tribeRank = u => { const t = TRIBE_ORDER.indexOf(TRIBE[u.speciesId]); return t < 0 ? 99 : t; };
const CMP = {
  level: (a, b) => b.level - a.level || rank(a) - rank(b) || byName(a, b),
  tribe: (a, b) => tribeRank(a) - tribeRank(b) || a.speciesId.localeCompare(b.speciesId) || b.level - a.level,
  name: (a, b) => byName(a, b) || b.level - a.level,
  grade: (a, b) => rank(a) - rank(b) || byName(a, b) || b.level - a.level,
};
// 정렬하고, 묶기면 같은 종을 한 칸으로(대표는 레벨이 가장 높은 개체)
function arrange(units, sort, grouped) {
  const cmp = CMP[sort] || CMP.level;
  if (!grouped) return units.slice().sort(cmp).map(u => ({rep: u, all: [u]}));
  const m = new Map(); for (const u of units) { if (!m.has(u.speciesId)) m.set(u.speciesId, []); m.get(u.speciesId).push(u); }
  return [...m.values()].map(all => { all.sort((a, b) => b.level - a.level); return {rep: all[0], all}; }).sort((x, y) => cmp(x.rep, y.rep));
}
const toolbar = (sort, grouped) => `<div class="toolbar"><span class="seg" role="group" aria-label="정렬">${SORTS.map(([k, n]) => `<button data-sort="${k}" aria-pressed="${k === sort}">${n}</button>`).join('')}</span><button class="toggle" data-group aria-pressed="${grouped}">같은 갈매미 묶기</button></div>`;
const STATUS = {IN_ADVENTURE: '모험 중', AVAILABLE: ''};

// ── 내 갈매미: 목록 + 상세 ──
SCREENS.units = async (sel) => {
  const list = (await api(R('owned'))).units ?? [];
  let pick = sel || null, sort = pref.get('sort', 'level'), grouped = pref.get('group', '1') === '1';
  const draw = async () => {
    const rows = arrange(list, sort, grouped);
    if (!list.some(u => u.ownedId === pick)) pick = rows[0]?.rep.ownedId;
    const cur = list.find(u => u.ownedId === pick);
    const sib = cur ? list.filter(u => u.speciesId === cur.speciesId).sort((a, b) => b.level - a.level) : [];
    const det = cur ? (await api(R('owned/read'), {ownedId: cur.ownedId})).unit : null;
    const kinds = new Set(list.map(u => u.speciesId)).size;
    render(`${bg('village/identity-coast-panorama.webp')}${top('내 갈매미', `${list.length}마리 · ${kinds}종`, credits())}
<section class="panel" style="left:8px;top:64px;width:560px;bottom:8px;padding:10px;display:flex;flex-direction:column;gap:8px">${toolbar(sort, grouped)}
<div class="grid scroll" data-keep="units" style="grid-template-columns:repeat(4,1fr);align-content:start">${rows.map(({rep: u, all}) => { const on = all.some(x => x.ownedId === pick), st = STATUS[u.status] ?? u.status; return `<button class="slot${on ? ' on' : ''}" data-id="${u.ownedId}"><span class="art"><img src="${front(u.speciesId)}" alt="" loading="lazy"></span><span class="nm">${esc(u.name)}</span><span class="lv">${GRADE[u.rarity] ?? u.rarity} · ${all.length > 1 ? '최고 ' : ''}Lv.${u.level}</span>${all.length > 1 ? `<span class="badge2 count">×${all.length}</span>` : ''}${st && all.length === 1 ? `<span class="badge tag">${esc(st)}</span>` : ''}</button>`; }).join('') || '<p class="muted">아직 갈매미가 없어요.</p>'}</div>
</section>
<section class="panel detail" style="left:576px;right:8px;top:64px;bottom:8px;padding:12px">${det ? unitDetail(det, sib, pick) : '<p class="muted">갈매미를 골라 주세요.</p>'}</section>`, () => {
      on('[data-id]', 'click', b => { pick = b.dataset.id; draw(); });
      on('[data-copy]', 'click', b => { pick = b.dataset.copy; draw(); });
      on('[data-sort]', 'click', b => { sort = b.dataset.sort; pref.set('sort', sort); screen.querySelector('[data-keep]')?.scrollTo(0, 0); draw(); });
      on('[data-group]', 'click', () => { grouped = !grouped; pref.set('group', grouped ? '1' : '0'); draw(); });
    });
  };
  await draw();
};
function unitDetail(u, sib = [], pick = u.ownedId) {
  const g = u.growth ?? {}, s = u.stats ?? {};
  const xp = g.level >= g.maxLevel ? '최고 레벨' : `다음 레벨까지 ${g.xpToNext ?? '-'} XP`;
  return `<h2 style="margin:0;font-size:20px">${esc(u.name)}</h2><div class="grade">${GRADE[u.rarity] ?? u.rarity} · Lv.${u.level}/${u.maxLevel}${u.reincarnation ? ` · 환생 ${u.reincarnation}` : ''}</div>
${sib.length > 1 ? `<div class="copies" aria-label="같은 갈매미 ${sib.length}마리">${sib.map(x => `<button data-copy="${x.ownedId}" aria-pressed="${x.ownedId === pick}">Lv.${x.level}${STATUS[x.status] ? ` · ${STATUS[x.status]}` : ''}</button>`).join('')}</div>` : ''}
<div class="big${sib.length > 1 ? ' short' : ''}"><img src="${front(u.speciesId)}" alt="${esc(u.name)}"></div>
<div class="muted" style="font-size:14px;margin:4px 0">${xp}</div>
<div class="stats">${STAT.map(([k, n]) => `<div>${n}<b>${s[k] ?? '-'}</b></div>`).join('')}</div>
<ul class="skills scroll" style="margin-top:8px">${[u.skills?.attack, ...(u.skills?.current ?? [])].filter(Boolean).map(k => `<li><span>${esc(k.name)} <span class="muted">${ELEM[k.element] ?? k.element}</span></span><span class="muted">위력 ${k.power} · MP ${k.mpCost}</span></li>`).join('')}</ul>`;
}

// ── 모집: 하급·중급·고급(서버 모집 세션) ──
SCREENS.recruit = async () => {
  const info = await api(R('recruit/session'));
  me.credits = info.credits;
  if (info.pendingSessions?.length) return recruitResults(info.pendingSessions);
  const qty = {};
  const draw = () => render(`${bg('village/identity-coast-panorama.webp')}${top('모집', '지원서로 새 동료를 만나요', credits())}
<section class="panel" style="left:8px;right:8px;top:64px;bottom:8px;padding:14px;display:flex;flex-direction:column;gap:12px">
<div class="grid" style="grid-template-columns:repeat(3,1fr);flex:1">${(info.offers ?? []).map(o => { const n = qty[o.kind] ?? 1; return `<div class="offer">
<h3>${esc(o.label)} 모집</h3><div class="muted">지원서 ${o.cost}장 · 다시 뽑기 ${o.rerollCost}장</div>
<div class="odds">${Object.entries(o.weights).map(([g, w]) => `${GRADE[g] ?? g} ${w}%`).join(' · ')}</div>
<div class="qty">${[1, 5, 10].map(k => `<button data-k="${o.kind}" data-n="${k}" aria-pressed="${n === k}">${k}회</button>`).join('')}</div>
<button class="btn primary" data-start="${o.kind}" ${info.enabled === false || (me.credits ?? 0) < o.cost * n ? 'disabled' : ''}>모집하기<small>지원서 ${o.cost * n}장</small></button></div>`; }).join('')}</div>
<p class="muted" style="margin:0;font-size:14px">같은 등급 안에서는 모두 같은 확률이에요. 모집 횟수에 따른 보장은 없어요. <a class="gold" href="/odds" target="_blank" rel="noopener">모집 확률 자세히</a></p></section>`, () => {
    on('[data-k]', 'click', b => { qty[b.dataset.k] = +b.dataset.n; draw(); });
    on('[data-start]', 'click', b => once(async () => {
      const o = info.offers.find(x => x.kind === b.dataset.start);
      const r = await api(R('recruit/session/start'), {requestId: rid(), kind: o.kind, catalogVersion: info.catalogVersion, times: qty[o.kind] ?? 1});
      me.credits = r.credits;
      recruitResults(r.pendingSessions?.length ? r.pendingSessions : r.sessions);
    }));
  });
  draw();
};
function recruitResults(sessions) {
  let list = sessions.slice();
  // 서버가 돌려준 최신 대기 목록으로 카드 상태(불합격 가능 여부 등)를 맞춘다
  const sync = r => { me.credits = r.credits ?? me.credits; if (r.sessionId) Object.assign(list.find(x => x.sessionId === r.sessionId) ?? {}, {status: r.status, revision: r.revision, candidate: r.candidate, rerolls: r.rerolls, canReject: r.canReject, rejectRefund: r.rejectRefund, ownedSpecies: r.ownedSpecies});
    for (const p of r.pendingSessions ?? []) Object.assign(list.find(x => x.sessionId === p.sessionId) ?? {}, p); };
  // 중복: 이미 가진 종(보유 중), 또는 이번 결과 안에서 같은 종이 두 번째 이후로 나온 후보
  const dupOf = pending => { const seen = new Set(), out = new Set(); for (const s of pending) { const id = s.candidate?.speciesId; if (s.ownedSpecies || seen.has(id)) out.add(s.sessionId); seen.add(id); } return out; };
  const draw = () => {
    const pending = list.filter(s => s.status === 'PENDING'), dups = dupOf(pending);
    render(`${bg('village/identity-coast-panorama.webp')}${top('모집 결과', `${pending.length}명 확인 대기${dups.size ? ` · 중복 ${dups.size}` : ''}`, credits(), pending.length ? '' : 'village')}
<section class="panel" style="left:8px;right:8px;top:64px;bottom:8px;padding:12px;display:flex;flex-direction:column;gap:10px">
<div class="grid scroll" data-keep="recruit" style="grid-template-columns:repeat(5,1fr);align-content:start">${list.map(s => { const c = s.candidate ?? {}, done = s.status !== 'PENDING', dup = dups.has(s.sessionId); return `<div class="slot cand${done ? '' : ' on'}"><span class="art"><img src="${front(c.speciesId)}" alt="">
${done ? '' : `<button class="reroll" data-a="reroll" data-s="${s.sessionId}" ${s.rerolls ? 'disabled' : ''} title="다시 뽑기 · 지원서 ${s.rerollCost}장 (같은 등급에서 한 번)" aria-label="다시 뽑기, 지원서 ${s.rerollCost}장"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/><path d="M20 4v5h-5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg><small>${s.rerollCost}</small></button>`}
${done ? `<span class="badge tag">${s.status === 'CLAIMED' ? '합류' : s.status === 'REJECTED' ? '불합격' : esc(s.status)}</span>` : s.ownedSpecies ? '<span class="badge tag dup">보유 중</span>' : dup ? '<span class="badge tag dup">중복</span>' : ''}</span>
<span class="nm">${esc(c.name)}</span><span class="lv">${GRADE[c.rarity] ?? c.rarity} · Lv.${c.level}</span>
${!done && s.canReject ? `<span class="acts"><button class="btn quiet" data-a="reject" data-s="${s.sessionId}">불합격 <small>지원서 ${s.rejectRefund}장 돌려받기</small></button></span>` : ''}</div>`; }).join('')}</div>
<div style="display:flex;gap:10px;justify-content:flex-end;align-items:center;flex:none"><span class="muted" style="margin-right:auto;font-size:14px">새로고침 아이콘: 같은 등급에서 한 번 다시 뽑기 · 불합격: 이미 가진 종만, 지원서 1장 돌려받기</span><button class="btn" data-go="units">내 갈매미</button>
${dups.size ? `<button class="btn" data-dedupe>중복 정리 (${dups.size})</button>` : ''}${pending.length ? `<button class="btn primary" data-all>모두 합류 (${pending.length})</button>` : '<button class="btn primary" data-go="recruit">모집 계속</button>'}</div></section>`, () => {
      on('[data-a]', 'click', b => once(async () => {
        const s = list.find(x => x.sessionId === b.dataset.s);
        const r = await api(R('recruit/session/action'), {requestId: rid(), sessionId: s.sessionId, revision: s.revision, action: b.dataset.a});
        sync(r); if (b.dataset.a === 'reject') toast('지원서를 돌려받았어요.');
        draw();
      }));
      // 중복 정리: 이번 결과에서 겹친 종은 한 마리를 먼저 합류(그래야 서버 규칙상 나머지를 불합격할 수 있음) → 불합격 가능한 중복을 모두 불합격
      on('[data-dedupe]', 'click', () => {
        if (!confirm(`중복 ${dups.size}명을 불합격 처리하고 지원서를 돌려받을까요?\n같은 갈매미는 조합 재료로도 쓸 수 있어요. 겹친 종은 한 마리씩 먼저 합류해요.`)) return;
        once(async () => {
          let refunded = 0;
          for (const s of list.filter(x => x.status === 'PENDING' && dups.has(x.sessionId) && !x.ownedSpecies)) {
            const first = list.find(x => x.status === 'PENDING' && x.candidate?.speciesId === s.candidate?.speciesId && !dups.has(x.sessionId));
            if (first) sync(await api(R('recruit/session/action'), {requestId: rid(), sessionId: first.sessionId, revision: first.revision, action: 'claim'}));
          }
          for (const s of list.filter(x => x.status === 'PENDING' && dups.has(x.sessionId))) {
            const cur = list.find(x => x.sessionId === s.sessionId);
            if (!cur.canReject) continue;
            sync(await api(R('recruit/session/action'), {requestId: rid(), sessionId: cur.sessionId, revision: cur.revision, action: 'reject'})); refunded += cur.rejectRefund || 1;
          }
          toast(refunded ? `중복 정리: 지원서 ${refunded}장을 돌려받았어요.` : '불합격할 수 있는 중복이 없어요.'); draw();
        });
      });
      on('[data-all]', 'click', () => once(async () => {
        const pend = list.filter(s => s.status === 'PENDING');
        const r = await api(R('recruit/session/claim-all'), {requestId: rid(), sessions: pend.map(s => ({sessionId: s.sessionId, revision: s.revision}))});
        me.credits = r.credits; for (const s of pend) s.status = 'CLAIMED';
        toast(`${pend.length}명이 방범대에 합류했어요.`); draw();
      }));
    });
  };
  draw();
}

// ── 스토리 ──
const PLACE = {village_statue: ['identity-statue', '마을 석상 터'], final_statue: ['identity-statue', '석상 앞'], human_records: ['identity-records', '인간의 기록 장소'], hideout: ['identity-hq', '은신처'], memory_workshop: ['identity-records', '기억 속 작업장'], defense_village: ['identity-hq', '마을 방어 준비']};
SCREENS.story = async () => {
  const st = await api(R('story/state'));
  if (st.scene) return storyScene(st);
  const q = st.currentQuest;
  const active = q && q.status === 'ACTIVE';
  const adv = active && q.adventureId ? (await api(R('adventure/active'))).adventures?.find(a => a.adventureId === q.adventureId) : null;
  const goal = !q ? (st.nextStoryHint || '지금 들을 수 있는 이야기를 모두 들었어요.')
    : q.status === 'READY' ? '마을에 돌아왔어요. 후속 이야기를 들을 수 있어요.'
    : active ? `현재 의뢰 · 현장 확인 ${q.objective?.wins ?? 0}/${q.objective?.requiredWins ?? 1}${adv ? `\n모험 진행 중 · ${adv.party?.length ?? 0}마리 출전` : ''}`
    : '짧은 이야기를 듣고 의뢰를 수락해 주세요.';
  const lv = q?.recommendedLevel?.length === 2 ? `\n권장 레벨 ${q.recommendedLevel[0]}~${q.recommendedLevel[1]} · 입장 제한 아님` : '';
  const done = (q?.objective?.wins ?? 0) >= (q?.objective?.requiredWins ?? 1);
  const acts = !q ? '' : q.status === 'READY' ? `<button class="btn primary" data-open="return">귀환 이야기 듣기</button>`
    : active ? (adv?.activeBattleId ? `<button class="btn primary" data-resume="${adv.activeBattleId}">전투 이어가기</button>`
      : done && adv ? `<button class="btn primary" data-return="${adv.adventureId}" data-rev="${adv.revision}">마을로 귀환<small>모은 보상을 받아요</small></button>`
      : `<button class="btn primary" data-form>편성하고 출발</button>`)
    : `<button class="btn primary" data-open="intro">대화 이어가기</button>`;
  const QS = {COMPLETED: '완료', ACTIVE: '진행 중', READY: '귀환 대기', AVAILABLE: '열림'};
  const chapters = st.quests?.length ? `<ul class="chapters scroll">${st.quests.map(x => `<li class="${x.id === q?.id ? 'now' : ''}"><b>${x.chapter}장</b><span style="flex:1">${esc(x.title)}</span><span>${QS[x.status] ?? esc(x.status)}</span></li>`).join('')}</ul>` : '';
  render(`${bg('village/identity-coast-panorama.webp')}${top('스토리', q ? `챕터 ${q.chapter}` : '', credits())}
<section class="panel quest"><span class="tag" style="align-self:flex-start">${q?.kind === 'main' ? '본편' : '이야기'}</span><h2>${esc(q?.title ?? '다음 이야기')}</h2><div class="goal">${esc(goal + lv)}</div>${chapters}<div class="acts">${acts}</div></section>
<section class="panel" style="left:544px;right:8px;top:66px;bottom:16px;padding:14px;display:flex;flex-direction:column;gap:8px">
<img src="${A}village/${PLACE.village_statue[0]}.webp" alt="" style="height:230px;object-fit:contain;filter:drop-shadow(0 6px 10px rgba(0,0,0,.5))">
<div class="muted" style="font-size:14px;text-align:center">석상 복원 ${st.statue?.stage ?? 1}단계${st.statue?.hatched ? ' · 본편 완료' : ''}</div>
<div class="muted" style="font-size:14px">방범대장 Lv.${st.commander?.level ?? 1} · 다음 레벨까지 ${st.commander?.xpToNext ?? '-'} XP</div></section>`, () => {
    // 의뢰가 많으면 목록이 길어진다: 지금 의뢰가 보이게 목록 안에서만 스크롤(판·버튼은 그대로)
    { const list = screen.querySelector('.chapters'), now = list?.querySelector('.now'); if (now) list.scrollTop = now.offsetTop - list.offsetTop - (list.clientHeight - now.offsetHeight) / 2; }
    on('[data-open]', 'click', b => once(async () => storyScene(await api(R('story/open'), {requestId: rid(), revision: st.revision, questId: q.id, phase: b.dataset.open}))));
    on('[data-form]', 'click', () => formation(q, adv));
    on('[data-resume]', 'click', b => once(async () => battleScreen(await api(R('battle/read'), {battleId: b.dataset.resume}), q)));
    on('[data-return]', 'click', b => once(async () => {
      const r = await api(R('adventure/return'), {requestId: rid(), adventureId: b.dataset.return, revision: +b.dataset.rev});
      returnResult(r);
    }));
  });
};
function storyScene(st) {
  const sc = st.scene, [bld, place] = PLACE[sc.background] ?? PLACE.village_statue, l = sc.line ?? {};
  const narr = l.speakerId === 'narrator' || !l.speakerName;
  const label = !sc.last ? '다음' : sc.phase === 'intro' ? '의뢰 수락' : sc.phase === 'return' ? '이야기 완료' : '닫기';
  render(`<div class="scene"><div class="place" style="background-image:url('${A}village/identity-coast-panorama.webp')"></div><div class="bg" style="background:linear-gradient(rgba(7,19,28,.15),rgba(7,19,28,.55))"></div>
<img class="bldg" src="${A}village/${bld}.webp" alt="">${String(sc.camera ?? '').includes('hero') ? `<img class="hero" src="${rear('u01')}" alt="갈매미맨의 뒷모습">` : ''}
<div class="caption">${esc(place)}<small>${sc.phase === 'return' ? '귀환 이야기' : sc.phase === 'replay' ? '다시 보기' : '이야기'}</small></div>
<button class="back" style="position:absolute;left:14px;top:12px" data-close><img src="${A}ui/icon-back.webp" alt="">나가기</button>
<div class="dialog"><div class="say"><div class="who">${narr ? '' : esc(l.speakerName)}</div><div class="line${narr ? ' narr' : ''}">${esc(l.text)}</div></div><button class="btn primary" data-next style="min-width:150px">${label}</button></div></div>`, () => {
    on('[data-next]', 'click', () => once(async () => {
      if (sc.last && sc.phase === 'intro') {
        const r = await api(R('story/accept'), {requestId: rid(), revision: st.revision, questId: sc.questId});
        toast('의뢰를 수락했어요. 편성하고 출발해 주세요.'); return SCREENS.story(r);
      }
      const r = await api(R('story/advance'), {requestId: rid(), revision: st.revision, cursor: sc.cursor});
      if (r.scene) return storyScene(r);
      if (sc.phase === 'return') toast('의뢰를 마쳤어요!');
      go('story');
    }));
    on('[data-close]', 'click', () => once(async () => { await api(R('story/close'), {requestId: rid(), revision: st.revision}); go('story'); }));
  });
}

// ── 편성: 1~3마리, 첫 번째가 출전, 나머지 예비 ──
async function formation(q, adv) {
  const units = (await api(R('owned'))).units?.filter(u => u.status === 'AVAILABLE') ?? [];
  const MAX = 3; let sort = pref.get('sort', 'level'), grouped = pref.get('group', '1') === '1';
  let picked = arrange(units, 'level', false).slice(0, 1).map(r => r.rep.ownedId);
  const role = i => i ? `예비 ${i}` : '출전';
  const draw = () => {
    const rows = arrange(units, sort, grouped), chosen = picked.map(id => units.find(u => u.ownedId === id));
    render(`${bg('village/identity-coast-panorama.webp')}${top('전투 편성', `스토리 · 출전 1 + 예비 ${MAX - 1}`, '', 'story')}
<section class="panel" style="left:8px;top:64px;width:640px;bottom:8px;padding:10px;display:flex;flex-direction:column;gap:8px">${toolbar(sort, grouped)}
<div class="muted" style="font-size:14px;flex:none">누른 순서대로 편성돼요. 첫 번째 갈매미가 먼저 출전해요.${grouped ? ' 묶인 칸은 누를 때마다 레벨 높은 개체부터 들어가요.' : ''}</div>
<div class="grid scroll" data-keep="formation" style="grid-template-columns:repeat(5,1fr);align-content:start">${rows.map(({rep: u, all}) => { const idx = all.map(x => picked.indexOf(x.ownedId)).filter(i => i >= 0).sort(); return `<button class="slot" aria-pressed="${idx.length > 0}" data-sp="${u.speciesId}" data-id="${u.ownedId}"><span class="art"><img src="${front(u.speciesId)}" alt="" loading="lazy"></span><span class="nm">${esc(u.name)}</span><span class="lv">${GRADE[u.rarity] ?? ''} · ${all.length > 1 ? '최고 ' : ''}Lv.${u.level}</span>${idx.length ? `<span class="badge tag role">${idx.length > 1 ? `편성 ${idx.length}` : role(idx[0])}</span>` : ''}${all.length > 1 ? `<span class="badge2 count">×${all.length}</span>` : ''}</button>`; }).join('')}</div>
</section>
<section class="panel" style="left:656px;right:8px;top:64px;bottom:8px;padding:12px;display:flex;flex-direction:column;gap:8px">
<h2 style="margin:0;font-size:18px">출전 ${chosen.length}/${MAX}</h2>
${chosen.map((u, i) => `<button class="opt" data-rm="${u.ownedId}" title="눌러서 빼기"><span><b>${esc(u.name)}</b><br><span>${role(i)} · Lv.${u.level} · 눌러서 빼기</span></span><img src="${front(u.speciesId)}" alt="" style="height:44px"></button>`).join('')}
<div style="margin-top:auto" class="muted">${q ? esc(q.title) : ''}</div>
<button class="btn primary" data-go-battle ${chosen.length ? '' : 'disabled'}>편성 확정 · 전투 시작</button></section>`, () => {
      on('[data-id]', 'click', b => {
        const all = grouped ? units.filter(u => u.speciesId === b.dataset.sp).sort((x, y) => y.level - x.level) : [units.find(u => u.ownedId === b.dataset.id)];
        const free = all.filter(u => !picked.includes(u.ownedId));
        if (all.length === 1 && picked.includes(all[0].ownedId)) picked.splice(picked.indexOf(all[0].ownedId), 1);
        else if (free.length && picked.length < MAX) picked.push(free[0].ownedId);
        else if (!free.length) { const last = [...picked].reverse().find(id => all.some(u => u.ownedId === id)); picked.splice(picked.indexOf(last), 1); }
        else toast(`최대 ${MAX}마리까지 편성할 수 있어요.`);
        draw();
      });
      on('[data-rm]', 'click', b => { picked.splice(picked.indexOf(b.dataset.rm), 1); draw(); });
      on('[data-sort]', 'click', b => { sort = b.dataset.sort; pref.set('sort', sort); screen.querySelector('[data-keep]')?.scrollTo(0, 0); draw(); });
      on('[data-group]', 'click', () => { grouped = !grouped; pref.set('group', grouped ? '1' : '0'); draw(); });
      on('[data-go-battle]', 'click', () => once(async () => {
        const body = {requestId: rid(), questId: q.id, ownedIds: picked};
        if (adv?.adventureId) body.adventureId = adv.adventureId;
        battleScreen(await api(R('story/battle/create'), body), q);
      }));
    });
  };
  draw();
}

// ── 전투 ──
function hpRow(label, v, max, cls = '') { const p = Math.max(0, Math.min(100, max ? v / max * 100 : 0)); return `<div class="row"><span>${label}</span><span class="meter ${cls}"><i style="width:${p}%"></i></span><span>${v}/${max}</span></div>`; }
function defenseMark(skill, intel) {
  const d = intel?.defenses?.[skill.element];
  return d === 'weak' ? '<span class="up">▲유리 ×1.5</span>' : d === 'resist' ? '<span class="down">▼불리 ×0.67</span>' : d === 'unknown' ? '<span>상성 미확인</span>' : '';
}
function eventLine(v) {
  const turn = v.state.events?.at(-1); if (!turn) return '';
  const names = {self: v.state.fighters[0]?.name, enemy: v.state.fighters[1]?.name};
  return (turn.events ?? []).map(e => {
    const who = esc(e.presentation?.before?.find(x => x.id === e.actor)?.name ?? names[e.actor] ?? '');
    if (e.kind === 'attack' || e.kind === 'skill') return `${who}의 ${esc(e.presentation?.skill?.name ?? (e.kind === 'attack' ? '공격' : '기술'))}! ${e.damage ?? 0} 피해${e.defense === 'weak' ? ' (효과가 굉장했다)' : e.defense === 'resist' ? ' (효과가 별로)' : ''}`;
    if (e.kind === 'switch') return `${who} 교체`;
    if (e.kind === 'focus') return `${who} 집중`;
    if (e.kind === 'item') return `${who} 아이템 사용`;
    return '';
  }).filter(Boolean).slice(-2).join('<br>');
}
function battleScreen(v, q) {
  const st = v.state, my = st.fighters[0], foe = st.fighters[1], ac = st.augmentChoice, intel = st.opponentIntel;
  if (v.status === 'FINISHED') return settleBattle(v, q);
  const forced = my.hp <= 0;
  const off = v.disabledCommands ?? {};
  const reserves = (st.reserves ?? []).filter(r => r.ownedId);
  let mode = 'main';
  const draw = () => {
    const panel = mode === 'skill' ? `<h2>기술 선택</h2><div class="list">${(my.skills ?? []).map(k => `<button class="opt" data-skill="${k.id}" ${k.mpCost > my.mp ? 'disabled' : ''}><span><b>${esc(k.name)}</b><br><span>${ELEM[k.element] ?? k.element} · 위력 ${k.power} · MP ${k.mpCost}</span></span>${defenseMark(k, intel)}</button>`).join('') || '<p class="muted">쓸 수 있는 기술이 없어요.</p>'}</div><button class="btn quiet" data-mode="main">돌아가기</button>`
      : mode === 'switch' || forced ? `<h2>${forced ? '다음에 나갈 갈매미' : '교체'}</h2><div class="list">${reserves.map(r => `<button class="opt" data-switch="${r.ownedId}" ${r.hp <= 0 ? 'disabled' : ''}><span><b>${esc(r.name)}</b><br><span>Lv.${r.level} · HP ${r.hp}/${r.stats?.hp} · MP ${r.mp}/${r.stats?.mp}</span></span><img src="${front(r.speciesId)}" alt="" style="height:42px"></button>`).join('') || '<p class="muted">예비 갈매미가 없어요.</p>'}</div>${forced ? '' : '<button class="btn quiet" data-mode="main">돌아가기</button>'}`
      : mode === 'item' ? `<h2>아이템</h2><div class="list">${(st.inventory ?? []).map(it => { const d = v.itemDefinitions?.[it.itemId ?? it.id] ?? {}; return `<button class="opt" data-item="${esc(it.itemId ?? it.id)}" ${(it.count ?? 1) < 1 ? 'disabled' : ''}><span><b>${esc(d.name ?? it.itemId)}</b><br><span>${esc(d.desc ?? '')}</span></span><span>×${it.count ?? 1}</span></button>`; }).join('') || '<p class="muted">가진 아이템이 없어요.</p>'}</div><button class="btn quiet" data-mode="main">돌아가기</button>`
      : `<h2>행동 선택</h2><div class="cmdgrid">
<button class="cmd red" data-act="attack"><img src="${A}ui/icon-attack.webp" alt="">공격</button>
<button class="cmd red" data-mode="skill"><img src="${A}ui/icon-goldenstrike.webp" alt="">기술</button>
<button class="cmd blue" data-act="focus"><img src="${A}ui/icon-shield.webp" alt="">집중</button>
<button class="cmd green" data-mode="item" ${off.item ? 'disabled' : ''}><img src="${A}ui/icon-item.webp" alt="">아이템</button>
<button class="cmd blue" data-mode="switch" ${off.switch || !reserves.some(r => r.hp > 0) ? 'disabled' : ''}><img src="${A}ui/icon-switch.webp" alt="">교체</button>
<button class="cmd gray" data-act="escape" ${off.escape ? 'disabled' : ''}><img src="${A}ui/icon-surrender.webp" alt="">도망치기</button></div>`;
    render(`${bg('village/identity-coast-panorama.webp')}<header class="top"><button class="back" data-go="story"><img src="${A}ui/icon-back.webp" alt="">스토리</button><h1>턴 ${st.turn + 1}</h1><span class="sub">${forced ? '쓰러진 갈매미를 교체해 주세요' : '행동을 선택하세요'}</span><span class="grow"></span>${q ? `<span class="sub">${esc(q.title)}</span>` : ''}</header>
<div class="field" style="background-image:url('${A}ui/bg-harbor.webp')">
<img class="foe-art" src="${foeArt(foe)}" alt="${esc(foe.name)}"><img class="me-art" src="${rear(my.speciesId)}" alt="${esc(my.name)}의 뒷모습">
<div class="bar foe"><div class="nm">상대 · ${esc(foe.name)} Lv${foe.level}</div>${hpRow('HP', foe.hp, foe.stats?.hp)}${hpRow('MP', foe.mp, foe.stats?.mp, 'mp')}</div>
<div class="bar mine"><div class="nm">출전 · ${esc(my.name)} Lv${my.level}</div>${hpRow('HP', my.hp, my.stats?.hp)}${hpRow('MP', my.mp, my.stats?.mp, 'mp')}</div>
${eventLine(v) ? `<div class="log">${eventLine(v)}</div>` : ''}</div>
<section class="panel cmds">${panel}</section>
${ac && ac.phase === 'choose' && !ac.selected && !forced ? `<div class="overlay"><div class="sheet"><h2>증강체 선택 · ${ac.round}라운드</h2><p class="muted" style="margin:0">하나를 골라요. 30초 안에 고르지 않으면 무작위로 정해져요.</p>
<div class="row">${ac.offers.map((o, i) => `<div style="flex:1;display:flex;flex-direction:column;gap:6px"><button class="aug" data-aug="${o.id}"><span class="tier">${o.tier === 'gold' ? '골드' : o.tier === 'silver' ? '실버' : o.tier === 'prism' ? '프리즘' : esc(o.tier)}</span><b>${esc(o.name)}</b><p>${esc(o.desc)}</p></button><button class="btn quiet" data-reroll="${i}" ${ac.rerolled?.[i] ? 'disabled' : ''}>이 칸 다시 뽑기</button></div>`).join('')}</div>
${ac.ownedDefinitions?.length ? `<div class="muted" style="font-size:13px">가진 증강체: ${ac.ownedDefinitions.map(d => esc(d.name)).join(', ')}</div>` : ''}</div></div>` : ''}`, () => {
      on('[data-mode]', 'click', b => { mode = b.dataset.mode; draw(); });
      const act = action => once(async () => { const r = await api(R('battle/action'), {requestId: rid(), battleId: v.battleId, revision: v.revision, action}); battleScreen(r, q); });
      on('[data-act]', 'click', b => { if (b.dataset.act === 'escape' && !confirm('도망칠까요? 이번 모험에서 받지 않은 보상을 잃을 수 있어요.')) return; act({kind: b.dataset.act}); });
      on('[data-skill]', 'click', b => act({kind: 'skill', skillId: b.dataset.skill}));
      on('[data-switch]', 'click', b => act({kind: 'switch', targetOwnedId: b.dataset.switch}));
      on('[data-item]', 'click', b => act({kind: 'item', itemId: b.dataset.item}));
      on('[data-aug]', 'click', b => act({kind: 'augment', augmentId: b.dataset.aug, phaseId: ac.phaseId}));
      on('[data-reroll]', 'click', b => act({kind: 'augment-reroll', slot: +b.dataset.reroll, phaseId: ac.phaseId}));
    });
  };
  draw();
}
async function settleBattle(v, q) {
  const s = v.settlement ? v : await api(R('battle/settle'), {requestId: rid(), battleId: v.battleId, revision: v.revision});
  const set = s.settlement ?? {}, win = set.outcome === 'win';
  const owned = (await api(R('owned'))).units ?? [];
  const xp = Object.entries(set.xpByOwnedId ?? {});
  const pending = set.unitXp?.delivery === 'pending-return' || set.adventureStatus === 'ACTIVE';
  render(`${bg('village/identity-coast-panorama.webp')}<div class="overlay"><div class="sheet result"><h2>${win ? '승리!' : set.outcome === 'escape' ? '도망쳤어요' : '패배'}</h2>
<p class="muted" style="margin:0">${win ? (pending ? '경험치는 모험에서 정상 귀환하면 받아요.' : '경험치를 받았어요.') : '이번 전투에서는 경험치를 받지 못했어요.'}</p>
${win && xp.length ? `<div class="units">${xp.map(([id, n]) => { const u = owned.find(x => x.ownedId === id); return `<div class="u"><img src="${front(u?.speciesId)}" alt=""><span><b>${esc(u?.name ?? '갈매미')}</b><br><span class="xp">+${n} XP</span></span></div>`; }).join('')}</div>` : ''}
<div class="row" style="justify-content:flex-end"><button class="btn primary" data-go="story">스토리로</button></div></div></div>`);
}
function returnResult(r) {
  const xp = r.unitXp ?? r.settlement?.unitXp ?? r.rewards?.unitXp;
  toast('모험에서 귀환했어요. 모은 보상을 받았어요.');
  go('story');
  return xp;
}

go('boot');
