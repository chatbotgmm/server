# 전투 그림 후처리: art/raw/<ID>_s0.png(앞·뒤 두 칸 시트) → 배경 제거 → 두 칸으로 잘라
# public/battle/sprites/<ID>_s0_front.png, <ID>_s0_back.png
# 앞·뒤 칸을 먼저 나눈 뒤 칸마다 배경 제거: rembg(일러스트용 isnet-anime 모델)가 있으면 그것으로(어두운 배경·후광도 지움), 없으면 자홍색(#FF00FF) 배경만 지움
#   설치: pip install "rembg[cpu]"  (처음 실행 때 모델 약 176MB 내려받음)
# GPT가 앞·뒤를 따로 준 경우: art/gpt/<ID>_front.png, art/gpt/<ID>_back.png 가 있으면 그걸 먼저 씀
#   진짜 투명 배경(네 귀퉁이 투명)이면 배경 제거 없이 그대로, 아니면 위와 같이 배경 제거
# 사용: python3 scripts/process-sprites.py D1 D2 ...   (아무것도 안 주면 art/gpt·art/raw 전부)
#   flip=front  앞모습을 좌우반전(상대는 화면 왼쪽의 내 갈매미를 봐야 함. GPT 첫 시트는 앞모습이 모두 오른쪽을 봐서 이걸로 뒤집음)
#   flip=back / flip=both 도 가능
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

_sessions = {}
def _rembg(im, model):
    from rembg import remove, new_session
    if model not in _sessions: _sessions[model] = new_session(model)
    return remove(im, session=_sessions[model])

def strong(im):  # 거의 불투명한 픽셀 수(캐릭터를 얼마나 살렸는지)
    return sum(1 for v in im.getchannel('A').getdata() if v > 200)

def cleanup(im):
    # 배경 쪽 남은 얼룩: 아주 옅은 픽셀, 어둡고 반투명한 픽셀은 지움(어두운 캐릭터 몸은 불투명이라 남음)
    px = im.load(); w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 30 or (a < 128 and (r * 299 + g * 587 + b * 114) / 1000 < 70): px[x, y] = (r, g, b, 0)
    return im

def remove_bg(im):
    try:
        import rembg  # noqa: F401
    except ImportError:
        return key_magenta(im)
    # 일러스트 모델(isnet-anime)이 기본. 어두운 캐릭터 등에서 캐릭터를 많이 놓치면(불투명 픽셀이 u2net의 85% 미만) u2net 결과를 씀
    a = _rembg(im, 'isnet-anime'); u = _rembg(im, 'u2net')
    pick = a if strong(a) >= strong(u) * 0.85 else u
    return cleanup(pick)

def middle_cut(im):
    # 앞·뒤 칸 경계: 시트 전체를 배경 제거한 결과에서 가운데(35~65%) 중 그림이 가장 적은 세로줄
    a = im.getchannel('A').load(); w, h = im.size
    cols = [sum(1 for y in range(0, h, 2) if a[x, y] > 24) for x in range(w)]
    lo, hi = int(w * 0.35), int(w * 0.65)
    return min(range(lo, hi), key=lambda x: cols[x])

def is_transparent(im):
    if im.mode != 'RGBA': return False
    a = im.getchannel('A'); w, h = im.size
    return all(a.getpixel(p) < 10 for p in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)])

FLIP = dict(v.split('=', 1) for v in sys.argv[1:] if '=' in v).get('flip', '')
sys.argv = [v for v in sys.argv if '=' not in v]

def load_panel(path):
    im = Image.open(path)
    im = im.convert('RGBA') if im.mode in ('RGBA', 'LA', 'P') else im.convert('RGB')
    if is_transparent(im): return cleanup(im)
    return remove_bg(im.convert('RGB'))

def pair_sheet(front, back):
    # 앞·뒤 두 장을 가로로 붙여 한 시트로(사이 40px), 경계 x를 함께 돌려줌
    w, h = front.width + back.width + 40, max(front.height, back.height)
    sheet = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    sheet.paste(front, (0, h - front.height)); sheet.paste(back, (front.width + 40, h - back.height))
    return sheet, front.width + 20

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ids = sys.argv[1:] or sorted({os.path.basename(p).split('_')[0] for p in
                                  glob.glob(os.path.join(root, 'art/raw/*_s0.png')) + glob.glob(os.path.join(root, 'art/gpt/*_front.png'))})
    out = os.path.join(root, 'public/battle/sprites'); os.makedirs(out, exist_ok=True)
    for i in ids:
        src = os.path.join(root, f'art/raw/{i}_s0.png')
        gf, gb = (os.path.join(root, f'art/gpt/{i}_{s}.png') for s in ('front', 'back'))
        with tempfile.TemporaryDirectory() as t:
            keyed_path = os.path.join(t, 'keyed.png')
            if os.path.exists(gf) and os.path.exists(gb):
                f, bk = load_panel(gf), load_panel(gb)
                if FLIP in ('front', 'both'): f = f.transpose(Image.FLIP_LEFT_RIGHT)
                if FLIP in ('back', 'both'): bk = bk.transpose(Image.FLIP_LEFT_RIGHT)
                keyed, cut = pair_sheet(f, bk); keyed.save(keyed_path)
                slice_pair(root, i, keyed_path, out, cut); continue
            if not os.path.exists(src): print(f'{i}: 원본 없음'); continue
            # 칸마다 따로 배경 제거(시트 전체로 하면 뒷모습 몸통이 반투명해지는 경우가 있었음)
            # 칸마다 '시트 전체로 지운 결과'와 '칸만 지운 결과' 중 캐릭터(불투명 픽셀)가 더 많이 남은 쪽을 씀
            raw = Image.open(src).convert('RGB'); whole = remove_bg(raw); cut = middle_cut(whole); w, h = raw.size
            keyed = Image.new('RGBA', (w, h), (0, 0, 0, 0))
            for box in [(0, 0, cut, h), (cut, 0, w, h)]:
                keyed.paste(max([whole.crop(box), remove_bg(raw.crop(box))], key=strong), box[:2])
            keyed.save(keyed_path)
            slice_pair(root, i, keyed_path, out, cut)

def slice_pair(root, i, keyed_path, out, cut):
    r = subprocess.run([sys.executable, '-I', os.path.join(root, 'scripts/slice-sprites.py'), keyed_path, out,
                        f'{i}_s0_front,{i}_s0_back', 'auto', '512', f'split={cut}', 'anchor=box'], capture_output=True, text=True)
    print(f'{i}: ' + (r.stdout.strip().replace('\n', ' | ') or r.stderr.strip()[-300:]))

main()
