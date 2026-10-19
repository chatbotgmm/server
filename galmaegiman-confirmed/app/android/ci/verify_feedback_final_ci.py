#!/usr/bin/env python3
"""Fail closed on final feedback native captures and all original/new test methods."""
import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from verify_feedback_stage1_ci import (verify_junit as stage1_junit, verify_png,
    shop_profiles, rect, inside, same_rect, finite, integer, java_round)
from verify_registration_ci import check, emit, sha256

HERE = Path(__file__).resolve().parent
BASELINE = "b40dc7c1ed81ba1cdaa56da88d40a7f4996c992a"
def read(path): return json.loads(Path(path).read_text(encoding="utf-8"))
SUITES = read(HERE / "feedback-final-suites.json")
HOME = read(HERE / "feedback-final-home-party.json")
ATTENDANCE = read(HERE / "feedback-final-attendance.json")
APPROVED = {f"{prefix}{i}" for prefix, n in (("D",8),("E",8),("I",9),("T",8)) for i in range(1,n+1)}
SCENES = ("home","party-story","party-pvp","modes","realtime","missing-sprite")
COMMERCE = ("recruit","craft","masked-craft","mix-six","expedition-empty","expedition-ten","shop")

def junit(directory, report):
    result = stage1_junit(directory, report)
    check(len(SUITES) == 7 and sum(map(len, SUITES.values())) == 41, "Required final suites changed")
    for name, methods in SUITES.items():
        root = ET.parse(Path(directory) / ("TEST-" + name + ".xml")).getroot()
        cases = root.findall("testcase")
        check(root.get("name") == name and len(cases) == len(methods), "Wrong final suite identity/count")
        check(sorted(c.get("name") for c in cases) == sorted(methods), "Missing/duplicate final test method: " + name)
        check(all(c.get("classname") == name and not any(c.find(k) is not None for k in ("failure","error","skipped")) for c in cases),
              "Final feedback test failed/skipped: " + name)
    check(result["totals"]["tests"] >= 398 and result["totals"]["passed"] >= 382
          and result["totals"]["skipped"] <= 16, "Original/new executed test floor decreased")
    return result

def exact_keys(data, names, message):
    check(type(data) is dict and set(data) == set(names), message)

def pngs(directory, expected):
    check({p.name for p in directory.glob("*.png")} == set(expected), "Missing/extra captures: " + directory.name)
    return [verify_png(directory / name, *size, minimum_bytes=100 if name.startswith("button-") else 10000) for name, size in sorted(expected.items())]

def flags(value, expected):
    for key, want in expected.items():
        check(type(value.get(key)) is type(want) and value[key] == want, "Wrong/missing measured " + key)

def positive(value, keys):
    for key in keys: check(integer(value.get(key),1), "Missing positive measured " + key)

def profile(metric, p):
    for key in ("widthPx", "heightPx", "densityDpi"):
        check(type(metric.get(key)) is int and metric[key] == p[key], "Wrong profile " + key)
    check(finite(metric.get("fontScale")) and abs(metric["fontScale"]-p["fontScale"]) < .001, "Wrong font scale")

def home(root):
    directory = root / "feedback-home-party"
    expected = {p["name"]+"-"+scene+".png": (p["widthPx"],p["heightPx"]) for p in HOME["profiles"] for scene in SCENES}
    metrics, findings = read(directory/"metrics.json"), read(directory/"findings.json")
    exact_keys(metrics,expected,"HOME/PARTY metrics incomplete")
    exact_keys(findings,expected,"HOME/PARTY findings incomplete")
    manifest = read(directory/"manifest.json")
    flags(manifest,{"schemaVersion":1,"baseline":BASELINE,"requiredCaptureCount":24})
    check({c["filename"] for c in manifest["captures"]} == set(expected), "HOME/PARTY manifest capture mismatch")
    for p in HOME["profiles"]:
        for scene in SCENES:
            name = p["name"]+"-"+scene+".png"; m=metrics[name]
            check(findings[name] == [], "HOME/PARTY native finding: " + name)
            flags(m,{"scene":scene,"registrationViews":0,"apiWrites":0})
            profile(m["profile"],p)
            insets=p.get("insetsPx",[0,0,0,0])
            check(m["profile"]["insetsPx"] == dict(zip(("left","top","right","bottom"),insets)), "Wrong HOME safe insets")
            safe=[insets[0],insets[1],p["widthPx"]-insets[2],p["heightPx"]-insets[3]]
            art=m["artworks"]
            check(type(art) is list and len(art)==(0 if scene=="home" else 6),"Missing whole sprites")
            missing=0
            for a in art:
                outer=rect(a["viewPx"])
                check(inside(safe,outer) and same_rect(outer,a["visiblePx"]),"Clipped sprite")
                if a["id"]=="unapproved-identity-fixture":
                    missing+=1
                    flags(a,{"bitmapWidth":0,"bitmapHeight":0})
                    check(a["status"] and a["expectedSource"] is None
                          and a["targetPx"]==dict.fromkeys(("left","top","right","bottom"),0),"Unknown sprite substituted")
                else:
                    check(a["id"] in APPROVED and a["status"]=="" and a["expectedSource"]=="game-art/duel/sprites/"+a["id"]+"_s0_front.webp","Wrong sprite identity/source")
                    positive(a,("bitmapWidth","bitmapHeight"))
                    target=rect(a["targetPx"])
                    check(inside([0,0,outer[2]-outer[0],outer[3]-outer[1]],target),"Sprite target cropped")
                    check(abs((target[2]-target[0])/(target[3]-target[1])-a["bitmapWidth"]/a["bitmapHeight"])<.001,"Sprite aspect changed")
            check(missing==(2 if scene=="missing-sprite" else 0),"Missing identity scenario lost")
            check(type(m["controls"]) is list and m["controls"],"Missing control geometry")
            minimum=java_round(48*p["densityDpi"]/160)
            for control in m["controls"]:
                box=rect(control["boundsPx"])
                check(inside(safe,box) and box[2]-box[0]>=minimum and box[3]-box[1]>=minimum,"Clipped/small HOME/PARTY action")
            for label in m["liveLabels"]: check(inside(safe,label["boundsPx"]),"Clipped live label")
    states=read(directory/"button-states.json")
    exact_keys(states,("paper","primary","tab"),"Missing button styles")
    for kind, entries in states.items():
        exact_keys(entries,("default","pressed","released","cancelled","selected","disabled"),"Missing button state")
        for state, tones in entries.items():
            positive(tones,("samples",))
            key="navyFraction" if state=="pressed" else "paperFraction"
            check(finite(tones.get(key)) and (state=="disabled" or tones[key]>.55),"Wrong button visual state")
            expected[f"button-{kind}-{state}.png"]=(210,72)
    for kind in states:
        original=(directory/f"button-{kind}-default.png").read_bytes()
        for state in ("released","cancelled"):
            check((directory/f"button-{kind}-{state}.png").read_bytes()==original,"Button did not restore exact pixels")
        for state in ("pressed","disabled"):
            check((directory/f"button-{kind}-{state}.png").read_bytes()!=original,"Button feedback not visibly distinct")
    nav=read(directory/"home-navigation.json")
    routes={"0:lobby-expedition":"expedition","1:lobby-draw":"draw","2:lobby-collection":"collection",
            "3:lobby-craft":"craft","4:lobby-shop":"shop","5:lobby-play":"modes","6:lobby-daily":"daily",
            "8:lobby-settings":"settings","9:null":"shop","10:null":"shop","11:null":"shop","12:lobby-team":"team"}
    exact_keys(nav,routes,"HOME route coverage changed")
    for key,value in nav.items(): flags(value,{"route":routes[key],"cancelDispatches":0,"releaseDispatches":1})
    saves=read(directory/"party-save-behavior.json")
    exact_keys(saves,("story","pvp"),"Missing party save coverage")
    for name,entry in saves.items():
        flags(entry,{"writes":1,"saved":True,"path":"native/team" if name=="story" else "battle-team"})
        key="keys" if name=="story" else "ids"
        check(entry.get("payload")=={key:["o1","o4","o3"] if name=="story" else ["D1","D8","D3"]},
              "Party save payload/order changed")
    flags(read(directory/"realtime-locks.json"),dict.fromkeys(("pendingLock","readyLock","stalePhaseBlocked","deadlineBlocked"),True))
    drag=read(directory/"home-grounded-drag.json")
    flags(drag,{"controlsChecked":13,"actualTouchDispatch":True,"cancelledReturnSettled":True,"apiWrites":0})
    check(finite(drag.get("feetY")) and drag["feetY"]>0,"Missing grounded feet")
    expected["home-grounded-drag.png"]=(915,412)
    return pngs(directory,expected)

def commerce(root):
    directory=root/"feedback-commerce"
    expected={state+"-"+name:(p[0],p[1]) for name,p in shop_profiles().items() for state in COMMERCE}
    metrics,findings=read(directory/"geometry.json"),read(directory/"findings.json")
    exact_keys(metrics,expected,"Commerce matrix incomplete");exact_keys(findings,expected,"Commerce findings incomplete")
    for name,m in metrics.items():
        check(findings[name]==[],"Commerce native finding: "+name)
        flags(m,{"passed":True,"actualNativeViews":True,"apiWrites":0})
        positive(m,("touchTargets","nativeTextViews"))
        check(integer(m.get("wholeCharacterSprites")),"Missing character count")
        if name.startswith("expedition-"):check(m["wholeCharacterSprites"]>=8,"Missing expedition candidates")
    return pngs(directory,{name+".png":size for name,size in expected.items()})

def attendance(root):
    directory=root/"feedback-attendance";expected={}
    for group,states in (("attendance",ATTENDANCE["attendanceStates"]),("profile",["profile"])):
        names={state+"-"+p["name"]:p for p in ATTENDANCE["profiles"] for state in states}
        metrics=read(directory/(group+"-metrics.json"));findings=read(directory/(group+"-findings.json"))
        exact_keys(metrics,names,"Attendance/profile metric matrix incomplete")
        exact_keys(findings,names,"Attendance/profile finding matrix incomplete")
        for name,p in names.items():
            m=metrics[name];check(findings[name]==[],"Attendance native finding: "+name)
            profile(m,p);check(m["insetsPx"]==p["insetsPx"],"Wrong attendance safe area")
            flags(m,ATTENDANCE[group+"RequiredFields"]);positive(m,ATTENDANCE[group+"PositiveFields"])
            if group=="attendance":
                check(m["perPage"] in (2,4,6) and m["pageCount"]==(6+m["perPage"]-1)//m["perPage"],"Missing mission pages")
                if p["name"] in ("phone-915x412","tablet-1280x720"):check(m["pageCount"]==1,"Normal-size missions unnecessarily paged")
                captures=[name+"-page"+str(i+1)+".png" for i in range(m["pageCount"])]
                check(m["pageCaptures"]==captures,"Mission capture order/count mismatch")
            else:
                captures=[name+".png"];check(m["capture"]==captures[0],"Wrong profile capture")
            for capture in captures: expected[capture]=(p["widthPx"],p["heightPx"])
    return pngs(directory,expected)

def dossier(root):
    expected={f"{w}x{h}-dpi{dpi}-font{font}-{state}.png":(w,h)
              for w,h,dpi in ((915,412,160),(780,360,160),(2048,838,360),(1280,720,160))
              for font in (100,130) for state in ("results","record")}
    return pngs(root/"result-dossier",expected)

PROGRESSION_PROFILES = {
    f"{w}x{h}-dpi{dpi}-font{font}":dict(widthPx=w,heightPx=h,densityDpi=dpi,fontScale=font/100,insetsPx=[0,0,0,0])
    for w,h,dpi,font in ((915,374,160,100),(915,374,160,130),(780,319,160,100),(780,319,160,130),(2048,838,420,100))}
PROGRESSION_TABS = ("pass","reroll","reincarnation","reassessment")

def progression(root):
    directory=root/"feature-progression"
    expected={name+"-"+tab:(p["widthPx"],p["heightPx"]) for name,p in PROGRESSION_PROFILES.items() for tab in PROGRESSION_TABS}
    metrics,findings=read(directory/"geometry.json"),read(directory/"findings.json")
    exact_keys(metrics,expected,"Progression capture matrix incomplete")
    exact_keys(findings,expected,"Progression finding matrix incomplete")
    for name,p in PROGRESSION_PROFILES.items():
        for tab in PROGRESSION_TABS:
            key=name+"-"+tab;m=metrics[key]
            check(findings[key]==[],"Progression native finding: "+key)
            flags(m,{"actualNativeViews":True,"apiWrites":0,"scrollContainers":0,"tab":tab})
            profile(m["profile"],p);check(m["profile"]["insetsPx"]==p["insetsPx"],"Wrong progression safe area")
            safe=[0,0,p["widthPx"],p["heightPx"]];minimum=java_round(48*p["densityDpi"]/160)
            controls=m.get("controls");check(type(controls) is list and controls,"Missing progression controls")
            check(integer(m.get("targets"),1) and m["targets"]==len(controls),"Progression action coverage mismatch")
            tags=[c.get("tag") for c in controls]
            required={"progression-tab-"+t for t in PROGRESSION_TABS}|{"progression-refresh"}
            required.add({"pass":"progression-pass-m1","reroll":"progression-reroll-claim",
                          "reincarnation":"progression-reincarnation-owned-1","reassessment":"progression-reassessment-owned-2"}[tab])
            if tab!="reroll":required|={"progression-previous","progression-next"}
            check(len(set(tags))==len(tags) and required<=set(tags),"Missing/duplicate progression destination")
            for c in controls:
                b=rect(c["boundsPx"])
                check(inside(safe,b) and same_rect(b,c["visibleBoundsPx"]),"Clipped progression control")
                check(type(c.get("minTouchTargetPx")) is int and c["minTouchTargetPx"]==minimum
                      and b[2]-b[0]>=minimum and b[3]-b[1]>=minimum,"Progression action below 48dp")
            texts=m.get("texts");check(type(texts) is list and len(texts)>=len(required),"Missing progression text measurements")
            for t in texts:
                value=t.get("text");check(type(value) is str and value,"Missing actual progression text")
                check(inside(safe,t["boundsPx"]) and same_rect(t["boundsPx"],t["visibleBoundsPx"])
                      and inside(t["visibleBoundsPx"],t["glyphBoundsPx"]),"Clipped progression text/glyph")
                flags(t,{"ellipsisCount":0})
                positive(t,("textLength","laidOutTextEnd","layoutHeightPx","contentHeightPx"))
                check(t["textLength"]==t["laidOutTextEnd"]==len(value.encode("utf-16-le"))//2,"Progression text truncated")
                check(t["layoutHeightPx"]<=t["contentHeightPx"],"Progression text height clipped")
            joined=" ".join(t["text"] for t in texts)
            for term in {"pass":("100점","지원서 3장"),"reroll":("5시간","30분"),
                         "reincarnation":("owned-1","환생"),"reassessment":("owned-2","재심사")}[tab]:
                check(term in joined,"Missing server progression state: "+term)
    return pngs(directory,{name+".png":size for name,size in expected.items()})

def verify(root,results,report):
    check(root.is_dir(),"Missing native evidence directory")
    return {"status":"passed","junit":junit(results,report),
            "home_party":home(root),"commerce":commerce(root),"attendance":attendance(root),"result_dossier":dossier(root),"progression":progression(root),
            "visual_review":"pending; automated evidence does not replace inspecting actual PNGs",
            "physical_device_review":"not_run"}

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-review",type=Path,required=True)
    parser.add_argument("--test-results",type=Path,required=True)
    parser.add_argument("--full-suite-report",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args(argv)
    try: result=verify(args.native_review,args.test_results,read(args.full_suite_report))
    except Exception as error:
        emit({"status":"failed","error":str(error)},args.output);print(str(error),file=sys.stderr);return 1
    emit(result,args.output);return 0
if __name__=="__main__":sys.exit(main())
