// 웹 「내 갈매미」(/my): 카톡에서 받은 6자리 코드로 로그인 → 내 갈매미 목록·전투 편성·탐험 편성
// 로그인 상태는 서명된 쿠키(30일). 쿠키에는 플레이어 id와 만료 시각만, 서명 키는 SKILL_SECRET에서 따로 계산
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import type { GameService } from './game.js';
import { GameError } from './types.js';
import { loadBattleRules } from './battle/load.js';

const COOKIE='gmm_s',DAYS=30,KSTATE='gmm_ks',KPEND='gmm_kp';
export interface KakaoLogin {restKey:string;clientSecret?:string}
export function registerMy(app:FastifyInstance,game:GameService,opt:{root:string;secret:string;baseUrl:()=>Promise<string>;kakao?:KakaoLogin;fetch?:typeof fetch}){
  const key=createHmac('sha256',opt.secret).update('web-session').digest();
  const sign=(v:string)=>createHmac('sha256',key).update(v).digest('base64url');
  const issue=(playerId:string)=>{const exp=Math.floor(Date.now()/1000)+DAYS*86400,v=`${playerId}.${exp}`;return `${v}.${sign(v)}`;};
  const cookie=(req:FastifyRequest,name:string)=>(req.headers.cookie??'').split(/;\s*/).find(c=>c.startsWith(`${name}=`))?.slice(name.length+1);
  // 서명된 값 `${값}.${만료}.${서명}` 확인(쓰임새마다 다른 머리말로 서명해 서로 바꿔 쓰지 못하게)
  const signed=(prefix:string,v:string,secs:number)=>{const exp=Math.floor(Date.now()/1000)+secs,b=`${v}.${exp}`;return `${b}.${sign(prefix+b)}`;};
  const unsign=(prefix:string,raw:string|undefined):string|null=>{
    if(!raw)return null;
    const [id,exp,sig]=raw.split('.');
    if(!id||!exp||!sig)return null;
    const want=Buffer.from(sign(`${prefix}${id}.${exp}`)),got=Buffer.from(sig);
    if(want.length!==got.length||!timingSafeEqual(want,got)||Number(exp)<Date.now()/1000)return null;
    return id;
  };
  const session=(req:FastifyRequest)=>unsign('',cookie(req,COOKIE));
  // https로 들어온 요청이면 Secure 쿠키(클라우드플레어 터널 뒤라 전달 헤더로 판단)
  const secure=(req:FastifyRequest)=>/https/.test(String(req.headers['cf-visitor']??req.headers['x-forwarded-proto']??''));
  const cookieLine=(req:FastifyRequest,name:string,value:string,maxAge:number)=>`${name}=${value}; Path=/my; Max-Age=${maxAge}; HttpOnly; SameSite=Lax${secure(req)?'; Secure':''}`;
  const setCookie=(req:FastifyRequest,reply:FastifyReply,value:string,maxAge:number)=>reply.header('Set-Cookie',cookieLine(req,COOKIE,value,maxAge));
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
    // 카카오로 로그인하다 넘어온 경우: 이번 로그인한 계정에 그 카카오 계정을 연결
    const kakaoId=unsign('kp.',cookie(req,KPEND));
    let kakao:string|undefined;
    if(kakaoId){
      try{await game.webLinkKakao(playerId,kakaoId);kakao='linked';}
      catch(e){if(!(e instanceof GameError))throw e;kakao=e.message;}
    }
    reply.header('Set-Cookie',[cookieLine(req,COOKIE,issue(playerId),DAYS*86400),...(kakaoId?[cookieLine(req,KPEND,'',0)]:[])]);
    return {ok:true,kakao};
  });
  app.post('/my/api/logout',async(req,reply)=>{if(!guard(req,reply))return;setCookie(req,reply,'',0);return {ok:true};});
  const limit={config:{rateLimit:{max:120,timeWindow:'1 minute',keyGenerator:ipKey('myapi')}}};
  // 아이템 종류·가격(battle.yaml을 요청 때마다 읽어 고치면 바로 반영)
  const itemDefs=()=>{const R=loadBattleRules(opt.root);return {items:Object.entries(R.data.items).map(([id,v])=>({id,...v})),rules:R.data.itemRules};};
  app.get('/my/api/me',limit,async(req,reply)=>{
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login',kakao:!!opt.kakao,linking:!!unsign('kp.',cookie(req,KPEND))});
    const me=await game.webMe(id);
    if(!me)return reply.code(401).send({error:'login',kakao:!!opt.kakao});
    const defs=itemDefs();
    return reply.header('Cache-Control','no-store').send({...me,kakaoLogin:!!opt.kakao,items:await game.webItems(id),itemDefs:defs.items,itemRules:defs.rules,prices:game.webPrices()});
  });
  // 로그인한 사람의 요청을 실행하고, 게임 안내 오류는 그대로 보여 줌
  const act=<B,>(path:string,run:(id:string,body:B)=>Promise<unknown>)=>app.post<{Body:B}>(path,limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    try{return {ok:true,result:await run(id,(req.body??{}) as B)};}
    catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
  });
  act<{ids?:unknown}>('/my/api/party',(id,b)=>game.webSetParty(id,b.ids as string[]));
  act('/my/api/expedition/start',id=>game.webStartExpedition(id));
  act('/my/api/expedition/claim',id=>game.webClaimExpedition(id));
  act<{item?:unknown}>('/my/api/shop',(id,b)=>{
    if(b.item!=='tickets'&&b.item!=='peanutTickets'&&b.item!=='skip')throw new GameError('없는 물건이에요.');
    return game.webShop(id,b.item);
  });
  act<{id?:unknown;count?:unknown}>('/my/api/items/buy',(id,b)=>{
    const defs=itemDefs(),item=defs.items.find(x=>x.id===b.id);
    if(!item)throw new GameError('없는 아이템이에요.');
    return game.webBuyItem(id,{id:item.id,name:item.name,price:item.price},Number(b.count??1),defs.rules.maxStack);
  });
  app.post<{Body:{ids?:unknown}}>('/my/api/battle-team',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    try{return {battleTeam:await game.webSetBattleTeam(id,req.body?.ids as string[])};}
    catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
  });

  act('/my/api/kakao/unlink',id=>game.webLinkKakao(id,null));

  // ── 카카오 로그인(KAKAO_REST_KEY가 있을 때만) ──
  // 처음: 카카오 → 연결된 계정 없음 → 카톡 코드로 한 번 로그인하면 그 계정에 연결. 다음부터는 카카오만 누르면 로그인
  const kakao=opt.kakao,http=opt.fetch??fetch;
  const origin=async(req:FastifyRequest)=>(await opt.baseUrl())||`${secure(req)?'https':'http'}://${req.headers.host}`;
  const back=(reply:FastifyReply,q:string)=>reply.redirect(`/my${q}`);
  app.get('/my/kakao',async(req,reply)=>{
    if(!kakao)return reply.code(404).send({error:'not found'});
    const state=randomBytes(16).toString('base64url');
    const u=new URL('https://kauth.kakao.com/oauth/authorize');
    u.search=new URLSearchParams({client_id:kakao.restKey,redirect_uri:`${await origin(req)}/my/kakao/callback`,response_type:'code',state}).toString();
    reply.header('Set-Cookie',cookieLine(req,KSTATE,state,600));
    return reply.redirect(u.toString());
  });
  app.get<{Querystring:{code?:string;state?:string;error?:string}}>('/my/kakao/callback',{config:{rateLimit:{max:20,timeWindow:'1 minute',keyGenerator:ipKey('mykakao')}}},async(req,reply)=>{
    if(!kakao)return reply.code(404).send({error:'not found'});
    const state=cookie(req,KSTATE),clear=cookieLine(req,KSTATE,'',0);
    if(req.query.error)return back(reply.header('Set-Cookie',clear),'?kakao=cancel');
    if(!state||state!==req.query.state||typeof req.query.code!=='string')return back(reply.header('Set-Cookie',clear),'?kakao=fail');
    let kakaoId:string;
    try{
      const tok=await http('https://kauth.kakao.com/oauth/token',{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded;charset=utf-8'},
        body:new URLSearchParams({grant_type:'authorization_code',client_id:kakao.restKey,redirect_uri:`${await origin(req)}/my/kakao/callback`,code:req.query.code,...(kakao.clientSecret?{client_secret:kakao.clientSecret}:{})})});
      const t=await tok.json() as {access_token?:string};
      if(!tok.ok||!t.access_token)throw new Error(`token ${tok.status}`);
      const meRes=await http('https://kapi.kakao.com/v2/user/me',{headers:{authorization:`Bearer ${t.access_token}`}});
      const u=await meRes.json() as {id?:number|string};
      if(!meRes.ok||u.id==null||!/^\d+$/.test(String(u.id)))throw new Error(`me ${meRes.status}`);
      kakaoId=String(u.id);
    }catch(e){req.log.warn({err:String(e)},'kakao login failed');return back(reply.header('Set-Cookie',clear),'?kakao=fail');}
    const linked=await game.webKakaoPlayer(kakaoId);
    if(linked)return back(reply.header('Set-Cookie',[clear,cookieLine(req,COOKIE,issue(linked),DAYS*86400)]),'');
    const now=session(req);
    if(now){   // 이미 코드로 로그인한 상태에서 「카카오 연결」
      try{await game.webLinkKakao(now,kakaoId);return back(reply.header('Set-Cookie',clear),'?kakao=linked');}
      catch(e){if(e instanceof GameError)return back(reply.header('Set-Cookie',clear),'?kakao=taken');throw e;}
    }
    return back(reply.header('Set-Cookie',[clear,cookieLine(req,KPEND,signed('kp.',kakaoId,600),600)]),'?kakao=link');
  });
}
