import type {Prisma} from '@prisma/client';
import type {State} from '../roster60-battle.js';
import {RosterError} from '../roster60-onboarding.js';
import type {StoryProgress} from './types.js';

/** Private, server-pinned battle metadata; there is no HTTP route accepting it. */
export type StoryBattleContext={version:'story-battle-v1';contentVersion:string;encounterId:string;speciesId:string;kind:'black'|'villain'|'ordinary';mode:'story'|'adventure';questId?:string;acceptToken?:string;dropAmount:number;maxBalance:number};
const fail=(code:string):never=>{throw new RosterError(409,code);};

/** Runs in the same transaction as the authoritative battle settlement, even after feature disable. */
export async function settleStoryBattle(tx:Prisma.TransactionClient,row:{id:string;playerId:string},state:State,snapshot:any){
 const context=snapshot.story as StoryBattleContext|undefined;if(!context)return null;
 if(context.version!=='story-battle-v1'||context.encounterId!==snapshot.encounter?.id||context.speciesId!==state.fighters[1]?.speciesId||context.mode!==state.mode||!Number.isSafeInteger(context.dropAmount)||context.dropAmount<0||!Number.isSafeInteger(context.maxBalance)||context.maxBalance<0||context.maxBalance>2_000_000_000)return fail('STORY_BATTLE_SNAPSHOT_INVALID');
 const old=await tx.roster60StoryBattleAward.findUnique({where:{playerId_battleId:{playerId:row.playerId,battleId:row.id}}});
 if(old)return {darkGangAwarded:old.amount,questId:old.questId};
 const account=await tx.roster60StoryAccount.findUnique({where:{playerId:row.playerId}});if(!account)return fail('STORY_ACCOUNT_NOT_FOUND');
 const progress=JSON.parse(account.state) as StoryProgress;
 // A human villain or a duel/rank battle cannot become a black-gull drop by its name.
 const amount=state.outcome==='win'&&!state.surrendered&&context.kind==='black'&&['story','adventure'].includes(context.mode)?context.dropAmount:0;
 if(account.darkGangBalance+amount>context.maxBalance||account.darkGangLifetime+amount>context.maxBalance)return fail('STORY_MATERIAL_LIMIT');
 let progressed=false;
 if(context.questId){
  const accepted=progress.accepted[context.questId];
  if(!accepted||accepted.token!==context.acceptToken||!accepted.battles.includes(row.id))return fail('STORY_BATTLE_LINK_INVALID');
  if(state.outcome==='win'&&!state.surrendered){accepted.wins++;progressed=true;}
 }
 const changed=await tx.roster60StoryAccount.updateMany({where:{playerId:row.playerId,revision:account.revision},data:{state:JSON.stringify(progress),revision:{increment:1},darkGangBalance:{increment:amount},darkGangLifetime:{increment:amount}}});
 if(changed.count!==1)return fail('STORY_STALE_REVISION');
 await tx.roster60StoryBattleAward.create({data:{playerId:row.playerId,battleId:row.id,questId:context.questId??null,amount,outcome:state.outcome??'invalid'}});
 return {darkGangAwarded:amount,questId:context.questId??null,objectiveProgressed:progressed,materialsPreservedForFutureEnhancement:true};
}
