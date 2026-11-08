#!/usr/bin/env python3
"""랜덤 디펜스(public/defense) 파일 주소에 내용 지문(?v=…)을 붙인다.

Cloudflare가 js·css를 브라우저에 4시간 캐시하라고 덮어써서(서버의 no-cache를 무시),
파일을 바꿔도 예전 파일이 보이는 문제가 있었다(2026-11-08 확인). 화면(/defense)은 캐시되지 않으므로
그 안의 파일 주소가 내용에 따라 바뀌면 항상 새 파일을 받는다.

순서(아래 파일이 위 파일 주소에 들어감): data.js → engine.js(data 지문) → defense.js(engine·bgm.mp3 지문) → index.html(defense·css 지문).
여러 번 돌려도 결과가 같다. 사용: python3 -I scripts/web/stamp-defense.py [public/defense 폴더]
"""
import hashlib
import re
import sys
from pathlib import Path

D = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'public/defense'
h = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()[:12]


def stamp(path, pattern, ver):
    s = path.read_text(encoding='utf-8')
    new, n = re.subn(pattern, lambda m: f'{m.group(1)}?v={ver}', s)
    assert n >= 1, f'{path.name}: 주소를 찾지 못함 {pattern}'
    if new != s:
        path.write_text(new, encoding='utf-8')
    return n


stamp(D / 'engine.js', r"(from '\./data\.js)(?:\?v=[0-9a-f]+)?(?=')", h(D / 'data.js'))
stamp(D / 'defense.js', r"(from '\./engine\.js)(?:\?v=[0-9a-f]+)?(?=')", h(D / 'engine.js'))
if (D / 'bgm.mp3').exists():
    stamp(D / 'defense.js', r"(BGM_URL = '/defense/bgm\.mp3)(?:\?v=[0-9a-f]+)?(?=')", h(D / 'bgm.mp3'))
stamp(D / 'index.html', r'(/defense/defense\.js)(?:\?v=[0-9a-f]+)?(?=")', h(D / 'defense.js'))
stamp(D / 'index.html', r'(/defense/defense\.css)(?:\?v=[0-9a-f]+)?(?=")', h(D / 'defense.css'))
for f in ['data.js', 'engine.js', 'defense.js', 'defense.css', 'index.html']:
    print(f'{h(D / f)}  {f}')
