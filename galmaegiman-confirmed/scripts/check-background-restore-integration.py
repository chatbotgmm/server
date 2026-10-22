#!/usr/bin/env python3
"""배경 복원 연결 정적 계약 검사. Android 실행/육안 검증을 대체하지 않는다."""
from pathlib import Path
import sys
root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
java = root / 'app/android/app/src/main/java/org/dpdns/gmm'
def require(file, *tokens):
    text = (java / file).read_text()
    for token in tokens:
        assert token in text, (file, token)
require('MainActivity.java', 'BackgroundMotionBinding backgroundEffects;',
        'backgroundEffects = new BackgroundMotionBinding(this)', 'backgroundEffects.resumed(true)',
        'backgroundEffects.resumed(false)', 'backgroundEffects.effect.refreshLifecycle()',
        'backgroundEffects.trim()', 'backgroundEffects.close()')
require('GameRootLayout.java', 'backgroundEffects.reset()')
require('ThemeArt.java', 'backgroundEffects.draw(canvas, "native/release-v1/" + file', 'this::invalidateSelf')
require('HomeArt.java', 'backgroundEffects.draw(canvas, "native/release-v1/backgrounds/bg-home.png"', 'owner::invalidate')
require('LobbyView.java', 'new HomeArt(host, this)')
require('BattleView.java', 'backgroundEffects.boat.draw(', 'backgroundEffects.draw(', '"battle-v1/bg-harbor.png"')
require('BackgroundMotionOverlay.java', 'request != generation', 'decoder.shutdownNow()',
        '!reducedMotion', 'runtimeGate.getAsBoolean()', 'canvas.clipPath(waterMask)', 'if (!canAnimate()) return')
require('BackgroundMotionBinding.java', 'effect.unbindCompositeOwner()', 'Matrix.ScaleToFit.FILL')
print('배경 8종 렌더 경로·소유 View·생명주기·정적 fallback 계약 검사 통과 (실기기 검증 아님)')
