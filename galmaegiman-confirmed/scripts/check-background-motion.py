#!/usr/bin/env python3
"""실제 효과 시트·원본 해시·좌표·핑퐁 계약 정적 검증 (Pillow 필요)."""
import hashlib
import json
import math
from pathlib import Path
import re
import sys
from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]
REPO = PROJECT.parent
ASSETS = PROJECT/'app/android/app/src/main/assets'
DEFAULT = ASSETS/'native/background-motion-v1'
SEQUENCE = list(range(8)) + list(range(6,0,-1))


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(file, algorithm='sha256'):
    data = file.read_bytes()
    if algorithm == 'git':
        return hashlib.sha1(f'blob {len(data)}\0'.encode()+data).hexdigest()
    return hashlib.sha256(data).hexdigest()


def validate(root=DEFAULT):
    root = Path(root).resolve()
    manifest = json.loads((root/'runtime.json').read_text())
    require(manifest['schemaVersion'] == 1 and manifest['originalsModified'] is False, '스키마/원본 보존 선언')
    require(manifest['playback']['sequence'] == SEQUENCE, '전역 핑퐁 순서')
    require(manifest['playback']['grid'] == [4,2] and manifest['playback']['frameOrder'] == 'row-major', '전역 그리드')
    require(len(manifest['assets']) == 8, '장면 8개 필요')
    paths, scenes, report = set(), set(), []
    for row in manifest['assets']:
        file, scene = row['file'], row['sceneId']
        require(re.fullmatch(r'(indoor|outdoor)/[a-z0-9-]+\.png', file), '시트 경로 오류')
        require(file not in paths and scene not in scenes, '중복 경로/장면')
        paths.add(file); scenes.add(scene)
        sheet = root/file
        require(sheet.resolve().is_relative_to(root) and sheet.is_file(), '시트 누락/경로 이탈')
        require(digest(sheet) == row['sha256'], '시트 SHA-256 불일치')
        bg = row['background']
        require(re.fullmatch(r'(native/release-v1/backgrounds|battle-v1)/[a-z0-9-]+\.png', bg['androidAssetPath']), '원본 asset 경로')
        original = ASSETS/bg['androidAssetPath']
        require(original.resolve() == (REPO/bg['repoPath']).resolve(), '원본 두 경로 불일치')
        require(bg['preserveOriginal'] and digest(original) == bg['sha256'] and digest(original,'git') == bg['gitBlobSha1'], '원본 해시 변경')
        with Image.open(original) as image:
            require(list(image.size) == bg['dimensions'], '원본 크기')
        require(row['sheetSize'] == [2048,1024] and row['frameSize'] == [512,512], '매핑 시트 크기')
        require(row['playbackSequence'] == SEQUENCE, '핑퐁 끝점/순서')
        ms = row['frameDurationMs']
        require(isinstance(ms,int) and 0 < ms <= 60000 and row['loopDurationMs'] == len(SEQUENCE)*ms, '루프 길이')
        require(isinstance(row['crossfadeMs'],int) and 0 <= row['crossfadeMs'] <= ms, 'crossfade 범위')
        require(row['blend'] == 'source-over' and 0 <= row['opacity'] <= 1, '블렌드/불투명도')
        rects, anchors, boxes = row['frameRects'], row['frameAnchorsNormalized'], row['boundsNormalizedByFrame']
        require(len(rects)==len(anchors)==len(boxes)==8, '프레임 개수')
        ax,ay = row['sceneAnchorNormalized'];rw,rh = row['renderFrameSizeNormalized']
        require(all(math.isfinite(n) for n in (ax,ay,rw,rh)), '유한 앵커')
        require(row['boundsNormalized'] == boxes[0], '기준/첫 프레임 좌표 불일치')
        with Image.open(sheet) as image:
            require(image.mode == 'RGBA' and image.format == 'PNG' and list(image.size) == [2048,1024], 'RGBA PNG/크기')
            for i,(rect,pivot,box) in enumerate(zip(rects,anchors,boxes)):
                require(rect == {'x':i%4*512,'y':i//4*512,'width':512,'height':512}, '프레임 사각형')
                x,y = rect['x'],rect['y']
                alpha = image.getchannel('A').crop((x,y,x+512,y+512))
                edges = [(0,0,512,1),(0,511,512,512),(0,0,1,512),(511,0,512,512)]
                require(all(alpha.crop(edge).getextrema()[1] == 0 for edge in edges), '프레임 테두리 알파')
                require(alpha.getextrema()[1] > 0, '빈 프레임')
                l,t,w,h = (box[k] for k in ('x','y','width','height'))
                require(all(math.isfinite(n) for n in (*pivot,l,t,w,h)), '유한 배치 좌표')
                require(all(0 <= n <= 1 for n in pivot) and l>=0 and t>=0 and w>0 and h>0 and l+w<=1.00001 and t+h<=1.00001, '정규화 좌표 범위')
                require(abs(w-rw)<1e-5 and abs(h-rh)<1e-5 and abs(l-(ax-pivot[0]*rw))<1e-5 and abs(t-(ay-pivot[1]*rh))<1e-5, '앵커/배치 불일치')
        report.append({'scene':scene,'frames':8,'loopMs':row['loopDurationMs'],'crossfadeMs':row['crossfadeMs'],
                       'waterMaskRequiredByRenderer':row.get('requiresWaterOcclusionMask',False) or 'water-' in row['id'],
                       'sheetSha256':row['sha256'],'originalUnchanged':True})
    require(paths == {str(p.relative_to(root)) for d in ('indoor','outdoor') for p in (root/d).glob('*.png')}, '매핑 밖 시트')
    return report


if __name__ == '__main__':
    try:
        print(json.dumps({'result':'pass','scenes':validate(sys.argv[1] if len(sys.argv)>1 else DEFAULT)},ensure_ascii=False,indent=2))
    except (ValueError,KeyError,OSError) as error:
        print(f'실패: {error}',file=sys.stderr)
        sys.exit(1)

