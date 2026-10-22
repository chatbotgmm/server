import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {attrKeys,rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattleService,serializeServiceBattle} from '../galmaegiman-confirmed/src/battle/service-factory.ts';
import {innateAttackFactor,skillPreview,act,chooseTogether,choose,activeOf} from '../galmaegiman-confirmed/src/battle/engine.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url)));data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));data.multipliers.critChance=0;
const R=rulesFrom(data),service=createBattleService(R),rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const skill=attr=>({id:'test-hit',name:'시험',power:1000,cat:'phys',attr,cd:0,effects:[],desc:''});
function make(){const b=service.create([rec('a')],[rec('b')],123);b.phase='action';b.rng=421;for(const s of b.sides){s.offers=null;s.augs=[];s.augment={};for(const f of s.fighters){f.hp=f.maxHp=100000000;f.base={patk:100,pdef:100,matk:100,mdef:100,spd:f.side?1:100};f.scale=1;f.passive={};f.skills=[skill('FIRE')];}}return b;}
const copy=b=>service.restore(serializeServiceBattle(b));
function hit(b,attr,hits=1){const a=activeOf(b,0),d=activeOf(b,1);a.skills=[{...skill(attr),hits}];d.skills=[{id:'idle',name:'대기',power:0,cd:0,effects:[],desc:''}];const before=b.eventSeq;act(b,[{kind:'skill',skillId:'test-hit'},{kind:'skill',skillId:'idle'}],{prevalidated:true});return b.events.filter(e=>e.id>before);}
const strongPair=()=>{for(const a of attrKeys)for(const d of attrKeys)if(R.typeMult(a,d)>1)return [a,d];};
const weakPair=()=>{for(const a of attrKeys)for(const d of attrKeys)if(R.typeMult(a,d)<1)return [a,d];};
test('100유닛상성×10기술속성=1000조합: bounded·preview·실피해 일치',()=>{
 const template=make();
 for(const own of attrKeys)for(const enemy of attrKeys)for(const attack of attrKeys){
  const b=copy(template),a=activeOf(b,0),d=activeOf(b,1);a.attr=own;d.attr=enemy;
  const expected=R.typeMult(own,enemy)>1?1.1:R.typeMult(own,enemy)<1?.9:1;
  assert.equal(innateAttackFactor(b,a,d),expected);assert(expected>=.9&&expected<=1.1);
  const preview=skillPreview(b,0,skill(attack));assert.equal(preview.innateMultiplier,expected);assert.equal(preview.multiplier,R.typeMult(attack,enemy));
  assert.equal(preview.sameAttributeMultiplier,attack===own?data.multipliers.sameAttr:1);
  assert.equal(preview.combinedAttributeMultiplier,preview.multiplier*expected*preview.sameAttributeMultiplier);
  const old=copy(b);delete old.innateDamageVersion;
  const base=hit(old,attack).find(e=>e.reason==='skill').amount,newDamage=hit(b,attack).find(e=>e.reason==='skill').amount;
  assert(Math.abs(newDamage-base*expected)<=1.1,`${own}/${enemy}/${attack}: ${base}→${newDamage}`);
  if(expected>1)assert(newDamage>=base);if(expected<1)assert(newDamage<=base);
 }
});
test('기존 강상성1.5×일치1.2=1.8에서 새1.1로1.98, 상태전용은1',()=>{
 const b=make(),[a,d]=strongPair();activeOf(b,0).attr=a;activeOf(b,1).attr=d;
 assert.equal(data.multipliers.sameAttr,1.2);assert.equal(R.typeMult(a,d),1.5);
 assert(Math.abs(skillPreview(b,0,skill(a)).combinedAttributeMultiplier-1.98)<1e-12);
 const p=skillPreview(b,0,{...skill(a),power:0});assert.equal(p.innateMultiplier,1);assert.equal(p.sameAttributeMultiplier,1);
});
test('무속성전장 둘다중립, focus/weakguard/typebridge는 기술상성만 변경',()=>{
 const b=make(),[a,d]=strongPair();activeOf(b,0).attr=a;activeOf(b,1).attr=d;b.sides[0].augs=['focus'];
 let p=skillPreview(b,0,skill(a));assert.equal(p.multiplier,1.9);assert.equal(p.innateMultiplier,1.1);
 b.sides[1].augs=['weakguard'];p=skillPreview(b,0,skill(a));assert.equal(p.multiplier,1);assert.equal(p.innateMultiplier,1.1);
 b.sides[0].neutral=b.sides[1].neutral=3;p=skillPreview(b,0,skill(a));assert.equal(p.multiplier,1);assert.equal(p.innateMultiplier,1);
 b.sides[0].neutral=b.sides[1].neutral=0;b.sides[0].augs=['typebridge'];b.sides[1].augs=[];
 const weakSkill=attrKeys.find(t=>R.typeMult(t,d)<1);p=skillPreview(b,0,skill(weakSkill));assert.equal(p.multiplier,1);assert.equal(p.innateMultiplier,1.1);
});
test('다단공격 각타격 한번만 보정, 흡혈/반사는 실제피해에서만 파생',()=>{
 const b=make(),[a,d]=strongPair();activeOf(b,0).attr=a;activeOf(b,1).attr=d;activeOf(b,0).hp-=10000;activeOf(b,0).passive.lifesteal=20;activeOf(b,1).passive.reflect=20;
 const old=copy(b);delete old.innateDamageVersion;
 const before=hit(old,a,3),after=hit(b,a,3),oldHits=before.filter(e=>e.reason==='skill'),newHits=after.filter(e=>e.reason==='skill');assert.equal(newHits.length,3);
 newHits.forEach((e,i)=>assert(Math.abs(e.amount-oldHits[i].amount*1.1)<=1.1));
 const actual=newHits.reduce((s,e)=>s+e.amount,0);assert.equal(after.find(e=>e.reason==='lifesteal').amount,Math.round(actual*.2));
 const reflect=after.filter(e=>e.reason==='reflect');assert.equal(reflect.reduce((s,e)=>s+e.amount,0),Math.round(actual*.2));
});
test('역습은 유닛상성 재보정 없이 기존 기술상성 조건/피해를 유지',()=>{
 const b=make(),[attack,def]=weakPair();activeOf(b,0).attr=attack;activeOf(b,1).attr=def;b.sides[1].augs=['counter'];
 const old=copy(b);delete old.innateDamageVersion;
 const oldCounter=hit(old,attack).find(e=>e.reason==='counter'),nextCounter=hit(b,attack).find(e=>e.reason==='counter');
 assert(oldCounter&&nextCounter);assert.equal(nextCounter.amount,oldCounter.amount);assert.equal(nextCounter.multiplier,oldCounter.multiplier);
});
test('자유자재 선택 후 다른 속성 기술에도 현재 내속성 보정 적용',()=>{
 const b=make(),enemy=activeOf(b,1).attr;
 const strong=attrKeys.find(a=>R.typeMult(a,enemy)>1),weak=attrKeys.find(a=>R.typeMult(a,enemy)<1),attack=attrKeys.find(a=>a!==strong&&a!==weak);
 activeOf(b,0).attr=weak;const before=skillPreview(b,0,skill(attack));assert.equal(before.innateMultiplier,.9);
 b.phase='choose';b.sides[0].offers=['reverse'];b.sides[1].offers=['first'];chooseTogether(b,[{augment:'reverse'},{augment:'first'}]);
 const snapshot=b.sides[0].freestyle;choose(b,0,{freestyle:{selectionId:snapshot.selectionId,phaseId:snapshot.phaseId,targetKey:snapshot.targetKey,attribute:strong}});
 const after=skillPreview(b,0,skill(attack));assert.equal(after.innateMultiplier,1.1);assert.equal(before.multiplier,after.multiplier);assert.equal(before.sameAttributeMultiplier,1);assert.equal(after.sameAttributeMultiplier,1);
});
test('snapshot 버전 없는 구 V1/V2·구epoch는 변화없고 restore/restart도 보존',()=>{
 const b=make(),[a,d]=strongPair();activeOf(b,0).attr=a;activeOf(b,1).attr=d;
 for(const change of [x=>delete x.innateDamageVersion,x=>delete x.combatRulesEpoch,x=>x.combatRulesEpoch='old']){
  const old=copy(b);change(old);assert.equal(innateAttackFactor(old,activeOf(old,0),activeOf(old,1)),1);
  const saved=serializeServiceBattle(old),restored=service.restore(saved),restarted=service.restart(saved,[rec('x')],[rec('y')],123);
  assert.equal(restored.innateDamageVersion,old.innateDamageVersion);assert.equal(restarted.innateDamageVersion,old.innateDamageVersion);assert.equal(restarted.combatRulesEpoch,old.combatRulesEpoch);
 }
 const restored=service.restore(serializeServiceBattle(b));assert.equal(restored.innateDamageVersion,1);assert.equal(innateAttackFactor(restored,activeOf(restored,0),activeOf(restored,1)),1.1);
});

test('화상/회복/고정 체력대가에는 추가 보정 없고 actualDamage 파생추가타는 한 번만 반영',()=>{
 const b=make(),[a,d]=strongPair();activeOf(b,0).attr=a;activeOf(b,1).attr=d;activeOf(b,1).rec.level=50;
 b.phase='choose';b.sides[0].offers=['underdog'];choose(b,0,{augment:'underdog'});
 activeOf(b,0).status.burn=3;
 const old=copy(b);delete old.innateDamageVersion;
 const before=hit(old,a),after=hit(b,a),main=after.filter(e=>e.reason==='skill').reduce((n,e)=>n+(e.amount??0),0);
 assert.equal(after.find(e=>e.reason==='underdog'&&e.target.side===1).amount,Math.round(main*.35));
 assert.equal(after.find(e=>e.reason==='underdog'&&e.target.side===0).amount,before.find(e=>e.reason==='underdog'&&e.target.side===0).amount);
 assert.equal(after.find(e=>e.reason==='burn').amount,before.find(e=>e.reason==='burn').amount);
 const heal=make();activeOf(heal,0).attr=a;activeOf(heal,1).attr=d;activeOf(heal,0).hp-=1000000;
 const pre=copy(heal);delete pre.innateDamageVersion;
 for(const x of [heal,pre]){
  activeOf(x,0).skills=[{id:'heal',name:'회복',power:0,cd:0,effects:[{kind:'heal',target:'self',pct:1}],desc:''}];
  activeOf(x,1).skills=[{id:'idle',name:'대기',power:0,cd:0,effects:[],desc:''}];
  act(x,[{kind:'skill',skillId:'heal'},{kind:'skill',skillId:'idle'}],{prevalidated:true});
 }
 assert.equal(activeOf(heal,0).hp,activeOf(pre,0).hp);
});

test('방 규칙 지문도 innate snapshot 버전으로 구분하며 구 기록은 자동승격하지 않음',()=>{
 const b=make(),current=serializeServiceBattle(b);assert.equal(service.compatibleFingerprint(current),service.fingerprint);assert(service.fingerprint.endsWith('innateDamageVersion:1'));
 delete b.innateDamageVersion;const old=serializeServiceBattle(b),fingerprint=service.compatibleFingerprint(old);assert(fingerprint&&!fingerprint.endsWith('innateDamageVersion:1'));
 assert.equal(service.restore(old).innateDamageVersion,undefined);assert.equal(service.restart(old,[rec('c')],[rec('d')],1).innateDamageVersion,undefined);
});
