import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient,type Prisma} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {applyLoanLevel,bindLoanLevelRecipient,checkpointLoanLevels,loanLevelFloors} from '../src/starter-loan-levels.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {progressionReceipt} from '../src/progression.js';
import {pruneExpiredLoanReferences} from '../src/starter-loans.js';
const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('임시 레벨 계승 SQLite transaction 경계',()=>{
 let db:PrismaClient;const playerIds=new Set<string>();
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;
  if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\//.test(url))throw new Error('명시된 /tmp 격리 테스트 DB만 허용');
  db=new PrismaClient({datasources:{db:{url}}});
  for(const [i,id] of ['D2','D4'].entries())await db.characterDefinition.upsert({where:{id},update:{},create:{id,name:id,rarity:'LIMITED',position:-9900-i,quote:'test',imageUrl:''}});
 });
 afterAll(async()=>{
  if(!db)return;
  try{const service=new GameService(db,loadContent());for(const id of playerIds)if(await db.player.findUnique({where:{id}}))await service.webDeleteAccount(id);}
  finally{await db.$disconnect();}
 });
 const transaction=<T>(run:(tx:Prisma.TransactionClient)=>Promise<T>)=>db.$transaction(run,{maxWait:10000,timeout:10000});
 async function fixture(){
  const p=await db.player.create({data:{identity:`loan-level-test:${randomUUID()}`}});playerIds.add(p.id);
  const source=await db.ownedCharacter.create({data:{playerId:p.id,characterId:'D2',obtainedVia:'TEMPORARY_STARTER',obtainedAt:new Date(100),loanExpiresAt:new Date(500)}});
  const grant=await db.starterLoanGrant.create({data:{playerId:p.id,selectedIds:['D2'],ownedIds:[source.id],grantedAt:new Date(100),expiresAt:new Date(500)}});
  await db.starterLoanLevel.create({data:{loanOwnedId:source.id,playerId:p.id,characterId:'D2',grantId:grant.id,createdAt:new Date(100),updatedAt:new Date(100)}});
  const permanent=(characterId='D2')=>db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST_PERMANENT',obtainedAt:new Date(700)}});
  return {p,source,permanent};
 }
 it('만료 후 같은 종 첫 영구 개체만 연결하고 높은 영구 레벨/XP는 유지',async()=>{
  const {p,source,permanent}=await fixture();const wrong=await permanent('D4');
  await transaction(tx=>bindLoanLevelRecipient(tx,p.id,wrong,new Date(700)));
  await transaction(tx=>checkpointLoanLevels(tx,p.id,'ended-after-expiry',[{ownedId:source.id,level:22}],60,new Date(600)));
  const first=await permanent(),second=await permanent();
  await transaction(async tx=>{await bindLoanLevelRecipient(tx,p.id,first,new Date(700));await bindLoanLevelRecipient(tx,p.id,second,new Date(800));await bindLoanLevelRecipient(tx,p.id,first,new Date(800));});
  const floors=await loanLevelFloors(db,p.id,[first.id,second.id,wrong.id]);
  expect(floors.get(first.id)).toBe(22);expect(floors.has(second.id)).toBe(false);expect(floors.has(wrong.id)).toBe(false);
  expect(applyLoanLevel({level:1,xp:10,stage:0},floors.get(first.id)!,60)).toEqual({level:22,xp:0,stage:0});
  expect(applyLoanLevel({level:40,xp:19},floors.get(first.id)!,60)).toEqual({level:40,xp:19});
  expect(await db.auditEvent.count({where:{playerId:p.id,kind:'STARTER_LOAN_LEVEL_BIND'}})).toBe(1);
 });
 it('유효기간 중 먼저 얻은 영구본에 연결하고 이후 훈련도 같은 recipient만 상승',async()=>{
  const {p,source,permanent}=await fixture();
  const early=await db.ownedCharacter.create({data:{playerId:p.id,characterId:'D2',obtainedVia:'TEST_PERMANENT',obtainedAt:new Date(200)}});
  await transaction(tx=>bindLoanLevelRecipient(tx,p.id,early,new Date(200)));
  await transaction(tx=>checkpointLoanLevels(tx,p.id,'active-training',[{ownedId:source.id,level:15}],60,new Date(400)));
  expect((await loanLevelFloors(db,p.id,[early.id])).get(early.id)).toBe(15);
  const later=await permanent();await transaction(tx=>bindLoanLevelRecipient(tx,p.id,later,new Date(700)));
  await transaction(tx=>checkpointLoanLevels(tx,p.id,'late-settlement',[{ownedId:source.id,level:20}],60,new Date(800)));
  const floors=await loanLevelFloors(db,p.id,[early.id,later.id]);expect(floors.get(early.id)).toBe(20);expect(floors.has(later.id)).toBe(false);
 });
 it('동시 첫 recipient CAS는 한 개체에만 연결',async()=>{
  const {p,source,permanent}=await fixture();const copies=await Promise.all([permanent(),permanent()]);
  await Promise.all(copies.map(owned=>transaction(tx=>bindLoanLevelRecipient(tx,p.id,owned,new Date(700)))));
  const row=await db.starterLoanLevel.findUniqueOrThrow({where:{loanOwnedId:source.id}});
  expect(copies.map(x=>x.id)).toContain(row.recipientOwnedId);
  expect(await db.auditEvent.count({where:{playerId:p.id,kind:'STARTER_LOAN_LEVEL_BIND'}})).toBe(1);
 });
 it('동시 checkpoint는 최고 레벨 유지; 같은 checkpoint 재시도는 추가 audit 없음',async()=>{
  const {p,source}=await fixture();
  await Promise.all([12,35,22,35].map(level=>transaction(tx=>checkpointLoanLevels(tx,p.id,`battle-${level}`,[{ownedId:source.id,level}],60,new Date(400)))));
  expect((await db.starterLoanLevel.findUniqueOrThrow({where:{loanOwnedId:source.id}})).highestLevel).toBe(35);
  const count=await db.auditEvent.count({where:{playerId:p.id}});
  await transaction(tx=>checkpointLoanLevels(tx,p.id,'battle-35',[{ownedId:source.id,level:35}],60,new Date(600)));
  expect(await db.auditEvent.count({where:{playerId:p.id}})).toBe(count);
 });
 it('실제 progression receipt 재실행/충돌로 archive 추가 성장 불가',async()=>{
  const {p,source}=await fixture();
  const settle=(level:number,payload:unknown={session:'receipt-battle'})=>transaction(tx=>progressionReceipt(tx,p,'receipt-battle-1','NATIVE_BATTLE',payload,async()=>{await checkpointLoanLevels(tx,p.id,'receipt-battle',[{ownedId:source.id,level}],60,new Date(600));return {ok:true};}));
  await settle(24);await settle(50);
  await expect(settle(55,{session:'different'})).rejects.toThrow();
  expect((await db.starterLoanLevel.findUniqueOrThrow({where:{loanOwnedId:source.id}})).highestLevel).toBe(24);
  expect(await db.auditEvent.count({where:{playerId:p.id,kind:'STARTER_LOAN_LEVEL_CHECKPOINT'}})).toBe(1);
 });
 it('recipient 생성·연결·checkpoint·audit 모두 transaction rollback',async()=>{
  const {p,source}=await fixture();
  await expect(transaction(async tx=>{
   const next=await tx.ownedCharacter.create({data:{playerId:p.id,characterId:'D2',obtainedVia:'TEST_PERMANENT',obtainedAt:new Date(600)}});
   await bindLoanLevelRecipient(tx,p.id,next,new Date(600));await checkpointLoanLevels(tx,p.id,'rollback',[{ownedId:source.id,level:30}],60,new Date(600));throw new Error('rollback marker');
  })).rejects.toThrow('rollback marker');
  expect(await db.ownedCharacter.count({where:{playerId:p.id}})).toBe(1);
  expect(await db.starterLoanLevel.findUniqueOrThrow({where:{loanOwnedId:source.id}})).toMatchObject({highestLevel:1,recipientOwnedId:null});
  expect(await db.auditEvent.count({where:{playerId:p.id}})).toBe(0);
 });
 it('만료 StorySave 정리 이후 활성전투 checkpoint 및 다음 영구 roster floor 유지',async()=>{
  const {p,source,permanent}=await fixture();const key=`o${source.id}`;
  const captured={level:9,xp:15};
  await db.storySave.create({data:{playerId:p.id,data:JSON.stringify({units:{[key]:captured},team:[key],hp:{}})}});
  await transaction(tx=>pruneExpiredLoanReferences(tx,p,new Date(500)));
  expect(JSON.parse((await db.storySave.findUniqueOrThrow({where:{playerId:p.id}})).data).units).toEqual({});
  await transaction(tx=>checkpointLoanLevels(tx,p.id,'active-ended-late',[{ownedId:source.id,level:14}],60,new Date(550)));
  const next=await permanent();await transaction(tx=>bindLoanLevelRecipient(tx,p.id,next,new Date(700)));
  const floors=await loanLevelFloors(db,p.id,[next.id]);expect(applyLoanLevel({level:1,xp:0},floors.get(next.id)!,60).level).toBe(14);expect(captured).toEqual({level:9,xp:15});
  expect((await loanLevelFloors(db,'another-owner',[next.id])).size).toBe(0);
 });
 it('실제 webDeleteAccount가 archive/receipt/source/recipient를 함께 지우고 다른 소유자는 보존',async()=>{
  const {p,source,permanent}=await fixture();const other=await fixture();const next=await permanent();
  await transaction(async tx=>{await checkpointLoanLevels(tx,p.id,'account-before-delete',[{ownedId:source.id,level:17}],60,new Date(600));await bindLoanLevelRecipient(tx,p.id,next,new Date(700));});
  const service=new GameService(db,loadContent(),()=>new Date(800));
  expect(await service.webDeleteAccount(p.id)).toBe(true);
  expect(await db.player.findUnique({where:{id:p.id}})).toBeNull();
  expect(await db.starterLoanLevel.count({where:{playerId:p.id}})).toBe(0);
  expect(await db.starterLoanGrant.count({where:{playerId:p.id}})).toBe(0);
  expect(await db.ownedCharacter.count({where:{playerId:p.id}})).toBe(0);
  expect(await db.auditEvent.count({where:{playerId:p.id}})).toBe(0);
  expect(await db.starterLoanLevel.count({where:{playerId:other.p.id}})).toBe(1);
 });

});
