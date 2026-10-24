import {RealtimeRankedClient} from './realtime-ranked-client.js';

// casual-ai-disclosure-v3: only the server room kind identifies an AI opponent.
const aiNotice=r=>r?.opponentKind==='AI'?'AI 대전 · 일반 MMR에 반영되지 않아요':'';
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
// Separate presentation adapter: no createBattle, act, pickAi, aiChoose or replay settlement.
export function createRealtimeScreen({api,host,confirmLeave,onExit,imageBase=''}) {
  let destroyed=false,visible=false,phase=null,augment=null,switchTo=null,followup=null;
  let draftTeam=[],draftDirty=false,rosterPage=0;
  let selectedItem=null,itemTarget=null;
  const waitingLines=['어울리는 대전 상대를 찾아요.','갈매기들이 상대를 부르고 있어요.','수비대는 출전 준비 중!','잠깐 날개를 쭉 펴 볼까요?'];
  let waitingCopyIndex=0;
  const root=document.createElement('section');root.className='rt-ranked';root.hidden=true;host.append(root);
  const style=document.createElement('style');style.textContent=`
  .rt-ranked[hidden]{display:none!important}.rt-ranked{position:fixed;inset:0;z-index:250;background:#2b231df5;color:#3d2918;display:grid;place-items:center;padding:1.5vh 2vw;box-sizing:border-box}
  .rt-paper{width:min(1100px,96vw);height:96dvh;box-sizing:border-box;background:linear-gradient(110deg,#eddbb3,#fff1ce 50%,#e5c99c);border:3px solid #79572f;border-radius:18px;box-shadow:0 8px 35px #0008;display:flex;flex-direction:column;gap:8px;padding:clamp(8px,2vh,20px);overflow:hidden}
  .rt-paper h2,.rt-paper p{margin:0}.rt-head,.rt-row,.rt-team{display:flex;align-items:center;justify-content:space-between;gap:8px}.rt-head{flex-wrap:wrap}.rt-clock{font-weight:800;font-variant-numeric:tabular-nums}.rt-arena{display:grid;grid-template-columns:1fr 1fr;gap:16px;min-height:0;flex:1}.rt-side{display:flex;align-items:center;gap:10px;min-height:0}.rt-side img{width:clamp(50px,12vw,160px);max-height:22vh;object-fit:contain}.rt-side meter{width:100%}.rt-side small{display:block}.rt-controls{flex:0 1 auto;min-height:0;display:grid;gap:6px}.rt-options{display:flex;gap:8px;min-height:0}.rt-options>button,.rt-options>article{flex:1;min-width:0}.rt-ranked button{color:#3d2918;background:#fff5d8;border:2px solid #a3824f;border-radius:10px;padding:8px;cursor:pointer}.rt-ranked button:disabled{opacity:.5;cursor:default}.rt-ranked button[aria-pressed=true]{border-color:#593800;background:#f4cb72}.rt-aug{padding:8px;border:1px solid #b59460;border-radius:10px;display:flex;flex-direction:column;gap:6px}.rt-aug>button:first-child{flex:1}.rt-aug b,.rt-aug small{display:block}.rt-aug small{font-size:clamp(10px,1.6vh,15px)}.rt-log{font-size:12px;white-space:nowrap;text-overflow:ellipsis;overflow:hidden}.rt-error{color:#8a241b}.rt-foot{font-size:12px}.rt-actions{display:flex;flex-wrap:wrap;gap:6px}.rt-actions button{flex:1}.rt-queue{justify-content:center;text-align:center;height:auto;min-height:40vh}.rt-sr{position:absolute;left:-9999px}
  @media(max-height:550px){.rt-paper{gap:4px;padding:8px}.rt-paper h2{font-size:17px}.rt-ranked button{padding:5px}.rt-side img{max-height:16vh}.rt-aug{padding:4px}.rt-foot{font-size:10px}.rt-log{display:none}}

  /* realtime-queue-ui-v3: only matchmaking, never the augment/battle layout */
  .rt-match{width:min(760px,96vw);height:min(650px,94dvh);min-height:0;display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:clamp(8px,2vh,20px);padding:clamp(14px,3vw,30px);overflow:hidden;background:radial-gradient(ellipse at top,#fff9e9,#eee0bf);border:2px solid #a88a51;box-shadow:inset 0 0 0 4px #fff7e5,0 12px 45px #0005}
  .rt-match-head{display:flex;justify-content:space-between;align-items:center;gap:10px}.rt-match-mode{border:1px solid #b39a6d;border-radius:99px;padding:5px 12px;font-size:14px;font-weight:700}.rt-match-kicker{font-size:12px;letter-spacing:.12em;color:#70634e}
  .rt-match-main{display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:clamp(8px,2vh,18px);min-height:0}.rt-match-emblem{display:grid;place-items:center;border:2px solid #b39a6d;outline:1px solid #d2bc90;outline-offset:5px;border-radius:50%;width:62px;height:62px;font-size:30px;color:#416d65;background:#f6ecd5}.rt-match h2{font-size:clamp(20px,4vw,30px);line-height:1.3}.rt-match-time{font-size:clamp(27px,5vw,42px);font-variant-numeric:tabular-nums;font-weight:700;letter-spacing:.06em}.rt-match-time small{display:block;font-size:12px;letter-spacing:0;font-weight:400;margin-bottom:4px}.rt-match-policy{max-width:48ch;font-size:clamp(12px,1.8vw,15px);line-height:1.5;color:#5e5140;word-break:keep-all}.rt-match-status{font-size:13px;color:#59674e;min-height:1.4em}.rt-match-error{color:#8b3428;font-size:13px;line-height:1.4;overflow-wrap:anywhere;max-height:3em;overflow:hidden}
  .rt-match-foot{display:grid;gap:8px}.rt-match-buttons{display:flex;gap:8px;justify-content:center}.rt-match-buttons button{min-height:48px;flex:1;max-width:360px;font-size:16px;font-weight:700}.rt-match-buttons [data-cmd=cancel],.rt-match-buttons [data-cmd=matchAccept]{background:#416d65;color:#fff9e9;border-color:#31544e;box-shadow:0 3px 0 #294840}.rt-match-note{text-align:center;color:#75654d;font-size:12px;line-height:1.4}
  @media(max-height:450px) and (orientation:landscape){.rt-match{width:min(920px,96vw);height:94dvh;grid-template-columns:minmax(0,1fr) minmax(0,1fr);grid-template-rows:auto minmax(0,1fr);padding:12px 18px;gap:8px 24px}.rt-match-head{grid-column:1/-1}.rt-match-main{grid-column:1;gap:5px}.rt-match-foot{grid-column:2;grid-row:2;align-content:center;gap:10px}.rt-match-emblem{width:38px;height:38px;font-size:20px;outline-offset:3px}.rt-match h2{font-size:20px}.rt-match-time{font-size:26px}.rt-match-policy{font-size:12px}.rt-match-kicker{font-size:10px}}
  @media(prefers-reduced-motion:reduce){.rt-match *{animation:none!important;transition:none!important}}
  /* queue-cleanup-v3.3: small CSS flourish; no assets or extra queue controls. */
  .rt-search-clean .rt-match-status{max-width:42ch;line-height:1.5;word-break:keep-all;overflow-wrap:anywhere}
  .rt-search-clean .rt-match-emblem{border-color:#b9aa8a;outline-color:#ded2b7;background:#faf3e2}
  .rt-search-drift{animation:rt-search-drift 4s ease-in-out infinite}
  @keyframes rt-search-drift{0%,100%{transform:translateY(0) rotate(-4deg)}50%{transform:translateY(-4px) rotate(4deg)}}
  @media(prefers-reduced-motion:reduce){.rt-search-drift{animation:none!important}}
  `;document.head.append(style);
  const client=new RealtimeRankedClient({api,onChange:draw});
  const show=()=>{visible=true;root.hidden=false;draw(client.state);};
  const close=()=>{if(client.dismiss()){visible=false;root.hidden=true;onExit();}};
  function button(label,cmd,disabled=false,pressed=false){return `<button data-cmd="${esc(cmd)}" ${disabled?'disabled':''} aria-pressed="${pressed}">${label}</button>`;}
  function draw(s) {
    if(!visible)return;
    if(s.matchDismissed){visible=false;root.hidden=true;client.dismiss();onExit();return;}
    const r=s.room,b=r?.battle,t=b?.teams?.[0],foe=b?.teams?.[1];
    if(!r){
      const terminal=['idle','cancelled','expired','failed'].includes(s.status),lost=s.status==='disconnected',matched=s.status==='matched';
      const labels={preparing:'대전을 준비하고 있어요',joining:'대기열에 연결하고 있어요',waiting:'상대를 찾고 있어요',recovering:'매칭을 확인하고 있어요',reconnecting:'다시 연결하고 있어요',cancelling:'취소를 확인하고 있어요',cancelled:'매칭을 취소했어요',expired:'매칭 대기가 끝났어요',idle:'지금은 대기 중이 아니에요',failed:'매칭을 시작하지 못했어요',disconnected:'연결을 확인하지 못했어요',matched:'상대를 찾았어요'};
      const mode=s.mode==='normal'?'일반전':s.mode==='rank'?'랭크전':'대전 매칭';
      const canClose=terminal||(lost&&client.roomId);
      const showWait=s.status==='waiting'&&!s.error;
      root.innerHTML=`<div class="rt-paper rt-match rt-search-clean" role="dialog" aria-modal="true" aria-label="${esc(mode)} 매칭"><header class="rt-match-head"><span class="rt-match-mode">${esc(mode)}</span><span class="rt-match-kicker">수비대 대전</span></header><div class="rt-match-main"><span class="rt-match-emblem ${showWait?'rt-search-drift':''}" aria-hidden="true">${terminal||lost?'◇':matched?'⚔':'⌕'}</span><h2>${esc(labels[s.status]||'상태 확인 중')}</h2>${showWait?`${client.queueTimeVisible()?'<div class="rt-match-time"><small>대기 시간</small><span data-queue-elapsed>00:00</span></div>':''}<button type="button" class="rt-match-status rt-wait-copy" data-wait-copy aria-label="${esc(waitingLines[waitingCopyIndex])} · 다음 대기 문구 보기">${esc(waitingLines[waitingCopyIndex])}</button>`:`<p class="rt-match-status" role="status">${esc(s.error||(s.status==='cancelling'?'서버 확인이 끝나면 알려드릴게요.':matched?'대전 화면으로 연결하고 있어요.':terminal?'모드 선택으로 돌아갈 수 있어요.':'잠시만 기다려 주세요.'))}</p>`}</div><footer class="rt-match-foot"><div class="rt-match-buttons">${canClose?button('닫기','close'):matched?'':button(s.status==='cancelling'?'취소 확인 중…':'매칭 취소','cancel',s.status==='cancelling')}</div>${s.status==='failed'&&client.attempt?'<p class="rt-match-note">취소는 아직 확인되지 않았어요. 다음 접속 때 이어서 확인해요.</p>':!terminal&&!lost?'<p class="rt-match-note">먼저 매칭이 확정되면 수락 화면으로 연결돼요.</p>':''}</footer></div>`;
      bind();clock();return;
    }
    if(phase!==r.phaseId){phase=r.phaseId;augment=null;switchTo=null;followup=null;selectedItem=null;itemTarget=null;draftTeam=[...(r.preparation?.selectedIds||[])];draftDirty=false;rosterPage=0;}
    if(r.phase==='accept'){
      const accepted=!!r.acceptance?.accepted,blocked=accepted||!r.canSubmit||s.pending;
      root.innerHTML=`<div class="rt-paper rt-match" role="dialog" aria-modal="true" aria-label="대전 수락"><header class="rt-match-head"><span class="rt-match-mode">${r.mode==='rank'?'랭크전':'일반전'}</span><span class="rt-match-kicker">상대를 찾았어요</span></header><div class="rt-match-main"><span class="rt-match-emblem" aria-hidden="true">⚔</span><h2>${accepted?'수락했어요':'대전을 수락할까요?'}</h2><div class="rt-match-time"><small>수락 제한 시간</small><span class="rt-clock"></span></div><p class="rt-match-status" role="status">${accepted?'상대의 수락을 기다리고 있어요.':r.acceptance?.opponentAccepted?'상대가 수락했어요.':'10초 안에 수락해 주세요.'}</p></div><footer class="rt-match-foot"><p class="rt-match-policy">${aiNotice(r)?esc(aiNotice(r)):'둘 다 수락하면 출전 준비로 넘어가요.'}<br>상대가 거절하거나 응답하지 않으면, 수락한 유저는 다시 매칭을 기다려요.</p><p class="rt-match-error">${esc(s.error)}</p><div class="rt-match-buttons">${button(accepted?'수락 완료':s.pending?'수락 확인 중…':'수락','matchAccept',blocked)}${button('거절','matchDecline',blocked)}</div>${s.error&&client.pendingCommand?button('응답 다시 확인','retry',!!client.sending):''}</footer></div>`;
      bind();clock();return;
    }
    if(r.result && !b){
      const title=r.result.winner===null?'무승부':r.result.winner===0?'승리!':'패배';
      root.innerHTML=`<div class="rt-paper rt-queue" role="dialog" aria-modal="true" aria-label="대전 결과"><h2>${title}</h2><p>${aiNotice(r)?esc(aiNotice(r))+" · ":""}${esc(r.result.reason)}</p><p>${r.result.settlement==='pending'?'점수 확정 중…':'대전이 종료됐어요.'}</p>${button('모드 고르기로','close')}</div>`;
      bind();return;
    }
    if(r.phase==='prepare') {
      const p=r.preparation;
      if(!p)throw new Error('준비 화면 정보를 확인하지 못했어요.');
      if(JSON.stringify(p.selectedIds)===JSON.stringify(draftTeam))draftDirty=false;
      if(!draftDirty)draftTeam=[...p.selectedIds];
      const blocked=!r.canSubmit||p.ready||s.pending;
      const pages=Math.max(1,Math.ceil(p.available.length/6));rosterPage=Math.min(rosterPage,pages-1);
      const choices=p.available.slice(rosterPage*6,rosterPage*6+6);
      root.innerHTML=`<div class="rt-paper" role="dialog" aria-modal="true" aria-label="대전 준비"><header class="rt-head"><h2>${r.mode==='rank'?'랭크':'일반'} 대전 준비</h2><span class="rt-clock"></span>${button('항복','surrender',s.pending)}</header><p>${aiNotice(r)?esc(aiNotice(r))+" · ":""}수비대 3마리를 골라요. 양쪽이 준비되면 바로 증강체 선택으로 넘어가요.</p><div class="rt-actions">${draftTeam.map(id=>{const u=p.available.find(x=>x.id===id);return button(`선택 해제: ${esc(u?.name||id)}`,`prepRemove:${p.available.findIndex(x=>x.id===id)}`,blocked);}).join('')}</div><div class="rt-prep-grid" style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px;flex:1;min-height:0">${choices.map(u=>{const index=p.available.indexOf(u),selected=draftTeam.includes(u.id);return button(`<img src="${esc(imageBase+u.image)}" alt="" style="max-height:12vh;max-width:45%;object-fit:contain"><b>${esc(u.name)}</b><small>${esc(u.rarity)}</small>`,`prepPick:${index}`,blocked||(!selected&&draftTeam.length>=3),selected);}).join('')}</div><div class="rt-row">${button('이전','prepPrev',rosterPage===0)}<span>${rosterPage+1}/${pages}</span>${button('다음','prepNext',rosterPage===pages-1)}</div><p role="status">${p.ready?'준비 완료 · 상대를 기다려요.':s.pending?'편성 저장 확인 중…':draftDirty?'3마리를 모두 골라야 서버에 저장돼요.':`편성 저장됨 · ${draftTeam.length}/3`}${p.opponentReady?' · 상대 준비 완료':''}</p>${button('준비 완료','prepReady',blocked||draftTeam.length!==3)}<p class="rt-error" role="status">${esc(s.error)}</p>${s.error&&client.pendingCommand?button('같은 편성 저장 다시 확인','retry'):''}<p class="rt-foot">준비 30초 후 마지막으로 서버에 저장된 편성으로 시작해요. 상대 편성은 준비가 끝날 때 공개돼요.</p></div>`;
      bind();clock();return;
    }
    if(augment && !t.offers.some(a=>a.id===augment))augment=null;
    const blocked=!r.canSubmit||r.mySubmitted||s.pending||!!r.result;
    const fighter=team=>{const f=team.fighters[team.active];return `<div class="rt-side"><img src="${esc(imageBase+f.image)}" alt=""><div><b>${esc(f.name)}</b><small>${esc(f.attr)} · ${f.hp}/${f.maxHp}</small><meter min="0" max="${f.maxHp}" value="${Math.max(0,f.hp)}"></meter><small>${(f.effects||[]).map(e=>esc(`${e.label} ${e.valueText}`)).join(' · ')}</small></div></div>`;};
    let controls='';
    if(r.result){
      const title=r.result.winner===null?'무승부':r.result.winner===0?'승리!':'패배';
      const delta=r.result.rank?.lpDelta;
      controls=`<h2>${title}</h2><p>${esc(r.result.reason)} · ${r.result.settlement==='pending'?'점수 확정 중…':r.mode==='rank'?`점수 확정 완료${typeof delta==='number'?` · LP ${delta>=0?'+':''}${delta}`:''}`:'일반전은 점수가 없어요.'}</p>${button('모드 고르기로','close')}`;
    } else if(b.phase==='choose') {
      controls=`<div class="rt-options">${t.offers.map((a,i)=>`<article class="rt-aug">${button(`<b>${esc(a.name)}</b><small>${esc(a.desc)}</small>`,`augment:${i}`,blocked,augment===a.id)}${button('다시 뽑기',`reroll:${i}`,blocked||a.rerolled)}</article>`).join('')}</div>`;
      if(t.needSwitch) controls+=`<div class="rt-actions">${t.fighters.map((f,i)=>button(esc(f.name),`chooseSwitch:${i}`,blocked||f.hp<=0,switchTo===i)).join('')}</div>`;
      const ready=(!t.offers.length||augment!==null)&&(!t.needSwitch||switchTo!==null);
      controls+=button('선택 확정','choose',blocked||!ready);
    } else if(b.phase==='action') {
      const f=t.fighters[t.active];
      controls=`<div class="rt-actions">${t.legal.filter(a=>a.kind==='skill').map((a,i)=>button(esc(f.skills.find(k=>k.id===a.skillId)?.name||a.skillId),`skill:${i}`,blocked)).join('')}</div><div class="rt-actions">${t.legal.filter(a=>a.kind==='switch').map((a,i)=>button(`교체: ${esc(t.fighters[a.to].name)}`,`switch:${i}`,blocked,switchTo===a.to)).join('')}</div>`;
      if(switchTo!==null){
        const free=t.augments.some(a=>a.id==='freeswitch');
        if(free){controls+=`<div class="rt-actions">${(t.switchFollowups?.[String(switchTo)]||[]).map((k,i)=>button(esc(k.name),`followup:${i}`,blocked,followup===k.id)).join('')}</div>`;}
        controls+=button(free?'교체 + 기술 확정':'교체 확정','switchConfirm',blocked||(free&&!followup));
      }
      const stock=id=>(b.inventory||[]).find(x=>x.id===id)?.count||0;
      controls+=`<div class="rt-actions">${(b.items||[]).map((it,i)=>button(`${esc(it.name)} ×${stock(it.id)}${it.reason?` · ${esc(it.reason)}`:''}`,`itemPick:${i}`,blocked||stock(it.id)<=0||!!it.reason||(b.itemLimit>0&&t.itemsUsed>=b.itemLimit),selectedItem===it.id)).join('')}</div>`;
      const item=(b.items||[]).find(x=>x.id===selectedItem);
      if(item){
        if(item.revive>0)controls+=`<div class="rt-actions">${t.fighters.map((unit,i)=>button(`부활: ${esc(unit.name)}`,`itemTarget:${i}`,blocked||unit.hp>0,itemTarget===i)).join('')}</div>`;
        controls+=button(`${esc(item.name)} 사용 확정`,`itemConfirm`,blocked||!!item.reason||stock(item.id)<=0||(b.itemLimit>0&&t.itemsUsed>=b.itemLimit)||(item.revive>0&&itemTarget===null));
      }
    }
    root.innerHTML=`<div class="rt-paper" role="dialog" aria-modal="true" aria-label="실시간 대전"><header class="rt-head"><h2>${r.mode==='rank'?'랭크게임':'일반게임'} · ${b.turn}턴</h2><span class="rt-clock"></span>${r.result?'':button('항복','surrender',s.pending)}</header><div class="rt-arena">${fighter(t)}${fighter(foe)}</div><p role="status">${aiNotice(r)?esc(aiNotice(r))+" · ":""}${r.mySubmitted?'선택 완료 · 상대를 기다려요.':r.opponentReady?'상대 선택 완료':'동시에 선택해요.'}</p><div class="rt-controls">${controls}</div><p class="rt-error" role="status">${esc(s.error)}</p>${s.error&&client.pendingCommand?button('같은 선택 다시 확인','retry'):''}${s.error&&client.pendingSurrender?button('항복 처리 다시 확인','surrenderRetry'):''}<p class="rt-log">${esc((b.log||[]).slice(-2).join(' / '))}</p><p class="rt-foot">30초가 지나면 서버가 기본 공격·교체·증강체를 자동 선택해요. 행동 3회 연속 미입력 시 패배해요.</p></div>`;
    bind();clock();
  }
  function bind(){const copy=root.querySelector('[data-wait-copy]');if(copy)copy.onclick=()=>{
    if(destroyed||!visible||copy!==root.querySelector('[data-wait-copy]')||client.state.status!=='waiting'||client.state.error)return;
    waitingCopyIndex=(waitingCopyIndex+1)%waitingLines.length;copy.textContent=waitingLines[waitingCopyIndex];copy.setAttribute('aria-label',`${waitingLines[waitingCopyIndex]} · 다음 대기 문구 보기`);
  };const rendered=client.state.room,generation=client.generation;root.querySelectorAll('[data-cmd]').forEach(el=>el.onclick=async()=>{
    const [cmd,n]=el.dataset.cmd.split(':'),i=Number(n),r=client.state.room,t=r?.battle?.teams[0];
    if(generation!==client.generation)return;
    if(rendered && (r?.roomId!==rendered.roomId || r?.phaseId!==rendered.phaseId || r?.revision!==rendered.revision))return;
    if(cmd==='cancel')client.cancel();
    else if(cmd==='close')close();
    else if(cmd==='matchAccept'||cmd==='matchDecline')void client.submit({accept:cmd==='matchAccept'});
    else if(cmd==='retry')void client.retryCommand();
    else if(cmd==='prepPrev'){rosterPage=Math.max(0,rosterPage-1);draw(client.state);}
    else if(cmd==='prepNext'){rosterPage++;draw(client.state);}
    else if(cmd==='prepPick'||cmd==='prepRemove'){
      const p=r.preparation;if(!p||p.ready||client.state.pending||!r.canSubmit)return;
      const id=p.available[i]?.id;if(!id)return;
      draftTeam=draftTeam.includes(id)?draftTeam.filter(x=>x!==id):draftTeam.length<3?[...draftTeam,id]:draftTeam;
      draftDirty=true;draw(client.state);
      // Serial submission: while this request is unresolved, roster buttons stay disabled.
      // Only complete valid lineups are saved; timeout retains the previous server draft.
      if(draftTeam.length===3)void client.submit({prepare:{team:[...draftTeam],ready:false}});
    }
    else if(cmd==='prepReady'){if(draftTeam.length===3)void client.submit({prepare:{team:[...draftTeam],ready:true}});}
    else if(cmd==='surrenderRetry')void client.surrender();
    else if(cmd==='surrender'){if(await confirmLeave('항복하면 패배로 처리돼요. 항복할까요?')){const latest=client.state.room;if(generation===client.generation && latest?.roomId===rendered?.roomId && latest?.phaseId===rendered?.phaseId && !latest?.result)void client.surrender();}}
    else if(cmd==='augment'){augment=t.offers[i].id;draw(client.state);}
    else if(cmd==='chooseSwitch'){switchTo=i;draw(client.state);}
    else if(cmd==='reroll')void client.submit({reroll:i});
    else if(cmd==='choose')void client.submit({choose:{...(augment?{augment}:{}),...(switchTo!==null?{switchTo}:{})}});
    else if(cmd==='skill')void client.submit({action:t.legal.filter(a=>a.kind==='skill')[i]});
    else if(cmd==='switch'){switchTo=t.legal.filter(a=>a.kind==='switch')[i].to;followup=null;selectedItem=null;itemTarget=null;draw(client.state);}
    else if(cmd==='followup'){followup=t.switchFollowups[String(switchTo)][i].id;draw(client.state);}
    else if(cmd==='switchConfirm')void client.submit({action:{kind:'switch',to:switchTo,...(followup?{then:followup}:{})}});
    else if(cmd==='itemPick'){selectedItem=r.battle.items[i].id;itemTarget=null;switchTo=null;followup=null;draw(client.state);}
    else if(cmd==='itemTarget'){itemTarget=i;draw(client.state);}
    else if(cmd==='itemConfirm'){const item=r.battle.items.find(x=>x.id===selectedItem);if(item)void client.submit({action:{kind:'item',item:selectedItem,...(item.revive>0?{target:itemTarget}:{})}});}
  });}
  function clock(){const wait=root.querySelector('[data-queue-elapsed]');if(wait){const seconds=client.queueElapsed();wait.textContent=`${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;}const el=root.querySelector('.rt-clock'),sec=client.seconds();if(el)el.textContent=sec===null?'':`${sec}초`;}
  const clockTimer=setInterval(()=>{if(visible)clock();},250);
  const onVisibility=()=>{if(!destroyed&&!document.hidden&&visible)void client.tick();};
  const onPageHide=()=>{if(!destroyed)client.stop();};
  const onPageShow=()=>{if(!destroyed&&visible&&!['failed','disconnected'].includes(client.state.status)){client.startPolling();void client.tick();}};
  document.addEventListener('visibilitychange',onVisibility);
  addEventListener('pagehide',onPageHide);
  addEventListener('pageshow',onPageShow);
  return {start(mode){if(destroyed)return;void client.start(mode);show();},async restore(){
    if(destroyed)return;
    const q=await api('pvp/queue/current');
    if(destroyed)return;
    if(['preparing','waiting','matched'].includes(q.status)){show();await client.recover();}
  },destroy(){if(destroyed)return;destroyed=true;visible=false;client.destroy();
    document.removeEventListener('visibilitychange',onVisibility);
    removeEventListener('pagehide',onPageHide);removeEventListener('pageshow',onPageShow);
    clearInterval(clockTimer);root.remove();style.remove();}};
}
