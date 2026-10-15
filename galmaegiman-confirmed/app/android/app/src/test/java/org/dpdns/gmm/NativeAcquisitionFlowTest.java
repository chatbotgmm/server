package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** 이번 획득 키와 기존 성장 기록을 구분하고, 이동을 소비 요청 없이 처리한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeAcquisitionFlowTest {
  final NativeGameFlowTest fixtures = new NativeGameFlowTest();

  @Before
  public void isolate() {
    fixtures.isolate();
  }

  @After
  public void resetFontScale() {
    RuntimeEnvironment.setFontScale(1);
  }

  JSONObject fighter(String key, int level) {
    JSONObject unit = fixtures.fighter(key);
    try {
      unit.put("level", level).put("stage", level > 20 ? 2 : 0);
      unit.put("stats", new JSONObject());
      unit.put("learn", new JSONArray())
          .put("promo", new JSONArray())
          .put("evolve", new JSONArray());
    } catch (JSONException error) {
      throw new AssertionError(error);
    }
    return unit;
  }

  JSONObject state(JSONObject... units) {
    return MainActivity.obj(
        "me",
        MainActivity.obj(
            "units",
            new JSONArray()
                .put(
                    MainActivity.obj(
                        "id",
                        "D1",
                        "name",
                        "갈매미",
                        "tier",
                        "LIMITED",
                        "top",
                        true,
                        "have",
                        units.length,
                        "away",
                        1))),
        "units",
        new JSONArray(Arrays.asList(units)),
        "team",
        new JSONArray());
  }

  JSONObject result() {
    return MainActivity.obj(
        "id", "D1", "name", "갈매미", "tier", "LIMITED", "tierName", "한정", "img", "D1", "n", 2);
  }

  static final class FakeApi extends NativeApi {
    final List<String> requests = new ArrayList<>();
    final JSONObject state;
    boolean deferred;
    Result pending;
    String session = "획득 검토";

    FakeApi(MainActivity h, JSONObject state) {
      super(h);
      this.state = state;
    }

    @Override
    String token() {
      return session;
    }

    @Override
    void call(String path, JSONObject body, Result done) {
      requests.add((body == null ? "GET " : "POST ") + path);
      assertEquals("native/state", path);
      assertNull("획득 이동에서는 소비 요청을 재실행하지 않음", body);
      if (deferred) pending = done;
      else done.done(state, null);
    }
  }

  FakeApi api(MainActivity h, JSONObject state) {
    h.api.close();
    FakeApi api = new FakeApi(h, state);
    h.api = api;
    h.state = state;
    h.screen = "drawResult";
    h.motionEnabled = false;
    h.guide.enabled = false;
    return api;
  }

  @Test
  public void acquiredKeysExcludeOlderRaisedAndExpeditionIndividualsWithoutMutatingThem() {
    JSONObject old = fighter("o10", 37), added1 = fighter("o20", 5), added2 = fighter("o21", 5);
    JSONObject latest = state(old, added1, added2);
    Set<String> before = NativeAcquisitionFlow.keys(state(old));
    List<JSONObject> found = NativeAcquisitionFlow.matches(latest, "D1", before);
    assertEquals(2, found.size());
    assertEquals("o20", found.get(0).optString("key"));
    assertEquals("o21", found.get(1).optString("key"));
    assertEquals(37, old.optInt("level"));
    assertEquals(2, old.optInt("stage"));
    assertEquals(
        1, latest.optJSONObject("me").optJSONArray("units").optJSONObject(0).optInt("away"));
  }

  @Test
  public void sameSpeciesMultipleNewIndividualsRequireExplicitKeySelection() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      FakeApi api = api(h, state(fighter("o10", 37), fighter("o20", 5), fighter("o21", 6)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of("o10"));
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      assertNull(chosen.get());
      assertNotNull(h.visibleDialog);
      assertEquals(2, h.visibleDialog.getListView().getAdapter().getCount());
      assertTrue(h.visibleDialog.getListView().getAdapter().getItem(1).toString().contains("#21"));
      Shadows.shadowOf(h.visibleDialog).clickOnItem(1);
      assertEquals("o21", chosen.get());
      assertEquals(Collections.singletonList("GET native/state"), api.requests);
      assertEquals(37, h.unit("o10").optInt("level"));
    }
  }

  @Test
  public void missingAcquisitionProofNeverAutoSelectsAnOlderIndividual() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state(fighter("o10", 37)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of("o10"));
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      assertNull(chosen.get());
      assertNotNull(h.visibleDialog);
      assertEquals(1, h.visibleDialog.getListView().getAdapter().getCount());
    }
  }

  @Test
  public void duplicateTapsShareOneGetAndAccountChangeDropsPendingNavigation() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      FakeApi api = api(h, state(fighter("o20", 5)));
      api.deferred = true;
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of());
      AtomicReference<String> chosen = new AtomicReference<>();
      flow.choose(result(), chosen::set);
      flow.choose(result(), chosen::set);
      assertEquals(1, api.requests.size());
      api.session = "다른 계정";
      api.pending.done(api.state, null);
      assertNull(chosen.get());
      assertFalse(h.busy);
    }
  }

  @Test
  public void fullTeamRequiresExplicitReplacementAndNeverAutoSaves() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      JSONObject state =
          state(fighter("o1", 25), fighter("o2", 20), fighter("o3", 15), fighter("o4", 5));
      try {
        state.put("team", MainActivity.arr(Arrays.asList("o1", "o2", "o3")));
      } catch (JSONException error) {
        throw new AssertionError(error);
      }
      FakeApi api = api(h, state);
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "craft", Set.of("o1", "o2", "o3"));
      flow.team("o4");
      assertNull(h.menus.teamDraft);
      assertNotNull(h.visibleDialog);
      Shadows.shadowOf(h.visibleDialog).clickOnItem(1);
      assertEquals(Arrays.asList("o1", "o4", "o3"), h.menus.teamDraft);
      assertEquals(Arrays.asList("o1", "o2", "o3"), h.menus.teamSavedIds);
      assertEquals("acquisition", h.menus.teamReturnTarget());
      assertTrue(h.menus.isTeamDirty());
      assertTrue(api.requests.isEmpty());
      assertEquals(25, h.unit("o1").optInt("level"));
    }
  }

  @Test
  public void collectionOnlyGradesOfferDetailsAndRecipes() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state());
      JSONObject common = MainActivity.obj("id", "C1", "name", "기본 갈매미", "tier", "COMMON");
      View actions = new NativeAcquisitionFlow(h, "draw", Set.of()).actions(common);
      assertNotNull(actions.findViewWithTag("acquisition-detail"));
      assertNotNull(actions.findViewWithTag("acquisition-craft"));
      assertNull(actions.findViewWithTag("acquisition-grow"));
      assertNull(actions.findViewWithTag("acquisition-team"));
    }
  }

  @Test
  public void accountResetClearsAcquisitionResultsAndReturnContext() {
    try (var controller = fixtures.start()) {
      MainActivity h = controller.get();
      api(h, state(fighter("o20", 5)));
      NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "craft", Set.of());
      h.menus.economy.acquisitionFlow = flow;
      h.menus.economy.drawFlow = flow;
      h.menus.economy.drawBefore = Set.of("o10");
      h.menus.lastDraw = MainActivity.obj("items", new JSONArray().put(result()));
      h.menus.unitReturn = "acquisition";
      h.menus.reset();
      assertNull(h.menus.economy.acquisitionFlow);
      assertNull(h.menus.economy.drawFlow);
      assertNull(h.menus.economy.drawBefore);
      assertNull(h.menus.lastDraw);
      assertEquals("story", h.menus.unitReturn);
    }
  }

  @Test
  public void acquiredCardsAndActionsCaptureAtPhoneAndTabletSizes() throws Exception {
    for (int[] size : new int[][] {{915, 412}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      try (var controller = fixtures.start()) {
        MainActivity h = controller.get();
        api(h, state(fighter("o20", 5)));
        NativeRenderReviewTest review = new NativeRenderReviewTest();
        review.viewport(h, size[0], size[1]);
        NativeAcquisitionFlow flow = new NativeAcquisitionFlow(h, "draw", Set.of());
        h.base("뽑기 결과", "drawResult");
        h.root.addView(
            new NativeReveal(
                h,
                MainActivity.obj(
                    "items",
                    new JSONArray()
                        .put(result())
                        .put(
                            MainActivity.obj(
                                "id",
                                "C1",
                                "name",
                                "기본 갈매미",
                                "tier",
                                "COMMON",
                                "tierName",
                                "흔함",
                                "img",
                                "C1"))),
                () -> {},
                true,
                flow),
            new android.widget.FrameLayout.LayoutParams(-1, -1));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        review.capture(h, "ux72-acquisition-" + size[0], size[0], size[1]);
        assertNotNull(h.root.findViewWithTag("acquisition-grow"));
        assertNotNull(h.root.findViewWithTag("acquisition-team"));
        h.root.findViewWithTag("acquisition-card-C1").performClick();
        assertNotNull(h.root.findViewWithTag("acquisition-craft"));
        assertNull(h.root.findViewWithTag("acquisition-grow"));
      }
    }
  }
}
