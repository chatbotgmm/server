# 항구 수면 레이어 분리

76판의 작은 수면 굴절만으로는 파도가 밀려오는 형태를 읽기 어려웠다. 기존 수면 마스크는 전체 배경의 약2.33%이고, 폰에서는 굴절이 약1px이며 캐릭터가 그 일부를 가린다. 새 구현은 바다 질감·원근별 흐름·진행하는 파도·포말과 원본 고정 구조물을 분리한다.

이번 작업본의 버전은77 / `2.2.3-native`다.

## 자산과 렌더링

- 기존 `native/journal/c-home-{morning,day,evening,night}.webp` 네 원본과 캐릭터 파일은 보존한다. 버튼 색상 통일 요청은 철회되었으므로 버튼 소스와 팔레트는76판 그대로 둔다.
- `native/journal/ocean-surface.webp`는 물만 있는 새 이미지다. 원본 항구 배경의 회화 질감을 참고해 image_gen으로 생성했다.2172×724,295,846바이트이며 원본 PNG는 `/workspace/generated_images/exec-c95b9ad2-fa2e-4189-9189-408cb5af3d7e.png`에 보존한다. 다리·배·건물·캐릭터·UI는 이 이미지에 합치지 않는다.
- `JournalSeaMask`는 source1890×832 좌표의 물 외곽과 고정물 구멍을 관리한다. 네 시간대 원본을 대조한 좌표와 배제 근거는 검토 자료의 `water-regions.json`에 보존한다.
- `JournalScenePack`은 원본 배경, 물만 투명하게 만든 원본 구조물 crop, 공유 바다/포말 재질과 등록 좌표를 묶는다. 구조물을 재생성하지 않고 각 시간대 원본 픽셀을 추출한다. 준비 작업은 `NativeArt`의 기존 로더 스레드에서 수행한다.
- `JournalSeaLayers`는 먼 물·중경 파도·가까운 물·포말을 서로 다른 속도로 그린다. 파도 마루가 앞으로 진행하고 커졌다가 사라지며 그 아래 그늘과 끊긴 포말로 두께를 표현한다. 원본 구조물은 위에 고정하고 캐릭터와 UI는 기존 렌더링을 따른다.
- 시간대별 물색은 실제 원본 수면을 기준으로 맞춘다. UI 밝기와 위치, 서버 KST 출석·보상 주기는 바꾸지 않는다.

기존 `SceneMotion` 시계 하나를 사용하고 현재/전환 대상 두 장면만 소유한다. 바다·포말은 공유하며 Shader·행렬·경로를 준비해 재사용한다. onDraw에서 이미지 디코딩이나 비트맵을 생성하지 않는다.600ms 시간대 전환, 첫 시간대 즉시 표시, 오프라인 동작을 유지한다. 백그라운드·미표시·움직임 줄이기·시스템 애니메이션 끄기에서는 같은 레이어의 정적 화면을 표시한다.

## 검토 기준

- 네 현지 시간대의 실제 Native View/Canvas·동봉 글꼴 화면을 폰915×412·태블릿1280×720으로 캡처한다.
- 실제 바다·포말·원본 구조물 분리 이미지와 등록 좌표를 따로 저장해 가림 관계를 확인한다.
- 정수 수면 경계 밖 구조물 픽셀과 고정 UI를 보호하고, 감소 모션의 시간 불변·전환 시작/끝·원본 fallback을 검사한다.
- 긴 재생의 UV 나머지 계산과 이미지 반복 이음새, 노을의 물색을 검토한다. 이 구현에서는 X/Y MIRROR 주기와 실제 세로 배율을 함께 고려한다.
- 실제 로비의 낮/밤 각6초 프레임과76판의 같은 화면을 비교한다. 서버 게임 상태·메뉴·버튼·성장 기록은 유지한다.

실기기 GPU 프레임 시간·발열·배터리와 실제 터치 중의 감각은 별도 검수 대상이다. Robolectric의 오프라인 프레임 렌더 시간은 기기 성능으로 해석하지 않는다. 구름·별빛·배 흔들림은 이번 수면 분리의 범위에 포함하지 않는다.

## 실제 검증

최종 집중 검사34개 모두 통과했다(실패·오류·제외0). API35의 실제 Skia로 네 시간대 시작·로그인·로비32장, 폰/태블릿 수면 t0/t2 16장과 낮/밤 각90프레임을 저장했다. 필수 로그인 전후 시작·로비·전투의 폰/태블릿8장을 직접 검토했다. 원본 고정물의 화면 배율별 바깥 픽셀 변화0, 양방향 시간대 중간 합성·전환 시작/끝·원본 fallback·긴 UV 반복·움직임 줄이기·고정 UI·Z플립 안전 영역·기존 전투 재생을 확인했다. 원본438개와76판 APK 자산411개는 바이트 그대로 유지하며 새 바다 이미지1개만 추가한다.

API24 검사는 Bitmap+Gradient ComposeShader 네 밴드의 생성·배치·Canvas 호출을 확인한다. Robolectric API24 LEGACY의 Region.setPath raster 미지원 때문에 실제 수면 추출/픽셀 합성은 API35 native-Skia 검사와 구분한다. 실기기 API24 GPU 성능을 확인했다는 의미는 아니다.

검토에서 찾아 고친 내용은 반복 이음새와36/46/92초 UV 튐, 물 경계724픽셀의 축소 coverage 번짐, 시간대 중간 합성의 이중 경계, 이전 ScenePack 강참조, 포말의 갑작스러운 등장, 노을의 잘못된 물색, 전경 구조물 주변4~10sourcepx 물색 여백이다. 큰 각진 파형은 고정물 마스크 잔여로 보였으나 실제 프레임/알파 추적에서 생성 재질의 움직이는 명암면으로 확인해 둥근 파형 이미지로 교체했다.

[실제 전체 화면·환경음12초](/workspace/review-artifacts/pr-2/layered-harbor77/layered-harbor-preview.mp4) · [76판/수면 레이어 확대 비교6초](/workspace/review-artifacts/pr-2/layered-harbor77/water-before-after-detail.mp4)

| 현지 시간 | 실제 폰 로비 | 실제 태블릿 로비 |
| --- | --- | --- |
| 아침 | [915×412](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/915-morning-t2.png) | [1280×720](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/1280-morning-t2.png) |
| 낮 | [915×412](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/915-day-t2.png) | [1280×720](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/1280-day-t2.png) |
| 저녁 | [915×412](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/915-evening-t2.png) | [1280×720](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/1280-evening-t2.png) |
| 밤 | [915×412](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/915-night-t2.png) | [1280×720](/workspace/review-artifacts/pr-2/layered-harbor77/native/harbor-motion/1280-night-t2.png) |

변경 파일은 JournalScenePack/JournalSeaLayers/JournalSeaMask, 기존 NativeArt/JournalAmbient/JournalWater, 새 바다 WebP, 관련 검사와 버전 설정이다. 기존 게임 규칙·API·캐릭터·음원·버튼 소스는 변경하지 않는다.

## 서명 빌드

77 / `2.2.3-native` release APK와 lint를 통과했다. APK는85,162,071바이트이며 SHA-256은 `894c27b4c39ef9c9a283713e5c94fa14c5c620b1751fb67e1a34a5f95b8d3a69`다. 기존76과 앱ID·서명이 같고 실제 DEX에 분리 수면 클래스가 있으며 검토/시험 클래스는 포함하지 않는다. 기존411개 APK 자산을 보존하고 새 물 전용 WebP1개만 추가했다.

최종 별도 시각 검토에서도 수면16장·기본 화면8장과 낮/밤 연속프레임의 진행, 각진 명암면 완화·넓은 물색 후광 해소·구조물 보존을 확인했다. 실기기 성능 검수는 별도 범위로 유지한다. 상세 검토·서명/자산 검증은 `/workspace/review-artifacts/pr-2/layered-harbor77/final-verification.json`과 `/workspace/review-artifacts/pr-2/release77/apk-verification.json`에 있다.
