// 성장: 레벨·경험치·진화(승진) 3단계·진화 증강체(직무 표창). 수치는 data/battle.yaml의 growth·evoAugments
import type { EvoAugment, Passive, Rules, Skill, TopTier } from './core.js';

export type Stage=0|1|2|3;
export const stageName=['원형','1단계','2단계','3단계'] as const;
// 저장되는 유닛 한 마리의 상태(스카우트하면 이 상태 그대로 복사)
export interface UnitRecord {temporary?:boolean;expiresAt?:number|null;uid:string;id:string;name:string;tier:TopTier;level:number;xp:number;stage:Stage;evoAugs:string[];learned?:string[];learnUsed?:number;learnPick?:number;reincarnation?:number;statBonusPct?:number;ownedCharacterId?:string;inheritedFrom?:string}

// 다음 레벨까지 경험치 = xpBase × 레벨^xpExp (기본 20 × 레벨^1.5)
export const xpToNext=(r:Rules,level:number)=>Math.round(r.data.growth.xpBase*level**r.data.growth.xpExp);
// 대결 1번 경험치(나간 갈매미마다): 이기면 winXp × 상대 평균 레벨, 지면 loseXp ×
export const battleXp=(r:Rules,enemyAvgLevel:number,won:boolean)=>Math.round((won?r.data.growth.winXp:r.data.growth.loseXp)*Math.max(1,enemyAvgLevel));
export function addXp(r:Rules,rec:UnitRecord,amount:number){
  const max=r.data.growth.maxLevel;
  rec.xp+=Math.max(0,Math.floor(amount));
  while(rec.level<max&&rec.xp>=xpToNext(r,rec.level)){rec.xp-=xpToNext(r,rec.level);rec.level++;}
  if(rec.level>=max)rec.xp=0;
  return rec;
}
// 진화: 레벨 조건 + 스토리 조건(storyCleared = 깬 부 수)
export function canEvolve(r:Rules,rec:UnitRecord,storyCleared:number){
  return rec.stage<3&&rec.level>=(r.data.growth.evolveLevel as number[])[rec.stage]&&storyCleared>rec.stage;
}
// 그 레벨에서 가능한 최고 진화 단계(스토리 조건 제외)
export const maxStageAt=(r:Rules,level:number)=>r.data.growth.evolveLevel.filter(l=>level>=l).length as Stage;

export function evoChoices(r:Rules,rec:UnitRecord,rand:()=>number){
  const role=r.unitDef(rec.id)!.role;
  const pool=r.evoAugments.filter(a=>(a.roles==='ANY'||a.roles.includes(role))&&!rec.evoAugs.includes(a.id));
  const out:EvoAugment[]=[];
  while(out.length<3&&pool.length)out.push(pool.splice(Math.floor(rand()*pool.length),1)[0]);
  return out;
}
export function passiveOf(r:Rules,rec:UnitRecord):Passive{
  const p:Record<string,number|boolean>={};
  for(const id of rec.evoAugs){const a=r.evoAug.get(id);if(!a)continue;
    for(const [k,v] of Object.entries(a.passive))p[k]=typeof v==='boolean'?v:((p[k] as number|undefined)??0)+(v as number);}
  return p as Passive;
}

// 레벨·진화로 커지는 배율(체력·공격·방어와 피해에 같이 씀)
export const levelScale=(r:Rules,rec:{level:number;stage:Stage})=>(1+r.data.growth.levelGrowth/100*(rec.level-1))*r.data.growth.stageMult[rec.stage];
// 능력치: 유닛은 data/battle.yaml units의 stats(유닛마다 따로), 빌런은 역할 기본값 × power(체력은 × hp).
//   체력·물리공격·물리방어·마법공격·마법방어 = 값 × 등급 보정 × 레벨·진화 배율 × 직무 표창(%)
//   속도 = 값 × (레벨 1당 +1%) × (진화 1·2·3단계 +6·12.5·20%) × 직무 표창 → 레벨 차이로 순서가 크게 뒤집히진 않음
export function statsOf(r:Rules,rec:UnitRecord){
  const def=r.unitDef(rec.id);if(!def)throw new Error(`전투 유닛이 아님: ${rec.id}`);
  const role=r.data.roles[def.role],t=(rec.reincarnation??0)>0&&rec.ownedCharacterId!==rec.id?1:r.data.tiers[rec.tier],scale=levelScale(r,rec),p=passiveOf(r,rec);
  const lv=1+r.data.growth.levelGrowth/100*(rec.level-1),st=r.data.growth.stageMult[rec.stage];
  const pw=def.power??1,hm=def.hpMult??pw;
  const b=((rec.reincarnation??0)>0&&rec.ownedCharacterId!==rec.id?{hp:role.hp,patk:role.atk,pdef:role.def,matk:role.atk,mdef:role.def,spd:role.spd}:def.stats)??{hp:role.hp*hm/pw,patk:role.atk,pdef:role.def,matk:role.atk,mdef:role.def,spd:role.spd};
  const f=(v:number,pct=0,m=def.stats?1:pw)=>Math.round(v*t*m*scale*(1+pct/100)*(1+((rec.reincarnation??0)>0?(rec.statBonusPct??0):0)/100));
  return {hp:f(b.hp,p.hpPct),patk:f(b.patk,p.atkPct),pdef:f(b.pdef,p.defPct),matk:f(b.matk,p.atkPct),mdef:f(b.mdef,p.defPct),
    spd:Math.round(b.spd*(1+(lv-1)*0.25)*(1+(st-1)*0.5)*(1+(p.spdPct??0)/100)*(1+((rec.reincarnation??0)>0?(rec.statBonusPct??0):0)/100))};
}

// ── 기술 칸(v0.15.8): 4칸 모두 바꿀 수 있음 — 처음엔 기본기·고유기·역할기·보조 속성 기술 ──
// rec.learned = ['@', 칸 키…]: 'basic'(기본기)·'sig'(고유기)·'role'(역할기)·'sub'(보조 속성)·배운 기술 키. 맨 앞 '@'가 없으면 예전 저장
//   (예전: 기본기·고유기는 고정이라 저장에 없고, 역할기·보조 속성·배운 기술만) → 기본기·고유기를 앞에 붙여 읽음
const FIXED_OLD=['basic','sig'];
export function slotKeys(r:Rules,rec:UnitRecord):string[]{
  const def=r.unitDef(rec.id)!,raw=rec.learned??[],hasSub=!!def.sub&&def.sub!==def.attr;
  let list:string[];
  if(raw[0]==='@')list=raw.slice(1);
  else{const old=raw.includes('role')||raw.includes('sub')?raw:[...raw,'role','sub'];list=[...FIXED_OLD,...old];}
  if(rec.inheritedFrom)list=['sig',...list];
  return [...new Set(list)].filter(k=>k==='basic'||k==='sig'||k==='role'||(k==='sub'?hasSub:!!r.data.learn.skills[k])).slice(0,r.data.learn.slots);
}
export function slotSkills(r:Rules,rec:UnitRecord):Skill[]{
  const def=r.unitDef(rec.id)!,playable=r.units.has(rec.id);
  return slotKeys(r,rec).map(k=>{
    const skill=k==='basic'?(playable?r.elementalSkill('basic',def.attr):undefined)??{...r.basicSkill(def.attr,def.basicCat),...(def.basic?{name:def.basic}:{})}
      :k==='sig'?(rec.inheritedFrom?r.unitDef(rec.inheritedFrom)?.signature??def.signature:def.signature):k==='role'?(playable&&def.role==='ATTACK'?r.elementalSkill('attack',def.attr):undefined)??r.roleSkill(def.role,def.attr)
      :k==='sub'?(playable?r.elementalSkill('sub',def.sub!):undefined)??r.subSkill(def.sub!):r.learnSkill(k,def.attr)!;
    return r.unitPower(k==='sig'&&rec.inheritedFrom?rec.inheritedFrom:rec.id,skill);
  });
}
const keyOfSkill=(id:string)=>id.startsWith('basic-')?'basic':id==='sig'?'sig':id.startsWith('role-')?'role':id.startsWith('sub-')?'sub':id.replace(/^learn-/,'');

// 씨앗으로 정해지는 난수(같은 씨앗이면 늘 같은 순서)
function seeded(seed:string):()=>number{let h=2166136261;for(const ch of seed)h=Math.imul(h^ch.charCodeAt(0),16777619);let t=h>>>0;
  return ()=>{t=(t+0x6d2b79f5)|0;let x=Math.imul(t^(t>>>15),t|1);x^=x+Math.imul(x^(x>>>7),x|61);return((x^(x>>>14))>>>0)/4294967296;};}
// 저장된 개체 번호·승진 단계에서 후보를 재현한다. 재접속으로 다시 뽑을 수 없다.
export function stableEvoChoices(r:Rules,rec:UnitRecord){
  return evoChoices(r,rec,seeded(`evolve:${rec.uid}:${rec.id}:${rec.stage}:${rec.evoAugs.join(',')}`));
}
// ── 타고나는 기술(v0.16.2): 똥폭탄(기본기) + 무작위 1~2개. 후보 = 역할기·보조 속성 기술·그 역할의 첫 배울 레벨 기술.
//    seed(개체 번호 등)로 정해서 다시 계산해도 같고, 같은 종류라도 개체마다 다름. 나머지 칸은 비어 있다가 레벨업·승진으로 채움
export function innateLearned(r:Rules,unitId:string,seed:string):string[]{
  const def=r.unitDef(unitId)!,rand=seeded(`innate:${unitId}:${seed}`),hasSub=!!def.sub&&def.sub!==def.attr,first=r.data.learn.levels[0];
  const pool=['role',...(hasSub?['sub']:[]),...r.learnables.filter(x=>x.level<=first&&(!x.roles||x.roles.includes(def.role))).map(x=>x.id)];
  for(let i=pool.length-1;i>0;i--){const j=Math.floor(rand()*(i+1));[pool[i],pool[j]]=[pool[j],pool[i]];}
  return ['@','basic',...pool.slice(0,rand()<0.5?1:2)];
}
// ── 승진 전용 기술(v0.16.2): 1단계 = 고유기(sig). 승진한 단계까지의 전용 기술 중 아직 칸에 없는 것은 언제든 배울 수 있음(배울 기회는 안 씀)
export const PROMO_KEYS=['sig'] as const;
export function promoSkills(r:Rules,rec:UnitRecord):Skill[]{
  const have=new Set(slotKeys(r,rec)),def=r.unitDef(rec.id)!;
  return PROMO_KEYS.filter((k,i)=>(rec.stage??0)>i&&!have.has(k)).map(()=>r.unitPower(rec.inheritedFrom??rec.id,rec.inheritedFrom?r.unitDef(rec.inheritedFrom)?.signature??def.signature:def.signature));
}
export function learnPromo(r:Rules,rec:UnitRecord,key:string,replace?:string){
  if(!promoSkills(r,rec).some(k=>k.id===key))throw new Error('지금 배울 수 있는 전용 기술이 아니에요');
  let list=slotKeys(r,rec);
  if(list.length>=r.data.learn.slots){
    const old=replace?keyOfSkill(replace):'';
    if(rec.inheritedFrom&&old==='sig')throw new Error('계승 고유기는 잊을 수 없어요. 다른 기술을 골라 주세요.');
    if(!old||!list.includes(old))throw new Error('칸이 꽉 찼어요. 잊을 기술을 골라 주세요');
    list=list.filter(x=>x!==old);
  }
  rec.learned=['@',...list,key];
  return rec;
}
// ── 레벨업 기술 배우기(진화와 별개): learn.levels에 도달할 때마다 1번 기회.
//    후보 = 그 레벨에 새로 열린 기술(역할에 맞고 아직 없는 것). 한 기회에 최대 perChance개까지 배움(다 배우거나 「그만 배우기」로 기회 끝)
export const learnChances=(r:Rules,rec:UnitRecord)=>Math.max(0,r.data.learn.levels.filter(l=>rec.level>=l).length-(rec.learnUsed??0));
export const learnLeft=(r:Rules,rec:UnitRecord)=>learnChances(r,rec)>0?Math.max(0,(r.data.learn.perChance??1)-(rec.learnPick??0)):0;
export function learnOffers(r:Rules,rec:UnitRecord):Skill[]{
  if(learnChances(r,rec)<=0)return [];
  const def=r.unitDef(rec.id)!,tier=r.data.learn.levels[rec.learnUsed??0],have=new Set(slotKeys(r,rec));
  return r.learnables.filter(s=>s.level===tier&&(!s.roles||s.roles.includes(def.role))&&!have.has(s.id)).slice(0,r.data.learn.offers).map(s=>r.unitPower(rec.id,r.learnSkill(s.id,def.attr)!));
}
// id: 배울 기술(null이면 이번 기회 끝내기), replace: 칸이 찼을 때 잊을 기술(4칸 중 아무거나, 기술 id)
export function learnSkill(r:Rules,rec:UnitRecord,id:string|null,replace?:string){
  if(learnChances(r,rec)<=0)throw new Error('지금은 배울 기회가 없어요');
  const done=()=>{rec.learnUsed=(rec.learnUsed??0)+1;rec.learnPick=0;};
  if(!id){done();return rec;}
  const key=id.replace(/^learn-/,'');
  if(!learnOffers(r,rec).some(s=>s.id===`learn-${key}`))throw new Error('후보에 없는 기술이에요');
  let list=slotKeys(r,rec);
  if(list.length>=r.data.learn.slots){
    const old=replace?keyOfSkill(replace):'';
    if(rec.inheritedFrom&&old==='sig')throw new Error('계승 고유기는 잊을 수 없어요. 다른 기술을 골라 주세요.');
    if(!old||!list.includes(old))throw new Error('칸이 꽉 찼어요. 잊을 기술을 골라 주세요');
    list=list.filter(x=>x!==old);
  }
  rec.learned=['@',...list,key];
  rec.learnPick=(rec.learnPick??0)+1;
  if(rec.learnPick>=(r.data.learn.perChance??1)||!learnOffers(r,rec).length)done();
  return rec;
}


