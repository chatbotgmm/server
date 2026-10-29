import type {PrismaClient} from '@prisma/client';
import {fighter} from './roster60-battle.js';
import {equippedSkills,readSkillInheritance} from './roster60-skill-inheritance.js';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {growthAt,type SkillDefinition} from './battle/roster60-rules.js';
import {fiveStatKeys,fiveRebirthPolicy,validateFiveStatCatalog,type FiveStats} from './battle/roster60-five-stats.js';

type OwnedRow={id:string;speciesId:string;totalXp:bigint;reincarnation:number;status:string;locked:boolean;skillInheritanceJson?:string|null};
type DefinedSkill=SkillDefinition&{power:number;learnLevel?:number};
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const difference=(after:FiveStats,before:FiveStats)=>Object.fromEntries(fiveStatKeys.map(key=>[key,after[key]-before[key]])) as FiveStats;

/** Authenticated owned-instance reads. Catalog/species entries are never an ownership claim. */
export class RosterOwned {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private now:()=>number=Date.now){}
 private ready(){
  const c=this.catalog();if(c?.approved!==true)return fail('ROSTER_CATALOG_PENDING',503);
  validateFiveStatCatalog(c);return c;
 }
 private projection(c:RosterCatalog,row:OwnedRow){
  const definition=c.characters.find(unit=>unit.id===row.speciesId);if(!definition)return fail('BATTLE_UNIT_DATA_PENDING',503);
  if(row.totalXp<0n||row.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');
  if(row.reincarnation&&c.rebirthPolicy!==fiveRebirthPolicy)return fail('FIVE_STAT_REBIRTH_POLICY_PENDING',503);
  const grown=growthAt(Number(row.totalXp),definition.growth,row.reincarnation,definition.reincarnationXpPolicy);
  const current=fighter(c,row.speciesId,grown.level,row.id,row.reincarnation,row.skillInheritanceJson);
  const summary={ownedId:row.id,speciesId:row.speciesId,name:current.name,rarity:definition.rarity,
   level:grown.level,maxLevel:grown.maxLevel,reincarnation:row.reincarnation,status:row.status,locked:row.locked,stats:current.stats};
  return {definition,grown,current,summary};
 }
 async list(playerId:string){
  const c=this.ready();
  const rows=await this.db.roster60Owned.findMany({where:{playerId,status:{not:'CONSUMED'}},orderBy:{id:'asc'}});
  const units=rows.map(row=>this.projection(c,row).summary).sort((a,b)=>b.level-a.level||a.ownedId.localeCompare(b.ownedId));
  return {flow:'roster60-owned-v1',catalogVersion:c.version,serverNow:this.now(),units};
 }
 async read(playerId:string,ownedId:string){
  return this.db.$transaction(async tx=>{
   // Non-owned, removed and consumed IDs share the same response. No species lookup fallback.
   const row=await tx.roster60Owned.findFirst({where:{id:ownedId,playerId,status:{not:'CONSUMED'}}});
   if(!row)return fail('UNIT_NOT_FOUND',404);
   const c=this.ready(),{definition,grown,current,summary}=this.projection(c,row);
   const party=await tx.roster60PvpParty.findUnique({where:{playerId}});
   const partyIds:string[]=party?JSON.parse(party.ownedIds):[];
   const base=(definition as typeof definition&{levelOneStats:FiveStats}).levelOneStats;
   const levelStats=fighter(c,row.speciesId,grown.level,row.id,0).stats;
   const skill=(value:DefinedSkill)=>({id:value.id,name:value.name,element:value.element,power:value.power,
    mpCost:value.mpCost,learnLevel:value.learnLevel??1,effectText:'위력 '+value.power,effectsStatus:'POWER_ONLY_NO_ADDITIONAL_EFFECTS'});
   const slots=equippedSkills(c,row.speciesId,row.skillInheritanceJson);
   const future=slots.map(entry=>entry.skill).filter(value=>(value.learnLevel??1)>grown.level&&(value.learnLevel??1)<=grown.maxLevel);
   const nextLevel=future.length?Math.min(...future.map(value=>value.learnLevel??1)):null;
   const unit={...summary,growth:{level:grown.level,maxLevel:grown.maxLevel,reincarnation:grown.reincarnation,
    totalXp:grown.totalXp,xp:grown.xp,xpToNext:grown.xpToNext,levelStartTotalXp:grown.totalXp-grown.xp,
    nextLevelTotalXp:grown.xpToNext===null?null:grown.totalXp+grown.xpToNext},
    statBreakdown:{base:{...base},growth:difference(levelStats,base),rebirth:difference(current.stats,levelStats),
     total:{...current.stats},other:null,otherStatus:'NO_OTHER_BONUS_MODEL'},
    aptitude:structuredClone(current.suitability),
    // Only this authenticated, exact-owned-ID DTO exposes the owner's actual profile.
    // The common publicDefenseSafe filter remains intact for every existing public/opponent DTO.
    ownResistance:structuredClone(current.defense),
    skills:{attack:skill(current.attack),current:(current.skills as DefinedSkill[]).map(skill),
     slots:slots.map(entry=>({slot:entry.slot,...skill(entry.skill),unlocked:(entry.skill.learnLevel??1)<=grown.level,inherited:readSkillInheritance(row.skillInheritanceJson)?.replaceSlot===entry.slot})),inheritance:readSkillInheritance(row.skillInheritanceJson),
     nextLearn:nextLevel===null?null:{level:nextLevel,skills:future.filter(value=>(value.learnLevel??1)===nextLevel).map(skill)},
     growth:{status:'DESIGN_NEEDED',reason:'NO_PER_INDIVIDUAL_SKILL_GROWTH_MODEL'}},
    party:{pvp:{position:partyIds.includes(row.id)?partyIds.indexOf(row.id)+1:null,size:partyIds.length},activity:row.status}};
   return {flow:'roster60-owned-v1',catalogVersion:c.version,serverNow:this.now(),unit};
  });
 }
}
