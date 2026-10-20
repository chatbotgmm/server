// Versioned opt-in catalog. Importing it never activates V2 in an existing battle.
// V1 data/battle.yaml and its 24 IDs remain unchanged. Numeric semantics are implemented by ID.
export type CatalogTier='silver'|'gold'|'prismatic';
export interface CatalogAugment {id:string;tier:CatalogTier;version:2;name:string;desc:string;p:Record<string,number>}
export interface AugmentCatalogV2 {version:2;augments:CatalogAugment[]}
export const augmentCatalogV2:AugmentCatalogV2={
  "version": 2,
  "augments": [
    {
      "id": "first",
      "tier": "silver",
      "version": 2,
      "name": "출근 도장",
      "desc": "다음 기술의 우선도 +1",
      "p": {
        "priority": 1,
        "uses": 1
      }
    },
    {
      "id": "bulwark",
      "tier": "silver",
      "version": 2,
      "name": "철벽",
      "desc": "선두가 2턴 동안 피해 12% 감소",
      "p": {
        "pct": 12,
        "turns": 2
      }
    },
    {
      "id": "firstaid",
      "tier": "silver",
      "version": 2,
      "name": "응급처치",
      "desc": "살아 있는 아군 모두 체력 10% 회복",
      "p": {
        "pct": 10
      }
    },
    {
      "id": "sharp",
      "tier": "silver",
      "version": 2,
      "name": "매의 눈",
      "desc": "다음 3번 공격 첫 타 급소 확률 +10%p",
      "p": {
        "pct": 10,
        "uses": 3,
        "firstHitOnly": 1
      }
    },
    {
      "id": "swift",
      "tier": "silver",
      "version": 2,
      "name": "칼퇴 준비",
      "desc": "3턴 동안 아군 속도 +10%",
      "p": {
        "pct": 10,
        "turns": 3
      }
    },
    {
      "id": "thick",
      "tier": "silver",
      "version": 2,
      "name": "두꺼운 깃털",
      "desc": "3턴 동안 아군 물리·마법 방어 +10%",
      "p": {
        "pct": 10,
        "turns": 3
      }
    },
    {
      "id": "overtime",
      "tier": "silver",
      "version": 2,
      "name": "야근 수당",
      "desc": "다음 3번 공격 위력 +8%",
      "p": {
        "pct": 8,
        "uses": 3,
        "powerBonus": 1
      }
    },
    {
      "id": "drain",
      "tier": "silver",
      "version": 2,
      "name": "회식비 정산",
      "desc": "다음 3번 적중 실제 피해의 12% 회복",
      "p": {
        "pct": 12,
        "uses": 3,
        "maxHpCapPct": 4
      }
    },
    {
      "id": "thorns",
      "tier": "silver",
      "version": 2,
      "name": "가시 깃털",
      "desc": "다음 3번 피격 실제 피해의 10% 반사",
      "p": {
        "pct": 10,
        "uses": 3,
        "maxHpCapPct": 4,
        "actualDamage": 1
      }
    },
    {
      "id": "pierce",
      "tier": "silver",
      "version": 2,
      "name": "송곳 부리",
      "desc": "다음 3번 공격 방어 10% 관통",
      "p": {
        "pct": 10,
        "uses": 3
      }
    },
    {
      "id": "lunch",
      "tier": "silver",
      "version": 2,
      "name": "점심시간",
      "desc": "다음 3턴 종료에 선두 체력 3% 회복",
      "p": {
        "pct": 3,
        "turns": 3
      }
    },
    {
      "id": "learn",
      "tier": "silver",
      "version": 2,
      "name": "상성 학습",
      "desc": "3턴 동안 같은 속성 재피격 피해 −8%, 최대16%",
      "p": {
        "step": 8,
        "max": 16,
        "turns": 3
      }
    },
    {
      "id": "strip",
      "tier": "silver",
      "version": 2,
      "name": "기세 꺾기",
      "desc": "상대 선두 강화 하나를 제거",
      "p": {
        "pct": 0,
        "turns": 0,
        "removeCount": 1
      }
    },
    {
      "id": "rinse",
      "tier": "silver",
      "version": 2,
      "name": "긴급 세척",
      "desc": "선두의 상태 이상을 모두 해제",
      "p": {}
    },
    {
      "id": "resetline",
      "tier": "silver",
      "version": 2,
      "name": "전열 정비",
      "desc": "살아 있는 아군의 능력치 약화 제거",
      "p": {}
    },
    {
      "id": "handoffguard",
      "tier": "silver",
      "version": 2,
      "name": "안전 인계",
      "desc": "다음 두 번 교체 후 피해 12% 감소",
      "p": {
        "guardPct": 12
      }
    },
    {
      "id": "relief",
      "tier": "silver",
      "version": 2,
      "name": "구호 배달",
      "desc": "다음 두 번 교체 때 출전 아군 8% 회복",
      "p": {
        "healMaxHpPct": 8
      }
    },
    {
      "id": "aim",
      "tier": "silver",
      "version": 2,
      "name": "정밀 조준",
      "desc": "다음 공격 첫 타 급소 확률 +20%p",
      "p": {
        "critChancePlus": 20
      }
    },
    {
      "id": "response",
      "tier": "silver",
      "version": 2,
      "name": "후수 응수",
      "desc": "상대 기술 후 공격하면 위력 +12%",
      "p": {
        "powerBonusPct": 12
      }
    },
    {
      "id": "exploit",
      "tier": "silver",
      "version": 2,
      "name": "빈틈 공략",
      "desc": "약화된 적에게 방어 10% 추가 관통",
      "p": {
        "piercePct": 10
      }
    },
    {
      "id": "reverse",
      "tier": "gold",
      "version": 2,
      "name": "역상성",
      "desc": "이번 전투 내 공격 강함과 약함을 뒤집음",
      "p": {}
    },
    {
      "id": "weakguard",
      "tier": "gold",
      "version": 2,
      "name": "약점 보강",
      "desc": "받는 강한 상성 피해를 보통 배율로",
      "p": {}
    },
    {
      "id": "focus",
      "tier": "gold",
      "version": 2,
      "name": "집중 공략",
      "desc": "강한 상성 공격 배율을 1.9로",
      "p": {
        "value": 1.9
      }
    },
    {
      "id": "neutral",
      "tier": "gold",
      "version": 2,
      "name": "무속성 지대",
      "desc": "3턴 동안 양쪽 모든 상성 1배",
      "p": {
        "turns": 3
      }
    },
    {
      "id": "camo",
      "tier": "gold",
      "version": 2,
      "name": "보호색",
      "desc": "선두 속성을 상대에게 강한 속성으로 변경",
      "p": {}
    },
    {
      "id": "dual",
      "tier": "gold",
      "version": 2,
      "name": "이중 속성",
      "desc": "선두 기본·역할 공격에 상대 약점 속성 추가",
      "p": {}
    },
    {
      "id": "counter",
      "tier": "gold",
      "version": 2,
      "name": "역습 태세",
      "desc": "약한 상성 공격을 맞고 생존하면 위력40 반격",
      "p": {
        "power": 40
      }
    },
    {
      "id": "freeswitch",
      "tier": "gold",
      "version": 2,
      "name": "인사 발령",
      "desc": "교체 후 같은 턴 기술 사용 가능",
      "p": {}
    },
    {
      "id": "lastone",
      "tier": "gold",
      "version": 2,
      "name": "마지막 출근",
      "desc": "아군이 하나면 공격·방어·속도 +30%",
      "p": {
        "pct": 30
      }
    },
    {
      "id": "rustle",
      "tier": "gold",
      "version": 2,
      "name": "바스락",
      "desc": "상대 선두 수면1턴, 면역이면 제외",
      "p": {}
    },
    {
      "id": "peanut",
      "tier": "gold",
      "version": 2,
      "name": "땅콩 기세",
      "desc": "갈매미맨 공격 +30%, 받는 피해 +15%",
      "p": {
        "atk": 30,
        "taken": 15
      }
    },
    {
      "id": "relaytype",
      "tier": "gold",
      "version": 2,
      "name": "속성 릴레이",
      "desc": "다른 속성을 이어 쓰면 공격 +25%",
      "p": {
        "powerBonusPct": 25
      }
    },
    {
      "id": "breach",
      "tier": "gold",
      "version": 2,
      "name": "파훼 예고",
      "desc": "보조 기술 뒤 다음 공격은 보호막 관통",
      "p": {}
    },
    {
      "id": "triage",
      "tier": "gold",
      "version": 2,
      "name": "응급 흡수",
      "desc": "체력 절반 이하 공격 시 실제 피해 25% 회복",
      "p": {
        "actualDamageHealPct": 25,
        "maxHpHealCapPct": 10
      }
    },
    {
      "id": "rescue",
      "tier": "gold",
      "version": 2,
      "name": "구조 신호",
      "desc": "피격으로 체력 30% 아래가 되면 상태 해제",
      "p": {}
    },
    {
      "id": "firstshell",
      "tier": "gold",
      "version": 2,
      "name": "단단한 첫발",
      "desc": "각 아군 첫 피격 기술 피해 30% 감소",
      "p": {
        "guardPct": 30
      }
    },
    {
      "id": "chase",
      "tier": "gold",
      "version": 2,
      "name": "추격 표식",
      "desc": "적이 교체한 턴 공격 위력 +30%",
      "p": {
        "powerBonusPct": 30
      }
    },
    {
      "id": "baton",
      "tier": "gold",
      "version": 2,
      "name": "마지막 전달",
      "desc": "첫 아군 전투불가 후 다음 출전 피해 −35%",
      "p": {
        "guardPct": 35
      }
    },
    {
      "id": "overflowguard",
      "tier": "gold",
      "version": 2,
      "name": "넘치는 활력",
      "desc": "초과회복을 최대체력20%까지 흡수쉴드로 전환",
      "p": {
        "overflowToShieldPct": 100,
        "shieldMaxHpCapPct": 20,
        "shieldTurns": 2,
        "conversions": 3
      }
    },
    {
      "id": "typebridge",
      "tier": "gold",
      "version": 2,
      "name": "상성 다리",
      "desc": "다음 2번 불리한 공격 배율을 보통으로",
      "p": {
        "floorOutgoingTypeMultiplier": 1
      }
    },
    {
      "id": "redline",
      "tier": "prismatic",
      "version": 2,
      "name": "한계 돌파",
      "desc": "체력을 걸고 다음 공격 위력 +65%",
      "p": {
        "powerBonusPct": 65,
        "selfMaxHpCostPct": 20
      }
    },
    {
      "id": "underdog",
      "tier": "prismatic",
      "version": 2,
      "name": "거인 사냥",
      "desc": "상대가 10레벨 높으면 실제 피해 35% 추가",
      "p": {
        "actualDamageBonusPct": 35,
        "extraDamageFoeMaxHpCapPct": 18,
        "selfMaxHpCostPct": 8
      }
    },
    {
      "id": "glasswing",
      "tier": "prismatic",
      "version": 2,
      "name": "유리 날개",
      "desc": "두 공격 위력 +50%, 그동안 받는 피해 증가",
      "p": {
        "powerBonusPct": 50,
        "incomingDamagePlusPct": 25
      }
    },
    {
      "id": "lonecharge",
      "tier": "prismatic",
      "version": 2,
      "name": "배수의 진",
      "desc": "선두가 교체를 포기하고 두 공격 강화",
      "p": {
        "powerBonusPct": 45,
        "piercePct": 20
      }
    },
    {
      "id": "equalfield",
      "tier": "prismatic",
      "version": 2,
      "name": "평준화 결투",
      "desc": "한 턴 낮은 레벨 쪽의 공격 계산을 보정",
      "p": {
        "effectiveAttackLevelGapCap": 15,
        "outgoingTypeMultiplier": 1
      }
    },
    {
      "id": "blindspot",
      "tier": "prismatic",
      "version": 2,
      "name": "사각 돌파",
      "desc": "불리한 공격 한 번을 2배 상성으로",
      "p": {
        "replaceTypeMultiplier": 2,
        "selfMaxHpCostPct": 12
      }
    },
    {
      "id": "doubleedge",
      "tier": "prismatic",
      "version": 2,
      "name": "양날 계약",
      "desc": "다음 적중에 실제 피해 30% 추가, 자신도 반동",
      "p": {
        "extraActualDamagePct": 30,
        "extraFoeMaxHpCapPct": 18,
        "recoilOfExtraPct": 50
      }
    },
    {
      "id": "lastmatch",
      "tier": "prismatic",
      "version": 2,
      "name": "최후의 불꽃",
      "desc": "마지막 아군 저체력 공격 한 번 +80%",
      "p": {
        "powerBonusPct": 80,
        "selfMaxHpCostPct": 10
      }
    },
    {
      "id": "bloodrelay",
      "tier": "prismatic",
      "version": 2,
      "name": "혈맹 인계",
      "desc": "교체하는 아군 체력을 써서 다음 공격 강화",
      "p": {
        "outgoingMaxHpCostPct": 20,
        "incomingNextPowerBonusPct": 60
      }
    },
    {
      "id": "quietstorm",
      "tier": "prismatic",
      "version": 2,
      "name": "고요한 폭풍",
      "desc": "보조 기술 두 번 뒤 공격 한 번 +90%",
      "p": {
        "nextPowerBonusPct": 90,
        "requiredDistinctUtilityCasts": 2
      }
    },
    {
      "id": "rainbow",
      "tier": "prismatic",
      "version": 2,
      "name": "삼색 연쇄",
      "desc": "세 속성으로 연속 공격하면 세 번째 +75%",
      "p": {
        "thirdPowerBonusPct": 75,
        "distinctTypes": 3
      }
    },
    {
      "id": "crackedarmor",
      "tier": "prismatic",
      "version": 2,
      "name": "균열 갑옷",
      "desc": "첫 피격을 줄이고 이후 방어가 약해짐",
      "p": {
        "guardPct": 60,
        "afterwardDefensePenaltyPct": 25
      }
    },
    {
      "id": "mortgage",
      "tier": "prismatic",
      "version": 2,
      "name": "빌린 생명",
      "desc": "즉시 선두 35% 회복, 두 턴 뒤 대가",
      "p": {
        "healMaxHpPct": 35,
        "deferredMaxHpCostPct": 25
      }
    },
    {
      "id": "coldbargain",
      "tier": "prismatic",
      "version": 2,
      "name": "냉혹한 거래",
      "desc": "다음 공격은 회복 효과를 포기하고 위력 +55%",
      "p": {
        "powerBonusPct": 55
      }
    },
    {
      "id": "shatterprice",
      "tier": "prismatic",
      "version": 2,
      "name": "방벽 거래",
      "desc": "자기 강화 전부를 버리고 방어 관통 공격",
      "p": {
        "piercePct": 65
      }
    },
    {
      "id": "hostileweather",
      "tier": "prismatic",
      "version": 2,
      "name": "폭풍 계약",
      "desc": "두 턴 상성 공격을 강화하되 양쪽이 받음",
      "p": {
        "bothSidesStrongMultiplier": 2.2,
        "bothSidesWeakMultiplier": 0.6
      }
    },
    {
      "id": "slowhammer",
      "tier": "prismatic",
      "version": 2,
      "name": "느린 철퇴",
      "desc": "다음 공격을 후공으로 미루고 위력 +70%",
      "p": {
        "forcePriority": -3,
        "powerBonusPct": 70
      }
    },
    {
      "id": "siegebreak",
      "tier": "prismatic",
      "version": 2,
      "name": "공성 돌파",
      "desc": "고체력 적에게 한 번 큰 피해, 다음 턴 취약",
      "p": {
        "powerBonusPct": 65,
        "nextTurnIncomingPlusPct": 30
      }
    },
    {
      "id": "sharedburden",
      "tier": "prismatic",
      "version": 2,
      "name": "공동 부담",
      "desc": "대기 아군의 체력을 걸고 다음 공격 +60%",
      "p": {
        "benchMaxHpCostPct": 10,
        "powerBonusPct": 60
      }
    },
    {
      "id": "sealedduel",
      "tier": "prismatic",
      "version": 2,
      "name": "봉인 결투",
      "desc": "다음 공격에서 쌍방 증강 방어와 흡혈을 차단",
      "p": {}
    }
  ]
};
// 기본 스키마를 외부 변경으로부터 보호해 ID·등급·허용 키 검증을 고정해요.
for(const a of augmentCatalogV2.augments){Object.freeze(a.p);Object.freeze(a);}
Object.freeze(augmentCatalogV2.augments);Object.freeze(augmentCatalogV2);
export const battleAugIdsV2:readonly string[]=Object.freeze(augmentCatalogV2.augments.map(a=>a.id));
// Readable numeric restrictions: no unknown parameters; percentages 0–100,
// priorities −3–3, multipliers 0.1–3, turn/count/level parameters bounded integers.
export function parameterRange(key:string,id?:string):{min:number;max:number;integer?:boolean}{
  if(id==='triage'&&key==='maxHpHealCapPct')return {min:0,max:15};
  if(id==='strip'&&(key==='turns'||key==='pct'))return {min:0,max:0,integer:true};
  if(['firstHitOnly','powerBonus','actualDamage'].includes(key))return {min:0,max:1,integer:true};
  if(/priority/i.test(key))return {min:-3,max:3,integer:true};
  if(/multiplier|^value$/i.test(key))return {min:0.1,max:3};
  if(/Pct$/.test(key))return {min:0,max:100};
  if(/turn|casts|distinctTypes|^uses$|^removeCount$|^conversions$/i.test(key))return {min:1,max:20,integer:true};
  if(/level/i.test(key))return {min:1,max:200,integer:true};
  if(/^power$/i.test(key))return {min:1,max:400,integer:true};
  return {min:0,max:100};
}
function object(value:unknown,label:string):Record<string,unknown>{
  if(!value||typeof value!=='object'||Array.isArray(value))throw new Error(`${label}: 객체가 필요해요`);
  return value as Record<string,unknown>;
}
function keys(value:Record<string,unknown>,allowed:string[],label:string){
  if(Object.keys(value).some(k=>!allowed.includes(k)))throw new Error(`${label}: 알 수 없는 필드`);
}
export function parseAugmentCatalogV2(input:unknown):AugmentCatalogV2{
  const data=object(input,'카탈로그');keys(data,['version','augments'],'카탈로그');
  if(data.version!==2||!Array.isArray(data.augments)||data.augments.length!==60)throw new Error('V2 증강은 버전 2, 정확히 60개여야 해요');
  const seen=new Set<string>();const counts={silver:0,gold:0,prismatic:0};
  const augments=data.augments.map((raw,index):CatalogAugment=>{
    const a=object(raw,`증강 ${index}`);keys(a,['id','tier','version','name','desc','p'],'증강');
    const template=augmentCatalogV2.augments.find(t=>t.id===a.id);
    if(!template||seen.has(template.id))throw new Error('알 수 없거나 중복된 증강 ID');seen.add(template.id);
    if(a.version!==2||a.tier!==template.tier)throw new Error(`${template.id}: 버전 또는 등급 오류`);
    if(typeof a.name!=='string'||a.name.length<1||a.name.length>30||typeof a.desc!=='string'||a.desc.length<1||a.desc.length>160)throw new Error(`${template.id}: 이름/설명 오류`);
    const p=object(a.p,`${template.id}.p`);keys(p,Object.keys(template.p),`${template.id}.p`);
    for(const k of Object.keys(template.p)){
      const n=p[k],range=parameterRange(k,template.id);
      if(typeof n!=='number'||!Number.isFinite(n)||n<range.min||n>range.max||(range.integer&&!Number.isInteger(n)))throw new Error(`${template.id}.${k}: ${range.min}–${range.max}${range.integer?' 정수':''} 필요`);
    }
    counts[template.tier]++;
    return {id:template.id,tier:template.tier,version:2,name:a.name,desc:a.desc,p:{...p} as Record<string,number>};
  });
  if(Object.values(counts).some(n=>n!==20))throw new Error('각 등급은 정확히 20개여야 해요');
  return {version:2,augments};
}
// Weights deliberately required: no unapproved probability defaults or automatic activation.
export function createAugmentConfigV2(weights:Record<CatalogTier,number>,catalog:AugmentCatalogV2=augmentCatalogV2){
  const checked=parseAugmentCatalogV2(catalog);
  keys(object(weights,'등급 가중치'),['silver','gold','prismatic'],'등급 가중치');
  for(const t of ['silver','gold','prismatic'] as const)if(!Number.isFinite(weights[t])||weights[t]<0)throw new Error('등급 가중치 오류');
  if(!Number.isFinite(weights.silver+weights.gold+weights.prismatic)||weights.silver+weights.gold+weights.prismatic<=0)throw new Error('양수 등급 가중치가 필요해요');
  return {version:2 as const,catalog:checked.augments.map(({id,tier})=>({id,tier})),weights:{...weights}};
}
