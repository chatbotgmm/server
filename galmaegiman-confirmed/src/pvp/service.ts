// Draft target: src/pvp/service.ts. Requires generated Prisma models and battle adapter.
import {createHash,createHmac,randomBytes,randomInt,timingSafeEqual} from 'node:crypto';
import type {Prisma,PrismaClient,RealtimeAttempt,RealtimeRoom} from '@prisma/client';
import type {Content} from '../content.js';
import {topTiers} from '../content.js';
import {GameError} from '../types.js';
import {loadBattleRules} from '../battle/load.js';
import {createBattle,type Battle} from '../battle/engine.js';
import type {UnitRecord} from '../battle/growth.js';
import {loadStory} from '../battle/story.js';
import {casualRecord,matchCasualBot,applyCasualCompensation,deployedMean} from './casual-balance.js';
import {serializeBattle,deserializeBattle,required,validateCommand,resolvePhase,automaticCommand,rerollCommand,itemRequirement,consumedItemSides,type Command} from '../battle/realtime-adapter.js';
import {registerRankedMatch,finishAndSettleRankedMatch,matchmakingRating,settleCasualHuman,rankSummary} from '../ranked/settlement.js';
import {DEFAULT_RANKED_CONFIG} from '../ranked/rating.js';
import {createRealtimeNativeMapper,mapRealtimePreparationCandidates} from './realtime-native-view.js';

import {chooseBotCommand} from '../battle/realtime-bot.js';

import {holdItem,consumePhaseItems,refundRoomItems,inventoryForPlayer,type ItemHold} from './item-escrow.js';

// SQLite has one writer. Queue this client's realtime transactions before opening
// them so concurrent BEGIN/write-lock waiters cannot starve the engine workers.
// The database lock below remains authoritative across clients/processes.
const realtimeTransactionTails=new WeakMap<PrismaClient,Promise<void>>();

type Tx=Prisma.TransactionClient;
type Side=0|1;
export type Mode='normal'|'rank';
type Preparation={teams:[UnitRecord[],UnitRecord[]];ready:[boolean,boolean];available:[Array<{id:string;name:string;rarity:string;image:string}>,Array<{id:string;name:string;rarity:string;image:string}>]};
type PrepareCommand={prepare:{team:string[];ready:boolean}};
type Acceptance={accepted:[boolean,boolean];attempts:[string,string|null]};
type State={acceptance?:Acceptance;botSide?:1;preparation?:Preparation;rulesVersion:string;battle:string;phase:number;pending:[Command|null,Command|null];automatic:[boolean,boolean];misses:[number,number]};
type EndReason='MATCH_DECLINED'|'MATCH_TIMEOUT'|'BATTLE'|'SURRENDER'|'TIMEOUT'|'DOUBLE_TIMEOUT'|'SERVER_ABORT';
type End={winner:Side|null;reason:EndReason;settlement:'settled'|'not_applicable';receipt?:unknown;requeuedSides?:Side[];returnedAttempts?:[string,string|null]};
export const REALTIME_LIMITS={acceptMs:10_000,casualAiAfterMs:30_000,maxMatchesPerPass:50,turnMs:30_000,attemptMs:120_000,queueLeaseMs:45_000,legacyLeaseMs:2*3600_000,maxWaiting:2000,maxRooms:1000,maxRequestsPerRoom:2000,roomMaxMs:2*3600_000};
const sides=[0,1] as const;
const id=()=>randomBytes(18).toString('base64url');
function fail(message:string):never{throw new GameError(message);}
const parseState=(r:RealtimeRoom)=>JSON.parse(r.stateJson) as State;
const canonical=(x:unknown):string=>{
  if(Array.isArray(x))return '['+x.map(canonical).join(',')+']';
  if(x&&typeof x==='object')return '{'+Object.entries(x).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>JSON.stringify(k)+':'+canonical(v)).join(',')+'}';
  return JSON.stringify(x)??'null';
};

export class RealtimePvpService {
  private R:ReturnType<typeof loadBattleRules>;private mapper:ReturnType<typeof createRealtimeNativeMapper>;private timer:ReturnType<typeof setInterval>|undefined;private ticking=false;private work:Promise<void>|undefined;
  private rulesVersion:string;private casualRulesVersion:string;private startLevel:number;
  constructor(private db:PrismaClient,private content:Content,root:string,private secret:string,private now=()=>Date.now()){
    this.R=loadBattleRules(root);this.startLevel=loadStory(root,this.R).start.level;this.mapper=createRealtimeNativeMapper(root);
    this.rulesVersion=createHash('sha256').update('realtime-engine-v2-preparation-items\0').update(JSON.stringify(this.R.data)).digest('hex');
    this.casualRulesVersion=createHash('sha256').update('realtime-engine-v3-casual-growth\0').update(JSON.stringify(this.R.data)).digest('hex');
  }
  private version(mode:string){return mode==='normal'?this.casualRulesVersion:this.rulesVersion;}
  start(onError:(e:unknown)=>void){if(this.timer)return;this.timer=setInterval(()=>{if(this.ticking)return;this.ticking=true;this.work=this.tick().catch(onError).finally(()=>{this.ticking=false;});},1000);this.timer.unref();}
  async stop(){if(this.timer)clearInterval(this.timer);this.timer=undefined;await this.work;}
  // A write acquired before reads serializes the queue/room transaction across processes.
  // SQLite busy retries restart the WHOLE transaction, never only one LP update.
  private async tx<T>(run:(tx:Tx)=>Promise<T>):Promise<T>{
    const previous=realtimeTransactionTails.get(this.db)??Promise.resolve();
    let release!:()=>void;
    const tail=new Promise<void>(resolve=>{release=resolve;});
    realtimeTransactionTails.set(this.db,tail);
    await previous;
    try{return await this.transactionWithRetry(run);}
    finally{
      release();
      if(realtimeTransactionTails.get(this.db)===tail)realtimeTransactionTails.delete(this.db);
    }
  }
  private async transactionWithRetry<T>(run:(tx:Tx)=>Promise<T>):Promise<T>{
    for(let retry=0;;retry++)try{return await this.db.$transaction(async tx=>{
      await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
      return run(tx);
    },{maxWait:5000,timeout:15000});}catch(e){
      const code=(e as {code?:string}).code;
      if(retry>=3||!['P2034','P1008','P2028','P2002','RANKED_CONCURRENT_WRITE'].includes(code??''))throw e;
      await new Promise(resolve=>setTimeout(resolve,20*(retry+1)));
    }
  }
  private token(a:RealtimeAttempt){return createHmac('sha256',this.secret).update('realtime-attempt-v1\0'+a.id+'\0'+a.playerId+'\0'+a.mode+'\0'+a.joinNotAfter.getTime()).digest('base64url');}
  private async ownedAttempt(tx:Tx,playerId:string,attemptId:string,token:string){
    const a=await tx.realtimeAttempt.findUnique({where:{id:attemptId}});
    if(!a||a.playerId!==playerId)return fail('매칭 요청이 없어요. 다시 시작해 주세요.');
    const expected=Buffer.from(this.token(a)),given=Buffer.from(token);
    if(given.length!==expected.length||!timingSafeEqual(given,expected))fail('매칭 요청을 확인할 수 없어요.');
    return a;
  }
  private queueView(a:RealtimeAttempt|null){return a?{status:({ISSUED:'idle',WAITING:'waiting',MATCHED:'matched',CANCELLED:'cancelled',EXPIRED:'expired',UPGRADE_REQUIRED:'cancelled'} as Record<string,string>)[a.state],mode:a.mode,attemptId:a.id,token:this.token(a),joinNotAfter:a.joinNotAfter.getTime(),queuedAt:a.queuedAt?.getTime()??null,...(a.state==='UPGRADE_REQUIRED'?{notice:'대전 수락 기능을 사용하려면 앱을 업데이트해 주세요.'}:{}),...(a.roomId?{roomId:a.roomId}:{}),serverNow:this.now()}:{status:'idle',serverNow:this.now()};}
  private async activity(tx:Tx,playerId:string){
    let a=await tx.realtimeActivity.findUnique({where:{playerId}});
    if(a&&a.kind!=='ROOM'&&a.expiresAt.getTime()<=this.now()){
      if(a.kind==='QUEUE')await tx.realtimeAttempt.updateMany({where:{id:a.targetId,state:'WAITING'},data:{state:'EXPIRED'}});
      await tx.realtimeActivity.delete({where:{playerId}});a=null;
    }
    return a;
  }
  async attempt(playerId:string,mode:Mode){return this.tx(async tx=>{
    if(!await tx.player.findUnique({where:{id:playerId},select:{id:true}}))fail('로그인을 다시 해 주세요.');
    const active=await this.activity(tx,playerId);
    if(active)fail('진행 중인 전투나 매칭으로 돌아가 주세요.');
    // Bound inert requests; replace only unqueued attempts owned by this user.
    await tx.realtimeAttempt.updateMany({where:{playerId,state:'ISSUED'},data:{state:'EXPIRED'}});
    const a=await tx.realtimeAttempt.create({data:{id:id(),playerId,mode,state:'ISSUED',rosterJson:JSON.stringify({protocol:3}),issuedAt:new Date(this.now()),joinNotAfter:new Date(this.now()+REALTIME_LIMITS.attemptMs)}});
    return {attemptId:a.id,token:this.token(a),joinNotAfter:a.joinNotAfter.getTime(),serverNow:this.now()};
  });}
  private async roster(tx:Tx,playerId:string,selection?:string[],mode:Mode='rank'):Promise<{units:UnitRecord[];name:string;ids:string[]}>{
    const p=await tx.player.findUniqueOrThrow({where:{id:playerId}});
    const previous=selection?null:await tx.realtimeLineup.findUnique({where:{playerId}});
    const ids:string[]=selection??(previous?JSON.parse(previous.teamJson):p.battleTeam.split(','));
    const fallback=()=>{if(previous)return this.roster(tx,playerId,p.battleTeam.split(','),mode);return fail('전투 편성에서 소유한 최상위 갈매미 세 마리를 정해 주세요.');};
    if(!Array.isArray(ids)||ids.length!==3||new Set(ids).size!==3)return fallback();
    const own=await tx.ownedCharacter.findMany({where:{playerId,status:{in:['AVAILABLE','EXPEDITION']}},select:{id:true,characterId:true},orderBy:{id:'asc'}});
    // Species IDs from older clients resolve deterministically, never to account maximum.
    const selected=ids.map(key=>own.find(x=>`o${x.id}`===key)||own.find(x=>x.characterId===key));
    if(selected.some(x=>!x||!topTiers.has(this.content.map.get(x.characterId)?.rarity??''))||new Set(selected.map(x=>x?.characterId)).size!==3)return fallback();
    let saved:{units?:Record<string,unknown>}={};
    if(mode==='normal'){
      const row=await tx.storySave.findUnique({where:{playerId}});
      try{saved=row?JSON.parse(row.data):{};}catch{fail('저장된 갈매미 성장 정보를 확인해 주세요.');}
      if(!saved||typeof saved!=='object'||(saved.units!==undefined&&(!saved.units||typeof saved.units!=='object'||Array.isArray(saved.units))))fail('저장된 갈매미 성장 정보를 확인해 주세요.');
    }
    const units=selected.map((owned):UnitRecord=>{
      const x=owned!,c=this.content.map.get(x.characterId)!;
      const base={uid:mode==='normal'?`o${x.id}`:id(),id:c.id,name:c.name,tier:c.rarity as UnitRecord['tier']};
      return mode==='normal'?casualRecord(this.R,base,saved.units?.[base.uid]??saved.units?.[c.id],this.startLevel):{...base,level:this.content.economy.pvp.level,xp:0,stage:0,evoAugs:[]};
    });
    return {units,name:p.nickname??`갈매미 #${p.id.slice(0,4)}`,ids:units.map(x=>x.id)};
  }
  async join(playerId:string,attemptId:string,token:string){return this.tx(async tx=>{
    let a=await this.ownedAttempt(tx,playerId,attemptId,token);
    if(a.state!=='ISSUED')return this.queueView(a);
    if(!this.hasProtocol(a))return this.queueView(await this.rejectLegacyAttempt(tx,a));
    if(a.joinNotAfter.getTime()<=this.now()){a=await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'EXPIRED'}});return this.queueView(a);}
    if(await this.activity(tx,playerId))fail('진행 중인 전투나 매칭으로 돌아가 주세요.');
    if(await tx.realtimeAttempt.count({where:{state:'WAITING'}})>=REALTIME_LIMITS.maxWaiting)fail('매칭 요청이 많아요. 잠시 뒤 다시 시도해 주세요.');
    const roster=await this.roster(tx,playerId,undefined,a.mode as Mode),rating=await matchmakingRating(tx,playerId,a.mode==='rank'?'ranked':'normal');
    const profile=a.mode==='rank'?await tx.rankedProfile.findUniqueOrThrow({where:{playerId}}):null;
    const leaseUntil=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);
    a=await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'WAITING',queuedAt:new Date(this.now()),leaseUntil,rosterJson:JSON.stringify({protocol:3,units:roster.units}),playerName:roster.name,rating:Math.round(rating),tierIndex:profile?.tierIndex??0}});
    await tx.realtimeActivity.create({data:{playerId,kind:'QUEUE',targetId:a.id,expiresAt:leaseUntil}});
    await this.match(tx,a.mode as Mode);
    return this.queueView(await tx.realtimeAttempt.findUniqueOrThrow({where:{id:a.id}}));
  });}
  async cancel(playerId:string,attemptId:string,token:string,_requestId:string){return this.tx(async tx=>{
    let a=await this.ownedAttempt(tx,playerId,attemptId,token);
    if(a.state==='ISSUED'||a.state==='WAITING'){
      a=await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'CANCELLED'}});
      await tx.realtimeActivity.deleteMany({where:{playerId,kind:'QUEUE',targetId:a.id}});
    }
    return this.queueView(a);
  });}
  async currentQueue(playerId:string){return this.tx(async tx=>{
    const activity=await this.activity(tx,playerId);
    if(!activity){const last=await tx.realtimeAttempt.findFirst({where:{playerId},orderBy:{issuedAt:'desc'}});return this.queueView(last?.state==='UPGRADE_REQUIRED'?last:null);}
    if(activity.kind.startsWith('LEGACY_'))return this.queueView(null);
    const a=await tx.realtimeAttempt.findFirst({where:{playerId,...(activity.kind==='ROOM'?{roomId:activity.targetId}:{id:activity.targetId})},orderBy:{issuedAt:'desc'}});
    if(a?.state==='WAITING'){
      const leaseUntil=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);
      await tx.realtimeAttempt.update({where:{id:a.id},data:{leaseUntil}});
      await tx.realtimeActivity.update({where:{playerId},data:{expiresAt:leaseUntil}});
      await this.match(tx,a.mode as Mode);
      return this.queueView(await tx.realtimeAttempt.findUniqueOrThrow({where:{id:a.id}}));
    }
    return this.queueView(a);
  });}
  private async match(tx:Tx,mode:Mode){
    const candidates=await tx.realtimeAttempt.findMany({where:{state:'WAITING',mode,leaseUntil:{gt:new Date(this.now())}},orderBy:[{queuedAt:'asc'},{id:'asc'}],take:REALTIME_LIMITS.maxWaiting});
    // Count only live queue ownership, not online users, expired leases or orphan attempts.
    const activities=await tx.realtimeActivity.findMany({where:{kind:'QUEUE',targetId:{in:candidates.map(x=>x.id)},expiresAt:{gt:new Date(this.now())}}});
    const active=new Set(activities.map(x=>x.playerId+'\0'+x.targetId));
    for(const a of candidates)if(!this.hasProtocol(a))await this.rejectLegacyAttempt(tx,a);
    const rows=candidates.filter(x=>this.hasProtocol(x)&&active.has(x.playerId+'\0'+x.id));
    const used=new Set<string>();let created=0;
    for(const a of rows){
      if(used.has(a.id))continue;
      const gap=(x:RealtimeAttempt)=>Math.min(1200,100+Math.floor((this.now()-x.queuedAt!.getTime())/5000)*25);
      const tierGap=(x:RealtimeAttempt)=>Math.floor((this.now()-x.queuedAt!.getTime())/30000);
      const b=rows.filter(x=>x.id!==a.id&&x.playerId!==a.playerId&&!used.has(x.id)&&Math.abs(a.rating-x.rating)<=Math.max(gap(a),gap(x))&&(mode!=='rank'||Math.abs(a.tierIndex-x.tierIndex)<=Math.max(tierGap(a),tierGap(x))))
        .sort((x,y)=>Math.abs(a.rating-x.rating)-Math.abs(a.rating-y.rating))[0];
      if(!b)continue;
      if(created>=REALTIME_LIMITS.maxMatchesPerPass)return;
      if(await tx.realtimeRoom.count({where:{state:'ACTIVE'}})>=REALTIME_LIMITS.maxRooms)return;
      const roomId=id(),battle=createBattle(this.R,this.attemptUnits(a),this.attemptUnits(b),randomInt(2**31-1));
      const state:State={acceptance:{accepted:[false,false],attempts:[a.id,b.id]},preparation:{teams:[this.attemptUnits(a),this.attemptUnits(b)],ready:[false,false],available:[await this.preparationOptions(tx,a.playerId,mode),await this.preparationOptions(tx,b.playerId,mode)]},rulesVersion:this.version(mode),battle:serializeBattle(battle),phase:0,pending:[null,null],automatic:[false,false],misses:[0,0]};
      await tx.realtimeRoom.create({data:{id:roomId,mode,player0Id:a.playerId,player1Id:b.playerId,namesJson:JSON.stringify([a.playerName,b.playerName]),stateJson:JSON.stringify(state),deadline:new Date(this.now()+REALTIME_LIMITS.acceptMs)}});
      for(const x of [a,b]){
        await tx.realtimeAttempt.update({where:{id:x.id},data:{state:'MATCHED',roomId}});
        await tx.realtimeActivity.update({where:{playerId:x.playerId},data:{kind:'ROOM',targetId:roomId,expiresAt:new Date(this.now()+REALTIME_LIMITS.roomMaxMs)}});
        used.add(x.id);
      }
      created++;
    }
    // Human matching always runs first under the same writer lock. Ranked NEVER falls back.
    if(mode!=='normal')return;
    // Evaluate population AFTER human pairs leave the queue, under this same lock.
    // The remaining person's original 30-second wait is retained, never restarted.
    const remaining=rows.filter(x=>!used.has(x.id));
    if(new Set(remaining.map(x=>x.playerId)).size!==1)return;
    for(const a of remaining){
      if(created>=REALTIME_LIMITS.maxMatchesPerPass)return;
      if(used.has(a.id)||!a.queuedAt||this.now()-a.queuedAt.getTime()<REALTIME_LIMITS.casualAiAfterMs)continue;
      if(await tx.realtimeRoom.count({where:{state:'ACTIVE'}})>=REALTIME_LIMITS.maxRooms)return;
      await this.createCasualBotRoom(tx,a);created++;
    }
  }
  private async createCasualBotRoom(tx:Tx,a:RealtimeAttempt){
    if(a.mode!=='normal'||a.state!=='WAITING'||!a.queuedAt||this.now()-a.queuedAt.getTime()<REALTIME_LIMITS.casualAiAfterMs||!a.leaseUntil||a.leaseUntil.getTime()<=this.now())fail('AI 대체 매칭은 대기 중인 일반전에만 사용할 수 있어요.');
    const roomId=id(),human=this.attemptUnits(a);
    const pool=this.content.characters.filter(x=>topTiers.has(x.rarity));
    if(pool.length<3)fail('AI 편성을 준비할 수 없어요.');
    const chosen=[] as typeof pool;
    while(chosen.length<3)chosen.push(pool.splice(randomInt(pool.length),1)[0]);
    const bot=matchCasualBot(this.R,human,chosen.map((x):UnitRecord=>({uid:id(),id:x.id,name:x.name,tier:x.rarity as UnitRecord['tier'],level:this.content.economy.pvp.level,xp:0,stage:0,evoAugs:[]})));
    const battle=createBattle(this.R,human,bot,randomInt(2**31-1));
    const state:State={acceptance:{accepted:[false,true],attempts:[a.id,null]},botSide:1,preparation:{teams:[human,bot],ready:[false,true],available:[await this.preparationOptions(tx,a.playerId,'normal'),[]]},rulesVersion:this.version('normal'),battle:serializeBattle(battle),phase:0,pending:[null,null],automatic:[false,false],misses:[0,0]};
    // No synthetic Player, rating profile, queue entry or inventory is created for the AI.
    await tx.realtimeRoom.create({data:{id:roomId,mode:'normal',player0Id:a.playerId,player1Id:'AI:'+roomId,namesJson:JSON.stringify([a.playerName,'AI 갈매미 · 일반 MMR 미반영']),stateJson:JSON.stringify(state),deadline:new Date(this.now()+REALTIME_LIMITS.acceptMs)}});
    await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'MATCHED',roomId}});
    await tx.realtimeActivity.update({where:{playerId:a.playerId},data:{kind:'ROOM',targetId:roomId,expiresAt:new Date(this.now()+REALTIME_LIMITS.roomMaxMs)}});
  }
  private commitBot(s:State,b:Battle){
    if(s.botSide===1&&required(b,1)&&!s.pending[1]){s.pending[1]=chooseBotCommand(b,1);s.automatic[1]=false;}
  }
  private hasProtocol(a:RealtimeAttempt){try{return JSON.parse(a.rosterJson??'null')?.protocol===3;}catch{return false;}}
  private attemptUnits(a:RealtimeAttempt):UnitRecord[]{const value=JSON.parse(a.rosterJson??'null');if(value?.protocol!==3||!Array.isArray(value.units)||value.units.length!==3)fail('매칭 편성을 확인할 수 없어요.');deployedMean(value.units,this.R.data.growth.maxLevel);return value.units;}
  private async rejectLegacyAttempt(tx:Tx,a:RealtimeAttempt){
    const updated=await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'UPGRADE_REQUIRED'}});
    await tx.realtimeActivity.deleteMany({where:{playerId:a.playerId,kind:'QUEUE',targetId:a.id}});return updated;
  }
  private async rejectAcceptance(tx:Tx,r:RealtimeRoom,s:State,reason:'MATCH_DECLINED'|'MATCH_TIMEOUT',decliner?:Side){
    const acceptance=s.acceptance!,requeuedSides:Side[]=[];
    if(decliner!==undefined)acceptance.accepted[decliner]=false;
    // Release both old room locks, then atomically restore only accepted human waiters.
    await tx.realtimeActivity.deleteMany({where:{kind:'ROOM',targetId:r.id}});
    for(const side of sides){
      const attemptId=acceptance.attempts[side];if(!attemptId)continue;
      const playerId=side===0?r.player0Id:r.player1Id;
      const a=await tx.realtimeAttempt.findUnique({where:{id:attemptId}});
      if(!a||a.playerId!==playerId||a.roomId!==r.id||a.state!=='MATCHED')fail('매칭 요청 상태가 달라요.');
      if(acceptance.accepted[side]){
        const leaseUntil=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);
        await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'WAITING',roomId:null,leaseUntil}});
        await tx.realtimeActivity.create({data:{playerId,kind:'QUEUE',targetId:a.id,expiresAt:leaseUntil}});requeuedSides.push(side);
      }else await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'CANCELLED',roomId:null}});
    }
    delete s.acceptance;delete s.preparation;s.pending=[null,null];s.automatic=[false,false];
    const battle=deserializeBattle(this.R,s.battle);battle.phase='over';battle.winner=null;s.battle=serializeBattle(battle);
    const end:End={winner:null,reason,settlement:'not_applicable',requeuedSides,returnedAttempts:acceptance.attempts};
    return tx.realtimeRoom.update({where:{id:r.id},data:{state:'FINISHED',stateJson:JSON.stringify(s),resultJson:JSON.stringify(end),deadline:null,revision:{increment:1}}});
  }
  private async cancelReturnedWaiter(tx:Tx,r:RealtimeRoom,side:Side){
    if(r.state!=='FINISHED'||!r.resultJson)return r;
    const end=JSON.parse(r.resultJson) as End;
    if(!['MATCH_DECLINED','MATCH_TIMEOUT'].includes(end.reason)||!end.requeuedSides?.includes(side))return r;
    const attemptId=end.returnedAttempts?.[side];if(!attemptId)return r;
    const playerId=side===0?r.player0Id:r.player1Id,a=await tx.realtimeAttempt.findUnique({where:{id:attemptId}});
    // A late decline can cancel its returned waiting ticket, never a subsequent match.
    if(a?.playerId!==playerId||a.state==='MATCHED')return r;
    if(a.state==='WAITING'){
      const activity=await tx.realtimeActivity.findUnique({where:{playerId}});
      if(activity?.kind!=='QUEUE'||activity.targetId!==a.id)return r;
      await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'CANCELLED'}});
      await tx.realtimeActivity.delete({where:{playerId}});
    }
    end.requeuedSides=end.requeuedSides.filter(x=>x!==side);
    return tx.realtimeRoom.update({where:{id:r.id},data:{resultJson:JSON.stringify(end),revision:{increment:1}}});
  }
  private async acceptMatch(tx:Tx,r:RealtimeRoom,s:State){
    if(r.mode==='rank'){
      if(s.botSide!==undefined)fail('랭크는 실제 유저끼리만 대전할 수 있어요.');
      await registerRankedMatch(tx,{roomId:r.id,player0Id:r.player0Id,player1Id:r.player1Id,participant0Kind:'HUMAN',participant1Kind:'HUMAN'});
    }
    delete s.acceptance;s.phase++;
    return tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),deadline:new Date(this.now()+REALTIME_LIMITS.turnMs),revision:{increment:1}}});
  }
  private async preparationOptions(tx:Tx,playerId:string,mode:Mode){
    const owned=await tx.ownedCharacter.findMany({where:{playerId,status:{in:['AVAILABLE','EXPEDITION']}},select:{id:true,characterId:true},orderBy:{id:'asc'}});
    const candidates=mapRealtimePreparationCandidates(this.content,owned.map(x=>x.characterId));
    if(mode==='rank')return candidates;
    const row=await tx.storySave.findUnique({where:{playerId}});
    let saved:{units?:Record<string,unknown>}={};try{saved=row?JSON.parse(row.data):{};}catch{fail('저장된 갈매미 성장 정보를 확인해 주세요.');}
    return owned.flatMap(x=>{
      const c=candidates.find(c=>c.id===x.characterId);if(!c)return [];
      const key=`o${x.id}`;
      // Corrupt unused copies must not poison a valid deployed roster.
      try{const rec=casualRecord(this.R,{uid:key,id:c.id,name:c.name,tier:c.rarity as UnitRecord['tier']},saved?.units?.[key]??saved?.units?.[c.id],this.startLevel);
        return [{...c,id:key,characterId:x.characterId,level:rec.level,stage:rec.stage}];
      }catch(e){if(e instanceof GameError)return [];throw e;}
    });
  }
  private async beginBattle(tx:Tx,r:RealtimeRoom,s:State){
    const prep=s.preparation!;
    // Revalidate ownership at the boundary; no half-created battle or saved partial lineup.
    const a=await this.roster(tx,r.player0Id,prep.teams[0].map(x=>r.mode==='normal'?x.uid:x.id),r.mode as Mode);
    const b=s.botSide===1?{units:matchCasualBot(this.R,a.units,prep.teams[1]),ids:prep.teams[1].map(x=>x.id)}:await this.roster(tx,r.player1Id,prep.teams[1].map(x=>r.mode==='normal'?x.uid:x.id),r.mode as Mode);
    const battle=createBattle(this.R,a.units,b.units,randomInt(2**31-1));
    applyCasualCompensation(battle,r.mode as Mode,s.botSide===1);
    await tx.player.update({where:{id:r.player0Id},data:{battleTeam:a.ids.join(',')}});
    if(s.botSide!==1)await tx.player.update({where:{id:r.player1Id},data:{battleTeam:b.ids.join(',')}});
    for(const [playerId,ids] of (s.botSide===1?[[r.player0Id,a.units.map(x=>x.uid)]]:[[r.player0Id,r.mode==='normal'?a.units.map(x=>x.uid):a.ids],[r.player1Id,r.mode==='normal'?b.units.map(x=>x.uid):b.ids]]) as Array<[string,string[]]>)await tx.realtimeLineup.upsert({where:{playerId},create:{playerId,teamJson:JSON.stringify(ids)},update:{teamJson:JSON.stringify(ids)}});
    delete s.preparation;s.battle=serializeBattle(battle);s.phase++;s.pending=[null,null];s.automatic=[false,false];
    try{this.commitBot(s,battle);}catch{return this.finish(tx,r,s,null,'SERVER_ABORT');}
    return tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),deadline:new Date(this.now()+REALTIME_LIMITS.turnMs),revision:{increment:1}}});
  }
  private side(r:RealtimeRoom,playerId:string):Side{if(r.player0Id===playerId)return 0;if(r.player1Id===playerId&&parseState(r).botSide===undefined)return 1;return fail('참가한 대전이 아니에요.');}
  private async room(tx:Tx,playerId:string,roomId:string){const r=await tx.realtimeRoom.findUnique({where:{id:roomId}});if(!r)return fail('대전이 없어요.');this.side(r,playerId);return r;}
  private phaseId(r:RealtimeRoom,s:State){return `${r.id}:${s.phase}`;}
  private async finish(tx:Tx,r:RealtimeRoom,s:State,winner:Side|null,reason:Exclude<EndReason,'MATCH_DECLINED'|'MATCH_TIMEOUT'>){
    if(s.acceptance)return this.rejectAcceptance(tx,r,s,'MATCH_TIMEOUT');
    await refundRoomItems(tx,r.id);
    delete s.preparation;const b=deserializeBattle(this.R,s.battle);b.phase='over';b.winner=winner;s.battle=serializeBattle(b);s.pending=[null,null];s.automatic=[false,false];
    const receipt=s.botSide!==undefined?undefined:r.mode==='rank'?await finishAndSettleRankedMatch(tx,{roomId:r.id,winner,reason}):await settleCasualHuman(tx,{roomId:r.id,player0Id:r.player0Id,player1Id:r.player1Id,participant0Kind:'HUMAN',participant1Kind:'HUMAN',winner,reason});
    const end:End={winner,reason,settlement:s.botSide!==undefined||reason==='SERVER_ABORT'||reason==='DOUBLE_TIMEOUT'?'not_applicable':'settled',receipt};
    const updated=await tx.realtimeRoom.update({where:{id:r.id},data:{state:'FINISHED',stateJson:JSON.stringify(s),resultJson:JSON.stringify(end),deadline:null,revision:{increment:1}}});
    await tx.realtimeActivity.deleteMany({where:{kind:'ROOM',targetId:r.id}});
    return updated;
  }
  private async advance(tx:Tx,r:RealtimeRoom,expire:boolean){
    if(r.state!=='ACTIVE')return r;
    const s=parseState(r);let b=deserializeBattle(this.R,s.battle);
    if(s.botSide!==undefined&&(s.botSide!==1||r.mode!=='normal'))return this.finish(tx,r,s,null,'SERVER_ABORT');
    if(s.rulesVersion!==this.version(r.mode))return this.finish(tx,r,s,null,'SERVER_ABORT');
    if(this.now()-r.createdAt.getTime()>=REALTIME_LIMITS.roomMaxMs)return this.finish(tx,r,s,null,'SERVER_ABORT');
    if(s.acceptance){
      if(s.acceptance.accepted.every(Boolean))return this.acceptMatch(tx,r,s);
      if(expire&&r.deadline&&this.now()>=r.deadline.getTime())return this.rejectAcceptance(tx,r,s,'MATCH_TIMEOUT');
      return r;
    }
    if(s.preparation){
      if(s.preparation.ready.every(Boolean)||(expire&&r.deadline&&this.now()>=r.deadline.getTime())){
        try{return await this.beginBattle(tx,r,s);}catch(e){if(e instanceof GameError)return this.finish(tx,r,s,null,'SERVER_ABORT');throw e;}
      }
      return r;
    }
    try{this.commitBot(s,b);}catch{return this.finish(tx,r,s,null,'SERVER_ABORT');}
    if(expire&&r.deadline&&this.now()>=r.deadline.getTime()){
      for(const side of sides)if(required(b,side)&&!s.pending[side]){
        if(b.phase==='action')s.misses[side]++;
        try{s.pending[side]=automaticCommand(b,side);s.automatic[side]=true;}catch{return this.finish(tx,r,s,null,'SERVER_ABORT');}
      }
      const lost=sides.filter(side=>s.misses[side]>=3);
      if(lost.length)return this.finish(tx,r,s,lost.length===2?null:(1-lost[0]) as Side,lost.length===2?'DOUBLE_TIMEOUT':'TIMEOUT');
    }
    let transitioned=false;
    for(let guard=0;guard<16;guard++){
      try{this.commitBot(s,b);}catch{return this.finish(tx,r,s,null,'SERVER_ABORT');}
      if(sides.some(side=>required(b,side)&&!s.pending[side]))break;
      const before=b,oldPhaseId=this.phaseId(r,s);
      const expected:ItemHold[]=[];for(const side of sides){const c=s.pending[side];if(c?.action?.kind==='item')expected.push({side,playerId:side===0?r.player0Id:r.player1Id,itemId:c.action.item});}
      try{b=resolvePhase(b,s.pending,s.automatic);}catch{return this.finish(tx,r,s,null,'SERVER_ABORT');}
      await consumePhaseItems(tx,r.id,oldPhaseId,expected,consumedItemSides(before,b));
      s.battle=serializeBattle(b);s.phase++;s.pending=[null,null];s.automatic=[false,false];transitioned=true;
      if(b.phase==='over')return this.finish(tx,r,s,b.winner,'BATTLE');
      if(guard===15)return this.finish(tx,r,s,null,'SERVER_ABORT');
    }
    return tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),...(transitioned?{deadline:new Date(this.now()+REALTIME_LIMITS.turnMs)}:{}),revision:{increment:1}}});
  }
  private async view(tx:Tx,r:RealtimeRoom,playerId:string){
    const side=this.side(r,playerId),s=parseState(r),b=deserializeBattle(this.R,s.battle),end=r.resultJson?JSON.parse(r.resultJson) as End:null;
    if(s.acceptance&&r.state==='ACTIVE')return {roomId:r.id,mode:r.mode as Mode,phase:'accept' as const,opponentKind:s.botSide===1?'AI' as const:'HUMAN' as const,phaseId:this.phaseId(r,s),revision:r.revision,deadline:r.deadline?.getTime()??null,serverNow:this.now(),canSubmit:!s.acceptance.accepted[side],mySubmitted:s.acceptance.accepted[side],opponentReady:s.acceptance.accepted[(1-side) as Side],acceptance:{accepted:s.acceptance.accepted[side],opponentAccepted:s.acceptance.accepted[(1-side) as Side]},result:null,battle:null,preparation:null};
    if(end&&(end.reason==='MATCH_DECLINED'||end.reason==='MATCH_TIMEOUT'))return {roomId:r.id,mode:r.mode as Mode,phase:'over' as const,opponentKind:s.botSide===1?'AI' as const:'HUMAN' as const,phaseId:this.phaseId(r,s),revision:r.revision,deadline:null,serverNow:this.now(),canSubmit:false,mySubmitted:false,opponentReady:false,acceptance:null,preparation:null,battle:null,result:{winner:null,reason:end.reason,settlement:'not_applicable' as const,requeued:end.requeuedSides?.includes(side)??false}};
    if(s.preparation&&r.state==='ACTIVE'){const p=s.preparation;return {roomId:r.id,mode:r.mode as Mode,phase:'prepare' as const,opponentKind:s.botSide===1?'AI' as const:'HUMAN' as const,phaseId:this.phaseId(r,s),revision:r.revision,deadline:r.deadline?.getTime()??null,serverNow:this.now(),canSubmit:!p.ready[side],mySubmitted:p.ready[side],opponentReady:p.ready[(1-side) as Side],result:null,battle:null,preparation:{selectedIds:p.teams[side].map(x=>r.mode==='normal'?x.uid:x.id),ready:p.ready[side],opponentReady:p.ready[(1-side) as Side],available:p.available[side]}};}
    const receipt=end?.receipt as {players?:Array<{playerId:string;before:unknown;after:unknown;lpDelta?:number;mmrDelta?:number;promotionChange?:unknown}>}|undefined;
    const own=receipt?.players?.find(p=>p.playerId===playerId);
    const publicRank=(value:unknown)=>{const p=value as {tierIndex:number;lp:number;promotionActive:boolean;promotionWins:number;promotionLosses:number;wins:number;losses:number;games:number};return {tierIndex:p.tierIndex,tier:DEFAULT_RANKED_CONFIG.tiers[p.tierIndex],lp:p.lp,promotionActive:p.promotionActive,promotionWins:p.promotionWins,promotionLosses:p.promotionLosses,wins:p.wins,losses:p.losses,games:p.games};};
    const before=own&&r.mode==='rank'?publicRank(own.before):undefined,after=own&&r.mode==='rank'?publicRank(own.after):undefined;
    const rank=own&&before&&after?{before,after,tier:after.tier,point:after.lp,change:own.lpDelta,lpDelta:own.lpDelta,wins:after.wins,losses:after.losses,promotionChange:own.promotionChange}:undefined;
    const result=end?{winner:end.winner===null?null:(end.winner===side?0:1) as Side,reason:end.reason,settlement:end.settlement,...(rank?{rank}:{})}:null;
    const meta={roomId:r.id,mode:r.mode as Mode,opponentKind:s.botSide===1?'AI' as const:'HUMAN' as const,phaseId:this.phaseId(r,s),revision:r.revision,deadline:r.deadline?.getTime()??null,serverNow:this.now(),canSubmit:r.state==='ACTIVE'&&required(b,side)&&!s.pending[side],mySubmitted:!!s.pending[side],opponentReady:!!s.pending[(1-side) as Side],result};
    return {...meta,phase:b.phase,preparation:null,battle:this.mapper({battle:b,viewerSide:side,sessionId:r.id,names:JSON.parse(r.namesJson) as [string,string],room:meta,inventory:await inventoryForPlayer(tx,playerId)})};
  }
  async currentRoom(playerId:string,roomId:string){return this.tx(async tx=>{
    let r=await this.room(tx,playerId,roomId);
    if(r.state==='ACTIVE'&&(parseState(r).rulesVersion!==this.version(r.mode)||(r.deadline&&this.now()>=r.deadline.getTime())))r=await this.advance(tx,r,true);
    return this.view(tx,r,playerId);
  });}
  async command(playerId:string,input:{roomId:string;phaseId:string;requestId:string;command:Command|{reroll:number}|PrepareCommand|{accept:boolean}}){return this.tx(async tx=>{
    let r=await this.room(tx,playerId,input.roomId);const side=this.side(r,playerId),fingerprint=canonical({phaseId:input.phaseId,command:input.command});
    const prior=await tx.realtimeRequest.findUnique({where:{roomId_playerId_requestId:{roomId:r.id,playerId,requestId:input.requestId}}});
    if(prior){if(prior.fingerprint!==fingerprint)fail('같은 요청 번호의 내용이 달라요.');return this.view(tx,r,playerId);}
    if(r.state==='ACTIVE'&&(parseState(r).rulesVersion!==this.version(r.mode)||(r.deadline&&this.now()>=r.deadline.getTime())))r=await this.advance(tx,r,true);
    // Return the authoritative progressed state rather than rolling back an expired phase.
    if(r.state!=='ACTIVE'||this.phaseId(r,parseState(r))!==input.phaseId){
      if('accept' in input.command&&!input.command.accept&&this.phaseId(r,parseState(r))===input.phaseId){
        const revision=r.revision;r=await this.cancelReturnedWaiter(tx,r,side);
        // At most one terminal cancellation per side can add this receipt.
        if(r.revision!==revision)await tx.realtimeRequest.create({data:{id:id(),roomId:r.id,playerId,requestId:input.requestId,fingerprint}});
      }
      return this.view(tx,r,playerId);
    }
    const s=parseState(r),b=deserializeBattle(this.R,s.battle);
    if(s.acceptance){
      if(!('accept' in input.command))fail('먼저 대전 수락 여부를 선택해 주세요.');
      if(await tx.realtimeRequest.count({where:{roomId:r.id}})>=REALTIME_LIMITS.maxRequestsPerRoom)fail('요청 한도를 넘었어요.');
      await tx.realtimeRequest.create({data:{id:id(),roomId:r.id,playerId,requestId:input.requestId,fingerprint}});
      if(!input.command.accept)return this.view(tx,await this.rejectAcceptance(tx,r,s,'MATCH_DECLINED',side),playerId);
      s.acceptance.accepted[side]=true;
      r=await tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),revision:{increment:1}}});
      r=await this.advance(tx,r,false);return this.view(tx,r,playerId);
    }
    if('accept' in input.command)fail('대전 수락 시간이 끝났어요.');
    if(s.preparation){
      if(!('prepare' in input.command))fail('먼저 전투 편성을 준비해 주세요.');
      if(s.preparation.ready[side])fail('준비 완료했어요. 상대를 기다려 주세요.');
      if(await tx.realtimeRequest.count({where:{roomId:r.id}})>=REALTIME_LIMITS.maxRequestsPerRoom)fail('요청 한도를 넘었어요.');
      const selected=await this.roster(tx,playerId,input.command.prepare.team,r.mode as Mode);
      s.preparation.teams[side]=selected.units;s.preparation.ready[side]=input.command.prepare.ready;
      await tx.realtimeRequest.create({data:{id:id(),roomId:r.id,playerId,requestId:input.requestId,fingerprint}});
      r=await tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),revision:{increment:1}}});
      r=await this.advance(tx,r,false);return this.view(tx,r,playerId);
    }
    if('prepare' in input.command)fail('편성 시간이 끝났어요.');
    if(s.pending[side]||!required(b,side))fail('이미 선택했어요. 상대를 기다려 주세요.');
    if(await tx.realtimeRequest.count({where:{roomId:r.id}})>=REALTIME_LIMITS.maxRequestsPerRoom)fail('요청 한도를 넘었어요.');
    if('reroll' in input.command){s.battle=serializeBattle(rerollCommand(b,side,input.command.reroll));}
    else {validateCommand(b,side,input.command);const item=itemRequirement(b,side,input.command);if(item)await holdItem(tx,{roomId:r.id,phaseId:input.phaseId,side,playerId,itemId:item.itemId});s.pending[side]=input.command;s.automatic[side]=false;if(b.phase==='action')s.misses[side]=0;}
    await tx.realtimeRequest.create({data:{id:id(),roomId:r.id,playerId,requestId:input.requestId,fingerprint}});
    r=await tx.realtimeRoom.update({where:{id:r.id},data:{stateJson:JSON.stringify(s),revision:{increment:1}}});
    if(!('reroll' in input.command))r=await this.advance(tx,r,false);
    return this.view(tx,r,playerId);
  });}
  async surrender(playerId:string,roomId:string,requestId:string){return this.tx(async tx=>{
    let r=await this.room(tx,playerId,roomId);const side=this.side(r,playerId),fingerprint='surrender';
    const prior=await tx.realtimeRequest.findUnique({where:{roomId_playerId_requestId:{roomId,playerId,requestId}}});
    if(prior&&prior.fingerprint!==fingerprint)fail('같은 요청 번호의 내용이 달라요.');
    if(r.state!=='ACTIVE')return this.view(tx,await this.cancelReturnedWaiter(tx,r,side),playerId);
    if(!prior&&await tx.realtimeRequest.count({where:{roomId}})>=REALTIME_LIMITS.maxRequestsPerRoom)fail('요청 한도를 넘었어요.');
    if(!prior)await tx.realtimeRequest.create({data:{id:id(),roomId,playerId,requestId,fingerprint}});
    if(r.state==='ACTIVE'&&(parseState(r).rulesVersion!==this.version(r.mode)||(r.deadline&&this.now()>=r.deadline.getTime())))r=await this.advance(tx,r,true);
    if(r.state==='ACTIVE'){const s=parseState(r);r=s.acceptance?await this.rejectAcceptance(tx,r,s,'MATCH_DECLINED',side):await this.finish(tx,r,s,(1-side) as Side,'SURRENDER');}
    r=await this.cancelReturnedWaiter(tx,r,side);return this.view(tx,r,playerId);
  });}
  async rank(playerId:string){return this.tx(async tx=>{const summary=await rankSummary(tx,playerId);const p=await tx.player.findUniqueOrThrow({where:{id:playerId}});return {...summary,tiers:summary.tiers.map(t=>t.name),top:summary.top.map(t=>({rank:t.rank,name:t.nickname,point:t.point,tier:t.tier,wins:t.wins,losses:t.losses,me:t.playerId===playerId})),level:this.content.economy.pvp.level,team:p.battleTeam?p.battleTeam.split(','):null};});}
  async tick(){await this.tx(async tx=>{
    const now=new Date(this.now());
    // Only inert/terminal queue transport records are pruned; match history/ratings remain.
    await tx.realtimeAttempt.deleteMany({where:{state:{in:['ISSUED','EXPIRED','CANCELLED','UPGRADE_REQUIRED']},joinNotAfter:{lt:new Date(this.now()-3600_000)}}});
    const expired=await tx.realtimeAttempt.findMany({where:{state:'WAITING',leaseUntil:{lte:now}},take:100});
    for(const a of expired){await tx.realtimeAttempt.update({where:{id:a.id},data:{state:'EXPIRED'}});await tx.realtimeActivity.deleteMany({where:{playerId:a.playerId,kind:'QUEUE',targetId:a.id}});}
    await this.match(tx,'rank');await this.match(tx,'normal');
  });
    const rooms=await this.db.realtimeRoom.findMany({where:{state:'ACTIVE',deadline:{lte:new Date(this.now())}},orderBy:{deadline:'asc'},take:30,select:{id:true}});
    const errors:unknown[]=[];
    for(const selected of rooms)try{await this.tx(async tx=>{const r=await tx.realtimeRoom.findUnique({where:{id:selected.id}});if(r?.state==='ACTIVE'&&r.deadline&&r.deadline.getTime()<=this.now())await this.advance(tx,r,true);});}catch(e){errors.push(e);}
    if(errors.length)throw new AggregateError(errors,'Some realtime rooms need operator review');
  }
  async claimLegacy(playerId:string,targetId:string,kind:'LEGACY_NATIVE'|'LEGACY_WEB'='LEGACY_NATIVE'){return this.tx(async tx=>{
    const a=await this.activity(tx,playerId);if(a)fail('진행 중인 전투나 매칭으로 돌아가 주세요.');
    await tx.realtimeActivity.create({data:{playerId,kind,targetId,expiresAt:new Date(this.now()+REALTIME_LIMITS.legacyLeaseMs)}});
  });}
  async releaseLegacy(playerId:string,targetId?:string,kind:'LEGACY_NATIVE'|'LEGACY_WEB'='LEGACY_NATIVE'){await this.tx(tx=>tx.realtimeActivity.deleteMany({where:{playerId,kind,...(targetId?{targetId}:{})}}));}
  async nativeOperation(playerId:string,path:string,run:()=>Promise<unknown>){
    const claim=path==='battle/start'?id():undefined;
    if(claim)await this.claimLegacy(playerId,claim);
    try{
      const value=await run();const result=value as {phase?:string;session?:string|null};
      if(claim&&result.session)await this.tx(tx=>tx.realtimeActivity.updateMany({where:{playerId,kind:'LEGACY_NATIVE',targetId:claim},data:{targetId:result.session!}}));
      if(path==='battle/turn'&&result.phase==='over'&&result.session)await this.releaseLegacy(playerId,result.session);
      if(path==='battle/current'&&result.session===null)await this.releaseLegacy(playerId);
      return value;
    }catch(e){if(claim)await this.releaseLegacy(playerId,claim);throw e;}
  }
  async legacyWebStart(playerId:string,run:()=>Promise<unknown>){
    const claim=id();await this.claimLegacy(playerId,claim,'LEGACY_WEB');
    try{const value=await run(),match=(value as {match?:string}).match;if(match)await this.tx(tx=>tx.realtimeActivity.updateMany({where:{playerId,kind:'LEGACY_WEB',targetId:claim},data:{targetId:match}}));return value;}
    catch(e){await this.releaseLegacy(playerId,claim,'LEGACY_WEB');throw e;}
  }
  async legacyWebResult(playerId:string,matchId:string,run:()=>Promise<unknown>){const result=await run();await this.releaseLegacy(playerId,matchId,'LEGACY_WEB');return result;}
}
