// PC 웹게임(/game60) 1단계 흐름 검토: 실제 서버에 시험 계정으로 들어가 마을→모집→내 갈매미→스토리 대화→편성→전투(증강·기술)→정산→귀환→귀환 이야기까지
// 진행하며 화면을 찍고, 화면 밖으로 나간 요소·잘린 글자를 숫자로 검사한다. 시험 서버·격리 DB에서만 쓴다(실제 지원서·경험치가 움직임).
// 사용: node scripts/web/game60-review.cjs <결과 폴더> <가로> <세로> <서버 주소> <curl 쿠키 파일>
const {chromium}=require('playwright');const fs=require('fs');
const [out,W,H,BASE,JAR]=[process.argv[2],+process.argv[3]||960,+process.argv[4]||540,process.argv[5]||'http://127.0.0.1:3191',process.argv[6]];fs.mkdirSync(out,{recursive:true});
const jar=fs.readFileSync(JAR,'utf8').split('\n').map(l=>l.replace(/^#HttpOnly_/,'')).filter(l=>l&&!l.startsWith('#')).map(l=>l.split('\t'));
(async()=>{const b=await chromium.launch();const ctx=await b.newContext({viewport:{width:W,height:H}});
await ctx.addCookies(jar.map(f=>({name:f[5],value:f[6],domain:new URL(BASE).hostname,path:f[2],httpOnly:true})));
const p=await ctx.newPage();const errs=[];p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});p.on('pageerror',e=>errs.push(String(e)));
const bad=[];
const check=async name=>bad.push(...(await p.evaluate(nm=>{const st=document.getElementById('stage').getBoundingClientRect(),r=[];
 for(const el of document.querySelectorAll('#stage *')){if(el.tagName==='IMG'||el.closest('.bg,.pano,.place,.art,.big,.field,.list,.grid')&&el.tagName!=='BUTTON')continue;const b=el.getBoundingClientRect();if(!b.width||!b.height||getComputedStyle(el).visibility==='hidden')continue;
  if(b.left<st.left-1||b.right>st.right+1||b.top<st.top-1||b.bottom>st.bottom+1)r.push(nm+' 밖: '+(el.className||el.tagName)+' '+(el.textContent||'').trim().slice(0,12));
  if(el.children.length===0&&el.textContent.trim()&&el.scrollWidth>el.clientWidth+1&&getComputedStyle(el).overflow!=='visible')r.push(nm+' 잘림: '+(el.className||el.tagName)+' '+el.textContent.trim().slice(0,12));}
 return r;},name)));
let n=0;const shot=async name=>{await p.waitForTimeout(500);await check(name);await p.screenshot({path:`${out}/${String(++n).padStart(2,'0')}-${name}.png`});};
const click=async sel=>{await p.locator(sel).first().click();await p.waitForTimeout(450);};
await p.goto(BASE+'/game60',{waitUntil:'load'});await p.waitForTimeout(1500);await shot('village');
await click('.dock [data-go="recruit"]');await shot('recruit');
await click('[data-k="LOW"][data-n="5"]');await click('[data-start="LOW"]');await shot('recruit-results');
if(await p.locator('[data-a="reroll"]').count())await click('[data-a="reroll"]');
await click('[data-all]');await shot('recruit-claimed');
await click('[data-go="units"]');await shot('units');
await click('.top [data-go="village"]');await click('.dock [data-go="story"]');await shot('story');
await click('[data-open="intro"]');await shot('scene-1');
for(let i=0;i<10;i++){const t=await p.locator('[data-next]').textContent();if(t.includes('수락')){await shot('scene-last');await click('[data-next]');break;}await click('[data-next]');}
await shot('story-accepted');
await click('[data-form]');await shot('formation-0');
const ids=await p.locator('.slot[data-id]').count();for(let i=1;i<Math.min(3,ids);i++)await click(`.slot[data-id] >> nth=${i}`);
await shot('formation');await click('[data-go-battle]');await shot('battle-augment');
for(let t=0;t<60;t++){
 if(await p.locator('.result').count())break;
 if(await p.locator('[data-aug]').count()){await click('[data-aug]');continue;}
 if(await p.locator('[data-switch]:not([disabled])').count()&&!(await p.locator('[data-act="attack"]').count())){await click('[data-switch]:not([disabled])');continue;}
 if(t===2){await click('[data-mode="skill"]');await shot('battle-skills');if(await p.locator('[data-skill]:not([disabled])').count()){await click('[data-skill]:not([disabled])');continue;}await click('[data-mode="main"]');}
 if(t===1)await shot('battle');
 await click('[data-act="attack"]');
}
await shot('battle-result');await click('[data-go="story"]');await shot('story-after');
if(await p.locator('[data-return]').count()){await click('[data-return]');await shot('returned');}
if(await p.locator('[data-open="return"]').count()){await click('[data-open="return"]');await shot('return-scene');for(let i=0;i<12;i++){if(!(await p.locator('[data-next]').count()))break;await click('[data-next]');}await shot('story-next');}
// 로그인 전 화면
const c2=await b.newContext({viewport:{width:W,height:H}});const p2=await c2.newPage();await p2.goto(BASE+'/game60');await p2.waitForTimeout(1200);await p2.screenshot({path:`${out}/00-login.png`});
console.log('오류',JSON.stringify(errs));console.log('숫자 검사',bad.length?JSON.stringify([...new Set(bad)]):'통과');await b.close();})();
