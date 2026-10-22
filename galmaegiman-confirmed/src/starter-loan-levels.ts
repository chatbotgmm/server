import type {Prisma} from '@prisma/client';
import {GameError} from './types.js';
type Tx=Prisma.TransactionClient;
export type LoanLevelCheckpoint={ownedId:number;level:number};

// Level only. No XP/skill/promotion/evolution/currency copying; an increased floor starts with 0 XP.
export function applyLoanLevel<T extends {level:number;xp:number}>(record:T,floor:number,maxLevel:number):T{
  const level=Math.max(record.level,Math.min(maxLevel,Math.max(1,floor)));
  return level>record.level?{...record,level,xp:0}:record;
}
export async function loanLevelFloors(tx:Pick<Tx,'starterLoanLevel'>,playerId:string,ids:number[]){
  const rows=await tx.starterLoanLevel.findMany({where:{playerId,OR:[{loanOwnedId:{in:ids}},{recipientOwnedId:{in:ids}}]}});
  const floors=new Map<number,number>();
  for(const row of rows){floors.set(row.loanOwnedId,Math.max(floors.get(row.loanOwnedId)??0,row.highestLevel));if(row.recipientOwnedId!==null)floors.set(row.recipientOwnedId,Math.max(floors.get(row.recipientOwnedId)??0,row.highestLevel));}
  return floors;
}
// Called only within the authenticated native battle settlement receipt transaction, before expiry cleanup.
export async function checkpointLoanLevels(tx:Tx,playerId:string,session:string,checkpoints:LoanLevelCheckpoint[],maxLevel:number,now:Date){
  for(const point of checkpoints){
    if(!Number.isSafeInteger(point.ownedId)||!Number.isInteger(point.level)||point.level<1||point.level>maxLevel)throw new GameError('전투 레벨 기록이 맞지 않아요.');
    const row=await tx.starterLoanLevel.findUnique({where:{loanOwnedId:point.ownedId}});
    if(!row||row.playerId!==playerId)throw new GameError('임시 갈매미의 레벨 기록을 확인해 주세요.');
    // Row provenance is written only by the grant transaction, never from StorySave.
    const source=await tx.ownedCharacter.findFirst({where:{id:row.loanOwnedId,playerId,characterId:row.characterId,obtainedVia:'TEMPORARY_STARTER',loanExpiresAt:{not:null}}});
    if(!source)throw new GameError('임시 갈매미의 지급 기록을 확인해 주세요.');
    if(point.level<=row.highestLevel)continue;
    const changed=await tx.starterLoanLevel.updateMany({where:{loanOwnedId:row.loanOwnedId,playerId,highestLevel:{lt:point.level}},data:{highestLevel:point.level,lastSession:session,updatedAt:now}});
    if(changed.count)await tx.auditEvent.create({data:{playerId,kind:'STARTER_LOAN_LEVEL_CHECKPOINT',detail:{grantId:row.grantId,loanOwnedId:row.loanOwnedId,level:point.level,session}}});
  }
}
// First subsequent same-species permanent copy only. The binding is never reassigned after consumption.
export async function bindLoanLevelRecipient(tx:Tx,playerId:string,owned:{id:number;characterId:string;obtainedAt:Date;loanExpiresAt:Date|null},now:Date){
  if(owned.loanExpiresAt!==null)return;
  const rows=await tx.starterLoanLevel.findMany({where:{playerId,characterId:owned.characterId,recipientOwnedId:null,createdAt:{lte:owned.obtainedAt}},orderBy:{loanOwnedId:'asc'}});
  for(const row of rows){
    const changed=await tx.starterLoanLevel.updateMany({where:{loanOwnedId:row.loanOwnedId,playerId,recipientOwnedId:null},data:{recipientOwnedId:owned.id,boundAt:now}});
    if(changed.count)await tx.auditEvent.create({data:{playerId,kind:'STARTER_LOAN_LEVEL_BIND',detail:{grantId:row.grantId,loanOwnedId:row.loanOwnedId,recipientOwnedId:owned.id,characterId:owned.characterId,level:row.highestLevel}}});
  }
}
