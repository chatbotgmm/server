// 수비대 대결 규칙 계산(검사 없이): 서버가 검사한 data/battle.yaml 데이터를 엔진이 쓰는 형태로 바꿈.
// 브라우저 묶음에도 들어가므로 검사 라이브러리(zod)를 쓰지 않음. 검사는 rules.ts(buildRules)에서.
// 기술 설명은 적지 않으면 숫자로 자동으로 만듦 → 숫자만 고쳐도 설명이 맞음
import type { BattleData } from './rules.js';
import { recipePowerBonus } from './recipe-value.js';
import { augmentCatalogV2, augmentTemplateV2, parseAugmentCatalogV2, type AugmentCatalogV2, type CatalogTier } from './augment-catalog-v2.js';
export { battleAugIdsV2, augmentCatalogV2, parseAugmentCatalogV2, createAugmentConfigV2 } from './augment-catalog-v2.js';
export type { AugmentCatalogV2, CatalogTier } from './augment-catalog-v2.js';
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
// cat: 물리(물리공격↔물리방어)·마법(마법공격↔마법방어)·자동(쓰는 유닛의 더 높은 공격)
export type Cat='phys'|'mag'|'auto';
export const catName:Record<Cat,string>={phys:'물리',mag:'마법',auto:'자동'};
export type SkillCondition='burned'|'slowed'|'bound'|'lowHp'|'finisher'|'opening'|'slower'|'weakened';
export interface Skill {id:string;name:string;attr?:Attr;power?:number;cat?:Cat;hits?:number;priority?:boolean;crit?:boolean;cd:number;effects:Effect[];desc:string;pierce?:number;drain?:number;bonus?:{when:SkillCondition;pct:number};basePower?:number;recipeValue?:number;recipeBonusPct?:number;summary?:string}
export interface UnitStats {hp:number;patk:number;pdef:number;matk:number;mdef:number;spd:number}
export interface UnitDef {id:string;attr:Attr;sub?:Attr;syn?:string;basic?:string;basicCat?:Cat;stats?:UnitStats;mirror?:boolean;role:Role;signature:Skill;name?:string;power?:number;hpMult?:number;boss?:boolean}
export interface Passive {hpPct?:number;atkPct?:number;defPct?:number;spdPct?:number;pierce?:number;execute?:number;critPlus?:number;opener?:number;
  lifesteal?:number;reflect?:number;cut?:number;regen?:number;lastStand?:boolean;statusPlus?:number;statusResist?:number;sigCd?:number;handoff?:number;durationPlus?:number}
export interface EvoAugment {id:string;name:string;desc:string;roles:Role[]|'ANY';passive:Passive}
export interface BattleAugment {id:string;name:string;desc:string;p:Record<string,number>;tier?:CatalogTier;version?:2;effect?:'choose-own-attribute'}
// Version is absent in old saved rules; absence always means legacy V1.
export interface RulesOptions {augmentVersion?:1|2;augmentCatalog?:AugmentCatalogV2}
export const battleAugIds=['reverse','weakguard','focus','neutral','camo','dual','counter','learn','first','bulwark','firstaid','freeswitch','lastone','strip','rustle','peanut','sharp','swift','thick','overtime','drain','thorns','pierce','lunch'] as const;
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
  if(s.pierce)parts.push(`상대 방어 ${s.pierce}% 무시`);
  if(s.drain)parts.push(`실제로 준 피해의 ${s.drain}%만큼 자신 체력 회복`);
  if(s.bonus){
    const condition:Record<SkillCondition,string>={burned:'화상 상태인 상대에게',slowed:'속도가 감소한 상대에게',bound:'묶인 상대에게',lowHp:'자신 체력이 50% 이하일 때',finisher:'상대 체력이 40% 이하일 때',opening:'상대 체력이 90% 이상일 때',slower:'자신이 상대보다 느릴 때',weakened:'방어가 감소한 상대에게'};
    parts.push(`${condition[s.bonus.when]} 위력 +${s.bonus.pct}%`);
  }
  for(const e of s.effects)parts.push(effectText(e,attrName));
  return parts.join(', ')||'효과 없음';
}

// 전투 카드에는 선택에 필요한 핵심만, 전체 효과는 desc에 유지해요.
export function skillSummary(s:Omit<Skill,'id'|'desc'>):string{
  const parts:{text:string;rank:number}[]=[];
  const add=(text:string,rank=3)=>parts.push({text,rank});
  if(s.priority)add('선제 공격',2);
  if(s.crit)add('반드시 급소',2);
  if(s.pierce)add(`방어 ${s.pierce}% 관통`,2);
  if(s.drain)add(`피해 ${s.drain}% 흡수`,2);
  if(s.bonus){
    const label:Record<SkillCondition,string>={burned:'화상 적',slowed:'감속된 적',bound:'묶인 적',lowHp:'내 체력 50%↓',finisher:'체력 40%↓ 적',opening:'체력 90%↑ 적',slower:'느릴 때',weakened:'방어 약화 적'};
    add(`${label[s.bonus.when]} +${s.bonus.pct}%`,1);
  }
  for(const e of s.effects){
    const duration='turns' in e?` · ${e.turns}턴`:'';
    switch(e.kind){
      case 'buff':add(`${e.target==='team'?'아군 ':e.target==='self'?'내 ':''}${statName[e.stat]} ${fmt(e.pct)}${duration}`,e.target==='self'&&e.pct<0?0:3);break;
      case 'status':add(`${statusName[e.status]} ${e.chance}%${duration}`);break;
      case 'heal':add(`${e.target==='bench'?'대기 아군':'내 체력'} ${e.pct}% 회복`);break;
      case 'guard':add(`받는 피해 -${e.pct}%${duration}`);break;
      case 'endure':add(`${e.turns}턴 쓰러짐 방지`);break;
      case 'protect':add('이번 턴 피해 무효');break;
      case 'regen':add(`${e.target==='team'?'아군':'자신'} 매턴 ${e.pct}% 회복${duration}`);break;
      case 'cleanse':add(`상태 해제${e.immuneTurns?` · ${e.immuneTurns}턴 면역`:''}`);break;
      case 'revive':add(`아군 부활 · 체력 ${e.pct}%`);break;
      case 'strip':add('상대 강화 제거');break;
      case 'lockSelf':add(`공격 +${e.atkPct}% · 교체 불가`,0);break;
      case 'recharge':break; // 구형 데이터 호환: 현재 엔진은 휴식하지 않아요.
    }
  }
  return [...new Set(parts.sort((a,b)=>a.rank-b.rank).map(p=>p.text))].slice(0,2).join(' · ');
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
  hpPct:v=>`최대 체력 +${v}%`,atkPct:v=>`물리·마법 공격 +${v}%`,defPct:v=>`물리·마법 방어 +${v}%`,spdPct:v=>`속도 +${v}%`,
};
export function augDesc(id:string,p:Record<string,number>,attrNm:string){
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
    case 'rustle':return '상대 선두가 새우gang 소리에 1턴 멈춰요';
    case 'peanut':return `내 갈매미맨들의 공격 +${p.atk}%, 대신 받는 피해 +${p.taken}%`;
    case 'sharp':return `내 갈매미들의 급소 확률 +${p.pct}%`;
    case 'swift':return `내 갈매미들의 속도 +${p.pct}%(먼저 움직이기 쉬워요)`;
    case 'thick':return `내 갈매미들의 물리·마법 방어 +${p.pct}%`;
    case 'overtime':return `내 갈매미들의 물리·마법 공격 +${p.pct}%`;
    case 'drain':return `내가 준 피해의 ${p.pct}%만큼 체력 회복`;
    case 'thorns':return `나를 때린 상대가 준 피해의 ${p.pct}%를 돌려받아요`;
    case 'pierce':return `내 공격이 상대 방어를 ${p.pct}% 무시해요`;
    case 'lunch':return `턴이 끝날 때마다 나와 있는 내 갈매미 체력 ${p.pct}% 회복`;
  }
  return augmentTemplateV2(id)?.desc??'';
}

// 검사를 마친 데이터로 규칙 만들기(브라우저는 서버가 검사한 데이터를 받아 이것만 씀)
export function rulesFrom(d:BattleData,options:RulesOptions={}){
  const augmentVersion=options.augmentVersion??d.augmentVersion??1;
  if(augmentVersion!==1&&augmentVersion!==2)throw new Error('증강 버전은 1 또는 2여야 해요');
  if((options.augmentCatalog||d.augmentCatalog)&&augmentVersion!==2)throw new Error('V2 증강 카탈로그에는 버전 2가 필요해요');
  const catalog=augmentVersion===2?parseAugmentCatalogV2(options.augmentCatalog??d.augmentCatalog??augmentCatalogV2):undefined;
  // 상성: 강점 2·약점 2, 자기 자신 금지
  for(const a of attrKeys){
    const s=d.chart[a].strong;
    if(s[0]===s[1]||s.includes(a))throw new Error(`상성 오류 ${a}: 같은 속성이 겹치거나 자기 자신에게 강함`);
    const weakTo=attrKeys.filter(x=>d.chart[x].strong.includes(a)).length;
    if(weakTo!==2)throw new Error(`상성 오류 ${a}: 약점이 ${weakTo}개(2개여야 함)`);
  }
  const attrName=(a:Attr)=>d.attributes[a].name;
  const typeMult=(atk:Attr,def:Attr)=>d.chart[atk].strong.includes(def)?d.multipliers.strong:d.chart[def].strong.includes(atk)?d.multipliers.weak:1;
  const toCat=(c?:string):Cat|undefined=>c==='물리'?'phys':c==='마법'?'mag':c==='자동'?'auto':undefined;
  const mkSkill=(id:string,s:SkillIn,a:Attr):Skill=>{
    if(!s.power&&(s.pierce||s.drain||s.bonus))throw new Error(`기술 「${s.name}」: 관통·흡혈·조건 강화에는 공격 위력이 필요해요`);
    if(s.power&&!s.cat)throw new Error(`기술 「${s.name}」: 공격 기술은 cat(물리·마법·자동)을 정해야 해요`);
    const base={name:s.name.replaceAll('{속성}',attrName(a)),cd:0,effects:(s.effects??[]).map(toEffect).filter(e=>e.kind!=='recharge'),
      ...(s.power?{power:s.power,attr:s.attr??a,cat:toCat(s.cat)}:{}),...(s.pierce?{pierce:s.pierce}:{}),...(s.drain?{drain:s.drain}:{}),...(s.bonus?{bonus:{...s.bonus}}:{}),...(s.hits?{hits:s.hits}:{}),...(s.priority?{priority:true}:{}),...(s.crit?{crit:true}:{})};
    return {id,...base,desc:s.desc??skillDesc(base,attrName),summary:skillSummary(base)};
  };
  const basicSkill=(a:Attr,cat?:Cat):Skill=>({id:`basic-${a}`,name:d.attributes[a].basic,attr:a,power:d.basicPower,cat:cat??toCat(d.attributes[a].basicCat),cd:0,effects:[],desc:`위력 ${d.basicPower}`});
  const roleSkill=(r:Role,a:Attr):Skill=>mkSkill(`role-${r}`,d.roles[r].skill,a);
  // 보조 속성 기술: 자기 속성이 아닌 공격(상성이 불리할 때 꺼내 씀)
  const subSkill=(a:Attr):Skill=>({id:`sub-${a}`,name:d.attributes[a].sub,attr:a,power:d.subSkill.power,cat:toCat(d.attributes[a].subCat),cd:0,effects:[],desc:`${attrName(a)} 속성 공격 · 위력 ${d.subSkill.power}`});
  const synOf=new Map<string,string>();for(const [k,v] of Object.entries(d.synergies))for(const m of v.members)synOf.set(m,k);
  const units=new Map<string,UnitDef>(Object.entries(d.units).map(([id,u])=>[id,{id,attr:u.attr,sub:u.sub,syn:synOf.get(id),role:u.role,stats:u.stats,signature:mkSkill('sig',u.skill,u.attr)}]));
  // 스토리 빌런: 공격·방어는 역할 기본값 × power, 체력은 × hp(없으면 power). 졸병 1 미만, 보스는 hp를 크게
  const villains=new Map<string,UnitDef>(Object.entries(d.villains).map(([id,v])=>[id,{id,name:v.name,basic:v.basic,basicCat:toCat(v.basicCat),mirror:v.mirror,attr:v.attr,sub:v.sub,role:v.role,power:v.power,hpMult:v.hp??v.power,boss:v.boss??false,signature:mkSkill('sig',v.skill,v.attr)}]));
  const unitDef=(id:string)=>units.get(id)??villains.get(id);
  if(d.elemental)for(const a of attrKeys)if(!d.elemental[a])throw new Error(`속성 기술 누락: ${a}`);
  if(d.recipePower)for(const id of units.keys())if(!Number.isSafeInteger(d.recipeValues?.[id])||(d.recipeValues?.[id]??0)<=0)throw new Error(`조합 가치 누락: ${id}`);
  const recipeValue=(id:string)=>units.has(id)?d.recipeValues?.[id]:undefined;
  const recipeBonus=(id:string)=>{const n=recipeValue(id);return n!==undefined&&d.recipePower?recipePowerBonus(n,d.recipePower):0;};
  const unitPower=(id:string,skill:Skill):Skill=>{
    const value=recipeValue(id);if(value===undefined||!skill.power)return skill;
    const base=skill.basePower??skill.power,bonus=recipeBonus(id),power=Math.max(1,Math.round(base*(1+bonus/100)));
    // 위력은 한 번만 보정하며, 설명과 전투 이벤트도 같은 수치를 사용해요.
    return {...skill,power,basePower:base,recipeValue:value,recipeBonusPct:bonus,desc:skill.desc.replace(new RegExp(`위력 ${skill.power}(?=으로|,| ·|$)`),`위력 ${power}`)};
  };
  const elementalSkill=(kind:'basic'|'sub'|'attack',a:Attr):Skill|undefined=>{
    const skill=d.elemental?.[a]?.[kind];return skill?mkSkill(kind==='attack'?'role-ATTACK':`${kind}-${a}`,skill,a):undefined;
  };
  // 시너지 합동기(같은 시너지 2마리+, 팀당 1번)·레벨업으로 배우는 기술. 속성을 안 적은 기술은 쓰는 유닛의 속성
  const comboSkill=(k:string,a:Attr):Skill|undefined=>d.synergies[k]?mkSkill(`combo-${k}`,d.synergies[k].skill,a):undefined;
  const learnSkill=(id:string,a:Attr):Skill|undefined=>{
    const s=d.learn.skills[id];if(!s)return undefined;const {level,roles,...sk}=s;void level;void roles;
    const trait=!s.attr&&s.power?d.elemental?.[a]?.basic:undefined;
    if(!trait)return mkSkill(`learn-${id}`,sk,a);
    // 기본기를 잊어도 자기 속성으로 쓰는 공용 공격에는 그 속성의 전투 특성이 남아요.
    // 특정 속성이 정해진 학습기·보조 기술에는 다른 속성 특성을 섞지 않아요.
    const effects=[...(sk.effects??[]),...(trait.effects??[])].filter((effect,index,list)=>list.findIndex(other=>JSON.stringify(other)===JSON.stringify(effect))===index);
    const split=!!trait.hits&&!sk.hits;
    return mkSkill(`learn-${id}`,{...sk,effects,...(trait.pierce?{pierce:Math.max(sk.pierce??0,trait.pierce)}:{}),...(trait.drain?{drain:Math.max(sk.drain??0,trait.drain)}:{}),
      ...(!(sk.bonus)&&trait.bonus?{bonus:trait.bonus}:{}),...(split?{power:Math.round(sk.power!*.55),hits:2}:{})},a);
  };
  const learnables=Object.entries(d.learn.skills).map(([id,s])=>({id,level:s.level,roles:s.roles}));
  const battleAugments:BattleAugment[]=catalog?catalog.augments.map(a=>({...a,p:{...a.p}})):battleAugIds.map(id=>{
    const raw=d.battleAugments[id] as {name:string;desc?:string}&Record<string,unknown>;
    const p=Object.fromEntries(Object.entries(raw).filter(([,v])=>typeof v==='number')) as Record<string,number>;
    return {id,name:raw.name,desc:raw.desc??augDesc(id,p,''),p};
  });
  const evoAugments:EvoAugment[]=Object.entries(d.evoAugments).map(([id,a])=>{
    const {name,roles,desc,...passive}=a;
    // 예전 재사용 감소 표창은 공격 보정으로 이어받아 무효 표창이 되지 않게 함.
    if(passive.sigCd){passive.atkPct=(passive.atkPct??0)+passive.sigCd*5;delete passive.sigCd;}
    const text=(Object.entries(passive) as [keyof Passive,number|boolean][]).map(([k,v])=>passiveText[k](Number(v))).join(', ');
    return {id,name,roles,passive:passive as Passive,desc:desc??text};
  });
  return {data:d,augmentVersion,attrName,typeMult,basicSkill,roleSkill,subSkill,elementalSkill,unitPower,recipeValue,recipeBonus,comboSkill,learnSkill,learnables,units,villains,unitDef,battleAugments,battleAug:new Map(battleAugments.map(a=>[a.id,a])),
    evoAugments,evoAug:new Map(evoAugments.map(a=>[a.id,a]))};
}
export type Rules=ReturnType<typeof rulesFrom>;


