#!/usr/bin/env bash
# GMM111 + 경험치(마무리 전액·나머지 절반·1.5승 최소) 패치 적용. root로 실행: sudo bash apply.sh
# 하는 일: 지금 실행 폴더를 새 폴더로 복사 → 패치 12개 덮어쓰기 → DB·설정 백업 → 실행 폴더 전환 → 재시작·확인.
# 확인 실패 시 이전 폴더로 자동 되돌림. 운영 DB 내용은 바꾸지 않는다(스키마 변경 없음).
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
NAME=gmm111-xp-20261104
BUNDLE=galmaegiman-server-patch-$NAME.tar.gz
BUNDLE_SHA=beb6e4015177aca238b51d1b79297493a5a6e9f7c8145a927f0b74193fe5dd1b
OLD_EXPECT=/home/ubuntu/gmm-releases/907e737-20261029T230140Z/galmaegiman-confirmed
REL_NEW=/home/ubuntu/gmm-releases/$NAME
NEW=$REL_NEW/galmaegiman-confirmed
CONF=/etc/systemd/system/gmm-server.service.d/zzzz-recruit-working-directory.conf
DB=/home/ubuntu/gmm/galmaegiman-confirmed/storage/confirmed.db
REPO=/home/ubuntu/gmm
BK=/home/ubuntu/gmm-deploy-backups/$NAME-$(date -u +%Y%m%dT%H%M%SZ)
TMP=$(mktemp -d /tmp/gmm-apply-XXXXXX)
say(){ printf '%s\n' "▶ $*"; }
healthy(){ for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then return 0; fi; sleep 1; done; return 1; }

say '1/7 사전 확인'
OLD=$(systemctl show gmm-server -p WorkingDirectory --value)
[ "$OLD" = "$OLD_EXPECT" ] || { echo "실행 폴더가 예상과 달라 중단: $OLD"; exit 1; }
grep -qx "WorkingDirectory=$OLD" "$CONF" || { echo "설정 파일 내용이 예상과 달라 중단: $CONF"; exit 1; }
[ ! -e "$REL_NEW" ] || { echo "새 폴더가 이미 있어 중단: $REL_NEW"; exit 1; }
[ -f "$DB" ] && [ ! -L "$DB" ] || { echo "DB를 찾지 못해 중단: $DB"; exit 1; }
need=$(( $(du -sk "$(dirname "$OLD")" | cut -f1) + $(du -sk "$DB" | cut -f1) + 102400 ))
free=$(df -Pk /home/ubuntu | awk 'NR==2{print $4}')
[ "$free" -gt "$need" ] || { echo "디스크 여유 부족(필요 ${need}KB, 남음 ${free}KB)으로 중단"; exit 1; }
systemctl is-active --quiet gmm-server || { echo '서버가 지금 실행 중이 아니라 중단'; exit 1; }

say '2/7 묶음·서버 스냅샷 받기(읽기)'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin manual/home-assembly-20261019
runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:deploy/server-$NAME/$BUNDLE" > "$TMP/$BUNDLE"
echo "$BUNDLE_SHA  $TMP/$BUNDLE" | sha256sum -c --quiet || { echo '묶음 지문이 달라 중단'; exit 1; }
runuser -u ubuntu -- git -C "$REPO" fetch -q origin server-snapshot/gmm111-20261103
runuser -u ubuntu -- git -C "$REPO" show FETCH_HEAD:all-sha256.txt > "$TMP/all-sha256.txt"

say '3/7 지금 실행 폴더가 스냅샷(11-03) 이후 그대로인지 확인'
(cd "$OLD" && sha256sum -c --quiet "$TMP/all-sha256.txt") || { echo '스냅샷 이후 서버 파일이 바뀌어 중단(패치를 다시 만들어야 함)'; exit 1; }

say '4/7 새 폴더 만들기(복사) + 패치 덮어쓰기'
install -d -m 700 -o ubuntu -g ubuntu "$BK"
cp -a "$(dirname "$OLD")" "$REL_NEW"
tar -xzf "$TMP/$BUNDLE" -C "$NEW" --no-same-owner
tar -tzf "$TMP/$BUNDLE" | grep -v '/$' | while read -r f; do chown ubuntu:ubuntu "$NEW/$f"; done
for f in $(tar -tzf "$TMP/$BUNDLE" | grep -v '/$'); do cmp -s <(tar -xzOf "$TMP/$BUNDLE" "$f") "$NEW/$f" || { echo "덮어쓰기 확인 실패: $f"; exit 1; }; done

say '5/7 DB·설정 백업'
runuser -u ubuntu -- python3 - "$DB" "$BK/confirmed-before.db" <<'PY'
import sqlite3,sys
src=sqlite3.connect(sys.argv[1]);dst=sqlite3.connect(sys.argv[2]);src.backup(dst);dst.close()
ok=sqlite3.connect(sys.argv[2]).execute('pragma integrity_check').fetchone()[0];assert ok=='ok',ok;print('DB 백업 무결성',ok)
PY
cp -a "$CONF" "$BK/zzzz-recruit-working-directory.conf.bak"
echo "$OLD" > "$BK/old-working-directory.txt"

say '6/7 실행 폴더 전환·재시작'
printf '[Service]\nWorkingDirectory=%s\n' "$NEW" > "$CONF"
# 여기서부터는 실패해도 멈추지 않고 아래 확인·자동 되돌림으로 간다.
systemctl daemon-reload || true
systemctl restart gmm-server || true
if healthy && [ "$(systemctl show gmm-server -p WorkingDirectory --value)" = "$NEW" ]; then
  say '7/7 완료: 새 폴더로 정상 실행 중'
  echo "새 실행 폴더: $NEW"
  echo "백업 폴더: $BK"
  echo "되돌리기는 README의 rollback 명령에 이 백업 폴더를 넣어 실행: $BK"
else
  echo '새 폴더로 정상 기동하지 않아 이전 폴더로 되돌립니다.'
  cp -a "$BK/zzzz-recruit-working-directory.conf.bak" "$CONF"
  systemctl daemon-reload; systemctl restart gmm-server
  if healthy; then echo "되돌림 완료: 이전 폴더($OLD)로 정상 실행 중. 새 폴더는 확인용으로 남겨 둠: $REL_NEW"; else echo '되돌림 후에도 확인 실패. 백업: '"$BK"; fi
  exit 1
fi
rm -rf "$TMP"
