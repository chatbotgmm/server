# 전투 그림 후처리: art/raw/<ID>_s0.png(앞·뒤 두 칸 시트) → 자홍색 배경 제거 → 두 칸으로 잘라
# public/battle/sprites/<ID>_s0_front.png, <ID>_s0_back.png
# 사용: python3 -I scripts/process-sprites.py D1 D2 ...   (아무것도 안 주면 art/raw 전부)
import sys, os, glob, subprocess, tempfile
from PIL import Image

def key_magenta(src, dst):
    im = Image.open(src).convert('RGBA'); px = im.load(); w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            # 자홍색(빨강·파랑 높고 초록 낮음)과의 거리로 투명도: 가까우면 투명, 경계는 부드럽게
            d = max(0, min(r, b) - g)              # 자홍 정도
            if r > 150 and b > 150 and g < 120:
                px[x, y] = (r, g, b, 0)
            elif d > 60 and r > 120 and b > 120:
                alpha = int(max(0, min(255, (130 - d) * 3)))
                px[x, y] = (r, g, b, min(a, alpha))
    im.save(dst)

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ids = sys.argv[1:] or sorted(os.path.basename(p).split('_')[0] for p in glob.glob(os.path.join(root, 'art/raw/*_s0.png')))
    out = os.path.join(root, 'public/battle/sprites'); os.makedirs(out, exist_ok=True)
    for i in ids:
        src = os.path.join(root, f'art/raw/{i}_s0.png')
        if not os.path.exists(src): print(f'{i}: 원본 없음'); continue
        with tempfile.TemporaryDirectory() as t:
            keyed = os.path.join(t, 'keyed.png'); key_magenta(src, keyed)
            r = subprocess.run([sys.executable, '-I', os.path.join(root, 'scripts/slice-sprites.py'), keyed, out, f'{i}_s0_front,{i}_s0_back', 'auto', '512'], capture_output=True, text=True)
            print(f'{i}: ' + (r.stdout.strip().replace('\n', ' | ') or r.stderr.strip()[-300:]))

main()
