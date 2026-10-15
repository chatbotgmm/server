package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.*;

/** 항구 수비조장의 모험 도감: 가죽 가장자리와 밝은 기록지를 공통으로 그린다. */
final class JournalTheme {
  static final int PAPER = 0xfff7edda;
  static final int INK = 0xff302b25;
  static final int LEATHER = 0xff51372f;
  static final int BRASS = 0xffc49a59;
  static final int TEAL = 0xff28535b;
  static final int MUTED = 0xff6c6053;
  static final int WHITE = 0xfffffbf0;
  static final int LINE = 0xffd8c8ab;
  static final int SELECTED = 0xffe7eee7;
  static final int DISABLED = 0xffeae3d5;
  static final int PRIMARY = TEAL;
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
    float density = -1;
    DashPathEffect stitches;
    final Matrix matrix = new Matrix();
    final LinearGradient paper =
        new LinearGradient(0, 0, 0, 1, WHITE, PAPER, Shader.TileMode.CLAMP);
    final LinearGradient primary =
        new LinearGradient(0, 0, 0, 1, 0xff3f7079, 0xff21444c, Shader.TileMode.CLAMP);
    final LinearGradient secondary =
        new LinearGradient(0, 0, 0, 1, 0xfffffbf0, 0xffeee1ca, Shader.TileMode.CLAMP);

    void density(float value) {
      if (density == value) return;
      density = value;
      stitches = new DashPathEffect(new float[] {2 * value, 2.5f * value}, 0);
    }
  }

  /** clipped는 황동 리벳 장식을 표시한다. 입력 bounds는 변경하지 않는다. */
  static void drawPanel(Canvas canvas, RectF bounds, float density, int fill, boolean clipped) {
    drawRecord(canvas, bounds, Math.max(.5f, density), fill, LEATHER, 3, 2, clipped);
  }

  /** 작은 정보 카드는 버튼보다 얇은 가죽 테두리와 짧은 그림자로 구별한다. */
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
    b.density(density);
    Paint p = b.paint;
    p.reset();
    p.setAntiAlias(true);
    b.outer.set(bounds);
    b.outer.inset(density, density);
    b.outer.bottom -= depth * density;
    if (b.outer.isEmpty()) return;
    float radius = Math.min(9 * density, b.outer.height() * .18f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, depth * density);
    p.setColor(0x4051372f);
    canvas.drawRoundRect(b.shadow, radius, radius, p);
    p.setColor(LEATHER);
    canvas.drawRoundRect(b.outer, radius, radius, p);
    if (rim >= 3) {
      b.seam.set(b.outer);
      b.seam.inset(1.2f * density, 1.2f * density);
      p.setStyle(Paint.Style.STROKE);
      p.setColor(0xffaa8d6e);
      p.setStrokeWidth(.6f * density);
      p.setPathEffect(b.stitches);
      canvas.drawRoundRect(b.seam, Math.max(1, radius - density), Math.max(1, radius - density), p);
      p.setPathEffect(null);
      p.setStyle(Paint.Style.FILL);
    }
    b.inner.set(b.outer);
    b.inner.inset(rim * density, rim * density);
    if (!b.inner.isEmpty()) {
      surface(b, b.inner, fill, false);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setShader(null);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(.7f * density);
      p.setColor(edge == LEATHER ? 0xffd5c4a2 : edge);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setStyle(Paint.Style.FILL);
    }
    if (clipped && b.outer.width() > 48 * density) {
      p.setColor(BRASS);
      canvas.drawCircle(b.outer.left + 6 * density, b.outer.top + 6 * density, 1.2f * density, p);
      canvas.drawCircle(b.outer.right - 6 * density, b.outer.top + 6 * density, 1.2f * density, p);
    }
    p.setStyle(Paint.Style.FILL);
    p.setPathEffect(null);
  }

  /** 버튼은 정보 카드보다 선명한 면과 짧은 높이 차이·황동 리벳으로 구별한다. */
  static void drawButton(
      Canvas canvas, RectF bounds, int fill, float radius, float depth, Paint paint) {
    if (bounds.isEmpty()) return;
    int save = canvas.save();
    if (paint.getAlpha() != 255 || paint.getColorFilter() != null) canvas.saveLayer(bounds, paint);
    float density = Math.max(.5f, radius / 16f);
    Brushes b = BRUSHES.get();
    b.density(density);
    Paint p = b.paint;
    p.reset();
    p.setAntiAlias(true);
    b.outer.set(bounds);
    b.outer.inset(.7f * density, .7f * density);
    float corner = Math.min(8 * density, b.outer.height() * .18f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, Math.min(depth, 2.5f * density));
    p.setColor(0x6651372f);
    canvas.drawRoundRect(b.shadow, corner, corner, p);
    surface(b, b.outer, fill, true);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f * density);
    p.setColor(fill == PRIMARY ? BRASS : fill == DISABLED ? 0xffb9af9f : 0xff957048);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    b.inner.set(b.outer);
    b.inner.inset(3 * density, 3 * density);
    p.setStrokeWidth(.7f * density);
    p.setColor(fill == PRIMARY ? 0xff729392 : 0xfffff8de);
    canvas.drawRoundRect(
        b.inner, Math.max(1, corner - 2 * density), Math.max(1, corner - 2 * density), p);
    p.setStyle(Paint.Style.FILL);
    if (fill != DISABLED) {
      p.setColor(fill == PRIMARY ? BRASS : 0xffa17b44);
      float inset = Math.min(9 * density, b.outer.width() * .08f);
      canvas.drawCircle(b.outer.left + inset, b.outer.centerY(), 1.4f * density, p);
      canvas.drawCircle(b.outer.right - inset, b.outer.centerY(), 1.4f * density, p);
    }
    canvas.restoreToCount(save);
  }
}
