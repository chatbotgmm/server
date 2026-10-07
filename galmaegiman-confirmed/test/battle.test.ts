import { describe, expect, it } from 'vitest';
import { loadContent } from '../src/content.js';
import { attrs, strongAgainst, typeMult, unitDefs, unitDefMap, type TopTier } from '../src/battle/units.js';
import { addXp, battleXp, canEvolve, statsOf, xpToNext, type UnitRecord } from '../src/battle/growth.js';
import { act, activeOf, aiChoose, choose, createBattle, pending, pickAi, type Battle } from '../src/battle/engine.js';
import { expectedAttempts, levelRange, maxStageAt, referenceLevel, scoutChance, scoutCopy, scoutRules } from '../src/battle/scout.js';

const content=loadContent();
const top=content.characters.filter(c=>['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity));
const rec=(id:string,level=30,extra:Partial<UnitRecord>={}):UnitRecord=>({uid:`${id}-${level}`,id,name:content.map.get(id)!.name,tier:content.map.get(id)!.rarity as TopTier,level,xp:0,stage:0,evoAugs:[],...extra});
function play(b:Battle,limit=400){
  for(let i=0;i<limit&&b.phase!=='over';i++){if(b.phase==='choose'){aiChoose(b,0);aiChoose(b,1);continue;}act(b,[pickAi(b,0),pickAi(b,1)]);}
  return b;
}

describe('수비대 대결 데이터', ()=>{
  it('10속성: 모두 강점 2·약점 2, 배율 1.5 / 0.67', ()=>{
    for(const a of attrs){
      expect(strongAgainst[a]).toHaveLength(2);
      expect(attrs.filter(x=>strongAgainst[x].includes(a))).toHaveLength(2);
    }
    expect(typeMult('STAR','SEA')).toBe(1.5);expect(typeMult('SEA','STAR')).toBe(0.67);expect(typeMult('STONE','STAR')).toBe(1);
  });
  it('최상위 33종 모두 전투 정보가 있고, 역할은 공격 10·수비 11·유틸 12', ()=>{
    expect(unitDefs).toHaveLength(33);
    expect(new Set(unitDefs.map(u=>u.id))).toEqual(new Set(top.map(c=>c.id)));
    const n=(r:string)=>unitDefs.filter(u=>u.role===r).length;
    expect([n('ATTACK'),n('DEFENSE'),n('UTILITY')]).toEqual([10,11,12]);
  });
});

describe('성장', ()=>{
  it('경험치로 레벨이 오르고, 진화는 레벨과 스토리 조건을 둘 다 봄', ()=>{
    const r=rec('D1',1);addXp(r,xpToNext(1)+xpToNext(2));expect(r.level).toBe(3);
    const r20=rec('D1',20);expect(canEvolve(r20,0)).toBe(false);expect(canEvolve(r20,1)).toBe(true);
    expect(canEvolve(rec('D1',34,{stage:1}),2)).toBe(false);
    expect(battleXp(10,true)).toBe(600);expect(battleXp(10,false)).toBe(200);
    let total=0;for(let l=1;l<20;l++)total+=xpToNext(l);expect(total).toBe(13427);
  });
  it('레벨·진화가 오르면 능력치가 커짐', ()=>{
    expect(statsOf(rec('T4',30)).hp).toBeGreaterThan(statsOf(rec('T4',10)).hp);
    expect(statsOf(rec('T4',30,{stage:1})).atk).toBeGreaterThan(statsOf(rec('T4',30)).atk);
  });
});

describe('전투 엔진', ()=>{
  it('같은 시드면 같은 결과', ()=>{
    const mk=()=>play(createBattle([rec('D6'),rec('T3'),rec('I4')],[rec('D4'),rec('E2'),rec('T7')],42));
    const a=mk(),b=mk();expect(a.log).toEqual(b.log);expect(a.winner).toEqual(b.winner);
    expect(a.phase).toBe('over');
  });
  it('전투 시작 때와 전투불가 때 양쪽 모두 증강체를 고름', ()=>{
    const b=createBattle([rec('D6',60)],[rec('D4',1),rec('E2',1)],7);
    expect(pending(b,0).augments).toHaveLength(3);expect(pending(b,1).augments).toHaveLength(3);
    aiChoose(b,0);aiChoose(b,1);expect(b.phase).toBe('action');
    // 레벨 60 별(바다에 강함)이 레벨 1 바다를 한 방에
    for(let i=0;i<5&&activeOf(b,1).hp>0;i++){act(b,[{kind:'skill',skillId:'basic-STAR'},pickAi(b,1)]);if(b.phase==='choose')break;}
    expect(b.phase).toBe('choose');
    expect(pending(b,0).augments).toHaveLength(3);expect(pending(b,1).augments).toHaveLength(3);expect(pending(b,1).needSwitch).toBe(true);
    expect(()=>choose(b,1,{augment:pending(b,1).augments![0]})).toThrow();   // 교체도 골라야 함
  });
  it('상성: 별 → 바다가 바다 → 별보다 아픔, 역상성 증강체는 뒤집음', ()=>{
    const hit=(a:string,d:string,aug?:string)=>{
      const b=createBattle([rec(a)],[rec(d)],1);
      b.sides[0].offers=null;b.sides[1].offers=null;b.phase='action';if(aug)b.sides[0].augs.push(aug);
      const before=activeOf(b,1).hp;act(b,[{kind:'skill',skillId:`basic-${unitDefMap.get(a)!.attr}`},{kind:'skill',skillId:`basic-${unitDefMap.get(d)!.attr}`}]);
      return (before-activeOf(b,1).hp)/activeOf(b,1).maxHp;
    };
    expect(hit('D6','D4')).toBeGreaterThan(hit('D6','T1'));            // 별→바다(강) > 별→돌(보통)
    expect(hit('D6','D4','reverse')).toBeLessThan(hit('D6','D4'));     // 역상성
  });
  it('땅콩 폭주는 이름에 갈매미맨이 붙은 갈매미에게만', ()=>{
    const b=createBattle([rec('D7')],[rec('D1')],3);   // 갈크탑 vs 태초의 갈매미맨
    expect(activeOf(b,0).peanut).toBe(false);expect(activeOf(b,1).peanut).toBe(true);
  });
});

describe('외근(스카우트)', ()=>{
  it('기준 레벨은 높은 3마리 평균, 만나는 범위는 ±3', ()=>{
    expect(referenceLevel([rec('D1',40),rec('D2',30),rec('D3',20),rec('D4',1)])).toBe(30);
    expect(levelRange(2)).toEqual([1,5]);expect(levelRange(59)).toEqual([56,60]);
    expect(maxStageAt(19)).toBe(0);expect(maxStageAt(35)).toBe(2);expect(maxStageAt(60)).toBe(3);
  });
  it('성공률: 등급·진화·명함 반영, 최대 90%, 천장에서 100%', ()=>{
    expect(scoutChance('LIMITED',0,0,0)).toBe(35);
    expect(scoutChance('IMMORTAL',2,3,0)).toBeCloseTo(6*0.7*2.5);
    expect(scoutChance('LIMITED',0,3,0)).toBe(87.5);expect(scoutChance('LIMITED',0,9,0)).toBe(87.5);
    expect(scoutChance('IMMORTAL',0,0,scoutRules.pity.IMMORTAL-1)).toBe(100);
    expect(expectedAttempts(100,4)).toBe(1);
    expect(expectedAttempts(6,16)).toBeCloseTo((1-0.94**16)/0.06);
  });
  it('스카우트하면 레벨·진화·증강체까지 그대로, 새 번호만 다름', ()=>{
    const src=rec('E7',42,{xp:123,stage:2,evoAugs:['pierce','atk']});
    const copy=scoutCopy(src,'new-1');
    expect(copy).toEqual({...src,uid:'new-1'});expect(copy.evoAugs).not.toBe(src.evoAugs);
  });
});
