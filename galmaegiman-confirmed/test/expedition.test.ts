import {describe,it,expect} from 'vitest';
import {loadContent} from '../src/content.js';
import {planExpedition,rollExpedition,expectedSnacks} from '../src/expedition.js';
const content=loadContent();
const bySyn=(key:string)=>content.characters.filter(c=>c.synergy===key).map(c=>c.id);
const [b1,b2,b3,b4]=bySyn('BASIC');
describe('탐험 시너지',()=>{
  it('시너지 없음: 1마리당 10개, 60분',()=>{
    const plan=planExpedition(content,[b1,bySyn('GOLD')[0]]);
    expect(plan.minutes).toBe(60);expect(plan.synergies).toEqual([]);expect(expectedSnacks(plan)).toBe(20);
  });
  it('기본 2마리 1단계 +15%, 4마리 2단계 +30%',()=>{
    expect(planExpedition(content,[b1,b2]).snackPercent).toBe(15);
    expect(planExpedition(content,[b1,b2,b3,b4]).snackPercent).toBe(30);
    expect(expectedSnacks(planExpedition(content,[b1,b2,b3,b4]))).toBe(52);
  });
  it('같은 유닛 중복도 시너지 마리 수에 포함 + 갈매미 카운트',()=>{
    const plan=planExpedition(content,[b1,b1,b1,b1,b1]);
    expect(plan.synergies[0]).toMatchObject({key:'BASIC',count:5,level:2});
    expect(plan.countBonus).toEqual({unitId:b1,count:5,percent:25});
    expect(plan.snackPercent).toBe(55);
  });
  it('암흑 2단계는 40분, 갈 의복 2단계는 마리당 +4',()=>{
    const dark=bySyn('DARK');
    expect(planExpedition(content,dark).minutes).toBe(40);
    const outfit=bySyn('OUTFIT').slice(0,4);
    expect(expectedSnacks(planExpedition(content,outfit))).toBe(40+16);
  });
  it('최대 10마리, 최상위가 아니면 거부',()=>{
    expect(()=>planExpedition(content,Array(11).fill(b1))).toThrow();
    expect(()=>planExpedition(content,['C1'])).toThrow();
  });
  it('확률 보상 판정: 모두 실패/모두 성공',()=>{
    const plan=planExpedition(content,[...bySyn('GOLD'),...bySyn('SEA'),...bySyn('COSMOS').slice(0,2)]);
    const none=rollExpedition(content,plan,max=>max-1);
    expect(none).toMatchObject({doubled:false,tickets:0});expect(none.commonId).toBeUndefined();
    const all=rollExpedition(content,plan,()=>0);
    expect(all.doubled).toBe(true);expect(all.snacks).toBe(expectedSnacks(plan)*2);expect(all.commonId).toBe('C1');expect(all.tickets).toBe(1);
  });
});
