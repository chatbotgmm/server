#!/usr/bin/env bash
# 랜덤 디펜스를 최신 판으로 한 번에 올리기. root로 실행: sudo bash defense-update.sh
# - 지금 서버 파일 지문으로 단계(10차~15차 등)를 알아내고, 아는 단계면 바로 최신 판(deploy/defense-latest)으로 바꾼다.
#   이미 최신이면 아무것도 안 한다. 모르는 상태면 아무것도 바꾸지 않고 멈춘다.
# - 실행 폴더를 복사하지 않는다(디스크 거의 안 씀). 바꾸는 파일만 백업하고, 서버 코드(site.js)가 바뀔 때만 재시작한다.
# - 확인에 실패하면 바로 되돌린다. DB·설정은 건드리지 않는다.
set -Eeuo pipefail
umask 077
[ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
REPO=/home/ubuntu/gmm
REL=/home/ubuntu/gmm-releases
SRCDIR=deploy/defense-latest
PATHS="dist/src/my.js dist/src/site.js public/defense/index.html public/defense/defense.css public/defense/defense.js public/defense/engine.js public/defense/data.js public/defense/bgm.mp3"
TMP=$(mktemp -d /tmp/gmm-defense-XXXXXX)
say(){ printf '%s\n' "▶ $*"; }
sha(){ [ -f "$1" ] && sha256sum "$1" | cut -d' ' -f1 || echo -; }
fp(){ local p; for p in $PATHS; do printf '%s  %s\n' "$(sha "$CUR/$p")" "$p"; done | sha256sum | cut -d' ' -f1; }
healthy(){ for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:3000/health 2>/dev/null | grep -q '"ok":true' && curl -fsS --max-time 3 http://127.0.0.1:3000/ready 2>/dev/null | grep -q '"ready":true'; then return 0; fi; sleep 1; done; return 1; }
served(){ curl -fsS --max-time 15 "http://127.0.0.1:3000$1" 2>/dev/null | sha256sum | cut -d' ' -f1; }
check(){ local i miss v e; for i in $(seq 1 10); do miss=''
  v=$(grep -oE 'defense\.js\?v=[0-9a-f]+' "$CUR/public/defense/index.html" | cut -d= -f2 || true)
  e=$(grep -oE 'engine\.js\?v=[0-9a-f]+' "$CUR/public/defense/defense.js" | cut -d= -f2 || true)
  [ "$(served /defense)" = "$(sha "$CUR/public/defense/index.html")" ] || miss+=' 디펜스화면'
  [ "$(served "/defense/defense.js?v=$v")" = "$(sha "$CUR/public/defense/defense.js")" ] || miss+=' 디펜스화면코드'
  [ "$(served "/defense/engine.js?v=$e")" = "$(sha "$CUR/public/defense/engine.js")" ] || miss+=' 디펜스규칙'
  if [ -f "$CUR/public/defense/bgm.mp3" ]; then [ "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' -r 0-99 http://127.0.0.1:3000/defense/bgm.mp3 2>/dev/null)" = 206 ] || miss+=' 배경음악'; fi
  curl -fsS --max-time 10 http://127.0.0.1:3000/about 2>/dev/null | grep -q 'site/harbor.css' || miss+=' 사이트'
  curl -fsS --max-time 10 http://127.0.0.1:3000/game60 2>/dev/null | grep -q '/game60/game.js' || miss+=' 웹게임'
  curl -sS --max-time 10 -X POST -H 'x-gmm: 1' -H 'content-type: application/json' -d '{"ticket":"x","secret":"y"}' http://127.0.0.1:3000/my/api/app/kakao-poll 2>/dev/null | grep -q '로그인 요청을 찾지 못했어요' || miss+=' 게임카카오로그인'
  curl -sS --max-time 10 -X POST -H 'content-type: application/json' -d '{"code":"x"}' http://127.0.0.1:3000/defense/room/join 2>/dev/null | grep -q '6자리' || miss+=' 같이하기방'
  [ -z "$miss" ] && return 0; sleep 2; done
  echo "확인 실패 항목:$miss"; return 1; }

say '1/6 사전 확인'
CUR=$(systemctl show gmm-server -p WorkingDirectory --value)
case "$CUR" in "$REL"/?*) ;; *) echo "실행 폴더가 $REL 아래가 아니라 중단: $CUR"; exit 1;; esac
systemctl is-active --quiet gmm-server || { echo '서버가 지금 실행 중이 아니라 중단'; exit 1; }
free=$(df -Pk "$CUR" | awk 'NR==2{print $4}'); [ "$free" -gt 30720 ] || { echo "디스크 여유 부족(남음 ${free}KB, 30MB 필요)으로 중단. deploy/tools/cleanup-releases.sh 로 정리해 주세요."; exit 1; }

say '2/6 최신 판 받기(읽기)'
runuser -u ubuntu -- git -C "$REPO" fetch -q origin manual/home-assembly-20261019
for f in defense-latest.tar.gz defense-latest.tar.gz.sha256 files.sha256 known-states.txt latest-state.txt; do runuser -u ubuntu -- git -C "$REPO" show "FETCH_HEAD:$SRCDIR/$f" > "$TMP/$f"; done
(cd "$TMP" && sha256sum -c --quiet defense-latest.tar.gz.sha256) || { echo '묶음 지문이 달라 중단'; exit 1; }

say '3/6 지금 단계 알아보기'
now=$(fp)
if grep -q "^$now " "$TMP/latest-state.txt"; then echo '이미 최신이에요. 바꿀 것이 없어요.'; rm -rf "$TMP"; exit 0; fi
stage=$(awk -v f="$now" '$1==f{ $1=""; sub(/^ /,""); print; exit }' "$TMP/known-states.txt")
[ -n "$stage" ] || { echo '지금 서버 파일이 아는 단계(10차~최신)와 달라 중단(아무것도 바꾸지 않음). 화면을 캡처해 알려 주세요.'; for p in $PATHS; do printf '  %s %s\n' "$(sha "$CUR/$p" | cut -c1-12)" "$p"; done; exit 1; }
echo "지금 단계: $stage → 최신으로 올립니다"
for p in $PATHS; do [ ! -L "$CUR/$p" ] || { echo "바로가기라 중단: $p"; exit 1; }; done

say '4/6 바꿀 파일 백업 → 최신 파일로 바꾸기'
BK=/home/ubuntu/gmm-deploy-backups/defense-update-$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 700 -o ubuntu -g ubuntu "$BK"
echo "$CUR" > "$BK/working-directory.txt"; echo "$stage" > "$BK/stage.txt"
mkdir -p "$TMP/x"; tar -xzf "$TMP/defense-latest.tar.gz" -C "$TMP/x" --no-same-owner
(cd "$TMP/x" && sha256sum -c --quiet "$TMP/files.sha256") || { echo '풀린 파일 지문이 달라 중단'; exit 1; }
LIST=$(cd "$TMP/x" && find . -type f | sed 's#^\./##' | sort)
site_changed=no
: > "$BK/new-files.txt"
for f in $LIST; do
  if [ -f "$CUR/$f" ]; then install -d -m 700 "$BK/files/$(dirname "$f")"; cp -a "$CUR/$f" "$BK/files/$f"; else echo "$f" >> "$BK/new-files.txt"; fi
  [ "$f" = dist/src/site.js ] && ! cmp -s "$TMP/x/$f" "$CUR/$f" && site_changed=yes
done
echo "$site_changed" > "$BK/site-changed.txt"
restore(){ local f; for f in $LIST; do if [ -f "$BK/files/$f" ]; then cp -a "$BK/files/$f" "$CUR/$f.restore" && mv -f "$CUR/$f.restore" "$CUR/$f"; fi; done
  while IFS= read -r f; do [ -n "$f" ] && rm -f -- "${CUR:?}/$f"; done < "$BK/new-files.txt"; }
for f in $LIST; do install -d -m 755 -o ubuntu -g ubuntu "$CUR/$(dirname "$f")"; install -m 644 -o ubuntu -g ubuntu "$TMP/x/$f" "$CUR/$f.new"; mv -f "$CUR/$f.new" "$CUR/$f"; done
for f in $LIST; do cmp -s "$TMP/x/$f" "$CUR/$f" || { echo "바꾸기 확인 실패: $f → 되돌림"; restore; exit 1; }; done

say "5/6 $( [ "$site_changed" = yes ] && echo '서버 재시작(서버 코드가 바뀜)' || echo '재시작 필요 없음')"
[ "$site_changed" = yes ] && { systemctl restart gmm-server || true; }

say '6/6 확인'
if healthy && check; then
  say "완료: 랜덤 디펜스 최신 판 적용($stage → 최신)"
  echo "백업 폴더: $BK"
  echo "되돌리기: deploy/tools/defense-rollback.sh 에 이 백업 폴더를 넣어 실행"
else
  echo '확인에 실패해 이전 파일로 되돌립니다.'; restore
  [ "$site_changed" = yes ] && { systemctl restart gmm-server || true; }
  if healthy; then echo "되돌림 완료($stage 상태로 정상 실행 중)."; else echo '되돌림 뒤에도 서버 확인 실패. 백업: '"$BK"; fi
  exit 1
fi
rm -rf "$TMP"
