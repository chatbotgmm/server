# 배경 9곳을 선명하게: GPT 배경 시트(art/gpt/bg_all.png, 3×3)의 칸을 AI 업스케일(Real-ESRGAN animevideov3 ×4) 뒤 16:10으로 잘라 저장
# 칸 원본이 418×261px밖에 안 돼서, 그냥 늘리면 폰에서 깨져 보임(v0.9.87까지)
# 사용: VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json python3 -I scripts/upscale-bg.py [art/gpt/bg_all.png]
#   준비: scripts/upscale-art.py 맨 위 설명과 같음(realesrgan-ncnn-py, 그래픽카드 없으면 lavapipe)
import sys, os
from PIL import Image
from realesrgan_ncnn_py import Realesrgan
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'art/gpt/bg_all.png')
names = 'harbor,ink,deep,hall,market,strait,laundry,sky,nest'.split(',')
im = Image.open(src).convert('RGB'); cw, ch = im.width // 3, im.height // 3
model = Realesrgan(gpuid=0, model=3)   # 3 = realesr-animevideov3-x4
for i, n in enumerate(names):
    c = im.crop(((i % 3) * cw, (i // 3) * ch, (i % 3 + 1) * cw, (i // 3 + 1) * ch))
    tw, th = c.width, round(c.width * 10 / 16)
    if th > c.height: th, tw = c.height, round(c.height * 16 / 10)
    l, t = (c.width - tw) // 2, (c.height - th) // 2
    c = model.process_pil(c.crop((l, t, l + tw, t + th))).resize((1600, 1000), Image.LANCZOS)
    c.save(os.path.join(root, f'public/battle/bg/{n}.jpg'), quality=86, optimize=True, progressive=True); print(n, c.size, flush=True)
