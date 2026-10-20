// 웹 게임 화면: 방범대 도감(#dex) · 전투 편성(#team, 실시간 준비에도 같은 부품) · 플레이 모드(#play)
// 목록은 페이지 그리드 + 같은 화면 상세(전체 보기 창 없음). 그리드 칸 수는 실제 남은 높이로 계산해 화면 전체 스크롤을 만들지 않는다.
(() => {
  'use strict';
  const S = window.GMM, { esc, fmt } = S;
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const view = $('#view'), app = $('#app');

  // 그리드 한 페이지 칸 수: 열은 CSS(--cols), 행은 남은 높이(최소 카드 높이 --min-h)로. 최대 행(--rows)을 넘지 않는다
  function capacity(grid) {
    const cs = getComputedStyle(grid), cols = parseInt(cs.getPropertyValue('--cols')) || 3, maxRows = parseInt(cs.getPropertyValue('--rows')) || 3;
    const minH = parseFloat(cs.getPropertyValue('--min-h')) || 96, gap = parseFloat(cs.rowGap) || 8;
    const rows = Math.max(1, Math.min(maxRows, Math.floor((grid.clientHeight + gap) / (minH + gap))));
    grid.style.gridTemplateRows = `repeat(${rows}, minmax(0, 1fr))`;
    return cols * rows;
  }
  // 페이지 넘김 부품: render(items, page) 다시 그리기, resize 때 칸 수 다시 계산
  function pager(root, { grid, list, item, onPick, selected, label = '페이지' }) {
    const g = $(grid, root), prev = $('[data-prev]', root), next = $('[data-next]', root), info = $('[data-pageinfo]', root);
    let page = 0, per = 1, items = list();
    const draw = () => {
      items = list(); per = capacity(g);
      const pages = Math.max(1, Math.ceil(items.length / per)); page = Math.min(page, pages - 1);
      g.innerHTML = items.length ? items.slice(page * per, page * per + per).map((x, i) => item(x, page * per + i, selected() === x)).join('') : '<p class="empty">표시할 대원이 없어요.</p>';
      info.textContent = `${label} ${page + 1} / ${pages}`; prev.disabled = page === 0; next.disabled = page >= pages - 1;
    };
    prev.onclick = () => { page--; draw(); }; next.onclick = () => { page++; draw(); };
    g.onclick = e => { const b = e.target.closest('[data-i]'); if (b && !b.disabled) onPick(items[Number(b.dataset.i)], Number(b.dataset.i)); };
    const show = x => { const i = items.indexOf(x); if (i >= 0) page = Math.floor(i / per); draw(); };
    return { draw, show };
  }
  let resizer = null;
  const onResize = fn => { resizer = fn; };
  addEventListener('resize', () => { clearTimeout(onResize.t); onResize.t = setTimeout(() => resizer && resizer(), 120); });
  addEventListener('hashchange', () => { resizer = null; });

  const unitSub = u => [u.attrName || u.attr, u.tierName].filter(Boolean).join(' · ');
  // 공통 대원 카드(입사 등록증 자리). 최종 카드 디자인이 정해지면 이 함수와 CSS .reg-card만 바꾼다(145종 그림은 기존 그대로 씀)
  // card: {i, id, name, image, sub, selected}
  const regCard = c => `<button type="button" class="card unit reg-card" data-card="registration" role="option" data-i="${c.i}" data-id="${esc(c.id)}" aria-selected="${!!c.selected}">
    <span class="reg-photo"><img src="${esc(c.image)}" alt="" width="96" height="96" loading="lazy"></span><b class="reg-name${String(c.image).startsWith('/site/reg/') ? ' sr' : ''}">${esc(c.name)}</b><small class="reg-sub">${esc(c.sub || '')}</small></button>`;
  S.regCard = regCard;
  const hpPct = u => (u.maxHp ? Math.max(0, Math.min(100, Math.round(u.hp / u.maxHp * 100))) : 0);

  // ── 방범대 도감 ──
  const DEX_FILTERS = [['all', '전체'], ['attr', '속성'], ['tier', '등급'], ['own', '보유']];
  let dexState = { f: 'all', v: '', sel: null };
  async function dex() {
    S.title('방범대 도감', ''); app.dataset.place = 'collection'; app.dataset.screen = 'inner';
    view.innerHTML = `<section class="dex"><div class="dex-list">
      <div class="filters" role="tablist" aria-label="도감 거르기">${DEX_FILTERS.map(([k, t]) => `<button type="button" role="tab" data-f="${k}" aria-selected="${dexState.f === k}">${t}</button>`).join('')}</div>
      <div class="chips" data-chips hidden></div>
      <div class="grid dex-grid" role="listbox" aria-label="대원 목록"></div>
      <div class="pager"><button class="btn ghost icon" type="button" data-prev aria-label="이전 페이지">‹</button><span data-pageinfo aria-live="polite"></span><button class="btn ghost icon" type="button" data-next aria-label="다음 페이지">›</button></div>
    </div><aside class="detail dex-detail" aria-live="polite"><div class="state"><span class="spin"></span>도감을 받는 중…</div></aside>
    <div class="cta"><button class="btn primary lg wide" type="button" data-add disabled>편성에 넣기</button><p class="why" data-why></p></div></section>`;
    if (!S.catalog) { try { S.busy('catalog', true); S.catalog = (await S.api('native/catalog')).units; } catch (x) { if (x.status !== 401) $('.dex-detail').innerHTML = `<p class="err" role="alert">${esc(x.message)}</p>`; return; } finally { S.busy('catalog', false); } }
    if (!view.querySelector('.dex')) return;   // 받는 동안 다른 화면으로 이동
    const all = S.catalog;
    const values = f => [...new Set(all.map(u => (f === 'attr' ? u.attrName : f === 'tier' ? u.tierName : '')).filter(Boolean))];
    const list = () => all.filter(u => dexState.f === 'all' || (dexState.f === 'own' ? u.have > 0 : !dexState.v || (dexState.f === 'attr' ? u.attrName : u.tierName) === dexState.v));
    const chips = $('[data-chips]', view);
    const drawChips = () => {
      chips.hidden = !(dexState.f === 'attr' || dexState.f === 'tier');
      if (chips.hidden) return;
      chips.innerHTML = values(dexState.f).map(v => `<button type="button" data-v="${esc(v)}" aria-pressed="${dexState.v === v}">${esc(v)}</button>`).join('');
    };
    const detail = u => {
      dexState.sel = u.id;
      const owned = S.state?.units.find(x => x.id === u.id), add = $('[data-add]', view), why = $('[data-why]', view);
      $('.dex-detail', view).innerHTML = `<div class="d-head"><img class="${S.card(u.id, u.image).startsWith('/site/reg/') ? 'reg' : ''}" src="${esc(S.card(u.id, u.image))}" alt="" width="132" height="176"><div><h2>${esc(u.name)}</h2><p class="sub">${esc(unitSub(u)) || '&nbsp;'}</p>
        <p class="sub">${u.known ? `보유 ${fmt(u.have)}${u.away ? ` · 탐험 중 ${fmt(u.away)}` : ''}` : '아직 만나지 못했어요'}</p></div></div>
        <div class="d-body">${u.quote ? `<q>${esc(u.quote)}</q>` : ''}${u.tagline ? `<p>${esc(u.tagline)}</p>` : ''}${u.intro ? `<p class="muted">${esc(u.intro)}</p>` : ''}${u.hint ? `<p><b>단서</b> ${esc(u.hint)}</p>` : ''}
        ${u.source ? `<p><b>만나는 방법</b> ${esc(u.source)}</p>` : ''}
        ${u.materials.length ? `<p><b>조합 재료</b> ${u.materials.map(m => `${esc(m.name)} ${fmt(m.need)}`).join(' · ')}</p>` : ''}</div>`;
      // 편성은 기존 규칙: 보유한 최상위 대원만(서버 편성 API와 같음)
      add.disabled = !owned; why.textContent = owned ? '' : !u.top ? '전투 편성은 최상위(제한·초월·영원·불멸) 대원만 할 수 있어요.' : '보유한 대원만 편성할 수 있어요.';
      add.onclick = owned ? () => { location.hash = `#team?add=${encodeURIComponent(owned.key)}`; } : null;
      $$('.dex-grid [data-i]', view).forEach(b => b.setAttribute('aria-selected', String(b.dataset.id === u.id)));
    };
    const p = pager(view, {
      grid: '.dex-grid', list, selected: () => all.find(u => u.id === dexState.sel),
      item: (u, i, on) => regCard({ i, id: u.id, name: u.name, image: S.card(u.id, u.image), sub: u.known ? u.attrName || u.tierName : '미발견', selected: on }),
      onPick: u => detail(u),
    });
    $('.filters', view).onclick = e => { const b = e.target.closest('[data-f]'); if (!b) return; dexState.f = b.dataset.f; dexState.v = ''; $$('.filters [data-f]', view).forEach(x => x.setAttribute('aria-selected', String(x === b))); drawChips(); p.draw(); };
    chips.onclick = e => { const b = e.target.closest('[data-v]'); if (!b) return; dexState.v = dexState.v === b.dataset.v ? '' : b.dataset.v; drawChips(); p.draw(); };
    drawChips(); p.draw();
    const first = all.find(u => u.id === dexState.sel) || list()[0]; if (first) { p.show(first); detail(first); }
    onResize(() => p.draw());
  }

  // ── 전투 편성(평상시 저장) · 실시간 준비(같은 부품, 준비 완료) ──
  // opts: {mode:'save'|'ready', team:[key], candidates:[unit], deadline, submit(keys)}
  function skillCard(sk) {
    if (!sk) return '<div class="skill none"><b>빈 기술 칸</b><small>배운 기술이 없어요</small></div>';
    return `<div class="skill"><span class="attr-dot" data-attr="${esc(sk.attr || '')}" aria-hidden="true"></span><b>${esc(sk.name)}</b><small>${esc([sk.power ? `위력 ${sk.power}` : '', sk.cd ? `재사용 ${sk.cd}턴` : '', sk.summary || sk.desc].filter(Boolean).join(' · '))}</small></div>`;
  }
  function teamBoard(opts) {
    const units = opts.candidates, byKey = k => units.find(u => u.key === k);
    let slots = [...opts.team, null, null, null].slice(0, 3), cur = Math.max(0, slots.findIndex(k => !k)), focus = slots[0] ? slots[0] : null;
    if (opts.focus && byKey(opts.focus)) { focus = opts.focus; const at = slots.indexOf(opts.focus); cur = at >= 0 ? at : cur; }
    if (cur < 0) cur = 0;
    view.innerHTML = `<section class="team ${opts.mode}">
      <div class="slots-wrap">${opts.tabs || ''}<div class="slots" role="radiogroup" aria-label="현재 3인"></div></div>
      <div class="focus"><div class="stand small" data-standee></div><div class="f-info"></div><div class="skills" aria-label="선택 대원 기술"></div></div>
      <div class="cands"><h3>보유 대원</h3><div class="grid team-grid" role="listbox" aria-label="후보 대원"></div>
        <div class="pager"><button class="btn ghost icon" type="button" data-prev aria-label="이전 페이지">‹</button><span data-pageinfo aria-live="polite"></span><button class="btn ghost icon" type="button" data-next aria-label="다음 페이지">›</button></div></div>
      <div class="cta">${opts.mode === 'ready' ? '<p class="timer" data-timer hidden></p>' : ''}<button class="btn primary lg wide" type="button" data-go>${opts.mode === 'ready' ? '준비 완료' : '편성 저장'}</button><p class="why" data-why role="status"></p></div></section>`;
    const drawSlots = () => {
      $('.slots', view).innerHTML = slots.map((k, i) => { const u = byKey(k); return `<button type="button" class="slot" role="radio" aria-checked="${i === cur}" data-s="${i}">
        ${u ? `<img src="${S.thumb(u.id)}" alt="" width="64" height="64">` : '<span class="ph" aria-hidden="true"></span>'}<span><small>슬롯 ${i + 1}</small><b>${u ? esc(u.name) : '비어 있음'}</b>${u ? `<small>${esc([u.attr, S.ROLE[u.role]].filter(Boolean).join(' · '))}</small>` : ''}</span></button>`; }).join('');
    };
    const drawFocus = () => {
      const u = byKey(focus);
      if (!u) { $('.f-info', view).innerHTML = '<p class="muted">슬롯을 고르고 아래 후보에서 대원을 골라요.</p>'; $('.skills', view).innerHTML = ''; $('.stand', view).innerHTML = ''; return; }
      $('.f-info', view).innerHTML = `<h2>${esc(u.name)}</h2><p class="sub">Lv ${fmt(u.level)} · ${S.STAGE[u.stage] ?? ''} · ${esc(u.attr || '')} · ${esc(S.ROLE[u.role] || '')}</p>
        ${u.maxHp && opts.mode === 'save' && opts.need !== 3 ? `<p class="sub">스토리 체력 ${fmt(u.hp)} / ${fmt(u.maxHp)}</p>` : ''}${opts.mode === 'save' && u.promotionNeed ? `<p class="sub">경험치 ${fmt(u.xp)} / ${fmt(u.xpNext)}${u.canLearn ? ' · 새 기술 배우기 가능' : ''}${u.canPromote || u.canEvolve ? ' · 승진 가능' : ''}</p>` : ''}`;
      // 스토리 편성에서만: 성장(기술 배우기·승진)·체력 회복. 대전 편성·실시간 준비에서는 보여 주지 않음
      if (opts.mode === 'save' && opts.need !== 3 && u.key) {
        const canGrow = u.learn?.length || u.promo?.length || u.evolve?.length;
        $('.f-info', view).insertAdjacentHTML('beforeend', `<div class="f-acts"><button class="btn ${canGrow ? 'primary' : 'ghost'} sm" type="button" data-grow>${canGrow ? '성장하기' : '성장 조건'}</button>${u.maxHp && u.hp < u.maxHp ? '<button class="btn ghost sm" type="button" data-heal>체력 회복</button>' : ''}</div>`);
        $('[data-grow]', view).onclick = () => S.growth(u.key, () => S.render());
        const hb = $('[data-heal]', view); if (hb) hb.onclick = () => S.heal(u.key, () => S.render());
      }
      const sk = (u.skills || []).filter(s => !String(s.id).startsWith('combo-'));
      $('.skills', view).innerHTML = [0, 1, 2, 3].map(i => skillCard(sk[i])).join('');
      S.standee($('.stand', view), S.sprite(u.image), u.name);
    };
    const changed = () => JSON.stringify(slots.filter(Boolean)) !== JSON.stringify(opts.team);
    const drawCta = () => {
      const n = slots.filter(Boolean).length, go = $('[data-go]', view), why = $('[data-why]', view);
      if (!go) return;   // 제출 뒤 다른 화면(증강체 등)으로 바뀐 경우
      const need = opts.need === 3 || opts.mode === 'ready' ? n !== 3 : n < 1;
      go.disabled = need || opts.busy || (opts.mode === 'save' && !changed());
      why.textContent = need ? (opts.mode === 'ready' ? '대원 3명을 골라야 준비할 수 있어요.' : opts.need === 3 ? '대전에는 서로 다른 최상위 대원 3명이 필요해요.' : '대원을 1명 이상 골라 주세요.') : opts.mode === 'save' && !changed() ? '저장된 편성과 같아요.' : '';
    };
    drawSlots(); drawFocus(); drawCta();
    const p = pager(view, {
      grid: '.team-grid', list: () => units, selected: () => byKey(focus),
      item: (u, i, on) => { const at = slots.indexOf(u.key); return regCard({ i, id: u.id, name: u.name, image: S.card(u.id), sub: at >= 0 ? `슬롯 ${at + 1}` : [u.attr, u.level ? `Lv ${u.level}` : ''].filter(Boolean).join(' · '), selected: on }); },
      onPick: u => {
        // 후보를 고르면 선택한 슬롯만 바뀐다(이미 다른 슬롯에 있으면 서로 자리 바꿈). 서버 제출은 따로
        const at = slots.indexOf(u.key);
        if (at >= 0 && at !== cur) { slots[at] = slots[cur]; }
        slots[cur] = u.key; focus = u.key;
        const empty = slots.findIndex(k => !k); if (empty >= 0) cur = empty;
        drawSlots(); drawFocus(); drawCta(); p.draw();
      },
    });
    $('.slots', view).onclick = e => { const b = e.target.closest('[data-s]'); if (!b) return; cur = Number(b.dataset.s); if (slots[cur]) focus = slots[cur]; drawSlots(); drawFocus(); p.draw(); };
    $('.slots', view).onkeydown = e => { if (!['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'].includes(e.key)) return; e.preventDefault(); cur = (cur + (e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : 2)) % 3; drawSlots(); $(`[data-s="${cur}"]`, view).focus(); };
    $('[data-go]', view).onclick = async e => {
      const b = e.currentTarget, keys = slots.filter(Boolean);
      opts.busy = true; drawCta(); b.setAttribute('aria-busy', 'true'); const label = b.textContent; b.textContent = opts.mode === 'ready' ? '보내는 중…' : '저장 중…';
      try { await opts.submit(keys); opts.team = keys; } catch (x) { $('[data-why]', view).textContent = x.message; }
      finally { opts.busy = false; b.removeAttribute('aria-busy'); b.textContent = label; drawCta(); }
    };
    p.draw(); if (focus) p.show(byKey(focus));
    onResize(() => { p.draw(); });
    return { redrawCta: drawCta };
  }
  // 편성은 두 가지(앱과 같음): 스토리·연습용(native/team, 개체 키 1~3) · 대전용(battle-team, 서로 다른 최상위 종 3)
  function team(params) {
    S.title('전투 편성', ''); app.dataset.place = 'team'; app.dataset.screen = 'inner';
    const st = S.state;
    if (!st) { view.innerHTML = '<div class="state"><span class="spin"></span>대원 정보를 받는 중…</div>'; return; }
    const pvp = params.get('mode') === 'pvp', add = params.get('add'), u = params.get('u');
    const tabs = `<div class="team-tabs" role="tablist" aria-label="편성 종류"><a role="tab" href="#team" aria-selected="${!pvp}">스토리·연습</a><a role="tab" href="#team?mode=pvp" aria-selected="${pvp}">대전(일반·랭크)</a></div>`;
    if (!pvp) {
      const keys = [...st.team];
      teamBoard({ mode: 'save', tabs, team: keys, candidates: st.units, focus: add || u || keys[0],
        submit: async next => { await S.api('native/team', { keys: next }); S.state.team = next; S.toast('스토리 편성을 저장했어요.'); S.header(); } });   // 저장은 서버 응답 뒤에만 반영
      if (add && !keys.includes(add)) S.toast('넣을 슬롯을 고른 뒤 후보에서 이 대원을 다시 눌러 주세요.');
      return;
    }
    const species = [...new Map(st.units.map(x => [x.id, { ...x, key: x.id }])).values()];
    const saved = (st.me.battleTeam || []).filter(id => species.some(x => x.id === id));
    const addId = add && st.units.find(x => x.key === add)?.id;
    teamBoard({ mode: 'save', need: 3, tabs, team: saved, candidates: species, focus: addId || saved[0],
      submit: async next => { const r = await S.api('battle-team', { ids: next }); S.state.me.battleTeam = r.battleTeam || next; S.toast('대전 편성을 저장했어요.'); } });
  }
  S.teamBoard = teamBoard;
  Object.assign(S, { pager, onResize });   // more.js(모집·조합·탐험·상점)도 같은 페이지 그리드를 쓴다

  // ── 플레이: 기존 모드(스토리·연습·일반전·랭크전) ──
  function play() {
    S.title('플레이', ''); app.dataset.place = ''; app.dataset.screen = 'inner';
    const st = S.state;
    const next = st && st.chapters.flatMap(c => c.stages.map(s => ({ ...s, chapter: c }))).find(s => s.open && !s.cleared);
    view.innerHTML = `<section class="modes">
      <button type="button" class="mode" data-mode="story" ${st ? '' : 'disabled'}><b>스토리</b><span>${next ? `다음: ${esc(next.chapter.title)} · ${esc(next.title)}` : st ? '단계를 골라 다시 도전할 수 있어요' : '정보를 받는 중…'}</span></button>
      <button type="button" class="mode" data-mode="practice" ${st ? '' : 'disabled'}><b>연습</b><span>지금 편성으로 연습 전투를 해요</span></button>
      <button type="button" class="mode" data-mode="normal"><b>일반전</b><span>실시간 대전. 30초 넘게 상대가 없으면 서버 규칙에 따라 AI와 겨룰 수 있고, 그때는 AI라고 표시해요.</span></button>
      <button type="button" class="mode" data-mode="rank"><b>랭크전</b><span>사람끼리만 겨뤄요. AI로 바꾸지 않아요.</span></button>
      <p class="err" role="alert"></p></section>`;
    view.querySelector('.modes').onclick = async e => {
      const b = e.target.closest('[data-mode]'); if (!b || b.disabled) return;
      const m = b.dataset.mode;
      if (m === 'normal' || m === 'rank') { location.hash = `#match?mode=${m}`; return; }
      if (m === 'story') { location.hash = '#story'; return; }   // 단계 고르기·대사는 story.js
      $$('.mode', view).forEach(x => { x.disabled = true; }); b.setAttribute('aria-busy', 'true');
      try {
        const body = { mode: 'practice', roster: st.team.map(k => st.units.find(u => u.key === k)?.id).filter(Boolean) };
        S.battle = { kind: 'native', snap: await S.api('native/battle/start', body), at: Date.now() };
        S.state.activeBattle = true; location.hash = '#battle';
      } catch (x) { $('.err', view).textContent = x.message; $$('.mode', view).forEach(y => { y.disabled = false; }); b.removeAttribute('aria-busy'); }
    };
  }

  Object.assign(S.routes, { dex, team, play });
})();
