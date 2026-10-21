package org.dpdns.gmm;

import android.graphics.*;

/** 이미지 버튼도 공통 버튼과 같은 눌림·비활성 표현을 사용한다. */
final class GamePressState {
  boolean pressed, enabled = true;
  int alpha = 255;
  private final Paint layer = new Paint();
  private static final ColorMatrixColorFilter DISABLED_FILTER = disabledFilter();

  private static ColorMatrixColorFilter disabledFilter() {
    // 원본의 알파·윤곽·테두리는 유지하고 채도와 밝기만 조금 낮춘다.
    ColorMatrix tone = new ColorMatrix();
    tone.setSaturation(.65f);
    ColorMatrix brightness = new ColorMatrix();
    brightness.setScale(.92f, .92f, .92f, 1f);
    tone.postConcat(brightness);
    return new ColorMatrixColorFilter(tone);
  }

  boolean update(int[] states) { return update(states, false); }

  boolean update(int[] states, boolean allowHover) {
    boolean down = false, active = false;
    for (int state : states) {
      if (state == android.R.attr.state_pressed
          || (allowHover && state == android.R.attr.state_hovered)) down = true;
      if (state == android.R.attr.state_enabled) active = true;
    }
    down = down && active;
    boolean changed = down != pressed || active != enabled;
    pressed = down;
    enabled = active;
    return changed;
  }

  int begin(Canvas canvas, Rect bounds, float density) {
    layer.setAlpha(alpha);
    layer.setColorFilter(enabled ? null : DISABLED_FILTER);
    int save = canvas.saveLayer(new RectF(bounds), layer);
    if (enabled && pressed) {
      canvas.translate(0, 2 * density);
      canvas.scale(.985f, .985f, bounds.exactCenterX(), bounds.exactCenterY());
    }
    return save;
  }

  void finish(Canvas canvas, int save) {
    canvas.restoreToCount(save);
  }
}
