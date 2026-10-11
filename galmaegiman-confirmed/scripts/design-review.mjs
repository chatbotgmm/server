// 디자인 검토용 캡처(빌드 전에 꼭): 가로(휴대폰·큰 화면) × 첫 화면(로그인 전·후)·로비·대사·전투, 화면 넘침·상태 칸 겹침 검사
// 사용: node scripts/design-review.mjs <결과 폴더> <게임 열쇠> [글꼴 폴더]  (시험 서버 :3999, 심사 코드 135790 로그인 필요)
//   [글꼴 폴더](기본 <결과 폴더>/fonts)에 @fontsource/black-han-sans·gowun-dodum·bagel-fat-one 패키지가 있어야 함(없으면 멈춤: 기본 글꼴로 찍으면 검토가 틀려져서)
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const out=process.argv[2],K=process.argv[3],F=process.argv[4]||out+'/fonts';
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
let css=null;try{css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');}
catch{console.error('글꼴 패키지가 없어요: '+F+' — 실제 게임 글꼴이 아니면 검토가 틀려지니 멈춰요(세 번째 인자로 글꼴 폴더를 주세요)');process.exit(1);}
const b=await chromium.launch();
for(const [n,w,h] of [['L',915,412],['S',780,360],['W',1280,720]]){   // 게임은 가로만(앱 가로 고정): 휴대폰 가로·큰 화면 가로
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:2,serviceWorkers:'block'});   // 서비스 워커가 글꼴을 직접 받으면 아래 가로채기가 안 먹어서 막음
  if(css)await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
  if(css)await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
  const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
  const ov=()=>p.evaluate(()=>({y:document.documentElement.scrollHeight>innerHeight+1,x:document.documentElement.scrollWidth>innerWidth+1}));
  await p.goto(`http://localhost:3999/duel?k=${K}`);await p.waitForSelector('.title');await p.evaluate(async()=>{for(const f of ['400 100px "Bagel Fat One"','400 40px "Black Han Sans"','400 20px "Gowun Dodum"'])await document.fonts.load(f,'갈매미맨 수비대 항구를 지켜라 게임 시작 로그인 이어하기 스토리 자유 대결');await document.fonts.ready;});await p.waitForTimeout(1500);
  await p.screenshot({path:`${out}/R${n}-1title-out.png`});console.log(n,'첫 화면(로그인 전)',JSON.stringify(await ov()));
  await p.click('a.tgo',{force:true});await p.waitForSelector('#code');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/,{timeout:15000});
  await p.waitForSelector('.title');await p.evaluate(async()=>{for(const f of ['400 100px "Bagel Fat One"','400 40px "Black Han Sans"','400 20px "Gowun Dodum"'])await document.fonts.load(f,'갈매미맨 수비대 항구를 지켜라 게임 시작 로그인 이어하기 스토리 자유 대결');await document.fonts.ready;});await p.waitForTimeout(1500);
  await p.screenshot({path:`${out}/R${n}-2title-in.png`});console.log(n,'첫 화면(로그인 뒤)',JSON.stringify(await ov()));
  await p.click('#tset');await p.waitForSelector('.sheet.set');await p.waitForTimeout(500);await p.screenshot({path:`${out}/R${n}-2b-settings.png`});console.log(n,'설정 창 안 스크롤',await p.evaluate(()=>{const e=document.querySelector('.sheet.set');return e.scrollHeight>e.clientHeight+1;}));await p.click('#sX');
  await p.click('#tgo',{force:true});await p.waitForSelector('.ts');await p.waitForTimeout(1200);
  await p.screenshot({path:`${out}/R${n}-3lobby.png`});console.log(n,'로비',JSON.stringify(await ov()));
  await p.click('.dock a[data-h="draw"]');const mf=p.frameLocator('.myov iframe');await mf.locator('.gbar').waitFor({timeout:15000});await p.waitForTimeout(1800);
  await p.screenshot({path:`${out}/R${n}-3c-my.png`});console.log(n,'내 갈매미',JSON.stringify(await ov()));
  await mf.locator('#gback').click();await p.waitForSelector('.myov',{state:'detached'});await p.waitForSelector('.ts');await p.waitForTimeout(800);
  await p.click('#td');await p.waitForSelector('.sheet.today .att-week');await p.waitForTimeout(500);await p.screenshot({path:`${out}/R${n}-3f-attend.png`});console.log(n,'출석체크 창 안 스크롤',await p.evaluate(()=>{const e=document.querySelector('.sheet.today');return e.scrollHeight>e.clientHeight+1;}));
  await p.click('#tdX');await p.waitForSelector('.ts.lobby');await p.waitForTimeout(600);
  await p.click('#go',{force:true});await p.waitForSelector('.modes');await p.waitForTimeout(1200);await p.screenshot({path:`${out}/R${n}-3g-modes.png`});console.log(n,'모드 고르기',JSON.stringify(await ov()),'칸 넘침',await p.evaluate(()=>[...document.querySelectorAll('.mcard')].some(e=>e.scrollHeight>e.clientHeight+1)));
  await p.click('#mStory',{force:true});await p.waitForTimeout(1200);if(await p.$('#rnd')){await p.click('#rnd');await p.click('#go');await p.waitForTimeout(1200);}
  await p.waitForSelector('.dbox');await p.waitForTimeout(1800);await p.screenshot({path:`${out}/R${n}-3b-talk.png`});console.log(n,'대사',JSON.stringify(await ov()));
  for(let i=0;i<40 && !(await p.$('.aug'));i++){await p.click('.dbox').catch(()=>{});await p.waitForTimeout(200);}
  await p.waitForTimeout(500);await p.screenshot({path:`${out}/R${n}-3d-aug.png`});console.log(n,'증강체',JSON.stringify(await ov()),'창 안 스크롤',await p.evaluate(()=>{const e=document.querySelector('#modal .sheet');return e?e.scrollHeight>e.clientHeight+1:null;}));
  if(await p.$('[data-r]')){await p.click('[data-r]');await p.waitForTimeout(500);await p.screenshot({path:`${out}/R${n}-3e-reroll.png`});}
  await p.click('.aug');await p.click('#ok');await p.waitForSelector('.sk');await p.waitForTimeout(2200);
  await p.screenshot({path:`${out}/R${n}-4battle.png`});console.log(n,'전투',JSON.stringify(await ov()));
  // 겹침 검사: 상태 칸과 캐릭터 그림
  console.log(n,'겹침',JSON.stringify(await p.evaluate(()=>{const r=id=>document.getElementById(id).getBoundingClientRect();const o=(a,b)=>!(a.right<b.left||b.right<a.left||a.bottom<b.top||b.bottom<a.top);return {내칸_상대그림:o(r('b0'),r('m1')),상대칸_상대그림:o(r('b1'),r('m1'))};})));
  console.log(n,'errors',JSON.stringify(errs));await c.close();
}
await b.close();
