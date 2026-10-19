# Android recent battle records

The Android records page keeps the latest ten completed, observed battles per authenticated player on this installation. Entry: Modes > Battle > Battle records. Story, normal, ranked and practice are labelled separately in the same records list. There is no new active-battle control.

## Evidence and limits

`src/native.ts` stores native Fight sessions in memory with expiry. Its snapshots contain revision, teams, HP, effects, cumulative history and display events. Existing result settlement records are not complete native-state archives. Realtime's `mapRealtimeNativeBattle` returns a viewer-specific projection and hides pending opponent choices.

This implementation records the authorized snapshots as they arrive, before visual playback. It does not rerun combat, infer missing states, or merge the final state backward into earlier frames. Replay advances by received snapshot, not by each engine-internal event. Every frame retains its own chronological event/history data and full observed teams. An interrupted connection may leave unobserved intermediate states; the player explains this. If engine turn/event sequence shows earlier progression, it explicitly says the recording starts partway through. Transport revisions include acceptance/preparation and are not used as evidence of a missing beginning. This feature cannot recover historical result-only matches and is not a cross-device replay service.

Only a confirmed terminal snapshot (`phase=over` and a result object) following a non-terminal snapshot is archived. Failed requests, abandoned partial sessions, and a lone historic final result are never presented as complete replay recordings. Draft capture survives app restart. One draft is retained per account, reflecting the app's single active battle. Session identity deduplicates completions. New completions are prepended and the eleventh prunes the oldest. The first received completion time is retained.

## Security and read-only behavior

- The auth token's stable player-ID prefix selects a SHA-256-named account file. Raw credentials are never stored.
- Files live under Android `getNoBackupFilesDir()`: app-private and excluded from backup/transfer. Uninstall or clearing app storage removes the records.
- Root snapshot fields are allowlisted. Room/submission payloads, settlement/reward payloads, credentials and live legal-action lists are excluded. Opponent offers/pending selections are removed, even if a server variant sends them.
- No prior frame is enriched from later frames. Opponent skill detail also follows the existing post-first-turn disclosure rule. Art uses the recorded original-species image path and stage; it never substitutes combatDefinitionId (which may identify a reincarnation ancestor). If the source only provides an original-species front thumbnail, the player says that no back image was recorded. Recorded scale is bounded to 0.5–2 and tint follows the existing native story tint rules. Unknown paths are not guessed.
- `BattleReplayTimeline` and `BattleReplayStore` have no API/economy/combat dependency. `NativeBattleReplay.Player` does not instantiate `BattleView`, call turn/room commands, award rewards, update ratings, or consume items.
- Player navigation is local. Play/pause/step only move a defensive-copy cursor. Leaving, backgrounding, account mismatch and disposal stop playback; stale callbacks cannot restart a disposed cursor.
- A single serial background worker handles archive reads, writes and fsync. The UI freezes only each newly accepted DTO and its account identity. A cached active account holds only the index and current draft; completed matches live in separate files, so observing a frame never reads/rewrites ten old archives. Writes use a synced temporary file and atomic same-directory rename. The archive index is published only after its recording file is durable. I/O failure does not create an index entry; the in-memory draft/index rolls back so an identical terminal response can retry after storage recovers. On a valid index load and after successful publication, unreferenced match files and stale temporary files are cleaned up, including crash leftovers. Malformed effect entries are rejected and the renderer also skips null entries. Duplicate frames have no disk I/O. Drafts are bounded to 1,200 frames / 4 MiB; overflow is marked once then ignored for that session, including after restart. The records screen explains limits and storage/corruption exclusions. Pending queued writes continue serially across navigation/backgrounding; abrupt process termination can still lose a not-yet-written last response.

## Integration

Apply the provided MainActivity and NativeMenus narrow patches to the final integrated owners. The NativeRealtime owner adds `NativeBattleReplay.observe(h, next)` after accepted snapshot decoration, before playback. MainActivity observes battleScreen and accepted native turn responses, routes records locally, blocks replay-tagged action submission, and pauses playback onPause.

No server code, schema, migration, operational database or deployment changes are required. Existing server behavior is unchanged.

## Verification

New suites: BattleReplayStoreTest (10 tests), BattleReplayStabilityTest (10 tests), NativeBattleReplayReviewTest (6 tests). Tests cover ten-item retention, duplicate completion, account isolation, restart, failed/partial results, regression, privacy filtering, immutable deterministic state, read-only API absence, unchanged user state, account change and disposal. Stability coverage adds frame/byte caps, duplicate/overflow write counts, malformed storage, real Handler timer ticks, dirty-team navigation, and rapid queued account switches, transient write recovery, crash-orphan cleanup, malformed effect defense and explicit unknown-mode/result labels. Realtime integration separately verifies all accepted snapshots are archived before animation coalescing. Native review renders the production player at four phone/density/font profiles, two positions each; screenshots and observed sidecars are emitted to `battle-replay/`.

Local Java syntax parsing passed. This executor has no Android SDK/Gradle runtime; Android compilation, Robolectric execution and visual capture review must run in CI before the feature is reported verified. Source parsing is not a runtime or visual pass.
