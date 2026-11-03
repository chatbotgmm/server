import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {RosterPvp} from '../src/roster60-pvp.js';
import {defaultPresencePolicy,type PresenceState,type PresenceMonitor} from '../src/progression/roster60-presence.js';
import type {NormalXpPolicy} from '../src/progression/normal-xp.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const originalCatalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const originalBattle=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
// 이미 v2로 고정된 방의 정산 규칙으로 검증한다(이 시험의 대상은 v2 계약). 새 배포 정책(v3)은 test/finisher-split.test.ts·normal-xp.integration.test.ts에서 검증한다.
const originalXp=JSON.parse(readFileSync('test/fixtures/normal-xp-policy-v2.json','utf8')) as NormalXpPolicy;
const monitorKey='roster60-presence-monitor-v1';

suite('roster60 reconnect grace and server-observed departure (isolated service transactions)',()=>{
 let db:PrismaClient,service:RosterPvp,catalog:any,battlePolicy:any,xpPolicy:NormalXpPolicy,now:number,url:string;
 const owners=new Set<string>(),extraClients:PrismaClient[]=[];
 const request=()=>randomUUID();
 type Actor={id:string;ownedId:string};
 type Room={a:Actor;b:Actor;id:string;startedAt:number;sideA:0|1;sideB:0|1};
 const server=(client=db)=>new RosterPvp(client,()=>catalog,()=>battlePolicy,()=>now,()=>xpPolicy,undefined,{...defaultPresencePolicy});
 beforeAll(()=>{
  url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');
  db=new PrismaClient({datasources:{db:{url}}});
 });
 beforeEach(()=>{
  catalog=structuredClone(originalCatalog);battlePolicy=structuredClone(originalBattle);xpPolicy=structuredClone(originalXp);
  const source=battlePolicy.encounters.find((e:any)=>e.mode==='duel'&&!e.tutorial);
  // A single 100-XP source is an arithmetic fixture, not an approved reward amount.
  // The 45/90/45-second durations below are configurable test defaults as documented by the module.
  battlePolicy.encounters=[{...source,id:'reconnect-arithmetic-fixture',mode:'duel',tutorial:false,speciesId:'u02',enemySpeciesIds:['u02'],level:1,winXp:100,lossXp:0}];
  expect(xpPolicy).toMatchObject({rulesVersion:'normal-xp-v2',timeout:'validated-grace-survivor-only',disconnect:'validated-grace-survivor-only'});
  now=Date.now();service=server();
 });
 afterEach(async()=>{
  if(!db)return;const ids=[...owners];
  const rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]},select:{id:true}});
  await db.casualHumanSettlement.deleteMany({where:{roomId:{in:rooms.map(r=>r.id)}}});
  await db.roster60PvpMatch.deleteMany({where:{id:{in:rooms.map(r=>r.id)}}});
  await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});
  await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});
  await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});
  await db.dailyProgress.deleteMany({where:{playerId:{in:ids}}});
  await db.player.deleteMany({where:{id:{in:ids}}});
  await db.casualHumanRating.deleteMany({where:{playerId:{in:ids}}});owners.clear();
  for(const client of extraClients)await client.$disconnect();extraClients.length=0;
 });
 afterAll(async()=>{if(db)await db.$disconnect();});

 async function actor():Promise<Actor>{
  const p=await db.player.create({data:{identity:'reconnect-fixture:'+request()}});owners.add(p.id);
  const u=await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',source:'reconnect-fixture:'+request()}});
  await service.party(p.id,[u.id]);return {id:p.id,ownedId:u.id};
 }
 async function stored(id:string){const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id}});return {row,state:JSON.parse(row.state),presence:row.presence?JSON.parse(row.presence) as PresenceState:null,settlement:row.settlement?JSON.parse(row.settlement):null};}
 async function monitor():Promise<PresenceMonitor>{const row=await db.appMeta.findUniqueOrThrow({where:{key:monitorKey}});return JSON.parse(row.value);}
 async function passiveDamage(id:string){
  const snapshot=await stored(id);
  // Disable damage only in this disposable room so combat tuning cannot end a
  // reconnect fixture before its transport deadline. Presence/XP are untouched.
  for(const party of snapshot.state.parties)for(const fighter of party){fighter.attack.power=0;fighter.attack.mpCost=0;for(const skill of fighter.skills){skill.power=0;skill.mpCost=0;}}
  await db.roster60PvpMatch.update({where:{id},data:{state:JSON.stringify(snapshot.state)}});
 }
 async function pair():Promise<Room>{
  const a=await actor(),b=await actor();
  expect((await service.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');
  const joined:any=await service.join(b.id,{requestId:request(),mode:'normal'});expect(joined.battle.opponentKind).toBe('HUMAN');
  await service.accept(a.id,{requestId:request(),battleId:joined.battle.battleId,revision:joined.battle.revision,accept:true});
  const active:any=await service.accept(b.id,{requestId:request(),battleId:joined.battle.battleId,revision:joined.battle.revision,accept:true});expect(active.status).toBe('ACTIVE');
  await passiveDamage(active.battleId);const raw=await stored(active.battleId),sideA=raw.row.player0Id===a.id?0:1;
  expect(raw.presence).toMatchObject({version:'roster60-presence-v1'});
  return {a,b,id:active.battleId,startedAt:now,sideA,sideB:(1-sideA) as 0|1};
 }
 async function read(actor:Actor){return (await service.current(actor.id) as any).battle;}
 async function at(room:Pick<Room,'startedAt'>,offset:number,pollers:Actor[]=[]){
  now=room.startedAt+offset;for(const actor of pollers)await service.current(actor.id);await service.tick();
 }
 async function xp(actor:Actor){return (await db.roster60Owned.findUniqueOrThrow({where:{id:actor.ownedId}})).totalXp;}
 async function expectZero(room:Room){
  expect(await xp(room.a)).toBe(0n);expect(await xp(room.b)).toBe(0n);
  const records=await db.roster60NormalXpLedger.findMany({where:{roomId:room.id}});
  expect(records).toHaveLength(2);expect(records.every(r=>r.appliedXp===0&&r.deferredXp===0)).toBe(true);
 }
 async function acknowledge(actor:Actor,battle:any){
  const input={requestId:request(),battleId:battle.battleId,revision:battle.revision};
  const settled=await service.settle(actor.id,input);expect(await service.settle(actor.id,input)).toEqual(settled);
  await service.settle(actor.id,{...input,requestId:request()});
 }
 async function healthySurvivor(room:Room,until=75_000){for(let offset=15_000;offset<=until;offset+=15_000)await at(room,offset,[room.b]);}

 it('freezes missed-turn progression during 45-to-90-second grace and resumes when the player returns before cutoff',async()=>{
  const room=await pair();await healthySurvivor(room,45_000);const frozen=await stored(room.id);
  for(const offset of [60_000,75_000,89_999]){
   await at(room,offset,[room.b]);const next=await stored(room.id);
   expect(next.row.status).toBe('ACTIVE');expect(next.state.turn).toBe(frozen.state.turn);expect(next.state.misses).toEqual(frozen.state.misses);
  }
  const returned=await read(room.a);expect(returned.status).toBe('ACTIVE');
  const touched=await stored(room.id);expect(touched.presence!.participants[room.sideA]!.lastSeenAt).toBe(room.startedAt+89_999);
  await at(room,90_001,[room.a,room.b]);expect((await stored(room.id)).row.status).toBe('ACTIVE');
  expect(await xp(room.a)).toBe(0n);expect(await xp(room.b)).toBe(0n);
 },20000);

 it.each([90_000,90_001])('a late return at %i ms cannot erase the proven departure; only the continuous survivor receives one win award',async cutoff=>{
  const room=await pair();await healthySurvivor(room);await at(room,89_999,[room.b]);
  now=room.startedAt+cutoff;const departed=await read(room.a),survivor=await read(room.b);
  expect(departed.settlement).toMatchObject({reason:'TIMEOUT',terminationKind:'DISCONNECTED',outcome:'loss',xpAwarded:0,xpDeferred:0});
  expect(survivor.settlement).toMatchObject({reason:'TIMEOUT',terminationKind:'DISCONNECTED',outcome:'win',xpAwarded:100,xpDeferred:0});
  const terminal=await stored(room.id);expect(terminal.state.winner).toBe(room.sideB);
  expect(terminal.presence!.participants[room.sideA]!.lastSeenAt).toBe(room.startedAt);
  expect(await xp(room.a)).toBe(0n);expect(await xp(room.b)).toBe(100n);
  const count=await db.roster60NormalXpLedger.count({where:{roomId:room.id}});expect(count).toBe(2);
  for(let i=0;i<3;i++){expect((await read(room.b)).settlement.xpAwarded).toBe(100);await service.tick();}
  const second=new PrismaClient({datasources:{db:{url}}});extraClients.push(second);service=server(second);
  expect((await read(room.a)).settlement.xpAwarded).toBe(0);expect((await read(room.b)).settlement.xpAwarded).toBe(100);
  await acknowledge(room.a,departed);await acknowledge(room.b,survivor);
  expect((await service.current(room.a.id)).status).toBe('idle');expect((await service.current(room.b.id)).status).toBe('idle');
  expect(await xp(room.b)).toBe(100n);expect(await db.roster60NormalXpLedger.count({where:{roomId:room.id}})).toBe(count);
 },20000);

 it('does not infer a winner when both humans disappear while the server monitor continues successfully',async()=>{
  const room=await pair();const ratings=await db.casualHumanRating.findMany({where:{playerId:{in:[room.a.id,room.b.id]}},orderBy:{playerId:'asc'}});
  for(const offset of [15_000,30_000,45_000,60_000,75_000,90_000])await at(room,offset);
  const ended=await stored(room.id);expect(ended.settlement).toMatchObject({reason:'DOUBLE_TIMEOUT',winner:null});
  await expectZero(room);expect(await db.casualHumanRating.findMany({where:{playerId:{in:[room.a.id,room.b.id]}},orderBy:{playerId:'asc'}})).toEqual(ratings);
 },20000);

 it('a player appearing near the end cannot claim continuous residence and a free win',async()=>{
  const room=await pair();for(const offset of [15_000,30_000,45_000,60_000])await at(room,offset);
  await at(room,61_000,[room.b]);await at(room,75_000,[room.b]);await at(room,90_000,[room.b]);
  const ended=await stored(room.id);expect(ended.settlement).toMatchObject({reason:'DOUBLE_TIMEOUT',winner:null});
  await expectZero(room);
 },20000);

 it('a shared server-observation gap aborts neutrally without a defeat, rating change or XP award',async()=>{
  const room=await pair(),before=await monitor();
  const ratings=await db.casualHumanRating.findMany({where:{playerId:{in:[room.a.id,room.b.id]}},orderBy:{playerId:'asc'}});
  now=room.startedAt+45_001;const seen=await read(room.b);
  expect(seen.settlement).toMatchObject({reason:'SERVER_ABORT',winner:null,outcome:'draw',xpAwarded:0});
  expect((await monitor()).generation).toBeGreaterThan(before.generation);await expectZero(room);
  expect(await db.casualHumanRating.findMany({where:{playerId:{in:[room.a.id,room.b.id]}},orderBy:{playerId:'asc'}})).toEqual(ratings);
 },20000);

 it('another service and Prisma client reuse the healthy DB monitor epoch instead of inventing a restart outage',async()=>{
  const room=await pair();await at(room,15_000,[room.b]);const before=await monitor();
  const second=new PrismaClient({datasources:{db:{url}}});extraClients.push(second);service=server(second);
  await at(room,30_000,[room.b]);expect((await monitor()).generation).toBe(before.generation);expect((await stored(room.id)).row.status).toBe('ACTIVE');
  for(const offset of [45_000,60_000,75_000,90_000])await at(room,offset,[room.b]);
  expect((await read(room.b)).settlement).toMatchObject({reason:'TIMEOUT',terminationKind:'DISCONNECTED',xpAwarded:100});
  expect(await xp(room.a)).toBe(0n);expect(await xp(room.b)).toBe(100n);
 },20000);

 it('an explicitly persisted shared recovery generation neutralizes an old room even for a short restart',async()=>{
  const room=await pair();await at(room,15_000,[room.b]);const before=await monitor();now=room.startedAt+16_000;
  // Models the shared server recovery coordinator, not a per-process UUID or client claim.
  await db.appMeta.update({where:{key:monitorKey},data:{value:JSON.stringify({...before,generation:before.generation+1,lastSuccessAt:now,continuousSince:now})}});
  service=server();expect((await read(room.b)).settlement).toMatchObject({reason:'SERVER_ABORT',winner:null,xpAwarded:0});await expectZero(room);
 },20000);

 it('an AI room measures only the real human and pays no XP when that human leaves after grace',async()=>{
  const human=await actor();await service.join(human.id,{requestId:request(),mode:'normal'});now+=30_000;
  const queued:any=await service.current(human.id);expect(queued.battle.opponentKind).toBe('AI');
  await service.accept(human.id,{requestId:request(),battleId:queued.battle.battleId,revision:queued.battle.revision,accept:true});
  await passiveDamage(queued.battle.battleId);const clock={startedAt:now};
  for(const offset of [15_000,30_000,45_000,60_000,75_000,90_000])await at(clock,offset);
  const ended=await read(human);expect(ended.settlement).toMatchObject({reason:'TIMEOUT',terminationKind:'DISCONNECTED',outcome:'loss',xpAwarded:0});
  const raw=await stored(queued.battle.battleId);expect(raw.presence!.participants[1]).toBeNull();expect(await xp(human)).toBe(0n);
  const records=await db.roster60NormalXpLedger.findMany({where:{roomId:raw.row.id}});expect(records).toHaveLength(1);expect(records[0]).toMatchObject({playerId:human.id,ownedId:human.ownedId,appliedXp:0,deferredXp:0});
  expect(records.some(r=>r.playerId.startsWith('AI:')||r.ownedId.startsWith('AI:'))).toBe(false);
 },20000);

 it('invalid actions and another account queue poll do not regenerate the missing room participant presence',async()=>{
  const room=await pair();await at(room,15_000,[room.b]);const before=await stored(room.id);
  await expect(service.action(room.a.id,{requestId:request(),battleId:room.id,revision:before.row.revision,action:{kind:'skill',skillId:'not-a-learned-skill'}})).rejects.toMatchObject({code:'SKILL_NOT_EQUIPPED'});
  expect((await stored(room.id)).presence!.participants[room.sideA]!.lastSeenAt).toBe(room.startedAt);
  const waiter=await actor();await service.join(waiter.id,{requestId:request(),mode:'normal'});now=room.startedAt+20_000;
  expect((await service.current(waiter.id)).status).toBe('waiting');await service.options(room.a.id);
  expect((await stored(room.id)).presence!.participants[room.sideA]!.lastSeenAt).toBe(room.startedAt);
  for(const offset of [30_000,45_000,60_000,75_000,90_000])await at(room,offset,[room.b]);
  expect((await read(room.b)).settlement).toMatchObject({terminationKind:'DISCONNECTED',xpAwarded:100});expect(await xp(room.a)).toBe(0n);
 },20000);

 it('a human who keeps polling but misses three turns is an input timeout, not a proved disconnect win reward',async()=>{
  const room=await pair();let active=await read(room.b);
  await service.action(room.b.id,{requestId:request(),battleId:room.id,revision:active.revision,action:{kind:'focus'}});
  for(const offset of [15_000,30_000,45_000,60_000,75_000,90_000]){
   await at(room,offset,[room.a,room.b]);
   if(offset===30_000||offset===60_000){active=await read(room.b);expect(active.status).toBe('ACTIVE');await service.action(room.b.id,{requestId:request(),battleId:room.id,revision:active.revision,action:{kind:'focus'}});}
  }
  const ended=await stored(room.id);expect(ended.settlement).toMatchObject({reason:'TIMEOUT',terminationKind:'TURN_INPUT',winner:room.sideB});
  expect(ended.state.misses[room.sideA]).toBe(3);expect(ended.state.misses[room.sideB]).toBe(0);await expectZero(room);
 },20000);

 it('a pre-presence stored room is released neutrally without fabricating old absence evidence',async()=>{
  const room=await pair();
  await db.roster60PvpMatch.update({where:{id:room.id},data:{presence:null}});
  now=room.startedAt+1000;expect((await read(room.b)).settlement).toMatchObject({reason:'SERVER_ABORT',winner:null,xpAwarded:0});
  await expectZero(room);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:room.a.ownedId}})).status).toBe('AVAILABLE');expect((await db.roster60Owned.findUniqueOrThrow({where:{id:room.b.ownedId}})).status).toBe('AVAILABLE');
 },20000);
});
