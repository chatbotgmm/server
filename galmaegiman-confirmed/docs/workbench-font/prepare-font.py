from pathlib import Path
from fontTools.ttLib import TTCollection, TTFont
from fontTools import subset
from fontTools.pens.recordingPen import RecordingPen
import hashlib,json
out=Path(__file__).parent
source=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
collection=TTCollection(source)
f=collection.fonts[0]
f.recalcTimestamp=False
source_cmap=f.getBestCmap()
# Broad static Korean UI subset: full Hangul and Jamo, Latin/Greek/Cyrillic,
# general punctuation, currency, arrows, math, symbols, CJK punctuation,
# compatibility/fullwidth forms. CJK ideographs use platform fallback.
ranges=[(0x0000,0x052F),(0x1100,0x11FF),(0x1E00,0x1EFF),(0x2000,0x2BFF),(0x3000,0x303F),(0x3130,0x318F),(0x3200,0x33FF),(0xA960,0xA97F),(0xAC00,0xD7FF),(0xFE00,0xFE6F),(0xFF00,0xFFEF)]
keep={c for a,b in ranges for c in range(a,b+1)}
options=subset.Options()
options.name_IDs=['*'];options.name_languages=['*'];options.name_legacy=True
options.layout_features=['*'];options.recalc_timestamp=False
subsetter=subset.Subsetter(options=options)
subsetter.populate(unicodes=keep)
subsetter.subset(f)
# Renamed derivative avoids any reserved-name ambiguity; original provenance retained.
for record in f['name'].names:
 if record.nameID in (1,4,6,16):
  text={1:'GMM Workbench',4:'GMM Workbench Regular',6:'GMMWorkbench-Regular',16:'GMM Workbench'}[record.nameID]
  record.string=text.encode(record.getEncoding())
if 'CFF ' in f:
 cff=f['CFF '].cff
 cff.fontNames=['GMMWorkbench-Regular']
 cff.topDictIndex[0].FamilyName='GMM Workbench'
 cff.topDictIndex[0].FullName='GMM Workbench Regular'
path=out/'workbench-noto-regular.otf'
f.save(path)
g=TTFont(path)
assert g.sfntVersion=='OTTO' and 'CFF ' in g and 'fvar' not in g
assert all(c in g.getBestCmap() for c in range(0xAC00,0xD7A4))
assert set(g.getBestCmap())==set(source_cmap)&keep
# Compare every retained glyph outline and advance width to the original mock JP face.
original=TTFont(source,fontNumber=0)
a=original.getGlyphSet(); b=g.getGlyphSet()
for code,name in g.getBestCmap().items():
 p=RecordingPen();q=RecordingPen();a[source_cmap[code]].draw(p);b[name].draw(q)
 assert p.value==q.value,hex(code)
 assert a[source_cmap[code]].width==b[name].width,hex(code)
# Check actual mock characters against default face 0.
mock=Path('/workspace/shared/combination-preview/redesign.cjs').read_text()
jp=collection.fonts[0];jmap=jp.getBestCmap();jset=jp.getGlyphSet()
differences=[]
for code in set(map(ord,mock)) & set(g.getBestCmap()):
 p=RecordingPen();q=RecordingPen();collection.fonts[1].getGlyphSet()[collection.fonts[1].getBestCmap()[code]].draw(p);jset[jmap[code]].draw(q)
 if p.value!=q.value or a[source_cmap[code]].width!=jset[jmap[code]].width:differences.append(hex(code))
license_source=Path('/usr/share/doc/fonts-noto-cjk/copyright').read_text()
license_block=license_source.rsplit('\nLicense: SIL-1.1\n',1)[1].split('\nLicense: GPL-3+\n',1)[0]
license_text='Copyright 2014-2021 Adobe (http://www.adobe.com/).\nNoto is a trademark of Google Inc.\nCopyright 2010-2012, Google Corporation\n\n'+ '\n'.join('' if line==' .' else line.removeprefix(' ') for line in license_block.splitlines())+'\n'
(out/'OFL.txt').write_text(license_text)
report={'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'source_face':0,'source_family':original['name'].getDebugName(1),'source_version':original['name'].getDebugName(5),'output':path.name,'output_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'cmap_count':len(g.getBestCmap()),'glyph_count':len(g.getGlyphOrder()),'full_hangul_syllables':11172,'outline_and_advance_comparisons':len(g.getBestCmap()),'mock_character_JP_KR_differences':differences,'ranges':[[hex(a),hex(b)] for a,b in ranges]}
(out/'font-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
