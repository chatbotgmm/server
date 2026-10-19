#!/usr/bin/env python3
"""웹(공개 사이트·웹 게임)용 그림·글꼴을 앱 자산에서 만든다. 원본은 앱 assets(harbor-v1, battle-v1, native 글꼴)이다.
- 그림: PNG → WebP(배경 q82, 표면·아이콘 q90, 알파 유지). 크기는 원본 그대로(9분할 inset 좌표를 바꾸지 않기 위해).
- 글꼴: KS X 1001 한글 2350자 + 저장소 글(data/, src/, public/site, public/game)에 나온 한글 + ASCII·문장부호만 남긴 woff2.
사용(galmaegiman-confirmed 폴더): python3 scripts/web/build-site-assets.py   # fontTools·brotli 필요
"""
import os, re, sys, glob
from PIL import Image
from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, 'app/android/app/src/main/assets')
OUT = os.path.join(ROOT, 'public/site/a')
os.makedirs(OUT, exist_ok=True)

def webp(src, name, q):
    im = Image.open(src)
    im.save(os.path.join(OUT, name + '.webp'), 'WEBP', quality=q, method=6)

for f in sorted(glob.glob(os.path.join(A, 'harbor-v1/*.png'))):
    n = os.path.basename(f)[:-4]
    webp(f, n, 82 if n.startswith('bg-') else 90)
for f in sorted(glob.glob(os.path.join(A, 'battle-v1/*.png'))):
    n = os.path.basename(f)[:-4]
    webp(f, 'b-' + n, 82 if n.startswith('bg-') else 90)

chars = set(chr(c) for c in range(0x20, 0x7f))
chars |= set('·…“”‘’「」『』《》〈〉→←↑↓×○●◆◇■□★☆♥※—–•‹›«»ⓘ✓✔％％∼～、。')
for c in range(0xAC00, 0xD7A4):
    try:
        chr(c).encode('euc_kr'); chars.add(chr(c))
    except UnicodeEncodeError:
        pass
for pat in ['data/**/*', 'src/**/*.ts', 'public/site/**/*.js', 'public/site/**/*.html', 'public/game/**/*.js', 'public/game/**/*.html']:
    for f in glob.glob(os.path.join(ROOT, pat), recursive=True):
        if os.path.isfile(f):
            try:
                chars |= set(re.findall('[가-힣]', open(f, encoding='utf-8').read()))
            except UnicodeDecodeError:
                pass
text = ''.join(sorted(chars))
for ttf, name in [('GowunDodum-Regular.ttf', 'gowun'), ('Jua-Regular.ttf', 'jua')]:
    opts = subset.Options(); opts.flavor = 'woff2'; opts.layout_features = ['*']
    font = subset.load_font(os.path.join(A, 'native', ttf), opts)
    s = subset.Subsetter(opts); s.populate(text=text); s.subset(font)
    subset.save_font(font, os.path.join(OUT, name + '.woff2'), opts)
for lic in ['OFL-GowunDodum.txt', 'OFL-Jua.txt']:
    open(os.path.join(OUT, lic), 'w').write(open(os.path.join(A, 'native', lic)).read())
print('글자 수', len(chars))
for f in sorted(os.listdir(OUT)):
    print(f, os.path.getsize(os.path.join(OUT, f)))
