import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {augmentCatalogV2,parseAugmentCatalogV2,createAugmentConfigV2,parameterRange} from '../src/battle/augment-catalog-v2.ts';
import {rulesFrom,battleAugIds} from '../src/battle/core.ts';
const read=url=>JSON.parse(readFileSync(url,'utf8'));
const spec=read(new URL('../../docs/handoffs/augment-60-draft/catalog/augment-catalog-v1.json',import.meta.url));
const overrides=read(new URL('../../docs/handoffs/augment-60-draft/catalog/legacy-v2-runtime-overrides.json',import.meta.url)).overrides;
const fixture=read(new URL('../../test/battle-fixture.json',import.meta.url));
// 원본 YAML fixture에는 서버가 채우는 조합 가치가 없어요. 이 테스트는 증강 계약만 검증해요.
delete fixture.recipePower;
const copy=()=>structuredClone(augmentCatalogV2);
const edit=(id,key,value)=>{const c=copy();c.augments.find(a=>a.id===id).p[key]=value;return c;};
test('최종 설계의 60 ID·등급·설명과 숫자 계약이 일치',()=>{
 const parsed=parseAugmentCatalogV2(augmentCatalogV2);
 assert.equal(parsed.augments.length,60);
 const tiers={SILVER:'silver',GOLD:'gold',PRISM:'prismatic'};
 assert.deepEqual(parsed.augments.map(a=>a.id),spec.augments.map(a=>a.id));
 for(const a of spec.augments){
  const current=parsed.augments.find(x=>x.id===a.id);
  assert.equal(current.tier,tiers[a.tier]);assert.equal(current.name,a.name);assert.equal(current.desc,a.shortDescription);
  if(overrides[a.id])assert.deepEqual(current.p,overrides[a.id].p);
  else if(!a.existing)assert.deepEqual(current.p,Object.fromEntries(Object.entries(a.effectParams).filter(([,n])=>typeof n==='number')));
 }
 for(const tier of Object.values(tiers))assert.equal(parsed.augments.filter(a=>a.tier===tier).length,20);
 assert.deepEqual(parsed.augments.find(a=>a.id==='overflowguard').p,{overflowToShieldPct:100,shieldMaxHpCapPct:20,shieldTurns:2,conversions:3});
 assert.ok(!parsed.augments.some(a=>a.id==='dryspell'));
});
test('레거시 골드 11개 p는 V1과 동일',()=>{
 const legacy=rulesFrom(fixture);
 for(const a of augmentCatalogV2.augments.filter(a=>a.tier==='gold'&&battleAugIds.includes(a.id)))assert.deepEqual(a.p,legacy.battleAug.get(a.id).p,a.id);
});
test('strip 0턴만 허용하고 필수 숫자·정수·유한값·키를 검사',()=>{
 assert.doesNotThrow(()=>parseAugmentCatalogV2(edit('strip','turns',0)));
 for(const [id,key,value] of [['bulwark','turns',0],['strip','turns',1],['strip','pct',1],['sharp','firstHitOnly',2],['sharp','uses',1.5],['overflowguard','conversions',0],['slowhammer','forcePriority',-4],['focus','value',0],['thorns','pct',101],['thorns','pct',NaN],['thorns','pct',Infinity],['thorns','pct','10'],['thorns','unknown',1]])assert.throws(()=>parseAugmentCatalogV2(edit(id,key,value)),`${id}.${key}=${value}`);
 const c=copy();delete c.augments[0].p.uses;assert.throws(()=>parseAugmentCatalogV2(c));
});
test('모든 수치 키의 정상 경계값과 범위 밖을 검사',()=>{
 for(const a of augmentCatalogV2.augments)for(const k of Object.keys(a.p)){
  const {min,max,integer}=parameterRange(k,a.id);
  for(const v of [min,max])assert.doesNotThrow(()=>parseAugmentCatalogV2(edit(a.id,k,v)),`${a.id}.${k}=${v}`);
  for(const v of [min-1,max+1,...(integer?[min+0.5]:[])])assert.throws(()=>parseAugmentCatalogV2(edit(a.id,k,v)),`${a.id}.${k}=${v}`);
 }
});
test('고정 ID·등급·20개씩·버전·외부 변조를 검증',()=>{
 for(const mutate of [c=>c.version=1,c=>c.augments.pop(),c=>c.augments.push(c.augments[0]),c=>c.augments[0].id='other',c=>c.augments[0].id=c.augments[1].id,c=>c.augments[0].tier='gold',c=>c.augments[0].version=1,c=>c.extra=true,c=>c.augments[0].p=null]){const c=copy();mutate(c);assert.throws(()=>parseAugmentCatalogV2(c));}
 assert.throws(()=>{augmentCatalogV2.augments[0].id='bad';});
 assert.throws(()=>{augmentCatalogV2.augments[0].p.other=1;});
});
test('명시적 버전 2로만 활성화하며 기본 V1 규칙은 그대로',()=>{
 const before=JSON.stringify(fixture),v1=rulesFrom(fixture),v2=rulesFrom(fixture,{augmentVersion:2});
 assert.equal(v1.augmentVersion,1);assert.equal(v1.battleAugments.length,24);
 assert.equal(v2.augmentVersion,2);assert.equal(v2.battleAugments.length,60);
 assert.equal(JSON.stringify(fixture),before);
 assert.throws(()=>rulesFrom(fixture,{augmentCatalog:augmentCatalogV2}));
 assert.throws(()=>rulesFrom({...fixture,augmentCatalog:augmentCatalogV2}));
 assert.throws(()=>rulesFrom(fixture,{augmentVersion:3}));
 assert.equal(rulesFrom({...fixture,augmentVersion:2}).battleAugments.length,60);
 assert.equal(rulesFrom({...fixture,augmentVersion:2},{augmentVersion:1}).battleAugments.length,24);
 v2.battleAug.get('overtime').p.pct=15;assert.equal(augmentCatalogV2.augments.find(a=>a.id==='overtime').p.pct,8);
});
test('가중치에는 기본 확률이 없고 유한·비음수 합을 요구',()=>{
 assert.deepEqual(createAugmentConfigV2({silver:1,gold:0,prismatic:0}).weights,{silver:1,gold:0,prismatic:0});
 for(const weights of [undefined,null,{}, {silver:0,gold:0,prismatic:0},{silver:-1,gold:1,prismatic:1},{silver:NaN,gold:1,prismatic:1},{silver:Infinity,gold:1,prismatic:1},{silver:Number.MAX_VALUE,gold:Number.MAX_VALUE,prismatic:1},{silver:1,gold:1,prismatic:1,extra:1}])assert.throws(()=>createAugmentConfigV2(weights));
});
