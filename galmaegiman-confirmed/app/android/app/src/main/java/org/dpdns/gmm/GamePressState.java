package org.dpdns.gmm;

import android.graphics.*;

/** 이미지 버튼도 공통 버튼과 같은 눌림·비활성 표현을 사용한다. */
final class GamePressState {
  boolean pressed, enabled = true;
  int alpha = 255;
  private final Paint layer = new Paint();

  boolean update(int[] states) {
    boolean down = false, active = false;
    for (int state : states) {
      if (state == android.R.attr.state_pressed) down = true;
      if (state == android.R.attr.state_enabled) active = true;
    }
    boolean changed = down != pressed || active != enabled;
    pressed = down;
    enabled = active;
    return changed;
  }

  int begin(Canvas canvas, Rect bounds, float density) {
    layer.setAlpha(enabled ? alpha : alpha * 2 / 5);
    if (enabled) layer.setColorFilter(null);
    else {
      ColorMatrix gray = new ColorMatrix();
      gray.setSaturation(.15f);
      layer.setColorFilter(new ColorMatrixColorFilter(gray));
    }
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
