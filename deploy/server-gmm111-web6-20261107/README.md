# 서버 패치 8차: 웹게임 2단계 (2026-11-07)

**7차 패치(`deploy/server-gmm111-web5-20261107`)가 적용된 서버에만** 쓴다. 7차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 바뀌는 것 (웹게임 /game60)
- 도감(60종, 등급 거르기·보유만), 조합(재료 자동 선택·미리보기·기술 계승), 자유 모험(편성·연속 전투·정상 귀환으로 보상), 시장(새우gang으로 지원서·아이템), 출석·미션.
- 서버 기능은 앱과 같은 것(roster60 craft/collection/adventure, 공용 shop/items/attend/claim/daily)만 쓴다. 서버 코드 변경 없음.

## 내용
- 묶음 2개: public/game60/game.js, game.css. 원본: 브랜치 claude/charming-bardeen-sct1b0 커밋 fda281f. DB 변경 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web6-20261107/apply.sh > /tmp/gmm-apply8.sh && sudo bash /tmp/gmm-apply8.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 7차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web6-20261107/rollback.sh > /tmp/gmm-rollback8.sh && sudo bash /tmp/gmm-rollback8.sh <백업 폴더>
```
