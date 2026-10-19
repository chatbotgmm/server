// 갈매미맨 웹 게임(/game). 기존 서버 API(/my/api/*, /my/api/native/*, /my/api/pvp/*)만 쓰고 새 규칙·재화·보상을 만들지 않는다.
// 화면: 항구 홈 · 방범대 도감 · 전투 편성 · 플레이(모드) · 매칭 · 증강체 · 전투. 아직 웹에 없는 메뉴는 기존 웹 화면으로 연결한다.
// 원칙: 서버 응답 전에는 성공·보상을 보여 주지 않는다. 재화 숫자·뒤로 버튼을 「불러오는 중」으로 덮지 않는다(불러오기 표시는 따로).
(() => {
  'use strict';
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmt = n => (typeof n === 'number' ? n.toLocaleString('ko-KR') : '—');
  const view = $('#view'), app = $('#app');
  const S = { who: null, state: null, key: '', catalog: null, loading: new Set() };
  window.GMM = S;   // 검수 도구용(읽기만)

  // ── 서버 호출: 401이면 로그인 진입으로(돌아갈 곳은 공개 페이지 기억값) ──
  class ApiError extends Error { constructor(m, status) { super(m); this.status = status; } }
  async function api(path, body, opt = {}) {
    let r;
    try {
      r = await fetch(`/my/api/${path}`, { method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin', signal: opt.signal,
        headers: body === undefined ? { 'x-gmm': '1' } : { 'x-gmm': '1', 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (e) { if (e.name === 'AbortError') throw e; throw new ApiError('연결이 불안정해요. 연결을 확인해 주세요.', 0); }
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { goLogin(); throw new ApiError('로그인이 필요해요.', 401); }
    if (!r.ok) throw new ApiError(j.error || `오류(${r.status})`, r.status);
    return j;
  }
  function goLogin() {
    let from = '/';
    try { const f = JSON.parse(sessionStorage.getItem('gmm-from') || 'null'); if (f && /^\/(?!\/)/.test(f.path)) from = f.path.split('?')[0]; } catch { /* 기본값 */ }
    const next = location.hash === '#account' ? '/account' : '/game';
    location.replace(`/login?next=${next}&from=${encodeURIComponent(from)}`);
  }
  const toast = (t, kind = '') => { const e = $('#toast'); e.textContent = t; e.className = `toast ${kind}`; e.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => { e.hidden = true; }, 2800); };
  const sprite = url => (url && S.key ? `${url}${url.includes('?') ? '&' : '?'}k=${encodeURIComponent(S.key)}` : '');
  const thumb = id => `/thumbs/${encodeURIComponent(id)}.jpg`;
  const STAGE = ['원형', '1차 승진', '2차 승진', '3차 승진'];
  const ROLE = { ATTACK: '공격', DEFENSE: '수비', UTILITY: '지원' };

  // ── 대원 그림: 투명 여백을 잘라(알파 경계) 발을 지면 기준점에 맞춘다. 바닥 그림자는 따로 그린다 ──
  const bounds = new Map();
  function alphaBox(img) {
    if (bounds.has(img.src)) return bounds.get(img.src);
    const w = 200, h = Math.round(img.naturalHeight * w / img.naturalWidth), c = document.createElement('canvas'); c.width = w; c.height = h;
    const g = c.getContext('2d', { willReadFrequently: true }); g.drawImage(img, 0, 0, w, h);
    let d; try { d = g.getImageData(0, 0, w, h).data; } catch { return null; }
    let x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (d[(y * w + x) * 4 + 3] > 24) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    const s = img.naturalWidth / w, box = x1 < 0 ? null : { x: x0 * s, y: y0 * s, w: (x1 - x0 + 1) * s, h: (y1 - y0 + 1) * s };
    bounds.set(img.src, box); return box;
  }
  // 그림을 box 안에 「발 아래 중앙」 기준으로 그린다(비율 유지). flip: 뒷모습 좌우 반전 없음(원본 그대로)
  function standee(host, url, alt = '') {
    host.innerHTML = '<span class="shadow" aria-hidden="true"></span><canvas class="actor" role="img"></canvas>';
    const cv = $('canvas', host); cv.setAttribute('aria-label', alt);
    if (!url) { host.classList.add('empty'); return; }
    const img = new Image(); img.decoding = 'async';
    img.onload = () => {
      const b = alphaBox(img) || { x: 0, y: 0, w: img.naturalWidth, h: img.naturalHeight };
      const draw = () => {
        const W = host.clientWidth, H = host.clientHeight; if (!W || !H) return;
        const sc = Math.min(W / b.w, H / b.h), dw = Math.round(b.w * sc), dh = Math.round(b.h * sc), dpr = Math.min(2, devicePixelRatio || 1);
        cv.width = Math.round(dw * dpr); cv.height = Math.round(dh * dpr); cv.style.width = `${dw}px`; cv.style.height = `${dh}px`;
        const g = cv.getContext('2d'); g.imageSmoothingQuality = 'high'; g.drawImage(img, b.x, b.y, b.w, b.h, 0, 0, cv.width, cv.height);
        host.style.setProperty('--foot-w', `${Math.round(dw * 0.62)}px`);
      };
      draw(); host._redraw = draw;
    };
    img.onerror = () => host.classList.add('empty');
    img.src = url;
  }
  addEventListener('resize', () => $$('[data-standee]').forEach(h => h._redraw && h._redraw()));

  // ── 머리줄: 프로필(계정 진입), 재화 3종, 설정. 불러오는 중에도 마지막 숫자를 유지 ──
  const MONEY = [['snack', '새우gang', 'shrimp-snack', ''], ['peanut', '땅콩', 'peanut', ''], ['credits', '지원서', 'draw', '장']];
  function header() {
    const me = S.state?.me, lead = S.state && (S.state.units.find(u => u.key === S.state.team[0]) || S.state.units[0]);
    $('#nick').textContent = me?.nickname || S.who?.nickname || '이름 없는 대장';
    $('#face').innerHTML = lead ? `<img src="${thumb(lead.id)}" alt="" width="44" height="44">` : '';
    const vals = { snack: me?.snack, peanut: me?.peanut, credits: me?.credits ?? S.who?.credits };
    $('#money').innerHTML = MONEY.map(([k, label, icon, unit]) => `<a class="pill" href="${k === 'credits' ? '/my#draw' : '/my/classic#shop'}" title="${label}"><img src="/site/a/${icon}.webp" alt="" width="26" height="26"><span class="sr">${label}</span><b>${fmt(vals[k])}${vals[k] != null && unit ? `<small>${unit}</small>` : ''}</b></a>`).join('')
      + `<span class="sync" ${S.loading.size ? '' : 'hidden'} role="status" aria-label="정보 새로 받는 중"></span>`;
  }
  const busy = (k, on) => { on ? S.loading.add(k) : S.loading.delete(k); app.setAttribute('aria-busy', String(!!S.loading.size)); const s = $('.sync'); if (s) s.hidden = !S.loading.size; };

  // ── 하단 6메뉴: 기존 목적지 유지. 웹 게임에 아직 없는 화면은 기존 웹 화면으로 ──
  const DOCK = [['home', '항구', 'harbor', '#home'], ['explore', '탐험', 'explore', '/my/classic#party'], ['draw', '뽑기', 'draw', '/my#draw'], ['team', '방범대', 'team', '#dex'], ['combine', '조합', 'combine', '/my#craft'], ['shop', '상점', 'shop', '/my/classic#shop']];
  function dock(active) {
    $('#dock').innerHTML = DOCK.map(([k, label, icon, href]) => `<a href="${href}" class="${active === k ? 'on' : ''}" ${active === k ? 'aria-current="page"' : ''}${href.startsWith('#') ? '' : ' data-out'}><img src="/site/a/${icon}.webp" alt="" width="34" height="34"><span>${label}</span></a>`).join('');
  }

  // ── 시트(프로필·설정·출석): 같은 화면 위, 포커스 가두기와 복귀 ──
  let sheetBack = null;
  function sheet(title, html, after) {
    const w = $('#sheet'); sheetBack = document.activeElement;
    $('#sheet-title').textContent = title; $('.sheet-body', w).innerHTML = html; w.hidden = false;
    $('.close', w).focus(); if (after) after($('.sheet-body', w));
  }
  function closeSheet() { $('#sheet').hidden = true; if (sheetBack && sheetBack.focus) sheetBack.focus(); if (location.hash === '#account') history.replaceState(null, '', '#home'); }
  $('#sheet').addEventListener('click', e => { if (e.target.id === 'sheet' || e.target.closest('.close')) closeSheet(); });
  $('#sheet').addEventListener('keydown', e => {
    if (e.key === 'Escape') closeSheet();
    if (e.key !== 'Tab') return;
    const f = $$('a[href],button:not([disabled]),input', $('#sheet')), a = f[0], z = f[f.length - 1];
    if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); } else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
  });
  function account() {
    const me = S.state?.me;
    sheet('프로필 · 계정', `<div class="acct"><p class="big">${esc(me?.nickname || S.who?.nickname || '이름 없는 대장')}</p>
      ${me ? `<p class="muted">도감 ${fmt(me.dex)} / ${fmt(me.dexTotal)} · 카카오 ${me.kakao ? '연결됨' : '연결 안 됨'}</p>` : ''}
      <a class="btn ghost wide" href="/my/classic#account">계정 관리(카카오 연결·탈퇴)</a>
      <a class="btn ghost wide" href="/">공개 홈페이지</a>
      <button class="btn ghost wide" type="button" data-logout>로그아웃</button><p class="err" role="alert"></p></div>`, body => {
      $('[data-logout]', body).onclick = async e => {
        const b = e.currentTarget; b.disabled = true; b.textContent = '로그아웃 중…';
        try { await api('logout', {}); location.replace('/'); } catch (x) { $('.err', body).textContent = x.message; b.disabled = false; b.textContent = '로그아웃'; }
      };
    });
  }
  function settings() {
    let calm = false; try { calm = localStorage.getItem('gmm-calm') === '1'; } catch { /* 기본값 */ }
    sheet('설정', `<div class="acct"><label class="switch"><input type="checkbox" data-calm ${calm ? 'checked' : ''}><span>움직임 줄이기</span></label>
      <p class="muted small">이 기기에만 저장돼요.</p></div>`, body => {
      $('[data-calm]', body).onchange = e => { try { localStorage.setItem('gmm-calm', e.target.checked ? '1' : '0'); } catch { /* 저장 불가 */ } document.documentElement.classList.toggle('calm', e.target.checked); };
    });
  }
  try { if (localStorage.getItem('gmm-calm') === '1') document.documentElement.classList.add('calm'); } catch { /* 기본값 */ }
  $('#profile').onclick = account;
  $('#settings').onclick = settings;

  // ── 출석·미션: 서버의 오늘 미션(읽기)과 출석. 결과는 서버 응답 뒤에만 ──
  async function daily() {
    sheet('출석 · 미션', '<div class="state" role="status"><span class="spin"></span>오늘 미션을 받는 중…</div>');
    const body = $('#sheet .sheet-body');
    try {
      const d = await api('daily');
      const ms = d.missions || [];
      body.innerHTML = `<ul class="missions">${ms.map(m => `<li class="${m.done ? 'done' : ''}"><b>${esc(m.label)}</b><span>${fmt(m.value)} / ${fmt(m.goal)}</span><em>${m.done ? '완료' : `지원서 ${fmt(m.tickets)}장`}</em></li>`).join('')}</ul>
        <p class="muted small">미션 보상은 서버 기준으로 지급돼요.</p>`;
    } catch (x) { if (x.status !== 401) body.innerHTML = `<p class="err" role="alert">${esc(x.message)}</p><button class="btn ghost" type="button" data-retry>다시 시도</button>`; const r = $('[data-retry]', body); if (r) r.onclick = daily; }
  }

  // ── 항구 홈 ──
  function home() {
    title('', 'home');
    const st = S.state, lead = st && (st.units.find(u => u.key === st.team[0]) || st.units[0]);
    view.innerHTML = `<section class="home">
      <div class="scene" aria-label="항구 광장">
        <p class="place">항구 광장</p>
        <div class="stand" data-standee></div>
        <aside class="growth" aria-label="대표 대원 성장">
          ${lead ? `<b>${esc(lead.name)}</b><span class="lv">Lv ${fmt(lead.level)} · ${STAGE[lead.stage] ?? `${lead.stage}단계`}</span>
          <span class="xp" role="img" aria-label="경험치 ${fmt(lead.xp)} / ${fmt(lead.xpNext)}"><i style="width:${lead.xpNext ? Math.min(100, Math.round(lead.xp / lead.xpNext * 100)) : 0}%"></i></span>
          <a class="btn ghost sm" href="#team?u=${encodeURIComponent(lead.key)}">성장 보기</a>` : st ? '<span class="muted">대표 대원이 없어요</span>' : '<span class="muted">대원 정보를 받는 중…</span>'}
        </aside>
      </div>
      <div class="acts">
        ${st?.activeBattle ? '<a class="btn primary xl" href="#battle">진행 중 전투로</a>' : '<a class="btn primary xl" href="#play">플레이</a>'}
        <a class="btn ghost lg" href="#team">전투 편성</a>
        <button class="btn ghost lg" type="button" data-daily>출석 · 미션</button>
      </div></section>`;
    $('[data-daily]', view).onclick = daily;
    if (lead) standee($('.stand', view), sprite(lead.image), `${lead.name} 대표 대원`);
  }

  // ── 아직 이 단계에서 만들지 않은 화면 ──
  function soon(name) {
    title(name, '');
    view.innerHTML = `<section class="soon"><h1>${esc(name)}</h1><p class="muted">웹 게임에서 이 화면을 준비하고 있어요.</p><a class="btn ghost" href="#home">항구로</a></section>`;
  }

  function title(t, active) {
    $('#back').hidden = !t; $('#profile').hidden = !!t; $('#title').textContent = t;
    dock(active);
  }

  // ── 화면 이동(해시) ──
  const ROUTES = { home, account: () => { home(); account(); }, play: () => soon('플레이'), team: () => soon('전투 편성'), dex: () => soon('방범대 도감'), match: () => soon('매칭'), augment: () => soon('증강체'), battle: () => soon('전투') };
  const route = () => { const [name] = location.hash.slice(1).split('?'); return ROUTES[name] ? name : 'home'; };
  const params = () => new URLSearchParams(location.hash.split('?')[1] || '');
  function render() { ROUTES[route()](params()); }
  S.render = render; S.api = api; S.sprite = sprite; S.standee = standee; S.sheet = sheet; S.title = title; S.toast = toast; S.busy = busy; S.header = header; S.STAGE = STAGE; S.ROLE = ROLE; S.esc = esc; S.fmt = fmt; S.thumb = thumb;
  addEventListener('hashchange', () => { if (!$('#sheet').hidden) $('#sheet').hidden = true; render(); view.focus({ preventScroll: true }); });

  async function loadState() {
    busy('state', true); header();
    try { S.state = await api('native/state'); } finally { busy('state', false); header(); }
  }
  async function boot() {
    header(); render();
    try { S.who = await api('who'); } catch (x) { if (x.status !== 401) view.innerHTML = `<section class="soon"><p class="err" role="alert">${esc(x.message)}</p><button class="btn ghost" type="button" onclick="location.reload()">다시 시도</button></section>`; return; }
    header();
    try { const [k] = await Promise.all([api('duel-key').then(r => r.k).catch(() => ''), loadState()]); S.key = k; }
    catch (x) { if (x.status !== 401) toast(x.message, 'bad'); }
    header(); render();
  }
  S.loadState = loadState;
  boot();
})();
