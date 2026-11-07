#!/usr/bin/env bash
# apply.sh(4차) 이전 실행 폴더(3차 상태)로 되돌리기. root로 실행: sudo bash rollback.sh <apply.sh가 알려 준 백업 폴더>
# 설정 파일만 되돌리고 재시작한다. 새 폴더와 DB는 지우거나 바꾸지 않는다.
# (DB는 이 패치에서 바뀌지 않는다.)
set -Eeuo pipefail
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
BK=${1:?백업 폴더 경로가 필요해요}
CONF=/etc/systemd/system/gmm-server.service.d/zzzz-recruit-working-directory.conf
[ -f "$BK/zzzz-recruit-working-directory.conf.bak" ] || { echo "백업 설정이 없어 중단: $BK"; exit 1; }
OLD=$(cat "$BK/old-working-directory.txt")
[ -d "$OLD" ] || { echo "이전 폴더가 없어 중단: $OLD"; exit 1; }
cp -a "$BK/zzzz-recruit-working-directory.conf.bak" "$CONF"
systemctl daemon-reload || true
systemctl restart gmm-server || true
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then
    echo "되돌림 완료: $(systemctl show gmm-server -p WorkingDirectory --value) 로 정상 실행 중"; exit 0; fi; sleep 1; done
echo "되돌린 뒤에도 서버 확인 실패. 'systemctl status gmm-server' 결과를 확인해 주세요."; exit 1
