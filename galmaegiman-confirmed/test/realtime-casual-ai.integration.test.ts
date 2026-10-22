import {createBattleService,serializeServiceBattle} from '../src/battle/service-factory.js';
import {beforeAll,beforeEach,afterEach,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {loadContent} from '../src/content.js';
import {loadBattleRules} from '../src/battle/load.js';
import {RealtimePvpService,REALTIME_LIMITS} from '../src/pvp/service.js';
import {chooseBotCommand} from '../src/battle/realtime-bot.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('casual-only AI fallback (isolated SQLite)',()=>{
 let db:PrismaClient,service:RealtimePvpService,now:number;
 const content=loadContent(),R=loadBattleRules(),secret='casual-ai-synthetic-secret'.repeat(3);
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:.*\/galmaegiman-tests-[^/]+\/test\.db$/.test(url))throw Error('Isolated test DB required');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(async()=>{await db.$transaction([db.realtimeItemEscrow.deleteMany(),db.realtimeLineup.deleteMany(),db.realtimeRequest.deleteMany(),db.realtimeActivity.deleteMany(),db.realtimeAttempt.deleteMany(),db.realtimeRoom.deleteMany(),db.rankedSettlement.deleteMany(),db.rankedMatch.deleteMany(),db.rankedProfile.deleteMany(),db.casualHumanSettlement.deleteMany(),db.casualHumanRating.deleteMany()]);now=Date.now();service=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);});
 afterEach(()=>service.stop());afterAll(()=>db.$disconnect());
 async function player(){const p=await db.player.create({data:{identity:'casual-ai-'+randomUUID(),battleTeam:'D1,T1,E1'}});for(const characterId of ['D1','T1','E1'])await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST'}});return p;}
 async function waiting(mode:'normal'|'rank'='normal'){const p=await player(),t=await service.attempt(p.id,mode);await service.join(p.id,t.attemptId,t.token);return {p,t};}
 async function fallback(){const x=await waiting();now+=REALTIME_LIMITS.casualAiAfterMs;const q=await service.currentQueue(x.p.id);expect(q.status).toBe('matched');const roomId=(q as {roomId:string}).roomId,v=await service.currentRoom(x.p.id,roomId);await service.command(x.p.id,{roomId,phaseId:v.phaseId,requestId:randomUUID(),command:{accept:true}});return {...x,roomId};}
 async function stored(roomId:string){const r=await db.realtimeRoom.findUniqueOrThrow({where:{id:roomId}});return {r,s:JSON.parse(r.stateJson)};}
 async function ready(x:Awaited<ReturnType<typeof fallback>>){const v=await service.currentRoom(x.p.id,x.roomId);return service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:randomUUID(),command:{prepare:{team:['D1','T1','E1'],ready:true}}});}
 const choice=(v:any)=>v.battle.teams[0].attributeSelection?{choose:{freestyle:attributeChoice(v.battle.teams[0].attributeSelection)}}:({choose:{...(v.battle.teams[0].offers.length?{augment:v.battle.teams[0].offers[0].id}:{}),...(v.battle.teams[0].needSwitch?{switchTo:v.battle.teams[0].fighters.findIndex((f:any)=>f.hp>0)}:{})}});
 const attributeChoice=(s:any)=>({selectionId:s.selectionId,phaseId:s.phaseId,targetKey:s.targetKey,attribute:s.attributes[0]});
 async function action(x:Awaited<ReturnType<typeof fallback>>){let v=await ready(x);for(let step=0;step<2&&v.phase==='choose';step++)v=await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:randomUUID(),command:choice(v)});expect(v.phase).toBe('action');return v;}
 it('waits the full 30 seconds and starts exactly one explicit AI preparation room',async()=>{
  const x=await waiting(),players=await db.player.count();now+=29_999;expect((await service.currentQueue(x.p.id)).status).toBe('waiting');expect(await db.realtimeRoom.count()).toBe(0);
  now++;const q=await service.currentQueue(x.p.id),roomId=(q as {roomId:string}).roomId;expect(q.status).toBe('matched');const proposal=await service.currentRoom(x.p.id,roomId);expect(proposal.phase).toBe('accept');const v=await service.command(x.p.id,{roomId,phaseId:proposal.phaseId,requestId:'accept-first',command:{accept:true}});
  expect(v).toMatchObject({mode:'normal',opponentKind:'AI',phase:'prepare',opponentReady:true,battle:null,deadline:now+30_000});expect(v.preparation?.selectedIds).toEqual((await db.ownedCharacter.findMany({where:{playerId:x.p.id},orderBy:{id:'asc'}})).map(x=>`o${x.id}`));
  await service.tick();await service.currentQueue(x.p.id);await service.join(x.p.id,x.t.attemptId,x.t.token);expect(await db.realtimeRoom.count()).toBe(1);expect(await db.player.count()).toBe(players);expect(await db.rankedMatch.count()).toBe(0);expect(await db.realtimeActivity.count()).toBe(1);
  const {r,s}=await stored(roomId);expect(s.botSide).toBe(1);expect(JSON.parse(r.namesJson)[1]).toContain('AI');expect(await db.player.findUnique({where:{id:r.player1Id}})).toBeNull();
 });
 it('ranked remains human-only through repeated waits well beyond the fallback threshold',async()=>{
  const x=await waiting('rank');for(let i=0;i<12;i++){now+=20_000;await service.currentQueue(x.p.id);await service.tick();}expect((await service.currentQueue(x.p.id)).status).toBe('waiting');expect(await db.realtimeRoom.count()).toBe(0);expect(await db.rankedMatch.count()).toBe(0);
 });
 it('an eligible human arriving at the boundary wins over AI creation',async()=>{
  const x=await waiting(),other=await player(),t=await service.attempt(other.id,'normal');now+=30_000;const q=await service.join(other.id,t.attemptId,t.token);expect(q.status).toBe('matched');const {r,s}=await stored((q as {roomId:string}).roomId);expect(s.botSide).toBeUndefined();expect(new Set([r.player0Id,r.player1Id])).toEqual(new Set([x.p.id,other.id]));
 });
 it('orphaned queue rows cannot block eligible humans or count as active population',async()=>{
  const x=await waiting();const original=await db.realtimeAttempt.findUniqueOrThrow({where:{id:x.t.attemptId}});await db.realtimeAttempt.update({where:{id:original.id},data:{rating:1000}});
  for(let i=0;i<100;i++)await db.realtimeAttempt.create({data:{id:'synthetic-ineligible-'+i,playerId:'never-match-'+i,mode:'normal',state:'WAITING',issuedAt:new Date(now),queuedAt:new Date(now+1),joinNotAfter:new Date(now+120_000),leaseUntil:new Date(now+45_000),rating:100_000+i*10_000}});
  const other=await player(),t=await service.attempt(other.id,'normal');now+=30_000;const q=await service.join(other.id,t.attemptId,t.token);expect(q.status).toBe('matched');expect((await stored((q as {roomId:string}).roomId)).s.botSide).toBeUndefined();
 });
 it('another active casual human blocks AI even outside the widening MMR window',async()=>{
  const a=await waiting();await db.realtimeAttempt.update({where:{id:a.t.attemptId},data:{rating:100000}});const b=await waiting();
  for(let i=0;i<6;i++){now+=20_000;await service.currentQueue(a.p.id);await service.currentQueue(b.p.id);await service.tick();}
  expect(await db.realtimeRoom.count()).toBe(0);expect((await service.currentQueue(a.p.id)).status).toBe('waiting');expect((await service.currentQueue(b.p.id)).status).toBe('waiting');
 });
 it('a ranked waiter never blocks a sole casual waiter or receives AI itself',async()=>{
  const casual=await waiting(),ranked=await waiting('rank');now+=30_000;await service.tick();expect((await service.currentQueue(casual.p.id)).status).toBe('matched');expect((await service.currentQueue(ranked.p.id)).status).toBe('waiting');expect(await db.realtimeRoom.count()).toBe(1);
 });
 it('disconnected stale leases stop blocking the still-active casual waiter',async()=>{
  const a=await waiting();await db.realtimeAttempt.update({where:{id:a.t.attemptId},data:{rating:100000}});const b=await waiting();now+=30_000;expect((await service.currentQueue(a.p.id)).status).toBe('waiting');now+=16_000;const q=await service.currentQueue(a.p.id);expect(q.status).toBe('matched');await service.tick();expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:b.t.attemptId}})).state).toBe('EXPIRED');expect(await db.realtimeRoom.count()).toBe(1);
 });
 it('cancelling the other casual waiter opens the sole-waiter gate without forfeiting either',async()=>{
  const a=await waiting();await db.realtimeAttempt.update({where:{id:a.t.attemptId},data:{rating:100000}});const b=await waiting();now+=30_000;await service.cancel(b.p.id,b.t.attemptId,b.t.token,'other-cancel');expect((await service.currentQueue(a.p.id)).status).toBe('matched');expect((await service.join(b.p.id,b.t.attemptId,b.t.token)).status).toBe('cancelled');expect(await db.realtimeRoom.count()).toBe(1);
 });
 it('population is measured after human pairs leave, retaining only the leftover original wait',async()=>{
  const a=await waiting();await db.realtimeAttempt.update({where:{id:a.t.attemptId},data:{rating:100000}});const b=await waiting();await db.realtimeAttempt.update({where:{id:b.t.attemptId},data:{rating:200000}});const c=await waiting();
  await db.realtimeAttempt.updateMany({where:{id:{in:[a.t.attemptId,b.t.attemptId]}},data:{rating:100000}});now+=30_000;await service.tick();
  const rooms=await db.realtimeRoom.findMany();expect(rooms).toHaveLength(2);const bot=rooms.find(r=>JSON.parse(r.stateJson).botSide===1)!;expect(bot.player0Id).toBe(c.p.id);expect(rooms.filter(r=>JSON.parse(r.stateJson).botSide===undefined)).toHaveLength(1);
 });
 it('a younger leftover does not receive immediate AI merely because older humans paired',async()=>{
  const a=await waiting();await db.realtimeAttempt.update({where:{id:a.t.attemptId},data:{rating:100000}});const b=await waiting();await db.realtimeAttempt.update({where:{id:b.t.attemptId},data:{rating:200000}});now+=10_000;const c=await waiting();await db.realtimeAttempt.updateMany({where:{id:{in:[a.t.attemptId,b.t.attemptId]}},data:{rating:100000}});now+=20_000;await service.tick();expect(await db.realtimeRoom.count()).toBe(1);expect((await service.currentQueue(c.p.id)).status).toBe('waiting');now+=10_000;expect((await service.currentQueue(c.p.id)).status).toBe('matched');expect(await db.realtimeRoom.count()).toBe(2);
 });
 it('cancel at the exact boundary prevents fallback when cancellation commits first',async()=>{
  const x=await waiting();now+=30_000;expect((await service.cancel(x.p.id,x.t.attemptId,x.t.token,'cancel')).status).toBe('cancelled');await service.tick();expect(await db.realtimeRoom.count()).toBe(0);expect((await service.join(x.p.id,x.t.attemptId,x.t.token)).status).toBe('cancelled');
 });
 it('cancel racing fallback is atomically cancelled or matched, never a forfeited duplicate',async()=>{
  const x=await waiting();now+=30_000;const [cancel]=await Promise.all([service.cancel(x.p.id,x.t.attemptId,x.t.token,'race'),service.tick()]);const a=await db.realtimeAttempt.findUniqueOrThrow({where:{id:x.t.attemptId}});
  if(cancel.status==='cancelled'){expect(a.state).toBe('CANCELLED');expect(await db.realtimeRoom.count()).toBe(0);}else{expect(cancel.status).toBe('matched');expect(a.state).toBe('MATCHED');expect(await db.realtimeRoom.count()).toBe(1);expect((await stored(a.roomId!)).r.state).toBe('ACTIVE');}
 });
 it('simultaneous human arrival and fallback yield a single room for the first player',async()=>{
  const x=await waiting(),p=await player(),t=await service.attempt(p.id,'normal');now+=30_000;await Promise.all([service.tick(),service.join(p.id,t.attemptId,t.token)]);const rooms=await db.realtimeRoom.findMany({where:{OR:[{player0Id:x.p.id},{player1Id:x.p.id}]}});expect(rooms).toHaveLength(1);expect(await db.realtimeActivity.count({where:{playerId:x.p.id}})).toBe(1);expect((await service.cancel(x.p.id,x.t.attemptId,x.t.token,'late')).status).toBe('matched');
 });
 it('expired leases and cancelled attempts never receive bots',async()=>{const x=await waiting();now+=46_000;await service.tick();expect(await db.realtimeRoom.count()).toBe(0);expect((await db.realtimeAttempt.findUniqueOrThrow({where:{id:x.t.attemptId}})).state).toBe('EXPIRED');});
 it('preparation keeps the last valid edited team and begins a fresh 30-second augment phase',async()=>{
  const x=await fallback(),v=await service.currentRoom(x.p.id,x.roomId);await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'edit',command:{prepare:{team:['T1','E1','D1'],ready:false}}});now=v.deadline!;await service.tick();const a=await service.currentRoom(x.p.id,x.roomId);expect(a.phase).toBe('choose');expect(a.deadline).toBe(now+30_000);expect((await db.player.findUniqueOrThrow({where:{id:x.p.id}})).battleTeam).toBe('T1,E1,D1');expect(await db.realtimeLineup.count()).toBe(1);
 });
 it('AI choices are private and persist unchanged across polls, human rerolls and restart',async()=>{
  const x=await fallback(),v=await ready(x),before=await stored(x.roomId);expect(before.s.pending[1]).toEqual(chooseBotCommand(createBattleService(R).restore(before.s.battle),1));expect(before.s.automatic[1]).toBe(false);expect(v.opponentReady).toBe(true);expect(v.battle!.teams[1].offers).toEqual([]);expect(v).not.toHaveProperty('pending');
  await service.currentRoom(x.p.id,x.roomId);await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'reroll',command:{reroll:0}});const restarted=new RealtimePvpService(db,content,process.cwd(),secret,()=>now);await restarted.currentRoom(x.p.id,x.roomId);const after=await stored(x.roomId);expect(after.s.pending[1]).toEqual(before.s.pending[1]);expect(after.r.deadline).toEqual(before.r.deadline);
  const r=await service.currentRoom(x.p.id,x.roomId);const cmd={roomId:x.roomId,phaseId:r.phaseId,requestId:'choose',command:choice(r)};const next=await service.command(x.p.id,cmd);const committed=await stored(x.roomId);expect(next.phase).toBe('action');expect(committed.s.pending[1]).toEqual(chooseBotCommand(createBattleService(R).restore(committed.s.battle),1));await service.command(x.p.id,cmd);expect((await stored(x.roomId)).s.pending[1]).toEqual(committed.s.pending[1]);
 });
 it('AI side cannot be authenticated even by passing its stored identifier to the service',async()=>{
  const x=await fallback(),{r}=await stored(x.roomId);await expect(service.currentRoom(r.player1Id,r.id)).rejects.toThrow('참가한');await expect(service.surrender(r.player1Id,r.id,'forged')).rejects.toThrow('참가한');
 });
 it('surrender is idempotent and never settles human MMR, legacy points, ranked LP or games',async()=>{
  const x=await fallback(),p=await db.player.findUniqueOrThrow({where:{id:x.p.id}}),rating=await db.casualHumanRating.findUniqueOrThrow({where:{playerId:x.p.id}});const a=await service.surrender(x.p.id,x.roomId,'leave');await service.surrender(x.p.id,x.roomId,'leave');await service.surrender(x.p.id,x.roomId,'again');expect(a.result).toMatchObject({winner:1,settlement:'not_applicable'});expect(await db.casualHumanRating.findUniqueOrThrow({where:{playerId:x.p.id}})).toEqual(rating);expect((await db.player.findUniqueOrThrow({where:{id:x.p.id}})).rankPoint).toBe(p.rankPoint);expect(await db.casualHumanSettlement.count()).toBe(0);expect(await db.rankedSettlement.count()).toBe(0);expect(await db.rankedProfile.count()).toBe(0);expect(await db.realtimeActivity.count()).toBe(0);
 });
 it('battle victory against AI leaves every human rating statistic unchanged',async()=>{
  const x=await fallback();await action(x);const {r,s}=await stored(x.roomId),b=createBattleService(R).restore(s.battle),rating=await db.casualHumanRating.findUniqueOrThrow({where:{playerId:x.p.id}});
  // This checks settlement through a real attack/finish, not random roster effects.
  // Augment selection can already have installed sleep, endure, protection or stat modifiers.
  // Normalize both sides' transient combat state before supplying deterministic lethal damage.
  b.rng=12345;
  for(const side of b.sides){
   side.augs=[];side.goFirst=false;side.neutral=0;side.needSwitch=false;
   for(const f of side.fighters){
    f.passive={};f.cds={};f.mods=[];f.status={};f.statusUntil={};f.guards=[];f.regens=[];
    f.endure=0;f.protect=false;f.recharge=false;f.immune=0;f.lockAtk=0;f.lastStandUsed=false;
    f.attacked=false;f.actedTurn=0;f.lastUtility={};f.healUntil=0;f.extraAttr=undefined;f.hitBy={};
    f.scale=1;f.attr='STONE';f.base={patk:100,pdef:100,matk:100,mdef:100,spd:100};
   }
  }
  const foe=b.sides[1];for(const f of foe.fighters)f.hp=0;const target=foe.fighters[foe.active];target.hp=1;target.passive={};target.guards=[];foe.augs=[];target.skills=[{id:'basic-bot-fixture',name:'fixture',cat:'phys',power:1,cd:0,effects:[],desc:''}];s.pending[1]={action:{kind:'skill',skillId:'basic-bot-fixture'}};
  const me=b.sides[0].fighters[b.sides[0].active];me.hp=me.maxHp;me.base.spd=1000;me.passive={};me.skills=[{id:'basic-victory-fixture',name:'fixture',cat:'phys',power:100000,cd:0,effects:[],desc:''}];
  s.battle=serializeServiceBattle(b);await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});
  const v=await service.currentRoom(x.p.id,x.roomId);const out=await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'win',command:{action:{kind:'skill',skillId:'basic-victory-fixture'}}});
  expect(out.result).toMatchObject({winner:0,reason:'BATTLE',settlement:'not_applicable'});expect(await db.casualHumanRating.findUniqueOrThrow({where:{playerId:x.p.id}})).toEqual(rating);expect(await db.casualHumanSettlement.count()).toBe(0);expect(await db.rankedSettlement.count()).toBe(0);
 });
 it('third missed human action forfeits to AI without a bot timeout or rating change',async()=>{
  const x=await fallback();await action(x);const {r,s}=await stored(x.roomId);s.misses=[2,0];await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});now=r.deadline!.getTime();await service.tick();const v=await service.currentRoom(x.p.id,x.roomId);expect(v.result).toMatchObject({winner:1,reason:'TIMEOUT',settlement:'not_applicable'});expect((await stored(r.id)).s.misses).toEqual([3,0]);expect((await db.casualHumanRating.findUniqueOrThrow({where:{playerId:x.p.id}})).games).toBe(0);
 });
 async function wounded(){const x=await fallback();await action(x);const {r,s}=await stored(x.roomId),b=createBattleService(R).restore(s.battle);b.sides[0].fighters[b.sides[0].active].hp=1;s.battle=serializeServiceBattle(b);await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});await db.playerItem.create({data:{playerId:x.p.id,itemId:'potion',count:1}});return x;}
 it('human item resolves with the precommitted AI action and is charged exactly once',async()=>{
  const x=await wounded(),v=await service.currentRoom(x.p.id,x.roomId);const cmd={roomId:x.roomId,phaseId:v.phaseId,requestId:'potion',command:{action:{kind:'item' as const,item:'potion'}}};await service.command(x.p.id,cmd);await service.command(x.p.id,cmd);expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:x.p.id,itemId:'potion'}}})).count).toBe(0);expect((await db.realtimeItemEscrow.findFirstOrThrow({where:{roomId:x.roomId}})).state).toBe('CONSUMED');expect(await db.realtimeItemEscrow.count({where:{roomId:x.roomId}})).toBe(1);
 });
 it('invalid and late AI-room item requests do not debit inventory',async()=>{
  const x=await wounded(),v=await service.currentRoom(x.p.id,x.roomId);await expect(service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'bad',command:{action:{kind:'item',item:'potion',target:2}}})).rejects.toThrow();now=v.deadline!;await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'late',command:{action:{kind:'item',item:'potion'}}});expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:x.p.id,itemId:'potion'}}})).count).toBe(1);expect(await db.realtimeItemEscrow.count({where:{roomId:x.roomId}})).toBe(0);
 });
 it('a resolution error refunds the human item in the same terminal transaction',async()=>{
  const x=await wounded(),{r,s}=await stored(x.roomId);s.pending[1]={action:{kind:'skill',skillId:'invalid-fixture'}};await db.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s)}});const v=await service.currentRoom(x.p.id,x.roomId);const out=await service.command(x.p.id,{roomId:x.roomId,phaseId:v.phaseId,requestId:'refund',command:{action:{kind:'item',item:'potion'}}});expect(out.result).toMatchObject({reason:'SERVER_ABORT',settlement:'not_applicable'});expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:x.p.id,itemId:'potion'}}})).count).toBe(1);expect((await db.realtimeItemEscrow.findFirstOrThrow({where:{roomId:x.roomId}})).state).toBe('REFUNDED');
 });
});
