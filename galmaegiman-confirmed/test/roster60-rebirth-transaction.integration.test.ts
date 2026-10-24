import {beforeAll,afterAll,it,describe,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('rebirth account cost transaction (synthetic eligibility only)',()=>{
 let db:PrismaClient,service:RosterReincarnation;const owners:string[]=[];
 const unit={id:'u01',name:'갈매미맨',rarity:'COMMON',stats:{hp:100},levelOneStats:{hp:100,mp:100,patk:100,matk:100,pdef:100,mdef:100},attack:{id:'a',name:'a',element:'NORMAL' as const,mpCost:1},skills:[],suitability:Object.fromEntries(skillElements.map(e=>[e,0])) as any,defense:Object.fromEntries(skillElements.map(e=>[e,'normal'])) as any,growth:Array.from({length:99},(_,i)=>({totalXp:i*10,maxMp:100+i}))};
 const catalog={approved:true,version:'synthetic',characters:[unit]};
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');db=new PrismaClient({datasources:{db:{url}}});service=new RosterReincarnation(db,()=>catalog,()=>({version:'TEST_ONLY',grades:['COMMON'],firstMinLevel:1,repeatMinLevel:1}));});
 afterAll(async()=>{await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();});
 async function account(peanut=5){const p=await db.player.create({data:{identity:randomUUID(),peanut}});owners.push(p.id);return p.id;}
 async function owned(playerId:string){return db.roster60Owned.create({data:{playerId,speciesId:'u01',source:randomUUID()}});}
 async function request(p:string,id:string){const x=(await service.preview(p)).candidates.find(x=>x.ownedId===id) as any;return {ownedId:id,requestId:randomUUID(),catalogVersion:catalog.version,previewFingerprint:x.previewFingerprint};}
 it('first free, retries identical, then one peanut; cap5 and no compound',async()=>{
  const p=await account(),u=await owned(p),r=await request(p,u.id),first=await service.confirm(p,r);
  expect(first.paidPeanut).toBe(0);expect(await service.confirm(p,r)).toEqual(first);
  for(let n=2;n<=5;n++){const result=await service.confirm(p,await request(p,u.id));expect(result.paidPeanut).toBe(1);expect(result.preview.afterLevelOneStats.hp).toBe(100+n*10);}
  expect((await db.player.findUniqueOrThrow({where:{id:p}})).peanut).toBe(1);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(0n);
  await expect(service.confirm(p,{...r,requestId:randomUUID()})).rejects.toThrow('REINCARNATION_LIMIT');
 });
 it('two concurrent units cannot both consume account free allowance',async()=>{
  const p=await account(),a=await owned(p),b=await owned(p),ra=await request(p,a.id),rb=await request(p,b.id);
  const results=await Promise.allSettled([service.confirm(p,ra),service.confirm(p,rb)]);
  expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);
  expect((await db.roster60RebirthAccount.findUniqueOrThrow({where:{playerId:p}})).successes).toBe(1);
  const remaining=await db.roster60Owned.findFirstOrThrow({where:{playerId:p,reincarnation:0}});
  expect((await service.confirm(p,await request(p,remaining.id))).paidPeanut).toBe(1);
 });
 it('tampered preview, wrong owner, missing funds and receipt failure never consume/reset',async()=>{
  const p=await account(0),other=await account(),a=await owned(p),r=await request(p,a.id);
  await expect(service.confirm(p,{...r,previewFingerprint:'0'.repeat(64)})).rejects.toThrow('PREVIEW_CHANGED');
  await expect(service.confirm(other,r)).rejects.toThrow('UNIT_NOT_FOUND');
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:p}})).toBeNull();
  await db.$executeRawUnsafe("CREATE TRIGGER fail_rebirth_receipt BEFORE INSERT ON Roster60RebirthReceipt BEGIN SELECT RAISE(ABORT,'test rollback'); END");
  try{await expect(service.confirm(p,r)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER fail_rebirth_receipt');}
  expect(await db.roster60RebirthAccount.findUnique({where:{playerId:p}})).toBeNull();
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.id}})).reincarnation).toBe(0);
  await service.confirm(p,r);await expect(service.confirm(p,await request(p,a.id))).rejects.toThrow('INSUFFICIENT_PEANUT');
  expect((await db.roster60RebirthAccount.findUniqueOrThrow({where:{playerId:p}})).successes).toBe(1);
  await expect(service.confirm(p,{...r,ownedId:'changed'})).rejects.toThrow('REQUEST_ID_REUSED');
 });
 it('concurrent identical request converges on one receipt without second charge',async()=>{
  const p=await account(),u=await owned(p),r=await request(p,u.id);
  await Promise.allSettled([service.confirm(p,r),service.confirm(p,r)]);
  const result=await service.confirm(p,r);expect(result.paidPeanut).toBe(0);
  expect(await db.roster60RebirthReceipt.count({where:{playerId:p}})).toBe(1);
  expect((await db.roster60RebirthAccount.findUniqueOrThrow({where:{playerId:p}})).successes).toBe(1);
  expect((await db.player.findUniqueOrThrow({where:{id:p}})).peanut).toBe(5);
 });
 it('production default keeps unknown eligibility closed',async()=>{
  const p=await account(),u=await owned(p),r=await request(p,u.id);
  await expect(new RosterReincarnation(db,()=>catalog).confirm(p,r)).rejects.toThrow('REINCARNATION_ELIGIBILITY_POLICY_REQUIRED');
 });
});
