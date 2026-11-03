import {describe,it,expect} from 'vitest';
import {finisherSplitShares} from '../src/progression/finisher-split.js';
import {normalXpShares,validateNormalXpPolicy,type NormalXpMember} from '../src/progression/normal-xp.js';
import {readFileSync} from 'node:fs';
import {pacedUnitWin,validateUnitXpPacing,type UnitXpPacing} from '../src/progression/unit-xp-pacing.js';
import {instanceGrowthCurve} from '../src/battle/roster60-rules.js';

const member=(id:string,level=10,winXp=100):NormalXpMember=>({id,playerId:'p1',startingXp:'0',growth:[],reincarnation:0,sourceEncounterId:'DUEL_'+level,winXp,lossXp:0,lossFallbackUsed:false,level});

describe('마무리 전액·편성 나머지 절반 분배',()=>{
 it('마무리 유닛은 W, 나머지는 W/2(내림)를 받고 편성 순서와 무관하다',()=>{
  expect(Object.fromEntries(finisherSplitShares(1695,['a','b','c'],'b'))).toEqual({a:847,b:1695,c:847});
  expect(Object.fromEntries(finisherSplitShares(1695,['c','b','a'],'b'))).toEqual({a:847,b:1695,c:847});
  expect(Object.fromEntries(finisherSplitShares(1,['a','b'],'a'))).toEqual({a:1,b:0});
  expect(Object.fromEntries(finisherSplitShares(100,['solo'],'solo'))).toEqual({solo:100});
 });
 it('마무리 유닛이 편성에 없거나 편성이 잘못되면 지급하지 않고 거절한다',()=>{
  expect(()=>finisherSplitShares(100,['a','b'],'z')).toThrow();
  expect(()=>finisherSplitShares(100,['a','a'],'a')).toThrow();
  expect(()=>finisherSplitShares(-1,['a'],'a')).toThrow();
  expect(()=>finisherSplitShares(100,[],'a')).toThrow();
 });
 it('일반전 v3: 이긴 편은 마무리 전액·미출전 예비 포함 나머지 절반, 진 편은 0',()=>{
  const members=[member('o1',10),member('o2',12,140),member('o3',8)];
  // 기본량은 실제 출전 개체 중 최고 시작 레벨의 원천(o2: 140). o3은 출전하지 않았다.
  const win=normalXpShares(members,['o1','o2'],'win',undefined,{finisherId:'o1'});
  expect(win.baseXp).toBe(140);expect(Object.fromEntries(win.shares)).toEqual({o1:140,o2:70,o3:70});expect(win.poolXp).toBe(280);
  const loss=normalXpShares(members,['o1','o2'],'loss',undefined,{finisherId:undefined});
  expect(loss.poolXp).toBe(0);expect(loss.shares.size).toBe(0);
  expect(()=>normalXpShares(members,['o1'],'win',undefined,{finisherId:undefined})).toThrow();
 });
 it('일반전 v2(이미 고정된 방)는 예전처럼 출전 개체 균등·패배 50%로 정산한다',()=>{
  const members=[member('o1',10),member('o2',12,140),member('o3',8)];
  expect(Object.fromEntries(normalXpShares(members,['o1','o2'],'win').shares)).toEqual({o1:70,o2:70});
  expect(Object.fromEntries(normalXpShares(members,['o1','o2'],'loss').shares)).toEqual({o1:35,o2:35});
 });
 it('배포할 일반전 정책 파일은 v3이고 검증을 통과한다',()=>{
  const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
  expect(policy).toMatchObject({rulesVersion:'normal-xp-v3',distribution:'finisher-full-rest-half',participation:'all-formed-owned',outcomePercent:{win:100,loss:0}});
  expect(()=>validateNormalXpPolicy(policy,'five-stat-device-test-v1')).not.toThrow();
  expect(()=>validateNormalXpPolicy({...policy,outcomePercent:{win:100,loss:50}},'five-stat-device-test-v1')).toThrow();
 });
 it('일반전 v3 + 1.5승 최소: 마무리 개체 W=max(원천, 적정 레벨 최소), 나머지 절반', ()=>{
  const P='data/test-profiles/five-stat-device-test-v1/',catalog=JSON.parse(readFileSync(P+'catalog.json','utf8')),pacing=JSON.parse(readFileSync(P+'unit-xp-pacing-policy.json','utf8')) as UnitXpPacing;
  expect(pacing).toMatchObject({winsPerLevel:1.5,distribution:'finisher-full-rest-half'});expect(()=>validateUnitXpPacing(pacing,catalog.version)).not.toThrow();
  // 예전 판 조합(2승·균등)과 새 판 조합(1.5승·마무리)만 허용한다.
  expect(()=>validateUnitXpPacing({...pacing,winsPerLevel:2},catalog.version)).toThrow();expect(()=>validateUnitXpPacing({...pacing,winsPerLevel:1},catalog.version)).toThrow();
  const def=catalog.characters.find((u:any)=>u.id==='u01'),curve=instanceGrowthCurve(def.growth,0,def.reincarnationXpPolicy);
  const at=(id:string,level:number,winXp:number):NormalXpMember=>({id,playerId:'p1',startingXp:String(curve[level-1].totalXp),growth:def.growth,reincarnation:0,reincarnationXpPolicy:def.reincarnationXpPolicy,sourceEncounterId:'training-'+level,winXp,lossXp:0,lossFallbackUsed:false,level,nearestTierMinimum:level});
  const need=(level:number)=>curve[level].totalXp-curve[level-1].totalXp;
  // Lv40: 원천 2400 < 1.5승 최소 ceil(need×2/3) → 최소로 올린다.
  const high=[at('a',40,2400),at('b',40,2400)],min40=Math.ceil(need(40)*2/3);expect(pacedUnitWin(high[0],40,pacing,40)).toBe(min40);expect(min40).toBeGreaterThan(2400);
  expect(Object.fromEntries(normalXpShares(high,['a'],'win',{policy:pacing,enemyLevel:40},{finisherId:'a'}).shares)).toEqual({a:min40,b:Math.floor(min40/2)});
  // Lv10: 원천 600 > 최소 → 원천 유지.
  const low=[at('a',10,600),at('b',10,600)];expect(Math.ceil(need(10)*2/3)).toBeLessThan(600);
  expect(Object.fromEntries(normalXpShares(low,['a','b'],'win',{policy:pacing,enemyLevel:10},{finisherId:'b'}).shares)).toEqual({a:300,b:600});
  expect(normalXpShares(low,['a','b'],'loss',{policy:pacing,enemyLevel:10},{finisherId:undefined}).shares.size).toBe(0);
 });
});
