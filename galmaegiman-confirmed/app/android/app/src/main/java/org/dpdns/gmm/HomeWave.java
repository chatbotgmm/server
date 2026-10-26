package org.dpdns.gmm;

/** 수면과 배가 공유하는 원본 픽셀 단위 파동. 시계/스레드를 소유하지 않는다. */
final class HomeWave {
  static final double PERIOD = 5.6;
  static double phase(double seconds) { return seconds * Math.PI * 2 / PERIOD; }
  static float height(float x, float y, double seconds) {
    return (float) (2.8 * Math.sin(phase(seconds) + (x - 985) * .012)
        + .5 * Math.sin(phase(seconds) * 2 + y * .045));
  }
  static float bob(double seconds) { return height(985, 475, seconds); }
  static float roll(double seconds) { return (float) (.75 * Math.cos(phase(seconds))); }
  private HomeWave() {}
}
