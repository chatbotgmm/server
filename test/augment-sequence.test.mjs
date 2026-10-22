import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {balancedSixProfile,createAugmentSequence,parseSequenceProfile} from '../galmaegiman-confirmed/src/battle/augment-sequence.ts';
import {rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattleService,serializeServiceBattle} from '../galmaegiman-confirmed/src/battle/service-factory.ts';
import {rerollAugment} from '../galmaegiman-confirmed/src/battle/engine.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url)));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
const rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
function rng(seed){return ()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};}
test('명시 프로필의 정확 P개수 확률과 극단 경로, 6실버 불가',()=>{
 const w=balancedSixProfile.prismaticCountWeights;assert.equal(w.reduce((a,b)=>a+b),10000);
 assert.equal(w.slice(3).reduce((a,b)=>a+b)/10000,.03);assert.equal(w[6]/10000,.0001);
 let lower=0;
 for(let k=0;k<7;k++)for(const bias of [0,.999]){
  let calls=0;const seq=createAugmentSequence(balancedSixProfile,()=>++calls===1?(lower+w[k]/2)/10000:bias);
  assert.equal(seq.length,6);assert.equal(seq.filter(t=>t==='prismatic').length,k);assert(!seq.every(t=>t==='silver'));
  if(bias===.999)lower+=w[k];
 }
 // Fisher–Yates 셔플의 위치별 한계확률은 기대 개수 / 6이다.
 const p=w.reduce((sum,n,k)=>sum+n*k,0)/10000,g=w.reduce((sum,n,k)=>sum+n*(k===0?3.6:Math.floor((6-k)/2)+(6-k)%2*.6),0)/10000;
 assert.equal(p,.8861);assert(Math.abs(g-2.81454)<1e-12);assert(Math.abs((6-p-g)-2.29936)<1e-12);
});
test('결정적 100000개 시퀀스 시뮬레이션은 프로필과 일치',()=>{
 const r=rng(43),counts=Array(7).fill(0);for(let i=0;i<100000;i++){const seq=createAugmentSequence(balancedSixProfile,r);counts[seq.filter(t=>t==='prismatic').length]++;assert(!seq.every(t=>t==='silver'));}
 for(let k=0;k<7;k++)assert(Math.abs(counts[k]/100000-balancedSixProfile.prismaticCountWeights[k]/10000)<.007);
 assert(counts[6]>0);console.log('100000회 P개수 분포',counts);
 assert.deepEqual(createAugmentSequence(balancedSixProfile,rng(42)),createAugmentSequence(balancedSixProfile,rng(42)));
});
test('기본 V2: 동일등급·리롤 유지·재접속/재시작 보존·미래순서 서버 저장',()=>{
 const service=createBattleService(rulesFrom(data));assert.equal(service.version,2);
 const b=service.create([rec('a')],[rec('b')],47);assert.equal(b.augmentOffers.sequence.length,6);
 const tier=b.augmentOffers.round.tier,index=b.augmentOffers.roundIndex;
 const same=()=>{for(const side of b.sides)for(const id of side.offers)assert.equal(b.R.battleAug.get(id).tier,tier);};same();
 rerollAugment(b,0,0);same();rerollAugment(b,0,0,true);same();assert.equal(b.augmentOffers.roundIndex,index);
 const saved=serializeServiceBattle(b),restored=service.restore(saved);assert.deepEqual(restored.augmentOffers,b.augmentOffers);
 const fresh=service.restart(saved,[rec('a')],[rec('b')],47);assert.deepEqual(fresh.augmentOffers.sequence,b.augmentOffers.sequence);
});
test('잘못된 프로필은 거부한다',()=>{
 for(const p of [{...balancedSixProfile,goldBias:2},{...balancedSixProfile,prismaticCountWeights:[1]},{...balancedSixProfile,prismaticCountWeights:Array(7).fill(0)},{...balancedSixProfile,prismaticCountWeights:[NaN,1,1,1,1,1,1]}])assert.throws(()=>parseSequenceProfile(p));
});

test('새 epoch capability만 신규 전투 진입, 구 진행 전투와 포기는 계속 허용',async()=>{
 const {supportsCombatEpoch,combatUpgradeRequired}=await import('../galmaegiman-confirmed/src/battle/service-factory.ts');
 const {CURRENT_COMBAT_RULES_EPOCH:E}=await import('../galmaegiman-confirmed/src/battle/combat-epoch.ts');
 for(const path of ['queue/attempt','queue/join','battle/start']){assert.equal(supportsCombatEpoch(path,E,undefined,undefined),false);assert.equal(supportsCombatEpoch(path,E,undefined,E),true);}
 for(const path of ['room/current','room/command','battle/current','battle/turn']){assert.equal(supportsCombatEpoch(path,E,undefined,undefined),true);assert.equal(supportsCombatEpoch(path,E,E,undefined),false);assert.equal(supportsCombatEpoch(path,E,E,E),true);}
 assert(supportsCombatEpoch('battle/turn',E,E,undefined,true));assert(supportsCombatEpoch('room/surrender',E,E,undefined));assert.equal(combatUpgradeRequired().upgradeRequired,true);
 const service=createBattleService(rulesFrom(data)),b=service.create([rec('a')],[rec('b')],1);
 assert.equal(b.combatRulesEpoch,E);assert.equal(service.restore(serializeServiceBattle(b)).combatRulesEpoch,E);
 const oldCatalog=structuredClone(b.R.data.augmentCatalog);const reverse=oldCatalog.augments.find(a=>a.id==='reverse');delete reverse.effect;reverse.tier='gold';
 for(const options of [{version:1},{version:2,weights:{silver:1,gold:1,prismatic:1},catalog:oldCatalog}]){const old=createBattleService(rulesFrom(data),options).create([rec('a')],[rec('b')],1);assert.equal(service.restart(serializeServiceBattle(old),[rec('a')],[rec('b')],1).combatRulesEpoch,undefined);}
});

test('공개 실시간 DTO는 현재 등급만 노출하고 미래 6순서/RNG/규칙 snapshot을 숨긴다',async()=>{
 const {mapRealtimeNativeBattle}=await import('../galmaegiman-confirmed/src/pvp/realtime-native-view.ts');
 const service=createBattleService(rulesFrom(data)),b=service.create([rec('a')],[rec('b')],4321);
 assert.equal(b.augmentOffers.sequence.length,6);assert.equal(typeof b.rng,'number');
 const room={roomId:'room',mode:'normal',phaseId:'room:1',revision:1,deadline:30,serverNow:1,canSubmit:true,mySubmitted:false,opponentReady:false,result:null};
 const forbidden=new Set(['sequence','sequenceProfile','prismaticCountWeights','goldBias','rng','seed','serviceRules','augmentOffers','roundIndex','offerSeen']);
 const inspect=value=>{if(value&&typeof value==='object')for(const [k,v] of Object.entries(value)){assert(!forbidden.has(k),`공개 금지 필드: ${k}`);inspect(v);}};
 for(const viewerSide of [0,1]){
  const dto=mapRealtimeNativeBattle({battle:b,viewerSide,sessionId:'room',names:['가','나'],room,inventory:[],assetRoot:'/tmp/no-assets'});inspect(dto);
  assert.equal(dto.teams[0].offers.length,3);assert.equal(dto.teams[1].offers.length,0);
  assert(dto.teams[0].offers.every(a=>a.tier===b.augmentOffers.round.tier));
 }
 // 별도 native snapshot은 private closure이므로 연결 정적 검사(실제 HTTP 통합 시험은 별도 필요).
 const source=readFileSync(new URL('../galmaegiman-confirmed/src/native.ts',import.meta.url),'utf8');
 const snapshot=source.slice(source.indexOf('function snapshot(f:Fight)'),source.indexOf("route('POST','battle/start'"));
 assert(snapshot.startsWith('function snapshot(f:Fight)'));assert(!snapshot.includes('...b,'));assert(!snapshot.includes('...f,'));
 for(const key of ['augmentOffers','sequenceProfile','prismaticCountWeights','rng:','seed:','serviceRules'])assert(!snapshot.includes(key),`native snapshot 공개 금지: ${key}`);
});

test('실제 act→전투불가 경로를 6회 넘겨도 7번째 제안·순환·반복 없음',async()=>{
 const {act,chooseTogether,activeOf}=await import('../galmaegiman-confirmed/src/battle/engine.ts');
 const service=createBattleService(rulesFrom(data)),b=service.create([rec('a'),rec('aa')],[rec('b'),rec('bb')],99);
 let offerRounds=1;const sequence=[...b.augmentOffers.sequence];
 const finish=()=>{
  if(b.phase!=='choose')return;
  chooseTogether(b,b.sides.map(s=>({...(s.offers?{augment:s.offers.find(id=>id!=='reverse')}:{ }),...(s.needSwitch?{switchTo:s.fighters.findIndex(f=>f.hp>0)}:{})})));
 };
 finish();
 for(let i=1;i<=9;i++){
  // 장기전/반복 부활 경계 fixture: 각 회차 시작만 복구하고 실제 피해·전투불가·제안 함수를 통과한다.
  for(const s of b.sides){s.augment={};for(const f of s.fighters){f.maxHp=f.hp=1000000000;f.passive={};f.mods=[];f.guards=[];f.status={};f.endure=0;f.protect=false;f.augment={};f.base={patk:100,pdef:100,matk:100,mdef:100,spd:f.side?1:100};f.scale=1;}}
  const a=activeOf(b,0),foe=activeOf(b,1);foe.hp=1;
  a.skills=[{id:'cap-test-hit',name:'시험',power:100000000,cat:'phys',cd:0,effects:[],desc:''}];
  foe.skills=[{id:'cap-test-idle',name:'시험대기',power:0,cd:0,effects:[],desc:''}];
  act(b,[{kind:'skill',skillId:'cap-test-hit'},{kind:'skill',skillId:'cap-test-idle'}],{prevalidated:true});
  assert.equal(foe.hp,0);assert.equal(b.sides[1].needSwitch,true);
  if(i<6){offerRounds++;assert(b.sides.every(s=>s.offers?.length===3));assert.equal(b.augmentOffers.round.tier,sequence[i]);for(const s of b.sides)assert(s.offers.every(id=>b.R.battleAug.get(id).tier===sequence[i]));}
  else {assert(b.sides.every(s=>s.offers===null));assert.equal(b.augmentOffers.round,undefined);assert.equal(b.augmentOffers.roundIndex,6);}
  finish();assert.equal(b.phase,'action');assert.deepEqual(b.augmentOffers.sequence,sequence);
 }
 assert.equal(offerRounds,6);
});
