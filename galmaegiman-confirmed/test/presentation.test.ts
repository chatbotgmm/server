import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {mask,displayName,matchCharacters,duration} from '../src/presentation.js';
const content=loadContent();
describe('히든 비공개 표시',()=>{
  const d1=content.map.get('D1')!;
  it('미발견 히든 이름은 ???로 가림',()=>{
    expect(d1.introduction).toContain('거울떼갈매미');
    expect(mask(content,d1.introduction,new Set())).not.toContain('거울떼갈매미');
    expect(mask(content,d1.introduction,new Set())).toContain('???');
  });
  it('발견하면 이름 공개',()=>expect(mask(content,d1.introduction,new Set(['H6']))).toContain('거울떼갈매미'));
  it('히든 유닛 표시명',()=>{
    expect(displayName(content.map.get('H1')!,new Set())).toBe('???');
    expect(displayName(content.map.get('H1')!,new Set(['H1']))).toBe('브라자 갈매미맨');
  });
  it('이름 검색: 전체 이름, "갈매미맨" 생략, 부분 일치',()=>{
    expect(matchCharacters(content.characters,'금갑').map(c=>c.id)).toEqual(['U1']);
    expect(matchCharacters(content.characters,'황금').map(c=>c.id)).toEqual(['C2']);
    expect(matchCharacters(content.characters,'갈매미사우루스').map(c=>c.id)).toEqual(['L13']);
  });
  it('남은 시간 표시',()=>{expect(duration(90*60000)).toBe('1시간 30분');expect(duration(40*60000)).toBe('40분');});
});
