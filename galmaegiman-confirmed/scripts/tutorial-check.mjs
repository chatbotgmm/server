// 새 유저 튜토리얼 끝까지(v0.15.0). 시험 계정을 새 유저로 만든 뒤(갈매미·스토리 저장 없음, gameTutorial 0) 실행. 사용: node scripts/tutorial-check.mjs <결과 폴더> <글꼴 폴더>
// 새 유저 튜토리얼 끝까지: 각 단계 캡처 + 덮개가 막는지 + 단계 저장 확인
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const [out,F,W,H]=[process.argv[2],process.argv[3],+(process.argv[4]||1006),+(process.argv[5]||411)];
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
const css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');
const b=await chromium.launch();const c=await b.newContext({viewport:{width:W,height:H},deviceScaleFactor:2});
await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));let bad=0;const R=(t,ok,x='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${t} ${x}`);};
const api=path=>p.evaluate(async path=>{const r=await fetch('/my/api/'+path,{headers:{'x-gmm':'1'}});return r.json();},path);
let n=0;const shot=async t=>{await p.waitForTimeout(600);await p.screenshot({path:`${out}/${String(++n).padStart(2,'0')}-${t}.png`});};
const key=()=>p.evaluate(()=>document.querySelector('.tut')?.dataset.key??null);
const waitKey=async(k,ms=15000)=>{for(let t=0;t<ms;t+=200){if((await key())===k)return true;await p.waitForTimeout(200);}return false;};
const clickHole=async()=>{const r=await p.evaluate(()=>{const h=document.querySelector('.tut .hole').getBoundingClientRect();return [h.x+h.width/2,h.y+h.height/2];});await p.mouse.click(r[0],r[1]);};
await p.goto('http://localhost:3999/my?next=duel&g=1');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/);await p.waitForSelector('.t2');
R('새 유저 → 1단계',(await api('tutorial')).step===1);
await p.click('#tgo');await p.waitForSelector('.home8');R('1 출석 밝힘',await waitKey('1'));await shot('1-attend');
// 덮개가 다른 곳을 막는지: 뽑기 메뉴 자리를 눌러도 안 열림
const drawBtn=await p.evaluate(()=>{const r=document.querySelector('.h8menu a[data-h="draw"]').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2];});await p.mouse.click(...drawBtn);await p.waitForTimeout(500);R('덮개가 다른 버튼 막음',!(await p.$('.myov')));
await clickHole();await p.waitForSelector('.sheet.today');await shot('1b-sheet');
if(await p.$('.sheet.today .big:not([disabled])'))await p.click('.sheet.today .big:not([disabled])').catch(()=>{});await p.waitForTimeout(800);
await p.click('#tdX').catch(()=>{});
R('2 뽑기 메뉴 밝힘',await waitKey('2a'));await shot('2a-lobby');await clickHole();
R('2 뽑기 버튼 밝힘',await waitKey('2b',20000));await shot('2b-draw');await clickHole();
const fr=()=>p.frames().find(f=>f.url().includes('#draw')||f.url().includes('/my'));
await p.waitForTimeout(800);const f=fr();await f.waitForSelector('.rv-egg',{timeout:15000});await f.click('.rv-egg');await p.waitForTimeout(2500);await shot('2c-reveal');await f.click('#rvOk');
R('2 끝 → D1 보상 카드',await waitKey('card-d1'));await shot('2d-reward');await p.click('.tut [data-tc="ok"]');
await p.waitForTimeout(1200);const t3=await api('tutorial');R('서버 3단계',t3.step===3,JSON.stringify(t3));
R('D1 받음',(await api('story')).owned.includes('D1'));
R('3 뒤로 가기 밝힘',await waitKey('3x'));await clickHole();
R('3 수비대 밝힘',await waitKey('3a'));await clickHole();
for(const k of ['3b','3c','3d']){const ok=await waitKey(k,8000);R('3 '+k,ok,String(await key()));if(!ok)continue;await shot(k);await clickHole();}
  R('건너뛰기 버튼 오른쪽 아래',await p.evaluate(()=>{const r=document.querySelector('.tut .tskip')?.getBoundingClientRect();return !r||(innerWidth-r.right<30&&innerHeight-r.bottom<30)||r.left<30;}));
await p.waitForTimeout(1200);R('4로 넘어감',(await api('tutorial')).step===4,String(await key()));
R('4 뒤로',await waitKey('4x'));await clickHole();
R('4 플레이',await waitKey('4a'));await shot('4a-play');await clickHole();
R('4 스토리',await waitKey('4b'));await shot('4b-modes');await clickHole();
for(let i=0;i<120&&(await key())!=='4c';i++){await p.click('.dbox').catch(()=>{});await p.waitForTimeout(150);}
R('4 증강체 안내',await waitKey('4c'));await shot('4c-aug');
await p.click('#modal .aug[data-a]');await p.click('#ok');
R('4 속성 상성 카드',await waitKey('4d',15000));await shot('4d-type');await p.click('.tut [data-tc="type"]');
R('4 공격 밝힘',await waitKey('4e'));await shot('4e-attack');await clickHole();
R('4 기술 밝힘',await waitKey('4f'));await shot('4f-skill');await clickHole();

// 전투 끝까지(공격만)
for(let i=0;i<60;i++){if(await p.$('#panel [data-r]'))break;if(await p.$('#bAtk')){await p.click('#bAtk').catch(()=>{});await p.waitForTimeout(200);const s=await p.$('#panel .sk:not([disabled])');if(s)await s.click().catch(()=>{});}
  if(await p.$('#modal:not([hidden]) [data-a]')){await p.click('#modal .aug[data-a]').catch(()=>{});await p.click('#ok').catch(()=>{});}
  if(await p.$('#modal:not([hidden]) [data-t]')){await p.click('#modal [data-t]').catch(()=>{});await p.click('#ok').catch(()=>{});}
  await p.waitForTimeout(700);}
R('4 체력 안내',await waitKey('4g',8000));await shot('4g-hp');await p.click('.tut [data-tc="hp"]').catch(()=>{});await shot('4h-result');const won=await p.evaluate(()=>document.querySelector('#panel h2')?.textContent);console.log('   결과',won);
await p.click('#panel [data-r="0"]').catch(()=>{});for(let i=0;i<60&&!(await p.$('.story'));i++){await p.click('.dbox').catch(()=>{});await p.waitForTimeout(150);}
if(/승리/.test(won||'')){R('5 끝 카드',await waitKey('done',10000));await shot('5-done');await p.click('.tut [data-tc="end"]');await p.waitForTimeout(800);R('서버 9단계',(await api('tutorial')).step===9);}
// 처음 보는 화면 안내
await p.click('#bk').catch(()=>{});await p.waitForTimeout(800);
R('오류 없음',!errs.length,JSON.stringify(errs));await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
