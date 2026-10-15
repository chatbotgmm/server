package org.dpdns.gmm;

import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.view.*;

/** 중앙의 빈 공간에서만 시작하는 드래그. UI 위치와 터치 영역은 움직이지 않는다. */
final class DragParallax {
  private final View owner;
  private final float slop, limit;
  private float downX, downY;
  private boolean candidate, dragging;
  private ValueAnimator returning;
  float x, y;

  DragParallax(View owner, float limit) {
    this.owner = owner;
    this.limit = limit;
    slop = ViewConfiguration.get(owner.getContext()).getScaledTouchSlop();
  }

  boolean intercept(MotionEvent e, RectF region, boolean enabled) {
    if (!motionAllowed()) {
      close();
      return false;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      candidate = enabled && region.contains(e.getX(), e.getY());
      dragging = false;
      downX = e.getX();
      downY = e.getY();
      if (candidate && returning != null) returning.cancel();
    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && candidate) {
      if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) dragging = true;
      return dragging;
    } else if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
      release();
      candidate = false;
    }
    return false;
  }

  boolean touch(MotionEvent e) {
    if (!motionAllowed()) {
      close();
      return false;
    }
    if (!candidate) return false;
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) dragging = true;
        if (dragging) {
          x = bounded((e.getX() - downX) * .15f, limit);
          y = bounded((e.getY() - downY) * .15f, limit * .65f);
          owner.postInvalidateOnAnimation();
        }
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        boolean wasDragging = dragging;
        release();
        candidate = false;
        if (!wasDragging && e.getActionMasked() == MotionEvent.ACTION_UP) owner.performClick();
        break;
    }
    return true;
  }

  private float bounded(float value, float bound) {
    return Math.max(-bound, Math.min(bound, value));
  }

  void release() {
    if (!motionAllowed()) {
      close();
      owner.invalidate();
      return;
    }
    if (returning != null) returning.cancel();
    final float fromX = x, fromY = y;
    returning = ValueAnimator.ofFloat(1, 0);
    returning.setDuration(260);
    returning.addUpdateListener(
        a -> {
          if (!motionAllowed()) {
            close();
            owner.invalidate();
            return;
          }
          float fraction = (float) a.getAnimatedValue();
          x = fromX * fraction;
          y = fromY * fraction;
          owner.postInvalidateOnAnimation();
        });
    returning.start();
    dragging = false;
  }

  private boolean motionAllowed() {
    if (!(owner.getContext() instanceof MainActivity)) return true;
    MainActivity host = (MainActivity) owner.getContext();
    return SceneMotion.enabled(host) && host.motionResumed;
  }

  void close() {
    if (returning != null) returning.cancel();
    x = y = 0;
    candidate = dragging = false;
  }
}
