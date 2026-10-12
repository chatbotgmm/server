// 편성 옆 속성 상성표(v0.15.2) 점검. 사용: node scripts/typechart-check.mjs <결과 폴더> <글꼴 폴더>
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const [out,F]=process.argv.slice(2);let bad=0;const R=(t,ok,x='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${t} ${x}`);};
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
const css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');
const b=await chromium.launch();
for(const [n,w,h] of [['Z',1006,411],['S',780,360],['W',1280,720]]){
const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:2});
await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
await p.goto('http://localhost:3999/my?next=duel&g=1');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/);await p.waitForSelector('.t2');await p.click('#tgo');await p.waitForSelector('.home8');await p.waitForTimeout(800);
await p.click('.h8menu a[data-h="units"]');const mf=p.frameLocator('iframe').first();await mf.locator('.tabs.gseg').waitFor({timeout:15000});await mf.locator('.tabs.gseg [data-t="battle"]').click();await p.waitForTimeout(1200);
await mf.locator('[data-bv="chart"]').click();await p.waitForTimeout(600);await p.screenshot({path:`${out}/${n}-chart.png`});
const fr=p.frames().find(f=>f.url().includes('/my'));
const r=await fr.evaluate(()=>{const t=document.querySelector('.tchart'),L=document.querySelector('.btleft');const rows=[...t.querySelectorAll('.trow')];return {rows:rows.length,mine:t.querySelectorAll('.trow.mine').length,cut:rows.filter(e=>e.scrollWidth>e.clientWidth+1||e.scrollHeight>e.clientHeight+1||e.clientHeight<20||e.getBoundingClientRect().bottom>L.getBoundingClientRect().bottom).length,sum:document.querySelector('.tsum')?.textContent,sumIn:document.querySelector('.tsum').getBoundingClientRect().bottom<=L.getBoundingClientRect().bottom+1,page:document.documentElement.scrollHeight>innerHeight+1};});
R(n+' 상성표 10줄·잘림 없음·요약 보임',r.rows===10&&!r.cut&&r.sumIn&&!r.page,JSON.stringify(r));
await mf.locator('[data-bv="podium"]').click();await p.waitForTimeout(400);R(n+' 단상으로 돌아감',await fr.evaluate(()=>!!document.querySelector('.podium')));
R(n+' 오류 없음',!errs.length,JSON.stringify(errs));await c.close();}
await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
