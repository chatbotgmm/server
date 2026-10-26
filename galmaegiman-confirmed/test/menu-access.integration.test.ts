import {beforeAll,afterAll,it,expect,describe} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID,createHash,createHmac} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {menuFeatures} from '../src/menu-access.js';
import {reviewRouteAllowed} from '../src/review-auth.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('authenticated menus without business locks (isolated DB)',()=>{
 let db:PrismaClient,app:Awaited<ReturnType<typeof buildApp>>,user:string,admin:string;
 const ids:string[]=[],old=process.env.ROSTER60_PROFILE,oldDefault=process.env.GMM_TEST_MENU_DEFAULT;
 const secret='menu-access-isolated-test-secret-only';
 function headers(id:string){const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`;return {'x-gmm':'1','x-gmm-s':`${v}.${createHmac('sha256',key).update(v).digest('base64url')}`};}
 const request=(id:string,path:string,payload?:any)=>app.inject({method:payload===undefined?'GET':'POST',url:'/my/api/'+path,headers:headers(id),...(payload===undefined?{}:{payload})});
 const key=()=> 'menu-access:v1:account:'+createHash('sha256').update(user).digest('hex');
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');
  db=new PrismaClient({datasources:{db:{url}}});
  for(let i=0;i<2;i++)ids.push((await db.player.create({data:{identity:randomUUID(),isAdmin:i===1,credits:215}})).id);
  [user,admin]=ids;process.env.GMM_TEST_MENU_DEFAULT='invalid-obsolete-setting';process.env.ROSTER60_PROFILE='five-stat-device-test-v1';
  app=await buildApp(db,new GameService(db,loadContent()),{menuLocks:true,secret,baseUrl:async()=>'https://example.com'});
 });
 afterAll(async()=>{
  if(app)await app.close();if(oldDefault===undefined)delete process.env.GMM_TEST_MENU_DEFAULT;else process.env.GMM_TEST_MENU_DEFAULT=oldDefault;
  if(old===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=old;
  if(db){await db.appMeta.deleteMany({where:{key:key()}});await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();}
 });
 it('opens eight entries for new accounts and ignores saved force_lock and malformed old configuration',async()=>{
  for(const value of [JSON.stringify({revision:12,overrides:Object.fromEntries(menuFeatures.map(f=>[f,'force_lock']))}),'invalid-json']){
   await db.appMeta.upsert({where:{key:key()},create:{key:key(),value},update:{value}});
   const r=await request(user,'native/menu-access');expect(r.statusCode,r.body).toBe(200);expect(r.json().enforcementEnabled).toBe(false);
   expect(Object.keys(r.json().features)).toEqual(menuFeatures);expect(Object.values(r.json().features).every((f:any)=>f.enabled&&f.requiredLevel===null)).toBe(true);
   expect((await db.appMeta.findUniqueOrThrow({where:{key:key()}})).value).toBe(value);
  }
  expect((await request(user,'native/roster60/recruit/session')).statusCode).toBe(200);
  expect((await request(user,'native/roster60/collection')).statusCode).toBe(200);
  expect((await request(user,'native/roster60/home')).json().rebirthUnlocked).toBe(true);
 });
 it('keeps real signed-session authentication and administrator authorization',async()=>{
  expect((await app.inject({url:'/my/api/native/menu-access'})).statusCode).toBe(403);
  expect((await app.inject({url:'/my/api/native/menu-access',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
  expect((await request('missing-player','native/menu-access')).statusCode).toBe(401);
  expect((await request(user,'admin/menu-access?playerId='+user)).statusCode).toBe(403);
  expect((await request(admin,'admin/menu-access?playerId='+user)).statusCode).toBe(200);
  expect(reviewRouteAllowed('GET','native/menu-access',undefined)).toBe(true);expect(reviewRouteAllowed('POST','admin/menu-access',{})).toBe(false);
 });
 it('rejects retired lock writes without changing stored settings, audit rows or balances',async()=>{
  const value=(await db.appMeta.findUniqueOrThrow({where:{key:key()}})).value;
  const audit=await db.auditEvent.count({where:{playerId:admin}}),input={requestId:randomUUID(),targetPlayerId:user,featureId:'recruit',mode:'force_lock',expectedRevision:0};
  expect((await request(user,'admin/menu-access',input)).statusCode).toBe(403);
  expect((await request(admin,'admin/menu-access',{...input,isAdmin:true})).statusCode).toBe(400);
  const r=await request(admin,'admin/menu-access',input);expect(r.statusCode,r.body).toBe(410);expect(r.json().error).toBe('MENU_ACCESS_RETIRED');
  expect((await db.appMeta.findUniqueOrThrow({where:{key:key()}})).value).toBe(value);
  expect(await db.auditEvent.count({where:{playerId:admin}})).toBe(audit);expect((await db.player.findUniqueOrThrow({where:{id:user}})).credits).toBe(215);
 });
 it('allows action handlers to enforce strict payloads and owner-scoped battle access',async()=>{
  for(const path of ['native/roster60/recruit/session/start','native/roster60/craft/confirm','native/roster60/adventure/start','native/roster60/pvp/party','native/roster60/reincarnation/confirm','native/roster60/battle/create']){
   const r=await request(user,path,{});expect(r.statusCode,path+':'+r.body).toBe(400);expect(r.json().error).toBe('INVALID_REQUEST');
  }
  const battle=await request(user,'native/roster60/battle/read',{battleId:randomUUID()});expect(battle.statusCode).toBe(404);expect(battle.json().error).toBe('BATTLE_NOT_FOUND');
  expect((await db.player.findUniqueOrThrow({where:{id:user}})).credits).toBe(215);
 });
});
