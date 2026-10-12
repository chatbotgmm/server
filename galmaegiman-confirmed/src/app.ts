import { statSync } from 'node:fs';
import Fastify, { type FastifyReply } from 'fastify';
import rateLimit from '@fastify/rate-limit';
import { createHash, timingSafeEqual } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import type { PrismaClient } from '@prisma/client';
import { ZodError } from 'zod';
import { GameService } from './game.js';
import { parseKakao, kakaoResponse, imageIdPattern } from './kakao.js';
import { GameError } from './types.js';
import { registerWeb } from './web.js';
import { battleRoster } from './battle/prototype.js';
import { registerDuel } from './duel.js';
import { registerMy, type KakaoLogin } from './my.js';
export const VERSION='0.15.7';
export interface AppOptions {secret:string;botId?:string;log?:boolean;baseUrl:()=>Promise<string>;root?:string;kakaoLogin?:KakaoLogin;fetch?:typeof fetch;reviewCode?:string}
export async function buildApp(db:PrismaClient,game:GameService,options:AppOptions){
  if(options.secret.length<32)throw new Error('SKILL_SECRET을 최소 32자로 설정하세요.');
  const app=Fastify({logger:options.log??false,bodyLimit:32_768,trustProxy:false});
  await app.register(rateLimit,{global:false});
  app.get('/health',async()=>({ok:true,mode:'KAKAO_CHANNEL',edition:'GALMAEMI_118',version:VERSION}));
  app.get('/ready',async(_req,reply)=>{
    try{await db.$queryRaw`SELECT 1`;return {ready:true};}catch{return reply.code(503).send({ready:false});}
  });
  const known=(id:string)=>imageIdPattern.test(id)&&(!/^[CUSRLHDTEIN]\d/.test(id)||game.content.map.has(id));
  app.get<{Params:{file:string}}>('/images/:file',async(req,reply)=>{
    if(!/^[A-Za-z0-9-]+\.png$/.test(req.params.file)||!known(req.params.file.slice(0,-4)))return reply.code(404).send({error:'not found'});
    try{return reply.type('image/png').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(options.root??process.cwd(),'public/images',req.params.file)));}
    catch{return reply.code(404).send({error:'image missing'});}
  });
  // 카드용 작은 그림(JPEG). 없으면 원본 PNG로 대신합니다.
  app.get<{Params:{file:string}}>('/thumbs/:file',async(req,reply)=>{
    const id=req.params.file.slice(0,-4);
    if(!/^[A-Za-z0-9-]+\.jpg$/.test(req.params.file)||!known(id))return reply.code(404).send({error:'not found'});
    const root=options.root??process.cwd();
    try{return reply.type('image/jpeg').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(root,'public/thumbs',req.params.file)));}
    catch{
      try{return reply.type('image/png').header('Cache-Control','public, max-age=3600').send(await readFile(resolve(root,'public/images',`${id}.png`)));}
      catch{return reply.code(404).send({error:'image missing'});}
    }
  });
  // 횡스크롤 전투 시험판(/play). 그림 목록은 실제 있는 파일만 알려 주고, 없는 그림은 게임이 임시 도형으로 대신합니다.
  const playDir=resolve(options.root??process.cwd(),'public/play');
  const phaserFile=resolve(dirname(createRequire(import.meta.url).resolve('phaser/package.json')),'dist/phaser-arcade-physics.min.js');
  const sendFile=async(reply:FastifyReply,type:string,path:string,maxAge=300)=>{
    const body=await readFile(path).catch(()=>null);
    if(!body)return reply.code(404).send({error:'not found'});
    return reply.type(type).header('Cache-Control',`public, max-age=${maxAge}`).send(body);
  };
  app.get('/play',async(_req,reply)=>sendFile(reply,'text/html; charset=utf-8',resolve(playDir,'index.html')));
  app.get('/play/game.js',async(_req,reply)=>sendFile(reply,'text/javascript; charset=utf-8',resolve(playDir,'game.js')));
  // 2.5D(Three.js 포함) 묶음 파일은 빌드 때 만들어짐(scripts/build-play.mjs)
  app.get('/play/game3d.js',async(_req,reply)=>sendFile(reply,'text/javascript; charset=utf-8',resolve(options.root??process.cwd(),'dist/play/game3d.js')));
  app.get('/play/phaser.js',async(_req,reply)=>sendFile(reply,'text/javascript; charset=utf-8',phaserFile,86_400));
  app.get('/play/sprites.json',async(_req,reply)=>{
    const names=await readdir(resolve(playDir,'sprites')).catch(()=>[] as string[]);
    return reply.header('Cache-Control','public, max-age=300').send(names.filter(n=>/^[a-z0-9_]+\.png$/.test(n)).map(n=>n.slice(0,-4)).sort());
  });
  app.get<{Params:{file:string}}>('/play/sprites/:file',async(req,reply)=>{
    if(!/^[a-z0-9_]+\.png$/.test(req.params.file))return reply.code(404).send({error:'not found'});
    return sendFile(reply,'image/png',resolve(playDir,'sprites',req.params.file),3600);
  });
  // 턴제 배틀 시험판(/battle): 화면·스크립트, 유닛 전투 정보(등급·출신 동네로 계산, 히든·최상위 제외)
  const battleDir=resolve(options.root??process.cwd(),'public/battle');
  const roster=JSON.stringify(battleRoster(game.content));
  app.get('/battle',async(_req,reply)=>sendFile(reply,'text/html; charset=utf-8',resolve(battleDir,'index.html')));
  app.get('/battle/battle.js',async(_req,reply)=>sendFile(reply,'text/javascript; charset=utf-8',resolve(battleDir,'battle.js')));
  app.get('/battle/roster.json',async(_req,reply)=>reply.type('application/json; charset=utf-8').header('Cache-Control','public, max-age=300').send(roster));
  registerDuel(app,game.content,{root:options.root??process.cwd(),secret:options.secret});
  registerMy(app,game,{root:options.root??process.cwd(),secret:options.secret,baseUrl:options.baseUrl,kakao:options.kakaoLogin,fetch:options.fetch,reviewCode:options.reviewCode});
  // 그림 파일 수정 시각을 주소 뒤에 붙여, 그림을 바꾸면 카카오가 새로 받아 가게 합니다
  const stamps=new Map<string,string>();
  const stamp=(path:string)=>{
    if(!stamps.has(path)){
      try{stamps.set(path,Math.floor(statSync(resolve(options.root??process.cwd(),'public',path)).mtimeMs).toString(36));}catch{stamps.set(path,'');}
    }
    return stamps.get(path)!;
  };
  // 공개 웹페이지(홈·세계관·도감)
  registerWeb(app,game.content,{root:options.root??process.cwd(),stamp,version:VERSION,salt:options.secret,data:{
    unitComments:id=>game.unitComments(id),recentComments:n=>game.recentComments(n),topComments:n=>game.topComments(n),like:(id,v)=>game.webLike(id,v),
    ranking:kind=>game.publicRanking(kind),seenCounts:()=>game.seenCounts(),hiddenFirsts:()=>game.hiddenFirsts(),
    searchPlayers:q=>game.searchPlayers(q),profile:n=>game.webProfile(n),source:id=>game.sourceText(id)}});
  // 한 사람당 1분에 40번까지(카톡 버튼 연타·자동 입력 방지). 메모리에만 두고 1분 지난 기록은 버립니다
  const hits=new Map<string,number[]>();
  const perUser=(id:string,now=Date.now())=>{
    const recent=(hits.get(id)??[]).filter(t=>now-t<60_000);
    if(recent.length>=40){hits.set(id,recent);return false;}
    recent.push(now);hits.set(id,recent);
    if(hits.size>5000)for(const [k,v] of hits)if(!v.some(t=>now-t<60_000))hits.delete(k);
    return true;
  };
  for(const path of ['/kakao/skill','/kakao'])app.post(path,{
    // 카카오가 보내는 요청만 키 검증 후 처리. 외부 전달 헤더를 사용자 권한으로 쓰지 않습니다.
    onRequest:async(req,reply)=>{
      const header=req.headers['x-skill-secret'];
      const supplied=typeof header==='string'?Buffer.from(header):Buffer.alloc(0);
      const expected=Buffer.from(options.secret);
      if(supplied.length!==expected.length||!timingSafeEqual(supplied,expected))return reply.code(401).send({error:'skill authentication required'});
    },
    // 카카오 서버 전체 상한(모든 유저 합계). 한 사람이 너무 빠르게 보내는 것은 아래 perUser로 따로 막습니다
    config:{rateLimit:{max:1200,timeWindow:'1 minute',keyGenerator:()=> 'kakao-skill'}},
  },async(req,reply)=>{
    try{
      const input=parseKakao(req.body,options.botId);
      if(!perUser(input.identity))return kakaoResponse({text:'⏳ 너무 빨리 보내고 있어요. 잠깐 쉬었다가 다시 보내 주세요.'});
      const result=await game.handle(input.identity,input.message,input.button);
      // 카카오 로그인 회원번호가 왔는지(값은 기록하지 않음): 오면 웹 카카오 로그인을 코드 없이 연결할 수 있음
      if(input.appUserId)void game.noteChatApp(input.identity,input.appUserId).then(fresh=>{if(fresh)req.log.info({appUserId:'present'},'kakao appUserId received');}).catch(()=>{});
      const out=kakaoResponse(result,await options.baseUrl(),stamp);
      // 진단용: 카카오에 보낸 응답 요약(말풍선 종류·크기·버튼). 명령어 첫 낱말만 남깁니다
      // (뒤의 글자에는 관리자 코드·쿠폰 코드·닉네임이 들어갈 수 있어 기록하지 않음). bot은 카카오 봇 ID(.env의 KAKAO_BOT_ID 확인용)
      req.log.info({command:input.message.trim().split(/\s+/)[0].slice(0,12),bot:input.bot,outputs:out.template.outputs.map(o=>Object.keys(o)[0]),bytes:JSON.stringify(out).length,quick:out.template.quickReplies.map(q=>q.label)},'kakao response');
      return out;
    }catch(e){
      if(e instanceof GameError){
        // 게임 안내 오류도 카드로: 첫 줄 = 제목, 나머지 = 설명
        const [title,...rest]=e.message.split('\n');
        return kakaoResponse({text:e.message,cards:{items:[{imageId:'s-oops',title,description:rest.join('\n')}]},choices:[{label:'도움말',message:'도움말'},{label:'뽑기',message:'뽑기'},{label:'내정보',message:'내정보'}]},await options.baseUrl(),stamp);
      }
      if(e instanceof ZodError)return reply.code(400).send(kakaoResponse({text:'스킬 요청의 bot.id, userRequest.user.id, utterance를 확인하세요.'}));
      if(e instanceof Error&&e.message==='BOT_MISMATCH')return reply.code(403).send({error:'bot mismatch'});
      // 사용자의 발화/ID/비밀키/요청 본문은 기록하지 않습니다.
      req.log.error({errorCode:e instanceof Error?e.name:'Unknown',reference:createHash('sha256').update(req.id).digest('hex').slice(0,12)},'game request failed');
      return kakaoResponse({text:'처리하다 문제가 생겼어요. 잠시 뒤 다시 해 주세요. 끝나지 않은 작업은 취소돼요.'});
    }
  });
  return app;
}
