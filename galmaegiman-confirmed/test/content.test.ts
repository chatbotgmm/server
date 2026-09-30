import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {readFileSync,mkdtempSync,mkdirSync,copyFileSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {parse,stringify} from 'yaml';
const data=loadContent();
describe('75종 콘텐츠',()=>{
  it('75종과 모든 비흔함 조합식 69개',()=>{
    expect(data.characters).toHaveLength(75);expect(data.recipes).toHaveLength(69);
  });
  it('75개 PNG 연결, 수정본25/기존50',()=>{
    expect(data.characters.filter(c=>c.artVersion==='v3')).toHaveLength(25);
    for(const c of data.characters)expect(readFileSync(`public${c.imageUrl}`).subarray(0,8).toString('hex')).toBe('89504e470d0a1a0a');
  });
  it('교배·초반 한마리 밈 없음',()=>{
    const text=readFileSync('data/catalog.yaml','utf8')+readFileSync('data/recipes.yaml','utf8');
    expect(text).not.toMatch(/BREEDING|갈매미 한마리/);
  });
  it('흔함을 시작점으로 모든 결과 도달 가능',()=>{
    const reached=new Set(data.characters.filter(c=>c.rarity==='COMMON').map(c=>c.id));
    for(let pass=0;pass<3;pass++)for(const r of data.recipes)if(r.materials.every(id=>reached.has(id)))reached.add(r.resultId);
    expect(reached.size).toBe(75);
  });
  it('누락된 11종은 콘셉트 표시명, 기존 64종의 이름은 보존',()=>{
    const original=parse(readFileSync('data/catalog.yaml','utf8')).characters as [string,string|null,string,string][];
    expect(data.characters.filter(c=>c.nameStatus==='CONCEPT_DISPLAY')).toHaveLength(11);
    expect(data.characters.filter(c=>c.nameStatus==='REFERENCE')).toHaveLength(64);
    expect(data.characters.every(c=>c.name.length>0&&!/^갈매미맨 [CUSR]\d{3}[MF]$/.test(c.name))).toBe(true);
    expect(data.map.get('C003F')?.name).toBe('비닐봉지 갈매미맨');
    for(const [id,name] of original)if(name!==null)expect(data.map.get(id)?.name).toBe(name);
  });
  function invalidNames(change:(names:Record<string,string>)=>void){
    const root=mkdtempSync(join(tmpdir(),'galmaegiman-names-'));
    try{
      mkdirSync(join(root,'data'));
      for(const file of ['catalog.yaml','recipes.yaml'])copyFileSync(`data/${file}`,join(root,'data',file));
      const names=parse(readFileSync('data/display-names.yaml','utf8'));
      change(names.names);
      writeFileSync(join(root,'data/display-names.yaml'),stringify(names));
      return ()=>{try{return loadContent(root);}finally{rmSync(root,{recursive:true});}};
    }catch(error){rmSync(root,{recursive:true});throw error;}
  }
  it('기존 이름을 표시명 설정으로 덮어쓰지 못함',()=>{
    expect(invalidNames(names=>{names.R001M='덮어쓴 이름';})).toThrow('덮어쓸 수 없습니다');
  });
  it('이름이 다시 빠지면 코드명으로 숨기지 않고 검증 실패',()=>{
    expect(invalidNames(names=>{delete names.C003F;})).toThrow('표시명이 누락되었습니다');
  });
});
