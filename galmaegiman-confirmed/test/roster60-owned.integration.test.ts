import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import Fastify from 'fastify';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {mkdtempSync,mkdirSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {RosterOwned} from '../src/roster60-owned.js';
import {RosterBattle,fighter} from '../src/roster60-battle.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {RosterLibrary} from '../src/roster60-library.js';
import {registerRoster60} from '../src/roster60-routes.js';
import {fiveStatKeys} from '../src/battle/roster60-five-stats.js';
import {publicDefenseSafe} from '../src/battle/roster60-observed-defense.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const battlePolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));

suite('내 갈매미: authenticated exact owned-instance reads',()=>{
 let db:PrismaClient,player:string,other:string,service:RosterOwned;
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{
  player=(await db.player.create({data:{identity:'owned-read:'+randomUUID(),peanut:10}})).id;
  other=(await db.player.create({data:{identity:'owned-read:'+randomUUID()}})).id;
  service=new RosterOwned(db,()=>catalog,()=>12345);
 });
 afterEach(async()=>{await db.player.deleteMany({where:{id:{in:[player,other]}}});});
 afterAll(async()=>{await db.$disconnect();});
 const definition=(speciesId='u01')=>catalog.characters.find((unit:any)=>unit.id===speciesId);
 async function own(speciesId='u01',level=1,owner?:string,extra:Record<string,unknown>={}){
  return db.roster60Owned.create({data:{playerId:owner??player,speciesId,totalXp:BigInt(definition(speciesId).growth[level-1].totalXp),source:randomUUID(),...extra}});
 }

 it('lists every live individual including same species, locked and active units without cross-account entries',async()=>{
  const low=await own(),high=await own('u01',10),locked=await own('u02',3,player,{locked:true}),active=await own('u03',5,player,{status:'BATTLE'});
  const consumed=await own('u01',20,player,{status:'CONSUMED'}),foreign=await own('u01',30,other);
  const result=await service.list(player);
  expect(result).toMatchObject({flow:'roster60-owned-v1',catalogVersion:catalog.version,serverNow:12345});
  expect(result.units.map(unit=>unit.ownedId)).toEqual([high.id,active.id,locked.id,low.id]);
  expect(result.units.filter(unit=>unit.speciesId==='u01')).toHaveLength(2);
  expect(result.units.some(unit=>[consumed.id,foreign.id].includes(unit.ownedId))).toBe(false);
  expect(result.units.find(unit=>unit.ownedId===active.id)?.status).toBe('BATTLE');
  expect(result.units.find(unit=>unit.ownedId===locked.id)?.locked).toBe(true);
  expect((await service.list(other)).units.map(unit=>unit.ownedId)).toEqual([foreign.id]);
 });

 it('uses exact owned growth and decomposes authoritative final stats without invented bonus or skill stages',async()=>{
  const row=await own('u45',20,player,{reincarnation:2});
  // Use the approved per-instance XP curve rather than assuming pre-rebirth thresholds.
  const peer=await own('u45',1),before=await db.roster60Owned.findUniqueOrThrow({where:{id:row.id}});
  await db.roster60PvpParty.create({data:{playerId:player,ownedIds:JSON.stringify([peer.id,row.id])}});
  const result=await service.read(player,row.id),unit=result.unit;
  expect(unit.ownedId).toBe(row.id);expect(unit.speciesId).toBe('u45');
  expect(unit.stats).toEqual(fighter(catalog,'u45',unit.level,row.id,2).stats);
  for(const key of fiveStatKeys)expect(unit.statBreakdown.base[key]+unit.statBreakdown.growth[key]+unit.statBreakdown.rebirth[key]).toBe(unit.stats[key]);
  expect(unit.statBreakdown.total).toEqual(unit.stats);expect(unit.statBreakdown.other).toBeNull();
  expect(unit.statBreakdown.otherStatus).toBe('NO_OTHER_BONUS_MODEL');
  expect(unit.aptitude).toEqual(definition('u45').suitability);expect(unit.ownResistance).toEqual(definition('u45').defense);
  expect(unit.party.pvp).toEqual({position:2,size:2});
  expect(unit.skills.growth).toEqual({status:'DESIGN_NEEDED',reason:'NO_PER_INDIVIDUAL_SKILL_GROWTH_MODEL'});
  expect(unit.skills.current.every(skill=>skill.learnLevel<=unit.level)).toBe(true);
  expect(unit.skills.current.every(skill=>skill.effectText==='위력 '+skill.power)).toBe(true);
  expect(unit.growth.levelStartTotalXp+unit.growth.xp).toBe(unit.growth.totalXp);
  expect(unit.growth.nextLevelTotalXp).toBe(unit.growth.totalXp+unit.growth.xpToNext!);
  expect((await service.read(player,peer.id)).unit).toMatchObject({level:1,reincarnation:0,party:{pvp:{position:1,size:2}}});
  expect(await db.roster60Owned.findUniqueOrThrow({where:{id:row.id}})).toEqual(before);
 });

 it('returns the same 404 for a foreign, absent or consumed ID and never falls back to a species',async()=>{
  const foreign=await own('u01',1,other),consumed=await own('u01',1,player,{status:'CONSUMED'});
  for(const id of [foreign.id,consumed.id,randomUUID(),'u01'])await expect(service.read(player,id)).rejects.toMatchObject({status:404,code:'UNIT_NOT_FOUND'});
 });

 it('refreshes level and XP from a real battle settlement, then refreshes the same ID after actual rebirth',async()=>{
  const c=structuredClone(catalog);c.characters.find((unit:any)=>unit.id==='u01').attack.power=1_000_000;
  const read=new RosterOwned(db,()=>c),battle=new RosterBattle(db,()=>c,()=>battlePolicy),row=await own();
  const before=await read.read(player,row.id);expect(before.unit.level).toBe(1);
  let view:any=await battle.create(player,{requestId:randomUUID(),ownedId:row.id,encounterId:'tutorial'});
  view=await battle.action(player,{requestId:randomUUID(),battleId:view.battleId,revision:view.revision,action:{kind:'attack'}});
  expect(view.state.outcome).toBe('win');
  const settled:any=await battle.settle(player,{requestId:randomUUID(),battleId:view.battleId,revision:view.revision});
  const grown=(await read.read(player,row.id)).unit;
  expect(grown.growth.totalXp).toBe(settled.settlement.xpAwarded);expect(grown.level).toBeGreaterThan(before.unit.level);
  expect((await read.list(player)).units.find(unit=>unit.ownedId===row.id)?.level).toBe(grown.level);
  const eligible=await own('u45',60),rebirth=new RosterReincarnation(db,()=>catalog);
  const previous=(await service.read(player,eligible.id)).unit;
  const preview:any=(await rebirth.preview(player)).candidates.find(unit=>unit.ownedId===eligible.id);
  expect(preview.eligible).toBe(true);
  await rebirth.confirm(player,{ownedId:eligible.id,requestId:randomUUID(),catalogVersion:catalog.version,previewFingerprint:preview.previewFingerprint});
  const current=(await service.read(player,eligible.id)).unit;
  expect(current).toMatchObject({ownedId:eligible.id,level:1,maxLevel:99,reincarnation:1,growth:{totalXp:0,xp:0}});
  expect(current.stats).toEqual(fighter(catalog,'u45',1,eligible.id,1).stats);
  expect(current.statBreakdown.rebirth.hp).toBeGreaterThan(previous.statBreakdown.rebirth.hp);
 });

 it('reports current/next learned skills and no next threshold at the real growth cap',async()=>{
  const row=await own('u01',1),first=(await service.read(player,row.id)).unit;
  expect(first.skills.nextLearn?.level).toBe(5);
  await db.roster60Owned.update({where:{id:row.id},data:{totalXp:BigInt(definition().growth[4].totalXp)}});
  const next=(await service.read(player,row.id)).unit;
  expect(next.skills.current.length).toBeGreaterThan(first.skills.current.length);expect(next.skills.nextLearn?.level).toBe(10);
  await db.roster60Owned.update({where:{id:row.id},data:{totalXp:BigInt(definition().growth[59].totalXp)}});
  const cap=(await service.read(player,row.id)).unit;
  expect(cap.growth.xpToNext).toBeNull();expect(cap.growth.nextLevelTotalXp).toBeNull();expect(cap.skills.nextLearn).toBeNull();
 });

 it('keeps authentication/profile guards and strict IDs, exposes own resistance only through the owned DTO',async()=>{
  const root=mkdtempSync(join(tmpdir(),'owned-read-routes-')),app=Fastify(),oldProfile=process.env.ROSTER60_PROFILE;
  mkdirSync(join(root,'data'));writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(catalog));
  delete process.env.ROSTER60_PROFILE;
  try{
   registerRoster60(app,db,root,req=>req.headers.authorization===player?player:req.headers.authorization===other?other:null,(req,reply)=>{if(req.headers['x-gmm']!=='1'){reply.code(403).send({error:'forbidden'});return false;}return true;});
   const row=await own(),foreign=await own('u01',1,other),path='/my/api/native/roster60/owned',headers={'x-gmm':'1',authorization:player};
   expect((await app.inject({method:'GET',url:path,headers:{'x-gmm':'1'}})).statusCode).toBe(401);
   expect((await app.inject({method:'GET',url:path,headers:{authorization:player}})).statusCode).toBe(403);
   expect((await app.inject({method:'POST',url:path+'/read',headers,payload:{ownedId:row.id,speciesId:'u01'}})).statusCode).toBe(400);
   expect((await app.inject({method:'POST',url:path+'/read',headers,payload:{ownedId:foreign.id}})).statusCode).toBe(404);
   const response=await app.inject({method:'POST',url:path+'/read',headers,payload:{ownedId:row.id}});
   expect(response.statusCode).toBe(200);expect(response.headers['cache-control']).toBe('no-store');
   expect(response.json().unit.ownResistance).toEqual(definition().defense);
   const switchOwner=await app.inject({method:'GET',url:path,headers:{...headers,authorization:other}});
   expect(switchOwner.json().units.map((unit:any)=>unit.ownedId)).toEqual([foreign.id]);
   const collection=await new RosterLibrary(db,()=>catalog).collection(player);
   expect(collection.characters[0]).not.toHaveProperty('defense');
   expect(publicDefenseSafe({own:{defense:definition().defense},opponent:{defense:definition().defense}})).toEqual({own:{},opponent:{}});
  }finally{await app.close();if(oldProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=oldProfile;rmSync(root,{recursive:true,force:true});}
 });
});
