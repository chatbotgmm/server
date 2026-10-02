import {describe,it,expect} from 'vitest';
import {loadContent,tiers,topTiers} from '../src/content.js';
const content=loadContent();
describe('118종 콘텐츠',()=>{
  it('등급별 수량 6/11/15/20/20/13/8/8/8/9 = 118',()=>{
    const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
    expect([count('COMMON'),count('UNCOMMON'),count('SPECIAL'),count('RARE'),count('LEGEND'),count('HIDDEN'),count('LIMITED'),count('TRANSCEND'),count('ETERNAL'),count('IMMORTAL')]).toEqual([6,11,15,20,20,13,8,8,8,9]);
    expect(content.characters).toHaveLength(118);
  });
  it('흔함을 뺀 112종 모두 조합식 1개, 재료는 결과보다 낮은 단계',()=>{
    expect(content.recipes).toHaveLength(112);
    for(const r of content.recipes)for(const m of r.materials)expect(tiers[content.map.get(m)!.rarity]).toBeLessThan(tiers[content.map.get(r.resultId)!.rarity]);
  });
  it('히든 13종만 비공개 조합',()=>{
    expect(content.recipes.filter(r=>r.hidden).map(r=>r.resultId).sort()).toEqual([...content.hiddenIds].sort());
    expect(content.hiddenIds).toHaveLength(13);
  });
  it('최상위 33종 시너지: 원형 6그룹×4 + 갈 의복 9',()=>{
    const top=content.characters.filter(c=>topTiers.has(c.rarity));
    expect(top).toHaveLength(33);
    const groups:Record<string,number>={};
    for(const c of top)groups[c.synergy!]=(groups[c.synergy!]??0)+1;
    expect(groups).toEqual({BASIC:4,GOLD:4,DARK:4,SEA:4,BLOSSOM:4,COSMOS:4,OUTFIT:9});
  });
  it('최상위는 다른 조합의 재료가 아님',()=>{
    const top=new Set(content.characters.filter(c=>topTiers.has(c.rarity)).map(c=>c.id));
    expect(content.recipes.flatMap(r=>r.materials).filter(m=>top.has(m))).toEqual([]);
  });
  it('조합식 수량: 금갑 = 기본×2 + 황금',()=>{
    expect(content.recipes.find(r=>r.resultId==='U1')!.materials.sort()).toEqual(['C1','C1','C2']);
  });
  it('경제 수치: 뽑기 비용 2/3/5, 교환 150, 뽑기권 30/10/1시간',()=>{
    const e=content.economy;
    expect([e.gachas.LOW.cost,e.gachas.MID.cost,e.gachas.HIGH.cost]).toEqual([2,3,5]);
    expect(e.gachas.HIGH.weights).toEqual({SPECIAL:79,RARE:20,LEGEND:1});
    expect(e.exchange.snackCost).toBe(150);
    expect(e.tickets).toEqual({welcome:30,claim:10,cooldownHours:1});
    expect(e.rewards.COMMON).toEqual({first:0,duplicate:0});
    expect(e.rewards.IMMORTAL).toEqual({first:1400,duplicate:280});
  });
});
