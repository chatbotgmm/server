from pathlib import Path
base=Path(__file__).parent
import json, hashlib
source=base.parent/'app/android/app/src/main/java/org/dpdns/gmm/NativeEconomy.java'
if not source.exists(): source=base/'NativeEconomy.java'
after=source.read_text()
expected=json.loads((base/'navy-behavior-baseline.json').read_text())['methods']
def method(s,name):
  import re
  m=re.search(r'^  (?:void|String|JSONObject) '+name+r'\([^\n]*\) \{',s,re.M)
  assert m,name
  start=m.start(); i=m.end(); depth=1
  while depth:
    c=s[i]; depth+= (c=='{')-(c=='}'); i+=1
  return s[start:i]
for name in ['mixUnit','mixResult','confirmMix','performCraft','acquisitionOrigin','materialGuide']:
  assert hashlib.sha256(method(after,name).encode()).hexdigest()==expected[name],name+' changed'
  print('UNCHANGED',name)
assert 'cell.setTag("mix-material-"' in after
assert 'cell.setOnClickListener' in after
assert 'slot.setClickable(false)' in after
assert 'MeasureSpec.makeMeasureSpec(contentHeight,MeasureSpec.EXACTLY)' in after
print('PASS six transaction/lookup methods byte-identical + view ownership/height source checks')
