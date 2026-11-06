# 서버 패치 3차: 공개 홈페이지를 지금 게임(60종) 규칙으로 + 테마 통일 + PC 웹게임 1단계 (2026-11-06)

**2차 패치(`deploy/server-gmm111-xp2-20261104`)가 적용된 서버에만** 쓴다. 2차 상태가 아니면 아무것도 바꾸지 않고 멈춘다.
게임 서버 기능·경험치 규칙·증강 설정·DB는 바꾸지 않는다.

## 바뀌는 것
- 모든 공개 페이지가 첫 화면과 같은 테마(남색 머리줄·양피지 본문·금색 선·같은 바닥). 머리줄 행동은 「앱 다운로드」.
- 게임 소개·캐릭터 소개·모집 확률·세계관·계정 삭제 안내를 **서버에 있는 60종 자료**(도감·모집 정책·스토리)로 새로 보여 줌.
  - 캐릭터 소개: 커먼~전설 38종(히든·제한·초월·불멸·영원은 비공개), 방범대 등록증 그림.
  - 모집 확률: 하급·중급·고급 등급 확률과 1종당 확률, 다시 뽑기 비용, 보장 없음.
  - 세계관: 스토리모드 첫 장면·7개 챕터·마을 사람들.
- 예전 145종 페이지(도감·조합법·히든 단서·랭킹·수다방·이벤트·검색·프로필)는 닫혀 첫 화면으로 이동. 사이트맵에서도 뺌.
- PC 웹게임(예전 규칙) 버튼을 홈페이지에서 뺌(주소 /game은 남음).
- **새 PC 웹게임 1단계 `/game60`**(홈페이지에는 아직 연결하지 않음, 주소로만 들어감): 앱과 같은 계정·서버 기능으로
  로그인 → 마을 → 내 갈매미 → 모집(하급·중급·고급, 다시 뽑기·불합격·모두 합류) → 스토리(대화·의뢰 수락·편성 1~3마리·전투·증강 선택·정산·귀환·귀환 이야기).
  조합·도감·시장·탐험·대전은 2·3단계.

## 내용
- 묶음 211개: dist/src/site.js, public/site/harbor.css(새)·site.js, home-v4/index.html·home.css, village-v4/index.html, public/site/units/u01~u38.webp(새), public/game60/(새, 웹게임 화면·그림·글꼴 약 10MB)
- 60종 자료는 서버에 이미 있는 파일을 읽는다(catalog.json·roster60-recruit-policy.json·story-mode-v1.json, 기대 지문으로 확인). 새로 넣지 않는다.
- 원본 소스: 브랜치 claude/charming-bardeen-sct1b0 커밋 ee051b0. 운영 site.js는 웹 브랜치(10cac95) 빌드와 바이트까지 같아 새 빌드로 바꾼다. web.js는 건드리지 않는다(예전 주소는 site.js의 요청 훅이 가로챔).
- DB 스키마·내용 변경 없음, 새 설치 없음.

## 시험
- 전체 시험 526개 통과, 타입 검사 통과.
- 웹게임: 서버 파일 복제본에서 새 계정으로 실제 흐름을 끝까지 진행(960×540·915×412·1280×720), 화면 밖·잘린 글자 검사 통과, 오류 0.
- 공개 페이지 10개 × 4가지 크기 캡처, 가로 넘침·머리줄 겹침·깨진 그림 검사 통과, 눈으로 확인.
- 서버 파일 복제본에 이 묶음을 덮어 실제 dist로 실행: /health·/ready 정상, apply.sh 확인 항목 모두 통과.
- apply.sh는 새 테마·모집 확률·등록증 그림·/dex 이동·첫 화면 버튼·/game60을 확인하고, 실패하면 자동으로 2차 상태로 되돌린다.

## 서버 적용 (Session Manager)
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web-20261106/apply.sh > /tmp/gmm-apply3.sh && sudo bash /tmp/gmm-apply3.sh
```
되돌리기(마지막에 나온 백업 폴더 사용, 2차 상태로 돌아감):
```bash
sudo -u ubuntu git -C /home/ubuntu/gmm fetch -q origin manual/home-assembly-20261019 && sudo -u ubuntu git -C /home/ubuntu/gmm show FETCH_HEAD:deploy/server-gmm111-web-20261106/rollback.sh > /tmp/gmm-rollback3.sh && sudo bash /tmp/gmm-rollback3.sh <백업 폴더>
```
재시작하는 몇 초 동안 접속 중인 사람은 잠깐 끊길 수 있다. 브라우저에 예전 화면이 남아 있으면 새로고침(최대 5분 캐시).
