#!/usr/bin/env bash
# 구글 플레이 앱(TWA)과 홈페이지를 잇는 파일(config/assetlinks.json) 만들기
# 사용: sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/assetlinks.sh
# 지문(SHA-256)은 플레이 콘솔 → 앱 → 출시 → 설정 → 앱 무결성 → 「앱 서명 키 인증서」의 SHA-256 (업로드 키 아님)
set -euo pipefail
app="$(cd "$(dirname "$0")/.." && pwd)"
read -rp '앱 패키지 이름 (예: org.dpdns.gmm.twa): ' pkg
[[ "$pkg" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo '패키지 이름 모양이 아니에요(소문자, 점으로 구분).'; exit 1; }
read -rp 'SHA-256 지문 (AA:BB:… 64자리, 여러 개면 쉼표로): ' fps
json=""
IFS=',' read -ra arr <<< "$fps"
for f in "${arr[@]}"; do
  f="$(echo "$f" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
  [[ "$f" =~ ^([0-9A-F]{2}:){31}[0-9A-F]{2}$ ]] || { echo "지문 모양이 아니에요: $f"; exit 1; }
  json="${json:+$json,}\"$f\""
done
mkdir -p "$app/config"
printf '[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"%s","sha256_cert_fingerprints":[%s]}}]\n' "$pkg" "$json" > "$app/config/assetlinks.json"
echo "✅ $app/config/assetlinks.json 을 만들었어요. 서버를 다시 켤 필요 없어요."
echo "확인: https://<홈페이지>/.well-known/assetlinks.json"
