# 그림 선명하게 키우기(애니메 그림용 AI 업스케일, Real-ESRGAN animevideov3 ×2) — 투명 배경 유지
# 준비(한 번): pip install realesrgan-ncnn-py numpy scipy pillow  + 리눅스는 apt install libomp5 mesa-vulkan-drivers(그래픽카드 없으면 CPU로)
# 사용: python3 -I scripts/upscale-art.py <입력.png>... out=<폴더> [h=768] [webp]
#   h: 결과 높이(가로는 비율대로). webp: .webp(품질 90)로 저장(같은 이름, 확장자만 바뀜)
#   그래픽카드가 없으면 VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json 를 앞에 붙여 실행
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage
from realesrgan_ncnn_py import Realesrgan

opts = dict(v.split('=', 1) for v in sys.argv[1:] if '=' in v)
files = [v for v in sys.argv[1:] if '=' not in v and v != 'webp']
webp, H, out = 'webp' in sys.argv[1:], int(opts.get('h', 768)), opts['out']
os.makedirs(out, exist_ok=True)
model = Realesrgan(gpuid=0, model=0)   # 0 = realesr-animevideov3-x2

for f in files:
    im = Image.open(f).convert('RGBA'); px = np.array(im); a = px[:, :, 3]
    # 투명한 곳의 색을 가장 가까운 그림 색으로 채움(가장자리에 검은 테두리가 생기지 않게)
    if (a < 8).any() and (a >= 8).any():
        idx = ndimage.distance_transform_edt(a < 8, return_distances=False, return_indices=True)
        px[:, :, :3] = px[idx[0], idx[1], :3]
    up = model.process_pil(Image.fromarray(px[:, :, :3])).convert('RGBA')
    up.putalpha(Image.fromarray(a).resize(up.size, Image.LANCZOS))
    up = up.resize((max(1, round(up.width * H / up.height)), H), Image.LANCZOS)
    name = os.path.splitext(os.path.basename(f))[0]
    p = os.path.join(out, name + ('.webp' if webp else '.png'))
    up.save(p, quality=90, method=6) if webp else up.save(p, optimize=True)
    print(p, up.size, os.path.getsize(p) // 1024, 'KB')
