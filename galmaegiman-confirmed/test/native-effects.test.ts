import {describe,expect,it} from 'vitest';
import {loadBattleRules} from '../src/battle/load.js';
import {act,activeOf,choose,createBattle} from '../src/battle/engine.js';
import type {Skill} from '../src/battle/core.js';
import type {UnitRecord} from '../src/battle/growth.js';
import {nativeFighterEffects} from '../src/native.js';

const R=loadBattleRules();
const rec=(uid:string):UnitRecord=>({uid,id:'D1',name:'갈매미맨',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const ready=()=>{
  const b=createBattle(R,[rec('mine')],[rec('foe')],7);b.phase='action';
  for(const s of b.sides){s.offers=null;for(const f of s.fighters){f.maxHp=f.hp=10000;f.passive={};}}
  activeOf(b,0).base.spd=1000;activeOf(b,1).status.sleep=100;
  return b;
};
const idle:Skill={id:'test-idle',name:'약한 공격',power:1,cat:'phys',cd:0,effects:[],desc:''};

describe('네이티브 현재 효과 표시',()=>{
  it('실제 증강체 피해 감소는 적용 직후·턴 경과·만료를 그대로 보여 준다',()=>{
    const b=ready(),me=activeOf(b,0);b.phase='choose';b.sides[0].offers=['bulwark'];
    choose(b,0,{augment:'bulwark'});
    const turns=R.battleAug.get('bulwark')!.p.turns??2,pct=R.battleAug.get('bulwark')!.p.pct??40;
    expect(nativeFighterEffects(R,me)).toContainEqual({id:'guard:0',label:'받는 피해 감소',valueText:`-${pct}%`,remainingTurns:turns,kind:'guard'});
    me.skills=[idle];
    for(let remaining=turns-1;remaining>=0;remaining--){
      act(b,[{kind:'skill',skillId:idle.id},{kind:'skill',skillId:activeOf(b,1).skills[0].id}]);
      const guard=nativeFighterEffects(R,me).find(e=>e.id==='guard:0');
      if(remaining>0)expect(guard?.remainingTurns).toBe(remaining);else expect(guard).toBeUndefined();
    }
  });

  it('기술 강화·재생·상태이상의 남은 턴은 실제 엔진 적용 후 차감·만료와 같다',()=>{
    const b=ready(),me=activeOf(b,0),foe=activeOf(b,1);
    const move:Skill={...idle,id:'test-effects',effects:[{kind:'buff',stat:'atk',pct:25,turns:3,target:'self'},{kind:'regen',pct:10,turns:3,target:'self'},{kind:'status',status:'bind',chance:100,turns:3}]};
    me.skills=[move,idle];me.hp=5000;
    act(b,[{kind:'skill',skillId:move.id},{kind:'skill',skillId:foe.skills[0].id}]);
    const applied=nativeFighterEffects(R,me),appliedFoe=nativeFighterEffects(R,foe);
    expect(applied).toContainEqual({id:'mod:atk:0',label:'공격 강화',valueText:'+25%',remainingTurns:2,kind:'buff'});
    expect(applied).toContainEqual({id:'regen:0',label:'재생',valueText:'턴마다 최대 체력 10% 회복',remainingTurns:2,kind:'regen'});
    expect(appliedFoe.find(e=>e.id==='status:bind')).toMatchObject({remainingTurns:2,valueText:`속도 ${R.data.status.bindSpd}%`});
    for(const remaining of [1,0]){
      act(b,[{kind:'skill',skillId:idle.id},{kind:'skill',skillId:foe.skills[0].id}]);
      const effects=nativeFighterEffects(R,me),bind=nativeFighterEffects(R,foe).find(e=>e.id==='status:bind');
      if(remaining){expect(effects.map(e=>e.remainingTurns)).toEqual([1,1]);expect(bind?.remainingTurns).toBe(1);}
      else {expect(effects).toEqual([]);expect(bind).toBeUndefined();}
    }
    // 보관한 이전 응답도 실제 전투의 턴 차감에 따라 바뀌지 않아야 한다.
    expect(applied.map(e=>e.remainingTurns)).toEqual([2,2]);expect(appliedFoe.find(e=>e.id==='status:bind')?.remainingTurns).toBe(2);
  });

  it('표시 조회를 반복해도 RNG·HP·현재 효과·기록은 바뀌지 않으며 중첩 효과를 임의 합산하지 않는다',()=>{
    const b=ready(),f=activeOf(b,0);f.status={burn:2,rage:1};f.mods=[{stat:'def',pct:-20,turns:2}];f.guards=[{pct:20,turns:1},{pct:30,turns:2}];f.regens=[{pct:5,turns:2}];f.endure=2;f.immune=1;f.protect=true;f.lockAtk=40;f.extraAttr='ICE';
    const before=JSON.stringify(b),first=nativeFighterEffects(R,f),second=nativeFighterEffects(R,f);
    expect(first).toEqual(second);expect(JSON.stringify(b)).toBe(before);
    expect(first.filter(e=>e.kind==='guard').map(e=>e.valueText)).toEqual(['-20%','-30%','체력이 1 아래로 떨어지지 않음','새로운 상태이상 차단','이번 턴 공격 차단']);
    expect(first.find(e=>e.id==='lockAtk')).toMatchObject({remainingTurns:null,valueText:'공격 +40%'});
    expect(first.find(e=>e.id==='extraAttr')).toMatchObject({remainingTurns:null,attrCode:'ICE',valueText:R.attrName('ICE')});
    expect(first.find(e=>e.id==='status:burn')?.valueText).toContain(`${R.data.status.burnDamage}%`);
    expect(new Set(first.map(e=>e.id)).size).toBe(first.length);
    first[0].remainingTurns=999;expect(f.status.burn).toBe(2);
  });
});
