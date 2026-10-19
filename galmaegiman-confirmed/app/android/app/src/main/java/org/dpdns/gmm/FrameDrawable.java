package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 대화·전투 기록·상태창도 늘인 그림 대신 같은 도감 프레임을 직접 그린다. */
final class FrameDrawable extends Drawable {
  private final MainActivity host;
  private final String name;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  FrameDrawable(MainActivity host, String name) {
    this.host = host;
    this.name = name;
  }

  @Override
  public void draw(Canvas canvas) {
    drawFrame(canvas, host, name, new RectF(getBounds()), paint);
  }

  static void drawFrame(Canvas c, MainActivity host, String name, RectF dst, Paint p) {
    if (dst.isEmpty()) return;
    int save = c.save();
    if (p.getAlpha() != 255 || p.getColorFilter() != null) c.saveLayer(dst, p);
    JournalTheme.drawPanel(
        c, dst, host.getResources().getDisplayMetrics().density, JournalTheme.PAPER, true);
    c.restoreToCount(save);
  }

  @Override
  public void setAlpha(int value) {
    paint.setAlpha(value);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter value) {
    paint.setColorFilter(value);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
