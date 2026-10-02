import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { Prisma, type PrismaClient, type Player } from '@prisma/client';
import type { Character, Content, Recipe, Tier } from './content.js';
import { rarityNames, tierOrder, topTiers, tiers } from './content.js';
import { GameError, choice, type GameReply, type ButtonAction, type ListEntry, type Choice } from './types.js';
import { GachaEngine, type GachaKind } from './gacha.js';
import { characterInfo, matchCharacters, listReply, materialCounts, displayName, isRevealed, mask, duration, HIDDEN_NAME } from './presentation.js';
import { acquisitionText, achievementText, emblems, prelude, rewardGuide, speech, type Receipt } from './acquisition.js';
import { DateTime } from 'luxon';
import { planExpedition, rollExpedition, expectedSnacks, type ExpeditionPlan, type Roll } from './expedition.js';

type Count={total:number;usable:number;locked:number;away:number};
type Stock=Map<string,Count>;
type Tx=Prisma.TransactionClient;
type ActionPayload={characterId?:string;ids?:number[];recipeId?:string};
const json=(x:unknown)=>x as Prisma.InputJsonValue;
const gachaWords:Record<string,GachaKind>={하급:'LOW',중급:'MID',고급:'HIGH'};
const menu=[choice('뽑기'),choice('뽑기권 받기','뽑기권받기'),choice('내갈매미'),choice('조합가능'),choice('조합목록'),choice('탐험'),choice('도감'),choice('내정보')];
// 관리자로 등록되어 있고 관리자 모드를 켰을 때만 뽑기권 무제한
const unlimited=(p:{isAdmin:boolean;adminMode:boolean})=>p.isAdmin&&p.adminMode;
const adminOnText='🛠 관리자 모드 켜짐!\n뽑기에 뽑기권을 쓰지 않아요(무제한).\n⚠ 모드를 끄면 그동안 뽑은 유닛·조합·탐험·재화가 모두 켜기 전으로 돌아가요.';
const help=[
  '🏝 갈매미맨 — 갈매미를 모아 조합하는 게임',
  '',
  '🎟 뽑기권 받기 · 5시간마다 10장',
  '🥚 뽑기 · 하급 2장 / 중급 3장 / 고급 5장',
  '🧩 조합가능 · 지금 만들 수 있는 것',
  '🔮 합치기 · 재료를 골라 숨은 조합 찾기',
  '🧭 탐험 · 최상위 유닛을 보내 새우깡 벌기',
  '🎯 미션 · 🏆 칭호 · 📚 도감 · 🐦 내갈매미',
  '',
  '이렇게도 써요',
  '하급뽑기 5 · 조합 금갑 · 도감 야경',
  '합치기 황금쌍패성기사, 은하매듭직조자',
  '탐험보내기 갈발 ×3, 갈내복'
].join('\n');
const statusOf=(c:Count|undefined):Count=>c??{total:0,usable:0,locked:0,away:0};

export class GameService {
  private tail:Promise<unknown>=Promise.resolve();
  private pending=0;
  private readonly topIds:string[];
  constructor(private db:PrismaClient,public content:Content,private clock=()=>new Date(),
    private gacha=new GachaEngine(content.characters,content.economy.gachas),private roll:Roll=randomInt,private root=process.cwd(),private adminCode?:string){
    this.topIds=content.characters.filter(c=>topTiers.has(c.rarity)).map(c=>c.id);
  }
  handle(identity:string,message:string,button?:ButtonAction):Promise<GameReply>{
    if(this.pending>=30)return Promise.resolve({text:'요청이 몰렸습니다. 잠시 뒤 다시 입력해 주세요.'});
    this.pending++;
    let expired=false;
    let timer:ReturnType<typeof setTimeout>;
    const timeout=new Promise<GameReply>((_resolve,reject)=>{
      timer=setTimeout(()=>{expired=true;reject(new GameError('서버가 혼잡합니다. 잠시 뒤 같은 요청을 다시 보내세요.'));},1000);
    });
    const task=this.tail.then(async()=>{
      if(expired)throw new GameError('대기 중 만료된 요청');
      clearTimeout(timer);
      return this.db.$transaction(async tx=>{
        const now=this.clock();
        const player=await this.player(tx,identity,now);
        return this.dispatch(tx,player,message,now,button);
      },{maxWait:500,timeout:2500});
    });
    this.tail=task.catch(()=>{});
    return Promise.race([task,timeout]).then(result=>{
      const imageName=result.imageId?this.content.map.get(result.imageId)?.name:undefined;
      // 같은 버튼이 두 번 보이지 않게 정리합니다(카카오 바로가기 최대 10개).
      const seen=new Set<string>();
      const choices=result.choices?.filter(c=>!seen.has(c.message)&&Boolean(seen.add(c.message))).slice(0,10);
      return {...result,...(choices?{choices}:{}),...(imageName?{imageName}:{})};
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
    const data={player:{credits:p.credits,snack:p.snack,lastClaimAt:p.lastClaimAt},owned,collection,expeditions,achievements,daily};
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
    await tx.player.update({where:{id:p.id},data:{credits:d.player.credits,snack:d.player.snack,lastClaimAt:date(d.player.lastClaimAt),revision:{increment:1}}});
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
    if(p.snack>=this.content.economy.exchange.snackCost&&await this.hasTop(tx,p)){lines.push(`🍤 새우깡 ${p.snack}개로 교환 가능`);choices.push(choice('교환'));}
    return {text:lines.length?`\n\n그동안 해 볼 것\n${lines.join('\n')}`:'',choices};
  }
  private claimHint(p:Player,now:Date){
    const wait=this.claimWait(p,now);
    return wait?`다음 뽑기권 받기: ${duration(wait)} 뒤`:'🎟 지금 뽑기권을 받을 수 있습니다.';
  }
  private resolve(query:string,pool:Character[],command:string,known:Set<string>):Character|GameReply{
    const found=matchCharacters(pool,query);
    if(found.length===1)return found[0];
    if(!found.length)return {text:`🤔 "${query}"… 그런 갈매미는 없어요.\n이름 일부만 써도 돼요. 예: ${command} 금갑`,choices:[choice('조합목록'),choice('도감'),choice('내갈매미')]};
    return listReply('어떤 갈매미일까요?',found.slice(0,5).map(c=>({title:`${emblems[c.rarity]} ${c.name}`,description:characterInfo(this.content,c),message:`${command} ${c.name}`})),
      '눌러서 골라 주세요. 없으면 이름을 더 길게 써 주세요.',[choice('도움말')]);
  }
  private async issue(tx:Tx,p:Player,kind:string,payload:ActionPayload,now:Date,text:string,imageId?:string,detail?:string):Promise<GameReply>{
    await tx.pendingAction.deleteMany({where:{playerId:p.id,result:{equals:Prisma.DbNull},expiresAt:{lt:now}}});
    const token=randomBytes(8).toString('hex');
    await tx.pendingAction.create({data:{token,playerId:p.id,revision:p.revision,kind,payload:json(payload),expiresAt:new Date(now.getTime()+600_000)}});
    const [header,title,message,fallback]=kind==='CRAFT'?['만들까요?','🧩 조합하기','조합 확정','재료를 써서 1마리를 만들어요']:
      kind==='EXPEDITION'?['출발할까요?','🧭 탐험 출발','탐험 확정','돌아올 때까지 다른 데 못 써요']:['교환할까요?','🍤 교환하기','교환 확정','새우깡을 써서 받아요'];
    return {text,imageId,list:{title:header,showText:true,items:[
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
    const snacks=Math.max(0,Math.min(planned,2_000_000_000-account.snack));
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
  private async progress(tx:Tx,p:Player,kind:'DRAW'|'CRAFT'|'EXPEDITION',amount:number,now:Date):Promise<string[]>{
    const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const field=kind==='DRAW'?'draws':kind==='CRAFT'?'crafts':'expeditions';
    const row=await tx.dailyProgress.upsert({where:{playerId_day:{playerId:p.id,day}},create:{playerId:p.id,day,[field]:amount},update:{[field]:{increment:amount}}});
    const done=new Set(row.rewarded.split(',').filter(Boolean));
    const lines:string[]=[];
    for(const m of this.content.economy.missions){
      const value=m.key==='DRAW'?row.draws:m.key==='CRAFT'?row.crafts:row.expeditions;
      if(value<m.goal||done.has(m.key))continue;
      done.add(m.key);
      if(m.tickets)await tx.player.update({where:{id:p.id},data:{credits:{increment:m.tickets}}});
      await this.audit(tx,p,'MISSION',{day,key:m.key,tickets:m.tickets});
      lines.push(`🎯 오늘의 미션 완료! ${m.label} · 🎟 +${m.tickets}`);
    }
    if(lines.length)await tx.dailyProgress.update({where:{playerId_day:{playerId:p.id,day}},data:{rewarded:[...done].join(',')}});
    return lines;
  }
  private async missionText(tx:Tx,p:Player,now:Date){
    const day=DateTime.fromJSDate(now).setZone('Asia/Seoul').toISODate()!;
    const row=await tx.dailyProgress.findUnique({where:{playerId_day:{playerId:p.id,day}}});
    const done=new Set((row?.rewarded??'').split(',').filter(Boolean));
    return this.content.economy.missions.map(m=>{
      const value=Math.min(m.goal,(m.key==='DRAW'?row?.draws:m.key==='CRAFT'?row?.crafts:row?.expeditions)??0);
      return `${done.has(m.key)?'✅':'⬜'} ${m.label} (${value}/${m.goal}) · 🎟 ${m.tickets}`;
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
  // 결과 끝에 붙는 다음 행동 안내와 버튼
  private async nextStep(tx:Tx,p:Player,now:Date,first:Choice[]=[]){
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id}});
    const ready=await this.craftable(tx,p);
    const cheapest=Math.min(...Object.values(this.content.economy.gachas).map(g=>g.cost));
    const canClaim=!this.claimWait(account,now);
    const lines=[`🎟 ${unlimited(account)?'무제한(관리자)':`${account.credits}장`} · 🍤 ${account.snack}개`];
    if(ready)lines.push(`✨ 지금 조합 가능 ${ready}종!`);
    if(!unlimited(account)&&account.credits<cheapest)lines.push(canClaim?'🎟 뽑기권 받기를 눌러 10장을 받으세요!':this.waitLine(account,now));
    const choices=[...first,...(ready?[choice(`조합가능 ${ready}`,'조합가능')]:[]),...(canClaim?[choice('뽑기권 받기','뽑기권받기')]:[]),choice('뽑기'),...menu.slice(2)];
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
      return {text:`⏳ 아직 받을 수 없어요\n${this.waitLine(p,now).slice(2)}\n🎟 지금 ${p.credits}장${alt.text}`,choices:[...alt.choices,choice('뽑기'),...menu.slice(2)]};
    }
    const updated=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{credits:{increment:t.claim},lastClaimAt:now,revision:{increment:1}}});
    if(updated.count!==1)throw new GameError('잠깐 겹쳤어요. 뽑기권 받기를 다시 눌러 주세요.');
    await this.audit(tx,p,'TICKET_CLAIM',{delta:t.claim,balanceAfter:p.credits+t.claim});
    return {text:`🎟 뽑기권 +${t.claim}!\n지금 ${p.credits+t.claim}장 · 다음은 ${t.cooldownHours}시간 뒤`,choices:[choice('하급 ×5','하급뽑기 5'),choice('중급 ×3','중급뽑기 3'),choice('고급 ×2','고급뽑기 2'),choice('뽑기'),...menu.slice(2)]};
  }
  private async gachaMenu(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const g=this.content.economy.gachas;
    const cheapest=Math.min(...Object.values(g).map(x=>x.cost));
    if(!unlimited(p)&&p.credits<cheapest){
      const alt=await this.meanwhile(tx,p,now);
      return {text:`🥚 뽑기 · 🎟 ${p.credits}장\n뽑기권이 모자라요. (하급 ${g.LOW.cost}장부터)\n${this.waitLine(p,now)}${alt.text}`,
        choices:[...(this.claimWait(p,now)?[]:[choice('뽑기권 받기','뽑기권받기')]),...alt.choices,choice('확률'),...menu.slice(2)]};
    }
    const items=(['LOW','MID','HIGH'] as GachaKind[]).map(kind=>({
      title:`${g[kind].label} 뽑기 · 🎟 ${g[kind].cost}`,
      description:Object.entries(g[kind].weights).map(([t,w])=>`${emblems[t]}${rarityNames[t]} ${w}`).join(' · ')+'%',
      message:`${g[kind].label}뽑기`
    }));
    return {...listReply(`🥚 뽑기 · 🎟 ${unlimited(p)?'무제한(관리자)':`${p.credits}장`}`,items,`숫자를 붙이면 여러 번 (최대 10): 하급뽑기 5${this.claimWait(p,now)?'':'\n🎟 지금 뽑기권을 받을 수 있어요!'}`),
      choices:[choice('하급 ×5','하급뽑기 5'),choice('중급 ×5','중급뽑기 5'),choice('고급 ×2','고급뽑기 2'),choice('뽑기권 받기','뽑기권받기'),choice('확률'),...menu.slice(2,6)]};
  }
  private async draw(tx:Tx,p:Player,kind:GachaKind,times:number,now:Date):Promise<GameReply>{
    const g=this.content.economy.gachas[kind];
    const cost=g.cost*times;
    const admin=unlimited(p);
    if(!admin&&p.credits<cost){
      const cheaper=(['LOW','MID','HIGH'] as GachaKind[]).filter(k=>this.content.economy.gachas[k].cost<=p.credits);
      const alt=await this.meanwhile(tx,p,now);
      return {text:`🎟 뽑기권이 모자라요\n${g.label} ${times}회 ${cost}장 · 지금 ${p.credits}장${cheaper.length?`\n(${cheaper.map(k=>this.content.economy.gachas[k].label).join('·')} 뽑기는 지금 할 수 있어요)`:''}\n${this.waitLine(p,now)}${alt.text}`,
        choices:[...cheaper.map(k=>choice(`${this.content.economy.gachas[k].label}뽑기`)),...(this.claimWait(p,now)?[]:[choice('뽑기권 받기','뽑기권받기')]),...alt.choices,...menu.slice(2)]};
    }
    // 관리자 모드를 켠 관리자는 테스트용으로 뽑기권을 쓰지 않습니다.
    const paid=await tx.player.updateMany({where:{id:p.id,revision:p.revision,...(admin?{}:{credits:{gte:cost}})},data:{...(admin?{}:{credits:{decrement:cost}}),revision:{increment:1}}});
    if(paid.count!==1)throw new GameError('잠깐 겹쳤어요. 뽑기를 다시 눌러 주세요.');
    const known=await this.known(tx,p);
    const results:{c:Character;receipt:Receipt}[]=[];
    for(let i=0;i<times;i++){
      const c=this.gacha.draw(kind);
      const owned=await this.grant(tx,p,c.id,'GACHA',now);
      results.push({c,receipt:owned.receipt});
    }
    await this.audit(tx,p,'GACHA',{kind,times,cost:admin?0:cost,admin,results:results.map(r=>r.c.id),balanceAfter:admin?p.credits:p.credits-cost});
    const missions=await this.progress(tx,p,'DRAW',times,now);
    const again=admin||p.credits-cost>=cost;
    const next=await this.nextStep(tx,p,now,again?[choice(times>1?`${g.label} ×${times} 더`:`${g.label} 한 번 더`,`${g.label}뽑기${times>1?` ${times}`:''}`)]:[]);
    const tail=[...missions,next.line].join('\n');
    if(times===1){
      const {c,receipt}=results[0];
      return {text:`${acquisitionText(this.content,c,'GACHA',receipt,known)}\n\n${tail}`,...(receipt.first?{imageId:this.image(c.id,known)}:{}),
        choices:[...next.choices.slice(0,2),...(receipt.first?[choice('유닛 보기',`도감 ${c.name}`)]:[]),...next.choices.slice(2)]};
    }
    const best=[...results].sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity])[0];
    const firstNew=results.filter(r=>r.receipt.first).sort((a,b)=>tiers[b.c.rarity]-tiers[a.c.rarity])[0];
    const snacks=results.reduce((sum,r)=>sum+r.receipt.snacks,0);
    const lines=results.map(r=>`${r.receipt.first?'🆕':'　'} ${emblems[r.c.rarity]} ${r.c.name}`);
    const news=results.filter(r=>r.receipt.first).length;
    const highlight=firstNew&&tiers[firstNew.c.rarity]>=tiers.RARE?`\n\n${prelude(firstNew.c,true)}${emblems[firstNew.c.rarity]} ${firstNew.c.name}`:'';
    const achieved=results.filter(r=>r.receipt.achievement).map(r=>achievementText(r.receipt.achievement!));
    return {text:mask(this.content,`🥚 ${g.label} 뽑기 ×${times}\n\n${lines.join('\n')}${highlight}\n\n${news?`🆕 새 발견 ${news}종`:`새 발견 없음 · 최고 ${rarityNames[best.c.rarity]}`}${snacks?` · 🍤 +${snacks}`:''}${achieved.length?`\n\n${achieved.join('\n\n')}`:''}\n\n${tail}`,known),
      ...(firstNew?{imageId:this.image(firstNew.c.id,known)}:{}),choices:next.choices};
  }
  private probabilityText(){
    const g=this.content.economy.gachas;
    return (['LOW','MID','HIGH'] as GachaKind[]).map(k=>`${g[k].label} 뽑기 (${g[k].cost}장): ${Object.entries(g[k].weights).map(([t,w])=>`${rarityNames[t]} ${w}%`).join(' · ')}`).join('\n');
  }

  // ───────── 조합 ─────────
  private async chooseMaterials(tx:Tx,p:Player,recipe:Recipe,known:Set<string>){
    const inventory=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:'AVAILABLE',locked:false,characterId:{in:recipe.materials}},orderBy:{id:'asc'}});
    const selected:number[]=[];
    for(const id of recipe.materials){
      const owned=inventory.find(c=>c.characterId===id&&!selected.includes(c.id));
      if(!owned)throw new GameError(`${this.name(id,known)}이(가) 부족해요.\n(보호 중이거나 탐험 중인 갈매미는 재료로 쓰지 않아요)`);
      selected.push(owned.id);
    }
    return selected;
  }
  private canCraft(recipe:Recipe,stock:Stock){
    return [...materialCounts(recipe.materials)].every(([id,n])=>statusOf(stock.get(id)).usable>=n);
  }
  private source(id:string){
    const c=this.content.map.get(id)!;
    const g=this.content.economy.gachas;
    const from=(['LOW','MID','HIGH'] as GachaKind[]).filter(k=>g[k].weights[c.rarity]).map(k=>g[k].label);
    return [...(from.length?[`${from.join('·')} 뽑기`]:[]),...(c.rarity==='COMMON'?[]:['조합'])].join(' / ');
  }
  private recipeText(recipe:Recipe,stock:Stock,known:Set<string>,collected?:boolean){
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
    return mask(this.content,`🧩 ${emblems[result.rarity]} ${result.name} (${rarityNames[result.rarity]})${flavor?`\n${flavor}`:''}\n\n${lines.join('\n')}\n\n${ready?'✨ 재료가 모두 모였어요!':`재료 ${missing}종이 더 필요해요.`}`,known);
  }
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
    if(!query)return {text:'🔮 합치기\n재료를 직접 골라 섞어요. 목록에 없는 숨은 조합도 이렇게 찾아요.\n\n합치기 황금쌍패성기사, 은하매듭직조자\n합치기 기본 갈매미맨 ×2, 황금 갈매미맨',choices:[choice('조합목록'),choice('내갈매미')]};
    const materials:string[]=[];
    for(const {name,count} of this.parseUnits(query)){
      const c=this.resolve(name,this.revealed(known),'합치기',known);
      if(!('id' in c))return c;
      for(let i=0;i<count;i++)materials.push(c.id);
    }
    if(materials.length<2)throw new GameError('재료는 2마리 이상, 쉼표(,)로 구분해 주세요.');
    const key=[...materials].sort().join('+');
    const recipe=this.content.recipes.find(r=>[...r.materials].sort().join('+')===key);
    if(!recipe)return {text:`${materials.map(id=>this.name(id,known)).join(' + ')}\n\n…아무 일도 일어나지 않았다.\n(이 조합으로는 아무것도 안 나와요)`,choices:[choice('조합목록'),choice('내갈매미')]};
    const ids=await this.chooseMaterials(tx,p,recipe,known);
    const result=this.content.map.get(recipe.resultId)!;
    if(!isRevealed(result,known))
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,`🔮 ${materials.map(id=>this.name(id,known)).join(' + ')}\n\n재료에서 낯선 기운이 느껴진다…\n결과: ${HIDDEN_NAME}\n만들어 보면 정체가 드러나요.`,undefined,`재료 ${materials.length}마리를 써요`);
    const seen=Boolean(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:result.id}}}));
    return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,this.recipeText(recipe,await this.stock(tx,p),known,seen),this.image(result.id,known),`재료 ${ids.length}마리를 써요`);
  }

  // ───────── 목록 ─────────
  private gradeOf(label:string){return tierOrder.find(t=>rarityNames[t]===label);}
  private page(query:string,total:number){
    const page=Number(query||1),pages=Math.max(1,Math.ceil(total/5));
    if(!Number.isSafeInteger(page)||page<1||page>pages)throw new GameError(`페이지는 1~${pages}까지 있어요.`);
    return {page,pages,start:(page-1)*5};
  }
  private async browse(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const stock=await this.stock(tx,p);
    const entries=await tx.collectionEntry.findMany({where:{playerId:p.id}});
    const collected=new Set(entries.map(e=>e.characterId));
    const known=new Set([...collected].filter(id=>this.content.hiddenIds.includes(id)));
    const recipeMode=command==='조합목록'||command==='조합가능';
    const grades=(recipeMode?tierOrder.filter(t=>t!=='COMMON'):tierOrder) as readonly Tier[];
    const [first,second,...extra]=query.trim().split(/\s+/).filter(Boolean);
    let grade:Tier|undefined,pageQuery=query.trim();
    if(first&&!/^\d+$/.test(first)){
      grade=this.gradeOf(first);
      if(!grade||!grades.includes(grade)||extra.length)throw new GameError(`그런 등급은 없어요. 예: ${command} ${recipeMode?'전설':'특별'}`);
      pageQuery=second??'';
    }
    if(recipeMode&&!grade){
      const visible=this.visibleRecipes(known);
      const lines=grades.map(t=>{
        const all=this.content.recipes.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const shown=visible.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const ready=shown.filter(r=>this.canCraft(r,stock)).length;
        const count=t==='HIDDEN'?`발견 ${shown.length}/${all.length}`:`${all.length}종`;
        return `${emblems[t]} ${rarityNames[t]} · ${count}${ready?` · ✨ ${ready}`:''}`;
      });
      return {text:`🧩 ${command} · 등급을 고르세요\n\n${lines.join('\n')}\n\n✨ = 지금 만들 수 있는 수\n❔ 히든은 합치기로 찾아요.`,choices:grades.slice(0,9).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`)).concat(choice('합치기'))};
    }
    let characters:Character[];
    // 재료가 몇 종 모였는지: 만들 수 있는 것과 거의 다 모인 것을 앞에 보여 줍니다.
    const progressOf=(r:Recipe)=>{const need=[...materialCounts(r.materials)];return {have:need.filter(([id,n])=>statusOf(stock.get(id)).usable>=n).length,total:need.length};};
    if(recipeMode)characters=this.visibleRecipes(known).filter(r=>command!=='조합가능'||this.canCraft(r,stock))
      .map(r=>({r,pr:progressOf(r)})).sort((a,b)=>Number(this.canCraft(b.r,stock))-Number(this.canCraft(a.r,stock))||b.pr.have/b.pr.total-a.pr.have/a.pr.total)
      .map(({r})=>this.content.map.get(r.resultId)!);
    else if(command==='내갈매미')characters=this.content.characters.filter(c=>stock.has(c.id));
    else characters=this.content.characters;
    if(command==='교환')characters=this.content.characters.filter(c=>c.rarity==='COMMON');
    if(grade)characters=characters.filter(c=>c.rarity===grade);
    if(!characters.length)return {text:command==='조합가능'?'지금 만들 수 있는 게 없어요.\n조합목록에서 모자란 재료를 확인해 보세요.':grade==='HIDDEN'&&recipeMode?'아직 발견한 히든 조합이 없습니다.\n합치기로 숨은 조합을 찾아보세요.':'아직 갈매미가 없어요. 뽑기부터 해 볼까요?',choices:[choice('조합목록'),choice('뽑기'),choice('합치기'),choice('내갈매미')]};
    const {page,pages,start}=this.page(pageQuery,characters.length);
    const items:ListEntry[]=characters.slice(start,start+5).map(c=>{
      const have=statusOf(stock.get(c.id));
      const recipe=this.content.recipes.find(r=>r.resultId===c.id);
      if(!isRevealed(c,known))return {title:HIDDEN_NAME,description:'❔ 아직 못 찾은 히든',message:'합치기'};
      const pr=recipe?progressOf(recipe):{have:0,total:0};
      const status=recipeMode?(this.canCraft(recipe!,stock)?'✨ 만들 수 있어요':`재료 ${pr.have}/${pr.total}종`):
        command==='내갈매미'?`${have.total}마리${have.away?` · 탐험 ${have.away}`:''}${have.locked?` · 보호 ${have.locked}`:''}`:
        command==='교환'?`🍤 ${this.content.economy.exchange.snackCost}`:collected.has(c.id)?'✅ 수집':'⬜ 미수집';
      return {title:`${emblems[c.rarity]} ${c.name}`,description:`${rarityNames[c.rarity]} · ${status}`,message:`${recipeMode?'조합':command} ${c.name}`};
    });
    const base=grade?`${command} ${rarityNames[grade]}`:command;
    const nav=[...(page>1?[choice('이전',`${base} ${page-1}`)]:[]),...(page<pages?[choice('다음 페이지',`${base} ${page+1}`)]:[])];
    const gradeChoices=grades.filter(t=>t!==grade).slice(0,6).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`));
    return listReply(`${base} · ${page} / ${pages}`,items,command==='교환'?'교환할 흔함을 눌러 주세요.':recipeMode?'눌러서 재료를 확인해요.':'눌러서 자세히 봐요.',[...nav,...(command==='교환'?[]:gradeChoices),...menu.slice(0,2)].slice(0,10));
  }
  private async detail(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const known=await this.known(tx,p);
    const c=this.resolve(query,this.revealed(known),command,known);
    if(!('id' in c))return c;
    const entry=await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}});
    const count=statusOf((await this.stock(tx,p)).get(c.id));
    const held=[count.locked?`🔒 보호 ${count.locked}`:'',count.away?`🧭 탐험 ${count.away}`:''].filter(Boolean).join(' · ');
    const own=entry?`🐦 ${count.total}마리 보유${held?` (${held})`:''} · ${entry.count}번 만남`:'⬜ 아직 못 만났어요';
    const text=mask(this.content,`${emblems[c.rarity]} 【${c.name}】 ${characterInfo(this.content,c)}\n\n${c.introduction}\n\n${speech(c)}\n\n${own}`,known);
    const manage=command==='내갈매미';
    return {text,imageId:entry?this.image(c.id,known):undefined,choices:[
      ...(c.rarity==='COMMON'?[choice('교환',`교환 ${c.name}`)]:[choice('조합식 보기',`조합식 ${c.name}`)]),
      ...(c.synergy&&count.usable+count.locked?[choice('탐험 보내기',`탐험보내기 ${c.name}`)]:[]),
      ...(manage&&count.usable?[choice('1마리 보호',`잠금 ${c.name}`)]:[]),
      ...(manage&&count.locked?[choice('1마리 보호 해제',`잠금해제 ${c.name}`)]:[]),
      ...(manage?[]:[choice('보유 상태',`내갈매미 ${c.name}`)]),
      ...menu.slice(0,4)
    ].slice(0,10)};
  }

  // ───────── 탐험 ─────────
  private async activeExpedition(tx:Tx,p:Player){
    return tx.expedition.findFirst({where:{playerId:p.id,claimedAt:null}});
  }
  private effectText(key:string,level:1|2){
    const fx=this.content.economy.expedition.effects as Record<string,Record<string,unknown>>;
    const i=level-1,v=(k:string)=>(fx[key][k] as number[])[i];
    return key==='BASIC'?`새우깡 +${v('snackPercent')}%`:key==='GOLD'?`${v('doublePercent')}% 확률로 새우깡 2배`:key==='DARK'?`${v('minutes')}분 만에 귀환`:
      key==='SEA'?`${v('commonPercent')}% 확률로 흔함 1마리`:key==='BLOSSOM'?`${v('uncommonPercent')}% 확률로 안흔함 1마리`:key==='COSMOS'?`${v('ticketPercent')}% 확률로 뽑기권 1장`:`마리당 새우깡 +${v('snackPerUnit')}`;
  }
  private planText(plan:ExpeditionPlan){
    const syn=plan.synergies.map(s=>`✨ ${s.label} ×${s.count} (${s.level}단계) · ${this.effectText(s.key,s.level)}`);
    if(plan.countBonus)syn.push(`✨ 같은 유닛 ×${plan.countBonus.count} · 갈매미 ${['','한','두','세','네','다섯'][Math.min(plan.countBonus.count,5)]} 마리 +${plan.countBonus.percent}%`);
    return `🧭 ${plan.size}마리 · ⏱ ${duration(plan.minutes*60000)} · 🍤 예상 ${expectedSnacks(plan)}개\n${syn.length?syn.join('\n'):'시너지 없음 (같은 시너지 2마리부터)'}`;
  }
  private async expeditionStatus(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    const e=this.content.economy.expedition;
    if(active){
      const plan=active.plan as unknown as ExpeditionPlan;
      const units=[...materialCounts(active.unitIds as string[])].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
      const left=active.endsAt.getTime()-now.getTime();
      return {text:`${left>0?`🧭 탐험 중 · ${duration(left)} 뒤 귀환`:'✅ 탐험대가 돌아왔어요! 보상을 받으세요.'}\n\n${units}\n\n${this.planText(plan)}`,choices:[...(left>0?[]:[choice('탐험 보상 받기','탐험보상받기')]),...menu]};
    }
    const owned=await tx.ownedCharacter.groupBy({by:['characterId'],where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},_count:{_all:true}});
    if(!owned.length)return {text:`🧭 탐험\n최상위 갈매미(제한·초월·영원·불멸)를 보내면 ${duration(e.minutes*60000)} 뒤 새우깡을 가져와요.\n1마리당 🍤 ${e.snackPerUnit} · 최대 ${e.maxParty}마리 · 같은 시너지끼리 보내면 보너스!\n\n아직 보낼 갈매미가 없어요. 전설을 모아 최상위를 만들어 보세요.`,choices:[choice('조합목록'),...menu]};
    const lines=owned.map(o=>{const c=this.content.map.get(o.characterId)!;return `${emblems[c.rarity]} ${c.name}${o._count._all>1?` ×${o._count._all}`:''} · ${e.effects[c.synergy!].label}`;});
    return {text:`🧭 탐험 보낼 갈매미\n\n${lines.join('\n')}\n\n자동 편성이 편해요. 직접 고르려면\n탐험보내기 이름, 이름 ×2 (최대 ${e.maxParty}마리)`,choices:[choice('자동 편성','탐험보내기 자동'),choice('시너지 안내','시너지'),...menu]};
  }
  private synergyGuide(){
    const e=this.content.economy.expedition,fx=e.effects;
    const rows=[
      `기본 (${fx.BASIC.concept}): 새우깡 +${fx.BASIC.snackPercent[0]}% / +${fx.BASIC.snackPercent[1]}%`,
      `황금 (${fx.GOLD.concept}): ${fx.GOLD.doublePercent[0]}% / ${fx.GOLD.doublePercent[1]}% 확률로 새우깡 2배`,
      `암흑 (${fx.DARK.concept}): 탐험 시간 ${fx.DARK.minutes[0]}분 / ${fx.DARK.minutes[1]}분`,
      `바다 (${fx.SEA.concept}): ${fx.SEA.commonPercent[0]}% / ${fx.SEA.commonPercent[1]}% 확률로 흔함 1마리`,
      `벚꽃 (${fx.BLOSSOM.concept}): ${fx.BLOSSOM.uncommonPercent[0]}% / ${fx.BLOSSOM.uncommonPercent[1]}% 확률로 안흔함 1마리`,
      `우주 (${fx.COSMOS.concept}): ${fx.COSMOS.ticketPercent[0]}% / ${fx.COSMOS.ticketPercent[1]}% 확률로 뽑기권 1장`,
      `갈 의복 (${fx.OUTFIT.concept}): 마리당 새우깡 +${fx.OUTFIT.snackPerUnit[0]} / +${fx.OUTFIT.snackPerUnit[1]}`,
      `같은 유닛 여러 마리: ${Object.entries(e.countBonusPercent).map(([n,v])=>`${n}마리 +${v}%`).join(' · ')}`
    ];
    return `🧭 탐험 시너지\n같은 시너지 ${e.levels[0]}마리 이상 = 1단계 / ${e.levels[1]}마리 이상 = 2단계 (같은 유닛 중복 포함)\n여러 시너지가 함께 발동하면 효과를 모두 더합니다.\n\n${rows.join('\n')}`;
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
      for(const {name,count} of this.parseUnits(query)){
        const c=this.resolve(name,pool,'탐험보내기',new Set());
        if(!('id' in c))return c;
        const free=available.filter(o=>o.characterId===c.id&&!selected.includes(o));
        if(free.length<count)throw new GameError(`${c.name}은(는) 지금 ${free.length}마리만 보낼 수 있어요.`);
        selected.push(...free.slice(0,count));
      }
    }
    if(selected.length>e.maxParty)throw new GameError(`한 번에 ${e.maxParty}마리까지만 보낼 수 있어요.`);
    const plan=planExpedition(this.content,selected.map(o=>o.characterId));
    const units=[...materialCounts(selected.map(o=>o.characterId))].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
    return this.issue(tx,p,'EXPEDITION',{ids:selected.map(o=>o.id)},now,`🧭 탐험대 편성\n${units}\n\n${this.planText(plan)}`);
  }
  private async claimExpedition(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    if(!active)return {text:'진행 중인 탐험이 없습니다. 탐험에서 보내 보세요.',choices:[choice('탐험'),...menu]};
    if(active.endsAt>now)return {text:`🧭 아직 탐험 중이에요. ${duration(active.endsAt.getTime()-now.getTime())} 뒤 돌아와요.`,choices:[choice('탐험'),...menu]};
    const marked=await tx.expedition.updateMany({where:{id:active.id,claimedAt:null},data:{claimedAt:now}});
    if(marked.count!==1)throw new GameError('이미 받은 탐험 보상이에요.');
    const plan=active.plan as unknown as ExpeditionPlan;
    const rolled=rollExpedition(this.content,plan,this.roll);
    await tx.ownedCharacter.updateMany({where:{expeditionId:active.id,playerId:p.id,status:'EXPEDITION'},data:{status:'AVAILABLE',expeditionId:null}});
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{snack:true,credits:true}});
    const snacks=Math.max(0,Math.min(rolled.snacks,2_000_000_000-account.snack));
    await tx.player.update({where:{id:p.id},data:{snack:{increment:snacks},credits:{increment:rolled.tickets},revision:{increment:1}}});
    const known=await this.known(tx,p);
    const lines=[`🍤 새우깡 +${snacks}${rolled.doubled?' (황금 시너지 2배!)':''}`];
    let imageId:string|undefined;
    for(const id of [rolled.commonId,rolled.uncommonId].filter((x):x is string=>Boolean(x))){
      const owned=await this.grant(tx,p,id,'EXPEDITION',now);
      const c=this.content.map.get(id)!;
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
    const five=plan.countBonus&&plan.countBonus.count>=5?'\n\n갈매미 한 마리… 갈매미 두 마리… 갈매미 세 마리… 네 마리…\n(정적)\n다섯 마리.\n앙~~~~ 갈매미맨이야!!!!':'';
    return {text:mask(this.content,`🧭 탐험대 귀환!${five}\n\n${lines.join('\n')}\n\n${[...missions,next.line].join('\n')}`,known),imageId,choices:next.choices};
  }

  // ───────── 확정 · 취소 ─────────
  private async cancel(tx:Tx,p:Player,token:string):Promise<GameReply>{
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청을 찾지 못했습니다.');
    if(action.result)return {text:'이미 완료된 요청입니다. 다시 처리하거나 취소하지 않습니다.',choices:menu};
    await tx.pendingAction.delete({where:{token}});
    return {text:'취소했습니다. 유닛과 재화는 사용하지 않았습니다.',choices:menu};
  }
  private async confirm(tx:Tx,p:Player,token:string,now:Date):Promise<GameReply>{
    if(!/^[a-f0-9]{16}$/.test(token))throw new GameError('확인 카드의 버튼을 눌러 주세요.');
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청이 아니에요. 처음부터 다시 해 주세요.');
    if(action.result)return action.result as unknown as GameReply;
    if(action.expiresAt<=now)throw new GameError('⏳ 10분이 지나 취소됐어요. 다시 해 주세요.');
    if(action.revision!==p.revision)throw new GameError('그 사이 보유 상태가 바뀌었어요. 다시 해 주세요.');
    const revised=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{revision:{increment:1}}});
    if(revised.count!==1)throw new GameError('다른 요청이 먼저 처리됐어요. 다시 해 주세요.');
    const payload=action.payload as ActionPayload;
    const known=await this.known(tx,p);
    let result:GameReply;
    if(action.kind==='RECEIVE'){
      const c=this.content.map.get(payload.characterId??'');
      if(!c||c.rarity!=='COMMON')throw new GameError('교환할 수 없는 유닛입니다.');
      if(!(await this.hasTop(tx,p)))throw new GameError('🔒 교환은 최상위 갈매미가 있어야 열려요.');
      const cost=this.content.economy.exchange.snackCost;
      if(p.snack<cost)throw new GameError('🍤 새우깡이 부족해요.');
      await tx.player.update({where:{id:p.id},data:{snack:{decrement:cost}}});
      const owned=await this.grant(tx,p,c.id,'EXCHANGE',now);
      const next=await this.nextStep(tx,p,now,[choice('더 교환','교환')]);
      result={text:`🍤 → ${acquisitionText(this.content,c,'EXCHANGE',owned.receipt,known)}\n\n${next.line}`,...(owned.receipt.first?{imageId:this.image(c.id,known)}:{}),choices:next.choices};
      await this.audit(tx,p,'EXCHANGE',{token,characterId:c.id,ownedId:owned.id,delta:-cost,balanceAfter:p.snack-cost});
    }else if(action.kind==='CRAFT'){
      const recipe=this.content.recipes.find(r=>r.resultId===payload.recipeId);
      if(!recipe)throw new GameError('조합식이 없습니다.');
      const ids=payload.ids??[];
      const selected=await this.chooseMaterials(tx,p,recipe,known);
      if(ids.length!==selected.length||ids.some((id,i)=>id!==selected[i]))throw new GameError('재료 상태가 바뀌었어요. 다시 해 주세요.');
      const update=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',locked:false},data:{status:'CONSUMED',consumedAt:now}});
      if(update.count!==ids.length)throw new GameError('재료가 겹쳤어요. 다시 해 주세요.');
      const owned=await this.grant(tx,p,recipe.resultId,'COMBINATION',now);
      const c=this.content.map.get(recipe.resultId)!;
      const after=new Set([...known,...(c.rarity==='HIDDEN'?[c.id]:[])]);
      const discovered=c.rarity==='HIDDEN'&&owned.receipt.first?'🔓 숨은 조합을 발견했습니다!\n\n':'';
      const missions=await this.progress(tx,p,'CRAFT',1,now);
      const next=await this.nextStep(tx,p,now,c.synergy?[choice('탐험 보내기','탐험')]:[]);
      result={text:`${discovered}${acquisitionText(this.content,c,'COMBINATION',owned.receipt,after)}\n\n${[...missions,next.line].join('\n')}`,...(owned.receipt.first?{imageId:this.image(c.id,after)}:{}),
        choices:[...next.choices.slice(0,2),...(owned.receipt.first?[choice('유닛 보기',`도감 ${c.name}`)]:[]),...next.choices.slice(2)]};
      await this.audit(tx,p,'CRAFT',{token,recipe:recipe.resultId,consumed:ids,ownedId:owned.id});
    }else if(action.kind==='EXPEDITION'){
      if(await this.activeExpedition(tx,p))throw new GameError('이미 탐험 중입니다.');
      const ids=payload.ids??[];
      const units=await tx.ownedCharacter.findMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}}});
      if(units.length!==ids.length)throw new GameError('보낼 갈매미 상태가 바뀌었어요. 다시 편성해 주세요.');
      const unitIds=ids.map(id=>units.find(u=>u.id===id)!.characterId);
      const plan=planExpedition(this.content,unitIds);
      const ends=new Date(now.getTime()+plan.minutes*60000);
      const expedition=await tx.expedition.create({data:{playerId:p.id,unitIds:json(unitIds),plan:json(plan),startedAt:now,endsAt:ends}});
      const moved=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE'},data:{status:'EXPEDITION',expeditionId:expedition.id}});
      if(moved.count!==ids.length)throw new GameError('탐험 출발 충돌입니다. 다시 시도하세요.');
      result={text:`🧭 탐험대 출발! (${ids.length}마리)\n⏱ ${duration(plan.minutes*60000)} 뒤 돌아와요.\n돌아오면 '탐험 보상 받기'를 눌러 주세요. 🍤 예상 ${expectedSnacks(plan)}개`,choices:[choice('탐험'),...menu]};
      await this.audit(tx,p,'EXPEDITION_SEND',{token,expeditionId:expedition.id,ownedIds:ids,unitIds,minutes:plan.minutes});
    }else throw new GameError('지원하지 않는 요청입니다.');
    await tx.pendingAction.update({where:{token},data:{result:json(result)}});
    return result;
  }

  // ───────── 명령 분기 ─────────
  private async dispatch(tx:Tx,p:Player,message:string,now:Date,button?:ButtonAction):Promise<GameReply>{
    const trimmed=message.trim().replace(/^[!/]/,'');
    const parts=trimmed.split(/\s+/);
    const [command,...args]=parts;
    const query=args.join(' ');
    const compact=trimmed.replace(/\s+/g,'');
    if(button){
      const visible=parts.join(' ');
      if(button.action==='confirm'&&['조합 확정','교환 확정','탐험 확정'].includes(visible))return this.confirm(tx,p,button.token,now);
      if(button.action==='cancel'&&visible==='취소')return this.cancel(tx,p,button.token);
      throw new GameError('버튼 요청이 일치하지 않습니다. 미리보기를 다시 열어 주세요.');
    }
    if(['조합 확정','교환 확정','탐험 확정','취소'].includes(parts.join(' ')))return {text:'미리보기의 진행 또는 취소 항목을 직접 눌러 주세요.',choices:menu};
    if(['받기','뽑기권받기','뽑기권','충전'].includes(compact))return this.claim(tx,p,now);
    const gm=compact.match(/^(?:(하급|중급|고급)뽑기|뽑기(하급|중급|고급))(\d+)?$/);
    if(gm){
      const times=Number(gm[3]??1);
      if(!Number.isSafeInteger(times)||times<1||times>10)throw new GameError('뽑기는 한 번에 1~10회예요. 예: 하급뽑기 5');
      return this.draw(tx,p,gachaWords[gm[1]??gm[2]],times,now);
    }
    if(compact==='뽑기')return this.gachaMenu(tx,p,now);
    if(command==='확률')return {text:`🎲 뽑기 확률\n\n${this.probabilityText()}\n\n같은 등급 안에선 모두 같은 확률이에요.`,choices:[choice('뽑기'),...menu.slice(1)]};
    if(command==='보상')return {text:rewardGuide(this.content),choices:[choice('교환'),...menu]};
    if(command==='교환'){
      const cost=this.content.economy.exchange.snackCost;
      if(!query||/^\d+$/.test(query)){
        const reply=await this.browse(tx,p,'교환',query);
        const gate=(await this.hasTop(tx,p))?'':'\n🔒 최상위 갈매미가 생기면 열려요.';
        return {...reply,text:`${reply.text}\n\n🍤 ${cost}개 = 흔함 1마리 · 지금 🍤 ${p.snack}개${gate}`};
      }
      if(!(await this.hasTop(tx,p)))throw new GameError(`🔒 교환은 최상위 갈매미(제한·초월·영원·불멸)가 있어야 열려요.\n새우깡 ${p.snack}개는 그대로 모아 둘게요.`);
      const c=this.resolve(query,this.content.characters.filter(c=>c.rarity==='COMMON'),'교환',new Set());
      if(!('id' in c))return c;
      if(p.snack<cost)throw new GameError(`🍤 새우깡이 부족해요. ${cost}개 필요, 지금 ${p.snack}개.`);
      return this.issue(tx,p,'RECEIVE',{characterId:c.id},now,`🍤 → ${emblems[c.rarity]} ${c.name}\n새우깡 ${cost}개 (지금 ${p.snack}개)`,this.image(c.id,new Set()),`새우깡 ${cost}개를 써요`);
    }
    if(command==='합치기')return this.combine(tx,p,query,now);
    if(command==='조합'||command==='조합식'){
      if(!query)return this.browse(tx,p,'조합목록','');
      const known=await this.known(tx,p);
      const c=this.resolve(query,this.revealed(known,c=>c.rarity!=='COMMON'),command,known);
      if(!('id' in c))return c;
      const recipe=this.content.recipes.find(r=>r.resultId===c.id)!;
      const stock=await this.stock(tx,p);
      const seen=Boolean(await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}}));
      const text=this.recipeText(recipe,stock,known,seen);
      if(command==='조합식'||!this.canCraft(recipe,stock)){
        // 모자란 재료로 바로 가는 버튼: 조합 재료는 그 조합식, 뽑기 재료는 해당 뽑기
        const lacking=[...materialCounts(recipe.materials)].filter(([id,n])=>statusOf(stock.get(id)).usable<n).map(([id])=>this.content.map.get(id)!);
        const jump=lacking.filter(m=>m.rarity!=='COMMON'&&isRevealed(m,known)).slice(0,3).map(m=>choice(`${m.name.slice(0,9)} 조합식`,`조합식 ${m.name}`));
        return {text,imageId:this.image(c.id,known),choices:[...(this.canCraft(recipe,stock)?[choice('조합하기',`조합 ${c.name}`)]:[]),...jump,choice('뽑기'),choice('조합가능'),choice('조합목록'),choice('내갈매미')]};
      }
      const ids=await this.chooseMaterials(tx,p,recipe,known);
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,text,this.image(c.id,known),`재료 ${ids.length}마리를 써요`);
    }
    if(command==='잠금'||command==='잠금해제'){
      if(!query)return {text:`${command} 뒤에 이름을 붙여 주세요. 예: ${command} 금갑\n🔒 보호한 갈매미는 조합 재료로 안 써요.`,choices:[choice('내갈매미')]};
      const known=await this.known(tx,p);
      const c=this.resolve(query,this.revealed(known),command,known);
      if(!('id' in c))return c;
      const locked=command==='잠금';
      const owned=await tx.ownedCharacter.findFirst({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:!locked},orderBy:{id:'asc'}});
      if(!owned)return {text:`지금 ${command}할 수 있는 ${c.name}이(가) 없어요.\n(보유 수량, 보호, 탐험 상태를 확인해 주세요)`,choices:[choice('보유 상태',`내갈매미 ${c.name}`)]};
      await tx.ownedCharacter.update({where:{id:owned.id},data:{locked}});
      await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
      await this.audit(tx,p,'LOCK',{id:owned.id,locked});
      return {text:locked?`🔒 ${c.name} 1마리를 보호했어요. 조합 재료로 안 써요.`:`🔓 ${c.name} 1마리 보호를 풀었어요.`,choices:[choice('보유 상태',`내갈매미 ${c.name}`),...menu.slice(0,3)]};
    }
    if(command==='탐험'&&!query)return this.expeditionStatus(tx,p,now);
    if(compact.startsWith('탐험보내기')||(command==='탐험'&&args[0]==='보내기'))return this.sendExpedition(tx,p,trimmed.replace(/^탐험\s*보내기/u,'').trim(),now);
    if(['탐험보상받기','탐험보상','탐험받기'].includes(compact))return this.claimExpedition(tx,p,now);
    if(command==='시너지')return {text:this.synergyGuide(),choices:[choice('탐험'),...menu]};
    if(command==='관리자'||command==='관리자모드'){
      const sub=args[0]??'';
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
    if(command==='미션')return {text:`🎯 오늘의 미션 · 밤 12시 초기화\n\n${await this.missionText(tx,p,now)}\n\n채우는 순간 바로 받아요.`,choices:[choice('뽑기'),choice('조합가능'),choice('탐험'),...menu]};
    if(command==='칭호'){
      const got=await this.titles(tx,p);
      const all=tierOrder.map(t=>`${got.includes(this.content.economy.achievements[t].title)?'🏆':'・'} ${rarityNames[t]} 도감 완성 → 「${this.content.economy.achievements[t].title}」 · 🎟 ${this.content.economy.achievements[t].tickets}`);
      return {text:`🏆 칭호 ${got.length}/${tierOrder.length}\n\n${all.join('\n')}\n\n등급 도감을 다 채우면 칭호와 뽑기권을 받아요.`,choices:[choice('도감'),...menu]};
    }
    if(command==='내정보'){
      const count=await tx.collectionEntry.count({where:{playerId:p.id}});
      const active=await this.activeExpedition(tx,p);
      const trip=active?(active.endsAt>now?`탐험 중 · 귀환까지 ${duration(active.endsAt.getTime()-now.getTime())}`:'탐험 귀환 · 보상 받기 대기'):'탐험 없음';
      const titles=await this.titles(tx,p);
      return {text:`🏝 갈매미 섬${titles.length?` · 「${titles.at(-1)}」`:''}${p.isAdmin?(p.adminMode?' · 🛠 관리자 모드':' · 관리자(모드 꺼짐)'):''}\n🎟 ${unlimited(p)?'무제한':`${p.credits}장`} · 🍤 ${p.snack}개\n📚 도감 ${count}/${this.content.characters.length} · 🏆 칭호 ${titles.length}개\n${this.claimHint(p,now)}\n🧭 ${trip}\n\n🎯 오늘의 미션\n${await this.missionText(tx,p,now)}`,choices:[choice('미션'),choice('칭호'),...menu]};
    }
    if((command==='도감'||command==='내갈매미')&&query&&!/^\d+$/.test(query)&&!this.gradeOf(args[0]))return this.detail(tx,p,command,query);
    if(['내갈매미','도감','조합목록','조합가능'].includes(command))return this.browse(tx,p,command,query);
    if(['출석','재료','땅콩떼기','땅콩교환','교배'].includes(command))return {text:'이 기능은 개편으로 종료되었어요.\n🎟 뽑기권은 "뽑기권 받기", 🍤 새우깡은 획득 보상과 탐험으로 얻어요.',choices:menu};
    return {text:help,choices:menu};
  }
}
