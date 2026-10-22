# 조합 네이비 작업대 독립 소스 검토

검토일: 2026-10-21. 대상: `NativeEconomy.before.java` 대비 `NativeEconomy.java`, `NavyWorkbenchTheme.java`.

## 검토 중 발견·전달한 문제

1. **ScrollView 콘텐츠 높이 보장**: LayoutParams 높이만 설정하면 ScrollView의 UNSPECIFIED 측정에서 weighted 자식 크기가 무너질 위험. 구현자가 `NavyWorkbenchRow.onMeasure`에서 `contentHeight` EXACTLY 측정하도록 수정한 것을 재확인함.
2. **시안 글꼴 덮어쓰기**: 제거 배지에 Noto를 지정한 직후 `h.headline`으로 덮어쓰던 구문. 구현자가 제거함.
3. **1개 재료의 숨은 단서**: 새로운 빈 결과가 1개 선택된 hiddenHint를 버리던 동작. 구현자에게 기존 HiddenRecipeFrame으로 안내를 감싸도록 요청함. 재검증 필요.
4. **큰 글씨의 빈 결과 높이**: 최소720dp 폭/fontScale2이면 결과 안내가 여러 줄로 늘어 420dp 콘텐츠 높이에서 넘칠 위험. 동적 최소 높이 확장 또는 빈 안내 스크롤 요청함. 네이티브 실측 필요.
5. **외부 가로 스크롤과 카드 스와이프**: 고밀도 화면에서 부모 스크롤이 먼저 MOVE를 가로챌 수 있음. 버튼 탐색은 유지되나 제스처 우선순위는 실기기 QA 필요.

## 실제 수행한 검증

- JDK javac parser로 세 Java 파일 문법 검사 통과(컴파일/타입 확인 아님).
- `check-navy-behavior-review.py`: mixUnit, mixResult, confirmMix, performCraft, acquisitionOrigin, materialGuide의 원문이 이전 버전과 동일함을 확인. 기존 거래/조회/귀환 동작 수정 없음.
- 클릭 tag/동작 소유자를 48dp 이상 전체 재료 cell로 옮긴 소스 확인.
- Android SDK android.jar, 실행 가능한 Gradle 도구를 찾지 못함. **Android build, Robolectric 실행, API24 기기, 실제 글꼴 캡처는 검증하지 못함.**

## 추가한 실행 대기 테스트

`NativeCombinationWorkbenchTest.java`에 기존 거래/중복 방지/비밀 단서/모드 귀환 테스트를 유지하면서 다음을 추가·갱신함.

- 915×412,1080×444 등 6개 해상도/밀도 × fontScale1/1.3/2.
- 스크롤 후 버튼 전체가 실제 visible rect 안에 도달하는지, 모든 동작 타깃48dp, 글자 가로·세로 잘림/말줄임 검사.
- 공개 결과와 숨은 단서2개가 함께 표시되는 경우 보존.
- 재료1개 시작 상태는 가짜 카드2장이 아닌 의미있는 빈 안내1개, 조합비활성.
- 단서재료1개에서도 hiddenHint 보존 테스트.
- API24 LEGACY 그래픽 테스트에서 번들 font 로드, 결과모드 전환, 재료추가/삭제, 글로벌 headline/prose 불변 검사.

이 문서는 소스/문법 검토 결과이며 화면 또는 APK 품질 합격 판정이 아니다.

## 후속 재검토

- 문제1·2 수정 소스 확인.
- 문제3: 한 개 hiddenHint일 때 `HiddenRecipeFrame(h, true)` 안에 의미 있는 빈 안내를 넣어 단서가 유지되는 소스 확인.
- 문제4: 빈 안내 자체가 fillViewport ScrollView + wrap-content 자식이 되어 큰 글씨 내용에 접근할 수 있는 소스 확인.
- 문제5: carousel DOWN에서 부모 intercept를 금지하고 수직 판정/UP/CANCEL에서 해제하는 소스 확인. 실제 터치 라우팅은 기기 QA가 남음.
- 헤더 제목/뒤로가기 높이가 fontMetrics에 맞게 확장되는 후속 수정도 확인.
- 최종 소스 문법3개 통과, 원문 동일 거래/조회메서드6개, geometry288개(해상도·밀도·insets·fontscale·서버슬롯수 조합) 통과.
- 산술은 View 측정/글자 래스터의 대체물이 아님. NativeCombinationWorkbenchTest의 실제 실행과 정해진 디자인 게이트는 여전히 미완료.

## 최종 승인 변경 재검토 (2026-10-21 13:19 UTC)

최종 시안은 `combination-existing-theme-refined.png`로 변경됨. 앞선 평면 네이비 통합패널 시안보다 **기존 작업실 배경·질감 유지, 금장 반복 축소**가 우선한다.

- 원본 `ThemeArt.image()`/`ThemeArt.file()`는 같은 package에서 접근 가능한 Bitmap/String 반환 메서드임. `h.art.alpha()`도 Rect 반환이며 알파12 초과 바운드 탐색은 시안 JS와 일치함.
- quiet Drawable의 중앙 crop은 원본 alpha bounds 기준20/29/60/42%와 일치(네이티브 정수 반올림은 최대1px 차이). drawBitmap destination이 둥근 clip 내부라 원본 바깥 금장 프레임을 다시 그리지 않음.
- Paint/Path/Drawable alpha는 인스턴스별 소유. disabled/pressed/default가 각각 별도 Drawable이므로 공유 alpha 누수 없음. Canvas save/restore와 stroke 상태 초기화 확인.
- 페이지 투명, HEADER 중앙 질감, 선택재료/결과 NAVY 중앙 질감, 종이 버튼과 주요추가 버튼 원본금장 매핑이 최종 시안 방향과 일치. 실제 네이티브 픽셀일치는 미검증.
- 기존1dp급 divider를 8dp 간격2개로 교체한 것을 반영해 geometry script 차감폭을16dp로 수정. 288조합 재통과, 핵심6메서드 원문동일, Java AST3개 재통과.
- 추가 버튼은 StateListDrawable을 원본 ThemeArt 배경으로 덮어쓰므로 비활성 상태에서 배경 alpha 변화는 없고 글자색만 변함. 동작은 setEnabled로 차단되나 비활성 시각표현 QA 권장.
