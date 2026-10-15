package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 현장 기록부에 다는 두 동료의 황동 연결 문장. */
final class ComboEmblem extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);

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
    float x = b.exactCenterX(), y = b.exactCenterY(), r = b.width() * .27f;
    p.setStyle(Paint.Style.FILL);
    p.setColor(JournalTheme.LEATHER);
    c.drawRoundRect(new RectF(b), h.dp(7), h.dp(7), p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(h.dp(2));
    p.setColor(JournalTheme.BRASS);
    c.drawCircle(x - r * .48f, y, r, p);
    c.drawCircle(x + r * .48f, y, r, p);
    p.setStrokeWidth(h.dp(1));
    p.setColor(JournalTheme.PAPER);
    c.drawLine(x - r * 1.1f, y - r * .7f, x - r * .7f, y - r * 1.1f, p);
    c.drawLine(x + r * .7f, y + r * 1.1f, x + r * 1.1f, y + r * .7f, p);
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
