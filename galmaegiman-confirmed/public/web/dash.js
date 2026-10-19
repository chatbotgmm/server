// 갈매미맨 웹 대시보드(v0.14.0): 홈·뽑기·조합·보관함·개체 상세. 기존 「내 갈매미」 API(/my/api/*)만 씀, 새 규칙 없음
// 데이터: me(보유 종류·수·재화) · draw(뽑기 정보) · craft(조합식·재료) · story(최상위 갈매미의 스토리 레벨·기술·승진·직무 표창) · /duel/data.json(속성·역할·기술 이름)
// 즐겨찾기는 서버에 칸이 없어 이 기기(localStorage)에만 저장 — 화면에도 그렇게 적음
(() => {
  'use strict';
  // 공개 홈페이지 로그인(/login)에서 카카오로 넘어온 경우(10분 안): 서버가 /my로 돌려보내므로 원래 목적지로 이어 보냄
  try {
    const ret = JSON.parse(sessionStorage.getItem('gmm-login-return') || 'null');
    if (ret && location.pathname === '/my' && Date.now() - ret.at < 600e3) {
      sessionStorage.removeItem('gmm-login-return');
      const next = ret.next === '/account' ? '/account' : '/game', from = /^\/(?!\/)[A-Za-z0-9/_-]*$/.test(ret.from) ? ret.from : '/';
      const k = new URLSearchParams(location.search).get('kakao');
      if (k === 'cancel') {
        const was = JSON.parse(sessionStorage.getItem('gmm-from') || 'null');
        sessionStorage.setItem('gmm-restore', JSON.stringify({ path: from, y: was && was.path === from ? was.y : 0, at: Date.now() }));
        location.replace(from); return;
      }
      location.replace(k === null || k === 'linked' ? next : `/login?next=${next}&from=${encodeURIComponent(from)}&kakao=${encodeURIComponent(k)}`); return;
    }
  } catch { /* 저장 안 되는 브라우저: 그대로 /my */ }
  const $ = s => document.querySelector(s), main = $('#main');
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const icon = (n, extra = '') => `<svg class="ico" ${extra} aria-hidden="true"><use href="#i-${n}"/></svg>`;
  const TIER_COLOR = { COMMON: '#8a8a8a', UNCOMMON: '#2f8f5b', SPECIAL: '#8a7a6a', RARE: '#1f8fb0', EPIC: '#7a4fd0', LEGEND: '#d9a400', HIDDEN: '#5b3d8f', LIMITED: '#d9483b', TRANSCEND: '#2a6fc0', ETERNAL: '#0f8a7b', IMMORTAL: '#c79100', NEUTRAL: '#8a7a6a' };
  const TIER_RANK = ['COMMON', 'NEUTRAL', 'UNCOMMON', 'SPECIAL', 'RARE', 'EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL'];
  const ROLE = { ATTACK: '공격', DEFENSE: '수비', UTILITY: '유틸' };
  // 그림 자리: web/img/<이름>이 있으면 그 그림, 없으면 같은 크기의 점선 칸(파일 이름 표시)
  const art = (file, cls = '', style = '') => `<div class="art ${cls}" data-art="${file}" style="${style}"><img src="/web/img/${file}" alt="" onerror="this.parentNode.classList.add('miss')"></div>`;
  // 캐릭터: 모든 갈매미를 기존 카드 그림(public/images)으로. 테두리·이름판만 잘라 냄(v0.16.0: 최상위 33종의 새 그림 web/img/char-*.webp는 카드와 달라서 쓰지 않음)
  const ch = (id, cls = '') => `<div class="ch ${cls}"><img class="card" src="/images/${encodeURIComponent(id)}.png" alt=""></div>`;
  const thumb = img => `<div class="ch"><img class="card" src="/thumbs/${encodeURIComponent(img)}.jpg" alt=""></div>`;   // 서버가 준 그림 이름(안 밝혀진 갈매미는 가려진 그림)
  const tag = (tier, name) => `<span class="tag" style="--tc:${TIER_COLOR[tier] ?? '#8a7a6a'}">${esc(name)}</span>`;
  const loading = (t = '불러오는 중…') => `<div class="state" role="status"><div class="spin"></div>${esc(t)}</div>`;
  const failed = (msg, retry) => { main.innerHTML = `<div class="state" role="alert"><b class="err">${esc(msg)}</b><button class="btn ghost sm" id="retry">${icon('retry')}다시 시도</button></div>`; $('#retry').onclick = retry; };
  const toast = t => { document.querySelector('.toast')?.remove(); const e = document.createElement('div'); e.className = 'toast'; e.setAttribute('role', 'status'); e.textContent = t; document.body.append(e); setTimeout(() => e.remove(), 2600); };

  async function api(path, body) {
    const r = await fetch(`/my/api/${path}`, body === undefined ? { headers: { 'x-gmm': '1' } } : { method: 'POST', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { const e = new Error('login'); e.info = j; throw e; }
    if (!r.ok) throw new Error(j.error || `오류(${r.status})`);
    return j;
  }
  // ── 상태 ──
  let me = null, story = null, rules = null, sel = null;
  const FAV_KEY = 'gmm-web-fav';
  const favs = new Set((() => { try { return JSON.parse(localStorage.getItem(FAV_KEY) || '[]'); } catch { return []; } })());
  const saveFav = () => { try { localStorage.setItem(FAV_KEY, JSON.stringify([...favs])); } catch { /* 저장 안 되는 브라우저 */ } };
  const lvOf = id => story?.data?.units?.[id] ?? null;   // 스토리 진행이 있는 최상위 갈매미만
  const unitOf = id => me.units.find(u => u.id === id);
  async function loadRules() {
    if (rules) return rules;
    const { k } = await api('duel-key');
    const r = await fetch(`/duel/data.json?k=${encodeURIComponent(k)}`); if (!r.ok) throw new Error('게임 자료를 받지 못했어요');
    rules = (await r.json()).rules; return rules;
  }

  // ── 상단·하단 메뉴 ──
  const PAGES = [['home', '홈', 'home'], ['draw', '뽑기', 'egg'], ['craft', '조합', 'craft'], ['box', '보관함', 'box'], ['unit', '관리', 'crown']];
  const PUBLIC = [['/dex', '도감', '📚'], ['/recipes', '조합법', '📜'], ['/rank', '랭킹', '🏆'], ['/event', '이벤트', '🎁']];
  function chrome(page) {
    const items = PAGES.map(([h, t, i]) => `<a href="#${h === 'unit' && sel ? `unit=${sel}` : h}" class="${page === h ? 'on' : ''}" ${page === h ? 'aria-current="page"' : ''}>${icon(i)}<span>${t}</span></a>`).join('');
    if (!me) {   // 로그인 전(v0.16.0): 공개 페이지 메뉴 + 로그인 버튼
      const pub = PUBLIC.map(([h, t, e]) => `<a href="${h}"><i aria-hidden="true">${e}</i><span>${t}</span></a>`).join('');
      $('#nav').innerHTML = pub; $('#tabbar').innerHTML = pub + `<a href="#login" class="on">${icon('home')}<span>로그인</span></a>`;
      $('#me').innerHTML = `<a class="btn sm lgin" href="#login">🔑 로그인</a>`; return;
    }
    $('#nav').innerHTML = items; $('#tabbar').innerHTML = items;
    const lead = me.battleTeam[0] || me.units[0]?.id;
    $('#me').innerHTML = `<div class="cur" aria-label="재화"><span title="새우gang">🍤 ${me.snack.toLocaleString('ko-KR')}</span><span title="땅콩">🥜 ${me.peanut.toLocaleString('ko-KR')}</span><span title="뽑기권">🎫 ${me.credits.toLocaleString('ko-KR')}</span></div>
      <button class="avatar" id="av" aria-haspopup="menu" aria-expanded="false" aria-label="계정 메뉴"><span class="face">${lead ? `<img src="/thumbs/${encodeURIComponent(lead)}.jpg" alt="">` : ''}</span>${icon('down')}</button>`;
    $('#av').onclick = e => { e.stopPropagation(); acctMenu(); };
  }
  function acctMenu() {
    const old = document.querySelector('.menu'); if (old) { old.remove(); $('#av')?.setAttribute('aria-expanded', 'false'); return; }
    const m = document.createElement('div'); m.className = 'menu'; m.setAttribute('role', 'menu');
    m.innerHTML = `<b>${esc(me.nickname || '이름 없는 수비조장')}</b><small>도감 ${me.dex}/${me.dexTotal} · 🍤 ${me.snack} · 🥜 ${me.peanut} · 🎫 ${me.credits}</small>
      <a role="menuitem" href="/my/classic#party">${icon('box')}탐험대 편성</a><a role="menuitem" href="/my/classic#shop">${icon('egg')}상점·아이템</a>
      <a role="menuitem" href="/my/classic#account">${icon('gear')}계정(카카오 연결·탈퇴)</a><button role="menuitem" id="toGame">${icon('game')}수비대 대결(게임)</button><button role="menuitem" id="logout">${icon('out')}로그아웃</button>`;
    $('#me').append(m); $('#av').setAttribute('aria-expanded', 'true');
    m.querySelector('#logout').onclick = async () => { await api('logout', {}).catch(() => {}); location.reload(); };
    m.querySelector('#toGame').onclick = toGame;
    const off = e => { if (!m.contains(e.target)) { m.remove(); $('#av')?.setAttribute('aria-expanded', 'false'); removeEventListener('click', off); } };
    setTimeout(() => addEventListener('click', off));
  }
  async function toGame() { try { const { k } = await api('duel-key'); location.href = `/duel?k=${encodeURIComponent(k)}`; } catch (e) { toast(e.message); } }

  // ── 로그인(기존과 같은 방식: 카톡 6자리 코드, 카카오 로그인) ──
  function loginView(info = {}) {
    chrome('home');
    const q = new URLSearchParams(location.search).get('kakao');
    const msg = { cancel: '카카오 로그인을 취소했어요.', fail: '카카오 로그인에 실패했어요. 잠시 뒤 다시 해 주세요.', taken: '이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.' }[q] || '';
    main.innerHTML = `<div class="panel login"><div class="ph">${icon('home')}<h2>${info.linking ? '카카오 계정 처음 연결' : '로그인'}</h2></div>
      ${info.kakao && !info.linking ? '<a class="btn wide kakao" href="/my/kakao">카카오로 로그인</a><p class="note" style="margin:14px 0 4px">또는 카톡 코드로 로그인</p>' : ''}
      <p class="note">카톡 갈매미맨 채널에 <b>웹 로그인</b>이라고 보내고, 받은 6자리 코드를 적어요. (10분 안에 한 번만)</p>
      <label class="sr" for="code">로그인 코드</label><input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000">
      <p class="note err" id="lerr" role="alert">${esc(msg)}</p><button class="btn wide" id="go">로그인</button></div>`;
    const go = async () => {
      const code = $('#code').value.replace(/\D/g, ''); if (code.length !== 6) { $('#lerr').textContent = '6자리 숫자를 입력해 주세요.'; return; }
      $('#go').disabled = true;
      try { await api('login', { code }); history.replaceState(null, '', location.pathname + location.hash); boot(); } catch (e) { $('#lerr').textContent = e.message === 'login' ? '다시 시도해 주세요.' : e.message; $('#go').disabled = false; }
    };
    $('#go').onclick = go; $('#code').onkeydown = e => { if (e.key === 'Enter') go(); };
  }

  // ── 로그인 전 소개 화면(v0.16.0): 메인(/)은 서버가 넣어 보냄, /my·로그인이 풀린 때는 받아 옴 ──
  const IN_KEY = 'gmm-in';
  const setIn = v => { try { v ? localStorage.setItem(IN_KEY, '1') : localStorage.removeItem(IN_KEY); } catch { /* 저장 안 되는 브라우저 */ } document.documentElement.classList.remove('pre-in'); };
  async function landingView(info = {}) {
    me = null; setIn(false);
    if (info.linking) return loginView(info);   // 카카오 계정 처음 연결은 기존 로그인 창(안내 문구가 다름)
    if (!main.dataset.landing) {
      try { const r = await fetch('/web/landing'); if (!r.ok) throw new Error(); main.innerHTML = await r.text(); main.dataset.landing = '1'; }
      catch { return loginView(info); }
    }
    chrome('home'); document.body.classList.add('on-home');
    const q = new URLSearchParams(location.search).get('kakao');
    const msg = { cancel: '카카오 로그인을 취소했어요.', fail: '카카오 로그인에 실패했어요. 잠시 뒤 다시 해 주세요.', taken: '이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.' }[q] || '';
    if (info.kakao) $('#kslot').innerHTML = '<a class="btn wide kakao" href="/my/kakao">카카오로 로그인</a><p class="note" style="margin:14px 0 4px">또는 카톡 코드로 로그인</p>';
    $('#lerr').textContent = msg;
    const go = async () => {
      const code = $('#code').value.replace(/\D/g, ''); if (code.length !== 6) { $('#lerr').textContent = '6자리 숫자를 입력해 주세요.'; return; }
      $('#go').disabled = true;
      try { await api('login', { code }); delete main.dataset.landing; history.replaceState(null, '', location.pathname + (location.hash === '#login' ? '' : location.hash)); boot(); }
      catch (e) { $('#lerr').textContent = e.message === 'login' ? '다시 시도해 주세요.' : e.message; $('#go').disabled = false; }
    };
    $('#go').onclick = go; $('#code').onkeydown = e => { if (e.key === 'Enter') go(); };
    if (location.hash === '#login' || msg) $('#login').scrollIntoView({ block: 'center' });
  }

  // ── 홈 ──
  async function home() {
    chrome('home');
    const lead = me.battleTeam[0] || me.units.find(u => u.top)?.id || me.units[0]?.id;
    main.innerHTML = `<div class="home">
      <section class="panel p-draw"><div class="ph">${icon('egg')}<h2>뽑기</h2></div>${art('draw-machine.webp', 'hd-art')}
        <p class="note" style="margin-bottom:12px">뽑기권 <b>${me.credits.toLocaleString('ko-KR')}장</b> 가지고 있어요.</p><a class="btn coral wide" href="#draw">뽑기</a></section>
      <section class="panel p-craft" id="hCraft"><div class="ph">${icon('craft')}<h2>조합</h2></div>${loading('조합식 확인 중…')}</section>
      <section class="panel p-unit" id="hUnit"><div class="ph">${icon('crown')}<h2>관리</h2></div>${lead ? unitSummary(lead) : '<p class="note">아직 갈매미가 없어요. 뽑기부터 해 보세요.</p>'}</section>
      <section class="panel p-box"><div class="ph">${icon('box')}<h2>보관함</h2><span class="sp"></span><span class="note">${me.units.length}종 · ${me.units.reduce((a, u) => a + u.have, 0)}마리</span><a class="btn ghost sm" href="#box">모두 보기</a></div>
        <div class="cards">${me.units.slice().sort(favFirst).slice(0, 6).map(u => unitCard(u)).join('') || '<p class="note">아직 갈매미가 없어요.</p>'}</div></section></div>`;
    bindCards(main, id => { location.hash = `unit=${id}`; });
    // 조합 미리보기: 지금 만들 수 있는 첫 조합식(없으면 가장 가까운 것)
    try {
      const c = await api('craft'), r = c.recipes.find(x => x.ready) || c.recipes[0], box = $('#hCraft'); if (!box) return;
      box.innerHTML = `<div class="ph">${icon('craft')}<h2>조합</h2></div>${r ? `${recipeSlots(r)}<p class="note" style="margin-bottom:12px">${r.ready ? `지금 <b>${esc(r.name)}</b>을(를) 만들 수 있어요.` : '재료가 모자라요. 재료를 더 모아 보세요.'}</p>` : '<p class="note">아직 볼 수 있는 조합식이 없어요.</p>'}
        <a class="btn lav wide" href="#craft${r ? `=${r.id}` : ''}">조합</a>`;
    } catch (e) { const box = $('#hCraft'); if (box) box.innerHTML = `<div class="ph">${icon('craft')}<h2>조합</h2></div><p class="note err">${esc(e.message)}</p><a class="btn lav wide" href="#craft">조합</a>`; }
  }
  const favFirst = (a, b) => Number(favs.has(b.id)) - Number(favs.has(a.id));
  function unitSummary(id) {
    const u = unitOf(id), lv = lvOf(id), aug = (lv?.evoAugs ?? []);
    return `<div class="unit-hero">${ch(id)}<div class="stat3">
      <div><small>레벨</small><b>${lv ? `Lv. ${lv.level}` : '–'}</b>${lv ? `<div class="bar"><i style="width:${xpPct(lv)}%"></i></div>` : `<p>${u?.top ? '스토리에 나가면 생겨요' : '최상위만 레벨이 있어요'}</p>`}</div>
      <div><small>특성</small><b style="font-size:16px">${esc(u?.tierName ?? '')}</b><p>${esc(u?.synergy ? `${u.synergy} 시너지` : '시너지 없음')}</p></div>
      <div><small>직무 표창</small><b>${aug.length}개</b><p>${lv ? `승진 ${lv.stage ?? 0}단계` : '–'}</p></div></div>
      <a class="btn ghost" href="#unit=${encodeURIComponent(id)}">${esc(u?.name ?? id)} 자세히</a></div>`;
  }
  function xpPct(lv) { const g = rules?.growth ?? { xpBase: 20, xpExp: 1.5 }; return Math.min(100, Math.round((lv.xp ?? 0) / Math.round(g.xpBase * lv.level ** g.xpExp) * 100)); }
  function unitCard(u, selected = false) {
    const lv = lvOf(u.id);
    return `<div class="card ${selected ? 'sel' : ''}" role="button" tabindex="0" data-id="${esc(u.id)}" aria-label="${esc(u.name)} ${esc(u.tierName)} ${u.have}마리">
      <button class="fav ${favs.has(u.id) ? 'on' : ''}" data-fav="${esc(u.id)}" aria-label="즐겨찾기" aria-pressed="${favs.has(u.id)}">${icon('star')}</button>
      ${ch(u.id)}${lv ? `<span class="lv">Lv. ${lv.level}</span>` : ''}<div class="nm"><b>${esc(u.name)}</b><small>${tag(u.tier, u.tierName)}×${u.have}${u.away ? ` · 탐험 ${u.away}` : ''}</small></div></div>`;
  }
  function bindCards(root, onPick) {
    root.querySelectorAll('[data-fav]').forEach(b => b.onclick = e => { e.stopPropagation(); const id = b.dataset.fav; favs.has(id) ? favs.delete(id) : favs.add(id); saveFav(); b.classList.toggle('on', favs.has(id)); b.setAttribute('aria-pressed', favs.has(id)); });
    root.querySelectorAll('.card[data-id]').forEach(c => { c.onclick = () => onPick(c.dataset.id); c.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onPick(c.dataset.id); } }; });
  }

  // ── 뽑기(기존 규칙: 등급별 비용·확률·천장·뽑기권 받기 대기시간은 서버가 정함) ──
  async function draw(last) {
    chrome('draw'); main.innerHTML = loading();
    let d; try { d = await api('draw'); } catch (e) { if (e.message === 'login') return loginView(e.info); return failed(e.message, () => draw()); }
    const credits = d.unlimited ? Infinity : d.credits;
    main.innerHTML = `<div class="dr"><section class="panel p-draw">${art('draw-machine.webp', 'hd-art')}
        <p style="margin:0 0 6px;font-weight:800;font-size:18px">🎫 뽑기권 ${d.unlimited ? '무제한(관리자)' : `${d.credits.toLocaleString('ko-KR')}장`}</p>
        <p class="note" style="margin-bottom:12px">${d.wait ? `다음 뽑기권 받기까지 ${waitText(d.wait)}` : `지금 뽑기권 ${d.claim}장을 받을 수 있어요 (${d.cooldownHours}시간마다)`}${d.event ? ` · 🎉 ${esc(d.event.label)}` : ''}</p>
        <button class="btn coral wide" id="claim" ${d.wait ? 'disabled' : ''}>뽑기권 받기</button></section>
      <section class="panel"><div class="ph">${icon('egg')}<h2>뽑기</h2></div><div class="gachas">${d.gachas.map(g => { const lim = Math.min(50, Math.floor(credits / g.cost)); return `<div class="gc">
        <h3>${esc(g.label)} 뽑기</h3>
        <div class="qty"><button data-q="${g.kind}" data-d="-1" aria-label="하나 줄이기" ${lim ? '' : 'disabled'}>−</button><input type="number" inputmode="numeric" min="1" max="${Math.max(1, lim)}" value="1" data-i="${g.kind}" aria-label="횟수" ${lim ? '' : 'disabled'}><button data-q="${g.kind}" data-d="1" aria-label="하나 늘리기" ${lim ? '' : 'disabled'}>+</button></div>
        <button class="btn coral" data-go="${g.kind}" data-cost="${g.cost}" data-lim="${lim}" ${lim ? '' : 'disabled'}>${lim ? `1회 뽑기 · 🎫 ${g.cost}` : '뽑기권 부족'}</button>
        <div class="odds">확률 · ${g.weights.map(w => `${esc(w.tierName)} ${w.w}%`).join(' · ')}${g.kind === 'HIGH' && d.pity ? `<br>🎯 ${esc(d.pity.tierName)} 보장까지 ${d.pity.left}회` : ''}</div></div>`; }).join('')}</div>
        <div id="dres">${last ? drawResult(last) : ''}</div></section></div>`;
    $('#claim').onclick = async () => { $('#claim').disabled = true; try { const r = await api('claim', {}); toast(String(r.result).split('\n')[0]); await refreshMe(); draw(); } catch (e) { toast(e.message); $('#claim').disabled = false; } };
    main.querySelectorAll('[data-i]').forEach(i => {
      const k = i.dataset.i, go = main.querySelector(`[data-go="${k}"]`), lim = +go.dataset.lim, cost = +go.dataset.cost;
      const val = () => Math.max(1, Math.min(lim, Math.floor(+i.value || 1))), paint = () => { if (lim) go.textContent = `${val()}회 뽑기 · 🎫 ${(cost * val()).toLocaleString('ko-KR')}`; };
      i.oninput = paint; i.onblur = () => { i.value = val(); paint(); };
      main.querySelectorAll(`[data-q="${k}"]`).forEach(b => b.onclick = () => { i.value = Math.max(1, Math.min(lim, val() + +b.dataset.d)); paint(); });
      go.onclick = async () => {
        main.querySelectorAll('[data-go]').forEach(x => { x.disabled = true; }); go.textContent = '뽑는 중…';
        try { const r = await api('draw', { kind: k, times: val() }); await refreshMe(); draw(r.result); } catch (e) { toast(e.message.split('\n')[0]); draw(); }
      };
    });
  }
  const waitText = ms => { const m = Math.ceil(ms / 60000), h = Math.floor(m / 60); return h ? `${h}시간 ${m % 60}분` : `${m}분`; };   // 기존 화면과 같은 계산(서버 값은 밀리초)
  const drawResult = r => `<h3 style="margin-top:16px">뽑기 결과</h3><div class="res">${r.items.map((u, i) => `<div class="card" style="animation-delay:${i * 40}ms">${u.first ? '<span class="new">NEW</span>' : ''}${u.n > 1 ? `<span class="cnt">×${u.n}</span>` : ''}${thumb(u.img)}<div class="nm"><b>${esc(u.name)}</b><small>${tag(u.tier, u.tierName)}</small></div></div>`).join('')}</div>`;

  // ── 조합(기존 규칙: 서버의 조합식·재료 수만, 보호·탐험 중은 재료 아님. 직접 섞기는 2~6마리) ──
  let craftData = null, craftSel = null, mixPick = [];
  async function craft(id) {
    chrome('craft'); main.innerHTML = loading();
    try { craftData = await api('craft'); } catch (e) { if (e.message === 'login') return loginView(e.info); return failed(e.message, () => craft(id)); }
    craftSel = craftData.recipes.find(r => r.id === id) ?? craftData.recipes.find(r => r.ready) ?? craftData.recipes[0] ?? null;
    renderCraft();
  }
  function recipeSlots(r) {
    return `<div class="mats">${r.materials.map((m, i) => `${i ? `<span class="op">+</span>` : ''}<div class="slot ${m.usable < m.need ? 'short' : ''}">${thumb(m.img)}${esc(m.name)}<small>${m.usable}/${m.need}${m.locked ? ` · 보호 ${m.locked}` : ''}${m.away ? ` · 탐험 ${m.away}` : ''}</small></div>`).join('')}<span class="op">→</span><div class="slot rslot">${thumb(r.img)}${esc(r.name)}<small>${tag(r.tier, r.tierName)}</small></div></div>`;
  }
  function renderCraft(out = '') {
    const r = craftSel, q = (main.querySelector('#rq')?.value ?? '').trim();
    const list = craftData.recipes.filter(x => !q || x.name.includes(q));
    main.innerHTML = `<div class="cr"><section class="panel p-craft"><div class="ph">${icon('craft')}<h2>조합식</h2></div>
        <label class="search">${icon('search')}<span class="sr">조합식 찾기</span><input id="rq" placeholder="이름으로 찾기" value="${esc(q)}"></label>
        <div class="rlist" style="margin-top:10px">${list.map(x => `<button class="ritem ${x.id === r?.id ? 'on' : ''}" data-r="${esc(x.id)}">${thumb(x.img)}<span><b>${esc(x.name)}</b><small>${esc(x.tierName)}${x.isNew ? ' · 처음' : ''}</small></span><span class="${x.ready ? 'ok' : 'no'}">${x.ready ? `${x.max}개 가능` : '재료 부족'}</span></button>`).join('') || '<p class="note">조합식이 없어요.</p>'}</div></section>
      <section class="panel p-craft">${r ? `<div class="ph"><h2>${esc(r.name)}</h2>${tag(r.tier, r.tierName)}</div>${recipeSlots(r)}
        <p class="note">${r.ready ? `재료가 있어요. 한 번에 최대 ${r.max}개까지 만들 수 있어요.` : '빨간 칸의 재료가 모자라요. 보호·탐험 중인 갈매미는 재료로 쓰지 않아요.'}</p>
        <div class="qty" style="margin:12px 0"><button data-cq="-1" aria-label="하나 줄이기" ${r.ready ? '' : 'disabled'}>−</button><input id="ct" type="number" min="1" max="${Math.max(1, r.max)}" value="1" aria-label="만들 개수" ${r.ready ? '' : 'disabled'}><button data-cq="1" aria-label="하나 늘리기" ${r.ready ? '' : 'disabled'}>+</button></div>
        <button class="btn lav wide" id="doCraft" ${r.ready ? '' : 'disabled'}>${r.ready ? '조합' : '재료가 모자라요'}</button>` : '<p class="note">볼 수 있는 조합식이 없어요.</p>'}
        ${out ? `<div class="out" role="status">${esc(out)}</div>` : ''}
        <div class="sec" style="margin-top:22px"><h3>${icon('craft')}직접 섞기 <span class="note">(숨은 조합 찾기 · 2~6마리 · 맞는 조합식이 없으면 재료는 그대로)</span></h3>
          <div class="pool">${craftData.pool.map(u => `<div class="card ${mixPick.includes(u.id) ? 'on' : ''}" role="button" tabindex="0" data-mix="${esc(u.id)}">${thumb(u.img)}<div class="nm"><b>${esc(u.name)}</b><small>×${u.usable}</small></div></div>`).join('')}</div>
          <div class="tools" style="margin-top:10px"><span class="note">고른 재료 ${mixPick.length}마리</span><span style="flex:1"></span><button class="btn ghost sm" id="mixClear" ${mixPick.length ? '' : 'disabled'}>비우기</button><button class="btn lav sm" id="doMix" ${mixPick.length >= 2 ? '' : 'disabled'}>섞기</button></div></div></section></div>`;
    $('#rq').oninput = () => { const pos = $('#rq').selectionStart; renderCraft(out); const i = $('#rq'); i.focus(); i.setSelectionRange(pos, pos); };
    main.querySelectorAll('[data-r]').forEach(b => b.onclick = () => { craftSel = craftData.recipes.find(x => x.id === b.dataset.r); history.replaceState(null, '', `#craft=${craftSel.id}`); renderCraft(); });
    const ct = $('#ct'), val = () => Math.max(1, Math.min(r?.max || 1, Math.floor(+ct.value || 1)));
    main.querySelectorAll('[data-cq]').forEach(b => b.onclick = () => { ct.value = Math.max(1, Math.min(r.max, val() + +b.dataset.cq)); });
    $('#doCraft') && ($('#doCraft').onclick = async () => {
      const n = val(); if (r.confirm && !confirm(`${r.name} ${n}개를 만들까요? 재료로 쓴 갈매미는 사라져요.`)) return;
      $('#doCraft').disabled = true; $('#doCraft').textContent = '조합하는 중…';
      try { const res = await api('craft', { id: r.id, times: n }); await refreshMe(); craftData = await api('craft'); craftSel = craftData.recipes.find(x => x.id === r.id) ?? craftSel; renderCraft(String(res.result)); }
      catch (e) { renderCraft(e.message); }
    });
    main.querySelectorAll('[data-mix]').forEach(c => c.onclick = () => {
      const id = c.dataset.mix, have = craftData.pool.find(u => u.id === id).usable, cnt = mixPick.filter(x => x === id).length;
      if (mixPick.length < 6 && cnt < have) mixPick.push(id); else mixPick = mixPick.filter((x, i, a) => !(x === id && i === a.lastIndexOf(id)));
      renderCraft(out);
    });
    $('#mixClear').onclick = () => { mixPick = []; renderCraft(); };
    $('#doMix').onclick = async () => {
      if (!confirm(`고른 ${mixPick.length}마리로 섞어 볼까요? 맞는 조합식이 있으면 재료가 사라져요.`)) return;
      $('#doMix').disabled = true;
      try { const res = (await api('mix', { ids: mixPick })).result; mixPick = []; await refreshMe(); craftData = await api('craft'); renderCraft(res.text); }
      catch (e) { renderCraft(e.message); }
    };
  }

  // ── 보관함(검색·등급·정렬·즐겨찾기) + 상세 ──
  const GROUPS = [['all', '전체'], ['top', '최상위'], ['LEGEND', '전설'], ['HIDDEN', '히든'], ['low', '그 밖'], ['fav', '즐겨찾기']];
  const SORTS = [['tier', '등급 높은 순'], ['name', '이름 순'], ['have', '많이 가진 순'], ['level', '레벨 높은 순']];
  let boxQ = '', boxF = 'all', boxS = 'tier';
  function box(showDetail) {
    chrome(showDetail ? 'unit' : 'box');
    const pass = u => (boxF === 'all' || (boxF === 'top' ? u.top : boxF === 'low' ? !u.top && !['LEGEND', 'HIDDEN'].includes(u.tier) : boxF === 'fav' ? favs.has(u.id) : u.tier === boxF)) && (!boxQ || u.name.includes(boxQ));
    const cmp = { tier: (a, b) => TIER_RANK.indexOf(b.tier) - TIER_RANK.indexOf(a.tier), name: (a, b) => a.name.localeCompare(b.name, 'ko'), have: (a, b) => b.have - a.have, level: (a, b) => (lvOf(b.id)?.level ?? 0) - (lvOf(a.id)?.level ?? 0) }[boxS];
    const list = me.units.filter(pass).sort((a, b) => favFirst(a, b) || cmp(a, b));
    if (!sel || !unitOf(sel)) sel = list[0]?.id ?? me.units[0]?.id ?? null;
    main.innerHTML = `<div class="box ${showDetail ? 'show' : ''}"><section class="panel p-box list"><div class="ph">${icon('box')}<h2>보관함</h2><span class="sp"></span><span class="note">${list.length}종</span></div>
        <div class="tools"><label class="search">${icon('search')}<span class="sr">이름 검색</span><input id="bq" placeholder="이름으로 찾기" value="${esc(boxQ)}"></label>
          <label class="sr" for="bs">정렬</label><select class="sel" id="bs">${SORTS.map(([k, t]) => `<option value="${k}" ${boxS === k ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
        <div class="tools" role="group" aria-label="등급">${GROUPS.map(([k, t]) => `<button class="chip ${boxF === k ? 'on' : ''}" data-f="${k}" aria-pressed="${boxF === k}">${t}</button>`).join('')}</div>
        ${boxF === 'fav' ? '<p class="note" style="margin-bottom:10px">즐겨찾기는 이 기기에만 저장돼요.</p>' : ''}
        <div class="cards">${list.map(u => unitCard(u, u.id === sel)).join('') || '<p class="note">조건에 맞는 갈매미가 없어요.</p>'}</div></section>
      <aside class="panel p-unit detail" id="detail" aria-live="polite">${sel ? '' : '<p class="note">갈매미를 골라 주세요.</p>'}</aside></div>`;
    $('#bq').oninput = () => { boxQ = $('#bq').value.trim(); const pos = $('#bq').selectionStart; box(); const i = $('#bq'); i.focus(); i.setSelectionRange(pos, pos); };
    $('#bs').onchange = () => { boxS = $('#bs').value; box(); };
    main.querySelectorAll('[data-f]').forEach(b => b.onclick = () => { boxF = b.dataset.f; box(); });
    bindCards(main.querySelector('.list'), id => {
      sel = id;
      if (matchMedia('(max-width:900px)').matches) { location.hash = `unit=${id}`; return; }   // 모바일: 상세 화면으로 이동
      history.replaceState(null, '', `#unit=${id}`); main.querySelectorAll('.list .card').forEach(c => c.classList.toggle('sel', c.dataset.id === id)); detail(id);
    });
    if (sel) detail(sel);
  }
  async function detail(id) {
    const el = $('#detail'); if (!el) return;
    const u = unitOf(id); if (!u) { el.innerHTML = '<p class="note">이 갈매미를 가지고 있지 않아요.</p>'; return; }
    const lv = lvOf(id), inTeam = me.battleTeam.includes(id);
    el.innerHTML = `<button class="btn ghost sm back" id="dBack">${icon('back')}보관함</button>
      <div class="dt-h"><div><h2>${esc(u.name)}</h2><div style="margin-top:4px">${tag(u.tier, u.tierName)} <span class="note">×${u.have}${u.away ? ` · 탐험 중 ${u.away}` : ''}</span></div></div><span class="sp" style="flex:1"></span>
        <button class="fav ${favs.has(id) ? 'on' : ''}" data-fav="${esc(id)}" style="position:static" aria-label="즐겨찾기" aria-pressed="${favs.has(id)}">${icon('star')}</button></div>
      ${ch(id, 'dt-big')}
      ${u.quote ? `<p class="quote">“${esc(u.quote)}”</p>` : ''}
      <div class="stat3" style="margin-top:12px"><div><small>레벨</small><b>${lv ? `Lv. ${lv.level}` : '–'}</b>${lv ? `<div class="bar"><i style="width:${xpPct(lv)}%"></i></div>` : ''}</div>
        <div><small>승진</small><b>${lv ? `${lv.stage ?? 0}단계` : '–'}</b></div><div><small>직무 표창</small><b>${lv ? (lv.evoAugs ?? []).length : '–'}</b></div></div>
      ${!u.top ? '<p class="note" style="margin-top:10px">레벨·기술·승진은 최상위 등급 갈매미만 있어요(수비대 대결에 나가는 갈매미).</p>' : !lv ? '<p class="note" style="margin-top:10px">아직 스토리 대결에 나간 적이 없어요. 대결에 나가면 레벨이 생겨요.</p>' : ''}
      <div id="dRules">${u.top ? loading('특성 불러오는 중…') : ''}</div>
      ${u.intro ? `<div class="sec"><h3>소개</h3><p class="note">${esc(u.intro)}</p></div>` : ''}
      <div class="sec"><h3>${icon('gear')}관리</h3>
        ${u.top ? `<button class="btn ${inTeam ? 'ghost' : ''} wide" id="dTeam">${inTeam ? `전투 편성에서 빼기 (${me.battleTeam.indexOf(id) + 1}번)` : me.battleTeam.length >= 3 ? '전투 편성이 가득 찼어요(3마리)' : '전투 편성에 넣기'}</button>` : ''}
        <button class="btn ghost wide" id="dGame" style="margin-top:8px">${icon('game')}기술 배우기·승진은 게임에서</button>
        <p class="note" style="margin-top:6px">레벨 올리기·기술 배우기·승진은 「수비대 대결」 게임 안에서만 할 수 있어요.</p></div>`;
    bindCards(el, () => {});
    el.querySelector('#dBack').onclick = () => { location.hash = 'box'; };
    el.querySelector('#dGame').onclick = toGame;
    const t = el.querySelector('#dTeam');
    if (t) { if (!inTeam && me.battleTeam.length >= 3) t.disabled = true;
      t.onclick = async () => { t.disabled = true; const ids = inTeam ? me.battleTeam.filter(x => x !== id) : [...me.battleTeam, id];
        try { const r = await fetch('/my/api/battle-team', { method: 'POST', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: JSON.stringify({ ids }) }); const j = await r.json(); if (!r.ok) throw new Error(j.error || '저장하지 못했어요'); me.battleTeam = j.battleTeam; toast(inTeam ? '전투 편성에서 뺐어요' : '전투 편성에 넣었어요'); detail(id); }
        catch (e) { toast(e.message); t.disabled = false; } }; }
    if (!u.top) return;
    try {
      const R = await loadRules(), d = R.units[id], box2 = $('#dRules'); if (!box2 || $('#detail') !== el) return;
      if (!d) { box2.innerHTML = '<p class="note">이 갈매미는 아직 대결 자료가 없어요.</p>'; return; }
      const learned = (lv?.learned ?? []).map(k => R.learn.skills[k]?.name ?? k), aug = (lv?.evoAugs ?? []).map(k => R.evoAugments[k]);
      box2.innerHTML = `<div class="sec"><h3>특성</h3><dl class="kv"><dt>속성</dt><dd>${esc(R.attributes[d.attr]?.name ?? d.attr)}${d.sub ? ` · 보조 ${esc(R.attributes[d.sub]?.name ?? d.sub)}` : ''}</dd>
          <dt>역할</dt><dd>${esc(ROLE[d.role] ?? d.role)}</dd><dt>고유 기술</dt><dd>${esc(d.skill?.name ?? '')}</dd>${u.synergy ? `<dt>시너지</dt><dd>${esc(R.synergies[u.synergy]?.name ?? u.synergy)}</dd>` : ''}</dl></div>
        <div class="sec"><h3>배운 기술</h3>${learned.length ? learned.map(n => `<span class="pill">${esc(n)}</span>`).join('') : '<span class="pill emp">아직 없어요</span>'}</div>
        <div class="sec"><h3>직무 표창 <span class="note">(승진할 때 받는 영구 증강)</span></h3>${aug.length ? aug.map(a => `<span class="pill" title="${esc(a?.desc ?? '')}">${esc(a?.name ?? '?')}</span>`).join('') : '<span class="pill emp">아직 없어요</span>'}
          <p class="note" style="margin-top:4px">전투 증강체는 대결할 때마다 새로 골라서 갈매미가 가지고 있지 않아요.</p></div>`;
    } catch (e) { const b2 = $('#dRules'); if (b2) b2.innerHTML = `<p class="note err">특성을 불러오지 못했어요: ${esc(e.message)}</p>`; }
  }

  // ── 시작·이동 ──
  async function refreshMe() { me = await api('me'); }
  function route() {
    if (!me) return;
    document.querySelector('.menu')?.remove();
    const h = decodeURIComponent(location.hash.slice(1)), [p, arg] = h.split('=');
    document.body.classList.toggle('on-home', !['draw', 'craft', 'box', 'units', 'unit'].includes(p));   // 앱 받기 칸은 홈에만
    scrollTo(0, 0);
    if (p === 'draw') return draw();
    if (p === 'craft') return craft(arg);
    if (p === 'box' || p === 'units') return box(false);
    if (p === 'unit') { if (arg) sel = arg; return box(true); }
    return home();
  }
  addEventListener('hashchange', route);
  async function boot() {
    if (!main.dataset.landing) main.innerHTML = loading();
    try { me = await api('me'); } catch (e) { if (e.message === 'login') return landingView(e.info); setIn(false); return failed(e.message, boot); }
    setIn(true); delete main.dataset.landing;
    try { story = await api('story'); } catch { story = null; }   // 레벨 정보가 없어도 나머지는 보여 줌
    loadRules().catch(() => {});
    const q = new URLSearchParams(location.search).get('kakao'); if (q) { toast(q === 'linked' ? '카카오 계정을 연결했어요' : '로그인했어요'); history.replaceState(null, '', '/my' + location.hash); }
    route();
  }
  const foot = $('#foot'); if (foot && !foot.children.length) fetch('/web/foot').then(r => r.ok ? r.text() : '').then(t => { foot.innerHTML = t; }).catch(() => {});
  boot();
})();
