'use strict';
let manifest, mode='concept', selected='home', side=false, toastTimer;
const $=id=>document.getElementById(id);
const assetUrl=path=>`${path}${path.includes('?')?'&':'?'}v=20261015-ux74`;
const audio={music:{value:75,muted:false},effects:{value:60,muted:true}};
function navigate(kind,id){location.hash=`${kind}/${id}`;}
function currentList(){return mode==='concept'?manifest.concepts:manifest.actual;}
function item(){return currentList().find(x=>x.id===selected)||currentList()[0];}
function toast(text){$('toast').textContent=text;$('toast').style.display='block';clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').style.display='none',3300);}
function dialog(title,body){$('actionTitle').textContent=title;$('actionBody').replaceChildren();if(typeof body==='string'){const p=document.createElement('p');p.textContent=body;$('actionBody').append(p);}else $('actionBody').append(body);$('actionDialog').showModal();}
function act(h){
 const target=h.target||selected;
 if(h.action){
  if(h.action==='claim-ticket'){dialog('뽑기권 30장','채용 공고를 받고 다음 수령 시간을 확인하는 화면입니다. 이 디자인 체험에서는 실제 계정의 뽑기권을 변경하지 않습니다.');return;}
  if(h.action==='draw'&&target===selected){dialog(h.label,'하급·중급은 각 등급의 실제 획득 확률을 따릅니다. 고급 뽑기 버튼에서는 획득 결과 시안으로 이동할 수 있어요.');return;}
  if(h.action==='type'){typeDialog(h.type);return;}
  if(h.action==='promotion'){dialog('직무 표창 선택','승진 시 역할에 맞는 표창 세 가지를 비교하고 하나를 고릅니다. 전투 증강체와 별개로 유지되는 성장입니다.');return;}
  if(selected==='settings'&&/음소거|볼륨/.test(h.label)){soundDialog();return;}
  if(target===selected){dialog(h.label,actionCopy(h));return;}
 }
 if(target.startsWith('actual:'))navigate('actual',target.slice(7));
 else if(target!==selected)navigate('concept',target);
 else dialog(h.label,actionCopy(h));
}
function actionCopy(h){const label=h.label;
 if(/수량|최대|증가|감소|횟수/.test(label))return '실제 게임에서는 원하는 횟수를 선택하고 총 뽑기권을 확인합니다. 화면 이동은 뽑기·조합·편성 등의 큰 메뉴 버튼으로 체험할 수 있어요.';
 if(/알림/.test(label))return '뽑기권 수령 가능 알림과 탐험대 귀환 알림을 각각 켜고 끄는 설정입니다. 실제 알림 권한 요청은 앱에서 진행합니다.';
 if(/동료|편성/.test(label))return '동료를 고른 뒤 출전 자리를 확인하고 저장하는 단계입니다. 이 체험에서는 편성 저장 버튼으로 다음 화면에 이동합니다.';
 if(/기록/.test(label))return '피해·회복 수치는 색으로, 상태 이상은 이름과 색으로 구분합니다. 전투 기록과 결과 시안에서 정보를 확인할 수 있어요.';
 if(/배경음악|효과음/.test(label))return '음소거를 해제하면 슬라이더에 저장한 기존 볼륨을 복원합니다.';
 return '선택한 동작의 위치와 읽기 순서를 확인하는 디자인 체험입니다. 실제 계정·보유 갈매미·재화는 변경하지 않습니다.';
}
function typeDialog(code){const chart={STONE:['돌',['ICE','GRAVITY']],GOLD:['황금',['THREAD','GRAVITY']],FIRE:['불',['ICE','BLOSSOM']],ICE:['얼음',['SEA','GOLD']],BLOSSOM:['꽃',['STONE','SHADOW']],SEA:['바다',['FIRE','STONE']],SHADOW:['그림자',['STAR','GOLD']],STAR:['별',['SEA','SHADOW']],THREAD:['실',['BLOSSOM','STAR']],GRAVITY:['중력',['THREAD','FIRE']]};const [name,strong]=chart[code];const weak=Object.keys(chart).filter(x=>chart[x][1].includes(code));dialog(name+'로 공격할 때','유리 ×1.5: '+strong.map(x=>chart[x][0]).join(' · ')+' / 불리 ×0.67: '+weak.map(x=>chart[x][0]).join(' · ')+'. 기술의 공격 속성을 기준으로 하며 복합 속성과 증강체는 전투의 최종 배율에서 확인해요.');}
function soundDialog(){const box=document.createElement('div');for(const [key,label] of [['music','배경음악'],['effects','효과음']]){const row=document.createElement('label'),span=document.createElement('span'),range=document.createElement('input'),button=document.createElement('button'),value=document.createElement('output');span.textContent=label;range.type='range';range.min=0;range.max=100;range.value=audio[key].value;const refresh=()=>{value.textContent=audio[key].muted?'음소거':audio[key].value+'%';button.textContent=audio[key].muted?'켜기':'끄기';};range.oninput=()=>{audio[key].value=+range.value;refresh();};button.onclick=()=>{audio[key].muted=!audio[key].muted;refresh();};refresh();row.append(span,range,value,button);box.append(row);}const note=document.createElement('p');note.textContent='슬라이더 우측에서 음소거하고, 다시 켜면 저장된 볼륨을 복원합니다. 소리를 재생하지 않는 설정 체험입니다.';box.append(note);dialog('소리 설정',box);}
function renderNav(){const list=$('screenList');list.replaceChildren();const filter=$('search').value.trim();let group='';currentList().forEach((s,i)=>{if(filter&&!s.title.includes(filter)&&!s.id.includes(filter))return;if(mode==='concept'&&s.group!==group){group=s.group;const title=document.createElement('span');title.className='group-label';title.textContent=group;list.append(title);}const b=document.createElement('button');b.classList.toggle('active',s.id===selected);b.setAttribute('aria-current',s.id===selected?'page':'false');const num=document.createElement('small');num.textContent=String(i+1).padStart(2,'0');b.append(num,document.createTextNode(s.title));b.onclick=()=>navigate(mode,s.id);list.append(b);});}
function renderGrid(){const grid=$('grid');grid.replaceChildren();$('overviewTitle').textContent=mode==='concept'?'최종 시안 22장 전체 보기':'실제 화면 적용 전후 전체 보기';currentList().forEach((s,i)=>{const b=document.createElement('button');b.className='tile'+(s.id===selected?' active':'');b.dataset.scene=s.id;const img=document.createElement('img');img.loading='lazy';img.src=assetUrl(s.thumb||s.after);img.alt=s.title;const t=document.createElement('strong');t.textContent=`${String(i+1).padStart(2,'0')}  ${s.title}`;const sub=document.createElement('span');sub.textContent=s.group||'소재 적용 전 · 후 비교';b.append(img,t,sub);b.onclick=()=>{navigate(mode,s.id);window.scrollTo({top:document.querySelector('.workspace')?.offsetTop||$('stage').offsetTop-130,behavior:'smooth'});};grid.append(b);});}
function render(){
 const s=item(),isConcept=mode==='concept',list=currentList();selected=s.id;
 document.title=`${s.title} · 갈매미맨 수비대 화면 검토`;
 $('screenTitle').textContent=s.title;$('group').textContent=isConcept?s.group:'실제 네이티브 화면 비교';$('position').textContent=`${list.indexOf(s)+1} / ${list.length}`;
 $('description').textContent=isConcept?s.description:(manifest.comparison?.description||'실제 네이티브 화면의 전후 비교입니다.');
 $('beforeImage').parentElement.querySelector('figcaption').textContent=manifest.comparison?.before||'기존 구현 · 적용 전';$('afterImage').parentElement.querySelector('figcaption').textContent=manifest.comparison?.after||'현재 구현 · 적용 후';
 $('conceptMode').classList.toggle('active',isConcept);$('actualMode').classList.toggle('active',!isConcept);
 $('conceptControls').hidden=!isConcept;$('actualControls').hidden=isConcept;$('conceptStage').hidden=!isConcept;$('comparisonStage').hidden=isConcept;
 $('hint').textContent=isConcept?'화면 속 버튼을 누르면 다른 시안으로 이동합니다.':'슬라이더를 움직이거나 나란히 보기를 선택하세요.';
 $('selectionDetails').querySelector('strong').textContent=isConcept?'최종 디자인 제안':'최종 시안 스타일을 반영한 실제 구현';
 $('selectionDetails').querySelector('p').textContent=isConcept?'캐릭터와 카드 원본은 유지했습니다. 시안의 계정·진행도·보유 재화는 화면별 예시입니다. 버튼으로 화면을 이동하며 전체 흐름을 확인하세요.':'실제 네이티브 화면 캡처입니다. 재질·버튼 위계·캐릭터 비중·정보 순서를 다듬은 전후를 비교합니다. 최종 시안은 추가 설계 방향이며 실제 구현과 구분해 표시합니다.';
 if(isConcept){
  const image=$('conceptImage');image.alt=s.title+' 최종 디자인 시안';loadImage(image,s.image);
  const spots=$('hotspots');spots.replaceChildren();spots.classList.toggle('visible',$('showHotspots').checked);
  for(const h of s.hotspots){const b=document.createElement('button');b.setAttribute('aria-label',h.label);b.title=h.label;b.style.left=(h.x/1280*100)+'%';b.style.top=(h.y/524*100)+'%';b.style.width=(h.w/1280*100)+'%';b.style.height=(h.h/524*100)+'%';b.onclick=()=>act(h);spots.append(b);}
  $('compareThis').hidden=!manifest.actual.some(x=>x.id===s.actual);$('compareThis').onclick=()=>navigate('actual',s.actual);
 }else{
  loadImage($('beforeImage'),s.before);loadImage($('afterImage'),s.after);const concept=manifest.concepts.find(x=>x.actual===s.id);$('conceptThis').hidden=!concept;$('conceptThis').onclick=()=>navigate('concept',concept.id);setCompare();
 }
 $('openImage').href=assetUrl(isConcept?s.image:s.after);$('saveImage').href=assetUrl(isConcept?s.image:s.after);
 renderNav();renderGrid();const next=list[(list.indexOf(s)+1)%list.length];const preload=new Image();preload.src=assetUrl(next.image||next.after);
}
function loadImage(image,src){$('loading').hidden=false;image.onload=()=>{$('loading').hidden=true;};image.onerror=()=>{$('loading').hidden=true;toast('화면을 불러오지 못했습니다. 다시 선택해 주세요.');};image.src=assetUrl(src);if(image.complete)$('loading').hidden=true;}
function setCompare(){const val=+$('compareSlider').value;$('comparisonStage').classList.toggle('side',side);$('slideView').classList.toggle('active',!side);$('sideView').classList.toggle('active',side);$('afterImage').parentElement.style.clipPath=`inset(0 0 0 ${val}%)`;$('compareLine').style.left=val+'%';}
function fromHash(){const bits=location.hash.slice(1).split('/');mode=bits[0]==='actual'?'actual':'concept';selected=bits[1]||'home';if(!currentList().some(x=>x.id===selected))selected=currentList()[0].id;render();}
function renderReferences(){const root=$('references');if(!root)return;root.replaceChildren();for(const entry of manifest.references||[]){const card=document.createElement('article');card.className='reference-card';const title=document.createElement('h3');title.textContent=entry.name;const focus=document.createElement('strong');focus.textContent=entry.focus;const applied=document.createElement('p');applied.textContent=entry.applied;const rank=document.createElement('small');rank.textContent=entry.rank;const period=document.createElement('small');period.textContent=entry.period;const links=document.createElement('div');for(const [url,label] of [[entry.official,'공식 화면·설명'],[entry.source,'순위 출처']]){const a=document.createElement('a');a.href=url;a.target='_blank';a.rel='noopener noreferrer';a.textContent=label;links.append(a);}card.append(title,focus,applied,rank,period,links);root.append(card);}}
async function init(){try{const r=await fetch('manifest.json?v=20261015-ux74');if(!r.ok)throw Error('manifest');manifest=await r.json();renderReferences();$('actualCount').textContent=manifest.actual.length;$('conceptMode').onclick=()=>navigate('concept','home');$('actualMode').onclick=()=>navigate('actual','home');$('search').oninput=renderNav;$('showHotspots').onchange=()=>{$('hotspots').classList.toggle('visible',$('showHotspots').checked);};$('prev').onclick=()=>move(-1);$('next').onclick=()=>move(1);$('compareSlider').oninput=setCompare;$('slideView').onclick=()=>{side=false;setCompare();};$('sideView').onclick=()=>{side=true;setCompare();};$('fullscreen').onclick=async()=>{if(document.fullscreenElement)await document.exitFullscreen();else if($('stage').requestFullscreen)await $('stage').requestFullscreen();else toast('기기의 화면 확대 기능을 사용해 주세요.');};$('actionClose').onclick=()=>$('actionDialog').close();window.addEventListener('hashchange',fromHash);window.addEventListener('keydown',e=>{if(/INPUT|TEXTAREA/.test(e.target.tagName)||$('actionDialog').open)return;if(e.key==='ArrowRight')move(1);if(e.key==='ArrowLeft')move(-1);});fromHash();}catch(e){$('screenTitle').textContent='화면 목록을 불러오지 못했습니다.';$('description').textContent='새로고침해 주세요.';}}
function move(delta){const list=currentList(),index=list.findIndex(x=>x.id===selected);navigate(mode,list[(index+delta+list.length)%list.length].id);}
init();
