package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 이전 버튼 호출도 같은 도감 재질과 실제 글꼴을 사용한다. */
final class ArtButton extends Drawable {
  final MainActivity host;
  final String kind;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();

  ArtButton(MainActivity h, String k) {
    host = h;
    kind = k;
  }

  @Override
  public void draw(Canvas c) {
    int save = state.begin(c, getBounds(), host.getResources().getDisplayMetrics().density);
    RectF r = new RectF(getBounds());
    GameButtonDrawable surface =
        new GameButtonDrawable(
            host, kind.equals("next") ? GameButtonDrawable.PRIMARY : GameButtonDrawable.PAPER);
    surface.setBounds(getBounds());
    surface.draw(c);
    p.setColor(JournalTheme.INK);
    p.setTypeface(host.font);
    p.setTextSize(Math.min(host.dp(18), r.height() * .38f));
    p.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = p.getFontMetrics();
    c.drawText(
        kind.equals("next") ? "다음" : "건너뛰기",
        r.centerX(),
        r.centerY() - (metrics.ascent + metrics.descent) / 2,
        p);
    state.finish(c, save);
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
  public void setAlpha(int a) {
    state.alpha = a;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter f) {
    p.setColorFilter(f);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
