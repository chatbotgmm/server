package org.dpdns.gmm;

/** Server timestamps projected onto a monotonic clock; never starts a local AI match. */
final class Roster60QueueClock {
  private long queuedAt, serverNow, sampledAt, aiAvailableAt;
  private boolean known, aiKnown;

  void clear() { known = false; aiKnown = false; }

  void update(String mode, long queuedAt, long serverNow, long aiAvailableAt, long monotonicNow) {
    clear();
    if (queuedAt < 0 || serverNow < queuedAt || monotonicNow < 0) return;
    this.queuedAt = queuedAt;
    this.serverNow = serverNow;
    sampledAt = monotonicNow;
    known = true;
    // A rank response cannot enable AI, including a malformed server/proxy response.
    aiKnown = "normal".equals(mode) && aiAvailableAt >= queuedAt;
    this.aiAvailableAt = aiAvailableAt;
  }

  long serverTime(long monotonicNow) {
    return serverNow + Math.max(0, monotonicNow - sampledAt);
  }

  String label(String mode, long monotonicNow) {
    if (!known) return "대기 시간 확인 중 · " + ("rank".equals(mode) ? "사용자 매칭" : "서버 확인 중");
    long now = serverTime(monotonicNow);
    long elapsed = Math.max(0, now - queuedAt) / 1000;
    String label = String.format(java.util.Locale.ROOT, "%d:%02d 대기", elapsed / 60, elapsed % 60);
    if ("rank".equals(mode)) return label + " · 사용자 매칭";
    if (!aiKnown) return label + " · AI 전환 시각 확인 중";
    long remaining = Math.max(0, aiAvailableAt - now);
    return label + (remaining > 0 ? " · " + ((remaining + 999) / 1000) + "초 후 AI 매칭" : " · AI 매칭 확인 중");
  }
}
