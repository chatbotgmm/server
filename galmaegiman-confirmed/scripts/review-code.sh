#!/usr/bin/env bash
# 스토어 심사용 로그인 코드(숫자 6자리)를 새로 정해 .env에 넣고 서버를 다시 켬
# 사용: sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/review-code.sh   (끄기: … review-code.sh off)
set -euo pipefail
app="$(cd "$(dirname "$0")/.." && pwd)"; env="$app/.env"
[ -f "$env" ] || { echo ".env가 없습니다: $env"; exit 1; }
tmp="$(mktemp)"; grep -v '^REVIEW_CODE=' "$env" > "$tmp" || true
if [ "${1:-}" = off ]; then code=''; else code="$(shuf -i 100000-999999 -n 1)"; fi
printf 'REVIEW_CODE=%s\n' "$code" >> "$tmp"; cat "$tmp" > "$env"; rm -f "$tmp"; chmod 600 "$env"
sudo systemctl restart gmm-server
if [ -n "$code" ]; then echo "✅ 심사용 코드: $code  (플레이 콘솔 → 앱 액세스에 적어 주세요. 심사 전용 계정으로만 들어가요)"; else echo '✅ 심사용 코드를 껐어요.'; fi
