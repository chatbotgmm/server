import { loadContent, tierOrder, rarityNames } from '../src/content.js';
import { existsSync, readFileSync } from 'node:fs';
import { loadBattleRules } from '../src/battle/load.js';
const data=loadContent();
const missing:string[]=[];
for(const c of data.characters){
  const path=`public/images/${c.id}.png`;
  if(!existsSync(path)){missing.push(c.id);continue;}
  const bytes=readFileSync(path);
  if(bytes.subarray(0,8).toString('hex')!=='89504e470d0a1a0a')throw new Error(`잘못된 PNG ${c.id}`);
}
console.log(JSON.stringify({
  edition:data.edition,units:data.characters.length,recipes:data.recipes.length,hiddenRecipes:data.recipes.filter(r=>r.hidden).length,
  tiers:Object.fromEntries(tierOrder.map(t=>[rarityNames[t],data.characters.filter(c=>c.rarity===t).length])),
  images:{present:data.characters.length-missing.length,missing:missing.length}
}));
if(missing.length)console.log(`이미지 없는 유닛 ${missing.length}종: 이미지 없이 텍스트로 응답합니다. public/images/<ID>.png 로 추가하면 바로 표시됩니다.`);
// 수비대 대결 데이터(data/battle.yaml): 형식·상성 검사 + 최상위 유닛이 빠짐없이 있는지
const battle=loadBattleRules();
const top=data.characters.filter(c=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity)).map(c=>c.id);
const missingBattle=top.filter(id=>!battle.units.has(id)),extraBattle=[...battle.units.keys()].filter(id=>!top.includes(id));
if(missingBattle.length||extraBattle.length)throw new Error(`battle.yaml 유닛 오류 — 빠짐: ${missingBattle.join(',')||'없음'} / 최상위가 아님: ${extraBattle.join(',')||'없음'}`);
console.log(JSON.stringify({battle:{units:battle.units.size,battleAugments:battle.battleAugments.length,evoAugments:battle.evoAugments.length}}));

