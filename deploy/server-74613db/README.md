# 서버 배포 묶음 (코드 커밋 74613db, claude/xp-finisher-split)

- 원본 코드 커밋: 74613db4b6d2f051b7e72b4e8d42afd863cf3fb9
  = backup/gmm-110-source-20261101(0b0fb8b) + 경험치 분배 변경 1개 커밋.
- 이전 묶음(36ef676)과 비교: 원래 GMM110 소스를 컴파일한 결과는 36ef676 dist와 파일 단위로 같고,
  다른 파일은 경험치 관련 7개(commander, normal-xp, story-unit-xp, roster60-adventure, roster60-battle,
  roster60-pvp, roster60-routes)와 새 파일 2개(finisher-split, unit-xp-pacing)뿐이다. 스토리 데이터·화면 묶음(play, duel)은 같다.
- 내용: dist/ 전체, prisma/schema.prisma, 열 추가 SQL 1개(이미 있으면 건너뜀), data/story/story-mode-v1.json,
  apply-additive-sql.py, **data/test-profiles/five-stat-device-test-v1/의 경험치 정책 파일 3개(이번에 처음 포함)**.
- DB 변경 없음(스키마가 907e737·36ef676 묶음과 같음). package-lock은 운영과 같음(635e79bc…) → npm 설치·Prisma 생성 불필요.

## 바뀌는 경험치 규칙 (자세히: galmaegiman-confirmed/docs/XP-FINISHER-SPLIT-20261103.md)

- 이긴 전투: 마무리 유닛(이긴 순간 나와 있던 내 유닛)은 보상 W 전액, 편성된 나머지(미출전 예비 포함)는 W의 절반(내림).
- 진 전투·항복·무승부·도주: 0. (일반전 패배 50%도 없어진다.)
- 최소 보상: W가 마무리 유닛의 「다음 레벨 필요 XP × 2/3(올림)」보다 작으면 그 값까지 올린다(적정 레벨 1.5승에 1레벨).
  기준 레벨은 마무리 유닛 레벨, 적이 더 낮으면 적 레벨.
- 적용: 스토리(의뢰), 일반전(사람·AI), 모험. 랭크전(0)은 그대로. 예전 단일 연습전은 1마리 지급 방식은 그대로, 최소 보상만 적용.
- 배포 전에 이미 고정된 대기·방·전투는 예전 규칙으로 정산한다. 확정된 원장·영수증은 다시 지급하지 않는다.
- 정책 파일: story-unit-xp-policy.json(v2), normal-xp-policy.json(v3), unit-xp-pacing-policy.json(1.5승판).
  모두 시험값(productionBalanceApproved:false). 묶음을 풀면 서버 폴더의 같은 파일을 덮어쓴다.

## 시험

- 격리 SQLite `npm run test:db`: 1247개 중 1236 통과. 실패 11개는 원래 소스(0b0fb8b)에서도 같은 11개
  (진행 초기화 4, 초기화 세션 1, 일일 진행 2, 시험 프로필 1, 스토리 성장 2, 공개 웹 1 — 공개 웹은 build 전 실행 때문).
  스토리 성장 2개는 새 규칙에서 더 높은 레벨로 더 멀리 진행한 뒤 최고 레벨(60)에서 지는 전투가 남아 실패한다(원래는 Lv35에서 STORY_12).
- tsc 오류 0.
- 리허설: 원래 GMM110 폴더 복사본에 이 묶음을 풀고 새 임시 DB에 SQL 적용(1개 이미 있음·무결성 ok)·시드 후
  같은 dist 기동 → /health ok, /ready true.
- 실제 운영 DB·실기기에서는 검증하지 않았다.
