# Android 새 1대1 전투 검토 실행 단위

**현재 소스 기본 흐름은 [기기 내부 오프라인 검토 모드](OFFLINE-REVIEW.md)입니다.** Node/USB 없이 시작하며 제품 대전·계정과 연결하지 않습니다. 소스/테스트만 갱신했고 새 APK는 만들지 않았습니다. 이전 f77d457 APK에는 이 변경이 없습니다.

후속 PNG 요청: [승인 PNG 연결 준비 상태](APPROVED-PNG-INTEGRATION-PENDING.md). 신규 자료 전송 실패로 **아직 기존 벡터 화면 유지**. 샘플 디코딩/주입 경계만 준비했으며 새 APK 없음.

## 이전 f77d457 구현 기록 (현재 기본 흐름 아님)

2026-10-24: **개발 전용 Android 런처 → 로컬 Node fixture → DTO → 6명령/6기술 패널** 연결 코드를 추가했다. 기존 MainActivity/운영 인증 API/DB는 연결하지 않는다. 앱을 실제 실행하거나 렌더링한 결과는 아니다. Gradle 다운로드가 DNS에서 막혔고 SDK/기기가 없어 Java 컴파일 및 실제 HTTP→Java 계약 검증까지만 완료했다.

기준 브랜치 `review/roster60-static-20261024`, 앱 기준 `cde215207210d4eed8dde80fe5c7b3df629337eb`. 직전 로컬 `420f5d1`까지의 작업을 보존했다. 이번 read-only `git ls-remote`에서 app 브랜치 cde2152, 운영 main-oz3tvo 41104175, gpt-art b4b19815를 다시 확인했다. 원격 코드 변경 없음.

## 개발 환경 실행 절차

프로젝트 루트는 `galmaegiman-confirmed`다. SDK36/JDK17 이상과 정상 Gradle 다운로드가 가능한 개발 장비에서만 아래 Android 단계를 진행한다. 서명 환경변수나 배포 키는 필요하지 않다.

```sh
node review/combat-v2/server.mjs
# 별도 터미널: 연결한 개발 장비/에뮬레이터만 대상으로
adb reverse tcp:18765 tcp:18765
# 디버그 APK를 설치한 뒤 앱 목록의 '갈매미맨 전투 검토 (개발)' 실행
# 화면 '새 검토 전투' 클릭
```

`src/debug/AndroidManifest.xml`의 별도 Activity가 진입점이다. MainActivity는 변경하지 않았고 release에 이 Activity/런처/u01 검토 PNG가 포함되지 않는다. APK 생성/설치는 이번 환경에서 하지 않았다. 기본 디버그 앱에는 기존 MainActivity도 함께 있으므로 **검토 런처만 실행**한다.

서버는 `127.0.0.1:18765`에만 수신한다. Android URL도 이 주소로 고정하고 HTTP redirect를 거부한다. 프로덕션 SERVER_URL/계정 토큰은 사용하지 않는다. Origin 요청과 loopback 이외 Host를 거부한다. 같은 기기의 개발 프로세스를 인증하는 서버는 아니며 외부 공개/포트포워딩용이 아니다. 데이터는 프로세스 메모리뿐이다.

새 전투는 양쪽 u01이며 명확히 표시된 synthetic 스탯이다. 공격·6개 기술 MP 지출 후 fixture 상대가 공격/집중하고 응답한다. 집중은 `floor(maxMP*0.25)`, 최대치 제한, 한 행동 소비. 서버는 양쪽 행동의 최종 DTO를 최초 요청 ID와 함께 저장해서 응답 유실·동시 중복 요청도 상대 행동을 중복 실행하지 않는다. 동일 ID/변경 내용·오래된 revision은 거부한다. Android는 대기 중 입력을 잠그고 실패 시 같은 JSON/requestId로만 재시도한다. 세션 소실/409 복구는 '새 검토 전투'이며 영속 재접속 구현은 아니다. Activity 재생성에는 마지막 DTO와 미확인 요청을 저장한다. 세션32개/명령1000개 제한 도달 시 로컬 서버를 재시작한다.

## 실제 연결 범위와 파일

| 파일 | 역할 |
|---|---|
| `review/combat-v2/server.mjs`, `server.test.mjs` | loopback fixture HTTP, 전체 턴 멱등성·실제 응답 검증 |
| `review/combat-v2/engine.mjs`, `fixture.mjs` | 새 epoch 1대1/MP/적합도/방어/반사, 교체 가능한 밸런스 함수 |
| `app/android/app/src/debug/java/org/dpdns/gmm/CombatV2ReviewActivity.java` | 개발 런처, 전용 HTTP Transport, u01 전후 Canvas, 생명주기/재시도 |
| `app/android/app/src/main/java/org/dpdns/gmm/CombatV2ReviewPanel.java` | 기본/기술 각각2행3열, 별도 뒤로, HP/MP, 길게 누르기 |
| 같은 경로 `CombatV2ReviewContract.java` | epoch·HP/MP·6명령·최대6기술 계약 검증 |
| 같은 경로 `CombatV2ArtCatalog.java` | 최신 manifest active_views/정확한 exclusive rect 공용 입력 |
| 같은 경로 `CombatV2ReviewStyle.java`, `CombatV2Effects.java` | 기존 폰트/남색 토큰, 벡터 사용, 판정과 분리된 공용효과 |
| `app/android/app/src/main/res/drawable/combat_v2_*.xml`, `values/combat_v2_strings.xml` | 아이콘/상태/게이지/배지·문자열 |
| `app/android/app/src/debug/assets/combat-v2/` | b4b19815 manifest 원본 및 u01 front/rear 원본만 |
| `app/android/app/src/androidTest/java/org/dpdns/gmm/CombatV2ReviewLayoutTest.java` | 작은 가로 패널 계측 검사 준비(미실행) |

기술 길게 누르는 동안만 설명·공격 적합도를 표시하고 손 떼기/이동 취소/다중터치/CANCEL/focus 상실/detach 시 미시전·설명 해제. 설명 공간은 미리 예약해 표시 중 그리드를 밀지 않는다. 뒤로 버튼/시스템 뒤로는 기술 목록에서 기본 명령으로 돌아온다. 미연결 아이템/교체/도망은 비활성이고 사유를 텍스트/접근성 설명으로 제공한다.

정해진 피해 규칙: 약점1.5/보통1/내성0.5/무효0, 반사는 대상 피해 없이 공격자 반환·재반사 없음. 기술만8속성, 적합도1당10% 보정과 방어 곱셈, MP 비용 별도. 음수 피해는0으로 제한하며 무효는 별도0 처리한다. 약점 추가턴 없음. 반사 상세 공식·스탯·MP곡선·비용·적합도 분포는 fixture이며 최종 밸런스가 아니다. maxMP<4 집중은 내림 때문에0회복(임의 최소1 없음). 판정 중 정책 실패는 복제 상태를 폐기해 MP 일부 소비를 막는다. 정책 객체/참가자를 복사해 생성 후 외부 변경을 차단했으나 주입 함수의 내부 클로저도 순수해야 한다.

`review-roster60-combat-v1`과 balanceVersion으로 기록하며 기존 운영 replay와 혼합하지 않는다. 계정 인증·영속 session/revision/receipt·다중 서버·아이템/교체/도망·증강체·온보딩 API는 미연결이다. 온보딩의 u01→튜토리얼→우편 선택권→u02~08 영구 지급은 이전 격리 계약/테스트를 유지한다. D1/임시15일 제거 규칙을 운영 지급 코드에 반쯤 적용하지 않았다.

## 리소스·출처·용량

- 8속성 벡터 + 6명령 벡터: 직접 작성. 일반은 타격 섬광 형태이며 바위 원소가 아니다. 아이콘 옆 글자/설명으로 색에만 의존하지 않는다.
- 4버튼 상태 shape(기본/눌림/선택/비활성) + selector, HP/MP2게이지, 방어5배지 = **26 drawable XML**. 패널/아이콘/게이지/배지에 사용한다. 적합도는 기술 설명의 '적합도 · 공격 보정'으로 방어 배지와 분리한다.
- 남색 `0xe8102433`, 글자 `0xfff5ead0`는 기존 NavyWorkbenchTheme에서 가져왔다. 새 양피지·금장·질감 PNG 없음.
- 실제 기존 폰트 `assets/fonts/workbench-noto-regular.otf`를 재사용. 출처/라이선스 `assets/fonts/OFL-workbench.txt`(SIL OFL1.1, Adobe/Google 고지). 새 폰트 도입 없음.
- 공용 시전 섬광·타격/피격 링·집중 십자/링은 직접 작성 Canvas 코드. **기본 끔**, 별도 토글. 효과 비활성화는 판정/MP/행동과 무관하다. 무효에 성공 피격 효과를 만들지 않고 반사는 반환 방향 화살표를 표시한다. 현재 새 이벤트만 표시하며 캐릭터 관절/표정/상위 전용 애니메이션은 없다.
- u01 원본/manifest는 사용자 제공 GitHub `gpt-art`의 **b4b19815e047b4a8189a084d44f78ae22530d7ed**에서 정확한 blob으로 복사. 기존 프로젝트 아트의 출처를 보존하며 새 제3자 라이선스를 주장하지 않는다. 3파일 합 **3,331,871 bytes**, 원본 픽셀 변경 없음. 앞/뒤 실제 파일 확인 및 SHA256 일치. 원본 PNG 두 장만 번들, 나머지59종 PNG는 이번 번들에 없음.
- 공용 catalog는60개 ID/120개 active view를 검증했다. 50% 분할하지 않으며 u17 rear-v4/u31 v6 비대칭rect 유지. 미니 높이0.6만 확정; 다른 종 상대높이1은 미검증 preview fallback이다. u01 발점(front629.5,1206 / rear627,1190)은 alpha 기반 후보, 실기기 승인 아님. 이번 최신 아트 갱신의 최종 승인은 u31 v6 디자인/방향만이며 전체60종 최종승인을 의미하지 않는다.

옛 상위 전용 애니메이션은 새 체계에서 **사용하지 않는 자산**이다. 새 검토 코드/자산 경로는 UpperAnimationArt를 참조하지 않는다. 다만 기존 공용 APK 패키징 `app/android/app/build.gradle:197~227`은 prepareUpper33Art→bundleGameArt→`native/upper33-v1`을 아직 포함한다. 기존 `BattleView.java:999`가 UpperAnimationArt를 생성하므로 이번에 공용 패키징에서 일괄 삭제하지 않았다. 옛 runtime 전환 후 해당 의존성과 번들 경로를 함께 제거해야 한다. 이번 실제 제외 파일 **0**, 검증된 APK 절감량 **없음**. 조사된 원본은 animation-v1 946파일/600,974,122bytes, runtime-v1 3파일/290,759bytes이며 APK 크기가 아니다. common8 신규 모션과 혼동하거나 삭제하지 않았다. Git 원본/외부 자료 영구 삭제 없음.

## 검증 결과·차단

- Node **19/19 통과**: 엔진11, 실제 HTTP1, 기존 온보딩7. 중복 동시요청·revision·6번째 기술·MP·집중 내림/상한·무효/반사·실패 원자성·외부 config 변경 회귀 포함.
- JDK21 compiler 모듈 + Android API16 compile stub/org.json으로 Activity 포함 신규 Java 전체 컴파일 통과. 실제 HTTP DTO→Java contract/gesture 검사 통과. 전체 Android 앱 빌드 성공을 의미하지 않는다.
- Java manifest 검사:120뷰/u17override/u31front906px/미니60%/옛C1매핑거부 통과. Python: 모든rect 원본범위, u01hash/RGBA,26 drawable XML·strings 구문 통과. 이미지 실제 정면/후면을 열어 확인했다.
- 독립 코드 검토에서 발견한 4건(높이px, 눌림 상태, 이전 턴 효과 재생, players/balance 외부 참조)을 수정했다.
- **Gradle 실패**: `:app:compileDebugJavaWithJavac --no-daemon` 시 wrapper의 `services.gradle.org` 다운로드가 `UnknownHostException`에서 중단. 컴파일 전에 발생한 DNS/네트워크 실패이며 Library403/권한 거부가 아니다. 서명 관련 환경변수4개를 child process에서 unset 후 실행했고 키/암호를 조회하지 않았다.
- `adb`, `sdkmanager`, `emulator`가 PATH에 없고 `/opt/android-sdk`, `/usr/lib/android-sdk`도 없음. 실기기/에뮬레이터 실행, Android HTTP 왕복, AAPT 리소스 링크, APK/AAB, 실제 스크린샷 **미실행**. 스크린샷을 생성하거나 렌더 완료로 주장하지 않는다.
- 작은 가로 검증 준비:640×320dp/font_scale1에서 오른쪽320×244dp 패널을 measure/layout해6버튼/기술7버튼(뒤로 포함), 높이·텍스트 수용을 검사하는 계측 소스 추가. 실제 UI/큰 글꼴/접근성/겹침 검증은 실행 대기다. 이 기준 이하 크기나 큰 글꼴 지원을 완료로 간주하지 않는다.

자동 확인:
```sh
REVIEW_DTO_OUTPUT=/tmp/combat-http.json node --test review/combat-v2/*.test.mjs review/onboarding-v2/*.test.mjs
python3 review/combat-v2/validate-resources.py
# Java 컴파일 후 AndroidContractCheck /tmp/combat-http.json 및 ArtCatalogCheck <debug manifest> 실행
```

다음 순서: SDK/Gradle 환경에서 debug compile·계측·실제 로컬 HTTP/스크린샷 → 화면 크기/롱프레스/취소/회전 검증 → 승인 데이터 공급 및 영속 인증 API → 아이템/교체/도망·증강체/온보딩 운영 교체 → 구패키징 제거 검증. 수치·반사 공식·증강체 목록은 별도 밸런스 승인 대상이며 3대3 실행은 보류다. 원격 push/운영 병합/배포/DB 변경/Library403 재시도 없음.
