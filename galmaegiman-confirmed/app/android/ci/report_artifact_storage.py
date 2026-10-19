#!/usr/bin/env python3
"""Measure retained raster groups without deleting or changing generated evidence."""
import argparse
import json
from pathlib import Path
GROUPS = {
 'recruitment-counter':['recruitment-counter/*.png'],
 'home-motion':['home-drag-center-*.png','home-water-phone-*.png','home-disabled-play-image.png','home-currency-max-font130.png'],
 'button-artwork-states':['theme-state/button-*-normal-pressed-cancelled-selected-disabled.png'],
 **{name:[name+'/*.png'] for name in ('currency-display','team-character-panels','fullscreen','team-insertion-preview','story-cards','feedback-mode-navigation','realtime-identity-revision','battle-ui-revision','battle-replay','result-reassessment')},
}
def report(root,approved=False):
    seen=set();groups={}
    for name,patterns in GROUPS.items():
        files=sorted({p for pattern in patterns for p in root.glob(pattern) if p.is_file()})
        if any(p in seen for p in files):raise ValueError('Duplicate PNG upload group')
        seen.update(files);groups[name]={'files':len(files),'bytes':sum(p.stat().st_size for p in files)}
    all_png=list(root.rglob('*.png'));json_files=list(root.rglob('*.json'))
    build=root.parent
    apks=sorted((build/'outputs/apk').glob('*/*.apk'))
    metadata={p for folder in ('native-review','home-assembly-review','registration-review','theme-review','test-results','reports/tests','registration-ci','theme-ci','phase2-ci','recruitment-revision-ci','feedback-stage1-ci','feedback-final-ci','home-tip-ci','apk-identities') for p in (build/folder).rglob('*') if p.is_file() and p.suffix in ('.json','.xml','.html','.txt')}
    return dict(apk_files=[dict(file=p.name,bytes=p.stat().st_size) for p in apks],
                apk_bytes=sum(p.stat().st_size for p in apks),
                unique_metadata_input_bytes=sum(p.stat().st_size for p in metadata),
                approved_source=approved,retention_days=1,groups=groups,
                generated_native_png_files=len(all_png),generated_native_png_bytes=sum(p.stat().st_size for p in all_png),
                unique_review_png_files=len(seen),unique_review_png_bytes=sum(p.stat().st_size for p in seen),
                uploaded_review_png_bytes=0 if approved else sum(p.stat().st_size for p in seen),
                native_measurement_json_bytes=sum(p.stat().st_size for p in json_files),
                note='Actual uncompressed artifact input bytes. ZIP overhead/compression and later targeted reports/APKs are not included; every native capture remains generated and checked locally.')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--approved',default='false');p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=report(a.root,a.approved=='true');a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
