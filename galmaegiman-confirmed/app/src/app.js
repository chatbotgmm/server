// 갈매미맨 안드로이드 앱 연결부: 「내 갈매미」 화면(my.js)보다 먼저 불려서 window.gmmApp 을 만들어 둠
// - 서버 주소, 로그인 토큰 보관(웹의 쿠키 대신)
// - 카카오 로그인: 브라우저로 로그인 → gmmapp://auth?code=… 로 앱에 돌아옴 → 처음 만든 비밀값으로 토큰 교환(PKCE)
// - 알림: 탐험대가 돌아올 때, 뽑기권을 받을 수 있을 때(휴대폰 안에서 예약, 서버·Firebase 필요 없음)
// - 진동: 뽑기 결과(전설 이상이면 세게)
// - 뒤로 가기 버튼, 바깥 링크(도감·검색·방침)는 앱 안 브라우저로
(() => {
  const BASE = 'https://gmm.dpdns.org';
  const P = (window.Capacitor && window.Capacitor.Plugins) || {};
  const KEY = 'gmm-token', VKEY = 'gmm-kakao-verifier';
  const onMy = location.pathname.endsWith('my.html');   // 지금 화면: 게임(index.html) / 내 갈매미(my.html)
  window.GMM_BASE = BASE;

  const store = {
    get: k => { try { return localStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* 저장 못 해도 진행 */ } },
  };
  const b64url = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  // 카카오 로그인 시작: 비밀값(verifier)은 앱에만, 서버에는 그 해시(challenge)만 보냄
  async function kakao() {
    const v = b64url(crypto.getRandomValues(new Uint8Array(32)));
    store.set(VKEY, v);
    const c = b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(v)));
    open(`${BASE}/my/kakao?app=1&c=${c}`);
  }
  const open = url => (P.Browser ? P.Browser.open({ url }) : window.open(url, '_blank'));

  // 알림 예약(같은 번호는 덮어씀): 1 = 탐험 귀환, 2 = 뽑기권 받기
  // 대략적인 시각(isExactNotification:false): 「정확한 알람」 권한을 쓰지 않으므로, true(기본값)면 예약할 때마다 「알람 및 리마인더」 설정 화면이 떠요
  let asked = false, last = '';
  async function onMe(me) {
    const N = P.LocalNotifications;
    if (!N) return;
    const key = `${me.trip?.endsAt ?? ''}|${me.claimReadyAt ?? ''}`;
    if (key === last) return;   // 시각이 바뀔 때만 다시 예약
    last = key;
    try {
      if (!asked) { asked = true; const p = await N.checkPermissions(); if (p.display !== 'granted') await N.requestPermissions(); }
      await N.cancel({ notifications: [{ id: 1 }, { id: 2 }] }).catch(() => {});
      const soon = Date.now() + 5000, list = [];
      const trip = me.trip && new Date(me.trip.endsAt).getTime();
      if (trip && trip > soon) list.push({ id: 1, title: '🧭 탐험대가 돌아왔어요!', body: '보상을 받으러 가 볼까요?', schedule: { at: new Date(trip), allowWhileIdle: true }, isExactNotification: false, extra: { tab: 'party' } });
      const claim = me.claimReadyAt && new Date(me.claimReadyAt).getTime();
      if (claim && claim > soon) list.push({ id: 2, title: '🎟 뽑기권을 받을 수 있어요', body: '「뽑기」에서 뽑기권 받기를 눌러요.', schedule: { at: new Date(claim), allowWhileIdle: true }, isExactNotification: false, extra: { tab: 'draw' } });
      if (list.length) await N.schedule({ notifications: list });
    } catch { /* 알림을 못 써도 게임은 그대로 */ }
  }
  // 뽑기 진동: 전설 이상이 나오면 세게 두 번
  const TOP = new Set(['LEGEND', 'HIDDEN', 'LIMITED', 'TRANSCEND', 'ETERNAL', 'IMMORTAL']);
  async function onDraw(items) {
    const H = P.Haptics;
    if (!H) return;
    try {
      if (items.some(i => TOP.has(i.tier))) { await H.impact({ style: 'HEAVY' }); setTimeout(() => H.impact({ style: 'HEAVY' }).catch(() => {}), 180); }
      else await H.impact({ style: items.some(i => i.first) ? 'MEDIUM' : 'LIGHT' });
    } catch { /* 진동 없는 기기 */ }
  }

  window.gmmApp = { token: () => store.get(KEY), setToken: t => store.set(KEY, t || null), onMe, onDraw, kakao };

  // 링크: 화면 안 탭(#…)은 그대로, 카카오 로그인은 앱 방식, 나머지 사이트 주소는 앱 안 브라우저로
  document.addEventListener('click', e => {
    const a = e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    const h = a.getAttribute('href');
    if (h.startsWith('#')) return;
    e.preventDefault();
    if (h.startsWith('/my/kakao')) return void kakao();
    // 앱 안 화면: 게임(index.html) ↔ 내 갈매미(my.html)
    const tab = h.split('#')[1] || '';
    if (h === '/my' || h.startsWith('/my#') || h.startsWith('my.html')) { if (onMy) location.hash = tab; else location.href = `my.html${tab ? `#${tab}` : ''}`; return; }
    if (h === '/' || h.startsWith('/duel') || h === 'index.html') { location.href = 'index.html'; return; }
    open(h.startsWith('http') ? h : BASE + h);
  }, true);

  // 카카오 로그인에서 돌아옴: gmmapp://auth?code=…
  P.App?.addListener('appUrlOpen', async ({ url }) => {
    try {
      const u = new URL(url), code = u.searchParams.get('code'), v = store.get(VKEY);
      if (u.protocol !== 'gmmapp:' || !code || !v) return;
      store.set(VKEY, null);
      const r = await fetch(`${BASE}/my/api/app/exchange`, { method: 'POST', headers: { 'content-type': 'application/json', 'x-gmm': '1' }, body: JSON.stringify({ code, verifier: v }) });
      const j = await r.json().catch(() => ({}));
      P.Browser?.close().catch(() => {});
      if (r.ok && j.token) { store.set(KEY, j.token); location.href = 'index.html'; }
    } catch { /* 다시 시도하면 됨 */ }
  });
  // 알림을 누르면 그 탭으로
  P.LocalNotifications?.addListener('localNotificationActionPerformed', ({ notification }) => {
    const tab = notification?.extra?.tab;
    if (tab) { if (onMy) location.hash = tab; else location.href = `my.html#${tab}`; }
  });
  // 뒤로 가기: 게임은 화면 안 ‹ 버튼 → 첫 화면에선 앱 끝내기 / 내 갈매미는 열린 창 닫기 → 게임으로
  P.App?.addListener('backButton', () => {
    if (onMy) {
      const res = document.querySelector('.result');
      if (res) return void res.remove();
      location.href = 'index.html'; return;
    }
    const modal = document.getElementById('modal');
    const bk = document.getElementById('bk');
    if (modal && !modal.hidden && !modal.classList.contains('scene-on')) { modal.hidden = true; return; }
    if (bk) return void bk.click();
    P.App.exitApp();
  });
})();
