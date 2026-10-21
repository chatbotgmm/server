package org.dpdns.gmm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

/** 미공개 카드의 테두리만 밝힌다. 분리·백그라운드에서는 프레임 예약을 멈춘다. */
final class HiddenRecipeFrame extends FrameLayout {
  private final MainActivity host;
  private final boolean hint;
  private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
  private long started = SystemClock.uptimeMillis();

  HiddenRecipeFrame(MainActivity host, boolean hint) {
    super(host);
    this.host = host;
    this.hint = hint;
    // 미공개 카드의 이름·등급 글자가 어두운 장면 위가 아니라 양피지 위에, 가장자리 장식(약 9dp) 안쪽에 놓이게 한다.
    setPadding(host.dp(10), host.dp(10), host.dp(10), host.dp(10));
    setBackground(ThemeArt.of(host).drawable(ThemeArt.Surface.PAPER));
    setContentDescription(hint ? "미공개 입사등록증 · 선택 재료에 숨은 조합 단서가 있어요. 성공 여부는 미확인이에요." : "조합 결과 카드");
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    if (!hint) return;
    boolean running = isAttachedToWindow() && isShown() && hasWindowFocus() && host.motionResumed;
    float wave = running ? (float) ((1 - Math.cos((SystemClock.uptimeMillis() - started) * Math.PI / 1000)) / 2) : 0;
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(host.dp(2));
    stroke.setColor(0xffb68b45);
    stroke.setAlpha((int) (65 + 100 * wave));
    float inset = host.dp(2);
    canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, host.dp(6), host.dp(6), stroke);
    if (running) postInvalidateOnAnimation();
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
