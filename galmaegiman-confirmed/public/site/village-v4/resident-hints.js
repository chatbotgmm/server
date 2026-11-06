/* Preview-only resident dialogue. No storage, network, rewards or gameplay actions. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.GmmResidentHints=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const HINTS=[
 {id:'hq',zone:0,text:'출항 전에 방범대 편성은 확인했어?',detail:'방범대 본부는 편성·모집을 배치할 구역입니다. 지금은 마을 이동 미리보기예요.'},
 {id:'hq-life',zone:0,text:'돌 날개를 쭉! 오늘도 마을 한 바퀴야.',detail:'방범대 본부 구역으로 이동했습니다. 주민 대사는 분위기와 길 안내용입니다.'},
 {id:'records',zone:1,text:'처음 보는 갈매미라면 도감부터 펼쳐봐!',detail:'기록 지구는 도감·조합을 배치할 구역입니다. 이 미리보기에서 도감이나 조합은 실행되지 않습니다.'},
 {id:'records-life',zone:1,text:'기록소는 저쪽이야. 같이 둘러볼래?',detail:'기록 지구로 이동했습니다. 실제 기록소 기능은 연결되지 않은 미리보기입니다.'},
 {id:'harbor',zone:2,text:'우리 배는 항구 광장에 있어. 같이 가볼래?',detail:'항구 광장으로 이동했습니다. 배를 살펴볼 수 있으며 실제 출항은 실행되지 않습니다.'},
 {id:'harbor-life',zone:2,text:'바닷바람 좋다! 잠깐 쉬었다 갈까?',detail:'항구 광장으로 이동했습니다. 이 말풍선은 마을 생활 대사입니다.'},
 {id:'market',zone:3,text:'상인과 대장장이를 찾으면 시장 거리로!',detail:'시장 거리는 상인·대장장이를 배치할 구역입니다. 실제 구매·강화는 실행되지 않습니다.'},
 {id:'market-life',zone:3,text:'오늘도 시장 구경! 넌 어디로 가니?',detail:'시장 거리로 이동했습니다. 상점 기능은 아직 연결되지 않은 미리보기입니다.'}
];
const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
function project(p,s,v){return {id:p.id,zone:p.zone,x:(p.x-s.camera)*s.zoom*v.width/s.logicalWidth,y:(p.y-s.topY)*s.zoom*v.height/s.logicalHeight,foot:(p.foot-s.topY)*s.zoom*v.height/s.logicalHeight}}
function visible(p,v){return p.x>=20&&p.x<=v.width-20&&p.y>=8&&p.foot<=v.height-8&&p.foot>=p.y}
function overlap(a,b){return a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y}
function placement(p,v,size,obstacles=[]){const m=8,w=Math.min(size.width,v.width-2*m),h=size.height;if(w<120||v.height<h+2*m)return null;const options=[{x:p.x-w/2,y:p.y-h-10},{x:p.x+25,y:p.y-h/2},{x:p.x-w-25,y:p.y-h/2},{x:p.x-w/2,y:p.foot+10}];for(const q of options){const r={x:clamp(q.x,m,v.width-w-m),y:clamp(q.y,m,v.height-h-m),width:w,height:h};if(!obstacles.some(o=>overlap(r,o)))return r}return null}
class Scheduler{
 constructor({random=Math.random,showMs=5000,gapMin=20000,gapMax=35000,initialMs=3000}={}){this.random=random;this.showMs=showMs;this.gapMin=gapMin;this.gapMax=gapMax;this.remaining=initialMs;this.active=null;this.lastHint=null;this.seen=new Set();this.enabled=true}
 gap(){return this.gapMin+this.random()*(this.gapMax-this.gapMin)}
 dismiss(){this.active=null;this.remaining=this.gap()}
 setEnabled(on){this.enabled=!!on;this.dismiss()}
 choose(residents,manualId){let choices=residents.flatMap(p=>HINTS.filter(h=>h.zone===p.zone).map(h=>({residentId:p.id,hint:h})));if(manualId!==undefined)choices=choices.filter(x=>x.residentId===manualId);const fresh=choices.filter(x=>!this.seen.has(x.hint.id)&&x.hint.id!==this.lastHint);if(fresh.length)choices=fresh;else{choices=choices.filter(x=>x.hint.id!==this.lastHint);if(!choices.length)return null;this.seen.clear()}if(!choices.length)return null;const a=choices[Math.min(choices.length-1,Math.floor(this.random()*choices.length))];this.seen.add(a.hint.id);this.lastHint=a.hint.id;return a}
 show(residents,id){if(!this.enabled||this.active)return this.active;const a=this.choose(residents,id);if(a){this.active=a;this.remaining=this.showMs}return a}
 tick(dt,residents,{paused=false,hold=false}={}){if(!this.enabled||paused)return this.active;if(this.active&&!residents.some(p=>p.id===this.active.residentId)){this.dismiss();return null}if(!residents.length)return null;if(hold&&this.active)return this.active;this.remaining-=Math.max(0,Math.min(dt,1000));if(this.remaining<=0){if(this.active)this.dismiss();else this.show(residents)}return this.active}
}
function mount({canvas,host,controls,getScene,onNavigate=()=>{},onHint=()=>{},config={}}){
 const doc=host.ownerDocument,scheduler=new Scheduler(config),el=doc.createElement('div'),message=doc.createElement('button'),close=doc.createElement('button'),bar=doc.createElement('div'),toggle=doc.createElement('button'),talk=doc.createElement('button');
 el.className='gmm-resident-bubble';el.hidden=true;el.setAttribute('role','group');el.setAttribute('aria-label','갈매미 주민 이야기');message.type=close.type=toggle.type=talk.type='button';message.className='gmm-resident-message';close.className='gmm-resident-close';close.textContent='×';close.setAttribute('aria-label','주민 말풍선 닫기');el.append(message,close);host.append(el);bar.className='gmm-resident-controls';toggle.textContent='주민 힌트 켜짐';toggle.setAttribute('aria-pressed','true');talk.textContent='주민 이야기';bar.append(toggle,talk);(controls||host.parentElement).append(bar);
 let last=performance.now(),rendered=null,down=null,destroyed=false;
 function scene(){const s=getScene(),r=canvas.getBoundingClientRect(),v={width:r.width,height:r.height};return {s,v,points:(s.residents||[]).map(p=>project(p,s,v)).filter(p=>visible(p,v))}}
 function hide(){el.hidden=true;rendered=null}
 function dismiss(){scheduler.dismiss();hide()}
 function update(){if(destroyed)return;const now=performance.now(),dt=now-last;last=now;const {s,v,points}=scene(),rect=canvas.getBoundingClientRect(),offscreen=rect.bottom<=0||rect.top>=doc.documentElement.clientHeight||rect.right<=0||rect.left>=doc.documentElement.clientWidth;const paused=doc.hidden||offscreen||s.dragging||s.moving||s.ready===false;
 if(paused){hide();return}const a=scheduler.tick(dt,points,{hold:el.contains(doc.activeElement)||el.matches(':hover')});talk.disabled=!points.length||!scheduler.enabled;if(!a){hide();return}const p=points.find(p=>p.id===a.residentId);if(!p){hide();return}
 if(rendered!==a){message.textContent=a.hint.text;message.setAttribute('aria-label',a.hint.text+' 관련 구역 안내 보기');rendered=a}el.style.width=Math.min(270,v.width-16)+'px';el.hidden=false;const b=placement(p,v,{width:Math.min(270,v.width-16),height:el.offsetHeight||84},s.obstacles||[]);if(!b){hide();return}el.style.width=b.width+'px';el.style.left=(canvas.offsetLeft+b.x)+'px';el.style.top=(canvas.offsetTop+b.y)+'px';
 }
 function manual(id){const {s,points}=scene();if(s.dragging||s.moving||s.ready===false||!scheduler.enabled)return;scheduler.show(points,id);update()}
 message.onclick=()=>{const a=scheduler.active;if(!a)return;dismiss();onNavigate(a.hint.zone);onHint('마을 미리보기 · '+a.hint.detail);canvas.focus({preventScroll:true})};close.onclick=()=>{dismiss();talk.focus({preventScroll:true})};el.onkeydown=e=>{if(e.key==='Escape'){e.preventDefault();close.click()}};toggle.onclick=()=>{scheduler.setEnabled(!scheduler.enabled);toggle.textContent=scheduler.enabled?'주민 힌트 켜짐':'주민 힌트 꺼짐';toggle.setAttribute('aria-pressed',String(scheduler.enabled));update()};talk.onclick=()=>manual();
 function pd(e){down={x:e.clientX,y:e.clientY,id:e.pointerId};hide()}
 function pm(e){if(down&&Math.hypot(e.clientX-down.x,e.clientY-down.y)>6)down=null}
 function pu(e){const start=down;down=null;if(!start||start.id!==e.pointerId||Math.hypot(e.clientX-start.x,e.clientY-start.y)>6)return;const {points}=scene(),r=canvas.getBoundingClientRect(),x=e.clientX-r.left,y=e.clientY-r.top;const p=points.find(p=>Math.abs(p.x-x)<=Math.max(18,(p.foot-p.y)*.4)&&y>=p.y&&y<=p.foot);if(p)manual(p.id)}
 function cancel(){down=null}
 function visibility(){last=performance.now();if(doc.hidden)hide();else update()}
 canvas.addEventListener('pointerdown',pd);canvas.addEventListener('pointermove',pm);canvas.addEventListener('pointerup',pu);canvas.addEventListener('pointercancel',cancel);doc.addEventListener('visibilitychange',visibility);const timer=setInterval(update,250);
 return {update,activeResidentId:()=>el.hidden?null:scheduler.active?.residentId,show:manual,dismiss,destroy(){destroyed=true;clearInterval(timer);canvas.removeEventListener('pointerdown',pd);canvas.removeEventListener('pointermove',pm);canvas.removeEventListener('pointerup',pu);canvas.removeEventListener('pointercancel',cancel);doc.removeEventListener('visibilitychange',visibility);el.remove();bar.remove()},scheduler};
}
return {HINTS,Scheduler,project,visible,placement,mount};
});
