# 서버 패치 9차: GameMaker 게임용 카카오 로그인 (2026-11-07)

**8차 패치(`deploy/server-gmm111-web6-20261107`)가 적용된 서버에만** 쓴다. 8차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.
(7차·8차를 아직 적용하지 않았다면 7차 → 8차 → 9차 순서로 적용한다.)

## 바뀌는 것
GameMaker 게임(저장소 chatbotgmm/gmm)에서 「카카오로 로그인」을 쓸 수 있게 서버에 주소 4개를 더한다. 기존 로그인(카톡 코드, 앱 카카오 로그인, 심사 로그인)은 그대로다.

1. `POST /my/api/app/kakao-start` (x-gmm 머리글 필요, 같은 곳 1분 10번): 게임이 요청 번호(ticket)·비밀값(secret)·확인 글자(4자)·브라우저 주소를 받는다. 10분 유효.
2. `GET /my/kakao?gm=<요청 번호>`: 게임이 연 브라우저가 카카오 로그인 화면으로 넘어간다(카카오 개발자 설정의 돌아오는 주소는 기존 `/my/kakao/callback` 그대로).
3. 카카오 로그인 뒤 브라우저에 확인 화면: 게임 화면과 같은 확인 글자를 보여 주고 「같아요, 게임에 로그인」/「취소」를 누른다 → `POST /my/kakao/gm`(폼, 같은 곳 1분 20번).
4. `POST /my/api/app/kakao-poll` (x-gmm 필요, 같은 곳 1분 90번): 게임이 2초마다 요청 번호+비밀값으로 묻는다. 대기 중이면 `{pending:true}`, 끝나면 한 번만 `{token}`.

- 계정 처리는 지금 Android 앱 카카오 로그인과 같은 함수(`webKakaoSignIn`)를 쓴다: 이미 연결된 카카오 계정이면 그 계정, 챗봇 계정과 짝이면 연결, 둘 다 아니면 새 계정.
- 안전장치: 브라우저 주소에는 요청 번호만 들어가고 열쇠를 받는 비밀값은 게임만 가진다. 남이 만든 주소로 로그인되는 것을 막으려고 확인 화면에서 게임과 같은 확인 글자를 보고 직접 눌러야 한다. 확인 버튼은 그 브라우저에만 준 일회용 값이 있어야 통한다.
- 요청 상태는 메모리에만 둔다(서버를 다시 켜면 진행 중이던 게임 로그인만 처음부터 다시).

## 내용
- 묶음 1개 파일: `dist/src/my.js`. 운영 파일(지문 0f485633…)에 `patch_my.py`로 덧붙인 결과(107줄 추가, 기존 줄 변경 없음). DB 변경 없음.
- 시험(8차 상태 서버 복제본 + 가짜 카카오 응답): 시작→브라우저 로그인→확인→열쇠 받기→그 열쇠로 /my/api/me 200, 비밀값 틀림·일회용 값 재사용·두 번째 받기 모두 거절, 카카오 화면 취소·확인 화면 취소가 게임에 전달, 기존 카톡 코드 로그인·앱 카카오 로그인(교환) 정상, 시작 요청 1분 10번 제한 동작, 아래 적용 확인 항목 모두 통과(패치 전 서버에서는 「게임카카오로그인」 항목만 실패).

## 서버 적용 (Session Manager, 토큰 필요 — 지난번과 같은 순서)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-gmkakao-20261107/apply.sh > /tmp/gmm-apply9.sh && sudo bash /tmp/gmm-apply9.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 8차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-gmkakao-20261107/rollback.sh > /tmp/gmm-rollback9.sh && sudo bash /tmp/gmm-rollback9.sh <백업 폴더>
```
