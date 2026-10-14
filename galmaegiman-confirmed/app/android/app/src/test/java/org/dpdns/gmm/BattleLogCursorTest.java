package org.dpdns.gmm;

import static org.junit.Assert.*;

import org.junit.Test;

public class BattleLogCursorTest {
  @Test
  public void newlyAppendedLines() {
    assertEquals(
        2,
        BattleLogCursor.firstNewLine(
            new String[] {"start", "attack"}, new String[] {"start", "attack", "damage"}));
  }

  @Test
  public void rollingWindow() {
    String[] before = new String[50], after = new String[50];
    for (int i = 0; i < 50; i++) {
      before[i] = "line" + i;
      after[i] = "line" + (i + 6);
    }
    assertEquals(44, BattleLogCursor.firstNewLine(before, after));
  }

  @Test
  public void repeatedResponseHasNoNewLines() {
    assertEquals(
        3,
        BattleLogCursor.firstNewLine(new String[] {"a", "b", "c"}, new String[] {"a", "b", "c"}));
  }

  @Test
  public void noOverlapStartsFromFirstLine() {
    assertEquals(0, BattleLogCursor.firstNewLine(new String[] {"a"}, new String[] {"b"}));
  }

  @Test
  public void emptyPreviousLog() {
    assertEquals(0, BattleLogCursor.firstNewLine(new String[] {}, new String[] {"start"}));
  }

  @Test
  public void repeatedDamageLinesKeepLongestOverlap() {
    assertEquals(
        3,
        BattleLogCursor.firstNewLine(
            new String[] {"attack", "damage", "damage"},
            new String[] {"attack", "damage", "damage", "heal"}));
  }
}
