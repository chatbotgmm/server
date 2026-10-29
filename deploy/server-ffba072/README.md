# 서버 배포 묶음 (코드 커밋 ffba072, fix/game-polish-20261027)

- 원본 코드 커밋: ffba072 (통합 후속 ZIP v1 c059552 + 목표창 기본 접힘·시험 수정)
- 내용: dist/ 전체, prisma/schema.prisma, prisma/migrations/20261028_roster60_skill_inheritance/migration.sql,
  data/story/story-mode-v1.json(주민 대사 교정), apply-additive-sql.py
- DB 변경: Roster60Owned.skillInheritanceJson nullable TEXT 열 추가 1개(기존 데이터 변경·삭제 없음).
  prisma/manual의 같은 SQL은 쓰지 않는다(내용 동일, 기존 절차대로 migrations 쪽만 적용).
- package-lock은 운영(fa44401 이후)과 같다 → npm 설치 불필요, Prisma Client만 다시 생성.
- 외부 환경(Node v22.22.0)에서 prisma generate → npm run build(4GB 힙). tsc 오류 0.
- 시험: 서버 191/191, 추가 라우트 36/36. 전체 1138개 중 1128 통과, 실패 10개 중 7개는 기준 06230cf에서도 같은 실패,
  3개는 예전 시험이 계승 선택 없이 조합 확정(새 규칙상 409).
- 리허설: 06230cf 스키마 임시 DB에 SQL 적용(1개 적용·재실행 건너뜀·무결성 ok·새 스키마와 차이 없음) →
  새 dist 기동 /health ok, /ready true.
- 앱 org.gmm 108과 함께 전환해야 한다(구앱 106/107: 4마리 이상 새 편성·계승 없는 조합 확정 거절).
