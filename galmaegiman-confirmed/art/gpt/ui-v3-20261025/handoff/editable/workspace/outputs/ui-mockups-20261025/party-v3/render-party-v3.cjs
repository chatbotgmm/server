const fs=require('fs'),path=require('path');
const{createCanvas}=require('@napi-rs/canvas');
const{init,make,UI,images}=require('../v2/canvas-ui.cjs');
function miniPortrait(u,x,y,w,h){const im=images['sprites/u02-front.png'];let sw=1040,sh=910,scale=Math.min(w/sw,h/sh);u.ctx.drawImage(im,105,105,sw,sh,x+(w-sw*scale)/2,y+(h-sh*scale)/2,sw*scale,sh*scale)}
function texturedButton(u,x,y,w,h,label,primary=false){let im=images['icons/'+(primary?'button-secondary':'button-primary')+'.png'],r=primary?[8,11,589,251]:[41,30,621,269],sx=[r[0],r[0]+70,r[2]-70,r[2]],sy=[r[1],r[1]+60,r[3]-60,r[3]],cap=16,dx=[x,x+cap,x+w-cap,x+w],dy=[y,y+cap,y+h-cap,y+h];for(let j=0;j<3;j++)for(let i=0;i<3;i++)u.ctx.drawImage(im,sx[i],sy[j],sx[i+1]-sx[i],sy[j+1]-sy[j],dx[i],dy[j],dx[i+1]-dx[i],dy[j+1]-dy[j]);u.text(x+w/2,y+(h-32)/2,label,25,primary?u.C.ink:u.C.cream,'NotoBold','center')}
function render(u){const{C}=u;u.bg('bg-navigation-workshop-v1');u.header('전투 편성');
const members=['u01','u06','u07','u02','u04','u05'];
// Original left formation / owned list and right selected-unit / action arrangement.
// Original transparent sprites are shown intact rather than shrinking an entire registration card.
u.panel(40,138,1378,294);u.text(64,153,'출전 1명 + 후보 5명',28,C.cream,'NotoBold');u.text(1394,159,'편성 칸 선택 → 아래 대원 선택',21,C.muted,'Noto','right');
members.forEach((id,i)=>{let x=64+i*223,w=211;u.rr(x,196,w,216,13,i===0?'#30445ced':'#193047d9',i===0?'#F5E2B5':'#62738380',i===0?2:1);u.text(x+105,201,i===0?'출전':'후보 '+i,20,i===0?C.gold:C.muted,'Noto','center');if(id==='u02')miniPortrait(u,x+19,231,173,151);else u.sprite(id,'front',x+17,221,177,177);u.rr(x+1,371,w-2,40,11,'#193047e8',null);u.text(x+105,376,u.units[id].name,24,C.cream,'NotoBold','center');});
u.panel(40,450,1378,310);u.text(64,464,'보유 대원',27,C.cream,'NotoBold');u.text(255,470,'8명',21,C.muted);u.text(1394,470,'1 / 2  ·  좌우로 밀기',21,C.muted,'Noto','right');
['u01','u06','u07','u02'].forEach((id,i)=>{let x=64+i*334,w=318;u.rr(x,508,w,232,13,i===0?'#30445ced':'#193047d9',i===0?'#F5E2B5':'#62738380',i===0?2:1);if(id==='u02')miniPortrait(u,x+54,521,210,176);else u.sprite(id,'front',x+55,510,208,208);u.rr(x+1,695,w-2,44,11,'#193047ed',null);u.text(x+15,704,u.units[id].name,27,C.cream,'NotoBold');u.text(x+w-16,711,'Lv.'+(i<3?12:1),21,C.muted,'Noto','right');u.pill(x+12,518,i===0?'출전':'후보 '+i,86,false);if(i===0)u.pill(x+w-88,518,'선택',72,true);});
u.panel(1440,138,440,622);u.text(1464,157,'갈매미맨',32,C.cream,'NotoBold');u.pill(1776,158,'출전',80,true);u.text(1464,203,'커먼 · Lv.12 / 60',21,C.muted);u.sprite('u01','front',1520,220,278,250);
u.text(1464,479,'HP',22,C.muted);u.text(1635,476,'154',28,C.cream,'NotoBold','right');u.text(1670,479,'MP',22,C.muted);u.text(1854,476,'38',28,C.cream,'NotoBold','right');
[['내구','24'],['저항','12'],['속도','18']].forEach(([a,b],i)=>{let x=1464+i*136;u.text(x,522,a,20,C.muted);u.text(x+116,519,b,25,C.cream,'NotoBold','right')});
u.text(1464,582,'6 / 6 편성',23,C.muted);texturedButton(u,1706,561,150,58,'비우기');u.line(1464,628,392);u.text(1464,639,'스토리 · 기본 훈련',21,C.muted);texturedButton(u,1464,670,392,82,'편성 확정 · 전투 시작',true);
u.footer('S05','스토리 진입 예시 · 메인 관리: 편성 저장 / 대전: 매칭 시작 · 미니는 얼굴 확대 · 비우기 확인 필요');
}
(async()=>{await init();let {canvas,ctx}=make();render(UI(ctx));fs.writeFileSync(path.join(__dirname,'S05_전투편성_v3_캐릭터확대.png'),canvas.toBuffer('image/png'));let mobile=createCanvas(960,450),m=mobile.getContext('2d');m.drawImage(canvas,0,0,960,450);fs.writeFileSync(path.join(__dirname,'qa_mobile_960.png'),mobile.toBuffer('image/png'));console.log('S05_전투편성_v3_캐릭터확대.png');})();
