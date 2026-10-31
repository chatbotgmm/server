package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.graphics.PointF;
import android.graphics.RectF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class VillageInspectionViewportTest {
  @Test public void defaultScaleKeepsTheWholeFieldAndIdentityTouchCoordinates() {
    VillageInspectionViewport viewport = sized(915, 412);
    assertFalse(viewport.isZoomed());
    assertEquals(1, viewport.scale(), 0);
    assertEquals(0, viewport.offsetX(), 0);
    assertEquals(0, viewport.offsetY(), 0);
    RectF visible = viewport.visibleWorldRect(new RectF());
    assertRect(visible, 0, 0, 915, 412, 0);
    PointF point = new PointF();
    assertSame(point, viewport.toWorld(123, 234, point));
    assertPoint(point, 123, 234, 0);
    assertSame(point, viewport.toScreen(point.x, point.y, point));
    assertPoint(point, 123, 234, 0);
    viewport.dragVertical(120);
    assertEquals(0, viewport.offsetY(), 0);
  }

  @Test public void offCenterZoomPreservesTheFocalWorldPointWithTheReturnedCameraDelta() {
    for (int[] size : sizes()) for (float focusFraction : new float[]{.1f, .5f, .9f}) {
      VillageInspectionViewport viewport = sized(size[0], size[1]);
      VillageLoopCamera camera = new VillageLoopCamera();
      camera.setCameraUnits(19.37);
      double unitPixels = VillageLoopCamera.unitPixels(size[0], 2);
      float focusX = size[0] * focusFraction, focusY = size[1] * .63f;
      PointF oldWorld = viewport.toWorld(focusX, focusY, new PointF());
      double absoluteX = camera.cameraUnits() * unitPixels + oldWorld.x;
      double delta = viewport.zoom(1.35f, focusX, focusY, unitPixels);
      camera.moveUnits(delta);
      PointF nextWorld = viewport.toWorld(focusX, focusY, new PointF());
      assertEquals(absoluteX, camera.cameraUnits() * unitPixels + nextWorld.x, .001);
      assertEquals(oldWorld.y, nextWorld.y, .0002);
      assertEquals((1 - viewport.scale()) * size[0] / 2, viewport.offsetX(), .0002);
      assertCovered(viewport, size[0], size[1]);
      if (focusFraction == .5f) assertEquals(0, delta, .0000001);
      else assertEquals(Math.signum(focusFraction - .5f), Math.signum(delta), 0);
    }
  }

  @Test public void zoomOutAlsoPreservesFocusWhenVerticalEdgesAllowIt() {
    VillageInspectionViewport viewport = sized(1280, 720);
    viewport.restore(VillageInspectionViewport.MAX_SCALE, .5f);
    PointF before = viewport.toWorld(930, 360, new PointF());
    double delta = viewport.zoom(.8f, 930, 360, 1280);
    PointF after = viewport.toWorld(930, 360, new PointF());
    assertEquals(before.x, after.x + delta * 1280, .0001);
    assertEquals(before.y, after.y, .0001);
    assertEquals(1.2, viewport.scale(), .000001);
    assertCovered(viewport, 1280, 720);
  }

  @Test public void zoomAndDragClampAtBothVerticalEdgesWithoutExposingAnEmptyStrip() {
    VillageInspectionViewport viewport = sized(915, 412);
    viewport.zoom(Float.MAX_VALUE, 200, Float.MAX_VALUE, 915);
    assertEquals(VillageInspectionViewport.MAX_SCALE, viewport.scale(), 0);
    assertEquals((1 - viewport.scale()) * 412, viewport.offsetY(), 0);
    assertCovered(viewport, 915, 412);
    viewport.dragVertical(Float.MAX_VALUE);
    assertEquals(0, viewport.offsetY(), 0);
    assertEquals(0, viewport.panNormalized(), 0);
    viewport.dragVertical(-Float.MAX_VALUE);
    assertEquals((1 - viewport.scale()) * 412, viewport.offsetY(), 0);
    assertEquals(1, viewport.panNormalized(), 0);
    viewport.zoom(Float.MIN_VALUE, 200, -Float.MAX_VALUE, 915);
    assertEquals(1, viewport.scale(), 0);
    assertEquals(0, viewport.offsetX(), 0);
    assertEquals(0, viewport.offsetY(), 0);
    assertEquals(.5, viewport.panNormalized(), 0);
    assertFalse(viewport.isZoomed());
  }

  @Test public void coordinateRoundTripsAndVisibleBoundsAgreeAcrossPhoneAndLargeScreens() {
    for (int[] size : sizes()) for (float pan : new float[]{0, .23f, .5f, 1}) {
      VillageInspectionViewport viewport = sized(size[0], size[1]);
      viewport.restore(1.43f, pan);
      RectF visible = viewport.visibleWorldRect(new RectF());
      assertEquals(size[0] / viewport.scale(), visible.width(), .0005);
      assertEquals(size[1] / viewport.scale(), visible.height(), .0005);
      assertEquals(size[0] / 2f, visible.centerX(), .0002);
      for (float x : new float[]{0, size[0] * .17f, size[0]}) {
        for (float y : new float[]{0, size[1] * .81f, size[1]}) {
          PointF point = viewport.toWorld(x, y, new PointF());
          viewport.toScreen(point.x, point.y, point);
          assertPoint(point, x, y, .0005f);
        }
      }
      PointF corner = viewport.toScreen(visible.left, visible.top, new PointF());
      assertPoint(corner, 0, 0, .0005f);
      viewport.toScreen(visible.right, visible.bottom, corner);
      assertPoint(corner, size[0], size[1], .0005f);
      assertCovered(viewport, size[0], size[1]);
    }
  }

  @Test public void resizingKeepsTheNormalizedVerticalAnchorIncludingThroughZeroSize() {
    VillageInspectionViewport viewport = sized(915, 412);
    viewport.restore(1.5f, .72f);
    viewport.dragVertical(17);
    float pan = viewport.panNormalized();
    float normalizedCenter = viewport.toWorld(457.5f, 206, new PointF()).y / 412;
    for (int[] size : new int[][]{{1280, 720}, {0, 0}, {3440, 1440}, {915, 412}}) {
      viewport.setSize(size[0], size[1]);
      assertEquals(pan, viewport.panNormalized(), .000001);
      assertEquals((1 - viewport.scale()) * size[1] * pan, viewport.offsetY(), .0001);
      if (size[1] > 0) {
        assertEquals(normalizedCenter,
            viewport.toWorld(size[0] / 2f, size[1] / 2f, new PointF()).y / size[1], .000001);
      }
      assertCovered(viewport, size[0], size[1]);
    }
  }

  @Test public void restoreCanPrecedeLayoutAndClampsFinitePersistedValues() {
    VillageInspectionViewport viewport = new VillageInspectionViewport();
    viewport.restore(Float.MAX_VALUE, 9);
    assertEquals(VillageInspectionViewport.MAX_SCALE, viewport.scale(), 0);
    assertEquals(1, viewport.panNormalized(), 0);
    assertRect(viewport.visibleWorldRect(new RectF()), 0, 0, 0, 0, 0);
    viewport.setSize(915, 412);
    assertEquals(-206, viewport.offsetY(), 0);
    viewport.restore(1.4f, -8);
    assertEquals(0, viewport.offsetY(), 0);
    viewport.restore(1.4f);
    assertEquals((1 - viewport.scale()) * 206, viewport.offsetY(), .0001);
    viewport.restore(-Float.MAX_VALUE, 1);
    assertFalse(viewport.isZoomed());
    assertEquals(0, viewport.offsetY(), 0);
    viewport.restore(1.5f, 1);
    viewport.reset();
    assertEquals(1, viewport.scale(), 0);
    assertEquals(.5, viewport.panNormalized(), 0);
    assertRect(viewport.visibleWorldRect(new RectF()), 0, 0, 915, 412, 0);
  }

  @Test public void veryLargeFiniteInputsDoNotOverflowTransformState() {
    VillageInspectionViewport viewport = sized(Float.MAX_VALUE, Float.MAX_VALUE);
    double delta = viewport.zoom(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE);
    assertTrue(Double.isFinite(delta));
    assertTrue(Float.isFinite(viewport.offsetX()));
    assertTrue(Float.isFinite(viewport.offsetY()));
    RectF visible = viewport.visibleWorldRect(new RectF());
    assertTrue(Float.isFinite(visible.right));
    assertTrue(Float.isFinite(visible.bottom));
    PointF world = viewport.toWorld(Float.MAX_VALUE, Float.MAX_VALUE, new PointF());
    assertTrue(Float.isFinite(world.x));
    assertTrue(Float.isFinite(world.y));
    viewport.dragVertical(-Float.MAX_VALUE);
    viewport.dragVertical(Float.MAX_VALUE);
    assertEquals(0, viewport.offsetY(), 0);
  }

  @Test public void nonFiniteAndInvalidArgumentsLeaveTheViewportUnchanged() {
    VillageInspectionViewport viewport = sized(915, 412);
    viewport.restore(1.3f, .7f);
    float scale = viewport.scale(), x = viewport.offsetX(), y = viewport.offsetY(), pan = viewport.panNormalized();
    for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> viewport.setSize(invalid, 720));
      assertThrows(IllegalArgumentException.class, () -> viewport.setSize(1280, invalid));
      assertThrows(IllegalArgumentException.class, () -> viewport.restore(invalid));
      assertThrows(IllegalArgumentException.class, () -> viewport.restore(1.4f, invalid));
      assertThrows(IllegalArgumentException.class, () -> viewport.zoom(invalid, 100, 100, 915));
      assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, invalid, 100, 915));
      assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, 100, invalid, 915));
      assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, 100, 100, invalid));
      assertThrows(IllegalArgumentException.class, () -> viewport.dragVertical(invalid));
      assertThrows(IllegalArgumentException.class, () -> viewport.toWorld(invalid, 100, new PointF()));
      assertThrows(IllegalArgumentException.class, () -> viewport.toScreen(100, invalid, new PointF()));
    }
    assertThrows(IllegalArgumentException.class, () -> viewport.setSize(-1, 720));
    assertThrows(IllegalArgumentException.class, () -> viewport.setSize(1280, -1));
    assertThrows(IllegalArgumentException.class, () -> viewport.zoom(0, 100, 100, 915));
    assertThrows(IllegalArgumentException.class, () -> viewport.zoom(-1, 100, 100, 915));
    assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, 100, 100, 0));
    assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, 100, 100, -1));
    assertThrows(IllegalArgumentException.class, () -> viewport.zoom(1.1f, 100, 100, Double.MIN_VALUE));
    assertEquals(scale, viewport.scale(), 0);
    assertEquals(x, viewport.offsetX(), 0);
    assertEquals(y, viewport.offsetY(), 0);
    assertEquals(pan, viewport.panNormalized(), 0);
    assertCovered(viewport, 915, 412);
  }

  @Test public void conversionRejectsOverflowBeforeMutatingTheOutputPoint() {
    VillageInspectionViewport viewport = sized(915, 412);
    viewport.restore(1.5f);
    PointF output = new PointF(12, 34);
    assertThrows(IllegalArgumentException.class, () -> viewport.toScreen(Float.MAX_VALUE, 0, output));
    assertPoint(output, 12, 34, 0);
    assertThrows(IllegalArgumentException.class, () -> viewport.toScreen(0, Float.MAX_VALUE, output));
    assertPoint(output, 12, 34, 0);
    assertThrows(IllegalArgumentException.class, () -> viewport.toWorld(0, Float.NaN, output));
    assertPoint(output, 12, 34, 0);
  }

  private static VillageInspectionViewport sized(float width, float height) {
    VillageInspectionViewport viewport = new VillageInspectionViewport();
    viewport.setSize(width, height);
    return viewport;
  }
  private static int[][] sizes() { return new int[][]{{915, 412}, {1280, 720}, {2048, 838}, {3440, 1440}}; }
  private static void assertCovered(VillageInspectionViewport viewport, float width, float height) {
    assertTrue(viewport.offsetY() <= 0);
    assertTrue(viewport.offsetY() >= (1 - viewport.scale()) * height - .001);
    RectF visible = viewport.visibleWorldRect(new RectF());
    assertTrue(visible.left >= 0);
    assertTrue(visible.top >= 0);
    assertTrue(visible.right <= width + .001);
    assertTrue(visible.bottom <= height + .001);
  }
  private static void assertPoint(PointF point, float x, float y, float tolerance) {
    assertEquals(x, point.x, tolerance);
    assertEquals(y, point.y, tolerance);
  }
  private static void assertRect(RectF rect, float left, float top, float right, float bottom, float tolerance) {
    assertEquals(left, rect.left, tolerance);
    assertEquals(top, rect.top, tolerance);
    assertEquals(right, rect.right, tolerance);
    assertEquals(bottom, rect.bottom, tolerance);
  }
}
