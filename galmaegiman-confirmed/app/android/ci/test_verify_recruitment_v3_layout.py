"""Verifier fixtures only; passing this suite is NOT native render evidence."""
import copy, hashlib, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
import verify_recruitment_v3_layout as gate

class V3GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        (self.root/'fixture.png').write_bytes(b'unit fixture, not screenshot')
        controls=[]
        for i,tag in enumerate(sorted(gate.REQUIRED)):
            b=[i*52,0,i*52+48,48]
            controls.append(dict(id=tag,boundsDp=b,visibleBoundsDp=b,description=tag,enabled=True))
        labels=[]
        for i,tag in enumerate(('draw-total','draw-unit-cost','draw-claim-status','draw-pity-summary')):
            b=[i*150,60,i*150+140,80]
            labels.append(dict(id=tag,boundsDp=b,visibleBoundsDp=b,inkBoundsDp=b,text='live',ellipsisCount=0))
        self.c=dict(widthDp=640,heightDp=320,fontScale=1.0,density=1,widthPx=640,heightPx=320,state='low',actualNativeViews=True,apiWrites=0,verticalScroll=False,findings=[],png='fixture.png',sha256=hashlib.sha256((self.root/'fixture.png').read_bytes()).hexdigest(),safeBoundsDp=[0,0,640,320],selectedKind='LOW',selectors=[dict(kind=k,selected=k=='LOW',nonColorSelectionCue=True) for k in ('LOW','MID','HIGH')],hero=dict(count=1,kind='LOW',asset='recruitment/tray-low-v1.png',importantForAccessibility=False,boundsDp=[0,100,200,200],artBoundsDp=[0,100,200,200],sourceBounds=[0,0,200,100]),controls=controls,labels=labels)
        expected=gate.REFERENCE['LOW']; self.c['hero'].update(asset=expected['path'].removeprefix('native/release-v1/'), assetSha256=expected['sha256'], bitmapDimensions=expected['dimensions'], sourceBounds=expected['sourceRect'], artBoundsDp=[0,100,100*(1580/853),200])
        self.c['layoutRevision']=3
        self.c['compactMode']=False
        self.c['composition']={'upperInfoBoundsDp':[392,80,636,180],'purchaseBoundsDp':[392,190,636,306]}
        positions={'info':[480,100,628,148],'minus':[400,190,448,238],'plus':[504,190,552,238],'count':[452,190,500,238],'maximum':[560,190,608,238],'draw-submit':[400,258,628,306]}
        for control in self.c['controls']:
            if control['id'] in positions:control['boundsDp']=control['visibleBoundsDp']=positions[control['id']]
        positions={'draw-total':[400,240,628,256],'draw-unit-cost':[422,80,606,98],'draw-claim-status':[0,60,140,80],'draw-pity-summary':[480,100,628,148]}
        for label in self.c['labels']:
            label['boundsDp']=label['visibleBoundsDp']=label['inkBoundsDp']=positions[label['id']]
            if label['id']=='draw-pity-summary':label['ownerControl']='info'
    def validate(self,c):
        with patch.object(gate,'verify_png'):return gate.verify_capture(self.root,c)
    def test_well_formed_unit_fixture(self):self.assertEqual((640,320,1.0,'low'),self.validate(self.c))
    def test_old_schema_and_empty_matrix_rejected(self):
        for data in ({'schema':'recruitment-counter-v1'},{'schema':'recruitment-v3-native-layout/1','sourceRevision':'a'*40,'captures':[]}):
            (self.root/'evidence.json').write_text(json.dumps(data))
            with self.assertRaises(ValueError):gate.verify(self.root)
    def test_control_fail_closed(self):
        for key,value in [('boundsDp',[0,0,47,48]),('visibleBoundsDp',[0,0,1,1]),('description',''),('enabled',None)]:
            c=copy.deepcopy(self.c);c['controls'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.validate(c)
        c=copy.deepcopy(self.c);c['controls'].pop()
        with self.assertRaises(ValueError):self.validate(c)
        c=copy.deepcopy(self.c);c['controls'][1]['boundsDp']=c['controls'][0]['boundsDp'];c['controls'][1]['visibleBoundsDp']=c['controls'][0]['boundsDp']
        with self.assertRaises(ValueError):self.validate(c)
    def test_hero_fail_closed(self):
        for key,value in [('assetSha256','bad'),('bitmapDimensions',[768,512]),('sourceBounds',[0,0,1580,853]),('count',3),('kind','MID'),('asset','other.png'),('importantForAccessibility',True),('artBoundsDp',[0,100,100,200])]:
            c=copy.deepcopy(self.c);c['hero'][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.validate(c)
    def test_capture_and_text_fail_closed(self):
        for key,value in [('verticalScroll',True),('findings',['clipped']),('apiWrites',1),('sha256','bad'),('actualNativeViews',False)]:
            c=copy.deepcopy(self.c);c[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.validate(c)
        for key,value in [('ellipsisCount',1),('inkBoundsDp',[0,60,150,80]),('text','')]:
            c=copy.deepcopy(self.c);c['labels'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.validate(c)
    def test_state_fail_closed(self):
        for state in ('cooldown','insufficient'):
            c=copy.deepcopy(self.c);c['state']=state
            with self.assertRaises(ValueError):self.validate(c)
        c=copy.deepcopy(self.c);c['selectors'][1]['selected']=True
        with self.assertRaises(ValueError):self.validate(c)
    def test_exact_eleven_profile_state_records_required(self):
        expected=sorted({gate.BASE_PROFILE+(state,) for state in gate.STATES}|{p+('low',) for p in gate.PROFILES})
        data={'schema':'recruitment-v3-native-layout/1','sourceRevision':'a'*40,'captures':[list(k) for k in expected]}
        def write(): (self.root/'evidence.json').write_text(json.dumps(data))
        with patch.object(gate,'verify_capture',side_effect=lambda root,c:tuple(c)):
            write();self.assertEqual(11,gate.verify(self.root)['captures'])
            data['captures'].pop();write()
            with self.assertRaises(ValueError):gate.verify(self.root)
            data['captures'].append(data['captures'][0]);write()
            with self.assertRaises(ValueError):gate.verify(self.root)
    def test_native_failure_sidecar_cannot_be_ignored(self):
        data={'schema':'recruitment-v3-native-layout/1','sourceRevision':'a'*40,'captures':[],'failures':['native text clipped']}
        (self.root/'evidence.json').write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError,'producer reported failures'):gate.verify(self.root)
    def aggregate_fixture(self):
        d=self.root/'recruitment-counter';d.mkdir()
        (d/'fixture.png').touch()
        for name in ('claim-requesting-915x412.png','claim-refreshed-915x412.png'):(d/name).touch()
        (d/'evidence.json').write_text(json.dumps({'captures':[{'png':'fixture.png','widthPx':640,'heightPx':320}]}))
        claim=dict(requestingClaimEnabled=False,requestingBusy=True,requestsAfterDuplicateTap=1,refreshingClaimEnabled=False,refreshingBusy=True,refreshedClaimEnabled=False,refreshedBusy=False,refreshedCredits=230,refreshedCooldownMillis=3600000,calls=['GET draw','POST claim','GET native/state','GET draw'])
        (d/'claim-state.json').write_text(json.dumps(claim));return d,claim
    def test_active_aggregate_uses_v3_and_keeps_claim_contract(self):
        import verify_recruitment_revision_ci as aggregate
        d,claim=self.aggregate_fixture()
        with patch.object(gate,'verify') as native,patch.object(aggregate,'verify_png',return_value={}):
            self.assertEqual(3,len(aggregate.verify_counter_v3(self.root)));native.assert_called_once_with(d)
            for key,value in [('requestsAfterDuplicateTap',2),('refreshingClaimEnabled',True),('refreshedCredits',999)]:
                altered=dict(claim);altered[key]=value;(d/'claim-state.json').write_text(json.dumps(altered))
                with self.subTest(key=key),self.assertRaises(ValueError):aggregate.verify_counter_v3(self.root)
    def test_active_aggregate_cannot_accept_extra_or_missing_capture(self):
        import verify_recruitment_revision_ci as aggregate
        d,claim=self.aggregate_fixture()
        with patch.object(gate,'verify'),patch.object(aggregate,'verify_png',return_value={}):
            (d/'old-three-trays.png').touch()
            with self.assertRaises(ValueError):aggregate.verify_counter_v3(self.root)
            (d/'old-three-trays.png').unlink();(d/'claim-refreshed-915x412.png').unlink()
            with self.assertRaises(ValueError):aggregate.verify_counter_v3(self.root)
    def test_corrected_composition_is_required_and_fail_closed(self):
        for field,value in [('layoutRevision',1),('layoutRevision',2),('composition',{'upperInfoBoundsDp':[392,80,636,220],'purchaseBoundsDp':[392,190,636,306]})]:
            c=copy.deepcopy(self.c);c[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):self.validate(c)
        c=copy.deepcopy(self.c);c['composition']['purchaseBoundsDp']=[392,250,636,306]
        with self.assertRaises(ValueError):self.validate(c)
    def test_normal_profile_cannot_hide_fidelity_via_compact_fallback(self):
        c=copy.deepcopy(self.c);c.update(widthDp=800,heightDp=360,widthPx=800,heightPx=360,compactMode=True)
        with self.assertRaisesRegex(ValueError,'cannot pass by falling back'):self.validate(c)
    def test_r3_cost_pill_and_quantity_widths_cannot_regress(self):
        c=copy.deepcopy(self.c)
        pill=next(label for label in c['labels'] if label['id']=='draw-unit-cost')
        pill['boundsDp']=pill['visibleBoundsDp']=pill['inkBoundsDp']=[392,80,636,98]
        with self.assertRaisesRegex(ValueError,'cost pill must be 184dp'):self.validate(c)
        c=copy.deepcopy(self.c)
        count=next(control for control in c['controls'] if control['id']=='count')
        count['boundsDp']=count['visibleBoundsDp']=[452,190,501,238]
        with self.assertRaisesRegex(ValueError,'fixed 48dp'):self.validate(c)
    def test_real_png_decoder_required(self):
        with self.assertRaises(Exception):gate.verify_capture(self.root,self.c)

if __name__=='__main__':unittest.main()
