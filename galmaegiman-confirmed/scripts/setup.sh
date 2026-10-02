#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
if [ "$(node -p 'Number(process.versions.node.split(".")[0]) >= 22')" != true ]; then
  echo 'Ubuntu 안의 Node.js 22 이상이 필요합니다. README를 확인하세요.'; exit 1
fi
mkdir -p storage config backups
node scripts/init-env.mjs
database_url="$(node --env-file=.env -p 'process.env.DATABASE_URL')"
if [ "$database_url" != 'file:../storage/confirmed.db' ]; then
  echo '안전장치: 설치판은 storage/confirmed.db만 사용합니다. 이전 DB 경로를 복사하지 마세요.'; exit 1
fi
npm ci --no-audit --no-fund
npm run db:generate
if [ -f storage/confirmed.db ]; then
  edition="$(node scripts/db-tools.mjs inspect storage/confirmed.db)"
  if [ "$edition" != galmaemi-118-v1 ]; then
    echo '이전 판(75종) DB이거나 미완료 DB입니다. 자동 변경을 중단했습니다.'
    echo '개편판은 유저 데이터를 초기화합니다: 서버를 멈춘 뒤 npm run reset-data -- --confirm'; exit 1
  fi
  bash scripts/backup.sh
fi
node scripts/init-db.mjs
node --env-file=.env node_modules/prisma/build/index.js db push
node --env-file=.env --import tsx prisma/seed.ts
npm run data:check
npm run build
npm test
echo '설치 완료. 서버: npm start / 다른 Ubuntu 세션에서 HTTPS: npm run tunnel'
echo '카카오에 입력할 값은 node scripts/show-connection.mjs 로 확인하세요.'
