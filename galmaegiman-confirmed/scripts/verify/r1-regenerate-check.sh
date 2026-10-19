#!/usr/bin/env bash
# r1 소스 출처 확인: 0e29db5(ZIP 방식 최종 검증 커밋)에서 gmm-rebuild-manual.yml과 같은 순서로
# 소스를 다시 생성하고, 저장소에 풀어 둔 커밋(기본 8b6f675)과 파일 단위로 비교한다.
# 저장소 작업 폴더는 건드리지 않고 임시 worktree만 쓴다. 빌드·배포·DB 작업 없음.
# 사용: bash galmaegiman-confirmed/scripts/verify/r1-regenerate-check.sh [비교할 커밋]
set -euo pipefail
REF="${1:-8b6f675}"
BASE=0e29db5f9c85d1e9d0ea007ab19fd610e954b0fc
ROOT="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)/r1"
git -C "$ROOT" worktree add -q --detach "$WORK" "$BASE"
trap 'git -C "$ROOT" worktree remove --force "$WORK"' EXIT
cd "$WORK"
sha256sum --check --strict <<'SUMS'
07b6a7edd1e591826e438b19fadbd35c1a63ee9338ef75e71f6e2a9fe5c384d0  realtime-ranked-bundle.zip
7d7f9ab428fa592e722b75391d51af8a2ffbf766dbd2320fa123c150444dbaae  casual-ai-fallback-patch.zip
654ecd007f6defcc0fbb021a3cab2aafd8454c9b5bbf44f388b6785cf5e5ace6  queue-ui-cleanup-patch.zip
56db42812042a942940fb1ceed7171348e38a152f0f04490b8fd2ff54c180c21  gmm-rebuild-r1.zip
SUMS
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
root = Path.cwd().resolve()
for name in ['realtime-ranked-bundle.zip', 'casual-ai-fallback-patch.zip', 'queue-ui-cleanup-patch.zip']:
    with ZipFile(name) as z:
        for e in z.infolist():
            p = Path(e.filename)
            if p.is_absolute() or '..' in p.parts: raise SystemExit('unsafe path')
            if not e.is_dir() and (root / p).exists(): raise SystemExit('refusing to overwrite ' + str(p))
        z.extractall(root)
with ZipFile('gmm-rebuild-r1.zip') as z:
    z.extractall(root / 'gmm-rebuild-r1')
PY
{
  python3 apply-native-parchment.py
  python3 apply-augment-layout.py
  python3 apply-augment-switch-fix.py --apply
  python3 apply-realtime-ranked.py
  python3 apply-realtime-ranked.py --apply
  python3 apply-casual-ai.py
  python3 apply-casual-ai.py --apply
  python3 apply-queue-ui-cleanup.py
  python3 apply-queue-ui-cleanup.py --apply
  python3 gmm-rebuild-r1/apply_rebuild.py --project galmaegiman-confirmed
  python3 gmm-rebuild-r1/apply_rebuild.py --project galmaegiman-confirmed --apply
} > /dev/null
same=0; diff=0
while read -r f; do
  if git -C "$ROOT" cat-file -e "$REF:$f" 2>/dev/null && cmp -s <(git -C "$ROOT" show "$REF:$f") "$f"; then
    same=$((same + 1))
  else
    diff=$((diff + 1)); echo "다름: $f"
  fi
done < <(git status --porcelain --untracked-files=all -- galmaegiman-confirmed \
  | awk '{print $2}' | grep -vE '\.bak$|gmm-rebuild-backups')
echo "같음 $same / 다름 $diff (기준 $REF)"
[ "$diff" -eq 0 ]
