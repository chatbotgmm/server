import {afterAll,afterEach,beforeAll,beforeEach,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {cpSync,mkdtempSync,readFileSync,rmSync,unlinkSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {buildApp} from '../src/app.js';
import {GameService} from '../src/game.js';
import {loadContent} from '../src/content.js';
import {RosterPvp} from '../src/roster60-pvp.js';
import {RosterReincarnation} from '../src/roster60-reincarnation.js';
import {awardCommanderXp,commanderPolicyFingerprint,commanderPolicyReader,readCommanderState,validateCommanderPolicy,type CommanderEvent} from '../src/progression/commander.js';
import type {PresencePolicy} from '../src/progression/roster60-presence.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const source='data/test-profiles/five-stat-device-test-v1/';
const commanderPolicy=validateCommanderPolicy(JSON.parse(readFileSync(source+'commander-policy.json','utf8')));
const baseCatalog=JSON.parse(readFileSync(source+'catalog.json','utf8'));
const baseBattlePolicy=JSON.parse(readFileSync(source+'battle-policy.json','utf8'));
const normalXpPolicy=JSON.parse(readFileSync(source+'normal-xp-policy.json','utf8'));
const presencePolicy:PresencePolicy={version:'roster60-presence-policy-v1',leaseMs:10,totalAbsentMs:25,monitorGapMs:20};
const request=()=>randomUUID();
type Actor={id:string;ids:string[];headers?:Record<string,string>};

suite('방범대장 PvP 사건 정산과 실제 로그인·native 조회 경계 (격리 SQLite)',()=>{
 let db:PrismaClient,other:PrismaClient,now:number,root:string,catalog:any,battlePolicy:any;
 let app:Awaited<ReturnType<typeof buildApp>>|undefined,game:GameService;
 const owners=new Set<string>(),previousProfile=process.env.ROSTER60_PROFILE;
 const policyPath=()=>join(root,'data/roster60-commander-policy.json');
 const policy=()=>commanderPolicyReader(root)();
 const service=(client=db)=>new RosterPvp(client,()=>catalog,()=>battlePolicy,()=>now,()=>normalXpPolicy,undefined,presencePolicy,policy);
 const restorePolicy=()=>writeFileSync(policyPath(),JSON.stringify(commanderPolicy));

 beforeAll(()=>{
  const url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable SQLite only');
  db=new PrismaClient({datasources:{db:{url}}});other=new PrismaClient({datasources:{db:{url}}});delete process.env.ROSTER60_PROFILE;
 });
 beforeEach(()=>{
  now=Date.now();root=mkdtempSync(join(tmpdir(),'gmm-commander-pvp-native-'));cpSync('data',join(root,'data'),{recursive:true});
  catalog=structuredClone(baseCatalog);battlePolicy=structuredClone(baseBattlePolicy);battlePolicy.enemyStrategy='first-affordable';
  // A deterministic natural KO fixture. Battle HP, results and pending commands are never patched.
  for(const [species,speed,power] of [['u01',100000,1000000],['u02',1,1]] as const){
   const unit=catalog.characters.find((u:any)=>u.id===species);unit.stats.speed=speed;unit.levelOneStats.speed=speed;
   for(const stats of unit.combatGrowth)stats.speed=speed;
   unit.attack={...unit.attack,element:'NORMAL',mpCost:0,power};unit.suitability.NORMAL=0;unit.defense.NORMAL='normal';
   if(species==='u02'){unit.stats.hp=50;unit.levelOneStats.hp=50;for(const stats of unit.combatGrowth)stats.hp=50;}
  }
  for(const encounter of battlePolicy.encounters)if(encounter.mode==='duel'){encounter.speciesId='u02';encounter.enemySpeciesIds=['u02'];}
  writeFileSync(join(root,'data/roster60-approved.json'),JSON.stringify(catalog));
  writeFileSync(join(root,'data/roster60-battle-policy.json'),JSON.stringify(battlePolicy));
  writeFileSync(join(root,'data/roster60-normal-xp-policy.json'),JSON.stringify(normalXpPolicy));restorePolicy();
  game=new GameService(db,loadContent());
 });
 afterEach(async()=>{
  if(app){await app.close();app=undefined;}
  const ids=[...owners],rooms=await db.roster60PvpMatch.findMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]},select:{id:true}}),roomIds=rooms.map(r=>r.id);
  await db.rankedSettlement.deleteMany({where:{roomId:{in:roomIds}}});await db.rankedMatch.deleteMany({where:{roomId:{in:roomIds}}});
  await db.casualHumanSettlement.deleteMany({where:{OR:[{player0Id:{in:ids}},{player1Id:{in:ids}}]}});
  await db.roster60PvpMatch.deleteMany({where:{id:{in:roomIds}}});await db.roster60PvpQueue.deleteMany({where:{playerId:{in:ids}}});
  await db.roster60PvpParty.deleteMany({where:{playerId:{in:ids}}});await db.realtimeActivity.deleteMany({where:{playerId:{in:ids}}});
  await db.realtimeItemEscrow.deleteMany({where:{playerId:{in:ids}}});
  // Ordinary login creates legacy StorySave/WebLogin rows with restrictive foreign keys.
  for(const id of ids)await game.webDeleteAccount(id);owners.clear();
  if(root)rmSync(root,{recursive:true,force:true});
 });
 afterAll(async()=>{if(previousProfile===undefined)delete process.env.ROSTER60_PROFILE;else process.env.ROSTER60_PROFILE=previousProfile;await Promise.all([db.$disconnect(),other.$disconnect()]);});

 async function units(playerId:string,count=1,species='u01',level=8){
  const definition=catalog.characters.find((u:any)=>u.id===species),ids:string[]=[];
  for(let i=0;i<count;i++)ids.push((await db.roster60Owned.create({data:{playerId,speciesId:species,totalXp:BigInt(definition.growth[level-1].totalXp),source:'commander-native-fixture:'+request()}})).id);
  return ids;
 }
 async function actor(count=1,species='u01',level=8):Promise<Actor>{
  const player=await db.player.create({data:{identity:'commander-pvp:'+request()}});owners.add(player.id);
  return {id:player.id,ids:await units(player.id,count,species,level)};
 }
 async function pair(svc:RosterPvp,a:Actor,b:Actor){
  for(const owner of [a,b])await svc.party(owner.id,owner.ids);
  expect((await svc.join(a.id,{requestId:request(),mode:'normal'}) as any).status).toBe('waiting');
  const joined:any=await svc.join(b.id,{requestId:request(),mode:'normal'}),battleId=joined.battle.battleId;
  expect(joined.battle.opponentKind).toBe('HUMAN');
  await svc.accept(a.id,{requestId:request(),battleId,revision:0,accept:true});
  const battle:any=await svc.accept(b.id,{requestId:request(),battleId,revision:0,accept:true});expect(battle.status).toBe('ACTIVE');return battle;
 }
 async function naturalWin(svc:RosterPvp,a:Actor,b:Actor,battle:any){
  let current=battle,last:any,terminalCommand:any;
  for(let round=0;round<b.ids.length;round++){
   const base={battleId:battle.battleId,revision:current.revision};
   await svc.action(a.id,{...base,requestId:request(),action:{kind:'attack'}});
   terminalCommand={...base,requestId:request(),action:{kind:'focus' as const}};last=await svc.action(b.id,terminalCommand);
   if(last.status!=='ACTIVE')break;
   const next=last.state.reserves.find((unit:any)=>unit.hp>0);expect(next).toBeDefined();
   current=await svc.action(b.id,{battleId:battle.battleId,revision:last.revision,requestId:request(),action:{kind:'switch',targetOwnedId:next.ownedId}});
  }
  expect(last.status).toBe('FINISHED');expect(last.state.outcome).toBe('loss');return {last,terminalCommand};
 }
 const accountXp=async(id:string)=>(await db.commanderAccount.findUniqueOrThrow({where:{playerId:id}})).totalXp;
 const ownedRows=(a:Actor)=>db.roster60Owned.findMany({where:{playerId:a.id,id:{in:a.ids}},orderBy:{id:'asc'}});
 const roomLedger=(battleId:string)=>db.commanderXpLedger.findMany({where:{eventKind:'NORMAL_PLAY',eventId:battleId},orderBy:{playerId:'asc'}});
 async function award(playerId:string,event:CommanderEvent){return db.$transaction(tx=>awardCommanderXp(tx,playerId,event,commanderPolicy));}
 async function completedStoryBudget(a:Actor){
  // Authoritative core events, never a level/XP claim from a client request.
  for(const [kind,ids] of [['STORY_QUEST',commanderPolicy.story.mainQuestIds],['RESIDENT_QUEST',commanderPolicy.story.residentQuestIds]] as const)
   for(const id of ids)await award(a.id,{kind,eventId:commanderPolicy.story.contentVersion+':'+id,outcome:'completed'});
 }

 it.each([1,3])('편성 %i명의 정상 승패는 계정100/50 한 사건이며 개체XP·미출전예비와 독립이고 재조회가 중복 지급하지 않는다',async count=>{
  const svc=service(),a=await actor(count),b=await actor(count,'u02'),beforeA=await ownedRows(a),beforeB=await ownedRows(b),battle=await pair(svc,a,b);
  const {last,terminalCommand}=await naturalWin(svc,a,b,battle),winner:any=(await svc.current(a.id) as any).battle;
  expect(winner.settlement.commanderXpAwarded).toBe(commanderPolicy.rewards.normalWin);expect(last.settlement.commanderXpAwarded).toBe(commanderPolicy.rewards.normalLoss);
  expect(await accountXp(a.id)).toBe(BigInt(commanderPolicy.rewards.normalWin));expect(await accountXp(b.id)).toBe(BigInt(commanderPolicy.rewards.normalLoss));
  const winBase=battlePolicy.encounters.find((e:any)=>e.id==='training-10').winXp;
  const afterA=await ownedRows(a),afterB=await ownedRows(b);
  expect(afterA.reduce((sum,u,i)=>sum+u.totalXp-beforeA[i].totalXp,0n)).toBe(BigInt(winBase));
  expect(afterA.filter((u,i)=>u.totalXp>beforeA[i].totalXp)).toHaveLength(1);
  expect(afterB.reduce((sum,u,i)=>sum+u.totalXp-beforeB[i].totalXp,0n)).toBe(BigInt(Math.floor(winBase/2)));
  expect(afterB.every((u,i)=>u.totalXp-beforeB[i].totalXp===BigInt(Math.floor(winBase/2)/count))).toBe(true);
  const ledger=await roomLedger(battle.battleId);expect(ledger).toHaveLength(2);expect(new Set(ledger.map(row=>row.playerId))).toEqual(new Set([a.id,b.id]));
  expect(JSON.stringify(winner)).not.toContain('commanderRewards');expect(JSON.stringify(winner)).not.toContain('policyFingerprint');
  expect(await svc.action(b.id,terminalCommand)).toEqual(last);
  await svc.action(b.id,{...terminalCommand,requestId:request()});
  const reopened=service(other);for(const owner of [a,b]){await reopened.current(owner.id);await svc.current(owner.id);}
  for(const owner of [a,b]){
   const input={requestId:request(),battleId:battle.battleId,revision:last.revision};await svc.settle(owner.id,input);await reopened.settle(owner.id,{...input,requestId:request()});
  }
  expect(await roomLedger(battle.battleId)).toEqual(ledger);expect(await accountXp(a.id)).toBe(BigInt(commanderPolicy.rewards.normalWin));expect(await accountXp(b.id)).toBe(BigInt(commanderPolicy.rewards.normalLoss));
 });

 it.each(['win','loss'] as const)('30초 AI 일반전 %s도 같은 계정 기준을 쓰고 가짜 AI 계정·원장을 만들지 않는다',async outcome=>{
  if(outcome==='loss')catalog.characters.find((u:any)=>u.id==='u02').attack.power=1000000;
  const svc=service(),a=await actor(1,outcome==='win'?'u01':'u02');await svc.party(a.id,a.ids);
  await svc.join(a.id,{requestId:request(),mode:'normal'});now+=30000;
  const matched:any=await svc.current(a.id);expect(matched.battle.opponentKind).toBe('AI');const battleId=matched.battle.battleId;
  const active:any=await svc.accept(a.id,{requestId:request(),battleId,revision:0,accept:true});
  const done:any=await svc.action(a.id,{requestId:request(),battleId,revision:active.revision,action:{kind:outcome==='win'?'attack':'focus'}});
  expect(done.status).toBe('FINISHED');expect(done.state.outcome).toBe(outcome);
  const expected=outcome==='win'?commanderPolicy.rewards.normalWin:commanderPolicy.rewards.normalLoss;
  expect(done.settlement.commanderXpAwarded).toBe(expected);expect(await accountXp(a.id)).toBe(BigInt(expected));
  const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:battleId}});expect(await db.player.findUnique({where:{id:row.player1Id}})).toBeNull();
  expect(await db.commanderAccount.findUnique({where:{playerId:row.player1Id}})).toBeNull();expect((await roomLedger(battleId)).map(entry=>entry.playerId)).toEqual([a.id]);
 });

 it('도주·항복은 승자 쪽도 계정XP0으로 기록하며 개체XP와 재시도 결과를 바꾸지 않는다',async()=>{
  const svc=service(),a=await actor(),b=await actor(1,'u02'),before=[...await ownedRows(a),...await ownedRows(b)],battle=await pair(svc,a,b);
  const input={requestId:request(),battleId:battle.battleId,revision:battle.revision,action:{kind:'escape' as const}},done:any=await svc.action(a.id,input);
  expect(done.settlement).toMatchObject({reason:'SURRENDER',xpAwarded:0,commanderXpAwarded:0});
  expect(await svc.action(a.id,input)).toEqual(done);await service(other).current(b.id);
  expect(await accountXp(a.id)).toBe(0n);expect(await accountXp(b.id)).toBe(0n);expect((await roomLedger(battle.battleId)).every(row=>row.appliedXp===0)).toBe(true);
  for(const unit of before)expect((await db.roster60Owned.findUniqueOrThrow({where:{id:unit.id}})).totalXp).toBe(unit.totalXp);
 });

 it('서버가 관측한 유예 TIMEOUT은 연속 접속 생존자에게만100을 주고 이탈자는0이다',async()=>{
  const svc=service(),a=await actor(),b=await actor(1,'u02'),battle=await pair(svc,a,b),start=now;
  for(const delta of [10,20]){now=start+delta;expect((await svc.current(b.id) as any).battle.status).toBe('ACTIVE');}
  now=start+25;const done:any=(await svc.current(b.id) as any).battle;
  expect(done.settlement).toMatchObject({reason:'TIMEOUT',commanderXpAwarded:commanderPolicy.rewards.normalProvenTimeoutSurvivor});
  expect(done.settlement.commanderReward.reason).toBe('PROVEN_TIMEOUT_SURVIVOR');
  expect(await accountXp(b.id)).toBe(BigInt(commanderPolicy.rewards.normalProvenTimeoutSurvivor));expect(await accountXp(a.id)).toBe(0n);
  expect((await roomLedger(battle.battleId)).find(row=>row.playerId===a.id)?.outcome).toBe('departed');
  await service(other).current(a.id);await svc.current(b.id);expect(await roomLedger(battle.battleId)).toHaveLength(2);
 });

 it('큐에서 고정한 정책은 파일이 사라져도 원래 사건 기준으로 끝내고 조회에서 정책 부재를 구분한다',async()=>{
  const svc=service(),a=await actor(),b=await actor(1,'u02'),battle=await pair(svc,a,b);unlinkSync(policyPath());
  await naturalWin(svc,a,b,battle);expect(await accountXp(a.id)).toBe(BigInt(commanderPolicy.rewards.normalWin));
  expect(await readCommanderState(other,a.id,policy())).toMatchObject({status:'POLICY_PENDING',level:null,totalXp:null});
  restorePolicy();expect(await readCommanderState(other,a.id,policy())).toMatchObject({totalXp:String(commanderPolicy.rewards.normalWin),status:'READY'});
 });

 it.each(['null-policy','legacy-missing-pin'] as const)('%s으로 시작한 전투는 뒤늦은 설정으로 계정XP를 소급 지급하지 않는다',async mode=>{
  if(mode==='null-policy')unlinkSync(policyPath());
  const svc=service(),a=await actor(),b=await actor(1,'u02'),battle=await pair(svc,a,b);
  if(mode==='legacy-missing-pin'){
   const row=await db.roster60PvpMatch.findUniqueOrThrow({where:{id:battle.battleId}}),snapshot=JSON.parse(row.snapshot);delete snapshot.commanderPolicies;
   // Historical compatibility fixture only: omit the old plan, never alter an outcome or reward.
   await db.roster60PvpMatch.update({where:{id:row.id},data:{snapshot:JSON.stringify(snapshot)}});
  }
  restorePolicy();await naturalWin(svc,a,b,battle);
  for(const owner of [a,b])expect(await db.commanderAccount.findUnique({where:{playerId:owner.id}})).toBeNull();expect(await roomLedger(battle.battleId)).toHaveLength(0);
  await service(other).current(a.id);expect(await readCommanderState(other,a.id,policy())).toMatchObject({level:1,totalXp:'0',initialized:false});
 });

 it('같은 버전 정책의 실제 보상 변조는 계정 지문 불일치로 새 큐를 잠그기 전에 거절한다',async()=>{
  const a=await actor(),b=await actor(1,'u02');
  for(const owner of [a,b])await award(owner.id,{kind:'STORY_QUEST',eventId:commanderPolicy.story.contentVersion+':'+commanderPolicy.story.mainQuestIds[0],outcome:'completed'});
  const changed=structuredClone(commanderPolicy);changed.rewards.normalWin+=2;changed.rewards.normalLoss=Math.floor(changed.rewards.normalWin/2);changed.rewards.normalProvenTimeoutSurvivor=changed.rewards.normalWin;
  expect(()=>validateCommanderPolicy(changed)).not.toThrow();expect(commanderPolicyFingerprint(changed)).not.toBe(commanderPolicyFingerprint(commanderPolicy));writeFileSync(policyPath(),JSON.stringify(changed));
  expect(await readCommanderState(other,a.id,policy())).toMatchObject({status:'POLICY_MISMATCH',level:null});
  const svc=service();
  for(const owner of [a,b]){
   await svc.party(owner.id,owner.ids);await expect(svc.join(owner.id,{requestId:request(),mode:'normal'})).rejects.toMatchObject({code:'COMMANDER_ACCOUNT_POLICY_MISMATCH'});
   expect(await db.roster60PvpQueue.count({where:{playerId:owner.id}})).toBe(0);expect((await ownedRows(owner)).every(unit=>unit.status==='AVAILABLE')).toBe(true);
   expect(await accountXp(owner.id)).toBe(BigInt(commanderPolicy.rewards.storyQuest));expect((await db.commanderAccount.findUniqueOrThrow({where:{playerId:owner.id}})).policyFingerprint).toBe(commanderPolicyFingerprint(commanderPolicy));
   expect(await db.commanderXpLedger.count({where:{playerId:owner.id}})).toBe(1);
  }
 });

 async function http(actor:Actor|undefined,path:string,payload?:unknown){
  const response=await app!.inject({method:payload===undefined?'GET':'POST',url:'/my/api/'+path,headers:actor?.headers??{'x-gmm':'1','x-gmm-app':'1'},...(payload===undefined?{}:{payload:payload as any})});return {status:response.statusCode,body:response.json()};
 }
 async function login(species='u45',level=60):Promise<Actor>{
  const identity='commander-native-login:'+request();await game.handle(identity,'내정보');const player=await db.player.findUniqueOrThrow({where:{identity}});owners.add(player.id);
  const code=(await game.handle(identity,'웹 로그인')).text!.match(/\b\d{6}\b/)![0],signed=await http(undefined,'login',{code});expect(signed.status).toBe(200);
  return {id:player.id,ids:await units(player.id,1,species,level),headers:{'x-gmm':'1','x-gmm-app':'1','x-gmm-s':signed.body.token}};
 }
 async function httpOk(a:Actor,path:string,payload?:unknown){const response=await http(a,path,payload);expect(response.status,path+':'+JSON.stringify(response.body)).toBe(200);return response.body;}

 it('일반 로그인 native/state와 PvP options는 DB지휘관을 읽고 계정60·개체60을 각각 검사한다',async()=>{
  app=await buildApp(db,game,{secret:'commander-native-test-only-secret-over-32-characters',root,baseUrl:async()=>''});
  const a=await login(),native=await httpOk(a,'native/state'),options=await httpOk(a,'native/roster60/pvp/options');
  expect(native.commander).toMatchObject({name:'방범대장',level:1,totalXp:'0',unitXpIndependent:true});expect(options.commander).toEqual(native.commander);
  expect(options.units.find((unit:any)=>unit.ownedId===a.ids[0])).toMatchObject({level:60,rankEligible:true});
  expect(options.rankEligibility).toMatchObject({accountLevel:1,eligible:false,reason:'RANK_ACCOUNT_LEVEL_REQUIRED'});
  await httpOk(a,'native/roster60/pvp/party',{ownedIds:a.ids});
  expect((await http(a,'native/roster60/pvp/join',{requestId:request(),mode:'rank',accountLevel:60,totalXp:5900})).status).toBe(400);
  expect((await http(a,'native/roster60/pvp/join',{requestId:request(),mode:'rank'})).body.error).toBe('RANK_ACCOUNT_LEVEL_REQUIRED');
  await completedStoryBudget(a);const definition=catalog.characters.find((u:any)=>u.id==='u45');
  await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:BigInt(definition.growth[58].totalXp)}});
  expect((await httpOk(a,'native/state')).commander).toMatchObject({level:61,totalXp:'6000'});
  expect((await http(a,'native/roster60/pvp/join',{requestId:request(),mode:'rank'})).body.error).toBe('RANK_UNIT_LEVEL_REQUIRED');
  await db.roster60Owned.update({where:{id:a.ids[0]},data:{totalXp:BigInt(definition.growth[59].totalXp)}});
  const b=await login();await completedStoryBudget(b);await httpOk(b,'native/roster60/pvp/party',{ownedIds:b.ids});
  expect((await httpOk(a,'native/roster60/pvp/join',{requestId:request(),mode:'rank'})).status).toBe('waiting');
  const matched=await httpOk(b,'native/roster60/pvp/join',{requestId:request(),mode:'rank'});expect(matched.status).toBe('matched');expect(matched.battle.state.fighters[0].level).toBe(30);
  expect((await httpOk(a,'native/roster60/commander')).totalXp).toBe('6000');expect(await roomLedger(matched.battle.battleId)).toHaveLength(0);
 },30000);

 it('실제 개체 환생으로 레벨1이 되어도 방범대장 누적XP·레벨은 유지하고 랭크 개체 조건만 막는다',async()=>{
  const a=await actor(1,'u45',60);await completedStoryBudget(a);const before=await readCommanderState(other,a.id,policy()),rebirth=new RosterReincarnation(db,()=>catalog);
  const candidate=(await rebirth.preview(a.id)).candidates.find(unit=>unit.ownedId===a.ids[0]) as any;expect(candidate.eligible).toBe(true);
  await rebirth.confirm(a.id,{requestId:request(),ownedId:a.ids[0],catalogVersion:catalog.version,previewFingerprint:candidate.previewFingerprint});
  expect(await readCommanderState(other,a.id,policy())).toEqual(before);expect((await ownedRows(a))[0]).toMatchObject({totalXp:0n,reincarnation:1});
  const svc=service(),options:any=await svc.options(a.id);expect(options.commander).toMatchObject({level:61,totalXp:'6000'});expect(options.units[0]).toMatchObject({level:1,rankEligible:false});
  await svc.party(a.id,a.ids);await expect(svc.join(a.id,{requestId:request(),mode:'rank'})).rejects.toMatchObject({code:'RANK_UNIT_LEVEL_REQUIRED'});
 });
});
