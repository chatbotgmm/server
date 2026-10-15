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
  static final int MUTED = 0xff6f6254;
  static final int PRIMARY = 0xffe0c296;
  private static final ColorStateList CONTROL_TEXT =
      new ColorStateList(
          new int[][] {
            new int[] {-android.R.attr.state_enabled},
            new int[] {android.R.attr.state_selected},
            new int[0]
          },
          new int[] {MUTED, TEAL, INK});
  private static final int LEATHER_LIGHT = 0xff79604b;
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

  private static final class Brushes {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF outer = new RectF(),
        inner = new RectF(),
        seam = new RectF(),
        shadow = new RectF(),
        clip = new RectF();
    float density = -1;
    DashPathEffect stitches;

    void density(float value) {
      if (density == value) return;
      density = value;
      stitches = new DashPathEffect(new float[] {2 * value, 2.5f * value}, 0);
    }
  }

  /** clipped는 윗변의 황동 종이 클립을 표시한다. 입력 bounds는 변경하지 않는다. */
  static void drawPanel(Canvas canvas, RectF bounds, float density, int fill, boolean clipped) {
    drawRecord(canvas, bounds, Math.max(.5f, density), fill, LEATHER, 5, 3, clipped);
  }

  /** 작은 정보 카드는 버튼보다 얇은 가죽 테두리와 짧은 그림자로 구별한다. */
  static void drawPaper(Canvas canvas, RectF bounds, float density, int edge) {
    drawRecord(canvas, bounds, Math.max(.5f, density), PAPER, neutralEdge(edge), 3.5f, 1, false);
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
    p.setColor(0x7751372f);
    canvas.drawRoundRect(b.shadow, radius, radius, p);
    p.setColor(LEATHER);
    canvas.drawRoundRect(b.outer, radius, radius, p);
    b.seam.set(b.outer);
    b.seam.inset(1.8f * density, 1.8f * density);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0xffac906d);
    p.setStrokeWidth(.8f * density);
    p.setPathEffect(b.stitches);
    canvas.drawRoundRect(b.seam, Math.max(1, radius - density), Math.max(1, radius - density), p);
    p.setPathEffect(null);
    p.setStyle(Paint.Style.FILL);
    b.inner.set(b.outer);
    b.inner.inset(rim * density, rim * density);
    if (!b.inner.isEmpty()) {
      p.setColor(fill);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(density);
      p.setColor(edge == LEATHER ? 0xffd5c4a2 : edge);
      canvas.drawRoundRect(
          b.inner, Math.max(1, radius - 3 * density), Math.max(1, radius - 3 * density), p);
      p.setStyle(Paint.Style.FILL);
    }
    if (clipped && b.outer.width() > 48 * density) {
      b.clip.set(
          b.outer.left + 14 * density,
          b.outer.top - density,
          b.outer.left + 42 * density,
          b.outer.top + 6 * density);
      p.setColor(BRASS);
      canvas.drawRoundRect(b.clip, 2 * density, 2 * density, p);
      p.setColor(0xffe9cf94);
      canvas.drawRect(
          b.clip.left + 2 * density,
          b.clip.top + density,
          b.clip.right - 2 * density,
          b.clip.top + 2 * density,
          p);
    }
    p.setStyle(Paint.Style.FILL);
    p.setPathEffect(null);
  }

  /** 기존 Canvas 버튼 호출도 같은 가죽·봉제·황동 손잡이를 사용한다. */
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
    b.shadow.offset(0, depth);
    p.setColor(LEATHER);
    canvas.drawRoundRect(b.shadow, corner, corner, p);
    p.setColor(LEATHER_LIGHT);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    b.seam.set(b.outer);
    b.seam.inset(2 * density, 2 * density);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(.8f * density);
    p.setColor(0xffbba07a);
    p.setPathEffect(b.stitches);
    canvas.drawRoundRect(b.seam, Math.max(1, corner - density), Math.max(1, corner - density), p);
    p.setPathEffect(null);
    p.setStyle(Paint.Style.FILL);
    b.inner.set(b.outer);
    b.inner.inset(5 * density, 5 * density);
    p.setColor(fill);
    canvas.drawRoundRect(
        b.inner, Math.max(1, corner - 3 * density), Math.max(1, corner - 3 * density), p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(density);
    p.setColor(fill == TEAL || fill == LEATHER ? BRASS : 0xffd9bf86);
    canvas.drawRoundRect(
        b.inner, Math.max(1, corner - 3 * density), Math.max(1, corner - 3 * density), p);
    p.setStyle(Paint.Style.FILL);
    p.setColor(BRASS);
    float hinge = Math.min(12 * density, b.inner.height() * .5f);
    canvas.drawRoundRect(
        b.outer.left + density,
        b.outer.centerY() - hinge / 2,
        b.outer.left + 4 * density,
        b.outer.centerY() + hinge / 2,
        density,
        density,
        p);
    canvas.drawRoundRect(
        b.outer.right - 4 * density,
        b.outer.centerY() - hinge / 2,
        b.outer.right - density,
        b.outer.centerY() + hinge / 2,
        density,
        density,
        p);
    canvas.restoreToCount(save);
  }
}
