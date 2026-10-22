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
    inkInsideFrame(host, text, frame, top, bottom);
  }

  void inkInsideFrame(MainActivity host, TextView text, View frame, int top, int bottom) {
    Rect own = geometry.bounds(text, host.root), outer = geometry.bounds(frame, host.root);
    for (int line = 0; line < text.getLineCount(); line++) {
      int start = text.getLayout().getLineStart(line), end = text.getLayout().getLineEnd(line);
      String content = text.getText().subSequence(start, end).toString().stripTrailing();
      Rect ink = new Rect(); text.getPaint().getTextBounds(content, 0, content.length(), ink);
      int baseline = own.top + text.getBaseline() + text.getLayout().getLineBaseline(line) - text.getLayout().getLineBaseline(0);
      assertTrue("Real ink clears the top brass frame: " + text.getText(),
          baseline + ink.top >= outer.top + host.dp(top));
      assertTrue("Real ink clears the bottom brass frame: " + text.getText(),
          baseline + ink.bottom <= outer.bottom - host.dp(bottom));
    }
  }

  JSONArray expeditionCards(MainActivity host, String page) {
    JSONArray dimensions = new JSONArray();
    if (!page.equals("explore") && !page.equals("trip")) return dimensions;
    NativeEconomy economy = host.menus.economy;
    assertNull("Every selected slot is visible; no selected-member paging", host.root.findViewWithTag("expedition-selected-pages"));
    if (page.equals("explore")) {
      View pager = host.root.findViewWithTag("expedition-candidate-pages");
      assertNotNull(pager);
      assertTrue("Two 48dp candidate page actions fit", pager.getHeight() >= host.dp(96));
      assertEquals(geometry.bounds(pager, host.root), geometry.visibleIntersection(pager, host.root));
      View frame = host.root.findViewWithTag("expedition-summary");
      View copy = host.root.findViewWithTag("expedition-summary-copy");
      int labels = 0;
      for (View child : geometry.descendants(copy)) if (child instanceof TextView) {
        inkInsideFrame(host, (TextView) child, frame, 6, 6); labels++;
      }
      assertEquals("Count and exact live estimate remain readable", 2, labels);
    }
    int selected = 0, candidates = 0, empty = 0;
    List<String> ids = new ArrayList<>();
    if (page.equals("trip")) {
      JSONArray party = host.list(host.me().optJSONObject("trip"), "ids");
      for (int i = 0; i < party.length(); i++) ids.add(party.optString(i));
    } else ids.addAll(host.expeditionDraft);
    for (View view : geometry.descendants(host.root)) {
      String tag = String.valueOf(view.getTag());
      if (view.isShown() && tag.startsWith("expedition-empty-")) empty++;
      boolean member = tag.startsWith("expedition-member-"), candidate = tag.startsWith("expedition-add-");
      if (!view.isShown() || (!member && !candidate)) continue;
      int index = member ? Integer.parseInt(tag.substring("expedition-member-".length())) : -1;
      String id = member ? ids.get(index) : tag.substring("expedition-add-".length());
      if (member) selected++; else candidates++;
      JSONObject unit = economy.find(id);
      LinearLayout tile = (LinearLayout) view;
      assertEquals("Whole character plus canonical live name", 2, tile.getChildCount());
      assertTrue(tile.getChildAt(0) instanceof PartyCharacterView.Artwork);
      PartyCharacterView.Artwork art = (PartyCharacterView.Artwork) tile.getChildAt(0);
      assertEquals(id, art.id);
      assertEquals("/duel/sprites/" + id + "_s0_front.webp", art.path);
      assertNotNull("Approved canonical character sprite loaded: " + id, art.bitmap);
      assertFalse(art.bitmap.isRecycled());
      assertTrue(view.getContentDescription().toString().contains(unit.optString("name")));
      assertTrue(new RectF(0, 0, art.getWidth(), art.getHeight()).contains(art.target));
      assertEquals(art.getWidth() / 2f, art.target.centerX(), .01f);
      assertEquals(art.getHeight() / 2f, art.target.centerY(), .01f);
      assertEquals(art.bitmap.getWidth() / (float) art.bitmap.getHeight(), art.target.width() / art.target.height(), .001f);
      assertTrue("Character remains useful at short height", art.target.height() >= host.dp(24));
      assertEquals(geometry.bounds(art, host.root), geometry.visibleIntersection(art, host.root));
      assertTrue("48dp tile width", tile.getWidth() >= host.dp(48));
      assertTrue("48dp tile height", tile.getHeight() >= host.dp(48));
      TextView status = (TextView) tile.getChildAt(1);
      assertTrue(geometry.bounds(art, host.root).bottom <= geometry.bounds(status, host.root).top);
      assertEquals(geometry.bounds(status, host.root), geometry.visibleIntersection(status, host.root));
      int left = unit.optInt("have") - unit.optInt("away") - (candidate ? Collections.frequency(host.expeditionDraft, id) : 0);
      assertEquals(unit.optString("name"), status.getText().toString());
      assertEquals(unit.optString("name"), status.getContentDescription().toString());
      assertEquals("economy-name-preview", status.getTag());
      assertTrue(tile.getContentDescription().toString().contains(candidate ? "남은 " + left : (index + 1) + "번"));
      if (candidate && host.expeditionDraft.size() >= economy.partyMax()) {
        assertFalse(tile.isEnabled());
        assertTrue(tile.getContentDescription().toString().contains("정원 " + economy.partyMax()));
      }
      readable(status);
      dimensions.put(MainActivity.obj("tag", tag, "id", id, "mode", "CHARACTER", "path", art.path,
          "artWidth", art.target.width(), "artHeight", art.target.height(), "status", status.getText().toString()));
    }
    assertEquals("Every selected duplicate has its own visible slot", ids.size(), selected);
    assertEquals("All ten slot positions stay visible", economy.partyMax(), selected + empty);
    assertTrue("Candidate page is bounded at eight", candidates <= 8);
    if (page.equals("explore")) assertTrue(candidates > 0);
    return dimensions;
  }

  JSONArray economyFullCards(MainActivity host, String page, NativeEconomyThemeFixture.Api api) {
    if (page.startsWith("mix")) return mixCards(host, page, api);
    JSONArray measured = new JSONArray();
    if (!page.equals("craft") && !page.startsWith("mix") && !page.equals("forge")) return measured;
    View foreground = page.equals("forge") ? host.root.getChildAt(host.root.getChildCount() - 1) : host.root;
    List<String> selectedMix = new ArrayList<>();
    for (View view : geometry.descendants(foreground)) {
      if (!(view instanceof RegistrationCardView) || !view.isShown()) continue;
      RegistrationCardView card = (RegistrationCardView) view;
      RegistrationCardView.Artwork art = card.picture;
      String tag = String.valueOf(card.getTag());
      View parent = (View) card.getParent();
      String parentTag = String.valueOf(parent.getTag()), id;
      if (tag.equals("forge-result-card")) id = ((NativeForge) foreground).result.optString("id");
      else if (tag.equals("craft-result-image")) id = host.menus.recipeId;
      else if (tag.startsWith("craft-material-image-")) id = tag.substring("craft-material-image-".length());
      else if (tag.startsWith("craft-recipe-")) id = tag.substring("craft-recipe-".length());
      else if (parentTag.startsWith("mix-picker-material-")) id = parentTag.substring("mix-picker-material-".length());
      else if (parentTag.startsWith("mix-material-")) {
        id = parentTag.substring("mix-material-".length()); selectedMix.add(id);
      } else throw new AssertionError("Unclassified FULL economy card: " + tag + "/" + parentTag);
      JSONObject canonical = api.data.units.get(id);
      assertNotNull("Identity comes from the canonical visible server fixture: " + id, canonical);
      assertEquals(id, art.id);
      assertEquals(RegistrationCardView.FULL, art.mode);
      assertNotNull("Canonical FULL bitmap loaded before proof: " + id, art.bitmap);
      assertFalse(art.bitmap.isRecycled());
      assertEquals(View.GONE, card.name.getVisibility());
      assertEquals(View.GONE, card.grade.getVisibility());
      assertTrue(card.getContentDescription().toString().contains(canonical.optString("name")));
      assertTrue(new RectF(0, 0, art.getWidth(), art.getHeight()).contains(art.target));
      assertEquals(art.getWidth() / 2f, art.target.centerX(), .01f);
      assertEquals(art.getHeight() / 2f, art.target.centerY(), .01f);
      assertEquals(art.bitmap.getWidth() / (float) art.bitmap.getHeight(),
          art.target.width() / art.target.height(), .001f);
      int minimum = page.equals("forge") ? (host.contentHeightDp() < 390 ? 180 : 220)
          : (host.contentHeightDp() < 390 ? 100 : 120);
      assertTrue("Readable FULL economy art: " + tag + "/" + parentTag + " target=" + art.target + " minimum=" + minimum,
          art.target.height() >= host.dp(minimum));
      assertEquals(geometry.bounds(art, host.root), geometry.visibleIntersection(art, host.root));
      // Inspect only this tile, not the neighboring outcome/quantity controls.
      View identitySurface = page.equals("forge") ? card : parent;
      for (View child : geometry.descendants(identitySurface)) if (child.isShown() && child instanceof TextView) {
        String visible = ((TextView) child).getText().toString();
        assertNotEquals("Printed name is not duplicated outside the original art", canonical.optString("name"), visible);
        if (!canonical.optString("tierName").isEmpty())
          assertNotEquals("Printed tier is not duplicated outside the original art", canonical.optString("tierName"), visible);
      }
      measured.put(MainActivity.obj("tag", tag, "parentTag", parentTag, "id", id, "mode", "FULL",
          "artWidth", art.target.width(), "artHeight", art.target.height(), "minimumArtHeightDp", minimum));
    }
    assertTrue("This state contains actual FULL cards", measured.length() > 0);
    if (page.startsWith("mix")) assertEquals("All six slots preserve ordered duplicate material identity",
        host.menus.mixDraft, selectedMix);
    if (page.equals("forge")) {
      assertEquals(1, measured.length());
      assertNull("Legacy duplicate result title removed", foreground.findViewWithTag("forge-result-name"));
      RegistrationCardView card = foreground.findViewWithTag("forge-result-card");
      assertNull("No second paper frame around the original registration art", card.getBackground());
    }
    assertTrue(host.registrationArt().cachedBytes() <= 16 * 1024 * 1024);
    return measured;
  }

  Rect trayAlphaBounds(Bitmap bitmap) {
    int width = bitmap.getWidth(), height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    Rect bounds = new Rect(width, height, 0, 0);
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
      if ((pixels[y * width + x] >>> 24) <= 8) continue;
      bounds.left = Math.min(bounds.left, x); bounds.top = Math.min(bounds.top, y);
      bounds.right = Math.max(bounds.right, x + 1); bounds.bottom = Math.max(bounds.bottom, y + 1);
    }
    assertFalse("Tray has actual visible ink", bounds.isEmpty());
    return bounds;
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
      assertNotNull(host.root.findViewWithTag("mix-inventory"));
      assertNotNull(host.root.findViewWithTag("mix-workbench"));
      assertNotNull(host.root.findViewWithTag("mix-results"));
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
      int chosenTitles = 0;
      for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
        View title = host.root.findViewWithTag("draw-station-title-" + kind);
        View station = host.root.findViewWithTag("draw-card-" + kind);
        assertEquals("Title drawing state follows the actual tier choice", station.isSelected(), title.isSelected());
        Bitmap titlePixels = Bitmap.createBitmap(title.getWidth(), title.getHeight(), Bitmap.Config.ARGB_8888);
        try {
          title.getBackground().setBounds(0, 0, title.getWidth(), title.getHeight());
          title.getBackground().draw(new Canvas(titlePixels));
          int fill = titlePixels.getPixel(title.getWidth() / 2, title.getHeight() / 2);
          int edge = titlePixels.getPixel(1, title.getHeight() / 2);
          if (title.isSelected()) {
            chosenTitles++;
            assertEquals("Chosen tier has the distinct pale-gold reading surface", 0xFFF8E4AA, fill);
            assertEquals("Only chosen tier has the navy selection outline", 0xFF294F60, edge);
          } else {
            assertNotEquals("Unselected tier retains parchment artwork", 0xFFF8E4AA, fill);
          }
        } finally { titlePixels.recycle(); }
      }
      assertEquals("Exactly one visually selected title", 1, chosenTitles);
      int trays = 0, submits = 0;
      Set<String> paths = new HashSet<>();
      for (View view : geometry.descendants(host.root)) {
        if ("draw-submit".equals(view.getTag())) submits++;
        if (!(view instanceof NativeEconomy.RecruitmentTray)) continue;
        NativeEconomy.RecruitmentTray tray = (NativeEconomy.RecruitmentTray) view;
        assertNotNull("Distinct bundled application tray loaded", tray.bitmap);
        assertFalse(tray.bitmap.isRecycled());
        assertEquals(768, tray.bitmap.getWidth()); assertEquals(512, tray.bitmap.getHeight());
        assertTrue("Tray is a station illustration, not a tiny icon", tray.target.width() >= host.dp(120));
        assertTrue("Tray stays inside its native view", new RectF(0, 0, tray.getWidth(), tray.getHeight()).contains(tray.target));
        Rect ink = trayAlphaBounds(tray.bitmap);
        assertEquals("Transparent canvas margin is excluded from drawing", ink, tray.source);
        float inkBottom = tray.target.top + (ink.bottom - tray.source.top) * tray.target.height() / tray.source.height();
        assertEquals("Visible wood, not transparent padding, rests on the counter", tray.getHeight(), inkBottom, .01f);
        assertTrue("Selection stays attached to visible wood", tray.target.contains(tray.selectionBounds));
        assertEquals(tray.target.bottom, tray.selectionBounds.bottom, .01f);
        paths.add(tray.path); trays++;
      }
      assertEquals(3, trays); assertEquals("Each tier has its own artwork", 3, paths.size());
      assertEquals("One actual spend action", 1, submits);
      assertNull("Obsolete top claim banner is removed", host.root.findViewWithTag("draw-claim-rail"));
      assertNull("No decorative panel behind the compact bottom actions", host.root.findViewWithTag("draw-purchase-rail").getBackground());
      assertTrue("Claim lives in the same bottom row as quantity and submit",
          contains(host.root.findViewWithTag("draw-purchase-rail"), host.root.findViewWithTag("draw-claim")));
      assertNotNull(host.root.findViewWithTag("draw-pity"));
      assertTrue(host.root.findViewWithTag(page.equals("recruit-high") ? "draw-card-HIGH" : page.equals("recruit-mid") ? "draw-card-MID" : "draw-card-LOW").isSelected());
    }
    String sceneAsset = page.startsWith("recruit") ? AppSkin.background("reception")
        : page.startsWith("shop") ? AppSkin.background("market") : "backgrounds/bg-navigation-workshop-v1.png";
    Bitmap scene = ThemeArt.of(host).image(sceneAsset);
    assertNotNull("Actual bundled scene", scene);
    assertTrue("Full-resolution background", scene.getWidth() >= 1800 && scene.getHeight() >= 800);
    JSONArray fullCards = expeditionCards(host, page);
    JSONArray workbenchCards = economyFullCards(host, page, api);
    return MainActivity.obj("actualNativeViews", true, "controls", controls, "labels", labels, "namePreviewTruncations", truncations,
        "independentOverlapChecks", overlapChecks, "apiWrites", api.writes, "apiReads", api.calls.size(),
        "expeditionFullCards", fullCards, "workbenchFullCards", workbenchCards,
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
    for (int[] size : SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      try (var controller = flow.start()) {
        MainActivity host = controller.get();
        NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(host);
        host.menus.recipeQuery = ""; host.menus.craftOnly = false;
        host.menus.economy.craft(false);
        RegistrationArtTest.await(() -> host.menus.pendingFetches == 0);
        JSONArray recipes = api.responses.get("craft").getJSONArray("recipes");
        List<String> expected = new ArrayList<>(), seen = new ArrayList<>();
        for (int i = 0; i < recipes.length(); i++) expected.add(recipes.getJSONObject(i).getString("id"));
        int perPage = 1, pageIndex = 0;
        while (true) {
          ready(host, size); economyFullCards(host, "craft", api);
          List<String> pageIds = new ArrayList<>();
          for (View view : geometry.descendants(host.root.findViewWithTag("craft-recipe-list"))) {
            String tag = String.valueOf(view.getTag());
            if (tag.startsWith("craft-recipe-") && !tag.equals("craft-recipe-list"))
              pageIds.add(tag.substring("craft-recipe-".length()));
          }
          assertEquals(expected.subList(pageIndex * perPage, Math.min(expected.size(), (pageIndex + 1) * perPage)), pageIds);
          seen.addAll(pageIds);
          ViewGroup carousel = host.root.findViewWithTag("craft-result-carousel");
          assertNotNull(carousel); assertEquals(1, carousel.getChildCount());
          assertTrue(carousel.getWidth() >= host.dp(48) && carousel.getHeight() >= host.dp(48));
          assertEquals(geometry.bounds(carousel, host.root), geometry.visibleIntersection(carousel, host.root));
          assertNull(host.root.findViewWithTag("craft-recipe-pages-next"));
          assertNull(host.root.findViewWithTag("craft-recipe-pages-previous"));
          assertEquals(expected.get(pageIndex), host.menus.recipeId);
          assertNotNull(host.root.findViewWithTag("craft-submit"));
          if (!host.menus.economy.compact()) {
            TextView availability = host.root.findViewWithTag("craft-availability");
            assertNotNull(availability);
            assertEquals(JournalTheme.NAVY, availability.getCurrentTextColor());
            assertTrue(availability.getBackground() instanceof android.graphics.drawable.ColorDrawable);
            assertEquals("Opaque cream separates recipe conditions from workshop floor", JournalTheme.PAPER,
                ((android.graphics.drawable.ColorDrawable) availability.getBackground()).getColor());
            readable(availability);
            assertEquals(geometry.bounds(availability, host.root), geometry.visibleIntersection(availability, host.root));
          }
          if (pageIndex + 1 == expected.size()) {
            swipeForward(carousel);
            assertEquals("Swiping past the final recipe does not wrap or clear selection", expected.get(pageIndex), host.menus.recipeId);
            break;
          }
          swipeForward(carousel); pageIndex++;
        }
        assertEquals("Every server-visible high-tier recipe, in original order, one card per swipe", expected, seen);
        assertNull(host.visibleDialog);
        host.menus.economy.craft(true);
        RegistrationArtTest.await(() -> host.menus.pendingFetches == 0);
        JSONArray pool = api.responses.get("craft").getJSONArray("pool");
        expected.clear(); seen.clear(); pageIndex = 0;
        // 재료 탭은 원본 등록증 한 장씩 서버 순서대로 탐색한다.
        int mixPerPage = 1;
        for (int i = 0; i < pool.length(); i++) if (pool.getJSONObject(i).optInt("usable") > 0)
          expected.add(pool.getJSONObject(i).getString("id"));
        while (true) {
          ready(host, size); economyFullCards(host, "mix", api);
          List<String> pageIds = new ArrayList<>();
          for (View view : geometry.descendants(host.root.findViewWithTag("mix-inventory"))) {
            String tag = String.valueOf(view.getTag());
            if (tag.startsWith("mix-browse-card-")) pageIds.add(tag.substring("mix-browse-card-".length()));
          }
          assertEquals(expected.subList(pageIndex * mixPerPage, Math.min(expected.size(), (pageIndex + 1) * mixPerPage)), pageIds);
          seen.addAll(pageIds);
          View carousel = host.root.findViewWithTag("mix-inventory");
          assertNotNull(carousel);
          assertNull("Navigation is swipe-only, without obsolete arrows", host.root.findViewWithTag("mix-material-pages"));
          assertTrue("Browsing alone never adds a material", host.menus.mixDraft.isEmpty());
          if (pageIndex + 1 == expected.size()) {
            swipeForward(carousel);
            assertEquals("Swiping past the final material stays on the same card", pageIndex, host.menus.economy.mixPage);
            assertTrue(host.menus.mixDraft.isEmpty());
            break;
          }
          swipeForward(carousel); pageIndex++;
        }
        assertEquals("Every usable material keeps its server order and page size", expected, seen);
        assertNull(host.visibleDialog);
        mixSlotsKeepDuplicateIndicesAndOwnership(host, api, size);
        maskedCardsKeepServerIdentityWithoutRevealingArt(host, api, size);
        if (size == SIZES[0]) expeditionPagesKeepIdentityAndDuplicateIndices(host, api);
        assertEquals("All display, paging and draft actions remain read-only", 0, api.writes);
      }
    }
  }

  void swipeForward(View carousel) {
    assertTrue("Swipe uses an actually measured visible viewport", carousel.getWidth() > 0 && carousel.getHeight() > 0);
    long time = android.os.SystemClock.uptimeMillis();
    float from = carousel.getWidth() * .85f, to = carousel.getWidth() * .15f, y = carousel.getHeight() / 2f;
    for (int action : new int[] {android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP}) {
      android.view.MotionEvent event = android.view.MotionEvent.obtain(time, time + (action == android.view.MotionEvent.ACTION_DOWN ? 0 : 100), action,
          action == android.view.MotionEvent.ACTION_DOWN ? from : to, y, 0);
      try { assertTrue(carousel.dispatchTouchEvent(event)); } finally { event.recycle(); }
    }
  }

  void mixSlotsKeepDuplicateIndicesAndOwnership(MainActivity host, NativeEconomyThemeFixture.Api api, int[] size) throws Exception {
    // Behavior-only selection state. Canonical names/art and actual server usable counts are unchanged.
    NativeEconomy economy = host.menus.economy;
    JSONArray pool = api.data.craft.getJSONArray("pool");
    JSONObject first = pool.getJSONObject(0), second = pool.getJSONObject(1);
    String a = first.getString("id"), b = second.getString("id");
    economy.mixPage = 0;
    host.menus.mixDraft = new ArrayList<>(Arrays.asList(a, b, a));
    economy.craft(true); ready(host, size);
    List<View> selected = new ArrayList<>();
    for (View view : geometry.descendants(host.root.findViewWithTag("mix-workbench")))
      if (String.valueOf(view.getTag()).startsWith("mix-material-")) selected.add(view);
    assertEquals(3, selected.size());
    // 최종 조합 시안(495ca8e): 처음부터 서버 한도만큼 칸을 모두 보인다(0/6인데 4칸만 보이던 혼동 제거).
    int limit = api.data.craft.optInt("maxMaterials", 6);
    assertNotNull(host.root.findViewWithTag("mix-empty-slot-3"));
    assertNotNull(host.root.findViewWithTag("mix-empty-slot-" + (limit - 1)));
    assertNull(host.root.findViewWithTag("mix-empty-slot-" + limit));
    host.busy = true; selected.get(2).performClick();
    assertEquals(Arrays.asList(a, b, a), host.menus.mixDraft);
    host.root.findViewWithTag("mix-inventory").performClick();
    assertEquals(Arrays.asList(a, b, a), host.menus.mixDraft);
    host.busy = false; selected.get(2).performClick();
    assertEquals("Remove the third slot, not the first matching identity", Arrays.asList(a, b), host.menus.mixDraft);
    ready(host, size);
    int usable = first.getInt("usable");
    host.menus.mixDraft.clear();
    for (int i = 0; i < Math.min(6, usable); i++) host.menus.mixDraft.add(a);
    economy.craft(true); ready(host, size);
    List<String> before = new ArrayList<>(host.menus.mixDraft);
    View exhausted = host.root.findViewWithTag("mix-inventory");
    assertTrue("Carousel remains navigable when additions are exhausted", exhausted.isEnabled());
    exhausted.performClick(); assertEquals(before, host.menus.mixDraft);
    assertEquals("Canonical server usable inventory is never changed optimistically", usable, first.getInt("usable"));
    int filled = 0;
    for (View view : geometry.descendants(host.root.findViewWithTag("mix-workbench")))
      if (String.valueOf(view.getTag()).startsWith("mix-material-")) filled++;
    assertEquals("Six selected slots at the six-material limit", 6, filled);
    assertNull("No empty slot beyond the limit", host.root.findViewWithTag("mix-empty-slot-6"));
    economyFullCards(host, "mix-selected", api);
    assertEquals(0, api.writes); assertNull(host.visibleDialog);
    // Separate behavior-only server response: inventory exhausts before the six-slot cap.
    try {
      first.put("usable", 2);
      host.menus.mixDraft.clear(); host.menus.mixDraft.add(a); host.menus.mixDraft.add(a);
      economy.craft(true); ready(host, size);
      View noneLeft = host.root.findViewWithTag("mix-inventory");
      assertTrue(noneLeft.isEnabled());
      assertTrue(noneLeft.getContentDescription().toString().contains("사용 가능 0"));
      noneLeft.performClick();
      assertEquals(Arrays.asList(a, a), host.menus.mixDraft);
      assertEquals(2, first.getInt("usable"));
    } finally { first.put("usable", usable); }
    host.menus.mixDraft.clear();
  }

  /** 재료 탭: 보유 한 장은 원본 등록증, 선택 칸은 썸네일. 미공개 결과의 그림·정체는 숨긴다. */
  JSONArray mixCards(MainActivity host, String page, NativeEconomyThemeFixture.Api api) {
    JSONArray measured = new JSONArray();
    List<String> selectedMix = new ArrayList<>();
    ViewGroup carousel = host.root.findViewWithTag("mix-inventory");
    assertEquals("한 번에 보유 카드 한 장만 표시", 1, carousel.getChildCount());
    assertTrue(carousel.getChildAt(0) instanceof RegistrationCardView);
    RegistrationCardView browsing = (RegistrationCardView) carousel.getChildAt(0);
    RegistrationCardView.Artwork full = browsing.picture;
    JSONObject canonicalBrowse = api.data.units.get(full.id);
    assertNotNull("보유 카드의 정체는 서버 목록 그대로", canonicalBrowse);
    assertEquals("mix-browse-card-" + full.id, browsing.getTag());
    assertEquals(RegistrationCardView.FULL, full.mode);
    assertNotNull("원본 등록증을 읽은 뒤 검증", full.bitmap);
    assertFalse(full.bitmap.isRecycled());
    assertEquals(View.GONE, browsing.name.getVisibility());
    assertEquals(View.GONE, browsing.grade.getVisibility());
    assertEquals(full.bitmap.getWidth() / (float) full.bitmap.getHeight(),
        full.target.width() / full.target.height(), .001f);
    assertTrue(new RectF(0, 0, full.getWidth(), full.getHeight()).contains(full.target));
    assertEquals(geometry.bounds(full, host.root), geometry.visibleIntersection(full, host.root));
    assertTrue("보유 등록증은 작은 썸네일보다 크게 표시", full.target.height() >= host.dp(100));
    View add = host.root.findViewWithTag("mix-inventory");
    assertNotNull(add);
    assertTrue(add.getWidth() >= host.dp(48) && add.getHeight() >= host.dp(48));
    measured.put(MainActivity.obj("tag", browsing.getTag(), "id", full.id, "mode", "FULL",
        "artWidth", full.target.width(), "artHeight", full.target.height()));
    for (View view : geometry.descendants(host.root.findViewWithTag("mix-workbench"))) {
      String tag = String.valueOf(view.getTag());
      if (!tag.startsWith("mix-material-")) continue;
      String id = tag.substring("mix-material-".length());
      selectedMix.add(id);
      assertNotNull("Identity comes from the canonical visible server fixture: " + id, api.data.units.get(id));
      assertTrue("48dp thumbnail target: " + tag, view.getWidth() >= host.dp(48) && view.getHeight() >= host.dp(48));
      assertEquals(geometry.bounds(view, host.root), geometry.visibleIntersection(view, host.root));
      // 최종 조합 시안(495ca8e)은 선택 재료를 정사각 썸네일 대신 원본 등록증 축소본으로 보인다.
      // 원본 그림(같은 ID)이 실제로 불러와졌는지는 그대로 검사한다.
      RegistrationCardView art = null;
      for (View child : geometry.descendants(view)) if (child instanceof RegistrationCardView) art = (RegistrationCardView) child;
      assertNotNull("Original registration art: " + tag, art);
      assertEquals("Same canonical identity: " + tag, id, art.picture.id);
      assertNotNull("Original bitmap loaded before proof: " + tag, art.picture.bitmap);
      measured.put(MainActivity.obj("tag", tag, "id", id, "mode", "THUMB", "width", view.getWidth(), "height", view.getHeight()));
    }
    for (View view : geometry.descendants(host.root.findViewWithTag("mix-results"))) {
      if (!(view instanceof RegistrationCardView) || !view.isShown()) continue;
      RegistrationCardView card = (RegistrationCardView) view;
      boolean masked = false;
      for (android.view.ViewParent up = card.getParent(); up instanceof View; up = up.getParent())
        masked |= up instanceof HiddenRecipeFrame;
      if (masked) {
        assertNotEquals("Unknown result never shows character art", View.VISIBLE, card.picture.getVisibility());
        assertNotNull(card.findViewWithTag("craft-masked-placeholder"));
        measured.put(MainActivity.obj("tag", "mix-unknown", "mode", "MASKED"));
        continue;
      }
      RegistrationCardView.Artwork art = card.picture;
      JSONObject canonical = api.data.units.get(art.id);
      assertNotNull("Result identity is a canonical server unit: " + art.id, canonical);
      assertEquals(RegistrationCardView.FULL, art.mode);
      assertNotNull("Canonical FULL bitmap loaded before proof: " + art.id, art.bitmap);
      assertEquals(art.bitmap.getWidth() / (float) art.bitmap.getHeight(), art.target.width() / art.target.height(), .001f);
      assertEquals(geometry.bounds(art, host.root), geometry.visibleIntersection(art, host.root));
      measured.put(MainActivity.obj("tag", "mix-result", "id", art.id, "mode", "FULL",
          "artWidth", art.target.width(), "artHeight", art.target.height()));
    }
    assertTrue("This state contains actual cards", measured.length() > 0);
    assertEquals("Selected slots preserve ordered duplicate material identity", host.menus.mixDraft, selectedMix);
    assertTrue(host.registrationArt().cachedBytes() <= 16 * 1024 * 1024);
    return measured;
  }

  void assertMaskedCard(RegistrationCardView card, JSONObject serverUnit) {
    assertNotNull(card);
    assertEquals(serverUnit.optString("id"), card.picture.id);
    assertFalse(card.picture.visibleIdentity);
    assertFalse("Masked IDs must never request bundled FULL pixels", card.picture.loadAttempted);
    assertNull(card.picture.bitmap);
    assertEquals(View.VISIBLE, card.name.getVisibility());
    assertEquals(View.VISIBLE, card.grade.getVisibility());
    assertEquals(serverUnit.optString("name"), card.name.getText().toString());
    assertEquals(serverUnit.optString("tierName"), card.grade.getText().toString());
    assertTrue(card.getContentDescription().toString().contains(serverUnit.optString("name")));
    readable(card.name); readable(card.grade);
  }

  void maskedCardsKeepServerIdentityWithoutRevealingArt(MainActivity host, NativeEconomyThemeFixture.Api api, int[] size) throws Exception {
    // Legitimate server mask values from GameService.face; never used for normal proof captures.
    JSONObject recipe = api.data.craft.getJSONArray("recipes").getJSONObject(0);
    JSONObject material = recipe.getJSONArray("materials").getJSONObject(0);
    String recipeImg = recipe.getString("img"), materialImg = material.getString("img");
    recipe.put("img", "q-" + recipe.getString("tier")); material.put("img", "unknown");
    host.menus.recipeId = recipe.getString("id"); host.menus.economy.recipePage = 0;
    host.menus.economy.craft(false); ready(host, size);
    RegistrationCardView choice = host.root.findViewWithTag("craft-recipe-" + recipe.getString("id"));
    assertMaskedCard(choice, recipe);
    assertMaskedCard(host.root.findViewWithTag("craft-result-image"), recipe);
    assertMaskedCard(host.root.findViewWithTag("craft-material-image-" + material.getString("id")), material);
    int[] confirmed = {0};
    host.base("조합 결과", "craftResult");
    NativeForge overlay = new NativeForge(host, recipe.getJSONArray("materials"), recipe, false,
        "마스킹 상태 검증", () -> confirmed[0]++);
    host.root.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
    overlay.showResult(false); ready(host, size);
    assertMaskedCard(overlay.findViewWithTag("forge-result-card"), recipe);
    recipe.put("img", recipeImg); material.put("img", materialImg);
    overlay.updateResult(recipe); ready(host, size);
    economyFullCards(host, "forge", api);
    Button confirm = overlay.findViewWithTag("forge-result-confirm");
    confirm.performClick(); confirm.performClick();
    assertEquals("Existing one-shot result completion remains guarded", 1, confirmed[0]);
    host.root.removeView(overlay);
    assertTrue(overlay.detached);
    assertEquals(0, api.writes);
  }

  void expeditionPagesKeepIdentityAndDuplicateIndices(MainActivity host, NativeEconomyThemeFixture.Api api) throws Exception {
    host.me().remove("trip");
    NativeEconomy economy = host.menus.economy;
    economy.expedition();
    List<String> expected = new ArrayList<>(), seen = new ArrayList<>();
    JSONArray owned = host.list(host.me(), "units");
    for (int i = 0; i < owned.length(); i++) {
      JSONObject unit = owned.optJSONObject(i);
      if (unit.optBoolean("top") && unit.optInt("have") - unit.optInt("away") > 0) expected.add(unit.optString("id"));
    }
    while (true) {
      ready(host, SIZES[0]);
      expeditionCards(host, "explore");
      int visible = 0;
      for (View view : geometry.descendants(host.root.findViewWithTag("expedition-candidates"))) {
        String tag = view.getTag() instanceof String ? view.getTag().toString() : "";
        if (tag.startsWith("expedition-add-")) { seen.add(tag.substring("expedition-add-".length())); visible++; }
      }
      assertEquals(Math.min(8, expected.size() - economy.crewPage * 8), visible);
      Button next = actions.button(host.root.findViewWithTag("expedition-candidate-pages"), "›");
      if (!next.isEnabled()) break;
      next.performClick();
    }
    assertEquals("Side paging preserves every eligible server ID and its order", expected, seen);
    // Separate behavior fixture: duplicate selected copies retain global slot identity.
    host.expeditionDraft = new ArrayList<>(Arrays.asList("D1", "D2", "D1", "D3", "D4"));
    economy.selectedCrewPage = economy.crewPage = 0;
    economy.expedition();
    ready(host, SIZES[0]);
    View exhausted = host.root.findViewWithTag("expedition-add-D1");
    assertNotNull(exhausted); assertFalse(exhausted.isEnabled());
    assertTrue(exhausted.getContentDescription().toString().contains("남은 0"));
    assertEquals(economy.find("D1").optString("name"),
        ((TextView) exhausted.findViewWithTag("economy-name-preview")).getText().toString());
    host.busy = true;
    host.root.findViewWithTag("expedition-member-0").performClick();
    assertEquals(Arrays.asList("D1", "D2", "D1", "D3", "D4"), host.expeditionDraft);
    host.busy = false;
    assertNull(host.root.findViewWithTag("expedition-selected-pages"));
    ready(host, SIZES[0]);
    host.root.findViewWithTag("expedition-member-2").performClick();
    assertEquals("Remove exactly the visible third-slot duplicate, not the first matching ID",
        Arrays.asList("D1", "D2", "D3", "D4"), host.expeditionDraft);
    ready(host, SIZES[0]);
    host.root.findViewWithTag("expedition-member-3").performClick();
    ready(host, SIZES[0]);
    host.root.findViewWithTag("expedition-member-2").performClick();
    ready(host, SIZES[0]);
    assertEquals(Arrays.asList("D1", "D2"), host.expeditionDraft);
    assertEquals("Selected slots never require paging", 0, economy.selectedCrewPage);
    assertEquals("Presentation paging and draft selection never submit automatically", 0, api.writes);
    assertNull(host.visibleDialog);
  }
}




