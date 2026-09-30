#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v cloudflared >/dev/null || { echo '기존에 사용하던 Ubuntu cloudflared가 필요합니다. README를 확인하세요.'; exit 1; }
game_port="$(node --env-file=.env -p 'process.env.PORT || 3000')"
cloudflared tunnel --url "http://127.0.0.1:$game_port" 2>&1 | node scripts/tunnel-log.mjs
