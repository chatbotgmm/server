import {readFileSync} from 'node:fs';
import {describe,expect,it} from 'vitest';
import {growthAt,instanceGrowthCurve,type GrowthPoint} from '../src/battle/roster60-rules.js';
import type {BattlePolicy,State} from '../src/roster60-battle.js';
import {normalXpGrant,normalXpShares,normalXpSource,type NormalXpCurrentPolicy,type NormalXpMember} from '../src/progression/normal-xp.js';
import {calculatePacedBattleXp,createPacedBattleXpPlan,pacedBattleXpPlan,pacedPool,pacedUnitWin,validateUnitXpPacing,type PacedBattleXpPlan,type PacingMember,type UnitXpPacing} from '../src/progression/unit-xp-pacing.js';

const read=(name:string)=>JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/'+name,'utf8'));
const catalog=read('catalog.json');
const growth:GrowthPoint[]=catalog.characters.find((unit:any)=>unit.id==='u01').growth;
const battle=read('battle-policy.json') as BattlePolicy;
const normal=JSON.parse(readFileSync('test/fixtures/normal-xp-policy-v2.json','utf8')) as NormalXpCurrentPolicy;
// 「승리 2번에 1레벨·출전 개체 균등」 예전 판을 검증한다(이미 고정된 전투는 이 판으로 정산). 새 배포 판은 test/finisher-split.test.ts에서 검증한다.
const policy=JSON.parse(readFileSync('test/fixtures/unit-xp-pacing-policy-two-wins.json','utf8')) as UnitXpPacing;
const owner='pacing-owner';
const row={id:'pacing-battle',playerId:owner,ownedId:'owned-a'};
function owned(ownedId:string,level:number,reincarnation=0):PacingMember{
 const curve=instanceGrowthCurve(growth,reincarnation);
 return {ownedId,playerId:owner,startingXp:String(curve[level-1].totalXp),growth:structuredClone(growth),reincarnation};
}
function normalMember(id:string,level:number,reincarnation=0):NormalXpMember{
 const member=owned(id,level,reincarnation),source=normalXpSource(battle,level,normal);
 return {...member,id,level,...source,nearestTierMinimum:battle.encounters.find(e=>e.id===source.sourceEncounterId)!.level};
}
function state(outcome:'win'|'loss'|'escaped'|'draw',enteredIds:string[],surrendered=false):State{
 return {outcome,surrendered,participation:enteredIds.map(ownedId=>({ownedId,kind:'start',turn:0})),events:[],fighters:[{hp:outcome==='loss'?0:100}],reserves:[]} as unknown as State;
}
function plan(members:PacingMember[],extra:Partial<Omit<PacedBattleXpPlan,'version'|'members'>>={}):PacedBattleXpPlan{
 return createPacedBattleXpPlan({battleId:row.id,playerId:owner,policy:structuredClone(policy),enemyLevel:40,sourceWinXp:0,sourceLossXp:0,baseline:'total',reserveHalf:false,members,...extra});
}

describe('unit XP pacing from pinned growth and actual participation',()=>{
 it.each([10,15,20,24,40,59].flatMap(level=>[1,2,3].map(count=>({level,count}))))('fresh Lv$level with $count entered units reaches the next level in two wins, through both normal shares and battle plans',({level,count})=>{
  let members=Array.from({length:count},(_,index)=>normalMember('owned-'+String.fromCharCode(97+index),level));
  let battleMembers=members.map(m=>owned(m.id,level));
  const ids=members.map(m=>m.id),tier=members[0].nearestTierMinimum!;
  for(let wins=1;wins<=2;wins++){
   const normalBefore=structuredClone(members),split=normalXpShares(members,[...ids,...ids].reverse(),'win',{policy,enemyLevel:tier});
   expect([...split.shares.keys()]).toEqual(ids);
   expect([...split.shares.values()].reduce((sum,xp)=>sum+xp,0)).toBe(split.poolXp);
   members=members.map(member=>({...member,startingXp:normalXpGrant(member,split.shares.get(member.id)!).endingXp}));
   expect(normalBefore.map(m=>m.startingXp)).not.toEqual(members.map(m=>m.startingXp));
   expect(members.map(m=>growthAt(Number(m.startingXp),m.growth,m.reincarnation).level)).toEqual(ids.map(()=>level+(wins===2?1:0)));
   const pinned=plan(battleMembers,{enemyLevel:level}),before=structuredClone(pinned),result=calculatePacedBattleXp(pinned,row,state('win',ids));
   expect(pinned).toEqual(before);
   battleMembers=battleMembers.map(member=>({...member,startingXp:result.units.find(unit=>unit.ownedId===member.ownedId)!.endingXp}));
   expect(battleMembers.map(m=>growthAt(Number(m.startingXp),m.growth,m.reincarnation).level)).toEqual(ids.map(()=>level+(wins===2?1:0)));
  }
 });

 it('uses the highest eligible floor for a mixed party, splits equally and ignores an unentered higher-level reserve',()=>{
  const members=[normalMember('owned-a',15),normalMember('owned-b',20),normalMember('owned-c',40),normalMember('reserve',59)];
  const before=structuredClone(members),result=normalXpShares(members,['owned-c','owned-b','owned-a','owned-a'],'win',{policy,enemyLevel:40});
  expect(result).toMatchObject({baseXp:7590,poolXp:7590,sourceEncounterId:'training-40'});
  expect([...result.shares]).toEqual([['owned-a',2530],['owned-b',2530],['owned-c',2530]]);
  expect(result.shares.has('reserve')).toBe(false);expect(members).toEqual(before);
 });

 it('treats the existing nearest Lv10 normal tier as appropriate for Lv15, while a genuinely lower opponent stays capped',()=>{
  const members=['owned-a','owned-b','owned-c'].map(id=>normalMember(id,15));
  expect(pacedUnitWin(members[0],10,policy,10)).toBe(581);
  expect(pacedUnitWin(members[0],10,policy)).toBe(316);
  const expectedLower=Math.ceil((growth[9].totalXp-growth[8].totalXp)/2);
  expect(pacedUnitWin(members[0],9,policy,10)).toBe(expectedLower);
  expect(normalXpShares(members,members.map(m=>m.id),'win',{policy,enemyLevel:10}).poolXp).toBe(1743);
  expect(normalXpShares(members,members.map(m=>m.id),'win',{policy,enemyLevel:9}).poolXp).toBe(expectedLower*3);
 });

 it('does not apply the owned Lv40 floor against a Lv1 enemy or reduce a higher pre-existing reward',()=>{
  const high=owned('owned-a',40),before=structuredClone(high);
  expect(pacedUnitWin(high,1,policy)).toBe(10);
  expect(pacedPool([high],1,0,policy)).toBe(10);
  expect(pacedPool([high],1,2400,policy)).toBe(2400);
  expect(pacedUnitWin(high,40,policy)).toBe(2530);
  expect(high).toEqual(before);
  const low=owned('owned-a',1);expect(pacedPool([low,owned('owned-b',1),owned('owned-c',1)],1,60,policy)).toBe(60);
 });

 it('keeps normal loss at one half, direct practice at its existing one third, and story/adventure loss at zero',()=>{
  const members=['owned-a','owned-b','owned-c'].map(id=>normalMember(id,24)),ids=members.map(m=>m.id);
  const win=normalXpShares(members,ids,'win',{policy,enemyLevel:24}),loss=normalXpShares(members,ids,'loss',{policy,enemyLevel:24});
  expect(win.poolXp).toBe(3528);expect(loss.poolXp).toBe(1764);expect([...loss.shares.values()]).toEqual([588,588,588]);
  const direct=plan([owned('owned-a',24)],{enemyLevel:24,sourceWinXp:600,sourceLossXp:200});
  expect(calculatePacedBattleXp(direct,row,state('win',['owned-a'])).requestedXp).toBe(1176);
  expect(calculatePacedBattleXp(direct,row,state('loss',['owned-a'])).requestedXp).toBe(392);
  for(const sourceWinXp of [0,600]){
   const story=plan([owned('owned-a',24)],{enemyLevel:24,sourceWinXp,sourceLossXp:0});
   expect(calculatePacedBattleXp(story,row,state('win',['owned-a'])).requestedXp).toBe(1176);
   expect(calculatePacedBattleXp(story,row,state('loss',['owned-a']))).toMatchObject({requestedXp:0,xpAccepted:0,xpDiscarded:0,xpDeferred:0});
  }
 });

 it('keeps escape, surrender and draw at zero, including ordinary-adventure reserves',()=>{
  const pinned=plan([owned('owned-a',40),owned('reserve',40)],{sourceWinXp:600,sourceLossXp:200,reserveHalf:true,baseline:'per-entered'});
  for(const terminal of [state('escaped',['owned-a']),state('win',['owned-a'],true),state('loss',['owned-a'],true),state('draw',['owned-a'])]){
   const reward=calculatePacedBattleXp(pinned,row,terminal);
   expect(reward).toMatchObject({requestedXp:0,xpAccepted:0,xpDiscarded:0,xpDeferred:0});expect(reward.xpByOwnedId).toEqual({'owned-a':0,reserve:0});
  }
 });

 it('pays only entered units in a quest battle and preserves only the old half-source reward for ordinary adventure reserves',()=>{
  const members=[owned('owned-a',40),owned('owned-b',40),owned('reserve',59)],battleState=state('win',['owned-a','owned-b']);
  const quest=calculatePacedBattleXp(plan(members,{sourceWinXp:600}),row,battleState);
  expect(quest.requestedXpByOwnedId).toEqual({'owned-a':2530,'owned-b':2530,reserve:0});
  expect(quest.units.find(unit=>unit.ownedId==='reserve')?.status).toBe('NOT_ENTERED');
  const ordinary=calculatePacedBattleXp(plan(members,{sourceWinXp:600,baseline:'per-entered',reserveHalf:true}),row,battleState);
  expect(ordinary.requestedXpByOwnedId).toEqual({'owned-a':2530,'owned-b':2530,reserve:300});
  expect(ordinary.requestedXp).toBe(5360);expect(ordinary.xpAccepted).toBe(5360);
 });

 it.each([0,1])('clamps to the real cap in cycle %i, discards overflow and never changes the reincarnation state',reincarnation=>{
  const maximum=reincarnation?99:60,curve=instanceGrowthCurve(growth,reincarnation),cap=curve[maximum-1].totalXp;
  const member={...owned('owned-a',maximum-1,reincarnation),startingXp:String(cap-1)},pinned=plan([member],{enemyLevel:maximum-1}),before=structuredClone(pinned);
  const reward=calculatePacedBattleXp(pinned,row,state('win',['owned-a']));
  expect(reward.xpAccepted).toBe(1);expect(reward.xpDeferred).toBe(0);expect(reward.xpDiscarded).toBe(reward.requestedXp-1);
  expect(reward.units[0]).toMatchObject({endingXp:String(cap),appliedXp:1,status:'CAP_CLAMPED'});
  expect(growthAt(Number(reward.units[0].endingXp),growth,reincarnation).level).toBe(maximum);expect(pinned).toEqual(before);expect(pinned.members[0].reincarnation).toBe(reincarnation);
  const capped=plan([{...member,startingXp:String(cap)}],{enemyLevel:maximum-1,sourceWinXp:600});
  expect(calculatePacedBattleXp(capped,row,state('win',['owned-a']))).toMatchObject({requestedXp:600,xpAccepted:0,xpDiscarded:600,xpDeferred:0});
 });

 it('does not redistribute a capped participant share to its peer or use it after manual XP reset',()=>{
  const capped=owned('owned-a',60),growing=owned('owned-b',40),pinned=plan([capped,growing]),before=structuredClone(pinned);
  const reward=calculatePacedBattleXp(pinned,row,state('win',['owned-a','owned-b']));
  expect(reward.requestedXpByOwnedId).toEqual({'owned-a':2530,'owned-b':2530});expect(reward.xpByOwnedId).toEqual({'owned-a':0,'owned-b':2530});expect(reward.xpDiscarded).toBe(2530);
  const reborn=owned('owned-a',1,1);expect(pacedUnitWin(reborn,1,policy)).toBe(7);
  expect(reborn.startingXp).toBe('0');expect(reborn.reincarnation).toBe(1);expect(pinned).toEqual(before);
 });

 it('pins inputs, survives JSON reconnect without changing results, and leaves old snapshots without a plan untouched',()=>{
  const member=owned('owned-a',40),input={battleId:row.id,playerId:owner,policy:structuredClone(policy),enemyLevel:40,sourceWinXp:600,sourceLossXp:200,baseline:'total' as const,reserveHalf:false,members:[member]};
  const pinned=createPacedBattleXpPlan(input),first=calculatePacedBattleXp(pinned,row,state('win',['owned-a']));
  input.policy.version='future-policy';input.members[0].startingXp='0';input.sourceWinXp=1;
  expect(pinned.policy.version).toBe(policy.version);expect(pinned.sourceWinXp).toBe(600);
  const snap={policy:{catalogVersion:catalog.version},members:pinned.members.map(m=>({ownedId:m.ownedId,startingXp:m.startingXp})),unitXpPacing:JSON.parse(JSON.stringify(pinned))};
  const reconnected=pacedBattleXpPlan(row,snap)!;expect(calculatePacedBattleXp(reconnected,row,state('win',['owned-a']))).toEqual(first);
  expect(pacedBattleXpPlan(row,{})).toBeNull();expect(pacedBattleXpPlan(row,{unitXpPacing:null})).toBeNull();
  expect(()=>pacedBattleXpPlan(row,{...snap,members:[{ownedId:'owned-a',startingXp:'0'}]})).toThrow('UNIT_XP_PACING_SNAPSHOT_INVALID');
 });

 it('rejects strict-policy changes, forged party membership and malformed pinned plans',()=>{
  expect(validateUnitXpPacing(policy,catalog.version)).toEqual(policy);
  for(const changed of [null,[],{}, {...policy,extra:'client-amount'}, {...policy,approved:false},{...policy,version:''},{...policy,winsPerLevel:1},{...policy,difficulty:'unlimited'},{...policy,distribution:'all-owned'},{...policy,existingRewards:'replace'},{...policy,capOverflow:'bank'}])expect(()=>validateUnitXpPacing(changed,catalog.version)).toThrow('UNIT_XP_PACING_INVALID');
  expect(()=>validateUnitXpPacing(policy,'other-catalog')).toThrow('UNIT_XP_PACING_INVALID');
  for(const enemyLevel of [0,100,1.5,NaN])expect(()=>pacedUnitWin(owned('owned-a',10),enemyLevel,policy)).toThrow('UNIT_XP_PACING_INVALID');
  for(const invalid of [[],[owned('owned-a',10),owned('owned-a',10)],[{...owned('owned-a',10),playerId:'someone-else'}],[owned('AI:fake',10)]])expect(()=>plan(invalid)).toThrow('UNIT_XP_PACING_INVALID');
  expect(()=>plan([owned('owned-a',10)],{sourceWinXp:1,sourceLossXp:2})).toThrow('UNIT_XP_PACING_INVALID');
  expect(()=>calculatePacedBattleXp(plan([owned('owned-a',10)]),row,state('win',['owned-a','foreign']))).toThrow('UNIT_XP_PACING_PARTICIPATION_INVALID');
 });
});
