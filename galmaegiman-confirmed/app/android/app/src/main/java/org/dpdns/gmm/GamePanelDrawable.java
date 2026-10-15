package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 넓은 기록판은 봉제 가죽·종이·황동 클립으로 통일한다. */
final class GamePanelDrawable extends Drawable {
  final MainActivity host;
  final Paint p = new Paint(3);
  private int alpha = 255;
  private ColorFilter filter;

  GamePanelDrawable(MainActivity h) {
    host = h;
  }

  @Override
  public void draw(Canvas c) {
    int save = c.save();
    if (alpha != 255 || filter != null) {
      p.setAlpha(alpha);
      p.setColorFilter(filter);
      c.saveLayer(new RectF(getBounds()), p);
    }
    JournalTheme.drawPanel(
        c,
        new RectF(getBounds()),
        host.getResources().getDisplayMetrics().density,
        JournalTheme.PAPER,
        true);
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
