#!/usr/bin/env python3
"""Fail-closed gate for NEW native v3 captures; legacy 37 captures are not v3 proof.

Input is evidence.json in a native capture directory. This gate validates evidence;
it never manufactures screenshots or substitutes source assertions for a native run.
All geometry is dp in the safe-content coordinate system. Pixel image size is exact.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from verify_feedback_stage1_ci import verify_png
from recruitment_reference_assets import REFERENCE

PROFILES = {(640, 320, 1.0), (800, 360, 1.0), (800, 360, 1.3), (960, 400, 2.0), (1280, 720, 1.5)}
BASE_PROFILE = (800, 360, 1.0)
STATES = {'low', 'mid', 'high', 'cooldown', 'insufficient', 'maximum', 'unlimited'}
REQUIRED = {'back', 'draw-card-LOW', 'draw-card-MID', 'draw-card-HIGH', 'minus', 'plus', 'count', 'maximum', 'info', 'draw-claim', 'draw-submit'}


def check(condition, message):
    if not condition:
        raise ValueError(message)


def rect(value):
    check(isinstance(value, list) and len(value) == 4 and all(type(v) in (int, float) and math.isfinite(v) for v in value), 'Invalid rectangle')
    check(value[2] > value[0] and value[3] > value[1], 'Empty/reversed rectangle')
    return value


def inside(outer, inner):
    return outer[0] <= inner[0] < inner[2] <= outer[2] and outer[1] <= inner[1] < inner[3] <= outer[3]


def overlaps(a, b):
    return max(a[0], b[0]) < min(a[2], b[2]) and max(a[1], b[1]) < min(a[3], b[3])


def verify_capture(root, c):
    profile = (c['widthDp'], c['heightDp'], c['fontScale'])
    check(profile in PROFILES and c['state'] in STATES, 'Unknown required profile/state')
    check(c.get('actualNativeViews') is True and c.get('apiWrites') == 0, 'Missing read-only native capture provenance')
    check(c.get('verticalScroll') is False and c.get('findings') == [], 'Layout failure or scroll')
    check(c.get('density') and type(c['density']) in (float, int) and math.isfinite(c['density']) and c['density'] > 0, 'Invalid density')
    check(c.get('widthPx') == round(c['widthDp'] * c['density']) and c.get('heightPx') == round(c['heightDp'] * c['density']), 'Pixel/dp disagreement')
    name = c['png']
    check(isinstance(name, str) and Path(name).name == name and name.endswith('.png'), 'Unsafe capture name')
    path = root / name
    verify_png(path, c['widthPx'], c['heightPx'])
    check(c.get('sha256') == hashlib.sha256(path.read_bytes()).hexdigest(), 'Capture SHA mismatch')
    safe = rect(c['safeBoundsDp'])
    check(inside([0, 0, c['widthDp'], c['heightDp']], safe), 'Safe bounds outside viewport')
    check(c.get('selectedKind') in ('LOW', 'MID', 'HIGH'), 'Missing selected kind')
    if c['state'] in ('low', 'mid', 'high'):
        check(c['selectedKind'] == c['state'].upper(), 'Wrong selected kind')
    selectors = c['selectors']
    check(len(selectors) == 3 and {s['kind'] for s in selectors} == {'LOW', 'MID', 'HIGH'}, 'Need exactly three selector cards')
    for s in selectors:
        check(type(s.get('selected')) is bool and s['selected'] == (s['kind'] == c['selectedKind']), 'Wrong selected accessibility state')
        check(s.get('nonColorSelectionCue') is True, 'Missing check or equivalent non-color cue')
    hero = c['hero']
    check(hero.get('count') == 1 and hero.get('kind') == c['selectedKind'], 'Need one current selected hero')
    check(hero.get('importantForAccessibility') is False, 'Decorative hero must not duplicate speech')
    expected = REFERENCE[c['selectedKind']]
    check(hero.get('asset') == expected['path'].removeprefix('native/release-v1/'), 'Wrong hero asset')
    check(hero.get('assetSha256') == expected['sha256'] and hero.get('bitmapDimensions') == expected['dimensions'], 'Wrong hero asset identity/dimensions')
    check(hero.get('sourceBounds') == expected['sourceRect'], 'Wrong canonical hero source rectangle')
    bounds, ink = rect(hero['boundsDp']), rect(hero['artBoundsDp'])
    check(inside(safe, bounds) and inside(bounds, ink), 'Hero art clipped')
    source = rect(hero['sourceBounds'])
    check(abs((ink[2]-ink[0])/(ink[3]-ink[1]) - (source[2]-source[0])/(source[3]-source[1])) < .001, 'Hero stretched')
    controls = c['controls']
    check(len({a['id'] for a in controls}) == len(controls) and REQUIRED <= {a['id'] for a in controls}, 'Missing or duplicate essential controls')
    for a in controls:
        b = rect(a['boundsDp'])
        check(inside(safe, b) and a.get('visibleBoundsDp') == b, 'Control clipped')
        check(b[2]-b[0] >= 48 and b[3]-b[1] >= 48, 'Control below 48dp')
        check(isinstance(a.get('description'), str) and a['description'].strip(), 'Missing accessible action description')
    for i, a in enumerate(controls):
        for b in controls[i+1:]:
            check(not overlaps(a['boundsDp'], b['boundsDp']), 'Independent controls overlap')
    labels = c['labels']
    check({'draw-total', 'draw-unit-cost', 'draw-claim-status', 'draw-pity-summary'} <= {l['id'] for l in labels}, 'Missing required live labels')
    for label in labels:
        b, ink = rect(label['boundsDp']), rect(label['inkBoundsDp'])
        check(inside(safe, b) and inside(b, ink) and label.get('visibleBoundsDp') == b, 'Text clipped')
        check(label.get('ellipsisCount') == 0 and label.get('text'), 'Text absent/truncated')
        for a in controls:
            if label.get('ownerControl') != a['id']:
                check(not overlaps(b, a['boundsDp']), 'Text overlaps independent control')
    for i, label in enumerate(labels):
        for other in labels[i+1:]:
            check(not overlaps(label['boundsDp'], other['boundsDp']), 'Independent labels overlap')
    check(c.get('layoutRevision') == 3, 'Earlier composition cannot prove r3 corrected layout')
    check(type(c.get('compactMode')) is bool, 'Missing native composition mode')
    if profile == BASE_PROFILE:
        check(c['compactMode'] is False, 'Normal 800x360/font1 cannot pass by falling back to compact layout')
    composition = c['composition']
    upper, purchase = rect(composition['upperInfoBoundsDp']), rect(composition['purchaseBoundsDp'])
    check(inside(safe, upper) and inside(safe, purchase), 'Right information/purchase surfaces clipped')
    check(upper[3] <= purchase[1] and upper[0] == purchase[0] and upper[2] == purchase[2], 'Right information and purchase regions must be separate and aligned')
    check(not overlaps(bounds, upper) and not overlaps(bounds, purchase), 'Hero overlaps right information or purchase controls')
    by_id = {a['id']: a for a in controls}
    for tag in ('minus', 'plus', 'count', 'maximum', 'draw-submit'):
        check(inside(purchase, by_id[tag]['boundsDp']), 'Purchase control outside lower panel')
    check(inside(upper, by_id['info']['boundsDp']), 'Pity action outside upper information region')
    check(not overlaps(purchase, by_id['draw-claim']['boundsDp']), 'Claim must stay separate from purchase panel')
    for tag in ('minus', 'count', 'plus'):
        b = by_id[tag]['boundsDp']
        check(b[2] - b[0] == 48, 'Quantity input/buttons must retain fixed 48dp width')
    max_bounds = by_id['maximum']['boundsDp']
    check(max_bounds[2] - max_bounds[0] >= 48, 'Maximum button below 48dp')
    if c['fontScale'] <= 1:
        check(max_bounds[2] - max_bounds[0] <= 65, 'Normal-font maximum button exceeds 65dp')
    for left, right in (('minus', 'count'), ('count', 'plus')):
        check(4 <= by_id[right]['boundsDp'][0] - by_id[left]['boundsDp'][2] <= 10, 'Quantity gap outside 4–10dp')

    for label in labels:
        if label['id'] in ('draw-unit-cost', 'draw-pity-summary'):
            check(inside(upper, label['boundsDp']), 'Cost/pity missing from upper information region')
        if label['id'] == 'draw-unit-cost':
            pill = label['boundsDp']
            check(abs((pill[0] + pill[2]) / 2 - (upper[0] + upper[2]) / 2) <= 1, 'Cost pill not centered')
            if not c['compactMode']: check(pill[2] - pill[0] == 184, 'Normal cost pill must be 184dp')
        if label['id'] == 'draw-total':
            check(inside(purchase, label['boundsDp']), 'Total missing from purchase panel')
    enabled = {a['id']: a.get('enabled') for a in controls}
    check(all(type(v) is bool for v in enabled.values()), 'Missing enabled semantics')
    if c['state'] == 'cooldown': check(enabled['draw-claim'] is False, 'Cooldown claim enabled')
    if c['state'] == 'insufficient': check(enabled['draw-submit'] is False, 'Insufficient submit enabled')
    return profile + (c['state'],)


def verify(root):
    data = json.loads((root / 'evidence.json').read_text())
    check(data.get('schema') == 'recruitment-v3-native-layout/1', 'Legacy/synthetic schema cannot prove v3')
    check(isinstance(data.get('sourceRevision'), str) and len(data['sourceRevision']) == 40 and all(c in '0123456789abcdef' for c in data['sourceRevision']), 'Missing source revision')
    check(data.get('failures', []) == [], 'Native capture producer reported failures')
    keys = [verify_capture(root, c) for c in data['captures']]
    check(len(keys) == len(set(keys)), 'Duplicate native evidence')
    check(set(keys) == ({BASE_PROFILE + (s,) for s in STATES} | {p + ('low',) for p in PROFILES}), 'Missing required profile/state evidence')
    return {'captures': len(keys), 'status': 'passed'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture_directory', type=Path)
    print(json.dumps(verify(parser.parse_args().capture_directory)))
