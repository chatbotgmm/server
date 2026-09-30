import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {GachaEngine,loadGachaWeights,drawRarities} from '../src/gacha.js';
const content=loadContent();
const weights=loadGachaWeights();
describe('일반 뽑기 확률',()=>{
  it('사용자가 지정한 40/30/20/10 설정',()=>{
    expect(weights).toEqual({COMMON:40,UNCOMMON:30,SPECIAL:20,RARE:10});
  });
  it('0부터 99까지 모든 등급 추첨값의 분포와 경계',()=>{
    const counts:Record<string,number>={COMMON:0,UNCOMMON:0,SPECIAL:0,RARE:0};
    for(let ticket=0;ticket<100;ticket++){
      let call=0;
      const game=new GachaEngine(content.characters,weights,max=>call++===0?(expect(max).toBe(100),ticket):0);
      const actual=game.draw().rarity;
      const expected=ticket<40?'COMMON':ticket<70?'UNCOMMON':ticket<90?'SPECIAL':'RARE';
      expect(actual).toBe(expected);counts[actual]++;
    }
    expect(counts).toEqual(weights);
  });
  it('각 등급의 모든 캐릭터가 동일 크기 색인으로 선택 가능',()=>{
    const tickets={COMMON:0,UNCOMMON:40,SPECIAL:70,RARE:90};
    const reached=new Set<string>();
    for(const rarity of drawRarities){
      const pool=content.characters.filter(c=>c.rarity===rarity);
      for(let index=0;index<pool.length;index++){
        let call=0;
        const game=new GachaEngine(content.characters,weights,max=>{
          if(call++===0)return tickets[rarity];
          expect(max).toBe(pool.length);return index;
        });
        expect(game.draw().id).toBe(pool[index].id);reached.add(pool[index].id);
      }
    }
    expect(reached.size).toBe(75);
  });
  it('합계/빈 등급/난수 오류를 대체 보상으로 숨기지 않음',()=>{
    expect(()=>new GachaEngine(content.characters,{...weights,COMMON:39})).toThrow();
    expect(()=>new GachaEngine(content.characters.filter(c=>c.rarity!=='RARE'),weights)).toThrow();
    expect(()=>new GachaEngine(content.characters,weights,()=>100).draw()).toThrow('난수');
    expect(()=>new GachaEngine(content.characters,weights,()=>-1).draw()).toThrow('난수');
  });
});
