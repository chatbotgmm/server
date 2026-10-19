package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Native SeekBar's level supplies progress; the unfilled navy rail is visible even at zero. */
final class BrassSliderTrack extends Drawable {
  final float density;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final RectF rail = new RectF(), progress = new RectF();
  int alpha = 255;
  ColorFilter filter;
  BrassSliderTrack(MainActivity host) { density = host.getResources().getDisplayMetrics().density; }
  @Override protected boolean onLevelChange(int level) { invalidateSelf(); return true; }
  @Override public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    float half = 2.5f * density;
    rail.set(bounds.left, bounds.exactCenterY() - half, bounds.right, bounds.exactCenterY() + half);
    paint.setColorFilter(filter); paint.setAlpha(alpha); paint.setColor(0xff637a85); paint.setAlpha(alpha);
    canvas.drawRoundRect(rail, half, half, paint);
    if (getLevel() > 0) {
      progress.set(rail); progress.right = progress.left + progress.width() * Math.min(10000,getLevel()) / 10000f;
      paint.setColor(JournalTheme.NAVY); paint.setAlpha(alpha);
      canvas.drawRoundRect(progress, half, half, paint);
    }
  }
  @Override public int getIntrinsicHeight() { return Math.max(1,Math.round(5*density)); }
  @Override public void setAlpha(int value) { alpha=Math.max(0,Math.min(255,value)); invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter value) { filter=value; invalidateSelf(); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
