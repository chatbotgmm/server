#!/usr/bin/env python3
"""Fail before packaging if any required original black-gull art is absent or changed."""
import hashlib
import json
from pathlib import Path
import shutil
import struct
import sys

SPECIES = {'u01', 'u02', 'u06', 'u07', 'u14', 'u23', 'u28', 'u31'}


def prepare(root, manifest, output):
    root, manifest, output = Path(root), Path(manifest), Path(output)
    document = json.loads(manifest.read_text(encoding='utf-8'))
    content = json.loads((root / 'data/story/story-mode-v1.json').read_text(encoding='utf-8'))
    required = {row['speciesId'] for row in content['encounters'] if row['kind'] == 'black'}
    entries = document.get('entries', [])
    if document.get('schemaVersion') != 1 or len(entries) != len(SPECIES) or {row.get('speciesId') for row in entries} != SPECIES or required != SPECIES:
        raise ValueError('black story art must declare exactly the eight supported story species')
    originals = []
    for row in entries:
        species = row['speciesId']
        source = root / 'art/gpt/blackened-gulls-20261028' / (species + '-black-front.png')
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != row.get('sha256'):
            raise ValueError(species + ': black front fingerprint mismatch')
        if len(data) < 24 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR' or list(struct.unpack('>II', data[16:24])) != row.get('size'):
            raise ValueError(species + ': black front PNG dimensions mismatch')
        originals.append((source, species + '-front.png'))
    # Validate every source before replacing the previous generated pack. No fallback or pixel edit.
    if output.exists():
        shutil.rmtree(output)
    destination = output / 'roster60-art/black'
    destination.mkdir(parents=True)
    for source, filename in originals:
        shutil.copyfile(source, destination / filename)
    return len(originals)


if __name__ == '__main__':
    print('Black story art: ' + str(prepare(*sys.argv[1:])) + ' pinned originals bundled unchanged')
