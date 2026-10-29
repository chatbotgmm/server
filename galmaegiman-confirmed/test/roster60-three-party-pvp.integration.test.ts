import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {RosterPvp} from '../src/roster60-pvp.js';
import {fighter} from '../src/roster60-battle.js';
import {loadContent} from '../src/content.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
const normalXpPolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
const partyPolicy={version:'single-active-party-3-v1',minParty:1,maxParty:3,activeSlots:1,sizeRule:'up-to-max'};

suite('서버 3마리 편성과 기존 6마리 PVP 호환 (격리 SQLite)',()=>{
 let db:PrismaClient,service:RosterPvp,now:number;
 const players:string[]=[],accountLevels=new Map<string,number>();
 const makeService=()=>new RosterPvp(db,()=>catalog,()=>policy,()=>now,()=>normalXpPolicy,async(_tx,id)=>accountLevels.get(id)??null);
 beforeAll(()=>{const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('isolated DB only');db=new PrismaClient({datasources:{db:{url}}});});
 beforeEach(()=>{now=Date.now();accountLevels.clear();service=makeService();});
 afterEach(async()=>{
  await db.roster60PvpMatch.deleteMany({where:{player0Id:{in:players}}});
  await db.roster60PvpQueue.deleteMany({where:{playerId:{in:players}}});
  await db.roster60PvpParty.deleteMany({where:{playerId:{in:players}}});
  await db.realtimeActivity.deleteMany({where:{playerId:{in:players}}});
  await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:players}}});
  await db.player.deleteMany({where:{id:{in:players}}});players.length=0;
 });
 afterAll(()=>db.$disconnect());

 async function user(count=6){
  const player=await db.player.create({data:{identity:randomUUID()}});players.push(player.id);
  await new RosterOnboarding(db,()=>catalog).start(player.id);
  const ids=(await db.roster60Owned.findMany({where:{playerId:player.id}})).map(u=>u.id);
  for(let i=ids.length;i<count;i++)ids.push((await db.roster60Owned.create({data:{playerId:player.id,speciesId:'u'+String(i+1).padStart(2,'0'),source:'TEST-three-party-'+i}})).id);
  return {id:player.id,ids};
 }
 async function rankReady(account:Awaited<ReturnType<typeof user>>){
  accountLevels.set(account.id,60);
  for(const unit of await db.roster60Owned.findMany({where:{playerId:account.id}})){
   const definition=catalog.characters.find((d:any)=>d.id===unit.speciesId)!;
   await db.roster60Owned.update({where:{id:unit.id},data:{totalXp:BigInt(definition.growth[59].totalXp)}});
  }
 }
 async function queue(account:Awaited<ReturnType<typeof user>>,mode:'normal'|'rank'='normal'){
  await service.party(account.id,account.ids.slice(0,3),mode);
  const request={requestId:randomUUID(),mode};return {request,result:await service.join(account.id,request) as any};
 }
 async function legacyRankRoom(){
  const a=await user(),b=await user();await rankReady(a);await rankReady(b);
  await queue(a,'rank');const joined=(await queue(b,'rank')).result;
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:joined.battle.battleId}}),state=JSON.parse(row.state),snapshot=JSON.parse(row.snapshot);
  // 이전 릴리스가 만든 방의 상태/핀을 재현한다. 신규 편성 API를 우회하는 제품 코드는 없다.
  for(const player of [a,b]){
   const side=row.player0Id===player.id?0:1;
   for(const unit of await db.roster60Owned.findMany({where:{playerId:player.id,id:{in:player.ids.slice(3)}}})){
    state.parties[side].push({...fighter(catalog,unit.speciesId,loadContent().economy.pvp.level,unit.id),ownedId:unit.id});
    snapshot.members.push({id:unit.id,playerId:player.id,xp:unit.totalXp.toString()});
   }
   snapshot.rankAdmission.players.find((pin:any)=>pin.playerId===player.id).ownedIds=[...player.ids];
   await db.roster60Owned.updateMany({where:{playerId:player.id,id:{in:player.ids}},data:{status:'PVP'}});
   await db.roster60PvpParty.update({where:{playerId:player.id},data:{ownedIds:JSON.stringify(player.ids)}});
  }
  await db.roster60PvpMatch.update({where:{id:row.id},data:{state:JSON.stringify(state),snapshot:JSON.stringify(snapshot)}});
  return {a,b,row};
 }

 it('기존 최소1 정책을 유지하고 1·2·3만 저장하며 소유 개체 6마리는 보존한다',async()=>{
  const a=await user();
  for(const count of [1,2,3])expect(await service.party(a.id,a.ids.slice(0,count))).toMatchObject({partyIds:a.ids.slice(0,count)});
  for(const count of [0,4,6])await expect(service.party(a.id,a.ids.slice(0,count))).rejects.toMatchObject({code:'INVALID_PARTY'});
  expect(await service.options(a.id)).toMatchObject({maxParty:3,minParty:1,partyPolicy,partyIds:a.ids.slice(0,3),partySelectionRequired:false,partyCondition:null});
  expect(await db.roster60Owned.count({where:{playerId:a.id}})).toBe(6);
  expect(JSON.parse((await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}})).ownedIds)).toEqual(a.ids.slice(0,3));
 });

 it('개체 중복·타계정·잠금·랭크 계정과 개체 레벨 제한을 유지한다',async()=>{
  const a=await user(),b=await user(1);
  await expect(service.party(a.id,[a.ids[0],a.ids[0]])).rejects.toMatchObject({code:'INVALID_PARTY'});
  await expect(service.party(a.id,[a.ids[0],a.ids[1],b.ids[0]])).rejects.toMatchObject({code:'UNIT_NOT_FOUND'});
  await db.roster60Owned.update({where:{id:a.ids[2]},data:{locked:true}});
  await expect(service.party(a.id,a.ids.slice(0,3))).rejects.toMatchObject({code:'UNIT_UNAVAILABLE'});
  await db.roster60Owned.update({where:{id:a.ids[2]},data:{locked:false}});
  accountLevels.set(a.id,59);
  await expect(service.party(a.id,a.ids.slice(0,3),'rank')).rejects.toMatchObject({code:'RANK_ACCOUNT_LEVEL_REQUIRED'});
  accountLevels.set(a.id,60);
  await expect(service.party(a.id,a.ids.slice(0,3),'rank')).rejects.toMatchObject({code:'RANK_UNIT_LEVEL_REQUIRED'});
  await rankReady(a);await service.party(a.id,a.ids.slice(0,3),'rank');
  await db.roster60Owned.update({where:{id:a.ids[2]},data:{totalXp:0n}});
  await expect(service.join(a.id,{requestId:randomUUID(),mode:'rank'})).rejects.toMatchObject({code:'RANK_UNIT_LEVEL_REQUIRED'});
  expect(await db.roster60Owned.count({where:{playerId:a.id,status:'PVP_QUEUE'}})).toBe(0);
 });

 it('기존 6마리 저장 편성을 그대로 응답하고 새 전투 전에 재선택을 요구한다',async()=>{
  const a=await user();await db.roster60PvpParty.create({data:{playerId:a.id,ownedIds:JSON.stringify(a.ids)}});
  expect(await service.options(a.id)).toMatchObject({partyIds:a.ids,partyPolicy,partySelectionRequired:true,partyCondition:'PARTY_RESELECTION_REQUIRED'});
  for(const mode of ['normal','rank'] as const)await expect(service.join(a.id,{requestId:randomUUID(),mode})).rejects.toMatchObject({code:'PARTY_RESELECTION_REQUIRED'});
  expect(JSON.parse((await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}})).ownedIds)).toEqual(a.ids);
  expect(await db.roster60Owned.count({where:{playerId:a.id,status:'AVAILABLE'}})).toBe(6);
  expect(await db.roster60PvpQueue.count({where:{playerId:a.id}})).toBe(0);
  await service.party(a.id,a.ids.slice(1,4));
  expect(await service.options(a.id)).toMatchObject({partyIds:a.ids.slice(1,4),partySelectionRequired:false,partyCondition:null});
 });

 it('배포 전 6마리 대기는 사람·AI 매칭에서 제외하고 취소로 6마리를 모두 돌려준다',async()=>{
  const legacy=await user(),valid=await user();await queue(legacy);
  // 제품 데이터 이관이 아니라 배포 직전 이미 있던 큐를 재현하는 격리 시험 fixture다.
  await db.roster60PvpParty.update({where:{playerId:legacy.id},data:{ownedIds:JSON.stringify(legacy.ids)}});
  await db.roster60PvpQueue.update({where:{playerId:legacy.id},data:{ownedIds:JSON.stringify(legacy.ids)}});
  await db.roster60Owned.updateMany({where:{playerId:legacy.id,id:{in:legacy.ids}},data:{status:'PVP_QUEUE'}});
  expect(await service.current(legacy.id)).toMatchObject({status:'waiting',partySelectionRequired:true,partyCondition:'PARTY_RESELECTION_REQUIRED'});
  expect((await queue(valid)).result.status).toBe('waiting');
  now+=30_000;await service.tick();
  expect(await service.current(legacy.id)).toMatchObject({status:'waiting',partySelectionRequired:true,partyCondition:'PARTY_RESELECTION_REQUIRED'});
  expect(await db.roster60PvpMatch.count({where:{OR:[{player0Id:legacy.id},{player1Id:legacy.id}]}})).toBe(0);
  expect((await service.current(valid.id) as any).battle).toMatchObject({opponentKind:'AI'});
  expect((await service.cancel(legacy.id,{requestId:randomUUID()}) as any).status).toBe('idle');
  expect(await db.roster60Owned.count({where:{playerId:legacy.id,status:'AVAILABLE'}})).toBe(6);
  expect(JSON.parse((await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:legacy.id}})).ownedIds)).toEqual(legacy.ids);
 });

 it('3마리 일반 AI 대체는 양측 3마리와 기존 30초·멱등 규칙을 유지한다',async()=>{
  const a=await user(),queued=await queue(a);expect(queued.result.status).toBe('waiting');
  now+=29_999;expect((await service.current(a.id)).status).toBe('waiting');
  now++;const current:any=await service.current(a.id),battle=current.battle;
  expect(battle).toMatchObject({opponentKind:'AI',mode:'normal',status:'ACCEPTING'});
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:battle.battleId}}),state=JSON.parse(row.state),snapshot=JSON.parse(row.snapshot);
  expect(state.parties.map((p:any[])=>p.length)).toEqual([3,3]);
  expect(snapshot.members.map((m:any)=>m.id)).toEqual(a.ids.slice(0,3));
  expect(await db.roster60Owned.count({where:{playerId:a.id,status:'AVAILABLE'}})).toBe(3);
  expect(await service.join(a.id,queued.request)).toEqual(queued.result);
  const accepted:any=await service.accept(a.id,{requestId:randomUUID(),battleId:row.id,revision:0,accept:true});
  const request={requestId:randomUUID(),battleId:row.id,revision:accepted.revision,action:{kind:'focus' as const}};
  const result:any=await service.action(a.id,request);expect(result.state.turn).toBe(1);
  expect(await service.action(a.id,request)).toEqual(result);
  await expect(service.action(a.id,{...request,action:{kind:'attack'}})).rejects.toMatchObject({code:'REQUEST_ID_REUSED'});
  expect(await db.roster60PvpMatch.count({where:{player0Id:a.id}})).toBe(1);
 });

 it('3마리 사람 일반전과 랭크 매칭에서 선택 인원과 랭크 정규화를 보존한다',async()=>{
  for(const mode of ['normal','rank'] as const){
   const a=await user(),b=await user();if(mode==='rank'){await rankReady(a);await rankReady(b);}
   await queue(a,mode);const joined=(await queue(b,mode)).result,battle=joined.battle;
   expect(battle).toMatchObject({opponentKind:'HUMAN',mode,status:'ACCEPTING'});
   const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:battle.battleId}}),state=JSON.parse(row.state);
   expect(state.parties.map((p:any[])=>p.length)).toEqual([3,3]);
   if(mode==='rank')for(const party of state.parties)for(const unit of party)expect(unit.level).toBe(loadContent().economy.pvp.level);
  }
 });

 it('기존 6마리 랭크 수락·재시작 복귀·턴 명령·6번째 교체를 축소 없이 처리한다',async()=>{
  const {a,b,row}=await legacyRankRoom();
  await service.accept(a.id,{requestId:randomUUID(),battleId:row.id,revision:0,accept:true});
  let battle:any=await service.accept(b.id,{requestId:randomUUID(),battleId:row.id,revision:0,accept:true});expect(battle.status).toBe('ACTIVE');
  service=makeService();service.notePresenceRestart();
  battle=(await service.current(a.id) as any).battle;
  expect(battle.state.reserves).toHaveLength(5);expect(battle.status).toBe('ACTIVE');
  expect((await service.join(a.id,{requestId:randomUUID(),mode:'rank'}) as any).battle.battleId).toBe(row.id);
  await service.action(a.id,{requestId:randomUUID(),battleId:row.id,revision:battle.revision,action:{kind:'focus'}});
  battle=await service.action(b.id,{requestId:randomUUID(),battleId:row.id,revision:battle.revision,action:{kind:'focus'}}) as any;
  expect(battle.state.turn).toBe(1);
  await service.action(a.id,{requestId:randomUUID(),battleId:row.id,revision:battle.revision,action:{kind:'switch',targetOwnedId:a.ids[5]}});
  battle=await service.action(b.id,{requestId:randomUUID(),battleId:row.id,revision:battle.revision,action:{kind:'focus'}}) as any;
  const resumed:any=(await service.current(a.id) as any).battle;
  expect(resumed.state.fighters[0].ownedId).toBe(a.ids[5]);expect(resumed.state.reserves).toHaveLength(5);
  const stored=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:row.id}});
  expect(JSON.parse(stored.state).parties.map((p:any[])=>p.length)).toEqual([6,6]);
  expect(JSON.parse(stored.snapshot).members).toHaveLength(12);
 });

 it('기존 6마리 수락방의 거절·시간초과는 새 6마리 큐를 자동 생성하지 않는다',async()=>{
  for(const reason of ['decline','timeout'] as const){
   const {a,b,row}=await legacyRankRoom();
   await service.accept(a.id,{requestId:randomUUID(),battleId:row.id,revision:0,accept:true});
   if(reason==='decline')await service.accept(b.id,{requestId:randomUUID(),battleId:row.id,revision:0,accept:false});
   else{now+=10_001;await service.tick();}
   expect(await db.roster60PvpQueue.count({where:{playerId:{in:[a.id,b.id]}}})).toBe(0);
   const current:any=await service.current(a.id);
   expect(current.battle).toMatchObject({requeued:false,settlement:{partySelectionRequired:true,partyCondition:'PARTY_RESELECTION_REQUIRED'}});
   expect(await db.roster60Owned.count({where:{playerId:{in:[a.id,b.id]},status:'AVAILABLE'}})).toBe(12);
   expect(JSON.parse((await db.roster60PvpParty.findUniqueOrThrow({where:{playerId:a.id}})).ownedIds)).toEqual(a.ids);
  }
 });
});
