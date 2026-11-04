import {pacedPool,pacedFinisherWin,averageLevelPacing,averageLevelWin,validateUnitXpPacing,type UnitXpPacing} from './unit-xp-pacing.js';
import type {Prisma} from '@prisma/client';
import type {BattlePolicy,Fighter} from '../roster60-battle.js';
import {RosterError,type RosterCatalog} from '../roster60-onboarding.js';
import {validatePresenceForfeitProof,type PresenceForfeitProof} from './roster60-presence.js';
import {growthAt,instanceGrowthCurve,rosterMaxLevel,type GrowthPoint,type ReincarnationXpPolicy} from '../battle/roster60-rules.js';
import {unitXpProgress,type XpProgress} from './xp-progress.js';
import {finisherSplitShares,battleAverageLevel} from './finisher-split.js';

type NormalXpPolicyBase={approved:boolean;version:string;catalogVersion:string;profileKind:'user-authorized-test-baseline'|'approved-production';productionBalanceApproved:boolean;testValues:boolean;scope:'normal';source:'duel-nearest-owned-start-level';participation:'actually-entered-owned-only'|'all-formed-owned';surrender:'confirmed-zero';draw:'undecided-no-award';legacyRoom:'unconfigured-no-retroactive-award';notes?:string[];xpPacing?:UnitXpPacing};
export type NormalXpLegacyPolicy=NormalXpPolicyBase&{rulesVersion?:'normal-xp-v1';lossFallback:{numerator:number;denominator:number;minimum:number};capOverflow:'pending-policy-ledger';eligibleReason:'BATTLE';timeout:'undecided-no-award';disconnect:'undecided-no-award'};
export type NormalXpCurrentPolicy=NormalXpPolicyBase&{rulesVersion:'normal-xp-v2';outcomePercent:{win:100;loss:50};distribution:'equal-entered-owned';poolBasis:'highest-entered-start-level';remainder:'owned-id-ascending';capOverflow:'discard-overflow';eligibleReason:'BATTLE'|'BATTLE_OR_VALIDATED_GRACE_TIMEOUT';timeout:'undecided-no-award'|'validated-grace-survivor-only';disconnect:'undecided-no-award'|'validated-grace-survivor-only'};
// v3: 이긴 편의 마무리 유닛(이긴 순간 나와 있던 개체)은 기본량 전액, 편성된 나머지 개체는 절반(내림). 패배는 0.
export type NormalXpFinisherPolicy=NormalXpPolicyBase&{rulesVersion:'normal-xp-v3';participation:'all-formed-owned';outcomePercent:{win:100;loss:0};distribution:'finisher-full-rest-half';poolBasis:'highest-entered-start-level';capOverflow:'discard-overflow';eligibleReason:'BATTLE_OR_VALIDATED_GRACE_TIMEOUT';timeout:'validated-grace-survivor-only';disconnect:'validated-grace-survivor-only'};
export type NormalXpPolicy=NormalXpLegacyPolicy|NormalXpCurrentPolicy|NormalXpFinisherPolicy;
export type NormalXpMember={id:string;playerId:string;startingXp:string;growth:GrowthPoint[];reincarnation:number;reincarnationXpPolicy?:ReincarnationXpPolicy;sourceEncounterId:string;winXp:number;lossXp:number;lossFallbackUsed:boolean;level:number;nearestTierMinimum?:number};
export type NormalXpPlayerPlan={playerId:string;policy:NormalXpPolicy;members:NormalXpMember[];enemyLevel?:number};
// The container format stays compatible with queued/active v1 rooms; each plan pins its source policy.
export type NormalXpRoomSnapshot={version:'normal-xp-room-v1';players:NormalXpPlayerPlan[]};
type MemberReference={id:string;playerId:string;xp:string};
const fail=(code:string,status=503):never=>{throw new RosterError(status,code);};
const safeXp=(n:number)=>Number.isSafeInteger(n)&&n>=0&&n<=2_000_000_000;
const currentPolicy=(p:NormalXpPolicy):p is NormalXpCurrentPolicy=>p.rulesVersion==='normal-xp-v2';
const finisherPolicy=(p:NormalXpPolicy):p is NormalXpFinisherPolicy=>p.rulesVersion==='normal-xp-v3';
// 이미 고정된 v1 계획만 확정 v2 규칙 이름을 덧붙인다.
const modernPolicy=(p:NormalXpPolicy)=>currentPolicy(p)||finisherPolicy(p);
const effectiveVersion=(p:NormalXpPolicy)=>(modernPolicy(p)?p.version:p.version+':confirmed-v2')+(p.xpPacing?':'+p.xpPacing.version:'');
const compareOwned=(a:string,b:string)=>a<b?-1:a>b?1:0;

/** Legacy input is accepted only to read an already pinned queue/room, never for a new admission. */
export function validateNormalXpPolicy(p:NormalXpPolicy|null,catalogVersion:string,allowPinnedLegacy=false):asserts p is NormalXpPolicy{
 if(!p||p.approved!==true)return fail('NORMAL_XP_POLICY_PENDING');
 if(!p.version||p.catalogVersion!==catalogVersion||!['user-authorized-test-baseline','approved-production'].includes(p.profileKind)||p.productionBalanceApproved!==(p.profileKind==='approved-production')||p.testValues!==!p.productionBalanceApproved||p.scope!=='normal'||p.source!=='duel-nearest-owned-start-level'||p.participation!==(finisherPolicy(p)?'all-formed-owned':'actually-entered-owned-only')||p.surrender!=='confirmed-zero'||p.draw!=='undecided-no-award'||p.legacyRoom!=='unconfigured-no-retroactive-award')return fail('NORMAL_XP_POLICY_INVALID');
 if(p.xpPacing!==undefined)validateUnitXpPacing(p.xpPacing,catalogVersion);
 if(finisherPolicy(p)){
  if(p.outcomePercent?.win!==100||p.outcomePercent?.loss!==0||p.distribution!=='finisher-full-rest-half'||p.poolBasis!=='highest-entered-start-level'||p.capOverflow!=='discard-overflow'||p.eligibleReason!=='BATTLE_OR_VALIDATED_GRACE_TIMEOUT'||p.timeout!=='validated-grace-survivor-only'||p.disconnect!=='validated-grace-survivor-only'||Object.prototype.hasOwnProperty.call(p,'remainder'))return fail('NORMAL_XP_POLICY_INVALID');
 }else if(currentPolicy(p)){
  if(p.outcomePercent?.win!==100||p.outcomePercent?.loss!==50||p.distribution!=='equal-entered-owned'||p.poolBasis!=='highest-entered-start-level'||p.remainder!=='owned-id-ascending'||p.capOverflow!=='discard-overflow')return fail('NORMAL_XP_POLICY_INVALID');
  const presence=p.eligibleReason==='BATTLE_OR_VALIDATED_GRACE_TIMEOUT'&&p.timeout==='validated-grace-survivor-only'&&p.disconnect==='validated-grace-survivor-only';
  const prior=p.eligibleReason==='BATTLE'&&p.timeout==='undecided-no-award'&&p.disconnect==='undecided-no-award';
  if(!presence&&!(allowPinnedLegacy&&prior))return fail('NORMAL_XP_POLICY_INVALID');
 }else{
  if(p.rulesVersion!==undefined&&p.rulesVersion!=='normal-xp-v1'||p.capOverflow!=='pending-policy-ledger'||p.eligibleReason!=='BATTLE'||p.timeout!=='undecided-no-award'||p.disconnect!=='undecided-no-award')return fail('NORMAL_XP_POLICY_INVALID');
  const f=p.lossFallback;if(!f||!Number.isSafeInteger(f.numerator)||f.numerator<1||!Number.isSafeInteger(f.denominator)||f.denominator<1||f.numerator>f.denominator||!safeXp(f.minimum)||f.minimum<1)return fail('NORMAL_XP_POLICY_INVALID');
  if(!allowPinnedLegacy)return fail('NORMAL_XP_POLICY_PENDING');
 }
}

/** Source amounts remain a pinned test curve. v2 loss is always 50% of win, not duel lossXp. */
export function normalXpSource(policy:BattlePolicy,level:number,config:NormalXpPolicy){
 if(!Number.isSafeInteger(level)||level<1||level>99)return fail('NORMAL_XP_LEVEL_INVALID');
 const sources=policy.encounters.filter(e=>e.mode==='duel'&&!e.tutorial);
 if(!sources.length)return fail('NORMAL_XP_SOURCE_PENDING');
 if(sources.some(e=>!safeXp(e.winXp)||e.winXp<1||!safeXp(e.lossXp)))return fail('NORMAL_XP_SOURCE_INVALID');
 sources.sort((a,b)=>Math.abs(a.level-level)-Math.abs(b.level-level)||a.level-b.level||compareOwned(a.id,b.id));const source=sources[0];
 if(finisherPolicy(config))return {sourceEncounterId:source.id,winXp:source.winXp,lossXp:0,lossFallbackUsed:false};
 if(currentPolicy(config))return {sourceEncounterId:source.id,winXp:source.winXp,lossXp:Math.floor(source.winXp/2),lossFallbackUsed:false};
 const f=config.lossFallback,fallback=Math.max(f.minimum,Number(BigInt(source.winXp)*BigInt(f.numerator)/BigInt(f.denominator)));
 if(!safeXp(fallback)||fallback<1)return fail('NORMAL_XP_SOURCE_INVALID');
 return {sourceEncounterId:source.id,winXp:source.winXp,lossXp:source.lossXp>0?source.lossXp:fallback,lossFallbackUsed:source.lossXp===0};
}

/** Only the server's validated expired-grace proof adds a TIMEOUT survivor win. */
export function normalXpTerminal(reason:string,winner:0|1|null,timeoutGenerated=false,proof?:unknown):{eligible:boolean;status:string;presence?:PresenceForfeitProof}{
 if(timeoutGenerated)return {eligible:false,status:'TIMEOUT_RESOLUTION_POLICY_PENDING'};
 if(reason==='TIMEOUT'&&validatePresenceForfeitProof(proof)&&winner===proof.survivorSide)return {eligible:true,status:'DISCONNECT_WIN',presence:proof};
 if(reason==='BATTLE'&&winner!==null)return {eligible:true,status:'APPLIED'};
 if(reason==='SURRENDER')return {eligible:false,status:'SURRENDER_ZERO'};
 return {eligible:false,status:reason==='BATTLE'?'DRAW_POLICY_PENDING':'TERMINAL_POLICY_PENDING'};
}

/** Clamp to the actual 60/99 threshold in the pinned per-instance curve. No bank or reincarnation. */
export function normalXpGrant(member:Pick<NormalXpMember,'startingXp'|'growth'|'reincarnation'|'reincarnationXpPolicy'>,requestedXp:number){
 if(!safeXp(requestedXp)||!/^\d+$/.test(member.startingXp))return fail('NORMAL_XP_SNAPSHOT_INVALID');
 const start=BigInt(member.startingXp);if(start>BigInt(Number.MAX_SAFE_INTEGER))return fail('NORMAL_XP_SNAPSHOT_INVALID');
 // Invalid or already overflowing stored XP is not silently reduced or treated as an ordinary cap.
 growthAt(Number(start),member.growth,member.reincarnation,member.reincarnationXpPolicy);
 const curve=instanceGrowthCurve(member.growth,member.reincarnation,member.reincarnationXpPolicy),maximum=rosterMaxLevel(member.reincarnation);
 if(curve.length<maximum)return fail('NORMAL_XP_GROWTH_INCOMPLETE');
 const cap=BigInt(curve[maximum-1].totalXp),next=start+BigInt(requestedXp),ending=next>cap?cap:next;
 const appliedXp=Number(ending-start);if(appliedXp<0)return fail('NORMAL_XP_SNAPSHOT_INVALID');
 growthAt(Number(ending),member.growth,member.reincarnation,member.reincarnationXpPolicy);
 return {requestedXp,appliedXp,deferredXp:0,endingXp:ending.toString(),status:appliedXp<requestedXp?'CAP_CLAMPED':'APPLIED'};
}

/** Account pool, then outcome ratio, then equal shares. Reserves and duplicate entered IDs add no XP. */
export function normalXpShares(members:NormalXpMember[],enteredIds:readonly string[],outcome:'win'|'loss',pacing?:{policy:UnitXpPacing;enemyLevel:number},finisher?:{finisherId:string|undefined;averageLevel?:number}){
 if(new Set(members.map(m=>m.id)).size!==members.length)return fail('NORMAL_XP_SNAPSHOT_INVALID');
 const ids=[...new Set(enteredIds)].sort(compareOwned),byId=new Map(members.map(m=>[m.id,m]));
 if(ids.some(id=>typeof id!=='string'||!byId.has(id)||id.startsWith('AI:')))return fail('NORMAL_XP_MEMBERS_INVALID');
 const entered=ids.map(id=>byId.get(id)!);
 if(!entered.length)return {baseXp:0,poolXp:0,sourceEncounterId:null as string|null,shares:new Map<string,number>()};
 // Highest entered starting level is an explicit test proposal for the still-unapproved base amount.
 if(entered.some(m=>m.playerId!==entered[0].playerId||!safeXp(m.winXp)||m.winXp<1||!Number.isSafeInteger(m.level)||m.level<1||m.level>99||!m.sourceEncounterId))return fail('NORMAL_XP_SNAPSHOT_INVALID');
 const basis=[...entered].sort((a,b)=>b.level-a.level||compareOwned(a.id,b.id))[0];
 const baseXp=pacing&&!finisher?pacedPool(entered.map(m=>({...m,ownedId:m.id})),pacing.enemyLevel,basis.winXp,pacing.policy):basis.winXp;
 // v3: 이긴 편만 받는다. 마무리 개체 전액, 편성된 나머지(미출전 포함)는 절반.
 if(finisher){
  if(outcome!=='win')return {baseXp,poolXp:0,sourceEncounterId:basis.sourceEncounterId,shares:new Map<string,number>()};
  const finisherId=finisher.finisherId??fail('NORMAL_XP_FINISHER_MISSING'),finisherMember=byId.get(finisherId)??fail('NORMAL_XP_FINISHER_MISSING');
  // 기본량은 기존 원천 W. 보조 정책이 있으면 마무리 개체의 「적정 레벨 승리 N번에 1레벨」 최소값까지 올린다.
  // 통일 판: 전투 전원 평균 레벨 기준. 그 밖의 판은 기존 원천과 마무리 개체 최소값 중 큰 값.
  const winXp=pacing&&averageLevelPacing(pacing.policy)?averageLevelWin(finisherMember,finisher.averageLevel??fail('NORMAL_XP_SNAPSHOT_INVALID'),pacing.policy):pacing?pacedFinisherWin(finisherMember,pacing.enemyLevel,basis.winXp,pacing.policy,finisherMember.nearestTierMinimum):basis.winXp;
  const shares=finisherSplitShares(winXp,members.map(m=>m.id).sort(compareOwned),finisherId);
  return {baseXp:winXp,poolXp:[...shares.values()].reduce((sum,value)=>sum+value,0),sourceEncounterId:basis.sourceEncounterId,shares};
 }
 const poolXp=outcome==='win'?baseXp:Math.floor(baseXp/2),each=Math.floor(poolXp/ids.length),remainder=poolXp%ids.length;
 return {baseXp,poolXp,sourceEncounterId:basis.sourceEncounterId,shares:new Map(ids.map((id,index)=>[id,each+(index<remainder?1:0)]))};
}

/** Called before queue ownership is claimed. Every reward source and growth curve is pinned here. */
export async function prepareNormalXpPlayer(tx:Prisma.TransactionClient,playerId:string,members:MemberReference[],catalog:RosterCatalog,battlePolicy:BattlePolicy,config:NormalXpPolicy|null):Promise<NormalXpPlayerPlan>{
 validateNormalXpPolicy(config,catalog.version);if(!members.length||new Set(members.map(m=>m.id)).size!==members.length||members.some(m=>m.playerId!==playerId||m.id.startsWith('AI:')))return fail('NORMAL_XP_MEMBERS_INVALID');
 const plan:NormalXpMember[]=[];
 for(const member of members){
  const owned=await tx.roster60Owned.findFirst({where:{id:member.id,playerId}});if(!owned||owned.totalXp.toString()!==member.xp)return fail('UNIT_STATE_CHANGED',409);
  const definition=catalog.characters.find(c=>c.id===owned.speciesId);if(!definition)return fail('BATTLE_UNIT_DATA_PENDING');
  const level=growthAt(Number(owned.totalXp),definition.growth,owned.reincarnation,definition.reincarnationXpPolicy).level,source=normalXpSource(battlePolicy,level,config);
  const entry:NormalXpMember={id:member.id,playerId,startingXp:member.xp,growth:structuredClone(definition.growth),reincarnation:owned.reincarnation,reincarnationXpPolicy:structuredClone(definition.reincarnationXpPolicy),level,...source,...(config.xpPacing?{nearestTierMinimum:battlePolicy.encounters.find(e=>e.id===source.sourceEncounterId)!.level}:{})};
  normalXpGrant(entry,source.winXp);normalXpGrant(entry,source.lossXp);plan.push(entry);
 }
 if(config.xpPacing)pacedPool(plan.map(m=>({...m,ownedId:m.id})),99,Math.max(...plan.map(m=>m.winXp)),config.xpPacing);
 return {playerId,policy:structuredClone(config),members:plan};
}

export function queuedNormalXpPlan(raw:string|null|undefined,playerId:string,members:MemberReference[],catalogVersion?:string):NormalXpPlayerPlan|null{
 if(!raw)return null;let plan:NormalXpPlayerPlan;try{plan=JSON.parse(raw);}catch{return fail('NORMAL_XP_SNAPSHOT_INVALID');}
 if(!plan||!plan.policy||!Array.isArray(plan.members))return fail('NORMAL_XP_SNAPSHOT_INVALID');
 if(catalogVersion&&plan.policy.catalogVersion!==catalogVersion)return fail('NORMAL_XP_CATALOG_CHANGED',409);
 validateNormalXpPolicy(plan.policy,catalogVersion??plan.policy.catalogVersion,true);
 if(plan.playerId!==playerId||plan.members.length!==members.length||new Set(plan.members.map(m=>m.id)).size!==plan.members.length||new Set(members.map(m=>m.id)).size!==members.length||plan.members.some(m=>m.playerId!==playerId||!members.some(ref=>ref.id===m.id&&ref.xp===m.startingXp)))return fail('NORMAL_XP_SNAPSHOT_INVALID');
 return plan;
}

export type NormalXpSettlement={status:string;players:Record<string,{status:string;xpAwarded:number;xpDeferred:number;xpDiscarded:number;xpRequested:number;policyVersion:string|null;sourcePolicyVersion:string|null;rulesVersion:'normal-xp-v2'|'legacy-record';testValues:boolean;participation:'actually-entered-owned-only'|'all-formed-owned';units:{ownedId:string;requestedXp:number;appliedXp:number;deferredXp:number;discardedXp:number;status:string;sourceEncounterId:string|null;xpProgress?:XpProgress}[]}>;appliedByOwnedId:Record<string,number>};

/** Old completed ledgers are immutable; unsettled v1 plans retain sources but use the confirmed v2 rules. */
export async function settleNormalXp(tx:Prisma.TransactionClient,room:{id:string;mode:string;player0Id:string;player1Id:string},state:{botSide?:1;entered?:[string[],string[]];parties:[Fighter[],Fighter[]]},snapshot:{catalogVersion:string;members:MemberReference[];normalXp?:NormalXpRoomSnapshot},reason:string,winner:0|1|null,timeoutGenerated=false,terminationProof?:unknown):Promise<NormalXpSettlement|null>{
 // 랭크는 입장 때 경험치 계획을 고정한 방(통일 판)만 정산한다. 예전 랭크 방은 계속 0이다.
 if(room.mode!=='normal'&&!(room.mode==='rank'&&snapshot.normalXp?.version==='normal-xp-room-v1'))return null;
 const decision=normalXpTerminal(reason,winner,timeoutGenerated,terminationProof);
 const terminal:ReturnType<typeof normalXpTerminal>=decision.presence&&decision.presence.departedSide===state.botSide?{eligible:false,status:'TERMINAL_POLICY_PENDING'}:decision;
 const plans=snapshot.normalXp?.version==='normal-xp-room-v1'?snapshot.normalXp.players:[],result:NormalXpSettlement={status:plans.length?terminal.status:'LEGACY_POLICY_UNCONFIGURED',players:{},appliedByOwnedId:{}};
 for(const [side,playerId] of [room.player0Id,room.player1Id].entries()){
  if(side===state.botSide)continue;const plan=plans.find(p=>p.playerId===playerId),outcome=winner===null?'draw':winner===side?'win':'loss';
  const departed=terminal.presence?.departedSide===side,eligible=terminal.eligible&&!departed,terminalStatus=departed?'DISCONNECTED_ZERO':terminal.status,unconfiguredStatus=departed?'DISCONNECTED_ZERO':'LEGACY_POLICY_UNCONFIGURED';
  const refs=snapshot.members.filter(m=>m.playerId===playerId),retained=new Map<string,Awaited<ReturnType<typeof tx.roster60NormalXpLedger.findUnique>>>();
  if(new Set(refs.map(m=>m.id)).size!==refs.length||refs.some(m=>m.id.startsWith('AI:')))return fail('NORMAL_XP_MEMBERS_INVALID');
  for(const ref of refs)retained.set(ref.id,await tx.roster60NormalXpLedger.findUnique({where:{playerId_roomId_ownedId:{playerId,roomId:room.id,ownedId:ref.id}}}));
  // A complete prior decision is replayed before validating new rules or touching its old curve.
  if(refs.length&&refs.every(ref=>retained.get(ref.id))){
   const records=refs.map(ref=>retained.get(ref.id)!),statuses=records.map(r=>r.status);
   const status=statuses.includes('CAP_POLICY_PENDING')?'CAP_POLICY_PENDING':statuses.includes('CAP_CLAMPED')?'CAP_CLAMPED':statuses.includes('DISCONNECT_WIN')?'DISCONNECT_WIN':statuses.includes('APPLIED')?'APPLIED':statuses.find(value=>value!=='NOT_ENTERED')??'NOT_ENTERED';
   const own={status,xpAwarded:0,xpDeferred:0,xpDiscarded:0,xpRequested:0,policyVersion:records[0].policyVersion,sourcePolicyVersion:plan?.policy?.version??null,rulesVersion:'legacy-record' as const,testValues:plan?.policy?.testValues??false,participation:(plan?.policy&&finisherPolicy(plan.policy)?'all-formed-owned':'actually-entered-owned-only') as 'actually-entered-owned-only'|'all-formed-owned',units:[] as NormalXpSettlement['players'][string]['units']};
   for(const record of records){const discardedXp=Math.max(0,record.requestedXp-record.appliedXp-record.deferredXp),pinned=Array.isArray(plan?.members)?plan.members.find(m=>m&&m.id===record.ownedId&&m.playerId===playerId&&m.startingXp===record.startingXp):undefined,xpProgress=pinned?unitXpProgress(pinned,record.endingXp,record.startingXp):undefined;own.xpAwarded+=record.appliedXp;own.xpDeferred+=record.deferredXp;own.xpDiscarded+=discardedXp;own.xpRequested+=record.requestedXp;result.appliedByOwnedId[record.ownedId]=0;own.units.push({ownedId:record.ownedId,requestedXp:record.requestedXp,appliedXp:record.appliedXp,deferredXp:record.deferredXp,discardedXp,status:record.status,sourceEncounterId:record.sourceEncounterId,...(xpProgress?{xpProgress}:{})});}
   result.players[playerId]=own;continue;
  }
  if(plan)validateNormalXpPolicy(plan.policy,snapshot.catalogVersion,true);
  const targetPolicyVersion=plan?effectiveVersion(plan.policy):null;
  const own={status:plan?terminalStatus:unconfiguredStatus,xpAwarded:0,xpDeferred:0,xpDiscarded:0,xpRequested:0,policyVersion:targetPolicyVersion,sourcePolicyVersion:plan?.policy.version??null,rulesVersion:'normal-xp-v2' as 'normal-xp-v2'|'legacy-record',testValues:plan?.policy.testValues??false,participation:(plan&&finisherPolicy(plan.policy)?'all-formed-owned':'actually-entered-owned-only') as 'actually-entered-owned-only'|'all-formed-owned',units:[] as NormalXpSettlement['players'][string]['units']};result.players[playerId]=own;
  const entered=[...new Set(state.entered?.[side]??[])];
  if(plan&&(new Set(plan.members.map(m=>m.id)).size!==plan.members.length||plan.members.length!==refs.length||plan.members.some(m=>m.playerId!==playerId||!refs.some(ref=>ref.id===m.id&&ref.xp===m.startingXp))))return fail('NORMAL_XP_SNAPSHOT_INVALID');
  const pool=plan&&eligible&&outcome!=='draw'?normalXpShares(plan.members,entered,outcome,plan.policy.xpPacing?{policy:plan.policy.xpPacing,enemyLevel:plan.enemyLevel??fail('NORMAL_XP_SNAPSHOT_INVALID')}:undefined,finisherPolicy(plan.policy)?{finisherId:outcome==='win'?state.parties[side]?.[0]?.ownedId:undefined,averageLevel:battleAverageLevel(state.parties.flat().map(f=>f.level))}:undefined):null;
  for(const member of refs){
   if(member.id.startsWith('AI:'))return fail('NORMAL_XP_MEMBERS_INVALID');
   const pinned=plan?.members.find(m=>m.id===member.id&&m.playerId===playerId&&m.startingXp===member.xp);
   if(plan&&!pinned)return fail('NORMAL_XP_SNAPSHOT_INVALID');
   // v3는 편성된 개체 모두가 대상이다(미출전 예비도 절반을 받는다).
   let status=!plan?unconfiguredStatus:!eligible?terminalStatus:!finisherPolicy(plan.policy)&&!entered.includes(member.id)?'NOT_ENTERED':'APPLIED';
   const requestedXp=status==='APPLIED'?(pool?.shares.get(member.id)??0):0;
   const grant=pinned&&status==='APPLIED'?normalXpGrant(pinned,requestedXp):{requestedXp:0,appliedXp:0,deferredXp:0,endingXp:member.xp,status};if(status==='APPLIED'){status=grant.status;if(status==='APPLIED'&&terminalStatus==='DISCONNECT_WIN')status='DISCONNECT_WIN';}
   const old=retained.get(member.id);
   const record=old??await tx.roster60NormalXpLedger.create({data:{playerId,roomId:room.id,ownedId:member.id,policyVersion:targetPolicyVersion,outcome,reason,...grant,status,startingXp:member.xp,sourceEncounterId:pool?.shares.has(member.id)?pool.sourceEncounterId:pinned?.sourceEncounterId??null}});
   const discardedXp=Math.max(0,record.requestedXp-record.appliedXp-record.deferredXp);
   if(old){own.policyVersion=record.policyVersion;if(record.policyVersion!==targetPolicyVersion||record.status==='CAP_POLICY_PENDING')own.rulesVersion='legacy-record';}
   own.xpAwarded+=record.appliedXp;own.xpDeferred+=record.deferredXp;own.xpDiscarded+=discardedXp;own.xpRequested+=record.requestedXp;
   // A pre-existing ledger is a result replay, never another delta to apply.
   result.appliedByOwnedId[member.id]=old?0:record.appliedXp;
   if(record.status==='CAP_POLICY_PENDING')own.status='CAP_POLICY_PENDING';else if(record.status==='CAP_CLAMPED'&&own.status!=='CAP_POLICY_PENDING')own.status='CAP_CLAMPED';
   const xpProgress=pinned?unitXpProgress(pinned,record.endingXp,record.startingXp):undefined;
   own.units.push({ownedId:member.id,requestedXp:record.requestedXp,appliedXp:record.appliedXp,deferredXp:record.deferredXp,discardedXp,status:record.status,sourceEncounterId:record.sourceEncounterId,...(xpProgress?{xpProgress}:{})});
  }
 }
 return result;
}
