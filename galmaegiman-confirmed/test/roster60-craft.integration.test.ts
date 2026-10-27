import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {RosterCraft} from '../src/roster60-craft.js';
import {RosterLibrary} from '../src/roster60-library.js';
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
 afterAll(async()=>{if(db){await db.auditEvent.deleteMany({where:{playerId:{in:[owner,other]}}});await db.dailyProgress.deleteMany({where:{playerId:{in:[owner,other]}}});await db.player.deleteMany({where:{id:{in:[owner,other]}}});await db.$disconnect();}});
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
 it('hidden recipe names require discovery or complete materials; prepared materials sort first without consumption',async()=>{
   const player=(await db.player.create({data:{identity:'craft-discovery-'+randomUUID()}})).id;
   const extra={...catalog.characters[1],id:'u10',name:'hidden-not-found'};
   const c:RosterCatalog={...catalog,characters:[...catalog.characters,extra],recipes:[
     {resultId:'u10',visibility:'hidden',ingredients:[{unitId:'u09',count:1,minLevel:2}]},
     {resultId:'u09',visibility:'public',ingredients:[{unitId:'u02',count:2,minLevel:2}]}
   ]};
   const craft=new RosterCraft(db,()=>c);
   try{
     const legacy=await craft.options(player);expect(legacy.discoveryVersion).toBeUndefined();expect(legacy.recipes.some(r=>r.resultId==='u10')).toBe(false);
     let options=await craft.options(player,true);
     expect(options.recipes.find(r=>r.resultId==='u10')).toMatchObject({name:'???',nameVisible:false,imageVisible:false,discovered:false});
     const a=await material(11,player),b=await material(0,player);
     options=await craft.options(player,true);
     expect(options.recipes[0]).toMatchObject({resultId:'u09',name:'u09',materialsReady:true,craftable:false,levelRestricted:true,imageVisible:false});
     expect(options.recipes[0].ingredients[0]).toMatchObject({availableCount:2,readyCount:1,minLevel:2});
     expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(2);
     expect(await db.roster60CraftReceipt.count({where:{playerId:player}})).toBe(0);
     await db.roster60Owned.update({where:{id:b.id},data:{totalXp:11n}});
     expect((await craft.options(player,true)).recipes[0]).toMatchObject({craftable:true,levelRestricted:false});
   }finally{await db.player.delete({where:{id:player}});}
 });
 it('discovered outcomes include consumed acquisitions and encountered opponents without exposing defense tables',async()=>{
   const player=(await db.player.create({data:{identity:'craft-encounter-'+randomUUID()}})).id;
   try{
     await db.roster60Owned.create({data:{playerId:player,speciesId:'u02',status:'CONSUMED',source:'synthetic'}});
     await db.roster60Battle.create({data:{id:randomUUID(),playerId:player,ownedId:'archived-synthetic',seed:'private',snapshot:'{}',state:JSON.stringify({fighters:[{speciesId:'u02'},{speciesId:'u09',defense:{secret:true}}],events:[]})}});
     const options=await service.options(player,true);
     expect(options.recipes[0]).toMatchObject({resultId:'u09',name:'u09',discovered:true,imageVisible:true,materialsReady:false});
     expect(JSON.stringify(options)).not.toContain('secret');
     expect(options.units).toHaveLength(0);
     expect((await new RosterLibrary(db,()=>catalog).collection(player)).characters.find(u=>u.id==='u09')?.discovered).toBe(true);
   }finally{await db.player.delete({where:{id:player}});}
 });
 it('discovery projects long history without forgetting encounters or revealing private reserves',async()=>{
   const player=(await db.player.create({data:{identity:'craft-history-'+randomUUID()}})).id;
   try{
     const history=Array.from({length:200},(_,i)=>({id:'history-'+player+'-'+String(i).padStart(3,'0'),playerId:player,ownedId:'synthetic',seed:'private',snapshot:'{}',state:JSON.stringify({fighters:[{speciesId:'u02'},{speciesId:'u02'}],reserves:[{speciesId:'u10'}],padding:'x'.repeat(16384),events:i===199?[{events:[{presentation:{before:[{speciesId:'u02'},{speciesId:'u09'}],after:[{speciesId:'u02'},{speciesId:'u02'}]}}]}]:[]})}));
     await db.roster60Battle.createMany({data:history});
     await db.roster60Battle.createMany({data:['invalid-json',JSON.stringify({events:['bad',{events:['bad',3]}]})].map(state=>({id:randomUUID(),playerId:player,ownedId:'synthetic',seed:'private',snapshot:'{}',state}))});
     const extra={...catalog.characters[1],id:'u10',name:'private reserve'};
     const c:RosterCatalog={...catalog,characters:[...catalog.characters,extra],recipes:[...(catalog.recipes??[]),{resultId:'u10',visibility:'hidden',ingredients:[{unitId:'u09',count:1,minLevel:2}]}]};
     const began=performance.now(),options=await new RosterCraft(db,()=>c).options(player,true);
     console.log('조합 발견 투영 호스트 측정',JSON.stringify({historyRows:history.length,historicalJsonBytes:history.reduce((n,row)=>n+Buffer.byteLength(row.state),0),elapsedMs:Math.round(performance.now()-began),returnedRecipeCount:options.recipes.length}));
     expect(options.recipes.find(r=>r.resultId==='u09')).toMatchObject({discovered:true,imageVisible:true});
     expect(options.recipes.find(r=>r.resultId==='u10')).toMatchObject({discovered:false,name:'???',imageVisible:false});
   }finally{await db.player.delete({where:{id:player}});}
 });
 it('reborn materials cannot implicitly transfer eligibility',async()=>{
 const a=await material(),b=await material();await db.roster60Owned.update({where:{id:a.id},data:{reincarnation:1}});
 expect((await service.options(owner)).units.find(u=>u.ownedId===a.id)?.maxLevel).toBe(99);
 await expect(service.preview(owner,selection([a.id,b.id]))).rejects.toThrow('CRAFT_REINCARNATION_POLICY_REQUIRED');
 expect(await db.roster60Owned.count({where:{id:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(2);
 });
 it('first reincarnation execution stays closed without eligibility/cost and cannot stack on retry',async()=>{
 const a=await material(11),service=new RosterReincarnation(db,()=>catalog);
 for(let i=0;i<2;i++)await expect(service.confirm(owner,{ownedId:a.id,requestId:'blocked-test',catalogVersion:catalog.version,previewFingerprint:'0'.repeat(64)})).rejects.toThrow('LEVEL_ONE_BASE_STATS_REQUIRED');
 await expect(service.confirm(other,{ownedId:a.id,requestId:'foreign-test',catalogVersion:catalog.version,previewFingerprint:'0'.repeat(64)})).rejects.toThrow('UNIT_NOT_FOUND');
 const after=await db.roster60Owned.findUniqueOrThrow({where:{id:a.id}});expect(after.totalXp).toBe(11n);expect(after.reincarnation).toBe(0);
 });
 it('real preview service preserves baseline stats and suitability while hiding unobserved defense',async()=>{
 const a=await material(11),d=catalog.characters.find(u=>u.id==='u02')!;
 (d as any).levelOneStats={hp:100,mp:7,patk:20,matk:30,pdef:40,mdef:50};
 try{const response=await new RosterReincarnation(db,()=>catalog).preview(owner);
 const c=response.candidates.find(x=>x.ownedId===a.id)!;expect(c.eligible).toBe(false);
 expect(c.preview).toMatchObject({next:{level:1,totalXp:0,maxLevel:99},afterLevelOneStats:{hp:110,mp:8,patk:22,matk:33,pdef:44,mdef:55},suitability:d.suitability});
 expect(c.preview?.defense).toBeUndefined();expect(d.defense).toBeDefined();
 if(!('previewFingerprint' in c))throw Error('EXPECTED_REBIRTH_PREVIEW');
 const before=c.previewFingerprint,original=d.defense.FIRE;d.defense.FIRE=original==='normal'?'immune':'normal';
 try{const after=(await new RosterReincarnation(db,()=>catalog).preview(owner)).candidates.find(x=>x.ownedId===a.id)!;if(!('previewFingerprint' in after))throw Error('EXPECTED_REBIRTH_PREVIEW');expect(after.previewFingerprint).toBe(before);}
 finally{d.defense.FIRE=original;}
 }finally{delete (d as any).levelOneStats;}
 });
 it('preview floors average cumulative XP then calculates result level, never consumes',async()=>{
   const a=await material(11),b=await material(16);
   const p=await service.preview(owner,selection([a.id,b.id]));
   expect(p).toMatchObject({inheritedTotalXp:13,expectedLevel:2,remainingXp:1,xpToNext:null});
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
