"""Local Git only. Preserve originals; derive bounded lossless WebP runtime crops."""
from pathlib import Path
import json,subprocess,hashlib,io
from PIL import Image
COMMIT='b4b19815e047b4a8189a084d44f78ae22530d7ed'
PREFIX='galmaegiman-confirmed/art/gpt/android-battle-v2/roster60/'
out=Path('app/android/app/src/main/assets/roster60-art');out.mkdir(parents=True,exist_ok=True)
def blob(name):return subprocess.check_output(['git','show',COMMIT+':'+PREFIX+name])
m=json.loads(blob('manifest.json'));index={'sourceCommit':COMMIT,'purpose':'test-profile art; source approvals unchanged','pivotStatus':'alpha-bounds bottom center candidate; device validation pending','entries':[]};raw={}
for e in m['entries']:
 row={'id':e['id'],'reviewStatus':e.get('review_status'),'views':{}}
 for facing,v in e['active_views'].items():
  data=raw.setdefault(v['file'],blob(v['file']))
  assert hashlib.sha256(data).hexdigest()==v['sha256']
  im=Image.open(io.BytesIO(data));im.load();r=v['source_rect_ltrb_exclusive'];assert 0<=r[0]<r[2]<=im.width and 0<=r[1]<r[3]<=im.height
  crop=im.crop(r);bounds=crop.getchannel('A').point(lambda a:255 if a>8 else 0).getbbox();assert bounds
  # No midpoint splitting, redraw, tint or mirroring. Only authoritative region and runtime sampling.
  crop=crop.crop(bounds);originalSize=list(crop.size);crop.thumbnail((512,512),Image.Resampling.LANCZOS)
  file=e['id']+'-'+facing+'.webp';crop.save(out/file,'WEBP',lossless=True)
  row['views'][facing]={'file':file,'sourceFile':v['file'],'sourceSha256':v['sha256'],'sourceRect':r,'alphaBounds':list(bounds),'originalVisibleSize':originalSize,'size':list(crop.size),'pivot':[.5,1],'relativeHeight':.6 if e['id']=='u02' else 1,'runtimeSha256':hashlib.sha256((out/file).read_bytes()).hexdigest()}
 index['entries'].append(row)
assert len(index['entries'])==60
(out/'index.json').write_text(json.dumps(index,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'entries':60,'views':120,'sourceFiles':len(raw),'runtimeBytes':sum(p.stat().st_size for p in out.iterdir())}))
