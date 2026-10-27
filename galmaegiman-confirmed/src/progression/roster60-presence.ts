/** Server-observed presence only. No socket, client timestamp or process UUID is proof. */
export type PresenceSide=0|1;
export type PresencePolicy={version:'roster60-presence-policy-v1';leaseMs:number;totalAbsentMs:number;monitorGapMs:number};
// Configurable test defaults reuse the existing 45-second queue lease and nominal
// three 30-second missed turns. These exact durations are not user-approved values.
// Grace starts after 45 seconds; its remaining 45 seconds end at lastSeen + 90.
export const defaultPresencePolicy:Readonly<PresencePolicy>=Object.freeze({version:'roster60-presence-policy-v1',leaseMs:45_000,totalAbsentMs:90_000,monitorGapMs:45_000});
export type PresenceMonitor={version:'roster60-presence-monitor-v1';generation:number;lastSuccessAt:number;continuousSince:number};
export type PresenceParticipant={lastSeenAt:number;leaseUntil:number;continuousSince:number};
export type PresenceState={version:'roster60-presence-v1';policy:PresencePolicy;monitorGeneration:number;monitorStartedAt:number;initializedAt:number;botSide?:1;participants:[PresenceParticipant|null,PresenceParticipant|null]};
export type PresenceForfeitProof={version:'roster60-presence-proof-v1';departedSide:PresenceSide;graceStartedAt:number;graceUntil:number;survivorSide:PresenceSide;monitorGeneration:number;decisionAt:number};
export type PresenceGrace={side:PresenceSide;graceStartedAt:number;graceUntil:number};
export type PresenceDecision=
 |{kind:'CONTINUE'}
 |{kind:'RECONNECT_GRACE';absent:PresenceGrace[]}
 |{kind:'FORFEIT_PROVEN';proof:PresenceForfeitProof}
 |{kind:'ABORT_NEUTRAL';reason:'PRESENCE_UNINITIALIZED'|'INVALID_PRESENCE'|'MONITOR_UNAVAILABLE'|'MONITOR_RECOVERY'|'MONITOR_GAP'|'CLOCK_MOVED_BACK'|'SURVIVOR_UNPROVEN'|'INVALID_OBSERVER'};
const sides=[0,1] as const;
const record=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const time=(v:unknown):v is number=>typeof v==='number'&&Number.isSafeInteger(v)&&v>=0;
const positive=(v:unknown):v is number=>time(v)&&v>0;
const side=(v:unknown):v is PresenceSide=>v===0||v===1;
const exact=(v:Record<string,unknown>,keys:string[])=>Object.keys(v).length===keys.length&&keys.every(k=>Object.prototype.hasOwnProperty.call(v,k));
const abort=(reason:Extract<PresenceDecision,{kind:'ABORT_NEUTRAL'}>['reason']):PresenceDecision=>({kind:'ABORT_NEUTRAL',reason});
function policyValue(raw:unknown):PresencePolicy|null{
 if(!record(raw)||!exact(raw,['version','leaseMs','totalAbsentMs','monitorGapMs'])||raw.version!=='roster60-presence-policy-v1'||!positive(raw.leaseMs)||!positive(raw.totalAbsentMs)||raw.totalAbsentMs<=raw.leaseMs||!positive(raw.monitorGapMs))return null;
 return {version:raw.version,leaseMs:raw.leaseMs,totalAbsentMs:raw.totalAbsentMs,monitorGapMs:raw.monitorGapMs};
}
function monitorValue(raw:unknown):PresenceMonitor|null{
 if(!record(raw)||!exact(raw,['version','generation','lastSuccessAt','continuousSince'])||raw.version!=='roster60-presence-monitor-v1'||!positive(raw.generation)||!time(raw.lastSuccessAt)||!time(raw.continuousSince)||raw.continuousSince>raw.lastSuccessAt)return null;
 return {version:raw.version,generation:raw.generation,lastSuccessAt:raw.lastSuccessAt,continuousSince:raw.continuousSince};
}
function stateValue(raw:unknown):PresenceState|null{
 if(!record(raw)||raw.version!=='roster60-presence-v1'||!positive(raw.monitorGeneration)||!time(raw.monitorStartedAt)||!time(raw.initializedAt)||raw.monitorStartedAt>raw.initializedAt||raw.botSide!==undefined&&raw.botSide!==1||!Array.isArray(raw.participants)||raw.participants.length!==2)return null;
 const policy=policyValue(raw.policy);if(!policy)return null;
 const participants:[PresenceParticipant|null,PresenceParticipant|null]=[null,null];
 for(const index of sides){
  const p=raw.participants[index];if(index===raw.botSide){if(p!==null)return null;continue;}
  if(!record(p)||!exact(p,['lastSeenAt','leaseUntil','continuousSince'])||!time(p.lastSeenAt)||!time(p.leaseUntil)||!time(p.continuousSince)||p.lastSeenAt<raw.initializedAt||p.continuousSince<raw.initializedAt||p.continuousSince>p.lastSeenAt||!time(p.lastSeenAt+policy.totalAbsentMs)||p.leaseUntil!==p.lastSeenAt+policy.leaseMs)return null;
  participants[index]={lastSeenAt:p.lastSeenAt,leaseUntil:p.leaseUntil,continuousSince:p.continuousSince};
 }
 return {version:raw.version,policy,monitorGeneration:raw.monitorGeneration,monitorStartedAt:raw.monitorStartedAt,initializedAt:raw.initializedAt,...(raw.botSide===1?{botSide:1 as const}:{}),participants};
}

/** Call only for a successful shared DB monitor commit, never from a process startup UUID.
 * Persist the returned record atomically. Existing rooms pin both generation and
 * its continuousSince, so another Prisma client reads the same recovery epoch.
 */
export function monitorObservation(previous:unknown,now:number,config:Readonly<PresencePolicy>=defaultPresencePolicy):{monitor:PresenceMonitor;recovered:boolean;reason:'MONITOR_INITIALIZED'|'MONITOR_INVALID'|'MONITOR_GAP'|'CLOCK_MOVED_BACK'|null}{
 const policy=policyValue(config);if(!policy||!time(now))throw Error('INVALID_PRESENCE_MONITOR_INPUT');
 const old=monitorValue(previous);
 let reason:'MONITOR_INITIALIZED'|'MONITOR_INVALID'|'MONITOR_GAP'|'CLOCK_MOVED_BACK'|null=null;
 if(!old)reason=previous===null||previous===undefined?'MONITOR_INITIALIZED':'MONITOR_INVALID';
 else if(now<old.lastSuccessAt)reason='CLOCK_MOVED_BACK';
 else if(now-old.lastSuccessAt>policy.monitorGapMs)reason='MONITOR_GAP';
 const priorGeneration=old?.generation??(record(previous)&&positive(previous.generation)?previous.generation:0);
 const generation=reason?priorGeneration+1:old!.generation;
 if(!positive(generation))throw Error('INVALID_PRESENCE_MONITOR_GENERATION');
 return {monitor:{version:'roster60-presence-monitor-v1',generation,lastSuccessAt:now,continuousSince:reason?now:old!.continuousSince},recovered:reason!==null,reason};
}

/** New-room baseline only. Missing/old room state must go through decidePresence
 * and terminate neutrally; never fabricate historical observations on recovery.
 */
export function initializePresence(rawMonitor:unknown,now:number,config:Readonly<PresencePolicy>=defaultPresencePolicy,botSide?:1):PresenceState{
 const policy=policyValue(config),monitor=monitorValue(rawMonitor);
 if(!policy||!time(now)||!time(now+policy.totalAbsentMs)||botSide!==undefined&&botSide!==1)throw Error('INVALID_PRESENCE_INITIALIZATION');
 if(!monitor||monitor.lastSuccessAt>now||now-monitor.lastSuccessAt>policy.monitorGapMs)throw Error('PRESENCE_MONITOR_UNAVAILABLE');
 const participant=():PresenceParticipant=>({lastSeenAt:now,leaseUntil:now+policy.leaseMs,continuousSince:now});
 return {version:'roster60-presence-v1',policy,monitorGeneration:monitor.generation,monitorStartedAt:monitor.continuousSince,initializedAt:now,...(botSide===1?{botSide}:{}),participants:[participant(),botSide===1?null:participant()]};
}

/** Structural validation for an internally persisted server proof. This is not
 * authentication: no HTTP endpoint may accept a client-supplied proof as evidence.
 */
export function validatePresenceForfeitProof(raw:unknown,context?:{state:unknown;monitor:unknown}):raw is PresenceForfeitProof{
 const valid=record(raw)&&exact(raw,['version','departedSide','graceStartedAt','graceUntil','survivorSide','monitorGeneration','decisionAt'])&&raw.version==='roster60-presence-proof-v1'&&side(raw.departedSide)&&side(raw.survivorSide)&&raw.departedSide!==raw.survivorSide&&time(raw.graceStartedAt)&&time(raw.graceUntil)&&raw.graceUntil>raw.graceStartedAt&&time(raw.decisionAt)&&raw.decisionAt>=raw.graceUntil&&positive(raw.monitorGeneration);
 if(!valid)return false;if(!context)return true;
 const proof=raw as PresenceForfeitProof,decision=decidePresence(context.state,context.monitor,proof.decisionAt);
 return decision.kind==='FORFEIT_PROVEN'&&Object.keys(proof).every(key=>proof[key as keyof PresenceForfeitProof]===decision.proof[key as keyof PresenceForfeitProof]);
}

/** Evaluate before touching presence. A late poll cannot erase a passed cutoff.
 * Continuing to poll without submitting a turn remains CONTINUE, not a forfeit.
 */
export function decidePresence(rawState:unknown,rawMonitor:unknown,now:number):PresenceDecision{
 if(rawState===null||rawState===undefined)return abort('PRESENCE_UNINITIALIZED');
 const state=stateValue(rawState);if(!state||!time(now))return abort('INVALID_PRESENCE');
 const monitor=monitorValue(rawMonitor);if(!monitor)return abort('MONITOR_UNAVAILABLE');
 if(now<state.initializedAt||now<monitor.lastSuccessAt||state.participants.some(p=>p!==null&&now<p.lastSeenAt))return abort('CLOCK_MOVED_BACK');
 if(monitor.generation!==state.monitorGeneration||monitor.continuousSince!==state.monitorStartedAt)return abort('MONITOR_RECOVERY');
 if(now-monitor.lastSuccessAt>state.policy.monitorGapMs)return abort('MONITOR_GAP');
 const absent:PresenceGrace[]=sides.filter(index=>index!==state.botSide&&now>=state.participants[index]!.leaseUntil).map(index=>({side:index,graceStartedAt:state.participants[index]!.leaseUntil,graceUntil:state.participants[index]!.lastSeenAt+state.policy.totalAbsentMs}));
 if(!absent.length)return {kind:'CONTINUE'};
 const expired=absent.filter(p=>now>=p.graceUntil);if(!expired.length)return {kind:'RECONNECT_GRACE',absent};
 if(expired.length!==1)return abort('SURVIVOR_UNPROVEN');
 const departed=expired[0],survivorSide=(1-departed.side) as PresenceSide,survivor=state.participants[survivorSide];
 // AI has no presence lease, player record or reward. Its human can still leave
 // after grace; the resulting proof identifies the bot side for terminal outcome.
 const proven=survivorSide===state.botSide||survivor!==null&&survivor.leaseUntil>now&&survivor.continuousSince<=departed.graceStartedAt;
 if(!proven)return abort('SURVIVOR_UNPROVEN');
 return {kind:'FORFEIT_PROVEN',proof:{version:'roster60-presence-proof-v1',departedSide:departed.side,graceStartedAt:departed.graceStartedAt,graceUntil:departed.graceUntil,survivorSide,monitorGeneration:monitor.generation,decisionAt:now}};
}

/** An authenticated successful room read/action renews only that human side.
 * Invalid commands, queue polls, sweeps and bot commands must not call this.
 */
export function observePresence(rawState:unknown,observer:PresenceSide,rawMonitor:unknown,now:number):{state:PresenceState|null;decision:PresenceDecision}{
 const state=stateValue(rawState),before=decidePresence(rawState,rawMonitor,now);
 if(before.kind==='ABORT_NEUTRAL'||before.kind==='FORFEIT_PROVEN')return {state,decision:before};
 if(!state||!side(observer)||observer===state.botSide)return {state,decision:abort('INVALID_OBSERVER')};
 if(!time(now+state.policy.totalAbsentMs))return {state,decision:abort('INVALID_PRESENCE')};
 const prior=state.participants[observer]!;
 state.participants[observer]={lastSeenAt:now,leaseUntil:now+state.policy.leaseMs,continuousSince:now<=prior.leaseUntil?prior.continuousSince:now};
 return {state,decision:decidePresence(state,rawMonitor,now)};
}
