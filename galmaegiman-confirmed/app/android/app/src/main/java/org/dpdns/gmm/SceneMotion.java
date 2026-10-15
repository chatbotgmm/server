package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

/** 장면 하나당 기존 시계 하나만 쓴다. 배경·캐릭터는 약 30fps, 미표시·일시정지에는 정지한다. */
final class SceneMotion {
  final View owner;
  final MainActivity host;
  ValueAnimator clock;
  double millis;
  private boolean requested;
  private long lastInvalidation;
  private final Runnable lifecycle = this::refresh;
  private final ViewTreeObserver.OnPreDrawListener visibility =
      () -> {
        refresh();
        return true;
      };
  private final ContentObserver animationSetting =
      new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
          refresh();
          owner.invalidate();
        }
      };

  SceneMotion(View owner, MainActivity host) {
    this.owner = owner;
    this.host = host;
  }

  static boolean enabled(MainActivity host) {
    return NativeMotion.enabled(host, host.motionEnabled)
        && NativeMotion.legacyEnabled(host, host.motionEnabled);
  }

  void start() {
    if (!requested) {
      requested = true;
      if (host.journalTime != null) host.journalTime.addListener(lifecycle);
      owner.getViewTreeObserver().addOnPreDrawListener(visibility);
      host.getContentResolver()
          .registerContentObserver(
              Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
              false,
              animationSetting);
    }
    refresh();
  }

  private void refresh() {
    boolean run =
        requested
            && enabled(host)
            && host.motionResumed
            && owner.isAttachedToWindow()
            && owner.getWindowVisibility() == View.VISIBLE
            && owner.isShown();
    if (!run) {
      stopClock();
      return;
    }
    if (clock != null) return;
    clock = ValueAnimator.ofFloat(0, 120000);
    clock.setDuration(120000);
    clock.setRepeatCount(ValueAnimator.INFINITE);
    clock.setInterpolator(new LinearInterpolator());
    final long started = android.os.SystemClock.uptimeMillis();
    final double prior = millis;
    lastInvalidation = 0;
    clock.addUpdateListener(
        a -> {
          if (!enabled(host)
              || !host.motionResumed
              || !owner.isShown()
              || owner.getWindowVisibility() != View.VISIBLE) {
            stopClock();
            return;
          }
          long now = android.os.SystemClock.uptimeMillis();
          // 기존 시계는 유지하고 그리기만 제한한다. 복귀 시 백그라운드 경과 시간을 밀어 넣지 않는다.
          millis = prior + now - started;
          if (lastInvalidation == 0 || now - lastInvalidation >= 33) {
            lastInvalidation = now;
            owner.postInvalidateOnAnimation();
          }
        });
    clock.start();
  }

  float pulse(float period, float offset) {
    return !enabled(host) || clock == null
        ? 0
        : (float) ((1 - Math.cos(((millis + offset) % period) * Math.PI * 2 / period)) / 2);
  }

  private void stopClock() {
    ValueAnimator previous = clock;
    clock = null;
    if (previous != null) previous.cancel();
  }

  void close() {
    stopClock();
    millis = 0;
    if (!requested) return;
    requested = false;
    if (host.journalTime != null) host.journalTime.removeListener(lifecycle);
    if (owner.getViewTreeObserver().isAlive())
      owner.getViewTreeObserver().removeOnPreDrawListener(visibility);
    host.getContentResolver().unregisterContentObserver(animationSetting);
  }
}
