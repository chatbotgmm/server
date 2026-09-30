import { randomBytes } from 'node:crypto';
import { Prisma, type PrismaClient, type Player } from '@prisma/client';
import type { Character, Content, Recipe } from './content.js';
import { rarityNames } from './content.js';
import { GameError, choice, type GameReply, type ButtonAction, type ListEntry } from './types.js';
import { grantsSince, latestSlot, nextSlot, localDay } from './schedule.js';
import { GachaEngine, loadGachaWeights, drawRarities } from './gacha.js';
import {characterInfo,matchCharacters,listReply,materialCounts} from './presentation.js';
import {defaultRefillSettings,refillCreditsSince,refillAmountAt,type RefillSettings} from './refill-settings.js';
import {introduction,receiptText,rewardGuide,type Receipt} from './acquisition.js';
type Stock=Map<string,{total:number;usable:number;marked:number;locked:number;removed:number}>;
type Tx=Prisma.TransactionClient;
type ActionPayload={characterId?:string;ids?:number[];cost?:'CREDIT'|'SNACK'|'PEANUT';recipeId?:string;source?:'MATERIAL'};
const json=(x:unknown)=>x as Prisma.InputJsonValue;
const menu=[choice('뽑기'),choice('내갈매미'),choice('조합가능'),choice('조합목록'),choice('도감'),choice('내정보')];
const help='갈매미맨\n\n🥚 뽑기 — 랜덤 캐릭터 획득\n🧩 조합목록 — 이름을 눌러 재료 확인\n✅ 조합가능 — 지금 만들 수 있는 캐릭터\n🐦 내갈매미 — 이름별 보유 수량\n📚 도감 — 수집한 캐릭터 확인\n\n이름으로 바로 입력해도 됩니다.\n조합 브라자 / 도감 비닐봉지\n교환 도시락 / 잠금 비닐봉지\n\n출석 / 탐험 / 내정보 / 확률 / 보상';
export class GameService {
  private tail:Promise<unknown>=Promise.resolve();
  private pending=0;
  constructor(private db:PrismaClient,public content:Content,private clock=()=>new Date(),private gacha=new GachaEngine(content.characters,loadGachaWeights()),private settings:()=>Promise<RefillSettings>=async()=>defaultRefillSettings()){}
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
      let settings:RefillSettings;
      try{settings=await this.settings();}
      catch{throw new GameError('충전 설정을 읽을 수 없습니다. 관리자에게 설정 파일 확인을 요청해 주세요.');}
      return this.db.$transaction(async tx=>{
      const now=this.clock();
      const player=await this.player(tx,identity,now,settings);
      return this.dispatch(tx,player,message,now,settings,button);
      },{maxWait:500,timeout:2500});
    });
    this.tail=task.catch(()=>{});
    return Promise.race([task,timeout]).then(result=>{
      const imageName=result.imageId?this.content.map.get(result.imageId)?.name:undefined;
      return imageName?{...result,imageName}:result;
    }).finally(()=>{clearTimeout(timer);this.pending--;});
  }
  private async audit(tx:Tx,p:Player,kind:string,detail:unknown){
    await tx.auditEvent.create({data:{playerId:p.id,kind,detail:json(detail)}});
  }
  private async player(tx:Tx,identity:string,now:Date,settings:RefillSettings){
    let p=await tx.player.findUnique({where:{identity}});
    if(!p){
      p=await tx.player.create({data:{identity,lastGrantAt:latestSlot(now),createdAt:now}});
      await this.audit(tx,p,'WELCOME',{credits:3});
    }
    const grants=grantsSince(p.lastGrantAt,now);
    if(grants){
      const added=refillCreditsSince(settings,p.lastGrantAt,now);
      const newCredits=Math.min(2_000_000_000,p.credits+added);
      await this.audit(tx,p,'SCHEDULE_GRANT',{slots:grants,scheduledCredits:added,delta:newCredits-p.credits,creditsAfter:newCredits,refillAmount:refillAmountAt(settings,now),settingsChangedAt:settings.changes.at(-1)?.at??null});
      p=await tx.player.update({where:{id:p.id},data:{credits:newCredits,lastGrantAt:latestSlot(now)}});
    }
    return p;
  }
  private probabilityText(){
    return drawRarities.map(rarity=>`${rarityNames[rarity]} ${this.gacha.weights[rarity]}%`).join(' · ');
  }
  private async draw(tx:Tx,p:Player,now:Date,settings:RefillSettings):Promise<GameReply>{
    if(p.credits<1)return {text:`획득권이 없습니다. 다음 추가: ${nextSlot(now)} (한국 시간)\n매일 09·12·18시 +${refillAmountAt(settings,now)}장. 미사용분은 누적됩니다.`,choices:[choice('내정보'),choice('출석')]};
    const c=this.gacha.draw();
    const paid=await tx.player.updateMany({where:{id:p.id,revision:p.revision,credits:{gte:1}},data:{credits:{decrement:1},revision:{increment:1}}});
    if(paid.count!==1)throw new GameError('보유 상태가 바뀌었습니다. 다시 뽑기를 입력하세요.');
    const owned=await this.grant(tx,p,c.id,'GACHA',now);
    await this.audit(tx,p,'GACHA',{characterId:c.id,rarity:c.rarity,rarityPercent:this.gacha.weights[c.rarity as keyof typeof this.gacha.weights],ownedId:owned.id,currency:'credits',delta:-1,balanceAfter:p.credits-1});
    return {text:`${introduction(this.content,c,'GACHA')}\n\n${receiptText(c,owned.receipt)}\n\n획득권 ${p.credits-1}장 남음`,...(owned.receipt.first?{imageId:c.id}:{}),choices:this.acquisitionChoices(c,owned.receipt)};
  }
  private acquisitionChoices(c:Character,receipt:Receipt){
    return [choice('캐릭터 보기',`내갈매미 ${c.name}`),
      ...(receipt.snackBalance>=30?[choice('새우깡 교환','교환')]:[]),choice('획득 보상','보상'),...menu].slice(0,10);
  }
  private async issue(tx:Tx,p:Player,kind:string,payload:ActionPayload,now:Date,text:string,imageId?:string):Promise<GameReply>{
    await tx.pendingAction.deleteMany({where:{playerId:p.id,result:{equals:Prisma.DbNull},expiresAt:{lt:now}}});
    const token=randomBytes(8).toString('hex');
    await tx.pendingAction.create({data:{token,playerId:p.id,revision:p.revision,kind,payload:json(payload),expiresAt:new Date(now.getTime()+600_000)}});
    const title=kind==='CRAFT'?'조합하기':kind==='PEANUT'?'표식 전환하기':'교환하기';
    const message=kind==='CRAFT'?'조합 확정':kind==='PEANUT'?'표식 전환 확정':'교환 확정';
    return {text:`${text}\n\n아래 '${title}'를 누르면 완료됩니다. (10분 동안 유효)`,imageId,list:{
      title:'진행할까요?',showText:true,items:[
        {title,description:'위 내용을 확인하고 진행합니다.',message,button:{action:'confirm',token}},
        {title:'취소',description:'재료와 재화를 사용하지 않습니다.',message:'취소',button:{action:'cancel',token}}
      ]
    }};
  }

  private async grant(tx:Tx,p:Player,characterId:string,via:string,now:Date){
    const owned=await tx.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:via,obtainedAt:now}});
    const collection=await tx.collectionEntry.upsert({where:{playerId_characterId:{playerId:p.id,characterId}},create:{playerId:p.id,characterId},update:{count:{increment:1}}});
    const c=this.content.map.get(characterId)!;
    const first=collection.count===1;
    const reward=this.content.rewards[c.rarity as keyof Content['rewards']];
    const planned=first?reward.first:reward.duplicate;
    const account=await tx.player.findUniqueOrThrow({where:{id:p.id},select:{snack:true}});
    const snacks=Math.max(0,Math.min(planned,2_000_000_000-account.snack));
    if(snacks){
      await tx.player.update({where:{id:p.id},data:{snack:{increment:snacks}}});
      await this.audit(tx,p,'ACQUISITION_REWARD',{ownedId:owned.id,characterId,via,first,obtainedCount:collection.count,requested:planned,delta:snacks,balanceAfter:account.snack+snacks});
    }
    const tierIds=this.content.characters.filter(candidate=>candidate.rarity===c.rarity).map(candidate=>candidate.id);
    const tierKnown=await tx.collectionEntry.count({where:{playerId:p.id,characterId:{in:tierIds}}});
    const receipt:Receipt={first,count:collection.count,snacks,snackBalance:account.snack+snacks,tierKnown,tierTotal:tierIds.length};
    return {...owned,receipt};
  }
  private async chooseMaterials(tx:Tx,p:Player,recipe:Recipe){
    if(recipe.requiresCollection){
      const known=await tx.collectionEntry.count({where:{playerId:p.id,characterId:{in:recipe.requiresCollection}}});
      if(known!==recipe.requiresCollection.length)throw new GameError('필수 도감 등록 조건이 충족되지 않았습니다.');
    }
    const inventory=await tx.ownedCharacter.findMany({where:{playerId:p.id,status:'AVAILABLE',locked:false,peanutRemoved:recipe.peanutState??false,characterId:{in:recipe.materials}},orderBy:{id:'asc'}});
    const selected:number[]=[];
    for(const id of recipe.materials){
      const owned=inventory.find(c=>c.characterId===id&&!selected.includes(c.id));
      if(!owned)throw new GameError(`${this.content.map.get(id)!.name} 재료가 부족합니다. 보호·표식 전환 개체는 기본 조합에서 제외합니다.\n조합식 ${this.content.map.get(recipe.resultId)!.name}에서 필요 수량을 확인하세요.`);
      selected.push(owned.id);
    }
    return selected;
  }
  private async stock(tx:Tx,p:Player):Promise<Stock>{
    const rows=await tx.ownedCharacter.groupBy({by:['characterId','locked','peanutRemoved'],where:{playerId:p.id,status:'AVAILABLE'},_count:{_all:true}});
    const result:Stock=new Map();
    for(const row of rows){
      const count=result.get(row.characterId)??{total:0,usable:0,marked:0,locked:0,removed:0};
      const n=row._count._all;
      count.total+=n;
      if(row.locked)count.locked+=n;
      if(row.peanutRemoved)count.removed+=n;
      if(!row.locked){if(row.peanutRemoved)count.marked+=n;else count.usable+=n;}
      result.set(row.characterId,count);
    }
    return result;
  }
  private canCraft(recipe:Recipe,stock:Stock,known:Set<string>){
    return [...materialCounts(recipe.materials)].every(([id,n])=>(stock.get(id)?.[recipe.peanutState?'marked':'usable']??0)>=n)
      && (recipe.requiresCollection??[]).every(id=>known.has(id));
  }
  private recipeText(recipe:Recipe,stock:Stock,known:Set<string>){
    const result=this.content.map.get(recipe.resultId)!;
    const lines=[...materialCounts(recipe.materials)].map(([id,n])=>{
      const have=stock.get(id)?.[recipe.peanutState?'marked':'usable']??0;
      return `${have>=n?'✓':'・'} ${this.content.map.get(id)!.name}\n   필요 ${n}마리 · 사용 가능 ${have}마리`;
    });
    const extra=(recipe.requiresCollection??[]).filter(id=>!known.has(id));
    return `🧩 ${result.name}\n${characterInfo(result)}\n\n필요 재료\n${lines.join('\n\n')}\n\n${this.canCraft(recipe,stock,known)?'✅ 조합 준비 완료':'재료 또는 도감 조건이 부족합니다.'}${extra.length?`\n필요 도감: ${extra.map(id=>this.content.map.get(id)!.name).join(', ')}`:''}\n재료를 사용해 결과 1마리를 얻습니다.\n보호 중인 개체는 사용하지 않습니다.\n\n${recipe.story}`;
  }
  private resolve(query:string,pool:Character[],command:string):Character|GameReply{
    const found=matchCharacters(pool,query);
    if(found.length===1)return found[0];
    if(!found.length)return {text:`이름을 찾지 못했습니다.\n예: ${command} 브라자\n목록에서 캐릭터를 선택할 수도 있습니다.`,choices:[choice('조합목록'),choice('도감'),choice('내갈매미')]};
    return listReply('어떤 갈매미를 찾으세요?',found.slice(0,5).map(c=>({title:c.name,description:characterInfo(c),message:`${command} ${c.name}`})),
      '이름을 눌러 선택하세요. 목록에 없으면 이름을 조금 더 길게 입력하세요.',[choice('도움말')]);
  }
  private page(query:string,total:number){
    const page=Number(query||1),pages=Math.max(1,Math.ceil(total/5));
    if(!Number.isSafeInteger(page)||page<1||page>pages)throw new GameError(`페이지는 1부터 ${pages}까지입니다.`);
    return {page,pages,start:(page-1)*5};
  }
  private navigation(command:string,page:number,pages:number){
    return [...(page>1?[choice('이전',`${command} ${page-1}`)]:[]),...(page<pages?[choice('다음 페이지',`${command} ${page+1}`)]:[]),...menu.slice(0,4)];
  }
  private async browse(tx:Tx,p:Player,command:string,query:string):Promise<GameReply>{
    const stock=await this.stock(tx,p);
    const entries=await tx.collectionEntry.findMany({where:{playerId:p.id}});
    const known=new Set(entries.map(entry=>entry.characterId));
    const recipeMode=command==='조합목록'||command==='조합가능';
    const exchange=['재료','교환','땅콩교환'].includes(command);
    const grades=[['안흔함','UNCOMMON'],['특별','SPECIAL'],['희귀','RARE']] as const;
    const gradeChoices=grades.map(([label])=>choice(label,`${command} ${label}`));
    if(recipeMode&&!query){
      return listReply(`${command} · 등급 선택`,grades.map(([label,rarity])=>{
        const recipes=this.content.recipes.filter(r=>this.content.map.get(r.resultId)!.rarity===rarity);
        const ready=recipes.filter(r=>this.canCraft(r,stock,known)).length;
        return {title:label,description:`전체 ${recipes.length}종 · 지금 조합 가능 ${ready}종`,message:`${command} ${label}`};
      }),'결과 캐릭터의 등급을 선택하세요. 흔함은 조합식이 없는 기본 재료입니다.',[choice('전체 보기',`${command} 전체`),...menu.slice(0,3)]);
    }
    let pageQuery=query,grade:Character['rarity']|undefined,gradeLabel='전체';
    if(recipeMode&&!/^\d+$/.test(query)){
      const [label,page,...extra]=query.trim().split(/\s+/);
      if(label==='흔함')return {text:'흔함은 조합식이 없는 기본 재료입니다. 안흔함부터 조합식이 있습니다.',choices:gradeChoices};
      const selected=grades.find(([name])=>name===label);
      if((!selected&&label!=='전체')||extra.length)throw new GameError(`등급을 선택하세요.\n${command} 안흔함 / ${command} 특별 / ${command} 희귀`);
      grade=selected?.[1];gradeLabel=selected?.[0]??'전체';pageQuery=page??'';
    }
    let characters=this.content.characters;
    if(command==='내갈매미')characters=characters.filter(c=>stock.has(c.id));
    if(recipeMode)characters=this.content.recipes.filter(r=>command!=='조합가능'||this.canCraft(r,stock,known)).map(r=>this.content.map.get(r.resultId)!);
    if(grade)characters=characters.filter(c=>c.rarity===grade);
    if(exchange)characters=characters.filter(c=>c.rarity==='COMMON');
    if(!characters.length)return {text:command==='조합가능'?`지금 조합할 수 있는 ${gradeLabel==='전체'?'':gradeLabel+' '}캐릭터가 없습니다.\n조합목록에서 필요한 재료를 확인하세요.`:'아직 보유한 갈매미가 없습니다. 뽑기를 입력하세요.',choices:[...(recipeMode?[choice('필요 재료 보기',`조합목록 ${gradeLabel}`),...gradeChoices]:[]),choice('조합목록'),choice('뽑기'),choice('내갈매미')]};
    const {page,pages,start}=this.page(pageQuery,characters.length);
    const cost=command==='재료'?'획득권 1장':command==='교환'?'새우깡 30개':'땅콩 1개';
    const items:ListEntry[]=characters.slice(start,start+5).map(c=>{
      const have=stock.get(c.id);
      const recipe=this.content.recipes.find(r=>r.resultId===c.id);
      const status=recipeMode?(this.canCraft(recipe!,stock,known)?'✅ 조합 가능':'재료 모으는 중'):command==='내갈매미'?`보유 ${have!.total}마리 · 사용 가능 ${have!.usable}마리`:exchange?cost:known.has(c.id)?'수집 완료':'미수집';
      return {title:c.name,description:`${rarityNames[c.rarity]} · ${status}`,message:`${recipeMode?'조합':command} ${c.name}`};
    });
    const title=recipeMode?`${command} · ${gradeLabel}`:command;
    const navigation=this.navigation(recipeMode?`${command} ${gradeLabel}`:command,page,pages);
    return listReply(`${title} · ${page} / ${pages}`,items,'캐릭터 이름을 눌러 이미지와 자세한 정보를 보세요.',recipeMode?[...navigation,...gradeChoices,choice('등급 선택',command)]:navigation);
  }
  private async cancel(tx:Tx,p:Player,token:string):Promise<GameReply>{
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청을 찾지 못했습니다.');
    if(action.result)return {text:'이미 완료된 요청입니다. 다시 처리하거나 취소하지 않습니다.',choices:menu};
    await tx.pendingAction.delete({where:{token}});
    return {text:'취소했습니다. 재료와 재화는 사용하지 않았습니다.',choices:menu};
  }
  private async confirm(tx:Tx,p:Player,token:string,now:Date):Promise<GameReply>{
    if(!/^[a-f0-9]{16}$/.test(token))throw new GameError('미리보기 아래 진행 항목을 눌러 주세요. 오래된 화면이라면 조합을 다시 열어 주세요.');
    const action=await tx.pendingAction.findUnique({where:{token}});
    if(!action||action.playerId!==p.id)throw new GameError('본인의 확인 요청을 찾지 못했습니다. 다시 미리보기를 여세요.');
    if(action.result){
      const saved=action.result as unknown as GameReply;
      // 이전 버전에서 저장한 결과도 번호를 다시 노출하지 않습니다. 지급 기록은 유지합니다.
      if(/[CUSR]\d{3}[MF]|개체 #\d+/.test(saved.text)){
        const name=saved.imageId?this.content.map.get(saved.imageId)?.name:undefined;
        return {text:`이미 완료된 요청입니다.${name?`\n${name}`:''}\n추가로 재료를 사용하거나 지급하지 않았습니다.\n내갈매미에서 보유 상태를 확인하세요.`,imageId:saved.imageId,choices:menu};
      }
      return saved;
    }
    if(action.expiresAt<=now)throw new GameError('10분이 지나 만료됐습니다. 다시 미리보기를 여세요.');
    if(action.revision!==p.revision)throw new GameError('보유 상태가 바뀌었습니다. 다시 미리보기를 여세요.');
    const payload=action.payload as ActionPayload;
    if(action.kind==='RECEIVE'&&payload.cost==='CREDIT'&&payload.source!=='MATERIAL')throw new GameError('뽑기 방식이 변경되었습니다. 뽑기를 다시 입력하세요.');
    const revised=await tx.player.updateMany({where:{id:p.id,revision:p.revision},data:{revision:{increment:1}}});
    if(revised.count!==1)throw new GameError('다른 요청이 먼저 처리됐습니다. 다시 미리보기를 여세요.');
    let result:GameReply;
    if(action.kind==='RECEIVE'){
      const c=this.content.map.get(payload.characterId??'');
      if(!c)throw new GameError('캐릭터를 찾을 수 없습니다.');
      const field=payload.cost==='CREDIT'?'credits':payload.cost==='SNACK'?'snack':'peanut';
      const cost=field==='snack'?30:1;
      const balance=p[field];
      if(balance<cost)throw new GameError('재화가 부족합니다. 상태를 확인하고 다시 진행하세요.');
      await tx.player.update({where:{id:p.id},data:{[field]:{decrement:cost}}});
      const owned=await this.grant(tx,p,c.id,'CONFIRMED',now);
      result={text:`${introduction(this.content,c,'CONFIRMED')}\n\n${receiptText(c,owned.receipt)}\n\n${field==='credits'?'획득권':field==='snack'?'새우깡':'땅콩'} ${field==='snack'?owned.receipt.snackBalance:balance-cost} 남음`,...(owned.receipt.first?{imageId:c.id}:{}),choices:this.acquisitionChoices(c,owned.receipt)};
      await this.audit(tx,p,'RECEIVE',{token,characterId:c.id,ownedId:owned.id,currency:field,delta:-cost,balanceAfter:balance-cost});
    }else if(action.kind==='CRAFT'){
      const recipe=this.content.recipes.find(r=>r.resultId===payload.recipeId);
      if(!recipe)throw new GameError('조합식이 없습니다.');
      const ids=payload.ids??[];
      const selected=await this.chooseMaterials(tx,p,recipe);
      if(ids.length!==selected.length||ids.some((id,i)=>id!==selected[i]))throw new GameError('재료 상태가 바뀌었습니다. 다시 확인하세요.');
      const update=await tx.ownedCharacter.updateMany({where:{id:{in:ids},playerId:p.id,status:'AVAILABLE',locked:false,peanutRemoved:recipe.peanutState??false},data:{status:'CONSUMED',consumedAt:now}});
      if(update.count!==ids.length)throw new GameError('재료 소비 충돌입니다. 다시 확인하세요.');
      const owned=await this.grant(tx,p,recipe.resultId,'COMBINATION',now);
      const c=this.content.map.get(recipe.resultId)!;
      result={text:`${introduction(this.content,c,'COMBINATION')}\n\n재료 ${ids.length}마리를 사용해 1마리를 얻었습니다.\n${receiptText(c,owned.receipt)}`,...(owned.receipt.first?{imageId:c.id}:{}),choices:this.acquisitionChoices(c,owned.receipt)};
      await this.audit(tx,p,'CRAFT',{token,recipe:recipe.resultId,consumed:ids,ownedId:owned.id});
    }else if(action.kind==='PEANUT'){
      const owned=await this.peanutTarget(tx,p,payload.ids?.[0]);
      await tx.ownedCharacter.update({where:{id:owned.id},data:{peanutRemoved:true}});
      await tx.player.update({where:{id:p.id},data:{peanut:{increment:1}}});
      result={text:`${this.content.map.get(owned.characterId)!.name} 표식 전환 완료.\n땅콩 +1 · 현재 ${p.peanut+1}\n이 1마리는 기본 조합 재료에서 제외됩니다.`,choices:menu};
      await this.audit(tx,p,'PEANUT',{token,ownedId:owned.id,delta:1,balanceAfter:p.peanut+1});
    }else throw new GameError('지원하지 않는 요청입니다.');
    await tx.pendingAction.update({where:{token},data:{result:json(result)}});
    return result;
  }
  private async peanutTarget(tx:Tx,p:Player,id?:number){
    if(!id)throw new GameError('땅콩떼기 뒤에 캐릭터 이름을 입력하세요. 내갈매미에서 선택할 수도 있습니다.');
    const c=await tx.ownedCharacter.findFirst({where:{id,playerId:p.id,status:'AVAILABLE',locked:false},include:{character:true}});
    if(!c)throw new GameError('본인의 잠금 해제된 보유 개체가 아닙니다.');
    if(c.character.rarity!=='RARE'||c.character.sex!=='MALE')throw new GameError('이번 75종에서는 희귀 수컷만 표식을 전환할 수 있습니다.');
    if(c.peanutRemoved)throw new GameError('이미 표식을 전환한 개체입니다.');
    return c;
  }
  private async dispatch(tx:Tx,p:Player,message:string,now:Date,settings:RefillSettings,button?:ButtonAction):Promise<GameReply>{
    const parts=message.trim().replace(/^[!/]/,'').split(/\s+/);
    const [command,...args]=parts;
    const query=args.join(' ');
    if(button){
      const visible=parts.join(' ');
      if(button.action==='confirm'&&['확정','조합 확정','교환 확정','표식 전환 확정'].includes(visible))return this.confirm(tx,p,button.token,now);
      if(button.action==='cancel'&&visible==='취소')return this.cancel(tx,p,button.token);
      throw new GameError('버튼 요청이 일치하지 않습니다. 미리보기를 다시 열어 주세요.');
    }
    if(command==='확정')return this.confirm(tx,p,args[0]??'',now); // 이전 버전 버튼 호환
    if(['조합 확정','교환 확정','표식 전환 확정','취소'].includes(parts.join(' ')))return {text:'미리보기의 진행 또는 취소 항목을 직접 눌러 주세요. 버튼 정보가 없다면 원래 명령으로 미리보기를 다시 여세요.',choices:[choice('조합목록'),choice('내갈매미')]};
    if(['뽑기','받기','다음','캐릭터받기'].includes(command)||(command==='캐릭터'&&args[0]==='받기'))return this.draw(tx,p,now,settings);
    if(command==='확률')return {text:`일반 뽑기 확률\n${this.probabilityText()}\n같은 등급 안에서는 캐릭터별 동일 확률입니다.\n중복 획득이 가능합니다. 특수함은 일반 뽑기에 포함되지 않습니다.`,choices:menu};
    if(command==='보상')return {text:rewardGuide(this.content),choices:[choice('새우깡 교환','교환'),...menu]};
    if(['재료','교환','땅콩교환'].includes(command)){
      if(!query||/^\d+$/.test(query))return this.browse(tx,p,command,query);
      const c=this.resolve(query,this.content.characters.filter(c=>c.rarity==='COMMON'),command);
      if(!('id' in c))return c;
      const cost=command==='재료'?'CREDIT':command==='교환'?'SNACK':'PEANUT';
      if((cost==='CREDIT'?p.credits:cost==='SNACK'?p.snack:p.peanut)<(cost==='SNACK'?30:1))throw new GameError('재화가 부족합니다. 내정보에서 확인하세요.');
      return this.issue(tx,p,'RECEIVE',{characterId:c.id,cost,source:'MATERIAL'},now,`${c.name} 1마리\n비용: ${cost==='CREDIT'?'획득권 1장':cost==='SNACK'?'새우깡 30개':'땅콩 1개'}\n이미 보유한 캐릭터도 얻을 수 있습니다.`,c.id);
    }
    if(command==='조합'||command==='조합식'){
      if(!query)return this.browse(tx,p,'조합목록','');
      const c=this.resolve(query,this.content.characters.filter(c=>c.rarity!=='COMMON'),command);
      if(!('id' in c))return c;
      const recipe=this.content.recipes.find(r=>r.resultId===c.id)!;
      const stock=await this.stock(tx,p);
      const known=new Set((await tx.collectionEntry.findMany({where:{playerId:p.id}})).map(e=>e.characterId));
      const text=this.recipeText(recipe,stock,known);
      if(command==='조합식'||!this.canCraft(recipe,stock,known))return {text,imageId:c.id,choices:[...(this.canCraft(recipe,stock,known)?[choice('조합 준비',`조합 ${c.name}`)]:[]),choice('조합목록'),choice('내갈매미'),choice('뽑기')]};
      const ids=await this.chooseMaterials(tx,p,recipe);
      return this.issue(tx,p,'CRAFT',{recipeId:recipe.resultId,ids},now,text,c.id);
    }
    if(['잠금','잠금해제','땅콩떼기'].includes(command)){
      if(!query)return {text:`${command} 뒤에 캐릭터 이름을 입력하세요.\n내갈매미에서 이름을 눌러 관리할 수도 있습니다.\n같은 이름이 여러 마리면 조건에 맞는 오래된 1마리에 적용합니다.`,choices:[choice('내갈매미')]};
      let ownedId:number|undefined;
      if(/^\d+$/.test(query)){ // 이전 개체번호 입력 호환
        const value=Number(query);
        if(!Number.isSafeInteger(value)||value<=0)throw new GameError('보유 캐릭터를 다시 선택하세요.');
        ownedId=value;
      }else{
        const c=this.resolve(query,this.content.characters,command);
        if(!('id' in c))return c;
        if(command==='땅콩떼기'&&(c.rarity!=='RARE'||c.sex!=='MALE'))throw new GameError('희귀 수컷만 표식을 전환할 수 있습니다.');
        const owned=await tx.ownedCharacter.findFirst({where:{playerId:p.id,characterId:c.id,status:'AVAILABLE',locked:command==='잠금해제',...(command==='땅콩떼기'?{peanutRemoved:false}:{})},orderBy:{id:'asc'}});
        if(!owned)return {text:`${c.name} 중 지금 ${command}할 수 있는 개체가 없습니다.\n보유 수량과 보호 상태를 확인하세요.`,choices:[choice('보유 상태',`내갈매미 ${c.name}`)]};
        ownedId=owned.id;
      }
      if(command==='땅콩떼기'){
        const owned=await this.peanutTarget(tx,p,ownedId);
        const name=this.content.map.get(owned.characterId)!.name;
        return this.issue(tx,p,'PEANUT',{ids:[owned.id]},now,`${name} 1마리\n땅콩 표식 전환 → 땅콩 1개\n\n한 번만 가능하며 되돌릴 수 없습니다.\n전환된 1마리는 기본 조합 재료에서 제외됩니다.`);
      }
      const c=await tx.ownedCharacter.findFirst({where:{id:ownedId,playerId:p.id,status:'AVAILABLE'}});
      if(!c)throw new GameError('본인의 보유 개체가 아닙니다.');
      const locked=command==='잠금';
      if(c.locked!==locked){
        await tx.ownedCharacter.update({where:{id:c.id},data:{locked}});
        await tx.player.update({where:{id:p.id},data:{revision:{increment:1}}});
        await this.audit(tx,p,'LOCK',{id:c.id,locked});
      }
      const name=this.content.map.get(c.characterId)!.name;
      return {text:`${name} 1마리\n${locked?'🔒 보호했습니다. 조합 재료로 사용하지 않습니다.':'보호를 해제했습니다.'}`,choices:[choice('보유 상태',`내갈매미 ${name}`),...menu.slice(0,3)]};
    }
    if(command==='출석'){
      const day=localDay(now);
      if(p.attendanceDate===day)return {text:'오늘은 이미 출석했습니다. 한국 시간 자정 이후 다시 가능합니다.',choices:menu};
      await tx.player.update({where:{id:p.id},data:{attendanceDate:day,snack:{increment:30}}});
      await this.audit(tx,p,'ATTENDANCE',{day,delta:30,balanceAfter:p.snack+30});
      return {text:`출석 완료! 새우깡 30개 확정 지급 · 현재 ${p.snack+30}개\n교환을 입력하면 흔함 재료를 선택할 수 있습니다.`,choices:menu};
    }
    if(command==='탐험'){
      const wait=p.lastExploreAt?30_000-(now.getTime()-p.lastExploreAt.getTime()):0;
      if(wait>0)return {text:`${Math.ceil(wait/1000)}초 뒤 다시 탐험하세요. 보상은 항상 새우깡 10개입니다.`,choices:menu};
      await tx.player.update({where:{id:p.id},data:{lastExploreAt:now,snack:{increment:10}}});
      await this.audit(tx,p,'EXPLORE',{delta:10,balanceAfter:p.snack+10});
      return {text:`해저앙영 완료. 새우깡 10개 확정 지급 · 현재 ${p.snack+10}개\n갈매미는 물에 뜨지 않는다. 물이 갈매미 위에 뜬다.`,choices:menu};
    }
    if(command==='내정보'){
      const count=await tx.collectionEntry.count({where:{playerId:p.id}});
      return {text:`갈매미 섬\n획득권 ${p.credits}장 / 새우깡 ${p.snack} / 땅콩 ${p.peanut}\n도감 ${count}/75\n뽑기: ${this.probabilityText()}\n다음 +${refillAmountAt(settings,now)}장: ${nextSlot(now)} (한국 시간)\n미사용 획득권은 누적됩니다.`,choices:menu};
    }
    if((command==='도감'||command==='내갈매미')&&query&&!/^\d+$/.test(query)){
      const c=this.resolve(query,this.content.characters,command);
      if(!('id' in c))return c;
      const entry=await tx.collectionEntry.findUnique({where:{playerId_characterId:{playerId:p.id,characterId:c.id}}});
      const count=(await this.stock(tx,p)).get(c.id)??{total:0,usable:0,marked:0,locked:0,removed:0};
      const f=this.content.flavors.get(c.id)!;
      const text=`【${c.name}】\n${f.title}\n${characterInfo(c)}\n\n📜 도감 기록\n${f.lore}\n\n“${c.quote}”\n\n보유 ${count.total}마리 · 조합에 사용 가능 ${count.usable}마리\n보호 ${count.locked}마리 · 표식 전환 ${count.removed}마리\n${entry?`누적 ${entry.count}회 획득`:'아직 수집하지 못했습니다.'}`;
      const manage=command==='내갈매미';
      return {text,imageId:c.id,choices:[
        ...(c.rarity==='COMMON'?[choice('재료 선택',`재료 ${c.name}`)]:[choice('조합식 보기',`조합식 ${c.name}`)]),
        ...(manage&&count.total>count.locked?[choice('1마리 보호',`잠금 ${c.name}`)]:[]),
        ...(manage&&count.locked?[choice('1마리 보호 해제',`잠금해제 ${c.name}`)]:[]),
        ...(manage&&c.rarity==='RARE'&&c.sex==='MALE'&&count.usable?[choice('표식 전환',`땅콩떼기 ${c.name}`)]:[]),
        ...(manage?[]:[choice('보유 상태',`내갈매미 ${c.name}`)]),
        ...menu.slice(0,4)
      ]};
    }
    if(['내갈매미','도감','조합목록','조합가능'].includes(command))return this.browse(tx,p,command,query);
    if(command==='교배')return {text:'교배는 폐지되었습니다. 성별 제한 없이 정해진 재료로 조합하세요.',choices:[choice('조합목록')]};
    if(['땅콩뽑기','새우깡뽑기','상위뽑기'].includes(command))return {text:'새우깡 상위 뽑기는 추후 추가 예정입니다. 일반 뽑기는 ‘뽑기’를 입력하세요.\n원하는 흔함을 지정해 교환할 수도 있습니다.\n교환 / 땅콩교환\n특수함·전설 이상 콘텐츠는 이번 75종 판에 포함하지 않습니다.',choices:menu};
    return {text:help,choices:menu};
  }
}
