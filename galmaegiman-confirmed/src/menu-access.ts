import {z} from 'zod';
import type {PrismaClient} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
export const menuFeatures=['recruit','roster','craft','adventure','shop','party','daily','rebirth'] as const;
export type MenuFeature=typeof menuFeatures[number];
export type MenuMode='force_lock'|'force_unlock'|'auto';
export const menuChangeSchema=z.object({requestId:z.string().min(1).max(100),targetPlayerId:z.string().min(1).max(100),featureId:z.enum(menuFeatures),mode:z.enum(['force_lock','force_unlock','auto']),expectedRevision:z.number().int().nonnegative()}).strict();
export class MenuAccess {
 // Legacy constructor arguments are accepted for older maintenance clients only.
 constructor(private db:PrismaClient,_catalog?:()=>RosterCatalog|null,_testDefaultLock?:boolean){}
 async read(_id:string){
  // Authentication belongs to the route. Menu availability has no account,
  // configuration, ownership, catalog or level dependency.
  const features=Object.fromEntries(menuFeatures.map(featureId=>[featureId,{featureId,mode:'auto' as const,enabled:true,reason:'',requiredLevel:null}]));
  return {version:'menu-access-v1',revision:0,representativeLevel:null,levelReason:'',testDefaultLock:false,levelPolicy:'NONE',levelDecreasePolicy:'NONE',features,enforcementEnabled:false};
 }
 async change(actor:string,input:{requestId:string;targetPlayerId:string;featureId:MenuFeature;mode:MenuMode;expectedRevision:number}){
  menuChangeSchema.parse(input);
  const admin=await this.db.player.findUnique({where:{id:actor},select:{isAdmin:true,identity:true}});
  if(!admin?.isAdmin||admin.identity.startsWith('review:'))throw new RosterError(403,'ADMIN_REQUIRED');
  // Explicitly retire writes rather than reporting success for an ineffective lock.
  throw new RosterError(410,'MENU_ACCESS_RETIRED');
 }
}

