# v3 product integration — partial, local only

Base: cdabf5300dc2e7b2a8f6eb1d4c0158dd1c1b9275. No deployment, push, production DB changes or signing changes.

## Connected paths
- MainActivity.team -> management formation: six selected slots, portrait tiles, focused member enlarged, horizontal owned-unit pages; save separated from PvP matchmaking.
- NativeRoster60Battle options -> encounter -> Roster60FormationView -> authenticated create/adventure start. Confirmation sends active first plus up to five reserves. Pending mutations resume rather than create a second battle.
- Existing battle field and textured right command rail retained. Switch/item choices replace commands in the same rail; current/KO switch targets disabled and HP/MP shown. Six-skill and hold/release interaction remains existing implementation.
- Craft large left/right cards use RegistrationV10Art with same-species portrait fallback. Small material slots retain portraits. Server preview now also returns remainingXp and xpToNext from the existing floor-average/result-curve calculation; material conditions shown.

## Verification
- npm run build: PASS.
- Isolated npm run test:db: 66 files, 687 tests PASS, 89.59 seconds. DB /tmp/galmaegiman-tests-J2K6FS/test.db; no production data accessed.
- Java syntax parsing: 11 changed/new source and test files PASS. This does not check types or Android compilation.
- Android compile: BLOCKED before compilation; Gradle 8.14.3 unavailable locally, services.gradle.org UnknownHostException. Android/Robolectric/render/small viewport checks NOT EXECUTED. Updated tests cover formation cap/confirmation, live/KO switch targets and existing product battle/PvP interactions.

## Visual references and limitations
Official Library prepare_materialize succeeded for the six supplied individual mockups and later v3 PDF/overview, but every official helper materialize attempt failed with `download failed` (no HTTP status reported). Each used an explicit consumer-local destination. No alternate URL or download route was used. No v3 pixels could be inspected; this is NOT a claim of visual matching or a finished APK.

Locally inspected existing registration-v10 u01 card and roster60 u01 front portrait. The existing registration card embeds the word 태초; this patch does not modify original artwork or claim to remove that label. Existing harbor/background/panel resources are reused; exact B reference is unverified. No new raster asset was generated.

Remaining: reference-pixel comparison, Android compile/render and narrow-screen QA; revive-target and ongoing-adventure selection still use existing dialogs. Formation options currently provide IDs/name, so no invented level/stat values are displayed. Party and craft touch/portrait layout needs actual device validation. Full v3 resource pack is not yet received.
