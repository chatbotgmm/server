# 스토리 전투 임시 원화 연결 (2026-10-28)

기준은 `fix/game-polish-20261027`의 `085f204b13429889c631994961433a0a1f0ffb25`다. 신규 빌런의 정식 원화가 완성됐다는 보고가 아니다.

## 확인한 원인과 승인 범위

`src/story/villains.ts`는 전투 개체에 전용 `story_…` 종족 ID를 넣는다. `NativeRoster60Art.entry()`는 `roster60-art/index.json`의 `u01`~`u60` 60개 항목만 조회했으므로 아래 다섯 ID 모두 `IOException`으로 끝났다. 이전 코드는 이를 삼켜 이미지가 비어 있는 채 `assetsFailed()`에 도달했다. 같은 ID로 재시도해도 조회 결과가 바뀌지 않았다.

최신 Git tree와 `gpt-art@b4b19815e047b4a8189a084d44f78ae22530d7ed`의 관련 아트/스토리 메타데이터에서 이 다섯 캐릭터의 신규 승인 원화는 찾지 못했다. 관련 구현 문서에도 신규 빌런 최종 그림 미확정이 기록돼 있다. 최초에는 임의 대체를 보류했고, 이후 사용자가 다음 매핑을 보고받고 “그러면 이미지 먼저 쓰자”라고 승인한 범위만 임시 연결했다.

| 서버 종족 ID | 표시 이름 | 조우 | 승인된 임시 앞면 |
|---|---|---|---|
| `story_record` | 묵인 | `STORY_08_01` | `u01-front.webp` |
| `story_guide` | 돌비 | `STORY_09_01` | `u02-front.webp` |
| `story_engineer` | 철심 | `STORY_11_01` | `u22-front.webp` |
| `story_escort` | 닻날 | `STORY_18_01` | `u03-front.webp` |
| `story_dark_first` | 어둠의 태초 갈매미맨 · 첫깃의 변신 | `STORY_20_01` | `u31-front.webp` |

## 변경

`data/story/battle-art-v1.json`에 위 5종과 승인참조를 명시했다. `StoryBattleArt`는 정확히 이 5개 ID만 인식하고 `approved-temporary-alias` 상태와 원본 종족·앞면 파일의 일치를 검사한다. 전투의 `speciesId`, 이름, 능력치, 기술, 스토리 데이터는 변경하지 않는다. 다른 `story_…` ID나 첫깃 기본형에는 대체 그림을 제공하지 않는다.

`bundleGameArt`는 `prepareStoryBattleArt`를 통해 매핑의 완전성, 기존 로스터 인덱스의 앞면 메타데이터, 실제 번들 원본 파일 존재·WebP 헤더·고정 SHA-256을 먼저 검사한다. 원화 바이트를 편집하거나 복제하지 않고 기존 APK의 앞면 파일을 재사용하는 작은 별도 인덱스만 생성한다. 잘못된 매핑·누락·손상 파일은 생성물을 교체하기 전에 실패한다.

필수 원화 준비 gate는 유지했다. `NativeRoster60Art`의 인덱스·종족/방향 조회 실패와 `LocalGameArt`의 파일 읽기·디코드 실패에 안전한 진단을 추가했다. 로그에는 허용된 공개 자산 키와 예외 클래스 이름만 남긴다. 예외 메시지·스택·전투 상태·요청 본문은 기록하지 않는다. 알 수 없는 키는 `<invalid>`로 표시한다.

## 검증 범위

- 실제 저장소의 앞면 5개와 로스터 메타데이터로 번들 인덱스 생성이 통과했다. 원본 이미지는 수정하지 않았다.
- Python 번들 시험 3개가 통과했다. 5종 전부의 정확한 메타데이터 재사용, 잘못된/알 수 없는/중복/누락/미승인 매핑 및 해시 7종, 누락·손상·잘못 연결된 원본을 검사했다.
- 실제 `LocalArtDiagnostics`를 Java 컴파일러 모듈과 임시 Android Log 스텁으로 컴파일해 5개 자산 키, 민감 예외 메시지 제거, 위험 키 4종 거절, 디코드 실패 로그를 검사했다. 이는 Robolectric 실행과 별도다.
- `git diff --check`가 통과했다.
- `StoryBattleArtTest`, `LocalArtDiagnosticsTest`, `NativeRoster60BattleArtGateTest`에 5종 실제 디코드·상태 불변·로컬 재시도·기존 gate 해제와 unknown ID 차단 시험을 추가했다. 작성 시점의 Robolectric 실행 결과는 상위 작업의 환경 준비 후 별도 확인이 필요하다.
- APK·실기기 FPS·운영 배포 검증은 수행하지 않았다. 신규 빌런의 정식 원화는 여전히 미완료다.
