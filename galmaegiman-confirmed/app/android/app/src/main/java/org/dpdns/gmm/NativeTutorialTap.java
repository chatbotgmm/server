package org.dpdns.gmm;

/** 강조 대상에서 시작하고 끝난 단일 탭만 허용한다. 드래그와 취소는 전달하지 않는다. */
final class NativeTutorialTap {
  private boolean pressed;
  private float downX, downY;

  void down(float x, float y, boolean insideEnabledTarget) {
    downX = x;
    downY = y;
    pressed = insideEnabledTarget;
  }

  void move(float x, float y, int slop) {
    if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) cancel();
  }

  boolean up(boolean insideEnabledTarget) {
    boolean activate = pressed && insideEnabledTarget;
    cancel();
    return activate;
  }

  void cancel() { pressed = false; }
}
