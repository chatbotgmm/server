package org.dpdns.gmm;

/** UI의 수명과 서버 시간을 분리한다. 대기시간 종료는 재조회 사유일 뿐 지급 허가가 아니다. */
final class NativeProgressionState {
  private long serverNow, receivedAt;
  private boolean loaded;
  private int generation;
  int begin() { loaded = false; return ++generation; }
  boolean accept(int ticket, long now, long elapsed) {
    if (ticket != generation || now <= 0) return false;
    serverNow = now; receivedAt = elapsed; loaded = true; return true;
  }
  boolean current(int ticket) { return ticket == generation; }
  void invalidate() { loaded = false; ++generation; }
  boolean ready(long deadline) { return loaded && (deadline <= 0 || serverNow >= deadline); }
  long remaining(long deadline, long elapsed) {
    if (!loaded) return -1;
    return Math.max(0, deadline - serverNow - Math.max(0, elapsed - receivedAt));
  }
}
