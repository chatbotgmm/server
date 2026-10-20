// 3단계 화면 실행 검수(시험 서버 :3999만): 지원서 받기, 하급 모집, 조합(확인 두 번 누르기), 상점 구매, 탐험 출발 → 탐험 중 화면
// 사용: node scripts/web/actions-review.mjs <결과 폴더> [폭] [높이]
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out = process.argv[2], w = +process.argv[3] || 1440, h = +process.argv[4] || 900;
const b = await chromium.launch(); const c = await b.newContext({ viewport: { width: w, height: h } }); const p = await c.newPage(); p.setDefaultTimeout(10000);
const errs = []; p.on('response', r => { if (r.status() >= 500) errs.push(`${r.status()} ${new URL(r.url()).pathname}`); }); p.on('pageerror', e => errs.push(e.message));
await p.goto('http://127.0.0.1:3999/login?next=/game'); await p.fill('#code', '135790'); await p.click('.code button'); await p.waitForURL(u => new URL(u).pathname === '/game');
const text = async s => (await p.locator(s).innerText().catch(() => '-')).replace(/\s+/g, ' ').slice(0, 160);
// 지원서 받기
await p.goto('http://127.0.0.1:3999/game#draw'); await p.waitForSelector('[data-claim]');
console.log('claim 전:', await text('[data-claimtext]'));
if (!(await p.locator('[data-claim]').isDisabled())) { await p.click('[data-claim]'); await p.waitForTimeout(1500); console.log('claim 후:', await text('#toast'), '|', await text('[data-claimtext]')); }
// 조합: 가능한 첫 조합식, 확인 단계가 있으면 두 번
await p.goto('http://127.0.0.1:3999/game#craft'); await p.waitForSelector('[data-go]');
const before = await text('[data-why]'); await p.click('[data-go]'); console.log('조합 1번째 누름:', before, '→', await text('[data-go]'), '|', await text('[data-why]'));
if ((await text('[data-go]')).includes('재료를 써서')) await p.click('[data-go]');
await p.waitForSelector('.srv.done', { timeout: 10000 }).catch(() => {}); console.log('조합 결과:', await text('.srv')); await p.screenshot({ path: `${out}/act-craft.png` });
// 상점: 치료약 1개
await p.waitForTimeout(2500); await p.goto('http://127.0.0.1:3999/game#shop'); await p.waitForSelector('.good');
await p.locator('.good', { hasText: '치료약' }).first().click(); await p.click('[data-go]'); await p.waitForTimeout(1200); console.log('상점:', await text('[data-out]'), '|', await text('[data-why]')); await p.screenshot({ path: `${out}/act-shop.png` });
// 탐험: 후보 2마리 → 출발 → 탐험 중 화면
await p.goto('http://127.0.0.1:3999/game#explore'); await p.waitForTimeout(800);
if (await p.$('[data-left]')) console.log('이미 탐험 중:', await text('[data-left]'));
else { await p.click('.ex-grid [data-i="0"]'); await p.click('.ex-grid [data-i="1"]'); console.log('시너지:', await text('[data-syn]')); await p.click('[data-go]'); await p.waitForTimeout(1500); console.log('출발:', await text('[data-out]'), '|', await text('[data-why]')); await p.waitForTimeout(2500); }
await p.screenshot({ path: `${out}/act-explore.png` }); console.log('탐험 화면:', await text('.explore'));
console.log('5xx·스크립트 오류', errs); await b.close();
