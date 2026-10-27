import {createHash} from 'node:crypto';
import type {Prisma} from '@prisma/client';
import type {RosterCatalog} from '../roster60-onboarding.js';
import {growthAt} from '../battle/roster60-rules.js';

/** Admission levels do not change the existing normalized ranked battle level. */
export const roster60RankPolicyVersion='account60-party60-history-rank-v1';
/** Account-history pools apply to new normal and ranked matches, independently of level admission. */
export const roster60MatchPoolPolicyVersion='account-rebirth-history-count-normal-rank-v2';
export type RankPool='FIRST_LIFE'|'REBORN';
export type RebirthCountSource='NO_REBIRTH_HISTORY'|'ROSTER60_ACCOUNT_SUCCESSES'|'HISTORY_WITHOUT_ACCOUNT_COUNT'|'MIXED_LEGACY_HISTORY'|'INVALID_OR_INCOMPLETE_HISTORY';
export type RebirthHistory={pool:RankPool|null;count:number|null;countSource:RebirthCountSource;historyStamp:string};
export type MatchPoolPlayer={playerId:string;pool:RankPool;count:number|null;countSource:RebirthCountSource;historyStamp:string};
export type MatchPoolSnapshot={version:string;players:MatchPoolPlayer[]};
export type AccountLevelReader=(tx:Prisma.TransactionClient,playerId:string)=>number|null|Promise<number|null>;
/** No account growth model has been approved. Production must explicitly supply its reader. */
export const unknownAccountLevel:AccountLevelReader=()=>null;
export type RankEligibility={eligible:boolean;reason:string;accountLevel:number|null;requiredAccountLevel:60;requiredUnitLevel:60;pool:RankPool|null;scope:'rank-only';policyVersion:string;units:{ownedId:string;level:number;rankEligible:boolean;rankReason:string}[]};
export type RankAdmission={playerId:string;pool:RankPool;accountLevel:number;ownedIds:string[];catalogVersion:string};
export type RankAdmissionSnapshot={version:string;players:RankAdmission[]};

export function rankUnitEligibility(level:number){return {rankEligible:Number.isSafeInteger(level)&&level>=60,rankReason:Number.isSafeInteger(level)&&level>=60?'':'RANK_UNIT_LEVEL_REQUIRED'};}
export function rankLevelEligibility(accountLevel:number|null,units:{ownedId:string;level:number}[]){
 if(accountLevel==null)return {eligible:false,reason:'ACCOUNT_LEVEL_POLICY_PENDING'};
 if(!Number.isSafeInteger(accountLevel)||accountLevel<1)return {eligible:false,reason:'ACCOUNT_LEVEL_DATA_INVALID'};
 if(accountLevel<60)return {eligible:false,reason:'RANK_ACCOUNT_LEVEL_REQUIRED'};
 if(units.some(u=>!rankUnitEligibility(u.level).rankEligible))return {eligible:false,reason:'RANK_UNIT_LEVEL_REQUIRED'};
 return {eligible:true,reason:''};
}

/** Account-wide success counter counts completed R60 rebirth operations, not unit upgrades.
 * Legacy evidence proves the hard pool, but cannot safely be added to that counter:
 * consumed units, repeated cycles and overlapping receipts are not a lifetime count.
 */
export async function readRoster60RebirthHistory(tx:Prisma.TransactionClient,playerId:string):Promise<RebirthHistory>{
 let uncertain=false,legacyUncertain=false,r60Proof=false,legacyProof=false;const r60Evidence:string[]=[],legacyEvidence:string[]=[];
 const account=await tx.roster60RebirthAccount.findUnique({where:{playerId}});
 const validCount=account&&Number.isSafeInteger(account.successes)&&account.successes>=0?account.successes:null;
 if(account&&validCount===null)uncertain=true;
 if(validCount!==null&&validCount>0)r60Proof=true;
 const audit=await tx.auditEvent.findFirst({where:{playerId,kind:'REINCARNATION'},select:{id:true},orderBy:{id:'desc'}});if(audit)legacyProof=true;
 // Entire account history, including consumed rows; never just the deployed party.
 const r60Owned=await tx.roster60Owned.findFirst({where:{playerId,reincarnation:{gt:0}},select:{id:true,reincarnation:true},orderBy:{id:'asc'}});if(r60Owned)r60Proof=true;
 const legacyOwned=await tx.ownedCharacter.findFirst({where:{playerId,OR:[{reincarnation:{gt:0}},{obtainedVia:'REINCARNATION'}]},select:{id:true,reincarnation:true,obtainedVia:true},orderBy:{id:'desc'}});if(legacyOwned)legacyProof=true;
 const receipts=await tx.roster60RebirthReceipt.findMany({where:{playerId},select:{result:true}});
 for(const row of receipts)try{const value=JSON.parse(row.result);if(value.completed===true&&Number.isSafeInteger(value.preview?.next?.reincarnation)&&value.preview.next.reincarnation>0){r60Proof=true;r60Evidence.push(row.result);}else if(value.completed!==false){uncertain=true;r60Evidence.push(row.result);}}catch{uncertain=true;r60Evidence.push(row.result);}
 const legacy=await tx.progressionReceipt.findMany({where:{playerId,fingerprint:{contains:'reincarnate'}},select:{fingerprint:true,result:true}});
 for(const row of legacy)try{const request=JSON.parse(row.fingerprint),value=JSON.parse(row.result);if(request.action==='reincarnate'){if(value.ok===true&&Number.isSafeInteger(value.reincarnation)&&value.reincarnation>0){legacyProof=true;legacyEvidence.push(JSON.stringify([row.fingerprint,row.result]));}else if(value.ok!==false){legacyUncertain=true;legacyEvidence.push(JSON.stringify([row.fingerprint,row.result]));}}}catch{legacyUncertain=true;legacyEvidence.push(JSON.stringify([row.fingerprint,row.result]));}
 const historyStamp=createHash('sha256').update(JSON.stringify([account?.successes??null,audit??null,r60Owned??null,legacyOwned??null,r60Evidence.sort(),legacyEvidence.sort()])).digest('hex');
 if(r60Proof||legacyProof){
  if(legacyProof||legacyUncertain)return {historyStamp,pool:'REBORN',count:null,countSource:'MIXED_LEGACY_HISTORY'};
  if(uncertain)return {historyStamp,pool:'REBORN',count:null,countSource:'INVALID_OR_INCOMPLETE_HISTORY'};
  if(validCount!==null&&validCount>0)return {historyStamp,pool:'REBORN',count:validCount,countSource:'ROSTER60_ACCOUNT_SUCCESSES'};
  return {historyStamp,pool:'REBORN',count:null,countSource:'HISTORY_WITHOUT_ACCOUNT_COUNT'};
 }
 return uncertain||legacyUncertain?{historyStamp,pool:null,count:null,countSource:'INVALID_OR_INCOMPLETE_HISTORY'}:{historyStamp,pool:'FIRST_LIFE',count:0,countSource:'NO_REBIRTH_HISTORY'};
}
export async function readRoster60RankPool(tx:Prisma.TransactionClient,playerId:string):Promise<RankPool|null>{return (await readRoster60RebirthHistory(tx,playerId)).pool;}
export const readRoster60MatchPool=readRoster60RankPool;

/** Unknown is not zero. The queue's outer oldest-first loop still gives unknown
 * accounts their own selection turn; their candidate search uses the old MMR rule.
 * Known seekers prefer distance within the existing eligible MMR/tier range only.
 */
export function orderRebirthCandidates<T extends {playerId:string;rating:number;queuedAt:Date;reincarnationCount:number|null}>(seeker:{rating:number;reincarnationCount:number|null},candidates:T[]):T[]{
 const baseline=(a:T,b:T)=>Math.abs(seeker.rating-a.rating)-Math.abs(seeker.rating-b.rating)||a.queuedAt.getTime()-b.queuedAt.getTime()||(a.playerId<b.playerId?-1:a.playerId>b.playerId?1:0);
 const valid=(count:number|null):count is number=>count!==null&&Number.isSafeInteger(count)&&count>=0;
 const known=valid(seeker.reincarnationCount)?candidates.filter(c=>valid(c.reincarnationCount)):[];
 if(!known.length)return [...candidates].sort(baseline);
 return known.sort((a,b)=>Math.abs(seeker.reincarnationCount!-a.reincarnationCount!)-Math.abs(seeker.reincarnationCount!-b.reincarnationCount!)||baseline(a,b));
}

/** Count/source belong to an immutable internal queue/room pin, never HTTP input. */
export function validRebirthCountPin(pool:string|null|undefined,count:number|null|undefined,source:string|null|undefined):boolean{
 if(pool==='FIRST_LIFE')return count===0&&source==='NO_REBIRTH_HISTORY';
 if(pool!=='REBORN')return false;
 if(source==='ROSTER60_ACCOUNT_SUCCESSES')return count!==null&&count!==undefined&&Number.isSafeInteger(count)&&count>0;
 return count===null&&['HISTORY_WITHOUT_ACCOUNT_COUNT','MIXED_LEGACY_HISTORY','INVALID_OR_INCOMPLETE_HISTORY'].includes(source??'');
}

/** Empty ownedIds evaluates only the account gate for pre-selection options. */
export async function evaluateRoster60Rank(tx:Prisma.TransactionClient,playerId:string,ownedIds:string[],catalog:RosterCatalog,reader:AccountLevelReader=unknownAccountLevel,options:{allowedStatuses?:string[]}={}):Promise<RankEligibility>{
 const accountLevel=await reader(tx,playerId),units:RankEligibility['units']=[];
 const base={accountLevel:accountLevel??null,requiredAccountLevel:60 as const,requiredUnitLevel:60 as const,pool:null as RankPool|null,scope:'rank-only' as const,policyVersion:roster60RankPolicyVersion,units};
 if(new Set(ownedIds).size!==ownedIds.length||ownedIds.length>6)return {...base,eligible:false,reason:'INVALID_PARTY'};
 for(const ownedId of ownedIds){
  const owned=await tx.roster60Owned.findFirst({where:{id:ownedId,playerId}});
  if(!owned)return {...base,eligible:false,reason:'UNIT_NOT_FOUND'};
  if(owned.locked||options.allowedStatuses&&!options.allowedStatuses.includes(owned.status))return {...base,eligible:false,reason:'UNIT_UNAVAILABLE'};
  const definition=catalog.characters.find(u=>u.id===owned.speciesId);
  if(!definition)return {...base,eligible:false,reason:'RANK_CATALOG_CHANGED'};
  if(owned.totalXp<0n||owned.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return {...base,eligible:false,reason:'INVALID_XP'};
  let level:number;try{level=growthAt(Number(owned.totalXp),definition.growth,owned.reincarnation,definition.reincarnationXpPolicy).level;}catch{return {...base,eligible:false,reason:'RANK_CATALOG_CHANGED'};}
  units.push({ownedId,level,...rankUnitEligibility(level)});
 }
 const gate=rankLevelEligibility(accountLevel,units);if(!gate.eligible)return {...base,...gate};
 const pool=await readRoster60RankPool(tx,playerId);if(!pool)return {...base,eligible:false,reason:'RANK_REBIRTH_HISTORY_PENDING'};
 return {...base,eligible:true,reason:'',pool};
}

export function rankAdmission(playerId:string,ownedIds:string[],catalogVersion:string,eligibility:RankEligibility):RankAdmission|null{
 return eligibility.eligible&&eligibility.pool&&eligibility.accountLevel!==null?{playerId,pool:eligibility.pool,accountLevel:eligibility.accountLevel,ownedIds:[...ownedIds],catalogVersion}:null;
}
export function readRankAdmission(raw:string|null|undefined):RankAdmission|null{
 if(!raw)return null;try{const pin=JSON.parse(raw) as RankAdmission;return typeof pin.playerId==='string'&&['FIRST_LIFE','REBORN'].includes(pin.pool)&&Number.isSafeInteger(pin.accountLevel)&&pin.accountLevel>=60&&Array.isArray(pin.ownedIds)&&pin.ownedIds.length>=1&&pin.ownedIds.length<=6&&new Set(pin.ownedIds).size===pin.ownedIds.length&&pin.ownedIds.every(id=>typeof id==='string'&&id.length>0)&&typeof pin.catalogVersion==='string'?pin:null;}catch{return null;}
}
