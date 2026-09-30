import {describe,it,expect} from 'vitest';
import {mkdtempSync,mkdirSync,copyFileSync,readFileSync,writeFileSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {parse,stringify} from 'yaml';
import {loadContent} from '../src/content.js';
import {introduction,receiptText} from '../src/acquisition.js';
const content=loadContent();
function withEdited(file:string,edit:(value:any)=>void,check:(root:string)=>void){
  const root=mkdtempSync(join(tmpdir(),'galmaegiman-flavor-'));
  try{
    mkdirSync(join(root,'data'));
    for(const name of ['catalog.yaml','recipes.yaml','display-names.yaml','flavor.yaml','acquisition-rewards.yaml'])copyFileSync(`data/${name}`,join(root,'data',name));
    const path=join(root,'data',file),value=parse(readFileSync(path,'utf8'));edit(value);writeFileSync(path,stringify(value));
    check(root);
  }finally{rmSync(root,{recursive:true,force:true});}
}
describe('75종 소개와 보상 데이터',()=>{
  it('75종 모두 고유 별칭·등장·기록이 있고 비흔함 69종은 조합 서사가 있음',()=>{
    expect(content.flavors.size).toBe(75);
    for(const key of ['title','arrival','lore'] as const)expect(new Set([...content.flavors.values()].map(f=>f[key])).size).toBe(75);
    for(const c of content.characters)if(c.rarity!=='COMMON')expect(content.flavors.get(c.id)!.fusion).not.toBe('-');
  });
  it('전체 등급 획득 및 조합 소개는 500자 안에 이름·보상을 담음',()=>{
    for(const c of content.characters)for(const kind of ['GACHA','COMBINATION'] as const){
      if(kind==='COMBINATION'&&c.rarity==='COMMON')continue;
      const r=content.rewards[c.rarity as keyof typeof content.rewards];
      const text=`${introduction(content,c,kind)}\n\n재료 10마리를 사용해 1마리를 얻었습니다.\n${receiptText(c,{first:true,count:1,snacks:r.first,snackBalance:2_000_000_000,tierKnown:40,tierTotal:40})}\n획득권 2000000000장 남음`;
      expect(text.length).toBeLessThanOrEqual(500);expect(text).toContain(c.name);
      expect(text).not.toMatch(/[CUSR]\d{3}[MF]|개체 #/);
      if(c.rarity==='RARE')expect(text).toContain(content.flavors.get(c.id)!.lore);
    }
  });
  it('소개만 바꾸어도 기존 DB 콘텐츠 해시 및 캐릭터·레시피 데이터는 그대로',()=>{
    withEdited('flavor.yaml',v=>{v.characters[0][1]='검증용 별칭';},root=>{
      const next=loadContent(root);expect(next.hash).toBe(content.hash);expect(next.characters).toEqual(content.characters);expect(next.recipes).toEqual(content.recipes);
    });
  });
  it.each([
    ['누락',(v:any)=>v.characters.pop()],
    ['중복',(v:any)=>{v.characters[0][0]=v.characters[1][0];}],
    ['과도한 본문',(v:any)=>{v.characters[0][3]='가'.repeat(221);}],
    ['조합 설명 누락',(v:any)=>{v.characters.find((r:string[])=>r[0]==='U001M')[4]='-';}]
  ] as const)('잘못된 소개 데이터 %s는 시작 전 거부',(_name,edit)=>{
    withEdited('flavor.yaml',edit,root=>expect(()=>loadContent(root)).toThrow());
  });
  it.each([
    ['음수',(v:any)=>{v.rewards.RARE.first=-1;}],
    ['소수',(v:any)=>{v.rewards.RARE.first=90.5;}],
    ['기본 재료 교환 보너스',(v:any)=>{v.rewards.COMMON.first=1;}],
    ['등급 역전',(v:any)=>{v.rewards.RARE.first=20;}],
    ['첫 발견보다 높은 중복',(v:any)=>{v.rewards.UNCOMMON.duplicate=11;}]
  ] as const)('잘못된 보상 데이터 %s는 시작 전 거부',(_name,edit)=>{
    withEdited('acquisition-rewards.yaml',edit,root=>expect(()=>loadContent(root)).toThrow());
  });
});
