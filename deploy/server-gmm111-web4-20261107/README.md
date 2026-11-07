# 서버 패치 6차: 웹게임 정렬·같은 갈매미 묶기 (2026-11-07)

**5차 패치(`deploy/server-gmm111-web3-20261107`)가 적용된 서버에만** 쓴다. 5차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 바뀌는 것 (웹게임 /game60)
- 내 갈매미·편성: 정렬 4가지(레벨순·종족순·이름순·등급순), 고른 값은 브라우저에 기억. 종족은 방범대 등록증 v10 자료(검토안).
- 같은 갈매미 묶기(기본 켜짐): 같은 종은 한 칸에 ×N. 내 갈매미 상세에서 개체(레벨별) 칩으로 바꿔 보기.
- 편성: 묶인 칸은 누를 때마다 레벨 높은 개체부터 들어가고, 오른쪽 목록을 누르면 빠짐.

## 내용
- 묶음 2개: public/game60/game.js, game.css. 원본: 브랜치 claude/charming-bardeen-sct1b0 커밋 ee712a1. DB 변경 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web4-20261107/apply.sh > /tmp/gmm-apply6.sh && sudo bash /tmp/gmm-apply6.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 5차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web4-20261107/rollback.sh > /tmp/gmm-rollback6.sh && sudo bash /tmp/gmm-rollback6.sh <백업 폴더>
```
