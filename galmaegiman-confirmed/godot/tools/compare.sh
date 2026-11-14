#!/usr/bin/env bash
# 웹 판 엔진과 고도 판 엔진이 같은 결과를 내는지 여러 시드·난이도로 비교한다.
# 사용(galmaegiman-confirmed 폴더에서): GODOT=<고도 실행 파일> bash godot/tools/compare.sh
# 시험용 난이도 바꾸기 값은 2진수로 딱 떨어지는 수만 쓴다(0.125 등). 고도 JSON 읽기가 0.1 같은 수의 마지막 자리를 틀릴 수 있어서.
set -euo pipefail
GODOT="${GODOT:?고도 실행 파일 경로를 GODOT에 넣어 주세요}"
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"; trap 'rm -rf "${OUT:?}"' EXIT
node tools/export_data.mjs >/dev/null
fail=0
run() {   # 시드 난이도 최대초 [난이도바꾸기JSON]
  local f="$OUT/$1-$2.jsonl"
  BOT_TOPS="${BOT_TOPS:-}" node tools/ref_bot.mjs "$1" "$2" "$3" "$f" "${4:-{\}}" | sed 's/^/  웹 판: /' 
  if ! BOT_TOPS="${BOT_TOPS:-}" SAVE_AT="${SAVE_AT:-}" "$GODOT" --headless --path . --script res://scripts/test/compare_test.gd -- "$1" "$2" "$3" "$f" "${4:-{\}}" 2>&1 | grep -E "^(결과|다름|줄 수|  \()"; then fail=1; fi
}
run 1 master 600
run 2 high 600
run 3 artisan 600
run 4 intro 4000 '{"line":0.125,"boss":0.25}'
run 5 mid 4000 '{"line":0.125,"boss":0.125}'
run 6 intro 4000 '{"line":0.0625,"boss":0.0625}'
run 7 intro 4000 '{"line":0.03125,"boss":0.03125}'
BOT_TOPS=1 run 9 mid 3000 '{"line":0.25,"boss":0.25}'
BOT_TOPS=1 run 10 high 3000 '{"line":0.25,"boss":0.25}'
SAVE_AT=900 run 8 intro 4000 '{"line":0.0625,"boss":0.0625}'
if [ $fail -ne 0 ]; then echo "비교 실패"; exit 1; fi
echo "모두 같음"
