# GMM107 소스 백업과 새 환경 재현

이 브랜치는 비공개 소스 백업용이다. 운영 배포·DB 변경·APK 게시·새 서명키 생성을 수행하는 브랜치가 아니다.
기준은 fix/game-polish-20261027@60d5ddea0d2754f19ce8dc7db105d307a7fe4d60이며, 앞서 전달한 선행 UI·XP 연결·아군 정보·여섯 회귀·모집 공개 수정과 마을 탐색/한번에 보기, 새 XP 정책을 모두 소스에 합쳤다. 별도 과거 ZIP을 다시 적용하지 않는다.

## 소스와 데이터 범위

제품/시험 변경67파일(기존55 + XP14 - 공통2)과 공개 재현 보조2파일이다. 키·암호·민감 환경설정·서명 입력·APK/AAB/ZIP·생성 캡처·영상·DB·캐시는 포함하지 않는다. 새 원화 파일은0개이며 기존 Git 원본과 manifest/generator를 사용한다. 승인되지 않은 전달자 원화와 기본 바탕/계절 재제작은 포함하지 않는다. 기본 마을 아트 전체 조화 개선은 사용자 최우선 미완료 항목이다.

## Android 빌드

확인한 환경은 OpenJDK21.0.12.1, Android SDK36/build-tools36.0.0, Python3/Pillow12.3.0, 저장소 Gradle wrapper다. 이 도구와 dependency cache는 저장소에 넣지 않는다. 새 환경은 정상 package-manager 경로로 설치/준비한다.

최종 빌드는 org.gmm /107/2.2.5-native다. app/build.gradle의 이번 버전 변경은 두 줄이며 기존 정상 signingConfig를 유지했다. 기존 설치106의 실제 패키지/인증서와 호환되는지는 별도 검증해야 한다.

기존 호스트 검증에서 사용한 공개 repository initializer를 scripts/build/android-repos.init.gradle로 함께 보존했다. Google Maven을 공식 dl.google.com 주소로 설정하고 호스트 시험의 user.home을 writable scratch로 고정한다. 자격증명은 없다.

```bash
mkdir -p /workspace/scratch/java-user
cd galmaegiman-confirmed/app/android
./gradlew --no-daemon --max-workers=2 \
  -I ../../scripts/build/android-repos.init.gradle \
  -PgmmApplicationId=org.gmm -PgmmVersionCode=107 :app:assembleRelease
```

정상 bundleGameArt/등록증V10/빌런alias/흑화 아트 태스크를 실행해야 한다. APK 빌드에서 이를 -x로 생략하지 않는다. Gradle이 참조하는 기존 원본이 sparse checkout에서 빠져 있으면 현재 커밋의 해당 추적 파일을 먼저 복구한다. 전체 레거시 카드/애니메이션을 다시 번들하는 플래그는 켜지 않는다.

기록된 APK는294141958B, SHA256 529eca14bb55e3c60a3bd7386f3e8b4d45870b2714d0cfe04f7052b8b0a5306c의 미서명 산출물이다. 새 환경 재빌드의 바이트 동일성은 아직 검증되지 않았다. 미서명 APK는 설치/업데이트 완료본이 아니다.

기존 키의 이번 APK 사용 동의는 별도로 확보된 상태다. 키/암호는 이 브랜치에 없으며 새 키/debug 대체를 허용하지 않는다. 비밀을 채팅/명령 로그/Git에 넣지 않는다. 기존 Gradle이 지원하는 외부 환경 변수 이름은 GMM_ANDROID_KEYSTORE, GMM_ANDROID_STORE_PASSWORD, GMM_ANDROID_KEY_ALIAS, GMM_ANDROID_KEY_PASSWORD다. 실제 안전한 입력 확보와 원본 키의 서명·검증은 별도 작업이다.

## 시험 기록과 재현 한계

최종 native 선택 회귀는29클래스193/193, 실패/오류/skip0이었다. SDK35 native-Skia 호스트와 실제 원본 아트/폰트를 사용했다. 제품 Java505개 및 APK 상대 소스 입력515개의 최종 해시와 이 백업 소스를 대조했다. 호스트 전용 캡처 드라이버2개는 제품에 넣지 않는다.

당시 QA 경로에서는 실제 원본129개를 별도 staging하고 bundleGameArt를 생략했다. 새 checkout에서의 시험은 정상 generator/bundle을 먼저 실행한 상태에서 진행해야 한다. 이 새 환경 recipe를 재실행했다고 주장하지 않는다. 기록된 선택 목록은 다음과 같다.

```text
org.dpdns.gmm.VillageInspectionViewportTest
org.dpdns.gmm.NativeVillageExplorationTest
org.dpdns.gmm.NativeVillageObservationTest
org.dpdns.gmm.NativeVillageHomeTest
org.dpdns.gmm.NativeVillageHomeLoadingTest
org.dpdns.gmm.NativeVillageArtworkRefreshTest
org.dpdns.gmm.NativeVillageWindowTransitionTest
org.dpdns.gmm.NativeVillageHomeAssetsWindowTest
org.dpdns.gmm.VillageArtSamplingTest
org.dpdns.gmm.NativeVillageWaterCullingTest
org.dpdns.gmm.NativeVillageBuildingPressTest
org.dpdns.gmm.NativeVillageResidentPressTest
org.dpdns.gmm.NativeVillageQuestTrackerTest
org.dpdns.gmm.NativeVillageQuestHeaderTest
org.dpdns.gmm.NativeVillageShortcutsTest
org.dpdns.gmm.NativeVillageBuildDesignReviewTest
org.dpdns.gmm.NativeVillageOverlayImmersiveTest
org.dpdns.gmm.GameDialogImmersiveTest
org.dpdns.gmm.NativeRoster60FullscreenBattleTest
org.dpdns.gmm.RecruitOpeningRegressionTest
org.dpdns.gmm.RecruitFooterRegressionTest
org.dpdns.gmm.RecruitCandidateDecisionTest
org.dpdns.gmm.RecruitCardRerollTest
org.dpdns.gmm.NativeRoster60RecruitSessionTest
org.dpdns.gmm.NativeRecruitInertialRailTest
org.dpdns.gmm.RecruitRegistrationDirectionPreviewTest
org.dpdns.gmm.ApplicationEnvelopeTest
org.dpdns.gmm.RegistrationV10CardTest.candidateAndReceiptShowTheRegistrationCard
org.dpdns.gmm.NativeRecruitOverviewTest
```

서버 최종 시험은 격리 SQLite+seed+TypeScript --noEmit 성공 및 선택24파일330/330이었다(새52개는330에 포함). 운영 DATABASE_URL을 쓰지 않고 새 mkdtemp 경로의 빈 SQLite를 독점 생성하여 DATABASE_URL/TEST_DB_URL을 그 임시 파일로 고정한 자식 프로세스에서 Prisma db push --skip-generate, prisma/seed.ts, tsc --noEmit, 다음 vitest 선택을 차례로 실행했다. 기존 scripts/test-db.mjs는 격리 DB를 사용하지만 선택 인자를 전달하지 않고 전체 suite를 실행하므로330개 명령과 혼동하지 않는다.

```text
test/unit-xp-pacing.test.ts
test/xp-reward-uplift.integration.test.ts
test/normal-xp-uplift.integration.test.ts
test/commander-reward-uplift.test.ts
test/commander-reward-uplift.integration.test.ts
test/normal-xp-policy.test.ts
test/normal-xp.integration.test.ts
test/normal-xp-learning.integration.test.ts
test/story-unit-xp.integration.test.ts
test/commander-policy.test.ts
test/commander.integration.test.ts
test/commander-story-adventure.integration.test.ts
test/commander-pvp-native.integration.test.ts
test/roster60-battle.integration.test.ts
test/roster60-adventure.integration.test.ts
test/roster60-play-modes.integration.test.ts
test/roster60-owned.integration.test.ts
test/progression-xp-progress.test.ts
test/story-mode-api.integration.test.ts
test/roster60-three-party-pve.integration.test.ts
test/roster60-skill-inheritance.test.ts
test/roster60-craft-inheritance.integration.test.ts
test/story-encounter-scaling.integration.test.ts
test/roster60-reconnect.integration.test.ts
```

## 새 XP 서버 반영 경계

새 서버 코드와 일치 정책 두 개를 함께 반영해야 하며 APK만으로 새 보상이 켜지지 않는다. 현재 추가 정책은 five-stat-device-test-v1의 명시 시험 프로필이다. 실제 운영 profile/catalogVersion/계정 정책 계약 확인 없이 시험 파일을 운영 경로에 복사하지 않는다.

일반전 실제 출전3명/동레벨/레벨업 직후XP0/적정 난이도에서 Lv10/15/20/24/40/59는 모두2승 기준이다. Lv15 개체 몫200→581, 다음레벨6승→2승이다. 계정 메인250/주민600/일반승150·기존 일반패75/독립성공귀환150이다. 새 전투/활동 snapshot에만 고정하며 기존 대기열·진행 전투·수락 의뢰·완료 영수증은 기존 보상을 유지한다. 성장곡선/초과폐기/수동환생·기존 지급 대상은 유지한다. 이번 증분은 schema/nullable열/migration을 추가하지 않는다.

독립 흑화 재료 모험의 새 유닛 보상0 경로는 일반 하한으로 연결했지만 별도 흑화1.3/1.5 배율과 스토리/모험 패배50%는 미확정이라 넣지 않았다. 기본 계정 표시 한도99를60으로 바꾸지 않았다. 스토리·모험의 귀환 정산과 즉시 정산 차이를 유지한다.

## 미검증/미포함

실기기 FPS·설치·기존106 서명 호환·운영 API/profile 반영은 미확인이다. 생성 미리보기/캡처는 통제된 DTO의 호스트 렌더이며 실제 계정 상태가 아니다. 기본 마을 아트 조화·낮밤/계절 활성화·전달자 새원화·기록소 바닥 아트 교정·영웅증강/2대2엔진은 이 소스 백업으로 완료됐다고 주장하지 않는다.
