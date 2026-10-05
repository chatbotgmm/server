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
  return {identity:JSON.stringify(['KAKAO_CHANNEL',p.bot.id,p.userRequest.user.type??'botUserKey',p.userRequest.user.id]),message:p.userRequest.utterance,button};
}
// 유닛 ID와 화면용 그림(미발견·탐험·뽑기 3종)
export const imageIdPattern=/^(?:[CUSRLHDTEIN]\d{1,2}|unknown|explore|ticket|s-(?:info|oops|wait|shop|exchange|mix|dex|rank|peanut|ticket|event|lock|tutorial|timeout|invite|sell)|m-(?:draw|craft|rare|legend|top)|gacha-(?:LOW|MID|HIGH)|q-(?:COMMON|UNCOMMON|SPECIAL|RARE|LEGEND|HIDDEN|LIMITED|TRANSCEND|ETERNAL|IMMORTAL|NEUTRAL)|y-(?:BASIC|GOLD|DARK|SEA|BLOSSOM|COSMOS|OUTFIT))$/;
const quick=(result:GameReply)=>(result.choices??[]).slice(0,10).map(c=>({action:'message',label:c.label.slice(0,14),messageText:c.message}));
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
// 카드 넘기기 버튼 명령(messageText) 합계 예산. 실기기 진단: 743바이트는 표시, 854바이트부터 카카오가
// 응답을 표시하지 않음(서버는 200). 여유를 두고 600바이트로 제한하고, 넘치면 뒤 카드부터 두 번째 버튼을 빼고,
// 그래도 넘치면 뒤 카드의 버튼을 뺍니다. 화면별로 예산 안에 들도록 만들고 이건 안전장치로만 씁니다.
export const CAROUSEL_BUTTON_BUDGET=600;
type CardItem=NonNullable<GameReply['cards']>['items'][number];
export function fitButtons(items:CardItem[],budget=CAROUSEL_BUTTON_BUDGET):CardItem[]{
  const out=items.map(c=>({...c,buttons:c.buttons?.slice(0,2)}));
  const bytes=()=>out.reduce((sum,c)=>sum+(c.buttons??[]).reduce((s,b)=>s+Buffer.byteLength(b.message),0),0);
  for(let i=out.length-1;i>=0&&bytes()>budget;i--)if((out[i].buttons?.length??0)>1)out[i]={...out[i],buttons:out[i].buttons!.slice(0,1)};
  for(let i=out.length-1;i>=0&&bytes()>budget;i--)if(out[i].buttons?.length)out[i]={...out[i],buttons:[]};
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
      ...(c.buttons?.length?{buttons:c.buttons.slice(0,many?2:3).map((b,_,all)=>({action:'message',label:b.label.slice(0,all.length===2?7:14),messageText:b.message}))}:{})
    });
    // 카드 넘기기는 한 묶음 10장까지라 11장 이상이면 두 묶음으로 나눕니다
    const shown=many?fitButtons(cards.items.slice(0,20)):cards.items;
    const chunks=[shown.slice(0,10),shown.slice(10,20)].filter(c=>c.length);
    const visuals=cards.items.length===1?[{basicCard:card(cards.items[0])}]:chunks.map(c=>({carousel:{type:'basicCard',items:c.map(card)}}));
    const text=(t?:string)=>t?[{simpleText:{text:t.slice(0,950)}}]:[];
    // 말풍선은 최대 3개: 넘치면 머리말부터 뺍니다(확인 목록·그림·본문은 남김).
    outputs.push(...text(cards.intro),...visuals,...text(cards.outro),...(result.list?[listCard(result.list,baseUrl?url:undefined)]:[]));
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
