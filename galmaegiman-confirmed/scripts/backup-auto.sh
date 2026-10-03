#!/usr/bin/env bash
# 자동 백업: DB를 backups/에 복사(무결성 확인 포함)하고, 최근 BACKUP_KEEP개(기본 7)만 남깁니다.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
KEEP="${BACKUP_KEEP:-7}"
node scripts/db-tools.mjs current
# 오래된 자동·수동 백업 정리 (backups/current-*만 대상, 최신 KEEP개 유지)
ls -1dt backups/current-* 2>/dev/null | tail -n +"$((KEEP+1))" | while read -r old; do rm -rf -- "$old"; echo "오래된 백업 삭제: $old"; done
