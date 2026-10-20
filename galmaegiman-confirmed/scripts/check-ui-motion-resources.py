#!/usr/bin/env python3
"""UI PNG/매핑 정적 검증. 실행: python scripts/check-ui-motion-resources.py (Pillow 필요)."""
import hashlib
import json
from pathlib import Path
import re
import sys
from PIL import Image

DEFAULT = Path(__file__).resolve().parents[1] / 'app/android/app/src/main/assets/native/ui-motion-v1'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def validate(root):
    root = Path(root).resolve()
    manifest = json.loads((root / 'runtime.json').read_text())
    require(manifest['schemaVersion'] == 1, '지원하지 않는 스키마')
    rows = manifest['buttons'] + manifest['icons']
    require(len(manifest['buttons']) == 18 and len(manifest['icons']) == 6, '버튼 18 / 아이콘 6 필요')
    paths, ids, families, report = set(), set(), {}, []
    for row in rows:
        path, key = row['file'], row['id']
        require(re.fullmatch(r'(buttons|icons)/[a-z_]+\.png', path), f'허용하지 않는 경로: {path}')
        require(key not in ids and path not in paths, f'중복 ID/경로: {key}')
        ids.add(key)
        paths.add(path)
        file = root / path
        require(file.resolve().is_relative_to(root), f'루트 밖 경로: {path}')
        require(file.is_file(), f'누락 PNG: {path}')
        with Image.open(file) as im:
            require(im.format == 'PNG' and im.mode == 'RGBA', f'RGBA PNG 아님: {path}')
            im.load()
            require(list(im.size) == row['size'], f'크기 불일치: {path}')
            alpha = im.getchannel('A')
            lo, hi = alpha.getextrema()
            require(lo == 0 and hi >= 128, f'투명 배경/가시 픽셀 누락: {path}')
            histogram = alpha.histogram()
            require(sum(histogram[1:255]) > 0, f'부드러운 알파 경계 누락: {path}')
            if path.startswith('icons/'):
                box = alpha.point(lambda value: 255 if value >= 128 else 0).getbbox()
                require(box is not None, f'빈 아이콘: {path}')
                x, y, r, b = box
                require([x,y,r-x,b-y] == row['visibleBoundsAlpha128'], f'아이콘 가시 경계 불일치: {path}')
                require(row['usage'] == 'runtime_icon', f'아이콘 용도 오류: {path}')
            else:
                family, state = row['family'], row['state']
                require(state in ('normal','pressed','disabled'), f'상태 오류: {path}')
                require(key == f'{family}_{state}' and path == f'buttons/{key}.png', f'상태 매핑 오류: {path}')
                require(row['usage'] == ('runtime_base' if state == 'normal' else 'visual_reference_only'), f'런타임 상태 교체 금지: {path}')
                family_rows = families.setdefault(family, [])
                family_rows.append(row)
                insets = row['contentInsets']
                require(set(insets) == {'left','top','right','bottom'} and all(0 <= v < 1 for v in insets.values()), f'여백 오류: {path}')
                require(insets['left']+insets['right'] < 1 and insets['top']+insets['bottom'] < 1, f'빈 내용 영역: {path}')
            report.append({'file':path,'size':list(im.size),'transparentPixels':histogram[0],
                           'alphaRange':[lo,hi], 'partialAlphaPixels':sum(histogram[1:255]),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    require(len(families) == 6, '버튼 종류 6개 필요')
    for family, entries in families.items():
        require({r['state'] for r in entries} == {'normal','pressed','disabled'}, f'상태 누락: {family}')
        require(len({tuple(r['size']) for r in entries}) == 1, f'상태 캔버스 불일치: {family}')
    require(paths == {str(p.relative_to(root)) for directory in ('buttons','icons') for p in (root/directory).glob('*.png')}, '매핑 밖 PNG')
    # 검토용 힌트 시트는 production 하위 어디에도 넣지 않는다.
    require(not any('hint' in p.name.lower() or p.suffix.lower() == '.gif' for p in root.rglob('*') if p.is_file()), 'production에 힌트 시트/GIF 포함')
    roles = json.loads((root/'button-roles.json').read_text())
    require(set(roles['families']) == set(families), '종류별 역할 매핑 불일치')
    require(all(r['base'] in paths and r['base'].endswith('_normal.png') for r in roles['families'].values()), '역할이 기본 PNG를 참조하지 않음')
    return report


if __name__ == '__main__':
    try:
        report = validate(sys.argv[1] if len(sys.argv) > 1 else DEFAULT)
        print(json.dumps({'result':'pass','pngCount':len(report),'files':report}, ensure_ascii=False, indent=2))
    except (ValueError, KeyError, OSError) as error:
        print(f'실패: {error}', file=sys.stderr)
        sys.exit(1)
