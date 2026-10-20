import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createOfferRound,rerollOffer} from '../galmaegiman-confirmed/src/battle/augment-offers.ts';
const tiers=['silver','gold','prismatic'],cat=tiers.flatMap(tier=>Array.from({length:20},(_,i)=>({id:`${tier}${i}`,tier}))),all=cat.map(x=>x.id),weights={silver:1,gold:1,prismatic:1};
const seeded=seed=>()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296};
const make=(seed=1,owned=[[],[]],eligible=[all,all],w=weights)=>createOfferRound(cat,owned,eligible,w,seeded(seed));
test('양측 공통 등급, 서로 겹치지 않는 3개, 보유 ID 제외: 1000시드',()=>{
 for(let seed=0;seed<1000;seed++){const x=make(seed,[['silver0','gold0'],['prismatic0','gold1']]);assert.equal(x.kind,'offered');const r=x.round;assert.deepEqual(r.offers.map(x=>x.length),[3,3]);assert.equal(new Set(r.offers.flat()).size,6);assert(r.offers.flat().every(id=>cat.find(a=>a.id===id).tier===r.tier));assert(!r.offers.flat().some(id=>['silver0','gold0','prismatic0','gold1'].includes(id)));}
});
test('같은 입력과 시드 재현 및 JSON 저장 복원',()=>{assert.deepEqual(make(91),make(91));const r=make().round,copy=JSON.parse(JSON.stringify(r));assert.deepEqual(rerollOffer(r,0,0,cat,[[],[]],all,seeded(7)),rerollOffer(copy,0,0,cat,[[],[]],all,seeded(7)));assert.deepEqual(r,copy);});
test('편성별 후보가 비대칭이어도 양쪽 3개 배정',()=>{const a=['silver0','silver1','silver2','silver3','silver4','silver5'],b=['silver0','silver1','silver2'];for(let seed=0;seed<100;seed++){const r=make(seed,[[],[]],[a,b]).round;assert.deepEqual([...r.offers[1]].sort(),b);assert(r.offers[0].every(x=>!b.includes(x)));}});
test('고갈 등급 제외 후 공동 대체, 전체 고갈은 난수/상태 소비 없음',()=>{const e=all.filter(x=>!x.startsWith('silver')||x==='silver0');assert.notEqual(make(1,[[],[]],[e,e]).round.tier,'silver');let calls=0;assert.deepEqual(createOfferRound(cat,[[],[]],[all.slice(0,5),all.slice(0,5)],weights,()=>{calls++;return 0}),{kind:'exhausted'});assert.equal(calls,0);});
test('재추첨 등급 유지, 모든 기존 노출 배제, 칸당 한 번',()=>{const r=make().round,seen=[...r.seen];for(const side of [0,1])for(let slot=0;slot<3;slot++){const v=rerollOffer(r,side,slot,cat,[[],[]],all,seeded(side+slot));assert.equal(v.kind,'rerolled');assert(!seen.includes(v.id));seen.push(v.id);assert.equal(cat.find(a=>a.id===v.id).tier,r.tier);assert.throws(()=>rerollOffer(r,side,slot,cat,[[],[]],all,()=>0));}assert.equal(new Set(seen).size,12);});
test('재추첨 고갈은 기존 제안/사용권/난수 유지',()=>{const r=make().round,before=structuredClone(r);let calls=0;assert.deepEqual(rerollOffer(r,0,0,cat,[[],[]],r.seen,()=>{calls++;return 0}),{kind:'exhausted'});assert.deepEqual(r,before);assert.equal(calls,0);});
test('등급 경계와 잘못된 입력',()=>{for(const [n,tier] of [[0,'silver'],[1/3,'gold'],[2/3,'prismatic'],[0.999,'prismatic']])assert.equal(createOfferRound(cat,[[],[]],[all,all],weights,()=>n).round.tier,tier);assert.throws(()=>make(1,[[],[]],[all,all],{...weights,gold:-1}));assert.throws(()=>createOfferRound([...cat,cat[0]],[[],[]],[all,all],weights,()=>0));});
