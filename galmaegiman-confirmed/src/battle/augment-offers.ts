// 순수 증강체 배정: 난수 공급원·회차 상태는 호출한 서버 엔진이 저장한다.
export type AugmentTier = 'silver' | 'gold' | 'prismatic';
export interface OfferEntry { id:string; tier:AugmentTier }
export interface OfferRound { tier:AugmentTier; offers:[string[],string[]]; seen:string[]; rerolled:[boolean[],boolean[]] }
export type OfferResult = {kind:'offered'; round:OfferRound} | {kind:'exhausted'};
export const AUGMENT_TIERS:readonly AugmentTier[]=['silver','gold','prismatic'];
type Pair<T>=[T,T];
const unique=(xs:readonly string[])=>[...new Set(xs)].sort();
function pick<T>(pool:T[],rng:()=>number):T {
  const n=rng();if(!Number.isFinite(n)||n<0||n>=1)throw new Error('난수는 0 이상 1 미만이어야 합니다.');
  return pool.splice(Math.floor(n*pool.length),1)[0];
}
function canDeal(p:Pair<string[]>){return p[0].length>=3&&p[1].length>=3&&new Set([...p[0],...p[1]]).size>=6;}
export function createOfferRound(catalog:readonly OfferEntry[],owned:Pair<readonly string[]>,eligible:Pair<readonly string[]>,weights:Record<AugmentTier,number>,rng:()=>number,first:0|1=0):OfferResult {
  if(catalog.some(a=>!a.id||!AUGMENT_TIERS.includes(a.tier)))throw new Error('증강체 ID/등급 오류');
  if(new Set(catalog.map(x=>x.id)).size!==catalog.length)throw new Error('중복 증강체 ID');
  for(const t of AUGMENT_TIERS)if(!Number.isFinite(weights[t])||weights[t]<0)throw new Error('등급 가중치 오류');
  const blocked=new Set(owned.flat()), allow=eligible.map(xs=>new Set(xs));
  const pools=new Map(AUGMENT_TIERS.map(t=>[t,[0,1].map(side=>unique(catalog.filter(a=>a.tier===t&&!blocked.has(a.id)&&allow[side].has(a.id)).map(a=>a.id))) as Pair<string[]>]));
  const tiers=AUGMENT_TIERS.filter(t=>weights[t]>0&&canDeal(pools.get(t)!));
  if(!tiers.length)return {kind:'exhausted'};
  const n=rng();if(!Number.isFinite(n)||n<0||n>=1)throw new Error('난수 오류');
  let cursor=n*tiers.reduce((sum,t)=>sum+weights[t],0),tier=tiers.at(-1)!;
  for(const t of tiers){cursor-=weights[t];if(cursor<0){tier=t;break;}}
  const p=pools.get(tier)!,other=first===0?1:0,out:Pair<string[]>=[[],[]];
  while(out[first].length<3){
    // 상대에게 3개가 남는 선택만 허용한다. 양쪽 후보 집합의 크기 조건으로 완전 배정 가능.
    const candidates=p[first].filter(id=>!p[other].includes(id)||p[other].length>3);
    const id=pick(candidates,rng);out[first].push(id);
    p[first]=p[first].filter(x=>x!==id);p[other]=p[other].filter(x=>x!==id);
  }
  while(out[other].length<3)out[other].push(pick(p[other],rng));
  return {kind:'offered',round:{tier,offers:out,seen:out.flat(),rerolled:[[false,false,false],[false,false,false]]}};
}
export function rerollOffer(round:OfferRound,side:0|1,slot:number,catalog:readonly OfferEntry[],owned:Pair<readonly string[]>,eligible:readonly string[],rng:()=>number):{kind:'rerolled';id:string}|{kind:'exhausted'} {
  if(!Number.isInteger(slot)||slot<0||slot>=3||!round.offers[side][slot])throw new Error('다시 뽑을 증강체가 없음');
  if(round.rerolled[side][slot])throw new Error('이 칸은 이미 다시 뽑았음');
  const blocked=new Set([...owned.flat(),...round.seen,...round.offers.flat()]),allow=new Set(eligible);
  const pool=unique(catalog.filter(a=>a.tier===round.tier&&!blocked.has(a.id)&&allow.has(a.id)).map(a=>a.id));
  if(!pool.length)return {kind:'exhausted'};
  const id=pick(pool,rng);round.offers[side][slot]=id;round.seen.push(id);round.rerolled[side][slot]=true;
  return {kind:'rerolled',id};
}
