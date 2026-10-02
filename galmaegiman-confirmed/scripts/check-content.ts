import { loadContent, tierOrder, rarityNames } from '../src/content.js';
import { existsSync, readFileSync } from 'node:fs';
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
