#!/usr/bin/env python3
"""PC 웹게임(60종) 그림 자료를 웹용 WebP로 만든다. 원본은 건드리지 않는다.

사용: python3 scripts/web/build-game60-assets.py <roster60 저장소의 galmaegiman-confirmed> <출력 폴더(public/game60/assets)>
- 앱(APK)에 들어가는 그림을 그대로 쓰되 웹 크기로 줄인다: 60종 앞·뒤 모습, 흑화 8종, 전투 아이콘·배경, 메뉴 아이콘.
- 마을·스토리 배경은 홈페이지 마을 시각탐색의 갈매미 건물(public/site/village-v4/assets)을 쓴다.
"""
import json
import sys
from pathlib import Path
from PIL import Image

src = Path(sys.argv[1]); out = Path(sys.argv[2]); here = Path(__file__).resolve().parents[2]
app = src / 'app/android/app/src/main/assets'
out.mkdir(parents=True, exist_ok=True)
made = []

def save(img, rel, max_w=None, max_h=None, q=80):
    img = img.convert('RGBA')
    w, h = img.size
    s = min(1.0, (max_w or w) / w, (max_h or h) / h)
    if s < 1: img = img.resize((round(w * s), round(h * s)), Image.LANCZOS)
    p = out / rel; p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p, 'WEBP', quality=q, method=6)
    made.append((rel, p.stat().st_size))

# 60종 앞·뒤 모습(앱 roster60-art, 512px) → 높이 384
idx = json.loads((app / 'roster60-art/index.json').read_text(encoding='utf-8'))
for e in idx['entries']:
    for view, info in e['views'].items():
        save(Image.open(app / 'roster60-art' / info['file']), f"units/{e['id']}-{view}.webp", max_h=384)
# 흑화 8종(스토리 적 앞모습)
for f in sorted((src / 'art/gpt/blackened-gulls-20261028').glob('*-black-front.png')):
    save(Image.open(f), f"black/{f.name.split('-')[0]}.webp", max_h=384)
# 전투 아이콘·배경(ui-v3)
ui = src / 'art/gpt/ui-v3-20261025/runtime/optimized/ui'
for f in sorted(ui.glob('*.webp')):
    save(Image.open(f), f"ui/{f.name}", max_w=1600 if f.name.startswith('bg-') else 256)
# 메뉴 아이콘·재화(home-menu-v1)
for f in sorted((app / 'native/home-menu-v1').glob('*.png')):
    save(Image.open(f), f"menu/{f.stem}.webp", max_w=640 if f.stem in ('menu', 'panel', 'play') else 192)
# 갈매미 건물·마을(홈페이지 마을 시각탐색과 같은 그림)
v4 = here / 'public/site/village-v4/assets'
for name in ['identity-hq', 'identity-market', 'identity-records', 'identity-statue', 'identity-ship', 'identity-coast-panorama', 'identity-residents', '10-distant-bay']:
    save(Image.open(v4 / f'{name}.png'), f"village/{name}.webp", max_w=1400, q=78)
total = sum(s for _, s in made)
print(f'{len(made)}개 · {total // 1024}KB')
