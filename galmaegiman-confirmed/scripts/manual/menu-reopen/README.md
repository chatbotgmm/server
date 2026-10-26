# 전체 테스트 계정 메뉴 재개방 (관리자 API)

> 2026-10-26 이후 메뉴 잠금 기능은 제거되었습니다. 아래 절차는 과거 운영 기록입니다.
> 현재 서버는 8개 메뉴를 항상 열고 계정별 설정·레벨을 조회하지 않습니다.
> 관리자 변경 API는 권한·입력 검사 후 `410 MENU_ACCESS_RETIRED`를 반환하므로
> 이 재개방 스크립트나 `GMM_TEST_MENU_DEFAULT` 설정을 적용하지 않습니다.
> 기존 설정·감사 기록과 실제 기능의 로그인·소유권·재화·환생 자격 검사는 유지됩니다.

기준: `manual/home-assembly-20261019`, `32da6c2b97aba22f582cb88e5ab9782b7e4e8913`.

`src/menu-access.ts`는 `auto`를 `TEST_DEFAULT_LOCK`로 닫습니다. 전역 기본값을
바꾸면 테스트 계정 이외에도 영향을 줄 수 있고, 기존 `force_lock`은 남습니다.
따라서 **운영자가 확인한 전체 테스트 계정 명단에만** 기존 관리자 API로
8개 메뉴를 `force_unlock`합니다. 이미 열린 메뉴는 건드리지 않습니다.
아래 기존계정 API 도구 자체에는 서버 배포가 필요 없습니다. 새 가입자까지 자동 개방하려면
이번 서버 정책 코드를 배포하고 아래 전역 테스트 설정을 적용해야 합니다.
DB 직접 수정·스키마 변경·APK/AAB 빌드는 필요 없습니다.

## 필요한 권한과 사전 확인

- 대상 서버의 정확한 HTTPS origin과 정상 관리자 로그인 세션. 심사용 계정은 불가합니다.
- 누락 없는 테스트 계정 ID 목록을 운영자가 확인해야 합니다. 닉네임, identity,
  추측한 접두어로 테스트 계정을 자동 선별하지 않습니다. 현재 서버 전체가 테스트 범위임이
  확인되면 아래 읽기 전용 명단 추출 도구를 사용하며 사용자에게 개별 ID 입력을 요구하지 않습니다.
- 계정 목록과 계획 파일은 민감한 운영 자료입니다. 저장소에 커밋하거나 채팅에 올리지 않습니다.
- 자격증명을 발급·위조하거나 권한을 올리는 도구가 아닙니다. 세션은 안전하게
  `GMM_ADMIN_SESSION` 환경 변수로 제공하고 명령줄 인자·스크린샷·로그에 넣지 않습니다.
- 기존 운영 변경 절차에 따라 승인과 백업을 확인합니다. 스크립트는 DB를 읽거나 백업하지 않습니다.

## 실행 (Node 22 이상)

작업 디렉터리: `galmaegiman-confirmed`.
별도 보호된 디렉터리에 `accounts.json`을 JSON 문자열 배열로 준비합니다.
`GMM_ORIGIN`은 경로 없는 HTTPS 주소, `GMM_ADMIN_SESSION`은 기존 관리자 세션입니다.

```sh
umask 077
node scripts/manual/menu-reopen/reopen.mjs plan /secure/accounts.json /secure/menu-plan.json
# 계획의 모든 계정 ID, before, revision, 대상 origin을 로컬에서 검토
node scripts/manual/menu-reopen/reopen.mjs apply /secure/menu-plan.json --confirm-all-listed-test-accounts
unset GMM_ADMIN_SESSION
```

`plan`은 GET만 실행하며 계획 파일을 0600 권한으로 새로 만듭니다(덮어쓰기 불가).
`apply`는 origin을 대조한 뒤 모든 대상 계정의 revision과 기존 모드를 다시 확인합니다.
그 후 기존 POST `/my/api/admin/menu-access`로 순차 반영합니다. 서버의 관리자 검증,
revision 충돌 방지, 요청별 영수증과 `MENU_ACCESS_CHANGED` 감사 이벤트를 그대로 사용합니다.
마지막에는 모든 계정을 GET으로 다시 읽어 8개 모두 `force_unlock` 및 `enabled:true`인지 확인합니다.
출력에는 계정 수·변경 수만 표시하며 계정 ID나 세션은 표시하지 않습니다.

## 부분 적용·충돌·통신 오류

여러 계정/메뉴 전체가 하나의 트랜잭션은 아닙니다. 실패 전 일부만 적용될 수 있습니다.
401/403/409/429, 리다이렉트, 15초 시간초과, 잘못된 응답은 즉시 중단하며 자동 재시도하지 않습니다.
특히 네트워크 오류는 **반영 결과 불명확**입니다. 운영자가 기존 계획의 requestId와 서버 감사
기록을 확인하고 GET 조회로 현재 상태를 먼저 확인해야 합니다. 권한/제한 오류를 우회하지 않습니다.
의도된 동시 관리자 변경인지 확인하기 전 오래된 계획을 수정해 강제로 적용하지 않습니다.
문제가 해소되고 나머지 재개방이 여전히 승인된 경우 새 이름의 계획을 생성·검토하여 실행합니다.
기존 `force_unlock`은 건너뛰므로 정상 완료 후 새 계획으로 재실행해도 추가 변경은 없습니다.

되돌리기가 승인되면 계획의 `before` 모드가 복구 근거입니다. 현재 revision을 GET으로 확인하고
기존 관리자 API로 해당 모드를 적용해야 합니다. 자동 롤백은 다른 관리자의 변경을 덮을 수 있어 없습니다.

## 개방과 실제 기능 조건 구분

이 작업은 메뉴 잠금만 변경합니다. 환생 자격·레벨, 보유 유닛/재화, 구매 비용,
인증, 관리자 권한 등 각 기능의 서버 검증을 변경하거나 실행하지 않습니다.
관리자 GET 검증 후 실제 테스트 계정의 앱을 새로고침하고
`GET /my/api/native/menu-access` 및 화면을 확인해야 사용자 측 반영까지 검증됩니다.
실제 구매/환생/재화 소비를 검증 명목으로 실행하지 않습니다.

## 검증

```sh
node --check scripts/manual/menu-reopen/reopen.mjs
node --test test/menu-reopen.test.mjs
```

11개 모의 API 단위시험: 읽기 전용 계획, 8개 개방/명단 경계, 재실행,
사전 충돌, 모드 충돌, 오류 중단/재시도 없음, 최종 상태 검증,
입력 검증, HTTPS/리다이렉트/세션 처리, 오류 비밀값 억제, 잘못된 영수증 중단.
이는 실제 DB/API 통합시험이 아닙니다. 기존 저장소의 `npm run test:db`는
전체 소스·의존성·Prisma가 갖춰진 격리 환경에서 별도로 실행해야 합니다.

## 현재 테스트 서버 전체 + 새 테스트 가입자 (권장 적용 순서)

1. 운영자가 대상 서비스/DB가 현재 테스트 서버 전체 범위임을 확인합니다. 프로필 환경값만으로
   실제 DB가 테스트 전용이라는 사실이 증명되지는 않으므로 서비스 설정도 함께 확인합니다.
2. 이번 `src/menu-policy.ts`, `src/menu-access.ts`, `src/roster60-routes.ts` 서버 변경을
   기존 승인된 서버 배포 절차로 배포합니다. 앱 APK/AAB와 별개이며 기존 서버 TypeScript 빌드가 필요합니다.
3. 해당 테스트 서비스에 다음 두 환경값을 함께 지정하고 서버를 재시작합니다.
   `ROSTER60_PROFILE=five-stat-device-test-v1`, `GMM_TEST_MENU_DEFAULT=open`.
   profile이 다르거나 없는 상태에서 open은 시작 오류로 거부합니다. 알 수 없는 설정값도 거부합니다.
4. 검증된 테스트 DB 연결과 프로필을 가진 배포 호스트에서 기존 전체 Player 명단을 읽기 전용 추출합니다.
   `.env`를 쓰는 배포라면 아래처럼 명시적으로 로딩합니다. 파일/환경 비밀값은 출력하지 않습니다.

   ```sh
   node --env-file=.env scripts/manual/menu-reopen/export-test-accounts.mjs /secure/accounts.json --confirm-test-server-all-players
   ```

   추출 결과는 행수만 표시합니다. 현재 서비스에 비테스트 계정이 함께 있다면 전체 추출/적용을 중단하고
   확인된 테스트 대상 목록만 사용합니다. 명단 추출은 서버 DB에 쓰지 않습니다.
5. 위 plan/apply 절차로 현재 모든 계정의 기존 명시적 잠금까지 재개방하고 전체 상태를 재조회합니다.
   새 계정은 명단에 없어도 전역 설정으로 auto가 열립니다. 이후 관리자가 새로 force_lock한 계정은 닫힙니다.
6. 새 테스트 계정/기존 테스트 계정에서 native/menu-access 응답과 앱 새로고침을 확인합니다.

### 우선순위와 되돌리기

`force_lock` → 항상 닫힘; `force_unlock` → 항상 열림;
`auto` → 명시적 테스트 profile + open 설정일 때만 열림. 설정 미지정 또는 locked는 기존 닫힘 유지.
`testDefaultLock` 응답 필드도 실제 선택된 기본값을 나타냅니다. 메뉴 enforcement는 그대로 유지합니다.

기본값 되돌리기는 `GMM_TEST_MENU_DEFAULT=locked`로 재시작(또는 해당 설정 제거)합니다.
단 기존계정 일괄 도구의 force_unlock은 전역 locked보다 우선하므로 이것만으로 전체 롤백되지는 않습니다.
전체 복구가 승인되면 저장된 계획의 before를 현재 revision 기반 관리자 API로 복원해야 합니다.
관리자의 이후 변경이 있으면 계획을 덮어쓰지 말고 개별 검토합니다.

현재 검증: Node 24에서 정책 4개 + 재개방 도구 11개 단위시험 통과.
추가한 실제 MenuAccess/Prisma 통합시험은 이 소스 부분 체크아웃에 의존성·Prisma·격리 DB가 없어 미실행입니다.
전체 TypeScript 검사, 기존 test:db, 운영 API, 실제 앱 화면 검증도 미실행입니다.

## EC2 운영자 셸에서 기존 계정 즉시 열기 (세션·토큰 불필요)

`local-reopen.mjs`는 검증된 서비스 폴더의 **기존** `dist/src/menu-access.js`와
생성된 Prisma client를 사용합니다. 같은 폴더의 `reopen.mjs`도 필요합니다.
서버/API로 세션을 보내거나 브라우저 쿠키를 추출하지 않습니다.
기존 `MenuAccess.change`를 호출하므로 관리자 확인·트랜잭션·revision·감사·영수증은 그대로입니다.
`requestId`의 `ec2-shell:<run UUID>:<순번>:<feature>` 접두어가 운영자 셸 실행임을 구분합니다.
일반 사용자가 UI에서 클릭했다고 기록하지 않습니다. 계획의 source도 `ec2-operator-shell`입니다.

### 실제 환경에서 먼저 확인할 사항

- systemd의 실제 WorkingDirectory와 환경 로딩 경로. 아래 예시 `.env`는 실제 서비스가 사용하는 파일이어야 합니다.
- 기존 `ROSTER60_PROFILE=five-stat-device-test-v1`과 대상 전체 Player가 테스트 범위임을 확인합니다.
  단지 검사를 통과하려고 테스트 프로필을 새로 덧씌우지 않습니다.
- 서비스가 사용하는 SQLite DB의 **검증된 절대경로**. 스크립트는 파일 존재와
  Prisma 연결의 `PRAGMA database_list`의 main.file을 realpath로 대조합니다.
- 같은 서비스의 기존 `DATABASE_URL`이 file: 형식이며 이미 존재하는 DB를 가리키는지 확인합니다.
  연결 초기화 전에 경로가 맞다는 운영 확인이 필요합니다. 이 도구는 DB 위치를 추측하거나 바꾸지 않습니다.
- `dist/src/menu-access.js`가 존재하고 `MenuAccess`를 내보낼 것,
  `node_modules/@prisma/client/default.js` 및 생성된 client/engine이 설치되어 있을 것.
  없다면 중단하며 자동 설치/생성/빌드/배포하지 않습니다.
- 기존 isAdmin=true 및 identity가 review:가 아닌 관리자가 정확히 1명이면 그 ID를
  보호된 plan 파일에 명시합니다(콘솔에는 actorVerified만 표시). 0명 또는 복수면 중단합니다. 복수인 경우에만 운영자가 확인한
  `--admin-player-id ID`로 선택할 수 있습니다. 권한 부여/관리자 생성은 하지 않습니다.

### 명령 형식 (실제 확인 경로에 맞춰 치환)

```sh
cd /VERIFIED/SERVICE
node --env-file=.env scripts/manual/menu-reopen/local-reopen.mjs --service-dir "$PWD" --database-file /VERIFIED/DATABASE.db --plan /secure/menu-local-plan.json
# 출력의 actorVerified, accounts=N, plannedChanges와 0600 계획의 actorPlayerId를 로컬에서 확인한 뒤
node --env-file=.env scripts/manual/menu-reopen/local-reopen.mjs --service-dir "$PWD" --database-file /VERIFIED/DATABASE.db --plan /secure/menu-local-plan.json --apply --expect-accounts N
```

명시적 `--apply`가 없으면 DB에는 쓰지 않습니다. 계획 파일을 0600으로 새로 만들며,
계정별 기존 AppMeta 원문(null은 기존행 없음), 기존 모드, revision, 요청ID를 보관합니다.
이 파일은 **메뉴 변경 복원 자료이지 전체 DB 백업은 아닙니다.** 서비스의 기존 일관된
DB 백업 절차를 쓸 수 있다면 함께 보관하되 실행 중 SQLite 파일을 단순 복사하는 것을
일관된 백업이라고 간주하지 않습니다.

apply는 관리자·서비스/DB 경로·명단·행수를 계획에 묶고, 전체 사전 revision 확인 후
순차 변경합니다. 마지막 전체 메뉴 재조회와 Player 명단 재조회에서 차이가 나면 중단/미완료로 보고합니다.
실행 중 새 가입자가 생기면 결과를 무조건 전체 완료라 하지 않습니다. 오류·경합 시 자동 재시도나
자동 롤백을 하지 않으며, 일부 적용됐을 수 있으므로 현재 상태와 ec2-shell 감사 요청을 먼저 확인합니다.

이 방식은 **기존 현재 계정만 즉시 개방**합니다. 신규 가입자의 auto 기본값은 앞 절의 정책코드와
설정이 실제 서비스에 반영되기 전에는 계속 잠깁니다. 이 스크립트는 설정파일 수정·서비스 재시작을 하지 않습니다.

추가 검증: 로컬 CLI 모의 DB/서비스 단위시험 6개 통과(기존 actor 선택, DB경로 대조,
범위·행수·명단 결합, AppMeta 일관성, 운영자 감사 접두어). 실제 EC2/Prisma 실행은 미검증입니다.
