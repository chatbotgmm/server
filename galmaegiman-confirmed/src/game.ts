import { hiddenMaterialHint, mixMaterialLimit } from './craft-hints.js';
import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { Prisma, type PrismaClient, type Player } from '@prisma/client';
import type { Character, Content, Economy, Recipe, Tier } from './content.js';
import { rarityNames, tierOrder, topTiers, tiers } from './content.js';
import { GameError, choice, shareChoice, type GameReply, type ButtonAction, type ListEntry, type Choice, type CardEntry } from './types.js';
import { GachaEngine, type GachaKind } from './gacha.js';
import { characterInfo, matchCharacters, listReply, materialCounts, displayName, isRevealed, mask, duration, HIDDEN_NAME } from './presentation.js';
import { acquisitionText, acquisitionCard, achievementText, emblems, prelude, rewardGuide, speech, story, type AcquisitionKind, type Receipt } from './acquisition.js';
import { cardsPerPage, cardCost } from './kakao.js';
import { DateTime } from 'luxon';
import type { Rules } from './battle/core.js';
import type { UnitRecord } from './battle/growth.js';
import { createBattle } from './battle/engine.js';
import { replay, type PvpEvent } from './battle/replay.js';
import { planExpedition, rollExpedition, expectedSnacks, type ExpeditionPlan, type Roll } from './expedition.js';

import {awardPlayPoints,progressionState,progressionReceipt,claimMilestone,claimReroll,consumeReroll,transformOwned,requireProgressionIdle} from './progression.js';

type Count={total:number;usable:number;locked:number;away:number};
type Stock=Map<string,Count>;
type Tx=Prisma.TransactionClient;
type ActionPayload={characterId?:string;ids?:number[];recipeId?:string;times?:number;badge?:string};
const json=(x:unknown)=>x as Prisma.InputJsonValue;
const gachaWords:Record<string,GachaKind>={하급:'LOW',중급:'MID',고급:'HIGH'};
const menu=[choice('뽑기'),choice('뽑기권 받기','뽑기권받기'),choice('조합가능'),choice('내갈매미'),choice('미션'),choice('상점'),choice('조합목록'),choice('탐험'),choice('도감'),choice('내정보')];
// 관리자로 등록되어 있고 관리자 모드를 켰을 때만 뽑기권 무제한
const unlimited=(p:{isAdmin:boolean;adminMode:boolean})=>p.isAdmin&&p.adminMode;
const adminOnText='🛠 관리자 모드 켜짐!\n뽑기에 뽑기권을 쓰지 않아요(무제한).\n⚠ 모드를 끄면 그동안 뽑은 유닛·조합·탐험·재화가 모두 켜기 전으로 돌아가요.';
// 도움말: 숫자는 economy.yaml에서 읽음. 예시는 실제 조합식이 아닌 자리표시(히든 정답이 새지 않게)
const helpExamples='하급뽑기 5 · 조합 금갑 · 도감 야경\n조합 재료이름, 재료이름 ×2 (재료로 직접)\n탐험보내기 갈발 ×3, 갈내복';
const helpText=(e:Economy)=>[
  '🏝 갈매미맨 — 갈매미를 모아 조합하는 게임',
  '',
  `🎟 뽑기권 받기 · ${e.tickets.cooldownHours}시간마다 ${e.tickets.claim}장`,
  `🥚 뽑기 · 하급 ${e.gachas.LOW.cost}장 / 중급 ${e.gachas.MID.cost}장 / 고급 ${e.gachas.HIGH.cost}장`,
  '🧩 조합가능 · 지금 만들 수 있는 것',
  '🔮 조합 고르기 · 재료를 직접 골라 숨은 조합 찾기',
  '🧭 탐험 · 최상위 갈매미를 보내 새우gang 벌기',
  '🎯 미션 · 🏆 칭호 · 📚 도감 · 🐦 내갈매미',
  '🥇 랭킹 · 🏷 닉네임 · 💬 댓글 · 🛒 상점 · 🍤 팔기 · 🥜 땅콩떼기',
  '💌 초대코드 · 📘 튜토리얼 · 🎉 이벤트',
  '',
  '이렇게도 써요',
  helpExamples
].join('\n');
// 튜토리얼: 1 뽑기 → 2 조합 → 3 도감 보기 → 4 닉네임 정하기 → 5 재료로 직접 조합(숨은 조합 찾는 법). 순서대로 달성하면 단계마다 뽑기권(웹 이벤트 페이지도 이 목록을 씀)
export const tutorialSteps=[
  {title:'뽑기 해 보기',how:'아무 뽑기나 1번',go:choice('뽑기'),image:'m-draw'},
  {title:'조합해 보기',how:'조합가능에서 하나 만들기',go:choice('조합가능'),image:'m-craft'},
  {title:'도감 열어 보기',how:'모은 갈매미 확인하기',go:choice('도감'),image:'s-dex'},
  {title:'닉네임 정하기',how:'"닉네임 갈매미왕"처럼 입력',go:choice('닉네임'),image:'s-rank'},
  {title:'재료로 직접 조합해 보기',how:'재료 고르기에서 2마리 이상 골라 섞기',go:choice('재료 고르기','조합 고르기'),image:'s-mix'}];
// 결과 말풍선: 이번에 얻은 것 → 구분선 → 📌 지금 상태. 숫자는 천 단위 쉼표
const RULE='──────────';
const num=(n:number)=>n.toLocaleString('ko-KR');
const report=(below:string,nextLine:string)=>`${below?`${below}\n\n${RULE}\n`:''}${nextLine}`;
const statusOf=(c:Count|undefined):Count=>c??{total:0,usable:0,locked:0,away:0};

export const LEGACY_RANK_UNAVAILABLE="랭크는 실제 유저끼리만 대전해요. 최신 앱의 실시간 유저 랭크전에서 다시 시작해 주세요.";

export class GameService {
  private tail:Promise<unknown>=Promise.resolve();
  private pending=0;
  private readonly topIds:string[];
  // 이번 요청 사용자가 한 번이라도 얻은 유닛(요청은 tail로 하나씩 처리). 미수집 유닛 그림 스포일러 방지용
  private viewerSeen=new Set<string>();
  constructor(private db:PrismaClient,public content:Content,private clock=()=>new Date(),
    private gacha=new GachaEngine(content.characters,content.economy.gachas),private roll:Roll=randomInt,private root=process.cwd(),private adminCode?:string){
    this.topIds=content.characters.filter(c=>topTiers.has(c.rarity)).map(c=>c.id);
  }
  handle(identity:string,message:string,button?:ButtonAction):Promise<GameReply>{
    if(this.pending>=30)return Promise.resolve({text:'요청이 몰렸어요. 잠시 뒤 다시 입력해 주세요.'});
    this.pending++;
    let expired=false;
    let timer:ReturnType<typeof setTimeout>;
    const timeout=new Promise<GameReply>((_resolve,reject)=>{
      timer=setTimeout(()=>{expired=true;reject(new GameError('서버가 바빠요. 잠시 뒤 같은 요청을 다시 보내 주세요.'));},1000);
    });
    const task=this.tail.then(async()=>{
      if(expired)throw new GameError('대기 중 만료된 요청');
      clearTimeout(timer);
      return this.db.$transaction(async tx=>{
        const now=this.clock();
        const player=await this.player(tx,identity,now);
        this.viewerSeen=new Set((await tx.collectionEntry.findMany({where:{playerId:player.id},select:{characterId:true}})).map(r=>r.characterId));
        return this.tidy(tx,player,message,now,await this.dispatch(tx,player,message,now,button));
      },{maxWait:500,timeout:2500});
    });
    this.tail=task.catch(()=>{});
    return Promise.race([task,timeout]).then(result=>{
      const imageName=result.imageId?this.content.map.get(result.imageId)?.name:undefined;
      // 같은 버튼이 두 번 보이지 않게 정리합니다(카카오 바로가기 최대 10개).
      const seen=new Set<string>();
      const choices=result.choices?.filter(c=>!seen.has(c.message)&&Boolean(seen.add(c.message))).slice(0,10);
      // 그 밖의 글 응답도 기본 그림 카드로 감쌉니다(그림: 응답의 유닛, 없으면 기본 갈매미맨)
      const wrapped=this.info(result,result.imageId??'s-info');
      return {...wrapped,...(choices?{choices}:{}),...(imageName?{imageName}:{})};
    }).finally(()=>{clearTimeout(timer);this.pending--;});
  }

  // ───────── 공통 ─────────
  private async audit(tx:Tx,p:Player,kind:string,detail:unknown){
    await tx.auditEvent.create({data:{playerId:p.id,kind,detail:json(detail)}});
  }
  private async player(tx:Tx,identity:string,now:Date){
    let p=await tx.player.findUnique({where:{identity}});
    if(!p){
      const welcome=this.content.economy.tickets.welcome;
      p=await tx.player.create({data:{identity,credits:welcome,createdAt:now}});
      await this.audit(tx,p,'WELCOME',{credits:welcome});
    }
    return p;
  }
  private image(id:string,known:Set<string>){
    const c=this.content.map.get(id);
    if(!c||!isRevealed(c,known))return undefined;
    return existsSync(resolve(this.root,'public/images',`${id}.png`))?id:undefined;
  }
  // 카드 그림: 공개됐고 그림이 있으면 그 유닛, 아니면 미발견 실루엣
  private cardImage(id:string,known:Set<string>){return this.image(id,known)??'unknown';}
  // 등급 실루엣(q-<등급>.png, 없으면 기본 실루엣): 아직 얻지 못한 유닛의 그림 대신
  private tierFace(t:string){return existsSync(resolve(this.root,'public/images',`q-${t}.png`))?`q-${t}`:'unknown';}
  // 사용자가 얻은 적 있는 유닛만 그림, 아니면 등급 실루엣(스포일러 방지)
  private face(id:string,known:Set<string>){
    const c=this.content.map.get(id);
    if(!c)return 'unknown';
    return this.viewerSeen.has(id)?this.cardImage(id,known):c.rarity==='HIDDEN'&&!isRevealed(c,known)?'unknown':this.tierFace(c.rarity);
  }
  private seenImage(id:string,known:Set<string>){return this.viewerSeen.has(id)?this.image(id,known):undefined;}
  private maskCard(c:CardEntry,known:Set<string>):CardEntry{
    const m=(t:string)=>mask(this.content,t,known);
    return {...c,title:m(c.title),description:m(c.description),...(c.buttons?{buttons:c.buttons.map(b=>({...b,label:m(b.label),message:m(b.message)}))}:{})};
  }
  // 획득 결과 카드 1장: 연출 → 그림 카드 → 보상·미션 / 구분선 / 잔액 안내
  private acquired(c:Character,kind:AcquisitionKind,receipt:Receipt,known:Set<string>,gains:string[],nextLine:string,intro='',buttons:Choice[]=[]):Pick<GameReply,'cards'>{
    const a=acquisitionCard(this.content,c,kind,receipt,known);
    const below=[...a.extra,...gains].join('\n');
    // 전설 이상 등장 연출은 카드 앞 말풍선으로 남김(그 밖의 글은 카드 설명으로 합쳐짐)
    return {cards:{intro:[intro,a.drama].filter(Boolean).join('\n\n')||undefined,keepIntro:Boolean(a.drama)&&tiers[c.rarity]>=tiers.LEGEND,
      items:[this.maskCard({imageId:this.cardImage(c.id,known),title:a.title,description:a.description,buttons:[choice('도감 보기',`도감 ${c.name}`),...buttons].slice(0,3)},known)],
      outro:report(below,nextLine)}};
  }
  // 글 응답을 큰 카드 1장으로: 첫 줄 = 제목, 다음 줄들(빈 줄 전까지, 최대 3줄) = 설명, 나머지 = 아래 말풍선
  private info(reply:GameReply,imageId:string,buttons?:Choice[]):GameReply{
    if(reply.cards||reply.list||!reply.text)return reply;
    const [title,...rest]=reply.text.split('\n');
    const blank=rest.findIndex(l=>!l.trim());
    const desc=(blank<0?rest:rest.slice(0,blank)).slice(0,3);
    const after=rest.slice(desc.length).join('\n').trim();
    return {...reply,cards:{items:[{imageId,title,description:desc.join('\n'),buttons:(buttons??reply.choices??[]).slice(0,3)}],...(after?{outro:after}:{})}};
  }
  // 뽑기 3종 카드. 그림은 그 뽑기에서 나오는 대표 유닛
  private gachaCards(p:Player):CardEntry[]{
    const g=this.content.economy.gachas;
    return (['LOW','MID','HIGH'] as GachaKind[]).map(kind=>({imageId:`gacha-${kind}`,title:`${g[kind].label} 뽑기 · 🎟 ${g[kind].cost}장`,
      description:Object.entries(g[kind].weights).map(([t,w])=>`${emblems[t]} ${rarityNames[t]} ${w}%`).join('\n')+(g[kind].pity?`\n🎯 ${rarityNames[g[kind].pity!.tier]} 보장까지 ${g[kind].pity!.count-p.highPity}회`:''),
      buttons:[choice('1회',`${g[kind].label}뽑기`),choice('10회',`${g[kind].label}뽑기 10`)]}));
  }
  // 등급 카드 그림: 그 등급의 첫 유닛. 히든은 발견한 것이 있으면 그것, 없으면 실루엣
  private gradeImage(t:Tier,known:Set<string>){
    const first=this.content.characters.find(c=>c.rarity===t&&this.viewerSeen.has(c.id)&&isRevealed(c,known));
    return first?this.cardImage(first.id,known):this.tierFace(t);
  }
  private helpCards():CardEntry[]{
    const g=this.content.economy.gachas,t=this.content.economy.tickets,none=new Set<string>();
    return [
      {imageId:'m-draw',title:'🥚 뽑기',description:`${t.cooldownHours}시간마다 뽑기권 ${t.claim}장\n하급 ${g.LOW.cost} · 중급 ${g.MID.cost} · 고급 ${g.HIGH.cost}장`,buttons:[choice('뽑기'),choice('뽑기권 받기','뽑기권받기'),choice('확률')]},
      {imageId:'m-craft',title:'🧩 조합',description:'재료를 모아 높은 등급을 만들어요',buttons:[choice('조합가능'),choice('조합목록')]},
      {imageId:'s-mix',title:'🔮 재료로 조합',description:'재료를 직접 골라 섞어요. 숨은 조합(히든)도 이렇게 찾아요',buttons:[choice('재료 고르기','조합 고르기')]},
      {imageId:'explore',title:'🧭 탐험',description:'최상위 갈매미를 보내 새우gang을 벌어요',buttons:[choice('탐험'),choice('시너지')]},
      {imageId:'s-dex',title:'📚 도감',description:'모은 갈매미와 칭호·미션',buttons:[choice('도감'),choice('내갈매미'),choice('내정보')]},
      {imageId:'s-shop',title:'🛒 상점',description:'새우gang으로 흔함·안흔함·뽑기권',buttons:[choice('상점')]},
      {imageId:'s-invite',title:'💌 친구 초대',description:`초대 코드를 나누면 둘 다 🎟 ${this.content.economy.invite.tickets}`,buttons:[choice('초대코드'),choice('튜토리얼')]},
      {imageId:'s-rank',title:'🥇 랭킹',description:'도감 수집 순위\n닉네임을 정하면 이름이 나와요',buttons:[choice('랭킹'),choice('닉네임')]}
    ];
  }
  private async known(tx:Tx,p:Player){
    const rows=await tx.collectionEntry.findMany({where:{playerId:p.id,characterId:{in:this.content.hiddenIds}},select:{characterId:true}});
    return new Set(rows.map(r=>r.characterId));
  }
  private name(id:string,known:Set<string>){return displayName(this.content.map.get(id)!,known);}
  private revealed(known:Set<string>,filter:(c:Character)=>boolean=()=>true){
    return this.content.characters.filter(c=>isRevealed(c,known)&&filter(c));
  }
  private async hasTop(tx:Tx,p:Player){
    return (await tx.ownedCharacter.count({where:{playerId:p.id,status:{in:['AVAILABLE','EXPEDITION']},characterId:{in:this.topIds}}}))>0;
  }
  // 관리자 모드를 켜는 순간의 계정 상태를 저장합니다.
  private async saveSnapshot(tx:Tx,p:Player,now:Date){
    const where={playerId:p.id};
    const [owned,collection,expeditions,achievements,daily]=await Promise.all([
      tx.ownedCharacter.findMany({where}),tx.collectionEntry.findMany({where}),tx.expedition.findMany({where}),tx.achievement.findMany({where}),tx.dailyProgress.findMany({where})]);
    const data={player:{credits:p.credits,snack:p.snack,lastClaimAt:p.lastClaimAt,highPity:p.highPity,peanut:p.peanut},owned,collection,expeditions,achievements,daily};
    await tx.adminSnapshot.upsert({where:{playerId:p.id},create:{playerId:p.id,data:json(data),createdAt:now},update:{data:json(data),createdAt:now}});
  }
  // 저장해 둔 상태로 되돌립니다. 저장된 것이 없으면 아무것도 바꾸지 않습니다.
  private async restoreSnapshot(tx:Tx,p:Player){
    const snap=await tx.adminSnapshot.findUnique({where:{playerId:p.id}});
    if(!snap)return false;
    const d=snap.data as any;
    const date=(v:string|null)=>v?new Date(v):null;
    const where={playerId:p.id};
    await tx.pendingAction.deleteMany({where});
    await tx.ownedCharacter.deleteMany({where});
    await tx.collectionEntry.deleteMany({where});
    await tx.expedition.deleteMany({where});
    await tx.achievement.deleteMany({where});
    await tx.dailyProgress.deleteMany({where});
    if(d.owned.length)await tx.ownedCharacter.createMany({data:d.owned.map((o:any)=>({...o,obtainedAt:new Date(o.obtainedAt),consumedAt:date(o.consumedAt)}))});
    if(d.collection.length)await tx.collectionEntry.createMany({data:d.collection});
    if(d.expeditions.length)await tx.expedition.createMany({data:d.expeditions.map((e:any)=>({...e,startedAt:new Date(e.startedAt),endsAt:new Date(e.endsAt),claimedAt:date(e.claimedAt),result:e.result??Prisma.DbNull}))});
    if(d.achievements.length)await tx.achievement.createMany({data:d.achievements.map((a:any)=>({...a,createdAt:new Date(a.createdAt)}))});
    if(d.daily.length)await tx.dailyProgress.createMany({data:d.daily});
    await tx.player.update({where:{id:p.id},data:{credits:d.player.credits,snack:d.player.snack,lastClaimAt:date(d.player.lastClaimAt),highPity:d.player.highPity??0,peanut:d.player.peanut??0,revision:{increment:1}}});
    await tx.adminSnapshot.delete({where:{playerId:p.id}});
    return true;
  }
  private claimWait(p:Player,now:Date){
    if(!p.lastClaimAt)return 0;
    return Math.max(0,p.lastClaimAt.getTime()+this.content.economy.tickets.cooldownHours*3_600_000-now.getTime());
  }
  // "다음 뽑기권: 4시간 48분 뒤 (오후 11:24)"
  private waitLine(p:Player,now:Date){
    const wait=this.claimWait(p,now);
    if(!wait)return '🎟 지금 뽑기권을 받을 수 있어요!';
    // 서버의 ICU 데이터에 따라 'PM'이 나올 수 있어 오전/오후를 직접 붙입니다.
    const t=DateTime.fromMillis(now.getTime()+wait).setZone('Asia/Seoul');
    return `⏳ 다음 뽑기권: ${duration(wait)} 뒤 (${t.hour<12?'오전':'오후'} ${t.hour%12||12}:${String(t.minute).padStart(2,'0')})`;
  }
  // 뽑기권이 없을 때 대신 할 수 있는 것
  private async meanwhile(tx:Tx,p:Player,now:Date){
    const lines:string[]=[],choices:Choice[]=[];
    const ready=await this.craftable(tx,p);
    if(ready){lines.push(`✨ 조합 가능 ${ready}종`);choices.push(choice(`조합가능 ${ready}`,'조합가능'));}
    const trip=await this.activeExpedition(tx,p);
    if(trip){
      const left=trip.endsAt.getTime()-now.getTime();
      lines.push(left>0?`🧭 탐험대 귀환까지 ${duration(left)}`:'🧭 탐험대가 돌아왔어요!');
      if(left<=0)choices.push(choice('탐험 보상 받기','탐험보상받기'));
    }else if(await this.hasTop(tx,p)){lines.push('🧭 탐험을 보낼 수 있어요');choices.push(choice('탐험'));}
    const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const row=await tx.dailyProgress.findUnique({where:{playerId_day:{playerId:p.id,day}}});
    const done=(row?.rewarded??'').split(',').filter(Boolean).length,total=this.content.economy.missions.length;
    if(done<total){lines.push(`🎯 오늘의 미션 ${done}/${total}`);choices.push(choice('미션'));}
    if(p.snack>=this.content.economy.exchange.snackCost){lines.push(`🍤 새우gang ${p.snack}개 · 상점에서 쓸 수 있어요`);choices.push(choice('상점'));}
    return {text:lines.length?`\n\n그동안 해 볼 것\n${lines.join('\n')}`:'',choices};
  }
  private claimHint(p:Player,now:Date){
    const wait=this.claimWait(p,now);
    return wait?`다음 뽑기권 받기: ${duration(wait)} 뒤`:'🎟 지금 뽑기권을 받을 수 있어요!';
  }
  // again: 고른 유닛으로 다시 보낼 전체 명령(여러 재료 중 하나만 고를 때 나머지 재료를 지키기 위함)
  private resolve(query:string,pool:Character[],command:string,known:Set<string>,again:(c:Character)=>string=c=>`${command} ${c.name}`):Character|GameReply{
    const found=matchCharacters(pool,query);
    if(found.length===1)return found[0];
    if(!found.length)return this.info({text:`🤔 "${query}"… 그런 갈매미는 없어요.\n이름 일부만 써도 돼요. 예: ${command} 금갑`,choices:[choice('조합목록'),choice('도감'),choice('내갈매미')]},'unknown');
    const picks=found.slice(0,10);
    return {...listReply('어떤 갈매미일까요?',picks.slice(0,5).map(c=>({title:`${emblems[c.rarity]} ${c.name}`,description:characterInfo(this.content,c),message:again(c)})),
      '눌러서 골라 주세요. 없으면 이름을 더 길게 써 주세요.',[choice('도움말')]),list:undefined,
      cards:{intro:'🤔 어떤 갈매미일까요?',items:picks.map(c=>({imageId:this.face(c.id,known),title:`${emblems[c.rarity]} ${c.name}`,description:characterInfo(this.content,c),buttons:[choice('이걸로',again(c))]}))}};
  }
  private async issue(tx:Tx,p:Player,kind:string,payload:ActionPayload,now:Date,text:string,imageId?:string,detail?:string,cards?:GameReply['cards']):Promise<GameReply>{
    await tx.pendingAction.deleteMany({where:{playerId:p.id,result:{equals:Prisma.DbNull},expiresAt:{lt:now}}});
    const token=randomBytes(8).toString('hex');
    await tx.pendingAction.create({data:{token,playerId:p.id,revision:p.revision,kind,payload:json(payload),expiresAt:new Date(now.getTime()+600_000)}});
    const [header,title,message,fallback]=kind==='CRAFT'?['만들까요?','🧩 조합하기','조합 확정','재료를 써서 1마리를 만들어요']:
      kind==='EXPEDITION'?['출발할까요?','🧭 탐험 출발','탐험 확정','돌아올 때까지 다른 데 못 써요']:kind.startsWith('SHOP')?['살까요?','🛒 구매하기','구매 확정',kind==='SHOP_BADGE'||kind==='SHOP_PEANUT_TICKETS'?'땅콩을 써요':'새우gang을 써요']:kind==='PEANUT'?['땅콩을 뗄까요?','🥜 땅콩떼기','땅콩떼기 확정','되돌릴 수 없어요']:kind==='SELL'?['팔까요?','🍤 팔기','팔기 확정','되돌릴 수 없어요']:['교환할까요?','🍤 교환하기','교환 확정','새우gang을 써서 받아요'];
    return {text,imageId,...(cards?{cards}:{}),list:{title:header,showText:true,items:[
      {title,description:detail??fallback,message,button:{action:'confirm',token}},
      {title:'그만두기',description:'아무것도 쓰지 않아요',message:'취소',button:{action:'cancel',token}}
    ]}};
  }
  private async grant(tx:Tx,p:Player,characterId:string,via:string,now:Date){
    const owned=await tx.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:via,obtainedAt:now}});
    const collection=await tx.collectionEntry.upsert({where:{playerId_characterId:{playerId:p.id,characterId}},create:{playerId:p.id,characterId},update:{count:{increment:1}}});
    const c=this.content.map.get(characterId)!;
    const first=collection.count===1;
    const reward=this.content.economy.rewards[c.rarity];
    const planned=first?reward.first:reward.duplicate;
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{snack:true}});
    const boost=this.event(now)?.snackMultiplier??1;   // 이벤트 기간 획득 보상 배율
    const snacks=Math.max(0,Math.min(Math.round(planned*boost),2_000_000_000-account.snack));
    if(snacks){
      await tx.player.update({where:{id:p.id},data:{snack:{increment:snacks}}});
      await this.audit(tx,p,'ACQUISITION_REWARD',{ownedId:owned.id,characterId,via,first,obtainedCount:collection.count,delta:snacks,balanceAfter:account.snack+snacks});
    }
    const tierIds=this.content.characters.filter(x=>x.rarity===c.rarity).map(x=>x.id);
    const tierKnown=await tx.collectionEntry.count({where:{playerId:p.id,characterId:{in:tierIds}}});
    const receipt:Receipt={first,count:collection.count,snacks,snackBalance:account.snack+snacks,tierKnown,tierTotal:tierIds.length};
    if(first&&tierKnown===tierIds.length){
      const key=`TIER_${c.rarity}`;
      if(!(await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key}}}))){
        const a=this.content.economy.achievements[c.rarity];
        await tx.achievement.create({data:{playerId:p.id,key,createdAt:now}});
        if(a.tickets)await tx.player.update({where:{id:p.id},data:{credits:{increment:a.tickets}}});
        await this.audit(tx,p,'ACHIEVEMENT',{key,title:a.title,tickets:a.tickets});
        receipt.achievement={tier:c.rarity,title:a.title,tickets:a.tickets};
      }
    }
    return {...owned,receipt};
  }
  // 오늘(한국 시간)의 미션 진행을 올리고, 새로 달성한 미션 보상을 지급합니다.
  // 미션 키 → 오늘 진행 칸
  private static missionField={DRAW:'draws',CRAFT:'crafts',CRAFT_RARE:'craftRare',CRAFT_LEGEND:'craftLegend',CRAFT_TOP:'craftTop',EXPEDITION:'expeditions'} as const;
  // 오늘(한국 시간)의 미션 진행을 올리고, 새로 달성한 미션 보상을 지급합니다.
  // 조합은 결과 등급에 따라 희귀·전설/히든·최상위 칸도 함께 올립니다.
  private async progress(tx:Tx,p:Player,kind:'DRAW'|'CRAFT'|'EXPEDITION',amount:number,now:Date,crafted?:Tier):Promise<string[]>{
    await awardPlayPoints(tx,p,amount*(kind==='CRAFT'?3:1),now);
    const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const add:Record<string,number>={[GameService.missionField[kind]]:amount};
    if(kind==='CRAFT'&&crafted){
      if(crafted==='RARE')add.craftRare=amount;
      if(crafted==='LEGEND'||crafted==='HIDDEN')add.craftLegend=amount;
      if(topTiers.has(crafted))add.craftTop=amount;
    }
    const row=await tx.dailyProgress.upsert({where:{playerId_day:{playerId:p.id,day}},create:{playerId:p.id,day,...add},
      update:Object.fromEntries(Object.entries(add).map(([k,v])=>[k,{increment:v}]))});
    const done=new Set(row.rewarded.split(',').filter(Boolean));
    const lines:string[]=[];
    for(const m of this.content.economy.missions){
      const value=row[GameService.missionField[m.key]];
      if(value<m.goal||done.has(m.key))continue;
      done.add(m.key);
      if(m.tickets)await tx.player.update({where:{id:p.id},data:{credits:{increment:m.tickets}}});
      await this.audit(tx,p,'MISSION',{day,key:m.key,tickets:m.tickets});
      lines.push(`🎉 오늘의 미션 완료! ${m.label} · 🎟 +${m.tickets}`);
    }
    if(lines.length)await tx.dailyProgress.update({where:{playerId_day:{playerId:p.id,day}},data:{rewarded:[...done].join(',')}});
    return lines;
  }
  private async missionText(tx:Tx,p:Player,now:Date){
    const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const row=await tx.dailyProgress.findUnique({where:{playerId_day:{playerId:p.id,day}}});
    const done=new Set((row?.rewarded??'').split(',').filter(Boolean));
    return this.content.economy.missions.map(m=>{
      const value=Math.min(m.goal,row?.[GameService.missionField[m.key]]??0);
      return `${done.has(m.key)?'✅':'⬜'} ${m.label}${m.goal>1?` (${value}/${m.goal})`:''} · 🎟 ${m.tickets}`;
    }).join('\n');
  }
  private async titles(tx:Tx,p:Player){
    const rows=await tx.achievement.findMany({where:{playerId:p.id,key:{startsWith:'TIER_'}}});
    const got=new Set(rows.map(r=>r.key.slice(5)));
    return tierOrder.filter(t=>got.has(t)).map(t=>this.content.economy.achievements[t].title);
  }
  // 지금 만들 수 있는 조합 수(공개된 조합식 기준)
  private async craftable(tx:Tx,p:Player){
    const known=await this.known(tx,p);
    const stock=await this.stock(tx,p);
    return this.visibleRecipes(known).filter(r=>this.canCraft(r,stock)).length;
  }
  // 재료가 딱 1마리 모자란 조합(높은 등급부터)
  private async almost(tx:Tx,p:Player){
    const known=await this.known(tx,p),stock=await this.stock(tx,p);
    return this.visibleRecipes(known).map(r=>{
      const short=[...materialCounts(r.materials)].map(([id,n])=>({id,lack:Math.max(0,n-statusOf(stock.get(id)).usable)})).filter(x=>x.lack);
      return {c:this.content.map.get(r.resultId)!,short};
    }).filter(x=>x.short.length===1&&x.short[0].lack===1)
      .sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity]||b.c.position-a.c.position)
      .map(x=>({c:x.c,lack:this.name(x.short[0].id,known)}));
  }
  // 결과 끝에 붙는 다음 행동 안내와 버튼
  private async nextStep(tx:Tx,p:Player,now:Date,first:Choice[]=[]){
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id}});
    const ready=await this.craftable(tx,p);
    const cheapest=Math.min(...Object.values(this.content.economy.gachas).map(g=>g.cost));
    const canClaim=!this.claimWait(account,now);
    const lines=[`📌 지금  🎟 ${unlimited(account)?'무제한(관리자)':`${num(account.credits)}장`} · 🍤 ${num(account.snack)}개`];
    if(ready)lines.push(`✨ 조합 가능 ${ready}종`);
    const near=(await this.almost(tx,p))[0];
    if(near)lines.push(`🔜 ${near.lack} 1마리만 더 있으면 ${emblems[near.c.rarity]} ${near.c.name}!`);
    if(!unlimited(account)&&account.credits<cheapest)lines.push(canClaim?`🎟 뽑기권 받기를 눌러 ${this.content.economy.tickets.claim+(this.event(now)?.claimBonus??0)}장을 받으세요!`:this.waitLine(account,now));
    const choices=[...first,...(ready?[choice(`조합가능 ${ready}`,'조합가능')]:[]),...(near?[choice(`${near.c.name.slice(0,8)} 재료`,`조합식 ${near.c.name}`)]:[]),...(canClaim?[choice('뽑기권 받기','뽑기권받기')]:[]),choice('뽑기'),...menu.slice(2)];
    return {line:lines.join('\n'),choices,account};
  }
  private async stock(tx:Tx,p:Player):Promise<Stock>{
    const rows=await tx.ownedCharacter.groupBy({by:['characterId','locked','status'],where:{playerId:p.id,status:{in:['AVAILABLE','EXPEDITION']}},_count:{_all:true}});
    const result:Stock=new Map();
    for(const row of rows){
      const count=statusOf(result.get(row.characterId));
      const n=row._count._all;
      count.total+=n;
      if(row.status==='EXPEDITION')count.away+=n;
      else if(row.locked)count.locked+=n;
      else count.usable+=n;
      result.set(row.characterId,count);
    }
    return result;
  }

  // ───────── 뽑기권 · 뽑기 ─────────
  private async claim(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const t=this.content.economy.tickets;
    const wait=this.claimWait(p,now);
    if(wait){
      const alt=await this.meanwhile(tx,p,now);
      return this.info({text:`⏳ 아직 받을 수 없어요\n${this.waitLine(p,now).slice(2)}\n🎟 지금 ${p.credits}장${alt.text}`,choices:[...alt.choices,choice('뽑기'),...menu.slice(2)]},'s-wait');
    }
    const ev=this.event(now),amount=t.claim+(ev?.claimBonus??0);   // 이벤트 기간 추가 지급
    const updated=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{credits:{increment:amount},lastClaimAt:now,revision:{increment:1}}});
    if(updated.count!==1)throw new GameError('잠깐 겹쳤어요. 뽑기권 받기를 다시 눌러 주세요.');
    await this.audit(tx,p,'TICKET_CLAIM',{delta:amount,balanceAfter:p.credits+amount,...(ev?{event:ev.label}:{})});
    return this.info({text:`🎟 뽑기권 +${amount}!${ev?.claimBonus?` (🎉 ${ev.label} +${ev.claimBonus})`:''}\n지금 ${p.credits+amount}장 · 다음은 ${t.cooldownHours}시간 뒤`,choices:[...this.drawChoices(p,p.credits+amount),choice('뽑기'),...menu.slice(2)]},'ticket');
  }
  private async gachaMenu(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const g=this.content.economy.gachas;
    const cheapest=Math.min(...Object.values(g).map(x=>x.cost));
    if(!unlimited(p)&&p.credits<cheapest){
      const alt=await this.meanwhile(tx,p,now);
      return this.info({text:`🥚 뽑기 · 🎟 ${p.credits}장\n뽑기권이 모자라요. (하급 ${g.LOW.cost}장부터)\n${this.waitLine(p,now)}${alt.text}`,
        choices:[...(this.claimWait(p,now)?[]:[choice('뽑기권 받기','뽑기권받기')]),...alt.choices,...menu.slice(2)]},'s-wait');
    }
    const items=(['LOW','MID','HIGH'] as GachaKind[]).map(kind=>({
      title:`${g[kind].label} 뽑기 · 🎟 ${g[kind].cost}장`,
      description:Object.entries(g[kind].weights).map(([t,w])=>`${emblems[t]}${rarityNames[t]} ${w}`).join(' · ')+'%',
      message:`${g[kind].label}뽑기`
    }));
    const ev=this.event(now);
    const title=`🥚 뽑기 · 🎟 ${unlimited(p)?'무제한(관리자)':`${p.credits}장`}${ev?`\n🎉 ${ev.label} 진행 중!${ev.claimBonus?` 뽑기권 받기 +${ev.claimBonus}`:''}${ev.snackMultiplier>1?` · 새우gang 보상 ×${ev.snackMultiplier}`:''}`:''}`;
    const hint=`숫자를 붙이면 여러 번 (최대 50): 하급뽑기 30${this.claimWait(p,now)?'':'\n🎟 지금 뽑기권을 받을 수 있어요!'}`;
    const cards=[...(ev?[{imageId:'s-event',title:`🎉 ${ev.label}`,description:`${ev.claimBonus?`뽑기권 받기 +${ev.claimBonus}`:''}${ev.claimBonus&&ev.snackMultiplier>1?' · ':''}${ev.snackMultiplier>1?`새우gang 보상 ×${ev.snackMultiplier}`:''}\n~ ${DateTime.fromISO(ev.to).setZone('Asia/Seoul').toFormat('M/d HH:mm')}`} as CardEntry]:[]),...this.gachaCards(p)];
    return {text:listReply(title,items,hint).text,cards:{intro:title,items:cards,outro:hint},
      choices:[...this.drawChoices(p),choice('뽑기권 받기','뽑기권받기'),...menu.slice(2,6)]};
  }
  // out: 웹 화면용으로 뽑은 결과(유닛·처음 발견)를 받아 감
  private async draw(tx:Tx,p:Player,kind:GachaKind,times:number,now:Date,out?:{c:Character;first:boolean;key:string}[]):Promise<GameReply>{
    const g=this.content.economy.gachas[kind];
    const cost=g.cost*times;
    const admin=unlimited(p);
    if(!admin&&p.credits<cost){
      const cheaper=(['LOW','MID','HIGH'] as GachaKind[]).filter(k=>this.content.economy.gachas[k].cost<=p.credits);
      const alt=await this.meanwhile(tx,p,now);
      return this.info({text:`🎟 뽑기권이 모자라요\n${g.label} ${times}회 ${cost}장 · 지금 ${p.credits}장${cheaper.length?`\n(${cheaper.map(k=>this.content.economy.gachas[k].label).join('·')} 뽑기는 지금 할 수 있어요)`:''}\n${this.waitLine(p,now)}${alt.text}`,
        choices:[...cheaper.map(k=>choice(`${this.content.economy.gachas[k].label}뽑기`)),...(this.claimWait(p,now)?[]:[choice('뽑기권 받기','뽑기권받기')]),...alt.choices,...menu.slice(2)]},'s-wait');
    }
    // 관리자 모드를 켠 관리자는 테스트용으로 뽑기권을 쓰지 않습니다.
    const paid=await tx.player.updateMany({where:{id:p.id,revision:p.revision,...(admin?{}:{credits:{gte:cost}})},data:{...(admin?{}:{credits:{decrement:cost}}),revision:{increment:1}}});
    if(paid.count!==1)throw new GameError('잠깐 겹쳤어요. 뽑기를 다시 눌러 주세요.');
    const known=await this.known(tx,p);
    const results:{c:Character;receipt:Receipt}[]=[];
    // 천장: 지정 등급 이상 없이 count회째가 되면 그 등급 확정
    const pity=kind==='HIGH'?g.pity:undefined;   // 천장 카운트는 고급 뽑기에만 저장합니다
    let counter=p.highPity,pityHit=false;
    for(let i=0;i<times;i++){
      let c=this.gacha.draw(kind);
      if(pity){
        counter++;
        if(tiers[c.rarity]<tiers[pity.tier]&&counter>=pity.count){c=this.gacha.drawTier(pity.tier);pityHit=true;}
        if(tiers[c.rarity]>=tiers[pity.tier])counter=0;
      }
      const owned=await this.grant(tx,p,c.id,'GACHA',now);
      if(kind==='HIGH')await tx.ownedCharacter.update({where:{id:owned.id},data:{obtainedVia:'GACHA_HIGH'}});
      results.push({c,receipt:owned.receipt});out?.push({c,first:Boolean(owned.receipt.first),key:`o${owned.id}`});
    }
    if(pity)await tx.player.update({where:{id:p.id},data:{highPity:counter}});
    const pityLine=pity?(pityHit?`🎯 천장! ${rarityNames[pity.tier]} 확정`:`🎯 ${rarityNames[pity.tier]} 보장까지 ${pity.count-counter}회`):'';
    await this.audit(tx,p,'GACHA',{kind,times,cost:admin?0:cost,admin,results:results.map(r=>r.c.id),balanceAfter:admin?p.credits:p.credits-cost,...(pity?{pityAfter:counter,pityHit}:{})});
    const missions=[...await this.progress(tx,p,'DRAW',times,now),...await this.tutorialStep(tx,p,1),...(pityLine?[pityLine]:[])];
    const again=admin||p.credits-cost>=cost;
    const next=await this.nextStep(tx,p,now,again?[choice(times>1?`${g.label} ×${times} 더`:`${g.label} 한 번 더`,`${g.label}뽑기${times>1?` ${times}`:''}`)]:[]);
    const tail=[...missions,next.line].join('\n');
    if(times===1){
      const {c,receipt}=results[0];
      return {text:`${acquisitionText(this.content,c,'GACHA',receipt,known)}\n\n${tail}`,...(receipt.first?{imageId:this.image(c.id,known)}:{}),
        ...this.acquired(c,'GACHA',receipt,known,missions,next.line,'',tiers[c.rarity]>=tiers.LEGEND?[shareChoice()]:[]),
        choices:next.choices};
    }
    const firstNew=results.filter(r=>r.receipt.first).sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity])[0];
    const top=[...results].sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity])[0];
    const snacks=results.reduce((sum,r)=>sum+r.receipt.snacks,0);
    const news=results.filter(r=>r.receipt.first).length;
    // 같은 유닛은 한 줄로 묶고, 높은 등급부터 등급별로 보여 줍니다.
    const groups=new Map<string,{c:Character;n:number;first:boolean}>();
    for(const r of results){const g=groups.get(r.c.id)??{c:r.c,n:0,first:false};g.n++;g.first||=r.receipt.first;groups.set(r.c.id,g);}
    const sorted=[...groups.values()].sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity]||Number(b.first)-Number(a.first)||a.c.position-b.c.position);
    const blocks:string[]=[];
    for(const t of [...new Set(sorted.map(x=>x.c.rarity))]){
      const rows=sorted.filter(x=>x.c.rarity===t).map(x=>`  ${x.first?'🆕 ':''}${x.c.name}${x.n>1?` ×${x.n}`:''}`);
      blocks.push(`${emblems[t]} ${rarityNames[t]}\n${rows.join('\n')}`);
    }
    // 희귀 이상을 새로 찾았거나 전설 이상이 나오면 맨 위에서 연출합니다.
    const drama=tiers[top.c.rarity]>=tiers.LEGEND?prelude(top.c,!!top.receipt.first):firstNew&&tiers[firstNew.c.rarity]>=tiers.RARE?prelude(firstNew.c,true):'';
    const head=`${drama}🥚 ${g.label} 뽑기 ×${times}${news?` · 🆕 새 발견 ${news}종!`:''}`;
    const gains=[...(snacks?[`🍤 새우gang +${num(snacks)}`]:[]),...missions].join('\n');
    const achieved=results.filter(r=>r.receipt.achievement).map(r=>achievementText(r.receipt.achievement!));
    const outro=report([...(gains?[gains]:[]),...achieved].join('\n\n'),next.line);
    const body=[head,blocks.join('\n\n'),outro].join('\n\n');
    // 카드 넘기기: 유닛마다 그림 카드 1장(높은 등급부터). 그림이 하나라도 없으면 글로만 보냅니다. 전설 이상은 📤 공유하기
    const items=sorted.map(x=>this.maskCard({imageId:this.cardImage(x.c.id,known),title:`${emblems[x.c.rarity]} ${x.c.name}${x.n>1?` ×${x.n}`:''}`,
      description:`${rarityNames[x.c.rarity]}${x.first?' · 🆕 새 발견!':''}`,buttons:[choice('도감 보기',`도감 ${x.c.name}`),...(tiers[x.c.rarity]>=tiers.LEGEND?[shareChoice()]:[])]},known));
    // 카드는 높은 등급부터 20장까지(카카오 2묶음), 나머지는 아래 말풍선에 수로만
    const hidden=items.length>20?`\n(그 밖에 ${items.length-20}종은 위 목록 참고)`:'';
    const cards={cards:{intro:mask(this.content,head,known),items:items.slice(0,20),outro:mask(this.content,outro+hidden,known),keepIntro:Boolean(drama)&&tiers[top.c.rarity]>=tiers.LEGEND}};
    return {text:mask(this.content,body,known),...cards,...(firstNew?{imageId:this.image(firstNew.c.id,known)}:{}),choices:next.choices};
  }
  private probabilityText(){
    const g=this.content.economy.gachas;
    return (['LOW','MID','HIGH'] as GachaKind[]).map(k=>`${g[k].label} 뽑기 (${g[k].cost}장): ${Object.entries(g[k].weights).map(([t,w])=>`${rarityNames[t]} ${w}%`).join(' · ')}${g[k].pity?`\n  🎯 천장: ${g[k].pity!.count}회 안에 ${rarityNames[g[k].pity!.tier]} 확정`:''}`).join('\n');
  }

  // ───────── 조합 ─────────
  private async chooseMaterials(tx:Tx,p:Player,recipe:Recipe,known:Set<string>,times=1){
    const inventory=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:'AVAILABLE',locked:false,characterId:{in:recipe.materials}},orderBy:{id:'asc'}});
    const selected:number[]=[];
    for(const id of Array.from({length:times},()=>recipe.materials).flat()){
      const owned=inventory.find(c=>c.characterId===id&&!selected.includes(c.id));
      if(!owned)throw new GameError(`${this.name(id,known)}이(가) 부족해요.\n(보호 중이거나 탐험 중인 갈매미는 재료로 쓰지 않아요)`);
      selected.push(owned.id);
    }
    return selected;
  }
  // 지금 재료로 몇 번 만들 수 있는지(최대 50)
  private maxCraft(recipe:Recipe,stock:Stock){
    return Math.min(50,...[...materialCounts(recipe.materials)].map(([id,n])=>Math.floor(statusOf(stock.get(id)).usable/n)));
  }
  // 조합 실행(확인 카드·바로 조합 공통). ids는 chooseMaterials로 고른 재료 전체
  private async craft(tx:Tx,p:Player,recipe:Recipe,ids:number[],times:number,now:Date,known:Set<string>,token?:string):Promise<GameReply>{
    const selected=await this.chooseMaterials(tx,p,recipe,known,times);
    if(ids.length!==selected.length||ids.some((id,i)=>id!==selected[i]))throw new GameError('재료 상태가 바뀌었어요. 다시 해 주세요.');
    const inherited=await tx.ownedCharacter.findMany({where:{id:{in:ids},playerId:p.id,reincarnation:{gt:0}}});
    if(inherited.length)await requireProgressionIdle(tx,p.id,now);
    const lineageByOutput=Array.from({length:times},(_,i)=>inherited.filter(u=>ids.slice(i*recipe.materials.length,(i+1)*recipe.materials.length).includes(u.id)));
    if(lineageByOutput.some(us=>us.length>1))throw new GameError('한 조합에는 환생 계보를 한 마리만 넣을 수 있어요. 계승 능력을 보호하기 위해 취소했어요.');
    const update=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',locked:false},data:{status:'CONSUMED',consumedAt:now}});
    if(update.count!==ids.length)throw new GameError('재료가 겹쳤어요. 다시 해 주세요.');
    const c=this.content.map.get(recipe.resultId)!;
    const receipts:Receipt[]=[];
    for(let i=0;i<times;i++){const result=await this.grant(tx,p,recipe.resultId,'COMBINATION',now);const parent=lineageByOutput[i][0];if(parent)await tx.ownedCharacter.update({where:{id:result.id},data:{reincarnation:parent.reincarnation,inheritedFrom:parent.inheritedFrom,statBonusPct:parent.statBonusPct}});receipts.push(result.receipt);}
    const after=new Set([...known,...(c.rarity==='HIDDEN'?[c.id]:[])]);
    const first=receipts.find(r=>r.first);
    const discovered=c.rarity==='HIDDEN'&&first?'🔓 숨은 조합을 발견했어요!\n\n':'';
    const tutorial=await this.tutorialStep(tx,p,2);
    const missions=[...await this.progress(tx,p,'CRAFT',times,now,c.rarity),...tutorial];
    const next=await this.nextStep(tx,p,now,c.synergy?[choice('탐험 보내기','탐험')]:[]);
    await this.audit(tx,p,'CRAFT',{token,recipe:recipe.resultId,times,consumed:ids});
    const again=this.maxCraft(recipe,await this.stock(tx,p));
    const more=again?[choice(times>1?`${again}개 더`:'한 번 더',`조합 ${c.name}${times>1?` ${again}`:''}`)]:[];
    if(times===1){
      const receipt=receipts[0];
      return {text:`${discovered}${acquisitionText(this.content,c,'COMBINATION',receipt,after)}\n\n${[...missions,next.line].join('\n')}`,...(receipt.first?{imageId:this.image(c.id,after)}:{}),
        // 전설 이상(전설·히든·최상위)은 카톡 공유 버튼
        ...this.acquired(c,'COMBINATION',receipt,after,missions,next.line,discovered.trim(),[...(tiers[c.rarity]>=tiers.LEGEND?[shareChoice()]:[]),...(c.synergy?[choice('탐험 보내기','탐험')]:[])]),
        choices:[...more,...next.choices]};
    }
    const snacks=receipts.reduce((a,r)=>a+r.snacks,0);
    const achieved=receipts.filter(r=>r.achievement).map(r=>achievementText(r.achievement!));
    const head=`🧩 ${emblems[c.rarity]} ${c.name} ×${times} 조합 완료!`;
    const below=[...(snacks?[`🍤 새우gang +${num(snacks)}`]:[]),...achieved,...missions].join('\n');
    const outro=report(below,next.line);
    return {text:mask(this.content,`${discovered}${head}\n\n${outro}`,after),
      cards:{intro:mask(this.content,`${discovered}${head}`,after)||undefined,items:[this.maskCard({imageId:this.cardImage(c.id,after),title:`${emblems[c.rarity]} ${c.name} ×${times}`,
        description:`${rarityNames[c.rarity]}${first?' · 🆕 새 발견!':''} · 보유 ${receipts.at(-1)!.count}마리`,buttons:[choice('도감 보기',`도감 ${c.name}`),...(tiers[c.rarity]>=tiers.LEGEND?[shareChoice()]:[])]},after)],outro:mask(this.content,outro,after)},
      choices:[...more,...next.choices]};
  }
  private canCraft(recipe:Recipe,stock:Stock){
    return [...materialCounts(recipe.materials)].every(([id,n])=>statusOf(stock.get(id)).usable>=n);
  }
  private source(id:string){
    const c=this.content.map.get(id)!;
    const g=this.content.economy.gachas;
    // 실제로 얻을 수 있는 곳만: 뽑기(noGacha 제외), 교환·탐험(흔함·안흔함), 조합(조합식 있는 것), 땅콩떼기(특수함)
    const from=c.noGacha?[]:(['LOW','MID','HIGH'] as GachaKind[]).filter(k=>g[k].weights[c.rarity]).map(k=>g[k].label);
    const low=c.rarity==='COMMON'||c.rarity==='UNCOMMON';
    return [...(from.length?[`${from.join('·')} 뽑기`]:[]),...(low?['교환']:[]),...(c.rarity==='SPECIAL'?['땅콩 교환']:[]),...(this.content.recipes.some(r=>r.resultId===id)?['조합']:[]),
      ...(c.rarity==='NEUTRAL'?['땅콩떼기']:[]),...(low?[`탐험(${c.rarity==='COMMON'?'바다':'벚꽃'} 시너지)`]:[])].join(' / ');
  }
  private recipeParts(recipe:Recipe,stock:Stock,known:Set<string>,collected?:boolean){
    const result=this.content.map.get(recipe.resultId)!;
    const lines=[...materialCounts(recipe.materials)].map(([id,n])=>{
      const s=statusOf(stock.get(id));
      const m=this.content.map.get(id)!;
      const held=[s.locked?`보호 ${s.locked}`:'',s.away?`탐험 ${s.away}`:''].filter(Boolean).join('·');
      return `${s.usable>=n?'✅':'⬜'} ${emblems[m.rarity]} ${this.name(id,known)} ${Math.min(s.usable,n)}/${n}${s.usable<n?` · ${this.source(id)}`:''}${held?` (${held})`:''}`;
    });
    const ready=this.canCraft(recipe,stock);
    const missing=[...materialCounts(recipe.materials)].filter(([id,n])=>statusOf(stock.get(id)).usable<n).length;
    const flavor=collected===false?'🆕 처음 만드는 갈매미예요!':'';
    const status=ready?'✨ 재료가 모두 모였어요!':`재료 ${missing}종이 더 필요해요.`;
    const m=(t:string)=>mask(this.content,t,known);
    return {ready,body:m(lines.join('\n')),
      card:{imageId:this.face(result.id,known),title:m(`🧩 ${emblems[result.rarity]} ${result.name}`),description:m(`${rarityNames[result.rarity]}${flavor?` · ${flavor}`:''}\n${status}`)} as CardEntry,
      text:m(`🧩 ${emblems[result.rarity]} ${result.name} (${rarityNames[result.rarity]})${flavor?`\n${flavor}`:''}\n\n${lines.join('\n')}\n\n${status}`)};
  }
  private recipeText(recipe:Recipe,stock:Stock,known:Set<string>,collected?:boolean){return this.recipeParts(recipe,stock,known,collected).text;}
  private visibleRecipes(known:Set<string>){
    return this.content.recipes.filter(r=>!r.hidden||known.has(r.resultId));
  }
  private parseUnits(query:string){
    return query.split(/[,，+\n]/u).map(s=>s.trim()).filter(Boolean).map(token=>{
      const m=token.match(/^(.*?)\s*(?:[x×*]\s*(\d+)|(\d+)\s*마리)$/u);
      const name=(m?m[1]:token).trim();
      const count=m?Number(m[2]??m[3]):1;
      if(!name||!Number.isSafeInteger(count)||count<1||count>10)throw new GameError('수량은 1~10이에요. 예: 갈발 ×3');
      return {name,count};
    });
  }
  private async combine(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const known=await this.known(tx,p);
    // 재료로 조합(예전 이름 '합치기'). 카드로 고르기: "조합 고르기" → 첫 재료, "조합 A 다음" → 다음 재료,
    // "이대로 섞기" 카드가 "조합 A, B"를 보냄. 고르기 단계: "조합 A, B 다음"(예전 "+"도 허용) [쪽 N]
    const step=query.match(/^(?:(.+?)\s*(?:\+|다음))?\s*(?:쪽\s*(\d+))?$/u);
    if(step&&(step[1]||!query||step[2]))return this.combinePicker(tx,p,known,step[1]?.trim(),Number(step[2]??1));
    const materials:string[]=[];
    const units=this.parseUnits(query);
    const rebuild=(command:string,i:number,c:Character)=>`${command} ${units.map((u,j)=>`${j===i?c.name:u.name}${u.count>1?` ×${u.count}`:''}`).join(', ')}`;
    for(const [i,{name,count}] of units.entries()){
      const c=this.resolve(name,this.revealed(known),'조합',known,c=>rebuild('조합',i,c));
      if(!('id' in c))return c;
      for(let i=0;i<count;i++)materials.push(c.id);
    }
    if(materials.length<2)throw new GameError('재료는 2마리 이상, 쉼표(,)로 구분해 주세요.');
    // 재료를 가졌는지 먼저 봅니다. 조합식이 있는지부터 보면 재료 없이 이름만 넣어 히든 정답을 알아낼 수 있어요
    const owned=await this.stock(tx,p);
    const short=[...materialCounts(materials)].find(([id,n])=>statusOf(owned.get(id)).usable<n);
    if(short)throw new GameError(`${this.name(short[0],known)}이(가) 부족해요.\n가진 갈매미로만 조합할 수 있어요. (보호·탐험 중인 갈매미는 빼고 세요)`);
    const lesson=await this.tutorialStep(tx,p,GameService.tutorialSteps.findIndex(t=>t.go.message==='조합 고르기')+1);
    const withLesson=(r:GameReply):GameReply=>lesson.length?{...r,text:`${lesson[0]}\n\n${r.text}`,...(r.cards?{cards:{...r.cards,intro:[lesson[0],r.cards.intro].filter(Boolean).join('\n\n')}}:{})}:r;
    const key=[...materials].sort().join('+');
    const recipe=this.content.recipes.find(r=>[...r.materials].sort().join('+')===key);
    if(!recipe)return withLesson(this.info({text:`🔮 …아무 일도 일어나지 않았다.\n${materials.map(id=>this.name(id,known)).join(' + ')}\n(이 조합으로는 아무것도 안 나와요)`,choices:[choice('다시 고르기','조합 고르기'),choice('히든 힌트','조합목록 히든'),choice('조합목록')]},'s-mix'));
    const ids=await this.chooseMaterials(tx,p,recipe,known);
    const result=this.content.map.get(recipe.resultId)!;
    if(!isRevealed(result,known))
      return withLesson(await this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,`🔮 ${materials.map(id=>this.name(id,known)).join(' + ')}\n\n재료에서 낯선 기운이 느껴진다…\n결과: ${HIDDEN_NAME}\n만들어 보면 정체가 드러나요.`,undefined,`재료 ${materials.length}마리를 써요`,
        {items:[{imageId:'unknown',title:`🔮 ${HIDDEN_NAME}`,description:'재료에서 낯선 기운이 느껴진다…\n만들어 보면 정체가 드러나요.'}],outro:`🔮 ${materials.map(id=>this.name(id,known)).join(' + ')}`}));
    const seen=Boolean(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:result.id}}}));
    const parts=this.recipeParts(recipe,await this.stock(tx,p),known,seen);
    return withLesson(await this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,parts.text,this.seenImage(result.id,known),`재료 ${ids.length}마리를 써요`,{items:[parts.card],outro:parts.body}));
  }

  // 카드로 재료 고르기. chosenText = "A, B ×2" 처럼 지금까지 고른 재료(없으면 처음)
  private async combinePicker(tx:Tx,p:Player,known:Set<string>,chosenText:string|undefined,pageNo:number):Promise<GameReply>{
    const stock=await this.stock(tx,p);
    const chosen:Character[]=[];
    const units=chosenText?this.parseUnits(chosenText):[];
    for(const [i,{name,count}] of units.entries()){
      const c=this.resolve(name,this.revealed(known),'조합',known,c=>`조합 ${units.map((u,j)=>`${j===i?c.name:u.name}${u.count>1?` ×${u.count}`:''}`).join(', ')} 다음`);
      if(!('id' in c))return c;
      for(let k=0;k<count;k++)chosen.push(c);
    }
    if(chosen.length>=6)throw new GameError('재료는 6마리까지 고를 수 있어요.');
    const used=materialCounts(chosen.map(c=>c.id));
    const label=(list:Character[])=>[...materialCounts(list.map(c=>c.id))].map(([id,n])=>`${this.name(id,known)}${n>1?` ×${n}`:''}`).join(', ');
    // 쓸 수 있는 보유 유닛, 높은 등급부터. 이미 고른 만큼은 빼고 셉니다
    const left=(c:Character)=>statusOf(stock.get(c.id)).usable-(used.get(c.id)??0);
    const pool=this.content.characters.filter(c=>isRevealed(c,known)&&left(c)>0).sort((a,b)=>tiers[b.rarity]-tiers[a.rarity]||a.position-b.position);
    const guide='❔ 숨은 조합 힌트는 "조합목록 히든"에서 볼 수 있어요.\n직접 입력도 돼요: 조합 이름, 이름';
    if(!pool.length)return this.info({text:`🔮 재료로 조합\n${chosen.length?'더 고를 갈매미가 없어요.':'재료로 쓸 갈매미가 없어요. 뽑기부터!'}`,choices:[...(chosen.length>=2?[choice('이대로 섞기',`조합 ${label(chosen)}`)]:[]),choice('뽑기'),choice('히든 힌트','조합목록 히든')]},'s-mix');
    // 카드마다 버튼 1개. 재료가 2개 이상이면 맨 앞에 '이대로 섞기' 카드.
    // 한 쪽 카드 수는 카카오 글자 예산 안에 드는 만큼(재료가 많을수록 버튼 명령이 길어져 줄어듦)
    const mixable=chosen.length>=2;
    const base=chosen.length?`조합 ${label(chosen)} 다음`:'조합 고르기';
    const all:CardEntry[]=pool.map(c=>{
      const next=[...chosen,c];
      return {imageId:this.cardImage(c.id,known),title:`${emblems[c.rarity]} ${c.name}`,description:`${rarityNames[c.rarity]} · 쓸 수 있는 ${left(c)}마리`,
        buttons:[!chosen.length?choice('첫 재료로',`조합 ${c.name} 다음`):next.length<6?choice('재료로 고르기',`조합 ${label(next)} 다음`):choice('6마리로 섞기',`조합 ${label(next)}`)]};
    });
    const mixCard:CardEntry={imageId:'s-mix',title:'🔮 이대로 섞기',description:`${label(chosen)}\n카드를 넘겨 재료를 더 고를 수도 있어요 (최대 6)`,buttons:[choice('섞기',`조합 ${label(chosen)}`)]};
    const size=cardsPerPage(all,mixable?9:10,mixable?cardCost(mixCard):0);
    const {page,pages,start}=this.page(String(pageNo),pool.length,size);
    const items=all.slice(start,start+size);
    if(mixable)items.unshift(mixCard);
    const head=chosen.length?`🔮 고른 재료: ${label(chosen)}\n${chosen.length+1}번째 재료를 고르세요 (${page}/${pages})`:`🔮 재료로 조합 · 첫 재료를 고르세요 (${page}/${pages})\n조합목록에 없는 숨은 조합도 이렇게 찾아요.`;
    const nav=[...(page>1?[choice('이전',`${base} 쪽 ${page-1}`)]:[]),...(page<pages?[choice('다음 쪽',`${base} 쪽 ${page+1}`)]:[])];
    return {text:mask(this.content,`${head}\n\n${items.map(i=>i.title).join('\n')}\n\n${guide}`,known),cards:{intro:mask(this.content,head,known),items:items.map(i=>this.maskCard(i,known)),outro:guide},
      choices:[...nav,...(chosen.length>=2?[choice('이대로 섞기',`조합 ${label(chosen)}`)]:[]),...(chosen.length?[choice('처음부터','조합 고르기')]:[]),choice('히든 힌트','조합목록 히든'),choice('조합목록')]};
  }

  // 이름에 '갈매미맨'이 들어간 유닛만 땅콩떼기 가능(갈매미걸·중성 제외)
  private peanutable(c:Character){return c.name.includes('갈매미맨');}
  private async peanut(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const known=await this.known(tx,p),stock=await this.stock(tx,p);
    if(!query){
      // 낮은 등급부터 보여 줍니다(높은 등급을 실수로 떼지 않도록)
      const pool=this.content.characters.filter(c=>this.peanutable(c)&&isRevealed(c,known)&&statusOf(stock.get(c.id)).usable>0)
        .sort((a,b)=>tiers[a.rarity]-tiers[b.rarity]||a.position-b.position).slice(0,10);
      const guide='🥜 갈매미맨 1마리 → 중성 갈매미 1마리 + 땅콩 1개\n땅콩은 🥜 땅콩 상점에서 써요(뽑기권·특별 골라 받기·닉네임 장식).';
      if(!pool.length)return this.info({text:`🥜 땅콩떼기\n땅콩을 뗄 갈매미맨이 없어요.\n\n${guide}`,choices:[choice('뽑기'),...menu]},'s-peanut');
      return {text:`🥜 땅콩떼기 · 갈매미맨을 고르세요\n\n${pool.map(c=>`${emblems[c.rarity]} ${c.name}`).join('\n')}\n\n${guide}`,
        cards:{intro:`🥜 땅콩떼기 · 갈매미맨을 고르세요 (지금 🥜 ${p.peanut}개)`,items:pool.map(c=>({imageId:this.cardImage(c.id,known),title:`${emblems[c.rarity]} ${c.name}`,description:`${rarityNames[c.rarity]} · 쓸 수 있는 ${statusOf(stock.get(c.id)).usable}마리`,buttons:[choice('땅콩 떼기',`땅콩떼기 ${c.name}`)]})),outro:guide},
        choices:[choice('내갈매미'),...menu]};
    }
    const c=this.resolve(query,this.revealed(known,x=>this.peanutable(x)),'땅콩떼기',known);
    if(!('id' in c))return c;
    if(!statusOf(stock.get(c.id)).usable)throw new GameError(`${c.name}이(가) 없어요.\n(보호 중이거나 탐험 중인 갈매미는 안 돼요)`);
    const neutral=this.content.characters.find(x=>x.rarity==='NEUTRAL')!;
    const warn=tiers[c.rarity]>=tiers.LEGEND?`\n⚠ ${rarityNames[c.rarity]} 등급이에요. 정말 뗄까요?`:'';
    return this.issue(tx,p,'PEANUT',{characterId:c.id},now,`🥜 ${c.name}의 땅콩을 뗄까요?\n→ ${emblems[neutral.rarity]} ${neutral.name} 1마리 + 🥜 땅콩 1개${warn}`,undefined,'되돌릴 수 없어요',
      {items:[{imageId:this.cardImage(c.id,known),title:`🥜 ${c.name}`,description:`→ ${emblems[neutral.rarity]} ${neutral.name} + 🥜 1개${warn}`}]});
  }
  private static tutorialSteps=tutorialSteps;
  // 튜토리얼이 남았는지(다음 단계가 닉네임인데 이미 닉네임이 있으면 남은 것으로 보지 않음)
  private tutorialLeft(a:{tutorial:number;nickname:string|null}){
    const next=GameService.tutorialSteps[a.tutorial];
    return Boolean(next)&&!(next.go.message==='닉네임'&&a.nickname);
  }
  // 지금 뽑기권으로 할 수 있는 횟수(50·10·5·1 중 큰 것)의 뽑기 버튼
  private drawChoices(p:{credits:number;isAdmin:boolean;adminMode:boolean},credits=p.credits){
    const g=this.content.economy.gachas;
    return (['LOW','MID','HIGH'] as GachaKind[]).map(k=>{const n=unlimited(p)?50:[50,10,5,1].find(t=>t*g[k].cost<=credits);return n?choice(`${g[k].label} ×${n}`,`${g[k].label}뽑기${n>1?` ${n}`:''}`):undefined;}).filter((c):c is Choice=>Boolean(c));
  }
  private static tutorialDone=GameService.tutorialSteps.length;
  private event(now:Date){
    return this.content.economy.events.find(e=>new Date(e.from)<=now&&now<new Date(e.to));
  }
  private async tutorialStep(tx:Tx,p:Player,step:number):Promise<string[]>{
    const done=await tx.player.updateMany({where:{id:p.id,tutorial:step-1},data:{tutorial:step,credits:{increment:this.content.economy.tutorial.tickets}}});
    if(!done.count)return [];
    await this.audit(tx,p,'TUTORIAL',{step,tickets:this.content.economy.tutorial.tickets});
    const thanks=step===GameService.tutorialDone&&await this.payInviter(tx,p,this.clock())?`\n💌 초대해 준 친구에게도 뽑기권 ${this.content.economy.invite.tickets}장이 갔어요!`:'';
    const next=GameService.tutorialSteps[step];
    const line=`📘 튜토리얼 ${step}/${GameService.tutorialDone} 완료! 🎟 +${this.content.economy.tutorial.tickets}`;
    // 닉네임을 이미 정해 둔 사람은 닉네임 단계까지 바로 완료
    if(next?.go.message==='닉네임'&&(await tx.player.findUniqueOrThrow({where:{id:p.id},select:{nickname:true}})).nickname)return [`${line}\n${(await this.tutorialStep(tx,p,step+1))[0]}`];
    return [`${line}${next?`\n다음: ${next.title} (${next.how})`:`\n🎉 튜토리얼 끝! 이제 자유롭게 모아 보세요.${thanks}`}`];
  }
  private async tutorialView(tx:Tx,p:Player):Promise<GameReply>{
    const N=GameService.tutorialDone;
    // 다음 단계가 닉네임인데 이미 닉네임이 있으면(닉네임 단계가 생기기 전에 정한 사람) 여기서 완료 처리
    const before=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{tutorial:true,nickname:true}});
    const late=GameService.tutorialSteps[before.tutorial]?.go.message==='닉네임'&&before.nickname?await this.tutorialStep(tx,p,before.tutorial+1):[];
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{tutorial:true}});
    const t=this.content.economy.tutorial.tickets;
    const items:CardEntry[]=GameService.tutorialSteps.map((s,i)=>({imageId:/^[A-Z]\d/.test(s.image)?this.cardImage(s.image,new Set()):s.image,
      title:`${account.tutorial>i?'✅':account.tutorial===i?'👉':'⬜'} ${i+1}. ${s.title}`,description:`${s.how} · 🎟 ${t}`,...(account.tutorial===i?{buttons:[s.go]}:{})}));
    const head=`${late.length?`${late[0]}\n\n`:''}${account.tutorial>=N?'📘 튜토리얼 완료! 🎉':`📘 튜토리얼 ${account.tutorial}/${N} · 단계마다 🎟 ${t}`}`;
    const cover:CardEntry={imageId:'s-tutorial',title:account.tutorial>=N?'🎉 튜토리얼 완료!':'📘 튜토리얼',description:account.tutorial>=N?'이제 자유롭게 모아 보세요.':`${account.tutorial}/${N} 단계 · 단계마다 🎟 ${t}`};
    return {text:`${head}\n\n${items.map(i=>`${i.title} · ${i.description}`).join('\n')}`,cards:{...(late.length?{intro:late[0]}:{}),items:[cover,...items]},choices:[...(account.tutorial<N?[GameService.tutorialSteps[account.tutorial].go]:[]),...menu]};
  }
  // ───────── 팔기 · 초대 ─────────
  private sellPrice(c:Character){return this.content.economy.sell[c.rarity]??0;}
  // 팔 수 있는 수: 보호·탐험이 아닌 것 중, 각 유닛 1마리는 남김
  private sellable(c:Character,stock:Stock){const s=statusOf(stock.get(c.id));return this.sellPrice(c)?Math.max(0,Math.min(s.usable,s.total-1)):0;}
  private async sell(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const known=await this.known(tx,p),stock=await this.stock(tx,p);
    const pool=this.content.characters.filter(c=>isRevealed(c,known)&&this.sellable(c,stock)>0);
    const gradeArg=tierOrder.find(t=>rarityNames[t]===query.replace(/\s*전부$/u,'').trim());
    if(!query||gradeArg){
      const list=(gradeArg?pool.filter(c=>c.rarity===gradeArg):pool).sort((a,b)=>tiers[a.rarity]-tiers[b.rarity]||a.position-b.position);
      if(gradeArg&&/전부$/u.test(query)){
        if(!list.length)throw new GameError(`팔 수 있는 ${rarityNames[gradeArg]}이(가) 없어요.`);
        return this.sellIssue(tx,p,list.map(c=>[c,this.sellable(c,stock)] as [Character,number]),known,now);
      }
      const guide='갈매미마다 1마리는 남기고, 🔒 보호·🧭 탐험 중인 갈매미는 팔지 않아요.\n최상위(제한~불멸)는 팔 수 없어요.';
      if(!list.length)return this.info({text:`🍤 팔기\n남는 갈매미가 없어요.\n\n${guide}`,choices:[choice('내갈매미'),choice('상점'),...menu]},'s-sell');
      const grades=[...new Set(pool.map(c=>c.rarity))];
      const all:CardEntry[]=list.map(c=>{const n=this.sellable(c,stock);return {imageId:this.cardImage(c.id,known),title:`${emblems[c.rarity]} ${c.name}`,
        description:`팔 수 있는 ${n}마리 · 마리당 🍤 ${this.sellPrice(c)}`,buttons:[choice('1마리 팔기',`팔기 ${c.name} 1`),...(n>1?[choice(`${n}마리 팔기`,`팔기 ${c.name} ${n}`)]:[])]};});
      // 한 화면 카드 수는 카카오 글자 예산 안에서(나머지는 등급별 보기·전부 팔기로)
      const items=all.slice(0,cardsPerPage(all));
      const head=`🍤 팔기 · 남는 갈매미 ${list.length}종${list.length>items.length?` (앞 ${items.length}종)`:''}`;
      return {text:`${head}\n\n${items.map(i=>`${i.title} · ${i.description}`).join('\n')}\n\n${guide}`,cards:{intro:head,items,outro:guide},
        choices:[...grades.slice(0,5).map(t=>choice(`${rarityNames[t]} 전부 팔기`,`팔기 ${rarityNames[t]} 전부`)),choice('상점'),choice('내갈매미')]};
    }
    const m=query.match(/^(.+?)\s+(\d+|전부)$/u);
    const c=this.resolve(m?m[1]:query,this.revealed(known),'팔기',known);
    if(!('id' in c))return c;
    if(!this.sellPrice(c))throw new GameError('최상위 갈매미는 팔 수 없어요.');
    const max=this.sellable(c,stock);
    if(!max)throw new GameError(`팔 수 있는 ${c.name}이(가) 없어요.\n(1마리는 남기고, 보호·탐험 중인 갈매미는 팔지 않아요)`);
    const n=!m||m[2]==='전부'?(m?max:1):Number(m[2]);
    if(n<1||n>max)throw new GameError(`${c.name}은(는) ${max}마리까지 팔 수 있어요.`);
    return this.sellIssue(tx,p,[[c,n]],known,now);
  }
  private async sellIssue(tx:Tx,p:Player,lines:[Character,number][],known:Set<string>,now:Date){
    const ids:number[]=[];
    for(const [c,n] of lines){
      const units=await tx.ownedCharacter.findMany({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:false},orderBy:{id:'desc'},take:n});
      ids.push(...units.map(u=>u.id));
    }
    const total=lines.reduce((a,[c,n])=>a+this.sellPrice(c)*n,0);
    const summary=lines.map(([c,n])=>`${emblems[c.rarity]} ${c.name} ×${n}`).join('\n');
    const first=lines[0][0];
    return this.issue(tx,p,'SELL',{ids},now,`🍤 팔기\n${summary}\n→ 새우gang +${total}`,undefined,`${ids.length}마리 → 🍤 +${total}`,
      {items:[{imageId:this.cardImage(first.id,known),title:`🍤 팔기 → +${total}`,description:lines.length>3?`${lines.length}종 ${ids.length}마리`:summary}]});
  }
  private async invite(tx:Tx,p:Player,code:string,now:Date):Promise<GameReply>{
    const e=this.content.economy.invite;
    if(!code){
      let mine=p.inviteCode;
      for(let i=0;!mine&&i<5;i++){
        const next=randomBytes(4).toString('hex').slice(0,6).toUpperCase();
        if(!(await tx.player.findUnique({where:{inviteCode:next}}))){await tx.player.update({where:{id:p.id},data:{inviteCode:next}});mine=next;}
      }
      const friends=await tx.player.findMany({where:{invitedBy:p.id},select:{id:true}});
      const waiting=friends.length?await tx.achievement.count({where:{playerId:{in:friends.map(f=>f.id)},key:'INVITE_PENDING'}}):0;
      return this.info({text:`💌 내 초대 코드: ${mine}\n친구가 "초대 ${mine}"라고 보내면 친구는 바로 🎟 ${e.tickets}\n친구가 튜토리얼을 끝내면 나도 🎟 ${e.tickets}\n\n초대한 친구 ${friends.length}/${e.max}명${waiting?` (튜토리얼 진행 중 ${waiting}명)`:''}\n친구는 가입 후 ${e.days}일 안에 입력해야 해요.`,choices:[choice('내정보'),...menu]},'s-invite');
    }
    const target=code.toUpperCase();
    if(p.invitedBy)throw new GameError('이미 초대 코드를 입력했어요.');
    if(now.getTime()-p.createdAt.getTime()>e.days*86_400_000)throw new GameError(`초대 코드는 가입 후 ${e.days}일 안에만 입력할 수 있어요.`);
    const inviter=await tx.player.findUnique({where:{inviteCode:target}});
    if(!inviter)throw new GameError('그런 초대 코드는 없어요. 다시 확인해 주세요.');
    if(inviter.id===p.id)throw new GameError('내 코드는 입력할 수 없어요.');
    if(await tx.player.count({where:{invitedBy:inviter.id}})>=e.max)throw new GameError('이 코드는 초대 인원이 다 찼어요.');
    await tx.player.update({where:{id:p.id},data:{invitedBy:inviter.id,credits:{increment:e.tickets}}});
    await this.audit(tx,p,'INVITE',{inviter:inviter.id,tickets:e.tickets});
    // 초대한 사람 보상은 친구가 튜토리얼을 끝낼 때(부계정으로 코드만 넣는 악용 방지). 이미 끝냈으면 바로
    await tx.achievement.create({data:{playerId:p.id,key:'INVITE_PENDING',createdAt:now}});
    const paid=p.tutorial>=GameService.tutorialDone?await this.payInviter(tx,p,now):false;
    return this.info({text:`💌 초대 완료!\n🎟 +${e.tickets} · 지금 ${p.credits+e.tickets}장\n${paid?`초대한 친구도 🎟 +${e.tickets}을 받았어요.`:`튜토리얼(${GameService.tutorialDone}단계)을 끝내면 초대한 친구도 🎟 ${e.tickets}을 받아요.`}`,choices:[choice('튜토리얼'),choice('뽑기'),...menu]},'s-invite');
  }
  // 초대받은 친구가 튜토리얼을 끝내면 초대한 사람에게 보상(한 번만)
  private async payInviter(tx:Tx,p:Player,now:Date){
    const pending=await tx.achievement.deleteMany({where:{playerId:p.id,key:'INVITE_PENDING'}});
    const me=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{invitedBy:true}});
    if(!pending.count||!me.invitedBy)return false;
    const inviter=await tx.player.findUnique({where:{id:me.invitedBy}});
    if(!inviter)return false;
    const tickets=this.content.economy.invite.tickets;
    await tx.player.update({where:{id:inviter.id},data:{credits:{increment:tickets}}});
    await tx.achievement.create({data:{playerId:p.id,key:'INVITE_PAID',createdAt:now}});
    await this.audit(tx,inviter,'INVITE_REWARD',{friend:p.id,tickets});
    return true;
  }
  private exchangeCost(c:Character){
    const e=this.content.economy.exchange;
    return c.rarity==='UNCOMMON'?e.uncommonCost:e.snackCost;
  }
  // 교환 값: 흔함·안흔함은 새우gang, 특별은 땅콩(땅콩 상점)
  private exchangePrice(c:Character){return c.rarity==='SPECIAL'?{peanut:this.content.economy.peanutShop.special}:{snack:this.exchangeCost(c)};}
  private priceText(c:Character){const x=this.exchangePrice(c);return x.peanut?`🥜 ${x.peanut}`:`🍤 ${x.snack}`;}
  // 닉네임 장식(땅콩 상점 배지)
  private badgeEmoji(id?:string|null){return this.content.economy.peanutShop.badges.find(b=>b.id===id)?.emoji??'';}
  private named(nick:string|null|undefined,badge?:string|null,fallback=''){const e=this.badgeEmoji(badge);return nick?`${e?`${e} `:''}${nick}`:fallback;}
  // ───────── 상점 (새우gang 사용처) ─────────
  private async shop(tx:Tx,p:Player,sub:string,now:Date):Promise<GameReply>{
    const e=this.content.economy,none=new Set<string>();
    if(sub==='뽑기권'){
      const t=e.shop.tickets;
      if(p.snack<t.snacks)throw new GameError(`🍤 새우gang이 부족해요. ${t.snacks}개 필요, 지금 ${p.snack}개.`);
      return this.issue(tx,p,'SHOP_TICKETS',{},now,`🛒 뽑기권 ${t.amount}장\n새우gang ${t.snacks}개 (지금 ${p.snack}개)`,undefined,`새우gang ${t.snacks}개를 써요`,
        {items:[{imageId:'s-ticket',title:`🛒 뽑기권 ${t.amount}장`,description:`새우gang ${t.snacks}개 · 지금 ${p.snack}개`}]});
    }
    if(sub==='땅콩'){
      const ps=e.peanutShop;
      const items:CardEntry[]=[
        {imageId:'s-ticket',title:`🎟 뽑기권 ${ps.tickets}장`,description:`🥜 1개 · 지금 🥜 ${p.peanut}개`,buttons:[choice('바꾸기','상점 땅콩뽑기권')]},
        {imageId:'s-exchange',title:'✦ 특별 골라 받기',description:`원하는 특별 1마리 · 🥜 ${ps.special}`,buttons:[choice('고르기','교환 특별')]},
        {imageId:'s-rank',title:'🎀 닉네임 장식',description:`랭킹·수다방·프로필에 이름 옆으로 보여요 · 🥜 ${Math.min(...ps.badges.map(b=>b.cost))}~${Math.max(...ps.badges.map(b=>b.cost))}`,buttons:[choice('보기','상점 장식')]}];
      const outro='🥜 땅콩은 땅콩떼기로 얻어요(갈매미맨 1마리 → 중성 갈매미 + 땅콩 1개).';
      return {text:`🥜 땅콩 상점 · 지금 🥜 ${p.peanut}개\n\n${items.map(i=>`${i.title} · ${i.description}`).join('\n')}\n\n${outro}`,cards:{intro:`🥜 땅콩 상점 · 지금 🥜 ${p.peanut}개`,items,outro},choices:[choice('땅콩떼기'),choice('상점'),...menu]};
    }
    if(sub==='땅콩뽑기권'){
      const t=e.peanutShop.tickets;
      if(p.peanut<1)throw new GameError(`🥜 땅콩이 없어요.\n땅콩떼기로 갈매미맨의 땅콩을 떼면 생겨요.`);
      return this.issue(tx,p,'SHOP_PEANUT_TICKETS',{},now,`🥜 → 🎟 뽑기권 ${t}장\n땅콩 1개 (지금 ${p.peanut}개)`,undefined,`땅콩 1개를 써요`,
        {items:[{imageId:'s-ticket',title:`🥜 → 🎟 뽑기권 ${t}장`,description:`땅콩 1개 · 지금 🥜 ${p.peanut}개`}]});
    }
    if(sub==='장식')return this.badgeShop(tx,p);
    if(sub==='탐험귀환'){
      const cost=e.shop.expeditionSkip.snacks,active=await this.activeExpedition(tx,p);
      if(!active||active.endsAt<=now)throw new GameError('바로 돌아오게 할 탐험이 없어요.\n탐험 중일 때만 쓸 수 있어요.');
      if(p.snack<cost)throw new GameError(`🍤 새우gang이 부족해요. ${cost}개 필요, 지금 ${p.snack}개.`);
      return this.issue(tx,p,'SHOP_SKIP',{},now,`🛒 탐험 즉시 귀환\n남은 ${duration(active.endsAt.getTime()-now.getTime())} · 새우gang ${cost}개`,undefined,`새우gang ${cost}개를 써요`,
        {items:[{imageId:this.cardImage((active.unitIds as string[])[0],none),title:'🛒 탐험 즉시 귀환',description:`남은 시간 ${duration(active.endsAt.getTime()-now.getTime())}\n새우gang ${cost}개 · 지금 ${p.snack}개`}]});
    }
    const items:CardEntry[]=[
      {imageId:'s-exchange',title:'🥚 흔함 고르기',description:`원하는 흔함 1마리 · 🍤 ${e.exchange.snackCost}`,buttons:[choice('고르기','교환 흔함')]},
      {imageId:'s-exchange',title:'🔹 안흔함 고르기',description:`원하는 안흔함 1마리 · 🍤 ${e.exchange.uncommonCost}`,buttons:[choice('고르기','교환 안흔함')]},
      {imageId:'s-ticket',title:`🎟 뽑기권 ${e.shop.tickets.amount}장`,description:`🍤 ${e.shop.tickets.snacks}`,buttons:[choice('사기','상점 뽑기권')]},
      {imageId:'s-sell',title:'🍤 남는 갈매미 팔기',description:`흔함 ${e.sell.COMMON??0} · 안흔함 ${e.sell.UNCOMMON??0} · 특별 ${e.sell.SPECIAL??0}…(1마리당)`,buttons:[choice('팔기','팔기')]},
      {imageId:'explore',title:'🧭 탐험 즉시 귀환',description:`진행 중인 탐험을 바로 끝냄 · 🍤 ${e.shop.expeditionSkip.snacks}`,buttons:[choice('사기','상점 탐험귀환')]},
      {imageId:'s-peanut',title:'🥜 땅콩 상점',description:`뽑기권 ${e.peanutShop.tickets}장 · 특별 골라 받기 · 닉네임 장식 · 지금 🥜 ${p.peanut}개`,buttons:[choice('들어가기','상점 땅콩')]}
    ];
    const outro='🍤 새우gang 얻는 법: 처음 발견·중복 획득 보상, 팔기, 탐험 (자세히: 보상)';
    return {text:`🛒 상점 · 🍤 ${p.snack}개 · 🥜 ${p.peanut}개\n\n${items.map(i=>`${i.title} · ${i.description}`).join('\n')}\n\n${outro}`,cards:{intro:`🛒 상점 · 지금 🍤 ${p.snack}개 · 🥜 ${p.peanut}개`,items,outro},choices:[choice('흔함 교환','교환 흔함'),choice('안흔함 교환','교환 안흔함'),choice('🥜 땅콩 상점','상점 땅콩'),choice('뽑기권 사기','상점 뽑기권'),...menu]};
  }
  // 닉네임 장식: 산 것은 '달기', 안 산 것은 '사기'(땅콩)
  private async badgeShop(tx:Tx,p:Player):Promise<GameReply>{
    const owned=new Set((await tx.achievement.findMany({where:{playerId:p.id,key:{startsWith:'BADGE_'}}})).map(a=>a.key.slice(6)));
    const items:CardEntry[]=this.content.economy.peanutShop.badges.map(b=>{const has=owned.has(b.id),on=p.badge===b.id;
      return {imageId:'s-rank',title:`${b.emoji} ${b.label}${on?' · 달고 있음':''}`,description:has?`가지고 있어요 · 예) ${b.emoji} ${p.nickname??'닉네임'}`:`🥜 ${b.cost} · 예) ${b.emoji} ${p.nickname??'닉네임'}`,
        buttons:[has?(on?choice('빼기','장식 빼기'):choice('달기',`장식 달기 ${b.label}`)):choice('사기',`장식 사기 ${b.label}`)]};});
    const head=`🎀 닉네임 장식 · 지금 🥜 ${p.peanut}개`;
    const outro=`${p.nickname?'':'장식은 닉네임 옆에 보여요. 먼저: 닉네임 갈매미왕\n'}한 번 사면 계속 쓸 수 있고, 하나만 달 수 있어요.`;
    return {text:`${head}\n\n${items.map(i=>`${i.title} · ${i.description}`).join('\n')}\n\n${outro}`,cards:{intro:head,items,outro},choices:[choice('땅콩 상점','상점 땅콩'),...menu]};
  }
  // ───────── 목록 ─────────
  private gradeOf(label:string){return tierOrder.find(t=>rarityNames[t]===label);}
  private page(query:string,total:number,size=5){
    const page=Number(query||1),pages=Math.max(1,Math.ceil(total/size));
    if(!Number.isSafeInteger(page)||page<1||page>pages)throw new GameError(`페이지는 1~${pages}까지 있어요.`);
    return {page,pages,start:(page-1)*size};
  }
  private async browse(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const stock=await this.stock(tx,p);
    const entries=await tx.collectionEntry.findMany({where:{playerId:p.id}});
    const collected=new Set(entries.map(e=>e.characterId));
    const known=new Set([...collected].filter(id=>this.content.hiddenIds.includes(id)));
    const recipeMode=command==='조합목록'||command==='조합가능';
    const grades=(recipeMode?tierOrder.filter(t=>t!=='COMMON'&&t!=='NEUTRAL'):tierOrder) as readonly Tier[];
    const [first,second,...extra]=query.trim().split(/\s+/).filter(Boolean);
    let grade:Tier|undefined,pageQuery=query.trim();
    if(first&&!/^\d+$/.test(first)){
      grade=this.gradeOf(first);
      if(!grade||!grades.includes(grade)||extra.length)throw new GameError(`그런 등급은 없어요. 예: ${command} ${recipeMode?'전설':'특별'}`);
      pageQuery=second??'';
    }
    if(command==='조합목록'&&!grade){
      const visible=this.visibleRecipes(known);
      const lines=grades.map(t=>{
        const all=this.content.recipes.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const shown=visible.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const ready=shown.filter(r=>this.canCraft(r,stock)).length;
        const count=t==='HIDDEN'?`발견 ${shown.length}/${all.length}`:`${all.length}종`;
        return `${emblems[t]} ${rarityNames[t]} · ${count}${ready?` · ✨ ${ready}`:''}`;
      });
      const cards:CardEntry[]=grades.map(t=>{
        const all=this.content.recipes.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const shown=visible.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const ready=shown.filter(r=>this.canCraft(r,stock)).length;
        return {imageId:this.gradeImage(t,known),title:`${emblems[t]} ${rarityNames[t]}`,
          description:`${t==='HIDDEN'?`발견 ${shown.length}/${all.length} · 목록에서 힌트 보기`:`${all.length}종`}${ready?`\n✨ 지금 ${ready}종 만들 수 있어요`:''}`,
          buttons:[choice('목록 보기',`${command} ${rarityNames[t]}`),...(ready?[choice(`✨ ${ready}종`,`조합가능 ${rarityNames[t]}`)]:[]),...(t==='HIDDEN'?[choice('재료 고르기','조합 고르기')]:[])]};
      });
      return {text:`🧩 ${command} · 등급을 고르세요\n\n${lines.join('\n')}\n\n✨ = 지금 만들 수 있는 수\n❔ 히든은 재료로 직접 조합해서 찾아요(조합 고르기).`,cards:{intro:`🧩 ${command} · 등급을 골라 보세요`,items:cards},choices:grades.slice(0,9).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`)).concat(choice('합치기'))};
    }
    if(command==='도감'&&!grade){
      const lines=tierOrder.map(t=>{
        const ids=this.content.characters.filter(c=>c.rarity===t).map(c=>c.id);
        const got=ids.filter(id=>collected.has(id)).length;
        return `${emblems[t]} ${rarityNames[t]} · ${got}/${ids.length}${got===ids.length?' 🏆':''}`;
      });
      const cards:CardEntry[]=tierOrder.map(t=>{
        const ids=this.content.characters.filter(c=>c.rarity===t).map(c=>c.id);
        const got=ids.filter(id=>collected.has(id)).length;
        return {imageId:this.gradeImage(t,known),title:`${emblems[t]} ${rarityNames[t]}`,description:`수집 ${got}/${ids.length}${got===ids.length?' · 🏆 완성!':''}`,buttons:[choice('보기',`도감 ${rarityNames[t]}`)]};
      });
      return {text:`📚 도감 · ${collected.size}/${this.content.characters.length}\n\n${lines.join('\n')}\n\n등급을 골라 보세요.`,cards:{intro:`📚 도감 · ${collected.size}/${this.content.characters.length}`,items:cards},choices:tierOrder.map(t=>choice(rarityNames[t],`도감 ${rarityNames[t]}`))};
    }
    let characters:Character[];
    // 재료가 몇 종 모였는지: 만들 수 있는 것과 거의 다 모인 것을 앞에 보여 줍니다.
    const progressOf=(r:Recipe)=>{const need=[...materialCounts(r.materials)];return {have:need.filter(([id,n])=>statusOf(stock.get(id)).usable>=n).length,total:need.length};};
    if(command==='조합가능')characters=this.visibleRecipes(known).filter(r=>this.canCraft(r,stock)).map(r=>this.content.map.get(r.resultId)!)
      // 만들 수 있는 것: 높은 등급부터, 같은 등급이면 처음 만드는 것 먼저
      .sort((a,b)=>tiers[b.rarity]-tiers[a.rarity]||Number(collected.has(a.id))-Number(collected.has(b.id))||a.position-b.position);
    else if(recipeMode)characters=this.visibleRecipes(known)
      .map(r=>({r,pr:progressOf(r)})).sort((a,b)=>Number(this.canCraft(b.r,stock))-Number(this.canCraft(a.r,stock))||b.pr.have/b.pr.total-a.pr.have/a.pr.total)
      .map(({r})=>this.content.map.get(r.resultId)!);
    else if(command==='내갈매미')characters=this.content.characters.filter(c=>stock.has(c.id));
    else characters=this.content.characters;
    if(command==='교환')characters=this.content.characters.filter(c=>c.rarity===(grade==='UNCOMMON'||grade==='SPECIAL'?grade:'COMMON'));
    if(grade)characters=characters.filter(c=>c.rarity===grade);
    // 히든 조합목록: 못 찾은 히든도 ??? 카드와 힌트로 보여 줍니다(찾은 것 먼저)
    if(command==='조합목록'&&grade==='HIDDEN')characters=this.content.characters.filter(c=>c.rarity==='HIDDEN').sort((a,b)=>Number(isRevealed(b,known))-Number(isRevealed(a,known))||a.position-b.position);
    if(!characters.length)return this.info({text:command==='조합가능'?'지금 만들 수 있는 게 없어요.\n조합목록에서 모자란 재료를 확인해 보세요.':grade==='HIDDEN'&&recipeMode?'아직 발견한 히든 조합이 없어요.\n조합 고르기로 재료를 직접 섞어 찾아보세요.':'아직 갈매미가 없어요. 뽑기부터 해 볼까요?',choices:[choice('조합목록'),choice('뽑기'),choice('재료 고르기','조합 고르기'),choice('내갈매미')]},'unknown');
    const gradePool=command==='조합가능'?grades.filter(t=>this.visibleRecipes(known).some(r=>this.content.map.get(r.resultId)!.rarity===t&&this.canCraft(r,stock))):grades;
    const gradeChoices=[...(command==='조합가능'&&grade?[choice('전체','조합가능')]:[]),...gradePool.filter(t=>t!==grade).slice(0,6).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`))];
    const build=(c:Character):CardEntry=>{
      if(!isRevealed(c,known))return {imageId:'unknown',title:HIDDEN_NAME,description:`❔ 힌트: ${c.hint??'재료로 직접 조합해서 찾아요'}`,buttons:[choice('재료 고르기','조합 고르기')]};
      const have=statusOf(stock.get(c.id)),own=collected.has(c.id),title=`${emblems[c.rarity]} ${c.name}`;
      if(recipeMode){
        const recipe=this.content.recipes.find(r=>r.resultId===c.id)!,pr=progressOf(recipe),ok=this.canCraft(recipe,stock);
        const lack=ok?'':[...materialCounts(recipe.materials)].filter(([id,n])=>statusOf(stock.get(id)).usable<n).map(([id])=>this.name(id,known)).slice(0,2).join(', ');
        if(command==='조합가능')return {imageId:this.face(c.id,known),title,
          description:`${rarityNames[c.rarity]} · ${own?`보유 ${have.total}마리`:'🆕 처음 만드는 갈매미!'}`,
          buttons:[choice('지금 만들기',`조합 ${c.name}`),...(this.maxCraft(recipe,stock)>1?[choice(`최대 ${this.maxCraft(recipe,stock)}개`,`조합 ${c.name} 최대`)]:[]),choice('재료 보기',`조합식 ${c.name}`)]};
        return {imageId:this.face(c.id,known),title,description:ok?`${rarityNames[c.rarity]} · ✨ 만들 수 있어요`:`${rarityNames[c.rarity]} · 재료 ${pr.have}/${pr.total}종\n모자람: ${lack}`,
          buttons:ok?[choice('만들기',`조합 ${c.name}`),choice('재료 보기',`조합식 ${c.name}`)]:[choice('재료 보기',`조합식 ${c.name}`)]};
      }
      if(command==='교환')return {imageId:this.face(c.id,known),title,description:`${rarityNames[c.rarity]} · ${this.priceText(c)} · 보유 ${have.total}마리`,buttons:[choice('교환하기',`교환 ${c.name}`)]};
      if(command==='내갈매미')return {imageId:this.cardImage(c.id,known),title,description:`${rarityNames[c.rarity]} · ${have.total}마리${have.away?` · 탐험 ${have.away}`:''}${have.locked?` · 보호 ${have.locked}`:''}`,buttons:[choice('자세히',`내갈매미 ${c.name}`)]};
      return {imageId:this.face(c.id,known),title,description:`${rarityNames[c.rarity]} · ${own?'✅ 수집':'⬜ 미수집'}`,buttons:[choice('자세히',`도감 ${c.name}`)]};
    };
    // 목록은 그림 카드 넘기기. 한 쪽 카드 수는 카카오 글자 예산 안에 드는 만큼(최대 10장)
    const all=characters.map(build);
    const {page,pages,start}=this.page(pageQuery,characters.length,cardsPerPage(all));
    const cards=all.slice(start,start+cardsPerPage(all));
    const base=grade?`${command} ${rarityNames[grade]}`:command;
    const nav=[...(page>1?[choice('이전',`${base} ${page-1}`)]:[]),...(page<pages?[choice('다음 페이지',`${base} ${page+1}`)]:[])];
    const fresh=command==='조합가능'?characters.filter(c=>!collected.has(c.id)).length:0;
    const head=command==='조합가능'?`✨ 지금 만들 수 있는 갈매미 ${characters.length}종${fresh?` · 🆕 처음 ${fresh}종`:''}${grade?` (${rarityNames[grade]})`:''}${pages>1?` · ${page}/${pages}쪽`:''}`:`${command==='조합목록'?'🧩':command==='교환'?'🍤':command==='도감'?'📚':'🐦'} ${base}${pages>1?` · ${page}/${pages}쪽`:''}`;
    const text=mask(this.content,`${head}\n\n${cards.map(c=>`${c.title}\n${c.description}`).join('\n\n')}`,known);
    return {text,cards:{intro:head,items:cards.map(c=>this.maskCard(c,known))},choices:[...nav,...gradeChoices,...menu.slice(0,2)].slice(0,10)};
  }
  private async detail(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const known=await this.known(tx,p);
    const c=this.resolve(query,this.revealed(known),command,known);
    if(!('id' in c))return c;
    const entry=await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}});
    const count=statusOf((await this.stock(tx,p)).get(c.id));
    const held=[count.locked?`🔒 보호 ${count.locked}`:'',count.away?`🧭 탐험 ${count.away}`:''].filter(Boolean).join(' · ');
    const own=entry?`🐦 ${count.total}마리 보유${held?` (${held})`:''} · ${entry.count}번 만남`:`⬜ 아직 못 만났어요\n얻는 곳: ${this.source(c.id)}`;
    const text=mask(this.content,`${emblems[c.rarity]} 【${c.name}】 ${characterInfo(this.content,c)}\n\n${story(c)}\n\n${own}`,known);
    const manage=command==='내갈매미';
    const choices=[
      ...(c.rarity==='COMMON'?[choice('교환',`교환 ${c.name}`)]:[choice('조합식 보기',`조합식 ${c.name}`)]),
      ...(c.synergy&&count.usable+count.locked?[choice('탐험 보내기',`탐험보내기 ${c.name}`)]:[]),
      ...(manage&&count.usable?[choice('전부 보호',`잠금 ${c.name}`)]:[]),
      ...(manage&&count.locked?[choice('보호 해제',`잠금해제 ${c.name}`)]:[]),
      ...(this.peanutable(c)&&count.usable?[choice('땅콩떼기',`땅콩떼기 ${c.name}`)]:[]),
      ...(manage&&this.sellPrice(c)&&count.usable&&count.total>1?[choice('남는 것 팔기',`팔기 ${c.name} 전부`)]:[]),
      ...(manage?[]:[choice('보유 상태',`내갈매미 ${c.name}`)]),
      choice('💬 댓글',`댓글 ${c.name}`),
      ...menu.slice(0,4)
    ].slice(0,10);
    const card=this.maskCard({imageId:entry?this.cardImage(c.id,known):this.face(c.id,known),title:`${emblems[c.rarity]} ${c.name}`,description:`${characterInfo(this.content,c)}\n${own}`,buttons:choices.slice(0,3)},known);
    return {text,imageId:entry?this.image(c.id,known):undefined,cards:{items:[card],outro:mask(this.content,story(c),known)},choices};
  }

  // ───────── 댓글 (카톡에서 쓰고, 웹·카톡에서 봄) ─────────
  // 쓰기: 닉네임 + 그 유닛을 만나 본 사람만. 1분에 1개·하루 30개, 2~100자, 링크·금지어 불가
  private static bannedWords=['시발','씨발','ㅅㅂ','ㅆㅂ','병신','ㅂㅅ','개새','새끼','좆','지랄','미친놈','미친년','닥쳐','꺼져','느금','니애미','섹스','fuck','shit','bitch'];
  private commentProblem(text:string){
    const plain=text.replace(/\s+/g,'').toLowerCase();
    if(text.length<2||text.length>100)return '댓글은 2~100자로 써 주세요.';
    if(/https?:|www\.|\.(com|net|kr|io|me|gg|ly)\b|open\.kakao/i.test(text))return '댓글에는 링크를 넣을 수 없어요.';
    if(GameService.bannedWords.some(w=>plain.includes(w)))return '고운 말로 다시 써 주세요. 🪨';
    return '';
  }
  private commentLine(c:{id:number;text:string;likes:number;createdAt:Date;player:{nickname:string|null;badge:string|null}},known:Set<string>){
    return `#${c.id} ${this.named(c.player.nickname,c.player.badge,'이름 없는 갈매미')} · 👍 ${c.likes} · ${DateTime.fromJSDate(c.createdAt).setZone('Asia/Seoul').toFormat('M/d HH:mm')}\n${mask(this.content,c.text,known)}`;
  }
  private async commentCommand(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const known=await this.known(tx,p);
    if(!query)return this.info({text:'💬 갈매미 댓글\n만나 본 갈매미에게 댓글을 남겨요.\n쓰기: 댓글 금갑 너무 귀여워요\n보기: 댓글 금갑\n좋아요: 좋아요 번호 · 지우기: 댓글삭제 번호\n\n남긴 댓글은 홈페이지 도감에도 올라가요.',choices:[choice('도감'),choice('닉네임'),...menu]},'s-info');
    // "댓글 이름 내용": 이름은 앞에서부터 가장 길게 맞는 유닛
    const words=query.split(/\s+/);
    let unit:Character|undefined,rest='';
    for(let k=words.length;k>=1&&!unit;k--){
      const name=words.slice(0,k).join(' ');
      const exact=this.revealed(known).find(c=>c.name===name)??this.revealed(known).find(c=>c.name.replace(/\s+/g,'')===name.replace(/\s+/g,''));
      if(exact){unit=exact;rest=words.slice(k).join(' ');}
    }
    if(!unit){
      const found=this.resolve(words[0],this.revealed(known),'댓글',known);
      if(!('id' in found))return found;
      unit=found;rest=words.slice(1).join(' ');
    }
    const c=unit;
    if(!rest){
      const shown=await tx.comment.findMany({where:{characterId:c.id,hidden:false},orderBy:[{likes:'desc'},{createdAt:'desc'}],take:5,include:{player:{select:{nickname:true,badge:true}}}});
      const total=await tx.comment.count({where:{characterId:c.id,hidden:false}});
      const head=`💬 ${emblems[c.rarity]} ${c.name} 댓글 ${total}개${total>5?' (좋아요 많은 5개)':''}`;
      const body=shown.length?shown.map(x=>this.commentLine(x,known)).join('\n\n'):'아직 댓글이 없어요. 첫 댓글을 남겨 보세요!';
      return {text:`${head}\n\n${body}\n\n쓰기: 댓글 ${c.name} 내용`,cards:{intro:head,items:[{imageId:this.face(c.id,known),title:`💬 ${c.name}`,description:`댓글 ${total}개\n쓰기: 댓글 ${c.name} 내용`}],outro:`${body}\n\n좋아요: 아래 👍 버튼`},
        choices:[...shown.map(x=>choice(`👍 #${x.id}`,`좋아요 ${x.id}`)),choice('도감 보기',`도감 ${c.name}`),...menu.slice(0,3)]};
    }
    if(!p.nickname)throw new GameError('댓글은 닉네임이 있어야 쓸 수 있어요.\n먼저: 닉네임 갈매미왕');
    if(!(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}})))throw new GameError(`${c.name}을(를) 아직 만나지 못했어요.\n만나 본 갈매미에게만 댓글을 남길 수 있어요.`);
    const problem=this.commentProblem(rest.trim());
    if(problem)throw new GameError(problem);
    const last=await tx.comment.findFirst({where:{playerId:p.id},orderBy:{createdAt:'desc'}});
    if(last&&now.getTime()-last.createdAt.getTime()<60_000)throw new GameError('댓글은 1분에 1개까지 쓸 수 있어요. 잠깐 뒤에 다시!');
    const today=DateTime.fromJSDate(now).setZone('Asia/Seoul').startOf('day').toJSDate();
    if(await tx.comment.count({where:{playerId:p.id,createdAt:{gte:today}}})>=30)throw new GameError('댓글은 하루 30개까지예요. 내일 또 만나요!');
    const made=await tx.comment.create({data:{playerId:p.id,characterId:c.id,text:rest.trim(),createdAt:now}});
    await this.audit(tx,p,'COMMENT',{commentId:made.id,characterId:c.id});
    const site=this.content.economy.homepage?.replace(/\/$/,'');
    return this.info({text:`💬 댓글을 남겼어요 (#${made.id})\n${emblems[c.rarity]} ${c.name}\n「${rest.trim()}」${site&&c.rarity!=='HIDDEN'?`\n\n홈페이지에서 보기: ${site}/dex/${c.id}`:''}`,choices:[choice('댓글 보기',`댓글 ${c.name}`),choice('지우기',`댓글삭제 ${made.id}`),...menu.slice(0,3)]},this.face(c.id,known));
  }
  // 좋아요: 사람(카톡)·방문자(웹)마다 댓글당 1번, 내 댓글은 안 됨
  private async likeComment(tx:Tx,id:number,voter:string,ownerCheck?:string){
    const c=await tx.comment.findUnique({where:{id}});
    if(!c||c.hidden)throw new GameError('그런 댓글은 없어요.');
    if(ownerCheck&&c.playerId===ownerCheck)throw new GameError('내 댓글에는 좋아요를 누를 수 없어요.');
    if(await tx.commentLike.findUnique({where:{commentId_voter:{commentId:id,voter}}}))return {comment:c,already:true};
    await tx.commentLike.create({data:{commentId:id,voter}});
    const updated=await tx.comment.update({where:{id},data:{likes:{increment:1}}});
    return {comment:updated,already:false};
  }
  // ───────── 웹 「내 갈매미」(로그인: 카톡에서 받은 6자리 코드) ─────────
  private async webCode(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    // 만료된 코드와, 이 사람의 아직 안 쓴 예전 코드는 지움(코드는 한 사람당 하나만 유효)
    await tx.webLogin.deleteMany({where:{OR:[{expiresAt:{lt:now}},{playerId:p.id,usedAt:null}]}});
    let code='';
    for(let i=0;i<8;i++){code=String(randomInt(0,1_000_000)).padStart(6,'0');if(!await tx.webLogin.findUnique({where:{code}}))break;}
    await tx.webLogin.create({data:{code,playerId:p.id,expiresAt:new Date(now.getTime()+10*60_000)}});
    const site=(this.content.economy.homepage??'').replace(/\/$/,'');
    return {text:`🔑 웹 로그인 코드: ${code}\n10분 안에 ${site?`${site}/my`:'홈페이지 「내 갈매미」'}에 입력하세요. 한 번만 쓸 수 있어요.\n\n이 코드는 다른 사람에게 알려 주지 마세요.`,choices:menu};
  }
  // 코드 확인 → 플레이어 id(맞지 않거나 만료·사용됨이면 null)
  async webLoginCode(code:string){
    if(!/^\d{6}$/.test(code))return null;
    return this.db.$transaction(async tx=>{
      const now=this.clock(),row=await tx.webLogin.findUnique({where:{code}});
      if(!row||row.usedAt||row.expiresAt<now)return null;
      const player=await tx.player.findUnique({where:{id:row.playerId}});
      if(!player||player.identity.startsWith('review:'))return null;
      const used=await tx.webLogin.updateMany({where:{code,usedAt:null},data:{usedAt:now}});
      if(used.count!==1)return null;
      await tx.auditEvent.create({data:{playerId:row.playerId,kind:'WEB_LOGIN',detail:json({})}});
      return row.playerId;
    });
  }
  // 카카오 로그인: 연결된 플레이어 id(없으면 null). 연결이 없어도 챗봇이 같은 회원번호(appUserId)를 보낸 적이 있으면 그 플레이어로 자동 연결
  async webKakaoPlayer(kakaoId:string){
    const linked=(await this.db.kakaoLink.findUnique({where:{kakaoId}}))?.playerId;
    if(linked)return linked;
    const chat=await this.db.chatAppUser.findUnique({where:{appUserId:kakaoId}});
    if(!chat)return null;
    try{await this.webLinkKakao(chat.playerId,kakaoId);return chat.playerId;}catch(e){if(e instanceof GameError)return null;throw e;}
  }
  // 챗봇 요청에 온 appUserId 기록(이 서버가 켜진 뒤 처음 본 짝만 DB에 씀). 처음 기록했으면 true
  private seenApp=new Map<string,string>();
  async noteChatApp(identity:string,appUserId:string){
    if(this.seenApp.get(identity)===appUserId)return false;
    const p=await this.db.player.findUnique({where:{identity},select:{id:true}});
    if(!p)return false;
    await this.db.$transaction([
      this.db.chatAppUser.deleteMany({where:{appUserId,NOT:{playerId:p.id}}}),
      this.db.chatAppUser.upsert({where:{playerId:p.id},create:{appUserId,playerId:p.id},update:{appUserId}}),
    ]);
    if(this.seenApp.size>5000)this.seenApp.clear();
    this.seenApp.set(identity,appUserId);
    return true;
  }
  async webLinkKakao(playerId:string,kakaoId:string|null){
    return this.webRun(playerId,async(tx,p)=>{
      if(p.identity.startsWith('review:'))throw new GameError('연결할 수 없는 계정이에요.');
      if(kakaoId){
        const other=await tx.kakaoLink.findUnique({where:{kakaoId}});
        if(other&&other.playerId!==p.id)throw new GameError('이 카카오 계정은 이미 다른 갈매미맨 계정에 연결돼 있어요.');
        await tx.kakaoLink.upsert({where:{playerId:p.id},create:{kakaoId,playerId:p.id},update:{kakaoId}});
      }else await tx.kakaoLink.deleteMany({where:{playerId:p.id}});
      await this.audit(tx,p,'WEB_KAKAO',{linked:!!kakaoId});
      return !!kakaoId;
    });
  }
  // 계정 삭제(탈퇴): 이 사람의 모든 게임 데이터를 한 거래로 지움. 다음에 카톡에 말을 걸면 새 계정으로 시작
  async webDeleteAccount(playerId:string){
    return this.webRun(playerId,async(tx,p)=>{
      await tx.appMeta.upsert({where:{key:'realtime-pvp-write-lock'},create:{key:'realtime-pvp-write-lock',value:'1'},update:{value:'1'}});
      const active=await tx.realtimeActivity.findUnique({where:{playerId:p.id}});
      if(active&&(active.kind==='ROOM'||active.expiresAt.getTime()>Date.now()))throw new GameError('매칭을 취소하거나 진행 중인 대전을 마친 뒤 탈퇴해 주세요.');
      await tx.rankedProfile.deleteMany({where:{playerId:p.id}});
      await tx.casualHumanRating.deleteMany({where:{playerId:p.id}});
      await tx.realtimeAttempt.deleteMany({where:{playerId:p.id}});
      await tx.realtimeRequest.deleteMany({where:{playerId:p.id}});
      await tx.realtimeLineup.deleteMany({where:{playerId:p.id}});
      await tx.realtimeItemEscrow.deleteMany({where:{playerId:p.id}});
      await tx.realtimeActivity.deleteMany({where:{playerId:p.id}});
      const rooms=await tx.realtimeRoom.findMany({where:{OR:[{player0Id:p.id},{player1Id:p.id}]},select:{id:true,player0Id:true,namesJson:true}});
      for(const room of rooms){const names=JSON.parse(room.namesJson) as string[];names[room.player0Id===p.id?0:1]='탈퇴한 사용자';await tx.realtimeRoom.update({where:{id:room.id},data:{namesJson:JSON.stringify(names)}});}
      const where={playerId:p.id};
      const mine=(await tx.comment.findMany({where,select:{id:true}})).map(c=>c.id);
      await tx.commentLike.deleteMany({where:{OR:[{commentId:{in:mine}},{voter:`p:${p.id}`}]}});
      await tx.comment.deleteMany({where});
      for(const m of [tx.progressionReceipt,tx.progressionMilestone,tx.progressionState,tx.ownedCharacter,tx.collectionEntry,tx.expedition,tx.pendingAction,tx.auditEvent,tx.achievement,tx.dailyProgress,tx.adminSnapshot,tx.webLogin,tx.kakaoLink,tx.storySave,tx.chatAppUser,tx.playerItem] as unknown as {deleteMany:(a:{where:{playerId:string}})=>Promise<unknown>}[])
        await m.deleteMany({where});
      await tx.groupUserKey.deleteMany({where});
      await tx.roomMember.deleteMany({where});
      await tx.roomWeek.deleteMany({where});
      await tx.roomDex.updateMany({where:{firstPlayerId:p.id},data:{firstPlayerId:null}});
      await tx.player.delete({where:{id:p.id}});
      return true;
    });
  }
  async webWho(playerId:string){
    const p=await this.db.player.findUnique({where:{id:playerId},select:{nickname:true,credits:true}});
    return p?{nickname:p.nickname,credits:p.credits}:null;
  }
  // 내 갈매미 요약: 재화, 가진 유닛(종류별 수·탐험 중), 전투·탐험 편성, 진행 중인 탐험
  async webMe(playerId:string){
    const p=await this.db.player.findUnique({where:{id:playerId}});
    if(!p)return null;
    const [owned,dex,trip,kakao,previous]=await Promise.all([
      this.db.ownedCharacter.groupBy({by:['characterId','status','locked'],where:{playerId,status:{in:['AVAILABLE','EXPEDITION']}},_count:{_all:true}}),
      this.db.collectionEntry.count({where:{playerId}}),
      this.db.expedition.findFirst({where:{playerId,claimedAt:null}}),
      this.db.kakaoLink.count({where:{playerId}}),
      this.db.expedition.findFirst({where:{playerId,claimedAt:{not:null}},orderBy:{startedAt:"desc"},select:{unitIds:true}}),
    ]);
    const by=new Map<string,{have:number;away:number;locked:number}>();
    for(const r of owned){const e=by.get(r.characterId)??{have:0,away:0,locked:0};e.have+=r._count._all;if(r.status==='EXPEDITION')e.away+=r._count._all;else if(r.locked)e.locked+=r._count._all;by.set(r.characterId,e);}
    const units=[...by].map(([id,e])=>{const c=this.content.map.get(id)!;return {id,name:c.name,tier:c.rarity,tierName:rarityNames[c.rarity],top:topTiers.has(c.rarity),synergy:c.synergy??null,quote:c.quote,tagline:c.tagline,intro:c.introduction,...e};})   // 한마디·소개: 게임 안 갈매미 상세 화면용(가진 갈매미만)
      .sort((a,b)=>tiers[b.tier]-tiers[a.tier]||this.content.map.get(a.id)!.position-this.content.map.get(b.id)!.position);
    const ids=(s:string)=>s?s.split(',').filter(id=>this.content.map.has(id)):[];
    return {nickname:p.nickname,snack:p.snack,peanut:p.peanut,credits:p.credits,kakao:kakao>0,claimReadyAt:new Date((p.lastClaimAt?.getTime()??0)+this.content.economy.tickets.cooldownHours*3_600_000).toISOString(),dex,dexTotal:this.content.characters.length,units,
      battleTeam:ids(p.battleTeam),party:ids(p.partyDraft),previousParty:previous?(previous.unitIds as string[]).filter(id=>this.content.map.has(id)):[],
      trip:trip?{startedAt:trip.startedAt.toISOString(),endsAt:trip.endsAt.toISOString(),units:(trip.unitIds as string[]).map(id=>this.content.map.get(id)?.name??id),ids:trip.unitIds as string[]}:null};
  }
  // 닉네임 바꾸기(카톡 「닉네임」·단체방 공용). 이미 같은 이름이면 false, 바꿨으면 true, 쓸 수 없는 이름은 GameError
  private async changeNickname(tx:Tx,p:Player,name:string){
    if(!/^[가-힣a-zA-Z0-9 ]{2,10}$/u.test(name))throw new GameError('닉네임은 2~10자, 한글·영문·숫자(띄어쓰기 가능)만 쓸 수 있어요.');
    // 공개 랭킹에서 운영자인 척하지 못하게 막는 이름
    const plain=name.replace(/\s+/g,'').toLowerCase();
    if(['관리자','운영자','운영진','운영팀','관리팀','개발자','공식','갈매미맨','admin','official','staff'].some(w=>plain.includes(w))||/^(gm|dev)\d*$/.test(plain))throw new GameError(`「${name}」은(는) 운영진과 헷갈릴 수 있어서 쓸 수 없어요.`);
    if(name===p.nickname)return false;
    if(await tx.player.findUnique({where:{nickname:name}}))throw new GameError(`「${name}」은(는) 이미 누가 쓰고 있어요.`);
    await tx.player.update({where:{id:p.id},data:{nickname:name}});
    await this.audit(tx,p,'NICKNAME',{from:p.nickname,to:name});
    return true;
  }
  // ───────── 단체방(그룹) 챗봇용(src/group/) ─────────
  // 단체방에서 처음 부른 사람의 플레이어(없으면 가입 보상과 함께 만듦). 카톡 요청과 같은 줄에서 처리
  groupEnsurePlayer(identity:string):Promise<string>{
    const task=this.tail.then(()=>this.db.$transaction(async tx=>(await this.player(tx,identity,this.clock())).id,{maxWait:500,timeout:2500}));
    this.tail=task.catch(()=>{});
    return task;
  }
  async groupSetNickname(playerId:string,raw:string){
    const name=raw.normalize('NFKC').trim().replace(/\s+/g,' ');
    return this.webRun(playerId,(tx,p)=>this.changeNickname(tx,p,name));
  }
  // 모집권 받기 결과를 글 대신 숫자로(단체방은 응답 문구를 따로 만듦)
  async groupClaim(playerId:string){
    return this.webRun(playerId,async(tx,p,now)=>{
      const wait=this.claimWait(p,now);
      if(wait)return {claimed:false as const,waitMs:wait,credits:p.credits};
      await this.claim(tx,p,now);
      const credits=(await tx.player.findUniqueOrThrow({where:{id:p.id},select:{credits:true}})).credits;
      return {claimed:true as const,amount:credits-p.credits,credits,cooldownHours:this.content.economy.tickets.cooldownHours};
    });
  }
  // 웹 요청도 카톡 요청과 같은 줄에 세워(동시에 처리되지 않게) 한 거래로 실행
  private webRun<T>(playerId:string,fn:(tx:Tx,p:Player,now:Date)=>Promise<T>):Promise<T>{
    const task=this.tail.then(()=>this.db.$transaction(async tx=>{
      const p=await tx.player.findUnique({where:{id:playerId}});
      if(!p)throw new GameError('로그인을 다시 해 주세요.');
      // 카톡 요청과 같게: 이 사람이 얻은 적 있는 유닛(그림을 보여 줄지 정할 때 씀)
      this.viewerSeen=new Set((await tx.collectionEntry.findMany({where:{playerId:p.id},select:{characterId:true}})).map(r=>r.characterId));
      return fn(tx,p,this.clock());
    },{maxWait:500,timeout:2500}));
    this.tail=task.catch(()=>{});
    return task;
  }
  // 카톡과 같은 미리보기(확인 요청)를 만들고 바로 확정: 웹은 화면에서 한 번 묻고 보냄
  private async confirmNow(tx:Tx,p:Player,now:Date,reply:GameReply){
    const token=reply.list?.items.find(i=>i.button?.action==='confirm')?.button?.token;
    if(!token)return reply;
    return this.confirm(tx,p,token,now);
  }
  private replyText=(r:GameReply)=>(r.text??'').trim();
  async webProtect(playerId:string,id:string,locked:boolean){
    if(!this.content.map.has(id))throw new GameError('없는 갈매미예요.');
    return this.webRun(playerId,async(tx,p)=>{
      const changed=await tx.ownedCharacter.updateMany({where:{playerId:p.id,characterId:id,status:'AVAILABLE',locked:!locked},data:{locked}});
      if(!changed.count)throw new GameError('보호 상태를 바꿀 갈매미가 없어요. 탐험·보유 상태를 확인해 주세요.');
      await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
      await this.audit(tx,p,'LOCK',{characterId:id,count:changed.count,locked});
      return `${changed.count}마리 ${locked?'보호했어요':'보호를 해제했어요'}`;
    });
  }
  // 탐험 편성(카톡 「탐험」과 같은 편성): 최상위 유닛 종류 id 목록(같은 유닛 여러 번 가능)
  async webSetParty(playerId:string,ids:string[]){
    if(!Array.isArray(ids)||ids.some(id=>typeof id!=='string'||!this.topIds.includes(id)))throw new GameError('탐험은 최상위 갈매미만 보낼 수 있어요.');
    return this.webRun(playerId,async(tx,p)=>{
      if(await this.activeExpedition(tx,p))throw new GameError('이미 탐험 중이에요. 돌아온 뒤 편성해 주세요.');
      await this.saveDraft(tx,p,ids);
      return this.draftIds(p);
    });
  }
  async webStartExpedition(playerId:string){
    return this.webRun(playerId,async(tx,p,now)=>this.replyText(await this.confirmNow(tx,p,now,await this.partyDraft(tx,p,'출발','',now))));
  }
  async webClaimExpedition(playerId:string){
    return this.webRun(playerId,async(tx,p,now)=>this.replyText(await this.claimExpedition(tx,p,now)));
  }
  // 상점: 카톡 상점과 같은 물건·같은 확인 과정
  // count: 웹에서 적은 수량. 카톡과 같은 구매를 그 수만큼 한 거래로 반복(중간에 모자라면 전부 취소)
  async webShop(playerId:string,item:'tickets'|'peanutTickets'|'skip',count=1){
    const sub={tickets:'뽑기권',peanutTickets:'땅콩뽑기권',skip:'탐험귀환'}[item];
    if(!sub)throw new GameError('없는 물건이에요.');
    if(!Number.isSafeInteger(count)||count<1||count>(item==='skip'?1:50))throw new GameError(item==='skip'?'한 번만 살 수 있어요.':'한 번에 1~50개까지 살 수 있어요.');
    return this.webRun(playerId,async(tx,p,now)=>{
      let text='';
      for(let i=0;i<count;i++){
        const fresh=i?await tx.player.findUniqueOrThrow({where:{id:p.id}}):p;
        text=this.replyText(await this.confirmNow(tx,fresh,now,await this.shop(tx,fresh,sub,now)));
      }
      return count>1?`🛒 ×${count} 완료!\n${text}`:text;
    });
  }
  // 대결 아이템 사기(종류·가격은 battle.yaml items에서 검사한 값을 받음)
  async webBuyItem(playerId:string,item:{id:string;name:string;price:number},count:number,maxStack:number){
    if(!Number.isSafeInteger(count)||count<1||count>maxStack)throw new GameError(`한 번에 1~${maxStack}개까지 살 수 있어요.`);
    return this.webRun(playerId,async(tx,p)=>{
      const cost=item.price*count;
      const have=(await tx.playerItem.findUnique({where:{playerId_itemId:{playerId:p.id,itemId:item.id}}}))?.count??0;
      const held=await tx.realtimeItemEscrow.count({where:{playerId:p.id,itemId:item.id,state:'HELD'}});
      if(have+held+count>maxStack)throw new GameError(`${item.name}은(는) 예약한 수량을 포함해 ${maxStack}개까지 가질 수 있어요. 지금 ${have}개, 전투 예약 ${held}개.`);
      const paid=await tx.player.updateMany({where:{id:p.id,snack:{gte:cost}},data:{snack:{decrement:cost}}});
      if(paid.count!==1)throw new GameError(`🍤 새우gang이 부족해요. ${cost}개 필요, 지금 ${p.snack}개.`);
      await tx.playerItem.upsert({where:{playerId_itemId:{playerId:p.id,itemId:item.id}},create:{playerId:p.id,itemId:item.id,count},update:{count:{increment:count}}});
      await this.audit(tx,p,'SHOP',{item:`ITEM_${item.id}`,count,snacks:-cost});
      return `🛒 ${item.name} ×${count} 샀어요! 🍤 -${cost}`;
    });
  }
  // ───────── 웹 뽑기(카톡 「뽑기」·「뽑기권받기」와 같은 처리) ─────────
  async webDrawInfo(playerId:string){
    const p=await this.db.player.findUnique({where:{id:playerId}});
    if(!p)return null;
    const now=this.clock(),g=this.content.economy.gachas,ev=this.event(now),pity=g.HIGH.pity;
    return {credits:p.credits,unlimited:unlimited(p),wait:this.claimWait(p,now),claim:this.content.economy.tickets.claim+(ev?.claimBonus??0),cooldownHours:this.content.economy.tickets.cooldownHours,
      event:ev?{label:ev.label,claimBonus:ev.claimBonus??0,snack:ev.snackMultiplier}:null,
      pity:pity?{tierName:rarityNames[pity.tier],left:Math.max(0,pity.count-p.highPity)}:null,
      gachas:(['LOW','MID','HIGH'] as GachaKind[]).map(kind=>({kind,label:g[kind].label,cost:g[kind].cost,img:`gacha-${kind}`,
        weights:Object.entries(g[kind].weights).map(([t,w])=>({tier:t,tierName:rarityNames[t],w}))}))};
  }
  async webClaim(playerId:string){
    return this.webRun(playerId,async(tx,p,now)=>this.replyText(await this.claim(tx,p,now)));
  }
  // 웹·앱 쿠폰 입력(카톡 「쿠폰 코드」와 같은 처리: 1인 1회·기간 확인)
  async webCoupon(playerId:string,code:string){
    if(!code.replace(/\s+/g,''))throw new GameError('쿠폰 코드를 적어 주세요.');
    if(code.length>60)throw new GameError('🎟 맞는 쿠폰이 없어요.');
    return this.webRun(playerId,async(tx,p,now)=>this.replyText(await this.coupon(tx,p,code,now)));
  }
  // 게임 로비 「출석체크」: 오늘의 미션 진행(카톡과 같은 칸) + 출석(하루 한 번, Achievement ATTEND_<날짜>)
  async webDaily(playerId:string){
    const now=this.clock(),day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const row=await this.db.dailyProgress.findUnique({where:{playerId_day:{playerId,day}}});
    const done=new Set((row?.rewarded??'').split(',').filter(Boolean));
    const missions=this.content.economy.missions.map(m=>({key:m.key,label:m.label,goal:m.goal,value:Math.min(m.goal,row?.[GameService.missionField[m.key]]??0),done:done.has(m.key),tickets:m.tickets}));
    const att=await this.db.achievement.findMany({where:{playerId,key:{startsWith:'ATTEND_'}},select:{key:true},orderBy:{key:'desc'},take:40});
    const days=new Set(att.map(a=>a.key.slice(7)));
    let streak=0;for(let d=DateTime.fromISO(day).minus({days:days.has(day)?0:1});days.has(d.toISODate()!);d=d.minus({days:1}))streak++;
    // 이번 주(연속 1~7일째 칸) 보상표: 오늘 출석했으면 오늘이 든 주, 아니면 내일 받을 칸이 든 주
    const n=days.has(day)?streak:streak+1,week=Math.floor((n-1)/7);
    return {day,missions,attend:{today:days.has(day),streak,week:week+1,dayInWeek:((n-1)%7)+1,rewards:[1,2,3,4,5,6,7].map(k=>this.attendReward(week*7+k)),next:days.has(day)?null:this.attendReward(n)}};
  }
  // 연속 n일째 출석 보상(뽑기권)
  private attendReward(n:number){
    const a=this.content.economy.attendance;
    return a.days[(n-1)%7]+a.weekStep*Math.min(Math.floor((n-1)/7),a.weekCap);
  }
  async webAttend(playerId:string){
    return this.webRun(playerId,async(tx,p,now)=>{
      const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!,key=`ATTEND_${day}`;
      if(await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key}}}))throw new GameError('오늘은 이미 출석했어요. 내일 또 와요!');
      let streak=1;for(let d=DateTime.fromISO(day).minus({days:1});await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key:`ATTEND_${d.toISODate()}`}}});d=d.minus({days:1})){streak++;if(streak>400)break;}
      const tickets=this.attendReward(streak);
      await tx.achievement.create({data:{playerId:p.id,key,createdAt:now}});
      if(tickets)await tx.player.update({where:{id:p.id},data:{credits:{increment:tickets},revision:{increment:1}}});
      await this.audit(tx,p,'ATTEND',{day,streak,tickets});
      return {streak,tickets,dayInWeek:((streak-1)%7)+1};
    });
  }
  // ── PvP(v0.11.0): 상대 = 다른 유저의 「전투 편성」 팀(컴퓨터가 조종), 양쪽 모두 같은 레벨 ──
  // 대결 표는 서버 메모리(1시간). 랭크는 시작 때 패배로 먼저 셈(중간에 나가도 패배), 기록을 다시 돌려 이긴 게 맞으면 되돌리고 승리 점수
  private pvpMatches=new Map<string,{player:string;mode:'normal'|'rank';seed:number;mine:UnitRecord[];foe:UnitRecord[];exp:number;lost:number;done:boolean}>();
  private pvpTier(point:number){const t=[...this.content.economy.pvp.tiers].sort((a,b)=>a.min-b.min);return [...t].reverse().find(x=>point>=x.min)?.name??t[0].name;}
  private pvpRecs(ids:string[],side:string):UnitRecord[]{
    const lv=this.content.economy.pvp.level;
    return ids.map((id,i)=>({uid:`${side}${i}`,id,name:this.content.map.get(id)?.name??id,tier:this.content.map.get(id)!.rarity as UnitRecord['tier'],level:lv,xp:0,stage:0,evoAugs:[]}));
  }
  private pvpTeam(raw:string){const ids=raw.split(',').filter(Boolean);return ids.length===3&&ids.every(id=>this.topIds.includes(id))&&new Set(ids).size===3?ids:null;}
  async webPvp(playerId:string){
    const p=await this.db.player.findUnique({where:{id:playerId}});
    if(!p)throw new GameError('로그인을 다시 해 주세요.');
    const top=await this.db.player.findMany({where:{identity:{not:{startsWith:'review:'}},OR:[{rankWins:{gt:0}},{rankLosses:{gt:0}}]},orderBy:[{rankPoint:'desc'},{rankWins:'desc'}],take:20,select:{id:true,nickname:true,rankPoint:true,rankWins:true,rankLosses:true}});
    const played=p.rankWins+p.rankLosses>0,rank=played?await this.db.player.count({where:{identity:{not:{startsWith:'review:'}},rankPoint:{gt:p.rankPoint},OR:[{rankWins:{gt:0}},{rankLosses:{gt:0}}]}})+1:null;
    return {tiers:[...this.content.economy.pvp.tiers].sort((a,b)=>a.min-b.min).map(t=>t.name),level:this.content.economy.pvp.level,win:this.content.economy.pvp.win,lose:this.content.economy.pvp.lose,team:this.pvpTeam(p.battleTeam),
      me:{point:p.rankPoint,tier:this.pvpTier(p.rankPoint),wins:p.rankWins,losses:p.rankLosses,rank},
      top:top.map((r,i)=>({rank:i+1,name:r.nickname??`갈매미 #${r.id.slice(0,4)}`,point:r.rankPoint,tier:this.pvpTier(r.rankPoint),wins:r.rankWins,losses:r.rankLosses,me:r.id===p.id}))};
  }
  assertLegacyPvpAllowed(mode:string):void{
    // Human-owned saved teams are still AI controlled in this legacy path.
    if(mode==='rank')throw new GameError(LEGACY_RANK_UNAVAILABLE);
  }
  async webPvpMatch(playerId:string,mode:string){
    this.assertLegacyPvpAllowed(mode);
    if(mode!=='normal'&&mode!=='rank')throw new GameError('없는 대전 방식이에요.');
    const now=this.clock().getTime();
    for(const [k,v] of this.pvpMatches)if(v.exp<now)this.pvpMatches.delete(k);
    if(this.pvpMatches.size>20_000)this.pvpMatches.clear();
    return this.webRun(playerId,async(tx,p)=>{
      const mine=this.pvpTeam(p.battleTeam);
      if(!mine)throw new GameError('「전투 편성」에서 최상위 갈매미 3마리를 먼저 정해 주세요.');
      const have=await tx.ownedCharacter.findMany({where:{playerId:p.id,characterId:{in:mine},status:{in:['AVAILABLE','EXPEDITION']}},select:{characterId:true}});
      if(new Set(have.map(h=>h.characterId)).size!==3)throw new GameError('전투 편성에 지금 가지고 있지 않은 갈매미가 있어요. 편성을 다시 해 주세요.');
      // 상대: 3마리를 편성한 다른 유저. 랭크는 점수가 가까운 10명 중에서
      const cands=(await tx.player.findMany({where:{identity:{not:{startsWith:'review:'}},id:{not:p.id},battleTeam:{contains:','}},select:{id:true,nickname:true,battleTeam:true,rankPoint:true},take:500}))
        .map(r=>({...r,team:this.pvpTeam(r.battleTeam)})).filter(r=>r.team);
      const pool=mode==='rank'?cands.sort((a,b)=>Math.abs(a.rankPoint-p.rankPoint)-Math.abs(b.rankPoint-p.rankPoint)).slice(0,10):cands;
      const pick=pool.length?pool[randomInt(pool.length)]:null;
      if(!pick&&mode==='rank')throw new GameError('아직 랭크 상대가 없어요. 일반게임으로 먼저 해 보세요.');
      const bot=[...this.topIds];for(let i=bot.length-1;i>0;i--){const j=randomInt(i+1);[bot[i],bot[j]]=[bot[j],bot[i]];}   // 상대가 없으면(일반만) 무작위 연습 팀
      const foeIds=pick?pick.team!:bot.slice(0,3);
      let lost=0;
      if(mode==='rank'){lost=Math.min(this.content.economy.pvp.lose,p.rankPoint);
        await tx.player.update({where:{id:p.id},data:{rankPoint:{decrement:lost},rankLosses:{increment:1}}});}
      const id=randomBytes(12).toString('base64url'),seed=randomInt(2**31-1);
      const m={player:p.id,mode:mode as 'normal'|'rank',seed,mine:this.pvpRecs(mine,'a'),foe:this.pvpRecs(foeIds,'b'),exp:now+3600_000,lost,done:false};
      this.pvpMatches.set(id,m);
      await this.audit(tx,p,'PVP_START',{mode,foe:pick?.id??'BOT'});
      return {match:id,mode,seed,mine:m.mine,foe:m.foe,foeName:pick?(pick.nickname??`갈매미 #${pick.id.slice(0,4)}`):'훈련 교관 팀',
        foeTier:pick?this.pvpTier(pick.rankPoint):null,bot:!pick};
    });
  }
  // 대결 기록 확인: 같은 시드·팀으로 다시 돌려 이긴 쪽을 서버가 정함(화면이 보낸 승패는 믿지 않음)
  async webPvpResult(playerId:string,R:Rules,match:string,events:PvpEvent[]){
    const m=this.pvpMatches.get(match);
    if(!m||m.player!==playerId||m.exp<this.clock().getTime())throw new GameError('대전 정보가 없어요. 다시 시작해 주세요.');
    this.assertLegacyPvpAllowed(m.mode);
    if(m.done)throw new GameError('이미 결과를 받은 대전이에요.');
    let winner:0|1|null;
    try{winner=replay(createBattle(R,m.mine,m.foe,m.seed),events);}catch{throw new GameError('대전 기록이 맞지 않아요.');}
    if(winner===null)throw new GameError('대전이 아직 끝나지 않았어요.');
    m.done=true;
    const won=winner===0,pv=this.content.economy.pvp;
    return this.webRun(playerId,async(tx,p)=>{
      if(m.mode==='rank'&&won)await tx.player.update({where:{id:p.id},data:{rankPoint:{increment:m.lost+pv.win},rankLosses:{decrement:1},rankWins:{increment:1}}});
      await this.audit(tx,p,'PVP_END',{mode:m.mode,won});
      const after=await tx.player.findUniqueOrThrow({where:{id:p.id}});
      return {won,mode:m.mode,change:m.mode==='rank'?(won?pv.win:-m.lost):0,point:after.rankPoint,tier:this.pvpTier(after.rankPoint),wins:after.rankWins,losses:after.rankLosses};
    }).catch(e=>{m.done=false;throw e;});
  }
  async webDraw(playerId:string,kind:string,times:number){
    if(kind!=='LOW'&&kind!=='MID'&&kind!=='HIGH')throw new GameError('없는 뽑기예요.');
    if(!Number.isSafeInteger(times)||times<1||times>50)throw new GameError('뽑기는 한 번에 1~50회예요.');
    return this.webRun(playerId,async(tx,p,now)=>{
      const out:{c:Character;first:boolean;key:string}[]=[];
      const r=await this.draw(tx,p,kind,times,now,out);
      if(!out.length)throw new GameError(this.replyText(r));   // 뽑기권 부족 안내
      const known=await this.known(tx,p);
      // 같은 유닛은 묶고 높은 등급부터
      const by=new Map<string,{c:Character;n:number;first:boolean;ownedKeys:string[]}>();
      for(const x of out){const e=by.get(x.c.id)??{c:x.c,n:0,first:false,ownedKeys:[]};e.n++;e.first||=x.first;e.ownedKeys.push(x.key);by.set(x.c.id,e);}
      const items=[...by.values()].sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity]||Number(b.first)-Number(a.first)||a.c.position-b.c.position)
        .map(x=>({id:x.c.id,name:this.name(x.c.id,known),tier:x.c.rarity,tierName:rarityNames[x.c.rarity],img:this.cardImage(x.c.id,known),n:x.n,first:x.first,ownedKeys:x.ownedKeys}));
      return {items,summary:(times>1?r.cards?.outro:undefined)??this.replyText(r)};
    });
  }

  // ───────── 웹 조합(카톡 「조합」과 같은 처리) ─────────
  // 조합식 목록(공개된 것 + 발견한 히든)과 재료로 섞을 수 있는 가진 유닛. 아직 얻지 못한 유닛은 그림 대신 등급 실루엣
  async webCraftInfo(playerId:string){
    return this.webRun(playerId,async(tx,p)=>{
      const known=await this.known(tx,p),stock=await this.stock(tx,p);
      const unit=(id:string)=>{const c=this.content.map.get(id)!;return {id,name:this.name(id,known),tier:c.rarity,tierName:rarityNames[c.rarity],img:this.face(id,known)};};
      const recipes=this.visibleRecipes(known).map(r=>{
        const c=this.content.map.get(r.resultId)!;
        return {...unit(c.id),isNew:!this.viewerSeen.has(c.id),ready:this.canCraft(r,stock),max:this.maxCraft(r,stock),confirm:tiers[c.rarity]>tiers.SPECIAL||c.rarity==='HIDDEN',
          materials:[...materialCounts(r.materials)].map(([id,n])=>{const s=statusOf(stock.get(id));return {...unit(id),need:n,usable:s.usable,locked:s.locked,away:s.away};})};
      }).sort((a,b)=>Number(b.ready)-Number(a.ready)||tiers[b.tier as Tier]-tiers[a.tier as Tier]);
      const pool=this.revealed(known).map(c=>({c,u:statusOf(stock.get(c.id)).usable})).filter(x=>x.u>0)
        .sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity]||a.c.position-b.c.position).map(({c,u})=>({...unit(c.id),usable:u,hiddenHint:hiddenMaterialHint(this.content.recipes,known,c.id)}));
      return {recipes,pool,maxMaterials:mixMaterialLimit(this.content.recipes)};
    });
  }
  // 이름(결과 유닛)으로 조합: times번(재료가 되는 만큼까지)
  async webCraft(playerId:string,resultId:string,times:number){
    if(!Number.isSafeInteger(times)||times<1||times>50)throw new GameError('한 번에 1~50개까지 만들 수 있어요.');
    return this.webRun(playerId,async(tx,p,now)=>{
      const known=await this.known(tx,p);
      const recipe=this.visibleRecipes(known).find(r=>r.resultId===resultId);
      if(!recipe)throw new GameError('그런 조합식은 없어요.');
      const stock=await this.stock(tx,p);
      if(!this.canCraft(recipe,stock))throw new GameError('재료가 모자라요. (보호·탐험 중인 갈매미는 재료로 쓰지 않아요)');
      const most=this.maxCraft(recipe,stock);
      if(times>most)throw new GameError(`재료가 ${most}개 만들 만큼만 있어요.`);
      const ids=await this.chooseMaterials(tx,p,recipe,known,times);
      return this.replyText(await this.craft(tx,p,recipe,ids,times,now,known));
    });
  }
  // 재료로 직접 섞기(숨은 조합 찾기): 맞는 조합식이 없으면 재료는 그대로
  async webMix(playerId:string,ids:string[]){
    if(!Array.isArray(ids)||ids.length<2||ids.length>mixMaterialLimit(this.content.recipes)||ids.some(id=>typeof id!=='string'||!this.content.map.has(id)))throw new GameError(`재료는 2~${mixMaterialLimit(this.content.recipes)}마리를 골라 주세요.`);
    return this.webRun(playerId,async(tx,p,now)=>{
      const known=await this.known(tx,p);
      if(ids.some(id=>!isRevealed(this.content.map.get(id)!,known)))throw new GameError('재료는 가진 갈매미로만 골라 주세요.');
      const owned=await this.stock(tx,p);
      const short=[...materialCounts(ids)].find(([id,n])=>statusOf(owned.get(id)).usable<n);
      if(short)throw new GameError(`${this.name(short[0],known)}이(가) 부족해요. (보호·탐험 중인 갈매미는 빼고 세요)`);
      const lesson=await this.tutorialStep(tx,p,GameService.tutorialSteps.findIndex(t=>t.go.message==='조합 고르기')+1);
      const key=[...ids].sort().join('+');
      const recipe=this.content.recipes.find(r=>[...r.materials].sort().join('+')===key);
      const head=lesson.length?`${lesson[0]}\n\n`:'';
      if(!recipe)return {made:false,text:`${head}🔮 …아무 일도 일어나지 않았다.\n${ids.map(id=>this.name(id,known)).join(' + ')}\n(이 조합으로는 아무것도 안 나와요. 재료는 그대로예요)`};
      const chosen=await this.chooseMaterials(tx,p,recipe,known);
      return {made:true,id:recipe.resultId,text:head+this.replyText(await this.craft(tx,p,recipe,chosen,1,now,known))};
    });
  }

  // 대결 중 아이템 쓰기: 가진 개수에서 1개 빼기(0개면 거절)
  private async consumeItem(tx:Tx,p:Player,item:{id:string;name:string}){
      const used=await tx.playerItem.updateMany({where:{playerId:p.id,itemId:item.id,count:{gte:1}},data:{count:{decrement:1}}});
      if(used.count!==1)throw new GameError(`${item.name}이(가) 없어요. 「내 갈매미」 상점에서 살 수 있어요.`);
      await this.audit(tx,p,'ITEM_USE',{item:item.id});
      return (await tx.playerItem.findUniqueOrThrow({where:{playerId_itemId:{playerId:p.id,itemId:item.id}}})).count;
  }
  async webUseItem(playerId:string,item:{id:string;name:string}){
    return this.webRun(playerId,(tx,p)=>this.consumeItem(tx,p,item));
  }
  // 스토리 진행(JSON, 모양 검사는 my.ts에서)
  // 로그인한 네이티브 도감: 현재 보유가 0이어도 한 번 발견한 기록을 유지한다.
  async webCollectionIds(playerId:string){
    return (await this.db.collectionEntry.findMany({where:{playerId},select:{characterId:true}})).map(row=>row.characterId);
  }
  async webStory(playerId:string):Promise<unknown>{
    const row=await this.db.storySave.findUnique({where:{playerId}});
    if(!row)return null;
    try{return JSON.parse(row.data);}catch{return null;}
  }
  // 스토리용 내 최상위 갈매미(v0.16.2, 개체별): 한 마리마다 번호(oid). 조합·판매로 없어진 것은 빠짐
  async webStoryUnits(playerId:string){
    const rows=await this.db.ownedCharacter.findMany({where:{playerId,status:{in:['AVAILABLE','EXPEDITION']},OR:[{characterId:{in:this.topIds}},{reincarnation:{gt:0},inheritedFrom:{not:null}}]},select:{id:true,characterId:true,locked:true,reincarnation:true,inheritedFrom:true,statBonusPct:true},orderBy:{id:'asc'}});
    return rows.map(r=>({oid:r.id,id:topTiers.has(this.content.map.get(r.characterId)!.rarity)?r.characterId:(r.inheritedFrom??r.characterId),inheritedFrom:r.inheritedFrom,ownedCharacterId:r.characterId,name:this.content.map.get(r.characterId)!.name,locked:r.locked,reincarnation:r.reincarnation,statBonusPct:r.statBonusPct}));
  }
  private async saveStory(tx:Tx,p:Player,data:unknown){
      if(data===null){await tx.storySave.deleteMany({where:{playerId:p.id}});return true;}
      // 개체 열쇠(o번호)는 지금 가진 최상위 갈매미만 남김. 편성에 있거나 키운 개체는 보호(조합·판매 재료로 안 쓰이게)
      const d=data as {units:Record<string,{level:number;xp:number;learned?:string[];stage?:number}>;team:string[];hp?:Record<string,unknown>};
      const okeys=new Set([...Object.keys(d.units),...d.team,...Object.keys(d.hp??{})].filter(k=>/^o\d+$/.test(k)));
      const mine=new Set((await tx.ownedCharacter.findMany({where:{playerId:p.id,id:{in:[...okeys].map(k=>+k.slice(1))},status:{in:['AVAILABLE','EXPEDITION']},OR:[{characterId:{in:this.topIds}},{reincarnation:{gt:0},inheritedFrom:{not:null}}]},select:{id:true}})).map(r=>`o${r.id}`));
      const keep=(k:string)=>!/^o\d+$/.test(k)||mine.has(k);
      d.units=Object.fromEntries(Object.entries(d.units).filter(([k])=>keep(k)));d.team=d.team.filter(keep);
      if(d.hp)d.hp=Object.fromEntries(Object.entries(d.hp).filter(([k])=>keep(k)));
      const raised=Object.entries(d.units).filter(([k,u])=>mine.has(k)&&(u.xp>0||u.level>1||(u.stage??0)>0||(u.learned??[]).length>4)).map(([k])=>k);
      const protect=[...new Set([...d.team.filter(k=>mine.has(k)),...raised])].map(k=>+k.slice(1));
      if(protect.length)await tx.ownedCharacter.updateMany({where:{playerId:p.id,id:{in:protect},locked:false},data:{locked:true}});
      const text=JSON.stringify(d);
      await tx.storySave.upsert({where:{playerId:p.id},create:{playerId:p.id,data:text},update:{data:text}});
      return true;
  }
  async webSaveStory(playerId:string,data:unknown){
    return this.webRun(playerId,(tx,p)=>this.saveStory(tx,p,data));
  }
  // 네이티브 회복·전투 보상: 아이템 차감과 저장을 같은 거래로 확정한다.
  async webNativeCommit(playerId:string,item?:{id:string;name:string},data?:unknown,award?:{session:string;points:number}){
    return this.webRun(playerId,async(tx,p,now)=>{
      if(award&&!(p.isAdmin&&p.adminMode)){return progressionReceipt(tx,p,`battle:${award.session}`,'battleReward',{},async()=>{
        if(item)await this.consumeItem(tx,p,item);if(data!==undefined)await this.saveStory(tx,p,data);
        await awardPlayPoints(tx,p,award.points,now);return true;});}
      if(item)await this.consumeItem(tx,p,item);
      if(data!==undefined)await this.saveStory(tx,p,data);
      return true;
    });
  }
  async webProgression(playerId:string){return this.webRun(playerId,(tx,p,now)=>progressionState(tx,p,this.content,now));}
  async webProgressionAction(playerId:string,requestId:string,action:'pass'|'reroll'|'reassess'|'reincarnate',value?:string,before?:()=>void){
    return this.webRun(playerId,(tx,p,now)=>progressionReceipt(tx,p,requestId,action,{value:value??null},async()=>{
      if(action==='pass')return claimMilestone(tx,p,value!);
      if(action==='reroll')return claimReroll(tx,p,now);
      before?.();return transformOwned(tx,p,this.content,value!,action,now);
    }));
  }
  async webConsumeBonusReroll(playerId:string,requestId:string){
    return this.webRun(playerId,(tx,p,now)=>progressionReceipt(tx,p,requestId,'consumeReroll',{},async()=>{await consumeReroll(tx,p,now);return {ok:true};}));
  }
  // 게임 튜토리얼(v0.15.0): 처음 물을 때 새 유저(스토리 저장·가진 갈매미 없음)면 1단계, 아니면 9(끝)로 정함
  async webTutorial(playerId:string){
    const p=await this.db.player.findUnique({where:{id:playerId},select:{gameTutorial:true,gameSeen:true}});
    if(!p)throw new GameError('계정을 찾지 못했어요.');
    let step=p.gameTutorial;
    if(step===0){
      const old=(await this.db.storySave.count({where:{playerId}}))>0||(await this.db.ownedCharacter.count({where:{playerId}}))>0;
      step=old?9:1;await this.db.player.update({where:{id:playerId},data:{gameTutorial:step}});
    }
    return {step,seen:p.gameSeen.split(',').filter(Boolean)};
  }
  // 단계는 앞으로만(9 = 끝·건너뛰기). 뽑기 단계(2)를 지나면 태초의 갈매미맨(D1)을 한 번 지급(한 번도 가진 적 없을 때만). 그 자리에서 건너뛰면 전투 편성도 D1로
  async webTutorialSet(playerId:string,step:number,seen?:string){
    if(!Number.isInteger(step)||step<1||(step>5&&step!==9))throw new GameError('튜토리얼 단계가 맞지 않아요.');
    if(seen!==undefined&&!/^[a-z]{1,20}$/.test(seen))throw new GameError('화면 이름이 맞지 않아요.');
    const now=new Date();
    return this.webRun(playerId,async(tx,p)=>{
      const cur=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{gameTutorial:true,gameSeen:true,battleTeam:true}});
      const next=Math.max(cur.gameTutorial,step);
      let granted=false;
      if(cur.gameTutorial>=1&&cur.gameTutorial<=2&&next>=3&&!(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:'D1'}}}))){
        await this.grant(tx,p,'D1','TUTORIAL',now);granted=true;
        if(next===9&&!cur.battleTeam)await tx.player.update({where:{id:p.id},data:{battleTeam:'D1'}});   // 건너뛰면 전투 편성도 이 한 마리로(진행 중이면 3단계에서 직접 편성)
      }
      const seenList=cur.gameSeen.split(',').filter(Boolean);
      if(seen&&!seenList.includes(seen)&&seenList.length<40)seenList.push(seen);
      await tx.player.update({where:{id:p.id},data:{gameTutorial:next,gameSeen:seenList.join(',')}});
      return {step:next,seen:seenList,granted};
    });
  }
  async webItems(playerId:string){
    return (await this.db.playerItem.findMany({where:{playerId,count:{gt:0}}})).map(r=>({id:r.itemId,count:r.count}));
  }
  webPrices(){const e=this.content.economy;return {tickets:{snack:e.shop.tickets.snacks,amount:e.shop.tickets.amount},peanutTickets:{peanut:1,amount:e.peanutShop.tickets},skip:{snack:e.shop.expeditionSkip.snacks},maxParty:e.expedition.maxParty};}
  // 전투 편성: 가진 최상위 유닛 종류 0~3개(순서 = 출전 순서)
  async webSetBattleTeam(playerId:string,ids:string[]){
    if(!Array.isArray(ids)||ids.length>3||new Set(ids).size!==ids.length)throw new GameError('전투 편성은 서로 다른 최상위 갈매미 3마리까지예요.');
    for(const id of ids)if(typeof id!=='string'||!this.topIds.includes(id))throw new GameError('최상위 갈매미만 전투에 나갈 수 있어요.');
    const have=await this.db.ownedCharacter.findMany({where:{playerId,characterId:{in:ids},status:{in:['AVAILABLE','EXPEDITION']}},select:{characterId:true}});
    const mine=new Set(have.map(h=>h.characterId));
    const missing=ids.filter(id=>!mine.has(id));
    if(missing.length)throw new GameError(`가지고 있지 않은 갈매미예요: ${missing.map(id=>this.content.map.get(id)?.name??id).join(', ')}`);
    await this.db.player.update({where:{id:playerId},data:{battleTeam:ids.join(',')}});
    return ids;
  }

  async webLike(id:number,visitor:string){
    if(!Number.isSafeInteger(id)||id<1)return null;
    try{
      const r=await this.db.$transaction(tx=>this.likeComment(tx as unknown as Tx,id,`w:${visitor}`));
      return {likes:r.comment.likes,already:r.already};
    }catch(e){if(e instanceof GameError)return null;throw e;}
  }
  // 웹: 유닛 댓글(숨김 제외). 히든 유닛 댓글은 웹에 안 보임(스포일러)
  async unitComments(characterId:string,take=30){
    const c=this.content.map.get(characterId);
    if(!c||c.rarity==='HIDDEN')return [];
    return (await this.reader.comment.findMany({where:{characterId,hidden:false},orderBy:[{likes:'desc'},{createdAt:'desc'}],take,include:{player:{select:{nickname:true,badge:true}}}}))
      .map(x=>({id:x.id,text:mask(this.content,x.text,new Set()),likes:x.likes,at:x.createdAt,name:x.player.nickname??'이름 없는 갈매미',badge:this.badgeEmoji(x.player.badge),characterId:x.characterId}));
  }
  async recentComments(take=30){
    return this.cached(`recent-comments-${take}`,15_000,async()=>(await this.reader.comment.findMany({where:{hidden:false,characterId:{notIn:this.content.hiddenIds}},orderBy:{createdAt:'desc'},take,include:{player:{select:{nickname:true,badge:true}}}}))
      .map(x=>({id:x.id,text:mask(this.content,x.text,new Set()),likes:x.likes,at:x.createdAt,name:x.player.nickname??'이름 없는 갈매미',badge:this.badgeEmoji(x.player.badge),characterId:x.characterId})));
  }
  async topComments(take=10){
    const since=new Date(this.clock().getTime()-7*86_400_000);
    return this.cached(`top-comments-${take}`,15_000,async()=>(await this.reader.comment.findMany({where:{hidden:false,likes:{gt:0},createdAt:{gte:since},characterId:{notIn:this.content.hiddenIds}},orderBy:[{likes:'desc'},{createdAt:'desc'}],take,include:{player:{select:{nickname:true,badge:true}}}}))
      .map(x=>({id:x.id,text:mask(this.content,x.text,new Set()),likes:x.likes,at:x.createdAt,name:x.player.nickname??'이름 없는 갈매미',badge:this.badgeEmoji(x.player.badge),characterId:x.characterId})));
  }

  // ───────── 랭킹 ─────────
  // 도감 수집 수 순위. 관리자 모드를 켠 계정은 제외합니다.
  // 랭킹: 도감(수집 종 수) / 최상위(보유 최상위 수) / 주간(이번 주 조합 횟수, 월요일 0시 기준)
  // 랭킹 점수(관리자 모드 제외). 카톡 랭킹과 웹 랭킹이 같은 기준을 씁니다
  private async rankRows(tx:Tx,kind:'DEX'|'TOP'|'WEEK',now:Date){
    const where={player:{adminMode:false,identity:{not:{startsWith:'review:'}}}};
    let rows:{playerId:string;score:number}[];
    if(kind==='DEX')rows=(await tx.collectionEntry.groupBy({by:['playerId'],where,_count:{_all:true}})).map(r=>({playerId:r.playerId,score:r._count._all}));
    else if(kind==='TOP')rows=(await tx.ownedCharacter.groupBy({by:['playerId'],where:{...where,status:{in:['AVAILABLE','EXPEDITION']},characterId:{in:this.topIds}},_count:{_all:true}})).map(r=>({playerId:r.playerId,score:r._count._all}));
    else{
      const monday=DateTime.fromJSDate(now).setZone('Asia/Seoul').startOf('week').toJSDate();
      const crafts=await tx.auditEvent.findMany({where:{...where,kind:'CRAFT',createdAt:{gte:monday}},select:{playerId:true,detail:true,createdAt:true}});
      const admin=await this.adminWindows(tx);
      const sum=new Map<string,number>();
      for(const c of crafts)if(!admin(c.playerId,c.createdAt))sum.set(c.playerId,(sum.get(c.playerId)??0)+Number((c.detail as {times?:number}).times??1));
      rows=[...sum].map(([playerId,score])=>({playerId,score}));
    }
    return rows.sort((a,b)=>b.score-a.score||a.playerId.localeCompare(b.playerId));
  }
  // 관리자 모드였던 시간: ADMIN 기록(mode 켜짐 → 꺼짐)으로 구간을 만듭니다.
  // 모드를 끄면 유닛·재화는 되돌아가지만 기록(감사 로그)은 남으므로, 주간 랭킹·히든 최초 발견에서 이 구간의 조합은 뺍니다
  private async adminWindows(tx:Tx){
    const rows=await tx.auditEvent.findMany({where:{kind:'ADMIN'},orderBy:{createdAt:'asc'},select:{playerId:true,detail:true,createdAt:true}});
    const open=new Map<string,number>(),spans=new Map<string,[number,number][]>();
    for(const r of rows){
      const on=(r.detail as {mode?:boolean}).mode===true,t=r.createdAt.getTime();
      if(on&&!open.has(r.playerId))open.set(r.playerId,t);
      else if(!on&&open.has(r.playerId)){spans.set(r.playerId,[...(spans.get(r.playerId)??[]),[open.get(r.playerId)!,t]]);open.delete(r.playerId);}
    }
    for(const [id,t] of open)spans.set(id,[...(spans.get(id)??[]),[t,Infinity]]);
    return (playerId:string,at:Date)=>(spans.get(playerId)??[]).some(([a,b])=>at.getTime()>=a&&at.getTime()<b);
  }
  // 웹 공개 랭킹: 순위·이름(닉네임 없으면 갈매미 #앞4자리)·점수·최고 유닛.
  // 게임 요청 처리(트랜잭션 대기열)를 막지 않도록 트랜잭션 없이 읽고, 30초 동안 결과를 재사용합니다
  private rankCache=new Map<string,{at:number;data:Awaited<ReturnType<GameService['loadRanking']>>}>();
  async publicRanking(kind:'DEX'|'TOP'|'WEEK',limit=50){
    const now=this.clock(),hit=this.rankCache.get(kind);
    if(hit&&now.getTime()-hit.at<30_000)return hit.data;
    const data=await this.loadRanking(kind,limit,now);
    this.rankCache.set(kind,{at:now.getTime(),data});
    return data;
  }
  private async loadRanking(kind:'DEX'|'TOP'|'WEEK',limit:number,now:Date){
    const tx=this.db as unknown as Tx;
    {
      const rows=await this.rankRows(tx,kind,now),top=rows.slice(0,limit),ids=top.map(r=>r.playerId);
      const players=await tx.player.findMany({where:{id:{in:ids}},select:{id:true,nickname:true,badge:true}});
      const got=await tx.collectionEntry.findMany({where:{playerId:{in:ids}},select:{playerId:true,characterId:true}});
      const best=new Map<string,Character>();
      // 최고 유닛: 등급이 높고, 같으면 뒤 번호(카톡 랭킹과 같은 기준)
      for(const g of got){const c=this.content.map.get(g.characterId),b=best.get(g.playerId);if(c&&(!b||tiers[c.rarity]>tiers[b.rarity]||(tiers[c.rarity]===tiers[b.rarity]&&c.position>b.position)))best.set(g.playerId,c);}
      return {total:rows.length,generatedAt:now,rows:top.map((r,i)=>{const pl=players.find(x=>x.id===r.playerId),nick=pl?.nickname;return {rank:i+1,name:nick??`갈매미 #${r.playerId.slice(0,4)}`,nick:Boolean(nick),badge:this.badgeEmoji(pl?.badge),score:r.score,best:best.get(r.playerId)??null};})};
    }
  }
  // 쿠폰: 공백 무시하고 코드 비교, 1인 1회(Achievement 키 COUPON_<id>), 기간 확인
  private async coupon(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const site=this.content.economy.homepage?.replace(/^https?:\/\//,'')??'공식 홈페이지';
    const active=this.content.economy.coupons.filter(c=>(!c.from||new Date(c.from)<=now)&&(!c.to||now<new Date(c.to)));
    const typed=query.replace(/\s+/g,'');
    if(!typed)return this.info({text:`🎟 쿠폰\n${active.length?`지금 진행 중: ${active.map(c=>`${c.label} (뽑기권 ${c.tickets}장)`).join(', ')}\n${site} 곳곳에 숨은 쿠폰 조각을 모아 보세요.`:'지금 진행 중인 쿠폰이 없어요.'}\n입력: 쿠폰 코드`,choices:[choice('뽑기'),...menu.slice(1)]},'s-event');
    const c=active.find(x=>x.code.replace(/\s+/g,'')===typed);
    if(!c)throw new GameError(`🎟 맞는 쿠폰이 없어요.\n조각을 모두 모았는지, 순서가 맞는지 확인해 보세요.\n힌트는 ${site} 에 있어요.`);
    const key=`COUPON_${c.id}`;
    if(await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key}}}))throw new GameError(`🎟 이미 받은 쿠폰이에요.\n${c.label} 쿠폰은 한 번만 받을 수 있어요.`);
    await tx.achievement.create({data:{playerId:p.id,key,createdAt:now}});
    await tx.player.update({where:{id:p.id},data:{credits:{increment:c.tickets},revision:{increment:1}}});
    await this.audit(tx,p,'COUPON',{coupon:c.id,tickets:c.tickets});
    return this.info({text:`🎉 쿠폰 사용 완료!\n${c.label}\n🎟 뽑기권 +${c.tickets}장 (지금 ${unlimited(p)?'무제한':`${p.credits+c.tickets}장`})`,choices:[choice('고급 ×10','고급뽑기 10'),choice('뽑기'),...menu.slice(1)]},'s-ticket');
  }
  // ───────── 공개 웹용 읽기 (트랜잭션 없이, 짧게 캐시) ─────────
  private webCache=new Map<string,{at:number;data:unknown}>();
  private async cached<T>(key:string,ms:number,load:()=>Promise<T>):Promise<T>{
    const now=Date.now(),hit=this.webCache.get(key);
    if(hit&&now-hit.at<ms)return hit.data as T;
    const data=await load();this.webCache.set(key,{at:now,data});return data;
  }
  private get reader(){return this.db as unknown as Tx;}
  sourceText(id:string){return this.source(id);}
  // 유닛별로 만난 사람 수(관리자 모드 제외)
  async seenCounts():Promise<Map<string,number>>{
    return this.cached('seen',60_000,async()=>new Map((await this.reader.collectionEntry.groupBy({by:['characterId'],where:{player:{adminMode:false,identity:{not:{startsWith:'review:'}}}},_count:{_all:true}})).map(r=>[r.characterId,r._count._all])));
  }
  // 히든별 최초 발견자(닉네임 없으면 null)와 시각: 조합 기록에서 가장 이른 것
  async hiddenFirsts():Promise<Map<string,{name:string|null;at:Date}>>{
    return this.cached('firsts',60_000,async()=>{
      const want=new Set(this.content.hiddenIds),found=new Map<string,{playerId:string;at:Date}>(),admin=await this.adminWindows(this.reader);
      for(let skip=0;want.size;skip+=1000){
        const batch=await this.reader.auditEvent.findMany({where:{kind:'CRAFT',player:{adminMode:false,identity:{not:{startsWith:'review:'}}}},orderBy:{createdAt:'asc'},skip,take:1000,select:{playerId:true,detail:true,createdAt:true}});
        for(const e of batch){const id=String((e.detail as {recipe?:string}).recipe??'');if(want.has(id)&&!admin(e.playerId,e.createdAt)){want.delete(id);found.set(id,{playerId:e.playerId,at:e.createdAt});}}
        if(batch.length<1000)break;
      }
      const players=await this.reader.player.findMany({where:{id:{in:[...found.values()].map(f=>f.playerId)}},select:{id:true,nickname:true}});
      return new Map([...found].map(([id,f])=>[id,{name:players.find(x=>x.id===f.playerId)?.nickname??null,at:f.at}]));
    });
  }
  // 닉네임 검색(관리자 모드 제외, 최대 10명)
  async searchPlayers(q:string){
    if(!q.trim())return [];
    return (await this.reader.player.findMany({where:{nickname:{contains:q.trim()},adminMode:false,identity:{not:{startsWith:'review:'}}},select:{nickname:true},take:10,orderBy:{nickname:'asc'}})).map(p=>p.nickname!);
  }
  // 공개 프로필: 등급별 수집 수, 순위, 칭호, 최고 유닛, 최초 발견 히든 수
  async webProfile(nickname:string){
    const p=await this.reader.player.findFirst({where:{nickname,adminMode:false,identity:{not:{startsWith:'review:'}}}});
    if(!p)return null;
    const now=this.clock();
    const got=(await this.reader.collectionEntry.findMany({where:{playerId:p.id},select:{characterId:true}})).map(g=>this.content.map.get(g.characterId)).filter((c):c is Character=>Boolean(c));
    const byTier=new Map<string,number>();for(const c of got)byTier.set(c.rarity,(byTier.get(c.rarity)??0)+1);
    const best=[...got].sort((a,b)=>tiers[b.rarity]-tiers[a.rarity]||b.position-a.position)[0]??null;
    const rankOf=async(kind:'DEX'|'TOP'|'WEEK')=>{const rows=await this.rankRows(this.reader,kind,now);const i=rows.findIndex(r=>r.playerId===p.id);return i<0?null:{rank:i+1,total:rows.length,score:rows[i].score};};
    const [dex,top,week]=[await rankOf('DEX'),await rankOf('TOP'),await rankOf('WEEK')];
    const titles=await this.titles(this.reader,p);
    const firsts=[...(await this.hiddenFirsts()).values()].filter(f=>f.name===nickname).length;
    return {nickname,badge:this.badgeEmoji(p.badge),joined:p.createdAt,dex:got.length,byTier,best,dexRank:dex,topRank:top,weekRank:week,titles,firsts};
  }
  private async ranking(tx:Tx,p:Player,mode:string,now:Date):Promise<GameReply>{
    const kind=mode==='최상위'?'TOP':mode==='주간'?'WEEK':'DEX';
    const rows=await this.rankRows(tx,kind,now);
    const unit=kind==='DEX'?'📚':kind==='TOP'?'🗿':'🧩';
    const title=kind==='DEX'?'도감 수집':kind==='TOP'?'최상위 보유':'이번 주 조합';
    const known=await this.known(tx,p);
    const top=rows.slice(0,10);
    const players=await tx.player.findMany({where:{id:{in:top.map(r=>r.playerId)}},select:{id:true,nickname:true,badge:true}});
    const label=(id:string)=>{const x=players.find(y=>y.id===id);return this.named(x?.nickname,x?.badge,`갈매미 #${id.slice(0,4)}`);};
    const medal=(i:number)=>['🥇','🥈','🥉'][i]??`${i+1}위`;
    const items:CardEntry[]=[];
    for(const [i,r] of top.entries()){
      const got=await tx.collectionEntry.findMany({where:{playerId:r.playerId},select:{characterId:true}});
      const best=got.map(g=>this.content.map.get(g.characterId)!).filter(Boolean).sort((a,b)=>tiers[b.rarity]-tiers[a.rarity]||b.position-a.position)[0];
      const shown=best&&isRevealed(best,known);
      items.push({imageId:best?(shown?this.face(best.id,known):'unknown'):'unknown',title:`${medal(i)} ${label(r.playerId)}`,
        description:`${unit} ${r.score}${kind==='DEX'?`/${this.content.characters.length}`:''}${best?` · 최고 ${emblems[best.rarity]} ${shown?best.name:HIDDEN_NAME}`:''}`});
    }
    const mineIndex=rows.findIndex(r=>r.playerId===p.id);
    const mine=p.adminMode?'🛠 관리자 모드 중에는 랭킹에서 빠져요.':mineIndex<0?'아직 기록이 없어요.':`내 순위: ${mineIndex+1}위 / ${rows.length}명 · ${unit} ${rows[mineIndex].score}`;
    const tabs=[['도감','랭킹'],['최상위','랭킹 최상위'],['주간','랭킹 주간']].filter(([n])=>n!==(kind==='DEX'?'도감':kind==='TOP'?'최상위':'주간')).map(([n,m])=>choice(`${n} 랭킹`,m));
    const nick=p.nickname?'':'🏷 닉네임을 정하면 랭킹에 이름이 나와요: 닉네임 갈매미왕';
    const text=`🏆 랭킹 · ${title}\n\n${top.map((r,i)=>`${medal(i)} ${label(r.playerId)} · ${unit} ${r.score}`).join('\n')||'아직 아무도 없어요.'}\n\n${mine}${nick?`\n${nick}`:''}`;
    if(!items.length)return {text,choices:[...tabs,choice('뽑기'),...menu]};
    return {text,cards:{intro:`🏆 랭킹 · ${title}\n${mine}`,items,...(nick?{outro:nick}:{})},choices:[...tabs,choice('닉네임'),choice('도감'),...menu]};
  }

  // ───────── 탐험 ─────────
  private async activeExpedition(tx:Tx,p:Player){
    return tx.expedition.findFirst({where:{playerId:p.id,claimedAt:null}});
  }
  private effectText(key:string,level:1|2){
    const fx=this.content.economy.expedition.effects as Record<string,Record<string,unknown>>;
    const i=level-1,v=(k:string)=>(fx[key][k] as number[])[i];
    return key==='BASIC'?`새우gang +${v('snackPercent')}%`:key==='GOLD'?`${v('doublePercent')}% 확률로 새우gang 2배`:key==='DARK'?`${v('minutes')}분 만에 귀환`:
      key==='SEA'?`${v('commonPercent')}% 확률로 흔함 1마리`:key==='BLOSSOM'?`${v('uncommonPercent')}% 확률로 안흔함 1마리`:key==='COSMOS'?`${v('ticketPercent')}% 확률로 뽑기권 1장`:`마리당 새우gang +${v('snackPerUnit')}`;
  }
  private planText(plan:ExpeditionPlan){
    const syn=plan.synergies.map(s=>`✨ ${s.label} ×${s.count} (${s.level}단계) · ${this.effectText(s.key,s.level)}`);
    if(plan.countBonus)syn.push(`✨ 같은 갈매미 ×${plan.countBonus.count} · 갈매미 ${['','한','두','세','네','다섯'][Math.min(plan.countBonus.count,5)]} 마리 +${plan.countBonus.percent}%`);
    return `🧭 ${plan.size}마리 · ⏱ ${duration(plan.minutes*60000)} · 🍤 예상 ${expectedSnacks(plan)}개\n${syn.length?syn.join('\n'):'시너지 없음 (같은 시너지 2마리부터)'}`;
  }
  // 탐험대 카드: 같은 유닛은 한 장(×n), 최대 10장
  private partyCards(unitIds:string[],button?:(c:Character)=>Choice):CardEntry[]{
    const e=this.content.economy.expedition;
    return [...materialCounts(unitIds)].slice(0,10).map(([id,n])=>{const c=this.content.map.get(id)!;
      return {imageId:this.cardImage(id,new Set()),title:`${emblems[c.rarity]} ${c.name}${n>1?` ×${n}`:''}`,description:`${rarityNames[c.rarity]} · ${e.effects[c.synergy!].label} 시너지`,...(button?{buttons:[button(c)]}:{})};});
  }
  private async expeditionStatus(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    const e=this.content.economy.expedition;
    if(active){
      const plan=active.plan as unknown as ExpeditionPlan;
      const units=[...materialCounts(active.unitIds as string[])].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
      const left=active.endsAt.getTime()-now.getTime();
      const head=left>0?`🧭 탐험 중 · ${duration(left)} 뒤 귀환`:'✅ 탐험대가 돌아왔어요! 보상을 받으세요.';
      return {text:`${head}\n\n${units}\n\n${this.planText(plan)}`,cards:{intro:head,items:this.partyCards(active.unitIds as string[]),outro:this.planText(plan)},choices:[...(left>0?[]:[choice('탐험 보상 받기','탐험보상받기')]),...menu]};
    }
    const owned=await tx.ownedCharacter.groupBy({by:['characterId'],where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},_count:{_all:true}});
    if(!owned.length)return this.info({text:`🧭 탐험\n최상위 갈매미(제한·초월·영원·불멸)를 보내면 ${duration(e.minutes*60000)} 뒤 새우gang을 가져와요.\n1마리당 🍤 ${e.snackPerUnit} · 최대 ${e.maxParty}마리\n\n같은 시너지끼리 보내면 보너스!\n아직 보낼 갈매미가 없어요. 전설을 모아 최상위를 만들어 보세요.`,choices:[choice('조합목록 제한'),choice('시너지'),...menu]},'explore');
    return this.partyBuilder(tx,p,'',now);
  }
  // 탐험대 편성: 넣은 유닛은 서버(partyDraft)에 저장하고, 카드 버튼은 "편성추가 이름"·"편성빼기 이름"처럼 짧게 보냄.
  // (카카오는 카드 넘기기의 버튼 명령 합계가 길면(실기기 854바이트) 응답을 표시하지 않음)
  // "탐험편성 A, B ×2"를 직접 입력하면 그 구성으로 바꿉니다. "탐험편성 쪽 N"은 후보 쪽 넘기기
  private async partyBuilder(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    if(await this.activeExpedition(tx,p))return this.expeditionStatus(tx,p,now);
    const pageArg=query.match(/^쪽\s*(\d+)$/u);
    if(query&&!pageArg){
      const pool=this.content.characters.filter(c=>c.synergy),ids:string[]=[];
      for(const {name,count} of this.parseUnits(query)){
        const c=this.resolve(name,pool,'탐험편성',new Set());
        if(!('id' in c))return c;
        for(let i=0;i<count;i++)ids.push(c.id);
      }
      await this.saveDraft(tx,p,ids);
    }
    return this.partyView(tx,p,now,Number(pageArg?.[1]??1));
  }
  private draftIds(p:Player){return p.partyDraft?p.partyDraft.split(',').filter(id=>this.content.map.has(id)):[];}
  private async saveDraft(tx:Tx,p:Player,ids:string[]){
    const e=this.content.economy.expedition;
    if(ids.length>e.maxParty)throw new GameError(`탐험대는 최대 ${e.maxParty}마리예요.`);
    const have=await this.topStock(tx,p);
    for(const [id,n] of materialCounts(ids))if((have.get(id)??0)<n)throw new GameError(`${this.content.map.get(id)!.name}은(는) ${have.get(id)??0}마리만 보낼 수 있어요.`);
    p.partyDraft=ids.join(',');
    await tx.player.update({where:{id:p.id},data:{partyDraft:p.partyDraft}});
  }
  private async topStock(tx:Tx,p:Player){
    const owned=await tx.ownedCharacter.groupBy({by:['characterId'],where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},_count:{_all:true}});
    return new Map(owned.map(o=>[o.characterId,o._count._all]));
  }
  private async partyDraft(tx:Tx,p:Player,action:string,query:string,now:Date):Promise<GameReply>{
    if(await this.activeExpedition(tx,p))return this.expeditionStatus(tx,p,now);
    const ids=this.draftIds(p);
    if(action==='비우기'){await this.saveDraft(tx,p,[]);return this.partyView(tx,p,now,1);}
    if(action==='출발'){
      if(!ids.length)throw new GameError('편성된 갈매미가 없어요. 카드의 ➕ 추가로 넣어 주세요.');
      return this.sendExpedition(tx,p,[...materialCounts(ids)].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', '),now);
    }
    // "편성추가 이름 [수]" / "편성빼기 이름"
    const m=query.match(/^(.+?)(?:\s*[x×]?\s*(\d+))?$/u);
    if(!m)throw new GameError('예: 편성추가 태초의 갈매미맨 · 편성추가 태초의 갈매미맨 3');
    const c=this.resolve(m[1].trim(),this.content.characters.filter(c=>c.synergy),`편성${action}`,new Set());
    if(!('id' in c))return c;
    const n=Number(m[2]??1);
    if(!Number.isSafeInteger(n)||n<1)throw new GameError('수는 1 이상이에요.');
    if(action==='추가')await this.saveDraft(tx,p,[...ids,...Array(n).fill(c.id)]);
    else{const i=ids.lastIndexOf(c.id);if(i<0)throw new GameError(`${c.name}은(는) 편성에 없어요.`);await this.saveDraft(tx,p,ids.filter((_,j)=>j!==i));}
    return this.partyView(tx,p,now,1);
  }
  private async partyView(tx:Tx,p:Player,now:Date,pageNo:number):Promise<GameReply>{
    const e=this.content.economy.expedition,none=new Set<string>();
    const have=await this.topStock(tx,p);
    if(!have.size)return this.expeditionStatus(tx,p,now);
    // 저장된 편성 중 지금 없는 유닛은 빼고 봅니다
    const counted=new Map<string,number>(),party:Character[]=[];
    for(const id of this.draftIds(p)){const k=(counted.get(id)??0)+1;if(k<=(have.get(id)??0)&&party.length<e.maxParty){counted.set(id,k);party.push(this.content.map.get(id)!);}}
    const label=(list:Character[])=>[...materialCounts(list.map(c=>c.id))].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
    const used=materialCounts(party.map(c=>c.id));
    const full=party.length>=e.maxParty;
    const cards:CardEntry[]=[];
    // 넣은 유닛 카드(빼기)
    for(const [id,n] of used){const c=this.content.map.get(id)!;
      cards.push({imageId:this.cardImage(id,none),title:`✅ ${emblems[c.rarity]} ${c.name}${n>1?` ×${n}`:''}`,description:`편성됨 · ${e.effects[c.synergy!].label} 시너지`,buttons:[choice('빼기',`편성빼기 ${c.name}`)]});}
    // 후보 카드(추가): 남은 자리만큼 쪽으로 나눔, 같은 시너지 많은 순
    const syn=new Map<string,number>();for(const c of party)syn.set(c.synergy!,(syn.get(c.synergy!)??0)+1);
    const left=[...have.keys()].map(id=>this.content.map.get(id)!).filter(c=>(have.get(c.id)??0)-(used.get(c.id)??0)>0)
      .sort((a,b)=>(syn.get(b.synergy!)??0)-(syn.get(a.synergy!)??0)||tiers[b.rarity]-tiers[a.rarity]||a.position-b.position);
    const candidates:CardEntry[]=left.map(c=>{
      const rest=(have.get(c.id)??0)-(used.get(c.id)??0);
      return {imageId:this.cardImage(c.id,none),title:`${emblems[c.rarity]} ${c.name}`,description:`${rarityNames[c.rarity]} · ${e.effects[c.synergy!].label} 시너지 · 남은 ${rest}마리`,buttons:[choice('➕ 추가',`편성추가 ${c.name}`)]};
    });
    // 후보 카드 수: 10장 - 넣은 유닛 카드, 그리고 카카오 글자 예산 안에서
    const size=Math.max(1,Math.min(10-cards.length,cardsPerPage(candidates,10,cards.reduce((s,c)=>s+cardCost(c),0))));
    const {page,pages,start}=full||!left.length?{page:1,pages:1,start:0}:this.page(String(pageNo),left.length,size);
    if(!full)cards.push(...candidates.slice(start,start+size));
    const plan=party.length?planExpedition(this.content,party.map(c=>c.id)):undefined;
    const head=`🧭 탐험대 편성 ${party.length}/${e.maxParty}${party.length?`\n${label(party)}`:'\n카드의 ➕ 추가로 한 마리씩 넣어요.'}${pages>1?` (${page}/${pages})`:''}`;
    const info=plan?this.planText(plan):`1마리당 🍤 ${e.snackPerUnit} · ⏱ ${duration(e.minutes*60000)} · 같은 시너지 ${e.levels[0]}마리부터 보너스`;
    const nav=[...(page>1?[choice('이전',`탐험편성 쪽 ${page-1}`)]:[]),...(page<pages?[choice('다음 쪽',`탐험편성 쪽 ${page+1}`)]:[])];
    return {text:`${head}\n\n${info}\n\n${cards.map(c=>c.title).join('\n')}`,cards:{intro:head,items:cards,outro:`${info}${full?'\n탐험대가 꽉 찼어요.':'\n여러 마리는 입력: 편성추가 이름 3'}`},
      choices:[...(party.length?[choice(`🚀 출발 (${party.length}마리)`,'편성출발'),choice('비우기','편성비우기')]:[]),...nav,choice('자동 편성','탐험보내기 자동'),choice('시너지 안내','시너지'),...menu]};
  }
  private synergyGuide(){
    const e=this.content.economy.expedition,fx=e.effects;
    const rows=[
      `기본 (${fx.BASIC.concept}): 새우gang +${fx.BASIC.snackPercent[0]}% / +${fx.BASIC.snackPercent[1]}%`,
      `황금 (${fx.GOLD.concept}): ${fx.GOLD.doublePercent[0]}% / ${fx.GOLD.doublePercent[1]}% 확률로 새우gang 2배`,
      `암흑 (${fx.DARK.concept}): 탐험 시간 ${fx.DARK.minutes[0]}분 / ${fx.DARK.minutes[1]}분`,
      `바다 (${fx.SEA.concept}): ${fx.SEA.commonPercent[0]}% / ${fx.SEA.commonPercent[1]}% 확률로 흔함 1마리`,
      `벚꽃 (${fx.BLOSSOM.concept}): ${fx.BLOSSOM.uncommonPercent[0]}% / ${fx.BLOSSOM.uncommonPercent[1]}% 확률로 안흔함 1마리`,
      `우주 (${fx.COSMOS.concept}): ${fx.COSMOS.ticketPercent[0]}% / ${fx.COSMOS.ticketPercent[1]}% 확률로 뽑기권 1장`,
      `갈 의복 (${fx.OUTFIT.concept}): 마리당 새우gang +${fx.OUTFIT.snackPerUnit[0]} / +${fx.OUTFIT.snackPerUnit[1]}`,
      `같은 갈매미 여러 마리: ${Object.entries(e.countBonusPercent).map(([n,v])=>`${n}마리 +${v}%`).join(' · ')}`
    ];
    return `🧭 탐험 시너지\n같은 시너지 ${e.levels[0]}마리 이상 = 1단계 / ${e.levels[1]}마리 이상 = 2단계 (같은 갈매미 중복 포함)\n여러 시너지가 함께 발동하면 효과를 모두 더해요.\n\n${rows.join('\n')}`;
  }
  private async sendExpedition(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    if(await this.activeExpedition(tx,p))throw new GameError('🧭 이미 탐험 중이에요. 탐험에서 확인해 보세요.');
    const e=this.content.economy.expedition;
    const available=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},orderBy:{id:'asc'}});
    if(!available.length)throw new GameError('보낼 수 있는 최상위 갈매미가 없어요.');
    let selected:typeof available=[];
    if(!query||/^(자동|전체)$/.test(query.trim())){
      // 같은 시너지끼리 묶이도록 큰 무리부터 채웁니다.
      const groups=new Map<string,typeof available>();
      for(const o of available){const key=this.content.map.get(o.characterId)!.synergy!;groups.set(key,[...(groups.get(key)??[]),o]);}
      for(const group of [...groups.values()].sort((a,b)=>b.length-a.length))
        selected.push(...group.sort((a,b)=>a.characterId.localeCompare(b.characterId)));
      selected=selected.slice(0,e.maxParty);
    }else{
      const pool=this.content.characters.filter(c=>c.synergy);
      const parts=this.parseUnits(query);
      for(const [i,{name,count}] of parts.entries()){
        const c=this.resolve(name,pool,'탐험보내기',new Set(),c=>`탐험보내기 ${parts.map((u,j)=>`${j===i?c.name:u.name}${u.count>1?` ×${u.count}`:''}`).join(', ')}`);
        if(!('id' in c))return c;
        const free=available.filter(o=>o.characterId===c.id&&!selected.includes(o));
        if(free.length<count)throw new GameError(`${c.name}은(는) 지금 ${free.length}마리만 보낼 수 있어요.`);
        selected.push(...free.slice(0,count));
      }
    }
    if(selected.length>e.maxParty)throw new GameError(`한 번에 ${e.maxParty}마리까지만 보낼 수 있어요.`);
    const plan=planExpedition(this.content,selected.map(o=>o.characterId));
    const units=[...materialCounts(selected.map(o=>o.characterId))].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
    return this.issue(tx,p,'EXPEDITION',{ids:selected.map(o=>o.id)},now,`🧭 탐험대 편성\n${units}\n\n${this.planText(plan)}`,undefined,undefined,
      {intro:`🚀 탐험 출발 준비\n\n${this.planText(plan)}`,items:this.partyCards(selected.map(o=>o.characterId))});
  }
  private async claimExpedition(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    if(!active)return this.info({text:'🧭 진행 중인 탐험이 없어요\n탐험에서 보내 보세요.',choices:[choice('탐험'),...menu]},'explore');
    if(active.endsAt>now)return this.expeditionStatus(tx,p,now);
    const marked=await tx.expedition.updateMany({where:{id:active.id,claimedAt:null},data:{claimedAt:now}});
    if(marked.count!==1)throw new GameError('이미 받은 탐험 보상이에요.');
    const plan=active.plan as unknown as ExpeditionPlan;
    const rolled=rollExpedition(this.content,plan,this.roll);
    await tx.ownedCharacter.updateMany({where:{expeditionId:active.id,playerId:p.id,status:'EXPEDITION'},data:{status:'AVAILABLE',expeditionId:null}});
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{snack:true,credits:true}});
    const snacks=Math.max(0,Math.min(rolled.snacks,2_000_000_000-account.snack));
    await tx.player.update({where:{id:p.id},data:{snack:{increment:snacks},credits:{increment:rolled.tickets},revision:{increment:1},...(!p.partyDraft?{partyDraft:(active.unitIds as string[]).join(',')}:{})}});
    const known=await this.known(tx,p);
    const lines=[`🍤 새우gang +${snacks}${rolled.doubled?' (황금 시너지 2배!)':''}`];
    let imageId:string|undefined;
    const joined:CardEntry[]=[];
    for(const id of [rolled.commonId,rolled.uncommonId].filter((x):x is string=>Boolean(x))){
      const owned=await this.grant(tx,p,id,'EXPEDITION',now);
      const c=this.content.map.get(id)!;
      const a=acquisitionCard(this.content,c,'EXPEDITION',owned.receipt,known);
      joined.push({imageId:this.cardImage(id,known),title:a.title,description:a.description,buttons:[choice('도감 보기',`도감 ${c.name}`)]});
      lines.push(`${owned.receipt.first?'🆕':'🐦'} ${emblems[c.rarity]} ${c.name} 합류!${owned.receipt.snacks?` (🍤 +${owned.receipt.snacks})`:''}`);
      if(owned.receipt.achievement)lines.push('',achievementText(owned.receipt.achievement));
      if(owned.receipt.first&&!imageId)imageId=this.image(id,known);
    }
    if(rolled.tickets)lines.push(`🎟 뽑기권 +${rolled.tickets}`);
    const result={...rolled,snacks};
    await tx.expedition.update({where:{id:active.id},data:{result:json(result)}});
    await this.audit(tx,p,'EXPEDITION_CLAIM',{expeditionId:active.id,...result});
    const missions=await this.progress(tx,p,'EXPEDITION',1,now);
    const next=await this.nextStep(tx,p,now,[choice('다시 보내기','탐험보내기 자동'),choice('교환')]);
    const five=plan.countBonus&&plan.countBonus.count>=5?'\n\n갈매미 한 마리…\n갈매미 두 마리…\n갈매미 세 마리…\n갈매미 네 마리…\n(정적)\n갈매미 다섯 마리.\n앙~~~~ 갈매미맨이야!!!!':'';
    // 카드: 합류한 유닛, 없으면 탐험대 대표 유닛 1장
    const leader=this.content.map.get((active.unitIds as string[])[0])!;
    const items=joined.length?joined:[{imageId:this.cardImage(leader.id,known),title:'🧭 탐험대 귀환!',description:`${emblems[leader.rarity]} ${leader.name}${plan.size>1?` 외 ${plan.size-1}마리`:''}\n🍤 새우gang +${snacks}`}];
    const below=[...lines,...missions].join('\n');
    return {text:mask(this.content,`🧭 탐험대 귀환!${five}\n\n${lines.join('\n')}\n\n${[...missions,next.line].join('\n')}`,known),imageId,
      cards:{intro:mask(this.content,`🧭 탐험대 귀환!${five}`,known),items:items.map(i=>this.maskCard(i,known)),outro:mask(this.content,report(below,next.line),known)},choices:next.choices};
  }

  // ───────── 확정 · 취소 ─────────
  private async cancel(tx:Tx,p:Player,token:string):Promise<GameReply>{
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('내 확인 요청을 찾지 못했어요. 처음부터 다시 해 주세요.');
    if(action.result)return {text:'이미 끝난 요청이에요. 다시 처리하거나 취소하지 않아요.',choices:menu};
    await tx.pendingAction.delete({where:{token}});
    return {text:'취소했어요. 갈매미와 재화는 쓰지 않았어요.',choices:menu};
  }
  private async confirm(tx:Tx,p:Player,token:string,now:Date):Promise<GameReply>{
    if(!/^[a-f0-9]{16}$/.test(token))throw new GameError('확인 카드의 버튼을 눌러 주세요.');
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청이 아니에요. 처음부터 다시 해 주세요.');
    if(action.result)return action.result as unknown as GameReply;
    if(action.expiresAt<=now){
      const pay=action.payload as ActionPayload,c=this.content.map.get(pay.recipeId??pay.characterId??'');
      const retry=action.kind==='CRAFT'&&c?(c.rarity==='HIDDEN'?choice('다시 하기','조합 고르기'):choice('다시 하기',`조합 ${c.name}${(pay.times??1)>1?` ${pay.times}`:''}`))
        :action.kind==='RECEIVE'&&c?choice('다시 하기',`교환 ${c.name}`):action.kind==='PEANUT'&&c?choice('다시 하기',`땅콩떼기 ${c.name}`)
        :action.kind==='SELL'?choice('다시 하기','팔기'):action.kind==='EXPEDITION'?choice('다시 하기','탐험'):choice('다시 하기','상점');
      return this.info({text:'⏳ 10분이 지나 취소됐어요\n아무것도 쓰지 않았어요. 다시 해 주세요.',choices:[retry,...menu]},'s-timeout');
    }
    if(action.revision!==p.revision)throw new GameError('그 사이 보유 상태가 바뀌었어요. 다시 해 주세요.');
    const revised=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{revision:{increment:1}}});
    if(revised.count!==1)throw new GameError('다른 요청이 먼저 처리됐어요. 다시 해 주세요.');
    const payload=action.payload as ActionPayload;
    const known=await this.known(tx,p);
    let result:GameReply;
    if(action.kind==='RECEIVE'){
      const c=this.content.map.get(payload.characterId??'');
      if(!c||(c.rarity!=='COMMON'&&c.rarity!=='UNCOMMON'&&c.rarity!=='SPECIAL'))throw new GameError('교환할 수 없는 갈매미예요.');
      const price=this.exchangePrice(c);
      if(price.peanut&&p.peanut<price.peanut)throw new GameError('🥜 땅콩이 부족해요.');
      if(price.snack&&p.snack<price.snack)throw new GameError('🍤 새우gang이 부족해요.');
      const cost=price.snack??0;
      await tx.player.update({where:{id:p.id},data:price.peanut?{peanut:{decrement:price.peanut}}:{snack:{decrement:cost}}});
      const owned=await this.grant(tx,p,c.id,'EXCHANGE',now);
      const next=await this.nextStep(tx,p,now,[choice('더 교환','교환')]);
      result={text:`🍤 → ${acquisitionText(this.content,c,'EXCHANGE',owned.receipt,known)}\n\n${next.line}`,...(owned.receipt.first?{imageId:this.image(c.id,known)}:{}),
        ...this.acquired(c,'EXCHANGE',owned.receipt,known,[],next.line),choices:next.choices};
      await this.audit(tx,p,'EXCHANGE',{token,characterId:c.id,ownedId:owned.id,...(price.peanut?{peanut:-price.peanut,peanutAfter:p.peanut-price.peanut}:{delta:-cost,balanceAfter:p.snack-cost})});
    }else if(action.kind==='PEANUT'){
      const c=this.content.map.get(payload.characterId??'');
      if(!c||!this.peanutable(c))throw new GameError('땅콩을 뗄 수 없는 갈매미예요.');
      const unit=await tx.ownedCharacter.findFirst({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:false},orderBy:{id:'asc'}});
      if(unit?.reincarnation)throw new GameError('환생 계보는 땅콩떼기로 소모할 수 없어요. 조합으로 이어 키워 주세요.');
      if(!unit)throw new GameError(`${c.name}이(가) 없어요.\n(보호 중이거나 탐험 중인 갈매미는 안 돼요)`);
      await tx.ownedCharacter.update({where:{id:unit.id},data:{status:'CONSUMED',consumedAt:now}});
      await tx.player.update({where:{id:p.id},data:{peanut:{increment:1}}});
      const neutral=this.content.characters.find(x=>x.rarity==='NEUTRAL')!;
      const owned=await this.grant(tx,p,neutral.id,'PEANUT',now);
      const next=await this.nextStep(tx,p,now,[choice('또 떼기','땅콩떼기')]);
      result={text:`🥜 ${c.name}의 땅콩을 뗐다…\n${acquisitionText(this.content,neutral,'EXCHANGE',owned.receipt,known)}\n🥜 땅콩 +1 (지금 ${p.peanut+1}개)\n\n${next.line}`,
        ...this.acquired(neutral,'PEANUT',owned.receipt,known,[`🥜 땅콩 +1 (지금 ${p.peanut+1}개)`],next.line,`🥜 ${c.name}의 땅콩을 뗐다…`),choices:next.choices};
      await this.audit(tx,p,'PEANUT',{token,from:c.id,ownedId:unit.id,neutralOwnedId:owned.id,peanutAfter:p.peanut+1});
    }else if(action.kind==='SELL'){
      const ids=payload.ids??[];
      const units=await tx.ownedCharacter.findMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',locked:false}});
      if(units.some(u=>u.reincarnation>0))throw new GameError('환생 계보는 판매할 수 없어요. 조합으로 이어 키워 주세요.');
      if(units.length!==ids.length)throw new GameError('팔 갈매미 상태가 바뀌었어요. 다시 해 주세요.');
      const stock=await this.stock(tx,p);
      for(const [id,n] of materialCounts(units.map(u=>u.characterId)))if(n>this.sellable(this.content.map.get(id)!,stock))throw new GameError('1마리는 남겨야 해요. 다시 해 주세요.');
      const total=units.reduce((a,u)=>a+this.sellPrice(this.content.map.get(u.characterId)!),0);
      await tx.ownedCharacter.updateMany({where:{id:{in:ids}},data:{status:'CONSUMED',consumedAt:now}});
      await tx.player.update({where:{id:p.id},data:{snack:{increment:total}}});
      await this.audit(tx,p,'SELL',{token,ids,snacks:total});
      result=this.info({text:`🍤 팔기 완료!\n${ids.length}마리 → 새우gang +${total}\n지금 🍤 ${p.snack+total}개`,choices:[choice('더 팔기','팔기'),choice('상점'),...menu]},'s-sell');
    }else if(action.kind==='SHOP_TICKETS'){
      const t=this.content.economy.shop.tickets;
      if(p.snack<t.snacks)throw new GameError('🍤 새우gang이 부족해요.');
      await tx.player.update({where:{id:p.id},data:{snack:{decrement:t.snacks},credits:{increment:t.amount}}});
      await this.audit(tx,p,'SHOP',{token,item:'TICKETS',snacks:-t.snacks,tickets:t.amount});
      result=this.info({text:`🛒 뽑기권 +${t.amount}!\n🍤 -${t.snacks} · 지금 🎟 ${p.credits+t.amount}장 · 🍤 ${p.snack-t.snacks}개`,choices:[choice('뽑기'),choice('상점'),...menu.slice(2)]},'s-ticket');
    }else if(action.kind==='SHOP_PEANUT_TICKETS'){
      const t=this.content.economy.peanutShop.tickets;
      if(p.peanut<1)throw new GameError('🥜 땅콩이 없어요.');
      await tx.player.update({where:{id:p.id},data:{peanut:{decrement:1},credits:{increment:t}}});
      await this.audit(tx,p,'SHOP',{token,item:'PEANUT_TICKETS',peanut:-1,tickets:t});
      result=this.info({text:`🎟 뽑기권 +${t}!\n🥜 -1 · 지금 🎟 ${p.credits+t}장 · 🥜 ${p.peanut-1}개`,choices:[...this.drawChoices(p,p.credits+t),choice('땅콩 상점','상점 땅콩'),...menu.slice(2)]},'s-ticket');
    }else if(action.kind==='SHOP_BADGE'){
      const b=this.content.economy.peanutShop.badges.find(x=>x.id===payload.badge);
      if(!b)throw new GameError('그런 장식은 없어요.');
      if(await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key:`BADGE_${b.id}`}}}))throw new GameError('이미 가지고 있는 장식이에요.');
      if(p.peanut<b.cost)throw new GameError('🥜 땅콩이 부족해요.');
      await tx.player.update({where:{id:p.id},data:{peanut:{decrement:b.cost},badge:b.id}});
      await tx.achievement.create({data:{playerId:p.id,key:`BADGE_${b.id}`,createdAt:now}});
      await this.audit(tx,p,'SHOP',{token,item:'BADGE',badge:b.id,peanut:-b.cost});
      result=this.info({text:`🎀 ${b.label}을(를) 달았어요!\n${this.named(p.nickname,b.id,'(닉네임을 정하면 이름 옆에 보여요)')}\n🥜 -${b.cost} · 지금 🥜 ${p.peanut-b.cost}개`,choices:[choice('장식 보기','상점 장식'),choice('랭킹'),...menu]},'s-rank');
    }else if(action.kind==='SHOP_SKIP'){
      const cost=this.content.economy.shop.expeditionSkip.snacks;
      const active=await this.activeExpedition(tx,p);
      if(!active||active.endsAt<=now)throw new GameError('바로 돌아오게 할 탐험이 없어요.');
      if(p.snack<cost)throw new GameError('🍤 새우gang이 부족해요.');
      await tx.player.update({where:{id:p.id},data:{snack:{decrement:cost}}});
      await tx.expedition.update({where:{id:active.id},data:{endsAt:now}});
      await this.audit(tx,p,'SHOP',{token,item:'EXPEDITION_SKIP',snacks:-cost,expeditionId:active.id});
      result=this.info({text:`🧭 탐험대가 바로 돌아왔어요!\n🍤 -${cost} · 보상을 받으세요.`,choices:[choice('탐험 보상 받기','탐험보상받기'),choice('상점'),...menu]},this.cardImage((active.unitIds as string[])[0],new Set()));
    }else if(action.kind==='CRAFT'){
      const recipe=this.content.recipes.find(r=>r.resultId===payload.recipeId);
      if(!recipe)throw new GameError('조합식이 없어요.');
      result=await this.craft(tx,p,recipe,payload.ids??[],payload.times??1,now,known,token);
    }else if(action.kind==='EXPEDITION'){
      if(await this.activeExpedition(tx,p))throw new GameError('이미 탐험 중이에요.');
      const ids=payload.ids??[];
      const units=await tx.ownedCharacter.findMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}}});
      if(units.length!==ids.length)throw new GameError('보낼 갈매미 상태가 바뀌었어요. 다시 편성해 주세요.');
      const unitIds=ids.map(id=>units.find(u=>u.id===id)!.characterId);
      const plan=planExpedition(this.content,unitIds);
      const ends=new Date(now.getTime()+plan.minutes*60000);
      const expedition=await tx.expedition.create({data:{playerId:p.id,unitIds:json(unitIds),plan:json(plan),startedAt:now,endsAt:ends}});
      const moved=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE'},data:{status:'EXPEDITION',expeditionId:expedition.id}});
      if(moved.count!==ids.length)throw new GameError('탐험 출발이 겹쳤어요. 다시 해 주세요.');
      // 실제 출발한 편성을 보존한다. 보상 수령 후 같은 중복 수량으로 다시 보낼 수 있다.
      await tx.player.update({where:{id:p.id},data:{partyDraft:unitIds.join(',')}});
      result={text:`🧭 탐험대 출발! (${ids.length}마리)\n⏱ ${duration(plan.minutes*60000)} 뒤 돌아와요.\n돌아오면 '탐험 보상 받기'를 눌러 주세요. 🍤 예상 ${expectedSnacks(plan)}개`,
        cards:{intro:`🧭 탐험대 출발! ⏱ ${duration(plan.minutes*60000)} 뒤 귀환 · 🍤 예상 ${expectedSnacks(plan)}개`,items:this.partyCards(unitIds),outro:"돌아오면 '탐험 보상 받기'를 눌러 주세요."},choices:[choice('탐험'),...menu]};
      await this.audit(tx,p,'EXPEDITION_SEND',{token,expeditionId:expedition.id,ownedIds:ids,unitIds,minutes:plan.minutes});
    }else throw new GameError('지원하지 않는 요청이에요.');
    await tx.pendingAction.update({where:{token},data:{result:json(result)}});
    return result;
  }

  // ───────── 명령 분기 ─────────
  // 바로가기 버튼 정리: 지금 못 쓰는 것·지금 화면과 같은 것은 빼고, 뽑기권을 받을 수 있으면 앞에 둡니다
  private async tidy(tx:Tx,p:Player,message:string,now:Date,reply:GameReply):Promise<GameReply>{
    if(!reply.choices?.length)return reply;
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id}});
    const here=message.trim().replace(/\s+/g,' ');
    const canClaim=!this.claimWait(account,now);
    const ready=await this.craftable(tx,account);
    const explore=Boolean(await this.activeExpedition(tx,account))||await this.hasTop(tx,account);
    let choices=reply.choices.filter(c=>{
      // 같은 화면 버튼은 빼되, 뽑기 '한 번 더'처럼 반복하는 행동은 남깁니다
      if(c.message===here&&!/^(하급|중급|고급)뽑기/.test(here))return false;
      if(c.message==='뽑기권받기')return canClaim;
      if(c.message==='조합가능')return ready>0;
      if(c.message==='탐험')return explore;
      return true;
    });
    // 바로가기는 5개까지: 뽑기권 받기(받을 수 있을 때) → 이 화면에 맞는 것(앞쪽) → 튜토리얼(남았을 때)
    const tutorialLeft=this.tutorialLeft(account)&&here!=='튜토리얼';
    const specific=choices.filter(c=>c.message!=='뽑기권받기'&&c.message!=='튜토리얼');
    const room=5-(canClaim?1:0)-(tutorialLeft?1:0);
    choices=[...(canClaim?[choice('🎟 뽑기권 받기','뽑기권받기')]:[]),...specific.slice(0,room),...(tutorialLeft?[choice('📘 튜토리얼','튜토리얼')]:[])];
    return {...reply,choices};
  }
  private async dispatch(tx:Tx,p:Player,message:string,now:Date,button?:ButtonAction):Promise<GameReply>{
    const trimmed=message.trim().replace(/^[!/]/,'');
    const parts=trimmed.split(/\s+/);
    const [command,...args]=parts;
    const query=args.join(' ');
    const compact=trimmed.replace(/\s+/g,'');
    if(button){
      const visible=parts.join(' ');
      if(button.action==='confirm'&&['조합 확정','교환 확정','탐험 확정','구매 확정','땅콩떼기 확정','팔기 확정'].includes(visible))return this.confirm(tx,p,button.token,now);
      if(button.action==='cancel'&&visible==='취소')return this.cancel(tx,p,button.token);
      throw new GameError('버튼 요청이 맞지 않아요. 미리보기를 다시 열어 주세요.');
    }
    if(['조합 확정','교환 확정','탐험 확정','구매 확정','땅콩떼기 확정','팔기 확정','취소'].includes(parts.join(' ')))return {text:'미리보기의 진행 또는 취소 항목을 직접 눌러 주세요.',choices:menu};
    if(['받기','뽑기권받기','뽑기권','충전'].includes(compact))return this.claim(tx,p,now);
    const gm=compact.match(/^(?:(하급|중급|고급)뽑기|뽑기(하급|중급|고급))(\d+)?$/);
    if(gm){
      const times=Number(gm[3]??1);
      if(!Number.isSafeInteger(times)||times<1||times>50)throw new GameError('뽑기는 한 번에 1~50회예요. 예: 하급뽑기 10');
      return this.draw(tx,p,gachaWords[gm[1]??gm[2]],times,now);
    }
    if(compact==='뽑기')return this.gachaMenu(tx,p,now);
    if(command==='확률')return {text:`🎲 뽑기 확률\n\n${this.probabilityText()}\n\n같은 등급 안에선 모두 같은 확률이에요.`,cards:{intro:'🎲 뽑기 확률',items:this.gachaCards(p),outro:'같은 등급 안에선 모두 같은 확률이에요.'},choices:[choice('뽑기'),...menu.slice(1)]};
    if(command==='보상'){
      const r=this.content.economy.rewards,known=await this.known(tx,p);
      const items=tierOrder.filter(t=>t!=='COMMON').map(t=>({imageId:this.gradeImage(t,known),title:`${emblems[t]} ${rarityNames[t]}`,description:`첫 발견 🍤 ${r[t].first}\n중복 🍤 ${r[t].duplicate}`}));
      return {text:rewardGuide(this.content),cards:{intro:'🎁 획득 보상 (새우gang)',items,outro:`흔함은 보상이 없어요.\n새우gang은 상점에서 흔함·안흔함·뽑기권으로 바꿔요.`},choices:[choice('상점'),...menu]};
    }
    if(command==='교환'){
      // 흔함·안흔함을 골라 새우gang으로 받습니다. "교환 안흔함"이면 안흔함 목록
      const tierArg=args[0]==='안흔함'?'UNCOMMON':args[0]==='흔함'?'COMMON':args[0]==='특별'?'SPECIAL':undefined;
      if(!query||/^\d+$/.test(query)||(tierArg&&args.length<=2&&(!args[1]||/^\d+$/.test(args[1])))){
        const reply=await this.browse(tx,p,'교환',query);
        const note=tierArg==='SPECIAL'?`✦ 특별 1마리 🥜 ${this.content.economy.peanutShop.special} · 지금 🥜 ${p.peanut}개`:`🍤 흔함 ${this.content.economy.exchange.snackCost} · 안흔함 ${this.content.economy.exchange.uncommonCost} · 지금 🍤 ${p.snack}개`;
        return {...reply,text:`${reply.text}\n\n${note}`,...(reply.cards?{cards:{...reply.cards,outro:note}}:{}),choices:[choice(tierArg==='UNCOMMON'?'흔함 교환':'안흔함 교환',tierArg==='UNCOMMON'?'교환 흔함':'교환 안흔함'),choice('상점'),...(reply.choices??[]).filter(c=>!/^교환 /.test(c.message))]};
      }
      const c=this.resolve(query.replace(/^특별\s+/,''),this.content.characters.filter(c=>c.rarity==='COMMON'||c.rarity==='UNCOMMON'||c.rarity==='SPECIAL'),'교환',new Set());
      if(!('id' in c))return c;
      const price=this.exchangePrice(c);
      if(price.peanut&&p.peanut<price.peanut)throw new GameError(`🥜 땅콩이 부족해요. ${price.peanut}개 필요, 지금 ${p.peanut}개.\n땅콩떼기로 모을 수 있어요.`);
      if(price.snack&&p.snack<price.snack)throw new GameError(`🍤 새우gang이 부족해요. ${price.snack}개 필요, 지금 ${p.snack}개.`);
      const pay=price.peanut?`땅콩 ${price.peanut}개`:`새우gang ${price.snack}개`,have=price.peanut?`🥜 ${p.peanut}개`:`🍤 ${p.snack}개`,icon=price.peanut?'🥜':'🍤';
      return this.issue(tx,p,'RECEIVE',{characterId:c.id},now,`${icon} → ${emblems[c.rarity]} ${c.name}\n${pay} (지금 ${have})`,this.seenImage(c.id,new Set()),`${pay}를 써요`,
        {items:[{imageId:this.face(c.id,new Set()),title:`${icon} → ${emblems[c.rarity]} ${c.name}`,description:`${pay} · 지금 ${have}`}]});
    }
    if(command==='쿠폰'||command==='코드')return this.coupon(tx,p,query,now);
    if(command==='이벤트'){
      const ev=this.event(now);
      const site=this.content.economy.homepage?.replace(/^https?:\/\//,'')??'공식 홈페이지';
      const coupons=this.content.economy.coupons.filter(c=>(!c.from||new Date(c.from)<=now)&&(!c.to||now<new Date(c.to)));
      const couponLine=coupons.length?`\n\n🎟 쿠폰: ${coupons.map(c=>`${c.label} (뽑기권 ${c.tickets}장)`).join(', ')}\n${site} 에 숨은 조각을 모아 '쿠폰 코드'로 입력!`:'';
      // 언제나 받는 혜택: 친구 초대 · 튜토리얼 + 웹 이벤트 페이지 안내
      const inv=this.content.economy.invite;
      const always=`\n\n💌 친구 초대: 친구는 바로, 나는 친구가 튜토리얼을 끝내면 뽑기권 ${inv.tickets}장씩 (최대 ${inv.max}명)\n📘 튜토리얼: ${tutorialSteps.length}단계, 단계마다 뽑기권 ${this.content.economy.tutorial.tickets}장${this.content.economy.homepage?`\n\n자세히: ${this.content.economy.homepage.replace(/\/$/,'')}/event`:''}`;
      const extra=[choice('초대코드'),choice('튜토리얼')];
      if(!ev&&coupons.length)return this.info({text:`🎉 이벤트${couponLine}${always}`,choices:[choice('쿠폰'),...extra,choice('뽑기'),...menu.slice(1)]},'s-event');
      if(!ev)return this.info({text:`🎉 이벤트\n지금 진행 중인 기간 이벤트는 없어요.\n열리면 뽑기 화면 맨 앞에 알려 드려요.${always}`,choices:[...extra,choice('뽑기'),...menu.slice(1)]},'s-event');
      const what=[...(ev.claimBonus?[`뽑기권 받기 +${ev.claimBonus}장 더`]:[]),...(ev.snackMultiplier>1?[`새우gang 보상 ×${ev.snackMultiplier}`]:[])];
      return this.info({text:`🎉 ${ev.label}\n${what.join('\n')}\n~ ${DateTime.fromISO(ev.to).setZone('Asia/Seoul').toFormat('M/d HH:mm')}까지${couponLine}${always}`,choices:[choice('뽑기권 받기','뽑기권받기'),choice('뽑기'),...menu.slice(1)]},'s-event');
    }
    if(compact==='웹로그인'||compact==='로그인')return this.webCode(tx,p,now);
    // 탈퇴는 웹에서(실수로 지우지 않게 이름을 한 번 더 적어 확인)
    if(compact==='탈퇴'||compact==='계정삭제'||compact==='회원탈퇴'){
      const site=(this.content.economy.homepage??'').replace(/\/$/,'');
      return this.info({text:`🗑 계정 삭제(탈퇴)\n${site?`${site}/my#account`:'홈페이지 「내 갈매미」'} 에서 로그인한 뒤 「계정 삭제」를 누르면 바로 지워져요.\n로그인 코드는 여기서 「웹 로그인」을 보내면 받아요.\n\n지우면 갈매미·도감·재화·댓글이 모두 사라지고 되돌릴 수 없어요.`,choices:[choice('웹 로그인'),...menu.slice(0,4)]},'s-info');
    }
    if(command==='상점'||compact==='땅콩상점')return this.shop(tx,p,compact==='땅콩상점'?'땅콩':args.join(''),now);
    if(command==='장식'){
      const [act,...rest]=args,name=rest.join(' ').trim();
      const b=this.content.economy.peanutShop.badges.find(x=>x.label===name||x.label.replace(/\s*배지$/,'')===name||x.emoji===name);
      if(act==='빼기'){await tx.player.update({where:{id:p.id},data:{badge:null}});return this.info({text:'🎀 장식을 뺐어요.',choices:[choice('장식 보기','상점 장식'),...menu]},'s-rank');}
      if(!act||!b)return this.badgeShop(tx,p);
      const has=await tx.achievement.findUnique({where:{playerId_key:{playerId:p.id,key:`BADGE_${b.id}`}}});
      if(act==='달기'){
        if(!has)throw new GameError(`${b.label}은(는) 아직 없어요. 장식 사기 ${b.label}`);
        await tx.player.update({where:{id:p.id},data:{badge:b.id}});
        return this.info({text:`🎀 ${b.label}을(를) 달았어요!\n${this.named(p.nickname,b.id,'(닉네임을 정하면 이름 옆에 보여요)')}`,choices:[choice('장식 보기','상점 장식'),choice('랭킹'),...menu]},'s-rank');
      }
      if(act==='사기'){
        if(has)throw new GameError('이미 가지고 있는 장식이에요. 장식 달기로 쓰세요.');
        if(p.peanut<b.cost)throw new GameError(`🥜 땅콩이 부족해요. ${b.cost}개 필요, 지금 ${p.peanut}개.\n땅콩떼기로 모을 수 있어요.`);
        return this.issue(tx,p,'SHOP_BADGE',{badge:b.id},now,`🎀 ${b.label}\n${b.emoji} ${p.nickname??'닉네임'} · 땅콩 ${b.cost}개 (지금 ${p.peanut}개)`,undefined,`땅콩 ${b.cost}개를 써요`,
          {items:[{imageId:'s-rank',title:`🎀 ${b.label}`,description:`${b.emoji} ${p.nickname??'닉네임'}\n땅콩 ${b.cost}개 · 지금 🥜 ${p.peanut}개`}]});
      }
      return this.badgeShop(tx,p);
    }
    if(command==='팔기'||command==='판매')return this.sell(tx,p,query,now);
    if(command==='초대코드'||(command==='초대'&&!query))return this.invite(tx,p,'',now);
    if(command==='초대')return this.invite(tx,p,query.trim(),now);
    if(compact.startsWith('땅콩떼기'))return this.peanut(tx,p,trimmed.replace(/^땅콩\s*떼기/u,'').trim(),now);
    // 조합 하나로: "조합 고르기"·"조합 A 다음"·"조합 A, B"(재료로 직접) / "조합 금갑"(이름으로). '합치기'는 예전 말이라 그대로 받아 줌
    if(command==='조합'||command==='합치기'){
      const q=query.trim();
      if(/^고르기(?:\s*쪽\s*\d+)?$/.test(q)||/다음(?:\s*쪽\s*\d+)?$/.test(q)||/^쪽\s*\d+$/.test(q)||(command==='합치기'&&!q))return this.combine(tx,p,q.replace(/^고르기\s*/,''),now);
      if(command==='합치기'||/[,，+]|[x×*]\s*\d|\d+\s*마리/.test(q))return this.combine(tx,p,q,now);
    }
    if(command==='조합'||command==='조합식'){
      if(!query)return this.browse(tx,p,'조합목록','');
      const known=await this.known(tx,p);
      // "조합 금갑 5" / "조합 금갑 최대"
      const many=command==='조합'?query.match(/^(.+?)\s+(\d+|최대)$/u):null;
      const c=this.resolve(many?many[1]:query,this.revealed(known,c=>c.rarity!=='COMMON'),command,known);
      if(!('id' in c))return c;
      const recipe=this.content.recipes.find(r=>r.resultId===c.id)!;
      const stock=await this.stock(tx,p);
      const seen=Boolean(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}}));
      const parts=this.recipeParts(recipe,stock,known,seen);
      const text=parts.text;
      if(command==='조합식'||!this.canCraft(recipe,stock)){
        // 모자란 재료로 바로 가는 버튼: 조합 재료는 그 조합식, 뽑기 재료는 해당 뽑기
        const lacking=[...materialCounts(recipe.materials)].filter(([id,n])=>statusOf(stock.get(id)).usable<n).map(([id])=>this.content.map.get(id)!);
        // 흔함·안흔함은 바로 교환, 그 위는 그 조합식으로
        const jump=lacking.filter(m=>isRevealed(m,known)).slice(0,3).map(m=>m.rarity==='COMMON'||m.rarity==='UNCOMMON'?choice(`${m.name.slice(0,9)} 교환`,`교환 ${m.name}`):choice(`${m.name.slice(0,9)} 조합식`,`조합식 ${m.name}`));
        const make=this.canCraft(recipe,stock)?[choice('조합하기',`조합 ${c.name}`)]:[];
        return {text,imageId:this.seenImage(c.id,known),cards:{items:[{...parts.card,buttons:[...make,...jump].slice(0,3)}],outro:parts.body},
          choices:[...make,...jump,choice('뽑기'),choice('조합가능'),choice('조합목록'),choice('내갈매미')]};
      }
      const most=this.maxCraft(recipe,stock);
      const times=many?(many[2]==='최대'?most:Math.min(Number(many[2]),most)):1;
      if(many&&many[2]!=='최대'&&(Number(many[2])<1||Number(many[2])>50))throw new GameError('한 번에 1~50개까지 만들 수 있어요. 예: 조합 금갑 5');
      if(many&&Number(many[2])>most)throw new GameError(`재료가 ${most}개 만들 만큼만 있어요.`);
      const ids=await this.chooseMaterials(tx,p,recipe,known,times);
      // 특별 이하(히든 제외)는 확인 없이 바로 만들고, 희귀 이상은 확인 카드를 거칩니다
      if(tiers[c.rarity]<=tiers.SPECIAL&&c.rarity!=='HIDDEN')return this.craft(tx,p,recipe,ids,times,now,known);
      const label=times>1?`${times}개 · 재료 ${ids.length}마리를 써요`:`재료 ${ids.length}마리를 써요`;
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids,times},now,times>1?`🧩 ${c.name} ×${times}\n\n${text}`:text,this.seenImage(c.id,known),label,
        {items:[times>1?{...parts.card,title:`${parts.card.title} ×${times}`}:parts.card],outro:parts.body});
    }
    if(command==='잠금'||command==='잠금해제'){
      if(!query)return {text:`${command} 뒤에 이름을 붙여 주세요. 예: ${command} 금갑\n🔒 보호한 갈매미는 조합 재료로 안 써요.`,choices:[choice('내갈매미')]};
      const known=await this.known(tx,p);
      const c=this.resolve(query,this.revealed(known),command,known);
      if(!('id' in c))return c;
      const locked=command==='잠금';
      // 그 유닛 전부를 한 번에 보호/해제합니다
      const changed=await tx.ownedCharacter.updateMany({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:!locked},data:{locked}});
      if(!changed.count)return this.info({text:`${locked?'🔒':'🔓'} ${c.name}\n지금 ${command}할 ${c.name}이(가) 없어요.\n(보유 수량, 보호, 탐험 상태를 확인해 주세요)`,choices:[choice('보유 상태',`내갈매미 ${c.name}`)]},this.cardImage(c.id,known));
      await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
      await this.audit(tx,p,'LOCK',{characterId:c.id,count:changed.count,locked});
      return this.info({text:locked?`🔒 ${c.name} 보호\n${changed.count}마리를 모두 보호했어요. 조합 재료·팔기에 안 써요.`:`🔓 ${c.name} 보호 해제\n${changed.count}마리 보호를 모두 풀었어요.`,choices:[choice('보유 상태',`내갈매미 ${c.name}`),...menu.slice(0,3)]},'s-lock');
    }
    if(command==='탐험'&&!query)return this.expeditionStatus(tx,p,now);
    const draftCmd=compact.match(/^편성(추가|빼기|비우기|출발)/u);
    if(draftCmd)return this.partyDraft(tx,p,draftCmd[1],trimmed.replace(/^편성\s*(추가|빼기|비우기|출발)/u,'').trim(),now);
    if(compact.startsWith('탐험편성'))return this.partyBuilder(tx,p,trimmed.replace(/^탐험\s*편성/u,'').trim(),now);
    if(compact.startsWith('탐험보내기')||(command==='탐험'&&args[0]==='보내기'))return this.sendExpedition(tx,p,trimmed.replace(/^탐험\s*보내기/u,'').trim(),now);
    if(['탐험보상받기','탐험보상','탐험받기'].includes(compact))return this.claimExpedition(tx,p,now);
    if(command==='시너지'){
      const e=this.content.economy.expedition;
      const items=(Object.keys(e.effects) as (keyof typeof e.effects)[]).map(key=>{
        const face=this.content.characters.find(c=>c.synergy===key&&this.viewerSeen.has(c.id));
        return {imageId:face?this.cardImage(face.id,new Set()):existsSync(resolve(this.root,'public/images',`y-${key}.png`))?`y-${key}`:'unknown',title:`✨ ${e.effects[key].label} (${e.effects[key].concept})`,description:`1단계: ${this.effectText(key,1)}\n2단계: ${this.effectText(key,2)}`};
      });
      return {text:this.synergyGuide(),cards:{intro:`🧭 탐험 시너지\n같은 시너지 ${e.levels[0]}마리 이상 = 1단계 / ${e.levels[1]}마리 이상 = 2단계`,items,outro:`같은 갈매미 여러 마리: ${Object.entries(e.countBonusPercent).map(([n,v])=>`${n}마리 +${v}%`).join(' · ')}\n여러 시너지가 함께 발동하면 효과를 모두 더해요.`},choices:[choice('탐험'),...menu]};
    }
    if(command==='관리자'||command==='관리자모드'){
      const sub=args[0]??'';
      if(sub==='상태'){
        if(!p.isAdmin)return this.info({text:'관리자로 등록된 계정이 아니에요.',choices:menu},'unknown');
        const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
        const since=new Date(now.getTime()-86_400_000);
        const [players,today,trips]=await Promise.all([tx.player.count(),tx.dailyProgress.count({where:{day}}),tx.expedition.count({where:{claimedAt:null}})]);
        const kinds=await tx.auditEvent.groupBy({by:['kind'],where:{createdAt:{gte:since}},_count:{_all:true}});
        const n=(k:string)=>kinds.find(x=>x.kind===k)?._count._all??0;
        const newbies=await tx.player.count({where:{createdAt:{gte:since}}});
        return this.info({text:`🛠 운영 상태\n👥 전체 ${players}명 · 오늘 활동 ${today}명 · 24시간 신규 ${newbies}명\n🧭 진행 중 탐험 ${trips}\n\n최근 24시간\n🥚 뽑기 ${n('GACHA')} · 🧩 조합 ${n('CRAFT')} · 🍤 교환 ${n('EXCHANGE')} · 팔기 ${n('SELL')} · 🛒 상점 ${n('SHOP')} · 🥜 땅콩 ${n('PEANUT')} · 💌 초대 ${n('INVITE')}\n(서버 오류 수는 EC2 로그에서 확인: journalctl -u gmm-server)`,choices:menu},'s-info');
      }
      if(sub==='댓글숨김'||sub==='댓글보이기'){
        if(!p.isAdmin)return this.info({text:'관리자로 등록된 계정이 아니에요.',choices:menu},'unknown');
        const id=Number((args[1]??'').replace(/^#/,''));
        const c=Number.isSafeInteger(id)&&id>0?await tx.comment.findUnique({where:{id}}):null;
        if(!c)throw new GameError('그런 댓글은 없어요. 예: 관리자 댓글숨김 12');
        await tx.comment.update({where:{id},data:{hidden:sub==='댓글숨김'}});
        await this.audit(tx,p,'COMMENT_MODERATE',{commentId:id,hidden:sub==='댓글숨김'});
        return {text:`${sub==='댓글숨김'?'🙈 숨겼어요':'👀 다시 보이게 했어요'} (#${id})\n「${c.text.slice(0,40)}」`,choices:menu};
      }
      if(sub==='해제'){
        const restored=p.adminMode?await this.restoreSnapshot(tx,p):false;
        await tx.player.update({where:{id:p.id},data:{isAdmin:false,adminMode:false}});
        await this.audit(tx,p,'ADMIN',{registered:false,mode:false,restored});
        return {text:`관리자 등록을 해제했어요.${restored?'\n🔄 관리자 모드 동안의 뽑기·조합·재화는 모두 켜기 전으로 되돌렸어요.':''}\n다시 쓰려면 관리자 코드를 입력하세요.`,choices:menu};
      }
      if(['켜기','켬','on','끄기','끔','off'].includes(sub)||!sub){
        if(!p.isAdmin)return {text:'관리자로 등록된 계정이 아니에요. (관리자 코드 입력 필요)',choices:menu};
        if(!sub)return {text:`🛠 관리자 모드: ${p.adminMode?'켜짐 (뽑기권 무제한)':'꺼짐 (일반 유저와 같음)'}`,choices:[choice(p.adminMode?'관리자 끄기':'관리자 켜기'),...menu]};
        const on=['켜기','켬','on'].includes(sub);
        if(on===p.adminMode)return {text:on?'이미 관리자 모드가 켜져 있어요.':'이미 관리자 모드가 꺼져 있어요.',choices:[choice(on?'관리자 끄기':'관리자 켜기'),...menu]};
        if(on)await this.saveSnapshot(tx,p,now);
        const restored=on?false:await this.restoreSnapshot(tx,p);
        await tx.player.update({where:{id:p.id},data:{adminMode:on}});
        await this.audit(tx,p,'ADMIN',{registered:true,mode:on,restored});
        return on?{text:`${adminOnText}\n끄려면: 관리자 끄기`,choices:[choice('뽑기'),choice('관리자 끄기'),...menu]}
          :{text:`관리자 모드를 껐어요.${restored?'\n🔄 모드 동안의 뽑기·조합·재화를 모두 켜기 전으로 되돌렸어요.':''}\n이제 일반 유저처럼 뽑기권이 줄어요. 다시 켜려면: 관리자 켜기`,choices:[choice('내정보'),choice('관리자 켜기'),...menu]};
      }
      if(!this.adminCode)return {text:'관리자 기능이 꺼져 있어요. (서버 .env에 ADMIN_CODE가 없어요)',choices:menu};
      const given=Buffer.from(args.join(' ')),expected=Buffer.from(this.adminCode);
      if(given.length!==expected.length||!timingSafeEqual(given,expected))return {text:'관리자 코드가 맞지 않아요.',choices:menu};
      if(!p.adminMode)await this.saveSnapshot(tx,p,now);
      await tx.player.update({where:{id:p.id},data:{isAdmin:true,adminMode:true}});
      await this.audit(tx,p,'ADMIN',{registered:true,mode:true});
      return {text:`🛠 관리자로 등록했어요.\n${adminOnText}\n끄기: 관리자 끄기 · 켜기: 관리자 켜기\n등록 해제: 관리자 해제`,choices:[choice('뽑기'),choice('관리자 끄기'),...menu]};
    }
    if(command==='닉네임'||command==='닉네임설정'){
      const name=query.normalize('NFKC').trim().replace(/\s+/g,' ');
      if(!name)return {text:`🏷 닉네임: ${p.nickname??'(없음)'}\n랭킹에 표시돼요. 바꾸려면: 닉네임 갈매미왕\n(2~10자, 한글·영문·숫자)`,choices:[choice('랭킹'),...menu]};
      if(!await this.changeNickname(tx,p,name))return {text:`이미 「${name}」이에요.`,choices:[choice('랭킹'),...menu]};
      const step=await this.tutorialStep(tx,p,GameService.tutorialSteps.findIndex(t=>t.go.message==='닉네임')+1);
      return {text:`🏷 닉네임을 「${name}」(으)로 정했어요.${step.length?`\n\n${step[0]}`:''}`,choices:[choice('랭킹'),...menu]};
    }
    if(command==='랭킹'||command==='순위')return this.ranking(tx,p,args[0]??'',now);
    if(command==='댓글')return this.commentCommand(tx,p,query,now);
    if(command==='좋아요'){
      const id=Number(query.replace(/^#/,''));
      if(!Number.isSafeInteger(id)||id<1)throw new GameError('좋아요 뒤에 댓글 번호를 붙여 주세요. 예: 좋아요 12');
      const r=await this.likeComment(tx,id,`p:${p.id}`,p.id);
      const unit=this.content.map.get(r.comment.characterId)!;
      return this.info({text:`${r.already?'이미 좋아요를 눌렀어요':'👍 좋아요!'} (#${id})\n지금 👍 ${r.comment.likes}`,choices:[choice('댓글 더 보기',`댓글 ${unit.name}`),...menu.slice(0,3)]},'s-info');
    }
    if(command==='댓글삭제'||command==='댓글지우기'){
      const id=Number(query.replace(/^#/,''));
      const c=Number.isSafeInteger(id)&&id>0?await tx.comment.findUnique({where:{id}}):null;
      if(!c||(c.playerId!==p.id&&!p.isAdmin))throw new GameError('내 댓글 번호를 확인해 주세요. 예: 댓글삭제 12');
      await tx.commentLike.deleteMany({where:{commentId:id}});
      await tx.comment.delete({where:{id}});
      await this.audit(tx,p,'COMMENT_DELETE',{commentId:id,byAdmin:c.playerId!==p.id});
      return {text:`🗑 댓글 #${id}을(를) 지웠어요.`,choices:menu};
    }
    if(command==='미션'){
      const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
      const row=await tx.dailyProgress.findUnique({where:{playerId_day:{playerId:p.id,day}}});
      const done=new Set((row?.rewarded??'').split(',').filter(Boolean));
      const look:Record<string,[string,Choice]>={DRAW:['m-draw',choice('뽑기')],CRAFT:['m-craft',choice('조합가능')],CRAFT_RARE:['m-rare',choice('희귀 조합목록','조합목록 희귀')],
        CRAFT_LEGEND:['m-legend',choice('전설 조합목록','조합목록 전설')],CRAFT_TOP:['m-top',choice('제한 조합목록','조합목록 제한')],EXPEDITION:['explore',choice('탐험')]};
      const items=this.content.economy.missions.map(m=>{
        const value=Math.min(m.goal,row?.[GameService.missionField[m.key]]??0),ok=done.has(m.key);
        return {imageId:/^[A-Z]\d/.test(look[m.key][0])?this.cardImage(look[m.key][0],new Set()):look[m.key][0],title:`${ok?'✅':'🎯'} ${m.label}`,description:`${ok?'완료!':m.goal>1?`진행 ${value}/${m.goal}`:'아직'} · 🎟 ${m.tickets}`,...(ok?{}:{buttons:[look[m.key][1]]})};
      });
      return {text:`🎯 오늘의 미션 · 밤 12시 초기화\n\n${await this.missionText(tx,p,now)}\n\n채우는 순간 바로 받아요.`,cards:{intro:`🎯 오늘의 미션 ${done.size}/${items.length} · 밤 12시 초기화`,items,outro:'채우는 순간 바로 받아요.'},choices:[choice('뽑기'),choice('조합가능'),choice('탐험'),...menu]};
    }
    if(command==='칭호'){
      const got=await this.titles(tx,p);
      const all=tierOrder.map(t=>`${got.includes(this.content.economy.achievements[t].title)?'🏆':'・'} ${rarityNames[t]} 도감 완성 → 「${this.content.economy.achievements[t].title}」 · 🎟 ${this.content.economy.achievements[t].tickets}`);
      const known=await this.known(tx,p);
      const items=tierOrder.map(t=>{const a=this.content.economy.achievements[t],ok=got.includes(a.title);
        return {imageId:ok?this.gradeImage(t,known):'unknown',title:`${ok?'🏆':'🔒'} 「${a.title}」`,description:`${emblems[t]} ${rarityNames[t]} 도감 완성 · 🎟 ${a.tickets}${ok?'\n획득!':''}`,buttons:[choice(`${rarityNames[t]} 도감`,`도감 ${rarityNames[t]}`)]};});
      return {text:`🏆 칭호 ${got.length}/${tierOrder.length}\n\n${all.join('\n')}\n\n등급 도감을 다 채우면 칭호와 뽑기권을 받아요.`,
        cards:{intro:`🏆 칭호 ${got.length}/${tierOrder.length}`,items,outro:'등급 도감을 다 채우면 칭호와 뽑기권을 받아요.'},choices:[choice('도감'),...menu]};
    }
    if(command==='내정보'){
      const count=await tx.collectionEntry.count({where:{playerId:p.id}});
      const active=await this.activeExpedition(tx,p);
      const trip=active?(active.endsAt>now?`탐험 중 · 귀환까지 ${duration(active.endsAt.getTime()-now.getTime())}`:'탐험 귀환 · 보상 받기 대기'):'탐험 없음';
      const titles=await this.titles(tx,p);
      // 프로필 그림: 가진 유닛 중 가장 높은 등급
      const known=await this.known(tx,p),stock=await this.stock(tx,p);
      const best=this.content.characters.filter(c=>stock.has(c.id)&&isRevealed(c,known)).sort((a,b)=>tiers[b.rarity]-tiers[a.rarity]||b.position-a.position)[0];
      const profile:CardEntry={imageId:best?this.cardImage(best.id,known):'unknown',title:`🏝 갈매미 섬${titles.length?` · 「${titles.at(-1)}」`:''}`,
        description:`🎟 ${unlimited(p)?'무제한':`${p.credits}장`} · 🍤 ${p.snack}개${p.peanut?` · 🥜 ${p.peanut}`:''}\n📚 도감 ${count}/${this.content.characters.length} · 🏆 칭호 ${titles.length}개`,buttons:[choice('도감'),choice('칭호'),choice('미션')]};
      const today=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
      const doneToday=((await tx.dailyProgress.findUnique({where:{playerId_day:{playerId:p.id,day:today}}}))?.rewarded??'').split(',').filter(Boolean).length;
      const status=`${p.isAdmin?(p.adminMode?'🛠 관리자 모드\n':'관리자(모드 꺼짐)\n'):''}${this.claimHint(p,now)}\n🧭 ${trip}\n🎯 오늘의 미션 ${doneToday}/${this.content.economy.missions.length}`;
      return {cards:{items:[this.maskCard(profile,known)],outro:status},text:`🏝 갈매미 섬${titles.length?` · 「${titles.at(-1)}」`:''}${p.isAdmin?(p.adminMode?' · 🛠 관리자 모드':' · 관리자(모드 꺼짐)'):''}\n🎟 ${unlimited(p)?'무제한':`${p.credits}장`} · 🍤 ${p.snack}개\n📚 도감 ${count}/${this.content.characters.length} · 🏆 칭호 ${titles.length}개\n${this.claimHint(p,now)}\n🧭 ${trip}\n\n🎯 오늘의 미션\n${await this.missionText(tx,p,now)}`,choices:[choice('미션'),choice('칭호'),...menu]};
    }
    if((command==='도감'||command==='내갈매미')&&query&&!/^\d+$/.test(query)&&!this.gradeOf(args[0]))return this.detail(tx,p,command,query);
    if(command==='도감'){
      const line=await this.tutorialStep(tx,p,3);
      const reply=await this.browse(tx,p,command,query);
      return line.length?{...reply,text:`${line[0]}\n\n${reply.text}`,...(reply.cards?{cards:{...reply.cards,intro:`${line[0]}\n\n${reply.cards.intro??''}`.trim()}}:{})}:reply;
    }
    if(command==='튜토리얼')return this.tutorialView(tx,p);
    if(['내갈매미','조합목록','조합가능'].includes(command))return this.browse(tx,p,command,query);
    return {text:helpText(this.content.economy),cards:{intro:'🏝 이 섬의 항구는 돌이 지킨다.\n그들을 갈매미라 부른다. 갈매미는 존재한다.',items:this.helpCards(),outro:`이렇게도 써요\n${helpExamples}`},choices:menu};
  }
}


