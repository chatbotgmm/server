"""Generate shared Node cases, ensure the bundled fixture hasn't drifted, run Java parity checks.
No network, Android APK, account or database operations.
"""
import argparse,json,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--javac',required=True);p.add_argument('--json-jar',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[2]
main=root/'app/android/app/src/main/java/org/dpdns/gmm'
debug=root/'app/android/app/src/debug/java/org/dpdns/gmm'
with tempfile.TemporaryDirectory(prefix='combat-offline-') as tmp:
 t=Path(tmp);cases=t/'cases.json';fixture=t/'fixture.json'
 subprocess.run(['node',str(root/'review/combat-v2/offline-cases.mjs'),str(cases),str(fixture)],check=True)
 bundled=root/'app/android/app/src/debug/assets/combat-v2/offline-fixture.json'
 assert json.loads(fixture.read_text())==json.loads(bundled.read_text()),'Bundled fixture differs from Node reference'
 sources=[main/'CombatV2ReviewContract.java',main/'SkillHoldGesture.java',debug/'CombatV2OfflineEngine.java',debug/'CombatV2OfflineSession.java',root/'review/combat-v2/OfflineParityCheck.java']
 subprocess.run([a.javac,'-cp',a.json_jar,'-d',tmp,*map(str,sources)],check=True)
 subprocess.run([a.java,'-cp',tmp+':'+a.json_jar,'org.dpdns.gmm.OfflineParityCheck',str(cases)],check=True)
