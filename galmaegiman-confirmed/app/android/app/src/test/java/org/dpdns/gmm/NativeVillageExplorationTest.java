package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Actual input, authored art, and complete-window behavior for village inspection. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageExplorationTest {
  final NativeVillageHomeLoadingTest loading = new NativeVillageHomeLoadingTest();
  final NativeVillageHomeTest fixture = loading.fixture;
  final NativeHomePartyFeedbackTest helper = fixture.helper;
  static final NativeHomePartyFeedbackTest.Profile PHONE = NativeVillageHomeTest.PHONE;

  @Before public void setup() { fixture.setup(); }
  @After public void restore() throws Exception { fixture.restore(); }

  NativeVillageHomeView open(MainActivity h) throws Exception {
    fixture.prepare(h);
    NativeVillageHomeView view = fixture.open(h, PHONE);
    settle(h, view, PHONE);
    view.ambient.fixedTime = 0L;
    return view;
  }
  void settle(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile) throws Exception {
    for (int i = 0; i < 4; i++) { LocalGameArtTest.drain(); helper.layout(h, profile); }
    assertTrue("The displayed authored window stays complete", view.sceneReady());
    assertTrue("Wait for the requested window before checking its geometry", view.targetReady);
  }
  void finishMotion(MainActivity h, NativeVillageHomeView view) throws Exception {
    for (int i = 0; i < 90; i++) {
      frame(16); view.computeScroll();
      if (i % 6 == 0) settle(h, view, PHONE);
      if (!view.quarterMoving && view.scroller.isFinished()) break;
    }
    settle(h, view, PHONE);
    assertFalse("A quarter turn reaches its destination", view.quarterMoving);
    assertTrue(view.scroller.isFinished());
  }
  void camera(MainActivity h, NativeVillageHomeView view, double units) throws Exception {
    view.stopMotion(); view.moveCameraUnits(units - view.desiredCamera.cameraUnits()); settle(h, view, PHONE);
    assertEquals(units, view.camera.cameraUnits(), .000001);
  }
  static void frame(int millis) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
  static RectF bounds(View view) { return new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()); }
  static View[] fixed(NativeVillageHomeView view) {
    return new View[]{view.pass, view.settings, view.daily, view.shortcuts, view.questTracker, view.previousQuarter, view.nextQuarter};
  }
  static Map<View,RectF> fixedBounds(NativeVillageHomeView view) {
    Map<View,RectF> result = new LinkedHashMap<>();
    for (View control : fixed(view)) result.put(control, bounds(control));
    return result;
  }
  static void assertFixed(Map<View,RectF> before) {
    for (Map.Entry<View,RectF> entry : before.entrySet()) {
      View control = entry.getKey(); assertRect(entry.getValue(), bounds(control), 0);
      assertEquals(1, control.getScaleX(), 0); assertEquals(1, control.getScaleY(), 0);
      assertEquals(0, control.getTranslationX(), 0); assertEquals(0, control.getTranslationY(), 0);
    }
  }
  static void assertRect(RectF expected, RectF actual, float tolerance) {
    assertEquals(expected.left, actual.left, tolerance); assertEquals(expected.top, actual.top, tolerance);
    assertEquals(expected.right, actual.right, tolerance); assertEquals(expected.bottom, actual.bottom, tolerance);
  }
  static void assertNoGap(NativeVillageHomeView view) {
    RectF painted = view.screenRect(view.field), visible = view.visibleWorld();
    assertTrue(painted.left <= .001 && painted.top <= .001);
    assertTrue(painted.right >= view.field.right - .001 && painted.bottom >= view.field.bottom - .001);
    assertTrue(visible.left >= -.001 && visible.top >= -.001);
    assertTrue(visible.right <= view.field.right + .001 && visible.bottom <= view.field.bottom + .001);
    assertEquals(View.GONE, view.loadingPanel.getVisibility());
    assertTrue(view.assets.committedReady());
  }
  static boolean coveredByControl(NativeVillageHomeView view, float x, float y) {
    for (View control : fixed(view)) if (control.getVisibility() == View.VISIBLE && bounds(control).contains(x, y)) return true;
    return false;
  }
  static PointF opaqueScreenPoint(NativeVillageHomeView view, Button target) {
    PointF result = findOpaqueScreenPoint(view, target);
    assertNotNull("No opaque unoccluded inspected touch point: " + target.getTag(), result);
    return result;
  }
  static PointF findOpaqueScreenPoint(NativeVillageHomeView view, Button target) {
    RectF art = NativeVillageHomeTest.shownArt(view, target);
    if (!art.intersect(view.visibleWorld())) return null;
    PointF screen = new PointF();
    for (float y = art.top + 2; y < art.bottom - 2; y += 3) for (float x = art.left + 2; x < art.right - 2; x += 3) {
      view.inspection.toScreen(x, y, screen);
      if (view.safe.contains(screen.x, screen.y) && !coveredByControl(view, screen.x, screen.y)
          && NativeVillageHomeTest.sourceAlphaAt(view, target, x, y) >= 200
          && view.alphaHitWorldButton(target, x - target.getLeft(), y - target.getTop())) return new PointF(screen.x, screen.y);
    }
    return null;
  }
  static void assertWorldMatrix(NativeVillageHomeView view, View target) {
    RectF matrix = new RectF(0, 0, target.getWidth(), target.getHeight());
    target.getMatrix().mapRect(matrix); matrix.offset(target.getLeft(), target.getTop());
    assertRect(view.screenRect(bounds(target)), matrix, .001f);
  }
  static void pointers(NativeVillageHomeView view, long down, int action, int[] ids, float[] x, float[] y) {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
    for (int i = 0; i < ids.length; i++) {
      properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
      coords[i] = new MotionEvent.PointerCoords(); coords[i].x = x[i]; coords[i].y = y[i]; coords[i].pressure = 1; coords[i].size = 1;
    }
    MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, ids.length, properties, coords, 0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
    try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
  }
  static void single(NativeVillageHomeView view, long down, int action, int id, float x, float y) {
    pointers(view, down, action, new int[]{id}, new float[]{x}, new float[]{y});
  }
  static void pinchMove(NativeVillageHomeView view, long down, float centerX, float y, float span) {
    frame(20); pointers(view, down, MotionEvent.ACTION_MOVE, new int[]{7, 19}, new float[]{centerX - span / 2, centerX + span / 2}, new float[]{y, y});
  }

  @Test public void fourQuarterButtonsCloseTheWorldInBothDirectionsAcrossEitherSeam() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h); NativeVillageHomeTest.Api api = (NativeVillageHomeTest.Api) h.api;
      for (double start : new double[]{-.35, 7.65}) for (int direction : new int[]{-1, 1}) {
        camera(h, view, start); RectF original = new RectF(view.buildingBounds.get("recruit"));
        for (int step = 1; step <= 4; step++) {
          (direction < 0 ? view.previousQuarter : view.nextQuarter).performClick();
          assertEquals(start + direction * step * 2, view.quarterTargetUnits, .000001);
          finishMotion(h, view); assertEquals(start + direction * step * 2, view.camera.cameraUnits(), .000001);
          assertNoGap(view);
        }
        assertEquals(VillageLoopCamera.modulo(start, 8), VillageLoopCamera.modulo(view.camera.cameraUnits(), 8), .000001);
        assertRect(original, view.buildingBounds.get("recruit"), .001f);
      }
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void rapidQuarterButtonsAccumulateTheirPendingTwoUnitDestinations() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h); camera(h, view, 7.75);
      for (int i = 1; i <= 4; i++) { view.nextQuarter.performClick(); assertEquals(7.75 + i * 2, view.quarterTargetUnits, .000001); }
      assertTrue(view.quarterMoving); assertTrue(view.camera.cameraUnits() < view.quarterTargetUnits);
      for (int i = 1; i <= 2; i++) { view.previousQuarter.performClick(); assertEquals(15.75 - i * 2, view.quarterTargetUnits, .000001); }
      finishMotion(h, view); assertEquals(11.75, view.camera.cameraUnits(), .000001);
      view.previousQuarter.performClick(); view.previousQuarter.performClick(); finishMotion(h, view);
      assertEquals(7.75, view.camera.cameraUnits(), .000001);
    }
  }

  @Test public void focalZoomVerticalDragAndOpaqueTapUseTheSameTransformedWorldWhileHudStaysFixed() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h); Map<View,RectF> controls = fixedBounds(view);
      float focusX = view.field.width() * .68f, focusY = view.field.height() * .53f;
      PointF before = view.inspection.toWorld(focusX, focusY, new PointF());
      double absoluteX = view.camera.cameraUnits() * view.unitPixels() + before.x;
      view.zoomAt(1.4f, focusX, focusY); settle(h, view, PHONE);
      PointF after = view.inspection.toWorld(focusX, focusY, new PointF());
      assertEquals(absoluteX, view.camera.cameraUnits() * view.unitPixels() + after.x, .001);
      assertEquals(before.y, after.y, .001); assertTrue(view.inspection.isZoomed()); assertFixed(controls);
      float previousY = view.inspection.offsetY(); double previousCamera = view.desiredCamera.cameraUnits();
      long down = SystemClock.uptimeMillis(); float x = view.field.centerX(), y = view.field.centerY();
      single(view, down, MotionEvent.ACTION_DOWN, 7, x, y); frame(40);
      single(view, down, MotionEvent.ACTION_MOVE, 7, x, y + 25); frame(40);
      single(view, down, MotionEvent.ACTION_MOVE, 7, x, y + 45);
      single(view, down, MotionEvent.ACTION_CANCEL, 7, x, y + 45); settle(h, view, PHONE);
      assertTrue("A vertical inspection drag moves the crop", view.inspection.offsetY() > previousY);
      assertEquals(previousCamera, view.desiredCamera.cameraUnits(), .000001); assertFixed(controls); assertNoGap(view);
      Button target = view.buildingButtons.get("recruit"); assertEquals(View.VISIBLE, target.getVisibility()); assertWorldMatrix(view, target);
      PointF tap = opaqueScreenPoint(view, target); final int[] clicked = {0}; target.setOnClickListener(ignored -> clicked[0]++);
      NativeVillageHomeTest.tapAt(view, tap.x, tap.y); assertEquals("The enlarged opaque artwork receives its own tap", 1, clicked[0]);
      assertTrue(((NativeVillageHomeTest.Api) h.api).writes.isEmpty());
    }
  }

  @Test public void rawPinchCancelsThePressedPictureAndHandsTheSurvivingPointerToDrag() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h);
      Button target = view.buildingButtons.get("recruit"); PointF hit = opaqueScreenPoint(view, target); final int[] clicked = {0}; target.setOnClickListener(ignored -> clicked[0]++);
      long down = SystemClock.uptimeMillis(); single(view, down, MotionEvent.ACTION_DOWN, 7, hit.x, hit.y); assertSame(target, view.pressedBuilding);
      frame(20); pointers(view, down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{7, 19}, new float[]{hit.x, hit.x + 100}, new float[]{hit.y, hit.y});
      assertNull(view.pressedBuilding); float center = hit.x + 50;
      pinchMove(view, down, center, hit.y, 160); pinchMove(view, down, center, hit.y, 220); pinchMove(view, down, center, hit.y, 260);
      settle(h, view, PHONE); assertTrue("Raw two-finger span changes inspect the village", view.inspection.isZoomed()); assertTrue(view.multiTouch); assertEquals(0, clicked[0]);
      float scale = view.inspection.scale();
      frame(20); pointers(view, down, MotionEvent.ACTION_POINTER_UP, new int[]{7, 19}, new float[]{center - 130, center + 130}, new float[]{hit.y, hit.y});
      double camera = view.desiredCamera.cameraUnits(); frame(30);
      single(view, down, MotionEvent.ACTION_MOVE, 19, center + 165, hit.y); settle(h, view, PHONE);
      assertEquals("Handoff starts from the surviving pointer without a jump", camera - 35 / (scale * view.unitPixels()), view.desiredCamera.cameraUnits(), .0001);
      assertEquals(scale, view.inspection.scale(), .000001);
      single(view, down, MotionEvent.ACTION_UP, 19, center + 165, hit.y); frame(32);
      assertNull(view.pressedBuilding); assertEquals(0, clicked[0]); assertNull(view.owner.menu);
    }
  }

  @Test public void pinchTakesOverAFlingAndSingleDragTakesOverAQuarterAnimation() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h);
      NativeVillageHomeTest.fling(view, false); assertFalse("Exercise a real moving fling", view.scroller.isFinished());
      long down = SystemClock.uptimeMillis(); float x = 450, y = view.field.centerY();
      single(view, down, MotionEvent.ACTION_DOWN, 7, x - 50, y); frame(20);
      pointers(view, down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{7, 19}, new float[]{x - 50, x + 50}, new float[]{y, y});
      pinchMove(view, down, x, y, 180); pinchMove(view, down, x, y, 260);
      pointers(view, down, MotionEvent.ACTION_CANCEL, new int[]{7, 19}, new float[]{x - 130, x + 130}, new float[]{y, y}); settle(h, view, PHONE);
      assertTrue(view.inspection.isZoomed()); assertTrue(view.scroller.isFinished()); double stopped = view.desiredCamera.cameraUnits();
      frame(160); view.computeScroll(); settle(h, view, PHONE); assertEquals(stopped, view.desiredCamera.cameraUnits(), .000001);
      view.nextQuarter.performClick(); double abandoned = view.quarterTargetUnits; frame(64); view.computeScroll();
      assertTrue(view.quarterMoving); down = SystemClock.uptimeMillis();
      single(view, down, MotionEvent.ACTION_DOWN, 7, x, y); double dragStart = view.desiredCamera.cameraUnits(); frame(40);
      single(view, down, MotionEvent.ACTION_MOVE, 7, x + 60, y); single(view, down, MotionEvent.ACTION_CANCEL, 7, x + 60, y); settle(h, view, PHONE);
      assertFalse(view.quarterMoving); assertTrue(view.scroller.isFinished());
      assertEquals(dragStart - 60 / (view.inspection.scale() * view.unitPixels()), view.desiredCamera.cameraUnits(), .0001);
      assertTrue(Math.abs(view.camera.cameraUnits() - abandoned) > .1);
      double dragged = view.camera.cameraUnits(); frame(700); view.computeScroll(); settle(h, view, PHONE); assertEquals(dragged, view.camera.cameraUnits(), .000001);
    }
  }

  static final class CountingSource implements NativeVillageHomeAssets.Source {
    final NativeVillageHomeAssets.LocalSource local; int loads, callbacks, configurations;
    CountingSource(MainActivity h) { local = new NativeVillageHomeAssets.LocalSource(h); }
    public int sample(String path) { return local.sample(path); }
    public Bitmap peek(String path) { return local.peek(path); }
    public void configure(VillageLayout layout, int width, int height, float density) { configurations++; local.configure(layout, width, height, density); }
    public void load(String path, Consumer<Bitmap> done) { loads++; local.load(path, bitmap -> { callbacks++; done.accept(bitmap); }); }
  }
  @Test public void samePhysicalSizeZoomReusesDecodedArtAndStartsNoHomeReads() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); CountingSource source = new CountingSource(h); NativeVillageHomeView view = loading.open(h, source, PHONE);
      view.owner.statueStage = 1; view.assets.loadStatue(view.layout, 1, view.owner::current, view::updateNotice); settle(h, view, PHONE);
      NativeVillageHomeTest.Api api = (NativeVillageHomeTest.Api) h.api; List<String> reads = new ArrayList<>(api.paths);
      int loads = source.loads, callbacks = source.callbacks, configurations = source.configurations;
      for (float factor : new float[]{1.15f, 1.15f, 1.15f, 1 / VillageInspectionViewport.MAX_SCALE}) {
        view.zoomAt(factor, view.field.centerX(), view.field.centerY()); settle(h, view, PHONE);
        Bitmap image = NativeHomePartyFeedbackTest.render(h.root); image.recycle(); assertNoGap(view);
      }
      assertEquals("Inspection does not create another decode key", configurations, source.configurations);
      assertEquals("Existing physical-resolution PNGs serve zoom in and out", loads, source.loads);
      assertEquals(callbacks, source.callbacks); assertEquals(reads, api.paths); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void modalPauseHomeReturnAndRenewedTokenRestoreOnlyTheirOwnAccountInspection() throws Exception {
    try (ActivityController<MainActivity> activity = helper.flow.start()) {
      MainActivity h = activity.get(); NativeVillageHomeView view = open(h); NativeVillageHomeTest.Api api = (NativeVillageHomeTest.Api) h.api;
      camera(h, view, 8006.25); view.zoomAt(1.4f, view.field.centerX(), view.field.height() * .7f); settle(h, view, PHONE);
      double camera = view.camera.cameraUnits(); float scale = view.inspection.scale(), pan = view.inspection.panNormalized(); String scope = view.owner.scope;
      NativeVillageHomeTest.tap(view, view.settings); assertTrue(view.owner.menu.isShowing()); assertTrue(view.owner.back());
      assertSame(view, h.villageHome.view); assertEquals(scale, view.inspection.scale(), 0); assertEquals(pan, view.inspection.panNormalized(), 0);
      view.owner.pause(); view.owner.resume(); settle(h, view, PHONE); assertEquals(camera, view.camera.cameraUnits(), .000001); assertEquals(scale, view.inspection.scale(), .000001);
      h.openPlayMenu(); h.back(); view = h.villageHome.view; settle(h, view, PHONE);
      assertEquals(camera, view.camera.cameraUnits(), .000001); assertEquals(scale, view.inspection.scale(), .000001); assertEquals(pan, view.inspection.panNormalized(), .000001);
      view.owner.pause(); api.account = "village-player.2.renewed"; view.owner.resume(); view = h.villageHome.view; settle(h, view, PHONE);
      assertEquals(scope, view.owner.scope); assertEquals(camera, view.camera.cameraUnits(), .000001); assertEquals(scale, view.inspection.scale(), .000001); assertEquals(pan, view.inspection.panNormalized(), .000001);
      view.owner.pause(); api.account = "other-explorer.1.signature"; view.owner.resume(); view = h.villageHome.view; settle(h, view, PHONE);
      assertNotEquals(scope, view.owner.scope); assertEquals(0, view.camera.cameraUnits(), 0); assertEquals(NativeVillageHomeView.DEFAULT_SCALE, view.inspection.scale(), 0); assertEquals(NativeVillageHomeView.DEFAULT_PAN, view.inspection.panNormalized(), 0);
      camera(h, view, 1.25); view.zoomAt(1.1f, view.field.centerX(), view.field.centerY()); settle(h, view, PHONE);
      view.owner.pause(); api.account = "village-player.3.returned"; view.owner.resume(); view = h.villageHome.view; settle(h, view, PHONE);
      assertEquals(scope, view.owner.scope); assertEquals(camera, view.camera.cameraUnits(), .000001); assertEquals(scale, view.inspection.scale(), .000001); assertEquals(pan, view.inspection.panNormalized(), .000001);
      assertTrue(api.writes.isEmpty());
    }
  }

  void capture(MainActivity h, NativeVillageHomeView view, NativeHomePartyFeedbackTest.Profile profile, String name, JSONArray evidence) throws Exception {
    Path directory = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "village-exploration"); Files.createDirectories(directory);
    String file = profile.name + "-" + name + ".png"; Bitmap image = NativeHomePartyFeedbackTest.render(h.root);
    try { NativeVillageHomeArtTest.png(directory.resolve(file), image); } finally { image.recycle(); }
    evidence.put(MainActivity.obj("file", file, "profile", profile.json(), "cameraU", view.camera.cameraUnits(), "scale", view.inspection.scale(), "pan", view.inspection.panNormalized(), "previous", bounds(view.previousQuarter).toShortString(), "next", bounds(view.nextQuarter).toShortString()));
    NativeVillageHomeArtTest.json(directory.resolve("geometry.json"), evidence);
  }
  @Test public void actualPhoneTabletAndLargeFontCapturesKeepControlsHitsAndTheInspectedFieldComplete() throws Exception {
    JSONArray evidence = new JSONArray();
    for (NativeHomePartyFeedbackTest.Profile profile : NativeVillageHomeTest.PROFILES) {
      helper.configure(profile);
      try (ActivityController<MainActivity> activity = helper.flow.start()) {
        MainActivity h = activity.get(); fixture.prepare(h); NativeVillageHomeView view = fixture.open(h, profile); settle(h, view, profile);
        view.ambient.fixedTime = 0L; NativeVillageHomeArtTest.requireFonts(h); NativeVillageHomeArtTest.requireVerified(view.layout);
        Map<View,RectF> controls = fixedBounds(view);
        for(Button target:NativeVillageHomeTest.worldTargets(view)){RectF caption=view.screenRect(view.labelRect(new RectF(target.getLeft(),target.getTop(),target.getRight(),target.getBottom()),target));if(view.field.contains(caption))for(View navigation:new View[]{view.previousQuarter,view.nextQuarter})assertFalse("Quarter controls cannot cover the visible facility/resident nameplate",RectF.intersects(caption,bounds(navigation)));}
        capture(h, view, profile, "overview", evidence);
        for (int quarter = 0; quarter < 4; quarter++) {
          view.stopMotion(); view.moveCameraUnits(quarter * 2 + .25 - view.desiredCamera.cameraUnits()); settle(h, view, profile);
          view.zoomAt(VillageInspectionViewport.MAX_SCALE / view.inspection.scale(), view.field.centerX(), view.field.centerY()); settle(h, view, profile);
          assertNoGap(view); assertFixed(controls);
          for (Button control : new Button[]{view.previousQuarter, view.nextQuarter}) {
            assertEquals(h.dp(64), control.getWidth()); assertEquals(h.dp(48), control.getHeight());
            assertTrue(view.safe.contains(bounds(control))); NativeNoScrollReviewTest.assertFullyVisible(control, h.root);
            assertTrue(control.getContentDescription().toString().contains("90"));
          }
          for (View control : fixed(view)) if (control.getVisibility() == View.VISIBLE) NativeNoScrollReviewTest.assertFullyVisible(control, h.root);
          assertFalse(RectF.intersects(bounds(view.previousQuarter), bounds(view.nextQuarter)));
          boolean hit = false;
          for (Button target : NativeVillageHomeTest.worldTargets(view)) {
            assertWorldMatrix(view, target);
            PointF point = findOpaqueScreenPoint(view, target);
            if (point != null) { assertTrue(view.field.contains(point.x, point.y)); hit = true; }
          }
          assertTrue("Every captured district has a reachable authored picture", hit);
          capture(h, view, profile, "quarter-" + quarter + "-inspection", evidence);
        }
        assertTrue(((NativeVillageHomeTest.Api) h.api).writes.isEmpty());
      }
    }
  }
}
