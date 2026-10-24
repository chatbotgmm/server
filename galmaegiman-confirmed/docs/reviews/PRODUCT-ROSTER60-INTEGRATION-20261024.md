## Confirmed per-cycle required levels (latest)

requiredReincarnationLevel(currentCount) is the single server rule: 0→60, 1→70, 2→80, 3→90, 4→99; count5 has no next rebirth. Eligibility configuration no longer accepts firstMinLevel/repeatMinLevel overrides. Preview includes requiredLevel and its fingerprint; confirmation recomputes the identical plan before any mutation. Android displays the returned requirement. The default product grade policy remains absent and execution returns REINCARNATION_GRADE_POLICY_REQUIRED; no all-grade assumption was added.

Transaction tests grow synthetic units through each actual confirmed threshold: 59/69/79/89/98 reject without consuming allowance or currency; 60/70/80/90/99 succeed, reset to Lv1/current-cycle XP0 and retain max99, followed by cap5 denial. Existing request receipt, duplicate/concurrent free usage, funds, fingerprint/ownership and rollback tests now use these real thresholds rather than synthetic level1 eligibility. Grade COMMON and arithmetic XP curve are still explicitly synthetic test inputs, not approved catalog data.

Validation: server build and full disposable-DB suite 565/565 (47 files) passed; Android new-flow 5/5, explicit server-required-level display test 1/1 and product debug build passed. Real device execution remains untested.

Only eligible grades/species remain undecided for enabling rebirth. Other independent blockers (approved base stats/catalog, new battle integration, craft inheritance and excess XP) remain unchanged. No production DB/schema changes, push or deployment.

## Account-first free rebirth and additive repeat policy (latest)

Confirmed product defaults: account's first SUCCESS is free; thereafter one peanut (ROSTER60_REBIRTH_PEANUT_COST, default1). Each owned instance may rebirth at most five times. Six original species level-one stats use 100% + 10% * resulting count, max150%, integer half-up. No compounding, no change to suitability/defense. Each success resets current-cycle XP0 and Lv1; cap99 and confirmed early required-XP70% persist. The functions are now reincarnationBaseStats / previewReincarnationCycle.

Roster60RebirthAccount.successes and Roster60RebirthReceipt are durable owner-scoped models; additive SQL is review-only. Transaction recomputes preview fingerprint (catalog, instance, XP, cycle, account success count, price, eligibility and projected values), uses account success CAS, conditionally charges Player.peanut, conditionally updates the exact owned instance, then records a receipt. Failures roll back all steps including initial account-state creation. Same request returns prior receipt, changed payload under same ID is rejected. Two distinct units with concurrent free previews cannot both be free: one success invalidates the other's price preview, requiring explicit re-preview; there is no silently increased charge. No external API/DB mutation was performed.

Production constructor deliberately has no eligibility policy. Authenticated confirm route is connected to the transaction service, but fails closed before mutation until approved allowed grades, first required level and repeat required level are supplied. Synthetic test eligibility is explicitly level1/common and is NOT a product default. Android actual reincarnation tab shows projected count, free/peanut price and before/after stats; execution stays disabled with the missing eligibility explanation. End-to-end Android confirmation/retry UI remains for the next approved policy step, not claimed complete.

Validation: fixed-source full disposable SQLite suite 564/564 (47 files), growth rule tests 7/7, server build, Android new-flow suite 5/5 followed by final changed-screen 1/1 and debug build passed. Initial suite had two stale-export failures while functions were being renamed; the final clean run passed. Concurrency, duplicate request, free-counter rollback, insufficient peanut, cap5, tampered preview, foreign owner and receipt-insertion failure are covered. No device test.

Still needed: eligible grades/species, first required level, repeat required level (60 or99 was asked upstream). Craft inheritance of rebirth and overflow XP remain separate pending decisions. Bonus scale, repeat maximum and current price no longer need decisions. Existing battle-state locking must be extended when the new durable battle runtime is connected; current transaction rejects recorded realtime activity and unavailable/locked instances.

## First reincarnation six-stat bonus and real preview tab

Confirmed: ordinary same-species level-one base HP, MP, patk, matk, pdef and mdef each receive +10% once. firstReincarnationStats computes absolute after-values from immutable baseline (integer round-half-up via BigInt), never increments an already bonused value. previewFirstReincarnation rejects reincarnation !=0, preserves suitability/defense by copy, and resets current-cycle totalXp to 0 / level1 / cap99. All six fields are mandatory; no attack/defense values are invented. The old battle/core growth contract already separates physical/magic attack/defense, but the workbook lacks those base values.

Actual native progression reincarnation tab for roster60 accounts now reads authenticated GET /my/api/native/roster60/reincarnation/preview. It displays same-species normal-level-one versus reborn-level-one six stats and Lv1/99, XP0. No old sprite/character substitution. The existing viewport/pagination remains. An approved catalog must provide explicit levelOneStats; Lv20 worksheet stats are not relabeled Lv1. Missing catalog/baseline data shows preparation state.

Execution is deliberately NOT enabled: POST reincarnation/confirm validates signed owner and strict request, then returns REINCARNATION_ELIGIBILITY_COST_POLICY_REQUIRED with no writes. Repeated calls cannot compound the bonus. Old progression reincarnate is blocked for enrolled new-flow accounts, so it cannot silently consume a legacy top-tier and return a random common. The existing old policy allows only top-tier, no level requirement, consumes source and randomly creates a common, adds 5% per cycle capped25%, with no extra currency charge. These restrictions/conversions conflict with same-species new roster and cannot be carried over as approval.

Validation for this step: server build passed; disposable full suite 558/558; final focused integration 18/18; growth rules 7/7; Android new-flow 5/5 and product debug build passed. No real device test. Preview compares ordinary level-one base versus reborn level-one base; current-level HP/MP/stat growth curves remain unapproved, so no fabricated current-level comparison is shown.

Minimum decisions before transactional execution: (1) eligible species/grades and required level, (2) any currency/material charge or explicitly free. Repeat rebirth remains blocked; craft inheritance and excess XP remain pending independently. After those decisions, implement owner/CAS reset + durable request receipt in one transaction, lock/battle checks and persisted preview fingerprint, then enable Android confirmation. Do not claim the current read-only preview is an executed reset.

## Reincarnation caps and cycle reset (2026-10-24)

Confirmed: ordinary owned instances cap at 60; server-owned reincarnation >0 unlocks 99. New roster schema adds reincarnation=0, additive manual SQL only. No client request accepts reincarnation or maxLevel. rosterMaxLevel is the common server source; growthAt/startMp and crafting options/result DTO use it; Android renders server maxLevel instead of guessing from grade. Existing legacy OwnedCharacter.reincarnation and progression.ts consume/create/reset behavior were inspected but not imported into the new model. Legacy runtime is still separate and not claimed upgraded.

New reset preview contract in battle/roster60-reincarnation.ts separates baseline level-one stats, permanent bonus stats, resulting level-one stats, and previous-cycle XP for audit. next.totalXp=0, next.level=1, next.maxLevel=99: old XP cannot restore the prior level. No runtime reset route is exposed: approved bonus policy/version is mandatory, at least one stat must improve and none may decrease. Repeat bonuses are supplied as a full policy result, not multiplied implicitly. Android before/after reset screen is NOT connected yet; the DTO is prepared for it.

Current-cycle cumulative XP is the crafting input; historical/lifetime XP is not. Reborn ingredients are rejected with CRAFT_REINCARNATION_POLICY_REQUIRED until eligibility inheritance is decided. XP above the exact configured cap threshold is rejected with CAP_XP_POLICY_REQUIRED, not banked, dropped, awarded or migrated. Short synthetic curves remain test inputs, not complete production growth tables.

Parent clarified the delayed hold was not a user cancellation: 70% is confirmed. confirmedReincarnationXpPolicy is now the product default: each next-level requirement for current levels 1..59 is 70%, ceil rounding; current levels 60..98 use the normal requirement. Normal instances use the unchanged base curve and cap at 60. No earned XP multiplier is applied. Normal crafting results use their own normal curve. An explicit policy parameter remains available for isolated test cases, without setting other balance defaults. Six-stat first bonus is now approved as described above; repeat policy and base XP/MP curves remain unapproved.

Confirmed-70% follow-up: server build and 7 rule cases plus 15 crafting/onboarding/reset integration cases passed. Android source did not change in this follow-up; no new APK was built.

Validation: server build passed; full disposable-DB suite 555/555 (46 files), focused craft/reset 10/10, Node rule tests 7/7, Android crafting/onboarding 4/4 and product debug build passed. The 60/99 UI test consumes server DTO values. Device execution and new reset screen are unverified/unconnected.

Pending minimum decisions: base stat inputs and repeat policy; crafting inheritance of reincarnation; cap overflow XP disposition. Schema/data deployment and actual reset execution remain unperformed.

## Product crafting UI connection (2026-10-24, local only)

The actual MainActivity mix/craftRecipes entry now selects NativeRoster60Craft for accounts whose server tutorial.flow is roster60-v1. The authenticated product routes are GET native/roster60/craft/options and POST craft/preview, craft/confirm, craft/receipt. No fixture or legacy character grant substitutes for missing approved data. New onboarding updates this flow immediately; webTutorial returns it on reload. Old tutorial overlays and starter-loan prompts are suppressed for the new flow.

The existing workshop background, NavyWorkbenchTheme font, 39:33:28 fixed viewport, tabs and MixCarousel are reused. Tap adds/removes up to six owned-instance IDs; changing selection/recipe discards the preview. The server supplies per-instance cumulative XP and level, expected result level, inherited total XP and fingerprint. Confirmation is disabled without a preview. Request UUID and exact payload are synchronously persisted before sending; double taps are blocked, unknown outcomes retry the same UUID, and re-entry queries the owner-scoped receipt. Navigation/session generations discard stale callbacks. Persistence failure sends nothing. This screen currently shows names/progress rather than new character portraits; art connection is not claimed complete.

The catalog is still absent/unapproved, so the deployed-equivalent product screen remains a truthful preparation/error state, with retry and no consumption. Options currently cap at 1,000 available/unlocked instances and explicitly report truncation. Pending recovery is scoped to the current signed session token hash and backend; re-login/token rotation does not recover the old local pending identifier (server receipt and exactly-once grant remain durable). No full inventory pagination or hidden-recipe discovery UI is claimed.

Battle settlement inspection: src/native.ts battle/action still uses legacy Fight/UnitRecord, legacy attribute/augment replay, webNativeCommit and old addXp; its snapshot contains HP but no roster60 MP. src/game.ts webPvpResult replays the legacy rules and is not a new 1v1 settlement adapter. verifyRosterTutorial remains an internal transaction hook with no new trusted settlement caller. Do not attach client victory/MP/XP fields or the offline fixture to this path. Required next unit: durable owner-scoped new battle state + catalog/rule version, atomic revision/action receipt, server-calculated HP/MP snapshot, then completion receipt/XP/tutorial proof in the same transaction. Battle rewards, damage/skill costs, growth and opponents require explicit approved configuration; the pending balance decision has not been replaced with test defaults. This turn does not claim new battle settlement or product MP synchronization connected.

Validation: npm run build passed; full disposable-DB suite 552/552 across 45 files passed; final targeted route/transaction tests 12/12 passed. Product :app:assembleDebug and selected Android regression tests 21/21 passed (craft 3, onboarding 1, starter loans 3, tutorial 14). 780×319 fixed-viewport check passed after using the existing render/settle helper; the initial test failed before the window was fully laid out, not a confirmed device defect. Request double tap, preview invalidation, stale callbacks, uncertain retry, back/re-entry, owner-scoped receipts and missing catalog were exercised. No production DB/schema execution, deployment, remote push or merge. Tests use disposable SQLite and synthetic Android API DTOs. Android layout checks are Robolectric, not device installation/render evidence.

# 제품 60종 통합 진행 상태

## 최신: 초안60종 복원 및 실제 조합 거래 경로

숫자 임시 채택에 대한 사용자 답변은 아직 기다린다. 부모가 보충한 u30~u49를 합쳐 `design/roster60-review/workbook-complete-proposal.json`을 만들었다. 전체60개 ID/14필드/8개 적합도·방어/300습득 항목/52레시피를 검증했다. 원본 방어 `null`은 보존했으며 모든 자료는 approved:false다. `scripts/validate-roster60-proposal.mjs`로 다시 검증할 수 있다. source-workbook 원본 바이트는 Drive 파일참조 다운로드의 NETWORK OSError로 이 환경에서 읽지 못했으며 재시도하지 않았다. 전체 JSON은 부모의 분석 메시지 병합본이다.

`src/roster60-craft.ts`와 실제 `/my/api/native/roster60/craft/preview`, `/craft/confirm`을 연결했다. signed-session/guard는 기존 제품 인증이다. 미승인/미설치 catalog는503으로 차단하며 fixture를 fallback으로 사용하지 않는다. 입력은 결과ID/실제 소유 개체ID 목록/catalogVersion, 확정은 추가 requestId/previewFingerprint다. 클라이언트 XP/레벨 주장은 받지 않는다.

서버는 개체별 누적XP와 재료별 레벨을 조회하고, 소유권/AVAILABLE/잠금/중복ID/레시피별 정확한 수량/최소레벨을 확인한다. preview는 미소비이며 동일한 계산을 confirm에서 다시 수행한다. XP·레시피·예상 결과가 달라지면 PREVIEW_CHANGED로 거부한다(실수로 동일 catalogVersion을 유지한 변경도 포함). 소비·결과 개체 생성·멱등 영수증은 하나의 Prisma transaction이다. 동일 요청은 원래 결과, 같은 requestId의 다른 입력은 거부한다. 소비한 재료 재사용/타계정 개체/잠금 개체는 거부한다. 결과 생성 실패는 소비와 영수증까지 롤백한다.

Roster60Owned에 status/locked를 추가하고 Roster60CraftReceipt는 Player와 cascade FK를 둔다. `prisma/manual/20261024_roster60_craft_additive.sql`은 ADD COLUMN/CREATE TABLE만 사용한다. Prisma 기본 diff의 테이블 재작성 대신 비파괴 추가 SQL로 정리했고 in-memory SQLite에서 기존123XP 개체가 보존되는 것을 확인했다. 실제 서버 schema/DB에는 적용하지 않았다.

검증: TypeScript/웹 빌드 성공, 전체 격리 SQLite550/550(45파일) 통과. 같은 버전 카탈로그 수정 회귀를 추가한 후 조합6+온보딩5 대상11/11 통과. Android 소스는 이번 단계 변경하지 않았으며 경량 APK c3022df와 제품 온보딩 후보8f6d1fe는 보존한다. **현재 조합의 Android 화면/새 전투 사용중 개체 예약/정산 연동은 미연결**이며 새 전투 전체 완료나 새 제품 APK 적용으로 표현하지 않는다.

## 후속 구현: 실제 인증/DB/Android 온보딩 연결

이 절은 아래 초기 조사보다 최신이다. `src/roster60-routes.ts`를 `src/my.ts`의 실제 signed-session/guard 경계에 등록했다. `/my/api/native/roster60/{state,start,complete,preview,confirm}`이며 요청에 playerId/완료증거를 넣을 수 없다. `src/roster60-onboarding.ts`는 Prisma의 실제 트랜잭션, 계정별 고유 시작/선택 영수증, 조건부 소비와 영구 지급을 사용한다. `prisma/schema.prisma`의 Roster60State/Roster60Owned는 옛 개체 ID를 덮어쓰지 않는다. 수동 검토 SQL은 `prisma/manual/20261024_roster60_onboarding_additive.sql`이며 실제 서버에는 실행하지 않았다.

튜토리얼 메뉴 MainActivity.tutorial은 새 서비스의 start를 호출하며, NativeNews에서 roster60 우편을 누르면 7종 미리보기→상세정보→최종 확인으로 이어진다. 취소는 confirm API를 호출하지 않는다. 새 UI는 기존 폰트와 남색 패널/버튼 자산을 재사용한다. 승인된 data/roster60-approved.json이 없으면 서버 preview/confirm은503으로 차단한다. UI에 스탯/기술/적합도/방어 및 시작MP를 표시하며 fixture는 넣지 않았다.

**현재 연결 한계:** Roster60Owned는 새 시스템의 영구 저장이지만 아직 기존 편성·BattleView·전투 엔진의 입력이 아니다. `verifyRosterTutorial`은 서버 정산 전용 함수로 만들었으나 새 전투 정산 호출자는 아직 없다. 클라이언트 complete는 서버 proof가 없으면409를 반환한다. 따라서 지금 제품 튜토리얼을 끝까지 플레이하거나 선택 보상을 정상 획득하는 전체 흐름은 미완료다. 새 흐름 시작 계정은 기존 D1 지급과 임시 스타터 요청이 거부된다. 아직 새 흐름을 시작하지 않은 구 계정의 옛 코드는 남아 있으며 전면 철거 완료라고 하지 않는다.

로컬 검증: npm ci(쓰기 가능한 /workspace/npm-cache 사용), Prisma client 생성, npm run build 통과. 전체 격리 DB 테스트544/544(44파일) 통과 후 동시 시작/서로 다른 선택 경합을 추가하여 대상5/5 통과. 기존 Android 튜토리얼/스타터17개 통과, 새 MainActivity 진입·취소·중복 제출 억제 테스트1개 통과. 후속 스타일 변경 후 같은 관련18개 테스트와 assembleDebug를 다시 실행해 BUILD SUCCESSFUL(27초). 제품 패키지 org.gmm, debug서명, 개발 서버 loopback 설정이며 새 전투가 완성된 APK가 아니다. 실제 운영 DB와 실제 휴대폰에는 접근하지 않았다.

워크북 자료는 `design/roster60-review/README.md`와 부분 구조 JSON/아트 CSV에 보존했다. 부모의 전체 메시지 중 u30~u49가 잘려 스탯 원본을 임의 복원하지 않았다. 60종 이름과52개 레시피 구조는 저장했지만 미승인 수치를 approved catalog로 승격하지 않았다.

전달 상태 추가: Codex 로컬 파일을 직접 다운로드 첨부로 등록하는 도구는 현재 목록에 없음. 사용자 승인 Drive 개발 폴더의 동일 파일명 중복 없음 확인 후 공식 upload_file을 한 번 호출했으나, **호출 전 전달 계층 HTTP413:220610389bytes >104857600bytes**로 거절. Drive 신규 파일/링크 없음. 이 거절 뒤 분할/우회/재시도 없음.

현재 기준은 review/roster60-static-20261024의 3de7670 이후 로컬 작업이다. 작업 시작 시 clean이었다. 이번 턴은 원격 최신 조회를 하지 않았다. 3de7670이 원격에 없다는 직전 확인을 유지한다. 운영 적용 완료가 아니다.

## APK 전달 재시도

사용자의 새 명시적 요청으로 공식 Library 대용량 helper를 현재 스킬에서 다시 받아 **한 번** 실행했다. tools/list 연결에서 URLError/OSError, HTTP403, exit1. prepare/upload/finalize 이전에 중단되어 신규 업로드는 시작되지 않았다. Library ID 없음. 프록시와 계정 권한 중 어느 계층이 거절했는지는 확정할 수 없다. 다른 경로나 GitHub로 우회하지 않았다.

보존 APK: /workspace/combat-v2-offline-apk-3de7670/galmaegiman-offline-vector-review-3de7670-debug.apk

220610389 bytes. 재확인 SHA256: 1ab03ed02c2fd029d54b483b2d7b11ed4df964475c4d21b24ca038a96768ef6d

이 APK는 새 PNG가 없는 벡터 대체 오프라인 검토판이다. 제품 통합 APK가 아니다. 이번 턴 재빌드하지 않았다.

## 실제 연결 대상

| 영역 | 제품 파일과 현재 차이 |
|---|---|
| 인증 및 전투 | src/native.ts의 route 인증/계정 직렬화, battle/start/current/turn, 완료 정산; src/battle/service-factory.ts와 engine.ts/core.ts/rules.ts. 현재 옛 10속성·개체 속성 기반. 새 엔진을 이 인증/정산 경계에 붙여야 함. review HTTP/오프라인 엔진은 제품 인증 경로가 아님. |
| 전투 버전 | src/battle/combat-epoch.ts, public-dto.ts, replay.ts 및 Android BattleReplayStore/Timeline/NativeBattleReplay. 새 epoch와 balance 버전별 리플레이 필요. 기존 세션을 새 규칙으로 해석하지 않음. |
| 성장/사용 가능 개체 | src/game.ts webBattleUnits와 편성 저장의 topIds 필터, src/battle/growth.ts UnitRecord 및 slotSkills. 커먼/언커먼 포함 새 정의로 교체 필요. |
| 조합 | src/game.ts chooseMaterials/craft/webCraftInfo/webCraft. 재료 상태 재검증→소비→grant는 같은 webRun 트랜잭션. 현재 XP 평균 없음. preview도 같은 선택 재료와 성장 계산을 써야 함. 중복 재료ID·동시 소비·결과 생성 실패 롤백 검사 필요. |
| 온보딩 | src/game.ts webTutorialSet: 단계2 통과 시 D1 지급, skip시 D1 편성, 임시 스타터 eligible 설정. src/my.ts tutorial 라우트. src/starter-loans.ts와 starter-loan-levels.ts. 새 u01 지급/우편 선택권 완료영수증으로 함께 전환해야 함. |
| 저장 | prisma/schema.prisma OwnedCharacter, StorySave, PlayerItem, ProgressionReceipt. 새 누적XP·티켓·완료/선택 영수증의 원자 저장 설계 필요. 실제 DB 변경 없음. 인증/결제 테이블 삭제 대상 아님. |
| Android | MainActivity.battle→BattleView, NativeTutorialController, NativeStarterLoans, NativeWorkbench 및 우편 화면. debug CombatV2ReviewActivity 단독 진입점으로 제품 통합을 대신하지 않음. |
| 패키징 | app/android/app/build.gradle. 제품 패키지는 명시적 gmmApplicationId=org.gmm. 기존 상위 자산은 아직 BattleView 의존이 있으므로 화면 전환 후 참조 분석과 함께 제외. 공유 효과는 보존. |

## 이번 구현

src/battle/roster60-rules.ts에 제품용 공통 계산/데이터 계약을 추가했다. review fixture를 import하지 않는다. 8기술속성, 6기술 한도, 개체 자체속성 거부, 완전한 적합도/방어 프로필 검증, 명시적 MP 성장표, 시작MP 충전, 집중25% 내림/상한, MP소비, 방어/적합도 곱셈과 반사 단일 타깃, 누적XP평균→레벨 계산을 포함한다.

반사 상세 계산은 필수 주입 정책으로 남겼으며 미지정 시 거부한다. 스탯/성장곡선/기술비용/반사 공식을 제품 기본값으로 만들어 넣지 않았다. XP 합은 BigInt로 계산해 큰 입력의 합산 오차를 막고 레벨 상한에서도 누적 XP를 보존한다. 집중은 정수 내림, 최대MP<4이면 회복0이다. 새 증강체 효과는 추가하지 않았다.

**아직 제품 라우트에서 이 모듈을 호출하지 않는다.** 이것은 제품 통합의 첫 소스 단계이지 사용자가 게임에서 새 기능을 쓸 수 있다는 뜻이 아니다. D1/임시 지급을 먼저 끊어 시작 개체가 없는 반쪽 흐름을 만들지 않았다. 후속 코드에서 새 시작 지급/우편과 함께 제거해야 한다.

검증: node --test test/roster60-rules.test.mjs, 5/5 통과. 사용한 성장/비용 숫자는 테스트 산술 fixture로만 존재한다. 실제 DB/HTTP/Android 제품 E2E, TypeScript 전체 타입 검사, 새 제품 APK 빌드는 이번 단계 미실행.

## 충돌 증강체

src/battle/augment-catalog-v2.ts의 reverse(자유자재), camo(보호색)는 자기 속성 변경으로 직접 충돌한다. dual은 기존 기본/역할 공격과 추가 속성에 의존한다. focus(집중 공략)는 약점1.9배로 고정1.5와 충돌하며 새 집중 행동과 명칭도 혼동된다. weakguard/neutral/counter는 새 무효·반사까지 포함할지 정의가 필요하다. 이는 전체 증강체 감사 완료 목록이 아니며 우선 확인한 충돌이다. 임의 대체 효과를 넣지 않는다.

## 최소 입력과 다음 단계

필요한 원본: 실제 60종의 등급/기술(최대6)/기술비용/스탯/MP성장/적합도/방어 프로필, 레벨별 누적XP 및 레시피. 현재 제품 data/units.json·battle.yaml은 구체계이고 검토 fixture는 제품 데이터가 아니다. 새 원본 위치를 요청했다. 새 증강체 목록, 반사 상세 정책, 아이템·교체·도망의 새 규칙도 미확정이다. 1대1은 각 순간 활동 개체1을 뜻하며 교체 가능한 예비 편성 수까지 임의 확정하지 않는다.

1. 원본 검증→제품 content 로더와 새 전투 엔진/서비스, 인증·revision·중복요청·서버 정산/리플레이 연결 (대략1~2일).
2. 새 시작 지급/튜토리얼/우편 선택권 및 조합 누적XP를 실제 트랜잭션에 연결, 격리 DB 경쟁/롤백 테스트 (대략1~2일).
3. MainActivity/BattleView의 새 제품 화면·6명령/6기술·MP·롱프레스와 도감/편성/우편/조합 연동, 아트manifest/패키징 (대략1~2일).
4. 서버/Android 회귀·작은 가로화면·제품 debug APK 검증 (대략1일, 실기기 별도).

이는 규모 추정이며 완료 약속이 아니다. 원본/미정 규칙/이미지 차단/회귀 결과에 따라 달라진다. PNG Library403 재시도 금지 유지. 제품 소스/실제 이미지/서버 준비가 빠진 APK를 완성본으로 전달하지 않는다. 운영배포·DB삭제·원격병합·Play게시·서명키 조회를 수행하지 않는다.
