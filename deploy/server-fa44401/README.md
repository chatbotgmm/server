# 서버 배포 묶음 (코드 커밋 fa44401)

- 원본: 정책 통합(449e55d, tree 9ead0d74) + 스토리 성장 후속(c90d6b2, tree 66a11a26). fa44401은 앱 화면만 바꿔 서버 결과는 같다.
- 내용: dist/ 전체, prisma/schema.prisma, 새 표·열 SQL 8개, data/story/*.json,
  data/test-profiles/five-stat-device-test-v1/commander-policy.json·normal-xp-policy.json, apply-additive-sql.py
- story-unit-xp-policy.json은 넣지 않았다(스토리 개체 XP 꺼 둠). 스토리 자체도 ROSTER60_STORY_PROFILE이 없으면 503으로 닫혀 있다.
- 일반전 XP·방범대장 곡선은 시험값(productionBalanceApproved:false)이다. 사용자가 시험값 그대로 운영 배포를 선택했다.
- 외부 환경(Node v22.22.0)에서 npm ci → prisma generate → npm run build(4GB 힙). package-lock은 c37c24e와 같다.
- SQL 리허설: c37c24e·2e16916 스키마 임시 DB 각각에 적용 → 무결성 ok, 새 스키마와 차이 없음(빈 migrate diff). 두 번째 실행은 모두 건너뜀.
- 리허설 DB에서 새 dist 기동 → /health ok, /ready true.
- 단독 실행용 전체 프로젝트가 아니다. 기존 배포 폴더 복사본에 dist·prisma·data 일부만 덮어쓴다.
