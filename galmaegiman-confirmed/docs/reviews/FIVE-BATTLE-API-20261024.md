# Authenticated five-stat battle persistence checkpoint

Actual product registration: src/roster60-routes.ts under /my/api/native/roster60/battle. Same existing authentication/guard/rate-limit machinery, no public fixture API. New tables are additive; migration SQL is review-only for production. No deployment performed.

## Client/Android contract

Use NativeApi with paths `native/roster60/battle/...`; do not route through CombatV2ReviewContract or claim its review epoch is product protocol.

- POST create `{requestId,ownedId,encounterId}`. Encounter is selected from server configuration; clients cannot supply enemy stats, rewards, seed or speed. Unit must belong to authenticated account and be AVAILABLE/unlocked. It becomes BATTLE in the same transaction as creation/receipt.
- POST read `{battleId}`. Returns persisted view after reconnect/app restart. Foreign battle IDs return404.
- GET active. Lists current account ACTIVE/FINISHED sessions (latest20); individual read remains available.
- POST action `{requestId,battleId,revision,action}`; action is exactly `{kind:"attack"}`, `{kind:"focus"}` or `{kind:"skill",skillId}`. At most six equipped skills. No client HP/MP or RNG fields. One request resolves one action per living side in stored speed order, then increments revision. Item/switch/escape are explicitly disabled with reason codes.
- POST settle `{requestId,battleId,revision}`. FINISHED only. Stored reward/XP curve is used; reward, unit unlock, battle status, account revision, optional tutorial proof and receipt are atomic. A settled battle returns its recorded settlement without awarding again even under a different request ID.

Response: `{battleId,revision,status,state,settlement,disabledCommands}`.
`state.version="five-battle-v1"`, `turn`, `fighters[0=self,1=enemy]` with id/speciesId/name/level/stats{hp,mp,durability,statusResistance,speed}, current hp/mp, attack, skills, suitability, defense; `decision` holds current ordered fighter IDs/speeds/turn; `events` holds completed turn decisions and results; outcome null/win/loss/draw. Status ACTIVE/FINISHED/SETTLED. Seed and immutable full policy snapshot stay server-side. DTO is distinct from offline review protocol.

Android must persist a pending request ID + exact payload before sending a mutation, resend the same request after ambiguous network failure, and replace display HP/MP from the returned server snapshot. Same request ID with different operation/payload gives REQUEST_ID_REUSED; stale revision gives STALE_REVISION with no MP spend. Request retries return the original response even if later state progressed; follow with read to get latest. No actual product battle screen wiring is claimed in this checkpoint.

## Server enablement gates

`data/roster60-approved.json` must pass existing five-stat catalog validation. Each selected species requires exact five-stat `combatGrowth[level-1]`, MP matching XP growth, and explicit finite power on attack/skills. Legacy effects/priority are rejected. Reborn units are gated until five-stat rebirth policy is approved.

`data/roster60-battle-policy.json` is absent in product checkout. Creation returns BATTLE_POLICY_PENDING unless a server-owned approved policy explicitly supplies version/catalogVersion/calculation model+coefficients/reflection strategy/enemy strategy/maxRounds/encounters. Test policies are written only into temporary test directories. No candidate numeric balance is installed in data/.

Current supported configured strategy families: reflection return-post-durability; enemy first-affordable; configured turn limit draw/no XP. These are selectable implementation candidates, not new user approvals. Unknown policies are rejected. State effects and future synergy/priority data are not activated; speed modifier list is currently empty.

Catalog and policy are pinned at creation; existing sessions/retries/reconnect/settlement do not depend on subsequent file changes. Server creates random32-byte seed and first turn decision atomically; action uses stored decision and saves the next decision with state and receipt. No reroll on request retry.

XP overflow retains prior explicit CAP_XP_POLICY_REQUIRED rejection: transaction rolls back and battle stays FINISHED/locked, available for later settlement after policy resolution. No silent deletion/banking or double reward. Session cancellation/escape and cleanup policy remain pending; do not enable content requiring those paths before it is defined.

## Remaining integration

Actual authenticated durable API exists and is tested through Fastify+isolated Prisma DB. Product Android rendering/transport caller still needs wiring; no new playable APK delivered. Home mailbox/rebirth menu work is unchanged. This is not full game integration completion. Production migration/deployment require separate scope approval.

## Environment recovery and regression diagnosis

Recovered the same /workspace/server-roster60 checkout at1017d50 with all six pending battle paths intact; no new checkout, overwrite or production DB access. The previous casual-AI failure at test line81 was independently reproduced with a forced `reverse` offer (the existing freestyle/own-attribute augment). Its extra `choose` phase is legitimate; the test incorrectly demanded immediate `action`. Test now covers both `focus` and `reverse`, checks the specific extra phase and preserves bot commitment/retry assertions. No old engine runtime rule was changed.

A targeted rerun also exposed the pre-existing potion escrow fixture's dependence on a randomly selected lonecharge heal-lock. Its wounded helper now clears only test fighter augment state so the potion is valid before testing transaction refund behavior. This does not remove heal-lock behavior from the product.

Targeted suite28/28 passed; TypeScript/web build passed. New battle source hash at recovery: d8cd423fc46962f41eef4c4ae2a3f382605b10d076de1917563189798a4afe0d. Migration hash: cbd03680ebb69cf894dffe403433fb236cee5404ed828a509ef1319404c05a88. Raw reproduction log /tmp/battle-recovery-reproduction.log; targeted log /tmp/battle-recovery-targeted-final.log.

Final recovered run: 51 files / 581 tests passed in74.36s. Isolated DB /tmp/galmaegiman-tests-McRXP6/test.db; log /tmp/battle-recovery-full.log. Final build log /tmp/battle-recovery-build-final.log (passed). No production database, remote publish or Android device execution in this recovery task.
