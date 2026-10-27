import {describe,expect,it} from 'vitest';
import {mkdtempSync,mkdirSync,readFileSync,rmSync,writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {awardCommanderXp,commanderAccountLevelReader,commanderPolicyFingerprint,commanderEventReward,commanderLevelAt,commanderPolicyReader,commanderSigned64Maximum,readCommanderState,simulateCommanderBudget,validateCommanderPolicy,type CommanderEvent} from '../src/progression/commander.js';

const original=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/commander-policy.json','utf8'));
const story=JSON.parse(readFileSync('data/story/story-mode-v1.json','utf8'));
const config=()=>validateCommanderPolicy(structuredClone(original));
const normal=(change:Partial<CommanderEvent>={}):CommanderEvent=>({kind:'NORMAL_PLAY',eventId:'room',outcome:'win',reason:'BATTLE',...change});
const proof=()=>({version:'roster60-presence-proof-v1',departedSide:0,survivorSide:1,graceStartedAt:1_000,graceUntil:2_000,monitorGeneration:1,decisionAt:2_000});
function memory(){
 const accounts=new Map<string,any>(),ledger=new Map<string,any>();
 return {accounts,ledger,tx:{
  commanderAccount:{findUnique:async({where}:any)=>accounts.get(where.playerId)??null,upsert:async({where,create}:any)=>{if(!accounts.has(where.playerId))accounts.set(where.playerId,{...create,revision:0});return {...accounts.get(where.playerId)};},updateMany:async({where,data}:any)=>{const row=accounts.get(where.playerId);if(!row||row.revision!==where.revision||row.policyVersion!==where.policyVersion||row.totalXp!==where.totalXp)return {count:0};accounts.set(where.playerId,{...row,totalXp:data.totalXp,revision:row.revision+1});return {count:1};}},
  commanderXpLedger:{findUnique:async({where}:any)=>ledger.get(JSON.stringify(where.playerId_eventKind_eventId))??null,create:async({data}:any)=>{ledger.set(JSON.stringify({playerId:data.playerId,eventKind:data.eventKind,eventId:data.eventId}),{...data});return data;}},
 } as any};
}

describe('방범대장 계정 XP 시험 정책과 유닛 성장 분리',()=>{
 it('명시 시험 profile만 허용하고 production 승인으로 꾸민 flags를 거절한다',()=>{
  expect(config()).toMatchObject({testValues:true,productionBalanceApproved:false,initialization:'test-only-zero-no-backfill',unitXpRelationship:'independent-not-sum-not-split'});
  for(const change of [{testValues:false},{productionBalanceApproved:true},{profileKind:'approved-production'},{approved:false},{initialization:'copy-highest-unit'}])expect(()=>validateCommanderPolicy({...original,...change})).toThrow();
  expect(()=>validateCommanderPolicy(null)).toThrow();
 });
 it('설정 loader는 명시 파일만 읽고 시험 fixture로 자동 대체하지 않는다',()=>{
  const root=mkdtempSync(join(tmpdir(),'commander-policy-'));
  try{
   expect(commanderPolicyReader(root)()).toBeNull();expect(()=>commanderPolicyReader(root,'other-profile')).toThrow('UNKNOWN_ROSTER60_PROFILE');
   const directory=join(root,'data/test-profiles/five-stat-device-test-v1');mkdirSync(directory,{recursive:true});writeFileSync(join(directory,'commander-policy.json'),JSON.stringify(original));
   expect(commanderPolicyReader(root)()).toBeNull();expect(commanderPolicyReader(root,'five-stat-device-test-v1')()).toMatchObject({version:original.version,testValues:true});
   writeFileSync(join(directory,'commander-policy.json'),'{broken');expect(()=>commanderPolicyReader(root,'five-stat-device-test-v1')()).toThrow();
  }finally{rmSync(root,{recursive:true,force:true});}
 });
 it('증가하지 않는 곡선·비정수 XP·잘못된 보상과 중복 의뢰를 거절한다',()=>{
  for(const mutate of [(p:any)=>p.growth[0].totalXp=1,(p:any)=>p.growth[5].totalXp=400,(p:any)=>p.growth[1].level=4,(p:any)=>p.rewards.normalWin=-1,(p:any)=>p.rewards.normalLoss=0.5,(p:any)=>p.story.residentQuestIds.push(p.story.mainQuestIds[0])]){const p=structuredClone(original);mutate(p);expect(()=>validateCommanderPolicy(p)).toThrow();}
 });
 it('일반전 계정 XP도 패배 50%와 증명된 이탈 승리 100% 비율을 강제한다',()=>{
  for(const change of [{normalLoss:51},{normalProvenTimeoutSurvivor:99}]){const p=config();Object.assign(p.rewards,change);expect(()=>validateCommanderPolicy(p)).toThrow();}
  const odd=config();Object.assign(odd.rewards,{normalWin:101,normalLoss:50,normalProvenTimeoutSurvivor:101});expect(validateCommanderPolicy(odd)).toBe(odd);
 });
 it('정책 지문은 객체 키·설명 메모 순서를 제외하고 실제 곡선·보상 의미를 고정한다',()=>{
  const p=config(),reordered=Object.fromEntries(Object.entries(p).reverse()) as typeof p;
  reordered.rewards=Object.fromEntries(Object.entries(p.rewards).reverse()) as typeof p.rewards;reordered.notes=[...(p.notes??[])].reverse();
  reordered.growth=p.growth.map(point=>({totalXp:point.totalXp,level:point.level}));reordered.story={...p.story,mainQuestIds:[...p.story.mainQuestIds].reverse()};
  expect(commanderPolicyFingerprint(reordered)).toBe(commanderPolicyFingerprint(p));
  for(const mutate of [(value:typeof p)=>value.growth[1].totalXp=110,(value:typeof p)=>{value.rewards.normalWin++;value.rewards.normalProvenTimeoutSurvivor++;}]){const changed=structuredClone(p);mutate(changed);expect(changed.version).toBe(p.version);expect(commanderPolicyFingerprint(changed)).not.toBe(commanderPolicyFingerprint(p));}
 });
 it('계정 59/60 경계는 유닛 레벨과 독립이며 마지막 곡선 뒤 누적 XP를 폐기하지 않는다',()=>{
  const p=config();expect(commanderLevelAt(0n,p)).toMatchObject({level:1,xpToNext:'100'});expect(commanderLevelAt(5899n,p).level).toBe(59);expect(commanderLevelAt(5900n,p).level).toBe(60);expect(commanderLevelAt(6000n,p).level).toBe(61);
  expect(commanderLevelAt(11_000n,p)).toMatchObject({level:99,nextLevelXp:null,xpToNext:null,status:'CURVE_EXTENSION_PENDING'});
  expect(()=>commanderLevelAt(-1n,p)).toThrow();expect(()=>commanderLevelAt(commanderSigned64Maximum+1n,p)).toThrow();
 });
 it('실제 21개 본편·4개 주민 의뢰와 필수 연결 모험 예산으로 60을 넘고 본편만으로는 넘지 않는다',()=>{
  const p=config(),main=story.quests.filter((q:any)=>q.kind==='main'),side=story.quests.filter((q:any)=>q.kind==='resident');
  expect(p.story.mainQuestIds).toEqual(main.map((q:any)=>q.id));expect(p.story.residentQuestIds).toEqual(side.map((q:any)=>q.id));expect(main).toHaveLength(21);expect(side).toHaveLength(4);
  const requiredReturns=story.quests.filter((q:any)=>q.objective.encounterIds.some((id:string)=>story.encounters.some((e:any)=>e.id===id&&e.mode==='adventure'))).length;
  expect(requiredReturns).toBeGreaterThan(0);expect(simulateCommanderBudget(p,requiredReturns)).toMatchObject({mainQuestCount:21,residentQuestCount:4,mainXp:4200,residentXp:1800,totalXp:6000,mainLevel:43,totalLevel:61,questLinkedAdventureXp:0,mainOnlyBelow60:true,completeBudgetReaches60:true});
 });
 it('본편·주민 보상은 해당 contentVersion과 실제 의뢰 ID에만 연결된다',()=>{
  const p=config(),quest:CommanderEvent={kind:'STORY_QUEST',eventId:p.story.contentVersion+':STORY_01',outcome:'completed'};
  expect(commanderEventReward(quest,p).requestedXp).toBe(p.rewards.storyQuest);
  expect(commanderEventReward({...quest,kind:'RESIDENT_QUEST',eventId:p.story.contentVersion+':RESIDENT_BLACKSMITH_01'},p).requestedXp).toBe(p.rewards.residentQuest);
  for(const change of [{eventId:'STORY_01'},{eventId:p.story.contentVersion+':STORY_99'},{kind:'RESIDENT_QUEST' as const}])expect(()=>commanderEventReward({...quest,...change},p)).toThrow();
 });
 it('독립 모험의 승리 귀환만 지급하고 의뢰 연결 모험은 같은 플레이의 이중 보상이 아니다',()=>{
  const p=config(),event:CommanderEvent={kind:'ADVENTURE_RETURN',eventId:'run',outcome:'completed',reason:'WIN_RETURN'};
  expect(commanderEventReward(event,p).requestedXp).toBe(p.rewards.independentAdventureReturn);
  expect(commanderEventReward({...event,linkedQuestId:'STORY_01'},p)).toEqual({requestedXp:0,reason:'QUEST_LINKED_ADVENTURE_ZERO'});
  for(const reason of ['NO_CLEAR_RETURN','SURRENDER','SERVER_ABORT'])expect(commanderEventReward({...event,reason},p).requestedXp).toBe(0);
 });
 it('정상 일반게임 승패는 계정 사건 보상이며 개체 XP 합산·참가 수 분할 입력을 받지 않는다',()=>{
  const p=config();expect(commanderEventReward(normal(),p).requestedXp).toBe(p.rewards.normalWin);expect(commanderEventReward(normal({outcome:'loss'}),p).requestedXp).toBe(p.rewards.normalLoss);
  for(const extra of [{amount:99999},{unitXp:99999},{participantCount:6}])expect(()=>commanderEventReward({...normal(),...extra} as CommanderEvent,p)).toThrow();
 });
 it('도주·항복·무승부·서버 중단·자동 턴 KO는 계정 XP 0이다',()=>{
  const p=config();for(const event of [normal({reason:'SURRENDER'}),normal({outcome:'surrender'}),normal({outcome:'draw'}),normal({reason:'SERVER_ABORT'}),normal({timeoutGenerated:true}),normal({reason:'TIMEOUT',side:1})])expect(commanderEventReward(event,p).requestedXp).toBe(0);
 });
 it('증명된 유예 종료의 잔류 승리만 지급하고 이탈자·잘못된 증명에는 지급하지 않는다',()=>{
  const p=config(),event=normal({reason:'TIMEOUT',side:1,terminationProof:proof()});expect(commanderEventReward(event,p).requestedXp).toBe(p.rewards.normalProvenTimeoutSurvivor);
  for(const change of [{side:0 as const},{outcome:'departed' as const},{terminationProof:{...proof(),decisionAt:1999}},{terminationProof:{...proof(),survivorSide:0}}])expect(commanderEventReward({...event,...change},p).requestedXp).toBe(0);
 });
 it('정책 미설정에서는 null 레벨과 0 보상만 반환하고 계정·원장을 만들지 않는다',async()=>{
  const {tx,accounts,ledger}=memory();expect(await readCommanderState(tx,'owner',null)).toMatchObject({level:null,totalXp:null,status:'POLICY_PENDING'});expect(await commanderAccountLevelReader(()=>null)(tx,'owner')).toBeNull();
  expect(await awardCommanderXp(tx,'owner',normal(),null)).toMatchObject({status:'POLICY_PENDING',xpAwarded:0,level:null,totalXp:null});expect(accounts.size).toBe(0);expect(ledger.size).toBe(0);
 });
 it('미생성 계정의 XP0·Lv1은 시험 설정에서만 읽으며 읽기만으로 저장하지 않는다',async()=>{
  const {tx,accounts}=memory();expect(await readCommanderState(tx,'owner',config())).toMatchObject({level:1,totalXp:'0',initialized:false,initialization:'TEST_ZERO_NO_BACKFILL',testValues:true});expect(accounts.size).toBe(0);
 });
 it('같은 사건은 정책 변경·재조회·proof 관찰 시각 변화에도 한번만 지급한다',async()=>{
  const {tx,accounts,ledger}=memory(),event=normal({reason:'TIMEOUT',side:1,terminationProof:proof()}),first=await awardCommanderXp(tx,'owner',event,config());
  expect(first).toMatchObject({xpAwarded:100,recordedXp:100,totalXp:'100',replayed:false});
  const replay=await awardCommanderXp(tx,'owner',{...event,terminationProof:{...proof(),decisionAt:3000}},null);expect(replay).toMatchObject({xpAwarded:0,recordedXp:100,totalXp:'100',replayed:true});expect(accounts.get('owner').totalXp).toBe(100n);expect(ledger.size).toBe(1);
  await expect(awardCommanderXp(tx,'owner',{...event,outcome:'loss'},config())).rejects.toMatchObject({code:'COMMANDER_EVENT_CONFLICT'});
 });
 it('서로 다른 계정·서로 다른 사건은 독립이며 합계는 보존하고 마지막 곡선 이후도 누적한다',async()=>{
  const {tx,accounts}=memory(),p=config();await awardCommanderXp(tx,'a',normal(),p);await awardCommanderXp(tx,'b',normal(),p);await awardCommanderXp(tx,'a',normal({eventId:'room2',outcome:'loss'}),p);
  expect(accounts.get('a').totalXp).toBe(150n);expect(accounts.get('b').totalXp).toBe(100n);
  accounts.set('a',{...accounts.get('a'),totalXp:10_000n});const r=await awardCommanderXp(tx,'a',normal({eventId:'room3'}),p);expect(r.totalXp).toBe('10100');expect(r.commander).toMatchObject({level:99,status:'CURVE_EXTENSION_PENDING',nextLevelXp:null});
 });
 it('정책 불일치·손상·signed64 저장 한도는 자동 고침이나 유닛 추정 없이 보류한다',async()=>{
  const {tx,accounts}=memory(),p=config();accounts.set('owner',{totalXp:5900n,policyVersion:'old-policy',policyFingerprint:commanderPolicyFingerprint(p),revision:1});expect(await readCommanderState(tx,'owner',p)).toMatchObject({level:null,status:'POLICY_MISMATCH'});expect(await commanderAccountLevelReader(()=>p)(tx,'owner')).toBeNull();await expect(awardCommanderXp(tx,'owner',normal(),p)).rejects.toMatchObject({code:'COMMANDER_ACCOUNT_POLICY_MISMATCH'});
  accounts.set('owner',{totalXp:commanderSigned64Maximum,policyVersion:p.version,policyFingerprint:commanderPolicyFingerprint(p),revision:1});await expect(awardCommanderXp(tx,'owner',normal(),p)).rejects.toMatchObject({code:'COMMANDER_XP_STORAGE_LIMIT'});
  accounts.set('owner',{totalXp:-1n,policyVersion:p.version,policyFingerprint:commanderPolicyFingerprint(p),revision:1});expect(await readCommanderState(tx,'owner',p)).toMatchObject({level:null,status:'DATA_INVALID'});
 });
 it('같은 버전의 곡선·보상 변경도 읽기와 지급에서 보류하며 지문을 공개하지 않는다',async()=>{
  const {tx,accounts,ledger}=memory(),p=config(),first=await awardCommanderXp(tx,'owner',normal(),p);expect(JSON.stringify(first)).not.toContain('policyFingerprint');
  expect([...ledger.values()][0].policyFingerprint).toBe(commanderPolicyFingerprint(p));
  for(const mutate of [(value:typeof p)=>value.growth[1].totalXp=110,(value:typeof p)=>{value.rewards.normalWin++;value.rewards.normalProvenTimeoutSurvivor++;}]){
   const changed=structuredClone(p);mutate(changed);expect(await readCommanderState(tx,'owner',changed)).toMatchObject({level:null,status:'POLICY_MISMATCH'});expect(await commanderAccountLevelReader(()=>changed)(tx,'owner')).toBeNull();
   await expect(awardCommanderXp(tx,'owner',normal({eventId:'different-room'}),changed)).rejects.toMatchObject({code:'COMMANDER_ACCOUNT_POLICY_MISMATCH'});
  }
  expect(accounts.get('owner').totalXp).toBe(100n);expect(ledger.size).toBe(1);
 });
});
