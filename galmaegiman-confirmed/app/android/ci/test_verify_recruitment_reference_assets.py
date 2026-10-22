"""Finite asset bytes, not evidence that Android rendered them."""
import copy, json, unittest
from pathlib import Path
from unittest.mock import patch
import recruitment_reference_assets as assets
from verify_theme_ci import allowed_asset_changes
from verify_recruitment_revision_ci import tray_lock

class ReferenceAssetsTest(unittest.TestCase):
    def test_exact_six_bundled_assets_and_original_three_are_preserved(self):
        root=Path(__file__).resolve().parent.parent/'app/src/main/assets'
        report=assets.verify_reference_assets(lambda p:(root/p).read_bytes())
        self.assertEqual(6,report['files'])
        self.assertEqual(3,len(tray_lock()))
    def test_changed_hash_missing_bytes_and_forged_rectangle_fail_closed(self):
        with self.assertRaises(ValueError):assets.verify_reference_assets(lambda p:b'bad')
        lock=json.loads((Path(__file__).parent/'recruitment-assets-lock.json').read_text())
        for field,value in [('sourceRect',[0,0,1,1]),('dimensions',[768,512]),('sha256','0'*64),('path','native/release-v1/recruitment/other.png')]:
            changed=copy.deepcopy(lock);changed['referenceV3'][0][field]=value
            with patch.object(assets.json,'loads',return_value=changed), self.assertRaises(ValueError):assets.reference_lock()
    def test_only_exact_new_image_font_and_license_paths_allowed(self):
        lock=json.loads((Path(__file__).parent/'theme-assets-lock.json').read_text())
        paths=[assets.ASSET_PREFIX+e['path'] for e in assets.reference_lock()]
        allowed_asset_changes(paths,lock,tray_lock())
        for path in paths:
            with self.assertRaises(ValueError):allowed_asset_changes([path+'.bak'],lock,tray_lock())
