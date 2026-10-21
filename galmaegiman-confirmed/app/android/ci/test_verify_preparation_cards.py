#!/usr/bin/env python3
"""Synthetic checker fixtures only, not screenshots or native execution evidence."""
import contextlib
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

import verify_preparation_cards as verifier


class PreparationGateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.contract=json.loads((Path(__file__).parent/'preparation-card-contract.json').read_text())
        self.metrics={};self.findings={};captures=[]
        for stem in verifier.STEMS:
            for variant in verifier.VARIANTS:
                name=stem+'-'+variant;text=verifier.AI_NOTICE if stem=='prepare-ai' else '준비 중'
                status={'text':text,'lines':[{'left':0.0,'right':180.0}], 'contentWidth':200,'contentHeight':40,
                        'layoutHeight':24,'textLength':len(text.encode('utf-16-le'))//2,'laidOutTextEnd':len(text.encode('utf-16-le'))//2}
                cards=[]
                if stem!='prepare-empty':
                    ids={f'realtime-prepare-slot-{i}':f'D{i+1}' for i in range(3)}
                    offset,values=(3,('D8','L14','E7')) if stem=='prepare-page2' else (0,('D1','D2','D3'))
                    ids.update({f'realtime-prepare-candidate-{offset+i}':identifier for i,identifier in enumerate(values)})
                    w,h=(180,240) if variant=='1280x720' else (129,172)
                    cards=[{'tag':tag,'id':identifier,'mode':'CHARACTER','pixelIdentityVerified':True,'containedWidth':w,'containedHeight':h,
                            'bitmapWidth':192,'bitmapHeight':256,'enabled':stem not in ('prepare-ready','prepare-expired'),'dynamicState':'Lv30','nameMatchesServer':True,'registrationViews':0,'spritePath':'/duel/sprites/'+identifier+'_s0_front.webp','targetContained':True} for tag,identifier in ids.items()]
                for card in cards:
                    if card['id']=='L14':
                        card.update(spritePath='',imageStatus='이미지 없음',missingIdentityPreserved=True,
                                    pixelIdentityVerified=False,containedWidth=0,containedHeight=0,bitmapWidth=0,bitmapHeight=0)
                self.metrics[name]={'actualNativeViews':True,'apiWrites':0,'preparationStatus':status,'preparationCards':cards}
                self.findings[name]=[]
                # Deliberately not a PNG: CRC/decoder proof belongs to the mandatory original gate.
                data=('synthetic checker payload: '+name).encode();(self.root/(name+'.png')).write_bytes(data)
                captures.append({'file':name+'.png','sha256':hashlib.sha256(data).hexdigest(),'measurements':copy.deepcopy(self.metrics[name])})
        captures += [{'file':f'other-case-{i}.png'} for i in range(96)]
        suite=lambda count:{'status':'passed','tests':count,'skipped':0,'failures':0,'errors':0}
        self.base={'status':'passed','module':'realtime','capture_count':124,'expected_capture_count':124,'captures':captures,
                   'junit':{self.contract['test_class']:suite(1),self.contract['state_test_class']:suite(15)}}
        self.name='prepare-rank-915x412';self.save()
    def tearDown(self):self.temp.cleanup()
    def save(self):
        (self.root/'metrics.json').write_text(json.dumps(self.metrics));(self.root/'findings.json').write_text(json.dumps(self.findings))
    def verify(self):self.save();return verifier.verify(self.root,self.contract)
    def reject(self):
        with self.assertRaises(ValueError):self.verify()
    def card(self):return self.metrics[self.name]['preparationCards'][0]
    def status(self):return self.metrics[self.name]['preparationStatus']
    def verify_base(self):verifier.verify_base_report(self.root,self.contract,self.base,list(self.metrics))
    def test_28_complete_cases_and_144_full_cards_pass(self):
        result=self.verify();self.assertEqual(result['actual_prepare_captures'],28);self.assertEqual(result['verified_full_cards'],144);self.verify_base()
    def test_character_target_must_be_contained(self):self.card()['targetContained']=False;self.reject()
    def test_character_name_must_match_server(self):self.card()['nameMatchesServer']=False;self.reject()
    def test_registration_frame_must_be_absent(self):self.card()['registrationViews']=1;self.reject()
    def test_exact_sprite_path_required(self):self.card()['spritePath']='/duel/sprites/D2_s0_front.webp';self.reject()
    def test_missing_identity_cannot_substitute_pixels(self):
        self.metrics['prepare-page2-915x412']['preparationCards'][4]['bitmapWidth']=192;self.reject()
    def test_missing_identity_fallback_required(self):
        self.metrics['prepare-page2-915x412']['preparationCards'][4]['missingIdentityPreserved']=False;self.reject()
    def test_missing_status_rejects_prepatch_evidence(self):del self.metrics[self.name]['preparationStatus'];self.reject()
    def test_missing_capture_metric_fails(self):del self.metrics[self.name];self.reject()
    def test_extra_prepare_case_fails(self):self.metrics['prepare-invented']=copy.deepcopy(self.metrics[self.name]);self.reject()
    def test_failed_findings_fails(self):self.findings[self.name]=['clip'];self.reject()
    def test_wrong_authoritative_card_fails(self):self.card()['id']='D99';self.reject()
    def test_duplicate_card_tag_fails(self):self.metrics[self.name]['preparationCards'][1]['tag']=self.card()['tag'];self.reject()
    def test_cropped_card_mode_fails(self):self.card()['mode']='THUMB';self.reject()
    def test_unverified_pixels_fail(self):self.card()['pixelIdentityVerified']=False;self.reject()
    def test_too_small_card_fails(self):self.card()['containedHeight']=23;self.reject()
    def test_wrong_source_aspect_fails(self):self.card()['containedWidth']=150;self.reject()
    def test_nan_card_size_fails(self):self.card()['containedWidth']=float('nan');self.reject()
    def test_missing_source_bitmap_fails(self):self.card()['bitmapWidth']=0;self.reject()
    def test_ready_phase_must_lock_cards(self):self.metrics['prepare-ready-915x412']['preparationCards'][0]['enabled']=True;self.reject()
    def test_missing_live_level_fails(self):self.card()['dynamicState']='D1';self.reject()
    def test_empty_case_cannot_invent_cards(self):self.metrics['prepare-empty-915x412']['preparationCards']=[copy.deepcopy(self.card())];self.reject()
    def test_truncated_status_text_fails(self):self.status()['laidOutTextEnd']-=1;self.reject()
    def test_status_height_clipping_fails(self):self.status()['layoutHeight']=41;self.reject()
    def test_status_width_clipping_fails(self):self.status()['lines'][0]['right']=201;self.reject()
    def test_nonfinite_status_line_fails(self):self.status()['lines'][0]['right']=float('nan');self.reject()
    def test_three_status_lines_fail(self):self.status()['lines']*=3;self.reject()
    def test_ai_disclosure_is_exact(self):
        status=self.metrics['prepare-ai-915x412']['preparationStatus'];status['text']='AI';status['textLength']=status['laidOutTextEnd']=2;self.reject()
    def test_boolean_write_counter_fails(self):self.metrics[self.name]['apiWrites']=False;self.reject()
    def test_write_request_fails(self):self.metrics[self.name]['apiWrites']=1;self.reject()
    def test_contract_cannot_reduce_original_capture_count(self):self.contract['expected_total_capture_count']=28;self.reject()
    def test_original_gate_must_pass(self):
        self.base['status']='failed'
        with self.assertRaises(ValueError):self.verify_base()
    def test_original_fifteen_state_tests_cannot_be_skipped(self):
        self.base['junit'][self.contract['state_test_class']]['skipped']=1
        with self.assertRaises(ValueError):self.verify_base()
    def test_changed_png_after_original_gate_fails(self):
        (self.root/(self.name+'.png')).write_bytes(b'changed')
        with self.assertRaises(ValueError):self.verify_base()
    def test_changed_metrics_after_original_gate_fail(self):
        self.metrics[self.name]['preparationStatus']['contentWidth']=201;self.save()
        with self.assertRaises(ValueError):self.verify_base()
    def test_failure_always_emits_report(self):
        self.status()['laidOutTextEnd']=1;self.save()
        contract=self.root/'contract.json';contract.write_text(json.dumps(self.contract));base=self.root/'base.json';base.write_text(json.dumps(self.base));output=self.root/'result.json'
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            code=verifier.main(['--directory',str(self.root),'--contract',str(contract),'--realtime-report',str(base),'--output',str(output)])
        self.assertEqual(code,1);self.assertEqual(json.loads(output.read_text())['status'],'failed')


if __name__=='__main__':unittest.main()
