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
- Android compile: initial DNS blockage resolved using the existing proxy as command-local Java properties and ANDROID_USER_HOME in the writable workspace. org.gmm compileDebugJavaWithJavac PASS (43 seconds). Test-source Java compilation also PASS. No APK/signing/deployment. Robolectric follow-up results below; reference-pixel comparison remains blocked.

## Visual references and limitations
Official Library prepare_materialize succeeded for the six supplied individual mockups and later v3 PDF/overview, but every official helper materialize attempt failed with `download failed` (no HTTP status reported). Each used an explicit consumer-local destination. No alternate URL or download route was used. No v3 pixels could be inspected; this is NOT a claim of visual matching or a finished APK.

Locally inspected existing registration-v10 u01 card and roster60 u01 front portrait. The approved u01 name is 갈매미맨 and its approved species label is 태초. The label is correct and must remain; it is distinct from the retired old character 태초갈매미맨. The earlier final-response suggestion to replace that label was incorrect. Existing harbor/background/panel resources are reused; exact B reference is unverified. No new raster asset was generated.

Remaining: reference-pixel comparison and full device/narrow-screen visual QA; revive-target and ongoing-adventure selection still use existing dialogs. Formation options currently provide IDs/name, so no invented level/stat values are displayed. Party and craft touch/portrait layout needs actual device validation. Full v3 resource pack is not yet received.

## Follow-up access diagnosis
- Library: prepare_materialize succeeded for all eight files; workspace_path was null. Official library_file_transfer.py materialize failed once per transfer at the explicit /workspace/ui-v3-20261025/inputs destination with exit 1 and `download failed`. No HTTP code returned; destination remains empty. No repeated signed transfer or alternate URL was attempted. A new supported attachment/resource pack can be consumed when delivered; UI visual validation remains pending.
- Remote read-only ls-remote: manual/home-assembly-20261019 = 97c98e226c3a21591149d7da7d57eae06184b0d8, which is an ancestor of local HEAD. Remote review/recruit-policy-20261025 was not returned. No fetch, merge, push, or user-file overwrite performed.
- Existing environment HTTP(S) proxy resolves; the official Gradle URL returns a redirect through it. Java wrapper direct DNS failed. A command-local Java proxy configuration now uses the existing environment proxy; no network/security configuration or credentials were changed. Compile result recorded separately after completion.

Android test environment: initial Robolectric run failed before methods because /home/agent is read-only. A temporary Gradle init script configures test-JVM user.home inside /workspace and the existing proxy. No global settings or repository build policy changed. First actual run: 15 tests, four failures; two outdated debug/disabled-button assertions, one missing menu-access fixture response, and missing adventure escrow XP display. Assertions now target hidden debug text plus real state, fixture supplies explicit rebirth permission, and adventure displays accumulated XP sum. Final rerun PASS: NativeRoster60ProductBattleTest 5, Roster60ProductPresentationTest 6, NativeRoster60HumanPvpTest 4; total 15 tests, zero failures/errors, BUILD SUCCESSFUL in 55 seconds. Includes headless native-graphics layout/interaction checks, not physical-device or v3 image matching. Logs: /tmp/ui-v3-proxy-compile-2.log and /tmp/ui-v3-android-tests-final.log. No additional server changes since the 687-test pass.
