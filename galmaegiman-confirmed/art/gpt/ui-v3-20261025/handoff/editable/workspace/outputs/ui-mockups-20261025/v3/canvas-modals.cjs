'use strict';
// Current v3 non-battle modal states. Original illustration PNGs remain intact; shared UI supplies the restored navy/cream and Noto Sans KR style.
// M02/M03 live only in battle-v3; obsolete A-background functions removed from this copy.
function close(u,x,y){u.text(x,y,'×',38,u.C.muted,'Noto','center');}
function base(u,renderers,id){if(typeof renderers[id]!=='function')throw new Error('Missing modal base renderer '+id);renderers[id](u);u.shade(.68);}
function lock(u,cx,cy){const c=u.ctx;c.save();c.strokeStyle=u.C.gold;c.lineWidth=4;c.beginPath();c.roundRect(cx-25,cy-8,50,40,7);c.stroke();c.beginPath();c.arc(cx,cy-10,16,Math.PI,0);c.stroke();c.fillStyle=u.C.gold;c.beginPath();c.arc(cx,cy+7,4,0,Math.PI*2);c.fill();c.fillRect(cx-2,cy+8,4,11);c.restore();}
module.exports={
 M01(u,r){
  base(u,r,'S24');u.rr(140,70,1640,660,18,'#193047','#7d8f9d');close(u,1734,94);
  u.text(186,108,'커먼 선택지급권',40,u.C.cream,'NotoBold');u.text(186,170,'함께할 대원 1명을 선택하세요.',29,u.C.muted);
  const ids=['u02','u04','u06'];ids.forEach((id,i)=>{const x=186+i*302;if(i===0)u.rr(x,225,276,360,14,'#30445c','#d1ba86',2);u.portrait(id,x+16,237,244,272);u.text(x+138,536,u.units[id].name,30,u.C.cream,'NotoBold','center');if(i===0)u.pill(x+171,240,'선택',81,true)});
  u.dots(626,614,3,0);u.text(626,642,'좌우로 밀어 다른 대원 보기',24,u.C.muted,'Noto','center');
  u.ctx.save();u.ctx.fillStyle='#8295a544';u.ctx.fillRect(1106,225,1,445);u.ctx.restore();
  u.text(1150,232,'미니',42,u.C.cream,'NotoBold');u.pill(1280,241,'커먼',90);
  u.text(1150,326,'HP  98',32,u.C.cream,'NotoBold');u.text(1420,326,'MP  42',32,u.C.cream,'NotoBold');
  u.text(1150,385,'내구  16',29,u.C.cream);u.text(1420,385,'저항  14',29,u.C.cream);u.text(1150,444,'속도  28',29,u.C.cream);
  u.btn(1150,510,172,54,'상세 보기');u.btn(1150,600,570,80,'미니 선택','primary');
  u.text(186,687,'갈매미맨 제외 · 선택한 대원은 영구 보유',23,u.C.muted);
  u.footer('M01','u01 제외 커먼 1개 선택 · 영구 보유 · 능력치는 예시 · 선택 확정 전 지급권 소모 없음');
 },
 M04(u,r){
  base(u,r,'S09');u.rr(330,80,1260,650,18,'#193047','#7d8f9d');u.text(380,115,'레벨이 올랐어요!',42,u.C.cream,'NotoBold');
  u.card('u01',384,202,336,448);u.text(800,222,'갈매미맨',36,u.C.cream,'NotoBold');
  u.text(800,288,'Lv.12',49,u.C.muted,'NotoBold');u.text(970,292,'→',44,u.C.gold);u.text(1055,288,'Lv.13',49,u.C.gold,'NotoBold');
  u.text(800,386,'HP  154 → 160',32,u.C.cream,'NotoBold');u.text(800,440,'MP  38 → 40',32,u.C.cream,'NotoBold');
  u.text(800,500,'내구 24 → 25  ·  저항 12 → 13',27,u.C.muted);u.text(800,542,'속도 18 → 19',27,u.C.muted);
  u.btn(800,600,730,80,'확인','primary');
  u.footer('M04','레벨업 결과 예시 · 등록증 원본 1장 · 전후 수치를 붙여 비교 · 확인하면 호출 화면으로 복귀');
 },
 M05(u,r){
  base(u,r,'S04');u.rr(480,160,960,490,18,'#193047','#7d8f9d');lock(u,566,231);
  u.text(650,205,'지금은 점검 중이에요',42,u.C.cream,'NotoBold');
  u.text(650,279,'점검이 끝나면 다시 이용할 수 있어요.',28,u.C.muted);
  u.text(650,365,'현재 대표 레벨  Lv.12',30,u.C.cream,'NotoBold');u.text(650,421,'대표 레벨 조건  미정',28,u.C.muted);
  u.btn(760,532,400,80,'확인','primary');
  u.footer('M05','테스트 잠금 안내 · 대표 레벨 조건은 미정 · 잠금 상태의 서버 연결은 구현 단계에서 확인');
 },
 M06(u,r){
  base(u,r,'S03');u.rr(460,190,1000,420,18,'#193047','#7d8f9d');
  const c=u.ctx;c.save();c.strokeStyle=u.C.gold;c.lineWidth=5;c.beginPath();c.arc(531,259,24,.38,Math.PI*1.8);c.stroke();c.beginPath();c.moveTo(545,231);c.lineTo(554,248);c.lineTo(536,245);c.stroke();c.restore();
  u.text(590,226,'연결을 확인해 주세요',42,u.C.cream,'NotoBold');u.text(590,312,'인터넷 연결 상태를 확인한 후\n다시 연결해 주세요.',28,u.C.muted,'Noto','left',0,1.5);
  u.btn(710,460,500,80,'다시 연결','primary');
  u.footer('M06','연결 복구 예시 · 현재 화면 유지 · 전투·수령·정산 상태 확인 전 홈 이동이나 중복 처리 없음');
 }
};
