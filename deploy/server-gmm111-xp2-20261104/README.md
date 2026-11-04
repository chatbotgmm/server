# 서버 패치 2차: 경험치 보상 통일 · 랭크 경험치 · 자유탐험 적 레벨 (2026-11-04)

**1차 패치(`deploy/server-gmm111-xp-20261104`)가 적용된 서버에만** 쓴다. 1차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.
GMM111 증강 기능·설정은 그대로 유지한다.

## 바뀌는 것
- 모든 전투(스토리·일반전·모험·연습전·랭크) 경험치 보상 W를 하나로 통일:
  전투에 나온 모든 유닛(양쪽 편성 전원, 예비 포함)의 평균 레벨 L(내림)에서
  마무리 유닛이 1.5승에 1레벨 오르는 양 = ceil((L→L+1 필요 XP) × 2/3).
  스토리 고정 보상·일반전 원천표·모험 winXp는 쓰지 않는다.
- 분배는 그대로: 마무리 유닛 W, 편성된 나머지 W/2(내림), 패배·항복·무승부·도주 0.
- 연습전도 편성 전원에게 같은 방식으로 나눈다.
- 랭크도 같은 규칙(랭크는 30레벨로 싸워 L=30). 랭크는 60레벨 이상만 들어갈 수 있어,
  환생하지 않은 개체(상한 60)는 실제로 0, 환생 개체(상한 99)만 성장한다. 랭크 결과 화면에 경험치 표시.
- 자유탐험(스토리가 아닌 모험) 적 레벨 = 내 편성 평균 ±2(1~60). 스토리·연습전 적 레벨은 그대로.
- 이미 시작한 대기·방·전투는 시작할 때 고정된 규칙으로 정산한다.
- 예(u01): 내 15레벨 3마리 vs 적 1레벨 → L=11 → 마무리 487, 나머지 243씩.

## 내용 (6개)
- dist/src/progression/finisher-split.js, normal-xp.js, unit-xp-pacing.js
- dist/src/roster60-battle.js, roster60-pvp.js
- data/test-profiles/five-stat-device-test-v1/unit-xp-pacing-policy.json (`unit-xp-unified-average-1_5-20261104-v1`)
- 원본 소스: 브랜치 claude/xp-finisher-split 커밋 2d7b38b. 1차 서버 파일에 「1차 빌드→2차 빌드」 변경만 3자 병합(충돌 1곳, 양쪽 살림).
  파일별 변경 줄 수가 「1차 빌드→2차 빌드」와 정확히 같음.
- DB 스키마 변경 없음, 새 설치 없음.

## 시험
- 소스 전체 시험(test:db): 1253개 중 실패 11개는 원래 소스와 같은 묶음(경험치와 무관).
- 합친 파일 모듈 101개 불러오기 오류 0.
- 증강이 켜진 조건에서 자유탐험 3회: 적 레벨 16·14·15(내 평균 15 ±2), 정산 775/387/387·699/349/349·775/387/387로 기대와 일치.

## 서버 적용 (Session Manager)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-xp2-20261104/apply.sh > /tmp/gmm-apply2.sh && sudo bash /tmp/gmm-apply2.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 1차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-xp2-20261104/rollback.sh > /tmp/gmm-rollback.sh && sudo bash /tmp/gmm-rollback.sh <백업 폴더>
```
재시작하는 몇 초 동안 접속 중인 사람은 잠깐 끊길 수 있다.
