# GPT 시트를 게임 그림으로 넣기(빌런 앞모습·배경)
# 사용:
#   빌런: python3 -I scripts/place-art.py villains <시트.png> <열x행> <V1,V2,...> [label=50] [flip]
#         → public/battle/sprites/V1_front.png (높이 512, 투명 배경, 칸마다 그림만 잘라 크기 맞춤)
#           flip: 좌우 반전(빌런은 화면 오른쪽에서 왼쪽의 내 갈매미를 봐야 함)
#   배경: python3 -I scripts/place-art.py bg <시트.png> <열x행> <harbor,ink,...> [label=50]
#         → public/battle/bg/harbor.jpg (가로 1024, 16:10으로 잘라 JPEG)
import sys, os
import numpy as np
from PIL import Image

opts = dict(v.split('=', 1) for v in sys.argv[1:] if '=' in v)
args = [v for v in sys.argv[1:] if '=' not in v]
kind, src, grid, names = args[0], args[1], args[2], args[3].split(',')
flip = 'flip' in args[4:]
gx, gy = (int(v) for v in grid.lower().split('x'))
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
im = Image.open(src).convert('RGBA'); w, h = im.size; cw, ch = w // gx, h // gy; lab = int(opts.get('label', 0))

if kind == 'villains':
    a = np.asarray(im)[:, :, 3]
    if not all(a[y, x] < 10 for y, x in [(1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)]):
        from rembg import remove, new_session
        im = remove(im.convert('RGB'), session=new_session('isnet-anime')).convert('RGBA')
        print('투명 배경이 아니어서 rembg로 배경을 지웠어요')
    out = os.path.join(root, 'public/battle/sprites')
    for i, n in enumerate(names):
        if not n or n == '-': continue
        x0, y0 = (i % gx) * cw, (i // gx) * ch
        cell = np.array(im.crop((x0, y0, x0 + cw, y0 + ch))); cell[:lab, :, 3] = 0
        a = cell[:, :, 3]; a[a < 30] = 0
        c = Image.fromarray(cell); box = c.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
        if not box: print(f'{n}: 빈 칸'); continue
        c = c.crop(box)
        if flip: c = c.transpose(Image.FLIP_LEFT_RIGHT)
        c = c.resize((max(1, round(c.width * 512 / c.height)), 512), Image.LANCZOS)
        c.save(os.path.join(out, f'{n}_front.png')); print(f'{n}_front.png {c.size}')
elif kind == 'bg':
    out = os.path.join(root, 'public/battle/bg'); os.makedirs(out, exist_ok=True)
    for i, n in enumerate(names):
        x0, y0 = (i % gx) * cw, (i // gx) * ch
        c = im.crop((x0, y0 + lab, x0 + cw, y0 + ch)).convert('RGB')
        tw, th = c.width, round(c.width * 10 / 16)          # 16:10로 가운데 자르기
        if th > c.height: th, tw = c.height, round(c.height * 16 / 10)
        l, t = (c.width - tw) // 2, (c.height - th) // 2
        c = c.crop((l, t, l + tw, t + th)).resize((1024, 640), Image.LANCZOS)
        c.save(os.path.join(out, f'{n}.jpg'), quality=82); print(f'{n}.jpg')
else:
    sys.exit('villains 또는 bg')
