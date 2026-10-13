package org.dpdns.gmm;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** 모서리는 같은 배율로 유지하고 프레임의 중앙과 직선 테두리만 확장한다. */
final class FrameDrawable extends Drawable {
  private final MainActivity host;
  private final String name;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

  FrameDrawable(MainActivity host, String name) {
    this.host = host;
    this.name = name;
  }

  @Override
  public void draw(Canvas canvas) {
    drawFrame(canvas, host, name, new RectF(getBounds()), paint);
  }

  static void drawFrame(Canvas c, MainActivity host, String name, RectF dst, Paint p) {
    boolean coral = !name.equals("dialog.webp");
    Bitmap b = host.art.asset(coral ? "coral-frame.png" : name);
    if (b == null || dst.isEmpty()) return;
    Rect src =
        (name.equals("dialog.webp") || coral)
            ? new Rect(0, 0, b.getWidth(), b.getHeight())
            : host.art.alpha(b, name);
    int left, right, top, bottom;
    float edge;
    if (coral) {
      left = 320;
      right = 400;
      top = 260;
      bottom = 360;
      edge = host.dp(name.equals("status.webp") ? 42 : 32);
      Paint paper = new Paint(Paint.ANTI_ALIAS_FLAG);
      paper.setAlpha(p.getAlpha());
      paper.setShader(
          new LinearGradient(
              0, dst.top, 0, dst.bottom, 0xfffcf5e3, 0xfff2e1c6, Shader.TileMode.CLAMP));
      RectF inner = new RectF(dst);
      inner.inset(host.dp(8), host.dp(8));
      c.drawRoundRect(inner, host.dp(12), host.dp(12), paper);
    } else {
      left = right = top = bottom = 90;
      edge = host.dp(28);
    }
    float scale =
        Math.min(
            edge / Math.max(left, right),
            Math.min(dst.width() / (left + right), dst.height() / (top + bottom)));
    int[] sx = {src.left, src.left + left, src.right - right, src.right};
    int[] sy = {src.top, src.top + top, src.bottom - bottom, src.bottom};
    float[] dx = {dst.left, dst.left + left * scale, dst.right - right * scale, dst.right};
    float[] dy = {dst.top, dst.top + top * scale, dst.bottom - bottom * scale, dst.bottom};
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 3; x++) {
        if (dx[x + 1] > dx[x] && dy[y + 1] > dy[y])
          c.drawBitmap(
              b,
              new Rect(sx[x], sy[y], sx[x + 1], sy[y + 1]),
              new RectF(dx[x], dy[y], dx[x + 1], dy[y + 1]),
              p);
      }
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
