# 기존 제품 UI 재사용 점검 — 2026-10-25

## 적용한 로컬 변경

사용자 최신 요청인 기존 UI 재사용에 따라 a0a3cd6의 하단 전폭 전투 조작판을 변경했다. 기존 `BattleView`의 전장 65.5% / 오른쪽 조작판 구조를 공통 상수로 사용한다. 실제 `BattleSkin`의 panel-command, panel-hp, button-attack/item/switch/surrender를 새 인증 전투 화면에도 사용한다. 원본 panel-command/panel-hp PNG를 직접 열어 확인했다. 종이색 HP 패널에는 기존 JournalTheme.INK 색을 쓴다. MP, 집중, 6명령과 최대 6기술은 새 계약을 유지한다.

이는 기존 BattleView 전체를 그대로 쓰는 것은 아니다. 구 BattleView는 구 fighters/attr/증강체/playback 계약에 결합되어 있어 그대로 전달하면 구 속성·전투 규칙을 새 데이터에 적용하게 된다. 새 API 전송과 front/rear 아트, 서버 결과 정산은 유지하고 기존 시각 자산과 전장/조작판 분할을 재사용했다. 기존 구 전투 재생 효과 전체의 새 엔진 이식은 아직 하지 않았다. 검토용 별도 Activity를 만들지 않았다.

## 모집에서 확인한 정확한 원인

- `RecruitmentScreen.select`: 기존 수량 상한 min(50, credits / cost) 뒤 새 candidate session이면 다시 min(limit,1).
- `Roster60RecruitPurchase.buy`: 새 session 경로는 times 인수를 버리고 start(kind,catalogVersion) 한 번만 요청.
- `RecruitSessions.start`: 계정별 미수령 후보 한 개만 허용. 즉 UI 제한 한 줄만 제거하면 선택 수량보다 적게 모집되는 결함이 생긴다.
- 기존 `NativeReveal.bindReassessment`: 결과 카드별 48dp 재심사 버튼이며 결과 전체 일괄 재추첨 버튼이 아니다. 하지만 지급된 소유 개체 key, 구 progression/reassess, 대기시간/구 등급 조건에 연결된다. 이 서버 규칙은 새 후보 규칙으로 복사하면 안 된다.
- 기존 NativeReveal은 카드 넘기기/결과 확인, 50회 결과 중 최대 3장 동시 뷰를 갖는다. 새로운 가로 무제한 카드 모달보다 이 컨트롤 재사용이 적합하다.

## 기존 동작에 맞는 추천 연결 계약 (아직 활성화하지 않음)

기존 수량 선택 1..50과 잔액 기반 최대 버튼을 보존하고, start 요청에 times를 전달한다. 하나의 트랜잭션에서 times × 단가를 차감하고 times개의 미수령 후보와 최초 영수증을 저장한다. 재시도는 최초 후보 배열을 반환한다. 잔액 부족/중간 생성 실패 시 전부 롤백한다. 기존 후보가 미수령 상태면 새 모집 시작 대신 해당 결과를 복원한다.

기존 카드별 버튼을 이어 각 후보마다 동급 1회 리롤(단가의 절반 1/3/5), 선택한 후보만 교체하는 방식이 추천이다. 최초 후보를 포기하고 최종 후보만 수령한다. 전체 결과 리롤은 기존 UI의 카드별 조작과 달라 추천하지 않는다. 다회 모집은 승인됐지만 이 개별 후보 리롤 범위는 부모가 아직 확인 대기라고 명시했으므로 유료 동작은 활성화하지 않았다. 가격/확률/천장/보유 개체는 변경하지 않았다.

## 검증과 한계

Java 구문 파싱 및 git diff --check 통과. Android presentation 시험을 오른쪽 조작판 위치/기존 BattleSkin 재사용을 검사하도록 갱신했다. 이 시험은 실행하지 못했다. 기존 Gradle 8.14.3 distribution이 캐시에 없고 공식 services.gradle.org DNS 조회 실패로 Android 컴파일/Robolectric 실행이 차단된 상태다. 구문 파싱은 타입 검사나 컴파일 성공을 뜻하지 않는다. 실기기 스크린샷/시안 일치/터치영역 QA 미검증이다. 서버/운영 DB 변경, push, 배포, APK 서명은 하지 않았다.
