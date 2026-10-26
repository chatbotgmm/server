package org.dpdns.gmm;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 앱 콘텐츠 로딩 전용. OS splash·로그인·로비 그림과 분리한다. */
final class InitialLoadingView extends FrameLayout {
  private final MainActivity host;
  private final Bitmap artwork;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF imageBox = new RectF(), bar = new RectF();
  private final LinearLayout footer;
  private final TextView status;
  private final SceneMotion motion;
  private float progress = -1;

  InitialLoadingView(MainActivity host, String message, Runnable retry) {
    super(host);
    this.host = host;
    artwork = host.art.asset("loading/play-store-feature.png");
    motion = new SceneMotion(this, host);
    setWillNotDraw(false);
    setBackgroundColor(0xff173d50);
    setTag("initial-loading");
    footer = new LinearLayout(host);
    footer.setOrientation(LinearLayout.VERTICAL);
    footer.setGravity(Gravity.CENTER);
    status = host.text(message, 15);
    status.setGravity(Gravity.CENTER);
    status.setTextColor(0xfffff4db);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    footer.addView(status, new LinearLayout.LayoutParams(-1, -2));
    if (retry != null) {
      Button button = host.button("다시 연결", retry);
      footer.addView(button, new LinearLayout.LayoutParams(host.dp(160), host.dp(48)));
    }
    addView(footer);
  }

  void setMessage(String message) { status.setText(message); }

  void setProgress(GameResourcePreparation.Progress prepared) {
    progress = prepared.total <= 0 ? -1 : prepared.completed / (float) prepared.total;
    status.setText("게임 리소스 준비 · " + prepared.succeeded + " / " + prepared.total
        + (prepared.failed == 0 ? "" : " · 실패 " + prepared.failed)
        + (prepared.deferred == 0 ? "" : " · 배경 " + prepared.deferred + "개는 화면에서 준비"));
    invalidate();
  }

  /** Failed resources stay visible as failures; retry or continue is an explicit choice. */
  void setActions(Runnable retry, Runnable proceed) {
    while (footer.getChildCount() > 1) footer.removeViewAt(1);
    LinearLayout actions = new LinearLayout(host);
    actions.setGravity(Gravity.CENTER);
    if (retry != null) actions.addView(host.button("리소스 다시 준비", retry),
        new LinearLayout.LayoutParams(host.dp(180), host.dp(48)));
    if (proceed != null) actions.addView(host.button("준비된 자료로 계속", proceed),
        new LinearLayout.LayoutParams(host.dp(180), host.dp(48)));
    footer.addView(actions, new LinearLayout.LayoutParams(-1, -2));
    requestLayout();
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    setMeasuredDimension(width, height);
    int pad = host.dp(12);
    footer.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width - 2 * pad), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(Math.max(1, height - 2 * pad), MeasureSpec.AT_MOST));
    float bottom = Math.max(pad, height - pad - footer.getMeasuredHeight());
    float barWidth = Math.min(host.dp(180), Math.max(0, width - 4 * pad));
    bar.set((width - barWidth) / 2f, bottom - host.dp(10),
        (width + barWidth) / 2f, bottom - host.dp(6));
    float availableWidth = Math.max(0, width - 2f * pad);
    float availableHeight = Math.max(0, bar.top - host.dp(12) - pad);
    float scale = Math.min(availableWidth / 1024f, availableHeight / 500f);
    float w = 1024 * scale, h = 500 * scale;
    imageBox.set((width - w) / 2, pad + (availableHeight - h) / 2,
        (width + w) / 2, pad + (availableHeight + h) / 2);
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int pad = host.dp(12), top = Math.max(pad, getHeight() - pad - footer.getMeasuredHeight());
    footer.layout(pad, top, getWidth() - pad, top + footer.getMeasuredHeight());
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint.setAlpha(255);
    if (artwork != null && !imageBox.isEmpty()) canvas.drawBitmap(artwork, null, imageBox, paint);
    paint.setColor(0xff547481);
    canvas.drawRoundRect(bar, host.dp(2), host.dp(2), paint);
    paint.setColor(0xffedcf8c);
    if (progress < 0) {
      // Account/update requests have no known work count.
      paint.setAlpha(150 + Math.round(105 * motion.pulse(2200, 0)));
      canvas.drawRoundRect(bar, host.dp(2), host.dp(2), paint);
    } else {
      paint.setAlpha(255);
      canvas.drawRoundRect(new RectF(bar.left, bar.top, bar.left + bar.width()
          * Math.max(0, Math.min(1, progress)), bar.bottom), host.dp(2), host.dp(2), paint);
    }
    paint.setAlpha(255);
  }

  @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); motion.start(); }
  @Override protected void onDetachedFromWindow() { motion.close(); super.onDetachedFromWindow(); }
}
