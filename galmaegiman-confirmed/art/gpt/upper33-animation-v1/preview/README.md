# Local animation inspector

Open `index.html` in a desktop browser after extracting all package parts into the same directory. Keep `preview/`, `D1/`…`D8/`, `T1/`…`T8/`, `E1/`…`E8/`, and `I1/`…`I9/` as siblings. No network, server, installation or publishing is required. The catalog embeds metadata only; PNGs load by relative paths.

If a browser blocks local-file images, serve the parent directory locally with `python -m http.server 8765 --bind 127.0.0.1` and open `http://127.0.0.1:8765/preview/`. This is optional, loopback-only, and is not a deployment.

## Controls
- Choose any of 33 characters, front/back, and idle/attack/hit-defend/entry.
- Pause, step backward/forward or restart. Space toggles playback; arrow keys step when a form control is not focused.
- One-shot actions stop at their last frame; Repeat explicitly overrides that behavior.
- Toggle measured anchors, proposed uniform sheet scale, available authored Y offsets and overlays independently.
- Cyan cross is the fixed world origin. Yellow cross is the current rendered foot anchor. Dashed outline is the source cell. Green outline is recorded visible alpha bounds.
- The right panel highlights the current frame in the unchanged source sheet.

## Metadata policy
`build_catalog.py` reads the actual heterogeneous manifests and hash-bound anchor sidecars. It does not edit pixels or source metadata. Reviewed anchor sidecars take precedence. Body-normalization scale takes precedence over older silhouette proposals. Uniform sheet scales apply to all six frames; poses are never individually rescaled. Available authored offsets are interpreted as source-pixel Y displacement and scaled with the sheet.

Missing scales visibly use raw 1.0. Missing anchors visibly fall back to the nominal cell pivot. For entry actions or explicitly reviewed airborne frames, missing authored offsets visibly use zero only as an **anchor-only diagnostic**. Ordinary grounded actions use zero displacement without a missing-offset warning. This may suppress intended hops or entry motion, so it is not faithful game playback. No airborne displacement is invented. Hash mismatches are prominently flagged and require a rebuilt catalog after repair/review.

## Verification performed
- JavaScript syntax check passed.
- Catalog adapts all 33 characters / 264 canonical sheets.
- Current PNG hashes verified against manifests; see `catalog-audit.json`.
- Mock-DOM control tests cover all 264 sheets, finite canvas coordinates, all frame stepping, one-shot stop and repeat. See `test-results.json`.
- Real Chromium smoke test was attempted but blocked by the execution environment's socket sandbox; a permission retry also failed. **The preview has not been visually browser-validated.** `test_preview.cjs` is retained for a developer with Playwright and Chromium to run.
- Asset-level independent acceptance is separate from game/runtime acceptance. This HTML is a diagnostic handoff, not runtime validation.

## Updating the catalog
Run `python preview/build_catalog.py` from the package parent after manifests, anchors or PNGs change. It writes only preview metadata. Run `node preview/test_controls.cjs` for the dependency-free control test. Optional browser test requires Playwright plus `/usr/bin/chromium` (adjust executable path for your machine).

## Known limitations
The final catalog has proposed uniform scales and measured anchors for all 264 sheets, with no PNG/manifest hash mismatches. 67 sheets are flagged for unprovided entry or independently reviewed airborne trajectories. Ordinary grounded actions are not counted as missing motion. See `metadata-gaps.json` and per-sheet warnings. The preview cannot establish intended entry height, recoil displacement or gameplay timing without those authored decisions. Large normalized decorations can extend beyond the diagnostic viewport; this is flagged separately from source-cell clipping. Final game renderer playback and transitions still need validation.
