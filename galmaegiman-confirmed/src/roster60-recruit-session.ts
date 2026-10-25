import {createHash,randomInt} from 'node:crypto';
import type {PrismaClient,Prisma} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {recruitmentGrowth} from './roster60-library.js';
export interface RecruitSessionPolicy {
 version:string;
 offers:{kind:string;label:string;cost:number;weights:Record<string,number>}[];
 halfRounding:'ceil'|'floor'|null;
 // Deliberate boundary: no silent inheritance of legacy pity.
 pity:'none'|null;
}
type Snapshot={batchId?:string;batchIndex?:number;batchSize?:number;catalogVersion:string;policyVersion:string;kind:string;baseCost:number;rerollCost:number;rerolls:number;original:any;candidate:any;pool:any[];ownedId?:string};
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
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>RecruitSessionPolicy|null,private roll:(max:number)=>number=randomInt){}
 private ready(){const p=this.policy();if(!p||p.pity===null)throw new RosterError(503,'RECRUIT_SESSION_POLICY_PENDING');
 if(p.pity!=='none'||!p.version||!Array.isArray(p.offers)||![null,'ceil','floor'].includes(p.halfRounding))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 // Latest pools: no COMMON in MID, no COMMON/UNCOMMON in HIGH; top four remain excluded.
 const allowed:Record<string,string[]>={LOW:['COMMON','UNCOMMON','RARE'],MID:['UNCOMMON','RARE','EPIC'],HIGH:['RARE','EPIC','LEGEND','HIDDEN']};
 if(new Set(p.offers.map(o=>o.kind)).size!==p.offers.length||p.offers.some(o=>!allowed[o.kind]||Object.keys(o.weights).some(g=>!allowed[o.kind].includes(g))))throw new RosterError(503,'RECRUIT_POLICY_INVALID');
 for(const o of p.offers){halfRecruitCost(o.cost,p.halfRounding);recruitWeightTickets(o.weights);}
 return p;}
 private view(row:{id:string;revision:number;status:string;snapshot:string}){const s:Snapshot=JSON.parse(row.snapshot);return {batchId:s.batchId??row.id,batchIndex:s.batchIndex??0,batchSize:s.batchSize??1,sessionId:row.id,revision:row.revision,status:row.status,kind:s.kind,baseCost:s.baseCost,rerollCost:s.rerollCost,rerolls:s.rerolls,candidate:s.candidate,ownedId:s.ownedId,catalogVersion:s.catalogVersion};}
 async read(id:string,sessionId:string){const row=await this.db.roster60RecruitSession.findFirst({where:{id:sessionId,playerId:id}});if(!row)throw new RosterError(404,'RECRUIT_SESSION_NOT_FOUND');return this.view(row);}
 async info(id:string){const configured=this.policy();const p=configured&&configured.pity!==null?this.ready():configured;return {catalogVersion:this.catalog()?.version,enabled:!!p&&p.pity!==null,reason:p&&p.pity!==null?'':'RECRUIT_SESSION_POLICY_PENDING',offers:p?.offers.map(o=>({kind:o.kind,label:o.label,cost:o.cost,weights:{...o.weights},rerollCost:o.cost%2&&!p.halfRounding?null:halfRecruitCost(o.cost,p.halfRounding)}))??[],sessions:(await this.db.roster60RecruitSession.findMany({where:{playerId:id,status:'PENDING'},orderBy:{createdAt:'asc'}})).map(r=>this.view(r)).sort((a,b)=>a.batchId===b.batchId?a.batchIndex-b.batchIndex:0)};}
 private async transact(playerId:string,requestId:string,payload:unknown,run:(tx:Prisma.TransactionClient)=>Promise<unknown>){
 const fingerprint=createHash('sha256').update(JSON.stringify(['candidate-v1',payload])).digest('hex');
 const once=()=>this.db.$transaction(async tx=>{
  const old=await tx.roster60RecruitReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});
  if(old){if(old.fingerprint!==fingerprint)throw new RosterError(409,'REQUEST_ID_REUSED');return JSON.parse(old.result);}
  const result={...await run(tx) as Record<string,unknown>,requestId};await tx.roster60RecruitReceipt.create({data:{playerId,requestId,fingerprint,result:JSON.stringify(result)}});return result;
 });
 for(let attempt=0;;attempt++){try{return await once();}catch(e){if(attempt<2&&['P2002','P2034'].includes((e as any).code))continue;throw e;}}
 }
 private async charge(tx:Prisma.TransactionClient,id:string,cost:number){
 const p=await tx.player.findUniqueOrThrow({where:{id}});
 const unlimited=p.isAdmin&&p.adminMode;
 if(!unlimited&&p.credits<cost)throw new RosterError(409,'INSUFFICIENT_CREDITS');
 const changed=await tx.player.updateMany({where:{id,revision:p.revision,...(unlimited?{}:{credits:{gte:cost}})},data:{...(unlimited?{}:{credits:{decrement:cost}}),revision:{increment:1}}});
 if(changed.count!==1)throw new RosterError(409,'RECRUIT_RETRY');return unlimited?p.credits:p.credits-cost;
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
  const credits=await this.charge(tx,id,total);
  if(await tx.roster60RecruitSession.findFirst({where:{playerId:id,status:'PENDING'}}))throw new RosterError(409,'RECRUIT_SESSION_PENDING');
  const sessions=[];for(let index=0;index<times;index++){
  let n=this.roll(10000),grade=weights[0][0];for(const [g,w] of weights){if(n<w){grade=g;break;}n-=w;}
  const pool=c.characters.filter(u=>u.rarity===grade).map(u=>({speciesId:u.id,name:u.name,rarity:u.rarity,...recruitmentGrowth(u)}));
  if(!pool.length)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
  const candidate=pool[this.roll(pool.length)];
  const snapshot:Snapshot={batchId:b.requestId,batchIndex:index,batchSize:times,catalogVersion:c.version,policyVersion:p.version,kind:b.kind,baseCost:o.cost,rerollCost,rerolls:0,original:candidate,candidate,pool};
  const row=await tx.roster60RecruitSession.create({data:{playerId:id,snapshot:JSON.stringify(snapshot)}});sessions.push(this.view(row));}
  return {...sessions[0],sessions,credits};
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
 return {sessions:results,credits:(await tx.player.findUniqueOrThrow({where:{id}})).credits};
 });
 }
 async action(id:string,b:{requestId:string;sessionId:string;revision:number;action:'reroll'|'claim'}){return this.transact(id,b.requestId,[b.action,b.sessionId,b.revision],async tx=>{
  const row=await tx.roster60RecruitSession.findFirst({where:{id:b.sessionId,playerId:id}});if(!row)throw new RosterError(404,'RECRUIT_SESSION_NOT_FOUND');
  if(row.revision!==b.revision||row.status!=='PENDING')throw new RosterError(409,'STALE_REVISION');
  const s:Snapshot=JSON.parse(row.snapshot);
  if(b.action==='reroll'&&s.rerolls!==0)throw new RosterError(409,'RECRUIT_REROLL_USED');
  const lock=await tx.roster60RecruitSession.updateMany({where:{id:row.id,playerId:id,revision:b.revision,status:'PENDING'},data:{revision:{increment:1}}});if(lock.count!==1)throw new RosterError(409,'STALE_REVISION');
  let credits=(await tx.player.findUniqueOrThrow({where:{id}})).credits;
  if(b.action==='reroll'){
   credits=await this.charge(tx,id,s.rerollCost);s.candidate=s.pool[this.roll(s.pool.length)];s.rerolls=1;
  }else{
   const owned=await tx.roster60Owned.create({data:{playerId:id,speciesId:s.candidate.speciesId,totalXp:BigInt(s.candidate.totalXp),source:'recruit-session:'+row.id}});s.ownedId=owned.id;
  }
  const next=await tx.roster60RecruitSession.update({where:{id:row.id},data:{snapshot:JSON.stringify(s),status:b.action==='claim'?'CLAIMED':'PENDING'}});
  return {...this.view(next),credits};
 });}
}
