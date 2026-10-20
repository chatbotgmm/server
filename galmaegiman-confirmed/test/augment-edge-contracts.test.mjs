import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom,attrKeys,battleAugIds} from '../src/battle/core.ts';
import {createBattle,choose,act,activeOf,legalActions,skillProblem,skillPreview,chooseTogether} from '../src/battle/engine.ts';
import {createAugmentConfigV2} from '../src/battle/augment-catalog-v2.ts';
import {serializeBattle,deserializeBattle} from '../src/battle/realtime-adapter.ts';
import {levelScale} from '../src/battle/growth.ts';

const data=JSON.parse(readFileSync(new URL('../../test/battle-fixture.json',import.meta.url),'utf8'));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
data.multipliers.critChance=0;
const R=rulesFrom(data,{augmentVersion:2}), config=createAugmentConfigV2({silver:1,gold:1,prismatic:1});
const rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const attack=(id='hit',attr=attrKeys[0])=>({id,name:id,power:40,cat:'phys',attr,cd:0,effects:[],desc:''});
function make(){const b=createBattle(R,[rec('a'),rec('aa')],[rec('b'),rec('bb')],19,config);for(const s of b.sides){s.offers=null;for(const f of s.fighters){f.hp=f.maxHp=10000;f.base={patk:100,pdef:100,matk:100,mdef:100,spd:f.side?90:100};f.scale=10;f.passive={};f.attr=attrKeys[0];f.skills=[attack()];f.status={};}}b.phase='action';return b;}
function select(b,id,side=0){b.phase='choose';b.sides[side].offers=[id];choose(b,side,{augment:id});assert.equal(b.phase,'action');return b;}
// 상대 행동이 측정값을 바꾸지 않도록 테스트 전용 무효과 기술을 사전검증 경로로 실행한다.
function idle(b,side){const id=`idle-${b.turn}`;activeOf(b,side).skills.push({id,name:id,power:0,cd:0,effects:[],desc:''});return {kind:'skill',skillId:id};}
function step(b,a={kind:'skill',skillId:'hit'},d){b.rng=b.testSeed??314159;const seq=b.eventSeq;act(b,[a,d??idle(b,1)],{prevalidated:true});return b.events.filter(e=>e.id>seq);}
function hit(b){return step(b).filter(e=>e.kind==='damage'&&e.reason==='skill'&&e.target.side===1).reduce((n,e)=>n+e.amount,0);}
function incoming(b){return step(b,idle(b,0),{kind:'skill',skillId:'hit'}).filter(e=>e.kind==='damage'&&e.reason==='skill'&&e.target.side===0).reduce((n,e)=>n+e.amount,0);}
function near(x,y,tolerance=2){assert(Math.abs(x-y)<=tolerance,`${x} ≈ ${y}`);}
function power(b,action){return step(b,action).find(e=>e.kind==='skill'&&e.actor.side===0)?.power;}
function weakPair(b){const a=attrKeys.find(a=>attrKeys.some(d=>R.typeMult(a,d)<1)),d=attrKeys.find(d=>R.typeMult(a,d)<1);activeOf(b,0).skills[0].attr=a;activeOf(b,1).attr=d;return [a,d];}
function strongPair(b){const a=attrKeys.find(a=>attrKeys.some(d=>R.typeMult(a,d)>1)),d=attrKeys.find(d=>R.typeMult(a,d)>1);activeOf(b,0).skills[0].attr=a;activeOf(b,1).attr=d;return [a,d];}

const shield=f=>f.augment?.shield??0;
const uses=b=>b.sides[0].augment?.uses?.overflowguard;
function healSkill(b,pct=25,target='self',id='heal'){const f=activeOf(b,0);const sk={id,name:id,power:0,cd:0,effects:[{kind:'heal',pct,target}],desc:''};f.skills.push(sk);return sk;}
const cast=sk=>({kind:'skill',skillId:sk.id});
test('만피 회복기는 overflowguard 잔여횟수·쉴드공간이 있을 때만 합법',()=>{
 const b=make(),sk=healSkill(b);assert.notEqual(skillProblem(b,0,sk),null);
 select(b,'overflowguard');assert.equal(skillProblem(b,0,sk),null);
 const f=activeOf(b,0);f.augment={...f.augment,shield:2000,shieldUntil:b.turn+1};assert.notEqual(skillProblem(b,0,sk),null);
 f.augment.shield=1000;assert.equal(skillProblem(b,0,sk),null);b.sides[0].augment.uses.overflowguard=0;assert.notEqual(skillProblem(b,0,sk),null);
});
test('만피 예외도 healUntil·같은 보조 연속사용 제한을 유지',()=>{
 const b=select(make(),'overflowguard'),sk=healSkill(b),f=activeOf(b,0);
 f.healUntil=b.turn;assert.notEqual(skillProblem(b,0,sk),null);f.healUntil=0;
 f.lastUtility[sk.id]=b.turn-1;assert.notEqual(skillProblem(b,0,sk),null);
 delete f.lastUtility[sk.id];assert.equal(skillProblem(b,0,sk),null);
});
test('대기 아군 만피 회복도 합법적 쉴드 대상이 있으면 허용',()=>{
 const b=select(make(),'overflowguard'),sk=healSkill(b,25,'bench');assert.equal(skillProblem(b,0,sk),null);
 step(b,cast(sk));assert.equal(shield(b.sides[0].fighters[1]),2000);assert.equal(shield(activeOf(b,0)),0);
});
test('HP90%에25% 회복은 HP100%+쉴드15%, 100%에30%는20%',()=>{
 for(const [hp,pct,expected] of [[9000,25,1500],[10000,30,2000]]){const b=select(make(),'overflowguard'),f=activeOf(b,0);f.hp=hp;step(b,cast(healSkill(b,pct)));assert.equal(f.hp,10000);assert.equal(shield(f),expected);assert.equal(uses(b),2);}
});
test('쉴드가 이미 상한이면 변환횟수·만료일을 갱신하지 않음',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0),sk=healSkill(b,30);f.augment={...f.augment,shield:2000,shieldUntil:b.turn+4};const until=f.augment.shieldUntil;
 step(b,cast(sk));assert.equal(uses(b),3);assert.equal(f.augment.shieldUntil,until);
});
test('직접피해 이벤트는 absorbed와 shield 전후를 기록, amount는 실제HP손실',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0);step(b,cast(healSkill(b,30)));
 const before=f.hp,e=step(b,idle(b,0),{kind:'skill',skillId:'hit'}).find(e=>e.kind==='damage'&&e.reason==='skill'&&e.target.side===0);
 assert.equal(e.amount,before-f.hp);assert.equal(e.amount,0);assert(e.absorbed>0);assert.equal(e.shieldBefore,2000);assert.equal(e.shieldAfter,2000-e.absorbed);
});
test('쉴드 만료는 현재 포함2턴, 대기중에도 진행하고 교체로 연장되지 않음',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0);step(b,cast(healSkill(b,25)));assert.equal(shield(f),2000);
 step(b,{kind:'switch',to:1});assert.equal(shield(f),0);
 step(b,{kind:'switch',to:0});assert.equal(shield(f),0);assert.equal(uses(b),2);
});
test('쉴드가 있는 대상이 비용으로 전투불가 되면 즉시 제거',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0);f.hp=100;select(b,'mortgage');
 f.augment.shield=2000;f.augment.shieldUntil=b.turn+9;f.augment.mortgageDue=b.turn;f.hp=1;
 step(b,idle(b,0));assert.equal(f.hp,0);assert.equal(shield(f),0);
});
for(const id of ['redline','coldbargain','sealedduel'])test(`${id} 시전회복 금지는 기술회복·흡혈·초과회복쉴드 모두 차단`,()=>{
 const b=select(make(),'overflowguard');select(b,id);const f=activeOf(b,0);f.skills[0].effects=[{kind:'heal',pct:100,target:'self'}];f.skills[0].drain=100;f.passive.lifesteal=100;
 step(b);assert.equal(shield(f),0);assert.equal(uses(b),3);assert.equal(f.hp,id==='redline'?8000:10000);
});
test('회복상한 초과분은 쉴드로 전환하지 않음: drain 최대HP4%',()=>{
 const b=select(make(),'overflowguard');select(b,'drain');const f=activeOf(b,0);f.skills[0].power=500;activeOf(b,1).maxHp=activeOf(b,1).hp=100000;
 step(b);assert(shield(f)>0);assert(shield(f)<=400);assert.equal(f.hp,10000);
});
for(const [id,power] of [['redline',66],['glasswing',60],['slowhammer',68],['coldbargain',62]])test(`${id} 미리보기는 신규 위력 예측, RNG·HP·횟수 전체불변`,()=>{
 const b=select(make(),id),f=activeOf(b,0);if(id==='coldbargain')f.skills[0].drain=20;const before=serializeBattle(b),preview=skillPreview(b,0,f.skills[0]);
 assert.equal(serializeBattle(b),before);assert.equal(preview.power,power);
 const e=step(b).find(e=>e.kind==='skill'&&e.actor.side===0);assert.equal(e.power,preview.power);
});
test('비용을 낼 수 없는 redline 미리보기는 기본위력, 전투상태 불변',()=>{
 const b=select(make(),'redline'),f=activeOf(b,0);f.hp=2000;const before=serializeBattle(b);assert.equal(skillPreview(b,0,f.skills[0]).power,40);assert.equal(serializeBattle(b),before);
});
test('blindspot 미리보기는 조건부 상성2배, 비용/사용권 소비 없음',()=>{
 const b=make();weakPair(b);select(b,'blindspot');const before=serializeBattle(b);assert.equal(skillPreview(b,0,activeOf(b,0).skills[0]).multiplier,2);assert.equal(serializeBattle(b),before);
});
test('chooseTogether camo/dual은 양쪽 새 선두 공통스냅샷으로 결정',()=>{
 const best=foe=>attrKeys.filter(a=>R.typeMult(a,foe)>1).sort((x,y)=>R.typeMult(foe,x)-R.typeMult(foe,y))[0];
 for(const [left,right] of [['camo','dual'],['dual','camo'],['camo','camo']])for(const a of attrKeys)for(const d of attrKeys){
  const b=make();b.phase='choose';for(const side of [0,1]){b.sides[side].needSwitch=true;activeOf(b,side).hp=0;b.sides[side].offers=[side?right:left];}
  b.sides[0].fighters[1].attr=a;b.sides[1].fighters[1].attr=d;
  chooseTogether(b,[{augment:left,switchTo:1},{augment:right,switchTo:1}]);
  assert.equal(activeOf(b,0)[left==='camo'?'attr':'extraAttr'],best(d),`${left}/${right} ${a}/${d} side0`);
  assert.equal(activeOf(b,1)[right==='camo'?'attr':'extraAttr'],best(a),`${left}/${right} ${a}/${d} side1`);
 }
});
test('쉴드흡수 실제피해0은 기술·패시브·증강 흡혈과 새쉴드를 만들지 않음',()=>{
 const b=select(make(),'overflowguard');step(b,cast(healSkill(b,30)));select(b,'overflowguard',1);select(b,'drain',1);
 const foe=activeOf(b,1);foe.skills[0].drain=100;foe.passive.lifesteal=100;
 const e=step(b,idle(b,0),{kind:'skill',skillId:'hit'}).find(e=>e.kind==='damage'&&e.reason==='skill');
 assert.equal(e.amount,0);assert.equal(shield(foe),0);assert.equal(b.sides[1].augment.uses.overflowguard,3);
});
test('과잉공격 피해를 흡혈량과 초과회복쉴드로 부풀리지 않음',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0);activeOf(b,1).hp=1;f.skills[0].power=500;f.skills[0].drain=100;f.passive.lifesteal=0;
 step(b);assert.equal(shield(f),1);assert.equal(uses(b),2);
});
test('부활HP는 초과회복쉴드와 변환소모를 만들지 않음',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0),bench=b.sides[0].fighters[1];bench.hp=0;
 f.skills.push({id:'revive',name:'부활',power:0,cd:0,effects:[{kind:'revive',pct:100}],desc:''});step(b,{kind:'skill',skillId:'revive'});
 assert.equal(bench.hp,10000);assert.equal(shield(bench),0);assert.equal(uses(b),3);
});
test('유효변환은 팀3회, 쉴드 소진·교체 후에도 재충전되지 않음',()=>{
 const b=select(make(),'overflowguard');for(let i=0;i<4;i++){
  const f=activeOf(b,0);f.healUntil=0;f.augment={...f.augment,shield:0};const sk=healSkill(b,30,'self',`heal-${i}`);
  step(b,cast(sk));assert.equal(shield(f),i<3?2000:0);assert.equal(uses(b),Math.max(0,2-i));
 }
 step(b,{kind:'switch',to:1});assert.equal(uses(b),0);
});
test('화상·증강 추가피해는 직접피해 전용 쉴드를 우회',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0);step(b,cast(healSkill(b,30)));f.status.burn=1;
 step(b,idle(b,0));assert.equal(f.hp,10000-Math.round(10000*data.status.burnDamage/100));
 const c=select(make(),'overflowguard'),target=activeOf(c,0);target.augment={...target.augment,shield:100,shieldUntil:c.turn+5};select(c,'doubleedge',1);
 const events=step(c,idle(c,0),{kind:'skill',skillId:'hit'});assert(events.some(e=>e.reason==='doubleedge'&&e.amount>0));
});
test('신규 위력보너스 중첩은+100%상한, 미리보기와 시전 일치',()=>{
 const b=select(make(),'redline');select(b,'glasswing');select(b,'slowhammer');const f=activeOf(b,0),before=serializeBattle(b),preview=skillPreview(b,0,f.skills[0]);
 assert.equal(preview.power,80);assert.equal(serializeBattle(b),before);assert.equal(step(b).find(e=>e.kind==='skill'&&e.actor.side===0).power,80);
});
test('만피 회복기 예외는 사전검증을 우회하지 않는 실제 act에서도 실행',()=>{
 const b=select(make(),'overflowguard'),f=activeOf(b,0),sk=healSkill(b,30);
 assert(legalActions(b,0).some(a=>a.kind==='skill'&&a.skillId===sk.id));
 assert.doesNotThrow(()=>act(b,[cast(sk),{kind:'skill',skillId:'hit'}]));
 assert.equal(uses(b),2);assert.equal(f.hp,10000);assert(shield(f)>0&&shield(f)<2000);
});
