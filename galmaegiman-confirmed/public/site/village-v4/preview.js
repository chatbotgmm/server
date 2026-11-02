/* Pure world-space NPC locomotion. No DOM, timers, rendering or global camera state.
 * Paths are approved ground waypoints supplied by scene layout, NOT navmesh guesses.
 * dt is seconds; hidden/drag/focus/hint pause discards elapsed time (no catch-up).
 */
function createVillageWalkers(specs){
 const ids=new Set();
 const states=specs.map((p,i)=>{
  if(p.id==null||ids.has(String(p.id)))throw Error('NPC ids must be unique');ids.add(String(p.id));
  if(!Array.isArray(p.path)||p.path.length<2||p.path.some(q=>!Number.isFinite(q.x)||!Number.isFinite(q.y)))throw Error('NPC needs at least two finite ground waypoints');
  const path=p.path.map(q=>({x:q.x,y:q.y}));
  return {id:String(p.id),path,x:path[0].x,y:path[0].y,target:1,direction:1,facing:p.facing===-1?-1:1,
   speed:Math.max(1,Math.min(60,p.speed||22)),idleDuration:Math.max(.2,p.idleSeconds||2.5),idle:Math.max(0,p.initialIdle??i*.33),walkTime:0,walking:false,frame:0,
   hitWidth:p.hitWidth||64,hitHeight:p.hitHeight||110};
 });
 const pausedId=(ids,id)=>ids instanceof Set?ids.has(id):Array.isArray(ids)?ids.includes(id):ids===id;
 function step(dt,opts={}){
  const delta=Number.isFinite(dt)?Math.min(.05,Math.max(0,dt)):0;
  for(const s of states){
   const pause=opts.paused||opts.hidden||opts.dragging||opts.motion===false||opts.reducedMotion||opts.activeResidentId===s.id||pausedId(opts.hintIds,s.id)||pausedId(opts.focusIds,s.id);
   if(pause||delta===0){s.walking=false;s.frame=0;continue}
   if(s.idle>0){s.idle=Math.max(0,s.idle-delta);s.walking=false;s.frame=0;continue}
   const q=s.path[s.target],dx=q.x-s.x,dy=q.y-s.y,d=Math.hypot(dx,dy),travel=s.speed*delta;
   if(Math.abs(dx)>.001)s.facing=dx<0?-1:1;
   s.walking=true;s.walkTime+=delta;s.frame=Math.floor(s.walkTime*5)%4;
   if(d<=travel){
    s.x=q.x;s.y=q.y;s.idle=s.idleDuration;s.walking=false;s.frame=0;s.walkTime=0;
    if(s.target===s.path.length-1)s.direction=-1;else if(s.target===0)s.direction=1;
    s.target+=s.direction;
   }else{s.x+=dx/d*travel;s.y+=dy/d*travel}
  }
  return poses();
 }
 function poses(){return states.map(s=>({id:s.id,x:s.x,y:s.y,facing:s.facing,direction:s.facing,walking:s.walking,moving:s.walking,frame:s.frame,
  // Accessibility/interaction bounds in world space. Renderer converts via SAME camera.
  hit:{left:s.x-s.hitWidth/2,top:s.y-s.hitHeight,width:s.hitWidth,height:s.hitHeight}}))}
 return {step,update:step,poses};
}

const npcWalkers=createVillageWalkers([{id:'resident-8',path:[{x:4500,y:775},{x:5010,y:775}],speed:30,idleSeconds:4,initialIdle:0,hitWidth:115,hitHeight:135},{id:'resident-9',path:[{x:2750,y:779},{x:3110,y:779}],speed:30,idleSeconds:3.5,initialIdle:1.2,hitWidth:115,hitHeight:135}]);
const files=['identity-ship','identity-hq','identity-records','identity-market','09-ground-alpha-draft','10-distant-bay','11-foreground-rocks','12-water-patch','13-seam-junction','life-lighthouse','identity-coast-panorama','identity-residents','life-props','identity-statue','identity-walk','identity-finger-piers'];
const harborGeometry={ship:{x:4800,anchorY:700,scale:.56},inlet:{left:4380,right:5240,front:746}};
// The basin follows the piers' actual outer deck edges, not the transparent PNG rectangle.
// Upper shore joins the existing y684 waterline; near deck ends meet the plaza at y746.
const quayOpening={backY:684,frontY:746,backLeft:4414,backRight:5206,frontLeft:4398,frontRight:5222};
function traceQuayOpening(inset=0){const q=quayOpening;ctx.moveTo(q.backLeft+inset-camera,0);ctx.lineTo(q.backRight-inset-camera,0);ctx.lineTo(q.backRight-inset-camera,q.backY);ctx.lineTo(q.frontRight-inset-camera,q.frontY-inset);ctx.lineTo(q.frontLeft+inset-camera,q.frontY-inset);ctx.lineTo(q.backLeft+inset-camera,q.backY);ctx.closePath()}
const names=['방범대 본부','기록 지구','항구 광장','시장 거리'];
const centers=[960,2880,4800,6720],world=7680,H=1080;
const c=document.querySelector('canvas'),ctx=c.getContext('2d'),frame=document.querySelector('#frame'),scrub=document.querySelector('#scrub'),status=document.querySelector('#status');
let residentHints=null;
let centerX=4800,camera=0,zoom=1,topY=0,W=1920,loaded=false,images={},drag=null;
const placements=[{id:1,x:850,foot:730,scale:.68,bounds:[65,10,1530,946]},{id:2,x:2870,foot:748,scale:.78,bounds:[15,13,1663,922]},{id:3,x:6800,foot:760,scale:.70,bounds:[30,80,1578,900]}];
function img(id,x,y,w,h){const source=document.querySelector('#mood').value==='evening'?eveningImages[files[id]]||images[files[id]]:images[files[id]];ctx.drawImage(source,x,y,w,h)}
function clamp(v){return Math.max(960,Math.min(6720,v))}
let navTarget=null,velocity=0;
const fixedCamera={close:{zoom:1.36,topY:10},wide:{zoom:1,topY:-25}};
function stopTravel(){tourStart=null;navTarget=null;velocity=0}
function setCamera(v){stopTravel();centerX=clamp(v);scrub.value=centerX;draw()}
function travelTo(v){tourStart=null;velocity=0;navTarget=clamp(v);if(reducedMotion.matches){setCamera(navTarget);return}lastFrame=null;schedule()}
function jump(i){travelTo(centers[i])}
function advanceCamera(dt){if(drag)return;dt=Math.max(0,Math.min(.1,dt));if(navTarget!==null){centerX+= (navTarget-centerX)*(1-Math.exp(-7*dt));if(Math.abs(navTarget-centerX)<.15){centerX=navTarget;navTarget=null}}else if(Math.abs(velocity)>.5){const decay=Math.exp(-5*dt);centerX+=velocity*(1-decay)/5;velocity*=decay}else velocity=0;const bounded=clamp(centerX);if(bounded!==centerX)velocity=0;centerX=bounded;scrub.value=centerX}
function frameCamera(){const f=fixedCamera[document.querySelector('#cameraMode').value]||fixedCamera.close;zoom=f.zoom;topY=f.topY;camera=Math.max(0,Math.min(world-W/zoom,centerX-W/zoom/2))}
scrub.min=960;scrub.max=6720;scrub.value=4800;
const feedback=document.querySelector('#feedback'),zoneStatus=document.querySelector('#zoneStatus');
const phaseNames={morning:'아침',noon:'점심',evening:'저녁',predawn:'새벽'};
function announce(text){feedback.textContent=text}
let lastZoneSummary='';
const nav=document.querySelector('#nav');names.forEach((n,i)=>{const b=document.createElement('button');b.textContent=n;b.setAttribute('aria-pressed',String(i===2));b.onclick=()=>jump(i);nav.append(b)});
let t=0,tourStart=null,recording=false;
const lighting={morning:{tint:'rgba(242,158,68,.29)',lamps:.15},noon:{tint:'rgba(0,0,0,0)',lamps:0},evening:{tint:'rgba(0,0,0,0)',lamps:1,layered:true},predawn:{tint:'rgba(38,66,99,.32)',lamps:.45}};
const lightAnchors={1:[[442,640,40],[906,625,40],[579,366,29],[827,337,29],[1015,777,28],[1185,777,28],[371,780,26],[131,773,23]],2:[[257,547,30],[612,541,30],[367,286,25],[564,274,25],[717,319,25],[1022,708,25],[1332,702,25],[1010,613,23],[1298,598,23],[901,625,23]],3:[[336,420,28],[558,412,28],[142,661,30],[653,650,30],[1027,595,30],[1380,568,30],[1327,746,85]]};
const eveningImages={},paintedLightPatches={};
const mix=(a,b,t)=>a+(b-a)*t;
const unit=v=>Math.max(0,Math.min(1,v));
function palette(stops,y){for(let k=1;k<stops.length;k++){if(y<=stops[k][0]){const a=stops[k-1],b=stops[k],u=unit((y-a[0])/(b[0]-a[0]));return a[1].map((v,i)=>mix(v,b[1][i],u))}}return stops[stops.length-1][1]}
// Runtime color grading of separate source layers: source geometry/alpha remain unchanged.
function eveningPixel(id,r,g,b,x,y){const lum=(.2126*r+.7152*g+.0722*b)/255;
if(id===5){
 if(y<.405){const sky=palette([[0,[27,31,62]],[.19,[72,45,83]],[.32,[146,83,89]],[.405,[68,47,79]]],y);const cloud=unit((lum-.57)/.36);const warmCloud=[176,121,132];const texture=.72+lum*.42;return sky.map((v,i)=>mix(v*texture,warmCloud[i]*(.72+y*.6),cloud*.76))}
 const depth=unit((y-.405)/.595),base=palette([[0,[60,46,84]],[1,[22,43,72]]],depth);const ripple=unit((lum-.35)/.65),reflection=Math.exp(-Math.pow((x-.52)/.27,2));return base.map((v,i)=>v*(.6+lum*.7)+ripple*reflection*[65,33,39][i]);
}
if(id===7){const ripple=unit((lum-.35)/.65),reflection=Math.exp(-Math.pow((x-.52)/.32,2));return [23,42,69].map((v,i)=>v*(.65+lum*.65)+ripple*reflection*[58,31,40][i])}
const grey=lum*255,exposure=id===6?[.30,.34,.45]:[.38,.39,.49];return [r,g,b].map((v,i)=>mix(grey,v,.74)*exposure[i]);
}
function makeSurface(w,h){const out=document.createElement('canvas');out.width=w;out.height=h;return out}
function prepareEveningLayers(){for(let id=0;id<files.length;id++){const im=images[files[id]],out=makeSurface(im.width,im.height),g=out.getContext('2d',{willReadFrequently:true});
if(id!==5&&id!==7){g.filter=id===6?'saturate(82%) brightness(48%)':id===10?'saturate(72%) brightness(60%)':(id===11||id===14)?'saturate(88%) brightness(78%)':'saturate(86%) brightness(66%)';g.drawImage(im,0,0);g.filter='none';g.globalCompositeOperation='source-atop';g.fillStyle='rgba(73,49,63,.06)';g.fillRect(0,0,out.width,out.height);g.globalCompositeOperation='source-over';eveningImages[files[id]]=out;continue}
g.drawImage(im,0,0);const pixels=g.getImageData(0,0,out.width,out.height),d=pixels.data,refs=new Float32Array(out.width);for(let x=0;x<out.width;x++)refs[x]=Math.exp(-Math.pow((x/(out.width-1)-.52)/(id===5?.27:.32),2));
for(let y=0;y<out.height;y++){const fy=y/(out.height-1),isSky=id===5&&fy<.405,base=isSky?palette([[0,[27,31,62]],[.19,[72,45,83]],[.32,[146,83,89]],[.405,[68,47,79]]],fy):id===5?palette([[0,[60,46,84]],[1,[22,43,72]]],unit((fy-.405)/.595)):[23,42,69];
for(let x=0;x<out.width;x++){const n=(y*out.width+x)*4;if(d[n+3]===0)continue;const lum=(.2126*d[n]+.7152*d[n+1]+.0722*d[n+2])/255;
if(isSky){const cloud=unit((lum-.57)/.36)*.76,texture=.72+lum*.42,cloudTone=.72+fy*.6;d[n]=mix(base[0]*texture,176*cloudTone,cloud);d[n+1]=mix(base[1]*texture,121*cloudTone,cloud);d[n+2]=mix(base[2]*texture,132*cloudTone,cloud)}
else{const ripple=unit((lum-.35)/.65)*refs[x],texture=id===5?.6+lum*.7:.65+lum*.65;d[n]=base[0]*texture+ripple*(id===5?65:58);d[n+1]=base[1]*texture+ripple*(id===5?33:31);d[n+2]=base[2]*texture+ripple*(id===5?39:40)}}}g.putImageData(pixels,0,0);eveningImages[files[id]]=out}
for(const [key,anchors] of Object.entries({...lightAnchors,0:[[83,788,28],[1250,766,28],[1290,789,28]]})){const id=Number(key),im=images[files[id]];paintedLightPatches[id]=anchors.map(([x,y,r])=>{const radius=r*1.3,size=Math.ceil(radius*2),out=makeSurface(size,size),g=out.getContext('2d');g.drawImage(im,x-radius,y-radius,size,size,0,0,size,size);g.globalCompositeOperation='destination-in';const mask=g.createRadialGradient(radius,radius,r*.10,radius,radius,radius);mask.addColorStop(0,'rgba(255,255,255,1)');mask.addColorStop(.42,'rgba(255,255,255,.90)');mask.addColorStop(1,'rgba(255,255,255,0)');g.fillStyle=mask;g.fillRect(0,0,size,size);return {image:out,x,y,r,radius,size}})}
}
// Restore actual painted lamp/fire detail, not a solid circular bright dot.
function objectLights(id,x,y,scale){if(document.querySelector('#mood').value!=='evening')return;for(const p of paintedLightPatches[id]||[]){ctx.save();ctx.globalCompositeOperation='screen';glow(x+p.x*scale,y+p.y*scale,p.r*scale*1.8,.14);ctx.restore();ctx.save();ctx.globalAlpha=.92;ctx.drawImage(p.image,x+(p.x-p.radius)*scale,y+(p.y-p.radius)*scale,p.size*scale,p.size*scale);ctx.restore();if(id===0){ctx.save();ctx.globalCompositeOperation='screen';ctx.translate(x+p.x*scale,y+p.y*scale);ctx.scale(.45,1);glow(0,0,17*scale,.72);ctx.restore()}}}
function glow(x,y,r,a){const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,`rgba(255,231,158,${a})`);g.addColorStop(.18,`rgba(255,187,70,${a*.8})`);g.addColorStop(1,'rgba(255,148,40,0)');ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2)}
function drawLighting(phase){if(phase==='evening')return;const l=lighting[phase];ctx.save();ctx.fillStyle=l.tint;ctx.fillRect(0,0,W,H);
if(phase==='morning'){const g=ctx.createLinearGradient(0,0,W,H*.85);g.addColorStop(0,'rgba(255,217,137,.40)');g.addColorStop(.45,'rgba(255,181,91,.10)');g.addColorStop(1,'rgba(54,75,105,.16)');ctx.fillStyle=g;ctx.fillRect(0,0,W,H)}
if(l.lamps){ctx.globalCompositeOperation='screen';for(const p of placements){const left=p.x-(p.bounds[0]+p.bounds[2])/2*p.scale,top=p.foot-p.bounds[3]*p.scale;for(const [x,y,r] of lightAnchors[p.id])glow((left+x*p.scale-camera)*zoom,(top+y*p.scale-topY)*zoom,r*p.scale*zoom,l.lamps*.86)}
// Ship lantern coordinates follow the same bob/roll transform as the painted ship.
const moving=document.querySelector('#motion').checked&&!reducedMotion.matches,bob=moving?Math.sin(t*1.3)*9:0,angle=moving?Math.sin(t*.9)*.009:0;
for(const [x,y] of [[83,788],[1250,766],[1290,789]]){const dx=(x-687)*harborGeometry.ship.scale,dy=(y-1123)*harborGeometry.ship.scale,wx=4800+dx*Math.cos(angle)-dy*Math.sin(angle),wy=harborGeometry.ship.anchorY+bob+dx*Math.sin(angle)+dy*Math.cos(angle);glow((wx-camera)*zoom,(wy-topY)*zoom,25*zoom,l.lamps*.9)}}ctx.restore()}
// Living-harbor resources are independent image layers, never baked into UI or replacement scenery.
const lifeLayout={
 statue:{x:5275,foot:782,height:330,bounds:[100,17,965,1490]},
 lighthouse:{x:5350,base:480,width:250,anchorX:.493,anchorY:.987,lanternX:.493,lanternY:.207},
 waterfront:[{x:150,base:378,width:720},{x:1830,base:378,width:700},{x:3100,base:378,width:680},{x:3900,base:378,width:640},{x:5040,base:378,width:680},{x:6110,base:378,width:730},{x:7050,base:378,width:620}],
 residentRects:[{x:0,y:135,w:611,h:660,foot:645/660},{x:611,y:135,w:593,h:660,foot:645/660},{x:1204,y:135,w:570,h:660,foot:645/660}],
 propRects:[{x:0,y:0,w:660,h:724,foot:675/724},{x:660,y:0,w:800,h:724,foot:673/724},{x:1460,y:0,w:712,h:724,foot:686/724}],props:[{x:320,foot:757,height:122,sprite:0},{x:1510,foot:749,height:100,sprite:2},{x:2250,foot:758,height:114,sprite:2},{x:3500,foot:752,height:100,sprite:2},{x:4190,foot:749,height:125,sprite:0},{x:5560,foot:752,height:116,sprite:0},{x:6130,foot:753,height:130,sprite:1},{x:7330,foot:747,height:110,sprite:1}],
 residents:[{x:590,foot:765,height:140,sprite:0,phase:.2},{x:1260,foot:758,height:131,sprite:1,phase:2.0},{x:2410,foot:765,height:138,sprite:2,phase:1.0},{x:3370,foot:758,height:132,sprite:0,phase:2.6},{x:4310,foot:765,height:140,sprite:1,phase:.7},{x:5420,foot:755,height:130,sprite:2,phase:1.6},{x:6250,foot:765,height:138,sprite:0,phase:2.3},{x:7220,foot:758,height:133,sprite:1,phase:.1}]
};
function phaseIsLit(){const phase=document.querySelector('#mood').value;return phase==='evening'||phase==='predawn'}
function lifeMotion(){return document.querySelector('#motion').checked&&!reducedMotion.matches}
function seaContact(x,y,rx,ry,alpha=.18){ctx.save();ctx.translate(x-camera,y);ctx.scale(1,ry/rx);const g=ctx.createRadialGradient(0,0,rx*.10,0,0,rx);g.addColorStop(0,`rgba(22,35,57,${alpha})`);g.addColorStop(1,'rgba(22,35,57,0)');ctx.fillStyle=g;ctx.fillRect(-rx,-rx,rx*2,rx*2);ctx.restore()}
function drawWaterfront(){const width=1500,base=400,im=images[files[10]],h=width*im.height/im.width,visible=W/zoom,progress=unit(camera/Math.max(1,world-visible)),left=width>=visible?-(width-visible)*progress:(visible-width)*.5;ctx.save();ctx.globalAlpha=.56;img(10,left,base-h*(610/724),width,h);ctx.restore()}
function drawMooringDetails(){const motion=lifeMotion(),bob=motion?Math.sin(t*1.3)*9:0,roll=motion?Math.sin(t*.9)*.009:0,ss=harborGeometry.ship.scale;ctx.save();ctx.lineCap='round';for(const [px,py,sx,sy] of [[4450,650,270,986],[5178,646,1240,990]]){const dx=(sx-687)*ss,dy=(sy-1123)*ss,x=4800+dx*Math.cos(roll)-dy*Math.sin(roll),y=harborGeometry.ship.anchorY+bob+dx*Math.sin(roll)+dy*Math.cos(roll);ctx.beginPath();ctx.moveTo(px-camera,py);ctx.quadraticCurveTo((px+x)*.5-camera,Math.max(py,y)+13,x-camera,y);ctx.strokeStyle='rgba(73,52,32,.82)';ctx.lineWidth=3;ctx.stroke();ctx.strokeStyle='rgba(198,162,107,.62)';ctx.lineWidth=1;ctx.stroke()}
ctx.strokeStyle=phaseIsLit()?'rgba(165,190,207,.28)':'rgba(216,238,235,.42)';ctx.lineWidth=1.2;for(let i=0;i<6;i++){const bobble=motion?Math.sin(t*.9+i*1.7)*1.1:0,x=4545+i*88,y=694+(i%3)*3+bobble;ctx.beginPath();ctx.moveTo(x-camera,y);ctx.quadraticCurveTo(x+20-camera,y+1.5,x+40+(i%2)*13-camera,y-.6);ctx.stroke()}ctx.restore()}
function drawFingerPiers(){const im=images[files[15]],width=harborGeometry.inlet.right-harborGeometry.inlet.left,scale=width/im.width,top=harborGeometry.inlet.front-570*scale;img(15,harborGeometry.inlet.left-camera,top,width,im.height*scale)}
function drawLighthouse(){const p=lifeLayout.lighthouse,im=images[files[9]],h=p.width*im.height/im.width,left=p.x-p.width*p.anchorX-camera,top=p.base-h*p.anchorY;
if(phaseIsLit()){const lx=left+p.width*p.lanternX,ly=top+h*p.lanternY,angle=-.09+(lifeMotion()?Math.sin(t*.20)*.12:0);ctx.save();ctx.translate(lx,ly);ctx.scale(-1,1);ctx.rotate(angle);const beam=ctx.createLinearGradient(0,0,700,0);beam.addColorStop(0,'rgba(255,219,135,.11)');beam.addColorStop(.35,'rgba(255,219,135,.035)');beam.addColorStop(1,'rgba(255,219,135,0)');ctx.fillStyle=beam;ctx.beginPath();ctx.moveTo(0,-5);ctx.lineTo(700,-95);ctx.lineTo(700,95);ctx.lineTo(0,5);ctx.closePath();ctx.fill();ctx.restore()}
seaContact(p.x,p.base,82,10,.28);img(9,left,top,p.width,h);ctx.save();ctx.strokeStyle=phaseIsLit()?'rgba(195,192,206,.26)':'rgba(218,242,246,.38)';ctx.lineWidth=1.5;const lap=lifeMotion()?Math.sin(t*.8)*2:0;ctx.beginPath();ctx.ellipse(p.x-camera,p.base+lap,87,8,0,.1,Math.PI-.1);ctx.stroke();ctx.restore();if(phaseIsLit()){ctx.save();ctx.globalCompositeOperation='screen';glow(left+p.width*p.lanternX,top+h*p.lanternY,20,.7);ctx.restore()}}
function drawWarmPath(){if(document.querySelector('#mood').value!=='evening')return;ctx.save();ctx.globalCompositeOperation='screen';for(const x of [570,1200,2430,3260,4320,5410,6260,7100]){ctx.save();ctx.translate(x-camera,765);ctx.scale(1,.23);glow(0,0,155,.11);ctx.restore()}ctx.restore()}
function residentPose(p){const speaking=residentHints?.activeResidentId()==='resident-'+lifeLayout.residents.indexOf(p);const breath=lifeMotion()&&!speaking?Math.sin(t*1.25+p.phase)*.012:0;return {x:p.x,y:p.foot,scaleY:1+breath}}
function drawStatue(){const p=lifeLayout.statue,im=images[files[13]],b=p.bounds,scale=p.height/(b[3]-b[1]),w=im.width*scale,h=im.height*scale,left=p.x-camera-(b[0]+b[2])*.5*scale,top=p.foot-b[3]*scale;
ctx.save();ctx.fillStyle='rgba(26,24,37,.24)';ctx.beginPath();ctx.ellipse(p.x-camera,p.foot+2,(b[2]-b[0])*scale*.42,9,0,0,Math.PI*2);ctx.fill();ctx.restore();img(13,left,top,w,h);
if(phaseIsLit()){ctx.save();ctx.globalCompositeOperation='screen';ctx.translate(p.x-camera,p.foot-15);ctx.scale(1,.35);glow(0,0,75,.12);ctx.restore()}}
function drawProps(){const source=document.querySelector('#mood').value==='evening'?eveningImages[files[12]]:images[files[12]];for(const p of lifeLayout.props){const r=lifeLayout.propRects[p.sprite],h=p.height,w=h*r.w/r.h;ctx.drawImage(source,r.x,r.y,r.w,r.h,p.x-camera-w/2,p.foot-h*r.foot,w,h)}}
const walkFrames=[{x:0,w:546,cx:290.5,foot:648},{x:546,w:508,cx:253.5,foot:647},{x:1054,w:524,cx:261.5,foot:648},{x:1578,w:560,cx:259.5,foot:652}];
function drawWalkers(){const source=document.querySelector('#mood').value==='evening'?eveningImages[files[14]]:images[files[14]];for(const p of npcWalkers.poses()){const crop=walkFrames[p.frame],s=.247;ctx.save();ctx.fillStyle='rgba(29,24,36,.22)';ctx.beginPath();ctx.ellipse(p.x-camera,p.y,39,4,0,0,Math.PI*2);ctx.fill();ctx.translate(p.x-camera,p.y);ctx.scale(p.direction,1);ctx.drawImage(source,crop.x,0,crop.w,735,-crop.cx*s,-crop.foot*s,crop.w*s,735*s);ctx.restore()}}
function drawResidents(){const source=document.querySelector('#mood').value==='evening'?eveningImages[files[11]]:images[files[11]];for(const p of lifeLayout.residents){const crop=lifeLayout.residentRects[p.sprite],pose=residentPose(p),h=p.height,w=h*crop.w/crop.h;ctx.save();ctx.fillStyle='rgba(29,24,36,.22)';ctx.beginPath();ctx.ellipse(pose.x-camera,pose.y,w*.28,4,0,0,Math.PI*2);ctx.fill();ctx.translate(pose.x-camera,pose.y);ctx.scale(1,pose.scaleY);ctx.drawImage(source,crop.x,crop.y,crop.w,crop.h,-w/2,-h*crop.foot,w,h);ctx.restore()}}
/* World-space water overlay. Call after bay background, before waterfront/ship/quay.
 * ctx already has the scene camera scale; x positions are translated by camera.
 * No timers/state: caller owns hidden-page pause; motion=false is deterministic.
 */
function drawHarborWater(ctx,{camera=0,t=0,width=1920,phase='noon',motion=true,waterTop=390,quayY=684,worldWidth=7680}={}){
 const time=motion?t:0;
 const palettes={
  morning:{wave:'255,218,161',shadow:'77,124,145',foam:'245,232,195',warm:'255,190,106'},
  noon:{wave:'212,246,246',shadow:'44,116,143',foam:'229,250,241',warm:'255,223,157'},
  evening:{wave:'160,192,211',shadow:'47,74,109',foam:'188,213,220',warm:'255,191,112'},
  predawn:{wave:'156,181,209',shadow:'48,78,115',foam:'179,202,220',warm:'243,186,115'}
 };
 const p=palettes[phase]||palettes.noon;
 const hash=n=>{const v=Math.sin(n*127.1+311.7)*43758.5453;return v-Math.floor(v)};
 ctx.save();ctx.translate(-camera,0);
 ctx.beginPath();ctx.rect(Math.max(0,camera),waterTop,Math.min(width,worldWidth-camera),quayY-waterTop);ctx.clip();
 ctx.lineCap='round';
 // Fixed irregular world samples: only local wave shape/opacity evolves.
 // Two incommensurate phases avoid synchronized breathing and obvious short loops.
 const first=Math.max(0,Math.floor((camera-160)/130)),last=Math.min(Math.ceil(worldWidth/130),Math.ceil((camera+width+160)/130));
 for(let row=0;row<12;row++){
  const depth=row/11;
  for(let col=first;col<=last;col++){
   const seed=row*1031+col*17,r=hash(seed),phaseA=time*(.63+hash(seed+45)*.27)+r*6.283;
   const phaseB=time*(1.07+hash(seed+93)*.19)+hash(seed+58)*6.283;
   const x=col*130+hash(seed+9)*102;
   const y=waterTop+12+depth*(quayY-waterTop-37)+(hash(seed+23)-.5)*17;
   const swell=Math.sin(phaseA)*.65+Math.sin(phaseB)*.35;
   const len=10+depth*30+hash(seed+19)*31;
   const opacity=(.10+.10*depth)*(.6+.4*swell);
   ctx.strokeStyle=`rgba(${p.wave},${opacity})`;ctx.lineWidth=.7+depth*.9;
   ctx.beginPath();ctx.moveTo(x-len/2,y+swell*.8);
   ctx.bezierCurveTo(x-len*.22,y-1.6-depth*1.8+Math.sin(phaseB),x+len*.15,y+1.5+swell*1.5,x+len/2,y-swell*.8);ctx.stroke();
   // Small companion glint, offset asymmetrically rather than identical parallel bars.
   if(r>.62){ctx.strokeStyle=`rgba(${p.wave},${opacity*.55})`;ctx.lineWidth=.7;ctx.beginPath();ctx.moveTo(x+len*.24,y+4);ctx.quadraticCurveTo(x+len*.41,y+3+swell,x+len*.65,y+4);ctx.stroke()}
  }
 }
 // Irregular, softly curved fragments of lighthouse light. No ladder-like spacing.
 const strength=phase==='evening'?.22:phase==='predawn'?.14:phase==='morning'?.06:.025;
 for(let i=0;i<23;i++){
  const q=hash(i+200),y=waterTop+57+i*9+(hash(i+719)-.5)*7,spread=6+i*1.1;
  const wave=Math.sin(time*.91+i*2.17),x=5350+(hash(i+471)-.5)*spread+wave*3;
  ctx.strokeStyle=`rgba(${p.warm},${strength*(.3+.7*q)*(.75+.25*Math.sin(time*1.13+i))})`;ctx.lineWidth=.8+q*.7;
  ctx.beginPath();ctx.moveTo(x-spread*(.12+hash(i+333)*.5),y+wave);
  ctx.quadraticCurveTo(x,y+wave+1.5,x+spread*(.18+hash(i+222)*.4),y+wave-.6);ctx.stroke();
 }
 // Hull disturbance shares the ship's 1.3 rad/s bob and .9 rad/s roll.
 // Smooth sinusoidal displacement, not modulo resets or continuously expanding rings.
 const bob=Math.sin(time*1.3)*9,roll=Math.sin(time*.9)*.009;
 for(const side of [-1,1])for(let i=0;i<3;i++){
  const wave=Math.sin(time*1.3-i*.8),x=4800+side*(227+i*27),y=653+i*8+bob*.25+side*roll*190;
  ctx.strokeStyle=`rgba(${p.foam},${(.10-i*.022)*(.65+.35*wave)})`;ctx.lineWidth=1.1;
  ctx.beginPath();ctx.moveTo(x-side*21,y-2);ctx.quadraticCurveTo(x,y+3+wave*2,x+side*(29+i*6),y-1);ctx.stroke();
 }
 // Seaward edge only; asynchronous short lapping marks don't make a moving seam cover.
 for(let x=Math.floor((camera-90)/117)*117;x<camera+width+90;x+=117){
  const phaseOffset=hash(x+777)*6.28,swell=Math.sin(time*1.3+phaseOffset),length=16+hash(x+34)*23;
  ctx.strokeStyle=`rgba(${p.foam},${.055+.075*(swell+1)/2})`;ctx.lineWidth=1;
  const y=quayY-6-(swell+1)*1.8;
  ctx.beginPath();ctx.moveTo(x,y);ctx.quadraticCurveTo(x+length*.44,y-1.5,x+length,y+.4);ctx.stroke();
 }
 ctx.restore();
}


function draw(){if(!loaded)return;if(c.width!==W)c.width=W;if(c.height!==H)c.height=H;ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,W,H);frameCamera();ctx.setTransform(zoom,0,0,zoom,0,-topY*zoom);const para=document.querySelector('#parallax').checked,offset=para?camera*.1:camera;
// One continuous distant panorama crop across the full town: no mirrored mountain joins.
const panoramaWidth=3600,visibleWidth=W/zoom,panoramaRange=Math.max(0,panoramaWidth-visibleWidth),panoramaProgress=unit(camera/Math.max(1,world-visibleWidth));
const panoramaOffset=panoramaRange*(para?.12+panoramaProgress*.76:panoramaProgress);
img(5,-panoramaOffset,-150,panoramaWidth,1350);
for(let i=0;i<4;i++)img(7,i*1920-camera,540,1920,640);
ctx.save();ctx.beginPath();ctx.rect(-camera,390,world,684-390);traceQuayOpening(1);ctx.clip();drawHarborWater(ctx,{camera,t,width:W/zoom,phase:document.querySelector('#mood').value,motion:lifeMotion(),waterTop:390,quayY:harborGeometry.inlet.front});ctx.restore();drawWaterfront();drawLighthouse();
// Ship sits behind quay: retained silhouette and source-alpha; hull base partly occluded.
seaContact(harborGeometry.ship.x,690,285,7,.14);
const motion=document.querySelector('#motion').checked&&!reducedMotion.matches;const ship=images[files[0]],ss=harborGeometry.ship.scale,bob=motion?Math.sin(t*1.3)*9:0;ctx.save();ctx.beginPath();ctx.rect(4300-camera,0,1100,690);ctx.clip();ctx.translate(4800-camera,harborGeometry.ship.anchorY+bob);ctx.rotate(motion?Math.sin(t*.9)*.009:0);img(0,-687*ss,-1123*ss,ship.width*ss,ship.height*ss);objectLights(0,-687*ss,-1123*ss,ss);ctx.restore();
ctx.save();ctx.beginPath();ctx.rect(-camera,0,world,H);traceQuayOpening();ctx.clip('evenodd');for(let i=0;i<4;i++)img(4,i*1920-camera,280,1920,720);ctx.restore();
// Deck posts stand in front of the quay, while underwater supports stay below the plaza lip.
ctx.save();ctx.beginPath();ctx.rect(-camera,0,world,harborGeometry.inlet.front);ctx.clip();drawFingerPiers();drawMooringDetails();ctx.restore();
// Seam cover disabled by design: no overlay patches across district boundaries.
for(const p of placements){const im=images[files[p.id]],b=p.bounds;ctx.save();ctx.fillStyle='rgba(26,23,35,.24)';ctx.beginPath();ctx.ellipse(p.x-camera,p.foot+4,(b[2]-b[0])*p.scale*.43,18,0,0,Math.PI*2);ctx.fill();ctx.restore();img(p.id,p.x-(b[0]+b[2])/2*p.scale-camera,p.foot-b[3]*p.scale,im.width*p.scale,im.height*p.scale);objectLights(p.id,p.x-(b[0]+b[2])/2*p.scale-camera,p.foot-b[3]*p.scale,p.scale)}
drawWarmPath();drawProps();drawStatue();drawResidents();drawWalkers();
if(document.querySelector('#fg').checked){const speed=para?1.04:1;for(const x of [-150,1730,3700,5650,7540])img(6,x-camera*speed,785,860,322.5)}
ctx.setTransform(1,0,0,1,0,0);
const mood=document.querySelector('#mood').value;drawLighting(mood);
if(document.querySelector('#risk').checked){ctx.lineWidth=2;ctx.strokeStyle='#ff6969';ctx.setLineDash([10,8]);for(const x of [1920,3840,5760]){ctx.beginPath();ctx.moveTo((x-camera)*zoom,Math.max(0,(330-topY)*zoom));ctx.lineTo((x-camera)*zoom,H);ctx.stroke()}ctx.strokeStyle='#ffe39d';ctx.strokeRect(32,32,W-64,H-64);ctx.setLineDash([]);ctx.font='22px system-ui';ctx.fillStyle='#14304cdd';ctx.fillRect(42,42,550,44);ctx.fillStyle='#ffe39d';ctx.fillText('QA SAFE AREA 32px · red = raw tile joins',56,72)}
residentHints?.update();
status.textContent=`카메라 중심 ${Math.round(centerX)} · 확대 ${zoom.toFixed(2)}배 · 높이 ${topY.toFixed(0)} · 보이는 범위 ${camera.toFixed(0)}–${(camera+W/zoom).toFixed(0)} / 7680 · 화면 ${W} × 1080`;
const activeZone=Math.max(0,Math.min(3,Math.floor(centerX/1920)));
[...nav.children].forEach((b,i)=>{b.classList.toggle('active',i===activeZone);b.setAttribute('aria-pressed',String(i===activeZone))});
const summary=names[activeZone]+' · '+phaseNames[mood];scrub.setAttribute('aria-valuetext',summary+' 근처');
if(summary!==lastZoneSummary){zoneStatus.textContent=summary;lastZoneSummary=summary} window.previewState={camera,centerX,zoom,topY,width:W,loaded,centers,mood,reducedMotion:reducedMotion.matches,navTarget,velocity};}
c.addEventListener('pointerdown',e=>{stopTravel();drag={x:e.clientX,centerX,zoom,lastCenter:centerX,lastMs:performance.now(),lastVelocity:0};c.setPointerCapture(e.pointerId)});
c.addEventListener('pointermove',e=>{if(!drag)return;const now=performance.now(),next=clamp(drag.centerX-(e.clientX-drag.x)*W/c.clientWidth/drag.zoom),dt=Math.max(8,now-drag.lastMs);drag.lastVelocity=Math.max(-2400,Math.min(2400,(next-drag.lastCenter)*1000/dt));drag.lastCenter=next;drag.lastMs=now;centerX=next;scrub.value=centerX;draw()});
function releaseDrag(cancelled=false){if(!drag)return;velocity=cancelled||reducedMotion.matches||performance.now()-drag.lastMs>80?0:drag.lastVelocity;drag=null;lastFrame=null;schedule()}
c.addEventListener('pointerup',()=>releaseDrag());c.addEventListener('pointercancel',()=>releaseDrag(true));c.addEventListener('lostpointercapture',()=>releaseDrag(true));
c.addEventListener('keydown',e=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();travelTo(e.key==='Home'?960:e.key==='End'?6720:(navTarget??centerX)+(e.key==='ArrowLeft'?-240:240))}});
c.addEventListener('wheel',e=>{e.preventDefault();travelTo((navTarget??centerX)+(Math.abs(e.deltaX)>Math.abs(e.deltaY)?e.deltaX:e.deltaY)*W/c.clientWidth/zoom)},{passive:false});
scrub.oninput=()=>setCamera(+scrub.value);document.querySelector('#ratio').onchange=e=>{W=e.target.value==='20/9'?2400:1920;frame.style.aspectRatio=e.target.value;draw()};document.querySelector('#cameraMode').onchange=draw;for(const id of ['parallax','risk','fg'])document.querySelector('#'+id).onchange=draw;
Promise.all(files.map(n=>new Promise((resolve,reject)=>{const im=new Image;im.onload=()=>{images[n]=im;resolve()};im.onerror=()=>reject(n);im.src='assets/'+n+'.png'}))).then(()=>{prepareEveningLayers();loaded=true;announce('마을 이미지를 불러왔습니다. 구역을 선택하거나 화면을 밀어 이동하세요.');draw()}).catch(()=>announce('마을 이미지를 불러오지 못했습니다. 페이지를 새로고침해 주세요.'));

document.querySelector('#mood').onchange=draw;
const reducedMotion=window.matchMedia('(prefers-reduced-motion: reduce)');
if(reducedMotion.matches)document.querySelector('#motion').checked=false;
let raf=null,lastFrame=null,recorder=null,recordTimer=null,recordURL=null;
function schedule(){if(raf===null&&!document.hidden)raf=requestAnimationFrame(loop)}
function loop(ms){raf=null;if(document.hidden)return;const delta=lastFrame===null?0:Math.min(100,ms-lastFrame);lastFrame=ms;if(!reducedMotion.matches)t+=delta/1000;advanceCamera(delta/1000);npcWalkers.update(delta/1000,{paused:document.hidden||!!drag||navTarget!==null||Math.abs(velocity)>.5||tourStart!==null,activeResidentId:residentHints?.activeResidentId(),reducedMotion:reducedMotion.matches,motion:document.querySelector('#motion').checked});
if(tourStart!==null){const elapsed=(ms-tourStart)/1000;const anchors=[4800,6720,2880,960];const step=Math.min(3,Math.floor(elapsed/6));const local=elapsed-step*6;const next=anchors[Math.min(3,step+1)];const u=Math.max(0,Math.min(1,(local-4)/2));const blend=reducedMotion.matches?0:u*u*(3-2*u);centerX=clamp(anchors[step]+(next-anchors[step])*blend);scrub.value=centerX;setMood(['noon','evening','predawn','morning'][step],false)}
draw();if((document.querySelector('#motion').checked&&!reducedMotion.matches)||tourStart!==null||navTarget!==null||Math.abs(velocity)>.5)schedule()}
const timeListeners=new Set();
function setMood(value,redraw=true){if(!['morning','noon','evening','predawn'].includes(value))return;document.querySelector('#mood').value=value;GmmPreviewPhase.updateLinks(value);window.villageTimeState={phase:value,source:'manual-preview'};for(const fn of timeListeners)fn({phase:value,mode:'manual-preview',clockConnected:false});if(redraw)draw()}
window.setVillageTime=setMood;window.GmmTimeTheme={getState:()=>({phase:document.querySelector('#mood').value,mode:'manual-preview',clockConnected:false}),setPhase:phase=>setMood(phase),subscribe:fn=>{timeListeners.add(fn);return()=>timeListeners.delete(fn)}};setMood(GmmPreviewPhase.read(location.search),false);
document.querySelector('#mood').onchange=e=>setMood(e.target.value);
document.querySelector('#motion').onchange=()=>{lastFrame=null;draw();schedule()};
reducedMotion.addEventListener('change',()=>{if(reducedMotion.matches)document.querySelector('#motion').checked=false;lastFrame=null;draw();schedule()});
document.addEventListener('visibilitychange',()=>{lastFrame=null;if(document.hidden){releaseDrag(true);stopTravel();if(raf!==null)cancelAnimationFrame(raf);raf=null;stopRecording()}else{draw();schedule()}});
function stopRecording(){if(recordTimer)clearTimeout(recordTimer);recordTimer=null;if(recorder&&recorder.state!=='inactive')recorder.stop()}
window.addEventListener('pagehide',()=>{stopRecording();if(recordURL)URL.revokeObjectURL(recordURL)});
const recordButton=document.querySelector('#record');
recordButton.onclick=()=>{if(recording){stopRecording();return}if(!loaded)return;if(!c.captureStream||typeof MediaRecorder==='undefined'){announce('이 브라우저는 녹화를 지원하지 않습니다. 미리보기는 계속 사용할 수 있습니다.');return}
let stream;try{W=1920;centerX=4800;document.querySelector('#cameraMode').value='close';frame.style.aspectRatio='16/9';document.querySelector('#ratio').value='16/9';document.querySelector('#risk').checked=false;draw();stream=c.captureStream(24);const type=MediaRecorder.isTypeSupported('video/webm;codecs=vp9')?'video/webm;codecs=vp9':'video/webm';const rec=new MediaRecorder(stream,{mimeType:type,videoBitsPerSecond:6000000});recorder=rec;const chunks=[];rec.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};const cleanup=()=>{recording=false;tourStart=null;recorder=null;recordButton.textContent='24초 시연 녹화';stream.getTracks().forEach(s=>s.stop());lastFrame=null;draw()};rec.onstop=()=>{if(recordURL)URL.revokeObjectURL(recordURL);const a=document.querySelector('#download');recordURL=URL.createObjectURL(new Blob(chunks,{type:'video/webm'}));a.href=recordURL;a.download='village-motion-qa.webm';a.textContent='시연 영상 저장';a.hidden=false;cleanup();announce('녹화가 끝났습니다. 시연 영상 저장 링크를 사용할 수 있습니다.')};rec.onerror=()=>{cleanup();announce('녹화에 실패했습니다. 미리보기는 계속 사용할 수 있습니다.')};recording=true;tourStart=performance.now();rec.start();recordButton.textContent='녹화 중 · 누르면 중지';announce('시연을 녹화하고 있습니다. 녹화 버튼을 다시 누르면 중지합니다.');recordTimer=setTimeout(stopRecording,24000);schedule()}catch(error){if(stream)stream.getTracks().forEach(s=>s.stop());recording=false;tourStart=null;recorder=null;recordButton.textContent='24초 시연 녹화';announce('녹화를 시작하지 못했습니다. 미리보기는 계속 사용할 수 있습니다.')}};
schedule();

window.VillageCameraQA={step:advanceCamera,setCamera,travelTo,lighting,fixedCamera,eveningPixel,eveningLayersReady:()=>Object.keys(eveningImages).length===files.length,lifeLayout,residentPose,npcWalkers,walkFrames,harborGeometry};

if(window.GmmResidentHints){
residentHints = window.GmmResidentHints.mount({
  canvas: c, host: frame,
  controls: document.querySelector('.controls'),
  getScene: () => {
    const r=c.getBoundingClientRect();
    const sx=x=>(x-camera)*zoom*r.width/W;
    const sy=y=>(y-topY)*zoom*r.height/H;
    return {
      camera, zoom, topY, logicalWidth:W, logicalHeight:H,
      ready:loaded, dragging:!!drag, moving:navTarget!==null||Math.abs(velocity)>.5||tourStart!==null,
      residents:lifeLayout.residents.map((p,i)=>{
        const pose=residentPose(p),crop=lifeLayout.residentRects[p.sprite];
        return {id:'resident-'+i,zone:Math.max(0,Math.min(3,Math.floor(p.x/1920))),
          x:pose.x,y:pose.y-p.height*crop.foot*pose.scaleY,foot:pose.y};
      }).concat(npcWalkers.poses().map(p=>({id:p.id,zone:Math.floor(p.x/1920),x:p.x,y:p.y-135,foot:p.y}))),
      // Protect the main ship silhouette; all bounds here are CSS px, canvas-relative.
      // Add any future in-frame labels similarly, without covering the entire walkway.
      obstacles:[{x:sx(4460),y:sy(80),width:sx(5170)-sx(4460),height:sy(690)-sy(80)}]
    };
  },
  onNavigate: jump,
  onHint: announce,
  config:{showMs:5000,gapMin:20000,gapMax:35000,initialMs:3000}
});
window.addEventListener('pagehide',()=>residentHints.destroy(),{once:true});

}
