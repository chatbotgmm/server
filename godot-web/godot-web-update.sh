#!/usr/bin/env bash
# 갈매미맨 랜덤 디펜스 고도 판(웹 빌드)을 지금 서버 실행 폴더의 public/defense-godot에 설치한다. root로: sudo bash godot-web-update.sh
# 묶음: 이 갈래(manual/godot-web)의 godot-web/defense-godot.tar(index.wasm은 .gz만 들어 있어 여기서 풀어 둠). 원본은 저장소 chatbotgmm/grd
# - 서버 코드는 바꾸지 않음(주소 /defense-godot/는 디펜스 묶음의 site.js가 냄). 새 폴더에 풀어 지문을 확인한 뒤 한 번에 바꿔 끼움
# - 예전 것은 /home/ubuntu/gmm-deploy-backups/godot-web-<시각>으로 옮겨 둠. 확인에 실패하면 되돌림
# - 주의: 서버 실행 폴더(gmm-releases/…)가 새로 바뀌면 이 폴더는 따라가지 않으므로 다시 실행해야 함
set -Eeuo pipefail
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
REPO=/home/ubuntu/gmm; BR=manual/godot-web; REL=/home/ubuntu/gmm-releases
CUR=$(systemctl show gmm-server -p WorkingDirectory --value)
case "$CUR" in "$REL"/?*) ;; *) echo "실행 폴더가 $REL 아래가 아니라 중단: $CUR"; exit 1;; esac
free=$(df -Pk "$CUR" | awk 'NR==2{print $4}'); [ "$free" -gt 204800 ] || { echo "디스크 여유 부족(남음 ${free}KB, 200MB 필요)으로 중단"; exit 1; }
TMP=$(mktemp -d /tmp/gmm-godot-XXXXXX); trap 'rm -rf "${TMP:?}"' EXIT
echo '▶ 1/4 묶음 받기'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin "$BR"
for f in defense-godot.tar defense-godot.tar.sha256 files.sha256; do runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:godot-web/$f" > "$TMP/$f"; done
(cd "$TMP" && sha256sum -c --quiet defense-godot.tar.sha256) || { echo '묶음 지문이 달라 중단'; exit 1; }
echo '▶ 2/4 풀기·확인'
mkdir "$TMP/x"; tar -xf "$TMP/defense-godot.tar" -C "$TMP/x" --no-same-owner
gzip -dc "$TMP/x/index.wasm.gz" > "$TMP/x/index.wasm"
(cd "$TMP/x" && sha256sum -c --quiet "$TMP/files.sha256") || { echo '풀린 파일 지문이 달라 중단'; exit 1; }
DEST="$CUR/public/defense-godot"; NEW="$DEST.new"; BK=/home/ubuntu/gmm-deploy-backups/godot-web-$(date -u +%Y%m%dT%H%M%SZ)
rm -rf -- "${NEW:?}"; install -d -m 755 -o ubuntu -g ubuntu "$NEW"
for f in "$TMP"/x/*; do install -m 644 -o ubuntu -g ubuntu "$f" "$NEW/$(basename "$f")"; done
echo '▶ 3/4 바꿔 끼우기'
had=no; if [ -d "$DEST" ]; then install -d -m 700 -o ubuntu -g ubuntu "$(dirname "$BK")"; mv "$DEST" "$BK"; had=yes; fi
mv "$NEW" "$DEST"
echo '▶ 4/4 확인'
ok=yes
curl -fsS --max-time 10 http://127.0.0.1:3000/defense-godot/ | grep -q 'index.js?v=' || ok=no
[ "$(curl -sS --max-time 30 -o /dev/null -w '%{http_code} %{content_type}' -H 'Accept-Encoding: gzip' http://127.0.0.1:3000/defense-godot/index.wasm)" = '200 application/wasm' ] || ok=no
[ "$(curl -sS --max-time 60 -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/defense-godot/index.pck)" = 200 ] || ok=no
if [ "$ok" = yes ]; then echo "▶ 완료: 고도 판 설치 $(cat "$DEST/BUILD.txt" 2>/dev/null) → $DEST"; [ "$had" = yes ] && echo "예전 것: $BK"; exit 0; fi
echo '확인 실패 → 되돌림'; rm -rf -- "${DEST:?}"; [ "$had" = yes ] && mv "$BK" "$DEST"; exit 1
