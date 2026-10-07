// 외근(스토리 클리어 후 개인 활동): 내 레벨대의 갈매미를 만나 대결에서 이기면 스카우트를 시도.
// 스카우트에 성공하면 그 갈매미를 레벨·경험치·진화 단계·진화 증강체까지 그대로 데려옴.
// 수치 근거와 기대값 표: design/BATTLE-SYSTEM.md (scripts/battle-balance.ts로 다시 계산)
import type { TopTier } from './units.js';
import { evolveLevel, type Stage, type UnitRecord } from './growth.js';

export const scoutRules={
  freeOutingsPerDay:3,          // 하루 무료 외근
  extraOutingPeanut:1,          // 추가 외근 1번 = 땅콩 1개
  maxExtraPerDay:10,
  levelBand:3,                  // 기준 레벨 ±3
  // 만나는 등급 비율(%)
  encounter:{LIMITED:45,TRANSCEND:30,ETERNAL:17,IMMORTAL:8} as Record<TopTier,number>,
  // 대결에서 이긴 뒤 스카우트 기본 성공률(%)
  base:{LIMITED:35,TRANSCEND:22,ETERNAL:12,IMMORTAL:6} as Record<TopTier,number>,
  // 진화 단계가 높을수록 데려오기 어려움
  stageFactor:[1,0.85,0.7,0.55] as const,
  // 명함(땅콩)을 건네면 성공률 ×(1 + 0.5 × 개수), 최대 3개
  cardBonus:0.5,maxCards:3,
  cap:90,
  // 천장: 같은 등급에서 연속 실패하면 N번째 시도는 반드시 성공
  pity:{LIMITED:4,TRANSCEND:6,ETERNAL:10,IMMORTAL:16} as Record<TopTier,number>,
};

// 기준 레벨: 내 전투 유닛 중 레벨 높은 3마리의 평균(내림)
export function referenceLevel(team:UnitRecord[]){
  const top=[...team].map(u=>u.level).sort((a,b)=>b-a).slice(0,3);
  return top.length?Math.floor(top.reduce((s,v)=>s+v,0)/top.length):1;
}
export function levelRange(ref:number){return [Math.max(1,ref-scoutRules.levelBand),Math.min(60,ref+scoutRules.levelBand)] as const;}
// 그 레벨에서 가능한 최고 진화 단계(스토리 조건은 무시: 다른 유저의 유닛이면 이미 진화해 있을 수 있음)
export const maxStageAt=(level:number)=>evolveLevel.filter(l=>level>=l).length as Stage;

// 성공률(%) = 기본 × 진화 단계 보정 × (1 + 0.5 × 명함), 최대 90. 천장에 닿으면 100
export function scoutChance(tier:TopTier,stage:Stage,cards:number,failsSoFar:number){
  if(failsSoFar+1>=scoutRules.pity[tier])return 100;
  const k=Math.max(0,Math.min(scoutRules.maxCards,Math.floor(cards)));
  return Math.min(scoutRules.cap,scoutRules.base[tier]*scoutRules.stageFactor[stage]*(1+scoutRules.cardBonus*k));
}
// 성공까지 기대 시도 수(천장 포함): Σ_{i=0}^{N-1} (1-p)^i
export function expectedAttempts(p:number,pityAt:number){
  const q=1-p/100;let e=0;for(let i=0;i<pityAt;i++)e+=q**i;return e;
}

// 스카우트 성공: 원본 상태를 그대로 복사(새 고유번호만 붙임). 원래 주인의 유닛은 그대로 남음
export function scoutCopy(src:UnitRecord,newUid:string):UnitRecord{
  return {uid:newUid,id:src.id,name:src.name,tier:src.tier,level:src.level,xp:src.xp,stage:src.stage,evoAugs:[...src.evoAugs]};
}
