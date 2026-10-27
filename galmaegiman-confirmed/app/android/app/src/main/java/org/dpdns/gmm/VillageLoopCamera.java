package org.dpdns.gmm;

/** Continuous camera in screen-width units; only projections repeat around the village. */
final class VillageLoopCamera {
  static final double REFERENCE_WORLD_UNITS = 8;
  static final double MINIMUM_UNIT_DP = 650;
  private final double worldUnits;
  private double cameraUnits;

  VillageLoopCamera() { this(REFERENCE_WORLD_UNITS); }
  VillageLoopCamera(double worldUnits) {
    positive(worldUnits, "worldUnits");
    this.worldUnits = worldUnits;
  }
  double worldUnits() { return worldUnits; }
  double cameraUnits() { return cameraUnits; }
  void setCameraUnits(double units) {
    finite(units, "cameraUnits");
    cameraUnits = units;
  }
  void moveUnits(double delta) {
    finite(delta, "delta");
    setCameraUnits(cameraUnits + delta);
  }
  void movePixels(double pixels, double unitPixels) {
    finite(pixels, "pixels"); positive(unitPixels, "unitPixels");
    moveUnits(pixels / unitPixels);
  }

  static double unitPixels(double viewportPixels, double density) {
    finite(viewportPixels, "viewportPixels");
    if (viewportPixels < 0) throw new IllegalArgumentException("viewportPixels");
    positive(density, "density");
    return Math.max(viewportPixels, MINIMUM_UNIT_DP * density);
  }

  /** The repeat length changes with parallax so every layer closes after one full world turn. */
  double layerPeriodUnits(double parallax) {
    layer(parallax);
    return worldUnits * parallax;
  }
  double tileStrideUnits(double parallax) { return layerPeriodUnits(parallax) / 2; }
  double layerPhaseUnits(double parallax) {
    return modulo(cameraUnits * parallax, layerPeriodUnits(parallax));
  }

  /** Nearest repeated object center to the viewport center, measured from the viewport left. */
  double projectNearestUnits(double objectUnits, double parallax, double viewportUnits) {
    finite(objectUnits, "objectUnits"); finite(viewportUnits, "viewportUnits");
    if (viewportUnits < 0) throw new IllegalArgumentException("viewportUnits");
    double period = layerPeriodUnits(parallax), center = viewportUnits / 2;
    return center + modulo(objectUnits - cameraUnits * parallax - center + period / 2, period) - period / 2;
  }
  double projectX(double objectUnits, double parallax, double unitPixels, double viewportPixels) {
    positive(unitPixels, "unitPixels"); finite(viewportPixels, "viewportPixels");
    if (viewportPixels < 0) throw new IllegalArgumentException("viewportPixels");
    return projectNearestUnits(objectUnits, parallax, viewportPixels / unitPixels) * unitPixels;
  }

  /** Closest continuous camera destination. Half-world ties choose the positive direction. */
  double nearestEquivalent(double targetCameraUnits) {
    finite(targetCameraUnits, "targetCameraUnits");
    double delta = modulo(targetCameraUnits - cameraUnits + worldUnits / 2, worldUnits) - worldUnits / 2;
    if (delta == -worldUnits / 2) delta = worldUnits / 2;
    return cameraUnits + delta;
  }
  double nearestDistrict(int district) {
    if (district < 0 || district > 3) throw new IllegalArgumentException("district");
    return nearestEquivalent(district * worldUnits / 4);
  }
  int districtIndex() {
    return (int) Math.floor(modulo(cameraUnits, worldUnits) / (worldUnits / 4));
  }
  static double modulo(double value, double period) {
    finite(value, "value"); positive(period, "period");
    double answer = value % period;
    if (answer < 0) answer += period;
    return answer == 0 ? 0 : answer;
  }
  private static void layer(double value) {
    positive(value, "parallax");
    if (value > 1) throw new IllegalArgumentException("parallax");
  }
  private static void positive(double value, String name) {
    finite(value, name);
    if (value <= 0) throw new IllegalArgumentException(name);
  }
  private static void finite(double value, String name) {
    if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name);
  }
}
