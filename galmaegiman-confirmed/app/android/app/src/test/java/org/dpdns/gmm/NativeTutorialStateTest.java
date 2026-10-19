package org.dpdns.gmm;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class NativeTutorialStateTest {
  @Test
  public void replayCannotGrantRewardsOrOverwriteSavedStep() {
    NativeTutorialState s = new NativeTutorialState();
    s.sync(9, Arrays.asList("tut", "craft"));
    s.replay();
    assertFalse(s.persist());
    assertEquals(1, s.step);
    s.sync(9, Arrays.asList("tut"));
    assertEquals(1, s.step);
    assertTrue(s.advance(3));
    assertFalse(s.persist());
    assertFalse(s.canHint("shop"));
    assertTrue(s.seen.contains("craft"));
  }

  @Test
  public void newPlayerProgressIsMonotonicAndHintsRequireCompletedTraining() {
    NativeTutorialState s = new NativeTutorialState();
    s.sync(1, Arrays.asList("tut"));
    assertFalse(s.canHint("craft"));
    assertTrue(s.advance(2));
    assertFalse(s.advance(1));
    assertEquals(2, s.step);
    assertTrue(s.advance(9));
    assertTrue(s.canHint("craft"));
    s.seen.add("craft");
    assertFalse(s.canHint("craft"));
    assertTrue(s.canHint("shop"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidServerRewardBoundaryIsRejected() {
    new NativeTutorialState().advance(6);
  }
}
