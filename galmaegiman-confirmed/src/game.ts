import { randomBytes, randomInt } from 'node:crypto';
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
const help='갈매미맨\n\n🎯 미션 — 오늘의 미션 / 🏆 칭호\n🎟 뽑기권 받기 — 10장 (5시간마다)\n🥚 뽑기 — 하급 / 중급 / 고급\n🧩 조합목록 — 이름을 눌러 재료 확인\n✅ 조합가능 — 지금 만들 수 있는 유닛\n🔮 합치기 — 재료를 직접 골라 조합 (숨은 조합 발견)\n🐦 내갈매미 — 보유 유닛\n📚 도감 — 수집 기록\n🧭 탐험 — 최상위 유닛 파견\n\n이름으로 바로 입력해도 됩니다.\n하급뽑기 5 / 조합 금갑 / 도감 야경\n합치기 황금쌍패성기사, 은하매듭직조자\n탐험보내기 갈발 ×3, 갈내복\n\n내정보 / 확률 / 보상 / 교환';
const statusOf=(c:Count|undefined):Count=>c??{total:0,usable:0,locked:0,away:0};

export class GameService {
  private tail:Promise<unknown>=Promise.resolve();
  private pending=0;
  private readonly topIds:string[];
  constructor(private db:PrismaClient,public content:Content,private clock=()=>new Date(),
    private gacha=new GachaEngine(content.characters,content.economy.gachas),private roll:Roll=randomInt,private root=process.cwd()){
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
  private claimWait(p:Player,now:Date){
    if(!p.lastClaimAt)return 0;
    return Math.max(0,p.lastClaimAt.getTime()+this.content.economy.tickets.cooldownHours*3_600_000-now.getTime());
  }
  private claimHint(p:Player,now:Date){
    const wait=this.claimWait(p,now);
    return wait?`다음 뽑기권 받기: ${duration(wait)} 뒤`:'🎟 지금 뽑기권을 받을 수 있습니다.';
  }
  private resolve(query:string,pool:Character[],command:string,known:Set<string>):Character|GameReply{
    const found=matchCharacters(pool,query);
    if(found.length===1)return found[0];
    if(!found.length)return {text:`이름을 찾지 못했습니다.\n예: ${command} 금갑\n목록에서 유닛을 선택할 수도 있습니다.`,choices:[choice('조합목록'),choice('도감'),choice('내갈매미')]};
    return listReply('어떤 갈매미를 찾으세요?',found.slice(0,5).map(c=>({title:c.name,description:characterInfo(this.content,c),message:`${command} ${c.name}`})),
      '이름을 눌러 선택하세요. 목록에 없으면 이름을 조금 더 길게 입력하세요.',[choice('도움말')]);
  }
  private async issue(tx:Tx,p:Player,kind:string,payload:ActionPayload,now:Date,text:string,imageId?:string):Promise<GameReply>{
    await tx.pendingAction.deleteMany({where:{playerId:p.id,result:{equals:Prisma.DbNull},expiresAt:{lt:now}}});
    const token=randomBytes(8).toString('hex');
    await tx.pendingAction.create({data:{token,playerId:p.id,revision:p.revision,kind,payload:json(payload),expiresAt:new Date(now.getTime()+600_000)}});
    const [title,message]=kind==='CRAFT'?['조합하기','조합 확정']:kind==='EXPEDITION'?['탐험 보내기','탐험 확정']:['교환하기','교환 확정'];
    return {text:`${text}\n\n아래 '${title}'를 누르면 완료됩니다. (10분 동안 유효)`,imageId,list:{
      title:'진행할까요?',showText:true,items:[
        {title,description:'위 내용을 확인하고 진행합니다.',message,button:{action:'confirm',token}},
        {title:'취소',description:'유닛과 재화를 사용하지 않습니다.',message:'취소',button:{action:'cancel',token}}
      ]
    }};
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
    const lines=[`🎟 ${account.credits}장 · 🍤 ${account.snack}개`];
    if(ready)lines.push(`✨ 지금 조합 가능 ${ready}종!`);
    if(account.credits<cheapest)lines.push(canClaim?'🎟 뽑기권 받기를 눌러 10장을 받으세요!':`⏳ 다음 뽑기권까지 ${duration(this.claimWait(account,now))}`);
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
    if(wait)return {text:`아직 뽑기권을 받을 수 없습니다.\n${duration(wait)} 뒤에 다시 받을 수 있어요.\n(받은 뒤 ${t.cooldownHours}시간마다 ${t.claim}장 · 미수령분은 쌓이지 않습니다)\n\n보유 뽑기권 ${p.credits}장`,choices:menu};
    const updated=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{credits:{increment:t.claim},lastClaimAt:now,revision:{increment:1}}});
    if(updated.count!==1)throw new GameError('상태가 바뀌었습니다. 다시 뽑기권 받기를 입력하세요.');
    await this.audit(tx,p,'TICKET_CLAIM',{delta:t.claim,balanceAfter:p.credits+t.claim});
    return {text:`🎟 뽑기권 ${t.claim}장을 받았습니다!\n보유 뽑기권 ${p.credits+t.claim}장\n\n다음 받기: ${t.cooldownHours}시간 뒤`,choices:[choice('뽑기'),...menu.slice(2)]};
  }
  private gachaMenu(p:Player,now:Date):GameReply{
    const g=this.content.economy.gachas;
    const items=(['LOW','MID','HIGH'] as GachaKind[]).map(kind=>({
      title:`${g[kind].label} 뽑기 · ${g[kind].cost}장`,
      description:Object.entries(g[kind].weights).map(([t,w])=>`${rarityNames[t]} ${w}%`).join(' / '),
      message:`${g[kind].label}뽑기`
    }));
    return {...listReply(`뽑기 · 보유 뽑기권 ${p.credits}장`,items,`여러 번은 숫자를 붙이세요. 예: 하급뽑기 5 (최대 10회)\n${this.claimHint(p,now)}`),
      choices:[choice('하급 ×5','하급뽑기 5'),choice('중급 ×5','중급뽑기 5'),choice('고급 ×2','고급뽑기 2'),choice('뽑기권 받기','뽑기권받기'),choice('확률'),...menu.slice(2,6)]};
  }
  private async draw(tx:Tx,p:Player,kind:GachaKind,times:number,now:Date):Promise<GameReply>{
    const g=this.content.economy.gachas[kind];
    const cost=g.cost*times;
    if(p.credits<cost)return {text:`뽑기권이 부족합니다. ${g.label} 뽑기 ${times}회 = ${cost}장 · 보유 ${p.credits}장\n${this.claimHint(p,now)}`,choices:[choice('뽑기권 받기','뽑기권받기'),choice('뽑기'),choice('내정보')]};
    const paid=await tx.player.updateMany({where:{id:p.id,revision:p.revision,credits:{gte:cost}},data:{credits:{decrement:cost},revision:{increment:1}}});
    if(paid.count!==1)throw new GameError('보유 상태가 바뀌었습니다. 다시 뽑기를 입력하세요.');
    const known=await this.known(tx,p);
    const results:{c:Character;receipt:Receipt}[]=[];
    for(let i=0;i<times;i++){
      const c=this.gacha.draw(kind);
      const owned=await this.grant(tx,p,c.id,'GACHA',now);
      results.push({c,receipt:owned.receipt});
    }
    await this.audit(tx,p,'GACHA',{kind,times,cost,results:results.map(r=>r.c.id),balanceAfter:p.credits-cost});
    const missions=await this.progress(tx,p,'DRAW',times,now);
    const again=p.credits-cost>=cost;
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
    const highlight=firstNew&&tiers[firstNew.c.rarity]>=tiers.RARE?`\n\n${prelude(firstNew.c,true)}${emblems[firstNew.c.rarity]} ${firstNew.c.name}\n${firstNew.c.tagline}\n${speech(firstNew.c)}`:'';
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
      if(!owned)throw new GameError(`${this.name(id,known)} 재료가 부족합니다. 보호 중이거나 탐험 중인 개체는 조합에 쓰지 않습니다.`);
      selected.push(owned.id);
    }
    return selected;
  }
  private canCraft(recipe:Recipe,stock:Stock){
    return [...materialCounts(recipe.materials)].every(([id,n])=>statusOf(stock.get(id)).usable>=n);
  }
  private recipeText(recipe:Recipe,stock:Stock,known:Set<string>){
    const result=this.content.map.get(recipe.resultId)!;
    const lines=[...materialCounts(recipe.materials)].map(([id,n])=>{
      const have=statusOf(stock.get(id)).usable;
      return `${have>=n?'✓':'・'} ${this.name(id,known)}\n   필요 ${n}마리 · 사용 가능 ${have}마리`;
    });
    return mask(this.content,`🧩 ${result.name}\n${characterInfo(this.content,result)}\n\n필요 재료\n${lines.join('\n\n')}\n\n${this.canCraft(recipe,stock)?'✅ 조합 준비 완료':'재료가 부족합니다.'}\n재료를 사용해 결과 1마리를 얻습니다.\n보호 중이거나 탐험 중인 개체는 사용하지 않습니다.\n\n${recipe.story}`,known);
  }
  private visibleRecipes(known:Set<string>){
    return this.content.recipes.filter(r=>!r.hidden||known.has(r.resultId));
  }
  private parseUnits(query:string){
    return query.split(/[,，+\n]/u).map(s=>s.trim()).filter(Boolean).map(token=>{
      const m=token.match(/^(.*?)\s*(?:[x×*]\s*(\d+)|(\d+)\s*마리)$/u);
      const name=(m?m[1]:token).trim();
      const count=m?Number(m[2]??m[3]):1;
      if(!name||!Number.isSafeInteger(count)||count<1||count>10)throw new GameError('수량은 1~10으로 입력하세요. 예: 갈발 ×3');
      return {name,count};
    });
  }
  private async combine(tx:Tx,p:Player,query:string,now:Date):Promise<GameReply>{
    const known=await this.known(tx,p);
    if(!query)return {text:'재료를 직접 골라 조합합니다. 공개되지 않은 숨은 조합도 이 방법으로 발견할 수 있습니다.\n\n예: 합치기 황금쌍패성기사, 은하매듭직조자\n같은 재료 여러 마리: 합치기 기본 갈매미맨 ×2, 황금 갈매미맨',choices:[choice('조합목록'),choice('내갈매미')]};
    const materials:string[]=[];
    for(const {name,count} of this.parseUnits(query)){
      const c=this.resolve(name,this.revealed(known),'합치기',known);
      if(!('id' in c))return c;
      for(let i=0;i<count;i++)materials.push(c.id);
    }
    if(materials.length<2)throw new GameError('재료를 2마리 이상 입력하세요. 쉼표(,)로 구분합니다.');
    const key=[...materials].sort().join('+');
    const recipe=this.content.recipes.find(r=>[...r.materials].sort().join('+')===key);
    if(!recipe)return {text:`${materials.map(id=>this.name(id,known)).join(' + ')}\n\n…아무 일도 일어나지 않았다.\n이 재료 조합으로 만들어지는 갈매미는 없습니다.`,choices:[choice('조합목록'),choice('내갈매미')]};
    const ids=await this.chooseMaterials(tx,p,recipe,known);
    const result=this.content.map.get(recipe.resultId)!;
    if(!isRevealed(result,known))
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,`🔮 ${materials.map(id=>this.name(id,known)).join(' + ')}\n\n재료에서 낯선 기운이 느껴집니다.\n결과: ${HIDDEN_NAME} (히든)\n조합하면 정체가 공개됩니다.`);
    return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,this.recipeText(recipe,await this.stock(tx,p),known),this.image(result.id,known));
  }

  // ───────── 목록 ─────────
  private gradeOf(label:string){return tierOrder.find(t=>rarityNames[t]===label);}
  private page(query:string,total:number){
    const page=Number(query||1),pages=Math.max(1,Math.ceil(total/5));
    if(!Number.isSafeInteger(page)||page<1||page>pages)throw new GameError(`페이지는 1부터 ${pages}까지입니다.`);
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
      if(!grade||!grades.includes(grade)||extra.length)throw new GameError(`등급을 선택하세요.\n예: ${command} ${recipeMode?'전설':'특별'}`);
      pageQuery=second??'';
    }
    if(recipeMode&&!grade){
      const visible=this.visibleRecipes(known);
      const lines=grades.map(t=>{
        const all=this.content.recipes.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const shown=visible.filter(r=>this.content.map.get(r.resultId)!.rarity===t);
        const ready=shown.filter(r=>this.canCraft(r,stock)).length;
        return t==='HIDDEN'?`${rarityNames[t]} · 발견 ${shown.length}/${all.length}종 · 조합 가능 ${ready}종`:`${rarityNames[t]} · ${all.length}종 · 조합 가능 ${ready}종`;
      });
      return {text:`${command} · 등급 선택\n\n${lines.join('\n')}\n\n흔함은 조합식이 없는 기본 유닛입니다.\n히든 조합은 공개되지 않습니다. 합치기로 발견하세요.`,choices:grades.slice(0,9).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`)).concat(choice('합치기'))};
    }
    let characters:Character[];
    if(recipeMode)characters=this.visibleRecipes(known).filter(r=>command!=='조합가능'||this.canCraft(r,stock)).map(r=>this.content.map.get(r.resultId)!);
    else if(command==='내갈매미')characters=this.content.characters.filter(c=>stock.has(c.id));
    else characters=this.content.characters;
    if(command==='교환')characters=this.content.characters.filter(c=>c.rarity==='COMMON');
    if(grade)characters=characters.filter(c=>c.rarity===grade);
    if(!characters.length)return {text:command==='조합가능'?'지금 조합할 수 있는 유닛이 없습니다.\n조합목록에서 필요한 재료를 확인하세요.':grade==='HIDDEN'&&recipeMode?'아직 발견한 히든 조합이 없습니다.\n합치기로 숨은 조합을 찾아보세요.':'아직 보유한 갈매미가 없습니다. 뽑기를 입력하세요.',choices:[choice('조합목록'),choice('뽑기'),choice('합치기'),choice('내갈매미')]};
    const {page,pages,start}=this.page(pageQuery,characters.length);
    const items:ListEntry[]=characters.slice(start,start+5).map(c=>{
      const have=statusOf(stock.get(c.id));
      const recipe=this.content.recipes.find(r=>r.resultId===c.id);
      if(!isRevealed(c,known))return {title:HIDDEN_NAME,description:'히든 · 미발견',message:'합치기'};
      const status=recipeMode?(this.canCraft(recipe!,stock)?'✅ 조합 가능':'재료 모으는 중'):
        command==='내갈매미'?`보유 ${have.total} · 사용 가능 ${have.usable}${have.away?` · 탐험 중 ${have.away}`:''}`:
        command==='교환'?`새우깡 ${this.content.economy.exchange.snackCost}개`:collected.has(c.id)?'수집 완료':'미수집';
      return {title:c.name,description:`${characterInfo(this.content,c)} · ${status}`,message:`${recipeMode?'조합':command} ${c.name}`};
    });
    const base=grade?`${command} ${rarityNames[grade]}`:command;
    const nav=[...(page>1?[choice('이전',`${base} ${page-1}`)]:[]),...(page<pages?[choice('다음 페이지',`${base} ${page+1}`)]:[])];
    const gradeChoices=grades.filter(t=>t!==grade).slice(0,6).map(t=>choice(rarityNames[t],`${command} ${rarityNames[t]}`));
    return listReply(`${base} · ${page} / ${pages}`,items,command==='교환'?'흔함 이름을 눌러 교환을 준비하세요.':'이름을 눌러 이미지와 자세한 정보를 보세요.',[...nav,...(command==='교환'?[]:gradeChoices),...menu.slice(0,2)].slice(0,10));
  }
  private async detail(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const known=await this.known(tx,p);
    const c=this.resolve(query,this.revealed(known),command,known);
    if(!('id' in c))return c;
    const entry=await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}});
    const count=statusOf((await this.stock(tx,p)).get(c.id));
    const text=mask(this.content,`【${c.name}】\n${characterInfo(this.content,c)}\n${c.tagline}\n\n📜 도감 기록\n${c.introduction}\n\n${speech(c)}\n\n보유 ${count.total}마리 · 조합에 사용 가능 ${count.usable}마리\n보호 ${count.locked}마리 · 탐험 중 ${count.away}마리\n${entry?`누적 ${entry.count}회 획득`:'아직 수집하지 못했습니다.'}`,known);
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
  private planText(plan:ExpeditionPlan){
    const fx=this.content.economy.expedition.effects;
    const syn=plan.synergies.map(s=>`・${s.label} ${s.count}마리 → ${s.level}단계 (${fx[s.key].concept})`);
    if(plan.countBonus)syn.push(`・같은 유닛 ${plan.countBonus.count}마리 → 갈매미 ${['','한','두','세','네','다섯'][Math.min(plan.countBonus.count,5)]} 마리 +${plan.countBonus.percent}%`);
    const extras=[
      plan.doublePercent?`새우깡 2배 확률 ${plan.doublePercent}%`:'',plan.commonPercent?`흔함 1마리 확률 ${plan.commonPercent}%`:'',
      plan.uncommonPercent?`안흔함 1마리 확률 ${plan.uncommonPercent}%`:'',plan.ticketPercent?`뽑기권 1장 확률 ${plan.ticketPercent}%`:''
    ].filter(Boolean);
    return `파견 ${plan.size}마리 · 소요 ${duration(plan.minutes*60000)}\n기본 새우깡 ${plan.baseSnacks}개 → 시너지 반영 ${expectedSnacks(plan)}개\n\n시너지\n${syn.length?syn.join('\n'):'・없음 (같은 시너지 2마리 이상부터 발동)'}${extras.length?`\n\n추가 보상\n${extras.map(x=>`・${x}`).join('\n')}`:''}`;
  }
  private async expeditionStatus(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    const e=this.content.economy.expedition;
    if(active){
      const plan=active.plan as unknown as ExpeditionPlan;
      const units=[...materialCounts(active.unitIds as string[])].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
      const left=active.endsAt.getTime()-now.getTime();
      return {text:`🧭 탐험 중\n\n${units}\n\n${this.planText(plan)}\n\n${left>0?`귀환까지 ${duration(left)}`:'✅ 귀환했습니다! 보상을 받으세요.'}`,choices:[...(left>0?[]:[choice('탐험 보상 받기','탐험보상받기')]),...menu]};
    }
    const owned=await tx.ownedCharacter.groupBy({by:['characterId'],where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},_count:{_all:true}});
    if(!owned.length)return {text:`🧭 탐험\n\n최상위 유닛(제한·초월·영원·불멸)을 보내 ${duration(e.minutes*60000)} 뒤 새우깡을 받습니다.\n1마리당 새우깡 ${e.snackPerUnit}개 · 최대 ${e.maxParty}마리\n같은 시너지를 ${e.levels[0]}마리/${e.levels[1]}마리 이상 함께 보내면 시너지 효과가 발동합니다.\n\n아직 보낼 수 있는 최상위 유닛이 없습니다.`,choices:[choice('조합목록'),...menu]};
    const lines=owned.map(o=>{const c=this.content.map.get(o.characterId)!;return `・${c.name} ×${o._count._all} (${e.effects[c.synergy!].label})`;});
    return {text:`🧭 탐험 · 보낼 수 있는 유닛\n\n${lines.join('\n')}\n\n보내기: 탐험보내기 이름, 이름 ×2\n자동 편성: 탐험보내기 자동\n최대 ${e.maxParty}마리 · 1마리당 새우깡 ${e.snackPerUnit}개 · ${duration(e.minutes*60000)}\n탐험 중인 유닛은 돌아와서 보상을 받을 때까지 어떤 행위에도 쓸 수 없습니다.`,choices:[choice('자동 편성','탐험보내기 자동'),choice('시너지 안내','시너지'),...menu]};
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
    if(await this.activeExpedition(tx,p))throw new GameError('이미 탐험 중입니다. 탐험을 입력해 상태를 확인하세요.');
    const e=this.content.economy.expedition;
    const available=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:'AVAILABLE',characterId:{in:this.topIds}},orderBy:{id:'asc'}});
    if(!available.length)throw new GameError('보낼 수 있는 최상위 유닛이 없습니다.');
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
        if(free.length<count)throw new GameError(`${c.name}: 보낼 수 있는 개체가 ${free.length}마리뿐입니다.`);
        selected.push(...free.slice(0,count));
      }
    }
    if(selected.length>e.maxParty)throw new GameError(`한 번에 최대 ${e.maxParty}마리까지 보낼 수 있습니다.`);
    const plan=planExpedition(this.content,selected.map(o=>o.characterId));
    const units=[...materialCounts(selected.map(o=>o.characterId))].map(([id,n])=>`${this.content.map.get(id)!.name}${n>1?` ×${n}`:''}`).join(', ');
    return this.issue(tx,p,'EXPEDITION',{ids:selected.map(o=>o.id)},now,`🧭 탐험 준비\n\n${units}\n\n${this.planText(plan)}\n\n탐험 중인 유닛은 돌아와서 보상을 받을 때까지 조합·보호 등 어떤 행위에도 쓸 수 없습니다.`);
  }
  private async claimExpedition(tx:Tx,p:Player,now:Date):Promise<GameReply>{
    const active=await this.activeExpedition(tx,p);
    if(!active)return {text:'진행 중인 탐험이 없습니다.',choices:[choice('탐험'),...menu]};
    if(active.endsAt>now)return {text:`아직 탐험 중입니다. 귀환까지 ${duration(active.endsAt.getTime()-now.getTime())}`,choices:[choice('탐험'),...menu]};
    const marked=await tx.expedition.updateMany({where:{id:active.id,claimedAt:null},data:{claimedAt:now}});
    if(marked.count!==1)throw new GameError('이미 받은 탐험 보상입니다.');
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
    if(!/^[a-f0-9]{16}$/.test(token))throw new GameError('미리보기 아래 진행 항목을 눌러 주세요.');
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청을 찾지 못했습니다. 다시 미리보기를 여세요.');
    if(action.result)return action.result as unknown as GameReply;
    if(action.expiresAt<=now)throw new GameError('10분이 지나 만료됐습니다. 다시 미리보기를 여세요.');
    if(action.revision!==p.revision)throw new GameError('보유 상태가 바뀌었습니다. 다시 미리보기를 여세요.');
    const revised=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{revision:{increment:1}}});
    if(revised.count!==1)throw new GameError('다른 요청이 먼저 처리됐습니다. 다시 미리보기를 여세요.');
    const payload=action.payload as ActionPayload;
    const known=await this.known(tx,p);
    let result:GameReply;
    if(action.kind==='RECEIVE'){
      const c=this.content.map.get(payload.characterId??'');
      if(!c||c.rarity!=='COMMON')throw new GameError('교환할 수 없는 유닛입니다.');
      if(!(await this.hasTop(tx,p)))throw new GameError('교환은 최상위 유닛을 보유해야 할 수 있습니다.');
      const cost=this.content.economy.exchange.snackCost;
      if(p.snack<cost)throw new GameError('새우깡이 부족합니다. 상태를 확인하고 다시 진행하세요.');
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
      if(ids.length!==selected.length||ids.some((id,i)=>id!==selected[i]))throw new GameError('재료 상태가 바뀌었습니다. 다시 확인하세요.');
      const update=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',locked:false},data:{status:'CONSUMED',consumedAt:now}});
      if(update.count!==ids.length)throw new GameError('재료 소비 충돌입니다. 다시 확인하세요.');
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
      if(units.length!==ids.length)throw new GameError('보낼 유닛의 상태가 바뀌었습니다. 다시 탐험보내기를 입력하세요.');
      const unitIds=ids.map(id=>units.find(u=>u.id===id)!.characterId);
      const plan=planExpedition(this.content,unitIds);
      const ends=new Date(now.getTime()+plan.minutes*60000);
      const expedition=await tx.expedition.create({data:{playerId:p.id,unitIds:json(unitIds),plan:json(plan),startedAt:now,endsAt:ends}});
      const moved=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE'},data:{status:'EXPEDITION',expeditionId:expedition.id}});
      if(moved.count!==ids.length)throw new GameError('탐험 출발 충돌입니다. 다시 시도하세요.');
      result={text:`🧭 탐험 출발! ${ids.length}마리\n귀환: ${duration(plan.minutes*60000)} 뒤\n돌아오면 '탐험 보상 받기'를 눌러 주세요.\n예상 새우깡 ${expectedSnacks(plan)}개 (확률 보상 별도)`,choices:[choice('탐험'),...menu]};
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
      if(!Number.isSafeInteger(times)||times<1||times>10)throw new GameError('뽑기 횟수는 1~10회입니다. 예: 하급뽑기 5');
      return this.draw(tx,p,gachaWords[gm[1]??gm[2]],times,now);
    }
    if(compact==='뽑기')return this.gachaMenu(p,now);
    if(command==='확률')return {text:`뽑기 확률\n\n${this.probabilityText()}\n\n같은 등급 안에서는 유닛마다 같은 확률입니다. 중복 획득이 가능합니다.`,choices:[choice('뽑기'),...menu.slice(1)]};
    if(command==='보상')return {text:rewardGuide(this.content),choices:[choice('교환'),...menu]};
    if(command==='교환'){
      const cost=this.content.economy.exchange.snackCost;
      if(!query||/^\d+$/.test(query)){
        const reply=await this.browse(tx,p,'교환',query);
        const gate=(await this.hasTop(tx,p))?'':'\n\n⚠️ 교환은 최상위 유닛(제한·초월·영원·불멸)을 보유해야 할 수 있습니다.';
        return {...reply,text:`${reply.text}\n\n새우깡 ${cost}개 = 흔함 1마리 · 보유 새우깡 ${p.snack}개${gate}`};
      }
      if(!(await this.hasTop(tx,p)))throw new GameError(`교환은 최상위 유닛(제한·초월·영원·불멸)을 보유해야 할 수 있습니다.\n보유 새우깡 ${p.snack}개는 그대로 보관됩니다.`);
      const c=this.resolve(query,this.content.characters.filter(c=>c.rarity==='COMMON'),'교환',new Set());
      if(!('id' in c))return c;
      if(p.snack<cost)throw new GameError(`새우깡이 부족합니다. 필요 ${cost}개 · 보유 ${p.snack}개`);
      return this.issue(tx,p,'RECEIVE',{characterId:c.id},now,`${c.name} 1마리\n비용: 새우깡 ${cost}개 (보유 ${p.snack}개)\n이미 보유한 유닛도 받을 수 있습니다.`,this.image(c.id,new Set()));
    }
    if(command==='합치기')return this.combine(tx,p,query,now);
    if(command==='조합'||command==='조합식'){
      if(!query)return this.browse(tx,p,'조합목록','');
      const known=await this.known(tx,p);
      const c=this.resolve(query,this.revealed(known,c=>c.rarity!=='COMMON'),command,known);
      if(!('id' in c))return c;
      const recipe=this.content.recipes.find(r=>r.resultId===c.id)!;
      const stock=await this.stock(tx,p);
      const text=this.recipeText(recipe,stock,known);
      if(command==='조합식'||!this.canCraft(recipe,stock))return {text,imageId:this.image(c.id,known),choices:[...(this.canCraft(recipe,stock)?[choice('조합 준비',`조합 ${c.name}`)]:[]),choice('조합목록'),choice('내갈매미'),choice('뽑기')]};
      const ids=await this.chooseMaterials(tx,p,recipe,known);
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,text,this.image(c.id,known));
    }
    if(command==='잠금'||command==='잠금해제'){
      if(!query)return {text:`${command} 뒤에 유닛 이름을 입력하세요.\n보호한 개체는 조합 재료로 쓰지 않습니다.`,choices:[choice('내갈매미')]};
      const known=await this.known(tx,p);
      const c=this.resolve(query,this.revealed(known),command,known);
      if(!('id' in c))return c;
      const locked=command==='잠금';
      const owned=await tx.ownedCharacter.findFirst({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:!locked},orderBy:{id:'asc'}});
      if(!owned)return {text:`${c.name} 중 지금 ${command}할 수 있는 개체가 없습니다.\n보유 수량, 보호 상태, 탐험 여부를 확인하세요.`,choices:[choice('보유 상태',`내갈매미 ${c.name}`)]};
      await tx.ownedCharacter.update({where:{id:owned.id},data:{locked}});
      await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
      await this.audit(tx,p,'LOCK',{id:owned.id,locked});
      return {text:`${c.name} 1마리\n${locked?'🔒 보호했습니다. 조합 재료로 사용하지 않습니다.':'보호를 해제했습니다.'}`,choices:[choice('보유 상태',`내갈매미 ${c.name}`),...menu.slice(0,3)]};
    }
    if(command==='탐험'&&!query)return this.expeditionStatus(tx,p,now);
    if(compact.startsWith('탐험보내기')||(command==='탐험'&&args[0]==='보내기'))return this.sendExpedition(tx,p,trimmed.replace(/^탐험\s*보내기/u,'').trim(),now);
    if(['탐험보상받기','탐험보상','탐험받기'].includes(compact))return this.claimExpedition(tx,p,now);
    if(command==='시너지')return {text:this.synergyGuide(),choices:[choice('탐험'),...menu]};
    if(command==='미션')return {text:`🎯 오늘의 미션 (한국 시간 0시 초기화)\n\n${await this.missionText(tx,p,now)}\n\n목표를 채우면 바로 지급됩니다.`,choices:menu};
    if(command==='칭호'){
      const got=await this.titles(tx,p);
      const all=tierOrder.map(t=>`${got.includes(this.content.economy.achievements[t].title)?'🏆':'・'} ${rarityNames[t]} 도감 완성 → 「${this.content.economy.achievements[t].title}」 · 🎟 ${this.content.economy.achievements[t].tickets}`);
      return {text:`🏆 칭호 ${got.length}/${tierOrder.length}\n\n${all.join('\n')}\n\n등급 도감을 처음 모두 채우면 칭호와 뽑기권을 받습니다.`,choices:[choice('도감'),...menu]};
    }
    if(command==='내정보'){
      const count=await tx.collectionEntry.count({where:{playerId:p.id}});
      const active=await this.activeExpedition(tx,p);
      const trip=active?(active.endsAt>now?`탐험 중 · 귀환까지 ${duration(active.endsAt.getTime()-now.getTime())}`:'탐험 귀환 · 보상 받기 대기'):'탐험 없음';
      const titles=await this.titles(tx,p);
      return {text:`🏝 갈매미 섬${titles.length?` · 「${titles.at(-1)}」`:''}\n🎟 ${p.credits}장 · 🍤 ${p.snack}개\n📚 도감 ${count}/${this.content.characters.length} · 🏆 칭호 ${titles.length}개\n${this.claimHint(p,now)}\n🧭 ${trip}\n\n🎯 오늘의 미션\n${await this.missionText(tx,p,now)}`,choices:[choice('미션'),choice('칭호'),...menu]};
    }
    if((command==='도감'||command==='내갈매미')&&query&&!/^\d+$/.test(query)&&!this.gradeOf(args[0]))return this.detail(tx,p,command,query);
    if(['내갈매미','도감','조합목록','조합가능'].includes(command))return this.browse(tx,p,command,query);
    if(['출석','재료','땅콩떼기','땅콩교환','교배'].includes(command))return {text:'이 기능은 개편으로 종료되었습니다.\n뽑기권은 "뽑기권 받기"로, 새우깡은 탐험으로 얻습니다.',choices:menu};
    return {text:help,choices:menu};
  }
}
