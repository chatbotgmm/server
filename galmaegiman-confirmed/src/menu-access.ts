import {z} from 'zod';
import {createHash} from 'node:crypto';
import type {PrismaClient} from '@prisma/client';
import {growthAt} from './battle/roster60-rules.js';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
export const menuFeatures=['recruit','roster','craft','adventure','shop','party','daily','rebirth'] as const;
export type MenuFeature=typeof menuFeatures[number];
export type MenuMode='force_lock'|'force_unlock'|'auto';
const savedSchema=z.object({revision:z.number().int().nonnegative(),overrides:z.partialRecord(z.enum(menuFeatures),z.enum(['force_lock','force_unlock','auto']))});
export const menuChangeSchema=z.object({requestId:z.string().min(1).max(100),targetPlayerId:z.string().min(1).max(100),featureId:z.enum(menuFeatures),mode:z.enum(['force_lock','force_unlock','auto']),expectedRevision:z.number().int().nonnegative()}).strict();
const hash=(s:string)=>createHash('sha256').update(s).digest('hex');
const accountKey=(id:string)=>'menu-access:v1:account:'+hash(id);
export function menuFeatureFor(path:string):MenuFeature|null{
 const p=path.replace(/^\/my\/api\//,'').split('?')[0];
 if(/^(native\/roster60\/recruit(?:\/|$)|draw$|claim$)/.test(p))return 'recruit';
 if(/^(native\/roster60\/collection$|units(?:\/|$)|native\/catalog$|native\/growth$|native\/progression\/reassess$)/.test(p))return 'roster';
 if(/^(native\/roster60\/craft(?:\/|$)|craft(?:\/|$)|mix$)/.test(p))return 'craft';
 if(/^(native\/roster60\/adventure(?:\/|$)|expedition(?:\/|$))/.test(p))return 'adventure';
 if(/^(shop(?:\/|$)|items\/buy$|native\/shop(?:\/|$))/.test(p))return 'shop';
 if(/^(native\/roster60\/pvp\/(party|options)$|native\/team$|party$|battle-team$)/.test(p))return 'party';
 if(/^(daily$|attend$|mission(?:\/|$)|native\/progression\/pass\/claim$)/.test(p))return 'daily';
 if(/^(native\/roster60\/rebirth(?:\/|$)|native\/roster60\/reincarnation(?:\/|$)|native\/progression\/reincarnate$|native\/roster60\/home\/rebirth-intro\/complete$)/.test(p))return 'rebirth';
 return null;
}
export class MenuAccess {
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null){}
 async read(id:string){
  if(!await this.db.player.findUnique({where:{id},select:{id:true}}))throw new RosterError(401,'login');
  const row=await this.db.appMeta.findUnique({where:{key:accountKey(id)}});
  const saved=row?savedSchema.parse(JSON.parse(row.value)):{revision:0,overrides:{} as Partial<Record<MenuFeature,MenuMode>>};
  let representativeLevel:number|null=0,levelReason='';
  const owned=await this.db.roster60Owned.findMany({where:{playerId:id,status:{not:'CONSUMED'}},select:{speciesId:true,totalXp:true,reincarnation:true}});
  try{if(owned.length){const c=this.catalog();if(!c?.approved)throw Error('LEVEL_CATALOG_UNAVAILABLE');for(const u of owned){const d=c.characters.find(v=>v.id===u.speciesId);if(!d||u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))throw Error('LEVEL_DATA_INVALID');representativeLevel=Math.max(representativeLevel!,growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy).level);}}}
  catch{representativeLevel=null;levelReason='LEVEL_DATA_UNAVAILABLE';}
  const features=Object.fromEntries(menuFeatures.map(featureId=>{const mode:MenuMode=saved.overrides[featureId]??'auto';return [featureId,{featureId,mode,enabled:mode==='force_unlock',reason:mode==='force_unlock'?'':mode==='force_lock'?'ACCOUNT_LOCKED':'TEST_DEFAULT_LOCK',requiredLevel:null}];}));
  return {version:'menu-access-v1',revision:saved.revision,representativeLevel,levelReason,testDefaultLock:true,levelPolicy:'PENDING',levelDecreasePolicy:'PENDING',features};
 }
 async require(id:string,feature:MenuFeature){const state=await this.read(id);if(!state.features[feature].enabled)throw new RosterError(403,'MENU_LOCKED:'+feature);}
 async change(actor:string,input:{requestId:string;targetPlayerId:string;featureId:MenuFeature;mode:MenuMode;expectedRevision:number}){
  menuChangeSchema.parse(input);
  return this.db.$transaction(async tx=>{
   // Serialize changes and receipts without changing the schema or existing balances.
   await tx.appMeta.upsert({where:{key:'menu-access:v1:write-lock'},create:{key:'menu-access:v1:write-lock',value:'1'},update:{value:'1'}});
   const admin=await tx.player.findUnique({where:{id:actor}});if(!admin?.isAdmin||admin.identity.startsWith('review:'))throw new RosterError(403,'ADMIN_REQUIRED');
   const key='menu-access:v1:receipt:'+hash(actor+'\0'+input.requestId),fingerprint=hash(JSON.stringify(input));
   const prior=await tx.appMeta.findUnique({where:{key}});if(prior){const receipt=JSON.parse(prior.value);if(receipt.fingerprint!==fingerprint)throw new RosterError(409,'REQUEST_ID_REUSED');return receipt.result;}
   if(!await tx.player.findUnique({where:{id:input.targetPlayerId}}))throw new RosterError(404,'PLAYER_NOT_FOUND');
   const target=accountKey(input.targetPlayerId),row=await tx.appMeta.findUnique({where:{key:target}}),state=row?savedSchema.parse(JSON.parse(row.value)):{revision:0,overrides:{} as Partial<Record<MenuFeature,MenuMode>>};
   if(state.revision!==input.expectedRevision)throw new RosterError(409,'STALE_REVISION');
   const before=state.overrides[input.featureId]??'auto';state.overrides[input.featureId]=input.mode;state.revision++;
   await tx.appMeta.upsert({where:{key:target},create:{key:target,value:JSON.stringify(state)},update:{value:JSON.stringify(state)}});
   const result={targetPlayerId:input.targetPlayerId,featureId:input.featureId,mode:input.mode,revision:state.revision};
   await tx.auditEvent.create({data:{playerId:actor,kind:'MENU_ACCESS_CHANGED',detail:{...result,before,requestId:input.requestId}}});
   await tx.appMeta.create({data:{key,value:JSON.stringify({fingerprint,result})}});return result;
  });
 }
}
