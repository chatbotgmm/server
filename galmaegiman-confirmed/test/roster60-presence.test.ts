import {describe,expect,it} from 'vitest';
import {defaultPresencePolicy,decidePresence,initializePresence,monitorObservation,observePresence,validatePresenceForfeitProof,type PresenceMonitor,type PresencePolicy,type PresenceState} from '../src/progression/roster60-presence.js';

// Pure arithmetic fixtures only. No DB, timer, sockets or wall clock are involved.
const origin=1000;
function session(botSide?:1,policy:PresencePolicy={...defaultPresencePolicy}){
 let monitor:PresenceMonitor=monitorObservation(null,origin,policy).monitor;
 let state:PresenceState=initializePresence(monitor,origin,policy,botSide);
 return {
  get monitor(){return monitor;},get state(){return state;},
  monitorAt(now:number){monitor=monitorObservation(monitor,now,policy).monitor;return monitor;},
  observe(observer:0|1,now:number){monitor=monitorObservation(monitor,now,policy).monitor;const result=observePresence(state,observer,monitor,now);if(result.state)state=result.state;return result;},
  decide(now:number){return decidePresence(state,monitor,now);}
 };
}
function continuouslyPresentOpponent(){
 const s=session();s.observe(1,origin+30_000);s.observe(1,origin+60_000);s.observe(1,origin+89_000);s.monitorAt(origin+90_000);return s;
}

describe('server-observed roster60 reconnect presence (configurable test defaults)',()=>{
 it('pins 45-second suspicion and a total 90-second absence window without mutating inputs',()=>{
  expect(defaultPresencePolicy).toMatchObject({leaseMs:45_000,totalAbsentMs:90_000,monitorGapMs:45_000});
  const s=session(),before=structuredClone(s.state);
  const seen=s.observe(0,origin+30_000);
  expect(seen.decision.kind).toBe('CONTINUE');expect(before.participants[0]!.lastSeenAt).toBe(origin);
  expect(s.state.participants[0]).toEqual({lastSeenAt:origin+30_000,leaseUntil:origin+75_000,continuousSince:origin});
  expect(s.state.participants[1]).toEqual(before.participants[1]);
 });
 it('separates an expired lease from grace expiry and accepts a return before the fixed cutoff',()=>{
  const s=session();s.observe(1,origin+30_000);s.observe(1,origin+45_000);
  expect(s.decide(origin+45_000)).toEqual({kind:'RECONNECT_GRACE',absent:[{side:0,graceStartedAt:origin+45_000,graceUntil:origin+90_000}]});
  s.observe(1,origin+75_000);const returned=s.observe(0,origin+89_999);
  expect(returned.decision.kind).toBe('CONTINUE');expect(s.state.participants[0]!.continuousSince).toBe(origin+89_999);
 });
 it('proves one departure only with a continuously observed survivor and does not touch a late return',()=>{
  const s=continuouslyPresentOpponent(),decision=s.decide(origin+90_000);
  expect(decision.kind).toBe('FORFEIT_PROVEN');if(decision.kind!=='FORFEIT_PROVEN')throw Error('expected proof');
  expect(decision.proof).toEqual({version:'roster60-presence-proof-v1',departedSide:0,survivorSide:1,graceStartedAt:origin+45_000,graceUntil:origin+90_000,monitorGeneration:1,decisionAt:origin+90_000});
  expect(validatePresenceForfeitProof(decision.proof)).toBe(true);
  const late=observePresence(s.state,0,s.monitor,origin+90_000);
  expect(late.decision).toEqual(decision);expect(late.state!.participants[0]!.lastSeenAt).toBe(origin);
 });
 it('does not award a win to an opponent who appeared only after a broken lease',()=>{
  const s=session();s.monitorAt(origin+30_000);s.monitorAt(origin+60_000);s.observe(1,origin+61_000);s.monitorAt(origin+90_000);
  expect(s.state.participants[1]!.continuousSince).toBe(origin+61_000);
  expect(s.decide(origin+90_000)).toEqual({kind:'ABORT_NEUTRAL',reason:'SURVIVOR_UNPROVEN'});
 });
 it('waits for grace when both are absent, then terminates neutrally rather than inventing a survivor',()=>{
  const s=session();s.monitorAt(origin+30_000);s.monitorAt(origin+45_000);
  const grace=s.decide(origin+45_000);expect(grace.kind).toBe('RECONNECT_GRACE');
  s.monitorAt(origin+75_000);s.monitorAt(origin+90_000);
  expect(s.decide(origin+90_000)).toEqual({kind:'ABORT_NEUTRAL',reason:'SURVIVOR_UNPROVEN'});
 });
 it('keeps healthy polling turn idleness separate from a disconnect forfeit',()=>{
  const s=session();
  for(const offset of [30_000,60_000,90_000,120_000]){s.observe(0,origin+offset);s.observe(1,origin+offset);expect(s.decide(origin+offset).kind).toBe('CONTINUE');}
 });
 it('requires a healthy shared monitor and neutralizes gaps and recovery generations',()=>{
  const s=session();
  expect(s.decide(origin+45_001)).toEqual({kind:'ABORT_NEUTRAL',reason:'MONITOR_GAP'});
  const recovered=monitorObservation(s.monitor,origin+45_001);
  expect(recovered).toMatchObject({recovered:true,reason:'MONITOR_GAP',monitor:{generation:2,continuousSince:origin+45_001}});
  expect(decidePresence(s.state,recovered.monitor,origin+45_001)).toEqual({kind:'ABORT_NEUTRAL',reason:'MONITOR_RECOVERY'});
  // A second Prisma process reading the same persisted record keeps its epoch;
  // creating a process is not represented by a new random identifier here.
  const otherProcess=monitorObservation(recovered.monitor,origin+45_002);
  expect(otherProcess.recovered).toBe(false);expect(otherProcess.monitor.generation).toBe(2);
 });
 it('neutralizes clock reversal, unknown old rooms and invalid shared-monitor records',()=>{
  const s=session();s.observe(0,origin+10_000);
  expect(decidePresence(s.state,s.monitor,origin+9_999)).toEqual({kind:'ABORT_NEUTRAL',reason:'CLOCK_MOVED_BACK'});
  const backwards=monitorObservation(s.monitor,origin+9_999);expect(backwards).toMatchObject({recovered:true,reason:'CLOCK_MOVED_BACK',monitor:{generation:2}});
  expect(decidePresence(undefined,s.monitor,origin+10_000)).toEqual({kind:'ABORT_NEUTRAL',reason:'PRESENCE_UNINITIALIZED'});
  expect(observePresence(undefined,0,s.monitor,origin+10_000).state).toBeNull();
  expect(decidePresence(s.state,{...s.monitor,generation:'process-uuid'},origin+10_000)).toEqual({kind:'ABORT_NEUTRAL',reason:'MONITOR_UNAVAILABLE'});
 });
 it('excludes AI from observations and makes no synthetic human survivor lease',()=>{
  const s=session(1);expect(s.state.participants[1]).toBeNull();
  expect(observePresence(s.state,1,s.monitor,origin).decision).toEqual({kind:'ABORT_NEUTRAL',reason:'INVALID_OBSERVER'});
  s.monitorAt(origin+30_000);s.monitorAt(origin+60_000);s.monitorAt(origin+90_000);
  const decision=s.decide(origin+90_000);expect(decision.kind).toBe('FORFEIT_PROVEN');
  if(decision.kind!=='FORFEIT_PROVEN')throw Error('expected AI-room departure');
  expect(decision.proof).toMatchObject({departedSide:0,survivorSide:1});expect(s.state.participants[1]).toBeNull();
 });
 it('pins configurable test timing rather than changing existing rooms with a later config edit',()=>{
  const config:PresencePolicy={version:'roster60-presence-policy-v1',leaseMs:10,totalAbsentMs:25,monitorGapMs:20};
  const s=session(undefined,config);config.leaseMs=1;
  expect(s.state.policy.leaseMs).toBe(10);
  // Use the pinned policy when committing monitor observations for this fixture.
  let monitor=monitorObservation(s.monitor,origin+10,s.state.policy).monitor;
  let state=observePresence(s.state,1,monitor,origin+10).state!;
  monitor=monitorObservation(monitor,origin+20,state.policy).monitor;state=observePresence(state,1,monitor,origin+20).state!;
  monitor=monitorObservation(monitor,origin+25,state.policy).monitor;
  const decision=decidePresence(state,monitor,origin+25);expect(decision.kind).toBe('FORFEIT_PROVEN');
  if(decision.kind==='FORFEIT_PROVEN')expect(decision.proof).toMatchObject({graceStartedAt:origin+10,graceUntil:origin+25});
 });
 it('rejects malformed proof shapes and inconsistent cutoff, side or generation claims',()=>{
  const s=continuouslyPresentOpponent(),decision=s.decide(origin+90_000);
  if(decision.kind!=='FORFEIT_PROVEN')throw Error('expected proof');const proof=decision.proof;
  for(const invalid of [null,{}, {...proof,version:'client-claim'}, {...proof,departedSide:1}, {...proof,survivorSide:2}, {...proof,graceUntil:proof.graceStartedAt}, {...proof,decisionAt:proof.graceUntil-1}, {...proof,monitorGeneration:0}, {...proof,monitorGeneration:'uuid'}, {...proof,decisionAt:Infinity}, {...proof,clientApproved:true}])expect(validatePresenceForfeitProof(invalid)).toBe(false);
 });
 it('can revalidate a plausible proof against the persisted state and monitor before committing it',()=>{
  const s=continuouslyPresentOpponent(),decision=s.decide(origin+90_000);
  if(decision.kind!=='FORFEIT_PROVEN')throw Error('expected proof');
  const context={state:s.state,monitor:s.monitor};
  expect(validatePresenceForfeitProof(decision.proof,context)).toBe(true);
  expect(validatePresenceForfeitProof({...decision.proof,departedSide:1,survivorSide:0},context)).toBe(false);
  expect(validatePresenceForfeitProof({...decision.proof,monitorGeneration:2},context)).toBe(false);
  expect(validatePresenceForfeitProof(decision.proof,{state:undefined,monitor:s.monitor})).toBe(false);
 });
 it('rejects invalid initialization timing rather than silently creating a new policy or lease',()=>{
  const monitor=monitorObservation(null,origin).monitor;
  expect(()=>initializePresence(monitor,origin,{...defaultPresencePolicy,totalAbsentMs:45_000})).toThrow('INVALID_PRESENCE_INITIALIZATION');
  expect(()=>initializePresence(monitor,origin+45_001)).toThrow('PRESENCE_MONITOR_UNAVAILABLE');
  expect(()=>monitorObservation(monitor,origin,{...defaultPresencePolicy,monitorGapMs:0})).toThrow('INVALID_PRESENCE_MONITOR_INPUT');
 });
});
