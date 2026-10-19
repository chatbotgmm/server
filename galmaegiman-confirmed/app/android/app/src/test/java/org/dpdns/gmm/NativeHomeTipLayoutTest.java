package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.Html;
import android.text.Layout;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDisplayManager;

/** Real TextView, bundled fonts/art and native Skia, with every API request intercepted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeHomeTipLayoutTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  static final class Profile {
    final int width, height, left, top, right, bottom;
    final String qualifiers;
    Profile(int w, int h, String q, int l, int t, int r, int b) {
      width = w; height = h; qualifiers = q;
      left = l; top = t; right = r; bottom = b;
    }
  }
  static final Profile[] PROFILES = {
      new Profile(915, 412, "w915dp-h412dp-land-mdpi", 0, 0, 0, 0),
      new Profile(1006, 411, "w1006dp-h411dp-land-mdpi", 32, 8, 24, 16),
      new Profile(1280, 720, "w1280dp-h720dp-land-mdpi", 0, 0, 0, 0),
      new Profile(915, 374, "w915dp-h374dp-land-mdpi", 0, 0, 0, 0),
      // Supplied screenshot ratio/pixels at a synthetic phone density; actual device DPI is unknown.
      new Profile(2048, 838, "w1024dp-h419dp-land-xhdpi", 32, 0, 32, 0)
  };

  public static class HomeHost extends MainActivity {
    String requested = "";
    @Override void load(String target) { requested = target; }
  }

  static final class OfflineApi extends NativeApi {
    final List<String> paths = new ArrayList<>();
    OfflineApi(MainActivity host) { super(host); }
    @Override String token() { return "offline-home-tip-layout"; }
    @Override void call(String path, JSONObject body, Result done) {
      assertNull("No account mutations in visual fixtures", body);
      assertTrue("No unexpected request escapes the offline fixture: " + path,
          path.equals("draw") || path.equals("daily"));
      paths.add(path);
      done.done(new JSONObject(), null);
    }
  }

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restore() {
    RuntimeEnvironment.setFontScale(1f);
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
  }

  ActivityController<HomeHost> start(boolean resume) {
    ActivityController<HomeHost> controller = Robolectric.buildActivity(HomeHost.class).create();
    HomeHost h = controller.get();
    h.api.close();
    h.motionEnabled = false;
    h.guide.enabled = false;
    h.api = new OfflineApi(h);
    h.art.key = "offline-home-tip-layout";
    h.state = MainActivity.obj("me", MainActivity.obj("nickname", "항구 수비조장", "snack", 6653,
        "peanut", 0, "credits", 217, "units", new JSONArray()),
        "team", new JSONArray().put("tip-guide"),
        "units", new JSONArray().put(MainActivity.obj("key", "tip-guide", "id", "D1",
            "name", "태초의 갈매미맨", "level", 24, "hp", 100, "maxHp", 100, "xp", 2290,
            "xpNext", 2352, "attr", "돌", "attrCode", "STONE", "role", "DEFENSE",
            "skills", new JSONArray(), "learn", new JSONArray(), "evolve", new JSONArray())),
        "chapters", new JSONArray(), "activeBattle", resume);
    controller.start().resume().visible();
    h.home();
    return controller;
  }

  void layout(HomeHost h, Profile profile) {
    ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, profile.qualifiers);
    h.getWindow().setLayout(profile.width, profile.height);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    ViewCompat.setOnApplyWindowInsetsListener(h.root, null);
    h.root.setPadding(profile.left, profile.top, profile.right, profile.bottom);
    Runnable measure = () -> {
      review.force(h.root);
      h.root.measure(View.MeasureSpec.makeMeasureSpec(profile.width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(profile.height, View.MeasureSpec.EXACTLY));
      h.root.layout(0, 0, profile.width, profile.height);
    };
    measure.run();
    review.preparePreDraw(h.root, measure);
  }

  Rect bounds(View child, LobbyView parent) {
    Rect rect = new Rect();
    child.getDrawingRect(rect);
    parent.offsetDescendantRectToMyCoords(child, rect);
    return rect;
  }

  static final class Ink {
    final Rect bounds = new Rect();
    int pixels;
  }
  Ink ink(Bitmap image) {
    Ink ink = new Ink();
    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
      if (Color.alpha(pixels[y * image.getWidth() + x]) < 8) continue;
      if (ink.pixels++ == 0) ink.bounds.set(x, y, x + 1, y + 1);
      else ink.bounds.union(x, y, x + 1, y + 1);
    }
    return ink;
  }

  /** Persist both native rasters before an assertion, so a failed oracle is inspectable. */
  void saveInkFailure(TextView text, Bitmap reference, Bitmap actual, int paintBefore,
      int alphaDifferences, int missingAlphaSamples, int extraAlphaSamples, Rect expected, Rect drawn) {
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output == null || output.isBlank()) return;
    try {
      Path directory = Path.of(output, "home-tip-ink-diagnostics");
      Files.createDirectories(directory);
      String name = "ink-" + text.getWidth() + "x" + text.getHeight() + "-"
          + Integer.toHexString(text.getText().toString().hashCode());
      for (int i = 0; i < 2; i++) {
        try (OutputStream stream = Files.newOutputStream(directory.resolve(name
            + (i == 0 ? "-reference.png" : "-actual.png")))) {
          (i == 0 ? reference : actual).compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
      }
      JSONObject details = MainActivity.obj("text", text.getText().toString(),
          "width", text.getWidth(), "height", text.getHeight(), "fontPixels", text.getTextSize(),
          "paintColorBeforeDraw", Integer.toHexString(paintBefore),
          "paintColorAfterDraw", Integer.toHexString(text.getPaint().getColor()),
          "currentTextColor", Integer.toHexString(text.getCurrentTextColor()),
          "alphaDifferences", alphaDifferences, "missingAlphaSamples", missingAlphaSamples,
          "extraAlphaSamples", extraAlphaSamples, "referenceInk", rect(new RectF(expected)),
          "actualInk", rect(new RectF(drawn)), "guard", 32,
          "textOriginX", text.getCompoundPaddingLeft(),
          "textOriginY", text.getBaseline() - text.getLayout().getLineBaseline(0),
          "gravity", text.getGravity(), "layoutHeight", text.getLayout().getHeight());
      Files.write(directory.resolve(name + ".json"), details.toString(2).getBytes(StandardCharsets.UTF_8));
    } catch (Exception error) { throw new AssertionError("Cannot retain native ink failure evidence", error); }
  }

  /** Compare identical paint state, keeping the Layout reference outside the TextView clip. */
  Rect nativeInk(TextView text) { return nativeInk(text, true); }

  Rect nativeInk(TextView text, boolean retainFailure) {
    Layout layout = text.getLayout();
    assertNotNull(layout);
    assertEquals("Advice must not scroll horizontally", 0, text.getScrollX());
    assertEquals("Advice must not scroll vertically", 0, text.getScrollY());
    int guard = 32;
    int width = text.getWidth() + guard * 2;
    int height = Math.max(text.getHeight(), layout.getHeight()
        + text.getCompoundPaddingTop() + text.getCompoundPaddingBottom()) + guard * 2;
    Bitmap unclipped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      int paintBefore = text.getPaint().getColor();
      Drawable background = text.getBackground();
      text.setBackground(null);
      try {
        Canvas visible = new Canvas(actual);
        visible.translate(guard, guard);
        // TextView.onDraw initializes the shared TextPaint's current color and drawable state.
        // Drawing Layout first compares an uninitialized paint against a different native raster.
        text.draw(visible);
      } finally { text.setBackground(background); }
      assertSame("Drawing must not replace the measured native Layout", layout, text.getLayout());
      Canvas full = new Canvas(unclipped);
      // getBaseline includes the actual extended padding and vertical-gravity offset. Do not
      // assume TOP gravity or change the production view's positioning to suit the oracle.
      full.translate(guard + text.getCompoundPaddingLeft(),
          guard + text.getBaseline() - layout.getLineBaseline(0));
      layout.draw(full);
      Ink allInk = ink(unclipped), drawnInk = ink(actual);
      int[] referencePixels = new int[width * height], actualPixels = new int[width * height];
      unclipped.getPixels(referencePixels, 0, width, 0, 0, width, height);
      actual.getPixels(actualPixels, 0, width, 0, 0, width, height);
      int alphaDifferences = 0, missingAlphaSamples = 0, extraAlphaSamples = 0;
      for (int i = 0; i < referencePixels.length; i++) {
        int expected = Color.alpha(referencePixels[i]), drawn = Color.alpha(actualPixels[i]);
        if (expected != drawn) alphaDifferences++;
        if (expected > drawn) missingAlphaSamples++;
        if (drawn > expected) extraAlphaSamples++;
      }
      Rect result = new Rect(allInk.bounds);
      result.offset(-guard, -guard);
      boolean inReadingInset = result.left >= text.getCompoundPaddingLeft()
          && result.right <= text.getWidth() - text.getCompoundPaddingRight()
          && result.top >= text.getCompoundPaddingTop()
          && result.bottom <= text.getHeight() - text.getCompoundPaddingBottom();
      if (retainFailure && (alphaDifferences != 0 || !inReadingInset || allInk.pixels <= 40))
        saveInkFailure(text, unclipped, actual, paintBefore, alphaDifferences,
            missingAlphaSamples, extraAlphaSamples, allInk.bounds, drawnInk.bounds);
      assertTrue("Native Korean glyphs really rendered", allInk.pixels > 40);
      // Exact per-coordinate alpha equality, including samples below the bounds threshold:
      // no count-only cancellation, antialias tolerance, or extra ink can conceal lost pixels.
      assertEquals("Native TextView retains every alpha sample of the unbounded Layout",
          0, alphaDifferences);
      assertEquals("Native TextView does not clip any rendered glyph pixels", allInk.pixels, drawnInk.pixels);
      assertEquals("Rendered ink matches the unbounded native Layout", allInk.bounds, drawnInk.bounds);
      assertTrue("Every native glyph stays within its reading inset: " + result, inReadingInset);
      return result;
    } finally { unclipped.recycle(); actual.recycle(); }
  }

  Rect nativeBubble(TextView tip) {
    int guard = 24;
    Bitmap bitmap = Bitmap.createBitmap(tip.getWidth() + guard * 2,
        tip.getHeight() + guard * 2, Bitmap.Config.ARGB_8888);
    try {
      Canvas canvas = new Canvas(bitmap);
      canvas.translate(guard, guard);
      tip.draw(canvas);
      Ink painted = ink(bitmap);
      assertTrue("Actual native paper bubble rendered", painted.pixels > 100);
      painted.bounds.offset(tip.getLeft() - guard, tip.getTop() - guard);
      return painted.bounds;
    } finally { bitmap.recycle(); }
  }

  JSONArray rect(RectF value) {
    return new JSONArray().put((Object) value.left).put((Object) value.top)
        .put((Object) value.right).put((Object) value.bottom);
  }

  JSONObject fitConstraints(HomeHost h, LobbyView l, float scale) throws Exception {
    RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
    JSONArray spaces = new JSONArray();
    TextView probe = h.text("", 13);
    probe.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
    probe.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, l.tip.getTextSize());
    probe.setTypeface(h.prose);
    probe.setMinHeight(h.dp(48));
    probe.setPadding(l.tip.getPaddingLeft(), l.tip.getPaddingTop(),
        l.tip.getPaddingRight(), l.tip.getPaddingBottom());
    probe.setText(l.tipCopy);
    for (int clearance : new int[] {h.dp(8), h.dp(2)})
      for (Rect slot : HomeTipLayout.freeSpaces(safe, l.tipExclusions(safe), clearance, h.dp(8))) {
      JSONArray measurements = new JSONArray();
      for (int requested : new int[] {h.dp(300), h.dp(420)}) {
        int width = Math.min(requested, slot.width());
        if (width <= 0) continue;
        probe.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        measurements.put(MainActivity.obj("width", width, "fullNativeTextHeight", probe.getMeasuredHeight(),
            "availableHeight", slot.height(), "lines", probe.getLineCount()));
      }
      spaces.put(MainActivity.obj("bounds", rect(new RectF(slot)), "clearancePixels", clearance,
          "screenEdgeInsetPixels", h.dp(8), "measurements", measurements));
    }
    JSONObject constraints = MainActivity.obj("width", l.getWidth(), "height", l.getHeight(),
        "fontScale", scale, "fontPixels", l.tip.getTextSize(), "safe", rect(safe),
        "resume", l.findViewWithTag("lobby-resume") != null, "tip", l.tipCopy.toString(),
        "chosenPage", rect(new RectF(l.tipFrame)), "consumedCharacters", l.tipNextPageStart,
        "totalCharacters", l.tipCopy.length(), "freeSpaces", spaces);
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output != null && !output.isBlank()) {
      Path directory = Path.of(output);
      Files.createDirectories(directory);
      Files.write(directory.resolve("home-advice-fit-failure-" + l.getWidth() + "x" + l.getHeight()
          + "-font" + Math.round(scale * 100) + ".json"),
          constraints.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    return constraints;
  }

  JSONObject inspect(HomeHost h, LobbyView l, float scale) throws Exception {
    TextView tip = l.tip;
    assertEquals(View.VISIBLE, tip.getVisibility());
    if (l.tipPaged) fail("Complete native advice does not fit this fixture; measured constraints: "
        + fitConstraints(h, l, scale).toString());
    assertTrue("Body font was not reduced to force a fit", tip.getTextSize() >= h.dp(13) * scale - 1);
    assertEquals(tip.getText().length(), tip.getLayout().getLineEnd(tip.getLineCount() - 1));
    for (int line = 0; line < tip.getLineCount(); line++)
      assertEquals("No ellipsis hides advice", 0, tip.getLayout().getEllipsisCount(line));
    RectF safe = h.safeBounds(l.getWidth(), l.getHeight());
    Rect bubble = bounds(tip, l);
    assertEquals("Placement is the actually laid-out native TextView", l.tipFrame, bubble);
    assertTrue("A manually tappable advice bubble retains a 48dp hit area",
        tip.isClickable() && bubble.width() >= h.dp(48) && bubble.height() >= h.dp(48));
    assertTrue("Bubble stays inside real window insets", safe.contains(new RectF(bubble)));
    RectF readingSafe = new RectF(safe);
    readingSafe.inset(h.dp(8), h.dp(8));
    assertTrue("Compact control clearance preserves the 8dp screen-edge inset",
        readingSafe.contains(new RectF(bubble)));
    Rect glyphs = nativeInk(tip);
    glyphs.offset(bubble.left, bubble.top);
    Rect paper = nativeBubble(tip);
    assertTrue(safe.contains(new RectF(paper)));
    assertTrue(safe.contains(new RectF(glyphs)));
    ArrayList<RectF> exclusions = new ArrayList<>();
    exclusions.add(l.tipHeroExclusion());
    // Independently use laid-out controls, not only the production solver's input rectangles.
    for (View button : l.buttons) exclusions.add(new RectF(bounds(button, l)));
    assertNull("Removed HOME growth surface is not an advice obstacle", l.findViewWithTag("lobby-growth"));
    assertNull("Removed harbor shortcut is absent", l.findViewWithTag("lobby-harbor"));
    View resume = l.findViewWithTag("lobby-resume");
    if (resume != null) exclusions.add(new RectF(bounds(resume, l)));
    JSONArray excluded = new JSONArray();
    float minimumClearance = Float.POSITIVE_INFINITY;
    for (RectF obstacle : exclusions) {
      assertFalse("Bubble covers hero/header/menu: " + obstacle,
          RectF.intersects(new RectF(bubble), obstacle));
      assertFalse("Actual paper pixels cover an exclusion: " + obstacle,
          RectF.intersects(new RectF(paper), obstacle));
      assertFalse("Actual glyph pixels cover an exclusion: " + obstacle,
          RectF.intersects(new RectF(glyphs), obstacle));
      float clearance = Math.max(Math.max(bubble.left - obstacle.right, obstacle.left - bubble.right),
          Math.max(bubble.top - obstacle.bottom, obstacle.top - bubble.bottom));
      minimumClearance = Math.min(minimumClearance, clearance);
      assertTrue("Even compact placement keeps at least 2dp clear of every exclusion",
          clearance >= h.dp(2));
      excluded.put(rect(obstacle));
    }
    assertNull("Advice never opens a modal", h.visibleDialog);
    assertEquals(6653, h.me().optInt("snack"));
    assertEquals(0, h.me().optInt("peanut"));
    assertEquals(217, h.me().optInt("credits"));
    return MainActivity.obj("bubble", rect(new RectF(bubble)), "nativePaper", rect(new RectF(paper)),
        "nativeInk", rect(new RectF(glyphs)), "safe", rect(safe), "exclusions", excluded,
        "fontPixels", tip.getTextSize(), "lines", tip.getLineCount(),
        "verticalPaddingPixels", tip.getPaddingTop(), "minimumTouchPixels", h.dp(48),
        "minimumMeasuredClearancePixels", minimumClearance, "screenEdgeInsetPixels", h.dp(8),
        "clippedInkPixels", 0);
  }

  void save(HomeHost h, Profile p, String name, JSONObject evidence) throws Exception {
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output == null || output.isBlank()) return;
    Path directory = Path.of(output);
    Files.createDirectories(directory);
    Bitmap image = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    try {
      h.root.draw(new Canvas(image));
      try (OutputStream stream = Files.newOutputStream(directory.resolve(name + ".png"))) {
        image.compress(Bitmap.CompressFormat.PNG, 100, stream);
      }
    } finally { image.recycle(); }
    Files.write(directory.resolve(name + "-metrics.json"),
        evidence.toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void everyTipKeepsNativeInkAndPaperClearAtPhoneInsetsTabletAndLargeFont() throws Exception {
    JSONArray fixtures = new JSONArray();
    for (Profile profile : PROFILES) for (float scale : new float[] {1f, 1.3f}) {
      RuntimeEnvironment.setQualifiers(profile.qualifiers);
      RuntimeEnvironment.setFontScale(scale);
      for (boolean resume : new boolean[] {false, true}) {
        try (ActivityController<HomeHost> controller = start(resume)) {
          HomeHost h = controller.get();
          layout(h, profile);
          LobbyView l = (LobbyView) h.root.getChildAt(0);
          // Load and settle the exact bundled native artwork before checking/capturing it.
          Bitmap warm = Bitmap.createBitmap(profile.width, profile.height, Bitmap.Config.ARGB_8888);
          h.root.draw(new Canvas(warm)); warm.recycle();
          RegistrationArtTest.await(h.art::idle);
          assertTrue("Bundled home scene is ready", l.homeArt.ready());
          assertNotNull(l.homeArt.hero());
          JSONArray allTips = new JSONArray();
          String[] tips = l.gullTips();
          for (int i = 0; i < tips.length; i++) {
            l.tipCopy = Html.fromHtml(tips[i], Html.FROM_HTML_MODE_LEGACY);
            l.tipPageStart = 0;
            l.tip.setText(l.tipCopy);
            l.tip.setVisibility(View.VISIBLE);
            l.tip.scrollTo(0, 0);
            layout(h, profile);
            JSONObject evidence = inspect(h, l, scale);
            allTips.put(evidence);
            if (i == 6) {
              String name = "home-advice-" + profile.width + "x" + profile.height
                  + "-font" + Math.round(scale * 100) + (resume ? "-resume" : "");
              save(h, profile, name, evidence);
            }
          }
          assertEquals(8, allTips.length());
          fixtures.put(MainActivity.obj("width", profile.width, "height", profile.height,
              "qualifiers", profile.qualifiers, "fontScale", scale, "resume", resume,
              "tips", allTips, "pagedTips", 0, "scrollableTips", 0));
        }
      }
    }
    String output = System.getProperty("gmm.nativeReviewOutput");
    if (output != null && !output.isBlank()) Files.write(Path.of(output, "home-advice-fixtures.json"),
        MainActivity.obj("fixtures", fixtures, "tipChecks", 160, "screenshotCount", 20)
            .toString(2).getBytes(StandardCharsets.UTF_8));
  }

  @Test public void manualCharacterTipsStillCycleAndButtonsKeepTheirRoutes() throws Exception {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      layout(h, PROFILES[0]);
      LobbyView l = (LobbyView) h.root.getChildAt(0);
      assertEquals(View.GONE, l.tip.getVisibility());
      l.gull.performClick();
      layout(h, PROFILES[0]);
      int first = l.tipAt;
      assertEquals(View.VISIBLE, l.tip.getVisibility());
      l.tip.performClick();
      layout(h, PROFILES[0]);
      assertNotEquals("A native text tap manually chooses a different tip", first, l.tipAt);
      assertNull(h.visibleDialog);
      // A stale preceding page offset must not skip the beginning of a newly selected tip.
      l.tipNextPageStart = 7;
      l.gull.performClick();
      assertEquals("New tip invalidates any preceding page offset immediately", 0, l.tipNextPageStart);
      l.tip.performClick(); // Deliberately no layout between character and text taps.
      assertEquals("Rapid taps never start partway through new advice", 0, l.tipPageStart);
      assertEquals(l.tipCopy.toString(), l.tip.getText().toString());
      layout(h, PROFILES[0]);
      l.tipCopy = "긴 조언도 차근차근 한 장씩 읽어 봐. ".repeat(24);
      l.tipPageStart = 0;
      l.tipNextPageStart = 0;
      l.tip.setText(l.tipCopy);
      layout(h, PROFILES[0]);
      assertTrue("The rapid-page fixture really requires measured pages", l.tipPaged);
      int nextPage = l.tipNextPageStart, sameTip = l.tipAt;
      String sameCopy = l.tipCopy.toString();
      l.tip.performClick();
      l.tip.performClick(); // The next page still has not gone through layout.
      assertEquals("Two fast taps advance only to the pending unread page", nextPage, l.tipPageStart);
      assertEquals("Pending measurement is guarded", -1, l.tipNextPageStart);
      assertEquals("Fast page taps do not replace the current advice", sameTip, l.tipAt);
      assertEquals(sameCopy, l.tipCopy.toString());
      layout(h, PROFILES[0]);
      assertTrue("Layout restores a valid subsequent page boundary", l.tipNextPageStart > l.tipPageStart);
      MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0);
      l.dispatchTouchEvent(down); down.recycle();
      assertEquals("Existing outside-tap dismissal remains", View.GONE, l.tip.getVisibility());
      String[] targets = {"expedition", "draw", "collection", "craft", "shop", "modes",
          "daily", null, "settings", "shop", "shop", "shop", null};
      assertEquals("Every remaining HOME control is represented", targets.length, l.buttons.size());
      assertNull("Retired harbor route is absent", l.findViewWithTag("lobby-harbor"));
      for (int i = 0; i < targets.length; i++) if (targets[i] != null) {
        h.requested = "";
        l.buttons.get(i).performClick();
        assertEquals(targets[i], h.requested);
      }
    }
  }

  void assertTipPage(TextView tip, HomeTipLayout.Result result, RectF safe, RectF hero, float size) {
    assertNull("No native scrolling movement method", tip.getMovementMethod());
    assertTrue(safe.contains(new RectF(result.bounds)));
    assertFalse(RectF.intersects(new RectF(result.bounds), hero));
    assertTrue("Every tappable page, including the last, stays at least 48dp tall",
        result.bounds.height() >= tip.getMinHeight());
    assertEquals(size, tip.getTextSize(), .001f);
    assertEquals(result.bounds.width(), tip.getMeasuredWidth());
    assertEquals(result.bounds.height(), tip.getMeasuredHeight());
    tip.layout(result.bounds.left, result.bounds.top, result.bounds.right, result.bounds.bottom);
    nativeInk(tip);
  }

  @Test public void constrainedAlternativeIsMeasuredBoundedAndNeverCoversTheHero() {
    try (ActivityController<HomeHost> controller = start(false)) {
      HomeHost h = controller.get();
      // A stale shared paint must be initialized by TextView before the reference is drawn.
      TextView oracle = h.text("네이티브 글자는 잘림 없이 보여야 해요.\n두 번째 줄도 끝까지 읽어요.", 17);
      oracle.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      oracle.setPadding(12, 8, 12, 8);
      oracle.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      oracle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      oracle.layout(0, 0, oracle.getMeasuredWidth(), oracle.getMeasuredHeight() + 32);
      oracle.getPaint().setColor(Color.TRANSPARENT); // Deliberately different from currentTextColor.
      nativeInk(oracle);
      assertEquals(oracle.getCurrentTextColor(), oracle.getPaint().getColor());
      // Negative control: retain the same full Layout but visibly clip its native raster.
      oracle.layout(0, 0, oracle.getWidth(), oracle.getCompoundPaddingTop()
          + oracle.getCompoundPaddingBottom() + 1);
      AssertionError clipping = null;
      try { nativeInk(oracle, false); } catch (AssertionError expected) { clipping = expected; }
      assertNotNull("The strict oracle must reject a genuinely clipped TextView", clipping);
      assertTrue("The negative control must fail pixel equality, not an unrelated precondition",
          clipping.getMessage().contains("retains every alpha sample"));

      TextView tip = h.text("끼룩! 조언을 눌러서 다음 항로를 보자.", 17);
      tip.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));
      tip.setPadding(12, 8, 12, 8);
      tip.setMinHeight(h.dp(48));
      float size = tip.getTextSize();
      RectF roomy = new RectF(0, 0, 600, 360), centeredHero = new RectF(140, 160, 280, 330);
      HomeTipLayout.Result preferred = HomeTipLayout.measure(tip, roomy, centeredHero,
          Arrays.asList(centeredHero), 8, 220, 260, 120);
      boolean aboveAlsoFits = false;
      for (Rect space : HomeTipLayout.freeSpaces(roomy, Arrays.asList(centeredHero), 8))
        aboveAlsoFits |= space.bottom <= centeredHero.top - 8
            && space.width() >= preferred.bounds.width() && space.height() >= preferred.bounds.height();
      assertTrue("Fixture really offers a second valid region above the hero", aboveAlsoFits);
      assertFalse(preferred.paged);
      assertTrue("Choose the right side when both right and above can fit complete copy",
          preferred.bounds.left >= centeredHero.right + 8);
      assertTipPage(tip, preferred, roomy, centeredHero, size);

      tip.setText("끼룩! 빈 공간에서 편하게 읽어 봐.");
      RectF narrow = new RectF(0, 0, 360, 320), rightHero = new RectF(80, 150, 352, 312);
      HomeTipLayout.Result alternate = HomeTipLayout.measure(tip, narrow, rightHero,
          Arrays.asList(rightHero), 8, 240, 280, 120);
      assertFalse("An above-hero alternative holds complete copy without paging", alternate.paged);
      assertEquals(tip.length(), alternate.consumed);
      assertTrue("No right-side space: use the measured free region above the hero",
          alternate.bounds.bottom <= rightHero.top - 8);
      assertTipPage(tip, alternate, narrow, rightHero, size);

      // Reproduce the observed short-phone shortage without assuming a font's line height.
      // The full copy fits only after reducing control clearance, never the text size.
      String compactCopy = "전투를 시작하면 증강체 3개 중 하나를 골라. 마음에 안 들면 칸마다 한 번씩 다시 뽑을 수 있어.";
      tip.setText(compactCopy);
      tip.setPadding(12, 4, 12, 4);
      tip.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int completeHeight = tip.getMeasuredHeight();
      RectF compactSafe = new RectF(0, 0, 500, 320), compactHero = new RectF(0, 0, 120, 320);
      RectF upper = new RectF(0, 0, 500, 90);
      RectF lower = new RectF(0, 90 + completeHeight + 4, 500, 320);
      List<RectF> compactExclusions = Arrays.asList(compactHero, upper, lower);
      for (Rect space : HomeTipLayout.freeSpaces(compactSafe, compactExclusions, 8))
        assertTrue("Preferred clearance genuinely leaves too little measured height",
            space.height() < completeHeight);
      tip.setPadding(12, 8, 12, 8);
      HomeTipLayout.Result compact = HomeTipLayout.measure(tip, compactSafe, compactHero,
          compactExclusions, 8, 300, 300, 120);
      assertFalse("Compact clearance fits the entire original advice without pages", compact.paged);
      assertEquals(compactCopy, tip.getText().toString());
      assertEquals(compactCopy.length(), compact.consumed);
      assertTrue("Original 8px screen-edge inset remains",
          new RectF(8, 8, 492, 312).contains(new RectF(compact.bounds)));
      for (RectF obstacle : compactExclusions) {
        RectF separated = new RectF(obstacle);
        separated.inset(-2, -2);
        assertFalse("Compact placement still clears every control/hero by 2px",
            RectF.intersects(new RectF(compact.bounds), separated));
      }
      assertTipPage(tip, compact, compactSafe, compactHero, size);

      String longCopy = "긴 조언은 글씨 크기를 유지하고 빈 공간 안에서 읽어요. ".repeat(24);
      RectF safe = new RectF(0, 0, 320, 180), hero = new RectF(0, 0, 110, 180);
      int offset = 0, pages = 0;
      StringBuilder displayed = new StringBuilder();
      while (offset < longCopy.length()) {
        tip.setText(longCopy.substring(offset));
        HomeTipLayout.Result result = HomeTipLayout.measure(tip, safe, hero,
            Arrays.asList(hero), 8, 250, 300, 100);
        if (pages == 0) assertTrue("Long synthetic copy requires tap pages", result.paged);
        assertTrue("Every page consumes real text", result.consumed > 0);
        assertEquals(result.consumed, tip.length());
        assertTipPage(tip, result, safe, hero, size);
        displayed.append(tip.getText());
        offset += result.consumed;
        pages++;
        assertTrue("Pagination must make bounded progress", pages <= longCopy.length());
      }
      assertTrue(pages > 1);
      assertEquals("Every character is preserved, including the last 48dp page", longCopy, displayed.toString());
    }
  }
}
