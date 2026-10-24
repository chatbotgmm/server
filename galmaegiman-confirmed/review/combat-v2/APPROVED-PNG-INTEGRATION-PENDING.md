# 승인 PNG 연결 준비 — 자료 접근 차단, 미적용

요청: 8속성+6명령 14슬롯의 승인 PNG를 개발 전투 화면에 연결. 일반/공격은 같은 그림, 집중은 focus-v2만 사용. 남색 패널/문자열/기존 상태 표현 유지. 새 방어5종/VFX4종은 아직 입력 대상 아님.

## 실제 자료 접근 결과

현재 Library prepare_materialize 도구를 확인하고 현재 환경 목적지 `/workspace/combat-v2-approved-icons`를 지정했다.
- ZIP `libfile_8815b7d4dd08819187fd329f1e6dd885`, version0, `전투UI_이미지리소스_14종_원본.zip`, 12,712,181bytes.
- 미리보기 `libfile_9f113408e02481919668361a9eb87a0b`, version0, `전투UI_이미지리소스_14종.png`, 922,174bytes.

준비 요청은 두 파일 모두 성공하고 전송 정보를 반환했다. 최신 공식 Library helper를 새 디렉터리에 받아 각 파일에 한 번씩 실행했으나 모두 `library file transfer failed: download failed` (exit1). HTTP 상태/권한 거부 상세는 제공되지 않았다. 따라서 이전403과 동일한 오류라고 단정하지 않는다. 이 단계는 도구 조회 network 실패도 아니며 **준비 이후 실제 바이트 전송 실패**다.

목적지에는 완성 파일이 없다. ZIP manifest/README/선택 목록/PNG 픽셀을 읽거나 해시 검증하지 못했다. 원본이1254×1254 RGBA라는 것은 사용자 전달 정보이며 현 환경 검증 결과가 아니다. 재시도·다른 API·추측 URL·다른 환경 경로 사용 없음.

## 파일 없이 완료한 코드 준비

- `CombatV2ReviewPanel.java`: IconBinder 주입 경계 추가. 기존 생성자는 기존 벡터 바인더를 그대로 사용하며 Activity 호출도 변경하지 않음.
- `src/debug/java/org/dpdns/gmm/CombatV2ReviewPngIcons.java`: 명시적인 검증 매핑을 전달받는 개발 전용 PNG 바인더 준비. 현재 Activity에 연결하지 않음.
- `CombatV2IconSpec.java`: 14슬롯 누락/경로 검증, normal/attack 동일 경로, focus-v2 표시 요구. 이는 **향후 확인한 ZIP manifest를 변환할 어댑터 입력 계약**이며 읽지 못한 실제 ZIP 스키마를 추측한 것이 아님. focus-v2 표시는 픽셀 증명이 아니므로 파일 확보 시 manifest/해시/시안 확인이 반드시 필요.
- `IconSpecCheck.java`: 합성 경로를 쓰는 코드 계약 테스트. 승인 이미지 fixture로 오인하지 말 것.

로더는 먼저 inJustDecodeBounds로 크기만 읽고, 사용 크기(현재 기존 UI와 같은12dp)에 맞는 power-of-two inSampleSize를 계산한다. inScaled=false, ARGB_8888, path+target 단위512KiB bitmap LRU; normal/attack은 같은 cache 키. Drawable은 버튼마다 별도 생성한다. 원본을 동시 최대 해상도 디코딩하거나 tint/색 변경하지 않는다. cache clear 때 표시 중인 bitmap을 강제로 recycle하지 않는다. 예상 파일이 없거나 매핑이 틀리면 조용히 새 아트 적용으로 오인시키지 않고 실패한다.

12dp에서 새 일러스트의 식별성이 충분한지는 아직 미검증이며 실제 PNG 확보 후 작은 화면에서 확인해야 한다. 기존 눌림/선택/비활성 배경과 라벨은 유지한다.

## 검증

설치된 JDK 컴파일러 + Android SDK36 android.jar로 신규 로더/패널/기존 Activity Java 컴파일 통과. JVM 계약 검사 통과:14슬롯, immutable 매핑, normal/attack 공유, focus-v2 gate, traversal 거부,1254px 기준12/24/36/48/64px 목표의 sample 계산. 실제 PNG 디코딩/Android 렌더/실기기/새 APK는 미실행.

기존 f77d457 APK는 구 도형 아이콘판 그대로다. 이 준비 코드가 그 APK에 포함됐거나 신규 PNG가 적용됐다고 표시하지 않는다. 운영/원격push/DB/새APK 변경 없음.

## 파일 확보 후 최소 다음 단계

1. 같은 지원 전송 경로가 복구되면 승인 ZIP/미리보기 확보, actual manifest/선택 목록 읽기, 픽셀·알파·해시 확인. rejected 집중 제외/focus-v2 선택 검증.
2. 관리 원본은 별도 `art/gpt/android-battle-v2/ui-icons-approved-v1/`에 보존하는 방안. 개발 런타임 입력만 `src/debug/assets/combat-v2/icons/`에 배치하고 실제 manifest에서 검증된 매핑 생성. 현재 이 경로에 원본 파일을 만든 것은 아님.
3. 승인 전 임의 파생 이미지/색 변경/외부 최적화 사용 없음. 필요 시 로컬 파생 크기·포맷과 원본 해시 연결을 별도 검토. 현재는 런타임 샘플 디코딩으로 메모리만 관리하며 APK 용량 절감은 아직 없음.
4. Activity의 IconBinder를 검증된 PNG 바인더로 교체하고 실제 이미지 decode/길게 누르기/상태/UI 작은 크기 검증. 그 이후 별도 승인 시 새APK.
