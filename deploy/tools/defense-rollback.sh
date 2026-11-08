#!/usr/bin/env bash
# defense-update.sh 되돌리기: 백업해 둔 파일을 제자리에 돌려놓고 새로 생긴 파일을 지운다(서버 코드가 바뀌었던 경우만 재시작).
# root로 실행: sudo bash defense-rollback.sh <defense-update.sh가 알려 준 백업 폴더>
set -Eeuo pipefail
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
BK=${1:?백업 폴더 경로가 필요해요}
[ -f "$BK/working-directory.txt" ] || { echo "백업 정보가 없어 중단: $BK"; exit 1; }
CUR=$(cat "$BK/working-directory.txt")
[ "$(systemctl show gmm-server -p WorkingDirectory --value)" = "$CUR" ] || { echo "실행 폴더가 백업 때와 달라 중단: $CUR"; exit 1; }
if [ -d "$BK/files" ]; then (cd "$BK/files" && find . -type f | sed 's#^\./##') | while IFS= read -r f; do cp -a "$BK/files/$f" "$CUR/$f.restore" && mv -f "$CUR/$f.restore" "$CUR/$f"; done; fi
while IFS= read -r f; do [ -n "$f" ] && rm -f -- "${CUR:?}/$f"; done < "$BK/new-files.txt"
if [ "$(cat "$BK/site-changed.txt" 2>/dev/null)" = yes ]; then systemctl restart gmm-server || true; fi
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then
    echo "되돌림 완료($(cat "$BK/stage.txt" 2>/dev/null) 상태)."; exit 0; fi; sleep 1; done
echo "되돌린 뒤 서버 확인 실패. 'systemctl status gmm-server' 결과를 확인해 주세요."; exit 1
