package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 웹 모드 선택판의 종이 바탕·도톰한 나무 테두리·네 모서리 장식. */
final class GamePanelDrawable extends Drawable {
  final MainActivity host;
  final Paint p = new Paint(3);

  GamePanelDrawable(MainActivity h) {
    host = h;
  }

  public void draw(Canvas c) {
    RectF r = new RectF(getBounds());
    r.inset(host.dp(1), host.dp(1));
    r.bottom -= host.dp(5);
    GameButtonDrawable.drawSurface(c, r, 0xffe2a764, host.dp(20), host.dp(2), host.dp(5), p);
    RectF paper = new RectF(r);
    paper.inset(host.dp(6), host.dp(6));
    p.setColor(0xfffff4e0);
    c.drawRoundRect(paper, host.dp(14), host.dp(14), p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(host.dp(2));
    p.setColor(0xffbd8749);
    c.drawRoundRect(paper, host.dp(14), host.dp(14), p);
    p.setStyle(Paint.Style.FILL);
    float inset = host.dp(16);
    for (int i = 0; i < 4; i++) {
      float x = i % 2 == 0 ? r.left + inset : r.right - inset,
          y = i < 2 ? r.top + inset : r.bottom - inset;
      p.setColor(0xffbd8749);
      c.drawCircle(x, y, host.dp(5), p);
      p.setColor(0xfff5cb64);
      c.drawCircle(x, y - host.dp(.5f), host.dp(3.7f), p);
    }
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
