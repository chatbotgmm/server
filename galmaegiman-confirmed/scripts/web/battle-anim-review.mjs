// 웹 전투 연출 검수(시험 서버 :3999만): 스토리 전투에서 증강체를 고른 뒤 공격 1회, 연출 도중 장면을 시간별로 찍는다
// 사용: node scripts/web/battle-anim-review.mjs <결과 폴더> [폭] [높이]
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out = process.argv[2], w = +process.argv[3] || 1440, h = +process.argv[4] || 900, B = 'http://127.0.0.1:3999';
const b = await chromium.launch(); const c = await b.newContext({ viewport: { width: w, height: h } }); const p = await c.newPage(); p.setDefaultTimeout(10000);
const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('response', r => { if (r.status() >= 500) errs.push(`${r.status()} ${new URL(r.url()).pathname}`); });
await p.goto(`${B}/login?next=/game`); await p.fill('#code', '135790'); await p.click('.code button'); await p.waitForURL(u => new URL(u).pathname === '/game');
const api = (path, body) => p.evaluate(async ([path, body]) => { const r = await fetch('/my/api/' + path, { method: body ? 'POST' : 'GET', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined }); return r.json(); }, [path, body]);
const cur = await api('native/battle/current'); if (cur.session) await api('native/battle/turn', { session: cur.session, revision: cur.revision, leave: true });
const st = await api('native/state'); for (const k of st.team || []) { const u = st.units.find(x => x.key === k); if (u && u.hp < u.maxHp * 0.5) await api('native/heal', { key: k, item: 'big' }); }
const start = await api('native/battle/start', { mode: 'story', stage: 'P-1' }); if (start.error) { console.log('시작 실패', start.error); process.exit(1); }
await p.goto(`${B}/game#battle`); await p.waitForSelector('.a-card'); await p.click('.a-card'); await p.click('[data-ok]');
await p.waitForSelector('.battle .cmd'); await p.waitForTimeout(150); await p.screenshot({ path: `${out}/anim-0-augment-banner.png` });
await p.waitForTimeout(2500);
await p.click('[data-p="attack"]'); await p.click('[data-skill]:not([disabled])');
for (const [i, ms] of [[1, 250], [2, 300], [3, 350], [4, 500], [5, 700]]) { await p.waitForTimeout(ms); await p.screenshot({ path: `${out}/anim-${i}.png` }); }
console.log('재생 중 표시:', await p.evaluate(() => document.querySelector('.stage')?.classList.contains('playing')));
await p.waitForTimeout(3000); await p.screenshot({ path: `${out}/anim-9-final.png` });
console.log('최종 기록:', (await p.locator('.log').innerText()).slice(0, 120), '| 명령 패널:', await p.locator('[data-pane-title]').innerText());
const c2 = await api('native/battle/current'); if (c2.session) await api('native/battle/turn', { session: c2.session, revision: c2.revision, leave: true });
console.log('오류', errs); await b.close();
