"""현재875 복구 후보 보호회귀. Android/화면 실행 증거가 아니다."""
import copy
import json
import unittest
import verify_theme_ci as theme
import verify_v2_source_batch as batch

class CurrentBuildRepairTests(unittest.TestCase):
    def setUp(self):
        self.lock = json.loads(theme.LOCK.read_text())

    def test_home_without_actual_transition_bytes_fails(self):
        with self.assertRaises(ValueError):
            theme.allowed_asset_changes([theme.HOME_PATH], self.lock)

    def test_fake_transition_fails(self):
        for before, after in [(b'', b''), (None, b''), (b'old', b'new')]:
            with self.subTest(before=before), self.assertRaises(ValueError):
                theme.allowed_asset_changes([theme.HOME_PATH], self.lock, home_transition=(before, after))

    def test_home_lock_changes_do_not_authorize_change(self):
        for field, value in [('bytes',1), ('sha256','0'*64), ('git_blob_sha','0'*40),
                             ('dimensions',[841,1870]), ('baseline',False)]:
            lock=copy.deepcopy(self.lock)
            next(e for e in lock['files'] if e['path']=='backgrounds/bg-home.png')[field]=value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError,'lock identity'):
                theme.verify_home_transition(b'',b'',lock)

    def test_wrong_source_prefix_fails(self):
        self.lock['source_prefix']+='-other'
        with self.assertRaisesRegex(ValueError,'source path'):
            theme.verify_home_transition(b'',b'',self.lock)

    def test_phase2_matches_exact_updated_theme(self):
        phase=json.loads((theme.HERE/'phase2-assets-lock.json').read_text())
        old={e['path']:e for e in phase['preserved_phase_one_files']}
        added={e['path']:e for e in phase['files']}
        self.assertEqual({e['path']:e for e in self.lock['files']},{**old,**added})
        self.assertEqual(len(old),24); self.assertEqual(len(added),1)

    def test_no_baseline_asset_wildcard(self):
        for e in self.lock['files']:
            if e['baseline']:
                with self.subTest(path=e['path']), self.assertRaises(ValueError):
                    theme.allowed_asset_changes([self.lock['source_prefix']+'/'+e['path']],self.lock)

    def test_repair_paths_are_hash_payload_not_control_exemptions(self):
        prefix='galmaegiman-confirmed/app/android/'
        for path in ['ci/theme-assets-lock.json','ci/phase2-assets-lock.json','ci/verify_theme_ci.py',
                     'ci/test_verify_theme_ci.py','ci/test_current_build_repair.py',
                     'app/src/test/java/org/dpdns/gmm/NativeStarterLoansLifecycleTest.java']:
            self.assertIn(prefix+path,batch.PAYLOAD_PATHS)
            self.assertNotIn(prefix+path,batch.CONTROL_PATHS)
        self.assertEqual(len(batch.CONTROL_PATHS),4)

    def test_tokio_only_on_backend_test_step(self):
        path='.github/workflows/home-assembly-check.yml'
        text=(theme.HERE.parents[3]/path).read_text()
        self.assertEqual(text.count('TOKIO_WORKER_THREADS:'),1)
        self.assertIn("      - name: Test all backend suites with isolated seeded SQLite\n"
                      "        env:\n"
                      "          # Four simultaneous SQLite transactions must not exhaust the engine workers.\n"
                      "          TOKIO_WORKER_THREADS: '4'\n"
                      "        run: |\n"
                      "          set -o pipefail\n"
                      "          npm run test:db 2>&1 | tee backend-test.log",text)
        self.assertIn(path,batch.PAYLOAD_PATHS); self.assertNotIn(path,batch.CONTROL_PATHS)

if __name__=='__main__': unittest.main()
