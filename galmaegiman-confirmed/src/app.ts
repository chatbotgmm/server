import Fastify from 'fastify';
import rateLimit from '@fastify/rate-limit';
import { createHash, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { PrismaClient } from '@prisma/client';
import { ZodError } from 'zod';
import { GameService } from './game.js';
import { parseKakao, kakaoResponse } from './kakao.js';
import { GameError } from './types.js';
export interface AppOptions {secret:string;botId?:string;log?:boolean;baseUrl:()=>Promise<string>;root?:string}
export async function buildApp(db:PrismaClient,game:GameService,options:AppOptions){
  if(options.secret.length<32)throw new Error('SKILL_SECRET을 최소 32자로 설정하세요.');
  const app=Fastify({logger:options.log??false,bodyLimit:32_768,trustProxy:false});
  await app.register(rateLimit,{global:false});
  app.get('/health',async()=>({ok:true,mode:'KAKAO_CHANNEL',edition:'GALMAEMI_118',version:'0.7.0'}));
  app.get('/ready',async(_req,reply)=>{
    try{await db.$queryRaw`SELECT 1`;return {ready:true};}catch{return reply.code(503).send({ready:false});}
  });
  const known=(id:string)=>id==='unknown'||game.content.map.has(id);
  app.get<{Params:{file:string}}>('/images/:file',async(req,reply)=>{
    if(!/^(?:[CUSRLHDTEI]\d{1,2}|unknown)\.png$/.test(req.params.file)||!known(req.params.file.slice(0,-4)))return reply.code(404).send({error:'not found'});
    try{return reply.type('image/png').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(options.root??process.cwd(),'public/images',req.params.file)));}
    catch{return reply.code(404).send({error:'image missing'});}
  });
  // 카드용 작은 그림(JPEG). 없으면 원본 PNG로 대신합니다.
  app.get<{Params:{file:string}}>('/thumbs/:file',async(req,reply)=>{
    const id=req.params.file.slice(0,-4);
    if(!/^(?:[CUSRLHDTEI]\d{1,2}|unknown)\.jpg$/.test(req.params.file)||!known(id))return reply.code(404).send({error:'not found'});
    const root=options.root??process.cwd();
    try{return reply.type('image/jpeg').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(root,'public/thumbs',req.params.file)));}
    catch{
      try{return reply.type('image/png').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(root,'public/images',`${id}.png`)));}
      catch{return reply.code(404).send({error:'image missing'});}
    }
  });
  for(const path of ['/kakao/skill','/kakao'])app.post(path,{
    // 카카오가 보내는 요청만 키 검증 후 처리. 외부 전달 헤더를 사용자 권한으로 쓰지 않습니다.
    onRequest:async(req,reply)=>{
      const header=req.headers['x-skill-secret'];
      const supplied=typeof header==='string'?Buffer.from(header):Buffer.alloc(0);
      const expected=Buffer.from(options.secret);
      if(supplied.length!==expected.length||!timingSafeEqual(supplied,expected))return reply.code(401).send({error:'skill authentication required'});
    },
    config:{rateLimit:{max:180,timeWindow:'1 minute',keyGenerator:()=> 'kakao-skill'}},
  },async(req,reply)=>{
    try{
      const input=parseKakao(req.body,options.botId);
      const result=await game.handle(input.identity,input.message,input.button);
      return kakaoResponse(result,await options.baseUrl());
    }catch(e){
      if(e instanceof GameError){
        // 게임 안내 오류도 카드로: 첫 줄 = 제목, 나머지 = 설명
        const [title,...rest]=e.message.split('\n');
        return kakaoResponse({text:e.message,cards:{items:[{imageId:'C3',title,description:rest.join('\n')}]},choices:[{label:'도움말',message:'도움말'},{label:'뽑기',message:'뽑기'},{label:'내정보',message:'내정보'}]},await options.baseUrl());
      }
      if(e instanceof ZodError)return reply.code(400).send(kakaoResponse({text:'스킬 요청의 bot.id, userRequest.user.id, utterance를 확인하세요.'}));
      if(e instanceof Error&&e.message==='BOT_MISMATCH')return reply.code(403).send({error:'bot mismatch'});
      // 사용자의 발화/ID/비밀키/요청 본문은 기록하지 않습니다.
      req.log.error({errorCode:e instanceof Error?e.name:'Unknown',reference:createHash('sha256').update(req.id).digest('hex').slice(0,12)},'game request failed');
      return kakaoResponse({text:'처리에 실패했습니다. 서버 터미널과 DB 준비 상태를 확인해 주세요. 미완료 작업은 취소됩니다.'});
    }
  });
  return app;
}
