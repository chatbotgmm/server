package org.dpdns.gmm;

import android.graphics.PointF;
import android.graphics.RectF;

/** Inspection transform over the existing world coordinates; horizontal travel stays in the loop camera. */
final class VillageInspectionViewport {
  static final float MIN_SCALE = 1f;
  static final float MAX_SCALE = 1.5f;
  private float width, height;
  private float scale = MIN_SCALE;
  private float offsetY;
  // Keep the travel fraction separately so a temporary zero-height layout cannot lose it.
  private double verticalFraction = .5;

  void setSize(float width, float height) {
    nonNegative(width, "width");
    nonNegative(height, "height");
    this.width = width == 0 ? 0 : width;
    this.height = height == 0 ? 0 : height;
    offsetY = verticalOffset(scale, this.height, verticalFraction);
  }

  float scale() { return scale; }
  float offsetX() { return width == 0 || scale == MIN_SCALE ? 0 : (float) ((1d - scale) * width / 2); }
  float offsetY() { return offsetY; }
  float panNormalized() { return (float) verticalFraction; }
  boolean isZoomed() { return scale > MIN_SCALE; }

  RectF visibleWorldRect(RectF out) {
    if (out == null) throw new NullPointerException("out");
    out.set((float) (-offsetX() / (double) scale), (float) (-offsetY / (double) scale),
        (float) ((width - (double) offsetX()) / scale), (float) ((height - (double) offsetY) / scale));
    return out;
  }

  PointF toWorld(float screenX, float screenY, PointF out) {
    if (out == null) throw new NullPointerException("out");
    finite(screenX, "screenX");
    finite(screenY, "screenY");
    float x = finiteFloat((screenX - (double) offsetX()) / scale, "worldX");
    float y = finiteFloat((screenY - (double) offsetY) / scale, "worldY");
    out.set(x, y);
    return out;
  }

  PointF toScreen(float worldX, float worldY, PointF out) {
    if (out == null) throw new NullPointerException("out");
    finite(worldX, "worldX");
    finite(worldY, "worldY");
    float x = finiteFloat(worldX * (double) scale + offsetX(), "screenX");
    float y = finiteFloat(worldY * (double) scale + offsetY, "screenY");
    out.set(x, y);
    return out;
  }

  /**
   * Return the delta to add to VillageLoopCamera to retain the horizontal world point under focus.
   * Vertical focus is retained until a field edge requires clamping to avoid an empty strip.
   */
  double zoom(float factor, float focusX, float focusY, double unitPixels) {
    positive(factor, "factor");
    finite(focusX, "focusX");
    finite(focusY, "focusY");
    positive(unitPixels, "unitPixels");
    float nextScale = (float) clamp(scale * (double) factor, MIN_SCALE, MAX_SCALE);
    if (nextScale == scale) return 0;

    double oldWorldX = (focusX - (double) offsetX()) / scale;
    float nextOffsetX = nextScale == MIN_SCALE || width == 0 ? 0 : (float) ((1d - nextScale) * width / 2);
    double nextWorldX = (focusX - (double) nextOffsetX) / nextScale;
    double cameraDeltaUnits = (oldWorldX - nextWorldX) / unitPixels;
    finite(cameraDeltaUnits, "cameraDeltaUnits");

    double oldWorldY = (focusY - (double) offsetY) / scale;
    float minimumY = minimumOffsetY(nextScale, height);
    float nextOffsetY = (float) clamp(focusY - oldWorldY * nextScale, minimumY, 0);
    double nextFraction = minimumY == 0 ? verticalFraction : nextOffsetY / (double) minimumY;
    scale = nextScale;
    verticalFraction = nextScale == MIN_SCALE ? .5 : nextFraction;
    offsetY = nextScale == MIN_SCALE || nextOffsetY == 0 ? 0 : nextOffsetY;
    return cameraDeltaUnits;
  }

  void dragVertical(float screenDy) {
    finite(screenDy, "screenDy");
    float minimumY = minimumOffsetY(scale, height);
    if (minimumY == 0) return;
    offsetY = (float) clamp(offsetY + (double) screenDy, minimumY, 0);
    if (offsetY == 0) offsetY = 0;
    verticalFraction = offsetY / (double) minimumY;
  }

  void reset() {
    scale = MIN_SCALE;
    offsetY = 0;
    verticalFraction = .5;
  }

  /** Restore a scale with a centered vertical crop. */
  void restore(float scale) { restore(scale, .5f); }

  /** panNormalized is 0 at the top of the field and 1 at the bottom. */
  void restore(float scale, float panNormalized) {
    finite(scale, "scale");
    finite(panNormalized, "panNormalized");
    float nextScale = (float) clamp(scale, MIN_SCALE, MAX_SCALE);
    double nextFraction = nextScale == MIN_SCALE ? .5 : clamp(panNormalized, 0, 1);
    this.scale = nextScale;
    verticalFraction = nextFraction;
    offsetY = verticalOffset(nextScale, height, nextFraction);
  }

  private static float verticalOffset(float scale, float height, double fraction) {
    float minimumY = minimumOffsetY(scale, height);
    return minimumY == 0 || fraction == 0 ? 0 : (float) (minimumY * fraction);
  }
  private static float minimumOffsetY(float scale, float height) {
    return scale == MIN_SCALE || height == 0 ? 0 : (float) ((1d - scale) * height);
  }
  private static double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
  private static float finiteFloat(double value, String name) {
    finite(value, name);
    if (value < -Float.MAX_VALUE || value > Float.MAX_VALUE) throw new IllegalArgumentException(name);
    return (float) value;
  }
  private static void nonNegative(double value, String name) {
    finite(value, name);
    if (value < 0) throw new IllegalArgumentException(name);
  }
  private static void positive(double value, String name) {
    finite(value, name);
    if (value <= 0) throw new IllegalArgumentException(name);
  }
  private static void finite(double value, String name) {
    if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name);
  }
}
