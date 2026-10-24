import {randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient} from '@prisma/client';
import {validateRosterDefinition,type RosterDefinition} from './battle/roster60-rules.js';
export const commonChoices=['u02','u03','u04','u05','u06','u07','u08'] as const;
export type RosterRecipe={resultId:string;ingredients:{unitId:string;count:number;minLevel:number}[];visibility:'public'|'hidden'};
export type RosterCatalog={statSchema?:'gmm-five-stats-v1';rebirthPolicy?:string;profileKind?:string;version:string;approved:boolean;characters:(RosterDefinition&{rarity:string;stats:Record<string,number>})[];recipes?:RosterRecipe[]};
export class RosterError extends Error {constructor(public status:number,public code:string){super(code);}}
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
type Tx=Prisma.TransactionClient;
function choices(catalog:RosterCatalog|null){
  if(catalog?.approved!==true||typeof catalog.version!=='string'||!catalog.version) return fail('ROSTER_CATALOG_PENDING',503);
  if(!Array.isArray(catalog.characters))return fail('ROSTER_CATALOG_INVALID',503);
  const list=commonChoices.map(id=>{
    const matches=catalog.characters.filter(c=>c.id===id);
    if(matches.length!==1)return fail('ROSTER_CATALOG_INVALID',503);
    const unit=matches[0];
    try{validateRosterDefinition(unit);}catch{return fail('ROSTER_CATALOG_INVALID',503);}
    if(unit.rarity!=='COMMON'||!unit.stats||!Object.keys(unit.stats).length||Object.values(unit.stats).some(v=>!Number.isFinite(v)||v<0))return fail('ROSTER_CATALOG_INVALID',503);
    return structuredClone(unit);
  });
  return {statSchema:catalog.statSchema,catalogVersion:catalog.version,characters:list};
}
function publicState(s:{starterId:string;completedAt:Date|null;mailId:string|null;selectedId:string|null;rewardOwnedId:string|null;catalogVersion:string|null}){
  return {flow:'roster60-v1',starter:{id:s.starterId,speciesId:'u01',name:'갈매미맨',permanent:true},
    tutorialComplete:s.completedAt!==null,ticket:s.mailId?{ticketId:s.mailId,name:'커먼 선택지급권',quantity:s.selectedId?0:1,
      status:s.selectedId?'used':'unused',characterId:s.selectedId,ownedId:s.rewardOwnedId,catalogVersion:s.catalogVersion}:null};
}
// Only a trusted battle settlement may call this, inside its own durable transaction.
// No HTTP endpoint accepts a proof or a client-supplied completion flag.
export async function verifyRosterTutorial(tx:Tx,playerId:string,settledBattleId:string){
  if(!settledBattleId)throw new Error('SERVER_BATTLE_RECEIPT_REQUIRED');
  await tx.roster60State.updateMany({where:{playerId,tutorialProof:null},data:{tutorialProof:settledBattleId}});
}
export class RosterOnboarding {
  constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
  private async transaction<T>(fn:(tx:Tx)=>Promise<T>):Promise<T>{
    try{return await this.db.$transaction(fn);}catch(e){
      // A unique-constraint/transaction-conflict error rolls back; retry the whole operation once.
      if(['P2002','P2034'].includes((e as {code?:string}).code??''))return this.db.$transaction(fn);
      throw e;
    }
  }
  async start(playerId:string){return this.transaction(async tx=>{
    const old=await tx.roster60State.findUnique({where:{playerId}});if(old)return publicState(old);
    const starter=await tx.roster60Owned.create({data:{playerId,speciesId:'u01',source:'tutorial-starter'}});
    const state=await tx.roster60State.create({data:{playerId,starterId:starter.id}});
    return publicState(state);
  });}
  async state(playerId:string){const s=await this.db.roster60State.findUnique({where:{playerId}});return s?publicState(s):{flow:'roster60-v1',started:false};}
  async complete(playerId:string){return this.transaction(async tx=>{
    const s=await tx.roster60State.findUnique({where:{playerId}});
    if(!s?.tutorialProof)return fail('TUTORIAL_NOT_VERIFIED');
    if(s.completedAt)return publicState(s);
    const mailId='roster60-'+randomUUID();
    await tx.nativeInboxMessage.create({data:{id:mailId,playerId,title:'커먼 선택지급권',bodyText:'갈매미맨을 제외한 커먼 7종 중 한 마리를 선택해 영구 지급받습니다. 선택 전 능력 정보를 확인하세요. 취소하면 소비되지 않습니다.'}});
    return publicState(await tx.roster60State.update({where:{playerId},data:{mailId,completedAt:new Date()}}));
  });}
  async preview(playerId:string,ticketId:string){
    const s=await this.db.roster60State.findFirst({where:{playerId,mailId:ticketId,completedAt:{not:null}}});
    if(!s)return fail('TICKET_NOT_FOUND',404);
    if(s.selectedId)return publicState(s);
    return {...publicState(s),...choices(this.catalog())};
  }
  async confirm(playerId:string,ticketId:string,characterId:string,catalogVersion:string){
    if(!(commonChoices as readonly string[]).includes(characterId))return fail('INVALID_CHOICE',400);
    return this.transaction(async tx=>{
      const s=await tx.roster60State.findFirst({where:{playerId,mailId:ticketId,completedAt:{not:null}}});
      if(!s)return fail('TICKET_NOT_FOUND',404);
      if(s.selectedId){if(s.selectedId!==characterId)return fail('ALREADY_REDEEMED');return publicState(s);}
      const catalog=choices(this.catalog());if(catalog.catalogVersion!==catalogVersion)return fail('CATALOG_CHANGED');
      // Conditional claim and permanent grant share one transaction; a failed grant rolls back consumption.
      const ownedId=randomUUID();
      const updated=await tx.roster60State.updateMany({where:{playerId,mailId:ticketId,selectedId:null},data:{selectedId:characterId,rewardOwnedId:ownedId,catalogVersion}});
      if(updated.count!==1)return fail('ALREADY_REDEEMED');
      await tx.roster60Owned.create({data:{id:ownedId,playerId,speciesId:characterId,source:'tutorial-common-choice'}});
      return publicState(await tx.roster60State.findUniqueOrThrow({where:{playerId}}));
    });
  }
}
