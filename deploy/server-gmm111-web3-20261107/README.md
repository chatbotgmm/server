# 서버 패치 5차: 웹게임 긴 목록 스크롤·모집 중복 처리 (2026-11-07)

**4차 패치(`deploy/server-gmm111-web2-20261107`)가 적용된 서버에만** 쓴다. 4차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.

## 바뀌는 것 (웹게임 /game60)
- 이야기 목록·내 갈매미·편성·모집 결과가 판 안에서 스크롤(다시 그려도 위치 유지). 페이지 넘김 대신 스크롤, 그림은 보이는 것부터.
- 모집 결과: 카드별 합류 버튼을 없애고 「모두 합류」만. 다시 뽑기는 그림 위 새로고침 아이콘. 「보유 중」·「중복」 표시.
- 「중복 정리」: 겹친 종은 한 마리를 먼저 합류시킨 뒤 나머지를 불합격(서버 규칙: 이미 가진 종만 불합격, 지원서 1장 환급). 확인 창을 거침.
- 카드가 눌려 그림·이름이 겹치던 것, 내 갈매미 상세 그림·판이 잘리던 것 수정.

## 내용
- 묶음 2개: public/game60/game.js, game.css. 원본: 브랜치 claude/charming-bardeen-sct1b0 커밋 9040fe5. DB 변경 없음.

## 시험
- 4차 상태 복제본에 적용해 실행, 확인 항목 통과. 갈매미 35마리·모집 10회×4·중복 정리(지원서 6장 환급)·의뢰 40개 목록으로 화면 시험, 화면 밖·오류 0.

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web3-20261107/apply.sh > /tmp/gmm-apply5.sh && sudo bash /tmp/gmm-apply5.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 4차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web3-20261107/rollback.sh > /tmp/gmm-rollback5.sh && sudo bash /tmp/gmm-rollback5.sh <백업 폴더>
```
