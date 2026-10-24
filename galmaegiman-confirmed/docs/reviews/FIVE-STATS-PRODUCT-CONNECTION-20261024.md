# Five-stat product boundary checkpoint

Confirmed fields: hp (체력), mp (마력, consumable MP), durability (내구력), statusResistance (상태이상 저항력), speed (속도). statusResistance is not the element reaction profile. No attack/magic-attack stat. Level-dependent common power scaling is required but formula is pending.

Actual connections in this checkpoint:
- Product `/my/api/native/roster60` catalog reader requires `statSchema: gmm-five-stats-v1`, exact five numeric fields in `stats` and `levelOneStats`, and matching baseline MP/growth. Old approved six-stat files cannot pass this reader. Catalog/schema is not approved by adding this marker.
- Actual ticket detail UI displays the five fields; missing/old payload is preparation state instead of showing old attack/defense labels. Existing authenticated selection/atomic grant path stays in place.
- Actual reincarnation preview rejects the five-stat catalog with `FIVE_STAT_REBIRTH_POLICY_PENDING` before cost lookup/projection. The old six-stat helper remains for archival regression tests and must not serve new product catalogs.
- Actual new-flow reincarnation UI labels are five-stat labels. Undefined bonuses are not populated.

No roster numeric data, defense formula, resistance formula, speed ordering, synergy, or five-stat bonus targets were invented. Existing level caps/XP/rebirth cost and count rules are unchanged. v0.1/v0.2 remain obsolete review drafts, not product catalogs.

Not connected yet: durable new battle session/action/reconnect/settlement, new five-stat battle calculations, actual new battle Android transport, home menu/mail relocation and account-persistent reincarnation introduction. Existing product battle remains legacy; this checkpoint is not a playable new battle release. New battle policy absence must fail explicitly when that route is implemented, not fall back to old engine or offline fixtures.

Validation: server TypeScript/web build; targeted pure and real Fastify route tests (auth then reject obsolete catalog before DB); bonus pending preview test; existing disposable database regression suite; Android compile and NativeRoster60 screen tests. No device/emulator run, no new APK delivery, no production DB/network request, no push/deploy/upload.

Verified results: npm build passed; disposable DB suite 571/571 across49 files; Android compile + NativeRoster60 UI regression tests6/6 passed (final run21s). Disposable DB: /tmp/galmaegiman-tests-E6MpOU/test.db. Logs: /tmp/five-stat-server-tests.log and /tmp/five-stat-android-final.log.
