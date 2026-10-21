package org.dpdns.gmm;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

/** Home-only weighted pose input. x/y deform the body around fixed feet, never move the scene. */
final class HomeGullDrag {
  static final long RETURN_MILLIS = 1100;
  private final View owner;
  private final float slop, limitX, limitY;
  private float downX, downY, startX, startY;
  private boolean candidate, dragging;
  ValueAnimator returning;
  float x, y;

  HomeGullDrag(View owner, float limitX, float limitY) {
    this.owner = owner;
    this.limitX = limitX;
    this.limitY = limitY;
    slop = ViewConfiguration.get(owner.getContext()).getScaledTouchSlop();
  }

  boolean intercept(MotionEvent event, RectF region, boolean enabled) {
    if (!motionAllowed()) { close(); return false; }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        candidate = enabled && region.contains(event.getX(), event.getY());
        dragging = false;
        downX = event.getX();
        downY = event.getY();
        startX = x;
        startY = y;
        if (candidate) cancelReturn();
        break;
      case MotionEvent.ACTION_MOVE:
        if (candidate && distance(event) > slop) dragging = true;
        return dragging;
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        if (candidate) release();
        candidate = false;
        break;
    }
    return false;
  }

  boolean touch(MotionEvent event) {
    if (!motionAllowed()) { close(); return false; }
    if (!candidate) return false;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        if (distance(event) > slop) dragging = true;
        if (dragging) {
          x = bounded(startX + (event.getX() - downX) * .035f, limitX);
          y = bounded(startY + (event.getY() - downY) * .014f, limitY);
          owner.postInvalidateOnAnimation();
        }
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        boolean wasDragging = dragging;
        release();
        candidate = false;
        if (!wasDragging && event.getActionMasked() == MotionEvent.ACTION_UP) owner.performClick();
        break;
    }
    return true;
  }

  private double distance(MotionEvent event) {
    return Math.hypot(event.getX() - downX, event.getY() - downY);
  }

  static float bounded(float value, float bound) {
    return Math.max(-bound, Math.min(bound, value));
  }

  /** Zero-velocity critically damped decay, normalized to settle exactly without overshoot. */
  static float remaining(float fraction) {
    double t = Math.max(0, Math.min(1, fraction));
    double tail = 9 * Math.exp(-8);
    return (float) Math.max(0, ((1 + 8 * t) * Math.exp(-8 * t) - tail) / (1 - tail));
  }

  void release() {
    dragging = false;
    cancelReturn();
    if (!motionAllowed()) { close(); owner.invalidate(); return; }
    if (x == 0 && y == 0) return;
    final float fromX = x, fromY = y;
    returning = ValueAnimator.ofFloat(0, 1);
    returning.setDuration(RETURN_MILLIS);
    returning.setInterpolator(new LinearInterpolator());
    returning.addUpdateListener(animation -> {
      if (!motionAllowed()) { close(); owner.invalidate(); return; }
      float weight = remaining((float) animation.getAnimatedValue());
      x = fromX * weight;
      y = fromY * weight;
      owner.postInvalidateOnAnimation();
    });
    returning.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation) {
        if (returning == animation) {
          returning = null;
          x = y = 0;
          owner.invalidate();
        }
        animation.removeAllListeners();
        ((ValueAnimator) animation).removeAllUpdateListeners();
      }
    });
    returning.start();
  }

  private boolean motionAllowed() {
    if (!(owner.getContext() instanceof MainActivity))
      return NativeMotion.enabled(owner.getContext(), true)
          && NativeMotion.legacyEnabled(owner.getContext(), true);
    MainActivity host = (MainActivity) owner.getContext();
    return SceneMotion.enabled(host) && host.motionResumed
        && owner.isShown() && owner.getWindowVisibility() == View.VISIBLE;
  }

  private void cancelReturn() {
    ValueAnimator previous = returning;
    returning = null;
    if (previous != null) {
      previous.removeAllUpdateListeners();
      previous.removeAllListeners();
      previous.cancel();
    }
  }

  void close() {
    cancelReturn();
    x = y = 0;
    candidate = dragging = false;
  }
}
