import { loadContent } from '../src/content.js';
import { readFileSync,writeFileSync,mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
const data=loadContent();
const manifest=data.characters.map(c=>{
  const bytes=readFileSync(`public/images/${c.id}.png`);
  if(bytes.subarray(0,8).toString('hex')!=='89504e470d0a1a0a')throw new Error(`잘못된 PNG ${c.id}`);
  return {id:c.id,name:c.name,nameStatus:c.nameStatus,artVersion:c.artVersion,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')};
});
if(process.argv.includes('--write-manifest')){
  mkdirSync('docs',{recursive:true});
  writeFileSync('docs/asset-manifest.json',JSON.stringify(manifest,null,2)+'\n');
  writeFileSync('docs/ROSTER-AND-RECIPES.md','# 75종 · 조합식 검토\n\n이름이 비어 있던 11종에는 기존 이미지 콘셉트에 따른 표시명을 붙였습니다. 과거 확정 이름을 복원한 것이 아닙니다. 기존 이름 64종은 유지했습니다. 표시명 목록과 적용 범위는 NAME-FIX.md를 참고하세요. 아래 69개 조합식은 테스트 재구성안이며 과거 확정안의 복원본이 아닙니다.\n\n'+data.characters.map(c=>{
    const r=data.recipes.find(r=>r.resultId===c.id);
    return `## ${c.id} · ${c.name}\n\n- 등급: ${c.rarity} / 성별: ${c.sex}\n- 콘셉트: ${c.concept}\n- 문구: ${c.quote}\n- 이미지: ${c.artVersion}\n- 조합: ${r?r.materials.join(' + '):'없음 (기본 재료)'}\n${r?`- 개연성: ${r.story}\n`:''}`;
  }).join('\n'));
}
console.log(JSON.stringify({characters:data.characters.length,recipes:data.recipes.length,v3:manifest.filter(m=>m.artVersion==='v3').length,v2:manifest.filter(m=>m.artVersion==='v2').length,conceptDisplayNames:manifest.filter(m=>m.nameStatus==='CONCEPT_DISPLAY').length}));
