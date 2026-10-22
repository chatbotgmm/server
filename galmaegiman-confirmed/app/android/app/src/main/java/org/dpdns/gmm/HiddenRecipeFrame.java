package org.dpdns.gmm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

/** 미공개 카드의 테두리만 밝힌다. 분리·백그라운드에서는 프레임 예약을 멈춘다. */
final class HiddenRecipeFrame extends FrameLayout {
  // UI-COLOR.md: 금빛은 진행·보상 전용. 단서 강조는 같은 따뜻한 계열(캐러멜·토피)에서 두께·빛 번짐으로 준다.
  static final int CARAMEL = 0xffa3541b, TOFFEE = 0xffdd9a58, CREAM = 0xfffff4db;
  private final MainActivity host;
  private final boolean hint;
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path border = new Path(), comet = new Path(), sparkle = new Path();
  private final PathMeasure measure = new PathMeasure();
  private long started = SystemClock.uptimeMillis();

  HiddenRecipeFrame(MainActivity host, boolean hint) {
    super(host);
    this.host = host;
    this.hint = hint;
    // 미공개 카드의 이름·등급 글자가 어두운 장면 위가 아니라 양피지 위에, 가장자리 장식(약 9dp) 안쪽에 놓이게 한다.
    setPadding(host.dp(10), host.dp(10), host.dp(10), host.dp(10));
    setBackground(ThemeArt.of(host).drawable(ThemeArt.Surface.PAPER));
    setContentDescription(hint ? "미공개 입사등록증 · 선택 재료에 숨은 조합 단서가 있어요. 성공 여부는 미확인이에요." : "조합 결과 카드");
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeCap(Paint.Cap.ROUND);
  }

  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    border.reset();
    float inset = host.dp(4);
    border.addRoundRect(new RectF(inset, inset, w - inset, h - inset), host.dp(6), host.dp(6), Path.Direction.CW);
    measure.setPath(border, true);
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    if (!hint) return;
    boolean running = isAttachedToWindow() && isShown() && hasWindowFocus() && host.motionResumed;
    paintHint(canvas, running, SystemClock.uptimeMillis() - started);
    if (running) postInvalidateOnAnimation();
  }

  /** 단서 테두리 그리기. t는 재생 시작 후 경과 ms(시험에서 특정 시점을 그릴 때도 사용). */
  void paintHint(Canvas canvas, boolean running, long t) {
    int w = getWidth(), h = getHeight();
    // 약 2초 주기의 숨쉬기(1초 뒤 가장 밝음).
    float wave = running ? (float) ((1 - Math.cos(t * Math.PI / 1000)) / 2) : .5f;
    // 1) 카드 전체에 따뜻한 빛이 숨 쉬듯 번진다(황동 카드 위에서도 깜빡임이 보이게).
    fill.setColor(TOFFEE);
    fill.setAlpha(running ? (int) (10 + 60 * wave) : 0);
    canvas.drawRoundRect(0, 0, w, h, host.dp(6), host.dp(6), fill);
    // 2) 진한 캐러멜 테두리(3dp).
    stroke.setStrokeWidth(host.dp(3));
    stroke.setColor(CARAMEL);
    stroke.setAlpha(255);
    canvas.drawPath(border, stroke);
    // 3) 테두리를 따라 1.6초에 한 바퀴 도는 굵은 크림색 빛줄기(토피색 번짐 포함).
    float length = measure.getLength();
    if (running && length > 0) {
      float head = (t % 1600) / 1600f * length, tail = length * .25f;
      comet.reset();
      if (head - tail >= 0) measure.getSegment(head - tail, head, comet, true);
      else { measure.getSegment(length + head - tail, length, comet, true); measure.getSegment(0, head, comet, true); }
      stroke.setStrokeWidth(host.dp(10)); stroke.setColor(TOFFEE); stroke.setAlpha(150); canvas.drawPath(comet, stroke);
      stroke.setStrokeWidth(host.dp(5)); stroke.setColor(CREAM); stroke.setAlpha(255); canvas.drawPath(comet, stroke);
    }
    // 4) 네 모서리 안쪽 반짝이 별: 차례로 커졌다 작아진다. 멈춤 상태에서는 중간 크기로 남는다.
    float m = host.dp(16);
    float[][] spots = {{m, m}, {w - m, m}, {w - m, h - m}, {m, h - m}};
    for (int i = 0; i < spots.length; i++) {
      float phase = running ? (float) ((1 - Math.cos((t + i * 400) * Math.PI / 800)) / 2) : .5f;
      star(canvas, spots[i][0], spots[i][1], host.dp(5) + host.dp(7) * phase, (int) (140 + 115 * phase));
    }
  }

  private void star(Canvas canvas, float x, float y, float r, int alpha) {
    sparkle.reset();
    float k = r * .28f;
    sparkle.moveTo(x, y - r); sparkle.lineTo(x + k, y - k); sparkle.lineTo(x + r, y); sparkle.lineTo(x + k, y + k);
    sparkle.lineTo(x, y + r); sparkle.lineTo(x - k, y + k); sparkle.lineTo(x - r, y); sparkle.lineTo(x - k, y - k); sparkle.close();
    fill.setColor(CREAM); fill.setAlpha(alpha); canvas.drawPath(sparkle, fill);
    stroke.setStrokeWidth(host.dp(1.5f)); stroke.setColor(CARAMEL); stroke.setAlpha(alpha); canvas.drawPath(sparkle, stroke);
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    started = SystemClock.uptimeMillis();
    if (hint) invalidate();
  }

  @Override protected void onVisibilityChanged(View changed, int visibility) {
    super.onVisibilityChanged(changed, visibility);
    if (visibility == VISIBLE && hint) invalidate();
  }
}
