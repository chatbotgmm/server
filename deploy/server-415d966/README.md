# 서버 배포 묶음 (코드 커밋 415d966, fix/game-polish-20261027)

- 원본 코드 커밋: 415d9661c2bbe894d9fea1841d5a3fa9dc96031f
- ffba072 묶음의 상위 집합: dist/ 전체, prisma/schema.prisma, 열 추가 SQL 1개, data/story/story-mode-v1.json,
  apply-additive-sql.py. ffba072 배포 전이든 후든 그대로 쓴다(SQL은 이미 있으면 건너뜀).
- ffba072 대비 서버 변경: src/story/service.ts 하나(정책 없이 수락한 예전 의뢰도 새 출항 때 현재 정책을 고정해 XP 지급).
  DB 변경 추가 없음. 스토리 개체 XP 정책 파일(data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json)이
  운영 폴더에 없으면 이 수정이 있어도 스토리 XP는 0이다. 이 묶음은 그 파일을 넣지 않는다.
- package-lock은 운영과 같음(635e79bc…). Prisma Client만 다시 생성.
- 시험: 서버 전체 1148개 중 1138 통과, 실패 10개는 ffba072와 같은 목록(7개는 06230cf 기준부터 실패, 3개는 계승 선택 없이 조합 확정하는 예전 시험).
  tsc 오류 0. 06230cf 스키마 임시 DB에 SQL 적용·시드 후 같은 dist 기동 → /health ok, /ready true.
