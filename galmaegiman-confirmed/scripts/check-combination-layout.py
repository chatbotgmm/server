"""네이티브 View 실행 대신 사용할 수 없는 독립 산술/소스 경계 검사."""
from pathlib import Path
import math
base=Path(__file__).parent
source=base.parent/'app/android/app/src/main/java/org/dpdns/gmm/NativeEconomy.java'
if not source.exists(): source=base/'NativeEconomy.java'
s=source.read_text()
for token in ['float[] weights = {.39f, .33f, .28f}', 'new LinearLayout.LayoutParams(h.dp(8),-1)', 'h.dp(720)',
              'h.dp(320+(int)Math.ceil(Math.max(0,scale-1)*100))',
              'MeasureSpec.makeMeasureSpec(contentHeight,MeasureSpec.EXACTLY)',
              'cell.setTag("mix-material-"', 'cell.setOnClickListener',
              'slot.setClickable(false)', 'viewport.setFillViewport(true)',
              'View empty = navyEmptyResult()', 'new HiddenRecipeFrame(h, true)']:
    assert token in s,token
checks=0
for width,height,density in [(915,412,1),(1080,444,1),(1080,444,1.5),(1080,444,2),(780,360,1),(1280,720,1)]:
 for insets in [(0,0),(48,24)]:
  for scale in [1,1.3,2]:
   vw=(width-insets[0])/density-24
   vh=(height-insets[1])/density-12-52
   content_w=max(vw,720)
   content_h=max(vh,320+math.ceil(max(0,scale-1)*100))
   # Final approved texture design uses two explicit8dp gaps.
   weights=[.39,.33,.28]
   columns=[(content_w-16)*x for x in weights]
   assert min(columns)>48
   assert columns[0]-20-96>0 # full card + distinct48dp previous/next
   selected_w=columns[1]-20
   # Heading is text-measured at runtime; use90dp upper allowance for scale2.
   selected_h=content_h-16-90-48
   assert selected_h>=96
   for slots in [0,2,4,5,6,9,12,24]:
    ncol=max(1,min(2 if slots<=4 else 3,int(selected_w)//48))
    rows=math.ceil(slots/ncol)
    row_h=max(48,int(selected_h)//max(1,rows))
    rects=[]
    for i in range(slots):
     x=i%ncol*selected_w/ncol;y=i//ncol*row_h
     rect=(x,y,x+selected_w/ncol,y+row_h)
     assert rect[2]-rect[0]>=48 and rect[3]-rect[1]>=48
     assert rect[2]<=selected_w+1e-6
     for a in rects:
      assert not (rect[0]<a[2]-1e-6 and rect[2]>a[0]+1e-6 and rect[1]<a[3] and rect[3]>a[1])
     rects.append(rect)
    if slots<=6: assert rows*row_h<=selected_h
    checks+=1
print('PASS',checks,'geometry cases (text/Android measurement NOT executed)')
