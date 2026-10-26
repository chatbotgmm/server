# 갈매미맨 서버 외부 빌드·시험 보고서

- 커밋: 2e16916699226f7079c8c981942761664a027953 (detached worktree, 코드 수정 없음)
- 작업 폴더: galmaegiman-confirmed
- 환경: Linux, 메모리 15GB, CPU 4. Node v22.22.0, npm 10.9.4
- package-lock.json SHA256: 635e79bc225fb3fbd79f5f813409b53793851a8c6e0acb68e95f16a204e9af1b
- prisma/schema.prisma SHA256: 04a1fb2e400b52df0ccfa188e5a0b71ff5e0ee736e4baca8e34e3ef84dc589e1
- .env 없음(.env.example만 존재), DATABASE_URL·TEST_DB_URL 환경변수 해제 상태에서 실행. 운영 DB 미사용.

## 단계와 결과
| 단계 | 명령 | 결과 |
|---|---|---|
| 의존성 | npm ci --no-audit --no-fund | 성공, 138 패키지 |
| Prisma | npx prisma generate | 성공 |
| 빌드 | NODE_OPTIONS=--max-old-space-size=4096 npm run build | 성공, 약 26초, tsc+esbuild 최대 RSS 약 1,149,056KB(약 1.1GB) |
| 격리 DB 시험 | npm run test:db (임시 SQLite /tmp/galmaegiman-tests-xgQnRY) | 67파일 / 691시험 모두 통과 |
| Node 계약시험 | node --experimental-vm-modules --test (6개 파일) | 48 / 48 통과 |

Node 시험 파일: test/roster60-presentation.test.mjs, test/roster60-opponent-intel.test.mjs, test/menu-policy.test.mjs,
test/menu-reopen.test.mjs, test/menu-local-reopen.test.mjs, test/web-maintenance.node.mjs

## 압축
- 파일: galmaegiman-server-dist-2e16916.tar.gz
- SHA256: 4eb3635771c033c5cb2095ff139719c232b7dfb561c22ea6d64c8deed762ec62
- 크기: 667672 bytes, dist 파일 158개(목록·크기·SHA256: dist-files.tsv)
- 필수 파일 포함: dist/src/server.js, dist/play/game3d.js, dist/duel/duel.js
- 내용은 dist/ 하나뿐. .env, DB, storage, node_modules, 인증키, APK/AAB 없음(목록 검사 0건).
- 참고: tsconfig include에 따라 dist/test, dist/prisma(seed), dist/scripts 컴파일 결과도 dist 안에 있어 그대로 포함.
- tar는 이름순·소유자 0·고정 mtime·gzip -n으로 만들었다.

## 첨부 로그
srv-build.log, srv-testdb.log, srv-node.log
