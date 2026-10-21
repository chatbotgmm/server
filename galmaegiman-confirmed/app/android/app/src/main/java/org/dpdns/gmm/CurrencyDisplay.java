package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.Button;

/** 재화는 메뉴 팻말과 구별되는 낮은 남색 정보 홈으로 표시한다. 터치 영역은 유지한다. */
final class CurrencyDisplay extends Drawable {
  static final int NAVY = 0xff172d3b, PRESSED = 0xff203d4c;
  static final int TEXT = 0xfff5efdc, MUTED = 0xffc4c9c7;
  private final float density;
  private int alpha = 255;
  private ColorFilter filter;
  private final Paint layer = new Paint();
  private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF face = new RectF();

  CurrencyDisplay(float density) { this.density = density; }

  static void bind(MainActivity host, Button button, String kind) {
    button.setTag("currency-" + kind);
    button.setBackground(new CurrencyDisplay(host.getResources().getDisplayMetrics().density));
    button.setTextColor(new ColorStateList(new int[][] {
        new int[] {-android.R.attr.state_enabled}, new int[0]}, new int[] {MUTED, TEXT}));
  }

  static int ink(boolean enabled) { return enabled ? TEXT : MUTED; }

  static void draw(Canvas canvas, RectF bounds, float density,
      boolean enabled, boolean pressed, boolean focused) {
    draw(canvas, bounds, density, enabled, pressed, focused,
        new Paint(Paint.ANTI_ALIAS_FLAG), new RectF());
  }

  private static void draw(Canvas canvas, RectF bounds, float density,
      boolean enabled, boolean pressed, boolean focused, Paint paint, RectF face) {
    face.set(bounds);
    face.inset(density, Math.min(6 * density, bounds.height() / 8));
    float radius = 6 * density;
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(enabled && pressed ? PRESSED : NAVY);
    canvas.drawRoundRect(face, radius, radius, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(density);
    paint.setColor(enabled ? JournalTheme.BRASS : 0xff897b61);
    face.inset(density / 2, density / 2);
    canvas.drawRoundRect(face, radius, radius, paint);
    // 위쪽의 얕은 음영만으로 홈을 표현한다. 큰 프레임·리벳·밑받침은 쓰지 않는다.
    paint.setColor(0x66000000);
    canvas.drawLine(face.left + radius, face.top + 2 * density,
        face.right - radius, face.top + 2 * density, paint);
    if (enabled && focused) {
      paint.setColor(TEXT);
      face.inset(2 * density, 2 * density);
      canvas.drawRoundRect(face, 4 * density, 4 * density, paint);
    }
    paint.setStyle(Paint.Style.FILL);
  }

  @Override public boolean isStateful() { return true; }
  @Override protected boolean onStateChange(int[] states) { invalidateSelf(); return true; }
  @Override public void draw(Canvas canvas) {
    boolean enabled = false, pressed = false, focused = false;
    for (int state : getState()) {
      if (state == android.R.attr.state_enabled) enabled = true;
      if (state == android.R.attr.state_pressed) pressed = true;
      if (state == android.R.attr.state_focused) focused = true;
    }
    layer.setAlpha(alpha); layer.setColorFilter(filter);
    int save = canvas.saveLayer(new RectF(getBounds()), layer);
    draw(canvas, new RectF(getBounds()), density, enabled, pressed, focused, brush, face);
    canvas.restoreToCount(save);
  }
  @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
