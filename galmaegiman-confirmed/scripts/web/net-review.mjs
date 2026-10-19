// 사용: node scripts/web/net-review.mjs <결과 폴더>   (시험 서버 :3999만)
// 느린 응답·연결 끊김 검사(시험 서버만): 재화·뒤로 버튼이 「불러오는 중」으로 덮이지 않는지, 제출 전 성공 표시가 없는지, 끊김 뒤 복구
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out=process.argv[2];
const b=await chromium.launch();const c=await b.newContext({viewport:{width:915,height:412}});const p=await c.newPage();p.setDefaultTimeout(10000);
const api=(path,body)=>p.evaluate(async([path,body])=>{const r=await fetch('/my/api/'+path,{method:body?'POST':'GET',headers:{'x-gmm':'1','content-type':'application/json'},body:body?JSON.stringify(body):undefined});return r.json();},[path,body]);
await p.goto('http://127.0.0.1:3999/login?next=/game');await p.fill('#code','135790');await p.click('.code button');await p.waitForURL(u=>new URL(u).pathname==='/game');await p.waitForTimeout(1500);
// 1) 느린 state(3초): 첫 화면 동안 머리줄
await c.route('**/my/api/native/state',async r=>{await new Promise(x=>setTimeout(x,3000));await r.continue();});
await p.goto('http://127.0.0.1:3999/game#team');await p.waitForTimeout(800);await p.screenshot({path:`${out}/net1-slow-state.png`});
console.log('slow state: money=',await p.locator('#money').innerText(),'| back=',await p.locator('#back').isVisible(),'| view=',(await p.locator('#view').innerText()).slice(0,40));
await p.waitForTimeout(3500);console.log('after: money=',(await p.locator('#money').innerText()).replace(/\n/g,' '));
await c.unroute('**/my/api/native/state');
// 2) 느린 편성 저장(2.5초): 저장 중 표시, 성공 토스트는 응답 뒤
await c.route('**/my/api/native/team',async r=>{await new Promise(x=>setTimeout(x,2500));await r.continue();});
await p.goto('http://127.0.0.1:3999/game#team');await p.waitForSelector('.team-grid [data-i]');
await p.click('.slots [data-s="2"]');await p.locator('.team-grid .reg-card').filter({hasNotText:'슬롯'}).first().click();await p.click('[data-go]');await p.waitForTimeout(500);
console.log('saving: btn=',await p.locator('[data-go]').innerText(),'| toast visible=',await p.locator('#toast').isVisible());
await p.screenshot({path:`${out}/net2-saving.png`});await p.waitForTimeout(3000);
console.log('saved: toast=',await p.locator('#toast').innerText().catch(()=>'-'),'| why=',await p.locator('[data-why]').innerText());
await c.unroute('**/my/api/native/team');
// 3) 스토리 전투 중 연결 끊김 → 공격 → 오류 안내, 다시 연결 → 상태 다시 받기
const cur=await api('native/battle/current');if(cur.session)await api('native/battle/turn',{session:cur.session,revision:cur.revision,leave:true});
const st=await api('native/battle/start',{mode:'story',stage:'P-1'});console.log('start',st.error||'ok');
await p.goto('http://127.0.0.1:3999/game#battle');await p.waitForSelector('.a-card');await p.click('.a-card');await p.click('[data-ok]');await p.waitForSelector('.battle .cmd');
await c.setOffline(true);await p.click('[data-p="attack"]');await p.click('[data-skill]:not([disabled])');await p.waitForTimeout(800);
console.log('offline: note=',await p.locator('.cmd .err').innerText(),'| pane=',await p.locator('[data-pane-title]').innerText());await p.screenshot({path:`${out}/net3-offline.png`});
await c.setOffline(false);await p.waitForTimeout(4500);console.log('online again: pane=',await p.locator('[data-pane-title]').innerText(),'| note=',await p.locator('.cmd .err').innerText());await p.screenshot({path:`${out}/net4-online.png`});
const c2=await api('native/battle/current');if(c2.session)await api('native/battle/turn',{session:c2.session,revision:c2.revision,leave:true});
await b.close();
