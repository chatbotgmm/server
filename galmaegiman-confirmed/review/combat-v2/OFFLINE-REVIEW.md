# 기기 내부 오프라인 검토 모드

현재 개발 Activity는 외부 서버 없이 시작한다. 화면에 **오프라인 검토 전투 · 기기 내부 fixture · 수치 미확정 · 제품 대전/계정 미연결**을 표시한다. 기존 PC Node/USB 경로를 기본 흐름에서 제거했으며 Node 서버 코드는 기준 엔진과 개발 테스트용으로 보존했다. 제품 로그인·실제 대전·유저 보유 개체·서버 DB와 연결하지 않는다.

이번 작업은 소스/로컬 컴파일/테스트만 수행했다. **새 APK를 생성하지 않았다.** 이전 f77d457 APK는 여전히 PC Node+USB가 필요한 구도형판이며 이번 오프라인 코드를 포함하지 않는다.

## 구현

- `src/debug/java/org/dpdns/gmm/CombatV2OfflineEngine.java`: org.json 기반 순수 Java 검토 판정기. Node 검토 epoch/DTO와 동일한 1대1 행동·MP·적합도·방어·반사 계약. 운영 엔진 대체물이 아니다.
- `CombatV2OfflineSession.java`: 화면이 사용하는 로컬 Transport의 세션 계층. 사용자/상대 행동을 복제 엔진에서 모두 성공시킨 뒤 한 번에 확정. 동일 요청은 원래 최종 DTO, 같은ID/다른내용은 충돌. synchronized로 동시 반복 제출 보호. 오류 시 원본 전투 미변경.
- `CombatV2ReviewActivity.java`: HttpURLConnection/실행 스레드/응답 재시도 UI 제거. 첫 진입에 로컬 fixture 전투 자동 생성. 초기화 버튼은 새 battleId와 가득 찬 HP/MP로 재시작. Panel/아트/공용효과 렌더 경계를 재사용한다.
- `src/debug/assets/combat-v2/offline-fixture.json`: Node `fixture.mjs`에서 생성한 명시적 synthetic 설정. u01 대 u01, 기존 테스트용 maxMP13, 기본 공격비용2 등 **이미 있던 검토값**이며 최종 밸런스 승격이 아니다. `check-offline-parity.py`가 Node 생성값과 번들 파일의 의미상 일치를 검사한다.
- `CombatV2ReviewPanel.java`: 기술 페이지 저장·복원 경계와 pause 시 hold 취소. 기존 PNG IconBinder 경계 보존; 승인 PNG가 아직 없으므로 기존 벡터 바인더를 유지한다.

집중은 최대MP25% 내림/상한, 4미만 MP에서는0회복. 큰 정수MP는 long 합산 후 상한 처리해 음수 overflow를 막는다. 양쪽 개체의 실제 attack/skills 정수비용/6기술 상한/중복ID/8속성·방어 완전성을 검증한다. Node의 일반적인 JS safe-integer 범위 전체를 지원하는 제품 엔진이 아니며 이 Android 검토기는 vitals/cost를 비음수32비트 정수로 제한한다. 반사 상세 공식은 기존 `fixture-base-times-fit` 검토 정책만 지원하고 제품 승인 공식이라고 표시하지 않는다.

재생성: Bundle에 초기 fixture와 성공한 사용자 명령만 저장하고 재생하여 상태·receipt를 복구한다. 기술 목록 열린 상태도 복구. compact 이력은128턴까지 제한하고 이후 새 검토 전투를 시작하도록 거부한다. 일반 화면 재생성은 이어지지만 종료 후 새 진입은 새 검토 전투다. 영속 DB 저장·여러 프로세스 동기화·계정 복구 기능은 아니다. 효과는 DTO의 새 이벤트만 표현하며 판정과 분리되어 있다.

## 검증

1. `offline-cases.mjs`가 같은 Node 기준 엔진으로13개 시나리오, 잘못된 설정6개, 양쪽 행동/재진입 세션 사례의 기대값을 생성한다. `OfflineParityCheck.java`가 **65항목**을 비교했다. 초기MP/개체·레벨별 생성값, 6번째 기술, MP부족, 집중내림·상한/작은MP/큰MP, 양수·음수 적합도, 일반 포함5방어, 무효와 매우 큰 적합도, 단일 반사·치명반사, turn/revision/중복/동시중복, 거부 시 상태 불변, 재생성 후 과거receipt 재시도, 새 진입을 확인했다.
2. 기존 Node 엔진·HTTP·온보딩 테스트 **19/19 통과**. 서버/온보딩 규칙은 변경하지 않았다.
3. `CombatV2OfflineScreenTest`의 Robolectric API35 실제 Activity 테스트: 자동 시작MP13, 6번째 기술, 길게 누른 후 손떼기 미시전, CANCEL 미시전, 누르는 중 뒤로가기 미시전, pause/resume 취소, 시스템뒤로 기본메뉴, 집중 회복, Activity 재생성/기술페이지 복원, 새 진입 초기화.
4. 독립 검토에서 발견한 큰MP overflow와 실제 기술비용 검증 누락을 수정하고 회귀 사례를 추가했다. 독립 재실행도65항목 통과.

이 결과는 JVM/Robolectric 테스트이며 **실기기·에뮬레이터·픽셀 스크린샷/작은 화면 가독성 검증은 아니다**. PNG 실제 디코딩/신규 그림체 표시도 미검증이다. Gradle 실행 목표는 `:app:testDebugUnitTest --tests org.dpdns.gmm.CombatV2OfflineScreenTest`이며 assemble/package APK 목표를 실행하지 않았다.

재현:
```sh
python3 review/combat-v2/check-offline-parity.py --java <JDK>/bin/java --javac <JDK>/bin/javac --json-jar <org.json jar>
node --test review/combat-v2/*.test.mjs review/onboarding-v2/*.test.mjs
```
이 환경에서 Robolectric 다운로드 lock이 기본 사용자 홈의 read-only 경로에 생기는 문제는 테스트 JVM의 `user.home`을 `/workspace/android-build-tools/test-user`로 지정한 로컬 init script로 해결했다. 기존 proxy 설정을 Gradle/test JVM에 전달했다. 승인 PNG 전송이나 그403을 우회한 작업이 아니다.

## 이미지 연결의 남은 상태

- 첫14PNG ZIP `libfile_8815b7d4dd08819187fd329f1e6dd885` v0: 준비는 성공했으나 공식 바이트 전송에서 `URLError -> OSError(403)` 확인. 추가 재시도 없음. 실제 파일/manifest/픽셀/해시 미확인. `CombatV2ReviewPngIcons`와 IconSpec 준비 코드는 보존했지만 아직 Activity에 연결하지 않았다.
- 두 번째9PNG ZIP `libfile_a81cb1c5429081918e18026abeb24364`는 전달된 최신 정보상 **v1**. 최신 수정 파일은 `weak-v2.png`, `normal-v2.png`, `null-v4.png`, `reflect-v2.png`. 내성과 공용효과4개는 동일. **사용자 최종 평가 전이며 승인 완료가 아니다.** 이 환경에서 다운로드하지 않았다.
- 남은 단계: 지원 전송의403 해결 후 실제 PNG 확인/검증 매핑/샘플 디코딩/UI 교체, 사용자 평가 중인4종의 최종 상태 확인. 이후 별도 APK 요청 범위에서 새 이미지+오프라인판 빌드/기기 확인/전달. 이번에 구 APK를 새 결과로 다시 제공하지 않았다.

원격push/운영merge·배포/DB/Play/서명키 접근 없음.
