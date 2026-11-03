import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterPvp} from '../src/roster60-pvp.js';
import {instanceGrowthCurve} from '../src/battle/roster60-rules.js';
import type {NormalXpPolicy} from '../src/progression/normal-xp.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const originalCatalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const originalBattle=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
// 이미 v2로 고정된 대기·대전의 정산 규칙을 검증한다. 새 배포 정책(v3)은 test/finisher-split.test.ts에서 검증한다.
const originalNormal=JSON.parse(readFileSync('test/fixtures/normal-xp-policy-v2.json','utf8'));
// 「승리 2번에 1레벨·출전 개체 균등」 예전 판을 검증한다(이미 고정된 전투는 이 판으로 정산). 새 배포 판은 test/finisher-split.test.ts에서 검증한다.
const pacing=JSON.parse(readFileSync('test/fixtures/unit-xp-pacing-policy-two-wins.json','utf8'));
const request=()=>randomUUID();
type Actor={id:string;ids:string[];starting:bigint[]};

suite('normal reward uplift through real service finish (disposable SQLite only)',()=>{
 let db:PrismaClient,url:string,now:number,catalog:any,battle:any,normal:NormalXpPolicy|null,service:RosterPvp;
 const owners=new Set<string>();
 const restart=(client=db)=>new RosterPvp(client,()=>catalog,()=>battle,()=>now,()=>normal,async()=>60);
 const enable=()=>{battle={...structuredClone(originalBattle),xpPacing:structuredClone(pacing)};normal={...structuredClone(originalNormal),xpPacing:structuredClone(pacing)};};
 beforeAll(()=>{url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(()=>{now=Date.now();catalog=structuredClone(originalCatalog);enable();service=restart();});
 afterEach(async()=>{
  const ids=[...owners];await db.roster60PvpMatch.deleteMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});await db.auditEvent.deleteMany({where:{playerId:{in:ids}}});await db.dailyProgress.deleteMany({where:{playerId:{in:ids}}});await db.player.deleteMany({where:{id:{in:ids}}});owners.clear();
 });
 afterAll(async()=>{if(db)await db.$disconnect();});

 async function actor(level=15,count=3,reincarnation=0,atXp?:bigint):Promise<Actor>{
  const player=await db.player.create({data:{identity:'normal-uplift:'+request()}});owners.add(player.id);
  const definition=catalog.characters.find((u:any)=>u.id==='u45'),curve=instanceGrowthCurve(definition.growth,reincarnation,definition.reincarnationXpPolicy),ids:string[]=[],starting:bigint[]=[];
  for(let index=0;index<count;index++){const totalXp=atXp??BigInt(curve[level-1].totalXp),owned=await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u45',totalXp,reincarnation,source:request()}});ids.push(owned.id);starting.push(totalXp);}
  await service.party(player.id,ids);return {id:player.id,ids,starting};
 }
 async function stored(room:string){const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:room}});return {row,state:JSON.parse(row.state),snapshot:JSON.parse(row.snapshot)};}
 async function view(actor:Actor){return (await service.current(actor.id) as any).battle;}
 async function pair(a:Actor,b:Actor){
  expect((await service.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');const joined:any=await service.join(b.id,{requestId:request(),mode:'normal'});expect(joined.status).toBe('matched');
  return accept(a,b,joined.battle);
 }
 async function accept(a:Actor,b:Actor|undefined,battleView:any){
  await service.accept(a.id,{requestId:request(),battleId:battleView.battleId,revision:battleView.revision,accept:true});if(b)await service.accept(b.id,{requestId:request(),battleId:battleView.battleId,revision:battleView.revision,accept:true});return battleView.battleId as string;
 }
 async function ai(a:Actor){
  expect((await service.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');now+=30_000;const current:any=await service.current(a.id);expect(current.battle.opponentKind).toBe('AI');return accept(a,undefined,current.battle);
 }
 async function damageOnly(room:string){
  const saved=await stored(room);for(const party of saved.state.parties)for(const fighter of party){fighter.attack.power=0;fighter.attack.mpCost=0;for(const skill of fighter.skills){skill.power=0;skill.mpCost=0;}}
  // Damage-only fixture: actual service commands still record participation and settle XP.
  await db.roster60PvpMatch.update({where:{id:room},data:{state:JSON.stringify(saved.state)}});
 }
 async function enterAll(room:string,a:Actor,b?:Actor){
  await damageOnly(room);
  for(let index=1;index<a.ids.length;index++){
   const current=await view(a);await service.action(a.id,{requestId:request(),battleId:room,revision:current.revision,action:{kind:'switch',targetOwnedId:a.ids[index]}});
   if(b)await service.action(b.id,{requestId:request(),battleId:room,revision:current.revision,action:{kind:'switch',targetOwnedId:b.ids[index]}});
  }
 }
 async function finish(room:string,winner:Actor,loser?:Actor){
  const saved=await stored(room),side=saved.row.player0Id===winner.id?0:1,other=1-side;
  saved.state.parties[side][0].stats.speed=1000;saved.state.parties[side][0].attack={...saved.state.parties[side][0].attack,element:'NORMAL',power:1_000_000,mpCost:0};saved.state.parties[other][0].stats.speed=1;saved.state.parties[other][0].defense.NORMAL='normal';saved.state.parties[other][0].hp=1;
  for(const reserve of saved.state.parties[other].slice(1))reserve.hp=0;if(saved.state.botSide===1)saved.state.pending[1]={kind:'attack'};
  await db.roster60PvpMatch.update({where:{id:room},data:{state:JSON.stringify(saved.state)}});
  const current=await view(winner),attack={requestId:request(),battleId:room,revision:current.revision,action:{kind:'attack' as const}};
  let receipt:any=await service.action(winner.id,attack),command:any=attack,owner=winner.id;
  if(loser){command={requestId:request(),battleId:room,revision:current.revision,action:{kind:'focus'}};owner=loser.id;receipt=await service.action(loser.id,command);}
  expect((await view(winner)).state.outcome).toBe('win');return {winner:await view(winner),loser:loser?await view(loser):null,receipt,command,owner};
 }
 async function deltas(actor:Actor){return Promise.all(actor.ids.map(async(id,index)=>(await db.roster60Owned.findUniqueOrThrow({where:{id}})).totalXp-actor.starting[index]));}
 async function acknowledge(actor:Actor){const current=await view(actor);await service.settle(actor.id,{requestId:request(),battleId:current.battleId,revision:current.revision});}

 it('pays three actually-entered Lv15 human winners 581 each and splits the existing half-loss pool',async()=>{
  const a=await actor(),b=await actor(),room=await pair(a,b);await enterAll(room,a,b);const done=await finish(room,a,b);
  expect(done.winner.settlement).toMatchObject({xpRequested:1743,xpAwarded:1743,xpDeferred:0,xpDiscarded:0});expect(await deltas(a)).toEqual([581n,581n,581n]);
  expect(done.loser.settlement.xpRequested).toBe(871);const losses=await deltas(b);expect(losses.reduce((a,b)=>a+b,0n)).toBe(871n);expect(losses.every(x=>x===290n||x===291n)).toBe(true);
  expect(await service.action(done.owner,done.command)).toEqual(done.receipt);expect(await deltas(a)).toEqual([581n,581n,581n]);expect(await db.roster60NormalXpLedger.count({where:{roomId:room}})).toBe(6);
 });

 it('counts the existing nearest Lv10 AI tier as appropriate for Lv15 and preserves 581 per entered unit',async()=>{
  const a=await actor(),room=await ai(a),created=await stored(room);expect(created.state.parties[1].map((f:any)=>f.level)).toEqual([10,10,10]);expect(created.snapshot.normalXp.players[0].enemyLevel).toBe(10);
  await enterAll(room,a);const done=await finish(room,a);expect(done.winner.settlement.xpAwarded).toBe(1743);expect(await deltas(a)).toEqual([581n,581n,581n]);
 });

 it('does not grant the Lv15 two-win floor for genuinely low Lv1 human opponents',async()=>{
  const a=await actor(),b=await actor(1),room=await pair(a,b);await enterAll(room,a,b);const done=await finish(room,a,b);
  expect(done.winner.settlement.xpAwarded).toBe(600);expect(await deltas(a)).toEqual([200n,200n,200n]);
 });

 it('retains an old queued plan when the schedule is activated before matching',async()=>{
  normal=structuredClone(originalNormal);battle=structuredClone(originalBattle);const a=await actor(),b=await actor();
  await service.join(a.id,{requestId:request(),mode:'normal'});const oldQueue=await db.roster60PvpQueue.findUniqueOrThrow({where:{playerId:a.id}});expect(JSON.parse(oldQueue.normalXpSnapshot!).policy.xpPacing).toBeUndefined();
  enable();const joined:any=await service.join(b.id,{requestId:request(),mode:'normal'}),room=await accept(a,b,joined.battle);await enterAll(room,a,b);const done=await finish(room,a,b);
  expect(done.winner.settlement.xpAwarded).toBe(600);expect(await deltas(a)).toEqual([200n,200n,200n]);expect(done.loser.settlement.xpRequested).toBe(871);
  const retained=(await stored(room)).snapshot.normalXp.players.find((p:any)=>p.playerId===a.id);expect(retained).toEqual(JSON.parse(oldQueue.normalXpSnapshot!));
 });

 it('reconnects and settles the pinned active schedule despite removing or changing current configuration',async()=>{
  const a=await actor(),b=await actor(),room=await pair(a,b);await enterAll(room,a,b);const pinned=(await stored(room)).row.snapshot;
  normal=null;battle=structuredClone(originalBattle);for(const source of battle.encounters)source.winXp=999999;
  const reconnect=new PrismaClient({datasources:{db:{url}}});service=restart(reconnect);
  try{
   expect((await view(a)).status).toBe('ACTIVE');const done=await finish(room,a,b);expect(done.winner.settlement.xpAwarded).toBe(1743);expect(await deltas(a)).toEqual([581n,581n,581n]);
   expect(await service.action(done.owner,done.command)).toEqual(done.receipt);await acknowledge(a);await acknowledge(b);expect((await stored(room)).row.snapshot).toBe(pinned);
   expect(await deltas(a)).toEqual([581n,581n,581n]);
  }finally{await reconnect.$disconnect();service=restart();}
 });

 it('leaves escape at zero for both players and replays the same zero decision',async()=>{
  const a=await actor(),b=await actor(),room=await pair(a,b);await enterAll(room,a,b);const current=await view(a),command={requestId:request(),battleId:room,revision:current.revision,action:{kind:'escape' as const}};
  const escaped:any=await service.action(a.id,command);expect(escaped.settlement).toMatchObject({reason:'SURRENDER',xpRequested:0,xpAwarded:0});expect(await deltas(a)).toEqual([0n,0n,0n]);expect(await deltas(b)).toEqual([0n,0n,0n]);expect(await service.action(a.id,command)).toEqual(escaped);
 });

 it('clamps raised requested XP at both real unit caps without overflow storage or automatic rebirth',async()=>{
  const def=catalog.characters.find((u:any)=>u.id==='u45');
  for(const cycle of [0,1]){
   const maximum=cycle?99:60,curve=instanceGrowthCurve(def.growth,cycle,def.reincarnationXpPolicy),cap=BigInt(curve[maximum-1].totalXp),a=await actor(maximum-1,1,cycle,cap-1n),b=await actor(maximum-1,1,cycle,cap-1n),room=await pair(a,b),done=await finish(room,a,b);
   expect(done.winner.settlement).toMatchObject({xpAwarded:1,xpDeferred:0});expect(done.winner.settlement.xpRequested).toBeGreaterThan(1);expect(done.winner.settlement.xpDiscarded).toBe(done.winner.settlement.xpRequested-1);
   expect(await db.roster60Owned.findUniqueOrThrow({where:{id:a.ids[0]}})).toMatchObject({totalXp:cap,reincarnation:cycle,status:'AVAILABLE'});expect(await db.roster60RebirthAccount.findUnique({where:{playerId:a.id}})).toBeNull();
   expect(await service.action(done.owner,done.command)).toEqual(done.receipt);expect(await deltas(a)).toEqual([1n]);await acknowledge(a);await acknowledge(b);
  }
 });
});
