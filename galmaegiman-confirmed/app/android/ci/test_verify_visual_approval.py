import errno
import time
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
from verify_visual_approval import MARKER, SCHEMA, fingerprint, verify

def cleanup_fixture(tmp):
    # Retry only the observed concurrent-directory cleanup race. Keep all other
    # errors, and a persistent ENOTEMPTY, visible as test failures.
    for attempt in range(5):
        try:
            tmp.cleanup()
            return
        except OSError as error:
            if error.errno != errno.ENOTEMPTY or attempt == 4:
                raise
            time.sleep(0.1)

class VisualApprovalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(cleanup_fixture, self.tmp)
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        (self.root/'source.java').write_text('source v1')
        (self.root/'asset.png').write_bytes(b'asset v1')
        self.commit()
        self.base = self.git('rev-parse', 'HEAD').strip()
        self.marker = self.root / MARKER
    def git(self, *args):
        return subprocess.check_output(['git', '-c', 'maintenance.auto=false', '-c', 'gc.auto=0', '-c', 'gc.autoDetach=false', '-C', str(self.root), *args]).decode()
    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')
    def approve(self):
        data = dict(schema=SCHEMA, reviewed_source_commit=self.base,
                    source_tree_sha256=fingerprint(self.root,self.base),
                    reviewed_run_url='https://github.com/chatbotgmm/server/actions/runs/123',
                    reviewed_evidence_sha256='a'*64, reviewer='visual reviewer',
                    native_images_and_motion_reviewed=True)
        self.marker.parent.mkdir(parents=True, exist_ok=True)
        self.marker.write_text(json.dumps(data))
        self.commit()
        return data
    @patch('test_verify_visual_approval.time.sleep')
    def test_cleanup_retries_only_transient_nonempty_directory(self, sleep):
        tmp=Mock();tmp.cleanup.side_effect=[OSError(errno.ENOTEMPTY,'fixture race'),None]
        cleanup_fixture(tmp)
        self.assertEqual(tmp.cleanup.call_count,2)
        sleep.assert_called_once_with(0.1)
    @patch('test_verify_visual_approval.time.sleep')
    def test_cleanup_never_hides_persistent_or_unrelated_errors(self, sleep):
        for code,calls in ((errno.ENOTEMPTY,5),(errno.EACCES,1)):
            tmp=Mock();tmp.cleanup.side_effect=OSError(code,'fixture cleanup error')
            with self.subTest(code=code), self.assertRaises(OSError):cleanup_fixture(tmp)
            self.assertEqual(tmp.cleanup.call_count,calls)
    def test_missing_approval_runs_capture_only(self):
        self.assertFalse(verify(self.root,self.marker)['approved'])
    def test_approval_only_commit_preserves_exact_tree(self):
        self.approve()
        self.assertTrue(verify(self.root,self.marker)['approved'])
    def test_every_relevant_change_invalidates_approval(self):
        self.approve()
        for path in ('source.java','asset.png','ci/verify.py','.github/workflows/home.yml','test/Test.java'):
            with self.subTest(path=path):
                target=self.root/path; target.parent.mkdir(parents=True,exist_ok=True)
                target.write_text('changed')
                self.commit()
                self.assertFalse(verify(self.root,self.marker)['approved'])
                self.git('reset','--hard','HEAD~1')
    def test_working_copy_approval_cannot_replace_committed_review(self):
        data=self.approve();data['reviewer']='injected approval'
        self.marker.write_text(json.dumps(data))
        with self.assertRaises(ValueError): verify(self.root,self.marker)
    def test_uncommitted_source_change_cannot_be_built(self):
        self.approve(); (self.root/'source.java').write_text('unreviewed working copy')
        with self.assertRaises(subprocess.CalledProcessError): verify(self.root,self.marker)
    def test_deleted_asset_invalidates_approval(self):
        self.approve(); (self.root/'asset.png').unlink(); self.commit()
        self.assertFalse(verify(self.root,self.marker)['approved'])
    def test_forged_tree_hash_does_not_approve(self):
        data=self.approve(); data['source_tree_sha256']='0'*64
        self.marker.write_text(json.dumps(data)); self.commit()
        self.assertFalse(verify(self.root,self.marker)['approved'])
    def test_missing_visual_confirmation_rejected(self):
        data=self.approve(); data['native_images_and_motion_reviewed']=False
        self.marker.write_text(json.dumps(data)); self.commit()
        with self.assertRaises(ValueError): verify(self.root,self.marker)
    def test_missing_evidence_hash_rejected(self):
        data=self.approve(); data['reviewed_evidence_sha256']=''
        self.marker.write_text(json.dumps(data)); self.commit()
        with self.assertRaises(ValueError): verify(self.root,self.marker)

if __name__ == '__main__': unittest.main()
