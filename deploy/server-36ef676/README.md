# 서버 배포 묶음 (코드 커밋 36ef676, fix/game-polish-20261027)

- 원본 코드 커밋: 36ef676 (907e737 + UI 후속 통합 증분)
- 이전 묶음(ffba072·415d966·907e737)의 상위 집합: dist/ 전체, prisma/schema.prisma, 열 추가 SQL 1개,
  data/story/story-mode-v1.json, apply-additive-sql.py. 어느 단계에서 배포하든 그대로 쓴다(SQL은 이미 있으면 건너뜀).
- 907e737 대비 서버 변경: 예비 대원 ownResistance 읽기 필드(roster60-battle/pvp), 스토리 빌런 5종 hp·power 배수 1.5→1.2
  (data/story/story-mode-v1.json, 새 전투부터). DB 변경 추가 없음.
- 시험: 서버 전체 1184개 중 1174 통과(실패 10개는 이전과 같은 목록), tsc 오류 0,
  06230cf 스키마 임시 DB에 SQL 적용·시드 후 같은 dist 기동 → /health ok, /ready true.
- 스토리 XP 정책 파일은 넣지 않았다(415d966 README 참고).
