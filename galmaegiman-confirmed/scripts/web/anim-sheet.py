# 제미나이가 그려 준 애니메이션 시트(흰 배경, 칸 없음)를 게임용으로 바꿈(2026-11-16).
# 흰 배경 지우기(가장자리와 이어진 흰색만) → 그림 덩어리를 frames개로 나눔 → 같은 배율로 256×256 칸에 발(아래 끝)을 224선에 맞춰 놓음 → webp
# 사용: python3 -I scripts/web/anim-sheet.py <입력.png> <출력.webp> [frames=6] [rows=1] [ref=min|last]
#   flip: 좌우로 뒤집을 칸(예: 1,2,5)
#   ref: 평소 그림 크기에 맞출 기준 칸 — min(가장 낮은 칸, 날개 편 칸 때문에 몸이 작아지지 않게) · last(마지막 칸, 「내려놓기」처럼 서 있는 자세로 끝날 때)
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

src, dst = sys.argv[1], sys.argv[2]
frames = int(sys.argv[3]) if len(sys.argv) > 3 else 6
rows = int(sys.argv[4]) if len(sys.argv) > 4 else 1
ref = sys.argv[5] if len(sys.argv) > 5 else 'min'
flip = {int(v) for v in (sys.argv[6].split(',') if len(sys.argv) > 6 and sys.argv[6] else [])}   # 좌우로 뒤집을 칸 번호(1부터) — 원본처럼 왼쪽을 보게 맞춤
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
for r in range(rows):   # 덩어리 단위로 칸 나누기: 날개처럼 옆 칸 자리까지 뻗은 부분도 몸과 이어져 있으면 함께(세로줄로 자르지 않음)
    part = rgba[r * band:(r + 1) * band]
    m = part[..., 3] > 8
    lab2, k = ndimage.label(m)
    if k == 0: raise SystemExit('그림이 없어요')
    idx = np.arange(1, k + 1)
    area = ndimage.sum(m, lab2, idx)
    cx = ndimage.center_of_mass(m, lab2, idx)
    big = [i2 for i2 in range(k) if area[i2] >= area.max() * 0.15]   # 몸통 덩어리(별·먼지 같은 작은 조각 제외)
    if len(big) != frames:
        # 몸통 수가 칸 수와 다르면(몸끼리 붙음 등) 예전 방식처럼 가로 위치로 칸을 나눔
        bounds = [W * f / frames for f in range(frames + 1)]
        centers = [(bounds[f] + bounds[f + 1]) / 2 for f in range(frames)]
    else:
        centers = sorted(cx[i2][1] for i2 in big)
    owner = {}
    for i2 in range(k):
        x2 = cx[i2][1]; owner[i2 + 1] = min(range(frames), key=lambda f: abs(centers[f] - x2))
    for f in range(frames):
        keep = np.isin(lab2, [l for l, o in owner.items() if o == f])
        if not keep.any(): raise SystemExit(f'{r + 1}줄 {f + 1}칸이 비었어요')
        piece = part.copy(); piece[~keep] = 0
        ys, xs = np.where(keep)
        cells.append(piece[ys.min():ys.max() + 1, xs.min():xs.max() + 1])
mh = max(c.shape[0] for c in cells); mw = max(c.shape[1] for c in cells)
rh = cells[-1].shape[0] if ref == 'last' else min(c.shape[0] for c in cells)
s = min(FIT_H / rh, (BASE - 4) / mh, (CELL - 6) / mw)   # 기준 칸을 몸 높이 204로, 단 가장 큰 칸도 위·옆으로 잘리지 않게   # 기준 칸을 몸 높이 204로, 단 가장 큰 칸도 256 칸 안에
out = Image.new('RGBA', (CELL * len(cells), CELL), (0, 0, 0, 0))   # 여러 줄로 그려 와도 한 줄로 이어 붙임(게임은 칸 번호 순서로 가로로 읽음)
for i, c in enumerate(cells):
    img = Image.fromarray(c, 'RGBA')
    if i + 1 in flip: img = img.transpose(Image.FLIP_LEFT_RIGHT)
    img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    out.alpha_composite(img, (i * CELL + (CELL - img.width) // 2, BASE - img.height))
out.save(dst, 'WEBP', quality=88, method=6)
print(f'{dst}: {out.size[0]}×{out.size[1]}, 배율 {s:.3f}, 칸 {len(cells)}개')
