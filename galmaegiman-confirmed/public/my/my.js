// 내 갈매미(/my): 로그인(카톡에서 받은 6자리 코드) → 갈매미 목록·전투 편성·탐험 편성·아이템·상점
(() => {
  const app = document.getElementById('app'), toastEl = document.getElementById('toast');
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const api = async (path, body) => {
    const r = await fetch(`/my/api/${path}`, body === undefined ? { headers: { 'x-gmm': '1' } } : { method: 'POST', headers: { 'content-type': 'application/json', 'x-gmm': '1' }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { loginScreen(); throw new Error('login'); }
    if (!r.ok) throw new Error(j.error || `오류(${r.status})`);
    return j;
  };
  const toast = t => { toastEl.textContent = t; toastEl.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => toastEl.classList.remove('on'), 1800); };
  const thumb = id => `/thumbs/${encodeURIComponent(id)}.jpg`;
  const head = right => `<header><h1>🪨 내 갈매미</h1><span class="sp"></span>${right ?? ''}<a class="home" href="/">홈</a></header>`;

  // ── 로그인 ──
  function loginScreen(msg = '') {
    app.innerHTML = `${head()}<div class="card login">
      <h2 style="font-weight:400;margin:4px 0 6px">로그인</h2>
      <ol class="steps"><li>카톡 갈매미맨 채널에 <b>웹 로그인</b>이라고 보내요.</li><li>받은 6자리 코드를 아래에 입력해요. (10분 안에, 한 번만)</li></ol>
      <input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000">
      <p class="err" id="err">${esc(msg)}</p>
      <button class="btn wide" id="go">로그인</button>
      <p class="note" style="margin-top:12px">로그인은 이 기기에서 30일 동안 유지돼요.</p></div>`;
    const go = async () => {
      const code = app.querySelector('#code').value.replace(/\D/g, '');
      if (code.length !== 6) { app.querySelector('#err').textContent = '6자리 숫자를 입력해 주세요.'; return; }
      app.querySelector('#go').disabled = true;
      try { await api('login', { code }); main(); }
      catch (e) { app.querySelector('#err').textContent = e.message; app.querySelector('#go').disabled = false; }
    };
    app.querySelector('#go').onclick = go;
    app.querySelector('#code').onkeydown = e => { if (e.key === 'Enter') go(); };
    app.querySelector('#code').focus();
  }

  // ── 내 갈매미 ──
  let me, tab = 'units', filter = 'all';
  async function main() {
    try { me = await api('me'); } catch (e) { if (e.message !== 'login') app.innerHTML = `${head()}<p class="empty">${esc(e.message)}</p>`; return; }
    draw();
  }
  function draw() {
    const tabs = [['units', '🪨 갈매미'], ['battle', '⚔️ 전투 편성'], ['party', '🧭 탐험 편성'], ['items', '🎒 아이템'], ['shop', '🛒 상점']];
    app.innerHTML = `${head('<button class="btn ghost" id="out" style="padding:6px 12px;font-size:.85rem">로그아웃</button>')}
      <div class="card"><div class="row" style="margin-bottom:10px"><b style="font-weight:400;font-size:1.15rem">${esc(me.nickname || '이름 없는 수비조장')}</b><span class="tag">도감 ${me.dex}/${me.dexTotal}</span></div>
        <div class="money"><div><b>🍤 ${me.snack}</b><small>새우깡</small></div><div><b>🥜 ${me.peanut}</b><small>땅콩</small></div><div><b>🎫 ${me.credits}</b><small>뽑기권</small></div><div><b>${me.units.reduce((a, u) => a + u.have, 0)}</b><small>가진 갈매미</small></div></div></div>
      <div class="tabs">${tabs.map(([k, t]) => `<button data-t="${k}" class="${tab === k ? 'on' : ''}">${t}</button>`).join('')}</div>
      <div id="body"></div>`;
    app.querySelector('#out').onclick = async () => { await api('logout', {}).catch(() => {}); loginScreen('로그아웃했어요.'); };
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; draw(); });
    ({ units, battle, party, items, shop })[tab](app.querySelector('#body'));
  }

  // 갈매미 목록: 등급별로 걸러 보기
  function units(el) {
    const groups = [['all', '전체'], ['top', '최상위'], ['LEGEND', '전설'], ['HIDDEN', '히든'], ['low', '그 밖']];
    const pass = u => filter === 'all' || (filter === 'top' ? u.top : filter === 'low' ? !u.top && !['LEGEND', 'HIDDEN'].includes(u.tier) : u.tier === filter);
    const list = me.units.filter(pass);
    el.innerHTML = `<div class="filters">${groups.map(([k, t]) => `<button data-f="${k}" class="${filter === k ? 'on' : ''}">${t}</button>`).join('')}</div>
      ${list.length ? `<div class="grid">${list.map(u => `<div class="u"><img loading="lazy" src="${thumb(u.id)}" alt=""><span class="n">×${u.have}</span>${u.away ? `<span class="away">탐험 ${u.away}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></div>`).join('')}</div>`
        : '<p class="empty">이 등급의 갈매미가 아직 없어요.</p>'}`;
    el.querySelectorAll('[data-f]').forEach(b => b.onclick = () => { filter = b.dataset.f; draw(); });
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

  // 탐험 편성: 카톡 「탐험」과 같은 편성(지금은 보기만)
  function party(el) {
    const name = id => me.units.find(u => u.id === id)?.name ?? id;
    const left = me.trip ? Math.max(0, new Date(me.trip.endsAt) - Date.now()) : 0;
    el.innerHTML = `<div class="card">${me.trip
      ? `<b style="font-weight:400">🧭 탐험 중</b><p class="note">${me.trip.units.map(esc).join(', ')}<br>${left ? `남은 시간 약 ${Math.ceil(left / 60000)}분` : '돌아왔어요! 카톡에서 「탐험」으로 보상을 받아요.'}</p>`
      : '<b style="font-weight:400">🧭 지금 탐험 중인 갈매미가 없어요</b>'}</div>
      <div class="card"><b style="font-weight:400">편성 중인 탐험대</b>
        ${me.party.length ? `<div class="grid" style="margin-top:8px">${me.party.map(id => `<div class="u"><img loading="lazy" src="${thumb(id)}" alt=""><b>${esc(name(id))}</b></div>`).join('')}</div>` : '<p class="note">비어 있어요.</p>'}
        <p class="note">탐험대 편성·출발은 지금은 카톡에서 「탐험」으로 해요. 웹에서 바꾸기는 다음 업데이트에 넣을게요.</p></div>`;
  }

  // 아이템·상점: 준비 중(다음 업데이트)
  function items(el) {
    el.innerHTML = `<div class="card"><b style="font-weight:400">🎒 아이템</b><p class="note">치료약 같은 대결 아이템은 다음 업데이트에 넣을게요. 상점에서 사서 대결 중에 쓰는 방식이에요.</p></div>`;
  }
  function shop(el) {
    el.innerHTML = `<div class="card"><b style="font-weight:400">🛒 상점</b><p class="note">지금 가진 것: 🍤 새우깡 ${me.snack} · 🥜 땅콩 ${me.peanut}<br>웹 상점(뽑기권·아이템 구매)은 다음 업데이트에 넣을게요. 지금은 카톡에서 「상점」으로 살 수 있어요.</p></div>`;
  }

  main();
})();
