# 제미나이가 그려 준 애니메이션 시트(흰 배경, 칸 없음)를 게임용으로 바꿈(2026-11-16).
# 흰 배경 지우기(가장자리와 이어진 흰색만) → 그림 덩어리를 frames개로 나눔 → 같은 배율로 256×256 칸에 발(아래 끝)을 224선에 맞춰 놓음 → webp
# 사용: python3 -I scripts/web/anim-sheet.py <입력.png> <출력.webp> [frames=6] [rows=1] [ref=min|last]
#   ref: 평소 그림 크기에 맞출 기준 칸 — min(가장 낮은 칸, 날개 편 칸 때문에 몸이 작아지지 않게) · last(마지막 칸, 「내려놓기」처럼 서 있는 자세로 끝날 때)
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

src, dst = sys.argv[1], sys.argv[2]
frames = int(sys.argv[3]) if len(sys.argv) > 3 else 6
rows = int(sys.argv[4]) if len(sys.argv) > 4 else 1
ref = sys.argv[5] if len(sys.argv) > 5 else 'min'
CELL, BASE, FIT_H, FIT_W = 256, 224, 204, 236

im = np.asarray(Image.open(src).convert('RGB')).astype(np.float32)
H, W, _ = im.shape
mn, mx = im.min(axis=2), im.max(axis=2)
whiteish = (mn >= 236) & (mx - mn <= 14)
lab, n = ndimage.label(whiteish)
edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
bg = np.isin(lab, list(edge))
# 가장자리 부드럽게: 배경과 닿은 2픽셀 띠는 흰색에 섞인 정도로 투명도를 매김(흰 테두리 남지 않게)
near = ndimage.binary_dilation(bg, iterations=2) & ~bg
alpha = np.where(bg, 0.0, 1.0)
a_edge = np.clip((255 - mn) / 60.0, 0, 1)
alpha = np.where(near, np.minimum(alpha, a_edge), alpha)
rgb = np.where(alpha[..., None] > 0, (im - 255 * (1 - alpha[..., None])) / np.maximum(alpha[..., None], 1e-3), 0)
rgba = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)

def split(mask_rows):   # 한 줄 안에서 그림 덩어리를 frames개로 묶음(별·먼지처럼 떨어진 조각은 가까운 덩어리에)
    m = mask_rows > 8
    lab2, k = ndimage.label(m)
    sl = ndimage.find_objects(lab2)
    iv = sorted([(s[1].start, s[1].stop, (s[0].stop - s[0].start) * (s[1].stop - s[1].start)) for s in sl if s is not None])
    groups = [[a, b] for a, b, _ in iv]
    merged = []
    for a, b in groups:
        if merged and a <= merged[-1][1]: merged[-1][1] = max(merged[-1][1], b)
        else: merged.append([a, b])
    while len(merged) > frames:   # 가장 좁은 틈부터 합침
        gaps = [merged[i + 1][0] - merged[i][1] for i in range(len(merged) - 1)]
        sizes = [b - a for a, b in merged]
        i = min(range(len(gaps)), key=lambda j: (min(sizes[j], sizes[j + 1]) > 120, gaps[j]))
        merged[i] = [merged[i][0], merged[i + 1][1]]; del merged[i + 1]
    colsum = m.sum(axis=0)
    while len(merged) < frames:   # 덩어리가 붙어 있으면(날개·먼지가 옆 칸과 겹침) 가장 넓은 덩어리를 가운데쯤 가장 빈 세로줄에서 자름
        i = max(range(len(merged)), key=lambda j: merged[j][1] - merged[j][0]); a0, b0 = merged[i]
        lo, hi = a0 + (b0 - a0) // 5, b0 - (b0 - a0) // 5
        cut = lo + int(np.argmin(colsum[lo:hi]))
        merged[i:i + 1] = [[a0, cut], [cut, b0]]
    if len(merged) != frames: raise SystemExit(f'덩어리 {len(merged)}개 — {frames}개로 못 나눔')
    return merged

band = H // rows
cells = []
for r in range(rows):
    part = rgba[r * band:(r + 1) * band]
    for a, b in split(part[..., 3]):
        piece = part[:, a:b]
        ys = np.where(piece[..., 3].max(axis=1) > 8)[0]
        cells.append(piece[ys[0]:ys[-1] + 1])
mh = max(c.shape[0] for c in cells); mw = max(c.shape[1] for c in cells)
rh = cells[-1].shape[0] if ref == 'last' else min(c.shape[0] for c in cells)
s = min(FIT_H / rh, 250 / mh, FIT_W / mw)   # 기준 칸을 몸 높이 204로, 단 가장 큰 칸도 256 칸 안에
out = Image.new('RGBA', (CELL * frames, CELL * rows), (0, 0, 0, 0))
for i, c in enumerate(cells):
    img = Image.fromarray(c, 'RGBA')
    img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    col, row = i % frames, i // frames
    out.alpha_composite(img, (col * CELL + (CELL - img.width) // 2, row * CELL + BASE - img.height))
out.save(dst, 'WEBP', quality=88, method=6)
print(f'{dst}: {out.size[0]}×{out.size[1]}, 배율 {s:.3f}, 칸 {len(cells)}개')
