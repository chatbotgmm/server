# 등급별 시트(한 장에 여러 유닛) → 유닛별 그림으로 자르기
# 사용: python3 -I scripts/split-grade-sheet.py <시트.png> <등급 D|T|E|I> <front|back> [개수(기본 D·T·E 8, I 9)] [순서=ids]
#   결과: art/gpt/<ID>_<front|back>.png (투명 배경, 그림만 딱 맞게 자름)
#   그다음 python3 -I scripts/process-sprites.py 를 돌리면 앞·뒤를 짝지어 public/battle/sprites/ 에 넣음
# 순서: 왼쪽 위부터 오른쪽으로, 줄이 끝나면 다음 줄 = D1, D2, ... (다른 순서면 order=D3,D1,... 로 지정)
# 배경: 진짜 투명이면 그대로, 아니면 rembg(isnet-anime)로 지움
# 칸이 고른 격자 시트(예: 33종 11×3, 칸 왼쪽 위에 번호 글자): grid=11x3 label=50 → 칸대로 자르고 칸 위쪽 50px(글자)를 지움
#   등급 자리에 ALL을 주면 순서 D1~D8, T1~T8, E1~E8, I1~I9
# 승진 단계 시트(GPT evo_D_s3_front.png 등): stage=3 → art/gpt/<ID>_s3_<front|back>.png
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

opts = dict(v.split('=', 1) for v in sys.argv[1:] if '=' in v)
args = [v for v in sys.argv[1:] if '=' not in v]
src, grade, side = args[0], args[1].upper(), args[2]
n = int(args[3]) if len(args) > 3 else (9 if grade == 'I' else 8)
ALL = [f'{g}{k}' for g, m in (('D', 8), ('T', 8), ('E', 8), ('I', 9)) for k in range(1, m + 1)]
ids = opts['order'].split(',') if 'order' in opts else ALL if grade == 'ALL' else [f'{grade}{k}' for k in range(1, n + 1)]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
stg = f"_s{opts['stage']}" if opts.get('stage') else ''   # 승진 단계 그림이면 파일 이름에 _s<단계>

im = Image.open(src).convert('RGBA'); w, h = im.size
a = np.asarray(im)[:, :, 3]
if not all(a[y, x] < 10 for y, x in [(1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)]):
    from rembg import remove, new_session
    im = remove(im.convert('RGB'), session=new_session('isnet-anime')).convert('RGBA')
    print('투명 배경이 아니어서 rembg로 배경을 지웠어요')
px = np.array(im); a = px[:, :, 3].copy()
a[a < 30] = 0; px[:, :, 3] = a                       # 아주 옅은 얼룩 제거

if 'grid' in opts:   # 격자 시트: 칸대로 자르기
    gx, gy = (int(v) for v in opts['grid'].lower().split('x')); cw, ch = w // gx, h // gy; lab_h = int(opts.get('label', 0))
    out = os.path.join(root, 'art/gpt'); os.makedirs(out, exist_ok=True)
    for idx, uid in enumerate(ids):
        x0, y0 = (idx % gx) * cw, (idx // gx) * ch
        cell = px[y0:y0 + ch, x0:x0 + cw].copy(); cell[:lab_h, :, 3] = 0
        img = Image.fromarray(cell); box = img.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
        if not box: print(f'{uid}_{side}: 빈 칸'); continue
        img = img.crop(box); img.save(os.path.join(out, f'{uid}{stg}_{side}.png'))
        edge = [box[0] <= 1, box[2] >= cw - 1, box[1] <= lab_h + 1, box[3] >= ch - 1]
        print(f'{uid}_{side}: {img.size}' + ('  ! 칸 가장자리에 닿음(잘렸을 수 있음)' if any(edge) else ''))
    sys.exit(0)

mask = a > 24
grown = ndimage.binary_dilation(mask, iterations=max(4, w // 150))   # 몸에서 살짝 떨어진 장식도 한 덩어리로
lab, k = ndimage.label(grown)
sizes = ndimage.sum(mask, lab, range(1, k + 1))
order = np.argsort(sizes)[::-1]
big = [int(i) + 1 for i in order[:len(ids)]]
if k < len(ids): sys.exit(f'그림 덩어리가 {k}개뿐이에요(필요 {len(ids)}개). 캐릭터끼리 붙어 있는지 확인해 주세요')
small_ratio = sizes[big[-1] - 1] / sizes[big[0] - 1]
if small_ratio < 0.15: print(f'  ! 가장 작은 덩어리가 가장 큰 것의 {small_ratio:.0%}예요. 캐릭터가 둘로 갈라졌거나 붙었을 수 있어요')

cent = ndimage.center_of_mass(mask, lab, big)
boxes = ndimage.find_objects(lab)
# 나머지 작은 덩어리(파편·효과)는 가장 가까운 캐릭터에 붙임
owner = {b: b for b in big}
for j in range(1, k + 1):
    if j in owner: continue
    cy, cx = ndimage.center_of_mass(mask, lab, j) if sizes[j - 1] else (0, 0)
    owner[j] = min(big, key=lambda b: (cent[big.index(b)][0] - cy) ** 2 + (cent[big.index(b)][1] - cx) ** 2)

# 읽는 순서: 세로 중심으로 줄을 묶고, 줄 안에서는 왼쪽부터
hts = [boxes[b - 1][0].stop - boxes[b - 1][0].start for b in big]
rows = []
for b in sorted(big, key=lambda b: cent[big.index(b)][0]):
    cy = cent[big.index(b)][0]
    if rows and abs(cy - np.mean([cent[big.index(x)][0] for x in rows[-1]])) < np.median(hts) * 0.5: rows[-1].append(b)
    else: rows.append([b])
seq = [b for r in rows for b in sorted(r, key=lambda b: cent[big.index(b)][1])]
print('줄별 개수:', [len(r) for r in rows])

out = os.path.join(root, 'art/gpt'); os.makedirs(out, exist_ok=True)
for uid, b in zip(ids, seq):
    keep = np.isin(lab, [j for j, o in owner.items() if o == b]) & mask
    one = px.copy(); one[:, :, 3] = np.where(keep, px[:, :, 3], 0)
    img = Image.fromarray(one); img = img.crop(img.getchannel('A').getbbox())
    path = os.path.join(out, f'{uid}{stg}_{side}.png'); img.save(path)
    print(f'{uid}_{side}: {img.size}')
