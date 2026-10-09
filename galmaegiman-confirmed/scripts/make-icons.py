# 갈매미 큰 그림(GPT 할 일 7, art/gpt/app_icon.png) 하나로 모든 아이콘·시작 화면 다시 만들기
# 사용: python3 -I scripts/make-icons.py art/gpt/app_icon.png
#  - 사이트: public/gull.png(128), favicon-32, icon-192/512(투명), icon-192/512-maskable(파란 바탕, 가운데 안전 영역)
#  - 안드로이드 앱: mipmap-*/ic_launcher(둥근 사각)·_round(원)·_foreground(적응형 아이콘 앞면), drawable*/splash(시작 화면)
#  - 구글 플레이 아이콘: app/store/play-icon-512.png (512×512, 파란 바탕 꽉 채움 — 모서리는 플레이가 둥글게 깎음)
import sys, os
from PIL import Image, ImageDraw

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLUE, BEIGE = (43, 124, 196, 255), (247, 240, 227, 255)
src = Image.open(sys.argv[1]).convert('RGBA')
art = src.crop(src.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox())

def fit(size, frac, bg=None, shape=None):
    """size×size 판 가운데에 그림을 frac 비율로. bg가 있으면 바탕(shape: square·rounded·circle)"""
    im = Image.new('RGBA', (size, size))
    if bg:
        m = Image.new('L', (size, size)); d = ImageDraw.Draw(m)
        if shape == 'circle': d.ellipse((0, 0, size - 1, size - 1), fill=255)
        elif shape == 'rounded': d.rounded_rectangle((0, 0, size - 1, size - 1), radius=size // 6, fill=255)
        else: d.rectangle((0, 0, size, size), fill=255)
        im.paste(Image.new('RGBA', (size, size), bg), (0, 0), m)
    a = art.copy(); a.thumbnail((round(size * frac), round(size * frac)), Image.LANCZOS)
    im.alpha_composite(a, ((size - a.width) // 2, (size - a.height) // 2))
    return im

def save(im, rel):
    p = os.path.join(root, rel); os.makedirs(os.path.dirname(p), exist_ok=True); im.save(p, optimize=True); print(rel, im.size)

save(fit(128, 1.0), 'public/gull.png')
save(fit(32, 1.0), 'public/favicon-32.png')
for n in (192, 512):
    save(fit(n, 0.96), f'public/icon-{n}.png')
    save(fit(n, 0.62, BLUE), f'public/icon-{n}-maskable.png')
save(fit(512, 0.78, BLUE), 'app/store/play-icon-512.png')

res = 'app/android/app/src/main/res'
for name, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    save(fit(px, 0.80, BLUE, 'rounded'), f'{res}/mipmap-{name}/ic_launcher.png')
    save(fit(px, 0.74, BLUE, 'circle'), f'{res}/mipmap-{name}/ic_launcher_round.png')
    # 적응형 아이콘 앞면: 108dp 중 가운데 66dp만 늘 보임 → 그림은 약 58%
    save(fit(px * 9 // 4, 0.58), f'{res}/mipmap-{name}/ic_launcher_foreground.png')

def splash(w, h):
    im = Image.new('RGBA', (w, h), BEIGE); s = round(min(w, h) * 0.32)
    a = art.copy(); a.thumbnail((s, s), Image.LANCZOS); im.alpha_composite(a, ((w - a.width) // 2, (h - a.height) // 2))
    return im.convert('RGB')
for d in sorted(os.listdir(os.path.join(root, res))):
    p = os.path.join(root, res, d, 'splash.png')
    if os.path.exists(p):
        w, h = Image.open(p).size; save(splash(w, h), f'{res}/{d}/splash.png')
