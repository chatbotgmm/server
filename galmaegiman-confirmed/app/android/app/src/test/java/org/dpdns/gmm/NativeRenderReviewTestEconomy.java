package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import org.json.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** 경제 페이지의 시간 경계와 고정 행동, 최근 탐험대 편성을 실제 Android View로 확인한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRenderReviewTestEconomy {
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before
  public void scheduledFramesUseThePausedLooperClock() {
    review.scheduledFramesUseThePausedLooperClock();
  }

  View taggedPrefix(View root, String prefix) {
    if (root.getTag() instanceof String && ((String) root.getTag()).startsWith(prefix)) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = taggedPrefix(group.getChildAt(i), prefix);
        if (found != null) return found;
      }
    }
    return null;
  }

  Button button(View root, String text) {
    if (root instanceof Button && ((Button) root).getText().toString().equals(text))
      return (Button) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = button(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test
  public void completedTripChangesActionWithoutReloadAndStopsTickerOnLeave() throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity a = controller.get();
      long now = System.currentTimeMillis();
      a.me()
          .put(
              "trip",
              MainActivity.obj(
                  "startedAt",
                  java.time.Instant.ofEpochMilli(now - 30000).toString(),
                  "endsAt",
                  java.time.Instant.ofEpochMilli(now + 1500).toString(),
                  "ids",
                  MainActivity.arr(java.util.List.of("D1", "T1"))));
      a.menus.economy.expedition();
      review.capture(a, "economy-trip-waiting", 915, 412);
      assertNotNull(button(a.root, "바로 귀환"));
      Thread.sleep(1800);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
      Button reward = button(a.root, "탐험 보상 받기");
      assertNotNull("귀환 경계에서 서버 재호출 없이 보상 버튼으로 갱신", reward);
      review.capture(a, "economy-trip-ready", 915, 412);
      int epoch = a.epoch;
      a.show("home");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
      assertTrue(a.epoch > epoch);
      assertEquals("home", a.screen);
    }
  }

  @Test
  public void unchangedPartyDisablesSaveAndDraftRequiresSaveBeforeDeparture() throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity a = controller.get();
      a.me().put("trip", JSONObject.NULL);
      JSONArray units = a.list(a.me(), "units");
      String owned = "";
      for (int i = 0; i < units.length(); i++) {
        JSONObject u = units.optJSONObject(i);
        if (u.optBoolean("top") && u.optInt("have") - u.optInt("away") > 0) {
          owned = u.optString("id");
          break;
        }
      }
      assertFalse("탐험 가능한 검토 유닛 필요", owned.isEmpty());
      a.me().put("party", MainActivity.arr(java.util.List.of(owned)));
      a.expeditionDraft = null;
      a.menus.economy.expedition();
      review.capture(a, "economy-expedition-saved", 915, 412);
      assertNotNull(a.root.findViewWithTag("expedition-summary").getBackground());
      View saveStatus = a.root.findViewWithTag("expedition-save-status");
      assertNotNull(((View) saveStatus.getParent()).getBackground());
      assertFalse(button(a.root, "편성 저장").isEnabled());
      assertTrue(button(a.root, "탐험 출발").isEnabled());
      button(a.root, "비우기").performClick();
      assertTrue(button(a.root, "편성 저장").isEnabled());
      assertFalse(button(a.root, "탐험 출발").isEnabled());
      assertTrue(a.expeditionDirty);
      a.show("home");
    }
  }

  @Test
  public void drawKeepsQuantityTotalAndSubmitInTheSameCardAndCraftKeepsItsFooter()
      throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity a = controller.get();
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        review.viewport(a, size[0], size[1]);
        a.menus.economy.draw();
        review.await(() -> a.menus.pendingFetches == 0);
        review.capture(a, "economy-" + size[0] + "-draw", size[0], size[1]);
        for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
          LinearLayout card = a.root.findViewWithTag("draw-card-" + kind);
          LinearLayout quantity = a.root.findViewWithTag("draw-quantity-" + kind);
          Button submit = a.root.findViewWithTag("draw-submit-" + kind);
          TextView total = a.root.findViewWithTag("draw-total-" + kind);
          assertSame("실행은 해당 등급 카드 안에", card, submit.getParent());
          assertSame("수량은 같은 카드 안에", card, quantity.getParent());
          assertSame("총 비용은 같은 카드 안에", card, total.getParent());
          assertTrue(quantity.getBottom() <= total.getTop());
          assertTrue(total.getBottom() <= submit.getTop());
          assertTrue("수량·비용·실행은 이어서 배치", submit.getBottom() - quantity.getTop() < a.dp(145));
          String before = total.getText().toString();
          button(quantity, "＋").performClick();
          assertNotEquals("수량 조정에 총 비용 동시 갱신", before, total.getText().toString());
          assertEquals("2회 뽑기", submit.getText().toString());
        }
        Button high = a.root.findViewWithTag("draw-submit-HIGH");
        high.performClick();
        assertNotNull("재화 사용 확인 유지", a.visibleDialog);
        TextView confirmation = a.visibleDialog.findViewById(android.R.id.message);
        assertTrue(confirmation.getText().toString().contains("고급 2회"));
        assertTrue(confirmation.getText().toString().contains("10장"));
        a.visibleDialog.dismiss();
        review.viewport(a, size[0], size[1]);
        a.menus.economy.craft(false);
        review.await(() -> a.menus.pendingFetches == 0);
        review.capture(a, "economy-" + size[0] + "-craft", size[0], size[1]);
        assertEquals(a.page, a.menus.economy.craftActions.getParent());
        assertEquals("고정 수량과 만들기", 2, a.menus.economy.craftActions.getChildCount());
      }
    }
  }

  @Test
  public void emptyMixSlotOpensAvailableMaterialPickerAndSelectionHasExplicitRemove()
      throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity a = controller.get();
      a.menus.mixDraft.clear();
      a.menus.economy.craft(true);
      review.await(() -> a.menus.pendingFetches == 0);
      review.capture(a, "economy-mix-empty", 915, 412);
      View empty = a.root.findViewWithTag("mix-empty-slot-0");
      assertNotNull(empty);
      assertTrue("빈 + 슬롯도 실제 선택 동작 제공", empty.performClick());
      assertNotNull(a.visibleDialog);
      android.app.AlertDialog pickerDialog = a.visibleDialog;
      View picker = taggedPrefix(pickerDialog.getWindow().getDecorView(), "mix-picker-material-");
      assertNotNull("사용할 수 있는 검토 재료 필요", picker);
      String id = picker.getTag().toString().substring("mix-picker-material-".length());
      picker.performClick();
      assertEquals(java.util.List.of(id), a.menus.mixDraft);
      assertFalse("재료 선택 후 실제 선택기 닫기", pickerDialog.isShowing());
      // Dialog의 OnDismissListener는 메인 큐에서 전달되므로 실제 종료 뒤 참조 정리도 확인한다.
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertNull("닫기 콜백 이후 선택기 참조 정리", a.visibleDialog);
      View remove = a.root.findViewWithTag("mix-material-" + id);
      assertNotNull("선택한 재료는 고정 슬롯에서 직접 뺄 수 있음", remove);
      assertTrue(remove.getContentDescription().toString().contains("재료에서 빼기"));
      assertTrue(remove.isClickable());
      review.capture(a, "economy-mix-picked", 915, 412);
      remove.performClick();
      assertTrue(a.menus.mixDraft.isEmpty());
      assertNotNull(a.root.findViewWithTag("mix-empty-slot-0"));
      a.show("home");
    }
  }
}
