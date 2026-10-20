#!/usr/bin/env bash
# localhost 전용 합성 fixture. 운영 .env/DB/심사 비밀번호를 사용하지 않습니다.
# 사용: bash scripts/verify/review-server.sh [--check]
# 실행 중 출력되는 임시 폴더의 token.txt를 화면 캡처 도구에 전달. 종료하면 삭제됩니다.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec env -i PATH="$PATH" node --import tsx scripts/verify/review-fixture.ts "$@"
