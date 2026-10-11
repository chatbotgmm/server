// UI 점검(v0.12.7): 로비 하단 메뉴·출석·편성 화면을 4가지 가로 크기로 찍고 숫자로 검사
// 사용: node scripts/ui-check.mjs <결과 폴더> <게임 열쇠> <글꼴 폴더>  (시험 서버 :3999, 심사 코드 135790)
// 검사: 화면 넘침, 편성 저장 버튼이 화면 안에 다 보이는지, 메뉴 버튼 높이·글자 크기 통일, 알림 점 잘림, 플레이와 메뉴 겹침,
//       누르는 곳 크기(40px 이상), 출석 창 닫기 버튼 보임, 창 안 스크롤
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const out=process.argv[2],K=process.argv[3],F=process.argv[4];
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
const css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');
const b=await chromium.launch();let bad=0;
const R=(n,what,ok,info='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${n} ${what}${info?' '+info:''}`);};
for(const [n,w,h,dpr] of [['S',780,360,2],['L',915,412,2],['W',1280,720,2],['X',1920,1080,1]]){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:dpr,serviceWorkers:'block'});
  await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
  await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
  const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
  await p.goto(`http://localhost:3999/duel?k=${K}`);await p.waitForSelector('.title');
  await p.click('a.tgo',{force:true});await p.waitForSelector('#code');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/,{timeout:15000});
  await p.waitForSelector('.title');await p.click('#tgo',{force:true});await p.waitForSelector('.home8');await p.waitForTimeout(1800);
  await p.screenshot({path:`${out}/${n}-1lobby.png`});
  const lob=await p.evaluate(()=>{
    const r=e=>e.getBoundingClientRect(),bs=[...document.querySelectorAll('.h8menu a')],play=document.querySelector('#go');
    const hs=bs.map(e=>Math.round(r(e).height)),fs=bs.map(e=>{const t=e.querySelector('span,b');return t?parseFloat(getComputedStyle(t).fontSize):null});
    const dots=[...document.querySelectorAll('.home8 .dot')].map(d=>{const a=r(d),p=r(d.parentElement);return {w:Math.round(a.width),out:a.top<0||a.right>innerWidth||a.left<0,dx:Math.round(a.right-p.right),dy:Math.round(a.top-p.top)}});
    const pr=r(play),overlap=bs.some(e=>{const a=r(e);return !(a.right<=pr.left||a.left>=pr.right||a.bottom<=pr.top||a.top>=pr.bottom)});
    const small=[...document.querySelectorAll('.home8 button,.home8 a')].filter(e=>{const a=r(e);return a.width>0&&(a.width<40||a.height<40)}).map(e=>e.id||e.className);
    const stage=document.querySelector('.h8stage b');
    return {hs,fs,dots,overlap,small,stage:stage?{fs:parseFloat(getComputedStyle(stage).fontSize),txt:stage.textContent}:null,x:document.documentElement.scrollWidth>innerWidth+1};
  });
  R(n,'메뉴 높이 통일',new Set(lob.hs).size===1,JSON.stringify(lob.hs));
  R(n,'메뉴 글자 크기 통일(실제 글자)',lob.fs.every(f=>f!==null)&&new Set(lob.fs).size===1,JSON.stringify(lob.fs));
  R(n,'알림 점 같은 크기·위치, 안 잘림',lob.dots.every(d=>!d.out)&&Math.max(...lob.dots.map(d=>d.w))-Math.min(...lob.dots.map(d=>d.w))<=1&&Math.max(...lob.dots.map(d=>d.dx))-Math.min(...lob.dots.map(d=>d.dx))<=1,JSON.stringify(lob.dots));
  R(n,'플레이와 메뉴 안 겹침',!lob.overlap);
  R(n,'누르는 곳 40px 이상',!lob.small.length,JSON.stringify(lob.small));
  R(n,'스테이지 글자 12px 이상',lob.stage&&lob.stage.fs>=12,JSON.stringify(lob.stage));
  R(n,'로비 가로 넘침 없음',!lob.x);
  // 출석·미션
  await p.click('#td');await p.waitForSelector('.sheet.today .att-week');await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-2attend.png`});
  const att=await p.evaluate(()=>{const r=e=>e.getBoundingClientRect(),x=document.querySelector('#tdX'),s=document.querySelector('.sheet.today'),a=x&&r(x);
    return {close:a&&a.top>=0&&a.bottom<=innerHeight&&a.right<=innerWidth,sheetIn:r(s).bottom<=innerHeight+1&&r(s).top>=-1,cls:[...document.querySelectorAll('.att-d')].map(e=>e.className.replace('att-d','').trim()),ms:[...document.querySelectorAll('.msn-r')].map(e=>e.className.replace('msn-r','').trim())}});
  R(n,'출석 창 닫기 보임',att.close);R(n,'출석 창 화면 안',att.sheetIn);
  console.log('  출석 칸 상태',JSON.stringify(att.cls),'미션 상태',JSON.stringify(att.ms));
  await p.click('#tdX');await p.waitForSelector('.home8');await p.waitForTimeout(500);
  // 계정 메뉴
  await p.click('#lprof');await p.waitForSelector('.pmenu');await p.waitForTimeout(500);await p.screenshot({path:`${out}/${n}-3prof.png`});
  await p.mouse.click(w/2,h/2);await p.waitForTimeout(300);
  // 편성
  await p.click('.h8menu a[data-h="units"]');const mf=p.frameLocator('.myov iframe');await mf.locator('.tabs.gseg').waitFor({timeout:15000});
  await mf.locator('.tabs.gseg [data-t="battle"]').click();await p.waitForTimeout(1500);await p.screenshot({path:`${out}/${n}-4battle.png`});
  const fr=p.frames().find(f=>f.url().includes('/my'));
  const bt=await fr.evaluate(()=>{const r=e=>e.getBoundingClientRect(),s=document.querySelector('#save'),a=r(s);
    const tabs=r(document.querySelector('.tabs.gseg')),pod=document.querySelector('.podium')&&r(document.querySelector('.podium'));
    return {saveIn:a.top>=0&&a.bottom<=innerHeight,save:[Math.round(a.top),Math.round(a.bottom),innerHeight],pageScroll:document.scrollingElement.scrollHeight>innerHeight+1,overlapTabs:pod?pod.top<tabs.bottom:false,podCut:[...document.querySelectorAll('.pd')].some(d=>r(d).bottom>r(document.querySelector('.btleft')).bottom+1),
      badges:[...document.querySelectorAll('.grid .u.on')].map(e=>e.querySelector('.selb')?.textContent??null),status:document.querySelector('.savest')?.textContent??null}});
  R(n,'편성 저장 버튼 화면 안에 다 보임',bt.saveIn,JSON.stringify(bt.save));R(n,'편성 페이지 전체 스크롤 없음',!bt.pageScroll);R(n,'탭·단상 안 겹침',!bt.overlapTabs);R(n,'단상 안 잘림',!bt.podCut);
  R(n,'선택 카드 배지(선봉/2번/3번)',bt.badges.length&&bt.badges.every(Boolean),JSON.stringify(bt.badges));R(n,'저장 상태 글',!!bt.status,JSON.stringify(bt.status));
  // 하나 빼서 바뀐 상태
  const first=mf.locator('.grid .u.on').first();if(await first.count()){await first.click();await p.waitForTimeout(400);await p.screenshot({path:`${out}/${n}-5changed.png`});
    console.log('  바꾼 뒤 상태',JSON.stringify(await fr.evaluate(()=>({dis:document.querySelector('#save').disabled,st:document.querySelector('.savest')?.textContent??null}))));}
  R(n,'오류 없음',!errs.length,JSON.stringify(errs));
  await c.close();
}
await b.close();console.log(bad?`✗ ${bad}개 실패`:'✓ 모두 통과');
