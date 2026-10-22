import {createBattleService} from '../src/battle/service-factory.js';
import {beforeAll,afterAll,beforeEach,describe,it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {GameService} from '../src/game.js';
import {loadContent,topTiers} from '../src/content.js';
import {awardPlayPoints,progressionConfig} from '../src/progression.js';
import {loadBattleRules} from '../src/battle/load.js';
import {statsOf,slotSkills,learnSkill,learnOffers,type UnitRecord} from '../src/battle/growth.js';
import {createBattle,rerollAugment} from '../src/battle/engine.js';
import {registerNative} from '../src/native.js';
import Fastify from 'fastify';
import {RealtimePvpService} from '../src/pvp/service.js';
const C=loadContent(),R=loadBattleRules();
describe('진행 보상과 환생: 새 임시 SQLite 원자성',()=>{
 let db:PrismaClient,db2:PrismaClient,dir:string,game:GameService,now:Date,p:{id:string};
 beforeAll(async()=>{dir=mkdtempSync(join(tmpdir(),'progression-test-'));const url=`file:${join(dir,'isolated.db')}`;writeFileSync(join(dir,'isolated.db'),'');execFileSync(process.execPath,['node_modules/prisma/build/index.js','db','push','--skip-generate'],{env:{...process.env,DATABASE_URL:url},stdio:'pipe'});db=new PrismaClient({datasources:{db:{url}}});db2=new PrismaClient({datasources:{db:{url}}});for(const c of C.characters)await db.characterDefinition.create({data:{id:c.id,name:c.name,rarity:c.rarity,position:c.position,quote:c.quote,imageUrl:c.imageUrl}});},30000);
 beforeEach(async()=>{now=new Date('2026-10-19T00:00:00Z');p=await db.player.create({data:{identity:randomUUID(),tutorial:5}});game=new GameService(db,C,()=>now);});
 afterAll(async()=>{await db.$disconnect();await db2.$disconnect();rmSync(dir,{recursive:true,force:true});});
 const own=(characterId='D1',data:Record<string,unknown>={})=>db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEST',...data}});
 const key=(o:{id:number})=>`o${o.id}`;
 it('같은 요청 12회 동시 환생은 정확히 1개 결과와 같은 영수증만 만든다',async()=>{const o=await own();const result=await Promise.all(Array.from({length:12},()=>game.webProgressionAction(p.id,'rebirth-0001','reincarnate',key(o))));for(const r of result)expect(r).toEqual(result[0]);expect(await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}})).toBe(1);expect(await db.progressionReceipt.count({where:{playerId:p.id}})).toBe(1);expect(result[0]).toMatchObject({rarity:'COMMON',reincarnation:1,inheritedFrom:'D1',statBonusPct:5});await expect(game.webProgressionAction(p.id,'rebirth-0001','reroll')).rejects.toThrow('다른 작업');});
 it('다른 요청과 다른 DB 클라이언트의 중복 소비도 결과는 하나다',async()=>{const o=await own(),other=new GameService(db2,C,()=>now);const results=await Promise.allSettled([game.webProgressionAction(p.id,'parallel-001','reincarnate',key(o)),other.webProgressionAction(p.id,'parallel-002','reincarnate',key(o))]);expect(results.filter(x=>x.status==='fulfilled')).toHaveLength(1);expect(await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}})).toBe(1);},20000);
 it('타인·보호·탐험·전투 개체를 소비하지 않는다',async()=>{const stranger=await db.player.create({data:{identity:randomUUID()}});for(const data of [{locked:true},{status:'EXPEDITION'},{playerId:stranger.id}]){const o=await own('D1',data);await expect(game.webProgressionAction(p.id,randomUUID(),'reincarnate',key(o))).rejects.toThrow();expect((await db.ownedCharacter.findUniqueOrThrow({where:{id:o.id}})).status).not.toBe('CONSUMED');}const o=await own();await db.realtimeActivity.create({data:{playerId:p.id,kind:'ROOM',targetId:'ongoing',expiresAt:new Date(+now-1)}});await expect(game.webProgressionAction(p.id,randomUUID(),'reincarnate',key(o))).rejects.toThrow('전투');});
 it('최상위로 돌아온 계보는 +2가 되고 최초 고유기 하나와 보너스를 유지한다',async()=>{const o=await own('T1',{reincarnation:1,inheritedFrom:'D1',statBonusPct:5});expect(await game.webProgressionAction(p.id,randomUUID(),'reincarnate',key(o))).toMatchObject({reincarnation:2,inheritedFrom:'D1',statBonusPct:10});});
 it('패스 중복 수령·미달·가짜 milestone을 거절한다',async()=>{await db.progressionState.create({data:{playerId:p.id,points:10}});const before=await db.player.findUniqueOrThrow({where:{id:p.id}});const r=await Promise.all(Array.from({length:10},()=>game.webProgressionAction(p.id,'milestone-001','pass','p10')));expect(r.every(x=>JSON.stringify(x)===JSON.stringify(r[0]))).toBe(true);expect((await db.player.findUniqueOrThrow({where:{id:p.id}})).credits).toBe(before.credits+1);for(const id of ['p10','p30','fake'])await expect(game.webProgressionAction(p.id,randomUUID(),'pass',id)).rejects.toThrow();});
 it('리롤은 5시간 쿨다운·30분 유효·1회 소비이며 정확한 경계도 안전하다',async()=>{const claim:any=await game.webProgressionAction(p.id,'reroll-claim-1','reroll');expect(claim.expiresAt).toBe(+now+1800000);await expect(game.webProgressionAction(p.id,'reroll-claim-2','reroll')).rejects.toThrow('5시간');const used=await Promise.all(Array.from({length:5},()=>game.webConsumeBonusReroll(p.id,'reroll-use-001')));expect(used).toHaveLength(5);await expect(game.webConsumeBonusReroll(p.id,'reroll-use-002')).rejects.toThrow();now=new Date(+now+5*3600000);await game.webProgressionAction(p.id,'reroll-claim-3','reroll');now=new Date(+now+30*60000);expect((await game.webProgression(p.id)).reroll.available).toBe(false);await expect(game.webConsumeBonusReroll(p.id,'reroll-use-003')).rejects.toThrow();});
 it('고급 모집 출처만 같은 희귀·전설 등급으로 재심사하고 대기 시간 동안 추가 지급하지 않는다',async()=>{const rare=C.characters.find(c=>c.rarity==='RARE')!;const legacy=await own(rare.id),o=await own(rare.id,{obtainedVia:'GACHA_HIGH'});await expect(game.webProgressionAction(p.id,randomUUID(),'reassess',key(legacy))).rejects.toThrow('고급 모집');const result:any=await game.webProgressionAction(p.id,'reassess-001','reassess',key(o));expect(result.rarity).toBe('RARE');expect(result.id).not.toBe(rare.id);expect(result.nextAvailableAt).toBe(+now+progressionConfig.reassessmentCooldownMs);await expect(game.webProgressionAction(p.id,'reassess-002','reassess',result.key)).rejects.toThrow('대기');expect((await game.webProgression(p.id)).pass.points).toBe(0);});
 it('포인트 일일 상한·관리자 제외·서버 완료 보상 중복 방지가 동작한다',async()=>{const player=await db.player.findUniqueOrThrow({where:{id:p.id}});await db.$transaction(tx=>awardPlayPoints(tx,player,1000,now));expect((await game.webProgression(p.id)).pass.points).toBe(progressionConfig.dailyCap);now=new Date(+now+24*3600000);await Promise.all(Array.from({length:4},()=>game.webNativeCommit(p.id,undefined,undefined,{session:'test-session-001',points:3})));expect((await game.webProgression(p.id)).pass.points).toBe(progressionConfig.dailyCap+3);await db.player.update({where:{id:p.id},data:{isAdmin:true,adminMode:true}});await expect(game.webProgressionAction(p.id,randomUUID(),'reroll')).rejects.toThrow('관리자');});
 it('일반 환생과 성장된 최상위 개체에서 보너스·고유기를 개체별로만 적용한다',()=>{const base:UnitRecord={uid:'o1',id:'T1',name:'T1',tier:'TRANSCEND',level:1,xp:0,stage:0,evoAugs:[],learned:['@','basic','sig']};const inherited={...base,ownedCharacterId:'T1',reincarnation:2,statBonusPct:10,inheritedFrom:'D1'};expect(slotSkills(R,inherited).find(s=>s.id==='sig')?.name).toBe(R.unitDef('D1')!.signature.name);expect(slotSkills(R,base).find(s=>s.id==='sig')?.name).toBe(R.unitDef('T1')!.signature.name);expect(statsOf(R,inherited).hp).toBe(Math.round(statsOf(R,base).hp*1.1));const common={...inherited,ownedCharacterId:C.characters.find(c=>c.rarity==='COMMON')!.id};expect(statsOf(R,common).hp).toBeGreaterThan(0);expect(statsOf(R,base)).toEqual(statsOf(R,{...base}));});
 it('추가 리롤 엔진은 기본 리롤을 유지하며 보너스를 명시한 호출만 2회 허용한다',()=>{const rec:UnitRecord={uid:'a',id:'D1',name:'D1',tier:'LIMITED',level:1,xp:0,stage:0,evoAugs:[]};const b=createBattle(R,[rec],[{...rec,uid:'b'}],777);rerollAugment(b,0,0);expect(()=>rerollAugment(b,0,0)).toThrow();expect(()=>rerollAugment(b,0,0,true)).not.toThrow();});
 it('네이티브 progression API는 인증·요청 형태를 검사하고 확정 거절을 표시한다',async()=>{const app=Fastify();registerNative(app,game,process.cwd(),req=>req.headers.authorization===p.id?p.id:null,()=>true);try{expect((await app.inject({url:'/my/api/native/progression'})).statusCode).toBe(401);const bad=await app.inject({method:'POST',url:'/my/api/native/progression/reincarnate',headers:{authorization:p.id},payload:{requestId:'tiny',key:'o1',points:999}});expect(bad.statusCode).toBe(400);expect(bad.json()).toMatchObject({ok:false,retryable:false});const state=await app.inject({url:'/my/api/native/progression',headers:{authorization:p.id}});expect(state.statusCode).toBe(200);expect(state.json().pass.points).toBe(0);}finally{await app.close();}});
 it('조합은 계보를 결과 개체에 승계하고 같은 결과의 복수 계보·전투 중 소비를 원자적으로 거절한다',async()=>{
  const recipe=C.recipes.find(r=>r.materials.length>=2&&C.map.get(r.resultId)!.rarity!=='HIDDEN')!;
  const rows=[];for(const [i,id] of recipe.materials.entries())rows.push(await own(id,i===0?{reincarnation:2,inheritedFrom:'D1',statBonusPct:10}:{}));
  await game.webCraft(p.id,recipe.resultId,1);
  expect(await db.ownedCharacter.findFirstOrThrow({where:{playerId:p.id,status:'AVAILABLE',characterId:recipe.resultId}})).toMatchObject({reincarnation:2,inheritedFrom:'D1',statBonusPct:10});
  for(const [i,id] of recipe.materials.entries())await own(id,i<2?{reincarnation:1,inheritedFrom:'D1',statBonusPct:5}:{});
  const before=await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}});
  await expect(game.webCraft(p.id,recipe.resultId,1)).rejects.toThrow('한 조합');expect(await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}})).toBe(before);
  await db.realtimeActivity.create({data:{playerId:p.id,kind:'QUEUE',targetId:'queued',expiresAt:new Date(+now+60000)}});
  await expect(game.webCraft(p.id,recipe.resultId,1)).rejects.toThrow('전투');expect(await db.ownedCharacter.count({where:{playerId:p.id,status:'AVAILABLE'}})).toBe(before);
 });
 it('계승 고유기 교체는 배움 횟수 차감 없이 거절한다',()=>{
  const rec:UnitRecord={uid:'o9',id:'D1',name:'D1',tier:'LIMITED',level:60,xp:0,stage:0,evoAugs:[],learned:['@','basic','sig','role',R.learnables[0].id],inheritedFrom:'D1',reincarnation:1};
  const offer=learnOffers(R,rec)[0];expect(offer).toBeDefined();const before=structuredClone(rec);expect(()=>learnSkill(R,rec,offer.id,'sig')).toThrow('계승');expect(rec).toEqual(before);
 });
 it.each(['normal','rank'] as const)('%s 준비·전투 경계를 통과해도 일반 환생의 정확한 개체를 유지한다',async mode=>{
  const source=await own(),reborn:any=await game.webProgressionAction(p.id,randomUUID(),'reincarnate',key(source));const keys=[reborn.key,key(await own('T1')),key(await own('E1'))];
  const other=await db.player.create({data:{identity:randomUUID(),battleTeam:'D2,T2,E2'}});for(const id of ['D2','T2','E2'])await db.ownedCharacter.create({data:{playerId:other.id,characterId:id,obtainedVia:'TEST'}});
  await db.realtimeLineup.create({data:{playerId:p.id,teamJson:JSON.stringify(keys)}});
  const service=new RealtimePvpService(db,C,process.cwd(),'progression-test-secret',()=>+now);
  try{
   for(const playerId of [p.id,other.id]){const t=await service.attempt(playerId,mode);await service.join(playerId,t.attemptId,t.token);}
   const room=await db.realtimeRoom.findFirstOrThrow({where:{OR:[{player0Id:p.id},{player1Id:p.id}]}});
   const command=async(playerId:string,command:any)=>{const view=await service.currentRoom(playerId,room.id);return service.command(playerId,{roomId:room.id,phaseId:view.phaseId,requestId:randomUUID(),command});};
   await command(p.id,{accept:true});const accepted=await command(other.id,{accept:true});
   const prep=await service.currentRoom(p.id,room.id);expect(prep.preparation?.available.some(x=>x.id===reborn.key)).toBe(true);
   await command(p.id,{prepare:{team:keys,ready:true}});await command(other.id,{prepare:{team:['D2','T2','E2'],ready:true}});
   const stored=await db.realtimeRoom.findUniqueOrThrow({where:{id:room.id}}),battle=createBattleService(R).restore(JSON.parse(stored.stateJson).battle),side=stored.player0Id===p.id?0:1;
   expect(battle.sides[side].fighters[0].rec).toMatchObject({uid:reborn.key,ownedCharacterId:reborn.id,inheritedFrom:'D1',reincarnation:1,statBonusPct:5});
   const view=await service.currentRoom(p.id,room.id);expect(view.battle?.teams[0].fighters[0]).toMatchObject({id:reborn.id,name:`${reborn.name} +1`,image:`/thumbs/${reborn.id}.jpg`});await game.webProgressionAction(p.id,randomUUID(),'reroll');
   const first=await command(p.id,{reroll:0});const second=await command(p.id,{reroll:0});expect(second.battle?.bonusReroll.available).toBe(false);await expect(command(p.id,{reroll:0})).rejects.toThrow();
  }finally{service.stop();}
 });

});
