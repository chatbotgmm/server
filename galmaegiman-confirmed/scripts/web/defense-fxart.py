#!/usr/bin/env python3
"""디펜스 효과 그림(assets-src/defense-fx/*.png, Kenney Particle Pack CC0를 흰색+투명도로 줄인 것)을
public/defense/fxart.js 한 파일(data: 주소)로 묶어요. 서버는 /defense/ 아래 .js·.css·.mp3만 내주므로 그림을 JS에 넣음(2026-11-21).
다시 만들기: python3 -I scripts/web/defense-fxart.py  (그다음 stamp-defense.py)"""
import base64
from pathlib import Path
R = Path(__file__).resolve().parents[2]
src = R / 'assets-src' / 'defense-fx'
out = ['// 자동 생성(scripts/web/defense-fxart.py) — 손으로 고치지 않기. 그림: Kenney Particle Pack(CC0 1.0, https://kenney.nl)', 'export const FX = {']
for p in sorted(src.glob('*.png')):
    out.append(f"  {p.stem}: 'data:image/png;base64,{base64.b64encode(p.read_bytes()).decode()}',")
out.append('};\n')
(R / 'public' / 'defense' / 'fxart.js').write_text('\n'.join(out), encoding='utf-8')
print('fxart.js', sum(1 for _ in src.glob('*.png')), '장')
