# 새 1대1 전투 최소 실행 단위 (검토 전용)

기준 앱 cde215207210d4eed8dde80fe5c7b3df629337eb 위 기존 검토 브랜치 변경을 보존했다. 운영 라우트/DB/기존 전투 엔진에 반쯤 연결하지 않고 새 review epoch로 분리했다. **Node 명령 처리 → 실제 JSON DTO → Java DTO 검증 → Android 명령 패널** 경계를 구현했다. Android 패널은 아직 MainActivity에 부착하지 않았으며 실제 화면 렌더/네트워크 왕복 검증은 미실행이다.

## 실행

```sh
node --test review/combat-v2/engine.test.mjs review/onboarding-v2/contract.test.mjs
node review/combat-v2/demo.mjs > /tmp/combat-v2-review-dto.json
```

`engine.mjs`는 상태·참가자·현재 행동권·MP·요청 revision·요청 ID를 보유하는 동기식 검토 프로세서다. 서버가 신뢰하는 auth principal만 받도록 설계했지만 실제 로그인/HTTP 인증 어댑터는 없다. 참가자가 아닌 principal, 옛 revision, 모르는 기술, MP 부족, 미연결 행동을 거부한다. 같은 요청 ID/내용은 원래 결과, 같은 ID/다른 내용은 충돌이다. 실패하는 정책 함수는 상태/MP를 일부 변경하지 않는다. 이 보장은 단일 프로세스 메모리 범위이며 프로세스 재시작·다중 서버·영속 트랜잭션 보장이 아니다.

## 연결한 기능

- 기술 속성만 일반·불·물·번개·얼음·자연·성·어둠 8개. 새 fighter에는 자체 속성을 저장하지 않는다.
- 약점1.5/보통1/내성0.5/무효0. 적합도 1당 피해10%를 방어 배율과 곱하고 MP 비용과 분리. 일반 속성 공격에도 동일 적용. 음수 적합도에서 음수 피해·회복이 발생하지 않도록 `max(0,1+0.1*적합도)` 사용. 이는 확정된 적합도 하한/분포가 아니라 유효 피해 하한 안전 처리다. 무효는 높은 적합도로 뚫리지 않는다.
- 반사 시 대상 HP는 그대로, 공격자에게 단 한 번 반환. 재반사 함수 재호출 없음. 반사 계산 자체는 필수 주입 정책으로 분리했다. 테스트의 단순 반환 공식은 최종 밸런스가 아니다. 현재 최소 엔진에는 흡혈/적중 추가효과 시스템 자체가 없으며 향후 효과 순서 승인과 연결이 필요하다.
- 개체/레벨별 최대MP는 필수 `maxMp(fighter)` 설정에서 산출. 새 전투는 최대 MP로 시작. 기술별 비용과 기본 공격 비용 모두 외부 데이터에 있어 0MP 기본공격을 제품 기본값으로 확정하지 않았다.
- 집중은 자신의 한 행동을 소비하고 최대MP의25% 회복, 최대치 제한. 검토 구현 선택: 회복량은 내림, 피해도 내림 후 실제 남은 HP로 제한. 최대MP가4 미만이면 집중 회복량은0이며, 최소1 보정은 임의 도입하지 않았다. 약점/집중으로 추가 행동을 주지 않는다.
- 각 진영1개체, 교대로 한 행동. 첫 행동 진영은 필수 입력으로 받아 속도/선공 정책을 임의 확정하지 않는다. 3대3·전체 대상 기술 실행 없음.
- Android `CombatV2ReviewPanel`은 기본 명령6개를2행3열로 표시하고 기술 선택 시 별도2행3열로 전환한다. 뒤로 버튼은 그리드 밖. 6번째 기술까지 사용 가능, 7개 이상은 조용히 자르지 않고 계약 오류. HP/MP·비용·미사용 사유 표시.
- 아이템/교체/도망 명령은 보이되 미연결 사유와 함께 비활성. 보유 아이템 소비·교체 대상·도망 확률을 임의 구현하지 않았다.
- 기술을 길게 누르는 동안 설명 표시, 손을 떼면 닫힘/시전 안 함. 이동·다중터치·취소·focus 상실·detach 때 타이머와 설명 정리. MP 부족 기술도 설명은 볼 수 있으나 클릭 시전은 차단. 네트워크 응답 대기 중 중복 탭 차단. Transport는 결과 불명 재시도 시 최초 requestId를 보존해야 한다.

## 파일과 실제 연결 범위

- `review/combat-v2/engine.mjs`: 1대1 명령/판정/DTO/메모리 receipt와 검토 replay frames.
- `fixture.mjs`: 명백히 표시된 가상 스탯·비용·MP 성장·기본 피해·반사 정책. 승인된 60종 데이터가 아님.
- `engine.test.mjs`: 신규10개 테스트. `demo.mjs`: Java 검증용 실제 DTO 출력.
- `app/android/app/src/main/java/org/dpdns/gmm/CombatV2ReviewContract.java`: epoch/MP/6명령/6기술 DTO gate.
- 같은 경로 `CombatV2ReviewPanel.java`: 실제 Android View 명령 UI. `bind(snapshot)` 및 `Transport.submit(command)`로 연결하는 경계이며 MainActivity/운영 API에서 생성하지 않음.
- 기존 `SkillHoldGesture.java`를 변경 없이 재사용. `AndroidContractCheck.java`가 DTO와 gesture를 JVM에서 검증.

review epoch는 `review-roster60-combat-v1`; balanceVersion도 별도 기록한다. 메모리 frame에는 HP/MP 변경·방어 판정·반사 대상이 보존되지만 기존 `gmm-combat-20261021-v3` 리플레이와 섞지 않는다. 영속 기록·재시작 복원·엔진 재실행 검증은 다음 어댑터 작업이다.

## 검증 증거와 한계

- Node: 신규10 + 기존 온보딩7 = **17/17 통과**. MP 시작/성장, 집중 회복/상한/행동권, 6번째 기술/부족MP, 4방어 배율·일반·적합도, 음수/무효 안전, 반사/재반사 없음/종료, 참가자/멱등/revision, 실패 원자성, 7기술 거부, 버전/HP/MP frame 검사.
- Java: JDK21 compiler 모듈로 신규 패널/DTO gate + 기존 SkillHoldGesture + JVM 검사기를 **로컬 컴파일 성공**. 컴파일 classpath는 Maven의 Android API16 stub(`com.google.android:android:4.1.1.4`)와 org.json 20240303. 임시 `/tmp/combat-v2-compile`만 사용. 전체 앱의 SDK36/Gradle 빌드 성공을 의미하지 않는다.
- 실제 Node DTO를 Java에서 읽어 6기술 보존, epoch/MP/개수 오류 거부, long-hold/tap/drag/cancel 상태 검증 통과. **Android View를 실제로 inflate/render하거나 터치 주입한 테스트가 아니다.**
- 미실행: 전체 앱 Gradle/단위·계측 테스트, 에뮬레이터/실기기, 작은 화면·큰 글꼴·UI 겹침, 실제 Android Transport/인증 API 연결, DB 거래, 운영 push/병합/배포/초기화. 별도 APK/AAB 미생성.

## 최신 아트와 온보딩 상태

정상 Git 접근으로 b4b19815e047b4a8189a084d44f78ae22530d7ed manifest를 확인했다. 사용자 최종 승인은 **u31 v6 디자인/방향만**이다. u31 활성 파일 `turnaround-v6-direction.png`, front rect `[0,0,906,887]`, rear rect `[906,0,1774,887]`, right/bottom exclusive. 전체60종 승인으로 확대하지 않는다. 이번 명령 UI 단위에는 이미지를 연결하지 않아 이전 d8465fd3 고정 준비 패키지를 최신 u31로 오인하지 않는다. 후속 renderer는 최신 manifest 영역을 사용해야 한다. Library403 재시도 없음.

온보딩은 기존 격리 계약의 u01 갈매미맨 → 튜토리얼 → 완료 우편 선택권 → u02~u08 중1 영구 지급을 유지한다. D1/15일 임시 폐지는 새 흐름의 확정 규칙이며 실제 운영 지급 코드 제거/API 연결은 아직이다. 승인되지 않은 가상 전투 fixture를 우편 선택권의 승인 캐릭터 상세로 제공하지 않는다.

## 다음 연결

1. 새 review 화면 host/Transport와 authenticated 검토 API를 붙여 같은 DTO/명령을 왕복 검증. 최초 요청 ID를 재사용하고 stale 응답/결과 불명 시 서버 상태 재조회. 영속 session/revision/receipt를 원자 트랜잭션으로 저장.
2. 승인 데이터 공급 경계에 실제60종 기술·비용·MP 성장·적합도/방어 프로필 연결. 수치 미정은 설정 데이터로 유지하며 fixture를 제품 기본값으로 승격하지 않음. 반사 정책도 독립 교체.
3. 최신 아트 renderer·발 기준점·미니60%·HP/MP와 겹침을 실기기 검토. UI의 focus 손실/창닫힘/롱프레스 및 6칸 접근성을 계측.
4. 아이템/교체/도망 계약과 전체 증강체는 별도 정의 후 활성화. 3대3은 보류. 온보딩/우편 영속 어댑터와 함께 검증 후 기존 흐름 교체 범위를 확정. 이번 작업에서 운영 삭제나 배포는 하지 않음.
