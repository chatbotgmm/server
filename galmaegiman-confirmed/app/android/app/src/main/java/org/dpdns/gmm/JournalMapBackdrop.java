package org.dpdns.gmm;

import android.graphics.*;
import android.view.View;

/** 조합 기록을 받치는 고정 지도 종이. 실제 조합·수량·클릭 영역과 분리한다. */
final class JournalMapBackdrop extends View implements FullBleed {
  private final MainActivity host;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF page = new RectF();
  private final Path coast = new Path();

  JournalMapBackdrop(MainActivity host) {
    super(host);
    this.host = host;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    page.set(host.dp(3), host.dp(3), width - host.dp(3), height - host.dp(3));
    coast.reset();
    coast.moveTo(width * .04f, height * .25f);
    coast.cubicTo(
        width * .22f, height * .08f, width * .15f, height * .55f, width * .38f, height * .42f);
    coast.cubicTo(
        width * .53f, height * .32f, width * .57f, height * .89f, width * .77f, height * .75f);
    coast.cubicTo(
        width * .89f, height * .63f, width * .83f, height * .46f, width * .97f, height * .40f);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawColor(JournalTheme.WOOD);
    JournalTheme.drawPanel(
        canvas, page, getResources().getDisplayMetrics().density, JournalTheme.PAPER, true);
    paint.setColor(0x0c6f5c3f);
    paint.setStrokeWidth(host.dp(1));
    float grid = host.dp(42);
    for (float x = grid; x < getWidth() - grid / 2; x += grid)
      canvas.drawLine(x, host.dp(12), x, getHeight() - host.dp(12), paint);
    for (float y = grid; y < getHeight() - grid / 2; y += grid)
      canvas.drawLine(host.dp(12), y, getWidth() - host.dp(12), y, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(0x226d6045);
    paint.setStrokeWidth(host.dp(2));
    canvas.drawPath(coast, paint);
    paint.setStyle(Paint.Style.FILL);
  }
}
