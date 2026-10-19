package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 설정도 다른 화면과 같은 기록부 재질을 사용한다. */
final class GameSettingsDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  private final RectF bounds = new RectF();
  private int alpha = 255;
  private ColorFilter filter;

  GameSettingsDrawable(MainActivity h) {
    this.h = h;
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
    JournalTheme.drawPanel(
        c, bounds, h.getResources().getDisplayMetrics().density, JournalTheme.PAPER, true);
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
