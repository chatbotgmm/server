#!/usr/bin/env python3
"""Keep the isolated complete backend run at its prior 457 plus three matrix and eighteen owned-receipt cases."""
import argparse
import re
from pathlib import Path

def verify(text):
    text=re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', text)
    summaries=re.findall(r'^\s*Tests\s+([^\r\n]+)',text,re.M)
    if not summaries: raise ValueError('Missing complete backend test summary')
    summary=summaries[-1]
    passed=re.search(r'\b(\d+) passed\b',summary)
    total=re.search(r'\((\d+)\)\s*$',summary)
    if not passed or not total or int(passed[1])<478 or int(total[1])!=int(passed[1]):
        raise ValueError('Require all 457 existing plus three matrix and eighteen receipt backend tests, with no failed/skipped cases')
    return int(passed[1])

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('log',type=Path);args=parser.parse_args()
    print('Backend passed:',verify(args.log.read_text()))
