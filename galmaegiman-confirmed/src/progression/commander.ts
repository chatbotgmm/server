import {createHash} from 'node:crypto';
import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import type {Prisma} from '@prisma/client';
import {RosterError} from '../roster60-onboarding.js';
import type {AccountLevelReader} from './roster60-rank.js';
import {validatePresenceForfeitProof} from './roster60-presence.js';
import type {XpProgress,XpProgressPoint} from './xp-progress.js';

export type CommanderPolicy={
 approved:true;version:string;profileKind:'user-authorized-test-baseline';productionBalanceApproved:false;testValues:true;
 name:'방범대장';eventBasis:'one-account-event';unitXpRelationship:'independent-not-sum-not-split';unitReincarnation:'account-retained';
 initialization:'test-only-zero-no-backfill';legacyEvents:'no-retroactive-reconstruction';totalXpPolicy:'retain-lifetime-total';curveOverflow:'last-known-level-extension-pending';
 growth:{level:number;totalXp:number}[];
 story:{contentVersion:string;mainQuestIds:string[];residentQuestIds:string[]};
 rewards:{storyQuest:number;residentQuest:number;independentAdventureReturn:number;normalWin:number;normalLoss:number;normalProvenTimeoutSurvivor:number};
 budget:{requiredIndependentAdventures:number};notes?:string[];
};
export type CommanderEvent={kind:'STORY_QUEST'|'RESIDENT_QUEST'|'ADVENTURE_RETURN'|'NORMAL_PLAY';eventId:string;outcome:'completed'|'win'|'loss'|'draw'|'surrender'|'departed'|'aborted';reason?:string;linkedQuestId?:string|null;timeoutGenerated?:boolean;terminationProof?:unknown;side?:0|1};
export type CommanderState={name:'방범대장';level:number|null;totalXp:string|null;nextLevelXp:string|null;xpToNext:string|null;maxLevel:number|null;policyVersion:string|null;revision:number|null;initialized:boolean;status:'READY'|'POLICY_PENDING'|'POLICY_MISMATCH'|'CURVE_EXTENSION_PENDING'|'DATA_INVALID';testValues:boolean;productionBalanceApproved:boolean;initialization:'TEST_ZERO_NO_BACKFILL'|'UNCONFIGURED';unitXpIndependent:true;totalXpPolicy:'retain-lifetime-total'};
export type CommanderXpResult={status:'APPLIED'|'RECORDED_ZERO'|'POLICY_PENDING';reason:string;xpAwarded:number;recordedXp:number;requestedXp:number;level:number|null;totalXp:string|null;policyVersion:string|null;replayed:boolean;commander:CommanderState;xpProgress?:XpProgress};
export const commanderSigned64Maximum=9_223_372_036_854_775_807n;
const fail=(code:string,status=503):never=>{throw new RosterError(status,code);};
const record=(value:unknown):value is Record<string,unknown>=>!!value&&typeof value==='object'&&!Array.isArray(value);
const safeAmount=(value:unknown):value is number=>typeof value==='number'&&Number.isSafeInteger(value)&&value>=0&&value<=2_000_000_000;
const id=(value:unknown):value is string=>typeof value==='string'&&value.length>0&&value.length<=300;
const kinds=['STORY_QUEST','RESIDENT_QUEST','ADVENTURE_RETURN','NORMAL_PLAY'];
const outcomes=['completed','win','loss','draw','surrender','departed','aborted'];

/** This initial profile deliberately cannot masquerade as approved production balance.
 * Production rollout needs its own explicit initialization and growth policy.
 */
export function validateCommanderPolicy(raw:unknown):CommanderPolicy{
 if(raw===null||raw===undefined)return fail('COMMANDER_POLICY_PENDING');
 if(!record(raw)||raw.approved!==true||!id(raw.version)||raw.profileKind!=='user-authorized-test-baseline'||raw.productionBalanceApproved!==false||raw.testValues!==true||raw.name!=='방범대장'||raw.eventBasis!=='one-account-event'||raw.unitXpRelationship!=='independent-not-sum-not-split'||raw.unitReincarnation!=='account-retained'||raw.initialization!=='test-only-zero-no-backfill'||raw.legacyEvents!=='no-retroactive-reconstruction'||raw.totalXpPolicy!=='retain-lifetime-total'||raw.curveOverflow!=='last-known-level-extension-pending')return fail('COMMANDER_POLICY_INVALID');
 if(!Array.isArray(raw.growth)||raw.growth.length<60||raw.growth.length>1000)return fail('COMMANDER_POLICY_INVALID');
 for(const [index,point] of raw.growth.entries())if(!record(point)||point.level!==index+1||typeof point.totalXp!=='number'||!Number.isSafeInteger(point.totalXp)||point.totalXp<0||(index===0?point.totalXp!==0:point.totalXp<=(raw.growth[index-1] as {totalXp:number}).totalXp))return fail('COMMANDER_POLICY_INVALID');
 if(!record(raw.story)||!id(raw.story.contentVersion))return fail('COMMANDER_POLICY_INVALID');
 const main=raw.story.mainQuestIds,side=raw.story.residentQuestIds;
 if(!Array.isArray(main)||!Array.isArray(side)||!main.length||main.some(x=>!id(x))||side.some(x=>!id(x))||new Set([...main,...side]).size!==main.length+side.length)return fail('COMMANDER_POLICY_INVALID');
 const rewards=raw.rewards;
 if(!record(rewards)||Object.keys(rewards).length!==6||!['storyQuest','residentQuest','independentAdventureReturn','normalWin','normalLoss','normalProvenTimeoutSurvivor'].every(key=>safeAmount(rewards[key]))||!record(raw.budget)||!safeAmount(raw.budget.requiredIndependentAdventures))return fail('COMMANDER_POLICY_INVALID');
 if(rewards.normalLoss!==Math.floor((rewards.normalWin as number)/2)||rewards.normalProvenTimeoutSurvivor!==rewards.normalWin)return fail('COMMANDER_POLICY_INVALID');
 return raw as CommanderPolicy;
}

/** Stable semantic fingerprint: arbitrary object key order and notes/budget
 * presentation metadata never alter an account's growth contract.
 */
export function commanderPolicyFingerprint(policy:CommanderPolicy):string{
 validateCommanderPolicy(policy);
 const r=policy.rewards;
 const semantic=[policy.version,policy.approved,policy.profileKind,policy.productionBalanceApproved,policy.testValues,policy.name,policy.eventBasis,policy.unitXpRelationship,policy.unitReincarnation,policy.initialization,policy.legacyEvents,policy.totalXpPolicy,policy.curveOverflow,policy.growth.map(point=>[point.level,point.totalXp]),policy.story.contentVersion,[...policy.story.mainQuestIds].sort(),[...policy.story.residentQuestIds].sort(),[r.storyQuest,r.residentQuest,r.independentAdventureReturn,r.normalWin,r.normalLoss,r.normalProvenTimeoutSurvivor]];
 return createHash('sha256').update(JSON.stringify(semantic)).digest('hex');
}

/** Same explicit profile boundary as roster60; never fall back to a review fixture. */
export function commanderPolicyReader(root:string,profile?:string):()=>CommanderPolicy|null{
 if(profile&&profile!=='five-stat-device-test-v1')throw Error('UNKNOWN_ROSTER60_PROFILE');
 const file=resolve(root,profile?'data/test-profiles/'+profile+'/commander-policy.json':'data/roster60-commander-policy.json');
 return ()=>{if(!existsSync(file))return null;let raw:unknown;try{raw=JSON.parse(readFileSync(file,'utf8'));}catch{return fail('COMMANDER_POLICY_INVALID');}return validateCommanderPolicy(raw);};
}

export function commanderLevelAt(totalXp:bigint,policy:CommanderPolicy){
 if(totalXp<0n||totalXp>commanderSigned64Maximum)return fail('COMMANDER_XP_DATA_INVALID');
 let index=0;for(let i=1;i<policy.growth.length&&totalXp>=BigInt(policy.growth[i].totalXp);i++)index=i;
 const next=policy.growth[index+1]?.totalXp;
 return {level:policy.growth[index].level,nextLevelXp:next===undefined?null:String(next),xpToNext:next===undefined?null:(BigInt(next)-totalXp).toString(),maxLevel:policy.growth.at(-1)!.level,status:next===undefined?'CURVE_EXTENSION_PENDING' as const:'READY' as const};
}

/** Last-known policy boundary is a display limit, not a new XP cap or growth formula. */
export function commanderXpProgress(startingXp:bigint,endingXp:bigint,policy:CommanderPolicy):XpProgress {
 const point=(totalXp:bigint):XpProgressPoint=>{const level=commanderLevelAt(totalXp,policy);return {level:level.level,totalXp:totalXp.toString(),levelStartTotalXp:String(policy.growth[level.level-1].totalXp),nextLevelTotalXp:level.nextLevelXp,maxLevel:level.maxLevel};};
 return {before:point(startingXp),after:point(endingXp)};
}

function emptyState(status:CommanderState['status']='POLICY_PENDING'):CommanderState{return {name:'방범대장',level:null,totalXp:null,nextLevelXp:null,xpToNext:null,maxLevel:null,policyVersion:null,revision:null,initialized:false,status,testValues:false,productionBalanceApproved:false,initialization:'UNCONFIGURED',unitXpIndependent:true,totalXpPolicy:'retain-lifetime-total'};}
function accountState(row:{totalXp:bigint;policyVersion:string;policyFingerprint:string;revision:number}|null,policy:CommanderPolicy):CommanderState{
 if(row&&(row.policyVersion!==policy.version||row.policyFingerprint!==commanderPolicyFingerprint(policy)))return {...emptyState('POLICY_MISMATCH'),policyVersion:row.policyVersion,initialized:true};
 if(row&&(!Number.isSafeInteger(row.revision)||row.revision<0||row.totalXp<0n||row.totalXp>commanderSigned64Maximum))return {...emptyState('DATA_INVALID'),policyVersion:row.policyVersion,initialized:true};
 const totalXp=row?.totalXp??0n;
 return {...emptyState(),...commanderLevelAt(totalXp,policy),totalXp:totalXp.toString(),policyVersion:policy.version,revision:row?.revision??0,initialized:!!row,testValues:true,initialization:'TEST_ZERO_NO_BACKFILL'};
}

/** Read-only: no unit lookup, no highest-unit inference, and no historical reconstruction. */
export async function readCommanderState(tx:Prisma.TransactionClient,playerId:string,policy:CommanderPolicy|null):Promise<CommanderState>{
 if(policy===null)return emptyState();validateCommanderPolicy(policy);
 return accountState(await tx.commanderAccount.findUnique({where:{playerId}}),policy);
}
/** Only this authoritative account row supplies rank account level. Unit admission stays separate. */
export function commanderAccountLevelReader(policy:()=>CommanderPolicy|null):AccountLevelReader{return async(tx,playerId)=>{
 const config=policy();if(config===null)return null;
 try{validateCommanderPolicy(config);}catch(e){if(e instanceof RosterError)return null;throw e;}
 const state=await readCommanderState(tx,playerId,config);return state.status==='READY'||state.status==='CURVE_EXTENSION_PENDING'?state.level:null;
};}

function validateEvent(event:CommanderEvent){
 if(!record(event)||!kinds.includes(event.kind)||!id(event.eventId)||!outcomes.includes(event.outcome)||event.reason!==undefined&&(typeof event.reason!=='string'||event.reason.length>100)||event.linkedQuestId!==undefined&&event.linkedQuestId!==null&&!id(event.linkedQuestId)||event.timeoutGenerated!==undefined&&typeof event.timeoutGenerated!=='boolean'||event.side!==undefined&&event.side!==0&&event.side!==1)return fail('COMMANDER_EVENT_INVALID',400);
 if(Object.keys(event).some(key=>!['kind','eventId','outcome','reason','linkedQuestId','timeoutGenerated','terminationProof','side'].includes(key)))return fail('COMMANDER_EVENT_INVALID',400);
}
function eventFingerprint(event:CommanderEvent){
 const proof=event.terminationProof,presence=validatePresenceForfeitProof(proof)?[proof.version,proof.departedSide,proof.survivorSide,proof.graceStartedAt,proof.graceUntil,proof.monitorGeneration]:'unproven';
 // An immutable terminal fact is included; observation clocks and proof decisionAt are not.
 return createHash('sha256').update(JSON.stringify([event.kind,event.eventId,event.outcome,event.reason??'',event.linkedQuestId??null,event.timeoutGenerated??false,event.side??null,presence])).digest('hex');
}
export function commanderEventReward(event:CommanderEvent,policy:CommanderPolicy):{requestedXp:number;reason:string}{
 validateEvent(event);validateCommanderPolicy(policy);
 if(event.kind==='STORY_QUEST'||event.kind==='RESIDENT_QUEST'){
  const keys=event.kind==='STORY_QUEST'?policy.story.mainQuestIds:policy.story.residentQuestIds;
  if(!keys.some(key=>event.eventId===policy.story.contentVersion+':'+key))return fail('COMMANDER_QUEST_SOURCE_INVALID',409);
  return event.outcome==='completed'?{requestedXp:event.kind==='STORY_QUEST'?policy.rewards.storyQuest:policy.rewards.residentQuest,reason:'QUEST_COMPLETED'}:{requestedXp:0,reason:'QUEST_NOT_COMPLETED'};
 }
 if(event.kind==='ADVENTURE_RETURN'){
  if(event.linkedQuestId)return {requestedXp:0,reason:'QUEST_LINKED_ADVENTURE_ZERO'};
  return event.outcome==='completed'&&event.reason==='WIN_RETURN'?{requestedXp:policy.rewards.independentAdventureReturn,reason:'INDEPENDENT_ADVENTURE_COMPLETED'}:{requestedXp:0,reason:'ADVENTURE_NO_CLEAR_ZERO'};
 }
 if(event.timeoutGenerated)return {requestedXp:0,reason:'AUTOMATIC_TURN_ZERO'};
 if(event.reason==='TIMEOUT'){
  const proof=event.terminationProof;
  return event.outcome==='win'&&validatePresenceForfeitProof(proof)&&event.side===proof.survivorSide?{requestedXp:policy.rewards.normalProvenTimeoutSurvivor,reason:'PROVEN_TIMEOUT_SURVIVOR'}:{requestedXp:0,reason:'TIMEOUT_NON_SURVIVOR_ZERO'};
 }
 if(event.reason==='BATTLE'&&(event.outcome==='win'||event.outcome==='loss'))return {requestedXp:event.outcome==='win'?policy.rewards.normalWin:policy.rewards.normalLoss,reason:event.outcome==='win'?'NORMAL_WIN':'NORMAL_LOSS'};
 return {requestedXp:0,reason:'NORMAL_NON_COMPLETION_ZERO'};
}

/** Caller owns the domain transaction and its retry. Account CAS and unique event
 * ledger commit together; a uniqueness/concurrency failure must roll back both.
 * Null policy is non-blocking and creates no rows. Callers must preserve the
 * original null policy/first-completion boundary instead of replaying old events.
 */
export async function awardCommanderXp(tx:Prisma.TransactionClient,playerId:string,event:CommanderEvent,policy:CommanderPolicy|null):Promise<CommanderXpResult>{
 validateEvent(event);const fingerprint=eventFingerprint(event),where={playerId_eventKind_eventId:{playerId,eventKind:event.kind,eventId:event.eventId}};
 const old=await tx.commanderXpLedger.findUnique({where});
 if(old){if(old.fingerprint!==fingerprint)return fail('COMMANDER_EVENT_CONFLICT',409);let stored:CommanderXpResult;try{stored=JSON.parse(old.result);}catch{return fail('COMMANDER_LEDGER_INVALID');}return {...stored,xpAwarded:0,recordedXp:old.appliedXp,replayed:true};}
 if(policy===null){const commander=emptyState();return {status:'POLICY_PENDING',reason:'COMMANDER_POLICY_PENDING',xpAwarded:0,recordedXp:0,requestedXp:0,level:null,totalXp:null,policyVersion:null,replayed:false,commander};}
 validateCommanderPolicy(policy);const reward=commanderEventReward(event,policy),policyFingerprint=commanderPolicyFingerprint(policy);
 const account=await tx.commanderAccount.upsert({where:{playerId},create:{playerId,totalXp:0n,policyVersion:policy.version,policyFingerprint},update:{}});
 const before=accountState(account,policy);if(before.status==='POLICY_MISMATCH')return fail('COMMANDER_ACCOUNT_POLICY_MISMATCH');if(before.status==='DATA_INVALID')return fail('COMMANDER_XP_DATA_INVALID');
 const ending=account.totalXp+BigInt(reward.requestedXp);if(ending>commanderSigned64Maximum)return fail('COMMANDER_XP_STORAGE_LIMIT');
 const changed=await tx.commanderAccount.updateMany({where:{playerId,policyVersion:policy.version,policyFingerprint,revision:account.revision,totalXp:account.totalXp},data:{totalXp:ending,revision:{increment:1}}});
 if(changed.count!==1)return fail('COMMANDER_CONCURRENT_WRITE',409);
 const commander=accountState({totalXp:ending,revision:account.revision+1,policyVersion:policy.version,policyFingerprint},policy);
 const result:CommanderXpResult={status:reward.requestedXp?'APPLIED':'RECORDED_ZERO',reason:reward.reason,xpAwarded:reward.requestedXp,recordedXp:reward.requestedXp,requestedXp:reward.requestedXp,level:commander.level,totalXp:commander.totalXp,policyVersion:policy.version,replayed:false,commander,xpProgress:commanderXpProgress(account.totalXp,ending,policy)};
 await tx.commanderXpLedger.create({data:{playerId,eventKind:event.kind,eventId:event.eventId,fingerprint,policyVersion:policy.version,policyFingerprint,outcome:event.outcome,reason:event.reason??'',status:result.status,requestedXp:reward.requestedXp,appliedXp:reward.requestedXp,startingXp:account.totalXp,endingXp:ending,result:JSON.stringify(result)}});
 return result;
}

/** Finite current-content budget; quest-linked required adventures contribute zero
 * additional account XP and are represented explicitly in the simulation input.
 */
export function simulateCommanderBudget(policy:CommanderPolicy,requiredQuestLinkedAdventureReturns=0){
 validateCommanderPolicy(policy);if(!safeAmount(requiredQuestLinkedAdventureReturns))return fail('COMMANDER_BUDGET_INVALID');
 const mainXp=policy.story.mainQuestIds.length*policy.rewards.storyQuest,residentXp=policy.story.residentQuestIds.length*policy.rewards.residentQuest,independentAdventureXp=policy.budget.requiredIndependentAdventures*policy.rewards.independentAdventureReturn;
 if(!Number.isSafeInteger(mainXp+residentXp+independentAdventureXp))return fail('COMMANDER_BUDGET_INVALID');
 const mainLevel=commanderLevelAt(BigInt(mainXp),policy).level,totalXp=mainXp+residentXp+independentAdventureXp,totalLevel=commanderLevelAt(BigInt(totalXp),policy).level;
 return {testValues:true,productionBalanceApproved:false,mainQuestCount:policy.story.mainQuestIds.length,residentQuestCount:policy.story.residentQuestIds.length,mainXp,residentXp,requiredQuestLinkedAdventureReturns,questLinkedAdventureXp:0,requiredIndependentAdventures:policy.budget.requiredIndependentAdventures,independentAdventureXp,totalXp,mainLevel,totalLevel,mainOnlyBelow60:mainLevel<60,completeBudgetReaches60:totalLevel>=60};
}
