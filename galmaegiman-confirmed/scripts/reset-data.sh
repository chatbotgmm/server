#!/usr/bin/env bash
# v0.3 개편: 모든 유저 데이터(재화·보유 유닛·도감·탐험·기록)를 초기화합니다.
# 기존 DB는 무결성 검사를 거친 백업과 원본 파일 모두 backups/ 아래에 보관하고 삭제하지 않습니다.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
if [ "${1:-}" != "--confirm" ]; then
  echo '모든 유저 데이터를 초기화합니다. 되돌리려면 backups/ 의 보관본을 사용해야 합니다.'
  echo '게임 서버를 먼저 멈춘 뒤 실행하세요: npm run reset-data -- --confirm'
  exit 1
fi
dest="backups/reset-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$dest"
if [ -f storage/confirmed.db ]; then
  node scripts/db-tools.mjs current
  mv storage/confirmed.db* "$dest"/
  echo "기존 DB 원본 보관: $dest"
fi
# 폐지된 자동 충전 설정도 보관 위치로 옮깁니다.
if [ -f config/refill-settings.json ]; then mv config/refill-settings.json "$dest"/; fi
bash scripts/setup.sh
echo '초기화 완료. 새 DB로 서버를 다시 시작하세요.'
