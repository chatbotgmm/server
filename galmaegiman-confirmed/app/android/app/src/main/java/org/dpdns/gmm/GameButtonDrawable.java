package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 도감 가죽 표지에 밝은 종이·황동 면과 눌림·선택 상태를 함께 표시한다. */
final class GameButtonDrawable extends Drawable {
  static final int PAPER = JournalTheme.PAPER, PRIMARY = JournalTheme.PRIMARY;
  final MainActivity host;
  final int color;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();
  private boolean selected;

  GameButtonDrawable(MainActivity h, int c) {
    host = h;
    color = c == 0xfffffaf0 ? PAPER : c == 0xffdd9a58 ? PRIMARY : c;
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean nextSelected = false;
    for (int value : states)
      if (value == android.R.attr.state_selected || value == android.R.attr.state_checked)
        nextSelected = true;
    boolean changed = state.update(states) | (nextSelected != selected);
    selected = nextSelected;
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void draw(Canvas c) {
    RectF r = new RectF(getBounds());
    r.bottom -= host.dp(3);
    int save = state.begin(c, getBounds(), host.getResources().getDisplayMetrics().density);
    drawSurface(c, r, color, host.dp(16), host.dp(2), state.pressed ? host.dp(1) : host.dp(3), p);
    if (selected) {
      r.inset(host.dp(6), host.dp(6));
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(host.dp(2));
      p.setColor(JournalTheme.TEAL);
      c.drawRoundRect(r, host.dp(4), host.dp(4), p);
      p.setStyle(Paint.Style.FILL);
    }
    state.finish(c, save);
  }

  static void drawSurface(
      Canvas c, RectF r, int color, float radius, float border, float depth, Paint p) {
    int fill = color == 0xfffffaf0 ? PAPER : color == 0xffdd9a58 ? PRIMARY : color;
    JournalTheme.drawButton(c, r, fill, radius, depth, p);
  }

  static int lighten(int color, float fraction) {
    return Color.rgb(
        (int) (Color.red(color) + (255 - Color.red(color)) * fraction),
        (int) (Color.green(color) + (255 - Color.green(color)) * fraction),
        (int) (Color.blue(color) + (255 - Color.blue(color)) * fraction));
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
