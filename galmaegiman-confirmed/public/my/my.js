// 내 갈매미(/my): 로그인(카톡에서 받은 6자리 코드) → 갈매미 목록·전투 편성·탐험 편성·아이템·상점
(() => {
  const app = document.getElementById('app'), toastEl = document.getElementById('toast');
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const api = async (path, body) => {
    const r = await fetch(`/my/api/${path}`, body === undefined ? { headers: { 'x-gmm': '1' } } : { method: 'POST', headers: { 'content-type': 'application/json', 'x-gmm': '1' }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { auth = j; loginScreen(); throw new Error('login'); }
    if (!r.ok) throw new Error(j.error || `오류(${r.status})`);
    return j;
  };
  const toast = t => { toastEl.textContent = t; toastEl.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => toastEl.classList.remove('on'), 1800); };
  const thumb = id => `/thumbs/${encodeURIComponent(id)}.jpg`;
  const head = right => `<header><h1>🪨 내 갈매미</h1><span class="sp"></span>${right ?? ''}<a class="home" href="/">홈</a></header>`;

  // ── 로그인 ──
  // 카카오 로그인에서 돌아온 결과(?kakao=…)는 한 번 보여 주고 주소에서 지움
  let auth = {};
  const kq = new URLSearchParams(location.search).get('kakao');
  if (kq) history.replaceState(null, '', '/my');
  const kmsg = { cancel: '카카오 로그인을 취소했어요.', fail: '카카오 로그인에 실패했어요. 잠시 뒤 다시 해 주세요.', taken: '이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.', linked: '카카오 계정을 연결했어요! 다음부터는 카카오로 로그인만 누르면 돼요.' }[kq] ?? '';
  const kakaoBtn = '<a class="btn wide kakao" href="/my/kakao">카카오로 로그인</a>';
  function loginScreen(msg = kmsg) {
    const linking = auth.linking || kq === 'link';
    if (linking) msg = '';
    app.innerHTML = `${head()}<div class="card login">
      <h2 style="font-weight:400;margin:4px 0 6px">${linking ? '카카오 계정 처음 연결' : '로그인'}</h2>
      ${linking ? '<p class="note">처음 한 번만 카톡 코드로 내 갈매미맨 계정을 알려 주세요. 그러면 이 카카오 계정이 연결돼서, 다음부터는 카카오로 로그인만 누르면 돼요.</p>'
        : auth.kakao ? `${kakaoBtn}<p class="note" style="margin:10px 0 4px">또는 카톡 코드로 로그인</p>` : ''}
      <ol class="steps"><li>카톡 갈매미맨 채널에 <b>웹 로그인</b>이라고 보내요.</li><li>받은 6자리 코드를 아래에 입력해요. (10분 안에, 한 번만)</li></ol>
      <input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000">
      <p class="err" id="err">${esc(msg)}</p>
      <button class="btn wide" id="go">로그인</button>
      <p class="note" style="margin-top:12px">로그인은 이 기기에서 30일 동안 유지돼요.</p></div>`;
    const go = async () => {
      const code = app.querySelector('#code').value.replace(/\D/g, '');
      if (code.length !== 6) { app.querySelector('#err').textContent = '6자리 숫자를 입력해 주세요.'; return; }
      app.querySelector('#go').disabled = true;
      try { const r = await api('login', { code }); if (r.kakao) toast(r.kakao === 'linked' ? '카카오 계정을 연결했어요!' : r.kakao); main(); }
      catch (e) { app.querySelector('#err').textContent = e.message; app.querySelector('#go').disabled = false; }
    };
    app.querySelector('#go').onclick = go;
    app.querySelector('#code').onkeydown = e => { if (e.key === 'Enter') go(); };
    app.querySelector('#code').focus();
  }

  // ── 내 갈매미 ──
  let me, tab = 'units', filter = 'all';
  async function main() {
    try { me = await api('me'); if (kmsg && !main.shown) { main.shown = 1; toast(kmsg); } } catch (e) { if (e.message !== 'login') app.innerHTML = `${head()}<p class="empty">${esc(e.message)}</p>`; return; }
    draw();
  }
  function draw() {
    const tabs = [['units', '🪨 갈매미'], ['craft', '🧩 조합'], ['battle', '⚔️ 전투 편성'], ['party', '🧭 탐험 편성'], ['items', '🎒 아이템'], ['shop', '🛒 상점']];
    app.innerHTML = `${head('<button class="btn ghost" id="out" style="padding:6px 12px;font-size:.85rem">로그아웃</button>')}
      <div class="card"><div class="row" style="margin-bottom:10px"><b style="font-weight:400;font-size:1.15rem">${esc(me.nickname || '이름 없는 수비조장')}</b><span class="tag">도감 ${me.dex}/${me.dexTotal}</span></div>
        <div class="money"><div><b>🍤 ${me.snack}</b><small>새우깡</small></div><div><b>🥜 ${me.peanut}</b><small>땅콩</small></div><div><b>🎫 ${me.credits}</b><small>뽑기권</small></div><div><b>${me.units.reduce((a, u) => a + u.have, 0)}</b><small>가진 갈매미</small></div></div></div>
      <div class="tabs">${tabs.map(([k, t]) => `<button data-t="${k}" class="${tab === k ? 'on' : ''}">${t}</button>`).join('')}</div>
      <div id="body"></div>`;
    app.querySelector('#out').onclick = async () => { await api('logout', {}).catch(() => {}); loginScreen('로그아웃했어요.'); };
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; if (tab === 'craft') craftData = null; draw(); });
    ({ units, craft, battle, party, items, shop })[tab](app.querySelector('#body'));
  }

  // 갈매미 목록: 등급별로 걸러 보기
  function units(el) {
    const groups = [['all', '전체'], ['top', '최상위'], ['LEGEND', '전설'], ['HIDDEN', '히든'], ['low', '그 밖']];
    const pass = u => filter === 'all' || (filter === 'top' ? u.top : filter === 'low' ? !u.top && !['LEGEND', 'HIDDEN'].includes(u.tier) : u.tier === filter);
    const list = me.units.filter(pass);
    el.innerHTML = `<div class="filters">${groups.map(([k, t]) => `<button data-f="${k}" class="${filter === k ? 'on' : ''}">${t}</button>`).join('')}</div>
      ${list.length ? `<div class="grid">${list.map(u => `<div class="u"><img loading="lazy" src="${thumb(u.id)}" alt=""><span class="n">×${u.have}</span>${u.away ? `<span class="away">탐험 ${u.away}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></div>`).join('')}</div>`
        : '<p class="empty">이 등급의 갈매미가 아직 없어요.</p>'}
      ${me.kakaoLogin ? `<div class="card row" style="justify-content:space-between;margin-top:14px"><span class="note">${me.kakao ? '✅ 카카오 계정 연결됨 (카카오로 로그인 가능)' : '카카오 계정을 연결하면 다음부터 코드 없이 로그인해요.'}</span>
        ${me.kakao ? '<button class="btn ghost" id="unlink" style="padding:6px 12px;font-size:.85rem">연결 해제</button>' : '<a class="btn kakao" href="/my/kakao" style="padding:8px 14px;font-size:.9rem">카카오 연결</a>'}</div>` : ''}`;
    el.querySelectorAll('[data-f]').forEach(b => b.onclick = () => { filter = b.dataset.f; draw(); });
    el.querySelector('#unlink')?.addEventListener('click', () => run('kakao/unlink', {}, '카카오 계정 연결을 끊을까요? 그다음엔 카톡 코드로 로그인해요.'));
  }

  // 전투 편성: 최상위 3마리(순서 = 출전 순서)
  function battle(el) {
    let pick = [...me.battleTeam];
    const tops = me.units.filter(u => u.top);
    const render = () => {
      el.innerHTML = `<div class="card"><p class="note" style="margin-top:0">수비대 대결에 나갈 <b>최상위 갈매미 3마리</b>를 골라요. 처음 고른 갈매미가 먼저 나가요.</p>
        <div class="slots">${[0, 1, 2].map(i => { const u = tops.find(x => x.id === pick[i]); return u ? `<div class="slot full"><img src="${thumb(u.id)}" alt=""><span>${i + 1}. ${esc(u.name)}</span></div>` : `<div class="slot">${i + 1}번 자리<br>비어 있음</div>`; }).join('')}</div>
        <button class="btn wide" id="save" ${JSON.stringify(pick) === JSON.stringify(me.battleTeam) ? 'disabled' : ''}>편성 저장</button></div>
        ${tops.length ? `<div class="grid">${tops.map(u => { const i = pick.indexOf(u.id); return `<button class="u ${i >= 0 ? 'on' : ''}" data-id="${u.id}"><img loading="lazy" src="${thumb(u.id)}" alt="">${i >= 0 ? `<span class="ord">${i + 1}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></button>`; }).join('')}</div>`
          : '<p class="empty">아직 최상위 갈매미가 없어요. 카톡에서 조합해 깨워 주세요.</p>'}`;
      el.querySelectorAll('[data-id]').forEach(b => b.onclick = () => {
        const id = b.dataset.id, i = pick.indexOf(id);
        if (i >= 0) pick.splice(i, 1); else if (pick.length < 3) pick.push(id); else toast('3마리까지예요. 먼저 하나를 빼 주세요.');
        render();
      });
      el.querySelector('#save').onclick = async () => {
        try { const r = await api('battle-team', { ids: pick }); me.battleTeam = r.battleTeam; toast('전투 편성을 저장했어요'); render(); }
        catch (e) { if (e.message !== 'login') toast(e.message); }
      };
    };
    render();
  }

  // 실행 → 결과 알림 → 다시 불러오기
  const run = async (path, body, confirmText) => {
    if (confirmText && !confirm(confirmText)) return;
    try { const r = await api(path, body ?? {}); const t = typeof r.result === 'string' ? r.result : ''; toast(t.split('\n')[0] || '완료!'); me = await api('me'); draw(); }
    catch (e) { if (e.message !== 'login') toast(e.message.split('\n')[0]); }
  };

  // 조합: 카톡 「조합」과 같은 처리. 만들 수 있는 것 / 전체 조합식(등급·이름 찾기) / 재료로 섞기(숨은 조합 찾기)
  let craftView = 'ready', craftGrade = 'all', craftQ = '', mixPick = [], craftData = null;
  const cimg = img => `/thumbs/${encodeURIComponent(img)}.jpg`;
  // 조합 결과(카톡 답장과 같은 글)를 창으로 보여 줌
  const showResult = text => {
    const box = document.createElement('div'); box.className = 'result';
    box.innerHTML = `<div class="card"><p class="pre">${esc(text)}</p><button class="btn wide" id="rok">확인</button></div>`;
    document.body.append(box); box.querySelector('#rok').onclick = () => box.remove(); box.onclick = e => { if (e.target === box) box.remove(); };
  };
  async function craft(el) {
    if (!craftData) { el.innerHTML = '<p class="empty">조합식을 불러오는 중…</p>'; try { craftData = await api('craft'); } catch (e) { if (e.message !== 'login') el.innerHTML = `<p class="empty">${esc(e.message)}</p>`; return; } }
    const { recipes, pool } = craftData, ready = recipes.filter(r => r.ready);
    const seg = [['ready', `✨ 만들 수 있는 것 ${ready.length}`], ['all', '📜 전체 조합식'], ['mix', '🔮 재료로 섞기']];
    const head = `<div class="filters">${seg.map(([k, t]) => `<button data-v="${k}" class="${craftView === k ? 'on' : ''}">${t}</button>`).join('')}</div>`;
    const card = r => `<div class="rc ${r.ready ? 'ok' : ''}"><div class="rh"><img loading="lazy" src="${cimg(r.img)}" alt=""><div><b>${esc(r.name)}</b>${r.isNew ? ' <span class="tag">🆕 처음</span>' : ''}<small>${esc(r.tierName)}</small></div></div>
      <ul>${r.materials.map(m => `<li class="${m.usable >= m.need ? 'have' : ''}">${m.usable >= m.need ? '✅' : '⬜'} ${esc(m.name)} <b>${Math.min(m.usable, m.need)}/${m.need}</b>${m.locked || m.away ? ` <small>(${[m.locked ? `보호 ${m.locked}` : '', m.away ? `탐험 ${m.away}` : ''].filter(Boolean).join('·')})</small>` : ''}</li>`).join('')}</ul>
      ${r.ready ? `<div class="row"><button class="btn" data-c="${r.id}" data-n="1">만들기</button>${r.max > 1 ? `<button class="btn ghost" data-c="${r.id}" data-n="${r.max}">${r.max}개 모두</button>` : ''}</div>` : ''}</div>`;
    let body = '';
    if (craftView === 'ready') body = ready.length ? `<div class="rgrid">${ready.map(card).join('')}</div>` : '<p class="empty">지금 만들 수 있는 게 없어요. 「전체 조합식」에서 모자란 재료를 확인해 보세요.</p>';
    if (craftView === 'all') {
      const grades = [...new Map(recipes.map(r => [r.tier, r.tierName])).entries()];
      const list = recipes.filter(r => (craftGrade === 'all' || r.tier === craftGrade) && (!craftQ || r.name.includes(craftQ) || r.materials.some(m => m.name.includes(craftQ))));
      body = `<div class="row" style="margin-bottom:8px"><input id="cq" class="search" placeholder="이름으로 찾기 (결과·재료)" value="${esc(craftQ)}"></div>
        <div class="filters"><button data-g="all" class="${craftGrade === 'all' ? 'on' : ''}">전체</button>${grades.map(([k, t]) => `<button data-g="${k}" class="${craftGrade === k ? 'on' : ''}">${esc(t)}</button>`).join('')}</div>
        ${list.length ? `<div class="rgrid">${list.map(card).join('')}</div>` : '<p class="empty">찾는 조합식이 없어요.</p>'}`;
    }
    if (craftView === 'mix') {
      const left = id => (pool.find(u => u.id === id)?.usable ?? 0) - mixPick.filter(x => x === id).length;
      const nm = id => pool.find(u => u.id === id)?.name ?? id;
      body = `<div class="card"><b style="font-weight:400">🔮 재료 ${mixPick.length}/6</b>
        <p class="note">${mixPick.length ? mixPick.map(id => esc(nm(id))).join(' + ') : '아래에서 재료를 2~6마리 골라 섞어요. 조합목록에 없는 숨은 조합도 이렇게 찾아요. 아무것도 안 나오면 재료는 그대로예요.'}</p>
        <div class="row"><button class="btn ghost" id="mclr" ${mixPick.length ? '' : 'disabled'}>비우기</button><button class="btn" id="mgo" ${mixPick.length >= 2 ? '' : 'disabled'}>🔮 섞기</button></div></div>
        ${pool.length ? `<div class="grid">${pool.map(u => { const n = mixPick.filter(x => x === u.id).length; return `<div class="u ${n ? 'on' : ''}"><img loading="lazy" src="${cimg(u.img)}" alt="">${n ? `<span class="ord">${n}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)} · 남은 ${left(u.id)}</small>
          <div class="row" style="justify-content:center;margin-top:4px"><button class="btn ghost" data-mm="${u.id}" style="padding:2px 10px" ${n ? '' : 'disabled'}>－</button><button class="btn ghost" data-mp="${u.id}" style="padding:2px 10px" ${left(u.id) > 0 && mixPick.length < 6 ? '' : 'disabled'}>＋</button></div></div>`; }).join('')}</div>` : '<p class="empty">재료로 쓸 갈매미가 없어요.</p>'}`;
    }
    el.innerHTML = head + body;
    el.querySelectorAll('[data-v]').forEach(b => b.onclick = () => { craftView = b.dataset.v; craft(el); });
    el.querySelectorAll('[data-g]').forEach(b => b.onclick = () => { craftGrade = b.dataset.g; craft(el); });
    const q = el.querySelector('#cq'); if (q) { q.oninput = () => { craftQ = q.value.trim(); clearTimeout(q.t); q.t = setTimeout(() => { craft(el); const n = el.querySelector('#cq'); n.focus(); n.setSelectionRange(n.value.length, n.value.length); }, 250); }; }
    const done = async text => { showResult(text); craftData = null; mixPick = []; try { me = await api('me'); } catch { /* 무시 */ } draw(); };
    el.querySelectorAll('[data-c]').forEach(b => b.onclick = async () => {
      const r = recipes.find(x => x.id === b.dataset.c), n = +b.dataset.n;
      if ((r.confirm || n > 1) && !confirm(`재료 ${r.materials.reduce((a, m) => a + m.need, 0) * n}마리를 써서 ${r.name}${n > 1 ? ` ${n}개` : ''}를 만들까요?`)) return;
      b.disabled = true;
      try { done((await api('craft', { id: r.id, times: n })).result); } catch (e) { if (e.message !== 'login') { toast(e.message.split('\n')[0]); b.disabled = false; } }
    });
    el.querySelectorAll('[data-mp]').forEach(b => b.onclick = () => { mixPick.push(b.dataset.mp); craft(el); });
    el.querySelectorAll('[data-mm]').forEach(b => b.onclick = () => { mixPick.splice(mixPick.lastIndexOf(b.dataset.mm), 1); craft(el); });
    el.querySelector('#mclr')?.addEventListener('click', () => { mixPick = []; craft(el); });
    el.querySelector('#mgo')?.addEventListener('click', async () => {
      if (!confirm(`${mixPick.length}마리를 섞어 볼까요? 맞는 조합이면 재료를 쓰고, 아니면 재료는 그대로예요.`)) return;
      try { const r = (await api('mix', { ids: mixPick })).result; if (r.made) done(r.text); else { showResult(r.text); } } catch (e) { if (e.message !== 'login') toast(e.message.split('\n')[0]); }
    });
  }

  // 탐험 편성: 카톡 「탐험」과 같은 편성·같은 규칙(최상위만, 같은 유닛 여러 마리 가능)
  function party(el) {
    const name = id => me.units.find(u => u.id === id)?.name ?? id;
    if (me.trip) {
      const left = Math.max(0, new Date(me.trip.endsAt) - Date.now());
      el.innerHTML = `<div class="card"><b style="font-weight:400">🧭 탐험 중</b><p class="note">${me.trip.units.map(esc).join(', ')}</p>
        ${left ? `<p>남은 시간 약 <b>${Math.ceil(left / 60000)}분</b></p><button class="btn ghost wide" id="skip">⚡ 바로 돌아오게 하기 (🍤 ${me.prices.skip.snack})</button>`
          : '<p>돌아왔어요!</p><button class="btn wide" id="claim">🎁 탐험 보상 받기</button>'}</div>`;
      el.querySelector('#claim')?.addEventListener('click', () => run('expedition/claim'));
      el.querySelector('#skip')?.addEventListener('click', () => run('shop', { item: 'skip' }, `새우깡 ${me.prices.skip.snack}개로 탐험대를 바로 돌아오게 할까요?`));
      return;
    }
    let draft = [...me.party];
    const tops = me.units.filter(u => u.top && u.have - u.away > 0), max = me.prices.maxParty;
    const render = () => {
      const cnt = id => draft.filter(x => x === id).length;
      const syn = {}; for (const id of draft) { const s = me.units.find(u => u.id === id)?.synergy; if (s) syn[s] = (syn[s] ?? 0) + 1; }
      const changed = JSON.stringify(draft) !== JSON.stringify(me.party);
      el.innerHTML = `<div class="card"><b style="font-weight:400">🧭 탐험대 ${draft.length}/${max}</b>
        <p class="note">${draft.length ? [...new Set(draft)].map(id => `${esc(name(id))}${cnt(id) > 1 ? ` ×${cnt(id)}` : ''}`).join(', ') : '아래에서 ＋로 넣어요. 같은 시너지끼리 보내면 보너스!'}</p>
        ${Object.keys(syn).length ? `<p class="note">시너지: ${Object.entries(syn).map(([k, n]) => `${k} ${n}마리`).join(' · ')}</p>` : ''}
        <div class="row"><button class="btn ghost" id="clear" ${draft.length ? '' : 'disabled'}>비우기</button><button class="btn ghost" id="save" ${changed ? '' : 'disabled'}>편성 저장</button>
        <button class="btn" id="go" ${draft.length && !changed ? '' : 'disabled'}>🚀 출발</button></div>${changed ? '<p class="note">출발하려면 먼저 편성 저장을 눌러요.</p>' : ''}</div>
        ${tops.length ? `<div class="grid">${tops.map(u => { const n = cnt(u.id), free = u.have - u.away; return `<div class="u ${n ? 'on' : ''}"><img loading="lazy" src="${thumb(u.id)}" alt="">${n ? `<span class="ord">${n}</span>` : ''}<b>${esc(u.name)}</b><small>남은 ${free - n}마리</small>
            <div class="row" style="justify-content:center;margin-top:4px"><button class="btn ghost" data-m="${u.id}" style="padding:2px 10px" ${n ? '' : 'disabled'}>－</button><button class="btn ghost" data-p="${u.id}" style="padding:2px 10px" ${n < free && draft.length < max ? '' : 'disabled'}>＋</button></div></div>`; }).join('')}</div>`
          : '<p class="empty">보낼 수 있는 최상위 갈매미가 없어요.</p>'}`;
      el.querySelectorAll('[data-p]').forEach(b => b.onclick = () => { draft.push(b.dataset.p); render(); });
      el.querySelectorAll('[data-m]').forEach(b => b.onclick = () => { draft.splice(draft.lastIndexOf(b.dataset.m), 1); render(); });
      el.querySelector('#clear').onclick = () => { draft = []; render(); };
      el.querySelector('#save').onclick = () => run('party', { ids: draft });
      el.querySelector('#go').onclick = () => run('expedition/start', {}, `탐험대 ${draft.length}마리를 출발시킬까요? 돌아올 때까지 다른 데 못 써요.`);
    };
    render();
  }

  // 아이템: 가진 대결 아이템
  function items(el) {
    const mine = id => me.items.find(i => i.id === id)?.count ?? 0;
    el.innerHTML = `<div class="card"><b style="font-weight:400">🎒 대결 아이템</b><p class="note">수비대 대결 스토리에서 「🎒 아이템」으로 써요. 쓰면 그 턴 행동을 쓰고, 대결 한 번에 ${me.itemRules.perBattle}개까지예요.</p></div>
      ${me.itemDefs.map(d => `<div class="card row" style="justify-content:space-between"><div><b style="font-weight:400">${esc(d.name)}</b> <span class="tag">${mine(d.id)}개</span><p class="note" style="margin:2px 0 0">${esc(d.desc)}</p></div></div>`).join('')}`;
  }

  // 상점: 카톡 상점과 같은 물건 + 대결 아이템
  function shop(el) {
    const p = me.prices, mine = id => me.items.find(i => i.id === id)?.count ?? 0;
    el.innerHTML = `<div class="card"><b style="font-weight:400">🛒 상점</b><p class="note">지금 🍤 ${me.snack} · 🥜 ${me.peanut}</p></div>
      <div class="card row" style="justify-content:space-between"><div>🎫 뽑기권 ${p.tickets.amount}장<p class="note" style="margin:0">🍤 ${p.tickets.snack}</p></div><button class="btn" id="t1" ${me.snack >= p.tickets.snack ? '' : 'disabled'}>사기</button></div>
      <div class="card row" style="justify-content:space-between"><div>🥜 → 🎫 뽑기권 ${p.peanutTickets.amount}장<p class="note" style="margin:0">땅콩 1개</p></div><button class="btn" id="t2" ${me.peanut >= 1 ? '' : 'disabled'}>바꾸기</button></div>
      <div class="card"><b style="font-weight:400">🎒 대결 아이템</b></div>
      ${me.itemDefs.map(d => `<div class="card row" style="justify-content:space-between"><div>${esc(d.name)} <span class="tag">가진 ${mine(d.id)}개</span><p class="note" style="margin:0">${esc(d.desc)}<br>🍤 ${d.price}</p></div>
        <div class="row"><button class="btn ghost" data-buy="${d.id}" data-n="1" ${me.snack >= d.price ? '' : 'disabled'}>1개</button><button class="btn ghost" data-buy="${d.id}" data-n="5" ${me.snack >= d.price * 5 ? '' : 'disabled'}>5개</button></div></div>`).join('')}
      <p class="note">갈매미 교환·팔기·닉네임 장식은 카톡 「상점」에서 할 수 있어요.</p>`;
    el.querySelector('#t1').onclick = () => run('shop', { item: 'tickets' }, `새우깡 ${p.tickets.snack}개로 뽑기권 ${p.tickets.amount}장을 살까요?`);
    el.querySelector('#t2').onclick = () => run('shop', { item: 'peanutTickets' }, `땅콩 1개로 뽑기권 ${p.peanutTickets.amount}장을 바꿀까요?`);
    el.querySelectorAll('[data-buy]').forEach(b => b.onclick = () => { const d = me.itemDefs.find(x => x.id === b.dataset.buy), n = +b.dataset.n; run('items/buy', { id: d.id, count: n }, `${d.name} ${n}개를 새우깡 ${d.price * n}개로 살까요?`); });
  }

  main();
})();
