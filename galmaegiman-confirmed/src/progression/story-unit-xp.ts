import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {RosterError} from '../roster60-onboarding.js';
import type {State} from '../roster60-battle.js';
import type {GrowthPoint,ReincarnationXpPolicy} from '../battle/roster60-rules.js';
import {growthAt} from '../battle/roster60-rules.js';
import type {AdventureRewards} from '../roster60-adventure-rewards.js';
import {normalXpGrant} from './normal-xp.js';
import {FINISHER_SPLIT_DISTRIBUTION,finisherSplitShares} from './finisher-split.js';

export type StoryUnitXpPolicy={approved:true;version:string;profileKind:'user-authorized-test-baseline';productionBalanceApproved:false;testValues:true;catalogVersion:string;contentVersion:string;scope:'accepted-quest-battles-only';distribution:'equal-actually-entered'|'finisher-full-rest-half';loss:'zero-existing-story';surrender:'zero';draw:'zero';capOverflow:'discard-overflow';questCompletionUnitXp:'none';rewards:Record<string,{winXp:number;lossXp:0}>;notes?:string[]};
export type StoryUnitXpMember={ownedId:string;playerId:string;startingXp:string;growth:GrowthPoint[];reincarnation:number;reincarnationXpPolicy?:ReincarnationXpPolicy};
export type StoryUnitXpPlan={version:'story-unit-xp-plan-v1';battleId:string;playerId:string;questId:string;acceptToken:string;encounterId:string;policy:StoryUnitXpPolicy;members:StoryUnitXpMember[]};
export type StoryUnitXpResult={policyVersion:string;questId:string;encounterId:string;requestedXp:number;xpAccepted:number;xpDiscarded:number;xpDeferred:0;xpByOwnedId:Record<string,number>;requestedXpByOwnedId:Record<string,number>;units:{ownedId:string;startingXp:string;endingXp:string;requestedXp:number;appliedXp:number;discardedXp:number;status:string}[]};
export type StoryUnitXpEscrow={version:'story-unit-xp-escrow-v1';questId:string;acceptToken:string;policyVersion:string;xp:Record<string,number>};
const fail=(code:string,status=409):never=>{throw new RosterError(status,code);};
const record=(v:unknown):v is Record<string,any>=>!!v&&typeof v==='object'&&!Array.isArray(v);
const text=(v:unknown):v is string=>typeof v==='string'&&v.length>0&&v.length<=200;
const amount=(v:unknown):v is number=>typeof v==='number'&&Number.isSafeInteger(v)&&v>=0&&v<=2_000_000_000;
const idOrder=(a:string,b:string)=>a<b?-1:a>b?1:0;
const required=['approved','version','profileKind','productionBalanceApproved','testValues','catalogVersion','contentVersion','scope','distribution','loss','surrender','draw','capOverflow','questCompletionUnitXp','rewards'];

export function validateStoryUnitXpPolicy(raw:unknown,catalogVersion:string,contentVersion:string,allowedEncounterIds?:string[]):StoryUnitXpPolicy{
 if(!record(raw)||required.some(k=>!Object.prototype.hasOwnProperty.call(raw,k))||Object.keys(raw).some(k=>![...required,'notes'].includes(k))||raw.approved!==true||!text(raw.version)||raw.profileKind!=='user-authorized-test-baseline'||raw.productionBalanceApproved!==false||raw.testValues!==true||!text(raw.catalogVersion)||raw.catalogVersion!==catalogVersion||!text(raw.contentVersion)||raw.contentVersion!==contentVersion||raw.scope!=='accepted-quest-battles-only'||!['equal-actually-entered',FINISHER_SPLIT_DISTRIBUTION].includes(raw.distribution)||raw.loss!=='zero-existing-story'||raw.surrender!=='zero'||raw.draw!=='zero'||raw.capOverflow!=='discard-overflow'||raw.questCompletionUnitXp!=='none'||!record(raw.rewards))return fail('STORY_UNIT_XP_POLICY_INVALID',503);
 const ids=Object.keys(raw.rewards);if(!ids.length||ids.some(id=>!text(id)||!/^[A-Za-z0-9_-]+$/.test(id)||['__proto__','constructor','prototype'].includes(id)))return fail('STORY_UNIT_XP_POLICY_INVALID',503);
 for(const r of Object.values(raw.rewards))if(!record(r)||Object.keys(r).sort().join()!=='lossXp,winXp'||!amount(r.winXp)||r.lossXp!==0)return fail('STORY_UNIT_XP_POLICY_INVALID',503);
 if(raw.notes!==undefined&&(!Array.isArray(raw.notes)||raw.notes.length>50||raw.notes.some(n=>typeof n!=='string'||!n||n.length>2000)))return fail('STORY_UNIT_XP_POLICY_INVALID',503);
 if(allowedEncounterIds){const allowed=[...new Set(allowedEncounterIds)];if(allowed.length!==ids.length||ids.some(id=>!allowed.includes(id)))return fail('STORY_UNIT_XP_POLICY_INVALID',503);}
 return raw as StoryUnitXpPolicy;
}

/** No production fallback, network fetch, current-player reward or implicit default amount. */
export function storyUnitXpPolicyReader(root:string,profile?:string):()=>StoryUnitXpPolicy|null{
 if(profile!=='five-stat-device-test-v1')return ()=>null;
 const path=resolve(root,'data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json');
 return ()=>{if(!existsSync(path))return null;let raw:unknown;try{raw=JSON.parse(readFileSync(path,'utf8'));}catch{return fail('STORY_UNIT_XP_POLICY_INVALID',503);}if(!record(raw))return fail('STORY_UNIT_XP_POLICY_INVALID',503);return validateStoryUnitXpPolicy(raw,raw.catalogVersion,raw.contentVersion);};
}

function checkedMembers(members:StoryUnitXpMember[],playerId:string){
 if(!Array.isArray(members)||!members.length||members.length>6||new Set(members.map(m=>m.ownedId)).size!==members.length)return fail('STORY_UNIT_XP_MEMBERS_INVALID');
 for(const m of members){if(!text(m.ownedId)||m.ownedId.startsWith('AI:')||m.playerId!==playerId||!/^\d+$/.test(m.startingXp)||!Array.isArray(m.growth))return fail('STORY_UNIT_XP_MEMBERS_INVALID');normalXpGrant(m,0);}
}
const rewardIdentity=(p:StoryUnitXpPolicy)=>JSON.stringify([p.version,p.catalogVersion,p.contentVersion,Object.keys(p.rewards).sort().map(id=>[id,p.rewards[id].winXp,p.rewards[id].lossXp])]);

export function createStoryUnitXpPlan(playerId:string,battleId:string,story:unknown,encounter:{id:string;mode?:string;winXp:number;lossXp:number},catalogVersion:string,members:StoryUnitXpMember[]):StoryUnitXpPlan|null{
 if(!record(story)||story.unitXpPolicy===undefined||story.unitXpPolicy===null)return null;
 if(story.version!=='story-battle-v1'||!text(story.questId)||!text(story.acceptToken)||!text(story.contentVersion)||story.encounterId!==encounter.id||story.mode!==encounter.mode||!['story','adventure'].includes(story.mode)||encounter.winXp!==0||encounter.lossXp!==0)return fail('STORY_UNIT_XP_CONTEXT_INVALID');
 const policy=validateStoryUnitXpPolicy(story.unitXpPolicy,catalogVersion,story.contentVersion);if(!Object.prototype.hasOwnProperty.call(policy.rewards,encounter.id))return fail('STORY_UNIT_XP_REWARD_MISSING',503);
 checkedMembers(members,playerId);
 return {version:'story-unit-xp-plan-v1',battleId,playerId,questId:story.questId,acceptToken:story.acceptToken,encounterId:encounter.id,policy:structuredClone(policy),members:structuredClone(members)};
}

/** Missing snapshots remain on their original contract. A new profile never backfills old battles. */
export function storyUnitXpPlan(row:{id:string;playerId:string;ownedId:string},snap:any):StoryUnitXpPlan|null{
 const raw=snap.storyUnitXp;if(raw===undefined||raw===null)return null;
 if(!record(raw)||raw.version!=='story-unit-xp-plan-v1'||raw.battleId!==row.id||raw.playerId!==row.playerId||!Array.isArray(raw.members)||!Array.isArray(snap.members)||!record(snap.policy)||!record(snap.encounter))return fail('STORY_UNIT_XP_SNAPSHOT_INVALID');
 const expected=createStoryUnitXpPlan(row.playerId,row.id,snap.story,snap.encounter,snap.policy.catalogVersion,raw.members as StoryUnitXpMember[]);
 if(!expected||raw.questId!==expected.questId||raw.acceptToken!==expected.acceptToken||raw.encounterId!==expected.encounterId||!record(raw.policy))return fail('STORY_UNIT_XP_SNAPSHOT_INVALID');
 const pinned=validateStoryUnitXpPolicy(raw.policy,expected.policy.catalogVersion,expected.policy.contentVersion);
 if(rewardIdentity(pinned)!==rewardIdentity(expected.policy)||raw.members.length!==snap.members.length||raw.members.some((m:any)=>!snap.members.some((ref:any)=>ref.ownedId===m.ownedId&&ref.startingXp===m.startingXp))||!raw.members.some((m:any)=>m.ownedId===row.ownedId))return fail('STORY_UNIT_XP_SNAPSHOT_INVALID');
 return raw as StoryUnitXpPlan;
}

export function storyUnitXpEnteredIds(row:{ownedId:string},state:State,members:readonly StoryUnitXpMember[]):string[]{
 const ids:unknown[]=[row.ownedId,...(state.participation??[]).map(p=>p.ownedId)];
 for(const turn of state.events as {events?:{actor?:string;kind?:string;to?:string}[]}[])for(const event of turn.events??[])if(event.actor==='self'&&['switch','forced-switch'].includes(event.kind??'')&&event.to)ids.push(event.to);
 const owned=new Set(members.map(m=>m.ownedId));if(ids.some(id=>typeof id!=='string'||!owned.has(id)))return fail('STORY_UNIT_XP_PARTICIPATION_INVALID');
 return [...new Set(ids as string[])].sort(idOrder);
}

/** Integer totals are a test proposal. Only executed server participation selects the recipients. */
export function calculateStoryUnitXp(plan:StoryUnitXpPlan,row:{ownedId:string},state:State,effectiveStartingXp?:Record<string,string>):StoryUnitXpResult{
 const entered=storyUnitXpEnteredIds(row,state,plan.members),alive=[state.fighters[0],...(state.reserves??[])].some(f=>f.hp>0);
 const won=state.outcome==='win'&&!state.surrendered&&alive,winXp=plan.policy.rewards[plan.encounterId].winXp;
 // 고정된 정책 판을 따른다: 새 판은 마무리 전액·편성 나머지 절반, 예전 판은 출전 개체 균등 분배.
 const finisherSplit=plan.policy.distribution===FINISHER_SPLIT_DISTRIBUTION;
 const total=won?winXp:0,each=Math.floor(total/entered.length),remainder=total%entered.length;
 const shares=finisherSplit?(won?finisherSplitShares(winXp,plan.members.map(m=>m.ownedId),state.fighters[0].ownedId!):new Map<string,number>()):new Map(entered.map((id,index)=>[id,each+(index<remainder?1:0)])),result:StoryUnitXpResult={policyVersion:plan.policy.version,questId:plan.questId,encounterId:plan.encounterId,requestedXp:[...shares.values()].reduce((sum,value)=>sum+value,0),xpAccepted:0,xpDiscarded:0,xpDeferred:0,xpByOwnedId:{},requestedXpByOwnedId:{},units:[]};
 for(const member of plan.members){const requestedXp=shares.get(member.ownedId)??0,start=effectiveStartingXp?.[member.ownedId]??member.startingXp,grant=normalXpGrant({...member,startingXp:start},requestedXp),discardedXp=grant.requestedXp-grant.appliedXp;
  result.xpAccepted+=grant.appliedXp;result.xpDiscarded+=discardedXp;result.xpByOwnedId[member.ownedId]=grant.appliedXp;result.requestedXpByOwnedId[member.ownedId]=requestedXp;result.units.push({ownedId:member.ownedId,startingXp:start,endingXp:grant.endingXp,requestedXp,appliedXp:grant.appliedXp,discardedXp,status:finisherSplit||entered.includes(member.ownedId)?grant.status:'NOT_ENTERED'});
 }
 return result;
}

export function storyUnitXpEscrow(saved:any,members:readonly {ownedId:string}[]):StoryUnitXpEscrow|null{
 const raw=saved.storyUnitXpEscrow;if(raw===undefined||raw===null)return null;
 if(!record(raw)||raw.version!=='story-unit-xp-escrow-v1'||!text(raw.questId)||raw.questId!==saved.storyQuestId||!text(raw.acceptToken)||!text(raw.policyVersion)||!record(raw.xp))return fail('STORY_UNIT_XP_ESCROW_INVALID');
 const allowed=new Set(members.map(m=>m.ownedId));for(const [id,value] of Object.entries(raw.xp))if(!allowed.has(id)||!amount(value))return fail('STORY_UNIT_XP_ESCROW_INVALID');
 return raw as StoryUnitXpEscrow;
}

export function addStoryUnitXpEscrow(saved:any,pending:AdventureRewards,plan:StoryUnitXpPlan,result:StoryUnitXpResult){
 if(saved.storyQuestId!==plan.questId)return fail('STORY_UNIT_XP_CONTEXT_INVALID');
 const escrow=storyUnitXpEscrow(saved,plan.members)??{version:'story-unit-xp-escrow-v1' as const,questId:plan.questId,acceptToken:plan.acceptToken,policyVersion:plan.policy.version,xp:{}};
 if(escrow.acceptToken!==plan.acceptToken||escrow.policyVersion!==plan.policy.version)return fail('STORY_UNIT_XP_CONTEXT_INVALID');
 for(const [id,value] of Object.entries(result.xpByOwnedId)){const next=(pending.xp[id]??0)+value,storyNext=(escrow.xp[id]??0)+value;if(!amount(next)||!amount(storyNext))return fail('INVALID_REWARD');pending.xp[id]=next;escrow.xp[id]=storyNext;}
 saved.storyUnitXpEscrow=escrow;
}

/** Clamp only the marked story portion. Old/general adventure escrow keeps its original contract. */
export function storyUnitXpReturn(saved:any,rewards:AdventureRewards):{rewards:AdventureRewards;summary:{delivery:'returned';policyVersion:string;questId:string;xpAwarded:number;xpDiscarded:number;xpDeferred:0;xpByOwnedId:Record<string,number>}}|null{
 const escrow=storyUnitXpEscrow(saved,saved.members);if(!escrow)return null;
 const next=structuredClone(rewards),summary={delivery:'returned' as const,policyVersion:escrow.policyVersion,questId:escrow.questId,xpAwarded:0,xpDiscarded:0,xpDeferred:0 as const,xpByOwnedId:{} as Record<string,number>};
 for(const member of saved.members as StoryUnitXpMember[]){const all=next.xp[member.ownedId]??0,story=escrow.xp[member.ownedId]??0;if(story>all)return fail('STORY_UNIT_XP_ESCROW_INVALID');const ordinary=all-story,start=BigInt(member.startingXp)+BigInt(ordinary);if(start>BigInt(Number.MAX_SAFE_INTEGER))return fail('INVALID_XP');growthAt(Number(start),member.growth,member.reincarnation,member.reincarnationXpPolicy);
  const grant=normalXpGrant({...member,startingXp:start.toString()},story);next.xp[member.ownedId]=ordinary+grant.appliedXp;summary.xpAwarded+=grant.appliedXp;summary.xpDiscarded+=story-grant.appliedXp;summary.xpByOwnedId[member.ownedId]=grant.appliedXp;
 }
 return {rewards:next,summary};
}
