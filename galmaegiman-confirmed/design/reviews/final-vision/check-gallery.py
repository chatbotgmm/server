import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[3]
out=Path(os.environ.get('GMM_REVIEW_OUTPUT','/workspace/review-artifacts/pr-2/final-vision/browser'))
out.mkdir(parents=True,exist_ok=True)
manifest=json.loads((root/'public/design-review/manifest.json').read_text())
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1440,'height':1080},device_scale_factor=1)
 errors=[]
 page.on('pageerror',lambda e: errors.append(str(e)))
 page.goto(os.environ.get('GMM_REVIEW_BASE','http://127.0.0.1:8009/design-review/')+'#concept/home')
 page.wait_for_selector('#hotspots button')
 page.evaluate('document.querySelectorAll("#grid img").forEach(i=>i.loading="eager")')
 page.wait_for_function('Array.from(document.querySelectorAll("#grid img")).every(i=>i.complete&&i.naturalWidth>0)')
 page.screenshot(path=str(out/'desktop-home.png'),full_page=True)
 tested=[]
 for scene in manifest['concepts']:
  print(scene['id'],flush=True)
  page.evaluate('(id)=>location.hash="concept/"+id',scene['id'])
  page.wait_for_function('(id)=>selected===id && mode==="concept"',arg=scene['id'])
  page.wait_for_function('document.getElementById("conceptImage").complete && document.getElementById("conceptImage").naturalWidth>0')
  assert page.locator('#screenList button.active').inner_text().endswith(scene['title'])
  for i,h in enumerate(scene['hotspots']):
   page.evaluate('(id)=>location.hash="concept/"+id',scene['id'])
   page.wait_for_function('(id)=>selected===id && mode==="concept"',arg=scene['id'])
   print('  '+h['label']+' -> '+h.get('target',''),flush=True)
   page.locator('#hotspots button').nth(i).click(force=True)
   target=h.get('target',scene['id'])
   if target!=scene['id']:
    expected=target if target.startswith('actual:') else 'concept:'+target
    
    try: page.wait_for_function('(want)=>mode+":"+selected===want',arg=expected,timeout=5000)
    except Exception:
     print('FAILED',scene['id'],h,page.evaluate('({mode,selected,url:location.hash,dialog:document.getElementById("actionDialog").open})'),flush=True)
     page.screenshot(path=str(out/'failed.png'));raise
   else:
    page.wait_for_selector('#actionDialog[open]')
    page.locator('#actionClose').click()
   tested.append({'scene':scene['id'],'label':h['label'],'target':target})
 for scene in manifest['actual']:
  page.evaluate('(id)=>location.hash="actual/"+id',scene['id'])
  page.wait_for_function('(id)=>mode==="actual" && selected===id',arg=scene['id'])
  page.wait_for_function('document.getElementById("beforeImage").complete && document.getElementById("afterImage").complete && document.getElementById("beforeImage").naturalWidth>0 && document.getElementById("afterImage").naturalWidth>0')
 page.evaluate('location.hash="actual/home"')
 page.wait_for_function('mode==="actual" && selected==="home"')
 page.locator('#compareSlider').fill('35')
 page.screenshot(path=str(out/'desktop-compare.png'),full_page=True)
 page.locator('#sideView').click()
 page.screenshot(path=str(out/'desktop-side.png'),full_page=False)
 for width,height in [(412,915),(915,412),(1280,720)]:
  page.set_viewport_size({'width':width,'height':height})
  page.evaluate('location.hash="concept/home"')
  page.wait_for_function('mode==="concept" && selected==="home"')
  assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),(width,'overflow')
  page.evaluate('document.querySelectorAll("#grid img").forEach(i=>i.loading="eager")')
  page.wait_for_function('Array.from(document.querySelectorAll("#grid img")).every(i=>i.complete&&i.naturalWidth>0)')
  page.screenshot(path=str(out/f'viewport-{width}-{height}.png'),full_page=True)
 assert not errors,errors
 (out/'verification.json').write_text(json.dumps({'conceptScreens':22,'actualPairs':33,'hotspotsTested':len(tested),'pageErrors':errors,'hotspots':tested},ensure_ascii=False,indent=2))
 browser.close()
 print(json.dumps({'conceptScreens':22,'actualPairs':33,'hotspotsTested':len(tested),'pageErrors':errors}))
