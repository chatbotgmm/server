# GPT 승진 그림용 참고 시트: 등급별로 지금(원형) 그림을 GPT가 그릴 순서·배치 그대로 모음 → art/ref/ref_<등급>_<front|back>.png
# 사용: python3 -I scripts/make-evo-refs.py   (D·T·E 4×2, I 3×3, 왼쪽 위부터 D1, D2, …)
import os
from PIL import Image
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(root, 'art/ref'); os.makedirs(out, exist_ok=True)
CELL = 340
for g, n, (gx, gy) in (('D', 8, (4, 2)), ('T', 8, (4, 2)), ('E', 8, (4, 2)), ('I', 9, (3, 3))):
    for side in ('front', 'back'):
        sheet = Image.new('RGBA', (gx * CELL, gy * CELL))
        for k in range(n):
            im = Image.open(os.path.join(root, f'public/battle/sprites/{g}{k + 1}_s0_{side}.webp')).convert('RGBA')
            im.thumbnail((CELL - 60, CELL - 60), Image.LANCZOS)
            x, y = (k % gx) * CELL, (k // gx) * CELL
            sheet.alpha_composite(im, (x + (CELL - im.width) // 2, y + CELL - 30 - im.height))
        p = os.path.join(out, f'ref_{g}_{side}.png')
        sheet.quantize(colors=255, method=Image.FASTOCTREE).save(p, optimize=True); print(p, sheet.size, os.path.getsize(p) // 1024, 'KB')
