import {growthAt} from './battle/roster60-rules.js';
import {randomInt,createHash} from 'node:crypto';
import type {PrismaClient} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {loadContent} from './content.js';
import {collectionDefenseKnowledge,defenseKnowledgeProjection} from './battle/roster60-observed-defense.js';
import {recordRosterDailyProgress} from './roster60-daily-progress.js';
export const rosterGrades={COMMON:'커먼',UNCOMMON:'언커먼',RARE:'레어',EPIC:'에픽',LEGEND:'전설',HIDDEN:'히든',LIMITED:'제한',TRANSCEND:'초월',IMMORTAL:'불멸',ETERNAL:'영원'};
// Approved grant levels, independent of unapproved offer prices/grade weights.
export const recruitGrantLevels=Object.freeze({COMMON:1,UNCOMMON:1,RARE:10,EPIC:15,LEGEND:20,HIDDEN:20});
export function recruitmentGrowth(unit:RosterCatalog['characters'][number]){
 const level=recruitGrantLevels[unit.rarity as keyof typeof recruitGrantLevels];
 if(level===undefined)throw new RosterError(409,'RECRUIT_LEVEL_POLICY_PENDING');
 const point=unit.growth?.[level-1];
 if(!point)throw new RosterError(503,'RECRUIT_GROWTH_INVALID');
 try{const state=growthAt(point.totalXp,unit.growth);if(state.level!==level)throw Error('level');return state;}
 catch{throw new RosterError(503,'RECRUIT_GROWTH_INVALID');}
}
export class RosterLibrary {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private roll:(max:number)=>number=randomInt,private completionTime=()=>new Date()){}
 private ready(){const c=this.catalog();if(!c?.approved)throw new RosterError(503,'ROSTER_CATALOG_PENDING');return c;}
 async collection(playerId:string){const c=this.ready(),owned=await this.db.roster60Owned.findMany({where:{playerId}}),knowledge=await collectionDefenseKnowledge(this.db,playerId,c);
  return {flow:'roster60-v1',catalogVersion:c.version,testBaseline:c.profileKind==='user-authorized-test-baseline',grades:rosterGrades,characters:c.characters.map(u=>({id:u.id,name:u.name,rarity:u.rarity,role:(u as any).role,levelOneStats:(u as any).levelOneStats,growthPerLevel:(u as any).growthPerLevel,suitability:u.suitability,defenseKnowledge:defenseKnowledgeProjection(u.id,knowledge[u.id]??{}),skills:u.skills,ownedCount:owned.filter(o=>o.speciesId===u.id&&o.status!=='CONSUMED').length,discovered:owned.some(o=>o.speciesId===u.id)}))};
 }
 private offers(c:RosterCatalog){return Object.entries(loadContent().economy.gachas).map(([kind,g])=>({kind,label:g.label,cost:g.cost,weights:g.weights,enabled:kind==='LOW'&&Object.keys(g.weights).every(t=>c.characters.some(u=>u.rarity===t)),reason:kind==='LOW'?'':'새 등급 확률 승인 대기 (기존 SPECIAL 등급 없음)'}));}
 async info(playerId:string){const c=this.ready(),p=await this.db.player.findUniqueOrThrow({where:{id:playerId}});return {flow:'roster60-v1',catalogVersion:c.version,credits:p.credits,grantLevels:recruitGrantLevels,offers:this.offers(c)};}
 async recruit(playerId:string,input:{requestId:string;kind:string;times:number;catalogVersion:string}){
  const fingerprint=createHash('sha256').update(JSON.stringify([input.kind,input.times,input.catalogVersion])).digest('hex');
  const run=()=>this.db.$transaction(async tx=>{
   const receipt=await tx.roster60RecruitReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:input.requestId}}});
   if(receipt){if(receipt.fingerprint!==fingerprint)throw new RosterError(409,'REQUEST_ID_REUSED');return JSON.parse(receipt.result);}
   const c=this.ready();if(c.version!==input.catalogVersion)throw new RosterError(409,'CATALOG_CHANGED');
   if(!Number.isSafeInteger(input.times)||input.times<1||input.times>50)throw new RosterError(400,'INVALID_REQUEST');
   const offer=this.offers(c).find(o=>o.kind===input.kind);if(!offer?.enabled)throw new RosterError(409,'RECRUIT_GRADE_POLICY_PENDING');
   if(!await tx.roster60State.findUnique({where:{playerId}}))throw new RosterError(409,'ROSTER_START_REQUIRED');
   const p=await tx.player.findUniqueOrThrow({where:{id:playerId}}),cost=offer.cost*input.times;
   const unlimited=p.isAdmin&&p.adminMode;
   if(!unlimited&&p.credits<cost)throw new RosterError(409,'INSUFFICIENT_CREDITS');
   const updated=await tx.player.updateMany({where:{id:playerId,revision:p.revision,...(unlimited?{}:{credits:{gte:cost}})},data:{revision:{increment:1},...(unlimited?{}:{credits:{decrement:cost}})}});
   if(updated.count!==1)throw new RosterError(409,'RECRUIT_RETRY');
   const weights=Object.entries(offer.weights) as [string,number][];if(weights.reduce((s,[,w])=>s+w,0)!==100)throw new RosterError(503,'RECRUIT_POLICY_INVALID');
   const items=[];for(let i=0;i<input.times;i++){
    let r=this.roll(100),grade=weights[0][0];for(const [g,w] of weights){if(r<w){grade=g;break;}r-=w;}
    const pool=c.characters.filter(u=>u.rarity===grade),u=pool[this.roll(pool.length)];
    const growth=recruitmentGrowth(u);
    const o=await tx.roster60Owned.create({data:{playerId,speciesId:u.id,totalXp:BigInt(growth.totalXp),source:'recruit:'+input.requestId+':'+i}});items.push({ownedId:o.id,speciesId:u.id,name:u.name,rarity:u.rarity,level:growth.level,totalXp:growth.totalXp});
   }
   await recordRosterDailyProgress(tx,playerId,'DRAW',items.length,this.completionTime());
   const credits=(await tx.player.findUniqueOrThrow({where:{id:playerId},select:{credits:true}})).credits;
   const result={flow:'roster60-v1',requestId:input.requestId,catalogVersion:c.version,charged:unlimited?0:cost,credits,items};
   await tx.roster60RecruitReceipt.create({data:{playerId,requestId:input.requestId,fingerprint,result:JSON.stringify(result)}});return result;
  });
  try{return await run();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return run();throw e;}
 }
}
