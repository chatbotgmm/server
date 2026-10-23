package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.Rect;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Offline contract tests. No real account, shop, claim or network is used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w840dp-h344dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeExpeditionUiTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeEconomyThemeReviewTest layout = new NativeEconomyThemeReviewTest();
  static final long NOW = 1800000000000L;
  @Before public void isolate() { flow.isolate(); TemporaryUnitPresentation.reset(); }
  @After public void restore() { RuntimeEnvironment.setFontScale(1f); TemporaryUnitPresentation.reset(); }

  static final class Api extends NativeApi {
    JSONObject state;
    JSONObject nextWriteState;
    final List<String> calls = new ArrayList<>();
    final List<String> writes = new ArrayList<>();
    Result pending;
    Result pendingWrite;
    boolean defer;
    boolean deferWrites;
    String failure;
    String writeFailure;
    Api(MainActivity h, JSONObject state) { super(h); this.state = state; }
    @Override String token() { return "offline-expedition-contract"; }
    @Override void call(String path, JSONObject body, Result done) {
      calls.add(path);
      if (body != null) {
        writes.add(path + ":" + body);
        if (deferWrites) { pendingWrite = done; return; }
        finishWrite(done);
        return;
      }
      if (!path.equals("native/state")) throw new AssertionError(path);
      if (defer) { pending = done; return; }
      done.done(failure == null ? snapshot(state) : null, failure);
    }
    void finishWrite(Result done) {
      if (writeFailure != null) { done.done(null, writeFailure); return; }
      // Tests provide server snapshots; the fake does not recalculate game rewards or rules.
      if (nextWriteState != null) { state = nextWriteState; nextWriteState = null; }
      done.done(MainActivity.obj("result", "시험 처리 완료"), null);
    }
    void finishPendingWrite() {
      assertNotNull(pendingWrite);
      Result done = pendingWrite;
      pendingWrite = null;
      finishWrite(done);
    }
  }

  static JSONObject snapshot(JSONObject value) {
    try { return new JSONObject(value.toString()); }
    catch (JSONException error) { throw new AssertionError(error); }
  }

  void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

  void dialogButton(MainActivity h, int button) {
    assertNotNull(h.visibleDialog);
    h.visibleDialog.getButton(button).performClick();
    idle();
  }

  void dismissDialog(MainActivity h) {
    assertNotNull(h.visibleDialog);
    h.visibleDialog.dismiss();
    idle();
    assertNull(h.visibleDialog);
  }

  JSONObject completedSnapshot(Api api, int cost) throws Exception {
    JSONObject updated = snapshot(api.state);
    JSONObject me = updated.getJSONObject("me");
    me.put("snack", me.getInt("snack") - cost);
    me.getJSONObject("trip").put("endsAt", java.time.Instant.ofEpochMilli(NOW).toString());
    return updated;
  }

  JSONObject claimedSnapshot(Api api) throws Exception {
    JSONObject updated = snapshot(api.state);
    JSONObject me = updated.getJSONObject("me");
    me.remove("trip");
    me.put("snack", me.getInt("snack") + 155);
    me.getJSONArray("units").getJSONObject(0).put("away", 0);
    return updated;
  }

  JSONObject trip(boolean complete) {
    JSONArray ids = new JSONArray();
    for (int i = 0; i < 10; i++) ids.put("D1");
    return MainActivity.obj("startedAt", java.time.Instant.ofEpochMilli(NOW - 600000).toString(),
        "endsAt", java.time.Instant.ofEpochMilli(NOW + (complete ? -1 : 3000000)).toString(), "ids", ids,
        "plan", MainActivity.obj("size", 10, "baseSnacks", 100, "snackPercent", 55, "flatPerUnit", 0,
            "doublePercent", 30, "synergies", new JSONArray(), "countBonus", MainActivity.obj("percent", 25)));
  }

  Api prepare(MainActivity h, boolean complete) throws Exception {
    JSONObject unit = MainActivity.obj("id", "D1", "name", "긴 이름을 가진 검토 대원", "top", true,
        "have", 13, "away", 10, "permanentCount", 12, "temporaryCount", 1, "synergy", "BASIC");
    JSONObject state = MainActivity.obj("serverNow", NOW, "units", new JSONArray(), "me",
        MainActivity.obj("snack", 1000, "units", new JSONArray().put(unit), "party", new JSONArray(),
            "previousParty", new JSONArray().put("D1").put("D1").put("D1"), "trip", trip(complete),
            "prices", MainActivity.obj("maxParty", 10, "skip", MainActivity.obj("snack", 100))));
    h.api.close(); Api api = new Api(h, state); h.api = api; h.guide.enabled = false;
    h.motionEnabled = false; h.state = snapshot(state); TemporaryUnitPresentation.sync(NOW);
    h.menus.economy.expedition(); return api;
  }

  @Test public void tripUsesTwoRowsAndKeepsEveryDuplicateAtSupportedViewports() throws Exception {
    for (int[] size : new int[][] {{840,344}, {800,360}, {640,320}, {1280,720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      for (float scale : new float[] {1f, 1.3f, 2f}) {
        RuntimeEnvironment.setFontScale(scale);
        try (var controller = flow.start()) {
          MainActivity h = controller.get(); Api api = prepare(h, false);
          h.root.setPadding(h.dp(12), h.dp(4), h.dp(12), h.dp(8)); layout.layout(h, size);
          LinearLayout grid = h.root.findViewWithTag("expedition-selected-crew");
          assertEquals(2, grid.getChildCount());
          for (int i = 0; i < 10; i++) {
            View tile = h.root.findViewWithTag("expedition-member-" + i);
            assertNotNull(tile);
            assertTrue("Character retains positive area", ((LinearLayout) tile).getChildAt(0).getHeight() > 0);
            assertTrue(tile.getWidth() >= h.dp(48)); assertTrue(tile.getHeight() >= h.dp(48));
            Rect visible = new Rect(); assertTrue(tile.getGlobalVisibleRect(visible));
            assertEquals(tile.getWidth(), visible.width()); assertEquals(tile.getHeight(), visible.height());
          }
          assertEquals(5, ((LinearLayout) grid.getChildAt(0)).getChildCount());
          assertEquals(5, ((LinearLayout) grid.getChildAt(1)).getChildCount());
          View crew = h.root.findViewWithTag("expedition-crew-panel");
          LinearLayout status = h.root.findViewWithTag("expedition-status-panel");
          LinearLayout actions = h.root.findViewWithTag("expedition-trip-actions");
          assertSame(crew.getParent(), status.getParent());
          assertTrue(crew.getRight() <= status.getLeft());
          assertEquals(.68f, ((LinearLayout.LayoutParams) crew.getLayoutParams()).weight, .001f);
          assertEquals(.32f, ((LinearLayout.LayoutParams) status.getLayoutParams()).weight, .001f);
          assertSame(status, actions.getParent());
          assertEquals(LinearLayout.VERTICAL, actions.getOrientation());
          View details = h.root.findViewWithTag("expedition-trip-details");
          View action = h.root.findViewWithTag("expedition-trip-action");
          assertSame(actions, details.getParent()); assertSame(actions, action.getParent());
          assertTrue(details.getBottom() <= action.getTop());
          for (View button : new View[] {details, action}) {
            assertTrue(button.getHeight() >= h.dp(48));
            assertTrue(button.getWidth() >= h.dp(48));
            Rect bounds = new Rect(); button.getDrawingRect(bounds);
            status.offsetDescendantRectToMyCoords(button, bounds);
            assertTrue(new Rect(0, 0, status.getWidth(), status.getHeight()).contains(bounds));
            Rect visible = new Rect(); assertTrue(button.getGlobalVisibleRect(visible));
            assertEquals(button.getHeight(), visible.height());
          }
          assertTrue(api.calls.isEmpty());
        }
      }
    }
  }

  @Test public void storedPlanIgnoresLaterDraftAndCurrentUnitSynergy() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); prepare(h, false);
      h.expeditionDraft = new ArrayList<>(List.of("D2"));
      h.me().getJSONArray("units").getJSONObject(0).put("synergy", "DARK");
      assertEquals("기본 계산 · 155 새우gang", h.menus.economy.tripReward(h.me().getJSONObject("trip")));
      assertTrue(h.menus.economy.tripChances(h.me().getJSONObject("trip")).contains("30%"));
      assertEquals("출발 보상 정보 확인 필요", h.menus.economy.tripReward(MainActivity.obj()));
    }
  }

  @Test public void recentPartyExcludesTemporaryCopiesAndUnsavedDepartureStaysBlocked() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false); h.me().remove("trip");
      h.expeditionDraft = new ArrayList<>(); h.menus.economy.expedition();
      assertFalse(h.root.findViewWithTag("expedition-depart").isEnabled());
      h.root.findViewWithTag("expedition-more").performClick();
      new NativeRenderReviewTestEconomy().button(h.visibleDialog.getWindow().getDecorView(), "최근 편성").performClick();
      assertEquals(List.of("D1", "D1"), h.expeditionDraft);
      assertNull(h.root.findViewWithTag("expedition-depart"));
      h.root.findViewWithTag("expedition-member-0").performClick();
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      assertEquals(List.of("D1"), h.expeditionDraft);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void cancelAndBackDismissDoNotPurchase() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      assertNotNull(h.visibleDialog); h.visibleDialog.cancel(); idle();
      assertNull(h.visibleDialog);
      assertTrue(api.writes.isEmpty());
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      dialogButton(h, AlertDialog.BUTTON_NEGATIVE);
      assertNull(h.visibleDialog);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      assertNotNull(h.visibleDialog);
      h.visibleDialog.onBackPressed(); idle();
      assertNull(h.visibleDialog);
      assertEquals(3, api.calls.size());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void paidCompletionUsesConfirmedServerPriceAndRemainsSeparateFromClaim() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false);
      // The preflight must use the current server price, not the stale 100 in the rendered state.
      api.state.getJSONObject("me").getJSONObject("prices").getJSONObject("skip").put("snack", 137);
      api.nextWriteState = completedSnapshot(api, 137);
      JSONObject shown = h.me().getJSONObject("trip");
      h.menus.economy.tripAction(shown, false);
      TextView prompt = h.visibleDialog.findViewById(android.R.id.message);
      assertTrue(prompt.getText().toString().contains("137"));
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      assertEquals(1, api.writes.size()); assertTrue(api.writes.get(0).startsWith("shop:"));
      assertFalse(api.writes.get(0).contains("claim"));
      assertFalse(h.busy);
      assertEquals(863, h.me().getInt("snack"));
      assertEquals(10, h.me().getJSONArray("units").getJSONObject(0).getInt("away"));
      assertEquals(shown.getString("startedAt"), h.me().getJSONObject("trip").getString("startedAt"));
      assertEquals(shown.getJSONArray("ids").toString(), h.me().getJSONObject("trip").getJSONArray("ids").toString());
      assertEquals(shown.getJSONObject("plan").toString(), h.me().getJSONObject("trip").getJSONObject("plan").toString());
      assertEquals("보상 받기", ((Button) h.root.findViewWithTag("expedition-trip-action")).getText().toString());
      dialogButton(h, AlertDialog.BUTTON_POSITIVE); // acknowledge the successful purchase
      h.menus.economy.tripAction(shown, false); // stale purchase tap cannot buy the completed trip again
      assertEquals(1, api.writes.size());
      api.nextWriteState = claimedSnapshot(api);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), true);
      assertEquals(2, api.writes.size()); assertTrue(api.writes.get(1).startsWith("expedition/claim:"));
      assertFalse(h.busy); assertFalse(h.me().has("trip"));
      assertEquals(1018, h.me().getInt("snack"));
      assertEquals(0, h.me().getJSONArray("units").getJSONObject(0).getInt("away"));
      assertNotNull(h.root.findViewWithTag("expedition-depart"));
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
    }
  }

  @Test public void changedPriceOrCompletedTripDuringConfirmationDoesNotPurchase() throws Exception {
    for (boolean completed : new boolean[] {false, true}) {
      try (var controller = flow.start()) {
        MainActivity h = controller.get(); Api api = prepare(h, false);
        h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
        if (completed) api.state.getJSONObject("me").put("trip", trip(true));
        else api.state.getJSONObject("me").getJSONObject("prices").getJSONObject("skip").put("snack", 101);
        dialogButton(h, AlertDialog.BUTTON_POSITIVE);
        assertEquals(2, api.calls.size()); // both preflight reads actually ran
        assertFalse(h.busy);
        assertTrue(api.writes.isEmpty());
      }
    }
  }

  @Test public void insufficientBalanceAndReadFailureRecoverWithoutWrites() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false); api.state.getJSONObject("me").put("snack", 99);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      assertTrue(api.writes.isEmpty()); dismissDialog(h);
      api.failure = "시험 조회 실패";
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      assertFalse(h.busy); assertTrue(api.writes.isEmpty()); dismissDialog(h);
      api.failure = null; api.state.getJSONObject("me").put("trip", trip(true));
      api.nextWriteState = claimedSnapshot(api);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), true);
      assertEquals(1, api.writes.size()); assertTrue(api.writes.get(0).startsWith("expedition/claim:"));
      assertFalse(h.busy); assertFalse(h.me().has("trip"));
    }
  }

  @Test public void duplicateTapsDuringPreflightMakeOneReadAndAlreadyClaimedMakesNoWrite() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, true); api.defer = true;
      JSONObject shown = h.me().getJSONObject("trip");
      h.menus.economy.tripAction(shown, true); h.menus.economy.tripAction(shown, true);
      assertEquals(1, api.calls.size()); api.state.getJSONObject("me").remove("trip");
      api.pending.done(snapshot(api.state), null); idle();
      assertFalse(h.busy); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void purchaseFailureReleasesBusyAndRetryRequiresFreshConfirmation() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false);
      JSONObject shown = h.me().getJSONObject("trip");
      api.deferWrites = true; api.writeFailure = "연결하지 못했어요. 시험 구매 실패";
      h.menus.economy.tripAction(shown, false); dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      assertTrue(h.busy); assertEquals(1, api.writes.size());
      h.menus.economy.tripAction(shown, false);
      assertEquals(1, api.writes.size());
      api.finishPendingWrite(); idle();
      assertFalse(h.busy); assertEquals(1000, h.me().getInt("snack"));
      assertEquals(shown.getString("endsAt"), h.me().getJSONObject("trip").getString("endsAt"));
      assertEquals(1, api.writes.size()); // network failure cannot automatically repeat a purchase
      dialogButton(h, AlertDialog.BUTTON_NEGATIVE);
      api.deferWrites = false; api.writeFailure = null; api.nextWriteState = completedSnapshot(api, 100);
      int readsBeforeRetry = Collections.frequency(api.calls, "native/state");
      h.menus.economy.tripAction(shown, false);
      assertEquals(1, api.writes.size()); assertNotNull(h.visibleDialog);
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      assertEquals(2, api.writes.size()); assertFalse(h.busy);
      assertEquals(readsBeforeRetry + 3, Collections.frequency(api.calls, "native/state"));
      assertEquals(900, h.me().getInt("snack"));
      assertEquals(10, h.me().getJSONArray("units").getJSONObject(0).getInt("away"));
    }
  }

  @Test public void claimFailureKeepsCrewAwayAndSuccessfulRetryReturnsThemOnce() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, true);
      JSONObject shown = h.me().getJSONObject("trip");
      api.deferWrites = true; api.writeFailure = "시험 수령 실패";
      h.menus.economy.tripAction(shown, true);
      assertTrue(h.busy); assertEquals(1, api.writes.size());
      h.menus.economy.tripAction(shown, true);
      assertEquals(1, api.writes.size());
      api.finishPendingWrite(); idle();
      assertFalse(h.busy); assertTrue(h.me().has("trip"));
      assertEquals(1000, h.me().getInt("snack"));
      assertEquals(10, h.me().getJSONArray("units").getJSONObject(0).getInt("away"));
      dismissDialog(h);
      api.deferWrites = false; api.writeFailure = null; api.nextWriteState = claimedSnapshot(api);
      h.menus.economy.tripAction(shown, true);
      assertEquals(2, api.writes.size()); assertFalse(h.busy); assertFalse(h.me().has("trip"));
      assertEquals(1155, h.me().getInt("snack"));
      assertEquals(0, h.me().getJSONArray("units").getJSONObject(0).getInt("away"));
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      h.menus.economy.tripAction(shown, true);
      assertEquals(2, api.writes.size()); assertFalse(h.busy);
      assertEquals(1155, h.me().getInt("snack"));
    }
  }

  @Test public void leavingDuringPreflightDiscardsTheLateResult() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, true); api.defer = true;
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), true);
      assertTrue(h.busy);
      h.base("다른 화면", "home");
      api.pending.done(snapshot(api.state), null); idle();
      assertEquals("home", h.screen);
      assertFalse(h.busy); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void resumeDismissesOldPurchaseConfirmationAndRefreshesWithoutPurchase() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      assertNotNull(h.visibleDialog);
      h.menus.economy.refreshExpeditionOnResume(); idle();
      assertNull(h.visibleDialog);
      assertEquals("expedition", h.screen);
      assertEquals(2, api.calls.size()); assertTrue(api.writes.isEmpty());
      api.defer = true;
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), false);
      h.menus.economy.refreshExpeditionOnResume();
      assertEquals(3, api.calls.size()); // an in-flight read is not duplicated on resume
    }
  }

  @Test public void claimSuccessWithReloadFailureNeverRepeatsTheClaim() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, true);
      api.deferWrites = true; api.nextWriteState = claimedSnapshot(api);
      h.menus.economy.tripAction(h.me().getJSONObject("trip"), true);
      api.failure = "시험 결과 조회 실패";
      api.finishPendingWrite(); idle();
      assertFalse(h.busy); assertEquals(1, api.writes.size());
      assertNotNull(h.visibleDialog); // acknowledge the successful write, then reload only
      api.failure = null;
      dialogButton(h, AlertDialog.BUTTON_POSITIVE);
      assertFalse(h.me().has("trip")); assertEquals(1155, h.me().getInt("snack"));
      assertEquals(1, api.writes.size());
      assertNotNull(h.root.findViewWithTag("expedition-depart"));
    }
  }

  @Test public void preparationCandidateCancelBackAndSaveRemainSeparate() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h, false);
      h.me().remove("trip"); h.expeditionDraft = new ArrayList<>(); h.menus.economy.expedition();
      NativeExpeditionPreparation prep = h.menus.economy.expeditionPreparation;
      assertNull(h.root.findViewWithTag("expedition-candidates"));
      h.root.findViewWithTag("expedition-empty-0").performClick();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      assertEquals(List.of("D1", "D1"), prep.draft.editing);
      assertTrue(h.expeditionDraft.isEmpty()); assertTrue(api.writes.isEmpty());
      h.back(); assertFalse(prep.draft.active()); assertTrue(h.expeditionDraft.isEmpty());
      assertTrue(api.writes.isEmpty());
      h.root.findViewWithTag("expedition-empty-0").performClick();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      h.root.findViewWithTag("expedition-picker-cancel").performClick();
      assertTrue(h.expeditionDraft.isEmpty());
      prep.open(-1); h.root.findViewWithTag("expedition-add-D1").performClick();
      View oldAccept = h.root.findViewWithTag("expedition-picker-accept");
      oldAccept.performClick(); oldAccept.performClick();
      assertEquals(List.of("D1"), h.expeditionDraft); assertTrue(api.writes.isEmpty());
      assertNull(h.root.findViewWithTag("expedition-depart"));
      api.deferWrites = true;
      h.root.findViewWithTag("expedition-save").performClick();
      h.root.findViewWithTag("expedition-save").performClick();
      prep.open(-1); assertFalse(prep.draft.active());
      assertEquals(1, api.writes.size()); assertTrue(api.writes.get(0).startsWith("party:"));
      api.finishPendingWrite(); idle();
      assertNotNull(h.root.findViewWithTag("expedition-depart")); assertEquals(1, api.writes.size());
      h.root.findViewWithTag("expedition-depart").performClick();
      assertEquals(1, api.writes.size()); dialogButton(h, AlertDialog.BUTTON_NEGATIVE);
      h.root.findViewWithTag("expedition-member-0").performLongClick();
      assertNotNull(h.visibleDialog); dismissDialog(h); assertEquals(List.of("D1"), h.expeditionDraft);
      h.root.findViewWithTag("expedition-member-0").performClick();
      dialogButton(h, AlertDialog.BUTTON_NEUTRAL); // replacement is still a cancellable local session
      assertTrue(prep.draft.active()); assertTrue(prep.draft.editing.isEmpty());
      h.back(); assertEquals(List.of("D1"), h.expeditionDraft); assertEquals(1, api.writes.size());
    }
  }

  @Test public void preparationBoardAndPickerKeepTargetsInsideSafeArea() throws Exception {
    for (int[] size : new int[][] {{840,344}, {800,360}, {568,320}}) {
      for (float scale : new float[] {1f, 1.5f, 2f}) {
        RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
        RuntimeEnvironment.setFontScale(scale);
        try (var controller = flow.start()) {
          MainActivity h = controller.get(); Api api = prepare(h, false);
          h.me().remove("trip"); JSONArray units = new JSONArray();
          for (int i = 1; i <= 8; i++) units.put(MainActivity.obj("id", "D" + i, "name", "긴 이름 대원 " + i,
              "top", true, "have", 3, "permanentCount", 3, "away", 0, "synergy", "BASIC"));
          h.me().put("units", units); h.expeditionDraft = new ArrayList<>(List.of("D1", "D2"));
          h.menus.economy.expedition(); NativeExpeditionPreparation prep = h.menus.economy.expeditionPreparation;
          h.root.setPadding(h.dp(12), h.dp(4), h.dp(12), h.dp(8));
          for (boolean picking : new boolean[] {false, true}) {
            if (picking) prep.open(-1);
            layout.layout(h, size);
            for (View view : layout.geometry.descendants(h.root)) {
              if (!view.isShown() || !(view instanceof Button || String.valueOf(view.getTag()).startsWith("expedition-member-")
                  || String.valueOf(view.getTag()).startsWith("expedition-empty-"))) continue;
              assertTrue(view.getWidth() >= h.dp(48)); assertTrue(view.getHeight() >= h.dp(48));
              Rect visible = new Rect(); assertTrue(view.getGlobalVisibleRect(visible));
              assertEquals(view.getWidth(), visible.width()); assertEquals(view.getHeight(), visible.height());
            }
            if (picking) {
              int count = 0;
              for (View view : layout.geometry.descendants(h.root.findViewWithTag("expedition-candidates")))
                if (String.valueOf(view.getTag()).startsWith("expedition-add-")) count++;
              assertEquals(prep.perPage(), count);
              assertNull(h.root.findViewWithTag("expedition-selected-crew"));
            } else assertNull(h.root.findViewWithTag("expedition-candidates"));
          }
          assertTrue(api.writes.isEmpty());
        }
      }
    }
  }
}
