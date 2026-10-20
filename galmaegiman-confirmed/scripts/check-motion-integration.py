#!/usr/bin/env python3
"""배경 경로·원본 수면 좌표·제한적 버튼 연결 및 입력 보호 정적 회귀 검사."""
from pathlib import Path
import json,re
from PIL import Image,ImageFont

project=Path(__file__).resolve().parents[1]
java=project/'app/android/app/src/main/java/org/dpdns/gmm'
assets=project/'app/android/app/src/main/assets'
src=lambda name:(java/(name+'.java')).read_text()
binding=src('BackgroundMotionBinding')
manifest=json.loads((assets/'native/background-motion-v1/runtime.json').read_text())
scene_paths=dict(re.findall(r'case "([^"]+\.png)": return "([^"]+)";',binding))
assert {a['background']['androidAssetPath']:a['sceneId'] for a in manifest['assets']}==scene_paths
masks={scene:[float(v) for v in values.split(',')] for scene,values in re.findall(r'case "(\w+)": box = new float\[\]\{([^}]+)\}',binding)}
assert set(masks)=={'navigation','market','harbor','home'}
for a in manifest['assets']:
 if a['sceneId'] in masks:
  l,t,r,b=masks[a['sceneId']];w,h=a['background']['dimensions']
  assert 0<=l<r<=w and 0<=t<b<=h
  # 미리보기의 넓은 배치 사각형을 가림 마스크로 대체하지 않았다.
  assert (r-l)*(b-t)<w*h*.01
assert 'backgroundEffects.draw' in src('ThemeArt') and 'this::invalidateSelf' in src('ThemeArt')
assert 'backgroundEffects.draw' in src('HomeArt') and 'owner::invalidate' in src('HomeArt')
assert 'backgroundEffects.boat.draw' in src('BattleView') and 'backgroundEffects.draw' in src('BattleView')
assert 'addView' not in binding and 'setOnTouchListener' not in binding
assert 'sceneDragStart(e.getX(), e.getY())' in src('LobbyView')
assert 'child == gull' in src('LobbyView') and 'child.isClickable()' in src('LobbyView')
assert 'parallax.intercept' in src('TitleView') and 'parallax.touch' in src('TitleView')
for depth in ('.2f','.6f','.8f'):
 assert 'parallax.x * '+depth in src('BattleView')
assert 'backgroundEffects.reset()' in src('GameRootLayout')
for hook in ('backgroundEffects.resumed(false)','backgroundEffects.resumed(true)','backgroundEffects.trim()','backgroundEffects.close()','effect.refreshLifecycle()'):
 assert hook in src('MainActivity')
assert '"mix".equals(h.screen) && "조합하기".equals(name) && icon == null' in src('MenuKit')
assert '"account".equals(h.screen) && "u-settings".equals(icon)' in src('MenuKit')
assert 'setOnTouchListener' not in src('UiMotionUiBinding')
# 실제 글꼴의 픽셀 검토. Android FontMetrics 검사를 대체하지 않는다.
font=ImageFont.truetype(str(assets/'native/Jua-Regular.ttf'),16)
button=json.loads((assets/'native/ui-motion-v1/runtime.json').read_text())['buttons'][0]
w,h=button['size'];scale=48/h
assert font.getlength('조합하기')+8 <= w*scale*(1-.16-.16)
assert sum(font.getmetrics())+4 <= 48*(1-.27-.29)
print('연결 정적 검사 통과: 실제 8경로, 수면4곳, lifecycle, 기존 입력/깊이, 버튼 적용범위 및 16px 글꼴')
