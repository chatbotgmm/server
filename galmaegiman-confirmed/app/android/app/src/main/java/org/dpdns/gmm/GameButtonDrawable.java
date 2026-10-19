package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 모험일지의 종이·황동 면과 눌림·선택 상태를 함께 표시한다. */
final class GameButtonDrawable extends Drawable {
  static final int PAPER = JournalTheme.PAPER, PRIMARY = JournalTheme.PRIMARY;
  final MainActivity host;
  final int color;
  final Paint p = new Paint(3);
  final Paint opacity = new Paint();
  final GamePressState state = new GamePressState();
  private final RectF bounds = new RectF(), layerBounds = new RectF();
  private boolean selected;
  private ColorFilter filter;

  GameButtonDrawable(MainActivity h, int c) {
    host = h;
    color = c == 0xfffffaf0 ? PAPER : c == 0xffdd9a58 || c == 0xffe0c296 ? PRIMARY : c;
    ThemeArt.of(h).image(ThemeArt.file(ThemeArt.Surface.PAPER));
    ThemeArt.of(h).image(ThemeArt.file(ThemeArt.Surface.NAVY));
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean nextSelected = false;
    for (int value : states)
      if (value == android.R.attr.state_selected
          || value == android.R.attr.state_checked
          || value == android.R.attr.state_activated) nextSelected = true;
    boolean changed = state.update(states, true) | (nextSelected != selected);
    selected = nextSelected;
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void draw(Canvas c) {
    RectF r = bounds;
    r.set(getBounds());
    r.bottom -= host.dp(3);
    int outer = c.save();
    if (filter != null) {
      opacity.setAlpha(255);
      opacity.setColorFilter(filter);
      layerBounds.set(getBounds());
      c.saveLayer(layerBounds, opacity);
    }
    int save;
    if (state.enabled) save = state.begin(c, getBounds(), host.getResources().getDisplayMetrics().density);
    else {
      opacity.setAlpha(state.alpha); opacity.setColorFilter(null);
      layerBounds.set(getBounds()); save = c.saveLayer(layerBounds, opacity);
    }
    if (!state.enabled) {
      // 비활성 글자는 MUTED다. 그림 배경이 비치지 않는 기존 읽기 면을 유지한다.
      drawSurface(c, r, JournalTheme.DISABLED, host.dp(16), host.dp(2), 0, p);
    } else {
      // Every actionable native button rests on parchment. Navy is transient feedback only.
      ThemeArt.of(host).surface(c,
          state.pressed ? ThemeArt.Surface.NAVY : ThemeArt.Surface.PAPER, r);
    }
    boolean focused = false;
    for (int value : getState()) if (value == android.R.attr.state_focused) focused = true;
    if ((selected || focused) && state.enabled) {
      r.inset(host.dp(3), host.dp(3));
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(host.dp(1.8f));
      p.setColor(focused ? JournalTheme.FOCUS : JournalTheme.BRASS);
      c.drawRoundRect(r, host.dp(3), host.dp(3), p);
      p.setStyle(Paint.Style.FILL);
      if (selected && !state.pressed) {
        p.setColor(0x14628c90);
        c.drawRoundRect(r, host.dp(3), host.dp(3), p);
      }
    }
    state.finish(c, save);
    c.restoreToCount(outer);
  }

  static void drawSurface(
      Canvas c, RectF r, int color, float radius, float border, float depth, Paint p) {
    int fill =
        color == 0xfffffaf0 ? PAPER : color == 0xffdd9a58 || color == 0xffe0c296 ? PRIMARY : color;
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
    filter = value;
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
