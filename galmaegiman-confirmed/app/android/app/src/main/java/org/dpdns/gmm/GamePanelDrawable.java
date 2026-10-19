package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 넓은 기록판은 목재 받침·양피지·황동 지도 모서리로 통일한다. */
final class GamePanelDrawable extends Drawable {
  final MainActivity host;
  final Paint p = new Paint(3);
  private final RectF bounds = new RectF();
  private int alpha = 255;
  private ColorFilter filter;

  GamePanelDrawable(MainActivity h) {
    host = h;
    ThemeArt.of(h).image(ThemeArt.file(ThemeArt.Surface.PANEL));
  }

  @Override
  public void draw(Canvas c) {
    bounds.set(getBounds());
    int save = c.save();
    if (alpha != 255 || filter != null) {
      p.setAlpha(alpha);
      p.setColorFilter(filter);
      c.saveLayer(bounds, p);
    }
    ThemeArt.of(host).surface(c, ThemeArt.Surface.PANEL, bounds);
    c.restoreToCount(save);
  }

  @Override
  public void setAlpha(int value) {
    alpha = value;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter value) {
    filter = value;
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
