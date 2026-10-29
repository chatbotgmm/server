import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {createHash,randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {fighter,RosterBattle,type BattlePolicy} from '../src/roster60-battle.js';
import {RosterAdventure} from '../src/roster60-adventure.js';
import type {RosterCatalog} from '../src/roster60-onboarding.js';
import {roster60PartyPolicy} from '../src/roster60-party-policy.js';
import {RosterStory} from '../src/story/service.js';
import {initialStoryProgress,type StoryContent} from '../src/story/types.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const fingerprint=(v:unknown)=>createHash('sha256').update(JSON.stringify(v)).digest('hex');

suite('3마리 신규 PVE 편성과 기존 6마리 진행 호환',()=>{
 let db:PrismaClient,battle:RosterBattle,adventure:RosterAdventure;
 const players:string[]=[];
 const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8')) as RosterCatalog;
 const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8')) as BattlePolicy;
 const content:StoryContent={version:'three-party-story-test',productionBalanceApproved:false,chapters:[],residents:[],
  scenes:[{id:'intro',background:'test',lines:[{speakerId:'test',text:'출발'}]},{id:'return',background:'test',lines:[{speakerId:'test',text:'귀환'}]}],
  quests:[{id:'Q',kind:'main',chapter:1,recommendedLevel:[1,1],title:'시험',unlockAfter:[],acceptScene:'intro',returnScene:'return',objective:{kind:'adventure_win',encounterIds:['story-adventure'],wins:1}}],
  encounters:[{id:'story-adventure',speciesId:'u07',level:1,kind:'black',mode:'adventure',winXp:10,lossXp:0}],
  policy:{darkGangPerWin:1,maxBalance:100,mainLifetimeGates:{},thresholdsAreTestValues:true}};
 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('격리된 시험 DB만 허용');
  db=new PrismaClient({datasources:{db:{url}}});
  const enemy=catalog.characters.find(u=>u.id==='u07')! as any;enemy.combatGrowth[0].hp=1;enemy.levelOneStats.hp=1;enemy.attack.power=0;enemy.skills=[];
  policy.encounters=[{id:'duel',speciesId:'u07',level:1,tutorial:false,mode:'duel',winXp:10,lossXp:0},{id:'tutorial',speciesId:'u07',level:1,tutorial:true,mode:'story',winXp:10,lossXp:0},{id:'adventure',speciesId:'u07',level:1,tutorial:false,mode:'adventure',winXp:10,lossXp:0}];
  battle=new RosterBattle(db,()=>catalog,()=>policy);adventure=new RosterAdventure(db,()=>catalog,fighter);
 });
 afterAll(async()=>{await db.dailyProgress.deleteMany({where:{playerId:{in:players}}});await db.auditEvent.deleteMany({where:{playerId:{in:players}}});await db.player.deleteMany({where:{id:{in:players}}});await db.$disconnect();});
 async function user(count=6){
  const p=await db.player.create({data:{identity:randomUUID()}});players.push(p.id);
  const units=[];for(let i=0;i<count;i++)units.push(await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',source:randomUUID()}}));
  return {id:p.id,ids:units.map(u=>u.id)};
 }
 const create=(player:string,ids:string[],encounterId='duel',adventureId?:string)=>battle.create(player,{requestId:randomUUID(),ownedId:ids[0],reserveIds:ids.slice(1),encounterId,adventureId}) as Promise<any>;
 const command=(player:string,v:any,action:any)=>battle.action(player,{requestId:randomUUID(),battleId:v.battleId,revision:v.revision,action}) as Promise<any>;
 async function legacyRun(player:string,ids:string[]){
  const current=await adventure.start(player,{requestId:randomUUID(),ownedIds:ids.slice(0,3)}) as any;
  const row=await db.roster60Adventure.findUniqueOrThrow({where:{id:current.adventureId}}),state=JSON.parse(row.state);
  for(const id of ids.slice(3)){
   state.party.push({...fighter(catalog,'u01',1,'self'),ownedId:id});
   state.members.push({ownedId:id,startingXp:'0',growth:catalog.characters.find(u=>u.id==='u01')!.growth,reincarnation:0});
   await db.roster60Owned.update({where:{id},data:{status:'ADVENTURE'}});
  }
  await db.roster60Adventure.update({where:{id:row.id},data:{state:JSON.stringify(state)}});
  return adventure.read(player,row.id) as Promise<any>;
 }
 async function acceptStory(player:string,adventureId?:string){
  const progress=initialStoryProgress();progress.accepted.Q={token:randomUUID(),wins:0,battles:[],adventureId};
  await db.roster60StoryAccount.create({data:{playerId:player,contentVersion:content.version,state:JSON.stringify(progress)}});
  return new RosterStory(db,()=>catalog,()=>policy,content);
 }

 it('서버 options가 최대3·최소1·단일출전 모드 계약을 제공한다',async()=>{
  const u=await user(3),options=await battle.options(u.id);
  expect(options).toMatchObject({minParty:1,maxParty:3,partyPolicy:roster60PartyPolicy});
  expect(options.encounters.every(e=>e.partyPolicy===roster60PartyPolicy)).toBe(true);
 });
 it.each([1,2,3])('기존 최소편성 정책을 유지하여 신규 %i마리 전투와 모험을 허용한다',async count=>{
  const u=await user(count),v=await create(u.id,u.ids);
  expect(v.state.reserves).toHaveLength(count-1);expect(v.state.fighters).toHaveLength(2);
  expect(await db.roster60Owned.count({where:{playerId:u.id,status:'BATTLE'}})).toBe(count);
  const a=await user(count),run=await adventure.start(a.id,{requestId:randomUUID(),ownedIds:a.ids}) as any;
  expect(run.party).toHaveLength(count);
 });
 it.each([0,4,6])('신규 %i마리 모험 편성을 거절하고 개체 상태를 보존한다',async count=>{
  const u=await user(count);
  await expect(adventure.start(u.id,{requestId:randomUUID(),ownedIds:u.ids})).rejects.toMatchObject({code:'INVALID_PARTY'});
  expect(await db.roster60Adventure.count({where:{playerId:u.id}})).toBe(0);
  expect(await db.roster60Owned.count({where:{playerId:u.id,status:'AVAILABLE'}})).toBe(count);
 });
 it.each([4,6])('신규 %i마리 연습·튜토리얼 전투에는 예외를 만들지 않는다',async count=>{
  const u=await user(count);
  for(const encounter of ['duel','tutorial'])await expect(create(u.id,u.ids,encounter)).rejects.toMatchObject({code:'INVALID_PARTY'});
  expect(await db.roster60Battle.count({where:{playerId:u.id}})).toBe(0);
  expect(await db.roster60Owned.count({where:{playerId:u.id,status:'AVAILABLE'}})).toBe(count);
 });
 it('중복·타계정·잠금 개체의 신규 편성 검증을 유지한다',async()=>{
  const a=await user(3),b=await user(1);
  await expect(create(a.id,[a.ids[0],a.ids[0]])).rejects.toMatchObject({code:'INVALID_PARTY'});
  await expect(create(a.id,[a.ids[0],b.ids[0]])).rejects.toMatchObject({code:'UNIT_NOT_FOUND'});
  await db.roster60Owned.update({where:{id:a.ids[1]},data:{locked:true}});
  await expect(create(a.id,[a.ids[0],a.ids[1]])).rejects.toMatchObject({code:'UNIT_UNAVAILABLE'});
  expect(await db.roster60Owned.count({where:{playerId:a.id,status:'AVAILABLE'}})).toBe(3);
 });
 it('기존 6마리 전투 스냅샷 읽기·명령·정산과 생성 영수증 재전송을 보존한다',async()=>{
  const u=await user(6);let v=await create(u.id,u.ids.slice(0,3));
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:v.battleId}}),state=JSON.parse(row.state),snapshot=JSON.parse(row.snapshot);
  for(const id of u.ids.slice(3)){
   state.reserves.push({...fighter(catalog,'u01',1,'self'),ownedId:id});snapshot.members.push({ownedId:id,startingXp:'0'});
   await db.roster60Owned.update({where:{id},data:{status:'BATTLE'}});
  }
  await db.roster60Battle.update({where:{id:row.id},data:{state:JSON.stringify(state),snapshot:JSON.stringify(snapshot)}});
  v=await battle.read(u.id,row.id);expect(v.state.reserves).toHaveLength(5);expect((await battle.active(u.id)).battles[0]).toEqual(v);
  const request={requestId:randomUUID(),ownedId:u.ids[0],reserveIds:u.ids.slice(1),encounterId:'duel'};
  await db.roster60BattleReceipt.create({data:{playerId:u.id,requestId:request.requestId,fingerprint:fingerprint(['create',request.ownedId,'duel',request.reserveIds,null]),result:JSON.stringify(v)}});
  expect(await battle.create(u.id,request)).toEqual(v);
  v=await command(u.id,v,{kind:'switch',targetOwnedId:u.ids[5]});expect(v.state.fighters[0].ownedId).toBe(u.ids[5]);
  v=await command(u.id,v,{kind:'escape'});expect(v.status).toBe('FINISHED');
  const settled=await battle.settle(u.id,{requestId:randomUUID(),battleId:v.battleId,revision:v.revision}) as any;
  expect(settled.settlement.xpAwarded).toBe(0);expect(await db.roster60Owned.count({where:{playerId:u.id,status:'AVAILABLE'}})).toBe(6);
 });
 it('기존 6마리 모험은 정확한 저장편성으로 다음 전투와 정상귀환을 완료한다',async()=>{
  const u=await user(7),ids=u.ids.slice(0,6),run=await legacyRun(u.id,ids);
  expect((await adventure.active(u.id)).adventures[0].party).toHaveLength(6);
  for(const wrong of [ids.slice(0,3),[...ids.slice(0,5),u.ids[6]]])await expect(create(u.id,wrong,'adventure',run.adventureId)).rejects.toMatchObject({code:'INVALID_PARTY'});
  let v=await create(u.id,ids,'adventure',run.adventureId);expect(v.state.reserves).toHaveLength(5);
  v=await command(u.id,v,{kind:'attack'});expect(v.status).toBe('FINISHED');
  await battle.settle(u.id,{requestId:randomUUID(),battleId:v.battleId,revision:v.revision});
  const ready=await adventure.read(u.id,run.adventureId) as any;
  const returned=await adventure.return(u.id,{requestId:randomUUID(),adventureId:run.adventureId,revision:ready.revision}) as any;
  expect(returned.status).toBe('RETURNED');expect(returned.party).toHaveLength(6);
  expect(await db.roster60Owned.count({where:{id:{in:ids},status:'AVAILABLE'}})).toBe(6);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:ids[0]}})).totalXp).toBe(10n);
  expect((await db.roster60Owned.findUniqueOrThrow({where:{id:ids[5]}})).totalXp).toBe(5n);
 });
 it('기존 6마리 모험 시작 영수증은 신규 최대수 검증보다 먼저 재전송한다',async()=>{
  const u=await user(6),run=await legacyRun(u.id,u.ids),request={requestId:randomUUID(),ownedIds:u.ids};
  await db.roster60AdventureReceipt.create({data:{playerId:u.id,requestId:request.requestId,fingerprint:fingerprint(['start',u.ids]),result:JSON.stringify(run)}});
  expect(await adventure.start(u.id,request)).toEqual(run);
 });
 it('신규 스토리 6마리 출발은 의도·모험·개체 상태를 바꾸기 전에 거절한다',async()=>{
  const u=await user(6),story=await acceptStory(u.id),before=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:u.id}});
  await expect(story.createBattle(u.id,{requestId:randomUUID(),questId:'Q',ownedIds:u.ids})).rejects.toMatchObject({code:'INVALID_PARTY'});
  expect(await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:u.id}})).toEqual(before);
  expect(await db.roster60Adventure.count({where:{playerId:u.id}})).toBe(0);
  expect(await db.roster60StoryReceipt.count({where:{playerId:u.id}})).toBe(0);
  expect(await db.roster60Owned.count({where:{playerId:u.id,status:'AVAILABLE'}})).toBe(6);
  const v=await story.createBattle(u.id,{requestId:randomUUID(),questId:'Q',ownedIds:u.ids.slice(0,3)}) as any;expect(v.state.reserves).toHaveLength(2);
 });
 it('기존 스토리 6마리 모험의 다음 전투·생성 재전송을 보존한다',async()=>{
  const u=await user(6),run=await legacyRun(u.id,u.ids),story=await acceptStory(u.id,run.adventureId);
  const request={requestId:randomUUID(),questId:'Q',ownedIds:u.ids,adventureId:run.adventureId};
  const v=await story.createBattle(u.id,request) as any;expect(v.state.reserves).toHaveLength(5);
  expect(await story.createBattle(u.id,request)).toEqual(v);
 });
 it('기존 6마리 모험 시작 후 스토리 링크 저장이 실패해도 같은 출발 요청을 복구한다',async()=>{
  const u=await user(6),run=await legacyRun(u.id,u.ids),story=await acceptStory(u.id);
  const request={requestId:randomUUID(),questId:'Q',ownedIds:u.ids},row=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:u.id}}),progress=JSON.parse(row.state);
  progress.departure={requestId:request.requestId,questId:'Q',encounterId:'story-adventure',ownedIds:u.ids};
  await db.roster60StoryAccount.update({where:{playerId:u.id},data:{state:JSON.stringify(progress)}});
  await db.roster60StoryReceipt.create({data:{playerId:u.id,requestId:request.requestId,fingerprint:fingerprint(['battle','Q',null,u.ids,null]),result:JSON.stringify({pending:true,contentVersion:content.version,questId:'Q',acceptToken:progress.accepted.Q.token,encounterId:'story-adventure'})}});
  await db.roster60AdventureReceipt.create({data:{playerId:u.id,requestId:'story-'+fingerprint([request.requestId,'adventure']),fingerprint:fingerprint(['start',u.ids]),result:JSON.stringify(run)}});
  const v=await story.createBattle(u.id,request) as any;expect(v.state.reserves).toHaveLength(5);expect(v.state.adventureId).toBe(run.adventureId);
  expect(await db.roster60Adventure.count({where:{playerId:u.id}})).toBe(1);
  expect(await story.createBattle(u.id,request)).toEqual(v);
 });
});
