#!/usr/bin/env python3
"""Synthetic validator checks; these are not native fit or screenshot evidence."""
import contextlib
import copy
import io
import json
from pathlib import Path
import random
import struct
import tempfile
import unittest
import zlib

import verify_home_tip_ci as verifier


def png(width, height):
    def chunk(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    noise=random.Random(17).randbytes(width*height*3)
    raw=b''.join(b'\0'+noise[y*width*3:(y+1)*width*3] for y in range(height))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')


class HomeTipCheckerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.captures=self.root/'captures';self.captures.mkdir()
        self.junit=self.root/'junit';self.junit.mkdir()
        original=json.loads((Path(__file__).parent/'home-tip-capture-contract.json').read_text())
        self.contract={**original,'fixtures':copy.deepcopy(original['fixtures'][:2]),'screenshotCount':2,'tipChecks':16}
        metric={'bubble':[30,5,90,55],'nativePaper':[30,5,90,55],'nativeInk':[35,10,85,50],
                'safe':[0,0,96,64],'exclusions':[[0,0,20,60],[30,58,90,64]],
                'fontPixels':12.0,'lines':2,'verticalPaddingPixels':4,'minimumTouchPixels':48,'clippedInkPixels':0}
        self.summary={'screenshotCount':2,'tipChecks':16,'fixtures':[]}
        for f in self.contract['fixtures']:
            f.update(width=96,height=64)
            (self.captures/f['png']).write_bytes(png(96,64))
            (self.captures/f['metrics']).write_text(json.dumps(metric))
            actual={k:f[k] for k in ('width','height','qualifiers','fontScale','resume')}
            actual.update(tips=[copy.deepcopy(metric) for _ in range(8)],pagedTips=0,scrollableTips=0)
            self.summary['fixtures'].append(actual)
        self.save_summary()
        self.write_junit()

    def tearDown(self): self.temp.cleanup()
    def save_summary(self): (self.captures/self.contract['summary']).write_text(json.dumps(self.summary))
    def write_junit(self,child='',count=3,identity=None):
        cases=''.join('<testcase name="'+name+'">'+child+'</testcase>' for name in self.contract['testMethods'])
        (self.junit/self.contract['junitFile']).write_text('<testsuite name="'+(identity or self.contract['testClass'])+'" tests="'+str(count)+'" skipped="0" failures="0" errors="0">'+cases+'</testsuite>')
    def verify(self): return verifier.verify(self.contract,self.captures,self.junit)
    def fail(self):
        with self.assertRaises((ValueError, OSError)):self.verify()
    def mutate_tip(self,key,value):
        self.summary['fixtures'][0]['tips'][0][key]=value;self.save_summary()

    def test_complete_synthetic_evidence_passes(self):
        result=self.verify();self.assertEqual(result['tipChecks'],16);self.assertEqual(result['manual_visual_review'],'pending')
    def test_native_json_integer_font_scale_is_valid(self):
        self.summary['fixtures'][0]['fontScale']=1;self.save_summary();self.verify()
    def test_safe_above_hero_fallback_passes_without_unconditional_right_side(self):
        actual=self.summary['fixtures'][0]
        for tip in actual['tips']:
            tip.update(bubble=[5,5,65,55],nativePaper=[5,5,65,55],nativeInk=[10,10,60,50],exclusions=[[0,56,96,64]])
        self.save_summary()
        (self.captures/self.contract['fixtures'][0]['metrics']).write_text(json.dumps(actual['tips'][6]))
        self.verify()
    def test_subminimum_touch_height_fails(self):
        self.mutate_tip('minimumTouchPixels',52);self.fail()
    def test_missing_touch_metric_fails(self):
        del self.summary['fixtures'][0]['tips'][0]['minimumTouchPixels'];self.save_summary()
        with self.assertRaises((ValueError,KeyError)):self.verify()
    def test_missing_png_fails(self):
        (self.captures/self.contract['fixtures'][0]['png']).unlink();self.fail()
    def test_extra_png_fails(self):
        (self.captures/'home-advice-extra.png').write_bytes(png(96,64));self.fail()
    def test_corrupt_png_fails(self):
        p=self.captures/self.contract['fixtures'][0]['png'];data=bytearray(p.read_bytes());data[40]^=1;p.write_bytes(data);self.fail()
    def test_extra_json_fails(self):
        (self.captures/'home-advice-extra.json').write_text('{}');self.fail()
    def test_missing_all_tip_summary_fails(self):
        (self.captures/self.contract['summary']).unlink();self.fail()
    def test_junit_child_skip_fails_even_when_suite_counter_zero(self):
        self.write_junit('<skipped/>');self.fail()
    def test_junit_child_failure_fails_even_when_suite_counter_zero(self):
        self.write_junit('<failure/>');self.fail()
    def test_wrong_junit_identity_fails(self):
        self.write_junit(identity='Unrelated');self.fail()
    def test_incomplete_junit_fails(self):
        self.write_junit(count=2);self.fail()
    def test_clipped_tip_fails(self): self.mutate_tip('clippedInkPixels',1);self.fail()
    def test_hero_overlap_fails(self): self.mutate_tip('bubble',[10,5,90,35]);self.fail()
    def test_missing_action_exclusions_fails(self): self.mutate_tip('exclusions',[]);self.fail()
    def test_blank_or_unmeasured_native_ink_fails(self): self.mutate_tip('nativeInk',[0,0,0,0]);self.fail()
    def test_boolean_line_counter_fails(self): self.mutate_tip('lines',True);self.fail()
    def test_nonfinite_rectangle_fails(self): self.mutate_tip('safe',[0,0,float('inf'),64]);self.fail()
    def test_captured_tip_must_match_summary(self):
        p=self.captures/self.contract['fixtures'][0]['metrics'];m=json.loads(p.read_text());m['lines']=3;p.write_text(json.dumps(m));self.fail()
    def test_missing_tip_metrics_fails(self):
        self.summary['fixtures'][0]['tips'].pop();self.save_summary();self.fail()
    def test_normal_fixture_pagination_fails(self):
        self.summary['fixtures'][0]['pagedTips']=1;self.save_summary();self.fail()
    def test_normal_fixture_scrolling_fails(self):
        self.summary['fixtures'][0]['scrollableTips']=1;self.save_summary();self.fail()
    def test_wrong_fixture_identity_fails(self):
        self.summary['fixtures'][0]['resume']=True;self.save_summary();self.fail()
    def test_failure_still_writes_json_report(self):
        contract=self.root/'contract.json';contract.write_text(json.dumps(self.contract));output=self.root/'report.json'
        (self.captures/self.contract['summary']).unlink()
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            result=verifier.main(['--contract',str(contract),'--captures',str(self.captures),'--test-results',str(self.junit),'--output',str(output)])
        self.assertEqual(result,1);self.assertEqual(json.loads(output.read_text())['status'],'failed')
    def test_actual_frozen_contract_has_twenty_screens_and_160_tips(self):
        c=json.loads((Path(__file__).parent/'home-tip-capture-contract.json').read_text())
        self.assertEqual(c['version'],2);self.assertIn('minimumTouchPixels',c['requiredMetrics']);self.assertEqual(c['testCount'],3);self.assertEqual(c['screenshotCount'],20);self.assertEqual(c['tipChecks'],160)
        self.assertEqual(len({f['png'] for f in c['fixtures']}),20)


if __name__=='__main__':unittest.main()
