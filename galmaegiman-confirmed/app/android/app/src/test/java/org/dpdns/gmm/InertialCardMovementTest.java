package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.HorizontalScrollView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/** Real child/parent MotionEvents and native scrollX frames, not finalX-only physics checks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class InertialCardMovementTest {
  @Before public void clock() { new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock(); }

  final class Rail implements AutoCloseable {
    final ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible();
    final Activity host = controller.get();
    final FrameLayout root = new FrameLayout(host);
    final HorizontalScrollView rail;
    final LinearLayout row = new LinearLayout(host);
    final AtomicInteger clicks = new AtomicInteger(), cancels = new AtomicInteger();
    final float density = host.getResources().getDisplayMetrics().density;
    final JSONArray frames = new JSONArray();
    long down;
    Rail() { this(false); }
    Rail(boolean plainNative) {
      rail = plainNative ? new HorizontalScrollView(host) : new InertialCardScrollView(host);
      row.setOrientation(LinearLayout.HORIZONTAL);
      for (int i = 0; i < 40; i++) {
        Button card = new Button(host); card.setText("대원 " + i);
        card.setOnClickListener(v -> {
          if (!(rail instanceof InertialCardScrollView) || !((InertialCardScrollView) rail).blocksClick()) clicks.incrementAndGet();
        });
        card.setOnTouchListener((v, event) -> {
          if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancels.incrementAndGet();
          return false;
        });
        row.addView(card, new LinearLayout.LayoutParams(dp(160), -1));
      }
      rail.addView(row, new FrameLayout.LayoutParams(-2, -1));
      root.addView(rail, new FrameLayout.LayoutParams(-1, -1)); host.setContentView(root);
      layout(); advance(200);
    }
    int dp(float value) { return Math.round(value * density); }
    int max() { return row.getWidth() - rail.getWidth(); }
    OverScroller scroller() { return ReflectionHelpers.getField(rail, "mScroller"); }
    void layout() {
      root.measure(View.MeasureSpec.makeMeasureSpec(dp(915), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(dp(180), View.MeasureSpec.EXACTLY));
      root.layout(0, 0, dp(915), dp(180));
    }
    void advance(int millis) {
      ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS); rail.computeScroll();
      if (root.isLayoutRequested()) layout();
      frames.put(MainActivity.obj("at", SystemClock.uptimeMillis(), "x", rail.getScrollX(),
          "nativeVelocityPxPerSecond", scroller().getCurrVelocity()));
    }
    void event(int action, float xDp, int afterMillis) {
      if (afterMillis > 0) advance(afterMillis);
      long now = SystemClock.uptimeMillis(); if (action == MotionEvent.ACTION_DOWN) down = now;
      MotionEvent event = MotionEvent.obtain(down, now, action, dp(xDp), dp(100), 0);
      try { root.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    void tap(float x) {
      event(MotionEvent.ACTION_DOWN, x, 0); event(MotionEvent.ACTION_UP, x, 16); advance(1);
    }
    void center() {
      if (rail instanceof InertialCardScrollView) ((InertialCardScrollView) rail).stopMotion();
      rail.scrollTo(max() / 2, 0); advance(200);
    }
    void settle() { for (int i = 0; i < 240 && !scroller().isFinished(); i++) advance(16); advance(200); }
    void trace(String name) throws Exception {
      Path path = Path.of(System.getProperty("gmm.nativeReviewOutput"), "scroll-movement");
      Files.createDirectories(path);
      Files.write(path.resolve(name + ".json"), MainActivity.obj("density", density,
          "maxScroll", max(), "clock", "Robolectric synthetic frame clock; not device FPS", "frames", frames).toString(2).getBytes(StandardCharsets.UTF_8));
    }
    @Override public void close() { controller.close(); }
  }

  @Test public void fingerTrackingReversesDirectionAndNativeReleaseContinuesAcrossFrames() throws Exception {
    try (Rail f = new Rail()) {
      f.center(); int before = f.rail.getScrollX();
      int slop = ViewConfiguration.get(f.host).getScaledTouchSlop();
      f.event(MotionEvent.ACTION_DOWN, 650, 0);
      f.event(MotionEvent.ACTION_MOVE, 650 - slop / f.density / 2, 16);
      assertEquals("Sub-slop jitter does not move the row", before, f.rail.getScrollX());
      f.event(MotionEvent.ACTION_MOVE, 590, 16); int first = f.rail.getScrollX();
      f.event(MotionEvent.ACTION_MOVE, 530, 16); int second = f.rail.getScrollX();
      assertTrue("After native slop ownership transfer, leftward finger motion moves content left", first >= before && second > first);
      assertEquals("The next 60dp finger move is followed without losing more distance", f.dp(60), second - first);
      assertTrue("The actual clickable child receives CANCEL on drag", f.cancels.get() > 0);
      f.event(MotionEvent.ACTION_MOVE, 570, 16);
      assertTrue("Direction reversal follows the same finger immediately", f.rail.getScrollX() < second);
      f.event(MotionEvent.ACTION_MOVE, 510, 16);
      f.event(MotionEvent.ACTION_MOVE, 450, 16);
      f.event(MotionEvent.ACTION_UP, 390, 16); int released = f.rail.getScrollX();
      assertTrue("Real release velocity is positive after a leftward swipe", f.scroller().getFinalX() > released);
      int last = released, changes = 0;
      for (int frame = 0; frame < 20; frame++) {
        f.advance(16); int x = f.rail.getScrollX();
        assertTrue("No frame jumps backwards during an interior forward fling", x >= last);
        if (x > last) changes++; last = x;
      }
      assertTrue("Release keeps moving over consecutive native frames", changes >= 5);
      assertEquals("A drag never becomes a click", 0, f.clicks.get()); f.trace("drag-reverse-release");
    }
  }

  @Test public void firstCrossSlopOwnershipTransferMatchesUnmodifiedNativeScrollView() throws Exception {
    List<Integer> deltas = new ArrayList<>();
    for (boolean plain : new boolean[] {true, false}) try (Rail f = new Rail(plain)) {
      f.center(); int before = f.rail.getScrollX();
      f.event(MotionEvent.ACTION_DOWN, 650, 0);
      f.event(MotionEvent.ACTION_MOVE, 590, 16); deltas.add(f.rail.getScrollX() - before);
      int first = f.rail.getScrollX(); f.event(MotionEvent.ACTION_MOVE, 530, 16);
      deltas.add(f.rail.getScrollX() - first);
      f.event(MotionEvent.ACTION_CANCEL, 530, 16);
    }
    assertEquals("Initial slop transfer uses the same native handling", deltas.get(0), deltas.get(2));
    assertEquals("Subsequent displacement matches unmodified native scrolling", deltas.get(1), deltas.get(3));
    assertTrue(deltas.get(3) > 0);
  }

  @Test public void aNewDragInterruptsFlingAndMovesImmediatelyInTheOppositeDirection() throws Exception {
    try (Rail f = new Rail()) {
      f.center(); f.rail.fling(f.dp(1800)); f.advance(32); int before = f.rail.getScrollX();
      f.event(MotionEvent.ACTION_DOWN, 400, 0);
      f.event(MotionEvent.ACTION_MOVE, 460, 16); int first = f.rail.getScrollX();
      f.event(MotionEvent.ACTION_MOVE, 520, 16);
      assertTrue("Re-touch must not trap the next drag", first < before && f.rail.getScrollX() < first);
      f.event(MotionEvent.ACTION_UP, 580, 16); int released = f.rail.getScrollX(); f.advance(16);
      assertTrue("Reversed release keeps the reversed sign", f.rail.getScrollX() < released);
      assertEquals(0, f.clicks.get()); f.trace("reverse-during-fling");
    }
  }

  @Test public void nativeReleaseVelocityKeepsDirectionAndPlatformMaximumClamp() throws Exception {
    for (int direction : new int[] {1, -1}) {
      List<String> nativeResults = new ArrayList<>();
      for (boolean plain : new boolean[] {true, false}) try (Rail f = new Rail(plain)) {
        f.center(); float start = direction > 0 ? 800 : 100;
        f.event(MotionEvent.ACTION_DOWN, start, 0);
        for (int i = 1; i <= 3; i++) f.event(MotionEvent.ACTION_MOVE, start - direction * i * 160, 8);
        f.event(MotionEvent.ACTION_UP, start - direction * 640, 8);
        int released = f.rail.getScrollX(); f.advance(8);
        int max = ViewConfiguration.get(f.host).getScaledMaximumFlingVelocity();
        nativeResults.add("plain="+plain+" direction="+direction+" release="+released
            +" final="+f.scroller().getFinalX()+" velocity="+f.scroller().getCurrVelocity()+" cap="+max);
        assertTrue("Native release clamps an extreme 125Hz swipe: "+nativeResults,
            f.scroller().getCurrVelocity() <= max + 1);
        assertTrue("Release preserves the finger's opposite content direction: "+nativeResults,
            direction * (f.scroller().getFinalX() - released) > 0);
        assertEquals(0, f.clicks.get());
      }
    }
  }

  @Test public void cancelAndBothEdgesSettleWithoutReturningToAnOldOffset() throws Exception {
    try (Rail f = new Rail()) {
      f.center(); f.event(MotionEvent.ACTION_DOWN, 650, 0);
      f.event(MotionEvent.ACTION_MOVE, 550, 16); f.event(MotionEvent.ACTION_CANCEL, 550, 16);
      int cancelled = f.rail.getScrollX(); f.advance(400);
      assertEquals("CANCEL cannot launch the cancelled gesture's fling", cancelled, f.rail.getScrollX());
      f.rail.scrollTo(f.max() - f.dp(10), 0); f.rail.fling(f.dp(8000)); f.settle();
      assertEquals("Native right overscroll springs back to the right edge", f.max(), f.rail.getScrollX());
      f.rail.fling(-f.dp(8000)); f.settle();
      assertEquals("Native left overscroll springs back to zero", 0, f.rail.getScrollX());
      f.tap(80); assertEquals("A settled edge still accepts a tap", 1, f.clicks.get());
      f.trace("cancel-and-edges");
    }
  }

  @Test public void lowSpeedUnfinishedNativeFlingAllowsTheCurrentTap() throws Exception {
    assertLowSpeed(16);
  }

  void assertLowSpeed(int interval) throws Exception {
    try (Rail f = new Rail()) {
      f.center(); f.rail.fling(f.dp(1800));
      f.advance(interval); f.advance(interval);
      long started = SystemClock.uptimeMillis(); int frameCount = 2;
      float threshold = ViewConfiguration.get(f.host).getScaledMinimumFlingVelocity();
      for (int i = 0; i < 300 && f.scroller().getCurrVelocity() > threshold * .6f; i++) { f.advance(interval); frameCount++; }
      assertFalse("Exercise an unfinished native fling, not an already idle rail", f.scroller().isFinished());
      assertTrue(f.scroller().getCurrVelocity() <= threshold * .6f);
      float nativeBefore = f.scroller().getCurrVelocity();
      float observedBefore = ReflectionHelpers.getField(f.rail, "scrollSpeed");
      f.event(MotionEvent.ACTION_DOWN, 650, 0);
      boolean stopping = ReflectionHelpers.getField(f.rail, "stoppingFling");
      f.event(MotionEvent.ACTION_UP, 650, 16); f.advance(1);
      f.trace("low-speed-click-" + interval + "ms-" + f.density + "x");
      assertEquals("A low-speed first tap is a click immediately: native=" + nativeBefore
          + " observed=" + observedBefore + " limit=" + threshold + " stop=" + stopping
          + " frames=" + frameCount + " elapsed=" + (SystemClock.uptimeMillis() - started), 1, f.clicks.get());
    }
  }

  @Test public void fastNativeSmoothScrollingUsesTheSameStopThenClickRule() throws Exception {
    try (Rail f = new Rail()) {
      f.center();
      ShadowLooper.idleMainLooper(5000, TimeUnit.MILLISECONDS);
      ((InertialCardScrollView) f.rail).smoothScrollCardsBy(f.dp(400));
      f.advance(16); f.advance(16);
      assertTrue("Native smooth scrolling is actually moving", f.scroller().getCurrX() != f.max() / 2);
      f.tap(650); assertEquals("A fast native smooth-scroll tap only stops", 0, f.clicks.get());
      f.tap(650); assertEquals("The next stationary tap clicks immediately", 1, f.clicks.get());
      f.trace("smooth-scroll-stop-click");
    }
  }

  @Test public void highSpeedDownStaysStopOnlyEvenThoughNativeDownAbortsVelocity() throws Exception {
    try (Rail f = new Rail()) {
      f.center(); f.rail.fling(f.dp(1800)); f.advance(32);
      f.tap(650); assertEquals("High-speed DOWN owns stop-only intent for its full gesture", 0, f.clicks.get());
      assertTrue(f.scroller().isFinished());
      f.tap(650); assertEquals("A separate stopped tap has no fixed dead time", 1, f.clicks.get());
      f.trace("fast-stop-then-click");
    }
  }

  @Test public void stationaryEdgeTapDoesNotInheritEarlierHighSpeedSamples() throws Exception {
    try (Rail f = new Rail()) {
      f.rail.scrollTo(f.max() - f.dp(10), 0); f.rail.fling(f.dp(8000));
      for (int i = 0; i < 240 && !f.scroller().isFinished(); i++) f.advance(16);
      assertTrue(f.scroller().isFinished()); assertEquals(f.max(), f.rail.getScrollX());
      f.tap(80);
      assertEquals("Current stationary edge accepts the first tap without an added cooldown", 1, f.clicks.get());
      f.trace("immediate-stationary-edge-click");
    }
  }

  @Test @Config(qualifiers = "w915dp-h412dp-land-xhdpi")
  public void lowSpeedThresholdUsesDensityScaledNativeMotion() throws Exception {
    assertLowSpeed(16);
  }

  @Test public void lowSpeedRuleUsesElapsedTimeAt8And33msFrameIntervals() throws Exception {
    assertLowSpeed(8);
    assertLowSpeed(33);
  }
}
