// 웹 게임: 스토리 단계 고르기(#story) · 스토리 대사 보기 · 대원 성장(기술 배우기·승진 기술·승진) · 스토리 체력 회복
// 모두 기존 서버 API(native/state·battle/start·growth·heal)만 쓴다. 해금 조건·보상·성장 규칙은 서버가 판정하고 화면은 결과만 보여 준다.
(() => {
  'use strict';
  const S = window.GMM, { esc, fmt } = S;
  const $ = (s, el = document) => el.querySelector(s), $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const view = $('#view'), app = $('#app');
  const allStages = st => st.chapters.flatMap((c, ci) => c.stages.map(s => ({ ...s, chapter: c, ci })));

  // ── 대사 보기: 같은 화면 위 시트. 말한 사람 그림·이름, 다음/건너뛰기. 끝나면 done() ──
  function dialog(lines, title, done) {
    const st = S.state, lead = st && (st.units.find(u => u.key === st.team[0]) || st.units[0]);
    const who = w => w === 'N' ? { name: '', img: '' } : w === 'ME' ? { name: st?.me?.nickname || '수비조장', img: lead ? S.card(lead.id) : '' }
      : { name: st?.names?.[w] || w, img: st?.portraits?.[w] ? S.sprite(st.portraits[w]) : '' };
    let i = 0;
    const list = (lines || []).filter(l => l && l.say);
    if (!list.length) { done && done(); return; }
    S.sheet(title, '<div class="talk" aria-live="polite"></div>', body => {
      const draw = () => {
        const l = list[i], w = who(l.who);
        body.innerHTML = `<div class="talk ${l.who === 'N' ? 'narr' : ''}">${w.img ? `<img src="${esc(w.img)}" alt="" width="96" height="96">` : ''}<div>${w.name ? `<b>${esc(w.name)}</b>` : ''}<p>${esc(l.say)}</p></div></div>
          <div class="row talk-nav"><span class="muted small">${i + 1} / ${list.length}</span><button class="btn ghost" type="button" data-skip>건너뛰기</button><button class="btn primary" type="button" data-next>${i < list.length - 1 ? '다음' : done ? '시작' : '닫기'}</button></div>`;
        $('[data-next]', body).onclick = () => { if (i < list.length - 1) { i++; draw(); $('[data-next]', body).focus(); } else finish(); };
        $('[data-skip]', body).onclick = finish;
      };
      const finish = () => { $('#sheet').hidden = true; done && done(); };
      draw(); $('[data-next]', body).focus();
    });
  }
  S.dialog = dialog;

  // ── 스토리 단계 고르기: 장(chapter) 탭 + 단계 목록 + 같은 화면 상세(적·힌트·해금 조건) ──
  let storyCh = null, storySel = null;
  function story() {
    S.title('스토리', ''); app.dataset.place = ''; app.dataset.screen = 'inner';
    const st = S.state;
    if (!st) { view.innerHTML = '<div class="state"><span class="spin"></span>이야기를 받는 중…</div>'; return; }
    const stages = allStages(st), next = stages.find(s => s.open && !s.cleared);
    if (storyCh === null) storyCh = next ? next.ci : 0;
    const ch = st.chapters[storyCh];
    if (!storySel || !ch.stages.some(s => s.id === storySel)) storySel = (ch.stages.find(s => s.open && !s.cleared) || ch.stages.find(s => s.open) || ch.stages[0]).id;
    view.innerHTML = `<section class="split story">
      <div class="pane list-pane"><div class="chtabs" role="tablist" aria-label="장">${st.chapters.map((c, i) => `<button type="button" role="tab" data-ch="${i}" aria-selected="${i === storyCh}">${esc(c.title.split('·')[0].trim())}</button>`).join('')}</div>
        <h2>${esc(ch.title)}</h2>
        <div class="stages" role="listbox" aria-label="단계">${ch.stages.map(s => `<button type="button" class="stage-row ${s.cleared ? 'done' : ''}" role="option" data-st="${esc(s.id)}" aria-selected="${s.id === storySel}" ${s.open ? '' : 'aria-disabled="true"'}>
          <b>${esc(s.id)} ${esc(s.title)}</b><small>${s.cleared ? '✓ 클리어' : s.open ? (s.boss ? '보스' : '도전 가능') : '잠김'}</small></button>`).join('')}</div>
        ${ch.intro?.length ? '<button class="btn ghost" type="button" data-intro>장 이야기 보기</button>' : ''}</div>
      <div class="pane detail-pane" aria-live="polite"><div data-detail></div><div class="cta-row"><button class="btn ghost lg" type="button" data-quick>바로 시작</button><button class="btn primary lg" type="button" data-go>이야기 보고 시작</button></div><p class="why" data-why role="status"></p></div></section>`;
    $('.chtabs', view).onclick = e => { const b = e.target.closest('[data-ch]'); if (b) { storyCh = Number(b.dataset.ch); storySel = null; story(); } };
    $('.stages', view).onclick = e => { const b = e.target.closest('[data-st]'); if (b) { storySel = b.dataset.st; story(); } };
    const intro = $('[data-intro]', view); if (intro) intro.onclick = () => dialog(ch.intro, ch.title);
    const s = ch.stages.find(x => x.id === storySel);
    const lock = s.lock || {}, reqs = (lock.requirements || []).filter(r => !r.met);
    $('[data-detail]', view).innerHTML = `<h2>${esc(s.id)} ${esc(s.title)}</h2>${s.boss ? '<p class="sub"><b class="boss">보스 전투</b></p>' : ''}
      <ul class="foes">${s.enemy.map(e => `<li><img src="${esc(S.sprite(st.portraits?.[e.id] || ''))}" alt="" width="64" height="64" onerror="this.style.visibility='hidden'"><span><b>${esc(st.names?.[e.id] || e.id)}</b><small>Lv ${fmt(e.level)} · ${esc(st.typeChart?.[e.attrCode]?.name || st.attributes?.[e.attrCode]?.name || '')} · ${esc(S.ROLE[e.role] || '')}</small></span></li>`).join('')}</ul>
      ${s.hint ? `<p class="hint-box">${esc(s.hint)}</p>` : ''}
      ${s.open ? '' : `<div class="locked"><b>아직 잠겨 있어요</b><ul>${reqs.map(r => `<li>${esc(r.label)}${r.action === 'team' ? ' <a href="#team">편성</a>' : ''}</li>`).join('') || `<li>${esc(lock.reason || '앞 단계를 먼저 깨요')}</li>`}</ul></div>`}`;
    const go = $('[data-go]', view), quick = $('[data-quick]', view), why = $('[data-why]', view);
    const low = st.team.map(k => st.units.find(u => u.key === k)).filter(u => u && u.hp <= 0);
    go.disabled = quick.disabled = !s.open || !!low.length;
    why.innerHTML = !s.open ? '' : low.length ? `체력이 0인 대원이 있어요: ${low.map(u => esc(u.name)).join(', ')} · <a href="#team?u=${encodeURIComponent(low[0].key)}">회복하기</a>` : '';
    const start = async btn => {
      go.disabled = quick.disabled = true; btn.setAttribute('aria-busy', 'true');
      try { S.battle = { kind: 'native', snap: await S.api('native/battle/start', { mode: 'story', stage: s.id }), at: Date.now(), stage: s.id }; S.state.activeBattle = true; location.hash = '#battle'; }
      catch (x) { why.textContent = x.message; go.disabled = quick.disabled = false; btn.removeAttribute('aria-busy'); }
    };
    go.onclick = () => dialog(s.before, `${s.id} ${s.title}`, () => start(go));
    quick.onclick = () => start(quick);
  }

  // ── 성장: 기술 배우기 · 승진 전용 기술 · 승진(표창 고르기). 결과는 서버 응답의 unit으로 바꾼다 ──
  const SLOTS = 4;
  const skillSub = k => esc([k.power ? `위력 ${k.power}` : '', k.summary || k.desc].filter(Boolean).join(' · '));
  function growth(key, after) {
    const u = S.state.units.find(x => x.key === key); if (!u) return;
    const own = (u.skills || []).filter(k => !String(k.id).startsWith('combo-')), full = own.length >= SLOTS;
    const tabs = [u.learn?.length && 'learn', u.promo?.length && 'promo', u.evolve?.length && 'evolve'].filter(Boolean);
    let kind = tabs[0], pick = null, replace = null;
    S.sheet(`${u.name} 성장`, '<div data-g></div>', body => {
      const draw = () => {
        const list = kind === 'learn' ? u.learn : kind === 'promo' ? u.promo : u.evolve;
        body.innerHTML = `<div class="filters" role="tablist">${tabs.map(t => `<button type="button" role="tab" data-k="${t}" aria-selected="${t === kind}">${{ learn: '기술 배우기', promo: '승진 기술', evolve: '승진' }[t]}</button>`).join('')}</div>
          ${!tabs.length ? `<p class="muted">지금은 할 수 있는 성장이 없어요.${u.nextLearnLevel ? ` 다음 기술은 Lv ${fmt(u.nextLearnLevel)}에 열려요.` : ''}${u.promotionNeed?.level ? ` 승진은 Lv ${fmt(u.promotionNeed.level)} · 스토리 ${fmt(u.promotionNeed.needParts)}부 클리어가 필요해요.` : ''}</p>` : ''}
          ${kind === 'learn' ? `<p class="sub">이번 기회에 ${fmt(u.learnPerChance)}개까지 배울 수 있어요.</p>` : ''}
          ${kind === 'evolve' ? `<p class="sub">${S.STAGE[u.stage]} → ${S.STAGE[u.stage + 1]}. 표창 하나를 골라요. 승진하면 체력이 가득 차요.</p>` : ''}
          <div class="list">${(list || []).map((x, i) => `<button type="button" class="cbtn skill" data-p="${i}" aria-pressed="${pick === i}"><b>${esc(x.name)}</b><small>${kind === 'evolve' ? esc(x.desc || '') : skillSub(x)}</small></button>`).join('')}</div>
          ${pick !== null && kind !== 'evolve' && full ? `<p class="sub">칸이 꽉 찼어요. 잊을 기술을 골라요.</p><div class="list">${own.map(k => `<button type="button" class="cbtn" data-r="${esc(k.id)}" aria-pressed="${replace === k.id}"><b>${esc(k.name)}</b></button>`).join('')}</div>` : ''}
          ${pick !== null && kind === 'evolve' && list[pick]?.preview ? `<p class="sub">체력 ${fmt(list[pick].preview.beforeStats.hp)} → ${fmt(list[pick].preview.afterStats.hp)} · 물리공격 ${fmt(list[pick].preview.beforeStats.patk)} → ${fmt(list[pick].preview.afterStats.patk)}</p>` : ''}
          <div class="row">${kind === 'learn' ? '<button class="btn ghost" type="button" data-stop>이번엔 그만 배우기</button>' : ''}${tabs.length ? `<button class="btn primary" type="button" data-ok ${pick === null || (kind !== 'evolve' && full && !replace) ? 'disabled' : ''}>${kind === 'evolve' ? '승진하기' : '배우기'}</button>` : ''}</div><p class="err" role="alert"></p>`;
        $$('[data-k]', body).forEach(b => { b.onclick = () => { kind = b.dataset.k; pick = replace = null; draw(); }; });
        $$('[data-p]', body).forEach(b => { b.onclick = () => { pick = Number(b.dataset.p); draw(); }; });
        $$('[data-r]', body).forEach(b => { b.onclick = () => { replace = b.dataset.r; draw(); }; });
        const send = async (payload, btn) => {
          btn.disabled = true; btn.setAttribute('aria-busy', 'true');
          try {
            const r = await S.api('native/growth', { key, ...payload });
            const i = S.state.units.findIndex(x => x.key === key); if (i >= 0 && r.unit) S.state.units[i] = r.unit;
            S.toast(payload.kind === 'evolve' ? '승진했어요!' : payload.skill ? '기술을 배웠어요.' : '이번 배울 기회를 마쳤어요.');
            $('#sheet').hidden = true; after && after(); S.loadState().catch(() => {});
          } catch (x) { $('.err', body).textContent = x.message; btn.disabled = false; btn.removeAttribute('aria-busy'); }
        };
        const ok = $('[data-ok]', body); if (ok) ok.onclick = () => {
          const x = list[pick];
          send(kind === 'evolve' ? { kind, augment: x.id } : { kind, skill: x.id, ...(full ? { replace } : {}) }, ok);
        };
        const stop = $('[data-stop]', body); if (stop) stop.onclick = () => send({ kind: 'learn', skill: null }, stop);
      };
      draw();
    });
  }
  S.growth = growth;

  // ── 스토리 체력 회복: 치료약 종류(회복량은 서버 설정)와 가진 수. 회복은 서버 응답 뒤 상태를 다시 받아 반영 ──
  function heal(key, after) {
    const u = S.state.units.find(x => x.key === key); if (!u) return;
    const defs = S.state.itemDefs || {}, inv = new Map((S.state.items || []).map(x => [x.id, x.count]));
    const items = Object.entries(defs).filter(([, d]) => d.heal).map(([id, d]) => ({ id, ...d, count: inv.get(id) || 0 }));
    S.sheet(`${u.name} 회복`, `<p class="sub">스토리 체력 ${fmt(u.hp)} / ${fmt(u.maxHp)} · 시간이 지나면 저절로 차기도 해요.</p>
      <div class="list">${items.map(d => `<button type="button" class="cbtn" data-h="${esc(d.id)}" ${d.count ? '' : 'disabled'}><b>${esc(d.name)}</b><small>${esc(d.desc || '')} · ${fmt(d.count)}개</small></button>`).join('')}</div>
      <p class="sub"><a href="#shop">상점에서 사기</a></p><p class="err" role="alert"></p>`, body => {
      $$('[data-h]', body).forEach(b => { b.onclick = async () => {
        $$('[data-h]', body).forEach(x => { x.disabled = true; }); b.setAttribute('aria-busy', 'true');
        try { await S.api('native/heal', { key, item: b.dataset.h }); await S.loadState(); S.toast('체력을 회복했어요.'); $('#sheet').hidden = true; after && after(); }
        catch (x) { $('.err', body).textContent = x.message; b.removeAttribute('aria-busy'); $$('[data-h]', body).forEach(y => { y.disabled = !items.find(d => d.id === y.dataset.h)?.count; }); }
      }; });
    });
  }
  S.heal = heal;

  Object.assign(S.routes, { story });
})();
