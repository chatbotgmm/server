package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Real native views and bundled assets with pinned local fixtures; no token, HTTP or skipped proof. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeEconomyThemeReviewTest {
  static final int[][] SIZES = {{915,412,100}, {1006,411,100}, {1280,720,100}, {915,412,130}, {780,360,130}};
  static final String[] PAGES = {"recruit", "recruit-high", "shop", "shop-items", "craft", "mix", "mix-selected", "explore", "trip", "forge"};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  final NativeThemeReviewTest shared = new NativeThemeReviewTest();
  final NativeRenderReviewTestEconomy actions = new NativeRenderReviewTestEconomy();

  @Before public void isolate() { flow.isolate(); review.scheduledFramesUseThePausedLooperClock(); }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  void layout(MainActivity host, int[] size) {
    shared.layout(host, size[0], size[1]);
  }

  void ready(MainActivity host, int[] size) throws Exception {
    layout(host, size);
    Bitmap kickoff = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(kickoff)); } finally { kickoff.recycle(); }
    RegistrationArtTest.await(() -> host.art.idle() && host.registrationArt().idle() && host.menus.pendingFetches == 0);
    layout(host, size);
  }

  Path output() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "economy-theme");
    Files.createDirectories(output);
    return output;
  }

  void capture(MainActivity host, String name, int[] size) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(bitmap));
      try (OutputStream out = Files.newOutputStream(output().resolve(name + ".png"))) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
      }
    } finally { bitmap.recycle(); }
  }

  boolean contains(View ancestor, View child) {
    for (View current = child; current != null;) {
      if (current == ancestor) return true;
      current = current.getParent() instanceof View ? (View) current.getParent() : null;
    }
    return false;
  }

  int readable(TextView text) {
    assertNotNull("Native text layout: " + text.getText(), text.getLayout());
    assertTrue("Native text height: " + text.getText() + " view=" + text.getHeight() + " layout=" + text.getLayout().getHeight()
        + " padding=" + text.getCompoundPaddingTop() + "," + text.getCompoundPaddingBottom(), text.getHeight()
        - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() >= text.getLayout().getHeight());
    int available = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
    boolean preview = "economy-name-preview".equals(text.getTag());
    if (preview) {
      assertNotNull("Truncated identity keeps its complete accessible name", text.getContentDescription());
      assertEquals(text.getText().toString(), text.getContentDescription().toString());
      assertTrue(text.getMaxLines() <= 2);
    }
    int truncations = 0;
    for (int line = 0; line < text.getLineCount(); line++) {
      int omitted = text.getLayout().getEllipsisCount(line);
      if (!preview) assertEquals("Costs, counts, actions and prose must not truncate: " + text.getText(), 0, omitted);
      else if (omitted > 0) truncations++;
      assertTrue("Text stays inside its native line width: " + text.getText(),
          text.getLayout().getLineMax(line) <= available + 1);
      if (omitted == 0) {
        int start = text.getLayout().getLineStart(line), end = text.getLayout().getLineEnd(line);
        String content = text.getText().subSequence(start, end).toString().stripTrailing();
        Rect ink = new Rect(); text.getPaint().getTextBounds(content, 0, content.length(), ink);
        int baseline = text.getBaseline() + text.getLayout().getLineBaseline(line) - text.getLayout().getLineBaseline(0);
        assertTrue("Actual glyph ink stays below the top clip: " + content, baseline + ink.top >= text.getCompoundPaddingTop());
        assertTrue("Actual glyph ink stays above the bottom clip: " + content,
            baseline + ink.bottom <= text.getHeight() - text.getCompoundPaddingBottom());
      }
    }
    return truncations;
  }

  void inkInsideFrame(MainActivity host, String textTag, String frameTag, int top, int bottom) {
    TextView text = host.root.findViewWithTag(textTag);
    View frame = host.root.findViewWithTag(frameTag);
    assertNotNull(text); assertNotNull(frame);
    Rect own = geometry.bounds(text, host.root), outer = geometry.bounds(frame, host.root);
    for (int line = 0; line < text.getLineCount(); line++) {
      int start = text.getLayout().getLineStart(line), end = text.getLayout().getLineEnd(line);
      String content = text.getText().subSequence(start, end).toString().stripTrailing();
      Rect ink = new Rect(); text.getPaint().getTextBounds(content, 0, content.length(), ink);
      int baseline = own.top + text.getBaseline() + text.getLayout().getLineBaseline(line) - text.getLayout().getLineBaseline(0);
      assertTrue("Real ink clears the top brass frame: " + textTag,
          baseline + ink.top >= outer.top + host.dp(top));
      assertTrue("Real ink clears the bottom brass frame: " + textTag,
          baseline + ink.bottom <= outer.bottom - host.dp(bottom));
    }
  }

  JSONObject audit(MainActivity host, String page, NativeEconomyThemeFixture.Api api) {
    assertNull("Core selection stays inline", host.visibleDialog);
    int controls = 0, labels = 0, truncations = 0, overlapChecks = 0;
    List<View> actions = new ArrayList<>();
    List<TextView> textViews = new ArrayList<>();
    View foreground = page.equals("forge") ? host.root.getChildAt(host.root.getChildCount() - 1) : host.root;
    for (View view : geometry.descendants(foreground)) {
      if (!view.isShown()) continue;
      if (view instanceof ViewGroup) {
        assertFalse("No whole-page or nested vertical scrolling", view.canScrollVertically(1));
        assertFalse("No reverse vertical scrolling", view.canScrollVertically(-1));
      }
      if (view instanceof TextView && ((TextView) view).length() > 0) {
        TextView label = (TextView) view;
        truncations += readable(label);
        if (!geometry.hasScrollingAncestor(label, foreground)) {
          assertEquals("Text is fully visible inside its actual parents: " + label.getText(),
              geometry.bounds(label, host.root), geometry.visibleIntersection(label, host.root));
          textViews.add(label);
        }
        labels++;
      }
      if (view instanceof Button || view instanceof EditText || (view.isClickable() && view.getTag() instanceof String)) {
        if (geometry.hasScrollingAncestor(view, host.root)) continue;
        assertTrue("48dp action height: " + view.getTag(), view.getHeight() >= host.dp(48));
        assertTrue("48dp action width: " + view.getTag(), view.getWidth() >= host.dp(48));
        assertEquals("Action fully inside its actual parents: " + view.getTag(),
            geometry.bounds(view, host.root), geometry.visibleIntersection(view, host.root));
        actions.add(view);
        controls++;
      }
    }
    for (int i = 0; i < actions.size(); i++) for (int j = i + 1; j < actions.size(); j++) {
      View first = actions.get(i), second = actions.get(j);
      if (contains(first, second) || contains(second, first)) continue;
      assertFalse("Independent actions do not overlap: " + first.getTag() + "/" + second.getTag(),
          Rect.intersects(geometry.bounds(first, host.root), geometry.bounds(second, host.root)));
      overlapChecks++;
    }
    for (TextView label : textViews) for (View action : actions) {
      if (contains(action, label) || contains(label, action)) continue;
      assertFalse("Text does not overlap a separate action: " + label.getText() + "/" + action.getTag(),
          Rect.intersects(geometry.bounds(label, host.root), geometry.bounds(action, host.root)));
      overlapChecks++;
    }
    assertTrue(controls > 0);
    assertTrue(labels > 0);
    if (page.startsWith("mix")) {
      assertNotNull(host.root.findViewWithTag("mix-inline-materials"));
      assertNotNull(host.root.findViewWithTag("mix-workbench"));
    }
    if (page.equals("craft")) {
      View workbench = host.root.findViewWithTag("craft-workbench");
      assertNotNull(workbench);
      assertNull("Scene remains visible between the separate material cards", workbench.getBackground());
      assertNotNull(host.root.findViewWithTag("craft-recipe-list"));
    }
    if (page.startsWith("shop")) {
      assertNotNull(host.root.findViewWithTag("market-counter"));
      assertNotNull(host.root.findViewWithTag("shop-purchase-rail"));
    }
    if (page.startsWith("recruit")) {
      int seals = 0, submits = 0;
      for (View view : geometry.descendants(host.root)) {
        if ("draw-submit".equals(view.getTag())) submits++;
        if (!(view instanceof NativeEconomy.RecruitmentSeal)) continue;
        NativeEconomy.RecruitmentSeal seal = (NativeEconomy.RecruitmentSeal) view;
        assertNotNull("Original envelope atlas loaded", seal.atlas);
        assertFalse(seal.atlas.isRecycled());
        assertEquals(1774, seal.atlas.getWidth()); assertEquals(887, seal.atlas.getHeight());
        assertTrue("Envelope is a station illustration, not a tiny icon", seal.target.width() >= host.dp(120));
        assertTrue("Envelope art stays inside its native view", new RectF(0, 0, seal.getWidth(), seal.getHeight()).contains(seal.target));
        seals++;
      }
      assertEquals(3, seals); assertEquals("One actual spend action", 1, submits);
      inkInsideFrame(host, "draw-credits-label", "draw-claim-rail", 12, 12);
      inkInsideFrame(host, "draw-claim-status", "draw-claim-rail", 12, 12);
      inkInsideFrame(host, "draw-pity", "draw-purchase-rail", 12, 10);
      inkInsideFrame(host, "draw-total", "draw-purchase-rail", 12, 10);
      assertNotNull(host.root.findViewWithTag("draw-pity"));
      assertTrue(host.root.findViewWithTag(page.equals("recruit-high") ? "draw-card-HIGH" : "draw-card-LOW").isSelected());
    }
    String sceneAsset = page.startsWith("recruit") ? AppSkin.background("reception")
        : page.startsWith("shop") ? AppSkin.background("market") : "backgrounds/bg-navigation-workshop-v1.png";
    Bitmap scene = ThemeArt.of(host).image(sceneAsset);
    assertNotNull("Actual bundled scene", scene);
    assertTrue("Full-resolution background", scene.getWidth() >= 1800 && scene.getHeight() >= 800);
    return MainActivity.obj("actualNativeViews", true, "controls", controls, "labels", labels, "namePreviewTruncations", truncations,
        "independentOverlapChecks", overlapChecks, "apiWrites", api.writes, "apiReads", api.calls.size(),
        "sceneAsset", sceneAsset, "sceneWidth", scene.getWidth(), "sceneHeight", scene.getHeight(),
        "rootKind", page.equals("forge") ? "overlay" : "page", "sourceCommit", api.data.source.optString("sourceCommit"));
  }

  @Test public void renderImageLedEconomyPagesAcrossPhoneInsetTabletAndLargeFont() throws Exception {
    JSONObject results = new JSONObject(), findings = new JSONObject();
    List<String> failures = new ArrayList<>();
    for (int[] size : SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      try (var controller = flow.start()) {
        MainActivity host = controller.get();
        NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(host);
        host.me().remove("trip");
        for (String page : PAGES) {
          String name = page + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
          JSONArray problems = new JSONArray(); findings.put(name, problems);
          try {
            if (page.startsWith("recruit")) {
              host.menus.economy.draw();
              if (page.equals("recruit-high")) {
                host.root.findViewWithTag("draw-card-HIGH").performClick();
                actions.button(host.root.findViewWithTag("draw-quantity"), "＋").performClick();
              }
            } else if (page.startsWith("shop")) {
              host.menus.economy.shopFocus = page.equals("shop-items") ? "items/buy:big" : "shop:tickets";
              host.menus.economy.shop();
            } else if (page.equals("craft")) host.menus.economy.craft(false);
            else if (page.startsWith("mix")) {
              host.menus.mixDraft.clear();
              if (page.equals("mix-selected")) {
                JSONArray pool = api.responses.get("craft").getJSONArray("pool");
                for (int i = 0; i < pool.length() && host.menus.mixDraft.size() < 6; i++) {
                  JSONObject unit = pool.getJSONObject(i);
                  for (int n = 0; n < unit.optInt("usable") && host.menus.mixDraft.size() < 6; n++)
                    host.menus.mixDraft.add(unit.getString("id"));
                }
              }
              host.menus.economy.craft(true);
            } else if (page.equals("explore")) {
              host.me().remove("trip");
              host.expeditionDraft = null;
              host.menus.economy.expedition();
            } else if (page.equals("trip")) {
              JSONArray ids = host.list(host.me(), "party");
              if (ids.length() == 0) {
                JSONArray units = host.list(host.me(), "units");
                for (int i = 0; i < units.length() && ids.length() < 3; i++)
                  if (units.getJSONObject(i).optBoolean("top")) ids.put(units.getJSONObject(i).getString("id"));
              }
              // Presentation-only recorded-trip state. No departure or claim request is made.
              long now = System.currentTimeMillis();
              host.me().put("trip", MainActivity.obj("ids", ids,
                  "startedAt", java.time.Instant.ofEpochMilli(now - 900000).toString(),
                  "endsAt", java.time.Instant.ofEpochMilli(now + 2700000).toString()));
              host.menus.economy.expedition();
            } else {
              JSONObject recipe = api.responses.get("craft").getJSONArray("recipes").getJSONObject(0);
              // Existing server recipe art is used only to render the result UI; inventory is untouched.
              host.menus.economy.forgeResult(recipe.getJSONArray("materials"), recipe, false,
                  "조합 결과 표시 검토", new NativeAcquisitionFlow(host, "craft", Collections.emptySet()), false);
            }
            ready(host, size);
            capture(host, name, size);
            assertEquals("Read-only local review account", 0, api.writes);
            results.put(name, audit(host, page, api));
          } catch (Throwable failure) {
            failures.add(name + ": " + failure); problems.put(failure.toString());
            capture(host, name, size);
            results.put(name, MainActivity.obj("passed", false, "failure", failure.toString()));
          }
        }
      }
    }
    Files.write(output().resolve("geometry.json"), results.toString(2).getBytes(StandardCharsets.UTF_8));
    Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
    assertTrue(String.join("\n", failures), failures.isEmpty());
    assertEquals(PAGES.length * SIZES.length, results.length());
  }

  @Test public void inlineRecipeAndMaterialPagesKeepEveryServerChoiceWithoutMutations() throws Exception {
    try (var controller = flow.start()) {
      MainActivity host = controller.get();
      NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(host);
      host.menus.recipeQuery = "";
      host.menus.craftOnly = false;
      host.menus.economy.craft(false);
      RegistrationArtTest.await(() -> host.menus.pendingFetches == 0);
      JSONArray recipes = api.responses.get("craft").getJSONArray("recipes");
      Set<String> expected = new HashSet<>(), seen = new HashSet<>();
      for (int i = 0; i < recipes.length(); i++) expected.add(recipes.getJSONObject(i).getString("id"));
      while (true) {
        View list = host.root.findViewWithTag("craft-recipe-list");
        for (View view : geometry.descendants(list)) {
          if (!(view.getTag() instanceof String) || !view.getTag().toString().startsWith("craft-recipe-")) continue;
          String id = view.getTag().toString().substring("craft-recipe-".length());
          seen.add(id);
        }
        Button next = actions.button(host.root.findViewWithTag("craft-recipe-pages"), "›");
        if (!next.isEnabled()) break;
        next.performClick();
      }
      seen.remove("list");
      assertEquals("Only server-visible recipes, including every returned choice", expected, seen);
      assertNull(host.visibleDialog);
      host.menus.economy.craft(true);
      RegistrationArtTest.await(() -> host.menus.pendingFetches == 0);
      JSONArray pool = api.responses.get("craft").getJSONArray("pool");
      expected.clear(); seen.clear();
      for (int i = 0; i < pool.length(); i++) if (pool.getJSONObject(i).optInt("usable") > 0)
        expected.add(pool.getJSONObject(i).getString("id"));
      while (true) {
        for (View view : geometry.descendants(host.root.findViewWithTag("mix-inline-materials"))) {
          if (view.getTag() instanceof String && view.getTag().toString().startsWith("mix-picker-material-"))
            seen.add(view.getTag().toString().substring("mix-picker-material-".length()));
        }
        Button next = actions.button(host.root.findViewWithTag("mix-material-pages"), "›");
        if (!next.isEnabled()) break;
        next.performClick();
      }
      assertEquals(expected, seen);
      assertNull(host.visibleDialog);
    }
  }
}
