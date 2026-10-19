# 로그인 게임 화면의 네이티브 기능 복원 검토

이 문서는 배포된 69판을 기준으로 누락을 조사하고, 그 이후 작업 중인 네이티브 수정안의 코드와 웹 게임 원본을 대조한 기록이다. 검토 대상은 `public/duel/duel.js`와 로그인한 게임 안에서 여는 `public/my/my.js`의 GAMEUI(`/my?g=1`)다. 표시·배치·효과의 코드가 연결되어 있는지와 서버 응답의 일관성을 확인한다.

**시각 유사도 95%를 측정하거나 달성했다고 주장하지 않는다.** 아래의 ‘구현’은 코드에서 진입점과 처리를 확인했다는 뜻이다. 휴대폰·태블릿·Z플립 안전 영역의 원본 글꼴 캡처와 통합 검증 기록을 아래에 남긴다. 실제 기기 설치와 시각 유사도 측정은 수행하지 않았다.

## 범위와 유지할 동작

- 로그인·첫 화면·로비·모드·스토리·편성·성장·전투·경제·출석·계정·설정·튜토리얼의 게임 화면을 복원한다.
- 공개 사이트 `/dex`, `/hidden`, `/recipes`, `/event`, `/rank`, `/search`, `/p/:name`, `/talk`, `/world`의 별도 페이지·랭킹·커뮤니티 UI는 이번 범위에 포함하지 않는다. `native/catalog`는 가진 갈매미 상세에서 획득처·조합·재료 사용처를 연결하는 읽기 전용 메타데이터로 사용한다.
- 웹의 ‘모험·상위 갈매미 포획’은 준비 중이다. 네이티브에서도 준비 중으로 유지하며 새 게임 모드가 구현됐다고 표시하지 않는다.
- 웹 시험판의 미보유 33종 스토리 사용과 로그인 전 브라우저 로컬 저장은 복원 범위에서 제외한다. 스토리는 실제 보유 개체를 서버가 검증하며, 자유로운 캐릭터 선택은 연습 모드에서 제공한다.
- 캐릭터 33종의 앞·뒤 원본은 유지한다. 원본 방 레이어·아이콘·버튼 이미지와 재질·글꼴 체계를 재사용하며, 캐릭터 전체를 다시 제작하지 않는다.
- 기존 69판의 진영·편성 칸·개체 키를 사용하는 구조화 전투 표시 이벤트, 서버 HP 동기화, 중복·지연 응답 처리, 30초 증강체 선택 제한을 유지한다.
- 랭크의 시작 시 패배 선반영 규칙을 변경하지 않는다. 나가기 결과에서는 실제 점수·차감·승패를 표시하고 중복 요청으로 다시 차감하지 않는다.

## 69판에서 확인한 주요 차이

69판에서는 공격·피격 대상과 실제 HP 표시 정확도가 개선됐지만, 아래의 안내와 후속 행동이 웹 원본에 비해 부족했다.

| 묶음 | 69판 확인 내용 | 이번 수정의 대응 |
| --- | --- | --- |
| 전투 정보 | 상대 능력치·역할·속도 비교 창과 증강체 상세 재열기 없음 | `NativeBattleSheets`, 상대 정보·증강체 버튼, 서버 `role/base/matchup` |
| 기술 선택 | 합동기·긴 설명·로그가 공간을 차지하고 기술의 중요 정보가 분산 | `SkillCardView`, 짧은 한 줄 효과·실제 상성·사용 불가 이유, 상세 시트 |
| 성장 | 팀 칸의 HP바·새 기술·승진 강조와 정확한 학습 기회 안내 부족 | `NativeGrowthGuidance`, 학습/승진 메타·선택/교체/건너뛰기 확인 |
| 보상 뒤 이동 | 모든 결과에서 ‘계속’만 제공 | 모드별 재도전·한 판 더·편성·목록·성장 이동 |
| 로비 | 평균 레벨·실적·빨간 점·갈매미 터치 팁 부족 | `LobbyView`의 서버 상태 요약·동적 알림·방별 터치 팁 |
| 튜토리얼 | 정적 5장 안내만 제공 | 실제 화면 앵커 강조·첫 뽑기 보상·전투 상성/HP 안내·재보기·첫 진입 힌트 |
| 편성·스토리 | 순서 변경·합동기 안내·HP 자동 갱신·전체 적/보스 안내 부족 | 순서 드래그/길게 누르기·팀 불러오기·1분 갱신·조건/적 정보 |
| 경제 | 탐험 시너지·받기 시각 갱신·상세 획득처 연결 부족 | 실제 경제 설정 기반 시너지·진행/대기 갱신·획득 경로 |
| 연출·소리 | 효과음이 짧은 비프 중심이고 등급별 결과·조합 연출 차이가 작음 | 상황별 PCM 효과음·스토리/보스 BGM·등급 빛·카드 뒤집기·조합 성공/실패 |

## 현재 수정안의 기능 대응표

### 첫 화면·로비·안내·계정

| 웹 진입점·기능 | 네이티브 대응 | 현재 코드 상태 |
| --- | --- | --- |
| `titleScreen()` 로그인/게임 시작, 원본 레이어, 숨쉬기 | `TitleView`, `SceneMotion`, `DragParallax` | 구현. UI 고정·레이어 이동과 모션 종료 처리 유지 |
| 코드 로그인·카카오 로그인/처음 연결·해제 | `MainActivity.login/beginKakao/exchange`, `NativeExtras.account` | 기존 기능 유지. 서명 세션·전용 딥링크 사용 |
| `menu()` 5개 메뉴·재화·플레이·다음 이야기 | `LobbyView` | 구현. 원본 메뉴와 게임 상태에 따른 꼬리표 |
| 로비 평균 레벨·전체 실적 | `LobbyView` | 구현. 실제 팀/챕터 상태 계산 |
| `profMenu()` 계정·설정·PvP 요약 | `NativeExtras.profile` | 구현. 랭크 점수·티어·승패 조회, 닫기/외부 터치 처리 |
| 출석·받을 뽑기권·탐험 귀환·성장 빨간 점 | `LobbyView` | 구현. 서버 조회와 시간 갱신, 화면 이탈 시 콜백 정리 |
| `gullTip()` 원본 8종 팁 | `LobbyView` | 구현. 방별 캐릭터 터치 영역과 드래그 구분 |
| `todaySheet()` 출석 주차·연속일·7일 상태·내일 보상 | `NativeExtras.daily` | 구현. 받음/오늘/오늘 받음/잠김과 보상 요약 |
| 미션 진행·자동 지급·완료 수·한국 자정 초기화 | `NativeExtras.daily` | 구현. 서버 미션 상태 재사용 |
| `settingsSheet()` 음악/FX 볼륨·개별 음소거·전체 소리 | `NativeSettingsSheet`, `AudioControlView`, `NativeSound` | 구현. 음소거 상태와 저장 음량 분리, 해제 시 음량 복원 |
| 설정 쿠폰·튜토리얼 재보기·로그아웃·탈퇴 | `NativeSettingsSheet`, `NativeTutorialController` | 구현. 탈퇴 직접 입력과 재확인 유지 |
| 계정 카카오 연결 상태·개인정보·탈퇴 | `NativeExtras.account` | 구현. 서버/브라우저 인증 경로 재사용 |
| `tutPlan/tutTick()` 첫 출근 실제 화면 안내 | `NativeTutorialController/Overlay/State` | 구현. 출석→뽑기→첫 갈매미→편성 저장→첫 전투 흐름 |
| 첫 갈매미 보상·튜토리얼 건너뛰기 | `NativeTutorialController`, 기존 `tutorial` API | 구현. 서버 단계/지급 재사용, 재보기에서 지급하지 않음 |
| 상성·HP 카드와 화면 첫 진입 힌트 | `NativeTutorialController` | 구현. `seen` 저장과 재보기 분리 |

### 모드·편성·스토리·성장

| 웹 진입점·기능 | 네이티브 대응 | 현재 코드 상태 |
| --- | --- | --- |
| `modeScreen()` 캠페인·PvP·준비 중 모험·연습 | `NativeMenus.modes` | 구현 |
| 모드 내 전체 순위·승패 증감·편성 부족 안내 | `NativeMenus.modes`, `NativeExtras.pvp` | 구현. 내 전체 순위·미참여·승리/패배 증감·미편성 경고와 편성 이동 확인을 보완 |
| 일반/랭크 상대 소개(VS) | `NativeVersusView`, `MainActivity.startFight`, `matchInfo` | 구현. 상대 닉네임·티어·봇 안내·동일 레벨·랭크 경고 |
| `pvpTop()` 상위 20·내 행·점수/승패 | `NativeExtras.pvp` | 구현. 내 전체 순위·미참여 안내 포함 |
| `pickScreen('free')` 연습 팀·레벨·무작위·합동기 | `NativeExtras.practice/picker`, `NativeGrowthGuidance` | 구현. Native의 상대 팀/승진 단계 선택도 유지 |
| 연습 레벨별 학습 기술 | `src/native.ts` practice 기록 생성 | 구현. 원본처럼 학습 기회마다 후보 학습/대체를 적용하고 저장하지 않음 |
| `battle()` 단상·후보 선택·저장/비활성 | `NativeMenus.team`, 이미지 버튼 상태 | 구현. 체크·선봉/순서 배지·가로 스크롤 위치 보존 |
| 단상 한마디 순환 | `NativeMenus.startPodiumQuotes` | 구현. 원본 한마디/소개 재사용 |
| 스토리 전투 편성 불러오기 | `NativeMenus.team` | 구현. 종류를 현재 보유 개체 키로 매핑 |
| `typeTable()` 우리 팀·유리/불리·이유 | `NativeMenus.chart` | 구현. 팀 우선 표시·속성 선택·이유 안내 |
| `storyScreen/partList/partView()` 챕터·미리보기·잠금·실적 | `NativeMenus.story` | 구현. 원본 단계 조건·완료·전체 적/보스 정보 |
| 스토리 팀 HP·XP·성장 배지·합동기 | `NativeGrowthGuidance.storyUnit/combos` | 구현. 체력/경험치 바, 새 기술·전용 기술·승진 표시 |
| `dragOrder()` 출전 순서 변경 | `NativeTeamReorder`, `NativeMenus.reorderStory` | 구현. 드래그와 길게 누르기 선택 모두 제공 |
| HP 1분 자동 갱신·치료약 진입 | `NativeMenus.story`, `NativeLibrary.healSheet` | 구현. 안전한 화면 콜백과 서버 상태 재조회 |
| 스토리 처음부터 | `NativeMenus.resetStory`, `POST native/story/reset` | 구현. 확인 문구·재확인·서버 `RESET` 입력 검사·전투 중 금지 |
| `talk()` 타이핑·화자·다음/건너뛰기 | `StoryView` | 구현. 첫 터치 문장 완성, 다음 터치 진행 |
| 장면 시작 카드·인용 강조·TIP/NPC·빌런 틴트 | `StoryView`, `sceneActors` | 구현. HALL과 원본 BODY·tint 메타 포함 |
| `skillSheet()` 4칸·학습 후보·잊을 칸·남은 기회 | `NativeLibrary.unit/skillChoice/commitLearning`, `NativeGrowthGuidance` | 구현. 선택과 확인 분리, 원본 슬롯 교체 |
| 학습 건너뛰기 영구 소비 경고 | `NativeLibrary.skillChoice` | 구현. 남은 기회가 사라지는 것 재확인 |
| 역할·시너지·정확한 다음 승진/다음 학습 Lv | `NativeLibrary.unit`, `growthInfo` | 구현. `promotionNeed`, 학습 기회 메타 사용 |
| `promoSheet()` 표창 3개·승진·전용 기술 | `NativeLibrary`, 성장 시트 | 구현. 서버 고정 후보·승진 연출·전용 기술 안내 |
| 기술 상세 재사용 정책 | `NativeLibrary.skill`, `NativeBattleSheets.skill` | 구현. 현재 게임 규칙에 따라 공격 연속 사용·효과 적용 중 제한·합동기 전투당 1회를 안내. 사용자가 제거한 공격 후 대기 시간을 되살리지 않음 |

### 전투·보상·연출

| 웹 진입점·기능 | 네이티브 대응 | 현재 코드 상태 |
| --- | --- | --- |
| `actionPanel()` 공격·아이템·교체·도망 | `BattleView` | 구현. 큰 명령 제목만 유지하고 사용 조건 처리 |
| 4기술+합동기·실제 상성·적용 중 사용 방지 | `SkillCardView`, `BattleView.skills`, 서버 `preview/reason/legal` | 구현. 설명 압축·상세 열기·사용 불가 사유 |
| 공격자·피격자·HP·반사/회복/다단/교체 정확도 | `BattlePlayback`, `BattleEventCursor`, 서버 `displayEvents` | 69판의 구조화 이벤트 유지 |
| 상대 정보(1턴 이후) | `NativeBattleSheets.foe` | 구현. 실제 레벨 반영 `base`, 역할·속도 비교·기술·증강체 |
| 증강체 효과 다시 보기 | `NativeBattleSheets.augments` | 구현. 내/상대 증강체 이름·분류·효과 |
| 증강체 3개·추천·개별 재뽑기·출전 선택 복귀 | `NativeAugmentView` | 구현. 원본 분류/추천과 현재 상태 비교 |
| 30초 뒤 현재 제안 중 랜덤 | `src/native.ts`, `NativeAugmentView` | 기존 사용자 요청 기능 유지. 재추첨/재접속으로 시간 연장 안 함 |
| 증강체/교체 후보의 실제 상성 | 서버 `matchup`, `NativeBattleSheets`, `NativeAugmentView` | 구현. RNG를 변경하지 않고 실제 엔진 미리보기 사용 |
| 전투 기록 전체 확인 | `NativeBattleSheets.history`, 서버 `history` | 구현. 웹 원본에는 별도 전체 기록 창이 없으나 기존 로그 확인 기능을 확장 |
| 이전/현재 기록·나/상대 시각 강조 | `BattlePlayback.showThrough`, `BattleView.log` | 구현. `NativeBattleLog`로 최근 2행을 구분하고 현재 행 강조·이전 행 흐림·진영 태그를 적용한다. 이름 추론 없이 명시적 prefix 및 구조화 이벤트 참조만 사용한다. 피해·회복·강화 수치와 상태이상은 색과 굵기로 강조하고 전체 기록 창에도 같은 규칙을 적용한다 |
| 교체 후보 이미지·HP·역할·공격/방어 상성 | `BattleView.switches`, `NativeBattleSheets` | 구현 |
| 아이템 효과·수량·사용 제한·불가 이유·부활 대상 | `BattleView.items`, `NativeBattleSheets.itemEffect`, 서버 `itemLimit` | 구현. 실제 `perBattle`와 서버 원자적 소비 유지 |
| 도망 경험치 없음·HP 유지·랭크 점수 안내 | `BattleView`, `MainActivity.showBattleResult`, 서버 `result.left` | 구현. 재도전/돌아가기와 성장/체력 요약. 스토리 체력 안내를 스토리 모드에만 표시 |
| 승패·XP·레벨업 HP 완전 회복 | `BattleRewardsView`, 서버 성장 보상 | 구현. 서버 확정된 성장만 표시 |
| 성장 가능 강조·개체 기술/승진 바로가기 | `BattleRewardsView`, `MainActivity.showBattleResult` | 구현. `canLearn/canPromote/canEvolve`와 후보 배열 사용 |
| 스토리 실패 재도전·PvP 한 판 더·연습 편성 | `MainActivity.showBattleResult/finishBattle/retryStory` | 구현. 모드별 버튼·체력 0 재도전 안내 |
| 첫 보스 클리어 후 장 완료 대사 | 서버 `chapterClear`, `MainActivity.finishBattle` | 구현. 최초 클리어 때만 표시하여 재도전 중 반복 상영 방지 |
| 돌진·피격·회복·기절·교체·합동기 | `BattlePlayback`, `BattleView.Scene` | 주요 연출 구현. 웹의 모든 기술별 세부 입자/특수 glyph 타이밍까지 동일하다고 주장하지 않음 |
| `sound.js` 상황별 12종 효과음 | `GameEffectPcm`, `NativeSound`, `BattlePlayback` | 구현. tap/hit/crit/big/weak/faint/heal/aug/combo/win/lose/levelup |
| title/story/battle/boss 음악과 복귀·오디오 포커스 | `NativeSound`, `MainActivity.battleMusic/onStart` | 구현. 로컬 WAV와 효과음 버스, 음량·모드 구분 |

### 수집·조합·뽑기·탐험·상점

| 웹 진입점·기능 | 네이티브 대응 | 현재 코드 상태 |
| --- | --- | --- |
| `units()` 가진 목록·이름/등급/속성 검색·필터 | `NativeLibrary.collection` | 구현. 이번 범위에서는 가진 목록을 유지 |
| `unitDetail/unitMenu()` 큰 그림·소개·이전/다음·길게 메뉴 | `NativeLibrary.detail/hold` | 구현. 탐험 중·보유·보호·전투 출전 정보 |
| 보호/해제·조합/탐험으로 보내기 | `NativeLibrary.hold`, 기존 API | 구현. 사용 가능 개체와 탐험 상태 검사 |
| 상세 획득처·만드는 법·재료 사용처 | `NativeLibrary.acquisition`, `native/catalog` | 구현. 기존 조합/획득 정보로 게임 화면 연결. 공개 사이트 별도 도감은 만들지 않음 |
| 미발견 히든·최상위 비공개 | `native/catalog` | 구현. ???/unknown·숨은 재료 비공개·최상위 소개 숨김·과거 발견 기록 보존 |
| `drawTab()` 3등급·수량/최대·확률·천장·이벤트 | `NativeEconomy.draw/drawGuide` | 구현. 서버 확률/천장·관리자 무제한 상태 재사용 |
| 뽑기권 지급량·주기·대기 시각 갱신·받기 | `NativeEconomy.draw/tick/claimTickets` | 구현. 열린 화면에서도 준비 시각에 버튼 상태 갱신 |
| 지난 뽑기 결과 다시 보기 | `NativeEconomy.reveal`, `NativeReveal` | 구현. 서버 결과를 재생하며 재지급하지 않음 |
| 알 흔들기·등급 빛·전설 광선·카드 뒤집기 | `NativeReveal` | 구현. NEW·중복 마릿수와 화면 이탈 시 모션 정리 |
| `craftGame()` 검색·가능만·재료 수·수량·확인 | `NativeEconomy.recipes/recipe` | 구현. 처음 발견·보호/탐험·재료 부족 안내 |
| 재료 섞기 2~6개·빼기·비우기·실패 보존 | `NativeEconomy.mix`, 기존 `mix` API | 구현. 실패 시 재료 보존 규칙 재사용 |
| 재료 획득처 안내·조합 후 결과 연출 | `NativeEconomy.materialGuide`, `NativeForge` | 구현. 성공/실패·등급·결과 카드·음향 |
| `party()` 동일종 복수·저장·최근 편성 복구 | `NativeEconomy.expedition` | 구현. 실제 최대 인원 설정·보상 뒤 이전 편성 유지 |
| 탐험 시너지별 마릿수·보너스 안내·예상 결과 | `EconomyPreview`, `NativeEconomy.synergyGuide/showExpeditionEstimate` | 구현. 서버 경제 설정 재사용. 확률 보상은 예상임을 표시 |
| 진행 시간·귀환·스킵·길 위 탐험대 이동 | `NativeEconomy.showTrip/tick` | 구현. 귀환 시각에 화면 상태 갱신 |
| `shop()` 새우/땅콩 뽑기권·아이템·수량·총액·한도 | `NativeEconomy.shop` | 구현. 서버 실제 보유/가격/최대 적재량 사용 |
| 뽑기권·탐험 로컬 알림 | `NativeAlerts` | 기존 사용자 요청 기능 유지. 공개 웹 서버 푸시 기능을 새로 만들지 않음 |

## 디자인·배치의 공통 대응

`GamePaperDrawable`, `GamePanelDrawable`, `GameButtonDrawable`, `GameSkillDrawable`와 원본 이미지 버튼을 공유해 크림색 종이·따뜻한 나무·메뉴 파스텔·속성 강조를 통일한다. 제목/버튼/이름과 본문 글꼴의 역할을 유지하고, 눌림·선택·비활성 상태는 `GamePressState` 및 버튼 상태에서 표현한다.

배치는 안전 영역 안의 UI와 화면 끝까지 이어지는 배경을 분리한다. `GameRootLayout`, `GameSafeArea`의 Z플립 컷아웃/둥근 모서리 대응을 유지하며, 메뉴 밀도는 `MainActivity.compact()` 기준과 각 화면의 고정 안내/스크롤 영역으로 조절한다. 배경 블러는 같은 원본에 대한 캐시를 사용한다. 이 변경에 새로운 배경 전체 제작이나 캐릭터 원본 수정이 완료됐다는 주장은 포함하지 않는다.

## 검증 기록과 남은 확인

### 현재 완료한 서버 검증

- `npx tsc -p tsconfig.json --noEmit`: API 통합 편집 후 통과.
- 새 `test/native-parity.test.ts` 7개와 기존 네이티브 관련 통합 테스트 8개, 총 15개 통과. 해당 명령에서 선택 범위 밖 63개는 실행하지 않았다.
- 테스트는 새 `/tmp/galmaegiman-tests-…/test.db`를 생성해 수행했다. 실제 서비스 DB의 초기화·재화·계정·소유 개체는 변경하지 않았다.
- 검증 내용: 실제 경제/성장 조건, 과거 발견/현재 보유 구분과 숨김 정보, 초기화 확인·전투 중 거절·재화 보존, 연습 학습, reverse/neutral 실제 상성과 조회 불변, PvP 나가기 선반영 점수와 중복 처리.
- 로그: `/workspace/build-tools/native-parity-server-tests.log`.

### 캡처에서 찾아 고친 부분

- 폰 전투 편성 후보가 282dp인데 뷰포트가 161dp여서 하단이 잘렸다. 전환 직후 이전 태블릿 높이를 읽던 공통 높이 판정을 새 화면 제약과 안전 영역의 작은 값으로 고쳤고, 폰 후보 이미지와 중복 메타를 압축했다. 전체 카드 표시 검증은 유지했다.
- 스토리 팀의 성장 글자를 최소 12sp로 키우고 편성·치료약을 헤더로 옮겨 바로 사용할 수 있게 했다.
- 다단 타격의 합계 기록이 첫 피격 프레임보다 먼저 나오던 표시를 보완했다. 같은 기록에 연결된 마지막 이벤트가 재생된 뒤 합계를 표시하고, 연속 공격의 합계임을 명시한다. 서버 로그·피해·HP 계산은 변경하지 않았다.
- 전투는 제목·기술명과 수치·메타·본문의 글꼴 역할을 분리했다. 피해는 붉은색, 회복은 초록색, 강화는 금갈색, 일반 수치는 청색이며 상태이상도 각각 강조한다. 시스템 글자 크기가 커지면 기술 카드 높이를 늘려 설명 겹침을 방지한다.
- 기술의 속성·위력이 본문에도 반복되던 부분을 제거했다. 기술명/칩/효과/48dp 상세 버튼을 분리하고 피해 감소 수치가 한 줄 생략으로 사라지지 않도록 효과는 최대 2줄로 표시한다.
- 첫 전투 안내가 명령판 컨테이너를 가리켜 공격을 실행하지 못하던 연결을 실제 공격 버튼으로 고쳤다.
- VS 화면은 각 창의 타이머가 자기 창만 닫도록 변경해 이전 세션의 창이 남거나 새 창을 닫지 않게 했다.
- 대사는 부착 전 예약에 의존하지 않고 화면 부착 시 첫 타이핑을 예약한다. 제목 닫기·타이핑 완성·다음 줄·화면 이탈 시 중지 검증을 유지한다.
- 검토 환경의 Choreographer가 Vsync마다 가상 시계를 강제로 진행하던 설정을 고쳤다. 프레임을 16ms 단위로 진행해 타이핑과 연출 중간 상태 검증을 안정화하고, 실제 시험 전투는 종료하여 다음 시험을 막지 않게 했다.
- 예전 계정의 지연된 401 응답이 새 로그인 세션을 지우지 않도록 요청 시점의 세션을 사용한다.

### 완료된 검증과 한계

- `npm run build`와 격리 DB `npm run test:db`: **153개 모두 통과**. 서버 보상·성장·탐험·로그인과 새 네이티브 API 통합을 검증한다.
- 웹 가로 915×412·1280×720: 실제 게임 글꼴 40개 캡처, 넘침·버튼 잘림·상태창/불투명 캐릭터 겹침 검사 통과. 필수 첫 화면 로그인 전/후·로비·전투의 두 크기를 직접 확인했다.
- Android 전투·성장·경제·효과음·튜토리얼·재생·안전 영역 검증 **66개 통과**, 별도 Release 기본 검증 **16개 통과**(중복 6개 제외 총 76개). 최신 XML과 지표는 검토 폴더에 보관한다.
- 네이티브 캡처는 Android SDK 35의 Native Skia/Robolectric에서 실제 Java View와 APK 원본 이미지를 렌더했다. HTML로 네이티브 화면을 대신 그리지 않았다.
- 원본 비교/네이티브 캡처/지표/회귀 검증 XML: `/workspace/review-artifacts/pr-2/parity70/`.
- 실제 Z플립·갤탭에서 설치·터치·키보드·오디오를 측정하지 않았다. 웹의 모든 기술별 특수 입자/glyph와 연출 타이밍의 95% 일치를 측정했다고 주장하지 않는다.
- 공개 웹 UI·게시판·댓글은 추가하지 않았다. 33종 앞뒤 캐릭터와 기존 방/아이콘/버튼 원본을 유지했고, 새 배경 전체 제작은 진행하지 않았다.
- APK 버전은 **2.0.7-native(70)**, 기존 개선판과 같은 패키지·서명으로 업데이트한다. 최종 빌드·서명·SHA256·EC2 배포 확인은 발행 기록으로 남긴다.

### 최종 Release 발행 검증

- `2.0.7-native(70)` Release 빌드와 lint/signing 통과.
- 패키지 `org.dpdns.gmm.native.revised`, 인증서 SHA256 `097fb35527efeb6ab86b5d52908a634ad8cf0e556b24f5f2de918b91a4754897`로 기존 개선판과 동일 서명.
- APK SHA256: `4e4b5ce53483ddf47ce4e75e0725870d5dcd297ae6cbfe1a236133a0e2600b4c`, 50,836,446바이트.
- 기존 66판과 공통인 원본 자산 392개를 비교해 변경 0개. 추가된 자산은 스토리 음원과 빌드 최적화 프로필이며, 캐릭터·원본 이미지·글꼴은 유지했다.
- 실제 Production 서버 URL 포함, 리뷰 서버 및 Robolectric 테스트 코드 미포함, debuggable 미설정 확인.
- 다운로드 주소: https://gmm.dpdns.org/app/native . EC2 발행 확인은 배포 검증 기록에 별도로 보관한다.
