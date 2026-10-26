import type {PrismaClient} from '@prisma/client';
import type {RosterCatalog} from './roster60-onboarding.js';
import {rebirthGradePolicy} from './roster60-reincarnation.js';
const grades=rebirthGradePolicy.grades;
export class RosterHome {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
 async read(playerId:string){
  // The catalog describes action eligibility only; it never locks the menu.
  let c:RosterCatalog|null=null;try{c=this.catalog();}catch{/* bonus policy remains unknown */}
  const state=await this.db.roster60Home.findUnique({where:{playerId}});
  const now=new Date(),mail=await this.db.nativeInboxMessage.findMany({where:{playerId,publishedAt:{lte:now},OR:[{expiresAt:null},{expiresAt:{gt:now}}]},select:{id:true}});
  const reads=await this.db.nativeNewsRead.count({where:{playerId,kind:'inbox',itemId:{in:mail.map(m=>m.id)}}});
  const onboarding=await this.db.roster60State.findUnique({where:{playerId}});
  return {rebirthUnlocked:true,rebirthIntroPending:!state?.rebirthIntroComplete,rebirthAllowedGrades:grades,rebirthBonusPending:c?.rebirthPolicy!=='hp-mp-durability-additive-v1',mailUnread:mail.length-reads,mailRewardPending:!!onboarding?.mailId&&!onboarding.selectedId};
 }
 async completeIntro(playerId:string){
  await this.db.roster60Home.upsert({where:{playerId},create:{playerId,rebirthUnlocked:true,rebirthIntroComplete:true},update:{rebirthUnlocked:true,rebirthIntroComplete:true}});
  return {complete:true}; // idempotent; cancel/background never calls this endpoint
 }
}
