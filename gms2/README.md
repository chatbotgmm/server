# 갈매미맨 방범대 — GameMaker 프로젝트 (0.1, 1단계)

Android 앱을 GameMaker로 대체하기 위한 프로젝트다. 서버·계정·자료는 지금 운영 서버(https://gmm.dpdns.org)를 그대로 쓴다.

## 여는 법
```bash
git clone -b claude/charming-bardeen-sct1b0 https://github.com/chatbotgmm/server.git
```
GameMaker에서 `server/gms2/Galmaegiman.yyp`를 연다.
- 형식: GameMaker **2024.11** 형식으로 만들었다(2024.11.0.179로 저장된 공개 프로젝트 파일을 그대로 본뜸). 더 새 GameMaker는 열 때 자동으로 바꿔 준다. 2024.11보다 옛 판(예: 2022 LTS)에서는 열리지 않을 수 있다.
- 이 프로젝트는 **GameMaker로 실행해 보지 않았다**(만든 환경에 GameMaker가 없음). 문법 검사(@bscotch/gml-parser), 내장 함수·상수·인자 수 대조(GmlSpec), 프로젝트 파일 읽기 검사(@bscotch/yy), 같은 서버 요청 시험(서버 복제본)까지만 했다. 처음 열어 실행할 때 나는 오류는 알려 주면 고친다.

## 1단계에 있는 것
- 로그인: 카톡 「갈매미맨」 채널에 「웹 로그인」을 보내 받은 6자리 코드 → 서버가 준 열쇠를 기기에 저장(galmaegiman.ini), 이후 x-gmm-s 머리글로 보냄(앱과 같은 방식)
- 마을(갈매미 건물 메뉴), 모집(하급·중급·고급, 다시 뽑기·불합격·모두 합류), 내 갈매미(정렬 4가지·같은 갈매미 묶기·상세)
- 스토리: 의뢰 → 대화 장면 → 편성(1~3마리) → 전투(증강체 선택·다시 뽑기, 공격·기술 상성 표시·집중·아이템·교체·도망) → 정산 → 귀환·귀환 이야기
- 화면: 높이 540 기준, 너비 960~1280(창 비율대로 GUI 크기를 정해 늘어나지 않음), 앱 남색 작업대 색, 앱 글꼴

## 다음 단계
조합·도감·자유 모험·시장·출석(2단계), 실시간 대전(3단계), 카카오 로그인, 효과음·연출.

## 구조
- `scripts/` GML: `scr_config`(서버 주소·색), `scr_api`(서버 호출·열쇠), `scr_ui`(즉시 그리기 방식 화면·입력·목록), `scr_assets`(그림·글꼴), `scr_app`(시작·로그인·화면 이동), `scr_screen_*`(화면별)
- `objects/obj_app`: 하나뿐인 오브젝트. Create·Step·Draw GUI·비동기 HTTP(62)·비동기 대화창(63)
- `rooms/rm_main`: obj_app 하나가 있는 방
- `datafiles/`: 그림(PNG, 앱 그림에서 만든 것)·글꼴(Noto 기반 OFL, 배포 가능). 하위 폴더 형식을 확인하지 못해 한 폴더에 접두어로 구분
- `options/`: 2024.11 템플릿 + Android(org.dpdns.gmmgame, 가로 전용, 인터넷 권한, arm64), Windows(창 크기 바꾸기 허용), 화면 맞춤은 전체(full scale)

## 알려진 제약
- **HTML5(웹)**: GameMaker 설명서상 HTML5의 font_add는 웹 글꼴만, WebGL을 끈 경우에만 된다. 그래서 지금 그대로 HTML5로 내보내면 한글이 안 나올 수 있다(웹은 지금 /game60 웹게임이 있음). HTML5는 같은 서버 주소에서 돌려야 한다(다른 출처는 서버가 막음).
- **Android 패키지**는 기존 앱(org.dpdns.gmm)과 겹치지 않게 org.dpdns.gmmgame으로 했다. 출시 때 바꿀 수 있다.
- 그림은 sprite_add로 실행 중 불러온다(WebP는 못 읽어 PNG). HTML5에서는 비동기라 잠깐 빈 칸일 수 있다.
- 글꼴은 처음 쓰는 글자를 그때 만든다(설명서: 한 프레임에 새 글자 약 200자). 처음 화면이 바뀔 때 한두 프레임 늦을 수 있다.
