# I6–I9 animation asset integration

Each character has front/back × idle/attack/hit-defend/entry. PNG atlases are 1536×1024, 3 columns × 2 rows, six 512×512 cells, row-major.

- Source artwork and repairs were generated only with the built-in image tool. Original generated pixels are copied without programmatic repainting.
- True transparency is present. Hidden RGB may show brown previews in image viewers that ignore alpha. Maximum alpha254 is expected; visible isolation was checked at alpha>8/255.
- Preserve each full512×512 source cell. Use the measured per-frame foot anchor in metadata and the same actor world origin across all animation frames.
- Use the proposed body_normalization.uniform_sheet_render_scale for all six frames of one sheet. Do not rescale each frame independently. These are visual estimates, requiring renderer/playback validation.
- I6 orbital stars/feathers and I8 magic oval are canonical ornaments, not cast shadows. They are excluded from body-height normalization. No new ground shadows were baked in.
- I9 shell shoes contain water. The shell base midpoint is the ground anchor.
- Frame timing is provided as a starting proposal. Idle loops; other actions play once and return to idle.
- Independent static asset acceptance does not mean runtime validation. Confirm neutral transition scale, pivot, timing, filtering/bleed and background compositing in the actual game.
- Prior rejected generations remain in archive/ and are excluded from manifests.
