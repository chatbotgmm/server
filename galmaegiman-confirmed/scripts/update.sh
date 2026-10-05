#!/usr/bin/env bash
# 서버 업데이트 한 번에: 멈춤 → git pull → setup.sh → 시작 → health 확인
# 사용: sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/update.sh
#       (새 커밋이 없어도 다시 설치하려면 끝에 -f)
# 유저 데이터는 지우지 않습니다(setup.sh가 시작 전에 DB를 백업).
set -euo pipefail

# git pull이 이 파일 자체를 바꿔도 안전하도록 전체를 함수로 읽은 뒤 실행
main() {
  local app repo branch before after force=0 service=gmm-server
  [ "${1:-}" = -f ] && force=1
  app="$(cd "$(dirname "$0")/.." && pwd)"
  repo="$(git -C "$app" rev-parse --show-toplevel)"
  branch="$(git -C "$repo" rev-parse --abbrev-ref HEAD)"

  echo "▶ 1/5 새 코드 확인 ($branch)"
  before="$(git -C "$repo" rev-parse --short HEAD)"
  git -C "$repo" fetch -q origin "$branch"
  after="$(git -C "$repo" rev-parse --short "origin/$branch")"
  if [ "$before" = "$after" ] && [ "$force" = 0 ]; then
    echo "이미 최신입니다 ($before). 다시 설치하려면: 같은 명령 끝에 -f"
    health; return 0
  fi
  echo "$before → $after"
  git -C "$repo" log --oneline "$before..$after" | head -10 || true

  echo "▶ 2/5 서버 멈춤"
  sudo systemctl stop "$service"
  # 여기부터 실패하면 서버를 다시 켜 두고 알림
  trap 'echo "❌ 업데이트 중 실패했습니다. 위 오류를 캡처해 보내 주세요. 서버는 다시 켭니다."; sudo systemctl start '"$service"' || true; exit 1' ERR

  echo "▶ 3/5 코드 받기"
  git -C "$repo" pull --ff-only origin "$branch"

  echo "▶ 4/5 설치 (setup.sh)"
  bash "$app/scripts/setup.sh"

  trap - ERR
  echo "▶ 5/5 서버 시작"
  sudo systemctl start "$service"
  health
}

health() {
  local i out
  for i in $(seq 1 15); do
    if out="$(curl -fsS http://127.0.0.1:3000/health 2>/dev/null)"; then
      echo "✅ 정상: $out"; return 0
    fi
    sleep 2
  done
  echo "❌ 30초 안에 health 응답이 없습니다. 확인: sudo journalctl -u gmm-server -n 50 --no-pager"
  return 1
}

main "$@"
