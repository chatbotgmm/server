package org.dpdns.gmm;

/** Android와 독립적인 단조 시간 재생기. 샘플링 시 객체/프레임 비트맵을 만들지 않는다. */
final class BackgroundMotionTimeline {
  private final int[] sequence;
  private final long frameMs, loopMs, crossfadeMs;
  private long started;
  private boolean running;
  int current, next;
  float nextWeight;

  BackgroundMotionTimeline(int[] sequence, long frameMs, long crossfadeMs) {
    int[] expected = {0,1,2,3,4,5,6,7,6,5,4,3,2,1};
    if (!java.util.Arrays.equals(sequence, expected) || frameMs <= 0 || frameMs > 60000
        || crossfadeMs < 0 || crossfadeMs > frameMs) throw new IllegalArgumentException("배경 재생 규칙 오류");
    this.sequence = sequence.clone();
    this.frameMs = frameMs;
    this.crossfadeMs = crossfadeMs;
    loopMs = frameMs * sequence.length;
  }

  void start(long monotonicMs) { started = monotonicMs; running = true; sample(monotonicMs); }
  void stop() { running = false; nextWeight = 0; }
  boolean isRunning() { return running; }

  void sample(long monotonicMs) {
    if (!running) return;
    long elapsed = Math.max(0, monotonicMs - started) % loopMs;
    int slot = (int) (elapsed / frameMs);
    current = sequence[slot];
    next = sequence[(slot + 1) % sequence.length];
    long inside = elapsed % frameMs;
    nextWeight = crossfadeMs == 0 ? 0 : Math.max(0f, (inside - (frameMs - crossfadeMs)) / (float) crossfadeMs);
  }
}
