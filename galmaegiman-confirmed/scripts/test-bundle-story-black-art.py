import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location('black_art_bundle', Path(__file__).with_name('bundle-story-black-art.py'))
BUNDLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUNDLE)
ROOT = Path(__file__).resolve().parents[1]


class StoryBlackArtBundleTest(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        self.output = self.root / 'generated'
        self.manifest = self.root / 'data/story/black-art-v1.json'
        self.manifest.parent.mkdir(parents=True)
        self.content = json.loads((ROOT / 'data/story/story-mode-v1.json').read_text())
        self.document = json.loads((ROOT / 'data/story/black-art-v1.json').read_text())
        self.sources = self.root / 'art/gpt/blackened-gulls-20261028'
        self.sources.mkdir(parents=True)
        # Only bundle validation uses this synthetic header; native tests decode the real pinned PNGs.
        self.data = b'\x89PNG\r\n\x1a\n\0\0\0\rIHDR' + struct.pack('>II', 1, 1)
        for row in self.document['entries']:
            row['sha256'] = hashlib.sha256(self.data).hexdigest()
            row['size'] = [1, 1]
            (self.sources / (row['speciesId'] + '-black-front.png')).write_bytes(self.data)

    def bundle(self):
        self.manifest.write_text(json.dumps(self.document))
        (self.manifest.parent / 'story-mode-v1.json').write_text(json.dumps(self.content))
        return BUNDLE.prepare(self.root, self.manifest, self.output)

    def test_exact_eight_files_preserve_source_bytes_and_runtime_names(self):
        self.assertEqual(8, self.bundle())
        target = self.output / 'roster60-art/black'
        self.assertEqual({species + '-front.png' for species in BUNDLE.SPECIES}, {path.name for path in target.iterdir()})
        for image in target.iterdir():
            self.assertEqual(self.data, image.read_bytes())

    def test_missing_corrupt_or_wrong_size_source_preserves_previous_generated_pack(self):
        self.bundle()
        marker = self.output / 'previous-marker'
        marker.write_text('preserved')
        source = self.sources / 'u14-black-front.png'
        source.unlink()
        with self.assertRaises(FileNotFoundError):
            self.bundle()
        source.write_bytes(b'changed-original')
        with self.assertRaisesRegex(ValueError, 'fingerprint'):
            self.bundle()
        source.write_bytes(self.data)
        next(row for row in self.document['entries'] if row['speciesId'] == 'u14')['size'] = [2, 1]
        with self.assertRaisesRegex(ValueError, 'dimensions'):
            self.bundle()
        self.assertEqual('preserved', marker.read_text())

    def test_missing_duplicate_or_new_species_never_silently_omit_or_fallback(self):
        original = copy.deepcopy(self.document)
        for entries in [original['entries'][:-1], original['entries'][:-1] + [original['entries'][0]], original['entries'] + [{'speciesId': 'u60'}]]:
            self.document['entries'] = entries
            with self.assertRaises(ValueError):
                self.bundle()
        self.document = original
        next(row for row in self.content['encounters'] if row['kind'] == 'black')['speciesId'] = 'u60'
        with self.assertRaises(ValueError):
            self.bundle()


if __name__ == '__main__':
    unittest.main()
