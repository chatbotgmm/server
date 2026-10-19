package org.dpdns.gmm;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 시스템 뒤로 가기도 화면의 복귀·종료 확인·처리 중 보호를 따른다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeBackNavigationTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  @Test
  public void systemBackReturnsFromLoginToTitle() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      h.login();
      assertEquals("login", h.screen);
      h.getOnBackPressedDispatcher().onBackPressed();
      assertEquals("title", h.screen);
      assertFalse(h.isFinishing());
    }
  }

  @Test
  public void systemBackKeepsExitConfirmation() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      h.getOnBackPressedDispatcher().onBackPressed();
      assertNotNull(h.visibleDialog);
      assertTrue(h.visibleDialog.isShowing());
      assertFalse(h.isFinishing());
    }
  }

  @Test
  public void systemBackWaitsForPendingOperation() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      h.login();
      h.busy = true;
      h.getOnBackPressedDispatcher().onBackPressed();
      assertEquals("login", h.screen);
      assertNull(h.visibleDialog);
      assertFalse(h.isFinishing());
    }
  }
}
