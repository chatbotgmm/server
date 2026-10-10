# 사용자가 건네준 GPT 그림(한 줄에 3~4마리) → 시트로 저장하고 유닛별로 자르기 (docs/GPT-NOW.md 1~18번)
# 사용: python3 -I scripts/take-sheet.py <받은 그림> <번호 1~18> [비교 그림 저장 폴더]
#   결과: art/gpt/sheet_<등급>_<front|back>_<a|b|c>.png, art/gpt/<ID>_<front|back>.png
#   캐릭터끼리 닿아 있어도 나눔: 그림을 깎아 몸통 핵을 찾고, 모든 픽셀을 가장 가까운 핵에 붙임. 몸에서 떨어진 작은 조각·옆 캐릭터 부스러기는 지움
#   gpt-art 브랜치에 올리기는 따로(scripts 밖에서 git으로)
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

GROUPS = [('D', 'a', ['D1', 'D2', 'D3', 'D4']), ('D', 'b', ['D5', 'D6', 'D7', 'D8']), ('T', 'a', ['T1', 'T2', 'T3', 'T4']), ('T', 'b', ['T5', 'T6', 'T7', 'T8']),
          ('E', 'a', ['E1', 'E2', 'E3', 'E4']), ('E', 'b', ['E5', 'E6', 'E7', 'E8']), ('I', 'a', ['I1', 'I2', 'I3']), ('I', 'b', ['I4', 'I5', 'I6']), ('I', 'c', ['I7', 'I8', 'I9'])]
src, num = sys.argv[1], int(sys.argv[2])
cmp_dir = sys.argv[3] if len(sys.argv) > 3 else None
side = 'front' if num <= 9 else 'back'
grade, part, ids = GROUPS[(num - 1) % 9]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(root, 'art/gpt'); os.makedirs(out, exist_ok=True)

im = Image.open(src).convert('RGBA'); px = np.array(im); a = px[:, :, 3]
if not all(a[y, x] < 10 for y, x in [(1, 1), (1, -2), (-2, 1), (-2, -2)]):
    from rembg import remove, new_session
    im = remove(im.convert('RGB'), session=new_session('isnet-anime')).convert('RGBA'); px = np.array(im); a = px[:, :, 3]
    print('투명 배경이 아니어서 rembg로 배경을 지웠어요')
sheet = os.path.join(out, f'sheet_{grade}_{side}_{part}.png'); im.save(sheet); print('시트:', sheet, im.size)

# 몸통 핵: 깎을수록 붙은 캐릭터가 떨어짐. 핵 개수가 마리 수가 될 때까지
n = len(ids)
for er in range(0, 80, 2):
    core = ndimage.binary_erosion(a > 128, iterations=er) if er else a > 128
    lab, k = ndimage.label(core); sz = ndimage.sum(core, lab, range(1, k + 1))
    big = [i + 1 for i, s in enumerate(sz) if s > 0.02 * core.sum()]
    if len(big) >= n: break
if len(big) < n: sys.exit(f'몸통을 {n}개로 나누지 못했어요(찾은 것 {len(big)}개). 캐릭터 사이가 너무 붙어 있어요')
big = sorted(sorted(big, key=lambda i: -ndimage.sum(core, lab, i))[:n], key=lambda i: ndimage.center_of_mass(core, lab, i)[1])
cores = np.zeros(a.shape, int)
for j, i in enumerate(big): cores[lab == i] = j + 1
_, (iy, ix) = ndimage.distance_transform_edt(cores == 0, return_indices=True)
own = cores[iy, ix]
# 몸에서 떨어져 떠 있는 장식은 「핵」이 아니라 「몸」이 가장 가까운 캐릭터 것으로(옆 캐릭터 핵이 더 가까워 잘못 붙던 것)
allm = a > 60
lb0, k0 = ndimage.label(allm); s0 = ndimage.sum(allm, lb0, range(1, k0 + 1))
bodies = []
for j in range(n):
    bm = allm & (own == j + 1); lbj, kj = ndimage.label(bm)
    # 몸 = 몸통 핵이 들어 있는 덩어리 + 큰 장식 덩어리(떠 있는 가발·별처럼). 작은 조각은 이 「몸」과의 거리로 주인을 정함
    hit = np.unique(lbj[(cores == j + 1) & bm]); hit = hit[hit > 0]
    szj = ndimage.sum(bm, lbj, range(1, kj + 1)); hit = np.union1d(hit, [i + 1 for i, v in enumerate(szj) if v >= 0.03 * allm.sum()])
    bodies.append(np.isin(lbj, hit) if len(hit) else bm)
dist = np.stack([ndimage.distance_transform_edt(~bm) for bm in bodies])
for i, sz0 in enumerate(s0):
    comp = lb0 == i + 1
    if any((comp & bm).any() for bm in bodies): continue   # 몸통 덩어리(붙은 캐릭터 포함)는 위의 나눔 그대로
    near = int(np.argmin([d[comp].min() for d in dist]))
    own[ndimage.binary_dilation(comp, iterations=3) & (a > 0)] = near + 1

for j, uid in enumerate(ids):
    p = px.copy(); p[(own != j + 1), 3] = 0
    al = p[:, :, 3]
    # 떠 있는 장식(보석·꽃잎·물방울)은 남기고, 지우는 것: ① 옆 캐릭터에서 잘려 온 조각(다른 캐릭터와의 경계선에 닿은 작은 덩어리) ② 먼지
    m = al > 60
    lb, kk = ndimage.label(m); s2 = ndimage.sum(m, lb, range(1, kk + 1)); main = int(np.argmax(s2)) + 1
    other = (own != j + 1) & (a > 60)
    edge = ndimage.binary_dilation(other, iterations=3)
    drop = np.zeros(a.shape, bool)
    for i, s in enumerate(s2):
        if i + 1 == main: continue
        comp = lb == i + 1
        if s < 0.0005 * s2.sum() or (s < 0.05 * s2.sum() and (comp & edge).any()): drop |= comp
    p[ndimage.binary_dilation(drop, iterations=2) & ~ndimage.binary_dilation(lb == main, iterations=1), 3] = 0
    ys, xs = np.where(p[:, :, 3] > 10)
    img = Image.fromarray(p).crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    path = os.path.join(out, f'{uid}_{side}.png'); img.save(path); print(uid, img.size)

if cmp_dir:   # 비교용: 유닛 그림 줄 + 검은 실루엣 줄
    W, H = 360, 400
    c = Image.new('RGBA', (W * n, H * 2 + 30), (239, 220, 182, 255))
    for j, uid in enumerate(ids):
        t = Image.open(os.path.join(out, f'{uid}_{side}.png')).convert('RGBA'); t.thumbnail((W - 20, H - 20))
        c.alpha_composite(t, (j * W + (W - t.width) // 2, H - t.height))
        sil = Image.new('RGBA', t.size, (30, 30, 30, 255)); sil.putalpha(Image.fromarray(np.where(np.asarray(t)[:, :, 3] > 40, 255, 0).astype('uint8')))
        c.alpha_composite(sil, (j * W + (W - t.width) // 2, 2 * H + 30 - t.height))
    os.makedirs(cmp_dir, exist_ok=True); fp = os.path.join(cmp_dir, f'{grade}_{side}_{part}.png'); c.save(fp); print('비교:', fp)
