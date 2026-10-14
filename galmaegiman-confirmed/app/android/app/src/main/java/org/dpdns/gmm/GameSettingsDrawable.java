package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class GameSettingsDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);

  GameSettingsDrawable(MainActivity h) {
    this.h = h;
  }

  public void draw(Canvas c) {
    RectF r = new RectF(getBounds());
    r.inset(h.dp(2), h.dp(2));
    p.setColor(0xffefdcb6);
    c.drawRoundRect(r, h.dp(22), h.dp(22), p);
    c.save();
    Path clip = new Path();
    clip.addRoundRect(r, h.dp(22), h.dp(22), Path.Direction.CW);
    c.clipPath(clip);
    p.setColor(0x096c4c24);
    for (float y = r.top + h.dp(8); y < r.bottom; y += h.dp(15))
      for (float x = r.left + h.dp(7); x < r.right; x += h.dp(17)) c.drawCircle(x, y, h.dp(1), p);
    c.restore();
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(h.dp(3));
    p.setColor(0xffd4a971);
    c.drawRoundRect(r, h.dp(22), h.dp(22), p);
    p.setStyle(Paint.Style.FILL);
  }

  public void setAlpha(int a) {
    p.setAlpha(a);
  }

  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
