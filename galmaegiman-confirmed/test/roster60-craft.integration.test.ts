import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {RosterCraft} from '../src/roster60-craft.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import {skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('roster60 transactional combination',()=>{
 let db:PrismaClient,owner:string,other:string,service:RosterCraft;
 // Deliberately synthetic arithmetic data, not product defaults.
 const catalog:RosterCatalog={approved:true,version:'craft-test-only',characters:['u02','u09'].map(id=>({id,name:id,rarity:'COMMON',stats:{hp:10},
   attack:{id:'test',name:'test',element:'NORMAL',mpCost:1},skills:[],
   suitability:Object.fromEntries(skillElements.map(e=>[e,0])) as any,defense:Object.fromEntries(skillElements.map(e=>[e,'normal'])) as any,
   growth:[{totalXp:0,maxMp:7},{totalXp:id==='u02'?10:12,maxMp:9}]})),recipes:[{resultId:'u09',ingredients:[{unitId:'u02',count:2,minLevel:2}],visibility:'public'}]};
 beforeAll(async()=>{
   const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('disposable DB only');
   db=new PrismaClient({datasources:{db:{url}}});
   owner=(await db.player.create({data:{identity:'craft-'+randomUUID()}})).id;
   other=(await db.player.create({data:{identity:'craft-other-'+randomUUID()}})).id;
   service=new RosterCraft(db,()=>catalog);
 });
 afterAll(async()=>{if(db){await db.player.deleteMany({where:{id:{in:[owner,other]}}});await db.$disconnect();}});
 async function material(xp=11,playerId=owner){return db.roster60Owned.create({data:{playerId,speciesId:'u02',totalXp:BigInt(xp),source:randomUUID()}});}
 const selection=(ids:string[])=>({materialIds:ids,resultId:'u09',catalogVersion:catalog.version});
 it('options only expose available owned units and receipt is owner scoped',async()=>{
   const a=await material(),foreign=await material(11,other),locked=await material();
   await db.roster60Owned.update({where:{id:locked.id},data:{locked:true}});
   const options=await service.options(owner);
   expect(options.units.some(u=>u.ownedId===a.id)).toBe(true);
   expect(options.units.some(u=>[foreign.id,locked.id].includes(u.ownedId))).toBe(false);
   await expect(new RosterCraft(db,()=>null).options(owner)).rejects.toThrow('ROSTER_CATALOG_PENDING');
   const b=await material(),sel=selection([a.id,b.id]),p=await service.preview(owner,sel),requestId=randomUUID();
   const result=await service.confirm(owner,{...sel,requestId,previewFingerprint:p.previewFingerprint});
   expect(await service.receipt(owner,requestId)).toEqual({status:'completed',result});
   expect(await service.receipt(other,requestId)).toEqual({status:'unknown'});
 });
 it('preview floors average cumulative XP then calculates result level, never consumes',async()=>{
   const a=await material(11),b=await material(16);
   const p=await service.preview(owner,selection([a.id,b.id]));
   expect(p).toMatchObject({inheritedTotalXp:13,expectedLevel:2});
   expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(2);
 });
 it('rejects duplicate IDs, foreign/locked material, low level, and wrong recipe',async()=>{
   const a=await material(),b=await material(11,other),low=await material(0);
   await expect(service.preview(owner,selection([a.id,a.id]))).rejects.toThrow('INVALID_MATERIALS');
   await expect(service.preview(owner,selection([a.id,b.id]))).rejects.toThrow('MATERIALS_UNAVAILABLE');
   await expect(service.preview(owner,selection([a.id,low.id]))).rejects.toThrow('MATERIAL_LEVEL_REQUIRED');
   const c=await material();await db.roster60Owned.update({where:{id:c.id},data:{locked:true}});
   await expect(service.preview(owner,selection([a.id,c.id]))).rejects.toThrow('MATERIALS_UNAVAILABLE');
   await expect(service.preview(owner,{...selection([a.id]),resultId:'u60'})).rejects.toThrow('INVALID_RECIPE');
 });
 it('same transaction consumes exact materials, grants XP, and records idempotent receipt',async()=>{
   const a=await material(11),b=await material(16),s=selection([a.id,b.id]);
   const p=await service.preview(owner,s),request={...s,requestId:randomUUID(),previewFingerprint:p.previewFingerprint};
   const result=await service.confirm(owner,request);
   expect(await service.confirm(owner,{...request,materialIds:[b.id,a.id]})).toEqual(result);
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:result.ownedId}})).totalXp).toBe(13n);
   expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'CONSUMED'}})).toBe(2);
   await expect(service.confirm(owner,{...request,resultId:'u02'})).rejects.toThrow('REQUEST_ID_REUSED');
   await expect(service.confirm(owner,{...request,requestId:randomUUID()})).rejects.toThrow('MATERIALS_UNAVAILABLE');
 });
 it('changed XP invalidates preview and failed result insertion rolls consumption back',async()=>{
   const a=await material(),b=await material(),s=selection([a.id,b.id]);
   const p=await service.preview(owner,s);
   await db.roster60Owned.update({where:{id:a.id},data:{totalXp:12n}});
   await expect(service.confirm(owner,{...s,requestId:randomUUID(),previewFingerprint:p.previewFingerprint})).rejects.toThrow('PREVIEW_CHANGED');
   const fresh=await service.preview(owner,s),requestId=randomUUID();
   await db.$executeRawUnsafe("CREATE TRIGGER roster60_craft_fail BEFORE INSERT ON Roster60Owned WHEN NEW.speciesId='u09' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
   try{await expect(service.confirm(owner,{...s,requestId,previewFingerprint:fresh.previewFingerprint})).rejects.toThrow();}
   finally{await db.$executeRawUnsafe('DROP TRIGGER roster60_craft_fail');}
   expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(2);
   expect(await db.roster60CraftReceipt.count({where:{playerId:owner,requestId}})).toBe(0);
 });
 it('simultaneous duplicate requests create only one result; retries converge',async()=>{
   const a=await material(),b=await material(),s=selection([a.id,b.id]),p=await service.preview(owner,s);
   const request={...s,requestId:randomUUID(),previewFingerprint:p.previewFingerprint};
   const attempts=await Promise.allSettled([service.confirm(owner,request),service.confirm(owner,request)]);
   expect(attempts.some(r=>r.status==='fulfilled')).toBe(true);
   const result=await service.confirm(owner,request);
   expect(await db.roster60Owned.count({where:{playerId:owner,source:'craft:'+request.requestId}})).toBe(1);
   for(const a of attempts)if(a.status==='fulfilled')expect(a.value.ownedId).toBe(result.ownedId);
 },20000);
 it('same-version catalog edits cannot silently change the previewed result level',async()=>{
   const a=await material(11),b=await material(16),s=selection([a.id,b.id]),p=await service.preview(owner,s);
   const target=catalog.characters.find(u=>u.id==='u09')!,old=target.growth[1].totalXp;
   target.growth[1].totalXp=20;
   try{await expect(service.confirm(owner,{...s,requestId:randomUUID(),previewFingerprint:p.previewFingerprint})).rejects.toThrow('PREVIEW_CHANGED');}
   finally{target.growth[1].totalXp=old;}
   expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(2);
 });
});
