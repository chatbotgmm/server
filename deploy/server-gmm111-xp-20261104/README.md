# 서버 패치: GMM111 운영 상태 + 경험치 분배 (2026-11-04)

지금 운영 서버(907e737 + 11-02 22:35 GMM111 직접 반영분)에 **경험치 변경만** 더한 12개 파일 패치다.
GMM111 증강 기능·설정(`ROSTER60_CURRENT_V3_ENABLED=true`, battle-policy의 증강 v3)은 그대로 유지한다.
앞의 `deploy/server-74613db/`(GMM110 기반)는 GMM111을 지우므로 쓰지 않는다.

## 바뀌는 경험치 규칙
- 이긴 전투: 마무리 유닛(이긴 순간 나와 있던 내 유닛)은 보상 W 전액, 편성된 나머지(미출전 예비 포함)는 W의 절반(내림).
- 진 전투·항복·무승부·도주: 0 (일반전 패배 50%도 없어짐).
- 최소 보상: W가 마무리 유닛의 「다음 레벨 필요 XP × 2/3(올림)」보다 작으면 그 값까지 올림(적정 레벨 1.5승에 1레벨).
  기준 레벨은 마무리 유닛 레벨, 적이 더 낮으면 적 레벨.
- 적용: 스토리 의뢰, 일반전(사람·AI), 모험. 랭크(0) 그대로. 이미 시작한 대기·방·전투는 예전 규칙으로 정산.
- 자세히: claude/xp-finisher-split 브랜치의 galmaegiman-confirmed/docs/XP-FINISHER-SPLIT-20261103.md

## 만든 방법
- 서버 실행 폴더 스냅샷: 브랜치 `server-snapshot/gmm111-20261103` (11-03 23:55, .env·DB 제외).
  스냅샷의 전체 파일 지문을 907e737 묶음과 비교: 다른 dist 파일 6개(roster60-battle, roster60-pvp, roster60-routes,
  site, story/routes, story/service) + 새 파일 4개(roster60-augments, roster60-current-augment-gate·catalog·runtime) + battle-policy.json(증강 v3 추가)뿐.
- GMM111 서버 소스(.ts)는 저장소·서버에 없어서, 컴파일된 파일에서 3자 병합했다.
  기준 = 36ef676 묶음(= GMM110 컴파일 결과), 우리 = 서버 파일, 상대 = 경험치 빌드(커밋 74613db).
  충돌 6곳(모두 같은 줄에 양쪽이 추가한 것)을 양쪽을 살려 해결. 파일별 변경 줄 수가 「36→경험치」와 정확히 같음(7개 파일 모두).
  36ef676에만 있던 변경(예비 대원 내성 표시, 스토리 빌런 1.5→1.2)은 서버에 없던 것이라 넣지 않았다.

## 내용 (서버 실행 폴더 galmaegiman-confirmed 기준 경로)
- 바뀜: dist/src/progression/commander.js, normal-xp.js, story-unit-xp.js, dist/src/roster60-adventure.js, roster60-battle.js, roster60-pvp.js, roster60-routes.js
- 새 파일: dist/src/progression/finisher-split.js, unit-xp-pacing.js
- 정책: data/test-profiles/five-stat-device-test-v1/story-unit-xp-policy.json(새로), normal-xp-policy.json(v3로 교체), unit-xp-pacing-policy.json(새로)
- DB 스키마 변경 없음, 새 설치 없음.

## 시험
- 합친 파일 101개 모듈 문법·불러오기 오류 0.
- 컴파일된 전체 시험(1199개)을 합친 파일과 경험치 빌드에 각각 실행: 경험치 시험은 모두 통과.
  합친 파일에서만 실패한 13개는 경험치와 무관 — 홈페이지 새 화면 파일(public/site/home-v4)이 시험 폴더에 없음 4,
  36ef676에만 있던 예비 대원 내성 표시 2, 36ef676에만 있던 빌런 1.2 조정 7(서버는 지금 1.5 그대로).
- 증강이 켜진 조건(증강 v3 정책 + 환경값 켜짐 + 새 앱)에서 모험 전투를 실제 서비스 코드로 증강 선택 → 승리 → 정산:
  마무리 775 / 나머지 387·387 (Lv15 1.5승 최소, 원천 10보다 큼) 기대와 일치.
- 같은 조건으로 서버 기동: /health ok, /ready true. 실제 운영 DB·실기기에서는 검증하지 않았다.

## 서버 적용 (Session Manager)
`apply.sh`가 하는 일: 사전 확인(실행 폴더·설정·디스크·서버 상태) → 지금 폴더가 스냅샷 이후 그대로인지 지문 확인 →
새 폴더 `/home/ubuntu/gmm-releases/gmm111-xp-20261104`로 복사 + 패치 → DB·설정 백업 → 실행 폴더 전환·재시작 → /health·/ready 확인.
하나라도 맞지 않으면 아무것도 바꾸지 않고 멈추고, 재시작 후 확인에 실패하면 이전 폴더로 자동 되돌린다.

```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-xp-20261104/apply.sh > /tmp/gmm-apply.sh && sudo bash /tmp/gmm-apply.sh
```

되돌리기(apply.sh가 마지막에 알려 준 백업 폴더 사용):

```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-xp-20261104/rollback.sh > /tmp/gmm-rollback.sh && sudo bash /tmp/gmm-rollback.sh <백업 폴더>
```

재시작하는 몇 초 동안 접속 중인 사람은 잠깐 끊길 수 있다.
