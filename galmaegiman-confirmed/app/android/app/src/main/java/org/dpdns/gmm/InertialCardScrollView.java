package org.dpdns.gmm;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;

/** Native velocity-based scrolling shared by card rails. Never snaps to a card/page. */
class InertialCardScrollView extends HorizontalScrollView {
  private final int touchSlop;
  private float downX, downY;
  private boolean touching, moved, stoppingFling, moving;
  private long lastScrollAt, suppressUntil;
  private final Runnable idle = new Runnable() {
    @Override public void run() {
      long remaining = 96 - (SystemClock.uptimeMillis() - lastScrollAt);
      if (remaining > 0) postDelayed(this, remaining);
      else moving = false;
    }
  };

  InertialCardScrollView(Context context) {
    super(context);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    setHorizontalScrollBarEnabled(false);
    setClipToPadding(false);
  }

  /** Gate card taps and explicit action buttons while a drag/fling is in progress. */
  boolean blocksClick() {
    return moved || stoppingFling || moving || SystemClock.uptimeMillis() < suppressUntil;
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      stoppingFling = moving;
      touching = true;
      moved = false;
      downX = event.getX(); downY = event.getY();
    } else if (action == MotionEvent.ACTION_MOVE) {
      float dx = event.getX() - downX, dy = event.getY() - downY;
      if (dx * dx + dy * dy > touchSlop * touchSlop) moved = true;
    } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
      moved = true;
    }
    boolean handled = super.dispatchTouchEvent(event);
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      if (moved || stoppingFling || action == MotionEvent.ACTION_CANCEL)
        suppressUntil = SystemClock.uptimeMillis() + 160;
      touching = false;
      moved = false;
      stoppingFling = false;
    }
    return handled;
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent event) {
    boolean intercepted = super.onInterceptTouchEvent(event);
    // The first touch stops a moving rail. It must never activate the card underneath.
    return intercepted || stoppingFling;
  }

  @Override public void fling(int velocityX) {
    super.fling(velocityX);
    if (velocityX != 0) markMoving();
  }

  @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
    super.onScrollChanged(x, y, oldX, oldY);
    if (x != oldX && !touching) markMoving();
  }

  private void markMoving() {
    moving = true;
    lastScrollAt = SystemClock.uptimeMillis();
    removeCallbacks(idle);
    postDelayed(idle, 96);
  }

  void stopMotion() {
    // The native DOWN path aborts the old trajectory. A zero-velocity fling still
    // has a pending frame and can undo a later restored/programmatic offset.
    long now = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0);
    MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    try { super.onTouchEvent(down); super.onTouchEvent(cancel); }
    finally { down.recycle(); cancel.recycle(); }
    moving = false;
    removeCallbacks(idle);
  }

  @Override protected void onDetachedFromWindow() {
    stopMotion();
    super.onDetachedFromWindow();
  }
}
