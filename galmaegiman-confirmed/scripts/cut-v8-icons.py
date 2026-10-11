# 받은 게임 모드 시안(art/ref/v8/panel_*.png)에서 아이콘 6개를 잘라 바탕(크림색)을 지움 → public/battle/icons/mode-*.png
# 바탕 지우기: 잘라낸 칸 가장자리와 이어진, 가장자리 색과 비슷한 픽셀만 투명(아이콘 안쪽 흰 부분은 안 지움)
import os
import numpy as np
from PIL import Image
from scipy import ndimage
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CUT = {'panel_pvp.png': {'mode-pvp': (82, 40, 222, 155), 'mode-normal': (92, 176, 204, 258), 'mode-rank': (96, 296, 198, 398)},
       'panel_campaign.png': {'mode-camp': (82, 40, 250, 155), 'u-story': (92, 176, 208, 262), 'mode-adv': (98, 292, 198, 398)}}
for f, cuts in CUT.items():
    src = Image.open(os.path.join(root, 'art/ref/v8', f)).convert('RGBA')
    for name, box in cuts.items():
        im = np.array(src.crop(box)).astype(int); rgb = im[:, :, :3]
        edge = np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]]); bg = np.median(edge, axis=0)
        near = np.sqrt(((rgb - bg) ** 2).sum(2)) < (16 if name == 'u-story' else 30)
        lab, _ = ndimage.label(near); border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        keep = ~np.isin(lab, list(border))
        # 칸 구분선·테두리 조각은 따로 떨어진 덩어리 → 가장 큰 덩어리(아이콘)만, 그 안 구멍(책장 같은 밝은 면)은 채움
        kl, kn = ndimage.label(ndimage.binary_opening(keep, iterations=2))
        if kn: keep = ndimage.binary_fill_holes(kl == 1 + int(np.argmax(ndimage.sum(np.ones_like(kl), kl, range(1, kn + 1)))))
        drop = ~keep
        a = np.where(drop, 0, 255).astype(np.uint8); a = ndimage.gaussian_filter(a.astype(float), 0.7).clip(0, 255).astype(np.uint8)
        im[:, :, 3] = np.minimum(im[:, :, 3], a)
        out = Image.fromarray(im.astype(np.uint8)); bb = out.getbbox(); out = out.crop(bb)
        out.save(os.path.join(root, f'public/battle/icons/{name}.png'), optimize=True); print(name, out.size)
