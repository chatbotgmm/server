// 공개 웹페이지: 홈 · 이벤트 · 수다방 · 세계관 · 도감(+상세) · 조합법 · 히든 단서 · 랭킹 · 검색 · 프로필
// 누구나 보는 페이지라 스포일러를 가립니다.
// - 히든: 이름·그림·이야기 대신 ??? 와 단서(발견한 사람이 늘수록 더 열림)
// - 최상위(제한~불멸): 이름만, 그림은 등급 실루엣, 이야기는 가림
// 세계관 글은 data/world.md(마크다운)를 고치면 바로 반영됩니다.
import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { marked } from 'marked';
import { DateTime } from 'luxon';
import type { Character, Content } from './content.js';
import { rarityNames, topTiers } from './content.js';
import { emblems } from './acquisition.js';
import { tutorialSteps } from './game.js';

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

export interface RankRow{rank:number;name:string;nick:boolean;badge?:string;score:number;best:Character|null}
export interface WebProfile{nickname:string;badge?:string;joined:Date;dex:number;byTier:Map<string,number>;best:Character|null;dexRank:{rank:number;total:number;score:number}|null;topRank:{rank:number;total:number;score:number}|null;weekRank:{rank:number;total:number;score:number}|null;titles:string[];firsts:number}
export interface WebComment{id:number;text:string;likes:number;at:Date;name:string;badge?:string;characterId:string}
export interface WebData{
  unitComments:(id:string)=>Promise<WebComment[]>;
  recentComments:(take?:number)=>Promise<WebComment[]>;
  topComments:(take?:number)=>Promise<WebComment[]>;
  like:(id:number,visitor:string)=>Promise<{likes:number;already:boolean}|null>;
  ranking:(kind:'DEX'|'TOP'|'WEEK')=>Promise<{total:number;generatedAt:Date;rows:RankRow[]}>;
  seenCounts:()=>Promise<Map<string,number>>;
  hiddenFirsts:()=>Promise<Map<string,{name:string|null;at:Date}>>;
  searchPlayers:(q:string)=>Promise<string[]>;
  profile:(nickname:string)=>Promise<WebProfile|null>;
  source:(id:string)=>string;
}
export interface WebOptions{root:string;stamp:(path:string)=>string;version:string;data?:WebData;salt?:string}

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
  // 조합 계보도: 재료를 거슬러 내려감. 히든 재료는 ??? 로 멈춤(스포일러), 깊은 단계는 접어 둠
  const expandable=(id:string)=>{const c=content.map.get(id)!;return c.rarity!=='HIDDEN'&&Boolean(recipeOf(id));};
  const tree=(id:string,n:number,depth:number):string=>{
    const head=chip(id,n);
    if(!expandable(id)||depth>12)return `<li class="leaf">${head}</li>`;
    const kids=counts(recipeOf(id)!.materials).map(([m,k])=>tree(m,k,depth+1)).join('');
    return `<li><details${depth<1?' open':''}><summary>${head}</summary><ul>${kids}</ul></details></li>`;
  };
  // 흔함 기준 총 필요 수: 더 내려갈 수 없는 재료(흔함·특수함·히든 등)까지 곱해서 합산
  const baseNeeds=(id:string,mult:number,acc:Map<string,number>)=>{
    if(!expandable(id)){acc.set(id,(acc.get(id)??0)+mult);return acc;}
    for(const [m,k] of counts(recipeOf(id)!.materials))baseNeeds(m,mult*k,acc);
    return acc;
  };
  const empty:WebData={unitComments:async()=>[],recentComments:async()=>[],topComments:async()=>[],like:async()=>null,ranking:async()=>({total:0,generatedAt:new Date(),rows:[]}),seenCounts:async()=>new Map(),hiddenFirsts:async()=>new Map(),searchPlayers:async()=>[],profile:async()=>null,source:()=>''};
  const data=options.data??empty;

  // 홈페이지 창설 쿠폰 조각: 세계관·조합법·히든 페이지 맨 아래에 하나씩
  const now=()=>new Date();
  const huntCoupon=()=>content.economy.coupons.find(c=>c.pieces.length&&(!c.from||new Date(c.from)<=now())&&(!c.to||now()<new Date(c.to)));
  const piecePages=['/world','/recipes','/hidden'];
  const piece=(path:string)=>{const c=huntCoupon(),i=piecePages.indexOf(path);if(!c||i<0||!c.pieces[i])return '';
    return `<aside class="piece"><span>🧩 쿠폰 조각 ${i+1}/${c.pieces.length}</span><b>「${esc(c.pieces[i])}」</b><small>조각을 순서대로 모아 카톡에 <b>쿠폰 조각1조각2조각3</b> 처럼 보내 보세요.</small></aside>`;};

  // data/world.md: 파일이 바뀌면 다시 읽음
  let world={mtime:0,html:''};
  // data/*.md 글(고치면 서버를 다시 켜지 않아도 반영)
  const mdCache=new Map<string,{mtime:number;html:string}>();
  const mdHtml=(file:string,empty:string)=>{
    const path=resolve(options.root,'data',file);
    try{
      const m=statSync(path).mtimeMs,c=mdCache.get(file);
      if(c?.mtime===m)return c.html;
      const html=marked.parse(readFileSync(path,'utf8'),{async:false}) as string;
      mdCache.set(file,{mtime:m,html});return html;
    }catch{return empty;}
  };
  const worldHtml=()=>{
    try{
      const path=resolve(options.root,'data/world.md');
      const m=statSync(path).mtimeMs;
      if(m!==world.mtime)world={mtime:m,html:marked.parse(readFileSync(path,'utf8'),{async:false}) as string};
    }catch{world={mtime:0,html:'<p>세계관 글을 준비 중이에요.</p>'};}
    return world.html;
  };

  // 자동완성: 입력하면 갈매미(초성 가능)·닉네임 추천. 갈매미 목록은 /search-index.json 을 한 번 받아 씀
  const suggestJs=`(()=>{const f=document.querySelector('form.search');if(!f)return;const i=f.q,box=f.querySelector('.sug');let idx=null,t,n=0;
const CHO='${CHO}',ini=s=>[...s].map(ch=>{const c=ch.charCodeAt(0)-44032;return c>=0&&c<11172?CHO[Math.floor(c/588)]:ch}).join(''),
e=s=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const load=async()=>idx||(idx=await (await fetch('/search-index.json')).json());
async function run(){const my=++n,q=i.value.replace(/\\s+/g,'');if(!q){box.hidden=true;return}
const list=await load(),only=/^[ㄱ-ㅎ]+$/.test(q),units=list.filter(u=>(only?ini(u.n.replace(/\\s+/g,'')):u.n.replace(/\\s+/g,'')).includes(q)).slice(0,6);
let ps=[];if(!only){try{ps=await (await fetch('/api/players?q='+encodeURIComponent(q))).json()}catch(_){}}
if(my!==n)return;
box.innerHTML=units.map(u=>'<a href="/dex/'+u.i+'"><img src="'+u.p+'" alt="">'+e(u.n)+'<small style="color:'+u.c+'">'+e(u.t)+'</small></a>').join('')
+ps.slice(0,4).map(p=>'<a href="/p/'+encodeURIComponent(p)+'"><img class="gi" src="/gull.png" alt=""> '+e(p)+'<small>수비대원</small></a>').join('')
+'<a href="/search?q='+encodeURIComponent(i.value.trim())+'">🔍 “'+e(i.value.trim())+'” 전체 검색</a>';box.hidden=false}
i.addEventListener('input',()=>{clearTimeout(t);t=setTimeout(run,120)});i.addEventListener('focus',()=>{load();if(i.value)run()});
document.addEventListener('click',ev=>{if(!f.contains(ev.target))box.hidden=true});
i.addEventListener('keydown',ev=>{const a=[...box.querySelectorAll('a')];if(box.hidden||!a.length)return;let k=a.findIndex(x=>x.classList.contains('on'));
if(ev.key==='ArrowDown'||ev.key==='ArrowUp'){ev.preventDefault();a[k]?.classList.remove('on');k=ev.key==='ArrowDown'?(k+1)%a.length:(k<=0?a.length-1:k-1);a[k].classList.add('on')}
else if(ev.key==='Enter'&&k>=0){ev.preventDefault();location.href=a[k].href}else if(ev.key==='Escape')box.hidden=true});})();`;
  // 머리말 🔍(검색 줄 펼치기)·폰 아래 「더보기」
  const headerJs=`(()=>{const sr=document.getElementById('srow');document.querySelectorAll('a.sbtn').forEach(sb=>sb.addEventListener('click',ev=>{ev.preventDefault();sr.hidden=!sr.hidden;if(!sr.hidden){scrollTo({top:0});sr.querySelector('input').focus();}}));
fetch('/my/api/who',{headers:{'x-gmm':'1'}}).then(r=>r.ok?r.json():null).then(j=>{if(!j)return;
document.querySelectorAll('a.lgn').forEach(a=>{a.href='/my#units';a.title=(j.nickname||'내 계정')+' · 🎟 '+j.credits+'장';const i=a.querySelector('i'),b=a.querySelector('b'),s=a.querySelector('span');
const g='<img class="gi" src="/gull.png" alt="">';if(i){i.innerHTML=g;if(b){b.textContent=a.dataset.l;if(s)s.textContent=a.dataset.s}else{a.lastChild.textContent=a.dataset.l}}else a.innerHTML=g+' '+a.dataset.l;});}).catch(()=>{});
const mb=document.getElementById('mbtn'),mo=document.getElementById('more');mb&&mb.addEventListener('click',e=>{e.stopPropagation();mo.hidden=!mo.hidden;});document.addEventListener('click',e=>{if(mo&&!mo.hidden&&!mo.contains(e.target))mo.hidden=true;});})();`;
  // 댓글 한 줄: 닉네임(프로필 링크)·시간·좋아요 버튼. unit=true면 어느 유닛 댓글인지 칩도
  const when=(d:Date)=>DateTime.fromJSDate(d).setZone('Asia/Seoul').toFormat('M/d HH:mm');
  const commentItem=(c:WebComment,unit=false)=>`<li class="cm"><div class="cm-h"><a href="/p/${encodeURIComponent(c.name)}"><b>${c.badge?`${esc(c.badge)} `:''}${esc(c.name)}</b></a> <span class="note">${esc(when(c.at))}</span>${unit?` ${chip(c.characterId)}`:''}</div>
<p>${esc(c.text)}</p><button class="like" type="button" data-id="${c.id}" aria-label="좋아요">👍 <b>${c.likes}</b></button></li>`;
  const likeJs=`document.addEventListener('click',async ev=>{const b=ev.target.closest&&ev.target.closest('button.like');if(!b||b.disabled)return;b.disabled=true;
try{const r=await fetch('/api/comments/'+b.dataset.id+'/like',{method:'POST'});if(r.ok){const j=await r.json();b.querySelector('b').textContent=j.likes;b.classList.add('on');}else b.disabled=false}catch(_){b.disabled=false}});`;
  const howToComment=(name?:string)=>`<p class="note">💬 카톡에서 <span class="mono">댓글 ${esc(name??'갈매미이름')} 하고 싶은 말</span> 로 남겨요. 만나 본 갈매미에게만 쓸 수 있고, 닉네임이 함께 보여요.</p>`;
  // 메뉴: 자주 쓰는 순서. 모바일 하단 탭은 6칸이라 히든은 조합법 탭에 묶음(히든 페이지에서 조합법 탭이 켜짐)
  // 큰 메뉴 6개: 뽑기 · 조합 · 도감 · 탐험대 편성 · 검색 · 로그인(뽑기·조합·탐험대는 「내 갈매미」 탭으로 바로)
  // 나머지(홈·이벤트·조합법·히든·수다방·랭킹·세계관)는 ☰ 더보기
  const mainItems=[['/my#draw','뽑기','🥚'],['/my#craft','조합','🧩'],['/dex','도감','📚'],['/my#party','탐험대 편성','🧭']];
  const tabItems=mainItems.map(([h,l,i])=>[h,l==='탐험대 편성'?'탐험대':l,i]);
  const moreItems=[['/','홈','🏝'],['/event','이벤트','🎁'],['/recipes','조합법','📜'],['/hidden','히든 단서','❔'],['/talk','수다방','💬'],['/rank','랭킹','🏆'],['/world','세계관','🗺']];
  const tabOn=(h:string,active:string)=>active===h||(h==='/recipes'&&active==='/hidden');
  // 조합법·히든 페이지 위 전환 탭
  const recipeTabs=(active:string)=>`<div class="tabs"><a href="/recipes"${active==='/recipes'?' class="on"':''}>🧩 조합법</a><a href="/hidden"${active==='/hidden'?' class="on"':''}>❔ 히든 단서</a></div>`;
  // 공유 미리보기(카톡·SNS): 절대 주소가 필요해 economy.homepage 를 씀
  const site=(content.economy.homepage??'').replace(/\/$/,'');
  const channel=content.economy.kakaoChannel;
  // 카톡 채널 버튼: 말풍선 아이콘(이모지 대신 단색 그림) + 글자. big은 홈·이벤트 본문용
  const bubble='<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M12 3C6.5 3 2 6.6 2 11c0 2.8 1.8 5.3 4.6 6.7l-.9 3.4c-.1.4.3.7.6.5l4-2.7c.6.1 1.1.1 1.7.1 5.5 0 10-3.6 10-8S17.5 3 12 3z"/></svg>';
  const playBtn=(big=false)=>channel?`<a class="play${big?' big':''}" href="${esc(channel)}" target="_blank" rel="noopener">${bubble}<span>카톡에서 플레이</span></a>`:'';
  const logo=()=>exists('logo')?`<img src="${img('images','logo')}" alt="갈매미맨" height="40" style="height:40px;width:auto;vertical-align:middle">`:'<img class="gi" src="/gull.png" alt=""> 갈매미맨';
  // 사이트 이름(구글 검색 결과 「사이트 이름」): 홈 제목·og:site_name·WebSite 구조화 데이터를 같은 이름으로
  const SITE_NAME='갈매미맨',SITE_ALT=['갈매미맨 수비대'];
  interface Og{image?:string;desc?:string}
  const page=(title:string,active:string,body:string,q='',og:Og={})=>`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${active==='/'?SITE_NAME:`${esc(title)} · ${SITE_NAME}`}</title>
<meta name="description" content="${esc(og.desc??'돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기')}">
<meta property="og:type" content="website"><meta property="og:site_name" content="${SITE_NAME}"><meta property="og:description" content="${esc(og.desc??'돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기')}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&family=Gowun+Dodum&display=swap" rel="stylesheet">
<link rel="icon" type="image/png" href="/favicon.png"><link rel="apple-touch-icon" href="/icon-192.png"><link rel="manifest" href="/manifest.webmanifest"><meta name="theme-color" content="#164b80"><meta name="apple-mobile-web-app-capable" content="yes"><meta name="mobile-web-app-capable" content="yes">
<meta property="og:title" content="${active==='/'?SITE_NAME:`${esc(title)} · ${SITE_NAME}`}"><meta property="og:image" content="${site}${og.image??img('images','C1')}">
<style>
:root{--c1:#2a6fc0;--c2:#d9483b;--c3:#0f8a7b;--c4:#7a4fd0;--c5:#b06f00;--c6:#c93f7c;--bg:#f7f0e3;--card:#fffdf8;--text:#2d2a26;--muted:#6f675c;--line:#e8dcc6;--edge:#d8c7a6;--accent:#1d5b94;--accent-soft:#e2edf7;--gold:#d9a400;
 --sea1:#2b7cc4;--sea2:#164b80;--sky:#9fd3f5;--shadow:0 2px 0 var(--edge),0 10px 24px rgba(80,55,20,.07)}
@media (prefers-color-scheme:dark){:root{--c1:#7db4ff;--c2:#ff8a7a;--c3:#5fd3c3;--c4:#b9a0ff;--c5:#f2b84b;--c6:#ff92c2;--bg:#121820;--card:#1c242e;--text:#ececec;--muted:#a3a9b1;--line:#2d3845;--edge:#0c1117;--accent:#8fc1f0;--accent-soft:#22324a;
 --sea1:#1f5d99;--sea2:#0e2c4d;--sky:#1d4a78;--shadow:0 2px 0 var(--edge),0 10px 24px rgba(0,0,0,.25)}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
/* 글꼴: 제목·버튼·메뉴·이름은 Black Han Sans, 본문은 Gowun Dodum(둘 다 굵기 1개뿐이라 강조는 색으로) */
h1,h2,h3,.logo,.stat b,.links a b,.today h3,header nav a,.mylink,.btn,.play,.tabs a,.chip,.badge,.tabbar a,.hstats,.more a,.tag,.rank .no,.rank .sc,.mini .sc,.title,.like,form.search button{font-family:"Black Han Sans","Apple SD Gothic Neo","Malgun Gothic",sans-serif;font-weight:400;letter-spacing:.01em}
body{font-synthesis:none}
/* 갈매미 아이콘: 글자 크기에 맞춰 이모지처럼 */
img.gi{width:1.25em;height:1.25em;vertical-align:-.28em;display:inline-block}
.tabbar i img.gi,.links a i img.gi{width:1.35em;height:1.35em;vertical-align:middle}
.hero img.gi,.today img.gi{width:1.25em;height:1.25em;max-width:none;border-radius:0;box-shadow:none;justify-self:auto}
p b,p strong,li b,li strong,.prose td b,.prose td strong{color:var(--accent)}
.event b,.event strong{color:#a0461a}
body{background-image:radial-gradient(var(--line) 1px,transparent 1.2px),linear-gradient(180deg,transparent 0,transparent 70%,rgba(31,78,121,.06) 100%);background-size:22px 22px,100% 100%;background-attachment:fixed}
.tabbar,.more{display:none}
/* 더보기 메뉴: 폰은 아래 탭 위에, PC는 머리말 ☰ 아래에 뜸 */
.more{position:fixed;z-index:31;background:var(--card);border:1px solid var(--line);border-radius:18px;box-shadow:0 12px 40px rgba(0,0,0,.25);padding:8px;grid-template-columns:1fr 1fr;gap:6px}
.more:not([hidden]){display:grid}
.more a{display:flex;align-items:center;gap:8px;padding:12px;border-radius:12px;background:var(--bg);text-decoration:none;color:var(--text)}
.more a.on{background:var(--accent-soft);color:var(--accent)}
@media (min-width:900px){.more{right:max(16px,calc(50vw - 524px));top:60px;width:320px}}
@media (max-width:899px){
 header nav{display:none}
 .more{left:8px;right:8px;top:62px}
 .tabbar{display:grid;grid-template-columns:repeat(6,1fr);position:fixed;left:0;right:0;bottom:0;z-index:30;background:var(--card);border-top:1px solid var(--line);padding:4px 2px calc(4px + env(safe-area-inset-bottom))}
 .tabbar a{display:flex;flex-direction:column;align-items:center;gap:1px;text-decoration:none;color:var(--muted);font-size:.72rem;white-space:nowrap;padding:4px 0;border-radius:12px}
 .tabbar i{font-style:normal;font-size:1.3rem;line-height:1.2}
 .tabbar .on{color:var(--accent);background:var(--accent-soft)}
 main{padding-bottom:96px}
 footer{padding-bottom:96px}
}
.play{display:inline-flex;align-items:center;justify-content:center;gap:6px;padding:8px 14px;border-radius:999px;background:#fee500;color:#191600;text-decoration:none;font-size:.95rem;white-space:nowrap;box-shadow:0 3px 0 #c9b200}
.play:active,.btn:active{transform:translateY(2px);box-shadow:none}
.play svg{flex:none}.play:hover{background:#fada0a}
.mylink{flex:none;display:inline-flex;align-items:center;padding:7px 12px;border-radius:999px;background:rgba(255,255,255,.16);color:#fff;text-decoration:none;font-size:.9rem;white-space:nowrap}
.mylink:hover{background:rgba(255,255,255,.28)}
header .play{flex:none;box-shadow:0 3px 0 #c9b200}
@media (max-width:899px){.mylink{display:none}header .play span{display:none}header .play{padding:9px 11px}}
.play.big{padding:13px 24px;font-size:1.08rem;box-shadow:0 4px 0 #c9b200,0 8px 20px rgba(254,229,0,.35)}
body{margin:0;background:var(--bg);color:var(--text);font-family:"Gowun Dodum","Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif;line-height:1.7;word-break:keep-all;overflow-wrap:anywhere}
a{color:var(--accent)}
/* 머리말: 바다색 한 줄(로고 · 메뉴 · 검색 · 로그인 · 카톡 플레이). 검색은 🔍로 펼침 */
header{background:linear-gradient(180deg,var(--sea1),var(--sea2));color:#fff;position:sticky;top:0;z-index:25;box-shadow:0 2px 0 rgba(0,0,0,.12)}
.bar{max-width:1080px;margin:0 auto;padding:8px 16px;display:flex;align-items:center;gap:8px}
.logo{font-size:1.1rem;text-decoration:none;color:#fff;flex:none;filter:drop-shadow(0 2px 0 rgba(0,0,0,.18))}
header nav{display:flex;gap:2px;flex:1;min-width:0}
header nav a{text-decoration:none;color:rgba(255,255,255,.88);padding:6px 10px;border-radius:999px;font-size:.95rem;white-space:nowrap}
header nav a:hover{background:rgba(255,255,255,.14)}
header nav a.on{background:#fff;color:var(--sea2);box-shadow:0 2px 0 rgba(0,0,0,.15)}
header nav .sbtn,header nav .lbtn{background:rgba(255,255,255,.14)}
header nav .sbtn{margin-left:auto}
.hbtn{flex:none;display:inline-grid;place-items:center;width:38px;height:38px;border-radius:50%;border:0;background:rgba(255,255,255,.16);color:#fff;font-size:1.05rem;cursor:pointer;text-decoration:none}
.hbtn:hover{background:rgba(255,255,255,.28)}
.sp{flex:1}
@media (min-width:900px){.sp{display:none}}
@media (max-width:899px){header nav{display:none}}
.searchrow{max-width:1080px;margin:0 auto;padding:0 16px 10px}
.searchrow[hidden]{display:none}
form.search{display:flex;gap:6px;min-width:0}
form.search input{flex:1;min-width:0;padding:10px 14px;border-radius:999px;border:0;background:var(--card);color:var(--text);font:inherit;font-size:1rem}
form.search button{padding:10px 16px;border-radius:999px;border:0;background:#fee500;color:#191600;font:inherit;box-shadow:0 3px 0 #c9b200;cursor:pointer}
form.search{position:relative}
.sug{position:absolute;left:0;right:0;top:calc(100% + 6px);z-index:20;background:var(--card);border:1px solid var(--line);border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.18);padding:6px;max-height:70vh;overflow:auto}
.sug a,.sug span{display:flex;align-items:center;gap:10px;padding:7px 8px;border-radius:10px;text-decoration:none;color:var(--text)}
.sug a:hover,.sug a.on{background:var(--accent-soft)}
.sug img{width:32px;height:32px;border-radius:8px;object-fit:cover}
.sug small{margin-left:auto;font-size:.75rem;font-weight:800}
.sug span{color:var(--muted);font-weight:500}
main{max-width:1080px;margin:0 auto;padding:20px 16px 48px}
h1{font-size:1.8rem;margin:.4em 0 .5em}h2{font-size:1.3rem;margin:1.8em 0 .7em}
main>h2::after,section>h2::after{content:'';display:block;width:40px;height:4px;border-radius:4px;background:var(--gold);margin-top:6px}
blockquote{margin:0 0 1em;padding:10px 14px;border-left:4px solid var(--accent);background:var(--accent-soft);border-radius:0 8px 8px 0;color:var(--text)}
blockquote p{margin:0}
.prose table{width:100%;border-collapse:collapse;background:var(--card);border-radius:10px;overflow:hidden;margin:.5em 0 1em;font-size:.95rem}
.prose th,.prose td{padding:9px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
.prose th{background:var(--accent-soft)}.prose td:first-child,.prose th:first-child{white-space:nowrap}.prose thead:empty,.prose th:empty{display:none}
.prose ol,.prose ul{padding-left:1.3em}.prose li{margin:.35em 0}
/* 홈 배너: 하늘 → 바다, 아래 물결. 안에 소개·버튼·숫자, 오른쪽 오늘의 갈매미 */
.hero{display:grid;gap:20px;align-items:center;grid-template-columns:1fr;position:relative;overflow:hidden;border-radius:24px;padding:26px 22px 54px;
 background:linear-gradient(180deg,var(--sky) 0%,#d7eefb 70%,#7cc0ec 70.3%,var(--sea1) 100%);box-shadow:var(--shadow)}
@media (prefers-color-scheme:dark){.hero{background:linear-gradient(180deg,#0f2a47 0%,#173d63 70%,#1a4e80 70.3%,#0d2b4b 100%)}}
.hero .btns .btn{white-space:nowrap}
@media (max-width:719px){.hero{background:linear-gradient(180deg,var(--sky) 0%,#d7eefb 52%,#7cc0ec 52.3%,var(--sea1) 100%)}.hero h1{font-size:1.9rem}.hero .btns .btn{padding:11px 12px}}
@media (max-width:719px) and (prefers-color-scheme:dark){.hero{background:linear-gradient(180deg,#0f2a47 0%,#173d63 52%,#1a4e80 52.3%,#0d2b4b 100%)}}
.hero::after{content:'';position:absolute;left:0;right:0;bottom:0;height:46px;background:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 46' preserveAspectRatio='none'%3E%3Cpath d='M0 20 Q15 8 30 20 T60 20 T90 20 T120 20 V46 H0Z' fill='%23f2e3c4'/%3E%3C/svg%3E") repeat-x;background-size:120px 46px}
@media (prefers-color-scheme:dark){.hero::after{filter:brightness(.25)}}
.hero>*{position:relative;z-index:1}
.hero h1{font-size:2.3rem;margin:.1em 0 .3em;color:#123a63;text-shadow:0 2px 0 rgba(255,255,255,.6)}
@media (prefers-color-scheme:dark){.hero h1{color:#fff;text-shadow:none}}
.hero .lead{color:#21476d;background:rgba(255,255,255,.55);border-radius:14px;padding:10px 14px;margin:0}
@media (prefers-color-scheme:dark){.hero .lead{color:#dce9f6;background:rgba(0,0,0,.25)}}
.hero .lead b{color:#0d5a9e}
@media (prefers-color-scheme:dark){.hero .lead b{color:#9fd0ff}.hstats span{background:rgba(255,255,255,.14);color:#e6f0fa}}
.hstats{display:flex;gap:8px;flex-wrap:wrap;margin-top:14px}
.hstats span{background:rgba(255,255,255,.85);color:#1b3d60;border-radius:999px;padding:4px 12px;font-size:.88rem;box-shadow:0 2px 0 rgba(0,0,0,.08)}
.hstats b{color:var(--c2)}
@media (min-width:720px){.hero{grid-template-columns:1.25fr .75fr;padding:34px 34px 60px}}
.hero img{width:100%;max-width:420px;height:auto;justify-self:center;border-radius:16px;box-shadow:0 6px 24px rgba(0,0,0,.15)}
.lead{font-size:1.05rem;color:var(--muted)}
.btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px;align-items:center}
.btn{display:inline-flex;align-items:center;justify-content:center;padding:11px 18px;border-radius:999px;background:var(--card);color:var(--text);border:1.5px solid var(--edge);text-decoration:none;font-size:1rem;box-shadow:0 3px 0 var(--edge)}
.btn:hover{color:var(--accent)}
@media (max-width:719px){.hero .btns .play.big{flex:1 1 100%}.hero .btns .btn{flex:1 1 0}}
.event{margin:18px 0;padding:14px 16px;border-radius:14px;background:linear-gradient(135deg,#fff3c4,#ffe0ef);color:#3b2a00;border:1px solid #f0d27a}
.event b{font-size:1.05rem}.event a{color:#7a4300;font-weight:800}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:20px 0}
.stat{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:12px 14px;box-shadow:var(--shadow)}
.stat b{display:block;font-size:1.4rem}
/* 바로가기: 앱 아이콘처럼(아이콘 동그라미 + 이름 + 짧은 설명) */
.links{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
@media (min-width:900px){.links{grid-template-columns:repeat(7,1fr)}.links.six{grid-template-columns:repeat(6,1fr)}}
@media (max-width:899px){.links.six{grid-template-columns:repeat(3,1fr)}}
.links a{display:flex;flex-direction:column;align-items:center;text-align:center;gap:4px;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:14px 6px;text-decoration:none;color:var(--text);box-shadow:var(--shadow);transition:transform .15s}
.links a:hover{transform:translateY(-3px)}
.links a i{font-style:normal;font-size:1.6rem;width:52px;height:52px;border-radius:50%;display:grid;place-items:center;background:var(--accent-soft)}
.links a b{display:block;font-size:1rem}.links a span{color:var(--muted);font-size:.78rem;line-height:1.35}
@media (max-width:719px){.links a span{display:none}.links a{padding:10px 4px}}
.chips{display:flex;gap:6px;flex-wrap:wrap;margin:6px 0 4px;position:sticky;top:54px;z-index:4;background:var(--bg);padding:8px 0}
.chip{text-decoration:none;font-size:.85rem;font-weight:700;padding:5px 10px;border-radius:999px;border:1.5px solid currentColor;background:var(--card)}
.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}
@media (min-width:600px){.grid{grid-template-columns:repeat(auto-fill,minmax(170px,1fr))}}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;overflow:hidden;display:flex;flex-direction:column;text-decoration:none;color:var(--text);box-shadow:var(--shadow);transition:transform .15s}
a.card:hover{transform:translateY(-3px)}
.card img{width:100%;height:auto;aspect-ratio:1;object-fit:cover;display:block;background:linear-gradient(135deg,var(--line),var(--bg))}
/* 도감 카드: 소개는 2줄까지(전체는 상세에서) */
.grid .card .in>p:not(.q){display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.grid .card .q{display:none}
.card .in{padding:10px 12px 12px}
.badge{display:inline-block;font-size:.75rem;font-weight:800;color:#fff;padding:2px 8px;border-radius:999px}
.card h3{margin:6px 0 4px;font-size:1rem}
.card p{margin:4px 0;font-size:.88rem}
.card .q,.card .r{color:var(--muted)}.card .r{font-size:.8rem}
.card.locked .in p{color:var(--muted)}
.uc{display:inline-flex;align-items:center;gap:6px;padding:3px 10px 3px 3px;margin:3px 4px 3px 0;border-radius:999px;background:var(--card);border:1px solid var(--line);text-decoration:none;color:var(--text);font-size:.9rem;font-weight:600}
.uc img{width:28px;height:28px;border-radius:50%;object-fit:cover}
/* 조합법: PC는 2단, 한 칸 = 결과 한 줄 + 재료 한 줄 */
.recs{display:grid;gap:8px;grid-template-columns:1fr}
@media (min-width:900px){.recs{grid-template-columns:1fr 1fr}}
.rec{display:flex;flex-wrap:wrap;align-items:center;gap:0 2px;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:8px 10px;box-shadow:var(--shadow)}
.rec>div{display:contents}
.rec .eq{color:var(--muted);font-weight:800;margin:0 4px}
.detail{display:grid;gap:18px;grid-template-columns:1fr}
@media (min-width:720px){.detail{grid-template-columns:360px 1fr}}
.detail img.big{width:100%;height:auto;border-radius:16px;box-shadow:0 6px 24px rgba(0,0,0,.15)}
.box{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:14px 16px;margin:10px 0;box-shadow:var(--shadow)}
.box h3{margin:0 0 6px;font-size:1rem}
.hint{display:grid;gap:6px}
.hint .h{padding:8px 10px;border-radius:10px;background:var(--bg);border:1px dashed var(--line)}
.hint .h.lock{color:var(--muted)}
.hint .h b{margin-right:6px}
.mono{font-family:ui-monospace,Menlo,Consolas,monospace;letter-spacing:.08em}
.tabs{display:flex;gap:6px;margin:4px 0 14px;flex-wrap:wrap}
.tabs a{text-decoration:none;padding:7px 14px;border-radius:999px;border:1.5px solid var(--edge);color:var(--muted);background:var(--card);box-shadow:0 2px 0 var(--edge)}
.tabs a.on{border-color:var(--accent);color:#fff;background:var(--accent);box-shadow:0 2px 0 var(--sea2)}
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
.today{display:grid;grid-template-columns:120px 1fr;gap:12px;align-items:center;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:10px;text-decoration:none;color:var(--text);box-shadow:0 6px 24px rgba(0,0,0,.08)}
@media (min-width:720px){.today{grid-template-columns:1fr;max-width:300px;justify-self:center;transform:rotate(2deg);border:4px solid #fff;transition:transform .2s}.today:hover{transform:rotate(0) scale(1.02)}}
.today img{width:100%;height:auto;border-radius:12px}
.today small{color:var(--gold);font-weight:800}.today h3{margin:2px 0;font-size:1.3rem}.today p{margin:0;font-size:.88rem;color:var(--muted)}
.homegrid{display:grid;gap:10px;grid-template-columns:1fr}
@media (min-width:720px){.homegrid{grid-template-columns:1fr 1fr}}
.mini{list-style:none;margin:0 0 6px;padding:0;display:grid;gap:4px}
.mini li{display:grid;grid-template-columns:32px 1fr auto;align-items:center;gap:8px}
.mini .no{font-size:1.2rem}.mini .sc{font-weight:800}
.tree,.tree ul{list-style:none;margin:0;padding-left:0}
.tree ul{padding-left:18px;border-left:2px dashed var(--line);margin-left:14px}
.tree li{margin:2px 0}
.tree summary{cursor:pointer;list-style:none;display:flex;align-items:center;gap:2px}
.tree summary::-webkit-details-marker{display:none}
.tree summary::before{content:'▸';color:var(--muted);width:16px;flex:none;text-align:center}.tree details[open]>summary::before{content:'▾'}
.tree li.leaf{padding-left:18px}
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(92px,1fr));gap:10px}
@media (min-width:900px){.tiles{grid-template-columns:repeat(auto-fill,minmax(118px,1fr))}}
.tile{display:block;text-decoration:none;color:var(--text);text-align:center;font-size:.8rem;line-height:1.25;transition:transform .15s}
.tile:hover{transform:translateY(-3px)}
.tile img{width:100%;height:auto;aspect-ratio:1;border-radius:14px;display:block;margin-bottom:4px;background:linear-gradient(135deg,var(--line),var(--bg));box-shadow:var(--shadow)}
.piece{margin:32px 0 0;padding:16px;border-radius:14px;border:2px dashed var(--gold);background:var(--card);display:grid;gap:4px;text-align:center}
.piece span{color:var(--muted);font-weight:700}.piece>b{font-size:1.5rem}.piece small{color:var(--muted)}
.evgrid{display:grid;gap:10px;grid-template-columns:1fr}
@media (min-width:720px){.evgrid{grid-template-columns:1fr 1fr}}
.ev{position:relative;margin:0 0 10px}.evgrid .ev{margin:0}
.ev h3{font-size:1.1rem}.ev ul{margin:6px 0;padding-left:1.3em}.ev ol{margin:6px 0;padding-left:1.3em}.ev li{margin:3px 0}
.ev .big{margin:4px 0 6px;font-size:1.05rem}.ev .big b{color:var(--accent);font-size:1.2rem}
.tag{position:absolute;top:10px;right:12px;font-size:.75rem;font-weight:800;padding:2px 8px;border-radius:999px}
.tag.live{background:#ffe0e0;color:#b4232a}.tag.soon{background:var(--accent-soft);color:var(--accent)}
.ev .mono{letter-spacing:0;background:var(--bg);border:1px solid var(--line);padding:0 6px;border-radius:6px;font-family:inherit;font-weight:700}.cmd .mono{letter-spacing:0;font-family:inherit;color:var(--accent)}
table.cmd{width:100%;border-collapse:collapse}.cmd td{padding:7px 4px;border-bottom:1px solid var(--line);vertical-align:top}.cmd tr:last-child td{border-bottom:0}.cmd td:first-child{white-space:nowrap;font-weight:700;padding-right:12px}
/* 세계관: 동화책처럼 읽히게(가운데 좁은 단, 넉넉한 줄 간격, 장 제목은 이야기 머리글) */
.tale{max-width:680px;margin:0 auto;font-size:1.06rem;line-height:1.95}
.tale h1{text-align:center;font-size:2rem;margin:.6em 0 .3em}
.tale>blockquote{border:0;background:none;text-align:center;color:var(--muted);font-size:1.1rem;padding:0;margin:0 0 2em}
.tale h2{border:0;text-align:center;font-size:1.3rem;margin:2.6em 0 1em;color:var(--accent)}
.tale h2::after{content:'';display:block;width:42px;height:3px;border-radius:3px;background:var(--gold);margin:10px auto 0}
.tale p{margin:0 0 1.1em}
.tale p:last-child{text-align:center;font-size:1.15rem;margin-top:2em}
/* 색 다양하게: 제목·카드·숫자마다 6가지 색을 돌아가며(어두운 화면은 밝은 톤) */
.hl{color:var(--c2)}
main>h2:nth-of-type(6n+1),.tale h2:nth-of-type(6n+1){color:var(--c1)}main>h2:nth-of-type(6n+2),.tale h2:nth-of-type(6n+2){color:var(--c2)}
main>h2:nth-of-type(6n+3),.tale h2:nth-of-type(6n+3){color:var(--c3)}main>h2:nth-of-type(6n+4),.tale h2:nth-of-type(6n+4){color:var(--c4)}
main>h2:nth-of-type(6n+5),.tale h2:nth-of-type(6n+5){color:var(--c5)}main>h2:nth-of-type(6n+6),.tale h2:nth-of-type(6n+6){color:var(--c6)}
.links a:nth-child(6n+1) b,.stats .stat:nth-child(6n+1) b,.evgrid .ev:nth-child(6n+1) h3,.evgrid .ev:nth-child(6n+1) .big b{color:var(--c1)}
.links a:nth-child(6n+2) b,.stats .stat:nth-child(6n+2) b,.evgrid .ev:nth-child(6n+2) h3,.evgrid .ev:nth-child(6n+2) .big b{color:var(--c2)}
.links a:nth-child(6n+3) b,.stats .stat:nth-child(6n+3) b,.evgrid .ev:nth-child(6n+3) h3,.evgrid .ev:nth-child(6n+3) .big b{color:var(--c3)}
.links a:nth-child(6n+4) b,.stats .stat:nth-child(6n+4) b,.evgrid .ev:nth-child(6n+4) h3,.evgrid .ev:nth-child(6n+4) .big b{color:var(--c4)}
.links a:nth-child(6n+5) b,.stats .stat:nth-child(6n+5) b,.evgrid .ev:nth-child(6n+5) h3,.evgrid .ev:nth-child(6n+5) .big b{color:var(--c5)}
.links a:nth-child(6n+6) b,.stats .stat:nth-child(6n+6) b,.evgrid .ev:nth-child(6n+6) h3,.evgrid .ev:nth-child(6n+6) .big b{color:var(--c6)}

.homegrid .box:nth-child(1) h3{color:var(--c5)}.homegrid .box:nth-child(2) h3{color:var(--c4)}.homegrid .box:nth-child(3) h3{color:var(--c3)}
@media (min-width:1000px){.homegrid{grid-template-columns:1fr 1fr 1fr}}
.today h3{color:var(--c3)}
.lead b{color:var(--c1)}
.tale p b,.tale li b{color:var(--c3)}
.cms{list-style:none;margin:0;padding:0;display:grid;gap:8px}
.cm{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px 12px;position:relative}
.cm-h{display:flex;align-items:center;gap:6px;flex-wrap:wrap;padding-right:64px}.cm-h a{text-decoration:none;color:var(--c4)}
.cm p{margin:4px 0 0;white-space:pre-wrap}
.like{position:absolute;top:8px;right:10px;border:1.5px solid var(--line);background:var(--bg);color:var(--text);border-radius:999px;padding:3px 10px;font:inherit;cursor:pointer}
.like.on{border-color:var(--c2);color:var(--c2)}.like b{color:inherit}
.cm .mono,.note .mono{letter-spacing:0;font-family:inherit;background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:0 5px}
footer{text-align:center;color:var(--muted);font-size:.8rem;padding:24px 16px 40px}
footer a{color:var(--muted)}
.box details{margin:4px 0}.box summary{cursor:pointer}
</style></head><body>
<header><div class="bar"><a class="logo" href="/">${logo()}</a>
<nav>${mainItems.map(([h,l,i])=>`<a href="${h}"${active===h?' class="on"':''}>${i} ${l}</a>`).join('')}<a href="/search" class="sbtn${active==='/search'?' on':''}">🔍 검색</a><a href="/my" class="lbtn lgn" data-l="내 갈매미">🔑 로그인</a></nav><span class="sp"></span>
<button class="hbtn" type="button" id="mbtn" aria-label="더보기" aria-expanded="false">☰</button>${playBtn()}</div>
<div class="searchrow" id="srow"${q?'':' hidden'}><form class="search" action="/search" method="get" role="search" autocomplete="off"><input name="q" value="${esc(q)}" placeholder="갈매미·닉네임·이야기 검색 (초성 OK)" aria-label="검색" maxlength="30"><button>검색</button><div class="sug" hidden></div></form></div></header>
<main>${body}${piece(active)}</main>
<nav class="tabbar" aria-label="메뉴">${tabItems.map(([h,l,i])=>`<a href="${h}"${tabOn(h,active)?' class="on"':''}><i>${i}</i>${l}</a>`).join('')}<a href="/search" class="sbtn${active==='/search'?' on':''}"><i>🔍</i>검색</a><a href="/my" class="lgn" data-i="gull" data-l="내 갈매미"><i>🔑</i>로그인</a></nav>
<div class="more" id="more" hidden>${moreItems.map(([h,l,i])=>`<a href="${h}"${active===h?' class="on"':''}>${i} ${l}</a>`).join('')}</div>
<footer>갈매미맨 · 카카오톡 채널 챗봇 게임 · v${esc(options.version)}<br><a href="/privacy">개인정보처리방침</a> · <a href="/odds">뽑기 확률</a> · <a href="/account/delete">계정 삭제</a></footer>
<script>${suggestJs}${likeJs}${headerJs}if('serviceWorker' in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});</script>
</body></html>`;

  // 검색엔진용 표준 주소(canonical): 보기 옵션(?view, ?sort) 없는 주소로 묶음. 랭킹 탭(?k)만 유지. 검색 결과는 색인하지 않음(noindex)
  const headFor=(url:string)=>{
    const [path,qs='']=url.split('?');
    if(path==='/search')return '<meta name="robots" content="noindex">';
    const k=new URLSearchParams(qs).get('k');
    const keep=path==='/rank'&&k&&/^(dex|top|week)$/.test(k)?`?k=${k}`:'';
    const ld=path==='/'&&site?`<meta property="og:url" content="${esc(site)}/"><script type="application/ld+json">${JSON.stringify({'@context':'https://schema.org','@type':'WebSite',name:SITE_NAME,alternateName:SITE_ALT,url:`${site}/`}).replace(/</g,'\\u003c')}</script>`:'';
    return site?`<link rel="canonical" href="${esc(site+path+keep)}">${ld}`:'';
  };
  const send=(reply:FastifyReply,html:string,maxAge=300)=>reply.type('text/html; charset=utf-8').header('Cache-Control',`public, max-age=${maxAge}`)
    .send(html.replace('</head>',`${headFor(reply.request.url)}</head>`));
  // 방문자마다 따로 셉니다. 서버는 127.0.0.1에서만 받고 Cloudflare 터널을 거쳐 오므로 CF-Connecting-IP가 실제 방문자 주소예요
  // (헤더가 없으면 한 묶음으로 셈)
  const limit={config:{rateLimit:{max:300,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`web:${ip}`:'web';}}}};
  const notFound=(reply:FastifyReply,msg:string)=>reply.code(404).type('text/html; charset=utf-8').send(page('없음','',`<div class="empty">${esc(msg)}<br><a href="/">홈으로</a></div>`));

  // ── 홈(v0.16.0): 「내 갈매미」 대시보드(public/web, 민트 테마)를 메인으로 씀 ──
  // 로그인 전에는 서버가 소개 화면(landing)을 <main>에 넣어 보냄(검색엔진도 이 내용을 읽음). 로그인돼 있으면 dash.js가 대시보드로 바꿈.
  // 세션 쿠키는 Path=/my라서 여기서는 로그인 여부를 알 수 없음 → 판단은 화면(dash.js)이 /my/api/me로.
  const todayUnit=()=>{   // 오늘의 갈매미: 한국 날짜마다 바뀜(히든·최상위 제외)
    const pool=content.characters.filter(x=>x.rarity!=='HIDDEN'&&!topTiers.has(x.rarity));
    const day=Math.floor(DateTime.now().setZone('Asia/Seoul').startOf('day').toSeconds()/86400);
    return {pool,day,today:pool[((day%pool.length)+pool.length)%pool.length]};
  };
  // 갈매미 이름 링크(히든은 ??? 로, 스포일러 가림)
  const unitLink=(id:string)=>{const c=content.map.get(id);if(!c)return '';return c.rarity==='HIDDEN'?`<a href="/hidden#h${hiddenIndex(c)}">???</a>`:`<a href="/dex/${c.id}">${esc(c.name)}</a>`;};
  const landing=async()=>{
    const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
    const c=huntCoupon();
    const {pool,day,today}=todayUnit();
    // 갈매미 미리보기 6마리(날마다 바뀜, 오늘의 갈매미 다음부터). 카드 그림은 기존 카드 그림(thumbs) 그대로
    const pick=Array.from({length:6},(_,i)=>pool[(((day+1+i*7)%pool.length)+pool.length)%pool.length]).filter((x,i,a)=>x.id!==today.id&&a.indexOf(x)===i);
    const week=await data.ranking('WEEK');
    const talk=await data.recentComments(3);
    const board=week.rows.length?{rows:week.rows,unit:'회',label:'이번 주 조합',k:'week'}:{rows:(await data.ranking('DEX')).rows,unit:`/${content.characters.length}`,label:'도감 수집',k:'dex'};
    const medal=['🥇','🥈','🥉'];
    const top3=board.rows.slice(0,3).map((r,i)=>`<li><span class="no">${medal[i]}</span><span class="who">${r.badge?`${esc(r.badge)} `:''}${r.nick?`<a href="/p/${encodeURIComponent(r.name)}">${esc(r.name)}</a>`:esc(r.name)}</span><span class="sc">${r.score}${board.unit}</span></li>`).join('');
    const hidden=content.characters.filter(x=>x.rarity==='HIDDEN');
    const recent=[...(await data.hiddenFirsts())].sort((x,y)=>y[1].at.getTime()-x[1].at.getTime()).slice(0,3)
      .map(([id,f])=>{const n=hidden.findIndex(x=>x.id===id)+1;return `<li><a href="/hidden#h${n}">❔ 히든 #${n} 발견!</a> <span>${f.name?esc(f.name):'이름 없는 갈매미'} · ${esc(DateTime.fromJSDate(f.at).setZone('Asia/Seoul').toFormat('M월 d일'))}</span></li>`;}).join('');
    const kakao=channel?`<a class="btn kakao lg" href="${esc(channel)}" target="_blank" rel="noopener">${bubble}카톡에서 플레이</a>`:'';
    const card=(x:Character,cls='')=>`<a class="lcard ${cls}" href="/dex/${x.id}"><img src="${img('thumbs',x.id)}" alt="${esc(x.name)}" width="480" height="480" loading="lazy"><b>${esc(x.name)}</b><small>${esc(rarityNames[x.rarity])}</small></a>`;
    return `<div class="landing">
<section class="lhero panel"><div class="lh-tx">
<p class="kicker">카카오톡 채널 챗봇 게임</p>
<h1>갈매미는 <span class="hl">존재</span>한다.</h1>
<p class="lead">돌로 된 갈매미들이 <b>갈매미 섬 항구 수비대</b>에 출근해서 항구를 지켜요. 몸이 돌이라 날지는 못해요. 새우gang 봉지 소리에는 약해요.</p>
<div class="lbtns">${kakao}<a class="btn ghost lg" href="#login">🔑 웹에서 시작하기</a><a class="btn ghost lg" href="/app/native">⬇ 네이티브 앱 받기</a></div>
<ul class="lstats"><li><b>${content.characters.length}</b>종 갈매미</li><li><b>${content.recipes.length}</b>가지 조합</li><li>히든 <b>${count('HIDDEN')}</b>종</li><li>동네 <b>7</b>곳</li></ul>
</div><a class="ltoday" href="/dex/${today.id}"><img src="${img('thumbs',today.id)}" alt="${esc(today.name)}" width="480" height="480"><span><small>☀️ 오늘의 갈매미</small><b>${esc(today.name)}</b><em>${esc(today.introduction.split(/(?<=[.!?…])\s+/u)[0])}</em></span></a></section>
<section class="panel login" id="login"><div class="ph"><svg class="ico" aria-hidden="true"><use href="#i-home"/></svg><h2>로그인</h2></div>
<div id="kslot"></div>
<p class="note">카톡 갈매미맨 채널에 <b>웹 로그인</b>이라고 보내고, 받은 6자리 코드를 적어요. (10분 안에 한 번만)</p>
<label class="sr" for="code">로그인 코드</label><input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000">
<p class="note err" id="lerr" role="alert"></p><button class="btn wide" id="go">로그인</button>
<p class="note">로그인하면 웹에서 <b>뽑기·조합·보관함</b>을 하고, 카톡과 같은 계정으로 이어져요.</p></section>
${c?`<a class="levent panel" href="/event"><b>🎉 ${esc(c.label)} 이벤트</b><span>이 사이트 어딘가에 <b>쿠폰 조각 ${c.pieces.length}개</b>가 숨어 있어요. 모두 찾아 카톡에 보내면 뽑기권 <b>${c.tickets}장</b>! (1인 1회)</span><em>이벤트·혜택 모두 보기 →</em></a>`:'<a class="levent panel" href="/event"><b>🎁 이벤트·혜택</b><span>가입 선물·친구 초대·튜토리얼 보상을 확인해요.</span><em>보러 가기 →</em></a>'}
<section class="panel lmeet"><div class="ph"><h2>갈매미 만나기</h2><span class="sp"></span><a class="more" href="/dex">도감 전체 →</a></div><div class="lcards">${pick.map(x=>card(x)).join('')}</div></section>
<div class="lgrid">
<section class="panel"><h3>🏆 ${board.label} TOP 3</h3>${top3?`<ol class="ltop">${top3}</ol>`:'<p class="note">아직 기록이 없어요.</p>'}<a class="more" href="/rank?k=${board.k}">전체 랭킹 →</a></section>
<section class="panel"><h3>💬 최근 한마디</h3>${talk.length?`<ul class="ltalk">${talk.map(x=>`<li><b><a href="/p/${encodeURIComponent(x.name)}">${x.badge?`${esc(x.badge)} `:''}${esc(x.name)}</a></b> → ${unitLink(x.characterId)}<span>${esc(x.text)}</span></li>`).join('')}</ul>`:'<p class="note">아직 댓글이 없어요. 카톡에서 <b>댓글 갈매미이름 하고 싶은 말</b>!</p>'}<a class="more" href="/talk">수다방 가기 →</a></section>
<section class="panel"><h3>❔ 최근 발견된 히든</h3>${recent?`<ul class="lhid">${recent}</ul>`:'<p class="note">아직 아무도 히든을 찾지 못했어요. 첫 발견자가 되어 보세요!</p>'}<a class="more" href="/hidden">히든 단서 보기 →</a></section>
</div>
<section class="panel lhow"><h2>이렇게 놀아요</h2><ol>
<li><b>뽑기</b><span>뽑기권으로 하급·중급·고급 뽑기. 카톡에서 <b>뽑기권받기</b>로 충전해요.</span></li>
<li><b>조합</b><span>같은 자리로 발령 내 합체! ${content.recipes.length}가지 조합법이 있어요.</span></li>
<li><b>히든 찾기</b><span>숨은 히든 ${count('HIDDEN')}종은 단서를 모아 찾아요.</span></li>
<li><b>탐험·대결</b><span>최상위 갈매미를 탐험에 보내고, 앱에서 스토리 대결을 해요.</span></li></ol></section>
</div>`;
  };
  // 공용 아래쪽(로그인 전·후 모두): 공개 페이지 바로 가기 + 정보
  const dashFoot=()=>`<footer class="foot"><nav aria-label="둘러보기"><a href="/dex">📚 도감</a><a href="/recipes">📜 조합법</a><a href="/hidden">❔ 히든 단서</a><a href="/rank">🏆 랭킹</a><a href="/talk">💬 수다방</a><a href="/world">🗺 세계관</a><a href="/event">🎁 이벤트</a><a href="/search">🔍 검색</a></nav>
<p>갈매미맨 · 카카오톡 채널 챗봇 게임 · v${esc(options.version)}<br><a href="/privacy">개인정보처리방침</a> · <a href="/odds">뽑기 확률</a> · <a href="/account/delete">계정 삭제</a></p></footer>`;
  const dashShell=()=>readFileSync(resolve(options.root,'public/web/index.html'),'utf8');
  app.get('/',limit,async(_req,reply)=>{
    const {today}=todayUnit();
    const desc=`오늘의 갈매미: ${today.name} · 돌로 된 갈매미들이 항구 수비대에 출근하는 섬`;
    const head=`<title>${SITE_NAME}</title><meta name="description" content="${esc(desc)}"><meta property="og:type" content="website"><meta property="og:site_name" content="${SITE_NAME}"><meta property="og:title" content="${SITE_NAME}"><meta property="og:description" content="${esc(desc)}"><meta property="og:image" content="${site}${img('images',today.id)}">`;
    const html=dashShell().replace('<title>내 갈매미맨</title>',head).replace('<meta name="robots" content="noindex">\n','')
      .replace('<main id="main"><div class="state"><div class="spin"></div>불러오는 중…</div></main>',`<main id="main" data-landing="1">${await landing()}</main>`)
      .replace('<!--foot-->',dashFoot());
    return reply.type('text/html; charset=utf-8').header('Cache-Control','no-cache').send(html.replace('</head>',`${headFor('/')}</head>`));
  });
  // /my(로그인 전)·로그인이 풀린 화면이 같은 소개 화면을 받아 감
  app.get('/web/landing',limit,async(_req,reply)=>reply.type('text/html; charset=utf-8').header('Cache-Control','no-cache').send(await landing()));
  app.get('/web/foot',limit,async(_req,reply)=>reply.type('text/html; charset=utf-8').header('Cache-Control','public, max-age=300').send(dashFoot()));

  // ── 이벤트·혜택: 진행 중 쿠폰·기간 이벤트 + 상시 혜택(가입·뽑기권 받기·튜토리얼·친구 초대) ──
  app.get('/event',limit,async(_req,reply)=>{
    const e=content.economy,at=now();
    const kst=(iso:string)=>DateTime.fromISO(iso).setZone('Asia/Seoul').toFormat('M월 d일 HH:mm');
    const period=(from?:string,to?:string)=>to?`${from?`${kst(from)} ~ `:''}${kst(to)}까지`:'끝날 때까지 (종료일 미정)';
    const live=e.events.filter(x=>new Date(x.from)<=at&&at<new Date(x.to));
    const soon=e.events.filter(x=>at<new Date(x.from)).sort((a,b)=>a.from.localeCompare(b.from));
    const effects=(x:typeof e.events[number])=>[x.claimBonus?`뽑기권 받기 +${x.claimBonus}장`:'',x.snackMultiplier>1?`새우gang ×${x.snackMultiplier}`:''].filter(Boolean).join(' · ')||'특별 이벤트';
    const coupons=e.coupons.filter(c=>(!c.from||new Date(c.from)<=at)&&(!c.to||at<new Date(c.to)));
    const couponBox=coupons.map(c=>`<div class="box ev"><span class="tag live">진행 중</span><h3>🧩 ${esc(c.label)} 쿠폰</h3>
<p class="big">🎟 뽑기권 <b>${c.tickets}장</b> <small>(1인 1회)</small></p>
${c.pieces.length?`<ol><li>이 사이트 곳곳에 숨은 <b>쿠폰 조각 ${c.pieces.length}개</b>를 찾아요. 페이지 맨 아래를 잘 보세요.</li><li>조각을 순서대로 이어서 카톡에 보내요. <span class="mono">쿠폰 조각1조각2…</span> (띄어쓰기 상관없음)</li></ol>
<p class="note">💡 힌트: 섬의 이야기, 조합하는 법, 서류에 없는 갈매미</p>`:'<p>카톡에 <span class="mono">쿠폰 코드</span>로 입력해요.</p>'}
<p class="note">📅 ${esc(period(c.from,c.to))}</p></div>`).join('');
    const eventBox=[...live.map(x=>[x,'live'] as const),...soon.map(x=>[x,'soon'] as const)].map(([x,k])=>`<div class="box ev"><span class="tag ${k}">${k==='live'?'진행 중':'곧 시작'}</span><h3>🎉 ${esc(x.label)}</h3><p class="big">${esc(effects(x))}</p><p class="note">📅 ${esc(kst(x.from))} ~ ${esc(kst(x.to))}</p></div>`).join('');
    const tTotal=tutorialSteps.length*e.tutorial.tickets;
    const body=`<h1>🎁 이벤트 · 혜택</h1>
<p class="lead">카톡 채널 <b>갈매미맨</b>에서 받을 수 있는 뽑기권을 한곳에 모았어요.</p>
${channel?`<div class="btns" style="margin:0 0 8px">${playBtn(true)}</div>`:''}
<h2>🔥 지금 진행 중</h2>
${couponBox+eventBox||'<div class="empty">지금 진행 중인 기간 한정 이벤트는 없어요. 아래 상시 혜택은 언제든 받을 수 있어요!</div>'}
<h2>🎟 언제나 받는 혜택</h2>
<div class="evgrid">
<div class="box ev"><h3>💌 친구 초대</h3><p class="big">친구도 나도 🎟 <b>${e.invite.tickets}장</b>씩</p>
<ol><li>카톡에 <span class="mono">초대코드</span> → 내 코드 확인</li><li>친구가 채널 추가 후 <span class="mono">초대 내코드</span> 입력 (예: 초대 ABC123) → 친구는 바로 🎟 ${e.invite.tickets}</li><li>친구가 튜토리얼 ${tutorialSteps.length}단계를 끝내면 → 나도 🎟 ${e.invite.tickets}</li></ol>
<p class="note">한 사람당 최대 ${e.invite.max}명 → 최대 ${(e.invite.max*e.invite.tickets).toLocaleString('ko-KR')}장 · 친구는 가입 후 ${e.invite.days}일 안에 입력</p></div>
<div class="box ev"><h3>📘 튜토리얼</h3><p class="big">${tutorialSteps.length}단계 · 단계마다 🎟 <b>${e.tutorial.tickets}장</b> (총 ${tTotal}장)</p>
<ol>${tutorialSteps.map(t=>`<li><b>${esc(t.title)}</b> · ${esc(t.how)}</li>`).join('')}</ol>
<p class="note">카톡에 <span class="mono">튜토리얼</span>을 보내면 지금 몇 단계인지 보여요.</p></div>
<div class="box ev"><h3>⏰ 뽑기권 받기</h3><p class="big">${e.tickets.cooldownHours}시간마다 🎟 <b>${e.tickets.claim}장</b></p><p>카톡에 <span class="mono">뽑기권받기</span>. 받지 않은 시간은 쌓이지 않으니 자주 들러 주세요.</p></div>
<div class="box ev"><h3>🐣 가입 선물</h3><p class="big">처음 말을 걸면 🎟 <b>${e.tickets.welcome}장</b></p><p>카톡 채널을 추가하고 아무 말이나 보내면 시작해요.</p></div>
<div class="box ev"><h3>🎯 오늘의 미션</h3><p class="big">매일 0시(한국 시간) 새로 시작 · 하루 최대 🎟 <b>${e.missions.reduce((n,m)=>n+m.tickets,0)}장</b></p>
<ul>${e.missions.map(m=>`<li>${esc(m.label)} · 🎟 ${m.tickets}</li>`).join('')}</ul><p class="note">달성하면 자동으로 들어와요. 카톡에 <span class="mono">미션</span>을 보내면 진행 상황이 보여요.</p></div>
</div>
<h2>⌨ 카톡에서 이렇게 보내요</h2>
<div class="box"><table class="cmd"><tbody>
<tr><td class="mono">뽑기권받기</td><td>${e.tickets.cooldownHours}시간마다 뽑기권 받기</td></tr>
<tr><td class="mono">초대코드</td><td>내 초대 코드 보기</td></tr>
<tr><td class="mono">초대 ABC123</td><td>친구 코드 입력하기</td></tr>
<tr><td class="mono">쿠폰 코드</td><td>쿠폰 입력하기</td></tr>
<tr><td class="mono">튜토리얼</td><td>튜토리얼 진행 보기</td></tr>
<tr><td class="mono">닉네임 갈매미왕</td><td>닉네임 정하기 (랭킹에 이름이 나와요)</td></tr>
<tr><td class="mono">조합 고르기</td><td>재료를 직접 골라 조합하기 (숨은 조합 찾기)</td></tr>
<tr><td class="mono">상점 땅콩</td><td>🥜 땅콩 상점 (뽑기권·특별 골라 받기·닉네임 장식)</td></tr>
<tr><td class="mono">댓글 금갑 귀여워요</td><td>만나 본 갈매미에게 한마디 (수다방·도감에 올라가요)</td></tr>
<tr><td class="mono">좋아요 12</td><td>댓글 12번에 좋아요</td></tr>
<tr><td class="mono">이벤트</td><td>진행 중인 이벤트 보기</td></tr>
</tbody></table></div>`;
    return send(reply,page('이벤트','/event',body,'',{desc:`친구 초대하면 친구도 나도 뽑기권 ${e.invite.tickets}장! 갈매미맨 이벤트·혜택 모음`}),60);
  });

  // ── 개인정보처리방침 · 계정 삭제 안내 · 뽑기 확률 공시 ──
  app.get('/privacy',limit,async(_req,reply)=>send(reply,page('개인정보처리방침','',`<article class="prose tale">${mdHtml('privacy.md','<p>준비 중이에요.</p>')}</article>`)));
  app.get('/account/delete',limit,async(_req,reply)=>send(reply,page('계정 삭제','',`<article class="prose tale"><h1>🗑 계정 삭제(탈퇴) 안내</h1>
<p>앱을 지웠어도 아래 순서로 언제든 계정과 게임 데이터를 지울 수 있어요.</p>
<ol><li>카카오톡 채널 「갈매미맨」에 <b>웹 로그인</b>을 보내 6자리 코드를 받거나, 카카오 계정을 연결해 두었다면 「카카오로 로그인」을 눌러요.</li>
<li><a href="/my#account">내 갈매미 → ⚙️ 계정</a>에서 로그인해요.</li>
<li>「계정 삭제」 칸에 <b>탈퇴</b>라고 적고 버튼을 눌러요. 바로 지워져요.</li></ol>
<p><b>지워지는 것:</b> 가진 갈매미, 도감, 뽑기권·새우gang·땅콩, 아이템, 탐험·미션·업적 기록, 스토리 진행, 닉네임, 댓글, 로그인 연결(카카오 회원번호 포함).</p>
<p><b>남는 것:</b> 서버 백업에 최대 7일 동안 남았다가 자동으로 사라져요. 다른 사람이 남긴 댓글이나 기록은 지워지지 않아요.</p>
<p>카톡 채널에 <b>탈퇴</b>를 보내도 이 방법을 알려 드려요. 자세한 내용은 <a href="/privacy">개인정보처리방침</a>을 봐 주세요.</p>
<p style="text-align:center"><a class="btn" href="/my#account">계정 삭제하러 가기</a></p></article>`)));
  app.get('/odds',limit,async(_req,reply)=>{
    const g=content.economy.gachas,pct=(n:number)=>`${Number(n.toFixed(4))}%`;
    const pool=(t:string)=>content.characters.filter(c=>c.rarity===t&&!c.noGacha);
    const secret=(t:string)=>t==='HIDDEN'||topTiers.has(t as Character['rarity']);
    const rows=(['LOW','MID','HIGH'] as const).map(k=>{
      const tiers=Object.entries(g[k].weights);
      const units=tiers.map(([t,w])=>{const list=pool(t);return `<details><summary><b>${esc(rarityNames[t])} ${w}%</b> <span class="note">· ${list.length}종 · 한 종마다 ${pct(w/list.length)}</span></summary><p>${secret(t)?'(이 등급은 이름을 비밀로 해요)':list.map(c=>chip(c.id)).join('')}</p></details>`;}).join('');
      const pity=k==='HIGH'?g[k].pity:undefined;   // 천장은 고급 뽑기에만(게임 처리와 같게)
      return `<div class="box"><h3>🥚 ${esc(g[k].label)} 뽑기 · 🎟 ${g[k].cost}장</h3>${units}${pity?`<p class="note">🎯 천장: ${pity.count}회 안에 ${esc(rarityNames[pity.tier])} 이상이 한 번도 안 나오면 ${pity.count}번째에 ${esc(rarityNames[pity.tier])} 확정(그 등급 안에서는 같은 확률). ${esc(rarityNames[pity.tier])} 이상이 나오면 다시 0부터 세요.</p>`:''}</div>`;
    }).join('');
    return send(reply,page('뽑기 확률','',`<h1>🎲 뽑기 확률 정보</h1>
<p class="lead">카톡과 홈페이지의 뽑기는 모두 같은 확률이에요. 등급을 먼저 정하고, 그 등급 안에서는 모든 갈매미가 같은 확률로 나와요. 한 종의 확률 = 등급 확률 ÷ 그 등급의 갈매미 수.</p>${rows}
<p class="note">뽑기권은 게임 안에서 무료로 받아요(${content.economy.tickets.cooldownHours}시간마다 받기·미션·이벤트 등). 확률을 바꿀 때는 미리 알려 드려요.</p>`));
  });
  app.get('/world',limit,async(_req,reply)=>send(reply,page('세계관','/world',`<article class="prose tale">${worldHtml()}</article>`)));

  // ── 도감 목록 ──
  app.get<{Querystring:{view?:string;sort?:string}}>('/dex',limit,async(req,reply)=>{
    // 보기: 작은 그림(기본, 예전 주소 view=s도 같음) / 소개가 있는 큰 카드(view=card). 정렬: 기본 / 이름순(sort=name)
    const small=req.query.view!=='card',byName=req.query.sort==='name';
    const qs=(v:boolean,n:boolean)=>{const p=[...(v?[]:['view=card']),...(n?['sort=name']:[])];return `/dex${p.length?`?${p.join('&')}`:''}`;};
    const tile=(c:Character)=>{const hid=c.rarity==='HIDDEN';
      return `<a class="tile" href="${hid?`/hidden#h${hiddenIndex(c)}`:`/dex/${c.id}`}" title="${hid?'???':esc(c.name)}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="" width="480" height="480"><span>${hid?'???':esc(c.name)}</span></a>`;};
    const card=(c:Character)=>{
      if(c.rarity==='HIDDEN')return `<a class="card locked" href="/hidden#h${hiddenIndex(c)}"><img loading="lazy" src="${img('thumbs','unknown')}" alt="???" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>???</h3><p>❔ ${esc(c.hint??'재료로 직접 조합해서 찾아요')}</p></div></a>`;
      if(topTiers.has(c.rarity))return `<a class="card locked" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3><p>모습과 이야기는 게임에서 직접 만나면 열려요.</p></div></a>`;
      return `<a class="card" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3><p>${esc(c.introduction)}</p><p class="q">💬 「${esc(c.quote)}」</p></div></a>`;
    };
    const tiers=dexOrder.filter(t=>content.characters.some(c=>c.rarity===t));
    const chips=tiers.map(t=>`<a class="chip" style="color:${tierColor[t]}" href="#${t}">${esc(rarityNames[t])}</a>`).join('');
    const sections=tiers.map(t=>{const list=content.characters.filter(c=>c.rarity===t).sort((a,b)=>byName&&a.rarity!=='HIDDEN'?a.name.localeCompare(b.name,'ko'):0);
      return `<h2 id="${t}" style="scroll-margin-top:110px">${esc(rarityNames[t])} <small style="color:var(--muted);font-weight:500">${list.length}종</small></h2><div class="${small?'tiles':'grid'}">${list.map(small?tile:card).join('')}</div>`;}).join('');
    return send(reply,page('도감','/dex',`<h1>갈매미 도감</h1><p class="lead">${content.characters.length}종의 갈매미. 카드를 누르면 만드는 법과 재료로 쓰이는 곳을 볼 수 있어요.</p><div class="tabs"><a href="${qs(true,byName)}"${small?' class="on"':''}>🔳 그림</a><a href="${qs(false,byName)}"${small?'':' class="on"'}>🃏 소개 카드</a><a href="${qs(small,false)}"${byName?'':' class="on"'}>번호순</a><a href="${qs(small,true)}"${byName?' class="on"':''}>가나다순</a></div><div class="chips">${chips}</div>${sections}`));
  });

  // ── 도감 상세 ──
  app.get<{Params:{id:string}}>('/dex/:id',limit,async(req,reply)=>{
    const c=content.map.get(req.params.id);
    if(!c)return notFound(reply,'그런 갈매미는 없어요.');
    if(c.rarity==='HIDDEN')return reply.redirect(`/hidden#h${hiddenIndex(c)}`);
    const top=topTiers.has(c.rarity),seen=(await data.seenCounts()).get(c.id)??0;
    const recipe=recipeOf(c.id);
    // 히든이 결과인 조합은 빼요('이 유닛이 히든 재료'라는 것도 비밀)
    const usedIn=content.recipes.filter(r=>!r.hidden&&r.materials.includes(c.id));
    const how=recipe?`<div class="box"><h3>🧩 만드는 법</h3>${counts(recipe.materials).map(([id,n])=>chip(id,n)).join('<span class="eq">+</span>')}</div>`:'';
    // 기본 재료 합계: 히든은 재료를 펼칠 수 없어(비밀) 따로 셉니다
    const all=recipe?[...baseNeeds(c.id,1,new Map())].sort((x,y)=>y[1]-x[1]):[];
    const needs=all.filter(([id])=>content.map.get(id)!.rarity!=='HIDDEN'),secret=all.filter(([id])=>content.map.get(id)!.rarity==='HIDDEN');
    const secretNote=secret.length?`<p class="note" style="margin:6px 0 0">＋ 히든 ${secret.reduce((x,y)=>x+y[1],0)}마리 ${secret.map(([id,k])=>chip(id,k)).join('')} — 히든은 재료가 비밀이라 합계에 넣지 않았어요. <a href="/hidden">단서 보기</a></p>`:'';
    const lineage=recipe?`<div class="box"><h3>🌳 조합 계보도</h3><ul class="tree">${tree(c.id,1,0)}</ul>
<p class="note" style="margin:8px 0 4px">처음부터 만들면 필요한 기본 재료 (총 ${needs.reduce((x,y)=>x+y[1],0)}마리${secret.length?', 히든 제외':''})</p>${needs.map(([id,k])=>chip(id,k)).join('')}${secretNote}</div>`:'';
    const used=usedIn.length?`<div class="box"><h3>➡️ 재료로 쓰이는 곳 (${usedIn.length})</h3>${usedIn.map(r=>chip(r.resultId)).join('')}</div>`:'';
    const where=data.source(c.id);
    const comments=await data.unitComments(c.id);
    const body=`<p class="note"><a href="/dex#${c.rarity}">← ${esc(rarityNames[c.rarity])} 도감</a></p>
<div class="detail"><img class="big" src="${img(top?'thumbs':'images',face(c))}" alt="${esc(c.name)}" width="800" height="800">
<div>${badge(c.rarity)}<h1 style="margin-top:.2em">${esc(c.name)}</h1>
${top?'<p class="lead">이 원형의 모습과 이야기는 게임에서 직접 만나면 열려요.</p>':`<p>${esc(c.introduction)}</p><p class="note">💬 「${esc(c.quote)}」</p>`}
<p class="note">🐦 지금까지 <b>${seen}명</b>의 수비대원이 만났어요.${where?` · 얻는 곳: ${esc(where)}`:''}</p>
${how}${used}</div></div>${lineage}
<div class="box"><h3>💬 수비대원들의 한마디 (${comments.length})</h3>${comments.length?`<ul class="cms">${comments.map(x=>commentItem(x)).join('')}</ul>`:'<p class="note">아직 한마디가 없어요. 첫 댓글의 주인공이 되어 보세요!</p>'}${howToComment(c.name)}</div>`;
    return send(reply,page(c.name,'/dex',body,'',{image:img(top?'thumbs':'images',face(c)),desc:top?`${rarityNames[c.rarity]} ${c.name} · 갈매미맨 도감`:`${rarityNames[c.rarity]} ${c.name} — ${c.introduction.split(/(?<=[.!?…])\s+/u)[0]}`}),60);
  });

  // ── 조합법 ──
  app.get('/recipes',limit,async(_req,reply)=>{
    const order=['UNCOMMON','SPECIAL','RARE','LEGEND','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'].filter(t=>content.recipes.some(r=>content.map.get(r.resultId)!.rarity===t));
    const chips=order.map(t=>`<a class="chip" style="color:${tierColor[t]}" href="#r-${t}">${esc(rarityNames[t])}</a>`).join('')+`<a class="chip" style="color:${tierColor.HIDDEN}" href="/hidden">히든 ❔</a>`;
    const sections=order.map(t=>{
      const list=content.recipes.filter(r=>content.map.get(r.resultId)!.rarity===t);
      return `<h2 id="r-${t}" style="scroll-margin-top:110px">${esc(rarityNames[t])} <small style="color:var(--muted);font-weight:500">${list.length}가지</small></h2>`+
        `<div class="recs">${list.map(r=>`<div class="rec"><div>${chip(r.resultId)}</div><div><span class="eq">=</span>${counts(r.materials).map(([id,n])=>chip(id,n)).join('<span class="eq">+</span>')}</div></div>`).join('')}</div>`;
    }).join('');
    const hiddenCount=content.characters.filter(c=>c.rarity==='HIDDEN').length;
    return send(reply,page('조합법','/recipes',`${recipeTabs('/recipes')}<h1>조합법</h1><p class="lead">누구랑 누구를 같은 자리로 발령 내면 무엇이 될까요? (수비대 인사팀 발령 명단) 이름을 누르면 그 갈매미의 상세로 가요. 히든 ${hiddenCount}종의 조합법은 비밀이에요. <a href="/hidden">단서 보러 가기</a></p><div class="chips">${chips}</div>${sections}`));
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
<div class="hint"><div class="h"><b>단서 1</b>${esc(c.hint??'재료로 직접 조합해서 찾아요')}</div><div class="h"><b>단서 2</b>재료 구성: ${esc(tiersLine)}</div>${h3}${h4}</div>
<p class="note" style="margin:8px 0 0">${who}</p></div>`;
    }).join('');
    const body=`<h1>❔ 서류에 없는 갈매미</h1>
<p class="lead">히든 ${hidden.length}종은 조합목록에 없어요. 카톡에서 <b>조합 고르기</b>로 재료를 직접 골라 섞어 봐야 찾을 수 있어요. 누군가 찾을 때마다 모두의 단서가 하나씩 더 열려요.</p>
<div class="stats"><div class="stat"><b>${found}/${hidden.length}</b>섬에서 발견된 히든</div><div class="stat"><b>1명</b>찾으면 초성 공개</div><div class="stat"><b>${HINT_NAME}명</b>찾으면 재료 이름 공개</div></div>
${cards}`;
    return send(reply,page('히든 단서','/hidden',recipeTabs('/hidden')+body),60);
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
      const shown=`${r.badge?`${esc(r.badge)} `:''}${esc(r.name)}`;
      const name=r.nick?`<a href="/p/${encodeURIComponent(r.name)}">${shown}</a>`:shown;
      return `<li${r.rank===1?' class="top1"':''}><span class="no">${medal(r.rank)}</span><img loading="lazy" src="${b.img}" alt="" width="56" height="56"><span class="who"><b>${name}</b><span>최고: ${esc(b.label)}</span></span><span class="sc">${tab[3]} ${r.score}${unit}</span></li>`;}).join('');
    const when=DateTime.fromJSDate(board.generatedAt).setZone('Asia/Seoul').toFormat('M/d HH:mm');
    const body=`<h1>랭킹</h1>${nav}
${list?`<ol class="rank">${list}</ol>`:'<div class="empty">아직 기록이 없어요. 카톡에서 첫 기록을 만들어 보세요!</div>'}
<p class="note">참여 ${board.total}명 중 상위 ${board.rows.length}명 · ${esc(when)} 기준(30초마다 갱신)${tab[2]==='WEEK'?' · 매주 월요일 0시(한국 시간)에 새로 시작':''}<br>
내 순위는 카톡에서 <b>랭킹</b>, 이름 표시는 <b>닉네임 원하는이름</b> 으로 정할 수 있어요. 닉네임이 있으면 이름을 눌러 프로필을 볼 수 있어요. 관리자 모드 계정은 빠져요.</p>`;
    return send(reply,page('랭킹','/rank',body),30);
  });

  // 자동완성용: 히든 제외 갈매미 목록, 닉네임 검색
  const searchIndex=()=>JSON.stringify(content.characters.filter(c=>c.rarity!=='HIDDEN').map(c=>({i:c.id,n:c.name,t:rarityNames[c.rarity],c:tierColor[c.rarity],p:img('thumbs',face(c))})));
  app.get('/search-index.json',limit,async(_req,reply)=>reply.type('application/json; charset=utf-8').header('Cache-Control','public, max-age=600').send(searchIndex()));
  app.get<{Querystring:{q?:string}}>('/api/players',limit,async(req,reply)=>{
    const q=String(req.query.q??'').trim().slice(0,20);
    return reply.header('Cache-Control','public, max-age=30').send(q?await data.searchPlayers(q):[]);
  });

  // ── 검색: 갈매미 이름(초성 가능) · 등급 · 이야기 · 닉네임 ──
  app.get<{Querystring:{q?:string}}>('/search',limit,async(req,reply)=>{
    const q=String(req.query.q??'').trim().slice(0,30);
    if(!q)return send(reply,page('검색','',`<h1>검색</h1><p class="lead">갈매미 이름이나 닉네임을 찾아요. 초성도 돼요. 예: <a href="/search?q=금갑">금갑</a> · <a href="/search?q=ㄱㄱ">ㄱㄱ</a> · <a href="/search?q=갈매미">갈매미</a></p>`,q),60);
    const plain=q.replace(/\s+/g,''),ini=isInitials(plain);
    const units=content.characters.filter(c=>c.rarity!=='HIDDEN'&&(ini?initials(c.name.replace(/\s+/g,'')).includes(plain):c.name.replace(/\s+/g,'').includes(plain))).slice(0,40);
    const players=ini?[]:await data.searchPlayers(q);
    const card=(c:Character)=>`<a class="card${topTiers.has(c.rarity)?' locked':''}" href="/dex/${c.id}"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge(c.rarity)}<h3>${esc(c.name)}</h3></div></a>`;
    // 등급 이름으로 찾기(예: 전설), 이야기 속 낱말로 찾기(히든·최상위 제외)
    const tier=Object.keys(rarityNames).find(t=>rarityNames[t]===plain&&t!=='HIDDEN');
    const byTier=tier?content.characters.filter(c=>c.rarity===tier):[];
    const byStory=ini?[]:content.characters.filter(c=>c.rarity!=='HIDDEN'&&!topTiers.has(c.rarity)&&!units.includes(c)&&c.introduction.replace(/\s+/g,'').includes(plain)).slice(0,24);
    // 이름이 정확히 맞으면: 이 갈매미로 만들 수 있는 것
    const exact=units.find(c=>c.name.replace(/\s+/g,'')===plain);
    const makes=exact?content.recipes.filter(r=>!r.hidden&&r.materials.includes(exact.id)):[];
    const total=units.length+byTier.length+byStory.length;
    const body=`<h1>“${esc(q)}” 검색</h1>
${exact?`<div class="box"><h3>${badge(exact.rarity)} <a href="/dex/${exact.id}">${esc(exact.name)}</a></h3>${makes.length?`이 갈매미로 만들 수 있는 것 (${makes.length}): ${makes.map(r=>chip(r.resultId)).join('')}`:'<span class="note">재료로 쓰이는 조합이 없어요.</span>'}</div>`:''}
${players.length?`<h2>🧑‍✈️ 수비대원 ${players.length}명</h2>${players.map(n=>`<a class="uc" href="/p/${encodeURIComponent(n)}"><img class="gi" src="/gull.png" alt=""> ${esc(n)}</a>`).join('')}`:''}
<h2>🐦 이름에 “${esc(q)}” ${units.length}종</h2>${units.length?`<div class="grid">${units.map(card).join('')}</div>`:'<p class="note">이름이 맞는 갈매미가 없어요. 히든은 검색되지 않아요. <a href="/hidden">히든 단서 보기</a></p>'}
${byTier.length?`<h2>${badge(tier!)} ${esc(rarityNames[tier!])} 등급 ${byTier.length}종</h2><div class="grid">${byTier.map(card).join('')}</div>`:''}
${byStory.length?`<h2>📖 이야기에 “${esc(q)}” ${byStory.length}종</h2><div class="grid">${byStory.map(card).join('')}</div>`:''}
${!total&&!players.length?'<p class="note">예: <a href="/search?q=전설">전설</a> · <a href="/search?q=빤스">빤스</a> · <a href="/search?q=ㄱㄱ">ㄱㄱ</a></p>':''}`;
    return send(reply,page(`${q} 검색`,'',body,q),60);
  });

  // ── 프로필 ──
  // ── 검색엔진용: robots.txt · sitemap.xml (주소는 economy.homepage 기준) ──
  // 갈매미 아이콘(돌 이모지 대신 쓰는 그림)·파비콘
  // 앱 설치(PWA) 파일: 아이콘·앱 정보·서비스 워커(사이트 전체를 맡으려면 맨 위 주소여야 함)·연결 끊김 화면
  const statics:[string,string,string,string][]=[
    ['/gull.png','public/gull.png','image/png','public, max-age=86400'],['/favicon.png','public/favicon-32.png','image/png','public, max-age=86400'],['/favicon.ico','public/favicon-32.png','image/png','public, max-age=86400'],
    ...[192,512].flatMap(n=>[[`/icon-${n}.png`,`public/icon-${n}.png`,'image/png','public, max-age=86400'],[`/icon-${n}-maskable.png`,`public/icon-${n}-maskable.png`,'image/png','public, max-age=86400']] as [string,string,string,string][]),
    ['/manifest.webmanifest','public/pwa/manifest.webmanifest','application/manifest+json; charset=utf-8','public, max-age=3600'],
    ['/sw.js','public/pwa/sw.js','text/javascript; charset=utf-8','no-cache'],
    ['/pwa/offline.html','public/pwa/offline.html','text/html; charset=utf-8','public, max-age=3600'],
  ];
  for(const [path,file,type,cache] of statics)
    app.get(path,async(_req,reply)=>reply.type(type).header('Cache-Control',cache).send(readFileSync(resolve(options.root,file))));
  // 구글 플레이 앱(TWA)과 이 사이트를 잇는 파일. config/assetlinks.json이 있을 때만(서명 키 지문은 플레이 콘솔에서 받음)
  app.get('/.well-known/assetlinks.json',async(_req,reply)=>{
    try{return reply.type('application/json').header('Cache-Control','public, max-age=3600').send(readFileSync(resolve(options.root,'config/assetlinks.json')));}
    catch{return reply.code(404).send({error:'not found'});}
  });
  // 안드로이드 앱 「갈매미맨 수비대」 내려받기(「내 갈매미」 맨 아래 설치 칸). 파일은 app/publish-apk.mjs로 public/app/에 넣음
  app.get('/app/apk.json',async(_req,reply)=>{
    try{return reply.type('application/json; charset=utf-8').header('Cache-Control','no-cache').send(readFileSync(resolve(options.root,'public/app/apk.json')));}
    catch{return reply.code(404).send({error:'not found'});}
  });
  app.get('/app/galmaegiman-suhbidae.apk',async(_req,reply)=>{
    const f=resolve(options.root,'public/app/galmaegiman-suhbidae.apk');
    if(!existsSync(f))return reply.code(404).send({error:'not found'});
    return reply.type('application/vnd.android.package-archive').header('Cache-Control','no-cache')
      .header('Content-Disposition','attachment; filename="galmaegiman-suhbidae.apk"').send(createReadStream(f));
  });
  app.get('/app/native.json',async(_req,reply)=>{
    const file=resolve(options.root,'public/app/native.json');
    return existsSync(file)?reply.type('application/json').header('Cache-Control','no-cache').send(readFileSync(file)):reply.code(404).send({error:'not found'});
  });
  app.get('/app/galmaegiman-native.apk',async(_req,reply)=>{
    const file=resolve(options.root,'public/app/galmaegiman-native.apk');
    return existsSync(file)?reply.type('application/vnd.android.package-archive').header('Cache-Control','no-cache').header('Content-Disposition','attachment; filename="galmaegiman-native.apk"').send(createReadStream(file)):reply.code(404).send({error:'not found'});
  });
  app.get('/app/native',async(_req,reply)=>{
    const file=resolve(options.root,'public/app/native.json'),meta=existsSync(file)?JSON.parse(readFileSync(file,'utf8')) as {versionName:string;size:number;date:string}:null;
    return send(reply,page('네이티브 앱 받기','/app/native',`<h1>갈매미맨 수비대 · 네이티브 앱</h1><p class="lead">로그인부터 스토리·전투·뽑기·조합·탐험까지 Android 앱에서 플레이해요.</p>${meta?`<p>${esc(meta.versionName)} · ${(meta.size/1048576).toFixed(1)}MB · ${esc(meta.date)}</p><a class="btn lg" href="/app/galmaegiman-native.apk" download>⬇ 네이티브 APK 받기</a>`:'<p>다운로드를 준비하고 있어요.</p>'}<p>별도 설치하는 시험판이에요. 기존 앱을 지우지 않아도 돼요. 같은 계정으로 로그인하면 갈매미와 스토리 진행을 이어서 사용해요.</p><ol><li>APK를 받은 뒤 파일을 열어요.</li><li>브라우저의 앱 설치를 허용하고 설치해요.</li><li>카톡에서 「웹 로그인」을 보내 받은 코드 또는 카카오로 로그인해요.</li></ol>`));
  });
  app.get('/robots.txt',limit,async(_req,reply)=>reply.type('text/plain; charset=utf-8').header('Cache-Control','public, max-age=3600')
    .send(`User-agent: *\nAllow: /\nDisallow: /api/\n${site?`Sitemap: ${site}/sitemap.xml\n`:''}`));
  app.get('/sitemap.xml',limit,async(_req,reply)=>{
    // 히든 상세는 히든 페이지로 넘어가므로 빼고, 프로필·검색 결과는 넣지 않음
    const paths=['/','/event','/world','/dex','/recipes','/hidden','/talk','/rank',...content.characters.filter(c=>c.rarity!=='HIDDEN').map(c=>`/dex/${c.id}`)];
    const xml=`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${paths.map(p=>`<url><loc>${esc(site+p)}</loc></url>`).join('\n')}\n</urlset>\n`;
    return reply.type('application/xml; charset=utf-8').header('Cache-Control','public, max-age=3600').send(xml);
  });

  // ── 수다방: 이번 주 인기 한마디 + 최근 한마디 ──
  app.get('/talk',limit,async(_req,reply)=>{
    const [top,recent]=await Promise.all([data.topComments(5),data.recentComments(40)]);
    const body=`<h1>💬 수다방</h1><p class="lead">갈매미마다 남긴 수비대원들의 한마디예요. 마음에 드는 말에는 👍를 눌러 주세요.</p>
${howToComment()}
<h2>🔥 이번 주 인기 한마디</h2>${top.length?`<ul class="cms">${top.map(x=>commentItem(x,true)).join('')}</ul>`:'<div class="empty">아직 좋아요를 받은 한마디가 없어요.</div>'}
<h2>🕊 방금 올라온 한마디</h2>${recent.length?`<ul class="cms">${recent.map(x=>commentItem(x,true)).join('')}</ul>`:'<div class="empty">아직 댓글이 없어요. 카톡에서 첫 댓글을 남겨 보세요!</div>'}`;
    return send(reply,page('수다방','/talk',body),15);
  });
  // 좋아요(웹): 방문자 주소를 그대로 저장하지 않고 해시로만 구분(같은 방문자는 댓글당 1번)
  app.post<{Params:{id:string}}>('/api/comments/:id/like',{config:{rateLimit:{max:30,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`like:${ip}`:'like';}}}},async(req,reply)=>{
    const ip=req.headers['cf-connecting-ip'];
    const visitor=createHash('sha256').update(`${options.salt??''}|${typeof ip==='string'?ip:'local'}`).digest('hex').slice(0,32);
    const r=await data.like(Number(req.params.id),visitor);
    if(!r)return reply.code(404).send({error:'not found'});
    return reply.header('Cache-Control','no-store').send(r);
  });

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
<div><h1 style="margin:.1em 0">${p.badge?esc(p.badge):'<img class="gi" src="/gull.png" alt="">'} ${esc(p.nickname)}</h1>
<p class="note">최고 갈매미: ${esc(bestLabel)} · 입사 ${esc(DateTime.fromJSDate(p.joined).setZone('Asia/Seoul').toFormat('yyyy. M. d.'))}</p>
${p.titles.length?`<p>${p.titles.map(t=>`<span class="title">🏆 ${esc(t)}</span>`).join('')}</p>`:''}
<div class="stats"><div class="stat"><b>${p.dex}/${content.characters.length}</b>도감</div><div class="stat"><b>${p.firsts}개</b>히든 최초 발견</div></div>
<div class="box"><h3>🏆 순위</h3>📚 도감 ${rk(p.dexRank,'종')}<br>🗿 최상위 ${rk(p.topRank,'마리')}<br>🧩 이번 주 조합 ${rk(p.weekRank,'회')}</div>
</div></div>
<h2>등급별 수집</h2><div class="bars box">${bars}</div>`;
    return send(reply,page(p.nickname,'',body),60);
  });
}
