package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeTutorialLayoutTest {
  private void inside(NativeTutorialLayout.Box safe, NativeTutorialLayout.Box card) {
    assertTrue(card.left >= safe.left && card.top >= safe.top);
    assertTrue(card.right <= safe.right && card.bottom <= safe.bottom);
  }

  @Test public void commonLandscapeSizesKeepTargetAndCardSeparate() {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}, {640, 360}}) {
      NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(24, 16, size[0] - 24, size[1] - 24);
      for (int x : new int[] {24, size[0] / 2, size[0] - 124}) {
        for (int y : new int[] {16, size[1] / 2, size[1] - 84}) {
          NativeTutorialLayout.Box target = new NativeTutorialLayout.Box(x, y, x + 100, y + 60);
          NativeTutorialLayout.Placement p = NativeTutorialLayout.place(safe, target, 440, 300, 220, 120, 10);
          inside(safe, p.card);
          assertFalse(p.coversTarget);
          assertFalse(p.card.intersects(target));
        }
      }
    }
  }

  @Test public void screenshot1080x444WithClippedParentTopKeepsHeaderCardVisible() {
    // 사용자 캡처 비율 + 상위 뷰가 윗부분 36px를 자르는 경우를 회귀 조건으로 고정한다.
    // 36px는 재현용 가정이며 캡처만으로 실제 디바이스 원인을 확정하지 않는다.
    NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(48, 44, 1064, 436);
    NativeTutorialLayout.Box target = new NativeTutorialLayout.Box(60, 36, 130, 64);
    NativeTutorialLayout.Placement p = NativeTutorialLayout.place(safe, target, 440, 180, 220, 120, 10);
    inside(safe, p.card);
    assertTrue(p.card.top >= 44);
    assertFalse(p.coversTarget);
    assertFalse(p.card.intersects(target));
  }

  @Test public void cutoutAndKeyboardInsetsConstrainAllCardEdges() {
    NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(70, 30, 800, 230);
    NativeTutorialLayout.Placement p = NativeTutorialLayout.place(safe, null, 440, 300, 220, 120, 10);
    inside(safe, p.card);
    assertEquals(200, p.card.height());
    assertFalse(p.coversTarget);
  }

  @Test public void oversizedTargetRequiresExplicitRevealRatherThanTinyInstructions() {
    NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(8, 8, 907, 404);
    NativeTutorialLayout.Placement p = NativeTutorialLayout.place(safe,
        new NativeTutorialLayout.Box(0, 0, 915, 412), 440, 300, 220, 120, 10);
    inside(safe, p.card);
    assertTrue(p.coversTarget);
  }

  @Test public void narrowAndEmptySafeBoundsNeverOverflow() {
    for (int size : new int[] {0, 1, 40, 100}) {
      NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(12, 12, 12 + size, 12 + size);
      inside(safe, NativeTutorialLayout.place(safe, safe, 440, 300, 220, 120, 10).card);
    }
  }

  @Test public void movingOrMissingTargetRecalculatesPlacement() {
    NativeTutorialLayout.Box safe = new NativeTutorialLayout.Box(8, 8, 907, 404);
    NativeTutorialLayout.Placement left = NativeTutorialLayout.place(safe,
        new NativeTutorialLayout.Box(8, 8, 300, 404), 440, 300, 220, 120, 10);
    NativeTutorialLayout.Placement right = NativeTutorialLayout.place(safe,
        new NativeTutorialLayout.Box(610, 8, 907, 404), 440, 300, 220, 120, 10);
    assertTrue(left.card.left > right.card.left);
    assertFalse(NativeTutorialLayout.place(safe, null, 440, 300, 220, 120, 10).coversTarget);
  }
}
