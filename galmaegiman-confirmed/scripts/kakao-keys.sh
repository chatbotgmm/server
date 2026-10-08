#!/usr/bin/env bash
# 카카오 로그인 키를 서버 .env에 넣기(화면에 키가 보이지 않게 입력받음). 넣은 뒤 서버를 다시 시작함
# 사용: sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/kakao-keys.sh
set -euo pipefail
app="$(cd "$(dirname "$0")/.." && pwd)"
env="$app/.env"
[ -f "$env" ] || { echo ".env가 없습니다: $env"; exit 1; }
read -rsp 'REST API 키 붙여넣기(화면에 안 보여요) 후 Enter: ' rest; echo
read -rsp 'Client Secret 붙여넣기(없으면 그냥 Enter): ' secret; echo
rest="$(printf '%s' "$rest" | tr -d '[:space:]')"; secret="$(printf '%s' "$secret" | tr -d '[:space:]')"
[[ "$rest" =~ ^[0-9a-f]{32}$ ]] || { echo 'REST API 키 모양이 아니에요(영문 소문자·숫자 32자). 다시 해 주세요.'; exit 1; }
[[ -z "$secret" || "$secret" =~ ^[A-Za-z0-9]{10,}$ ]] || { echo 'Client Secret 모양이 이상해요. 다시 해 주세요.'; exit 1; }
cp "$env" "$env.bak-kakao"
tmp="$(mktemp)"
grep -vE '^(KAKAO_REST_KEY|KAKAO_CLIENT_SECRET)=' "$env" > "$tmp" || true
printf 'KAKAO_REST_KEY=%s\nKAKAO_CLIENT_SECRET=%s\n' "$rest" "$secret" >> "$tmp"
cat "$tmp" > "$env"; rm -f "$tmp"
chmod 600 "$env"
echo '✅ .env에 넣었어요(이전 파일은 .env.bak-kakao).'
sudo systemctl restart gmm-server && echo '🔄 서버를 다시 시작했어요. 홈페이지 /my 에 「카카오로 로그인」 버튼이 보이면 성공!'
