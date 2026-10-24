# 모집 후보 새로고침 — 구현 및 활성화 대기

## 확인한 기존 동작
- Android `MainActivity.draw()` → `NativeRoster60Library.recruit()` → `native/roster60/recruit`.
- 기존 `전송 결과 다시 확인`은 저장된 동일 requestId 재조회이며 무료. 새 모집은 별도 비용 차감.
- 서버 `src/roster60-library.ts`는 차감과 `roster60Owned.create`를 바로 처리한다. 후보/리롤 단계가 없다.
- 구 `RecruitmentScreen.confirmDraw`/`NativeEconomy.performDraw`도 모집이고, `src/game.ts`의 구 웹 draw는 새 로스터 계정을 거절한다. 증강체 reroll은 다른 기능으로 변경하지 않음.
- `credits`는 지원서/뽑기권으로 표시되던 기존 재화. 모집권이라는 별도 인벤토리는 만들지 않음.

## 최신 확정 계약
미수령 후보 한 개를 새 모집 세션에 저장 → 수령 또는 1회 새로고침 → 마지막 후보만 한 번 지급.
새로고침은 확인 모달 없이 즉시 요청, 해당 회차 모집 가격의 절반 차감, 최초 후보와 같은 등급 풀만 사용.
동일 개체 재등장은 배제하지 않음. 첫 후보는 감사용 snapshot.original로 남고 새 후보는 snapshot.candidate로 구분.
기존 영구 지급 개체/영수증은 변환·삭제하지 않음. 기존 조합/보유 목록/성능은 변경하지 않음.

## 실제 연결
- 신규 인증 API GET recruit/session, POST recruit/session/start|action|read. 기존 인증/guard 그대로.
- 서버 세션 행에 소유자, revision, PENDING/CLAIMED, 원 후보/현재 후보, 동급 풀과 지급 XP snapshot 저장.
- 기존 계정별 요청 영수증 테이블에 candidate-v1 fingerprint로 별도 구분. 다른 payload 동일 ID 거절.
- revision CAS, 차감, 후보 변경/수령, 요청 영수증은 한 DB 트랜잭션. 실패 시 롤백.
- Android 실제 draw 진입이 새 세션 정책 또는 미수령 세션 존재 시 NativeRecruitSession을 사용.
- 가격은 서버 응답으로 표시. 네트워크 실패 시 후보 유지/동일 요청 재시도, 성공 응답은 화면 이탈해도 계정별 저장. stale는 read로 서버 최신 상태 복구.
- 새로고침이 아닌 최초 모집 확인은 보존. 기존 무료 결과 재조회와 유료 리롤은 구분.
- RecruitCardTransition은 원 View를 8개 영역으로 clipping/이동, 성공 이후만 400ms 파쇄 + 200ms 투입. 런타임 bitmap 캡처 생성 없음. 시스템 animation scale 0/앱 motion off는 즉시 교체, detach는 완료 결과 보존.
- 구 실제 지급 경로도 성공 영수증을 로컬에 먼저 저장하고 pending 제거하여 화면 이탈 시 결과 복원.

## 아직 활성화하지 않은 부분
`data/roster60-recruit-policy.json`은 의도적으로 만들지 않았다. 가격/확률/천장/홀수 비용 반올림 승인이 필요하다.
API 정책 없으면 enabled:false, 신규 start는 RECRUIT_SESSION_POLICY_PENDING. 기존 모집 동작은 유지되며 UI에 정책 준비 중 표시.
정수 짝수 비용은 정확히 절반. 홀수 + 반올림 정책 없음은 RECRUIT_ROUNDING_PENDING. ceil/floor는 주입 가능한 구현이지 승인된 선택 아님.
천장 현재 확정 안 됨: none도 명시 설정이 필요하며 현재 제품에 none을 적용하지 않음. 기존 highPity 읽기/갱신 없음.
시험 정책 LOW 2장/커먼100%/천장none은 테스트 안에서만 사용. 제품 가격/확률 아님.
중고급 활성화, 신규 모집 풀 수치, 레어 지급 레벨 미결정은 그대로. unsupported growth는 실행 거절.
DB 스키마와 검토 SQL만 작성했으며 실제 적용은 격리 /tmp 테스트DB뿐.

## 아트와 검증 한계
새 ‘방범대 등록증/GMM 공식 도장’ 파일 미전달. 기존 로스터 정면 crop과 남색 코드 프레임을 임시 후보 카드로 사용하며 새 인증도장을 흉내 내지 않았다.
기존 등록증 리소스는 구 ID 기반이어서 새 u01 등에 임의 매핑하지 않았다. 교체 효과는 View 단위라 승인 등록증 renderer로 이후 대체 가능.
Robolectric 640x320 PNG는 네이티브 View의 테스트 렌더이며 휴대폰 실제 화면/새 인증서 아트 승인 결과가 아님.
웹에는 새 후보 모집 UI가 없음; 구 웹 경로의 새 계정 차단 유지. 서버 route만 인증으로 준비.

## 검증 기록
- 격리 DB 전체 회귀: 60 파일 / 613 테스트 통과 (`/tmp/recruit-session-regression.log`).
- 이후 정책 허용 등급 검증과 추가 경계 사례 반영 후 모집 세션 집중 테스트: 8 테스트 통과 (`/tmp/recruit-session-targeted.log`).
- TypeScript/웹 `npm run build` 통과 (`/tmp/recruit-session-build.log`).
- Android 관련 Robolectric 16 테스트 및 debug Java compile 통과. 테스트 렌더는 같은 View 소스로 생성, 실기기 아님.
- 신규 authenticated route는 등록했으나 이번 신규 테스트는 서비스 실제 DB 트랜잭션 + Android mock API 계약 검사다. HTTP 인증부터 새 세션 UI까지의 실기기 E2E를 완료했다고 주장하지 않는다.
- APK 새 패키징/운영 DB migration/배포/외부 업로드/원격 push 미실행.

미리보기: `robolectric-640x320.png` (640×320, 테스트 렌더). 원래 상단 헤더는 보존하고 새 후보/버튼 영역만 남색으로 구성. 후보 프레임/문구/동작 버튼은 잘림 없이 확인. 화면의 1장/커먼 후보는 격리 fixture이며 승인 가격/확률이 아님.
