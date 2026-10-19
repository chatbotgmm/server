#!/usr/bin/env bash
# 서버 업데이트 한 번에: 코드 받기 → (서버 켠 채로) 설치·빌드·테스트 → 잠깐 멈춤 → DB 백업·반영 → 시작 → 확인
# 사용: sudo -iu ubuntu bash gmm/galmaegiman-confirmed/scripts/update.sh
#       (새 커밋이 없어도 다시 설치하려면 끝에 -f)
# 유저 데이터는 지우지 않습니다(DB 반영 전에 백업).
set -euo pipefail

# git pull이 이 파일 자체를 바꿔도 안전하도록 전체를 함수로 읽은 뒤 실행
main() {
  local app repo branch before after force=0 service=gmm-server
  [ "${1:-}" = -f ] && force=1
  app="$(cd "$(dirname "$0")/.." && pwd)"
  repo="$(git -C "$app" rev-parse --show-toplevel)"
  branch="$(git -C "$repo" rev-parse --abbrev-ref HEAD)"

  echo "▶ 1/5 새 코드 확인 ($branch)"
  before="$(git -C "$repo" rev-parse HEAD)"
  git -C "$repo" fetch -q origin "$branch"
  after="$(git -C "$repo" rev-parse "origin/$branch")"
  apk "$repo" "$app"
  if [ "$before" = "$after" ] && [ "$force" = 0 ]; then
    echo "이미 최신입니다 (${before:0:7}). 다시 설치하려면: 같은 명령 끝에 -f"
    health; checks; return 0
  fi
  echo "${before:0:7} → ${after:0:7}"
  git -C "$repo" log --oneline "$before..$after" | head -10 || true

  echo "▶ 2/5 코드 받기·설치·빌드·테스트 (서버는 켜 둔 채로)"
  git -C "$repo" pull -q --ff-only origin "$branch"
  if ! bash "$app/scripts/setup.sh" --prepare; then
    echo "❌ 설치·테스트에 실패했습니다. 서버는 멈추지 않았고, 코드를 이전 버전(${before:0:7})으로 되돌립니다."
    git -C "$repo" reset -q --hard "$before"
    bash "$app/scripts/setup.sh" --prepare >/dev/null 2>&1 || echo "⚠ 이전 버전 다시 빌드도 실패했습니다. 위 오류를 캡처해 보내 주세요."
    exit 1
  fi

  echo "▶ 3/5 서버 잠깐 멈춤"
  sudo systemctl stop "$service"
  # 여기부터 실패하면 서버를 다시 켜 두고 알림
  trap 'echo "❌ DB 반영 중 실패했습니다. 위 오류를 캡처해 보내 주세요. 서버는 다시 켭니다."; sudo systemctl start '"$service"' || true; exit 1' ERR

  echo "▶ 4/5 DB 백업·반영"
  bash "$app/scripts/setup.sh" --apply

  trap - ERR
  echo "▶ 5/5 서버 시작"
  sudo systemctl start "$service"
  health
  checks
}

# 안드로이드 앱 파일: 저장소를 키우지 않으려고 작업 브랜치가 아닌 apk 브랜치(최신 1개만)에 둠 → public/app/ 로 꺼냄(git 제외)
# 코드 변경이 없어도 새 APK만 올라왔을 수 있어서 매번 확인. 실패해도 업데이트는 계속
apk() {
  local repo="$1" app="$2" dir="$2/public/app"
  git -C "$repo" fetch -q origin apk 2>/dev/null || { echo "ℹ apk 브랜치 없음(앱 받기 칸 숨김)"; return 0; }
  mkdir -p "$dir"
  if git -C "$repo" show origin/apk:apk.json > "$dir/apk.json.new" 2>/dev/null && ! cmp -s "$dir/apk.json.new" "$dir/apk.json" \
     && git -C "$repo" show origin/apk:galmaegiman-suhbidae.apk > "$dir/galmaegiman-suhbidae.apk.new"; then
    mv "$dir/galmaegiman-suhbidae.apk.new" "$dir/galmaegiman-suhbidae.apk" && mv "$dir/apk.json.new" "$dir/apk.json"
    echo "📲 앱 파일 갱신: $(grep -o '"versionName": "[^"]*"' "$dir/apk.json")"
  fi
  # 네이티브 시험판은 기존 APK와 별도 파일로 보존한다. FETCH_HEAD는 단일 브랜치 fetch에도 유효하다.
  if git -C "$repo" show FETCH_HEAD:native.json > "$dir/native.json.new" 2>/dev/null \
     && git -C "$repo" show FETCH_HEAD:galmaegiman-native.apk > "$dir/galmaegiman-native.apk.new" 2>/dev/null; then
    if node --input-type=module -e 'import fs from "node:fs";import{createHash}from"node:crypto";const d=process.argv[1],m=JSON.parse(fs.readFileSync(d+"/native.json.new")),b=fs.readFileSync(d+"/galmaegiman-native.apk.new");if(b.length!==m.size||createHash("sha256").update(b).digest("hex")!==m.sha256)process.exit(1)' "$dir"; then
      # 별도 설치판으로 전환할 때 이전 네이티브 APK도 그대로 남긴다.
      if [ -f "$dir/native.json" ] && [ -f "$dir/galmaegiman-native.apk" ]; then
        if node --input-type=module -e 'import fs from "node:fs";const d=process.argv[1],old=JSON.parse(fs.readFileSync(d+"/native.json")),next=JSON.parse(fs.readFileSync(d+"/native.json.new"));process.exit(old.applicationId!==next.applicationId?0:1)' "$dir"; then
          cp "$dir/galmaegiman-native.apk" "$dir/galmaegiman-native-66.apk"
          cp "$dir/native.json" "$dir/native-66.json"
        fi
      fi
      mv "$dir/galmaegiman-native.apk.new" "$dir/galmaegiman-native.apk"
      mv "$dir/native.json.new" "$dir/native.json"
      echo "📲 네이티브 앱 파일 확인 완료"
    fi
  fi
  rm -f "$dir"/*.new
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

# 운영 점검: 자동 백업 타이머가 켜져 있는지
checks() {
  if ! systemctl is-active --quiet gmm-backup.timer 2>/dev/null; then
    echo "⚠ 자동 백업 타이머(gmm-backup.timer)가 꺼져 있습니다. 켜기: sudo bash $(cd "$(dirname "$0")" && pwd)/install-backup-timer.sh"
  fi
}

main "$@"
