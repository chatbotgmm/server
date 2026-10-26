import {beforeAll,afterAll,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {RosterLibrary} from '../src/roster60-library.js';
import {RecruitSessions} from '../src/roster60-recruit-session.js';
import {RosterCraft} from '../src/roster60-craft.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {fighter} from '../src/roster60-battle.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {rosterDailyDay} from '../src/roster60-daily-progress.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('actual new roster completions share daily counters and existing ticket rewards atomically',()=>{
 let db:PrismaClient;const ids:string[]=[],content=loadContent();
 const reward=(key:string)=>content.economy.missions.find(m=>m.key===key)!.tickets;
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated only');db=new PrismaClient({datasources:{db:{url}}});});
 afterAll(async()=>{
  const where={playerId:{in:ids}};await db.auditEvent.deleteMany({where});await db.dailyProgress.deleteMany({where});await db.playerItem.deleteMany({where});await db.progressionState.deleteMany({where});await db.player.deleteMany({where:{id:{in:ids}}});await db.$disconnect();
 });
 function environment(){
  const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
  const clock={now:new Date('2026-10-26T03:00:00Z'),calls:0},now=()=>{clock.calls++;return new Date(clock.now);};
  catalog.recipes=['u09','u17','u31','u45'].map(resultId=>({resultId,ingredients:[{unitId:'u02',count:2,minLevel:1}],visibility:'public'}));
  const policy:any={version:'daily-test-only',pity:'none',halfRounding:null,offers:[{kind:'LOW',label:'test',cost:2,weights:{COMMON:100}}]};
  return {catalog,clock,now,library:new RosterLibrary(db,()=>catalog,()=>0,now),sessions:new RecruitSessions(db,()=>catalog,()=>policy,()=>0,now),
   craft:new RosterCraft(db,()=>catalog,now),adventure:new RosterAdventure(db,()=>catalog,fighter,now)};
 }
 async function account(e:ReturnType<typeof environment>,credits=1000){
  const p=await db.player.create({data:{identity:randomUUID(),credits}});ids.push(p.id);
  await new RosterOnboarding(db,()=>e.catalog).start(p.id);
  await db.progressionState.create({data:{playerId:p.id,points:17,dailyPoints:2,pointDay:'unchanged-day'}});return p.id;
 }
 const daily=(id:string,date:Date)=>db.dailyProgress.findUnique({where:{playerId_day:{playerId:id,day:rosterDailyDay(date)}}});
 const credits=async(id:string)=>(await db.player.findUniqueOrThrow({where:{id}})).credits;
 async function unchangedPass(id:string){expect(await db.progressionState.findUniqueOrThrow({where:{playerId:id}})).toMatchObject({points:17,dailyPoints:2,pointDay:'unchanged-day'});}
 const startInput=(e:ReturnType<typeof environment>,times=1)=>({requestId:randomUUID(),kind:'LOW',catalogVersion:e.catalog.version,times});
 const rows=(batch:any)=>batch.sessions.map((s:any)=>({sessionId:s.sessionId,revision:s.revision}));
 async function craftInput(e:ReturnType<typeof environment>,id:string,target='u09',rarity='RARE'){
  e.catalog.characters.find((u:any)=>u.id===target).rarity=rarity;
  const materials=[];for(let i=0;i<2;i++)materials.push((await db.roster60Owned.create({data:{playerId:id,speciesId:'u02',source:randomUUID()}})).id);
  const selection={resultId:target,materialIds:materials,catalogVersion:e.catalog.version},preview=await e.craft.preview(id,selection);
  return {...selection,requestId:randomUUID(),previewFingerprint:preview.previewFingerprint};
 }
 async function run(e:ReturnType<typeof environment>,id:string,rewards:unknown={xp:{}}){
  const u=await db.roster60Owned.create({data:{playerId:id,speciesId:'u01',source:randomUUID()}});
  const trip=await e.adventure.start(id,{requestId:randomUUID(),ownedIds:[u.id]});
  await db.roster60Adventure.update({where:{id:trip.adventureId},data:{pendingRewards:JSON.stringify(rewards)}});return {trip,u};
 }
 it('direct permanent grants count actual units, pay configured threshold once, expose actual balance and never award pass points',async()=>{
  const e=environment(),id=await account(e),body=startInput(e,5),result=await e.library.recruit(id,body);
  expect(result.items).toHaveLength(5);expect((await daily(id,e.clock.now))?.draws).toBe(5);
  expect((await daily(id,e.clock.now))?.rewarded.split(',')).toContain('DRAW');
  expect(result.credits).toBe(1000-result.charged+reward('DRAW'));expect(result.credits).toBe(await credits(id));
  expect(await db.auditEvent.count({where:{playerId:id,kind:'MISSION'}})).toBe(1);await unchangedPass(id);
  e.clock.now=new Date('2026-10-27T03:00:00Z');expect(await e.library.recruit(id,body)).toEqual(result);
  expect(await daily(id,e.clock.now)).toBeNull();expect(await db.dailyProgress.count({where:{playerId:id}})).toBe(1);
  await expect(e.library.recruit(id,{...body,times:1})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
 });
 it('candidate starts/rerolls never count; claim and claimAll count on KST completion day across midnight',async()=>{
  const e=environment(),id=await account(e);e.clock.now=new Date('2026-10-26T14:59:59.999Z');const before=e.clock.now;
  const batch=await e.sessions.start(id,startInput(e,3));const rerolled=await e.sessions.action(id,{requestId:randomUUID(),sessionId:batch.sessionId,revision:0,action:'reroll'});
  expect(await daily(id,before)).toBeNull();expect(e.clock.calls).toBe(0);
  e.clock.now=new Date('2026-10-26T15:00:00Z');
  await e.sessions.action(id,{requestId:randomUUID(),sessionId:batch.sessionId,revision:rerolled.revision,action:'claim'});
  const rest=rows(batch).slice(1),request={requestId:randomUUID(),sessions:rest};await e.sessions.claimAll(id,request);
  expect((await daily(id,e.clock.now))?.draws).toBe(3);expect(await daily(id,before)).toBeNull();
  const next=await e.sessions.start(id,startInput(e,2)),bulk={requestId:randomUUID(),sessions:rows(next)},result=await e.sessions.claimAll(id,bulk);
  expect((await daily(id,e.clock.now))?.draws).toBe(5);expect(result.credits).toBe(await credits(id));
  const ui=await new GameService(db,content,()=>e.clock.now).webDaily(id);expect(ui.day).toBe('2026-10-27');expect(ui.missions.find(m=>m.key==='DRAW')).toMatchObject({value:5,done:true,tickets:reward('DRAW')});
  e.clock.now=new Date('2026-10-28T03:00:00Z');expect(await e.sessions.claimAll(id,bulk)).toEqual(result);expect(await daily(id,e.clock.now)).toBeNull();
  await expect(e.sessions.claimAll(id,{...bulk,requestId:randomUUID()})).rejects.toMatchObject({code:'STALE_REVISION'});await unchangedPass(id);
 });
 it('new RARE only counts generic crafts; new EPIC unlocks the existing 에픽 mission, previews never count',async()=>{
  const e=environment(),id=await account(e),rare=await craftInput(e,id);expect(await daily(id,e.clock.now)).toBeNull();
  await e.craft.confirm(id,rare);expect(await daily(id,e.clock.now)).toMatchObject({crafts:1,craftRare:0});expect(await credits(id)).toBe(1000);
  const epic=await craftInput(e,id,'u17','EPIC'),result=await e.craft.confirm(id,epic);
  expect(await e.craft.confirm(id,epic)).toEqual(result);expect(await daily(id,e.clock.now)).toMatchObject({crafts:2,craftRare:1});
  expect(await credits(id)).toBe(1000+reward('CRAFT_RARE'));
  await e.craft.confirm(id,await craftInput(e,id));expect(await daily(id,e.clock.now)).toMatchObject({crafts:3,craftRare:1});
  expect(await credits(id)).toBe(1000+reward('CRAFT_RARE')+reward('CRAFT'));
  await unchangedPass(id);
 });
 it('failed craft receipt rolls back materials, grant, daily counter, ticket reward and mission audit together',async()=>{
  const e=environment(),id=await account(e),body=await craftInput(e,id,'u17','EPIC');
  await db.$executeRawUnsafe("CREATE TRIGGER daily_craft_receipt_fail BEFORE INSERT ON Roster60CraftReceipt BEGIN SELECT RAISE(ABORT,'daily-test-rollback'); END");
  try{await expect(e.craft.confirm(id,body)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER daily_craft_receipt_fail');}
  expect(await daily(id,e.clock.now)).toBeNull();expect(await credits(id)).toBe(1000);expect(await db.auditEvent.count({where:{playerId:id,kind:'MISSION'}})).toBe(0);
  expect(await db.roster60Owned.count({where:{id:{in:body.materialIds},status:'AVAILABLE'}})).toBe(2);
  const results=await Promise.all([e.craft.confirm(id,body),e.craft.confirm(id,body)]);expect(results[0]).toEqual(results[1]);
  expect(await daily(id,e.clock.now)).toMatchObject({crafts:1,craftRare:1});expect(await credits(id)).toBe(1000+reward('CRAFT_RARE'));
 });
 it('claimAll daily failure rolls back the whole batch; late stale rows and foreign accounts never count',async()=>{
  const e=environment(),id=await account(e),other=await account(e),batch=await e.sessions.start(id,startInput(e,3)),body={requestId:randomUUID(),sessions:rows(batch)};
  await expect(e.sessions.claimAll(other,body)).rejects.toMatchObject({code:'RECRUIT_SESSION_NOT_FOUND'});
  await expect(e.sessions.claimAll(id,{...body,sessions:body.sessions.map((s:any,i:number)=>({...s,revision:i===2?9:s.revision}))})).rejects.toMatchObject({code:'STALE_REVISION'});
  expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(1);expect(await daily(id,e.clock.now)).toBeNull();
  await db.$executeRawUnsafe("CREATE TRIGGER daily_claim_fail BEFORE INSERT ON DailyProgress BEGIN SELECT RAISE(ABORT,'daily-claim-rollback'); END");
  try{await expect(e.sessions.claimAll(id,body)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER daily_claim_fail');}
  expect((await e.sessions.info(id)).sessions).toHaveLength(3);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(1);
  expect(await db.roster60RecruitReceipt.findUnique({where:{playerId_requestId:{playerId:id,requestId:body.requestId}}})).toBeNull();
  await e.sessions.claimAll(id,body);expect((await daily(id,e.clock.now))?.draws).toBe(3);expect(await daily(other,e.clock.now)).toBeNull();
 });
 it('successful return counts after reward payment only; start/read/LOST/replay do not',async()=>{
  const e=environment(),id=await account(e),before=new Date('2026-10-26T14:59:59.999Z');e.clock.now=before;
  const {trip,u}=await run(e,id);expect(await daily(id,before)).toBeNull();expect(e.clock.calls).toBe(0);
  await e.adventure.read(id,trip.adventureId);await e.adventure.active(id);expect(await daily(id,before)).toBeNull();
  await db.roster60Adventure.update({where:{id:trip.adventureId},data:{pendingRewards:JSON.stringify({xp:{[u.id]:7},currencies:{credits:3},items:{potion:1}})}});
  e.clock.now=new Date('2026-10-26T15:00:00Z');const body={requestId:randomUUID(),adventureId:trip.adventureId,revision:trip.revision},result=await e.adventure.return(id,body);
  expect(result.status).toBe('RETURNED');expect((await daily(id,e.clock.now))?.expeditions).toBe(1);expect(await daily(id,before)).toBeNull();
  expect(await credits(id)).toBe(1003+reward('EXPEDITION'));expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).totalXp).toBe(7n);
  expect(await e.adventure.return(id,body)).toEqual(result);await e.adventure.return(id,{...body,requestId:randomUUID()});expect((await daily(id,e.clock.now))?.expeditions).toBe(1);
  const lost=await run(e,id);await db.roster60Adventure.update({where:{id:lost.trip.adventureId},data:{status:'LOST'}});
  await e.adventure.return(id,{requestId:randomUUID(),adventureId:lost.trip.adventureId,revision:0});expect((await daily(id,e.clock.now))?.expeditions).toBe(1);await unchangedPass(id);
 });
 it('failed adventure reward capacity or receipt never counts and can retry atomically',async()=>{
  const e=environment(),id=await account(e),{trip}=await run(e,id,{xp:{},items:{potion:1}}),body={requestId:randomUUID(),adventureId:trip.adventureId,revision:0};
  await db.playerItem.create({data:{playerId:id,itemId:'potion',count:20}});
  await expect(e.adventure.return(id,body)).rejects.toMatchObject({code:'REWARD_CAPACITY_REQUIRED'});expect(await daily(id,e.clock.now)).toBeNull();
  await db.playerItem.update({where:{playerId_itemId:{playerId:id,itemId:'potion'}},data:{count:19}});
  await db.$executeRawUnsafe("CREATE TRIGGER daily_return_receipt_fail BEFORE INSERT ON Roster60AdventureReceipt BEGIN SELECT RAISE(ABORT,'daily-return-rollback'); END");
  try{await expect(e.adventure.return(id,body)).rejects.toThrow();}finally{await db.$executeRawUnsafe('DROP TRIGGER daily_return_receipt_fail');}
  expect(await daily(id,e.clock.now)).toBeNull();expect(await credits(id)).toBe(1000);expect((await e.adventure.read(id,trip.adventureId)).status).toBe('ACTIVE');
  expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:id,itemId:'potion'}}})).count).toBe(19);
  await e.adventure.return(id,body);expect((await daily(id,e.clock.now))?.expeditions).toBe(1);expect(await credits(id)).toBe(1000+reward('EXPEDITION'));
 });
 it('empty and all-zero legacy returns still unlock the party without daily progress; positive xp/currency/items each count after delivery',async()=>{
  const e=environment(),id=await account(e);
  for(const allZero of [false,true]){
   const {trip,u}=await run(e,id);
   if(allZero)await db.roster60Adventure.update({where:{id:trip.adventureId},data:{pendingRewards:JSON.stringify({xp:{[u.id]:0},currencies:{credits:0,snack:0,peanut:0},items:{potion:0}})}});
   const body={requestId:randomUUID(),adventureId:trip.adventureId,revision:0},result=await e.adventure.return(id,body);
   expect(result.status).toBe('RETURNED');expect(await e.adventure.return(id,body)).toEqual(result);
   await e.adventure.return(id,{...body,requestId:randomUUID()});
   expect((await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}})).status).toBe('AVAILABLE');
   expect(await daily(id,e.clock.now)).toBeNull();expect(await credits(id)).toBe(1000);
   expect(await db.auditEvent.count({where:{playerId:id,kind:'MISSION'}})).toBe(0);expect(e.clock.calls).toBe(0);
  }
  let count=0;
  for(const kind of ['xp','currency','item']){
   const {trip,u}=await run(e,id),before=await credits(id);
   const rewards=kind==='xp'?{xp:{[u.id]:7}}:kind==='currency'?{xp:{},currencies:{credits:3}}:{xp:{},items:{potion:1}};
   await db.roster60Adventure.update({where:{id:trip.adventureId},data:{pendingRewards:JSON.stringify(rewards)}});
   const body={requestId:randomUUID(),adventureId:trip.adventureId,revision:0};await e.adventure.return(id,body);count++;
   expect((await daily(id,e.clock.now))?.expeditions).toBe(count);
   expect(await credits(id)).toBe(before+(kind==='currency'?3:0)+(count===1?reward('EXPEDITION'):0));
   const after=await db.roster60Owned.findUniqueOrThrow({where:{id:u.id}});expect(after.status).toBe('AVAILABLE');expect(after.totalXp).toBe(kind==='xp'?7n:0n);
   if(kind==='item')expect((await db.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:id,itemId:'potion'}}})).count).toBe(1);
   await e.adventure.return(id,body);await e.adventure.return(id,{...body,requestId:randomUUID()});expect((await daily(id,e.clock.now))?.expeditions).toBe(count);
  }
  expect(await db.auditEvent.count({where:{playerId:id,kind:'MISSION'}})).toBe(1);await unchangedPass(id);
 });
 it('concurrent grants plus same-request retries pay a threshold only once and preserve actual unit count',async()=>{
  const e=environment(),id=await account(e);await e.library.recruit(id,startInput(e,4));const bodies=[startInput(e),startInput(e)];
  const outcomes=await Promise.allSettled(bodies.map(body=>e.library.recruit(id,body)));
  for(let i=0;i<outcomes.length;i++)if(outcomes[i].status==='rejected')await e.library.recruit(id,bodies[i]);
  for(const body of bodies)await e.library.recruit(id,body);
  expect((await daily(id,e.clock.now))?.draws).toBe(6);expect(await db.roster60Owned.count({where:{playerId:id}})).toBe(7);
  expect(await db.auditEvent.count({where:{playerId:id,kind:'MISSION'}})).toBe(1);expect(await credits(id)).toBe(1000-12+reward('DRAW'));await unchangedPass(id);
 });
 it('existing owned units and earlier receipts are not backfilled and accounts remain isolated',async()=>{
  const e=environment(),id=await account(e),other=await account(e);for(let i=0;i<10;i++)await db.roster60Owned.create({data:{playerId:id,speciesId:'u02',source:'past-grant-'+i}});
  await e.library.collection(id);await e.sessions.info(id);await e.craft.options(id);expect(await daily(id,e.clock.now)).toBeNull();
  await e.library.recruit(id,startInput(e));expect((await daily(id,e.clock.now))?.draws).toBe(1);expect(await daily(other,e.clock.now)).toBeNull();
 });
});
