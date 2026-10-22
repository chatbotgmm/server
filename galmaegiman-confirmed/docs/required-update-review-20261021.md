# 필수 Play 업데이트 — 검토용 초안 (2026-10-21)

**상태: 코드/순수 정책 검사만 완료. Android SDK 전체 빌드, APK/AAB, 실제 Play 업데이트, 실제 글꼴 화면 캡처는 미검증.** 기존 사용자 승인 예외에 따라 동일 검토 브랜치에만 저장하며, 출시·병합·배포 승인이나 완료를 뜻하지 않는다.

## 적용

- 시작 시 로그인/닉네임/튜토리얼보다 먼저 확인, 앱 복귀 때 다시 확인한다.
- 설치 앱의 Play `AppUpdateManager`가 실제 제공하는 새 버전이 있으면 게임 화면/입력을 가리고 필수 업데이트 화면을 표시한다. 하드코딩된 마켓 버전, 서버 최신 버전, 배포 예정 버전은 쓰지 않는다.
- 지원하면 즉시 업데이트를 시작한다. 지원하지 않으면 해당 설치의 `getPackageName()`으로 Play 스토어를 연다. 안내에는 업데이트/스토어/다시 확인/게임 종료만 있고 건너뛰기는 없다.
- 취소·실패·성공 result만으로 해제하지 않고 새 Play 정보로 재확인한다. 진행 중 immediate update는 복귀 시 다시 연결한다. 매 재시도는 새 `AppUpdateInfo`를 사용한다.
- 확인된 새 versionCode는 로컬에 보관한다. 취소 후 재실행 또는 일시 오프라인으로 제한을 우회하지 못한다. 해당 버전 설치 또는 Play의 업데이트 없음 확인 시 해제한다.
- 첫 확인 오류/12초 시간 초과처럼 **업데이트 존재가 확인되지 않은 경우는 허위 차단하지 않는다.** 이미 확인된 제한은 일반 네트워크 오류로 풀지 않는다. `ERROR_APP_NOT_OWNED`는 이전 계정의 확인값을 지우므로 Play 미소유/사이드로드를 무조건 잠그지 않는다. 이 기능은 서버 강제 최소버전/변조 방지 보안 장치가 아니다.
- 낮거나 같은 availableVersionCode로 더 높은 심사 빌드를 막지 않는다. 진행 중 버전 정보가 누락되어도 이번 프로세스의 gate는 유지하되 가짜 versionCode를 저장하지 않는다.
- 로그인/알림 딥링크와 진행 로딩은 gate 뒤로 지연한다. 기존 비동기 응답·게임 Dialog가 gate 위에 플레이 UI를 띄우지 못하게 방어했다. 단순 확인 중 게임 Dialog는 숨겼다가 제한이 없으면 복원하고, 새 버전 확인 시 닫는다. paused Activity에서는 업데이트/스토어/로그인을 새로 열지 않는다.
- 기존 applicationId, versionCode, org.gmm 빌드 속성, 인증/서버 프로토콜, 권한, 배포 설정은 변경하지 않았다. 라이브러리 의존성 `com.google.android.play:app-update:2.1.0`만 추가했다.

## 검사 및 검토에서 고친 것

- 순수 Java 정책 검사 **18개 PASS**: 최초 확인/최신/복귀/새 버전/취소·실패/오프라인/재시작/설치 완료/제공 철회/미소유/진행 중/재시도/이전 버전/상위 심사 빌드/버전 없는 진행 중.
- 네이티브 연결 **15개 정적 검사 PASS**. 로그인 callback/gate wiring 확인이며 실제 Android 통합 실행을 대신하지 않는다.
- 변경 Java 5개 파일 javac parser 문법 검사 PASS. Android/Play 의존성 타입 컴파일은 미실행.
- 독립 코드 검토에서 백그라운드 콜백 실행, 별도 게임 Dialog Window, 이전 Play 소유권 제한 문제를 발견하여 보완했다.
- 새 화면은 게임 글꼴·종이색·버튼색을 사용하고 높이가 낮으면 전체 내용 스크롤이 가능하도록 구성했다. **실제 잘림/글꼴 검수 통과 주장은 하지 않는다.**

재실행: 저장소의 `galmaegiman-confirmed` 기준 `python3 scripts/check-required-update.py` (JDK compiler module 필요).

## 빌드 담당자/실기기 검증 필요

1. 실제 출시 패키지 `org.gmm`, 기존 서명, 올바른 증가 versionCode로 의존성 해석 및 debug/release 타입 컴파일. 기존 빌드 property를 유지한다.
2. Play 내부 테스트 계정으로 낮은 버전을 Play에서 취득한 뒤 더 높은 버전을 해당 계정에 제공한다. 출시 전 모든 사용자에게 제한이 생긴다고 가정하지 않는다.
3. 새 버전 없음, 새 버전 있음, 즉시 업데이트 취소·완료·중단 복귀, 지원하지 않는 immediate, 스토어 왕복, 오프라인 처음 실행/확인 후 실행, 미소유, 설치 후 재실행을 검사한다.
4. 확인 중 로그인·심사 딥링크/알림, 이전 요청 응답·Dialog, 재시도 연타, Back/바깥 터치, 화면 재생성, 12초 이후 지연 응답을 검사한다.
5. API24 실제 환경과 최신 Android, 가로 915×412·1280×720 및 친구의 작은 가로 화면/노치/큰 글꼴에서 실제 게임 글꼴 캡처를 수행한다. 필수 버튼·문장이 잘리거나 system bar에 가려지지 않아야 한다.
6. 이 코드가 없는 구버전 설치에 기능이 소급 추가되지는 않는다. 우선 이 기능을 포함한 앱 업데이트가 필요하다.

## 공식 확인 근거

- [Android Java/Kotlin in-app update 가이드](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java)
- [AppUpdateManager API](https://developer.android.com/reference/com/google/android/play/core/appupdate/AppUpdateManager)
- [InstallErrorCode](https://developer.android.com/reference/com/google/android/play/core/install/model/InstallErrorCode)

위 공식 문서를 2026-10-21 확인했다. 새 Play 권한/계정/서버 배포는 요구하지 않았으며 마켓 게시, CI 실행, production 병합은 수행하지 않는다.
