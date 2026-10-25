# 계정별 메뉴 잠금 — 로컬 구현, 운영 미적용

## 화면/계약

| featureId | 메뉴 |
|---|---|
| recruit | 모집 (지원서 받기 포함) |
| roster | 방범대 |
| craft | 조합 |
| adventure | 탐험 |
| shop | 상점 (재화 바로가기 구매도 동일 권한) |
| party | 전투 편성 |
| daily | 출석·미션 |
| rebirth | 환생 |

`GET /my/api/native/menu-access`는 본인 권한만 반환한다. 기존 인증 세션과 `x-gmm: 1` 필요. `features[featureId]`에 `mode`, `enabled`, `reason`, `requiredLevel`이 있다. `representativeLevel`은 CONSUMED를 제외한 실제 보유 Roster60 개체의 XP/환생 곡선으로 계산한 최대 레벨이다. 보유 없음은 0, 카탈로그 불가/손상은 null과 LEVEL_DATA_UNAVAILABLE이다.

현재 계정별 force_lock=잠금, force_unlock=해제, auto=전역 테스트 기본잠금. 모든 8개 기능의 최초 상태는 auto이며 잠긴다. 해금 레벨과 레벨 하락 시 재잠금은 미정이므로 requiredLevel=null, levelPolicy/levelDecreasePolicy=PENDING. 임의 레벨 수치를 부여하지 않았다. 기능 해제는 환생 등 기존 실행 자격을 우회하지 않는다.

관리자는 `GET /my/api/admin/menu-access?playerId=...`로 대상 revision 조회 후 `POST /my/api/admin/menu-access`에 `{requestId,targetPlayerId,featureId,mode,expectedRevision}` 전송. 기존 isAdmin 권한만 사용하며 심사 세션과 일반 사용자는 거절한다. 새 관리자 계정/비밀키는 만들지 않는다. 동일 requestId/본문은 동일 영수증, 다른 본문은409, 오래된 revision은409. 변경과 감사기록/영수증은 한 트랜잭션. 잠금 상태는 AppMeta, 감사는 AuditEvent 사용. DB migration 없음.

## 실제 연결 및 경계

Production `src/server.ts`가 buildApp의 menuLocks:true를 고정 전달한다. 독립적인 기존 기능 단위시험은 옵션 없이 각 기능 정책을 검사하며 새 통합시험은 명시적으로 true로 실제 인증 API 잠금을 검사한다. 별도 서버 진입점을 추가할 경우 이 옵션을 누락하면 안 된다.

`/my/api`의 새/구 모집, 조합, 탐험, 상점, 편성, 출석, 환생 경로를 공통 preHandler에서 검사. adventureId가 있는 전투 생성 및 모험 encounter 직접 생성도 탐험 권한 검사. 우편/설정/프로필/플레이/온보딩 API는 추가로 잠그지 않는다. 플레이의 개체 선택과 실제 편성 편집을 혼동하여 전체 전투를 막지 않는다. 기존 채팅 bot 명령과 관리자 DB 직접 작업까지 막는 전역 게임 권한 시스템으로 주장하지 않는다.

Android LobbyView의 8개 메뉴는 같은 응답으로 회색/자물쇠, 탭 시 사유 안내. 조회 실패는 잠금. 홈 진입/메뉴 열기/열린 상태15초마다 갱신. UI가 일시적으로 오래된 해제 상태여도 서버가 후속 요청을 거절한다. 메뉴 외 버튼 보존. 새 Android 코드가 반영되지 않은 설치 APK는 잠금 그림을 표시하지 못할 수 있으나 새 서버의 API 거절은 적용된다.

잠금은 실행 중인 전투를 취소하거나 이미 지급한 보상을 회수하지 않는다. 이미 시작한 유료 모집 후보/모험 복귀도 해당 기능 잠금 중에는 직접 API 차단되므로 처리하려면 관리자가 그 계정 기능을 먼저 열어야 한다. 잠금 승인 전에 이미 처리에 진입한 요청을 소급 취소하는 기능은 없다.

## 운영 영향 / 검증 한계

운영 DB/배포/push/초기화 미실행. 기존 UI e29cbac/05a07fd와 OAuth 커밋 보존. 기존 잔액/보유 개체 불변. 서버를 배포하면 8개 기능은 기본 잠기므로 필요한 테스트 계정을 관리자가 개별 해제해야 한다. 레벨 자동 해금은 후속 정책 승인 전 활성화하지 않는다.

Android Gradle 배포본 캐시가 없고 기존 공식 배포 URL DNS 조회 실패가 확인되어 이 수정의 Android 컴파일/실기기 화면 검증은 아직 못 했다. 소스 변경을 시안 일치/설치 APK 완성으로 보고하지 않는다.

검증: npm run build 통과. 격리 test:db 65파일/679시험 통과(88.58초). 이후 구 story 저장의 편성 변경 우회와 재감정 경로 보완을 포함해 build 재통과 및 실제 인증 메뉴 통합시험 7개 재통과(1.46초). git diff --check 통과. Android wrapper cache에는 .part/.lck만 있고 실행 가능한 Gradle 배포본 없음. 운영 잠금 적용과 Android 실기기 검증은 미실행.
