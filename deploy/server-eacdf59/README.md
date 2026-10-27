# 서버 배포 묶음 (코드 커밋 eacdf59, fix/game-polish-20261027)

- 원본 코드 커밋: eacdf594a1f2ad072df906e4769bbffbec25fbbb
- 내용: dist/ 전체만. fa44401 배포본 대비 서버 실행 변경은 src/battle/roster60-rules.ts 하나(집중 MP 회복 최대치의 25% → 50%).
- prisma 스키마·data·package·public 변경 없음 → DB 변경 없음, Prisma Client 재생성 불필요.
- 외부 환경(Node v22.22.0)에서 prisma generate → npm run build(4GB 힙). tsc 오류 0.
- 시험: roster60-battle·roster60-pvp·roster60-test-baseline 21개 통과, roster60-rules 7개 통과.
- fa44401 배포 폴더 복사본의 dist만 바꾼다.
