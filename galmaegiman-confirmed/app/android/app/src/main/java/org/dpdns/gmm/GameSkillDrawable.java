package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 원본 기술 카드의 크림 종이와 속성 색 띠. */
final class GameSkillDrawable extends Drawable {
  final MainActivity h;
  final int accent;
  final boolean combo;
  final Paint p = new Paint(3);
  final GamePressState state = new GamePressState();

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

  GameSkillDrawable(MainActivity h, int accent, boolean combo) {
    this.h = h;
    this.accent = accent;
    this.combo = combo;
  }

  public void draw(Canvas canvas) {
    int save = state.begin(canvas, getBounds(), h.getResources().getDisplayMetrics().density);
    RectF box = new RectF(getBounds());
    box.inset(h.dp(1), h.dp(1));
    box.bottom -= h.dp(4);
    p.setStyle(Paint.Style.FILL);
    p.setColor(0x887a4e2a);
    RectF shadow = new RectF(box);
    shadow.offset(0, h.dp(4));
    canvas.drawRoundRect(shadow, h.dp(12), h.dp(12), p);
    p.setAlpha(255);
    p.setShader(
        new LinearGradient(
            0,
            box.top,
            0,
            box.bottom,
            combo ? 0xfffff3bf : 0xfffffaf0,
            combo ? 0xffffe3a0 : 0xffffefd6,
            Shader.TileMode.CLAMP));
    canvas.drawRoundRect(box, h.dp(12), h.dp(12), p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(h.dp(2));
    p.setColor(accent);
    canvas.drawRoundRect(box, h.dp(12), h.dp(12), p);
    p.setStyle(Paint.Style.FILL);
    canvas.save();
    Path clip = new Path();
    clip.addRoundRect(box, h.dp(12), h.dp(12), Path.Direction.CW);
    canvas.clipPath(clip);
    canvas.drawRect(box.left, box.top, box.right, box.top + h.dp(6), p);
    canvas.restore();
    state.finish(canvas, save);
  }

  public void setAlpha(int value) {
    state.alpha = value;
    invalidateSelf();
  }

  public void setColorFilter(ColorFilter filter) {
    p.setColorFilter(filter);
    invalidateSelf();
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
