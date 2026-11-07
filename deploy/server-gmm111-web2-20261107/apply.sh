#!/usr/bin/env bash
# 4차: 예전 웹게임(/game)·로그인 뒤 이동을 새 웹게임(/game60)으로. root로 실행: sudo bash apply.sh
# 3차 패치(gmm111-web-20261106)가 적용된 상태에서만 동작한다.
# 하는 일: 지금 실행 폴더를 새 폴더로 복사 → 파일 2개(site.js, my.html) 덮어쓰기 → DB·설정 백업 → 실행 폴더 전환 → 재시작·확인.
# 확인(서버 상태·새 테마·60종 페이지·예전 주소 이동) 실패 시 이전 폴더로 자동 되돌림. DB 내용·스키마는 바꾸지 않는다.
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
NAME=gmm111-web2-20261107
BUNDLE=galmaegiman-server-patch-$NAME.tar.gz
BUNDLE_SHA=7478f40c2af01bec7482b559a17b5f0bf2cb20dabcee91ff7c75bfbad5e88b11
OLD_EXPECT=/home/ubuntu/gmm-releases/gmm111-web-20261106/galmaegiman-confirmed
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
# 확인은 응답을 끝까지 받은 뒤 찾는다(set -o pipefail에서 `curl | grep -q`는 grep이 먼저 끝나면 curl이 실패로 끝나 맞아도 실패가 됨).
has(){ local body; body=$(curl -fsS --max-time 10 "http://127.0.0.1:3000$1" 2>/dev/null) || return 1; [[ "$body" == *"$2"* ]]; }
code(){ [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$1" 2>/dev/null)" = "$2" ]; }
site_ok(){ local i miss; for i in $(seq 1 10); do miss=''
  has /about 'site/harbor.css' || miss+=' 소개(테마)'
  has /odds '고급 모집' || miss+=' 모집확률'
  code /site/units/u01.webp 200 || miss+=' 등록증그림'
  code /dex 301 || miss+=' 예전주소이동'
  has / 'class="gold-cta" href="/download"' || miss+=' 첫화면버튼'
  has /game60 '/game60/game.js' || miss+=' 웹게임'
  code /game60/assets/units/u01-front.webp 200 || miss+=' 웹게임그림'
  # 패치에 없는 기존 파일도 그대로 있어야 한다(바로가기 사고 방지)
  has /about 'site/site.css?v=' || miss+=' 기존스타일연결'
  code /site/site.css 200 || miss+=' site.css'
  code /site/legacy.css 200 || miss+=' legacy.css'
  code /site/home-v4/harbor.png 200 || miss+=' 첫화면그림'
  code /site/village-v4/preview.js 200 || miss+=' 마을스크립트'
  [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{redirect_url}' http://127.0.0.1:3000/game 2>/dev/null)" = '302 http://127.0.0.1:3000/game60' ] || miss+=' 예전웹게임이동'
  has /my "const next = '/game60'" || miss+=' 로그인복귀'
  [ -z "$miss" ] && return 0; sleep 2; done
  echo "확인 실패 항목:$miss"; return 1; }

say '1/7 사전 확인'
OLD=$(systemctl show gmm-server -p WorkingDirectory --value)
[ "$OLD" = "$OLD_EXPECT" ] || { echo "실행 폴더가 예상과 달라 중단: $OLD"; exit 1; }
grep -qx "WorkingDirectory=$OLD" "$CONF" || { echo "설정 파일 내용이 예상과 달라 중단: $CONF"; exit 1; }
# 지난 시도에서 남은 실패 폴더는 지우지 않고 옆으로 치운다(지금 실행 중인 폴더면 중단)
if [ -e "$REL_NEW" ]; then
  [ "$OLD" != "$NEW" ] || { echo "새 폴더가 이미 실행 중이라 중단: $REL_NEW"; exit 1; }
  AWAY="$REL_NEW.failed-$(date -u +%Y%m%dT%H%M%SZ)"; mv "$REL_NEW" "$AWAY"; echo "지난번 실패 폴더를 옮겨 둠: $AWAY"
fi
[ -f "$DB" ] && [ ! -L "$DB" ] || { echo "DB를 찾지 못해 중단: $DB"; exit 1; }
need=$(( $(du -sk "$(dirname "$OLD")" | cut -f1) + $(du -sk "$DB" | cut -f1) + 153600 ))
free=$(df -Pk /home/ubuntu | awk 'NR==2{print $4}')
[ "$free" -gt "$need" ] || { echo "디스크 여유 부족(필요 ${need}KB, 남음 ${free}KB)으로 중단"; exit 1; }
systemctl is-active --quiet gmm-server || { echo '서버가 지금 실행 중이 아니라 중단'; exit 1; }

say '2/7 묶음·기대 지문 받기(읽기)'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin manual/home-assembly-20261019
for f in "$BUNDLE" expected-before.sha256; do runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:deploy/server-$NAME/$f" > "$TMP/$f"; done
echo "$BUNDLE_SHA  $TMP/$BUNDLE" | sha256sum -c --quiet || { echo '묶음 지문이 달라 중단'; exit 1; }

say '3/7 지금 실행 폴더가 3차 패치 상태 그대로인지 확인'
(cd "$OLD" && sha256sum -c --quiet "$TMP/expected-before.sha256") || { echo '3차 패치 상태와 서버 파일이 달라 중단(패치를 다시 만들어야 함)'; exit 1; }

say '4/7 새 폴더 만들기(복사) + 사이트 파일 덮어쓰기'
install -d -m 700 -o ubuntu -g ubuntu "$BK"
cp -a "$(dirname "$OLD")" "$REL_NEW"
# [바로가기 보호 시작] 운영 릴리스는 public/site 등이 예전 릴리스를 가리키는 바로가기(심볼릭 링크)다.
# 그대로 풀면 tar가 바로가기를 빈 폴더로 바꿔 기존 파일이 사라진다(2026-11-06 실제 발생). 그래서
# 패치가 들어갈 폴더가 바로가기면 가리키는 곳의 내용을 새 폴더에 진짜 폴더로 복사하고, 패치 파일 자리가 바로가기면 바로가기만 지운다.
# 원본(바로가기 대상)과 이전 실행 폴더는 건드리지 않는다.
materialize(){ local p="$NEW/$1" t; if [ -L "$p" ]; then t=$(readlink -f "$p"); [ -d "$t" ] || { echo "바로가기 대상이 폴더가 아니라 중단: $1 -> $t"; exit 1; }
  rm -- "$p"; cp -a -- "$t" "$p"; echo "바로가기를 실제 폴더로 복사: $1 (원본 $t)"; fi; }
for d in $(tar -tzf "$TMP/$BUNDLE" | grep '/$' | sed 's#^\./##; s#/$##' | grep -v '^$' | awk -F/ '{print NF" "$0}' | sort -n -k1,1 -s | cut -d' ' -f2- | uniq); do materialize "$d"; done
for f in $(tar -tzf "$TMP/$BUNDLE" | grep -v '/$'); do if [ -L "$NEW/$f" ]; then rm -- "$NEW/$f"; echo "파일 바로가기를 지우고 새로 씀: $f"; fi; done
# [바로가기 보호 끝]
# 새로 생기는 폴더는 읽을 수 있게(755), 기존 폴더 권한은 건드리지 않음
(umask 022; tar -xzf "$TMP/$BUNDLE" -C "$NEW" --no-same-owner --no-overwrite-dir)
tar -tzf "$TMP/$BUNDLE" | grep -v '/$' | while read -r f; do chown ubuntu:ubuntu "$NEW/$f"; done
# (이번 묶음은 새 폴더를 만들지 않음)
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
if healthy && [ "$(systemctl show gmm-server -p WorkingDirectory --value)" = "$NEW" ] && site_ok; then
  say '7/7 완료: 새 폴더로 정상 실행 중(예전 웹게임 → 새 웹게임 이동 확인)'
  echo "새 실행 폴더: $NEW"
  echo "백업 폴더: $BK"
  echo "되돌리기는 README의 rollback 명령에 이 백업 폴더를 넣어 실행: $BK"
else
  echo '새 폴더 확인에 실패해 이전 폴더로 되돌립니다.'
  cp -a "$BK/zzzz-recruit-working-directory.conf.bak" "$CONF"
  systemctl daemon-reload; systemctl restart gmm-server
  if healthy; then echo "되돌림 완료: 이전 폴더($OLD)로 정상 실행 중. 새 폴더는 확인용으로 남겨 둠: $REL_NEW"; else echo '되돌림 후에도 확인 실패. 백업: '"$BK"; fi
  exit 1
fi
rm -rf "$TMP"
