package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 계정·네트워크 없이 요청 피드백과 완료 순서·생명주기 복구를 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeOperationUiTest {
  @Before
  public void isolate() {
    new NativeGameFlowTest().isolate();
    new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock();
  }

  @Test
  public void immediateButtonFeedbackPrecedesDelayedProgressAndRestoresOnCompletion() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button save = host.button("편성 저장", () -> {});
      save.setContentDescription("선택한 수비대 편성 저장");
      host.root.addView(save);
      int operation = host.operations.begin("편성 저장 중…", save);
      assertEquals("편성 저장 중…", save.getText().toString());
      assertFalse("요청 시작 즉시 중복 입력 방지", save.isEnabled());
      assertNull(host.root.findViewWithTag("request-progress"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(179));
      assertNull("짧은 응답에서는 진행 창 깜박임 없음", host.root.findViewWithTag("request-progress"));
      // Window의 traversal barrier가 다음 vsync에서 해제되는 시간도 보낸다.
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      View overlay = host.root.findViewWithTag("request-progress");
      assertNotNull("지연되는 요청에는 진행 안내 표시", overlay);
      assertTrue(overlay.isClickable());
      assertEquals(
          "편성 저장 중…",
          ((TextView) overlay.findViewWithTag("request-progress-label")).getText().toString());
      assertTrue(host.operations.finish(operation));
      assertNull(host.root.findViewWithTag("request-progress"));
      assertEquals("편성 저장", save.getText().toString());
      assertEquals("선택한 수비대 편성 저장", save.getContentDescription().toString());
      assertTrue(save.isEnabled());
    }
  }

  @Test
  public void fastCompletionDoesNotLeaveDelayedOverlayOrEnableOriginallyDisabledButton() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button claim = host.button("보상 받기", () -> {});
      claim.setEnabled(false);
      int operation = host.operations.begin("보상 받는 중…", claim);
      assertTrue(host.operations.finish(operation));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      assertNull(host.root.findViewWithTag("request-progress"));
      assertEquals("보상 받기", claim.getText().toString());
      assertFalse("원래 잠겨 있던 행동을 완료 처리로 풀지 않음", claim.isEnabled());
    }
  }

  @Test
  public void staleCompletionCannotDismissNewerRequestOrRestoreItsButton() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button first = host.button("편성 저장", () -> {});
      Button second = host.button("탐험 출발", () -> {});
      int oldOperation = host.operations.begin("편성 저장 중…", first);
      int currentOperation = host.operations.begin("탐험 출발 중…", second);
      assertTrue("다음 요청 시작 시 이전 버튼 복원", first.isEnabled());
      assertEquals("편성 저장", first.getText().toString());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(180));
      View overlay = host.root.findViewWithTag("request-progress");
      assertNotNull(overlay);
      assertFalse(host.operations.finish(oldOperation));
      assertSame("늦은 응답이 현재 요청 창을 제거하지 않음", overlay, host.root.findViewWithTag("request-progress"));
      assertFalse(second.isEnabled());
      assertEquals("탐험 출발 중…", second.getText().toString());
      assertTrue(host.operations.finish(currentOperation));
      assertTrue(second.isEnabled());
      assertEquals("탐험 출발", second.getText().toString());
      assertNull(host.root.findViewWithTag("request-progress"));
    }
  }

  @Test
  public void closeCancelsPendingFeedbackAndRejectsLateCompletion() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button save = host.button("편성 저장", () -> {});
      int pending = host.operations.begin("편성 저장 중…", save);
      host.operations.close();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      assertNull(host.root.findViewWithTag("request-progress"));
      assertFalse(host.operations.finish(pending));
      assertTrue(save.isEnabled());
      assertEquals("편성 저장", save.getText().toString());
    }
  }
}
