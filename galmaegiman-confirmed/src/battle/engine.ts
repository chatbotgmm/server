// 수비대 대결 엔진: 3:3, 한 마리씩 나와 싸우는 턴제. 같은 시드면 같은 결과(서버가 다시 계산해 검증 가능).
// 전투 증강체(공통)는 ① 전투 시작 ② 어느 쪽이든 유닛이 전투불가가 될 때, 양쪽 모두 3개 중 1개를 고름.
import { attrName, basicSkill, roleSkill, statusName, strongAgainst, typeMult, unitDefMap, STRONG, WEAK,
  type Attr, type Effect, type Role, type Skill, type Stat, type Status } from './units.js';
import { levelScale, passiveOf, statsOf, type Passive, type UnitRecord } from './growth.js';

// ── 공통 전투 증강체: 상성을 뒤집을 만한 전투 개입형 ──
export interface BattleAugment {id:string;name:string;desc:string}
export const battleAugments:BattleAugment[]=[
  {id:'reverse',name:'역상성',desc:'이번 전투 동안 내 공격의 상성이 뒤집혀요(×1.5 ↔ ×0.67)'},
  {id:'weakguard',name:'약점 보강',desc:'내가 받는 상성 피해 ×1.5가 ×1.0이 돼요'},
  {id:'focus',name:'집중 공략',desc:'내 상성 공격이 ×1.5 대신 ×1.9가 돼요'},
  {id:'neutral',name:'무속성 지대',desc:'3턴 동안 양쪽 모든 상성이 ×1.0이 돼요'},
  {id:'camo',name:'보호색',desc:'지금 나와 있는 내 갈매미의 속성이 상대에게 강한 속성으로 바뀌어요'},
  {id:'dual',name:'이중 속성',desc:'지금 나와 있는 내 갈매미가 상대 약점을 찌르는 공격 속성을 하나 더 얻어요'},
  {id:'counter',name:'역습 태세',desc:'상성이 불리한 공격을 받으면 위력 40으로 바로 반격해요'},
  {id:'learn',name:'상성 학습',desc:'같은 속성에 맞을 때마다 그 속성 피해가 15%씩 줄어요(최대 45%)'},
  {id:'first',name:'출근 도장',desc:'다음 턴에는 무조건 내가 먼저 움직여요'},
  {id:'bulwark',name:'철벽',desc:'지금 나와 있는 내 갈매미가 2턴 동안 받는 피해 -40%'},
  {id:'firstaid',name:'응급처치',desc:'쓰러지지 않은 아군 모두 체력 25% 회복'},
  {id:'freeswitch',name:'인사 발령',desc:'이번 전투 동안 교체가 턴을 쓰지 않아요'},
  {id:'lastone',name:'마지막 출근',desc:'아군이 하나만 남으면 그 갈매미의 공격·방어·속도 +30%'},
  {id:'strip',name:'기세 꺾기',desc:'상대 강화를 모두 지우고 상대 선두 공격 -20%(2턴)'},
  {id:'rustle',name:'바스락',desc:'상대 선두가 새우깡 소리에 1턴 멈춰요'},
  {id:'peanut',name:'땅콩 기세',desc:'내 갈매미맨들의 공격 +30%, 대신 받는 피해 +15%'},
];
export const battleAugmentMap=new Map(battleAugments.map(a=>[a.id,a]));

// ── 상태 ──
interface Mod {stat:Stat;pct:number;turns:number}
export interface Fighter {
  side:0|1;rec:UnitRecord;name:string;scale:number;attr:Attr;role:Role;peanut:boolean;skills:Skill[];passive:Passive;
  maxHp:number;hp:number;base:{atk:number;def:number;spd:number};cds:Record<string,number>;mods:Mod[];
  status:Partial<Record<Status,number>>;guards:{pct:number;turns:number}[];regens:{pct:number;turns:number}[];
  endure:number;protect:boolean;recharge:boolean;immune:number;lockAtk:number;lastStandUsed:boolean;attacked:boolean;actedTurn:number;
  extraAttr?:Attr;hitBy:Partial<Record<Attr,number>>;
}
interface SideState {fighters:Fighter[];active:number;augs:string[];offers:string[]|null;needSwitch:boolean;reviveUsed:boolean;goFirst:boolean;neutral:number}
// 교체: '인사 발령' 증강체가 있으면 턴을 안 쓰므로 then(바로 쓸 기술)을 함께 보냄(없으면 AI가 고름)
export type Action={kind:'skill';skillId:string}|{kind:'switch';to:number;then?:string};
export interface Battle {rng:number;turn:number;phase:'choose'|'action'|'over';winner:0|1|null;sides:[SideState,SideState];log:string[]}

// 같은 시드 → 같은 난수(mulberry32)
function rand(b:Battle){let t=(b.rng=(b.rng+0x6d2b79f5)|0);t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return((t^(t>>>14))>>>0)/4294967296;}
const josa=(w:string,pair:string)=>{const c=w.charCodeAt(w.length-1);const has=c>=0xac00&&c<=0xd7a3&&(c-0xac00)%28>0;return w+(has?pair[0]:pair[1]);};

function makeFighter(rec:UnitRecord,side:0|1):Fighter{
  const def=unitDefMap.get(rec.id);if(!def)throw new Error(`전투 유닛이 아님: ${rec.id}`);
  const s=statsOf(rec),passive=passiveOf(rec);
  const sigCd=Math.max(1,def.signature.cd-(passive.sigCd??0));
  return {side,rec,name:rec.name,scale:levelScale(rec),attr:def.attr,role:def.role,peanut:rec.name.includes('갈매미맨'),passive,
    skills:[basicSkill(def.attr),roleSkill(def.role,def.attr),{...def.signature,cd:sigCd}],
    maxHp:s.hp,hp:s.hp,base:{atk:s.atk,def:s.def,spd:s.spd},cds:{},mods:[],status:{},guards:[],regens:[],
    endure:0,protect:false,recharge:false,immune:0,lockAtk:0,lastStandUsed:false,attacked:false,actedTurn:0,hitBy:{}};
}

export function createBattle(teamA:UnitRecord[],teamB:UnitRecord[],seed:number):Battle{
  if(!teamA.length||!teamB.length||teamA.length>3||teamB.length>3)throw new Error('팀은 1~3마리');
  const side=(t:UnitRecord[],i:0|1):SideState=>({fighters:t.map(r=>makeFighter(r,i)),active:0,augs:[],offers:null,needSwitch:false,reviveUsed:false,goFirst:false,neutral:0});
  const b:Battle={rng:seed|0,turn:1,phase:'choose',winner:null,sides:[side(teamA,0),side(teamB,1)],log:[]};
  offerAugments(b);
  b.log.push('대결 시작! 전투 증강체를 하나씩 골라요.');
  return b;
}
export const activeOf=(b:Battle,s:0|1)=>b.sides[s].fighters[b.sides[s].active];
const foeSide=(s:0|1)=>(s===0?1:0) as 0|1;
const alive=(f:Fighter)=>f.hp>0;

function offerAugments(b:Battle){
  for(const s of b.sides){
    const pool=battleAugments.filter(a=>!s.augs.includes(a.id)).map(a=>a.id);const out:string[]=[];
    while(out.length<3&&pool.length)out.push(pool.splice(Math.floor(rand(b)*pool.length),1)[0]);
    s.offers=out.length?out:null;
  }
  b.phase='choose';
}

// 고르기 단계: 증강체(제안이 있으면 필수), 쓰러진 선두 교체(필요하면 필수)
export function choose(b:Battle,side:0|1,pick:{augment?:string;switchTo?:number}){
  if(b.phase!=='choose')throw new Error('지금은 고를 때가 아님');
  const s=b.sides[side];
  if(s.offers){
    if(!pick.augment||!s.offers.includes(pick.augment))throw new Error('제안된 증강체 중 하나를 골라야 함');
    s.augs.push(pick.augment);s.offers=null;applyAugment(b,side,pick.augment);
  }
  if(s.needSwitch){
    const to=pick.switchTo;
    if(to===undefined||!s.fighters[to]||!alive(s.fighters[to]))throw new Error('나갈 갈매미를 골라야 함');
    s.active=to;s.needSwitch=false;b.log.push(`${josa(s.fighters[to].name,'이가')} 나왔어요!`);
  }
  if(b.sides.every(x=>!x.offers&&!x.needSwitch))b.phase='action';
}
export const pending=(b:Battle,side:0|1)=>({augments:b.sides[side].offers,needSwitch:b.sides[side].needSwitch});

function applyAugment(b:Battle,side:0|1,id:string){
  const me=activeOf(b,side),foe=activeOf(b,foeSide(side)),s=b.sides[side];
  const a=battleAugmentMap.get(id)!;b.log.push(`[${side===0?'내 팀':'상대'}] 증강체 「${a.name}」!`);
  if(id==='neutral'){b.sides[0].neutral=b.sides[1].neutral=3;}
  if(id==='camo'){const pick=(Object.keys(strongAgainst) as Attr[]).find(x=>strongAgainst[x].includes(foe.attr));if(pick){me.attr=pick;b.log.push(`${me.name}의 속성이 ${attrName[pick]}(으)로 바뀌었어요.`);}}
  if(id==='dual'){const pick=(Object.keys(strongAgainst) as Attr[]).find(x=>strongAgainst[x].includes(foe.attr));if(pick)me.extraAttr=pick;}
  if(id==='first')s.goFirst=true;
  if(id==='bulwark')me.guards.push({pct:40,turns:2});
  if(id==='firstaid')for(const f of s.fighters)if(alive(f))f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*0.25));
  if(id==='strip'){foe.mods=foe.mods.filter(m=>m.pct<0);foe.guards=[];foe.mods.push({stat:'atk',pct:-20,turns:2});}
  if(id==='rustle'&&!foe.immune){foe.status.sleep=1;b.log.push(`바스락… ${josa(foe.name,'이가')} 멈췄어요!`);}
}

// ── 능력치 계산 ──
function statOf(b:Battle,f:Fighter,stat:Stat){
  let v=f.base[stat];
  for(const m of f.mods)if(m.stat===stat)v*=1+m.pct/100;
  if(stat==='atk'&&f.lockAtk)v*=1+f.lockAtk/100;
  if(stat==='atk'&&f.status.burn)v*=0.8;
  if(f.status.rage){if(stat==='atk')v*=1.3;if(stat==='def')v*=0.7;}
  if(stat==='spd'&&f.status.bind)v*=0.7;
  const s=b.sides[f.side];
  if(s.augs.includes('lastone')&&s.fighters.filter(alive).length===1)v*=1.3;
  return Math.max(1,v);
}
function attackAttr(f:Fighter,move:Skill,target:Fighter):Attr{
  const a=move.attr??f.attr;
  if(f.extraAttr&&(move.id.startsWith('basic')||move.id==='role-strike')&&typeMult(f.extraAttr,target.attr)>typeMult(a,target.attr))return f.extraAttr;
  return a;
}
function multiplier(b:Battle,att:Fighter,def:Fighter,a:Attr){
  if(b.sides[0].neutral>0)return 1;
  let m=typeMult(a,def.attr);
  const as=b.sides[att.side],ds=b.sides[def.side];
  if(as.augs.includes('reverse'))m=m===STRONG?WEAK:m===WEAK?STRONG:1;
  if(m===STRONG&&as.augs.includes('focus'))m=1.9;
  if(m>1&&ds.augs.includes('weakguard'))m=1;
  return m;
}

function damage(b:Battle,att:Fighter,def:Fighter,move:Skill){
  const a=attackAttr(att,move,def),mult=multiplier(b,att,def,a);
  let atk=statOf(b,att,'atk'),dv=statOf(b,def,'def');
  if(att.passive.pierce)dv*=1-att.passive.pierce/100;
  const crit=move.crit||rand(b)<0.0625+(att.passive.critPlus??0)/100;
  // 체력이 레벨·진화로 커지는 만큼 피해도 같은 비율로 커짐(같은 레벨이면 몇 대에 쓰러지는지 일정)
  let d=((atk/dv)*(move.power??0)*0.5+2)*att.scale*(a===att.attr?1.2:1)*mult*(0.9+rand(b)*0.1)*(crit?1.5:1);
  if(att.passive.execute&&def.hp<=def.maxHp*0.3)d*=1+att.passive.execute/100;
  if(att.passive.opener&&!att.attacked)d*=1+att.passive.opener/100;
  const as=b.sides[att.side],ds=b.sides[def.side];
  if(as.augs.includes('peanut')&&att.peanut)d*=1.3;
  if(ds.augs.includes('peanut')&&def.peanut)d*=1.15;
  if(ds.augs.includes('learn'))d*=1-0.15*Math.min(3,def.hitBy[a]??0);
  // 받는 피해 감소는 모두 합쳐 최대 60%
  let keep=1;for(const g of def.guards)keep*=1-g.pct/100;if(def.passive.cut)keep*=1-def.passive.cut/100;
  d*=Math.max(0.4,keep);
  return {amount:Math.max(1,Math.round(d)),mult,crit,attr:a};
}

function hurt(b:Battle,f:Fighter,amount:number){
  let hp=f.hp-amount;
  if(hp<=0&&f.endure>0)hp=1;
  if(hp<=0&&f.passive.lastStand&&!f.lastStandUsed){f.lastStandUsed=true;hp=1;b.log.push(`${josa(f.name,'이가')} 불굴의 의지로 버텼어요!`);}
  f.hp=Math.max(0,hp);
}

function applyStatus(b:Battle,src:Fighter,target:Fighter,status:Status,chance:number,turns:number){
  if(target.hp<=0||target.immune>0)return;
  if(status==='rage'&&!target.peanut){b.log.push(`${josa(target.name,'은는')} 땅콩이 없어서 폭주하지 않아요.`);return;}
  let c=chance+(src.passive.statusPlus??0);
  if(target.passive.statusResist)c*=1-target.passive.statusResist/100;
  if(rand(b)*100<c){target.status[status]=turns+(target.actedTurn===b.turn?1:0);b.log.push(`${josa(target.name,'이가')} ${statusName[status]} 상태가 됐어요!`);}
}

function applyEffects(b:Battle,user:Fighter,target:Fighter,effects:Effect[]){
  const team=b.sides[user.side].fighters,extra=user.passive.durationPlus??0;
  for(const e of effects){
    if(e.kind==='buff'){
      const turns=e.turns+extra;
      const list=e.target==='self'?[user]:e.target==='team'?team.filter(alive):[target];
      for(const f of list)if(f.hp>0)f.mods.push({stat:e.stat,pct:e.pct,turns});
    }
    else if(e.kind==='status')applyStatus(b,user,target,e.status,e.chance,e.turns);
    else if(e.kind==='heal'){
      const list=e.target==='self'?[user]:team.filter(f=>alive(f)&&f!==user);
      for(const f of list){f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*e.pct/100));if(e.target==='self')delete f.status.burn;}
    }
    else if(e.kind==='guard')user.guards.push({pct:e.pct,turns:e.turns});
    else if(e.kind==='endure')user.endure=e.turns;
    else if(e.kind==='protect')user.protect=true;
    else if(e.kind==='regen'){const list=e.target==='self'?[user]:team.filter(alive);for(const f of list)f.regens.push({pct:e.pct,turns:e.turns});}
    else if(e.kind==='cleanse'){for(const f of team){f.status={};f.immune=e.immuneTurns;}}
    else if(e.kind==='revive'){
      const s=b.sides[user.side];const down=team.find(f=>f.hp<=0);
      if(!down||s.reviveUsed){b.log.push('일으켜 세울 갈매미가 없어요.');continue;}
      s.reviveUsed=true;down.hp=Math.round(down.maxHp*e.pct/100);down.status={};b.log.push(`"앙~" ${josa(down.name,'이가')} 다시 일어났어요!`);
    }
    else if(e.kind==='strip'){target.mods=target.mods.filter(m=>m.pct<0);target.guards=[];}
    else if(e.kind==='recharge')user.recharge=true;
    else if(e.kind==='lockSelf')user.lockAtk=e.atkPct;
  }
}

function useSkill(b:Battle,user:Fighter,target:Fighter,skill:Skill){
  b.log.push(`${user.name}의 ${skill.name}!`);
  if(skill.cd)user.cds[skill.id]=skill.cd+1;
  if(skill.power){
    if(target.protect){b.log.push(`${josa(target.name,'이가')} 모두 막아 냈어요!`);applyEffects(b,user,target,skill.effects.filter(e=>e.kind!=='status'&&e.kind!=='strip'));return;}
    const hits=skill.hits??1;let total=0,last={mult:1,crit:false,attr:user.attr as Attr};
    for(let i=0;i<hits&&target.hp>0;i++){const r=damage(b,user,target,skill);hurt(b,target,r.amount);total+=r.amount;last=r;}
    user.attacked=true;
    target.hitBy[last.attr]=(target.hitBy[last.attr]??0)+1;
    b.log.push(`${josa(target.name,'이가')} ${total} 피해를 입었어요.${last.crit?' 급소!':''}${last.mult>1?' 효과가 굉장해요!':last.mult<1?' 효과가 별로예요…':''}`);
    if(user.passive.lifesteal)user.hp=Math.min(user.maxHp,user.hp+Math.round(total*user.passive.lifesteal/100));
    if(target.passive.reflect&&target.hp>0){const r=Math.round(total*target.passive.reflect/100);hurt(b,user,r);b.log.push(`반사! ${josa(user.name,'이가')} ${r} 피해를 돌려받았어요.`);}
    if(last.mult<1&&b.sides[target.side].augs.includes('counter')&&target.hp>0){
      const r=damage(b,target,user,{id:'counter',name:'역습',power:40,cd:0,effects:[],desc:''});hurt(b,user,r.amount);b.log.push(`역습! ${josa(user.name,'이가')} ${r.amount} 피해를 입었어요.`);
    }
  }
  applyEffects(b,user,target,skill.effects);
}

function doSwitch(b:Battle,side:0|1,to:number){
  const s=b.sides[side],out=s.fighters[s.active],inn=s.fighters[to];
  if(!inn||!alive(inn)||to===s.active)throw new Error('교체할 수 없음');
  if(out.status.bind)throw new Error('묶여서 교체할 수 없음');
  if(out.lockAtk&&alive(out))throw new Error('뒤로 걷는 법을 몰라서 교체할 수 없음');
  if(out.passive.handoff)inn.hp=Math.min(inn.maxHp,inn.hp+Math.round(inn.maxHp*out.passive.handoff/100));
  s.active=to;b.log.push(`${josa(out.name,'은는')} 들어가고 ${josa(inn.name,'이가')} 나왔어요!`);
}

export function legalActions(b:Battle,side:0|1):Action[]{
  const s=b.sides[side],f=activeOf(b,side),out:Action[]=[];
  for(const sk of f.skills)if(!(f.cds[sk.id]>0))out.push({kind:'skill',skillId:sk.id});
  if(!f.status.bind&&!(f.lockAtk&&alive(f)))s.fighters.forEach((x,i)=>{if(i!==s.active&&alive(x))out.push({kind:'switch',to:i});});
  return out;
}

// 한 턴 진행. 양쪽 행동을 동시에 받음
export function act(b:Battle,actions:[Action,Action]){
  if(b.phase!=='action')throw new Error('지금은 행동할 때가 아님');
  const order:{side:0|1;action:Action;pri:number;spd:number;tie:number}[]=[];
  for(const side of [0,1] as const){
    let action=actions[side];
    const s=b.sides[side];
    if(action.kind==='switch'){
      doSwitch(b,side,action.to);
      if(s.augs.includes('freeswitch'))action=action.then?{kind:'skill',skillId:action.then}:pickAi(b,side);   // 교체가 턴을 안 쓰면 새로 나온 갈매미가 바로 행동
      else continue;
    }
    const f=activeOf(b,side),skill=f.skills.find(k=>k.id===(action as {skillId:string}).skillId);
    if(!skill||f.cds[skill.id]>0)throw new Error('쓸 수 없는 기술');
    order.push({side,action,pri:(s.goFirst?2:0)+(skill.priority?1:0),spd:statOf(b,f,'spd'),tie:rand(b)});
    s.goFirst=false;
  }
  order.sort((x,y)=>y.pri-x.pri||y.spd-x.spd||y.tie-x.tie);
  for(const o of order){
    const user=activeOf(b,o.side),target=activeOf(b,foeSide(o.side));
    if(!alive(user)||!alive(target))continue;
    user.actedTurn=b.turn;
    if(user.recharge){user.recharge=false;b.log.push(`${josa(user.name,'은는')} 힘을 너무 써서 쉬고 있어요.`);continue;}
    if(user.status.sleep){b.log.push(`${josa(user.name,'은는')} 쿨쿨 자고 있어요.`);continue;}
    if(user.status.freeze){b.log.push(`${josa(user.name,'은는')} 얼어서 움직이지 못해요.`);continue;}
    useSkill(b,user,target,user.skills.find(k=>k.id===(o.action as {skillId:string}).skillId)!);
  }
  endTurn(b);
}

function endTurn(b:Battle){
  for(const s of b.sides){
    const f=s.fighters[s.active];
    if(alive(f)){
      if(f.status.burn){const d=Math.max(1,Math.round(f.maxHp*0.06));hurt(b,f,d);b.log.push(`${josa(f.name,'이가')} 화상으로 ${d} 피해를 입었어요.`);}
      let heal=0;for(const r of f.regens)heal+=r.pct;if(f.passive.regen)heal+=f.passive.regen;
      if(heal&&alive(f))f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*heal/100));
    }
    for(const x of s.fighters){
      for(const k of Object.keys(x.cds))if(x.cds[k]>0)x.cds[k]--;
      x.mods=x.mods.filter(m=>--m.turns>0);x.guards=x.guards.filter(g=>--g.turns>0);x.regens=x.regens.filter(r=>--r.turns>0);
      for(const k of Object.keys(x.status) as Status[]){if(--x.status[k]!<=0)delete x.status[k];}
      if(x.endure>0)x.endure--;if(x.immune>0)x.immune--;x.protect=false;
    }
    if(s.neutral>0)s.neutral--;
  }
  b.turn++;
  // 전투불가 처리
  let fainted=false;
  for(const side of [0,1] as const){
    const s=b.sides[side],f=s.fighters[s.active];
    if(!alive(f)){fainted=true;b.log.push(`${josa(f.name,'이가')} 전투불가가 됐어요!`);
      if(s.fighters.some(alive))s.needSwitch=true;}
  }
  const lost=([0,1] as const).filter(side=>!b.sides[side].fighters.some(alive));
  if(lost.length){b.phase='over';b.winner=lost.length===2?null:lost[0]===0?1:0;b.log.push(b.winner===null?'무승부!':b.winner===0?'승리!':'패배…');return;}
  if(fainted){offerAugments(b);b.log.push('전투불가 발생! 양쪽 모두 증강체를 하나씩 골라요.');}
}

// ── 간단한 상대 AI(스토리 NPC·유저 대결 수비 팀) ──
export function pickAi(b:Battle,side:0|1):Action{
  const f=activeOf(b,side),foe=activeOf(b,foeSide(side));
  const legal=legalActions(b,side).filter((a):a is {kind:'skill';skillId:string}=>a.kind==='skill');
  let best:Action=legal[0]??{kind:'skill',skillId:f.skills[0].id},score=-1;
  for(const a of legal){
    const sk=f.skills.find(k=>k.id===a.skillId)!;let v=0;
    if(sk.power)v=sk.power*(sk.hits??1)*typeMult(attackAttr(f,sk,foe),foe.attr);
    if(sk.effects.some(e=>e.kind==='heal'))v=f.hp<f.maxHp*0.45?200:5;
    else if(sk.effects.some(e=>e.kind==='revive'))v=b.sides[side].fighters.some(x=>x.hp<=0)&&!b.sides[side].reviveUsed?250:0;
    else if(!sk.power){
      const already=sk.effects.some(e=>(e.kind==='buff'&&(e.target==='foe'?foe:f).mods.some(m=>m.stat===e.stat&&Math.sign(m.pct)===Math.sign(e.pct)))||(e.kind==='guard'&&f.guards.length>0)||(e.kind==='endure'&&f.endure>0)||(e.kind==='status'&&foe.status[e.status]));
      v=already?5:f.hp>f.maxHp*0.5?60:30;
    }
    else if(sk.effects.length)v*=1.1;
    if(v>score){score=v;best=a;}
  }
  return best;
}
export function aiChoose(b:Battle,side:0|1){
  const s=b.sides[side],p:{augment?:string;switchTo?:number}={};
  if(s.offers){
    // 상대 선두에게 상성이 불리하면 상성 뒤집기류를 먼저
    const me=activeOf(b,side),foe=activeOf(b,foeSide(side));
    const bad=typeMult(foe.attr,me.attr)>1||typeMult(me.attr,foe.attr)<1;
    const prefer=bad?['camo','reverse','weakguard','neutral','dual','counter']:['focus','dual','first','peanut','strip','bulwark'];
    p.augment=prefer.find(id=>s.offers!.includes(id))??s.offers[0];
  }
  if(s.needSwitch){const foe=activeOf(b,foeSide(side));let bi=-1,bv=-1;
    s.fighters.forEach((f,i)=>{if(!alive(f))return;const v=typeMult(f.attr,foe.attr)/typeMult(foe.attr,f.attr);if(v>bv){bv=v;bi=i;}});p.switchTo=bi;}
  choose(b,side,p);
}
