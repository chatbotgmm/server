import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rulesFrom} from '../galmaegiman-confirmed/src/battle/core.ts';
import {createBattleService,serializeServiceBattle,supportsAugmentVersion,requiredAugmentClientVersion} from '../galmaegiman-confirmed/src/battle/service-factory.ts';
import {publicAugment,publicAugmentState,publicShield} from '../galmaegiman-confirmed/src/battle/public-dto.ts';
import {mapRealtimeNativeBattle} from '../galmaegiman-confirmed/src/pvp/realtime-native-view.ts';
import {createHash} from 'node:crypto';
import {serializeBattle} from '../galmaegiman-confirmed/src/battle/realtime-adapter.ts';
const data=JSON.parse(readFileSync(new URL('./battle-fixture.json',import.meta.url),'utf8'));
data.recipeValues=Object.fromEntries(Object.keys(data.units).map(id=>[id,data.recipePower.baseline]));
const R=rulesFrom(data),weights={silver:1,gold:1,prismatic:1};
const rec=uid=>({uid,id:'D1',name:'시험',tier:'LIMITED',level:30,xp:0,stage:0,evoAugs:[]});
const make=s=>s.create([rec('a')],[rec('b')],1234);
test('명시 V1 서비스와 기존 규칙 지문 유지',()=>{const s=createBattleService(R,{version:1}),b=make(s);assert.equal(b.R.augmentVersion,1);assert.equal(b.effectsVersion,undefined);assert.equal(s.fingerprint,JSON.stringify(R.data));assert.equal(b.R.battleAug.size,24);});
test('명시 V2: 60개와 등급별 설정, caller 변경 격리',()=>{const options={version:2,weights:{...weights}},s=createBattleService(R,options);options.weights.silver=99;const b=make(s);assert.equal(b.R.battleAug.size,60);assert.equal(b.effectsVersion,2);assert.deepEqual(b.augmentOffers.weights,weights);assert.equal(b.augmentOffers.version,2);assert.equal(b.sides[0].offers.length,3);});
test('잘못된 버전, 가중치, 카탈로그 거부',()=>{for(const opts of [{version:3},{version:2},{version:2,weights:{silver:0,gold:0,prismatic:0}},{version:2,weights:{...weights,extra:1}}])assert.throws(()=>createBattleService(R,opts));const s=createBattleService(R,{version:2,weights}),catalog=structuredClone(s.R.data.augmentCatalog);catalog.augments[0].tier='invalid';assert.throws(()=>createBattleService(R,{version:2,weights,catalog}));});
test('V1 저장 전투는 V2 서비스에서도 V1로 복원',()=>{const b=make(createBattleService(R,{version:1})),service=createBattleService(R,{version:2,weights});for(const saved of [serializeBattle(b),serializeServiceBattle(b)]){const restored=service.restore(saved);assert.equal(restored.R.augmentVersion,1);assert.deepEqual(restored.sides,b.sides);assert.equal(restored.R.battleAug.get('drain').p.pct,R.battleAug.get('drain').p.pct);}});
test('V2 저장 규칙과 가중치를 서비스 설정 변경 후에도 보존',()=>{const b=make(createBattleService(R,{version:2,weights})),json=serializeServiceBattle(b),service=createBattleService(R,{version:2,weights:{silver:0,gold:0,prismatic:1}}),restored=service.restore(json);assert.deepEqual(restored.augmentOffers,b.augmentOffers);assert.deepEqual(restored.R.data,b.R.data);assert.equal(restored.serviceRules,undefined);});
test('불완전하거나 등급 불일치인 V2 저장 전투 거부',()=>{const service=createBattleService(R,{version:2,weights}),b=make(service);assert.throws(()=>service.restore(serializeBattle(b)));const saved=JSON.parse(serializeServiceBattle(b));saved.augmentOffers.catalog[0].tier='gold';assert.throws(()=>service.restore(JSON.stringify(saved)));});
test('DTO는 이름 설명 수치 버전 등급 리롤만 복사',()=>{const b=make(createBattleService(R,{version:2,weights})),id=b.sides[0].offers[0],dto=publicAugment(b,id,true);assert.deepEqual(Object.keys(dto).sort(),['id','name','desc','p','version','tier','rerolled'].sort());assert.equal(dto.version,2);dto.p.foo=1;assert.equal(b.R.battleAug.get(id).p.foo,undefined);assert.deepEqual(publicAugmentState(b),{augmentVersion:2,effectsVersion:2});assert.throws(()=>publicAugment(b,'unknown'));});
test('보호막은 현재 유효량만 공개하고 내부 상태는 숨김',()=>{const b=make(createBattleService(R,{version:2,weights})),f=b.sides[0].fighters[0];f.augment={shield:20,shieldUntil:b.turn+1,secret:99};assert.deepEqual(publicShield(b,f),{shield:20,shieldTurns:2});b.turn+=2;assert.deepEqual(publicShield(b,f),{shield:0,shieldTurns:0});});
test('V2 클라이언트 지원 헤더는 호환성만 확인',()=>{assert(supportsAugmentVersion(1,undefined));for(const h of [undefined,'1',2,['2']])assert.equal(supportsAugmentVersion(2,h),false);assert(supportsAugmentVersion(2,'2'));});
test('실제 서비스 생성점과 공개 mapper 배선 정적 확인',()=>{const read=p=>readFileSync(new URL('../galmaegiman-confirmed/src/'+p,import.meta.url),'utf8');const service=read('pvp/service.ts');assert.equal((service.match(/this.battles.(?:create|restart)\(/g)||[]).length,3);assert(!service.includes('deserializeBattle(this.R'));assert(service.includes('serializeServiceBattle as serializeBattle'));const mapper=read('pvp/realtime-native-view.ts');for(const field of ['absorbed: e.absorbed','shieldBefore: e.shieldBefore','shieldAfter: e.shieldAfter','publicShield(b,f)','const R = b.R','offers: own &&','const skills = own ?'])assert(mapper.includes(field),field);assert(read('native.ts').includes('const b=battles.create(team,foe,seed)'));assert(read('pvp/routes.ts').includes('supportsAugmentClient'));});

test('설정 전환 후에도 알려진 V1 및 V2 방 지문 정확 일치, 일반 데이터 변경은 거부',()=>{
 const v1=createBattleService(R,{version:1}),v2=createBattleService(R,{version:2,weights}),changedWeights=createBattleService(R,{version:2,weights:{silver:0,gold:1,prismatic:0}});
 const hash=f=>createHash('sha256').update('realtime-engine-v3-casual-growth\0').update(f).digest('hex');
 for(const origin of [v1,v2])for(const next of [v1,v2,changedWeights]){const saved=serializeServiceBattle(make(origin));assert.equal(hash(next.compatibleFingerprint(saved)),hash(origin.fingerprint));}
 assert.equal(v2.compatibleFingerprint(serializeBattle(make(v1))),v1.fingerprint);
 const changed=structuredClone(data);changed.basicPower++;const incompatible=createBattleService(rulesFrom(changed),{version:1});assert.equal(incompatible.compatibleFingerprint(serializeServiceBattle(make(v1))),null);
});
test('준비 방 시작 시 서버 현재 버전 대신 저장 방의 V1/V2 설정 보존',()=>{const v1=createBattleService(R,{version:1}),v2=createBattleService(R,{version:2,weights});for(const [origin,current] of [[v1,v2],[v2,v1]]){const prior=make(origin),b=current.restart(serializeServiceBattle(prior),[rec('newA')],[rec('newB')],98);assert.equal(b.R.augmentVersion,origin.version);assert.deepEqual(b.augmentOffers?.weights,prior.augmentOffers?.weights);}});
test('실제 공개 mapper는 상대 후보·기술·내부 상태 없이 새 수치 전달',()=>{
 const b=make(createBattleService(R,{version:2,weights}));b.sides[0].augs=[b.sides[0].offers[0]];b.sides[0].fighters[0].augment={shield:12,shieldUntil:b.turn};
 b.events=[{id:1,turn:1,kind:'damage',target:{side:0,slot:0,key:'a',id:'D1'},amount:2,absorbed:8,shieldBefore:20,shieldAfter:12,text:'피해',logIndex:0}];b.eventSeq=1;
 const room={roomId:'r',mode:'normal',phaseId:'r:1',revision:1,deadline:30,serverNow:1,canSubmit:true,mySubmitted:false,opponentReady:false,result:null};
 for(const viewerSide of [0,1]){const dto=mapRealtimeNativeBattle({battle:b,viewerSide,sessionId:'r',names:['가','나'],room,inventory:[],assetRoot:'/tmp/no-assets'});assert.equal(dto.augmentVersion,2);assert.equal(dto.teams[0].offers.length,3);assert.deepEqual(dto.teams[1].offers,[]);assert.deepEqual(dto.teams[1].fighters[0].skills,[]);assert.equal(dto.teams[viewerSide===0?0:1].fighters[0].shield,12);assert.equal(dto.displayEvents[0].absorbed,8);assert.equal(dto.displayEvents[0].shieldAfter,12);assert.equal(dto.displayEvents[0].target.side,viewerSide===0?0:1);const text=JSON.stringify(dto);for(const key of ['"rng":','"serviceRules":','"augmentOffers":','"offerSeen":','"shieldUntil":'])assert(!text.includes(key),key);}
});

test('구형 클라이언트 경계: V1→V2·V2→V1 모두 대상방 버전, 비전투 및 취소 허용',()=>{for(const path of ['battle/current','battle/turn','room/current','room/command']){assert(supportsAugmentVersion(requiredAugmentClientVersion(path,2,1),undefined));assert(!supportsAugmentVersion(requiredAugmentClientVersion(path,1,2),undefined));}for(const path of ['battle/start','queue/attempt','queue/join']){assert(!supportsAugmentVersion(requiredAugmentClientVersion(path,2,1),undefined));assert(supportsAugmentVersion(requiredAugmentClientVersion(path,1,2),undefined));}for(const path of ['growth','state','catalog','team','heal','rank','queue/cancel','queue/current','room/surrender'])assert(supportsAugmentVersion(requiredAugmentClientVersion(path,2,2),undefined));});

test('네이티브 leave 포기는 미지원 클라이언트도 허용',()=>{assert.equal(requiredAugmentClientVersion('battle/turn',2,2,true),1);assert.equal(requiredAugmentClientVersion('battle/turn',2,2,false),2);assert.equal(requiredAugmentClientVersion('room/command',2,2,true),2);});
