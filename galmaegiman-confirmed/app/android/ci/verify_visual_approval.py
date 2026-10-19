#!/usr/bin/env python3
"""Two-stage review gate. Approval changes cannot authorize changed source bytes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

MARKER = 'galmaegiman-confirmed/app/android/ci/visual-approval.json'
SCHEMA = 'native-visual-approval/v1'

def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])

def fingerprint(repo, ref):
    # Include every tracked file, mode and object ID, including CI, tests and assets.
    entries = git(repo, 'ls-tree', '-r', '-z', '--full-tree', ref).split(b'\0')
    entries = [e for e in entries if e and e.split(b'\t', 1)[1].decode() != MARKER]
    return hashlib.sha256(b'\0'.join(entries) + b'\0').hexdigest()

def verify(repo, marker):
    # Build/test tasks may create untracked output, but cannot alter reviewed files.
    subprocess.run(['git', '-C', str(repo), 'diff', '--exit-code', 'HEAD', '--', '.', ':(exclude)' + MARKER], check=True, stdout=subprocess.DEVNULL)
    head = git(repo, 'rev-parse', 'HEAD').decode().strip()
    digest = fingerprint(repo, head)
    candidate = {'schema': SCHEMA, 'reviewed_source_commit': head, 'source_tree_sha256': digest,
                 'reviewed_run_url': '', 'reviewed_evidence_sha256': '',
                 'reviewer': '', 'native_images_and_motion_reviewed': False}
    report = {'approved': False, 'source_commit': head, 'source_tree_sha256': digest,
              'approval_template': candidate, 'reason': 'Native images and motion require visual review before APK assembly.'}
    if not marker.exists():
        return report
    committed_marker = git(repo, 'show', 'HEAD:' + MARKER)
    if committed_marker != marker.read_bytes():
        raise ValueError('Approval must be committed unchanged, not injected by a build step')
    data = json.loads(committed_marker)
    if set(data) != set(candidate) or data.get('schema') != SCHEMA:
        raise ValueError('Malformed visual approval marker')
    sha = data['reviewed_source_commit']
    if not isinstance(sha, str) or not re.fullmatch('[a-f0-9]{40}', sha):
        raise ValueError('Visual approval must name a full reviewed commit SHA')
    if (not isinstance(data['reviewed_run_url'], str) or not re.fullmatch(
            r'https://github.com/chatbotgmm/server/actions/runs/[1-9][0-9]*', data['reviewed_run_url'])
        or not isinstance(data['reviewed_evidence_sha256'], str)
        or not re.fullmatch('[a-f0-9]{64}', data['reviewed_evidence_sha256'])
        or not isinstance(data['reviewer'], str) or not data['reviewer'].strip()
        or data['native_images_and_motion_reviewed'] is not True):
        raise ValueError('Visual approval lacks the reviewed run, evidence hash or explicit visual review')
    subprocess.run(['git', '-C', str(repo), 'merge-base', '--is-ancestor', sha, head], check=True)
    reviewed = fingerprint(repo, sha)
    if sha == head or data['source_tree_sha256'] != reviewed or digest != reviewed:
        report['reason'] = 'Stale visual approval: source/assets/tests/workflow differ from reviewed tree.'
        return report
    report.update(approved=True, reason='Exact reviewed source tree preserved.', approval=data)
    return report

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = verify(args.repo, args.repo / MARKER)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            output.write('approved=' + str(report['approved']).lower() + '\n')
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
