// 단체방 챗봇 명령 처리. 게임 규칙은 GameService(web* 메서드)를 그대로 쓰고, 응답 문구는 단체방용으로 짧게 따로 만들어요.
// 응답은 모두 buildResponse(허용 말풍선·길이 상한)를 거쳐요.
// 문구 원칙(덱 40·43·47): 광고·구매 유도·대화 열람 오해 표현을 쓰지 않아요. 게임 재화는 '모집권'으로 불러요.
import { emblems } from '../acquisition.js';
import { rarityNames, type Tier } from '../content.js';
import type { GameService } from '../game.js';
import { GameError } from '../types.js';
import type { GroupBotConfig } from './config.js';
import type { GroupRequest } from './request.js';
import { buildResponse, buildWelcome, quick, text, type GroupQuickReply, type GroupSkillResponse } from './response.js';
import type { GroupStore } from './store.js';

const GACHA:Record<string,'LOW'|'MID'|'HIGH'>={하급:'LOW',중급:'MID',고급:'HIGH'};
export const MAX_RECRUIT=10; // [우리] 단체방 화면을 덜 차지하게 한 번에 10회까지(앱·웹은 50회)
const len=(s:string)=>[...s].length;
const minutes=(ms:number)=>{const m=Math.ceil(ms/60000);return m>=60?`${Math.floor(m/60)}시간 ${m%60}분`:`${m}분`;};
const who=(nick:string|null|undefined)=>nick??'이름 없는 갈매미';
const menu:GroupQuickReply[]=[quick('모집 하급'),quick('모집권 받기'),quick('조합가능'),quick('방도감'),quick('도움말')];

export class GroupBot {
  constructor(private readonly game:GameService,private readonly store:GroupStore,private readonly config:GroupBotConfig,private readonly homepage:string){}
  private respond(lines:string[],quickReplies:GroupQuickReply[]=menu):GroupSkillResponse{
    return buildResponse([text(this.fit(lines))],quickReplies.slice(0,this.config.limits.quickReplies),{config:this.config});
  }
  // 상한(글자·줄)을 넘으면 끝에서 둘째 줄부터 빼서 맞춰요(마지막 줄 = 잔액·안내는 남김)
  private fit(lines:string[]){
    const {textChars,textLines}=this.config.limits,out=[...lines];
    while(out.length>2&&(out.length>textLines||len(out.join('\n'))>textChars))out.splice(out.length-2,1);
    const t=out.join('\n');
    return len(t)>textChars?[...t].slice(0,textChars-1).join('')+'…':t;
  }
  // 머리(head)·꼬리(tail) 줄은 꼭 남기고, 가운데 목록은 상한 안에 드는 만큼만 보여 준 뒤 「… 외 N개」로 줄여요
  private squeeze(head:string[],items:string[],tail:string[],unit='개'){
    const {textChars,textLines}=this.config.limits;
    const fits=(lines:string[])=>lines.length<=textLines&&len(lines.join('\n'))<=textChars;
    if(fits([...head,...items,...tail]))return [...head,...items,...tail];
    for(let n=items.length-1;n>=0;n--){
      const lines=[...head,...items.slice(0,n),`… 외 ${items.length-n}${unit}`,...tail];
      if(fits(lines))return lines;
    }
    return [...head,`… ${items.length}${unit}`,...tail];
  }
  // 이름 여러 개를 한 줄로: 앞의 max개만, 나머지는 「외 N」
  private names(list:string[],max=3){return list.length>max?`${list.slice(0,max).join(', ')} 외 ${list.length-max}`:list.join(', ');}
  welcome(){
    return buildWelcome('안녕하세요, 갈매미맨이에요!\n같이 갈매미를 모아 우리 방 도감을 채워요.\n@갈매미맨 도움말 로 시작해요.',[quick('도움말'),quick('모집 하급')],{config:this.config});
  }
  help(){
    return this.respond([
      '🪨 갈매미맨 단체방 명령어',
      '모집 하급·중급·고급 (횟수)',
      '모집권 받기 · 내정보',
      '조합가능 · 조합 이름',
      '도감 · 방도감 · 방순위',
      '닉네임 이름 (순위에 표시)',
      '방 사람들이 모은 갈매미가 우리 방 도감에 쌓여요.',
      `문의: ${this.homepage}`,
    ]);
  }
  async handle(req:GroupRequest):Promise<GroupSkillResponse>{
    const {playerId}=await this.store.resolvePlayer(req);
    const roomId=await this.store.room(req.env,req.groupKey);
    await this.store.touchMember(roomId,playerId);
    const words=req.utterance.replace(/\s+/g,' ').trim().split(' ').filter(Boolean);
    const [command='',...args]=words,rest=args.join(' ');
    try{
      switch(command){
        case '':case '도움말':case '명령어':return this.help();
        case '모집권':case '모집권받기':return this.claim(playerId);
        case '모집':return this.recruit(playerId,roomId,args);
        case '조합가능':return this.craftable(playerId);
        case '조합':return this.craft(playerId,roomId,args);
        case '도감':case '내정보':return this.me(playerId,roomId);
        case '방도감':return this.roomDex(roomId);
        case '방순위':case '순위':return this.ranking(roomId);
        case '닉네임':return this.nickname(playerId,rest);
        default:return this.respond([`「${command.slice(0,10)}」은(는) 모르는 명령이에요.`,'@갈매미맨 도움말 로 명령어를 볼 수 있어요.']);
      }
    }catch(e){
      // 게임 규칙 안내(재료 부족 등)는 첫 줄만 짧게
      if(e instanceof GameError)return this.respond([e.message.split('\n')[0].slice(0,120)]);
      throw e;
    }
  }
  private async claim(playerId:string){
    const r=await this.game.groupClaim(playerId);
    if(!r.claimed)return this.respond([`⏳ 모집권은 ${minutes(r.waitMs)} 뒤에 받을 수 있어요.`,`🎟 지금 ${r.credits}장`]);
    return this.respond([`🎟 모집권 +${r.amount}!`,`지금 ${r.credits}장 · 다음은 ${r.cooldownHours}시간 뒤`]);
  }
  private async recruit(playerId:string,roomId:string,args:string[]){
    const info=await this.game.webDrawInfo(playerId);
    if(!info)throw new GameError('잠시 뒤 다시 불러 주세요.');
    const kindName=args[0];
    if(!kindName||!GACHA[kindName])return this.respond([
      '🎟 어떤 모집을 할까요?',...info.gachas.map(g=>`${g.label}: 모집권 ${g.cost}장`),`지금 ${info.credits}장 · 예) 모집 고급 3`,
    ],[quick('모집 하급'),quick('모집 중급'),quick('모집 고급'),quick('모집권 받기')]);
    const kind=GACHA[kindName],times=args[1]===undefined?1:Number(args[1]);
    if(!Number.isSafeInteger(times)||times<1||times>MAX_RECRUIT)return this.respond([`단체방에서는 한 번에 1~${MAX_RECRUIT}회까지 모집해요.`,'예) 모집 고급 3']);
    const cost=info.gachas.find(g=>g.kind===kind)!.cost*times;
    if(!info.unlimited&&info.credits<cost)return this.respond([`🎟 모집권이 모자라요. (필요 ${cost}장 · 지금 ${info.credits}장)`,info.wait?`다음 모집권: ${minutes(info.wait)} 뒤`:'@갈매미맨 모집권 받기 로 받을 수 있어요.'],[quick('모집권 받기'),quick('조합가능'),quick('도움말')]);
    const {items}=await this.game.webDraw(playerId,kind,times);
    const fresh=items.filter(i=>i.first).map(i=>i.id);
    const firsts=await this.store.recordRoomFirsts(roomId,playerId,fresh);
    await this.store.bumpWeek(roomId,playerId,{recruits:times,finds:fresh.length});
    const nick=(await this.store.nicknames([playerId])).get(playerId);
    const after=await this.game.webDrawInfo(playerId);
    const names=new Map(items.map(i=>[i.id,i.name]));
    return this.respond(this.squeeze(
      [`🎟 ${who(nick)}님 ${kindName} 모집 ×${times}`],
      items.map(i=>`${emblems[i.tier]??''} ${i.name}${i.n>1?` ×${i.n}`:''}${i.first?' 🆕':''}`),
      [...(firsts.length?[`🏠 우리 방 첫 발견: ${this.names(firsts.map(id=>names.get(id)??'?'))}`]:[]),`남은 모집권 ${after?.credits??0}장`],'종'));
  }
  private async craftable(playerId:string){
    const {recipes}=await this.game.webCraftInfo(playerId);
    const ready=recipes.filter(r=>r.ready);
    if(!ready.length)return this.respond(['🧩 지금 바로 만들 수 있는 조합이 없어요.','모집으로 재료를 모아 보세요.'],[quick('모집 하급'),quick('모집권 받기'),quick('도움말')]);
    const top=ready.slice(0,4);
    return this.respond(this.squeeze(
      [`🧩 지금 만들 수 있는 조합 ${ready.length}개`],
      ready.map(r=>`${emblems[r.tier]??''} ${r.name} ← ${r.materials.map(m=>`${m.name}${m.need>1?`×${m.need}`:''}`).join('+')}`),
      [`예) 조합 ${top[0].name}`]),top.slice(0,4).map(r=>quick(len(`조합 ${r.name}`)<=14?`조합 ${r.name}`:r.name.slice(0,14),`조합 ${r.name}`)));
  }
  private async craft(playerId:string,roomId:string,args:string[]){
    const confirmed=args.at(-1)==='확정',name=(confirmed?args.slice(0,-1):args).join(' ').trim();
    if(!name)return this.respond(['만들 갈매미 이름을 붙여 주세요.','예) 조합 심해수호자 · 목록: 조합가능']);
    const {recipes}=await this.game.webCraftInfo(playerId);
    const recipe=recipes.find(r=>r.name===name);
    if(!recipe)return this.respond([`「${name.slice(0,20)}」 조합식을 찾지 못했어요.`,'이름을 확인하거나 @갈매미맨 조합가능 을 불러 보세요.']);
    const parts=recipe.materials.map(m=>`${m.name}${m.need>1?`×${m.need}`:''}`).join(' + ');
    if(!recipe.ready)return this.respond([`🧩 ${recipe.name} 재료가 모자라요.`,`필요: ${parts}`,...recipe.materials.filter(m=>m.usable<m.need).map(m=>`· ${m.name} ${m.usable}/${m.need}`)]);
    // 높은 등급은 재료를 많이 쓰므로 한 번 더 물어봐요(1:1 챗봇·웹과 같은 기준)
    if(recipe.confirm&&!confirmed)return this.respond([`🧩 ${recipe.name}을(를) 만들까요?`,`재료: ${parts}`,`진행: @갈매미맨 조합 ${recipe.name} 확정`],[quick('확정',`조합 ${recipe.name} 확정`),quick('조합가능')]);
    const before=new Set(await this.game.webCollectionIds(playerId));
    await this.game.webCraft(playerId,recipe.id,1);
    const first=!before.has(recipe.id);
    const firsts=first?await this.store.recordRoomFirsts(roomId,playerId,[recipe.id]):[];
    await this.store.bumpWeek(roomId,playerId,{crafts:1,finds:first?1:0});
    const nick=(await this.store.nicknames([playerId])).get(playerId);
    return this.respond([
      `🧩 ${who(nick)}님 조합 성공!`,
      `${emblems[recipe.tier]??''} ${recipe.name} (${rarityNames[recipe.tier as Tier]??recipe.tierName})${first?' 🆕':''}`,
      ...(firsts.length?['🏠 우리 방 첫 발견이에요!']:[]),
    ]);
  }
  private async me(playerId:string,roomId:string){
    const [mine,room,info]=await Promise.all([this.game.webCollectionIds(playerId),this.store.roomCollection(roomId),this.game.webDrawInfo(playerId)]);
    const nick=(await this.store.nicknames([playerId])).get(playerId),total=this.game.content.characters.length;
    return this.respond([
      `📖 ${who(nick)}님 도감 ${mine.length}/${total}`,
      `🏠 우리 방 도감 ${room.ids.size}/${total} (${room.members}명)`,
      `🎟 모집권 ${info?.credits??0}장`,
      ...(nick?[]:['닉네임을 정하면 방 순위에 이름이 나와요. 예) 닉네임 갈매미왕']),
    ]);
  }
  private async roomDex(roomId:string){
    const room=await this.store.roomCollection(roomId),total=this.game.content.characters.length;
    const recent=await this.store.recentRoomFirsts(roomId);
    const names=await this.store.nicknames(recent.map(r=>r.firstPlayerId).filter((x):x is string=>!!x));
    return this.respond([
      `🏠 우리 방 도감 ${room.ids.size}/${total}`,
      `함께 모은 사람 ${room.members}명`,
      ...(recent.length?['최근 우리 방 첫 발견',...recent.map(r=>`· ${this.game.content.map.get(r.characterId)?.name??'?'} — ${who(r.firstPlayerId?names.get(r.firstPlayerId):null)}`)]:['아직 이 방에서 처음 찾은 갈매미가 없어요.']),
    ],[quick('방순위'),quick('모집 하급'),quick('도감')]);
  }
  private async ranking(roomId:string){
    const {week,rows}=await this.store.weekRanking(roomId);
    if(!rows.length)return this.respond(['🏆 이번 주 방 순위가 아직 없어요.','모집·조합을 하면 순위에 올라가요.'],[quick('모집 하급'),quick('조합가능')]);
    return this.respond([
      `🏆 이번 주 방 순위 (${week.slice(5).replace('-','/')}~)`,
      ...rows.map((r,i)=>`${i+1}. ${who(r.name)} — 새 발견 ${r.finds} · 조합 ${r.crafts} · 모집 ${r.recruits}`),
    ],[quick('방도감'),quick('모집 하급'),quick('조합가능')]);
  }
  private async nickname(playerId:string,name:string){
    if(!name)return this.respond(['🏷 닉네임은 방 순위에 표시돼요.','예) 닉네임 갈매미왕 (2~10자, 한글·영문·숫자)']);
    const changed=await this.game.groupSetNickname(playerId,name);
    return this.respond([changed?`🏷 닉네임을 「${name.trim()}」(으)로 정했어요.`:`이미 「${name.trim()}」이에요.`]);
  }
}
