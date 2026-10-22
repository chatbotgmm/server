# PR #10 현재875 앱 오류 + 최소 웹 V2 호환 수정 인계

기준875fdb0ab499c05495b5c6bcc37e640508174d73. 이전로컬산출물소실후 exact875 Gitblob검증원본과각담당자수정에서재구성한후보이며,소실된최종패치와byte-identical이라고주장하지않는다. 현재 앱 수정 38개, 최소 웹 V2 호환 5개, 재검증에서 재현한 기존 backend 결함 수정 3개(생산 2개 + 결정적 회귀시험 1개)를 합친 46개 파일 후보다. next story/fieldItem 복원/웹 진입점 통합은 미포함. PR #10 head는 manual/home-assembly-20261019@875fdb0, base는 main-oz3tvo@ee833d1이다. 사용자는 이 수정의 PR #10 head 게시와 그에 따른 자동 검증을 승인했다. 이는 별도 workflow dispatch, APK 승인 게이트 변경, main 운영 병합, EC2/서비스 배포, 운영 DB 변경 승인이 아니다.

## 실제43실패 대응과 범위
원본581total/43failed/16skipped. 로그SHA256 `5242d37528b056868e6e51dc5498b33e82e5d97dbb84a43be7abc4f40478a81d`. `CURRENT-BUILD-875-FAILURE-MAP.md`의43행은소스대응이지43개runtimepass/실제UIbug판정이아니다.
- JSONnot-mocked16:runner/SDK35.
- unexpectedstarter-loansGET8:정확GET만ineligiblefake;예상밖endpoint/쓰기제한유지.
- replay4:currentepoch+명시적승인fixture;legacy차단유지.
- homecount2:실제15개+news목적지;geometry유지.
- aggregate1:HomeParty24기대유지,GET수정과실패진단추가. 누락4캡처인과는native재실행필요.
- 기타12:증강separator1,조합경제9,아이콘1,재화1. 독립기대값·현재tier/carousel계약으로수정하되pixel/가시성/거래단언보존.

Android 생산변경은 NativeEconomy.workbenchActionHeight의includeFontPadding-aware예약뿐이다. padding제외시ascent/descent+실제compoundpadding+기존8dp,최소48dp유지. 원래emptyText shaping/copy는그대로다. 수동reflow는검토후철회했다. text-fit시험은getLineMax를동일contentWidth+1예산으로검사하고full/visible/lineoffset을진단한다. 실제로그만으로visibleglyph clipping/trailingwhitespace원인은미확정. 기존ellipsis/vertical/globalvisibility/strictFULLart/거래단언유지. 실제oversized-singleline음성대조포함새native회귀3개는재실행필수.

현재LEGEND+정책은정식L1–L8와R14개정의를추가fixture로검사하며원래pinnedfixture는불변이다. HomeParty는GET와count진단을한번만통합. 그림/물마스크/productionDB기본값변경없음.

## 자산·소스 보호
이미게시된home한파일의theme/phase2잠금bytes/SHA256/Gitblob과합계만교정한다. 원래1870×841/baseline=true/타자산유지. historicalhome변경은정확old→new실제bytes/hash/blob/PNG검증을모두통과해야한다. 폴더/다른baseline자산예외없음. 유한source는원래119payload에실제변경경로만추가하고기존4control유지한다.

## CI-only worker 완화와 근거 한계
Home assembly check의backend시험step에만 `TOKIO_WORKER_THREADS: '4'`. 생산DB설정/pool/connection_limit/Promise.all4요청/5초deadline/assertion불변.

이전로컬조사에서2CPU/TOKIO2의동시SQLite BEGIN IMMEDIATE대기로CI와일치하는6pass/2timeout을재현했고후속receipt는fixtureINSERT에서대기했다. 실제CItrace/worker수는없어직접관측원인이아닌강한통제재현이다. Node로컬24.19.0/CI22.23.3차이도남는다. 당시fresh8개독립5회통과,기존로컬webbundle전제후533/533·41파일·68.53초·exit0가관측되었다. **이는소실전관측기록이며복구후보의새backend실행결과가아니다.**

재검증은완전체크아웃의기존 `npm run build` 다음 `TOKIO_WORKER_THREADS=4 npm run test:db`로새로그/환경/결과를보존한다. 내부진단경로/rawlog가실행전제는아니다.

사용자가 선택한 Android 빌드·전체 네이티브 재실행 담당은 Claude다. Home assembly check push trigger는 `manual/home-assembly-20261019`이다. **PR #10 head가 바로 이 브랜치이므로 push하면 CI가 실행될 수 있다.** 사용자는 PR #10 head 게시에 따른 자동 검증을 별도로 승인했다. Claude의 로컬 Android 재실행은 여전히 필요하며, 별도 dispatch나 APK 승인 게이트 우회/변경은 포함하지 않는다. workflow trigger와 기존 APK 승인 게이트는 변경하지 않았다.

## Claude 필수 검증
SDK/정식assets(특히R/Lportraits)/wrapper/Git이력을갖춘현재checkout에서:
```
python galmaegiman-confirmed/app/android/ci/verify_theme_ci.py source --repo .
python galmaegiman-confirmed/app/android/ci/verify_phase2_ci.py assets --repo . --output /tmp/current-phase2-assets.json
PYTHONPATH=galmaegiman-confirmed/app/android/ci python -c 'import verify_v2_source_batch as v; print(v.verify_repository("."))'
cd galmaegiman-confirmed/app/android/ci
python -m unittest discover -p 'test_verify*.py'
python -m unittest test_current_build_repair
cd ..
./gradlew :app:testDebugUnitTest
```
Sourcegate는trackedworktree와HEAD불일치를의도적으로거부한다. 로컬검토commit은게시승인이아니다. 전체581+시험/새NativeWorkbenchMeasureRegressionTest3개/원래strictcapture를수행하고XML·stacktrace·native측정을보존한다. 540×222/683×279/780×360font200에서carousel/재료art/heading/buttons예산을직접확인한다. 다음단언실패가나오면새로그로판단하고기준을낮추지않는다.

Pythonguard/Java구문/격리helper는Android실행아니다. 과거통과횟수는이번복구후보의새검증으로대체할수없다. 실제Android/화면/.git ancestry/fullbackend가미실행이면그대로표시한다. 기존16skipped를삭제/축소하지않는다. artifactquota와출시는별도사항이다.

## 최소 웹 V2 호환 범위와 실행
기존 `/game`의 인증·동일 출처 fetch에 capability `2`와 combat epoch `gmm-combat-20261021-v3`를 보낸다. 서버 등급/설명을 표시하고, `reverse` 서버 ID를 유지한다. 본인 active-fighter 속성만 열 가지 정식 속성으로 명시적 확인/취소하며 revision/session/deadline 변경 시 초안을 폐기한다. scoped token은 서버 소유다. 기존 attack/item/switch envelope를 유지하고 old epoch replay를 차단한다. 이 최소 웹 5개 자체는 그림·서버·schema·보안 설정·story reward 응답을 바꾸지 않는다. 별도로 승인한 기존 backend 결함 두 가지는 다음 절에 한정한다.

완전한 체크아웃에서 Node 22 이상(package engines), 고정 package-lock 및 해당 Prisma client를 사용한다. CI 관측 버전은 Node 22.23.3, 로컬 검증 환경 버전은 별도 기록한다. 다음 명령은 저장소의 `galmaegiman-confirmed`에서 실행한다:
```sh
npm ci
npm run db:generate
node --check public/game/game.js
node --check public/game/battle.js
node --test test/game-v2-ui.test.mjs
npm run build
TOKIO_WORKER_THREADS=4 npm run test:db
```
`test:db`는 새 임시 SQLite 경로를 배타적으로 만들고 해당 경로에만 schema/seed를 적용한다. 운영 DATABASE_URL로 수동 prisma db push/migrate를 실행하지 않는다. 새 `game-web-v2.integration.test.ts`는 TEST_DB_URL이 없으면 skip하므로 전체 검증에는 격리 DB runner가 필요하다. `/game`은 bundle이 아닌 원본 asset 응답이며 실제 HTTP body/cache 검사가 포함된다. VM/HTTP 검증은 실제 브라우저 레이아웃·두 사람 realtime E2E·Android 실행을 대신하지 않는다.

## 재검증 중 확인한 기존 backend 결함 두 가지
- 일반전의 완료된 무승부는 winner=null이라 미완료로 거절되었다. replay의 실제 phase=over로 완료 여부를 판정하며, 미완료·잘못된 기록·중복 정산·AI 랭크 차단은 유지한다. 일반전 무승부는 기존 non-win 처리로 받아 점수·승패 기록·보상 규칙을 변경하지 않는다.
- V2 선택 시간 초과 후 AI가 reverse의 두 번째 속성 선택을 남기면 진행이 멈췄다. 기존 수동 turn과 같은 AI-only 선택 처리 루프를 적용하고 사람의 증강·교체·속성 선택은 절대 자동 소비하지 않는다. 30초 deadline·revision·재시도 정책은 유지한다.
- 새 `test/pvp-completion-regressions.integration.test.ts`는 seed63 draw, seed303 AI reverse, seed82 사람 reverse를 실제 Fastify/격리 SQLite로 검사한다. 원본 대형 game 시험은 수정하지 않았다. 변경 전 두 실패/하나 통과, 변경 후 세 통과를 재현했다. 최초 비결정적 full-run의 seed/body는 수집되지 않아 특정 실패와의 인과는 직접 관측으로 단정하지 않는다.

## 이번 통합 후보의 새 로컬 검증 결과
2026-10-22에 Node24.19.0/npm11.9.0, Prisma6.19.3, Vitest5.0.3으로 locked offline install과 Prisma 생성, `npm run build`를 새로 수행했다. Linux 2CPU 제한/TOKIO_WORKER_THREADS=4와 새 격리 SQLite로 **540/540, 43파일, 66.96초, exit0**를 관측했다(원래533 + 웹HTTP4 + 새결정적회귀3). 이는 소실전533 관측과 별개다. 이전 재검증의 누락 입력 실패·간헐 draw/deadline 실패 기록은 보존했으며 성공만 골라 보고하지 않는다.

새 guard463 + 별도 repair8, 웹 DOM VM10, 실제 theme25+tray3/phase2/old→new음성7/finite156/패치 roundtrip이 통과했다. Java 구문65와 실제 Maven org.json/JUnit 순수 JVM helper9도 통과했으나 Android/Robolectric 실행이 아니다. 전체 Android581+와 신규회귀, 실제 화면/브라우저/PvP 두 사람 검증, Git 이력 기반 전체 source gate는 정식 체크아웃/SDK에서 여전히 필요하다. CI Node22 환경과의 차이도 남으며, 로컬540 성공이 실제 CI 성공이나 APK 승인/출시를 뜻하지 않는다.
