package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 두 동료의 힘이 합쳐지는 합동기 전용 문장. */
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
    float x = b.exactCenterX(), y = b.exactCenterY(), r = b.width() * .30f;
    p.setColor(0xff9b642c);
    c.drawCircle(x - r * .42f, y, r, p);
    c.drawCircle(x + r * .42f, y, r, p);
    p.setColor(0xffffed9c);
    c.drawCircle(x - r * .42f, y - r * .1f, r * .8f, p);
    p.setColor(0xffe6ae45);
    c.drawCircle(x + r * .42f, y - r * .1f, r * .8f, p);
    Path star = new Path();
    for (int i = 0; i < 10; i++) {
      double a = i * Math.PI / 5 - Math.PI / 2;
      float rr = r * (i % 2 == 0 ? .9f : .4f);
      float sx = x + (float) Math.cos(a) * rr, sy = y + (float) Math.sin(a) * rr;
      if (i == 0) star.moveTo(sx, sy);
      else star.lineTo(sx, sy);
    }
    star.close();
    p.setColor(0xfffffaf0);
    c.drawPath(star, p);
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
