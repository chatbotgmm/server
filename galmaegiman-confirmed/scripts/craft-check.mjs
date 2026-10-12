// 게임 조합 화면(v0.15.0) 한 화면·섞기 점검. 사용: node scripts/craft-check.mjs <결과 폴더> <글꼴 폴더>
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
await p.click('.h8menu a[data-h="craft"]');const mf=p.frameLocator('iframe').first();await mf.locator('.cr2').waitFor({timeout:15000});await p.waitForSelector('.myov.ready');await p.waitForTimeout(1500);
const fr=p.frames().find(f=>f.url().includes('/my'));fr.on?.('pageerror',e=>errs.push(e.message));
const chk=()=>fr.evaluate(()=>{const de=document.documentElement,c=document.querySelector('.cr2').getBoundingClientRect(),btn=document.querySelector('#doCraft,#mgo')?.getBoundingClientRect();return {page:de.scrollHeight>innerHeight+1,inView:c.bottom<=innerHeight+1,btn:btn?btn.bottom<=innerHeight:null,list:(e=>e?e.scrollHeight>e.clientHeight:null)(document.querySelector('.crlist,.crpool'))};});
await p.screenshot({path:`${out}/${n}-craft.png`});R(n+' 조합 한 화면',(r=>!r.page&&r.inView&&r.btn)(await chk()),JSON.stringify(await chk()));
await mf.locator('#conly').click();await p.waitForTimeout(500);await mf.locator('.cri .no').first().locator('..').click().catch(()=>{});await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-craft-all.png`});
await mf.locator('[data-v="mix"]').click();await p.waitForTimeout(600);await mf.locator('.crp:not([disabled])').nth(0).click();await mf.locator('.crp:not([disabled])').nth(1).click();await p.waitForTimeout(400);
await p.screenshot({path:`${out}/${n}-mix.png`});R(n+' 섞기 한 화면',(r=>!r.page&&r.inView&&r.btn)(await chk()),JSON.stringify(await chk()));
R(n+' 섞기 2마리 → 버튼 켜짐',await fr.evaluate(()=>!document.querySelector('#mgo').disabled));
R(n+' 오류 없음',!errs.length,JSON.stringify(errs));await c.close();}
await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
