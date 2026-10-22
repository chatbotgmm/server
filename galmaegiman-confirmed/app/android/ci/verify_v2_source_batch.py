#!/usr/bin/env python3
"""유한 V2/온보딩/뉴스 소스 범위만 검증. 컴파일/DB/기기 검증을 대신하지 않음."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess

BASELINE = '02a46bc58e8c031dd65e9a058e0ccec80f9e29b2'
BATCH_ID = 'v2-onboarding-news-20261021'
SCHEMA = 'reviewed-source-batch/v1'
HERE = Path(__file__).resolve().parent
LOCK = HERE / 'v2-onboarding-news-source-lock.json'
CONTROL_PATHS = frozenset('galmaegiman-confirmed/app/android/ci/' + p for p in (
    'verify_registration_ci.py', 'verify_v2_source_batch.py',
    'test_verify_v2_source_batch.py', 'v2-onboarding-news-source-lock.json'))
# BEGIN FINITE PAYLOAD PATHS — 최종 통합 후 exact 목록, 디렉터리 예외 없음.
PAYLOAD_PATHS = frozenset(['galmaegiman-confirmed/app/android/app/build.gradle', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/AugmentPresentation.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/BattleReplayArt.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/BattleReplayStore.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/BattleTypeInfo.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/BattleView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/CombatReplayCompatibility.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/FreestyleLayout.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/FreestyleSelection.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/HomeMenuLayout.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/LineupGuidance.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/LobbyView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/MainActivity.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeAlerts.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeApi.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeArt.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeAugmentView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeBackend.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeBattleReplay.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeBattleSheets.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeExtras.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeLibrary.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeMenus.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeNews.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeSettingsSheet.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeStarterLoans.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeTutorialController.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/NativeVersusView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/PartyCharacterView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/RegistrationCardView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/SkillAttributeBreakdown.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/StarterLoanSelection.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/StoryView.java', 'galmaegiman-confirmed/app/android/app/src/main/java/org/dpdns/gmm/TemporaryUnitPresentation.java', 'galmaegiman-confirmed/app/android/app/src/main/res/drawable-nodpi/user_launcher_art.png', 'galmaegiman-confirmed/app/android/app/src/main/res/drawable-nodpi/user_launcher_foreground.png', 'galmaegiman-confirmed/app/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml', 'galmaegiman-confirmed/app/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml', 'galmaegiman-confirmed/app/android/app/src/main/res/mipmap-anydpi/ic_launcher.xml', 'galmaegiman-confirmed/app/android/app/src/main/res/mipmap-anydpi/ic_launcher_round.xml', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/AugmentPresentationTest.java', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/CombatReplayCompatibilityTest.java', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/FreestyleLayoutTest.java', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/FreestyleSelectionTest.java', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/SkillAttributeBreakdownTest.java', 'galmaegiman-confirmed/app/android/app/src/test/java/org/dpdns/gmm/TemporaryUnitPresentationTest.java', 'galmaegiman-confirmed/app/android/ci/check_backend_target.groovy', 'galmaegiman-confirmed/app/android/ci/test_backend_target.py', 'galmaegiman-confirmed/data/battle.yaml', 'galmaegiman-confirmed/data/economy.yaml', 'galmaegiman-confirmed/data/units.json', 'galmaegiman-confirmed/docs/PROGRESSION.md', 'galmaegiman-confirmed/docs/STARTER-LEVEL-INHERITANCE.md', 'galmaegiman-confirmed/docs/TEMPORARY-STARTER-REVIEW.md', 'galmaegiman-confirmed/docs/handoffs/V2-ONBOARDING-NEWS-20261022.md', 'galmaegiman-confirmed/docs/migrations/v2-onboarding-news.sql', 'galmaegiman-confirmed/docs/temporary-starter-level-inheritance-migration.sql', 'galmaegiman-confirmed/docs/temporary-starter-migration.sql', 'galmaegiman-confirmed/prisma/manual/20261019_progression_additive.sql', 'galmaegiman-confirmed/prisma/schema.prisma', 'galmaegiman-confirmed/src/battle/augment-catalog-v2.ts', 'galmaegiman-confirmed/src/battle/augment-sequence.ts', 'galmaegiman-confirmed/src/battle/combat-epoch.ts', 'galmaegiman-confirmed/src/battle/core.ts', 'galmaegiman-confirmed/src/battle/engine.ts', 'galmaegiman-confirmed/src/battle/freestyle-selection.ts', 'galmaegiman-confirmed/src/battle/growth.ts', 'galmaegiman-confirmed/src/battle/public-dto.ts', 'galmaegiman-confirmed/src/battle/realtime-adapter.ts', 'galmaegiman-confirmed/src/battle/realtime-bot.ts', 'galmaegiman-confirmed/src/battle/replay.ts', 'galmaegiman-confirmed/src/battle/service-factory.ts', 'galmaegiman-confirmed/src/game.ts', 'galmaegiman-confirmed/src/my.ts', 'galmaegiman-confirmed/src/native.ts', 'galmaegiman-confirmed/src/news-routes.ts', 'galmaegiman-confirmed/src/news-sources.ts', 'galmaegiman-confirmed/src/news-store.ts', 'galmaegiman-confirmed/src/news.ts', 'galmaegiman-confirmed/src/progression.ts', 'galmaegiman-confirmed/src/pvp/realtime-native-view.ts', 'galmaegiman-confirmed/src/pvp/routes.ts', 'galmaegiman-confirmed/src/pvp/service.ts', 'galmaegiman-confirmed/src/review-auth.ts', 'galmaegiman-confirmed/src/server.ts', 'galmaegiman-confirmed/src/starter-loan-levels.ts', 'galmaegiman-confirmed/src/starter-loans.ts', 'galmaegiman-confirmed/test/augment-card-ui.test.mjs', 'galmaegiman-confirmed/test/battle-events.test.ts', 'galmaegiman-confirmed/test/casual-growth.integration.test.ts', 'galmaegiman-confirmed/test/catalog-v2.test.mjs', 'galmaegiman-confirmed/test/craft-preview.test.ts', 'galmaegiman-confirmed/test/game.integration.test.ts', 'galmaegiman-confirmed/test/native-parity.test.ts', 'galmaegiman-confirmed/test/native-type-matrix.test.ts', 'galmaegiman-confirmed/test/news-auth.integration.test.ts', 'galmaegiman-confirmed/test/news.node.ts', 'galmaegiman-confirmed/test/nickname-transaction.test.mjs', 'galmaegiman-confirmed/test/progression-migration.test.ts', 'galmaegiman-confirmed/test/progression.integration.test.ts', 'galmaegiman-confirmed/test/realtime-acceptance.integration.test.ts', 'galmaegiman-confirmed/test/realtime-casual-ai.integration.test.ts', 'galmaegiman-confirmed/test/realtime-pvp.integration.test.ts', 'galmaegiman-confirmed/test/recruitment-owned-receipts.integration.test.ts', 'galmaegiman-confirmed/test/resolve-ts.mjs', 'galmaegiman-confirmed/test/routes.node.ts', 'galmaegiman-confirmed/test/sources.node.ts', 'galmaegiman-confirmed/test/starter-loan-levels.integration.test.ts', 'galmaegiman-confirmed/test/starter-loan-levels.test.mjs', 'galmaegiman-confirmed/test/starter-loan-save-boundary.test.mjs', 'galmaegiman-confirmed/test/starter-loans.node.test.mjs', 'galmaegiman-confirmed/test/store.node.ts', 'galmaegiman-confirmed/test/temporary-loan-battle-gates.test.mjs', 'galmaegiman-confirmed/test/ts-source-loader.mjs', 'test/augment-effects.test.mjs', 'test/augment-sequence.test.mjs', 'test/freestyle-review.test.mjs', 'test/innate-factor.test.mjs', 'test/service-integration.test.mjs'])
# END FINITE PAYLOAD PATHS

def check(value, message):
    if not value:
        raise ValueError(message)

def verify_batch(root, changed, manifest):
    root = Path(root).resolve()
    check(type(manifest) is dict and set(manifest) == {'schema', 'batch_id', 'baseline_commit', 'files'}, 'Invalid batch manifest fields')
    check(manifest['schema'] == SCHEMA and manifest['batch_id'] == BATCH_ID, 'Unknown source batch')
    check(manifest['baseline_commit'] == BASELINE, 'Wrong source baseline')
    entries = manifest['files']
    check(type(entries) is list and len(entries) == len(PAYLOAD_PATHS), 'Wrong finite file count')
    indexed = {}
    for item in entries:
        check(type(item) is dict and set(item) == {'path', 'bytes', 'sha256'}, 'Invalid source entry')
        path = item['path']
        check(type(path) is str and path in PAYLOAD_PATHS and path not in indexed, 'Unknown or duplicate source path')
        pp = PurePosixPath(path)
        check(not pp.is_absolute() and '..' not in pp.parts and str(pp) == path, 'Unsafe source path')
        check(type(item['bytes']) is int and item['bytes'] > 0, 'Invalid source size')
        digest = item['sha256']
        check(type(digest) is str and len(digest) == 64 and all(c in '0123456789abcdef' for c in digest), 'Invalid source digest')
        source = root / path
        check(source.is_file() and source.resolve().is_relative_to(root), 'Missing or escaped source: ' + path)
        check(not any((root.joinpath(*pp.parts[:i])).is_symlink() for i in range(1, len(pp.parts)+1)), 'Redirected source: ' + path)
        data = source.read_bytes()
        check(len(data) == item['bytes'] and hashlib.sha256(data).hexdigest() == digest, 'Source hash mismatch: ' + path)
        indexed[path] = digest
    check(set(indexed) == PAYLOAD_PATHS, 'Incomplete finite source allowlist')
    check(all(type(path) is str for path in changed), 'Invalid changed path list')
    forbidden = set(filter(None, changed)) - PAYLOAD_PATHS - CONTROL_PATHS
    check(not forbidden, 'Unreviewed source/artifact changes: ' + ', '.join(sorted(forbidden)))
    return {'source_batch': BATCH_ID, 'source_baseline_commit': BASELINE,
            'source_batch_files': len(indexed), 'source_batch_sha256': dict(sorted(indexed.items())),
            'scope_only_not_compilation': True}

def verify_repository(root):
    root = Path(root).resolve()
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', BASELINE, 'HEAD'], check=True)
    changed = subprocess.check_output(['git', '-C', str(root), 'diff', '--name-only', '--no-renames', '-z', BASELINE, 'HEAD']).decode().split('\0')
    # Also fail on tracked worktree edits instead of inspecting a different source than HEAD.
    dirty = subprocess.check_output(['git', '-C', str(root), 'diff', '--name-only', '-z', 'HEAD']).decode().split('\0')
    check(not any(dirty), 'Tracked worktree differs from reviewed HEAD')
    return verify_batch(root, changed, json.loads(LOCK.read_text()))
