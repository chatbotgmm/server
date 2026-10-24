import type {PrismaClient,Prisma} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {rebirthGradePolicy} from './roster60-reincarnation.js';
const grades=rebirthGradePolicy.grades;
export async function recordRebirthUnlock(tx:Prisma.TransactionClient,playerId:string,rarity:string){
 if(!grades.includes(rarity))return;
 await tx.roster60Home.upsert({where:{playerId},create:{playerId,rebirthUnlocked:true},update:{rebirthUnlocked:true}});
}
export class RosterHome {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
 async read(playerId:string){
  // Catalog only supplies server-owned identity/grade; no client grade claims.
  let c:RosterCatalog|null=null;try{c=this.catalog();}catch{/* leave unknown catalog locked */}
  const state=await this.db.$transaction(async tx=>{
   const current=await tx.roster60Home.findUnique({where:{playerId}});
   if(current?.rebirthUnlocked)return current;
   if(c?.approved===true){
    const ids=c.characters.filter(u=>grades.includes(u.rarity)).map(u=>u.id);
    if(await tx.roster60Owned.findFirst({where:{playerId,speciesId:{in:ids}}}))await recordRebirthUnlock(tx,playerId,'LIMITED');
   }
   return tx.roster60Home.findUnique({where:{playerId}});
  });
  const now=new Date(),mail=await this.db.nativeInboxMessage.findMany({where:{playerId,publishedAt:{lte:now},OR:[{expiresAt:null},{expiresAt:{gt:now}}]},select:{id:true}});
  const reads=await this.db.nativeNewsRead.count({where:{playerId,kind:'inbox',itemId:{in:mail.map(m=>m.id)}}});
  const onboarding=await this.db.roster60State.findUnique({where:{playerId}});
  return {rebirthUnlocked:state?.rebirthUnlocked??false,rebirthIntroPending:!!state?.rebirthUnlocked&&!state.rebirthIntroComplete,rebirthAllowedGrades:grades,rebirthBonusPending:true,mailUnread:mail.length-reads,mailRewardPending:!!onboarding?.mailId&&!onboarding.selectedId};
 }
 async completeIntro(playerId:string){
  const result=await this.db.roster60Home.updateMany({where:{playerId,rebirthUnlocked:true},data:{rebirthIntroComplete:true}});
  if(!result.count)throw new RosterError(409,'REBIRTH_LOCKED');
  return {complete:true}; // idempotent; cancel/background never calls this endpoint
 }
}
