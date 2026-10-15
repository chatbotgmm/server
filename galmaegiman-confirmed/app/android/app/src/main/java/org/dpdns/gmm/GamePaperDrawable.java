package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 작은 기록 카드는 밝은 종이와 얇은 봉제 가죽으로 구분한다. */
final class GamePaperDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  final int edge;
  private int alpha = 255;
  private ColorFilter filter;

  GamePaperDrawable(MainActivity h) {
    this(h, JournalTheme.LEATHER);
  }

  GamePaperDrawable(MainActivity h, int edge) {
    this.h = h;
    this.edge = edge;
  }

  @Override
  public void draw(Canvas c) {
    int save = c.save();
    if (alpha != 255 || filter != null) {
      p.setAlpha(alpha);
      p.setColorFilter(filter);
      c.saveLayer(new RectF(getBounds()), p);
    }
    JournalTheme.drawPaper(
        c, new RectF(getBounds()), h.getResources().getDisplayMetrics().density, edge);
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
