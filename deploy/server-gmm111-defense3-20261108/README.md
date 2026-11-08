# 서버 패치 12차: 랜덤 디펜스 파일 주소에 내용 지문 (2026-11-08)

**11차 패치(`deploy/server-gmm111-defense2-20261108`, 디펜스 2판)가 적용된 서버에만** 쓴다. 지금 파일이 11차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 왜
11차를 적용해 서버 파일은 2판이 됐는데 휴대폰에서는 1판이 계속 보였다. 확인해 보니 서버는 `/defense` js·css를 `no-cache`로 보내지만,
Cloudflare를 거치면 `cache-control: max-age=14400`(4시간)으로 바뀌어 브라우저가 예전 파일을 계속 썼다(화면 `/defense`는 캐시되지 않음).
웹게임 `/game60`처럼 파일 주소에 내용 지문(`?v=…`)을 붙여, 파일이 바뀌면 주소도 바뀌게 한다.

## 바뀌는 것(가벼운 패치)
- 지금 실행 폴더의 `public/defense/` 파일 3개만 바꾼다: `index.html`(defense.js·css 주소에 지문), `defense.js`(engine.js 주소에 지문), `engine.js`(data.js 주소에 지문). 게임 내용은 11차와 같다.
- 실행 폴더 복사·서버 코드·DB·설정 변경·재시작 없음. 바꾸기 전 파일은 백업, 확인 실패 시 바로 되돌림.
- 앞으로 디펜스 파일을 바꿀 때는 `scripts/web/stamp-defense.py`로 지문을 다시 붙인다.

## 시험(11차 상태 서버 복제본)
- 적용 → 확인 통과, 지문 주소 4개 200, 휴대폰 크기 위습 소환·옮기기·조합·진행 오류 없음. 되돌리기 → 11차 상태 복귀, 다시 적용 성공.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense3-20261108/apply.sh > /tmp/gmm-apply12.sh && sudo bash /tmp/gmm-apply12.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 11차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense3-20261108/rollback.sh > /tmp/gmm-rollback12.sh && sudo bash /tmp/gmm-rollback12.sh <백업 폴더>
```
