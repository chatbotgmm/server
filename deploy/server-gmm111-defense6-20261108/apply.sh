#!/usr/bin/env bash
# 15차: 랜덤 디펜스 — 외부탐사 처음부터(앞 장을 끝내면 다음 장) · 등급별 강화 9칸(커먼·언커먼 묶음, 레어부터 등급마다) ·
#       위습 찾기는 상위 갈매미만(거장·금각·가루다·리치·여명) · 배경음악. root로 실행: sudo bash apply.sh
# 14차 패치(gmm111-defense5-20261108)가 적용된 상태에서만 동작한다.
# 가벼운 패치: 실행 폴더를 복사하지 않는다. 지금 실행 폴더에서 dist/src/site.js(배경음악 mp3를 내보내게, 부분 요청 지원)와
#   public/defense 파일 3개(index.html·defense.js·engine.js)를 바꾸고 bgm.mp3를 더한 뒤 서버를 재시작한다.
#   DB·설정은 바꾸지 않는다. 바꾸기 전 파일은 백업하고, 확인에 실패하면 되돌리고 다시 재시작한다.
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
NAME=gmm111-defense6-20261108
BUNDLE=galmaegiman-server-patch-$NAME.tar.gz
BUNDLE_SHA=aad4e55eda23398209b457796d786390a6a762e3c90e815ea70330fab44770c8
EXPECT=/home/ubuntu/gmm-releases/gmm111-defense-20261108/galmaegiman-confirmed
REPO=/home/ubuntu/gmm
BK=/home/ubuntu/gmm-deploy-backups/$NAME-$(date -u +%Y%m%dT%H%M%SZ)
TMP=$(mktemp -d /tmp/gmm-apply-XXXXXX)
FILES="dist/src/site.js public/defense/index.html public/defense/defense.js public/defense/engine.js"
NEWF="public/defense/bgm.mp3"
say(){ printf '%s\n' "▶ $*"; }
healthy(){ for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then return 0; fi; sleep 1; done; return 1; }
has(){ local body; body=$(curl -fsS --max-time 10 "http://127.0.0.1:3000$1" 2>/dev/null) || return 1; [[ "$body" == *"$2"* ]]; }
code(){ [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$1" 2>/dev/null)" = "$2" ]; }
post_has(){ local body; body=$(curl -sS --max-time 10 -X POST -H 'x-gmm: 1' -H 'content-type: application/json' -d "$2" "http://127.0.0.1:3000$1" 2>/dev/null) || return 1; [[ "$body" == *"$3"* ]]; }
check(){ local i miss v; for i in $(seq 1 10); do miss=''
  v=$(grep -oE 'defense\.js\?v=[0-9a-f]+' "$CUR/public/defense/index.html" | cut -d= -f2)
  has /defense "/defense/defense.js?v=$v" || miss+=' 디펜스화면'
  has "/defense/defense.js?v=$v" 'musicToggle' || miss+=' 배경음악화면'
  has "/defense/engine.js?v=$(grep -oE 'engine\.js\?v=[0-9a-f]+' "$CUR/public/defense/defense.js" | cut -d= -f2)" "UPG_COUNT" || miss+=' 등급별강화9칸'
  [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{content_type}' -r 0-99 http://127.0.0.1:3000/defense/bgm.mp3 2>/dev/null)" = '206 audio/mpeg' ] || miss+=' 배경음악파일'
  has /about 'site/harbor.css' || miss+=' 사이트'
  has /game60 '/game60/game.js' || miss+=' 웹게임'
  code /defense/defense.css 200 || miss+=' 디펜스스타일'
  post_has /my/api/app/kakao-poll '{"ticket":"x","secret":"y"}' '로그인 요청을 찾지 못했어요' || miss+=' 게임카카오로그인'
  [ -z "$miss" ] && return 0; sleep 2; done
  echo "확인 실패 항목:$miss"; return 1; }

say '1/6 사전 확인'
CUR=$(systemctl show gmm-server -p WorkingDirectory --value)
[ "$CUR" = "$EXPECT" ] || { echo "실행 폴더가 예상과 달라 중단: $CUR"; exit 1; }
systemctl is-active --quiet gmm-server || { echo '서버가 지금 실행 중이 아니라 중단'; exit 1; }
for f in $FILES; do [ -f "$CUR/$f" ] && [ ! -L "$CUR/$f" ] || { echo "파일이 없거나 바로가기라 중단: $f"; exit 1; }; done
[ ! -e "$CUR/$NEWF" ] || { echo "이미 있는 파일이라 중단(이미 적용?): $NEWF"; exit 1; }
free=$(df -Pk "$CUR" | awk 'NR==2{print $4}'); [ "$free" -gt 20480 ] || { echo "디스크 여유 부족(남음 ${free}KB, 20MB 필요)으로 중단"; exit 1; }

say '2/6 묶음·기대 지문 받기(읽기)'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin manual/home-assembly-20261019
for f in "$BUNDLE" expected-before.sha256; do runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:deploy/server-$NAME/$f" > "$TMP/$f"; done
echo "$BUNDLE_SHA  $TMP/$BUNDLE" | sha256sum -c --quiet || { echo '묶음 지문이 달라 중단'; exit 1; }

say '3/6 지금 파일이 14차 상태 그대로인지 확인'
(cd "$CUR" && sha256sum -c --quiet "$TMP/expected-before.sha256") || { echo '14차 상태와 서버 파일이 달라 중단(12·13·14차를 먼저 적용했는지 확인)'; exit 1; }

say '4/6 바꾸기 전 파일 백업 → 새 파일로 바꾸기'
install -d -m 700 -o ubuntu -g ubuntu "$BK"
for f in $FILES; do install -d -m 700 "$BK/$(dirname "$f")"; cp -a "$CUR/$f" "$BK/$f"; done
echo "$CUR" > "$BK/working-directory.txt"
mkdir -p "$TMP/x"; tar -xzf "$TMP/$BUNDLE" -C "$TMP/x" --no-same-owner
restore(){ for f in $FILES; do cp -a "$BK/$f" "$CUR/$f.restore" && mv -f "$CUR/$f.restore" "$CUR/$f"; done; rm -f -- "${CUR:?}/$NEWF"; }
for f in $FILES $NEWF; do install -m 644 -o ubuntu -g ubuntu "$TMP/x/$f" "$CUR/$f.new"; mv -f "$CUR/$f.new" "$CUR/$f"; done
for f in $FILES $NEWF; do cmp -s "$TMP/x/$f" "$CUR/$f" || { echo "바꾸기 확인 실패: $f → 되돌림"; restore; exit 1; }; done

say '5/6 서버 재시작(site.js가 바뀜)'
systemctl restart gmm-server || true

say '6/6 확인'
if healthy && check; then
  say '완료: 디펜스 15차(외부탐사 처음부터·등급별 강화 9칸·상위 위습 찾기·배경음악)'
  echo "백업 폴더: $BK"
  echo "되돌리기는 README의 rollback 명령에 이 백업 폴더를 넣어 실행: $BK"
else
  echo '확인에 실패해 이전 파일로 되돌리고 다시 재시작합니다.'; restore
  systemctl restart gmm-server || true
  if healthy; then echo '되돌림 완료(14차 상태로 정상 실행 중).'; else echo '되돌림 뒤에도 서버 확인 실패. 백업: '"$BK"; fi
  exit 1
fi
rm -rf "$TMP"
