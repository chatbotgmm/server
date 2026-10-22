"""유한 승인 범위의 negative tests. 소스 컴파일이나 Android 실행은 아님."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import verify_v2_source_batch as v

class FiniteBatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.manifest = {'schema':v.SCHEMA,'batch_id':v.BATCH_ID,'baseline_commit':v.BASELINE,'files':[]}
        for path in sorted(v.PAYLOAD_PATHS):
            data = ('synthetic:' + path).encode(); p = self.root/path
            p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
            self.manifest['files'].append({'path':path,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    def verify(self, changed=None):
        return v.verify_batch(self.root, list(v.PAYLOAD_PATHS) if changed is None else changed, self.manifest)
    def first(self): return self.root/self.manifest['files'][0]['path']
    def test_complete_exact_batch(self): self.assertEqual(self.verify()['source_batch_files'],len(v.PAYLOAD_PATHS))
    def test_empty_diff_still_hashes_every_payload(self):
        self.verify([]);self.first().write_text('mutated')
        with self.assertRaises(ValueError):self.verify([])
    def test_unknown_source_and_extra_artifact(self):
        for p in ['galmaegiman-confirmed/src/admin.ts','galmaegiman-confirmed/data/economy.yaml.bak','galmaegiman-confirmed/public/new.png','app.apk','app.aab','bundle.zip','galmaegiman-confirmed/app/android/new.txt']:
            with self.subTest(path=p),self.assertRaises(ValueError):self.verify([p])
    def test_wrong_baseline(self):
        for baseline in ['HEAD','5279b39021a6cea5919a61379ca6173e0cd9c98e','0'*40]:
            self.manifest['baseline_commit']=baseline
            with self.assertRaises(ValueError):self.verify()
    def test_wrong_schema_and_batch(self):
        for key in ['schema','batch_id']:
            before=self.manifest[key];self.manifest[key]='unreviewed'
            with self.assertRaises(ValueError):self.verify()
            self.manifest[key]=before
    def test_extra_manifest_fields(self):
        self.manifest['allow_all_src']=True
        with self.assertRaises(ValueError):self.verify()
    def test_missing_and_duplicate_entry(self):
        item=self.manifest['files'].pop()
        with self.assertRaises(ValueError):self.verify()
        self.manifest['files'].append(self.manifest['files'][0])
        with self.assertRaises(ValueError):self.verify()
    def test_extra_entry(self):
        self.manifest['files'].append(copy.deepcopy(self.manifest['files'][0]))
        with self.assertRaises(ValueError):self.verify()
    def test_same_count_unknown_path(self):
        self.manifest['files'][0]['path']='galmaegiman-confirmed/src/unknown.ts'
        with self.assertRaises(ValueError):self.verify()
    def test_missing_file(self):
        self.first().unlink()
        with self.assertRaises(ValueError):self.verify()
    def test_hash_and_size_mismatch(self):
        row=self.manifest['files'][0];row['sha256']='0'*64
        with self.assertRaises(ValueError):self.verify()
    def test_invalid_digest_and_bytes(self):
        row=self.manifest['files'][0];digest=row['sha256'];size=row['bytes']
        for value in [None,True,-1,0,'123']:
            row['bytes']=value
            with self.assertRaises(ValueError):self.verify()
        row['bytes']=size
        for value in ['x'*64,'A'*64,None,7]:
            row['sha256']=value
            with self.assertRaises(ValueError):self.verify()
        row['sha256']=digest
    def test_symlink_file_even_inside_repo(self):
        p=self.first();target=self.root/'target';p.rename(target);p.symlink_to(target)
        with self.assertRaises(ValueError):self.verify()
    def test_symlink_parent_even_inside_repo(self):
        p=self.first();parent=p.parent;target=self.root/'redirect';parent.rename(target);parent.symlink_to(target,target_is_directory=True)
        with self.assertRaises(ValueError):self.verify()
    def test_only_exact_four_control_paths(self):
        self.assertEqual(len(v.CONTROL_PATHS),4);self.verify(list(v.CONTROL_PATHS))
        with self.assertRaises(ValueError):self.verify([next(iter(v.CONTROL_PATHS))+'.bak'])
    def test_not_ancestor_is_not_silently_accepted(self):
        with patch.object(v.subprocess,'run',side_effect=subprocess.CalledProcessError(1,['git'])):
            with self.assertRaises(subprocess.CalledProcessError):v.verify_repository(self.root)
    def test_tracked_worktree_changes_fail(self):
        with patch.object(v.subprocess,'run'),patch.object(v.subprocess,'check_output',side_effect=[b'',b'src/changed\0']):
            with self.assertRaises(ValueError):v.verify_repository(self.root)
    def test_protected_payload_includes_data_and_expected_flow_fixtures(self):
        for p in ['galmaegiman-confirmed/data/units.json','galmaegiman-confirmed/data/battle.yaml','galmaegiman-confirmed/data/economy.yaml','galmaegiman-confirmed/test/battle-events.test.ts','galmaegiman-confirmed/test/game.integration.test.ts']:
            self.assertIn(p,v.PAYLOAD_PATHS)

if __name__=='__main__':unittest.main()
