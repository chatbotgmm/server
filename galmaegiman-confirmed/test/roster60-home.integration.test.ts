import {beforeAll,afterAll,it,expect,describe} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID} from 'node:crypto';
import {RosterHome,recordRebirthUnlock} from '../src/roster60-home.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('account-persistent rebirth home state (isolated DB)',()=>{
 let db:PrismaClient;const ids:string[]=[];
 const catalog=(rarity:string)=>({approved:true,version:'identity-test-only',characters:[{id:'u45',rarity}]}) as any;
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();});
 async function account(){const p=await db.player.create({data:{identity:randomUUID()}});ids.push(p.id);return p.id;}
 it('all ten grade boundaries; low-level upper grade unlock is distinct from rebirth execution',async()=>{
  for(const grade of ['COMMON','UNCOMMON','RARE','EPIC','LEGEND','HIDDEN','LIMITED','TRANSCEND','IMMORTAL','ETERNAL']){
   const id=await account();await db.roster60Owned.create({data:{playerId:id,speciesId:'u45',totalXp:0,source:'test'}});
   const home=new RosterHome(db,()=>catalog(grade)),v=await home.read(id),allowed=['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'].includes(grade);
   expect(v.rebirthUnlocked).toBe(allowed);expect(v.rebirthIntroPending).toBe(allowed);expect(v.rebirthBonusPending).toBe(true);
   if(!allowed)await expect(home.completeIntro(id)).rejects.toThrow('REBIRTH_LOCKED');
  }
 });
 it('cancel/restart remains pending; completion idempotent and retained after unit is consumed',async()=>{
  const id=await account();await db.$transaction(tx=>recordRebirthUnlock(tx,id,'LIMITED'));
  let home=new RosterHome(db,()=>null);expect((await home.read(id)).rebirthIntroPending).toBe(true);
  home=new RosterHome(db,()=>null);expect((await home.read(id)).rebirthIntroPending).toBe(true);
  expect(await home.completeIntro(id)).toEqual({complete:true});expect(await home.completeIntro(id)).toEqual({complete:true});
  const v=await new RosterHome(db,()=>null).read(id);expect(v.rebirthUnlocked).toBe(true);expect(v.rebirthIntroPending).toBe(false);
 });
 it('mail badge uses account unread rows and unused roster ticket independently',async()=>{
  const id=await account(),other=await account();const mail=await db.nativeInboxMessage.create({data:{playerId:id,title:'test',bodyText:'test'}});
  await db.nativeInboxMessage.create({data:{playerId:other,title:'other',bodyText:'test'}});
  const home=new RosterHome(db,()=>null);expect((await home.read(id)).mailUnread).toBe(1);
  await db.nativeNewsRead.create({data:{playerId:id,kind:'inbox',itemId:mail.id}});expect((await home.read(id)).mailUnread).toBe(0);
  await db.roster60State.create({data:{playerId:id,starterId:randomUUID(),mailId:'roster60-'+randomUUID()}});expect((await home.read(id)).mailRewardPending).toBe(true);
 });
});
