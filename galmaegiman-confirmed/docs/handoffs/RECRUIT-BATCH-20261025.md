# 다회 모집 연결 — 2026-10-25

## 실제 제품 경로

기존 RecruitmentScreen의 모집소 배경/트레이/등급 선택/수량 ±/최대/총비용/최초 모집 확인을 보존했다. 후보 회차라고 수량을 1로 잘라내던 조건을 제거하고 Roster60RecruitPurchase가 실제 선택 times를 인증 API에 전달한다. 범위는 기존 상한 50과 보유 credits/단가로 제한되며 서버가 다시 검증한다. 기존 가격/확률 JSON은 변경하지 않았다.

후보 결과는 기존 NativeRecruitSession과 RecruitCardTransition/RegistrationV10Art를 확장했다. 카드 넘기기, 후보별 새로고침/수령, 남은 후보 모두 수령, 모집소 복귀, 지난 결과 다시 보기로 연결했다. 등록증 파쇄→교체 연출은 기존 컴포넌트 그대로다. 한 번에 카드 한 장을 표시하며 50개 비트맵 뷰를 동시에 만들지 않는다. 새 검토 Activity나 별도 게임을 만들지 않았다. 구 NativeReveal의 지급 후 재심사/구 소유키/구 등급 제한은 사용하지 않는다. 기존 결과 화면 전체를 픽셀 단위로 이식한 것은 아니며 실기기 시각 검증은 남아 있다.

## 서버 계약

- POST native/roster60/recruit/session/start: 기존 requestId/kind/catalogVersion + optional times(정수1..50, 생략1).
- 결과는 기존 단일 session 필드도 유지하며 sessions 배열을 추가. 각 후보에 batchId/batchIndex/batchSize. 기존 단일 요청 fingerprint를 유지하여 과거 미확인 요청 재전송도 중복 차감하지 않는다.
- 한 트랜잭션에서 총비용 차감, 모든 후보 생성, 영수증 저장. 생성 도중 실패하면 전부 롤백. 미수령 후보가 남아 있으면 새 모집 차단.
- 기존 action reroll/claim: sessionId/revision을 기준으로 후보 하나만 처리. 리롤은 동일 등급 풀에서 한 번, 비용1/3/5. 최초 후보 기록과 최종 후보 분리, 기존 소유 개체 삭제 없음.
- POST native/roster60/recruit/session/claim-all: requestId, sessions:[{sessionId,revision}]. 1..50개 동일 배치/본인 소유/미수령 여부 검증. 소유 개체 생성과 상태/영수증을 원자적으로 저장. 한 후보라도 stale이면 전체 롤백.
- GET session 및 POST read로 미수령/개별 최신 상태 복구. 앱의 불명확한 요청은 prefs에 남겨 같은 requestId 재시도. 화면 이탈 성공도 결과 저장. 높은 revision을 낮은 과거 영수증으로 덮지 않는다.
- 기존 메뉴 잠금 정규식이 신규 하위 route도 차단. 심사용 로그인에도 동일 게임 기능 claim-all만 허용하며 관리자/계정 권한은 추가하지 않았다.
- DB 스키마 변경 없음. 기존 snapshot JSON 확장만 사용한다.

## 검증

격리 DB 시험에 다회 원자적 생성, 중간 생성 실패 롤백, 동일/상이 요청 동시성, 수량 악의 입력, 잔액 부족, 50개 경계, 후보별 동급 리롤, 개별/일괄 수령, 재접속, 다른 계정 거절, 늦은 stale 롤백, 수령/리롤 경합, 실제 인증 API와 계정별 메뉴 잠금 시험을 추가했다.

Android 시험에는 실제 기존 모집소 수량 전달, 카드별 리롤, 화면 이탈 중 일괄 수령 결과 저장, 재진입, 과거 revision 재적용 방지를 추가했다. Java 구문 검사만 실행 가능했다. Gradle 배포본 미설치 및 공식 services.gradle.org DNS 실패 때문에 Android 컴파일/Robolectric 실행은 미실행이며 실기기 화면·터치 검증도 미완료다. 서버 시험 성공과 앱 실행 검증을 혼동하지 않는다.

운영 재화 소모/배포/원격 push/DB 초기화/APK 서명 없음.

최종 실행 결과: `npm run build` 통과. `npm run test:db` 66파일 / 687시험 모두 통과(85.80초, 격리 DB `/tmp/galmaegiman-tests-q8tCKT/test.db`). 모집·잠금 집중시험 15개 통과. 최초 회귀의 1실패는 새 시험용 secret 길이가 서버 최소32자보다 짧았던 fixture 오류였으며 fixture 수정 후 전체를 다시 통과했다. Android 변경/시험 6파일 구문 파싱 및 diff 검사 통과, Android 실행시험은 미실행.
