# 서버 패치 7차: 파일 주소 버전을 내용 지문으로 (2026-11-07)

**6차 패치(`deploy/server-gmm111-web4-20261107`)가 적용된 서버에만** 쓴다. 6차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 바뀌는 것
- 증상: 6차를 적용했는데 웹게임에 정렬·묶기가 안 보임(강력 새로고침하면 보임).
- 원인: 페이지가 파일 주소 뒤에 수정 시각을 붙여(`game.js?v=…`) 새 파일을 받게 하는데, 배포 묶음 파일의 수정 시각이 날짜로 고정돼 5차·6차 game.js 주소가 같았다. 브라우저·Cloudflare(최대 4시간)가 예전 파일을 보여 줌.
- 수정: 수정 시각 대신 파일 내용 지문(sha256 앞 12자)을 붙인다. 내용이 바뀌면 주소가 반드시 바뀐다.

## 내용
- 묶음 1개: dist/src/site.js. 원본: 브랜치 claude/charming-bardeen-sct1b0 커밋 63099ba. DB 변경 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web5-20261107/apply.sh > /tmp/gmm-apply7.sh && sudo bash /tmp/gmm-apply7.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 6차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web5-20261107/rollback.sh > /tmp/gmm-rollback7.sh && sudo bash /tmp/gmm-rollback7.sh <백업 폴더>
```
