import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {RosterError} from '../roster60-onboarding.js';
import {growthAt,instanceGrowthCurve,rosterMaxLevel,type GrowthPoint,type ReincarnationXpPolicy} from '../battle/roster60-rules.js';
import type {State} from '../roster60-battle.js';
import {normalXpGrant} from './normal-xp.js';
import {FINISHER_SPLIT_DISTRIBUTION,finisherSplitShares} from './finisher-split.js';

/** An additive reward schedule, captured only when a new battle/queue is created. */
export type UnitXpPacing={approved:true;version:string;catalogVersion:string;winsPerLevel:2|1.5;difficulty:'owned-level-capped-by-enemy';distribution:'equal-actually-entered'|'finisher-full-rest-half';existingRewards:'keep-higher';capOverflow:'discard-overflow'};
export type PacingMember={ownedId:string;playerId:string;startingXp:string;growth:GrowthPoint[];reincarnation:number;reincarnationXpPolicy?:ReincarnationXpPolicy};
export type PacedBattleXpPlan={version:'paced-battle-xp-v1';battleId:string;playerId:string;policy:UnitXpPacing;enemyLevel:number;sourceWinXp:number;sourceLossXp:number;baseline:'total'|'per-entered';reserveHalf:boolean;recipientMode?:'initial-owned'|'entered';requireSurvivor?:boolean;members:PacingMember[]};
const fail=(code='UNIT_XP_PACING_INVALID'):never=>{throw new RosterError(503,code);};
const amount=(n:unknown):n is number=>Number.isSafeInteger(n)&&Number(n)>=0&&Number(n)<=2_000_000_000;
const level=(n:unknown):n is number=>Number.isSafeInteger(n)&&Number(n)>=1&&Number(n)<=99;
export function validateUnitXpPacing(raw:unknown,catalogVersion:string):UnitXpPacing{
 const p=raw as UnitXpPacing;const keys=['approved','version','catalogVersion','winsPerLevel','difficulty','distribution','existingRewards','capOverflow'];
 if(!p||Array.isArray(p)||Object.keys(p).sort().join()!==keys.sort().join()||p.approved!==true||typeof p.version!=='string'||!p.version||p.catalogVersion!==catalogVersion||!(p.winsPerLevel===2&&p.distribution==='equal-actually-entered'||p.winsPerLevel===1.5&&p.distribution===FINISHER_SPLIT_DISTRIBUTION)||p.difficulty!=='owned-level-capped-by-enemy'||p.existingRewards!=='keep-higher'||p.capOverflow!=='discard-overflow')return fail();
 return p;
}
export function unitXpPacingReader(root:string,profile?:string):()=>UnitXpPacing|null{
 if(profile&&profile!=='five-stat-device-test-v1')throw Error('UNKNOWN_ROSTER60_PROFILE');
 const file=resolve(root,profile?'data/test-profiles/'+profile+'/unit-xp-pacing-policy.json':'data/roster60-unit-xp-pacing-policy.json');
 return ()=>{if(!existsSync(file))return null;let raw:any;try{raw=JSON.parse(readFileSync(file,'utf8'));}catch{return fail();}return validateUnitXpPacing(raw,raw?.catalogVersion);};
}

/** Sparse normal-practice tiers already represent the nearest starting-level band.
 * A pinned nearest-tier minimum lets Lv15 vs its existing Lv10 AI count as
 * appropriate; a genuinely lower opponent never borrows the owned Lv15 curve. */
export function pacedUnitWin(member:Pick<PacingMember,'startingXp'|'growth'|'reincarnation'|'reincarnationXpPolicy'>,enemyLevel:number,policy:UnitXpPacing,nearestTierMinimum?:number){
 validateUnitXpPacing(policy,policy.catalogVersion);if(!level(enemyLevel)||nearestTierMinimum!==undefined&&!level(nearestTierMinimum))return fail();
 const grown=growthAt(Number(member.startingXp),member.growth,member.reincarnation,member.reincarnationXpPolicy);
 if(grown.level===grown.maxLevel)return 0;
 const difficulty=nearestTierMinimum!==undefined&&enemyLevel>=nearestTierMinimum?grown.level:Math.min(grown.level,enemyLevel);
 const curve=instanceGrowthCurve(member.growth,member.reincarnation,member.reincarnationXpPolicy);
 if(curve.length<rosterMaxLevel(member.reincarnation)||!curve[difficulty])return fail();
 // 정수만으로 나눈다(2승: /2, 1.5승: ×2/3). 나누어떨어지면 부동소수 오차 없이 그 값이다.
 return Math.ceil((curve[difficulty].totalXp-curve[difficulty-1].totalXp)*2/(policy.winsPerLevel*2));
}
export function pacedPool(members:(Pick<PacingMember,'startingXp'|'growth'|'reincarnation'|'reincarnationXpPolicy'>&{nearestTierMinimum?:number})[],enemyLevel:number,legacyPool:number,policy:UnitXpPacing){
 if(!amount(legacyPool)||!members.length)return fail();
 const floor=Math.max(...members.map(member=>pacedUnitWin(member,enemyLevel,policy,member.nearestTierMinimum)))*members.length;
 const total=Math.max(legacyPool,floor);if(!amount(total))return fail();return total;
}
/** 마무리 전액 판: 기존 보상과 마무리 유닛의 「적정 레벨 승리 N번에 1레벨」 최소값 중 큰 값. */
export function pacedFinisherWin(finisher:Pick<PacingMember,'startingXp'|'growth'|'reincarnation'|'reincarnationXpPolicy'>,enemyLevel:number,legacyWin:number,policy:UnitXpPacing,nearestTierMinimum?:number){
 if(!amount(legacyWin))return fail();
 const total=Math.max(legacyWin,pacedUnitWin(finisher,enemyLevel,policy,nearestTierMinimum));if(!amount(total))return fail();return total;
}
export function createPacedBattleXpPlan(input:Omit<PacedBattleXpPlan,'version'>):PacedBattleXpPlan{
 validateUnitXpPacing(input.policy,input.policy.catalogVersion);
 if(!input.battleId||!input.playerId||!level(input.enemyLevel)||!amount(input.sourceWinXp)||!amount(input.sourceLossXp)||input.sourceLossXp>input.sourceWinXp||!['total','per-entered'].includes(input.baseline)||typeof input.reserveHalf!=='boolean'||input.recipientMode!==undefined&&!['initial-owned','entered'].includes(input.recipientMode)||input.requireSurvivor!==undefined&&typeof input.requireSurvivor!=='boolean'||!input.members.length||input.members.length>6||new Set(input.members.map(m=>m.ownedId)).size!==input.members.length)return fail();
 for(const m of input.members){if(!m.ownedId||m.playerId!==input.playerId||m.ownedId.startsWith('AI:'))return fail();normalXpGrant(m,0);}
 pacedPool(input.members,input.enemyLevel,input.sourceWinXp*(input.baseline==='per-entered'?input.members.length:1),input.policy);
 return {version:'paced-battle-xp-v1',...structuredClone(input)};
}
export function pacedBattleXpPlan(row:{id:string;playerId:string;ownedId:string},snap:any):PacedBattleXpPlan|null{
 const raw=snap.unitXpPacing;if(raw==null)return null;
 if(raw.version!=='paced-battle-xp-v1'||raw.battleId!==row.id||raw.playerId!==row.playerId||!Array.isArray(snap.members)||!Array.isArray(raw.members)||raw.policy?.catalogVersion!==snap.policy?.catalogVersion)return fail('UNIT_XP_PACING_SNAPSHOT_INVALID');
 const checked=createPacedBattleXpPlan(raw);
 if(checked.members.length!==snap.members.length||checked.members.some(m=>!snap.members.some((r:any)=>r.ownedId===m.ownedId&&r.startingXp===m.startingXp))||!checked.members.some(m=>m.ownedId===row.ownedId))return fail('UNIT_XP_PACING_SNAPSHOT_INVALID');
 return checked;
}
export function calculatePacedBattleXp(plan:PacedBattleXpPlan,row:{ownedId:string},state:State,effectiveStartingXp?:Record<string,string>){
 const allowed=new Set(plan.members.map(m=>m.ownedId)),entered=new Set([row.ownedId,...(state.participation??[]).map(p=>p.ownedId)]);
 for(const turn of state.events as {events?:{actor?:string;kind?:string;to?:string}[]}[])for(const event of turn.events??[])if(event.actor==='self'&&['switch','forced-switch'].includes(event.kind??'')&&event.to)entered.add(event.to);
 if([...entered].some(id=>!allowed.has(id)))return fail('UNIT_XP_PACING_PARTICIPATION_INVALID');
 if(plan.recipientMode==='initial-owned'){entered.clear();entered.add(row.ownedId);}
 const ids=[...entered].sort(),members=ids.map(id=>plan.members.find(m=>m.ownedId===id)!);
 const alive=[state.fighters[0],...(state.reserves??[])].some(f=>f.hp>0),win=state.outcome==='win'&&!state.surrendered&&(!plan.requireSurvivor||alive),loss=state.outcome==='loss'&&!state.surrendered;
 // 마무리 전액 판(연습전 제외): 이긴 순간 나와 있던 유닛 W 전액, 편성된 나머지는 절반. 패배는 0.
 if(plan.policy.distribution===FINISHER_SPLIT_DISTRIBUTION&&plan.recipientMode!=='initial-owned'){
  const finisherId=state.fighters[0].ownedId!,finisher=plan.members.find(m=>m.ownedId===finisherId)??fail('UNIT_XP_PACING_PARTICIPATION_INVALID');
  const shares=win?finisherSplitShares(pacedFinisherWin(finisher,plan.enemyLevel,plan.sourceWinXp,plan.policy),plan.members.map(m=>m.ownedId),finisherId):new Map<string,number>();
  const result={policyVersion:plan.policy.version,requestedXp:0,xpAccepted:0,xpDiscarded:0,xpDeferred:0 as const,xpByOwnedId:{} as Record<string,number>,requestedXpByOwnedId:{} as Record<string,number>,units:[] as {ownedId:string;startingXp:string;endingXp:string;requestedXp:number;appliedXp:number;discardedXp:number;status:string}[]};
  for(const member of plan.members){const requestedXp=shares.get(member.ownedId)??0,start=effectiveStartingXp?.[member.ownedId]??member.startingXp,grant=normalXpGrant({...member,startingXp:start},requestedXp),discardedXp=grant.requestedXp-grant.appliedXp;result.requestedXp+=requestedXp;result.xpAccepted+=grant.appliedXp;result.xpDiscarded+=discardedXp;result.xpByOwnedId[member.ownedId]=grant.appliedXp;result.requestedXpByOwnedId[member.ownedId]=requestedXp;result.units.push({ownedId:member.ownedId,startingXp:start,endingXp:grant.endingXp,requestedXp,appliedXp:grant.appliedXp,discardedXp,status:grant.status});}
  return result;
 }
 const legacy=plan.sourceWinXp*(plan.baseline==='per-entered'?ids.length:1),winPool=pacedPool(members,plan.enemyLevel,legacy,plan.policy);
 // Preserve each mode's existing loss ratio. Story/adventure loss remains zero.
 const total=win?winPool:loss&&plan.sourceWinXp>0?Number(BigInt(winPool)*BigInt(plan.sourceLossXp)/BigInt(plan.sourceWinXp)):0;
 const each=Math.floor(total/ids.length),remainder=total%ids.length,shares=new Map(ids.map((id,index)=>[id,each+(index<remainder?1:0)]));
 const result={policyVersion:plan.policy.version,requestedXp:total,xpAccepted:0,xpDiscarded:0,xpDeferred:0 as const,xpByOwnedId:{} as Record<string,number>,requestedXpByOwnedId:{} as Record<string,number>,units:[] as {ownedId:string;startingXp:string;endingXp:string;requestedXp:number;appliedXp:number;discardedXp:number;status:string}[]};
 for(const member of plan.members){const requestedXp=shares.get(member.ownedId)??(win&&plan.reserveHalf?Math.floor(plan.sourceWinXp/2):0),start=effectiveStartingXp?.[member.ownedId]??member.startingXp,grant=normalXpGrant({...member,startingXp:start},requestedXp),discardedXp=grant.requestedXp-grant.appliedXp;result.xpAccepted+=grant.appliedXp;result.xpDiscarded+=discardedXp;result.xpByOwnedId[member.ownedId]=grant.appliedXp;result.requestedXpByOwnedId[member.ownedId]=requestedXp;result.units.push({ownedId:member.ownedId,startingXp:start,endingXp:grant.endingXp,requestedXp,appliedXp:grant.appliedXp,discardedXp,status:requestedXp?grant.status:entered.has(member.ownedId)?grant.status:'NOT_ENTERED'});}
 // Ordinary adventure reserves retain their old half source reward, outside the entered pool.
 result.requestedXp=Object.values(result.requestedXpByOwnedId).reduce((a,b)=>a+b,0);return result;
}
