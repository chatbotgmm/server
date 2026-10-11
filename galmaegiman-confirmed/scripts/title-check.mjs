// 사용: node scripts/title-check.mjs <결과 폴더> <게임 열쇠> <글꼴 폴더>  (시험 서버 :3999, 심사 코드 135790)
// 새 첫 화면 점검: 크기마다 로그인 전·후 캡처, 화면 밖 잘림, 그림 겹침(투명 제외 실제 픽셀), 누르는 칸 = 보이는 버튼, 로그인·게임 시작 흐름
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";
import { readFileSync } from "node:fs";
const [out,K,F]=process.argv.slice(2);
const pk={bhs:F+'/_fontsource_black-han-sans/package',gd:F+'/_fontsource_gowun-dodum/package',bfo:F+'/_fontsource_bagel-fat-one/package'};
const css=Object.entries(pk).map(([k,d])=>readFileSync(d+'/index.css','utf8').replace(/url\(\.\/files\//g,`url(https://fonts.gstatic.com/${k}/`)).join('\n');
const b=await chromium.launch();let bad=0;const R=(n,t,ok,x='')=>{if(!ok)bad++;console.log(`${ok?'✓':'✗'} ${n} ${t} ${x}`);};
const check=async(p,n,tag)=>{
  const r=await p.evaluate(async()=>{
    const els=[...document.querySelectorAll('.t2 .t2p,.t2 .t2acc')];
    const box=e=>{if(e.classList.contains('t2acc')){const a=e.getBoundingClientRect();return {x:a.x,y:a.y,w:a.width,h:a.height};}const cs=getComputedStyle(e);return {x:parseFloat(cs.left),y:parseFloat(cs.top),w:parseFloat(cs.width),h:parseFloat(cs.height)};};
    const W=innerWidth,H=innerHeight;
    const out=els.filter(e=>{const q=box(e);return q.x<-0.5||q.y<-0.5||q.x+q.w>W+0.5||q.y+q.h>H+0.5;}).map(e=>e.id||e.alt||e.src?.split('/').pop()||e.className);
    // 투명 아닌 픽셀 겹침(배율 1/2로)
    const k=0.5,masks=[];
    for(const e of els){const im=e.tagName==='IMG'?e:e.classList.contains('t2acc')?null:e.querySelector('img');const q=box(e);const c=new OffscreenCanvas(Math.ceil(W*k),Math.ceil(H*k)),g=c.getContext('2d');
      if(im){await im.decode().catch(()=>{});g.drawImage(im,q.x*k,q.y*k,q.w*k,q.h*k);}else{g.fillRect(q.x*k,q.y*k,q.w*k,q.h*k);}
      const d=g.getImageData(0,0,c.width,c.height).data;const m=new Uint8Array(c.width*c.height);for(let i=0;i<m.length;i++)m[i]=d[i*4+3]>60?1:0;masks.push([(e.id||(im?im.src.split('/').pop():e.className)),m]);}
    const ov=[];for(let i=0;i<masks.length;i++)for(let j=i+1;j<masks.length;j++){let o=0;const a=masks[i][1],bb=masks[j][1];for(let t=0;t<a.length;t++)if(a[t]&&bb[t])o++;if(o)ov.push(`${masks[i][0]}×${masks[j][0]}:${o}`);}
    // 누르는 칸 = 보이는 버튼(그림 칸과 같은지, 가운데를 누르면 그 버튼인지)
    const btn=[...document.querySelectorAll('.t2 .t2btn,.t2 .t2acc')].map(e=>{const a=e.getBoundingClientRect(),im=e.classList.contains('t2acc')?null:e.querySelector('img'),c=im?im.getBoundingClientRect():a;const hit=document.elementFromPoint(a.x+a.width/2,a.y+a.height/2);
      return {id:e.id||e.className.split(' ').pop(),same:Math.abs(a.width-c.width)<1&&Math.abs(a.height-c.height)<1&&Math.abs(a.x-c.x)<1&&Math.abs(a.y-c.y)<1,hit:!!hit&&e.contains(hit),wh:[Math.round(a.width),Math.round(a.height)]};});
    const set=document.querySelector('#tset').getBoundingClientRect();
    const scroll=document.documentElement.scrollWidth>W+1||document.documentElement.scrollHeight>H+1;
    const txt=[...document.querySelectorAll('.t2 .t2btn')].map(e=>e.textContent.trim()).filter(Boolean);
    return {out,ov,btn,set:[Math.round(set.width),Math.round(set.height)],setIn:set.right<=W&&set.top>=0,scroll,txt,acc:document.querySelector('#tacc')?.textContent.trim()??null};
  });
  R(n,`${tag} 화면 밖 잘림 없음`,!r.out.length,JSON.stringify(r.out));
  R(n,`${tag} 로고·캐릭터·버튼 겹침 없음`,!r.ov.length,JSON.stringify(r.ov));
  R(n,`${tag} 누르는 칸 = 보이는 버튼`,r.btn.every(x=>x.same&&x.hit),JSON.stringify(r.btn));
  R(n,`${tag} 버튼 높이 40px 이상`+(n==='P'?'(세로: 참고만)':''),n==='P'||r.btn.every(x=>x.wh[1]>=40)&&r.set[1]>=40,JSON.stringify([r.btn.map(x=>x.wh),r.set]));
  R(n,`${tag} 버튼 글자를 코드로 겹쳐 쓰지 않음`,!r.txt.length,JSON.stringify(r.txt));
  R(n,`${tag} 설정 버튼 화면 안`,r.setIn);R(n,`${tag} 스크롤 없음`,!r.scroll);
  return r;
};
for(const [n,w,h] of [['Z',1006,411],['L',915,412],['S',780,360],['I',844,390],['T',1280,800],['Q',1024,768],['W',1280,720],['X',1920,1080],['P',412,915]]){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:2,serviceWorkers:'block',hasTouch:true});
  await c.route('https://fonts.googleapis.com/**',r=>r.fulfill({contentType:'text/css',body:css}));
  await c.route('https://fonts.gstatic.com/**',r=>{const [k,f]=r.request().url().split('fonts.gstatic.com/')[1].split('/');r.fulfill({contentType:'font/woff2',headers:{'access-control-allow-origin':'*'},body:readFileSync(pk[k]+'/files/'+f)});});
  const p=await c.newPage();const errs=[];p.on('pageerror',e=>errs.push(e.message));
  await p.goto(`http://localhost:3999/duel?k=${K}`);await p.waitForSelector('.t2');await p.evaluate(()=>Promise.all([...document.images].map(i=>i.decode().catch(()=>{}))));await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(900);
  await p.screenshot({path:`${out}/${n}-1out.png`});await check(p,n,'로그인 전');
  // 로그인 전: 두 버튼 모두 로그인 화면으로
  const st0=await p.evaluate(()=>({login:!!document.querySelector('#tlogin'),start:!!document.querySelector('#tgo'),acc:!!document.querySelector('#tacc'),href:document.querySelector('#tlogin')?.getAttribute('href'),c:(()=>{const a=document.querySelector('#tlogin').getBoundingClientRect();return [a.x+a.width/2,a.y+a.height/2,a.height].map(Math.round)})()}));
  R(n,'로그인 전: 가운데 「로그인」만, 계정 칸 없음',st0.login&&!st0.start&&!st0.acc&&/\/my\?next=duel/.test(st0.href),JSON.stringify(st0));
  if(n==='Z'||n==='W'){
    await p.click('#tlogin');await p.waitForSelector('#code');R(n,'「로그인」 누르면 로그인 화면',/\/my\?next=duel/.test(p.url()),p.url());
    await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/,{timeout:15000});
  } else {
    await p.goto('http://localhost:3999/my?next=duel&g=1');await p.waitForSelector('#code');await p.fill('#code','135790');await p.click('#go');await p.waitForURL(/\/duel\?k=/,{timeout:15000});
  }
  await p.waitForSelector('.t2');await p.evaluate(()=>Promise.all([...document.images].map(i=>i.decode().catch(()=>{}))));await p.waitForTimeout(900);
  await p.screenshot({path:`${out}/${n}-2in.png`});const r=await check(p,n,'로그인 뒤');
  R(n,'로그인 뒤 계정 이름 표시(코드 글자)',!!r.acc,r.acc);
  const st1=await p.evaluate(()=>({login:!!document.querySelector('#tlogin'),c:(()=>{const a=document.querySelector('#tgo').getBoundingClientRect();return [a.x+a.width/2,a.y+a.height/2,a.height].map(Math.round)})(),acc:(()=>{const a=document.querySelector('#tacc').getBoundingClientRect();return [Math.round(innerWidth-a.right),Math.round(innerHeight-a.bottom),Math.round(a.height)]})()}));
  R(n,'로그인 뒤: 같은 자리에 「게임 시작」(가운데 좌표·높이 같음)',!st1.login&&Math.abs(st1.c[0]-st0.c[0])<=1&&Math.abs(st1.c[1]-st0.c[1])<=1&&st1.c[2]===st0.c[2],JSON.stringify([st0.c,st1.c]));
  R(n,n==='P'?'로그인 뒤: 계정 칸 오른쪽(세로는 설정 버튼 아래)':'로그인 뒤: 계정 칸 오른쪽 아래',st1.acc[0]<=16&&(n==='P'||st1.acc[1]<=16)&&st1.acc[2]>=40,JSON.stringify(st1.acc));
  if(n==='Z'||n==='W'){
    await p.click('#tacc');await p.waitForTimeout(1500);await p.screenshot({path:`${out}/${n}-3acc.png`});
    // 계정 창 닫기(돌아가기) → 첫 화면
    const fr=p.frameLocator('iframe').first();await fr.locator('#gback').click({timeout:8000});
    await p.waitForTimeout(1200);R(n,'계정 창 닫으면 첫 화면',!!(await p.$('.t2 #tacc')));
    await p.click('#tgo');await p.waitForSelector('.home8',{timeout:10000}).catch(()=>{});R(n,'로그인 뒤 「게임 시작」 → 로비',!!(await p.$('.home8')));
    await p.waitForTimeout(1000);await p.screenshot({path:`${out}/${n}-4lobby.png`});
  }
  R(n,'오류 없음',!errs.length,JSON.stringify(errs));await c.close();
}
await b.close();console.log(bad?`✗ ${bad}개 실패`:'✓ 모두 통과');
