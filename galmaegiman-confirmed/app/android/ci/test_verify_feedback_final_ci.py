"""Synthetic contract tests; these do not substitute for native graphics execution."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import verify_feedback_final_ci as v

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value))

def attendance_fixture(root):
    d=root/"feedback-attendance"
    for group,states in (("attendance",v.ATTENDANCE["attendanceStates"]),("profile",["profile"])):
        metrics={};findings={}
        for p in v.ATTENDANCE["profiles"]:
            for state in states:
                name=state+"-"+p["name"]
                m={k:p[k] for k in ("widthPx","heightPx","densityDpi","fontScale","insetsPx")}
                m.update(v.ATTENDANCE[group+"RequiredFields"])
                m.update(dict.fromkeys(v.ATTENDANCE[group+"PositiveFields"],1))
                if group=="attendance":
                    m.update(perPage=6,pageCount=1,pageCaptures=[name+"-page1.png"])
                    captures=m["pageCaptures"]
                else:m["capture"]=name+".png";captures=[m["capture"]]
                for capture in captures:(d/capture).parent.mkdir(parents=True,exist_ok=True);(d/capture).touch()
                metrics[name]=m;findings[name]=[]
        write(d/(group+"-metrics.json"),metrics);write(d/(group+"-findings.json"),findings)
    return d

def commerce_fixture(root):
    d=root/"feedback-commerce"
    metrics={};findings={}
    for name,p in v.shop_profiles().items():
        for state in v.COMMERCE:
            key=state+"-"+name
            metrics[key]=dict(passed=True,actualNativeViews=True,apiWrites=0,touchTargets=1,nativeTextViews=1,wholeCharacterSprites=8)
            findings[key]=[]
            d.mkdir(exist_ok=True);(d/(key+".png")).touch()
    write(d/"geometry.json",metrics);write(d/"findings.json",findings)
    return d

def home_fixture(root):
    d=root/"feedback-home-party";d.mkdir()
    metrics={};findings={};captures=[]
    for p in v.HOME["profiles"]:
        for scene in v.SCENES:
            name=p["name"]+"-"+scene+".png";(d/name).touch()
            profile=copy.deepcopy(p)
            profile["insetsPx"]=dict(zip(("left","top","right","bottom"),p.get("insetsPx",[0,0,0,0])))
            art=[]
            if scene!="home":
                for i in range(6):
                    a=dict(id="D"+str(i+1),status="",viewPx=[100+i*100,50,180+i*100,150],visiblePx=[100+i*100,50,180+i*100,150],
                           targetPx=[0,0,80,100],bitmapWidth=80,bitmapHeight=100,expectedSource=f"game-art/duel/sprites/D{i+1}_s0_front.webp")
                    if scene=="missing-sprite" and i<2:
                        a.update(id="unapproved-identity-fixture",status="이미지 없음",bitmapWidth=0,bitmapHeight=0,expectedSource=None,
                                 targetPx=dict.fromkeys(("left","top","right","bottom"),0))
                    art.append(a)
            metrics[name]=dict(scene=scene,profile=profile,registrationViews=0,apiWrites=0,artworks=art,
                               controls=[dict(boundsPx=[100,160,230,290])],liveLabels=[dict(boundsPx=[100,40,200,60])])
            findings[name]=[];captures.append(dict(filename=name,profile=p["name"],scene=scene))
    write(d/"metrics.json",metrics);write(d/"findings.json",findings)
    write(d/"manifest.json",dict(schemaVersion=1,baseline=v.BASELINE,requiredCaptureCount=24,captures=captures))
    states={}
    for kind in ("paper","primary","tab"):
        states[kind]={}
        for state in ("default","pressed","released","cancelled","selected","disabled"):
            states[kind][state]=dict(samples=10,paperFraction=.9 if state!="pressed" else .1,navyFraction=.9 if state=="pressed" else .1)
            (d/f"button-{kind}-{state}.png").write_bytes(state.encode() if state in ("pressed","disabled") else b"paper")
    write(d/"button-states.json",states)
    write(d/"home-navigation.json",{k:dict(cancelDispatches=0,releaseDispatches=1,route=route) for k,route in {"0:lobby-expedition":"expedition","1:lobby-draw":"draw","2:lobby-collection":"collection","3:lobby-craft":"craft","4:lobby-shop":"shop","5:lobby-play":"modes","6:lobby-daily":"daily","8:lobby-settings":"settings","9:null":"shop","10:null":"shop","11:null":"shop","12:lobby-team":"team"}.items()})
    write(d/"party-save-behavior.json",{name:dict(writes=1,saved=True,path="native/team" if name=="story" else "battle-team",payload={"keys":["o1","o4","o3"]} if name=="story" else {"ids":["D1","D8","D3"]}) for name in ("story","pvp")})
    write(d/"realtime-locks.json",dict.fromkeys(("pendingLock","readyLock","stalePhaseBlocked","deadlineBlocked"),True))
    write(d/"home-grounded-drag.json",dict(controlsChecked=13,actualTouchDispatch=True,cancelledReturnSettled=True,apiWrites=0,feetY=300))
    (d/"home-grounded-drag.png").touch()
    return d

def progression_fixture(root):
    d=root/"feature-progression";d.mkdir()
    metrics={};findings={}
    for name,p in v.PROGRESSION_PROFILES.items():
        for tab in v.PROGRESSION_TABS:
            key=name+"-"+tab;minimum=v.java_round(48*p["densityDpi"]/160)
            tags=["progression-tab-"+t for t in v.PROGRESSION_TABS]+["progression-refresh"]
            tags.append({"pass":"progression-pass-m1","reroll":"progression-reroll-claim",
                         "reincarnation":"progression-reincarnation-owned-1","reassessment":"progression-reassessment-owned-2"}[tab])
            if tab!="reroll":tags+=["progression-previous","progression-next"]
            controls=[dict(tag=tag,boundsPx=[0,0,minimum,minimum],visibleBoundsPx=[0,0,minimum,minimum],minTouchTargetPx=minimum) for tag in tags]
            label={"pass":"100점 지원서 3장","reroll":"5시간 30분","reincarnation":"owned-1 환생","reassessment":"owned-2 재심사"}[tab]
            texts=[dict(text=label,boundsPx=[0,0,200,40],visibleBoundsPx=[0,0,200,40],glyphBoundsPx=[2,2,190,30],
                        ellipsisCount=0,textLength=len(label),laidOutTextEnd=len(label),layoutHeightPx=32,contentHeightPx=40) for _ in tags]
            metrics[key]=dict(profile=p.copy(),tab=tab,actualNativeViews=True,apiWrites=0,scrollContainers=0,
                              controls=controls,texts=texts,targets=len(controls))
            findings[key]=[];(d/(key+".png")).touch()
    write(d/"geometry.json",metrics);write(d/"findings.json",findings)
    return d

class Contracts(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.png=patch.object(v,"verify_png",side_effect=lambda p,*a,**k:{"file":p.name});self.png.start();self.addCleanup(self.png.stop)
    def edit(self,d,file,fn):
        value=v.read(d/file);fn(value);write(d/file,value)
    def test_progression_full_positive(self):
        progression_fixture(self.root);self.assertEqual(20,len(v.progression(self.root)))
    def test_progression_missing_image(self):
        d=progression_fixture(self.root);next(d.glob("*.png")).unlink()
        with self.assertRaises(Exception):v.progression(self.root)
    def test_home_full_positive(self):
        home_fixture(self.root);self.assertEqual(43,len(v.home(self.root)))
    def test_commerce_full_positive(self):
        commerce_fixture(self.root);self.assertEqual(119,len(v.commerce(self.root)))
    def test_attendance_full_positive(self):
        attendance_fixture(self.root);self.assertEqual(40,len(v.attendance(self.root)))
    def test_attendance_all_mission_pages(self):
        d=attendance_fixture(self.root);data=v.read(d/"attendance-metrics.json")
        for key,m in data.items():
            if "phone-915x412" in key or "tablet-1280x720" in key:continue
            m.update(perPage=2,pageCount=3,pageCaptures=[key+"-page"+str(i)+".png" for i in (1,2,3)])
            for file in m["pageCaptures"]:(d/file).touch()
        write(d/"attendance-metrics.json",data)
        self.assertEqual(88,len(v.attendance(self.root)))
    def test_dossier_exact_matrix(self):
        d=self.root/"result-dossier";d.mkdir()
        for w,h,dpi in ((915,412,160),(780,360,160),(2048,838,360),(1280,720,160)):
            for font in (100,130):
                for state in ("results","record"):(d/f"{w}x{h}-dpi{dpi}-font{font}-{state}.png").touch()
        self.assertEqual(16,len(v.dossier(self.root)))
        next(d.glob("*.png")).unlink()
        with self.assertRaises(Exception):v.dossier(self.root)
    def test_png_extra_or_missing_rejected(self):
        d=commerce_fixture(self.root);(d/"unrequested.png").touch()
        with self.assertRaises(Exception):v.commerce(self.root)
        (d/"unrequested.png").unlink();next(d.glob("*.png")).unlink()
        with self.assertRaises(Exception):v.commerce(self.root)
    def test_button_restoration_is_byte_exact(self):
        d=home_fixture(self.root);(d/"button-primary-cancelled.png").write_bytes(b"navy")
        with self.assertRaises(Exception):v.home(self.root)
    def test_junit_new_suite_required_and_floor(self):
        results=self.root/"xml";results.mkdir()
        for name,methods in v.SUITES.items():
            root=ET.Element("testsuite",name=name)
            for method in methods:ET.SubElement(root,"testcase",name=method,classname=name)
            ET.ElementTree(root).write(results/("TEST-"+name+".xml"))
        stats={"totals":dict(tests=398,passed=382,skipped=16)}
        with patch.object(v,"stage1_junit",return_value=stats):
            self.assertEqual(stats,v.junit(results,{}))
            for key,bad in (("tests",397),("passed",381),("skipped",17)):
                with self.subTest(key=key):
                    broken=copy.deepcopy(stats);broken["totals"][key]=bad
                    with patch.object(v,"stage1_junit",return_value=broken),self.assertRaises(Exception):v.junit(results,{})
            name=next(iter(v.SUITES));p=results/("TEST-"+name+".xml")
            for element in ("skipped","failure","error"):
                tree=ET.parse(p);ET.SubElement(tree.getroot().find("testcase"),element);tree.write(p)
                with self.subTest(element=element),self.assertRaises(Exception):v.junit(results,{})
                tree.getroot().find("testcase").remove(tree.getroot().find("testcase/"+element));tree.write(p)
            tree=ET.parse(p);tree.getroot().find("testcase").set("name","renamed");tree.write(p)
            with self.assertRaises(Exception):v.junit(results,{})

    def test_cli_failure_emits_report(self):
        output=self.root/"failed.json";report=self.root/"full.json";write(report,{})
        self.assertEqual(1,v.main(["--native-review",str(self.root/"absent"),"--test-results",str(self.root),
                                  "--full-suite-report",str(report),"--output",str(output)]))
        self.assertEqual("failed",v.read(output)["status"])
    def test_final_gate_before_targeted_reruns_and_required_for_apk(self):
        workflow=(Path(__file__).resolve().parents[4]/".github/workflows/home-assembly-check.yml").read_text()
        gate=workflow.index("id: feedback_final_gate");evidence=workflow.index("id: feedback_final_evidence")
        rerun=workflow.index("- name: Targeted home lobby capture")
        assembly=workflow.index("- name: Assemble debug and separately installable preview APKs")
        self.assertLess(gate,evidence);self.assertLess(evidence,rerun);self.assertLess(rerun,assembly)
        for step in ("feedback_final_checks","feedback_final_gate","feedback_final_evidence"):
            self.assertIn("steps."+step+".outcome == 'success'",workflow[assembly:workflow.index("id: assemble",assembly)])
        self.assertIn("--minimum-tests 398 --previous-skipped 16",workflow)
        for suite in v.SUITES:self.assertIn("--require-unskipped-suite "+suite,workflow)
        self.assertIn("if-no-files-found: error",workflow[evidence:rerun])
    def test_backend_and_release_keep_permission_boundary(self):
        workflow=(Path(__file__).resolve().parents[4]/".github/workflows/home-assembly-check.yml").read_text()
        backend=workflow[workflow.index("  backend:"):workflow.index("  native-home:")]
        self.assertIn("contents: read",backend);self.assertNotIn("contents: write",backend)
        self.assertNotIn("secrets.",backend);self.assertNotIn("deploy",backend)
        for required in ("node-version: '22'","npm ci","npx prisma generate","npm run test:db","npm run build","DATABASE_URL: file:./ci.db"):
            self.assertIn(required,backend)
        release=workflow[workflow.index("  preview-release:"):]
        self.assertIn("needs: [native-home, backend]",release)
        self.assertIn("contents: write",release)
        self.assertIn("--prerelease --latest=false",release)
        self.assertIn('test "$target" = "$SHA"',release)


def add_mutations():
    groups=[
      ("attendance",attendance_fixture,v.attendance,"attendance-metrics.json",
       [("apiWrites",1),("actualNativeViews",False),("widthPx",1),("heightPx",1),("densityDpi",1),("fontScale",.5),
        ("insetsPx",[1,2,3,4]),("baseline","wrong"),("missionCount",5),("rewardCells",6),("checkedBounds",0),
        ("apiReads",0),("perPage",3),("pageCount",2),("pageCaptures",[])]),
      ("profile",attendance_fixture,v.attendance,"profile-metrics.json",
       [("apiWrites",1),("checkedBounds",0),("profileSettingsAbsent",False),("homeGearOpensSettings",False),("capture","wrong.png")]),
      ("commerce",commerce_fixture,v.commerce,"geometry.json",
       [("passed",False),("actualNativeViews",False),("apiWrites",1),("nativeTextViews",0),("touchTargets",0),("wholeCharacterSprites",-1)]),
      ("home",home_fixture,v.home,"metrics.json",
       [("apiWrites",1),("registrationViews",1),("controls",[]),("scene","wrong")])]
    for group,fixture,verify,file,mutations in groups:
        for key,bad in mutations:
            def test(self,fixture=fixture,verify=verify,file=file,key=key,bad=bad):
                d=fixture(self.root)
                self.edit(d,file,lambda data:next(iter(data.values())).__setitem__(key,bad))
                with self.assertRaises(Exception):verify(self.root)
            setattr(Contracts,"test_reject_"+group+"_"+key,test)
    for group,fixture,verify,file in [
      ("home",home_fixture,v.home,"findings.json"),("commerce",commerce_fixture,v.commerce,"findings.json"),
      ("attendance",attendance_fixture,v.attendance,"attendance-findings.json")]:
        for mode in ("nonempty","missing"):
            def test(self,fixture=fixture,verify=verify,file=file,mode=mode):
                d=fixture(self.root)
                def mutate(data):
                    key=next(iter(data))
                    if mode=="missing":data.pop(key)
                    else:data[key]=["actual native clipping"]
                self.edit(d,file,mutate)
                with self.assertRaises(Exception):verify(self.root)
            setattr(Contracts,"test_reject_"+group+"_findings_"+mode,test)
    for key,bad in (("id","D99"),("status","missing"),("expectedSource","game-art/wrong.webp"),("bitmapWidth",0),
                    ("bitmapHeight",0),("targetPx",[0,0,90,100]),("visiblePx",[100,50,180,149])):
        def test(self,key=key,bad=bad):
            d=home_fixture(self.root)
            self.edit(d,"metrics.json",lambda data:data["phone-915x412-party-story.png"]["artworks"][0].__setitem__(key,bad))
            with self.assertRaises(Exception):v.home(self.root)
        setattr(Contracts,"test_reject_sprite_"+key,test)
    for file,key,bad in (("home-navigation.json","route","wrong"),("home-navigation.json","cancelDispatches",1),
                         ("party-save-behavior.json","writes",2),("party-save-behavior.json","path","wrong"),
                         ("party-save-behavior.json","payload",{})):
        def test(self,file=file,key=key,bad=bad):
            d=home_fixture(self.root)
            self.edit(d,file,lambda data:next(iter(data.values())).__setitem__(key,bad))
            with self.assertRaises(Exception):v.home(self.root)
        setattr(Contracts,"test_reject_behavior_"+key,test)

    for key,bad in (("actualNativeViews",False),("apiWrites",1),("scrollContainers",1),("tab","wrong"),("targets",0),
                    ("controls",[]),("texts",[])):
        def test(self,key=key,bad=bad):
            d=progression_fixture(self.root)
            self.edit(d,"geometry.json",lambda data:next(iter(data.values())).__setitem__(key,bad))
            with self.assertRaises(Exception):v.progression(self.root)
        setattr(Contracts,"test_reject_progression_"+key,test)
    for key,bad in (("text",""),("ellipsisCount",1),("laidOutTextEnd",1),("textLength",1),("layoutHeightPx",41),
                    ("contentHeightPx",1),("glyphBoundsPx",[0,0,300,40]),("visibleBoundsPx",[0,0,100,40])):
        def test(self,key=key,bad=bad):
            d=progression_fixture(self.root)
            self.edit(d,"geometry.json",lambda data:next(iter(data.values()))["texts"][0].__setitem__(key,bad))
            with self.assertRaises(Exception):v.progression(self.root)
        setattr(Contracts,"test_reject_progression_text_"+key,test)
    for key,bad in (("tag","absent"),("minTouchTargetPx",47),("boundsPx",[0,0,47,48]),("visibleBoundsPx",[0,0,47,48])):
        def test(self,key=key,bad=bad):
            d=progression_fixture(self.root)
            self.edit(d,"geometry.json",lambda data:next(iter(data.values()))["controls"][0].__setitem__(key,bad))
            with self.assertRaises(Exception):v.progression(self.root)
        setattr(Contracts,"test_reject_progression_control_"+key,test)
    for key,bad in (("widthPx",1),("heightPx",1),("densityDpi",1),("fontScale",.5),("insetsPx",[1,0,0,0])):
        def test(self,key=key,bad=bad):
            d=progression_fixture(self.root)
            self.edit(d,"geometry.json",lambda data:next(iter(data.values()))["profile"].__setitem__(key,bad))
            with self.assertRaises(Exception):v.progression(self.root)
        setattr(Contracts,"test_reject_progression_profile_"+key,test)
    for mode in ("missing","nonempty"):
        def test(self,mode=mode):
            d=progression_fixture(self.root)
            def mutate(data):
                key=next(iter(data))
                if mode=="missing":data.pop(key)
                else:data[key]=["actual native overflow"]
            self.edit(d,"findings.json",mutate)
            with self.assertRaises(Exception):v.progression(self.root)
        setattr(Contracts,"test_reject_progression_findings_"+mode,test)

add_mutations()
if __name__=="__main__":unittest.main()
