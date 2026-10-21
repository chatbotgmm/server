"""네이티브 실행을 대신하지 않는 조합 레이아웃 산술/소스 회귀 검사."""
from pathlib import Path
import math
root = Path(__file__).resolve().parents[1]
p = root / 'app/android/app/src/main/java/org/dpdns/gmm/NativeEconomy.java'
if not p.exists(): p = Path(__file__).with_name('NativeEconomy.java')
s = p.read_text()
# 검증한 산술과 실제 런타임 코드가 분리되어 조용히 달라지는 것을 차단한다.
for formula in ['width / Math.max(1, h.dp(48))', '(slots + columns - 1) / columns',
                'Math.max(h.dp(48), viewportHeight / Math.max(1, rows))',
                'int slots = Math.max(0, limit)', 'data.optInt("maxMaterials", 6)',
                'header.addView(tools', 'browseActions.addView(add']:
    assert formula in s, formula
assert 'Math.max(4, m.mixDraft.size() + 1)' not in s
# px, density, 좌우 inset 합, 상하 inset 합. fontScale 증가는 제목 높이에 반영.
cases = [(915,412,1,0,0),(1080,444,1,0,0),(1080,444,1.5,48,24),
         (1080,444,2,48,24),(1280,720,1,0,0),(915,412,1,48,24)]
checks = 0
for w,h,d,ix,iy in cases:
  W,H=(w-ix)/d,(h-iy)/d
  page_width=W-24
  body=H-12-52-54  # page padding, existing header, existing footer+gap
  card_h=body-48   # pager; no extra tab/add/frame rows
  old_card_h=body-48-48-48-(10 if H<348 else 18)
  assert card_h>old_card_h+90
  # Left original 3:4 art contains, no crop or arbitrary width stretch.
  card_w=min((page_width-8)*.34,card_h*.75)
  assert card_w>0 and card_h>0
  if d==1 and ix==0 and w in (915,1080): assert card_h>=240
  selected=((page_width-8)*.66-22)*1.1/2.1-20
  for fs in (1,1.3,1.5):
    heading=max(32,math.ceil(22*fs)+8)
    viewport=max(0,body-18-heading)
    for slots in (0,2,4,5,6,9,12):
      columns=max(1,min(2 if slots<=4 else 3,int(selected)//48))
      rows=(slots+columns-1)//columns
      rh=max(48,int(viewport)//max(1,rows))
      assert selected/columns>=48
      assert rh>=48
      rects=[]
      for i in range(slots):
        col,row=i%columns,i//columns
        box=(col*selected/columns,row*rh,(col+1)*selected/columns,(row+1)*rh)
        assert box[0]>=0 and box[2]<=selected+1e-6
        assert box[3]<=rows*rh
        rects.append(box)
      if slots<=6 and d==1: assert rows*rh<=viewport  # 기본 화면 여섯 칸 동시 표시
      checks+=1
  print(f'{w}x{h} density={d} insets={ix}/{iy}: card area height {card_h:.1f}dp (old {old_card_h:.1f}dp)')
# 동작 보존: 탐색 경로는 draft/api를 만지지 않는다.
carousel=s[s.index('  final class MixCarousel'):s.index('  boolean tightBench')]
assert 'mixDraft.add' not in carousel and 'api.' not in carousel
assert 'case MotionEvent.ACTION_CANCEL:' in carousel
assert 'm.mixDraft.size() >= limit' in s and 'Collections.frequency(m.mixDraft, id) >= unit.optInt("usable")' in s
print(f'PASS {checks} geometry combinations + source/interaction guards (not Android rendering)')
