// 공개 홈페이지(웹 설계 「공개 홈페이지 보충 설계 v1」): 소개 홈 · 게임 소개 · 캐릭터 소개 · 공지사항 · 다운로드 · 로그인 진입 · 웹 게임(/game)
// 2026-11-06: 게임이 60종(roster60) 규칙으로 바뀌어, 소개·캐릭터·모집 확률·세계관을 서버의 실제 60종 자료로 만들고
//   예전 145종 페이지(도감·조합법·히든 단서·랭킹·수다방·이벤트·검색·프로필)는 닫는다(첫 화면으로 이동).
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
const NEXT=new Set(['/game','/game60','/account']);
const FROM=/^\/(?:about|world|characters|notices(?:\/[A-Za-z0-9_-]{1,40})?|download|dex(?:\/[A-Za-z0-9_-]{1,12})?|recipes|hidden|rank|event|talk|odds|privacy)?$/;
export const safeNext=(v:unknown)=>typeof v==='string'&&NEXT.has(v)?v:'/game';
export const safeFrom=(v:unknown)=>typeof v==='string'&&FROM.test(v)?v:'/';

// 공개 페이지 공통 틀(머리줄·메뉴·바닥). 새 공개 페이지(site.ts)와 예전 공개 페이지(web.ts: 도감·조합법·랭킹·이벤트 등)가 같이 쓴다
export interface ShellOptions{path:string;title:string;desc:string;body:string;bare?:boolean;script?:string;noindex?:boolean;image?:string;legacy?:boolean;canonical?:boolean}
// 예전부터 있던 공개 페이지(내용·기능 그대로, 새 디자인 틀): 메뉴의 「둘러보기」와 바닥에 연결
const MORE:[string,string][]=[['/odds','모집 확률']];
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
<div class="acts"><a class="gold-cta" href="/download">앱 다운로드</a></div>
<button class="menu-btn" type="button" aria-expanded="false" aria-controls="drawer" aria-label="메뉴 열기"><span aria-hidden="true"></span></button>`}
</div></header>
${o.bare?'':`<div class="drawer" id="drawer" hidden><div class="drawer-in" role="dialog" aria-modal="true" aria-label="메뉴">
<div class="drawer-top"><span class="brand">${logo}</span><button class="close" type="button" aria-label="메뉴 닫기">×</button></div>
<nav aria-label="공개 메뉴">${NAV.map(([h,t])=>`<a href="${h}"${o.path===h?' aria-current="page"':''}>${t}</a>`).join('')}</nav>
<nav class="drawer-more" aria-label="둘러보기">${MORE.map(([h,t])=>`<a href="${h}"${o.path===h?' aria-current="page"':''}>${t}</a>`).join('')}</nav>
<a class="btn primary wide" href="/download">앱 다운로드</a>
</div></div>`}
<main id="main">${o.body}</main>
<footer class="foot"><div class="wrap">
<div class="foot-head"><a class="brand" href="/" aria-label="갈매미맨 방범대 홈">${logo}</a><p>우리의 항구에서, 다음 모험을.</p></div>
<p class="foot-links">${channel?`<a href="${esc(channel)}" target="_blank" rel="noopener">카카오톡 채널 · 문의</a>`:''}<a href="/privacy" class="privacy"><b>개인정보 처리방침</b></a><a href="/account/delete">계정 삭제 안내</a><a href="/odds">모집 확률</a><a href="/notices">공지사항</a><a href="/download">다운로드</a></p>
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
  const r60=roster60Reader(opt.root);

  // ── 예전 145종(카톡 챗봇) 공개 페이지는 닫는다: 첫 화면으로 영구 이동. 모집 확률·세계관·사이트맵은 60종 기준 새 페이지로 ──
  app.addHook('onRequest',async(req,reply)=>{
    const path=(req.url.split('?')[0]??'').replace(/\/+$/,'')||'/';
    if(req.method!=='GET'&&req.method!=='HEAD')return;   // 페이지를 닫는 것이라 읽기 요청만 돌린다
    if(LEGACY_PAGE.test(path)||LEGACY_API.test(path))return reply.redirect('/',301);
    if(path==='/odds')return send(reply,oddsPage());
    if(path==='/world')return send(reply,worldPage());
    if(path==='/account/delete')return send(reply,deletePage());
    if(path==='/sitemap.xml')return reply.type('application/xml; charset=utf-8').header('Cache-Control','public, max-age=3600').send(sitemap());
  });
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
    const values:Record<string,string>={
      GMM_STYLE:esc(stamp('site/home-v4/home.css')),
      GMM_SCRIPT:esc(stamp('site/site.js')),
      GMM_CANONICAL:esc(site?site+'/':'/'),
      // 예전 홈에 있던 검색엔진용 사이트 정보(WebSite)를 새 홈에도 그대로 넣는다
      GMM_JSONLD:site?`<script type="application/ld+json">${JSON.stringify({'@context':'https://schema.org','@type':'WebSite',name:'갈매미맨',alternateName:['갈매미맨 수비대','갈매미맨 방범대'],url:`${site}/`}).replace(/</g,'\\u003c')}</script>`:'',
      GMM_VERSION:esc(opt.version),
      GMM_NOTICES:latest.length?'<ul class="nlist home-notices">'+latest.map(noticeRow).join('')+'</ul>':'<p class="empty-notices">아직 게시된 공지가 없어요.</p>',
      // 예전 챗봇 쿠폰 이벤트(/event)는 닫혀 홈에 띄우지 않는다
      GMM_EVENT:'',
      GMM_CONTACT:channel?'<a href="'+esc(channel)+'" target="_blank" rel="noopener">카카오톡 채널 · 문의</a>':'',
    };
    const html=readFileSync(resolve(opt.root,'public/site/home-v4/index.html'),'utf8').replace(/\{\{(GMM_[A-Z]+)\}\}/g,(_match,key:string)=>values[key]??'');
    return send(reply,html);
  });

  // 실제 /game과 별도로 제공하는 공개 마을 시각탐색 초안.
  app.get('/village',limit,async(_req,reply)=>send(reply,readFileSync(resolve(opt.root,'public/site/village-v4/index.html'),'utf8')));

  // ── 게임 소개: 서버의 60종 자료(도감·모집 정책·스토리)에서 숫자를 계산한다. 규칙 문장은 앱 안내 문구와 서버 정책을 따른다 ──
  app.get('/about',limit,async(_req,reply)=>{
    const d=r60(),chapters=d.story?.chapters.length;
    return send(reply,shell({path:'/about',title:'게임 소개',desc:'갈매미맨 방범대를 즐기는 방법: 모집, 조합, 탐험, 스토리, 모험, 대전',body:`
<div class="wrap page"><h1>게임 소개</h1>
<p class="lead">갈매미를 모집해 나만의 방범대를 꾸리고, 스토리와 대전에 나서는 Android 수집형 전투 게임이에요.</p>
<ol class="steps">
<li><b>모집</b><span>지원서로 ${d.recruit?d.recruit.offers.map(o=>`${esc(o.label)}(${o.cost}장)`).join('·'):'하급·중급·고급'} 모집을 해요. 등급은 정해진 확률로 나오고, 같은 등급 안에서는 모두 같은 확률이에요(<a href="/odds">모집 확률</a>). 매일 출석하면 지원서를 받아요.</span></li>
<li><b>조합</b><span>재료 갈매미가 모이면 조합으로 새 갈매미를 깨워요.</span></li>
<li><b>탐험</b><span>갈매미를 탐험에 보내면 시간이 지난 뒤 보상을 가지고 돌아와요.</span></li>
<li><b>스토리</b><span>${chapters?`${chapters}개 챕터`:'챕터'}를 따라 마을 주민의 의뢰를 해결하며 갈매미의 이야기를 찾아가요(<a href="/world">세계관</a>).</span></li>
<li><b>모험</b><span>모험 전투를 이어 가며 보상을 모아요. 정상 복귀하면 보상을 받고, 전멸하면 그 모험의 받지 않은 보상을 잃어요.</span></li>
<li><b>대전</b><span>일반 게임·랭크게임·연습 중에서 골라요. 실시간 대전에는 3마리 편성이 필요하고, 랭크게임은 출전 대원이 모두 Lv.60 이상이어야 해요.</span></li>
</ol>
<h2>전투 규칙</h2>
<ul class="plain">
<li><b>속성 상성</b>: 속성마다 강한 상대 2개, 약한 상대 2개가 있어요. 유리한 기술은 1.5배, 불리한 기술은 0.67배예요.</li>
<li><b>증강체</b>: 전투를 시작할 때 증강체를 골라요. 30초 안에 정하지 않으면 무작위로 정해져요.</li>
<li><b>경험치</b>: 이긴 전투에서 마지막에 나와 있던 갈매미는 보상 전부, 편성된 나머지 갈매미는 절반을 받아요. 진 전투는 받지 않아요. 보상 크기는 그 전투에 나온 모든 갈매미의 평균 레벨로 정해져요.</li>
<li><b>성장</b>: 레벨은 60까지 올라요. 제한·초월·불멸·영원 갈매미는 환생하면 99까지 올라요.</li>
</ul>
<h2>갈매미 ${d.units.length}종</h2>
<ul class="tiers">${R60_GRADES.filter(([g])=>d.units.some(u=>u.rarity===g)).map(([g,n])=>`<li><b>${n}</b><span>${d.units.filter(u=>u.rarity===g).length}종</span></li>`).join('')}</ul>
<p class="muted">히든과 제한·초월·불멸·영원 갈매미는 홈페이지에 공개하지 않아요. 게임에서 직접 만나 보세요.</p>
<h2>어디서 하나요</h2>
<ul class="plain">
<li>Android 앱: <a href="/download">다운로드</a> 페이지에서 배포 상태를 확인해 주세요.</li>
${channel?`<li>문의: <a href="${esc(channel)}" target="_blank" rel="noopener">카카오톡 채널</a></li>`:''}
</ul></div>`}));
  });

  // ── 캐릭터 소개: 공개 등급(커먼~전설) 갈매미만, 방범대 등록증(registration-v10) 그림 ──
  app.get('/characters',limit,async(_req,reply)=>{
    const list=r60().units.filter(u=>R60_PUBLIC.has(u.rarity)).map(u=>({id:u.id,n:u.name,t:R60_GRADE_NAME[u.rarity]??u.rarity,role:u.role??'',img:`/site/units/${u.id}.webp`}));
    return send(reply,shell({path:'/characters',title:'캐릭터 소개',desc:'갈매미맨 방범대의 갈매미들',image:list[0]?list[0].img:undefined,body:`
<div class="wrap page"><h1>캐릭터 소개</h1>
<p class="lead">공개된 갈매미 ${list.length}종이에요. 히든과 제한·초월·불멸·영원 갈매미는 게임에서 직접 만나 보세요.</p>
<div class="chars" data-chars><div class="char-grid" role="listbox" aria-label="갈매미 목록"></div>
<div class="pager"><button class="btn ghost icon" type="button" data-prev aria-label="이전 페이지">‹</button><span data-page aria-live="polite"></span><button class="btn ghost icon" type="button" data-next aria-label="다음 페이지">›</button></div>
<section class="char-detail" aria-live="polite"></section></div>
<script type="application/json" id="chars-data">${JSON.stringify(list).replace(/</g,'\\u003c')}</script>
</div>`}));
  });

  // ── 모집 확률: 서버가 실제로 쓰는 모집 정책 파일(data/roster60-recruit-policy.json)과 도감에서 계산 ──
  const pct=(n:number)=>`${Number(n.toFixed(4))}%`;
  const oddsPage=()=>{const d=r60(),p=d.recruit;
    const body=!p?'<p class="empty">모집 확률 정보를 불러오지 못했어요. 잠시 뒤 다시 확인해 주세요.</p>':`
<p class="lead">지원서로 하는 모집의 확률이에요. 먼저 등급이 정해지고, 같은 등급 안의 갈매미는 모두 같은 확률로 나와요.</p>
${p.offers.map(o=>`<section class="panel odds"><h2>${esc(o.label)} 모집 <small>지원서 ${o.cost}장 · 다시 뽑기 ${o.cost/2}장</small></h2>
<table><thead><tr><th scope="col">등급</th><th scope="col">등급 확률</th><th scope="col">해당 갈매미</th><th scope="col">1종당 확률</th></tr></thead><tbody>
${Object.entries(o.weights).map(([g,w])=>{const n=d.units.filter(u=>u.rarity===g).length;return `<tr><th scope="row">${R60_GRADE_NAME[g]??esc(g)}</th><td>${pct(w)}</td><td>${n}종</td><td>${n?pct(w/n):'-'}</td></tr>`;}).join('')}
</tbody></table></section>`).join('')}
<ul class="plain">
<li>다시 뽑기는 처음 나온 갈매미와 같은 등급 안에서 다시 뽑아요.</li>
<li>${p.pity==='none'?'모집 횟수에 따른 보장(천장)은 없어요.':'모집 횟수에 따른 보장 규칙은 게임 안내를 확인해 주세요.'}</li>
<li>제한·초월·불멸·영원 갈매미는 모집으로 나오지 않아요.</li>
<li>확률은 서버 설정(${esc(p.version)})에서 바로 계산해 보여 줘요. 바뀌면 이 페이지도 함께 바뀌어요.</li>
</ul>`;
    return shell({path:'/odds',title:'모집 확률',desc:'갈매미맨 방범대 모집 확률',body:`<div class="wrap page"><h1>모집 확률</h1>${body}</div>`});};

  // ── 세계관: 스토리모드 자료(data/story/story-mode-v1.json)의 첫 장면·챕터·마을 주민 ──
  const worldPage=()=>{const st=r60().story;
    const intro=st?.scenes.find(x=>x.id==='STORY_01_intro');
    const body=!st?'<p class="empty">세계관 정보를 불러오지 못했어요.</p>':`
<p class="lead">마을 사람들이 태어나기 전부터 지켜 온 석상 터. 왜 지키는지 아는 이가 없는 그 석상의 조각과 이유를 찾아 갈매미맨이 길을 나서요.</p>
${intro?`<section class="story-scene" aria-label="이야기의 시작"><h2>이야기의 시작</h2>${intro.lines.map(l=>l.speakerId==='narrator'?`<p class="narr">${esc(l.text)}</p>`:`<p class="line">“${esc(l.text)}”</p>`).join('')}</section>`:''}
<h2>스토리 챕터</h2>
<ol class="steps">${st.chapters.map(c=>`<li><b>${esc(c.title)}</b><span>${Array.isArray(c.recommendedLevel)&&c.recommendedLevel.length===2?`권장 레벨 ${c.recommendedLevel[0]}~${c.recommendedLevel[1]}`:''}</span></li>`).join('')}</ol>
<h2>마을 사람들</h2>
<ul class="residents">${st.residents.map(r=>`<li><b>${esc(r.name)}</b><span class="role">${esc(r.role)}</span><q>${esc(r.dailyDialogue)}</q></li>`).join('')}</ul>
<p class="muted">이야기의 나머지는 앱의 스토리모드에서 이어져요.</p>`;
    return shell({path:'/world',title:'세계관',desc:'갈매미맨 방범대의 세계와 스토리',body:`<div class="wrap page"><h1>세계관</h1>${body}</div>`});};

  // ── 계정 삭제 안내: 지금 앱(설정 → 계정 삭제)의 실제 순서. 지워지는 범위 문장은 앱 확인 창 문구를 따른다 ──
  const deletePage=()=>shell({path:'/account/delete',title:'계정 삭제',desc:'갈매미맨 방범대 계정 삭제(탈퇴) 안내',body:`
<div class="wrap page"><h1>계정 삭제(탈퇴) 안내</h1>
<p class="lead">앱에서 언제든 계정과 게임 데이터를 지울 수 있어요.</p>
<ol class="steps">
<li><b>설정 열기</b><span>앱의 마을 메뉴에서 설정을 열어요.</span></li>
<li><b>확인 문구 적기</b><span>「계정 삭제」를 펼치고 확인 칸에 <b>탈퇴</b>라고 적어요.</span></li>
<li><b>계정 삭제</b><span>「계정 삭제」 버튼을 누르면 바로 지워져요. 되돌릴 수 없어요.</span></li>
</ol>
<ul class="plain">
<li><b>지워지는 것</b>: 갈매미·도감·재화·아이템·탐험·스토리 진행.</li>
<li><b>남는 것</b>: 매일 만드는 서버 백업에 최대 약 7일 동안 남았다가 자동으로 사라져요.</li>
<li>진행 중인 대전이나 매칭이 있으면 마친 뒤 탈퇴해 주세요.</li>
</ul>
<p>앱을 지웠다면 다시 설치해 같은 계정으로 로그인한 뒤 위 순서로 지울 수 있어요. 도움이 필요하면 ${channel?`<a href="${esc(channel)}" target="_blank" rel="noopener">카카오톡 채널</a>로 문의해 주세요`:'문의해 주세요'}. 자세한 내용은 <a href="/privacy">개인정보 처리방침</a>을 봐 주세요.</p>
</div>`});

  // ── 사이트맵: 지금 열려 있는 공개 페이지만 ──
  const sitemap=()=>{const base=site||'';const pages=['/','/about','/world','/characters','/notices','/download','/village','/odds','/privacy','/account/delete',...notices().map(n=>`/notices/${n.id}`)];
    return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${pages.map(p=>`<url><loc>${esc(base+p)}</loc></url>`).join('')}</urlset>\n`;};

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
  // ── PC 웹게임(60종, 앱과 같은 서버 기능). 1단계: 로그인·마을·내 갈매미·모집·스토리. 화면은 public/game60 ──
  const game60File=()=>readFileSync(resolve(opt.root,'public/game60/index.html'),'utf8').replace(/\{\{(game\.(?:css|js))\}\}/g,(_m,f:string)=>stamp(`game60/${f}`));
  app.get('/game60',limit,async(_req,reply)=>send(reply,game60File(),'no-cache'));

  // ── 정적 파일: public/site, public/game 아래 정해진 확장자만 ──
  const types:Record<string,string>={css:'text/css; charset=utf-8',js:'text/javascript; charset=utf-8',webp:'image/webp',png:'image/png',woff2:'font/woff2',otf:'font/otf',txt:'text/plain; charset=utf-8',svg:'image/svg+xml'};
  for(const dir of ['site','game','game60'])app.get<{Params:{'*':string}}>(`/${dir}/*`,limit,async(req,reply)=>{
    const rel=req.params['*'],ext=/^[a-z0-9/_-]+\.([a-z0-9]+)$/.exec(rel)?.[1];
    if(!ext||!types[ext]||rel.includes('..'))return reply.code(404).send({error:'not found'});
    // 파일을 먼저 읽고 형식을 정한다(없는 파일에 형식을 먼저 정하면 404 대신 500이 남)
    let body:Buffer;try{body=readFileSync(resolve(opt.root,'public',dir,rel));}catch{return reply.code(404).send({error:'not found'});}
    return reply.type(types[ext]).header('Cache-Control',ext==='css'||ext==='js'?'public, max-age=300':'public, max-age=86400').send(body);
  });
}

// ── 지금 게임(60종, roster60) 자료 읽기: 서버에 있는 실제 파일을 읽고, 파일이 바뀌면 다시 읽는다 ──
export const R60_GRADES:[string,string][]=[['COMMON','커먼'],['UNCOMMON','언커먼'],['RARE','레어'],['EPIC','에픽'],['LEGEND','전설'],['HIDDEN','히든'],['LIMITED','제한'],['TRANSCEND','초월'],['IMMORTAL','불멸'],['ETERNAL','영원']];
export const R60_GRADE_NAME:Record<string,string>=Object.fromEntries(R60_GRADES);
// 홈페이지에 보여 주는 등급(예전 공개 정책과 같게 히든·최상위는 뺀다)
export const R60_PUBLIC=new Set(['COMMON','UNCOMMON','RARE','EPIC','LEGEND']);
// 닫는 예전 145종 페이지·자료 주소
export const LEGACY_PAGE=/^\/(?:dex(?:\/[^/]+)?|recipes|hidden|rank|talk|event|search|p\/[^/]+)$/;
export const LEGACY_API=/^\/(?:search-index\.json|api\/players)$/;
interface R60Unit{id:string;name:string;rarity:string;role?:string}
interface R60Recruit{version:string;pity:string|null;offers:{kind:string;label:string;cost:number;weights:Record<string,number>}[]}
interface R60Story{chapters:{id:number;title:string;recommendedLevel?:number[]}[];residents:{name:string;role:string;dailyDialogue:string}[];scenes:{id:string;lines:{speakerId:string;text:string}[]}[]}
export function roster60Reader(root:string){
  const profile=process.env.ROSTER60_PROFILE||'five-stat-device-test-v1';
  const files={catalog:`data/test-profiles/${profile}/catalog.json`,recruit:'data/roster60-recruit-policy.json',story:'data/story/story-mode-v1.json'};
  const cache=new Map<string,{mtime:number;value:unknown}>();
  const read=<T,>(rel:string):T|null=>{const file=resolve(root,rel);try{const mtime=statSync(file).mtimeMs,hit=cache.get(rel);if(hit&&hit.mtime===mtime)return hit.value as T;const value=JSON.parse(readFileSync(file,'utf8')) as T;cache.set(rel,{mtime,value});return value;}catch{return null;}};
  return ()=>{
    const catalog=read<{characters:R60Unit[]}>(files.catalog);
    return {units:Array.isArray(catalog?.characters)?catalog!.characters:[],recruit:read<R60Recruit>(files.recruit),story:read<R60Story>(files.story)};
  };
}
