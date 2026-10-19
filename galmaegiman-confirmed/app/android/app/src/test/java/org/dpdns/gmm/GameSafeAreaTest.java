package org.dpdns.gmm;

import static org.junit.Assert.*;

import androidx.core.graphics.Insets;
import org.junit.Test;

public class GameSafeAreaTest {
  @Test
  public void hiddenBarsDoNotShrinkFullScreenButCutoutRemains() {
    assertEquals(
        Insets.of(36, 10, 10, 10),
        GameSafeArea.combine(
            Insets.of(36, 0, 0, 0), Insets.NONE, Insets.NONE, Insets.of(10, 10, 10, 10)));
  }

  @Test
  public void visibleBarsAndKeyboardUseMaximumNotSum() {
    assertEquals(
        Insets.of(36, 24, 10, 170),
        GameSafeArea.combine(
            Insets.of(36, 0, 0, 0),
            Insets.of(0, 24, 0, 16),
            Insets.of(0, 0, 0, 170),
            Insets.of(10, 10, 10, 10)));
  }

  @Test
  public void rectangularSafeCornerStaysInsideRoundedScreen() {
    int safe = GameSafeArea.cornerInset(80);
    assertTrue(Math.hypot(80 - safe, 80 - safe) <= 80);
    assertTrue(Math.hypot(80 - safe + 1, 80 - safe + 1) > 80);
  }
}
