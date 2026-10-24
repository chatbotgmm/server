import {beforeAll,afterAll,it,describe,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {requiredReincarnationLevel} from '../src/battle/roster60-reincarnation.js';
import {instanceGrowthCurve,skillElements} from '../src/battle/roster60-rules.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('rebirth account cost transaction (synthetic eligibility only)',()=>{
 let db:PrismaClient,service:RosterReincarnation;const owners:string[]=[];
 const unit={id:'u45',name:'타이탄',rarity:'LIMITED',stats:{hp:100},levelOneStats:{hp:100,mp:100,patk:100,matk:100,pdef:100,mdef:100},attack:{id:'a',name:'a',element:'NORMAL' as const,mpCost:1},skills:[],suitability:Object.fromEntries(skillElements.map(e=>[e,0])) as any,defense:Object.fromEntries(skillElements.map(e=>[e,'normal'])) as any,growth:Array.from({length:99},(_,i)=>({totalXp:i*10,maxMp:100+i}))};
 const catalog={approved:true,version:'synthetic',characters:[unit]};
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');db=new PrismaClient({datasources:{db:{url}}});service=new RosterReincarnation(db,()=>catalog);});
 afterAll(async()=>{await db.player.deleteMany({where:{id:{in:owners}}});await db.$disconnect();});
 async function account(peanut=5){const p=await db.player.create({data:{identity:randomUUID(),peanut}});owners.push(p.id);return p.id;}
 async function owned(playerId:string){return db.roster60Owned.create({data:{playerId,speciesId:'u45',source:randomUUID(),totalXp:590n}});}
 async function reach(id:string,count:number,level=requiredReincarnationLevel(count)){const curve=instanceGrowthCurve(unit.growth,count);await db.roster60Owned.update({where:{id},data:{totalXp:BigInt(curve[level-1].totalXp)}});}
 async function request(p:string,id:string){const x=(await service.preview(p)).candidates.find(x=>x.ownedId===id) as any;return {ownedId:id,requestId:randomUUID(),catalogVersion:catalog.version,previewFingerprint:x.previewFingerprint};}
 it('first free, retries identical, then one peanut; cap5 and no compound',async()=>{
  const p=await account(),u=await owned(p),r=await request(p,u.id),first=await service.confirm(p,r);
  expect(first.paidPeanut).toBe(0);expect(await service.confirm(p,r)).toEqual(first);
  for(let n=2;n<=5;n++){await reach(u.id,n-1);const result=await service.confirm(p,await request(p,u.id));expect(result.paidPeanut).toBe(1);expect(result.preview.afterLevelOneStats.hp).toBe(100+n*10);}
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
  await service.confirm(p,r);await reach(a.id,1);await expect(service.confirm(p,await request(p,a.id))).rejects.toThrow('INSUFFICIENT_PEANUT');
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
 it('all five exact level boundaries share preview and execution policy',async()=>{
  const p=await account(),u=await owned(p);
  for(let count=0;count<5;count++){
   const required=requiredReincarnationLevel(count);
   await reach(u.id,count,required-1);
   const low=(await service.preview(p)).candidates.find(x=>x.ownedId===u.id) as any;
   expect(low.requiredLevel).toBe(required);expect(low.eligible).toBe(false);
   await expect(service.confirm(p,await request(p,u.id))).rejects.toThrow('REINCARNATION_REQUIREMENTS_NOT_MET');
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).reincarnation).toBe(count);
   await reach(u.id,count,required);
   const ready=(await service.preview(p)).candidates.find(x=>x.ownedId===u.id) as any;expect(ready.eligible).toBe(true);
   const r=await request(p,u.id),result=await service.confirm(p,r);
   expect(result.preview.next).toMatchObject({level:1,totalXp:0,maxLevel:99,reincarnation:count+1});
   expect(await service.confirm(p,r)).toEqual(result);
  }
  expect((await service.preview(p)).candidates.find(x=>x.ownedId===u.id)?.reason).toBe('REINCARNATION_LIMIT');
 });
 it('server allowlist covers all ten grades; no client grade input is used',async()=>{
  const p=await account(20);const original=unit.rarity;
  try{for(const grade of ['COMMON','UNCOMMON','RARE','EPIC','LEGEND','HIDDEN','LIMITED','TRANSCEND','IMMORTAL','ETERNAL']){
   unit.rarity=grade;const u=await owned(p),candidate=(await service.preview(p)).candidates.find(x=>x.ownedId===u.id) as any;
   const allowed=['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'].includes(grade);expect(candidate.eligible).toBe(allowed);
   if(allowed)expect((await service.confirm(p,await request(p,u.id))).completed).toBe(true);
   else await expect(service.confirm(p,await request(p,u.id))).rejects.toThrow('REINCARNATION_GRADE_NOT_ALLOWED');
  }}finally{unit.rarity=original;}
 });
});
