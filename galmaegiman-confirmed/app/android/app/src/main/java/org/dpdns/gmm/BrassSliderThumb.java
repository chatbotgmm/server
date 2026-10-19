package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Android SeekBar의 터치·키보드·TalkBack 처리는 그대로 두고 손잡이만 황동으로 그린다. */
final class BrassSliderThumb extends Drawable {
  final MainActivity host;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  int alpha = 255;
  ColorFilter filter;
  BrassSliderThumb(MainActivity host) { this.host = host; }
  @Override public int getIntrinsicWidth() { return host.dp(24); }
  @Override public int getIntrinsicHeight() { return host.dp(24); }
  @Override public void draw(Canvas canvas) {
    Rect b = getBounds(); float cx = b.exactCenterX(), cy = b.exactCenterY();
    float radius = Math.min(b.width(), b.height()) / 2f - host.dp(2);
    paint.setColorFilter(filter); paint.setAlpha(alpha); paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xff72502b); paint.setAlpha(alpha);
    canvas.drawCircle(cx, cy + host.dp(1), radius, paint);
    paint.setShader(new LinearGradient(cx, cy - radius, cx, cy + radius,
        new int[] {0xffffe6a1, 0xffbd8640, 0xffe9c16d}, null, Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, radius - host.dp(1), paint);
    paint.setShader(null); paint.setColor(0xff6b4b27); paint.setAlpha(alpha);
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(host.dp(1));
    canvas.drawCircle(cx, cy, radius - host.dp(3), paint);
    paint.setStyle(Paint.Style.FILL);
  }
  @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
