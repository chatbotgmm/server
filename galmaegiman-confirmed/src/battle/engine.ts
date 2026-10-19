// 수비대 대결 엔진: 3:3, 한 마리씩 나와 싸우는 턴제. 같은 시드면 같은 결과(서버가 다시 계산해 검증 가능).
// 전투 증강체(공통)는 ① 전투 시작 ② 어느 쪽이든 유닛이 전투불가가 될 때, 양쪽 모두 3개 중 1개를 고름.
import { attrKeys, statusName, type Attr, type Effect, type Role, type Rules, type Skill, type Stat, type Status, type Passive } from './core.js';
import { levelScale, passiveOf, slotSkills, statsOf, type UnitRecord } from './growth.js';
export type { BattleAugment } from './core.js';

// ── 상태 ──
interface Mod {stat:Stat;pct:number;turns:number}
export interface Fighter {
  side:0|1;rec:UnitRecord;name:string;scale:number;attr:Attr;role:Role;peanut:boolean;skills:Skill[];passive:Passive;
  maxHp:number;hp:number;base:{patk:number;pdef:number;matk:number;mdef:number;spd:number};cds:Record<string,number>;mods:Mod[];
  status:Partial<Record<Status,number>>;guards:{pct:number;turns:number}[];regens:{pct:number;turns:number}[];
  endure:number;protect:boolean;recharge:boolean;immune:number;lockAtk:number;lastStandUsed:boolean;attacked:boolean;actedTurn:number;
  lastUtility:Record<string,number>;healUntil:number;statusUntil:Partial<Record<Status,number>>;extraAttr?:Attr;hitBy:Partial<Record<Attr,number>>;
}
interface SideState {fighters:Fighter[];active:number;comboUsed:boolean;augs:string[];offers:string[]|null;rerolled:boolean[];offerSeen:string[];needSwitch:boolean;reviveUsed:boolean;goFirst:boolean;neutral:number;itemsUsed:number}
// 교체: '인사 발령' 증강체가 있으면 턴을 안 쓰므로 then(바로 쓸 기술)을 함께 보냄(없으면 AI가 고름)
// 아이템: 그 턴 행동을 쓰고, 기술보다 먼저 쓰임. target = 다시 일으킬 갈매미 순서(다시 출근 깃털)
export type Action={kind:'skill';skillId:string}|{kind:'switch';to:number;then?:string}|{kind:'item';item:string;target?:number};
// 화면 연출은 표시 이름 대신 진영·편성 칸·기록 번호로 대상을 찾는다. 같은 이름/종류도 서로 다른 대상이다.
export interface BattleFighterRef {side:0|1;slot:number;key:string;id:string}
export interface BattleEvent {
  id:number;turn:number;kind:'skill'|'damage'|'heal'|'switch'|'faint'|'revive'|'item'|'augment'|'status';
  actor?:BattleFighterRef;target?:BattleFighterRef;skillId?:string;skillName?:string;attr?:Attr;power?:number;reason?:string;
  amount?:number;hpBefore?:number;hpAfter?:number;maxHp?:number;crit?:boolean;multiplier?:number;hit?:number;
  status?:Partial<Record<Status,number>>;extraAttr?:Attr|null;
  text:string;logIndex:number;
}
export interface Battle {R:Rules;rng:number;turn:number;phase:'choose'|'action'|'over';winner:0|1|null;sides:[SideState,SideState];log:string[];events:BattleEvent[];eventSeq:number}
export const BATTLE_EVENT_LIMIT=200;
const fighterRef=(b:Battle,f:Fighter):BattleFighterRef=>({side:f.side,slot:b.sides[f.side].fighters.indexOf(f),key:f.rec.uid,id:f.rec.id});
type EventData=Omit<BattleEvent,'id'|'turn'|'text'|'logIndex'>&{turn?:number};
function event(b:Battle,data:EventData,logIndex=b.log.length-1):BattleEvent{
  // 저장된 이전 버전 대결도 이어 할 수 있게 없던 스트림은 첫 변경 시 만든다.
  b.events??=[];b.eventSeq??=b.events.at(-1)?.id??0;
  const e:BattleEvent={...data,id:++b.eventSeq,turn:data.turn??b.turn,logIndex,text:b.log[logIndex]??''};
  b.events.push(e);if(b.events.length>BATTLE_EVENT_LIMIT)b.events.splice(0,b.events.length-BATTLE_EVENT_LIMIT);
  return e;
}
function linkEvent(b:Battle,e:BattleEvent,logIndex=b.log.length-1){e.logIndex=logIndex;e.text=b.log[logIndex]??'';}
const skillEvent=(skill?:Skill)=>skill?{skillId:skill.id,skillName:skill.name,attr:skill.attr,power:skill.power??0}:{};
function statusEvent(b:Battle,target:Fighter,reason:string,actor?:Fighter){
  const e=event(b,{kind:'status',...(actor?{actor:fighterRef(b,actor)}:{}),target:fighterRef(b,target),reason,
    status:{...target.status},attr:target.attr,extraAttr:target.extraAttr??null});
  if(reason==='expire')e.text='';
}
function healed(b:Battle,actor:Fighter,target:Fighter,before:number,reason:string,skill?:Skill,revive=false){
  if(target.hp===before&&!revive)return;
  const e=event(b,{kind:revive?'revive':'heal',actor:fighterRef(b,actor),target:fighterRef(b,target),reason,...skillEvent(skill),
    amount:target.hp-before,hpBefore:before,hpAfter:target.hp,maxHp:target.maxHp});
  if(!revive)e.text=`${target.name}의 체력이 ${e.amount} 회복됐어요.`;
}

// 같은 시드 → 같은 난수(mulberry32)
function rand(b:Battle){let t=(b.rng=(b.rng+0x6d2b79f5)|0);t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return((t^(t>>>14))>>>0)/4294967296;}
const josa=(w:string,pair:string)=>{const c=w.charCodeAt(w.length-1);const has=c>=0xac00&&c<=0xd7a3&&(c-0xac00)%28>0;return w+(has?pair[0]:pair[1]);};

// 쓸 기술이 없을 때만 나오는 약한 공격(기본기를 잊었을 때 대비). 속성 없음·재사용 없음
export const STRUGGLE:Skill={id:'struggle',name:'몸통 박치기',power:30,cat:'phys',cd:0,effects:[],desc:'쓸 기술이 없을 때 나가는 약한 공격'};
const skillOf=(f:Fighter,id:string)=>id===STRUGGLE.id?{...STRUGGLE,attr:f.attr}:f.skills.find(k=>k.id===id);
function makeFighter(R:Rules,rec:UnitRecord,side:0|1,combo?:Skill):Fighter{
  const def=R.unitDef(rec.id);if(!def)throw new Error(`전투 유닛이 아님: ${rec.id}`);
  const s=statsOf(R,rec),passive=passiveOf(R,rec);
  return {side,rec,name:rec.name,scale:levelScale(R,rec),attr:def.attr,role:def.role,peanut:rec.name.includes('갈매미맨'),passive,
    // 기술 4칸(v0.15.8: 기본기·고유기도 바꿀 수 있음) + 합동기. 쓸 기술이 하나도 없을 때만 「몸통 박치기」(legalActions)
    skills:[...slotSkills(R,rec),...(combo?[R.unitPower(rec.id,combo)]:[])],
    maxHp:s.hp,hp:s.hp,base:{patk:s.patk,pdef:s.pdef,matk:s.matk,mdef:s.mdef,spd:s.spd},cds:{},mods:[],status:{},guards:[],regens:[],
    endure:0,protect:false,recharge:false,immune:0,lockAtk:0,lastStandUsed:false,attacked:false,actedTurn:0,lastUtility:{},healUntil:0,statusUntil:{},hitBy:{}};
}

export function createBattle(R:Rules,teamA:UnitRecord[],teamB:UnitRecord[],seed:number):Battle{
  if(!teamA.length||!teamB.length||teamA.length>3||teamB.length>3)throw new Error('팀은 1~3마리');
  // 같은 시너지가 2마리 이상이면 그 유닛들에게 합동기
  const comboFor=(t:UnitRecord[],r:UnitRecord)=>{const k=R.unitDef(r.id)?.syn;if(!k||new Set(t.filter(x=>R.unitDef(x.id)?.syn===k).map(x=>x.id)).size<2)return undefined;   // 같은 종류 2마리는 1마리로 셈
    return R.comboSkill(k,R.unitDef(r.id)!.attr);};
  const side=(t:UnitRecord[],i:0|1):SideState=>({fighters:t.map(r=>makeFighter(R,r,i,comboFor(t,r))),active:0,comboUsed:false,augs:[],offers:null,rerolled:[],offerSeen:[],needSwitch:false,reviveUsed:false,goFirst:false,neutral:0,itemsUsed:0});
  const b:Battle={R,rng:seed|0,turn:1,phase:'choose',winner:null,sides:[side(teamA,0),side(teamB,1)],log:[],events:[],eventSeq:0};
  // 거울(mirror) 빌런: 상대 팀에 가장 많은 속성을 그대로 비춤(같으면 상대 선두 속성)
  for(const i of [0,1] as const)for(const f of b.sides[i].fighters)if(R.unitDef(f.rec.id)?.mirror){
    const foes=b.sides[i===0?1:0].fighters,cnt=new Map<Attr,number>();for(const x of foes)cnt.set(x.attr,(cnt.get(x.attr)??0)+1);
    const top=Math.max(...cnt.values());f.attr=cnt.get(foes[0].attr)===top?foes[0].attr:[...cnt].find(([,n])=>n===top)![0];
    f.skills=f.skills.map(k=>k.id.startsWith('basic-')||k.id==='role-ATTACK'||k.id==='role-UTILITY'||k.id==='role-DEFENSE'?{...k,attr:k.power?f.attr:k.attr}:k);
  }
  offerAugments(b);
  b.log.push('대결 시작! 전투 증강체를 하나씩 골라요.');
  return b;
}
export const activeOf=(b:Battle,s:0|1)=>b.sides[s].fighters[b.sides[s].active];
const foeSide=(s:0|1)=>(s===0?1:0) as 0|1;
const alive=(f:Fighter)=>f.hp>0;

function offerAugments(b:Battle){
  for(const s of b.sides){
    // 대결 시작(모두 체력 가득)에는 회복 증강체(응급처치)를 내지 않음
    const pool=b.R.battleAugments.filter(a=>!s.augs.includes(a.id)&&!(a.id==='firstaid'&&s.fighters.every(f=>f.hp>=f.maxHp))).map(a=>a.id);const out:string[]=[];
    while(out.length<3&&pool.length)out.push(pool.splice(Math.floor(rand(b)*pool.length),1)[0]);
    s.offers=out.length?out:null;s.rerolled=out.map(()=>false);s.offerSeen=[...out];
  }
  b.phase='choose';
}

// 증강체 다시 뽑기: 제안 칸마다 제안이 나올 때마다 한 번씩. 이번에 이미 보인 것·가진 것은 다시 안 나옴
export function rerollAugment(b:Battle,side:0|1,i:number){
  const s=b.sides[side];
  if(b.phase!=='choose'||!s.offers||!s.offers[i])throw new Error('다시 뽑을 증강체가 없음');
  if(s.rerolled[i])throw new Error('이 칸은 이미 다시 뽑았음');
  const pool=b.R.battleAugments.map(a=>a.id).filter(id=>!s.augs.includes(id)&&!s.offers!.includes(id)&&!s.offerSeen.includes(id)&&!(id==='firstaid'&&s.fighters.every(f=>f.hp>=f.maxHp)));
  if(!pool.length)throw new Error('다른 증강체가 없음');
  const id=pool[Math.floor(rand(b)*pool.length)];
  s.offers[i]=id;s.rerolled[i]=true;s.offerSeen.push(id);
  return id;
}
// 고르기 단계: 증강체(제안이 있으면 필수), 쓰러진 선두 교체(필요하면 필수)
export function choose(b:Battle,side:0|1,pick:{augment?:string;switchTo?:number}){
  if(b.phase!=='choose')throw new Error('지금은 고를 때가 아님');
  const s=b.sides[side];
  const augment=s.offers?pick.augment:undefined;
  if(s.offers&&(!augment||!s.offers.includes(augment)))throw new Error('제안된 증강체 중 하나를 골라야 함');
  const to=s.needSwitch?pick.switchTo:undefined;
  if(s.needSwitch&&(to===undefined||!s.fighters[to]||!alive(s.fighters[to])))throw new Error('나갈 갈매미를 골라야 함');
  if(s.needSwitch){
    const out=s.fighters[s.active];
    s.active=to!;s.needSwitch=false;
    const incoming=s.fighters[s.active];
    b.log.push(`${josa(incoming.name,'이가')} 나왔어요!`);
    event(b,{kind:'switch',actor:fighterRef(b,out),target:fighterRef(b,incoming),reason:'faint'});
  }
  if(augment){
    s.augs.push(augment);s.offers=null;applyAugment(b,side,augment);
  }
  if(b.sides.every(x=>!x.offers&&!x.needSwitch))b.phase='action';
}
// Realtime only: validate both selections, replace both fainted fighters, then apply augments.
// The caller resolves this on a clone and persists only after successful completion.
export function chooseTogether(b:Battle,picks:[{augment?:string;switchTo?:number},{augment?:string;switchTo?:number}]){
  if(b.phase!=='choose')throw new Error('지금은 고를 때가 아님');
  for(const side of [0,1] as const){
    const s=b.sides[side],p=picks[side];
    if(s.offers&&(!p.augment||!s.offers.includes(p.augment)))throw new Error('제안된 증강체 중 하나를 골라야 함');
    if(s.needSwitch&&(p.switchTo===undefined||!Number.isInteger(p.switchTo)||!s.fighters[p.switchTo]||!alive(s.fighters[p.switchTo])))throw new Error('나갈 갈매미를 골라야 함');
  }
  for(const side of [0,1] as const){
    const s=b.sides[side];if(!s.needSwitch)continue;
    const out=s.fighters[s.active];s.active=picks[side].switchTo!;s.needSwitch=false;
    const incoming=s.fighters[s.active];b.log.push(`${josa(incoming.name,'이가')} 나왔어요!`);
    event(b,{kind:'switch',actor:fighterRef(b,out),target:fighterRef(b,incoming),reason:'faint'});
  }
  for(const side of [0,1] as const){
    const s=b.sides[side];if(!s.offers)continue;
    const augment=picks[side].augment!;s.augs.push(augment);s.offers=null;applyAugment(b,side,augment);
  }
  if(b.sides.every(s=>!s.offers&&!s.needSwitch))b.phase='action';
}
export const pending=(b:Battle,side:0|1)=>({augments:b.sides[side].offers,needSwitch:b.sides[side].needSwitch});

function applyAugment(b:Battle,side:0|1,id:string){
  const me=activeOf(b,side),foe=activeOf(b,foeSide(side)),s=b.sides[side];
  const R=b.R,a=R.battleAug.get(id)!,p=a.p;b.log.push(`[${side===0?'내 팀':'상대'}] 증강체 「${a.name}」!`);
  event(b,{kind:'augment',actor:fighterRef(b,me),skillId:id,skillName:a.name});
  if(id==='neutral'){b.sides[0].neutral=b.sides[1].neutral=p.turns??3;}
  // 상대에게 강한 속성 2개 중, 상대 속성 공격을 덜 아프게 받는 쪽(같으면 속성 목록 순서)
  const cands=attrKeys.filter(x=>R.data.chart[x].strong.includes(foe.attr));
  const beats=cands.sort((x,y)=>R.typeMult(foe.attr,x)-R.typeMult(foe.attr,y))[0];
  if(id==='camo'&&beats){me.attr=beats;b.log.push(`${me.name}의 속성이 ${R.attrName(beats)}(으)로 바뀌었어요.`);statusEvent(b,me,'camo',me);}
  if(id==='dual'&&beats){me.extraAttr=beats;statusEvent(b,me,'dual',me);}
  if(id==='first')s.goFirst=true;
  if(id==='bulwark')me.guards.push({pct:p.pct??40,turns:p.turns??2});
  if(id==='firstaid')for(const f of s.fighters)if(alive(f)){const before=f.hp;f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*(p.pct??25)/100));healed(b,me,f,before,'firstaid');}
  if(id==='strip'){foe.mods=foe.mods.filter(m=>m.pct<0);foe.guards=[];foe.mods.push({stat:'atk',pct:-(p.pct??20),turns:p.turns??2});}
  if(id==='rustle'&&!foe.immune){foe.status.sleep=1;b.log.push(`바스락… ${josa(foe.name,'이가')} 멈췄어요!`);statusEvent(b,foe,'rustle',me);}
}

// ── 능력치 계산 ──
// 강화·약화(「공격」「방어」)는 물리·마법 둘 다에 걸림
type BaseStat='patk'|'pdef'|'matk'|'mdef'|'spd';
const groupOf=(k:BaseStat):Stat=>k==='patk'||k==='matk'?'atk':k==='pdef'||k==='mdef'?'def':'spd';
function statOf(b:Battle,f:Fighter,key:BaseStat){
  const st=b.R.data.status,stat=groupOf(key);let v=f.base[key];
  for(const m of f.mods)if(m.stat===stat)v*=1+m.pct/100;
  if(stat==='atk'&&f.lockAtk)v*=1+f.lockAtk/100;
  if(stat==='atk'&&f.status.burn)v*=1+st.burnAtk/100;
  if(f.status.rage){if(stat==='atk')v*=1+st.rageAtk/100;if(stat==='def')v*=1+st.rageDef/100;}
  if(stat==='spd'&&f.status.bind)v*=1+st.bindSpd/100;
  const s=b.sides[f.side];
  if(s.augs.includes('lastone')&&s.fighters.filter(alive).length===1)v*=1+(b.R.battleAug.get('lastone')!.p.pct??30)/100;
  const ap=(id:string)=>(b.R.battleAug.get(id)!.p.pct??0)/100;
  if(stat==='spd'&&s.augs.includes('swift'))v*=1+ap('swift');
  if(stat==='def'&&s.augs.includes('thick'))v*=1+ap('thick');
  if(stat==='atk'&&s.augs.includes('overtime'))v*=1+ap('overtime');
  return Math.max(1,v);
}
function attackAttr(R:Rules,f:Fighter,move:Skill,target:Fighter):Attr{
  const a=move.attr??f.attr;
  if(f.extraAttr&&(move.id.startsWith('basic')||move.id==='role-ATTACK')&&R.typeMult(f.extraAttr,target.attr)>R.typeMult(a,target.attr))return f.extraAttr;
  return a;
}
function multiplier(b:Battle,att:Fighter,def:Fighter,a:Attr){
  if(b.sides[0].neutral>0)return 1;
  const M=b.R.data.multipliers;let m=b.R.typeMult(a,def.attr);
  const as=b.sides[att.side],ds=b.sides[def.side];
  if(as.augs.includes('reverse'))m=m===M.strong?M.weak:m===M.weak?M.strong:1;
  if(m===M.strong&&as.augs.includes('focus'))m=b.R.battleAug.get('focus')!.p.value??1.9;
  if(m>1&&ds.augs.includes('weakguard'))m=1;
  return m;
}

// 조건부 위력은 시전 직전 한 번 확정한다. 다단 공격 중 체력이 바뀌어도 다시 판정하지 않는다.
function skillPower(b:Battle,att:Fighter,def:Fighter,move:Skill){
  const basePower=move.power??0;
  let bonusActive=false;
  if(basePower&&move.bonus){
    switch(move.bonus.when){
      case 'burned':bonusActive=(def.status.burn??0)>0;break;
      case 'slowed':bonusActive=(def.status.bind??0)>0||def.mods.some(m=>m.stat==='spd'&&m.pct<0&&m.turns>0);break;
      case 'bound':bonusActive=(def.status.bind??0)>0;break;
      case 'lowHp':bonusActive=att.hp<=att.maxHp*0.5;break;
      case 'finisher':bonusActive=def.hp<=def.maxHp*0.4;break;
      case 'opening':bonusActive=def.hp>=def.maxHp*0.9;break;
      case 'slower':bonusActive=statOf(b,att,'spd')<statOf(b,def,'spd');break;
      case 'weakened':bonusActive=def.mods.some(m=>m.stat==='def'&&m.pct<0&&m.turns>0);break;
    }
  }
  const bonusPct=bonusActive?move.bonus!.pct:0;
  return {basePower,power:Math.round(basePower*(1+bonusPct/100)),bonusActive,bonusPct};
}

// 화면의 유불리·공격 종류도 실제 피해 계산과 같은 규칙으로 보여 준다. 난수·전투 상태를 바꾸지 않는다.
export function skillPreview(b:Battle,side:0|1,move:Skill){
  const att=activeOf(b,side),def=activeOf(b,(1-side) as 0|1),attr=attackAttr(b.R,att,move,def);
  return {attr,attrName:b.R.attrName(attr),multiplier:move.power?multiplier(b,att,def,attr):1,...skillPower(b,att,def,move),
    category:move.cat==='mag'||((move.cat??'auto')==='auto'&&att.base.matk>att.base.patk)?'mag':'phys'};
}

function damage(b:Battle,att:Fighter,def:Fighter,move:Skill){
  const R=b.R,M=R.data.multipliers,a=attackAttr(R,att,move,def),mult=multiplier(b,att,def,a);
  // 물리 기술: 물리공격↔물리방어, 마법 기술: 마법공격↔마법방어, 자동(역할기·합동기·반격): 쓰는 유닛의 더 높은 공격
  const mag=move.cat==='mag'||((move.cat??'auto')==='auto'&&att.base.matk>att.base.patk);
  let atk=statOf(b,att,mag?'matk':'patk'),dv=statOf(b,def,mag?'mdef':'pdef');
  if(att.passive.pierce)dv*=1-att.passive.pierce/100;
  if(move.pierce)dv*=1-move.pierce/100;
  const aug=(s:0|1,id:string)=>b.sides[s].augs.includes(id)?(R.battleAug.get(id)!.p.pct??0):0;
  if(aug(att.side,'pierce'))dv*=1-aug(att.side,'pierce')/100;
  const crit=move.crit||rand(b)<(M.critChance+(att.passive.critPlus??0)+aug(att.side,'sharp'))/100;
  // 체력이 레벨·진화로 커지는 만큼 피해도 같은 비율로 커짐(같은 레벨이면 몇 대에 쓰러지는지 일정)
  let d=((atk/dv)*(move.power??0)*0.5+2)*att.scale*(a===att.attr?M.sameAttr:1)*mult*(0.9+rand(b)*0.1)*(crit?M.crit:1);
  if(att.passive.execute&&def.hp<=def.maxHp*0.3)d*=1+att.passive.execute/100;
  if(att.passive.opener&&!att.attacked)d*=1+att.passive.opener/100;
  const as=b.sides[att.side],ds=b.sides[def.side];
  const pea=R.battleAug.get('peanut')!.p,lrn=R.battleAug.get('learn')!.p;
  if(as.augs.includes('peanut')&&att.peanut)d*=1+(pea.atk??30)/100;
  if(ds.augs.includes('peanut')&&def.peanut)d*=1+(pea.taken??15)/100;
  if(ds.augs.includes('learn'))d*=1-Math.min(lrn.max??45,(lrn.step??15)*(def.hitBy[a]??0))/100;
  // 받는 피해 감소는 모두 합쳐 최대 maxGuard%
  let keep=1;for(const g of def.guards)keep*=1-g.pct/100;if(def.passive.cut)keep*=1-def.passive.cut/100;
  d*=Math.max(1-M.maxGuard/100,keep);
  return {amount:Math.max(1,Math.round(d)),mult,crit,attr:a};
}

function hurt(b:Battle,f:Fighter,amount:number,details:Partial<EventData>={}){
  const before=f.hp;let hp=f.hp-amount;
  if(hp<=0&&f.endure>0)hp=1;
  if(hp<=0&&f.passive.lastStand&&!f.lastStandUsed){f.lastStandUsed=true;hp=1;b.log.push(`${josa(f.name,'이가')} 불굴의 의지로 버텼어요!`);}
  f.hp=Math.max(0,hp);
  return event(b,{...details,kind:'damage',target:fighterRef(b,f),amount:before-f.hp,hpBefore:before,hpAfter:f.hp,maxHp:f.maxHp});
}

function applyStatus(b:Battle,src:Fighter,target:Fighter,status:Status,chance:number,turns:number){
  if(target.hp<=0||target.immune>0||target.status[status]||(target.statusUntil[status]??0)>=b.turn)return;
  if(status==='rage'&&!target.peanut){b.log.push(`${josa(target.name,'은는')} 땅콩이 없어서 폭주하지 않아요.`);return;}
  let c=chance+(src.passive.statusPlus??0);
  if(target.passive.statusResist)c*=1-target.passive.statusResist/100;
  if(rand(b)*100<c){target.statusUntil[status]=b.turn+turns+1;target.status[status]=turns+(target.actedTurn===b.turn?1:0);b.log.push(`${josa(target.name,'이가')} ${statusName[status]} 상태가 됐어요!`);statusEvent(b,target,'skill',src);}
}

function applyEffects(b:Battle,user:Fighter,target:Fighter,effects:Effect[],skill?:Skill){
  const team=b.sides[user.side].fighters,extra=user.passive.durationPlus??0,healBlocked=user.healUntil>=b.turn;
  if(healBlocked&&effects.some(e=>e.kind==='heal'))b.log.push('회복 효과는 이미 적용되었습니다. 공격은 계속됩니다.');
  for(const e of effects){
    if(e.kind==='buff'){
      const turns=e.turns+extra;
      const list=e.target==='self'?[user]:e.target==='team'?team.filter(alive):[target];
      for(const f of list)if(f.hp>0&&!f.mods.some(m=>m.stat===e.stat&&Math.sign(m.pct)===Math.sign(e.pct)&&Math.abs(m.pct)>=Math.abs(e.pct))){
        f.mods=f.mods.filter(m=>!(m.stat===e.stat&&Math.sign(m.pct)===Math.sign(e.pct)));
        f.mods.push({stat:e.stat,pct:e.pct,turns});
      }
    }
    else if(e.kind==='status')applyStatus(b,user,target,e.status,e.chance,e.turns);
    else if(e.kind==='heal'){
      if(healBlocked)continue;
      user.healUntil=b.turn+1;
      const list=e.target==='self'?[user]:team.filter(f=>alive(f)&&f!==user);
      for(const f of list){const before=f.hp;f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*e.pct/100));healed(b,user,f,before,'skill',skill);if(e.target==='self'&&f.status.burn){delete f.status.burn;statusEvent(b,f,'heal',user);}}
    }
    else if(e.kind==='guard'&&!user.guards.some(g=>g.pct>=e.pct))user.guards.push({pct:e.pct,turns:e.turns});
    else if(e.kind==='endure'&&!user.endure)user.endure=e.turns;
    else if(e.kind==='protect')user.protect=true;
    else if(e.kind==='regen'){const list=e.target==='self'?[user]:team.filter(alive);for(const f of list)if(!f.regens.some(g=>g.pct>=e.pct))f.regens.push({pct:e.pct,turns:e.turns});}
    else if(e.kind==='cleanse'){for(const f of team){const changed=Object.keys(f.status).length>0;f.status={};f.immune=e.immuneTurns;if(changed)statusEvent(b,f,'cleanse',user);}}
    else if(e.kind==='revive'){
      const s=b.sides[user.side];const down=team.find(f=>f.hp<=0);
      if(!down||s.reviveUsed){b.log.push('일으켜 세울 갈매미가 없어요.');continue;}
      const before=down.hp;s.reviveUsed=true;down.hp=Math.round(down.maxHp*e.pct/100);down.status={};b.log.push(`"앙~" ${josa(down.name,'이가')} 다시 일어났어요!`);
      healed(b,user,down,before,'skill',skill,true);
      statusEvent(b,down,'revive',user);
    }
    else if(e.kind==='strip'){target.mods=target.mods.filter(m=>m.pct<0);target.guards=[];}
    // 재사용 대기·공격 뒤 휴식은 사용하지 않음.
    else if(e.kind==='lockSelf')user.lockAtk=e.atkPct;
  }
}

function useSkill(b:Battle,user:Fighter,target:Fighter,skill:Skill){
  const power=skillPower(b,user,target,skill);
  if(power.bonusActive)skill={...skill,power:power.power};
  b.log.push(`${user.name}의 ${skill.name}!${power.bonusActive?` 조건 강화 · 위력 ${power.basePower} → ${power.power}`:''}`);
  const cast=event(b,{kind:'skill',actor:fighterRef(b,user),...(skill.power?{target:fighterRef(b,target)}:{}),...skillEvent(skill)});
  if(!skill.power)user.lastUtility[skill.id]=b.turn;
  if(skill.power){
    if(target.protect){b.log.push(`${josa(target.name,'이가')} 모두 막아 냈어요!`);event(b,{kind:'damage',actor:fighterRef(b,user),target:fighterRef(b,target),...skillEvent(skill),reason:'protect',amount:0,hpBefore:target.hp,hpAfter:target.hp,maxHp:target.maxHp});applyEffects(b,user,target,skill.effects.filter(e=>e.kind!=='status'&&e.kind!=='strip'&&!(e.kind==='buff'&&e.target==='foe')),skill);return;}
    const hits=skill.hits??1;let total=0,actualTotal=0,last={mult:1,crit:false,attr:user.attr as Attr};
    const hitEvents:BattleEvent[]=[];
    for(let i=0;i<hits&&target.hp>0;i++){
      const r=damage(b,user,target,skill),e=hurt(b,target,r.amount,{actor:fighterRef(b,user),...skillEvent(skill),attr:r.attr,crit:!!r.crit,multiplier:r.mult,reason:'skill',hit:i+1});
      hitEvents.push(e);total+=r.amount;actualTotal+=e.amount??0;last=r;
    }
    user.attacked=true;
    target.hitBy[last.attr]=(target.hitBy[last.attr]??0)+1;
    b.log.push(`${josa(target.name,'이가')} ${total} 피해를 입었어요.${last.crit?' 급소!':''}${last.mult>1?' 효과가 굉장해요!':last.mult<1?' 효과가 별로예요…':''}`);
    for(const e of hitEvents)linkEvent(b,e);cast.attr=last.attr;
    if(skill.drain&&user.hp>0&&actualTotal>0){const before=user.hp;user.hp=Math.min(user.maxHp,user.hp+Math.round(actualTotal*skill.drain/100));healed(b,user,user,before,'lifesteal',skill);}
    if(user.passive.lifesteal){const before=user.hp;user.hp=Math.min(user.maxHp,user.hp+Math.round(total*user.passive.lifesteal/100));healed(b,user,user,before,'lifesteal',skill);}
    const ug=(s:0|1,id:string)=>b.sides[s].augs.includes(id)?(b.R.battleAug.get(id)!.p.pct??0):0;
    if(ug(user.side,'drain')&&user.hp>0&&total>0){const before=user.hp,h=Math.round(total*ug(user.side,'drain')/100);user.hp=Math.min(user.maxHp,user.hp+h);b.log.push(`${josa(user.name,'이가')} 체력을 ${h} 되찾았어요.`);healed(b,user,user,before,'drain',skill);}
    if(ug(target.side,'thorns')&&total>0){const r=Math.max(1,Math.round(total*ug(target.side,'thorns')/100)),e=hurt(b,user,r,{actor:fighterRef(b,target),reason:'thorns'});b.log.push(`가시 깃털! ${josa(user.name,'이가')} ${r} 피해를 돌려받았어요.`);linkEvent(b,e);}
    if(target.passive.reflect&&target.hp>0){const r=Math.round(total*target.passive.reflect/100),e=hurt(b,user,r,{actor:fighterRef(b,target),reason:'reflect'});b.log.push(`반사! ${josa(user.name,'이가')} ${r} 피해를 돌려받았어요.`);linkEvent(b,e);}
    if(last.mult<1&&b.sides[target.side].augs.includes('counter')&&target.hp>0){
      const r=damage(b,target,user,{id:'counter',name:'역습',power:b.R.battleAug.get('counter')!.p.power??40,cd:0,effects:[],desc:''}),e=hurt(b,user,r.amount,{actor:fighterRef(b,target),reason:'counter',skillId:'counter',skillName:'역습',attr:r.attr,crit:!!r.crit,multiplier:r.mult});b.log.push(`역습! ${josa(user.name,'이가')} ${r.amount} 피해를 입었어요.`);linkEvent(b,e);
    }
  }
  applyEffects(b,user,target,skill.effects,skill);
}

function doSwitch(b:Battle,side:0|1,to:number){
  const s=b.sides[side],out=s.fighters[s.active],inn=s.fighters[to];
  if(!inn||!alive(inn)||to===s.active)throw new Error('교체할 수 없음');
  if(out.status.bind)throw new Error('묶여서 교체할 수 없음');
  if(out.lockAtk&&alive(out))throw new Error('뒤로 걷는 법을 몰라서 교체할 수 없음');
  if(out.passive.handoff){const before=inn.hp;inn.hp=Math.min(inn.maxHp,inn.hp+Math.round(inn.maxHp*out.passive.handoff/100));healed(b,out,inn,before,'handoff');}
  s.active=to;b.log.push(`${josa(out.name,'은는')} 들어가고 ${josa(inn.name,'이가')} 나왔어요!`);
  event(b,{kind:'switch',actor:fighterRef(b,out),target:fighterRef(b,inn),reason:'action'});
}

// 아이템을 지금 쓸 수 있는지(못 쓰면 이유). 개수는 엔진 밖(계정)에서 따로 셈
export function itemProblem(b:Battle,side:0|1,item:string,target?:number):string|null{
  const it=b.R.data.items?.[item],s=b.sides[side],f=activeOf(b,side);
  if(!it)return '없는 아이템';
  if(s.itemsUsed>=(b.R.data.itemRules?.perBattle??0))return `아이템은 대결 한 번에 ${b.R.data.itemRules?.perBattle??0}개까지예요.`;
  if(it.revive){const t=target===undefined?s.fighters.findIndex(x=>!alive(x)):target;return s.fighters[t]&&!alive(s.fighters[t])?null:'다시 일으킬 갈매미가 없어요.';}
  if(!alive(f))return '나와 있는 갈매미가 전투불가예요.';
  if(it.heal&&f.hp>=f.maxHp&&!it.cleanse)return '체력이 가득해요.';
  if(it.cleanse&&!it.heal&&!Object.keys(f.status).length)return '풀 상태 이상이 없어요.';
  return null;
}
function useItem(b:Battle,side:0|1,item:string,target?:number){
  const why=itemProblem(b,side,item,target);if(why)throw new Error(why);
  const it=b.R.data.items[item],s=b.sides[side],f=activeOf(b,side),who=side===0?'내 팀':'상대';
  s.itemsUsed++;
  b.log.push(`[${who}] 아이템 「${it.name}」!`);
  const d=it.revive?s.fighters[target===undefined?s.fighters.findIndex(x=>!alive(x)):target]:f;
  event(b,{kind:'item',actor:fighterRef(b,f),target:fighterRef(b,d),skillId:item,skillName:it.name});
  if(it.revive){const before=d.hp;d.hp=Math.round(d.maxHp*it.revive/100);d.status={};b.log.push(`"앙~" ${josa(d.name,'이가')} 다시 일어났어요!`);healed(b,f,d,before,item,undefined,true);statusEvent(b,d,'revive',f);}
  if(it.heal){const before=f.hp;f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*it.heal/100));b.log.push(`${f.name}의 체력이 ${f.hp-before} 회복됐어요.`);healed(b,f,f,before,item);}
  if(it.cleanse){f.status={};b.log.push(`${josa(f.name,'이가')} 정신을 차렸어요! 상태 이상이 풀렸어요.`);statusEvent(b,f,'cleanse',f);}
}

// 합동기: 팀당 1번, 같은 시너지 동료가 하나 이상 살아 있어야
function comboReady(b:Battle,side:0|1,sk:Skill){
  if(!sk.id.startsWith('combo-'))return true;
  const s=b.sides[side],f=s.fighters[s.active],k=b.R.unitDef(f.rec.id)?.syn;
  return !s.comboUsed&&s.fighters.some(x=>x!==f&&alive(x)&&b.R.unitDef(x.rec.id)?.syn===k);
}
// 순수 보조 기술은 새 효과가 있을 때만 사용. 공격에 딸린 효과도 중복 누적하지 않음.
export function skillProblem(b:Battle,side:0|1,sk:Skill):string|null{
  if(!comboReady(b,side,sk))return '이미 사용했거나 합동기 동료가 없어요.';
  if(sk.power)return null;
  const u=activeOf(b,side),t=activeOf(b,foeSide(side)),team=b.sides[side].fighters;
  if(u.lastUtility[sk.id]===b.turn-1)return '이미 적용되었습니다. 같은 보조 기술을 연속해서 사용할 수 없어요.';
  const useful=sk.effects.some(e=>{
    switch(e.kind){
      case 'buff':return (e.target==='self'?[u]:e.target==='team'?team:[t]).some(f=>alive(f)&&!f.mods.some(m=>m.stat===e.stat&&Math.sign(m.pct)===Math.sign(e.pct)&&Math.abs(m.pct)>=Math.abs(e.pct)));
      case 'status':return alive(t)&&!t.immune&&!t.status[e.status]&&(t.statusUntil[e.status]??0)<b.turn&&(e.status!=='rage'||t.peanut);
      case 'heal':return u.healUntil<b.turn&&(e.target==='self'?[u]:team.filter(f=>f!==u)).some(f=>alive(f)&&(f.hp<f.maxHp||(e.target==='self'&&f.status.burn)));
      case 'guard':return !u.guards.some(g=>g.pct>=e.pct);
      case 'endure':return !u.endure;
      case 'protect':return !u.protect;
      case 'regen':return (e.target==='self'?[u]:team).some(f=>alive(f)&&!f.regens.some(g=>g.pct>=e.pct));
      case 'cleanse':return team.some(f=>alive(f)&&(Object.keys(f.status).length||!f.immune));
      case 'revive':return !b.sides[side].reviveUsed&&team.some(f=>!alive(f));
      case 'strip':return t.mods.some(m=>m.pct>0)||t.guards.length>0;
      case 'lockSelf':return !u.lockAtk;
      case 'recharge':return false;
    }
  });
  return useful?null:'이미 적용되었거나 적용할 대상이 없어요.';
}
export function legalActions(b:Battle,side:0|1):Action[]{
  const s=b.sides[side],f=activeOf(b,side),out:Action[]=[];
  for(const sk of f.skills)if(!skillProblem(b,side,sk))out.push({kind:'skill',skillId:sk.id});
  if(!out.length)out.push({kind:'skill',skillId:STRUGGLE.id});   // 모든 기술이 재사용 대기면 몸통 박치기
  if(!f.status.bind&&!(f.lockAtk&&alive(f)))s.fighters.forEach((x,i)=>{if(i!==s.active&&alive(x))out.push({kind:'switch',to:i});});
  return out;
}

// 한 턴 진행. 양쪽 행동을 동시에 받음
export function act(b:Battle,actions:[Action,Action],options?:{prevalidated?:boolean}){
  if(b.phase!=='action')throw new Error('지금은 행동할 때가 아님');
  const order:{side:0|1;action:Action;pri:number;spd:number;tie:number}[]=[];
  for(const side of [0,1] as const){
    let action=actions[side];
    const s=b.sides[side];
    if(action.kind==='item'){useItem(b,side,action.item,action.target);continue;}
    if(action.kind==='switch'){
      doSwitch(b,side,action.to);
      if(s.augs.includes('freeswitch'))action=action.then?{kind:'skill',skillId:action.then}:pickAi(b,side);   // 교체가 턴을 안 쓰면 새로 나온 갈매미가 바로 행동
      else continue;
    }
    const f=activeOf(b,side),skill=skillOf(f,(action as {skillId:string}).skillId);
    if(!skill)throw new Error('쓸 수 없는 기술');
    // Realtime commands are prevalidated together against the phase-start snapshot.
    if(!options?.prevalidated){const why=skillProblem(b,side,skill);if(why)throw new Error(why);}
    order.push({side,action,pri:(s.goFirst?2:0)+(skill.priority?1:0),spd:statOf(b,f,'spd'),tie:rand(b)});
    s.goFirst=false;
  }
  order.sort((x,y)=>y.pri-x.pri||y.spd-x.spd||y.tie-x.tie);
  for(const o of order){
    const user=activeOf(b,o.side),target=activeOf(b,foeSide(o.side));
    if(!alive(user)||!alive(target))continue;
    user.actedTurn=b.turn;
    if(user.status.sleep){b.log.push(`${josa(user.name,'은는')} 쿨쿨 자고 있어요.`);continue;}
    if(user.status.freeze){b.log.push(`${josa(user.name,'은는')} 얼어서 움직이지 못해요.`);continue;}
    const sk=skillOf(user,(o.action as {skillId:string}).skillId)!;
    if(sk.id.startsWith('combo-')){b.sides[o.side].comboUsed=true;b.log.push(`합동기 「${sk.name}」! ${b.R.data.synergies[sk.id.slice(6)]?.name??''}의 힘이 모였어요!`);}
    useSkill(b,user,target,sk);
  }
  endTurn(b);
}

function endTurn(b:Battle){
  for(const s of b.sides){
    const f=s.fighters[s.active];
    if(alive(f)){
      if(f.status.burn){const d=Math.max(1,Math.round(f.maxHp*b.R.data.status.burnDamage/100)),e=hurt(b,f,d,{reason:'burn'});b.log.push(`${josa(f.name,'이가')} 화상으로 ${d} 피해를 입었어요.`);linkEvent(b,e);}
      let heal=0;for(const r of f.regens)heal+=r.pct;if(f.passive.regen)heal+=f.passive.regen;
      if(s.augs.includes('lunch'))heal+=b.R.battleAug.get('lunch')!.p.pct??0;
      if(heal&&alive(f)){const before=f.hp;f.hp=Math.min(f.maxHp,f.hp+Math.round(f.maxHp*heal/100));healed(b,f,f,before,'regen');}
    }
    for(const x of s.fighters){
      const statusBefore=JSON.stringify(x.status);
      for(const k of Object.keys(x.cds))if(x.cds[k]>0)x.cds[k]--;
      x.mods=x.mods.filter(m=>--m.turns>0);x.guards=x.guards.filter(g=>--g.turns>0);x.regens=x.regens.filter(r=>--r.turns>0);
      for(const k of Object.keys(x.status) as Status[]){if(--x.status[k]!<=0)delete x.status[k];}
      if(JSON.stringify(x.status)!==statusBefore)statusEvent(b,x,'expire');
      if(x.endure>0)x.endure--;if(x.immune>0)x.immune--;x.protect=false;
    }
    if(s.neutral>0)s.neutral--;
  }
  b.turn++;
  // 전투불가 처리
  let fainted=false;
  for(const side of [0,1] as const){
    const s=b.sides[side],f=s.fighters[s.active];
    if(!alive(f)){fainted=true;b.log.push(`${josa(f.name,'이가')} 전투불가가 됐어요!`);event(b,{kind:'faint',target:fighterRef(b,f),hpBefore:f.hp,hpAfter:f.hp,maxHp:f.maxHp,turn:b.turn-1});
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
    const sk=skillOf(f,a.skillId)!;let v=0;
    if(sk.power){const preview=skillPreview(b,side,sk);v=preview.power*(sk.hits??1)*preview.multiplier*(preview.attr===f.attr?b.R.data.multipliers.sameAttr:1);}
    if(sk.effects.some(e=>e.kind==='heal')){
      let healing=0;
      if(f.healUntil<b.turn)for(const e of sk.effects)if(e.kind==='heal'){
        const targets=e.target==='self'?[f]:b.sides[side].fighters.filter(x=>x!==f);
        for(const target of targets)if(alive(target)&&(target.hp<target.maxHp||(e.target==='self'&&(target.status.burn??0)>0)))healing=Math.max(healing,target.hp<target.maxHp*0.45?200:5);
      }
      v=Math.max(v,healing);
    }
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
    const bad=b.R.typeMult(foe.attr,me.attr)>1||b.R.typeMult(me.attr,foe.attr)<1;
    const prefer=bad?['camo','reverse','weakguard','neutral','dual','counter']:['focus','dual','overtime','pierce','first','peanut','strip','sharp','bulwark'];
    p.augment=prefer.find(id=>s.offers!.includes(id))??s.offers[0];
  }
  if(s.needSwitch){const foe=activeOf(b,foeSide(side));let bi=-1,bv=-1;
    s.fighters.forEach((f,i)=>{if(!alive(f))return;const v=b.R.typeMult(f.attr,foe.attr)/b.R.typeMult(foe.attr,f.attr);if(v>bv){bv=v;bi=i;}});p.switchTo=bi;}
  choose(b,side,p);
}
