package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 기술도 기록 카드로 통일하되 속성 띠와 합동기의 황동 표시는 유지한다. */
final class GameSkillDrawable extends Drawable {
  final MainActivity h;
  final int accent;
  final boolean combo;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();
  private final Paint opacity = new Paint();
  private final Path clip = new Path();

  GameSkillDrawable(MainActivity h, int accent, boolean combo) {
    this.h = h;
    this.accent = accent;
    this.combo = combo;
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean changed = state.update(states);
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void draw(Canvas canvas) {
    int save;
    if (state.enabled)
      save = state.begin(canvas, getBounds(), h.getResources().getDisplayMetrics().density);
    else {
      opacity.setAlpha(state.alpha);
      save = canvas.saveLayer(new RectF(getBounds()), opacity);
    }
    if (p.getColorFilter() != null) canvas.saveLayer(new RectF(getBounds()), p);
    RectF box = new RectF(getBounds());
    JournalTheme.drawPaper(
        canvas,
        box,
        h.getResources().getDisplayMetrics().density,
        combo ? JournalTheme.BRASS : accent,
        state.enabled ? JournalTheme.PAPER : JournalTheme.DISABLED);
    box.inset(h.dp(5), h.dp(5));
    box.bottom -= h.dp(1);
    int strip = canvas.save();
    clip.reset();
    clip.addRoundRect(box, h.dp(4), h.dp(4), Path.Direction.CW);
    canvas.clipPath(clip);
    p.setStyle(Paint.Style.FILL);
    p.setColor(combo ? JournalTheme.BRASS : accent);
    canvas.drawRect(box.left, box.top, box.right, box.top + h.dp(3), p);
    canvas.restoreToCount(strip);
    state.finish(canvas, save);
  }

  @Override
  public void setAlpha(int value) {
    state.alpha = value;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter value) {
    p.setColorFilter(value);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
