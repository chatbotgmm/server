// Synthetic tests/demo ONLY. None of these stats, costs or growth values are product balance.
import {ELEMENTS, createCombat} from './engine.mjs';
export const profile = value => Object.fromEntries(ELEMENTS.map(e => [e,value]));
export const exampleFighter = (id, level=1) => ({id,name:id==='u01'?'갈매미맨':'검토 상대',level,maxHp:200,
  suitability:profile(0),defense:profile('normal'),
  attack:{id:'fixture-basic',name:'검토 공격',element:'NORMAL',mpCost:2,description:'비용은 테스트 전용'},
  skills:Array.from({length:6},(_,i)=>({id:`fixture-${i}`,name:`검토 기술 ${i+1}`,element:ELEMENTS[i],mpCost:i+3,
    description:'미승인 밸런스 fixture · 제품 기술 아님'}))});
export const exampleBalance = {
  version:'synthetic-fixture-not-product',
  maxMp:f => (f.id==='u01'?11:17)+f.level*2,
  baseDamage:() => 20,
  // Replaceable review policy, NOT an approved final reflection formula.
  reflectDamage:({baseDamage,suitabilityMultiplier}) => baseDamage*suitabilityMultiplier,
};
export function exampleBattle(overrides={}) {
  return createCombat({battleId:'review-1',players:['review-a','review-b'],
    fighters:[exampleFighter('u01'),exampleFighter('u02',2)],firstSide:0,balance:exampleBalance,...overrides});
}
