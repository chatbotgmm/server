# 60-augment integration review

Read-only source review, 2026-10-20 UTC. Candidate source was not changed. No push, merge, build, deployment, APK execution, or external contact performed.

## Decision
Do not replace branch files with the baseline-pinned candidate. Port the candidate's augment-only changes onto an exact checkout of the agreed integration head, currently reviewed at `b7f9092a7da592f7b0b402333ef7f8277acc464e`. Reconfirm that branch is the intended target before any integration action. The source baseline `34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d` and reviewed head are diverged (GitHub reports 65 ahead / 18 behind; merge base `9787b7cfa190985c0e699bbb6403af7e6710f881`). This is not a two-commit fast-forward.

## Concrete conflicts
Exact remote copies of the 12 modified candidate paths were inspected. `git apply --check` against the remote snapshot fails on six substantively changed files:
- `src/battle/engine.ts`: remote adds `bonus=false` to rerollAugment and bypasses the once-per-slot guard for earned rerolls. Candidate changes the same guard and offers logic. Candidate's V2 `rerollOffer` has another independent once-per-slot guard. Both guards and the service entitlement/consumption transaction need explicit reconciliation; changing only the engine signature is insufficient.
- `src/native.ts`: candidate snapshot/creation/service edits overlap remote earned-reroll responses and consumption, reincarnation/owned-character identity, and current story access logic. The remote code retains previous-stage and nonempty-team requirements, removes promotion admission requirements, and changes enemy promotion stages and error text. These are observed remote changes, not evidence of user approval of story policy; confirm intended policy separately.
- `src/pvp/service.ts`: imports and battle/service paths overlap remote progression rewards, bonus-reroll consumption, owned-instance roster/lineup selection, and reincarnation handling. Keep all of those while introducing pinned battle rules and V1/V2 restore behavior.
- `src/pvp/realtime-native-view.ts`: candidate shield/augment DTO changes overlap remote `ownedCharacterId` display IDs, `combatDefinitionId`, and image fallback. Preserve both identity fields and current image behavior while retaining privacy filtering.
- `NativeAugmentView.java`: candidate tier/description additions overlap remote layout and earned-reroll button/state logic. Preserve remote entitlement countdown, input gating, button labeling and styling.
- `BattleView.java`: candidate shield/effect display overlaps remote replacement with `statusNames(f)` and current battle panel layout. Incorporate shields without reverting the layout/status improvements.

A seventh apply failure in `src/battle/rules.ts` is only an end-of-file blank-line context difference, not a semantic conflict. `realtime-adapter.ts` passes patch context but has a semantic integration requirement: preserve remote `bonus` argument forwarding. Patch applicability alone is not compatibility.

## Latest changes to retain
- `battle.yaml` is not edited by the candidate patch, but copying the whole old workspace would undo promotion multipliers `[1, 1.2, 1.4, 1.6]`. Exact reviewed remote SHA-256: `5db5c50aab3cda3599ba5b34a2ab739301ce09bb50d5ba7a8749dce6e632f72d`.
- Preserve latest web `duel.js` story unlock/advisory text changes even though augment patch hunks apply independently.
- Keep `battle-type-baseline.json` and `verify_theme_ci.py` together from the remote head. Asset hash lock is `8524ced649302dcd1806f8dd6eef60fb293834f981e948bf7039aa8c9e16c489`; source commit is `88dec3686f8796abee8cd4a269ba43f49352108a`. Candidate does not edit these files.
- Recruitment card-back changes/tests are outside the candidate patch and should remain intact.

## Handoff acceptance gates
After integration is separately authorized: verify current head, preserve all remote features above, check new-file collisions in the full repository, rerun augment tests against the new data/rules rather than cite old baseline results, add earned-reroll V1/V2 tests (entitlement, no consumption on failed/exhausted reroll, retry/idempotency, no timer extension), owned-character identity/privacy tests, story admission and enemy-stage tests, and baseline asset/hash checks. Then perform full TypeScript/Fastify/DB integration and Android visual/runtime verification when authorized. Source-only tests do not establish APK execution or balance under stronger enemy stats. The current candidate still defaults to V1 unless server options explicitly opt into V2; no automatic rollout should be implied.

Sources:
- https://github.com/chatbotgmm/server/compare/34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d...b7f9092a7da592f7b0b402333ef7f8277acc464e
- https://github.com/chatbotgmm/server/commit/88dec3686f8796abee8cd4a269ba43f49352108a
- https://github.com/chatbotgmm/server/commit/b7f9092a7da592f7b0b402333ef7f8277acc464e

Limitations: no integration applied, no full build/tests run. GitHub compare file list can be capped and is merge-base-relative; conflict findings above rely on exact file reads at the reviewed head and direct comparison with candidate pristine files, not completeness of that list.
