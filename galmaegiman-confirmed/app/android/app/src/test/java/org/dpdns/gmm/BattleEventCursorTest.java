package org.dpdns.gmm;

import static org.junit.Assert.*;

import org.junit.Test;

public class BattleEventCursorTest {
  @Test
  public void reconnectSeedsLatestWithoutReplayingHistory() {
    BattleEventCursor c = new BattleEventCursor(40);
    assertEquals(3, c.firstNew(new long[] {38, 39, 40}, 40));
    assertEquals(2, c.firstNew(new long[] {39, 40, 41, 42}, 42));
  }

  @Test
  public void repeatedIdenticalDamageStillHasDistinctIds() {
    BattleEventCursor c = new BattleEventCursor(8);
    assertEquals(0, c.firstNew(new long[] {9, 10, 11}, 11));
    c.accept(11);
    assertEquals(3, c.firstNew(new long[] {9, 10, 11}, 11));
  }

  @Test
  public void cappedStreamWithOverlapIsSupported() {
    BattleEventCursor c = new BattleEventCursor(204);
    assertEquals(2, c.firstNew(new long[] {203, 204, 205, 206}, 206));
  }

  @Test
  public void missingEventsRequestSnapshotInsteadOfGuessing() {
    BattleEventCursor c = new BattleEventCursor(10);
    assertEquals(-1, c.firstNew(new long[] {13, 14}, 14));
    assertEquals(-1, c.firstNew(new long[] {11, 13}, 13));
    assertEquals(-1, c.firstNew(new long[] {}, 11));
  }

  @Test
  public void malformedSequenceDoesNotReplay() {
    BattleEventCursor c = new BattleEventCursor(10);
    assertEquals(-1, c.firstNew(new long[] {11, 11}, 11));
    assertEquals(-1, c.firstNew(new long[] {12, 11}, 12));
    assertEquals(-1, c.firstNew(new long[] {11}, 12));
    assertEquals(-1, c.firstNew(new long[] {0, 11}, 11));
    assertEquals(-1, c.firstNew(new long[] {9}, 9));
  }

  @Test
  public void acceptingOlderEventCannotMoveCursorBack() {
    BattleEventCursor c = new BattleEventCursor(11);
    c.accept(13);
    c.accept(12);
    assertEquals(13, c.seen());
  }
}
