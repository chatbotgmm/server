package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;

public class Roster60QueueClockTest {
  @Test public void thresholdUsesServerSampleAndMonotonicElapsedTime() {
    Roster60QueueClock clock = new Roster60QueueClock();
    clock.update("normal", 1_000_000, 1_029_000, 1_030_000, 100);
    assertEquals("0:29 대기 · 1초 후 AI 매칭", clock.label("normal", 100));
    assertEquals("0:29 대기 · 1초 후 AI 매칭", clock.label("normal", 1099));
    assertEquals("0:30 대기 · AI 매칭 확인 중", clock.label("normal", 1100));
    assertEquals("0:31 대기 · AI 매칭 확인 중", clock.label("normal", 2100));
  }

  @Test public void reconnectRestoresOriginalWaitAndServerCanCorrectTheEstimate() {
    Roster60QueueClock clock = new Roster60QueueClock();
    clock.update("normal", 1_000_000, 1_024_000, 1_030_000, 50);
    assertEquals("0:24 대기 · 6초 후 AI 매칭", clock.label("normal", 50));
    assertEquals("0:27 대기 · 3초 후 AI 매칭", clock.label("normal", 3050));
    clock.update("normal", 1_000_000, 1_025_000, 1_030_000, 3050);
    assertEquals("0:25 대기 · 5초 후 AI 매칭", clock.label("normal", 3050));
  }

  @Test public void rankNeverMentionsAiEvenWithMalformedDeadline() {
    Roster60QueueClock clock = new Roster60QueueClock();
    clock.update("rank", 1_000_000, 1_031_000, 1_030_000, 500);
    assertEquals("0:31 대기 · 사용자 매칭", clock.label("rank", 500));
    assertFalse(clock.label("rank", 10_000_000).contains("AI"));
  }

  @Test public void missingOrInvalidServerTimestampNeverInventsADeadline() {
    Roster60QueueClock clock = new Roster60QueueClock();
    clock.update("normal", -1, -1, -1, 0);
    assertEquals("대기 시간 확인 중 · 서버 확인 중", clock.label("normal", 60_000));
    clock.update("normal", 500, 499, 30_500, 0);
    assertEquals("대기 시간 확인 중 · 서버 확인 중", clock.label("normal", 60_000));
    clock.update("normal", 500, 2500, -1, 0);
    assertEquals("0:02 대기 · AI 전환 시각 확인 중", clock.label("normal", 0));
    clock.clear();
    assertEquals("대기 시간 확인 중 · 사용자 매칭", clock.label("rank", 0));
  }
}
