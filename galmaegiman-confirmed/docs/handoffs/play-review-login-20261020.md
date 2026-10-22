# Play 심사 로그인 코드 인계 — 아직 비활성

기준 `0bf7846214951f8b97a010b7cad851c89056dd84`, 브랜치 `feature/play-review-login-20261020`.
실제 계정·자격증명·운영 설정·DB·권한은 생성/변경하지 않았다. 배포와 APK/Actions 빌드도 하지 않았다. 테스트 문자열은 더미이며 사용 가능한 심사 인증정보가 아니다.

## 구현과 경계

- 첫 화면 `로그인` → `심사 계정 로그인 / Review login` → `Username`, `Password` → `로그인 / Sign in`. 카카오 처음 연결 중에는 진입 버튼을 표시하지 않는다.
- POST `/my/api/review/login`: 서버 환경값에 고정된 단일 Player ID만 사용한다. 요청의 임의 playerId는 인증 대상을 바꾸지 못한다. 계정 자동 생성/재화 지급/관리자 부여 없음.
- `REVIEW_LOGIN_ENABLED=true`와 모든 유효 설정이 함께 있어야 동작. 없거나 잘못되면 일반적인 인증 실패. 기존 `REVIEW_CODE` 옵션·자동 계정 생성은 제거. 이전 `review:*` 계정의 서명 쿠키도 차단한다.
- 고정 ID의 identity가 정확히 `review:play-store`, isAdmin=false, adminMode=false, KakaoLink/ChatAppUser/AdminSnapshot 없음을 로그인 및 매 인증 요청마다 검사한다. 일반 개인 계정을 가리키는 설정은 실패한다. 별도 Prisma 스키마 변경 없음.
- 서버에는 scrypt 해시만 보관: `scrypt$131072$8$1$<16~32바이트 salt의 소문자 hex>$<32바이트 key의 소문자 hex>`. Node 비동기 scrypt, N=131072/r=8/p=1, maxmem=192MiB, 키 timingSafeEqual. 직접 연결 IP(req.ip)별 endpoint 5회/분, 전역 KDF 동시 실행 1개와 FIFO 대기 8개 제한. 출처마다 실행/대기 합계 1개만 허용하며 무효 입력은 KDF 대기를 만들지 않는다. 실패 입력/미설정/없는 계정은 동일 안내, 제한은 동일 안내+429. 임의 X-Forwarded-For/CF-Connecting-IP는 신뢰하지 않는다(trustProxy=false). 한 직접 IP의 실패 요청은 다른 직접 IP의 예산을 소모하지 않는다. 프록시 뒤에서 모든 요청이 같은 직접 IP로 보이면 제한을 공유하므로 실제 활성화 전 신뢰 경계 검토가 필요하다. 운영 프록시 설정은 변경하지 않았고 분산 서비스 거부 공격 전체를 방어한다고 주장하지 않는다.
- 32바이트 랜덤 불투명 세션, 12시간 만료, 최대20개. 로그아웃 시 해당 세션 폐기, 서버 종료/재시작 시 전부 폐기. 비활성화 또는 버전 변경 후 프로세스 재시작 필요(환경값 hot reload 아님). 계정 권한/연결 조건이 바뀌면 기존 세션도 거부. 네트워크 실패한 로그아웃은 서버 폐기를 보장하지 못하며 전체 폐기는 재시작으로 수행한다.
- 세션 저장은 프로세스 메모리. 현재 단일 서버용이며 다중 인스턴스 운영 전 공유 세션/제한 저장소 설계가 별도로 필요하다. 이번 변경에서 배포 형태를 바꾸지 않는다.
- 요청 본문/비밀번호를 로그에 쓰지 않으며 인증 관련 헤더 redaction도 설정했다. 앱은 비밀번호를 저장·자동완성·상태 복원하지 않고 제출 즉시 칸을 비운다. 기존 request busy/epoch/account 검사로 중복 요청과 화면 변경 후 늦은 응답을 처리한다.
- 허용 목록: 자기 계정 도감/수집, 뽑기/조합, 게임 재화 상점, 출석/탐험, 편성/성장/진행도, 스토리/연습 전투. 기존 재화 소모/개체 소유권/트랜잭션/중복 요청 처리는 그대로 이용한다. 신규 유료 기능 없음.
- 차단: 계정 연결/삭제, 쿠폰, 운영 기능, 대인/랭크 대전과 신규 미검토 API. 심사 계정 데이터는 공개 수집·주간·최상위 순위, 발견 집계/최초 발견, 공개 프로필/검색, 구 PvP 후보/순위에서 제외된다. 기존 review:store도 제외된다.
- 제한: 실시간 PvP/계정 연결·삭제는 이 계정에서 심사할 수 없다. 모든 앱 기능에 대한 접근 완료나 Play 승인 준비 완료를 주장하지 않는다. 필요 시 별도 격리 심사 환경의 범위 승인이 필요하다. 일반 사용자 데이터가 있는 서버에서 대인 기능을 임의 개방하지 않는다.

## 승인 후 운영자가 해야 할 작업 (아직 실행하지 않음)

1. 심사용 고정 계정 신규 생성 및 시험용 진행/자원 범위를 사용자에게 승인받는다. 개인/관리자 계정 재사용 금지. identity `review:play-store`, 관리자 권한·카카오/챗봇 연결·관리 스냅샷·기존 대전 기록이 없는 독립 계정이어야 한다. 다른 이용자 초대/연결도 넣지 않는다. 재화/유닛/진행도 자동 초기화는 구현하지 않았다.
2. 승인 후 안전한 채널에서 랜덤 강한 비밀번호(권장 32자 이상, 서버 입력 허용 16~256자)와 고유 salt를 발급하여 위 형식으로 해시만 서버 비밀 설정에 저장한다. 사용자명은 ASCII `[A-Za-z0-9_@-]` 8~80자, 버전은 `[A-Za-z0-9_-]` 8~80자, Player ID는 고정 DB ID. 값들을 저장소/앱/명령줄 기록/로그에 넣지 않는다. 이번 작업에서 발급 도구를 실행하지 않았다.
3. 승인된 배포·계정 준비·비밀 설정 후에만 ENABLED=true로 활성화하고 재시작한다. HTTPS로 새 설치/재로그인/네트워크 오류/권한 거부/로그아웃을 실제 확인한다. 종료/철회 때 ENABLED=false와 재시작으로 모든 세션을 폐기하고 자격증명을 회수한다. 비밀번호 회전에도 버전 변경 및 재시작을 함께 수행한다.
4. 성공 확인 후에만 Play Console에 실제 값을 입력한다. 아래 초안을 실제 계정으로 오해하거나 제출하지 않는다.

## Play Console 입력 초안 (실제 계정 미발급)

- 이름(Name): `Galmaegiman dedicated review account` (안내 항목 이름)
- 사용자 이름(Username): `<APPROVAL_REQUIRED_USERNAME>` — 발급 후 교체
- 비밀번호(Password): `<APPROVAL_REQUIRED_PASSWORD>` — 안전한 발급 값으로 교체, 문서에 저장하지 않음
- 기타 정보(Other information): 아래 영문 안내. 500자 미만. 실제 출시 UI 검증 후 제출.

```text
Open the app and tap "로그인" (Login), then "심사 계정 로그인 / Review login". Enter the supplied username and password, then tap "로그인 / Sign in". No Kakao account or one-time code is needed. You can sign in again after logout or session expiry. Collection, crafting, growth, story and practice battles use this account. Live PvP, account linking and deletion are unavailable for this isolated review account.
```

## 검증과 Claude 인계

단위 테스트/HTTP inject는 mock DB와 더미 자격증명만 사용. `review-auth.test.ts` 22개와 기존 조합·content 회귀 20개, 합계 5파일 42개 통과. TypeScript noEmit, Java 구문 분석, diff whitespace 검사 수행. 운영 계정 발급/운영 DB 통합 테스트, 실기기·에뮬레이터 터치/키보드/화면 캡처·Android 컴파일은 미실행. 현재는 APK/Actions/운영 배포 금지이며 별도 허가 없이 실행하지 않는다.

`scripts/review-code.sh`는 계속 실패 종료(2)로 폐기되어 있다. 화면 검토용 `scripts/verify/review-server.sh`는 합성 fixture로 복구했다.

- `bash scripts/verify/review-server.sh --check`: /tmp의 새 SQLite에 합성 데이터만 생성, 기존 OTP 로그인 성공/동일 코드 재사용 실패/네이티브 상태 조회를 검증하고 삭제한다. 운영 .env를 읽지 않으며 부모 비밀 환경값도 전달하지 않는다. DB 경로 인수는 받지 않는다.
- `bash scripts/verify/review-server.sh`: 127.0.0.1:3999에서 같은 fixture를 실행한다. 출력된 임시 폴더의 token.txt를 기존 nativeReviewTokenFile에 전달한다. 파일 권한 0600, 폴더 0700. Ctrl+C/SIGTERM에서 서버·DB를 닫고 삭제한다. 강제 종료(SIGKILL) 때 남은 해당 임시 폴더만 수동 정리한다.
- 키를 발급하지 않고 로컬 전용 더미 서명 문자열을 사용한다. 합성 OTP는 정상 WebLogin에서 한 번 소비되며 이미 사용한 상태로 캡처를 시작한다. 실제 심사 계정/운영 활성화 수단이 아니다. localhost 실제 기동/health 응답, 토큰 0600 권한, SIGTERM 정리도 확인했다. APK/화면 캡처 자체는 실행하지 않았다.

P2 쿠키 수정: POST review/login과 logout만 기존 세션 검증에서 제외하되 각 handler의 x-gmm guard와 자격증명 검증/요청 제한을 유지한다. 유효 쿠키 재인증은 기존 세션을 폐기하며 실패 시 기존 rv_ 쿠키도 제거한다. 만료/철회 세션으로 일반 API에 접근하면 여전히401과 쿠키 삭제, 유효 세션의 금지 API는403이다.

향후 빌드 허가 후 Claude: 이 브랜치와 기존 motion/workbench 변경 전체를 유지하고 실제 Jua/GowunDodum 글꼴로 915×412·1280×720 및 키보드 열린 상태를 확인한다. 심사 진입 버튼/입력/제출/뒤로는 세로 스크롤에 있고 터치 높이 48dp 이상이다. 중복 클릭/틀린 비밀번호/429/뒤로/백그라운드/계정 전환/만료 후 재로그인과 카카오·OTP 회귀를 실제 확인한다. 현재는 이러한 런타임 검증을 통과했다고 표시하지 않는다.

보안 API 참고: https://nodejs.org/api/crypto.html#cryptoscryptpassword-salt-keylen-options-callback (scrypt 옵션·salt 및 timingSafeEqual API). 공식 Node 문서를 확인했으며 Play 정책 적합성 보증을 대신하지 않는다.

## 독립 검토 요청 범위

완료 커밋에서 별도 검토자가 고정 계정 바인딩, 기존 서명 쿠키/OTP 우회 차단, API 허용 목록의 빠진 경계, 로그·세션 폐기, 공개 집계 제외 쿼리와 일반 인증 회귀를 검토해야 한다. 이 문서의 테스트 결과는 독립 보안 검토 완료를 뜻하지 않는다. 실제 기기 검증과 독립 검토 결과 확인 전 운영 활성화하지 않는다.

## 2026-10-21 통합·변경 (manual/home-assembly-20261019)

`47b393e`·`30770c9`를 이 브랜치에 옮긴 뒤, 사용자 요청으로 **앱 로그인 화면 배치는 원래대로 두고 별도 로그인 주소 방식**으로 바꿨다. 위 「첫 화면 심사 계정 로그인」 버튼 설명은 더 이상 해당하지 않는다.

- **심사용 로그인 주소 `https://gmm.dpdns.org/review`** (noindex, no-store, no-referrer): 아이디·비밀번호 확인(같은 scrypt·요청 제한) → 2분짜리 일회용 코드 → `intent://auth?review=…;package=org.gmm`(Chrome) 자동 이동 + 「앱에서 열기」·`gmmrevised://auth?review=…` 버튼. 응답·로그에 비밀번호·세션 값 없음.
- **앱**: 로그인 화면 변경 없음. 기존 복귀 주소 처리에 `review` 코드만 추가 → `POST /my/api/review/exchange`(한 번만) → 심사 세션. `NativeReviewDeepLinkTest`.
- 사용자 지정 주소(gmmrevised)는 다른 앱이 같은 주소를 등록하면 코드를 가로챌 수 있다. 코드는 2분·1회·심사 계정 전용으로 제한했고, Chrome에서는 package=org.gmm으로 출시 앱을 직접 연다.
- **실시간 PvP를 일반 이용자와 같은 규칙으로 개방**(`pvp/queue/*`, `pvp/room/*`, `pvp/rank`, 네이티브 `battle/start`의 `normal`). 랭크 공개 순위에서는 다른 공개 집계처럼 `review:*` 제외. 계정 삭제·카카오 연결·쿠폰·관리 기능은 계속 403.
- **운영 도구** `scripts/manual/review-account.mjs`: check(읽기 전용) → create → prepare(1회 시작 자원) → password(입력 숨김, .env에는 해시만) → enable → 재시작 → verify(API 로그인·주요 API·로그아웃·재로그인과 /review 페이지→코드→세션까지, 상태 코드만 출력). 로컬 격리 DB·서버로 전 단계 확인, 서버 로그·.env에 평문·코드 없음.
- prepare 시작 자원(승인 대상): 닉네임 GmmReview, 뽑기권 100, 새우깡 3000, 갈매미 D1·D2·D3·C1·C2·U1, 대전 편성 D1·D2·D3, 튜토리얼 완료. 최상위 3마리는 실시간 PvP 진입 조건이다.

### EC2 순서(각 단계 승인 후)
```bash
cd ~/gmm/galmaegiman-confirmed
node --env-file=.env scripts/manual/review-account.mjs check
node --env-file=.env scripts/manual/review-account.mjs create
node --env-file=.env scripts/manual/review-account.mjs prepare
node --env-file=.env scripts/manual/review-account.mjs password
node --env-file=.env scripts/manual/review-account.mjs enable && sudo systemctl restart gmm-server
node --env-file=.env scripts/manual/review-account.mjs verify
```

## 2026-10-21 운영 상태
- 운영 `main-oz3tvo` @ `ee833d1`(PR #9) 배포. 외부 확인: `/review` 200(no-store, noindex), 잘못된 코드 교환 400.
- EC2에서 계정 생성·시작 자원·비밀번호·활성화·verify를 사용자가 실행했고, 81 앱(org.gmm)에서 `/review` 로그인 → 앱 접속을 사용자가 실기기로 확인(사용자 보고 기준. 결과 화면은 받지 못함).
- 만료 안내 영어 줄("Please sign in again …")은 `923fbc1`에만 있고 운영에는 아직 없다.
- Play Console 항목 이름: `Gmm review account`, Username `gmm-review`, Password는 운영자 보관(저장소·채팅에 없음).
