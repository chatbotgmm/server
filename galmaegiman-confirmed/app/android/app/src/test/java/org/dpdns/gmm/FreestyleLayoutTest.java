package org.dpdns.gmm;
import org.junit.Test;
import static org.junit.Assert.*;
public class FreestyleLayoutTest {
  @Test public void normalLandscapeKeepsGrid() {
    assertFalse(FreestyleLayout.compact(875,394,1));
    assertFalse(FreestyleLayout.compact(1192,702,1));
  }
  @Test public void narrowShortAndLargeFontUseModalNotTinyCells() {
    assertTrue(FreestyleLayout.compact(320,394,1));
    assertTrue(FreestyleLayout.compact(875,280,1));
    assertTrue(FreestyleLayout.compact(875,394,1.5f));
    assertTrue(FreestyleLayout.compact(320,280,2));
  }
}
