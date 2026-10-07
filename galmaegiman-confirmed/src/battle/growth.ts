// 성장: 레벨·경험치·진화(승진) 3단계·진화 증강체(직무 표창). 서버와 앱이 같이 씀.
import { roleBase, tierMult, unitDefMap, type Role, type TopTier } from './units.js';

export const MAX_LEVEL=60;
// 진화(승진) 단계: 0=원형, 1·2·3단계. 레벨 조건 + 스토리 조건(1부·2부·3부 클리어)은 서버가 함께 확인
export const evolveLevel=[20,35,50] as const;
export const stageMult=[1,1.12,1.25,1.4] as const;
export const stageName=['원형','1단계','2단계','3단계'] as const;
export type Stage=0|1|2|3;

// 저장되는 유닛 한 마리의 상태(스카우트하면 이 상태 그대로 복사)
export interface UnitRecord {uid:string;id:string;name:string;tier:TopTier;level:number;xp:number;stage:Stage;evoAugs:string[]}

// 다음 레벨까지 필요한 경험치: 20 × 레벨^1.5 (1→2: 20, 19→20: 1,656, 59→60: 9,064)
export const xpToNext=(level:number)=>Math.round(20*level**1.5);
// 대결 1번 경험치(나간 갈매미마다): 이기면 60 × 상대 평균 레벨, 지면 20 ×
// 예: 레벨 1→20 누적 13,427 → 상대 평균 레벨 10이면 약 22승, 1→50 누적 137,901 → 레벨 45 상대로 약 51승
export const battleXp=(enemyAvgLevel:number,won:boolean)=>Math.round((won?60:20)*Math.max(1,enemyAvgLevel));
export function addXp(rec:UnitRecord,amount:number){
  rec.xp+=Math.max(0,Math.floor(amount));
  while(rec.level<MAX_LEVEL&&rec.xp>=xpToNext(rec.level)){rec.xp-=xpToNext(rec.level);rec.level++;}
  if(rec.level>=MAX_LEVEL)rec.xp=0;
  return rec;
}
export function canEvolve(rec:UnitRecord,storyCleared:number){
  return rec.stage<3&&rec.level>=(evolveLevel as readonly number[])[rec.stage]&&storyCleared>rec.stage;
}

// 진화 증강체(유닛 전용, 영구). 진화할 때 역할 풀 + 공통 풀에서 3개 중 1개
export interface Passive {hpPct?:number;atkPct?:number;defPct?:number;spdPct?:number;pierce?:number;execute?:number;critPlus?:number;opener?:number;
  lifesteal?:number;reflect?:number;cut?:number;regen?:number;lastStand?:boolean;statusPlus?:number;statusResist?:number;sigCd?:number;handoff?:number;durationPlus?:number}
export interface EvoAugment {id:string;name:string;desc:string;roles:Role[]|'ANY';passive:Passive}
export const evoAugments:EvoAugment[]=[
  {id:'pierce',name:'관통 표창',desc:'상대 방어 25% 무시',roles:['ATTACK'],passive:{pierce:25}},
  {id:'execute',name:'마무리 표창',desc:'체력 30% 이하인 상대에게 피해 +40%',roles:['ATTACK'],passive:{execute:40}},
  {id:'crit',name:'급소 표창',desc:'급소 확률 +15%p',roles:['ATTACK'],passive:{critPlus:15}},
  {id:'opener',name:'선봉 표창',desc:'전투 첫 공격 피해 +50%',roles:['ATTACK'],passive:{opener:50}},
  {id:'lifesteal',name:'야근 식대 표창',desc:'준 피해의 20% 회복',roles:['ATTACK'],passive:{lifesteal:20}},
  {id:'reflect',name:'반사 표창',desc:'받은 피해의 20%를 되돌려 줌',roles:['DEFENSE'],passive:{reflect:20}},
  {id:'cut',name:'철갑 표창',desc:'받는 피해 -12%',roles:['DEFENSE'],passive:{cut:12}},
  {id:'regen',name:'근속 표창',desc:'턴이 끝날 때 체력 5% 회복',roles:['DEFENSE'],passive:{regen:5}},
  {id:'laststand',name:'불굴 표창',desc:'전투당 한 번, 쓰러질 피해를 체력 1로 버팀',roles:['DEFENSE'],passive:{lastStand:true}},
  {id:'resist',name:'정신력 표창',desc:'상태 이상에 걸릴 확률 절반',roles:['DEFENSE','UTILITY'],passive:{statusResist:50}},
  {id:'status',name:'전문가 표창',desc:'상태 이상 확률 +20%p',roles:['UTILITY'],passive:{statusPlus:20}},
  {id:'duration',name:'꼼꼼 표창',desc:'주는 강화·약화가 1턴 더 감',roles:['UTILITY'],passive:{durationPlus:1}},
  {id:'sigcd',name:'능률 표창',desc:'고유기 재사용 1턴 줄어듦',roles:['UTILITY','ATTACK'],passive:{sigCd:1}},
  {id:'handoff',name:'인수인계 표창',desc:'교체되어 나갈 때 들어오는 아군 체력 15% 회복',roles:['UTILITY','DEFENSE'],passive:{handoff:15}},
  {id:'hp',name:'체력 표창',desc:'최대 체력 +12%',roles:'ANY',passive:{hpPct:12}},
  {id:'atk',name:'힘 표창',desc:'공격 +10%',roles:'ANY',passive:{atkPct:10}},
  {id:'def',name:'맷집 표창',desc:'방어 +10%',roles:'ANY',passive:{defPct:10}},
  {id:'spd',name:'출근 속도 표창',desc:'속도 +10%',roles:'ANY',passive:{spdPct:10}},
];
export const evoAugmentMap=new Map(evoAugments.map(a=>[a.id,a]));
export function evoChoices(rec:UnitRecord,rand:()=>number){
  const role=unitDefMap.get(rec.id)!.role;
  const pool=evoAugments.filter(a=>(a.roles==='ANY'||a.roles.includes(role))&&!rec.evoAugs.includes(a.id));
  const out:EvoAugment[]=[];
  while(out.length<3&&pool.length)out.push(pool.splice(Math.floor(rand()*pool.length),1)[0]);
  return out;
}
export function passiveOf(rec:UnitRecord):Passive{
  const p:Passive={};
  for(const id of rec.evoAugs){const a=evoAugmentMap.get(id);if(!a)continue;
    for(const [k,v] of Object.entries(a.passive) as [keyof Passive,number|boolean][])(p as Record<string,number|boolean>)[k]=typeof v==='boolean'?v:((p[k] as number|undefined)??0)+v;}
  return p;
}

// 같은 유닛은 언제나 같은 개성(±5%)
function jitter(id:string,salt:number){let h=2166136261^salt;for(const ch of id)h=Math.imul(h^ch.charCodeAt(0),16777619);return 0.95+((h>>>0)%1000)/1000*0.1;}
// 레벨·진화로 커지는 배율(체력·공격·방어와 피해에 같이 씀)
export const levelScale=(rec:{level:number;stage:Stage})=>(1+0.04*(rec.level-1))*stageMult[rec.stage];
export function statsOf(rec:UnitRecord){
  const def=unitDefMap.get(rec.id);if(!def)throw new Error(`전투 유닛이 아님: ${rec.id}`);
  const b=roleBase[def.role],t=tierMult[rec.tier],lv=1+0.04*(rec.level-1),st=stageMult[rec.stage],p=passiveOf(rec);
  const f=(v:number,salt:number,pct=0)=>Math.round(v*t*lv*st*jitter(rec.id,salt)*(1+pct/100));
  return {hp:f(b.hp,1,p.hpPct),atk:f(b.atk,2,p.atkPct),def:f(b.def,3,p.defPct),spd:Math.round(b.spd*jitter(rec.id,4)*(1+(lv-1)*0.25)*(1+(st-1)*0.5)*(1+(p.spdPct??0)/100))};
}
