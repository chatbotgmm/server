import {beforeAll,beforeEach,afterEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import Fastify from 'fastify';
import {loadContent} from '../src/content.js';
import {RealtimePvpService} from '../src/pvp/service.js';
import {registerRealtimePvp} from '../src/pvp/routes.js';
import {V2_CLIENT_HEADERS} from './helpers/v2-client.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('explicit match acceptance (isolated SQLite)',()=>{
 let db:PrismaClient,service:RealtimePvpService,now:number;
 const content=loadContent(),secret='acceptance-synthetic-secret'.repeat(3);
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('Isolated test DB required');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{await db.$transaction([db.realtimeItemEscrow.deleteMany(),db.realtimeLineup.deleteMany(),db.realtimeRequest.deleteMany(),db.realtimeActivity.deleteMany(),db.realtimeAttempt.deleteMany(),db.realtimeRoom.deleteMany(),db.rankedSettlement.deleteMany(),db.rankedMatch.deleteMany(),db.rankedProfile.deleteMany(),db.casualHumanSettlement.deleteMany(),db.casualHumanRating.deleteMany()]);now=Date.now();service=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);});
 afterEach(()=>service.stop());afterAll(()=>db.$disconnect());
 async function player(){const p=await db.player.create({data:{identity:'acceptance-'+randomUUID(),battleTeam:'D1,T1,E1'}});for(const characterId of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});return p;}
 async function pair(mode:'rank'|'normal'='rank'){const a=await player(),b=await player(),ta=await service.attempt(a.id,mode),tb=await service.attempt(b.id,mode);await service.join(a.id,ta.attemptId,ta.token);const queued=(await db.realtimeAttempt.findUniqueOrThrow({where:{id:ta.attemptId}})).queuedAt;now+=1000;const q=await service.join(b.id,tb.attemptId,tb.token);expect(q.status).toBe('matched');return {a,b,ta,tb,queued,roomId:(q as {roomId:string}).roomId};}
 async function view(pid:string,roomId:string):Promise<any>{return service.currentRoom(pid,roomId);}
 async function accept(pid:string,roomId:string,value=true,requestId:string=randomUUID(),phaseId?:string):Promise<any>{const v=await view(pid,roomId);return service.command(pid,{roomId,phaseId:phaseId??v.phaseId,requestId,command:{accept:value}} as any);}
 async function noSettlement(){expect(await db.rankedMatch.count()).toBe(0);expect(await db.rankedSettlement.count()).toBe(0);expect(await db.casualHumanSettlement.count()).toBe(0);expect(await db.realtimeItemEscrow.count()).toBe(0);expect(await db.realtimeLineup.count()).toBe(0);}
 it('requires protocol 3 on attempt route without changing service attempt arguments',async()=>{
  const p=await player(),app=Fastify();registerRealtimePvp(app,service,()=>p.id,()=>true);service.stop();
  try{for(const payload of [{mode:'normal'},{mode:'normal',protocol:2}])expect((await app.inject({method:'POST',url:'/my/api/pvp/queue/attempt',headers:V2_CLIENT_HEADERS,payload})).statusCode).toBe(400);
   const r=await app.inject({method:'POST',url:'/my/api/pvp/queue/attempt',headers:V2_CLIENT_HEADERS,payload:{mode:'normal',protocol:3}});expect(r.statusCode).toBe(200);expect(r.json()).toHaveProperty('attemptId');
  }finally{await app.close();}
 });
 it.each(['rank','normal'] as const)('%s creates private 10-second acceptance before battle/preparation',async mode=>{
  const p=await pair(mode),a=await view(p.a.id,p.roomId),b=await view(p.b.id,p.roomId);
  expect(a).toMatchObject({phase:'accept',battle:null,acceptance:{accepted:false,opponentAccepted:false},deadline:now+10_000});expect(b).toMatchObject({phase:'accept',battle:null,acceptance:{accepted:false,opponentAccepted:false}});await noSettlement();
  expect(a.acceptance).not.toHaveProperty('opponentTeam');
 });
 it('one acceptance persists across restart and is viewer-relative without starting battle',async()=>{
  const p=await pair(),before=await view(p.a.id,p.roomId);await accept(p.a.id,p.roomId);
  const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
  expect(await restarted.currentRoom(p.a.id,p.roomId)).toMatchObject({phase:'accept',deadline:before.deadline,acceptance:{accepted:true,opponentAccepted:false}});
  expect(await view(p.b.id,p.roomId)).toMatchObject({acceptance:{accepted:false,opponentAccepted:true}});await noSettlement();
 });
 it('both accepting creates one ranked match and a fresh 30-second preparation phase',async()=>{
  const p=await pair();await accept(p.a.id,p.roomId);now+=9000;const ready=await accept(p.b.id,p.roomId);
  expect(ready).toMatchObject({phase:'prepare',battle:null,deadline:now+30_000});expect(await db.rankedMatch.count()).toBe(1);expect(await db.rankedSettlement.count()).toBe(0);
  expect(await db.realtimeLineup.count()).toBe(0);
 });
 it('simultaneous duplicate accepts advance once and preserve exactly one room/match',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);
  const cmd=(pid:string,requestId:string)=>service.command(pid,{roomId:p.roomId,phaseId:v.phaseId,requestId,command:{accept:true}} as any);
  await Promise.all([cmd(p.a.id,'a'),cmd(p.a.id,'a'),cmd(p.b.id,'b')]);
  expect((await view(p.a.id,p.roomId)).phase).toBe('prepare');expect(await db.rankedMatch.count()).toBe(1);expect(await db.realtimeRoom.count()).toBe(1);
  await cmd(p.a.id,'a');expect(await db.rankedMatch.count()).toBe(1);
 });
 it('decline requeues only the accepted peer and preserves its original queue age',async()=>{
  const p=await pair();await accept(p.a.id,p.roomId);const out=await accept(p.b.id,p.roomId,false,'decline');
  expect(out.result).toMatchObject({reason:'MATCH_DECLINED',settlement:'not_applicable'});expect(out.result.requeued).toBe(false);
  const av=await view(p.a.id,p.roomId);expect(av.result.requeued).toBe(true);expect(av.result).toMatchObject({reason:'MATCH_DECLINED',settlement:'not_applicable'});
  const a=await db.realtimeAttempt.findUniqueOrThrow({where:{id:p.ta.attemptId}});expect(a.state).toBe('WAITING');expect(a.queuedAt).toEqual(p.queued);
  expect((await service.currentQueue(p.a.id)).status).toBe('waiting');expect((await service.currentQueue(p.b.id)).status).not.toBe('waiting');await noSettlement();
  await accept(p.b.id,p.roomId,false,'decline');expect(await db.realtimeActivity.count({where:{playerId:p.a.id}})).toBe(1);
 });
 it('decline without either acceptance requeues nobody or changes ratings/items',async()=>{
  const p=await pair('normal');await db.playerItem.create({data:{playerId:p.a.id,itemId:'potion',count:2}});const ratings=await db.casualHumanRating.findMany({orderBy:{playerId:'asc'}});
  const out=await accept(p.a.id,p.roomId,false);expect(out.result.requeued).toBe(false);expect((await view(p.b.id,p.roomId)).result.requeued).toBe(false);
  expect(await db.realtimeActivity.count()).toBe(0);expect(await db.casualHumanRating.findMany({orderBy:{playerId:'asc'}})).toEqual(ratings);expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.a.id,itemId:'potion'}}})).count).toBe(2);await noSettlement();
 });
 it('exact deadline rejects late acceptance and requeues only the earlier accepter',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);await accept(p.a.id,p.roomId);now=v.deadline;
  const out=await accept(p.b.id,p.roomId);expect(out.result).toMatchObject({reason:'MATCH_TIMEOUT',settlement:'not_applicable'});expect(out.result.requeued).toBe(false);expect((await view(p.a.id,p.roomId)).result.requeued).toBe(true);
  expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:p.ta.attemptId}})).queuedAt).toEqual(p.queued);await noSettlement();
 });
 it.each(['decline','surrender'] as const)('late %s cancels the accepted player returned to waiting without rematching',async kind=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);await accept(p.a.id,p.roomId);now=v.deadline;
  const out=kind==='decline'?await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'late-no',command:{accept:false}}):await service.surrender(p.a.id,p.roomId,'late-leave');
  expect(out.result).toMatchObject({reason:'MATCH_TIMEOUT',requeued:false,settlement:'not_applicable'});expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:p.ta.attemptId}})).state).toBe('CANCELLED');expect(await db.realtimeActivity.count({where:{playerId:p.a.id}})).toBe(0);await service.tick();expect(await db.realtimeRoom.count()).toBe(1);await noSettlement();
 });
 it('a delayed decline never forfeits a new match already found for the returned ticket',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);await accept(p.a.id,p.roomId);await accept(p.b.id,p.roomId,false);const c=await player(),t=await service.attempt(c.id,'rank');const q=await service.join(c.id,t.attemptId,t.token);expect(q.status).toBe('matched');const next=(q as {roomId:string}).roomId;expect(next).not.toBe(p.roomId);
  const out=await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'delayed-decline',command:{accept:false}});expect(out.result).toMatchObject({requeued:true});expect((await db.realtimeRoom.findUniqueOrThrow({where:{id:next}})).state).toBe('ACTIVE');expect((await service.currentQueue(p.a.id)) as any).toMatchObject({status:'matched',roomId:next});await noSettlement();
 });
 it('a delayed old decline cannot cancel an unrelated newer queue attempt',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);await accept(p.a.id,p.roomId);await accept(p.b.id,p.roomId,false);await service.cancel(p.a.id,p.ta.attemptId,p.ta.token,'cancel-return');const t=await service.attempt(p.a.id,'rank');await service.join(p.a.id,t.attemptId,t.token);
  await service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'old-decline',command:{accept:false}});expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:t.attemptId}})).state).toBe('WAITING');expect((await db.realtimeActivity.findUniqueOrThrow({where:{playerId:p.a.id}})).targetId).toBe(t.attemptId);
 });
 it('timeout with no acceptance releases both without ranked LP or settlement',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId),before=await db.rankedProfile.findMany({orderBy:{playerId:'asc'}});now=v.deadline-1;await service.tick();expect((await view(p.a.id,p.roomId)).phase).toBe('accept');
  now++;await Promise.all([service.tick(),service.tick()]);const a=await view(p.a.id,p.roomId);expect(a.result).toMatchObject({reason:'MATCH_TIMEOUT',settlement:'not_applicable'});expect(a.result.requeued).toBe(false);expect((await view(p.b.id,p.roomId)).result.requeued).toBe(false);
  expect(await db.rankedProfile.findMany({orderBy:{playerId:'asc'}})).toEqual(before);expect(await db.realtimeActivity.count()).toBe(0);await noSettlement();
 });
 it('AI is preaccepted but the human must accept before preparation',async()=>{
  const p=await player(),t=await service.attempt(p.id,'normal');await service.join(p.id,t.attemptId,t.token);now+=30_000;const q=await service.currentQueue(p.id),roomId=(q as {roomId:string}).roomId;
  expect(await view(p.id,roomId)).toMatchObject({phase:'accept',battle:null,opponentKind:'AI',acceptance:{accepted:false,opponentAccepted:true},deadline:now+10_000});await noSettlement();
  expect(await accept(p.id,roomId)).toMatchObject({phase:'prepare',battle:null,deadline:now+30_000});expect(await db.rankedMatch.count()).toBe(0);
 });
 it('AI timeout never requeues a nonresponding human',async()=>{
  const p=await player(),t=await service.attempt(p.id,'normal');await service.join(p.id,t.attemptId,t.token);now+=30_000;const q=await service.currentQueue(p.id),roomId=(q as {roomId:string}).roomId;now+=10_000;await service.tick();
  expect(await view(p.id,roomId)).toMatchObject({result:{requeued:false,reason:'MATCH_TIMEOUT',settlement:'not_applicable'}});expect(await db.realtimeActivity.count()).toBe(0);await noSettlement();
 });
 it('accept versus decline race never starts battle when the second player declines',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId);await Promise.all([service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'yes',command:{accept:true}} as any),service.command(p.b.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'no',command:{accept:false}} as any)]);
  expect((await view(p.a.id,p.roomId)).result).toMatchObject({reason:'MATCH_DECLINED',settlement:'not_applicable'});expect((await view(p.b.id,p.roomId)).result.requeued).toBe(false);await noSettlement();
 });
 it('legacy preparation rooms without acceptance continue without another acceptance prompt',async()=>{
  const p=await pair('normal'),r=await db.realtimeRoom.findUniqueOrThrow({where:{id:p.roomId}}),state=JSON.parse(r.stateJson);delete state.acceptance;
  await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(state),deadline:new Date(now+30_000)}});
  const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
  for(const human of [p.a,p.b]){const v=await restarted.currentRoom(human.id,r.id);expect(v.phase).toBe('prepare');await restarted.command(human.id,{roomId:r.id,phaseId:v.phaseId,requestId:randomUUID(),command:{prepare:{team:['D1','T1','E1'],ready:true}}});}
  const ready=await restarted.currentRoom(p.a.id,r.id);expect(ready.phase).toBe('choose');expect(ready.battle).not.toBeNull();
 });
 it('old queued roster arrays are cancelled with UPGRADE_REQUIRED instead of matching',async()=>{
  const a=await player(),ta=await service.attempt(a.id,'rank');await service.join(a.id,ta.attemptId,ta.token);await db.realtimeAttempt.update({where:{id:ta.attemptId},data:{rosterJson:'[]'}});
  const b=await player(),tb=await service.attempt(b.id,'rank');await service.join(b.id,tb.attemptId,tb.token);await service.tick();
  const old=await service.currentQueue(a.id);expect(old).toMatchObject({status:'cancelled'});expect((old as any).notice).toContain('업데이트');expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:ta.attemptId}})).state).toBe('UPGRADE_REQUIRED');expect(await db.realtimeRoom.count()).toBe(0);await noSettlement();
 });
});

suite('realtime transaction scheduling (isolated SQLite)',()=>{
 let db:PrismaClient,service:RealtimePvpService,now:number;
 const content=loadContent(),secret='acceptance-synthetic-secret'.repeat(3);
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('Isolated test DB required');db=new PrismaClient({datasources:{db:{url:`${url}?connection_limit=5`}}});});
 beforeEach(async()=>{await db.$transaction([db.realtimeItemEscrow.deleteMany(),db.realtimeLineup.deleteMany(),db.realtimeRequest.deleteMany(),db.realtimeActivity.deleteMany(),db.realtimeAttempt.deleteMany(),db.realtimeRoom.deleteMany(),db.rankedSettlement.deleteMany(),db.rankedMatch.deleteMany(),db.rankedProfile.deleteMany(),db.casualHumanSettlement.deleteMany(),db.casualHumanRating.deleteMany()]);now=Date.now();service=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);});
 afterEach(()=>service.stop());afterAll(()=>db.$disconnect());
 async function player(){const p=await db.player.create({data:{identity:'acceptance-'+randomUUID(),battleTeam:'D1,T1,E1'}});for(const characterId of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});return p;}
 async function pair(mode:'rank'|'normal'='rank'){const a=await player(),b=await player(),ta=await service.attempt(a.id,mode),tb=await service.attempt(b.id,mode);await service.join(a.id,ta.attemptId,ta.token);const queued=(await db.realtimeAttempt.findUniqueOrThrow({where:{id:ta.attemptId}})).queuedAt;now+=1000;const q=await service.join(b.id,tb.attemptId,tb.token);expect(q.status).toBe('matched');return {a,b,ta,tb,queued,roomId:(q as {roomId:string}).roomId};}
 async function view(pid:string,roomId:string):Promise<any>{return service.currentRoom(pid,roomId);}
 async function accept(pid:string,roomId:string,value=true,requestId:string=randomUUID(),phaseId?:string):Promise<any>{const v=await view(pid,roomId);return service.command(pid,{roomId,phaseId:phaseId??v.phaseId,requestId,command:{accept:value}} as any);}
 async function noSettlement(){expect(await db.rankedMatch.count()).toBe(0);expect(await db.rankedSettlement.count()).toBe(0);expect(await db.casualHumanSettlement.count()).toBe(0);expect(await db.realtimeItemEscrow.count()).toBe(0);expect(await db.realtimeLineup.count()).toBe(0);}
 it('repeated simultaneous duplicate accepts on a constrained pool complete once',async()=>{
  for(let i=0;i<10;i++){
   const p=await pair(),v=await view(p.a.id,p.roomId);
   const command=(pid:string,requestId:string)=>service.command(pid,{roomId:p.roomId,phaseId:v.phaseId,requestId,command:{accept:true}});
   await Promise.all([command(p.a.id,'a'),command(p.a.id,'a'),command(p.b.id,'b')]);
   expect((await view(p.a.id,p.roomId)).phase).toBe('prepare');
   expect(await db.rankedMatch.count({where:{roomId:p.roomId}})).toBe(1);
   expect(await db.realtimeRequest.count({where:{roomId:p.roomId}})).toBe(2);
   await service.surrender(p.a.id,p.roomId,'cleanup');
  }
 });
 it('service instances sharing one Prisma client share the transaction queue',async()=>{
  const other=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
  for(let i=0;i<10;i++){
   const p=await pair(),v=await view(p.a.id,p.roomId);
   const input=(requestId:string)=>({roomId:p.roomId,phaseId:v.phaseId,requestId,command:{accept:true}});
   await Promise.all([service.command(p.a.id,input('a')),other.command(p.a.id,input('a')),other.command(p.b.id,input('b'))]);
   expect((await other.currentRoom(p.a.id,p.roomId)).phase).toBe('prepare');
   expect(await db.rankedMatch.count({where:{roomId:p.roomId}})).toBe(1);
   await service.surrender(p.a.id,p.roomId,'cleanup');
  }
 });
 it('failed validation releases the queue so already queued and later work can finish',async()=>{
  const p=await pair(),v=await view(p.a.id,p.roomId),other=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);
  const bad=service.command(p.a.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'invalid',command:{prepare:{team:['D1','T1','E1'],ready:true}}});
  const good=other.command(p.b.id,{roomId:p.roomId,phaseId:v.phaseId,requestId:'good-b',command:{accept:true}});
  const results=await Promise.allSettled([bad,good]);expect(results[0].status).toBe('rejected');expect(results[1].status).toBe('fulfilled');
  await accept(p.a.id,p.roomId);expect((await view(p.a.id,p.roomId)).phase).toBe('prepare');expect(await db.rankedMatch.count({where:{roomId:p.roomId}})).toBe(1);
 });
});
