"""Static provenance/region/resource checks; NOT rendering or device validation."""
from pathlib import Path
import hashlib,json,xml.etree.ElementTree as ET
from PIL import Image
root=Path(__file__).resolve().parents[2]
assets=root/'app/android/app/src/debug/assets/combat-v2'
m=json.loads((assets/'manifest.json').read_text())
assert len(m['entries'])==60
assert sum(len(e['active_views']) for e in m['entries'])==120
for e in m['entries']:
 for view,v in e['active_views'].items():
  assert view in ('front','rear')
  x,y,r,b=v['source_rect_ltrb_exclusive'];assert 0<=x<r and 0<=y<b
  assert not v['file'].startswith('/') and '..' not in v['file']
  source=next(f for f in e['files'] if f.get('path',f.get('file'))==v['file'])
  assert r<=source['size'][0] and b<=source['size'][1]
  if e['id']=='u01':
   p=assets/v['file'];assert hashlib.sha256(p.read_bytes()).hexdigest()==v['sha256']
   with Image.open(p) as image:assert image.size==tuple(source['size']) and image.mode=='RGBA'
u31=next(e for e in m['entries'] if e['id']=='u31')
assert 'v6' in u31['active_views']['front']['file']
assert 'rear-v4' in next(e for e in m['entries'] if e['id']=='u17')['active_views']['rear']['file']
res=root/'app/android/app/src/main/res'
files=list(res.glob('drawable/combat_v2_*.xml'))+list(res.glob('values/combat_v2_*.xml'))
for f in files:ET.parse(f)
assert len(list(res.glob('drawable/combat_v2_*.xml')))==26
print('PASS: 60 IDs / 120 bounded active rects; latest u31 v6/u17 override; u01 hash+RGBA; 26 drawable XML + strings. Not a render test.')
