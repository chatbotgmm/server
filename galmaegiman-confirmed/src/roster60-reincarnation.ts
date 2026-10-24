import type {PrismaClient} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {previewFirstReincarnation,type BaseSixStats} from './battle/roster60-reincarnation.js';
import {growthAt,validateRosterDefinition} from './battle/roster60-rules.js';
// No writes: eligibility/required level/cost still require product decisions.
export class RosterReincarnation {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
 async preview(playerId:string){
  const c=this.catalog();if(!c?.approved)throw new RosterError(503,'ROSTER_CATALOG_PENDING');
  const units=await this.db.roster60Owned.findMany({where:{playerId,status:'AVAILABLE'},take:1000,orderBy:{id:'asc'}});
  return {serverNow:Date.now(),flow:'roster60-v1',catalogVersion:c.version,candidates:units.map(u=>{
   const d=c.characters.find(x=>x.id===u.speciesId);let plan:unknown=null,reason='REINCARNATION_ELIGIBILITY_COST_POLICY_REQUIRED';
   try{
    if(!d)throw Error('ROSTER_CATALOG_INVALID');validateRosterDefinition(d);
    const base=(d as typeof d&{levelOneStats?:BaseSixStats}).levelOneStats;
    if(!base)throw Error('LEVEL_ONE_BASE_STATS_REQUIRED');
    if(base.mp!==d.growth[0].maxMp)throw Error('BASE_MP_MISMATCH');
    if(u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))throw Error('INVALID_XP');
    const before=growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy);
    plan={beforeLevel:before.level,...previewFirstReincarnation({totalXp:Number(u.totalXp),reincarnation:u.reincarnation,baseLevelOneStats:base,curve:d.growth,suitability:d.suitability,defense:d.defense})};
   }catch(e){reason=(e as Error).message;}
   return {ownedId:u.id,speciesId:u.speciesId,name:d?.name??u.speciesId,eligible:false,reason,preview:plan};
  })};
 }
 async confirm(playerId:string,ownedId:string){
  const unit=await this.db.roster60Owned.findFirst({where:{id:ownedId,playerId}});
  if(!unit)throw new RosterError(404,'UNIT_NOT_FOUND');
  if(unit.reincarnation!==0)throw new RosterError(409,'REPEAT_REINCARNATION_POLICY_REQUIRED');
  throw new RosterError(503,'REINCARNATION_ELIGIBILITY_COST_POLICY_REQUIRED');
 }
}
