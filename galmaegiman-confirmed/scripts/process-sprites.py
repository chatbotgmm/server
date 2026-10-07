# 전투 그림 후처리: art/raw/<ID>_s0.png(앞·뒤 두 칸 시트) → 배경 제거 → 두 칸으로 잘라
# public/battle/sprites/<ID>_s0_front.png, <ID>_s0_back.png
# 배경 제거: rembg(일러스트용 isnet-anime 모델)가 있으면 그것으로(어두운 배경·후광도 지움), 없으면 자홍색(#FF00FF) 배경만 지움
#   설치: pip install "rembg[cpu]"  (처음 실행 때 모델 약 176MB 내려받음)
# 사용: python3 scripts/process-sprites.py D1 D2 ...   (아무것도 안 주면 art/raw 전부)
import sys, os, glob, subprocess, tempfile
from PIL import Image

def key_magenta(im):
    im = im.convert('RGBA'); px = im.load(); w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            d = max(0, min(r, b) - g)
            if r > 150 and b > 150 and g < 120: px[x, y] = (r, g, b, 0)
            elif d > 60 and r > 120 and b > 120: px[x, y] = (r, g, b, min(a, int(max(0, min(255, (130 - d) * 3)))))
    return im

_session = None
def remove_bg(im):
    global _session
    try:
        from rembg import remove, new_session
    except ImportError:
        return key_magenta(im)
    if _session is None: _session = new_session('isnet-anime')
    return remove(im, session=_session)

def middle_cut(im):
    # 앞·뒤 칸이 붙어 있을 때를 대비해, 가운데(35~65%)에서 그림이 가장 적은 세로줄
    a = im.getchannel('A').load(); w, h = im.size
    cols = [sum(1 for y in range(0, h, 2) if a[x, y] > 24) for x in range(w)]
    lo, hi = int(w * 0.35), int(w * 0.65)
    return min(range(lo, hi), key=lambda x: cols[x])

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ids = sys.argv[1:] or sorted(os.path.basename(p).split('_')[0] for p in glob.glob(os.path.join(root, 'art/raw/*_s0.png')))
    out = os.path.join(root, 'public/battle/sprites'); os.makedirs(out, exist_ok=True)
    for i in ids:
        src = os.path.join(root, f'art/raw/{i}_s0.png')
        if not os.path.exists(src): print(f'{i}: 원본 없음'); continue
        with tempfile.TemporaryDirectory() as t:
            keyed_path = os.path.join(t, 'keyed.png')
            keyed = remove_bg(Image.open(src).convert('RGB')); keyed.save(keyed_path)
            cut = middle_cut(keyed)
            r = subprocess.run([sys.executable, '-I', os.path.join(root, 'scripts/slice-sprites.py'), keyed_path, out,
                                f'{i}_s0_front,{i}_s0_back', 'auto', '512', f'cut={cut}'], capture_output=True, text=True)
            print(f'{i}: ' + (r.stdout.strip().replace('\n', ' | ') or r.stderr.strip()[-300:]))

main()
