package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Actual APK views, fonts and asynchronous bundled card images. No painted test placeholders or server. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRegistrationReviewTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeDialogLayoutReviewTest geometry = new NativeDialogLayoutReviewTest();
  static final String[] PAGES = {"recruit", "collection", "team-story", "team-pvp", "result"};
  static final String[] IDS = {"D1", "D2", "D3", "D8", "L14", "E7", "H14", "S18", "R23", "I9"};

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restore() { RuntimeEnvironment.setFontScale(1); }

  JSONObject species(String id) throws Exception {
    JSONArray entries = RegistrationArtTest.metadata("manifest.json").getJSONArray("items");
    for (int i = 0; i < entries.length(); i++) {
      JSONObject entry = entries.getJSONObject(i);
      if (!entry.getString("id").equals(id)) continue;
      return MainActivity.obj("id", id, "name", entry.getString("name"),
          "tier", entry.getString("tier"), "tierName", entry.getString("tier_ko"),
          "known", true, "have", 3, "away", 0, "locked", 0, "top", true,
          "attr", "STONE", "attrCode", "STONE", "attrName", "돌", "image", "");
    }
    throw new AssertionError("Missing fixture identity in APK manifest: " + id);
  }

  JSONObject fixture() throws Exception {
    JSONArray units = new JSONArray(), owned = new JSONArray();
    for (int i = 0; i < IDS.length; i++) {
      JSONObject item = species(IDS[i]);
      owned.put(item);
      JSONObject instance = new JSONObject(item.toString());
      instance.put("key", "o" + (i + 1)).put("level", 12).put("hp", 100).put("maxHp", 100)
          .put("xp", 20).put("xpNext", 100).put("skills", new JSONArray());
      units.put(instance);
    }
    return MainActivity.obj("me", MainActivity.obj("name", "등록증 검토", "units", owned,
        "battleTeam", MainActivity.arr(Arrays.asList("D1", "D2", "D3"))),
        "units", units, "team", MainActivity.arr(Arrays.asList("o1", "o2", "o3")),
        "chapters", new JSONArray(), "attributes", MainActivity.obj("STONE", MainActivity.obj("name", "돌")),
        "typeChart", new JSONObject(), "tutorial", MainActivity.obj("step", 9));
  }

  JSONObject recruitment() {
    JSONArray offers = new JSONArray();
    String[] kinds = {"LOW", "MID", "HIGH"}, labels = {"하급", "중급", "고급"};
    for (int i = 0; i < kinds.length; i++) offers.put(MainActivity.obj(
        "kind", kinds[i], "label", labels[i], "cost", i == 2 ? 5 : i + 1,
        "img", "C1", "weights", new JSONArray().put(MainActivity.obj(
            "tier", "COMMON", "tierName", "흔함", "w", 100))));
    return MainActivity.obj("credits", 100, "claim", 5, "wait", 3600000, "gachas", offers,
        "pity", MainActivity.obj("tierName", "전설", "left", 9));
  }

  final class FakeApi extends NativeApi {
    final JSONObject stored;
    final List<String> reads = new ArrayList<>(), writes = new ArrayList<>();
    FakeApi(MainActivity host) throws Exception { super(host); stored = fixture(); }
    @Override String token() { return "offline-registration-review"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        writes.add(path);
        throw new AssertionError("Review is read-only; unexpected API write: " + path);
      }
      reads.add(path);
      if (path.equals("draw")) done.done(recruitment(), null);
      else if (path.equals("native/state")) done.done(stored, null);
      else if (path.equals("native/catalog"))
        done.done(MainActivity.obj("units", stored.optJSONObject("me").optJSONArray("units")), null);
      else throw new AssertionError("Unexpected fixture API read: " + path);
    }
  }

  FakeApi prepare(MainActivity host) throws Exception {
    host.api.close();
    FakeApi api = new FakeApi(host);
    host.api = api;
    host.state = api.stored;
    host.art.key = "offline-registration-review";
    host.guide.enabled = false;
    host.motionEnabled = false;
    return api;
  }

  void layout(MainActivity host, int width, int height) {
    review.viewport(host, width, height);
    // Match GameSafeArea's effective insets; prevent the synthetic Window from replacing them.
    ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(width == 1006 ? 36 : 0, width == 1006 ? 10 : 0,
        width == 1006 ? 10 : 0, width == 1006 ? 10 : 0);
    Runnable measure = () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, width, height);
    };
    measure.run();
    review.preparePreDraw(host.root, measure);
  }

  void show(MainActivity host, String page) throws Exception {
    if (page.equals("recruit")) host.menus.economy.draw();
    else if (page.equals("collection")) host.collection();
    else if (page.startsWith("team-")) {
      host.selectionMode = page.equals("team-pvp") ? "pvp" : "story";
      host.menus.teamDraft = null;
      host.team();
    } else {
      JSONArray items = new JSONArray();
      for (String id : new String[] {"D8", "E7", "L14"}) {
        JSONObject unit = species(id);
        unit.put("n", id.equals("D8") ? 3 : 1).put("first", !id.equals("L14"));
        items.put(unit);
      }
      // A deterministic recorded-result fixture exercises the real result and acquisition actions.
      // It does not manufacture a draw API response, mutate inventory or call a real account.
      host.menus.economy.reveal(MainActivity.obj("items", items), () -> {}, true);
    }
  }

  List<RegistrationCardView.Artwork> artworks(View root) {
    List<RegistrationCardView.Artwork> cards = new ArrayList<>();
    for (View view : geometry.descendants(root))
      if (view instanceof RegistrationCardView.Artwork && view.isShown())
        cards.add((RegistrationCardView.Artwork) view);
    return cards;
  }

  void awaitRealArt(MainActivity host, String page, int width, int height) throws Exception {
    // Drawing once starts the normal asynchronous scene pipeline; no test drawable is inserted.
    Bitmap kickoff = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(kickoff)); } finally { kickoff.recycle(); }
    RegistrationArtTest.await(() -> !host.busy && host.menus.pendingFetches == 0 && host.art.idle()
        && host.registrationArt().idle());
    layout(host, width, height);
    if (!page.equals("recruit")) {
      List<RegistrationCardView.Artwork> cards = artworks(host.root);
      assertEquals("Actual registration artwork count on " + page,
          page.equals("result") ? 3 : page.equals("collection") ? host.menus.library.collectionPageSize() : 6, cards.size());
      for (RegistrationCardView.Artwork card : cards) {
        assertTrue("Registration image requires positive layout space before decode: "
                + card.id + " tag=" + card.getTag() + " view=" + card.getWidth() + "x" + card.getHeight(),
            card.getWidth() > 0 && card.getHeight() > 0);
        assertTrue("Registration image must remain attached while waiting: " + card.id,
            card.isAttachedToWindow());
      }
      RegistrationArtTest.await(() -> {
        for (RegistrationCardView.Artwork card : cards)
          if (card.bitmap == null || card.status.length() != 0) return false;
        return true;
      });
      for (RegistrationCardView.Artwork card : cards) {
        assertFalse(card.bitmap.isRecycled());
        assertTrue("Loaded real asset, not a one-pixel placeholder", card.bitmap.getWidth() >= 64);
        assertTrue("All shown IDs exist in canonical bundle", RegistrationArtTest.canonicalIds().contains(card.id));
        int bucket = RegistrationArt.widthBucket(card.mode, card.getWidth(), card.getHeight());
        Bitmap reference = RegistrationArtTest.expected(host, card.id, card.mode, bucket);
        try { RegistrationArtTest.samePixels(page + "/" + card.id, reference, card.bitmap); }
        finally { reference.recycle(); }
      }
    } else {
      List<NativeEconomy.RecruitmentSeal> seals = new ArrayList<>();
      for (View view : geometry.descendants(host.root))
        if (view instanceof NativeEconomy.RecruitmentSeal && view.isShown())
          seals.add((NativeEconomy.RecruitmentSeal) view);
      assertEquals("Three real recruitment envelopes remain on the three stations", 3, seals.size());
      byte[] source = RegistrationArtTest.asset(host, "resources/application-envelope-atlas.png");
      Bitmap reference = BitmapFactory.decodeByteArray(source, 0, source.length);
      assertNotNull("Published envelope atlas decodes independently", reference);
      try {
        for (NativeEconomy.RecruitmentSeal seal : seals) {
          visible(host, seal);
          assertNotNull("The production Canvas seal has loaded its real atlas", seal.atlas);
          assertFalse(seal.atlas.isRecycled());
          assertEquals(1774, seal.atlas.getWidth());
          assertEquals(887, seal.atlas.getHeight());
          RegistrationArtTest.samePixels("Canonical recruitment envelope atlas", reference, seal.atlas);
          Bitmap rendered = Bitmap.createBitmap(seal.getWidth(), seal.getHeight(), Bitmap.Config.ARGB_8888);
          try {
            seal.draw(new Canvas(rendered));
            assertFalse("The production envelope was actually drawn", seal.target.isEmpty());
            assertTrue("Envelope contain target " + seal.target + " inside " + seal.getWidth() + "x" + seal.getHeight(),
                new RectF(0, 0, seal.getWidth(), seal.getHeight()).contains(seal.target));
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < rendered.getHeight(); y++)
              for (int x = 0; x < rendered.getWidth(); x++) {
                int pixel = rendered.getPixel(x, y);
                if (Color.alpha(pixel) > 0) colors.add(pixel);
              }
            assertTrue("The seal is real artwork, not a blank or flat placeholder", colors.size() > 16);
          } finally { rendered.recycle(); }
        }
      } finally { reference.recycle(); }
    }
  }

  View tag(MainActivity host, String tag) {
    View view = host.root.findViewWithTag(tag);
    assertNotNull("Required action: " + tag, view);
    return view;
  }

  void visible(MainActivity host, View view) {
    NativeNoScrollReviewTest.assertFullyVisible(view, host.root);
    Rect box = geometry.bounds(view, host.root);
    RectF safe = host.safeBounds(host.root.getWidth(), host.root.getHeight());
    assertTrue("Inside safe insets: " + view.getTag() + " " + box, safe.contains(new RectF(box)));
  }

  void action(MainActivity host, String tag) {
    View view = tag(host, tag);
    assertTrue("Action is an actual Button: " + tag, view instanceof Button);
    visible(host, view);
    assertTrue("48dp touch target: " + tag, view.getHeight() >= host.dp(48));
    TextView label = (TextView) view;
    assertNotNull("Native text layout: " + tag, label.getLayout());
    assertTrue("Action text has vertical room: " + tag,
        label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom()
            >= label.getLayout().getHeight());
    for (int line = 0; line < label.getLayout().getLineCount(); line++)
      assertEquals("Action text cannot be ellipsized: " + tag, 0, label.getLayout().getEllipsisCount(line));
  }

  JSONObject check(MainActivity host, String page, FakeApi api) throws Exception {
    JSONArray bounds = new JSONArray();
    List<RegistrationCardView.Artwork> cards = artworks(host.root);
    int overlapChecks = 0;
    View interactionSurface = host.root;
    if (page.equals("result")) {
      for (View view : geometry.descendants(host.root))
        if (view instanceof NativeReveal) interactionSurface = view;
      assertTrue("Audit the visible result overlay, not its covered header", interactionSurface instanceof NativeReveal);
    }
    List<View> activeViews = geometry.descendants(interactionSurface);
    for (RegistrationCardView.Artwork card : cards) {
      visible(host, card);
      assertNotNull("Ready bitmap", card.bitmap);
      assertEquals("Primary cards always include the original printed identity", RegistrationCardView.FULL, card.mode);
      float minimum = page.equals("collection") ? 180 : page.equals("result") ? 180 : 144;
      assertTrue("Large complete card instead of tiny portrait: " + card.getTag() + " " + card.target,
          card.target.height() >= host.dp(minimum));
      assertFalse("Nonempty contain target", card.target.isEmpty());
      assertTrue("Entire approved card/window remains inside its view: " + card.id
              + " tag=" + card.getTag() + " view=" + card.getWidth() + "x" + card.getHeight()
              + " bitmap=" + card.bitmap.getWidth() + "x" + card.bitmap.getHeight()
              + " target=" + card.target,
          new RectF(0, 0, card.getWidth(), card.getHeight()).contains(card.target));
      assertEquals("Contain is centered horizontally", card.getWidth() / 2f, card.target.centerX(), .01f);
      assertEquals("Contain is centered vertically", card.getHeight() / 2f, card.target.centerY(), .01f);
      assertEquals("Contain never squeezes image", card.bitmap.getWidth() / (float) card.bitmap.getHeight(),
          card.target.width() / card.target.height(), .0001f);
      Rect own = geometry.bounds(card, host.root);
      bounds.put(MainActivity.obj("id", card.id, "mode", card.mode,
          "left", own.left, "top", own.top, "right", own.right, "bottom", own.bottom,
          "bitmapWidth", card.bitmap.getWidth(), "bitmapHeight", card.bitmap.getHeight(),
          "targetLeft", card.target.left, "targetTop", card.target.top,
          "targetRight", card.target.right, "targetBottom", card.target.bottom));
      for (View action : activeViews)
        if (action instanceof Button && action.isShown()) {
          assertFalse("Artwork may not cover action: " + card.id + "/" + ((Button) action).getText(),
              Rect.intersects(own, geometry.bounds(action, host.root)));
          overlapChecks++;
        }
    }
    for (int i = 0; i < cards.size(); i++)
      for (int j = i + 1; j < cards.size(); j++) {
        assertFalse("Registration pictures cannot overlap each other",
            Rect.intersects(geometry.bounds(cards.get(i), host.root), geometry.bounds(cards.get(j), host.root)));
        overlapChecks++;
      }
    int actions = 0;
    if (page.equals("collection") || page.startsWith("team-")) {
      TextView pageNumber = (TextView) tag(host,
          page.equals("collection") ? "collection-page-number" : "team-candidate-page");
      visible(host, pageNumber);
      assertFalse("Page count stays visible even when previous/next is disabled", pageNumber.getText().toString().isEmpty());
      assertTrue("Page count has its own solid reading surface", pageNumber.getBackground() instanceof android.graphics.drawable.ColorDrawable);
      int background = ((android.graphics.drawable.ColorDrawable) pageNumber.getBackground()).getColor();
      assertTrue("Counter text contrast at least 4.5:1", androidx.core.graphics.ColorUtils.calculateContrast(
          pageNumber.getCurrentTextColor(), background) >= 4.5);
    }
    if (page.equals("recruit")) {
      for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
        View station = tag(host, "draw-card-" + kind);
        visible(host, station);
        assertTrue("Every tier is an inline native selection", station.isClickable());
        assertTrue("Large station touch target", station.getWidth() >= host.dp(48) && station.getHeight() >= host.dp(48));
        actions++;
      }
      action(host, "draw-submit");
      View quantity = tag(host, "draw-quantity"), total = tag(host, "draw-total"), submit = tag(host, "draw-submit");
      visible(host, quantity); visible(host, total); visible(host, tag(host, "draw-pity"));
      assertSame(quantity.getParent(), total.getParent()); assertSame(total.getParent(), submit.getParent());
      assertTrue("Quantity precedes total in the shared rail", quantity.getRight() <= total.getLeft());
      assertTrue("Total precedes spend action in the shared rail", total.getRight() <= submit.getLeft());
      assertTrue("Initial total reports one recruitment and its exact cost", ((TextView) total).getText().toString().contains("1회 · 총 지원서 "));
      assertEquals("모집하기", ((TextView) submit).getText().toString());
      assertTrue(submit.getContentDescription().toString().contains("1회"));
      int spendActions = 0;
      for (View view : geometry.descendants(host.root)) if ("draw-submit".equals(view.getTag())) spendActions++;
      assertEquals("Exactly one spend action for the selected tier", 1, spendActions);
      actions++;
    } else if (page.equals("collection")) {
      for (String name : new String[] {"collection-growth", "collection-team", "collection-craft", "collection-record"}) {
        action(host, name);
        actions++;
      }
      assertEquals(RegistrationCardView.FULL,
          ((RegistrationCardView.Artwork) tag(host, "collection-registration-" + host.menus.library.selectedCollectionId)).mode);
      assertEquals("D1", host.menus.library.selectedCollectionId);
    } else if (page.startsWith("team-")) {
      action(host, "tutorial:team-save");
      action(host, "team-remove");
      TextView remove = (TextView) tag(host, "team-remove");
      assertEquals("비우기", remove.getText().toString());
      assertEquals("Clear action must stay on one readable line", 1, remove.getLineCount());
      assertTrue("Clear label fits inside its actual compound padding",
          Math.ceil(remove.getLayout().getLineWidth(0))
              <= remove.getWidth() - remove.getCompoundPaddingLeft() - remove.getCompoundPaddingRight());
      actions += 2;
      boolean pvp = page.equals("team-pvp");
      assertEquals(pvp ? Arrays.asList("D1", "D2", "D3") : Arrays.asList("o1", "o2", "o3"), host.menus.teamDraft);
      for (int i = 0; i < 3; i++) {
        View slot = tag(host, "lineup-slot-" + i);
        View grip = slot.findViewWithTag(NativeTeamReorder.HANDLE_TAG);
        assertNotNull(grip);
        visible(host, grip);
        assertTrue(grip.getWidth() >= host.dp(48) && grip.getHeight() >= host.dp(48));
        assertTrue("Drag action remains accessible", grip.isFocusable());
        TextView state = slot.findViewWithTag("lineup-runtime-state");
        assertNotNull(state);
        visible(host, state);
        assertEquals("Dynamic state remains readable without ellipsis", 0, state.getLayout().getEllipsisCount(0));
        if (!pvp) {
          assertTrue(state.getText().toString().contains("Lv12"));
          assertTrue(state.getText().toString().contains("100/100"));
          assertTrue(state.getContentDescription().toString().contains("체력"));
        }
        assertEquals("D" + (i + 1), ((RegistrationCardView.Artwork) slot.findViewWithTag("lineup-portrait")).id);
        actions++;
      }
    } else {
      for (String name : new String[] {"acquisition-detail", "acquisition-grow", "acquisition-team", "acquisition-result-picker", "acquisition-confirm"}) {
        action(host, name);
        actions++;
      }
      String labels = flow.labels(host.root);
      for (String id : new String[] {"D8", "E7", "L14"}) {
        View card = tag(host, "acquisition-card-" + id);
        assertTrue("Server identity remains available to accessibility",
            card.getContentDescription().toString().contains(species(id).getString("name")));
        assertTrue("Server tier remains available to accessibility",
            card.getContentDescription().toString().contains(species(id).getString("tierName")));
      }
      assertTrue(labels.contains("NEW"));
      assertTrue(labels.contains("×3"));
      assertEquals(3, host.menus.lastDraw.getJSONArray("items").length());
    }
    assertTrue("Review never writes to API", api.writes.isEmpty());
    assertTrue("At least one action was checked", actions > 0);
    if (!page.equals("recruit")) assertTrue("Pairwise overlap checks ran", overlapChecks > 0);
    return MainActivity.obj("page", page, "width", host.root.getWidth(), "height", host.root.getHeight(),
        "fontScale", host.getResources().getConfiguration().fontScale,
        "insets", new JSONArray(Arrays.asList(host.root.getPaddingLeft(), host.root.getPaddingTop(),
            host.root.getPaddingRight(), host.root.getPaddingBottom())),
        "checkedActions", actions, "overlapChecks", overlapChecks,
        "loadedRegistrationImages", cards.size(), "registrationBounds", bounds,
        "apiWrites", api.writes.size(), "realBundledImages", true);
  }

  @Test public void enlargedCardsKeepSafeInsetsAndActionsAtLargeFontOnWidePhoneAndTablet() throws Exception {
    RuntimeEnvironment.setFontScale(1.3f);
    for (int[] size : new int[][] {{1006, 411}, {1280, 720}}) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(1.3f);
      for (String page : new String[] {"collection", "team-story", "team-pvp", "result"}) {
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get();
          FakeApi api = prepare(host);
          layout(host, size[0], size[1]);
          show(host, page);
          layout(host, size[0], size[1]);
          awaitRealArt(host, page, size[0], size[1]);
          layout(host, size[0], size[1]);
          check(host, page, api);
        }
      }
    }
  }

  /** Always written, including failures before the checked metrics are available. */
  JSONObject diagnosticState(MainActivity host) {
    JSONArray cards = new JSONArray();
    for (View view : geometry.descendants(host.root)) {
      if (!(view instanceof RegistrationCardView.Artwork)) continue;
      RegistrationCardView.Artwork card = (RegistrationCardView.Artwork) view;
      Rect bounds = geometry.bounds(card, host.root);
      View parent = card.getParent() instanceof View ? (View) card.getParent() : null;
      cards.put(MainActivity.obj("id", card.id, "tag", String.valueOf(card.getTag()),
          "mode", card.mode, "width", card.getWidth(), "height", card.getHeight(),
          "left", bounds.left, "top", bounds.top, "right", bounds.right, "bottom", bounds.bottom,
          "shown", card.isShown(), "attached", card.isAttachedToWindow(),
          "loadAttempted", card.loadAttempted, "status", card.status,
          "visibleIdentity", card.visibleIdentity,
          "bitmapWidth", card.bitmap == null ? 0 : card.bitmap.getWidth(),
          "bitmapHeight", card.bitmap == null ? 0 : card.bitmap.getHeight(),
          "targetLeft", card.target.left, "targetTop", card.target.top,
          "targetRight", card.target.right, "targetBottom", card.target.bottom,
          "parentTag", parent == null ? "" : String.valueOf(parent.getTag()),
          "parentWidth", parent == null ? 0 : parent.getWidth(),
          "parentHeight", parent == null ? 0 : parent.getHeight()));
    }
    return MainActivity.obj("epoch", host.epoch, "busy", host.busy,
        "pendingFetches", host.menus.pendingFetches, "nativeArtIdle", host.art.idle(),
        "registrationPending", host.registrationArt().pendingCount(),
        "registrationCacheBytes", host.registrationArt().cachedBytes(),
        "registrationClosed", host.registrationArt().closed(), "artworks", cards);
  }

  void writeScreenshot(MainActivity host, Path target, int width, int height) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(bitmap));
      Set<Integer> pixels = new HashSet<>();
      for (int y = 0; y < height; y += 7)
        for (int x = 0; x < width; x += 7) pixels.add(bitmap.getPixel(x, y));
      assertTrue("A meaningful native screenshot contains actual artwork and text", pixels.size() > 64);
      try (OutputStream stream = Files.newOutputStream(target)) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    } finally { bitmap.recycle(); }
  }

  @Test public void renderTwentyRealAssetScreensWithSafeAreaActionsAndOverlapEvidence() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/reports/native-review"), "registration");
    Files.createDirectories(output);
    JSONObject findings = new JSONObject(), metrics = new JSONObject(), diagnostics = new JSONObject();
    List<String> failures = new ArrayList<>();
    int exported = 0;
    int[][] variants = {{915, 412, 100}, {1006, 411, 100}, {1280, 720, 100}, {915, 412, 130}};
    for (int[] variant : variants) for (String page : PAGES) {
      int width = variant[0], height = variant[1];
      String stem = page + "-" + width + "x" + height + (variant[2] == 130 ? "-font130" : "");
      findings.put(stem, new JSONArray().put("Capture and assertions have not completed"));
    }
    try {
      for (int[] variant : variants) {
        int width = variant[0], height = variant[1];
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-land-mdpi");
        RuntimeEnvironment.setFontScale(variant[2] / 100f);
        for (String page : PAGES) {
          String stem = page + "-" + width + "x" + height + (variant[2] == 130 ? "-font130" : "");
          JSONArray errors = new JSONArray();
          try (ActivityController<MainActivity> controller = flow.start()) {
            MainActivity host = controller.get();
            FakeApi api = prepare(host);
            layout(host, width, height);
            show(host, page);
            layout(host, width, height);
            try {
              awaitRealArt(host, page, width, height);
              layout(host, width, height);
              metrics.put(stem, check(host, page, api));
            } catch (AssertionError | Exception error) {
              errors.put(error.toString());
            }
            diagnostics.put(stem, diagnosticState(host));
            // Preserve the actual failed screen for diagnosis, but never mark it as a passing capture.
            try {
              writeScreenshot(host, output.resolve(stem + ".png"), width, height);
              exported++;
            } catch (AssertionError | Exception error) { errors.put(error.toString()); }
          } catch (AssertionError | Exception error) { errors.put(error.toString()); }
          findings.put(stem, errors);
          if (errors.length() > 0) failures.add(stem + ": " + errors);
          Files.write(output.resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
          Files.write(output.resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
          Files.write(output.resolve("diagnostics.json"), diagnostics.toString(2).getBytes(StandardCharsets.UTF_8));
        }
      }
    } finally {
      Files.write(output.resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output.resolve("metrics.json"), metrics.toString(2).getBytes(StandardCharsets.UTF_8));
      Files.write(output.resolve("diagnostics.json"), diagnostics.toString(2).getBytes(StandardCharsets.UTF_8));
      RuntimeEnvironment.setFontScale(1);
    }
    assertEquals("All screenshot cases are recorded", 20, findings.length());
    assertEquals("All required PNGs are exported", 20, exported);
    assertTrue("Registration geometry/assets/actions failed:\n" + String.join("\n", failures), failures.isEmpty());
    assertEquals("Every screenshot has successful bounds/overlap/action evidence", 20, metrics.length());
  }
}
