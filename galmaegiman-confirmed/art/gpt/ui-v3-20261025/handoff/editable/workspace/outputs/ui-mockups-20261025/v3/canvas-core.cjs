module.exports={
S10(u){const{C}=u;u.bg('bg-collection');u.header('도감');
u.panel(40,138,1840,72);u.rr(64,150,258,48,7,'#263e54','#81919f');u.text(84,159,'등급: 커먼',24,C.cream,'NotoBold');u.ctx.beginPath();u.ctx.moveTo(283,169);u.ctx.lineTo(292,177);u.ctx.lineTo(301,169);u.ctx.strokeStyle=C.cream;u.ctx.lineWidth=2.5;u.ctx.stroke();u.rr(354,162,22,22,3,'#193047','#81919f');u.text(390,158,'보유만',24,C.muted);u.text(1852,161,'커먼 8종 · 1 / 8   (전체 60종)',23,C.muted,'Noto','right');
u.card('u01',132,230,360,480);u.dots(312,725,5,0);u.text(312,739,'좌우로 밀어 보기',20,C.cream,'Noto','center');
u.panel(614,230,1040,530);u.text(644,253,'갈매미맨',38,C.cream,'NotoBold');u.text(644,310,'태초 · 커먼',23,C.muted);u.pill(1440,260,'보유 · Lv.12',190);
u.tabs(644,357,['능력치','기술','적성','방어'],0,[170,150,150,150]);
// Values stay beside their labels, rather than on the far edge of a wide panel.
[['HP','154 / 154'],['MP','38 / 38'],['내구','24'],['저항','12'],['속도','18']].forEach(([a,b],i)=>{let y=430+i*56;u.text(644,y,a,27,C.muted);u.text(824,y,b,29,C.cream,'NotoBold');if(i<2)u.bar(1100,y+15,460,12,100,i?'mp':'hp');else{u.ctx.fillStyle='#6a7f9166';u.ctx.fillRect(1100,y+21,460,1)}});
u.footer('S10','한 화면 안의 능력치·기술·적성·방어 탭 · 상세로 중복 이동 제거 · 능력치·보유 상태 예시');},
S12(u){const{C}=u;const leftMode=u.craftLeftMode||'card';u.bg('bg-navigation-workshop-v1');u.header('조합',false);u.tabs(1340,46,['결과로 찾기','재료로 찾기'],1,[240,240]);
if(leftMode==='card')u.card('u05',181,166,360,480);else u.portrait('u05',115,193,475,437);u.panel(90,644,560,116);u.text(114,660,'황금',34,C.cream,'NotoBold');u.text(624,671,'커먼 · Lv.1 / 60',23,C.muted,'Noto','right');u.text(114,718,'좌우로 밀기 · 탭하여 재료 선택',21,C.muted);u.text(624,718,'2 / 8',21,C.muted,'Noto','right');
u.panel(680,138,640,622);u.text(704,158,'선택한 재료',27,C.cream,'NotoBold');u.text(1295,162,'3 / 6',23,C.muted,'Noto','right');
['u05','u06','u07',null,null,null].forEach((id,i)=>{let x=700+i%3*201,y=210+Math.floor(i/3)*241;u.rr(x,y,190,224,12,id?'#253d54':'#11273ce8',id?'#C4AD7D':'#53677966',id?1.5:1);if(id){u.portrait(id,x+13,y+9,164,170);u.text(x+95,y+185,u.units[id].name,25,C.cream,'NotoBold','center');u.pill(x+149,y+9,'✓',30,true)}else{u.text(x+95,y+75,'+',35,'#8a9baa','Noto','center');u.text(x+95,y+145,'재료 '+(i+1),22,C.muted,'Noto','center')}});
u.text(1000,714,'선택한 재료를 다시 누르면 해제돼요.',21,C.muted,'Noto','center');
u.panel(1344,138,536,622);u.text(1368,158,'예상 결과',27,C.cream,'NotoBold');u.text(1854,162,'1 / 3',23,C.muted,'Noto','right');u.card('u25',1489,205,245,327);u.text(1368,548,'룬장인',31,C.cream,'NotoBold');u.text(1518,557,'에픽',23,C.gold);u.text(1368,600,'예상 레벨',23,C.muted);u.text(1530,600,'미정',24,C.cream,'NotoBold');u.text(1368,640,'조건 확인 필요',23,C.muted);u.btn(1368,681,488,62,'조합 확정','disabled');
u.footer('S12',leftMode==='card'?'등록증 유지 · 재료 캐릭터 확대 · XP 계승 규칙 확정 / 개별 레시피·XP 곡선 데이터 미연결':'캐릭터 단독 대안 · XP 계승 규칙 확정 / 레시피·XP 곡선 데이터 미연결');},
};
