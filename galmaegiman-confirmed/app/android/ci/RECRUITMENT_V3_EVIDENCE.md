# Recruitment v3 evidence migration

This patch deliberately replaces the former three-large-trays, common-baseline composition with three selectors and one selected hero. `NativeEconomyThemeReviewTest` changes only its recruitment composition assertions; shared text visibility, 48dp, clipping and independent-overlap assertions remain intact. Existing mutation-contract tests remain intact.

## Historical verifier is not v3 proof

`verify_recruitment_revision_ci.py::verify_counter` and `NativeRecruitmentCounterReviewTest` are the historical v1 evidence contract: exactly 37 captures, three >=64px trays and one common baseline. The former is preserved for older revisions and asset-lock checks, not silently weakened. The latter was absent from the initial partial checkout and has now been fetched at the exact integration revision. Calling the preserved historical `verify_counter` function directly on v3 captures will correctly fail its incompatible layout assumptions; do not reuse old captures or disable assertions to make it green.

**Source migration implemented:** `NativeRecruitmentCounterReviewTest` was fetched from exact ffa4666868067b2e5feec5e8c967bb17a072c2bf (blob 18e36e0896ab4ae2bb256ca655904373bec986ef). Its render method now produces the v3 schema from measured native views, real text ink and PNG hashes. Its other three cooldown/claim/stale-operation methods remain unchanged. `verify_recruitment_revision_ci.py` now invokes `verify_counter_v3` in the active aggregate, binds sourceRevision to the checkout, preserves the claim-state contract and its two diagnostic PNGs, and hashes the new sidecar. Historical `verify_counter` remains for old-revision verifier tests only. The workflow runs new verifier tests and includes JSON sidecars in the existing artifact folder. No unrelated gates were disabled.

This source migration is syntax/static-reviewed but **has not run under Android/Robolectric**. It removes the known contradictory legacy assertion from the active path; it does not claim the new native tests pass. A complete checkout with existing dependencies must run them and fix any resulting genuine layout failures before acceptance.

## Focused native layout capture contract

`verify_recruitment_v3_layout.py` requires **11** captures, not a 112-image Cartesian product:

- All seven states (low/mid/high/cooldown/insufficient/maximum/unlimited): 800×360dp, fontScale 1.0.
- LOW boundary checks: 640×320/font1.0, 800×360/font1.3, 960×400/font2.0, 1280×720/font1.5.

Each must come from actual native views, contain its exact PNG SHA-256, pixel dimensions and density, safe-content bounds, selected kind, three selectors with selected/non-color cue semantics, one selected hero with asset/source/art bounds, all controls with measured/visible bounds, enabled state and description, and labels with actual ink/visible bounds and zero ellipsis. The gate measures >=48dp independently, clipping, overlaps, aspect ratio, state validity and no body vertical scroll. Required controls include back, three draw-card-KINDs, minus/plus/count/maximum/info/draw-claim/draw-submit. Required labels are draw-total, draw-unit-cost, draw-claim-status and draw-pity-summary (the measured compact information action text can be recorded with this semantic ID and ownerControl='info'). Essential pity remaining count must stay visible; full explanation may use a dialog.

See verifier source for exact field names. `evidence.json` top level: schema, sourceRevision (40-character commit), captures. There are deliberately **no generated example evidence files** beside this documentation: unit fixtures are not native evidence. Fail impossible profiles openly, then fix or document actual support policy; never hide controls or auto-shrink fonts to pass.

## Still outside this layout gate

Inset/fontScale combinations not represented above, actual TalkBack traversal, effective composited contrast, motion frames/reduced-motion/platform animator-off, repeated confirmation/request/claim guards, epoch/account/back/destroyed lifecycle, reveal timing, and skip/reopen mutation behavior require native tests/device inspection. The gate cannot establish them from source strings or user-provided booleans. Source revision/PNG hashes bind evidence files, not proof that an Android renderer actually generated them; review the producing native test and its run logs.

## Locally run

`python -m unittest test_verify_recruitment_revision_ci test_verify_recruitment_v3_layout -q` passes verifier unit fixtures. This does not run Android, compile Android, validate native geometry, or build APK/AAB. Native producer/aggregate integration is now implemented in source; its actual Android execution remains pending.

## Visual correction r2 (supersedes delivered v1 composition)

Every capture must now include `layoutRevision: 2` and `composition.upperInfoBoundsDp` / `composition.purchaseBoundsDp` measured from the two distinct right-column regions. The gate rejects delivered-v1 composition evidence even if its controls were geometrically valid. Upper information and lower purchase regions must be vertically separate, aligned, fully inside safe bounds and not intersect the hero. Unit cost and visible pity belong to the upper region; quantity, total and recruitment action belong to the lower panel; claim remains separate. This changes no viewport/state inventory or economic contracts.

Native assertions additionally check the selected card's distinct right-side check marker rather than the rejected title-prefix check, the separate information/purchase hierarchy, and absence of vertical scroll containers. Appearance and fidelity still require actual native captures; source/static tests cannot establish that the result matches the approved mockup. No Android rendering or build has been run in this correction workspace.

R2 local verification checkpoint: 59 Python verifier unit tests passed (47 historical + 12 current); four Java files passed syntax-only `JavacTask.parse`. Exact comparison confirmed the three cooldown/claim/stale-operation native test methods unchanged. These results do not execute those Android methods, validate actual view geometry, type-check Android symbols, or demonstrate visual fidelity.

Final r2 fidelity guards: the 800×360dp/fontScale1.0 profile must report measured `compactMode: false`; falling back to compact there now fails both the native producer and Python gate. Native audit requires the loaded canonical `/duel/icons/u-ticket.png` bitmap as claim's left compound drawable, exactly 26×26dp with a 6dp label gap. Existing `ready()` waits for NativeArt completion before audit. Latest local verifier count is **60 passing tests**; two edited native test sources syntax-parse passed. Native execution remains unrun.

## R3 correction guard (supersedes r2 evidence)

R3 capture metadata requires `layoutRevision: 3`; revision1/2 evidence is rejected. Native assertions now require the information block centered in the free band above purchase, a centered 184dp normal cost pill, preferred 74dp/68dp tier-card height constrained by actual available height, 94×52dp visible thumbnails, fixed48dp −/count/+ fields, 48–65dp maximum field with native1×24dp divider and4–10dp gaps, and8dp hero bottom inset. Existing9% side insets, 28%/224dp right-column minimum, canonical claim ticket, normal800×360/font1 noncompact guard,48dp/ink/clipping/no-overlap/no-vertical-scroll and economic assertions remain.

The Python gate independently checks cost centering/184dp normal width and quantity widths/gaps in actual native metadata. No screenshot inventory expansion. Compact text-preserving fallback and measured decoration reduction remain allowed outside the normal800×360 guard; no clipping is treated as success.

Local R3 checkpoint:61 verifier unit tests PASS; component and two native test Java sources syntax-parse PASS. Three cooldown/claim/stale-operation methods remain byte-identical to r2. These are not native execution, Android type-check or screenshot approval.

Final font-aware R3 policy: the maximum button is48–65dp at fontScale1; larger fonts may exceed65dp to retain the real label's natural width. Native audit explicitly checks natural text plus compound padding, and all gate text-ink/visible-bounds checks remain mandatory. This is not a font-size reduction or clipping exception.

## R3 reference-art integration checkpoint

The three new reference PNGs are versioned additions (1663×946); all three original PNGs and their hashes remain unchanged fallback assets. `recruitment-assets-lock.json` retains original `files` and adds six exact `referenceV3` identities: three PNGs, two static Noto Sans KR TTFs, and OFL license. `recruitment_reference_assets.py` pins the finite paths, bytes, SHA256s, dimensions, and per-kind canonical source rectangles. Source/APK theme gates validate these additions rather than broadly allowing image/font changes. The workflow runs the new asset regression suite.

Native producer now records actual asset-byte SHA256 and decoded dimensions. The v3 geometry gate requires matching selected-kind path/hash/dimensions and exact source rectangle before checking aspect-fit. Native audit independently scans alpha>128 bounds and requires correct dimensions. A new required native test covers per-kind metadata, defensive Rect copies, wrong-dimension/missing/recycled/decode-failure fallback and invalid rectangles. It is authored, not executed here.

Executed locally:64 recruitment verifier unit tests,23 theme verifier tests; actual six bundled new-asset hash checks; Java syntax parse (not type-check/build/runtime). Reference art was approved from a composite preview, not a native screenshot; possible minor edge fringes and Android rendering still need device/CI review. No claim/reveal/API contract changes are authorized by this asset gate migration.

Additional actual-byte checks: the full theme payload passed unchanged25base/themed entries +3old trays +3new trays; all three newPNG alpha>128 bounding boxes matched the locked sourceRect exactly. This checks bytes/metadata, not a rendered Android frame.
