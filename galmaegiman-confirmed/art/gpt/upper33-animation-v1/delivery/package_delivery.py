#!/usr/bin/env python3
"""Read-only art inventory; --build packages only 264 hash-bound accepted sheets.
No image changes. ZIP_STORED, deterministic timestamps, sorted allow-list.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'delivery'; LIMIT=20_000_000; PAYLOAD_LIMIT=18_000_000
ACTIONS=('idle','attack','hit-defend','entry');VIEWS=('front','back')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def allowed_meta(p):
 if p.suffix not in ('.json','.txt','.md'):return False
 if any(x in p.name.lower() for x in ('rejected','archive','draft','needsqa','first-','base64')):return False
 return bool(re.search(r'manifest|prompt|anchor|metadata|review|qa|integration|completion|scale',p.name,re.I))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--build',action='store_true');args=ap.parse_args();OUT.mkdir(exist_ok=True)
 roster=json.loads((ROOT/'manifest.json').read_text());reviews=json.loads((ROOT/'independent-reviews.json').read_text());ids=[c['id'] for c in roster['characters']];assert len(ids)==33 and len(set(ids))==33
 paths=set();selected=[];missing=[];pending=[];manifest_errors=[]
 for id in ids:
  b=ROOT/id
  mp=b/'manifest.json'
  if not mp.exists():manifest_errors.append(id+': missing manifest')
  else:
   md=json.loads(mp.read_text());entries=md.get('sheets',md.get('assets',[]));seen=set()
   for entry in entries:
    rel=entry.get('path',entry.get('file')) or (entry.get('view','')+'/'+entry.get('action','')+'.png')
    if rel.startswith(id+'/'):rel=rel[len(id)+1:]
    f=b/rel
    if not f.is_file() or entry.get('sha256')!=sha(f):manifest_errors.append(id+'/'+rel)
    seen.add(rel)
   if len(seen)!=8:manifest_errors.append(id+': manifest does not contain eight unique entries')
  for v in VIEWS:
   for a in ACTIONS:
    rel=f'{id}/{v}/{a}.png';p=ROOT/rel
    if not p.is_file():missing.append(rel);continue
    h=sha(p);r=reviews.get(rel,{});accepted=r.get('status')=='accepted_after_review' and r.get('sha256')==h
    selected.append({'path':rel,'bytes':p.stat().st_size,'sha256':h,'accepted':accepted});paths.add(p)
    if not accepted:pending.append(rel)
    for meta in p.parent.glob(a+'*'):
     if allowed_meta(meta):paths.add(meta)
    # Original per-action records may be action.json.
    if p.with_suffix('.json').exists():paths.add(p.with_suffix('.json'))
  for p in b.iterdir() if b.exists() else []:
   if p.is_file() and allowed_meta(p):paths.add(p)
 for name in ['manifest.json','QA-OVERVIEW.json','independent-reviews.json','D2-D8-completion.json','T1-T6-completion.json','I6-I9-integration.md']:
  p=ROOT/name
  if p.exists():paths.add(p)
 preview=ROOT/'preview'
 if preview.exists():
  for p in preview.rglob('*'):
   if p.is_file() and p.name in ('index.html','app.js','catalog.js','README.md','build_catalog.py','catalog-audit.json','metadata-gaps.json','test-results.json','test_controls.cjs','test_preview.cjs') and not any(x in p.parts for x in ('node_modules','.git')):paths.add(p)
 paths.add(Path(__file__).resolve())
 records=[]
 for p in sorted(paths):
  if p.suffix!='.png' and re.search(r'data:image/[^;]+;base64,[A-Za-z0-9+/]{100}',p.read_text(errors='replace')):raise RuntimeError('Embedded image/base64 rejected: '+str(p))
  records.append({'path':p.relative_to(ROOT).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p),'kind':'selected_png' if p.suffix=='.png' else 'metadata_or_preview'})
 parts=[];current=[];size=0
 for r in records:
  if r['bytes']>PAYLOAD_LIMIT:raise RuntimeError('Oversized indivisible file: '+r['path'])
  if current and size+r['bytes']>PAYLOAD_LIMIT:parts.append(current);current=[];size=0
  current.append(r['path']);size+=r['bytes']
 if current:parts.append(current)
 index={'format_version':1,'expected_characters':33,'expected_selected_pngs':264,'present_selected_pngs':len(selected),'accepted_selected_pngs':sum(s['accepted'] for s in selected),'missing':missing,'pending_or_stale_review':pending,'manifest_hash_errors':manifest_errors,'payload_files':len(records),'payload_bytes':sum(r['bytes'] for r in records),'selected_png_bytes':sum(r['bytes'] for r in selected),'zip_limit_bytes':LIMIT,'proposed_part_count':len(parts),'preview_present':(preview/'index.html').exists(),'acceptance_scope':'Static asset identity, action, alpha and cell-isolation QA; renderer integration/playback pending.','records':records,'parts':[{'name':f'upper33-part-{i:02d}-of-{len(parts):02d}.zip','files':part,'payload_bytes':sum(r['bytes'] for r in records if r['path'] in part)} for i,part in enumerate(parts,1)]}
 write(OUT/'master-index.json',index)
 readme=f'''상위 33종 애니메이션 전달 묶음\n\n선정 시트 {len(selected)}/264개, 현재 파일 해시와 일치하는 독립 QA 승인 {index['accepted_selected_pngs']}/264개.\n캐릭터별 앞/뒤 × 대기/공격/피격·방어/등장 = 8개 PNG, 시트당 3열×2행 6프레임.\nPNG 원본 바이트를 그대로 보관했습니다. 재압축·리사이즈·픽셀 수정 없음.\n\n사용법\n1. 모든 ZIP 분할 파일을 같은 빈 폴더에 압축 해제하세요. 각 ZIP은 독립 ZIP이며 바이트 분할 파일이 아닙니다.\n2. 중복 README.ko.txt/master-index.json은 같은 내용입니다.\n3. preview/index.html이 포함되어 있으면 브라우저로 여세요.\n4. master-index.json의 파일별 SHA-256과 크기로 무결성을 확인하세요.\n\n검수와 미완료 범위\n정적 에셋 승인과 실제 게임 연동 완료는 다릅니다. 렌더러 통합, 재생 속도/루프, 액션 전환, 전체 캐릭터 크기 일관성은 실제 런타임에서 검증해야 합니다.\n캐릭터 manifest의 프레임 앵커와 제안된 시트별 스케일을 적용하고 검증하세요. T3 뒷모습 기준점은 망토에 가려 추정값이며, T1/T3 등장 점프 오프셋도 제안값입니다.\nT1 앞모습 대기 시트는 이전 작업의 정확한 최초 생성 프롬프트가 남아 있지 않습니다. 이 누락을 숨기거나 새 프롬프트로 대체하지 않았습니다. 참조 해시·선정 PNG·검수 기록은 유지됩니다.\n일부 개별 메모의 과거 상태와 충돌하면 현재 PNG 해시가 일치하는 independent-reviews.json을 우선 확인하세요.\n\n용량 주의: 원본 전체는 약600MB입니다. 모든 시트를 APK에 일괄 포함하거나 GPU 메모리에 동시에 로드하지 마세요. 선택한 캐릭터·액션 단위로 필요할 때 로드하는 방식을 권장합니다. 실제 배포용 포맷·해상도 최적화는 별도 승인 후 수행할 후속 작업이며, 이번 원본은 그대로 유지했습니다.\n\nGitHub 업로드·게시·앱 코드 수정·배포는 하지 않았습니다.\n'''
 (OUT/'README.ko.txt').write_text(readme)
 report=f'''상위 33종 애니메이션 완료 현황\n\n선정 PNG: {len(selected)}/264개\n현재 파일 해시 기준 독립 검수 승인: {index['accepted_selected_pngs']}/264개\n선정 PNG 용량: {index['selected_png_bytes']:,}바이트\n전달 대상 총 용량: {index['payload_bytes']:,}바이트, 파일 {len(records)}개\n예상 ZIP: {len(parts)}개, 각 20,000,000바이트 이하\n\n정적 에셋 검수 승인 여부는 위 숫자로 확인됩니다. 렌더러 통합과 실제 재생 검증은 아직 완료되지 않았습니다. T1 앞모습 대기 최초 프롬프트 누락은 README에 명시했습니다.\n'''
 (OUT/'completion-report.ko.txt').write_text(report)
 print(json.dumps({k:index[k] for k in ['present_selected_pngs','accepted_selected_pngs','payload_bytes','proposed_part_count','preview_present','manifest_hash_errors']},ensure_ascii=False))
 if not args.build:return
 if missing or pending or manifest_errors or len(selected)!=264:raise SystemExit('Packaging blocked: requires264 hash-bound accepted selected PNGs.')
 if not index['preview_present']:raise SystemExit('Packaging blocked: local preview/index.html missing.')
 common={'README.ko.txt':readme.encode(),'master-index.json':(OUT/'master-index.json').read_bytes(),'completion-report.ko.txt':report.encode()}
 zipped=[]
 for part in index['parts']:
  dest=OUT/part['name']
  with zipfile.ZipFile(dest,'w',compression=zipfile.ZIP_STORED) as z:
   for name,data in sorted(common.items()):
    info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.external_attr=0o644<<16;z.writestr(info,data)
   for name in part['files']:
    data=(ROOT/name).read_bytes();expected=next(r['sha256'] for r in records if r['path']==name)
    if hashlib.sha256(data).hexdigest()!=expected:raise RuntimeError('File changed during packaging: '+name)
    info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.external_attr=0o644<<16;z.writestr(info,data)
  assert dest.stat().st_size<=LIMIT,(dest,dest.stat().st_size)
  with zipfile.ZipFile(dest) as z:assert z.testzip() is None
  zipped.append({'path':dest.name,'bytes':dest.stat().st_size,'sha256':sha(dest)})
 write(OUT/'zip-checksums.json',zipped);print('Built and verified '+str(len(zipped))+' ZIP files.')
 meta=OUT/'upper33-metadata-report.zip'
 with zipfile.ZipFile(meta,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(common.items()):
   info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.external_attr=0o644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
  for r in records:
   if r['kind']!='selected_png':
    info=zipfile.ZipInfo(r['path'],(2026,1,1,0,0,0));info.external_attr=0o644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,(ROOT/r['path']).read_bytes())
 assert meta.stat().st_size<=LIMIT
 with zipfile.ZipFile(meta) as z:assert z.testzip() is None
 write(OUT/'metadata-bundle-checksum.json',{'path':meta.name,'bytes':meta.stat().st_size,'sha256':sha(meta),'note':'No sprite PNGs inside. Metadata and local preview require full PNG collection for display.'})
if __name__=='__main__':main()
