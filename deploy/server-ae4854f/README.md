# 서버 배포 묶음 (코드 커밋 ae4854f, fix/game-polish-20261027)

- 원본 코드 커밋: ae4854f6a4fdaf173e49984bbd23fc80cf8e97dd
- 내용: dist/ 전체만. eacdf59 배포본 대비 서버 변경은 src 4개(흑화 외형 roster60-appearance 추가, 상대 정보·전투 표시·스토리 서비스 수정).
- prisma 스키마·data·package·public 변경 없음 → DB 변경 없음, Prisma Client 재생성 불필요.
- 외부 환경(Node v22.22.0)에서 prisma generate → npm run build(4GB 힙). tsc 오류 0.
- 시험: roster60-appearance 단위 7개, 관련 통합 8파일 98개 통과. 임시 DB에서 /health·/ready 통과.
- eacdf59 배포 폴더 복사본의 dist만 바꾼다.
