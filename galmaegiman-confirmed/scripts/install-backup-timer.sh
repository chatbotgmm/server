#!/usr/bin/env bash
# 매일 한국 시간 04:30에 자동 백업하는 systemd 타이머를 설치합니다.
# 사용법: sudo bash scripts/install-backup-timer.sh
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "sudo로 실행하세요: sudo bash scripts/install-backup-timer.sh"; exit 1; }
DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_USER="${SUDO_USER:-ubuntu}"
NODE_BIN="$(sudo -u "$RUN_USER" bash -lc 'command -v node' || true)"
[ -n "$NODE_BIN" ] || { echo "node를 찾지 못했습니다. $RUN_USER 계정에서 node -v 가 되는지 확인하세요."; exit 1; }
cat > /etc/systemd/system/gmm-backup.service <<UNIT
[Unit]
Description=GMM daily DB backup

[Service]
Type=oneshot
User=$RUN_USER
WorkingDirectory=$DIR
Environment=PATH=$(dirname "$NODE_BIN"):/usr/local/bin:/usr/bin:/bin
ExecStart=/usr/bin/env bash $DIR/scripts/backup-auto.sh
UNIT
cat > /etc/systemd/system/gmm-backup.timer <<UNIT
[Unit]
Description=GMM daily DB backup (04:30 KST)

[Timer]
OnCalendar=*-*-* 04:30:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now gmm-backup.timer
echo "지금 한 번 백업해 봅니다…"
systemctl start gmm-backup.service
journalctl -u gmm-backup.service -n 5 --no-pager
echo
systemctl list-timers gmm-backup.timer --no-pager
