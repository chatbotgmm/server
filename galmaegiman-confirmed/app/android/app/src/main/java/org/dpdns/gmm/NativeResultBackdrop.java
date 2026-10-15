package org.dpdns.gmm;

import android.graphics.*;

/** 뽑기·조합 연출의 청록 항해 표지. 얇은 황동 나침반과 항로는 중앙 그림을 가리지 않는다. */
final class NativeResultBackdrop {
  private static final ThreadLocal<Chart> CHART =
      new ThreadLocal<Chart>() {
        @Override
        protected Chart initialValue() {
          return new Chart();
        }
      };

  private static final class Chart {
    final RectF edge = new RectF();
    final Path course = new Path();
    int width, height;
    RadialGradient shade;
  }

  static void draw(MainActivity h, Canvas canvas, int width, int height, Paint paint) {
    Chart chart = CHART.get();
    if (chart.shade == null || chart.width != width || chart.height != height) {
      chart.width = width;
      chart.height = height;
      chart.shade =
          new RadialGradient(
              width * .5f,
              height * .4f,
              Math.max(1, Math.max(width, height) * .7f),
              JournalTheme.TEAL,
              0xff102a2e,
              Shader.TileMode.CLAMP);
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
    paint.setShader(chart.shade);
    canvas.drawRect(0, 0, width, height, paint);
    paint.setShader(null);
    paint.setColor(JournalTheme.BRASS);
    paint.setAlpha(30);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(h.dp(1));
    float cx = width * .13f, cy = height * .42f, radius = Math.max(1, height * .26f);
    canvas.drawCircle(cx, cy, radius, paint);
    canvas.drawCircle(cx, cy, radius * .84f, paint);
    for (int bearing = 0; bearing < 8; bearing++) {
      double angle = Math.PI * bearing / 4;
      float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
      canvas.drawLine(
          cx + dx * radius * .2f,
          cy + dy * radius * .2f,
          cx + dx * radius * (bearing % 2 == 0 ? 1.14f : .84f),
          cy + dy * radius * (bearing % 2 == 0 ? 1.14f : .84f),
          paint);
    }
    chart.course.reset();
    chart.course.moveTo(width * .78f, height * .12f);
    chart.course.cubicTo(
        width * .93f, height * .18f, width * .68f, height * .63f, width * .91f, height * .83f);
    canvas.drawPath(chart.course, paint);
    canvas.drawCircle(width * .78f, height * .12f, h.dp(3), paint);
    canvas.drawCircle(width * .91f, height * .83f, h.dp(3), paint);
    paint.setAlpha(255);
    RectF edge = chart.edge;
    edge.set(h.dp(5), h.dp(5), width - h.dp(5), height - h.dp(5));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(h.dp(5));
    paint.setColor(JournalTheme.LEATHER);
    canvas.drawRoundRect(edge, h.dp(6), h.dp(6), paint);
    edge.inset(h.dp(4), h.dp(4));
    paint.setStrokeWidth(h.dp(1));
    paint.setColor(JournalTheme.BRASS);
    paint.setAlpha(160);
    canvas.drawRoundRect(edge, h.dp(4), h.dp(4), paint);
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);
  }

  private NativeResultBackdrop() {}
}
