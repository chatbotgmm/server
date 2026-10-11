# 받은 디자인 소스(v0.13.0) → 게임 파일
#  rooms: <Z플립 레이어 폴더>/{attic,greenhouse,seaside_cafe,ship_workshop}/0x_*.png (모두 2640×1080, 같은 원점)
#    → public/battle/bg/<방>.jpg(합친 그림: 흐린 배경용) · <방>base.jpg(배경) · <방>plat.webp · <방>char.webp(전체 캔버스 그대로, 위치 안 바뀌게 자르지 않음)
#  intro: <시작·로딩 폴더>/objects/*.png + layout.json → public/duel/intro/<이름>.webp(+ 바다 배경 jpg), jua.woff2, layout.json
# 사용: python3 -I scripts/prep-v9.py <Z플립 폴더> <시작·로딩 폴더>
import os, sys, json, shutil
from PIL import Image
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
zf, intro = sys.argv[1], sys.argv[2]
bg = os.path.join(root, 'public/battle/bg'); out = os.path.join(root, 'public/duel/intro'); os.makedirs(out, exist_ok=True)
ROOM = {'attic': 'attic', 'greenhouse': 'greenhouse', 'seaside_cafe': 'cafe', 'ship_workshop': 'cabin'}
for src, name in ROOM.items():
    d = os.path.join(zf, src)
    Image.open(os.path.join(d, '00_preview.jpg')).convert('RGB').save(os.path.join(bg, f'{name}.jpg'), quality=84, optimize=True, progressive=True)
    Image.open(os.path.join(d, '01_background.png')).convert('RGB').save(os.path.join(bg, f'{name}base.jpg'), quality=84, optimize=True, progressive=True)
    for f, tag in [('02_platform.png', 'plat'), ('03_character.png', 'char')]:
        Image.open(os.path.join(d, f)).convert('RGBA').save(os.path.join(bg, f'{name}{tag}.webp'), quality=88, method=6)
    print('방', name)
lay = json.load(open(os.path.join(intro, 'layout.json')))
done = set()
# 바뀌는 글(닉네임·불러오는 중·시작/로그인 글자)과 시안용 50% 채움은 그림 대신 실제 글자·막대로. 로딩의 바다·부두는 시작 화면과 같은 그림이라 하나만
SKIP = {'title_account_name_text', 'loading_loading_status_text', 'loading_loading_fill_example_50pct', 'title_start_button_text', 'loading_harbor_background', 'loading_wooden_dock'}
for scr, objs in lay['screens'].items():
    for o in objs:
        key = o['object'].split('/')[-1][:-4]          # 예: title_guardian
        if key in done or key in SKIP: continue
        done.add(key); im = Image.open(os.path.join(intro, o['object']))
        try: im.load()
        except OSError:   # 받은 개체 파일이 중간에 잘린 경우(부두 2개): 같은 원점의 전체 캔버스 레이어에서 layout 좌표로 잘라 씀
            im = Image.open(os.path.join(intro, o['layer'])).crop((o['x'], o['y'], o['x'] + o['width'], o['y'] + o['height'])); print('잘린 파일 → 레이어에서 잘라 씀:', o['object'])
        if o['name'] == 'harbor_background': im.convert('RGB').save(os.path.join(out, f'{key}.jpg'), quality=84, optimize=True, progressive=True)
        else: im.convert('RGBA').save(os.path.join(out, f'{key}.webp'), quality=90, method=6)
from fontTools.ttLib import TTFont
f = TTFont(os.path.join(intro, 'fonts/Jua-Regular.ttf')); f.flavor = 'woff2'; f.save(os.path.join(out, 'jua.woff2'))
shutil.copy(os.path.join(intro, 'fonts/OFL.txt'), os.path.join(out, 'jua-OFL.txt'))
json.dump(lay, open(os.path.join(out, 'layout.json'), 'w'), ensure_ascii=False, indent=1)
print('시작·로딩', len(done), '개')
