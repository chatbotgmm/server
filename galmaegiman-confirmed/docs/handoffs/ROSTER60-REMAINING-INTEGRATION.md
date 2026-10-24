# 새 60종 제품 통합 누락 점검 (e0ff5fe 후속)

## 보상 출처
기존 `data/economy.yaml:54` + `src/expedition.ts` + `src/game.ts:1946`은 전투 모험이 아닌 60분 파견 탐험이다. 최대10개체, 개체당 새우gang10, 구 BASIC 증가15/30%, GOLD 두배15/30%, DARK 50/40분, SEA 구 커먼10/20%, BLOSSOM 구 언커먼5/10%, COSMOS 지원서1장15/30%, OUTFIT 개체당+2/4. 같은개체 수 보너스도 존재한다. 실제 claimExpedition은 claimedAt CAS와 동일 트랜잭션으로 구 OwnedCharacter 및 재화를 지급한다.
새 모험에 이 파견의 시간/구 시너지/구 개체 지급을 자동 이식할 근거가 없다. 해당 경로에 전투 소모 아이템 드롭표는 없다. 새 모험의 재화·아이템 드롭 확률/양은 설정하지 않았다.

## 독립 보완
`roster60-adventure-rewards.ts`는 서버 저장 임시 보상의 XP/credits/snack/peanut/items 공통 타입과 음수/NaN/알 수 없는 종류/외부 XP 대상 검증을 제공한다. 실제 adventure/return 거래는 재화·아이템·XP·개체 잠금 해제·영수증을 함께 처리한다. 한도 초과는 전체 롤백 및 임시 보상 유지이며 자의적인 초과분 삭제나 한도 확대를 하지 않는다. 전멸은 종류와 무관하게 해당 모험의 임시 보상 전체를 비운다. 아이템/재화 지급 테스트 값은 서버 DB fixture로 주입하며 런타임 드롭 정책으로 등록하지 않았다. 클라이언트 보상 입력 API는 없다.

## 실제 제품 경로 체크리스트
|영역|현재 코드 사실|남은 연결/결정|
|새 전투|roster60-routes + roster60-battle + NativeRoster60Battle 인증 제품 연결|서버 AI만 상대. 실제 인간 PvP 미완료|
|기존 인간 PvP|my.ts registerRealtimePvp, pvp/realtime-native-view는 기존 battle/engine 사용|새 roster 소유/6편성/5스탯/양측 명령·기한·교체 우선군/정산 어댑터 필요. 기존 규칙을 새 엔진 완료로 계산 금지|
|상태이상|five-combat.statusChance 순수 계산 존재. 새 fighter는 effects/priority 기술 거절|실제 상태 저장·적용·지속시간·턴말 처리 없음. 효과 목록/확률/지속시간 결정 필요. 해제 아이템 소비하지 않고 거절|
|심사 로그인|review-auth.ts 기존 allowlist에 native/roster60/* 없음. my.ts hook가 심사 세션에403 반환|새 API 명시 allowlist 및 로그인→신규 시작 E2E 필요. 실제 서버 인증 정보/설정 조회 안 함. 권한을 포괄 wildcard로 열지 않음|
|Android 편성|새 전투 자체 편성 선택은 Roster60Owned 사용|기존 native/team/StorySave 및 구 별도 편성 화면은 여전히 구 자료. 새 공통 편성 저장으로 전환 필요|
|웹|public/game·duel·my는 기존 엔진/개체 사용|새 roster 웹 전투 UI 없음. 이번 아이템 무제한 표시 수정이 웹 개편 완료는 아님|
|채팅|game.ts의 기존 명령/OwnedCharacter/파견 유지|새 모집/환생/임시지급 일부 가드만 존재. 전체 채팅 새 카탈로그 전환 미완료|
|보상|새 모험 XP 생산 및 공통 정산 지원|재화·아이템 드롭 생산표와 출처 대응 미정. 예비/참전 XP 분배 미정|
|모집|에픽Lv15 정책 및 거래 검증 완료, MID/HIGH비활성|전체등급 대응/확률/천장/가격 및 수급 경제 결정 필요|

운영 서비스 변경, DB 초기화, 외부 업로드 없음. 본 점검은 완성/배포 가능 판정이 아니다.

검증: Node/웹 빌드 성공. 격리 DB 전체 59파일/608테스트 통과(`/tmp/adventure-rewards-regression.log`). 공통 보상/정산 대상 7테스트 재검증. Android 소스 변경 및 APK 재빌드 없음; 앞선 e0ff5fe APK는 그대로 보존한다.
