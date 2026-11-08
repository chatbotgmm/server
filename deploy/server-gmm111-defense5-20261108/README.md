# 서버 패치 14차: 랜덤 디펜스 등급별 강화 (2026-11-08)

**13차 패치(`deploy/server-gmm111-defense4-20261108`)가 적용된 서버에만** 쓴다. 13차 상태가 아니면 아무것도 바꾸지 않고 멈춘다(12·13차를 먼저 적용).

## 바뀌는 것(가벼운 패치)
- 사용자 요청: 물딜·마딜 강화 → **등급별 강화**. 5칸: 커먼 · 언커먼 · 레어 · 에픽 · 전설 이상(전설·히든·제한·초월·불멸·영원).
- 한 단계마다 그 등급 갈매미 공격력 +14%. 비용(새우): 칸마다 기본 20·30·45·65·90에 단계마다 10·15·22·32·45씩 오름.
- 아래 판의 「물딜 강화」·「마딜 강화」 버튼은 「등급별 강화」 하나로 합치고, 누르면 5칸 강화 창이 열린다(칸마다 지금 단계·판에 있는 수·강화 버튼).
- 난이도: 자동 봇 250판 기준 클리어 10%, 경고 패배 12%(물딜·마딜 강화 때 12%·13%와 비슷하게 +14%로 맞춤).
- 지금 실행 폴더의 `public/defense/` 파일 3개(index.html·defense.js·engine.js)만 바꾼다. 복사·서버 코드·DB·재시작 없음, 바꾸기 전 파일 백업·실패 시 바로 되돌림.

## 시험(13차 상태 서버 복제본)
- 적용 → 확인 통과, 되돌리기 → 13차 상태 복귀, 다시 적용 성공. 휴대폰 크기로 강화 창에서 실제로 눌러 단계 오름, 플레이 오류 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 13차 다음에)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense5-20261108/apply.sh > /tmp/gmm-apply14.sh && sudo bash /tmp/gmm-apply14.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 13차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense5-20261108/rollback.sh > /tmp/gmm-rollback14.sh && sudo bash /tmp/gmm-rollback14.sh <백업 폴더>
```
