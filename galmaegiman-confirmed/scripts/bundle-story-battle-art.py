#!/usr/bin/env python3
"""Validate the five user-approved temporary aliases; reuse bundled originals without copying art."""
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

ALIASES = {'story_record': 'u01', 'story_guide': 'u02', 'story_engineer': 'u22',
           'story_escort': 'u03', 'story_dark_first': 'u31'}


def prepare(root, manifest, output):
    root, manifest, output = Path(root).resolve(), Path(manifest), Path(output)
    document = json.loads(manifest.read_text(encoding='utf-8'))
    profiles = json.loads((root / 'data/story/villains-test-v1.json').read_text(encoding='utf-8'))
    expected = {'story_' + row['id'] for row in profiles['profiles'] if row['combatAllowed']}
    entries = document.get('entries', [])
    if expected != set(ALIASES) or document.get('schemaVersion') != 1 or len(entries) != len(expected) or {row.get('id') for row in entries} != expected:
        raise ValueError('story art manifest must declare exactly the five approved temporary aliases')
    assets = root / 'app/android/app/src/main/assets/roster60-art'
    index = json.loads((assets / 'index.json').read_text(encoding='utf-8'))
    roster = {row['id']: row for row in index['entries']}
    runtime = []
    for row in entries:
        identity, source = row['id'], row.get('sourceSpeciesId')
        if source != ALIASES[identity] or row.get('status') != 'approved-temporary-alias':
            raise ValueError(identity + ': only the explicitly approved temporary alias is allowed')
        if not isinstance(row.get('approvalReference'), str) or not row['approvalReference'].strip():
            raise ValueError(identity + ': explicit approval reference required')
        front = copy.deepcopy(roster[source]['views']['front'])
        filename = source + '-front.webp'
        if front.get('file') != filename:
            raise ValueError(identity + ': roster front metadata mismatch')
        data = (assets / filename).read_bytes()
        if len(data) < 16 or data[:4] != b'RIFF' or data[8:12] != b'WEBP':
            raise ValueError(identity + ': bundled roster front is not WebP')
        fingerprint = row.get('assetSha256', '')
        if not isinstance(fingerprint, str) or not re.fullmatch('[0-9a-f]{64}', fingerprint) or hashlib.sha256(data).hexdigest() != fingerprint:
            raise ValueError(identity + ': bundled roster front fingerprint mismatch')
        runtime.append({'id': identity, 'status': row['status'], 'temporary': True,
                        'sourceSpeciesId': source, 'assetSha256': fingerprint, 'approvalReference': row['approvalReference'],
                        'views': {'front': front}})
    # Every alias and original validates before replacing generated metadata. No image is transformed.
    if output.exists():
        shutil.rmtree(output)
    generated = output / 'roster60-story-art'
    generated.mkdir(parents=True)
    (generated / 'index.json').write_text(json.dumps({'schemaVersion': 1, 'entries': runtime}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return runtime


if __name__ == '__main__':
    print('Story battle art: ' + str(len(prepare(*sys.argv[1:]))) + ' approved temporary aliases; no original art modified')
