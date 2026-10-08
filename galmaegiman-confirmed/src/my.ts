// 웹 「내 갈매미」(/my): 카톡에서 받은 6자리 코드로 로그인 → 내 갈매미 목록·전투 편성·탐험 편성
// 로그인 상태는 서명된 쿠키(30일). 쿠키에는 플레이어 id와 만료 시각만, 서명 키는 SKILL_SECRET에서 따로 계산
import { createHmac, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import type { GameService } from './game.js';
import { GameError } from './types.js';

const COOKIE='gmm_s',DAYS=30;
export function registerMy(app:FastifyInstance,game:GameService,opt:{root:string;secret:string}){
  const key=createHmac('sha256',opt.secret).update('web-session').digest();
  const sign=(v:string)=>createHmac('sha256',key).update(v).digest('base64url');
  const issue=(playerId:string)=>{const exp=Math.floor(Date.now()/1000)+DAYS*86400,v=`${playerId}.${exp}`;return `${v}.${sign(v)}`;};
  const session=(req:FastifyRequest):string|null=>{
    const raw=(req.headers.cookie??'').split(/;\s*/).find(c=>c.startsWith(`${COOKIE}=`))?.slice(COOKIE.length+1);
    if(!raw)return null;
    const [id,exp,sig]=raw.split('.');
    if(!id||!exp||!sig)return null;
    const want=Buffer.from(sign(`${id}.${exp}`)),got=Buffer.from(sig);
    if(want.length!==got.length||!timingSafeEqual(want,got)||Number(exp)<Date.now()/1000)return null;
    return id;
  };
  // https로 들어온 요청이면 Secure 쿠키(클라우드플레어 터널 뒤라 전달 헤더로 판단)
  const secure=(req:FastifyRequest)=>/https/.test(String(req.headers['cf-visitor']??req.headers['x-forwarded-proto']??''));
  const setCookie=(req:FastifyRequest,reply:FastifyReply,value:string,maxAge:number)=>
    reply.header('Set-Cookie',`${COOKIE}=${value}; Path=/my; Max-Age=${maxAge}; HttpOnly; SameSite=Lax${secure(req)?'; Secure':''}`);
  // 다른 사이트에서 몰래 보내는 요청 막기: 화면 스크립트가 붙이는 머리글이 있어야 함
  const guard=(req:FastifyRequest,reply:FastifyReply)=>{if(req.headers['x-gmm']!=='1'){reply.code(403).send({error:'forbidden'});return false;}return true;};
  const ipKey=(p:string)=>(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`${p}:${ip}`:p;};
  const file=async(reply:FastifyReply,type:string,path:string)=>{
    const body=await readFile(resolve(opt.root,path)).catch(()=>null);
    return body?reply.type(type).header('Cache-Control','no-cache').send(body):reply.code(404).send({error:'not found'});
  };

  app.get('/my',async(_req,reply)=>file(reply,'text/html; charset=utf-8','public/my/index.html'));
  app.get('/my/my.js',async(_req,reply)=>file(reply,'text/javascript; charset=utf-8','public/my/my.js'));
  // 로그인: 6자리 코드(같은 곳에서 1분에 10번까지)
  app.post<{Body:{code?:unknown}}>('/my/api/login',{config:{rateLimit:{max:10,timeWindow:'1 minute',keyGenerator:ipKey('mylogin')}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    const code=typeof req.body?.code==='string'?req.body.code.replace(/\s/g,''):'';
    const playerId=await game.webLoginCode(code);
    if(!playerId)return reply.code(400).send({error:'코드가 맞지 않거나 시간이 지났어요. 카톡에서 「웹 로그인」을 다시 보내 새 코드를 받아 주세요.'});
    setCookie(req,reply,issue(playerId),DAYS*86400);
    return {ok:true};
  });
  app.post('/my/api/logout',async(req,reply)=>{if(!guard(req,reply))return;setCookie(req,reply,'',0);return {ok:true};});
  const limit={config:{rateLimit:{max:120,timeWindow:'1 minute',keyGenerator:ipKey('myapi')}}};
  app.get('/my/api/me',limit,async(req,reply)=>{
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    const me=await game.webMe(id);
    return me?reply.header('Cache-Control','no-store').send(me):reply.code(401).send({error:'login'});
  });
  app.post<{Body:{ids?:unknown}}>('/my/api/battle-team',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    try{return {battleTeam:await game.webSetBattleTeam(id,req.body?.ids as string[])};}
    catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
  });
}
