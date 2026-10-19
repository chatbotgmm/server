// 실시간 일반전 흐름 검수(시험 서버 :3999만): 탐색 → (30초 사람 없음) AI 방 → 수락 → 준비 → 증강체 → 전투 → 항복 → 결과
// 사용: node scripts/web/realtime-review.mjs <결과 폴더> [폭] [높이]   (심사 계정에 대전 편성 3명이 있어야 함: scripts/verify/review-server.sh)
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out=process.argv[2],[w,h]=[+process.argv[3]||1440,+process.argv[4]||900];
const b=await chromium.launch();const c=await b.newContext({viewport:{width:w,height:h}});const p=await c.newPage();p.setDefaultTimeout(8000);
const errs=[];p.on('response',r=>{if(r.status()>=400&&r.status()!==401)errs.push(r.status()+' '+new URL(r.url()).pathname)});p.on('pageerror',e=>errs.push(e.message));
await p.goto('http://127.0.0.1:3999/login?next=/game');await p.fill('#code','135790');await p.click('.code button');await p.waitForURL(u=>new URL(u).pathname==='/game');
const api=(path,body)=>p.evaluate(async([path,body])=>{const r=await fetch('/my/api/'+path,{method:body?'POST':'GET',headers:{'x-gmm':'1','content-type':'application/json'},body:body?JSON.stringify(body):undefined});return r.json();},[path,body]);
const q=await api('pvp/queue/current');console.log('queue before',q.status,q.roomId||'');
if(q.status==='matched'&&q.roomId){const r=await api('pvp/room/current?roomId='+q.roomId);console.log('room',r.phase,JSON.stringify(r.result));console.log('surrender',(await api('pvp/room/surrender',{roomId:q.roomId,requestId:'x'+Date.now()})).phase);}
if(q.status==='waiting')await api('pvp/queue/cancel',{attemptId:q.attemptId,token:q.token,requestId:'c'+Date.now()});
await p.goto('http://127.0.0.1:3999/game#match?mode=normal');await p.waitForTimeout(3000);await p.screenshot({path:`${out}/rt1-search.png`});
console.log('search', await p.locator('[data-time]').textContent().catch(()=>'-'));
await p.waitForSelector('[data-yes]',{timeout:60000});await p.waitForTimeout(400);await p.screenshot({path:`${out}/rt2-accept.png`});
console.log('accept', await p.locator('.m-card').innerText());
await p.click('[data-yes]');await p.waitForSelector('.team.ready',{timeout:20000});await p.waitForTimeout(800);await p.screenshot({path:`${out}/rt3-prepare.png`});
console.log('prepare why', await p.locator('[data-why]').textContent(), 'timer', await p.locator('[data-timer]').textContent());
await p.click('[data-go]');await p.waitForTimeout(2500);console.log('after prepare errs',errs,await p.locator('.cta').innerText().catch(()=>'-'));await p.waitForSelector('.augment,.battle',{timeout:20000});await p.waitForTimeout(800);await p.screenshot({path:`${out}/rt4-aug.png`});
if(await p.$('.augment')){await p.click('.a-card');await p.click('[data-ok]');}
await p.waitForSelector('.battle .cmd',{timeout:20000});await p.waitForTimeout(1500);await p.screenshot({path:`${out}/rt5-battle.png`});
console.log('cmd', await p.locator('.cmd').innerText());
await p.click('[data-p="attack"]').catch(()=>{});await p.waitForTimeout(300);await p.click('[data-skill]:not([disabled])').catch(()=>{});await p.waitForTimeout(400);await p.screenshot({path:`${out}/rt6-submitted.png`});
await p.waitForTimeout(3500);await p.screenshot({path:`${out}/rt7-next.png`});console.log('after', await p.locator('.cmd').innerText());
await p.click('[data-p="leave"]').catch(()=>{});await p.waitForTimeout(300);await p.screenshot({path:`${out}/rt8-surrender.png`});await p.click('[data-yes]').catch(()=>{});await p.waitForTimeout(2500);await p.screenshot({path:`${out}/rt9-result.png`});console.log('result', await p.locator('.cmd').innerText().catch(()=>'-'));
console.log('errors',errs);await b.close();
