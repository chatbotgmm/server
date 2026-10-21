"""Read-only manifest adapter. Writes preview metadata only; never modifies PNGs."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
roster=json.loads((ROOT/'source/roster.json').read_text());chars=[];issues=[]
for person in roster:
 id=person['id'];d=ROOT/id;m=json.loads((d/'manifest.json').read_text());out=[]
 for s in m.get('sheets',m.get('assets',[])):
  path=s.get('file') or s.get('path') or f"{s['view']}/{s['action']}.png";view,action=path.split('/')[-2],Path(path).stem
  p=d/path;actual=hashlib.sha256(p.read_bytes()).hexdigest();hash_ok=actual==s['sha256']
  sz=s.get('size') or s.get('sheet_size') or s.get('sheetSize') or s.get('imageSize');cell=s.get('cell') or s.get('cell_size') or s.get('cellSize') or [sz[0]//3,sz[1]//2]
  fs=s.get('frames');fs=fs if isinstance(fs,list) else [{} for _ in range(6)]
  sidepath=p.with_suffix('.measured-anchors.json');side=json.loads(sidepath.read_text()) if sidepath.exists() else {}
  sidehash=side.get('source_sha256') or side.get('sha256');side_ok=not sidehash or sidehash==actual
  anchors=side.get('feet_anchors_local_px') or side.get('per_frame_feet_anchors_px') or side.get('anchors') or s.get('perFrameAnchors')
  scale=s.get('body_normalization',{}).get('uniform_sheet_render_scale') or s.get('proposed_sheet_scale') or s.get('sheet_scale_factor_proposed') or s.get('proposed_sheet_scale_to_neutral260px_height')
  offsets=s.get('authored_vertical_offset_px') or side.get('authored_vertical_offset_px')
  airborne=side.get('airborne_frames_zero_based',[])
  reviewed_airborne={('D3','front','hit-defend'):[3],('D4','front','idle'):[2],('D4','back','idle'):[2],('D4','front','hit-defend'):[3]}
  airborne=airborne or reviewed_airborne.get((id,view,action),[])
  motion_unknown=offsets is None and (action=='entry' or bool(airborne))
  durations=s.get('frame_durations_ms') or s.get('durations_ms') or s.get('durationsMs') or s.get('duration_ms') or m.get('actions',{}).get(action,{}).get('duration_ms')
  frames=[]
  for i,f in enumerate(fs):
   anchor=anchors[i] if anchors else f.get('feet_anchor_px') or f.get('foot_anchor_px') or f.get('measured_anchor_px') or f.get('feet_anchor_px_estimate')
   bounds=f.get('alpha8_bounds') or f.get('visible_bounds_alpha_gt8') or f.get('alpha_bbox_gt128') or (s.get('alphaBoundsThreshold8') or s.get('bounds_alpha127') or [None]*6)[i]
   frames.append({'rect':f.get('source_rect') or f.get('rect') or [i%3*cell[0],i//3*cell[1],*cell],'anchor':anchor,'bounds':bounds,'duration':durations[i] if durations else f.get('duration_ms',150),'offsetY':offsets[i] if offsets is not None else (None if motion_unknown else 0),'airborne':i in airborne})
  warnings=[]
  if not hash_ok:warnings.append('MANIFEST HASH MISMATCH: source changed; review required')
  if not side_ok:warnings.append('ANCHOR HASH MISMATCH: anchors stale; review required')
  if not scale:warnings.append('No proposed uniform sheet scale: raw 1.0 preview')
  if motion_unknown:warnings.append('Entry/airborne vertical trajectory unprovided: anchor-only diagnostic, not faithful motion')
  if any(f['anchor'] is None for f in frames):warnings.append('Missing foot anchors')
  out.append({'view':view,'action':action,'url':f'../{id}/{path}','sha256':actual,'cell':cell,'frames':frames,'scale':scale,'loop':s.get('loop',action=='idle'),'warnings':warnings,'anchorSource':str(sidepath.relative_to(ROOT)) if side else f'{id}/manifest.json'})
  if warnings:issues.append({'id':id,'view':view,'action':action,'warnings':warnings})
 chars.append({'id':id,'name':person['name'],'status':m.get('status'),'sheets':out})
assert len(chars)==33 and sum(len(c['sheets']) for c in chars)==264
catalog={'characters':chars,'issues':issues}
(ROOT/'preview/catalog.js').write_text('window.ANIMATION_CATALOG = '+json.dumps(catalog,ensure_ascii=False)+';\n')
(ROOT/'preview/catalog-audit.json').write_text(json.dumps({'characters':33,'sheets':264,'hashes_verified':264-sum(any('MANIFEST HASH' in w for w in i['warnings']) for i in issues),'issues':issues},ensure_ascii=False,indent=2))
print('Built 33-character / 264-sheet catalog;',sum(any('HASH MISMATCH' in w for w in i['warnings']) for i in issues),'hash mismatches;',len(issues),'sheets have metadata warnings')
