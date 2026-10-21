import tempfile
import unittest
from pathlib import Path
from report_artifact_storage import report
class ArtifactStorageTests(unittest.TestCase):
    def test_first_review_uploads_only_unique_revision_pngs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'recruitment-counter').mkdir();(root/'r1').mkdir()
            (root/'recruitment-counter/low.png').write_bytes(b'a'*17);(root/'r1/old.png').write_bytes(b'b'*100)
            result=report(root)
            self.assertEqual(result['generated_native_png_bytes'],117)
            self.assertEqual(result['uploaded_review_png_bytes'],17)
            self.assertEqual(result['unique_review_png_files'],1)
    def test_approved_rerun_keeps_captures_local_but_uploads_no_pngs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'home-disabled-play-image.png').write_bytes(b'a'*17)
            self.assertEqual(report(root,True)['uploaded_review_png_bytes'],0)
            self.assertEqual((root/'home-disabled-play-image.png').read_bytes(),b'a'*17)
    def test_workflow_uploads_no_legacy_png_trees_and_expires_in_one_day(self):
        import re
        workflow=(Path(__file__).resolve().parents[4]/'.github/workflows/home-assembly-check.yml').read_text()
        self.assertNotIn('phase2-full-suite-captures-',workflow);self.assertNotIn('all-screen-review-',workflow)
        for block in re.split(r'(?=^      - )',workflow,flags=re.M):
            if 'uses: actions/upload-artifact@' not in block:continue
            self.assertIn('retention-days: 1',block)
            if re.search(r'id: revision_\w+_evidence',block):
                self.assertIn("steps.visual_approval.outputs.approved != 'true'",block)
            elif 'build/native-review/' in block:
                self.assertNotIn('.png',block)
                for line in block.splitlines():
                    if 'build/native-review/' in line:self.assertTrue(line.endswith('.json'),line)
        self.assertLess(workflow.index('id: visual_approval'),workflow.index('id: revision_counter_evidence'))
        self.assertIn('app/build/apk-identities/checksums.txt',workflow)
