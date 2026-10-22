# 홈 펼침 메뉴 독립 검토

2026-10-21, 소스 검토. Android SDK/실기기 렌더 검증 아님.

## 확인된 보존 항목
- 기존 탐험/모집/방범대/조합/상점/출석/편성 핸들러 유지.
- 플레이 modes 진입 및 별도 진행 중 전투 복귀 네트워크 흐름 유지.
- 실제 항구 배경·물·배경 이펙트·캐릭터 호흡/드래그 원본 유지.
- 메뉴 밖 탭은 닫기 제스처 전체를 소비해 아래 조작부로 관통하지 않음.
- Android back은 홈 종료 확인보다 메뉴 닫기를 먼저 수행.
- 숨긴 버튼은 GONE이고 배지는 기존 eligibility만 집계.
- 실제 재화 값과 전체 접근성 설명 보존.
- 홈 밖 전역 테마 수정 없음(MainActivity는 홈 back 추가만).

## 발견 및 수정 요청
1. headerBottom=0 통합 입력이 테스트64/72와 불일치하여 작은 화면 캐릭터/복귀 영역 겹침. 부모가64dp 입력 수정 확인.
2. 568×280,font2,activeBattle에서 실제 gull layout이48dp보다 작음. 최소 터치영역 및 resume/tip 여유 확보 요청.
3. 고정 재화 폭에서 긴 잔액 무제한 축소로3~4sp까지 작아짐. 최소 가독 글꼴+정확한 전체값 접근 경로 유지한 단축표시/폭조정 요청.
4. 눌림 타일은 여전히 밝은 양피지인데 controlInk가흰색으로 전환됨. 눌림에도 진남색 글자 요청.
5. 복귀 버튼 LP 측정폭과 최종 layout폭이 달라 줄바꿈 잘림 가능. resumeBox EXACT 측정 요청.

## 수행 검사
`java --add-modules jdk.compiler ParseJava.java LobbyView.java MainActivity.java HomeMenuLayout.java HomeMenuArt.java`: Java syntax PASS:4.
컴파일 타입 검증·Robolectric·Android 캡처 또는 APK 빌드 통과를 뜻하지 않음.

## 수정 후 재검토
- 헤더 최소64dp 보정 확인.
- 실제 캐릭터 이미지와 별개로 gullBox 최소48dp 확대 및 stage 안 배치 확인. header72의50dp stage도48dp 타겟을 수용한다.
- 좁은 화면 재화 카운터 폭확대 및 만/억 단축표시, 전체 정확한 값 접근성 설명 유지 확인.
- 양피지 타일 눌림도 진한 잉크 유지 확인.
- 복귀 버튼 실제 resumeBox 폭 EXACT 재측정과2줄/말줄임·전체 접근성 설명 확인.
- 메뉴 키보드 방향키와 접근성 탐색 순서 지정 확인.

추가 회귀 검사: HomeMenuLayoutTest에 header0/64/72, 최소48dp guide stage, 확대 타겟 stage containment, header/resume 제외를 추가했다.
7크기×4밀도×4글꼴배율×2전투상태×3헤더=672개 조합, **93,408개 geometry assertion PASS**.
최신 소스 Java AST4개 다시 PASS. 실제 Android 글꼴 측정/네이티브 렌더/터치 테스트는 미실행.
튜토리얼의 숨겨진 메뉴 타겟 자동 펼침 연동은 구현 담당자가 별도로 확인 중이며 이 검토 PASS 범위에 포함하지 않는다.

## 분리 원화 및 튜토리얼 최종 소스 검토
- HomeMenuArt의 native/home-menu-v1/*.png 로딩 경로는 NativeArt.asset의 native/ 접두 처리와 일치한다.
- play/menu/tile source64px, panel52px은 surface-manifest와 실제4종PNG 크기 안에 있으며9slice 중앙구간이 양수다.
- 대상corner는표면크기의1/3이하로 제한해 대상slice역전이 없다.
- pressed LightingColorFilter, disabledalpha는 그린 후복원되고 focus는실제범위안에그린다.
- 완성홈스크린샷을런타임으로그리지않고별도표면/아이콘을실제값/버튼과조합한다.
- 9slice QA 이미지 직접 확인: 테두리/중앙텍스처에눈에띄는검은이음선없음.
- 튜토리얼홈타겟자동펼침은dialog가열려있을때우회하지않으며, 원래실제Button을반환한다. 버튼의최종위치기반overlay가계속사용된다.
- 안내문과실제바로가기일치필요: defenders타겟이전투편성으로직행하므로본문의'방범대에서'를'전투 편성에서'로바꾸도록전달했다.
- 최신HomeMenuArt/LobbyView/NativeTutorialController와Native테스트5개 총8파일AST PASS. SDK의메서드시그니처타입컴파일또는Robolectric실행결과는아니다.
- 최종14개원화존재/압축해시/원격일치검증은출판담당의완료조건이다(검토시점메뉴7아이콘은준비중).

## 코드 좌표 기반 시각 검토 보완
별도 미리보기 작업자가 새 원화+실제 Java좌표의640×320 타일에서 하단장식/글자 겹침을 발견했다. 구현자가 원화corner20dp를12/16dp로줄이고 라벨4dp상향 및안전여백을늘렸다. 이자료는네이티브캡처가아니며 실제Android글자측정게이트는여전히남는다. 튜토리얼본문도실제전투편성버튼과일치하도록수정했다.
