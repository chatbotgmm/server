function homeFrame(u,key,x,y,w,h,cap=23){u.frame('icons/'+key+'.png',x,y,w,h,cap)}
function homeToggle(u,open=false){
 const C=u.C;homeFrame(u,'menu',1189,652,210,105);const ctx=u.ctx;ctx.fillStyle=C.ink;
 if(open){ctx.save();ctx.strokeStyle=C.ink;ctx.lineWidth=5;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(1220,686);ctx.lineTo(1248,714);ctx.moveTo(1248,686);ctx.lineTo(1220,714);ctx.stroke();ctx.restore()}
 else{for(let i=0;i<4;i++)u.rr(1212+(i%2)*24,682+Math.floor(i/2)*24,19,19,3,C.ink,null)}
 u.text(1323,684,open?'닫기':'메뉴',31,C.ink,'NotoBold','center');
}
function homeBase(u){
 const C=u.C;u.bg('bg-home');homeFrame(u,'menu',90,43,360,106);u.img('icons/u-profile-native.png',112,68,55,55);u.text(286,73,'와로두',33,C.ink,'NotoBold','center');
 [['shrimp','800'],['peanut','0'],['application','183']].forEach(([ic,val],i)=>{let x=1114+i*225;homeFrame(u,'panel',x,43,210,106,16);u.img('icons/currency-'+ic+'.png',x+14,65,63,63);u.text(x+140,74,val,32,C.cream,'NotoBold','center')});homeFrame(u,'tile',1788,43,92,106,19);u.img('icons/settings.png',1805,65,59,59);
 homeFrame(u,'tile',90,169,106,112,21);u.text(143,197,'우편',29,C.ink,'NotoBold','center');u.rr(165,179,17,17,9,'#be5343','#e8c3a7');
 u.img('icons/home-guide-map.png',225,300,430,344);u.panel(178,670,575,70,true);u.text(465,688,'오늘도 항구를 함께 지켜요!',25,C.ink,'Noto','center');
 homeFrame(u,'play',1420,652,460,105,23);u.icon('icon-explore',1445,675,64);u.text(1680,672,'플레이',47,C.ink,'NotoBold','center');
}
function titleText(u,x,y,str,size){u.ctx.save();u.ctx.shadowColor='#143543';u.ctx.shadowBlur=10;u.ctx.shadowOffsetY=4;u.text(x,y,str,size,u.C.cream,'NotoBold');u.ctx.restore()}
function recruitResult(u,state){
 const C=u.C,received=state==='received',insufficient=state==='insufficient',cash=insufficient?0:5;
 u.bg('bg-recruitment-counter-v1');u.header('모집 결과',true,{application:String(cash),shrimp:'800',peanut:'0'});
 u.panel(40,138,1840,622);['u17','u18','u25'].forEach((id,i)=>{let x=230+i*560,used=state==='ready'&&i===0;u.card(id,x,150,315,410);u.text(x+157,570,received?'등록 완료':used?'재추첨 완료':insufficient?'재추첨 미사용':'재추첨 가능',24,received?C.green:used?C.muted:C.cream,'NotoBold','center');let label=received?'재추첨 종료':used?'재추첨 · 5장 사용':insufficient?'지원서 부족 · 5장':'재추첨 · 5장';u.btn(x,611,315,52,label,(received||used||insufficient)?'disabled':'');});
 u.line(70,674,1780);u.text(78,708,received?'3명 등록 완료':'동일 등급 · 카드당 1회',25,received?C.green:C.muted,'NotoBold');u.btn(1420,680,420,80,received?'최종 수령 완료':'3명 최종 수령',received?'disabled':'primary');
 const note=received?'최종 수령 후 상태 · 모든 카드 등록 완료 / 재추첨 및 중복 수령 불가':insufficient?'지원서 0장 상태 · 재추첨 미사용 카드도 비용 부족으로 비활성 · 최종 수령 가능':'지원서 5장 상태 · 첫 카드 재추첨 1회 사용 / 나머지 2장은 비용 5장으로 가능';
 u.footer('S15',note+' · 고급 모집 결과 예시');
}
module.exports={
 S01(u){const C=u.C;u.bg('bg-home');u.sprite('u01','front',150,230,470,470);titleText(u,750,214,'갈매미맨 방범대',82);u.panel(785,520,975,160);u.text(812,545,'항구를 준비하고 있어요',31,C.cream,'NotoBold');u.text(1730,551,'72%',28,C.gold,'NotoBold','right');u.bar(812,600,920,16,72);u.text(812,637,'저장된 진행 상황 확인 중',22,C.muted);u.footer('S01','기존 타이틀 구도 유지 · 새 u01 원본 사용 · 진행률과 상태 문구는 예시');},
 S02(u){const C=u.C;u.bg('bg-home');u.sprite('u01','front',150,230,470,470);titleText(u,800,92,'갈매미맨 방범대',66);u.panel(890,246,815,330);u.text(918,279,'로그인',35,C.cream,'NotoBold');u.rr(918,378,759,68,12,'#FEE500','#e9ce3f');u.text(1297,395,'카카오로 로그인',31,'#191919','NotoBold','center');u.btn(1417,473,260,49,'심사용 로그인');u.footer('S02','카카오 인증 후 앱 복귀 · 심사용 로그인 유지 · 운영 인증 설정 완료를 뜻하지 않는 시안');},
 S03(u){homeBase(u);homeToggle(u,false);u.footer('S03','기존 홈 배치 유지 · 작은 우편 / 설정 · 말풍선은 대원 아래 · 메뉴와 플레이는 오른쪽 아래');},
 S04(u){homeBase(u);const C=u.C;homeFrame(u,'panel',930,178,950,451,23);const items=[['모집','icon-draw'],['방범대','icon-collection'],['조합','icon-combine'],['탐험','icon-explore'],['상점','icon-shop'],['전투 편성','icon-team'],['출석·미션','icon-daily'],['환생',null]];
 items.forEach(([label,ic],i)=>{let x=953+(i%4)*229,y=201+Math.floor(i/4)*202;homeFrame(u,'tile',x,y,216,185,20);u.rr(x+10,y+10,196,165,10,'#52626866',null);
 if(ic)u.img('icons/'+ic+'.png',x+71,y+19,74,74,'contain',.82);else{u.rr(x+88,y+50,40,35,6,'#c2c8bb','#5e7378');u.ctx.beginPath();u.ctx.arc(x+108,y+49,14,Math.PI,0);u.ctx.strokeStyle='#d7ddcb';u.ctx.lineWidth=6;u.ctx.stroke()}
 u.text(x+108,y+103,label,28,C.ink,'NotoBold','center');u.rr(x+57,y+148,14,13,3,'#40565d',null);u.ctx.beginPath();u.ctx.arc(x+64,y+147,4,Math.PI,0);u.ctx.strokeStyle='#40565d';u.ctx.lineWidth=2;u.ctx.stroke();u.text(x+121,y+141,'점검 중',21,'#40565d','NotoBold','center')});homeToggle(u,true);u.footer('S04','기존 4×2 메뉴 배치 · 요청한 8개 테스트 잠금 · 대표 레벨 조건 미정');},
 S07(u){const C=u.C;u.bg('bg-home');u.sprite('u01','front',155,227,450,450);u.panel(760,150,1040,542);u.text(788,208,'닉네임',43,C.cream,'NotoBold');u.text(788,300,'닉네임을 입력해 주세요.',24,C.muted);u.panel(788,363,984,91,true);u.text(814,388,'와로두│',34,C.ink);u.btn(1607,477,165,49,'중복 확인');u.btn(1432,577,340,62,'이 이름으로 시작','primary');u.footer('S07','닉네임 입력/검증 방식은 제안 · 글자수·중복·금칙어 정책은 미정');},
 S08(u){const C=u.C;u.bg('bg-home');u.header('튜토리얼',false);u.sprite('u01','front',195,200,440,440);u.panel(755,159,1080,534);u.pill(784,184,'훈련 완료',145,true);u.text(784,247,'이제 함께 항구를 지켜요!',43,C.cream,'NotoBold');u.icon('currency-application',806,388,66);u.text(900,382,'커먼 선택지급권',30,C.cream,'NotoBold');u.text(900,433,'우편함에서 수령',23,C.muted);u.text(784,514,'갈매미맨을 제외한 커먼 대원 · 영구 보유',23,C.muted);u.btn(1536,585,270,62,'우편함으로','primary');u.footer('S08','시작은 u01 갈매미맨 · 튜토리얼 완료 후 선택지급권 우편 지급 · 대여/태초 별도 보상 없음');},
 S09(u){const C=u.C;u.bg('bg-team');u.header('방범대');
 u.panel(40,134,1840,72);u.text(66,151,'보유 대원 8명',29,C.cream,'NotoBold');u.text(336,158,'대표 Lv.12',24,C.muted);u.tabs(1090,142,['전체','커먼','언커먼'],0,[115,115,145]);u.btn(1600,142,252,55,'도감 보기');
 u.panel(40,221,1260,539);['u01','u06','u07','u02'].forEach((id,i)=>{const x=63+i*302;const selected=i===0;u.rr(x,245,285,417,13,selected?'#40566f':null,selected?'#ceb67b':null,1.5);u.portrait(id,x+14,262,257,294);u.text(x+142,570,u.units[id].name,31,C.cream,'NotoBold','center');u.text(x+142,618,'Lv.'+(i<3?12:1)+' / 60',25,C.muted,'Noto','center');if(selected){u.rr(x+239,258,29,29,15,C.gold,null);u.text(x+253,258,'✓',23,C.ink,'NotoBold','center')}});
 u.text(67,701,'커먼 · 8명',23,C.muted);u.dots(670,720,2,0);u.text(1269,701,'1 / 2',23,C.muted,'Noto','right');
 u.panel(1320,221,560,539);u.text(1352,255,'갈매미맨',38,C.cream,'NotoBold');u.text(1352,313,'커먼 · Lv.12 / 60',25,C.muted);u.line(1352,366,496);
 [['HP','154'],['MP','38'],['내구','24'],['저항','12'],['속도','18']].forEach(([label,val],i)=>{let x=1352+(i%2)*248,y=389+Math.floor(i/2)*56;u.text(x,y,label,25,C.muted);u.text(x+83,y,val,27,C.cream,'NotoBold')});
 u.btn(1352,588,496,62,'상세 보기','primary');u.btn(1352,672,496,58,'전투 편성');u.footer('S09','큰 원본 초상 목록 · 현재 보유 대원의 최고 레벨을 대표 레벨로 표시 · 수치는 예시');},
 S11(u){const C=u.C;u.bg('bg-collection');u.header('유닛 상세');
 u.panel(40,138,560,622);u.text(70,157,'보유',24,C.muted);u.text(569,157,'커먼',24,C.muted,'Noto','right');u.card('u01',120,196,400,533);
 u.panel(620,138,1260,622);u.text(652,163,'갈매미맨',38,C.cream,'NotoBold');u.text(864,175,'태초',23,C.muted);u.text(652,217,'Lv.12 / 60',25,C.muted);u.text(878,217,'HP 154',25,C.cream,'NotoBold');u.text(1056,217,'MP 38',25,C.cream,'NotoBold');u.tabs(652,266,['능력치','기술','적성','방어'],1,[145,135,135,135]);
 for(let i=0;i<6;i++){let x=652+(i%3)*401,y=336+Math.floor(i/3)*154;u.rr(x,y,385,136,11,'#294058','#71839565');u.text(x+22,y+25,'기본 기술 '+(i+1),29,C.cream,'NotoBold');u.text(x+22,y+83,'일반',23,C.muted);u.text(x+113,y+83,'MP '+[4,6,8,10,12,16][i],23,C.gold)}
 u.text(652,685,'길게 누르면 기술 설명',23,C.muted);u.btn(1600,677,250,57,'전투 편성','primary');u.footer('S11','보유 대원의 상세 · 기술명/비용은 구조 예시 · 설명은 누르는 동안만 표시');},
 S13(u){const C=u.C;u.bg('bg-navigation-workshop-v1');u.header('환생');
 u.panel(40,138,550,622);u.card('u57',139,175,352,469);u.text(72,671,'여명',33,C.cream,'NotoBold');u.text(72,717,'영원 · Lv.99',25,C.muted);u.text(558,717,'1 / 1',23,C.muted,'Noto','right');
 u.panel(610,138,1270,622);u.text(650,169,'환생 전후',34,C.cream,'NotoBold');u.text(650,248,'레벨',27,C.muted);u.text(790,244,'99',42,C.cream,'NotoBold');u.text(944,252,'→',30,C.muted);u.text(1050,244,'1',42,C.gold,'NotoBold');u.text(650,323,'환생',27,C.muted);u.text(790,319,'4 / 5회',36,C.cream,'NotoBold');u.text(944,327,'→',30,C.muted);u.text(1050,319,'5 / 5회',36,C.gold,'NotoBold');u.line(650,386,1188);
 u.text(650,421,'필요 레벨 99',27,C.cream,'NotoBold');u.text(916,425,'충족',24,C.green);u.text(1227,421,'현재 레벨 99',27,C.muted);u.text(650,487,'필요 땅콩',27,C.muted);u.icon('currency-peanut',827,480,43);u.text(890,487,'1개',27,C.cream,'NotoBold');u.text(1227,487,'보유 땅콩 0개',27,C.muted);u.text(650,564,'계정 첫 무료 사용 완료',24,C.muted);u.text(650,607,'환생 시 Lv.1 · 최대 5회',24,C.muted);u.btn(1490,670,350,62,'땅콩 부족','disabled');u.footer('S13','대상 등급: 제한·초월·불멸·영원 · 회차별 필요 Lv.60/70/80/90/99 · 수치는 예시');},
 S15(u){recruitResult(u,'ready')},
 S15_insufficient(u){recruitResult(u,'insufficient')},
 S15_received(u){recruitResult(u,'received')},
 S16(u){const C=u.C;u.bg('bg-recruitment-counter-v1');u.header('지원서 받기');u.img('tray-high.png',85,282,640,340);u.panel(805,218,1075,542);u.text(851,259,'지원서',37,C.cream,'NotoBold');u.icon('currency-application',851,336,70);u.text(952,345,'보유 183장',33,C.cream,'NotoBold');u.text(851,453,'다음 지급까지',27,C.muted);u.text(1074,449,'42분',34,C.cream,'NotoBold');u.btn(1580,439,250,62,'수령 대기','disabled');u.line(851,544,978);u.text(851,575,'최근 수령',25,C.muted);u.text(1050,575,'지원서 수령 완료',25,C.cream);u.btn(1490,666,340,62,'모집으로 돌아가기','primary');u.footer('S16','지원서 1종의 네이티브 수령 화면 · 지급 주기·수량·내역은 예시');},
 S17(u){const C=u.C;u.bg('bg-settings-harbor-office-v1');u.header('가방');u.panel(40,138,1840,622);u.text(68,167,'보유 아이템',31,C.cream,'NotoBold');u.tabs(1390,156,['전체','회복','선택권'],2,[110,110,135]);
 u.rr(68,244,310,410,14,'#44566b','#c6ae79',1.5);u.icon('currency-application',159,276,128);u.text(223,431,'커먼\n선택지급권',32,C.cream,'NotoBold','center',0,1.4);u.text(223,570,'보유 1개',24,C.muted,'Noto','center');u.text(343,260,'✓',29,C.gold,'NotoBold','center');
 u.text(447,262,'커먼 선택지급권',38,C.cream,'NotoBold');u.text(447,326,'튜토리얼 완료 보상',25,C.gold);u.line(447,380,1377);u.text(447,421,'커먼 대원 1명 선택 · 영구 보유',29,C.cream,'NotoBold');u.text(447,484,'갈매미맨 제외',26,C.muted);u.text(447,569,'선택 확정 후 아이템이 소모됩니다.',24,C.muted);u.btn(1455,650,370,62,'사용하기','primary');u.footer('S17','가방 보조 진입은 제안 · 8개 메뉴에 새 버튼 추가 없음 · 대상/영구보유 명시');}

};
