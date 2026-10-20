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
const VIEWS = [['pc', 1440, 900], ['tab', 1024, 768], ['land', 915, 412], ['port', 390, 844], ['small', 360, 640]].filter(([n]) => !process.env.VIEWS || process.env.VIEWS.split(',').includes(n));   // VIEWS=pc,port 처럼 일부만
const PUBLIC = [['home', '/'], ['about', '/about'], ['characters', '/characters'], ['notices', '/notices'], ['download', '/download'], ['login', '/login?next=/game&from=/about'],
  ['event', '/event'], ['dex', '/dex'], ['dex-one', '/dex/C1'], ['recipes', '/recipes'], ['hidden', '/hidden'], ['rank', '/rank'], ['talk', '/talk'], ['world', '/world'], ['search', '/search?q=갈매미'], ['odds', '/odds'], ['privacy', '/privacy'], ['privacy-old', '/privacy/2026-10-08'], ['delete', '/account/delete'], ['native', '/app/native'], ['missing', '/zz-none']];   // 옛 공개 페이지도 새 틀(legacy.css)로 검수
const GAME = [['g-home', '#home'], ['g-dex', '#dex'], ['g-team', '#team'], ['g-team-pvp', '#team?mode=pvp'], ['g-play', '#play'], ['g-story', '#story'], ['g-draw', '#draw'], ['g-craft', '#craft'], ['g-explore', '#explore'], ['g-shop', '#shop'], ['g-account', '#account']];
// 전투 단계: 화면마다 새 스토리 전투(P-1)를 시작해 증강체 → 명령(기본·공격·아이템·교체·도망 확인) → 상성표를 찍고 물러난다
const BATTLE = [['g-root', null], ['g-attack', '[data-p="attack"]'], ['g-item', '[data-p="item"]'], ['g-switch', '[data-p="switch"]'], ['g-leave', '[data-p="leave"]']];
const findings = [], infos = [];   // infos: 실패가 아닌 참고(패널 안 스크롤로 닿는 버튼 등)

const check = (game) => (() => {
  const r = [], vis = e => { const s = getComputedStyle(e), b = e.getBoundingClientRect(); return s.visibility !== 'hidden' && s.display !== 'none' && b.width > 0 && b.height > 0 && !e.closest('[hidden]'); };
  const name = e => (e.getAttribute('aria-label') || e.textContent || e.className || e.tagName).trim().replace(/\s+/g, ' ').slice(0, 40);
  const de = document.documentElement;
  if (de.scrollWidth > innerWidth + 1) r.push({ kind: '가로 넘침', detail: `${de.scrollWidth} > ${innerWidth}` });
  if (window.__game && de.scrollHeight > innerHeight + 1) r.push({ kind: '세로 넘침(게임)', detail: `${de.scrollHeight} > ${innerHeight}` });
  const hits = [...document.querySelectorAll('a[href],button,input,select,[role=option],[role=button]')].filter(e => vis(e) && !e.classList.contains('skip') && !(e.closest('details:not([open])') && !e.closest('summary')));   // 접힌 내용은 보이지 않음
  for (const e of hits) {
    const b = e.getBoundingClientRect();
    if ((b.width < 47.5 || b.height < 47.5) && !e.closest('.prose,.foot-links.sub,p,li>span,.lead,.plain,.muted')) r.push({ kind: '터치 48 미만', detail: `${name(e)} ${Math.round(b.width)}×${Math.round(b.height)}` });
    if (b.right > innerWidth + 1 || b.left < -1) r.push({ kind: '화면 밖(가로)', detail: name(e) });
    // 스스로 스크롤되는 패널 안(목록·상세)에 있으면 스크롤로 닿을 수 있다 → 실패가 아니라 정보로만 남긴다
    const scroller = (() => { for (let x = e.parentElement; x && x !== document.body; x = x.parentElement) { const o = getComputedStyle(x).overflowY; if ((o === 'auto' || o === 'scroll') && x.scrollHeight > x.clientHeight + 1) return x; } return null; })();
    if (window.__game && (b.bottom > innerHeight + 1 || b.top < -1)) r.push(scroller ? { kind: '패널 안 스크롤(정보)', detail: name(e), info: true } : { kind: '화면 밖(세로)', detail: name(e) });
  }
  for (const e of document.querySelectorAll('h1,h2,h3,b,.btn,.pill,label,.dock a span,.who b,.ccard span,.growth b,.reg-card,.cbtn,.slot,.a-card')) {   // 카드·버튼 자체가 내용보다 작으면(overflow hidden) 잘림으로 본다
    if (!vis(e) || e.classList.contains('sr')) continue;   // 화면 읽기용(일부러 1px)
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
  const p = await ctx.newPage(); p.setDefaultTimeout(6000); const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error' && !/status of 4\d\d/.test(m.text())) errs.push(m.text()); });
  p.on('response', r => { if (r.status() >= 400 && r.status() !== 401) errs.push(`${r.status()} ${new URL(r.url()).pathname}`); });   // 401은 로그인 전 공개 페이지의 정상 응답   // 로그인 전 공개 페이지의 /my/api/who 401은 정상
  const shot = async (n, game) => {
    await p.evaluate(g => { window.__game = g; }, game);
    await p.evaluate(async () => { await document.fonts.ready; });
    await p.waitForTimeout(500);
    const all = await p.evaluate(check(game)), r = all.filter(x => !x.info);
    for (const x of all) (x.info ? infos : findings).push({ view: vn, screen: n, ...x });
    await p.screenshot({ path: `${out}/${n}-${vn}.png`, fullPage: !game });
    console.log(vn, n, r.length ? r.map(x => `${x.kind}:${x.detail}`).join(' | ') : '문제 없음');
  };
  for (const [n, url] of PUBLIC) { if (only && !only.has(n)) continue; await p.goto(B + url); await shot(n, false); }
  if (!only || [...only].some(x => x.startsWith('g-') || x === 'menu')) {
    if (!only || only.has('menu')) if (w < 961) { await p.goto(B + '/'); await p.click('.menu-btn'); await p.waitForTimeout(200); await p.evaluate(() => { window.__game = false; }); await p.screenshot({ path: `${out}/menu-${vn}.png` }); await p.keyboard.press('Escape'); }
    await ctx.addCookies(auth.cookies);
    const ready = () => p.waitForFunction(() => window.GMM && window.GMM.state && !window.GMM.loading.size, null, { timeout: 15000 }).catch(() => {});
    const api = (path, body) => p.evaluate(async ([path, body]) => { const r = await fetch('/my/api/' + path, { method: body ? 'POST' : 'GET', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined }); return r.json(); }, [path, body]);
    for (const [n, hash] of GAME) {
      if (only && !only.has(n)) continue;
      await p.goto(`${B}/game${hash}`).catch(() => p.goto(`${B}/game${hash}`)); await ready();
      await p.waitForTimeout(900); await shot(n, true);
    }
    if (!only || only.has('g-extra')) {   // 출석·미션 시트, 하급 모집 1회 결과(시험 DB의 지원서만 씀), 직접 섞기 탭
      await p.goto(`${B}/game#home`); await ready(); await p.click('[data-daily]'); await p.waitForSelector('.week', { timeout: 8000 }).catch(() => {}); await p.waitForTimeout(400); await shot('g-daily', true); await p.keyboard.press('Escape');
      await p.goto(`${B}/game#draw`); await p.waitForSelector('[data-go]'); await p.click('.kind[data-k="LOW"]'); await p.click('[data-go]'); await p.waitForTimeout(350); await p.screenshot({ path: `${out}/g-draw-envelope-${vn}.png` }); /* 봉투 애니메이션 중간 */ await p.waitForSelector('.got', { timeout: 10000 }).catch(() => {}); await p.waitForTimeout(500); await shot('g-draw-result', true);
      await p.goto(`${B}/game#craft`); await p.waitForSelector('[data-t="mix"]'); await p.click('[data-t="mix"]'); await p.waitForSelector('.craft-grid [data-i]', { timeout: 8000 }).catch(() => {}); await p.click('.craft-grid [data-i="0"]').catch(() => {}); await p.waitForTimeout(400); await shot('g-craft-mix', true);
      await p.click('[data-t="recipes"]').catch(() => {}); await p.waitForTimeout(600);
    }
    if (!only || only.has('g-match')) {   // 랭크전(AI 대체 없음) 탐색 화면을 찍고 바로 취소
      await p.goto(`${B}/game#match?mode=rank`); await ready(); await p.waitForSelector('[data-cancel]:not([disabled])', { timeout: 10000 }).catch(() => {});
      await p.waitForTimeout(1600); await shot('g-match', true);
      await p.click('[data-cancel]').catch(() => {}); await p.waitForTimeout(1500);
    }
    if (!only || only.has('g-battle')) {
      await p.goto(`${B}/game#home`); await ready();
      const cur = await api('native/battle/current'); if (cur.session) await api('native/battle/turn', { session: cur.session, revision: cur.revision, leave: true });
      // 시험 계정만: 앞선 검수 전투로 스토리 체력이 0이 되면 시작이 거절되므로 큰 치료약으로 회복(게임 규칙 그대로 API 사용)
      const st0 = await api('native/state');
      for (const k of st0.team || []) { const u = st0.units.find(x => x.key === k); if (u && u.hp < u.maxHp * 0.5) { await api('native/heal', { key: k, item: 'big' }); } }
      const start = await api('native/battle/start', { mode: 'story', stage: 'P-1' });
      if (start.error) findings.push({ view: vn, screen: 'g-battle', kind: '전투 시작 실패', detail: start.error });
      else {
        await p.goto(`${B}/game#battle`); await p.waitForSelector('.augment,.battle', { timeout: 10000 }).catch(() => {}); await p.waitForTimeout(900); await shot('g-augment', true);
        await p.click('.a-card').catch(() => {}); await p.waitForTimeout(200); await shot('g-augment-picked', true);
        await p.click('[data-ok]').catch(() => {}); await p.waitForSelector('.battle .cmd', { timeout: 10000 }).catch(() => {}); await p.waitForTimeout(900);
        for (const [bn, sel] of BATTLE) {
          if (sel) { await p.click(sel).catch(() => {}); await p.waitForTimeout(300); }
          await shot(bn, true);
          if (sel) { await p.click('[data-back]').catch(() => {}); await p.waitForTimeout(200); }
        }
        await p.click('.my-hp [data-chart]').catch(() => {}); await p.waitForTimeout(300); await shot('g-chart', true); await p.keyboard.press('Escape');
        await p.click('[data-p="attack"]').catch(() => {}); await p.waitForTimeout(200);
        await p.click('[data-skill]:not([disabled])').catch(() => {}); await p.waitForTimeout(1200); await shot('g-after-turn', true);
        const c2 = await api('native/battle/current'); if (c2.session) await api('native/battle/turn', { session: c2.session, revision: c2.revision, leave: true });
        await p.goto(`${B}/game#battle`); await p.waitForTimeout(1200); await shot('g-result', true);
      }
    }
  }
  if (errs.length) findings.push({ view: vn, screen: '*', kind: '스크립트 오류', detail: [...new Set(errs)].join(' / ').slice(0, 400) });
  await ctx.close();
}
await b.close();
writeFileSync(`${out}/findings.json`, JSON.stringify(findings, null, 1));
writeFileSync(`${out}/infos.json`, JSON.stringify(infos, null, 1));
console.log('검사 결과', findings.length, '건 →', `${out}/findings.json`, `(참고 ${infos.length}건: infos.json)`);
