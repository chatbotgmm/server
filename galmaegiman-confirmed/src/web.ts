// 공개 웹페이지: 홈 · 이벤트 · 수다방 · 세계관 · 도감(+상세) · 조합법 · 히든 단서 · 랭킹 · 검색 · 프로필
// 누구나 보는 페이지라 스포일러를 가립니다.
// - 히든: 이름·그림·이야기 대신 ??? 와 단서(발견한 사람이 늘수록 더 열림)
// - 최상위(제한~불멸): 이름만, 그림은 등급 실루엣, 이야기는 가림
// 세계관 글은 data/world.md(마크다운)를 고치면 바로 반영됩니다.
import { readFileSync, statSync } from 'node:fs';
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
  const worldHtml=()=>{
    const path=resolve(options.root,'data/world.md');
    try{
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
+ps.slice(0,4).map(p=>'<a href="/p/'+encodeURIComponent(p)+'">🪨 '+e(p)+'<small>수비대원</small></a>').join('')
+'<a href="/search?q='+encodeURIComponent(i.value.trim())+'">🔍 “'+e(i.value.trim())+'” 전체 검색</a>';box.hidden=false}
i.addEventListener('input',()=>{clearTimeout(t);t=setTimeout(run,120)});i.addEventListener('focus',()=>{load();if(i.value)run()});
document.addEventListener('click',ev=>{if(!f.contains(ev.target))box.hidden=true});
i.addEventListener('keydown',ev=>{const a=[...box.querySelectorAll('a')];if(box.hidden||!a.length)return;let k=a.findIndex(x=>x.classList.contains('on'));
if(ev.key==='ArrowDown'||ev.key==='ArrowUp'){ev.preventDefault();a[k]?.classList.remove('on');k=ev.key==='ArrowDown'?(k+1)%a.length:(k<=0?a.length-1:k-1);a[k].classList.add('on')}
else if(ev.key==='Enter'&&k>=0){ev.preventDefault();location.href=a[k].href}else if(ev.key==='Escape')box.hidden=true});})();`;
  // 댓글 한 줄: 닉네임(프로필 링크)·시간·좋아요 버튼. unit=true면 어느 유닛 댓글인지 칩도
  const when=(d:Date)=>DateTime.fromJSDate(d).setZone('Asia/Seoul').toFormat('M/d HH:mm');
  const commentItem=(c:WebComment,unit=false)=>`<li class="cm"><div class="cm-h"><a href="/p/${encodeURIComponent(c.name)}"><b>${c.badge?`${esc(c.badge)} `:''}${esc(c.name)}</b></a> <span class="note">${esc(when(c.at))}</span>${unit?` ${chip(c.characterId)}`:''}</div>
<p>${esc(c.text)}</p><button class="like" type="button" data-id="${c.id}" aria-label="좋아요">👍 <b>${c.likes}</b></button></li>`;
  const likeJs=`document.addEventListener('click',async ev=>{const b=ev.target.closest&&ev.target.closest('button.like');if(!b||b.disabled)return;b.disabled=true;
try{const r=await fetch('/api/comments/'+b.dataset.id+'/like',{method:'POST'});if(r.ok){const j=await r.json();b.querySelector('b').textContent=j.likes;b.classList.add('on');}else b.disabled=false}catch(_){b.disabled=false}});`;
  const howToComment=(name?:string)=>`<p class="note">💬 카톡에서 <span class="mono">댓글 ${esc(name??'갈매미이름')} 하고 싶은 말</span> 로 남겨요. 만나 본 갈매미에게만 쓸 수 있고, 닉네임이 함께 보여요.</p>`;
  // 메뉴: 자주 쓰는 순서. 모바일 하단 탭은 6칸이라 히든은 조합법 탭에 묶음(히든 페이지에서 조합법 탭이 켜짐)
  const navItems=[['/','홈','🏝'],['/event','이벤트','🎁'],['/dex','도감','📚'],['/recipes','조합법','🧩'],['/hidden','히든','❔'],['/talk','수다방','💬'],['/rank','랭킹','🏆'],['/world','세계관','📜']];
  const tabItems=navItems.filter(([h])=>h!=='/hidden');
  const tabOn=(h:string,active:string)=>active===h||(h==='/recipes'&&active==='/hidden');
  // 조합법·히든 페이지 위 전환 탭
  const recipeTabs=(active:string)=>`<div class="tabs"><a href="/recipes"${active==='/recipes'?' class="on"':''}>🧩 조합법</a><a href="/hidden"${active==='/hidden'?' class="on"':''}>❔ 히든 단서</a></div>`;
  // 공유 미리보기(카톡·SNS): 절대 주소가 필요해 economy.homepage 를 씀
  const site=(content.economy.homepage??'').replace(/\/$/,'');
  const channel=content.economy.kakaoChannel;
  // 카톡 채널 버튼: 말풍선 아이콘(이모지 대신 단색 그림) + 글자. big은 홈·이벤트 본문용
  const bubble='<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M12 3C6.5 3 2 6.6 2 11c0 2.8 1.8 5.3 4.6 6.7l-.9 3.4c-.1.4.3.7.6.5l4-2.7c.6.1 1.1.1 1.7.1 5.5 0 10-3.6 10-8S17.5 3 12 3z"/></svg>';
  const playBtn=(big=false)=>channel?`<a class="play${big?' big':''}" href="${esc(channel)}" target="_blank" rel="noopener">${bubble}<span>카톡에서 플레이</span></a>`:'';
  const logo=()=>exists('logo')?`<img src="${img('images','logo')}" alt="갈매미맨" height="40" style="height:40px;width:auto;vertical-align:middle">`:'🪨 갈매미맨';
  interface Og{image?:string;desc?:string}
  const page=(title:string,active:string,body:string,q='',og:Og={})=>`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)} · 갈매미맨</title>
<meta name="description" content="${esc(og.desc??'돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기')}">
<meta property="og:type" content="website"><meta property="og:site_name" content="갈매미맨"><meta property="og:description" content="${esc(og.desc??'돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기')}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link href="https://fonts.googleapis.com/css2?family=Jua&display=swap" rel="stylesheet">
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22%3E%3Ctext y=%22.9em%22 font-size=%2290%22%3E%F0%9F%AA%A8%3C/text%3E%3C/svg%3E">
<meta property="og:title" content="${esc(title)} · 갈매미맨"><meta property="og:image" content="${site}${og.image??img('images','C1')}">
<style>
:root{--c1:#2a6fc0;--c2:#d9483b;--c3:#0f8a7b;--c4:#7a4fd0;--c5:#b06f00;--c6:#c93f7c;--bg:#f6f4ef;--card:#fff;--text:#2b2b2b;--muted:#6b6b6b;--line:#e2ddd3;--accent:#1f4e79;--accent-soft:#e3ecf5;--gold:#d9a400}
@media (prefers-color-scheme:dark){:root{--c1:#7db4ff;--c2:#ff8a7a;--c3:#5fd3c3;--c4:#b9a0ff;--c5:#f2b84b;--c6:#ff92c2;--bg:#16181c;--card:#20242a;--text:#ececec;--muted:#a3a3a3;--line:#353a42;--accent:#8fb8e8;--accent-soft:#25303d}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
h1,h2,.logo,.stat b,.links a b,.today h3{font-family:"Jua",system-ui,sans-serif;font-weight:400;letter-spacing:.01em}
/* 사이트 전체 Jua(굵기 1개뿐): 가짜 굵게를 끄고, 강조는 색으로 */
body{font-synthesis:none}
p b,p strong,li b,li strong,.prose td b,.prose td strong{color:var(--accent)}
.event b,.event strong{color:#a0461a}
body{background-image:radial-gradient(var(--line) 1px,transparent 1.2px),linear-gradient(180deg,transparent 0,transparent 70%,rgba(31,78,121,.06) 100%);background-size:22px 22px,100% 100%;background-attachment:fixed}
.tabbar{display:none}
@media (max-width:719px){
 header nav{display:none}
 .tabbar{display:grid;grid-template-columns:repeat(7,1fr);position:fixed;left:0;right:0;bottom:0;z-index:30;background:var(--card);border-top:1px solid var(--line);padding:4px 2px calc(4px + env(safe-area-inset-bottom))}
 .tabbar a{display:flex;flex-direction:column;align-items:center;gap:1px;text-decoration:none;color:var(--muted);font-size:.66rem;white-space:nowrap;font-weight:700;padding:4px 0;border-radius:10px}
 .tabbar a i{font-style:normal;font-size:1.25rem;line-height:1.2}
 .tabbar a.on{color:var(--accent);background:var(--accent-soft)}
 main{padding-bottom:96px}
 footer{padding-bottom:96px}
}
.play{display:inline-flex;align-items:center;justify-content:center;gap:6px;padding:7px 14px;border-radius:999px;background:#fee500;color:#191600;font-weight:800;text-decoration:none;font-size:.9rem;white-space:nowrap;box-shadow:0 1px 0 rgba(0,0,0,.08)}
.play svg{flex:none}.play:hover{background:#fada0a}
.mylink{display:inline-flex;align-items:center;padding:7px 12px;border-radius:999px;border:1.5px solid var(--line);color:var(--text);text-decoration:none;font-size:.9rem;white-space:nowrap}
.play.big{padding:12px 22px;font-size:1rem;box-shadow:0 4px 14px rgba(254,229,0,.35)}
body{margin:0;background:var(--bg);color:var(--text);font-family:"Jua",system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif;line-height:1.65;word-break:keep-all;overflow-wrap:anywhere}
a{color:var(--accent)}
header{background:var(--card);border-bottom:1px solid var(--line)}
@media (min-width:900px){header{position:sticky;top:0;z-index:5}}
.bar{max-width:1080px;margin:0 auto;padding:8px 16px;display:flex;align-items:center;gap:8px 14px;flex-wrap:wrap}
.logo{font-weight:800;font-size:1.1rem;text-decoration:none;color:var(--text)}
nav{display:flex;gap:2px;flex-wrap:wrap}
nav a{text-decoration:none;color:var(--muted);padding:5px 10px;border-radius:999px;font-weight:600;font-size:.92rem}
nav a.on{background:var(--accent-soft);color:var(--accent)}
form.search{display:flex;gap:6px;flex:1 1 300px;min-width:0}
form.search input{flex:1;min-width:0;padding:8px 12px;border-radius:999px;border:1.5px solid var(--line);background:var(--bg);color:var(--text);font:inherit;font-size:.95rem}
form.search button{padding:8px 14px;border-radius:999px;border:0;background:var(--accent);color:#fff;font-weight:700;font:inherit}
@media (prefers-color-scheme:dark){form.search button{color:#10151b}}
form.search{position:relative}
.sug{position:absolute;left:0;right:0;top:calc(100% + 6px);z-index:20;background:var(--card);border:1px solid var(--line);border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,.18);padding:6px;max-height:70vh;overflow:auto}
.sug a,.sug span{display:flex;align-items:center;gap:10px;padding:7px 8px;border-radius:10px;text-decoration:none;color:var(--text);font-weight:600}
.sug a:hover,.sug a.on{background:var(--accent-soft)}
.sug img{width:32px;height:32px;border-radius:8px;object-fit:cover}
.sug small{margin-left:auto;font-size:.75rem;font-weight:800}
.sug span{color:var(--muted);font-weight:500}
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
.btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px;align-items:center}
.btn{display:inline-flex;align-items:center;justify-content:center;padding:11px 18px;border-radius:999px;background:var(--card);color:var(--text);border:1.5px solid var(--line);text-decoration:none;font-weight:700;font-size:.95rem}
.btn:hover{border-color:var(--accent);color:var(--accent)}
@media (max-width:719px){.hero .btns .play.big{flex:1 1 100%}.hero .btns .btn{flex:1 1 0}}
.event{margin:18px 0;padding:14px 16px;border-radius:14px;background:linear-gradient(135deg,#fff3c4,#ffe0ef);color:#3b2a00;border:1px solid #f0d27a}
.event b{font-size:1.05rem}.event a{color:#7a4300;font-weight:800}
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
.today{display:grid;grid-template-columns:120px 1fr;gap:12px;align-items:center;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:10px;text-decoration:none;color:var(--text);box-shadow:0 6px 24px rgba(0,0,0,.08)}
@media (min-width:720px){.today{grid-template-columns:1fr;max-width:340px;justify-self:center}}
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
.tiles{display:grid;grid-template-columns:repeat(auto-fill,minmax(84px,1fr));gap:8px}
.tile{display:block;text-decoration:none;color:var(--text);text-align:center;font-size:.72rem;font-weight:700;line-height:1.25}
.tile img{width:100%;height:auto;aspect-ratio:1;border-radius:10px;display:block;margin-bottom:3px;background:var(--line)}
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
.links a:nth-child(6n+1){border-top:3px solid var(--c1)}.links a:nth-child(6n+2){border-top:3px solid var(--c2)}.links a:nth-child(6n+3){border-top:3px solid var(--c3)}
.links a:nth-child(6n+4){border-top:3px solid var(--c4)}.links a:nth-child(6n+5){border-top:3px solid var(--c5)}.links a:nth-child(6n+6){border-top:3px solid var(--c6)}
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
</style></head><body>
<header><div class="bar"><a class="logo" href="/">${logo()}</a>
<nav>${navItems.map(([h,l,i])=>`<a href="${h}"${active===h?' class="on"':''}>${i} ${l}</a>`).join('')}</nav>${playBtn()}<a class="mylink" href="/my" title="카카오·카톡 코드로 로그인해서 조합·편성·상점">🔑 로그인 · 내 갈매미</a>
<form class="search" action="/search" method="get" role="search" autocomplete="off"><input name="q" value="${esc(q)}" placeholder="갈매미·닉네임·이야기 검색 (초성 OK)" aria-label="검색" maxlength="30"><button>검색</button><div class="sug" hidden></div></form></div></header>
<main>${body}${piece(active)}</main>
<nav class="tabbar" aria-label="메뉴">${tabItems.map(([h,l,i])=>`<a href="${h}"${tabOn(h,active)?' class="on"':''}><i>${i}</i>${l}</a>`).join('')}</nav>
<footer>갈매미맨 · 카카오톡 채널 챗봇 게임 · v${esc(options.version)}</footer>
<script>${suggestJs}${likeJs}</script>
</body></html>`;

  // 검색엔진용 표준 주소(canonical): 보기 옵션(?view, ?sort) 없는 주소로 묶음. 랭킹 탭(?k)만 유지. 검색 결과는 색인하지 않음(noindex)
  const headFor=(url:string)=>{
    const [path,qs='']=url.split('?');
    if(path==='/search')return '<meta name="robots" content="noindex">';
    const k=new URLSearchParams(qs).get('k');
    const keep=path==='/rank'&&k&&/^(dex|top|week)$/.test(k)?`?k=${k}`:'';
    return site?`<link rel="canonical" href="${esc(site+path+keep)}">`:'';
  };
  const send=(reply:FastifyReply,html:string,maxAge=300)=>reply.type('text/html; charset=utf-8').header('Cache-Control',`public, max-age=${maxAge}`)
    .send(html.replace('</head>',`${headFor(reply.request.url)}</head>`));
  // 방문자마다 따로 셉니다. 서버는 127.0.0.1에서만 받고 Cloudflare 터널을 거쳐 오므로 CF-Connecting-IP가 실제 방문자 주소예요
  // (헤더가 없으면 한 묶음으로 셈)
  const limit={config:{rateLimit:{max:300,timeWindow:'1 minute',keyGenerator:(req:FastifyRequest)=>{const ip=req.headers['cf-connecting-ip'];return typeof ip==='string'&&ip.length<=45?`web:${ip}`:'web';}}}};
  const notFound=(reply:FastifyReply,msg:string)=>reply.code(404).type('text/html; charset=utf-8').send(page('없음','',`<div class="empty">${esc(msg)}<br><a href="/">홈으로</a></div>`));

  // ── 홈: 오늘의 갈매미 · 이번 주 TOP 3 · 최근 발견된 히든 · 이벤트 ──
  app.get('/',limit,async(_req,reply)=>{
    const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
    const c=huntCoupon();
    // 오늘의 갈매미: 한국 날짜마다 바뀜(히든·최상위 제외)
    const pool=content.characters.filter(x=>x.rarity!=='HIDDEN'&&!topTiers.has(x.rarity));
    const day=Math.floor(DateTime.now().setZone('Asia/Seoul').startOf('day').toSeconds()/86400);
    const today=pool[((day%pool.length)+pool.length)%pool.length];
    const week=await data.ranking('WEEK');
    const talk=await data.recentComments(3);
    const board=week.rows.length?{rows:week.rows,unit:'회',label:'이번 주 조합',k:'week'}:{rows:(await data.ranking('DEX')).rows,unit:`/${content.characters.length}`,label:'도감 수집',k:'dex'};
    const medal=['🥇','🥈','🥉'];
    const top3=board.rows.slice(0,3).map((r,i)=>`<li><span class="no">${medal[i]}</span><span class="who"><b>${r.badge?`${esc(r.badge)} `:''}${r.nick?`<a href="/p/${encodeURIComponent(r.name)}">${esc(r.name)}</a>`:esc(r.name)}</b></span><span class="sc">${r.score}${board.unit}</span></li>`).join('');
    const hidden=content.characters.filter(x=>x.rarity==='HIDDEN');
    const recent=[...(await data.hiddenFirsts())].sort((x,y)=>y[1].at.getTime()-x[1].at.getTime()).slice(0,3)
      .map(([id,f])=>{const n=hidden.findIndex(x=>x.id===id)+1;return `<a class="uc" href="/hidden#h${n}"><img src="${img('thumbs','unknown')}" alt="" width="28" height="28">히든 #${n} 발견! · ${f.name?esc(f.name):'이름 없는 갈매미'} · ${esc(DateTime.fromJSDate(f.at).setZone('Asia/Seoul').toFormat('M/d'))}</a>`;}).join('');
    const body=`<section class="hero"><div>
<h1>갈매미는 <span class="hl">존재</span>한다.</h1>
<p class="lead">돌로 된 갈매미들이 <b>갈매미 섬 항구 수비대</b>에 출근해서 항구를 지켜요. 몸이 돌이라 날지는 못해요. 새우깡 봉지 소리에는 약해요.</p>
<div class="btns">${playBtn(true)}<a class="btn" href="/world">세계관 보기</a><a class="btn" href="/dex">도감 보기</a></div>
</div><a class="today" href="/dex/${today.id}"><img src="${img('thumbs',today.id)}" alt="${esc(today.name)}" width="480" height="480"><div><small>☀️ 오늘의 갈매미</small><h3>${esc(today.name)}</h3><p>${esc(today.introduction.split(/(?<=[.!?…])\s+/u)[0])}</p></div></a></section>
${c?`<div class="event"><b>🎉 ${esc(c.label)} 이벤트</b><br>이 사이트 어딘가에 <b>쿠폰 조각 ${c.pieces.length}개</b>가 숨어 있어요. 모두 찾아 카톡에 <b>쿠폰 조각들</b>을 보내면 뽑기권 <b>${c.tickets}장</b>! (1인 1회)<br><a href="/event">🎁 이벤트·혜택 모두 보기 →</a></div>`:`<div class="event"><b>🎁 이벤트·혜택</b><br>친구 초대하면 친구도 나도 뽑기권 <b>${content.economy.invite.tickets}장</b>! <a href="/event">자세히 보기 →</a></div>`}
<div class="homegrid">
<div class="box"><h3>🏆 ${board.label} TOP 3</h3>${top3?`<ol class="mini">${top3}</ol>`:'<p class="note">아직 기록이 없어요.</p>'}<a class="note" href="/rank?k=${board.k}">전체 랭킹 →</a></div>
<div class="box"><h3>💬 최근 한마디</h3>${talk.length?`<ul class="cms">${talk.map(x=>commentItem(x,true)).join('')}</ul>`:'<p class="note">아직 댓글이 없어요. 카톡에서 <b>댓글 갈매미이름 하고 싶은 말</b>!</p>'}<br><a class="note" href="/talk">수다방 가기 →</a></div>
<div class="box"><h3>❔ 최근 발견된 히든</h3>${recent||'<p class="note">아직 아무도 히든을 찾지 못했어요. 첫 발견자가 되어 보세요!</p>'}<br><a class="note" href="/hidden">히든 단서 보기 →</a></div>
</div>
<div class="stats">
<div class="stat"><b>${content.characters.length}종</b>모을 수 있는 갈매미</div>
<div class="stat"><b>${content.recipes.length}가지</b>합체 조합</div>
<div class="stat"><b>${count('HIDDEN')}종</b>서류에 없는 갈매미</div>
<div class="stat"><b>7곳</b>섬의 동네</div></div>
<div class="links">
<a href="/event"><b>🎁 이벤트</b><span>쿠폰·친구 초대·튜토리얼 보상</span></a>
<a href="/world"><b>📜 세계관</b><span>섬과 동네, 악당 끼룩단</span></a>
<a href="/dex"><b>📚 도감</b><span>${content.characters.length}종의 이야기</span></a>
<a href="/recipes"><b>🧩 조합법</b><span>누구를 같은 자리로 발령 낼까</span></a>
<a href="/hidden"><b>❔ 히든 단서</b><span>함께 찾을수록 열리는 단서</span></a>
<a href="/talk"><b>💬 수다방</b><span>갈매미마다 남긴 한마디</span></a>
<a href="/rank"><b>🏆 랭킹</b><span>수비대 근무 평가</span></a>
</div>`;
    return send(reply,page('홈','/',body,'',{image:img('images',today.id),desc:`오늘의 갈매미: ${today.name} · 돌로 된 갈매미들이 항구 수비대에 출근하는 섬`}),60);
  });

  // ── 이벤트·혜택: 진행 중 쿠폰·기간 이벤트 + 상시 혜택(가입·뽑기권 받기·튜토리얼·친구 초대) ──
  app.get('/event',limit,async(_req,reply)=>{
    const e=content.economy,at=now();
    const kst=(iso:string)=>DateTime.fromISO(iso).setZone('Asia/Seoul').toFormat('M월 d일 HH:mm');
    const period=(from?:string,to?:string)=>to?`${from?`${kst(from)} ~ `:''}${kst(to)}까지`:'끝날 때까지 (종료일 미정)';
    const live=e.events.filter(x=>new Date(x.from)<=at&&at<new Date(x.to));
    const soon=e.events.filter(x=>at<new Date(x.from)).sort((a,b)=>a.from.localeCompare(b.from));
    const effects=(x:typeof e.events[number])=>[x.claimBonus?`뽑기권 받기 +${x.claimBonus}장`:'',x.snackMultiplier>1?`새우깡 ×${x.snackMultiplier}`:''].filter(Boolean).join(' · ')||'특별 이벤트';
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

  app.get('/world',limit,async(_req,reply)=>send(reply,page('세계관','/world',`<article class="prose tale">${worldHtml()}</article>`)));

  // ── 도감 목록 ──
  app.get<{Querystring:{view?:string;sort?:string}}>('/dex',limit,async(req,reply)=>{
    // 보기: 큰 카드(기본) / 작은 그림(view=s). 정렬: 기본 / 이름순(sort=name)
    const small=req.query.view==='s',byName=req.query.sort==='name';
    const qs=(v:boolean,n:boolean)=>{const p=[...(v?['view=s']:[]),...(n?['sort=name']:[])];return `/dex${p.length?`?${p.join('&')}`:''}`;};
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
    return send(reply,page('도감','/dex',`<h1>갈매미 도감</h1><p class="lead">${content.characters.length}종의 갈매미. 카드를 누르면 만드는 법과 재료로 쓰이는 곳을 볼 수 있어요.</p><div class="tabs"><a href="${qs(false,byName)}"${small?'':' class="on"'}>🃏 큰 카드</a><a href="${qs(true,byName)}"${small?' class="on"':''}>🔳 작은 그림</a><a href="${qs(small,false)}"${byName?'':' class="on"'}>번호순</a><a href="${qs(small,true)}"${byName?' class="on"':''}>가나다순</a></div><div class="chips">${chips}</div>${sections}`));
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
        list.map(r=>`<div class="rec"><div>${chip(r.resultId)}</div><div><span class="eq">=</span>${counts(r.materials).map(([id,n])=>chip(id,n)).join('<span class="eq">+</span>')}</div></div>`).join('');
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
${players.length?`<h2>🧑‍✈️ 수비대원 ${players.length}명</h2>${players.map(n=>`<a class="uc" href="/p/${encodeURIComponent(n)}">🪨 ${esc(n)}</a>`).join('')}`:''}
<h2>🐦 이름에 “${esc(q)}” ${units.length}종</h2>${units.length?`<div class="grid">${units.map(card).join('')}</div>`:'<p class="note">이름이 맞는 갈매미가 없어요. 히든은 검색되지 않아요. <a href="/hidden">히든 단서 보기</a></p>'}
${byTier.length?`<h2>${badge(tier!)} ${esc(rarityNames[tier!])} 등급 ${byTier.length}종</h2><div class="grid">${byTier.map(card).join('')}</div>`:''}
${byStory.length?`<h2>📖 이야기에 “${esc(q)}” ${byStory.length}종</h2><div class="grid">${byStory.map(card).join('')}</div>`:''}
${!total&&!players.length?'<p class="note">예: <a href="/search?q=전설">전설</a> · <a href="/search?q=빤스">빤스</a> · <a href="/search?q=ㄱㄱ">ㄱㄱ</a></p>':''}`;
    return send(reply,page(`${q} 검색`,'',body,q),60);
  });

  // ── 프로필 ──
  // ── 검색엔진용: robots.txt · sitemap.xml (주소는 economy.homepage 기준) ──
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
<div><h1 style="margin:.1em 0">${p.badge?esc(p.badge):'🪨'} ${esc(p.nickname)}</h1>
<p class="note">최고 갈매미: ${esc(bestLabel)} · 입사 ${esc(DateTime.fromJSDate(p.joined).setZone('Asia/Seoul').toFormat('yyyy. M. d.'))}</p>
${p.titles.length?`<p>${p.titles.map(t=>`<span class="title">🏆 ${esc(t)}</span>`).join('')}</p>`:''}
<div class="stats"><div class="stat"><b>${p.dex}/${content.characters.length}</b>도감</div><div class="stat"><b>${p.firsts}개</b>히든 최초 발견</div></div>
<div class="box"><h3>🏆 순위</h3>📚 도감 ${rk(p.dexRank,'종')}<br>🗿 최상위 ${rk(p.topRank,'마리')}<br>🧩 이번 주 조합 ${rk(p.weekRank,'회')}</div>
</div></div>
<h2>등급별 수집</h2><div class="bars box">${bars}</div>`;
    return send(reply,page(p.nickname,'',body),60);
  });
}
