import {createHash,randomInt} from 'node:crypto';
import type {PrismaClient,Prisma} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {recruitmentGrowth} from './roster60-library.js';
import {recordRosterDailyProgress} from './roster60-daily-progress.js';
export interface RecruitSessionPolicy {
 version:string;
 offers:{kind:string;label:string;cost:number;weights:Record<string,number>}[];
 halfRounding:'ceil'|'floor'|null;
 // Deliberate boundary: no silent inheritance of legacy pity.
 pity:'none'|null;
}
type Snapshot={batchId?:string;batchIndex?:number;batchSize?:number;catalogVersion:string;policyVersion:string;kind:string;baseCost:number;rerollCost:number;rerolls:number;original:any;candidate:any;pool:any[];ownedId?:string;paidBaseCost?:number;refundedTickets?:number};
export function halfRecruitCost(cost:number,rounding:RecruitSessionPolicy['halfRounding']){
 if(!Number.isSafeInteger(cost)||cost<=0)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 if(cost%2&&!rounding)throw new RosterError(503,'RECRUIT_ROUNDING_PENDING');
 return rounding==='floor'?Math.floor(cost/2):Math.ceil(cost/2);
}
// Integer basis-point tickets avoid passing fractional bounds to crypto.randomInt.
export function recruitWeightTickets(weights:Record<string,number>):[string,number][]{
 if(!weights||typeof weights!=='object'||Array.isArray(weights))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 const entries=Object.entries(weights).map(([g,w]):[string,number]=>{
  const scaled=w*100,tickets=Math.round(scaled);
  if(typeof w!=='number'||!Number.isFinite(w)||w<=0||w>100||!Number.isSafeInteger(tickets)||Math.abs(scaled-tickets)>1e-8)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
  return [g,tickets];
 });
 if(!entries.length||entries.reduce((sum,[,n])=>sum+n,0)!==10000)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 return entries;
}
export class RecruitSessions {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>RecruitSessionPolicy|null,private roll:(max:number)=>number=randomInt,private completionTime=()=>new Date()){}
 private ready(){const p=this.policy();if(!p||p.pity===null)throw new RosterError(503,'RECRUIT_SESSION_POLICY_PENDING');
 if(p.pity!=='none'||!p.version||!Array.isArray(p.offers)||![null,'ceil','floor'].includes(p.halfRounding))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 // Latest pools: no COMMON in MID, no COMMON/UNCOMMON in HIGH; top four remain excluded.
 const allowed:Record<string,string[]>={LOW:['COMMON','UNCOMMON','RARE'],MID:['UNCOMMON','RARE','EPIC'],HIGH:['RARE','EPIC','LEGEND','HIDDEN']};
 if(new Set(p.offers.map(o=>o.kind)).size!==p.offers.length||p.offers.some(o=>!allowed[o.kind]||Object.keys(o.weights).some(g=>!allowed[o.kind].includes(g))))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 for(const o of p.offers){halfRecruitCost(o.cost,p.halfRounding);recruitWeightTickets(o.weights);}
 return p;}
 private view(row:{id:string;revision:number;status:string;snapshot:string}){const s:Snapshot=JSON.parse(row.snapshot);return {batchId:s.batchId??row.id,batchIndex:s.batchIndex??0,batchSize:s.batchSize??1,sessionId:row.id,revision:row.revision,status:row.status,kind:s.kind,baseCost:s.baseCost,rerollCost:s.rerollCost,rerolls:s.rerolls,candidate:s.candidate,ownedId:s.ownedId,catalogVersion:s.catalogVersion,refundedTickets:s.refundedTickets??0};}
 private refundBlock(s:Snapshot){
  const paid=s.paidBaseCost;
  if(typeof paid!=='number'||!Number.isSafeInteger(paid))return 'PAYMENT_PROOF_UNAVAILABLE';
  if(paid===0)return 'FREE_GENERATION';
  // A candidate may return one ticket once. Never make generation refundable at a profit.
  if(paid<2)return 'REFUND_COST_TOO_LOW';
  return '';
 }
 private async state(tx:Prisma.TransactionClient,id:string){
  const [player,owned,rows]=await Promise.all([
   tx.player.findUniqueOrThrow({where:{id},select:{credits:true}}),
   tx.roster60Owned.findMany({where:{playerId:id,status:{not:'CONSUMED'}},select:{speciesId:true},distinct:['speciesId']}),
   tx.roster60RecruitSession.findMany({where:{playerId:id,status:'PENDING'},orderBy:[{createdAt:'asc'},{id:'asc'}]})
  ]);
  const species=new Set(owned.map(o=>o.speciesId)),sources=new Map(rows.map(r=>[r.id,JSON.parse(r.snapshot) as Snapshot]));
  const decorate=(view:any)=>{const ownedSpecies=species.has(view.candidate?.speciesId),source=sources.get(view.sessionId);
   const rejectReason=view.status!=='PENDING'||!source?'NOT_PENDING':!ownedSpecies?'NOT_OWNED_SPECIES':this.refundBlock(source);
   const canReject=rejectReason==='';return {...view,ownedSpecies,canReject,rejectRefund:canReject?1:0,rejectReason};};
  const pendingSessions=rows.map(r=>decorate(this.view(r))).sort((a,b)=>a.batchId===b.batchId?a.batchIndex-b.batchIndex:0);
  return {credits:player.credits,balances:{applicationTickets:player.credits},pendingSessions,decorate};
 }
 private async response(tx:Prisma.TransactionClient,id:string,result:Record<string,any>){
  const {decorate,...state}=await this.state(tx,id);
  return {...result,...(result.candidate?decorate(result):{}),...(Array.isArray(result.sessions)?{sessions:result.sessions.map(decorate)}:{}),...state};
 }
 async read(id:string,sessionId:string){return this.db.$transaction(async tx=>{const row=await tx.roster60RecruitSession.findFirst({where:{id:sessionId,playerId:id}});if(!row)throw new RosterError(404,'RECRUIT_SESSION_NOT_FOUND');return this.response(tx,id,this.view(row));});}
 async info(id:string){const configured=this.policy();const p=configured&&configured.pity!==null?this.ready():configured;return this.db.$transaction(async tx=>{const {decorate,...state}=await this.state(tx,id);return {catalogVersion:this.catalog()?.version,enabled:!!p&&p.pity!==null,reason:p&&p.pity!==null?'':'RECRUIT_SESSION_POLICY_PENDING',offers:p?.offers.map(o=>({kind:o.kind,label:o.label,cost:o.cost,weights:{...o.weights},rerollCost:o.cost%2&&!p.halfRounding?null:halfRecruitCost(o.cost,p.halfRounding)}))??[],...state,sessions:state.pendingSessions};});}

 private async transact(playerId:string,requestId:string,payload:unknown,run:(tx:Prisma.TransactionClient)=>Promise<unknown>){
 const fingerprint=createHash('sha256').update(JSON.stringify(['candidate-v1',payload])).digest('hex');
 const once=()=>this.db.$transaction(async tx=>{
  const old=await tx.roster60RecruitReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});
  if(old){if(old.fingerprint!==fingerprint)throw new RosterError(409,'REQUEST_ID_REUSED');const saved=JSON.parse(old.result);if(saved.receipt?.action==='reject')saved.receipt={...saved.receipt,replayed:true};return this.response(tx,playerId,saved);}
  const result=await this.response(tx,playerId,{...await run(tx) as Record<string,unknown>,requestId});await tx.roster60RecruitReceipt.create({data:{playerId,requestId,fingerprint,result:JSON.stringify(result)}});return result;
 });
 for(let attempt=0;;attempt++){try{return await once();}catch(e){if(attempt<2&&['P2002','P2034'].includes((e as any).code))continue;throw e;}}
 }
 private async charge(tx:Prisma.TransactionClient,id:string,cost:number){
 const p=await tx.player.findUniqueOrThrow({where:{id}});
 const unlimited=p.isAdmin&&p.adminMode;
 if(!unlimited&&p.credits<cost)throw new RosterError(409,'INSUFFICIENT_CREDITS');
 const changed=await tx.player.updateMany({where:{id,revision:p.revision,...(unlimited?{}:{credits:{gte:cost}})},data:{...(unlimited?{}:{credits:{decrement:cost}}),revision:{increment:1}}});
 if(changed.count!==1)throw new RosterError(409,'RECRUIT_RETRY');return {credits:unlimited?p.credits:p.credits-cost,charged:unlimited?0:cost};
 }
 async start(id:string,b:{requestId:string;kind:string;catalogVersion:string;times?:number}){
 const times=b.times??1;if(!Number.isSafeInteger(times)||times<1||times>50)throw new RosterError(400,'INVALID_REQUEST');
 // Keep historical single-candidate receipt fingerprints stable.
 return this.transact(id,b.requestId,times===1?['start',b.kind,b.catalogVersion]:['start',b.kind,b.catalogVersion,times],async tx=>{
  const p=this.ready(),c=this.catalog();if(!c?.approved)throw new RosterError(503,'ROSTER_CATALOG_PENDING');
  if(c.version!==b.catalogVersion)throw new RosterError(409,'CATALOG_CHANGED');
  if(!await tx.roster60State.findUnique({where:{playerId:id}}))throw new RosterError(409,'ROSTER_START_REQUIRED');
  const o=p.offers.find(o=>o.kind===b.kind);if(!o)throw new RosterError(409,'RECRUIT_GRADE_POLICY_PENDING');
  const rerollCost=halfRecruitCost(o.cost,p.halfRounding),weights=recruitWeightTickets(o.weights);
  // Account lock prevents parallel starts from creating two open rounds.
  const total=o.cost*times;if(!Number.isSafeInteger(total))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
  const charged=await this.charge(tx,id,total),credits=charged.credits;
  if(await tx.roster60RecruitSession.findFirst({where:{playerId:id,status:'PENDING'}}))throw new RosterError(409,'RECRUIT_SESSION_PENDING');
  const sessions=[];for(let index=0;index<times;index++){
  let n=this.roll(10000),grade=weights[0][0];for(const [g,w] of weights){if(n<w){grade=g;break;}n-=w;}
  const pool=c.characters.filter(u=>u.rarity===grade).map(u=>({speciesId:u.id,name:u.name,rarity:u.rarity,...recruitmentGrowth(u)}));
  if(!pool.length)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
  const candidate=pool[this.roll(pool.length)];
  const snapshot:Snapshot={batchId:b.requestId,batchIndex:index,batchSize:times,catalogVersion:c.version,policyVersion:p.version,kind:b.kind,baseCost:o.cost,rerollCost,rerolls:0,original:candidate,candidate,pool,paidBaseCost:charged.charged?o.cost:0};
  const row=await tx.roster60RecruitSession.create({data:{playerId:id,snapshot:JSON.stringify(snapshot)}});sessions.push(this.view(row));}
  return {...sessions[0],sessions,credits,receipt:{requestId:b.requestId,action:'start',chargedTickets:charged.charged,refundedTickets:0,grantedOwnedIds:[],sessionIds:sessions.map(s=>s.sessionId)}};
 });}
 async claimAll(id:string,b:{requestId:string;sessions:{sessionId:string;revision:number}[]}){
 if(!Array.isArray(b.sessions)||b.sessions.length<1||b.sessions.length>50||new Set(b.sessions.map(s=>s.sessionId)).size!==b.sessions.length)throw new RosterError(400,'INVALID_REQUEST');
 return this.transact(id,b.requestId,['claimAll',b.sessions],async tx=>{
 const results=[];let batch:string|undefined;
 for(const item of b.sessions){
 const row=await tx.roster60RecruitSession.findFirst({where:{id:item.sessionId,playerId:id}});if(!row)throw new RosterError(404,'RECRUIT_SESSION_NOT_FOUND');
 if(row.status!=='PENDING'||row.revision!==item.revision)throw new RosterError(409,'STALE_REVISION');
 const s:Snapshot=JSON.parse(row.snapshot),group=s.batchId??row.id;if(batch!==undefined&&batch!==group)throw new RosterError(400,'INVALID_REQUEST');batch=group;
 const lock=await tx.roster60RecruitSession.updateMany({where:{id:row.id,playerId:id,revision:item.revision,status:'PENDING'},data:{revision:{increment:1},status:'CLAIMED'}});if(lock.count!==1)throw new RosterError(409,'STALE_REVISION');
 const owned=await tx.roster60Owned.create({data:{playerId:id,speciesId:s.candidate.speciesId,totalXp:BigInt(s.candidate.totalXp),source:'recruit-session:'+row.id}});s.ownedId=owned.id;
 const next=await tx.roster60RecruitSession.update({where:{id:row.id},data:{snapshot:JSON.stringify(s)}});results.push(this.view(next));
 }
 await recordRosterDailyProgress(tx,id,'DRAW',results.length,this.completionTime());
 return {sessions:results,credits:(await tx.player.findUniqueOrThrow({where:{id}})).credits,receipt:{requestId:b.requestId,action:'claim-all',chargedTickets:0,refundedTickets:0,grantedOwnedIds:results.map(s=>s.ownedId!),sessionIds:results.map(s=>s.sessionId)}};
 });
 }
 async action(id:string,b:{requestId:string;sessionId:string;revision:number;action:'reroll'|'claim'|'reject'}){if(!['reroll','claim','reject'].includes(b.action))throw new RosterError(400,'INVALID_REQUEST');return this.transact(id,b.requestId,[b.action,b.sessionId,b.revision],async tx=>{
  const row=await tx.roster60RecruitSession.findFirst({where:{id:b.sessionId,playerId:id}});if(!row)throw new RosterError(404,'RECRUIT_SESSION_NOT_FOUND');
  if(row.revision!==b.revision||row.status!=='PENDING')throw new RosterError(409,'STALE_REVISION');
  const s:Snapshot=JSON.parse(row.snapshot);
  if(b.action==='reroll'&&s.rerolls!==0)throw new RosterError(409,'RECRUIT_REROLL_USED');
  if(b.action==='reject'){
   // Ownership is checked inside the same transaction as the terminal transition.
   // Consumed crafting material is not a currently owned species.
   if(!await tx.roster60Owned.findFirst({where:{playerId:id,speciesId:s.candidate.speciesId,status:{not:'CONSUMED'}},select:{id:true}}))throw new RosterError(409,'RECRUIT_NOT_OWNED_SPECIES');
   if(this.refundBlock(s))throw new RosterError(409,'RECRUIT_REFUND_UNAVAILABLE');
   const lock=await tx.roster60RecruitSession.updateMany({where:{id:row.id,playerId:id,revision:b.revision,status:'PENDING'},data:{revision:{increment:1},status:'REJECTED'}});if(lock.count!==1)throw new RosterError(409,'STALE_REVISION');
   // One fixed application ticket, never the draw/reroll price. No DRAW mission credit.
   const refund=await tx.player.updateMany({where:{id,credits:{lt:2147483647}},data:{credits:{increment:1},revision:{increment:1}}});if(refund.count!==1)throw new RosterError(409,'RECRUIT_BALANCE_LIMIT');
   s.refundedTickets=1;const next=await tx.roster60RecruitSession.update({where:{id:row.id},data:{snapshot:JSON.stringify(s)}});
   return {...this.view(next),receipt:{requestId:b.requestId,action:'reject',chargedTickets:0,refundedTickets:1,grantedOwnedIds:[],sessionIds:[row.id],replayed:false}};
  }
  const lock=await tx.roster60RecruitSession.updateMany({where:{id:row.id,playerId:id,revision:b.revision,status:'PENDING'},data:{revision:{increment:1}}});if(lock.count!==1)throw new RosterError(409,'STALE_REVISION');
  let credits=(await tx.player.findUniqueOrThrow({where:{id}})).credits,chargedTickets=0;
  if(b.action==='reroll'){
   const charged=await this.charge(tx,id,s.rerollCost);credits=charged.credits;chargedTickets=charged.charged;s.candidate=s.pool[this.roll(s.pool.length)];s.rerolls=1;
  }else{
   const owned=await tx.roster60Owned.create({data:{playerId:id,speciesId:s.candidate.speciesId,totalXp:BigInt(s.candidate.totalXp),source:'recruit-session:'+row.id}});s.ownedId=owned.id;
  }
  const next=await tx.roster60RecruitSession.update({where:{id:row.id},data:{snapshot:JSON.stringify(s),status:b.action==='claim'?'CLAIMED':'PENDING'}});
  if(b.action==='claim'){
   await recordRosterDailyProgress(tx,id,'DRAW',1,this.completionTime());
   credits=(await tx.player.findUniqueOrThrow({where:{id},select:{credits:true}})).credits;
  }
  return {...this.view(next),credits,receipt:{requestId:b.requestId,action:b.action,chargedTickets,refundedTickets:0,grantedOwnedIds:s.ownedId?[s.ownedId]:[],sessionIds:[row.id]}};
 });}
}
