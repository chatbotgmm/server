import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from PIL import Image
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('background_check',Path(__file__).with_name('check-background-motion.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class BackgroundResources(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)/'assets'
        shutil.copytree(check.DEFAULT,self.root)
        self.data = json.loads((self.root/'runtime.json').read_text())
        self.row = self.data['assets'][0]

    def save(self):
        (self.root/'runtime.json').write_text(json.dumps(self.data))

    def test_valid_and_water_fail_closed(self):
        rows = check.validate(self.root)
        self.assertEqual(sum(r['frames'] for r in rows),64)
        self.assertEqual({r['scene'] for r in rows if r['waterMaskRequiredByRenderer']},{'navigation','market','harbor','home'})

    def test_path_escape(self):
        self.row['file']='../outside.png';self.save()
        with self.assertRaisesRegex(ValueError,'경로'): check.validate(self.root)

    def test_duplicate_turnaround(self):
        self.row['playbackSequence'].insert(8,7);self.save()
        with self.assertRaisesRegex(ValueError,'핑퐁'): check.validate(self.root)

    def test_loop_duration(self):
        self.row['loopDurationMs']+=1;self.save()
        with self.assertRaisesRegex(ValueError,'루프'): check.validate(self.root)

    def test_crossfade(self):
        self.row['crossfadeMs']=self.row['frameDurationMs']+1;self.save()
        with self.assertRaisesRegex(ValueError,'crossfade'): check.validate(self.root)

    def test_anchor_mismatch(self):
        self.row['sceneAnchorNormalized'][0]+=.1;self.save()
        with self.assertRaisesRegex(ValueError,'앵커/배치'): check.validate(self.root)

    def test_wrong_frame_rect(self):
        self.row['frameRects'][7]['x']+=1;self.save()
        with self.assertRaisesRegex(ValueError,'프레임 사각형'): check.validate(self.root)

    def test_edge_alpha(self):
        file=self.root/self.row['file']
        with Image.open(file) as source:
            image=source.copy()
        image.putpixel((512,0),(255,255,255,1));image.save(file)
        self.row['sha256']=check.digest(file);self.save()
        with self.assertRaisesRegex(ValueError,'테두리 알파'): check.validate(self.root)

    def test_original_identity(self):
        self.row['background']['gitBlobSha1']='0'*40;self.save()
        with self.assertRaisesRegex(ValueError,'원본 해시'): check.validate(self.root)


if __name__=='__main__': unittest.main()
