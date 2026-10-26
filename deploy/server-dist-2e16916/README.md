# 서버 dist 배포 파일 (커밋 2e16916)

- 원본 커밋: 2e16916699226f7079c8c981942761664a027953 (코드 수정 없음)
- 외부 환경(Node v22.22.0, npm 10.9.4)에서 `npm ci` → `prisma generate` → `npm run build`
  (`--max-old-space-size=4096`) 후 `dist/`만 묶은 파일이다. 단독 실행용 전체 프로젝트가 아니다.
- 같은 커밋 소스·node_modules가 있는 EC2 배포 폴더(galmaegiman-confirmed)에 `dist/`만 풀어 쓴다.
- 시험: 격리 SQLite `npm run test:db` 67파일/691시험 통과, Node 계약시험 48/48 통과.
- 상세: REPORT.md, 파일별 해시: dist-files.tsv
- SHA256: 4eb3635771c033c5cb2095ff139719c232b7dfb561c22ea6d64c8deed762ec62
