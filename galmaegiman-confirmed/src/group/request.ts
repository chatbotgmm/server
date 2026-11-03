// 단체방 스킬 요청 파서.
// 근거: 그룹 챗봇 beta 가이드 덱 28 「스킬로 전달되는 주요 데이터」의 글자 순서
//   userRequest → block(id, name) / user.properties(botUserKey, plusfreindUserKey, appUserId, appUserStatus)
//               / chat.properties(botGroupKey) / utterance, params, lang, timezone
// TODO(verify): 위 중첩 경로는 덱 글자 순서로 읽은 것이고 예시 JSON으로 확인하지 못했어요.
//   개발(dev)에서는 요청의 '키 이름 모양'(값 없이)을 기록해 실제 구조와 맞는지 확인할 수 있게 했어요(requestShape).
import { z } from 'zod';
import type { BotEnv } from './config.js';
const key=z.string().min(1).max(256);
const payload=z.object({
  bot:z.object({id:z.string().min(1).max(128)}).passthrough().optional(),
  userRequest:z.object({
    block:z.object({id:z.string().max(128).optional(),name:z.string().max(200).optional()}).passthrough().nullish(),
    utterance:z.string().max(1000),
    params:z.record(z.string(),z.unknown()).nullish(),
    lang:z.string().max(32).nullish(),
    timezone:z.string().max(64).nullish(),
    user:z.object({
      properties:z.object({
        botUserKey:key,
        plusfriendUserKey:key.optional(),
        plusfreindUserKey:key.optional(),   // 덱 원문 철자. 실제 필드명 확인 전이라 두 철자를 모두 받아요(docs/group-bot/open-questions.md)
        appUserId:z.union([z.string(),z.number()]).optional(),
        appUserStatus:z.string().max(32).optional(),
      }).passthrough(),
    }).passthrough(),
    chat:z.object({properties:z.object({botGroupKey:key}).passthrough()}).passthrough(),
  }).passthrough(),
}).passthrough();
export interface GroupRequest {
  env:BotEnv;bot?:string;block?:{id?:string;name?:string};
  utterance:string;botUserKey:string;plusfriendUserKey?:string;appUserId?:string;groupKey:string;
}
export class GroupRequestError extends Error {constructor(message:string){super(message);this.name='GroupRequestError';}}
// 멘션으로 부르면 발화 앞에 '@챗봇이름'이 붙어 올 수 있어 떼어 내요.
// TODO(verify): 멘션이 발화에 어떤 모양으로 들어오는지 미확인. 앞쪽 '@이름 '만 지워요.
export function stripBotMention(utterance:string,names:string[]){
  let t=utterance.normalize('NFKC').trim();
  for(const n of names)if(t.startsWith(`@${n}`)){t=t.slice(n.length+1).trim();break;}
  return t;
}
export function parseGroupRequest(input:unknown,env:BotEnv,options:{botId?:string;botNames?:string[]}={}):GroupRequest{
  const r=payload.safeParse(input);
  if(!r.success)throw new GroupRequestError('단체방 스킬 요청 형식이 달라요');
  const p=r.data,u=p.userRequest,props=u.user.properties;
  if(options.botId&&p.bot?.id!==options.botId)throw new GroupRequestError('BOT_MISMATCH');
  const app=props.appUserId;
  // 카카오 로그인 회원번호: 숫자만 받고, 가입·연결 상태(registered)일 때만 써요(덱 28)
  const appUserId=props.appUserStatus==='registered'&&(typeof app==='string'||typeof app==='number')&&/^\d{1,20}$/.test(String(app))?String(app):undefined;
  return {env,...(p.bot?.id?{bot:p.bot.id}:{}),...(u.block?{block:{...(u.block.id?{id:u.block.id}:{}),...(u.block.name?{name:u.block.name}:{})}}:{}),
    utterance:stripBotMention(u.utterance,options.botNames??['갈매미맨']),botUserKey:props.botUserKey,
    ...(props.plusfriendUserKey??props.plusfreindUserKey?{plusfriendUserKey:props.plusfriendUserKey??props.plusfreindUserKey}:{}),
    ...(appUserId?{appUserId}:{}),groupKey:u.chat.properties.botGroupKey};
}
// 요청의 키 이름 모양만(값은 버림). 개발에서 실제 구조를 확인하는 용도. 예) {userRequest:{chat:{properties:{botGroupKey:'string'}}}}
export function requestShape(value:unknown,depth=0):unknown{
  if(depth>6)return '…';
  if(Array.isArray(value))return value.length?[requestShape(value[0],depth+1)]:[];
  if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).slice(0,40).map(([k,v])=>[k.slice(0,40),requestShape(v,depth+1)]));
  return value===null?'null':typeof value;
}
