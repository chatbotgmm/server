# 새 로스터 온보딩·우편 선택권 격리 계약

2026-10-24 확정: u01 갈매미맨으로 튜토리얼 시작 → 서버가 완료 검증 → 우편함에 커먼 선택지급권 1장 → u02~u08의 스탯·기술·적성·방어 정보 확인 → 최종 확인 시 티켓 소비와 영구 개체 지급을 한 거래로 처리. 취소/닫기는 미소비. 기존 D1 지급 및 15일 임시 스타터 2마리 정책은 새 흐름에서 폐지한다. 대체 재화 보상은 추가하지 않는다.

## 구현 상태와 실행

`contract.mjs`는 운영에 import되지 않는 검토용 모듈이다. HTTP 라우트·실제 DB 스키마·Android UI·현재 지급 함수를 변경하지 않았다. 기존 운영 기능에 반만 연결하지 않았다. 새 시스템 이름공간 `roster60-onboarding-v1`과 u01~u08 ID만 사용한다. 옛 D1/C1 ID를 덮어쓰지 않는다. 기존 임시 지급은 운영 코드에서 아직 실행 가능하며, 이 계약 작성만으로 제거 완료라고 말할 수 없다.

```sh
node --test review/onboarding-v2/contract.test.mjs
```

7개 테스트 통과. tests의 승인 catalog는 스탯 숫자가 없는 불투명 표시 fixture이며 제품 기본 데이터가 아니다. MP100/비용6·12·20·30 등 이전 미승인 제안값은 사용하지 않았다. 빌드·실제 DB·네트워크 호출은 없다.

## 계약

- `start(auth)`: 인증된 계정의 새 흐름 시작을 한 번 기록하고 u01 시작 개체를 준비. 같은 계정 반복 요청은 같은 시작 개체 식별자를 반환.
- `complete(auth)`: 서버 게임 진행이 기록한 tutorialCompleteVerified와 시작 상태가 있어야 완료 우편/티켓 생성. 클라이언트 완료 플래그/임의 step을 입력받지 않는다. 완료 후 재호출해도 소비된 티켓을 재충전하지 않는다.
- `preview(auth, {ticketId})`: 서버가 소유권/티켓 상태를 확인한 후 승인된 catalog의 u02~u08 상세를 반환. 이미 소비됐으면 receipt 반환. 창 닫기/취소는 API 호출이 필요 없고 상태 변경도 없다.
- `confirm(auth, {ticketId, characterId, catalogVersion})`: 서버 고정 선택 목록·소유권·미사용 상태·catalog 버전을 검사. 소비와 결과 개체/receipt를 같은 트랜잭션에서 저장. 동일 선택 재전송은 같은 receipt, 다른 선택은 ALREADY_REDEEMED. 사용 완료 응답은 이후 catalog 교체나 일시 장애에도 receipt로 재전송 가능.
- auth는 서버 세션의 신뢰된 principal이다. HTTP body의 playerId를 그대로 넣으면 안 된다. 숫자/스탯은 클라이언트에서 받지 않는다. 실제 catalog 승인/수치 schema 검증은 별도 서버 adapter의 책임이며 테스트 approved=true fixture를 제품 승인 자료로 사용하지 않는다.
- catalog 미승인/누락/불완전이면 CATALOG_PENDING으로 멈추고 지급하지 않는다. 아직 승인 데이터가 없으므로 실제 7종 정보 화면은 구현 완료가 아니다.

## 저장소 adapter·새 스키마 계획 (미실행)

검토 aggregate는 started, tutorialCompleteVerified, units, mail, ticket, receipt를 보유한다. 제품 저장소는 `store.transaction(authenticatedPlayerId, callback)`의 원자성/격리성을 반드시 제공해야 한다. 메모리 모델의 단일 큐는 여러 서버 프로세스의 경합을 검증하지 않는다.

권장 새 엔티티와 제약(설계안, Prisma/SQL을 실행하지 않음):

1. OnboardingV2: unique(playerId, flowVersion), 서버 검증 진행 증거. 시작 지급 키 고유 제약.
2. ChoiceTicketV2: owner, flowVersion, status, catalogVersion/receipt 참조. unique(playerId, flowVersion, completionRewardKind).
3. MailV2 또는 호환 가능한 기존 우편 attachment: unique(playerId, completionRewardKey), ticketId 고유 연결. 읽음 표시와 지급 완료를 분리.
4. NewRosterOwnedUnit: speciesId=u01~u60. 선택권 결과는 sourceTicketId UNIQUE. 기존 계정 참조는 유지하고 기존 캐릭터 ID를 재활용하지 않음.
5. ChoiceReceiptV2: ticketId UNIQUE, 선택 종/생성 개체/서버 catalog 버전. 티켓 조건부 UPDATE(status=unused)의 영향 행 1개 + 결과 생성 + receipt를 동일 DB transaction에서 commit. 실패하면 모두 rollback.

고유 충돌·직렬화 실패의 재시도는 rollback 이후 기존 receipt를 다시 읽어 처리한다. 임의 새 receipt/새 티켓을 발급하지 않는다. 실제 SQL 트랜잭션·고유 제약·둘 이상의 DB 연결에서 동시 확정/동시 완료/서버 재시작 검증이 완료되어야 운영용 중복 방지를 보장할 수 있다.

## 후속 Android·서버 연결 순서

1. 승인된 새 roster catalog와 기술 schema를 확보한다. 현재 GitHub PNG/manifest는 그림·영역 정보이지 스탯·MP·적성·방어 데이터가 아니다. u02~u08 각 종의 서버 승인 상세 데이터와 u01 첫 전투 정의가 필요하다. 값을 임의 생성하지 않는다.
2. 새 저장 엔티티·서버 진행 검증·authenticated API·catalog DTO를 구현하고 격리 DB에서 검증한다. 데이터 초기화는 옛 게임 상태 allowlist로 별도 설계하며 계정 인증/결제에는 확장하지 않는다. 이번에 운영 DB를 바꾸지 않는다.
3. `src/game.ts:webTutorialSet`의 D1 grant/battleTeam 자동 설정, `src/starter-loans.ts` 및 claim 경로를 새 흐름에서 제거/차단하고, 완료 step 대신 서버 진행 증거를 사용한다. 기존 우편 기능과 새 attachment/receipt의 연결 방식을 확정한다.
4. `NativeTutorialController.java`의 태초 보상 카드·이름 기반 spotlight·자체속성 설명, 임시 스타터 선택 UI/만료 안내를 새 흐름에서 제거한다. 새 튜토리얼은 u01을 참조하고, 완료 후 '우편함에서 커먼 선택지급권 확인'으로 안내한다. 이름은 갈매미맨이며 게임 타이틀 변경 아님.
5. Android 우편함 티켓 → 7종 상세 비교 → 선택 → 최종 확인. 확인 중 중복 탭 차단은 UX 보조일 뿐 서버 중복 방지를 대체하지 않는다. 재접속/화면 복귀 시 서버 티켓/receipt 재조회. 네트워크 결과 불명이면 새 지급 요청을 만들지 말고 같은 티켓 결과를 확인.

## 검증 결과와 남은 항목

통과: 시작/완료 각각 반복20회에서 1회 지급·우편, 완료 미검증 거부, preview 반복/닫기 후 상태 불변, 서로 다른7종 동시 확정 시 1개 성공, 새 service 인스턴스 재접속+동일 선택20회 재전송, 타인 티켓/u01/D1/미등록 종/구 catalog/위조 필드 거부, 미승인 catalog 차단, commit 실패 시 소비·개체 생성 및 우편·티켓 동시 rollback.

미실행/의존성: 실제 DB와 다중 프로세스 경합, 인증 API, 서버 완료 증거 생성, 승인 스탯/기술/적성/방어 catalog, Android 우편/상세/확인 UI·실기기, D1/임시 스타터 운영 경로 제거, 빌드·push·병합·DB·배포. 전체 게임 적용 완료가 아니다.
