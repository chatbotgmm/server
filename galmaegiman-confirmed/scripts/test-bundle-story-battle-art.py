import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
MODULE = Path(__file__).with_name('bundle-story-battle-art.py')
SPEC = importlib.util.spec_from_file_location('story_art_bundle', MODULE)
BUNDLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUNDLE)
REAL_ROOT = Path(__file__).resolve().parents[1]


class StoryBattleArtBundleTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        (self.root / 'data/story').mkdir(parents=True)
        (self.root / 'data/story/villains-test-v1.json').write_bytes((REAL_ROOT / 'data/story/villains-test-v1.json').read_bytes())
        self.document = json.loads((REAL_ROOT / 'data/story/battle-art-v1.json').read_text())
        self.manifest = self.root / 'data/story/battle-art-v1.json'
        self.output = self.root / 'generated'
        self.assets = self.root / 'app/android/app/src/main/assets/roster60-art'
        self.assets.mkdir(parents=True)
        self.index = json.loads((REAL_ROOT / 'app/android/app/src/main/assets/roster60-art/index.json').read_text())
        for row in self.document['entries']:
            data = b'RIFF\x10\0\0\0WEBPtest-only-header'
            (self.assets / (row['sourceSpeciesId'] + '-front.webp')).write_bytes(data)
            row['assetSha256'] = hashlib.sha256(data).hexdigest()

    def bundle(self):
        self.manifest.write_text(json.dumps(self.document))
        (self.assets / 'index.json').write_text(json.dumps(self.index))
        return BUNDLE.prepare(self.root, self.manifest, self.output)

    def test_all_five_explicit_aliases_reuse_exact_bundled_front_metadata(self):
        roster = {row['id']: row for row in self.index['entries']}
        result = self.bundle()
        self.assertEqual(5, len(result))
        self.assertEqual(['index.json'], [p.name for p in (self.output / 'roster60-story-art').iterdir()])
        for row in result:
            self.assertTrue(row['temporary'])
            self.assertEqual(BUNDLE.ALIASES[row['id']], row['sourceSpeciesId'])
            self.assertEqual(roster[row['sourceSpeciesId']]['views']['front'], row['views']['front'])

    def test_unknown_wrong_duplicate_missing_or_unapproved_alias_fails_without_replacing_output(self):
        self.bundle()
        marker = self.output / 'preserved-marker'
        marker.write_text('previous generated output')
        approved = copy.deepcopy(self.document)
        invalids = []
        for field, value in [('id', 'story_unknown'), ('sourceSpeciesId', 'u60'),
                             ('status', 'pending-approved-source'), ('approvalReference', ''), ('assetSha256', '0' * 64)]:
            changed = copy.deepcopy(approved)
            changed['entries'][0][field] = value
            invalids.append(changed)
        invalids.append({'schemaVersion': 1, 'entries': approved['entries'][:-1]})
        changed = copy.deepcopy(approved)
        changed['entries'][1] = copy.deepcopy(changed['entries'][0])
        invalids.append(changed)
        for changed in invalids:
            with self.subTest(document=changed):
                self.document = changed
                with self.assertRaises(ValueError):
                    self.bundle()
                self.assertEqual('previous generated output', marker.read_text())

    def test_missing_corrupt_or_wrong_front_original_is_rejected(self):
        self.bundle()
        original = self.assets / 'u01-front.webp'
        original.unlink()
        with self.assertRaises(FileNotFoundError):
            self.bundle()
        original.write_bytes(b'not-an-image')
        with self.assertRaisesRegex(ValueError, 'not WebP'):
            self.bundle()
        original.write_bytes(b'RIFF\x10\0\0\0WEBPtest-only-header')
        next(row for row in self.index['entries'] if row['id'] == 'u01')['views']['front']['file'] = 'u02-front.webp'
        with self.assertRaisesRegex(ValueError, 'metadata mismatch'):
            self.bundle()


if __name__ == '__main__':
    unittest.main()
