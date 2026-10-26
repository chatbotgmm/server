import {fiveRebirthPolicy,fiveRebirthStats,type FiveStats} from './battle/roster60-five-stats.js';
import {publicDefenseSafe} from './battle/roster60-observed-defense.js';
import {previewReincarnation} from './battle/roster60-reincarnation.js';
import type {PrismaClient,Prisma} from '@prisma/client';
import {createHash} from 'node:crypto';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {previewReincarnationCycle,requiredReincarnationLevel,type BaseSixStats} from './battle/roster60-reincarnation.js';
import {growthAt,validateRosterDefinition} from './battle/roster60-rules.js';
export const rebirthGradePolicy=Object.freeze({version:'limited-transcend-immortal-eternal-v1',grades:Object.freeze(['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'])});
export type RebirthRequest={ownedId:string;requestId:string;catalogVersion:string;previewFingerprint:string};
const hash=(v:unknown)=>createHash('sha256').update(JSON.stringify(v)).digest('hex');
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
export function rebirthCost(){const n=Number(process.env.ROSTER60_REBIRTH_PEANUT_COST??1);if(!Number.isSafeInteger(n)||n<0)throw Error('INVALID_REBIRTH_COST');return n;}
export class RosterReincarnation {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
 private async plan(tx:Prisma.TransactionClient,playerId:string,ownedId:string){
  const c=this.catalog();if(!c?.approved)return fail('ROSTER_CATALOG_PENDING',503);
  const u=await tx.roster60Owned.findFirst({where:{id:ownedId,playerId}});if(!u)return fail('UNIT_NOT_FOUND',404);
  if(u.reincarnation>=5)return fail('REINCARNATION_LIMIT');
  if(u.status!=='AVAILABLE'||u.locked)return fail('UNIT_UNAVAILABLE');
  const d=c.characters.find(x=>x.id===u.speciesId);if(!d)return fail('ROSTER_CATALOG_INVALID',503);validateRosterDefinition(d);
  // Explicit approved three-stat policy only; never fall through to the archived six-stat bonus.
  if(c.statSchema==='gmm-five-stats-v1'&&c.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);
  const base=(d as typeof d&{levelOneStats?:BaseSixStats}).levelOneStats;if(!base)return fail('LEVEL_ONE_BASE_STATS_REQUIRED',503);
  if(base.mp!==d.growth[0].maxMp)return fail('BASE_MP_MISMATCH',503);
  if(u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
  const before=growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy);
  const preview={beforeLevel:before.level,...(c.statSchema==='gmm-five-stats-v1'?(()=>{const original=base as unknown as FiveStats;const after=fiveRebirthStats(original,original,u.reincarnation+1);return {...previewReincarnation({totalXp:Number(u.totalXp),reincarnation:u.reincarnation,baseLevelOneStats:original,curve:d.growth},{approved:true,version:fiveRebirthPolicy,bonusFor:()=>Object.fromEntries(Object.keys(original).map(k=>[k,after[k as keyof FiveStats]-original[k as keyof FiveStats]]))}),suitability:structuredClone(d.suitability),defense:structuredClone(d.defense)};})():previewReincarnationCycle({totalXp:Number(u.totalXp),reincarnation:u.reincarnation,baseLevelOneStats:base,curve:d.growth,suitability:d.suitability,defense:d.defense}))};
  const account=await tx.roster60RebirthAccount.findUnique({where:{playerId}}),successes=account?.successes??0,cost=successes===0?0:rebirthCost();
  const requiredLevel=requiredReincarnationLevel(u.reincarnation);
  const policy=rebirthGradePolicy;let reason='';
  if(!policy.grades.includes(d.rarity))reason='REINCARNATION_GRADE_NOT_ALLOWED';
  else if(before.level<requiredLevel)reason='REINCARNATION_REQUIREMENTS_NOT_MET';
  // The public proof must not become a brute-force oracle for the hidden defense table.
  const fingerprint=hash([c.version,u.id,u.speciesId,u.totalXp.toString(),u.reincarnation,successes,cost,policy,requiredLevel,publicDefenseSafe(preview)]);
  return {ownedId:u.id,speciesId:u.speciesId,name:d.name,catalogVersion:c.version,eligible:!reason,reason,requiredLevel,preview,costPeanut:cost,accountFreeAvailable:successes===0,successes,previewFingerprint:fingerprint};
 }
 async preview(playerId:string){
  const c=this.catalog();if(!c?.approved)return fail('ROSTER_CATALOG_PENDING',503);
  const units=await this.db.roster60Owned.findMany({where:{playerId,status:'AVAILABLE'},take:1000,orderBy:{id:'asc'}});
  const candidates=[];for(const u of units){try{candidates.push(await this.plan(this.db,playerId,u.id));}catch(e){candidates.push({ownedId:u.id,name:c.characters.find(d=>d.id===u.speciesId)?.name??u.speciesId,eligible:false,reason:e instanceof RosterError?e.code:(e as Error).message,preview:null});}}
  return publicDefenseSafe({serverNow:Date.now(),flow:'roster60-v1',catalogVersion:c.version,candidates});
 }
 async confirm(playerId:string,input:RebirthRequest){
  const fingerprint=hash(input),run=()=>this.db.$transaction(async tx=>{
   const prior=await tx.roster60RebirthReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:input.requestId}}});
   if(prior){
    if(prior.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');
    const result=JSON.parse(prior.result);
    // Older completed receipts hashed the secret table. Do not replay that public oracle.
    if(result.preview?.defense&&typeof result.preview.defense==='object')delete result.previewFingerprint;
    return publicDefenseSafe(result);
   }
   const p=await this.plan(tx,playerId,input.ownedId);if(!p.eligible)return fail(p.reason);
   if(input.catalogVersion!==p.catalogVersion||input.previewFingerprint!==p.previewFingerprint)return fail('PREVIEW_CHANGED');
   if(await tx.realtimeActivity.findFirst({where:{playerId,OR:[{kind:'ROOM'},{expiresAt:{gt:new Date()}}]}}))return fail('BATTLE_ACTIVE');
   await tx.roster60RebirthAccount.upsert({where:{playerId},create:{playerId},update:{}});
   const account=await tx.roster60RebirthAccount.updateMany({where:{playerId,successes:p.successes},data:{successes:{increment:1}}});if(account.count!==1)return fail('PREVIEW_CHANGED');
   const paid=await tx.player.updateMany({where:{id:playerId,peanut:{gte:p.costPeanut}},data:{peanut:{decrement:p.costPeanut},revision:{increment:1}}});if(paid.count!==1)return fail('INSUFFICIENT_PEANUT');
   const updated=await tx.roster60Owned.updateMany({where:{id:input.ownedId,playerId,status:'AVAILABLE',locked:false,totalXp:BigInt(p.preview.previousCycleTotalXp),reincarnation:p.preview.next.reincarnation-1},data:{reincarnation:p.preview.next.reincarnation,totalXp:0n}});if(updated.count!==1)return fail('UNIT_CHANGED');
   const result=publicDefenseSafe({...p,paidPeanut:p.costPeanut,completed:true});
   await tx.roster60RebirthReceipt.create({data:{playerId,requestId:input.requestId,fingerprint,result:JSON.stringify(result)}});return result;
  });
  try{return await run();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return run();throw e;}
 }
}
