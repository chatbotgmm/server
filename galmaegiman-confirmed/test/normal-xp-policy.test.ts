import {describe,it,expect} from 'vitest';
import {existsSync,readFileSync} from 'node:fs';
import {normalXpGrant,normalXpShares,normalXpSource,normalXpTerminal,prepareNormalXpPlayer,queuedNormalXpPlan,settleNormalXp,validateNormalXpPolicy,type NormalXpPolicy,type NormalXpCurrentPolicy,type NormalXpLegacyPolicy,type NormalXpMember,type NormalXpPlayerPlan} from '../src/progression/normal-xp.js';
import type {BattlePolicy} from '../src/roster60-battle.js';
import type {GrowthPoint} from '../src/battle/roster60-rules.js';

const read=(name:string)=>JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/'+name,'utf8'));
// 이미 v2로 고정된 대기·대전의 정산 규칙을 검증한다. 새 배포 정책(v3)은 test/finisher-split.test.ts에서 검증한다.
const config=JSON.parse(readFileSync('test/fixtures/normal-xp-policy-v2.json','utf8')) as NormalXpCurrentPolicy;
const battlePolicy=read('battle-policy.json') as BattlePolicy;
const catalog=read('catalog.json');
const curve:GrowthPoint[]=Array.from({length:99},(_,i)=>({totalXp:i*100,maxMp:i+10}));
const member=(startingXp='0')=>({startingXp,growth:structuredClone(curve),reincarnation:0});
const presenceProof=(departedSide:0|1=0)=>({version:'roster60-presence-proof-v1' as const,departedSide,survivorSide:(1-departedSide) as 0|1,graceStartedAt:1000,graceUntil:31000,decisionAt:31000,monitorGeneration:1});
const source=(id:string,level:number,winXp:number,lossXp:number,mode:'duel'|'story'='duel',tutorial=false)=>({id,level,winXp,lossXp,mode,tutorial,speciesId:'u07'});
const pinned=(id:string,level=1,winXp=60):NormalXpMember=>({...member(),id,playerId:'player',sourceEncounterId:'training-'+level,winXp,lossXp:20,lossFallbackUsed:false,level});
function legacyPolicy():NormalXpLegacyPolicy{
 const p:any=structuredClone(config);for(const field of ['rulesVersion','outcomePercent','distribution','poolBasis','remainder'])delete p[field];
 p.version='normal-completion-xp-test-v1';p.eligibleReason='BATTLE';p.timeout='undecided-no-award';p.disconnect='undecided-no-award';p.capOverflow='pending-policy-ledger';p.lossFallback={numerator:1,denominator:2,minimum:1};return p;
}

describe('일반게임 정상 완료 XP의 확정 v2 규칙과 고정 원천',()=>{
 it('승리100/패배50·균등분배·상한초과폐기는 확정이며 기본량 선택만 시험 제안이다',()=>{
  expect(()=>validateNormalXpPolicy(config,catalog.version)).not.toThrow();
  expect(config).toMatchObject({approved:true,rulesVersion:'normal-xp-v2',profileKind:'user-authorized-test-baseline',productionBalanceApproved:false,testValues:true,outcomePercent:{win:100,loss:50},distribution:'equal-entered-owned',poolBasis:'highest-entered-start-level',remainder:'owned-id-ascending',capOverflow:'discard-overflow'});
  expect(config.notes?.join('\n')).toContain('시험 제안');expect(config.notes?.join('\n')).toContain('최소 1 보완을 하지 않는다');
  expect(existsSync('data/roster60-normal-xp-policy.json')).toBe(false);
 });

 it('미설정·미승인·다른 카탈로그와 확정 비율/분배를 바꾼 새 정책은 거절한다',()=>{
  for(const p of [null,{...config,approved:false}])expect(()=>validateNormalXpPolicy(p,catalog.version)).toThrow('NORMAL_XP_POLICY_PENDING');
  expect(()=>validateNormalXpPolicy(config,'different-catalog')).toThrow('NORMAL_XP_POLICY_INVALID');
  const changes=[{version:''},{scope:'rank'},{source:'client-reward'},{participation:'all-owned'},{capOverflow:'automatic-bank'},{eligibleReason:'ANY_END'},{surrender:'paid'},{draw:'paid'},{timeout:'paid'},{disconnect:'paid'},{legacyRoom:'retroactive-award'},{productionBalanceApproved:true},{testValues:false},{profileKind:'unknown'},{outcomePercent:{win:100,loss:100}},{outcomePercent:{win:99,loss:50}},{distribution:'per-unit'},{poolBasis:'all-party-highest'},{remainder:'random'},{rulesVersion:'unknown'}];
  for(const change of changes)expect(()=>validateNormalXpPolicy({...config,...change} as NormalXpPolicy,catalog.version),JSON.stringify(change)).toThrow('NORMAL_XP_POLICY_INVALID');
 });

 it('v1은 새 입장 정책으로 쓰지 않고 이미 고정된 계획을 읽을 때만 허용한다',()=>{
  const old=legacyPolicy();expect(()=>validateNormalXpPolicy(old,catalog.version)).toThrow('NORMAL_XP_POLICY_PENDING');
  expect(()=>validateNormalXpPolicy(old,catalog.version,true)).not.toThrow();
  const prior={...config,version:'normal-completion-xp-test-v2',eligibleReason:'BATTLE' as const,timeout:'undecided-no-award' as const,disconnect:'undecided-no-award' as const};expect(()=>validateNormalXpPolicy(prior,catalog.version,true)).not.toThrow();expect(()=>validateNormalXpPolicy(prior,catalog.version)).toThrow('NORMAL_XP_POLICY_INVALID');
  for(const lossFallback of [{numerator:0,denominator:2,minimum:1},{numerator:1,denominator:0,minimum:1},{numerator:3,denominator:2,minimum:1},{numerator:1.5,denominator:2,minimum:1},{numerator:1,denominator:2,minimum:0}])expect(()=>validateNormalXpPolicy({...old,lossFallback},catalog.version,true)).toThrow('NORMAL_XP_POLICY_INVALID');
 });

 it('기존 duel 승리 기본량을 재사용하며 패배량은 기존 lossXp가 아닌 정확한50%다',()=>{
  for(const level of [1,5,10,20,40,60]){
   const expected=battlePolicy.encounters.find(e=>e.mode==='duel'&&e.level===level)!;
   expect(normalXpSource(battlePolicy,level,config)).toEqual({sourceEncounterId:expected.id,winXp:expected.winXp,lossXp:Math.floor(expected.winXp/2),lossFallbackUsed:false});
  }
  expect(normalXpSource(battlePolicy,99,config).sourceEncounterId).toBe('training-60');
 });

 it('가장 가까운 레벨·낮은 레벨·ID 순으로 원천을 고르고 이야기/튜토리얼은 섞지 않는다',()=>{
  const policy={...battlePolicy,encounters:[source('story',15,9999,9999,'story'),source('tutorial-duel',15,8888,8888,'duel',true),source('z-ten',10,100,30),source('a-twenty',20,200,60),source('a-ten',10,90,20)]};
  expect(normalXpSource(policy,15,config)).toEqual({sourceEncounterId:'a-ten',winXp:90,lossXp:45,lossFallbackUsed:false});
  expect(normalXpSource(policy,16,config).sourceEncounterId).toBe('a-twenty');
 });

 it('홀수50%는 정수 내림하고 기본량1의 패배0을 최소1로 부풀리지 않는다',()=>{
  const policy={...battlePolicy,encounters:[source('odd',1,5,7)]};
  expect(normalXpSource(policy,1,config)).toMatchObject({winXp:5,lossXp:2,lossFallbackUsed:false});
  policy.encounters[0].winXp=1;expect(normalXpSource(policy,1,config).lossXp).toBe(0);
  policy.encounters[0].lossXp=999;expect(normalXpSource(policy,1,config).lossXp).toBe(0);
 });

 it('빈 원천·잘못된 수량과 레벨에 하드코딩한 보상을 넣지 않는다',()=>{
  expect(()=>normalXpSource({...battlePolicy,encounters:[]},1,config)).toThrow('NORMAL_XP_SOURCE_PENDING');
  for(const winXp of [0,-1,NaN,Infinity,1.5,2_000_000_001])expect(()=>normalXpSource({...battlePolicy,encounters:[source('bad',1,winXp,1)]},1,config)).toThrow('NORMAL_XP_SOURCE_INVALID');
  for(const lossXp of [-1,NaN,Infinity,1.5])expect(()=>normalXpSource({...battlePolicy,encounters:[source('bad',1,1,lossXp)]},1,config)).toThrow('NORMAL_XP_SOURCE_INVALID');
  for(const level of [0,100,NaN,1.5])expect(()=>normalXpSource(battlePolicy,level,config)).toThrow('NORMAL_XP_LEVEL_INVALID');
 });

 it('정상승패와 별도로 증거없는timeout/disconnect·무승부·항복은0을 유지한다',()=>{
  for(const winner of [0,1] as const)expect(normalXpTerminal('BATTLE',winner)).toEqual({eligible:true,status:'APPLIED'});
  expect(normalXpTerminal('SURRENDER',0)).toEqual({eligible:false,status:'SURRENDER_ZERO'});
  expect(normalXpTerminal('BATTLE',null)).toEqual({eligible:false,status:'DRAW_POLICY_PENDING'});
  for(const reason of ['TIMEOUT','DOUBLE_TIMEOUT','DISCONNECT','MATCH_TIMEOUT','MATCH_DECLINED','CANCEL','SERVER_ABORT','UNKNOWN'])expect(normalXpTerminal(reason,0),reason).toEqual({eligible:false,status:'TERMINAL_POLICY_PENDING'});
  expect(normalXpTerminal('BATTLE',0,true)).toEqual({eligible:false,status:'TIMEOUT_RESOLUTION_POLICY_PENDING'});
 });

 it('검증된유예만료TIMEOUT과일치하는잔류승자에만이탈승리를인정한다',()=>{
  for(const departed of [0,1] as const){const proof=presenceProof(departed);expect(normalXpTerminal('TIMEOUT',proof.survivorSide,false,proof)).toEqual({eligible:true,status:'DISCONNECT_WIN',presence:proof});expect(normalXpTerminal('TIMEOUT',departed,false,proof)).toEqual({eligible:false,status:'TERMINAL_POLICY_PENDING'});}
  const proof=presenceProof();expect(normalXpTerminal('TIMEOUT',null,false,proof).eligible).toBe(false);expect(normalXpTerminal('TIMEOUT',1,true,proof)).toEqual({eligible:false,status:'TIMEOUT_RESOLUTION_POLICY_PENDING'});
  for(const reason of ['DOUBLE_TIMEOUT','SERVER_ABORT','DISCONNECT','MATCH_TIMEOUT'])expect(normalXpTerminal(reason,1,false,proof).eligible).toBe(false);
  expect(normalXpTerminal('BATTLE',1,false,proof)).toEqual({eligible:true,status:'APPLIED'});expect(normalXpTerminal('BATTLE',1,true,proof).eligible).toBe(false);
  for(const changed of [{...proof,version:'untrusted'},{...proof,survivorSide:0},{...proof,graceStartedAt:31000},{...proof,decisionAt:30999},{...proof,monitorGeneration:0},{...proof,graceUntil:NaN},null,{},'proof'])expect(normalXpTerminal('TIMEOUT',1,false,changed)).toEqual({eligible:false,status:'TERMINAL_POLICY_PENDING'});
 });

 it('잔류사람은승리100%균등·이탈자는0이며상한과기확정원장재현은그대로적용한다',async()=>{
  for(const cap of [false,true]){
   const d={...pinned('departed'),playerId:'departed-player'},a={...pinned('survivor-a',cap?59:1,61),playerId:'survivor-player',startingXp:cap?'5890':'0'},b={...pinned('survivor-b',cap?59:1,61),playerId:'survivor-player',startingXp:cap?'5800':'0'},all=[d,a,b],records=new Map<string,any>(),tx={roster60NormalXpLedger:{findUnique:async({where}:any)=>records.get(where.playerId_roomId_ownedId.ownedId)??null,create:async({data}:any)=>{records.set(data.ownedId,data);return data;}}} as any;
   const snapshot={catalogVersion:catalog.version,members:all.map(m=>({id:m.id,playerId:m.playerId,xp:m.startingXp})),normalXp:{version:'normal-xp-room-v1' as const,players:[{playerId:d.playerId,policy:config,members:[d]},{playerId:a.playerId,policy:config,members:[a,b]}]}},room={id:'presence-room',mode:'normal',player0Id:d.playerId,player1Id:a.playerId},state={entered:[[d.id],[a.id,b.id]] as [string[],string[]],parties:[[],[]] as any};
   const r=await settleNormalXp(tx,room,state,snapshot,'TIMEOUT',1,false,presenceProof()),survivor=r!.players[a.playerId],departed=r!.players[d.playerId];expect(departed).toMatchObject({status:'DISCONNECTED_ZERO',xpRequested:0,xpAwarded:0,xpDeferred:0,xpDiscarded:0});expect(survivor).toMatchObject({status:cap?'CAP_CLAMPED':'DISCONNECT_WIN',xpRequested:61,xpAwarded:cap?40:61,xpDeferred:0,xpDiscarded:cap?21:0});expect(records.get(d.id)).toMatchObject({reason:'TIMEOUT',status:'DISCONNECTED_ZERO',requestedXp:0,appliedXp:0});expect(records.get(b.id)).toMatchObject({reason:'TIMEOUT',status:'DISCONNECT_WIN',requestedXp:30,appliedXp:30});
   const before=[...records.values()].map(r=>structuredClone(r)),replay=await settleNormalXp(tx,room,state,snapshot,'TIMEOUT',1);expect([...records.values()]).toEqual(before);expect(replay!.players[a.playerId].status).toBe(survivor.status);expect(Object.values(replay!.appliedByOwnedId)).toEqual([0,0,0]);
  }
 });

 it('AI방의사람이탈은0이며가짜AI보상행이나봇이탈을가장한사람승리XP를만들지않는다',async()=>{
  for(const departed of [0,1] as const){const m=pinned('human'),records:any[]=[],tx={roster60NormalXpLedger:{findUnique:async()=>null,create:async({data}:any)=>{records.push(data);return data;}}} as any;
   const r=await settleNormalXp(tx,{id:'ai-presence',mode:'normal',player0Id:'player',player1Id:'AI:ai-presence'},{botSide:1,entered:[[m.id],[]],parties:[[],[]] as any},{catalogVersion:catalog.version,members:[{id:m.id,playerId:m.playerId,xp:m.startingXp}],normalXp:{version:'normal-xp-room-v1',players:[{playerId:m.playerId,policy:config,members:[m]}]}},'TIMEOUT',(1-departed) as 0|1,false,presenceProof(departed));
   expect(Object.keys(r!.players)).toEqual(['player']);expect(r!.players.player.xpAwarded).toBe(0);expect(r!.players.player.status).toBe(departed===0?'DISCONNECTED_ZERO':'TERMINAL_POLICY_PENDING');expect(records).toHaveLength(1);expect(records[0].playerId).toBe('player');expect(records[0].ownedId).toBe('human');
  }
 });

 it('1/2/6개 출전 개체에 계정총량을 균등하게 나누고 ownedID순 나머지만1씩 더한다',()=>{
  const all=['f','e','d','c','b','a'].map(id=>pinned(id,1,61));
  for(const count of [1,2,6])for(const outcome of ['win','loss'] as const){
   const ids=all.slice(0,count).map(m=>m.id),r=normalXpShares(all,[...ids,...ids],outcome),amounts=[...r.shares.values()];
   expect(amounts.reduce((n,v)=>n+v,0)).toBe(outcome==='win'?61:30);expect(Math.max(...amounts)-Math.min(...amounts)).toBeLessThanOrEqual(1);
   expect([...r.shares.keys()]).toEqual([...ids].sort());expect([...r.shares]).toEqual([...normalXpShares(all,[...ids].reverse(),outcome).shares]);
  }
  expect([...normalXpShares(all,all.map(m=>m.id),'win').shares.values()]).toEqual([11,10,10,10,10,10]);
  const small=all.map(m=>({...m,winXp:1}));expect([...normalXpShares(small,small.map(m=>m.id),'loss').shares.values()]).toEqual([0,0,0,0,0,0]);
 });

 it('혼합출전레벨의 최고시작레벨 원천은 시험안으로 고정하고 미출전 예비는 총량에 영향없다',()=>{
  const all=[pinned('a',1,60),pinned('b',5,300),pinned('c',10,600),pinned('unentered',60,3600)];
  const r=normalXpShares(all,['a','b','c'],'win');expect(r).toMatchObject({baseXp:600,poolXp:600,sourceEncounterId:'training-10'});expect([...r.shares.values()]).toEqual([200,200,200]);
  expect(normalXpShares(all,['a','b','c'],'loss').poolXp).toBe(300);expect(normalXpShares(all,[],'win').poolXp).toBe(0);
  expect(()=>normalXpShares(all,['foreign'],'win')).toThrow('NORMAL_XP_MEMBERS_INVALID');expect(()=>normalXpShares(all,['AI:fake'],'win')).toThrow('NORMAL_XP_MEMBERS_INVALID');
 });

 it('59→60·98→99는 상한까지 부분 지급하고 나머지는 deferred없이 버린다',()=>{
  expect(normalXpGrant(member('100'),25)).toEqual({requestedXp:25,appliedXp:25,deferredXp:0,endingXp:'125',status:'APPLIED'});
  expect(normalXpGrant(member('5880'),20)).toMatchObject({appliedXp:20,endingXp:'5900',status:'APPLIED'});
  expect(normalXpGrant(member('5890'),20)).toEqual({requestedXp:20,appliedXp:10,deferredXp:0,endingXp:'5900',status:'CAP_CLAMPED'});
  expect(normalXpGrant(member('5900'),20)).toMatchObject({appliedXp:0,deferredXp:0,endingXp:'5900',status:'CAP_CLAMPED'});
  expect(normalXpGrant(member('5900'),0)).toMatchObject({appliedXp:0,deferredXp:0,status:'APPLIED'});
  const reborn={...member('8020'),reincarnation:1,reincarnationXpPolicy:{earlyRequiredPercent:70,throughLevel:60}};
  expect(normalXpGrant(reborn,11)).toEqual({requestedXp:11,appliedXp:10,deferredXp:0,endingXp:'8030',status:'CAP_CLAMPED'});
  expect(reborn.reincarnation).toBe(1);expect(()=>normalXpGrant({...member(),growth:curve.slice(0,59)},1)).toThrow('NORMAL_XP_GROWTH_INCOMPLETE');
 });

 it('잘못된 시작XP·요청량·성장 정의를 상한폐기로 숨기거나 저장XP를 감소시키지 않는다',()=>{
  for(const startingXp of ['-1','1.5','not-xp','9007199254740992'])expect(()=>normalXpGrant(member(startingXp),1)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');
  for(const xp of [-1,1.5,NaN,Infinity,2_000_000_001])expect(()=>normalXpGrant(member(),xp)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');
  expect(()=>normalXpGrant(member('5901'),1)).toThrow('CAP_XP_POLICY_REQUIRED');
  expect(()=>normalXpGrant({...member(),growth:[]},1)).toThrow('INVALID_GROWTH_ORIGIN');
  expect(()=>normalXpGrant({...member(),growth:[{totalXp:0,maxMp:10},{totalXp:0,maxMp:10}]},1)).toThrow('INVALID_GROWTH_ORDER');
 });

 it('실제 소유·시작XP·원천·성장곡선을 입장때복사해 원천파일수정과분리한다',async()=>{
  const localCatalog=structuredClone(catalog),localBattle=structuredClone(battlePolicy),localConfig=structuredClone(config);
  const owned={id:'owned-a',playerId:'player-a',speciesId:'u01',totalXp:0n,reincarnation:0};
  const tx={roster60Owned:{findFirst:async({where}:any)=>where.id===owned.id&&where.playerId===owned.playerId?owned:null}} as any;
  const refs=[{id:owned.id,playerId:owned.playerId,xp:'0'}],plan=await prepareNormalXpPlayer(tx,owned.playerId,refs,localCatalog,localBattle,localConfig);
  expect(plan.members[0]).toMatchObject({id:'owned-a',playerId:'player-a',startingXp:'0',level:1,sourceEncounterId:'training-1',winXp:60,lossXp:30,lossFallbackUsed:false});
  const saved=JSON.stringify(plan);localConfig.notes!.push('changed');localBattle.encounters.find(e=>e.id==='training-1')!.winXp=999;localCatalog.characters.find((u:any)=>u.id==='u01').growth[1].totalXp=999;
  expect(JSON.stringify(plan)).toBe(saved);expect(queuedNormalXpPlan(saved,owned.playerId,refs)).toEqual(plan);
  await expect(prepareNormalXpPlayer(tx,owned.playerId,[{id:'foreign',playerId:'other',xp:'0'}],catalog,battlePolicy,config)).rejects.toThrow('NORMAL_XP_MEMBERS_INVALID');
  await expect(prepareNormalXpPlayer(tx,owned.playerId,[{id:'AI:fake',playerId:owned.playerId,xp:'0'}],catalog,battlePolicy,config)).rejects.toThrow('NORMAL_XP_MEMBERS_INVALID');
  await expect(prepareNormalXpPlayer(tx,owned.playerId,[...refs,...refs],catalog,battlePolicy,config)).rejects.toThrow('NORMAL_XP_MEMBERS_INVALID');
  await expect(prepareNormalXpPlayer(tx,owned.playerId,[{id:owned.id,playerId:owned.playerId,xp:'1'}],catalog,battlePolicy,config)).rejects.toThrow('UNIT_STATE_CHANGED');
 });

 it('v1/v2고정큐 모두읽되 타계정·중복·누락·변경된시작XP는 거절한다',()=>{
  const refs=[{id:'a',playerId:'player',xp:'0'},{id:'b',playerId:'player',xp:'0'}],plan:NormalXpPlayerPlan={playerId:'player',policy:config,members:[pinned('a'),pinned('b')]};
  expect(queuedNormalXpPlan(null,'player',refs)).toBeNull();expect(queuedNormalXpPlan(undefined,'player',refs)).toBeNull();
  expect(queuedNormalXpPlan(JSON.stringify({...plan,policy:legacyPolicy()}),'player',refs)?.policy.version).toContain('test-v1');
  expect(()=>queuedNormalXpPlan('{bad-json','player',refs)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');expect(()=>queuedNormalXpPlan('{}','player',refs)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');
  expect(()=>queuedNormalXpPlan(JSON.stringify(plan),'foreign',refs)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');
  for(const changed of [(()=>{const p=structuredClone(plan);p.members.pop();return p;})(),(()=>{const p=structuredClone(plan);p.members[1]=structuredClone(p.members[0]);return p;})(),(()=>{const p=structuredClone(plan);p.members[0].startingXp='1';return p;})()])expect(()=>queuedNormalXpPlan(JSON.stringify(changed),'player',refs)).toThrow('NORMAL_XP_SNAPSHOT_INVALID');
 });

 it('미정산v1도고정win원천은보존하고 새100/50·균등·상한규칙으로한번정산한다',async()=>{
  for(const winner of [0,1] as const){
   const records=new Map<string,any>(),tx={roster60NormalXpLedger:{findUnique:async({where}:any)=>records.get(where.playerId_roomId_ownedId.ownedId)??null,create:async({data}:any)=>{records.set(data.ownedId,data);return data;}}} as any;
   const a={...pinned('a',59,61),startingXp:'5890'},b={...pinned('b',59,61),startingXp:'5800'},plan:NormalXpPlayerPlan={playerId:'player',policy:legacyPolicy(),members:[a,b]},snapshot={catalogVersion:catalog.version,members:[a,b].map(m=>({id:m.id,playerId:m.playerId,xp:m.startingXp})),normalXp:{version:'normal-xp-room-v1' as const,players:[plan]}},room={id:'room',mode:'normal',player0Id:'player',player1Id:'AI:room'},state={botSide:1 as const,entered:[['b','a','a'],[]] as [string[],string[]],parties:[[],[]] as any};
   const r=await settleNormalXp(tx,room,state,snapshot,'BATTLE',winner),own=r!.players.player;
   expect(own.xpRequested).toBe(winner===0?61:30);expect(own.xpDeferred).toBe(0);expect(own.xpDiscarded).toBe(winner===0?21:5);expect(own.status).toBe('CAP_CLAMPED');expect(own.policyVersion).toBe('normal-completion-xp-test-v1:confirmed-v2');
   expect(records.get('a').requestedXp).toBe(winner===0?31:15);expect(records.get('a').appliedXp).toBe(10);expect(records.get('b').appliedXp).toBe(winner===0?30:15);
   const replay=await settleNormalXp(tx,room,state,snapshot,'BATTLE',winner);expect(replay!.players.player.xpAwarded).toBe(own.xpAwarded);expect(Object.values(replay!.appliedByOwnedId)).toEqual([0,0]);expect(records.size).toBe(2);
  }
 });

 it('이미지급된v1패배20은새50%30으로다시계산하거나추가지급하지않는다',async()=>{
  const a={...pinned('a',2,60),startingXp:'100'},old={playerId:'player',roomId:'done-v1',ownedId:'a',policyVersion:'normal-completion-xp-test-v1',outcome:'loss',reason:'BATTLE',requestedXp:20,appliedXp:20,deferredXp:0,startingXp:'100',endingXp:'120',status:'APPLIED',sourceEncounterId:'training-2'},before=structuredClone(old),tx={roster60NormalXpLedger:{findUnique:async()=>old,create:async()=>{throw Error('paid v1 record must never be rewritten');}}} as any;
  const r=await settleNormalXp(tx,{id:'done-v1',mode:'normal',player0Id:'player',player1Id:'AI:done-v1'},{botSide:1,entered:[['a'],[]],parties:[[],[]] as any},{catalogVersion:catalog.version,members:[{id:'a',playerId:'player',xp:'100'}],normalXp:{version:'normal-xp-room-v1',players:[{playerId:'player',policy:legacyPolicy(),members:[a]}]}},'BATTLE',1);
  expect(old).toEqual(before);expect(r!.players.player).toMatchObject({xpAwarded:20,xpRequested:20,xpDeferred:0,xpDiscarded:0,policyVersion:'normal-completion-xp-test-v1',rulesVersion:'legacy-record'});expect(r!.appliedByOwnedId.a).toBe(0);
 });

 it('기확정v1원장은당시허용된짧은곡선이어도새cap계산전에원문을재현한다',async()=>{
  const a={...pinned('a'),growth:curve.slice(0,3)},old={playerId:'player',roomId:'short-v1',ownedId:'a',policyVersion:'normal-completion-xp-test-v1',outcome:'win',reason:'BATTLE',requestedXp:60,appliedXp:60,deferredXp:0,startingXp:'0',endingXp:'60',status:'APPLIED',sourceEncounterId:'training-1'},before=structuredClone(old),tx={roster60NormalXpLedger:{findUnique:async()=>old,create:async()=>{throw Error('retained decision must never be rewritten');}}} as any;
  const r=await settleNormalXp(tx,{id:'short-v1',mode:'normal',player0Id:'player',player1Id:'AI:short-v1'},{botSide:1,entered:[['a'],[]],parties:[[],[]] as any},{catalogVersion:catalog.version,members:[{id:'a',playerId:'player',xp:'0'}],normalXp:{version:'normal-xp-room-v1',players:[{playerId:'player',policy:legacyPolicy(),members:[a]}]}},'BATTLE',0);
  expect(old).toEqual(before);expect(r!.players.player).toMatchObject({xpAwarded:60,xpRequested:60,xpDeferred:0,xpDiscarded:0,policyVersion:'normal-completion-xp-test-v1'});expect(r!.appliedByOwnedId.a).toBe(0);
 });

 it('이미끝난v1보류원장은그대로재현하고 보류를새지급이나은행원천으로쓰지않는다',async()=>{
  const a={...pinned('a'),startingXp:'5890'},old={playerId:'player',roomId:'old-room',ownedId:'a',policyVersion:'normal-completion-xp-test-v1',outcome:'win',reason:'BATTLE',requestedXp:60,appliedXp:0,deferredXp:60,startingXp:'5890',endingXp:'5890',status:'CAP_POLICY_PENDING',sourceEncounterId:'training-1'},before=structuredClone(old),tx={roster60NormalXpLedger:{findUnique:async()=>old,create:async()=>{throw Error('old record must never be rewritten');}}} as any;
  const r=await settleNormalXp(tx,{id:'old-room',mode:'normal',player0Id:'player',player1Id:'AI:old-room'},{botSide:1,entered:[['a'],[]],parties:[[],[]] as any},{catalogVersion:catalog.version,members:[{id:'a',playerId:'player',xp:'5890'}],normalXp:{version:'normal-xp-room-v1',players:[{playerId:'player',policy:legacyPolicy(),members:[a]}]}},'BATTLE',0);
  expect(old).toEqual(before);expect(r!.players.player).toMatchObject({status:'CAP_POLICY_PENDING',xpAwarded:0,xpDeferred:60,xpDiscarded:0,policyVersion:'normal-completion-xp-test-v1',rulesVersion:'legacy-record'});expect(r!.appliedByOwnedId.a).toBe(0);
 });
});
