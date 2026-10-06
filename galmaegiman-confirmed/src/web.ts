// 공개 웹페이지: 홈 · 세계관 · 도감(+상세) · 조합법 · 히든 단서 · 랭킹 · 검색 · 프로필
// 누구나 보는 페이지라 스포일러를 가립니다.
// - 히든: 이름·그림·이야기 대신 ??? 와 단서(발견한 사람이 늘수록 더 열림)
// - 최상위(제한~불멸): 이름만, 그림은 등급 실루엣, 이야기는 가림
// 세계관 글은 data/world.md(마크다운)를 고치면 바로 반영됩니다.
import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply } from 'fastify';
import { marked } from 'marked';
import { DateTime } from 'luxon';
import type { Character, Content } from './content.js';
import { rarityNames, topTiers } from './content.js';
import { emblems } from './acquisition.js';

const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const tierColor:Record<string,string>={COMMON:'#7d7d7d',UNCOMMON:'#2e9d5b',SPECIAL:'#1a9fb3',RARE:'#8b4fd1',LEGEND:'#c99a1c',HIDDEN:'#5b3f8c',
  LIMITED:'#c8373b',TRANSCEND:'#3f86d4',ETERNAL:'#1f9e8f',IMMORTAL:'#d9a400',NEUTRAL:'#9a948a'};
// 웹 도감 등급 순서(특수함은 안흔함 다음)
const dexOrder=['COMMON','UNCOMMON','NEUTRAL','SPECIAL','RARE','LEGEND','HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'] as const;
// 히든 단서가 열리는 발견자 수
const HINT_INITIALS=1,HINT_NAME=3;
// 초성: 한글 음절만 초성으로, 나머지는 그대로
const CHO='ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
export const initials=(s:string)=>[...s].map(ch=>{const c=ch.charCodeAt(0)-0xac00;return c>=0&&c<11172?CHO[Math.floor(c/588)]:ch;}).join('');
const isInitials=(q:string)=>/^[ㄱ-ㅎ\s]+$/.test(q);

export interface RankRow{rank:number;name:string;nick:boolean;score:number;best:Character|null}
export interface WebProfile{nickname:string;joined:Date;dex:number;byTier:Map<string,number>;best:Character|null;dexRank:{rank:number;total:number;score:number}|null;topRank:{rank:number;total:number;score:number}|null;weekRank:{rank:number;total:number;score:number}|null;titles:string[];firsts:number}
export interface WebData{
  ranking:(kind:'DEX'|'TOP'|'WEEK')=>Promise<{total:number;generatedAt:Date;rows:RankRow[]}>;
  seenCounts:()=>Promise<Map<string,number>>;
  hiddenFirsts:()=>Promise<Map<string,{name:string|null;at:Date}>>;
  searchPlayers:(q:string)=>Promise<string[]>;
  profile:(nickname:string)=>Promise<WebProfile|null>;
  source:(id:string)=>string;
}
export interface WebOptions{root:string;stamp:(path:string)=>string;version:string;data?:WebData}

export function registerWeb(app:FastifyInstance,content:Content,options:WebOptions){
  const img=(kind:'thumbs'|'images',id:string)=>{const file=`${kind}/${id}.${kind==='thumbs'?'jpg':'png'}`;const v=options.stamp(file);return `/${file}${v?`?v=${v}`:''}`;};
  const exists=(id:string)=>{try{statSync(resolve(options.root,'public/images',`${id}.png`));return true;}catch{return false;}};
  const face=(c:Character)=>c.rarity==='HIDDEN'?'unknown':topTiers.has(c.rarity)?(exists(`q-${c.rarity}`)?`q-${c.rarity}`:'unknown'):c.id;
  const badge=(t:string)=>`<span class="badge" style="background:${tierColor[t]}">${esc(rarityNames[t])}</span>`;
  const recipeOf=(id:string)=>content.recipes.find(r=>r.resultId===id);
  const counts=(ids:string[])=>[...ids.reduce((m,id)=>m.set(id,(m.get(id)??0)+1),new Map<string,number>())];
  const hiddenIndex=(c:Character)=>content.characters.filter(x=>x.rarity==='HIDDEN').indexOf(c)+1;
  // 유닛 칩(작은 그림+이름). 히든은 ??? 로 히든 페이지에 연결
  const chip=(id:string,n=1)=>{const c=content.map.get(id)!;
    if(c.rarity==='HIDDEN')return `<a class="uc" href="/hidden#h${hiddenIndex(c)}"><img loading="lazy" src="${img('thumbs','unknown')}" alt="" width="28" height="28">???${n>1?` ×${n}`:''}</a>`;
    return `<a class="uc" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="" width="28" height="28">${esc(c.name)}${n>1?` ×${n}`:''}</a>`;};
  const empty:WebData={ranking:async()=>({total:0,generatedAt:new Date(),rows:[]}),seenCounts:async()=>new Map(),hiddenFirsts:async()=>new Map(),searchPlayers:async()=>[],profile:async()=>null,source:()=>''};
  const data=options.data??empty;

  // 홈페이지 창설 쿠폰 조각: 세계관·조합법·히든 페이지 맨 아래에 하나씩
  const now=()=>new Date();
  const huntCoupon=()=>content.economy.coupons.find(c=>c.pieces.length&&(!c.from||new Date(c.from)<=now())&&(!c.to||now()<new Date(c.to)));
  const piecePages=['/world','/recipes','/hidden'];
  const piece=(path:string)=>{const c=huntCoupon(),i=piecePages.indexOf(path);if(!c||i<0||!c.pieces[i])return '';
    return `<aside class="piece"><span>🧩 쿠폰 조각 ${i+1}/${c.pieces.length}</span><b>「${esc(c.pieces[i])}」</b><small>조각을 순서대로 모아 카톡에 <b>쿠폰 조각1조각2조각3</b> 처럼 보내 보세요.</small></aside>`;};

  // data/world.md: 파일이 바뀌면 다시 읽음
  let world={mtime:0,html:''};
  const worldHtml=()=>{
    const path=resolve(options.root,'data/world.md');
    try{
      const m=statSync(path).mtimeMs;
      if(m!==world.mtime)world={mtime:m,html:marked.parse(readFileSync(path,'utf8'),{async:false}) as string};
    }catch{world={mtime:0,html:'<p>세계관 글을 준비 중이에요.</p>'};}
    return world.html;
  };

  const navItems=[['/','홈'],['/world','세계관'],['/dex','도감'],['/recipes','조합법'],['/hidden','히든'],['/rank','랭킹']];
  const page=(title:string,active:string,body:string,q='')=>`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)} · 갈매미맨</title>
<meta name="description" content="돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기">
<meta property="og:title" content="${esc(title)} · 갈매미맨"><meta property="og:image" content="${img('images','C1')}">
<style>
:root{--bg:#f6f4ef;--card:#fff;--text:#2b2b2b;--muted:#6b6b6b;--line:#e2ddd3;--accent:#1f4e79;--accent-soft:#e3ecf5;--gold:#d9a400}
@media (prefers-color-scheme:dark){:root{--bg:#16181c;--card:#20242a;--text:#ececec;--muted:#a3a3a3;--line:#353a42;--accent:#8fb8e8;--accent-soft:#25303d}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif;line-height:1.65;word-break:keep-all;overflow-wrap:anywhere}
a{color:var(--accent)}
header{background:var(--card);border-bottom:1px solid var(--line)}
@media (min-width:900px){header{position:sticky;top:0;z-index:5}}
.bar{max-width:1080px;margin:0 auto;padding:8px 16px;display:flex;align-items:center;gap:8px 14px;flex-wrap:wrap}
.logo{font-weight:800;font-size:1.1rem;text-decoration:none;color:var(--text)}
nav{display:flex;gap:2px;flex-wrap:wrap}
nav a{text-decoration:none;color:var(--muted);padding:5px 10px;border-radius:999px;font-weight:600;font-size:.92rem}
nav a.on{background:var(--accent-soft);color:var(--accent)}
form.search{display:flex;gap:6px;flex:1 1 220px;min-width:0}
form.search input{flex:1;min-width:0;padding:8px 12px;border-radius:999px;border:1.5px solid var(--line);background:var(--bg);color:var(--text);font:inherit;font-size:.95rem}
form.search button{padding:8px 14px;border-radius:999px;border:0;background:var(--accent);color:#fff;font-weight:700;font:inherit}
@media (prefers-color-scheme:dark){form.search button{color:#10151b}}
main{max-width:1080px;margin:0 auto;padding:20px 16px 48px}
h1{font-size:1.7rem;margin:.4em 0 .5em}h2{font-size:1.25rem;margin:1.8em 0 .6em;padding-bottom:.3em;border-bottom:2px solid var(--line)}
blockquote{margin:0 0 1em;padding:10px 14px;border-left:4px solid var(--accent);background:var(--accent-soft);border-radius:0 8px 8px 0;color:var(--text)}
blockquote p{margin:0}
.prose table{width:100%;border-collapse:collapse;background:var(--card);border-radius:10px;overflow:hidden;margin:.5em 0 1em;font-size:.95rem}
.prose th,.prose td{padding:9px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
.prose th{background:var(--accent-soft)}.prose td:first-child,.prose th:first-child{white-space:nowrap}.prose thead:empty,.prose th:empty{display:none}
.prose ol,.prose ul{padding-left:1.3em}.prose li{margin:.35em 0}
.hero{display:grid;gap:20px;align-items:center;grid-template-columns:1fr}
@media (min-width:720px){.hero{grid-template-columns:1.1fr .9fr}}
.hero img{width:100%;max-width:420px;height:auto;justify-self:center;border-radius:16px;box-shadow:0 6px 24px rgba(0,0,0,.15)}
.lead{font-size:1.05rem;color:var(--muted)}
.btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px}
.btn{display:inline-block;padding:10px 16px;border-radius:12px;background:var(--accent);color:#fff;text-decoration:none;font-weight:700}
@media (prefers-color-scheme:dark){.btn{color:#10151b}}
.btn.ghost{background:transparent;color:var(--accent);border:2px solid var(--accent)}
.event{margin:18px 0;padding:14px 16px;border-radius:14px;background:linear-gradient(135deg,#fff3c4,#ffe0ef);color:#3b2a00;border:1px solid #f0d27a}
.event b{font-size:1.05rem}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:20px 0}
.stat{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px}
.stat b{display:block;font-size:1.4rem}
.links{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
.links a{display:block;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px;text-decoration:none;color:var(--text)}
.links a b{display:block;font-size:1.05rem}.links a span{color:var(--muted);font-size:.85rem}
.chips{display:flex;gap:6px;flex-wrap:wrap;margin:6px 0 4px;position:sticky;top:0;z-index:4;background:var(--bg);padding:8px 0}
@media (min-width:900px){.chips{top:52px}}
.chip{text-decoration:none;font-size:.85rem;font-weight:700;padding:5px 10px;border-radius:999px;border:1.5px solid currentColor;background:var(--card)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
@media (min-width:720px){.grid{grid-template-columns:repeat(auto-fill,minmax(220px,1fr))}}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;overflow:hidden;display:flex;flex-direction:column;text-decoration:none;color:var(--text)}
a.card:hover{border-color:var(--accent)}
.card img{width:100%;height:auto;aspect-ratio:1;object-fit:cover;display:block;background:var(--line)}
.card .in{padding:10px 12px 12px}
.badge{display:inline-block;font-size:.75rem;font-weight:800;color:#fff;padding:2px 8px;border-radius:999px}
.card h3{margin:6px 0 4px;font-size:1rem}
.card p{margin:4px 0;font-size:.88rem}
.card .q,.card .r{color:var(--muted)}.card .r{font-size:.8rem}
.card.locked .in p{color:var(--muted)}
.uc{display:inline-flex;align-items:center;gap:6px;padding:3px 10px 3px 3px;margin:3px 4px 3px 0;border-radius:999px;background:var(--card);border:1px solid var(--line);text-decoration:none;color:var(--text);font-size:.9rem;font-weight:600}
.uc img{width:28px;height:28px;border-radius:50%;object-fit:cover}
.rec{display:grid;grid-template-columns:1fr;gap:6px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px 12px;margin:8px 0}
@media (min-width:720px){.rec{grid-template-columns:260px 1fr;align-items:center}}
.rec .eq{color:var(--muted);font-weight:800;margin:0 4px}
.detail{display:grid;gap:18px;grid-template-columns:1fr}
@media (min-width:720px){.detail{grid-template-columns:360px 1fr}}
.detail img.big{width:100%;height:auto;border-radius:16px;box-shadow:0 6px 24px rgba(0,0,0,.15)}
.box{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 14px;margin:10px 0}
.box h3{margin:0 0 6px;font-size:1rem}
.hint{display:grid;gap:6px}
.hint .h{padding:8px 10px;border-radius:10px;background:var(--bg);border:1px dashed var(--line)}
.hint .h.lock{color:var(--muted)}
.hint .h b{margin-right:6px}
.mono{font-family:ui-monospace,Menlo,Consolas,monospace;letter-spacing:.08em}
.tabs{display:flex;gap:6px;margin:4px 0 14px;flex-wrap:wrap}
.tabs a{text-decoration:none;padding:7px 14px;border-radius:999px;border:1.5px solid var(--line);color:var(--muted);font-weight:700;background:var(--card)}
.tabs a.on{border-color:var(--accent);color:var(--accent);background:var(--accent-soft)}
.rank{list-style:none;margin:0;padding:0;display:grid;gap:8px}
.rank li{display:grid;grid-template-columns:44px 56px 1fr auto;align-items:center;gap:10px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:8px 12px}
.rank li.top1{border-color:var(--gold);box-shadow:0 0 0 2px rgba(217,164,0,.25)}
.rank .no{font-size:1.25rem;font-weight:800;text-align:center}
.rank img{width:56px;height:56px;border-radius:10px;object-fit:cover;background:var(--line)}
.rank .who b{display:block;font-size:1rem}.rank .who span{font-size:.82rem;color:var(--muted)}
.rank .sc{font-weight:800;font-size:1.05rem;white-space:nowrap}
.bars{display:grid;gap:6px}
.bars div{display:grid;grid-template-columns:64px 1fr 64px;gap:8px;align-items:center;font-size:.88rem}
.bars i{display:block;height:10px;border-radius:999px;background:var(--line);overflow:hidden}
.bars i s{display:block;height:100%;border-radius:999px;text-decoration:none}
.title{display:inline-block;margin:2px 4px 2px 0;padding:2px 10px;border-radius:999px;background:#fff3c4;color:#5a4300;font-size:.85rem;font-weight:700}
.empty{background:var(--card);border:1px dashed var(--line);border-radius:14px;padding:24px;text-align:center;color:var(--muted)}
.note{color:var(--muted);font-size:.85rem}
.piece{margin:32px 0 0;padding:16px;border-radius:14px;border:2px dashed var(--gold);background:var(--card);display:grid;gap:4px;text-align:center}
.piece span{color:var(--muted);font-weight:700}.piece>b{font-size:1.5rem}.piece small{color:var(--muted)}
footer{text-align:center;color:var(--muted);font-size:.8rem;padding:24px 16px 40px}
</style></head><body>
<header><div class="bar"><a class="logo" href="/">🪨 갈매미맨</a>
<nav>${navItems.map(([h,l])=>`<a href="${h}"${active===h?' class="on"':''}>${l}</a>`).join('')}</nav>
<form class="search" action="/search" method="get" role="search"><input name="q" value="${esc(q)}" placeholder="갈매미·닉네임 검색 (초성 OK)" aria-label="검색" maxlength="30"><button>검색</button></form></div></header>
<main>${body}${piece(active)}</main>
<footer>갈매미맨 · 카카오톡 채널 챗봇 게임 · v${esc(options.version)}</footer>
</body></html>`;

  const send=(reply:FastifyReply,html:string,maxAge=300)=>reply.type('text/html; charset=utf-8').header('Cache-Control',`public, max-age=${maxAge}`).send(html);
  const limit={config:{rateLimit:{max:600,timeWindow:'1 minute',keyGenerator:()=> 'web'}}};
  const notFound=(reply:FastifyReply,msg:string)=>reply.code(404).type('text/html; charset=utf-8').send(page('없음','',`<div class="empty">${esc(msg)}<br><a href="/">홈으로</a></div>`));

  // ── 홈 ──
  app.get('/',limit,async(_req,reply)=>{
    const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
    const c=huntCoupon();
    const body=`<section class="hero"><div>
<h1>갈매미는 존재한다.</h1>
<p class="lead">돌로 된 갈매미들이 <b>갈매미 섬 항구 수비대</b>에 출근해서 항구를 지켜요. 몸이 돌이라 날지는 못해요. 새우깡 봉지 소리에는 약해요.</p>
<div class="btns"><a class="btn" href="/world">세계관 보기</a><a class="btn ghost" href="/dex">도감 보기</a></div>
</div><img src="${img('images','C1')}" alt="기본 갈매미맨" width="800" height="800"></section>
${c?`<div class="event"><b>🎉 ${esc(c.label)} 이벤트</b><br>이 사이트 어딘가에 <b>쿠폰 조각 ${c.pieces.length}개</b>가 숨어 있어요. 모두 찾아 카톡에 <b>쿠폰 조각들</b>을 보내면 뽑기권 <b>${c.tickets}장</b>! (1인 1회)</div>`:''}
<div class="stats">
<div class="stat"><b>${content.characters.length}종</b>모을 수 있는 갈매미</div>
<div class="stat"><b>${content.recipes.length}가지</b>합체 조합</div>
<div class="stat"><b>${count('HIDDEN')}종</b>서류에 없는 갈매미</div>
<div class="stat"><b>7곳</b>섬의 동네</div></div>
<div class="links">
<a href="/world"><b>📜 세계관</b><span>섬과 동네, 악당 끼룩단</span></a>
<a href="/dex"><b>📚 도감</b><span>${content.characters.length}종의 이야기</span></a>
<a href="/recipes"><b>🧩 조합법</b><span>누구랑 안으면 뭐가 될까</span></a>
<a href="/hidden"><b>❔ 히든 단서</b><span>함께 찾을수록 열리는 단서</span></a>
<a href="/rank"><b>🏆 랭킹</b><span>수비대 근무 평가</span></a>
</div>`;
    return send(reply,page('홈','/',body),60);
  });

  app.get('/world',limit,async(_req,reply)=>send(reply,page('세계관','/world',`<article class="prose">${worldHtml()}</article>`)));

  // ── 도감 목록 ──
  app.get('/dex',limit,async(_req,reply)=>{
    const card=(c:Character)=>{
      if(c.rarity==='HIDDEN')return `<a class="card locked" href="/hidden#h${hiddenIndex(c)}"><img loading="lazy" src="${img('thumbs','unknown')}" alt="???" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>???</h3><p>❔ ${esc(c.hint??'합치기로 찾아요')}</p></div></a>`;
      if(topTiers.has(c.rarity))return `<a class="card locked" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3><p>모습과 이야기는 게임에서 직접 만나면 열려요.</p></div></a>`;
      return `<a class="card" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3><p>${esc(c.introduction)}</p><p class="q">💬 「${esc(c.quote)}」</p></div></a>`;
    };
    const tiers=dexOrder.filter(t=>content.characters.some(c=>c.rarity===t));
    const chips=tiers.map(t=>`<a class="chip" style="color:${tierColor[t]}" href="#${t}">${esc(rarityNames[t])}</a>`).join('');
    const sections=tiers.map(t=>{const list=content.characters.filter(c=>c.rarity===t);
      return `<h2 id="${t}" style="scroll-margin-top:110px">${esc(rarityNames[t])} <small style="color:var(--muted);font-weight:500">${list.length}종</small></h2><div class="grid">${list.map(card).join('')}</div>`;}).join('');
    return send(reply,page('도감','/dex',`<h1>갈매미 도감</h1><p class="lead">${content.characters.length}종의 갈매미. 카드를 누르면 만드는 법과 재료로 쓰이는 곳을 볼 수 있어요.</p><div class="chips">${chips}</div>${sections}`));
  });

  // ── 도감 상세 ──
  app.get<{Params:{id:string}}>('/dex/:id',limit,async(req,reply)=>{
    const c=content.map.get(req.params.id);
    if(!c)return notFound(reply,'그런 갈매미는 없어요.');
    if(c.rarity==='HIDDEN')return reply.redirect(`/hidden#h${hiddenIndex(c)}`);
    const top=topTiers.has(c.rarity),seen=(await data.seenCounts()).get(c.id)??0;
    const recipe=recipeOf(c.id);
    const usedIn=content.recipes.filter(r=>r.materials.includes(c.id));
    const how=recipe?`<div class="box"><h3>🧩 만드는 법</h3>${counts(recipe.materials).map(([id,n])=>chip(id,n)).join('<span class="eq">+</span>')}</div>`:'';
    const used=usedIn.length?`<div class="box"><h3>➡️ 재료로 쓰이는 곳 (${usedIn.length})</h3>${usedIn.map(r=>chip(r.resultId)).join('')}</div>`:'';
    const where=data.source(c.id);
    const body=`<p class="note"><a href="/dex#${c.rarity}">← ${esc(rarityNames[c.rarity])} 도감</a></p>
<div class="detail"><img class="big" src="${img(top?'thumbs':'images',face(c))}" alt="${esc(c.name)}" width="800" height="800">
<div>${badge(c.rarity)}<h1 style="margin-top:.2em">${esc(c.name)}</h1>
${top?'<p class="lead">이 원형의 모습과 이야기는 게임에서 직접 만나면 열려요.</p>':`<p>${esc(c.introduction)}</p><p class="note">💬 「${esc(c.quote)}」</p>`}
<p class="note">🐦 지금까지 <b>${seen}명</b>의 수비대원이 만났어요.${where?` · 얻는 곳: ${esc(where)}`:''}</p>
${how}${used}</div></div>`;
    return send(reply,page(c.name,'/dex',body),60);
  });

  // ── 조합법 ──
  app.get('/recipes',limit,async(_req,reply)=>{
    const order=['UNCOMMON','SPECIAL','RARE','LEGEND','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].filter(t=>content.recipes.some(r=>content.map.get(r.resultId)!.rarity===t));
    const chips=order.map(t=>`<a class="chip" style="color:${tierColor[t]}" href="#r-${t}">${esc(rarityNames[t])}</a>`).join('')+`<a class="chip" style="color:${tierColor.HIDDEN}" href="/hidden">히든 ❔</a>`;
    const sections=order.map(t=>{
      const list=content.recipes.filter(r=>content.map.get(r.resultId)!.rarity===t);
      return `<h2 id="r-${t}" style="scroll-margin-top:110px">${esc(rarityNames[t])} <small style="color:var(--muted);font-weight:500">${list.length}가지</small></h2>`+
        list.map(r=>`<div class="rec"><div>${chip(r.resultId)}</div><div><span class="eq">=</span>${counts(r.materials).map(([id,n])=>chip(id,n)).join('<span class="eq">+</span>')}</div></div>`).join('');
    }).join('');
    const hiddenCount=content.characters.filter(c=>c.rarity==='HIDDEN').length;
    return send(reply,page('조합법','/recipes',`<h1>조합법</h1><p class="lead">누구를 누구랑 꼭 안아 주면 무엇이 될까요? 이름을 누르면 그 갈매미의 상세로 가요. 히든 ${hiddenCount}종의 조합법은 비밀이에요. <a href="/hidden">단서 보러 가기</a></p><div class="chips">${chips}</div>${sections}`));
  });

  // ── 히든 단서 ──
  app.get('/hidden',limit,async(_req,reply)=>{
    const seen=await data.seenCounts(),firsts=await data.hiddenFirsts();
    const hidden=content.characters.filter(c=>c.rarity==='HIDDEN');
    const found=hidden.filter(c=>(seen.get(c.id)??0)>0).length;
    const cards=hidden.map((c,i)=>{
      const n=seen.get(c.id)??0,r=recipeOf(c.id)!,first=firsts.get(c.id);
      const mats=counts(r.materials);
      const tiersLine=counts(r.materials.map(id=>content.map.get(id)!.rarity)).map(([t,k])=>`${emblems[t]}${rarityNames[t]} ${k}`).join(' + ');
      const ini=mats.map(([id,k])=>`${initials(content.map.get(id)!.name)}${k>1?` ×${k}`:''}`).join(' + ');
      const reveal=content.map.get(mats[0][0])!;
      const h3=n>=HINT_INITIALS?`<div class="h"><b>단서 3</b>재료 초성: <span class="mono">${esc(ini)}</span></div>`:`<div class="h lock"><b>🔒 단서 3</b>누군가 처음 찾으면 재료 초성이 열려요.</div>`;
      const h4=n>=HINT_NAME?`<div class="h"><b>단서 4</b>재료 하나는 ${reveal.rarity==='HIDDEN'?'???':`<a href="/dex/${reveal.id}">${esc(reveal.name)}</a>`}</div>`:`<div class="h lock"><b>🔒 단서 4</b>${HINT_NAME}명이 찾으면 재료 하나의 이름이 열려요. (지금 ${n}명)</div>`;
      const who=first?`🏅 최초 발견: ${first.name?`<a href="/p/${encodeURIComponent(first.name)}">${esc(first.name)}</a>`:'이름 없는 갈매미'} · ${esc(DateTime.fromJSDate(first.at).setZone('Asia/Seoul').toFormat('M/d HH:mm'))}`:'아직 아무도 찾지 못했어요. 첫 발견자가 되어 보세요!';
      return `<div class="box" id="h${i+1}" style="scroll-margin-top:110px"><h3>${badge('HIDDEN')} 히든 #${i+1} <small class="note">· ${n}명 발견</small></h3>
<div class="hint"><div class="h"><b>단서 1</b>${esc(c.hint??'합치기로 찾아요')}</div><div class="h"><b>단서 2</b>재료 구성: ${esc(tiersLine)}</div>${h3}${h4}</div>
<p class="note" style="margin:8px 0 0">${who}</p></div>`;
    }).join('');
    const body=`<h1>❔ 서류에 없는 갈매미</h1>
<p class="lead">히든 ${hidden.length}종은 조합목록에 없어요. 카톡에서 <b>합치기</b>로 재료를 직접 골라 안아 줘야 나타나요. 누군가 찾을 때마다 모두의 단서가 하나씩 더 열려요.</p>
<div class="stats"><div class="stat"><b>${found}/${hidden.length}</b>섬에서 발견된 히든</div><div class="stat"><b>1명</b>찾으면 초성 공개</div><div class="stat"><b>${HINT_NAME}명</b>찾으면 재료 이름 공개</div></div>
${cards}`;
    return send(reply,page('히든 단서','/hidden',body),60);
  });

  // ── 랭킹 ──
  app.get<{Querystring:{k?:string}}>('/rank',limit,async(req,reply)=>{
    const tabs=[['dex','도감 수집','DEX','📚'],['top','최상위 보유','TOP','🗿'],['week','이번 주 조합','WEEK','🧩']] as const;
    const tab=tabs.find(t=>t[0]===req.query.k)??tabs[0];
    const nav=`<div class="tabs">${tabs.map(t=>`<a href="/rank?k=${t[0]}"${t===tab?' class="on"':''}>${t[3]} ${t[1]}</a>`).join('')}</div>`;
    const board=await data.ranking(tab[2]);
    const medal=(n:number)=>['🥇','🥈','🥉'][n-1]??String(n);
    // 웹 공개 규칙: 히든 최고 유닛은 ???, 최상위는 이름만(그림은 등급 실루엣)
    const bestOf=(c:Character|null)=>{
      if(!c)return {img:img('thumbs','unknown'),label:'아직 없음'};
      if(c.rarity==='HIDDEN')return {img:img('thumbs','unknown'),label:`${rarityNames[c.rarity]} ???`};
      return {img:img('thumbs',face(c)),label:`${rarityNames[c.rarity]} ${c.name}`};
    };
    const unit=tab[2]==='DEX'?`/${content.characters.length}`:tab[2]==='TOP'?'마리':'회';
    const list=board.rows.map(r=>{const b=bestOf(r.best);
      const name=r.nick?`<a href="/p/${encodeURIComponent(r.name)}">${esc(r.name)}</a>`:esc(r.name);
      return `<li${r.rank===1?' class="top1"':''}><span class="no">${medal(r.rank)}</span><img loading="lazy" src="${b.img}" alt="" width="56" height="56"><span class="who"><b>${name}</b><span>최고: ${esc(b.label)}</span></span><span class="sc">${tab[3]} ${r.score}${unit}</span></li>`;}).join('');
    const when=DateTime.fromJSDate(board.generatedAt).setZone('Asia/Seoul').toFormat('M/d HH:mm');
    const body=`<h1>랭킹</h1>${nav}
${list?`<ol class="rank">${list}</ol>`:'<div class="empty">아직 기록이 없어요. 카톡에서 첫 기록을 만들어 보세요!</div>'}
<p class="note">참여 ${board.total}명 중 상위 ${board.rows.length}명 · ${esc(when)} 기준(30초마다 갱신)${tab[2]==='WEEK'?' · 매주 월요일 0시(한국 시간)에 새로 시작':''}<br>
내 순위는 카톡에서 <b>랭킹</b>, 이름 표시는 <b>닉네임 원하는이름</b> 으로 정할 수 있어요. 닉네임이 있으면 이름을 눌러 프로필을 볼 수 있어요. 관리자 모드 계정은 빠져요.</p>`;
    return send(reply,page('랭킹','/rank',body),30);
  });

  // ── 검색: 갈매미 이름(초성 가능) · 닉네임 ──
  app.get<{Querystring:{q?:string}}>('/search',limit,async(req,reply)=>{
    const q=String(req.query.q??'').trim().slice(0,30);
    if(!q)return send(reply,page('검색','',`<h1>검색</h1><p class="lead">갈매미 이름이나 닉네임을 찾아요. 초성도 돼요. 예: <a href="/search?q=금갑">금갑</a> · <a href="/search?q=ㄱㄱ">ㄱㄱ</a> · <a href="/search?q=갈매미">갈매미</a></p>`,q),60);
    const plain=q.replace(/\s+/g,''),ini=isInitials(plain);
    const units=content.characters.filter(c=>c.rarity!=='HIDDEN'&&(ini?initials(c.name.replace(/\s+/g,'')).includes(plain):c.name.replace(/\s+/g,'').includes(plain))).slice(0,40);
    const players=ini?[]:await data.searchPlayers(q);
    const unitCards=units.map(c=>`<a class="card${topTiers.has(c.rarity)?' locked':''}" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3></div></a>`).join('');
    const body=`<h1>“${esc(q)}” 검색</h1>
<h2>🧑‍✈️ 수비대원 ${players.length}명</h2>${players.length?players.map(n=>`<a class="uc" href="/p/${encodeURIComponent(n)}">🪨 ${esc(n)}</a>`).join(''):`<p class="note">${ini?'초성 검색은 갈매미 이름에만 돼요.':'찾는 닉네임이 없어요. 닉네임을 정한 수비대원만 검색돼요.'}</p>`}
<h2>🐦 갈매미 ${units.length}종</h2>${units.length?`<div class="grid">${unitCards}</div>`:'<p class="note">찾는 갈매미가 없어요. 히든은 검색되지 않아요. <a href="/hidden">히든 단서 보기</a></p>'}`;
    return send(reply,page(`${q} 검색`,'',body,q),60);
  });

  // ── 프로필 ──
  app.get<{Params:{name:string}}>('/p/:name',limit,async(req,reply)=>{
    const name=String(req.params.name).slice(0,20);
    const p=await data.profile(name);
    if(!p)return notFound(reply,'그런 닉네임의 수비대원이 없어요.');
    const bestImg=p.best?img('thumbs',face(p.best)):img('thumbs','unknown');
    const bestLabel=!p.best?'아직 없음':p.best.rarity==='HIDDEN'?'히든 ???':`${rarityNames[p.best.rarity]} ${p.best.name}`;
    const bars=dexOrder.map(t=>{const total=content.characters.filter(c=>c.rarity===t).length,got=p.byTier.get(t)??0;
      return `<div><span>${esc(rarityNames[t])}</span><i><s style="width:${Math.round(got/total*100)}%;background:${tierColor[t]}"></s></i><span>${got}/${total}</span></div>`;}).join('');
    const rk=(r:WebProfile['dexRank'],u:string)=>r?`<b>${r.rank}위</b> / ${r.total}명 · ${r.score}${u}`:'기록 없음';
    const body=`<div class="detail"><img class="big" src="${bestImg}" alt="" width="480" height="480" style="max-width:280px">
<div><h1 style="margin:.1em 0">🪨 ${esc(p.nickname)}</h1>
<p class="note">최고 갈매미: ${esc(bestLabel)} · 입사 ${esc(DateTime.fromJSDate(p.joined).setZone('Asia/Seoul').toFormat('yyyy. M. d.'))}</p>
${p.titles.length?`<p>${p.titles.map(t=>`<span class="title">🏆 ${esc(t)}</span>`).join('')}</p>`:''}
<div class="stats"><div class="stat"><b>${p.dex}/${content.characters.length}</b>도감</div><div class="stat"><b>${p.firsts}개</b>히든 최초 발견</div></div>
<div class="box"><h3>🏆 순위</h3>📚 도감 ${rk(p.dexRank,'종')}<br>🗿 최상위 ${rk(p.topRank,'마리')}<br>🧩 이번 주 조합 ${rk(p.weekRank,'회')}</div>
</div></div>
<h2>등급별 수집</h2><div class="bars box">${bars}</div>`;
    return send(reply,page(p.nickname,'',body),60);
  });
}
