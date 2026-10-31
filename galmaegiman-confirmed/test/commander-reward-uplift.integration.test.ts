import {afterAll,afterEach,beforeAll,describe,expect,it} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {awardCommanderXp,commanderPolicyFingerprint,readCommanderState,validateCommanderPolicy,type CommanderEvent} from '../src/progression/commander.js';

const suite=process.env.TEST_DB_URL?describe:describe.skip;
const base=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/commander-policy.json','utf8'));
const schedule=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/commander-reward-schedule.json','utf8'));
const oldPin=()=>validateCommanderPolicy(structuredClone(base));
const newPin=()=>validateCommanderPolicy({...structuredClone(base),rewardSchedule:structuredClone(schedule)});
const normal=(eventId:string,outcome:CommanderEvent['outcome']='win',reason='BATTLE'):CommanderEvent=>({kind:'NORMAL_PLAY',eventId,outcome,reason});

suite('commander new reward schedule on existing accounts (disposable SQLite only)',()=>{
 let db:PrismaClient,url:string;const owners=new Set<string>();
 beforeAll(()=>{url=process.env.TEST_DB_URL!;if(!/^file:\/tmp\/galmaegiman-tests-[^/]+\/test.db$/.test(url))throw Error('disposable DB only');db=new PrismaClient({datasources:{db:{url}}});});
 afterEach(async()=>{await db.player.deleteMany({where:{id:{in:[...owners]}}});owners.clear();});
 afterAll(async()=>{if(db)await db.$disconnect();});
 async function actor(){const player=await db.player.create({data:{identity:'commander-uplift:'+randomUUID()}});owners.add(player.id);return player.id;}

 it('retains an old account, pays old/new pins correctly and preserves receipts across reconnect and retries',async()=>{
  const player=await actor(),old=oldPin(),next=newPin(),oldEvent=normal(randomUUID()),nextEvent=normal(randomUUID());
  const first=await db.$transaction(tx=>awardCommanderXp(tx,player,oldEvent,old));expect(first).toMatchObject({xpAwarded:100,totalXp:'100'});
  const before=await db.commanderXpLedger.findMany({where:{playerId:player}});
  const newReward=await db.$transaction(tx=>awardCommanderXp(tx,player,nextEvent,next));expect(newReward).toMatchObject({xpAwarded:150,totalXp:'250',rewardScheduleVersion:schedule.version});
  expect(await db.$transaction(tx=>awardCommanderXp(tx,player,oldEvent,next))).toMatchObject({xpAwarded:0,recordedXp:100,totalXp:'100',replayed:true});
  expect(await db.commanderXpLedger.findUnique({where:{playerId_eventKind_eventId:{playerId:player,eventKind:oldEvent.kind,eventId:oldEvent.eventId}}})).toEqual(before[0]);
  const reconnect=new PrismaClient({datasources:{db:{url}}});
  try{
   expect(await readCommanderState(reconnect as any,player,next)).toMatchObject({totalXp:'250',level:3,status:'READY'});
   expect(await reconnect.$transaction(tx=>awardCommanderXp(tx,player,nextEvent,old))).toMatchObject({xpAwarded:0,recordedXp:150,replayed:true,rewardScheduleVersion:schedule.version});
   expect(await reconnect.$transaction(tx=>awardCommanderXp(tx,player,normal(randomUUID()),old))).toMatchObject({xpAwarded:100,totalXp:'350'});
  }finally{await reconnect.$disconnect();}
  expect(await db.commanderAccount.findUnique({where:{playerId:player}})).toMatchObject({totalXp:350n,policyVersion:old.version,policyFingerprint:commanderPolicyFingerprint(old),revision:3});
  expect(await db.commanderXpLedger.count({where:{playerId:player}})).toBe(3);
 });

 it('commits raised win and existing half-loss amounts while escape remains zero and cannot pay twice',async()=>{
  const player=await actor(),policy=newPin(),win=normal(randomUUID()),loss=normal(randomUUID(),'loss'),escape=normal(randomUUID(),'surrender','SURRENDER');
  for(const [event,amount] of [[win,150],[loss,75],[escape,0]] as const){
   const first=await db.$transaction(tx=>awardCommanderXp(tx,player,event,policy));expect(first.xpAwarded).toBe(amount);
   expect(await db.$transaction(tx=>awardCommanderXp(tx,player,event,policy))).toMatchObject({xpAwarded:0,recordedXp:amount,replayed:true});
  }
  expect(await readCommanderState(db as any,player,policy)).toMatchObject({totalXp:'225',level:3,status:'READY'});
  expect(await db.commanderXpLedger.count({where:{playerId:player}})).toBe(3);
 });
});
