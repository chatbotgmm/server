package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Real home artwork, fixed feet, bounded drag, system preference and teardown regressions. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReleaseHomeMotionTest {
  final ReleaseHomeLayoutTest fixture = new ReleaseHomeLayoutTest();

  @Before public void setup() { fixture.isolate(); }
  @After public void restore() { fixture.restoreDisplay(); }

  @Test public void criticalReturnIsSlowBoundedAndNeverCrossesRest() {
    assertEquals(1, HomeGullDrag.remaining(0), 0);
    assertEquals(0, HomeGullDrag.remaining(1), .000001f);
    assertTrue(HomeGullDrag.RETURN_MILLIS >= 1000);
    assertTrue("Weighted response remains visible after 110ms", HomeGullDrag.remaining(.1f) > .75f);
    float previous = 1;
    for (int step = 0; step <= 1000; step++) {
      float current = HomeGullDrag.remaining(step / 1000f);
      assertTrue(current >= 0 && current <= previous);
      previous = current;
    }
    assertEquals(4, HomeGullDrag.bounded(10000, 4), 0);
    assertEquals(-4, HomeGullDrag.bounded(-10000, 4), 0);
  }

  @Test public void meshOnlyMovesWaterVerticesAndRetainsItsBuffers() {
    HomeWater water = new HomeWater();
    assertTrue(water.activeCount > 10);
    float[] original = water.base.clone(), buffer = water.vertices;
    for (double seconds : new double[] {0, .25, 2, 90, 10000}) {
      water.update(seconds);
      assertSame(buffer, water.vertices);
      assertArrayEquals(original, water.base, 0);
      for (int i = 0; i < original.length; i += 2) {
        float x = original[i], y = original[i + 1];
        if (!HomeWater.contains(x, y)) {
          assertEquals(x, water.vertices[i], 0);
          assertEquals(y, water.vertices[i + 1], 0);
        }
        assertTrue(Math.abs(water.vertices[i] - x) <= 2.35f);
        assertTrue(Math.abs(water.vertices[i + 1] - y) <= 2.88f);
      }
    }
    for (float[] landmark : new float[][] {
        {1200, 140}, {990, 396}, {1300, 380}, {1389, 520},
        {1470, 490}, {1664, 407}, {1117, 551}, {700, 600}, {1000, 720}})
      assertFalse("Landmark is outside water mask", HomeWater.contains(landmark[0], landmark[1]));
  }

  @Test public void realArtChangesOnlyInsideWaterAndReducedMotionIsPixelStable() {
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      HomeArt art = fixture.lobby(host).homeArt;
      Bitmap first = Bitmap.createBitmap(HomeWater.WIDTH, HomeWater.HEIGHT, Bitmap.Config.ARGB_8888);
      Bitmap second = Bitmap.createBitmap(HomeWater.WIDTH, HomeWater.HEIGHT, Bitmap.Config.ARGB_8888);
      Bitmap still = Bitmap.createBitmap(HomeWater.WIDTH, HomeWater.HEIGHT, Bitmap.Config.ARGB_8888);
      Bitmap expectedWater = rasterizedWaterMask();
      try {
        art.background(new Canvas(still), still.getWidth(), still.getHeight(), 0, false);
        art.background(new Canvas(first), first.getWidth(), first.getHeight(), .2, true);
        art.background(new Canvas(second), second.getWidth(), second.getHeight(), 2.7, true);
        int changedWater = 0;
        for (int y = 0; y < first.getHeight(); y++)
          for (int x = 0; x < first.getWidth(); x++) {
            if (first.getPixel(x, y) == second.getPixel(x, y)) continue;
            // Use native clip coverage, including narrow polygon corners. Four
            // diagonal point samples are not equivalent to a rasterized pixel.
            assertTrue("Only water pixels animate: " + x + "," + y,
                Color.alpha(expectedWater.getPixel(x, y)) > 0);
            changedWater++;
          }
        assertTrue("Water visibly changes between frames", changedWater > 100);
        for (int[] landmark : new int[][] {
            {1200, 140}, {990, 396}, {1300, 380}, {1389, 520},
            {1470, 490}, {1664, 407}, {1117, 551}, {700, 600}, {1000, 720}}) {
          int x = landmark[0], y = landmark[1];
          assertEquals("Structure/foreground landmark is outside the rasterized water mask",
              0, Color.alpha(expectedWater.getPixel(x, y)));
          assertEquals("First animated frame preserves the original landmark",
              still.getPixel(x, y), first.getPixel(x, y));
          assertEquals("Second animated frame preserves the original landmark",
              still.getPixel(x, y), second.getPixel(x, y));
        }
        art.background(new Canvas(first), first.getWidth(), first.getHeight(), 0, false);
        art.background(new Canvas(still), still.getWidth(), still.getHeight(), 55, false);
        assertTrue("Platform-reduced motion draws a stable original", first.sameAs(still));
      } finally { first.recycle(); second.recycle(); still.recycle(); expectedWater.recycle(); }
    }
  }

  private Bitmap rasterizedWaterMask() {
    Bitmap coverage = Bitmap.createBitmap(HomeWater.WIDTH, HomeWater.HEIGHT, Bitmap.Config.ARGB_8888);
    Path expected = new Path();
    // Build independently from the approved polygon coordinates, without calling
    // HomeWater.draw/update or expanding their boundary by an arbitrary tolerance.
    for (float[] polygon : HomeWater.POLYGONS) {
      expected.moveTo(polygon[0], polygon[1]);
      for (int i = 2; i < polygon.length; i += 2) expected.lineTo(polygon[i], polygon[i + 1]);
      expected.close();
    }
    Canvas canvas = new Canvas(coverage);
    canvas.clipPath(expected);
    canvas.drawColor(Color.WHITE);
    return coverage;
  }

  @Test public void dragKeepsFeetAndShadowFixedAndCancelsOnPauseSystemOffAndDetach() {
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      float originalScale = Settings.Global.getFloat(host.getContentResolver(),
          Settings.Global.ANIMATOR_DURATION_SCALE, 1);
      try {
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        host.motionEnabled = true;
        host.journalTime.motionPreferenceChanged();
        lobby.getViewTreeObserver().dispatchOnPreDraw();
        assertNotNull(lobby.motion.clock);
        RectF feet = lobby.heroBox();
        RectF shadow = lobby.heroShadowBox(feet);
        drag(lobby, feet, 500, 200);
        assertTrue(Math.abs(lobby.parallax.x) <= host.dp(4));
        assertTrue(Math.abs(lobby.parallax.y) <= host.dp(1.2f));
        assertTrue(lobby.parallax.x > 0);
        RectF pose = lobby.heroDrawBox(1, lobby.parallax.x, lobby.parallax.y);
        assertEquals(feet.bottom, pose.bottom, 0);
        assertEquals(feet.centerX(), pose.centerX(), .001f);
        assertEquals(shadow, lobby.heroShadowBox(lobby.heroBox()));
        lobby.parallax.release();
        assertNotNull(lobby.parallax.returning);
        controller.pause();
        assertEquals(0, lobby.parallax.x, 0);
        assertEquals(0, lobby.parallax.y, 0);
        assertNull(lobby.parallax.returning);
        assertNull(lobby.motion.clock);
        controller.resume();
        lobby.getViewTreeObserver().dispatchOnPreDraw();
        drag(lobby, feet, -500, -200);
        lobby.parallax.release();
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40));
        lobby.getViewTreeObserver().dispatchOnPreDraw();
        Bitmap capture = Bitmap.createBitmap(915, 412, Bitmap.Config.ARGB_8888);
        try { lobby.draw(new Canvas(capture)); } finally { capture.recycle(); }
        assertEquals(0, lobby.parallax.x, 0);
        assertNull(lobby.parallax.returning);
        assertNull(lobby.motion.clock);
        NativeSettingsSheet.show(host);
        assertNull(host.visibleDialog.getWindow().getDecorView().findViewWithTag("settings-reduced-motion"));
        host.visibleDialog.dismiss();
        // Detach must stop active clocks, not merely leave already-stopped ones null.
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        host.journalTime.motionPreferenceChanged();
        lobby.getViewTreeObserver().dispatchOnPreDraw();
        drag(lobby, feet, 500, 200);
        lobby.parallax.release();
        assertNotNull(lobby.parallax.returning);
        assertNotNull(lobby.motion.clock);
        host.root.removeView(lobby);
        assertNull(lobby.parallax.returning);
        assertNull(lobby.motion.clock);
        assertFalse(lobby.isAttachedToWindow());
      } finally {
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, originalScale);
      }
    }
  }

  @Test public void regrabCancelAndSecondPointerDoNotLeaveAnActiveDrag() {
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      float originalScale = Settings.Global.getFloat(host.getContentResolver(),
          Settings.Global.ANIMATOR_DURATION_SCALE, 1);
      try {
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1);
        host.motionEnabled = true;
        host.journalTime.motionPreferenceChanged();
        lobby.getViewTreeObserver().dispatchOnPreDraw();
        RectF area = lobby.heroBox();
        for (int terminal : new int[] {MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN}) {
          drag(lobby, area, 500, 200);
          lobby.parallax.release();
          assertNotNull(lobby.parallax.returning);
          float previousX = lobby.parallax.x, previousY = lobby.parallax.y;
          MotionEvent down = MotionEvent.obtain(0, 110, MotionEvent.ACTION_DOWN,
              area.centerX(), area.centerY(), 0);
          MotionEvent stop = terminal == MotionEvent.ACTION_POINTER_DOWN
              ? secondPointer(area) : MotionEvent.obtain(0, 120, terminal,
                  area.centerX(), area.centerY(), 0);
          MotionEvent move = MotionEvent.obtain(0, 130, MotionEvent.ACTION_MOVE,
              area.centerX() - 500, area.centerY() - 200, 0);
          try {
            assertFalse(lobby.parallax.intercept(down, area, true));
            assertNull("Re-grab cancels the previous return", lobby.parallax.returning);
            assertEquals(previousX, lobby.parallax.x, 0);
            assertEquals(previousY, lobby.parallax.y, 0);
            assertTrue(lobby.parallax.touch(stop));
            assertNotNull("Interrupted pose returns gently to rest", lobby.parallax.returning);
            assertFalse("Cancelled gesture cannot resume from a later move", lobby.parallax.touch(move));
          } finally { down.recycle(); stop.recycle(); move.recycle(); }
          lobby.parallax.close();
        }
      } finally {
        Settings.Global.putFloat(host.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, originalScale);
      }
    }
  }

  private MotionEvent secondPointer(RectF area) {
    MotionEvent.PointerProperties first = new MotionEvent.PointerProperties();
    first.id = 0;
    first.toolType = MotionEvent.TOOL_TYPE_FINGER;
    MotionEvent.PointerProperties second = new MotionEvent.PointerProperties();
    second.id = 1;
    second.toolType = MotionEvent.TOOL_TYPE_FINGER;
    MotionEvent.PointerCoords a = new MotionEvent.PointerCoords();
    a.x = area.centerX(); a.y = area.centerY(); a.pressure = a.size = 1;
    MotionEvent.PointerCoords b = new MotionEvent.PointerCoords();
    b.x = a.x + 10; b.y = a.y + 10; b.pressure = b.size = 1;
    return MotionEvent.obtain(0, 120,
        MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
        new MotionEvent.PointerProperties[] {first, second},
        new MotionEvent.PointerCoords[] {a, b}, 0, 0, 1, 1, 0, 0,
        android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
  }

  private void drag(LobbyView lobby, RectF area, float dx, float dy) {
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, area.centerX(), area.centerY(), 0);
    MotionEvent move = MotionEvent.obtain(0, 100, MotionEvent.ACTION_MOVE,
        area.centerX() + dx, area.centerY() + dy, 0);
    try {
      assertFalse(lobby.parallax.intercept(down, area, true));
      assertTrue(lobby.parallax.intercept(move, area, true));
      assertTrue(lobby.parallax.touch(move));
    } finally { down.recycle(); move.recycle(); }
  }
}
