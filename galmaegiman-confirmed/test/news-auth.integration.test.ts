// Real Fastify signed-session + Prisma isolation. Only the guarded disposable test DB is accepted.
import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {buildApp} from '../src/app.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('뉴스 실제 로그인·소유자·멱등성 경계 (격리 SQLite)',()=>{
 let db:PrismaClient,game:GameService,app:Awaited<ReturnType<typeof buildApp>>,owner:string,other:string;
 let headers:Record<string,string>,otherHeaders:Record<string,string>,item:string,foreign:string;
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;
  if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('Only unique /tmp test DB is allowed');
  db=new PrismaClient({datasources:{db:{url}}});game=new GameService(db,loadContent());
  app=await buildApp(db,game,{secret:'isolated-news-integration-secret'.repeat(3),baseUrl:async()=>'https://example.com'});
  const login=async()=>{const identity='news-'+randomUUID();await game.handle(identity,'내정보');const p=await db.player.findUniqueOrThrow({where:{identity}});
   const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0];
   const response=await app.inject({method:'POST',url:'/my/api/login',headers:{'x-gmm':'1','x-gmm-app':'1'},payload:{code}});expect(response.statusCode).toBe(200);
   return {id:p.id,headers:{'x-gmm':'1','x-gmm-s':response.json().token}};};
  const a=await login(),b=await login();owner=a.id;other=b.id;headers=a.headers;otherHeaders=b.headers;
  item=(await db.nativeInboxMessage.create({data:{playerId:owner,title:'내 우편',bodyText:'plain text',publishedAt:new Date(0)}})).id;
  foreign=(await db.nativeInboxMessage.create({data:{playerId:other,title:'다른 우편',bodyText:'private',publishedAt:new Date(0)}})).id;
 });
 afterAll(async()=>{if(app)await app.close();if(db){for(const id of [owner,other])if(id)await game.webDeleteAccount(id);await db.$disconnect();}});
 const read=(id:string,requestId:string,h=headers)=>app.inject({method:'POST',url:'/my/api/native/news/read',headers:h,payload:{kind:'inbox',id,requestId}});
 it('인증·guard 없으면 거부, owner DTO에는 다른 계정 우편 없음',async()=>{
  expect((await app.inject({url:'/my/api/native/news/inbox'})).statusCode).toBe(403);
  expect((await app.inject({url:'/my/api/native/news/inbox',headers:{'x-gmm':'1'}})).statusCode).toBe(401);
  const response=await app.inject({url:'/my/api/native/news/inbox',headers});expect(response.statusCode).toBe(200);expect(response.headers['cache-control']).toBe('no-store');
  expect(response.json().items.map((r:{id:string})=>r.id)).toEqual([item]);expect(response.body).not.toContain(foreign);
 });
 it('현재 전투는 version/epoch 모두 요구하되 capability가 인증을 대체하지 않는다',async()=>{
  const start=(h:Record<string,string>)=>app.inject({method:'POST',url:'/my/api/native/battle/start',headers:h,payload:{mode:'practice'}});
  const noVersion=await start(headers);expect(noVersion.statusCode).toBe(409);expect(noVersion.json().code).toBe('AUGMENT_VERSION_UNSUPPORTED');
  const noEpoch=await start({...headers,'x-gmm-augment-version':'2'});expect(noEpoch.statusCode).toBe(409);expect(noEpoch.json().code).toBe('COMBAT_EPOCH_UNSUPPORTED');
  const capabilities={'x-gmm':'1','x-gmm-augment-version':'2','x-gmm-combat-epoch':'gmm-combat-20261021-v3'};
  expect((await start(capabilities)).statusCode).toBe(401);
  expect((await app.inject({method:'POST',url:'/my/api/native/battle/start',headers:{...headers,...capabilities},payload:{mode:'forged'}})).statusCode).toBe(400);
 });
 it('외부 ID mark·클라이언트 playerId·수령 route는 DB 변경 없이 거부',async()=>{
  expect((await read(foreign,'foreign-request')).statusCode).toBe(404);
  expect((await app.inject({method:'POST',url:'/my/api/native/news/read',headers,payload:{kind:'inbox',id:item,requestId:'injected-owner',playerId:other}})).statusCode).toBe(400);
  expect((await app.inject({method:'POST',url:'/my/api/native/news/claim',headers,payload:{id:item}})).statusCode).toBe(404);
  expect(await db.nativeNewsRead.count({where:{playerId:owner,itemId:foreign}})).toBe(0);
 });
 it('동일 요청 재전송은 readAt 유지, 재사용충돌과 owner 격리는 실제 DB에서 검증',async()=>{
  const first=await read(item,'durable-request'),again=await read(item,'durable-request');expect(first.statusCode).toBe(200);expect(again.json()).toEqual(first.json());
  const second=(await db.nativeInboxMessage.create({data:{playerId:owner,title:'두 번째',bodyText:'text',publishedAt:new Date(0)}})).id;
  expect((await read(second,'durable-request')).statusCode).toBe(409);
  expect((await read(foreign,'durable-request',otherHeaders)).statusCode).toBe(200);
  expect(await db.nativeNewsRequest.count({where:{playerId:owner,requestId:'durable-request'}})).toBe(1);
  expect(await db.nativeNewsRead.count({where:{playerId:owner,itemId:second}})).toBe(0);
 });
});
