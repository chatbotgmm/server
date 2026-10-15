# G 항구의 수호자 — 76판

첫 화면의 큰 설명판을 없애고 전투에서 보는 태초의 갈매미맨(D1) 원본을 왼쪽에, 실제 글꼴의 제목과 황동 시작 버튼을 오른쪽에 배치했다. 사용자가 선택한 G의 구도와 목재·황동·잉크 분위기를 실제 Java View/Canvas에 통합했다.

## 실제 변경

- `TitleView.java`: APK의 `/duel/sprites/D1_s0_front.webp`를 비동기로 불러온다. 캐릭터 색과 비율은 보존하고 발을 고정한 미세 호흡·중앙 드래그를 유지한다. 큰 안내판은 제거했다.
- `TitleHarborArt.java`: 오른쪽 타이틀의 명암과 발 아래 환경/접촉 그림자를 그린다. 메뉴·문구·글자 밝기는 고정하며 현재 현지 시간의 배경과 주변 빛을 쓴다.
- `TitleHarborDrawable.java`: 크림 글자의 불투명 황동·금갈색 시작 버튼과 청회색 계정·설정 칩을 그린다. 가장 밝은 CTA 면에서도 글자 대비5.23:1이며 눌림과 비활성을 지원한다.
- 로그인 전 CTA는 ‘로그인’, 로그인 후에는 ‘게임 시작’이다. 실제 닉네임 계정 칩과 설정은 위쪽에 고정한다. 로그인 입력 폼은 왼쪽을 사용하므로 그 화면의 원본 D1만 오른쪽에 배치하고 중복 제목·시작 버튼을 숨긴다.
- `JournalAmbient.java`, `JournalWater.java`, `NativeSound.java`로 현지4시간 배경·수면 움직임·54초 파도/물결/갈매기 환경음을 함께 제공한다. 배경음 볼륨·음소거·이전 볼륨 복원은 기존 설정을 따른다.
- Java17·Gradle8.14.3·API24 이상·가로 화면과 같은 앱ID/서명을 유지한다. WebView·새 권한·API·게임 규칙·조합식·원본 캐릭터 파일은 추가/변경하지 않는다.

## 화면 검토

76판 실제 캡처 폴더: `/workspace/review-artifacts/pr-2/title-g76/native/`.

| 현지 시간 | 폰의 실제 G 첫 화면 | 태블릿의 실제 G 첫 화면 |
| --- | --- | --- |
| 아침 | [915×412](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-915-title-in-morning.png) | [1280×720](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-1280-title-in-morning.png) |
| 낮 | [915×412](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-915-title-in-day.png) | [1280×720](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-1280-title-in-day.png) |
| 저녁 | [915×412](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-915-title-in-evening.png) | [1280×720](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-1280-title-in-evening.png) |
| 밤 | [915×412](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-915-title-in-night.png) | [1280×720](/workspace/review-artifacts/pr-2/title-g76/native/journal-c-1280-title-in-night.png) |

로그인 전·후 시작 화면, 로그인 폼, 로비를 네 시간대·두 크기로 검토한다. 실제 전투와 Z플립1006×411의 안전 영역·고정 UI·패럴랙스도 기존 게임 흐름으로 확인한다. `TitleGTest`는 780×360·915×412·1006×411·1280×720, 글자130%, 큰 닉네임, 48dp 조작, 원본 동일성·발 접지·입력 행동·고정 버튼 픽셀을 검사한다.

환경음 출처와 파도 마스크/전환 검수에서 수정한 내용은 `HARBOR-IMMERSION.md`에 있다. 원본 이미지438개와 기존 APK 자산409개를 보존하고 환경음·출처 파일2개만 추가한다. 구름·별빛 분리 레이어, 실기기 스피커·이어폰 밸런스와 GPU 프레임 시간·발열 검수는 남아 있다.

## 최종 검증

집중 검사38개가 모두 통과했다(실패·오류·제외0). 실제 네 시간대의 시작·로그인·로비32장과 Z플립·전투·패럴랙스·단기 움직임 캡처를 확보했다. 시작/로그인26장, 로비·움직임·전투20장을 별도 검토자가 각각 직접 확인했고 root도 로그인 전후 시작·로비·전투의 필수 폰/태블릿8장을 직접 검토했다. 전투 두 화면은 조작9개·버튼 잘림0·상태창/상대 불투명 픽셀 겹침0을 확인했다.

전투 재생 검토에서 태블릿→폰 전환 뒤 가상 Window/Display가 태블릿 크기로 남아 상태창 터치 영역이30px로 측정되는 시험 환경 문제를 발견했다. 캡처 전에 실제 표시 크기를 동기화하도록 `NativeRenderReviewTest.capture()`를 보완했으며 같은5개 화면/재생 검사가 재실행 후 통과했다. 제품 전투 규칙이나 레이아웃은 변경하지 않았다. 최종 XML은 `title-g76/final-results/`, 합산 결과는 `title-g76/test-summary.json`에 보존한다. 글자130%는 실제 글꼴의 측정·글자 전체 표시·48dp·안전 영역 검사로 확인했으며 별도130% 시각 캡처 검수와 구분한다.

## 서명 빌드

76 / `2.2.2-native` release APK 빌드와 lint를 통과했다. APK는84,857,153바이트이며 SHA-256은 `abfb9886638239754a91cd0e57dd9fbe22def655124a0c88664160cdfd1894ab`다. 기존75와 앱ID·서명이 같고 API24·Java17, 운영서버 URL, 원본438개·기존409개 APK자산 보존과 신규환경음2개 포함을 확인했다. 실제 DEX에 G·수면 클래스가 있고 시험용 시안·테스트 클래스와 검토서버 URL은 포함되지 않는다. 상세 아티팩트 검증은 `/workspace/review-artifacts/pr-2/release76/apk-verification.json`에 있다.
