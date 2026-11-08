#!/usr/bin/env bash
# 오래된 실행 폴더 정리(디스크 여유 확보). root로 실행: sudo bash cleanup-releases.sh
# 남기는 것(지우지 않음):
#   1) 지금 실행 중인 폴더(gmm-server의 WorkingDirectory)
#   2) 마지막 패치 백업에 적힌 이전 실행 폴더(되돌리기용)
#   3) 위 폴더 안의 바로가기(심볼릭 링크)가 가리키는 폴더, 그 폴더가 다시 가리키는 폴더까지 전부
#   4) systemd·cron 설정에 적힌 폴더(다른 서비스용)
# 그 밖의 /home/ubuntu/gmm-releases/* 폴더만 목록·크기를 보여 주고, yes를 입력해야 지운다.
# DB(/home/ubuntu/gmm/...)·패치 백업 폴더·저장소는 건드리지 않는다.
set -Eeuo pipefail
REL=${GMM_REL:-/home/ubuntu/gmm-releases}
BKS=${GMM_BKS:-/home/ubuntu/gmm-deploy-backups}
if [ -z "${GMM_CUR:-}" ]; then
  [ "$(id -u)" = 0 ] || { echo 'sudo bash 로 실행해 주세요.'; exit 1; }
  CUR=$(systemctl show gmm-server -p WorkingDirectory --value)
else CUR=$GMM_CUR; fi
case "$CUR" in "$REL"/?*) ;; *) echo "실행 폴더가 $REL 아래가 아니라 중단: $CUR"; exit 1;; esac
[ -d "$CUR" ] || { echo "실행 폴더가 없어 중단: $CUR"; exit 1; }
top(){ local p=${1#"$REL"/}; printf '%s/%s\n' "$REL" "${p%%/*}"; }

declare -A keep=()
queue=("$(top "$CUR")")
last_bk=$(ls -1dt "$BKS"/*/ 2>/dev/null | head -1 || true)
if [ -n "$last_bk" ] && [ -f "$last_bk/old-working-directory.txt" ]; then
  o=$(cat "$last_bk/old-working-directory.txt")
  case "$o" in "$REL"/?*) [ -d "$o" ] && queue+=("$(top "$o")");; esac
fi
# 4) 시스템 설정(systemd·cron)에 적힌 실행 폴더도 남긴다(다른 서비스가 쓰고 있을 수 있음)
while IFS= read -r t; do [ -n "$t" ] && [ -d "$t" ] && queue+=("$(top "$t")"); done < <(grep -rhoE "$REL/[^/\"' ]+" ${GMM_CONF:-/etc/systemd/system /etc/cron.d /etc/crontab /var/spool/cron} 2>/dev/null | sort -u || true)
echo "▶ 남길 폴더 찾는 중(바로가기 따라가기)…"
while [ ${#queue[@]} -gt 0 ]; do
  d=${queue[0]}; queue=("${queue[@]:1}")
  [ -n "${keep[$d]:-}" ] && continue
  [ -d "$d" ] || continue
  keep[$d]=1
  while IFS= read -r t; do
    case "$t" in "$REL"/?*) n=$(top "$t"); [ -z "${keep[$n]:-}" ] && queue+=("$n");; esac
  done < <(find "$d" -xdev -type l -exec readlink -f {} + 2>/dev/null | sort -u)
done
echo "남김:"; for d in "${!keep[@]}"; do printf '  %s\n' "$d"; done | sort

cands=()
for d in "$REL"/*; do
  [ -d "$d" ] && [ ! -L "$d" ] || continue
  [ -n "${keep[$d]:-}" ] && continue
  cands+=("$d")
done
echo "지금 남은 공간: $(df -Ph "$REL" | awk 'NR==2{print $4}')"
if [ ${#cands[@]} -eq 0 ]; then echo "지울 수 있는 예전 실행 폴더가 없어요."; du -sh "$BKS" 2>/dev/null | sed 's/^/패치 백업 폴더 크기: /'; exit 0; fi
echo "지울 수 있는 예전 실행 폴더:"
du -sh "${cands[@]}" | sed 's/^/  /'
echo "합계: $(du -csh "${cands[@]}" | tail -1 | cut -f1)"
read -r -p '위 폴더를 지울까요? 지우려면 yes 를 입력: ' answer
[ "$answer" = yes ] || { echo '지우지 않았어요.'; exit 0; }
for d in "${cands[@]}"; do
  case "$d" in "$REL"/?*) rm -rf -- "${d:?}"; echo "지움: $d";; esac
done
echo "정리 뒤 남은 공간: $(df -Ph "$REL" | awk 'NR==2{print $4}')"
