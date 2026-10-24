# Playable product integration: conditional estimate at 2cc9443

APK compilation is available now (recent incremental debug build ~24 seconds; allow 5–15 minutes for build, packaging checks and metadata). This is not the time to a playable new-system product. No promise of autonomous background completion is implied. Estimates below are engineering effort, not guaranteed wall-clock deadlines, and exclude waiting for approval, access or device feedback.

## Verified current boundary

- src/native.ts battle/start/action and BattleView.java still use legacy fights, attributes/augments and progression. New roster instance IDs/MP/replay are not substituted into them.
- CombatV2ReviewPanel/Contract can display six commands and six skills. OfflineEngine/Session/ReviewActivity and u01 front/rear are in src/debug; item/switch/escape are explicitly disconnected there. This is not an authenticated product combat transport.
- roster60 start/mail choice/craft/rebirth routes are authenticated and transactional, with Android entries. Approved runtime catalog is absent; draft workbook is approved:false. Selection screens therefore cannot display fabricated product values.
- verifyRosterTutorial is internal and has no new battle settlement caller. A client victory flag cannot unlock the reward.
- No durable new battle/session/action/settlement store, new roster party adapter, complete six-stat growth pipeline or new augmentation product catalog is connected.
- Art catalog/source-rectangle reader exists; current usable review APK bundles only u01 front/rear, not verified runtime front/rear for all60. Approved new icon PNG transfer remains blocked. No retry/bypass attempted.

## Dependency order and effort ranges

|Work|Effort|Completion evidence|
|---|---:|---|
|Resolve one bundled test-data proposal; generate/validate60 units,32 named skills,52 recipes and enemy fixtures|3–5h after decision|Versioned nonfinal balance catalog; complete fields, references, curves and source mapping|
|Durable1v1 state, owned-instance binding/locks, start/resume/action auth, revision, idempotency, replay/catalog versions|6–10h|Server restart resumes; foreign/stale/duplicate actions rejected or replayed; no client HP/MP/XP authority|
|Action engine, MP/full start/focus, skill6, item/switch/escape semantics, enemy actions, immunity/reflection|5–8h|Same contract tests across action transitions; deterministic replay|
|Atomic settlement, rewards/current-cycle XP, item consumption, tutorial proof/mail, release locks|4–7h|Concurrent finish/reconnect/rollback do not duplicate awards; tutorial end-to-end|
|Product Android battle transport/screen, six-command navigation/hold cancel, reconnect/MP sync|6–10h|Actual MainActivity product entry drives authenticated battle; lifecycle/response ordering tests|
|Roster party/inventory/detail integration; craft preview/result and rebirth state refresh; sourceRect art packaging|4–7h|Owned instance used consistently across screens; no legacy fallback; all60 input mappings checked|
|New augmentation initial catalog and hooks; legacy references/package cleanup|3–6h|Only new supported effects reachable; shared assets retained; real package-size diff|
|Cross-flow integration/regression/layout tests and corrective work|5–9h|Start u01 → battle → mail choice → party → battle → craft/rebirth; restarts/concurrency/error paths|
|Product debug APK final checks and delivery|0.5–1.5h|Package/version/signature/hash/resources checked; supported upload result confirmed|

Planning total: approximately **37–64 engineering hours**, use **40–70h** allowance for integration discoveries; roughly **5–9 working days at8h/day**, not a calendar guarantee. Do not add subtask minimums to claim a fixed release date. A narrowly scoped authenticated tutorial1v1 milestone (not all content, not the final product) is approximately **14–24h** after the catalog decision. Real-device validation needs additional available-device time (allow2–4h initial pass, defects may add work). Existing565 server tests do not establish new combat end-to-end coverage.

## One bundled TEST BALANCE proposal — NOT approved or runtime defaults

Preserve current confirmed rules: skill-only8 elements; defense1.5/1/.5/0/reflect once; fit+10%/point independent of MP; focus25%; six commands/skill slots;1v1; confirmed onboarding/XP-average/rebirth rules. The following are proposed only, intended to unlock a playable test dataset together rather than dozens of separate questions.

1. Import the existing60 names/grades, Lv20 HP/MP, defenses, learnsets and52 recipes as **test balance v0.1**, retaining u01 name override. Source proposes only five learned skills per species: capacity6 remains, do not fabricate a sixth. Keep proposed recipe thresholds/hidden flags visibly versioned.
2. Base XP requirements reuse existing round(20*level^1.5), extend through98→99. Same normal curve across species. Confirmed rebirth discount applies to requirements1→60 only. Proposed win XP=60*enemy level, loss XP=20*enemy level (old values, not approved new rewards). No reward in repeatable practice. Proposal at cap: reject/hold unsupported overflow actions until approved overflow policy; do not silently discard or bank. If the user wants a cap award clamp instead, that must be part of the acceptance.
3. Derive HP/MP from workbook Lv20 values using the existing4%-per-level shape normalized to Lv20: reference * (1+.04*(L-1))/1.76, round-half-up, minimum1. Store the unrounded ordinary baseline separately; apply confirmed rebirth additive multiplier once before final rounding. No per-grade HP multiplier. This is a proposed new growth curve, not an already approved value.
4. Missing physical/magic stats: propose Lv20 (patk,matk,pdef,mdef) by role: balanced(60,60,50,50), physical striker(80,40,60,45), caster(40,80,45,60), hybrid melee caster(65,65,50,50), high-HP tank(60,40,75,65). '기습' uses its physical/caster category; no new speed stat. Use the same normalized growth shape. These are complete test defaults only, not claimed workbook data.
5. Skill families: normal 주먹/강한주먹/맹렬한주먹/온힘의 주먹; fire 불씨/불꽃이빨/불길채찍/불아귀; water 물방울/물살찌르기/너울송곳/너울어금니; lightning 번갯불/번개쏘기/벼락송곳/우레엄니; ice 서리바늘/고드름/얼음쐐기/서릿발; nature 잎날/가시꿰기/덩굴아귀/숲의 꾸짖음; holy 빛살/빛살꿰기/햇무리/새벽날개; dark 그늘손/그늘발톱/어둠송곳/그늘낫. Proposed tier power35/60/95/140, MP6/12/20/30. A secondary learnset entry uses its named tier1 skill, not a separate power tier. Basic attack proposed separate normal power20/MP0. These costs and power values were previously suggestions and still need acceptance.
6. Proposed deterministic base damage=max(1,floor(power*offense/(defense+40))), then confirmed suitability and defense factors, floor final nonnegative result. Normal uses patk/pdef; seven elemental families use matk/mdef. No crit/miss variance in testv0.1. Reflection returns the calculated incoming pre-defense-reaction amount using original target defense, with no second defense/reflect pass or hit/lifesteal effects. These formula details are proposals.
7. Suitability workbook values are mostly+2 across grades and conflict with the confirmed higher-grade tendency. Proposal: leave negatives and nonprimary entries unchanged; primary highest-positive element gains grade-band bonus: common/uncommon0, rare/epic1, legend/hidden2, limited/transcend3, immortal/eternal4. Ties retain workbook learnset primary family. Requires explicit approval as test distribution.
8. Proposed6 initial augments, replacing rather than translating old ones: attack+10%, magic attack+10%, physical defense+10%, magic defense+10%, battle maxHP+10%, battle maxMP+10%. Pick one before combat from three server-seeded options; no stacking/creature attribute change/free extra turn. These are NOT approved augment names/effects or a full future catalog.
9. Proposed first playable scope for actions: item uses an owned recovery item with an explicit versioned recovery amount and atomic consumption (suggest25% maxHP or maxMP); swap changes active unit from a selected reserve list while retaining1v1 field (suggest party3, only one active; NOT3v3); escape forfeits PvE with no reward. All need acceptance together because reserve count, item balance and escape details are not currently confirmed.
10. Proposed turn/deadlock policy: player first in tutorial/PvE, alternating actions, one enemy action after focus/item/swap;200-action cap ends a draw with no victory reward. Neither speed order nor turn cap is approved. Reborn crafting materials remain disabled until inheritance policy is decided; do not average lifetime XP or silently remove rebirth eligibility.

Acceptance needed as ONE package: approve/adjust test-balancev0.1 (not final competitive balance), including the explicit provisional limitations above. Catalog remains approved:false until acceptance. A follow-up major balance pass is separate from making the product playable.

## External/access and deployment boundaries

- Latest approved new icon/VFX PNGs are not materialized due confirmed Library403; do not retry/bypass. A playable mechanics build can use clearly labeled existing vector placeholders only with that understood; an approved-art-complete build waits for supported transfer.
- All60 front/rear files need verified local availability/manifest coordinates and actual packaging, not just GitHub branch metadata. u01 review art does not prove all60 complete. No unauthorized network/auth retry.
- Local SDK/JDK/Gradle caches currently support debug compilation. No Android physical device/emulator evidence. New network dependency/download failure may add external delay.
- No production deployment or DB schema application is authorized. A product APK that uses the new server requires a matching private/dev server and authorized additive schema; APK alone cannot upgrade the production backend. Offline review is not a substitute for that product connection.
- Keep org.gmm debug signing/version conflicts explicit; do not ask users to uninstall an installed production app to force installation. Supported delivery must be reverified for final package size; past lightweight review delivery is not proof the full product upload will succeed.

## Independent implementation that can proceed before balance approval

Persisted battle/session/action/settlement schemas, owner/revision checks, durable idempotency and restart tests; authenticated transport/interface and response-order handling; server-owned party instance selection and locks; tutorial settlement hook wiring with injected reward/catalog policy. Numerical transitions must fail closed without approved catalog, and tests must use labeled fixtures. Do not equate these infrastructure steps with a playable complete APK.
