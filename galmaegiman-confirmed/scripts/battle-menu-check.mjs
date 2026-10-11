// 전투 행동 4개(공격·아이템·교체·도망) 점검(v0.14.8). 사용: node scripts/battle-menu-check.mjs <결과 폴더> <글꼴 폴더>
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
await p.goto('http://localhost:3999/my?next=duel&g=1');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/);await p.waitForSelector('.t2');
await p.click('#tgo');await p.waitForSelector('.home8');await p.waitForTimeout(800);await p.click('#go',{force:true});await p.waitForSelector('.modes');await p.click('#mList');await p.waitForSelector('.schap');
await p.click('.pcard[data-p="1"]');await p.waitForSelector('.stage');await p.click('.stage:not([disabled])');
for(let i=0;i<40&&!(await p.$('.aug'));i++){await p.click('.dbox').catch(()=>{});await p.waitForTimeout(200);}
await p.click('.aug');await p.click('#ok');await p.waitForSelector('.bmenu');await p.waitForTimeout(2500);
const fit=()=>p.evaluate(()=>{const pn=document.getElementById('panel'),R=pn.getBoundingClientRect();return {cut:[...pn.querySelectorAll('button')].filter(e=>{const r=e.getBoundingClientRect();return r.bottom>R.bottom+1||r.right>R.right+1||r.bottom>innerHeight;}).length,scroll:pn.scrollHeight>pn.clientHeight+1,min:Math.min(...[...pn.querySelectorAll('button')].map(e=>e.getBoundingClientRect().height))};});
await p.screenshot({path:`${out}/${n}-menu.png`});
const m=await p.evaluate(()=>[...document.querySelectorAll('.bmenu .bm b')].map(e=>e.textContent));R(n+' 처음엔 4개만',m.join()==='공격,아이템,교체,도망가기'&&!(await p.$('.sk')),m.join());
R(n+' 4개 판 안·스크롤 없음',(f=>!f.cut&&!f.scroll&&f.min>=40)(await fit()),JSON.stringify(await fit()));
await p.click('#bAtk');await p.waitForSelector('.sk');await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-fight.png`});
R(n+' 공격 → 기술',(await p.$$('.sk')).length>=4);R(n+' 기술 판 잘림 없음',(f=>!f.cut)(await fit()),JSON.stringify(await fit()));
await p.click('#bback');await p.waitForSelector('.bmenu');R(n+' 뒤로 → 4개',true);
await p.click('#bSw');await p.waitForSelector('.swc');await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-swap.png`});
R(n+' 교체 → 갈매미·속성 표시',await p.evaluate(()=>[...document.querySelectorAll('.swc')].every(e=>e.querySelector('.chip')&&e.querySelector('.hpb'))));R(n+' 교체 판 잘림 없음',(f=>!f.cut&&!f.scroll)(await fit()),JSON.stringify(await fit()));
await p.click('.swc:not([disabled])');await p.waitForSelector('.bmenu, #modal:not([hidden]) .sheet',{timeout:20000});R(n+' 교체하면 턴 진행(4개 또는 쓰러진 뒤 고르기)',true);if(!(await p.$('.bmenu'))){console.log('   (교체 뒤 고르기 창이 떠서 아이템 확인은 건너뜀)');await c.close();continue;}
await p.waitForTimeout(500);await p.click('#bag',{timeout:3000}).catch(()=>{});await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-item.png`});
R(n+' 아이템 → 아이템 창',!!(await p.$('#modal:not([hidden]) .sheet')),'');
R(n+' 오류 없음',!errs.length,JSON.stringify(errs));await c.close();}
await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
