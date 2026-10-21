// 웹 「내 갈매미」(/my): 카톡에서 받은 6자리 코드로 로그인 → 내 갈매미 목록·전투 편성·탐험 편성
// 로그인 상태는 서명된 쿠키(30일). 쿠키에는 플레이어 id와 만료 시각만, 서명 키는 SKILL_SECRET에서 따로 계산
import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import type { GameService } from './game.js';
import { GameError } from './types.js';
import { loadBattleRules } from './battle/load.js';
import { duelKey } from './duel.js';
import {ReviewAuth,reviewRouteAllowed,REVIEW_ERROR,type ReviewConfig} from './review-auth.js';
import {registerNative} from './native.js';
import type {PrismaClient} from '@prisma/client';
import {RealtimePvpService} from './pvp/service.js';
import {registerRealtimePvp} from './pvp/routes.js';

const COOKIE='gmm_s',DAYS=30,KSTATE='gmm_ks',KPEND='gmm_kp',KAPP='gmm_ka',KLINK='gmm_kl';
// 안드로이드 앱(Capacitor) 화면 주소. 앱에서 오는 요청만 다른 주소에서 받음(CORS)
const APP_ORIGINS=new Set(['https://localhost','capacitor://localhost','http://localhost']);
export interface KakaoLogin {restKey:string;clientSecret?:string}
export function registerMy(app:FastifyInstance,game:GameService,opt:{db:PrismaClient;root:string;secret:string;baseUrl:()=>Promise<string>;kakao?:KakaoLogin;fetch?:typeof fetch;reviewLogin?:ReviewConfig}){
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
  // 웹은 쿠키, 앱은 머리글(x-gmm-s)에 같은 서명 값
  const review=new ReviewAuth(opt.db,opt.reviewLogin);
  const checked=new WeakMap<FastifyRequest,string>();
  const rawSession=(req:FastifyRequest)=>{const h=req.headers['x-gmm-s'];return typeof h==='string'&&h.length<200?h:cookie(req,COOKIE);};
  const session=(req:FastifyRequest)=>checked.get(req)??null;
  app.addHook('onClose',async()=>review.close());
  app.addHook('preHandler',async(req,reply)=>{
    // 자격증명 교환/폐기만 기존 세션 없이 처리. guard와 로그인 제한은 각 handler에서 유지.
    const path=req.url.split('?')[0];
    if(req.method==='POST'&&(path==='/my/api/review/login'||path==='/my/api/review/exchange'||path==='/my/api/logout'))return;
    if(path==='/review')return;   // 심사용 로그인 페이지: 기존 브라우저 쿠키와 무관하게 동작
    const raw=rawSession(req);if(!raw)return;
    if(raw.startsWith('rv_')){
      const id=await review.authenticate(raw);
      if(!id){setCookie(req,reply,'',0);return reply.code(401).send({error:'login'});}
      const path=req.url.split('?')[0].replace(/^\/my\/api\//,'');
      if(!reviewRouteAllowed(req.method,path,req.body))return reply.code(403).send({error:'심사 계정에서는 계정 연결·삭제와 다른 이용자에게 영향을 주는 기능을 사용할 수 없어요.'});
      checked.set(req,id);return;
    }
    const id=unsign('',raw);if(!id)return;
    // 과거 REVIEW_CODE로 발급한 30일 쿠키도 차단. 전용 계정은 전용 세션으로만 접근.
    const p=await opt.db.player.findUnique({where:{id},select:{identity:true}});
    if(p&&!p.identity.startsWith('review:'))checked.set(req,id);
  });
  app.addHook('onRequest',async(req,reply)=>{
    if(!req.url.startsWith('/my/api/')&&!req.url.startsWith('/duel/data.json')&&!req.url.startsWith('/app/apk.json'))return;   // 게임 데이터·새 앱 버전 정보도 앱에서 받음
    const o=req.headers.origin;
    if(typeof o!=='string'||!APP_ORIGINS.has(o))return;
    reply.header('Access-Control-Allow-Origin',o).header('Vary','Origin')
      .header('Access-Control-Allow-Headers','content-type,x-gmm,x-gmm-s,x-gmm-app').header('Access-Control-Allow-Methods','GET,POST').header('Access-Control-Max-Age','600');
    if(req.method==='OPTIONS')return reply.code(204).send();
  });
  // 앱 카카오 로그인: 브라우저에서 로그인 → 앱으로 한 번 쓰는 코드만 보냄 → 앱이 처음 만든 비밀값(verifier)으로 토큰 교환(PKCE)
  const appCodes=new Map<string,{player:string;challenge:string;exp:number;kakao?:string;error?:string}>();
  const appLinkStarts=new Map<string,{player:string;challenge:string;exp:number}>();
  const appLinkPending=new Map<string,{kakao:string;exp:number}>();
  // https로 들어온 요청이면 Secure 쿠키(클라우드플레어 터널 뒤라 전달 헤더로 판단)
  const secure=(req:FastifyRequest)=>/https/.test(String(req.headers['cf-visitor']??req.headers['x-forwarded-proto']??''));
  const cookieLine=(req:FastifyRequest,name:string,value:string,maxAge:number)=>`${name}=${value}; Path=/my; Max-Age=${maxAge}; HttpOnly; SameSite=Lax${secure(req)?'; Secure':''}`;
  const setCookie=(req:FastifyRequest,reply:FastifyReply,value:string,maxAge:number)=>reply.header('Set-Cookie',cookieLine(req,COOKIE,value,maxAge));
  // 다른 사이트에서 몰래 보내는 요청 막기: 화면 스크립트가 붙이는 머리글이 있어야 함
  const guard=(req:FastifyRequest,reply:FastifyReply)=>{if(req.headers['x-gmm']!=='1'){reply.code(403).send({error:'forbidden'});return false;}return true;};
  const realtime=new RealtimePvpService(opt.db,game.content,opt.root,opt.secret);
  const ipKey=(p:string)=>(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`${p}:${ip}`:p;};
  const file=async(reply:FastifyReply,type:string,path:string)=>{
    const body=await readFile(resolve(opt.root,path)).catch(()=>null);
    return body?reply.type(type).header('Cache-Control','no-cache').send(body):reply.code(404).send({error:'not found'});
  };

  // 웹은 새 화면(공개 사이트·/game)으로 이어 보내는 작은 페이지(public/site/my.html). 앱 게임 창(?g=1)·대결 다음(next=duel)은 기존 화면
  app.get<{Querystring:{g?:string;next?:string}}>('/my',async(req,reply)=>file(reply,'text/html; charset=utf-8',req.query.g==='1'||req.query.next==='duel'?'public/my/index.html':'public/site/my.html'));
  app.get('/my/classic',async(_req,reply)=>file(reply,'text/html; charset=utf-8','public/my/index.html'));
  app.get('/my/dash.js',async(_req,reply)=>file(reply,'text/javascript; charset=utf-8','public/web/dash.js'));
  // 웹 그림(ChatGPT가 만든 것, public/web/img): 없으면 화면이 크기 정해진 빈 칸을 그림
  app.get<{Params:{file:string}}>('/web/img/:file',async(req,reply)=>{
    if(!/^[a-z0-9][a-z0-9-]*\.(webp|png|jpg)$/i.test(req.params.file))return reply.code(404).send({error:'not found'});
    const ext=req.params.file.split('.').pop()!.toLowerCase();
    return file(reply,ext==='jpg'?'image/jpeg':`image/${ext}`,`public/web/img/${req.params.file}`);
  });
  app.get('/my/my.js',async(_req,reply)=>file(reply,'text/javascript; charset=utf-8','public/my/my.js'));
  app.get('/image-state.js',async(_req,reply)=>file(reply,'text/javascript; charset=utf-8','public/image-state.js'));
  // 로그인: 6자리 코드(같은 곳에서 1분에 10번까지)
  app.post<{Body:{code?:unknown;linkCode?:unknown}}>('/my/api/login',{config:{rateLimit:{max:10,timeWindow:'1 minute',keyGenerator:ipKey('mylogin')}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    const code=typeof req.body?.code==='string'?req.body.code.replace(/\s/g,''):'';
    const linkCode=typeof req.body?.linkCode==='string'?req.body.linkCode:'';
    const nativeLink=linkCode?appLinkPending.get(linkCode):null;
    if(linkCode&&(!nativeLink||nativeLink.exp<Date.now()))return reply.code(400).send({error:'카카오 연결 요청이 만료됐어요. 카카오 로그인을 다시 시작해 주세요.'});
    const playerId=await game.webLoginCode(code);
    if(!playerId)return reply.code(400).send({error:'코드가 맞지 않거나 시간이 지났어요. 카톡에서 「웹 로그인」을 다시 보내 새 코드를 받아 주세요.'});
    // 카카오로 로그인하다 넘어온 경우: 이번 로그인한 계정에 그 카카오 계정을 연결
    const kakaoId=unsign('kp.',cookie(req,KPEND));
    let kakao:string|undefined;
    if(nativeLink){
      if(appLinkPending.get(linkCode)!==nativeLink)return reply.code(400).send({error:'이미 처리된 카카오 연결 요청이에요.'});
      appLinkPending.delete(linkCode);
      try{await game.webLinkKakao(playerId,nativeLink.kakao);kakao='linked';}
      catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
    }
    if(kakaoId){
      try{await game.webLinkKakao(playerId,kakaoId);kakao='linked';}
      catch(e){if(!(e instanceof GameError))throw e;kakao=e.message;}
    }
    const token=issue(playerId);
    reply.header('Set-Cookie',[cookieLine(req,COOKIE,token,DAYS*86400),...(kakaoId?[cookieLine(req,KPEND,'',0)]:[])]);
    return {ok:true,kakao,...(req.headers['x-gmm-app']==='1'?{token}:{})};
  });
  app.post<{Body:{username?:unknown;password?:unknown}}>('/my/api/review/login',{bodyLimit:2048,config:{rateLimit:{max:5,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>`review-login:${req.ip}`,errorResponseBuilder:()=>({statusCode:429,error:REVIEW_ERROR})}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    reply.header('Cache-Control','no-store');
    const token=await review.login(req.body?.username,req.body?.password,req.ip);
    if(!token){if(rawSession(req)?.startsWith('rv_')){review.revoke(rawSession(req)!);setCookie(req,reply,'',0);}return reply.code(400).send({error:REVIEW_ERROR});}
    review.revoke(rawSession(req)??'');
    setCookie(req,reply,token,12*3600);
    return {ok:true,...(req.headers['x-gmm-app']==='1'?{token}:{})};
  });
  // ── Play 심사용 로그인 페이지 ──
  // 심사자에게 이 주소만 전달한다. 아이디·비밀번호 확인 후 2분짜리 일회용 코드로 앱(gmmrevised://auth?review=…)을 연다.
  // 앱은 코드를 심사 세션으로 바꾼다. 앱 화면 배치는 바꾸지 않는다. 비밀번호는 응답·로그에 남기지 않는다.
  const reviewCodes=new Map<string,{token:string;exp:number}>();
  const reviewPage=(body:string)=>`<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>Gmm review login</title>
<style>body{font-family:system-ui,sans-serif;background:#f5efdc;color:#26323a;margin:0;padding:24px;display:flex;justify-content:center}main{max-width:420px;width:100%}h1{font-size:22px}label{display:block;margin:14px 0 6px;font-weight:600}input{width:100%;box-sizing:border-box;font-size:18px;padding:12px;border:1px solid #8a7a5a;border-radius:8px;background:#fffaf0}button,a.btn{display:block;font:inherit;width:100%;box-sizing:border-box;margin-top:18px;padding:14px;font-size:18px;font-weight:700;text-align:center;border:0;border-radius:10px;background:#26323a;color:#fff8e8;text-decoration:none}p{line-height:1.5}.err{color:#a3332a}</style></head><body><main>${body}</main></body></html>`;
  const reviewForm=(error='')=>reviewPage(`<h1>갈매미맨 심사 계정 로그인<br>Gmm review login</h1>${error?`<p class="err">${error}</p>`:''}
<form method="post" action="/review" autocomplete="off"><label for="u">Username</label><input id="u" name="username" autocapitalize="none" autocomplete="off" spellcheck="false" maxlength="80" required>
<label for="p">Password</label><input id="p" name="password" type="password" autocomplete="off" maxlength="256" required>
<button type="submit">로그인 후 앱 열기 / Sign in and open the app</button></form><p>Install the app from Google Play first. 앱을 먼저 설치해 주세요.</p>`);
  app.get('/review',async(_req,reply)=>reply.type('text/html; charset=utf-8').header('Cache-Control','no-store').header('X-Robots-Tag','noindex').header('Referrer-Policy','no-referrer').send(reviewForm()));
  app.register(async sub=>{
    // 이 페이지의 HTML 폼만 받는다(다른 경로에는 영향 없음).
    sub.addContentTypeParser('application/x-www-form-urlencoded',{parseAs:'string',bodyLimit:2048},(_req,body,done)=>{try{done(null,Object.fromEntries(new URLSearchParams(String(body))));}catch(e){done(e as Error,undefined);}});
    sub.post<{Body:{username?:unknown;password?:unknown}}>('/review',{bodyLimit:2048,config:{rateLimit:{max:5,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>`review-login:${req.ip}`,errorResponseBuilder:()=>({statusCode:429,error:REVIEW_ERROR})}}},async(req,reply)=>{
      reply.type('text/html; charset=utf-8').header('Cache-Control','no-store').header('X-Robots-Tag','noindex').header('Referrer-Policy','no-referrer');
      const token=await review.login(req.body?.username,req.body?.password,req.ip);
      if(!token)return reply.code(400).send(reviewForm('로그인 정보를 확인해 주세요. / Check the username and password.'));
      const now=Date.now();
      for(const [k,v] of reviewCodes)if(v.exp<=now){review.revoke(v.token);reviewCodes.delete(k);}
      if(reviewCodes.size>=20){const [k,v]=reviewCodes.entries().next().value!;review.revoke(v.token);reviewCodes.delete(k);}
      const code=randomBytes(24).toString('base64url');reviewCodes.set(code,{token,exp:now+120_000});
      // Play 출시 앱(org.gmm)을 직접 연다. 다른 브라우저용으로 기존 앱 복귀 주소도 둔다.
      const intent=`intent://auth?review=${code}#Intent;scheme=gmmrevised;package=org.gmm;end`,plain=`gmmrevised://auth?review=${code}`;
      return reviewPage(`<h1>로그인 완료 / Signed in</h1><p>앱이 열리지 않으면 아래 버튼을 눌러 주세요(2분 안에). If the app does not open, tap the button within 2 minutes.</p>
<a class="btn" id="open" href="${intent}">앱에서 열기 / Open the app</a><a class="btn" href="${plain}" style="background:#596778">다른 방법으로 열기 / Open another way</a>
<script>location.href=${JSON.stringify(intent)};</script>`);
    });
  });
  app.post<{Body:{code?:unknown}}>('/my/api/review/exchange',{bodyLimit:1024,config:{rateLimit:{max:10,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>`review-exchange:${req.ip}`}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    reply.header('Cache-Control','no-store');
    const code=typeof req.body?.code==='string'?req.body.code:'',e=reviewCodes.get(code);
    reviewCodes.delete(code);   // 한 번만
    if(!e||e.exp<=Date.now()||!await review.authenticate(e.token))return reply.code(400).send({error:'심사 로그인 페이지에서 다시 로그인해 주세요.'});
    return {ok:true,token:e.token};
  });
  app.post('/my/api/logout',async(req,reply)=>{if(!guard(req,reply))return;review.revoke(rawSession(req)??'');setCookie(req,reply,'',0);return {ok:true};});
  const limit={config:{rateLimit:{max:120,timeWindow:'1 minute',keyGenerator:ipKey('myapi')}}};
  // 아이템 종류·가격(battle.yaml을 요청 때마다 읽어 고치면 바로 반영)
  const itemDefs=()=>{const R=loadBattleRules(opt.root);return {items:Object.entries(R.data.items).map(([id,v])=>({id,...v})),rules:R.data.itemRules};};
  // 홈페이지 메뉴용: 로그인돼 있으면 닉네임만(없으면 401). 홈페이지는 모두에게 같은 화면이라 화면에서 물어봄
  app.get('/my/api/who',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    const who=id?await game.webWho(id):null;
    if(!who)return reply.code(401).header('Cache-Control','no-store').send({error:'login'});
    return reply.header('Cache-Control','no-store').send(who);
  });
  app.get('/my/api/me',limit,async(req,reply)=>{
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login',kakao:!!opt.kakao,linking:!!unsign('kp.',cookie(req,KPEND))});
    const me=await game.webMe(id);
    if(!me)return reply.code(401).send({error:'login',kakao:!!opt.kakao});
    const defs=itemDefs();
    // 최상위 갈매미는 속성도(v0.14.9, 편성 덱 짜기용): attr 코드 + 이름
    const BR=loadBattleRules(opt.root);
    const units=me.units.map(u=>{const d=u.top?BR.unitDef(u.id):undefined;return d?{...u,attr:d.attr,attrName:BR.data.attributes[d.attr]?.name??d.attr}:u;});
    const typeChart=Object.fromEntries(Object.entries(BR.data.chart).map(([a,c])=>[a,{name:BR.data.attributes[a as keyof typeof BR.data.attributes]?.name??a,strong:c.strong,why:c.why}]));   // 속성 상성표(v0.15.2, 편성 옆)
    return reply.header('Cache-Control','no-store').send({...me,units,typeChart,kakaoLogin:!!opt.kakao,items:await game.webItems(id),itemDefs:defs.items,itemRules:defs.rules,prices:game.webPrices()});
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
  act<{id?:unknown;locked?:unknown}>('/my/api/units/protect',(id,b)=>{
    if(typeof b.id!=='string'||typeof b.locked!=='boolean')throw new GameError('갈매미와 보호 상태를 확인해 주세요.');
    return game.webProtect(id,b.id,b.locked);
  });
  act('/my/api/expedition/start',id=>game.webStartExpedition(id));
  act('/my/api/expedition/claim',id=>game.webClaimExpedition(id));
  act<{item?:unknown;count?:unknown}>('/my/api/shop',(id,b)=>{
    if(b.item!=='tickets'&&b.item!=='peanutTickets'&&b.item!=='skip')throw new GameError('없는 물건이에요.');
    return game.webShop(id,b.item,Number(b.count??1));
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

  // 조합(카톡 「조합」과 같은 처리)
  app.get('/my/api/craft',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send(await game.webCraftInfo(id));
  });
  act<{id?:unknown;times?:unknown}>('/my/api/craft',(id,b)=>game.webCraft(id,String(b.id??''),Number(b.times??1)));
  act<{ids?:unknown}>('/my/api/mix',(id,b)=>game.webMix(id,b.ids as string[]));
  // 뽑기
  app.get('/my/api/draw',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    const info=await game.webDrawInfo(id);
    if(!info)return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send(info);
  });
  act('/my/api/claim',id=>game.webClaim(id));
  app.get('/my/api/daily',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send(await game.webDaily(id));
  });
  act('/my/api/attend',id=>game.webAttend(id));
  // 쿠폰 입력: 코드 맞히기를 막으려고 따로 1분 10번까지
  app.post<{Body:{code?:unknown}}>('/my/api/coupon',{config:{rateLimit:{max:10,timeWindow:'1 minute',keyGenerator:ipKey('mycoupon')}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    try{return {ok:true,result:await game.webCoupon(id,typeof req.body?.code==='string'?req.body.code:'')};}
    catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
  });
  act<{kind?:unknown;times?:unknown}>('/my/api/draw',(id,b)=>game.webDraw(id,String(b.kind??''),Number(b.times??1)));
  // 계정 삭제(탈퇴): 「탈퇴」를 직접 적어야 실행. 지우고 나면 로그인 쿠키도 지움
  app.post<{Body:{confirm?:unknown}}>('/my/api/account/delete',{config:{rateLimit:{max:5,timeWindow:'1 minute',keyGenerator:ipKey('mydel')}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    if(req.body?.confirm!=='탈퇴')return reply.code(400).send({error:'확인 칸에 「탈퇴」를 적어 주세요.'});
    try{await game.webDeleteAccount(id);}catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
    req.log.info('account deleted');
    setCookie(req,reply,'',0);
    return {ok:true};
  });
  app.post<{Body:{code?:unknown;verifier?:unknown}}>('/my/api/app/exchange',{config:{rateLimit:{max:10,timeWindow:'1 minute',keyGenerator:ipKey('myapp')}}},async(req,reply)=>{
    if(!guard(req,reply))return;
    const code=typeof req.body?.code==='string'?req.body.code:'',verifier=typeof req.body?.verifier==='string'?req.body.verifier:'';
    const e=appCodes.get(code);
    appCodes.delete(code);   // 한 번만
    if(!e||e.exp<Date.now()||verifier.length<43||verifier.length>128)return reply.code(400).send({error:'로그인을 다시 해 주세요.'});
    const want=Buffer.from(e.challenge),got=Buffer.from(createHash('sha256').update(verifier).digest('base64url'));
    if(want.length!==got.length||!timingSafeEqual(want,got))return reply.code(400).send({error:'로그인을 다시 해 주세요.'});
    if(e.error)return reply.code(400).send({error:e.error});
    if(!e.player&&e.kakao){
      for(const [k,v] of appLinkPending)if(v.exp<Date.now())appLinkPending.delete(k);
      const linkCode=randomBytes(18).toString('base64url');appLinkPending.set(linkCode,{kakao:e.kakao,exp:Date.now()+600_000});
      return {needsLink:true,linkCode};
    }
    return {token:issue(e.player)};
  });
  // 「갈매미맨 수비대」(대결 게임) 열쇠: 로그인한 사람만(최상위 그림·이름이 나오는 게임이라 공개하지 않음)
  app.get('/my/api/duel-key',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    if(!session(req))return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send({k:duelKey(opt.secret)});
  });
  act('/my/api/kakao/unlink',id=>game.webLinkKakao(id,null));

  // ── 수비대 대결 스토리: 진행 저장(계정), 대결 중 아이템 ──
  // 진행은 브라우저가 계산해 보냄(스토리는 혼자 하는 모드라 재화 보상 없음). 모양·범위만 검사
  const storyShape=(R:ReturnType<typeof loadBattleRules>)=>{
    const unit=z.object({level:z.number().int().min(1).max(R.data.growth.maxLevel),xp:z.number().int().min(0).max(1e9),
      learned:z.array(z.string().max(30)).max(R.data.learn.slots+1).optional(),learnUsed:z.number().int().min(0).max(R.data.learn.levels.length).optional(),learnPick:z.number().int().min(0).max(4).optional(),
      stage:z.number().int().min(0).max(3).optional(),evoAugs:z.array(z.string().max(30)).max(3).optional()}).strict();
    // 열쇠: 종류 id(시험판·로그인 전, 예전 저장) 또는 개체 o번호(v0.16.2)
    const key=z.string().regex(/^([DTEI]\d{1,2}|o\d{1,10})$/);
    return z.object({units:z.record(key,unit).refine(u=>Object.keys(u).every(id=>id.startsWith('o')||R.units.has(id)),'없는 갈매미').refine(u=>Object.keys(u).length<=500,'너무 많아요'),
      team:z.array(key).max(3),cleared:z.array(z.string().max(12)).max(300),
      hp:z.record(key,z.object({f:z.number().min(0).max(1),at:z.number().int().min(0)}).strict()).optional()}).strict();   // 스토리 체력(v0.14.7): 전투 뒤 남은 비율과 그 시각(시간이 지나면 클라이언트가 회복 계산)
  };
  app.get('/my/api/story',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    const me=await game.webMe(id);
    if(!me)return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send({data:await game.webStory(id),nickname:me.nickname,battleTeam:me.battleTeam,
      owned:me.units.filter(u=>u.top).map(u=>u.id),mine:await game.webStoryUnits(id),items:await game.webItems(id)});
  });
  act<{data?:unknown}>('/my/api/story',async(id,b)=>{
    if(native.hasActiveStory(id))throw new GameError('진행 중인 네이티브 전투를 마친 뒤 진행을 변경해 주세요.');
    if(b.data===null)return game.webSaveStory(id,null);   // 처음부터 다시
    if(JSON.stringify(b.data??null).length>64_000)throw new GameError('저장할 내용이 너무 커요.');
    const r=storyShape(loadBattleRules(opt.root)).safeParse(b.data);
    if(!r.success)throw new GameError('스토리 진행 모양이 맞지 않아요.');
    return game.webSaveStory(id,r.data);
  });
  // PvP(일반·랭크): 내 랭크·순위표 / 대전 시작(상대·시드는 서버가) / 결과(기록을 서버가 다시 돌려 승패 확인)
  app.get('/my/api/pvp',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    try{return reply.header('Cache-Control','no-store').send(await game.webPvp(id));}
    catch(e){if(e instanceof GameError)return reply.code(400).send({error:e.message});throw e;}
  });
  act<{mode?:unknown}>('/my/api/pvp/match',(id,b)=>realtime.legacyWebStart(id,()=>game.webPvpMatch(id,String(b.mode??''))));
  const pvpEvent=z.union([z.object({r:z.number().int().min(0).max(2)}).strict(),
    z.object({c:z.object({augment:z.string().max(30).optional(),switchTo:z.number().int().min(0).max(2).optional()}).strict()}).strict(),
    z.object({a:z.union([z.object({kind:z.literal('skill'),skillId:z.string().max(40)}).strict(),
      z.object({kind:z.literal('switch'),to:z.number().int().min(0).max(2),then:z.string().max(40).optional()}).strict()])}).strict()]);
  act<{match?:unknown;log?:unknown}>('/my/api/pvp/result',async(id,b)=>{
    const log=z.array(pvpEvent).max(600).safeParse(b.log);
    if(!log.success||typeof b.match!=='string')throw new GameError('대전 기록이 맞지 않아요.');
    return realtime.legacyWebResult(id,b.match,()=>game.webPvpResult(id,loadBattleRules(opt.root),b.match as string,log.data));
  });
  // 대결 하나마다 표(서버 메모리, 2시간): 대결 한 번에 쓸 수 있는 아이템 수를 서버에서도 셈
  const fights=new Map<string,{player:string;used:number;exp:number}>();
  act('/my/api/story/battle',async id=>{
    const now=Date.now();
    for(const [k,v] of fights)if(v.exp<now)fights.delete(k);
    if(fights.size>20_000)fights.clear();
    const token=randomBytes(12).toString('base64url');
    fights.set(token,{player:id,used:0,exp:now+2*3600_000});
    return {battle:token,items:await game.webItems(id)};
  });
  // 전투 밖에서 회복 아이템 쓰기(v0.14.7, 스토리 체력): 체력 회복(heal)이 있는 아이템만, 대결당 개수 제한 없음. 회복은 클라이언트 스토리 저장에 반영
  act<{id?:unknown}>('/my/api/items/use-field',async(id,b)=>{
    if(native.hasActiveStory(id))throw new GameError('진행 중인 네이티브 전투에서 아이템을 사용해 주세요.');
    const item=itemDefs().items.find(x=>x.id===b.id);
    if(!item)throw new GameError('없는 아이템이에요.');
    if(!item.heal)throw new GameError('이 아이템은 대결 중에만 쓸 수 있어요.');
    const left=await game.webUseItem(id,{id:item.id,name:item.name});
    return {left,heal:item.heal};
  });
  // 게임 튜토리얼(v0.15.0): 단계 읽기 / 앞으로 넘기기(9 = 끝·건너뛰기) · 처음 보는 화면 안내 본 것 기록
  app.get('/my/api/tutorial',limit,async(req,reply)=>{
    if(!guard(req,reply))return;
    const id=session(req);
    if(!id)return reply.code(401).send({error:'login'});
    return reply.header('Cache-Control','no-store').send(await game.webTutorial(id));
  });
  act<{step?:unknown;seen?:unknown}>('/my/api/tutorial',(id,b)=>game.webTutorialSet(id,Number(b.step),b.seen===undefined?undefined:String(b.seen)));
  act<{battle?:unknown;id?:unknown}>('/my/api/items/use',async(id,b)=>{
    if(native.hasActiveStory(id))throw new GameError('진행 중인 네이티브 전투에서 아이템을 사용해 주세요.');
    const f=typeof b.battle==='string'?fights.get(b.battle):undefined;
    if(!f||f.player!==id||f.exp<Date.now())throw new GameError('대결 정보가 없어요. 대결을 다시 시작해 주세요.');
    const defs=itemDefs(),item=defs.items.find(x=>x.id===b.id);
    if(!item)throw new GameError('없는 아이템이에요.');
    if(f.used>=defs.rules.perBattle)throw new GameError(`아이템은 대결 한 번에 ${defs.rules.perBattle}개까지예요.`);
    const left=await game.webUseItem(id,{id:item.id,name:item.name});
    f.used++;
    return {left};
  });

  const native=registerNative(app,game,opt.root,session,guard,realtime);
  registerRealtimePvp(app,realtime,session,guard);

  // ── 카카오 로그인(KAKAO_REST_KEY가 있을 때만) ──
  // 처음: 카카오 → 연결된 계정 없음 → 카톡 코드로 한 번 로그인하면 그 계정에 연결. 다음부터는 카카오만 누르면 로그인
  act<{challenge?:unknown;app?:unknown}>('/my/api/kakao/link-start',async(id,b)=>{
    if(!opt.kakao)throw new GameError('카카오 로그인을 사용할 수 없어요.');
    if(typeof b.challenge!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(b.challenge))throw new GameError('연결 요청을 다시 시작해 주세요.');
    for(const [k,v] of appLinkStarts)if(v.exp<Date.now())appLinkStarts.delete(k);
    if(appLinkStarts.size>=20_000)throw new GameError('잠시 뒤 다시 시도해 주세요.');
    const ticket=randomBytes(18).toString('base64url');appLinkStarts.set(ticket,{player:id,challenge:b.challenge,exp:Date.now()+600_000});
    return {url:`/my/kakao?app=${b.app==='3'?'3':'2'}&c=${b.challenge}&link=${ticket}`};
  });
  const kakao=opt.kakao,http=opt.fetch??fetch;
  const origin=async(req:FastifyRequest)=>(await opt.baseUrl())||`${secure(req)?'https':'http'}://${req.headers.host}`;
  const back=(reply:FastifyReply,q:string)=>reply.redirect(`/my${q}`);
  const kakaoBack=(req:FastifyRequest,reply:FastifyReply,error:string)=>{
    const appC=cookie(req,KAPP);
    if(appC?.startsWith('native:')||appC?.startsWith('revised:'))return reply.header('Set-Cookie',[cookieLine(req,KSTATE,'',0),cookieLine(req,KAPP,'',0),cookieLine(req,KLINK,'',0)]).redirect(`${appC?.startsWith('revised:')?'gmmrevised':'gmmnative'}://auth?error=${error}`);
    return back(reply,`?kakao=${error}`);
  };
  app.get('/my/kakao',async(req,reply)=>{
    if(!kakao)return reply.code(404).send({error:'not found'});
    const state=randomBytes(16).toString('base64url');
    const q=req.query as {app?:string;c?:string;link?:string};
    let linkTarget='';
    if(q.link!==undefined){
      const ticket=typeof q.link==='string'?q.link:'';const pending=appLinkStarts.get(ticket);appLinkStarts.delete(ticket);
      if(!pending||pending.exp<Date.now()||(q.app!=='2'&&q.app!=='3')||q.c!==pending.challenge)return reply.code(400).send({error:'카카오 연결 요청이 만료됐어요. 앱에서 다시 시작해 주세요.'});
      linkTarget=pending.player;
    }
    const appC=(q.app==='1'||q.app==='2'||q.app==='3')&&typeof q.c==='string'&&/^[A-Za-z0-9_-]{43}$/.test(q.c)?(q.app==='3'?'revised:':q.app==='2'?'native:':'')+q.c:null;
    const u=new URL('https://kauth.kakao.com/oauth/authorize');
    u.search=new URLSearchParams({client_id:kakao.restKey,redirect_uri:`${await origin(req)}/my/kakao/callback`,response_type:'code',state}).toString();
    reply.header('Set-Cookie',[cookieLine(req,KSTATE,state,600),cookieLine(req,KAPP,appC??'',appC?600:0),cookieLine(req,KLINK,linkTarget?signed('app-link.',`${linkTarget}:${q.c}`,600):'',linkTarget?600:0)]);
    return reply.redirect(u.toString());
  });
  app.get<{Querystring:{code?:string;state?:string;error?:string}}>('/my/kakao/callback',{config:{rateLimit:{max:20,timeWindow:'1 minute',keyGenerator:ipKey('mykakao')}}},async(req,reply)=>{
    if(!kakao)return reply.code(404).send({error:'not found'});
    const state=cookie(req,KSTATE),clear=cookieLine(req,KSTATE,'',0);
    if(req.query.error)return kakaoBack(req,reply.header('Set-Cookie',clear),'cancel');
    if(!state||state!==req.query.state||typeof req.query.code!=='string')return kakaoBack(req,reply.header('Set-Cookie',clear),'fail');
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
    }catch(e){req.log.warn({err:String(e)},'kakao login failed');return kakaoBack(req,reply.header('Set-Cookie',clear),'fail');}
    const linked=await game.webKakaoPlayer(kakaoId);
    const appC=cookie(req,KAPP);
    const linkRaw=cookie(req,KLINK),linkPayload=unsign('app-link.',linkRaw);const [linkTarget,linkChallenge]=(linkPayload??'').split(':');
    const appNative=!!appC&&(appC.startsWith('native:')||appC.startsWith('revised:'));
    const appChallenge=appC?.replace(/^(native:|revised:)/,'')??'';
    const appScheme=appC?.startsWith('revised:')?'gmmrevised':'gmmnative';
    if(linkRaw&&appNative&&/^[A-Za-z0-9_-]{43}$/.test(appChallenge)){
      let error:string|undefined;
      if(!linkTarget||linkChallenge!==appChallenge)error='카카오 연결 요청이 만료됐어요. 앱에서 다시 시작해 주세요.';
      else try{await game.webLinkKakao(linkTarget,kakaoId);}catch(e){if(e instanceof GameError)error=e.message;else throw e;}
      const code=randomBytes(18).toString('base64url');appCodes.set(code,{player:linkTarget??'',challenge:appChallenge,exp:Date.now()+120_000,...(error?{error}:{})});
      return reply.header('Set-Cookie',[clear,cookieLine(req,KAPP,'',0),cookieLine(req,KLINK,'',0)]).redirect(`${appScheme}://auth?code=${code}`);
    }
    if(appC&&/^(native:|revised:)?[A-Za-z0-9_-]{43}$/.test(appC)&&(linked||appNative)){
      const native=appNative,challenge=appChallenge;
      for(const [k,v] of appCodes)if(v.exp<Date.now())appCodes.delete(k);
      const code=randomBytes(18).toString('base64url');
      appCodes.set(code,{player:linked??'',challenge,exp:Date.now()+120_000,...(!linked?{kakao:kakaoId}:{})});
      return reply.header('Set-Cookie',[clear,cookieLine(req,KAPP,'',0)]).redirect(`${native?appScheme:'gmmapp'}://auth?code=${code}`);
    }
    if(linked)return back(reply.header('Set-Cookie',[clear,cookieLine(req,COOKIE,issue(linked),DAYS*86400)]),'');
    const now=session(req);
    if(now){   // 이미 코드로 로그인한 상태에서 「카카오 연결」
      try{await game.webLinkKakao(now,kakaoId);return back(reply.header('Set-Cookie',clear),'?kakao=linked');}
      catch(e){if(e instanceof GameError)return back(reply.header('Set-Cookie',clear),'?kakao=taken');throw e;}
    }
    return back(reply.header('Set-Cookie',[clear,cookieLine(req,KPEND,signed('kp.',kakaoId,600),600)]),'?kakao=link');
  });
}
