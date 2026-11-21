#!/usr/bin/env bash
# pixelfx(vendor/pixelfx, MIT)에서 고른 효과를 public/defense/pixfx.js 한 파일로 묶는다. 고른 효과는 scripts/web/pixfx-entry.js에서.
# 사용(galmaegiman-confirmed 폴더에서): bash scripts/web/build-pixfx.sh  → 그다음 python3 -I scripts/web/stamp-defense.py
set -euo pipefail
cd "$(dirname "$0")/../.."
BANNER='/* 랜덤 디펜스 효과 묶음 — scripts/web/pixfx-entry.js를 esbuild로 묶음. 효과 코드: pixelfx (https://github.com/patch-gg/pixelfx), MIT License, Copyright (c) 2026 Patch, Copyright (c) 2026 Hugo Duprez. 일부는 Sprite Fusion(MIT, Hugo Duprez)·Hollow Crown(MIT, rehan-remade)에서 옮김 — vendor/pixelfx/LICENSE·NOTICE.md 참고 */'
npx esbuild scripts/web/pixfx-entry.js --bundle --format=esm --minify --target=es2020 --legal-comments=none "--banner:js=$BANNER" --outfile=public/defense/pixfx.js
ls -la public/defense/pixfx.js
