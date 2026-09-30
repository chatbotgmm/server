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
export function kakaoResponse(result:GameReply,baseUrl=''){
  const outputs:object[]=[];
  if(result.imageId&&baseUrl&&/^[CUSR]\d{3}[MF]$/.test(result.imageId)){
    outputs.push({simpleImage:{imageUrl:`${baseUrl}/images/${result.imageId}.png`,altText:(result.imageName??'갈매미맨').slice(0,50)}});
  }
  if(result.list){
    if(result.list.items.length<1||result.list.items.length>5)throw new Error('리스트 항목 수 오류');
    if(result.list.showText)outputs.push({simpleText:{text:result.text.slice(0,950)}});
    // 목록은 중복 본문 없이 터치할 수 있는 이름과 설명으로 표시합니다.
    outputs.push({listCard:{header:{title:result.list.title},items:result.list.items.map(item=>({
      title:item.title,description:item.description,action:'message',messageText:item.message,
      ...(baseUrl&&item.imageId&&/^[CUSR]\d{3}[MF]$/.test(item.imageId)?{imageUrl:`${baseUrl}/images/${item.imageId}.png`}:{}),
      ...(item.button?{extra:{gmAction:item.button.action,gmToken:item.button.token}}:{})
    }))}});
  }else{
    // 일반 본문은 카카오 제한 안에서 분할합니다.
    for(let i=0;i<Math.min(result.text.length,1900);i+=950)outputs.push({simpleText:{text:result.text.slice(i,i+950)}});
  }
  return {version:'2.0',template:{outputs:outputs.slice(0,3),quickReplies:(result.choices??[]).slice(0,10).map(c=>({action:'message',label:c.label.slice(0,14),messageText:c.message}))}};
}
