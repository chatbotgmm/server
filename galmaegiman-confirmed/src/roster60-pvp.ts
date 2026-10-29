import {isCurrentRoster60Party,roster60PartySelection} from './roster60-party-policy.js';
import {awardCommanderXp,readCommanderState,commanderAccountLevelReader,validateCommanderPolicy,type CommanderPolicy,type CommanderXpResult,type CommanderEvent} from './progression/commander.js';
import {roster60WriteTails as tails} from './roster60-write-queue.js';
import {defaultPresencePolicy,monitorObservation,initializePresence,observePresence,decidePresence,validatePresenceForfeitProof,type PresencePolicy,type PresenceMonitor,type PresenceState,type PresenceForfeitProof} from './progression/roster60-presence.js';
import {roster60LossFeedback,type Roster60FeedbackTerminationKind} from './battle/roster60-loss-feedback.js';
import {prepareNormalXpPlayer,queuedNormalXpPlan,settleNormalXp,type NormalXpPolicy,type NormalXpRoomSnapshot} from './progression/normal-xp.js';
import {opponentFighter,selectEnemySpecies} from './battle/roster60-opponent-intel.js';
import {captureDefenseSnapshot,battleKnowledge,observedOpponentIntel,resolvedDefenseObservation,addDefenseObservation,persistDefenseObservations,publicDefenseObservation,publicDefenseSafe,safeKnowledgeCatalog,type DefenseSnapshot,type DefenseObservation} from './battle/roster60-observed-defense.js';
import {capturePresentation,presentStep,presentationForViewer} from './battle/roster60-presentation.js';
import {createHash,randomBytes,randomUUID} from 'node:crypto';
import type {Prisma,PrismaClient,Roster60PvpMatch,Roster60PvpQueue} from '@prisma/client';
import {RosterError,type RosterCatalog} from './roster60-onboarding.js';
import {fighter,formationUnit,validatePolicy,type Fighter,type BattleAction,type BattlePolicy} from './roster60-battle.js';
import {growthAt,focusMp,spendMp} from './battle/roster60-rules.js';
import {fiveStatAttack,decideTurnOrder} from './battle/roster60-five-combat.js';
import {loadBattleRules} from './battle/load.js';
import {loadContent} from './content.js';
import {REALTIME_LIMITS} from './pvp/service.js';
import {matchmakingRating,registerRankedMatch,finishAndSettleRankedMatch,settleCasualHuman} from './ranked/settlement.js';
import {awardPlayPoints} from './progression.js';
import {evaluateRoster60Rank,rankUnitEligibility,rankAdmission,readRankAdmission,readRoster60RebirthHistory,orderRebirthCandidates,validRebirthCountPin,roster60MatchPoolPolicyVersion,roster60RankPolicyVersion,unknownAccountLevel,type MatchPoolSnapshot,type RankPool,type AccountLevelReader,type RankEligibility,type RankAdmission,type RankAdmissionSnapshot} from './progression/roster60-rank.js';
type Tx=Prisma.TransactionClient;type Side=0|1;type Mode='normal'|'rank';
type Pending=BattleAction&{heldItem?:string};
type Shared={connectionPause?:{observedAfter:number;startedAt:number};entered?:[string[],string[]];botSide?:1;turn:number;parties:[Fighter[],Fighter[]];pending:[Pending|null,Pending|null];accepted:[boolean,boolean];ack:[boolean,boolean];misses:[number,number];winner:Side|null;events:any[]};
type Snapshot={commanderPolicies?:{playerId:string;policy:CommanderPolicy|null}[];matchPool?:MatchPoolSnapshot;rankAdmission?:RankAdmissionSnapshot;normalXp?:NormalXpRoomSnapshot;policy:BattlePolicy;catalogVersion:string;observedDefense?:DefenseSnapshot;items:ReturnType<typeof loadBattleRules>['data']['items'];members:{id:string;playerId:string;xp:string}[];queues?:{rating:number;tierIndex:number;queuedAt:string}[]};
const sides=[0,1] as const;const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
export class RosterPvp {
 private presenceRestartPending=false;private presenceRestartWork:Promise<void>|undefined;
 notePresenceRestart(){this.presenceRestartPending=true;}
 constructor(private db:PrismaClient,private catalog:()=>RosterCatalog|null,private policy:()=>BattlePolicy|null,private now=()=>Date.now(),private normalXpPolicy:()=>NormalXpPolicy|null=()=>null,private accountLevelReader:AccountLevelReader=unknownAccountLevel,private presencePolicy:Readonly<PresencePolicy>=defaultPresencePolicy,private commanderPolicy:()=>CommanderPolicy|null=()=>null){if(this.accountLevelReader===unknownAccountLevel)this.accountLevelReader=commanderAccountLevelReader(this.commanderPolicy);}
 private async tx<T>(run:(tx:Tx)=>Promise<T>,skipRestart=false){if(!skipRestart&&this.presenceRestartPending){if(!this.presenceRestartWork)this.presenceRestartWork=this.startPresenceEpoch().finally(()=>{this.presenceRestartWork=undefined;});await this.presenceRestartWork;}const previous=tails.get(this.db)??Promise.resolve();let release!:()=>void;const tail=new Promise<void>(r=>release=r);tails.set(this.db,tail);await previous;try{for(let attempt=0;;attempt++)try{return await this.db.$transaction(async tx=>{await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});await this.observePresenceMonitor(tx);return run(tx);},{maxWait:5000,timeout:15000});}catch(e){if(attempt>=3||!['P2034','P2002','P1008','P2028','RANKED_CONCURRENT_WRITE','COMMANDER_CONCURRENT_WRITE'].includes((e as any).code))throw e;}}finally{release();if(tails.get(this.db)===tail)tails.delete(this.db);}}
 private async request(id:string,requestId:string,payload:unknown,run:(tx:Tx)=>Promise<unknown>){return this.tx(async tx=>{const fingerprint=createHash('sha256').update(JSON.stringify(['roster-human-v1',payload])).digest('hex');const old=await tx.roster60BattleReceipt.findUnique({where:{playerId_requestId:{playerId:id,requestId}}});if(old){if(old.fingerprint!==fingerprint)return fail('REQUEST_ID_REUSED');await this.observeReceiptRoom(tx,id,payload);return publicDefenseSafe(JSON.parse(old.result));}const result=await run(tx);await tx.roster60BattleReceipt.create({data:{playerId:id,requestId,fingerprint,result:JSON.stringify(result)}});return result;});}
 private async observePresenceMonitor(tx:Tx){
  const key='roster60-presence-monitor-v1',old=await tx.appMeta.findUnique({where:{key}});let previous:unknown=null;try{previous=old?JSON.parse(old.value):null;}catch{previous={invalid:true};}
  const {monitor}=monitorObservation(previous,this.now(),this.presencePolicy),value=JSON.stringify(monitor);
  await tx.appMeta.upsert({where:{key},create:{key,value},update:{value}});return monitor;
 }
 private async readPresenceMonitor(tx:Tx):Promise<PresenceMonitor>{const row=await tx.appMeta.findUnique({where:{key:'roster60-presence-monitor-v1'}});return JSON.parse(row?.value??'null') as PresenceMonitor;}
 private presenceValue(row:Roster60PvpMatch):unknown{try{return row.presence?JSON.parse(row.presence):null;}catch{return null;}}
 /** A known application restart begins a new observation window. HP, commands and turn survive;
  * neither side is blamed for observations that the server could not make while restarting. */
 async startPresenceEpoch(){return this.tx(async tx=>{
  const prior=await this.readPresenceMonitor(tx),now=this.now(),monitor:PresenceMonitor={version:'roster60-presence-monitor-v1',generation:prior.generation+1,lastSuccessAt:now,continuousSince:now};
  await tx.appMeta.update({where:{key:'roster60-presence-monitor-v1'},data:{value:JSON.stringify(monitor)}});
  const rooms=await tx.roster60PvpMatch.findMany({where:{status:{in:['ACTIVE','ACCEPTING']}},take:REALTIME_LIMITS.maxRooms});
  for(const room of rooms){let state:Shared;try{state=this.parse(room);if(!Array.isArray(state.parties)||state.parties.length!==2||!Array.isArray(state.pending))continue;}catch{continue;}
   state.misses=[0,0];delete state.connectionPause;const presence=initializePresence(monitor,now,this.presencePolicy,state.botSide);
   await tx.roster60PvpMatch.update({where:{id:room.id},data:{presence:JSON.stringify(presence),state:JSON.stringify(state),deadline:new Date(Math.max(room.deadline.getTime(),now+(room.status==='ACCEPTING'?REALTIME_LIMITS.acceptMs:REALTIME_LIMITS.turnMs)))}});
  }
 },true).then(()=>{this.presenceRestartPending=false;});}
 private async observeReceiptRoom(tx:Tx,id:string,payload:unknown){
  if(!Array.isArray(payload)||!['action','accept'].includes(payload[0])||typeof payload[1]!=='string')return;
  const row=await tx.roster60PvpMatch.findUnique({where:{id:payload[1]}});if(row&&row.status==='ACTIVE'&&(row.player0Id===id||row.player1Id===id))await this.advance(tx,row,id);
 }
 private connectionWait(row:Roster60PvpMatch,state:Shared,presence:PresenceState,now:number){
  const observedAfter=state.connectionPause?.observedAfter??row.deadline.getTime()-REALTIME_LIMITS.turnMs;
  if(!state.connectionPause&&now<row.deadline.getTime())return false;
  return sides.some(side=>side!==state.botSide&&!state.pending[side]&&presence.participants[side]!.lastSeenAt<=observedAfter);
 }
 private async publicPresence(tx:Tx,row:Roster60PvpMatch,state:Shared,side:Side){
  if(row.status!=='ACTIVE')return undefined;const raw=this.presenceValue(row),decision=decidePresence(raw,await this.readPresenceMonitor(tx),this.now());
  const result:{status:string;serverNow:number;ownGraceUntil?:number;opponentGraceUntil?:number}={status:'CONNECTED',serverNow:this.now()};
  if(decision.kind==='ABORT_NEUTRAL'||decision.kind==='FORFEIT_PROVEN')result.status='OBSERVATION_UNAVAILABLE';
  else if(decision.kind==='RECONNECT_GRACE'){
   result.status='RECONNECT_GRACE';for(const absent of decision.absent)if(absent.side===side)result.ownGraceUntil=absent.graceUntil;else result.opponentGraceUntil=absent.graceUntil;
  }else if(state.connectionPause){
   result.status='RECONNECT_GRACE';const presence=raw as PresenceState;
   for(const index of sides)if(index!==state.botSide&&!state.pending[index]&&presence.participants[index]!.lastSeenAt<=state.connectionPause.observedAfter){const until=presence.participants[index]!.lastSeenAt+presence.policy.totalAbsentMs;if(index===side)result.ownGraceUntil=until;else result.opponentGraceUntil=until;}
  }
  return result;
 }
 private queuedCommanderPolicy(raw:string|null|undefined,playerId:string):CommanderPolicy|null{if(!raw)return null;let pin:{playerId?:string;policy?:unknown};try{pin=JSON.parse(raw);}catch{return fail('COMMANDER_SNAPSHOT_INVALID',503);}if(pin.playerId!==playerId||!Object.prototype.hasOwnProperty.call(pin,'policy'))return fail('COMMANDER_SNAPSHOT_INVALID',503);return pin.policy===null?null:validateCommanderPolicy(pin.policy);}
 private side(r:Roster60PvpMatch,id:string):Side {if(r.player0Id===id)return 0;if(r.player1Id===id&&this.parse(r).botSide!==1)return 1;return fail('BATTLE_NOT_FOUND',404);}
 private async room(tx:Tx,id:string,battleId:string){const r=await tx.roster60PvpMatch.findUnique({where:{id:battleId}});if(!r)return fail('BATTLE_NOT_FOUND',404);this.side(r,id);return r;}
 private ids(r:Roster60PvpMatch):[string,string]{return [r.player0Id,r.player1Id];}
 private parse(r:Roster60PvpMatch){return JSON.parse(r.state) as Shared;}
 private snap(r:Roster60PvpMatch){return JSON.parse(r.snapshot) as Snapshot;}
 private async view(tx:Tx,r:Roster60PvpMatch,id:string){const side=this.side(r,id),other=(1-side) as Side,s=this.parse(r),snap=this.snap(r),alive=s.parties[side][0].hp>0;
 // Only own submission status is public. No seed, pending commands, opponent inventory or reserve owned IDs.
 const intel=observedOpponentIntel(s.parties[other][0],s.turn,snap.observedDefense,battleKnowledge(snap.observedDefense,s.events,String(side)),safeKnowledgeCatalog(this.catalog));
 const clone=(f:Fighter,self:boolean)=>self?publicDefenseSafe({...f,id:'self'}):opponentFighter(f,intel);
 const finished=['FINISHED','SETTLED'].includes(r.status);const outcome=finished?(s.winner===null?'draw':s.winner===side?'win':'loss'):null;
 const storedSettlement=r.settlement?JSON.parse(r.settlement):null,{normalXp,commanderRewards,rankConditions,matchPoolConditions,partyConditions,...baseSettlement}=storedSettlement??{};const ownXp=normalXp?.players?.[id],ownCommander=commanderRewards?.[id];
 const lossFeedback=roster60LossFeedback({state:s,viewerSide:side,mode:r.mode as Mode,outcome,reason:storedSettlement?.reason,status:r.status,opponentKind:s.botSide===1?'AI':'HUMAN',terminationKind:storedSettlement?.terminationKind});
 const ownPartyCondition=partyConditions?.[id];
 const settlement=storedSettlement?{...baseSettlement,...(ownPartyCondition?{partyCondition:ownPartyCondition,partySelectionRequired:true}:{}),outcome,...(ownCommander?{commanderReward:ownCommander,commander:ownCommander.commander,commanderXpAwarded:ownCommander.recordedXp??ownCommander.xpAwarded}:{}),...(lossFeedback?{lossFeedback}:{}),...(rankConditions&&Object.keys(rankConditions).length?{rankCondition:rankConditions[id]??'RANK_OPPONENT_ELIGIBILITY_CHANGED'}:{}),...(matchPoolConditions&&Object.keys(matchPoolConditions).length?{matchPoolCondition:matchPoolConditions[id]??'MATCH_POOL_OPPONENT_CHANGED'}:{}),xpAwarded:r.mode==='normal'?(ownXp?.xpAwarded??0):0,...(r.mode==='normal'?{xpDeferred:ownXp?.xpDeferred??0,xpDiscarded:ownXp?.xpDiscarded??0,xpRequested:ownXp?.xpRequested??0,xpByOwnedId:Object.fromEntries((ownXp?.units??[]).map((u:any)=>[u.ownedId,u.appliedXp])),normalXp:{status:ownXp?.status??(ownXp?.policyVersion==null?'LEGACY_POLICY_UNCONFIGURED':ownXp.units?.some((u:any)=>u.status==='CAP_POLICY_PENDING')?'CAP_POLICY_PENDING':normalXp?.status??'LEGACY_POLICY_UNCONFIGURED'),rulesVersion:ownXp?.rulesVersion??null,sourcePolicyVersion:ownXp?.sourcePolicyVersion??null,overflowDisposition:'discarded-not-banked',policyVersion:ownXp?.policyVersion??null,testValues:ownXp?.testValues??false,participation:'actually-entered-owned-only',deferredIsAutomaticReward:false,units:ownXp?.units??[]}}:{})}:null;
 const activity=finished&&['MATCH_TIMEOUT','MATCH_DECLINED'].includes(storedSettlement?.reason)?await tx.realtimeActivity.findUnique({where:{playerId:id}}):null;
 const requeued=s.ack[side]&&activity?.kind==='ROSTER_QUEUE'&&activity.targetId===r.id;
 const events=s.events.map(e=>({...e,decision:undefined,events:e.events.map((v:any)=>{
  const {defenseObservation,intelBefore,intelAfter,...visible}=v,presentation=presentationForViewer(v.presentation,side);
  return {...visible,actor:v.side===side?'self':'enemy',side:undefined,targetOwnedId:v.side===side?v.targetOwnedId:undefined,
   ...(v.side===side&&defenseObservation?{observedDefense:publicDefenseObservation(defenseObservation)}:{}),
   presentation:presentation?{...presentation,...(intelBefore?.[side]?{opponentIntelBefore:intelBefore[side]}:{}),...(intelAfter?.[side]?{opponentIntelAfter:intelAfter[side]}:{})}:undefined};
 })}));
 return {battleId:r.id,revision:r.revision,serverNow:this.now(),presence:await this.publicPresence(tx,r,s,side),status:r.status==='SETTLED'&&!s.ack[side]?'FINISHED':r.status,mode:r.mode,opponentKind:s.botSide===1?'AI':'HUMAN',deadline:r.deadline.getTime(),accepted:s.accepted[side],requeued,waitingForOpponent:!!s.pending[side]||(!alive?false:s.parties[other][0].hp<=0),balanceProfile:snap.policy.version,testBaseline:(snap.policy as BattlePolicy&{profileKind?:string}).profileKind==='user-authorized-test-baseline',
 state:{opponentIntel:intel,version:'five-battle-v1',rulesVersion:'party-actions-v2',mode:'duel',turn:s.turn,fighters:[clone(s.parties[side][0],true),clone(s.parties[other][0],false)],reserves:s.parties[side].slice(1).map(publicDefenseSafe),inventory:await tx.playerItem.findMany({where:{playerId:id},select:{itemId:true,count:true}}),outcome,events},itemDefinitions:snap.items,settlement,disabledCommands:{item:alive?'':'FORCED_SWITCH_REQUIRED',switch:'',escape:''}};
 }
 async tick(){return this.tx(async tx=>{await this.expireQueue(tx);await this.match(tx,'normal');const rooms=await tx.roster60PvpMatch.findMany({where:{status:{in:['ACTIVE','ACCEPTING']},deadline:{lte:new Date(this.now())}},take:100,orderBy:{deadline:'asc'}});for(const row of rooms)await this.advance(tx,row);});}
 async options(id:string){const c=this.catalog(),p=this.policy();validatePolicy(p,c);const own=await this.db.roster60Owned.findMany({where:{playerId:id,status:'AVAILABLE',locked:false},orderBy:{id:'asc'}});const commander=await readCommanderState(this.db,id,this.commanderPolicy());const party=await this.db.roster60PvpParty.findUnique({where:{playerId:id}});const rankEligibility=await evaluateRoster60Rank(this.db,id,[],c!,this.accountLevelReader);const partyIds=party?JSON.parse(party.ownedIds):[];return {units:own.map(u=>{const unit=formationUnit(c!,u);return {...unit,...rankUnitEligibility(unit.level)};}),partyIds,...roster60PartySelection(partyIds),rankEligibility,commander};}
 async party(id:string,ownedIds:string[],mode?:Mode){return this.tx(async tx=>{await this.expireQueue(tx);if(await tx.realtimeActivity.findUnique({where:{playerId:id}}))return fail('PVP_BUSY');await this.members(tx,id,ownedIds,'AVAILABLE','normal');const rankEligibility=mode==='rank'?await this.requireRank(tx,id,ownedIds,['AVAILABLE']):null;await tx.roster60PvpParty.upsert({where:{playerId:id},create:{playerId:id,ownedIds:JSON.stringify(ownedIds)},update:{ownedIds:JSON.stringify(ownedIds)}});return {partyIds:ownedIds,...roster60PartySelection(ownedIds),...(rankEligibility?{mode:'rank',rankEligibility}:{})};});}
 private async requireRank(tx:Tx,id:string,ownedIds:string[],allowedStatuses:string[]):Promise<RankEligibility>{const c=this.catalog();if(!c)return fail('RANK_CATALOG_CHANGED',503);const result=await evaluateRoster60Rank(tx,id,ownedIds,c,this.accountLevelReader,{allowedStatuses});if(!result.eligible)return fail(result.reason,result.reason.endsWith('_PENDING')||result.reason==='ACCOUNT_LEVEL_DATA_INVALID'?503:409);return result;}
 private async matchPoolCondition(tx:Tx,id:string,pool:string|null|undefined,version:string|null|undefined,count:number|null|undefined,source:string|null|undefined,stamp:string|null|undefined){
  if(version!==roster60MatchPoolPolicyVersion||!validRebirthCountPin(pool,count,source)||typeof stamp!=='string'||!/^[a-f0-9]{64}$/.test(stamp))return 'MATCH_POOL_QUEUE_POLICY_PENDING';
  const current=await readRoster60RebirthHistory(tx,id);
  return current.pool===null?'MATCH_POOL_POLICY_PENDING':current.pool!==pool?'MATCH_POOL_CLASS_CHANGED':current.count!==count||current.countSource!==source||current.historyStamp!==stamp?'MATCH_POOL_HISTORY_CHANGED':'';
 }
 private async matchQueueCondition(tx:Tx,row:Roster60PvpQueue){
  if(!isCurrentRoster60Party(JSON.parse(row.ownedIds)))return 'PARTY_RESELECTION_REQUIRED';
  const pool=await this.matchPoolCondition(tx,row.playerId,row.reincarnationPool,row.matchPoolPolicyVersion,row.reincarnationCount,row.reincarnationCountSource,row.reincarnationHistoryStamp);return pool|| (row.mode==='rank'?await this.rankQueueCondition(tx,row):'');
 }
 private async matchRoomConditions(tx:Tx,row:Roster60PvpMatch){
  const issues:Record<string,string>={},snapshot=this.snap(row),pin=snapshot.matchPool;
  // Already-created rooms retain their pre-policy contract. New queues always pin this field.
  if(!pin)return issues;
  for(const id of this.ids(row)){
   if(this.parse(row).botSide===1&&id===row.player1Id)continue;
   const player=Array.isArray(pin.players)?pin.players.find(p=>p.playerId===id):null;
   // A room made under the prior hard-pool policy already has its opponent;
   // preserve its level/class contract without retroactively requiring a soft count pin.
   const condition=pin.version==='account-rebirth-history-normal-rank-v1'?(await readRoster60RebirthHistory(tx,id)).pool===player?.pool?'':'MATCH_POOL_CLASS_CHANGED':await this.matchPoolCondition(tx,id,player?.pool,pin.version,player?.count,player?.countSource,player?.historyStamp);if(condition)issues[id]=condition;
  }
  if(!Object.keys(issues).length&&this.parse(row).botSide!==1&&pin.players[0]?.pool!==pin.players[1]?.pool)for(const id of this.ids(row))issues[id]='MATCH_POOL_CLASS_CHANGED';
  return issues;
 }
 private async rankPinCondition(tx:Tx,id:string,ownedIds:string[],pin:RankAdmission|null,status:string){
  if(!pin||pin.playerId!==id||pin.ownedIds.length!==ownedIds.length||pin.ownedIds.some(ownedId=>!ownedIds.includes(ownedId)))return 'RANK_QUEUE_POLICY_PENDING';
  const c=this.catalog();if(!c||pin.catalogVersion!==c.version)return 'RANK_CATALOG_CHANGED';
  const result=await evaluateRoster60Rank(tx,id,ownedIds,c,this.accountLevelReader,{allowedStatuses:[status]});
  return !result.eligible?result.reason:result.pool!==pin.pool?'RANK_QUEUE_CLASS_CHANGED':'';
 }
 private async rankQueueCondition(tx:Tx,row:Roster60PvpQueue){
  if(row.rankPolicyVersion!==roster60RankPolicyVersion||!['FIRST_LIFE','REBORN'].includes(row.reincarnationPool??''))return 'RANK_QUEUE_POLICY_PENDING';
  const pin=readRankAdmission(row.rankAdmissionSnapshot);if(!pin||pin.pool!==row.reincarnationPool)return 'RANK_QUEUE_POLICY_PENDING';
  return this.rankPinCondition(tx,row.playerId,JSON.parse(row.ownedIds),pin,'PVP_QUEUE');
 }
 private async rankRoomConditions(tx:Tx,row:Roster60PvpMatch){
  const issues:Record<string,string>={};if(row.mode!=='rank')return issues;const snapshot=this.snap(row),admission=snapshot.rankAdmission;
  for(const id of this.ids(row)){
   const raw=admission?.version===roster60RankPolicyVersion&&Array.isArray(admission.players)?admission.players.find(p=>p.playerId===id):undefined;
   const pin=raw?readRankAdmission(JSON.stringify(raw)):null;
   const condition=await this.rankPinCondition(tx,id,snapshot.members.filter(m=>m.playerId===id).map(m=>m.id),pin,'PVP');if(condition)issues[id]=condition;
  }
  if(!Object.keys(issues).length&&admission!.players[0].pool!==admission!.players[1].pool)for(const id of this.ids(row))issues[id]='RANK_QUEUE_CLASS_CHANGED';
  return issues;
 }
 private async members(tx:Tx,id:string,ids:string[],status:string,mode:Mode){if(!isCurrentRoster60Party(ids))return fail('INVALID_PARTY',400);const c=this.catalog(),p=this.policy();validatePolicy(p,c);const out:Fighter[]=[],members:Snapshot['members']=[];for(const ownedId of ids){const u=await tx.roster60Owned.findFirst({where:{id:ownedId,playerId:id}});if(!u)return fail('UNIT_NOT_FOUND',404);if(u.status!==status||u.locked)return fail('UNIT_UNAVAILABLE');const d=c!.characters.find(d=>d.id===u.speciesId);if(!d)return fail('BATTLE_UNIT_DATA_PENDING');if(u.totalXp<0n||u.totalXp>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');const lv=mode==='rank'?loadContent().economy.pvp.level:growthAt(Number(u.totalXp),d.growth,u.reincarnation,d.reincarnationXpPolicy).level;out.push({...fighter(c!,u.speciesId,lv,ownedId,u.reincarnation,u.skillInheritanceJson),ownedId});members.push({id:ownedId,playerId:id,xp:u.totalXp.toString()});}return {fighters:out,members};}
 private async expireQueue(tx:Tx){const rows=await tx.roster60PvpQueue.findMany({where:{leaseUntil:{lte:new Date(this.now())}}});for(const row of rows)await this.removeQueue(tx,row.playerId);}
 private async removeQueue(tx:Tx,id:string){const q=await tx.roster60PvpQueue.findUnique({where:{playerId:id}});if(!q)return;await tx.roster60Owned.updateMany({where:{playerId:id,id:{in:JSON.parse(q.ownedIds)},status:'PVP_QUEUE'},data:{status:'AVAILABLE'}});await tx.roster60PvpQueue.delete({where:{playerId:id}});await tx.realtimeActivity.deleteMany({where:{playerId:id,kind:'ROSTER_QUEUE'}});}
 async cancel(id:string,b:{requestId:string}){return this.request(id,b.requestId,['cancel'],async tx=>{await this.removeQueue(tx,id);return this.currentIn(tx,id);});}
 async join(id:string,b:{requestId:string;mode:Mode}){return this.request(id,b.requestId,['join',b.mode],async tx=>{
  await this.expireQueue(tx);const active=await this.findRoom(tx,id);if(active)return {status:'matched',mode:active.mode,serverNow:this.now(),battle:await this.view(tx,await this.advance(tx,active,id),id)};
  const prior=await tx.roster60PvpQueue.findUnique({where:{playerId:id}});if(prior)return this.currentIn(tx,id);
  if(await tx.realtimeActivity.findUnique({where:{playerId:id}}))return fail('PVP_BUSY');
  const saved=await tx.roster60PvpParty.findUnique({where:{playerId:id}});if(!saved)return fail('PARTY_REQUIRED');
  const ids:string[]=JSON.parse(saved.ownedIds);if(!isCurrentRoster60Party(ids))return fail('PARTY_RESELECTION_REQUIRED');
  const units=await this.members(tx,id,ids,'AVAILABLE',b.mode);
  const rankEligibility=b.mode==='rank'?await this.requireRank(tx,id,ids,['AVAILABLE']):null;
  const admission=rankEligibility?rankAdmission(id,ids,this.catalog()!.version,rankEligibility):null;
  const history=await readRoster60RebirthHistory(tx,id),pool=history.pool;if(pool===null)return fail('MATCH_POOL_POLICY_PENDING',503);if(rankEligibility&&rankEligibility.pool!==pool)return fail('MATCH_POOL_CLASS_CHANGED');
  const xpPlan=b.mode==='normal'?await prepareNormalXpPlayer(tx,id,units.members,this.catalog()!,this.policy()!,this.normalXpPolicy()):null;
  const commanderConfig=b.mode==='normal'?this.commanderPolicy():null;if(commanderConfig){validateCommanderPolicy(commanderConfig);const account=await readCommanderState(tx,id,commanderConfig);if(account.status==='POLICY_MISMATCH')return fail('COMMANDER_ACCOUNT_POLICY_MISMATCH',503);if(account.status==='DATA_INVALID')return fail('COMMANDER_XP_DATA_INVALID',503);}
  const commanderPin=commanderConfig?structuredClone(commanderConfig):null;
  for(const m of units.members){const n=await tx.roster60Owned.updateMany({where:{id:m.id,playerId:id,status:'AVAILABLE',locked:false},data:{status:'PVP_QUEUE'}});if(n.count!==1)return fail('UNIT_UNAVAILABLE');}
  const rating=await matchmakingRating(tx,id,b.mode==='rank'?'ranked':'normal'),rank=await tx.rankedProfile.findUnique({where:{playerId:id}}),lease=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);
  await tx.roster60PvpQueue.create({data:{playerId:id,mode:b.mode,rating:Math.round(rating),tierIndex:rank?.tierIndex??0,ownedIds:JSON.stringify(ids),queuedAt:new Date(this.now()),leaseUntil:lease,normalXpSnapshot:xpPlan?JSON.stringify(xpPlan):null,normalXpCatalogVersion:xpPlan?.policy.catalogVersion??null,commanderXpSnapshot:b.mode==='normal'?JSON.stringify({playerId:id,policy:commanderPin}):null,rankPolicyVersion:admission?roster60RankPolicyVersion:null,reincarnationPool:pool,reincarnationCount:history.count,reincarnationCountSource:history.countSource,reincarnationHistoryStamp:history.historyStamp,matchPoolPolicyVersion:roster60MatchPoolPolicyVersion,rankAdmissionSnapshot:admission?JSON.stringify(admission):null}});
  await tx.realtimeActivity.create({data:{playerId:id,kind:'ROSTER_QUEUE',targetId:id,expiresAt:lease}});await this.match(tx,b.mode);return this.currentIn(tx,id);
 });}
 private async findRoom(tx:Tx,id:string){const rooms=await tx.roster60PvpMatch.findMany({where:{OR:[{player0Id:id},{player1Id:id}]},orderBy:{createdAt:'desc'},take:20});return rooms.find(r=>!this.parse(r).ack[this.side(r,id)]);}
 async current(id:string){return this.tx(async tx=>this.currentIn(tx,id));}
 private async currentIn(tx:Tx,id:string){
  await this.expireQueue(tx);const q=await tx.roster60PvpQueue.findUnique({where:{playerId:id}});
  if(q){const lease=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);await tx.roster60PvpQueue.update({where:{playerId:id},data:{leaseUntil:lease}});await tx.realtimeActivity.updateMany({where:{playerId:id,kind:'ROSTER_QUEUE'},data:{expiresAt:lease}});await this.match(tx,q.mode as Mode);}
  const r=await this.findRoom(tx,id);if(r)return {status:'matched',mode:r.mode,serverNow:this.now(),battle:await this.view(tx,await this.advance(tx,r,id),id)};
  const normalXpCondition=q?.mode!=='normal'?null:!q.normalXpSnapshot||!q.normalXpCatalogVersion?'NORMAL_XP_POLICY_PENDING':q.normalXpCatalogVersion!==safeKnowledgeCatalog(this.catalog)?.version?'NORMAL_XP_CATALOG_CHANGED':null;
  const matchPoolCondition=q?await this.matchPoolCondition(tx,id,q.reincarnationPool,q.matchPoolPolicyVersion,q.reincarnationCount,q.reincarnationCountSource,q.reincarnationHistoryStamp):null;
  const rankCondition=q?.mode==='rank'?await this.rankQueueCondition(tx,q):null;
  const saved=q?null:await tx.roster60PvpParty.findUnique({where:{playerId:id}});
  const selection=roster60PartySelection(q?JSON.parse(q.ownedIds):saved?JSON.parse(saved.ownedIds):[]);
  return {status:q?'waiting':'idle',...selection,mode:q?.mode,queuedAt:q?.queuedAt.getTime()??null,aiAvailableAt:q?.mode==='normal'?q.queuedAt.getTime()+REALTIME_LIMITS.casualAiAfterMs:null,normalXpCondition,matchPoolCondition,matchPool:q?.reincarnationPool??null,matchReincarnationCount:q?.reincarnationCount??null,matchReincarnationCountSource:q?.reincarnationCountSource??null,matchPoolScope:'normal-and-rank',...(q?.mode==='rank'?{rankCondition,rankPool:q.reincarnationPool,rankScope:'rank-only'}:{}),cancelAllowed:!!q,serverNow:this.now()};
 }
 // Both new modes require a verified history pool before LIMIT. Revalidate
 // in pages so stale accounts cannot fill the candidate window indefinitely.
 private async matchingQueues(tx:Tx,mode:Mode){
  const currentVersion=mode==='normal'?safeKnowledgeCatalog(this.catalog)?.version:undefined;
  if(mode==='normal'&&!currentVersion)return [];
  const rows:Roster60PvpQueue[]=[];let cursor:string|undefined;
  while(rows.length<REALTIME_LIMITS.maxWaiting){
   const page=await tx.roster60PvpQueue.findMany({where:{mode,leaseUntil:{gt:new Date(this.now())},matchPoolPolicyVersion:roster60MatchPoolPolicyVersion,reincarnationPool:{in:['FIRST_LIFE','REBORN']},reincarnationCountSource:{not:null},reincarnationHistoryStamp:{not:null},...(mode==='rank'?{rankPolicyVersion:roster60RankPolicyVersion}:{normalXpSnapshot:{not:null},normalXpCatalogVersion:currentVersion})},orderBy:[{queuedAt:'asc'},{playerId:'asc'}],take:REALTIME_LIMITS.maxWaiting,...(cursor?{cursor:{playerId:cursor},skip:1}:{})});
   for(const row of page)if(!await this.matchQueueCondition(tx,row)){rows.push(row);if(rows.length===REALTIME_LIMITS.maxWaiting)break;}
   if(page.length<REALTIME_LIMITS.maxWaiting)break;cursor=page[page.length-1].playerId;
  }
  return rows;
 }
 private async match(tx:Tx,mode:Mode){const rows=await this.matchingQueues(tx,mode);const used=new Set<string>();let created=0;for(const a of rows){if(used.has(a.playerId))continue;const gap=(x:typeof a)=>Math.min(1200,100+Math.floor((this.now()-x.queuedAt.getTime())/5000)*25),tierGap=(x:typeof a)=>Math.floor((this.now()-x.queuedAt.getTime())/30000);const b=orderRebirthCandidates(a,rows.filter(x=>x.playerId!==a.playerId&&!used.has(x.playerId)&&Math.abs(a.rating-x.rating)<=Math.max(gap(a),gap(x))&&a.reincarnationPool===x.reincarnationPool&&(mode!=='rank'||Math.abs(a.tierIndex-x.tierIndex)<=Math.max(tierGap(a),tierGap(x)))))[0];if(!b)continue;if(created>=REALTIME_LIMITS.maxMatchesPerPass||await tx.roster60PvpMatch.count({where:{status:{in:['ACTIVE','ACCEPTING']}}})>=REALTIME_LIMITS.maxRooms)return;created++;
 if(await this.matchQueueCondition(tx,a)||await this.matchQueueCondition(tx,b))continue;
 const c=this.catalog(),policy=this.policy();validatePolicy(policy,c);const p0=await this.members(tx,a.playerId,JSON.parse(a.ownedIds),'PVP_QUEUE',mode),p1=await this.members(tx,b.playerId,JSON.parse(b.ownedIds),'PVP_QUEUE',mode);const id=randomUUID(),state:Shared={entered:[[p0.fighters[0].ownedId!],[p1.fighters[0].ownedId!]],turn:0,parties:[p0.fighters,p1.fighters],pending:[null,null],accepted:[false,false],ack:[false,false],misses:[0,0],winner:null,events:[]};
 const observedDefense=await captureDefenseSnapshot(tx,c!,{'0':{playerId:a.playerId,targets:p1.fighters},'1':{playerId:b.playerId,targets:p0.fighters}});
 const matchPool:MatchPoolSnapshot={version:roster60MatchPoolPolicyVersion,players:[a,b].map(q=>({playerId:q.playerId,pool:q.reincarnationPool as RankPool,count:q.reincarnationCount,countSource:q.reincarnationCountSource as MatchPoolSnapshot['players'][number]['countSource'],historyStamp:q.reincarnationHistoryStamp!}))};
 const admission:RankAdmissionSnapshot|undefined=mode==='rank'?{version:roster60RankPolicyVersion,players:[readRankAdmission(a.rankAdmissionSnapshot)!,readRankAdmission(b.rankAdmissionSnapshot)!]}:undefined;
 const normalXp:NormalXpRoomSnapshot|undefined=mode==='normal'?{version:'normal-xp-room-v1',players:[queuedNormalXpPlan(a.normalXpSnapshot,a.playerId,p0.members,c!.version),queuedNormalXpPlan(b.normalXpSnapshot,b.playerId,p1.members,c!.version)].filter((p):p is NonNullable<typeof p>=>p!==null)}:undefined;
 await tx.roster60PvpMatch.create({data:{id,presence:JSON.stringify(initializePresence(await this.readPresenceMonitor(tx),this.now(),this.presencePolicy)),player0Id:a.playerId,player1Id:b.playerId,mode,seed:randomBytes(32).toString('hex'),snapshot:JSON.stringify({commanderPolicies:mode==='normal'?[a,b].map(q=>({playerId:q.playerId,policy:this.queuedCommanderPolicy(q.commanderXpSnapshot,q.playerId)})):undefined,matchPool,rankAdmission:admission,normalXp,policy,catalogVersion:c!.version,observedDefense,items:loadBattleRules().data.items,members:[...p0.members,...p1.members],queues:[a,b].map(q=>({rating:q.rating,tierIndex:q.tierIndex,queuedAt:q.queuedAt.toISOString()}))} satisfies Snapshot),state:JSON.stringify(state),deadline:new Date(this.now()+REALTIME_LIMITS.acceptMs)}});
 for(const q of [a,b]){await tx.roster60Owned.updateMany({where:{playerId:q.playerId,id:{in:JSON.parse(q.ownedIds)},status:'PVP_QUEUE'},data:{status:'PVP'}});await tx.roster60PvpQueue.delete({where:{playerId:q.playerId}});await tx.realtimeActivity.update({where:{playerId:q.playerId},data:{kind:'ROSTER_ROOM',targetId:id,expiresAt:new Date(this.now()+REALTIME_LIMITS.roomMaxMs)}});used.add(q.playerId);}
 }
 // Human matching and fallback share the writer lock with cancel/join/current.
 // Rank is always human-only; every normal queue retains its own server deadline.
 if(mode!=='normal')return;
 for(const q of rows){
  if(used.has(q.playerId)||this.now()<q.queuedAt.getTime()+REALTIME_LIMITS.casualAiAfterMs)continue;
  if(created>=REALTIME_LIMITS.maxMatchesPerPass||await tx.roster60PvpMatch.count({where:{status:{in:['ACTIVE','ACCEPTING']}}})>=REALTIME_LIMITS.maxRooms)return;
  await this.createBotRoom(tx,q);created++;
 }
 }
 private async createBotRoom(tx:Tx,q:Roster60PvpQueue){
 if(q.mode!=='normal'||this.now()<q.queuedAt.getTime()+REALTIME_LIMITS.casualAiAfterMs||q.leaseUntil.getTime()<=this.now())return fail('INVALID_AI_QUEUE');
 const condition=await this.matchQueueCondition(tx,q);if(condition)return fail(condition);
 const c=this.catalog(),policy=this.policy();validatePolicy(policy,c);
 const human=await this.members(tx,q.playerId,JSON.parse(q.ownedIds),'PVP_QUEUE','normal');
 const encounters=policy.encounters.filter(e=>e.mode==='duel'&&!e.tutorial);
 if(!encounters.length)return fail('AI_POLICY_PENDING',503);
 const id=randomUUID(),seed=randomBytes(32).toString('hex');
 // Reuse pinned practice encounter levels/species and strategy; no new stat tuning.
 const bot=human.fighters.map((f,i)=>{const encounter=[...encounters].sort((a,b)=>Math.abs(a.level-f.level)-Math.abs(b.level-f.level)||a.id.localeCompare(b.id))[0],key='AI:'+id+':'+i;return {...fighter(c!,selectEnemySpecies(encounter,policy.encounters,seed+':'+i),encounter.level,key),ownedId:key};});
 const state:Shared={entered:[[human.fighters[0].ownedId!],[bot[0].ownedId!]],botSide:1,turn:0,parties:[human.fighters,bot],pending:[null,null],accepted:[false,true],ack:[false,true],misses:[0,0],winner:null,events:[]};
 const observedDefense=await captureDefenseSnapshot(tx,c!,{'0':{playerId:q.playerId,targets:bot}});
 const matchPool:MatchPoolSnapshot={version:roster60MatchPoolPolicyVersion,players:[{playerId:q.playerId,pool:q.reincarnationPool as RankPool,count:q.reincarnationCount,countSource:q.reincarnationCountSource as MatchPoolSnapshot['players'][number]['countSource'],historyStamp:q.reincarnationHistoryStamp!}]};
 const xpPlan=queuedNormalXpPlan(q.normalXpSnapshot,q.playerId,human.members,c!.version),normalXp:NormalXpRoomSnapshot={version:'normal-xp-room-v1',players:xpPlan?[xpPlan]:[]};
 await tx.roster60PvpMatch.create({data:{id,presence:JSON.stringify(initializePresence(await this.readPresenceMonitor(tx),this.now(),this.presencePolicy,1)),player0Id:q.playerId,player1Id:'AI:'+id,mode:'normal',seed,snapshot:JSON.stringify({commanderPolicies:[{playerId:q.playerId,policy:this.queuedCommanderPolicy(q.commanderXpSnapshot,q.playerId)}],matchPool,normalXp,policy,catalogVersion:c!.version,observedDefense,items:loadBattleRules().data.items,members:human.members,queues:[{rating:q.rating,tierIndex:q.tierIndex,queuedAt:q.queuedAt.toISOString()}]} satisfies Snapshot),state:JSON.stringify(state),deadline:new Date(this.now()+REALTIME_LIMITS.acceptMs)}});
 await tx.roster60Owned.updateMany({where:{playerId:q.playerId,id:{in:JSON.parse(q.ownedIds)},status:'PVP_QUEUE'},data:{status:'PVP'}});
 await tx.roster60PvpQueue.delete({where:{playerId:q.playerId}});
 await tx.realtimeActivity.update({where:{playerId:q.playerId},data:{kind:'ROSTER_ROOM',targetId:id,expiresAt:new Date(this.now()+REALTIME_LIMITS.roomMaxMs)}});
 }
 private primeBot(s:Shared,snap:Snapshot){
 if(s.botSide!==1)return;
 const party=s.parties[1];
 if(party[0].hp<=0){const next=party.findIndex((f,i)=>i>0&&f.hp>0);if(next>0){const before=capturePresentation(s.parties.map(p=>p[0]));this.switch(s,1,party[next].ownedId!);s.events.push({turn:s.turn,events:[{side:1,kind:'forced-switch',presentation:presentStep(before,s.parties.map(p=>p[0]))}]});}}
 if(s.parties.some(p=>p[0].hp<=0)||s.pending[1])return;
 const enemy=party[0],self=s.parties[0][0];
 let skill=[enemy.attack,...enemy.skills].find(sk=>sk.mpCost<=enemy.mp);
 if(snap.policy.enemyStrategy==='best-affordable'){
  const score=(sk:typeof enemy.attack)=>fiveStatAttack({power:sk.power,level:enemy.level,durability:self.stats.durability,suitability:enemy.suitability[sk.element],defense:self.defense[sk.element],attackerHp:enemy.hp,defenderHp:self.hp},snap.policy.calculation,(b,f)=>b*f);
  const usable=[enemy.attack,...enemy.skills].filter(sk=>sk.mpCost<=enemy.mp);
  usable.sort((a,b)=>{const x=score(a),y=score(b);return (y.reflected?-y.damage:y.damage)-(x.reflected?-x.damage:x.damage)||a.mpCost-b.mpCost;});
  skill=usable[0];const bestNow=skill?score(skill).damage:0,afterFocus=focusMp(enemy.mp,enemy.stats.mp).mp;
  if(enemy.skills.some(sk=>sk.mpCost>enemy.mp&&sk.mpCost<=afterFocus&&!score(sk).reflected&&score(sk).damage>2*bestNow))skill=undefined;
 }
 s.pending[1]=!skill?{kind:'focus'}:skill===enemy.attack?{kind:'attack'}:{kind:'skill',skillId:skill.id};
 }
 async accept(id:string,b:{requestId:string;battleId:string;revision:number;accept:boolean}){return this.request(id,b.requestId,['accept',b.battleId,b.revision,b.accept],async tx=>{let r=await this.advance(tx,await this.room(tx,id,b.battleId),id);if(r.status!=='ACCEPTING'){if(!b.accept){const activity=await tx.realtimeActivity.findUnique({where:{playerId:id}});if(activity?.kind==='ROSTER_QUEUE'&&activity.targetId===r.id)await this.removeQueue(tx,id);}return this.view(tx,r,id);}if(r.revision!==b.revision)return fail('STALE_REVISION');const s=this.parse(r),side=this.side(r,id);if(!b.accept){s.accepted[side]=false;r=await this.finish(tx,r,s,null,'MATCH_DECLINED');return this.view(tx,r,id);}s.accepted[side]=true;if(s.accepted.every(Boolean)){this.primeBot(s,this.snap(r));if(r.mode==='rank')await registerRankedMatch(tx,{roomId:r.id,player0Id:r.player0Id,player1Id:r.player1Id,participant0Kind:'HUMAN',participant1Kind:'HUMAN'});r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{status:'ACTIVE',revision:{increment:1},state:JSON.stringify(s),deadline:new Date(this.now()+REALTIME_LIMITS.turnMs)}});}else r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s)}});return this.view(tx,r,id);});}
 private validate(s:Shared,side:Side,a:BattleAction,snap:Snapshot){const party=s.parties[side],active=party[0];if(a.kind==='escape')return;
 if(active.hp<=0&&a.kind!=='switch')return fail('FORCED_SWITCH_REQUIRED');
 if(a.kind==='switch'){if(!party.slice(1).some(f=>f.ownedId===a.targetOwnedId&&f.hp>0))return fail('INVALID_SWITCH');return;}
 if(a.kind==='item'){const it=snap.items[a.itemId??''];if(!it)return fail('INVALID_ITEM');const target=it.revive?party.find(f=>f.ownedId===a.targetOwnedId):active;if(!target||(!it.revive&&a.targetOwnedId&&a.targetOwnedId!==active.ownedId))return fail('INVALID_ITEM_TARGET');if(it.revive?target.hp>0:!active.hp)return fail('INVALID_ITEM_TARGET');if(!it.revive&&!it.heal)return fail('NO_STATUS_TO_CLEANSE');if(it.heal&&active.hp>=active.stats.hp)return fail('HP_FULL');return;}
 if(a.kind==='focus')return;const skill=a.kind==='attack'?active.attack:a.kind==='skill'?active.skills.find(x=>x.id===a.skillId):null;if(!skill)return fail('SKILL_NOT_EQUIPPED');if(skill.mpCost>active.mp)return fail('INSUFFICIENT_MP');
 }
 async action(id:string,b:{requestId:string;battleId:string;revision:number;action:BattleAction}){return this.request(id,b.requestId,['action',b.battleId,b.revision,b.action.kind,b.action.skillId??null,b.action.itemId??null,b.action.targetOwnedId??null],async tx=>{
 const before=await this.room(tx,id,b.battleId);let r=await this.advance(tx,before,id);if(r.status!=='ACTIVE'||r.revision!==before.revision)return this.view(tx,r,id);if(r.revision!==b.revision)return fail('STALE_REVISION');const s=this.parse(r),side=this.side(r,id),snap=this.snap(r);this.validate(s,side,b.action,snap);if(s.connectionPause)return this.view(tx,r,id);
 const actionKnowledge=sides.map(viewer=>battleKnowledge(snap.observedDefense,s.events,String(viewer)));
 const actionCatalog=safeKnowledgeCatalog(this.catalog);
 const actionIntels=()=>sides.map(viewer=>observedOpponentIntel(s.parties[1-viewer][0],s.turn,snap.observedDefense,actionKnowledge[viewer],actionCatalog));
 if(b.action.kind==='escape'){const pair=s.parties.map(p=>p[0]),intelBefore=actionIntels();s.events.push({turn:s.turn,events:[{side,kind:'surrender',presentation:presentStep(capturePresentation(pair),pair),intelBefore,intelAfter:actionIntels()}]});r=await this.finish(tx,r,s,(1-side) as Side,'SURRENDER');return this.view(tx,r,id);}
 if(s.parties[side][0].hp<=0){const stepBefore=capturePresentation(s.parties.map(p=>p[0])),intelBefore=actionIntels();s.misses[side]=0;this.switch(s,side,b.action.targetOwnedId!);this.primeBot(s,snap);s.events.push({turn:s.turn,events:[{side,kind:'forced-switch',presentation:presentStep(stepBefore,s.parties.map(p=>p[0])),intelBefore,intelAfter:actionIntels()}]});r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s),revision:{increment:1},deadline:new Date(this.now()+REALTIME_LIMITS.turnMs)}});return this.view(tx,r,id);}
 if(s.parties.some(p=>p[0].hp<=0))return fail('OPPONENT_FORCED_SWITCH');if(s.pending[side])return fail('ACTION_ALREADY_SUBMITTED');
 const action:Pending={...b.action};if(action.kind==='item'){const count=await tx.playerItem.updateMany({where:{playerId:id,itemId:action.itemId!,count:{gt:0}},data:{count:{decrement:1}}});if(count.count!==1)return fail('ITEM_UNAVAILABLE');await tx.realtimeItemEscrow.create({data:{roomId:r.id,phaseId:'roster-v1:'+s.turn,side,playerId:id,itemId:action.itemId!,state:'HELD'}});action.heldItem=action.itemId;}
 s.pending[side]=action;s.misses[side]=0;r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s)}});if(s.pending.every(Boolean))r=await this.resolve(tx,r,s);return this.view(tx,r,id);
 });}
 private switch(s:Shared,side:Side,target:string){const party=s.parties[side],ix=party.findIndex((f,i)=>i>0&&f.ownedId===target&&f.hp>0);if(ix<0)return fail('INVALID_SWITCH');[party[0],party[ix]]=[party[ix],party[0]];s.entered??=[[],[]];if(!s.entered[side].includes(target))s.entered[side].push(target);}
 private async refund(tx:Tx,r:Roster60PvpMatch,s:Shared,side:Side){const a=s.pending[side];if(!a?.heldItem)return;const released=await tx.realtimeItemEscrow.updateMany({where:{roomId:r.id,phaseId:'roster-v1:'+s.turn,side,playerId:this.ids(r)[side],itemId:a.heldItem,state:'HELD'},data:{state:'REFUNDED'}});if(released.count!==1)return fail('ITEM_RESERVATION_INVALID');await tx.playerItem.upsert({where:{playerId_itemId:{playerId:this.ids(r)[side],itemId:a.heldItem}},create:{playerId:this.ids(r)[side],itemId:a.heldItem,count:1},update:{count:{increment:1}}});delete a.heldItem;}
 private async resolve(tx:Tx,r:Roster60PvpMatch,s:Shared,timeoutGenerated=false){const snap=this.snap(r),events:any[]=[],observations:DefenseObservation[]=[];
 const knowledge=sides.map(side=>battleKnowledge(snap.observedDefense,s.events,String(side)));
 const currentCatalog=safeKnowledgeCatalog(this.catalog);
 const intels=()=>sides.map(side=>observedOpponentIntel(s.parties[1-side][0],s.turn,snap.observedDefense,knowledge[side],currentCatalog));
 const decision=decideTurnOrder(r.id,s.turn,[{id:'p0',baseSpeed:s.parties[0][0].stats.speed,modifiers:[]},{id:'p1',baseSpeed:s.parties[1][0].stats.speed,modifiers:[]}],r.seed);const priority=(side:Side)=>['switch','item'].includes(s.pending[side]!.kind)?1:0;const ordered=decision.order.map(x=>x==='p0'?0:1) as Side[];ordered.sort((a,b)=>priority(b)-priority(a));
 for(const side of ordered){const a=s.pending[side]!,party=s.parties[side],actor=party[0],target=s.parties[1-side][0];if(!actor.hp||!target.hp){await this.refund(tx,r,s,side);continue;}
 const stepBefore=capturePresentation(s.parties.map(p=>p[0]));
 const intelBefore=intels();
 if(a.kind==='switch'){this.switch(s,side,a.targetOwnedId!);events.push({side,kind:'switch',presentation:presentStep(stepBefore,s.parties.map(p=>p[0])),intelBefore,intelAfter:intels()});continue;}
 if(a.kind==='item'){const it=snap.items[a.itemId!],to=it.revive?party.find(f=>f.ownedId===a.targetOwnedId)!:actor;const before=to.hp;to.hp=it.revive?Math.round(to.stats.hp*it.revive/100):Math.min(to.stats.hp,to.hp+Math.round(to.stats.hp*it.heal!/100));const used=await tx.realtimeItemEscrow.updateMany({where:{roomId:r.id,phaseId:'roster-v1:'+s.turn,side,playerId:this.ids(r)[side],itemId:a.heldItem,state:'HELD'},data:{state:'CONSUMED'}});if(used.count!==1)return fail('ITEM_RESERVATION_INVALID');delete a.heldItem;events.push({side,kind:'item',itemId:a.itemId,recovered:to.hp-before,targetOwnedId:to.ownedId,presentation:presentStep(stepBefore,s.parties.map(p=>p[0])),intelBefore,intelAfter:intels()});continue;}
 if(a.kind==='focus'){const gain=focusMp(actor.mp,actor.stats.mp);actor.mp=gain.mp;events.push({side,kind:'focus',recovered:gain.recovered,presentation:presentStep(stepBefore,s.parties.map(p=>p[0])),intelBefore,intelAfter:intels()});continue;}
 const skill=a.kind==='attack'?actor.attack:actor.skills.find(x=>x.id===a.skillId)!;actor.mp=spendMp(actor.mp,skill.mpCost);const hit=fiveStatAttack({power:skill.power,level:actor.level,durability:target.stats.durability,suitability:actor.suitability[skill.element],defense:target.defense[skill.element],attackerHp:actor.hp,defenderHp:target.hp},snap.policy.calculation,(base,fit)=>base*fit);if(hit.target==='attacker')actor.hp-=hit.damage;else target.hp-=hit.damage;
 const defenseObservation=resolvedDefenseObservation(target,skill.element,hit,String(side),snap.observedDefense,currentCatalog,knowledge[side]);
 if(defenseObservation){observations.push(defenseObservation);addDefenseObservation(knowledge[side],defenseObservation,snap.observedDefense);}
 events.push({side,kind:'skill',skillId:skill.id,...hit,presentation:presentStep(stepBefore,s.parties.map(p=>p[0]),skill),...(defenseObservation?{defenseObservation}:{}),intelBefore,intelAfter:intels()});
 }
 s.events.push({turn:s.turn,decision,events});s.turn++;s.pending=[null,null];
 // Knowledge, battle CAS, settlement and request receipt commit or roll back together.
 const claimed=await tx.roster60PvpMatch.updateMany({where:{id:r.id,revision:r.revision,status:'ACTIVE'},data:{state:JSON.stringify(s)}});if(claimed.count!==1)return fail('STALE_REVISION');
 await persistDefenseObservations(tx,s.botSide===1?{'0':r.player0Id}:{'0':r.player0Id,'1':r.player1Id},observations);
 const dead=s.parties.map(p=>p.every(f=>f.hp<=0));if(dead[0]||dead[1])return this.finish(tx,r,s,dead[0]&&dead[1]?null:dead[0]?1:0,'BATTLE',timeoutGenerated);if(s.turn>=snap.policy.maxRounds)return this.finish(tx,r,s,null,'BATTLE',timeoutGenerated);this.primeBot(s,snap);return tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s),revision:{increment:1},deadline:new Date(this.now()+REALTIME_LIMITS.turnMs)}});
 }
 private async advance(tx:Tx,r:Roster60PvpMatch,observerId?:string){
  if(!['ACTIVE','ACCEPTING'].includes(r.status))return r;let state=this.parse(r);const now=this.now();
  if(r.status==='ACCEPTING'){
   const poolConditions=await this.matchRoomConditions(tx,r),conditions=r.mode==='rank'?await this.rankRoomConditions(tx,r):{};
   if(Object.keys(conditions).length||Object.keys(poolConditions).length)return this.finish(tx,r,state,null,'SERVER_ABORT',false,conditions,poolConditions);
  }
  if(now-r.createdAt.getTime()>=REALTIME_LIMITS.roomMaxMs)return this.finish(tx,r,state,null,r.status==='ACCEPTING'?'MATCH_TIMEOUT':'SERVER_ABORT');
  if(r.status==='ACCEPTING'&&r.deadline.getTime()<=now)return this.finish(tx,r,state,null,'MATCH_TIMEOUT');
  const monitor=await this.readPresenceMonitor(tx);let presence=this.presenceValue(r),decision=decidePresence(presence,monitor,now);
  if(r.status==='ACTIVE'){
   if(decision.kind==='FORFEIT_PROVEN'){
    if(!validatePresenceForfeitProof(decision.proof,{state:presence,monitor}))return this.finish(tx,r,state,null,'SERVER_ABORT');
    return this.finish(tx,r,state,decision.proof.survivorSide,'TIMEOUT',false,{},{},decision.proof);
   }
   if(decision.kind==='ABORT_NEUTRAL')return this.finish(tx,r,state,null,decision.reason==='SURVIVOR_UNPROVEN'?'DOUBLE_TIMEOUT':'SERVER_ABORT');
  }
  if(observerId){
   const observation=observePresence(presence,this.side(r,observerId),monitor,now);
   if(observation.state&&observation.decision.kind!=='FORFEIT_PROVEN'&&observation.decision.kind!=='ABORT_NEUTRAL'){
    presence=observation.state;decision=observation.decision;r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{presence:JSON.stringify(presence)}});
   }
  }
  if(r.status==='ACCEPTING')return r;
  const current=presence as PresenceState;
  if(decision.kind==='RECONNECT_GRACE'||this.connectionWait(r,state,current,now)){
   const wasPaused=!!state.connectionPause;state.connectionPause??={observedAfter:r.deadline.getTime()-REALTIME_LIMITS.turnMs,startedAt:now};
   const checks=current.participants.filter((p):p is NonNullable<typeof p>=>p!==null).filter(p=>p.lastSeenAt<=state.connectionPause!.observedAfter||p.leaseUntil<=now).map(p=>p.leaseUntil>now?p.leaseUntil:p.lastSeenAt+current.policy.totalAbsentMs);
   const deadline=new Date(Math.max(now+1,Math.min(...checks,now+REALTIME_LIMITS.turnMs)));
   if(!wasPaused||r.deadline.getTime()!==deadline.getTime())r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{...(!wasPaused?{state:JSON.stringify(state)}:{}),deadline}});
   return r;
  }
  if(state.connectionPause){delete state.connectionPause;return tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(state),deadline:new Date(now+REALTIME_LIMITS.turnMs)}});}
  if(r.deadline.getTime()>now)return r;
  const forced=state.parties.map(p=>!p[0].hp);
  for(const side of sides)if(side!==state.botSide&&(forced.some(Boolean)?forced[side]:!state.pending[side]))state.misses[side]++;
  const lost=sides.filter(i=>state.misses[i]>=3);if(lost.length)return this.finish(tx,r,state,lost.length===2?null:(1-lost[0]) as Side,lost.length===2?'DOUBLE_TIMEOUT':'TIMEOUT');
  if(forced.some(Boolean))return tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(state),deadline:new Date(now+REALTIME_LIMITS.turnMs)}});
  for(const side of sides)if(!state.pending[side])state.pending[side]=state.parties[side][0].mp>=state.parties[side][0].attack.mpCost?{kind:'attack'}:{kind:'focus'};
  return this.resolve(tx,r,state,true);
 }
 private async finish(tx:Tx,r:Roster60PvpMatch,s:Shared,winner:Side|null,reason:'BATTLE'|'SURRENDER'|'TIMEOUT'|'DOUBLE_TIMEOUT'|'MATCH_TIMEOUT'|'MATCH_DECLINED'|'SERVER_ABORT',timeoutGenerated=false,admissionConditions:Record<string,string>={},poolConditions:Record<string,string>={},terminationProof?:PresenceForfeitProof){
 const rankConditions={...admissionConditions},matchPoolConditions={...poolConditions};
 for(const side of sides)await this.refund(tx,r,s,side);s.pending=[null,null];s.winner=winner;const pair={roomId:r.id,player0Id:r.player0Id,player1Id:r.player1Id,participant0Kind:'HUMAN' as const,participant1Kind:'HUMAN' as const};
 const rating=s.botSide===1||reason==='MATCH_TIMEOUT'||reason==='MATCH_DECLINED'||r.status==='ACCEPTING'&&reason==='SERVER_ABORT'?null:r.mode==='rank'?await finishAndSettleRankedMatch(tx,{roomId:r.id,winner,reason,now:new Date(this.now())}):await settleCasualHuman(tx,{...pair,winner,reason,now:new Date(this.now())});
 if(s.botSide!==1&&reason==='BATTLE'&&winner!==null){const p=await tx.player.findUniqueOrThrow({where:{id:this.ids(r)[winner]}});await awardPlayPoints(tx,p,3,new Date(this.now()));}
 // Normal XP is pinned at admission and committed with room finish. Ranked XP is unchanged.
 const normalXp=await settleNormalXp(tx,r,s,this.snap(r),reason,winner,timeoutGenerated,terminationProof);
 const commanderRewards:Record<string,CommanderXpResult>={};
 if(r.mode==='normal')for(const side of sides){if(side===s.botSide)continue;const playerId=this.ids(r)[side],config=this.snap(r).commanderPolicies?.find(p=>p.playerId===playerId)?.policy??null;
  const outcome:CommanderEvent['outcome']=reason==='SURRENDER'?'surrender':reason==='SERVER_ABORT'||reason==='DOUBLE_TIMEOUT'?'aborted':terminationProof?.departedSide===side?'departed':winner===null?'draw':winner===side?'win':'loss';
  commanderRewards[playerId]=await awardCommanderXp(tx,playerId,{kind:'NORMAL_PLAY',eventId:r.id,outcome,reason,timeoutGenerated,...(terminationProof?{terminationProof}:{}),side},config);
 }
 const partyConditions:Record<string,string>={};
 const abortedAdmission=r.status==='ACCEPTING'&&(Object.keys(rankConditions).length>0||Object.keys(matchPoolConditions).length>0);
 for(const m of this.snap(r).members){const gain=normalXp?.appliedByOwnedId[m.id]??0;const n=await tx.roster60Owned.updateMany({where:{id:m.id,playerId:m.playerId,status:'PVP',...(!abortedAdmission?{totalXp:BigInt(m.xp)}:{})},data:{status:'AVAILABLE',...(gain?{totalXp:BigInt(m.xp)+BigInt(gain)}:{})}});if(n.count!==1)return fail('UNIT_STATE_CHANGED');}
 await tx.realtimeActivity.deleteMany({where:{kind:'ROSTER_ROOM',targetId:r.id}});
 if(reason==='MATCH_TIMEOUT'||reason==='MATCH_DECLINED')for(const side of sides)if(side!==s.botSide&&s.accepted[side]){
  const id=this.ids(r)[side],snap=this.snap(r),q=snap.queues?.[side],ownedIds=snap.members.filter(m=>m.playerId===id).map(m=>m.id);if(!q)continue;
  if(!isCurrentRoster60Party(ownedIds)){partyConditions[id]='PARTY_RESELECTION_REQUIRED';continue;}
  const historyPin=snap.matchPool?.players.find(p=>p.playerId===id),roomPool=historyPin?.pool;
  const poolCondition=await this.matchPoolCondition(tx,id,roomPool,snap.matchPool?.version,historyPin?.count,historyPin?.countSource,historyPin?.historyStamp);if(poolCondition){matchPoolConditions[id]=poolCondition;continue;}
  const pin=r.mode==='rank'&&snap.rankAdmission?.version===roster60RankPolicyVersion?readRankAdmission(JSON.stringify(snap.rankAdmission.players.find(p=>p.playerId===id)??null)):null;
  if(r.mode==='rank'){const condition=await this.rankPinCondition(tx,id,ownedIds,pin,'AVAILABLE');if(condition){rankConditions[id]=condition;continue;}}
  const lease=new Date(this.now()+REALTIME_LIMITS.queueLeaseMs);await tx.roster60Owned.updateMany({where:{playerId:id,id:{in:ownedIds},status:'AVAILABLE'},data:{status:'PVP_QUEUE'}});
  await tx.roster60PvpQueue.create({data:{playerId:id,mode:r.mode,rating:q.rating,tierIndex:q.tierIndex,ownedIds:JSON.stringify(ownedIds),queuedAt:new Date(q.queuedAt),leaseUntil:lease,normalXpSnapshot:r.mode==='normal'&&snap.normalXp?.players.find(p=>p.playerId===id)?JSON.stringify(snap.normalXp.players.find(p=>p.playerId===id)):null,normalXpCatalogVersion:r.mode==='normal'?snap.normalXp?.players.find(p=>p.playerId===id)?.policy.catalogVersion??null:null,commanderXpSnapshot:r.mode==='normal'?JSON.stringify({playerId:id,policy:snap.commanderPolicies?.find(p=>p.playerId===id)?.policy??null}):null,rankPolicyVersion:pin?roster60RankPolicyVersion:null,reincarnationPool:roomPool??null,reincarnationCount:historyPin?.count??null,reincarnationCountSource:historyPin?.countSource??null,reincarnationHistoryStamp:historyPin?.historyStamp??null,matchPoolPolicyVersion:roster60MatchPoolPolicyVersion,rankAdmissionSnapshot:pin?JSON.stringify(pin):null}});
  await tx.realtimeActivity.create({data:{playerId:id,kind:'ROSTER_QUEUE',targetId:r.id,expiresAt:lease}});s.ack[side]=true;
 }
 const terminationKind:Roster60FeedbackTerminationKind|undefined=terminationProof?'DISCONNECTED':reason==='TIMEOUT'||reason==='DOUBLE_TIMEOUT'||timeoutGenerated?'TURN_INPUT':reason==='SERVER_ABORT'?'SERVER_OBSERVATION_GAP':undefined;
 const settlement={winner,reason,...(Object.keys(partyConditions).length?{partyConditions}:{}),...(r.mode==='normal'?{commanderRewards}:{}),...(terminationKind?{terminationKind}:{}),...(Object.keys(rankConditions).length?{rankConditions}:{}),...(Object.keys(matchPoolConditions).length?{matchPoolConditions}:{}),xpAwarded:normalXp?Object.values(normalXp.players).reduce((sum,p)=>sum+p.xpAwarded,0):0,rating,...(normalXp?{normalXp}:{})};return tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s),status:'FINISHED',settlement:JSON.stringify(settlement),revision:{increment:1}}});
 }
 async settle(id:string,b:{requestId:string;battleId:string;revision:number}){return this.request(id,b.requestId,['settle',b.battleId,b.revision],async tx=>{let r=await this.advance(tx,await this.room(tx,id,b.battleId),id);if(!['FINISHED','SETTLED'].includes(r.status))return fail('BATTLE_NOT_FINISHED');const s=this.parse(r);s.ack[this.side(r,id)]=true;r=await tx.roster60PvpMatch.update({where:{id:r.id},data:{state:JSON.stringify(s),status:'SETTLED'}});return this.view(tx,r,id);});}
}
