# 서버 패치 4차: 예전 웹게임 → 새 웹게임 (2026-11-07)

**3차 패치(`deploy/server-gmm111-web-20261106`)가 적용된 서버에만** 쓴다. 3차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 바뀌는 것
- 사용자 지적: 웹게임에 로그인하면 옛날 게임이 나옴. 원인: 카카오 로그인 뒤 거치는 `/my` 화면과 `/account`, 로그인 기본값이 모두 예전 웹게임 `/game`(60종 이전 규칙)으로 보냈음.
- `/game`·`/account` → 새 웹게임 `/game60`으로 이동. 로그인 뒤 기본으로 가는 곳도 `/game60`.
- `/my`(카카오 로그인 복귀 화면)가 `/game#메뉴` 대신 `/game60`으로 보냄. 앱 게임 창(`/my?g=1`)은 그대로.

## 내용
- 묶음 2개: dist/src/site.js, public/site/my.html. 원본: 브랜치 claude/charming-bardeen-sct1b0 커밋 25a6c72.
- DB 변경 없음.

## 시험
- 전체 시험 526개 통과. 3차 상태 복제본에 적용해 실행: /game·/account·카카오 복귀 → /game60, 로그인 안 됨 → /login?next=/game60.
- apply.sh는 3차 확인 항목 + /game 이동·/my 복귀 대상을 확인하고, 실패하면 3차 상태로 자동 되돌림.

## 서버 적용 (Session Manager, 토큰 필요 — 3차 때와 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web2-20261107/apply.sh > /tmp/gmm-apply4.sh && sudo bash /tmp/gmm-apply4.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 3차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web2-20261107/rollback.sh > /tmp/gmm-rollback4.sh && sudo bash /tmp/gmm-rollback4.sh <백업 폴더>
```
