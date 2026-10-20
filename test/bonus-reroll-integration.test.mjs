import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom,battleAugIds} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattle,rerollAugment} from '../galmaegiman-confirmed/src/battle/engine.ts';
import {serializeBattle,rerollCommand} from '../galmaegiman-confirmed/src/battle/realtime-adapter.ts';
// 통합 검사: 보상 추가 리롤(bonus)은 V1·V2 모두 칸당 1회 제한을 넘을 수 있고, 실패하면 상태가 그대로다.
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url),'utf8'));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
const R=rulesFrom(data),rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const config={version:2,catalog:battleAugIds.map((id,i)=>({id,tier:['silver','gold','prismatic'][Math.floor(i/8)]})),weights:{silver:1,gold:1,prismatic:1}};
const v1=(seed=7)=>createBattle(R,[rec('a'),rec('aa')],[rec('b'),rec('bb')],seed);
const v2=(seed=7)=>createBattle(R,[rec('a'),rec('aa')],[rec('b'),rec('bb')],seed,config);
const src=p=>readFileSync(new URL(p,import.meta.url),'utf8');

for(const [name,make] of [['V1',v1],['V2',v2]]){
  test(`${name}: 일반 리롤은 칸당 1회, 보상 리롤은 같은 칸을 다시 뽑음`,()=>{
    const b=make();rerollAugment(b,0,0);
    const before=serializeBattle(b);
    assert.throws(()=>rerollAugment(b,0,0),/이미 다시 뽑았음/);
    assert.equal(serializeBattle(b),before,'거절된 일반 리롤은 상태·난수 불변');
    const old=b.sides[0].offers[0],id=rerollAugment(b,0,0,true);
    assert.notEqual(id,old);assert.equal(b.sides[0].offers[0],id);
  });
  test(`${name}: realtime-adapter가 bonus 인자를 엔진까지 전달`,()=>{
    const b=make(),once=rerollCommand(b,0,1);
    assert.throws(()=>rerollCommand(once,0,1));
    const twice=rerollCommand(once,0,1,true);
    assert.notEqual(twice.sides[0].offers[1],once.sides[0].offers[1]);
    assert.equal(once.sides[0].offers[1],rerollCommand(b,0,1).sides[0].offers[1],'원본 불변·결정적');
  });
}
test('V2: 후보가 고갈된 보상 리롤은 실패하고 상태·난수가 그대로(서비스가 사용권을 차감하지 않음)',()=>{
  const b=v2();rerollAugment(b,0,0);
  b.augmentOffers.round.seen=[...b.augmentOffers.round.seen,...config.catalog.map(x=>x.id)];
  const before=serializeBattle(b);
  assert.throws(()=>rerollAugment(b,0,0,true),/다른 증강체가 없음/);
  assert.equal(serializeBattle(b),before);
});
test('서비스: 사용권 차감은 엔진 리롤 성공 뒤에만, 실시간 리롤은 제한 시간을 늘리지 않음(소스 계약)',()=>{
  const native=src('../galmaegiman-confirmed/src/native.ts');
  const n=native.indexOf('rerollAugment(next,0,v.reroll,bonusReroll)'),c=native.indexOf("if(bonusReroll)await game.webConsumeBonusReroll(id,`reroll:${f.id}:${f.revision}`)");
  assert(n>0&&c>n,'네이티브: 엔진 성공 뒤 요청 번호(세션·revision)로 1회 차감');
  const service=src('../galmaegiman-confirmed/src/pvp/service.ts');
  const line=service.split('\n').find(l=>l.includes("if('reroll' in input.command){"));
  assert(line,'실시간 리롤 분기');
  assert(line.indexOf('rerollCommand(b,side,input.command.reroll,bonus)')<line.indexOf('consumeReroll('),'실시간: 리롤 성공 뒤 차감');
  assert(!line.includes('deadline'),'리롤은 제한 시간을 바꾸지 않음');
  assert.match(service,/if\(!\('reroll' in input\.command\)\)r=await this\.advance\(tx,r,false\);/);
});
test('환생·보유 개체 식별과 이미지가 증강 DTO 통합 뒤에도 유지(소스 계약)',()=>{
  const native=src('../galmaegiman-confirmed/src/native.ts'),view=src('../galmaegiman-confirmed/src/pvp/realtime-native-view.ts');
  for(const s of [native,view]){
    assert.match(s,/id: ?x?f?\.rec\.ownedCharacterId\?\?(x|f)\.rec\.id, ?combatDefinitionId: ?(x|f)\.rec\.id/);
    assert.match(s,/image\((R, root, )?(x|f)\.rec\.ownedCharacterId\?\?(x|f)\.rec\.id/);
    assert.match(s,/publicShield\(b, ?(x|f)\)/);
  }
  assert.match(src('../galmaegiman-confirmed/src/pvp/service.ts'),/this\.roster\(tx,r\.player0Id,prep\.teams\[0\]\.map\(x=>x\.uid\)/);
});
test('스토리 입장 조건·문구는 증강 통합으로 바뀌지 않음(소스 계약)',()=>{
  const native=src('../galmaegiman-confirmed/src/native.ts');
  assert.match(native,/throw new GameError\('앞 전투를 먼저 클리어해 주세요\.'\)/);
  assert.doesNotMatch(native,/앞 전투와 승진 조건을 확인해 주세요/);
});
