# v0.2.6 전체 설치 — 갤탭 / Ubuntu

파일: `galmaegiman-random-75-tablet-v0.2.6.zip`

전체 소스, 테스트, 데이터, 기존 이미지 75장을 포함합니다. v0.2.6 게임 코드가 이미 반영되어 있으므로 예전 패치의 `apply.mjs`를 실행하지 않습니다. 전설·히든은 아직 포함하지 않습니다. 이미지 구성은 기존 v3 25장 + v2 50장입니다.

## 먼저 확인

- ZIP을 갤탭 Download 폴더에 저장합니다. 파일명이 자동으로 `(1)` 등으로 바뀌었다면 아래 명령의 파일명도 맞춰야 합니다.
- 게임 서버 창에서 Ctrl+C로 종료하고 명령 입력 프롬프트가 나올 때까지 기다립니다.
- 현재 `root@localhost` 프롬프트이면 이미 Ubuntu 안입니다. 여기서 `proot-distro login`을 다시 실행하지 않습니다.
- Termux의 `$` 상태일 때만 `proot-distro login ubuntu --termux-home`으로 들어갑니다.
- Ubuntu 안의 Node.js 22 이상, npm, unzip과 인터넷 연결이 필요합니다. node_modules와 Prisma 엔진은 해당 장치에서 설치합니다.
- 이 파일에는 사용자의 실제 DB·인증키·터널 주소가 없습니다. 기존 게임을 이어 하려면 반드시 A를 사용합니다.

## A. 기존 수집 데이터와 설정을 유지해 재설치

대상: `~/galmaegiman-confirmed`의 현재 75종 판. 초기 다른 프로젝트 `~/galmaegiman`용 자동 변환 절차가 아닙니다.

터널이 아직 켜져 있으면 유지할 수 있습니다. 게임 서버는 중지한 상태에서 **Ubuntu**에 아래 블록 전체를 붙여 넣습니다. 중간 명령이 실패하면 블록은 중단됩니다.

```bash
(
  set -e
  full_zip="$HOME/storage/downloads/galmaegiman-random-75-tablet-v0.2.6.zip"
  test -f "$full_zip"
  unzip -tq "$full_zip"
  cd "$HOME/galmaegiman-confirmed"
  node -e 'if (JSON.parse(require("node:fs").readFileSync("package.json","utf8")).name !== "galmaegiman-confirmed") process.exit(1)'
  test -f .env
  test -s storage/confirmed.db
  npm run backup
  previous_game_dir="$(mktemp -d "$HOME/galmaegiman-before-0.2.6-XXXXXX")"
  cd "$HOME"
  mv "$HOME/galmaegiman-confirmed" "$previous_game_dir/galmaegiman-confirmed"
  printf '기존 게임 보관 위치: %s/galmaegiman-confirmed\n' "$previous_game_dir"
  unzip -q -n "$full_zip" -d "$HOME"
  cd "$HOME/galmaegiman-confirmed"
  sha256sum -c docs/package-checksums.sha256 --quiet
  cp -a "$previous_game_dir/galmaegiman-confirmed/.env" .env
  cp -a "$previous_game_dir/galmaegiman-confirmed/storage" storage
  if [ -d "$previous_game_dir/galmaegiman-confirmed/config" ]; then
    cp -a "$previous_game_dir/galmaegiman-confirmed/config" config
  fi
  npm run setup
)
```

이 절차는 기존 폴더를 별도 이름 아래 보관한 뒤 새 코드를 설치합니다. 기존 DB, 사용자별 수집 이력, 재화, 인증키, 충전 설정, 저장된 공개 URL은 기존 폴더에서 복사합니다. 코드만 새 전체본으로 교체하므로 예전 테스트 파일의 패치 해시 불일치 검사를 거치지 않습니다. 기존 폴더와 그 안의 백업은 삭제하지 않습니다.

**오류가 나오면 여기서 멈추세요. 설치 완료 문구가 나오기 전에는 `npm start`를 실행하지 마세요.** 실패했다고 위 블록 전체를 다시 실행하지 말고 오류와 출력된 기존 게임 보관 위치를 확인해야 합니다. 자동으로 이전 코드로 되돌리는 절차는 아닙니다.

설치가 실패했어도 원본은 출력된 `galmaegiman-before-0.2.6-.../galmaegiman-confirmed` 안에 있습니다. 원본 게임을 재실행하려면 새 서버가 중지되어 있는지 확인하고, 출력된 그 실제 폴더로 이동해서 `npm start`를 실행할 수 있습니다. 폴더명을 추측해서 삭제하거나 DB를 초기화하지 마세요.

설치 완료 후:

```bash
cd ~/galmaegiman-confirmed
npm start
```

## B. 기존 데이터가 없는 환경에 신규 설치

`~/galmaegiman-confirmed`가 이미 있으면 이 절차는 중단됩니다. 기존 사용자라면 A를 사용하세요.

Ubuntu에서:

```bash
(
  set -e
  test ! -e "$HOME/galmaegiman-confirmed"
  full_zip="$HOME/storage/downloads/galmaegiman-random-75-tablet-v0.2.6.zip"
  test -f "$full_zip"
  unzip -tq "$full_zip"
  unzip -q -n "$full_zip" -d "$HOME"
  cd "$HOME/galmaegiman-confirmed"
  sha256sum -c docs/package-checksums.sha256 --quiet
  npm run setup
)
```

설치 완료 후:

```bash
cd ~/galmaegiman-confirmed
npm start
```

## HTTPS와 카카오 연결

기존 터널과 URL이 유효하고 A에서 설정을 복사했다면 그대로 사용합니다. 새 터널이 필요하면 별도의 Termux 세션을 열고 다음을 실행합니다. 이미 Ubuntu 세션이면 첫 줄을 제외합니다.

```bash
proot-distro login ubuntu --termux-home
cd ~/galmaegiman-confirmed
npm run tunnel
```

cloudflared는 기존 Ubuntu에 설치된 것을 사용합니다. 이 ZIP에 cloudflared 실행 파일은 포함하지 않습니다. 발급된 새 주소는 `config/public-url.txt`에 저장됩니다.

다른 Ubuntu 창에서 다음 명령으로 입력값을 확인합니다.

```bash
cd ~/galmaegiman-confirmed
node scripts/show-connection.mjs
```

- 카카오 스킬 URL / Test URL: 표시된 HTTPS 주소 + `/kakao/skill`
- 헤더 / 테스트 헤더: `X-Skill-Secret`과 표시된 값
- 신규 설치는 인증키가 새로 생성되므로 양쪽 헤더 값을 설정합니다.
- 터널 주소가 바뀌면 URL / Test URL도 변경·저장하고 운영 채널 반영을 확인합니다.
- 인증키가 보이는 출력은 다른 AI 협업용 소스와 함께 공개하지 않습니다.

기존 터널 주소를 수동 등록할 때는:

```bash
npm run url -- https://실제발급주소.trycloudflare.com
```

한글 예시는 실제 도메인으로 바꾸고 `/kakao/skill`은 붙이지 않습니다.

## 실행 확인

서버를 켠 뒤 다른 Ubuntu 창에서:

```bash
curl -fsS http://127.0.0.1:3000/health
curl -fsS http://127.0.0.1:3000/ready
```

`/health`는 `version: "0.2.6"`, `edition: "RANDOM_75"`를 반환해야 합니다. `/ready`는 `ready: true`를 반환해야 합니다.

카카오에서는 내정보·내갈매미·조합목록을 확인합니다. 뽑기는 실제 획득권을 사용합니다. 기존에 수집한 캐릭터는 업데이트 후에도 중복으로 판정되어 이미지가 생략되며, 상세 조회로 이미지를 볼 수 있습니다.

충전량 조회: `npm run refill`

충전량 변경 예: `npm run refill -- 10` (09/12/18시 공통값, 서버 재시작 불필요)

## 협업 환경의 검증

```bash
npm ci
npm run db:generate
npm run build
npm run data:check
npm run test:db
```

`test:db`는 별도의 임시 SQLite DB를 만듭니다. 일반 `npm test`만 실행하면 `TEST_DB_URL`이 없는 경우 DB 통합 테스트는 건너뜁니다. npm 의존성 설치에는 인터넷이 필요합니다.
