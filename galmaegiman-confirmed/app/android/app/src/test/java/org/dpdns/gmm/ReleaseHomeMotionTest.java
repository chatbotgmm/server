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

  float originalAnimationScale;
  @Before public void setup() {
    fixture.isolate();
    var resolver = org.robolectric.RuntimeEnvironment.getApplication().getContentResolver();
    originalAnimationScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1);
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1);
  }
  @After public void restore() {
    Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, originalAnimationScale);
    fixture.restoreDisplay();
  }

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
        assertTrue(Math.abs(water.vertices[i] - x) <= 5.47f);
        assertTrue(Math.abs(water.vertices[i + 1] - y) <= 6.51f);
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

  @Test public void blankCenterDragUsesRealDispatchAndControlsKeepTheirGestures() throws Exception {
    try (var controller = fixture.start(true)) {
      var host = controller.get();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      host.motionEnabled = true;
      host.journalTime.motionPreferenceChanged();
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      float x = lobby.getWidth() / 2f, y = lobby.getHeight() / 2f;
      assertFalse("The tested point is blank, not the character", lobby.heroBox().contains(x, y));
      assertTrue(lobby.sceneDragStart(x, y));
      saveHomeFrame(lobby, "home-drag-center-rest");
      assertTrue(dispatch(lobby, MotionEvent.ACTION_DOWN, x, y));
      dispatch(lobby, MotionEvent.ACTION_MOVE, x + 90, y + 30);
      assertTrue("Blank scene drag moves the weighted pose", lobby.parallax.x > 0);
      float dragged = lobby.parallax.x;
      saveHomeFrame(lobby, "home-drag-center-held");
      dispatch(lobby, MotionEvent.ACTION_CANCEL, x + 90, y + 30);
      assertNotNull(lobby.parallax.returning);
      advanceFrames(160);
      assertTrue("Heavy return stays on its original side", lobby.parallax.x > 0);
      assertTrue("Actual timed return reduces the offset", lobby.parallax.x < dragged);
      saveHomeFrame(lobby, "home-drag-center-return-t0160");
      advanceFrames(1240);
      saveHomeFrame(lobby, "home-drag-center-settled");
      assertEquals(0, lobby.parallax.x, .001f);
      assertEquals(0, lobby.parallax.y, .001f);
      assertNull(lobby.parallax.returning);
      for (int i = 0; i < lobby.getChildCount(); i++) {
        View child = lobby.getChildAt(i);
        if (child == lobby.gull || child.getVisibility() != View.VISIBLE || !child.isClickable()) continue;
        float bx = (child.getLeft() + child.getRight()) / 2f;
        float by = (child.getTop() + child.getBottom()) / 2f;
        assertFalse("Interactive child excludes scene input: " + child.getTag(), lobby.sceneDragStart(bx, by));
        dispatch(lobby, MotionEvent.ACTION_DOWN, bx, by);
        dispatch(lobby, MotionEvent.ACTION_MOVE, x, y);
        assertEquals("Leaving a button cannot acquire the scene drag", 0, lobby.parallax.x, 0);
        dispatch(lobby, MotionEvent.ACTION_CANCEL, x, y);
      }
      assertFalse("Header blank gap excluded", lobby.sceneDragStart(x, lobby.boxes[7].centerY()));
      assertFalse("Footer strip excluded", lobby.sceneDragStart(x, lobby.getHeight() - 2));
      // A normal tap still goes to its original listener after all cancelled drags.
      View play = lobby.buttons.get(5);
      float px = (play.getLeft() + play.getRight()) / 2f;
      float py = (play.getTop() + play.getBottom()) / 2f;
      dispatch(lobby, MotionEvent.ACTION_DOWN, px, py);
      dispatch(lobby, MotionEvent.ACTION_UP, px, py);
      assertEquals("modes", host.requested);
    }
  }

  @Test public void timedPhoneWaterFramesAdvanceAndResumeAfterPauseAndHomeReentry() throws Exception {
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      host.motionEnabled = true;
      host.journalTime.motionPreferenceChanged();
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNotNull(lobby.motion.clock);
      advanceFrames(150);
      Bitmap first = drawTimedHome(lobby);
      double before = lobby.motion.millis;
      advanceFrames(950);
      Bitmap second = drawTimedHome(lobby);
      try {
        assertTrue("Real animation clock advances", lobby.motion.millis > before + 500);
        int visibleWaterChanges = 0;
        // Compare only source-mask water that is visibly exposed in the actual home.
        float scale = Math.max(915f / HomeWater.WIDTH, 412f / HomeWater.HEIGHT);
        float left = (915 - HomeWater.WIDTH * scale) / 2;
        float top = (412 - HomeWater.HEIGHT * scale) / 2;
        for (int y = 0; y < 412; y++) for (int x = 0; x < 915; x++) {
          if (!HomeWater.contains((x - left) / scale, (y - top) / scale)
              || !lobby.sceneDragStart(x, y) || lobby.heroBox().contains(x, y)) continue;
          int a = first.getPixel(x, y), b = second.getPixel(x, y);
          int delta = Math.abs(Color.red(a) - Color.red(b))
              + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b));
          if (delta >= 12) visibleWaterChanges++;
        }
        assertTrue("Exposed phone water has perceptible timed pixel change: " + visibleWaterChanges,
            visibleWaterChanges > 100);
        saveWaterEvidence(first, second, before, lobby.motion.millis, visibleWaterChanges);
      } finally { first.recycle(); second.recycle(); }
      controller.pause();
      assertNull(lobby.motion.clock);
      double paused = lobby.motion.millis;
      advanceFrames(500);
      assertEquals(paused, lobby.motion.millis, 0);
      controller.resume();
      lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertNotNull(lobby.motion.clock);
      advanceFrames(250);
      assertTrue(lobby.motion.millis > paused);
      // Navigation replaces the old view; the newly entered home must have its own live clock.
      host.home();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView returned = fixture.lobby(host);
      assertNotSame(lobby, returned);
      assertNull(lobby.motion.clock);
      returned.getViewTreeObserver().dispatchOnPreDraw();
      assertNotNull(returned.motion.clock);
      advanceFrames(250);
      assertTrue(returned.motion.millis > 0);
    }
  }

  @Test public void disabledHomeControlKeepsImageAndAppliesSharedToneOnce() throws Exception {
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      RectF target = new RectF(0, 0, 260, 60);
      Bitmap original = Bitmap.createBitmap(260, 60, Bitmap.Config.ARGB_8888);
      Bitmap disabled = Bitmap.createBitmap(260, 60, Bitmap.Config.ARGB_8888);
      Bitmap expected = Bitmap.createBitmap(260, 60, Bitmap.Config.ARGB_8888);
      try {
        lobby.controlSurface(new Canvas(original), 5, target);
        lobby.buttons.get(5).setEnabled(false);
        lobby.controlSurface(new Canvas(disabled), 5, target);
        Canvas canvas = new Canvas(expected);
        GamePressState tone = new GamePressState();
        tone.enabled = false;
        int saved = tone.begin(canvas, new android.graphics.Rect(0, 0, 260, 60), 1);
        lobby.homeArt.surface(canvas, "ui/button-secondary.png", target);
        tone.finish(canvas, saved);
        assertTrue("Home uses original art with exactly one shared disabled filter", expected.sameAs(disabled));
        assertFalse("Disabled state is visibly distinguished", original.sameAs(disabled));
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < 60; y++) for (int x = 0; x < 260; x++) {
          assertEquals("Original surface opacity preserved", Color.alpha(original.getPixel(x, y)),
              Color.alpha(disabled.getPixel(x, y)));
          colors.add(disabled.getPixel(x, y));
        }
        assertTrue("Ornate textured image is retained rather than a flat panel", colors.size() > 100);
        saveHomeFrame(lobby, "home-disabled-play-image");
        View play = lobby.buttons.get(5);
        float x = (play.getLeft() + play.getRight()) / 2f;
        float y = (play.getTop() + play.getBottom()) / 2f;
        dispatch(lobby, MotionEvent.ACTION_DOWN, x, y);
        dispatch(lobby, MotionEvent.ACTION_UP, x, y);
        assertEquals("Disabled home command cannot navigate", "", host.requested);
        assertFalse("Disabled command still excludes scene gestures", lobby.sceneDragStart(x, y));
      } finally { original.recycle(); disabled.recycle(); expected.recycle(); }
    }
  }

  @Test public void currencyInsetsKeepMaximumAmountsAndShopTargetsAtLargeFont() throws Exception {
    org.robolectric.RuntimeEnvironment.setFontScale(1.3f);
    try (var controller = fixture.start(false)) {
      var host = controller.get();
      for (String key : new String[] {"snack", "peanut", "credits"}) host.me().put(key, Integer.MAX_VALUE);
      fixture.layout(host, 915, 412, 0, 0, 0, 0);
      LobbyView lobby = fixture.lobby(host);
      Bitmap frame = drawTimedHome(lobby);
      try {
        for (int i = 9; i <= 11; i++) {
          RectF box = lobby.boxes[i];
          View currency = lobby.buttons.get(i);
          assertTrue("Currency keeps the full accessible touch height", currency.getHeight() >= host.dp(48));
          assertTrue(currency.getContentDescription().toString().contains("2,147,483,647"));
          assertTrue(currency.getContentDescription().toString().contains("상점"));
          assertEquals("Inset navy display replaces ornate menu surface", CurrencyDisplay.NAVY,
              frame.getPixel(Math.round(box.centerX()), Math.round(box.top + host.dp(11))));
          assertFalse("Currency control excluded from scene drag", lobby.sceneDragStart(box.centerX(), box.centerY()));
          host.requested = "";
          dispatch(lobby, MotionEvent.ACTION_DOWN, box.centerX(), box.centerY());
          dispatch(lobby, MotionEvent.ACTION_UP, box.centerX(), box.centerY());
          assertEquals("Currency still opens the shop", "shop", host.requested);
        }
        saveHomeFrame(lobby, "home-currency-max-font130");
      } finally { frame.recycle(); }
    } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f); }
  }

  @Test public void currencyMaximumAmountsKeepFourteenSpAcrossSafePhoneWidths() throws Exception {
    int[][] profiles = {{780, 360, 0, 0, 0, 0}, {915, 412, 0, 0, 0, 0},
        {1006, 411, 32, 8, 24, 16}, {1280, 720, 0, 0, 0, 0}};
    for (int[] profile : profiles) {
      org.robolectric.RuntimeEnvironment.setQualifiers(
          "w" + profile[0] + "dp-h" + profile[1] + "dp-land-mdpi");
      org.robolectric.RuntimeEnvironment.setFontScale(1.3f);
      try (var controller = fixture.start(false)) {
        var host = controller.get();
        for (String key : new String[] {"snack", "peanut", "credits"}) host.me().put(key, Integer.MAX_VALUE);
        fixture.layout(host, profile[0], profile[1], profile[2], profile[3], profile[4], profile[5]);
        LobbyView lobby = fixture.lobby(host);
        RectF safe = host.safeBounds(profile[0], profile[1]);
        float textSize = lobby.currencyTextSize();
        assertTrue("Rendered amount never shrinks below 14sp at font 130%",
            textSize + .01f >= 14 * host.getResources().getDisplayMetrics().scaledDensity);
        android.graphics.Paint text = new android.graphics.Paint();
        text.setTypeface(host.headline); text.setTextSize(textSize);
        float amountWidth = text.measureText("2,147,483,647");
        for (int i = 9; i <= 11; i++) {
          RectF box = lobby.boxes[i];
          assertTrue("Maximum amount fits without font shrinking", amountWidth <= box.width() - host.dp(38));
          assertTrue("Amount keeps safe left edge", box.left >= safe.left);
          assertTrue("Amount keeps safe right edge", box.right <= safe.right);
          assertTrue("Amount does not overlap profile", box.left >= lobby.boxes[7].right + host.dp(8) - 1);
          assertTrue("Amount does not overlap settings", box.right <= lobby.boxes[8].left - host.dp(8) + 1);
          assertTrue("Profile stays a full hit target", lobby.boxes[7].width() >= host.dp(48));
        }
        Bitmap frame = Bitmap.createBitmap(profile[0], profile[1], Bitmap.Config.ARGB_8888);
        try { lobby.draw(new Canvas(frame)); } finally { frame.recycle(); }
      }
    }
  }

  private void advanceFrames(long durationMillis) {
    // PAUSED Choreographer needs individual vsync steps; one large clock jump
    // can dispatch only the animator's first frame and hide real intermediate motion.
    for (long elapsed = 0; elapsed < durationMillis; elapsed += 16)
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(
          Duration.ofMillis(Math.min(16, durationMillis - elapsed)));
  }

  private void saveHomeFrame(LobbyView lobby, String name) throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output == null || output.isEmpty()) return;
    java.io.File directory = new java.io.File(output);
    if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create " + output);
    Bitmap frame = drawTimedHome(lobby);
    try (var stream = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
      assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, stream));
    } finally { frame.recycle(); }
  }

  private void saveWaterEvidence(Bitmap first, Bitmap second, double firstMillis,
      double secondMillis, int changedPixels) throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output == null || output.isEmpty()) return;
    java.io.File directory = new java.io.File(output);
    if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create " + output);
    String[] names = {"home-water-phone-t0150.png", "home-water-phone-t1100.png"};
    Bitmap[] frames = {first, second};
    for (int i = 0; i < frames.length; i++)
      try (var stream = new java.io.FileOutputStream(new java.io.File(directory, names[i]))) {
        assertTrue(frames[i].compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    org.json.JSONObject evidence = new org.json.JSONObject()
        .put("schema", "home-water-motion-v1").put("width", 915).put("height", 412)
        .put("firstClockMillis", firstMillis).put("secondClockMillis", secondMillis)
        .put("visibleWaterChangedPixels", changedPixels).put("minimumRgbDelta", 12)
        .put("minimumChangedPixels", 100)
        .put("firstFrame", names[0]).put("secondFrame", names[1]);
    java.nio.file.Files.write(new java.io.File(directory, "home-water-motion.json").toPath(),
        evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private Bitmap drawTimedHome(LobbyView lobby) {
    Bitmap bitmap = Bitmap.createBitmap(915, 412, Bitmap.Config.ARGB_8888);
    lobby.draw(new Canvas(bitmap));
    return bitmap;
  }

  private boolean dispatch(LobbyView lobby, int action, float x, float y) {
    long now = android.os.SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
    try { return lobby.dispatchTouchEvent(event); } finally { event.recycle(); }
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

