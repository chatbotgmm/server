#!/usr/bin/env python3
"""필수 업데이트 순수 Java 정책 실행 + 네이티브 연결의 정적 회귀 검사. Android 실행 검사가 아님."""
from pathlib import Path
import subprocess
import tempfile
import sys

root = Path(__file__).resolve().parents[1]
source = root / 'app/android/app/src/main/java/org/dpdns/gmm'
test = root / 'scripts/tests/RequiredUpdatePolicyTest.java'
if len(sys.argv) > 1:  # 다운로드한 검토용 평면 작업 폴더에서도 같은 파일 검사
    source = Path(sys.argv[1])
    test = source / 'RequiredUpdatePolicyTest.java'
with tempfile.TemporaryDirectory() as classes:
    subprocess.run(['java', '--add-modules', 'jdk.compiler', 'com.sun.tools.javac.Main',
                    '-d', classes, str(source / 'RequiredUpdatePolicy.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', classes, 'org.dpdns.gmm.RequiredUpdatePolicyTest'], check=True)
main = (source / 'MainActivity.java').read_text()
gate = (source / 'NativeRequiredUpdate.java').read_text()
dialog = (source / 'GameDialog.java').read_text()
checks = {
    '최초 로그인은 확인 뒤 시작': 'requiredUpdate = new NativeRequiredUpdate(this);' in main and 'if (!exchange(getIntent()))' not in main,
    '게임 요청 진입 차단': 'if (busy || updateBlocks()) return;' in main,
    '진행 로딩 지연': 'if (updateBlocks()) { requiredUpdate.deferLoad(target); return; }' in main,
    '로그인 딥링크 지연': 'if (updateBlocks()) { requiredUpdate.deferIntent(); return true; }' in main,
    '로그인 비동기 응답 차단': '|| (requiredUpdate != null && requiredUpdate.required())' in main,
    '뒤로 가기 차단': 'void back() {\n    if (updateBlocks()) return;' in main,
    '다중 Dialog 차단': 'if (host.updateBlocks()) host.requiredUpdate.holdGameDialog(dialog);' in dialog,
    '취소 불가능한 gate': 'dialog.setCancelable(false);' in gate and 'dialog.setCanceledOnTouchOutside(false);' in gate,
    '결과값으로 통과하지 않음': 'requiredUpdate.result();' in main and 'void result()' in gate and 'check(false);' in gate,
    '포그라운드에서만 실행': 'private void settle(String error) {\n    if (!resumed) return;' in gate and 'if (closed || !resumed || flowRunning) return;' in gate,
    '타임아웃 뒤 오래된 응답 제외': 'ticket == generation' in gate and 'generation++;' in gate and '12000' in gate,
    '실제 패키지 스토어 링크': 'market://details?id=" + a.getPackageName()' in gate,
    'Play 소유권 예외 분리': 'InstallErrorCode.ERROR_APP_NOT_OWNED' in gate,
    '진행 중 즉시 업데이트 재개': 'DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS' in gate and '(launch || inProgress)' in gate,
    '앱 종료 외 우회 버튼 없음': all(x not in gate for x in ['"나중에"', '"건너뛰기"', '"게임 시작"']),
}
for name, passed in checks.items():
    if not passed:
        raise AssertionError(name)
print(f'Required update wiring STATIC PASS: {len(checks)} (not Android runtime)')
