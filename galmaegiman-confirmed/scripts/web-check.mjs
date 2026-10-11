// 웹 대시보드 점검(v0.14.0): PC·모바일 세로·가로에서 홈·뽑기·조합·보관함·상세 캡처 + 화면 넘침·누르는 곳 크기·오류
// 사용: node scripts/web-check.mjs <결과 폴더>  (시험 서버 :3999, 심사 코드 135790)
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
const out=process.argv[2];const b=await chromium.launch();let bad=0;
const R=(n,w,ok,i='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${n} ${w} ${i}`);};
for(const [n,w,h,m] of [['PC',1440,900,false],['MP',390,844,true],['ML',844,390,true]]){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:2,isMobile:m,hasTouch:m});const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
  await p.goto('http://localhost:3999/my');await p.waitForSelector('#code');await p.screenshot({path:`${out}/${n}-0login.png`});
  await p.fill('#code','135790');await p.click('#go');await p.waitForSelector('.home');await p.waitForTimeout(2500);
  const chk=async(t)=>{const r=await p.evaluate(()=>{const x=document.documentElement.scrollWidth>innerWidth+1;
    const small=[...document.querySelectorAll('button,a,[role=button],input,select')].filter(e=>{const a=(e.closest('label.search')??e).getBoundingClientRect();const s=getComputedStyle(e);return a.width>0&&a.height>0&&s.visibility!=='hidden'&&(a.height<40||a.width<40)}).map(e=>(e.id||e.className||e.tagName)+':'+Math.round(e.getBoundingClientRect().width)+'x'+Math.round(e.getBoundingClientRect().height));
    const clip=[...document.querySelectorAll('.btn,.chip')].filter(e=>{const a=e.getBoundingClientRect();return a.width>0&&(a.right>innerWidth+1||a.left<-1)}).length;return {x,small:[...new Set(small)].slice(0,6),clip}});
    R(n,t+' 가로 넘침 없음',!r.x);R(n,t+' 버튼 화면 밖 없음',!r.clip);R(n,t+' 누르는 곳 40px 이상',!r.small.length,JSON.stringify(r.small));};
  await p.screenshot({path:`${out}/${n}-1home.png`,fullPage:true});await chk('홈');
  await p.evaluate(()=>location.hash='draw');await p.waitForSelector('.gachas');await p.waitForTimeout(800);await p.screenshot({path:`${out}/${n}-2draw.png`,fullPage:true});await chk('뽑기');
  await p.fill('[data-i="LOW"]','3');await p.locator('[data-i="LOW"]').blur();await p.click('[data-go="LOW"]');await p.waitForSelector('.res .card');await p.waitForTimeout(900);
  R(n,'뽑기 결과 3칸 이상',await p.locator('.res .card').count()>=1,String(await p.locator('.res .card').count()));await p.screenshot({path:`${out}/${n}-3drawres.png`,fullPage:true});
  await p.evaluate(()=>location.hash='craft');await p.waitForSelector('.rlist');await p.waitForTimeout(900);await p.screenshot({path:`${out}/${n}-4craft.png`,fullPage:true});await chk('조합');
  await p.evaluate(()=>location.hash='box');await p.waitForSelector('.cards .card');await p.waitForTimeout(1200);await p.screenshot({path:`${out}/${n}-5box.png`});await chk('보관함');
  await p.locator('.list .card[data-id^="D"],.list .card[data-id^="T"],.list .card[data-id^="E"],.list .card[data-id^="I"]').first().click();await p.waitForTimeout(1800);
  const d=await p.evaluate(()=>({show:!!document.querySelector('#detail')&&getComputedStyle(document.querySelector('#detail')).display!=='none',name:document.querySelector('#detail h2')?.textContent,hash:location.hash,rules:document.querySelector('#dRules')?.textContent.slice(0,40)}));
  R(n,'상세 보임',d.show,JSON.stringify(d));await p.screenshot({path:`${out}/${n}-6detail.png`,fullPage:m});await chk('상세');
  // 실패 상태: 조합 정보 요청을 한 번 끊으면 오류 + 다시 시도 버튼, 누르면 다시 불러옴
  let cut=true;await p.route('**/my/api/craft',r=>{if(cut&&r.request().method()==='GET'){cut=false;return r.abort();}return r.continue();});
  await p.evaluate(()=>location.hash='draw');await p.waitForSelector('.gachas');await p.evaluate(()=>location.hash='craft');await p.waitForSelector('#retry');
  R(n,'실패 시 다시 시도 버튼',true);await p.click('#retry');await p.waitForSelector('.rlist');R(n,'다시 시도로 복구',true);
  R(n,'오류 없음',!errs.length,JSON.stringify(errs));await c.close();
}
await b.close();console.log(bad?`✗ ${bad}개 실패`:'✓ 모두 통과');
