// 검토용 실제 규칙·전투 상태. 계정/DB를 읽거나 바꾸지 않고 View 캡처에 공급해요.
// node --import tsx scripts/elemental-fixtures.mjs <출력폴더>
import {existsSync,mkdirSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {loadContent} from '../src/content.ts';
import {loadBattleRules} from '../src/battle/load.ts';
import {attrKeys} from '../src/battle/core.ts';
import {createBattle,activeOf,skillPreview,skillProblem,legalActions} from '../src/battle/engine.ts';
import {statsOf,slotSkills,learnOffers,promoSkills,xpToNext} from '../src/battle/growth.ts';
import {nativeFighterEffects} from '../src/native.ts';
const output=resolve(process.argv[2]??'/tmp/gmm-elemental-fixtures');mkdirSync(output,{recursive:true});
const R=loadBattleRules(),content=loadContent();
const image=(rec,side='front')=>{
  const stage=existsSync(`public/battle/sprites/${rec.id}_s${rec.stage}_${side}.webp`)?rec.stage:0;
  const file=`${rec.id}_s${stage}_${side}.webp`;
  if(!existsSync(`public/battle/sprites/${file}`))throw new Error(`원본 캐릭터 이미지 누락: ${file}`);
  return `/duel/sprites/${file}`;
};
const record=(id,uid)=>({id,uid,name:content.map.get(id).name,tier:content.map.get(id).rarity,level:30,xp:100,stage:1,evoAugs:[],learned:['@','basic','role','sub','sig'],learnUsed:0,learnPick:0});
for(const name of [...attrKeys,'FIRE-active','GRAVITY-active']){
  const attr=name.split('-')[0],defs=[...R.units.values()].filter(u=>u.attr===attr),def=defs.find(u=>u.role==='ATTACK')??defs[0];
  const rec=record(def.id,'o1'),bench=record(def.id==='I4'?'D1':'I4','o2'),foe=record('D7','foe1');
  const b=createBattle(R,[rec,bench],[foe],74);b.phase='action';for(const s of b.sides){s.offers=null;s.augs=[];}
  const me=activeOf(b,0),target=activeOf(b,1);b.sides[0].fighters[1].hp=Math.floor(b.sides[0].fighters[1].maxHp*.45);
  if(attr==='STONE')target.mods.push({stat:'def',pct:-10,turns:2});
  if(attr==='ICE')target.mods.push({stat:'spd',pct:-15,turns:2});
  if(attr==='THREAD')target.status.bind=2;
  if(attr==='BLOSSOM')me.hp=Math.floor(me.maxHp*.4);
  if(attr==='SHADOW')target.hp=Math.floor(target.maxHp*.3);
  if(name==='FIRE-active')target.status.burn=3;
  if(name==='GRAVITY-active'){me.base.spd=1;target.base.spd=100;}
  b.log=[`${R.attrName(attr)} 속성 전술 검토 · 조합 가치 흔함 ${R.recipeValue(def.id)}마리`];
  const battle={session:`element74-${name}`,revision:0,serverNow:Date.now(),mode:'practice',scene:'harbor',phase:'action',turn:1,itemLimit:3,history:b.log,log:b.log,displayEventVersion:1,displayEventSeq:0,displayEvents:[],displayLogStart:0,items:[],teams:b.sides.map((s,side)=>({active:s.active,comboUsed:s.comboUsed,augments:[],offers:[],needSwitch:false,legal:legalActions(b,side),itemsUsed:0,fighters:s.fighters.map((f,slot)=>{
    const forSlot={...b,sides:b.sides.map((own,index)=>index===side?{...own,active:slot}:own)};
    return {key:f.rec.uid,id:f.rec.id,name:f.name,level:f.rec.level,stage:f.rec.stage,role:f.role,base:f.base,bodyScale:1,image:image(f.rec,side===0?'back':'front'),hp:f.hp,maxHp:f.maxHp,attr:R.attrName(f.attr),attrCode:f.attr,status:f.status,effects:nativeFighterEffects(R,f),skills:f.skills.map(sk=>({...sk,preview:skillPreview(forSlot,side,sk),reason:skillProblem(forSlot,side,sk)}))};
  })}))};
  const unit={...rec,key:rec.uid,stats:statsOf(R,rec),recipeValue:R.recipeValue(rec.id),recipeBonusPct:R.recipeBonus(rec.id),role:def.role,attrCode:def.attr,attr:R.attrName(def.attr),image:image(rec),hp:me.hp,maxHp:me.maxHp,xpNext:xpToNext(R,rec.level),skills:slotSkills(R,rec),learn:learnOffers(R,rec),promo:promoSkills(R,rec),evolve:[]};
  writeFileSync(resolve(output,`${name}.json`),JSON.stringify({scenario:name,battle,unit},null,2));
}
console.log(JSON.stringify({output,fixtures:attrKeys.length+2,accountMutations:0}));
