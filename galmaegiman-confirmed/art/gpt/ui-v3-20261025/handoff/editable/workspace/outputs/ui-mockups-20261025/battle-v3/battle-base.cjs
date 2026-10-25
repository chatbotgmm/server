// S06 battle comparison mockups ONLY. No project or production files are changed.
// Original sprites, backgrounds and UI raster assets remain byte-for-byte unchanged.
// The same scene() composes both versions; only its background argument differs.
const fs = require('fs');
const path = require('path');
const { createCanvas, loadImage, GlobalFonts } = require('@napi-rs/canvas');
const ROOT = path.resolve(__dirname, '../../..');
const ASSETS = path.join(ROOT, 'app-design/battle-assets-v1/assets');
const ROSTER = path.join(ROOT, 'outputs/ui-mockups-20261025/assets/roster');
const FONT = path.join(ROOT, 'recruitment-v3-correction-r3/source/galmaegiman-confirmed/app/android/app/src/main/assets/native/recruitment/fonts');
GlobalFonts.registerFromPath(path.join(FONT, 'NotoSansKR-Regular.ttf'), 'BattleNoto');
GlobalFonts.registerFromPath(path.join(FONT, 'NotoSansKR-Bold.ttf'), 'BattleNotoBold');
const images = {};
const palette = { ink: '#173443', cream: '#fff1cc', gold: '#efd296', sub: '#c7d4d7' };

async function init() {
  for (const name of ['panel-command','panel-hp','button-attack','button-item','button-switch','button-surrender','icon-attack','icon-item','icon-switch','icon-surrender','icon-goldenstrike','icon-shield','decor-corner-tl','decor-corner-tr','decor-corner-bl','decor-corner-br']) {
    images[name] = await loadImage(path.join(ASSETS, name + '.png'));
  }
  images.ally = await loadImage(path.join(ROSTER, 'u01/rear.png'));
  images.enemy = await loadImage(path.join(ROSTER, 'u07/front.png'));
  images.indoor = await loadImage(path.join(ROOT, 'background-sources/bg-navigation-workshop-v1.png'));
  images.outdoor = await loadImage(path.join(ASSETS, 'bg-harbor.png'));
}

function UI(ctx) {
  function text(str, x, y, size = 28, color = palette.cream, bold = false, align = 'left', stroke = false) {
    ctx.save();
    ctx.font = `${size}px ${bold ? 'BattleNotoBold' : 'BattleNoto'}`;
    ctx.textAlign = align; ctx.textBaseline = 'top';
    if (stroke) { ctx.lineWidth = 5; ctx.lineJoin = 'round'; ctx.strokeStyle = '#173443b8'; ctx.strokeText(str,x,y); }
    ctx.fillStyle = color; ctx.fillText(str,x,y); ctx.restore();
  }
  function image(name,x,y,w,h,fit='contain') {
    const im = images[name];
    const scale = fit === 'cover' ? Math.max(w/im.width,h/im.height) : Math.min(w/im.width,h/im.height);
    ctx.save();
    if (fit === 'cover') {ctx.beginPath();ctx.rect(x,y,w,h);ctx.clip();}
    ctx.drawImage(im,x+(w-im.width*scale)/2,y+(h-im.height*scale)/2,im.width*scale,im.height*scale);
    ctx.restore();
  }
  // Native 9-slice: corners keep their shape; this does not alter the source raster.
  function nine(name,x,y,w,h,sourceInset,destInset) {
    const im=images[name], s=sourceInset,d=destInset;
    const sx=[0,s,im.width-s], sy=[0,s,im.height-s];
    const sw=[s,im.width-2*s,s],sh=[s,im.height-2*s,s];
    const dx=[x,x+d,x+w-d],dy=[y,y+d,y+h-d];
    const dw=[d,w-2*d,d],dh=[d,h-2*d,d];
    for(let row=0;row<3;row++)for(let col=0;col<3;col++)ctx.drawImage(im,sx[col],sy[row],sw[col],sh[row],dx[col],dy[row],dw[col],dh[row]);
  }
  function line(x,y,w) {
    const g=ctx.createLinearGradient(x,y,x+w,y);g.addColorStop(0,'#e2bd5c30');g.addColorStop(.15,'#e2bd5c');g.addColorStop(.85,'#e2bd5c');g.addColorStop(1,'#e2bd5c30');
    ctx.fillStyle=g;ctx.fillRect(x,y,w,2);
    for(const xx of [x+4,x+w-4]){ctx.beginPath();ctx.moveTo(xx,y-4);ctx.lineTo(xx+5,y+1);ctx.lineTo(xx,y+6);ctx.lineTo(xx-5,y+1);ctx.closePath();ctx.fillStyle='#e6c77e';ctx.fill();}
  }
  function shadow(x,y,rx,ry,alpha=.44) {
    ctx.save();ctx.translate(x,y);ctx.scale(rx,ry);
    const g=ctx.createRadialGradient(0,0,.05,0,0,1);g.addColorStop(0,`rgba(36,25,13,${alpha})`);g.addColorStop(.45,`rgba(36,25,13,${alpha*.7})`);g.addColorStop(1,'rgba(36,25,13,0)');
    ctx.fillStyle=g;ctx.beginPath();ctx.arc(0,0,1,0,Math.PI*2);ctx.fill();ctx.restore();
  }
  function bar(x,y,w,h,ratio,type) {
    ctx.save();ctx.beginPath();ctx.roundRect(x,y,w,h,h/2);ctx.fillStyle='#b38d53';ctx.fill();
    ctx.beginPath();ctx.roundRect(x+2,y+2,w-4,h-4,(h-4)/2);ctx.fillStyle='#ebd3a0';ctx.fill();
    ctx.beginPath();ctx.roundRect(x+3,y+3,(w-6)*ratio,h-6,(h-6)/2);
    const g=ctx.createLinearGradient(x,y,x,y+h);if(type==='HP'){g.addColorStop(0,'#4ed2cb');g.addColorStop(.5,'#00a9b8');g.addColorStop(1,'#007c91');}else{g.addColorStop(0,'#80b8f3');g.addColorStop(.5,'#4c85ce');g.addColorStop(1,'#345c9a');}ctx.fillStyle=g;ctx.fill();
    ctx.beginPath();ctx.roundRect(x,y,w,h,h/2);ctx.strokeStyle='#654f2c';ctx.lineWidth=1.4;ctx.stroke();ctx.restore();
  }
  function hud(x,y,w,h,role,name,hp,mp) {
    ctx.save();ctx.shadowColor='#11253270';ctx.shadowBlur=13;ctx.shadowOffsetY=5;nine('panel-hp',x,y,w,h,46,29);ctx.restore();
    text(role+' · '+name,x+30,y+19,29,palette.ink,true);
    text('Lv.12',x+w-31,y+25,21,palette.ink,true,'right');
    [['HP',hp,y+74],['MP',mp,y+112]].forEach(([label,value,yy])=>{
      text(label,x+31,yy-1,21,palette.ink,true);
      bar(x+79,yy+3,w-227,21,1,label);
      text(value,x+w-31,yy-2,22,palette.ink,true,'right');
    });
  }
  function action(x,y,plate,icon,label,sub,light=false) {
    ctx.save();ctx.shadowColor='#00000070';ctx.shadowBlur=7;ctx.shadowOffsetY=4;nine(plate,x,y,297,158,72,28);ctx.restore();
    image(icon,x+23,y+30,82,82);
    const main=light?palette.ink:'#fff2cf',secondary=light?'#465858':'#f6dbac';
    text(label,x+182,y+36,34,main,true,'center',!light);
    text(sub,x+182,y+88,19,secondary,false,'center',!light);
  }
  return {text,image,nine,line,shadow,bar,hud,action};
}

function scene(background,variant) {
  const canvas=createCanvas(1920,900),ctx=canvas.getContext('2d'),u=UI(ctx);
  u.image(background,0,0,1920,800,'cover');
  // Contact shadows are native UI shapes; the transparent character PNGs are unmodified.
  u.shadow(440,716,113,27,.51);u.shadow(440,716,67,12,.35);
  u.shadow(905,548,101,22,.48);u.shadow(905,548,62,11,.30);
  // Small contact patches follow each staggered foot, not just the sprite center.
  u.shadow(390,705,34,8,.44);u.shadow(493,712,37,8,.46);
  u.shadow(832,544,33,7,.43);u.shadow(994,531,31,7,.41);
  // Alpha-bounds: ally visible height 384px; distant enemy height 280px.
  // Feet stay on the same perspective floor plane for both backgrounds.
  u.image('enemy',744,241,322,322);
  u.image('ally',224,317,420,420);

  u.text('스토리 · 기본 훈련',51,18,22,palette.cream,true,'left',true);
  u.hud(45,60,545,167,'상대','불씨','165 / 165','41 / 41');
  u.hud(633,606,520,167,'출전','갈매미맨','154 / 154','38 / 38');

  ctx.save();ctx.shadowColor='#0b2034a0';ctx.shadowBlur=20;ctx.shadowOffsetX=-5;u.nine('panel-command',1190,16,712,770,75,43);ctx.restore();
  // Existing illustrated corner trims, kept clear of controls.
  u.image('decor-corner-tl',1185,10,104,88);
  u.image('decor-corner-tr',1803,10,104,88);
  u.image('decor-corner-bl',1185,700,104,88);
  u.image('decor-corner-br',1803,700,104,88);
  u.text('내 차례',1235,55,36,palette.cream,true);
  u.text('26초',1852,58,29,palette.gold,true,'right');
  u.text('턴 3 · 행동을 선택하세요',1238,112,22,palette.sub);
  u.line(1234,159,619);
  const actions=[
    ['button-attack','icon-attack','공격','기본 공격'],
    ['button-attack','icon-goldenstrike','기술','보유 기술 6개'],
    ['button-switch','icon-shield','집중','MP 25% 회복'],
    ['button-item','icon-item','아이템','보유 아이템'],
    ['button-switch','icon-switch','교체','후보 대원 선택'],
    ['button-surrender','icon-surrender','도망','스토리 · 항복',true]
  ];
  actions.forEach((a,i)=>u.action(1237+(i%2)*316,186+Math.floor(i/2)*173,...a));
  u.line(1276,713,535);
  u.text('출전 1명  ·  후보 5명',1547,732,21,palette.gold,false,'center');

  // Review-only caption sits OUTSIDE the 1920x800 runtime viewport.
  ctx.fillStyle='#f3f0e6';ctx.fillRect(0,800,1920,100);
  ctx.fillStyle='#d5c5a4';ctx.fillRect(0,800,1920,2);
  u.text(variant==='A'?'S06 전투 A 실내 배경':'S06 전투 B 항구 전장',42,831,27,palette.ink,true);
  u.text('기존 전투 UI 복원 · 대원 / UI 배치 동일 · 배경만 비교',574,836,22,'#516472');
  u.text('시안·예시데이터',1877,836,20,'#516472',false,'right');
  return canvas;
}


module.exports={init,scene,UI,images,palette,ROOT,ASSETS};
