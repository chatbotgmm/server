package org.dpdns.gmm;

import android.content.res.ColorStateList;
import android.graphics.*;

/** Harbor surfaces: quiet limestone, readable paper, navy actions and brass selection. */
final class JournalTheme {
  static final int PAPER = 0xfff3e9d3, INK = 0xff20343a, WOOD = 0xff153f50;
  static final int LEATHER = WOOD, BRASS = 0xffbc9555, TEAL = 0xff153f50;
  static final int MUTED = 0xff465860, WHITE = 0xffffffff, LINE = 0xffc9c4b5;
  static final int SELECTED = 0xffdce8e6, DISABLED = 0xffd7d5cd;
  static final int PRIMARY = TEAL, PRIMARY_HIGHLIGHT = 0xff235469;
  static final int POSITIVE_INFO = 0xff326b71, NEGATIVE_INFO = 0xffa84336;
  private static final ThreadLocal<Paint> BRUSH = new ThreadLocal<Paint>() {
    @Override protected Paint initialValue() { return new Paint(Paint.ANTI_ALIAS_FLAG); }
  };
  private static Paint brush() {
    Paint p = BRUSH.get(); p.reset(); p.setAntiAlias(true); return p;
  }
  private static final ColorStateList CONTROL_TEXT = colors(INK);
  private static final ColorStateList PRIMARY_TEXT = colors(WHITE);

  private static ColorStateList colors(int normal) {
    return new ColorStateList(new int[][] {
      {-android.R.attr.state_enabled}, {android.R.attr.state_selected},
      {android.R.attr.state_checked}, {android.R.attr.state_activated}, {}},
      new int[] {MUTED, TEAL, TEAL, TEAL, normal});
  }
  static ColorStateList controlText() { return CONTROL_TEXT; }
  static ColorStateList controlText(int fill) { return fill == PRIMARY ? PRIMARY_TEXT : CONTROL_TEXT; }

  private static void surface(Canvas c, RectF b, float density, int fill, int edge,
      float stroke, float depth, Paint p) {
    if (b.isEmpty()) return;
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    float d = Math.max(.5f, density), radius = 6 * d;
    RectF r = new RectF(b);
    r.inset(d, d);
    r.bottom -= depth * d;
    if (r.isEmpty()) return;
    if (depth > 0) {
      RectF shadow = new RectF(r);
      shadow.offset(0, depth * d);
      p.setColor(0x3320343a);
      c.drawRoundRect(shadow, radius, radius, p);
    }
    p.setColor(fill == PAPER ? 0xfffff8e8 : fill);
    c.drawRoundRect(r, radius, radius, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(stroke * d);
    p.setColor(edge);
    c.drawRoundRect(r, radius, radius, p);
    p.setStyle(Paint.Style.FILL);
  }
  static void drawPanel(Canvas c, RectF b, float d, int fill, boolean clipped) {
    // Preserve the loaded paper silhouette; the quiet center wash is handled by its renderer.
    if (fill == PAPER && JournalParchment.drawPanel(c, b, d)) return;
    Paint p = brush();
    surface(c, b, d, fill, fill == SELECTED ? BRASS : LINE, fill == SELECTED ? 2 : 1, 1, p);
    if (clipped && b.width() > 80 * d) {
      p.setColor(BRASS);
      c.drawRect(b.left + 10*d, b.top + d, b.left + 36*d, b.top + 3*d, p);
    }
  }
  static void drawPaper(Canvas c, RectF b, float d, int edge) { drawPaper(c, b, d, edge, PAPER); }
  static void drawPaper(Canvas c, RectF b, float d, int edge, int fill) {
    surface(c, b, d, fill, fill == SELECTED ? BRASS : LINE,
        fill == SELECTED ? 2 : .7f, 0, brush());
  }
  static void drawButton(Canvas c, RectF b, int fill, float radius, float depth, Paint paint) {
    Paint p = brush();
    p.setColorFilter(paint.getColorFilter());
    float d = Math.max(.5f, radius / 16f);
    if (fill == PAPER) {
      int save = c.save();
      if (paint.getAlpha() != 255 || paint.getColorFilter() != null) c.saveLayer(b, paint);
      boolean drawn = JournalParchment.drawButton(c, b, d);
      c.restoreToCount(save);
      if (drawn) return;
    }
    surface(c, b, d, fill, fill == PRIMARY || fill == SELECTED ? BRASS : LINE,
        fill == SELECTED ? 2 : 1, Math.min(1.5f, depth / d), p);
  }
  private JournalTheme() {}
}
