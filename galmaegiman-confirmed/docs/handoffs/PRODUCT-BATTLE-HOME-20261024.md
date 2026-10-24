# 제품 전투 진입 및 홈 환생/우편 연결

## 실제 연결
- `LobbyView` 플레이 → `MainActivity.openRosterBattle` → `NativeRoster60Battle`.
- 인증된 `native/roster60/battle/{active,options,create,read,action,settle}` 사용. 오프라인 fixture나 클라이언트 피해 계산을 제품 transport로 사용하지 않는다.
- 서버 HP/MP, 다섯 스탯, revision을 표시한다. 공격/기술/집중 요청 및 최대 여섯 기술을 지원한다. 아이템/교체/도망은 아직 연결되지 않아 이유와 함께 비활성화한다.
- 변경 요청은 계정+서버별 SharedPreferences에 requestId 및 정확한 payload를 먼저 저장한다. 응답 불명확 시 같은 요청을 재전송한다. 재진입 시 active/read로 서버 상태를 읽는다. 서버 영수증이 최종 멱등성을 보장한다.
- 제품 화면은 왼쪽 상태/복구/정산, 오른쪽 2행3열 명령. 기술 페이지 뒤로가기는 명령으로 복귀한다. hold 취소/해제/백그라운드는 시전하지 않는다.
- 튜토리얼 안내에서도 실제 전투로 진입할 수 있다. 완료 증명은 서버의 tutorial encounter 승리 정산에 따른다.

## 홈
- 원래 메뉴 소식/우편 자리는 잠금 가능한 환생. 왼쪽 48dp 독립 우편 버튼으로 기존 우편 기능 유지.
- `GET native/roster60/home`: 서버 카탈로그 등급+소유 기록으로 제한/초월/불멸/영원 첫 획득을 판정. 레벨 조건과 독립이다.
- `Roster60Home` 계정 행에 unlock 및 introComplete를 영속 저장한다. 새 조합 지급과 unlock은 같은 거래에서 기록한다.
- 안내 취소/배경 이동은 완료 요청을 보내지 않는다. 확인 버튼만 `POST home/rebirth-intro/complete`; 중복 완료도 동일 결과. 재접속 시 미완료 안내가 다시 나온다.
- 새 additive migration은 작성만 했으며 운영 DB에는 적용하지 않았다. 테스트는 폐기 가능한 /tmp DB만 사용한다.

## 남은 사항 / 실행 조건
- 승인된 5스탯 카탈로그와 전투 계산 계수/정책이 없어 현재 제품 서버에서는 준비 중 오류로 시작을 거절한다. 테스트용 정책을 제품 기본값으로 넣지 않았다.
- 새 5스탯 환생 보너스 대상은 답변 대기. 기능 잠금 해제와 환생 실행 허용은 별개이다.
- 이번 화면은 서버 상태/명령 연결 단계이다. 캐릭터 전투 아트/VFX 및 상세 턴 연출은 아직 이 제품 화면에 연결되지 않았다. 새 PNG Library 전송은 403 차단 상태이며 재시도하지 않았다.
- 제품 디버그 빌드는 org.gmm, 로컬 서버 URL http://127.0.0.1:18765. 인증/격리 서버 및 adb reverse 등 연결 준비가 필요하며 독립 오프라인 검토 APK가 아니다.
- org.gmm debug APK는 기존 제품 앱과 동일 패키지이므로 출시 서명 설치본에 덮어 설치할 수 없다. 이 작업에서는 설치/삭제/실기기 실행하지 않았다.
- 운영 서버/DB/배포/원격 push 없음. 실기기 렌더 검증 없음. Robolectric 및 서버 격리 API 테스트 결과를 실제 기기 검증으로 표현하지 않는다.

## 검증 근거
- 서버 `npm run build` 및 격리 DB 전체 회귀: 52파일/584테스트 통과. `/tmp/product-home-regression-verified.log`.
- Android 제품 화면 테스트는 서버 transport를 API-shaped fake로 교체하여 실제 홈 버튼과 화면 동작을 확인한다. 별도로 실제 인증 Fastify/Prisma API 테스트가 존재한다. Android와 Node를 한 프로세스로 실시간 연결해 검증한 것은 아니다.
- 새 홈 테스트의 headless decor는 부착되지 않으므로 onAttached/onDetached를 명시적으로 전달한다. 실제 Android OS 생명주기/백그라운드 검증은 남아 있다.
- 기존 홈 회귀의 옛 메뉴 수/플레이 경로 기대값을 갱신했다. 반투명 재화 아이콘의 투명 캔버스 픽셀과 실제 합성 픽셀을 비교하던 검증은 알파 및 RGB 채널별 3 이내 오차를 검증하도록 수정했다. 실제 홈 재화 디자인은 수정하지 않았다.
- 최종 Android 선택 회귀 23/23 통과(실패/오류/스킵 0), `assembleDebug` 성공. `/tmp/product-ui-final-pass.log`.
- 생성 APK: `app/android/app/build/outputs/apk/debug/app-debug.apk`, 220651148 bytes, SHA256 `069f70fcd1844bde2c5f81ef67197c5dca495550c5f37bbd24813dca0f59853b`.
- aapt 확인: package `org.gmm`, versionName `2.2.4-native`, versionCode `78`(기존 설정 유지, 출시 버전 갱신 아님), minSdk24/targetSdk36.
- apksigner 검증 성공: CN=Android Debug; 인증서 SHA256 `5c4a018046c682d7ff6d300cdce9ecb2ba87f1e3e5cd66f69e63f4814eacddd0`.
