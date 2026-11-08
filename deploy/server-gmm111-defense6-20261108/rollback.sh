#!/usr/bin/env bash
# apply.sh(15차) 되돌리기: 백업해 둔 14차 파일 4개를 제자리에 돌려놓고 bgm.mp3를 지운 뒤 재시작. root로 실행: sudo bash rollback.sh <apply.sh가 알려 준 백업 폴더>
set -Eeuo pipefail
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
BK=${1:?백업 폴더 경로가 필요해요}
[ -f "$BK/working-directory.txt" ] || { echo "백업 정보가 없어 중단: $BK"; exit 1; }
CUR=$(cat "$BK/working-directory.txt")
[ "$(systemctl show gmm-server -p WorkingDirectory --value)" = "$CUR" ] || { echo "실행 폴더가 백업 때와 달라 중단: $CUR"; exit 1; }
for f in dist/src/site.js public/defense/index.html public/defense/defense.js public/defense/engine.js; do
  [ -f "$BK/$f" ] || { echo "백업 파일이 없어 중단: $f"; exit 1; }
  cp -a "$BK/$f" "$CUR/$f.restore" && mv -f "$CUR/$f.restore" "$CUR/$f"
done
rm -f -- "${CUR:?}/public/defense/bgm.mp3"
systemctl restart gmm-server || true
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then
    echo '되돌림 완료(14차 상태로 정상 실행 중).'; exit 0; fi; sleep 1; done
echo "되돌린 뒤에도 서버 확인 실패. 'systemctl status gmm-server' 결과를 확인해 주세요."; exit 1
