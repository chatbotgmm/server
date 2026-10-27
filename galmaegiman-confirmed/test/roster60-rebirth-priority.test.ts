import {describe,expect,it} from 'vitest';
import {orderRebirthCandidates,readRoster60RebirthHistory,roster60MatchPoolPolicyVersion,validRebirthCountPin} from '../src/progression/roster60-rank.js';

function history(input:{successes?:number;r60?:number;legacy?:boolean;audit?:string;receipt?:unknown;legacyReceipt?:unknown}={}){
 return {
  roster60RebirthAccount:{findUnique:async()=>input.successes===undefined?null:{successes:input.successes}},
  roster60Owned:{findFirst:async()=>input.r60?{id:'history-owned',reincarnation:input.r60}:null},
  ownedCharacter:{findFirst:async()=>input.legacy?{id:3,reincarnation:7,obtainedVia:'REINCARNATION'}:null},
  auditEvent:{findFirst:async()=>input.audit?{id:input.audit}:null},
  roster60RebirthReceipt:{findMany:async()=>input.receipt===undefined?[]:[{result:typeof input.receipt==='string'?input.receipt:JSON.stringify(input.receipt)}]},
  progressionReceipt:{findMany:async()=>input.legacyReceipt===undefined?[]:[{fingerprint:JSON.stringify({action:'reincarnate'}),result:JSON.stringify(input.legacyReceipt)}]},
 } as any;
}
const candidate=(playerId:string,count:number|null,rating=1000,time=0)=>({playerId,reincarnationCount:count,rating,queuedAt:new Date(time)});
const ids=(rows:ReturnType<typeof candidate>[])=>rows.map(r=>r.playerId);

describe('계정 평생 환생 횟수의 근거와 미확정 보존',()=>{
 it('성공 계정 누적만 횟수로 쓰며 개체의 강화·환생 값을 합치지 않는다',async()=>{
  const value=await readRoster60RebirthHistory(history({successes:4,r60:2}),'owner');
  expect(value).toMatchObject({pool:'REBORN',count:4,countSource:'ROSTER60_ACCOUNT_SUCCESSES'});expect(value.historyStamp).toMatch(/^[a-f0-9]{64}$/);
  expect(await readRoster60RebirthHistory(history({r60:5}),'owner')).toMatchObject({pool:'REBORN',count:null,countSource:'HISTORY_WITHOUT_ACCOUNT_COUNT'});
 });
 it('레거시와 R60 계정 누적이 섞이면 평생 횟수를 추측하거나 합산하지 않는다',async()=>{
  for(const input of [{successes:4,legacy:true},{successes:4,audit:'event'},{successes:4,legacyReceipt:{ok:true,reincarnation:9}}])expect(await readRoster60RebirthHistory(history(input),'owner')).toMatchObject({pool:'REBORN',count:null,countSource:'MIXED_LEGACY_HISTORY'});
  expect(await readRoster60RebirthHistory(history({legacy:true}),'owner')).toMatchObject({pool:'REBORN',count:null});
 });
 it('환생 근거가 없을 때만 0이며 손상된 기록은 0으로 보완하지 않는다',async()=>{
  expect(await readRoster60RebirthHistory(history(),'owner')).toMatchObject({pool:'FIRST_LIFE',count:0,countSource:'NO_REBIRTH_HISTORY'});
  expect(await readRoster60RebirthHistory(history({successes:-1}),'owner')).toMatchObject({pool:null,count:null,countSource:'INVALID_OR_INCOMPLETE_HISTORY'});
  expect(await readRoster60RebirthHistory(history({successes:4,receipt:'broken'}),'owner')).toMatchObject({pool:'REBORN',count:null,countSource:'INVALID_OR_INCOMPLETE_HISTORY'});
 });
 it('명시적인 실패 영수증은 횟수·풀·성공 이력 지문을 바꾸지 않는다',async()=>{
  const base=await readRoster60RebirthHistory(history(),'owner');
  expect(await readRoster60RebirthHistory(history({receipt:{completed:false}}),'owner')).toEqual(base);
  expect(await readRoster60RebirthHistory(history({legacyReceipt:{ok:false}}),'owner')).toEqual(base);
 });
 it('혼재 계정의 미상 횟수가 그대로여도 새 R60 성공은 지문 변경으로 감지한다',async()=>{
  const before=await readRoster60RebirthHistory(history({successes:3,legacy:true}),'owner'),after=await readRoster60RebirthHistory(history({successes:4,legacy:true}),'owner');
  expect([before.count,after.count]).toEqual([null,null]);expect(before.historyStamp).not.toBe(after.historyStamp);
 });
 it('개정 큐 정책은 known과 honest unknown을 구별하고 잘못된 count pin을 거절한다',()=>{
  expect(roster60MatchPoolPolicyVersion).toBe('account-rebirth-history-count-normal-rank-v2');
  expect(validRebirthCountPin('FIRST_LIFE',0,'NO_REBIRTH_HISTORY')).toBe(true);
  expect(validRebirthCountPin('REBORN',4,'ROSTER60_ACCOUNT_SUCCESSES')).toBe(true);
  expect(validRebirthCountPin('REBORN',null,'MIXED_LEGACY_HISTORY')).toBe(true);
  for(const [pool,count,source] of [['REBORN',0,'ROSTER60_ACCOUNT_SUCCESSES'],['REBORN',0,'MIXED_LEGACY_HISTORY'],['FIRST_LIFE',null,'NO_REBIRTH_HISTORY'],['REBORN',4,null]] as const)expect(validRebirthCountPin(pool,count,source)).toBe(false);
 });
});

describe('같은 풀 안 환생 횟수 가까운 후보 우선과 기존 매칭 순서',()=>{
 it('횟수 거리가 MMR 근접보다 먼저이며 동일 횟수만 요구하지 않는다',()=>{
  const a={rating:1000,reincarnationCount:4},rows=[candidate('far-count',10,1000),candidate('near-count',3,1080)];
  expect(ids(orderRebirthCandidates(a,rows))).toEqual(['near-count','far-count']);
  expect(orderRebirthCandidates(a,[candidate('different-but-valid',8)])[0].playerId).toBe('different-but-valid');
 });
 it('동일 거리에서는 MMR·대기시각·ID 순서로 안정적으로 고른다',()=>{
  const a={rating:1000,reincarnationCount:4},rows=[candidate('later',3,1010,20),candidate('z',5,990,10),candidate('a',3,1010,10),candidate('closer-mmr',5,1005,30)];
  expect(ids(orderRebirthCandidates(a,rows))).toEqual(['closer-mmr','a','z','later']);
  expect(ids(orderRebirthCandidates(a,[...rows].reverse()))).toEqual(['closer-mmr','a','z','later']);
 });
 it('미상 횟수의 선택 차례는 기존 MMR·대기·ID를 쓰고 0으로 간주하지 않는다',()=>{
  const a={rating:1000,reincarnationCount:null},rows=[candidate('known-near-zero',1,1090),candidate('unknown',null,1000),candidate('known-huge',99,1010)];
  expect(ids(orderRebirthCandidates(a,rows))).toEqual(['unknown','known-huge','known-near-zero']);
 });
 it('known 후보가 없어도 unknown 후보와 매칭할 수 있어 대기를 무한 차단하지 않는다',()=>{
  const a={rating:1000,reincarnationCount:4};expect(orderRebirthCandidates(a,[candidate('unknown',null)])[0].playerId).toBe('unknown');
  expect(orderRebirthCandidates(a,[])).toEqual([]);
 });
});
