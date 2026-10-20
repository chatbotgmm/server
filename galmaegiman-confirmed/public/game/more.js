// 웹 게임: 뽑기(#draw, 하급·중급·고급 모집) · 조합(#craft) · 탐험(#explore) · 상점(#shop) · 계정(#account) · 출석·미션
// 모두 기존 /my/api/* 를 그대로 쓴다. 비용·확률·천장·보상은 서버 값만 보여 주고 바꾸지 않는다. 결과는 서버 응답 뒤에만 보인다.
// 서버 안내문은 카톡과 같은 문장이라 「뽑기권」이라고 적혀 있다. 웹 화면에서는 새 용어 「지원서」로만 바꿔 보여 준다(숫자·내용은 그대로).
(() => {
  'use strict';
  const S = window.GMM, { esc, fmt } = S;
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const view = $('#view'), app = $('#app');
  const term = t => String(t ?? '').replace(/(?:🎫|🎟️?)\s?(?=뽑기권|지원서)/g, '').replace(/뽑기권\s?받기/g, '지원서 받기').replace(/땅콩뽑기권/g, '땅콩 지원서').replace(/뽑기권/g, '지원서').replace(/(?:🎫|🎟️?)\s?/g, '지원서 ');
  const lines = t => term(t).split('\n').filter(Boolean).map(l => `<p>${esc(l)}</p>`).join('');
  const hm = ms => { const m = Math.ceil(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`; };
  const inner = (title, place = '') => { S.title(title, ''); app.dataset.place = place; app.dataset.screen = 'inner'; };
  const pagerHtml = '<div class="pager"><button class="btn ghost icon" type="button" data-prev aria-label="이전 페이지">‹</button><span data-pageinfo aria-live="polite"></span><button class="btn ghost icon" type="button" data-next aria-label="다음 페이지">›</button></div>';
  // 수량: − 칸 + (최소 1, 최대 max). 실제 가능 여부는 서버가 다시 확인
  const qty = (max, val = 1) => `<div class="qty" role="group" aria-label="수량"><button class="btn ghost icon" type="button" data-q="-1" aria-label="하나 줄이기">−</button><input type="number" inputmode="numeric" min="1" max="${max}" value="${Math.min(val, max)}" aria-label="수량" data-qv><button class="btn ghost icon" type="button" data-q="1" aria-label="하나 늘리기">+</button></div>`;
  const qtyBind = (root, max, onChange) => {
    const inp = $('[data-qv]', root); if (!inp) return () => 1;
    const get = () => Math.max(1, Math.min(max, Math.floor(Number(inp.value) || 1)));
    $$('[data-q]', root).forEach(b => { b.onclick = () => { inp.value = String(Math.max(1, Math.min(max, get() + Number(b.dataset.q)))); onChange && onChange(get()); }; });
    inp.onchange = () => { inp.value = String(get()); onChange && onChange(get()); };
    return get;
  };
  // 서버 실행 버튼: 누르는 동안 잠금·문구 바꾸기, 끝나면 원래대로
  async function run(btn, busyText, fn) {
    if (btn.disabled) return;
    const label = btn.innerHTML; btn.disabled = true; btn.setAttribute('aria-busy', 'true'); btn.textContent = busyText;
    try { return await fn(); } finally { btn.disabled = false; btn.removeAttribute('aria-busy'); btn.innerHTML = label; }
  }
  const refreshMe = () => S.loadState().catch(() => {});

  // 지원서 봉투 애니메이션: art/gpt/registration-v1/component/application-animation.json의 정수 소스 사각형·시간 그대로(아틀라스 1774×887, 4×2)
  // 끝 장면은 「서버 결과가 올 때까지 유지」. 결과 대원·글자는 따로 그린다. 움직임 줄이기면 끝 장면만 보여 준다
  const ENV = [[0, 0, 443, 443, 130], [443, 0, 444, 443, 120], [887, 0, 443, 443, 110], [1330, 0, 444, 443, 110], [0, 443, 443, 444, 120], [443, 443, 444, 444, 130], [887, 443, 443, 444, 140], [1330, 443, 444, 444, 140]];
  function envelope(host) {
    host.innerHTML = '<div class="envelope-wrap"><div class="envelope" role="img" aria-label="지원서 봉투를 여는 중"></div><p class="m-time" role="status">지원서 읽는 중…</p></div>';
    const el = $('.envelope', host), W = 240;
    const show = ([x, y, w, h]) => { const k = W / w; el.style.width = `${W}px`; el.style.height = `${Math.round(h * k)}px`; el.style.backgroundSize = `${1774 * k}px ${887 * k}px`; el.style.backgroundPosition = `${-x * k}px ${-y * k}px`; };
    const calm = document.documentElement.classList.contains('calm') || matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (calm) { show(ENV[ENV.length - 1]); return { played: Promise.resolve() }; }
    const played = new Promise(done => { let i = 0; const step = () => { show(ENV[i]); if (i === ENV.length - 1) return done(); setTimeout(() => { i++; step(); }, ENV[i][4]); }; step(); });
    return { played };
  }

  // ── 뽑기: 하급·중급·고급 모집. 무작위임을 분명히, 확률·천장은 서버 값 ──
  let drawPick = 'LOW';
  async function draw() {
    inner('뽑기', '');
    view.innerHTML = '<div class="state" role="status"><span class="spin"></span>모집 정보를 받는 중…</div>';
    let info;
    try { info = await S.api('draw'); } catch (x) { if (x.status !== 401) view.innerHTML = `<section class="soon"><p class="err" role="alert">${esc(x.message)}</p></section>`; return; }
    if (!location.hash.startsWith('#draw')) return;
    const at = Date.now();
    const g = () => info.gachas.find(x => x.kind === drawPick) || info.gachas[0];
    view.innerHTML = `<section class="split draw">
      <div class="pane list-pane"><h2>모집 고르기</h2><div class="kinds" role="radiogroup" aria-label="모집 종류">${info.gachas.map(x => `<button type="button" class="kind" role="radio" data-k="${x.kind}" aria-checked="${x.kind === drawPick}"><img src="/images/${esc(x.img)}.png" alt="" width="72" height="72"><span><b>${esc(x.label)} 모집</b><small>지원서 ${fmt(x.cost)}장</small></span></button>`).join('')}</div>
        <div class="claim"><p data-claimtext></p><button class="btn ghost wide" type="button" data-claim>지원서 받기</button></div></div>
      <div class="pane detail-pane" aria-live="polite"><div data-detail></div>
        <div class="cta-row"><span data-qslot></span><button class="btn primary lg" type="button" data-go>모집하기</button></div><p class="why" data-why role="status"></p></div></section>`;
    const claimText = () => {
      const left = Math.max(0, info.wait - (Date.now() - at));
      $('[data-claimtext]', view).textContent = info.unlimited ? '지원서 무제한 계정이에요.' : left > 0 ? `다음 지원서 받기까지 ${hm(left)} (한 번에 ${fmt(info.claim)}장, ${fmt(info.cooldownHours)}시간마다)` : `지금 지원서 ${fmt(info.claim)}장을 받을 수 있어요.${info.event ? ` (${esc(info.event.label)})` : ''}`;
      $('[data-claim]', view).disabled = !info.unlimited && left > 0;
    };
    const detail = () => {
      const x = g(), total = x.weights.reduce((a, w) => a + w.w, 0), max = Math.max(1, Math.min(50, Math.floor((S.state?.me?.credits ?? info.credits) / x.cost)));
      $('[data-detail]', view).innerHTML = `<h2>${esc(x.label)} 모집</h2><p class="sub">지원서 ${fmt(x.cost)}장으로 한 명을 모집해요. <b>어떤 대원이 올지는 무작위</b>예요.</p>
        <table class="odds"><caption class="sr">등급별 확률</caption><thead><tr><th scope="col">등급</th><th scope="col">확률</th></tr></thead><tbody>${x.weights.map(w => `<tr><th scope="row">${esc(w.tierName)}</th><td>${(w.w / total * 100).toFixed(w.w / total * 100 < 1 ? 1 : 0)}%</td></tr>`).join('')}</tbody></table>
        ${x.kind === 'HIGH' && info.pity ? `<p class="sub">${esc(info.pity.tierName)} 보장까지 고급 모집 ${fmt(info.pity.left)}회(천장)</p>` : ''}
        <p class="sub"><a href="/odds" target="_blank" rel="noopener">전체 확률 보기</a> · 가진 지원서 ${fmt(S.state?.me?.credits ?? info.credits)}장</p>`;
      $('[data-qslot]', view).innerHTML = qty(max);
      const get = qtyBind(view, max, n => { $('[data-why]', view).textContent = `지원서 ${fmt(n * x.cost)}장을 써요.`; });
      $('[data-why]', view).textContent = `지원서 ${fmt(get() * x.cost)}장을 써요.`;
      $('[data-go]', view).onclick = e => run(e.currentTarget, '지원서 읽는 중…', async () => {
        const before = $('[data-detail]', view).innerHTML, env = envelope($('[data-detail]', view));
        try {
          const req = S.api('draw', { kind: x.kind, times: get() });
          await Promise.all([req.catch(() => {}), env.played]);   // 마지막 장면은 서버 결과가 올 때까지 유지
          const r = (await req).result;
          $('[data-detail]', view).innerHTML = `<h2>모집 결과</h2><div class="got">${r.items.map(it => `<div class="got-card${it.first ? ' first' : ''}"><img src="${S.card(it.id, it.img)}" alt="${esc(it.name)} 입사 등록증" width="120" height="160"><b class="${S.card(it.id, it.img).startsWith('/site/reg/') ? 'sr' : ''}">${esc(it.name)}</b><small>${esc(it.tierName)}${it.n > 1 ? ` ×${fmt(it.n)}` : ''}${it.first ? ' · 새 대원' : ''}</small></div>`).join('')}</div><div class="srv">${lines(r.summary)}</div>
            <button class="btn ghost" type="button" data-again>${esc(x.label)} 모집으로 돌아가기</button>`;
          $('[data-again]', view).onclick = detail;
          S.catalog = null; await refreshMe(); info = await S.api('draw').catch(() => info);
        } catch (y) { $('[data-detail]', view).innerHTML = before; $('[data-why]', view).textContent = y.message; }   // 실패하면 결과 없이 원래 화면으로
      });
    };
    $('.kinds', view).onclick = e => { const b = e.target.closest('[data-k]'); if (!b) return; drawPick = b.dataset.k; $$('.kind', view).forEach(k => k.setAttribute('aria-checked', String(k === b))); detail(); };
    $('[data-claim]', view).onclick = e => run(e.currentTarget, '받는 중…', async () => {
      try { const r = await S.api('claim', {}); S.toast(term(r.result).split('\n')[0]); await refreshMe(); info = await S.api('draw'); } catch (y) { S.toast(y.message, 'bad'); }
      claimText(); detail();
    });
    claimText(); detail();
  }

  // ── 조합: 조합식(가능한 것 먼저) 페이지 그리드 + 같은 화면 재료 상세. 직접 섞기(숨은 조합 찾기) ──
  let craftTab = 'recipes', craftSel = null;
  async function craft() {
    inner('조합', 'collection');
    view.innerHTML = '<div class="state" role="status"><span class="spin"></span>조합식을 받는 중…</div>';
    let info;
    try { info = await S.api('craft'); } catch (x) { if (x.status !== 401) view.innerHTML = `<section class="soon"><p class="err" role="alert">${esc(x.message)}</p></section>`; return; }
    if (!location.hash.startsWith('#craft')) return;
    const mix = [];
    view.innerHTML = `<section class="split craft">
      <div class="pane list-pane"><div class="filters two" role="tablist"><button type="button" role="tab" data-t="recipes" aria-selected="${craftTab === 'recipes'}">조합식 <small>${fmt(info.recipes.filter(r => r.ready).length)}개 가능</small></button><button type="button" role="tab" data-t="mix" aria-selected="${craftTab === 'mix'}">직접 섞기</button></div>
        <div class="grid craft-grid" role="listbox" aria-label="${craftTab === 'mix' ? '재료' : '조합식'}"></div>${pagerHtml}</div>
      <div class="pane detail-pane" aria-live="polite"><div data-detail></div><div class="cta-row"><span data-qslot></span><button class="btn primary lg" type="button" data-go disabled>조합하기</button></div><p class="why" data-why role="status"></p></div></section>`;
    $('.filters', view).onclick = e => { const b = e.target.closest('[data-t]'); if (b) { craftTab = b.dataset.t; craft(); } };
    const go = $('[data-go]', view), why = $('[data-why]', view);
    if (craftTab === 'recipes') {
      const detail = r => {
        craftSel = r.id;
        $('[data-detail]', view).innerHTML = `<div class="d-head"><img class="${S.card(r.id, r.img).startsWith('/site/reg/') ? 'reg' : ''}" src="${S.card(r.id, r.img)}" alt="" width="96" height="128"><div><h2>${esc(r.name)}</h2><p class="sub">${esc(r.tierName)}${r.isNew ? ' · 처음 만드는 대원' : ''}</p></div></div>
          <ul class="mats">${r.materials.map(m => `<li class="${m.usable >= m.need ? 'ok' : 'short'}"><img src="/thumbs/${encodeURIComponent(m.img)}.jpg" alt="" width="40" height="40"><b>${esc(m.name)}</b><span>${fmt(m.usable)} / ${fmt(m.need)}${m.locked ? ` · 보호 ${fmt(m.locked)}` : ''}${m.away ? ` · 탐험 ${fmt(m.away)}` : ''}</span><em>${m.usable >= m.need ? '충분' : '부족'}</em></li>`).join('')}</ul>
          <p class="sub">보호·탐험 중인 대원은 재료로 쓰지 않아요.</p>`;
        $('[data-qslot]', view).innerHTML = r.ready ? qty(Math.max(1, Math.min(50, r.max))) : '';
        const get = r.ready ? qtyBind(view, Math.max(1, Math.min(50, r.max))) : () => 1;
        let confirmed = !r.confirm;
        go.disabled = !r.ready; go.textContent = '조합하기';
        why.textContent = r.ready ? (r.confirm ? '희귀 이상 대원이에요. 한 번 더 눌러 확인해요.' : '') : '재료가 모자라요.';
        go.onclick = e => {
          if (!confirmed) { confirmed = true; go.textContent = `재료를 써서 ${get()}번 조합`; why.textContent = '재료로 쓴 대원은 사라져요. 맞으면 한 번 더 눌러요.'; return; }
          run(e.currentTarget, '조합하는 중…', async () => {
            try { const t = (await S.api('craft', { id: r.id, times: get() })).result; $('[data-detail]', view).insertAdjacentHTML('afterbegin', `<div class="srv done">${lines(t)}</div>`); S.catalog = null; await refreshMe(); setTimeout(craft, 1800); }
            catch (y) { why.textContent = y.message; }
          });
        };
        $$('.craft-grid [data-i]', view).forEach(b => b.setAttribute('aria-selected', String(b.dataset.id === r.id)));
      };
      const p = S.pager(view, { grid: '.craft-grid', list: () => info.recipes, selected: () => info.recipes.find(r => r.id === craftSel),
        item: (r, i, on) => S.regCard({ i, id: r.id, name: r.name, image: S.card(r.id, r.img), sub: r.ready ? `가능 · 최대 ${r.max}` : r.tierName, selected: on }), onPick: r => detail(r) });
      p.draw(); const first = info.recipes.find(r => r.id === craftSel) || info.recipes[0]; if (first) { p.show(first); detail(first); } else $('[data-detail]', view).innerHTML = '<p class="muted">아직 열린 조합식이 없어요.</p>';
      S.onResize(() => p.draw());
    } else {
      const drawMix = () => {
        $('[data-detail]', view).innerHTML = `<h2>직접 섞기</h2><p class="sub">재료 2~6마리를 골라 숨은 조합을 찾아요. 맞는 조합이 없으면 재료는 그대로예요.</p>
          <div class="mixed">${mix.length ? mix.map((id, k) => { const u = info.pool.find(x => x.id === id); return `<button type="button" class="chip" data-rm="${k}" aria-label="${esc(u.name)} 빼기">${esc(u.name)} ×</button>`; }).join('') : '<span class="muted">목록에서 재료를 골라요.</span>'}</div>`;
        go.disabled = mix.length < 2; go.textContent = '섞어 보기'; why.textContent = mix.length < 2 ? '재료를 2마리 이상 골라요.' : '';
        $('[data-qslot]', view).innerHTML = '';
        $('.mixed', view).onclick = e => { const b = e.target.closest('[data-rm]'); if (b) { mix.splice(Number(b.dataset.rm), 1); drawMix(); p.draw(); } };
      };
      const p = S.pager(view, { grid: '.craft-grid', list: () => info.pool, selected: () => null,
        item: (u, i) => { const used = mix.filter(x => x === u.id).length; return S.regCard({ i, id: u.id, name: u.name, image: S.card(u.id, u.img), sub: `쓸 수 있음 ${u.usable - used}`, selected: used > 0 }); },
        onPick: u => { if (mix.length >= 6) { why.textContent = '재료는 6마리까지예요.'; return; } if (mix.filter(x => x === u.id).length >= u.usable) { why.textContent = '쓸 수 있는 수를 넘었어요.'; return; } mix.push(u.id); drawMix(); p.draw(); } });
      go.onclick = e => run(e.currentTarget, '섞는 중…', async () => {
        try { const r = (await S.api('mix', { ids: mix })).result; $('[data-detail]', view).insertAdjacentHTML('afterbegin', `<div class="srv ${r.made ? 'done' : ''}">${lines(r.text)}</div>`); if (r.made) { S.catalog = null; await refreshMe(); setTimeout(craft, 2200); } }
        catch (y) { why.textContent = y.message; }
      });
      p.draw(); drawMix(); S.onResize(() => p.draw());
    }
  }

  // ── 탐험: 최상위 대원 최대 N마리 파견 → 시간이 지나면 보상. 시너지 효과는 서버 설정값을 그대로 안내 ──
  async function explore() {
    inner('탐험', '');
    const me = S.state?.me;
    if (!me) { view.innerHTML = '<div class="state"><span class="spin"></span>정보를 받는 중…</div>'; return; }
    const ex = me.economy?.expedition, max = me.prices?.maxParty || ex?.maxParty || 10;
    if (me.trip) {
      const ends = new Date(me.trip.endsAt).getTime();
      view.innerHTML = `<section class="split explore"><div class="pane list-pane"><h2 data-head>탐험 중</h2><ul class="trip">${me.trip.units.map(n => `<li>${esc(n)}</li>`).join('')}</ul></div>
        <div class="pane detail-pane"><p class="m-time" data-left></p><p class="sub">돌아오면 보상을 받을 수 있어요.</p>
        <div class="cta-row"><button class="btn ghost lg" type="button" data-skip>바로 귀환(새우gang ${fmt(me.prices?.skip?.snack)})</button><button class="btn primary lg" type="button" data-claim>보상 받기</button></div><div class="srv" data-out aria-live="polite"></div><p class="why" data-why role="status"></p></div></section>`;
      const tick = () => { const left = ends - Date.now(); $('[data-left]', view).textContent = left > 0 ? `${hm(left)} 남았어요` : '탐험에서 돌아왔어요'; $('[data-head]', view).textContent = left > 0 ? '탐험 중' : '탐험 완료'; $('[data-claim]', view).disabled = left > 0; $('[data-skip]', view).disabled = left <= 0; };
      tick(); const t = setInterval(() => { if (!location.hash.startsWith('#explore')) clearInterval(t); else tick(); }, 15000);
      $('[data-claim]', view).onclick = e => run(e.currentTarget, '받는 중…', async () => { try { const r = (await S.api('expedition/claim', {})).result; $('[data-out]', view).innerHTML = lines(r); await refreshMe(); setTimeout(explore, 2500); } catch (y) { $('[data-why]', view).textContent = y.message; } });
      $('[data-skip]', view).onclick = e => run(e.currentTarget, '귀환하는 중…', async () => { try { const r = (await S.api('shop', { item: 'skip', count: 1 })).result; $('[data-out]', view).innerHTML = lines(r); await refreshMe(); setTimeout(explore, 1500); } catch (y) { $('[data-why]', view).textContent = y.message; } });
      return;
    }
    const cands = me.units.filter(u => u.top && u.have - u.away > 0);
    const pick = [...(me.party || [])].filter(id => cands.some(u => u.id === id)).slice(0, max);
    view.innerHTML = `<section class="split explore"><div class="pane list-pane"><h2>보낼 대원 <small data-n></small></h2><div class="grid ex-grid" role="listbox" aria-label="탐험 후보" aria-multiselectable="true"></div>${pagerHtml}</div>
      <div class="pane detail-pane"><h2>탐험대</h2><div class="mixed" data-team></div><div class="syn" data-syn></div>
      <p class="sub">${fmt(ex?.minutes)}분 동안 다녀와요. 대원 한 마리당 새우gang ${fmt(ex?.snackPerUnit)}개가 기본이에요.</p>
      <div class="cta-row"><button class="btn primary lg" type="button" data-go>탐험 출발</button></div><div class="srv" data-out aria-live="polite"></div><p class="why" data-why role="status"></p></div></section>`;
    const drawTeam = () => {
      $('[data-n]', view).textContent = `${pick.length} / ${max}`;
      $('[data-team]', view).innerHTML = pick.length ? pick.map((id, k) => `<button type="button" class="chip" data-rm="${k}" aria-label="${esc(cands.find(u => u.id === id)?.name)} 빼기">${esc(cands.find(u => u.id === id)?.name)} ×</button>`).join('') : '<span class="muted">목록에서 대원을 골라요.</span>';
      // 시너지: 같은 계열 마리 수만 세고, 효과 수치는 서버 설정(economy.expedition) 문구 그대로
      const cnt = {}; for (const id of pick) { const k = cands.find(u => u.id === id)?.synergy; if (k) cnt[k] = (cnt[k] || 0) + 1; }
      const lv = ex?.levels || [2, 4];
      $('[data-syn]', view).innerHTML = Object.entries(cnt).map(([k, n]) => { const e = ex?.effects?.[k]; const step = n >= lv[1] ? 2 : n >= lv[0] ? 1 : 0; return `<p><b>${esc(e?.label || k)}</b> ${fmt(n)}마리${step ? ` · ${step}단계 시너지(${esc(e?.concept || '')})` : ` · ${fmt(lv[0])}마리부터 시너지`}</p>`; }).join('');
      $('[data-go]', view).disabled = !pick.length; $('[data-why]', view).textContent = pick.length ? '' : '대원을 1마리 이상 골라요.';
    };
    const p = S.pager(view, { grid: '.ex-grid', list: () => cands, selected: () => null,
      item: (u, i) => S.regCard({ i, id: u.id, name: u.name, image: S.card(u.id), sub: pick.includes(u.id) ? '탐험대' : esc(u.attrName || u.tierName), selected: pick.includes(u.id) }),
      onPick: u => { const k = pick.indexOf(u.id); if (k >= 0) pick.splice(k, 1); else if (pick.length < max) pick.push(u.id); else { $('[data-why]', view).textContent = `탐험대는 ${max}마리까지예요.`; return; } drawTeam(); p.draw(); } });
    $('[data-team]', view).onclick = e => { const b = e.target.closest('[data-rm]'); if (b) { pick.splice(Number(b.dataset.rm), 1); drawTeam(); p.draw(); } };
    $('[data-go]', view).onclick = e => run(e.currentTarget, '출발 준비 중…', async () => {
      try { await S.api('party', { ids: pick }); const r = (await S.api('expedition/start', {})).result; $('[data-out]', view).innerHTML = lines(r); await refreshMe(); setTimeout(explore, 2200); }
      catch (y) { $('[data-why]', view).textContent = y.message; }
    });
    p.draw(); drawTeam(); S.onResize(() => p.draw());
  }

  // ── 상점: 지원서 묶음(새우gang·땅콩), 전투 아이템. 가격은 서버 값 ──
  async function shop() {
    inner('상점', '');
    const me = S.state?.me;
    if (!me) { view.innerHTML = '<div class="state"><span class="spin"></span>정보를 받는 중…</div>'; return; }
    let defs = [];
    try { defs = (await S.api('me')).itemDefs || []; } catch (x) { if (x.status === 401) return; }
    if (!location.hash.startsWith('#shop')) return;
    const inv = new Map((S.state.items || me.items || []).map(x => [x.id, x.count]));
    const goods = [
      { key: 'tickets', name: `지원서 ${fmt(me.prices.tickets.amount)}장`, price: `새우gang ${fmt(me.prices.tickets.snack)}`, desc: '새우gang으로 지원서를 사요.', max: 50, buy: n => S.api('shop', { item: 'tickets', count: n }) },
      { key: 'peanutTickets', name: `지원서 ${fmt(me.prices.peanutTickets.amount)}장`, price: `땅콩 ${fmt(me.prices.peanutTickets.peanut)}`, desc: '땅콩으로 지원서를 사요.', max: 50, buy: n => S.api('shop', { item: 'peanutTickets', count: n }) },
      ...defs.map(d => ({ key: d.id, name: d.name, price: `새우gang ${fmt(d.price)}`, desc: `${d.desc} · 가진 수 ${fmt(inv.get(d.id) || 0)}`, icon: d.id, max: me.itemRules?.maxStack || 10, buy: n => S.api('items/buy', { id: d.id, count: n }) })),
    ];
    let sel = goods[0];
    view.innerHTML = `<section class="split shop"><div class="pane list-pane"><h2>물건</h2><div class="goods" role="listbox" aria-label="상점 물건">${goods.map((g, i) => `<button type="button" class="good" role="option" data-g="${i}" aria-selected="${i === 0}">${g.icon ? `<img src="/duel/icons/${esc(g.icon)}.png" alt="" width="40" height="40" onerror="this.remove()">` : '<img src="/site/a/draw.webp" alt="" width="40" height="40">'}<span><b>${esc(g.name)}</b><small>${esc(g.price)}</small></span></button>`).join('')}</div></div>
      <div class="pane detail-pane" aria-live="polite"><div data-detail></div><div class="cta-row"><span data-qslot></span><button class="btn primary lg" type="button" data-go>사기</button></div><div class="srv" data-out></div><p class="why" data-why role="status"></p></div></section>`;
    const detail = () => {
      $('[data-detail]', view).innerHTML = `<h2>${esc(sel.name)}</h2><p class="sub">${esc(sel.price)} · 한 번에 ${fmt(sel.max)}개까지</p><p>${esc(sel.desc)}</p>`;
      $('[data-qslot]', view).innerHTML = qty(sel.max); const get = qtyBind(view, sel.max);
      $('[data-go]', view).onclick = e => run(e.currentTarget, '사는 중…', async () => {
        try { const r = (await sel.buy(get())).result; $('[data-out]', view).innerHTML = lines(r); $('[data-why]', view).textContent = ''; await refreshMe(); S.header(); }
        catch (y) { $('[data-why]', view).textContent = y.message; }
      });
    };
    $('.goods', view).onclick = e => { const b = e.target.closest('[data-g]'); if (!b) return; sel = goods[Number(b.dataset.g)]; $$('.good', view).forEach(x => x.setAttribute('aria-selected', String(x === b))); $('[data-out]', view).innerHTML = ''; detail(); };
    detail();
  }

  // ── 계정: 닉네임·도감, 카카오 연결/해제, 쿠폰, 로그아웃, 계정 삭제(서버 규칙: 「탈퇴」 직접 입력) ──
  function account() {
    inner('계정', '');
    const me = S.state?.me;
    view.innerHTML = `<section class="split account"><div class="pane list-pane"><h2>${esc(me?.nickname || S.who?.nickname || '이름 없는 대장')}</h2>
        ${me ? `<p class="sub">도감 ${fmt(me.dex)} / ${fmt(me.dexTotal)}</p><p class="sub">카카오 계정 ${me.kakao ? '연결됨' : '연결 안 됨'}</p>` : ''}
        <div class="stack">${me && !me.kakao ? '<a class="btn kakao wide" href="/my/kakao">카카오 계정 연결</a>' : ''}${me?.kakao ? '<button class="btn ghost wide" type="button" data-unlink>카카오 연결 해제</button>' : ''}
        <a class="btn ghost wide" href="/">공개 홈페이지</a><button class="btn ghost wide" type="button" data-logout>로그아웃</button></div><p class="why" data-why1 role="status"></p></div>
      <div class="pane detail-pane"><form class="coupon" data-coupon><label for="cp"><b>쿠폰 코드</b></label><div class="code-row"><input id="cp" maxlength="60" autocomplete="off"><button class="btn primary" type="submit">입력</button></div><div class="srv" data-cout aria-live="polite"></div></form>
        <details class="danger-zone"><summary>계정 삭제</summary><p>계정을 지우면 대원·재화·기록이 모두 사라지고 되돌릴 수 없어요. <a href="/account/delete" target="_blank" rel="noopener">삭제 안내</a></p>
        <label for="del">확인을 위해 「탈퇴」라고 적어 주세요.</label><div class="code-row"><input id="del" autocomplete="off"><button class="btn danger" type="button" data-del>계정 삭제</button></div><p class="err" data-derr role="alert"></p></details></div></section>`;
    const w1 = $('[data-why1]', view);
    $('[data-logout]', view).onclick = e => run(e.currentTarget, '로그아웃 중…', async () => { try { await S.api('logout', {}); location.replace('/'); } catch (y) { w1.textContent = y.message; } });
    const un = $('[data-unlink]', view);
    if (un) un.onclick = e => { if (un.dataset.sure !== '1') { un.dataset.sure = '1'; un.textContent = '정말 해제할까요? 한 번 더 눌러요'; w1.textContent = '해제하면 다음부터 카톡 코드로 로그인해요.'; return; }
      run(e.currentTarget, '해제하는 중…', async () => { try { await S.api('kakao/unlink', {}); await refreshMe(); account(); } catch (y) { w1.textContent = y.message; } }); };
    $('[data-coupon]', view).onsubmit = e => { e.preventDefault(); const b = $('[data-coupon] button', view);
      run(b, '확인 중…', async () => { try { const r = (await S.api('coupon', { code: $('#cp', view).value })).result; $('[data-cout]', view).innerHTML = lines(r); await refreshMe(); S.header(); } catch (y) { $('[data-cout]', view).innerHTML = `<p class="err">${esc(y.message)}</p>`; } }); };
    $('[data-del]', view).onclick = e => {
      if ($('#del', view).value.trim() !== '탈퇴') { $('[data-derr]', view).textContent = '확인 칸에 「탈퇴」를 적어 주세요.'; return; }
      run(e.currentTarget, '삭제하는 중…', async () => { try { await S.api('account/delete', { confirm: '탈퇴' }); location.replace('/'); } catch (y) { $('[data-derr]', view).textContent = y.message; } });
    };
  }

  // ── 출석·미션(항구 홈의 버튼에서 여는 시트): 오늘 미션 진행 + 7일 출석표 + 출석하기 ──
  async function daily() {
    S.sheet('출석 · 미션', '<div class="state" role="status"><span class="spin"></span>오늘 미션을 받는 중…</div>');
    const body = $('#sheet .sheet-body');
    let d;
    try { d = await S.api('daily'); } catch (x) { if (x.status !== 401) body.innerHTML = `<p class="err" role="alert">${esc(x.message)}</p>`; return; }
    const a = d.attend;
    body.innerHTML = `<h3 class="sh">출석 ${fmt(a.week)}주째 · 연속 ${fmt(a.streak)}일</h3>
      <ol class="week">${a.rewards.map((r, i) => { const day = i + 1, got = a.today ? day <= a.dayInWeek : day < a.dayInWeek, now = !a.today && day === a.dayInWeek; return `<li class="${got ? 'got' : ''}${now ? ' now' : ''}"><small>${day}일</small><b>${fmt(r)}</b><span>${got ? '받음' : now ? '오늘' : '장'}</span></li>`; }).join('')}</ol>
      <button class="btn primary wide" type="button" data-attend ${a.today ? 'disabled' : ''}>${a.today ? '오늘은 출석했어요' : `출석하기 · 지원서 ${fmt(a.next)}장`}</button><p class="why" data-aout role="status"></p>
      <h3 class="sh">오늘의 미션</h3><ul class="missions">${d.missions.map(m => `<li class="${m.done ? 'done' : ''}"><b>${esc(term(m.label))}</b><span>${fmt(m.value)} / ${fmt(m.goal)}</span><em>${m.done ? '완료' : `지원서 ${fmt(m.tickets)}장`}</em></li>`).join('')}</ul>
      <p class="muted small">미션 보상은 목표를 채우면 서버가 지급해요.</p>`;
    const b = $('[data-attend]', body);
    b.onclick = () => run(b, '출석하는 중…', async () => {
      try { const r = (await S.api('attend', {})).result; $('[data-aout]', body).textContent = `연속 ${fmt(r.streak)}일째 출석! 지원서 ${fmt(r.tickets)}장을 받았어요.`; await refreshMe(); S.header(); setTimeout(daily, 1600); }
      catch (y) { $('[data-aout]', body).textContent = y.message; }
    });
  }

  Object.assign(S.routes, { draw, craft, explore, shop, account });
  S.daily = daily;
})();
