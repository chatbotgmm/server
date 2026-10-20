// 스토리·성장·회복 검수(시험 서버 :3999만): 스토리 단계 화면, 대사 보기, 성장 시트, 회복 시트를 찍고 한 번씩 실행해 본다
// 사용: node scripts/web/story-review.mjs <결과 폴더> [폭] [높이]
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out = process.argv[2], w = +process.argv[3] || 1440, h = +process.argv[4] || 900, B = 'http://127.0.0.1:3999', tag = `${w}x${h}`;
const b = await chromium.launch(); const c = await b.newContext({ viewport: { width: w, height: h } }); const p = await c.newPage(); p.setDefaultTimeout(10000);
const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('response', r => { if (r.status() >= 500) errs.push(`${r.status()} ${new URL(r.url()).pathname}`); });
await p.goto(`${B}/login?next=/game`); await p.fill('#code', '135790'); await p.click('.code button'); await p.waitForURL(u => new URL(u).pathname === '/game');
const api = (path, body) => p.evaluate(async ([path, body]) => { const r = await fetch('/my/api/' + path, { method: body ? 'POST' : 'GET', headers: { 'x-gmm': '1', 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined }); return r.json(); }, [path, body]);
const cur = await api('native/battle/current'); if (cur.session) await api('native/battle/turn', { session: cur.session, revision: cur.revision, leave: true });
const ready = () => p.waitForFunction(() => window.GMM && window.GMM.state && !window.GMM.loading.size, null, { timeout: 15000 });
await p.goto(`${B}/game#story`); await ready(); await p.waitForTimeout(800); await p.screenshot({ path: `${out}/story-${tag}.png` });
console.log('단계 수', await p.locator('.stage-row').count(), '| 시작 버튼', await p.locator('[data-go]').isDisabled() ? '비활성' : '활성', '|', await p.locator('[data-why]').innerText());
await p.click('.chtabs [data-ch="1"]'); await p.waitForTimeout(300); await p.screenshot({ path: `${out}/story-locked-${tag}.png` });
await p.click('.chtabs [data-ch="0"]'); await p.waitForTimeout(300);
if (!(await p.locator('[data-go]').isDisabled())) { await p.click('[data-go]'); await p.waitForSelector('.talk'); await p.waitForTimeout(300); await p.screenshot({ path: `${out}/dialog-${tag}.png` }); console.log('대사:', (await p.locator('.talk').innerText()).replace(/\s+/g, ' ').slice(0, 80)); await p.click('[data-skip]'); await p.waitForSelector('.augment,.battle', { timeout: 10000 }); console.log('대사 뒤 전투 시작: 예'); }
const c2 = await api('native/battle/current'); if (c2.session) await api('native/battle/turn', { session: c2.session, revision: c2.revision, leave: true });
await p.goto(`${B}/game#home`); await ready(); await p.goto(`${B}/game#team`); await ready(); await p.waitForTimeout(800);
await p.screenshot({ path: `${out}/team-acts-${tag}.png` });
if (await p.$('[data-grow]')) { await p.click('[data-grow]'); await p.waitForSelector('#sheet:not([hidden])'); await p.waitForTimeout(300); await p.screenshot({ path: `${out}/growth-${tag}.png` }); console.log('성장 시트:', (await p.locator('#sheet .sheet-body').innerText()).replace(/\s+/g, ' ').slice(0, 140)); await p.keyboard.press('Escape'); }
if (await p.$('[data-heal]')) { await p.click('[data-heal]'); await p.waitForSelector('#sheet:not([hidden])'); await p.waitForTimeout(300); await p.screenshot({ path: `${out}/heal-${tag}.png` }); const hb = p.locator('[data-h]:not([disabled])').first(); if (await hb.count()) { await hb.click(); await p.waitForTimeout(1500); console.log('회복 뒤:', await p.locator('#toast').innerText().catch(() => '-')); } }
else console.log('회복 버튼 없음(체력 가득)');
console.log('오류', errs); await b.close();
