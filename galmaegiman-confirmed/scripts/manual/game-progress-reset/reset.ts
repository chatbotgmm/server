/** Explicit allowlist reset. Production entrypoint additionally enforces stop, backup and confirmation. */
import {Prisma,PrismaClient} from '@prisma/client';
import {createHash,randomUUID} from 'node:crypto';
import {realpathSync,existsSync} from 'node:fs';
import {resolve,dirname,basename} from 'node:path';
import {loadContent} from '../../../src/content.js';
import {RosterOnboarding} from '../../../src/roster60-onboarding.js';

export const erased = ['Roster60PvpParty','Roster60PvpQueue','Roster60PvpMatch','Roster60RecruitSession','Roster60AdventureReceipt','Roster60Adventure','Roster60RecruitReceipt','Roster60Home','Roster60BattleReceipt','Roster60Battle','Roster60RebirthReceipt','Roster60RebirthAccount','Roster60CraftReceipt','Roster60State','Roster60Owned','NativeNewsRequest','NativeNewsRead','NativeInboxMessage','StarterLoanLevel','StarterLoanGrant','ProgressionMilestone','ProgressionReceipt','ProgressionState','RealtimeItemEscrow','RealtimeRequest','RealtimeLineup','RealtimeActivity','RealtimeAttempt','RealtimeRoom','CasualHumanSettlement','RankedSettlement','RankedMatch','CasualHumanRating','RankedProfile','PlayerItem','StorySave','DailyProgress','Achievement','PendingAction','Expedition','CollectionEntry','OwnedCharacter'] as const;
export const preserved = ['Player','AppMeta','CharacterDefinition','AuditEvent','WebLogin','KakaoLink','ChatAppUser','AdminSnapshot','Comment','CommentLike'] as const;
export const marker='game-progress-reset-v1';
export function isolatedUrl(url:string){
 if(!url.startsWith('file:'))throw Error('ISOLATED_COPY_REQUIRED');
 const path=realpathSync(url.slice(5));
 if(!realpathSync(dirname(path)).startsWith('/tmp/gmm-progress-reset-')||basename(path)!=='copy.db'||!existsSync(resolve(dirname(path),'ISOLATED_COPY')))throw Error('ISOLATED_COPY_REQUIRED');
 return path;
}
export function initial(){return {credits:loadContent().economy.tickets.welcome,snack:0,peanut:0,lastClaimAt:null,highPity:0,tutorial:0,partyDraft:'',gameTutorial:1,starterLoanEnrolled:false,starterLoanEligibleAt:null,gameSeen:'',battleTeam:'',badge:null,rankPoint:1000,rankWins:0,rankLosses:0,invitedBy:null};}
async function inspect(tx:Prisma.TransactionClient){
 const tables=await tx.$queryRawUnsafe<{name:string}[]>(`SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != '_prisma_migrations'`);
 const known=new Set<string>([...erased,...preserved]);
 const unknown=tables.map(x=>x.name).filter(x=>!known.has(x));
 const counts:Record<string,number>={};
 for(const name of [...erased,...preserved]){if(!tables.some(t=>t.name===name))throw Error('SCHEMA_MISSING:'+name);const r=await tx.$queryRawUnsafe<{n:bigint}[]>(`SELECT COUNT(*) AS n FROM "${name}"`);counts[name]=Number(r[0].n);}
 const activeAdmin=await tx.player.count({where:{adminMode:true}});
 // Never dump details: billing-related audit entries require external entitlement review.
 const billing=await tx.auditEvent.count({where:{OR:['PAYMENT','PURCHASE','BILLING','REFUND','IAP','RECEIPT'].map(k=>({kind:{contains:k}}))}});
 const digest=createHash('sha256');
 for(const name of [...erased,...preserved]){
  const rows=await tx.$queryRawUnsafe<Record<string,unknown>[]>(`SELECT * FROM "${name}" ORDER BY rowid`);
  digest.update(name);digest.update(JSON.stringify(rows,(_,v)=>typeof v==='bigint'?v.toString():v));
 }
 const blockers=[...unknown.map(n=>'UNREVIEWED_TABLE:'+n),...(activeAdmin||counts.AdminSnapshot?['ADMIN_SNAPSHOT_REQUIRES_REVIEW']:[]),...(billing?['BILLING_AUDIT_REQUIRES_REVIEW']:[])];
 return {counts,initial:initial(),blockers,fingerprint:digest.digest('hex'),alreadyReset:!!await tx.appMeta.findUnique({where:{key:marker}})};
}
export async function plan(db:PrismaClient){return db.$transaction(inspect);}
export async function applyIsolated(db:PrismaClient,url:string,expected:string,confirmation:string){
 isolatedUrl(url);
 // Verify this Prisma connection itself points to that exact copy, not merely the supplied URL.
 const paths=await db.$queryRawUnsafe<{name:string;file:string}[]>('PRAGMA database_list');
 if(realpathSync(paths.find(p=>p.name==='main')!.file)!==isolatedUrl(url))throw Error('DATABASE_CONNECTION_MISMATCH');
 if(confirmation!=='APPLY TO ISOLATED COPY ONLY')throw Error('CONFIRMATION_REQUIRED');
 return applyTransaction(db,expected,'isolated-copy');
}
export async function applyTransaction(db:PrismaClient,expected:string,scope:'isolated-copy'|'approved-production'){
 return db.$transaction(async tx=>{
  const p=await inspect(tx);if(p.alreadyReset)throw Error('RESET_ALREADY_APPLIED');
  if(p.blockers.length)throw Error(p.blockers.join(','));if(p.fingerprint!==expected)throw Error('PLAN_CHANGED');
  const ids=await tx.player.findMany({select:{id:true}}),epoch=randomUUID();
  for(const name of erased)await tx.$executeRawUnsafe(`DELETE FROM "${name}"`);
  await tx.player.updateMany({data:{...initial(),revision:{increment:1}}});
  const service=new RosterOnboarding({$transaction:async(fn:any)=>fn(tx)} as PrismaClient,()=>null);
  for(const {id} of ids){await service.start(id);await tx.auditEvent.create({data:{playerId:id,kind:'GAME_PROGRESS_RESET',detail:{epoch,scope,newRoster:true}}});}
  await tx.appMeta.create({data:{key:marker,value:JSON.stringify({epoch,at:Date.now(),scope,productionReady:scope==='approved-production'})}});
  const violations=await tx.$queryRawUnsafe<unknown[]>('PRAGMA foreign_key_check');if(violations.length)throw Error('FOREIGN_KEY_CHECK_FAILED');
  return {accounts:ids.length,epoch,productionReady:scope==='approved-production'};
 },{timeout:60000});
}
