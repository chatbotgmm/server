import {describe,it,expect,beforeAll,afterAll,vi} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID} from 'node:crypto';import {readFileSync} from 'node:fs';
import {GameService} from '../src/game.js';import {loadContent} from '../src/content.js';
import {RosterLibrary} from '../src/roster60-library.js';import {RosterOnboarding} from '../src/roster60-onboarding.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('product 60 roster recruitment transactions',()=>{
 let db:PrismaClient,id:string;const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 let service:RosterLibrary;
 beforeAll(async()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});id=(await db.player.create({data:{identity:randomUUID(),credits:8}})).id;await new RosterOnboarding(db,()=>c).start(id);service=new RosterLibrary(db,()=>c,()=>0);});
 afterAll(async()=>{await db.player.delete({where:{id}});await db.$disconnect();});
 it('serves exactly 60, preserves LOW economy, blocks undecided grades',async()=>{const a=await service.collection(id);expect(a.characters).toHaveLength(60);expect(a.characters[0].ownedCount).toBe(1);const i=await service.info(id);expect(i.offers.find(o=>o.kind==='LOW')).toMatchObject({cost:2,weights:{COMMON:70,UNCOMMON:30},enabled:true});expect(i.offers.find(o=>o.kind==='MID')?.enabled).toBe(false);await expect(service.recruit(id,{requestId:randomUUID(),kind:'MID',times:1,catalogVersion:c.version})).rejects.toMatchObject({code:'RECRUIT_GRADE_POLICY_PENDING'});});
 it('atomic charge, permanent grant, duplicate fixed and payload conflict',async()=>{const b={requestId:randomUUID(),kind:'LOW',times:2,catalogVersion:c.version};const r=await service.recruit(id,b);expect(await service.recruit(id,b)).toEqual(r);expect(r.charged).toBe(4);expect(r.items.every((u:any)=>u.speciesId==='u01')).toBe(true);expect(await db.ownedCharacter.count({where:{playerId:id}})).toBe(0);expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(4);await expect(service.recruit(id,{...b,times:1})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});});
 it('insufficient funds and changed catalog never debit or grant',async()=>{const before=await db.roster60Owned.count({where:{playerId:id}});await expect(service.recruit(id,{requestId:randomUUID(),kind:'LOW',times:3,catalogVersion:c.version})).rejects.toMatchObject({code:'INSUFFICIENT_CREDITS'});await expect(service.recruit(id,{requestId:randomUUID(),kind:'LOW',times:1,catalogVersion:'old'})).rejects.toMatchObject({code:'CATALOG_CHANGED'});expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(before);expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(4);});
 it('legacy recruitment is rejected for the new account',async()=>{await expect(new GameService(db,loadContent()).webDraw(id,'LOW',1)).rejects.toThrow('새 60종');});
 it('failed grant rolls back charge, concurrent replay grants once',async()=>{
  const body={requestId:randomUUID(),kind:'LOW',times:1,catalogVersion:c.version};
  await expect(new RosterLibrary(db,()=>c,()=>{throw Error('injected grant failure');}).recruit(id,body)).rejects.toThrow('injected');
  expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(4);
  const [a,b]=await Promise.all([service.recruit(id,body),service.recruit(id,body)]);expect(a).toEqual(b);expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(2);
 });

 it('epic grant persists Lv15 XP atomically with replay; real MID/HIGH remain disabled',async()=>{
  await db.player.update({where:{id},data:{credits:10}});
  const actual=new RosterLibrary(db,()=>c,()=>0);
  for(const kind of ['MID','HIGH'])await expect(actual.recruit(id,{requestId:randomUUID(),kind,times:1,catalogVersion:c.version})).rejects.toMatchObject({code:'RECRUIT_GRADE_POLICY_PENDING'});
  // Test-only offer injection exercises the actual transaction; no product offer is enabled.
  vi.spyOn(actual as any,'offers').mockReturnValue([{kind:'TEST_EPIC',cost:2,weights:{EPIC:100},enabled:true}]);
  const body={requestId:randomUUID(),kind:'TEST_EPIC',times:1,catalogVersion:c.version};
  const [a,b]=await Promise.all([actual.recruit(id,body),actual.recruit(id,body)]);expect(a).toEqual(b);
  const u=c.characters.find((u:any)=>u.id===a.items[0].speciesId),o=await db.roster60Owned.findUniqueOrThrow({where:{id:a.items[0].ownedId}});
  expect(a.items[0]).toMatchObject({level:15,totalXp:u.growth[14].totalXp});expect(o.totalXp).toBe(BigInt(u.growth[14].totalXp));expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(8);
  const bad=structuredClone(c);bad.characters.filter((u:any)=>u.rarity==='EPIC').forEach((u:any)=>u.growth=u.growth.slice(0,14));
  const broken=new RosterLibrary(db,()=>bad,()=>0);vi.spyOn(broken as any,'offers').mockReturnValue([{kind:'TEST_EPIC',cost:2,weights:{EPIC:100},enabled:true}]);
  const count=await db.roster60Owned.count({where:{playerId:id}});
  await expect(broken.recruit(id,{...body,requestId:randomUUID()})).rejects.toMatchObject({code:'RECRUIT_GROWTH_INVALID'});
  expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(count);expect((await db.player.findUniqueOrThrow({where:{id}})).credits).toBe(8);
 });

});
