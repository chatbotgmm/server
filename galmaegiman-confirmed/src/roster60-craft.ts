import {createHash,randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient} from '@prisma/client';
import {combinationGrowth,growthAt,rosterMaxLevel,validateRosterDefinition} from './battle/roster60-rules.js';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
type Tx=Prisma.TransactionClient;
export type CraftSelection={resultId:string;materialIds:string[];catalogVersion:string};
export type CraftConfirmation=CraftSelection&{requestId:string;previewFingerprint:string};
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const hash=(value:unknown)=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const canonical=(b:CraftSelection)=>[b.resultId,[...b.materialIds].sort(),b.catalogVersion];
export class RosterCraft {
  constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
  async options(playerId:string){
    const c=this.catalog();
    if(c?.approved!==true||!Array.isArray(c.characters)||!Array.isArray(c.recipes))return fail('ROSTER_CATALOG_PENDING',503);
    const definitions=new Map(c.characters.map(u=>[u.id,u]));
    try{for(const u of c.characters)validateRosterDefinition(u);}catch{return fail('ROSTER_CATALOG_INVALID',503);}
    const rows=await this.db.roster60Owned.findMany({where:{playerId,status:'AVAILABLE',locked:false},orderBy:{id:'asc'},take:1001});
    return {catalogVersion:c.version,truncated:rows.length>1000,units:rows.slice(0,1000).map(u=>{
      const d=definitions.get(u.speciesId);if(!d)return fail('ROSTER_CATALOG_INVALID',503);
      if(u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_STORED_XP');
      let level:number;
      try{level=growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy).level;}
      catch(e){return fail((e as Error).message,503);}
      return {ownedId:u.id,speciesId:u.speciesId,name:d.name,totalXp:u.totalXp.toString(),level,maxLevel:rosterMaxLevel(u.reincarnation),reincarnation:u.reincarnation};
    }),recipes:c.recipes.filter(r=>r.visibility==='public').map(r=>({resultId:r.resultId,name:definitions.get(r.resultId)?.name??r.resultId,ingredients:r.ingredients}))};
  }
  async receipt(playerId:string,requestId:string){
    const r=await this.db.roster60CraftReceipt.findUnique({where:{playerId_requestId:{playerId,requestId}}});
    return r?{status:'completed',result:JSON.parse(r.result)}:{status:'unknown'};
  }
  private async plan(tx:Tx,playerId:string,input:CraftSelection){
    if(!input.materialIds.length||input.materialIds.length>6||new Set(input.materialIds).size!==input.materialIds.length)return fail('INVALID_MATERIALS',400);
    const catalog=this.catalog();
    if(catalog?.approved!==true||!Array.isArray(catalog.characters)||!Array.isArray(catalog.recipes))return fail('ROSTER_CATALOG_PENDING',503);
    if(catalog.version!==input.catalogVersion)return fail('CATALOG_CHANGED');
    const matching=catalog.recipes.filter(r=>r.resultId===input.resultId);
    if(matching.length!==1)return fail('INVALID_RECIPE',400);
    const recipe=matching[0];
    if(!Array.isArray(recipe.ingredients)||!recipe.ingredients.length||recipe.ingredients.some(i=>!Number.isSafeInteger(i.count)||i.count<1||!Number.isSafeInteger(i.minLevel)||i.minLevel<1))return fail('ROSTER_CATALOG_INVALID',503);
    if(new Set(recipe.ingredients.map(i=>i.unitId)).size!==recipe.ingredients.length)return fail('ROSTER_CATALOG_INVALID',503);
    const definition=(id:string)=>{
      const found=catalog.characters.filter(u=>u.id===id);
      if(found.length!==1)return fail('ROSTER_CATALOG_INVALID',503);
      try{validateRosterDefinition(found[0]);}catch{return fail('ROSTER_CATALOG_INVALID',503);}
      return found[0];
    };
    const target=definition(input.resultId);
    const materials=await tx.roster60Owned.findMany({where:{id:{in:input.materialIds},playerId,status:'AVAILABLE',locked:false},orderBy:{id:'asc'}});
    if(materials.length!==input.materialIds.length)return fail('MATERIALS_UNAVAILABLE');
    if(materials.some(m=>m.reincarnation!==0))return fail('CRAFT_REINCARNATION_POLICY_REQUIRED');
    if(recipe.ingredients.reduce((n,i)=>n+i.count,0)!==materials.length)return fail('INVALID_RECIPE',400);
    const xp=(v:bigint)=>{if(v<0n||v>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_STORED_XP');return Number(v);};
    for(const ingredient of recipe.ingredients){
      const group=materials.filter(m=>m.speciesId===ingredient.unitId);
      if(group.length!==ingredient.count)return fail('INVALID_RECIPE',400);
      const curve=definition(ingredient.unitId).growth;
      if(group.some(m=>growthAt(xp(m.totalXp),curve).level<ingredient.minLevel))return fail('MATERIAL_LEVEL_REQUIRED');
    }
    let result:ReturnType<typeof combinationGrowth>;
    try{result=combinationGrowth(materials.map(m=>xp(m.totalXp)),target.growth);}catch(e){if((e as Error).message==='CAP_XP_POLICY_REQUIRED')return fail('CAP_XP_POLICY_REQUIRED');throw e;}
    // Include the calculated result as well as version: a mistakenly edited catalog
    // with an unchanged version must not silently change the confirmed level.
    const previewFingerprint=hash([canonical(input),materials.map(m=>[m.id,m.speciesId,m.totalXp.toString()]),target.name,recipe,result]);
    return {resultId:target.id,resultName:target.name,catalogVersion:catalog.version,materialIds:materials.map(m=>m.id),
      inheritedTotalXp:result.totalXp,expectedLevel:result.level,maxLevel:result.maxLevel,reincarnation:0,previewFingerprint};
  }
  preview(playerId:string,input:CraftSelection){return this.db.$transaction(tx=>this.plan(tx,playerId,input));}
  async confirm(playerId:string,input:CraftConfirmation){
    if(!input.requestId||input.requestId.length>100||!input.previewFingerprint)return fail('INVALID_REQUEST',400);
    const fingerprint=hash([canonical(input),input.previewFingerprint]);
    const run=()=>this.db.$transaction(async tx=>{
      const old=await tx.roster60CraftReceipt.findUnique({where:{playerId_requestId:{playerId,requestId:input.requestId}}});
      if(old){if(old.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');return JSON.parse(old.result);}
      const plan=await this.plan(tx,playerId,input);
      if(plan.previewFingerprint!==input.previewFingerprint)return fail('PREVIEW_CHANGED');
      const consumed=await tx.roster60Owned.updateMany({where:{playerId,id:{in:plan.materialIds},status:'AVAILABLE',locked:false},data:{status:'CONSUMED'}});
      if(consumed.count!==plan.materialIds.length)return fail('MATERIALS_UNAVAILABLE');
      const unit=await tx.roster60Owned.create({data:{id:randomUUID(),playerId,speciesId:plan.resultId,totalXp:BigInt(plan.inheritedTotalXp),source:'craft:'+input.requestId}});
      const result={...plan,ownedId:unit.id};
      await tx.roster60CraftReceipt.create({data:{playerId,requestId:input.requestId,fingerprint,result:JSON.stringify(result)}});
      return result;
    });
    try{return await run();}catch(e){if(['P2002','P2034'].includes((e as {code?:string}).code??''))return run();throw e;}
  }
}
