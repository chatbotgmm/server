# Product UI routing repair (local; not deployed)

Base: dadc88e. OAuth/reset changes are unchanged. Installed org.gmm version 88 binary is not available for comparison. Production is the separately deployed d562 + selected recruit files, not this checkout.

Confirmed causes:
- TitleView.GUARDIAN_PATH pointed at D1_s0_front.webp independently of account data. Now uses existing packaged u01-front.webp; original image was visually inspected.
- MainActivity.roster60Menus selected old UI when a product account lacked roster60 tutorial state. org.gmm now routes to new UI regardless of that state; this does not create/reset any account. Empty party explicitly offers tutorial state check.
- NativeEconomy.claimTickets refreshed native/state then called its own draw(), bypassing NativeRoster60Library.recruit/session reload. The consumed roster60Session then fell back to the non-session recruit screen. It now calls h.draw(), the product routing entrypoint. Shared claim API is preserved; no new charge/reward logic.
- story/adventure/AI legacy entrypoints now open the existing authenticated new battle chooser; human PvP opens the new party/match route. Mode-specific curated chapter/encounter screens remain future work; no claim that the generic chooser equals the approved final design.
- NativeRoster60Pvp used dark default text over scenery and stacked scrolling action buttons. It now uses existing NavyWorkbenchTheme tokens, a 2x3 paginated roster, 2x3 action panel, explicit empty state. No page-level vertical scrolling.
- New battle info controls were three vertically stacked 48dp rows. They share one horizontal row now, preserving more height for stats; existing art, six-command/six-skill panel and long-press cancellation are unchanged.

Reference limits: BATTLE-REFERENCE-UI.md describes the older web/33-unit layout, not a definitive new60 Android mock. UI-COLOR.md also contains superseded old-art instructions. Latest user-approved navy workbench/no full-screen vertical scrolling requirements take precedence, using existing NavyWorkbenchTheme. No new art or font introduced.

Screenshot access: official Library preparation returned transfers, but first image download through its current helper failed with 'download failed'; pixels of the submitted title/party screenshots were not available here. Parent's visual descriptions are secondary evidence. No before/after Android renders produced.

Validation limits: Gradle 8.14.3 not cached and services.gradle.org DNS failed. New Robolectric routing/layout tests are provided but unexecuted. Server regression is independent and is not visual or Android compile verification. No APK, signing, push, deployment, DB changes, or cleanup.
