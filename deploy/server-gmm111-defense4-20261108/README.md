# 서버 패치 13차: 랜덤 디펜스 기본 속도 1.5배 (2026-11-08)

**12차 패치(`deploy/server-gmm111-defense3-20261108`)가 적용된 서버에만** 쓴다. 12차 상태가 아니면 아무것도 바꾸지 않고 멈춘다(12차를 먼저 적용).

## 바뀌는 것(가벼운 패치)
- 사용자 요청: 게임 속도를 더 빠르게. 기본 게임 속도를 1.5배로(배속 버튼 ×1·×2·×3도 각각 1.5배 빨라짐).
- 시간이 통째로 빨라지는 방식이라 난이도(봇 시험 결과)는 그대로다.
- 지금 실행 폴더의 `public/defense/` 파일 2개(defense.js, 지문이 바뀐 index.html)만 바꾼다. 복사·서버 코드·DB·재시작 없음, 바꾸기 전 파일 백업·실패 시 바로 되돌림.

## 시험(12차 상태 서버 복제본)
- 적용 → 확인 통과, 되돌리기 → 12차 상태 복귀, 다시 적용 성공. 같은 실제 시간에 처치 수 38 → 66(빨라짐 확인), 오류 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 12차 다음에)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense4-20261108/apply.sh > /tmp/gmm-apply13.sh && sudo bash /tmp/gmm-apply13.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 12차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense4-20261108/rollback.sh > /tmp/gmm-rollback13.sh && sudo bash /tmp/gmm-rollback13.sh <백업 폴더>
```
