# 서버 패치 10차: 갈매미맨 랜덤 디펜스 (2026-11-08)

**9차 패치(`deploy/server-gmm111-gmkakao-20261107`)가 적용된 서버에만** 쓴다. 9차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.
(7·8·9차를 아직 적용하지 않았다면 7차 → 8차 → 9차 → 10차 순서로 적용한다.)

## 바뀌는 것
- 새 주소 `https://gmm.dpdns.org/defense`: 로그인 없이 하는 미니 게임 「갈매미맨 랜덤 디펜스」(휴대폰 세로 기준, PC는 가운데에 같은 비율).
- 게임 파일 5개를 `public/defense/`에 더하고, `dist/src/site.js`에 `/defense`(화면)·`/defense/*`(js·css만) 주소를 덧붙인다. 기존 줄은 바꾸지 않는다(`patch_site.py`).
- 그림·글꼴은 이미 있는 `/game60/assets`를 같이 쓴다. 서버 기능(API)·DB는 쓰지 않는다. 기록(최고 웨이브)은 그 기기 브라우저에만 남는다.
- 모듈 파일이 서로 다른 판으로 섞이지 않게 `/defense` 파일은 매번 새로 받게 했다(no-cache, 전부 합쳐 약 60KB).

## 게임 규칙(요약)
- 가운데 5×6칸에 갈매미를 두고, 둘레 길을 도는 그림자 갈매미를 막는다. 「소환」은 커먼 8종 중 무작위.
- 강해지려면 게임 속 **공개 조합법 46개**대로 합친다(히든 조합법 6개는 브라우저 파일에 넣으면 드러나서 뺐다). 히든 갈매미는 10웨이브마다 나오는 보스를 잡으면 3마리 중 골라 얻는다.
- 조합법에서 ☆로 목표(최대 3개)를 정하면 판의 재료 갈매미에 같은 색 테두리가 생긴다.
- 길 위의 적이 70마리를 넘거나 보스를 60초 안에 못 잡으면 끝. 40웨이브 보스까지 잡으면 클리어. 새우로 소환·공격력 강화.
- 난이도: 자동 봇 500판(조합·소환·강화·겹친 커먼 판매) 기준 10웨이브 5% · 20웨이브 10% · 30웨이브 45% · 40웨이브 33%에서 끝나고 7% 클리어.

## 시험
- 9차 상태 서버 복제본에서: `/defense`·js·css 200(no-cache), 다른 확장자·경로 404, 휴대폰(390×844)·PC(1280×720)에서 시작·소환·조합·끌어 옮기기·2배속 진행 오류 없음.
- 아래 적용 확인 항목 모두 통과(패치 전 서버에서는 디펜스 4개 항목만 실패).

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense-20261108/apply.sh > /tmp/gmm-apply10.sh && sudo bash /tmp/gmm-apply10.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 9차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense-20261108/rollback.sh > /tmp/gmm-rollback10.sh && sudo bash /tmp/gmm-rollback10.sh <백업 폴더>
```
