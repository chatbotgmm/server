import {describe,expect,it} from 'vitest';
import {rankLevelEligibility,rankUnitEligibility,readRankAdmission,readRoster60MatchPool,readRoster60RankPool,roster60MatchPoolPolicyVersion,roster60RankPolicyVersion} from '../src/progression/roster60-rank.js';

describe('랭크의 계정 60·편성 개체 60 명시 경계',()=>{
 const units=(...levels:number[])=>levels.map((level,index)=>({ownedId:'owned-'+index,level}));

 it('계정과 편성 개체 제한을 함께 명시하며 전투 정규화나 새 성장 곡선으로 해석하지 않는다',()=>{
  expect(roster60RankPolicyVersion).toBe('account60-party60-history-rank-v1');
  expect(rankLevelEligibility(60,units(60))).toEqual({eligible:true,reason:''});
  expect(rankLevelEligibility(99,units(60,99))).toEqual({eligible:true,reason:''});
 });

 it('계정 59는 모든 개체가 높아도 입장하지 못하고 계정 60 경계부터 통과한다',()=>{
  expect(rankLevelEligibility(59,units(60,99))).toEqual({eligible:false,reason:'RANK_ACCOUNT_LEVEL_REQUIRED'});
  expect(rankLevelEligibility(60,units(60,99))).toEqual({eligible:true,reason:''});
 });

 it('아직 정의되지 않은 계정 레벨을 최고 개체·편성 평균으로 대신하지 않는다',()=>{
  expect(rankLevelEligibility(null,units(99,99,99))).toEqual({eligible:false,reason:'ACCOUNT_LEVEL_POLICY_PENDING'});
  for(const value of [NaN,Infinity,-1,1.5])expect(rankLevelEligibility(value,units(60)),String(value)).toEqual({eligible:false,reason:'ACCOUNT_LEVEL_DATA_INVALID'});
 });

 it('편성 중 한 개체라도 60 미만이면 계정 60 이상이어도 거절한다',()=>{
  for(const levels of [[59],[60,59],[60,60,60,60,60,59]])expect(rankLevelEligibility(60,units(...levels)),levels.join(',')).toEqual({eligible:false,reason:'RANK_UNIT_LEVEL_REQUIRED'});
  expect(rankLevelEligibility(60,units(60,60,60,60,60,60))).toEqual({eligible:true,reason:''});
 });

 it('환생 직후 1은 미달이며 다시 60에 도달해야 개체 제한을 통과한다',()=>{
  expect(rankUnitEligibility(1)).toEqual({rankEligible:false,rankReason:'RANK_UNIT_LEVEL_REQUIRED'});
  expect(rankUnitEligibility(59)).toEqual({rankEligible:false,rankReason:'RANK_UNIT_LEVEL_REQUIRED'});
  expect(rankUnitEligibility(60)).toEqual({rankEligible:true,rankReason:''});
  expect(rankUnitEligibility(99)).toEqual({rankEligible:true,rankReason:''});
 });

 it('편성을 고르기 전 옵션의 계정 자격은 별도로 읽되 빈 편성 입장을 허용하는 판정으로 쓰지 않는다',()=>{
  expect(rankLevelEligibility(60,[])).toEqual({eligible:true,reason:''});
  expect(rankLevelEligibility(59,[])).toEqual({eligible:false,reason:'RANK_ACCOUNT_LEVEL_REQUIRED'});
  expect(rankLevelEligibility(null,[])).toEqual({eligible:false,reason:'ACCOUNT_LEVEL_POLICY_PENDING'});
 });
});

describe('랭크 환생 풀의 영구 근거와 서버 입장 고정값',()=>{
 function history(input:{successes?:number;receipt?:unknown;legacy?:{fingerprint:string;result:string};audit?:boolean;r60History?:boolean;legacyHistory?:boolean}={}){
  return {
   roster60RebirthAccount:{findUnique:async()=>input.successes===undefined?null:{successes:input.successes}},
   roster60Owned:{findFirst:async()=>input.r60History?{id:'consumed-roster60-history'}:null},
   ownedCharacter:{findFirst:async()=>input.legacyHistory?{id:17}:null},
   roster60RebirthReceipt:{findMany:async()=>input.receipt===undefined?[]:[{result:typeof input.receipt==='string'?input.receipt:JSON.stringify(input.receipt)}]},
   progressionReceipt:{findMany:async()=>input.legacy?[input.legacy]:[]},
   auditEvent:{findFirst:async()=>input.audit?{id:'server-audit'}:null},
  } as any;
 }

 it('계정 성공 누적은 현재 편성이나 남은 환생 개체를 조회하지 않아도 환생 풀을 유지한다',async()=>{
  expect(await readRoster60RankPool(history({successes:1}),'owner')).toBe('REBORN');
  expect(await readRoster60RankPool(history({successes:5}),'owner')).toBe('REBORN');
  expect(await readRoster60RankPool(history({successes:0}),'owner')).toBe('FIRST_LIFE');
  expect(await readRoster60RankPool(history(),'owner')).toBe('FIRST_LIFE');
 });

 it('완료한 60종 영수증과 기존 환생 성공 영수증·서버 감사도 영구 근거로 인정한다',async()=>{
  expect(await readRoster60RankPool(history({receipt:{completed:true,preview:{next:{reincarnation:1}}}}),'owner')).toBe('REBORN');
  expect(await readRoster60RankPool(history({legacy:{fingerprint:JSON.stringify({action:'reincarnate',payload:{value:'o1'}}),result:JSON.stringify({ok:true,reincarnation:2})}}),'owner')).toBe('REBORN');
  expect(await readRoster60RankPool(history({audit:true}),'owner')).toBe('REBORN');
 });

 it('성공 영수증보다 오래된 계정 전체 개체 이력도 편성·현재 보유 상태와 독립된 환생 근거다',async()=>{
  expect(await readRoster60RankPool(history({r60History:true}),'owner')).toBe('REBORN');
  expect(await readRoster60RankPool(history({legacyHistory:true}),'owner')).toBe('REBORN');
 });

 it('일반게임과 랭크는 동일한 영구 이력 판정을 공유하고 계정 레벨 게이트와 혼동하지 않는다',async()=>{
  expect(roster60MatchPoolPolicyVersion).toBe('account-rebirth-history-count-normal-rank-v2');
  expect(await readRoster60MatchPool(history(),'owner')).toBe('FIRST_LIFE');
  expect(await readRoster60MatchPool(history({r60History:true}),'owner')).toBe('REBORN');
  expect(await readRoster60MatchPool(history({legacyHistory:true}),'owner')).toBe('REBORN');
  expect(await readRoster60MatchPool(history({receipt:'bad-json'}),'owner')).toBeNull();
 });

 it('성공 여부가 불명확한 기록은 비환생으로 숨기지 않고 분류 보류로 남긴다',async()=>{
  for(const input of [{successes:-1},{receipt:'broken-json'},{receipt:{preview:{next:{reincarnation:1}}}},{legacy:{fingerprint:'bad-json',result:'bad-json'}}])expect(await readRoster60RankPool(history(input),'owner'),JSON.stringify(input)).toBeNull();
  expect(await readRoster60RankPool(history({legacy:{fingerprint:JSON.stringify({action:'reroll',payload:{value:'reincarnate'}}),result:'{}'}}),'owner')).toBe('FIRST_LIFE');
 });

 it('명시적으로 실패한 환생 영수증은 성공 이력이 아니며 비환생 계정을 미확정으로 바꾸지 않는다',async()=>{
  expect(await readRoster60RankPool(history({receipt:{completed:false,preview:{next:{reincarnation:1}}}}),'owner')).toBe('FIRST_LIFE');
  expect(await readRoster60RankPool(history({legacy:{fingerprint:JSON.stringify({action:'reincarnate',payload:{value:'o1'}}),result:JSON.stringify({ok:false,reincarnation:1})}}),'owner')).toBe('FIRST_LIFE');
 });

 it('이전 큐의 누락·손상·중복 개체·계정 미달 고정값은 새 랭크 입장 증명으로 사용하지 않는다',()=>{
  const valid={playerId:'owner',pool:'REBORN',accountLevel:60,ownedIds:['a','b'],catalogVersion:'test-v1'};
  expect(readRankAdmission(JSON.stringify(valid))).toEqual(valid);
  for(const raw of [null,undefined,'{bad-json',JSON.stringify({...valid,pool:'UNKNOWN'}),JSON.stringify({...valid,accountLevel:59}),JSON.stringify({...valid,ownedIds:['a','a']}),JSON.stringify({...valid,ownedIds:[]})])expect(readRankAdmission(raw),String(raw)).toBeNull();
 });
});
