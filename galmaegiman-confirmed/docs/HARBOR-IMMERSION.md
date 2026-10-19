# 항구 환경음과 수면 움직임

77판은 작은 수면 굴절을 바다·파도·포말·원본 구조물 레이어로 교체했다. 최신 구현·검증·실제 움직임은 [수면 레이어 분리](LAYERED-HARBOR.md)에 정리한다. 아래는76판까지의 환경음과 초기 수면 구현 기록이다.

## 실제 앱 변경

`NativeSound.java`의 `title` 장면을 APK 안의 `native/harbor-ambience.ogg`로 연결했다. 메인·타이틀·일반 메뉴에서 8bit 음악 대신 실제 파도·가까운 잔물결·멀리 갈매기 녹음을 재생한다. 전투·보스·스토리 음악과 클릭·전투 효과음 경로는 유지한다.

상시 환경음은 기존 배경음(`music`) 볼륨·음소거를 따른다. 음소거 시 디코더를 해제하고 해제하면 보관한 볼륨으로 돌아온다. 준비 중 화면 변경·음소거·중지 뒤의 오래된 재생 콜백과 미디어 오류도 처리한다. 앱 백그라운드 중지와 오디오 포커스의 중단·복귀·작은 소리 전환을 유지한다. 새 설정이나 권한, 원격 음원 다운로드는 추가하지 않는다.

54초 스테레오 루프는 약703KB다. 파도·잔물결의 순환 겹침과 드문 갈매기 4회로 구성했다. CC0 원출처·고정 배포 커밋·추가 편집 기록은 함께 번들한 `native/harbor-ambience-sources.txt`에 있다. 원본 `title.wav`와 기존 게임 음악은 보존했다.

## 수면 렌더링

`JournalWater.java`를 `JournalAmbient.java`에 통합했다. 기존 `SceneMotion` 시계로 현재 수면 이미지를 작은 망으로 굴절하고, 원근에 따라 마루·그늘·반사광·끊긴 포말의 크기와 속도를 달리한다. 지도·성벽·다리·등대·배·기둥·밧줄과 캐릭터·UI는 수면 마스크에서 제외한다.

고정36×52망에서 실제 물 안의 꼭짓점42개만 갱신한다. 이미지 디코딩·새 비트맵 캐시·추가 프레임 루프는 없다. 기존 약30fps 갱신과 현재/전환 대상 두 장, 600ms 시간대 전환을 유지한다. 백그라운드·미표시·움직임 줄이기·시스템 애니메이션 비활성에서는 정적으로 표시한다.

실제 원본을 확대 검토하면서 먼 부표·배·바위가 포함된 작은 마스크6개를 제외했다. Skia 검사에서 드러난 마스크 경계 번짐은 정수 픽셀 경계로 정규화해 고쳤고, 전환 끝점에는 대상 망만 그려 이전 색 잔류를 없앴다.

## 검증과 검토 자료

`NativeHarborAudioTest`는 API24·35에서 로컬 재생·단일 반복·볼륨 복원·개별/전체 음소거·준비 콜백 취소·화면 변경·백그라운드·오디오 포커스·실패 복구를 검사한다. `JournalWaterTest`는 실제 Skia로 물 밖 픽셀 불변, 정적 화면 동일성, 전환 끝점 일치, 망 재사용과 구조물 좌표 제외를 확인한다. 기존 `JournalAmbientTest`, `JournalActivityLifecycleTest`, 실제 네 시간대와 오프라인 복귀 화면 검사도 사용한다.

`HarborImmersionReviewTest`는 실제 검토 서버 상태로 폰·태블릿 낮/밤 로비를 렌더하고 버튼·본문 픽셀과 게임 상태를 비교한다. 기존 보상 점의 맥박은 별도 허용 영역으로 둔다. 최초 Canvas에서 요청되는 출석 아이콘과 보상 조회까지 준비한 뒤 비교한다. 폰의 낮·밤 각6초 프레임은 영상 미리보기에도 사용한다.

초기 검토 자료 위치는 `/workspace/review-artifacts/pr-2/harbor-immersion/`다. 이후 사용자가 G를 선택해 76판의 제품 `TitleView`에 G 구도를 적용했다. A~G 비교 장면 자체는 테스트 전용으로 유지하고 APK에 넣지 않는다. 76판 실제 화면·최종 검증은 `TITLE-G76.md`에 정리한다.

최종 집중 검사32개가 모두 통과했다(실패·오류·제외0). `verified-suite-results/`에는 제품 검사31개와 준비 시점 보완 후 통과한 실제 로비 캡처1개의 최종 XML을 보존한다. `verification.json`에 실행 구분, 변경 소스 해시, 원본 자산137개 보존, 기존 A~D PNG 동일성을 기록했다. 낮/밤×폰/태블릿 t0·t2 캡처8장, 폰 낮·밤 총180프레임을 확보했다. `main-harbor-preview.mp4`는 실제 낮6초+밤6초의15fps 미리보기이며, 배경음은 기본70% 볼륨과 같은 gain을 적용했다. H264를 위해 오른쪽에만1px 패딩을 더했다.

| 현지 시간대 | 실제 폰 로비 | 실제 태블릿 로비 |
| --- | --- | --- |
| 아침 | [915×412](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-915-home-morning.png) | [1280×720](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-1280-home-morning.png) |
| 낮 | [915×412](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-915-home-day.png) | [1280×720](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-1280-home-day.png) |
| 저녁 | [915×412](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-915-home-evening.png) | [1280×720](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-1280-home-evening.png) |
| 밤 | [915×412](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-915-home-night.png) | [1280×720](/workspace/review-artifacts/pr-2/harbor-immersion/native/journal-c-1280-home-night.png) |

실기기 스피커·이어폰에서 환경음과 조작 소리의 균형, 기종별 GPU 프레임 시간·발열 검수는 남아 있다. 캡처 프레임의 오프라인 렌더 시간은 실기기 성능 수치로 해석하지 않는다.

이번에는 기존 네 시간대 배경과 원본 캐릭터를 사용해 새 이미지 파일 제작은 필요하지 않았다. 구름·별빛은 배경에 있는 정지 그림을 유지하며, 별도 움직임용 분리 레이어는 아직 준비하지 않았다. 파도는 새 이미지 없이 수면 마스크·망·Canvas 경로로 구현했다.
