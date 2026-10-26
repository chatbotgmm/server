import type {Prisma} from '@prisma/client';
import {loadContent,topTiers} from './content.js';
import {RosterError} from './roster60-onboarding.js';

type Kind='DRAW'|'CRAFT'|'EXPEDITION';
const fields={DRAW:'draws',CRAFT:'crafts',CRAFT_RARE:'craftRare',CRAFT_LEGEND:'craftLegend',CRAFT_TOP:'craftTop',EXPEDITION:'expeditions'} as const;
/** The time is captured by the server after successful completion, never supplied by a request. */
export function rosterDailyDay(now:Date){
 if(!Number.isFinite(now.getTime()))throw new RosterError(500,'INVALID_SERVER_COMPLETION_TIME');
 return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
}
export function rosterDailyIncrements(kind:Kind,amount:number,rarity?:string){
 if(!Number.isSafeInteger(amount)||amount<1)throw new RosterError(500,'INVALID_COMPLETED_AMOUNT');
 const add:Record<string,number>={[fields[kind]]:amount};
 if(kind==='CRAFT'){
  // New roster EPIC is 에픽; new RARE is 레어. The legacy field name stays compatible.
  if(rarity==='EPIC')add.craftRare=amount;
  if(rarity==='LEGEND'||rarity==='HIDDEN')add.craftLegend=amount;
  if(rarity&&topTiers.has(rarity))add.craftTop=amount;
 }
 return add;
}
/** Existing daily counters/ticket rewards only. Does not award any play-pass points. */
export async function recordRosterDailyProgress(tx:Prisma.TransactionClient,playerId:string,kind:Kind,amount:number,now:Date,rarity?:string){
 const day=rosterDailyDay(now),add=rosterDailyIncrements(kind,amount,rarity);
 const row=await tx.dailyProgress.upsert({where:{playerId_day:{playerId,day}},create:{playerId,day,...add},update:Object.fromEntries(Object.entries(add).map(([key,value])=>[key,{increment:value}]))});
 const done=new Set(row.rewarded.split(',').filter(Boolean));
 const completed=loadContent().economy.missions.filter(m=>row[fields[m.key]]>=m.goal&&!done.has(m.key));
 let tickets=0;
 if(completed.length){
  for(const mission of completed)done.add(mission.key);
  const claimed=await tx.dailyProgress.updateMany({where:{playerId,day,rewarded:row.rewarded},data:{rewarded:[...done].join(',')}});
  if(claimed.count!==1)throw new RosterError(409,'DAILY_PROGRESS_RETRY');
  for(const mission of completed){
   if(mission.tickets)await tx.player.update({where:{id:playerId},data:{credits:{increment:mission.tickets}}});
   await tx.auditEvent.create({data:{playerId,kind:'MISSION',detail:{day,key:mission.key,tickets:mission.tickets}}});
   tickets+=mission.tickets;
  }
 }
 return {day,tickets,completed:completed.map(m=>m.key)};
}
