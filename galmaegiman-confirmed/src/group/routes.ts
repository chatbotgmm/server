// 단체방 챗봇 스킬 주소. 챗봇 관리자센터의 블록마다 아래 주소를 스킬로 연결해요.
//   POST /kakao/group/skill    : 명령(폴백·일반 블록)
//   POST /kakao/group/welcome  : 봇 입장 블록(덱 24: 짧은 인사)
//   POST /kakao/group/help     : 도움말 블록(덱 23)
// 헤더 X-Skill-Secret = GROUP_SKILL_SECRET. 1:1 챗봇(/kakao/skill)과 비밀값을 나눠요.
import { createHash, timingSafeEqual } from 'node:crypto';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import type { PrismaClient } from '@prisma/client';
import type { GameService } from '../game.js';
import { loadGroupBotConfig, type BotEnv } from './config.js';
import { GroupRequestError, parseGroupRequest, requestShape } from './request.js';
import { buildResponse, text } from './response.js';
import { GroupBot } from './service.js';
import { GroupStore } from './store.js';
export interface GroupRouteOptions {secret:string;env:BotEnv;botId?:string;root?:string;clock?:()=>Date}
export const GROUP_SKILL_PATHS=['/kakao/group/skill','/kakao/group/welcome','/kakao/group/help'] as const;
export function registerGroupBot(app:FastifyInstance,db:PrismaClient,game:GameService,options:GroupRouteOptions){
  if(options.secret.length<32)throw new Error('GROUP_SKILL_SECRET을 최소 32자로 설정하세요.');
  const config=loadGroupBotConfig(options.root);
  const bot=new GroupBot(game,new GroupStore(db,game,options.clock),config,game.content.economy.homepage??'https://gmm.dpdns.org');
  const fallback=()=>buildResponse([text('지금은 처리하지 못했어요. 잠시 뒤 다시 불러 주세요.')],[],{config});
  // 한 사람당 1분에 20번까지(메모리, 1분 지나면 버림). [우리] 단체방은 1:1(40번)보다 낮게
  const hits=new Map<string,number[]>();
  const allow=(key:string,now=Date.now())=>{
    const recent=(hits.get(key)??[]).filter(t=>now-t<60_000);
    if(recent.length>=20){hits.set(key,recent);return false;}
    recent.push(now);hits.set(key,recent);
    if(hits.size>5000)for(const [k,v] of hits)if(!v.some(t=>now-t<60_000))hits.delete(k);
    return true;
  };
  const auth=async(req:FastifyRequest,reply:FastifyReply)=>{
    const header=req.headers['x-skill-secret'];
    const supplied=typeof header==='string'?Buffer.from(header):Buffer.alloc(0),expected=Buffer.from(options.secret);
    if(supplied.length!==expected.length||!timingSafeEqual(supplied,expected))return reply.code(401).send({error:'skill authentication required'});
  };
  for(const path of GROUP_SKILL_PATHS)app.post(path,{onRequest:auth,config:{rateLimit:{max:1200,timeWindow:'1 minute',keyGenerator:()=>'kakao-group-skill'}}},async(req,reply)=>{
    // 개발: 실제 요청 구조 확인용으로 키 이름 모양만 기록(값·발화는 기록하지 않음). TODO(verify) 해소 후 지워요
    if(options.env==='dev')req.log.info({shape:requestShape(req.body)},'group skill request shape');
    try{
      const input=parseGroupRequest(req.body,options.env,{botId:options.botId});
      if(path==='/kakao/group/welcome')return bot.welcome();
      if(path==='/kakao/group/help')return bot.help();
      if(!allow(`${input.env}:${input.botUserKey}`))return buildResponse([text('⏳ 너무 빨리 부르고 있어요. 잠깐 뒤 다시 불러 주세요.')],[],{config});
      const out=await bot.handle(input);
      // 진단용: 명령어 첫 낱말과 응답 크기만(뒤의 글자에는 닉네임 등이 들어갈 수 있어 기록하지 않음)
      req.log.info({command:input.utterance.split(/\s+/)[0]?.slice(0,12)??'',env:input.env,bytes:JSON.stringify(out).length},'group response');
      return out;
    }catch(e){
      if(e instanceof GroupRequestError){
        if(e.message==='BOT_MISMATCH')return reply.code(403).send({error:'bot mismatch'});
        return reply.code(400).send(fallback());
      }
      req.log.error({errorCode:e instanceof Error?e.name:'Unknown',reference:createHash('sha256').update(req.id).digest('hex').slice(0,12)},'group request failed');
      return fallback();
    }
  });
}
