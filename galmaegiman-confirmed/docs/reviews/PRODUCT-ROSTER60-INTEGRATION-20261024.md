# 제품 60종 통합 진행 상태

## 후속 구현: 실제 인증/DB/Android 온보딩 연결

이 절은 아래 초기 조사보다 최신이다. `src/roster60-routes.ts`를 `src/my.ts`의 실제 signed-session/guard 경계에 등록했다. `/my/api/native/roster60/{state,start,complete,preview,confirm}`이며 요청에 playerId/완료증거를 넣을 수 없다. `src/roster60-onboarding.ts`는 Prisma의 실제 트랜잭션, 계정별 고유 시작/선택 영수증, 조건부 소비와 영구 지급을 사용한다. `prisma/schema.prisma`의 Roster60State/Roster60Owned는 옛 개체 ID를 덮어쓰지 않는다. 수동 검토 SQL은 `prisma/manual/20261024_roster60_onboarding_additive.sql`이며 실제 서버에는 실행하지 않았다.

튜토리얼 메뉴 MainActivity.tutorial은 새 서비스의 start를 호출하며, NativeNews에서 roster60 우편을 누르면 7종 미리보기→상세정보→최종 확인으로 이어진다. 취소는 confirm API를 호출하지 않는다. 새 UI는 기존 폰트와 남색 패널/버튼 자산을 재사용한다. 승인된 data/roster60-approved.json이 없으면 서버 preview/confirm은503으로 차단한다. UI에 스탯/기술/적합도/방어 및 시작MP를 표시하며 fixture는 넣지 않았다.

**현재 연결 한계:** Roster60Owned는 새 시스템의 영구 저장이지만 아직 기존 편성·BattleView·전투 엔진의 입력이 아니다. `verifyRosterTutorial`은 서버 정산 전용 함수로 만들었으나 새 전투 정산 호출자는 아직 없다. 클라이언트 complete는 서버 proof가 없으면409를 반환한다. 따라서 지금 제품 튜토리얼을 끝까지 플레이하거나 선택 보상을 정상 획득하는 전체 흐름은 미완료다. 새 흐름 시작 계정은 기존 D1 지급과 임시 스타터 요청이 거부된다. 아직 새 흐름을 시작하지 않은 구 계정의 옛 코드는 남아 있으며 전면 철거 완료라고 하지 않는다.

로컬 검증: npm ci(쓰기 가능한 /workspace/npm-cache 사용), Prisma client 생성, npm run build 통과. 전체 격리 DB 테스트544/544(44파일) 통과 후 동시 시작/서로 다른 선택 경합을 추가하여 대상5/5 통과. 기존 Android 튜토리얼/스타터17개 통과, 새 MainActivity 진입·취소·중복 제출 억제 테스트1개 통과. 후속 스타일 변경 후 같은 관련18개 테스트와 assembleDebug를 다시 실행해 BUILD SUCCESSFUL(27초). 제품 패키지 org.gmm, debug서명, 개발 서버 loopback 설정이며 새 전투가 완성된 APK가 아니다. 실제 운영 DB와 실제 휴대폰에는 접근하지 않았다.

워크북 자료는 `design/roster60-review/README.md`와 부분 구조 JSON/아트 CSV에 보존했다. 부모의 전체 메시지 중 u30~u49가 잘려 스탯 원본을 임의 복원하지 않았다. 60종 이름과52개 레시피 구조는 저장했지만 미승인 수치를 approved catalog로 승격하지 않았다.

전달 상태 추가: Codex 로컬 파일을 직접 다운로드 첨부로 등록하는 도구는 현재 목록에 없음. 사용자 승인 Drive 개발 폴더의 동일 파일명 중복 없음 확인 후 공식 upload_file을 한 번 호출했으나, **호출 전 전달 계층 HTTP413:220610389bytes >104857600bytes**로 거절. Drive 신규 파일/링크 없음. 이 거절 뒤 분할/우회/재시도 없음.

현재 기준은 review/roster60-static-20261024의 3de7670 이후 로컬 작업이다. 작업 시작 시 clean이었다. 이번 턴은 원격 최신 조회를 하지 않았다. 3de7670이 원격에 없다는 직전 확인을 유지한다. 운영 적용 완료가 아니다.

## APK 전달 재시도

사용자의 새 명시적 요청으로 공식 Library 대용량 helper를 현재 스킬에서 다시 받아 **한 번** 실행했다. tools/list 연결에서 URLError/OSError, HTTP403, exit1. prepare/upload/finalize 이전에 중단되어 신규 업로드는 시작되지 않았다. Library ID 없음. 프록시와 계정 권한 중 어느 계층이 거절했는지는 확정할 수 없다. 다른 경로나 GitHub로 우회하지 않았다.

보존 APK: /workspace/combat-v2-offline-apk-3de7670/galmaegiman-offline-vector-review-3de7670-debug.apk

220610389 bytes. 재확인 SHA256: 1ab03ed02c2fd029d54b483b2d7b11ed4df964475c4d21b24ca038a96768ef6d

이 APK는 새 PNG가 없는 벡터 대체 오프라인 검토판이다. 제품 통합 APK가 아니다. 이번 턴 재빌드하지 않았다.

## 실제 연결 대상

| 영역 | 제품 파일과 현재 차이 |
|---|---|
| 인증 및 전투 | src/native.ts의 route 인증/계정 직렬화, battle/start/current/turn, 완료 정산; src/battle/service-factory.ts와 engine.ts/core.ts/rules.ts. 현재 옛 10속성·개체 속성 기반. 새 엔진을 이 인증/정산 경계에 붙여야 함. review HTTP/오프라인 엔진은 제품 인증 경로가 아님. |
| 전투 버전 | src/battle/combat-epoch.ts, public-dto.ts, replay.ts 및 Android BattleReplayStore/Timeline/NativeBattleReplay. 새 epoch와 balance 버전별 리플레이 필요. 기존 세션을 새 규칙으로 해석하지 않음. |
| 성장/사용 가능 개체 | src/game.ts webBattleUnits와 편성 저장의 topIds 필터, src/battle/growth.ts UnitRecord 및 slotSkills. 커먼/언커먼 포함 새 정의로 교체 필요. |
| 조합 | src/game.ts chooseMaterials/craft/webCraftInfo/webCraft. 재료 상태 재검증→소비→grant는 같은 webRun 트랜잭션. 현재 XP 평균 없음. preview도 같은 선택 재료와 성장 계산을 써야 함. 중복 재료ID·동시 소비·결과 생성 실패 롤백 검사 필요. |
| 온보딩 | src/game.ts webTutorialSet: 단계2 통과 시 D1 지급, skip시 D1 편성, 임시 스타터 eligible 설정. src/my.ts tutorial 라우트. src/starter-loans.ts와 starter-loan-levels.ts. 새 u01 지급/우편 선택권 완료영수증으로 함께 전환해야 함. |
| 저장 | prisma/schema.prisma OwnedCharacter, StorySave, PlayerItem, ProgressionReceipt. 새 누적XP·티켓·완료/선택 영수증의 원자 저장 설계 필요. 실제 DB 변경 없음. 인증/결제 테이블 삭제 대상 아님. |
| Android | MainActivity.battle→BattleView, NativeTutorialController, NativeStarterLoans, NativeWorkbench 및 우편 화면. debug CombatV2ReviewActivity 단독 진입점으로 제품 통합을 대신하지 않음. |
| 패키징 | app/android/app/build.gradle. 제품 패키지는 명시적 gmmApplicationId=org.gmm. 기존 상위 자산은 아직 BattleView 의존이 있으므로 화면 전환 후 참조 분석과 함께 제외. 공유 효과는 보존. |

## 이번 구현

src/battle/roster60-rules.ts에 제품용 공통 계산/데이터 계약을 추가했다. review fixture를 import하지 않는다. 8기술속성, 6기술 한도, 개체 자체속성 거부, 완전한 적합도/방어 프로필 검증, 명시적 MP 성장표, 시작MP 충전, 집중25% 내림/상한, MP소비, 방어/적합도 곱셈과 반사 단일 타깃, 누적XP평균→레벨 계산을 포함한다.

반사 상세 계산은 필수 주입 정책으로 남겼으며 미지정 시 거부한다. 스탯/성장곡선/기술비용/반사 공식을 제품 기본값으로 만들어 넣지 않았다. XP 합은 BigInt로 계산해 큰 입력의 합산 오차를 막고 레벨 상한에서도 누적 XP를 보존한다. 집중은 정수 내림, 최대MP<4이면 회복0이다. 새 증강체 효과는 추가하지 않았다.

**아직 제품 라우트에서 이 모듈을 호출하지 않는다.** 이것은 제품 통합의 첫 소스 단계이지 사용자가 게임에서 새 기능을 쓸 수 있다는 뜻이 아니다. D1/임시 지급을 먼저 끊어 시작 개체가 없는 반쪽 흐름을 만들지 않았다. 후속 코드에서 새 시작 지급/우편과 함께 제거해야 한다.

검증: node --test test/roster60-rules.test.mjs, 5/5 통과. 사용한 성장/비용 숫자는 테스트 산술 fixture로만 존재한다. 실제 DB/HTTP/Android 제품 E2E, TypeScript 전체 타입 검사, 새 제품 APK 빌드는 이번 단계 미실행.

## 충돌 증강체

src/battle/augment-catalog-v2.ts의 reverse(자유자재), camo(보호색)는 자기 속성 변경으로 직접 충돌한다. dual은 기존 기본/역할 공격과 추가 속성에 의존한다. focus(집중 공략)는 약점1.9배로 고정1.5와 충돌하며 새 집중 행동과 명칭도 혼동된다. weakguard/neutral/counter는 새 무효·반사까지 포함할지 정의가 필요하다. 이는 전체 증강체 감사 완료 목록이 아니며 우선 확인한 충돌이다. 임의 대체 효과를 넣지 않는다.

## 최소 입력과 다음 단계

필요한 원본: 실제 60종의 등급/기술(최대6)/기술비용/스탯/MP성장/적합도/방어 프로필, 레벨별 누적XP 및 레시피. 현재 제품 data/units.json·battle.yaml은 구체계이고 검토 fixture는 제품 데이터가 아니다. 새 원본 위치를 요청했다. 새 증강체 목록, 반사 상세 정책, 아이템·교체·도망의 새 규칙도 미확정이다. 1대1은 각 순간 활동 개체1을 뜻하며 교체 가능한 예비 편성 수까지 임의 확정하지 않는다.

1. 원본 검증→제품 content 로더와 새 전투 엔진/서비스, 인증·revision·중복요청·서버 정산/리플레이 연결 (대략1~2일).
2. 새 시작 지급/튜토리얼/우편 선택권 및 조합 누적XP를 실제 트랜잭션에 연결, 격리 DB 경쟁/롤백 테스트 (대략1~2일).
3. MainActivity/BattleView의 새 제품 화면·6명령/6기술·MP·롱프레스와 도감/편성/우편/조합 연동, 아트manifest/패키징 (대략1~2일).
4. 서버/Android 회귀·작은 가로화면·제품 debug APK 검증 (대략1일, 실기기 별도).

이는 규모 추정이며 완료 약속이 아니다. 원본/미정 규칙/이미지 차단/회귀 결과에 따라 달라진다. PNG Library403 재시도 금지 유지. 제품 소스/실제 이미지/서버 준비가 빠진 APK를 완성본으로 전달하지 않는다. 운영배포·DB삭제·원격병합·Play게시·서명키 조회를 수행하지 않는다.
