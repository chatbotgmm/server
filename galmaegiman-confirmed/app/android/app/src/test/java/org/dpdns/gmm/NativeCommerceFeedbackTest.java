package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** Actual Android views, packaged art, and local synthetic account only. Never contacts production. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w780dp-h319dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCommerceFeedbackTest {
  static final String[] STATES = {"recruit", "craft", "masked-craft", "mix-six", "expedition-empty", "expedition-ten", "shop"};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeShopCardBoundsTest frames = new NativeShopCardBoundsTest();
  final NativeEconomyThemeReviewTest economy = new NativeEconomyThemeReviewTest();
  final NativeRenderReviewTestEconomy controls = new NativeRenderReviewTestEconomy();
  final NativeRegistrationReviewTest identity = new NativeRegistrationReviewTest();

  @Before public void isolate() { flow.isolate(); frames.render.scheduledFramesUseThePausedLooperClock(); }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  List<String> top33() {
    List<String> ids = new ArrayList<>();
    for (String prefix : new String[] {"D", "E", "I", "T"})
      for (int n = 1; n <= (prefix.equals("I") ? 9 : 8); n++) ids.add(prefix + n);
    return ids;
  }

  NativeEconomyThemeFixture.Api prepare(MainActivity h) throws Exception {
    NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(h);
    JSONArray owned = h.me().getJSONArray("units");
    Set<String> known = new HashSet<>();
    for (int i = 0; i < owned.length(); i++) known.add(owned.getJSONObject(i).getString("id"));
    for (String id : top33()) {
      if (!known.contains(id)) owned.put(identity.species(id).put("have", 2).put("away", 0));
      try (InputStream input = h.getAssets().open("game-art/duel/sprites/" + id + "_s0_front.webp")) {
        assertTrue("Approved sprite is packaged before any fallback: " + id, input.read() >= 0);
      }
    }
    return api;
  }

  Path output() throws Exception {
    Path dir = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feedback-commerce");
    Files.createDirectories(dir); return dir;
  }

  void ready(MainActivity h, NativeShopCardBoundsTest.Profile p) throws Exception {
    frames.ready(h, p);
    RegistrationArtTest.await(() -> h.art.idle() && h.registrationArt().idle() && h.menus.pendingFetches == 0);
    frames.layout(h, p);
  }

  void capture(MainActivity h, NativeShopCardBoundsTest.Profile p, String name) throws Exception {
    Bitmap image = Bitmap.createBitmap(p.w, p.h, Bitmap.Config.ARGB_8888);
    try {
      h.root.draw(new Canvas(image));
      try (OutputStream out = Files.newOutputStream(output().resolve(name + ".png"))) {
        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
      }
    } finally { image.recycle(); }
  }

  JSONObject audit(MainActivity h, String state, NativeEconomyThemeFixture.Api api) throws Exception {
    int targets = 0, text = 0, art = 0;
    List<View> interactive = new ArrayList<>();
    for (View view : frames.descendants(h.root)) {
      if (!view.isShown()) continue;
      assertFalse("No vertical main-page workaround", view.canScrollVertically(1) || view.canScrollVertically(-1));
      if (view instanceof TextView && ((TextView) view).length() > 0) {
        economy.readable((TextView) view);
        assertEquals("Native glyph view is fully visible: " + ((TextView) view).getText(),
            frames.bounds(view, h.root), frames.visible(view, h.root)); text++;
      }
      if (view.isClickable() || view instanceof EditText) {
        Rect box = frames.bounds(view, h.root);
        assertEquals("Target is not clipped: " + view.getTag(), box, frames.visible(view, h.root));
        assertTrue("48dp action width: " + view.getTag(), box.width() >= h.dp(48));
        assertTrue("48dp action height: " + view.getTag(), box.height() >= h.dp(48));
        interactive.add(view); targets++;
      }
      if (view instanceof PartyCharacterView.Artwork) {
        PartyCharacterView.Artwork sprite = (PartyCharacterView.Artwork) view;
        assertEquals("/duel/sprites/" + sprite.id + "_s0_front.webp", sprite.path);
        assertNotNull(sprite.bitmap); assertFalse(sprite.bitmap.isRecycled());
        assertTrue(new RectF(0, 0, sprite.getWidth(), sprite.getHeight()).contains(sprite.target));
        assertTrue("Useful character portrait", sprite.target.height() >= h.dp(24));
        assertEquals(sprite.bitmap.getWidth() / (float) sprite.bitmap.getHeight(),
            sprite.target.width() / sprite.target.height(), .001f); art++;
      }
    }
    for (int i = 0; i < interactive.size(); i++) for (int j = i + 1; j < interactive.size(); j++) {
      View a = interactive.get(i), b = interactive.get(j);
      if (!economy.contains(a, b) && !economy.contains(b, a))
        assertFalse("Actions do not overlap", Rect.intersects(frames.bounds(a, h.root), frames.bounds(b, h.root)));
    }
    if (state.startsWith("expedition")) {
      int filled = 0, empty = 0;
      for (View v : frames.descendants(h.root)) {
        String tag = String.valueOf(v.getTag());
        if (tag.startsWith("expedition-member-")) filled++;
        if (tag.startsWith("expedition-empty-")) empty++;
        if (state.equals("expedition-ten") && tag.startsWith("expedition-add-")) {
          assertFalse(v.isEnabled()); assertTrue(v.getContentDescription().toString().contains("정원 10명"));
        }
      }
      assertEquals(10, filled + empty);
      assertEquals(state.equals("expedition-ten") ? 10 : 0, filled);
      assertNull(h.root.findViewWithTag("expedition-selected-pages"));
      assertTrue(art >= 8);
    }
    if (state.equals("recruit")) {
      assertFalse(flow.labels(h.root).contains("지급·보장"));
      assertNotNull(h.root.findViewWithTag("draw-pity"));
      assertNotNull(h.root.findViewWithTag("draw-claim-status"));
    }
    if (state.equals("craft")) {
      assertEquals("선택한 조합", ((TextView) h.root.findViewWithTag("craft-selected-heading")).getText().toString());
      assertTrue(flow.labels(h.root).contains("보유 "));
      assertTrue(flow.labels(h.root).contains("필요 "));
    }
    if (state.equals("masked-craft")) {
      RegistrationCardView result = h.root.findViewWithTag("craft-result-image");
      assertFalse(result.picture.visibleIdentity); assertFalse(result.picture.loadAttempted);
      assertNull(result.picture.bitmap); assertNotNull(result.findViewWithTag("craft-masked-placeholder"));
      assertEquals(api.data.craft.getJSONArray("recipes").getJSONObject(0).getString("name"), result.name.getText().toString());
    }
    if (state.equals("mix-six")) assertEquals(6, h.menus.mixDraft.size());
    if (state.equals("shop")) {
      Button buy = h.menus.economy.shopActions.get("shop:tickets");
      assertEquals("지원서 10장 구매 · 새우gang 1000개", buy.getText().toString());
    }
    assertNull(h.visibleDialog); assertEquals(0, api.writes);
    return MainActivity.obj("passed", true, "actualNativeViews", true, "touchTargets", targets,
        "nativeTextViews", text, "wholeCharacterSprites", art, "apiWrites", api.writes);
  }

  @Test public void shortLandscapeDensityAndLargeFontScreens() throws Exception {
    JSONObject results = new JSONObject(), findings = new JSONObject();
    List<String> failures = new ArrayList<>();
    for (NativeShopCardBoundsTest.Profile p : NativeShopCardBoundsTest.profiles()) {
      RuntimeEnvironment.setQualifiers(p.qualifiers()); RuntimeEnvironment.setFontScale(p.font / 100f);
      ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, p.qualifiers());
      try (var controller = flow.start()) {
        MainActivity h = controller.get(); NativeEconomyThemeFixture.Api api = prepare(h);
        h.getWindow().setLayout(p.w, p.h); frames.layout(h, p);
        for (String state : STATES) {
          String name = state + "-" + p.id;
          try {
            if (state.equals("recruit")) h.menus.economy.draw();
            else if (state.equals("craft")) { h.menus.recipeQuery = ""; h.menus.craftOnly = false; h.menus.economy.craft(false); }
            else if (state.equals("masked-craft")) {
              JSONObject recipe = api.data.craft.getJSONArray("recipes").getJSONObject(0);
              recipe.put("known", false).put("img", "q-" + recipe.getString("tier"));
              h.menus.recipeId = recipe.getString("id"); h.menus.economy.recipePage = 0;
              h.menus.economy.craft(false);
            } else if (state.equals("mix-six")) {
              h.menus.mixDraft = new ArrayList<>(Arrays.asList("C1", "C1", "C1", "C1", "C1", "C1"));
              h.menus.economy.craft(true);
            } else if (state.startsWith("expedition")) {
              h.me().remove("trip");
              List<String> ids = state.equals("expedition-ten") ? top33().subList(0, 10) : Collections.emptyList();
              h.me().put("party", MainActivity.arr(ids)); h.expeditionDraft = null;
              h.menus.economy.crewPage = 0; h.menus.economy.expedition();
            } else { h.menus.economy.shopFocus = "shop:tickets"; h.menus.economy.shop(); }
            ready(h, p); capture(h, p, name); results.put(name, audit(h, state, api)); findings.put(name, new JSONArray());
          } catch (Throwable failure) {
            failures.add(name + ": " + failure); capture(h, p, name);
            findings.put(name, new JSONArray().put(failure.toString()));
            results.put(name, MainActivity.obj("passed", false, "failure", failure.toString()));
          }
        }
      }
    }
    Files.writeString(output().resolve("geometry.json"), results.toString(2));
    Files.writeString(output().resolve("findings.json"), findings.toString(2));
    assertEquals(119, results.length()); assertTrue(String.join("\n", failures), failures.isEmpty());
  }

  @Test public void recipePagesAndFiltersClearOnlyTheSelection() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); NativeEconomyThemeFixture.Api api = prepare(h);
      String source = api.data.craft.toString(); h.menus.economy.craft(false);
      assertFalse(h.menus.recipeId.isEmpty());
      controls.button(h.root.findViewWithTag("craft-recipe-pages"), "›").performClick();
      assertEquals("", h.menus.recipeId); assertNotNull(h.root.findViewWithTag("craft-selection-empty"));
      assertEquals(0, h.menus.economy.craftActions.getChildCount());
      for (View v : frames.descendants(h.root.findViewWithTag("craft-recipe-list")))
        if (String.valueOf(v.getTag()).startsWith("craft-recipe-") && v.isClickable()) { v.performClick(); break; }
      assertFalse(h.menus.recipeId.isEmpty());
      controls.button(h.root.findViewWithTag("craft-inline-tools"), "가능만").performClick();
      assertEquals("", h.menus.recipeId); assertNotNull(h.root.findViewWithTag("craft-selection-empty"));
      assertEquals(source, api.data.craft.toString()); assertEquals(0, api.writes);
    }
  }

  @Test public void all33CandidatesCapacityAndDuplicateIndicesAreLocal() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); NativeEconomyThemeFixture.Api api = prepare(h);
      h.menus.economy.expedition(); List<String> seen = new ArrayList<>();
      while (true) {
        for (View view : frames.descendants(h.root.findViewWithTag("expedition-candidates"))) {
          String tag = String.valueOf(view.getTag());
          if (tag.startsWith("expedition-add-")) seen.add(tag.substring("expedition-add-".length()));
        }
        Button next = controls.button(h.root.findViewWithTag("expedition-candidate-pages"), "›");
        if (!next.isEnabled()) break; next.performClick();
      }
      assertEquals(top33(), seen);
      h.expeditionDraft = new ArrayList<>(Arrays.asList("D1", "D2", "D1", "D3", "D4", "D5", "D6", "D7", "D8", "E1"));
      h.menus.economy.crewPage = 0; h.menus.economy.expedition();
      View candidate = h.root.findViewWithTag("expedition-add-D3");
      assertFalse(candidate.isEnabled()); assertTrue(candidate.getContentDescription().toString().contains("정원 10명"));
      candidate.performClick(); assertEquals(10, h.expeditionDraft.size());
      h.root.findViewWithTag("expedition-member-2").performClick();
      assertEquals(Arrays.asList("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "E1"), h.expeditionDraft);
      assertTrue(h.root.findViewWithTag("expedition-add-D3").isEnabled());
      assertEquals(0, api.writes); assertNull(h.visibleDialog);
    }
  }
  static final class PendingApi extends NativeApi {
    final List<String> writes = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    Result pending;
    PendingApi(MainActivity h) { super(h); }
    @Override String token() { return "offline-expedition-pending"; }
    @Override void call(String path, JSONObject body, Result done) {
      assertNotNull("This fixture only records explicit local mutations", body);
      assertTrue(path.equals("party") || path.equals("expedition/start"));
      assertNull("Pending writes cannot be duplicated", pending);
      writes.add(path); bodies.add(body); pending = done;
    }
    void complete(String error) {
      Result done = pending; assertNotNull(done); pending = null;
      done.done(error == null ? new JSONObject() : null, error);
    }
  }

  @Test public void failedOrPendingSaveCannotDepartAndSuccessStillNeedsConfirmation() throws Exception {
    try (var controller = flow.start()) {
      MainActivity h = controller.get(); prepare(h); h.api.close();
      PendingApi api = new PendingApi(h); h.api = api;
      h.me().put("party", new JSONArray()); h.expeditionDraft = null;
      h.menus.economy.expedition();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      h.root.findViewWithTag("expedition-add-D1").performClick();
      h.root.findViewWithTag("expedition-save").performClick();
      assertEquals(List.of("party"), api.writes);
      assertEquals("[\"D1\",\"D1\"]", api.bodies.get(0).getJSONArray("ids").toString());
      h.root.findViewWithTag("expedition-save").performClick();
      assertEquals(1, api.writes.size());
      assertFalse(h.root.findViewWithTag("expedition-depart").isEnabled());
      api.complete("편성을 저장하지 못했어요.");
      assertTrue(h.menus.economy.partyChanged());
      assertEquals(0, h.me().getJSONArray("party").length());
      assertFalse(h.root.findViewWithTag("expedition-depart").isEnabled());
      assertNotNull(h.visibleDialog); h.visibleDialog.dismiss();
      h.root.findViewWithTag("expedition-save").performClick();
      api.complete(null);
      assertEquals(List.of("party", "party"), api.writes);
      assertFalse(h.menus.economy.partyChanged());
      h.root.findViewWithTag("expedition-depart").performClick();
      assertEquals("Showing confirmation never starts automatically", 2, api.writes.size());
      assertNotNull(h.visibleDialog);
      h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      assertEquals(List.of("party", "party", "expedition/start"), api.writes);
      assertEquals(0, api.bodies.get(2).length());
    }
  }
}
