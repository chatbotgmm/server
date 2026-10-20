# 버튼 리소스 로더 인계

> 최신 화면 연결 상태와 Claude 최종 지시문은 [motion-screen-integration-20261020.md](motion-screen-integration-20261020.md)를 우선 참고하세요. 아래 내용은 초기 유틸리티 단계 기록입니다.
## 입력과 범위

- 작업 브랜치 `feature/ui-motion-resources-20261020`. 조합 작업대 `d9f2375`를 보존한다.
- Library ZIP 다운로드는 소비 환경 프록시 403으로 실패했다. 부모가 직접 업로드한 `1398cc9b045a4e77933f939f12294c5e721db647`를 정상 Git fetch로 받아 실제 24개 PNG 및 runtime.json/README를 확인했다. ZIP을 로컬에 받았다고 주장하지 않는다.
- 파일 위치: `app/android/app/src/main/assets/native/ui-motion-v1/`. Android AssetManager 경로는 `native/ui-motion-v1/`이다.
- 이번 추가: `UiMotionAssets.java`, `button-roles.json`, 정적 검증기/오류 탐지 테스트, 이 인계와 review 지침. 기존 화면/이벤트/메뉴·우편함·SDK에는 연결하지 않았다.
- 기존 PNG 바이트, runtime.json, 원본 README는 수정하지 않았다. 배경 로더나 미제공 배경 파일 참조를 추가하지 않았다.

## 실제 이미지 검토와 선택

24개 원본을 열어 버튼 6종의 normal/pressed/disabled와 아이콘 6종을 비교했다. 파란 바탕은 밝은 텍스트, 양피지는 짙은 잉크 텍스트에 적합한 후보지만 최종 한글 글꼴 대비·줄바꿈은 실기기 확인이 필요하다. 봉인 버튼 왼쪽 인장, 캡슐 오른쪽 파란 칸, 원형/사각 버튼의 넓은 투명 여백을 확인했다.

상태별 PNG의 모서리·장식·광택 위치가 미세하게 달라, 로더는 normal 한 장에 ColorMatrix로 눌림 밝기와 비활성 채도/알파를 적용한다. Drawable의 bounds, 내용 좌표, 이미지 비율은 바꾸지 않는다. 프레임 전환이나 타이머가 없으므로 모션 줄이기·백그라운드·detach에도 반복 애니메이션이 남지 않는다. runtime.json의 0.97 축소/1dp 이동은 원본 제안이며 이 로더가 실행하는 사양은 아니다.

힌트 시트는 전달된 Git 파일에 없다. 생성된 검토용 힌트는 `docs/reviews/ui-motion-v1/`에만 보관하며 production 참조를 금지한다. 정적 검증기는 production 아래 hint 이름 및 GIF를 거절한다.

## 역할 및 텍스트 분리

`button-roles.json`은 실제 기본 PNG를 가리키는 역할 제안이다. 이벤트나 내비게이션을 자동으로 연결하지 않는다.

| 종류 | 제안 역할 | 글자 |
| --- | --- | --- |
| blue_banner | 화면의 주 행동 한 개, 예: 조합하기 | 흰색 |
| sealed_banner | 봉인/내용 확인 보조 행동 | 잉크 |
| blue_round | 짧은 아이콘 중심 주 행동 | 흰색 |
| split_pill | 왼쪽 설명·오른쪽 보조 기호의 단일 행동 | 잉크 |
| parchment_square | 설정/정보 보조 아이콘 행동 | 잉크 |
| parchment_panel | 보조 행동 또는 정보 패널 | 잉크 |

모든 텍스트는 기존 Jua/GowunDodum 네이티브 TextView/Button, 아이콘은 별도 Drawable/ImageView로 배치한다. PNG에 텍스트를 합치지 않는다. split_pill은 그림만 분할돼 있으므로 두 개의 터치 영역이라고 간주하지 않는다. mail/gem은 의미만 제공하며 우편함 구현이나 화폐 변경을 뜻하지 않는다. 기존 앱의 청록 주 행동 규칙을 전역 변경하지 않는 선택형 리소스다.

## 재사용 API

`UiMotionAssets(Context)`는 runtime.json을 읽고 `button(family)`, `icon(id)`를 제공한다. 잘못된 경로·누락 파일·크기·알파 오류는 IOException으로 드러내며 빈 그림으로 대체하지 않는다. 루트 밖 경로와 reference 상태 PNG는 런타임 로더가 거절한다. 비트맵은 인스턴스별 8MiB LruCache에서 공유하지만 상태를 가진 Drawable은 매번 새로 만든다. 화면군별 로더 한 개를 재사용하고 `clearCache()`로 캐시 참조를 놓을 수 있다. Bitmap은 recycle하지 않는다.

이름과 역할은 `runtime.json`, `button-roles.json`에서 확인한다. 첫 로딩/디코드는 작업 스레드에서 수행하고 View 연결은 UI 스레드에서 한다. 아래는 명시적 사용 예제이며 기존 화면에 삽입하지 않았다.

```java
// 작업 스레드에서 준비 (IOException은 호출자가 오류로 표시/보고).
UiMotionAssets resources = new UiMotionAssets(context);
UiMotionAssets.Background background = resources.button("blue_banner");
Drawable info = resources.icon("info");

// UI 스레드: 예시 크기이며 실제 글꼴과 안전 영역으로 최종 검증.
Button button = new Button(context);
button.setText("조합하기");
button.setAllCaps(false);
button.setTextColor(0xffffffff);
button.setTextSize(16);
button.setMinimumWidth(dp(48));
button.setMinimumHeight(dp(48));
button.setBackground(background);
button.setBackgroundTintList(null);
parent.addView(button, new LinearLayout.LayoutParams(dp(240), dp(100)));
button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
  Rect bounds = new Rect(0, 0, v.getWidth(), v.getHeight());
  RectF content = background.contentBounds(bounds);
  button.setPadding((int)Math.ceil(content.left), (int)Math.ceil(content.top),
      (int)Math.ceil(bounds.right - content.right),
      (int)Math.ceil(bounds.bottom - content.bottom));
});
button.setEnabled(canAct); // 클릭 차단과 Drawable 비활성 상태 모두 적용.
button.setOnClickListener(v -> confirmAction());
// info는 별도 ImageView 등에 연결. 배경 PNG나 버튼 문구에 합성하지 않는다.
```

`contentBounds`는 원본 normalized contentInsets를 contain 비율로 변환한 **배치 초안**이다. 9-slice 경계나 텍스트가 반드시 들어간다는 보증이 아니다. 봉인/캡슐은 비대칭 여백이고 원형/사각은 가시 면적이 작으므로 단순히 모든 버튼을 48dp 그림으로 축소하지 않는다. 터치 영역 자체는 최소 48dp이며 그림 크기와 별도로 보장한다. minSdk 24의 API만 사용하며 Android 컴파일은 미실행이다.

## 검증

- `python scripts/check-ui-motion-resources.py`: 24개 PNG 통과. 경로/중복/누락/크기/RGBA/투명 배경/부분 알파/아이콘 실제 알파 경계/상태별 캔버스/내용 여백/역할/검토용 힌트 제외 검사, 파일별 SHA-256 출력.
- `python scripts/test-ui-motion-resources.py`: 7개 테스트 통과. 정상 묶음, 루트 밖 경로, 잘못된 크기, 누락 파일, RGB 대체, 참고 상태를 runtime으로 지정, 힌트 시트 혼입을 검증한다. 필요한 Python 패키지는 Pillow다.
- 원본 일부의 최대 알파는 254다. 완전 투명 픽셀과 알파 128 이상 가시 픽셀·부분 알파를 검증하며 임의로 255로 보정하지 않는다.
- UiMotionAssets.java: javalang 구문 분석 통과. `git diff --check` 통과.
- APK/Android 컴파일/실기기/UI 스크린샷/Actions/배포 미실행. 기존 버튼 전부를 교체하지 않았으며 실행 중인 네이티브 렌더링 검증으로 오해하지 말 것.

## Claude 후속 확인

사용자가 선택한 화면만 명시적으로 도입하고, 빌드 전 루트 CLAUDE.md의 디자인 검토를 따른다. 밝고 어두운 배경에서 premultiplied alpha 외곽, 한글 문구/폰트, 터치 48dp, disabled 클릭 차단, pressed 해제/취소, 아이콘 가시 크기, 긴 텍스트와 좁은 가로 화면을 확인한다. 추가 배경 리소스가 올라오면 별도 계약으로 검토하며 이 버튼 로더에 임의 경로를 연결하지 않는다.
