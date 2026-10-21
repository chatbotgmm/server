package org.dpdns.gmm;

/** Android에 의존하지 않는 기술 길게 누르기 상태. 긴 누름은 절대 공격 클릭으로 끝나지 않는다. */
final class SkillHoldGesture {
  private final long timeout;
  private final float slopSquared;
  private long downAt;
  private float downX, downY;
  private boolean active, holding, cancelled;

  SkillHoldGesture(long timeout, float slop) {
    this.timeout = Math.max(1, timeout);
    slopSquared = Math.max(0, slop) * Math.max(0, slop);
  }

  void down(long now, float x, float y) {
    downAt = now; downX = x; downY = y;
    active = true; holding = false; cancelled = false;
  }

  boolean move(float x, float y) {
    float dx = x - downX, dy = y - downY;
    if (active && dx * dx + dy * dy > slopSquared) cancel();
    return cancelled;
  }

  boolean hold(long now) {
    if (!active || cancelled || holding || now - downAt < timeout) return false;
    holding = true;
    return true;
  }

  boolean release(long now) {
    boolean click = active && !cancelled && !holding && now - downAt < timeout;
    active = false; holding = false;
    return click;
  }

  void cancel() { active = false; holding = false; cancelled = true; }
  boolean active() { return active; }
  boolean holding() { return holding; }
  boolean cancelled() { return cancelled; }
}
