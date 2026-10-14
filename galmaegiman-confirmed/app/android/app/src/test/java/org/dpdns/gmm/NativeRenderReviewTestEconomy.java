package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import org.json.*;
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
  public void drawAndCraftKeepPrimaryActionsOutsideScrollableContent() throws Exception {
    try (ActivityController<MainActivity> controller = review.start()) {
      MainActivity a = controller.get();
      for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        a.menus.economy.draw();
        review.await(() -> a.menus.pendingFetches == 0);
        review.capture(a, "economy-" + size[0] + "-draw", size[0], size[1]);
        LinearLayout footer = (LinearLayout) a.page.getChildAt(a.page.getChildCount() - 1);
        assertEquals("하급·중급·고급 실행 버튼", 3, footer.getChildCount());
        for (int i = 0; i < 3; i++) assertTrue(footer.getChildAt(i) instanceof Button);
        a.menus.economy.craft(false);
        review.await(() -> a.menus.pendingFetches == 0);
        review.capture(a, "economy-" + size[0] + "-craft", size[0], size[1]);
        assertEquals(a.page, a.menus.economy.craftActions.getParent());
        assertEquals("고정 수량과 만들기", 2, a.menus.economy.craftActions.getChildCount());
      }
    }
  }
}
