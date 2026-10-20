"""검증기의 오류 탐지 테스트. 프로덕션 파일은 수정하지 않는다."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import sys
import unittest
from PIL import Image

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location('check_ui', Path(__file__).with_name('check-ui-motion-resources.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class ResourceValidation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)/'assets'
        shutil.copytree(check.DEFAULT, self.root)
        self.manifest = json.loads((self.root/'runtime.json').read_text())

    def save(self):
        (self.root/'runtime.json').write_text(json.dumps(self.manifest))

    def test_valid_bundle(self):
        self.assertEqual(len(check.validate(self.root)), 24)

    def test_traversal(self):
        self.manifest['buttons'][0]['file'] = '../secret.png'
        self.save()
        with self.assertRaisesRegex(ValueError, '경로'):
            check.validate(self.root)

    def test_wrong_dimensions(self):
        self.manifest['buttons'][0]['size'][0] += 1
        self.save()
        with self.assertRaisesRegex(ValueError, '크기 불일치'):
            check.validate(self.root)

    def test_missing_file(self):
        (self.root/self.manifest['buttons'][0]['file']).unlink()
        with self.assertRaisesRegex(ValueError, '누락 PNG'):
            check.validate(self.root)

    def test_opaque_replacement(self):
        path = self.root/self.manifest['buttons'][0]['file']
        with Image.open(path) as im:
            im.convert('RGB').save(path)
        with self.assertRaisesRegex(ValueError, 'RGBA'):
            check.validate(self.root)

    def test_reference_as_runtime(self):
        self.manifest['buttons'][1]['usage'] = 'runtime_base'
        self.save()
        with self.assertRaisesRegex(ValueError, '런타임 상태 교체 금지'):
            check.validate(self.root)

    def test_review_hint_excluded(self):
        (self.root/'hint-review.gif').write_bytes(b'review-only')
        with self.assertRaisesRegex(ValueError, '힌트 시트'):
            check.validate(self.root)


if __name__ == '__main__':
    unittest.main()
