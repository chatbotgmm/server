#!/usr/bin/env python3
"""GameMaker(gms2) 포함 파일용 PNG를 만든다. GameMaker의 sprite_add는 WebP를 못 읽어서 PNG로 바꾼다.
원본: 웹게임 그림(public/game60/assets, 앱 그림에서 만든 것). 모두 datafiles/ 한 폴더에 접두어로 구분(하위 폴더 형식 미확인).
사용: python3 build-gms2-assets.py <galmaegiman-confirmed> <gms2/datafiles>
"""
import sys
from pathlib import Path
from PIL import Image
src = Path(sys.argv[1]) / 'public/game60/assets'; out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
made = []
def save(p, name, max_h=None, max_w=None, colors=None):
    im = Image.open(p).convert('RGBA'); w, h = im.size
    s = min(1.0, (max_h or h) / h, (max_w or w) / w)
    if s < 1: im = im.resize((round(w * s), round(h * s)), Image.LANCZOS)
    if colors: im = im.quantize(colors=colors, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    f = out / name; im.save(f, 'PNG', optimize=True); made.append(f.stat().st_size)
for p in sorted((src / 'units').glob('*.webp')): save(p, p.stem + '.png', max_h=300, colors=256)          # u01-front.png
for p in sorted((src / 'black').glob('*.webp')): save(p, 'black-' + p.stem + '.png', max_h=300, colors=256)
for p in sorted((src / 'ui').glob('*.webp')): save(p, 'ui-' + p.stem + '.png', max_w=1280 if p.stem.startswith('bg-') else 96, colors=None if p.stem.startswith('bg-') else 256)
for p in sorted((src / 'menu').glob('*.webp')): save(p, 'menu-' + p.stem + '.png', max_w=96, colors=256)
for p in sorted((src / 'village').glob('*.webp')): save(p, 'village-' + p.stem + '.png', max_w=1280 if 'panorama' in p.stem or 'bay' in p.stem else 640, colors=256)
print(f'{len(made)}개 · {sum(made)//1024}KB')
