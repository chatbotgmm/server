// 가로 넘김 점검(v0.14.6): 출석 칸 글자, 스토리 장 가로 넘김, 팀 편성 가로 넘김, 뽑기 결과 한 줄. 사용: node scripts/scroll-check.mjs <결과 폴더> <게임 열쇠> <글꼴 폴더>
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const [out,K,F]=process.argv.slice(2);
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
const css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');
const b=await chromium.launch();let bad=0;const R=(n,t,ok,x='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${n} ${t} ${x}`);};
for(const [n,w,h] of [['Z',1006,411],['S',780,360],['W',1280,720]]){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:2,serviceWorkers:'block',hasTouch:true});
  await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
  await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
  const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
  await p.goto(`http://localhost:3999/my?next=duel&g=1`);await p.waitForSelector('#code');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/,{timeout:15000});await p.waitForSelector('.t2');await p.waitForTimeout(600);
  await p.click('#tgo');await p.waitForSelector('.home8');await p.waitForTimeout(800);
  // 출석
  await p.click('#td');await p.waitForSelector('.sheet.today .att-week');await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-att.png`});
  R(n,'출석 칸 글자 칸 안',await p.evaluate(()=>[...document.querySelectorAll('.att-d')].every(d=>{const r=d.getBoundingClientRect();return [...d.querySelectorAll('.as,em,small')].every(e=>{const q=e.getBoundingClientRect();return q.left>=r.left-0.5&&q.right<=r.right+0.5&&e.scrollWidth<=e.clientWidth+1;});})));
  await p.click('#tdX');await p.waitForTimeout(300);
  // 스토리
  await p.click('#go',{force:true});await p.waitForSelector('.modes');await p.click('#mStory');await p.waitForSelector('.schap');await p.waitForTimeout(900);
  await p.screenshot({path:`${out}/${n}-story.png`});
  R(n,'스토리 가로 넘김·세로 넘침 없음',await p.evaluate(()=>{const s=document.querySelector('.schap');return {h:s.scrollWidth>s.clientWidth,v:s.scrollHeight<=s.clientHeight+1,stl:0,page:document.documentElement.scrollHeight<=innerHeight+1};}).then(r=>(console.log('   ',JSON.stringify(r)),r.h&&r.v&&r.page)));
  R(n,'스토리 왼쪽 팀 칸 안 넘침·편성 버튼 위 막대',await p.evaluate(()=>{const a=document.querySelector('.steam'),e=document.querySelector('#ed').getBoundingClientRect();return a.scrollHeight<=a.clientHeight+1&&e.top<80;}));
  R(n,'처음엔 부 버튼만(스테이지 안 보임)',await p.evaluate(()=>document.querySelectorAll('.pcard').length>=3&&!document.querySelector('.stage')));
  await p.evaluate(()=>{const s=document.querySelector('.schap');s.scrollLeft=s.scrollWidth;});await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-story-end.png`});
  await p.evaluate(()=>{document.querySelector('.schap').scrollLeft=0;});await p.click('.pcard[data-p="1"]');await p.waitForSelector('.schap.part');await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-part.png`});
  R(n,'부 누르면 그 부 스테이지·넘침 없음',await p.evaluate(()=>{const l=document.querySelector('.stl');return document.querySelectorAll('.stage').length>0&&l.scrollHeight<=l.clientHeight+1&&document.documentElement.scrollHeight<=innerHeight+1;}));
  await p.click('#bk');await p.waitForSelector('.schap.parts');R(n,'‹ 부 목록 → 부 목록으로',!!(await p.$('.pcard')));
  await p.click('#ed');await p.waitForTimeout(1500);await p.screenshot({path:`${out}/${n}-pick.png`});
  R(n,'편성: 가로 넘김·세로 넘침 없음·카드 안 잘림·버튼 보임',await p.evaluate(()=>{const g=document.querySelector('.grid.pickrow'),go=document.querySelector('#go').getBoundingClientRect();const cut=[...g.querySelectorAll('.card')].slice(0,8).filter(c=>c.scrollHeight>c.clientHeight+1||c.getBoundingClientRect().bottom>g.getBoundingClientRect().bottom+1).length;const r={h:g.scrollWidth>g.clientWidth,v:g.scrollHeight>g.clientHeight+1,page:document.documentElement.scrollHeight>innerHeight+1,cut,go:go.bottom<=innerHeight,cw:getComputedStyle(g).getPropertyValue('--cw'),rows:getComputedStyle(g).getPropertyValue('--rows')};console.log(JSON.stringify(r));return r.h&&!r.v&&!r.page&&!cut&&r.go;}));
  await p.click('.grid.pickrow .card:nth-child(5)');await p.waitForTimeout(200);
  console.log('   편성 화면 세로 넘침',await p.evaluate(()=>[...document.querySelectorAll('*')].filter(e=>{const cs=getComputedStyle(e);return /(auto|scroll)/.test(cs.overflowY)&&e.scrollHeight>e.clientHeight+1;}).map(e=>e.className||e.tagName).slice(0,5)));
  await p.goto(`http://localhost:3999/duel?k=${K}`);await p.waitForSelector('.t2');await p.click('#tgo');await p.waitForSelector('.home8');await p.waitForTimeout(500);
  // 뽑기 결과 많이
  await p.click('.h8menu a[data-h="draw"]');const mf=p.frameLocator('iframe').first();await mf.locator('[data-di]').first().waitFor({timeout:15000});
  await mf.locator('[data-di]').first().fill('26');await mf.locator('[data-di]').first().dispatchEvent('input');await p.waitForTimeout(300);
  await mf.locator('.dgo').first().click();await mf.locator('.rv-egg').waitFor();await mf.locator('.rv-egg').click();await p.waitForTimeout(1500);await p.waitForTimeout(1800);R(n,'모두 열기 버튼 없음',!(await mf.locator('#rvAll').count()));
  await p.screenshot({path:`${out}/${n}-reveal.png`});
  const fr=p.frames().find(f=>f.url().includes('/my'));
  const r=await fr.evaluate(()=>{const row=document.querySelector('.reveal .rv-cards'),cs=[...row.querySelectorAll('.rv-c')];const tops=new Set(cs.map(c=>Math.round(c.getBoundingClientRect().top)));
    const names=[...row.querySelectorAll('.rv-front b')].filter(b=>b.scrollWidth>b.clientWidth+1).length;
    const btn=document.querySelector('#rvOk').getBoundingClientRect();
    return {n:cs.length,rows:tops.size,hscroll:row.scrollWidth>row.clientWidth,vscroll:row.scrollHeight>row.clientHeight+1||document.documentElement.scrollHeight>innerHeight+1,nameOver:names,cw:Math.round(cs[0].getBoundingClientRect().width),btnIn:btn.bottom<=innerHeight};});
  R(n,'뽑기 결과 한 줄·가로 넘김·세로 스크롤 없음·이름 안 삐져나옴·버튼 보임',r.rows===1&&r.hscroll&&!r.vscroll&&!r.nameOver&&r.btnIn,JSON.stringify(r));
  await fr.evaluate(()=>{const row=document.querySelector('.reveal .rv-cards');row.scrollLeft=row.scrollWidth/2;});await p.waitForTimeout(300);await p.screenshot({path:`${out}/${n}-reveal-mid.png`});
  R(n,'오류 없음',!errs.length,JSON.stringify(errs));await c.close();
}
await b.close();console.log(bad?`✗ ${bad}`:'✓ 모두 통과');
