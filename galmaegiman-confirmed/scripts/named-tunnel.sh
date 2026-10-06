#!/usr/bin/env bash
# 고정 주소 Cloudflare 터널(대시보드·Zero Trust 없이 서버에서 직접 만들기)
# 먼저 한 번: cloudflared tunnel login   (나오는 주소를 휴대폰으로 열어 도메인 선택 → 승인)
# 그다음:     bash scripts/named-tunnel.sh gmm.dpdns.org
# 하는 일: 터널 'gmm' 만들기(이미 있으면 재사용) → 도메인을 터널로 연결(DNS) → 설정 파일 →
#         systemd 서비스 gmm-named-tunnel 설치·시작 → https://도메인/health 확인
# 기존 Quick Tunnel(gmm-tunnel)은 건드리지 않습니다. 확인 후 직접 끄세요.
set -euo pipefail

main() {
  local host="${1:-}" name=gmm dir="$HOME/.cloudflared" bin id port
  if [ -z "$host" ]; then echo '사용법: bash scripts/named-tunnel.sh gmm.dpdns.org'; exit 1; fi
  bin="$(command -v cloudflared || true)"
  if [ -z "$bin" ]; then echo '❌ cloudflared가 없습니다.'; exit 1; fi
  if [ ! -f "$dir/cert.pem" ]; then
    echo '❌ 먼저 로그인이 필요합니다: cloudflared tunnel login'
    echo '   나오는 https 주소를 휴대폰 브라우저로 열고 gmm.dpdns.org 를 선택해 승인하세요.'; exit 1
  fi
  cd "$(dirname "$0")/.."
  port="$(node --env-file=.env -p 'process.env.PORT || 3000' 2>/dev/null || echo 3000)"

  echo "▶ 1/5 터널 '$name' 준비"
  id="$("$bin" tunnel list --output json 2>/dev/null | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const t=(JSON.parse(s||'[]')||[]).find(x=>x.name==='$name');process.stdout.write(t?t.id:'')})")"
  if [ -z "$id" ]; then
    "$bin" tunnel create "$name"
    id="$("$bin" tunnel list --output json | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const t=JSON.parse(s).find(x=>x.name==='$name');process.stdout.write(t?t.id:'')})")"
  fi
  if [ -z "$id" ] || [ ! -f "$dir/$id.json" ]; then
    echo "❌ 터널 정보를 찾지 못했습니다(id=$id). 화면을 캡처해 보내 주세요."; exit 1
  fi
  echo "   터널 id: $id"

  echo "▶ 2/5 $host → 터널 연결(DNS)"
  "$bin" tunnel route dns --overwrite-dns "$name" "$host"

  echo "▶ 3/5 설정 파일"
  cat > "$dir/config.yml" <<EOF
tunnel: $id
credentials-file: $dir/$id.json
ingress:
  - hostname: $host
    service: http://127.0.0.1:$port
  - service: http_status:404
EOF
  chmod 600 "$dir/config.yml"

  echo "▶ 4/5 서비스 gmm-named-tunnel 설치·시작"
  sudo tee /etc/systemd/system/gmm-named-tunnel.service >/dev/null <<EOF
[Unit]
Description=GMM fixed Cloudflare tunnel ($host)
After=network-online.target
Wants=network-online.target

[Service]
User=$(id -un)
ExecStart=$bin --no-autoupdate tunnel --config $dir/config.yml run $name
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
  sudo systemctl daemon-reload
  sudo systemctl enable --now gmm-named-tunnel

  echo "▶ 5/5 https://$host/health 확인 (최대 60초)"
  local i
  for i in $(seq 1 20); do
    if curl -fsS "https://$host/health" 2>/dev/null; then
      echo; echo "✅ 고정 주소 동작: https://$host"
      echo "다음: npm run url -- https://$host  →  sudo systemctl restart gmm-server  →  카카오 스킬 URL 변경"
      return 0
    fi
    sleep 3
  done
  echo "⚠ 아직 응답이 없습니다. Cloudflare에서 도메인이 Active인지 확인하세요(네임서버 반영 대기 중일 수 있음)."
  echo "   상태 확인: sudo systemctl status gmm-named-tunnel --no-pager | head -15"
  return 1
}

main "$@"
