# 화면 연결 결과 및 Claude 최종 빌드 인계

이 문서는 이전 `ui-motion-resources-20261020.md`, `background-motion-20261020.md`의 **미연결 상태 설명을 갱신**한다. 리소스 원본/runtime.json은 그대로이며 이번에는 화면 그리기 경로에 연결했다. 기반은 `d6d1b7627cf443d327f9c076a4dd5b8f1f7ccd48`, 브랜치는 `feature/ui-motion-resources-20261020`이다.

## 실제 연결한 화면

| 원본/효과 | 연결 지점 및 실제 화면 | 상태 |
| --- | --- | --- |
| bg-home / home-water | HomeArt.background → LobbyView 로비 | 수면 마스크 안 반짝임. 기존 HomeWater 굴절/안내 캐릭터 유지 |
| bg-collection / collection-lantern | ThemeArt.background → 도감 및 같은 서고 배경을 쓰는 기본 페이지 | 왼쪽 등불 효과 |
| bg-navigation-workshop / navigation-water | ThemeArt.background → NativeEconomy 조합(두 탭/결과), 탐험 | 중앙 창 수면 내부 반짝임 |
| bg-recruitment-counter / recruitment-lantern | ThemeArt.background → 모집 및 같은 원본을 쓰는 결과 | 오른쪽 탁상 등불 효과 |
| bg-settings-harbor-office / office-dust | ThemeArt.background → 계정 관리 | 햇빛 먼지. 설정 팝업은 모달 동안 효과 정지 |
| bg-market-canopy / market-water | ThemeArt.background → 상점 | 부두/기둥/벽을 제외한 수면 반짝임 |
| bg-team / team-leaf | ThemeArt.background → 팀 편성 및 같은 훈련장 배경의 페이지 | 바깥 잎 장식. UI 아래 합성 |
| battle-v1/bg-harbor / harbor-water | BattleView 전투 장면이 harbor인 경우 | 수면 반짝임 + 아래 국소 배 mesh |

첫 화면은 별도의 시간대별 JournalAmbient 원본/장면을 사용하므로 이 8종 시트를 잘못 겹치지 않았다. 첫 화면 명칭 ‘갈매미맨 방범대’와 기존 시간대/수면/캐릭터 표현을 유지한다. 위 효과는 실제 코드 연결 상태이고, APK 화면에서 확인했다는 뜻은 아니다.

## 합성·입력·생명주기

`BackgroundMotionBinding`은 Activity당 재생기 하나를 재사용한다. 기존 background draw 직후에 그 Canvas로 `drawEffect`를 호출한다. 별도 full-screen 터치 View를 addView하지 않는다. ThemeArt는 Drawable.invalidateSelf, 로비·전투는 실제 소유 View.invalidate를 사용한다. 실제 drawBitmap의 target에 원본 픽셀 Rect를 대응시켜 같은 Matrix/crop/drag를 적용한다. 매 draw의 동일 setter는 조기 반환하여 무효화 무한 루프를 피한다.

MainActivity resume/pause/window-focus/trim-memory/destroy, GameRootLayout.removeAllViews에 연결했다. 화면 교체는 요청 generation을 바꾸고 이전 시트/owner/repaint 참조를 해제한다. 오래된 비동기 완료는 폐기한다. 현재 화면의 시트 하나만 소유하고 모든 시트를 미리 로드하지 않는다. 모달이 떠 있으면 애니메이션을 멈추며 포커스 복귀 때 다시 시작한다. 모션 줄이기, 시트 실패, 메모리 경고에는 원본 정적 배경이 남는다.

기존 onInterceptTouchEvent/onTouchEvent는 수정하지 않았다. 로비는 DOWN 때 메뉴/클릭 가능한 자식을 제외하는 sceneDragStart 규칙을 유지한다. 배경·효과에서 새 터치 리스너/클릭 영역을 등록하지 않았고, 버튼 action/confirm/busy 처리는 보존한다.

## 실제 수면 픽셀 검토

원본 8종을 열어 확인했고 물 원본 4종은 원본 해상도 확대본에 경계를 표시해 별도 재검토했다. 아래는 (left,top,right,bottom), 원본 픽셀 기준이다. 이 작은 사각형들은 효과의 boundsNormalized에서 자동 유도한 것이 아니다.

| 장면 | 검토한 수면 내부 | 제외한 대상 |
| --- | --- | --- |
| navigation | 1018,370,1090,440 | 양옆 배·가운데 아래 밧줄·기둥 |
| market | 1250,410,1455,455 | 작은 배, 오른쪽 큰 배, 부두, 아래 벽/기둥 |
| harbor | 1180,400,1245,440 | 위쪽 작은 배 선체, 오른쪽 벽, 아래 기둥/밧줄 |
| home | 1280,405,1400,450 | 왼쪽 돛배, 위쪽 배, 오른쪽 기둥, 아래 밧줄 |

마스크는 원본에 붙어 움직여 viewport 변경이나 기존 드래그에서도 다른 물체로 이동하지 않는다. 경계 안에 물만 있음을 확대 이미지에서 확인했다. 매우 보수적이므로 빛이 원래 시트보다 좁게 나타날 수 있다. 물 그림 자체를 수정하거나 비수면 영역에 맥동을 주지 않는다.

## 항구 배 흔들림: 구현 범위와 제한

`HarborBoatMotion`/`HarborBoatGeometry`를 harbor 전투 배경 draw 직후 연결했다. 오른쪽 작은 배 주변의 **불투명 원본 패치** (1072,256,160,168)를 같은 자리에 drawBitmapMesh로 덮어 그린다. 안쪽 돛·선체를 포함한 plateau는 모두 같은 x/y 이동량, 테두리는 정확히 고정한다. 주기 4.8초, 원본 픽셀 최대 x 0.3/y 1.25이며 20×21 격자다. 오른쪽 위 원경 돛배가 패치에 일부 포함된 것을 확대 검토에서 찾아 해당 영역의 이동 가중치를 0으로 고정하고, 대상 배와 겹치지 않는 수면에서만 완만히 연결했다. 선박의 복제 cutout을 다른 위치에 덧붙이지 않아 이중 배/원래 배 잔상이 생기는 방식이 아니다. 원본 PNG와 건물 영역은 변경하지 않는다. 메모리/크기 오류 및 모션 끄기 시 원본만 표시한다.

실제 원본 패치에 두 극점/중립의 좌표 변환을 적용한 3배 확대 알고리즘 검토 이미지에서 돛·선체 모양 유지와 가장자리 연결을 확인했다. Android GPU 캡처가 아닌 보조 렌더링이며 실기기 동작 완료 증거로 취급하지 않는다. 순수 Java 검사에서 97시점의 가장자리 고정, 선체·돛 강체 plateau, 원경 돛배 고정, 최대 진폭, 뒤집힌 mesh 삼각형 없음을 검증했다.

제한: 주변 물 픽셀도 완만하게 보간되는 **국소 변형**이며 선박이 독립 추출된 진짜 레이어가 아니다. 모든 배·로비·첫 화면 배 흔들림으로 일반화하지 않았다. 건물은 패치 밖이고 돛은 plateau 안이지만 실기기에서 경계/돛 왜곡·중복 윤곽이 보이면 이 국소 호출을 비활성화하고 원본을 유지할 것.

## 드래그의 정확한 완료 범위

- 전투: 기존 분리 배경 0.2배, 우리 캐릭터 1배, 상대 캐릭터 0.6배, 전투 효과 0.8배 이동 유지. 이번 물빛/배 patch는 배경의 실제 target에 붙는다.
- 첫 화면: 기존 배경 0.25배와 별도 투명 캐릭터 0.9배 이동 유지.
- 로비: 배경을 가짜 다층으로 쪼개지 않았다. 기존 분리 안내 캐릭터의 발 고정 기울기/압축과 별도 종이 표현을 유지했다.
- 평면에 합쳐진 건물·배·전경 전체를 독립 이동시키는 일반 다층 parallax는 추가하지 않았다. 전체 화면 zoom/pan을 다층 구현이라고 보고하지 않는다.

## 버튼 6종·아이콘 6종 실제 적용 현황

공용 MenuKit 경유지만 매우 좁은 조건에서만 연결했다. `UiMotionUiBinding`은 비동기 디코드, epoch 확인, 원본 비율 contain, 실제 FontMetrics/문구 너비/48dp 검사 후 적용한다. 문구 변경·회전/크기 변화 때 재검사하며 안전 영역에 안 들어가면 기존 GameButtonDrawable을 유지한다. 새 배경에서도 enabled/pressed는 네이티브 Button 상태 그대로이며 single normal PNG에 코드 색상만 적용한다.

| 리소스 | 실제 적용 |
| --- | --- |
| blue_banner | 조합 작업대 `mix`의 ‘조합하기’만. 실제 문구가 들어갈 때 적용, 큰 글꼴/공간 부족 시 기존 버튼 |
| sealed_banner | 미적용. 봉인/수령 전용 역할과 비대칭 여백에 맞는 화면이 이번 범위에 없음 |
| blue_round | 미적용. 기존 48dp 컨트롤에 원형 투명 여백을 넣으면 표시 면적이 너무 작아짐 |
| split_pill | 미적용. 좌우 역할 설계 없이 기존 단일 버튼을 바꾸면 의미/내용 영역이 달라짐 |
| parchment_square | 미적용. 기존 사각 아이콘 영역에서 그림 축소·가독성 검토가 별도로 필요 |
| parchment_panel | 미적용. 기존 48dp 긴 버튼의 내용 높이를 보장하기 어려워 일괄 교체하지 않음 |
| settings 아이콘 | 계정 관리의 ‘설정’ 버튼 (`u-settings`)만. 기존 26dp compound drawable 위치/간격 유지 |
| close/info/book/gem/mail 아이콘 | 미적용. 기존 닫기·정보·도감·재화·우편 역할을 임의로 치환하지 않음. 리소스/로더는 사용 가능 |

위 미적용은 누락을 감춘 것이 아니라 기존 디자인·텍스트/터치 영역·업무 범위를 보존하기 위한 제한이다. 버튼 원본 18개 상태 PNG/아이콘 6개 모두 남아 있고, pressed/disabled 그림으로 실시간 교체하지 않는다. 선택 UI를 전면 재설계하지 않았다.

## 검사 결과

- `check-background-motion.py`: 실제 시트 8개/64프레임/원본 배경 해시/루프/좌표/테두리 alpha 통과.
- `test-background-motion.py`: 오류 탐지 9개 통과.
- `test-ui-motion-resources.py`: 버튼 리소스 오류 탐지 7개 통과.
- `test-background-motion-timeline.sh`: 실제 Java 재생기 단위 검사 및 실제 Java 항구 mesh 검사 통과. APK/Gradle을 사용하지 않는 순수 Java 검사다.
- `check-motion-integration.py`: 8개 실제 asset 경로 연결, 4개 수면 영역, lifecycle 연결, 기존 입력·깊이 규칙, 적용 버튼/아이콘 범위, 실제 Jua 16px 문구 안전 영역의 정적 검사 통과.
- 수정/신규 Java 구문 분석 및 git diff 검사 통과. 투명도·픽셀 좌표·mesh 극점은 로컬 보조 이미지로 육안 검토했다.
- APK/Gradle 빌드, Actions, 배포, EC2, DB 작업 미실행. applicationId·패키지 선언·서명·인증 설정 변경 없음.
- Android 전체 컴파일, 기기 이벤트/터치 자동화, 실제 화면 캡처, GPU blending·성능·peak memory 검증은 미실행이다.

## Claude에게 전달할 최종 지시문

> `chatbotgmm/server`의 `feature/ui-motion-resources-20261020` 최신 보고 커밋을 별도 작업 트리로 받아 주세요. 원본 수동 브랜치와 기존 Claude 작업은 덮어쓰지 마세요. 루트 CLAUDE.md 및 이 인계 문서를 읽고 원본 아트/조합 작업대/‘갈매미맨 방범대’ 제목을 보존하세요.
>
> 이번에는 APK 빌드를 맡습니다. 먼저 실제 게임 글꼴과 네이티브 화면으로 디자인 검토를 수행하세요. 780×360, 915×412, 1280×720 가로 화면에서 위 8종 효과가 배경에만 붙고 버튼·문구는 고정인지 확인하세요. 물 마스크 4곳이 배·밧줄·벽·기둥에 닿지 않는지, crop/드래그에도 원본과 일치하는지 확인하세요. 항구 배 mesh에서 돛/선체/경계 왜곡·이중 윤곽이 보이면 해당 호출을 끄고 원본을 유지하세요.
>
> 로비 메뉴 시작 드래그가 메뉴 클릭을 빼앗지 않는지, 중앙 드래그/캐릭터 이동과 버튼 터치가 충돌하지 않는지 검사하세요. pause/resume·모달 focus·뒤로가기·빠른 화면 전환·모션 끄기·메모리 경고에서 재생/디코드가 멈추고 원본 fallback이 유지되는지 확인하세요. 계정 전환/늦은 비동기 콜백도 확인하세요. 조합 ‘조합하기’의 실제 글꼴 안전 영역과 좁은 화면 fallback, 눌림/비활성, 확인창 중복 방지를 확인하세요.
>
> 위 검사와 Android 컴파일/관련 테스트를 통과한 뒤 APK를 빌드하고 결과 경로·검증 통과/미실행/제한을 보고하세요. applicationId/패키지/서명/인증 설정을 이번 작업에서 변경하지 마세요. Actions 실행, 서버/EC2/DB 변경, 운영 배포는 하지 마세요. 국소 배 mesh와 기존 분리 캐릭터 이동을 전체 선박/전경의 진짜 다층 분리 완료라고 표현하지 마세요. 코드 저장 시 `[skip ci]`를 사용하세요.
