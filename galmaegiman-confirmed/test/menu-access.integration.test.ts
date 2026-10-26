import {beforeAll,afterAll,it,expect,describe} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID,createHmac} from 'node:crypto';import {readFileSync} from 'node:fs';
import {buildApp} from '../src/app.js';import {GameService} from '../src/game.js';import {loadContent} from '../src/content.js';
import {MenuAccess,menuFeatures,menuFeatureFor} from '../src/menu-access.js';
import {reviewRouteAllowed} from '../src/review-auth.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('server authoritative account menu locks',()=>{
 let db:PrismaClient,app:Awaited<ReturnType<typeof buildApp>>,user:string,admin:string,other:string;const ids:string[]=[],old=process.env.ROSTER60_PROFILE,oldDefault=process.env.GMM_TEST_MENU_DEFAULT;
 const secret='menu-access-isolated-test-secret-only',catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 function headers(id:string){const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`;return {'x-gmm':'1','x-gmm-s':`${v}.${createHmac('sha256',key).update(v).digest('base64url')}`};}
 const request=(id:string,path:string,payload?:any)=>app.inject({method:payload===undefined?'GET':'POST',url:'/my/api/'+path,headers:headers(id),...(payload===undefined?{}:{payload})});
 const change=(targetPlayerId:string,featureId:string,mode:string,expectedRevision:number)=>({requestId:randomUUID(),targetPlayerId,featureId,mode,expectedRevision});
 beforeAll(async()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');db=new PrismaClient({datasources:{db:{url}}});
 for(let i=0;i<3;i++)ids.push((await db.player.create({data:{identity:randomUUID(),isAdmin:i===1,credits:215}})).id);
 [user,admin,other]=ids;process.env.GMM_TEST_MENU_DEFAULT='locked';process.env.ROSTER60_PROFILE='five-stat-device-test-v1';app=await buildApp(db,new GameService(db,loadContent()),{menuLocks:true,secret,baseUrl:async()=>'https://example.com'});
 });
 afterAll(async()=>{await app.close();if(oldDefault===undefined)delete process.env.GMM_TEST_MENU_DEFAULT;else process.env.GMM_TEST_MENU_DEFAULT=oldDefault;if(old===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=old;await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();});
 it('eight defaults fail closed, unrelated home/mail/play remain outside the map',async()=>{
 const r=await request(user,'native/menu-access');expect(r.statusCode,r.body).toBe(200);expect(r.json().representativeLevel).toBe(0);expect(r.json().enforcementEnabled).toBe(true);
 expect(Object.keys(r.json().features)).toEqual(menuFeatures);expect(Object.values(r.json().features).every((v:any)=>!v.enabled&&v.requiredLevel===null)).toBe(true);
 for(const path of ['native/roster60/home','native/roster60/state','native/roster60/battle/options','native/news/inbox','native/state','settings','profile'])expect(menuFeatureFor('/my/api/'+path)).toBeNull();
 expect((await request(user,'native/roster60/home')).statusCode).toBe(200);
 expect(reviewRouteAllowed('GET','native/menu-access',undefined)).toBe(true);expect(reviewRouteAllowed('POST','admin/menu-access',{})).toBe(false);
 });
 it('direct new and legacy APIs cannot bypass eight locks or spend inventory',async()=>{
 for(const [path,payload] of [['native/roster60/recruit/session',undefined],['native/roster60/recruit/session/start',{times:3}],['native/roster60/recruit/session/claim-all',{sessions:[]}],['draw',undefined],['claim',{}],['native/roster60/collection',undefined],['native/roster60/craft/options',undefined],['craft',{}],['native/roster60/adventure/start',{}],['shop',{item:'tickets',count:1}],['items/buy',{}],['native/roster60/pvp/party',{}],['battle-team',{}],['story',{data:{team:['o1']}}],['native/progression/reassess',{}],['daily',undefined],['attend',{}],['native/roster60/reincarnation/confirm',{}],['native/roster60/battle/create',{adventureId:randomUUID()}]] as const){const r=await request(user,path,payload);expect(r.statusCode,path+':'+r.body).toBe(403);expect(r.json().code).toBe('MENU_LOCKED');}
 expect((await db.player.findUniqueOrThrow({where:{id:user}})).credits).toBe(215);
 });
 it('authentication, admin authority and strict inputs enforced',async()=>{
 expect((await app.inject({method:'GET',url:'/my/api/native/menu-access',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
 expect((await request(user,'admin/menu-access?playerId='+other)).statusCode).toBe(403);
 expect((await request(admin,'admin/menu-access?playerId='+other)).statusCode).toBe(200);
 const input=change(user,'recruit','force_unlock',0);expect((await request(user,'admin/menu-access',input)).statusCode).toBe(403);
 expect((await request(admin,'admin/menu-access',{...input,isAdmin:true})).statusCode).toBe(400);
 expect((await request(admin,'admin/menu-access',{...input,featureId:'play'})).statusCode).toBe(400);
 expect((await request(admin,'admin/menu-access',{...input,targetPlayerId:'missing'})).statusCode).toBe(404);
 });
 it('single feature/account unlock; idempotent receipt; changed payload and stale revision rejected',async()=>{
 const input=change(user,'recruit','force_unlock',0),r=await request(admin,'admin/menu-access',input);expect(r.statusCode,r.body).toBe(200);
 expect((await request(admin,'admin/menu-access',input)).json()).toEqual(r.json());
 expect((await request(admin,'admin/menu-access',{...input,mode:'force_lock'})).statusCode).toBe(409);
 expect((await request(admin,'admin/menu-access',change(user,'shop','force_unlock',0))).statusCode).toBe(409);
 expect((await request(user,'native/roster60/recruit/session')).statusCode).toBe(200);
 expect((await request(other,'native/roster60/recruit/session')).statusCode).toBe(403);
 expect((await request(user,'native/roster60/collection')).statusCode).toBe(403);
 expect(await db.auditEvent.count({where:{playerId:admin,kind:'MENU_ACCESS_CHANGED'}})).toBe(1);
 });
 it('force lock and auto override survive new service instances and reopen without balances changing',async()=>{
 expect((await request(admin,'admin/menu-access',change(user,'recruit','force_lock',1))).statusCode).toBe(200);
 const service=new MenuAccess(db,()=>catalog);expect((await service.read(user)).features.recruit.reason).toBe('ACCOUNT_LOCKED');
 expect((await request(admin,'admin/menu-access',change(user,'recruit','auto',2))).statusCode).toBe(200);
 expect((await service.read(user)).features.recruit.reason).toBe('TEST_DEFAULT_LOCK');expect((await db.player.findUniqueOrThrow({where:{id:user}})).credits).toBe(215);
 });
 it('representative level uses owned XP, ignores consumed, and drops with actual ownership without inventing unlock levels',async()=>{
 const definition=catalog.characters.find((d:any)=>d.id==='u01');
 const low=await db.roster60Owned.create({data:{playerId:other,speciesId:'u01',totalXp:definition.growth[9].totalXp,source:'test'}});
 const high=await db.roster60Owned.create({data:{playerId:other,speciesId:'u01',totalXp:definition.growth[29].totalXp,source:'test-high'}});
 const service=new MenuAccess(db,()=>catalog);expect((await service.read(other)).representativeLevel).toBe(30);
 await db.roster60Owned.update({where:{id:high.id},data:{status:'CONSUMED'}});expect((await service.read(other)).representativeLevel).toBe(10);
 expect((await service.read(other)).levelDecreasePolicy).toBe('PENDING');expect((await service.read(other)).features.recruit.enabled).toBe(false);
 expect((await new MenuAccess(db,()=>null).read(other)).representativeLevel).toBeNull();
 await db.roster60Owned.update({where:{id:low.id},data:{status:'CONSUMED'}});expect((await service.read(other)).representativeLevel).toBe(0);
 });
 it('parallel same request changes once; competing revisions cannot silently overwrite',async()=>{
 const service=new MenuAccess(db,()=>catalog),input=change(other,'shop','force_unlock',0) as any;
 const out=await Promise.allSettled([service.change(admin,input),service.change(admin,input)]);expect(out.filter(r=>r.status==='fulfilled').length).toBeGreaterThan(0);
 expect((await service.read(other)).revision).toBe(1);expect(await service.change(admin,input)).toMatchObject({revision:1});
 const concurrent=await Promise.allSettled([service.change(admin,change(other,'shop','force_lock',1) as any),service.change(admin,change(other,'roster','force_unlock',1) as any)]);
 expect(concurrent.filter(r=>r.status==='fulfilled')).toHaveLength(1);expect((await service.read(other)).revision).toBe(2);
 });
});


suite('explicit test-profile open default preserves administrator overrides',()=>{
 it('new accounts auto-open; later force_lock wins; auto reopens; default rollback closes auto',async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable only');
  const db=new PrismaClient({datasources:{db:{url}}});const ids:string[]=[];
  try{
   const admin=await db.player.create({data:{identity:randomUUID(),isAdmin:true}});ids.push(admin.id);
   const player=await db.player.create({data:{identity:randomUUID(),credits:215}});ids.push(player.id);
   const open=new MenuAccess(db,()=>null,false);
   expect(Object.values((await open.read(player.id)).features).every(f=>f.enabled&&f.mode==='auto')).toBe(true);
   await expect(open.require(player.id,'rebirth')).resolves.toBeUndefined();
   await open.change(admin.id,{requestId:randomUUID(),targetPlayerId:player.id,featureId:'rebirth',mode:'force_lock',expectedRevision:0});
   expect((await open.read(player.id)).features.rebirth).toMatchObject({enabled:false,reason:'ACCOUNT_LOCKED'});
   await expect(open.require(player.id,'rebirth')).rejects.toMatchObject({status:403});
   await open.change(admin.id,{requestId:randomUUID(),targetPlayerId:player.id,featureId:'rebirth',mode:'auto',expectedRevision:1});
   expect((await open.read(player.id)).features.rebirth.enabled).toBe(true);
   expect((await new MenuAccess(db,()=>null,true).read(player.id)).features.rebirth.enabled).toBe(false);
   expect((await db.player.findUniqueOrThrow({where:{id:player.id}})).credits).toBe(215);
  }finally{await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();}
 });
});
