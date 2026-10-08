// 갈매미맨 서비스 워커: 앱처럼 설치(PWA)용. 화면은 항상 서버에서 새로 받고(게임 정보가 바뀌므로),
// 인터넷이 끊겼을 때만 저장해 둔 「연결 끊김」 화면을 보여 줌. 그림·글꼴만 캐시
const CACHE = 'gmm-v1';
const SHELL = ['/pwa/offline.html', '/gull.png', '/icon-192.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== 'GET') return;
  // 화면(페이지): 네트워크 먼저, 실패하면 연결 끊김 화면
  if (r.mode === 'navigate') { e.respondWith(fetch(r).catch(() => caches.match('/pwa/offline.html'))); return; }
  // API·로그인은 캐시하지 않음
  if (u.origin === location.origin && (u.pathname.startsWith('/my/api') || u.pathname.startsWith('/my/kakao') || u.pathname.startsWith('/api/'))) return;
  // 그림·글꼴: 캐시에 있으면 그것, 없으면 받아서 저장
  if (/\.(png|jpg|webp|woff2?)$/.test(u.pathname) || u.host === 'fonts.gstatic.com') {
    e.respondWith(caches.match(r).then(hit => hit || fetch(r).then(res => { if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then(c => c.put(r, copy)); } return res; })));
  }
});
