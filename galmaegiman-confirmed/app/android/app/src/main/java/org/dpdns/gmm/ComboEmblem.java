package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** C 합동기의 청록 표지에 쓰는 두 줄 파도와 황동 항해 인장. */
final class ComboEmblem extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  final Path wave = new Path();

  ComboEmblem(MainActivity h) {
    this.h = h;
    setBounds(0, 0, h.dp(38), h.dp(38));
  }

  @Override
  public int getIntrinsicWidth() {
    return h.dp(38);
  }

  @Override
  public int getIntrinsicHeight() {
    return h.dp(38);
  }

  @Override
  public void draw(Canvas c) {
    Rect b = getBounds();
    float x = b.exactCenterX(), y = b.exactCenterY(), r = b.width() * .40f;
    p.setStyle(Paint.Style.FILL);
    p.setColor(JournalTheme.TEAL);
    c.drawCircle(x, y, r, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(h.dp(1));
    p.setColor(JournalTheme.BRASS);
    c.drawCircle(x, y, r, p);
    p.setStrokeWidth(h.dp(1.6f));
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setColor(JournalTheme.PAPER);
    for (int line = 0; line < 2; line++) {
      float level = y + (line == 0 ? -.15f : .25f) * r;
      wave.reset();
      wave.moveTo(x - r * .67f, level);
      wave.cubicTo(
          x - r * .25f,
          level + r * .28f,
          x - r * .18f,
          level - r * .52f,
          x + r * .13f,
          level - r * .24f);
      wave.cubicTo(
          x + r * .30f,
          level + r * .04f,
          x + r * .46f,
          level + r * .17f,
          x + r * .67f,
          level - r * .06f);
      c.drawPath(wave, p);
    }
    p.setStyle(Paint.Style.FILL);
  }

  @Override
  public void setAlpha(int a) {
    p.setAlpha(a);
  }

  @Override
  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
