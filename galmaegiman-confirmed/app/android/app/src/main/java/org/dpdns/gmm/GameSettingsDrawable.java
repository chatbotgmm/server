package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 설정도 다른 화면과 같은 기록부 재질을 사용한다. */
final class GameSettingsDrawable extends Drawable {
  final MainActivity h;
  final Paint p = new Paint(3);
  final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private int alpha = 255;
  private ColorFilter filter;
  private final Drawable background;

  GameSettingsDrawable(MainActivity h) {
    this.h = h;
    background = ThemeArt.of(h).background("backgrounds/bg-settings-harbor-office-v1.png");
    background.setCallback(new Drawable.Callback() {
      @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
      @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
      @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
    });
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
    background.setBounds(getBounds());
    background.draw(c);
    border.setColor(JournalTheme.BRASS);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(h.dp(2));
    bounds.inset(h.dp(1), h.dp(1));
    c.drawRect(bounds, border);
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
