# 서버 배포 묶음 (코드 커밋 907e737, fix/game-polish-20261027)

- 원본 코드 커밋: 907e737 (415d966 + 전투 아군 내성 읽기 필드)
- ffba072·415d966 묶음의 상위 집합: dist/ 전체, prisma/schema.prisma, 열 추가 SQL 1개, data/story/story-mode-v1.json,
  apply-additive-sql.py. 어느 단계에서 배포하든 그대로 쓴다(SQL은 이미 있으면 건너뜀).
- 415d966 대비 서버 변경: src/roster60-battle.ts, src/roster60-pvp.ts — 전투 응답 fighters[0].ownResistance(본인 출전 개체만).
  DB 변경 추가 없음. 구 앱은 새 필드를 무시한다.
- 시험: 서버 전체 1149개 중 1139 통과(실패 10개는 이전과 같은 목록), tsc 오류 0,
  06230cf 스키마 임시 DB에 SQL 적용·시드 후 같은 dist 기동 → /health ok, /ready true.
- 스토리 XP 정책 파일은 넣지 않았다(415d966 README 참고).
