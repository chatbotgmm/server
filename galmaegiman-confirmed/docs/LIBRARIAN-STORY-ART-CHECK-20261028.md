# 사서 의뢰 전투 원화 경로 확인 (2026-10-28)

사용자는 실패한 곳을 “사서 서브스토리같은데”라고 설명했다. 정확한 의뢰·조우와 실패 화면의 APK 버전/해시는 아직 확인되지 않았다. 아래 결과를 설치 APK106의 정상 동작 증거로 확대하지 않는다.

## 실제 콘텐츠와 원화

현재 콘텐츠의 사서는 `librarian` 서온이며 의뢰는 `RESIDENT_LIBRARIAN_01` 「책 사이의 이름」이다. `STORY_15` 뒤 열리고, 두 조우 모두 원본 레벨 44의 흑화 모험전이다. 고유 빌런 5종의 임시 아트 alias와 다른 경로를 사용한다.

| 조우 | 서버 종족·변형 | APK 요청 경로 | 원본 SHA-256 |
|---|---|---|---|
| `RESIDENT_LIBRARIAN_01_01` | `u14`, `black` | `roster60-art/black/u14-front.png` | `c84e4feaaf770a3f2176b803be9ce300f3a1b621120d732d539a5997214f1670` |
| `RESIDENT_LIBRARIAN_01_02` | `u23`, `black` | `roster60-art/black/u23-front.png` | `f745ef06871b4191de402b6c4c07bd30f683ca9570778a167a09ae421ac64d38` |

원본은 각각 `art/gpt/blackened-gulls-20261028/u14-black-front.png`(1211×1299), `u23-black-front.png`(1282×1227)다. HEAD `085f204b`에서 직접 읽었으며 두 그림의 실제 픽셀도 확인했다. 흑화 8종 전체의 원본과 runtime 경로·해시도 대조했다.

`RosterStory.battleHook`은 이 조우의 서버 생성 적에게 `artVariant: black`을 넣는다. 공개 DTO는 이를 보존하며, 오래된 저장 전투도 서버의 story 문맥이 일치할 때만 변형을 복원한다. Android의 `Roster60BattleAppearance`는 이 변형과 정확한 8종 ID로 위 파일을 선택한다. 이름이나 기술 속성으로 그림을 추측하지 않는다.

## 별도 패키징 보강

기존 Gradle `Sync.from`은 원본 폴더가 비어 있어도 파일을 조용히 생략할 수 있었다. 이번 별도 후속 변경은 `prepareStoryBlackArt`에서 필요한 8종 원본의 존재·고정 SHA-256·PNG 치수를 검사한 뒤 기존과 같은 APK 경로에 원본 바이트를 복사한다. 누락·변경·중복·새 미지원 종족은 패키징 전에 실패한다. 이것은 설치 APK의 누락을 확인했다는 뜻이 아니다.

`data/story/black-art-v1.json`, `scripts/bundle-story-black-art.py`와 그 시험을 추가했다. 종족 ID·레벨·능력치·스토리 해금·보상·원화 준비 gate·알 수 없는 ID 처리·버전은 바꾸지 않았다.

## 재현과 검증 범위

- 격리 SQLite의 `test/story-librarian-art.integration.test.ts` 2개 시험이 통과했다. 원본 콘텐츠·카탈로그·전투 정책과 Lv44를 그대로 사용해 두 서버 저장 체크포인트에서 실제 `RosterStory.createBattle/read` 공개 DTO를 만들고, 명시적 black 변형과 옛 저장의 변형 복원을 확인했다. 이전 이야기 진행만 서버 소유 체크포인트 fixture로 준비했다. 전투 난도나 실제 사용자 저장을 검증한 시험은 아니다.
- 실제 생성 DTO 두 개와 출처 지문은 Android 시험 리소스 `org/dpdns/gmm/librarian-art/`에 보존했다. Android 시험에 임의 fighter 모양을 만들어 넣지 않았다.
- 실제 원본 8개로 번들 생성과 출력 SHA 대조가 통과했다. Python 번들 경계 시험 3개도 통과했다.
- `NativeLibrarianBattleArtGateTest` 2개 시험이 통과했다(실패·오류·스킵 0). 두 녹화 DTO의 실제 원화 디코드·gate 해제·로컬 재시도와 전체 흑화 8개 파일의 SHA·디코드를 검사했다. 이는 Android 단위시험 실행이며 APK·실기기 시험은 아니다.
- 설치 APK106 파일과 해시가 없어 그 APK의 자산 포함 여부·동일 실패 화면인지·실기기 동작은 미확인이다. 현재 소스의 경로가 맞는 것만으로 사용자의 실패가 해결됐다고 단정하지 않는다.
