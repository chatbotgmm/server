// 공개 홈페이지·웹 게임 검수 캡처(실제 Chromium). 시험 서버(:3999, 심사 코드 135790)에서만 쓴다. 운영 서버에 쓰지 않는다.
// 사용: node scripts/web/site-review.mjs <결과 폴더> [화면 이름,…]
// 뷰포트: PC 1440×900 · 태블릿 1024×768 · 가로 폰 915×412 · 세로 폰 390×844(+ 작은 세로 360×640)
// 숫자 검사(findings.json):
//  - 가로 넘침(페이지 폭보다 넓음), 게임 화면 세로 넘침(핵심 화면은 스크롤 없음)
//  - 보이는 버튼·링크의 터치 영역 48×48 미만
//  - 글자 잘림(요소 안에서 넘침: scrollWidth/Height > client)
//  - 형제 입력 요소끼리 겹침
//  - 게임 글꼴(Jua·Gowun Dodum)이 실제로 쓰였는지
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';
const out = process.argv[2]; if (!out) { console.error('결과 폴더를 주세요'); process.exit(1); }
const only = process.argv[3] ? new Set(process.argv[3].split(',')) : null;
mkdirSync(out, { recursive: true });
const B = 'http://127.0.0.1:3999';
const VIEWS = [['pc', 1440, 900], ['tab', 1024, 768], ['land', 915, 412], ['port', 390, 844], ['small', 360, 640]];
const PUBLIC = [['home', '/'], ['about', '/about'], ['characters', '/characters'], ['notices', '/notices'], ['download', '/download'], ['login', '/login?next=/game&from=/about']];
const GAME = [['g-home', '#home'], ['g-dex', '#dex'], ['g-team', '#team'], ['g-play', '#play'], ['g-match', '#match'], ['g-augment', '#augment'], ['g-battle', '#battle']];
const findings = [];

const check = (game) => (() => {
  const r = [], vis = e => { const s = getComputedStyle(e), b = e.getBoundingClientRect(); return s.visibility !== 'hidden' && s.display !== 'none' && b.width > 0 && b.height > 0 && !e.closest('[hidden]'); };
  const name = e => (e.getAttribute('aria-label') || e.textContent || e.className || e.tagName).trim().replace(/\s+/g, ' ').slice(0, 40);
  const de = document.documentElement;
  if (de.scrollWidth > innerWidth + 1) r.push({ kind: '가로 넘침', detail: `${de.scrollWidth} > ${innerWidth}` });
  if (window.__game && de.scrollHeight > innerHeight + 1) r.push({ kind: '세로 넘침(게임)', detail: `${de.scrollHeight} > ${innerHeight}` });
  const hits = [...document.querySelectorAll('a[href],button,input,select,[role=option],[role=button]')].filter(e => vis(e) && !e.classList.contains('skip'));
  for (const e of hits) {
    const b = e.getBoundingClientRect();
    if ((b.width < 47.5 || b.height < 47.5) && !e.closest('.prose,.foot-links.sub,p,li>span,.lead,.plain,.muted')) r.push({ kind: '터치 48 미만', detail: `${name(e)} ${Math.round(b.width)}×${Math.round(b.height)}` });
    if (b.right > innerWidth + 1 || b.left < -1) r.push({ kind: '화면 밖(가로)', detail: name(e) });
    if (window.__game && (b.bottom > innerHeight + 1 || b.top < -1)) r.push({ kind: '화면 밖(세로)', detail: name(e) });
  }
  for (const e of document.querySelectorAll('h1,h2,h3,b,.btn,.pill,label,.dock a span,.who b,.ccard span,.growth b')) {
    if (!vis(e)) continue;
    const s = getComputedStyle(e);
    if (s.webkitLineClamp && s.webkitLineClamp !== 'none') continue;   // 줄 수 제한은 의도(2줄)
    if (e.scrollWidth > e.clientWidth + 2 && s.overflow !== 'visible') r.push({ kind: '글자 잘림(가로)', detail: name(e) });
    if (e.scrollHeight > e.clientHeight + 2 && s.overflow !== 'visible') r.push({ kind: '글자 잘림(세로)', detail: name(e) });
  }
  const parents = new Set(hits.map(e => e.parentElement));
  for (const p of parents) {
    const kids = hits.filter(e => e.parentElement === p);
    for (let i = 0; i < kids.length; i++) for (let j = i + 1; j < kids.length; j++) {
      const a = kids[i].getBoundingClientRect(), b = kids[j].getBoundingClientRect();
      const ox = Math.min(a.right, b.right) - Math.max(a.left, b.left), oy = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
      if (ox > 2 && oy > 2) r.push({ kind: '겹침', detail: `${name(kids[i])} / ${name(kids[j])}` });
    }
  }
  const fonts = { jua: document.fonts.check('20px Jua', '갈매미맨'), gowun: document.fonts.check('20px "Gowun Dodum"', '갈매미맨') };
  if (!fonts.jua || !fonts.gowun) r.push({ kind: '게임 글꼴 미적용', detail: JSON.stringify(fonts) });
  return r;
});

const b = await chromium.launch();
// 로그인은 한 번만(서버 로그인 시도 제한 1분 10회) — 받은 쿠키를 게임 화면 캡처에 다시 쓴다. 공개 페이지는 로그인 전 상태로 찍는다
let auth = null;
{
  const c = await b.newContext(); const p = await c.newPage();
  await p.goto(B + '/login?next=/game&from=/'); await p.fill('#code', '135790'); await p.click('.code button');
  await p.waitForURL(u => new URL(u).pathname === '/game', { timeout: 20000 }); auth = await c.storageState(); await c.close();
}
for (const [vn, w, h] of VIEWS) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1, serviceWorkers: 'block', locale: 'ko-KR' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error' && !/status of 401/.test(m.text())) errs.push(m.text()); });   // 로그인 전 공개 페이지의 /my/api/who 401은 정상
  const shot = async (n, game) => {
    await p.evaluate(g => { window.__game = g; }, game);
    await p.evaluate(async () => { await document.fonts.ready; });
    await p.waitForTimeout(500);
    const r = await p.evaluate(check(game));
    for (const x of r) findings.push({ view: vn, screen: n, ...x });
    await p.screenshot({ path: `${out}/${n}-${vn}.png`, fullPage: !game });
    console.log(vn, n, r.length ? r.map(x => `${x.kind}:${x.detail}`).join(' | ') : '문제 없음');
  };
  for (const [n, url] of PUBLIC) { if (only && !only.has(n)) continue; await p.goto(B + url); await shot(n, false); }
  if (!only || [...only].some(x => x.startsWith('g-') || x === 'menu')) {
    if (!only || only.has('menu')) if (w < 961) { await p.goto(B + '/'); await p.click('.menu-btn'); await p.waitForTimeout(200); await p.evaluate(() => { window.__game = false; }); await p.screenshot({ path: `${out}/menu-${vn}.png` }); await p.keyboard.press('Escape'); }
    await ctx.addCookies(auth.cookies);
    for (const [n, hash] of GAME) {
      if (only && !only.has(n)) continue;
      await p.goto(`${B}/game${hash}`).catch(() => p.goto(`${B}/game${hash}`)); await p.waitForFunction(() => window.GMM && window.GMM.state && !window.GMM.loading.size, null, { timeout: 15000 }).catch(() => {});
      await p.waitForTimeout(900); await shot(n, true);
    }
  }
  if (errs.length) findings.push({ view: vn, screen: '*', kind: '스크립트 오류', detail: [...new Set(errs)].join(' / ').slice(0, 400) });
  await ctx.close();
}
await b.close();
writeFileSync(`${out}/findings.json`, JSON.stringify(findings, null, 1));
console.log('검사 결과', findings.length, '건 →', `${out}/findings.json`);
