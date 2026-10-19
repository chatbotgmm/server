#!/usr/bin/env bash
# 디자인 검토용 격리 서버(127.0.0.1:3999)와 임시 SQLite를 만들고, 심사 계정에 시험용 재화·갈매미를 넣는다.
# 운영 서버·DB와 무관하다. 결과: <작업폴더>/review.db, <작업폴더>/token.txt
# 사용: bash scripts/verify/review-server.sh <작업폴더>   (galmaegiman-confirmed 폴더에서, npm ci 후)
# 그다음 앱 화면 캡처(app/android에서):
#   ./gradlew :app:testDebugUnitTest --tests 'org.dpdns.gmm.NativeRenderReviewTest.captureAllScreens' \
#     -PnativeServer=http://127.0.0.1:3999 -PnativeReviewTokenFile=<작업폴더>/token.txt -PnativeReviewOutput=<결과폴더>
set -euo pipefail
W="$(cd "${1:?작업 폴더를 주세요}" && pwd)"
cd "$(dirname "$0")/../.."
export DATABASE_URL="file:$W/review.db"
[ -f "$W/review.db" ] || { touch "$W/review.db"; node node_modules/prisma/build/index.js db push --skip-generate >/dev/null; node --import tsx prisma/seed.ts; }
HOST=127.0.0.1 PORT=3999 SKILL_SECRET="$(openssl rand -hex 32)" REVIEW_CODE=135790 LOG_ENABLED=false \
  node --import tsx src/server.ts > "$W/server.log" 2>&1 &
for _ in $(seq 30); do curl -sf http://127.0.0.1:3999/health >/dev/null && break; sleep 1; done
H=(-H 'x-gmm: 1' -H 'x-gmm-app: 1' -H 'content-type: application/json')
curl -s -X POST http://127.0.0.1:3999/my/api/login "${H[@]}" -d '{"code":"135790"}' \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>process.stdout.write(JSON.parse(s).token))' > "$W/token.txt"
node --input-type=module <<'JS'
import {PrismaClient} from '@prisma/client';
const db=new PrismaClient();
const p=await db.player.findFirst({where:{identity:'review:store'}});
await db.player.update({where:{id:p.id},data:{credits:400,snack:30000,peanut:40,nickname:'검토대장',gameTutorial:9,tutorial:4}});
if(!await db.ownedCharacter.count({where:{playerId:p.id,obtainedVia:'review-seed'}}))
  for(const characterId of ['D1','T1','E1','I1','D2','D7','E2','T2','I2','E8','T7','D5'])
    await db.ownedCharacter.create({data:{playerId:p.id,characterId,obtainedVia:'review-seed'}});
await db.$disconnect();
JS
T="$(cat "$W/token.txt")"
for k in HIGH HIGH MID LOW; do curl -s -X POST http://127.0.0.1:3999/my/api/draw "${H[@]}" -H "x-gmm-s: $T" -d "{\"kind\":\"$k\",\"times\":10}" >/dev/null; done
echo "검토 서버 준비: http://127.0.0.1:3999 · 토큰 $W/token.txt"
