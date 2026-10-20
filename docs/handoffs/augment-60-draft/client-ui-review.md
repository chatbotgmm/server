# 증강 V2 카드 클라이언트 변경

## 구현 파일
- galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeAugmentView.java
- galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeApi.java
- galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/BattleView.java
- galmaegiman-confirmed/public/duel/duel.js
- galmaegiman-confirmed/test/augment-card-ui.test.mjs

## 소스 검토에서 찾아 고친 것
- 같은 ID를 공유하는 V2 카드에 V1 고정 문구가 적용되어 기간·횟수·대상을 잘못 설명하는 문제: version=2면 카탈로그 desc를 먼저 사용.
- 희귀도와 공격·수비·유틸 역할이 섞이는 문제: V2에만 실버·골드·프리즘 이름과 독립된 등급 색을 적용. V1 표시 유지.
- 신규 36개 ID의 전용 아이콘/역할이 정의되지 않은 경우: 네이티브는 공통 u-gift, 웹은 기존 공통 ✨, 역할은 ‘증강’으로 안전하게 표시. 등급이 누락/미지원이면 ‘등급 미확인’으로 표시.
- 선택 후 카드 테두리도 동일 등급 색을 유지. 기존 선택 표면·체크 표시 및 재추첨 로직은 유지.
- NativeApi의 공통 HTTP 요청에 x-gmm-augment-version: 2를 추가. 이 값은 지원 광고일 뿐 서버 V2 활성화 설정이 아님.

## 연결 계약
네이티브 offers: id, name, desc, p, version(숫자 2), tier(silver/gold/prismatic), rerolled.
웹: R.battleAug에 id, name, desc, p, version, tier가 있어야 함. 재추첨 상태는 기존 b.sides[0].rerolled 사용.
웹은 rulesFrom(j.rules)와 createBattle(R, ...)를 그대로 사용하며 이 변경 자체로 V2를 활성화하지 않음.

## 비빌드 검증
- node --check galmaegiman-confirmed/public/duel/duel.js 통과.
- node --import ./scripts/ts-source-loader.mjs --test galmaegiman-confirmed/test/augment-card-ui.test.mjs: 8/8 통과.
- V2 60개 카탈로그 설명/등급, V1 24개 원본 회귀, 신규36 폴백, 미래 등급 방어, 네이티브 소스 연결 계약, 네이티브 HTTP 변경 최소범위 확인.
- 세 등급 잉크 색은 양피지 #f5efdc 및 웹 크림 #fffaf0와 4.5 이상 대비. 웹 배지 흰 글자 대비도 4.5 이상.
- NativeApi 원본 blob SHA 0b04ab351122a8edba2712fd75de8eff9c52ffe2 일치. 출처는 client-ui-source-pin.json 참조.

## 남은 검증
실제 게임 글꼴·서버·전체 Android 소스/SDK가 준비되지 않은 작업공간이므로 실행 화면 캡처, 직접 시각검토, 넘침/겹침 검사와 APK 빌드는 수행하지 않음. 이 결과는 시각검토 완료 또는 출시 가능 판정이 아님.
빌드 담당자는 CLAUDE.md에 따라 실제 Jua/GowunDodum 글꼴로 가로 915×412, 1280×720 로그인 전후·로비·전투 화면을 확인해야 함. V2 카드에서는 긴 설명·프리즘 배지·추천 배지·선택 후 등급색·재추첨 후 설명 갱신을 추가 확인해야 함.
서버 활성화, 커밋, push, 배포는 수행하지 않음.

## 보호막 표시 추가
- 서비스 담당자 계약: fighter.shield는 남은 흡수 HP 절대량, shieldTurns는 현재 턴을 포함한 남은 턴 수.
- 네이티브 BattleView의 HP 막대 아래 기존 효과 요약 칸에 ‘보호막 36 · 2턴’ 표시. 효과 상세 접근성 설명에도 포함. 재생 중에는 기존 효과 갱신 문구를 유지해 최종 상태를 앞서 표시하지 않음.
- 웹은 f.augment.shield와 shieldUntil을 읽되 V2 양쪽 설정·살아 있음·기간 유효를 확인. HP 줄의 상태 칩에 같은 숫자와 턴 수 표시.
- 0/만료/전투불가/V1은 보호막 표시 없음. 보호막은 체력 수치나 체력 막대 비율에 합산하지 않음.
- NativeBattle statusArea 높이 64dp 미만에서는 기존 좁은 레이아웃을 침범하지 않도록 Canvas 요약이 나오지 않음. 접근성 설명에는 남음. 가로 목표 화면에서 겹침/잘림 실측은 아직 안 했으므로 빌드 전 필수 확인 대상.
- 웹 보호막은 엔진 현재 상태를 표시하므로 턴 중 재생 세부 이벤트에 따른 보호막 보간 애니메이션은 추가하지 않음.
- BattleView 원본 blob SHA ace668911ffe99ee2bd04dec4f065f636dc304f3 검증 완료.
