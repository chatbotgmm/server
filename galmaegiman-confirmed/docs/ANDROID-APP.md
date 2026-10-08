# 안드로이드 앱 만들기 (Capacitor)

앱 화면은 「내 갈매미」(뽑기 · 조합 · 내 갈매미 · 탐험대 편성 · 전투 편성 · 아이템 · 상점 · 계정)를 **앱 안에 넣은 것**이고,
게임 처리는 지금 서버(https://gmm.dpdns.org)가 해요. 카톡 챗봇과 같은 계정이에요.

## 앱에만 있는 것
- 로그인: 카톡 「웹 로그인」 6자리 코드 또는 카카오 로그인(브라우저로 로그인한 뒤 앱으로 돌아와요)
- 알림: 🧭 탐험대가 돌아올 때, 🎟 뽑기권을 받을 수 있을 때(휴대폰 안에서 예약 — 서버·Firebase 필요 없음)
- 진동: 뽑기 결과(전설 이상이면 세게 두 번)
- 뒤로 가기: 열린 창 닫기 → 뽑기 탭 → 앱 끝내기
- 도감·검색·개인정보처리방침은 앱 안 브라우저로 열려요
- 아직 없는 것: 수비대 대결(/duel, 시험판이라 열쇠 주소로만 열려요)

## 폴더
- `app/src/app.js` 앱 연결부(서버 주소·토큰·카카오·알림·진동)
- `app/build-www.mjs` `public/my`를 앱 화면(`app/www`)으로 복사
- `app/android/` 안드로이드 프로젝트(패키지 이름 `org.dpdns.gmm`, 아이콘·시작 화면은 갈매미 아이콘으로 만듦)

## 1. 내 컴퓨터 준비 (한 번만)
1. **Android Studio** 설치 (JDK·Android SDK가 같이 깔려요): https://developer.android.com/studio
2. **Node.js** 설치 (22 이상 권장): https://nodejs.org
3. 저장소 받기: `git clone https://github.com/chatbotgmm/server.git` → `cd server && git checkout main-oz3tvo`

## 2. 앱 열기
```bash
cd galmaegiman-confirmed/app
npm install
npm run sync          # public/my → app/www 복사 + 안드로이드 프로젝트에 반영
npx cap open android  # Android Studio가 열려요(처음엔 Gradle 내려받기에 몇 분 걸려요)
```
화면(`public/my/*`)을 고친 뒤에는 `npm run sync`를 다시 해요.

## 3. 내 폰에서 시험
- 폰: 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7번 탭(개발자 모드) → 개발자 옵션 → USB 디버깅 켜기
- USB로 연결 → Android Studio 위쪽 ▶ (Run). 처음 켜면 알림 허락을 물어봐요.
- 확인할 것: 코드 로그인 / 앱 껐다 켜도 로그인 유지 / 뽑기 진동 / 탐험 보내고 알림 / 카카오 로그인 후 앱으로 돌아오는지

## 4. 구글 플레이에 올릴 파일 만들기
1. Android Studio → **Build → Generate Signed App Bundle or APK → Android App Bundle**
2. 새 서명 키(keystore) 만들기 → **파일과 비밀번호를 안전한 곳에 따로 보관**(잃어버리면 업데이트를 못 올려요. 저장소에 올리지 마세요 — `.gitignore`에 넣어 둠)
3. `release` 로 만들기 → `app/android/app/release/app-release.aab`
4. 플레이 콘솔 → 앱 만들기 → **내부 테스트**에 aab 업로드부터

## 5. 플레이 콘솔에 적을 것
- 개인정보처리방침: `https://gmm.dpdns.org/privacy`
- 계정 삭제(데이터 보안 양식): `https://gmm.dpdns.org/account/delete`
- **앱 액세스(심사관 로그인)**: 서버에서 `sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/review-code.sh` → 나온 6자리 코드를 적고,
  "로그인 화면의 코드 칸에 이 숫자를 입력" 이라고 써요(심사 전용 계정으로만 들어가요). 심사가 끝나면 `review-code.sh off`.
- 데이터 보안: `data/privacy.md` 표대로(앱 활동·사용자 ID 수집, 판매·공유 안 함, 전송 암호화, 삭제 요청 가능)
- 콘텐츠 등급 설문: 실제대로(무료 뽑기권으로 하는 뽑기 있음, 결제 없음)
- 대상 연령, 광고 없음 등

## 참고
- 카카오 로그인은 카카오 SDK가 아니라 웹 로그인(브라우저)이라 카카오 디벨로퍼스에 안드로이드 플랫폼을 따로 등록하지 않아도 돼요.
  로그인 후 `gmmapp://auth?code=…` 로 앱에 돌아오고, 이 코드는 한 번만·2분만 쓰이며 앱이 처음 만든 비밀값이 있어야 토큰으로 바뀌어요.
- 카카오 계정이 아직 게임 계정에 연결되지 않았다면, 브라우저에 「처음 연결」 화면이 떠요. 거기서 카톡 코드로 한 번 로그인해 연결한 뒤 앱에서 다시 「카카오로 로그인」을 누르세요.
- 웹을 감싸는 방식(TWA, `docs/APP-PACKAGING.md`)은 이 앱으로 대신해요.
