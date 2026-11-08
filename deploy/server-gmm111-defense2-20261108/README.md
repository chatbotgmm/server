# 서버 패치 11차: 랜덤 디펜스 2판 (2026-11-08)

**10차 패치(`deploy/server-gmm111-defense-20261108`, 디펜스 1판)가 적용된 서버에만** 쓴다. 지금 파일이 10차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 가벼운 패치(디스크 거의 안 씀)
- 실행 폴더를 복사하지 않는다. 지금 실행 폴더의 `public/defense/` 파일 3개(data.js·defense.js·engine.js)만 바꾼다.
- 서버 코드·DB·설정은 바꾸지 않고 **재시작도 하지 않는다**(이 파일들은 요청마다 디스크에서 읽힌다).
- 바꾸기 전 파일 3개는 `/home/ubuntu/gmm-deploy-backups/gmm111-defense2-…`에 두고, 확인에 실패하면 바로 되돌린다.

## 바뀌는 것: 사용자와 다시 정한 규칙(2판)
- 위습: 처음 커먼 랜덤 위습 5개 + 웨이브마다 1개. 커먼·언커먼·레어·에픽 위습, 전설 위습은 「위습 찾기」 재주 갈매미(황금·드워프·룬장인·거장·금각)가 적을 쓰러뜨릴 때 확률로만.
- 타격수 = 물딜, 술사 = 마딜, 고체력·기습 = 딜이 낮은 보조(방깍·스턴·이속감소 / 마방깍·마딜증폭). 적은 방어력·마법방어가 따로 있고 웨이브가 일반·갑옷·결계로 바뀐다.
- 갈매미 자유 배치·겹침 허용(수 제한 없음), 끌어 옮기기·빈 곳 끌어 여러 마리 고르기·누른 곳으로 이동.
- 조합법 52개 전부(히든 포함), ☆ 조합 목표 → 재료에 같은 색 테두리.
- 웨이브마다 35마리, 70마리 넘은 동안 1초마다 경고(누적 20회 넘으면 패배). 보스는 그 라운드 안에 못 잡으면 패배, 보상은 위습.
- 외부탐사(8웨이브부터): 메인 스토리 7장 지역에 갈매미를 보내 점점 높은 위습.
- 난이도: 자동 봇 300판 기준 클리어 12%, 경고 패배 13%, 나머지는 10·20·30·40웨이브 보스(2%·23%·34%·16%).

## 시험(10차 1판 상태 서버 복제본)
- 적용 → 확인 항목 모두 통과, 파일 3개가 2판과 같음. 되돌리기 → 1판으로 복귀. 다시 적용 성공. 이미 2판인 상태에서 또 적용하면 3단계에서 멈춤.
- 휴대폰 크기로 위습 소환·끌어 옮기기·여러 마리 고르기·누른 곳으로 이동·조합·2배속 진행 오류 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense2-20261108/apply.sh > /tmp/gmm-apply11.sh && sudo bash /tmp/gmm-apply11.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 디펜스 1판으로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense2-20261108/rollback.sh > /tmp/gmm-rollback11.sh && sudo bash /tmp/gmm-rollback11.sh <백업 폴더>
```
