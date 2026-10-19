# 갈매미맨 r1 인계 — 소스·빌드·설치·검수

2026-10-19 기준. r1은 원래 Codex 81판을 꺼낸 것이 아니라, 확보한 이전 소스와 설계 자료로 **다시 만든** 소스다.

## 1. 소스가 어디에 있나
- 브랜치 `claude/new-session-fma5lb`(r1 `manual/rebuild-r1-20261019`의 0e29db5를 병합해 이어감).
- 커밋 `8b6f675`: 예전에는 CI가 매번 ZIP 4개(`realtime-ranked-bundle.zip`, `casual-ai-fallback-patch.zip`, `queue-ui-cleanup-patch.zip`, `gmm-rebuild-r1.zip`)와 `apply-*.py`로 소스를 만들었다. 그 결과 93개 파일을 저장소에 그대로 풀어 넣었다. **이후 수정은 이 파일들을 직접 고쳐 커밋한다.** ZIP과 스크립트는 기록용으로 남긴다.
- 출처 확인(누구나 다시 해 볼 수 있음): `bash galmaegiman-confirmed/scripts/verify/r1-regenerate-check.sh`
  - 0e29db5에서 같은 순서로 다시 생성해 8b6f675와 비교한다. 2026-10-19 결과는 「같음 93 / 다름 0」.
- `.github/workflows/gmm-rebuild-manual.yml`(ZIP 방식)은 0e29db5에서만 의미가 있다. 소스를 풀어 둔 지금은 `gmm-native-ci.yml`을 쓴다.

## 2. 빌드
### GitHub Actions(권장): `.github/workflows/gmm-native-ci.yml`
- 언제 도나: 위 두 브랜치의 `galmaegiman-confirmed/**`에 push할 때, 또는 Actions 화면의 「Run workflow」.
- 서버 검사: `tsc --noEmit`, 집중 vitest 4개 파일, 임시 SQLite 회귀(`scripts/verify/run-casual-regression.mjs`).
- 안드로이드: 단위 테스트(화면 캡처 포함), `assembleDebug`, `assemblePreview`.
- 결과물(아티팩트):
  - `gmm-preview-apk`: 기존 앱 옆에 설치하는 미리보기판.
  - `gmm-screen-review`: 캡처 PNG와 `findings.json`.
  - `gmm-native-test-reports`: 테스트 보고서.

### 내 컴퓨터에서
```bash
cd galmaegiman-confirmed/app/android
echo "sdk.dir=/안드로이드/SDK/경로" > local.properties   # 저장소에 올리지 않음(.gitignore)
./gradlew :app:testDebugUnitTest :app:assemblePreview
# APK: app/build/outputs/apk/preview/app-preview.apk
# 캡처: app/build/native-review/r1/*.png, findings.json
```
필요한 것: JDK 17, Android SDK platform 36, build-tools 35.

## 3. 기존 80판을 지우지 않고 설치해 보기
| APK | 패키지 | 기존 80판과의 관계 |
|---|---|---|
| **미리보기** `app-preview.apk` | `org.dpdns.gmm.native.revised.r1preview`, 홈 화면 이름 「갈매미맨 미리보기」 | **옆에 따로 설치된다.** 80판은 그대로 남는다. |
| 디버그 `app-debug.apk`(예: run 39182298080의 artifact 12156384239) | `org.dpdns.gmm.native.revised` (정식과 같음), 디버그 서명 | 서명이 달라 80판 위에 **설치되지 않는다.** 80판을 지우고 설치하지 말 것. |

미리보기판에서 주의할 점:
- 접속하는 서버가 운영 서버(`https://gmm.dpdns.org`)이고 **실제 계정**을 쓴다. 미리보기판에서 한 행동도 실제로 기록된다.
- 운영 서버에는 r1 서버 변경(일반전 AI·약팀 보정 등)이 배포되지 않았다. 그 기능은 미리보기판에서도 동작하지 않거나 이전 규칙대로 동작한다.
- 카카오 로그인 버튼을 숨겼다. 카카오 복귀 주소(`gmmrevised://auth`)를 정식 앱과 나눠 쓰면 로그인 복귀를 서로 가로채기 때문이다. 미리보기판의 복귀 주소는 `gmmr1preview`이고, 로그인은 카톡으로 받은 6자리 코드로 한다.
- 디버그 서명 키는 빌드하는 기계(CI 실행)마다 달라질 수 있다. 새 미리보기를 위에 설치하다 「충돌」이 나면 **미리보기 앱만** 지우고 다시 설치한다(80판은 지우지 않는다).

## 4. 80판 위에 올리는 업데이트 APK의 조건(아직 만들지 않음)
- 패키지: `org.dpdns.gmm.native.revised`.
- 서명: 기존 정식 키. 저장소 `apk` 브랜치 `native.json`에 남은 마지막 기록(78판)의 인증서 SHA-256은 `097fb35527efeb6ab86b5d52908a634ad8cf0e556b24f5f2de918b91a4754897`. 80판도 같은 키인지는 기기에서 확인해야 한다.
- versionCode: 설치된 80판보다 커야 한다. 지금 소스는 78이다. **79·80판 기록은 저장소에 없다.** 기기의 실제 값을 먼저 확인한다.
- 키·암호는 저장소나 채팅에 넣지 않는다. `GMM_ANDROID_KEYSTORE`, `GMM_ANDROID_STORE_PASSWORD`, `GMM_ANDROID_KEY_ALIAS`, `GMM_ANDROID_KEY_PASSWORD`를 GitHub Secrets로 받아 release 빌드하는 워크플로를 따로 만든다(승인 후).

## 5. 화면 검수(서버 없이)
`app/src/test/java/org/dpdns/gmm/R1ScreenReviewTest.java`
- 그리는 크기:
  - 휴대폰 가로 915×412
  - Z플립 1006×411 + 노치 인셋
  - 큰 화면 1280×720
- 그리는 화면:
  - 전투: 기본·공격·아이템·아이템 대상·교체·상성표
  - 증강체: 선택 전·후
  - 로비
  - 비교용: 예전 아이템 대화창
- 쓰는 것: 실제 View와 실제 게임 글꼴(assets/native/*.ttf). 정해 둔 고정 자료로 그린다.
- 숫자 검사(`findings.json`):
  - 버튼이 화면 밖으로 나가는지, 부모에 잘리는지
  - 터치 영역 48dp 미만
  - 글자 말줄임, 글자 세로 잘림
  - 형제 버튼끼리 겹침
- 한계: Robolectric 렌더는 실제 기기 화면이 아니다. 서버 자료가 필요한 화면(편성·도감·상점·탐험 등)은 아직 넣지 않았다. 실기기 확인은 따로 해야 한다.

## 6. 남은 일(순서)
1. 실기기에서 전투·편성·증강체·상성표 확인 → 수정.
2. 매칭 → 편성 → 증강체 → 결과를 확정 설계(앱 배치 02, 상태 배치 01~04)대로 완성.
3. 홈·탐험·뽑기·조합·상점·설정 배치 개편(지금은 공통 색·재질만 바뀜).
4. 전투 연출 중 다음 턴 시간이 흐르는 문제.
   - 원인 위치: `src/pvp/service.ts`의 `advance()`. 턴을 계산한 직후 `deadline = now + 30초`로 정한다.
   - 서버 규칙을 바꾸는 일이라 앱 설치만으로는 적용되지 않는다. 운영 반영은 별도 승인 후.
5. 웹을 같은 흐름으로 통일.

운영 서버·DB·점수 초기화·배포는 별도 승인 전에는 하지 않는다.
