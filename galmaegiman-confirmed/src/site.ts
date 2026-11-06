// 공개 홈페이지(웹 설계 「공개 홈페이지 보충 설계 v1」): 소개 홈 · 게임 소개 · 캐릭터 소개 · 공지사항 · 다운로드 · 로그인 진입 · 웹 게임(/game)
// - 공개 페이지는 로그인 없이 읽고, 로그인은 「게임 시작」·「내 계정」을 고른 때만 요청한다. 로그인돼 있어도 게임으로 자동 이동하지 않는다.
// - 세계관(/world)은 기존 페이지(쿠폰 조각 포함)를 그대로 쓰고 머리줄에서 연결만 한다.
// - 공지(data/notices.json)·다운로드(data/downloads.json)는 실제로 확인된 내용만 넣는다. 비어 있으면 「없음」·「연결 대기」로 보인다.
// - 공개 캐릭터는 기존 공개 도감 정책과 같게 히든·최상위(제한~불멸)를 뺀다.
import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { marked } from 'marked';
import type { Content } from './content.js';
import { rarityNames, topTiers } from './content.js';

const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
export interface Notice{id:string;title:string;date:string;category?:string;body:string}
export interface DownloadInfo{label:string;version:string;url:string;note?:string;sha256?:string;requirement?:string}
export interface SiteOptions{root:string;version:string;kakao:boolean}
// 로그인 뒤 갈 곳·취소하면 돌아갈 곳: 사이트 안의 정해진 경로만(바깥 주소·//·\\ 거부)
const NEXT=new Set(['/game','/account']);
const FROM=/^\/(?:about|world|characters|notices(?:\/[A-Za-z0-9_-]{1,40})?|download|dex(?:\/[A-Za-z0-9_-]{1,12})?|recipes|hidden|rank|event|talk|odds|privacy)?$/;
export const safeNext=(v:unknown)=>typeof v==='string'&&NEXT.has(v)?v:'/game';
export const safeFrom=(v:unknown)=>typeof v==='string'&&FROM.test(v)?v:'/';

// 공개 페이지 공통 틀(머리줄·메뉴·바닥). 새 공개 페이지(site.ts)와 예전 공개 페이지(web.ts: 도감·조합법·랭킹·이벤트 등)가 같이 쓴다
export interface ShellOptions{path:string;title:string;desc:string;body:string;bare?:boolean;script?:string;noindex?:boolean;image?:string;legacy?:boolean;canonical?:boolean}
// 예전부터 있던 공개 페이지(내용·기능 그대로, 새 디자인 틀): 메뉴의 「둘러보기」와 바닥에 연결
const MORE:[string,string][]=[['/village','마을 시각탐색'],['/dex','도감'],['/recipes','조합법'],['/hidden','히든 단서'],['/rank','랭킹'],['/talk','수다방'],['/event','이벤트'],['/search','검색']];
// 파일 수정 시각을 주소 뒤에 붙여, 파일이 바뀌면 브라우저가 새로 받게 한다
export const stampFor=(root:string)=>(p:string)=>{try{return `/${p}?v=${Math.floor(statSync(resolve(root,'public',p)).mtimeMs)}`;}catch{return `/${p}`;}};
export function siteShell(root:string,version:string,content:Content){
  const site=(content.economy.homepage??'').replace(/\/$/,'');
  const channel=content.economy.kakaoChannel;
  const stamp=stampFor(root);
  // 첫 화면(home-v4)과 같은 메뉴·문장(머리줄이 화면마다 달라 보이지 않게)
  const NAV:[string,string][]=[['/about','게임 소개'],['/world','세계관'],['/characters','캐릭터'],['/notices','공지사항'],['/download','다운로드'],['/village','마을 둘러보기']];
  const logo='<span class="crest" aria-hidden="true">✦</span><span class="brand-name">갈매미맨 <small>방 범 대</small></span>';
  return (o:ShellOptions)=>`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>${o.path==='/'?'갈매미맨':`${esc(o.title)} · 갈매미맨`}</title>
<meta name="description" content="${esc(o.desc)}">${o.noindex?'<meta name="robots" content="noindex">':''}
<meta property="og:type" content="website"><meta property="og:site_name" content="갈매미맨"><meta property="og:title" content="${esc(o.path==='/'?'갈매미맨':o.title)}"><meta property="og:description" content="${esc(o.desc)}">${site?`<meta property="og:image" content="${site}${o.image??'/images/C1.png'}">${o.canonical===false?'':`<link rel="canonical" href="${esc(site+o.path)}">`}`:''}
<link rel="icon" type="image/png" href="/favicon.png"><link rel="apple-touch-icon" href="/icon-192.png"><link rel="manifest" href="/manifest.webmanifest"><meta name="theme-color" content="#10263c">
${o.path==='/'&&site?`<script type="application/ld+json">${JSON.stringify({'@context':'https://schema.org','@type':'WebSite',name:'갈매미맨',alternateName:['갈매미맨 수비대'],url:`${site}/`}).replace(/</g,'\\u003c')}</script>`:''}
<link rel="stylesheet" href="${stamp('site/site.css')}">${o.legacy?`<link rel="stylesheet" href="${stamp('site/legacy.css')}">`:''}<link rel="stylesheet" href="${stamp('site/harbor.css')}">
</head><body class="site harbor">
<a class="skip" href="#main">본문 바로가기</a>
<header class="top"><div class="wrap">
<a class="brand" href="/" aria-label="갈매미맨 방범대 홈">${logo}</a>
${o.bare?`<a class="toplink" href="/" data-back>홈페이지로</a>`:`<nav class="nav" aria-label="공개 메뉴">${NAV.map(([h,t])=>`<a href="${h}"${o.path===h||o.path.startsWith(h+'/')?' aria-current="page"':''}>${t}</a>`).join('')}</nav>
<div class="acts"><a class="nav-cta" href="/login?next=/account" data-auth="account">로그인</a><a class="gold-cta" href="/game" data-start>게임 시작</a></div>
<button class="menu-btn" type="button" aria-expanded="false" aria-controls="drawer" aria-label="메뉴 열기"><span aria-hidden="true"></span></button>`}
</div></header>
${o.bare?'':`<div class="drawer" id="drawer" hidden><div class="drawer-in" role="dialog" aria-modal="true" aria-label="메뉴">
<div class="drawer-top"><span class="brand">${logo}</span><button class="close" type="button" aria-label="메뉴 닫기">×</button></div>
<nav aria-label="공개 메뉴">${NAV.map(([h,t])=>`<a href="${h}"${o.path===h?' aria-current="page"':''}>${t}</a>`).join('')}</nav>
<nav class="drawer-more" aria-label="둘러보기">${MORE.map(([h,t])=>`<a href="${h}"${o.path===h?' aria-current="page"':''}>${t}</a>`).join('')}</nav>
<a class="btn primary wide" href="/game" data-start>게임 시작</a><a class="btn ghost wide" href="/login?next=/account" data-auth="account">로그인 / 내 계정</a>
</div></div>`}
<main id="main">${o.body}</main>
<footer class="foot"><div class="wrap">
<div class="foot-head"><a class="brand" href="/" aria-label="갈매미맨 방범대 홈">${logo}</a><p>우리의 항구에서, 다음 모험을.</p></div>
<p class="foot-links">${channel?`<a href="${esc(channel)}" target="_blank" rel="noopener">카카오톡 채널 · 문의</a>`:''}<a href="/privacy" class="privacy"><b>개인정보 처리방침</b></a><a href="/account/delete">계정 삭제 안내</a><a href="/odds">모집 확률</a><a href="/notices">공지사항</a><a href="/download">다운로드</a></p>
<p class="foot-links sub" aria-label="둘러보기">${MORE.map(([h,t])=>`<a href="${h}">${t}</a>`).join('')}</p>
<div class="foot-bottom"><span>GALMAEMIMAN · v${esc(version)}</span><a href="#main">맨 위로 ↑</a></div>
</div></footer>
<script src="${stamp('site/site.js')}" defer></script>${o.script??''}
</body></html>`;
}

export function registerSite(app:FastifyInstance,content:Content,opt:SiteOptions){
  const site=(content.economy.homepage??'').replace(/\/$/,'');
  const channel=content.economy.kakaoChannel;
  const readJson=<T,>(file:string,empty:T):T=>{try{return JSON.parse(readFileSync(resolve(opt.root,'data',file),'utf8')) as T;}catch{return empty;}};
  const notices=()=>readJson<Notice[]>('notices.json',[]).filter(n=>n&&typeof n.id==='string'&&/^[A-Za-z0-9_-]{1,40}$/.test(n.id)&&n.title&&n.date).sort((a,b)=>b.date.localeCompare(a.date));
  const downloads=()=>readJson<{official:DownloadInfo|null;test:DownloadInfo|null}>('downloads.json',{official:null,test:null});
  const shell=siteShell(opt.root,opt.version,content);
  const stamp=stampFor(opt.root);
  const send=(reply:FastifyReply,html:string,cache='public, max-age=300')=>reply.type('text/html; charset=utf-8').header('Cache-Control',cache).send(html);
  const limit={config:{rateLimit:{max:300,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`site:${ip}`:'site';}}}};
  const pub=()=>content.characters.filter(c=>c.rarity!=='HIDDEN'&&!topTiers.has(c.rarity));
  const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
  const noticeRow=(n:Notice)=>`<li><a href="/notices/${esc(n.id)}"><b>${esc(n.title)}</b><span>${n.category?`${esc(n.category)} · `:''}<time datetime="${esc(n.date)}">${esc(n.date)}</time></span></a></li>`;
  const apkButton=(big=false)=>{const d=downloads().official;
    return d?`<a class="btn ghost${big?' lg':''}" href="/download">APK 다운로드</a>`:`<span class="btn ghost${big?' lg':''}" aria-disabled="true" title="공식 배포 링크 확인 전">APK 다운로드<small>공식 배포 확인 전</small></span>`;};

  // 승인된 공개 홈 시안. 공지·이벤트·인증 경로는 기존 운영 자료를 그대로 쓴다.
  app.get('/',limit,async(_req,reply)=>{
    const latest=notices().slice(0,3);
    const at=new Date(),ev=content.economy.coupons.find(c=>c.pieces.length&&(!c.from||new Date(c.from)<=at)&&(!c.to||at<new Date(c.to)));
    const values:Record<string,string>={
      GMM_STYLE:esc(stamp('site/home-v4/home.css')),
      GMM_SCRIPT:esc(stamp('site/site.js')),
      GMM_CANONICAL:esc(site?site+'/':'/'),
      // 예전 홈에 있던 검색엔진용 사이트 정보(WebSite)를 새 홈에도 그대로 넣는다
      GMM_JSONLD:site?`<script type="application/ld+json">${JSON.stringify({'@context':'https://schema.org','@type':'WebSite',name:'갈매미맨',alternateName:['갈매미맨 수비대','갈매미맨 방범대'],url:`${site}/`}).replace(/</g,'\\u003c')}</script>`:'',
      GMM_VERSION:esc(opt.version),
      GMM_NOTICES:latest.length?'<ul class="nlist home-notices">'+latest.map(noticeRow).join('')+'</ul>':'<p class="empty-notices">아직 게시된 공지가 없어요.</p>',
      GMM_EVENT:ev?'<a class="event-strip" href="/event"><b>진행 중 이벤트</b><span>'+esc(ev.label)+'</span><span>자세히 →</span></a>':'',
      GMM_CONTACT:channel?'<a href="'+esc(channel)+'" target="_blank" rel="noopener">카카오톡 채널 · 문의</a>':'',
    };
    const html=readFileSync(resolve(opt.root,'public/site/home-v4/index.html'),'utf8').replace(/\{\{(GMM_[A-Z]+)\}\}/g,(_match,key:string)=>values[key]??'');
    return send(reply,html);
  });

  // 실제 /game과 별도로 제공하는 공개 마을 시각탐색 초안.
  app.get('/village',limit,async(_req,reply)=>send(reply,readFileSync(resolve(opt.root,'public/site/village-v4/index.html'),'utf8')));

  // ── 게임 소개: 저장소의 실제 자료(대원 수·조합법 수)에서 숫자를 계산한다 ──
  app.get('/about',limit,async(_req,reply)=>{
    const tiers=['COMMON','UNCOMMON','SPECIAL','RARE','LEGEND','HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL','NEUTRAL'].filter(t=>count(t));
    return send(reply,shell({path:'/about',title:'게임 소개',desc:'갈매미맨을 즐기는 방법: 모집, 조합, 탐험, 전투',body:`
<div class="wrap page"><h1>게임 소개</h1>
<p class="lead">갈매미 섬 항구 수비대의 대원을 모으고, 조합하고, 함께 전투에 나서는 게임이에요.</p>
<ol class="steps">
<li><b>모집</b><span>지원서로 하급·중급·고급 모집을 해요. 어떤 대원이 올지는 무작위예요(<a href="/odds">모집 확률</a>).</span></li>
<li><b>조합</b><span>대원을 조합해 더 높은 등급의 대원을 만나요. 조합법은 ${content.recipes.length}가지예요(<a href="/recipes">조합법</a>).</span></li>
<li><b>탐험</b><span>대원을 탐험에 보내 보상을 받아요.</span></li>
<li><b>전투</b><span>대원 3명을 편성해 스토리 전투와 실시간 대전(일반전·랭크전)에 나가요.</span></li>
</ol>
<h2>대원 ${content.characters.length}종</h2>
<ul class="tiers">${tiers.map(t=>`<li><b>${esc(rarityNames[t]??t)}</b><span>${count(t)}종</span></li>`).join('')}</ul>
<p class="muted">히든 대원은 단서로 찾고, 제한·초월·영원·불멸 대원의 모습은 직접 만나면 열려요.</p>
<h2>어디서 하나요</h2>
<ul class="plain">
<li>웹: 이 사이트의 <a href="/game" data-start>게임 시작</a></li>
${channel?`<li>카카오톡 채널: <a href="${esc(channel)}" target="_blank" rel="noopener">갈매미맨 채널</a></li>`:''}
<li>Android 앱: <a href="/download">다운로드</a> 페이지에서 배포 상태를 확인해 주세요.</li>
</ul></div>`}));
  });

  // ── 캐릭터 소개: 공개 대원만, 페이지 그리드 + 같은 화면 상세 ──
  app.get('/characters',limit,async(_req,reply)=>{
    const list=pub().map(c=>({id:c.id,n:c.name,t:rarityNames[c.rarity]??c.rarity,r:c.rarity,q:c.quote??'',g:c.tagline??''}));
    return send(reply,shell({path:'/characters',title:'캐릭터 소개',desc:'갈매미 섬 항구 수비대의 대원들',body:`
<div class="wrap page"><h1>캐릭터 소개</h1>
<p class="lead">공개된 대원 ${list.length}명이에요. 히든과 최상위 대원은 게임에서 직접 만나 보세요. 전체 목록은 <a href="/dex">도감</a>에 있어요.</p>
<div class="chars" data-chars><div class="char-grid" role="listbox" aria-label="대원 목록"></div>
<div class="pager"><button class="btn ghost icon" type="button" data-prev aria-label="이전 페이지">‹</button><span data-page aria-live="polite"></span><button class="btn ghost icon" type="button" data-next aria-label="다음 페이지">›</button></div>
<section class="char-detail" aria-live="polite"></section></div>
<script type="application/json" id="chars-data">${JSON.stringify(list).replace(/</g,'\\u003c')}</script>
</div>`}));
  });

  // ── 공지사항: 실제 게시된 글만(data/notices.json) ──
  const noticeList=(page:number)=>{const all=notices(),per=8,pages=Math.max(1,Math.ceil(all.length/per)),p=Math.min(Math.max(1,page),pages);return {all,pages,p,rows:all.slice((p-1)*per,p*per)};};
  app.get<{Querystring:{p?:string}}>('/notices',limit,async(req,reply)=>{
    const {all,pages,p,rows}=noticeList(Number(req.query.p)||1);
    return send(reply,shell({path:'/notices',title:'공지사항',desc:'갈매미맨 공지사항',body:`
<div class="wrap page"><h1>공지사항</h1>
${all.length?`<ul class="nlist big">${rows.map(noticeRow).join('')}</ul>
<nav class="pager" aria-label="공지 페이지">${p>1?`<a class="btn ghost icon" href="/notices?p=${p-1}" aria-label="이전 페이지">‹</a>`:'<span class="btn ghost icon" aria-disabled="true">‹</span>'}<span>${p} / ${pages}</span>${p<pages?`<a class="btn ghost icon" href="/notices?p=${p+1}" aria-label="다음 페이지">›</a>`:'<span class="btn ghost icon" aria-disabled="true">›</span>'}</nav>`
:'<p class="empty">아직 게시된 공지가 없어요.</p>'}</div>`}));
  });
  app.get<{Params:{id:string}}>('/notices/:id',limit,async(req,reply)=>{
    const n=notices().find(x=>x.id===req.params.id);
    if(!n)return reply.code(404).type('text/html; charset=utf-8').send(shell({path:'/notices',title:'공지 없음',desc:'공지사항',noindex:true,body:'<div class="wrap page"><h1>공지를 찾을 수 없어요</h1><p><a href="/notices">공지 목록으로</a></p></div>'}));
    return send(reply,shell({path:`/notices/${n.id}`,title:n.title,desc:n.title,body:`
<div class="wrap page"><p class="crumb"><a href="/notices">‹ 공지 목록</a></p><article class="notice"><h1>${esc(n.title)}</h1><p class="muted">${n.category?`${esc(n.category)} · `:''}<time datetime="${esc(n.date)}">${esc(n.date)}</time></p>
<div class="prose">${marked.parse(n.body,{async:false}) as string}</div></article></div>`}));
  });

  // ── 다운로드: 정식·시험 배포를 나누고, 확인된 정보가 있을 때만 링크를 연다 ──
  app.get('/download',limit,async(_req,reply)=>{
    const {official,test}=downloads();
    const box=(d:DownloadInfo|null,kind:'official'|'test')=>d?`<p><b>${esc(d.label)}</b> · 버전 ${esc(d.version)}</p>${d.requirement?`<p class="muted">${esc(d.requirement)}</p>`:''}${d.note?`<p>${esc(d.note)}</p>`:''}${d.sha256?`<p class="mono">SHA-256 ${esc(d.sha256)}</p>`:''}<a class="btn ${kind==='official'?'primary':'ghost'}" href="${esc(d.url)}" rel="noopener">${kind==='official'?'정식 APK 다운로드':'시험판 받기'}</a>`
      :`<p class="muted">${kind==='official'?'공식 배포 링크·버전·지원 조건을 확인한 뒤 표시해요.':'공개 배포 허용·서명·출처·기존 설치 영향·설치 안내를 확인한 뒤 제공해요.'}</p><span class="btn ghost" aria-disabled="true">${kind==='official'?'정식 APK 다운로드':'시험 배포 연결 대기'}</span>`;
    return send(reply,shell({path:'/download',title:'다운로드',desc:'갈매미맨 Android 앱 내려받기',body:`
<div class="wrap page"><h1>다운로드</h1>
<div class="dl"><section class="panel"><h2>정식 Android 배포</h2>${box(official,'official')}</section>
<section class="panel test"><h2>시험판(선택)</h2><p class="muted">정식판과 따로 관리하는 확인용 빌드예요.</p>${box(test,'test')}</section></div>
<p class="muted">파일은 직접 누를 때만 내려받아요. 자동으로 내려받거나 설치를 권하지 않아요.</p></div>`}));
  });

  // ── 로그인 진입: 기존 인증 수단(카카오, 카톡 6자리 코드)만. 돌아갈 곳은 서버가 허용 경로로 거른다 ──
  app.get<{Querystring:{next?:string;from?:string;kakao?:string}}>('/login',limit,async(req,reply)=>{
    const next=safeNext(req.query.next),from=safeFrom(req.query.from);
    const k=typeof req.query.kakao==='string'&&/^(fail|taken|link)$/.test(req.query.kakao)?req.query.kakao:'';
    return send(reply,shell({path:'/login',title:'로그인',desc:'갈매미맨 로그인',bare:true,noindex:true,body:`
<section class="login-wrap"><div class="hero-bg" aria-hidden="true"></div>
<div class="login" data-login data-next="${esc(next)}" data-from="${esc(from)}" data-kakao="${opt.kakao?'1':''}" data-kstate="${esc(k)}">
<h1>${next==='/account'?'내 계정 로그인':'게임 시작을 위한 로그인'}</h1>
<p class="muted">${k==='link'?'처음 쓰는 카카오 계정이에요. 카톡 코드로 한 번 로그인하면 이 카카오 계정이 연결돼요.':'기존 계정으로 이어서 시작하세요.'}</p>
<div class="login-ways">
${opt.kakao&&k!=='link'?'<a class="btn kakao wide" href="/my/kakao" data-kakao-start>카카오로 로그인</a>':''}
<form class="code" data-code novalidate><label for="code">카톡 코드로 로그인</label>
<p class="muted small">카톡 갈매미맨 채널에 <b>웹 로그인</b>이라고 보내고, 받은 6자리 코드를 적어요. (10분 안에 한 번만)</p>
<div class="code-row"><input id="code" name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" placeholder="000000" aria-describedby="lerr" required><button class="btn primary" type="submit">로그인</button></div>
<p class="err" id="lerr" role="alert"></p></form>
</div>
<a class="back" href="${esc(from)}" data-cancel>돌아가기</a>
</div></section>`}),'no-store');
  });

  // ── 웹 게임: 화면은 public/game. 로그인 판단은 화면이 /my/api/who로(세션 쿠키가 Path=/my라 여기서는 모름) ──
  const gameFile=()=>readFileSync(resolve(opt.root,'public/game/index.html'),'utf8').replace(/\{\{([a-z]+\.(?:css|js))\}\}/g,(_m,f:string)=>stamp(`game/${f}`));
  app.get('/game',limit,async(_req,reply)=>send(reply,gameFile(),'no-cache'));
  app.get('/account',limit,async(_req,reply)=>reply.redirect('/game#account'));

  // ── 입사 등록증 카드(art/gpt/registration-v1, 145종 완성 WebP). 기존 /images·/thumbs와 같이 명단에 있는 ID만.
  // 미발견·히든·최상위를 가리는 것은 지금처럼 화면이 한다(서버가 실제 그림을 준 대원에게만 카드를 붙임)
  app.get<{Params:{file:string}}>('/site/reg/cards/:file',limit,async(req,reply)=>{
    const id=/^([A-Z]\d{1,2})\.webp$/.exec(req.params.file)?.[1];
    if(!id||!content.map.has(id))return reply.code(404).send({error:'not found'});
    try{return reply.type('image/webp').header('Cache-Control','public, max-age=86400').send(readFileSync(resolve(opt.root,'art/gpt/registration-v1/resources/cards',`${id}.webp`)));}
    catch{return reply.code(404).send({error:'image missing'});}
  });
  // ── 정적 파일: public/site, public/game 아래 정해진 확장자만 ──
  const types:Record<string,string>={css:'text/css; charset=utf-8',js:'text/javascript; charset=utf-8',webp:'image/webp',png:'image/png',woff2:'font/woff2',txt:'text/plain; charset=utf-8',svg:'image/svg+xml'};
  for(const dir of ['site','game'])app.get<{Params:{'*':string}}>(`/${dir}/*`,limit,async(req,reply)=>{
    const rel=req.params['*'],ext=/^[a-z0-9/_-]+\.([a-z0-9]+)$/.exec(rel)?.[1];
    if(!ext||!types[ext]||rel.includes('..'))return reply.code(404).send({error:'not found'});
    // 파일을 먼저 읽고 형식을 정한다(없는 파일에 형식을 먼저 정하면 404 대신 500이 남)
    let body:Buffer;try{body=readFileSync(resolve(opt.root,'public',dir,rel));}catch{return reply.code(404).send({error:'not found'});}
    return reply.type(types[ext]).header('Cache-Control',ext==='css'||ext==='js'?'public, max-age=300':'public, max-age=86400').send(body);
  });
}
