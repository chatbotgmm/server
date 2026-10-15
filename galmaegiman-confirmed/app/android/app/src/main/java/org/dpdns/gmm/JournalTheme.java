package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.*;

/** C 「선장의 모험일지」: 고정된 밝은 양피지와 잉크, 목재·황동 항해 장식. */
final class JournalTheme {
  static final int PAPER = 0xfff5efdc;
  static final int INK = 0xff292d2a;
  static final int WOOD = 0xff34261f;
  // 이전 호출부 이름을 유지하되 더 이상 봉제 가죽을 표현하지 않는다.
  static final int LEATHER = WOOD;
  static final int BRASS = 0xffb68b45;
  static final int TEAL = 0xff245b61;
  static final int MUTED = 0xff626c67;
  static final int WHITE = 0xffffffff;
  static final int LINE = 0xffd1c3a3;
  static final int SELECTED = 0xffe5ece5;
  static final int DISABLED = 0xfff0ecdf;
  static final int PRIMARY = TEAL;
  static final int PRIMARY_HIGHLIGHT = 0xff2d686d;
  static final int POSITIVE_INFO = 0xff3e7865;
  static final int NEGATIVE_INFO = 0xffa44136;
  private static final ColorStateList CONTROL_TEXT =
      new ColorStateList(
          new int[][] {
            new int[] {-android.R.attr.state_enabled},
            new int[] {android.R.attr.state_selected},
            new int[] {android.R.attr.state_checked},
            new int[] {android.R.attr.state_activated},
            new int[0]
          },
          new int[] {MUTED, TEAL, TEAL, TEAL, INK});
  private static final ColorStateList PRIMARY_TEXT =
      new ColorStateList(
          new int[][] {
            new int[] {-android.R.attr.state_enabled},
            new int[] {android.R.attr.state_selected},
            new int[] {android.R.attr.state_checked},
            new int[] {android.R.attr.state_activated},
            new int[0]
          },
          new int[] {MUTED, TEAL, TEAL, TEAL, WHITE});
  private static final ThreadLocal<Brushes> BRUSHES =
      new ThreadLocal<Brushes>() {
        @Override
        protected Brushes initialValue() {
          return new Brushes();
        }
      };

  private JournalTheme() {}

  static ColorStateList controlText() {
    return CONTROL_TEXT;
  }

  static ColorStateList controlText(int fill) {
    return fill == PRIMARY ? PRIMARY_TEXT : CONTROL_TEXT;
  }

  private static final class Brushes {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF outer = new RectF(),
        inner = new RectF(),
        seam = new RectF(),
        shadow = new RectF(),
        clip = new RectF();
    final Path contour = new Path(), ornament = new Path();
    final Matrix matrix = new Matrix();
    final LinearGradient paper =
        new LinearGradient(0, 0, 0, 1, 0xfffcf8eb, PAPER, Shader.TileMode.CLAMP);
    final LinearGradient primary =
        new LinearGradient(0, 0, 0, 1, PRIMARY_HIGHLIGHT, 0xff194b50, Shader.TileMode.CLAMP);
    final LinearGradient secondary =
        new LinearGradient(0, 0, 0, 1, 0xfffcf8e9, PAPER, Shader.TileMode.CLAMP);
  }

  /** clipped는 큰 기록판의 황동 모서리를 표시한다. 입력 bounds는 변경하지 않는다. */
  static void drawPanel(Canvas canvas, RectF bounds, float density, int fill, boolean clipped) {
    drawRecord(canvas, bounds, Math.max(.5f, density), fill, WOOD, 4.5f, 2, clipped);
  }

  /** 작은 정보 카드는 얇은 잉크선과 종이 가장자리로 버튼과 구별한다. */
  static void drawPaper(Canvas canvas, RectF bounds, float density, int edge) {
    drawRecord(canvas, bounds, Math.max(.5f, density), PAPER, neutralEdge(edge), 1.5f, .8f, false);
  }

  static void drawPaper(Canvas canvas, RectF bounds, float density, int edge, int fill) {
    drawRecord(canvas, bounds, Math.max(.5f, density), fill, neutralEdge(edge), 1.5f, .8f, false);
  }

  private static void surface(Brushes b, RectF bounds, int fill, boolean button) {
    if (fill == PAPER || fill == PRIMARY) {
      LinearGradient gradient = fill == PRIMARY ? b.primary : button ? b.secondary : b.paper;
      b.matrix.setScale(1, Math.max(1, bounds.height()));
      b.matrix.postTranslate(0, bounds.top);
      gradient.setLocalMatrix(b.matrix);
      b.paint.setColor(Color.WHITE);
      b.paint.setShader(gradient);
    } else b.paint.setColor(fill);
  }

  private static int neutralEdge(int color) {
    return color == 0xffd9b98a || color == 0xffc27c3d || color == 0xffb8823f || color == 0xffa8672e
        ? LEATHER
        : color;
  }

  private static void drawRecord(
      Canvas canvas,
      RectF bounds,
      float density,
      int fill,
      int edge,
      float rim,
      float depth,
      boolean clipped) {
    if (bounds.isEmpty()) return;
    Brushes b = BRUSHES.get();
    Paint p = b.paint;
    p.reset();
    p.setAntiAlias(true);
    b.outer.set(bounds);
    b.outer.inset(density, density);
    b.outer.bottom -= depth * density;
    if (b.outer.isEmpty()) return;
    float radius = Math.min(6 * density, b.outer.height() * .16f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, depth * density);
    p.setColor(0x6034261f);
    canvas.drawRoundRect(b.shadow, radius, radius, p);
    p.setColor(WOOD);
    canvas.drawRoundRect(b.outer, radius, radius, p);
    if (rim >= 3) {
      b.seam.set(b.outer);
      b.seam.inset(2.1f * density, 2.1f * density);
      // 1dp 선이 정수 경계 양쪽 픽셀에 나뉘어 사라지지 않도록 실제 픽셀 중심에 맞춘다.
      // density가 커져도 두꺼워지는 선의 중심만 정렬하며 전체 프레임 bounds는 바꾸지 않는다.
      b.seam.set(
          pixelCenter(b.seam.left),
          pixelCenter(b.seam.top),
          pixelCenter(b.seam.right),
          pixelCenter(b.seam.bottom));
      p.setStyle(Paint.Style.STROKE);
      p.setColor(BRASS);
      p.setStrokeWidth(1.1f * density);
      canvas.drawRoundRect(b.seam, Math.max(1, radius - density), Math.max(1, radius - density), p);
      p.setStyle(Paint.Style.FILL);
    }
    b.inner.set(b.outer);
    b.inner.inset(rim * density, rim * density);
    if (!b.inner.isEmpty()) {
      surface(b, b.inner, fill, false);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setShader(null);
      if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, clipped);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(.7f * density);
      p.setColor(edge == WOOD ? 0xffb79e72 : edge);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setStyle(Paint.Style.FILL);
    }
    if (clipped && b.outer.width() > 48 * density) {
      p.setColor(BRASS);
      for (int corner = 0; corner < 4; corner++) {
        float x = corner % 2 == 0 ? b.outer.left + 4 * density : b.outer.right - 4 * density;
        float y = corner < 2 ? b.outer.top + 4 * density : b.outer.bottom - 4 * density;
        canvas.drawCircle(x, y, .9f * density, p);
      }
    }
    p.setStyle(Paint.Style.FILL);
    p.setPathEffect(null);
  }

  private static float pixelCenter(float value) {
    return (float) Math.floor(value) + .5f;
  }

  /** 내용 면은 비워 두고 7dp 가장자리에만 종이 섬유·항해 지도 잉크를 새긴다. */
  private static void parchmentEdge(
      Canvas canvas, Brushes b, RectF bounds, float density, boolean mapCorners) {
    Paint p = b.paint;
    int save = canvas.save();
    b.contour.reset();
    b.contour.addRoundRect(bounds, 3 * density, 3 * density, Path.Direction.CW);
    canvas.clipPath(b.contour);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0x1ab68b45);
    p.setStrokeWidth(3 * density);
    canvas.drawRoundRect(bounds, 3 * density, 3 * density, p);
    b.clip.set(bounds);
    b.clip.inset(3.2f * density, 3.2f * density);
    p.setColor(0x1f8f7448);
    p.setStrokeWidth(.45f * density);
    canvas.drawRoundRect(b.clip, 2 * density, 2 * density, p);
    // 일정한 좌표식만 사용한다. 다시 그려도 섬유가 흔들리거나 난수를 만들지 않는다.
    p.setColor(0x326c5633);
    p.setStrokeWidth(.5f * density);
    float step = 19 * density;
    int index = 0;
    for (float x = bounds.left + 11 * density; x < bounds.right - 9 * density; x += step) {
      float offset = (index++ * 7 % 5) * .55f * density;
      canvas.drawLine(
          x, bounds.top + density, x + 2 * density, bounds.top + 1.8f * density + offset, p);
      canvas.drawLine(
          x + 4 * density,
          bounds.bottom - density,
          x + 5.5f * density,
          bounds.bottom - 2.5f * density - offset,
          p);
    }
    for (float y = bounds.top + 13 * density; y < bounds.bottom - 10 * density; y += 23 * density) {
      canvas.drawLine(bounds.left + density, y, bounds.left + 2.5f * density, y + density, p);
      canvas.drawLine(
          bounds.right - density, y + 3 * density, bounds.right - 2 * density, y + density, p);
    }
    if (mapCorners && bounds.width() > 96 * density && bounds.height() > 56 * density) {
      // 지도 나침반의 방위를 연상시키는 작은 꺾임. 본문 안으로 번지지 않는다.
      p.setColor(0x8fb68b45);
      p.setStrokeWidth(.65f * density);
      for (int corner = 0; corner < 4; corner++) {
        float x = corner % 2 == 0 ? bounds.left + 5 * density : bounds.right - 5 * density;
        float y = corner < 2 ? bounds.top + 5 * density : bounds.bottom - 5 * density;
        float dx = corner % 2 == 0 ? density : -density;
        float dy = corner < 2 ? density : -density;
        b.ornament.reset();
        b.ornament.moveTo(x + 10 * dx, y);
        b.ornament.lineTo(x + 3 * dx, y);
        b.ornament.lineTo(x + 3 * dx, y + 3 * dy);
        b.ornament.lineTo(x, y + 3 * dy);
        b.ornament.lineTo(x, y + 10 * dy);
        canvas.drawPath(b.ornament, p);
        canvas.drawCircle(x + 3 * dx, y + 3 * dy, 1.2f * density, p);
      }
    }
    canvas.restoreToCount(save);
    p.setStyle(Paint.Style.FILL);
  }

  /** 버튼은 정보 카드보다 선명한 면과 짧은 높이 차이·황동 리벳으로 구별한다. */
  static void drawButton(
      Canvas canvas, RectF bounds, int fill, float radius, float depth, Paint paint) {
    if (bounds.isEmpty()) return;
    int save = canvas.save();
    if (paint.getAlpha() != 255 || paint.getColorFilter() != null) canvas.saveLayer(bounds, paint);
    float density = Math.max(.5f, radius / 16f);
    Brushes b = BRUSHES.get();
    Paint p = b.paint;
    p.reset();
    p.setAntiAlias(true);
    b.outer.set(bounds);
    b.outer.inset(.7f * density, .7f * density);
    float corner = Math.min(6 * density, b.outer.height() * .16f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, Math.min(depth, 2.5f * density));
    p.setColor(0x9934261f);
    canvas.drawRoundRect(b.shadow, corner, corner, p);
    surface(b, b.outer, fill, true);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    p.setShader(null);
    if (fill == PAPER) parchmentEdge(canvas, b, b.outer, density, false);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f * density);
    p.setColor(fill == PRIMARY ? BRASS : fill == DISABLED ? 0xffb9b1a0 : 0xff917545);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    b.inner.set(b.outer);
    b.inner.inset(3 * density, 3 * density);
    p.setStrokeWidth(.7f * density);
    p.setColor(fill == PRIMARY ? 0xff72928b : 0xfffff9e7);
    canvas.drawRoundRect(
        b.inner, Math.max(1, corner - 2 * density), Math.max(1, corner - 2 * density), p);
    p.setStyle(Paint.Style.FILL);
    if (fill != DISABLED) {
      p.setColor(BRASS);
      float inset = Math.min(9 * density, b.outer.width() * .08f);
      canvas.drawCircle(b.outer.left + inset, b.outer.centerY(), 1.4f * density, p);
      canvas.drawCircle(b.outer.right - inset, b.outer.centerY(), 1.4f * density, p);
    }
    canvas.restoreToCount(save);
  }
}
