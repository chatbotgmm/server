# D8 animation asset QA

Status: asset_level_accepted_with_per_frame_anchors_renderer_validation_pending.

Eight canonical PNG sheets, six row-major frames per sheet, strict 3-column × 2-row cells. All current canonical sheets passed alpha>8 cell-isolation checks. See manifest.json for SHA256, timings and source rectangles; measured-anchor sidecars for reviewed feet and motion. Independent review: independent-set-review.json.

Renderer playback, scale normalization and airborne offsets remain integration work. Archived *-first-needsqa.png files are excluded from runtime delivery.
