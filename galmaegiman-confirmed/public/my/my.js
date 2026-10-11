// 내 갈매미(/my): 로그인(카톡에서 받은 6자리 코드) → 갈매미 목록·전투 편성·탐험 편성·아이템·상점
(() => {
// 게임 안 확인 창(안드로이드·브라우저 기본 팝업 대신): ask(글, {ok, cancel, danger}) → true/false, tell(글) → 확인만
const gdlg = (text, o = {}) => new Promise(res => {
  const w = document.createElement('div'); w.className = 'gdlg';
  w.innerHTML = `<div class="gdlg-box" role="dialog" aria-modal="true"><p></p><div class="gdlg-row">${o.alert ? '' : `<button class="gdlg-no">${o.cancel ?? '취소'}</button>`}<button class="gdlg-ok ${o.danger ? 'danger' : ''}">${o.ok ?? '확인'}</button></div></div>`;
  w.querySelector('p').textContent = text;
  const end = v => { w.remove(); document.removeEventListener('keydown', key); res(v); };
  const key = e => { if (e.key === 'Escape') end(false); if (e.key === 'Enter') end(true); };
  w.querySelector('.gdlg-ok').onclick = () => end(true); w.querySelector('.gdlg-no')?.addEventListener('click', () => end(false));
  w.onclick = e => { if (e.target === w && !o.alert) end(false); };
  document.addEventListener('keydown', key); document.body.appendChild(w); w.querySelector('.gdlg-ok').focus();
});
const ask = (text, o) => gdlg(text, o), tell = text => gdlg(text, { alert: true });
const josaEul = w => { const c = (w || '').charCodeAt(w.length - 1); return c >= 0xac00 && c <= 0xd7a3 && (c - 0xac00) % 28 ? '을' : '를'; };
  const app = document.getElementById('app'), toastEl = document.getElementById('toast');
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  // 안드로이드 앱: 화면은 앱 안에 있고(GMM_BASE = 서버 주소), 로그인은 쿠키 대신 토큰 머리글(x-gmm-s)
  const BASE = window.GMM_BASE || '', APP = window.gmmApp || null;
  const api = async (path, body) => {
    const h = { 'x-gmm': '1', ...(APP ? { 'x-gmm-app': '1', ...(APP.token() ? { 'x-gmm-s': APP.token() } : {}) } : {}) };
    const r = await fetch(`${BASE}/my/api/${path}`, body === undefined ? { headers: h } : { method: 'POST', headers: { 'content-type': 'application/json', ...h }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    if (r.status === 401) { if (APP && path !== 'login') APP.setToken(null); auth = j; loginScreen(); throw new Error('login'); }
    if (!r.ok) throw new Error(j.error || `오류(${r.status})`);
    return j;
  };
  // 게임에서 들어왔으면(앱이거나 /my?g=1) 사이트 머리글 대신 게임 화면 모양: 흐린 항구 배경 + 「‹ 게임으로」
  const GAMEUI = (() => { try { if (APP || new URLSearchParams(location.search).get('g') === '1') sessionStorage.setItem('gmm-g', '1'); return !!APP || sessionStorage.getItem('gmm-g') === '1'; } catch { return !!APP; } })();
  if (GAMEUI) {
    document.documentElement.classList.add('game');
    let k = ''; try { k = sessionStorage.getItem('gmm-k') || ''; } catch { /* 무시 */ }
    const art = APP ? 'title.jpg' : k ? `/duel/bg/key.jpg?k=${encodeURIComponent(k)}` : '';
    if (art) document.documentElement.style.setProperty('--gbg', `url("${art}")`);
  }
  // 게임 안 창(v0.10.1): 게임 로비가 이 화면을 안에 띄우면(iframe) 페이지를 옮기지 않고 창만 닫음
  // 게임 안에서는 아래 메뉴에서 들어온 묶음의 탭만 보여 줌(v0.11.1): 갈매미 = 내 갈매미·전투 편성·아이템(포켓몬의 파티·박스·가방처럼), 나머지는 단독. 다른 메뉴는 로비로 돌아가서
  const GROUP = { units: ['units', 'battle', 'items'], battle: ['units', 'battle', 'items'], items: ['units', 'battle', 'items'] };
  const groupOf = t => GROUP[t] ?? [t];
  const TABNAME = { draw: '뽑기', craft: '조합', units: '내 갈매미', party: '탐험대 편성', battle: '전투 편성', items: '아이템', shop: '상점', account: '계정' };
  const EMBED = (() => { try { return window.parent !== window; } catch { return true; } })();
  if (EMBED) document.documentElement.classList.add('embed');
  const backToGame = () => { if (EMBED) { parent.postMessage({ gmm: 'close' }, '*'); return; } if (APP) location.href = 'index.html'; else { let k = ''; try { k = sessionStorage.getItem('gmm-k') || ''; } catch { /* 무시 */ } location.href = k ? `/duel?k=${encodeURIComponent(k)}` : '/'; } };
  document.addEventListener('click', e => { if (e.target.closest && e.target.closest('#gback')) backToGame(); });
  const toast = t => { toastEl.textContent = t; toastEl.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => toastEl.classList.remove('on'), 1800); };
  const thumb = id => `${BASE}/thumbs/${encodeURIComponent(id)}.jpg`;
  // 게임 모드: 재화는 위쪽 줄 오른쪽에 작게(재화 확인이 화면의 목적처럼 보이지 않게)
  const curBar = m => m ? `<div class="cur">${[['🍤', m.snack, '새우깡'], ['🥜', m.peanut, '땅콩'], ['🎫', m.credits, '뽑기권']].map(([i, n, t]) => `<span title="${t}">${i}<b>${Number(n).toLocaleString('ko-KR')}</b></span>`).join('')}</div>` : '';
  const head = (right, m) => GAMEUI ? `<header class="gbar"><button class="gback" id="gback">‹ ${EMBED ? '로비' : '게임으로'}</button><h1>${GAMEUI && me && TABNAME[tab] ? (groupOf(tab).length > 1 ? '갈매미' : TABNAME[tab]) : '내 갈매미'}</h1><span class="sp"></span>${curBar(m)}</header>` : `<header><a class="logo" href="/"><img src="/images/logo.png" alt="갈매미맨"></a><h1>내 갈매미</h1><span class="sp"></span><a class="hl" href="/dex" title="도감">📚<span class="lt"> 도감</span></a><a class="hl" href="/search" title="검색">🔍</a>${right ?? ''}<a class="home" href="/" title="홈">🏠<span class="lt"> 홈</span></a></header>`;

  // ── 로그인 ──
  // 카카오 로그인에서 돌아온 결과(?kakao=…)는 한 번 보여 주고 주소에서 지움
  let auth = {};
  const kq = new URLSearchParams(location.search).get('kakao');
  if (kq) history.replaceState(null, '', '/my');
  const kmsg = { cancel: '카카오 로그인을 취소했어요.', fail: '카카오 로그인에 실패했어요. 잠시 뒤 다시 해 주세요.', taken: '이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.', linked: '카카오 계정을 연결했어요! 다음부터는 카카오로 로그인만 누르면 돼요.' }[kq] ?? '';
  const kakaoBtn = '<a class="btn wide kakao" href="/my/kakao">카카오로 로그인</a>';
  function loginScreen(msg = kmsg) {
    app.classList.remove('gv');
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
      try { const r = await api('login', { code }); if (APP && r.token) APP.setToken(r.token); if (APP && new URLSearchParams(location.search).get('next') === 'game') { location.href = 'index.html'; return; } if (r.kakao) toast(r.kakao === 'linked' ? '카카오 계정을 연결했어요!' : r.kakao); main(); }
      catch (e) { app.querySelector('#err').textContent = e.message; app.querySelector('#go').disabled = false; }
    };
    app.querySelector('#go').onclick = go;
    app.querySelector('#code').onkeydown = e => { if (e.key === 'Enter') go(); };
    app.querySelector('#code').focus();
  }

  // ── 내 갈매미 ──
  // 탭: 주소 #draw·#craft·#units·#party… 로 바로 열 수 있음(홈페이지 메뉴에서 씀)
  const TABS = ['draw', 'craft', 'units', 'party', 'battle', 'items', 'shop', 'account'];
  const fromHash = () => { const h = location.hash.slice(1); return TABS.includes(h) ? h : null; };
  let me, tab = fromHash() ?? 'draw', filter = 'all';
  addEventListener('hashchange', () => { const h = fromHash(); if (h && h !== tab && me) { tab = h; if (tab === 'craft') craftData = null; if (tab === 'draw') drawData = null; draw(); } });
  // 웹 게임 첫 화면의 「로그인」으로 왔으면(next=duel) 로그인 뒤 게임으로 돌려보냄(카카오 로그인으로 다녀와도 기억)
  if (!APP && new URLSearchParams(location.search).get('next') === 'duel') try { sessionStorage.setItem('gmm-next', 'duel'); } catch { /* 무시 */ }
  async function toDuel() {
    if (EMBED) return false;
    let go = false; try { go = sessionStorage.getItem('gmm-next') === 'duel'; sessionStorage.removeItem('gmm-next'); } catch { /* 무시 */ }
    if (!go) return false;
    try { const { k } = await api('duel-key'); location.href = `/duel?k=${encodeURIComponent(k)}`; return true; } catch { return false; }
  }
  async function main() {
    try { me = await api('me'); if (await toDuel()) return; APP?.onMe(me); if (kmsg && !main.shown) { main.shown = 1; toast(kmsg); } } catch (e) { if (e.message !== 'login') app.innerHTML = `${head()}<p class="empty">${esc(e.message)}</p>`; return; }
    draw();
  }
  function draw() {
    app.classList.toggle('gv', !GAMEUI); app.classList.toggle('gsolo', GAMEUI);
    const tabs = [['draw', '🥚 뽑기'], ['craft', '🧩 조합'], ['units', '<img class="gi" src="/gull.png" alt=""> 내 갈매미'], ['party', '🧭 탐험대 편성'], ['battle', '⚔️ 전투 편성'], ['items', '🎒 아이템'], ['shop', '🛒 상점'], ['account', '👤 계정']];
    app.innerHTML = `${head('<button class="btn ghost" id="out" title="로그아웃" style="padding:6px 12px;font-size:.85rem">🚪<span class="lt"> 로그아웃</span></button>', me)}
      ${GAMEUI ? '' : `<div class="card"><div class="row" style="margin-bottom:10px"><b style="font-weight:400;font-size:1.15rem">${esc(me.nickname || '이름 없는 수비조장')}</b><span class="tag">도감 ${me.dex}/${me.dexTotal}</span></div>
        <div class="money"><div><b><i>🍤</i>${me.snack}</b><small>새우깡</small></div><div><b><i>🥜</i>${me.peanut}</b><small>땅콩</small></div><div><b><i>🎫</i>${me.credits}</b><small>뽑기권</small></div><div><b><i>🪺</i>${me.units.reduce((a, u) => a + u.have, 0)}</b><small>갈매미</small></div></div></div>`}
      ${(() => { const show = GAMEUI ? tabs.filter(([k]) => groupOf(tab).includes(k)) : tabs; return show.length > 1 ? `<div class="tabs ${GAMEUI ? 'gseg' : ''}">${show.map(([k, t]) => `<button data-t="${k}" class="${tab === k ? 'on' : ''}">${t}</button>`).join('')}</div>` : ''; })()}
      <div id="body"></div>`;
    app.querySelector('#out')?.addEventListener('click', async () => { await api('logout', {}).catch(() => {}); APP?.setToken(null); loginScreen('로그아웃했어요.'); });
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => { tab = b.dataset.t; if (tab === 'craft') craftData = null; if (tab === 'draw') drawData = null; history.replaceState(null, '', `#${tab}`); draw(); });
    ({ draw: drawTab, units, craft, battle, party, items, shop, account })[tab](app.querySelector('#body'));
  }

  // 갈매미 목록: 등급별로 걸러 보기
  function units(el) {
    const groups = [['all', '전체'], ['top', '최상위'], ['LEGEND', '전설'], ['HIDDEN', '히든'], ['low', '그 밖']];
    const pass = u => filter === 'all' || (filter === 'top' ? u.top : filter === 'low' ? !u.top && !['LEGEND', 'HIDDEN'].includes(u.tier) : u.tier === filter);
    const list = me.units.filter(pass);
    el.innerHTML = `<div class="filters">${groups.map(([k, t]) => `<button data-f="${k}" class="${filter === k ? 'on' : ''}">${t}</button>`).join('')}</div>
      ${list.length ? `<div class="grid">${list.map((u, i) => `<div class="u" data-ui="${i}" style="--tc:${TIER_COLOR[u.tier] ?? '#888'}"><img loading="lazy" src="${thumb(u.id)}" alt=""><span class="n">×${u.have}</span>${u.away ? `<span class="away">탐험 ${u.away}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></div>`).join('')}</div>`
        : '<p class="empty">이 등급의 갈매미가 아직 없어요.</p>'}
      ${me.kakaoLogin && !GAMEUI ? `<div class="card row" style="justify-content:space-between;margin-top:14px"><span class="note">${me.kakao ? '✅ 카카오 계정 연결됨 (카카오로 로그인 가능)' : '카카오 계정을 연결하면 다음부터 코드 없이 로그인해요.'}</span>
        ${me.kakao ? '<button class="btn ghost" id="unlink" style="padding:6px 12px;font-size:.85rem">연결 해제</button>' : '<a class="btn kakao" href="/my/kakao" style="padding:8px 14px;font-size:.9rem">카카오 연결</a>'}</div>` : ''}`;
    el.querySelectorAll('[data-f]').forEach(b => b.onclick = () => { filter = b.dataset.f; draw(); });
    if (GAMEUI) el.querySelectorAll('[data-ui]').forEach(c => { c.tabIndex = 0; c.onclick = () => unitDetail(list, +c.dataset.ui); });
    el.querySelector('#unlink')?.addEventListener('click', () => run('kakao/unlink', {}, '카카오 계정 연결을 끊을까요? 그다음엔 카톡 코드로 로그인해요.'));
  }

  // 전투 편성: 최상위 3마리(순서 = 출전 순서)
  function battle(el) {
    let pick = [...me.battleTeam];
    const tops = me.units.filter(u => u.top);
    const render = () => {
      el.innerHTML = `<div class="card"><p class="note" style="margin-top:0">수비대 대결에 나갈 <b>최상위 갈매미 3마리</b>를 골라요. 처음 고른 갈매미가 먼저 나가요.</p>
        ${GAMEUI ? `<div class="podium">${[1, 0, 2].map(i => { const u = tops.find(x => x.id === pick[i]); return `<div class="pd pd${i + 1} ${u ? 'full' : ''}" ${u ? `data-rm="${u.id}"` : ''} style="--tc:${u ? TIER_COLOR[u.tier] ?? '#888' : '#c9b08a'}">
            <div class="pd-card">${u ? `<img src="${thumb(u.id)}" alt=""><b>${esc(u.name)}</b>` : '<span>＋</span><small>비어 있음</small>'}</div><div class="pd-step"><em>${i + 1}</em>${i ? `${i + 1}번` : '선봉'}</div></div>`; }).join('')}</div>`
        : `<div class="slots">${[0, 1, 2].map(i => { const u = tops.find(x => x.id === pick[i]); return u ? `<div class="slot full"><img src="${thumb(u.id)}" alt=""><span>${i + 1}. ${esc(u.name)}</span></div>` : `<div class="slot">${i + 1}번 자리<br>비어 있음</div>`; }).join('')}</div>`}
        <button class="btn wide" id="save" ${JSON.stringify(pick) === JSON.stringify(me.battleTeam) ? 'disabled' : ''}>편성 저장</button></div>
        ${tops.length ? `<div class="grid">${tops.map(u => { const i = pick.indexOf(u.id); return `<button class="u ${i >= 0 ? 'on' : ''}" data-id="${u.id}"><img loading="lazy" src="${thumb(u.id)}" alt="">${i >= 0 ? `<span class="ord">${i + 1}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></button>`; }).join('')}</div>`
          : '<p class="empty">아직 최상위 갈매미가 없어요. 카톡에서 조합해 깨워 주세요.</p>'}`;
      el.querySelectorAll('[data-rm]').forEach(b => b.onclick = () => { pick.splice(pick.indexOf(b.dataset.rm), 1); render(); });
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
    if (confirmText && !(await ask(confirmText))) return;
    try { const r = await api(path, body ?? {}); const t = typeof r.result === 'string' ? r.result : ''; toast(t.split('\n')[0] || '완료!'); me = await api('me'); APP?.onMe(me); draw(); }
    catch (e) { if (e.message !== 'login') toast(e.message.split('\n')[0]); }
  };

  // 뽑기: 카톡 「뽑기」·「뽑기권받기」와 같은 처리. 결과는 그림 카드로
  let drawData = null, drawLast = null;
  const TIER_COLOR = { COMMON: '#8a8a8a', UNCOMMON: '#2f8f5b', SPECIAL: '#8a7a6a', RARE: '#1f8fb0', EPIC: '#7a4fd0', LEGEND: '#d9a400', HIDDEN: '#5b3d8f', LIMITED: '#d9483b', TRANSCEND: '#2a6fc0', ETERNAL: '#0f8a7b', IMMORTAL: '#d9a400', NEUTRAL: '#8a7a6a' };
  const waitText = ms => { const m = Math.ceil(ms / 60000), h = Math.floor(m / 60); return h ? `${h}시간 ${m % 60}분` : `${m}분`; };
  async function drawTab(el) {
    if (!drawData) { el.innerHTML = '<p class="empty">불러오는 중…</p>'; try { drawData = await api('draw'); } catch (e) { if (e.message !== 'login') el.innerHTML = `<p class="empty">${esc(e.message)}</p>`; return; } }
    const d = drawData, credits = d.unlimited ? Infinity : d.credits;
    el.innerHTML = `<div class="card row" style="justify-content:space-between">
        <div><b style="font-weight:400;font-size:1.15rem">🎟 뽑기권 ${d.unlimited ? '무제한(관리자)' : `${d.credits}장`}</b>
        <p class="note" style="margin:2px 0 0">${d.wait ? `다음 뽑기권 받기까지 ${waitText(d.wait)}` : `지금 뽑기권 ${d.claim}장을 받을 수 있어요! (${d.cooldownHours}시간마다)`}${d.event ? ` · 🎉 ${esc(d.event.label)}` : ''}</p></div>
        <div class="row" style="gap:8px">${drawLast && GAMEUI ? '<button class="btn ghost" id="rvAgain">🥚 지난 결과</button>' : ''}<button class="btn" id="claim" ${d.wait ? 'disabled' : ''}>🎟 뽑기권 받기</button></div></div>
      ${drawLast && !GAMEUI ? `<div class="card drawres"><b style="font-weight:400">🥚 결과</b><div class="dgrid">${drawLast.items.map(u => `<div class="du ${u.first ? 'new' : ''}" style="--tc:${TIER_COLOR[u.tier] ?? '#888'}"><img src="${cimg(u.img)}" alt="">${u.first ? '<span class="nb">NEW</span>' : ''}${u.n > 1 ? `<span class="n">×${u.n}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></div>`).join('')}</div><p class="pre note" style="margin-top:8px">${esc(drawLast.summary)}</p></div>` : ''}
      <div class="ggrid">${d.gachas.map(g => `<div class="gc"><img src="${cimg(g.img)}" alt=""><div class="gi"><b>${esc(g.label)} 뽑기</b><small>🎟 ${g.cost}장 · ${g.weights.map(w => `<span class="nw">${esc(w.tierName)} ${w.w}%</span>`).join(' · ')}</small>
        ${g.kind === 'HIGH' && d.pity ? `<small>🎯 ${esc(d.pity.tierName)} 보장까지 ${d.pity.left}회</small>` : ''}
        ${GAMEUI ? (() => { const lim = Math.min(50, Math.floor(credits / g.cost)); return `<div class="dqty"><div class="qty"><button class="btn ghost" data-dq="${g.kind}" data-d="-1" ${lim ? '' : 'disabled'}>－</button><input type="number" inputmode="numeric" min="1" max="${Math.max(1, lim)}" value="1" data-di="${g.kind}" data-lim="${lim}" ${lim ? '' : 'disabled'}><button class="btn ghost" data-dq="${g.kind}" data-d="1" ${lim ? '' : 'disabled'}>＋</button><button class="btn ghost dmax" data-dq="${g.kind}" data-d="max" ${lim > 1 ? '' : 'disabled'}>최대</button></div>
          <button class="btn dgo" data-k="${g.kind}" data-n="1" data-cost="${g.cost}" ${lim ? '' : 'disabled'}>${lim ? `1회 뽑기 · 🎟 ${g.cost}` : '뽑기권 부족'}</button></div>`; })()
        : `<div class="row">${[1, 5, 10].map(n => `<button class="btn ${n === 1 ? '' : 'ghost'}" data-k="${g.kind}" data-n="${n}" ${credits >= g.cost * n ? '' : 'disabled'}>${n}회<small>🎟 ${g.cost * n}</small></button>`).join('')}</div>`}</div></div>`).join('')}</div>
      ${GAMEUI ? '' : '<p class="note">결과·보상·미션은 카톡과 같이 계정에 바로 들어가요.</p>'}`;
    // 수량 고르기(게임 화면, v0.11.1): －/＋/최대 → 뽑기 버튼에 횟수·뽑기권 표시
    el.querySelectorAll('[data-di]').forEach(i => {
      const k = i.dataset.di, lim = +i.dataset.lim, go = el.querySelector(`.dgo[data-k="${k}"]`), cost = +go.dataset.cost;
      const val = () => Math.max(1, Math.min(lim, Math.floor(+i.value || 1)));
      const paint = () => { const n = val(); go.dataset.n = n; if (lim) go.textContent = `${n}회 뽑기 · 🎟 ${(cost * n).toLocaleString('ko-KR')}`; };
      i.oninput = paint; i.onblur = () => { i.value = val(); paint(); };
      el.querySelectorAll(`[data-dq="${k}"]`).forEach(b => b.onclick = () => { i.value = b.dataset.d === 'max' ? lim : Math.max(1, Math.min(lim, val() + +b.dataset.d)); paint(); });
    });
    el.querySelector('#rvAgain')?.addEventListener('click', () => reveal(drawLast.items, true));
    el.querySelector('#claim').onclick = async () => { try { const r = await api('claim', {}); toast(String(r.result).split('\n')[0]); drawData = null; me = await api('me'); APP?.onMe(me); draw(); } catch (e) { if (e.message !== 'login') toast(e.message.split('\n')[0]); } };
    el.querySelectorAll('[data-k]').forEach(b => b.onclick = async () => {
      el.querySelectorAll('[data-k]').forEach(x => { x.disabled = true; });
      try { drawLast = (await api('draw', { kind: b.dataset.k, times: +b.dataset.n })).result; APP?.onDraw(drawLast.items); drawData = null; me = await api('me'); APP?.onMe(me); draw(); scrollTo({ top: 0, behavior: 'smooth' }); if (GAMEUI) reveal(drawLast.items); }
      catch (e) { if (e.message !== 'login') { toast(e.message.split('\n')[0]); drawData = null; drawTab(el); } }
    });
  }

  // ── 게임 연출(v0.10.2) ──
  const TIER_RANK = ['COMMON', 'NEUTRAL', 'UNCOMMON', 'SPECIAL', 'RARE', 'EPIC', 'LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL'];
  const rank = t => Math.max(0, TIER_RANK.indexOf(t));
  const big = id => `${BASE}/images/${encodeURIComponent(id)}.png`;
  // 뽑기 결과: 알이 흔들리다 깨지고(가장 좋은 등급 색으로 빛남) → 카드가 한 장씩 뒤집힘. 전설 이상은 빛줄기
  function reveal(items, again = false) {   // again: 지난 결과 다시 보기(알 없이 바로 카드)
    const top = items.reduce((a, u) => (rank(u.tier) > rank(a.tier) ? u : a), items[0]), col = TIER_COLOR[top.tier] ?? '#d9a400', hi = rank(top.tier) >= rank('LEGEND');
    const ov = document.createElement('div'); ov.className = `reveal ${hi ? 'hi' : ''}`; ov.style.setProperty('--tc', col);
    ov.innerHTML = `<div class="rv-egg"><div class="egg"></div><p>눌러서 열기</p></div>
      <div class="rv-cards" hidden>${items.map((u, i) => `<div class="rv-c ${rank(u.tier) >= rank('LEGEND') ? 'hi' : ''}" style="--tc:${TIER_COLOR[u.tier] ?? '#888'};--d:${i * 0.12}s"><div class="rv-in"><div class="rv-back">🥚</div>
        <div class="rv-front"><img src="${cimg(u.img)}" alt="">${u.first ? '<span class="nb">NEW</span>' : ''}${u.n > 1 ? `<span class="n">×${u.n}</span>` : ''}<b>${esc(u.name)}</b><small>${esc(u.tierName)}</small></div></div></div>`).join('')}</div>
      <div class="rv-btns" hidden><button class="btn ghost" id="rvAll">모두 열기</button><button class="btn" id="rvOk">확인</button></div>`;
    document.body.append(ov);
    const open = () => {
      if (ov.classList.contains('cracked')) return;
      ov.classList.add('shake');
      setTimeout(() => { ov.classList.add('cracked'); ov.querySelector('.rv-cards').hidden = false; ov.querySelector('.rv-btns').hidden = false; requestAnimationFrame(() => ov.classList.add('flip')); }, 700);
    };
    ov.querySelector('.rv-egg').onclick = open;
    // 카드 크기: 몇 장이든 화면 안에 다 들어오게(스크롤 없이) 열 수를 골라 가장 크게
    const fit = () => {
      // 쓸 수 있는 넓이 = 화면 − 바깥 여백(16×2) − 카드 칸 여백(6×2), 높이 = 화면 − 여백 − 버튼 줄
      const n = items.length, W = Math.min(innerWidth - 32, 920) - 14, H = innerHeight - 32 - 12 - 14 - 50, gap = 10;
      let best = 50, bc = n;
      for (let cols = 1; cols <= n; cols++) { const rows = Math.ceil(n / cols), w = Math.min((W - gap * (cols - 1)) / cols, ((H - gap * (rows - 1)) / rows) * 3 / 4.1); if (w > best) { best = w; bc = cols; } }
      const cw = Math.floor(Math.min(best, n === 1 ? 220 : 150));
      ov.style.setProperty('--cw', `${cw}px`);
      ov.querySelector('.rv-cards').style.maxWidth = `${bc * cw + gap * (bc - 1) + 12}px`;   // 고른 열 수에서 정확히 줄바꿈
    };
    fit(); addEventListener('resize', fit);
    if (again) { ov.classList.add('cracked', 'all'); ov.querySelector('.rv-cards').hidden = false; ov.querySelector('.rv-btns').hidden = false; ov.querySelector('#rvAll').hidden = true; }
    ov.querySelector('#rvAll').onclick = () => ov.classList.add('all');
    ov.querySelector('#rvOk').onclick = () => { removeEventListener('resize', fit); ov.classList.add('bye'); setTimeout(() => ov.remove(), 200); };
  }
  // 조합 연출: 재료 카드가 둥글게 모였다가 한가운데로 빨려 들어가 번쩍 → 결과 카드(섞기 실패면 펑 하고 흩어짐). 누르면 넘어감
  function forge(mats, r, n = 1, fail = false) {
    return new Promise(res => {
      const col = r ? TIER_COLOR[r.tier] ?? '#d9a400' : '#c98443';
      const ov = document.createElement('div'); ov.className = `forge ${fail ? 'fail' : ''}`; ov.style.setProperty('--tc', col);
      const k = mats.length;
      ov.innerHTML = `<div class="fg-ring">${mats.map((m, i) => `<img class="fg-m" src="${cimg(m)}" style="--a:${(360 / k) * i}deg" alt="">`).join('')}<div class="fg-core"></div></div>
        ${r ? `<div class="fg-res"><img src="${thumb(r.id)}" alt=""><b>${esc(r.name)}${n > 1 ? ` ×${n}` : ''}</b><small>${esc(r.tierName)}</small></div>` : ''}
        <p class="fg-tip">${fail ? '…아무 일도 일어나지 않았어요' : '누르면 계속'}</p>`;
      document.body.append(ov);
      requestAnimationFrame(() => ov.classList.add('go'));
      let ended = false; const end = () => { if (ended) return; ended = true; ov.classList.add('bye'); setTimeout(() => { ov.remove(); res(); }, 200); };
      setTimeout(() => { ov.classList.add('done'); ov.onclick = end; }, fail ? 1300 : 1500);
      setTimeout(end, 6000);
    });
  }
  // 갈매미 상세: 큰 카드·한마디·소개·가진 수. ◀ ▶로 목록 넘기기
  function unitDetail(list, at) {
    document.querySelector('.udet')?.remove();
    const u = list[at], col = TIER_COLOR[u.tier] ?? '#888';
    const ov = document.createElement('div'); ov.className = 'udet'; ov.style.setProperty('--tc', col);
    ov.innerHTML = `<div class="ud-box"><button class="ud-x" aria-label="닫기">✕</button>
      <div class="ud-art"><img src="${big(u.id)}" alt=""></div>
      <div class="ud-tx"><span class="ud-tier">${esc(u.tierName)}</span><h2>${esc(u.name)}</h2>
        ${u.quote ? `<p class="ud-q">「${esc(u.quote)}」</p>` : ''}${u.tagline ? `<p class="ud-tag">${esc(u.tagline)}</p>` : ''}
        ${u.intro ? `<p class="ud-intro">${esc(u.intro)}</p>` : ''}
        <div class="ud-chips"><span>🪺 ${u.have}마리</span>${u.away ? `<span>🧭 탐험 중 ${u.away}</span>` : ''}${u.top ? '<span>⚔️ 수비대 대결 출전 가능</span>' : ''}</div></div>
      ${list.length > 1 ? `<button class="ud-nav prev" aria-label="이전">‹</button><button class="ud-nav next" aria-label="다음">›</button>` : ''}</div>`;
    document.body.append(ov);
    const close = () => ov.remove();
    ov.querySelector('.ud-x').onclick = close; ov.onclick = e => { if (e.target === ov) close(); };
    ov.querySelector('.prev')?.addEventListener('click', () => unitDetail(list, (at - 1 + list.length) % list.length));
    ov.querySelector('.next')?.addEventListener('click', () => unitDetail(list, (at + 1) % list.length));
  }

  // 조합: 카톡 「조합」과 같은 처리. 만들 수 있는 것 / 전체 조합식(등급·이름 찾기) / 재료로 섞기(숨은 조합 찾기)
  let craftView = 'ready', craftGrade = 'all', craftQ = '', mixPick = [], craftData = null;
  const cimg = img => `${BASE}/thumbs/${encodeURIComponent(img)}.jpg`;
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
    const done = async text => { showResult(text); craftData = null; mixPick = []; try { me = await api('me'); APP?.onMe(me); } catch { /* 무시 */ } draw(); };
    el.querySelectorAll('[data-c]').forEach(b => b.onclick = async () => {
      const r = recipes.find(x => x.id === b.dataset.c), n = +b.dataset.n;
      if ((r.confirm || n > 1) && !(await ask(`재료 ${r.materials.reduce((a, m) => a + m.need, 0) * n}마리를 써서\n${r.name}${n > 1 ? ` ${n}개` : ''}${n > 1 ? '를' : josaEul(r.name)} 만들까요?`, { ok: '만들기' }))) return;
      b.disabled = true;
      try { const t = (await api('craft', { id: r.id, times: n })).result; if (GAMEUI) { await forge(r.materials.map(m => m.img), r, n); } done(t); } catch (e) { if (e.message !== 'login') { toast(e.message.split('\n')[0]); b.disabled = false; } }
    });
    el.querySelectorAll('[data-mp]').forEach(b => b.onclick = () => { mixPick.push(b.dataset.mp); craft(el); });
    el.querySelectorAll('[data-mm]').forEach(b => b.onclick = () => { mixPick.splice(mixPick.lastIndexOf(b.dataset.mm), 1); craft(el); });
    el.querySelector('#mclr')?.addEventListener('click', () => { mixPick = []; craft(el); });
    el.querySelector('#mgo')?.addEventListener('click', async () => {
      if (!(await ask(`${mixPick.length}마리를 섞어 볼까요?\n맞는 조합이면 재료를 쓰고, 아니면 재료는 그대로예요.`, { ok: '섞기' }))) return;
      try { const imgs = mixPick.map(id => pool.find(u => u.id === id)?.img).filter(Boolean); const r = (await api('mix', { ids: mixPick })).result; if (GAMEUI) await forge(imgs, null, 1, !r.made); if (r.made) done(r.text); else { showResult(r.text); } } catch (e) { if (e.message !== 'login') toast(e.message.split('\n')[0]); }
    });
  }

  // 탐험 편성: 카톡 「탐험」과 같은 편성·같은 규칙(최상위만, 같은 유닛 여러 마리 가능)
  function party(el) {
    const name = id => me.units.find(u => u.id === id)?.name ?? id;
    if (me.trip && GAMEUI) {
      const t0 = new Date(me.trip.startedAt ?? Date.now()).getTime(), t1 = new Date(me.trip.endsAt).getTime(), left = Math.max(0, t1 - Date.now());
      const pct = Math.min(100, Math.max(0, (Date.now() - t0) / Math.max(1, t1 - t0) * 100)), ids = me.trip.ids ?? [];
      const hm = ms => { const m = Math.ceil(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`; };
      el.innerHTML = `<div class="card exp ${left ? '' : 'back'}"><div class="ex-head"><b>🧭 ${left ? '탐험 중' : '탐험대가 돌아왔어요!'}</b><span>${left ? `남은 시간 <b>${hm(left)}</b>` : '보상을 받아요'}</span></div>
        <div class="ex-road"><div class="ex-path"><i style="width:${pct}%"></i></div><span class="ex-home">🏠</span><span class="ex-goal">${left ? '🏝️' : '🎁'}</span>
          <div class="ex-track"><div class="ex-party" style="left:${left ? pct : 0}%">${ids.slice(0, 5).map((id, i) => `<img src="${thumb(id)}" style="--i:${i}" alt="">`).join('')}${ids.length > 5 ? `<em>+${ids.length - 5}</em>` : ''}</div></div></div>
        <p class="note">${me.trip.units.map(esc).join(', ')}</p>
        ${left ? `<button class="btn ghost wide" id="skip">⚡ 바로 돌아오게 하기 (🍤 ${me.prices.skip.snack})</button>` : '<button class="btn wide" id="claim">🎁 탐험 보상 받기</button>'}</div>`;
      el.querySelector('#claim')?.addEventListener('click', () => run('expedition/claim'));
      el.querySelector('#skip')?.addEventListener('click', () => run('shop', { item: 'skip' }, `새우깡 ${me.prices.skip.snack}개로 탐험대를 바로 돌아오게 할까요?`));
      if (left) { clearTimeout(party.t); party.t = setTimeout(() => { if (tab === 'party') party(el); }, Math.min(60000, left + 500)); }
      return;
    }
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

  // 상점: 카톡 상점과 같은 물건 + 대결 아이템. 수량을 적어서 사요(가격 합계를 바로 보여 줌)
  function shop(el) {
    const p = me.prices, mine = id => me.items.find(i => i.id === id)?.count ?? 0, max = me.itemRules.maxStack;
    // 물건마다: 이름·설명, 수량 칸(최소 1 ~ 살 수 있는 만큼), 사기 버튼(합계)
    const goods = [
      { key: 't1', title: `🎟 뽑기권 ${p.tickets.amount}장`, note: '새우깡으로 사요', unit: p.tickets.snack, cur: '🍤', limit: Math.min(50, Math.floor(me.snack / p.tickets.snack)), buy: n => run('shop', { item: 'tickets', count: n }, `새우깡 ${p.tickets.snack * n}개로 뽑기권 ${p.tickets.amount * n}장을 살까요?`) },
      { key: 't2', title: `🥜 → 🎟 뽑기권 ${p.peanutTickets.amount}장`, note: '땅콩으로 바꿔요', unit: 1, cur: '🥜', limit: Math.min(50, me.peanut), buy: n => run('shop', { item: 'peanutTickets', count: n }, `땅콩 ${n}개로 뽑기권 ${p.peanutTickets.amount * n}장을 바꿀까요?`) },
      ...me.itemDefs.map(d => ({ key: d.id, title: `${d.name} <span class="tag">가진 ${mine(d.id)}개</span>`, note: d.desc, unit: d.price, cur: '🍤', limit: Math.max(0, Math.min(max - mine(d.id), Math.floor(me.snack / d.price))),
        buy: n => run('items/buy', { id: d.id, count: n }, `${d.name} ${n}개를 새우깡 ${d.price * n}개로 살까요?`) })),
    ];
    const icon = { t1: '🎟', t2: '🥜', ...Object.fromEntries(me.itemDefs.map(d => [d.id, `<img src="${BASE}/duel/icons/${d.id}.png" alt="" onerror="this.replaceWith('🎒')">`])) };
    el.innerHTML = GAMEUI ? `<div class="shelf-head"><b>🛒 수비대 매점</b><small>대결 아이템은 종류마다 ${max}개까지 · 갈매미 교환·팔기는 카톡 「상점」</small></div>
      <div class="shelf">${goods.map(g => `<div class="ware ${g.limit ? '' : 'off'}"><div class="ware-ic">${icon[g.key] ?? '🎁'}</div><b>${g.title}</b><small>${esc(g.note)}</small>
        <div class="qty"><button class="btn ghost" data-q="${g.key}" data-d="-1" ${g.limit ? '' : 'disabled'}>－</button><input type="number" inputmode="numeric" min="1" max="${Math.max(1, g.limit)}" value="1" data-i="${g.key}" ${g.limit ? '' : 'disabled'}><button class="btn ghost" data-q="${g.key}" data-d="1" ${g.limit ? '' : 'disabled'}>＋</button></div>
        <button class="btn price" data-b="${g.key}" ${g.limit ? '' : 'disabled'}>${g.limit ? `${g.cur} ${g.unit}` : g.cur === '🥜' ? '땅콩 부족' : '살 수 없음'}</button></div>`).join('')}</div>`
    : `<div class="card"><b style="font-weight:400">🛒 상점</b><p class="note" style="margin:2px 0 0">지금 🍤 ${me.snack} · 🥜 ${me.peanut} · 대결 아이템은 종류마다 ${max}개까지</p></div>
      ${goods.map(g => `<div class="card shopi"><div><b style="font-weight:400">${g.title}</b><p class="note" style="margin:2px 0 0">${esc(g.note)} · ${g.cur} ${g.unit}</p></div>
        <div class="qty"><button class="btn ghost" data-q="${g.key}" data-d="-1" ${g.limit ? '' : 'disabled'}>－</button><input type="number" inputmode="numeric" min="1" max="${Math.max(1, g.limit)}" value="1" data-i="${g.key}" ${g.limit ? '' : 'disabled'}><button class="btn ghost" data-q="${g.key}" data-d="1" ${g.limit ? '' : 'disabled'}>＋</button>
        <button class="btn" data-b="${g.key}" ${g.limit ? '' : 'disabled'}>${g.limit ? `사기 ${g.cur} ${g.unit}` : g.cur === '🥜' ? '땅콩 부족' : '살 수 없음'}</button></div></div>`).join('')}
      <p class="note">갈매미 교환·팔기·닉네임 장식은 카톡 「상점」에서 할 수 있어요.</p>`;
    const val = g => { const i = el.querySelector(`[data-i="${g.key}"]`); const n = Math.max(1, Math.min(g.limit, Math.floor(+i.value || 1))); return n; };
    const paint = g => { const n = val(g), b = el.querySelector(`[data-b="${g.key}"]`); if (g.limit) b.textContent = `${GAMEUI ? '' : '사기 '}${g.cur} ${(g.unit * n).toLocaleString('ko-KR')}`; };
    for (const g of goods) {
      const i = el.querySelector(`[data-i="${g.key}"]`);
      i.oninput = () => paint(g);
      i.onblur = () => { i.value = val(g); paint(g); };
      el.querySelectorAll(`[data-q="${g.key}"]`).forEach(b => b.onclick = () => { i.value = Math.max(1, Math.min(g.limit, val(g) + +b.dataset.d)); paint(g); });
      el.querySelector(`[data-b="${g.key}"]`).onclick = () => g.buy(val(g));
    }
  }

  // 계정: 카카오 연결 · 로그아웃 · 개인정보처리방침 · 계정 삭제(탈퇴)
  function account(el) {
    el.innerHTML = `<div class="card"><b style="font-weight:400">${esc(me.nickname || '이름 없는 수비조장')}</b><p class="note" style="margin:2px 0 0">도감 ${me.dex}/${me.dexTotal} · 🎟 ${me.credits} · 🍤 ${me.snack}</p></div>
      ${me.kakaoLogin ? `<div class="card row" style="justify-content:space-between"><span class="note">${me.kakao ? '✅ 카카오 계정 연결됨' : '카카오 계정을 연결하면 다음부터 코드 없이 로그인해요.'}</span>${me.kakao ? '<button class="btn ghost" id="unlink2">연결 해제</button>' : '<a class="btn kakao" href="/my/kakao">카카오 연결</a>'}</div>` : ''}
      <div class="card row" style="justify-content:space-between"><span class="note">이 기기에서 로그아웃해요.</span><button class="btn ghost" id="out2">🚪 로그아웃</button></div>
      <div class="card row" style="justify-content:space-between"><span class="note">어떤 정보를 왜 저장하는지</span><a class="btn ghost" href="/privacy">개인정보처리방침</a></div>
      <div class="card danger"><b style="font-weight:400">🗑 계정 삭제(탈퇴)</b>
        <p class="note">갈매미·도감·재화·아이템·탐험·스토리 진행·댓글이 <b>모두 지워지고 되돌릴 수 없어요.</b> 카톡 채널에 다시 말을 걸면 새 계정으로 시작해요.<br>서버 백업에는 최대 7일 동안 남아 있다가 자동으로 사라져요.</p>
        <input id="delc" class="search" placeholder="확인을 위해 탈퇴 라고 적어 주세요" autocomplete="off">
        <button class="btn wide" id="del" style="background:var(--bad);box-shadow:0 3px 0 #7a1f17" disabled>계정 삭제</button></div>`;
    el.querySelector('#unlink2')?.addEventListener('click', () => run('kakao/unlink', {}, '카카오 계정 연결을 끊을까요? 그다음엔 카톡 코드로 로그인해요.'));
    el.querySelector('#out2').onclick = async () => { await api('logout', {}).catch(() => {}); APP?.setToken(null); loginScreen('로그아웃했어요.'); };
    const c = el.querySelector('#delc'), d = el.querySelector('#del');
    c.oninput = () => { d.disabled = c.value.trim() !== '탈퇴'; };
    d.onclick = async () => {
      if (!(await ask('정말 계정을 삭제할까요? 되돌릴 수 없어요.', { ok: '삭제', danger: true }))) return;
      d.disabled = true;
      try { await api('account/delete', { confirm: '탈퇴' }); APP?.setToken(null); app.innerHTML = `${head()}<div class="card login"><h2 style="font-weight:400">계정을 삭제했어요</h2><p class="note">그동안 갈매미 섬을 지켜 주셔서 고마워요. 🫡</p><a class="btn wide" href="/">홈으로</a></div>`; }
      catch (e) { if (e.message !== 'login') { toast(e.message); d.disabled = false; } }
    };
  }

  main();
})();
