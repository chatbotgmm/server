// 서버 신뢰 경계. 신규 전투 기본은 V2, 저장 전투는 고정된 예전 규칙을 유지한다.
import {CURRENT_COMBAT_RULES_EPOCH} from './combat-epoch.js';
import {balancedSixProfile,parseSequenceProfile,type AugmentSequenceProfile} from './augment-sequence.js';
import {rulesFrom, type Rules} from './core.js';
import type {BattleData} from './rules.js';
import {createBattle, type Battle} from './engine.js';
import type {UnitRecord} from './growth.js';
import {augmentCatalogV2, parseAugmentCatalogV2, createAugmentConfigV2, type AugmentCatalogV2, type CatalogTier} from './augment-catalog-v2.js';
import {serializeBattle, deserializeBattle} from './realtime-adapter.js';
export type BattleServiceOptions = {version:1} | {version:2;sequenceProfile?:AugmentSequenceProfile;weights:Record<CatalogTier,number>;catalog?:AugmentCatalogV2};
function legacyData(R:Rules):BattleData {
  const data=structuredClone(R.data);delete data.augmentCatalog;if(data.augmentVersion===2)data.augmentVersion=1;return data;
}
const canonical=(value:unknown):string=>Array.isArray(value)?'['+value.map(canonical).join(',')+']':value&&typeof value==='object'?'{'+Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>JSON.stringify(k)+':'+canonical(v)).join(',')+'}':JSON.stringify(value)??'null';
function baseFingerprint(R:Rules){const d=legacyData(R);delete d.augmentVersion;return canonical(d);}
export function createBattleService(base:Rules,options:BattleServiceOptions={version:2,weights:{silver:1,gold:1,prismatic:1},sequenceProfile:balancedSixProfile}) {
  if(options.version!==1&&options.version!==2)throw new Error('지원하지 않는 서비스 증강 버전');
  const legacy=rulesFrom(legacyData(base));
  const catalog=options.version===2?parseAugmentCatalogV2(options.catalog??augmentCatalogV2):undefined;
  const config=options.version===2?{...createAugmentConfigV2(options.weights,catalog),...(options.sequenceProfile?{sequenceProfile:parseSequenceProfile(options.sequenceProfile)}:{})}:undefined;
  const R=catalog?rulesFrom({...legacyData(base),augmentVersion:2,augmentCatalog:catalog}):legacy;
  const innateEnabled=!!(config&&(config.sequenceProfile||catalog?.augments.some(a=>a.effect==='choose-own-attribute')));
  return {
    version:options.version, combatRulesEpoch:config&&(config.sequenceProfile||catalog?.augments.some(a=>a.effect==='choose-own-attribute'))?CURRENT_COMBAT_RULES_EPOCH:undefined, R, legacy,
    // V1 해시 입력은 그대로 유지한다. V2 가중치도 방 규칙 지문에 포함한다.
    fingerprint:JSON.stringify(R.data)+(config?'\0'+JSON.stringify(config):'')+(innateEnabled?'\0innateDamageVersion:1':''),
    create(teamA:UnitRecord[],teamB:UnitRecord[],seed:number){return createBattle(R,teamA,teamB,seed,config);},
    restart(json:string,teamA:UnitRecord[],teamB:UnitRecord[],seed:number):Battle {
      const b=this.restore(json);
      const savedConfig=b.R.augmentVersion===2?{...createAugmentConfigV2(b.augmentOffers!.weights,b.R.data.augmentCatalog),...(b.augmentOffers!.sequenceProfile?{sequenceProfile:parseSequenceProfile(b.augmentOffers!.sequenceProfile)}:{})}:undefined;
      const next=createBattle(b.R,teamA,teamB,seed,savedConfig);
      if(b.combatRulesEpoch)next.combatRulesEpoch=b.combatRulesEpoch;else delete next.combatRulesEpoch;
      if(b.innateDamageVersion===1)next.innateDamageVersion=1;else delete next.innateDamageVersion;return next;
    },
    compatibleFingerprint(json:string):string|null {
      try{const b=this.restore(json);if(baseFingerprint(b.R)!==baseFingerprint(legacy))return null;
        const savedConfig=b.R.augmentVersion===2?{...createAugmentConfigV2(b.augmentOffers!.weights,b.R.data.augmentCatalog),...(b.augmentOffers!.sequenceProfile?{sequenceProfile:parseSequenceProfile(b.augmentOffers!.sequenceProfile)}:{})}:undefined;
        return JSON.stringify(b.R.data)+(savedConfig?'\0'+JSON.stringify(savedConfig):'')+(b.R.augmentVersion===2&&b.combatRulesEpoch===CURRENT_COMBAT_RULES_EPOCH&&b.innateDamageVersion===1?'\0innateDamageVersion:1':'');
      }catch{return null;}
    },
    restore(json:string):Battle {
      const state=JSON.parse(json);
      // 배포 이전 V1 DB 레코드에는 snapshot이 없다. 새 V2 규칙에 재해석하지 않는다.
      const snapshot=state.serviceRules;
      if(!snapshot){if(state.effectsVersion===2)throw new Error('V2 저장 전투의 규칙 스냅샷이 없어요');return deserializeBattle(legacy,json);}
      const restored=rulesFrom(snapshot);
      if(restored.augmentVersion===2){
        const checked=createAugmentConfigV2(state.augmentOffers?.weights,parseAugmentCatalogV2(snapshot.augmentCatalog));
        if(state.augmentOffers?.sequenceProfile){parseSequenceProfile(state.augmentOffers.sequenceProfile);if(!Array.isArray(state.augmentOffers.sequence)||state.augmentOffers.sequence.length!==6||state.augmentOffers.sequence.some((t:unknown)=>!['silver','gold','prismatic'].includes(t as string))||!Number.isInteger(state.augmentOffers.roundIndex)||state.augmentOffers.roundIndex<0||state.augmentOffers.roundIndex>6)throw new Error('저장 증강 순서 오류');}
        if(state.augmentOffers?.version!==2||JSON.stringify(checked.catalog)!==JSON.stringify(state.augmentOffers.catalog))throw new Error('저장 증강 배정 설정이 규칙과 달라요');
      } else if(state.augmentOffers?.version===2)throw new Error('V1 저장 전투에 V2 배정 설정이 있어요');
      delete state.serviceRules;
      return deserializeBattle(restored,JSON.stringify(state));
    },
  };
}
// DB 내부 저장용. 클라이언트 응답에 사용하지 않는다.
export function serializeServiceBattle(b:Battle):string {
  return JSON.stringify({...JSON.parse(serializeBattle(b)),serviceRules:structuredClone(b.R.data)});
}
export function supportsAugmentVersion(version:1|2,header:unknown):boolean {return version===1||header==='2';}
// 새 전투만 현재 설정을 따르고, 진행 전투는 저장된 버전을 따른다.
// 취소·항복·성장·순위 조회에는 업데이트를 강제하지 않는다.
export function requiredAugmentClientVersion(path:string,newVersion:1|2,existingVersion:1|2=1,leave=false):1|2 {
  if(path==='battle/turn'&&leave)return 1;
  if(['queue/attempt','queue/join','battle/start'].includes(path))return newVersion;
  if(['room/current','room/command','battle/current','battle/turn'].includes(path))return existingVersion;
  return 1;
}


// UI 호환성 선언일 뿐 인증이 아니다. 소유권/phase/입력 검증은 별도로 수행한다.
export function supportsCombatEpoch(path:string,newEpoch:string|undefined,existingEpoch:string|undefined,header:unknown,leave=false):boolean {
  if(path==='battle/turn'&&leave)return true;
  const epoch=['queue/attempt','queue/join','battle/start'].includes(path)?newEpoch:['room/current','room/command','battle/current','battle/turn'].includes(path)?existingEpoch:undefined;
  return !epoch||header===epoch;
}
export const combatUpgradeRequired=()=>({error:'새 전투 규칙을 사용하려면 앱을 업데이트해 주세요.',code:'COMBAT_EPOCH_UNSUPPORTED',upgradeRequired:true,combatRulesEpoch:CURRENT_COMBAT_RULES_EPOCH});
