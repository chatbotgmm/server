#!/usr/bin/env bash
# apply.sh(12차) 되돌리기: 백업해 둔 11차 파일 3개(index.html·defense.js·engine.js)를 제자리에 돌려놓는다. root로 실행: sudo bash rollback.sh <apply.sh가 알려 준 백업 폴더>
# 서버 코드·DB·설정은 바꾸지 않고 재시작도 하지 않는다.
set -Eeuo pipefail
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
BK=${1:?백업 폴더 경로가 필요해요}
[ -f "$BK/working-directory.txt" ] || { echo "백업 정보가 없어 중단: $BK"; exit 1; }
CUR=$(cat "$BK/working-directory.txt")
[ "$(systemctl show gmm-server -p WorkingDirectory --value)" = "$CUR" ] || { echo "실행 폴더가 백업 때와 달라 중단: $CUR"; exit 1; }
for f in index.html defense.js engine.js; do
  [ -f "$BK/$f" ] || { echo "백업 파일이 없어 중단: $f"; exit 1; }
  cp -a "$BK/$f" "$CUR/public/defense/$f.restore" && mv -f "$CUR/public/defense/$f.restore" "$CUR/public/defense/$f"
done
curl -fsS --max-time 10 http://127.0.0.1:3000/defense 2>/dev/null | grep -q '/defense/defense.js"' && echo '되돌림 완료(11차 상태).' || { echo '되돌린 뒤 확인 실패. 서버 상태를 확인해 주세요.'; exit 1; }
