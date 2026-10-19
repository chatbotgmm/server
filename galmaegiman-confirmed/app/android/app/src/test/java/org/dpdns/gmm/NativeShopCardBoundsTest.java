package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.Looper;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** Stage 1: actual Android measurement, glyph ink and bundled frame art. No server access. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h374dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeShopCardBoundsTest {
  static final String BASE = "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89";
  static final String ASSET = "release-v1/ui/button-secondary.png";
  static final String ASSET_SHA = "267b4d660290f8ff5b169e8dc5582c38b49ff905";
  static final String[] STATES = {"first-page", "blocked-selected", "second-page"};
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest render = new NativeRenderReviewTest();
  final NativeRenderReviewTestEconomy controls = new NativeRenderReviewTestEconomy();

  static final class Profile {
    final String id;
    final int w, h, dpi, font;
    final int[] insets;
    Profile(String id, int w, int h, int dpi, int font, boolean inset) {
      this.id = id + "-font" + font + (inset ? "-insets" : "");
      this.w = w; this.h = h; this.dpi = dpi; this.font = font;
      insets = inset ? new int[] {32, 12, 12, 12} : new int[] {0, 0, 0, 0};
    }
    int widthDp() { return Math.round(w * 160f / dpi); }
    int heightDp() { return Math.round(h * 160f / dpi); }
    String qualifiers() {
      return "w" + widthDp() + "dp-h" + heightDp() + "dp-land-" + dpi + "dpi";
    }
  }

  static List<Profile> profiles() {
    List<Profile> result = new ArrayList<>();
    for (int[] size : new int[][] {{915,374}, {780,319}})
      for (int font : new int[] {100,130}) for (boolean inset : new boolean[] {false,true})
        result.add(new Profile(size[0] + "x" + size[1], size[0], size[1], 160, font, inset));
    // A screenshot does not reveal density. Exercise both plausible densities at its exact pixels.
    for (int dpi : new int[] {360,420})
      for (int font : new int[] {100,130}) for (boolean inset : new boolean[] {false,true})
        result.add(new Profile("2048x838-dpi" + dpi, 2048, 838, dpi, font, inset));
    result.add(new Profile("tablet1280x720", 1280, 720, 160, 100, false));
    return result;
  }

  @Before public void isolate() {
    flow.isolate();
    render.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  Path output() throws Exception {
    Path output = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feedback-stage1-shop");
    Files.createDirectories(output);
    return output;
  }

  void layout(MainActivity host, Profile p) {
    ViewCompat.setOnApplyWindowInsetsListener(host.root, null);
    host.root.setPadding(host.dp(p.insets[0]), host.dp(p.insets[1]), host.dp(p.insets[2]), host.dp(p.insets[3]));
    Runnable measure = () -> {
      render.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(p.w, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(p.h, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, p.w, p.h);
    };
    measure.run();
    render.preparePreDraw(host.root, measure);
  }

  void ready(MainActivity host, Profile p) throws Exception {
    layout(host, p);
    Bitmap image = Bitmap.createBitmap(p.w, p.h, Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(image)); } finally { image.recycle(); }
    RegistrationArtTest.await(() -> host.art.idle() && host.menus.pendingFetches == 0);
    layout(host, p);
  }

  void capture(MainActivity host, Profile p, String name) throws Exception {
    Bitmap image = Bitmap.createBitmap(p.w, p.h, Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(image));
      try (OutputStream out = Files.newOutputStream(output().resolve(name + ".png"))) {
        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
      }
    } finally { image.recycle(); }
  }

  Rect bounds(View view, ViewGroup root) {
    // getDrawingRect includes this View's own scroll. The ancestor conversion
    // subtracts it once, giving its stationary box, not its scrolled text origin.
    // Glyph geometry separately subtracts text.getScrollX/Y exactly once below.
    Rect out = new Rect();
    view.getDrawingRect(out);
    root.offsetDescendantRectToMyCoords(view, out);
    return out;
  }

  @Test public void viewBoxIgnoresOwnScrollButRetainsAncestorScrollAndClipping() {
    try (var controller = flow.start()) {
      MainActivity host = controller.get();
      FrameLayout root = new FrameLayout(host), viewport = new FrameLayout(host);
      TextView label = host.text("5,000", 14);
      root.addView(viewport); viewport.addView(label);
      root.layout(0, 0, 300, 200); viewport.layout(20, 30, 120, 130);
      label.layout(15, 10, 95, 50); label.scrollTo(523000, 7);
      assertEquals(new Rect(35, 40, 115, 80), bounds(label, root));
      assertEquals(bounds(label, root), visible(label, root));
      viewport.scrollTo(10, 5);
      assertEquals(new Rect(25, 35, 105, 75), bounds(label, root));
      assertEquals(bounds(label, root), visible(label, root));
      label.layout(80, 80, 160, 120);
      assertEquals(new Rect(90, 105, 170, 145), bounds(label, root));
      assertEquals(new Rect(90, 105, 120, 130), visible(label, root));
      assertNotEquals("Real ancestor clipping still fails full visibility", bounds(label, root), visible(label, root));
    }
  }

  Rect visible(View view, ViewGroup root) {
    Rect out = bounds(view, root);
    for (View current = view; current != root;) {
      assertTrue(current.getParent() instanceof ViewGroup);
      ViewGroup parent = (ViewGroup) current.getParent();
      Rect clip = parent == root ? new Rect(0, 0, root.getWidth(), root.getHeight()) : bounds(parent, root);
      if (parent.getClipToPadding()) {
        clip.left += parent.getPaddingLeft(); clip.top += parent.getPaddingTop();
        clip.right -= parent.getPaddingRight(); clip.bottom -= parent.getPaddingBottom();
      }
      if (parent.getClipChildren() && !out.intersect(clip)) return new Rect();
      current = parent;
    }
    return out;
  }

  JSONArray rect(Rect r) { return new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom); }
  JSONArray rect(RectF r) throws JSONException { return new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom); }

  List<View> descendants(View view) {
    List<View> result = new ArrayList<>(); result.add(view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) result.addAll(descendants(group.getChildAt(i)));
    }
    return result;
  }

  void verifyFrameAsset(MainActivity host) throws Exception {
    try (InputStream stream = host.getAssets().open("native/" + ASSET)) {
      byte[] data = stream.readAllBytes();
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(("blob " + data.length + "\0").getBytes(StandardCharsets.UTF_8));
      byte[] hash = digest.digest(data);
      StringBuilder hex = new StringBuilder();
      for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
      assertEquals("The reading bounds are calibrated to the unmodified bundled PAPER art", ASSET_SHA, hex.toString());
    }
    assertNotNull(ThemeArt.of(host).image("ui/button-secondary.png"));
  }

  String[] expected(MainActivity host, String key) {
    JSONObject me = host.me(), prices = me.optJSONObject("prices");
    if (key.equals("shop:tickets")) {
      JSONObject price = prices.optJSONObject("tickets");
      return new String[] {"지원서 " + price.optInt("amount") + "장", "새우gang " + price.optInt("snack") + "개", "보유 " + me.optInt("credits") + "장"};
    }
    if (key.equals("shop:peanutTickets")) {
      return new String[] {"지원서 " + prices.optJSONObject("peanutTickets").optInt("amount") + "장", "땅콩 1개", "땅콩 부족"};
    }
    String id = key.substring("items/buy:".length());
    for (int i = 0; i < me.optJSONArray("itemDefs").length(); i++) {
      JSONObject definition = me.optJSONArray("itemDefs").optJSONObject(i);
      if (!id.equals(definition.optString("id"))) continue;
      int count = 0;
      for (int j = 0; j < me.optJSONArray("items").length(); j++) {
        JSONObject item = me.optJSONArray("items").optJSONObject(j);
        if (id.equals(item.optString("id"))) count = item.optInt("count");
      }
      return new String[] {definition.optString("name"), "새우gang " + definition.optInt("price") + "개",
          "보유 " + count + " / " + me.optJSONObject("itemRules").optInt("maxStack")};
    }
    throw new AssertionError("Unexpected shop card " + key);
  }

  JSONObject auditText(MainActivity host, TextView text, String expected, float sizeSp, Rect reading) throws JSONException {
    assertEquals(expected, text.getText().toString());
    assertNotNull(text.getLayout());
    assertTrue("Visible text must have a native line", text.getLineCount() > 0);
    TextView reference = host.text("", sizeSp);
    assertEquals("Original font-scaled text size", reference.getTextSize(), text.getTextSize(), .01f);
    Rect own = bounds(text, host.root), shown = visible(text, host.root);
    assertEquals("The whole text View remains visible", own, shown);
    Layout layout = text.getLayout();
    assertEquals("All original copy reaches the layout", expected.length(), layout.getLineEnd(layout.getLineCount() - 1));
    JSONArray inkRects = new JSONArray(), layoutRects = new JSONArray();
    float x = own.left + text.getCompoundPaddingLeft() - text.getScrollX();
    float y = own.top + text.getBaseline() - layout.getLineBaseline(0) - text.getScrollY();
    RectF readingArea = new RectF(reading);
    int ellipsis = 0;
    for (int line = 0; line < layout.getLineCount(); line++) {
      ellipsis += layout.getEllipsisCount(line);
      String content = expected.substring(layout.getLineStart(line), layout.getLineEnd(line)).stripTrailing();
      Rect ink = new Rect();
      text.getPaint().getTextBounds(content, 0, content.length(), ink);
      float lineX = x + layout.getLineLeft(line), baseline = y + layout.getLineBaseline(line);
      RectF glyphs = new RectF(lineX + ink.left, baseline + ink.top, lineX + ink.right, baseline + ink.bottom);
      RectF lineBox = new RectF(lineX, y + layout.getLineTop(line), x + layout.getLineRight(line), y + layout.getLineBottom(line));
      assertTrue("Glyph ink clears the actual ornamental band: " + expected + " ink=" + glyphs + " reading=" + reading,
          readingArea.contains(glyphs));
      assertTrue("Full native line box clears the frame: " + expected + " line=" + lineBox + " reading=" + reading,
          readingArea.contains(lineBox));
      assertTrue("No text horizontal scrolling", text.getScrollX() == 0);
      assertTrue("Line fits text content width", layout.getLineWidth(line)
          <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + .01f);
      assertTrue("Glyph ink is not clipped by its own View", new RectF(own).contains(glyphs));
      assertTrue("Native line is not clipped by its own View", new RectF(own).contains(lineBox));
      inkRects.put(rect(glyphs)); layoutRects.put(rect(lineBox));
    }
    assertEquals("Prices and states may not be ellipsized", 0, ellipsis);
    return MainActivity.obj("tag", text.getTag(), "text", text.getText().toString(), "expectedText", expected,
        "fontSizePx", text.getTextSize(), "expectedFontSizePx", reference.getTextSize(), "viewRect", rect(own),
        "visibleRect", rect(shown), "inkRects", inkRects, "layoutRects", layoutRects,
        "ellipsisCount", ellipsis, "lineCount", layout.getLineCount(), "fullText", true);
  }

  JSONObject audit(MainActivity host, Profile p, String state, NativeEconomyThemeFixture.Api api) throws Exception {
    verifyFrameAsset(host);
    assertEquals(p.w, host.root.getWidth()); assertEquals(p.h, host.root.getHeight());
    assertEquals(p.dpi, host.getResources().getDisplayMetrics().densityDpi);
    assertEquals(p.font / 100f, host.getResources().getConfiguration().fontScale, .001f);
    JSONArray cards = new JSONArray(), controlRects = new JSONArray();
    Integer top = null, bottom = null;
    List<String> keys = state.equals("second-page")
        ? List.of("items/buy:big", "items/buy:clear", "items/buy:feather")
        : List.of("shop:tickets", "shop:peanutTickets", "items/buy:potion");
    float density = host.getResources().getDisplayMetrics().density;
    for (String key : keys) {
      ViewGroup card = host.root.findViewWithTag("shop-card-" + key);
      assertNotNull(card);
      Rect outer = bounds(card, host.root), shown = visible(card, host.root);
      assertEquals("Whole frame remains inside shelf and safe area", outer, shown);
      if (top == null) { top = outer.top; bottom = outer.bottom; }
      assertEquals("Equal card top", (int) top, outer.top);
      assertEquals("Equal card bottom", (int) bottom, outer.bottom);
      // Independent of card padding: maximum source bands in ThemeArt.surface, plus a 1dp ink gutter.
      Rect reading = new Rect(outer);
      reading.inset(Math.round(density * (80f / 3f + 1)), Math.round(density * (72f / 3f + 1)));
      assertTrue(reading.width() > 0 && reading.height() > 0);
      String[] exact = expected(host, key);
      JSONArray labels = new JSONArray();
      for (int i = 0; i < exact.length; i++) {
        TextView label = (TextView) card.getChildAt(i + 1);
        if (exact[i].isEmpty()) {
          assertEquals("Blank status reserves native row height", View.INVISIBLE, label.getVisibility());
          assertTrue(label.getMeasuredHeight() > 0);
          continue;
        }
        assertEquals(View.VISIBLE, label.getVisibility());
        labels.put(auditText(host, label, exact[i], i == 0 ? (host.compact() ? 14 : 18)
            : i == 1 ? (host.compact() ? 13 : 16) : (host.compact() ? 12 : 14), reading));
      }
      ImageView icon = (ImageView) card.getChildAt(0);
      assertTrue("Product illustration retains at least 24dp useful height", icon.getHeight() >= host.dp(24));
      assertNotNull("The real bundled product icon is present", icon.getDrawable());
      Rect iconBounds = bounds(icon, host.root);
      assertTrue("Illustration remains in its own frame", reading.contains(iconBounds));
      RectF imageBounds = new RectF(icon.getDrawable().getBounds());
      icon.getImageMatrix().mapRect(imageBounds);
      imageBounds.offset(iconBounds.left + icon.getPaddingLeft(), iconBounds.top + icon.getPaddingTop());
      assertTrue("Drawn product image retains at least 24dp height", imageBounds.height() + .01f >= host.dp(24));
      assertTrue("Drawn image has positive width", imageBounds.width() > 0);
      assertTrue("Drawn image stays inside its View", new RectF(iconBounds).contains(imageBounds));
      Rect nameBounds = bounds(card.getChildAt(1), host.root);
      assertTrue("Icon precedes the name vertically or in the compact horizontal header",
          iconBounds.bottom <= nameBounds.top || iconBounds.right <= nameBounds.left);
      assertFalse("The complete icon and name Views do not overlap", Rect.intersects(iconBounds, nameBounds));
      assertTrue(card.getBackground() instanceof ThemeArt.SurfaceDrawable);
      assertEquals(ThemeArt.Surface.PAPER, ((ThemeArt.SurfaceDrawable) card.getBackground()).type);
      cards.put(MainActivity.obj("tag", card.getTag(), "rect", rect(outer), "visibleRect", rect(shown),
          "readingRect", rect(reading), "iconRect", rect(iconBounds), "iconImageRect", rect(imageBounds),
          "minIconHeightPx", host.dp(24), "asset", ASSET, "assetSha", ASSET_SHA, "texts", labels));
    }
    for (View control : descendants(host.root)) {
      if (!control.isShown() || !control.isClickable()) continue;
      Rect own = bounds(control, host.root), shown = visible(control, host.root);
      assertEquals("Full touch target stays visible: " + control.getTag(), own, shown);
      assertTrue("48dp touch width", own.width() >= host.dp(48));
      assertTrue("48dp touch height", own.height() >= host.dp(48));
      String tag = control.getTag() == null ? control.getClass().getSimpleName() + ":"
          + (control instanceof TextView ? ((TextView) control).getText() : control.getContentDescription()) : control.getTag().toString();
      controlRects.put(MainActivity.obj("tag", tag, "rect", rect(own), "visibleRect", rect(shown),
          "enabled", control.isEnabled(), "minTouchTargetPx", host.dp(48)));
    }
    assertTrue(controlRects.length() >= 13);
    assertEquals(0, api.writes);
    return MainActivity.obj("passed", true, "actualNativeViews", true, "sourceCommit", BASE,
        "widthPx", p.w, "heightPx", p.h, "widthDp", p.widthDp(), "heightDp", p.heightDp(), "densityDpi", p.dpi,
        "fontScale", p.font / 100f, "insetsDp", new JSONArray(p.insets), "state", state,
        "cardCount", cards.length(), "alignedCardRects", true, "apiWrites", api.writes, "cards", cards, "controls", controlRects);
  }

  @Test public void renderShopCardsAtShortAspectAndLargeFont() throws Exception {
    JSONObject results = new JSONObject(), findings = new JSONObject();
    List<String> failures = new ArrayList<>();
    for (Profile p : profiles()) {
      RuntimeEnvironment.setQualifiers(p.qualifiers());
      RuntimeEnvironment.setFontScale(p.font / 100f);
      ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, p.qualifiers());
      try (var controller = flow.start()) {
        MainActivity host = controller.get();
        host.getWindow().setLayout(p.w, p.h);
        NativeEconomyThemeFixture.Api api = new NativeEconomyThemeFixture().prepare(host);
        host.me().put("snack", 7883).put("peanut", 0);
        host.me().put("items", new JSONArray().put(MainActivity.obj("id", "potion", "count", 16)));
        layout(host, p);
        for (String state : STATES) {
          String name = state + "-" + p.id;
          JSONArray problems = new JSONArray(); findings.put(name, problems);
          try {
            if (state.equals("first-page")) {
              host.menus.economy.shopFocus = "shop:tickets";
              host.menus.economy.shop();
            } else if (state.equals("blocked-selected")) {
              host.root.findViewWithTag("shop-card-shop:peanutTickets").performClick();
              assertFalse(host.menus.economy.shopActions.get("shop:peanutTickets").isEnabled());
            } else {
              controls.button(host.root.findViewWithTag("shop-inline-pages"), "›").performClick();
            }
            ready(host, p);
            capture(host, p, name);
            results.put(name, audit(host, p, state, api));
          } catch (Throwable failure) {
            problems.put(failure.toString()); failures.add(name + ": " + failure);
            results.put(name, MainActivity.obj("passed", false, "failure", failure.toString()));
            capture(host, p, name);
          }
        }
      }
    }
    Files.write(output().resolve("geometry.json"), results.toString(2).getBytes(StandardCharsets.UTF_8));
    Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
    assertEquals(51, results.length());
    assertTrue(String.join("\n", failures), failures.isEmpty());
  }

  @Test public void selectionPagingAndQuantityPreserveShopContract() throws Exception {
    RuntimeEnvironment.setQualifiers("w780dp-h319dp-land-mdpi");
    RuntimeEnvironment.setFontScale(1.3f);
    NativeEconomyThemeContractTest contract = new NativeEconomyThemeContractTest();
    try (var controller = flow.start()) {
      MainActivity host = controller.get();
      NativeEconomyThemeContractTest.Api api = contract.prepare(host);
      NativeEconomyThemeFixture fixture = new NativeEconomyThemeFixture();
      api.me.put("itemDefs", fixture.state.getJSONObject("me").getJSONArray("itemDefs"));
      api.me.put("items", new JSONArray().put(MainActivity.obj("id", "potion", "count", 16)));
      api.me.put("peanut", 0);
      String unchanged = api.me.toString();
      host.menus.economy.shop();
      host.root.findViewWithTag("shop-card-shop:peanutTickets").performClick();
      assertTrue(host.root.findViewWithTag("shop-card-shop:peanutTickets").isSelected());
      assertFalse(host.menus.economy.shopActions.get("shop:peanutTickets").isEnabled());
      assertTrue(api.writes.isEmpty()); assertNull(host.visibleDialog);
      controls.button(host.root.findViewWithTag("shop-inline-pages"), "›").performClick();
      assertNotNull(host.root.findViewWithTag("shop-card-items/buy:big"));
      controls.button(host.root.findViewWithTag("shop-inline-pages"), "‹").performClick();
      host.root.findViewWithTag("shop-card-shop:tickets").performClick();
      View rail = host.root.findViewWithTag("shop-purchase-rail");
      controls.button(rail, "＋").performClick();
      controls.button(rail, "−").performClick();
      controls.button(rail, "최대").performClick();
      controls.button(rail, "−").performClick();
      host.menus.economy.shopActions.get("shop:tickets").performClick();
      assertTrue(api.writes.isEmpty());
      String prompt = flow.labels(host.visibleDialog.getWindow().getDecorView());
      assertTrue(prompt.contains("지원서 10장 2개"));
      assertTrue(prompt.contains("새우gang 2000개"));
      contract.confirm(host);
      // AlertDialog posts its click callback behind the next ViewRoot frame.
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
      assertEquals(List.of("GET me", "POST shop"), api.requests);
      assertEquals(1, api.writes.size());
      assertEquals(2, api.writes.get(0).length());
      assertEquals("tickets", api.writes.get(0).optString("item"));
      assertEquals(2, api.writes.get(0).optInt("count"));
      assertEquals("Presentation changes never edit prices or inventory", unchanged, api.me.toString());
    }
  }
}
