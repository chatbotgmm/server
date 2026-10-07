// 수비대 대결 규칙 계산(검사 없이): 서버가 검사한 data/battle.yaml 데이터를 엔진이 쓰는 형태로 바꿈.
// 브라우저 묶음에도 들어가므로 검사 라이브러리(zod)를 쓰지 않음. 검사는 rules.ts(buildRules)에서.
// 기술 설명은 적지 않으면 숫자로 자동으로 만듦 → 숫자만 고쳐도 설명이 맞음
import type { BattleData } from './rules.js';
export const attrKeys=['STONE','GOLD','FIRE','ICE','BLOSSOM','SEA','SHADOW','STAR','THREAD','GRAVITY'] as const;
export type Attr=typeof attrKeys[number];
export const roleKeys=['ATTACK','DEFENSE','UTILITY'] as const;
export type Role=typeof roleKeys[number];
export const topTiers=['LIMITED','TRANSCEND','ETERNAL','IMMORTAL'] as const;
export type TopTier=typeof topTiers[number];
export type Stat='atk'|'def'|'spd';
export const statusKeys=['burn','freeze','sleep','bind','rage'] as const;
export type Status=typeof statusKeys[number];
export const statusName:Record<Status,string>={burn:'화상',freeze:'빙결',sleep:'잠',bind:'묶임',rage:'땅콩 폭주'};
const statName:Record<Stat,string>={atk:'공격',def:'방어',spd:'속도'};

export type Effect=
  |{kind:'buff';stat:Stat;pct:number;turns:number;target:'self'|'foe'|'team'}
  |{kind:'status';status:Status;chance:number;turns:number}
  |{kind:'heal';pct:number;target:'self'|'bench'}
  |{kind:'guard';pct:number;turns:number}
  |{kind:'endure';turns:number}
  |{kind:'protect'}
  |{kind:'regen';pct:number;turns:number;target:'self'|'team'}
  |{kind:'cleanse';immuneTurns:number}
  |{kind:'revive';pct:number}
  |{kind:'strip'}
  |{kind:'recharge'}
  |{kind:'lockSelf';atkPct:number};
export interface Skill {id:string;name:string;attr?:Attr;power?:number;hits?:number;priority?:boolean;crit?:boolean;cd:number;effects:Effect[];desc:string}
export interface UnitDef {id:string;attr:Attr;role:Role;signature:Skill}
export interface Passive {hpPct?:number;atkPct?:number;defPct?:number;spdPct?:number;pierce?:number;execute?:number;critPlus?:number;opener?:number;
  lifesteal?:number;reflect?:number;cut?:number;regen?:number;lastStand?:boolean;statusPlus?:number;statusResist?:number;sigCd?:number;handoff?:number;durationPlus?:number}
export interface EvoAugment {id:string;name:string;desc:string;roles:Role[]|'ANY';passive:Passive}
export interface BattleAugment {id:string;name:string;desc:string;p:Record<string,number>}
export const battleAugIds=['reverse','weakguard','focus','neutral','camo','dual','counter','learn','first','bulwark','firstaid','freeswitch','lastone','strip','rustle','peanut'] as const;
type SkillIn=BattleData['roles']['ATTACK']['skill'];
type EffectIn=NonNullable<SkillIn['effects']>[number];

// ── 자동 설명 ──
const fmt=(n:number)=>`${n>0?'+':''}${n}%`;
function effectText(e:Effect,attrName:(a:Attr)=>string):string{
  void attrName;
  switch(e.kind){
    case 'buff':return `${e.turns}턴 동안 ${e.target==='self'?'자신':e.target==='team'?'아군 전체':'상대'} ${statName[e.stat]} ${fmt(e.pct)}`;
    case 'status':return `${e.chance>=100?'':`${e.chance}% 확률로 `}상대 ${statusName[e.status]}(${e.turns}턴)`;
    case 'heal':return `${e.target==='self'?'자신':'대기 아군'} 체력 ${e.pct}% 회복`;
    case 'guard':return `${e.turns}턴 동안 받는 피해 -${e.pct}%`;
    case 'endure':return `${e.turns}턴 동안 체력이 1 아래로 안 떨어짐`;
    case 'protect':return '이번 턴 공격을 모두 막음';
    case 'regen':return `${e.turns}턴 동안 ${e.target==='team'?'아군 모두':'자신'} 턴마다 체력 ${e.pct}% 회복`;
    case 'cleanse':return `아군 상태 이상을 풀고 ${e.immuneTurns}턴 동안 막음`;
    case 'revive':return `쓰러진 아군 하나를 체력 ${e.pct}%로 일으킴(전투당 1번)`;
    case 'strip':return '상대 강화를 모두 지움';
    case 'recharge':return '다음 턴은 쉼';
    case 'lockSelf':return `이후 교체할 수 없는 대신 공격 +${e.atkPct}%`;
  }
}
export function skillDesc(s:Omit<Skill,'desc'|'id'>,attrName:(a:Attr)=>string){
  const parts:string[]=[];
  if(s.priority)parts.push('무조건 먼저');
  if(s.power)parts.push(`위력 ${s.power}${s.hits?`으로 ${s.hits}번`:''}`);
  if(s.crit)parts.push('반드시 급소');
  for(const e of s.effects)parts.push(effectText(e,attrName));
  return parts.join(', ')||'효과 없음';
}

function toEffect(e:EffectIn):Effect{
  if('buff' in e)return {kind:'buff',stat:e.buff,pct:e.pct,turns:e.turns,target:e.to};
  if('status' in e)return {kind:'status',status:e.status,chance:e.chance,turns:e.turns};
  if('heal' in e)return {kind:'heal',pct:e.heal,target:e.to};
  if('guard' in e)return {kind:'guard',pct:e.guard,turns:e.turns};
  if('endure' in e)return {kind:'endure',turns:e.endure};
  if('regen' in e)return {kind:'regen',pct:e.regen,turns:e.turns,target:e.to};
  if('cleanse' in e)return {kind:'cleanse',immuneTurns:e.cleanse};
  if('revive' in e)return {kind:'revive',pct:e.revive};
  if('strip' in e)return {kind:'strip'};
  if('recharge' in e)return {kind:'recharge'};
  if('lockSelf' in e)return {kind:'lockSelf',atkPct:e.lockSelf};
  return {kind:'protect'};
}

const passiveText:Record<keyof Passive,(v:number)=>string>={
  pierce:v=>`상대 방어 ${v}% 무시`,execute:v=>`체력 30% 이하인 상대에게 피해 +${v}%`,critPlus:v=>`급소 확률 +${v}%p`,opener:v=>`전투 첫 공격 피해 +${v}%`,
  lifesteal:v=>`준 피해의 ${v}% 회복`,reflect:v=>`받은 피해의 ${v}%를 되돌려 줌`,cut:v=>`받는 피해 -${v}%`,regen:v=>`턴이 끝날 때 체력 ${v}% 회복`,
  lastStand:()=>'전투당 한 번, 쓰러질 피해를 체력 1로 버팀',statusResist:v=>`상태 이상에 걸릴 확률 -${v}%`,statusPlus:v=>`상태 이상 확률 +${v}%p`,
  durationPlus:v=>`주는 강화·약화가 ${v}턴 더 감`,sigCd:v=>`고유기 재사용 ${v}턴 줄어듦`,handoff:v=>`교체되어 나갈 때 들어오는 아군 체력 ${v}% 회복`,
  hpPct:v=>`최대 체력 +${v}%`,atkPct:v=>`공격 +${v}%`,defPct:v=>`방어 +${v}%`,spdPct:v=>`속도 +${v}%`,
};
function augDesc(id:string,p:Record<string,number>,attrNm:string){
  void attrNm;
  switch(id){
    case 'reverse':return '이번 전투 동안 내 공격의 상성이 뒤집혀요(강함 ↔ 약함)';
    case 'weakguard':return '내가 받는 상성 피해(강함)가 보통 피해가 돼요';
    case 'focus':return `내 상성 공격 배율이 ×${p.value}가 돼요`;
    case 'neutral':return `${p.turns}턴 동안 양쪽 모든 상성이 ×1.0이 돼요`;
    case 'camo':return '지금 나와 있는 내 갈매미의 속성이 상대에게 강한 속성으로 바뀌어요';
    case 'dual':return '지금 나와 있는 내 갈매미가 상대 약점을 찌르는 공격 속성을 하나 더 얻어요';
    case 'counter':return `상성이 불리한 공격을 받으면 위력 ${p.power}으로 바로 반격해요`;
    case 'learn':return `같은 속성에 맞을 때마다 그 속성 피해가 ${p.step}%씩 줄어요(최대 ${p.max}%)`;
    case 'first':return '다음 턴에는 무조건 내가 먼저 움직여요';
    case 'bulwark':return `지금 나와 있는 내 갈매미가 ${p.turns}턴 동안 받는 피해 -${p.pct}%`;
    case 'firstaid':return `쓰러지지 않은 아군 모두 체력 ${p.pct}% 회복`;
    case 'freeswitch':return '이번 전투 동안 교체가 턴을 쓰지 않아요';
    case 'lastone':return `아군이 하나만 남으면 그 갈매미의 공격·방어·속도 +${p.pct}%`;
    case 'strip':return `상대 강화를 모두 지우고 상대 선두 공격 -${p.pct}%(${p.turns}턴)`;
    case 'rustle':return '상대 선두가 새우깡 소리에 1턴 멈춰요';
    case 'peanut':return `내 갈매미맨들의 공격 +${p.atk}%, 대신 받는 피해 +${p.taken}%`;
  }
  return '';
}

// 검사를 마친 데이터로 규칙 만들기(브라우저는 서버가 검사한 데이터를 받아 이것만 씀)
export function rulesFrom(d:BattleData){
  // 상성: 강점 2·약점 2, 자기 자신 금지
  for(const a of attrKeys){
    const s=d.chart[a].strong;
    if(s[0]===s[1]||s.includes(a))throw new Error(`상성 오류 ${a}: 같은 속성이 겹치거나 자기 자신에게 강함`);
    const weakTo=attrKeys.filter(x=>d.chart[x].strong.includes(a)).length;
    if(weakTo!==2)throw new Error(`상성 오류 ${a}: 약점이 ${weakTo}개(2개여야 함)`);
  }
  const attrName=(a:Attr)=>d.attributes[a].name;
  const typeMult=(atk:Attr,def:Attr)=>d.chart[atk].strong.includes(def)?d.multipliers.strong:d.chart[def].strong.includes(atk)?d.multipliers.weak:1;
  const mkSkill=(id:string,s:SkillIn,a:Attr):Skill=>{
    const base={name:s.name.replaceAll('{속성}',attrName(a)),cd:s.cd,effects:(s.effects??[]).map(toEffect),
      ...(s.power?{power:s.power,attr:s.attr??a}:{}),...(s.hits?{hits:s.hits}:{}),...(s.priority?{priority:true}:{}),...(s.crit?{crit:true}:{})};
    return {id,...base,desc:s.desc??skillDesc(base,attrName)};
  };
  const basicSkill=(a:Attr):Skill=>({id:`basic-${a}`,name:d.attributes[a].basic,attr:a,power:d.basicPower,cd:0,effects:[],desc:`위력 ${d.basicPower}`});
  const roleSkill=(r:Role,a:Attr):Skill=>mkSkill(`role-${r}`,d.roles[r].skill,a);
  const units=new Map<string,UnitDef>(Object.entries(d.units).map(([id,u])=>[id,{id,attr:u.attr,role:u.role,signature:mkSkill('sig',u.skill,u.attr)}]));
  const battleAugments:BattleAugment[]=battleAugIds.map(id=>{
    const raw=d.battleAugments[id] as {name:string;desc?:string}&Record<string,unknown>;
    const p=Object.fromEntries(Object.entries(raw).filter(([,v])=>typeof v==='number')) as Record<string,number>;
    return {id,name:raw.name,desc:raw.desc??augDesc(id,p,''),p};
  });
  const evoAugments:EvoAugment[]=Object.entries(d.evoAugments).map(([id,a])=>{
    const {name,roles,desc,...passive}=a;
    const text=(Object.entries(passive) as [keyof Passive,number|boolean][]).map(([k,v])=>passiveText[k](Number(v))).join(', ');
    return {id,name,roles,passive:passive as Passive,desc:desc??text};
  });
  return {data:d,attrName,typeMult,basicSkill,roleSkill,units,battleAugments,battleAug:new Map(battleAugments.map(a=>[a.id,a])),
    evoAugments,evoAug:new Map(evoAugments.map(a=>[a.id,a]))};
}
export type Rules=ReturnType<typeof rulesFrom>;
