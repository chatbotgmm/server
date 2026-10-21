import {describe, expect, it} from 'vitest';
import {hiddenMaterialHint, mixMaterialLimit} from '../src/craft-hints.js';
import type {Recipe} from '../src/content.js';

const recipes: Recipe[] = [
  {resultId:'public',materials:['a','b'],hidden:false,story:''},
  {resultId:'secret',materials:['a','a','c'],hidden:true,story:''},
  {resultId:'other',materials:['d','e','f','g','h','i'],hidden:true,story:''},
];
describe('미공개 재료 힌트', () => {
  it('전체 조합의 완성 여부와 무관하게 선택한 재료의 단서만 알려 준다', () => {
    expect(hiddenMaterialHint(recipes,new Set(),'a')).toBe(true);
    expect(hiddenMaterialHint(recipes,new Set(),'b')).toBe(false);
    expect(hiddenMaterialHint(recipes,new Set(),'missing')).toBe(false);
    expect(typeof hiddenMaterialHint(recipes,new Set(),'c')).toBe('boolean');
  });
  it('계정의 발견 상태를 반영하고 다른 미발견 조합의 단서는 유지한다', () => {
    expect(hiddenMaterialHint(recipes,new Set(['secret']),'a')).toBe(false);
    expect(hiddenMaterialHint(recipes,new Set(['secret']),'d')).toBe(true);
    expect(hiddenMaterialHint(recipes,new Set(),'a')).toBe(true);
  });
  it('조회로 레시피나 발견 목록을 변경하지 않는다', () => {
    const known = new Set<string>();
    const before = JSON.stringify(recipes);
    for (let i=0;i<10;i++) hiddenMaterialHint(recipes,known,'a');
    expect(JSON.stringify(recipes)).toBe(before);
    expect(known.size).toBe(0);
  });
  it('재료 한도는 네 칸에 고정하지 않고 실제 레시피 개수를 따른다', () => {
    expect(mixMaterialLimit(recipes)).toBe(6);
    expect(mixMaterialLimit([...recipes,{...recipes[0],materials:Array(8).fill('a')}])).toBe(8);
  });
});
