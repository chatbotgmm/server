package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;

/** Records existing mutation contracts; no request leaves the fake API. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeEconomyThemeContractTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTestEconomy controls = new NativeRenderReviewTestEconomy();

  @Before public void isolate() { flow.isolate(); }

  static JSONObject material() {
    return MainActivity.obj("id", "C1", "img", "C1", "name", "검토 재료", "tier", "COMMON", "usable", 4, "need", 2);
  }

  static JSONObject recipe() {
    return MainActivity.obj("id", "fixture-visible", "img", "C1", "name", "서버 공개 조합식",
        "tier", "LEGEND", "tierName", "전설", "ready", true, "max", 2,
        "materials", new JSONArray().put(material()));
  }

  static final class Api extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final List<JSONObject> writes = new ArrayList<>();
    final JSONObject craft = MainActivity.obj("recipes", new JSONArray().put(recipe()),
        "pool", new JSONArray().put(material()));
    final JSONObject me = MainActivity.obj("snack", 3000, "peanut", 3, "credits", 30,
        "party", new JSONArray(), "prices", MainActivity.obj(
            "tickets", MainActivity.obj("snack", 1000, "amount", 10),
            "peanutTickets", MainActivity.obj("peanut", 1, "amount", 20),
            "skip", MainActivity.obj("snack", 100), "maxParty", 10),
        "itemRules", MainActivity.obj("maxStack", 20), "itemDefs", new JSONArray(),
        "units", new JSONArray().put(MainActivity.obj("id", "D1", "name", "검토 대원", "top", true,
            "have", 3, "away", 1, "locked", true, "synergy", "BASIC")));
    Api(MainActivity host) { super(host); }
    @Override String token() { return "offline-economy-contract"; }
    @Override void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      if (body != null) {
        writes.add(body);
        if (path.equals("party")) done.done(new JSONObject(), null);
        return;
      }
      if (path.equals("native/roster60/recruit")) done.done(RegistrationIntegrationScreensTest.productRecruitment(), null);
      else if (path.equals("draw")) done.done(NativeRecruitmentProbabilityTest.serverEconomyFixture(), null);
      else if (path.equals("craft")) done.done(craft, null);
      else if (path.equals("me")) done.done(me, null);
      else if (path.equals("starter-loans")) done.done(StarterLoanTestFixture.ineligible(), null);
      else throw new AssertionError("Unexpected read " + path);
    }
  }

  Api prepare(MainActivity host) {
    host.api.close();
    Api api = new Api(host);
    host.api = api;
    host.guide.enabled = false;
    host.motionEnabled = false;
    host.getSharedPreferences("roster60_recruit", 0).edit().clear().commit();
    host.state = MainActivity.obj("me", api.me, "units", new JSONArray());
    return api;
  }

  void confirm(MainActivity host) {
    assertNotNull(host.visibleDialog);
    host.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
  }

  @Test public void missingTierMetadataCannotInventCraftEligibility() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      api.craft.getJSONArray("recipes").getJSONObject(0).remove("tier");
      api.craft.getJSONArray("pool").getJSONObject(0).remove("tier");
      host.menus.economy.craft(false);
      assertNull(host.root.findViewWithTag("craft-recipe-fixture-visible"));
      assertNotNull(host.root.findViewWithTag("craft-selection-empty"));
      host.menus.economy.craft(true);
      assertNull(host.root.findViewWithTag("mix-browse-card-C1"));
      assertTrue(host.menus.mixDraft.isEmpty());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void everyRecruitmentStationKeepsKindTimesAndOneConfirmedSpend() {
    for (String kind : RecruitmentProbabilityView.KINDS) try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      assertTrue("Current product route cannot be bypassed by this fixture", host.roster60Menus());
      host.menus.economy.draw();
      for (String choose : RecruitmentProbabilityView.KINDS)
        host.root.findViewWithTag("draw-card-" + choose).performClick();
      host.root.findViewWithTag("draw-card-" + kind).performClick();
      assertEquals(List.of("GET native/roster60/recruit"), api.requests);
      assertTrue(api.writes.isEmpty()); assertNull(host.visibleDialog);
      controls.button(host.root.findViewWithTag("draw-quantity"), "＋").performClick();
      Button submit = host.root.findViewWithTag("draw-submit");
      assertEquals("모집하기", submit.getText().toString());
      submit.performClick();
      assertTrue(api.writes.isEmpty());
      int cost = kind.equals("LOW") ? 2 : kind.equals("MID") ? 3 : 5;
      assertTrue(flow.labels(host.visibleDialog.getWindow().getDecorView()).contains("지원서 " + cost * 2 + "장"));
      confirm(host);
      assertEquals(List.of("GET native/roster60/recruit", "POST native/roster60/recruit"), api.requests);
      assertEquals(1, api.writes.size());
      JSONObject body = api.writes.get(0);
      assertEquals(4, body.length()); assertEquals(kind, body.optString("kind")); assertEquals(2, body.optInt("times"));
      assertEquals("product-fixture-v10", body.optString("catalogVersion"));
      assertFalse(body.optString("requestId").isEmpty());
      String key = NativeProgression.accountKey(BuildConfig.SERVER_URL + "|" + api.token());
      assertEquals(body.toString(), host.getSharedPreferences("roster60_recruit", 0).getString(key, ""));
      submit.performClick();
      assertEquals("Busy repeated taps never issue a second spend", 1, api.writes.size());
    }
  }

  @Test public void purchaseKeepsItemCountCostAndExplicitConfirmation() {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      host.menus.economy.shop();
      assertNotNull(host.root.findViewWithTag("market-counter"));
      View rail = host.root.findViewWithTag("shop-purchase-rail");
      controls.button(rail, "＋").performClick();
      host.menus.economy.shopActions.get("shop:tickets").performClick();
      assertTrue(api.writes.isEmpty());
      String prompt = flow.labels(host.visibleDialog.getWindow().getDecorView());
      assertTrue(prompt.contains("지원서 10장 2개"));
      assertTrue(prompt.contains("새우gang 2000개"));
      confirm(host);
      assertEquals(List.of("GET me", "POST shop"), api.requests);
      assertEquals(1, api.writes.size());
      JSONObject body = api.writes.get(0);
      assertEquals(2, body.length());
      assertEquals("tickets", body.optString("item"));
      assertEquals(2, body.optInt("count"));
      assertEquals(3000, api.me.optInt("snack"));
    }
  }

  @Test public void recipeKeepsOnlyServerVisibleIdAndOriginalTimesContract() {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      host.menus.recipeQuery = ""; host.menus.craftOnly = false;
      host.menus.economy.craft(false);
      View choice = host.root.findViewWithTag("craft-recipe-fixture-visible");
      assertNotNull(choice);
      assertNull(host.root.findViewWithTag("craft-recipe-fixture-hidden"));
      choice.performClick();
      assertNull(host.visibleDialog);
      controls.button(host.menus.economy.craftActions, "＋").performClick();
      controls.button(host.menus.economy.craftActions, "2개 만들기").performClick();
      assertTrue(api.writes.isEmpty());
      confirm(host);
      assertEquals(List.of("GET craft", "POST craft"), api.requests);
      JSONObject body = api.writes.get(0);
      assertEquals(2, body.length());
      assertEquals("fixture-visible", body.optString("id"));
      assertEquals(2, body.optInt("times"));
      assertEquals(4, api.craft.optJSONArray("pool").optJSONObject(0).optInt("usable"));
    }
  }

  @Test public void inlineMixKeepsDuplicatesInventoryLimitsAndIdsOnlyBody() {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      host.menus.mixDraft.clear();
      host.menus.economy.craft(true);
      host.root.findViewWithTag("mix-browse-card-C1").performClick();
      host.root.findViewWithTag("mix-browse-card-C1").performClick();
      assertEquals(List.of("C1", "C1"), host.menus.mixDraft);
      assertNull(host.visibleDialog);
      host.root.findViewWithTag("mix-submit").performClick();
      assertTrue(api.writes.isEmpty());
      confirm(host);
      assertEquals(List.of("GET craft", "POST mix"), api.requests);
      JSONObject body = api.writes.get(0);
      assertEquals(1, body.length());
      assertEquals("[\"C1\",\"C1\"]", body.optJSONArray("ids").toString());
      assertEquals(4, api.craft.optJSONArray("pool").optJSONObject(0).optInt("usable"));
    }
  }

  @Test public void expeditionKeepsOwnershipCapsSaveThenDepartureAndNoExtraWrites() {
    try (var controller = flow.start()) {
      MainActivity host = controller.get(); Api api = prepare(host);
      host.menus.economy.expedition();
      host.menus.economy.expeditionPreparation.open(-1);
      host.root.findViewWithTag("expedition-add-D1").performClick();
      host.root.findViewWithTag("expedition-add-D1").performClick();
      assertEquals(List.of("D1", "D1"), host.menus.economy.expeditionPreparation.draft.editing);
      assertTrue(host.expeditionDraft.isEmpty());
      assertFalse(host.root.findViewWithTag("expedition-add-D1").isEnabled());
      host.root.findViewWithTag("expedition-picker-accept").performClick();
      assertNull(host.root.findViewWithTag("expedition-depart"));
      assertTrue(api.writes.isEmpty());
      controls.button(host.root, "편성 저장").performClick();
      assertEquals(List.of("POST party"), api.requests);
      assertEquals("[\"D1\",\"D1\"]", api.writes.get(0).optJSONArray("ids").toString());
      assertEquals(1, api.writes.get(0).length());
      assertTrue(controls.button(host.root, "탐험 출발").isEnabled());
      controls.button(host.root, "탐험 출발").performClick();
      assertEquals(1, api.writes.size());
      confirm(host);
      assertEquals(List.of("POST party", "POST expedition/start"), api.requests);
      assertEquals(0, api.writes.get(1).length());
      assertEquals(3, api.me.optJSONArray("units").optJSONObject(0).optInt("have"));
      assertEquals(1, api.me.optJSONArray("units").optJSONObject(0).optInt("away"));
    }
  }
}
