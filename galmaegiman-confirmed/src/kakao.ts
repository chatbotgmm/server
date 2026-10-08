import { z } from 'zod';
import type { ButtonAction, GameReply } from './types.js';
const payload=z.object({
  bot:z.object({id:z.string().min(1).max(128)}),
  userRequest:z.object({utterance:z.string().min(1).max(1000),user:z.object({
    id:z.string().min(1).max(128),type:z.string().max(64).optional(),
    properties:z.record(z.string(),z.unknown()).nullish()
  })}),
  action:z.object({clientExtra:z.record(z.string(),z.unknown()).nullish()}).nullish()
});
const buttonSchema=z.object({gmAction:z.enum(['confirm','cancel']),gmToken:z.string().regex(/^[a-f0-9]{16}$/)});
export function parseKakao(input:unknown,expectedBot?:string){
  const p=payload.parse(input);
  if(expectedBot&&p.bot.id!==expectedBot)throw new Error('BOT_MISMATCH');
  const extra=p.action?.clientExtra;
  let button:ButtonAction|undefined;
  if(extra&&('gmAction' in extra||'gmToken' in extra)){
    const value=buttonSchema.parse(extra);
    button={action:value.gmAction,token:value.gmToken};
  }
  // 채널이 카카오 디벨로퍼스 앱과 연결돼 있으면 카카오 로그인 회원번호(appUserId)가 같이 올 수 있음(숫자만 받음)
  const app=p.userRequest.user.properties?.appUserId;
  const appUserId=(typeof app==='string'||typeof app==='number')&&/^\d{1,20}$/.test(String(app))?String(app):undefined;
  return {identity:JSON.stringify(['KAKAO_CHANNEL',p.bot.id,p.userRequest.user.type??'botUserKey',p.userRequest.user.id]),message:p.userRequest.utterance,button,bot:p.bot.id,appUserId};
}
// 유닛 ID와 화면용 그림(미발견·탐험·뽑기 3종)
export const imageIdPattern=/^(?:[CUSRLHDTEIN]\d{1,2}|unknown|logo|explore|ticket|s-(?:info|oops|wait|shop|exchange|mix|dex|rank|peanut|ticket|event|lock|tutorial|timeout|invite|sell)|m-(?:draw|craft|rare|legend|top)|gacha-(?:LOW|MID|HIGH)|q-(?:COMMON|UNCOMMON|SPECIAL|RARE|LEGEND|HIDDEN|LIMITED|TRANSCEND|ETERNAL|IMMORTAL|NEUTRAL)|y-(?:BASIC|GOLD|DARK|SEA|BLOSSOM|COSMOS|OUTFIT))$/;
const quick=(result:GameReply)=>(result.choices??[]).filter(c=>c.kind!=='share').slice(0,10).map(c=>({action:'message',label:c.label.slice(0,14),messageText:c.message}));
const listCard=(list:NonNullable<GameReply['list']>,url?:(path:string)=>string)=>{
  if(list.items.length<1||list.items.length>5)throw new Error('리스트 항목 수 오류');
  // 목록은 중복 본문 없이 터치할 수 있는 이름과 설명으로 표시합니다.
  return {listCard:{header:{title:list.title},items:list.items.map(item=>({
    title:item.title,description:item.description,action:'message',messageText:item.message,
    ...(url&&item.imageId&&imageIdPattern.test(item.imageId)?{imageUrl:url(`thumbs/${item.imageId}.jpg`)}:{}),
    ...(item.button?{extra:{gmAction:item.button.action,gmToken:item.button.token}}:{})
  }))}};
};
// stamp: 그림 파일이 바뀌면 주소도 바뀌게 붙이는 버전 값(카카오 그림 캐시 무효화)
// 카드 넘기기 글자 예산. 실기기 진단으로 본 기준: 카드 묶음의 글자 합계(제목×2(대체 글자 포함)+설명+버튼 이름+버튼 명령,
// UTF-8 바이트)가 1,297은 표시, 1,492부터 카카오가 응답을 표시하지 않음(서버는 200). 여유를 두고 1,150으로 제한.
// 화면은 cardCost로 쪽당 카드 수를 정하고, fitCards는 넘칠 때만 쓰는 안전장치(두 번째 버튼 → 설명 둘째 줄 → 버튼 순으로 뺌).
export const CAROUSEL_TEXT_BUDGET=1150;
type CardItem=NonNullable<GameReply['cards']>['items'][number];
const bytes=(s?:string)=>Buffer.byteLength(s??'');
const shownButtons=(c:CardItem)=>(c.buttons??[]).slice(0,2).map((b,_,all)=>({label:b.label.slice(0,all.length===2?7:14),message:b.message}));
export function cardCost(c:CardItem){
  return bytes(c.title.slice(0,50))*2+bytes(c.description?.slice(0,230))+shownButtons(c).reduce((s,b)=>s+bytes(b.label)+bytes(b.message),0);
}
// 이 카드들을 한 쪽에 몇 장씩 보여야 예산 안에 드는지(가장 무거운 카드 기준, 1~max)
export function cardsPerPage(items:CardItem[],max=10,reserve=0){
  const worst=Math.max(1,...items.map(cardCost));
  return Math.max(1,Math.min(max,Math.floor((CAROUSEL_TEXT_BUDGET-reserve)/worst)));
}
export function fitCards(items:CardItem[],budget=CAROUSEL_TEXT_BUDGET):CardItem[]{
  const out=items.map(c=>({...c,buttons:c.buttons?.slice(0,2)}));
  const total=()=>out.reduce((s,c)=>s+cardCost(c),0);
  for(let i=out.length-1;i>=0&&total()>budget;i--)if((out[i].buttons?.length??0)>1)out[i]={...out[i],buttons:out[i].buttons!.slice(0,1)};
  for(let i=out.length-1;i>=0&&total()>budget;i--)if(out[i].description?.includes('\n'))out[i]={...out[i],description:out[i].description!.split('\n')[0]};
  for(let i=out.length-1;i>=0&&total()>budget;i--)if(out[i].buttons?.length)out[i]={...out[i],buttons:[]};
  return out;
}
export function kakaoResponse(result:GameReply,baseUrl='',stamp:(path:string)=>string=()=>''){
  const url=(path:string)=>{const v=stamp(path);return `${baseUrl}/${path}${v?`?v=${v}`:''}`;};
  const outputs:object[]=[];
  const cards=result.cards;
  if(cards&&baseUrl&&cards.items.length>=1&&cards.items.length<=20&&cards.items.every(c=>imageIdPattern.test(c.imageId))){
    // 카드 넘기기 안의 카드는 버튼이 2개까지만 보여서(실기기 확인) 2개로 자릅니다
    const many=cards.items.length>1;
    const card=(c:typeof cards.items[number])=>({
      title:c.title.slice(0,50),...(c.description?{description:c.description.slice(0,230)}:{}),
      thumbnail:{imageUrl:url(`thumbs/${c.imageId}.jpg`),altText:c.title.slice(0,50),fixedRatio:true},
      ...(c.buttons?.length?{buttons:c.buttons.slice(0,many?2:3).map((b,_,all)=>b.kind==='share'?{action:'share',label:b.label.slice(0,all.length===2?7:14)}:{action:'message',label:b.label.slice(0,all.length===2?7:14),messageText:b.message})}:{})
    });
    // 카드 넘기기는 한 묶음 10장까지라 11장 이상이면 두 묶음으로 나누고, 묶음마다 글자 예산 안전장치
    const chunks=[cards.items.slice(0,10),cards.items.slice(10,20)].filter(c=>c.length).map(c=>many?fitCards(c):c);
    const visuals:object[]=cards.items.length===1?[{basicCard:card(cards.items[0])}]:chunks.map(c=>({carousel:{type:'basicCard',items:c.map(card)}}));
    const text=(t?:string)=>t?[{simpleText:{text:t.slice(0,950)}}]:[];
    // 카드 위주로: 연출(keepIntro)만 카드 앞 말풍선으로 남기고, 나머지 글은
    // 카드 1장이면 그 카드 설명에 합치고(230자 안일 때), 아니면 카드 뒤 말풍선 1개로 모읍니다.
    // 카드 1장: 아래 글(outro)은 카드 설명에 합치고, 머리말(intro)은 카드 앞 말풍선(유닛 카드 안에 머리말이 섞이면 헷갈려서)
    // 카드 여러 장: 머리말·요약을 카드 뒤 말풍선 1개로. 전설 이상 연출(keepIntro)은 항상 카드 앞
    const single=cards.items.length===1;
    const lead=cards.keepIntro||single?cards.intro:undefined;
    let tail=[lead?undefined:cards.intro,cards.outro].filter((t):t is string=>Boolean(t&&t.trim())).join('\n\n');
    if(single&&cards.outro){
      const compact=(t:string)=>t.split('\n').filter(l=>l.trim()&&!/^─+$/.test(l.trim())).join('\n');
      const c0=cards.items[0],merged=[compact(c0.description??''),compact(cards.outro)].filter(Boolean).join('\n');
      if(merged.length<=230){visuals[0]={basicCard:card({...c0,description:merged})};tail='';}
    }
    // 말풍선은 최대 3개: 넘치면 앞에서부터 뺍니다(확인 목록·그림은 남김).
    outputs.push(...text(lead),...visuals,...text(tail),...(result.list?[listCard(result.list,baseUrl?url:undefined)]:[]));
    return {version:'2.0',template:{outputs:outputs.slice(-3),quickReplies:quick(result)}};
  }
  if(result.imageId&&baseUrl&&/^[CUSRLHDTEIN]\d{1,2}$/.test(result.imageId)){
    outputs.push({simpleImage:{imageUrl:url(`images/${result.imageId}.png`),altText:(result.imageName??'갈매미맨').slice(0,50)}});
  }
  if(result.list){
    if(result.list.showText)outputs.push({simpleText:{text:result.text.slice(0,950)}});
    outputs.push(listCard(result.list,baseUrl?url:undefined));
  }else{
    // 일반 본문은 카카오 제한 안에서 분할합니다.
    for(let i=0;i<Math.min(result.text.length,1900);i+=950)outputs.push({simpleText:{text:result.text.slice(i,i+950)}});
  }
  return {version:'2.0',template:{outputs:outputs.slice(0,3),quickReplies:quick(result)}};
}
