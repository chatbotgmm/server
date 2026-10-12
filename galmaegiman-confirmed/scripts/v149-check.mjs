// v0.14.9 점검: 첫 화면 끌기, 부 화면 뒤로(오른쪽 아래), 잘린 악당 그림 가장자리, 아이템 창, 도망 판. 사용: node scripts/v149-check.mjs <결과 폴더> <글꼴 폴더>
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
await p.goto('http://localhost:3999/my?next=duel&g=1');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/);await p.waitForSelector('.t2');await p.waitForTimeout(800);
const api=(path,body)=>p.evaluate(async([path,body])=>{const r=await fetch('/my/api/'+path,body===undefined?{headers:{'x-gmm':'1'}}:{method:'POST',headers:{'content-type':'application/json','x-gmm':'1'},body:JSON.stringify(body)});return {s:r.status,j:await r.json()};},[path,body]);
// 1. 첫 화면 끌기
const lay=()=>p.evaluate(()=>[...document.querySelectorAll('.t2 .t2bg,.t2 .ch')].map(e=>getComputedStyle(e).translate));
const fixed=()=>p.evaluate(()=>['.t2 img[alt="갈매미맨 수비대"]','#tgo','#tacc','#tset'].map(s=>{const r=document.querySelector(s)?.getBoundingClientRect();return r?[r.x,r.y].map(Math.round).join(','):'-'}).join('|'));
const f0=await fixed();await p.mouse.move(w*0.5,h*0.55);await p.mouse.down();await p.mouse.move(w*0.8,h*0.7,{steps:10});await p.waitForTimeout(150);
const l=await lay();await p.screenshot({path:`${out}/${n}-title-drag.png`});
R(n+' 첫 화면 끌면 배경·캐릭터 서로 다르게',new Set(l).size>=4&&!l.includes('none'),JSON.stringify(l));R(n+' 로고·버튼·계정 고정',(await fixed())===f0);
await p.mouse.up();await p.waitForTimeout(800);R(n+' 놓으면 제자리',(await lay()).every(x=>x==='0px'||x==='0% 0%'||x==='0px 0px'||x==='none'),JSON.stringify(await lay()));
// 2. 진행: 1-6 열기
const orig=(await api('story')).j.data;const data=JSON.parse(JSON.stringify(orig));data.cleared=[...new Set([...(data.cleared||[]),'P-1','1-1','1-2','1-3','1-4','1-5'])];data.hp={};await api('story',{data});
await p.reload();await p.waitForSelector('.t2');await p.click('#tgo');await p.waitForSelector('.home8');await p.waitForTimeout(800);await p.click('#go',{force:true});await p.waitForSelector('.modes');await p.click('#mStory');await p.waitForSelector('.schap');
await p.click('.pcard[data-p="1"]');await p.waitForSelector('.stage');await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-part.png`});
R(n+' 부 화면 뒤로: 오른쪽 아래·「‹ 뒤로」',await p.evaluate(()=>{const e=document.querySelector('#bk'),r=e.getBoundingClientRect();return e.textContent.trim()==='‹ 뒤로'&&innerWidth-r.right<60&&innerHeight-r.bottom<40&&!document.querySelector('header #bk');}));
R(n+' 부 화면 스테이지 안 가림·넘침 없음',await p.evaluate(()=>{const bk=document.querySelector('#bk').getBoundingClientRect(),l=document.querySelector('.stl');return l.scrollHeight<=l.clientHeight+1&&[...document.querySelectorAll('.stage')].every(s=>{const r=s.getBoundingClientRect();return r.bottom<=bk.top+1||r.right<=bk.left+1;});}));
await p.click('[data-s="1-6"]');for(let i=0;i<60&&!(await p.$('.aug'));i++){await p.click('.dbox').catch(()=>{});await p.waitForTimeout(150);}
await p.click('.aug');await p.click('#ok');await p.waitForSelector('.bmenu');await p.waitForTimeout(2000);await p.screenshot({path:`${out}/${n}-boss.png`});
R(n+' 쭈라켄 새 그림(가장자리 처리 없음)',await p.evaluate(()=>!document.getElementById('m1').style.maskImage));
// 3. 아이템 창
await p.click('#bag');await p.waitForSelector('.sheet.items');await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-items.png`});
R(n+' 아이템 창 한 화면·버튼 보임',await p.evaluate(()=>{const s=document.querySelector('.sheet.items'),u=document.querySelector('#iu').getBoundingClientRect();return s.scrollHeight<=s.clientHeight+1&&u.bottom<=innerHeight;}));
await p.click('#ix');
// 4. 도망
await p.click('#flee');await p.waitForSelector('.gdlg');await p.click('.gdlg-ok');await p.waitForSelector('.fled');await p.waitForTimeout(700);await p.screenshot({path:`${out}/${n}-fled.png`});
R(n+' 도망 판 넘침 없음·버튼 보임',await p.evaluate(()=>{const pn=document.getElementById('panel'),r=document.querySelector('#fr').getBoundingClientRect();return pn.scrollHeight<=pn.clientHeight+1&&r.bottom<=innerHeight;}));
await p.click('#fb');await p.waitForSelector('.story');R(n+' 스토리로',true);
await api('story',{data:{...orig,hp:{}}});
R(n+' 오류 없음',!errs.length,JSON.stringify(errs));await c.close();}
await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
