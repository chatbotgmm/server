import unittest
from verify_backend_revision_ci import verify
class BackendRevisionTests(unittest.TestCase):
    def test_exact_and_larger_full_run_pass(self):
        for count in (478,479,500):self.assertEqual(verify(f' Test Files 47 passed (47)\n Tests {count} passed ({count})\n'),count)
    def test_colored_summary_passes(self):
        self.assertEqual(verify('\x1b[32m Tests 478 passed (478)\x1b[0m'),478)
    def test_missing_old_lower_skipped_and_failed_are_rejected(self):
        for text in ('','Tests 457 passed (457)','Tests 478 passed | 1 skipped (479)','Tests 1 failed | 478 passed (479)','Tests 478 passed'):
            with self.subTest(text=text),self.assertRaises(ValueError):verify(text)
    def test_last_run_is_authoritative(self):
        with self.assertRaises(ValueError):verify('Tests 478 passed (478)\nTests 3 passed (3)')

    def test_workflow_keeps_full_db_run_and_pipe_failure(self):
        from pathlib import Path
        workflow=(Path(__file__).resolve().parents[4]/'.github/workflows/home-assembly-check.yml').read_text()
        backend=workflow.split('  backend:',1)[1].split('  native-home:',1)[0]
        for requirement in ('set -o pipefail','npm run test:db 2>&1 | tee backend-test.log','verify_backend_revision_ci.py backend-test.log'):
            self.assertIn(requirement,backend)
