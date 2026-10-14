package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** GAMEUI의 나무 테두리·그라데이션·바닥 그림자와 눌림 상태. */
final class GameButtonDrawable extends Drawable {
  static final int PAPER = 0xfffffaf0, PRIMARY = 0xffdd9a58;
  final MainActivity host;
  final int color;
  final Paint p = new Paint(3);
  boolean pressed, enabled = true;
  int alpha = 255;

  GameButtonDrawable(MainActivity h, int c) {
    host = h;
    color = c;
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  protected boolean onStateChange(int[] states) {
    boolean down = false, active = false;
    for (int state : states) {
      if (state == android.R.attr.state_pressed) down = true;
      if (state == android.R.attr.state_enabled) active = true;
    }
    boolean changed = down != pressed || active != enabled;
    pressed = down;
    enabled = active;
    if (changed) invalidateSelf();
    return changed;
  }

  @Override
  public void draw(Canvas c) {
    RectF r = new RectF(getBounds());
    r.bottom -= host.dp(3);
    if (pressed) r.offset(0, host.dp(2));
    c.saveLayerAlpha(new RectF(getBounds()), enabled ? alpha : alpha * 2 / 5);
    drawSurface(c, r, color, host.dp(16), host.dp(2), pressed ? host.dp(1) : host.dp(3), p);
    c.restore();
  }

  static void drawSurface(
      Canvas c, RectF r, int color, float radius, float border, float depth, Paint p) {
    int edge = color == 0xfffee500 ? 0xffa89600 : color == PRIMARY ? 0xff7a4e2a : 0xffa8672e;
    p.setStyle(Paint.Style.FILL);
    p.setShader(null);
    p.setColor(edge);
    RectF shadow = new RectF(r);
    shadow.offset(0, depth);
    c.drawRoundRect(shadow, radius, radius, p);
    int top = color == PRIMARY ? 0xfff0bd85 : color == PAPER ? PAPER : lighten(color, .20f);
    p.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, color, Shader.TileMode.CLAMP));
    c.drawRoundRect(r, radius, radius, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(border);
    p.setColor(edge);
    RectF rim = new RectF(r);
    rim.inset(border / 2, border / 2);
    c.drawRoundRect(rim, radius, radius, p);
    if (color != PAPER) {
      rim.inset(border, border);
      p.setStrokeWidth(Math.max(1, border / 2));
      p.setColor(0x80fff8de);
      c.drawRoundRect(rim, Math.max(1, radius - border), Math.max(1, radius - border), p);
    }
    p.setStyle(Paint.Style.FILL);
    p.setAlpha(255);
  }

  static int lighten(int c, float a) {
    return Color.rgb(
        (int) (Color.red(c) + (255 - Color.red(c)) * a),
        (int) (Color.green(c) + (255 - Color.green(c)) * a),
        (int) (Color.blue(c) + (255 - Color.blue(c)) * a));
  }

  @Override
  public void setAlpha(int a) {
    alpha = a;
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
