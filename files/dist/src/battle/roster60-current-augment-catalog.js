/** Separate current-model catalog. Importing does not enable any battle policy. */
export const CURRENT_AUGMENT_VERSION = 'roster60-current-v1';
export const modernAugmentSpecs = [
    {
        "id": "first",
        "tier": "silver",
        "version": 2,
        "name": "출근 도장",
        "desc": "다음 공격 기술의 순서를 한 단계 앞당겨요.",
        "p": {
            "priority": 1,
            "uses": 1
        },
        "trigger": "합법적인 공격 기술의 행동 순서를 정할 때",
        "effect": "보유 효과 우선도 +1. 기본 속도·동률 seed는 보존하며 slowhammer -3이 우선한다. 수면은 새로 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "공격 의도 계획 1회에 소비",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "bulwark",
        "tier": "silver",
        "version": 2,
        "name": "철벽",
        "desc": "선두가 2턴 동안 받는 공격 피해를 12% 줄여요.",
        "p": {
            "pct": 12,
            "turns": 2
        },
        "trigger": "획득 시 생존 선두",
        "effect": "선두에 공격 피해 감소 guard12%를 부여한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "획득 턴을 포함한 전역 턴 종료 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "firstaid",
        "tier": "silver",
        "version": 2,
        "name": "응급처치",
        "desc": "살아 있는 아군 모두 최대 체력의 10%를 회복해요.",
        "p": {
            "pct": 10
        },
        "trigger": "획득 즉시",
        "effect": "각 생존 아군에 최대 HP10% 회복. 부활하지 않으며 최대 HP를 넘지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "즉시 1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "sharp",
        "tier": "silver",
        "version": 2,
        "name": "정밀 타격",
        "desc": "다음 3번 공격의 위력이 10% 올라요.",
        "p": {
            "pct": 10,
            "uses": 3
        },
        "trigger": "실제 공격 기술 시전",
        "effect": "위력 보너스10%를 가산한다. 기존 매의 눈의 급소 의존을 확정 위력으로 재구성했다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격 시전 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "swift",
        "tier": "silver",
        "version": 2,
        "name": "칼퇴 준비",
        "desc": "3턴 동안 살아 있는 아군의 속도가 10% 올라요.",
        "p": {
            "pct": 10,
            "turns": 3
        },
        "trigger": "획득 시 생존 아군",
        "effect": "현재 speed에 적용되는 임시 +10% modifier. 같은 양수 speed 계열은 최댓값만 적용한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "thick",
        "tier": "silver",
        "version": 2,
        "name": "두꺼운 깃털",
        "desc": "3턴 동안 살아 있는 아군의 내구가 15% 올라요.",
        "p": {
            "pct": 15,
            "turns": 3
        },
        "trigger": "획득 시 생존 아군",
        "effect": "현재 durability에 임시 +15%. 구 물리·마법 DEF를 새로 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "overtime",
        "tier": "silver",
        "version": 2,
        "name": "야근 수당",
        "desc": "다음 3번 공격의 위력이 8% 올라요.",
        "p": {
            "pct": 8,
            "uses": 3
        },
        "trigger": "실제 공격 기술 시전",
        "effect": "위력 보너스8%를 가산한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격 시전 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "drain",
        "tier": "silver",
        "version": 2,
        "name": "회식비 정산",
        "desc": "다음 3번 적중에서 실제 HP 피해의 12%를 회복해요. 회당 내 최대 체력 4%가 한도예요.",
        "p": {
            "pct": 12,
            "uses": 3,
            "capPct": 4
        },
        "trigger": "상대에게 실제 HP 피해를 준 뒤",
        "effect": "실제 상대 HP 손실12%를 생존 공격자에게 회복. 회복 요청은 내 최대HP4% 이하.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "양수 실제 HP 피해를 낸 공격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "thorns",
        "tier": "silver",
        "version": 2,
        "name": "가시 깃털",
        "desc": "다음 3번 피격에서 실제 HP 피해의 10%를 되돌려요. 회당 공격자 최대 체력 4%가 한도예요.",
        "p": {
            "pct": 10,
            "uses": 3,
            "capPct": 4
        },
        "trigger": "상대 공격으로 자기 HP가 실제 감소한 뒤",
        "effect": "실제 손실10%, 공격자 최대HP4% 한도의 직접 보복. 보복은 반사·흡혈·추가 보복을 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "양수 HP 피해 피격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "pierce",
        "tier": "silver",
        "version": 2,
        "name": "송곳 부리",
        "desc": "다음 3번 공격은 상대 내구의 15%를 무시해요.",
        "p": {
            "pct": 15,
            "uses": 3
        },
        "trigger": "실제 공격 기술 시전",
        "effect": "그 공격 계산에만 상대 현재 유효 durability를15% 낮춘다. 원본 능력치는 변경하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격 시전 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "lunch",
        "tier": "silver",
        "version": 2,
        "name": "점심시간",
        "desc": "다음 3턴 종료마다 살아 있는 선두가 최대 체력의 3%를 회복해요.",
        "p": {
            "pct": 3,
            "turns": 3
        },
        "trigger": "각 전역 턴 종료",
        "effect": "그때의 생존 선두만 최대HP3% 회복. 벤치 전체 회복이나 부활이 아니다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "획득 턴 포함 3회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "learn",
        "tier": "silver",
        "version": 2,
        "name": "상성 학습",
        "desc": "3턴 동안 같은 속성을 다시 맞을 때 피해가 8%씩, 최대 16% 줄어요.",
        "p": {
            "pct": 8,
            "capPct": 16,
            "turns": 3
        },
        "trigger": "공격을 받기 직전과 직후",
        "effect": "개체별 같은 기술 element의 이전 실제 피격 횟수로 guard를 계산하고 피격 뒤 횟수 증가. 획득 시 기록 초기화.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "strip",
        "tier": "silver",
        "version": 2,
        "name": "기세 꺾기",
        "desc": "상대 선두의 가장 강한 일시 강화 하나를 제거해요.",
        "p": {
            "count": 1
        },
        "trigger": "획득 즉시, 상대에 제거할 양수 modifier 또는 guard가 있을 때",
        "effect": "가장 큰 pct 하나 제거. 동률은 guard→durability→speed→statusResistance→잔여 턴 순. 소유 증강 자체는 삭제하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "즉시 1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "rinse",
        "tier": "silver",
        "version": 2,
        "name": "긴급 세척",
        "desc": "선두의 일시 약화를 지우고 최대 MP의 10%를 회복해요.",
        "p": {
            "mpPct": 10
        },
        "trigger": "획득 즉시",
        "effect": "생존 선두의 음수 augment modifiers 제거 후 MP 최대치10% 회복. 없는 상태 시스템이나 상태 확률은 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "즉시 1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "resetline",
        "tier": "silver",
        "version": 2,
        "name": "전열 정비",
        "desc": "아군의 일시 약화를 지우고 2턴 동안 속도를 10% 올려요.",
        "p": {
            "pct": 10,
            "turns": 2
        },
        "trigger": "획득 시 생존 아군",
        "effect": "음수 augment modifiers만 제거하고 현재 speed +10%를 부여한다. 양수 강화는 보존한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "정화 즉시, 속도는 전역 턴 종료 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "handoffguard",
        "tier": "silver",
        "version": 2,
        "name": "안전 인계",
        "desc": "다음 두 번의 자발 교체에서 출전 아군이 그 턴 받는 피해를 12% 줄여요.",
        "p": {
            "pct": 12,
            "uses": 2,
            "turns": 1
        },
        "trigger": "자발 교체 성공 직후",
        "effect": "출전 개체에 현재 전역 턴 종료까지 guard12%. 강제 교체는 소비하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "팀 공유 자발 교체 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "relief",
        "tier": "silver",
        "version": 2,
        "name": "구호 배달",
        "desc": "다음 두 번의 자발 교체에서 출전 아군이 최대 체력의 8%를 회복해요.",
        "p": {
            "pct": 8,
            "uses": 2
        },
        "trigger": "자발 교체 후 출전 개체가 손상됐을 때",
        "effect": "최대HP8% 회복. 강제 교체나 가득 찬 HP는 소비하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "양수 회복 요청이 가능한 교체 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "aim",
        "tier": "silver",
        "version": 2,
        "name": "집중 조준",
        "desc": "다음 공격의 위력이 20% 올라요.",
        "p": {
            "pct": 20,
            "uses": 1
        },
        "trigger": "실제 공격 기술 시전",
        "effect": "위력 보너스20%를 가산한다. 기존 급소 의존을 확정 위력으로 재구성했다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격 시전 1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "response",
        "tier": "silver",
        "version": 2,
        "name": "후수 응수",
        "desc": "상대가 이번 턴 먼저 공격했다면 내 공격 위력이 12% 올라요. 3회까지 적용돼요.",
        "p": {
            "pct": 12,
            "uses": 3
        },
        "trigger": "상대가 이번 전역 턴 실제 공격 기술을 시전한 뒤 내 시전",
        "effect": "위력 보너스12%. focus·아이템·교체는 상대 공격으로 세지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 실제 공격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "exploit",
        "tier": "silver",
        "version": 2,
        "name": "빈틈 공략",
        "desc": "속도가 약화된 적을 공격할 때 내구 20%를 무시해요. 3회까지 적용돼요.",
        "p": {
            "pct": 20,
            "uses": 3
        },
        "trigger": "대상에 남은 음수 speed modifier가 있는 실제 공격",
        "effect": "그 공격에만 대상 유효 durability20%를 무시한다. rustle 등 실제 약화와 연결된다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 실제 공격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "reverse",
        "tier": "prismatic",
        "version": 2,
        "name": "판세 전환",
        "desc": "내 남은 체력 비율이 상대보다 낮으면 공격 위력이 35% 올라요. 3회까지 적용돼요.",
        "p": {
            "pct": 35,
            "uses": 3
        },
        "trigger": "시전 직전 자기 HP%가 상대 HP%보다 낮을 때",
        "effect": "현재 체력 비율 조건의 위력 보너스35%. 개체 primary attribute를 만들거나 추측하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 실제 공격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "weakguard",
        "tier": "gold",
        "version": 2,
        "name": "약점 보강",
        "desc": "받는 강한 상성 공격의 배율을 보통 배율로 낮춰요. 무효·반사는 유지돼요.",
        "p": {
            "multiplier": 1
        },
        "trigger": "대상에게 들어오는 최종 일반 상성 배율이1보다 클 때",
        "effect": "일반 weak 반응을1로 제한한다. focus·weather 뒤에 적용하되 sealedduel은 이 방어를 무시한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전투 내 지속",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "focus",
        "tier": "gold",
        "version": 2,
        "name": "집중 공략",
        "desc": "강한 상성 공격의 배율이 1.9가 돼요. MP 집중 행동과는 별개예요.",
        "p": {
            "multiplier": 1.9
        },
        "trigger": "일반 공격 상성 배율이1보다 클 때",
        "effect": "일반 weak의 배율을1.9로 고정. 상대 weakguard가 뒤에 적용되며 immune/reflect는 바꾸지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전투 내 지속",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "neutral",
        "tier": "gold",
        "version": 2,
        "name": "무속성 지대",
        "desc": "3턴 동안 양쪽의 보통·강함·반감 상성이 1배가 돼요. 무효·반사는 유지돼요.",
        "p": {
            "multiplier": 1,
            "turns": 3
        },
        "trigger": "획득 즉시 전장에 적용",
        "effect": "양쪽 일반 normal/weak/resist 배율을1로 한다. weather보다 우선하며 suitability는 보존한다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "camo",
        "tier": "gold",
        "version": 2,
        "name": "보호색",
        "desc": "직전에 맞은 속성을 다시 맞으면 피해가 25% 줄어요. 3회까지 적용돼요.",
        "p": {
            "pct": 25,
            "uses": 3
        },
        "trigger": "직전 양수 HP 피해 공격의 element와 같은 공격을 다시 받을 때",
        "effect": "현재 개체별 직전 피격 element를 기억하고 같은 element 후속 공격에 guard25%. 개체 속성은 변경하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 피격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "dual",
        "tier": "gold",
        "version": 2,
        "name": "쌍갈래 준비",
        "desc": "서로 다른 속성을 처음 사용할 때 공격 위력이 20% 올라요. 최대 3속성에 적용돼요.",
        "p": {
            "pct": 20,
            "uses": 3
        },
        "trigger": "보유 후 아직 사용하지 않은 공격 element로 실제 시전",
        "effect": "팀 공유 기록에 없는 공격 element 첫 시전마다 위력20%. 새 개체 속성이나 자동 추가타는 없다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "서로 다른 element 최초 시전 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "counter",
        "tier": "gold",
        "version": 2,
        "name": "역습 태세",
        "desc": "강한 상성 공격을 맞고 살아남으면 실제 피해의 20%를 되돌려요. 공격자 최대 체력 6% 한도, 2회예요.",
        "p": {
            "pct": 20,
            "capPct": 6,
            "uses": 2
        },
        "trigger": "일반 weak 공격으로 HP가 감소한 뒤 생존했을 때",
        "effect": "실제 자기HP손실20%, 공격자최대HP6% 한도 직접 보복. 합성 기술·속성·MP를 만들지 않고 보복 재귀 없음.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 피격 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "freeswitch",
        "tier": "gold",
        "version": 2,
        "name": "인사 발령",
        "desc": "자발 교체 후 같은 턴 출전 아군의 기술을 하나 사용해요. 기술은 직접 선택해야 해요.",
        "p": {},
        "trigger": "인증된 자발 교체 요청의 명시적인 then 기술",
        "effect": "출전 개체 소유·장착·MP 검증 후 같은 행동에 후속 기술1개. 강제 교체에 추가 행동을 주지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전투 내 지속",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "lastone",
        "tier": "gold",
        "version": 2,
        "name": "마지막 출근",
        "desc": "살아 있는 아군이 하나면 위력·내구·속도가 각각 20% 올라요.",
        "p": {
            "pct": 20
        },
        "trigger": "생존 아군이 정확히1명인 동안",
        "effect": "현재 power/durability/speed에 조건부20%. 없는 ATK/물리마법DEF를 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족하는 동안",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "rustle",
        "tier": "gold",
        "version": 2,
        "name": "바스락",
        "desc": "상대 선두의 속도를 2턴 동안 최대 20% 낮춰요. 상태 저항이 높을수록 약화가 작아요.",
        "p": {
            "pct": 20,
            "turns": 2,
            "resistanceScale": 100
        },
        "trigger": "획득 즉시 상대 생존 선두",
        "effect": "결정적 speed 약화율=20/(1+statusResistance/100)%. 이100은 증강 설계 가정이며 기본 확률·지속시간을 바꾸지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "peanut",
        "tier": "gold",
        "version": 2,
        "name": "땅콩 기세",
        "desc": "갈매미맨의 공격 위력이 30% 오르지만 받는 피해도 15% 늘어요.",
        "p": {
            "pct": 30,
            "incomingPct": 15
        },
        "trigger": "개체 이름에 갈매미맨이 포함된 동안",
        "effect": "조건을 만족한 개체의 power+30%, incoming+15%. 특정 종족ID로 임의 한정하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전투 내 지속",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "relaytype",
        "tier": "gold",
        "version": 2,
        "name": "속성 릴레이",
        "desc": "직전과 다른 속성으로 공격하면 위력이 25% 올라요. 3회까지 적용돼요.",
        "p": {
            "pct": 25,
            "uses": 3
        },
        "trigger": "직전 실제 공격 element와 다른 실제 공격",
        "effect": "첫 공격은 조건 없음. focus·아이템은 직전 공격 속성을 바꾸지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 공격 3회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "breach",
        "tier": "gold",
        "version": 2,
        "name": "집중 파훼",
        "desc": "MP 집중 행동 뒤의 다음 공격은 흡수 실드를 무시해요. 한 번 적용돼요.",
        "p": {
            "uses": 1
        },
        "trigger": "실제 MP focus 행동 완료 후 다음 공격",
        "effect": "focus가 실제로 끝나면 준비하고 다음 공격에서 대상 augment shield만 건너뛴다. intrinsic immune/reflect와 내구는 보존.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "준비 후 실제 공격1회, 전투 종료 시 폐기",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "triage",
        "tier": "gold",
        "version": 2,
        "name": "응급 흡수",
        "desc": "공격 전 체력이 절반 이하이면 실제 HP 피해의 25%를 회복해요. 내 최대 체력 10% 한도, 2회예요.",
        "p": {
            "pct": 25,
            "capPct": 10,
            "thresholdPct": 50,
            "uses": 2
        },
        "trigger": "시전 전 HP≤50%이고 양수 상대HP피해를 준 뒤 생존",
        "effect": "실제 상대HP손실25% 회복 요청, 내 최대HP10%와 시전당 회복 총15% 한도.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 공격 2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "rescue",
        "tier": "gold",
        "version": 2,
        "name": "구조 신호",
        "desc": "피격으로 체력이 30% 이하가 되면 일시 약화를 지우고 최대 체력 15%의 실드를 얻어요. 한 번 적용돼요.",
        "p": {
            "thresholdPct": 30,
            "pct": 15,
            "uses": 1,
            "turns": 2
        },
        "trigger": "피격 전HP>30%, 피격 후0<HP≤30%",
        "effect": "음수 augment modifiers 제거 후 최대HP15% 흡수shield2턴. 죽은 개체는 구조하거나 부활하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건 충족1회, 실드는 전역 턴 종료2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "firstshell",
        "tier": "gold",
        "version": 2,
        "name": "단단한 첫발",
        "desc": "각 아군이 처음 받는 공격의 피해를 30% 줄여요.",
        "p": {
            "pct": 30
        },
        "trigger": "각 생존 아군 개체의 첫 실제 공격 피격",
        "effect": "개체별 첫 피격 guard30%. shield가 모두 막아도 공격 피격이면 준비 사용. sealedduel에서는 사용하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "개체별 첫 공격 피격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "chase",
        "tier": "gold",
        "version": 2,
        "name": "추격 표식",
        "desc": "상대가 자발 교체한 턴에는 공격 위력이 30% 올라요. 2회까지 적용돼요.",
        "p": {
            "pct": 30,
            "uses": 2
        },
        "trigger": "상대가 이번 전역 턴 자발 교체했을 때 실제 시전",
        "effect": "위력+30%. 강제 교체는 조건으로 세지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 공격2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "baton",
        "tier": "gold",
        "version": 2,
        "name": "마지막 전달",
        "desc": "첫 아군 전투불가 뒤 강제 출전한 개체가 그 턴 받는 피해를 35% 줄여요.",
        "p": {
            "pct": 35,
            "uses": 1,
            "turns": 1
        },
        "trigger": "아군KO 뒤 첫 강제 교체 성공",
        "effect": "출전 개체에 현재 전역 턴 종료까지 guard35%. 자발 교체에는 발동하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "팀 공유 강제 교체1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "overflowguard",
        "tier": "gold",
        "version": 2,
        "name": "넘치는 활력",
        "desc": "초과회복을 내 최대 체력 20%까지 실드로 바꿔요. 실드는 2턴, 전환은 3회예요.",
        "p": {
            "pct": 100,
            "capPct": 20,
            "turns": 2,
            "uses": 3
        },
        "trigger": "생존 개체의 합법적 회복 요청이 최대HP를 넘을 때",
        "effect": "허용 회복 요청의 실제 초과량100%를 shield에 가산, maxHP20% cap. 부활·금지된 회복에는 발동하지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "양수 초과량 전환3회, 각 실드2회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "typebridge",
        "tier": "gold",
        "version": 2,
        "name": "상성 다리",
        "desc": "다음 두 번 반감 공격의 배율을 보통으로 바꿔요. 무효·반사는 유지돼요.",
        "p": {
            "multiplier": 1,
            "uses": 2
        },
        "trigger": "일반 resist 배율의 실제 공격",
        "effect": "선행 neutral·equalfield 등으로 이미 불리하지 않으면 사용하지 않는다. immune/reflect를 공격 가능하게 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족한 공격2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "redline",
        "tier": "prismatic",
        "version": 2,
        "name": "한계 돌파",
        "desc": "최대 체력 20%를 내고 다음 공격 위력을 65% 올려요. 그 공격으로는 회복할 수 없어요.",
        "p": {
            "pct": 65,
            "costPct": 20,
            "uses": 1
        },
        "trigger": "자기HP가 최대HP20% 비용보다 많을 때 실제 공격",
        "effect": "비용을 지불하고 power+65%. 비용으로KO불가, 해당 시전 모든 회복 금지.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "생존 비용 지불한 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "underdog",
        "tier": "prismatic",
        "version": 2,
        "name": "거인 사냥",
        "desc": "10레벨 이상 높은 적에게 체력 8%를 내고 실제 피해 35%를 추가해요. 적 최대 체력 18% 한도, 2회예요.",
        "p": {
            "pct": 35,
            "costPct": 8,
            "capPct": 18,
            "levelGap": 10,
            "uses": 2
        },
        "trigger": "상대 레벨≥자기+10, 자기HP>비용8%인 공격",
        "effect": "시전 비용 후 양수 상대HP피해35% 직접추가. 전체 추가HP피해는 대상 최대HP18% 한도 공유.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "비용 지급한 공격2회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "glasswing",
        "tier": "prismatic",
        "version": 2,
        "name": "유리 날개",
        "desc": "3턴 동안 다음 두 번 공격 위력이 50% 오르고, 받는 피해는 25% 늘어요.",
        "p": {
            "pct": 50,
            "uses": 2,
            "turns": 3,
            "takenPct": 25
        },
        "trigger": "획득 직후3턴, 실제 공격2회",
        "effect": "power+50%는2회 한정, incoming+25% 위험은 사용권 소진 후에도3턴 만료까지 남는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "위력 실제공격2회 또는3회 종료, 위험3회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "lonecharge",
        "tier": "prismatic",
        "version": 2,
        "name": "배수의 진",
        "desc": "3턴 동안 다음 두 번 공격 위력이 45% 오르고 내구 20%를 무시해요. 받는 피해는 20% 늘어요.",
        "p": {
            "pct": 45,
            "piercePct": 20,
            "incomingPct": 20,
            "uses": 2,
            "turns": 3
        },
        "trigger": "획득 후3턴 내 실제 공격",
        "effect": "power+45%와 계산용 targetdurability−20%. 교체·아이템을 봉쇄하지 않고 위험incoming+20%를 대가로 둔다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "위력/관통2시전 또는3회 종료, 위험3회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "equalfield",
        "tier": "prismatic",
        "version": 2,
        "name": "평준화 결투",
        "desc": "다음 공격은 낮은 내 공격 계산 레벨을 최대 15 보정하고 일반 상성을 1배로 해요.",
        "p": {
            "levelGap": 15,
            "multiplier": 1,
            "uses": 1
        },
        "trigger": "다음 실제 공격",
        "effect": "effectiveLevel=min(99,ownLevel+min(15,max(0,foeLevel−ownLevel))). 실제성장값 보존, 일반상성1. 급소를 새로 만들지 않는다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "blindspot",
        "tier": "prismatic",
        "version": 2,
        "name": "사각 돌파",
        "desc": "최대 체력 12%를 내고 다음 반감 공격의 상성을 2배로 바꿔요. 무효·반사는 유지돼요.",
        "p": {
            "multiplier": 2,
            "costPct": 12,
            "uses": 1
        },
        "trigger": "일반 resist 공격이고 앞선 상성override없으며 자기HP>비용",
        "effect": "생존 비용 지불 후 일반 resist만2배. neutral·선행override로 반감이 아니면 비용·사용권 모두 유지.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건을 만족하고 비용 지급한 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "doubleedge",
        "tier": "prismatic",
        "version": 2,
        "name": "양날 계약",
        "desc": "다음 적중에 실제 피해 30%를 추가하고 추가 피해의 절반을 반동으로 받아요. 그 공격 회복은 금지돼요.",
        "p": {
            "pct": 30,
            "capPct": 18,
            "recoilPct": 50,
            "uses": 1
        },
        "trigger": "다음 양수 상대HP피해 공격",
        "effect": "직접추가=min(실제HP피해30%,대상최대HP18%공유예산), 추가 실제손실의50% 반동. 반동KO가능, 재귀없음.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "양수 상대HP피해 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "lastmatch",
        "tier": "prismatic",
        "version": 2,
        "name": "최후의 불꽃",
        "desc": "마지막 아군의 체력이 30% 이하이면 체력 10%를 내고 한 번 위력을 80% 올려요. 회복은 금지돼요.",
        "p": {
            "pct": 80,
            "costPct": 10,
            "thresholdPct": 30,
            "uses": 1
        },
        "trigger": "아군생존1명·시전전HP≤30%·자기HP>비용10%",
        "effect": "생존 비용 후power+80%, 해당 시전 회복금지.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건충족·비용지급 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "bloodrelay",
        "tier": "prismatic",
        "version": 2,
        "name": "혈맹 인계",
        "desc": "자발 교체하는 아군이 체력 20%를 내면 출전 아군의 다음 공격 위력이 60% 올라요.",
        "p": {
            "pct": 60,
            "costPct": 20,
            "uses": 1,
            "turns": 2
        },
        "trigger": "자발교체에서 outgoing HP>최대HP20%비용",
        "effect": "outgoing이 생존 비용 지급, incoming다음공격 power+60%. 강제교체·비용불가는 미발동/미소비.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "인계1회, 보너스는 이번·다음 전역 턴 종료까지",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "quietstorm",
        "tier": "prismatic",
        "version": 2,
        "name": "고요한 집중",
        "desc": "4턴 안에 MP 집중을 두 번 하면 다음 공격 위력이 90% 올라요. 그 공격 회복은 금지돼요.",
        "p": {
            "pct": 90,
            "uses": 1,
            "turns": 4,
            "count": 2
        },
        "trigger": "실제 MPfocus 행동2회 후 실제 공격",
        "effect": "팀 공유focus횟수2를 충족하면power+90%. 가짜utility대신 실제현대focus를명시. 해당시전 회복금지.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "획득부터 전역 턴 종료4회 또는 준비공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "rainbow",
        "tier": "prismatic",
        "version": 2,
        "name": "삼색 연쇄",
        "desc": "4턴 안에 서로 다른 세 속성으로 연속 공격하면 세 번째 위력이 75% 올라요.",
        "p": {
            "pct": 75,
            "distinctCount": 3,
            "uses": 1,
            "turns": 4
        },
        "trigger": "서로 다른 공격element3개 연속의 세 번째 시전",
        "effect": "동일element반복은 그element1개부터 재시작. focus/아이템/교체는 연쇄 초기화. 새crit대가나추가타없음.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "전역 턴 종료4회 또는 조건충족1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "crackedarmor",
        "tier": "prismatic",
        "version": 2,
        "name": "균열 갑옷",
        "desc": "첫 피격 피해를 60% 줄이지만, 그 뒤 2턴 동안 받는 피해가 25% 늘어요.",
        "p": {
            "pct": 60,
            "incomingPct": 25,
            "uses": 1,
            "turns": 2
        },
        "trigger": "획득 후 첫 실제 공격 피격",
        "effect": "guard60%1회 후 incoming+25%를 부여. 새DEF약화를가정하지않고피해위험을명시했다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "감소1피격, 위험은 그 턴 포함2회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "mortgage",
        "tier": "prismatic",
        "version": 2,
        "name": "빌린 생명",
        "desc": "선두가 즉시 체력 35%를 회복하고, 다음 턴 종료에 최대 체력 25%를 지불해요.",
        "p": {
            "costPct": 25,
            "turns": 2,
            "healPct": 35
        },
        "trigger": "획득 즉시 선두",
        "effect": "생존선두최대HP35%회복, 소유개체가벤치여도다음전역턴종료에25%직접비용. 지연비용KO가능.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "획득 즉시회복, 이번·다음 종료 중두번째에비용",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "coldbargain",
        "tier": "prismatic",
        "version": 2,
        "name": "냉혹한 거래",
        "desc": "회식비 정산 또는 응급 흡수를 보유하면 등장해요. 다음 공격 위력을 55% 올리는 대신 그 공격으로는 회복할 수 없어요.",
        "p": {
            "pct": 55,
            "uses": 1
        },
        "trigger": "팀이 drain 또는 triage를 보유한 경우에만 제안, 다음 실제 공격 시전",
        "effect": "power+55%, 해당 시전 모든 회복 금지. 제안의 보유 검사는 drain/triage의 남은 사용 횟수 검사와 별도다.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "shatterprice",
        "tier": "prismatic",
        "version": 2,
        "name": "방벽 거래",
        "desc": "자기 일시 강화와 실드를 버리고 다음 공격에서 상대 내구 65%를 무시해요.",
        "p": {
            "pct": 65,
            "uses": 1
        },
        "trigger": "양수 일시modifier·guard·shield가 하나 이상인 실제 공격",
        "effect": "공격자의 양수augmentmodifier/guard/shield전부제거 후 계산용targetdurability65%무시. 음수약화/보유ID는보존.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건충족 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "hostileweather",
        "tier": "prismatic",
        "version": 2,
        "name": "폭풍 계약",
        "desc": "2턴 동안 양쪽 강한 상성은 2.2배, 반감은 0.6배가 돼요. 무효·반사는 유지돼요.",
        "p": {
            "weakMultiplier": 2.2,
            "resistMultiplier": 0.6,
            "turns": 2
        },
        "trigger": "획득 즉시 아직 사용하지 않은 전장계약",
        "effect": "양측에같은일반상성전장1회생성. 다른쪽재선택으로기간을새로늘리지않는다. neutral우선.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "생성부터 전역 턴 종료2회, 전투당최초1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "slowhammer",
        "tier": "prismatic",
        "version": 2,
        "name": "느린 철퇴",
        "desc": "다음 공격을 뒤로 미루는 대신 위력을 70% 올려요.",
        "p": {
            "priority": -3,
            "pct": 70,
            "uses": 1
        },
        "trigger": "다음 실제 공격 의도순서와시전",
        "effect": "보유효과priority−3이first+1보다우선. power+70%는실제시전에만소비. 대기선두KO면소멸.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격1회 또는 대기선두KO",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "siegebreak",
        "tier": "prismatic",
        "version": 2,
        "name": "공성 돌파",
        "desc": "체력 80% 이상인 적에게 한 번 위력이 65% 올라요. 이후 2턴 동안 받는 피해가 30% 늘어요.",
        "p": {
            "pct": 65,
            "thresholdPct": 80,
            "incomingPct": 30,
            "uses": 1,
            "turns": 2
        },
        "trigger": "시전 직전 상대HP≥80%",
        "effect": "power+65%1회 후 자기incoming+30%위험을이번·다음종료까지부여.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건충족공격1회, 위험2회 종료",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "sharedburden",
        "tier": "prismatic",
        "version": 2,
        "name": "공동 부담",
        "desc": "대기 아군 모두가 체력 10%씩을 내면 다음 공격 위력이 60% 올라요.",
        "p": {
            "pct": 60,
            "costPct": 10,
            "uses": 1
        },
        "trigger": "생존벤치가1명이상이고전원HP>각최대HP10%",
        "effect": "생존벤치각각비용을한번에검증후지급, 공격power+60%. 비용으로벤치KO불가.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "조건충족·비용지급 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    },
    {
        "id": "sealedduel",
        "tier": "prismatic",
        "version": 2,
        "name": "봉인 결투",
        "desc": "다음 공격은 다른 증강 위력·흡혈과 상대 증강 방어·실드를 무시해요. 무효·반사는 유지돼요.",
        "p": {
            "uses": 1
        },
        "trigger": "다음 실제 공격 시전",
        "effect": "다른자기증강위력·관통·회복·비용·소비, 상대증강guard/shield/durability강화를건너뛴다. 이미걸린위험페널티는남는다. intrinsic특수상성은보존.",
        "stack": "같은 ID 중복 획득 없음. 사용 횟수는 팀 공유이며 개체 효과는 명시한 대상에 부여. 위력 가산 합100%, 내구 관통 합70%, 유효 내구 modifier 합계 −50%..+100%, speed 양수는 최댓값·음수는 합산 후 최종값0이상, 피해 감소 합성 상한60%, 취약 가산100%, shield 합계 최대HP20%.",
        "expiry": "실제 공격1회",
        "specialInteractions": "intrinsic immune는 HP피해0, reflect는 대상무피해·공격자반환. 둘은 일반 상성override로 바뀌지 않는다. 실제 상대HP피해가 없으면 흡혈·추가타·보복이 없다. 모든 2차 직접피해는 재귀 없음.",
        "balanceAssumption": true
    }
];
// Version 2 is the existing offer-card wire shape, not the combat runtime policy.
export const roster60CurrentCatalog = { version: 2, augments: modernAugmentSpecs.map(({ id, tier, version, name, desc, p }) => ({ id, tier, version, name, desc, p: { ...p } })) };
for (const spec of modernAugmentSpecs) {
    Object.freeze(spec.p);
    Object.freeze(spec);
}
Object.freeze(modernAugmentSpecs);
for (const a of roster60CurrentCatalog.augments) {
    Object.freeze(a.p);
    Object.freeze(a);
}
Object.freeze(roster60CurrentCatalog.augments);
Object.freeze(roster60CurrentCatalog);
export const modernAugmentById = new Map(modernAugmentSpecs.map(x => [x.id, x]));
