// 성장: 레벨·경험치·진화(승진) 3단계·진화 증강체(직무 표창). 수치는 data/battle.yaml의 growth·evoAugments
import type { EvoAugment, Passive, Rules, TopTier } from './rules.js';

export type Stage=0|1|2|3;
export const stageName=['원형','1단계','2단계','3단계'] as const;
// 저장되는 유닛 한 마리의 상태(스카우트하면 이 상태 그대로 복사)
export interface UnitRecord {uid:string;id:string;name:string;tier:TopTier;level:number;xp:number;stage:Stage;evoAugs:string[]}

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
  const role=r.units.get(rec.id)!.role;
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
// 같은 유닛은 언제나 같은 개성(±5%)
function jitter(id:string,salt:number){let h=2166136261^salt;for(const ch of id)h=Math.imul(h^ch.charCodeAt(0),16777619);return 0.95+((h>>>0)%1000)/1000*0.1;}
export function statsOf(r:Rules,rec:UnitRecord){
  const def=r.units.get(rec.id);if(!def)throw new Error(`전투 유닛이 아님: ${rec.id}`);
  const b=r.data.roles[def.role],t=r.data.tiers[rec.tier],scale=levelScale(r,rec),p=passiveOf(r,rec);
  const lv=1+r.data.growth.levelGrowth/100*(rec.level-1),st=r.data.growth.stageMult[rec.stage];
  const f=(v:number,salt:number,pct=0)=>Math.round(v*t*scale*jitter(rec.id,salt)*(1+pct/100));
  return {hp:f(b.hp,1,p.hpPct),atk:f(b.atk,2,p.atkPct),def:f(b.def,3,p.defPct),spd:Math.round(b.spd*jitter(rec.id,4)*(1+(lv-1)*0.25)*(1+(st-1)*0.5)*(1+(p.spdPct??0)/100))};
}
