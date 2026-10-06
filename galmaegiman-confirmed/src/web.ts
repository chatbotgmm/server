// 공개 웹페이지: 홈 · 세계관 · 도감. 누구나 보는 페이지라 스포일러를 가립니다.
// - 히든: 이름·그림·이야기 대신 ??? 와 힌트
// - 최상위(제한~불멸): 이름만, 그림은 등급 실루엣, 이야기는 가림
// 세계관 글은 data/world.md(마크다운)를 고치면 바로 반영됩니다.
import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import type { FastifyInstance, FastifyReply } from 'fastify';
import { marked } from 'marked';
import { DateTime } from 'luxon';
import type { Character, Content } from './content.js';
import { rarityNames, topTiers } from './content.js';

const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const tierColor:Record<string,string>={COMMON:'#7d7d7d',UNCOMMON:'#2e9d5b',SPECIAL:'#1a9fb3',RARE:'#8b4fd1',LEGEND:'#c99a1c',HIDDEN:'#5b3f8c',
  LIMITED:'#c8373b',TRANSCEND:'#3f86d4',ETERNAL:'#1f9e8f',IMMORTAL:'#d9a400',NEUTRAL:'#9a948a'};
// 웹 도감 등급 순서(특수함은 안흔함 다음)
const dexOrder=['COMMON','UNCOMMON','NEUTRAL','SPECIAL','RARE','LEGEND','HIDDEN','LIMITED','TRANSCEND','ETERNAL','IMMORTAL'] as const;

export interface RankRow{rank:number;name:string;score:number;best:Character|null}
export interface WebOptions{root:string;stamp:(path:string)=>string;version:string;ranking?:(kind:'DEX'|'TOP'|'WEEK')=>Promise<{total:number;generatedAt:Date;rows:RankRow[]}>}

export function registerWeb(app:FastifyInstance,content:Content,options:WebOptions){
  const img=(kind:'thumbs'|'images',id:string)=>{const file=`${kind}/${id}.${kind==='thumbs'?'jpg':'png'}`;const v=options.stamp(file);return `/${file}${v?`?v=${v}`:''}`;};
  const exists=(id:string)=>{try{statSync(resolve(options.root,'public/images',`${id}.png`));return true;}catch{return false;}};
  const face=(c:Character)=>c.rarity==='HIDDEN'?'unknown':topTiers.has(c.rarity)?(exists(`q-${c.rarity}`)?`q-${c.rarity}`:'unknown'):c.id;

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

  const page=(title:string,active:string,body:string)=>`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)} · 갈매미맨</title>
<meta name="description" content="돌로 된 갈매미들이 항구 수비대에 출근하는 섬, 갈매미 섬 이야기">
<meta property="og:title" content="${esc(title)} · 갈매미맨"><meta property="og:image" content="${img('images','C1')}">
<style>
:root{--bg:#f6f4ef;--card:#fff;--text:#2b2b2b;--muted:#6b6b6b;--line:#e2ddd3;--accent:#1f4e79;--accent-soft:#e3ecf5}
@media (prefers-color-scheme:dark){:root{--bg:#16181c;--card:#20242a;--text:#ececec;--muted:#a3a3a3;--line:#353a42;--accent:#8fb8e8;--accent-soft:#25303d}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,"Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif;line-height:1.65;word-break:keep-all;overflow-wrap:anywhere}
a{color:var(--accent)}
header{position:sticky;top:0;z-index:5;background:var(--card);border-bottom:1px solid var(--line)}
.bar{max-width:1080px;margin:0 auto;padding:10px 16px;display:flex;align-items:center;gap:14px;flex-wrap:wrap}
.logo{font-weight:800;font-size:1.1rem;text-decoration:none;color:var(--text)}
nav{display:flex;gap:6px;flex-wrap:wrap}
nav a{text-decoration:none;color:var(--muted);padding:6px 12px;border-radius:999px;font-weight:600;font-size:.95rem}
nav a.on{background:var(--accent-soft);color:var(--accent)}
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
.lead{font-size:1.1rem;color:var(--muted)}
.btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px}
.btn{display:inline-block;padding:11px 18px;border-radius:12px;background:var(--accent);color:#fff;text-decoration:none;font-weight:700}
@media (prefers-color-scheme:dark){.btn{color:#10151b}}
.btn.ghost{background:transparent;color:var(--accent);border:2px solid var(--accent)}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:24px 0}
.stat{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px}
.stat b{display:block;font-size:1.4rem}
.chips{display:flex;gap:6px;flex-wrap:wrap;margin:6px 0 4px;position:sticky;top:56px;z-index:4;background:var(--bg);padding:8px 0}
.chip{text-decoration:none;font-size:.85rem;font-weight:700;padding:5px 10px;border-radius:999px;border:1.5px solid currentColor;background:var(--card)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
@media (min-width:720px){.grid{grid-template-columns:repeat(auto-fill,minmax(220px,1fr))}}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;overflow:hidden;display:flex;flex-direction:column}
.card img{width:100%;height:auto;aspect-ratio:1;object-fit:cover;display:block;background:var(--line)}
.card .in{padding:10px 12px 12px}
.badge{display:inline-block;font-size:.75rem;font-weight:800;color:#fff;padding:2px 8px;border-radius:999px}
.card h3{margin:6px 0 4px;font-size:1rem}
.card p{margin:4px 0;font-size:.88rem}
.card .q{color:var(--muted)}.card .r{color:var(--muted);font-size:.8rem}
.card.locked .in p{color:var(--muted)}

.tabs{display:flex;gap:6px;margin:4px 0 14px;flex-wrap:wrap}
.tabs a{text-decoration:none;padding:7px 14px;border-radius:999px;border:1.5px solid var(--line);color:var(--muted);font-weight:700;background:var(--card)}
.tabs a.on{border-color:var(--accent);color:var(--accent);background:var(--accent-soft)}
.rank{list-style:none;margin:0;padding:0;display:grid;gap:8px}
.rank li{display:grid;grid-template-columns:44px 56px 1fr auto;align-items:center;gap:10px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:8px 12px}
.rank li.top1{border-color:#d9a400;box-shadow:0 0 0 2px rgba(217,164,0,.25)}
.rank .no{font-size:1.25rem;font-weight:800;text-align:center}
.rank img{width:56px;height:56px;border-radius:10px;object-fit:cover;background:var(--line)}
.rank .who b{display:block;font-size:1rem}.rank .who span{font-size:.82rem;color:var(--muted)}
.rank .sc{font-weight:800;font-size:1.05rem;white-space:nowrap}
.empty{background:var(--card);border:1px dashed var(--line);border-radius:14px;padding:24px;text-align:center;color:var(--muted)}
.note{color:var(--muted);font-size:.85rem}
footer{text-align:center;color:var(--muted);font-size:.8rem;padding:24px 16px 40px}
</style></head><body>
<header><div class="bar"><a class="logo" href="/">🪨 갈매미맨</a>
<nav>${[['/','홈'],['/world','세계관'],['/dex','도감'],['/rank','랭킹']].map(([h,l])=>`<a href="${h}"${active===h?' class="on"':''}>${l}</a>`).join('')}</nav></div></header>
<main>${body}</main>
<footer>갈매미맨 · 카카오톡 채널 챗봇 게임 · v${esc(options.version)}</footer>
</body></html>`;

  const send=(reply:FastifyReply,html:string)=>reply.type('text/html; charset=utf-8').header('Cache-Control','public, max-age=300').send(html);
  const limit={config:{rateLimit:{max:600,timeWindow:'1 minute',keyGenerator:()=> 'web'}}};

  app.get('/',limit,async(_req,reply)=>{
    const count=(t:string)=>content.characters.filter(c=>c.rarity===t).length;
    const body=`<section class="hero"><div>
<h1>갈매미는 존재한다.</h1>
<p class="lead">돌로 된 갈매미들이 <b>갈매미 섬 항구 수비대</b>에 출근해서 항구를 지켜요. 몸이 돌이라 날지는 못해요. 새우깡 봉지 소리에는 약해요.</p>
<div class="btns"><a class="btn" href="/world">세계관 보기</a><a class="btn ghost" href="/dex">도감 보기</a><a class="btn ghost" href="/rank">랭킹 보기</a></div>
</div><img src="${img('images','C1')}" alt="기본 갈매미맨" width="800" height="800"></section>
<div class="stats">
<div class="stat"><b>${content.characters.length}종</b>모을 수 있는 갈매미</div>
<div class="stat"><b>${content.recipes.length}가지</b>합체 조합</div>
<div class="stat"><b>${count('HIDDEN')}종</b>서류에 없는 갈매미</div>
<div class="stat"><b>7곳</b>섬의 동네</div></div>
<p>카카오톡 채널 챗봇에서 갈매미를 뽑고, 끌어안아 합체시키고, 끼룩단 섬으로 출장을 보내 보세요. 수비대원들의 순위는 <a href="/rank">랭킹</a>에서 볼 수 있어요.</p>`;
    return send(reply,page('홈','/',body));
  });

  app.get('/world',limit,async(_req,reply)=>send(reply,page('세계관','/world',`<article class="prose">${worldHtml()}</article>`)));

  app.get<{Querystring:{k?:string}}>('/rank',limit,async(req,reply)=>{
    const tabs=[['dex','도감 수집','DEX','📚'],['top','최상위 보유','TOP','🗿'],['week','이번 주 조합','WEEK','🧩']] as const;
    const tab=tabs.find(t=>t[0]===req.query.k)??tabs[0];
    const nav=`<div class="tabs">${tabs.map(t=>`<a href="/rank?k=${t[0]}"${t===tab?' class="on"':''}>${t[3]} ${t[1]}</a>`).join('')}</div>`;
    if(!options.ranking)return send(reply,page('랭킹','/rank',`<h1>랭킹</h1>${nav}<div class="empty">랭킹을 불러올 수 없어요.</div>`));
    const board=await options.ranking(tab[2]);
    const medal=(n:number)=>['🥇','🥈','🥉'][n-1]??String(n);
    // 웹 공개 규칙: 히든 최고 유닛은 ???, 최상위는 이름만(그림은 등급 실루엣)
    const bestOf=(c:Character|null)=>{
      if(!c)return {img:img('thumbs','unknown'),label:'아직 없음'};
      if(c.rarity==='HIDDEN')return {img:img('thumbs','unknown'),label:`${rarityNames[c.rarity]} ???`};
      return {img:img('thumbs',face(c)),label:`${rarityNames[c.rarity]} ${c.name}`};
    };
    const unit=tab[2]==='DEX'?`/${content.characters.length}`:tab[2]==='TOP'?'마리':'회';
    const list=board.rows.map(r=>{const b=bestOf(r.best);
      return `<li${r.rank===1?' class="top1"':''}><span class="no">${medal(r.rank)}</span><img loading="lazy" src="${b.img}" alt="" width="56" height="56"><span class="who"><b>${esc(r.name)}</b><span>최고: ${esc(b.label)}</span></span><span class="sc">${tab[3]} ${r.score}${unit}</span></li>`;}).join('');
    const when=DateTime.fromJSDate(board.generatedAt).setZone('Asia/Seoul').toFormat('M/d HH:mm');
    const body=`<h1>랭킹</h1>${nav}
${list?`<ol class="rank">${list}</ol>`:'<div class="empty">아직 기록이 없어요. 카톡에서 첫 기록을 만들어 보세요!</div>'}
<p class="note">참여 ${board.total}명 중 상위 ${board.rows.length}명 · ${esc(when)} 기준(30초마다 갱신)${tab[2]==='WEEK'?' · 매주 월요일 0시(한국 시간)에 새로 시작':''}<br>
내 순위는 카톡에서 <b>랭킹</b>, 이름 표시는 <b>닉네임 원하는이름</b> 으로 정할 수 있어요. 관리자 모드 계정은 빠져요.</p>`;
    return reply.type('text/html; charset=utf-8').header('Cache-Control','public, max-age=30').send(page('랭킹','/rank',body));
  });

  app.get('/dex',limit,async(_req,reply)=>{
    const card=(c:Character)=>{
      const color=tierColor[c.rarity],badge=`<span class="badge" style="background:${color}">${esc(rarityNames[c.rarity])}</span>`;
      if(c.rarity==='HIDDEN')return `<div class="card locked"><img loading="lazy" src="${img('thumbs','unknown')}" alt="???" width="480" height="480"><div class="in">${badge}<h3>???</h3><p>❔ ${esc(c.hint??'합치기로 찾아요')}</p></div></div>`;
      if(topTiers.has(c.rarity))return `<div class="card locked"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge}<h3>${esc(c.name)}</h3><p>이 원형의 모습과 이야기는 게임에서 직접 만나면 열려요.</p></div></div>`;
      const recipe=content.recipes.find(r=>r.resultId===c.id);
      const mats=recipe?[...recipe.materials.reduce((m,id)=>m.set(id,(m.get(id)??0)+1),new Map<string,number>())].map(([id,n])=>{const x=content.map.get(id)!;return x.rarity==='HIDDEN'?'???':`${x.name}${n>1?` ×${n}`:''}`;}).join(' + '):'';
      return `<div class="card"><img loading="lazy" src="${img('thumbs',face(c))}" alt="${esc(c.name)}" width="480" height="480"><div class="in">${badge}<h3>${esc(c.name)}</h3><p>${esc(c.introduction)}</p><p class="q">💬 「${esc(c.quote)}」</p>${mats?`<p class="r">🧩 ${esc(mats)}</p>`:''}</div></div>`;
    };
    const tiers=dexOrder.filter(t=>content.characters.some(c=>c.rarity===t));
    const chips=tiers.map(t=>`<a class="chip" style="color:${tierColor[t]}" href="#${t}">${esc(rarityNames[t])}</a>`).join('');
    const sections=tiers.map(t=>{const list=content.characters.filter(c=>c.rarity===t);
      return `<h2 id="${t}" style="scroll-margin-top:110px">${esc(rarityNames[t])} <small style="color:var(--muted);font-weight:500">${list.length}종</small></h2><div class="grid">${list.map(card).join('')}</div>`;}).join('');
    return send(reply,page('도감','/dex',`<h1>갈매미 도감</h1><p class="lead">${content.characters.length}종의 갈매미. 히든과 최상위 원형은 게임에서 직접 만나면 열려요.</p><div class="chips">${chips}</div>${sections}`));
  });
}
