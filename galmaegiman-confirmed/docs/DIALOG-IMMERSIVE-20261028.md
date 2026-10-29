# 대화창 전체화면 유지 · 2026-10-28

## 확인한 원인

`MainActivity.immersive()`는 Activity의 Window에만 시스템 바 숨김을 요청했다. `GameDialog`는 별도 AlertDialog Window를 만들지만 그 Window에 전체화면 정책을 적용하지 않았다. 따라서 입력·주민 대화 등 Dialog가 포커스를 가져가면 Activity의 설정만으로 상태·탐색 바를 숨길 수 없었다.

## 수정

- `MainActivity.immersive(Window)`로 대상 Window를 명시한다. 기존 `immersive()`는 기본 창과 현재 표시된 Dialog 양쪽에 적용하며, 복귀(`onResume`)와 앱이 직접 처리하는 구성 변경에도 다시 적용한다. 기존 내 갈매미·마을·전투 복귀 훅은 유지했다.
- `GameDialog`는 표시 전·후, `OnShow`, decor attach, Window 포커스 회복에서 자기 Window에 시스템 바 숨김을 요청한다.
- `Dialog.hide()` 뒤 같은 창을 `show()`하면 decor가 계속 붙어 있고 `OnShow`도 생략되는 Android 경로가 있다. 0×0 크기의 비상호작용 decor 자식이 실제 조상 visibility 전환을 받아 이 경우에만 다시 적용한다. 시스템 바 insets 이벤트에는 연결하지 않는다. 첫 attach 전에 닫혀 detach 이벤트가 없었던 창도 대기 중인 `OnShow`에서 자기 참조만 정리하고 기본 창을 복원한다.
- decor detach에서도 기본 창을 복원하고 `visibleDialog`를 자기 창일 때만 정리한다. 기능별 `OnDismissListener`가 덮어써져도 이 정리가 유지되며, 오래된 창을 닫아 새 창의 참조를 지우지 않는다.
- 키보드 타입은 숨기지 않는다. `NOT_FOCUSABLE`·`ALT_FOCUSABLE_IM` 플래그를 추가하지 않고 기존 `SOFT_INPUT_ADJUST_RESIZE`와 EditText 포커스를 유지한다.
- 시스템 바 스와이프는 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`를 유지한다. insets 전달 시 재숨김하거나 타이머로 강제 숨기는 루프를 만들지 않는다.
- 가로 화면 크기가 바뀌면 열린 Dialog 폭을 현재 안전 영역에 맞춰 다시 제한한다.
- dismiss 뒤 같은 Dialog 인스턴스를 다시 표시할 때도 현재 창 참조와 변경된 안전 폭을 복원한다.

## 시험

`GameDialogImmersiveTest` 6개는 API35의 실제 `InsetsController`가 기록하는 Window별 요청을 확인한다. 별도 Dialog의 표시·포커스 회복, 스와이프 뒤 insets 전달이 반복 재숨김을 하지 않음, 입력창의 IME 요청·포커스·resize 정책, Back/사용자 지정 dismiss/새 Dialog 참조 보호·대기 중인 OnShow, resume/구성 변경, hide/show·dismiss/show·가로 크기 변경을 다룬다. 기존 `GameDialogActionLayoutTest` 2개는 네 가지 화면/글자 크기에서 실제 표준 버튼 영역이 잘리지 않는지 확인한다.

초기 실행에서는 immersive 6개 중 hide/show 시험 1개가 실패했고 기존 action layout 2개는 통과했다. 보완 뒤 통합 실행에서는 hide/show 자체는 통과했으나 강화한 첫 attach 전 종료 시험에서 참조 정리 누락 1개를 발견해 수정했다. dismiss 뒤 재표시 시험은 detached Window의 PendingInsetsController에 내부 메서드를 호출하던 시험 주입을 공개 `show(systemBars)`로 고쳤고 재attach 뒤 실제 요청 검증은 유지했다. 이 최종 8개 시험의 실행 결과는 부모의 통합 검증에서 추가한다. 중간 두 실행은 별도 모집/전투 피드백 소스가 편집 중인 전체 컴파일 단계에서 중단되어 이 시험의 통과·실패 근거로 사용하지 않는다. APK 빌드·설치, 실기기 상태·탐색 바 전환, 실제 키보드 애니메이션·제조사별 제스처 동작은 확인하지 않았다. 요청된 insets 정책 검증과 OS 실제 표시 검증은 구분한다.

## 전달자 초상화 연결 조사

- `speakerId`: `messenger`, 표시 이름 `전달자`(`src/story/content.ts`).
- `data/story/story-mode-v1.json`의 `STORY_08_return` 첫 대사: “덕분에 무사해. 노인이 옮겨 간 거처의 흔적을 알려 줄게.” 같은 ID가 `STORY_08_intro`에도 사용된다.
- `NativeVillageStoryOverlay.scene()`는 현재 주민 목록에 없는 ID를 빈 값으로 바꾸므로 `messenger`는 초상화가 비어 있다. `portrait()`는 `VillageLayout.assets`를 사용한다.
- manifest 경로는 `app/android/app/src/main/assets/native/village-loop-v2/manifest.json`. 현재 `messenger` 자산은 없다. 승인된 원화가 도착하면 전용 자산 항목·번들 경로와 별도 스토리 화자 허용 분기를 연결해야 한다. 자산만 추가하면 scene()의 기존 ID 초기화 때문에 표시되지 않는다.
- 이번 패치에는 새 원화, 일반 캐릭터 대체, 마을 배치·대화 UI 변경을 넣지 않았다. 참고 스크린샷 실제 픽셀을 열어 정밀 재현한 결과도 아니다.

## 적용 범위

긴급 패치와 04 내 갈매미 이후의 별도 06 패치다. `GameDialog.java` 전체 변경, `MainActivity.java` 전체화면 관련 부분, 신규 테스트·이 문서만 포함한다. 서버 API·배포는 필요하지 않다. 기존 패치의 MainActivity 내 갈매미 훅을 포함해 재배포하지 않는다.
