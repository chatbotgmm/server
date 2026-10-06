#!/usr/bin/env bash
# 설치·업데이트. 두 단계로 나눠 실행할 수 있습니다.
#   --prepare : 서버가 켜져 있어도 되는 일(패키지 설치·빌드·격리 테스트). DB는 건드리지 않음
#   --apply   : 서버를 멈춘 뒤 하는 일(DB 백업 → 스키마 반영 → 데이터 반영)
#   (없음)    : 둘 다 차례로(처음 설치·reset-data용)
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
phase="${1:-all}"
case "$phase" in --prepare|--apply|all) ;; *) echo '사용법: bash scripts/setup.sh [--prepare|--apply]'; exit 1;; esac

prepare() {
  if [ "$(node -p 'Number(process.versions.node.split(".")[0]) >= 22')" != true ]; then
    echo 'Ubuntu 안의 Node.js 22 이상이 필요합니다. README를 확인하세요.'; exit 1
  fi
  mkdir -p storage config backups
  node scripts/init-env.mjs
  local database_url
  database_url="$(node --env-file=.env -p 'process.env.DATABASE_URL')"
  if [ "$database_url" != 'file:../storage/confirmed.db' ]; then
    echo '안전장치: 설치판은 storage/confirmed.db만 사용합니다. 이전 DB 경로를 복사하지 마세요.'; exit 1
  fi
  npm ci --no-audit --no-fund
  npm run db:generate
  npm run data:check
  npm run build
  # 실제 DB가 아닌 임시 DB로 전체 테스트
  npm run test:db
}

apply() {
  if [ -f storage/confirmed.db ]; then
    local edition
    edition="$(node scripts/db-tools.mjs inspect storage/confirmed.db)"
    if [ "$edition" != galmaemi-118-v1 ]; then
      echo '이전 판(75종) DB이거나 미완료 DB입니다. 자동 변경을 중단했습니다.'
      echo '개편판은 유저 데이터를 초기화합니다: 서버를 멈춘 뒤 npm run reset-data -- --confirm'; exit 1
    fi
    bash scripts/backup.sh
  fi
  node scripts/init-db.mjs
  node --env-file=.env node_modules/prisma/build/index.js db push --skip-generate
  node --env-file=.env --import tsx prisma/seed.ts
}

[ "$phase" = --apply ] || prepare
[ "$phase" = --prepare ] || apply
if [ "$phase" = all ]; then
  echo '설치 완료. 서버: npm start'
  echo '카카오에 입력할 값은 node scripts/show-connection.mjs 로 확인하세요.'
fi
