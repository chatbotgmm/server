package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 작은 기록 카드는 밝은 양피지와 얇은 지도 잉크선으로 구분한다. */
final class GamePaperDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  final int edge;
  private final RectF bounds = new RectF(), selectedBounds = new RectF();
  private final Paint selection = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean selected;
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
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean nextSelected = false;
    for (int state : states)
      if (state == android.R.attr.state_selected
          || state == android.R.attr.state_checked
          || state == android.R.attr.state_activated) nextSelected = true;
    if (nextSelected == selected) return false;
    selected = nextSelected;
    invalidateSelf();
    return true;
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
    float density = h.getResources().getDisplayMetrics().density;
    JournalTheme.drawPaper(
        c,
        bounds,
        density,
        selected ? JournalTheme.TEAL : edge,
        selected ? JournalTheme.SELECTED : JournalTheme.PAPER);
    if (selected) {
      selectedBounds.set(bounds);
      selectedBounds.inset(3.5f * density, 3.5f * density);
      if (!selectedBounds.isEmpty()) {
        selection.setStyle(Paint.Style.STROKE);
        selection.setColor(JournalTheme.TEAL);
        selection.setStrokeWidth(.8f * density);
        c.drawRoundRect(selectedBounds, 2 * density, 2 * density, selection);
      }
    }
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
