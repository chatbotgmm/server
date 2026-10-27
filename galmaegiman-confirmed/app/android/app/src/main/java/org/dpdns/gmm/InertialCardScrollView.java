package org.dpdns.gmm;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;

/** Native velocity-based scrolling shared by card rails. Never snaps to a card/page. */
class InertialCardScrollView extends HorizontalScrollView {
  private final int touchSlop;
  // Platform minimum fling speed is density-scaled: a technical click/stop
  // boundary, not a change to native acceleration, friction or release velocity.
  private final int clickSpeedLimit;
  private float downX, downY;
  private boolean touching, moved, stoppingFling;
  private long suppressUntil, velocitySampleAt, lastComputeAt;
  private float scrollSpeed;
  // Integer scroll offsets need several frame samples to avoid treating a
  // one-pixel tail step as a new fast fling. This is an observation window,
  // never a delay before a new tap is accepted.
  private static final long SPEED_WINDOW_MS = 48;
  private final int[] motionX = new int[32];
  private final long[] motionAt = new long[32];
  private int motionCount, motionNext;

  InertialCardScrollView(Context context) {
    super(context);
    ViewConfiguration configuration = ViewConfiguration.get(context);
    touchSlop = configuration.getScaledTouchSlop();
    clickSpeedLimit = configuration.getScaledMinimumFlingVelocity();
    setHorizontalScrollBarEnabled(false);
    setClipToPadding(false);
  }

  /** Gate card taps and explicit action buttons while a drag/fling is in progress. */
  boolean blocksClick() {
    return moved || stoppingFling || (!touching && Math.abs(scrollSpeed) > clickSpeedLimit)
        || SystemClock.uptimeMillis() < suppressUntil;
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      // Observe this frame before aborting. Gesture intent must retain the
      // pre-DOWN speed even though native DOWN immediately sets velocity to zero.
      computeScroll();
      boolean fast = Math.abs(scrollSpeed) > clickSpeedLimit;
      // Native interception otherwise captures even an unfinished slow fling.
      // Abort/reset first so a low-speed real DOWN can reach the card child.
      stopMotion();
      stoppingFling = fast;
      touching = true;
      moved = false;
      // A new gesture is a deliberate tap. Keep the previous release's posted
      // click blocked until this DOWN, rather than swallowing a second tap too.
      suppressUntil = 0;
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
    // A released edge glow also makes native DOWN interception return true,
    // although the content is stationary. Keep a low-speed tap with its child.
    if (!stoppingFling && event.getActionMasked() == MotionEvent.ACTION_DOWN) return false;
    if (!stoppingFling && event.getActionMasked() == MotionEvent.ACTION_MOVE && !moved) return false;
    // The first touch stops a moving rail. It must never activate the card underneath.
    return intercepted || stoppingFling;
  }

  @Override public void fling(int velocityX) {
    super.fling(velocityX);
    beginMotionObservation(velocityX);
  }

  private void beginMotionObservation(float initialSpeed) {
    motionCount = motionNext = 0;
    velocitySampleAt = SystemClock.uptimeMillis();
    scrollSpeed = initialSpeed;
    recordMotion(velocitySampleAt, getScrollX());
  }

  /** Seed final native smoothScrollBy at its actual start, excluding earlier idle time. */
  void smoothScrollCardsBy(int distance) {
    beginMotionObservation(0);
    int before = getScrollX();
    smoothScrollBy(distance, 0);
    // Native repeated-arrow handling can apply an immediate scrollBy instead.
    // That completed jump must not be mistaken for a later moving trajectory.
    if (getScrollX() != before) { motionCount = motionNext = 0; scrollSpeed = 0; }
  }

  @Override public void computeScroll() {
    int before = getScrollX();
    super.computeScroll();
    long now = SystemClock.uptimeMillis();
    // smoothScrollBy/To are final native methods and do not call fling().
    // Observe their actual displacement through this same frame path.
    if (motionCount == 0 && getScrollX() != before && lastComputeAt > 0 && now > lastComputeAt)
      recordMotion(lastComputeAt, before);
    if (motionCount > 0 && now > velocitySampleAt) {
      recordMotion(now, getScrollX());
      int first = (motionNext - motionCount + motionAt.length) % motionAt.length;
      int anchor = first;
      for (int i = 0; i < motionCount; i++) {
        int index = (first + i) % motionAt.length;
        if (motionAt[index] <= now - SPEED_WINDOW_MS) anchor = index;
        else break;
      }
      long elapsed = now - motionAt[anchor];
      if (elapsed > 0) scrollSpeed = (getScrollX() - motionX[anchor]) * 1000f / elapsed;
      // A completed native edge spring can leave older fast samples behind.
      // Only an actual unchanged frame at the legal boundary clears them;
      // interior sub-pixel frames still use the observation window above.
      int range = getChildCount() == 0 ? 0 : Math.max(0,
          getChildAt(0).getWidth() - getWidth() + getPaddingLeft() + getPaddingRight());
      if (getScrollX() == before && (getScrollX() == 0 || getScrollX() == range)) scrollSpeed = 0;
      velocitySampleAt = now;
    }
    lastComputeAt = now;
  }

  private void recordMotion(long at, int x) {
    motionAt[motionNext] = at; motionX[motionNext] = x;
    motionNext = (motionNext + 1) % motionAt.length;
    motionCount = Math.min(motionCount + 1, motionAt.length);
  }

  void stopMotion() {
    // The native DOWN path aborts the old trajectory. A zero-velocity fling still
    // has a pending frame and can undo a later restored/programmatic offset.
    long now = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0);
    MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    try { super.onTouchEvent(down); super.onTouchEvent(cancel); }
    finally { down.recycle(); cancel.recycle(); }
    scrollSpeed = 0;
    motionCount = motionNext = 0;
  }

  @Override protected void onDetachedFromWindow() {
    stopMotion();
    super.onDetachedFromWindow();
  }
}
