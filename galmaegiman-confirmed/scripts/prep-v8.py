# 8번 디자인 소스(art/ref/v8) → 게임에 쓰는 파일
#  - 배경 4장(830×466으로 받음): AI 업스케일(Real-ESRGAN animevideov3 ×4) 뒤 1920×1080 → public/battle/bg/<이름>.jpg
#  - 메뉴 버튼 시트(투명, 4개 가로): 덩어리마다 잘라 높이 180px → public/battle/icons/btn-<메뉴>.png
# 사용: VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json python3 -I scripts/prep-v8.py
import os
import numpy as np
from PIL import Image
from scipy import ndimage
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ref = os.path.join(root, 'art/ref/v8')
from realesrgan_ncnn_py import Realesrgan
model = Realesrgan(gpuid=0, model=3)
for n in ['attic', 'cafe', 'greenhouse', 'cabin']:
    im = Image.open(os.path.join(ref, f'bg_{n}.webp')).convert('RGB')
    out = model.process_pil(im).resize((1920, 1080), Image.LANCZOS)
    out.save(os.path.join(root, f'public/battle/bg/{n}.jpg'), quality=86, optimize=True, progressive=True); print(n, out.size, flush=True)
sheet = Image.open(os.path.join(ref, 'menu_buttons4.webp')).convert('RGBA'); a = np.array(sheet)[:, :, 3] > 20
lab, k = ndimage.label(ndimage.binary_closing(a, iterations=3)); sl = sorted(ndimage.find_objects(lab), key=lambda s: s[1].start)
big = [s for s in sl if (s[0].stop - s[0].start) * (s[1].stop - s[1].start) > 0.02 * a.size]
for name, s in zip(['party', 'draw', 'units', 'shop'], big):
    b = sheet.crop((s[1].start, s[0].start, s[1].stop, s[0].stop)); b = b.resize((round(b.width * 180 / b.height), 180), Image.LANCZOS)
    b.save(os.path.join(root, f'public/battle/icons/btn-{name}.png'), optimize=True); print('btn', name, b.size)
