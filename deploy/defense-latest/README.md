# 랜덤 디펜스 최신 판 (한 번에 올리기)

패치를 12차·13차…처럼 하나씩 하지 않고, 지금 서버가 어느 단계든(10차~15차) **한 번에 최신 판으로** 올린다.
사용자 요청(2026-11-08). 앞으로 디펜스를 고칠 때도 이 폴더의 묶음만 바꾸고 명령은 그대로다.

## 명령 (Session Manager, 토큰 필요)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/tools/defense-update.sh > /tmp/gmm-defense-update.sh && sudo bash /tmp/gmm-defense-update.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 바로 전 단계로):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/tools/defense-rollback.sh > /tmp/gmm-defense-rollback.sh && sudo bash /tmp/gmm-defense-rollback.sh <백업 폴더>
```

## 하는 일
- 서버 파일 8개(my.js·site.js·디펜스 파일 6개)의 지문으로 지금 단계를 알아낸다(`known-states.txt`). 이미 최신이면 아무것도 안 하고, 모르는 상태면 아무것도 바꾸지 않고 멈춘다.
- 실행 폴더를 복사하지 않는다. 바꾸는 파일만 백업하고 바꾼다. 서버 코드(site.js)가 바뀔 때만 재시작한다. 확인 실패 시 바로 되돌린다.

## 지금 최신 판에 든 것
- 15차까지 전부(2판 규칙, 지문 주소, 속도 1.5배, 외부탐사 처음부터, 등급별 강화 9칸, 상위 위습 찾기, 배경음악·mp3 내보내기)
- 이번 추가: 갈매미는 **끌어서만** 옮김(빈 곳을 누르면 고르기 풀림, 고른 무리는 하나를 끌면 함께), **소리 설정 창**(♪: 배경음악 켜기·끄기, 음량 막대·−/+, 기기에 기억)
- **선택 위습**(커먼·언커먼·레어·에픽): 원하는 갈매미를 골라 소환. 처음 커먼 1개 + 보스 보상(10웨이브 언커먼·20웨이브 레어·30웨이브 에픽 선택 위습). 아래 판 「선택 위습」 버튼 → 등급 칸 → 갈매미 누르기

## 시험(서버 복제본)
- 11차·12차·15차 상태에서 각각 최신으로 올라감(11·12차는 재시작, 15차는 재시작 없음), 최신이면 「이미 최신」, 파일 하나를 바꾼 모르는 상태는 멈춤. 되돌리기 → 12차 상태(bgm 지워짐).
- 휴대폰 크기: 끌어 옮기기·빈 곳 누르면 풀림·무리 끌기·음량 막대/−/+·켜기/끄기 오류 없음.
