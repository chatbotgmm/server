// 갈매미맨 공개 홈페이지: 작은 화면 메뉴, 로그인 상태 표시(내 계정), 게임 시작·로그인 진입과 복귀 위치, 캐릭터 소개 페이지 넘김
// 로그인 상태만으로 게임으로 보내지 않는다. 로그인 뒤 갈 곳·돌아갈 곳은 서버가 거른 값(data-next, data-from)만 쓴다.
(() => {
  'use strict';
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const store = {
    get(k) { try { return JSON.parse(sessionStorage.getItem(k) || 'null'); } catch { return null; } },
    set(k, v) { try { sessionStorage.setItem(k, JSON.stringify(v)); } catch { /* 저장 안 되는 브라우저: 복귀 위치만 잃음 */ } },
    del(k) { try { sessionStorage.removeItem(k); } catch { /* 무시 */ } },
  };
  const here = () => location.pathname + location.search;

  // ── 원래 공개 페이지로 돌아온 경우 스크롤 위치 복구 ──
  const back = store.get('gmm-restore');
  if (back && back.path === here() && Date.now() - back.at < 30 * 60e3) { store.del('gmm-restore'); addEventListener('load', () => scrollTo(0, back.y || 0)); }

  // ── 게임 시작·로그인: 누른 페이지와 위치를 기억(로그인 취소 때 복귀) ──
  const remember = () => store.set('gmm-from', { path: here(), y: Math.round(scrollY), at: Date.now() });
  $$('[data-start]').forEach(a => a.addEventListener('click', remember));
  $$('[data-auth]').forEach(a => {
    a.addEventListener('click', remember);
    if (!$('[data-login]')) a.href = `/login?next=/account&from=${encodeURIComponent(location.pathname)}`;
  });

  // ── 로그인돼 있으면 「로그인」만 「내 계정」으로(게임 시작 버튼은 그대로) ──
  if ($('[data-auth]')) fetch('/my/api/who', { headers: { 'x-gmm': '1' }, credentials: 'same-origin' })
    .then(r => (r.ok ? r.json() : null)).then(who => {
      if (!who) return;
      $$('[data-auth]').forEach(a => { a.textContent = '내 계정'; a.href = '/account'; });
    }).catch(() => { /* 확인 못 하면 「로그인」 그대로 */ });

  // ── 작은 화면 메뉴: 열고 닫기, Esc, 포커스 복귀, 닫으면 원래 위치 유지 ──
  const btn = $('.menu-btn'), drawer = $('#drawer');
  if (btn && drawer) {
    const close = () => { drawer.hidden = true; btn.setAttribute('aria-expanded', 'false'); document.body.style.overflow = ''; btn.focus(); };
    btn.addEventListener('click', () => { drawer.hidden = false; btn.setAttribute('aria-expanded', 'true'); document.body.style.overflow = 'hidden'; $('.close', drawer).focus(); });
    $('.close', drawer).addEventListener('click', close);
    drawer.addEventListener('click', e => { if (e.target === drawer) close(); });
    drawer.addEventListener('keydown', e => {
      if (e.key === 'Escape') close();
      if (e.key !== 'Tab') return;
      const f = $$('a,button', drawer), first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    });
    matchMedia('(min-width:961px)').addEventListener('change', m => { if (m.matches && !drawer.hidden) close(); });
  }

  // ── 로그인 진입 ──
  const box = $('[data-login]');
  if (box) {
    const next = box.dataset.next, from = box.dataset.from;
    const saved = store.get('gmm-from');
    const cancel = () => { store.set('gmm-restore', { path: from, y: saved && saved.path === from ? saved.y : 0, at: Date.now() }); store.del('gmm-from'); store.del('gmm-login-return'); };
    $('[data-cancel]', box).addEventListener('click', cancel);
    $$('[data-back]').forEach(a => { a.href = from; a.addEventListener('click', cancel); });
    // 카카오: 서버가 /my로 돌려보내므로 갈 곳을 기억해 두고 /my 화면이 이어서 보낸다(10분)
    const k = $('[data-kakao-start]', box);
    if (k) k.addEventListener('click', () => store.set('gmm-login-return', { next, from, at: Date.now() }));
    const err = $('#lerr', box), state = box.dataset.kstate;
    if (state) err.textContent = { fail: '카카오 로그인에 실패했어요. 잠시 뒤 다시 해 주세요.', taken: '이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.', link: '' }[state] || '';
    const form = $('[data-code]', box), input = $('#code', box), go = $('button[type=submit]', form);
    let busy = false;
    form.addEventListener('submit', async e => {
      e.preventDefault();
      if (busy) return;   // 로그인 중 중복 제출 차단
      const code = input.value.replace(/\D/g, '');
      if (code.length !== 6) { err.textContent = '6자리 숫자를 입력해 주세요.'; input.focus(); return; }
      busy = true; go.disabled = true; go.setAttribute('aria-busy', 'true'); go.textContent = '확인 중…'; err.textContent = '';
      try {
        const r = await fetch('/my/api/login', { method: 'POST', credentials: 'same-origin', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: JSON.stringify({ code }) });
        const j = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(j.error || `로그인하지 못했어요(${r.status}).`);
        store.del('gmm-login-return');
        location.replace(next);
      } catch (x) {
        err.textContent = x instanceof TypeError ? '연결이 불안정해요. 연결을 확인하고 다시 눌러 주세요.' : x.message;
        busy = false; go.disabled = false; go.removeAttribute('aria-busy'); go.textContent = '로그인'; input.focus();
      }
    });
  }

  // ── 캐릭터 소개: 페이지 그리드, 카드를 고르면 같은 화면 상세만 바뀜 ──
  const chars = $('[data-chars]'), data = $('#chars-data');
  if (chars && data) {
    const list = JSON.parse(data.textContent), grid = $('.char-grid', chars), detail = $('.char-detail', chars), label = $('[data-page]', chars);
    const per = () => (matchMedia('(max-width:640px)').matches ? 6 : matchMedia('(max-width:960px)').matches ? 8 : 12);
    let page = 0, sel = 0;
    const show = i => {
      const c = list[i]; sel = i;
      detail.innerHTML = `<img src="/images/${encodeURIComponent(c.id)}.png" alt="${esc(c.n)} 카드 그림" width="300" height="300" loading="lazy"><h2>${esc(c.n)}</h2><span class="tier">${esc(c.t)}</span>${c.q ? `<q>${esc(c.q)}</q>` : ''}${c.g ? `<p>${esc(c.g)}</p>` : ''}<p><a href="/dex/${encodeURIComponent(c.id)}">도감에서 자세히 ›</a></p>`;
      $$('.ccard', grid).forEach(b => b.setAttribute('aria-selected', String(Number(b.dataset.i) === i)));
    };
    const render = () => {
      const n = per(), pages = Math.max(1, Math.ceil(list.length / n));
      page = Math.min(page, pages - 1);
      grid.innerHTML = list.slice(page * n, page * n + n).map((c, k) => { const i = page * n + k; return `<button type="button" class="ccard reg-card" data-card="registration" role="option" data-i="${i}" aria-selected="${i === sel}"><img src="/thumbs/${encodeURIComponent(c.id)}.jpg" alt="" width="160" height="160" loading="lazy"><span>${esc(c.n)}</span></button>`; }).join('');
      label.textContent = `${page + 1} / ${pages}`;
      $('[data-prev]', chars).disabled = page === 0; $('[data-next]', chars).disabled = page >= pages - 1;
    };
    grid.addEventListener('click', e => { const b = e.target.closest('.ccard'); if (b) show(Number(b.dataset.i)); });
    $('[data-prev]', chars).addEventListener('click', () => { page--; render(); });
    $('[data-next]', chars).addEventListener('click', () => { page++; render(); });
    addEventListener('resize', () => render());
    render(); if (list.length) show(0);
  }
})();
