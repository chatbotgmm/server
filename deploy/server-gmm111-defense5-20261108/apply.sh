#!/usr/bin/env bash
# 14차: 랜덤 디펜스 강화를 등급별 강화로(커먼·언커먼·레어·에픽·전설 이상 5칸, 단계마다 +14%). 사용자 요청.
# 13차 패치(gmm111-defense4-20261108, 속도 1.5배)가 적용된 상태에서만 동작한다.
# 가벼운 패치: 실행 폴더를 복사하지 않고, 지금 실행 폴더의 public/defense 파일 3개(index.html·defense.js·engine.js)만 바꾼다.
#   서버 코드·DB·설정은 바꾸지 않고 재시작도 하지 않는다(이 파일들은 요청마다 디스크에서 읽힘).
#   바꾸기 전 파일 3개는 백업 폴더에 두고, 확인에 실패하면 바로 되돌린다.
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
NAME=gmm111-defense5-20261108
BUNDLE=galmaegiman-server-patch-$NAME.tar.gz
BUNDLE_SHA=008de4de2f7ba9a813855fc43cb26eef742f29bc296a0522ffec660d2c5cae97
EXPECT=/home/ubuntu/gmm-releases/gmm111-defense-20261108/galmaegiman-confirmed
REPO=/home/ubuntu/gmm
BK=/home/ubuntu/gmm-deploy-backups/$NAME-$(date -u +%Y%m%dT%H%M%SZ)
TMP=$(mktemp -d /tmp/gmm-apply-XXXXXX)
FILES="public/defense/index.html public/defense/defense.js public/defense/engine.js"
say(){ printf '%s\n' "▶ $*"; }
has(){ local body; body=$(curl -fsS --max-time 10 "http://127.0.0.1:3000$1" 2>/dev/null) || return 1; [[ "$body" == *"$2"* ]]; }
code(){ [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:3000$1" 2>/dev/null)" = "$2" ]; }
post_has(){ local body; body=$(curl -sS --max-time 10 -X POST -H 'x-gmm: 1' -H 'content-type: application/json' -d "$2" "http://127.0.0.1:3000$1" 2>/dev/null) || return 1; [[ "$body" == *"$3"* ]]; }
check(){ local i miss; for i in $(seq 1 5); do miss=''
  curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' || miss+=' 서버상태'
  has /defense '/defense/defense.js?v=' || miss+=' 디펜스화면(지문주소)'
  has "/defense/engine.js?v=$(grep -oE 'engine\.js\?v=[0-9a-f]+' "$CUR/public/defense/defense.js" | cut -d= -f2)" "data.js?v=" || miss+=' 디펜스지문주소'
  has /defense/engine.js 'WISP_GRADES' || miss+=' 디펜스2판규칙'
  has "/defense/defense.js?v=$(grep -oE 'defense\.js\?v=[0-9a-f]+' "$CUR/public/defense/index.html" | cut -d= -f2)" 'drawUpgrade' || miss+=' 디펜스등급강화화면'
  has "/defense/engine.js?v=$(grep -oE 'engine\.js\?v=[0-9a-f]+' "$CUR/public/defense/defense.js" | cut -d= -f2)" 'UPG_NAME' || miss+=' 디펜스등급강화규칙'
  has /defense/data.js '"hidden":1' || miss+=' 디펜스2판자료'
  code /defense/defense.css 200 || miss+=' 디펜스스타일'
  has /game60 '/game60/game.js' || miss+=' 웹게임'
  post_has /my/api/app/kakao-poll '{"ticket":"x","secret":"y"}' '로그인 요청을 찾지 못했어요' || miss+=' 게임카카오로그인'
  [ -z "$miss" ] && return 0; sleep 2; done
  echo "확인 실패 항목:$miss"; return 1; }

say '1/5 사전 확인'
CUR=$(systemctl show gmm-server -p WorkingDirectory --value)
[ "$CUR" = "$EXPECT" ] || { echo "실행 폴더가 예상과 달라 중단: $CUR"; exit 1; }
systemctl is-active --quiet gmm-server || { echo '서버가 지금 실행 중이 아니라 중단'; exit 1; }
for f in $FILES; do [ -f "$CUR/$f" ] && [ ! -L "$CUR/$f" ] || { echo "파일이 없거나 바로가기라 중단: $f"; exit 1; }; done

say '2/5 묶음·기대 지문 받기(읽기)'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin manual/home-assembly-20261019
for f in "$BUNDLE" expected-before.sha256; do runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:deploy/server-$NAME/$f" > "$TMP/$f"; done
echo "$BUNDLE_SHA  $TMP/$BUNDLE" | sha256sum -c --quiet || { echo '묶음 지문이 달라 중단'; exit 1; }

say '3/5 지금 파일이 13차 상태 그대로인지 확인'
(cd "$CUR" && sha256sum -c --quiet "$TMP/expected-before.sha256") || { echo '13차 상태와 서버 파일이 달라 중단(12·13차를 먼저 적용했는지 확인)'; exit 1; }

say '4/5 바꾸기 전 파일 3개 백업 → 새 파일로 바꾸기'
install -d -m 700 -o ubuntu -g ubuntu "$BK"
for f in $FILES; do cp -a "$CUR/$f" "$BK/$(basename "$f")"; done
echo "$CUR" > "$BK/working-directory.txt"
mkdir -p "$TMP/x"; tar -xzf "$TMP/$BUNDLE" -C "$TMP/x" --no-same-owner
restore(){ for f in $FILES; do cp -a "$BK/$(basename "$f")" "$CUR/$f.restore" && mv -f "$CUR/$f.restore" "$CUR/$f"; done; }
for f in $FILES; do
  install -m 644 -o ubuntu -g ubuntu "$TMP/x/$f" "$CUR/$f.new"
  mv -f "$CUR/$f.new" "$CUR/$f"
done
for f in $FILES; do cmp -s "$TMP/x/$f" "$CUR/$f" || { echo "바꾸기 확인 실패: $f → 되돌림"; restore; exit 1; }; done

say '5/5 확인'
if check; then
  say '완료: 디펜스 등급별 강화(재시작 없음). /defense를 새로 열면 적용돼요.'
  echo "백업 폴더: $BK"
  echo "되돌리기는 README의 rollback 명령에 이 백업 폴더를 넣어 실행: $BK"
else
  echo '확인에 실패해 이전 파일로 되돌립니다.'; restore
  if has /defense "defense.js?v=305691830c23"; then echo '되돌림 완료(13차 상태).'; else echo '되돌림 뒤 확인도 실패. 백업: '"$BK"; fi
  exit 1
fi
rm -rf "$TMP"
