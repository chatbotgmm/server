import {describe,it,expect,beforeAll,afterAll,beforeEach} from 'vitest';
import {PrismaClient} from '@prisma/client';import {randomUUID,createHmac} from 'node:crypto';import {readFileSync} from 'node:fs';
import {RecruitSessions,type RecruitSessionPolicy} from '../src/roster60-recruit-session.js';import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {buildApp} from '../src/app.js';import {GameService} from '../src/game.js';import {loadContent} from '../src/content.js';import {reviewRouteAllowed} from '../src/review-auth.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('atomic candidate batches',()=>{
 let db:PrismaClient,id:string,svc:RecruitSessions;const ids:string[]=[];
 const c=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 const p:RecruitSessionPolicy={version:'isolated-batch',halfRounding:null,pity:'none',offers:[{kind:'LOW',label:'low',cost:2,weights:{COMMON:100}}]};
 const input=(times=3)=>({requestId:randomUUID(),kind:'LOW',catalogVersion:c.version,times});
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{id=(await db.player.create({data:{identity:randomUUID(),credits:200}})).id;ids.push(id);await new RosterOnboarding(db,()=>c).start(id);svc=new RecruitSessions(db,()=>c,()=>p,()=>0);});
 afterAll(async()=>{await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});await db.dailyProgress.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();});
 const credit=async()=>(await db.player.findUniqueOrThrow({where:{id}})).credits;
 const rows=(s:any)=>s.sessions.map((x:any)=>({sessionId:x.sessionId,revision:x.revision}));
 it('charges quantity once with concurrent same request, restores all candidates, rejects changed payload',async()=>{
  const b=input(),[a,replay]=await Promise.all([svc.start(id,b),svc.start(id,b)]);expect(replay).toEqual(a);expect(a.sessions).toHaveLength(3);expect(await credit()).toBe(194);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(1);
  expect((await new RecruitSessions(db,()=>c,()=>p).info(id)).sessions).toHaveLength(3);
  await expect(svc.start(id,{...b,times:4})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
 });
 it('rerolls only selected candidate once and same grade, claims all exactly once',async()=>{
  const s=await svc.start(id,input());const r=await svc.action(id,{requestId:randomUUID(),sessionId:s.sessions[1].sessionId,revision:0,action:'reroll'});expect(r.candidate.rarity).toBe(s.sessions[1].candidate.rarity);expect(await credit()).toBe(193);expect((await svc.read(id,s.sessionId)).rerolls).toBe(0);
  await expect(svc.action(id,{requestId:randomUUID(),sessionId:r.sessionId,revision:1,action:'reroll'})).rejects.toMatchObject({code:'RECRUIT_REROLL_USED'});
  const b={requestId:randomUUID(),sessions:rows(s).map((x:any)=>({...x,revision:x.sessionId===r.sessionId?1:0}))};const [claim,replay]=await Promise.all([svc.claimAll(id,b),svc.claimAll(id,b)]);expect(replay).toEqual(claim);expect(claim.sessions).toHaveLength(3);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(4);expect((await svc.info(id)).sessions).toHaveLength(0);
  await expect(svc.action(id,{requestId:randomUUID(),sessionId:r.sessionId,revision:2,action:'reroll'})).rejects.toMatchObject({code:'STALE_REVISION'});
 });
 it('max 50, invalid quantity and insufficient funds cannot partially charge or create',async()=>{
  for(const times of [0,-1,51,1.5,NaN,Infinity])await expect(svc.start(id,input(times))).rejects.toMatchObject({code:'INVALID_REQUEST'});
  await db.player.update({where:{id},data:{credits:5}});await expect(svc.start(id,input(3))).rejects.toMatchObject({code:'INSUFFICIENT_CREDITS'});expect(await credit()).toBe(5);expect((await svc.info(id)).sessions).toHaveLength(0);
  await db.player.update({where:{id},data:{credits:100}});expect((await svc.start(id,input(50))).sessions).toHaveLength(50);expect(await credit()).toBe(0);
 });
 it('failure after first generated candidate rolls back debit, candidates and receipt',async()=>{
  let calls=0;const fail=new RecruitSessions(db,()=>c,()=>p,()=>{if(++calls===3)throw Error('injected generation failure');return 0;});const b=input();await expect(fail.start(id,b)).rejects.toThrow('injected');expect(await credit()).toBe(200);expect((await svc.info(id)).sessions).toHaveLength(0);expect(await db.roster60RecruitReceipt.count({where:{playerId:id}})).toBe(0);expect((await svc.start(id,b)).sessions).toHaveLength(3);
 });
 it('bulk claim rollback on late stale row and cross-account access',async()=>{
  const s=await svc.start(id,input());const b={requestId:randomUUID(),sessions:rows(s)};b.sessions[2].revision=9;await expect(svc.claimAll(id,b)).rejects.toMatchObject({code:'STALE_REVISION'});expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(1);expect((await svc.read(id,s.sessionId)).revision).toBe(0);
  await expect(svc.claimAll('other-account',{...b,sessions:rows(s)})).rejects.toMatchObject({code:'RECRUIT_SESSION_NOT_FOUND'});
  await expect(svc.claimAll(id,{...b,sessions:[rows(s)[0],rows(s)[0]]})).rejects.toMatchObject({code:'INVALID_REQUEST'});
 });
 it('claim/reroll race produces one committed transition and no duplicate ownership',async()=>{
  const s=await svc.start(id,input());const results=await Promise.allSettled([svc.claimAll(id,{requestId:randomUUID(),sessions:rows(s)}),svc.action(id,{requestId:randomUUID(),sessionId:s.sessionId,revision:0,action:'reroll'})]);expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);
  const pending=(await svc.info(id)).sessions;if(pending.length)await svc.claimAll(id,{requestId:randomUUID(),sessions:pending.map(x=>({sessionId:x.sessionId,revision:x.revision}))});expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(4);
 });
 it('concurrent different starts create only one batch; individual claim preserves other candidates',async()=>{
  const results=await Promise.allSettled([svc.start(id,input(3)),svc.start(id,input(3))]);expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);expect(await credit()).toBe(194);
  const pending=(await svc.info(id)).sessions;await svc.action(id,{requestId:randomUUID(),sessionId:pending[0].sessionId,revision:0,action:'claim'});expect((await svc.info(id)).sessions).toHaveLength(2);expect(await credit()).toBe(194);
 });
 it('authenticated batch endpoints need no menu unlock and retain quantity validation and bulk receipts',async()=>{
  const previous=process.env.ROSTER60_PROFILE,secret='isolated-batch-api-test-secret-only-not-production';process.env.ROSTER60_PROFILE='five-stat-device-test-v1';
  const app=await buildApp(db,new GameService(db,loadContent()),{menuLocks:true,secret,baseUrl:async()=>'https://example.com'});
  const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`,headers={'x-gmm':'1','x-gmm-s':`${v}.${createHmac('sha256',key).update(v).digest('base64url')}`};
  const call=(path:string,payload:any)=>app.inject({method:'POST',url:'/my/api/native/roster60/recruit/session/'+path,headers,payload});
  try{
   for(const times of [0,51,1.5,'3'])expect((await call('start',{...input(),times})).statusCode).toBe(400);
   const request=input(3),response=await call('start',request);expect(response.statusCode,response.body).toBe(200);const result=response.json();expect(result.sessions).toHaveLength(3);expect(await credit()).toBe(194);expect((await call('start',request)).json()).toEqual(result);
   const claim={requestId:randomUUID(),sessions:rows(result)},done=await call('claim-all',claim);expect(done.statusCode,done.body).toBe(200);expect((await call('claim-all',claim)).json()).toEqual(done.json());expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(4);
   expect(reviewRouteAllowed('POST','native/roster60/recruit/session/claim-all',claim)).toBe(true);
  }finally{await app.close();if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});}
 });

});
