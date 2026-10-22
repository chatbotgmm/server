import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattleService,serializeServiceBattle} from '../galmaegiman-confirmed/src/battle/service-factory.ts';
import {augmentCatalogV2,parseAugmentCatalogV2} from '../galmaegiman-confirmed/src/battle/augment-catalog-v2.ts';
import {choose,chooseTogether,skillPreview,automaticFreestyleChoice} from '../galmaegiman-confirmed/src/battle/engine.ts';
import {eligibleAugmentIds} from '../galmaegiman-confirmed/src/battle/augment-eligibility.ts';
import {FREESTYLE_SELECTION_ENABLED,validateFreestyleChoice,publicFreestyleSelection} from '../galmaegiman-confirmed/src/battle/freestyle-selection.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url)));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
const R=rulesFrom(data),options={version:2,weights:{silver:1,gold:1,prismatic:1}};
const rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const make=s=>s.create([rec('a')],[rec('b')],91);
test('60개 고유 ID 유지, 유효 등급별 개수 가변',()=>{
 const catalog=structuredClone(augmentCatalogV2);catalog.augments[0].tier='gold';
 assert.equal(parseAugmentCatalogV2(catalog).augments.length,60);
 for(const mutate of [c=>c.augments[0].tier='unknown',c=>c.augments.pop(),c=>c.augments[0].id=c.augments[1].id,c=>c.augments[0].effect='choose-own-attribute']){
  const c=structuredClone(catalog);mutate(c);assert.throws(()=>parseAugmentCatalogV2(c));
 }
});
test('구 V2 역상성 스냅샷 복원·재시작 때 효과와 gold 보존',()=>{
 const catalog=structuredClone(augmentCatalogV2),a=catalog.augments.find(a=>a.id==='reverse');delete a.effect;a.tier='gold';a.name='역상성';
 const old=createBattleService(R,{...options,catalog}),current=createBattleService(R,options),b=make(old),json=serializeServiceBattle(b);
 for(const next of [current.restore(json),current.restart(json,[rec('c')],[rec('d')],91)]){
  assert.equal(next.R.battleAug.get('reverse').effect,undefined);assert.equal(next.R.battleAug.get('reverse').tier,'gold');assert(eligibleAugmentIds(next,0).includes('reverse'));
 }
 assert.equal(current.compatibleFingerprint(json),old.fingerprint);
});
test('속성 선택 계약은 대상·진영·phase·10종 whitelist와 재전송 검증',()=>{
 const snapshot={selectionId:'r:1:0',phaseId:'r:1',side:0,targetKey:'unit-a',opponentAttribute:'FIRE'};
 const p={selectionId:snapshot.selectionId,phaseId:snapshot.phaseId,targetKey:snapshot.targetKey,attribute:'SEA'};
 assert.deepEqual(validateFreestyleChoice(snapshot,0,'r:1',p),{attribute:'SEA',duplicate:false});
 assert.deepEqual(validateFreestyleChoice({...snapshot,chosen:'SEA'},0,'r:1',p),{attribute:'SEA',duplicate:true});
 for(const patch of [{attribute:'WATER'},{targetKey:'enemy'},{phaseId:'old'},{selectionId:'other'},{extra:1}])assert.throws(()=>validateFreestyleChoice(snapshot,0,'r:1',{...p,...patch}));
 assert.throws(()=>validateFreestyleChoice(snapshot,1,'r:1',p));assert.throws(()=>validateFreestyleChoice(snapshot,0,'old',p));
 assert.throws(()=>validateFreestyleChoice({...snapshot,chosen:'FIRE'},0,'r:1',p));
 const dto=publicFreestyleSelection(snapshot);assert.equal(dto.attributes.length,10);assert.equal(dto.side,undefined);assert.equal(dto.opponentAttribute,'FIRE');assert.equal(snapshot.chosen,undefined);
});

test('자유자재는 양측 확정 후 공개 상대속성 고정, 내 유닛만 한 번 변경',()=>{
 const b=make(createBattleService(R,options));b.sides[0].offers=['reverse'];b.sides[1].offers=['first'];
 const old=b.sides[0].fighters[0].attr;
 chooseTogether(b,[{augment:'reverse'},{augment:'first'}]);assert.equal(b.phase,'choose');
 assert.equal(b.sides[0].fighters[0].attr,old);const snap=b.sides[0].freestyle;
 assert.equal(snap.opponentAttribute,b.sides[1].fighters[0].attr);
 const p=automaticFreestyleChoice(b,0),before=serializeServiceBattle(b);
 assert.throws(()=>choose(b,0,{freestyle:{...p,targetKey:'enemy'}}));assert.equal(serializeServiceBattle(b),before);
 choose(b,0,{freestyle:p});assert.equal(b.phase,'action');assert.equal(b.sides[0].fighters[0].attr,p.attribute);
 assert.equal(b.sides[0].freestyle.chosen,p.attribute);assert.equal(b.events.at(-1).reason,'freestyle');
});

test('실시간 속성 선택은 복제본에서 원자 해결, 자동/재접속/AI/비보유 대기 보존',async()=>{
 const {required,resolvePhase,automaticCommand,validateCommand}=await import('../galmaegiman-confirmed/src/battle/realtime-adapter.ts');
 const {chooseBotCommand}=await import('../galmaegiman-confirmed/src/battle/realtime-bot.ts');
 const {publicAttributeSelection,publicAugmentState}=await import('../galmaegiman-confirmed/src/battle/public-dto.ts');
 const service=createBattleService(R),b=make(service);b.sides[0].offers=['reverse'];b.sides[1].offers=['first'];
 const next=resolvePhase(b,[{choose:{augment:'reverse'}},{choose:{augment:'first'}}],[false,false]);
 assert.equal(b.sides[0].freestyle,undefined);assert.equal(required(next,0),true);assert.equal(required(next,1),false);
 assert.equal(publicAugmentState(next).choiceKind,'own-attribute');assert.equal(publicAttributeSelection(next,1),null);
 const saved=serializeServiceBattle(next),restored=service.restore(saved),cmd=automaticCommand(restored,0);
 assert.deepEqual(cmd,chooseBotCommand(restored,0));validateCommand(restored,0,cmd);
 assert.throws(()=>validateCommand(restored,0,{choose:{...cmd.choose,augment:'first'}}));
 assert.throws(()=>resolvePhase(restored,[{choose:{freestyle:{...cmd.choose.freestyle,attribute:'bad'}}},null],[false,false]));
 assert.equal(serializeServiceBattle(restored),saved);
 const resolved=resolvePhase(restored,[cmd,null],[false,false]),timed=resolvePhase(restored,[null,null],[true,false]);
 assert.equal(serializeServiceBattle(resolved),serializeServiceBattle(timed));assert.equal(resolved.phase,'action');
 assert.equal(publicAugmentState(resolved).choiceKind,undefined);assert.equal(publicAttributeSelection(resolved,0),null);
 assert.deepEqual(service.restore(serializeServiceBattle(resolved)).sides,resolved.sides);
});

test('자유자재 동시 선택은 고정된 상대 snapshot을 사용하고 제출 순서로 영향받지 않음',async()=>{
 const {resolvePhase}=await import('../galmaegiman-confirmed/src/battle/realtime-adapter.ts');
 const service=createBattleService(R),b=make(service);b.sides[0].offers=['reverse'];b.sides[1].offers=['reverse'];
 chooseTogether(b,[{augment:'reverse'},{augment:'reverse'}]);
 const a=automaticFreestyleChoice(b,0),c=automaticFreestyleChoice(b,1),snap=structuredClone(b.sides.map(s=>s.freestyle));
 const next=resolvePhase(b,[{choose:{freestyle:a}},{choose:{freestyle:c}}],[false,false]);
 assert.deepEqual(b.sides.map(s=>s.freestyle),snap);assert.equal(next.sides[0].fighters[0].attr,a.attribute);assert.equal(next.sides[1].fighters[0].attr,c.attribute);
 assert.equal(next.phase,'action');assert.equal(next.sides[0].freestyle.opponentAttribute,snap[0].opponentAttribute);
});
