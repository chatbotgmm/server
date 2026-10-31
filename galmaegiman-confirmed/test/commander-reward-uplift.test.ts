import {describe,expect,it} from 'vitest';
import {mkdtempSync,mkdirSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {awardCommanderXp,commanderEventReward,commanderLevelAt,commanderPolicyFingerprint,commanderPolicyReader,readCommanderState,simulateCommanderBudget,validateCommanderPolicy,type CommanderEvent} from '../src/progression/commander.js';

const directory='data/test-profiles/five-stat-device-test-v1/';
const original=JSON.parse(readFileSync(directory+'commander-policy.json','utf8'));
const schedule=JSON.parse(readFileSync(directory+'commander-reward-schedule.json','utf8'));
const legacy=()=>validateCommanderPolicy(structuredClone(original));
const uplift=()=>validateCommanderPolicy({...structuredClone(original),rewardSchedule:structuredClone(schedule)});
const normal=(eventId:string,outcome:CommanderEvent['outcome']='win',reason='BATTLE'):CommanderEvent=>({kind:'NORMAL_PLAY',eventId,outcome,reason});
function memory(){
 const accounts=new Map<string,any>(),ledger=new Map<string,any>();
 return {accounts,ledger,tx:{
  commanderAccount:{findUnique:async({where}:any)=>accounts.get(where.playerId)??null,upsert:async({where,create}:any)=>{if(!accounts.has(where.playerId))accounts.set(where.playerId,{...create,revision:0});return {...accounts.get(where.playerId)};},updateMany:async({where,data}:any)=>{const row=accounts.get(where.playerId);if(!row||row.revision!==where.revision||row.policyVersion!==where.policyVersion||row.policyFingerprint!==where.policyFingerprint||row.totalXp!==where.totalXp)return {count:0};accounts.set(where.playerId,{...row,totalXp:data.totalXp,revision:row.revision+1});return {count:1};}},
  commanderXpLedger:{findUnique:async({where}:any)=>ledger.get(JSON.stringify(where.playerId_eventKind_eventId))??null,create:async({data}:any)=>{const key=JSON.stringify({playerId:data.playerId,eventKind:data.eventKind,eventId:data.eventId});if(ledger.has(key))throw Error('duplicate test ledger');ledger.set(key,structuredClone(data));return data;}},
 } as any};
}

describe('new-activity commander reward schedule, retained account growth',()=>{
 it('loads an explicit adjacent schedule without editing the base account policy or auto-filling a missing policy',()=>{
  expect(original.rewardSchedule).toBeUndefined();
  const root=mkdtempSync(join(tmpdir(),'commander-uplift-')),target=join(root,directory);mkdirSync(target,{recursive:true});
  try{
   writeFileSync(join(target,'commander-reward-schedule.json'),JSON.stringify(schedule));
   const read=commanderPolicyReader(root,'five-stat-device-test-v1');expect(read()).toBeNull();
   writeFileSync(join(target,'commander-policy.json'),JSON.stringify(original));
   expect(read()?.rewardSchedule).toEqual(schedule);expect(read()?.rewards).toEqual(original.rewards);
   writeFileSync(join(target,'commander-reward-schedule.json'),'{invalid');expect(read).toThrow('COMMANDER_REWARD_SCHEDULE_INVALID');
   writeFileSync(join(target,'commander-reward-schedule.json'),JSON.stringify(schedule));writeFileSync(join(target,'commander-policy.json'),JSON.stringify(uplift()));expect(read).toThrow('COMMANDER_REWARD_SCHEDULE_CONFLICT');
  }finally{rmSync(root,{recursive:true,force:true});}
 });

 it('keeps the curve and existing account fingerprint while versioning only newly pinned rewards',()=>{
  const old=legacy(),next=uplift();expect(next.growth).toEqual(old.growth);expect(next.version).toBe(old.version);
  expect(commanderPolicyFingerprint(next)).toBe(commanderPolicyFingerprint(old));
  const reordered=structuredClone(next);reordered.rewardSchedule!.rewards=Object.fromEntries(Object.entries(reordered.rewardSchedule!.rewards).reverse()) as any;
  expect(commanderPolicyFingerprint(reordered)).toBe(commanderPolicyFingerprint(old));
  const changed=structuredClone(next);changed.growth[1].totalXp=101;expect(commanderPolicyFingerprint(changed)).not.toBe(commanderPolicyFingerprint(old));
 });

 it('raises each account reward source while preserving old pinned amounts and zero-event rules',()=>{
  const old=legacy(),next=uplift(),events:CommanderEvent[]=[
   {kind:'STORY_QUEST',eventId:old.story.contentVersion+':STORY_01',outcome:'completed'},
   {kind:'RESIDENT_QUEST',eventId:old.story.contentVersion+':RESIDENT_BLACKSMITH_01',outcome:'completed'},
   {kind:'ADVENTURE_RETURN',eventId:'run',outcome:'completed',reason:'WIN_RETURN'},normal('normal'),normal('loss','loss')];
  expect(events.map(event=>commanderEventReward(event,old).requestedXp)).toEqual([200,450,100,100,50]);
  expect(events.map(event=>commanderEventReward(event,next).requestedXp)).toEqual([250,600,150,150,75]);
  for(const event of [normal('escape','surrender','SURRENDER'),normal('draw','draw'),normal('abort','aborted','SERVER_ABORT'),{...events[2],reason:'NO_CLEAR_RETURN'},{...events[2],linkedQuestId:'STORY_01'}])expect(commanderEventReward(event,next).requestedXp).toBe(0);
  const proof={version:'roster60-presence-proof-v1',departedSide:0,survivorSide:1,graceStartedAt:1000,graceUntil:2000,monitorGeneration:1,decisionAt:2000};
  expect(commanderEventReward({...normal('proof'),reason:'TIMEOUT',side:1,terminationProof:proof},next).requestedXp).toBe(150);
  expect(commanderEventReward({...normal('proof'),reason:'TIMEOUT',side:0,terminationProof:proof},next).requestedXp).toBe(0);
 });

 it('keeps main-only below 60, makes resident quests worthwhile and leaves normal growth at one win',()=>{
  const p=uplift();expect(simulateCommanderBudget(p)).toMatchObject({mainXp:5250,residentXp:2400,totalXp:7650,mainLevel:53,totalLevel:77,mainOnlyBelow60:true,completeBudgetReaches60:true,questLinkedAdventureXp:0});
  expect(commanderLevelAt(5250n+1200n,p).level).toBe(65);
  for(const level of [10,15,20,24,40,59]){
   const start=p.growth[level-1].totalXp,needed=p.growth[level].totalXp-start,win=commanderEventReward(normal('level-'+level),p).requestedXp;
   expect(needed).toBe(100);expect(Math.ceil(needed/win)).toBe(1);
  }
 });

 it('rejects malformed or lower schedules and keeps the existing win/loss relation',()=>{
  for(const change of [null,{}, {...schedule,extra:true},{...schedule,version:''},{...schedule,rewards:{...schedule.rewards,normalWin:149}},{...schedule,rewards:{...schedule.rewards,normalLoss:74}},{...schedule,rewards:{...schedule.rewards,storyQuest:199}},{...schedule,rewards:{...schedule.rewards,normalWin:NaN}},{...schedule,rewards:{...schedule.rewards,unknown:1}}])expect(()=>validateCommanderPolicy({...original,rewardSchedule:change})).toThrow('COMMANDER_REWARD_SCHEDULE_INVALID');
 });

 it('awards new and old pins on the same existing account without replaying or rewriting receipts',async()=>{
  const {tx,accounts,ledger}=memory(),oldPin=legacy(),newPin=uplift(),oldEvent=normal('old-room');
  const first=await awardCommanderXp(tx,'owner',oldEvent,oldPin);expect(first).toMatchObject({xpAwarded:100,totalXp:'100'});expect(first.rewardScheduleVersion).toBeUndefined();
  const oldReceipt=structuredClone([...ledger.values()][0]);
  const gained=await awardCommanderXp(tx,'owner',normal('new-room'),newPin);expect(gained).toMatchObject({xpAwarded:150,totalXp:'250',rewardScheduleVersion:schedule.version});
  const replay=await awardCommanderXp(tx,'owner',oldEvent,newPin);expect(replay).toMatchObject({xpAwarded:0,recordedXp:100,totalXp:'100',replayed:true});expect(replay.rewardScheduleVersion).toBeUndefined();
  expect([...ledger.values()][0]).toEqual(oldReceipt);
  const reconnect=await readCommanderState(tx,'owner',newPin);expect(reconnect).toMatchObject({status:'READY',totalXp:'250',level:3});expect(accounts.get('owner').totalXp).toBe(250n);
  expect(await awardCommanderXp(tx,'owner',normal('new-room'),oldPin)).toMatchObject({xpAwarded:0,recordedXp:150,rewardScheduleVersion:schedule.version,replayed:true});
  expect(await awardCommanderXp(tx,'owner',normal('old-pinned-room-finishes-later'),oldPin)).toMatchObject({xpAwarded:100,totalXp:'350'});expect(ledger.size).toBe(3);
 });

 it('preserves account lifetime XP after its last known level rather than creating a new cap or unit rebirth',async()=>{
  const {tx,accounts}=memory(),p=uplift();accounts.set('owner',{playerId:'owner',totalXp:10_000n,policyVersion:p.version,policyFingerprint:commanderPolicyFingerprint(p),revision:3});
  const result=await awardCommanderXp(tx,'owner',normal('over-known-curve'),p);expect(result).toMatchObject({xpAwarded:150,totalXp:'10150',level:99,commander:{status:'CURVE_EXTENSION_PENDING'}});expect(accounts.get('owner').totalXp).toBe(10_150n);
 });
});
