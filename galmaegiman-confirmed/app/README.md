# Android 네이티브 앱

게임 화면은 Android View와 Canvas로 구현한다. MainActivity는 AppCompatActivity를 사용하며 APK에 HTML 게임 화면이나 Capacitor를 넣지 않는다. 웹사이트는 기존 웹 화면을 유지한다.

## 화면과 계정

로그인, 로비, 챕터·전투 선택, 대사, 수비대 편성, 경험치·기술·승진, 도감, 뽑기·결과, 조합·재료 혼합, 아이템·상점, 탐험, 출석·미션, 일반·랭크 대전, 자유 연습, 상성표, 설정을 제공한다.

기존 서버 계정과 StorySave를 사용한다. 카톡 로그인 코드와 카카오 PKCE 로그인을 지원한다. 네이티브 앱의 카카오 반환 주소는 `gmmnative://auth`, 기존 앱은 `gmmapp://auth`를 유지한다. 전투 계산은 공통 TypeScript 엔진을 서버에서 실행하며 요청 리비전과 소유권을 검사한다. 아이템 차감과 성장 저장은 같은 DB 거래로 처리한다.

캐릭터 원화는 서버의 기존 그림을 사용한다. `sprite-bounds.json`은 그 그림의 경계 좌표만 담은 표시 최적화 자료다. 개인 계정 토큰과 그림 열쇠를 APK에 넣지 않는다. 게임 이용에는 서버 연결이 필요하다.

## 빌드

Android Studio에서 `app/android`를 열거나 다음 명령을 실행한다. JDK 17 이상과 Android SDK 36이 필요하다.

```sh
cd app/android
./gradlew assembleDebug
```

배포 빌드의 서버 주소는 `https://gmm.dpdns.org`다. 기존 APK와 서명키가 다르므로 별도 패키지 `org.dpdns.gmm.native`로 설치하는 시험판이다. 기존 앱을 삭제할 필요가 없으며 같은 계정으로 로그인한다. 서명키를 Git에 올리지 않는다.

## 검토와 테스트

```sh
npm run test:db
adb reverse tcp:4004 tcp:4004
cd app/android
./gradlew assembleDebug assembleDebugAndroidTest -PnativeServer=http://127.0.0.1:4004
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class org.dpdns.gmm.NativeScreensTest org.dpdns.gmm.native.test/androidx.test.runner.AndroidJUnitRunner
```

`NativeScreensTest`는 로컬 검토 서버에만 연결한다. 실제 서버 데이터로 네이티브 화면을 열고 앱 View를 캡처한다. 소프트웨어 에뮬레이터의 System UI 오류 창은 앱 View 캡처에 포함되지 않는다. Z플립 비율 880×360과 태블릿 1280×720에서 각각 실행한다. 폰에서는 스크롤 가능한 본문을 사용하고 태블릿에서는 버튼 이미지의 비율을 유지한 나머지 높이를 전투기록에 배분한다. 최종 배포 APK는 테스트용 서버 옵션 없이 다시 빌드한다.

## 웹 다운로드 공개

```sh
node app/publish-native.mjs app/android/app/build/outputs/apk/debug/app-debug.apk
```

`apk` 브랜치에 기존 파일을 보존한 일반 커밋을 추가한다. 강제 push하지 않는다. 서버의 `scripts/update.sh`는 파일 크기와 SHA-256을 확인한 뒤 네이티브 APK를 반영한다. 다운로드 페이지는 `/app/native`이며 APK 파일은 `/app/galmaegiman-native.apk`다.

배경음악은 기존 `public/duel/sound.js`의 멜로디를 PCM 음원으로 옮겼다. Jua와 Black Han Sans 글꼴의 OFL 라이선스는 APK의 assets에 함께 들어간다.
