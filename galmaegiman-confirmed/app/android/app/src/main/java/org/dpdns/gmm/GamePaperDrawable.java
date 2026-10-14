package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 웹 GAMEUI의 크림 카드·연한 테두리·짧은 바닥 그림자. */
final class GamePaperDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  final int edge;

  GamePaperDrawable(MainActivity h) {
    this(h, 0xffd9b98a);
  }

  GamePaperDrawable(MainActivity h, int edge) {
    this.h = h;
    this.edge = edge;
  }

  public void draw(Canvas c) {
    RectF r = new RectF(getBounds());
    r.inset(h.dp(1), h.dp(1));
    r.bottom -= h.dp(3);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0x447a4e2a);
    RectF shadow = new RectF(r);
    shadow.offset(0, h.dp(3));
    c.drawRoundRect(shadow, h.dp(16), h.dp(16), p);
    p.setColor(0xfffffaf0);
    c.drawRoundRect(r, h.dp(16), h.dp(16), p);
    p.setColor(edge);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(h.dp(2));
    c.drawRoundRect(r, h.dp(16), h.dp(16), p);
    p.setStyle(Paint.Style.FILL);
    p.setAlpha(255);
  }

  public void setAlpha(int a) {
    p.setAlpha(a);
    invalidateSelf();
  }

  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
    invalidateSelf();
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
