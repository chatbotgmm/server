// 턴제 배틀 시험판(/battle)용 전투 정보. 유닛마다 따로 적지 않고 등급·출신 동네로 자동 계산합니다.
// 출신 동네(타입): 조합 재료를 흔함까지 거슬러 올라가 가장 많이 들어간 동네. 둘째 동네가 30% 이상이면 복합 타입.
// 히든·최상위·특수함은 넣지 않습니다(공개 웹에 올라가는 정보라 스포일러 방지).
import type { Content, SynergyKey } from '../content.js';

export const battleTiers=['COMMON','UNCOMMON','SPECIAL','RARE','LEGEND'] as const;
type BattleTier=typeof battleTiers[number];
// 흔함 출발 원형 → 출신 동네. 기본(C1)과 거인·소인은 거의 모든 조합에 섞여 들어가서 비중을 낮춤
const origin:Record<string,[SynergyKey,number]>={C1:['BASIC',.3],C2:['GOLD',1],C3:['DARK',1],C4:['SEA',1],C5:['BLOSSOM',1],C6:['COSMOS',1],C7:['BASIC',.3],C8:['BASIC',.3],C9:['OUTFIT',1]};
// 계산 결과가 이름과 안 맞는 유닛은 여기서 직접 고칩니다
const overrides:Record<string,SynergyKey[]>={L19:['OUTFIT','SEA']};
const tierMult:Record<BattleTier,number>={COMMON:1,UNCOMMON:1.15,SPECIAL:1.32,RARE:1.52,LEGEND:1.75};
// 동네별 기본 능력치(체력·공격·방어·속도)
const baseStats:Record<SynergyKey,[number,number,number,number]>={
  BASIC:[110,20,14,10],GOLD:[100,18,19,8],DARK:[86,22,9,16],SEA:[118,19,12,9],BLOSSOM:[96,17,12,12],COSMOS:[86,25,9,12],OUTFIT:[104,18,15,11],
};

export interface BattleUnit {id:string;name:string;tier:BattleTier;types:SynergyKey[];hp:number;atk:number;def:number;spd:number}

// 같은 유닛은 언제나 같은 값이 나오도록 ID로 만든 작은 흔들림(±8%)
function jitter(id:string,salt:number){
  let h=2166136261^salt;
  for(const ch of id)h=Math.imul(h^ch.charCodeAt(0),16777619);
  return 0.92+((h>>>0)%1000)/1000*0.16;
}

export function battleRoster(content:Content):BattleUnit[]{
  const recipeOf=new Map(content.recipes.map(r=>[r.resultId,r.materials]));
  const memo=new Map<string,Partial<Record<SynergyKey,number>>>();
  const tally=(id:string):Partial<Record<SynergyKey,number>>=>{
    const hit=memo.get(id);if(hit)return hit;
    const t:Partial<Record<SynergyKey,number>>={};
    if(origin[id])t[origin[id][0]]=origin[id][1];
    else for(const m of recipeOf.get(id)??[])for(const [k,v] of Object.entries(tally(m)) as [SynergyKey,number][])t[k]=(t[k]??0)+v;
    memo.set(id,t);return t;
  };
  return content.characters.filter(c=>(battleTiers as readonly string[]).includes(c.rarity)).map(c=>{
    const ranked=(Object.entries(tally(c.id)) as [SynergyKey,number][]).sort((a,b)=>b[1]-a[1]);
    const total=ranked.reduce((s,[,v])=>s+v,0);
    const types=overrides[c.id]??(ranked.length?[ranked[0][0],...(ranked[1]&&ranked[1][1]>=total*0.3?[ranked[1][0]]:[])]:['BASIC' as SynergyKey]);
    const [hp,atk,def,spd]=baseStats[types[0]];const m=tierMult[c.rarity as BattleTier];
    return {id:c.id,name:c.name,tier:c.rarity as BattleTier,types,
      hp:Math.round(hp*m*jitter(c.id,1)),atk:Math.round(atk*m*jitter(c.id,2)),def:Math.round(def*m*jitter(c.id,3)),spd:Math.round(spd*jitter(c.id,4)*(1+(m-1)*0.3))};
  });
}
