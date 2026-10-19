# C 「선장의 모험일지」 네이티브 적용 기록

## 기준과 보존 범위

2026-10-15 첨부 `gmm-c-native-android-handoff.zip`의 `START-HERE.txt`, `DESIGN-NATIVE.txt`, `INTEGRATION-JAVA.txt`, 참고 Java/XML, 승인 시안과 시간대 비교판을 확인했다. 첨부물의 예제는 설계 참고 자료이며 실제 동작·리소스 경로·게임 규칙은 현재 저장소를 기준으로 한다. 첨부 설명의 73판 이후 작업 중인 속성 기술 다양화·조합 가치 위력·간결한 기술 카드 변경도 보존한다.

Java + Android View/Canvas, JDK 17 언어 설정, Gradle 8.14.3, API 24 이상, 가로 화면과 APK 내 이미지·글꼴 구조를 유지한다. WebView, CSS/DOM, Capacitor, 신규 위치 권한이나 시간 API를 도입하지 않는다. 시안에 있는 재화·경험치·기술 수치나 조합 재료는 게임 데이터로 옮기지 않는다.

원본 캐릭터·전투 앞뒤 스프라이트·카드·썸네일 등 438개 보호 자산은 SHA-256으로 기존 `ux73/protected-art-manifest.json`과 대조했으며 모두 동일하다. C 배경은 새 경로에 추가하며 기존 캐릭터나 UI를 배경에 합성하지 않는다.

## 확인한 현재 화면·루프·로더

- `MainActivity`가 한 Activity의 `FrameLayout` 루트에 현재 게임 화면을 교체한다. 별도 게임 Fragment 계층이 없다. 화면 이동·로그인·API 응답의 기존 흐름을 유지한다.
- `TitleView`, `LobbyView`는 ViewGroup 내 Canvas 장면과 네이티브 조작 View를 함께 사용한다. `BattleView.Scene`은 기존 전투 장소와 캐릭터, 상태를 Canvas로 그린다. 전투 조작은 네이티브 View다.
- `SceneMotion`의 기존 장면 시계를 배경 효과에도 사용한다. 새 배경 전용 프레임 루프를 만들지 않는다. `BattlePlayback`의 서버 이벤트 기반 돌진·피격·회복·합동기·교체 재생은 별도 기존 전투 기능으로 보존한다.
- `NativeArt`는 APK 자산을 우선 읽고, 원본 캐릭터의 알파 경계를 적용한다. 기존 일반 이미지 LRU·서버 확장 이미지의 디스크 캐시는 유지한다. 새 시간대 배경은 작업 스레드에서 화면에 맞춰 디코딩하며 일반 LRU에 네 장을 넣지 않는다.
- 제목용 Jua와 본문용 Gowun Dodum은 APK 안 기존 글꼴을 재사용한다. 시간에 따라 글자·양피지의 대비를 낮추지 않는다.

## 네이티브 변경 파일과 역할

경로의 기준은 `app/android/app/src/main/`이다.

| 파일 | 적용 역할 |
| --- | --- |
| `java/org/dpdns/gmm/LocalTimeTheme.java` | `Calendar`·기기 `TimeZone`으로 아침/낮/저녁/밤 판정, 다음 분 경계 지연 계산 |
| `java/org/dpdns/gmm/JournalTimeController.java` | 진입·복귀·분 경계·시각/시간대/날짜 변경 재판정, 일시정지 시 예약·receiver 해제 |
| `java/org/dpdns/gmm/MainActivity.java` | 시간 컨트롤러와 기존 화면 수명주기 연결, 조합 페이지 배경 분기, 감소 모션 설정 저장·반영 |
| `java/org/dpdns/gmm/NativeArt.java` | APK 내부 C 배경 비동기 디코딩, 현재 화면에 필요한 해상도, 오래된 요청 결과 무시 |
| `java/org/dpdns/gmm/JournalAmbient.java` | 기존 시계의 시간으로 배경·약한 물 반사·등불 표시, 약 600ms 전환과 현재/전환 대상 소유 |
| `java/org/dpdns/gmm/SceneMotion.java` | 기존 시계 재사용, 약 30fps 갱신, 복귀·미표시·감소 모션·시스템 애니메이션 설정 반영 |
| `java/org/dpdns/gmm/JournalMapBackdrop.java` | 조합(`craft`/`mix`) 화면에만 고정 지도 종이를 Canvas로 표시, 실제 조합 동작과 분리 |
| `java/org/dpdns/gmm/JournalBattleLight.java`, `BattleView.java` | 기존 실외 장소 네 곳의 배경 위·캐릭터/UI 아래에 시간대별 정적인 미세 환경광 표시, 시간 변경 listener 연결 |
| `java/org/dpdns/gmm/NativeSettingsSheet.java` | 기존 설정에 ‘움직임 줄이기’ Switch 추가, 네이티브 저장 설정과 즉시 연결 |
| `java/org/dpdns/gmm/TitleView.java`, `LobbyView.java` | C 배경과 원본 캐릭터 분리, 고정 UI·메뉴·성장 기록 유지 |
| `java/org/dpdns/gmm/JournalTheme.java`, `GameButtonDrawable.java`, `GamePanelDrawable.java`, `GamePaperDrawable.java` | 고정 양피지·잉크·목재·황동·청록 재질 및 조작부 일관성 |
| `res/values/journal_theme.xml` | 첨부 C 네이티브 색/치수 토큰 |
| `assets/native/journal/c-home-*.webp` | 아래 네 시간대 정적 배경 |

전투·조합의 수치, 보유/필요 수량, 제작 동작은 서버 응답을 사용한다. 전투 장소는 기존 `BattleBackground` 경로를 유지하며 항구 메인 배경으로 챕터·심해·실내를 일괄 대체하지 않는다. C 적용 전에 진행하던 간결한 기술 카드·기술 상세 팝업 삭제·조합 가치에 따른 위력 보정도 최신 작업으로 보존한다. 최종 검증 과정에서 수정 범위가 추가되면 Git diff와 검증 결과가 우선한다.

`MainActivity.base()`는 `craft`와 `mix`에서만 `JournalMapBackdrop`을 사용한다. 지도 격자·해안선은 고정된 Canvas 장식이며 조합 목록·재료·보유/필요 수량·제작 버튼의 정보나 클릭 영역을 바꾸지 않는다.

`JournalBattleLight`는 실외 장면 `harbor`, `market`, `strait`, `sky`의 기존 배경에만 낮은 강도의 환경광을 추가한다. 낮은 별도 색을 덧씌우지 않으며 아침·저녁·밤의 색만 배경 위에 정적으로 표시한다. `BattleView.Scene`이 시간대 변경 listener로 다시 그리며 새 애니메이션 루프는 만들지 않는다. 캐릭터와 상태·기술·로그 UI는 이 광원 위에 원래 대비로 그린다. 실내·심해 등은 기존 장소 표현을 유지한다.

‘움직임 줄이기’는 `NativeSettingsSheet`의 Switch다. `MainActivity.reducedMotion()`이 `native_display` SharedPreferences의 `reduce_motion`에 저장하고 기존 모션 설정에 반영한다. 서버 설정이나 게임 진행 데이터는 변경하지 않는다.

## APK 배경 준비 결과

제공 PNG는 캐릭터·문구·버튼이 없는 배경 후보다. RGB WebP quality 92, method 6으로 변환했다. 아침·낮·저녁은 1890×832 원본, 밤은 1889×832이므로 밤만 수평 1px를 Lanczos로 정규화했다. 최종 네 장은 모두 1890×832이며 시야를 잘라내지 않았다. 지도·배·깃발·탑·바닥의 공통 구도와 모서리, 밝은 바다/어두운 하늘의 압축 상태를 원본 및 변환본에서 직접 확인했다. 네 장의 구름·해·달·물결은 각 시간대에 맞게 다른 정적 그림이며 완전히 같은 픽셀을 색보정한 자료가 아니다.

| 파일 (`assets/native/journal/`) | 바이트 | SHA-256 |
| --- | ---: | --- |
| `c-home-morning.webp` | 480,588 | `19370be97587ed512571d137d4ca66a07163abd138a538aaf1109598191dee9a` |
| `c-home-day.webp` | 550,364 | `d1d7fe602f9d3093d3770e139c0f10cd2b6a2f946a8bb5bdfde31e308a254ed0` |
| `c-home-evening.webp` | 466,604 | `57f402db36b858828b63710483e4b2fa081043c472456ee22ad77299122fe76a` |
| `c-home-night.webp` | 464,066 | `0e0e603aa171b67ed0ce96a6810dbc39c603e49960b6b4f99a159512a05892d3` |

원본 총 11,106,781바이트에서 1,961,622바이트로 82.34% 감소했다. 디코딩 크기는 파일 용량과 다르다. 1890×832 ARGB_8888은 한 장 6,289,920바이트(5.999MiB), 전환 두 장 약 11.997MiB다. 네 장을 모두 풀해상도로 상주시킬 경우 23.994MiB이므로 피한다. 현재 화면 맞춤 디코딩에서는 이보다 작을 수 있으며 Bitmap 행 정렬·임시 디코딩 버퍼·그래픽 드라이버의 추가 메모리는 별도다.

재현 변환 스크립트는 작업 환경의 `/workspace/build-tools/prepare-journal-c-assets.py`다. 자산 SHA·크기·품질 지표와 보호 자산 대조는 `/workspace/review-artifacts/pr-2/journal-c/asset-manifest.json`에 기록했다. 이 작업은 전달 배경의 빌드용 변환이며 새로운 캐릭터/장면 생성이나 UI 합성을 하지 않았다.

## 실측 환경광 레이어

좌표 기준은 정규화 전 1890×832, 원점은 좌측 상단이다. x는 1890, y와 광원 반경은 832로 나눈다. 렌더러는 배경 cover 크롭·패럴랙스와 같은 행렬로 좌표를 변환해야 한다. 전체 화면 크기를 바로 곱하면 긴 화면/태블릿에서 광원과 수면이 어긋난다.

- 수면: 교량·말뚝·보트·바위·밧줄을 피한 두 개의 보수적인 내부 다각형이다. 정밀한 해안선 alpha matte가 아니다. 약한 반사광만 이 안에 클립하며 경계가 드러나지 않을 알파와 강도를 쓴다. 네 시간대에 좌표 검토판을 만들어 물 밖으로 흐르지 않는지 확인했다.
- 전경 등불: 불꽃 중심 `(454, 210)` → `(0.2402116, 0.2524038)`, 반경 116px → 이미지 높이의 `0.1394231`. 저녁·밤에만 낮은 강도의 주변 빛을 겹친다. 먼 건물 창과 교량 등불을 임의로 깜빡이지 않는다.
- 대표 캐릭터 발: 열린 지도 바닥의 `(1360, 637)` → `(0.7195767, 0.7656250)` 권장. 기존 `.72/.76` 계열 앵커와 가깝다. 캐릭터별 알파 경계와 하단 메뉴에 대해서는 실제 네이티브 캡처로 최종 확인한다.
- Paint·Path·Shader는 구성 시 준비하고 `onDraw` 안 파일 읽기·디코딩·프레임별 새 Bitmap 생성을 피한다.

좌표 전체는 `/workspace/review-artifacts/pr-2/journal-c/ambient-geometry.json`, 시각 검토는 `ambient-geometry-{morning,day,evening,night}.png`다. 이 JSON은 검토 자료이며 런타임 파싱을 요구하지 않는다. 같은 실측 상수를 Canvas Path에 구성한다.

## 현지 시간과 동작 원칙

아침 05:00~09:59, 낮 10:00~16:59, 저녁 17:00~19:59, 밤 20:00~04:59를 기기 현지 시간으로 고른다. `TimeZone`을 영구 캐시하지 않는다. 서버 KST 출석·보상·갱신 규칙과 게임 상태를 바꾸지 않는다. 첫 진입은 현재 시간대 배경을 바로 표시하며 이전 시간대 그림을 일부러 먼저 보여주지 않는다. 이후 배경이 준비되면 약 600ms 알파 전환을 수행한다.

시간 판정은 벽시계, 움직임은 기존 단조 시계다. 백그라운드·detach·미표시에서 움직임을 멈추고 복귀 시 지난 시간을 한 번에 밀어 넣지 않는다. 앱 움직임 줄이기나 시스템 애니메이션 비활성에서는 정적 표시를 사용한다. 배경은 터치·접근성 포커스를 받지 않으며 메뉴·텍스트·캐릭터 얼굴을 환경광 루프로 흔들지 않는다. 드래그 패럴랙스와 기존 전투 액션은 각각 기존 기능 범위를 보존한다.

## 남은 이미지 제작 및 성능 검수 범위

현재 첨부 배경은 시간대별 정적 그림 네 장이다. 물 내부 마스크와 한 개의 등불 위치는 이번에 측정했지만 다음 자료는 제공되지 않았다.

1. 투명한 구름 분리 레이어와 구름이 지나갈 때 드러나는 하늘 원판. 이를 만들기 전에는 기존 배경에서 구름을 잘라 억지로 이동시키지 않는다.
2. 밤하늘 alpha mask, 별 위치/광도, 전경 지붕·돛·해안 가림 정보. 현재 그림 속 별은 정적이며 별 반짝임을 추가했다고 보고하지 않는다.
3. 정밀 수면 alpha matte와 반사광 흐름·전경 가림 맵. 현재 보수적 내부 Path는 약한 빛에 충분하지만 광범위한 물결 변형·굴절을 위한 자료는 아니다.
4. 챕터·심해·실내·조합 전용의 시간대별 배경/환경광 마스크. 이번 항구 자산을 다른 장소에 강제로 적용하지 않는다.

순수 Java/Robolectric 검증과 화면 캡처는 실제 저사양 기기의 프레임 시간·그래픽 메모리·발열·배터리 측정을 대체하지 않는다. API 24급 저사양 기기와 Z Flip을 포함한 실제 기기에서 반복 화면 이동, 600ms 전환, 시간대 연속 변경, 백그라운드 복귀, 10분 이상 유지의 성능을 별도 확인한다. 특히 전환 시 두 Bitmap 외 디코딩 임시 메모리와 기존 캐릭터 캐시의 합산 최고치를 측정한다.

빌드/배포 전 현재 소스에 대한 Robolectric 시간 경계·시간대·DST·오프셋·수명주기·오프라인·감소 모션·원본 게임 동작 검사를 수행하고, 네 시간대 실제 네이티브 캡처와 휴대폰/태블릿 안전영역을 직접 확인한다. 실제 화면 캡처 결과 경로는 `/workspace/review-artifacts/pr-2/journal-c/final-native/`로 분리하며 위의 배경 후보·좌표 검토판을 실제 앱 캡처로 간주하지 않는다. 서명 APK와 배포의 전체 해시·소스 커밋·독립 검증은 릴리스 산출물에 별도로 기록한다.


## 최종 소스 검증 (2026-10-15)

- Android 전체 187개 중 185개 통과, 실패·오류 0개. 시안 내보내기 전용 선택 검사 2개는 실행 조건 미지정으로 제외했다. 실제 게임 View 검사는 실행했다.
- API 24·35 시간 컨트롤러와 실제 Activity의 복귀·pause·시간/시간대 방송, 분 경계, 자정, 서울/뉴욕의 같은 순간, DST, 30/45분 오프셋, 오프라인·감소 모션·설정 저장을 검사했다. 첫 정확한 배경·600ms 전환·오래된 비동기 결과·현재/대상 두 장 제한도 확인했다.
- 실제 글꼴과 Android View/Canvas·Skia 캡처 335장. 로그인 전후 시작·로그인·메인의 네 시간대는 폰·태블릿 32장이며, Z Flip 안전영역·780×360/글꼴130%의 메인·전투·조합·설정을 추가 확인했다. 별도 시안/배경 후보를 실제 캡처로 세지 않았다.
- 검토에서 작은 화면 성장 기록의 레벨·경험치 누락과 큰 빈 공간을 고치고 48dp 성장 행동을 보존했다. Canvas 글자는 scaledDensity와 FontMetrics를 사용하며 고정 행의 실제 높이/폭에 맞춘다. 황동·목재 띠를 분리하고 황동선 중심을 실제 픽셀에 맞춰 mdpi에서도 흐려지지 않게 했다.
- UI 위치·글자색·양피지 안쪽 픽셀은 시간대와 무관하게 같고, 시간 변경은 화면·게임 상태·체력·서버 요청을 바꾸지 않는다. 실제 전투 4기술·합동기와 조합 수량·제작 행동을 유지했다.
- C 적용 전 최신 전투 소스의 서버 빌드·콘텐츠 검사·격리 DB 테스트 221개도 통과했다. 보호 원본 438개의 SHA-256이 동일하다. 새 권한·외부 시간/위치 API·웹 런타임은 추가하지 않았다.

최종 캡처는 `/workspace/review-artifacts/pr-2/journal-c/final-native/`, 원본 화면 네 장을 배치한 확인판은 `/workspace/review-artifacts/pr-2/journal-c/actual-native-four-phases.png`, 검사 수치는 `/workspace/review-artifacts/pr-2/journal-c/final-verification.json`이다. 실기기 성능 측정과 추가 레이어 제작 범위는 앞 절에 명시했다.
