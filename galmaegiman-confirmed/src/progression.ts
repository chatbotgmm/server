// 서버 확정 진행도. 클라이언트가 점수·등급·보상·시각을 지정할 수 없다.
import {randomInt} from 'node:crypto';
import type {Prisma, PrismaClient, Player} from '@prisma/client';
import type {Content} from './content.js';
import {topTiers} from './content.js';
import {GameError} from './types.js';
type Tx=Prisma.TransactionClient;
const setting=(name:string,fallback:number,min:number,max:number)=>{const raw=process.env[name];if(raw===undefined)return fallback;const n=Number(raw);if(!Number.isInteger(n)||n<min||n>max)throw new Error(`${name}: ${min}~${max} 정수가 필요합니다.`);return n;};
export const progressionConfig={
  rerollCooldownMs:5*3600000,rerollDurationMs:30*60000,
  reassessmentCooldownMs:setting('PROGRESSION_REASSESS_HOURS',24,1,720)*3600000,
  statBonusPct:setting('PROGRESSION_REBIRTH_BONUS_PCT',5,1,25),
  dailyCap:setting('PROGRESSION_DAILY_CAP',100,1,1000),
  milestones:[{id:'p10',points:10,credits:1},{id:'p30',points:30,credits:3},{id:'p60',points:60,credits:5},{id:'p100',points:100,credits:10}],
};
export const freshProgression=(tx:Tx,playerId:string)=>tx.progressionState.upsert({where:{playerId},create:{playerId},update:{}});
export async function awardPlayPoints(tx:Tx,p:Player,amount:number,now:Date){
  if(p.isAdmin&&p.adminMode)return;
  const s=await freshProgression(tx,p.id),day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
  const before=s.pointDay===day?s.dailyPoints:0,earned=Math.min(Math.max(0,Math.floor(amount)),Math.max(0,progressionConfig.dailyCap-before));
  if(earned)await tx.progressionState.update({where:{playerId:p.id},data:{points:{increment:earned},pointDay:day,dailyPoints:before+earned}});
}
export async function progressionReceipt(tx:Tx,p:Player,requestId:string,action:string,payload:unknown,run:()=>Promise<unknown>){
  if(!/^[A-Za-z0-9:_-]{8,80}$/.test(requestId))throw new GameError('요청 번호를 확인해 주세요.');
  const fingerprint=JSON.stringify({action,payload}),where={playerId_requestId:{playerId:p.id,requestId}};
  const prior=await tx.progressionReceipt.findUnique({where});
  if(prior){if(prior.fingerprint!==fingerprint)throw new GameError('같은 요청 번호를 다른 작업에 사용할 수 없어요.');return JSON.parse(prior.result);}
  if(p.isAdmin&&p.adminMode)throw new GameError('관리자 체험 모드에서는 진행 보상을 받을 수 없어요.');
  const result=await run();await tx.progressionReceipt.create({data:{playerId:p.id,requestId,fingerprint,result:JSON.stringify(result)}});return result;
}
export async function requireProgressionIdle(tx:Tx,playerId:string,now:Date){
  const activity=await tx.realtimeActivity.findFirst({where:{playerId,OR:[{kind:"ROOM"},{expiresAt:{gt:now}}]}});
  if(activity)throw new GameError('진행 중인 전투나 매칭을 마친 뒤 다시 시도해 주세요.');
}
export async function progressionState(tx:Tx,p:Player,content:Content,now:Date){
  const s=await freshProgression(tx,p.id),claims=await tx.progressionMilestone.findMany({where:{playerId:p.id}}),owned=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:{in:['AVAILABLE','EXPEDITION']}},orderBy:{id:'asc'}});
  const busy=!!await tx.realtimeActivity.findFirst({where:{playerId:p.id,OR:[{kind:"ROOM"},{expiresAt:{gt:now}}]}});
  return {serverNow:now.getTime(),pass:{points:s.points,milestones:progressionConfig.milestones.map(m=>({...m,claimed:claims.some(c=>c.milestoneId===m.id),claimable:s.points>=m.points&&!claims.some(c=>c.milestoneId===m.id)}))},
    reroll:{available:s.rerollAvailable&&!!s.rerollExpiresAt&&s.rerollExpiresAt>now,expiresAt:s.rerollExpiresAt?.getTime()??null,nextClaimAt:s.rerollNextClaimAt?.getTime()??null,cooldownMs:progressionConfig.rerollCooldownMs,durationMs:progressionConfig.rerollDurationMs},
    reassessment:{cooldownMs:progressionConfig.reassessmentCooldownMs,nextAvailableAt:s.reassessNextAt?.getTime()??null,candidates:owned.filter(o=>o.status==='AVAILABLE'&&!o.locked&&o.obtainedVia==='GACHA_HIGH'&&['RARE','LEGEND'].includes(content.map.get(o.characterId)?.rarity??'')&&!busy).map(o=>({key:`o${o.id}`,id:o.characterId,name:content.map.get(o.characterId)!.name,rarity:content.map.get(o.characterId)!.rarity}))},
    reincarnation:{statBonusPct:progressionConfig.statBonusPct,maxLevel:null,totalStatBonusCapPct:25,candidates:owned.filter(o=>topTiers.has(content.map.get(o.characterId)?.rarity??'')).map(o=>({key:`o${o.id}`,id:o.characterId,name:content.map.get(o.characterId)!.name,rarity:content.map.get(o.characterId)!.rarity,locked:o.locked,reincarnation:o.reincarnation,eligible:!busy&&!o.locked&&o.status==='AVAILABLE',reason:busy?'전투 또는 매칭 중':o.locked?'보호 중':o.status!=='AVAILABLE'?'탐험 중':null}))}};
}
export async function claimMilestone(tx:Tx,p:Player,id:string){
  const m=progressionConfig.milestones.find(m=>m.id===id);if(!m)throw new GameError('패스 보상을 다시 골라 주세요.');
  const s=await freshProgression(tx,p.id);if(s.points<m.points)throw new GameError('플레이 포인트가 부족해요.');
  if(await tx.progressionMilestone.findUnique({where:{playerId_milestoneId:{playerId:p.id,milestoneId:id}}}))throw new GameError('이미 받은 패스 보상이에요.');
  await tx.progressionMilestone.create({data:{playerId:p.id,milestoneId:id}});
  await tx.player.update({where:{id:p.id},data:{credits:{increment:m.credits}}});
  await tx.auditEvent.create({data:{playerId:p.id,kind:'PLAY_PASS',detail:{milestoneId:id,credits:m.credits}}});
  return {ok:true,milestoneId:id,credits:m.credits};
}
export async function claimReroll(tx:Tx,p:Player,now:Date){
  const s=await freshProgression(tx,p.id);if(s.rerollNextClaimAt&&s.rerollNextClaimAt>now)throw new GameError('추가 다시뽑기는 5시간마다 받을 수 있어요.');
  const expiresAt=new Date(+now+progressionConfig.rerollDurationMs),nextClaimAt=new Date(+now+progressionConfig.rerollCooldownMs);
  await tx.progressionState.update({where:{playerId:p.id},data:{rerollAvailable:true,rerollExpiresAt:expiresAt,rerollNextClaimAt:nextClaimAt}});
  return {ok:true,available:true,expiresAt:+expiresAt,nextClaimAt:+nextClaimAt};
}
export async function consumeReroll(tx:Tx,p:Player,now:Date){
  const changed=await tx.progressionState.updateMany({where:{playerId:p.id,rerollAvailable:true,rerollExpiresAt:{gt:now}},data:{rerollAvailable:false}});
  if(changed.count!==1)throw new GameError('사용할 수 있는 추가 다시뽑기가 없어요.');
}
export async function transformOwned(tx:Tx,p:Player,content:Content,key:string,kind:'reincarnate'|'reassess',now:Date){
  await requireProgressionIdle(tx,p.id,now);
  const source=await tx.ownedCharacter.findFirst({where:{id:Number(key.slice(1)),playerId:p.id,status:'AVAILABLE',locked:false}});
  if(!source)throw new GameError('보호·탐험 중이 아닌 내 개체를 골라 주세요.');
  const c=content.map.get(source.characterId)!;let pool=content.characters.filter(x=>x.rarity==='COMMON');
  if(kind==='reassess'){
    if(source.obtainedVia!=='GACHA_HIGH'||!['RARE','LEGEND'].includes(c.rarity))throw new GameError('고급 모집에서 얻은 희귀·전설만 재심사할 수 있어요.');
    const s=await freshProgression(tx,p.id);if(s.reassessNextAt&&s.reassessNextAt>now)throw new GameError('재심사 대기 시간이 남았어요.');
    pool=content.characters.filter(x=>x.rarity===c.rarity&&x.id!==c.id);
  }else if(!topTiers.has(c.rarity))throw new GameError('최상위 개체 한 마리만 환생할 수 있어요.');
  if(!pool.length)throw new GameError('변경 가능한 갈매미가 아직 없어요.');
  const target=pool[randomInt(pool.length)],changed=await tx.ownedCharacter.updateMany({where:{id:source.id,playerId:p.id,status:'AVAILABLE',locked:false},data:{status:'CONSUMED',consumedAt:now}});
  if(changed.count!==1)throw new GameError('개체 상태가 바뀌었어요. 다시 확인해 주세요.');
  const rebirth=kind==='reincarnate';
  const owned=await tx.ownedCharacter.create({data:{playerId:p.id,characterId:target.id,obtainedVia:rebirth?'REINCARNATION':'GACHA_HIGH',obtainedAt:now,reincarnation:rebirth?source.reincarnation+1:0,inheritedFrom:rebirth?(source.inheritedFrom??c.id):null,statBonusPct:rebirth?Math.min(25,(source.reincarnation+1)*progressionConfig.statBonusPct):0}});
  await tx.collectionEntry.upsert({where:{playerId_characterId:{playerId:p.id,characterId:target.id}},create:{playerId:p.id,characterId:target.id,count:1},update:{count:{increment:1}}});
  const save=await tx.storySave.findUnique({where:{playerId:p.id}});
  if(save){const d=JSON.parse(save.data);delete d.units?.[key];delete d.hp?.[key];if(Array.isArray(d.team))d.team=d.team.filter((k:string)=>k!==key);await tx.storySave.update({where:{playerId:p.id},data:{data:JSON.stringify(d)}});}
  const nextAvailableAt=rebirth?null:+now+progressionConfig.reassessmentCooldownMs;
  if(nextAvailableAt)await tx.progressionState.update({where:{playerId:p.id},data:{reassessNextAt:new Date(nextAvailableAt)}});
  await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
  await tx.auditEvent.create({data:{playerId:p.id,kind:rebirth?'REINCARNATION':'REASSESSMENT',detail:{sourceOwnedId:source.id,ownedId:owned.id,from:c.id,to:target.id}}});
  return {ok:true,key:`o${owned.id}`,id:target.id,name:target.name,rarity:target.rarity,reincarnation:owned.reincarnation,inheritedFrom:owned.inheritedFrom,statBonusPct:owned.statBonusPct,nextAvailableAt};
}

// 기동 시 읽기만 검사합니다. 누락된 운영 스키마를 자동 생성하지 않습니다.
export async function assertProgressionSchema(db:PrismaClient){
  try{await Promise.all([
    db.ownedCharacter.findFirst({select:{reincarnation:true,inheritedFrom:true,statBonusPct:true}}),
    db.progressionState.count(),db.progressionReceipt.count(),db.progressionMilestone.count(),
  ]);}catch{throw new Error('진행 기능 DB 스키마가 아직 적용되지 않았습니다. 서버를 시작하지 않습니다. 백업 후 prisma/manual/20261019_progression_additive.sql을 검토·승인하여 적용하고 Prisma client를 재생성하세요.');}
}
