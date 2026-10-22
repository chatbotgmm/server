# V2 · 신규 온보딩 · 소식 통합 소스 검토 / 빌드 인계

## 최신 사용자 요청 — 이번 빌드와 다음 배치 구분 (2026-10-22)
- 이번 빌드 산출물은 사용자가 최신 요청으로 지정한 **release AAB**다. 이전의 “APK만 먼저 / AAB 별도 승인 전 금지” 지시는 이번 요청으로 대체한다. 제공된 새 앱 아이콘을 포함한 최종 게시 commit을 사용한다.
- AAB 빌드 요청은 **Play 업로드·출시·운영 배포·main 병합·live DB migration 승인 아님**이다. package `org.gmm` 및 기존 서명 체계를 유지한다.
- story 200/500 지원과 D2 지원은 사용자가 **다음 코드 배치**로 승인했다. 이번 아이콘/AAB 배치에는 story 소스·보상·지급 변경을 포함하지 않는다.

## 기준과 승인 범위
- 정확한 기준: `chatbotgmm/server`, `manual/home-assembly-20261019`, `02a46bc58e8c031dd65e9a058e0ccec80f9e29b2`.
- 검증에 필요한 로컬 원본은 Git blob SHA로 복원했다 (전체 2,870 blob 중 필요한 파일만 복원한 작업 스냅샷이며 완전한 Git clone은 아님). baseline은 비교 기준일 뿐이다. 게시 후 Claude는 전달된 **최종 게시 commit 전체를 checkout**한다. 그 commit에 combined patch를 다시 적용하지 않는다. 최종 SHA는 자기참조를 피하기 위해 이 문서 밖 인계 메시지/장부로 전달한다. 기존 `NativeEconomy` TextView cast 및 UTF-8 테스트 수정은 보존했다.
- 검토 대상만: V2 기본/동일등급 6회 증강 순서/자유자재, 새 전투 자체속성 0.9·1·1.1, 등급·상세배율 UI, 임시 스타터/동일종 레벨 계승, 소식·개인 우편, 구 epoch 다시보기 차단.
- 기존 5279 source-lock/검증 함수/guard fixture 상수는 보존. 서비스 테스트는 기존 수치·소유권·보안 assertions를 유지하며 V1/V2·정확한 개체·새 cleanup 계약만 좁게 수정. 별도 유한 batch manifest는 baseline 02a46 대비 정확한 파일·해시만 허용한다. 기존 7개 backend 변경과 이번 변경은 별도 장부로 구분한다. source 승인 ≠ 컴파일·실기기·출시 승인.
- story 200/500 및 D2 지원은 다음 코드 배치로 승인되었으며, **이번 배치에는 구현·활성화하지 않았다**. 다음 배치까지 완료했다고 표현하지 않는다.

## 실제 검증
- pinned `package-lock.json`으로 `npm ci --ignore-scripts` (임시 npm cache, lifecycle script 미실행).
- `prisma validate`, `prisma generate`, `tsc --noEmit -p tsconfig.json`: PASS.
- `node scripts/build-play.mjs`: 검토한 로컬 웹 번들만 PASS. Android 산출물은 만들지 않음.
- 통합 Node 231/231, 뉴스 Node 15/15, replay gate 9/9 PASS. standalone Node의 역사 비교 tests는 별도 인계 `legacy-regression-fixtures.tar.gz`의 `pristine/` 참조 26개를 필요로 한다 (34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d 원본 blob 검증). 운영 소스/Android 산출물에 넣는 파일이 아니다. 기본 tsc/Prisma/SQLite Vitest는 이 참조폴더를 필요로 하지 않는다. Java 247파일 구문 parse PASS (Android 타입검사 아님).
- Python CI/guard/백엔드 격리 467/467 PASS (기존 negative fixture의 의도된 실패 출력 포함); 별도 UI 소스 계약 27 PASS, 순수 Java 홈 geometry 72조합/스타터 선택 16assertions PASS.
- 실제 SQLite/HTTP 전체 41파일 533/533 PASS, skip/fail 0. `node scripts/test-db.mjs`는 고유 `/tmp/galmaegiman-tests-*/test.db`에만 schema/seed를 생성한다.
- Android Gradle 전체 타입검사·JUnit·실기기·큰 글꼴/작은 화면/복귀/중복탭/네트워크 재시도는 미검증. 실제 Android SDK가 없는 환경의 Java parse를 compile PASS로 부르지 않는다.

## DB 업그레이드 (SQL 산출물만, 실행 승인 별도)
`docs/migrations/v2-onboarding-news.sql`은 임시 OwnedCharacter 만료열, Player 등록열 2개, StarterLoanGrant/StarterLoanLevel, NativeInboxMessage/NativeNewsRead/NativeNewsRequest를 함께 추가한다. 운영 DB에서 실행하지 않았다.

1. 대상 DB 경로·에디션·서비스 버전 확인 → 서비스 중지/쓰기 차단 → SQLite 일관된 백업 및 복원 검증. WAL까지 일관되게 포함해야 한다.
2. 먼저 백업의 격리 복사본에서 기준 02a46 Prisma schema와 실제 DB의 `prisma migrate diff --exit-code`가 차이 없음인지 확인. 이미 일부 열/테이블이 있거나 기준이 다르면 **중단**. `IF NOT EXISTS`로 충돌을 숨기지 않는다.
3. fail-fast 클라이언트(예: `sqlite3 -bail`)로 SQL을 **한 번만** 적용. `BEGIN IMMEDIATE` transaction이며 오류 발생 즉시 중단/ROLLBACK. 임의 ALTER 재실행 금지.
4. `PRAGMA foreign_key_check`, 기존 계정/개체/StorySave 보존, 최종 Prisma schema diff 없음 확인 후 테스트 서버의 신규 API/기존 기능 회귀검사.
5. 활성화 전 실패는 백업 복원으로 되돌린다. 활성화 뒤 신규 영수증/레벨 계승/우편 쓰기가 생겼다면 자동 열삭제/데이터삭제 rollback 금지: 쓰기 중단 후 백업과 신규 데이터 보전·조정 계획부터 승인받는다.

로컬 업그레이드 검증: pinned baseline schema로 새 `/tmp` DB 생성 → 기존 Player/OwnedCharacter/StorySave/CharacterDefinition 실제 행 삽입 → additive SQL 적용 → 모든 기존 열 값 보존/foreign_key_check PASS → 최종 Prisma schema `No difference detected`. 재적용은 duplicate column에서 거부되고 rollback 확인. Player 재생성/DROP 없이 ADD COLUMN만 사용한다. 기존 플레이어 일괄 등록이나 보상 backfill은 없다.

## 테스트 서버 → 앱 → 운영 순서
- 신규 APK는 현재 구 서버에 없는 starter/news API를 사용한다. **패치된 격리 TEST backend + 위 schema**를 먼저 준비하고 APK를 그 TEST 주소로 연결해야 신규기능을 검증할 수 있다. APK 단독 빌드는 API 검증이 아니다.
- 신규 대상만 일회 임시 스타터 등록. 기존 이용자 소급 등록/기간 연장/선택 변경 없음. 만료/소모 후에도 최고 레벨 기록만 보관해 등록 이후 새로 획득한 같은 종류의 첫 영구 개체에 한 번 연결; 승진·기술·아이템·XP는 복사하지 않는다. 관리자 체험은 계승 기록을 변경하지 않는다.
- TEST 설정의 `config/public-url.txt`가 PUBLIC_BASE_URL보다 우선한다. 운영 파일을 복사하지 말고 TEST origin과 일치시킨다. 운영 timer/푸시/쿠폰/외부 OAuth 연결은 사용하지 않는다.
- 소식은 읽기/읽음 표시뿐; reward claim API나 실보상 지급 없음. 개인 우편은 로그인 소유자만 조회/읽음 처리한다.
- 새 전투는 `x-gmm-augment-version: 2` 및 `x-gmm-combat-epoch: gmm-combat-20261021-v3` 지원 필요. 구 저장 전투의 규칙과 포기 경로는 유지한다. 구 epoch replay만 차단한다.
- **로컬 APK가 만들어졌다는 이유로 운영 capability 차단을 먼저 켜지 않는다.** 사용자가 실제로 업데이트할 수 있는 호환 스토어 버전의 배포/전파 확인 후 운영 서버 전환을 승인받는다. 그 전에는 TEST backend만 사용하거나 별도 검토된 호환 단계가 필요하다. 이 배치는 운영 전환을 자동 실행하지 않는다.
- 최종 코드 검토 이후 기존 검토 브랜치 `feature/review-onboarding-motion-20261021` 사용 여부를 최종 코드 검토/게시 담당자가 결정한다. 본 통합 작업은 게시/병합하지 않는다.
- **Claude는 최신 요청에 따라 이번 최종 commit의 release AAB를 빌드**한다. Play 업로드·출시, main 병합, 운영 배포, live DB migration, 실제 보상 지급은 별도 승인 전 금지. Actions artifact storage quota 미해결이면 그대로 보고하며 임의 삭제/요금제 변경하지 않는다.

## 최종 결과표
최종 patch/manifest/hash 장부와 실행 로그를 최종 코드 검토/게시 담당자가 검토한 뒤 게시 여부를 결정한다. 로컬 검증 통과가 원격 CI/Android 빌드·기기 검증 통과를 의미하지 않는다.


## 테스트 연결 및 release AAB의 서버/세션 안전 설정
- `gmmServerUrl`은 release/debug/preview 모두에 적용되는 선택적 HTTPS origin (예: `https://approved-test.example:8443`). 기본 production 주소는 변경하지 않았다. credentials·경로·마지막 `/`·query·fragment·제어문자·비ASCII·잘못된 port는 구성 시 거부한다. Java literal은 JSON serializer로 안전하게 생성한다.
- 기존 `nativeServer` alias는 debug/preview 전용으로 유지한다. 기존 CI의 `http://127.0.0.1:9`가 유지되며 HTTP 허용은 localhost/127.0.0.1/[::1]/10.0.2.2에만 한정한다. release로 cleartext를 확대하지 않는다. 두 property가 다른 URL이면 빌드를 거부한다.
- 정확한 기본 production URL만 기존 `native_account`를 사용한다. 그 외 backend는 URL 전체의 SHA-256으로 별도 prefs namespace를 사용하며 토큰·OAuth verifier/linkCode/linking·알림 상태를 복사하거나 기존 production 정보를 지우지 않는다. API 및 인증 이미지 요청은 자동redirect를 끄고 모든 3xx를 거부한다.
- 순수 JVM의 실제 helper + 소스 경계 4검사 PASS, Gradle에서 사용되는 실제 Groovy URL validator 33case PASS. Android Gradle 전체 빌드는 아직 안 했다. Groovy 검사는 공식 Maven Groovy 3.0.24+groovy-json 3.0.24를 임시 경로에만 받아 사용했다.
- TEST 서버는 새 빈 격리 DB와 별도 SKILL_SECRET을 사용한다. 정상 합성 계정의 6자리 로그인 흐름에서는 ADMIN_CODE 및 Kakao OAuth 키를 기본 미설정으로 둔다. 운영 DB·토큰·secret을 복제하지 않는다. 정상 로그인 세션도 SKILL_SECRET에 의존하므로 새 비밀값이 필요하지만, 이 작업에서는 키 생성·서버 provision·배포를 수행하지 않았다.
- 이번 요청은 **production 대상 release AAB 생성**이며 서버 주소는 `https://gmm.dpdns.org`다. 최종 게시 commit, 최신 배포 versionCode보다 큰 값, 기존 서명 설정을 확인한 뒤 아래 빌드를 진행할 수 있다. versionCode는 read-only 확인하고 사용값을 보고한다(임의 85 등 고정 금지). 기존 서명 key는 외부 보안 환경에서 제공하고 저장소에 넣지 않는다. 별도의 신규기능 QA에는 승인된 TEST URL/실행 서버가 필요하지만, TEST URL 미제공이 이번 production 대상 AAB 생성을 막지는 않는다. 이 빌드는 Play 업로드·출시·서버/DB 배포를 수행하거나 승인하지 않는다.

```text
./gradlew :app:bundleRelease \
  -PgmmApplicationId=org.gmm \
  -PgmmVersionCode=<확인한 최신값보다 큰 versionCode> \
  -PgmmServerUrl=https://gmm.dpdns.org
```

- 기존 제한 review-account는 gameTutorial=9로 준비되며 starter-loans 권한이 없다. reviewer allowlist를 넓히지 않는다. 첫 온보딩 검증은 격리 TEST DB의 정상 합성 신규 계정 + 정상 6자리 로그인으로 진행하고 prepare로 튜토리얼을 건너뛰지 않는다.
- 현재 feature 검토 브랜치 게시만으로 manual 전용 home-assembly-check가 자동 실행되지 않는다. CI를 위해 자동으로 manual/main에 병합하지 않는다. 원격 CI PASS라고 보고하지 않는다.


### 선택적 역사 비교 Node 검사 재현 (표준 CI와 별도)
전체 Git checkout에 아래 역사 commit 객체가 있어야 한다. shallow checkout에 없다면 해당 commit을 읽기 전용 fetch한 뒤 진행한다. 현재 최종 게시 commit을 바꾸거나 과거 소스를 운영 폴더에 덮어쓰지 않는다. 저장소 루트에서 빈 `pristine/` 참조폴더만 만든다.

```sh
set -o pipefail
git cat-file -e 34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d^{commit}
test ! -e pristine || { echo 'existing pristine must be verified separately'; exit 1; }
mkdir pristine
git archive 34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d \
  galmaegiman-confirmed/src/battle \
  galmaegiman-confirmed/public/duel/duel.js \
  galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeApi.java \
  | tar -x --strip-components=1 -C pristine
node --import ./scripts/ts-source-loader.mjs --test test/*.test.mjs galmaegiman-confirmed/test/*.mjs
```

경로 대응은 `pristine/<상대경로>` → 역사 commit의 `galmaegiman-confirmed/<상대경로>`다. 별도 인계 manifest `legacy-regression-fixtures.json`에는 로컬 검증에 사용한 26개 파일의 Git blob SHA/SHA-256이 있다. 그 archive/manifest는 **저장소에 포함되지 않은 선택적 대체 전달물**이며 위 Git 명령으로 필요한 참조를 재현할 수 있다. 표준 수락 증거는 full TypeScript/Prisma 및 실제 DB/HTTP 533개 검사다.
