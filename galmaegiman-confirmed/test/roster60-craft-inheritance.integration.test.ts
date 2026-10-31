import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {createHash,randomUUID} from 'node:crypto';
import {mkdirSync,mkdtempSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import Fastify from 'fastify';
import {RosterCraft,type CraftSelection} from '../src/roster60-craft.js';
import {RosterOwned} from '../src/roster60-owned.js';
import {RosterBattle,fighter} from '../src/roster60-battle.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterPvp} from '../src/roster60-pvp.js';
import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {registerRoster60} from '../src/roster60-routes.js';
import {skillInheritanceVersion,type StoredSkillInheritance} from '../src/roster60-skill-inheritance.js';

const base=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const basePolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const normalXpPolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authenticated one-skill combination inheritance',()=>{
 let db:PrismaClient,c:any,policy:any,player:string,other:string,craft:RosterCraft;
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{
  c=structuredClone(base);policy=structuredClone(basePolicy);
  // Transaction fixture changes the recipe only, retaining actual approved skill/growth definitions.
  c.recipes=[{resultId:'u09',ingredients:[{unitId:'u06',count:2,minLevel:1}],visibility:'public'}];
  player=(await db.player.create({data:{identity:'inherit-owner:'+randomUUID(),peanut:100}})).id;
  other=(await db.player.create({data:{identity:'inherit-other:'+randomUUID(),peanut:100}})).id;
  craft=new RosterCraft(db,()=>c);
 });
 afterEach(async()=>{
  await db.roster60PvpMatch.deleteMany({where:{OR:[{player0Id:{in:[player,other]}},{player1Id:{in:[player,other]}}]}});
  await db.auditEvent.deleteMany({where:{playerId:{in:[player,other]}}});
  await db.dailyProgress.deleteMany({where:{playerId:{in:[player,other]}}});
  await db.player.deleteMany({where:{id:{in:[player,other]}}});
 });
 afterAll(async()=>{await db.$disconnect();});
 const definition=(id:string)=>c.characters.find((unit:any)=>unit.id===id);
 async function owned(speciesId='u06',level=1,owner=player,extra:Record<string,unknown>={}){
  return db.roster60Owned.create({data:{playerId:owner,speciesId,totalXp:BigInt(definition(speciesId).growth[level-1].totalXp),source:randomUUID(),...extra}});
 }
 async function materials(high=45,low=1){return [await owned('u06',high),await owned('u06',low)];}
 function input(rows:{id:string}[],choice:CraftSelection['inheritance']|null={skillId:'water_4',replaceSlot:0}):CraftSelection{return {resultId:'u09',materialIds:rows.map(row=>row.id),catalogVersion:c.version,...(choice?{inheritance:choice}:{})};}
 async function complete(rows?:Awaited<ReturnType<typeof materials>>){const units=rows??await materials(),selection=input(units),preview=await craft.preview(player,selection),request={...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint};return {rows:units,selection,preview,request,result:await craft.confirm(player,request)};}

 it('preview exposes one row per actually learned skill and authoritative source, MP, element and allowed slots',async()=>{
  const rows=await materials(45,5),selection=input(rows,null),preview=await craft.preview(player,selection);
  expect(preview.inheritance.status).toBe('selection-required');expect(preview.inheritance.selected).toBeNull();
  expect(new Set(preview.inheritance.candidates.map(value=>value.skillId)).size).toBe(preview.inheritance.candidates.length);
  const high=preview.inheritance.candidates.find(value=>value.skillId==='water_4')!;
  expect(high).toMatchObject({name:definition('u06').skills[4].name,mpCost:22,element:'WATER',requiredLevel:40,source:{ownedId:rows[0].id,speciesId:'u06',sourceSpeciesId:'u06',sourceSlot:4,materialSkillSlot:4},allowedReplaceSlots:[0,1,2,3,4]});
  expect(high.source.name).toBe(definition('u06').name);expect(high.source.level).toBe(45);
  expect(preview.inheritance.resultSkillSlots).toHaveLength(5);
  expect(await db.roster60Owned.count({where:{id:{in:selection.materialIds},status:'AVAILABLE'}})).toBe(2);
 });
 it('new confirmation without exactly one selection is rejected without consuming materials',async()=>{
  const rows=await materials(),selection=input(rows,null),preview=await craft.preview(player,selection);
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint})).rejects.toMatchObject({code:'INHERITANCE_SELECTION_REQUIRED'});
  expect(await db.roster60Owned.count({where:{id:{in:selection.materialIds},status:'AVAILABLE'}})).toBe(2);
  expect(await db.roster60CraftReceipt.count({where:{playerId:player}})).toBe(0);
 });
 it('a high-source move cannot unlock through the replaced low-level slot in owned reads or real battle commands',async()=>{
  const {result}=await complete(await materials(40,1));expect(result.expectedLevel).toBeLessThan(40);
  const row=await db.roster60Owned.findUniqueOrThrow({where:{id:result.ownedId}}),saved=JSON.parse(row.skillInheritanceJson!);
  expect(saved).toMatchObject({replaceSlot:0,skillId:'water_4',requiredLevel:40,sourceSpeciesId:'u06',sourceSlot:4});
  const view=(await new RosterOwned(db,()=>c).read(player,row.id)).unit;
  expect(view.skills.slots[0]).toMatchObject({id:'water_4',learnLevel:40,unlocked:false,inherited:true});
  expect(view.skills.slots[0].effectText).toBe('위력 68');expect(view.skills.current.some(value=>value.id==='water_4')).toBe(false);
  expect(fighter(c,'u09',40,row.id,0,row.skillInheritanceJson).skills.some(value=>value.id==='water_4')).toBe(true);
  const battle=new RosterBattle(db,()=>c,()=>policy),created:any=await battle.create(player,{requestId:randomUUID(),ownedId:row.id,encounterId:'tutorial'});
  expect(created.state.fighters[0].skills.some((value:any)=>value.id==='water_4')).toBe(false);
  expect(created.state.fighters[0].attack).toEqual(definition('u09').attack);
  await expect(battle.action(player,{requestId:randomUUID(),battleId:created.battleId,revision:created.revision,action:{kind:'skill',skillId:'water_4'}})).rejects.toMatchObject({code:'SKILL_NOT_EQUIPPED'});
 });
 it('ordinary attacks and still-locked material moves never enter the candidate pool',async()=>{
  const rows=await materials(5,1),preview=await craft.preview(player,input(rows,null));
  expect(preview.inheritance.candidates.some(value=>value.skillId===definition('u06').attack.id)).toBe(false);
  expect(preview.inheritance.candidates.some(value=>value.skillId==='water_4')).toBe(false);
  await expect(craft.preview(player,input(rows,{skillId:definition('u06').attack.id,replaceSlot:0}))).rejects.toMatchObject({code:'INVALID_INHERITANCE_SKILL'});
  await expect(craft.preview(player,input(rows,{skillId:'water_1',replaceSlot:5}))).rejects.toMatchObject({code:'INVALID_INHERITANCE_SLOT'});
 });
 it('foreign, consumed and locked material IDs cannot mint or expose inheritance',async()=>{
  const a=await owned('u06',45),foreign=await owned('u06',45,other),locked=await owned('u06',45,player,{locked:true}),consumed=await owned('u06',45,player,{status:'CONSUMED'});
  for(const b of [foreign,locked,consumed])await expect(craft.preview(player,input([a,b]))).rejects.toMatchObject({code:'MATERIALS_UNAVAILABLE'});
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.id}})).status).toBe('AVAILABLE');
 });
 it('changing any material selection, XP or prior inheritance invalidates the old proof',async()=>{
  const rows=await materials(),selection=input(rows),preview=await craft.preview(player,selection),replacement=await owned('u06',1);
  await expect(craft.confirm(player,{...selection,materialIds:[rows[0].id,replacement.id],requestId:randomUUID(),previewFingerprint:preview.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
  await db.roster60Owned.update({where:{id:rows[1].id},data:{totalXp:rows[1].totalXp+1n}});
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
  const refreshed=await craft.preview(player,selection),sameKind:StoredSkillInheritance={version:skillInheritanceVersion,replaceSlot:0,skillId:'water_1',sourceSpeciesId:'u06',sourceSlot:0,requiredLevel:1,catalogVersion:c.version};
  await db.roster60Owned.update({where:{id:rows[1].id},data:{skillInheritanceJson:JSON.stringify(sameKind)}});
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:refreshed.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
  expect(await db.roster60Owned.count({where:{id:{in:selection.materialIds},status:'AVAILABLE'}})).toBe(2);
 });
 it('same-version source MP or unlock changes require a fresh preview',async()=>{
  const rows=await materials(45,1),selection=input(rows),preview=await craft.preview(player,selection),source=definition('u06').skills[4];
  source.mpCost++;
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
  const fresh=await craft.preview(player,selection);source.learnLevel=41;
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:fresh.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
  expect(await db.roster60Owned.count({where:{id:{in:selection.materialIds},status:'AVAILABLE'}})).toBe(2);
 });
 it('same-version target slot edits cannot silently change the selected replacement',async()=>{
  const rows=await materials(),selection=input(rows),preview=await craft.preview(player,selection);definition('u09').skills[0].learnLevel=2;
  await expect(craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint})).rejects.toMatchObject({code:'PREVIEW_CHANGED'});
 });
 it('one successful transaction records one override and retries converge without a second grant',async()=>{
  const {rows,request,result}=await complete();
  expect(await craft.confirm(player,{...request,materialIds:[...request.materialIds].reverse()})).toEqual(result);
  expect(await new RosterCraft(db,()=>c).receipt(player,request.requestId)).toEqual({status:'completed',result});
  expect(await craft.receipt(other,request.requestId)).toEqual({status:'unknown'});
  expect(await db.roster60Owned.count({where:{playerId:player,source:'craft:'+request.requestId}})).toBe(1);
  expect(await db.roster60Owned.count({where:{id:{in:rows.map(row=>row.id)},status:'CONSUMED'}})).toBe(2);
  await expect(craft.confirm(player,{...request,inheritance:{skillId:'water_2',replaceSlot:0}})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
 });
 it('completed pre-inheritance receipts keep their original hash and replay even after materials are gone',async()=>{
  const legacy={resultId:'u09',materialIds:['already-consumed-a','already-consumed-b'],catalogVersion:c.version},previewFingerprint='a'.repeat(64),requestId=randomUUID();
  const fingerprint=createHash('sha256').update(JSON.stringify([[legacy.resultId,[...legacy.materialIds].sort(),legacy.catalogVersion],previewFingerprint])).digest('hex'),result={ownedId:'historical-unit',legacy:true};
  await db.roster60CraftReceipt.create({data:{playerId:player,requestId,fingerprint,result:JSON.stringify(result)}});
  expect(await craft.confirm(player,{...legacy,requestId,previewFingerprint})).toEqual(result);
  expect(await craft.receipt(other,requestId)).toEqual({status:'unknown'});
 });
 it('parallel duplicate and competing confirmations cannot double-consume or double-grant',async()=>{
  const rows=await materials(),selection=input(rows),preview=await craft.preview(player,selection),request={...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint};
  const same=await Promise.all([craft.confirm(player,request),craft.confirm(player,request)]);expect(same[0]).toEqual(same[1]);
  expect(await db.roster60Owned.count({where:{playerId:player,source:'craft:'+request.requestId}})).toBe(1);
  const nextRows=await materials(),next=input(nextRows),nextPreview=await craft.preview(player,next);
  const competing=await Promise.allSettled([randomUUID(),randomUUID()].map(requestId=>craft.confirm(player,{...next,requestId,previewFingerprint:nextPreview.previewFingerprint})));
  expect(competing.filter(value=>value.status==='fulfilled')).toHaveLength(1);
  expect(await db.roster60Owned.count({where:{playerId:player,speciesId:'u09'}})).toBe(2);
 });
 it('result insertion and receipt insertion failures roll every consume and grant back',async()=>{
  for(const trigger of ["CREATE TRIGGER inheritance_grant_fail BEFORE INSERT ON Roster60Owned WHEN NEW.source LIKE 'craft:%' BEGIN SELECT RAISE(ABORT,'synthetic grant failure'); END","CREATE TRIGGER inheritance_receipt_fail BEFORE INSERT ON Roster60CraftReceipt BEGIN SELECT RAISE(ABORT,'synthetic receipt failure'); END"]){
   const rows=await materials(),selection=input(rows),preview=await craft.preview(player,selection),requestId=randomUUID();
   await db.$executeRawUnsafe(trigger);
   try{await expect(craft.confirm(player,{...selection,requestId,previewFingerprint:preview.previewFingerprint})).rejects.toThrow();}
   finally{await db.$executeRawUnsafe('DROP TRIGGER IF EXISTS inheritance_grant_fail');await db.$executeRawUnsafe('DROP TRIGGER IF EXISTS inheritance_receipt_fail');}
   expect(await db.roster60Owned.count({where:{id:{in:selection.materialIds},status:'AVAILABLE'}})).toBe(2);
   expect(await db.roster60Owned.count({where:{playerId:player,source:'craft:'+requestId}})).toBe(0);expect(await db.roster60CraftReceipt.count({where:{playerId:player,requestId}})).toBe(0);
  }
 });
 it('a private instance override cannot mutate its species or another same-species instance',async()=>{
  const original=JSON.stringify(definition('u09').skills),{result}=await complete(),peer=await owned('u09',45),read=new RosterOwned(db,()=>c);
  expect((await read.read(player,result.ownedId)).unit.skills.slots[0].id).toBe('water_4');
  expect((await read.read(player,peer.id)).unit.skills.slots[0].id).toBe('normal_1');
  expect(JSON.stringify(definition('u09').skills)).toBe(original);
  await expect(read.read(other,result.ownedId)).rejects.toMatchObject({status:404,code:'UNIT_NOT_FOUND'});
 });
 it('real rebirth preserves the same metadata and reapplies its source level lock at level one',async()=>{
  const {result}=await complete();definition('u09').rarity='LIMITED';
  await db.roster60Owned.update({where:{id:result.ownedId},data:{totalXp:BigInt(definition('u09').growth[59].totalXp)}});
  const before=await db.roster60Owned.findUniqueOrThrow({where:{id:result.ownedId}}),rebirth=new RosterReincarnation(db,()=>c),preview=await rebirth.preview(player),candidate=preview.candidates.find(value=>value.ownedId===result.ownedId)!;
  expect(candidate.eligible).toBe(true);if(!('previewFingerprint' in candidate))throw Error('missing rebirth proof');
  await rebirth.confirm(player,{ownedId:result.ownedId,requestId:randomUUID(),catalogVersion:c.version,previewFingerprint:candidate.previewFingerprint});
  const row=await db.roster60Owned.findUniqueOrThrow({where:{id:result.ownedId}});expect(row.id).toBe(before.id);expect(row.skillInheritanceJson).toBe(before.skillInheritanceJson);expect(row.totalXp).toBe(0n);expect(row.reincarnation).toBe(1);
  const read=(await new RosterOwned(db,()=>c).read(player,row.id)).unit;expect(read.level).toBe(1);expect(read.skills.slots[0]).toMatchObject({id:'water_4',learnLevel:40,unlocked:false,inherited:true});
 });
 it('recraft keeps only the chosen inherited origin instead of stacking every material override',async()=>{
  const {result}=await complete();await db.roster60Owned.update({where:{id:result.ownedId},data:{totalXp:BigInt(definition('u09').growth[44].totalXp)}});
  const otherOverride:StoredSkillInheritance={version:skillInheritanceVersion,replaceSlot:4,skillId:'normal_4',sourceSpeciesId:'u01',sourceSlot:4,requiredLevel:40,catalogVersion:c.version};
  const second=await owned('u06',45,player,{skillInheritanceJson:JSON.stringify(otherOverride)});
  c.recipes.push({resultId:'u12',ingredients:[{unitId:'u09',count:1,minLevel:1},{unitId:'u06',count:1,minLevel:1}],visibility:'public'});
  const selection={resultId:'u12',materialIds:[result.ownedId,second.id],catalogVersion:c.version,inheritance:{skillId:'water_4',replaceSlot:4}},preview=await craft.preview(player,selection);
  expect(preview.inheritance.selected?.source).toMatchObject({ownedId:result.ownedId,speciesId:'u09',sourceSpeciesId:'u06',sourceSlot:4});
  const next=await craft.confirm(player,{...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint}),row=await db.roster60Owned.findUniqueOrThrow({where:{id:next.ownedId}}),stored=JSON.parse(row.skillInheritanceJson!);
  expect(stored).toMatchObject({skillId:'water_4',sourceSpeciesId:'u06',sourceSlot:4,requiredLevel:40,replaceSlot:4});
  const slots=(await new RosterOwned(db,()=>c).read(player,row.id)).unit.skills.slots;expect(slots).toHaveLength(5);expect(slots.filter(value=>value.inherited)).toHaveLength(1);expect(slots.some(value=>value.id==='normal_4')).toBe(false);
 });
 it('adventure snapshots use the private equipped set and existing active snapshots remain unchanged',async()=>{
  const {result}=await complete();await db.roster60Owned.update({where:{id:result.ownedId},data:{totalXp:BigInt(definition('u09').growth[44].totalXp)}});
  const adventures=new RosterAdventure(db,()=>c,fighter),active:any=await adventures.start(player,{requestId:randomUUID(),ownedIds:[result.ownedId]});expect(active.party[0].skills.some((value:any)=>value.id==='water_4')).toBe(true);
  const before=JSON.stringify(active.party);await db.roster60Owned.update({where:{id:result.ownedId},data:{skillInheritanceJson:null}});
  expect(JSON.stringify((await adventures.read(player,active.adventureId)).party)).toBe(before);
 });
 it('legacy six-member battle snapshots keep their original skills without reprojecting later owned metadata',async()=>{
  const rows=await Promise.all(Array.from({length:6},()=>owned('u09',45))),battle=new RosterBattle(db,()=>c,()=>policy),created:any=await battle.create(player,{requestId:randomUUID(),ownedId:rows[0].id,encounterId:'tutorial'});
  const record=await db.roster60Battle.findUniqueOrThrow({where:{id:created.battleId}}),state=JSON.parse(record.state),snapshot=JSON.parse(record.snapshot);
  state.reserves=rows.slice(1).map(row=>({...fighter(c,'u09',45,'self',0),ownedId:row.id}));snapshot.members=rows.map(row=>({ownedId:row.id,startingXp:row.totalXp.toString()}));
  await db.roster60Battle.update({where:{id:record.id},data:{state:JSON.stringify(state),snapshot:JSON.stringify(snapshot)}});
  await db.roster60Owned.updateMany({where:{id:{in:rows.map(row=>row.id)}},data:{status:'BATTLE'}});
  const override:StoredSkillInheritance={version:skillInheritanceVersion,replaceSlot:0,skillId:'water_4',sourceSpeciesId:'u06',sourceSlot:4,requiredLevel:40,catalogVersion:c.version};
  await db.roster60Owned.update({where:{id:rows[0].id},data:{skillInheritanceJson:JSON.stringify(override)}});
  const saved=await battle.read(player,created.battleId);expect(saved.state.reserves).toHaveLength(5);
  expect(saved.state.fighters[0].skills).toEqual(created.state.fighters[0].skills);expect(saved.state.fighters[0].skills.some((value:any)=>value.id==='water_4')).toBe(false);
  expect(JSON.stringify(saved.state)).not.toContain('skillInheritanceJson');expect(JSON.stringify(saved.state)).not.toContain('sourceSpeciesId');
 });
 it('human normal PvP reads inherited skills while ranked normalization retains its existing level-lock rule',async()=>{
  for(const mode of ['normal','rank'] as const){
   const {result,rows}=await complete();const level=mode==='rank'?60:45;
   await db.roster60Owned.update({where:{id:result.ownedId},data:{totalXp:BigInt(definition('u09').growth[level-1].totalXp)}});
   const enemy=await owned('u02',level,other),pvp=new RosterPvp(db,()=>c,()=>policy,()=>Date.now(),()=>normalXpPolicy,async()=>60);
   await new RosterOnboarding(db,()=>c).start(player);await new RosterOnboarding(db,()=>c).start(other);
   await pvp.party(player,[result.ownedId]);await pvp.party(other,[enemy.id]);await pvp.join(player,{requestId:randomUUID(),mode});await pvp.join(other,{requestId:randomUUID(),mode});
   const view:any=await pvp.current(player);expect(view.status).toBe('matched');
   expect(view.battle.state.fighters[0].skills.some((value:any)=>value.id==='water_4')).toBe(mode==='normal');
   expect(view.battle.state.fighters[0].attack.id).toBe(definition('u09').attack.id);
   const opponentView:any=await pvp.current(other),publicState=JSON.stringify(opponentView.battle.state);
   for(const hidden of [rows[0].id,rows[1].id,'skillInheritanceJson','sourceSpeciesId','sourceSlot','requiredLevel',skillInheritanceVersion])expect(publicState).not.toContain(hidden);
   expect(opponentView.battle.state.fighters[1].ownResistance).toBeUndefined();
   expect(opponentView.battle.state.fighters[0].ownResistance).toEqual(definition('u02').defense);
   expect(opponentView.battle.state.fighters[1].defense).toBeUndefined();
   if(mode==='rank')expect(opponentView.battle.state.fighters[1].skills.some((value:any)=>value.id==='water_4')).toBe(false);
   await pvp.accept(player,{requestId:randomUUID(),battleId:view.battle.battleId,revision:view.battle.revision,accept:false});
   for(const owner of [player,other]){const end:any=await pvp.current(owner);if(end.status==='matched')await pvp.settle(owner,{requestId:randomUUID(),battleId:end.battle.battleId,revision:end.battle.revision});}
  }
 });
 it('the real u37 all-duplicate edge allows one matching-slot replacement without inventing a sixth move',async()=>{
  const recipe=base.recipes.find((value:any)=>value.resultId==='u37');c.recipes=[structuredClone(recipe)];
  const rows=await Promise.all(recipe.ingredients.map((value:any)=>owned(value.unitId,value.minLevel)));
  const selection={resultId:'u37',materialIds:rows.map(row=>row.id),catalogVersion:c.version},preview=await craft.preview(player,selection),candidate=preview.inheritance.candidates[0];
  expect(preview.inheritance.candidates.every(value=>value.allowedReplaceSlots.length===1)).toBe(true);
  const selected={...selection,inheritance:{skillId:candidate.skillId,replaceSlot:candidate.allowedReplaceSlots[0]}},confirmed=await craft.preview(player,selected);expect(confirmed.inheritance.selected?.kindUnchanged).toBe(true);
  const result=await craft.confirm(player,{...selected,requestId:randomUUID(),previewFingerprint:confirmed.previewFingerprint}),read=(await new RosterOwned(db,()=>c).read(player,result.ownedId)).unit;
  expect(read.skills.slots).toHaveLength(definition('u37').skills.length);expect(new Set(read.skills.slots.map(value=>value.id)).size).toBe(read.skills.slots.length);expect(read.skills.slots.filter(value=>value.inherited)).toHaveLength(1);
 });
 it('authenticated API rejects client numbers, extra skills and forged unlock levels, then confirms the exact choice',async()=>{
  const rows=await materials(40,1),root=mkdtempSync(join(tmpdir(),'inheritance-api-'));mkdirSync(join(root,'data'));writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(c));
  const previous=process.env.ROSTER60_PROFILE;delete process.env.ROSTER60_PROFILE;const app=Fastify();registerRoster60(app,db,root,request=>[player,other].includes(String(request.headers.authorization))?String(request.headers.authorization):null,()=>true);
  const post=(body:unknown,path='preview',owner=player)=>app.inject({method:'POST',url:'/my/api/native/roster60/craft/'+path,headers:{authorization:owner},payload:body as any});
  try{
   const selection=input(rows),first=await post(input(rows,null)),preview=await post(selection);expect(first.statusCode,first.body).toBe(200);expect(preview.statusCode,preview.body).toBe(200);const p=preview.json();
   for(const inheritance of [{...selection.inheritance,requiredLevel:1},{...selection.inheritance,mpCost:0},{...selection.inheritance,skills:['water_4','normal_4']},{skillId:'water_4',replaceSlot:-1}])expect((await post({...selection,inheritance})).statusCode).toBe(400);
   expect((await post(selection,'preview',other)).statusCode).toBe(409);
   const done=await post({...selection,requestId:'inheritance-public-fixture',previewFingerprint:p.previewFingerprint},'confirm');expect(done.statusCode,done.body).toBe(200);expect(done.json().inheritance.selected).toMatchObject({skillId:'water_4',requiredLevel:40,replaceSlot:0,unlockedAtResultLevel:false});
   const out='/workspace/scratch/inherit-server-09/api-fixtures';mkdirSync(out,{recursive:true});
   writeFileSync(join(out,'selection-required-preview.json'),JSON.stringify(first.json(),null,2)+'\n');writeFileSync(join(out,'selected-preview.json'),JSON.stringify(p,null,2)+'\n');writeFileSync(join(out,'confirmed.json'),JSON.stringify(done.json(),null,2)+'\n');
  }finally{await app.close();rmSync(root,{recursive:true,force:true});if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;}
 });
});
