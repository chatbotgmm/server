import {describe,it,expect,beforeAll,beforeEach,afterAll} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID,createHmac} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {RecruitSessions,type RecruitSessionPolicy} from '../src/roster60-recruit-session.js';
import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('paid duplicate candidate rejection is atomic and account scoped',()=>{
 const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
 const policy:RecruitSessionPolicy={version:'reject-fixture-only',halfRounding:null,pity:'none',offers:[{kind:'LOW',label:'low',cost:2,weights:{COMMON:100}}]};
 let db:PrismaClient,id:string,other:string,service:RecruitSessions;const players:string[]=[];
 const input=(times=1)=>({requestId:randomUUID(),kind:'LOW',catalogVersion:catalog.version,times});
 const action=(s:any)=>({requestId:randomUUID(),sessionId:s.sessionId,revision:s.revision,action:'reject' as const});
 const rows=(s:any)=>s.pendingSessions.map((r:any)=>({sessionId:r.sessionId,revision:r.revision}));
 const balance=async()=>(await db.player.findUniqueOrThrow({where:{id}})).credits;
 const count=()=>db.roster60Owned.count({where:{playerId:id,status:{not:'CONSUMED'}}});
 const duplicate=async(s:any)=>{if(!await db.roster60Owned.findFirst({where:{playerId:id,speciesId:s.candidate.speciesId,status:{not:'CONSUMED'}}}))await db.roster60Owned.create({data:{playerId:id,speciesId:s.candidate.speciesId,source:'isolated-rejection-test:'+randomUUID()}});};
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{id=(await db.player.create({data:{identity:randomUUID(),credits:1000}})).id;other=(await db.player.create({data:{identity:randomUUID(),credits:1000}})).id;players.push(id,other);await new RosterOnboarding(db,()=>catalog).start(id);await new RosterOnboarding(db,()=>catalog).start(other);service=new RecruitSessions(db,()=>catalog,()=>policy,()=>0);});
 afterAll(async()=>{await db.auditEvent.deleteMany({where:{playerId:{in:players}}});await db.dailyProgress.deleteMany({where:{playerId:{in:players}}});await db.player.deleteMany({where:{id:{in:players}}});await db.$disconnect();});

 it('rejects one paid duplicate, refunds exactly one, preserves owned copies, and replays without a second reward',async()=>{
  const s=await service.start(id,input());await duplicate(s);const before=await count();
  expect((await service.info(id)).pendingSessions[0]).toMatchObject({ownedSpecies:true,canReject:true,rejectRefund:1,rejectReason:''});
  const request=action(s),done=await service.action(id,request);
  expect(done).toMatchObject({sessionId:s.sessionId,status:'REJECTED',revision:1,credits:999,balances:{applicationTickets:999},refundedTickets:1,canReject:false,rejectReason:'NOT_PENDING',pendingSessions:[],receipt:{requestId:request.requestId,action:'reject',refundedTickets:1,replayed:false,grantedOwnedIds:[]}});
  expect(await count()).toBe(before);expect(await db.dailyProgress.count({where:{playerId:id}})).toBe(0);
  const replay=await new RecruitSessions(db,()=>null,()=>null).action(id,request);
  expect(replay).toEqual({...done,receipt:{...done.receipt,replayed:true}});expect(await balance()).toBe(999);
  await expect(service.action(id,{...request,requestId:randomUUID(),revision:1})).rejects.toMatchObject({code:'STALE_REVISION'});
  await expect(service.action(id,{...request,action:'claim'})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
 });

 it('concurrent identical request has one fresh receipt and only one credit increment',async()=>{
  const s=await service.start(id,input());await duplicate(s);const request=action(s);
  const results=await Promise.all([service.action(id,request),service.action(id,request)]);
  expect(results.map(r=>r.receipt.replayed).sort()).toEqual([false,true]);expect(await balance()).toBe(999);
  expect(await db.roster60RecruitReceipt.count({where:{playerId:id,requestId:request.requestId}})).toBe(1);
 });

 it('concurrent different reject requests leave one terminal transition and one refund',async()=>{
  const s=await service.start(id,input());await duplicate(s);const requests=[action(s),action(s)];
  const results=await Promise.allSettled(requests.map(r=>service.action(id,r)));
  expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);expect(await balance()).toBe(999);
  expect((await service.read(id,s.sessionId)).status).toBe('REJECTED');
  expect(await db.roster60RecruitReceipt.count({where:{playerId:id,requestId:{in:requests.map(r=>r.requestId)}}})).toBe(1);
 });

 it('parallel accounts may reuse the same request IDs without sharing candidates or refunds',async()=>{
  const request=input(),[a,b]=await Promise.all([service.start(id,request),service.start(other,request)]);
  const rejectId=randomUUID(),results=await Promise.all([service.action(id,{...action(a),requestId:rejectId}),service.action(other,{...action(b),requestId:rejectId})]);
  expect(a.sessionId).not.toBe(b.sessionId);expect(results.map(r=>r.credits)).toEqual([999,999]);
  expect(results[0].pendingSessions).toHaveLength(0);expect(results[1].pendingSessions).toHaveLength(0);
  expect(await db.roster60RecruitReceipt.count({where:{requestId:rejectId,playerId:{in:[id,other]}}})).toBe(2);
 });

 it('bulk acceptance races atomically with rejection and accepts only remaining candidates afterward',async()=>{
  const s=await service.start(id,input(3));await duplicate(s);const before=await count(),target=s.sessions[2];
  const results=await Promise.allSettled([service.action(id,action(target)),service.claimAll(id,{requestId:randomUUID(),sessions:rows(s)})]);
  expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);
  const row=await db.roster60RecruitSession.findUniqueOrThrow({where:{id:target.sessionId}}),rejected=row.status==='REJECTED';
  const pending=(await service.info(id)).pendingSessions;
  if(rejected){expect(pending).toHaveLength(2);const done=await service.claimAll(id,{requestId:randomUUID(),sessions:pending.map(r=>({sessionId:r.sessionId,revision:r.revision}))});expect(done.pendingSessions).toHaveLength(0);}
  else expect(pending).toHaveLength(0);
  expect(await count()).toBe(before+(rejected?2:3));expect(await balance()).toBe(rejected?995:994);
 });

 it('revalidates ownership at submission and never treats consumed copies as currently owned',async()=>{
  const second=new RecruitSessions(db,()=>catalog,()=>policy,max=>max===10000?0:1),s=await second.start(id,input());
  expect(s.canReject).toBe(false);expect(s.rejectReason).toBe('NOT_OWNED_SPECIES');
  await expect(service.action(id,action(s))).rejects.toMatchObject({code:'RECRUIT_NOT_OWNED_SPECIES'});
  await duplicate(s);expect((await service.read(id,s.sessionId)).canReject).toBe(true);
  await db.roster60Owned.updateMany({where:{playerId:id,speciesId:s.candidate.speciesId},data:{status:'CONSUMED'}});
  await expect(service.action(id,action(s))).rejects.toMatchObject({code:'RECRUIT_NOT_OWNED_SPECIES'});expect(await balance()).toBe(998);
 });

 it('foreign candidate IDs, stale revisions, and already claimed rows cannot refund',async()=>{
  const s=await service.start(id,input());await duplicate(s);
  await expect(service.action(other,action(s))).rejects.toMatchObject({code:'RECRUIT_SESSION_NOT_FOUND'});
  await expect(service.read(other,s.sessionId)).rejects.toMatchObject({code:'RECRUIT_SESSION_NOT_FOUND'});
  await expect(service.action(id,{...action(s),revision:9})).rejects.toMatchObject({code:'STALE_REVISION'});
  await service.action(id,{...action(s),action:'claim'});
  await expect(service.action(id,{...action(s),revision:1})).rejects.toMatchObject({code:'STALE_REVISION'});expect(await balance()).toBe(998);
 });

 it('free admin generation and historical candidates without debit proof remain claimable but cannot mint refunds',async()=>{
  await db.player.update({where:{id},data:{isAdmin:true,adminMode:true}});
  const free=await service.start(id,input());await duplicate(free);
  expect((await service.read(id,free.sessionId))).toMatchObject({canReject:false,rejectReason:'FREE_GENERATION'});
  await expect(service.action(id,action(free))).rejects.toMatchObject({code:'RECRUIT_REFUND_UNAVAILABLE'});expect(await balance()).toBe(1000);
  await service.claimAll(id,{requestId:randomUUID(),sessions:rows(free)});await db.player.update({where:{id},data:{adminMode:false}});
  const legacy=await service.start(id,input()),row=await db.roster60RecruitSession.findUniqueOrThrow({where:{id:legacy.sessionId}}),snapshot=JSON.parse(row.snapshot);delete snapshot.paidBaseCost;
  await db.roster60RecruitSession.update({where:{id:row.id},data:{snapshot:JSON.stringify(snapshot)}});
  expect(await service.read(id,row.id)).toMatchObject({canReject:false,rejectReason:'PAYMENT_PROOF_UNAVAILABLE'});
  await expect(service.action(id,action(legacy))).rejects.toMatchObject({code:'RECRUIT_REFUND_UNAVAILABLE'});
  expect((await service.claimAll(id,{requestId:randomUUID(),sessions:rows(legacy)})).sessions[0].status).toBe('CLAIMED');
 });

 it('reroll replaces one candidate without creating another refund entitlement',async()=>{
  const s=await service.start(id,input());await duplicate(s);
  const rolled=await service.action(id,{...action(s),action:'reroll'});expect(rolled.credits).toBe(997);
  const done=await service.action(id,action(rolled));expect(done.credits).toBe(998);expect(done.receipt.refundedTickets).toBe(1);
  await expect(service.action(id,{...action(rolled),revision:2})).rejects.toMatchObject({code:'STALE_REVISION'});
 });

 it('refund balance overflow rolls back status, revision, and receipt, then the same request can succeed',async()=>{
  const s=await service.start(id,input());await duplicate(s);const request=action(s);
  await db.player.update({where:{id},data:{credits:2147483647}});
  await expect(service.action(id,request)).rejects.toMatchObject({code:'RECRUIT_BALANCE_LIMIT'});
  expect(await service.read(id,s.sessionId)).toMatchObject({status:'PENDING',revision:0,refundedTickets:0});
  expect(await db.roster60RecruitReceipt.count({where:{playerId:id,requestId:request.requestId}})).toBe(0);
  await db.player.update({where:{id},data:{credits:20}});expect((await service.action(id,request)).credits).toBe(21);
 });

 it('a failure writing the final receipt rolls back refund and terminal transition together',async()=>{
  const s=await service.start(id,input());await duplicate(s);const request=action(s),before=await balance();
  if(!/^[a-zA-Z0-9_-]+$/.test(id))throw Error('unsafe test identifier');
  await db.$executeRawUnsafe(`CREATE TRIGGER reject_receipt_rollback BEFORE INSERT ON Roster60RecruitReceipt WHEN NEW.playerId='${id}' BEGIN SELECT RAISE(ABORT, 'injected receipt failure'); END`);
  try{await expect(service.action(id,request)).rejects.toThrow();expect(await balance()).toBe(before);expect(await service.read(id,s.sessionId)).toMatchObject({status:'PENDING',revision:0,refundedTickets:0});}
  finally{await db.$executeRawUnsafe('DROP TRIGGER reject_receipt_rollback');}
  expect((await service.action(id,request)).credits).toBe(before+1);
 });

 it('all three existing offer costs stay unchanged and rejection-only loops cannot increase tickets',async()=>{
  const actual:RecruitSessionPolicy={...policy,offers:[{kind:'LOW',label:'low',cost:2,weights:{COMMON:100}},{kind:'MID',label:'mid',cost:6,weights:{UNCOMMON:100}},{kind:'HIGH',label:'high',cost:10,weights:{RARE:100}}]};
  const svc=new RecruitSessions(db,()=>catalog,()=>actual,()=>0);
  for(const offer of actual.offers){const before=await balance(),s=await svc.start(id,{...input(3),kind:offer.kind});await duplicate(s);
   expect(s.receipt.chargedTickets).toBe(offer.cost*3);expect(s.credits).toBe(before-offer.cost*3);
   for(const candidate of s.sessions)await svc.action(id,action(candidate));
   expect(await balance()).toBe(before-offer.cost*3+3);expect((await svc.info(id)).pendingSessions).toHaveLength(0);
  }
  expect(await db.dailyProgress.count({where:{playerId:id}})).toBe(0);
 });

 it('zero-cost policy cannot generate refundable candidates and a paid cost below the refund safety floor is blocked',async()=>{
  const zero=new RecruitSessions(db,()=>catalog,()=>({...policy,offers:[{...policy.offers[0],cost:0}]}),()=>0);
  await expect(zero.start(id,input())).rejects.toMatchObject({code:'RECRUIT_POLICY_INVALID'});expect(await balance()).toBe(1000);
  expect((await service.info(id)).pendingSessions).toHaveLength(0);
  const one=new RecruitSessions(db,()=>catalog,()=>({...policy,halfRounding:'ceil',offers:[{...policy.offers[0],cost:1}]}),()=>0),s=await one.start(id,input());
  expect(s).toMatchObject({canReject:false,rejectReason:'REFUND_COST_TOO_LOW'});
  await expect(one.action(id,action(s))).rejects.toMatchObject({code:'RECRUIT_REFUND_UNAVAILABLE'});expect(await balance()).toBe(999);
 });

 it.each([
  {kind:'LOW',cost:2,rarity:'COMMON'},
  {kind:'MID',cost:6,rarity:'UNCOMMON'},
  {kind:'HIGH',cost:10,rarity:'RARE'}
 ])('$kind: every permitted batch size refunds no more than the actual debit',async({kind,cost,rarity})=>{
  await db.player.update({where:{id},data:{credits:100000}});
  const svc=new RecruitSessions(db,()=>catalog,()=>({...policy,offers:[{kind,label:kind,cost,weights:{[rarity]:100}}]}),()=>0);
  for(let times=1;times<=50;times++){
   const before=await balance(),s=await svc.start(id,{...input(times),kind});await duplicate(s);const ownedBefore=await count();
   expect(s.receipt.chargedTickets).toBe(cost*times);
   const pending=(await svc.info(id)).pendingSessions;
   expect(pending).toHaveLength(times);expect(pending.reduce((sum,r)=>sum+r.rejectRefund,0)).toBe(times);
   let refunded=0;
   for(const candidate of pending){const done=await svc.action(id,action(candidate));refunded+=done.receipt.refundedTickets;expect(done.receipt.grantedOwnedIds).toHaveLength(0);}
   expect(refunded).toBe(times);expect(refunded).toBeLessThanOrEqual(s.receipt.chargedTickets);
   expect(await balance()).toBe(before-cost*times+refunded);expect(await count()).toBe(ownedBefore);
   expect((await svc.info(id)).pendingSessions).toHaveLength(0);
  }
 },120000);

 it('replayed rejection carries current balances and pending state after another candidate is accepted',async()=>{
  const s=await service.start(id,input(2));await duplicate(s);const request=action(s),done=await service.action(id,request);
  expect(done.pendingSessions).toHaveLength(1);await service.claimAll(id,{requestId:randomUUID(),sessions:rows(done)});
  const replay=await service.action(id,request);expect(replay.receipt).toMatchObject({requestId:request.requestId,refundedTickets:1,replayed:true});expect(replay.pendingSessions).toHaveLength(0);expect(replay.credits).toBe(await balance());
 });

 it('authenticated API authorizes rejection, rejects client eligibility claims and supports a fifty-candidate acceptance body',async()=>{
  const previous=process.env.ROSTER60_PROFILE,secret='isolated-reject-api-secret-only-not-production';process.env.ROSTER60_PROFILE='five-stat-device-test-v1';
  const app=await buildApp(db,new GameService(db,loadContent()),{menuLocks:true,secret,baseUrl:async()=>'https://example.com'});
  const key=createHmac('sha256',secret).update('web-session').digest(),v=`${id}.${Math.floor(Date.now()/1000)+3600}`,headers={'x-gmm':'1','x-gmm-s':`${v}.${createHmac('sha256',key).update(v).digest('base64url')}`};
  const call=(path:string,payload:any)=>app.inject({method:'POST',url:'/my/api/native/roster60/recruit/session/'+path,headers,payload});
  try{
   const start=await call('start',input(50));expect(start.statusCode,start.body).toBe(200);const s=start.json();await duplicate(s);
   expect((await call('action',{...action(s),canReject:true})).statusCode).toBe(400);
   const request=action(s),rejection=await call('action',request);expect(rejection.statusCode,rejection.body).toBe(200);const done=rejection.json();expect(done.receipt).toMatchObject({refundedTickets:1,replayed:false});expect(done.pendingSessions).toHaveLength(49);
   const replay=await call('action',request);expect(replay.json().receipt.replayed).toBe(true);
   const accepted=await call('claim-all',{requestId:randomUUID(),sessions:rows(done)});expect(accepted.statusCode,accepted.body).toBe(200);expect(accepted.json().pendingSessions).toHaveLength(0);expect(accepted.json().receipt.grantedOwnedIds).toHaveLength(49);
  }finally{await app.close();if(previous===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previous;}
 });
});
