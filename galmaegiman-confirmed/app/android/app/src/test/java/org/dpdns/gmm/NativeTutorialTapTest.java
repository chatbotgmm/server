package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeTutorialTapTest {
  @Test public void targetTapActivatesOnce() {
    NativeTutorialTap tap = new NativeTutorialTap();
    tap.down(40, 40, true);
    tap.move(43, 42, 8);
    assertTrue(tap.up(true));
    assertFalse(tap.up(true));
  }

  @Test public void dragNeverTurnsIntoClickAfterReturningToTarget() {
    NativeTutorialTap tap = new NativeTutorialTap();
    tap.down(40, 40, true);
    tap.move(60, 40, 8);
    tap.move(40, 40, 8);
    assertFalse(tap.up(true));
  }

  @Test public void outsideStartDisabledTargetAndOutsideEndCannotActivate() {
    NativeTutorialTap tap = new NativeTutorialTap();
    tap.down(40, 40, false);
    assertFalse(tap.up(true));
    tap.down(40, 40, true);
    assertFalse(tap.up(false));
  }

  @Test public void cancellationOrSecondPointerClearsPendingClick() {
    NativeTutorialTap tap = new NativeTutorialTap();
    tap.down(40, 40, true);
    tap.cancel();
    assertFalse(tap.up(true));
  }
}
