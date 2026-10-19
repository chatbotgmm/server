#!/usr/bin/env python3
"""Additional fail-closed gate for 28 cases within the original 124 native captures."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

STEMS=('prepare-rank','prepare-slot','prepare-page2','prepare-ready','prepare-expired','prepare-ai','prepare-empty')
VARIANTS=('915x412','1006x411','1280x720','915x412-font130')
AI_NOTICE='AI 대전 · 일반 MMR에 반영되지 않아요'


def require(ok,message):
    if not ok: raise ValueError(message)


def finite(value):
    return type(value) in (int,float) and math.isfinite(value)


def read(path): return json.loads(Path(path).read_text(encoding='utf-8'))


def verify(directory,contract):
    require(contract['expected_total_capture_count']==124 and contract['expected_preparation_capture_count']==28
            and contract['state_test_count']==15,'Preparation contract reduced existing coverage')
    require(contract['scenario_stems']==list(STEMS) and contract['variants']==list(VARIANTS),'Preparation scenarios changed')
    require(contract['card_identity']=={'selected':['D1','D2','D3'],'first_candidate_page':['D1','D2','D3'],
            'second_candidate_page':['D8','L14','E7'],'empty':[]},'Authoritative identity contract changed')
    metrics=read(directory/'metrics.json');findings=read(directory/'findings.json')
    require(type(metrics) is dict and type(findings) is dict,'Missing keyed actual evidence')
    expected_names={f'{stem}-{variant}' for stem in STEMS for variant in VARIANTS}
    require({name for name in metrics if name.startswith('prepare-')}==expected_names,'Preparation metric set changed')
    require({name for name in findings if name.startswith('prepare-')}==expected_names,'Preparation finding set changed')
    verified=[];card_count=0
    for stem in STEMS:
        for variant in VARIANTS:
            name=f'{stem}-{variant}'
            require(findings.get(name)==[],f'Missing or failed real capture: {name}')
            metric=metrics[name]
            require(metric.get('actualNativeViews') is True and type(metric.get('apiWrites')) is int
                    and metric['apiWrites']==0,f'Not read-only native proof: {name}')
            status=metric.get('preparationStatus')
            require(type(status) is dict,f'Missing exact status bounds: {name}')
            require(type(status.get('text')) is str and len(status['text'])>0,f'Missing actual status text: {name}')
            for key in ('contentWidth','contentHeight','layoutHeight','textLength','laidOutTextEnd'):
                require(type(status.get(key)) is int and status[key]>0,f'Missing positive status measurement {key}: {name}')
            require(status['laidOutTextEnd']==status['textLength']==len(status['text'].encode('utf-16-le'))//2,
                    f'Status text truncated: {name}')
            lines=status.get('lines')
            require(type(lines) is list and 1<=len(lines)<=2 and status['layoutHeight']<=status['contentHeight'],
                    f'Status height clipped: {name}')
            for line in lines:
                require(type(line) is dict and finite(line.get('left')) and finite(line.get('right')),
                        f'Unmeasured status line: {name}')
                require(-.01<=line['left']<=line['right']<=status['contentWidth']+.01,f'Status width clipped: {name}')
            if stem=='prepare-ai':require(status['text']==AI_NOTICE,f'AI disclosure changed: {name}')
            cards=metric.get('preparationCards')
            require(type(cards) is list,f'Missing full-card evidence: {name}')
            expected={}
            if stem!='prepare-empty':
                expected={f'realtime-prepare-slot-{i}':identifier for i,identifier in enumerate(contract['card_identity']['selected'])}
                offset,ids=(3,contract['card_identity']['second_candidate_page']) if stem=='prepare-page2' else (0,contract['card_identity']['first_candidate_page'])
                expected.update({f'realtime-prepare-candidate-{offset+i}':identifier for i,identifier in enumerate(ids)})
            require(len(cards)==len(expected),f'Wrong full-card count: {name}')
            for card in cards:
                require(type(card) is dict,f'Missing card measurement: {name}')
                tag=card.get('tag');identifier=expected.pop(tag,None)
                require(identifier is not None and card.get('id')==identifier,f'Wrong authoritative identity: {name}/{tag}')
                require(card.get('mode')=='FULL' and card.get('pixelIdentityVerified') is True,f'Not whole real registration: {name}/{tag}')
                width,height=card.get('containedWidth'),card.get('containedHeight')
                minimum=(180,240) if variant=='1280x720' else (128,170)
                require(finite(width) and finite(height),f'Missing contain measurements: {name}/{tag}')
                require(width>=minimum[0] and height>=minimum[1] and abs(width/height-.75)<.001,f'Too small or cropped card: {name}/{tag}')
                require(type(card.get('bitmapWidth')) is int and card['bitmapWidth']>=192
                        and type(card.get('bitmapHeight')) is int and card['bitmapHeight']>=256,f'Missing source bitmap: {name}/{tag}')
                require(card.get('enabled') is (stem not in ('prepare-ready','prepare-expired')),f'Wrong phase lock: {name}/{tag}')
                require(type(card.get('dynamicState')) is str and 'Lv30' in card['dynamicState'],f'Missing live level: {name}/{tag}')
                card_count+=1
            require(not expected,f'Missing authoritative cards: {name}')
            verified.append(name)
    return {'status':'passed','actual_prepare_captures':len(verified),'verified_full_cards':card_count,'fixtures':verified,
            'manual_visual_review':'pending','actual_device_review':'not_run'}


def verify_base_report(directory,contract,base_report,fixtures):
    require(base_report.get('status')=='passed' and base_report.get('module')=='realtime'
            and base_report.get('capture_count')==124 and base_report.get('expected_capture_count')==124,
            'Original 124-capture realtime gate did not pass')
    for name,minimum in ((contract['test_class'],1),(contract['state_test_class'],15)):
        suite=base_report.get('junit',{}).get(name,{})
        require(suite.get('status')=='passed' and type(suite.get('tests')) is int and suite['tests']>=minimum
                and all(type(suite.get(key)) is int and suite[key]==0 for key in ('skipped','failures','errors')),
                'Required original realtime suite did not pass: '+name)
    captures=base_report.get('captures',[])
    require(len(captures)==124 and len({x['file'] for x in captures})==124,'Incomplete original capture report')
    indexed={x['file']:x for x in captures};metrics=read(directory/'metrics.json')
    for name in fixtures:
        prior=indexed.get(name+'.png')
        require(prior is not None,'Preparation PNG missing from original 124 gate: '+name)
        require(prior.get('measurements')==metrics[name],'Preparation measurements changed after original gate: '+name)
        require(hashlib.sha256((directory/(name+'.png')).read_bytes()).hexdigest()==prior.get('sha256'),
                'Preparation PNG changed after original gate: '+name)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    parser.add_argument('--contract',type=Path,required=True)
    parser.add_argument('--realtime-report',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(argv)
    try:
        contract=read(args.contract);report=verify(args.directory,contract)
        verify_base_report(args.directory,contract,read(args.realtime_report),report['fixtures'])
        report['original_realtime_capture_count']=124;result=0
    except (OSError,ValueError,KeyError,TypeError,AttributeError) as error:
        report={'status':'failed','errors':[str(error)],'manual_visual_review':'pending'};result=1
        print('::error::'+str(error),file=sys.stderr)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2));return result


if __name__=='__main__':sys.exit(main())
