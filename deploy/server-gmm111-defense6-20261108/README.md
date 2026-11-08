# 서버 패치 15차: 랜덤 디펜스 — 외부탐사 처음부터 · 등급별 강화 9칸 · 상위 위습 찾기 · 배경음악 (2026-11-08)

**14차 패치(`deploy/server-gmm111-defense5-20261108`)가 적용된 서버에만** 쓴다. 14차 상태가 아니면 아무것도 바꾸지 않고 멈춘다(12·13·14차를 먼저 적용).

## 바뀌는 것(사용자 요청)
- **외부탐사**: 처음(1웨이브)부터 1장을 보낼 수 있고, 앞 장을 끝내면 다음 장이 열린다(웨이브 조건 없음). 대장 세기·보상은 그대로.
- **등급별 강화 9칸**: 커먼·언커먼(묶음) · 레어 · 에픽 · 전설 · 히든 · 제한 · 초월 · 불멸 · 영원. 단계마다 그 등급 공격력 +14%.
- **위습 찾기는 상위 갈매미만**: 거장(전설) · 금각(히든) · 가루다(초월) · 리치(불멸) · 여명(영원). 황금·드워프·룬장인은 뺌.
- **배경음악**: 사용자가 보낸 곡(2분 54초, Opus)을 어디서나 재생되는 MP3(112kbps, 2.4MB)로 바꿔 넣음. 「시작하기」를 누르면 반복 재생, 음량 45%, 위 정보 줄·시작 화면의 ♪ 버튼으로 켜고 끔(기기에 기억), 일시정지·다른 앱이면 멈춤.
- 난이도 수치는 바꾸지 않음(사용자 요청). 참고로 자동 봇 200판에서 클리어 3.5%·경고 패배 10%로 14차(10%·12%)보다 어려워짐(낮은 등급 위습 찾기가 빠지고 강화가 9칸으로 나뉜 영향).

## 내용(가벼운 패치, 재시작 1번)
- 실행 폴더를 복사하지 않는다. 지금 실행 폴더에서 `dist/src/site.js`(`/defense/*`가 mp3도 내보내고 부분 요청 Range 206 지원, `patch_site2.py`)와 `public/defense/` 파일 3개(index.html·defense.js·engine.js)를 바꾸고 `bgm.mp3`를 더한 뒤 서버를 재시작한다.
- DB·설정 변경 없음. 바꾸기 전 파일 백업, 확인 실패 시 되돌리고 다시 재시작.

## 시험(14차 상태 서버 복제본)
- 적용 → 재시작·확인 통과, mp3 전체 200·부분 206·범위 밖 416·다른 확장자 404. 되돌리기 → 14차 상태(bgm 없음)로 복귀, 다시 적용 성공, 두 번째 적용은 멈춤.
- 휴대폰 크기: 「시작하기」 뒤 음악 재생, ♪로 끄고 켜기, 일시정지 때 멈춤, 플레이 오류 없음.

## 서버 적용 (Session Manager, 토큰 필요 — 14차 다음에)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense6-20261108/apply.sh > /tmp/gmm-apply15.sh && sudo bash /tmp/gmm-apply15.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 14차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-defense6-20261108/rollback.sh > /tmp/gmm-rollback15.sh && sudo bash /tmp/gmm-rollback15.sh <백업 폴더>
```
