# 추가 3개 화면 진단 / 수정 백로그

상태: 소스 기반 진단 완료, 아래 3개 화면 수정은 아직 미적용. 부모가 실제로 확인한 사용자 캡처의 증상과 소스 경로를 대조했다. 소비 환경에서 해당 Library 이미지 픽셀을 새로 내려받아 확인한 것은 아니다. 설치 APK와 현재 소스의 정확한 일치도는 미검증.

1. 조합: `NativeRoster60Craft.java:44` carousel이 label만 추가하고 ImageView/NativeRoster60Art/RegistrationV10Art를 전혀 바인딩하지 않는다. 중앙 슬롯도 :51 이름 Button뿐이다. 서버 `roster60-craft.ts:26` material은 speciesId, :27 recipe는 resultId 제공하므로 ID가 없는 서버 문제로 볼 근거가 없다. 기존 roster60-art/index.json의 front 매핑을 재사용해 재료 speciesId/결과 resultId를 연결해야 한다. 모집 등록증 이미지를 임의로 대신 쓰지 않는다. 기존 선택/탭해제/슬라이드와 3열 배치를 보존한다.
2. 도감: `NativeRoster60Library.java:15` 어두운 Navy 패널, :19 제목 및 :25~26 세부글자가 h.text 기본값을 그대로 사용한다. `MainActivity.java:445` 기본 text 색은 INK다. 밝은 NavyWorkbenchTheme.TEXT 적용 누락이다. :19 오른쪽 ScrollView 아래 모든 스탯/적합도/방어/기술을 세로 적재하므로 짧은 가로 화면에 전체 정보를 한 번에 보여주지 못한다. 그림은 :24에서 id→manifest front→roster60-art/file로 이미 바인딩되어 있다. 전체 아트 누락이나 옛 계정 탓으로 단정하면 안 된다. 기본/속성/기술 페이지 분할 등 고정 영역에 맞는 개선 필요.
3. 모집: `RecruitmentScreen.java:209~210` 후보 세션이면 limit=1로 제한. :216에서 + 입력을 다시1로 보정하면서 :224는 +/−/입력 컨트롤을 enabled로 둔다. 최대만 꺼져 있어 조작이 고장난 것처럼 보인다. `Roster60RecruitPurchase.java:52` 후보 세션 buy는 times를 보내지 않고 start 한 건만 보낸다. 새로운 다회 후보/리롤 정책을 임의 도입하지 말고 현재 단일 후보 흐름에서는 수량 컨트롤을 숨기고 '후보 1명씩 모집'으로 명시하는 것이 최소 수정안이다. 비용/확률과 기존 지원서 잔액 변경 불필요.

후속 회귀 기준:
- 조합 u05 재료/ u21 결과를 포함한 60개 ID 정면 매핑, 6슬롯 선택/해제, 페이지 이동 시 이전 이미지 잔상 없음, missing art 명시, 원본 비율/샘플디코딩 유지.
- 도감 제목/5스탯/적합도/방어/6기술 밝은 대비, 가로 작은 viewport/fontScale에서 영역 겹침/잘림 없음, 등급/보유필터/길게 누름 해제 동작 유지.
- 모집 session모드에서 수량 조절 불가능 UI 노출 없음, 1후보/동급리롤1회/최종1회수령 유지. 구 다회 경로(남아있는 경우)의 수량 계산은 별도 보존, 비용2/6/10 응답 그대로. 사용자 재화 소모 없이 격리 응답/단위시험으로 검사.
- 메뉴 잠금 패치 a6f4c70과 독립 화면 패치로 관리. 운영배포/push/계정초기화 없음.
