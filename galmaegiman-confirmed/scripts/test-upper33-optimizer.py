#!/usr/bin/env python3
"""실제 원본이 아닌 합성 264시트로 ImageIO 변환/알파/셀 배치를 검증한다. Pillow 필요."""
import hashlib, json, os, subprocess, sys, tempfile
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[1]
if not (root / 'art/gpt/upper33-runtime-v1/catalog.json').exists():
    root = Path(__file__).resolve().parent
    catalog = root / 'upper33-catalog.json'
    generator = root / 'PrepareUpper33Art.java'
else:
    catalog = root / 'art/gpt/upper33-runtime-v1/catalog.json'
    generator = root / 'scripts/PrepareUpper33Art.java'
colors = [(255,0,0,255),(0,255,0,255),(0,0,255,255),(255,255,0,255),(255,0,255,255),(0,255,255,255)]
with tempfile.TemporaryDirectory(prefix='gmm-upper33-') as tmp:
    tmp = Path(tmp)
    im = Image.new('RGBA', (1536, 1024)); draw = ImageDraw.Draw(im)
    for i, color in enumerate(colors):
        x,y = i%3*512, i//3*512
        draw.rectangle((x+64,y+80,x+447,y+431),fill=color)
        # 셀 접합부에 색이 닿아도 인접 프레임 색이 섞이지 않아야 한다.
        draw.rectangle((x,y+200,x+511,y+311),fill=color)
    im.save(tmp/'source.png'); digest = hashlib.sha256((tmp/'source.png').read_bytes()).hexdigest()
    sheets = [s for c in json.loads(catalog.read_text())['characters'] for s in c['sheets']]
    manifest = tmp/'sources.tsv'
    manifest.write_text(''.join(s['url']+'\t'+digest+'\n' for s in sheets))
    for s in sheets:
        dest = tmp/'in'/s['url']; dest.parent.mkdir(parents=True,exist_ok=True)
        os.link(tmp/'source.png',dest)
    command = [os.environ.get('JAVA','java'),'-Djava.awt.headless=true',str(generator),str(tmp/'in'),str(manifest),str(catalog),str(tmp/'out')]
    subprocess.run(command,check=True)
    count=0
    for s in sheets:
        with Image.open(tmp/'out'/s['url']) as actual:
            assert actual.size == (768,512)
            assert actual.getpixel((0,0))[3] == 0
            for i,color in enumerate(colors):
                assert actual.getpixel((i%3*256+128,i//3*256+128)) == color
                assert actual.getpixel((i%3*256,i//3*256+128)) == color
                assert actual.getpixel((i%3*256+255,i//3*256+128)) == color
            count+=1
    assert count == 264
    assert (tmp/'out/catalog.json').read_bytes()==catalog.read_bytes()
    manifest.write_text(manifest.read_text().replace(digest,'0'*64,1))
    rejected = subprocess.run(command,capture_output=True,text=True)
    assert rejected.returncode != 0 and 'Hash mismatch' in rejected.stderr
    print('PASS: 264 synthetic sheets, 1584 colored cells, alpha, catalog copy, corrupt-hash rejection')
