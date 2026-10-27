import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import Fastify from 'fastify';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,mkdirSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {registerRoster60} from '../src/roster60-routes.js';
import {adventureEscapeSucceeds} from '../src/roster60-adventure.js';
import {RosterPvp} from '../src/roster60-pvp.js';
import type {StoryContent,StoryQuest} from '../src/story/types.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
suite('인증된 스토리 저장·의뢰·모험·귀환의 격리 DB 수직 시험',()=>{
 let db:PrismaClient,app:ReturnType<typeof Fastify>,root:string,content:StoryContent,catalog:any,normalPolicy:any;
 let pvpClock:()=>number=Date.now;
 const owners=new Set<string>();
 const priorProfile=process.env.ROSTER60_PROFILE,priorStory=process.env.ROSTER60_STORY_PROFILE;
 const prefix='/my/api/native/roster60/';
 const request=()=>randomUUID();
 const save=(file:string,data:unknown)=>writeFileSync(join(root,'data',file),JSON.stringify(data));
 function server(){app=Fastify();registerRoster60(app,db,root,r=>owners.has(String(r.headers.authorization))?String(r.headers.authorization):null,()=>true,undefined,undefined,undefined,()=>pvpClock());}
 async function get(path:string,owner:string){return app.inject({method:'GET',url:prefix+path,headers:{authorization:owner}});}
 async function post(path:string,payload:any,owner:string){return app.inject({method:'POST',url:prefix+path,headers:{authorization:owner},payload});}
 async function ok(path:string,payload:any,owner:string){const r=await post(path,payload,owner);expect(r.statusCode,`${path}: ${r.body}`).toBe(200);return r.json();}
 async function state(owner:string){const r=await get('story/state',owner);expect(r.statusCode,r.body).toBe(200);return r.json();}
 async function materials(owner:string){const r=await get('story/materials',owner);expect(r.statusCode,r.body).toBe(200);return r.json();}
 async function account(highLevel=true){const p=await db.player.create({data:{identity:'story-test:'+request()}});owners.add(p.id);const u=await db.roster60Owned.create({data:{playerId:p.id,speciesId:'u01',source:request(),totalXp:BigInt(highLevel?catalog.characters.find((x:any)=>x.id==='u01').growth[59].totalXp:0)}});return {owner:p.id,ownedId:u.id};}
 const quest=(s:any,id:string)=>s.quests.find((q:any)=>q.id===id);
 async function open(owner:string,id:string,phase:'intro'|'return'|'replay'){const s=await state(owner);return ok('story/open',{requestId:request(),revision:s.revision,questId:id,phase},owner);}
 async function dialogue(owner:string){let s=await state(owner),lines:string[]=[];while(s.scene){lines.push(s.scene.line.text);if(s.scene.phase==='intro'&&s.scene.last)break;s=await ok('story/advance',{requestId:request(),revision:s.revision,cursor:s.scene.cursor},owner);}return {s,lines};}
 async function accept(owner:string,id:string){await open(owner,id,'intro');const {s}=await dialogue(owner);return ok('story/accept',{requestId:request(),revision:s.revision,questId:id},owner);}
 async function attackToWin(owner:string,battle:any){let b=battle;for(let i=0;b.status==='ACTIVE'&&i<100;i++)b=await ok('battle/action',{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'attack'}},owner);expect(b.status).toBe('FINISHED');expect(b.state.outcome).toBe('win');return b;}
 async function settle(owner:string,b:any,id=request()){return ok('battle/settle',{requestId:id,battleId:b.battleId,revision:b.revision},owner);}
 async function pvpTerminalDiagnostic(view:any,testNow:number){
  const room=await db.roster60PvpMatch.findUnique({where:{id:view.battleId},select:{status:true,revision:true,deadline:true,presence:true}});
  const monitor=await db.appMeta.findUnique({where:{key:'roster60-presence-monitor-v1'}});
  return JSON.stringify({status:view.status,outcome:view.state?.outcome,turn:view.state?.turn,reason:view.settlement?.reason,terminationKind:view.settlement?.terminationKind,serverNow:view.serverNow,testNow,wallNow:Date.now(),room,monitor:monitor?.value});
 }
 async function returnRun(owner:string,id:string){const r=await ok('adventure/read',{adventureId:id},owner);return ok('adventure/return',{requestId:request(),adventureId:id,revision:r.revision},owner);}
 async function complete(owner:string,ownedId:string,id:string){let s=await accept(owner,id);const q=content.quests.find(q=>q.id===id)!;if(q.statueStageOnAccept)expect(s.statue.stage).toBe(q.statueStageOnAccept);for(let i=0;i<q.objective.wins;i++){
   const b=await ok('story/battle/create',{requestId:request(),questId:id,ownedIds:[ownedId]},owner);await settle(owner,await attackToWin(owner,b));
  }
  s=await state(owner);const active=quest(s,id);if(active.adventureId)await returnRun(owner,active.adventureId);
  s=await state(owner);expect(quest(s,id).status,id).toBe('READY');await open(owner,id,'return');await dialogue(owner);s=await state(owner);expect(quest(s,id).status,id).toBe('COMPLETED');return s;
 }
 beforeAll(async()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');
  db=new PrismaClient({datasources:{db:{url}}});root=mkdtempSync(join(tmpdir(),'story-api-'));mkdirSync(join(root,'data/story'),{recursive:true});
  catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
  normalPolicy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/normal-xp-policy.json','utf8'));
  const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
  content=JSON.parse(readFileSync('data/story/story-mode-v1.json','utf8'));
  // 상태·저장 시험만을 위해 모든 조우를 레벨 1로 낮춘 격리 fixture다. 운영 난이도 검증이 아니다.
  content.encounters=content.encounters.map(e=>({...e,level:1,hpMultiplier:1,powerMultiplier:1}));
  save('roster60-approved.json',catalog);save('roster60-battle-policy.json',policy);save('roster60-normal-xp-policy.json',normalPolicy);save('story/story-mode-v1.json',content);
  delete process.env.ROSTER60_PROFILE;process.env.ROSTER60_STORY_PROFILE='story-device-test-v1';server();
 });
 afterAll(async()=>{
  await app?.close();if(priorProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=priorProfile;if(priorStory===undefined)delete process.env.ROSTER60_STORY_PROFILE;else process.env.ROSTER60_STORY_PROFILE=priorStory;
  if(db){await db.player.deleteMany({where:{id:{in:[...owners]}}});await db.$disconnect();}if(root)rmSync(root,{recursive:true,force:true});
 });

 it('새 계정은 첫 의뢰만 보고 미해금 제목·반전·개발자 전투를 직접 열 수 없다',async()=>{
  const a=await account(false),b=await account(false);const s=await state(a.owner);
  expect(s.quests.map((q:any)=>q.id)).toEqual(['STORY_01']);expect(s.scene).toBeNull();expect(s.statue).toMatchObject({stage:1,fragments:0,hatched:false});
  expect(s).not.toHaveProperty('balance');expect(s).not.toHaveProperty('darkGangBalance');
  const body=JSON.stringify(s);expect(body).not.toContain('죽은 적이 없는 친구');expect(body).not.toContain('애초에 죽은 적이 없어');expect(body).not.toContain('어둠의 태초 갈매미맨');
  expect(s.residents.every((n:any)=>n.status==='LOCKED'&&n.questId===null)).toBe(true);
  for(const phase of ['intro','return','replay'])expect((await post('story/open',{requestId:request(),revision:s.revision,questId:'STORY_14',phase},a.owner)).statusCode).toBe(409);
  expect((await post('story/battle/create',{requestId:request(),questId:'STORY_20',ownedIds:[a.ownedId]},a.owner)).statusCode).toBe(409);
  expect((await post('battle/create',{requestId:request(),encounterId:'STORY_20_01',ownedId:a.ownedId},a.owner)).statusCode).toBe(409);
  const options=await get('battle/options',a.owner);expect(options.statusCode).toBe(200);expect(options.body).not.toContain('STORY_20');
  expect((await post('story/open',{requestId:request(),revision:s.revision,questId:'STORY_01',phase:'intro',completed:['STORY_14']},a.owner)).statusCode).toBe(400);
  expect((await get('story/state','bad-session')).statusCode).toBe(401);
  expect((await state(b.owner)).revision).toBe(0);
 });

 it('재접속·닫기·다른 계정·응답 손실 재시도에서 대화 커서와 명시 수락을 보존한다',async()=>{
  const a=await account(),b=await account();let s=await open(a.owner,'STORY_01','intro');
  expect(quest(s,'STORY_01').status).toBe('AVAILABLE');
  const body={requestId:request(),revision:s.revision,cursor:s.scene.cursor};const advanced=await ok('story/advance',body,a.owner);
  // 응답을 받지 못했다고 가정하고 같은 요청을 재전송한다.
  expect(await ok('story/advance',body,a.owner)).toEqual(advanced);
  expect((await post('story/advance',{...body,cursor:body.cursor+1},a.owner)).json().error).toBe('REQUEST_ID_REUSED');
  expect((await post('story/advance',{...body,requestId:request()},a.owner)).json().error).toBe('STORY_STALE_REVISION');
  await app.close();server();s=await state(a.owner);expect(s.scene.cursor).toBe(advanced.scene.cursor);
  expect((await state(b.owner)).scene).toBeNull();
  await ok('story/close',{requestId:request(),revision:s.revision},a.owner);s=await open(a.owner,'STORY_01','intro');expect(s.scene.cursor).toBe(advanced.scene.cursor);
  const done=await dialogue(a.owner);s=done.s;expect(quest(s,'STORY_01').status).toBe('AVAILABLE');
  expect((await post('story/advance',{requestId:request(),revision:s.revision,cursor:s.scene.cursor},a.owner)).json().error).toBe('STORY_ACCEPT_REQUIRED');
  const acceptBody={requestId:request(),revision:s.revision,questId:'STORY_01'};
  const accepted=await ok('story/accept',acceptBody,a.owner);expect(await ok('story/accept',acceptBody,a.owner)).toEqual(accepted);
  expect(quest(accepted,'STORY_01').status).toBe('ACTIVE');expect(accepted.scene).toBeNull();
  expect((await post('story/accept',{...acceptBody,requestId:request()},a.owner)).json().error).toBe('STORY_STALE_REVISION');
 });

 it('첫 수직 흐름은 승리 즉시 재화를 한 번 지급하고 정상 귀환 후에만 조각·다음 의뢰를 확정한다',async()=>{
  const a=await account(),other=await account();await accept(a.owner,'STORY_01');const createBody={requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]};
  let b=await ok('story/battle/create',createBody,a.owner);expect(await ok('story/battle/create',createBody,a.owner)).toEqual(b);
  expect((await post('battle/read',{battleId:b.battleId},other.owner)).statusCode).toBe(404);
  expect((await post('battle/action',{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'attack'},outcome:'win'},a.owner)).statusCode).toBe(400);
  b=await attackToWin(a.owner,b);const settleId=request(),settled=await settle(a.owner,b,settleId);
  expect(settled.settlement.story.darkGangAwarded).toBe(content.policy.darkGangPerWin);
  expect(await settle(a.owner,b,settleId)).toEqual(settled);expect(await settle(a.owner,b)).toEqual(settled);
  expect(await db.roster60StoryBattleAward.count({where:{playerId:a.owner,battleId:b.battleId}})).toBe(1);
  let m=await materials(a.owner);expect(m.balance).toBe(content.policy.darkGangPerWin);expect(m.lifetimeEarned).toBe(m.balance);
  let s=await state(a.owner);expect(quest(s,'STORY_01').status).toBe('ACTIVE');expect(s.statue.fragments).toBe(0);
  expect((await post('story/open',{requestId:request(),revision:s.revision,questId:'STORY_01',phase:'return'},a.owner)).statusCode).toBe(409);
  // 정산 후 귀환 전 네트워크 단절/프로세스 종료에 해당하는 서버 재생성.
  await app.close();server();s=await state(a.owner);const run=await ok('adventure/read',{adventureId:quest(s,'STORY_01').adventureId},a.owner);
  const returnBody={requestId:request(),adventureId:run.adventureId,revision:run.revision};const returned=await ok('adventure/return',returnBody,a.owner);expect(await ok('adventure/return',returnBody,a.owner)).toEqual(returned);
  s=await state(a.owner);expect(quest(s,'STORY_01').status).toBe('READY');await open(a.owner,'STORY_01','return');await dialogue(a.owner);s=await state(a.owner);
  expect(s.statue).toMatchObject({stage:1,fragments:1});expect(quest(s,'STORY_01').status).toBe('COMPLETED');expect(quest(s,'STORY_02').status).toBe('AVAILABLE');
  expect(await materials(a.owner)).toEqual(m);
  const replay=await open(a.owner,'STORY_01','replay');expect(replay.scene).not.toBeNull();expect(replay.scene.line.text).toBe(content.scenes.find(scene=>scene.id==='STORY_01_intro')!.lines[0].text);await dialogue(a.owner);expect(await materials(a.owner)).toEqual(m);expect((await state(a.owner)).statue.fragments).toBe(1);
 });

 it('동시에 누른 대화 진행은 한 번만 전진하고 정산 실패는 모든 쓰기를 되돌린 뒤 재시도된다',async()=>{
  const a=await account();let s=await open(a.owner,'STORY_01','intro');const make=()=>({requestId:request(),revision:s.revision,cursor:s.scene.cursor});
  const taps=await Promise.all([post('story/advance',make(),a.owner),post('story/advance',make(),a.owner)]);expect(taps.map(r=>r.statusCode).sort()).toEqual([200,409]);
  await dialogue(a.owner);s=await state(a.owner);await ok('story/accept',{requestId:request(),revision:s.revision,questId:'STORY_01'},a.owner);
  const b=await attackToWin(a.owner,await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner));
  const before=await state(a.owner),settleBody={requestId:request(),battleId:b.battleId,revision:b.revision};
  await db.$executeRawUnsafe(`CREATE TRIGGER story_award_test_failure BEFORE UPDATE ON Roster60StoryAccount WHEN NEW.playerId='${a.owner}' BEGIN SELECT RAISE(ABORT, 'story-test-only-failure'); END`);
  try{expect((await post('battle/settle',settleBody,a.owner)).statusCode).toBe(500);expect((await ok('battle/read',{battleId:b.battleId},a.owner)).status).toBe('FINISHED');expect(await db.roster60StoryBattleAward.count({where:{playerId:a.owner}})).toBe(0);expect((await materials(a.owner)).balance).toBe(0);expect((await state(a.owner)).revision).toBe(before.revision);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('BATTLE');expect(await db.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:a.owner,requestId:settleBody.requestId}}})).toBeNull();}
  finally{await db.$executeRawUnsafe('DROP TRIGGER story_award_test_failure');}
  const paid=await ok('battle/settle',settleBody,a.owner);expect(paid.settlement.story.darkGangAwarded).toBe(content.policy.darkGangPerWin);expect((await materials(a.owner)).balance).toBe(content.policy.darkGangPerWin);
 });

 it('흑화 모험의 도주·패배는 새우Gang과 의뢰 승리를 주지 않는다',async()=>{
  const a=await account();await accept(a.owner,'STORY_01');let b=await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner);
  let n=0;while(!adventureEscapeSucceeds('story-escape-'+n,b.battleId,b.state.turn))n++;await db.roster60Battle.update({where:{id:b.battleId},data:{seed:'story-escape-'+n}});
  b=await ok('battle/action',{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'escape'}},a.owner);expect(b.state.outcome).toBe('escaped');expect((await settle(a.owner,b)).settlement.story.darkGangAwarded).toBe(0);
  let s=await state(a.owner);expect(quest(s,'STORY_01').objective.wins).toBe(0);expect((await materials(a.owner)).balance).toBe(0);
  b=await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner);
  // 이 임시 DB의 적만 확정 패배가 나도록 조정한다. 클라이언트는 이 입력을 보낼 수 없다.
  const row=await db.roster60Battle.findUniqueOrThrow({where:{id:b.battleId}}),battleState=JSON.parse(row.state);battleState.fighters[0].hp=1;battleState.fighters[1].attack.power=1000000;battleState.fighters[1].skills=[];battleState.decision.order=['enemy','self'];await db.roster60Battle.update({where:{id:b.battleId},data:{state:JSON.stringify(battleState)}});
  b=await ok('battle/action',{requestId:request(),battleId:b.battleId,revision:b.revision,action:{kind:'attack'}},a.owner);expect(b.state.outcome).toBe('loss');expect((await settle(a.owner,b)).settlement.story.darkGangAwarded).toBe(0);
  s=await state(a.owner);expect(quest(s,'STORY_01').objective.wins).toBe(0);expect((await materials(a.owner)).balance).toBe(0);
 });

 it('같은 의뢰의 동시 출발은 모험 하나만 예약하고 다른 대원을 고아 모험에 잠그지 않는다',async()=>{
  const a=await account();const second=await db.roster60Owned.create({data:{playerId:a.owner,speciesId:'u01',source:request()}});await accept(a.owner,'STORY_01');
  const attempts=await Promise.all([post('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner),post('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[second.id]},a.owner)]);
  expect(attempts.map(r=>r.statusCode).sort()).toEqual([200,409]);
  const winner=attempts.find(r=>r.statusCode===200)!.json(),s=await state(a.owner),q=quest(s,'STORY_01');
  expect(await db.roster60Adventure.count({where:{playerId:a.owner,status:'ACTIVE'}})).toBe(1);
  expect(q.adventureId).toBe(winner.state.adventureId);expect(q.battleId).toBe(winner.battleId);
  const units=await db.roster60Owned.findMany({where:{playerId:a.owner}});expect(units.filter(u=>u.status==='BATTLE')).toHaveLength(1);expect(units.filter(u=>u.status==='AVAILABLE')).toHaveLength(1);expect(units.some(u=>u.status==='ADVENTURE')).toBe(false);
 });

 it('다른 계정의 개체로 출발할 수 없고 실패한 위조 요청이 정상 출발을 막지 않는다',async()=>{
  const a=await account(),b=await account();await accept(a.owner,'STORY_01');const invalid=await post('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[b.ownedId]},a.owner);expect(invalid.statusCode).toBe(409);expect(await db.roster60Adventure.count({where:{playerId:a.owner}})).toBe(0);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:b.ownedId}})).status).toBe('AVAILABLE');
  const valid=await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner);expect(valid.status).toBe('ACTIVE');
 });

 it.each(['nonexistent','foreign','returned'])('잘못된 %s 모험 ID는 출발 예약을 남기지 않는다',async kind=>{
  const a=await account(),other=await account();await accept(a.owner,'STORY_01');let adventureId=request();
  if(kind==='foreign')adventureId=(await ok('adventure/start',{requestId:request(),ownedIds:[other.ownedId]},other.owner)).adventureId;
  if(kind==='returned'){
   const spare=await db.roster60Owned.create({data:{playerId:a.owner,speciesId:'u01',source:request()}}),run=await ok('adventure/start',{requestId:request(),ownedIds:[spare.id]},a.owner);await returnRun(a.owner,run.adventureId);adventureId=run.adventureId;
  }
  const denied=await post('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId],adventureId},a.owner);expect(denied.statusCode).toBe(404);expect((await state(a.owner)).pendingDeparture).toBeNull();expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('AVAILABLE');
  const valid=await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner);expect(valid.status).toBe('ACTIVE');expect(valid.state.adventureId).not.toBe(adventureId);
 });

 it('저장된 버전과 다른 콘텐츠로 재시작하면 새 대사와 잘못된 커서를 노출하지 않는다',async()=>{
  const a=await account();const before=await open(a.owner,'STORY_01','intro');const changed=structuredClone(content);changed.version=content.version+'-different';changed.scenes.find(s=>s.id==='STORY_01_intro')!.lines=[{speakerId:'narrator',text:'UNRELEASED_STORY_SECRET'}];
  await app.close();save('story/story-mode-v1.json',changed);server();
  try{const read=await get('story/state',a.owner);expect(read.statusCode).toBe(409);expect(read.json().error).toBe('STORY_CONTENT_CHANGED');expect(read.body).not.toContain('UNRELEASED_STORY_SECRET');}
  finally{await app.close();save('story/story-mode-v1.json',content);server();}
  const resumed=await state(a.owner);expect(resumed.scene).toEqual(before.scene);expect(resumed.revision).toBe(before.revision);
 });

 it('모험 시작 뒤 전투 생성 실패/재접속은 저장된 출발 의도를 재사용한다',async()=>{
  const a=await account();await accept(a.owner,'STORY_01');const createBody={requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]};
  await db.$executeRawUnsafe(`CREATE TRIGGER story_create_test_failure BEFORE INSERT ON Roster60Battle WHEN NEW.playerId='${a.owner}' BEGIN SELECT RAISE(ABORT, 'story-create-test-only-failure'); END`);
  try{expect((await post('story/battle/create',createBody,a.owner)).statusCode).toBe(500);expect(await db.roster60Adventure.count({where:{playerId:a.owner,status:'ACTIVE'}})).toBe(1);expect(await db.roster60Battle.count({where:{playerId:a.owner}})).toBe(0);expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('ADVENTURE');}
  finally{await db.$executeRawUnsafe('DROP TRIGGER story_create_test_failure');}
  await app.close();server();const created=await ok('story/battle/create',createBody,a.owner);expect(created.status).toBe('ACTIVE');expect(await db.roster60Adventure.count({where:{playerId:a.owner}})).toBe(1);expect(await db.roster60Battle.count({where:{playerId:a.owner}})).toBe(1);
  expect(await ok('story/battle/create',createBody,a.owner)).toEqual(created);
 });

 it('모험 시작 후 연결 전 중단된 의도는 다른 기기의 귀환 뒤 영구 예약을 남기지 않는다',async()=>{
  const a=await account();await accept(a.owner,'STORY_01');const createBody={requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]};
  await db.$executeRawUnsafe(`CREATE TRIGGER story_link_test_failure BEFORE UPDATE ON Roster60StoryAccount WHEN NEW.playerId='${a.owner}' AND instr(NEW.state, '"adventureId"')>0 BEGIN SELECT RAISE(ABORT, 'story-link-test-only-failure'); END`);
  try{expect((await post('story/battle/create',createBody,a.owner)).statusCode).toBe(500);}
  finally{await db.$executeRawUnsafe('DROP TRIGGER story_link_test_failure');}
  const run=await db.roster60Adventure.findFirstOrThrow({where:{playerId:a.owner,status:'ACTIVE'}});expect((await state(a.owner)).pendingDeparture).not.toBeNull();
  // 다른 기기는 전투 밖의 실제 모험을 읽고 정상 귀환할 수 있다.
  await returnRun(a.owner,run.id);await app.close();server();const oldRetry=await post('story/battle/create',createBody,a.owner);expect(oldRetry.statusCode).toBe(404);expect((await state(a.owner)).pendingDeparture).toBeNull();expect((await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).status).toBe('AVAILABLE');
  const next=await ok('story/battle/create',{requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]},a.owner);expect(next.status).toBe('ACTIVE');expect(next.state.adventureId).not.toBe(run.id);
 });

 it('전투 생성 후 바깥 영수증 저장 전 중단돼도 다른 기기의 귀환 뒤 같은 요청을 복구한다',async()=>{
  const a=await account();await accept(a.owner,'STORY_01');const createBody={requestId:request(),questId:'STORY_01',ownedIds:[a.ownedId]};
  const created=await ok('story/battle/create',createBody,a.owner),accountRow=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.owner}}),progress=JSON.parse(accountRow.state);
  // 실제 프로세스 종료 지점을 임시 DB의 바깥 workflow 영수증만 pending으로 되돌려 주입한다.
  await db.roster60StoryReceipt.update({where:{playerId_requestId:{playerId:a.owner,requestId:createBody.requestId}},data:{result:JSON.stringify({pending:true,contentVersion:content.version,questId:'STORY_01',acceptToken:progress.accepted.STORY_01.token,encounterId:'STORY_01_01',adventureId:created.state.adventureId})}});
  await settle(a.owner,await attackToWin(a.owner,created));await returnRun(a.owner,created.state.adventureId);await open(a.owner,'STORY_01','return');await dialogue(a.owner);
  const before=await materials(a.owner);await app.close();server();const recovered=await ok('story/battle/create',createBody,a.owner);expect(recovered.battleId).toBe(created.battleId);expect(await ok('story/battle/create',createBody,a.owner)).toEqual(recovered);expect(await db.roster60Adventure.count({where:{playerId:a.owner}})).toBe(1);expect(await db.roster60Battle.count({where:{playerId:a.owner}})).toBe(1);expect(await materials(a.owner)).toEqual(before);expect(quest(await state(a.owner),'STORY_01').status).toBe('COMPLETED');
 });

 it('완료한 의뢰의 반복 모험은 승리 재화만 더하고 기존 단일 PvE 연습전은 새우Gang을 지급하지 않는다',async()=>{
  const a=await account();await complete(a.owner,a.ownedId,'STORY_01');const before=await materials(a.owner),fragments=(await state(a.owner)).statue.fragments;
  const repeat=await ok('story/adventure/create',{requestId:request(),encounterId:'STORY_01_01',ownedIds:[a.ownedId]},a.owner);await settle(a.owner,await attackToWin(a.owner,repeat));await returnRun(a.owner,repeat.state.adventureId);
  const extra=await materials(a.owner);expect(extra.balance).toBe(before.balance+content.policy.darkGangPerWin);const s=await state(a.owner);expect(quest(s,'STORY_01').status).toBe('COMPLETED');expect(s.statue.fragments).toBe(fragments);
  // 기존 단일 PvE 연습전의 XP 지급 여유를 둔다. 실제 일반게임(RosterPvp normal)과 다른 경로다.
  await db.roster60Owned.update({where:{id:a.ownedId},data:{totalXp:BigInt(catalog.characters.find((x:any)=>x.id==='u01').growth[58].totalXp)}});
  const duel=await ok('battle/create',{requestId:request(),ownedId:a.ownedId,encounterId:'training-1'},a.owner);const settled=await settle(a.owner,await attackToWin(a.owner,duel));expect(settled.settlement.story).toBeUndefined();expect(await materials(a.owner)).toEqual(extra);expect(await db.roster60StoryBattleAward.count({where:{playerId:a.owner,battleId:duel.battleId}})).toBe(0);
 });

 it('실제 일반게임 normal의 30초 AI 승리·정산 재시도는 XP만 한 번 지급하고 새우Gang을 늘리지 않는다',async()=>{
  const a=await account();await complete(a.owner,a.ownedId,'STORY_01');
  // 정상 완료 XP를 받을 공간을 둔다. 최대 레벨 초과량은 별도 cap 보류 시험의 대상이다.
  await db.roster60Owned.update({where:{id:a.ownedId},data:{totalXp:BigInt(catalog.characters.find((x:any)=>x.id==='u01').growth[58].totalXp)}});
  const beforeMaterials=await materials(a.owner),beforeStory=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.owner}}),beforeXp=(await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}})).totalXp;
  const beforeAwards=await db.roster60StoryBattleAward.count({where:{playerId:a.owner}}),beforePveBattles=await db.roster60Battle.count({where:{playerId:a.owner}});
  const policy=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/battle-policy.json','utf8'));
  // 원본에 있는 레벨 1 연습 조우·u07만 AI 공급 후보로 제한한다. 스탯/XP/매칭 정책은 새로 만들지 않는다.
  policy.encounters=policy.encounters.filter((e:any)=>e.mode==='duel'&&e.level===1).map((e:any)=>({...e,enemySpeciesIds:['u07']}));
  expect(policy.encounters.length).toBeGreaterThan(0);let now=Date.now();const previousClock=pvpClock;pvpClock=()=>now;
  try{
   // Route sweeps and direct commands observe the same server clock throughout this case.
   let pvp=new RosterPvp(db,()=>catalog,()=>policy,()=>now,()=>normalPolicy);
   await pvp.party(a.owner,[a.ownedId]);const queued:any=await pvp.join(a.owner,{requestId:request(),mode:'normal'});expect(queued.status).toBe('waiting');now+=29_999;expect((await pvp.current(a.owner)).status).toBe('waiting');now++;
   const matched:any=await pvp.current(a.owner);expect(matched.battle).toMatchObject({mode:'normal',opponentKind:'AI',status:'ACCEPTING'});const battleId=matched.battle.battleId;
   expect(await db.roster60PvpMatch.count({where:{id:battleId,player0Id:a.owner,mode:'normal'}})).toBe(1);
   let battle:any=await pvp.accept(a.owner,{requestId:request(),battleId,revision:matched.battle.revision,accept:true}),lastAction:any;
   for(let i=0;battle.status==='ACTIVE'&&i<100;i++){lastAction={requestId:request(),battleId,revision:battle.revision,action:{kind:'attack' as const}};battle=await pvp.action(a.owner,lastAction);}
   expect(battle.state.outcome,battle.state.outcome==='win'?undefined:await pvpTerminalDiagnostic(battle,now)).toBe('win');expect(battle.settlement).toMatchObject({reason:'BATTLE',xpAwarded:policy.encounters[0].winXp,rating:null});expect(await pvp.action(a.owner,lastAction)).toEqual(battle);
   pvp=new RosterPvp(db,()=>catalog,()=>policy,()=>now,()=>normalPolicy);const confirm={requestId:request(),battleId,revision:battle.revision},settled:any=await pvp.settle(a.owner,confirm);expect(settled.status).toBe('SETTLED');expect(await pvp.settle(a.owner,confirm)).toEqual(settled);await pvp.settle(a.owner,{...confirm,requestId:request()});
   const owned=await db.roster60Owned.findUniqueOrThrow({where:{id:a.ownedId}});expect(owned.status).toBe('AVAILABLE');expect(owned.totalXp-beforeXp).toBe(BigInt(policy.encounters[0].winXp));
   expect(await materials(a.owner)).toEqual(beforeMaterials);expect(await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.owner}})).toEqual(beforeStory);expect(await db.roster60StoryBattleAward.count({where:{playerId:a.owner}})).toBe(beforeAwards);expect(await db.roster60StoryBattleAward.findUnique({where:{playerId_battleId:{playerId:a.owner,battleId}}})).toBeNull();expect(await db.roster60Battle.count({where:{playerId:a.owner}})).toBe(beforePveBattles);
  }finally{
   pvpClock=previousClock;
   await db.roster60PvpMatch.deleteMany({where:{player0Id:a.owner}});await db.roster60PvpQueue.deleteMany({where:{playerId:a.owner}});await db.roster60PvpParty.deleteMany({where:{playerId:a.owner}});await db.realtimeActivity.deleteMany({where:{playerId:a.owner}});await db.realtimeItemEscrow.deleteMany({where:{playerId:a.owner}});
  }
 });

 it('재료 모험은 전투 정산 후 재접속해도 같은 모험을 이어가고 정상 귀환으로 끝낸다',async()=>{
  const a=await account();await complete(a.owner,a.ownedId,'STORY_01');const before=await materials(a.owner);
  const first=await ok('story/adventure/create',{requestId:request(),encounterId:'STORY_01_01',ownedIds:[a.ownedId]},a.owner);await settle(a.owner,await attackToWin(a.owner,first));
  await app.close();server();const resumed=await state(a.owner);expect(resumed.materialAdventure).toMatchObject({adventureId:first.state.adventureId,status:'ACTIVE',activeBattleId:null});
  const second=await ok('story/adventure/create',{requestId:request(),encounterId:'STORY_01_01',ownedIds:[a.ownedId]},a.owner);expect(second.state.adventureId).toBe(first.state.adventureId);await settle(a.owner,await attackToWin(a.owner,second));expect((await materials(a.owner)).balance).toBe(before.balance+2*content.policy.darkGangPerWin);
  await returnRun(a.owner,first.state.adventureId);expect((await state(a.owner)).materialAdventure).toBeNull();expect(quest(await state(a.owner),'STORY_01').status).toBe('COMPLETED');
 });

 it('같은 엔진으로 21개 본편·주민 네 의뢰·석상 복원·부화를 끝내고 인간 빌런에게 지급하지 않는다',async()=>{
  const a=await account(),stages=[1];let blackWins=0,villainWins=0;
  for(const q of content.quests.filter(q=>q.kind==='main')){
   const before=await materials(a.owner);const s=await complete(a.owner,a.ownedId,q.id);if(stages.at(-1)!==s.statue.stage)stages.push(s.statue.stage);
   const encounters=q.objective.encounterIds.map(id=>content.encounters.find(e=>e.id===id)!);const black=encounters.filter(e=>e.kind==='black').length;blackWins+=black;villainWins+=encounters.filter(e=>e.kind==='villain').length;
   expect((await materials(a.owner)).balance).toBe(before.balance+black*content.policy.darkGangPerWin);
   for(const side of content.quests.filter(side=>side.kind==='resident'&&side.unlockAfter.includes(q.id))){
    const available=await state(a.owner);expect(available.residents.find((n:any)=>n.id===side.npcId).status).toBe('AVAILABLE');
    await complete(a.owner,a.ownedId,side.id);blackWins+=side.objective.wins;
    expect((await state(a.owner)).residents.find((n:any)=>n.id===side.npcId).status).toBe('COMPLETED');
   }
  }
  const s=await state(a.owner),m=await materials(a.owner);expect(s.history).toHaveLength(25);expect(s.currentQuest).toBeNull();expect(s.statue.hatched).toBe(true);expect(stages).toEqual([1,2,3,4,5]);expect(m.balance).toBe(blackWins*content.policy.darkGangPerWin);expect(villainWins).toBe(5);expect(m.enhancement).toMatchObject({enabled:false,automaticConsumption:false,eligibilityPolicy:'PENDING'});
  const snapshot=await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.owner}});
  // 보유 개체 환생/레벨 변경은 다른 저장 단위이며 이야기와 두 재화 이력에 reset hook을 두지 않는다.
  await db.roster60Owned.update({where:{id:a.ownedId},data:{totalXp:0n,reincarnation:1}});
  expect((await db.roster60StoryAccount.findUniqueOrThrow({where:{playerId:a.owner}})).state).toBe(snapshot.state);expect(await materials(a.owner)).toEqual(m);
  await open(a.owner,'STORY_21','replay');await dialogue(a.owner);expect(await materials(a.owner)).toEqual(m);expect((await state(a.owner)).statue.hatched).toBe(true);
 },60000);
});
