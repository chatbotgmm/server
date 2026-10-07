# 갈매미맨

카카오톡 **채널 1:1 챗봇**에서 즐기는 수집·조합 게임과 공식 홈페이지입니다.
돌로 된 갈매미 145종을 뽑고, 같은 자리로 발령 내 합체시키고, 숨은 히든 21종을 찾고, 최상위 원형을 탐험에 보냅니다.
결제·현금 환전 기능은 없습니다.

- 카톡 채널: https://pf.kakao.com/_GIAaX
- 홈페이지: https://gmm.dpdns.org (세계관·이벤트·도감·조합법·히든 단서·랭킹·검색·프로필)
- 수비대 대결(포켓몬식 턴제 + 증강체) 시험판: https://gmm.dpdns.org/battle ([docs/BATTLE-DESIGN.md](docs/BATTLE-DESIGN.md))
- 횡스크롤 전투 시험판(2.5D, `?2d`는 예전 2D): https://gmm.dpdns.org/play (`public/play/`, 그림 요청문 [docs/PLAY-SPRITE-PROMPTS.md](docs/PLAY-SPRITE-PROMPTS.md))
- 변경 이력·버전별 배포 메모: [docs/V0.3-DEPLOY.md](docs/V0.3-DEPLOY.md)

## 구성
| 부분 | 내용 |
|---|---|
| 서버 | Node.js 22 · Fastify · TypeScript (`src/`) |
| DB | SQLite + Prisma (`storage/confirmed.db`, 저장소에 넣지 않음) |
| 게임 규칙 | `src/game.ts` (명령 처리), `src/gacha.ts`, `src/expedition.ts` |
| 카카오 응답 | `src/kakao.ts` (카드 글자 예산 1,150바이트, 공유 버튼) |
| 홈페이지 | `src/web.ts` (서버가 HTML을 직접 만듦) |
| 유닛·조합식 | `data/units.json` (145종) |
| 수치·이벤트·쿠폰 | `data/economy.yaml` (뽑기 확률, 보상, 초대, 미션, 이벤트 기간 등) |
| 세계관 글 | `data/world.md` (홈페이지 /world, 고치면 바로 반영) |
| 세계관 설정집 | `design/WORLD.md`, `design/갈매미맨_세계관_스토리라인.docx` |
| 그림 | `public/images/*.png`, 카드용 작은 그림 `public/thumbs/*.jpg` ([파일명](docs/IMAGE-FILES.md)) |

## 운영 (EC2, ap-northeast-2)
- 서비스: `gmm-server`(게임 서버), `gmm-named-tunnel`(Cloudflare 고정 터널 → gmm.dpdns.org), `gmm-backup.timer`(매일 04:30 백업)
- 카카오 i 오픈빌더 스킬 URL: `https://gmm.dpdns.org/kakao/skill` (헤더 `X-Skill-Secret`)
- 업데이트(한 줄): 서버를 켠 채로 설치·빌드·테스트 → 잠깐 멈춤 → DB 백업·반영 → 시작
  ```bash
  sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/update.sh
  ```
  새 커밋이 없어도 다시 설치하려면 끝에 `-f`. 설치·테스트가 실패하면 서버를 멈추지 않고 이전 코드로 되돌립니다.
- 도메인·터널: [docs/DOMAIN-SETUP.md](docs/DOMAIN-SETUP.md)
- 백업을 서버 밖에도: [docs/BACKUP-OFFSITE.md](docs/BACKUP-OFFSITE.md)
- 비밀값(공개 금지): `.env`의 `SKILL_SECRET`·`ADMIN_CODE`, `~/.cloudflared/`, GitHub 토큰, 쿠폰 코드
- `.env`의 `KAKAO_BOT_ID`를 넣어 두면 다른 봇의 요청을 거절합니다. 값은 서버 로그의 `bot` 항목에서 확인:
  ```bash
  sudo journalctl -u gmm-server -n 50 --no-pager | grep -o '"bot":"[^"]*"' | tail -1
  ```

## 개발
```bash
npm ci
npm run test:db      # 임시 DB로 전체 테스트(실제 DB는 건드리지 않음)
npm run data:check   # 유닛·조합식·수치 검사
npm run build
```
처음 설치는 `bash scripts/setup.sh` (단계별: `--prepare` 설치·빌드·테스트 / `--apply` DB 백업·반영).
**유저 데이터를 지우는 `npm run reset-data`는 개편 때만 씁니다.**

## 게임 안의 주요 명령
뽑기 · 뽑기권받기 · 조합가능 · 조합목록 · 조합식 이름 · 조합 고르기(재료로 직접, 예전 '합치기') · 탐험 · 미션 · 칭호 · 도감 · 내갈매미 · 상점 · 교환 · 팔기 · 땅콩떼기 · 랭킹 · 닉네임 이름 · 초대코드 · 초대 코드 · 쿠폰 코드 · 이벤트 · 튜토리얼 · 확률 · 보상

## 문서
- 현재: `docs/V0.3-DEPLOY.md`(버전별 변경), `docs/DOMAIN-SETUP.md`, `docs/BACKUP-OFFSITE.md`, `docs/IMAGE-FILES.md`, `docs/ART-DIRECTION-V2.md`, `docs/CHATGPT-PROMPTS.md`
- 이전 판(0.2.x 갤탭·75종) 기록: `docs/archive/` — 지금 구조와 다르니 참고용으로만 보세요.
