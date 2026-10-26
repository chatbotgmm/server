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
  public void headerBackKeepsArrowWhileLoadingAndBlocksRepeatedNavigation() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      PendingApi api = prepareHeader(host);
      Button back = (Button) ((android.view.ViewGroup) host.page.getChildAt(0)).getChildAt(0);
      Button money = (Button) ((android.view.ViewGroup) host.page.getChildAt(0)).getChildAt(2);
      back.performClick();
      assertEquals("native/state", api.lastPath);
      assertEquals("‹", back.getText().toString());
      assertEquals("뒤로", back.getContentDescription().toString());
      assertFalse(back.isEnabled());
      assertTrue(host.busy);
      back.performClick();
      money.performClick();
      assertEquals("뒤로와 재화를 연속 눌러도 이동 요청은 한 번", 1, api.calls);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
      View overlay = host.root.findViewWithTag("request-progress");
      assertNotNull(overlay);
      assertEquals("진행 불러오는 중…", overlay.getContentDescription().toString());
      assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, overlay.getAccessibilityLiveRegion());
      assertEquals("‹", back.getText().toString());
      api.fail("테스트 연결 오류");
      assertFalse(host.busy);
      assertTrue(back.isEnabled());
      assertEquals("‹", back.getText().toString());
      assertEquals("뒤로", back.getContentDescription().toString());
      assertNull(host.root.findViewWithTag("request-progress"));
    }
  }

  @Test
  public void allHeaderCurrenciesKeepValuesAndAccessibleNamesDuringLoading() {
    for (int index = 0; index < 3; index++) {
      try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
        MainActivity host = controller.get();
        PendingApi api = prepareHeader(host);
        Button money =
            (Button) ((android.view.ViewGroup) host.page.getChildAt(0)).getChildAt(index + 2);
        String expected = new String[] {"6,483", "0", "152"}[index];
        CharSequence description = money.getContentDescription();
        money.performClick();
        assertEquals("native/state", api.lastPath);
        assertEquals(expected, money.getText().toString());
        assertEquals(description, money.getContentDescription());
        assertFalse(money.isEnabled());
        assertTrue(host.busy);
        money.performClick();
        assertEquals(1, api.calls);
        api.fail("테스트 연결 오류");
        assertEquals(expected, money.getText().toString());
        assertEquals(description, money.getContentDescription());
        assertTrue(money.isEnabled());
        assertFalse(host.busy);
        assertNull(host.root.findViewWithTag("request-progress"));
      }
    }
  }

  @Test
  public void preservedValueUpdatedDuringRequestIsNotRolledBackOnCompletion() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button money = host.button("6,483", () -> {});
      JournalButton.keepLabelDuringOperation(money);
      money.setContentDescription("새우 6,483");
      int operation = host.operations.begin("진행 불러오는 중…", money);
      assertEquals("6,483", money.getText().toString());
      money.setText("6,500");
      money.setContentDescription("새우 6,500");
      assertTrue(host.operations.finish(operation));
      assertEquals("6,500", money.getText().toString());
      assertEquals("새우 6,500", money.getContentDescription().toString());
      assertTrue(money.isEnabled());
    }
  }

  @Test
  public void preservedBackAlsoKeepsLabelDuringSaveBeforeLeavingAndClose() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      prepareHeader(host);
      Button back = (Button) ((android.view.ViewGroup) host.page.getChildAt(0)).getChildAt(0);
      // 탐험 편성 저장처럼 뒤로 이동 전에 실행되는 요청도 같은 버튼을 사용한다.
      int operation = host.operations.begin("편성 저장 중…", back);
      assertEquals("‹", back.getText().toString());
      assertEquals("뒤로", back.getContentDescription().toString());
      assertFalse(back.isEnabled());
      host.operations.close();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
      assertFalse(host.operations.finish(operation));
      assertEquals("‹", back.getText().toString());
      assertTrue(back.isEnabled());
      assertNull(host.root.findViewWithTag("request-progress"));
    }
  }

  @Test
  public void switchingBetweenPreservedValuesAndCommandLabelsDoesNotLeakPolicy() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button money = host.button("152", () -> {});
      JournalButton.keepLabelDuringOperation(money);
      Button save = host.button("편성 저장", () -> {});
      int stale = host.operations.begin("진행 불러오는 중…", money);
      int active = host.operations.begin("편성 저장 중…", save);
      assertEquals("152", money.getText().toString());
      assertTrue(money.isEnabled());
      assertEquals("편성 저장 중…", save.getText().toString());
      assertFalse(save.isEnabled());
      assertFalse(host.operations.finish(stale));
      assertTrue(host.operations.finish(active));
      assertEquals("편성 저장", save.getText().toString());
      int next = host.operations.begin("진행 불러오는 중…", money);
      assertEquals("152", money.getText().toString());
      assertTrue(host.operations.finish(next));
      assertEquals("152", money.getText().toString());
    }
  }

  @Test
  public void preservingLabelDoesNotEnableAnOriginallyDisabledValue() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      Button money = host.button("0", () -> {});
      JournalButton.keepLabelDuringOperation(money);
      money.setEnabled(false);
      int operation = host.operations.begin("진행 불러오는 중…", money);
      assertEquals("0", money.getText().toString());
      assertTrue(host.operations.finish(operation));
      assertFalse(money.isEnabled());
    }
  }

  PendingApi prepareHeader(MainActivity host) {
    host.api.close();
    PendingApi api = new PendingApi(host);
    host.api = api;
    // These header tests measure one warm navigation read; the cold-key barrier is exercised below.
    host.art.key = "operation-ui-warm-art-key";
    host.guide.enabled = false;
    host.state =
        MainActivity.obj(
            "me", MainActivity.obj("snack", 6483, "peanut", 0, "credits", 152));
    host.base("게임 모드", "modes");
    return api;
  }

  static final class PendingApi extends NativeApi {
    int calls;
    String lastPath;
    Result pending;
    final java.util.Map<String, Result> pendingByPath = new java.util.HashMap<>();
    final java.util.List<String> paths = new java.util.ArrayList<>();

    PendingApi(MainActivity host) {
      super(host);
    }

    @Override
    String token() {
      return "test-player.test-token";
    }

    @Override
    void call(String path, org.json.JSONObject body, Result callback) {
      calls++;
      lastPath = path;
      pending = callback;
      paths.add(path);
      assertNull("A pending path must not be issued twice", pendingByPath.put(path, callback));
    }

    void fail(String error) {
      Result callback = pending;
      pending = null;
      pendingByPath.clear();
      callback.done(null, error);
    }

    void complete(String path, org.json.JSONObject data, String error) {
      Result callback = pendingByPath.remove(path);
      assertNotNull("Expected pending read " + path, callback);
      callback.done(data, error);
    }
  }

  @Test public void coldHeaderNavigationWaitsForBothReadsAndRepeatedTapsCannotDuplicateThem() {
    try (ActivityController<MainActivity> controller = new NativeGameFlowTest().start()) {
      MainActivity host = controller.get();
      PendingApi api = prepareHeader(host);
      host.art.key = "";
      Button back = (Button) ((android.view.ViewGroup) host.page.getChildAt(0)).getChildAt(0);
      org.json.JSONObject stateBefore = host.state;
      back.performClick();
      assertEquals(java.util.Arrays.asList("native/state", "duel-key"), api.paths);
      assertTrue(host.busy);
      assertFalse(back.isEnabled());
      api.complete("duel-key", MainActivity.obj("k", "prepared-cold-key"), null);
      assertTrue("The key alone cannot finish authoritative navigation", host.busy);
      assertFalse(back.isEnabled());
      back.performClick();
      assertEquals(2, api.calls);
      assertEquals("", host.art.key);
      api.complete("native/state", null, "테스트 연결 오류");
      assertFalse(host.busy);
      assertTrue(back.isEnabled());
      assertSame(stateBefore, host.state);
      assertEquals("Failed state verification cannot install a partial key", "", host.art.key);
      assertEquals(2, api.calls);
    }
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
