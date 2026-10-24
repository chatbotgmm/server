import {createHash} from 'node:crypto';
import type {Prisma,PrismaClient,Roster60Adventure} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {fiveRebirthPolicy} from './battle/roster60-five-stats.js';
import {growthAt} from './battle/roster60-rules.js';
import type {Fighter,State} from './roster60-battle.js';
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
export const adventureEscapePolicy=Object.freeze({version:'adventure-escape-70-v1',successPercent:70});
// Server seed and turn determine one draw. Different request IDs cannot reroll a stale revision.
export function adventureEscapeSucceeds(seed:string,battleId:string,turn:number){if(!seed||!battleId||!Number.isSafeInteger(turn)||turn<0)throw Error('INVALID_ESCAPE_SEED');return createHash('sha256').update(JSON.stringify([adventureEscapePolicy.version,seed,battleId,turn])).digest().readUInt32BE(0)/4294967296<.7;}
export function adventureDto(row:Roster60Adventure){const state=JSON.parse(row.state);return {adventureId:row.id,revision:row.revision,status:row.status,activeBattleId:row.activeBattleId,party:state.party,pendingRewards:JSON.parse(row.pendingRewards),settlement:row.settlement?JSON.parse(row.settlement):null,escapePolicy:adventureEscapePolicy};}
export class RosterAdventure{
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private fighter:(c:RosterCatalog,species:string,level:number,id:string,cycle:number)=>Fighter){}
 private async request(playerId:string,requestId:string,payload:unknown,fn:(tx:Prisma.TransactionClient)=>Promise<unknown>){const fingerprint=createHash('sha256').update(JSON.stringify(payload)).digest('hex');const run=()=>this.db.$transaction(async tx=>{const old=await tx.roster60AdventureReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});if(old){if(old.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');return JSON.parse(old.result);}const result=await fn(tx);await tx.roster60AdventureReceipt.create({data:{playerId,requestId,fingerprint,result:JSON.stringify(result)}});return result;});try{return await run();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return run();throw e;}}
 async active(playerId:string){return {adventures:(await this.db.roster60Adventure.findMany({where:{playerId,status:'ACTIVE'},orderBy:{createdAt:'desc'}})).map(adventureDto)};}
 async read(playerId:string,id:string){const row=await this.db.roster60Adventure.findFirst({where:{id,playerId}});if(!row)return fail('ADVENTURE_NOT_FOUND',404);return adventureDto(row);}
 async start(playerId:string,b:{requestId:string;ownedIds:string[]}){return this.request(playerId,b.requestId,['start',b.ownedIds],async tx=>{
  const c=this.catalog();if(c?.approved!==true)return fail('ROSTER_CATALOG_PENDING',503);
  if(!b.ownedIds.length||b.ownedIds.length>6||new Set(b.ownedIds).size!==b.ownedIds.length)return fail('INVALID_PARTY',400);
  const party:Fighter[]=[],members=[];
  for(const id of b.ownedIds){const u=await tx.roster60Owned.findFirst({where:{id,playerId,status:'AVAILABLE',locked:false}});if(!u)return fail('UNIT_UNAVAILABLE');if(u.reincarnation&&c.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);const d=c.characters.find(x=>x.id===u.speciesId);if(!d)return fail('BATTLE_UNIT_DATA_PENDING',503);if(u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
   const lv=growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy).level;party.push({...this.fighter(c,u.speciesId,lv,'self',u.reincarnation),ownedId:id});members.push({ownedId:id,startingXp:u.totalXp.toString(),growth:d.growth,reincarnation:u.reincarnation,reincarnationXpPolicy:d.reincarnationXpPolicy});
   const changed=await tx.roster60Owned.updateMany({where:{id,playerId,status:'AVAILABLE',locked:false,totalXp:u.totalXp},data:{status:'ADVENTURE'}});if(changed.count!==1)return fail('UNIT_UNAVAILABLE');
  }
  return adventureDto(await tx.roster60Adventure.create({data:{playerId,catalogVersion:c.version,state:JSON.stringify({party,members}),pendingRewards:JSON.stringify({xp:{}})}}));
 });}
 async return(playerId:string,b:{requestId:string;adventureId:string;revision:number}){return this.request(playerId,b.requestId,['return',b.adventureId,b.revision],async tx=>{
  const row=await tx.roster60Adventure.findFirst({where:{id:b.adventureId,playerId}});if(!row)return fail('ADVENTURE_NOT_FOUND',404);if(row.status!=='ACTIVE')return adventureDto(row);if(row.revision!==b.revision)return fail('STALE_REVISION');if(row.activeBattleId)return fail('RETURN_ONLY_OUTSIDE_BATTLE');
  const state=JSON.parse(row.state),rewards=JSON.parse(row.pendingRewards);
  for(const m of state.members){const amount=rewards.xp[m.ownedId]??0;if(!Number.isSafeInteger(amount)||amount<0)return fail('INVALID_REWARD');const next=BigInt(m.startingXp)+BigInt(amount);if(next>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');try{growthAt(Number(next),m.growth,m.reincarnation,m.reincarnationXpPolicy);}catch(e){return fail((e as Error).message);}
   const changed=await tx.roster60Owned.updateMany({where:{id:m.ownedId,playerId,status:'ADVENTURE',totalXp:BigInt(m.startingXp)},data:{status:'AVAILABLE',totalXp:next}});if(changed.count!==1)return fail('UNIT_STATE_CHANGED');
  }
  const settlement={kind:'normal-return',rewards};const changed=await tx.roster60Adventure.updateMany({where:{id:row.id,playerId,revision:row.revision,status:'ACTIVE',activeBattleId:null},data:{status:'RETURNED',revision:{increment:1},pendingRewards:JSON.stringify({xp:{}}),settlement:JSON.stringify(settlement)}});if(changed.count!==1)return fail('STALE_REVISION');await tx.player.update({where:{id:playerId},data:{revision:{increment:1}}});return adventureDto({...row,status:'RETURNED',revision:row.revision+1,pendingRewards:JSON.stringify({xp:{}}),settlement:JSON.stringify(settlement)});
 });}
}
// Called only inside the authenticated battle settlement transaction. No client reward input.
export async function settleAdventureBattle(tx:Prisma.TransactionClient,row:{id:string;playerId:string;ownedId:string},state:State,snap:any){
 const run=await tx.roster60Adventure.findFirst({where:{id:snap.adventureId,playerId:row.playerId,status:'ACTIVE',activeBattleId:row.id}});if(!run)return fail('ADVENTURE_STATE_CHANGED');
 const saved=JSON.parse(run.state),pending=JSON.parse(run.pendingRewards);const party=[state.fighters[0],...(state.reserves??[])];
 if(saved.party.length!==party.length||saved.party.some((f:Fighter)=>!party.some(x=>x.ownedId===f.ownedId)))return fail('ADVENTURE_STATE_CHANGED');
 const lost=party.every(f=>f.hp<=0);let xp=0;
 if(!lost&&state.outcome==='win'){xp=snap.encounter.winXp;pending.xp[row.ownedId]=(pending.xp[row.ownedId]??0)+xp;if(!Number.isSafeInteger(pending.xp[row.ownedId]))return fail('INVALID_REWARD');}
 for(const m of saved.members){const changed=await tx.roster60Owned.updateMany({where:{id:m.ownedId,playerId:row.playerId,status:'BATTLE',totalXp:BigInt(m.startingXp)},data:{status:lost?'AVAILABLE':'ADVENTURE'}});if(changed.count!==1)return fail('UNIT_STATE_CHANGED');}
 const settlement=lost?{kind:'forced-return',lostRewards:pending}:null;
 const changed=await tx.roster60Adventure.updateMany({where:{id:run.id,playerId:row.playerId,revision:run.revision,status:'ACTIVE',activeBattleId:row.id},data:{activeBattleId:null,revision:{increment:1},state:JSON.stringify({...saved,party}),pendingRewards:JSON.stringify(lost?{xp:{}}:pending),status:lost?'LOST':'ACTIVE',settlement:settlement?JSON.stringify(settlement):null}});if(changed.count!==1)return fail('ADVENTURE_STATE_CHANGED');
 return {outcome:state.outcome,xpAwarded:0,xpEscrowed:xp,adventureId:run.id,adventureStatus:lost?'LOST':'ACTIVE',pendingRewards:lost?{xp:{}}:pending};
}
