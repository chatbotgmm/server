import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {GachaEngine,gachaKinds} from '../src/gacha.js';
const content=loadContent();
const config=content.economy.gachas;
describe('3단 뽑기 확률',()=>{
  it('0~99 추첨값의 등급 경계가 설정 확률과 정확히 일치',()=>{
    for(const kind of gachaKinds){
      const counts:Record<string,number>={};
      for(let ticket=0;ticket<100;ticket++){
        let call=0;
        const engine=new GachaEngine(content.characters,config,max=>call++===0?(expect(max).toBe(100),ticket):0);
        const tier=engine.draw(kind).rarity;
        counts[tier]=(counts[tier]??0)+1;
      }
      expect(counts).toEqual(config[kind].weights);
    }
  });
  it('고급 뽑기에서 전설 20종 모두 선택 가능',()=>{
    const legends=content.characters.filter(c=>c.rarity==='LEGEND');
    const reached=new Set<string>();
    for(let i=0;i<legends.length;i++){
      let call=0;
      const engine=new GachaEngine(content.characters,config,max=>call++===0?99:(expect(max).toBe(20),i));
      reached.add(engine.draw('HIGH').id);
    }
    expect(reached.size).toBe(20);
  });
  it('히든과 최상위는 어떤 뽑기에서도 나오지 않음',()=>{
    for(const kind of gachaKinds)expect(Object.keys(config[kind].weights).some(t=>['HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(t))).toBe(false);
  });
  it('합계 오류와 난수 오류를 숨기지 않음',()=>{
    expect(()=>new GachaEngine(content.characters,{...config,LOW:{...config.LOW,weights:{COMMON:69,UNCOMMON:30}}})).toThrow();
    expect(()=>new GachaEngine(content.characters,config,()=>100).draw('LOW')).toThrow('난수');
  });
});
