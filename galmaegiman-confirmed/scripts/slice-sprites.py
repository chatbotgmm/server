# 사용: python3 -I scripts/slice-sprites.py <시트> public/play/sprites <이름1,이름2,...> [기준높이px=520] [출력높이=512]
# 가로 한 줄 시트를 투명 틈으로 칸 나누기.
# 모든 시트·칸이 같은 비율이 되도록: 캔버스 높이 = 원본 기준높이(고정), 발 가운데를 캔버스 가로 가운데·바닥에 맞춤.
# (그림 폭은 칸마다 달라도 됨: 게임은 높이로 크기를 맞추고 가로 가운데를 발 위치로 씀)
import sys
from PIL import Image
src, out, names = sys.argv[1], sys.argv[2], sys.argv[3].split(',')
REF = int(sys.argv[4]) if len(sys.argv) > 4 else 520
H = int(sys.argv[5]) if len(sys.argv) > 5 else 512
im = Image.open(src).convert('RGBA'); w, h = im.size
ap = im.getchannel('A').load()
cols = [any(ap[x, y] > 24 for y in range(0, h, 2)) for x in range(w)]
runs, x = [], 0
while x < w:
    if cols[x]:
        s = x
        while x < w and cols[x]: x += 1
        runs.append([s, x])
    else: x += 1
big = [r for r in runs if r[1] - r[0] > 60]
for r in runs:  # 작은 조각(부스러기·바람 선)은 가장 가까운 칸에 붙임
    if r in big: continue
    near = min(big, key=lambda b: 0 if b[0] <= r[0] <= b[1] else min(abs(r[0] - b[1]), abs(r[1] - b[0])))
    near[0] = min(near[0], r[0]); near[1] = max(near[1], r[1])
assert len(big) == len(names), f'칸 {len(big)}개, 이름 {len(names)}개'
for (x0, x1), name in zip(big, names):
    c = im.crop((x0, 0, x1, h)); c = c.crop(c.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox())
    ca = c.getchannel('A').load()
    # 발: 맨 아래 10% 줄의 불투명 픽셀 가로 평균
    fy0 = int(c.height * 0.9); sx = n = 0
    for yy in range(fy0, c.height):
        for xx in range(c.width):
            if ca[xx, yy] > 128: sx += xx; n += 1
    foot = sx / max(n, 1)
    half = int(max(foot, c.width - foot)) + 4
    ch = max(REF, c.height + 4)
    if ch > REF: print(f'  ! {name}: 높이 {c.height}px가 기준 {REF}px보다 큼 → 기준높이를 올려 다시 자르세요')
    can = Image.new('RGBA', (half * 2, REF), (0, 0, 0, 0))
    can.paste(c, (int(half - foot), REF - c.height - 2), c)
    can = can.resize((round(can.width * H / REF), H), Image.LANCZOS)
    can.save(f'{out}/{name}.png', optimize=True)
    print(name, can.size, '원본 높이', c.height)
