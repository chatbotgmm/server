import type { Prisma, Player } from '@prisma/client';
import type { Content } from './content.js';
import type { Rules } from './battle/core.js';
import { GameError } from './types.js';

export const STARTER_LOAN_DURATION_MS=15*24*60*60*1000;
export const STARTER_LOAN_OPTIONS=[['STONE','T1'],['GOLD','D2'],['FIRE','T2'],['ICE','D5'],['BLOSSOM','T5'],['SEA','D4'],['SHADOW','D3'],['STAR','D6'],['THREAD','D8'],['GRAVITY','T6']] as const;
// null은 영구 개체. 경계 시각부터 신규 활동 불가. 종 ID가 아니라 개체 필드로 판정한다.
export const activeLoanWhere=(now:Date):Prisma.OwnedCharacterWhereInput=>({OR:[{loanExpiresAt:null},{loanExpiresAt:{gt:now}}]});
export function starterSelection(value:unknown):string[]{
  if(!Array.isArray(value)||value.length!==2||value.some(id=>typeof id!=='string'||!STARTER_LOAN_OPTIONS.some(([,candidate])=>candidate===id))||new Set(value).size!==2)throw new GameError('서로 다른 속성의 임시 갈매미 2마리를 골라 주세요.');
  return [...value].sort();
}
function options(content:Content,rules:Rules){return STARTER_LOAN_OPTIONS.map(([attrCode,id])=>{
  const c=content.map.get(id),d=rules.unitDef(id);
  if(!c||!d||d.attr!==attrCode||!['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].includes(c.rarity))throw new Error(`임시 시작 선택지 데이터 불일치: ${id}`);
  return {id,name:c.name,attrCode,attr:rules.attrName(d.attr),role:d.role,tier:c.rarity,image:`/duel/sprites/${id}_s0_front.webp`};
});}
export async function starterLoanState(tx:Prisma.TransactionClient,p:Player,content:Content,rules:Rules,now:Date){
  const grant=await tx.starterLoanGrant.findUnique({where:{playerId:p.id}});
  const receipt=grant?{id:grant.id,selectedIds:grant.selectedIds as string[],ownedIds:grant.ownedIds as number[],grantedAt:grant.grantedAt.getTime(),expiresAt:grant.expiresAt.getTime()}:null;
  const levelArchives=(await tx.starterLoanLevel.findMany({where:{playerId:p.id},orderBy:{loanOwnedId:'asc'}})).map(r=>({sourceOwnedId:r.loanOwnedId,characterId:r.characterId,level:r.highestLevel,recipientOwnedId:r.recipientOwnedId}));
  return {levelArchives,state:receipt?(receipt.expiresAt<=now.getTime()?'expired':'claimed'):p.starterLoanEligibleAt?'pending':'ineligible',serverNow:now.getTime(),chooseCount:2,durationMs:STARTER_LOAN_DURATION_MS,options:options(content,rules),receipt};
}
// 호출자는 인증된 player를 읽은 write transaction을 제공한다. player 고유키가 프로세스 간 중복 지급도 막는다.
export async function claimStarterLoans(tx:Prisma.TransactionClient,p:Player,content:Content,rules:Rules,input:unknown,now:Date){
  const selected=starterSelection(input);
  const previous=await tx.starterLoanGrant.findUnique({where:{playerId:p.id}});
  if(previous){
    if(JSON.stringify(previous.selectedIds)!==JSON.stringify(selected))throw new GameError('이미 다른 임시 갈매미를 선택했어요. 진행을 다시 불러와 주세요.');
    return starterLoanState(tx,p,content,rules,now);
  }
  if(!p.starterLoanEligibleAt||p.gameTutorial!==9||p.adminMode)throw new GameError('튜토리얼을 마친 뒤 임시 갈매미를 선택할 수 있어요.');
  options(content,rules); // 실제 데이터의 속성/등급을 서버에서 검증한다.
  const expiresAt=new Date(now.getTime()+STARTER_LOAN_DURATION_MS),ownedIds:number[]=[];
  for(const characterId of selected){
    // 일반 grant를 거치지 않음: 도감/최초 획득/중복 보상/재화/업적 없음.
    const owned=await tx.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'TEMPORARY_STARTER',obtainedAt:now,loanExpiresAt:expiresAt,locked:true}});
    ownedIds.push(owned.id);
  }
  const receipt=await tx.starterLoanGrant.create({data:{playerId:p.id,selectedIds:selected,ownedIds,grantedAt:now,expiresAt}});
  for(let i=0;i<ownedIds.length;i++)await tx.starterLoanLevel.create({data:{loanOwnedId:ownedIds[i],playerId:p.id,characterId:selected[i],grantId:receipt.id,highestLevel:1,createdAt:now,updatedAt:now}});
  await tx.auditEvent.create({data:{playerId:p.id,kind:'TEMPORARY_STARTER',detail:{receiptId:receipt.id,selectedIds:selected,ownedIds,grantedAt:now.toISOString(),expiresAt:expiresAt.toISOString()}}});
  return starterLoanState(tx,p,content,rules,now);
}

// 만료 개체는 감사/이미 시작된 전투의 정산을 위해 보존. 저장된 편성 참조만 개체 단위로 제거한다.
// 활성 전투는 시작 때 캡처한 roster를 사용하므로 이 정리는 전투 중 유닛 제거가 아니다.
export async function pruneExpiredLoanReferences(tx:Prisma.TransactionClient,p:Player,now:Date){
  const expired=await tx.ownedCharacter.findMany({where:{playerId:p.id,loanExpiresAt:{lte:now}},select:{id:true,characterId:true}});
  if(!expired.length)return;
  const keys=new Set(expired.map(x=>`o${x.id}`));
  const saved=await tx.storySave.findUnique({where:{playerId:p.id}});
  if(saved){
    let data:any;try{data=JSON.parse(saved.data);}catch{return;}
    if(data&&typeof data==='object'&&!Array.isArray(data)){
      let changed=false;
      if(Array.isArray(data.team)){const next=data.team.filter((k:unknown)=>!keys.has(String(k)));changed=next.length!==data.team.length;data.team=next;}
      for(const field of ['units','hp'])if(data[field]&&typeof data[field]==='object')for(const key of keys)if(key in data[field]){delete data[field][key];changed=true;}
      if(changed)await tx.storySave.update({where:{playerId:p.id},data:{data:JSON.stringify(data)}});
    }
  }
  // 레거시 종별 편성은 같은 종의 영구/유효 개체가 하나라도 있으면 유지한다.
  const species=[...new Set(expired.map(x=>x.characterId))];
  const stillOwned=new Set((await tx.ownedCharacter.findMany({where:{playerId:p.id,characterId:{in:species},status:{in:['AVAILABLE','EXPEDITION']},AND:activeLoanWhere(now)},select:{characterId:true}})).map(x=>x.characterId));
  const removed=new Set(species.filter(id=>!stillOwned.has(id)));
  const team=p.battleTeam.split(',').filter(id=>!removed.has(id)).join(',');
  if(team!==p.battleTeam){await tx.player.update({where:{id:p.id},data:{battleTeam:team}});p.battleTeam=team;}
  const lineup=await tx.realtimeLineup.findUnique({where:{playerId:p.id}});
  if(lineup){let ids:unknown;try{ids=JSON.parse(lineup.teamJson);}catch{return;}
    if(Array.isArray(ids)){const next=ids.filter(id=>!keys.has(String(id))&&!removed.has(String(id)));if(next.length!==ids.length)await tx.realtimeLineup.update({where:{playerId:p.id},data:{teamJson:JSON.stringify(next)}});}}
}
