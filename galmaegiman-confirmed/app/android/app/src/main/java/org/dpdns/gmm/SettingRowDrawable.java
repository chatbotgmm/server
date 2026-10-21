package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 내부 설정 행은 조용한 구분선만 사용한다. 큰 외곽 프레임을 반복하지 않는다. */
final class SettingRowDrawable extends Drawable {
  final MainActivity host;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  int alpha = 255;
  SettingRowDrawable(MainActivity host) { this.host = host; }
  @Override public void draw(Canvas canvas) {
    Rect b = getBounds();
    paint.setColor(0x30776443); paint.setAlpha(Color.alpha(0x30776443) * alpha / 255);
    paint.setStrokeWidth(host.dp(.7f));
    canvas.drawLine(b.left + host.dp(4), b.bottom - host.dp(1),
        b.right - host.dp(4), b.bottom - host.dp(1), paint);
  }
  @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
