package org.dpdns.gmm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Display-only ground contact; the shader and paint are retained between frames. */
final class BattleContactShadow {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RadialGradient gradient = new RadialGradient(0, 0, 1,
      new int[] {0x771b2b32, 0x431b2b32, 0x001b2b32}, new float[] {0, .45f, 1},
      Shader.TileMode.CLAMP);

  void draw(Canvas canvas, RectF body, float density, int alpha) {
    if (body.isEmpty() || alpha <= 0) return;
    int saved = canvas.save();
    canvas.translate(body.centerX(), body.bottom - 2 * density);
    canvas.scale(body.width() * .47f, body.height() * .065f);
    paint.setShader(gradient);
    paint.setAlpha(Math.max(0, Math.min(255, alpha)));
    canvas.drawCircle(0, 0, 1, paint);
    canvas.restoreToCount(saved);
  }
}
