import {describe,expect,it} from 'vitest';
import {readFileSync} from 'node:fs';
import {unitXpProgress} from '../src/progression/xp-progress.js';
import {growthAt,instanceGrowthCurve} from '../src/battle/roster60-rules.js';
import {commanderXpProgress,commanderSigned64Maximum,validateCommanderPolicy} from '../src/progression/commander.js';
import {normalXpSource,settleNormalXp,type NormalXpMember,type NormalXpCurrentPolicy} from '../src/progression/normal-xp.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import type {BattlePolicy} from '../src/roster60-battle.js';

const read=(name:string)=>JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/'+name,'utf8'));
const catalog=read('catalog.json') as RosterCatalog,battle=read('battle-policy.json') as BattlePolicy,policy=JSON.parse(readFileSync('test/fixtures/normal-xp-policy-v2.json','utf8')) as NormalXpCurrentPolicy;

describe('server receipt XP progress is a read-only projection of pinned growth',()=>{
 it('actual per-instance thresholds agree just before and after every 60/99-level boundary',()=>{
  for(const id of ['u01','u22','u45'])for(const reincarnation of [0,1]){
   const definition=catalog.characters.find(unit=>unit.id===id)!,member={startingXp:'0',growth:definition.growth,reincarnation,reincarnationXpPolicy:definition.reincarnationXpPolicy};
   const curve=instanceGrowthCurve(member.growth,reincarnation,member.reincarnationXpPolicy),maxLevel=reincarnation?99:60;
   for(let index=1;index<maxLevel;index++){
    const boundary=curve[index].totalXp,start=String(boundary-1),end=String(boundary),progress=unitXpProgress(member,end,start)!;
    expect(progress.before).toEqual({level:index,totalXp:start,levelStartTotalXp:String(curve[index-1].totalXp),nextLevelTotalXp:end,maxLevel});
    expect(progress.after).toEqual({level:index+1,totalXp:end,levelStartTotalXp:end,nextLevelTotalXp:index+1<maxLevel?String(curve[index+1].totalXp):null,maxLevel});
    expect(progress.after.level).toBe(growthAt(boundary,member.growth,reincarnation,member.reincarnationXpPolicy).level);
   }
  }
 });
 it('missing legacy thresholds remain optional and invalid display inputs cannot change growth or grants',()=>{
  const member={startingXp:'0',growth:[{totalXp:0,maxMp:10}],reincarnation:0},before=structuredClone(member);
  for(const ending of ['0','-1','invalid','9007199254740992'])expect(unitXpProgress(member,ending)).toBeUndefined();
  expect(member).toEqual(before);
 });
 it('commander boundaries retain signed64 totals and last-known-level extension semantics',()=>{
  const config=validateCommanderPolicy(read('commander-policy.json'));
  const boundary=BigInt(config.growth[59].totalXp),progress=commanderXpProgress(boundary-1n,boundary,config);
  expect(progress.before.level).toBe(59);expect(progress.after.level).toBe(60);expect(progress.after.levelStartTotalXp).toBe(boundary.toString());
  const high=commanderXpProgress(BigInt(Number.MAX_SAFE_INTEGER)+1n,commanderSigned64Maximum,config);
  expect(high.before.totalXp).toBe('9007199254740992');expect(high.after.totalXp).toBe('9223372036854775807');
  expect(high.after.nextLevelTotalXp).toBeNull();expect(high.after.maxLevel).toBe(config.growth.at(-1)!.level);
  expect(high.after.levelStartTotalXp).toBe(String(config.growth.at(-1)!.totalXp));
 });
 it('normal payout amounts and ledger writes remain unchanged while progress replays from pinned values',async()=>{
  const definition=catalog.characters.find(unit=>unit.id==='u45')!,curve=instanceGrowthCurve(definition.growth),startingXp=String(curve[59].totalXp-1),level=growthAt(Number(startingXp),definition.growth).level;
  const member:NormalXpMember={id:'progress-owned',playerId:'progress-player',startingXp,growth:structuredClone(definition.growth),reincarnation:0,reincarnationXpPolicy:definition.reincarnationXpPolicy,level,...normalXpSource(battle,level,policy)};
  const records=new Map<string,any>(),tx={roster60NormalXpLedger:{findUnique:async()=>records.get(member.id)??null,create:async({data}:any)=>{expect(data).not.toHaveProperty('xpProgress');records.set(member.id,structuredClone(data));return data;}}} as any;
  const room={id:'progress-room',mode:'normal',player0Id:member.playerId,player1Id:'AI:progress-room'},state={botSide:1 as const,entered:[[member.id],[]] as [string[],string[]],parties:[[],[]] as any},snapshot={catalogVersion:catalog.version,members:[{id:member.id,playerId:member.playerId,xp:startingXp}],normalXp:{version:'normal-xp-room-v1' as const,players:[{playerId:member.playerId,policy,members:[member]}]}};
  const first=(await settleNormalXp(tx,room,state,snapshot,'BATTLE',0))!,unit=first.players[member.playerId].units[0],stored=structuredClone([...records.values()]);
  expect(unit.appliedXp).toBe(1);expect(unit.requestedXp).toBe(member.winXp);expect(first.appliedByOwnedId[member.id]).toBe(1);
  expect(unit.xpProgress?.before.level).toBe(59);expect(unit.xpProgress?.after).toMatchObject({level:60,totalXp:String(curve[59].totalXp),nextLevelTotalXp:null});
  const replay=(await settleNormalXp(tx,room,state,snapshot,'BATTLE',0))!;
  expect(replay.players[member.playerId].units[0].xpProgress).toEqual(unit.xpProgress);expect(replay.appliedByOwnedId[member.id]).toBe(0);expect([...records.values()]).toEqual(stored);
  const legacySnapshot={...snapshot,normalXp:{version:'normal-xp-room-v1',players:[{playerId:member.playerId,policy}]}} as any;
  const legacy=(await settleNormalXp(tx,room,state,legacySnapshot,'BATTLE',0))!;
  expect(legacy.players[member.playerId].units[0].xpProgress).toBeUndefined();expect(legacy.players[member.playerId].xpAwarded).toBe(1);expect(legacy.appliedByOwnedId[member.id]).toBe(0);expect([...records.values()]).toEqual(stored);
 });
});
