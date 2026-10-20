# 배경 효과 시트 애니메이터 인계

## 입력 및 변경 범위

`feature/ui-motion-resources-20261020`의 부모 업로드 `2e07077fa6c276879c9867b8f0adf74c64580484`를 정상 Git fetch로 받아 `native/background-motion-v1/runtime.json`과 indoor/outdoor 8개 PNG를 실제 확인했다. 각 시트는 2048×1024 RGBA, 512×512 셀 8개다. 64프레임을 회색 배경의 임시 검토 시트로 열어 등불·먼지·물 반짝임·잎 모양을 확인했다. 원본 배경 PNG와 효과 PNG, 전달된 runtime.json은 수정하지 않았다. 기존 버튼 리소스와 첫 화면 ‘갈매미맨 방범대’, 조합 작업대 변경을 보존한다.

추가한 코드는 독립 재사용 컴포넌트이며 기존 화면에 연결하지 않았다. 패키지명/applicationId/인증 설정, Gradle, SDK, 우편함 변경 없음. APK·Actions·배포 실행 없음.

## 구성

- `BackgroundMotionSpec`: 전달된 매핑에서 선택 장면만 읽고 경로, 크기, 프레임 좌표, 앵커/배치 사각형, opacity, 루프/crossfade 계약을 검증한다.
- `BackgroundMotionTimeline`: Android 독립 순수 Java 재생기. `SystemClock.elapsedRealtime()`에서 전달받은 단조 시간으로 `0..7,6..1` 14단계를 샘플링한다. 끝점 중복 없음. 프레임 마지막 crossfadeMs 구간에서 다음 순서 항목으로 전환하며 7→6 및 1→0 경계도 동일하다. resume은 0부터 재시작하고 밀린 프레임을 추격하지 않는다.
- `BackgroundMotionOverlay`: 원본 정적 배경 위/UI 아래에 배치하는 터치 불가 View. 한 인스턴스가 한 장면만 소유한다. 한 번에 한 개 decode 작업, 다음 장면 미리 읽기 없음, 완료 콜백 전에는 다음 decode를 시작하지 않는다. 장면 변경 시 이전 참조를 먼저 놓고 generation으로 늦은 결과를 폐기한다. 8MiB 시트 하나를 src Rect로 재사용하며 프레임 Bitmap을 복제하지 않는다.
- 원본 배경이 사용하는 **동일한 원본 픽셀→화면 Matrix와 clip**을 외부에서 전달한다. 뷰포트 좌표로 앵커를 재해석하거나 별도 centerCrop을 추정하지 않는다. 배경과 overlay의 로컬 좌표가 다르면 원본 행렬에 그 위치 차이까지 포함해야 한다.
- crossfade는 작은 두 프레임 배치 사각형의 합집합에만 saveLayer, premultiplied ADD 가중합 후 한 번 source-over 합성한다. opacity를 적용하며 bounds 밖이나 전체 UI 레이어는 움직이지 않는다. GPU 중간 레이어 비용은 별도 존재하고 실기기 성능은 미검증이다.

## 물 효과는 기본 OFF

외부에서 검증된 물/가림 영역 Path를 공급하기 전에는 navigation, market, harbor, home의 물 효과를 그리지도 tick하지도 않는다. navigation은 원본 JSON에 requiresWaterOcclusionMask가 없지만 id에 water-가 있어 안전하게 필수로 처리한다. 모든 물 마스크는 **원본 이미지 픽셀 좌표**와 정확한 background asset identity를 사용한다. View는 전달받은 Path를 복사하고 배경과 같은 Matrix로 clip한다.

`boundsNormalized`는 효과 배치 사각형일 뿐, 물 영역 마스크가 아니다. 이 사각형이나 README의 대략적 y범위로 가짜 물 마스크를 만들지 않는다. 실제로 검토된 마스크 파일/좌표는 이번 입력에 없으므로 4개 물 장면은 기본 비활성이다. 건물·선박·벽을 제외한 정확한 마스크를 나중에 받은 후 명시적으로 활성화할 것.

## 적용 예제 (자동 연결하지 않음)

현재 배경을 그리는 View와 같은 좌표계를 가진 형제 overlay를 **UI 컨트롤보다 먼저** 추가한다. 화면당 한 overlay를 재사용한다. 원본 정적 배경 View는 항상 그대로 둔다. 아래 dp/parent/backgroundMatrix/viewport는 해당 화면의 기존 실제 값이며 새 배경 이미지를 생성하거나 경로를 추정하는 코드가 아니다.

```java
BackgroundMotionOverlay ambient = new BackgroundMotionOverlay(activity);
sceneContainer.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
// 이후에 버튼·문구를 올린다. ambient가 이미 있는 UI 위를 덮지 않게 순서를 확인한다.
ambient.selectScene("collection");
// bg-collection.png 원본 좌표 1870×841 기준. 실제 배경 renderer와 같은 행렬/clip.
ambient.setSourceTransform("native/release-v1/backgrounds/bg-collection.png",
    1870, 841, backgroundMatrix, viewport);
ambient.setReducedMotion(!activity.motionEnabled);
ambient.setHostResumed(activity.motionResumed);
```

배경 renderer가 source Rect를 crop한 뒤 destination Rect로 drawBitmap한다면, 같은 Matrix를 `setRectToRect(sourceRect, destinationRect, Matrix.ScaleToFit.FILL)`로 만들 수 있다. 이는 crop을 새로 계산하는 것이 아니라 실제 그리기에 사용한 두 Rect를 변환으로 표현하는 것이다. 원본을 decode 시 축소했다면 먼저 원본 픽셀→decode 픽셀 스케일을 포함해야 한다. 실제 렌더러의 pan/zoom/offset도 똑같이 합성한다. 크기나 변환이 변할 때만 setter를 호출하고 매 draw마다 무조건 갱신해 무효화 루프를 만들지 않는다.

```java
// 물 장면: 마스크 없이는 화면에 기존 정적 원본만 보인다.
ambient.selectScene("home");
ambient.setSourceTransform("native/release-v1/backgrounds/bg-home.png",
    1870, 841, homeBackgroundMatrix, viewport);
// 실제 전달·검토된 마스크가 있을 때만 아래 호출. 임의 전체 화면 Path 사용 금지.
ambient.setWaterMask("native/release-v1/backgrounds/bg-home.png", verifiedWaterPath);
```

Activity/화면 lifecycle을 명시적으로 연결한다.

```java
// Activity.onResume / 화면 활성화
ambient.setHostResumed(true);
// Activity.onPause / 화면 비활성화, 다른 페이지로 전환
ambient.setHostResumed(false);
// 사용자의 모션 줄이기 설정
ambient.setReducedMotion(true);
// 메모리 경고: 자동 재시도하지 않고 원본 정적 배경으로 고정
ambient.onTrimMemory();
// 사용자가 해당 화면을 다시 명시적으로 열 때 선택적으로 재시도
ambient.retryEffects();
// 최종 화면/Activity 종료
ambient.dispose();
```

View 자체도 window visibility, 조상 visibility, focus, detach를 검사한다. 숨김/정지/분리 시 callback을 제거하고 시트 참조를 놓는다. focus 상실 시 재생만 정지한다. 표시했던 bitmap은 RenderThread/display list 참조가 남을 수 있어 즉시 recycle하지 않으며 GC/GPU 회수 시점까지 일시적 메모리가 남을 수 있다. 따라서 ‘전체 앱 RSS가 항상 정확히 8MiB만 늘어난다’는 보장은 아니다. 동시에 8개 화면 overlay를 생성/유지하지 말고 단일 인스턴스를 재사용한다. dispose는 executor를 닫으며 종료 후 재사용하지 않는다.

오류/메모리 부족/미지원 설정/원본 identity 불일치에서는 overlay를 비워 원래 정적 배경을 그대로 보인다. `lastError()`로 로딩 실패를 개발 로그나 상태 표시에서 확인할 수 있다. 모션 줄이기는 전체 overlay를 끄며 정적 효과 프레임으로 대체하지 않는다. 외부 observer나 앱 전체 lifecycle 등록은 추가하지 않아 기존 Activity 동작을 바꾸지 않았다.

## 실행한 검증

1. `python scripts/check-background-motion.py`: 8개 시트/64프레임 통과. SHA-256, 원본 8개 배경의 SHA-256와 Git blob SHA-1, 실제 크기, 경로, 프레임 위치, 모든 프레임의 테두리 alpha=0, 빈 프레임 없음, normalized 앵커·피벗·사각형 일치, 핑퐁/루프/crossfade 검사.
2. `python scripts/test-background-motion.py`: 9개 오류 탐지 테스트 통과. 정상·물 안전 조건, 경로 이탈, 끝점 중복, 루프 길이, crossfade 범위, 앵커 불일치, 프레임 좌표 오류, 경계 alpha, 원본 해시 변경.
3. `JAVA=/usr/lib/jvm/java-21-openjdk-amd64/bin/java bash scripts/test-background-motion-timeline.sh`: 실제 순수 Java 재생기 컴파일·실행 통과. 3주기 순서, crossfade 시작/중간/마지막, 7→6 및 1→0, 정지 불변성, 긴 정지 후 재시작, 음수/긴 경과, crossfade=0, 잘못된 설정 거절. 출력은 /tmp에서 정리되며 APK/Gradle을 실행하지 않는다.
4. 신규 Java 3개 파일 javalang 구문 분석, git diff 검사 통과.

Android View/Canvas 컴파일·실기기 렌더링·lifecycle 자동화·GPU 알파/성능/메모리 측정은 미실행이다. 검증 결과를 실기기 완료로 표현하지 말 것.

## 남은 작업 / 완료 아님

Claude는 기존 CLAUDE.md의 빌드 전 디자인 검토를 따라 선택한 화면에서만 도입하고, 실제 글꼴/780×360·915×412·1280×720 가로 화면에서 원본→화면 변환, clip, 선박/벽/기둥 가림, 버튼·문구 정지, pause/resume/detach/빠른 장면 전환/메모리 경고를 검사한다. 화면별 시간 비용 및 GC/GPU peak를 측정한다. 실기기 확인 전 전체 화면 일괄 적용하지 않는다.

**실제 배 흔들림과 진짜 전경/배경 레이어별 드래그는 미완료**다. 효과 시트는 국소 등불·먼지·물빛·잎 장식이고 선박·건물 원본을 분리/변형하지 않는다. 항구 국소 opaque patch + drawBitmapMesh는 별도 검토 대안이며 이번 코드에 구현하지 않았다. 물 마스크 공급도 남아 있다.
