#!/usr/bin/env bash
# 저장소 루트에서 bash scripts/test-augment-draft.sh. Node.js 24와 git 필요.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
base=34e9b4b08ee0e6ed181bf9e87f73ce34bee7872d
git cat-file -e "$base^{commit}"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git archive HEAD galmaegiman-confirmed test scripts docs/handoffs/augment-60-draft | tar -x -C "$tmp"
mkdir "$tmp/pristine"
git archive "$base" galmaegiman-confirmed | tar -x -C "$tmp/pristine" --strip-components=1
cd "$tmp"
node --import ./scripts/ts-source-loader.mjs --test test/*.test.mjs galmaegiman-confirmed/test/*.mjs
