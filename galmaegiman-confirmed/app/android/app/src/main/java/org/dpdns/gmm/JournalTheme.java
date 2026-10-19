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
  static final int MUTED = 0xff58635d;
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
        new LinearGradient(
            0,
            0,
            0,
            1,
            new int[] {0xfff7efd7, 0xfffcf8eb, PAPER, 0xffeee2c5},
            new float[] {0, .16f, .77f, 1},
            Shader.TileMode.CLAMP);
    final LinearGradient primary =
        new LinearGradient(
            0,
            0,
            0,
            1,
            new int[] {0xff356e73, PRIMARY_HIGHLIGHT, PRIMARY, 0xff17474c},
            new float[] {0, .16f, .55f, 1},
            Shader.TileMode.CLAMP);
    final LinearGradient secondary =
        new LinearGradient(
            0,
            0,
            0,
            1,
            new int[] {0xfffff9e9, 0xfff8f0db, 0xffeadbb9},
            new float[] {0, .72f, 1},
            Shader.TileMode.CLAMP);
    final LinearGradient wood =
        new LinearGradient(
            0,
            0,
            0,
            1,
            new int[] {0xff8a6842, 0xff64462e, 0xff402d21, WOOD},
            new float[] {0, .13f, .8f, 1},
            Shader.TileMode.CLAMP);
    final LinearGradient topAge =
        new LinearGradient(0, 0, 0, 1, 0x428f602f, 0x008f602f, Shader.TileMode.CLAMP);
    final LinearGradient sideAge =
        new LinearGradient(0, 0, 1, 0, 0x388f602f, 0x008f602f, Shader.TileMode.CLAMP);
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

  private static void gradient(Brushes b, LinearGradient gradient, RectF bounds) {
    b.matrix.setScale(Math.max(1, bounds.width()), Math.max(1, bounds.height()));
    b.matrix.postTranslate(bounds.left, bounds.top);
    gradient.setLocalMatrix(b.matrix);
    b.paint.setColor(Color.WHITE);
    b.paint.setShader(gradient);
  }

  private static void surface(Brushes b, RectF bounds, int fill, boolean button) {
    if (fill == PAPER || fill == PRIMARY)
      gradient(b, fill == PRIMARY ? b.primary : button ? b.secondary : b.paper, bounds);
    else {
      b.paint.setShader(null);
      b.paint.setColor(fill);
    }
  }

  /** 소재는 NativeArt의 작업자에서 준비한다. 여기서는 이미 준비된 Shader만 그린다. */
  private static void material(
      Canvas canvas, Brushes b, RectF bounds, float radius, float density, int fill) {
    if (fill != PAPER && fill != SELECTED) return;
    int save = canvas.save();
    b.contour.reset();
    b.contour.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    canvas.clipPath(b.contour);
    JournalMaterial.draw(canvas, bounds, density, fill == SELECTED ? 25 : 48);
    canvas.restoreToCount(save);
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
    float radius = Math.min(5 * density, b.outer.height() * .14f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, depth * density);
    p.setColor(0x9634261f);
    canvas.drawRoundRect(b.shadow, radius, radius, p);
    // 목재 판의 외곽은 잉크색, 안쪽 단면은 따뜻한 나뭇결 방향의 밝기 차이다.
    p.setColor(WOOD);
    canvas.drawRoundRect(b.outer, radius, radius, p);
    if (rim >= 3) {
      b.seam.set(b.outer);
      b.seam.inset(.7f * density, .7f * density);
      gradient(b, b.wood, b.seam);
      canvas.drawRoundRect(b.seam, radius, radius, p);
      p.setShader(null);
      b.seam.set(b.outer);
      b.seam.inset(2.1f * density, 2.1f * density);
      b.seam.set(
          pixelCenter(b.seam.left), pixelCenter(b.seam.top),
          pixelCenter(b.seam.right), pixelCenter(b.seam.bottom));
      p.setStyle(Paint.Style.STROKE);
      p.setColor(BRASS);
      p.setStrokeWidth(1.1f * density);
      canvas.drawRoundRect(b.seam, Math.max(1, radius - density), Math.max(1, radius - density), p);
      p.setStyle(Paint.Style.FILL);
    }
    b.inner.set(b.outer);
    b.inner.inset(rim * density, rim * density);
    if (!b.inner.isEmpty()) {
      float innerRadius = Math.max(1, radius - 2.5f * density);
      surface(b, b.inner, fill, false);
      canvas.drawRoundRect(b.inner, innerRadius, innerRadius, p);
      p.setShader(null);
      material(canvas, b, b.inner, innerRadius, density, fill);
      if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, clipped);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(.65f * density);
      p.setColor(edge == WOOD ? 0xff987b50 : edge);
      canvas.drawRoundRect(b.inner, innerRadius, innerRadius, p);
      p.setStyle(Paint.Style.FILL);
    }
    if (clipped && b.outer.width() > 48 * density && b.outer.height() > 32 * density)
      cornerWork(canvas, b, b.outer, density);
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setPathEffect(null);
  }

  /** 작은 황동 모서리와 나사 홈. 내용 칸 안으로 큰 장식을 넣지 않는다. */
  private static void cornerWork(Canvas canvas, Brushes b, RectF bounds, float density) {
    Paint p = b.paint;
    for (int corner = 0; corner < 4; corner++) {
      float x = corner % 2 == 0 ? bounds.left + 3.7f * density : bounds.right - 3.7f * density;
      float y = corner < 2 ? bounds.top + 3.7f * density : bounds.bottom - 3.7f * density;
      float dx = corner % 2 == 0 ? density : -density;
      float dy = corner < 2 ? density : -density;
      b.ornament.reset();
      b.ornament.moveTo(x + 8 * dx, y);
      b.ornament.lineTo(x, y);
      b.ornament.lineTo(x, y + 8 * dy);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.25f * density);
      p.setColor(0xffdbc087);
      canvas.drawPath(b.ornament, p);
      p.setStyle(Paint.Style.FILL);
      p.setColor(0xff72512c);
      canvas.drawCircle(x, y, 1.7f * density, p);
      p.setColor(0xffd9ba79);
      canvas.drawCircle(x - .25f * density, y - .25f * density, 1.1f * density, p);
      p.setStrokeWidth(.5f * density);
      p.setColor(0xff755431);
      canvas.drawLine(x - .6f * density, y, x + .6f * density, y, p);
    }
  }

  private static float pixelCenter(float value) {
    return (float) Math.floor(value) + .5f;
  }

  /** 종이 섬유와 오래된 가장자리. 진한 지도선은 본문을 피한 모서리에만 둔다. */
  private static void parchmentEdge(
      Canvas canvas, Brushes b, RectF bounds, float density, boolean mapCorners) {
    Paint p = b.paint;
    int save = canvas.save();
    b.contour.reset();
    b.contour.addRoundRect(bounds, 3 * density, 3 * density, Path.Direction.CW);
    canvas.clipPath(b.contour);
    float band = Math.min(9 * density, Math.min(bounds.height(), bounds.width()) * .2f);
    b.clip.set(bounds.left, bounds.top, bounds.right, bounds.top + band);
    gradient(b, b.topAge, b.clip);
    canvas.drawRect(b.clip, p);
    b.matrix.setScale(1, -band);
    b.matrix.postTranslate(0, bounds.bottom);
    b.topAge.setLocalMatrix(b.matrix);
    p.setShader(b.topAge);
    canvas.drawRect(bounds.left, bounds.bottom - band, bounds.right, bounds.bottom, p);
    b.clip.set(bounds.left, bounds.top, bounds.left + band, bounds.bottom);
    gradient(b, b.sideAge, b.clip);
    canvas.drawRect(b.clip, p);
    b.matrix.setScale(-band, 1);
    b.matrix.postTranslate(bounds.right, 0);
    b.sideAge.setLocalMatrix(b.matrix);
    p.setShader(b.sideAge);
    canvas.drawRect(bounds.right - band, bounds.top, bounds.right, bounds.bottom, p);
    p.setShader(null);
    b.clip.set(bounds);
    b.clip.inset(2.6f * density, 2.6f * density);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0x728b7046);
    p.setStrokeWidth(.55f * density);
    canvas.drawRoundRect(b.clip, density, density, p);
    // 불규칙한 찢김·섬유는 매 프레임 같은 좌표이며 3dp 가장자리만 사용한다.
    p.setColor(0x457c5832);
    p.setStrokeWidth(.45f * density);
    int index = 0;
    for (float x = bounds.left + 8 * density; x < bounds.right - 7 * density; x += 17 * density) {
      float offset = (index++ * 7 % 5) * .33f * density;
      canvas.drawLine(
          x,
          bounds.top + .8f * density,
          x + 2.5f * density,
          bounds.top + 1.4f * density + offset,
          p);
      canvas.drawLine(
          x + 4 * density,
          bounds.bottom - .7f * density,
          x + 6 * density,
          bounds.bottom - 1.5f * density - offset,
          p);
    }
    for (float y = bounds.top + 9 * density; y < bounds.bottom - 8 * density; y += 21 * density) {
      canvas.drawLine(bounds.left + .6f * density, y, bounds.left + 2 * density, y + density, p);
      canvas.drawLine(
          bounds.right - .6f * density,
          y + 3 * density,
          bounds.right - 1.9f * density,
          y + density,
          p);
    }
    if (mapCorners && bounds.width() > 96 * density && bounds.height() > 56 * density) {
      p.setColor(0x8c9a7a44);
      p.setStrokeWidth(.6f * density);
      for (int corner = 0; corner < 4; corner++) {
        float x = corner % 2 == 0 ? bounds.left + 5 * density : bounds.right - 5 * density;
        float y = corner < 2 ? bounds.top + 5 * density : bounds.bottom - 5 * density;
        float dx = corner % 2 == 0 ? density : -density;
        float dy = corner < 2 ? density : -density;
        b.ornament.reset();
        b.ornament.moveTo(x + 13 * dx, y);
        b.ornament.lineTo(x + 3 * dx, y);
        b.ornament.lineTo(x + 3 * dx, y + 3 * dy);
        b.ornament.lineTo(x, y + 3 * dy);
        b.ornament.lineTo(x, y + 13 * dy);
        canvas.drawPath(b.ornament, p);
        b.ornament.reset();
        b.ornament.moveTo(x + 7 * dx, y + 3 * dy);
        b.ornament.lineTo(x + 7 * dx, y + 7 * dy);
        b.ornament.lineTo(x + 3 * dx, y + 7 * dy);
        canvas.drawPath(b.ornament, p);
        canvas.drawCircle(x + 3 * dx, y + 3 * dy, 1.05f * density, p);
      }
    }
    canvas.restoreToCount(save);
    p.setStyle(Paint.Style.FILL);
    p.setShader(null);
  }

  /** 손에 잡히는 얇은 황동판·청록 도장 면. 레이아웃과 실제 터치 영역은 바꾸지 않는다. */
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
    b.outer.inset(.8f * density, .8f * density);
    if (b.outer.isEmpty()) {
      canvas.restoreToCount(save);
      return;
    }
    float corner = Math.min(5 * density, b.outer.height() * .14f);
    b.shadow.set(b.outer);
    b.shadow.offset(0, Math.min(depth, 2.5f * density));
    // 얇은 바닥 단면과 가장자리의 밝은 황동이 버튼의 높이를 만든다.
    p.setColor(fill == DISABLED ? 0xffa79a80 : 0xff493523);
    canvas.drawRoundRect(b.shadow, corner, corner, p);
    p.setColor(fill == PRIMARY ? 0xff806337 : 0xff85633d);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    b.inner.set(b.outer);
    b.inner.inset(1.1f * density, 1.1f * density);
    surface(b, b.inner, fill, true);
    canvas.drawRoundRect(b.inner, Math.max(1, corner - density), Math.max(1, corner - density), p);
    p.setShader(null);
    material(canvas, b, b.inner, Math.max(1, corner - density), density, fill);
    if (fill == PAPER) parchmentEdge(canvas, b, b.inner, density, false);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(.75f * density);
    p.setColor(fill == PRIMARY ? 0xffdbbd78 : fill == DISABLED ? 0xffc3b69d : 0xffd9c195);
    canvas.drawRoundRect(b.outer, corner, corner, p);
    b.inner.set(b.outer);
    b.inner.inset(3 * density, 3 * density);
    p.setStrokeWidth(.65f * density);
    p.setColor(fill == PRIMARY ? 0xff73a09b : fill == SELECTED ? 0xff75a09a : 0xfff9f1da);
    canvas.drawRoundRect(
        b.inner, Math.max(1, corner - 2 * density), Math.max(1, corner - 2 * density), p);
    // 반짝임은 윗면의 1dp에만 두어 글자 아래를 밝히거나 흔들지 않는다.
    p.setColor(fill == PRIMARY ? 0x997eb1aa : 0xb3fff9e8);
    p.setStrokeWidth(.7f * density);
    canvas.drawLine(
        b.outer.left + 5 * density,
        b.outer.top + 1.7f * density,
        b.outer.right - 5 * density,
        b.outer.top + 1.7f * density,
        p);
    p.setStyle(Paint.Style.FILL);
    if (fill != DISABLED && b.outer.width() > 72 * density && b.outer.height() > 28 * density) {
      for (int cornerIndex = 0; cornerIndex < 4; cornerIndex++) {
        float x =
            cornerIndex % 2 == 0 ? b.outer.left + 5.3f * density : b.outer.right - 5.3f * density;
        float y = cornerIndex < 2 ? b.outer.top + 5.3f * density : b.outer.bottom - 5.3f * density;
        p.setColor(fill == PRIMARY ? 0xff20484b : 0xffa88a59);
        canvas.drawCircle(x, y + .5f * density, 1.25f * density, p);
        p.setColor(fill == PRIMARY ? 0xffc5ac73 : 0xffdcc89a);
        canvas.drawCircle(x, y, .85f * density, p);
      }
    }
    p.setShader(null);
    canvas.restoreToCount(save);
  }
}
