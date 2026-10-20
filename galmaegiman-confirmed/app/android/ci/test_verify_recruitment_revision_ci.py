import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
from verify_theme_ci import allowed_asset_changes
from verify_recruitment_revision_ci import (TRAYS, TRAY_INK, SUITES, PROFILES, STATES, tray_lock,
    verify_trays, verify_counter, verify_motion, verify_currency, verify_team, TEAM_PROFILES, fullscreen_profiles, verify_fullscreen, verify_insertion, story_card_profiles, verify_story_cards, MODE_PROFILES, verify_mode_navigation, verify_realtime_identity, verify_battle_utilities, verify_replay, verify_reassessment, reassessment_profiles, verify_dossier_diagnostics, verify_totals, junit)

class RevisionContractTest(unittest.TestCase):
    def test_full_suite_minima_reject_previous_count_or_added_skips(self):
        good=dict(status='passed',totals=dict(tests=501,passed=485,skipped=16))
        verify_totals(good)
        for field,value in [('tests',500),('passed',484),('skipped',17)]:
            data=json.loads(json.dumps(good));data['totals'][field]=value
            with self.subTest(field=field), self.assertRaises(ValueError):verify_totals(data)
        good['status']='failed'
        with self.assertRaises(ValueError):verify_totals(good)
    def test_sdk26_native_bounds_method_is_required_and_unskipped(self):
        method='itemMenuAutosizeHasNativeTextBoundsOnSdk26'
        self.assertIn(method,SUITES['BattleUtilitiesChartRevisionTest'])
        self.junit_fixture()
        p=self.root/'TEST-org.dpdns.gmm.BattleUtilitiesChartRevisionTest.xml';tree=ET.parse(p)
        case=next(c for c in tree.getroot().findall('testcase') if c.get('name')==method)
        ET.SubElement(case,'skipped');tree.write(p)
        with self.assertRaises(ValueError):junit(self.root)
        tree.getroot().remove(case);tree.write(p)
        with self.assertRaises(ValueError):junit(self.root)
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
    def write(self,path,data):
        p=self.root/path; p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(json.dumps(data))
    def counter_fixture(self):
        d=self.root/'recruitment-counter'; d.mkdir()
        geometry={}; findings={}
        for state in STATES:
            for profile in PROFILES:
                stem=state+'-'+profile; (d/(stem+'.png')).touch()
                geometry[stem]=dict(actualNativeViews=True,apiWrites=0,controls=3,labels=4,independentOverlapChecks=8,
                    trays=[dict(kind=k.upper(),path=f'recruitment/tray-{k}-v1.png',artWidth=TRAY_INK[k.upper()][2]-TRAY_INK[k.upper()][0],artHeight=TRAY_INK[k.upper()][3]-TRAY_INK[k.upper()][1],sourceBounds=TRAY_INK[k.upper()],visibleBaseline=300) for k in ('low','mid','high')],
                    selectedKind=state.upper() if state in ('low','mid','high') else 'LOW',totalText='합계 100',
                    claimEnabled=state!='cooldown',submitEnabled=state!='insufficient')
                findings[stem]=[]
        for name in ('claim-requesting-915x412.png','claim-refreshed-915x412.png'): (d/name).touch()
        self.write('recruitment-counter/geometry.json',geometry)
        self.write('recruitment-counter/findings.json',findings)
        self.write('recruitment-counter/claim-state.json',dict(requestingClaimEnabled=False,requestingBusy=True,requestsAfterDuplicateTap=1,refreshingClaimEnabled=False,refreshingBusy=True,refreshedClaimEnabled=False,refreshedBusy=False,refreshedCredits=230,refreshedCooldownMillis=3600000,calls=['GET draw','POST claim','GET native/state','GET draw']))
        return geometry,findings
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_all_37_required_native_captures(self,mock):
        self.counter_fixture(); self.assertEqual(len(verify_counter(self.root)),37)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_missing_or_extra_capture_rejected(self,mock):
        self.counter_fixture(); p=self.root/'recruitment-counter/low-915x412.png';p.unlink()
        with self.assertRaises(ValueError): verify_counter(self.root)
        p.touch(); (p.parent/'unreviewed.png').touch()
        with self.assertRaises(ValueError): verify_counter(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_geometry_state_and_art_contracts_fail_closed(self,mock):
        original,_=self.counter_fixture()
        for key,value in [('actualNativeViews',False),('apiWrites',1),('controls',0),('labels',0),('independentOverlapChecks',0),('trays',[]),('claimEnabled',True)]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data['cooldown-915x412'][key]=value
                self.write('recruitment-counter/geometry.json',data)
                with self.assertRaises(ValueError): verify_counter(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_native_findings_cannot_be_ignored(self,mock):
        _,findings=self.counter_fixture();findings['low-915x412']=['clipped']
        self.write('recruitment-counter/findings.json',findings)
        with self.assertRaises(ValueError): verify_counter(self.root)
    def motion_fixture(self):
        m=dict(schema='home-water-motion-v1',width=915,height=412,firstClockMillis=150,secondClockMillis=1100,
             minimumRgbDelta=12,minimumChangedPixels=100,visibleWaterChangedPixels=101,
             firstFrame='home-water-phone-t0150.png',secondFrame='home-water-phone-t1100.png')
        self.write('home-water-motion.json',m);return m
    @patch('verify_recruitment_revision_ci.verify_png',side_effect=lambda p,*a,**k:dict(sha256=p.name))
    def test_motion_uses_timed_distinct_frames(self,mock):
        self.motion_fixture();self.assertEqual(len(verify_motion(self.root)),8)
    @patch('verify_recruitment_revision_ci.verify_png',side_effect=lambda p,*a,**k:dict(sha256=p.name))
    def test_static_clock_or_unmeasured_water_rejected(self,mock):
        original=self.motion_fixture()
        for key,value in [('secondClockMillis',150),('visibleWaterChangedPixels',100),('minimumRgbDelta',1),('firstFrame','fake.png')]:
            with self.subTest(key=key):
                data=dict(original);data[key]=value;self.write('home-water-motion.json',data)
                with self.assertRaises(ValueError):verify_motion(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value=dict(sha256='same'))
    def test_duplicate_motion_images_rejected(self,mock):
        self.motion_fixture()
        with self.assertRaises(ValueError): verify_motion(self.root)
    def currency_fixture(self):
        d=self.root/'currency-display';d.mkdir()
        geometry={};findings={}
        for name in PROFILES:
            (d/(name+'.png')).touch()
            geometry[name]=dict(passed=True,displays=[dict(kind=kind,text='2,147,483,647',width=160,height=48,textWidth=120,availableWidth=148,left=i*160,top=0,right=(i+1)*160,bottom=48) for i,kind in enumerate(('snack','peanut','credits'))])
            findings[name]=[]
        self.write('currency-display/geometry.json',geometry);self.write('currency-display/findings.json',findings)
        return geometry
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_currency_has_five_complete_profiles(self,mock):
        self.currency_fixture();self.assertEqual(len(verify_currency(self.root)),5)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_currency_ink_touch_bounds_and_identity_cannot_regress(self,mock):
        original=self.currency_fixture()
        for key,value in [('width',47),('height',47),('textWidth',149),('left',-1),('kind','menu'),('text','')]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data['915x412']['displays'][0][key]=value
                self.write('currency-display/geometry.json',data)
                with self.assertRaises(ValueError):verify_currency(self.root)
    def team_fixture(self):
        directory=self.root/'team-character-panels';directory.mkdir()
        metrics={};findings={};captures=[]
        for name,(width,height,dpi,font,insets) in TEAM_PROFILES.items():
            left,top=insets[:2]
            for scene in ('party-story','party-pvp'):
                for page in ('first','last'):
                    file=name+'-'+scene+'-'+page+'.png';(directory/file).touch()
                    captures.append(dict(filename=file,profile=name,scene=scene,page=page))
                    profile=dict(widthPx=width,heightPx=height,densityDpi=dpi,fontScale=font,insetsPx=dict(zip(('left','top','right','bottom'),insets)))
                    arts=[]
                    for i in range(6):
                        view=[left+i*40,top+180,left+i*40+24,top+204]
                        arts.append(dict(id='D'+str(i+1),status='',expectedSource='game-art/duel/sprites/D'+str(i+1)+'_s0_front.webp',bitmapWidth=24,bitmapHeight=24,viewPx=view,visiblePx=view,targetPx=[0,0,24,24]))
                    size=48*dpi/160
                    metrics[file]=dict(profile=profile,scene=scene,apiWrites=0,registrationViews=0,solidPortraitCount=6,portraitColor='ff87999b',modalVisible=False,artworks=arts,
                        toolbarPx=[left,top+20,left+250,top+68],candidateAreaPx=[left,top+68,left+300,top+230],
                        targetLabel='1번 교체',pageLabel=('1 / 2' if page=='first' else '2 / 2'),
                        controls=[dict(boundsPx=[left,top,left+size,top+size])],liveLabels=[dict(boundsPx=[left,top,left+60,top+15])])
                    findings[file]=[]
        self.write('team-character-panels/manifest.json',dict(schemaVersion=1,suite='team-character-panels',baseline='680cc3565aafddcd262541faed44b6a333dde912',requiredCaptureCount=16,captures=captures))
        self.write('team-character-panels/metrics.json',metrics);self.write('team-character-panels/findings.json',findings)
        self.write('team-character-panels/inline-reorder.json',{scene:dict(modalVisible=False,draftOnly=True,busyGuard=True,discardRestores=True) for scene in ('party-story','party-pvp')})
        return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_team_has_six_whole_sprites_across_all_16_cases(self,mock):
        self.team_fixture();self.assertEqual(len(verify_team(self.root)),16)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_team_modal_color_page_and_geometry_cannot_regress(self,mock):
        original=self.team_fixture();first=next(iter(original))
        for key,value in [('solidPortraitCount',5),('portraitColor','0087999b'),('modalVisible',True),('apiWrites',1),('pageLabel','2 / 2'),('artworks',[]),('toolbarPx',[0,0,1,1])]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data[first][key]=value
                self.write('team-character-panels/metrics.json',data)
                with self.assertRaises(ValueError):verify_team(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_team_reorder_must_be_draft_only_and_busy_guarded(self,mock):
        self.team_fixture();p=self.root/'team-character-panels/inline-reorder.json';data=json.loads(p.read_text())
        data['party-pvp']['busyGuard']=False;p.write_text(json.dumps(data))
        with self.assertRaises(ValueError):verify_team(self.root)
    def fullscreen_fixture(self):
        directory=self.root/'fullscreen';directory.mkdir();metrics=[]
        for name,(width,height,side) in fullscreen_profiles().items():
            (directory/(name+'.png')).touch();left,right=(36,10) if side=='left' else (10,36)
            metrics.append(dict(name=name,background=f'[0,0][{width},{height}]',content=f'[{left},12][{width-right},{height-24}]',allEdgePixelsMatchBackground=True))
        self.write('fullscreen/metrics.json',metrics);return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_fullscreen_keeps_all_24_scene_rotation_profiles(self,mock):
        self.fullscreen_fixture();self.assertEqual(len(verify_fullscreen(self.root)),24)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_fullscreen_requires_edge_pixels_and_separate_safe_content(self,mock):
        original=self.fullscreen_fixture()
        for key,value in [('background','[36,12][905,388]'),('content','[0,0][915,412]'),('allEdgePixelsMatchBackground',False)]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data[0][key]=value;self.write('fullscreen/metrics.json',data)
                with self.assertRaises(ValueError):verify_fullscreen(self.root)
    def insertion_fixture(self):
        directory=self.root/'team-insertion-preview';directory.mkdir()
        for name in ('party-before','party-preview','party-cancel','party-drop','story-list-before','story-list-preview','story-list-cancel'):(directory/(name+'.png')).touch()
        for scene in ('party','story-list'):
            m=dict(schemaVersion=1,widthPx=915,heightPx=412,previewOffsetPx=80,repeatedMoves=12,cancelRestored=True,apiWrites=0,findings=[])
            if scene=='party':m.update(draftUnchangedBeforeDrop=True,outsideDropRestored=True,dropOrder=['o1','o3','o2'])
            else:m.update(stateUnchangedBeforeDrop=True)
            self.write('team-insertion-preview/'+scene+'.json',m)
        self.write('team-insertion-preview/story-order.json',dict(schemaVersion=1,modalVisible=False,saveCount=1,busyBlocksDuplicate=True,savedKeys=['o1','o3','o2'],findings=[]))
    @patch('verify_recruitment_revision_ci.verify_png',side_effect=lambda p,*a,**k:dict(sha256=p.name))
    def test_insertion_requires_seven_real_frames_and_cancel_guards(self,mock):
        self.insertion_fixture();self.assertEqual(len(verify_insertion(self.root)),7)
    @patch('verify_recruitment_revision_ci.verify_png',side_effect=lambda p,*a,**k:dict(sha256=p.name))
    def test_insertion_cannot_mutate_before_drop_or_skip_cancel_restore(self,mock):
        self.insertion_fixture();p=self.root/'team-insertion-preview/party.json';original=json.loads(p.read_text())
        for key,value in [('previewOffsetPx',0),('repeatedMoves',1),('cancelRestored',False),('apiWrites',1),('draftUnchangedBeforeDrop',False),('outsideDropRestored',False),('dropOrder',['o1','o1','o2'])]:
            with self.subTest(key=key):
                data=dict(original);data[key]=value;p.write_text(json.dumps(data))
                with self.assertRaises(ValueError):verify_insertion(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value=dict(sha256='identical'))
    def test_duplicate_insertion_frames_cannot_pass(self,mock):
        self.insertion_fixture()
        with self.assertRaises(ValueError):verify_insertion(self.root)
    def story_fixture(self):
        directory=self.root/'story-cards';directory.mkdir();metrics={};findings={}
        for name,(width,height,font) in story_card_profiles().items():
            (directory/(name+'.png')).touch()
            metrics[name]=dict(actualNativeViews=True,cards=3,width=width,height=height,fontScale=font,safeInsetsPx=[28,8,0,12],measuredWidth=width,measuredHeight=height,safeWidth=width-28,safeHeight=height-20,fixtureWrites=0,dialog=False,actions=3,boundsChecks=6,textLabels=8,inlineChoices=0)
            findings[name]=[]
        self.write('story-cards/metrics.json',metrics);self.write('story-cards/findings.json',findings)
        return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_story_cards_have_short_large_font_and_cutout_evidence(self,mock):
        self.story_fixture();self.assertEqual(len(verify_story_cards(self.root)),4)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_story_cards_cannot_omit_geometry_or_safe_insets(self,mock):
        original=self.story_fixture();first=next(iter(original))
        for key,value in [('cards',2),('safeInsetsPx',[0,0,0,0]),('safeWidth',915),('fixtureWrites',1),('dialog',True),('boundsChecks',0),('actions',0)]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data[first][key]=value;self.write('story-cards/metrics.json',data)
                with self.assertRaises(ValueError):verify_story_cards(self.root)
    def mode_navigation_fixture(self):
        from test_verify_feedback_stage1_ci import mode_manifest
        directory=self.root/'feedback-mode-navigation';directory.mkdir();metrics={};findings={};captures=[]
        for name,(w,h,dpi,font,insets) in MODE_PROFILES.items():
            safe=[insets[0],insets[1],w-insets[2],h-insets[3]]
            for route in ('modes','modesStory'):
                file=name+'-'+route+'.png';(directory/file).touch();captures.append(dict(filename=file,profile=name,route=route,widthPx=w,heightPx=h))
                cards={};texts={};labels={'story':'스토리','pvp':'대전'} if route=='modes' else {'story':'스토리모드','adventure':'모험\n준비 중'}
                cardwidth=(safe[2]-safe[0])/2-10
                for i,(key,label) in enumerate(labels.items()):
                    x=safe[0]+i*(cardwidth+10);y=safe[1]+10;box=[x,y,x+cardwidth,y+80*dpi/160]
                    reading=[box[0]+4,box[1]+4,box[2]-4,box[3]-4];glyph=[box[0]+8,box[1]+8,box[2]-8,box[3]-8]
                    cards[key]=dict(text=label,enabled=key!='adventure',motif={'story':0 if route=='modes' else 3,'pvp':1,'adventure':2}[key],boundsPx=box,readingBoundsPx=reading,glyphBoundsPx=glyph)
                    texts[key]=dict(boundsPx=box,visibleBoundsPx=box,glyphBoundsPx=glyph,ellipsisCount=0,autoSizeTextType=0,textScaleX=1,textSizePx=26)
                metrics[file]=dict(profile=name,route=route,actualNativeViews=True,screenWidthPx=w,screenHeightPx=h,densityDpi=dpi,fontScale=font,safeBoundsPx=safe,scrollContainers=0,apiWrites=0,fixtureReads=[],touchChecks=2,textChecks=2,cards=cards,texts=texts,findings=[])
                findings[file]=[]
        self.write('feedback-mode-navigation/manifest.json',dict(schemaVersion=1,suite='feedback-mode-navigation',baseline='680cc3565aafddcd262541faed44b6a333dde912',routes=['modes','modesStory'],captures=captures,profiles=mode_manifest()['profiles']))
        self.write('feedback-mode-navigation/metrics.json',metrics);self.write('feedback-mode-navigation/findings.json',findings)
        return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_category_routes_preserve_all_16_profiles_and_unavailable_adventure(self,mock):
        self.mode_navigation_fixture();self.assertEqual(len(verify_mode_navigation(self.root)),16)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_category_cannot_enable_adventure_or_clip_ink(self,mock):
        original=self.mode_navigation_fixture();first=next(k for k in original if '-modesStory.' in k)
        for key,value in [('enabled',True),('motif',1),('text','모험')]:
            with self.subTest(key=key):
                data=json.loads(json.dumps(original));data[first]['cards']['adventure'][key]=value;self.write('feedback-mode-navigation/metrics.json',data)
                with self.assertRaises(ValueError):verify_mode_navigation(self.root)
        data=json.loads(json.dumps(original));data[first]['texts']['story']['ellipsisCount']=1;self.write('feedback-mode-navigation/metrics.json',data)
        with self.assertRaises(ValueError):verify_mode_navigation(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_category_rejects_stale_baseline_and_root_motif_on_story_route(self,mock):
        original=self.mode_navigation_fixture()
        path=self.root/'feedback-mode-navigation/manifest.json';manifest=json.loads(path.read_text())
        stale=dict(manifest,baseline='8b55b4bde86c4ea8e1a9f4efb643d905564ffe89');path.write_text(json.dumps(stale))
        with self.assertRaises(ValueError):verify_mode_navigation(self.root)
        path.write_text(json.dumps(manifest))
        first=next(k for k in original if '-modesStory.' in k)
        original[first]['cards']['story']['motif']=0;self.write('feedback-mode-navigation/metrics.json',original)
        with self.assertRaises(ValueError):verify_mode_navigation(self.root)
    def junit_fixture(self):
        for suite,methods in dict(SUITES,JournalThemeTest=['existing'],NativeEffectsPresentationTest=['existing'],NativeHarborAudioTest=['existing'],NativeRecruitmentProbabilityTest=['existing']).items():
            name='org.dpdns.gmm.'+suite; root=ET.Element('testsuite',name=name)
            for method in methods: ET.SubElement(root,'testcase',name=method,classname=name)
            ET.ElementTree(root).write(self.root/('TEST-'+name+'.xml'))
    def realtime_fixture(self):
        metrics={}
        for profile,(w,h) in PROFILES.items():
            if profile=='780x360-font130': continue
            for stem in ('prepare-owned','prepare-rank-owned','accept-compact','augment-compact','augment-selected-compact'):
                identities=[]
                if stem.startswith('prepare'):
                    for n,species in enumerate(('D1','D2','D3','D1','D1','D2')):
                        identities.append(dict(tag='realtime-prepare-'+('slot-' if n<3 else 'candidate-')+str(n%3),species=species,path='/duel/sprites/'+species+'_s0_front.webp',pixelIdentityVerified=True,status=''))
                metrics[stem+'-'+profile]=dict(actualNativeViews=True,backendCompatiblePayload=True,apiWrites=0,actions=2,width=w,height=h,fontScale=1.3 if 'font130' in profile else 1.,identities=identities)
        self.write('realtime-identity-revision/metrics.json',metrics);return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_realtime_requires_twenty_original_identity_captures(self,mock):
        self.realtime_fixture();self.assertEqual(len(verify_realtime_identity(self.root)),20)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_realtime_cannot_swap_owned_copy_species_or_skip_pixels(self,mock):
        for key,value in [('species','D8'),('pixelIdentityVerified',False),('tag','wrong'),('status','hidden')]:
            data=self.realtime_fixture();data['prepare-owned-915x412']['identities'][0][key]=value;self.write('realtime-identity-revision/metrics.json',data)
            with self.assertRaises(ValueError):verify_realtime_identity(self.root)
    def battle_utilities_fixture(self):
        data=[dict(capture=f'battle-{pane}-{w}-inset-font130.png',width=w,height=h,fontScale=1.3,utilityLeft=200,panelLeft=190,status='화상',buttons=[dict(text='약 × 2',width=80,height=48,enabled=True)]) for w,h in ((640,360),(915,412)) for pane in ('menu','items')]
        self.write('battle-ui-revision/observations.json',data);return data
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_battle_utilities_require_six_short_large_font_frames(self,mock):
        self.battle_utilities_fixture();self.assertEqual(len(verify_battle_utilities(self.root)),6)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_battle_utilities_cannot_escape_panel_or_restore_effect_numbers(self,mock):
        for key,value in [('utilityLeft',180),('status','화상 6%'),('fontScale',1.0)]:
            data=self.battle_utilities_fixture();data[0][key]=value;self.write('battle-ui-revision/observations.json',data)
            with self.assertRaises(ValueError):verify_battle_utilities(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_battle_utilities_reject_subminimum_touch(self,mock):
        data=self.battle_utilities_fixture();data[0]['buttons'][0]['height']=47;self.write('battle-ui-revision/observations.json',data)
        with self.assertRaises(ValueError):verify_battle_utilities(self.root)
    def replay_fixture(self):
        metrics={}; captures=[]
        for name,profile in TEAM_PROFILES.items():
            for frame in ('start','end'):
                filename=name+'-'+frame+'.png';w,h=profile[:2];end=frame=='end'
                captures.append(dict(filename=filename,widthPx=w,heightPx=h))
                metrics[filename]=dict(widthPx=w,heightPx=h,position=2 if end else 0,frameCount=3,apiCalls=0,stateUnchanged=True,readOnlyScene=True,previousEnabled=end,nextEnabled=not end)
        self.write('battle-replay/manifest.json',dict(schemaVersion=1,suite='battle-replay',requiredCaptureCount=8,captures=captures))
        self.write('battle-replay/metrics.json',metrics);self.write('battle-replay/findings.json',{k:[] for k in metrics});return metrics
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_replay_requires_eight_read_only_boundary_frames(self,mock):
        self.replay_fixture();self.assertEqual(len(verify_replay(self.root)),8)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_replay_cannot_write_or_claim_wrong_position(self,mock):
        for key,value in [('apiCalls',1),('stateUnchanged',False),('readOnlyScene',False),('position',2),('previousEnabled',True)]:
            data=self.replay_fixture();data['phone-915x412-start.png'][key]=value;self.write('battle-replay/metrics.json',data)
            with self.assertRaises(ValueError):verify_replay(self.root)
    def test_matrix_asset_and_backend_test_reject_changed_bytes(self):
        from verify_theme_ci import verify_matrix_asset
        from verify_registration_ci import verify_matrix_test, MATRIX_TEST_PATH
        with self.assertRaises(ValueError):verify_matrix_asset(b'{}')
        path=self.root/MATRIX_TEST_PATH;path.parent.mkdir(parents=True);path.write_text('unapproved')
        with self.assertRaises(ValueError):verify_matrix_test(self.root)
    def test_receipt_backend_test_rejects_mutation_and_symlink(self):
        from verify_registration_ci import verify_receipt_test, RECEIPT_TEST_PATH
        path=self.root/RECEIPT_TEST_PATH;path.parent.mkdir(parents=True);path.write_text('unapproved')
        with self.assertRaises(ValueError):verify_receipt_test(self.root)
        target=self.root/'redirect';path.rename(target);path.symlink_to(target)
        with self.assertRaises(ValueError):verify_receipt_test(self.root)
    def test_matrix_asset_allowance_does_not_include_neighbors(self):
        from verify_theme_ci import MATRIX_ASSET_PATH
        lock={'source_prefix':'native','files':[]}
        allowed_asset_changes([MATRIX_ASSET_PATH],lock)
        with self.assertRaises(ValueError):allowed_asset_changes([MATRIX_ASSET_PATH+'.bak'],lock)
    def reassessment_fixture(self):
        data={}
        for name,(w,h,dpi,font,state) in reassessment_profiles().items():
            swiped=state=='swiped';density=dpi/160
            targets=[] if swiped else [dict(tag='acquisition-reassess-card-0',bounds=[10,10,10+48*density,10+48*density],widthDp=48,heightDp=48,enabled=True,description='재평가')]
            data[name]=dict(viewport=[w,h],densityDpi=dpi,fontScale=font,resultPage=1 if swiped else 0,selectedIndex=3 if swiped else 0,visibleSpecies=['S4','D1','I3'] if swiped else ['R13','R14','S3'],visibleCardCount=3,translationX=-110*density if state=='mid-drag' else 0,mutationCount=0,dossierOpen=False,refreshTargets=targets)
        self.write('result-reassessment/observations.json',data);return data
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_reassessment_requires_24_real_density_drag_frames(self,mock):
        self.reassessment_fixture();self.assertEqual(len(verify_reassessment(self.root)),24)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_reassessment_rejects_fake_drag_or_wrong_acquisition(self,mock):
        for key,value in [('translationX',0),('mutationCount',1),('dossierOpen',True),('selectedIndex',3),('visibleSpecies',['D1','D2','D3'])]:
            data=self.reassessment_fixture();data['915x412-dpi160-font100-mid-drag'][key]=value;self.write('result-reassessment/observations.json',data)
            with self.assertRaises(ValueError):verify_reassessment(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_reassessment_rejects_small_target(self,mock):
        data=self.reassessment_fixture();data['915x412-dpi160-font100-eligible']['refreshTargets'][0]['widthDp']=47;self.write('result-reassessment/observations.json',data)
        with self.assertRaises(ValueError):verify_reassessment(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_two_dossier_diagnostics_are_separate_from_24_reassessment_cases(self,mock):
        self.assertEqual(len(verify_dossier_diagnostics(self.root)),2)
        self.assertEqual({call.args[0].name for call in mock.call_args_list},{'780x360-dpi160-font100-results.png','780x360-dpi160-font130-results.png'})
    def test_exact_required_new_methods_pass(self):
        self.junit_fixture(); junit(self.root)
    def test_skipped_new_method_rejected(self):
        self.junit_fixture(); p=self.root/'TEST-org.dpdns.gmm.ReleaseHomeMotionTest.xml';tree=ET.parse(p)
        ET.SubElement(tree.getroot().find('testcase'),'skipped');tree.write(p)
        with self.assertRaises(ValueError):junit(self.root)
    def test_missing_new_method_rejected(self):
        self.junit_fixture();p=self.root/'TEST-org.dpdns.gmm.NativeRecruitmentCounterReviewTest.xml';tree=ET.parse(p)
        tree.getroot().remove(tree.getroot().find('testcase'));tree.write(p)
        with self.assertRaises(ValueError):junit(self.root)
    @patch('verify_recruitment_revision_ci.verify_png',return_value={})
    def test_pending_claim_must_remain_locked_without_duplicate_write(self,mock):
        self.counter_fixture()
        p=self.root/'recruitment-counter/claim-state.json'; original=json.loads(p.read_text())
        for key,value in [('requestingClaimEnabled',True),('requestingBusy',False),('requestsAfterDuplicateTap',2),('refreshedCredits',0),('calls',[])]:
            with self.subTest(key=key):
                data=dict(original);data[key]=value;p.write_text(json.dumps(data))
                with self.assertRaises(ValueError):verify_counter(self.root)
    def test_asset_extension_allows_only_three_exact_paths(self):
        lock=json.loads((Path(__file__).parent/'theme-assets-lock.json').read_text()); entries=tray_lock()
        prefix=lock['source_prefix']+'/'
        allowed_asset_changes([prefix+e['path'] for e in entries],lock,entries)
        for suffix in ('recruitment/tray-other.png','recruitment/tray-low-v2.png','characters/home-guide-map.png'):
            with self.assertRaises(ValueError): allowed_asset_changes([prefix+suffix],lock,entries)
    def test_workflow_requires_review_and_each_evidence_gate_before_apk(self):
        workflow=(Path(__file__).resolve().parents[4]/'.github/workflows/home-assembly-check.yml').read_text()
        assemble=workflow.split('- name: Assemble debug and separately installable preview APKs')[1].split('id: assemble')[0]
        for required in ('revision_checks','revision_gate','revision_counter_evidence','revision_motion_evidence','revision_state_evidence','revision_currency_evidence','revision_team_evidence','revision_fullscreen_evidence','revision_insertion_evidence','revision_story_evidence','revision_modes_evidence'):
            self.assertIn("steps."+required+".outcome == 'success'",assemble)
        self.assertIn("steps.visual_approval.outputs.approved == 'true'",assemble)
        self.assertIn("needs.native-home.outputs.apk_built == 'success'",workflow.split('  preview-release:')[1])
        self.assertLess(workflow.index('id: revision_gate'),workflow.index('id: home\n'))
        self.assertIn('- galmaegiman-confirmed/app/**',workflow)
    def test_tray_lock_has_only_three_distinct_immutable_images(self):
        entries=tray_lock();self.assertEqual({e['path'] for e in entries},TRAYS)
        with self.assertRaises(ValueError): verify_trays(lambda p:b'',entries)
        with self.assertRaises(ValueError): verify_trays(lambda p:b'',entries+[entries[0]])

if __name__=='__main__':unittest.main()
