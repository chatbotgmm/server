#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC=app/android/app/src/main/java/org/dpdns/gmm
java --add-modules jdk.compiler com.sun.tools.javac.Main -d "$OUT" "$SRC/SkillHoldGesture.java" tests/SkillHoldGestureTest.java tests/ParseJava.java
java -cp "$OUT" org.dpdns.gmm.SkillHoldGestureTest
java --add-modules jdk.compiler -cp "$OUT" ParseJava "$SRC/SkillHoldGesture.java" "$SRC/SkillHoldPreview.java" "$SRC/SkillCardView.java" "$SRC/BattleView.java" "$SRC/NativeBattleSheets.java"

python3 - <<'PYTEST'
from pathlib import Path
source = Path('app/android/app/src/main/java/org/dpdns/gmm/SkillHoldPreview.java').read_text()
assert 'new androidx.appcompat.widget.AppCompatTextView(h)' in source
assert 'TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(details' in source
assert 'details.setAutoSizeTextTypeUniformWithConfiguration' not in source
assert 'h.safeBounds(h.root.getWidth(), h.root.getHeight())' in source
assert 'h.root.getLocationOnScreen(rootOrigin)' in source
assert 'visible.intersect(safeScreen)' in source
assert 'visible.intersect(clipped)' in source
assert 'anchor.getRootView()' in source
assert 'ThemeArt.Surface.PAPER' in source and 'GamePanelDrawable' not in source
print('PASS: API24 compatibility / safe-area / current-paper static guards (not runtime UI tests)')
PYTEST
