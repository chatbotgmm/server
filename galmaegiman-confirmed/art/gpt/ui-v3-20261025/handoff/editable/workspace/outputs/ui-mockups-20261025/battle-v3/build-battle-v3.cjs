// REVIEW MOCKUPS ONLY: approved B harbor scene + native Canvas state proposals.
// No client code, gameplay configuration, deployment, or source raster is edited.
const fs=require('fs'),path=require('path');
const {loadImage}=require('@napi-rs/canvas');
const B=require('./battle-base.cjs');
const OUT=__dirname;
const PARTY=[
 {id:'u01',name:'갈매미맨',role:'출전',hp:[154,154],mp:[38,38],face:[245,230,600,600]},
 {id:'u06',name:'물방울',role:'후보',hp:[92,132],mp:[26,45],face:[245,180,580,580]},
 {id:'u07',name:'불씨',role:'후보',hp:[165,165],mp:[41,41],face:[265,290,620,620]},
 {id:'u02',name:'미니',role:'후보',hp:[66,98],mp:[28,42],face:[220,100,720,720]},
 {id:'u04',name:'꽃',role:'후보',hp:[114,114],mp:[31,31],face:[180,0,870,870]},
 {id:'u05',name:'황금',role:'후보',hp:[0,140],mp:[0,35],face:[260,280,540,540]}
];
const manifest={
 type:'mockup-only',productionChanges:false,viewport:[1920,800],canvas:[1920,900],
 selectedBackground:'B 항구',selectionEvidence:'Sentinel_31c5900de73c819180bfba2e552afa42: B',
 backgroundSource:'app-design/battle-assets-v1/assets/bg-harbor.png',
 approvedBase:'../battle-v2/S06_전투_B_항구전장_v2.png',
 invariant:'Same B backdrop, ally/enemy placement and size, contact shadows, HP/MP panels, command panel coordinates.',
 confirmedRules:{party:'1 active + 5 reserves',skills:6,attackMP:0,focus:'restore 25% of maximum MP',adventureEscape:'70% success; failure gives opponent 1 action',storyAndPvPForfeit:'XP 0'},
 unknownData:['Six skills equipped on this unit','Skill names, MP, effects, targeting','Item catalog, effect values and eligibility rules'],
 exampleData:['Reserve HP/MP, item quantity and disabled examples are layout-only.'],
 holdContract:{onHold:'Show description only, positioned above the battlefield and left of the pressed command.',onRelease:'Hide description; suppress click/cast.',onPointerCancel:'Hide description; do not cast, spend MP, or commit a turn.',shortTap:'Select a skill only; target/confirm before committing.'},
 itemContract:{flow:['item','eligible target','effect/quantity check','explicit use'],cancel:'No use, quantity loss, or turn commit.',eligibility:'Runtime item data must decide legal targets; mockup does not establish revive/full-HP policy.'},
 switchContract:{flow:['choose 1 of 5 reserves','explicit switch'],disabled:['already active','battle-incapacitated'],after:'Former active moves to reserves; total remains 1 + 5.',cancel:'Keep original active unit.'},
 integration:{S06:'../battle-v2/S06_전투_B_항구전장_v2.png',M02:'M02_전투기술설명_누르는동안_v3.png',M03:'M03-3_대원교체_v3.png',additionalStates:'All other outputs belong in battle-flow appendix; do not replace S22.',reviewFooter:'The lower 100px is review-only and not game runtime UI.'},
 verificationScope:'Static visual mockup and unchanged-pixel QA only; no runtime touch behavior is implemented or claimed tested.',
 portraitFocus:PARTY.map(({id,face})=>({id,sourceRect:face,review:'Both eyes and beak visible; u02 head shape and u04 flower bud included.'})),
 partyExamples:PARTY.map(({face,...p})=>p),outputs:[]
};

function make(){const canvas=B.scene('outdoor','B'),ctx=canvas.getContext('2d'),u=B.UI(ctx);return{canvas,ctx,u};}
function rounded(ctx,x,y,w,h,r,fill,stroke,lw=1){ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lw;ctx.stroke();}}
function panel(s,title,subtitle){const{ctx,u}=s;rounded(ctx,1195,22,702,758,18,'#132f43');u.nine('panel-command',1190,16,712,770,75,43);for(const[a,x,y]of[['tl',1185,10],['tr',1803,10],['bl',1185,700],['br',1803,700]])u.image('decor-corner-'+a,x,y,104,88);u.nine('button-surrender',1235,47,58,58,72,17);u.image('back',1248,60,31,31);u.text(title,1313,53,32,B.palette.cream,true);u.text('26초',1852,59,27,B.palette.gold,true,'right');u.text(subtitle,1239,115,21,B.palette.sub);u.line(1234,159,619);}
function footer(s,title,note){const{ctx,u}=s;ctx.fillStyle='#f3f0e6';ctx.fillRect(0,800,1920,100);ctx.fillStyle='#d5c5a4';ctx.fillRect(0,800,1920,2);u.text(title,40,830,25,B.palette.ink,true);note.split('\n').forEach((line,i)=>u.text(line,620,817+i*34,20,'#516472'));u.text('시안·예시데이터',1878,851,17,'#516472',false,'right');}
function button(s,x,y,w,h,label,primary=true,disabled=false){const{ctx,u}=s;ctx.save();if(disabled)ctx.globalAlpha=.48;u.nine(primary?'button-surrender':'button-switch',x,y,w,h,72,23);u.text(label,x+w/2,y+(h-30)/2-1,25,primary?B.palette.ink:B.palette.cream,true,'center');ctx.restore();}
function selectedOutline(s,x,y,w,h){rounded(s.ctx,x-3,y-3,w+6,h+6,10,null,'#ffdf7a',3);}
function portrait(s,p,x,y,size=80){const{ctx}=s;ctx.save();ctx.beginPath();ctx.arc(x+size/2,y+size/2,size/2,0,Math.PI*2);ctx.clip();ctx.fillStyle='#c7b78b';ctx.fillRect(x,y,size,size);const im=B.images['face-'+p.id],r=p.face;ctx.drawImage(im,...r,x,y,size,size);ctx.restore();ctx.beginPath();ctx.arc(x+size/2,y+size/2,size/2,0,Math.PI*2);ctx.lineWidth=2;ctx.strokeStyle='#967137';ctx.stroke();}
function card(s,p,index,mode){const{ctx,u}=s,x=1237+(index%2)*316,y=181+Math.floor(index/2)*153,w=297,h=141;const chosen=p.id==='u06',locked=mode==='switch'&&(p.id==='u01'||p.hp[0]===0);ctx.save();if(locked)ctx.globalAlpha=.61;u.nine('panel-hp',x,y,w,h,46,21);portrait(s,p,x+16,y+31,80);u.text(p.role,x+25,y+10,14,B.palette.ink,true);u.text(p.name,x+107,y+12,24,B.palette.ink,true);u.text('Lv.12',x+273,y+18,15,'#50636b',false,'right');[['HP',p.hp,49],['MP',p.mp,89]].forEach(([tag,v,dy])=>{u.text(tag,x+107,y+dy,16,B.palette.ink,true);u.text(v.join(' / '),x+274,y+dy,16,B.palette.ink,true,'right');u.bar(x+107,y+dy+23,167,10,v[0]/v[1],tag);});u.text(p.hp[0]===0?'전투불능':p.id==='u01'?'출전 중':chosen?'선택됨':'후보',x+56,y+115,14,p.hp[0]===0?'#842f25':B.palette.ink,true,'center');ctx.restore();if(chosen){selectedOutline(s,x,y,w,h);rounded(ctx,x+80,y+12,21,21,11,'#274e56');u.text('✓',x+90.5,y+12,16,'#fff3ce',true,'center');}return{x,y,w,h};}
function noteBox(s,x,y,w,h,lines){const{u}=s;u.nine('panel-hp',x,y,w,h,46,25);lines.forEach((line,i)=>u.text(line,x+25,y+23+i*36,i===0?25:20,B.palette.ink,i===0));}

function skillState(held=false){const s=make(),{ctx,u}=s;panel(s,'기술 선택','갈매미맨 · 현재 MP 38 / 38');for(let i=0;i<6;i++){const x=1237+(i%2)*316,y=181+Math.floor(i/2)*159;u.nine('button-switch',x,y,297,145,72,26);rounded(ctx,x+21,y+24,48,48,24,'#102a40aa','#d6b978',2);u.text(String(i+1),x+45,y+33,25,B.palette.gold,true,'center');u.text('기술 슬롯 '+(i+1),x+84,y+25,28,B.palette.cream,true);u.text('이름·MP 미정',x+84,y+73,20,B.palette.gold);if(held&&i===1){selectedOutline(s,x,y,297,145);u.text('누르는 중',x+267,y+113,16,B.palette.gold,true,'right');}}
 u.line(1237,683,613);u.text('짧게 눌러 선택 · 길게 눌러 설명',1544,706,22,B.palette.gold,false,'center');u.text('설명을 본 뒤 손을 떼어도 시전하지 않아요.',1544,742,18,B.palette.sub,false,'center');
 if(held){u.nine('panel-hp',611,24,560,229,46,28);u.text('기술 슬롯 2',639,47,28,B.palette.ink,true);u.text('MP 미정',1142,52,20,B.palette.ink,true,'right');u.text('대상 · 효과 · 소모 MP',639,95,21,B.palette.ink,true);u.text('확정된 기술 데이터가 연결되면',639,131,21,B.palette.ink);u.text('이 자리에서 설명을 확인합니다.',639,161,21,B.palette.ink);u.text('손을 떼면 닫힘 · 시전하지 않음',639,204,18,'#6d4f29',true);}
 footer(s,held?'M02 전투 기술 설명 · 누르는 동안':'S06-1 기술 선택 · 6개 슬롯','기술명·MP·효과는 미정 표기 · 임의 기술 배정 없음\n'+(held?'설명은 손가락 옆 상단 빈 공간 · 대원/HP 패널을 가리지 않음':'기존 2열 버튼 감각 유지 · 선택과 시전 분리'));
 return s;
}
function itemList(){const s=make(),{ctx,u}=s;panel(s,'아이템 선택','아이템 선택 → 대상 확인 → 사용');const entries=[['HP 회복 아이템','효과 수치 미정','3개 · 예시',false],['MP 회복 아이템','효과 수치 미정','2개 · 예시',false],['회복 아이템','보유 수량 0 · 사용 불가','0개 · 예시',true]];entries.forEach(([name,sub,count,disabled],i)=>{const x=1237,y=181+i*139;ctx.save();if(disabled)ctx.globalAlpha=.48;u.nine('button-item',x,y,613,126,72,25);u.image('icon-item',x+19,y+28,67,67);u.text(name,x+110,y+21,27,B.palette.cream,true);u.text(count,x+580,y+24,20,B.palette.gold,true,'right');u.text(sub,x+111,y+70,21,B.palette.sub);ctx.restore();if(i===0)selectedOutline(s,x,y,613,126);});u.text('선택 · HP 회복 아이템',1238,623,23,B.palette.cream,true);u.text('아이템명·회복량·대상 규칙은 데이터 연결 필요',1238,660,19,B.palette.gold);button(s,1237,710,205,57,'취소',false);button(s,1460,710,390,57,'대상 선택');footer(s,'M03-1 아이템 선택','기능별 배치 예시이며 실제 아이템명이 아님\n보유 0은 이유 표시 + 비활성 · 취소 시 아이템/턴 미소모');return s;}
function partyState(mode){const s=make(),{u}=s;panel(s,mode==='switch'?'대원 교체':'아이템 대상 선택',mode==='switch'?'후보 5명 중 선택 · 출전 1명 + 후보 5명':'HP 회복 아이템 · 보유 3개 · 효과 수치 미정');PARTY.forEach((p,i)=>card(s,p,i,mode));u.text(mode==='switch'?'물방울 선택 · HP 92 / 132 · MP 26 / 45':'선택 대상 · 물방울 · 현재 HP 92 / 132',1238,656,21,B.palette.cream,true);button(s,1237,709,205,58,'취소',false);button(s,1460,709,390,58,mode==='switch'?'물방울로 교체':'물방울에게 사용');footer(s,mode==='switch'?'M03-3 후보 선택 · 교체 확정':'M03-2 아이템 대상 · 사용 확정',mode==='switch'?'얼굴·현재 HP/MP 우선 · 현재 출전/전투불능은 교체 비활성\n교체 확정 전까지 현재 대원과 전투 유지':'선택 대상·효과·수량 확인 후 사용 · 취소 시 미소모\n대상 가능 여부는 아이템 데이터 기준; 회복량·조건은 미정');return s;}
function exitConfirm(mode){const s=make(),{ctx,u}=s;const adventure=mode==='모험';// Recompose only the native mode label strip from its original background asset.
 ctx.save();ctx.beginPath();ctx.rect(0,0,590,54);ctx.clip();u.image('outdoor',0,0,1920,800,'cover');ctx.restore();u.text(mode==='스토리'?'스토리 · 기본 훈련':mode+' · 전투',51,18,22,B.palette.cream,true,'left',true);
 panel(s,adventure?'도망 확인':'항복 확인',mode+' · 결정 전에 결과를 확인하세요');u.image('icon-surrender',1479,186,132,132);
 noteBox(s,1237,335,613,225,adventure?['도망 성공 확률 70%','실패하면 상대가 1회 행동합니다.','결정하기 전에는 행동을 소비하지 않습니다.']:['항복 시 경험치 0','항복하면 이 전투를 종료합니다.','취소하면 현재 전투로 돌아갑니다.']);u.text('정말 '+(adventure?'도망치시겠어요?':'항복하시겠어요?'),1545,607,28,B.palette.cream,true,'center');button(s,1237,704,297,63,'취소 · 전투 계속',false);button(s,1553,704,297,63,adventure?'도망 시도':'항복하기');footer(s,adventure?'M03-4 모험 · 도망 확인':'M03-'+(mode==='스토리'?'5':'6')+' '+mode+' · 항복 확인',adventure?'확정 규칙: 성공 70% · 실패 시 상대 1회 행동\n모험의 경험치/정산 규칙은 이 시안에서 새로 정의하지 않음':'확정 규칙: '+mode+' 항복 시 경험치 0\n취소와 항복을 분리 · 확인 전 전투 유지');return s;}

async function save(s,file,kind){await fs.promises.writeFile(path.join(OUT,file),await s.canvas.encode('png'));manifest.outputs.push({file,kind,canvas:[1920,900],background:'B 항구'});console.log(file);}
(async()=>{await B.init();B.images.back=await loadImage(path.join(B.ASSETS,'icon-back.png'));for(const p of PARTY)B.images['face-'+p.id]=await loadImage(path.join(B.ROOT,'outputs/ui-mockups-20261025/source/assets/sprites',p.id+'-front.png'));
 await save(skillState(false),'S06-1_전투_기술6칸선택_v3.png','skill-selection');
 await save(skillState(true),'M02_전투기술설명_누르는동안_v3.png','hold-description');
 await save(itemList(),'M03-1_아이템선택_v3.png','item-selection');
 await save(partyState('item'),'M03-2_아이템대상선택_v3.png','item-target');
 await save(partyState('switch'),'M03-3_대원교체_v3.png','reserve-switch');
 for(const [i,mode]of ['모험','스토리','PvP'].entries())await save(exitConfirm(mode),'M03-'+(i+4)+'_'+mode+'_'+(i===0?'도망':'항복')+'확인_v3.png','mode-exit-confirm');
 fs.writeFileSync(path.join(OUT,'battle-v3-manifest.json'),JSON.stringify(manifest,null,2));
})().catch(e=>{console.error(e);process.exit(1);});
