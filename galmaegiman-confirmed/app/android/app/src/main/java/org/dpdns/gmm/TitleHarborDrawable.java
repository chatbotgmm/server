package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 승인된 G의 불투명 황동 CTA와 계정 칩. 기존 눌림·비활성 동작을 사용한다. */
final class TitleHarborDrawable extends Drawable {
  static final int TEXT = 0xfffff4db, TOP = 0xff855f2d, BOTTOM = 0xff4c3623;
  final MainActivity host;
  final boolean primary;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final GamePressState state = new GamePressState();
  final RectF box = new RectF(), inner = new RectF();
  final Matrix matrix = new Matrix();
  final Path arrow = new Path();
  final LinearGradient gold = new LinearGradient(0, 0, 0, 1, TOP, BOTTOM, Shader.TileMode.CLAMP);

  TitleHarborDrawable(MainActivity host, boolean primary) {
    this.host = host;
    this.primary = primary;
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
    int save = state.begin(canvas, getBounds(), host.getResources().getDisplayMetrics().density);
    float density = host.getResources().getDisplayMetrics().density;
    box.set(getBounds());
    box.inset(density, density);
    box.bottom -= 4 * density;
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(null);
    paint.setColor(0xff31251e);
    inner.set(box);
    inner.offset(0, 4 * density);
    canvas.drawRoundRect(inner, 10 * density, 10 * density, paint);
    paint.setColor(primary ? 0xffc9a361 : 0xffb99b62);
    canvas.drawRoundRect(box, 10 * density, 10 * density, paint);
    inner.set(box);
    inner.inset(3 * density, 3 * density);
    if (primary) {
      matrix.setScale(1, inner.height());
      matrix.postTranslate(0, inner.top);
      gold.setLocalMatrix(matrix);
      paint.setShader(gold);
    } else paint.setColor(0xff1b303a);
    canvas.drawRoundRect(inner, 7 * density, 7 * density, paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(density);
    paint.setColor(0xffd8bc80);
    inner.inset(2 * density, 2 * density);
    canvas.drawRoundRect(inner, 5 * density, 5 * density, paint);
    paint.setStyle(Paint.Style.FILL);
    if (primary) {
      float x = box.left + 25 * density, y = box.centerY();
      arrow.rewind();
      arrow.moveTo(x - 4 * density, y - 8 * density);
      arrow.lineTo(x + 8 * density, y);
      arrow.lineTo(x - 4 * density, y + 8 * density);
      arrow.close();
      paint.setColor(TEXT);
      canvas.drawPath(arrow, paint);
    }
    state.finish(canvas, save);
  }

  @Override
  public void setAlpha(int alpha) {
    state.alpha = alpha;
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
