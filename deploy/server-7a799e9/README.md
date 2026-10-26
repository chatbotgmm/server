# 서버 배포 묶음 (코드 커밋 7a799e9)

- 원본 코드 커밋: 7a799e9cb638399bbdaaf921ead481248455d491
- 내용: dist/ 전체 + prisma/schema.prisma + prisma/migrations/20261026_roster60_observed_defense/migration.sql
- 외부 환경(Node v22.22.0)에서 npm ci → prisma generate → npm run build(4GB 힙). package-lock은 2e16916과 같음.
- 운영 2e16916 대비 서버 실행 변경: src(→dist), prisma(새 표 1개 추가). data/public/package 변경 없음.
- 새 표 SQL 리허설: 2e16916 스키마로 만든 임시 DB에 적용 → integrity ok, 새 스키마와 차이 없음.
- 격리 DB 시험 742개 중 25개 실패(시험 정리 단계 FK, 진행 초기화 도구 미등록=사용 안 함, 소스 문자열 검사 1).
- 단독 실행용 전체 프로젝트가 아니다. 기존 배포 폴더 복사본에 dist·prisma만 덮어쓴다.
