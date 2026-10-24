# 오프라인 경량 전투 검토 APK

별도 `:combatReview` 모듈. 제품 `:app` 소스/원본/패키징은 변경하지 않는다. `org.dpdns.gmm.combatreview`, versionCode79, versionName2.2.4-offline-review-lite, debug서명. MainActivity/로그인/제품 API/알림/인터넷 권한 없이 CombatV2ReviewActivity만 실행한다. 휴대폰 단독 fixture 전투이며 최종 밸런스나 제품 대전이 아니다. 새 승인 PNG는 전송 차단으로 미포함, 기존 벡터 대체 표시를 유지한다.

build.gradle의 Java include 목록은 기존 엔진/세션/화면/입력/공용효과/아트 loader 원본을 그대로 컴파일한다. 이미지 재생성/리사이즈/분할 없음. assets는 u01 front/rear, manifest, offline fixture, 기존 workbench 폰트와 OFL의6파일만 포함한다. res는 combat_v2 XML 리소스만 포함한다. 원본 파일이나 제품 generated assets를 삭제하지 않는다.

기존220610389바이트 APK의 주요 압축 자산: native/release-v1 35510124, journal33538117, upper33-v1 32048049, registration-v1 29740602, game-art/duel25218777, thumbs12385365바이트. 경량 모듈은 이 전체 경로와 다른 제품 자산을 패키징하지 않는다. 공용 검토 효과는 유지한다. APK 전체는5558010바이트,215052379바이트 감소. 단순 재압축/분할이 아닌 별도 설치 가능한 앱이다.

공식 도구로 `:combatReview:assembleDebug :combatReview:testDebugUnitTest` 실행. 기존 Node/Java parity65개 통과, Robolectric2개(6번째기술/MP/집중/롱프레스취소/뒤로가기/복원 및 패키지격리/이미지로딩) 통과. APK6assets가 소스와 바이트 일치, CRC/apksigner v2/zipalign 검증. 실제 기기 설치/픽셀 렌더는 미검증이다.

제품 org.gmm과 공존 가능. 이전 combatreview78과 같은 debug인증서로 versionCode79이므로 해당 검토판 업데이트 경로다. PC/USB/Node/인터넷 연결 불필요. 운영 키/계정/DB/Play에 접근하지 않는다.
