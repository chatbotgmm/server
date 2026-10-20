// 서버 신뢰 경계. HTTP 입력으로 이 설정을 만들지 않는다. 기본값은 항상 V1이다.
import {rulesFrom, type Rules} from './core.js';
import type {BattleData} from './rules.js';
import {createBattle, type Battle} from './engine.js';
import type {UnitRecord} from './growth.js';
import {augmentCatalogV2, parseAugmentCatalogV2, createAugmentConfigV2, type AugmentCatalogV2, type CatalogTier} from './augment-catalog-v2.js';
import {serializeBattle, deserializeBattle} from './realtime-adapter.js';
export type BattleServiceOptions = {version:1} | {version:2;weights:Record<CatalogTier,number>;catalog?:AugmentCatalogV2};
function legacyData(R:Rules):BattleData {
  const data=structuredClone(R.data);delete data.augmentCatalog;if(data.augmentVersion===2)data.augmentVersion=1;return data;
}
const canonical=(value:unknown):string=>Array.isArray(value)?'['+value.map(canonical).join(',')+']':value&&typeof value==='object'?'{'+Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>JSON.stringify(k)+':'+canonical(v)).join(',')+'}':JSON.stringify(value)??'null';
function baseFingerprint(R:Rules){const d=legacyData(R);delete d.augmentVersion;return canonical(d);}
export function createBattleService(base:Rules,options:BattleServiceOptions={version:1}) {
  if(options.version!==1&&options.version!==2)throw new Error('지원하지 않는 서비스 증강 버전');
  const legacy=rulesFrom(legacyData(base));
  const catalog=options.version===2?parseAugmentCatalogV2(options.catalog??augmentCatalogV2):undefined;
  const config=options.version===2?createAugmentConfigV2(options.weights,catalog):undefined;
  const R=catalog?rulesFrom({...legacyData(base),augmentVersion:2,augmentCatalog:catalog}):legacy;
  return {
    version:options.version, R, legacy,
    // V1 해시 입력은 그대로 유지한다. V2 가중치도 방 규칙 지문에 포함한다.
    fingerprint:JSON.stringify(R.data)+(config?'\0'+JSON.stringify(config):''),
    create(teamA:UnitRecord[],teamB:UnitRecord[],seed:number){return createBattle(R,teamA,teamB,seed,config);},
    restart(json:string,teamA:UnitRecord[],teamB:UnitRecord[],seed:number):Battle {
      const b=this.restore(json);
      const savedConfig=b.R.augmentVersion===2?createAugmentConfigV2(b.augmentOffers!.weights,b.R.data.augmentCatalog):undefined;
      return createBattle(b.R,teamA,teamB,seed,savedConfig);
    },
    compatibleFingerprint(json:string):string|null {
      try{const b=this.restore(json);if(baseFingerprint(b.R)!==baseFingerprint(legacy))return null;
        const savedConfig=b.R.augmentVersion===2?createAugmentConfigV2(b.augmentOffers!.weights,b.R.data.augmentCatalog):undefined;
        return JSON.stringify(b.R.data)+(savedConfig?'\0'+JSON.stringify(savedConfig):'');
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
