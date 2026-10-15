package org.dpdns.gmm;

import android.graphics.*;

/** 뽑기·조합 연출도 같은 항해 기록부의 청록 표지 위에서 이어진다. */
final class NativeResultBackdrop {
  static void draw(MainActivity h, Canvas canvas, int width, int height, Paint paint) {
    float radius = Math.max(1, Math.max(width, height) * .7f);
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
    paint.setShader(
        new RadialGradient(
            width * .5f,
            height * .4f,
            radius,
            JournalTheme.TEAL,
            0xff102a2e,
            Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, width, height, paint);
    paint.setShader(null);
    paint.setColor(JournalTheme.BRASS);
    paint.setAlpha(18);
    paint.setStrokeWidth(h.dp(1));
    int step = Math.max(h.dp(40), 1);
    for (int x = step; x < width; x += step) canvas.drawLine(x, 0, x, height, paint);
    for (int y = step; y < height; y += step) canvas.drawLine(0, y, width, y, paint);
    paint.setAlpha(255);
    RectF edge = new RectF(h.dp(5), h.dp(5), width - h.dp(5), height - h.dp(5));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(h.dp(5));
    paint.setColor(JournalTheme.LEATHER);
    canvas.drawRoundRect(edge, h.dp(12), h.dp(12), paint);
    edge.inset(h.dp(4), h.dp(4));
    paint.setStrokeWidth(h.dp(1));
    paint.setColor(JournalTheme.BRASS);
    paint.setAlpha(160);
    canvas.drawRoundRect(edge, h.dp(8), h.dp(8), paint);
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);
  }

  private NativeResultBackdrop() {}
}
