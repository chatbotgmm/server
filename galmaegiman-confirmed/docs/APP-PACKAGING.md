# 앱으로 내기 (PWA → 구글 플레이) 안내

갈매미맨 앱은 홈페이지(https://gmm.dpdns.org)를 감싸는 방식이에요. 게임 처리는 지금 서버가 그대로 해요.

## 0. 이미 준비된 것 (v0.9.70)
- 앱 정보 파일 `/manifest.webmanifest`: 이름, 아이콘(192·512, 마스크용 포함), 시작 주소, 바로가기(뽑기·조합·탐험대 편성)
- 서비스 워커 `/sw.js`: 인터넷이 끊기면 「연결 끊김」 화면을 보여 주고, 그림·글꼴은 저장해 둬요. 게임 정보(API)는 저장하지 않아요.
- 계정 삭제: 「내 갈매미」 → ⚙️ 계정, 앱 없이 쓸 수 있는 안내 `/account/delete`, 카톡 「탈퇴」
- 개인정보처리방침 `/privacy` (글은 `data/privacy.md`에서 고쳐요)
- 뽑기 확률 공시 `/odds`
- 앱 ↔ 홈페이지 연결 파일 `/.well-known/assetlinks.json` (아래 4단계에서 만들어요)

## 1. 먼저 할 일 (출시 전 꼭)
- [ ] `data/privacy.md`의 「문의」 절에 **개인정보 보호책임자 이름과 연락처**를 적기 (파일 안 주석 참고)
- [ ] 개인정보처리방침·이용 조건은 **법적 검토** 받기 (만 14세 미만 이용자 처리 포함)
- [ ] 512×512 이상 큰 원본 아이콘 준비 (지금 아이콘은 128px 원본을 늘린 것이라 살짝 흐려요)
- [ ] 「갈매미맨」 이름 상표 검색 (특허청 KIPRIS)
- [ ] OpenAI 약관 원문에서 생성 그림 상업적 사용 조건 확인

## 2. PWA로 먼저 써 보기 (스토어 없이)
- 안드로이드 크롬: 홈페이지 → 메뉴 → 「앱 설치」 또는 「홈 화면에 추가」
- 아이폰 사파리: 공유 → 「홈 화면에 추가」

## 3. 구글 플레이용 앱 만들기 (Bubblewrap, 내 컴퓨터에서)
Bubblewrap은 구글이 만든 도구로, PWA를 구글 플레이 앱(TWA, 크롬으로 보여 주는 앱)으로 만들어요.
처음 실행할 때 JDK와 Android SDK를 내려받을 수 있어요(용량이 커요).

```bash
npm i -g @bubblewrap/cli
mkdir gmm-app && cd gmm-app
bubblewrap init --manifest https://gmm.dpdns.org/manifest.webmanifest
#  - 패키지 이름: 예) org.dpdns.gmm.twa  (한 번 정하면 못 바꿔요)
#  - 서명 키(keystore) 비밀번호: 잃어버리면 업데이트를 못 올리니 안전하게 보관
bubblewrap build
#  → app-release-bundle.aab (플레이에 올릴 파일), app-release-signed.apk (내 폰 시험용)
```

## 4. 플레이 콘솔에 올리고 홈페이지와 연결
1. 플레이 콘솔에서 앱 만들기 → `app-release-bundle.aab` 업로드(내부 테스트부터 권장).
2. 플레이 콘솔 → 앱 → **출시 → 설정 → 앱 무결성** → 「**앱 서명 키 인증서**」의 SHA-256 지문을 복사.
   (Bubblewrap이 만든 키는 「업로드 키」가 돼요. 업로드 키 지문이 아니라 **앱 서명 키** 지문이어야 해요.)
3. 서버에서:
   ```bash
   sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/assetlinks.sh
   ```
   패키지 이름과 지문을 넣으면 `config/assetlinks.json`이 생겨요(서버 재시작 필요 없음).
4. 폰에 앱을 깔아서 **위쪽 주소창이 안 보이면 연결 성공**. 주소창이 보이면 지문이 틀린 거예요.

## 5. 플레이 콘솔에서 적을 것
- 개인정보처리방침 주소: `https://gmm.dpdns.org/privacy`
- 계정 삭제 주소(데이터 보안 양식): `https://gmm.dpdns.org/account/delete`
- 데이터 보안 양식: `data/privacy.md`의 표대로 (수집: 앱 활동·사용자 ID, 판매·공유 안 함, 삭제 요청 가능)
- 콘텐츠 등급 설문: 뽑기(무료 뽑기권) 있음, 폭력성 낮음 등 실제대로 답하기
- 카카오 로그인: 크롬으로 보여 주는 앱이라 지금 웹 로그인 그대로 쓸 수 있을 가능성이 높아요. 앱에서 한 번 직접 확인하세요.

## 참고
- Trusted Web Activity 빠른 시작: https://developer.chrome.com/docs/android/trusted-web-activity/quick-start
- 구글 플레이 계정 삭제 요구 사항: https://support.google.com/googleplay/android-developer/answer/13327111
- 아이폰 앱스토어는 웹을 감싼 앱이 심사 기준 4.2(최소 기능)로 거절될 수 있어요. 구글 플레이 뒤에 따로 준비하세요.
