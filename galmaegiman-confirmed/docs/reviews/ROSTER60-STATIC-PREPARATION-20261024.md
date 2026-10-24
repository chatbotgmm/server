# 새 60종 1대1 전투: 정적 자산 준비와 구현 전 결정

## 최신 범위

2026-10-24 사용자 결정이 이전 145종 병행 보존 방침보다 우선한다. 85버전까지의 옛 캐릭터/자체속성/증강체와 관련 테스트 성장 데이터는 새 체계와 영구 병행할 필요가 없다. 새 60종 전투·화면·증강체 개편을 준비한다. 이번에는 **1대1만**, 3대3 UI/실행은 보류한다. 계정 인증·결제 등 무관 데이터는 초기화 범위 밖이다. 이번 작업은 정적 자산 준비 스크립트와 문서만 작성하며 운영 DB 삭제·변경, 코드 push/병합, 빌드, 배포는 하지 않았다.

## 기준과 접근

- 실제 원격 heads 재조회: 앱 `manual/home-assembly-20261019` = `cde215207210d4eed8dde80fe5c7b3df629337eb`, 운영 `main-oz3tvo` = `41104175d007f726a126ad39f8187dce9ccadff3`. cde2152 이후 해당 앱 브랜치 변경 없음.
- 아트 `gpt-art` = `d8465fd3d991212e5318fe3cee2fdd2326c6c375`. 정상 Git fetch 및 정확한 blob 읽기로 `art/gpt/android-battle-v2/roster60/` 접근 성공. Library 403 경로는 사용하지 않았다.
- 작업 브랜치 `review/roster60-static-20261024`, worktree `/workspace/server-roster60`, 기준 cde2152. 이전 운영 기반 조사 브랜치 위에 기능 코드를 붙이지 않았다. AGENTS.md 및 관련 .agents/skills/SKILL.md는 체크아웃에서 발견되지 않았다.
- 원본은 `/workspace/roster60-d8465fd3`에 Git blob 그대로 읽었다. GitHub gpt-art의 원본을 수정하거나 앱 브랜치에 69PNG 전체를 중복 추가하지 않았다.

## 완료한 검토용 구현

`scripts/prepare-roster60-static-review.py`는 Python/Pillow 오프라인 준비 도구다. 네트워크·Git·Gradle·DB 호출을 하지 않는다. 지정 커밋의 manifest SHA-256을 고정하고 모든 원본 PNG 해시·크기·RGBA·활성 영역 경계·alpha를 확인한 뒤 새 출력 폴더에 검토용 catalog와 u01 원본 2장을 복사한다. 기존 출력 경로는 덮어쓰지 않는다.

```sh
python3 scripts/prepare-roster60-static-review.py \
  /workspace/roster60-d8465fd3 /workspace/roster60-static-review-new-u01
```

- 전체 60종·120 활성뷰·69PNG 검증 완료. 중앙 분할하지 않고 active_views.source_rect_ltrb_exclusive 사용.
- u17 front는 원본 `[0,0,916,887]`, rear는 `u17/rear-v4.png`임을 확인. 이전 후면은 선택하지 않음.
- 최종 출력은 **새 u01만**, 옛 C1 영구 매핑 없음. 처음 요청의 C1 임시 출력은 최신 결과가 아니며 사용하지 않는다.
- side 0=rear(아군), side 1=front(적), default_enabled=false, 기존 renderer fallback 계약. 실제 앱이 catalog를 읽도록 연결하지 않았음.
- 미니 u02는 기본 대비 0.6 높이 제약을 기록했지만 현재 u01 패키지에 넣지 않았다. 다른 개체를 모두 같은 높이로 자동 정규화하지 않음.
- anchor는 alpha>8 실루엣 하단 중앙의 **후보**다. 발 접지점의 최종 승인값으로 승격하지 않는다. source rect와 별도로 보존하므로 PNG 픽셀을 자르거나 다시 인코딩하지 않음.
- 출력 `/workspace/roster60-static-review-new-u01/`: catalog.json, validation.json, u01/front.png, u01/rear.png, 합계 2,997,437 bytes. 이것은 APK/빌드 산출물이 아니다.

## 최소 연결 순서

1. 새 1대1 DTO/fixture와 u01 정적 검토 화면부터 만든다. 기존 전투 진입 제한을 우회하려고 C1 수치나 지급 데이터를 만들지 않는다. 현 `src/pvp/service.ts`에는 length===3 및 3개체 봇/프로토콜 제약이 있어 별도 변경이 필요하다.
2. 원본 경로는 gpt-art의 `art/gpt/android-battle-v2/roster60/`로 유지. 앱 패키지용 경량 subset은 `native/roster60-static-review/` 같은 generated assets에 명시적으로 포함하도록 후속 연결한다. 지금 build.gradle은 변경하지 않았다.
3. Android `BattleView.Scene.fighter()` 및 별도 정적 bitmap loader에서 source rect, visible bounds, 승인 anchor로 발선을 맞춘다. 이미지 디코딩/캐시와 detach 정리 필요. 동일 원본의 매 프레임 요청/디코딩 금지. 옛 UpperAnimationArt의 확장이나 새 상위 애니메이션은 이번 대상 아님.
4. 롱프레스는 `SkillCardView`/`SkillHoldGesture`의 입력 흐름을 유지하고, 아트 View가 터치를 가로채지 않게 한다. 새 UI 시안의 HP/MP·기술/로그 패널 가림을 실제 가로 화면으로 검증한다. 현재 코드 준비만으로 겹침이 해결됐다고 주장하지 않음.
5. 새 60종 데이터 계약·기술만 8속성·MP·적성·개체 방어 프로필·전체 증강체를 확정한 뒤 서버 engine/core/rules/public-dto와 Android 표시를 연결한다. 1대1은 각 진영 active 1개만 허용하되 향후 team/target 목록 확장이 가능한 구조를 고려. 3대3 실행·UI는 추가하지 않음.
6. 테스트 초기화는 별도 스크립트 설계와 대상 테이블/필드 allowlist, dry-run/백업/fixture부터 준비. 옛 게임 상태와 계정 인증·결제를 구분하며 실제 운영 삭제는 지금 실행하지 않는다.

## 튜토리얼 D1 지급 제거 조사

- `src/my.ts:381` `/my/api/tutorial` → `src/game.ts:1575` webTutorialSet. 기존 1~2단계에서 next>=3, D1 도감 이력이 없으면 grant(D1,TUTORIAL). 건너뛰기 next=9이고 battleTeam이 비면 D1을 넣는다. '완료 시 지급'만 검색해서 마지막 단계만 수정하면 놓친다.
- webRun의 큐/Prisma 트랜잭션 안에서 단계는 Math.max로 전진하고 collectionEntry 존재 확인으로 반복 지급을 막는다. grant는 소유 개체뿐 아니라 도감·보상·업적에 영향을 주므로 지급 제거 테스트는 이 부수 효과도 확인해야 한다. 단계 전진·seen 저장·재전송 안전성은 남겨야 한다.
- `NativeTutorialController.java:259`의 태초 보상 카드, `:330`의 태초 이름 기반 spotlight, '이름 옆 칩은 속성' 설명은 새 규칙에 맞게 변경 대상. 첫 전투/편성 단계 진행 조건도 함께 변경한다. 문구만 숨기는 것으로 서버 지급은 제거되지 않는다.
- `src/starter-loans.ts`는 여전히 15일 임시 2마리, 기존 10속성의 옛 ID 후보, 서로 다른 속성 선택을 요구한다. gameTutorial=9와 starterLoanEligibleAt 이후 지급하므로 D1 제거 후 첫 전투가 튜토리얼 안에 남으면 시작 개체 부재 가능성이 있다.
- 임시 스타터는 player 고유 receipt와 동일 선택 재사용/다른 선택 거부, `webClaimStarterLoans`의 한정 DB 오류 재시도로 중복 지급·기간 갱신을 막는다. 이 정책까지 폐기하지 않는다. 새 60종 후보·기술만 속성 규칙과 선택 조건을 어떻게 조화시킬지 결정 필요.
- 대체 캐릭터·재화는 미정이며 지급하지 않았다. 핵심 결정은 **첫 전투의 개체 확보 경로와 임시 2마리 선택/해금 시점**이다. 이것은 15일 정책 취소 승인과 다르다.

## 사용자 검토가 필요한 최소 결정

1. 새 UI 시안과 아트 수정 우선순위/사용 가능 범위, 정면·후면 발 기준점/상대 크기. u09/u28/u31 후면 우선 보완, u17 소형 가독성 등 manifest 결함은 미해결. 미니60%는 이미 확정.
2. 8속성 최종 ID, 기술/MP/적성/방어 프로필 수치 및 새 증강체 목록. 옛 자유자재는 개체속성 변경이므로 그대로 이식하지 않는다. 임의 대체 효과 금지.
3. D1 지급 없는 첫 전투의 시작 개체 경로, 15일 임시 스타터 2마리의 새 후보/선택 기준/해금 시점. 대체 보상은 미정.
4. 조합 누적 XP 평균의 최대 레벨/재료 선택 세부 규칙. 새 테스트 데이터 초기화 대상 allowlist와 실행 시점은 설계 후 별도 범위 확인. 옛 145종 영구 매핑은 더 이상 선결 과제가 아니다.

## 검증과 미실행

수행: Python 실제 실행으로 69PNG 해시/크기/alpha 및 120 활성 영역 검증, u17 override 확인, 원본 복사 해시 동일, 새 u01만 출력·옛 C1 없음, 방향 선택 계약, 60% 제약, output 덮어쓰기 거부, 잘못된 manifest 거부, git diff --check. u01 후면 원본을 도구로 시각 확인했지만 최종 디자인 승인을 대신하지 않는다.

미실행: Android 컴파일/Gradle/APK/AAB, 실제 브라우저·에뮬레이터·실기기 렌더, 롱프레스/겹침/메모리 검증, 런타임 정적 renderer 연결, 새 전투 규칙/튜토리얼 지급 제거 코드, DB 초기화, push/병합/배포. **게임 적용 완료가 아니다.**
