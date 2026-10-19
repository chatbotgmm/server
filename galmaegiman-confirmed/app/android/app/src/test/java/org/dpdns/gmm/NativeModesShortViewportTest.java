package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import org.robolectric.shadows.ShadowDisplayManager;

/** 실제 modes 화면의 짧은 안전 영역 회귀 검사. 계정·서버·대체 이미지 없이 APK View를 그린다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h374dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeModesShortViewportTest {
  static final String BASELINE = "8b55b4bde86c4ea8e1a9f4efb643d905564ffe89";
  static final String[] STATES = {"ready", "loading", "error", "empty"};
  static final String[] ACTIONS = {"modes-story-team", "modes-story", "modes-adventure",
      "modes-normal", "modes-ranked", "modes-standings", "modes-practice", "modes-pvp-team"};
  static final String[] SUMMARIES = {"modes-story-summary", "modes-pvp-summary"};
  static final class Profile {
    final String name;
    final int width, height, dpi, left, top, right, bottom;
    final float fontScale;
    final boolean densityHypothesis;
    Profile(String n, int w, int h, int d, float f, int l, int t, int r, int b, boolean hypothesis) {
      name = n; width = w; height = h; dpi = d; fontScale = f;
      left = l; top = t; right = r; bottom = b; densityHypothesis = hypothesis;
    }
    String qualifiers() {
      // Qualifiers are dp; measure specs, screenshot size and WindowInsets are physical px.
      return "w" + Math.round(width * 160f / dpi) + "dp-h"
          + Math.round(height * 160f / dpi) + "dp-land-" + dpi + "dpi";
    }
    JSONObject json() {
      return MainActivity.obj("name", name, "widthPx", width, "heightPx", height,
          "densityDpi", dpi, "fontScale", fontScale, "insetsPx", edges(left, top, right, bottom),
          "safeWidthDp", (width - left - right) * 160f / dpi,
          "safeHeightDp", (height - top - bottom) * 160f / dpi,
          "densityInterpretation", densityHypothesis ? "hypothesis; source screenshot DPI unknown" : "test profile");
    }
  }
  static final Profile[] PROFILES = {
    new Profile("short-915x374-mdpi", 915, 374, 160, 1, 0, 0, 0, 0, false),
    new Profile("short-780x319-mdpi", 780, 319, 160, 1, 0, 0, 0, 0, false),
    new Profile("short-915x374-font130", 915, 374, 160, 1.3f, 0, 0, 0, 0, false),
    new Profile("short-780x319-font130", 780, 319, 160, 1.3f, 0, 0, 0, 0, false),
    // The observed outer gutter is modeled separately; neither DPI is asserted to be the user's DPI.
    new Profile("device-ratio-2048x838-360dpi", 2048, 838, 360, 1, 73, 26, 26, 26, true),
    new Profile("device-ratio-2048x838-420dpi", 2048, 838, 420, 1, 73, 26, 26, 26, true),
    new Profile("insets-915x374-xhdpi", 1950, 844, 320, 1, 72, 32, 48, 64, false),
    new Profile("insets-780x319-hdpi", 1260, 551, 240, 1, 54, 24, 36, 48, false)
  };

  final NativeMenusThemeReviewTest menusReview = new NativeMenusThemeReviewTest();
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();

  @Before public void isolate() {
    flow.isolate();
    review.scheduledFramesUseThePausedLooperClock();
  }
  @After public void restoreDisplay() {
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi");
    RuntimeEnvironment.setFontScale(1);
  }

  static JSONObject edges(int l, int t, int r, int b) {
    return MainActivity.obj("left", l, "top", t, "right", r, "bottom", b);
  }
  static JSONObject rect(Rect r) { return edges(r.left, r.top, r.right, r.bottom); }
  static boolean positive(Rect r) { return r.width() > 0 && r.height() > 0; }
  static void check(boolean ok, String message, JSONArray findings) {
    if (!ok) findings.put(message);
  }
  static void intersect(Rect target, Rect clip) {
    // Rect.intersect leaves its receiver unchanged when disjoint. Never mistake that for visibility.
    if (!target.intersect(clip)) target.setEmpty();
  }
  static Rect bounds(View view, View root) {
    int x = 0, y = 0;
    for (View current = view; current != root; ) {
      x += current.getLeft() + Math.round(current.getTranslationX());
      y += current.getTop() + Math.round(current.getTranslationY());
      if (!(current.getParent() instanceof View)) return new Rect();
      View parent = (View) current.getParent();
      x -= parent.getScrollX(); y -= parent.getScrollY(); current = parent;
    }
    return new Rect(x, y, x + view.getWidth(), y + view.getHeight());
  }
  static Rect safe(MainActivity host) {
    return new Rect(host.root.getPaddingLeft(), host.root.getPaddingTop(),
        host.root.getWidth() - host.root.getPaddingRight(),
        host.root.getHeight() - host.root.getPaddingBottom());
  }
  static Rect visibleIntersection(View view, MainActivity host) {
    Rect visible = bounds(view, host.root);
    if (!view.isShown() || view.getAlpha() <= 0) return new Rect();
    intersect(visible, safe(host));
    for (View current = view; current != host.root && current.getParent() instanceof View; ) {
      View parent = (View) current.getParent();
      if (parent instanceof ViewGroup && ((ViewGroup) parent).getClipChildren()) {
        Rect clip = bounds(parent, host.root);
        if (((ViewGroup) parent).getClipToPadding()) {
          clip.left += parent.getPaddingLeft(); clip.top += parent.getPaddingTop();
          clip.right -= parent.getPaddingRight(); clip.bottom -= parent.getPaddingBottom();
        }
        intersect(visible, clip);
      }
      Rect explicit = parent.getClipBounds();
      if (explicit != null) {
        Rect origin = bounds(parent, host.root); explicit.offset(origin.left, origin.top);
        intersect(visible, explicit);
      }
      current = parent;
    }
    return visible;
  }
  static void collect(View view, List<View> found) {
    found.add(view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), found);
    }
  }
  static boolean descendant(View child, View ancestor) {
    for (ViewParent p = child.getParent(); p instanceof View; p = p.getParent())
      if (p == ancestor) return true;
    return false;
  }
  static View action(MainActivity host, String tag) {
    View view = host.root.findViewWithTag(tag);
    if (view == null && tag.equals("modes-story")) view = host.root.findViewWithTag("tutorial:story");
    return view;
  }
  static String key(View view, List<View> all) {
    String tag = String.valueOf(view.getTag());
    if (tag.equals("tutorial:story")) return "modes-story";
    if (!tag.equals("null")) return tag;
    return view.getClass().getSimpleName() + "-" + all.indexOf(view);
  }

  void configure(Profile p) {
    RuntimeEnvironment.setQualifiers(p.qualifiers());
    ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, p.qualifiers());
    RuntimeEnvironment.setFontScale(p.fontScale);
  }
  void layout(MainActivity host, Profile p) {
    host.getWindow().setLayout(p.width, p.height);
    // The first call exercises the installed production listener. Later calls keep using the
    // production GameSafeArea calculation, then explicitly install its newly computed fixture padding.
    WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, p.top, p.right, p.bottom))
        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(p.left, 0, 0, 0))
        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(), Insets.of(p.left, 0, 0, 0))
        .setVisible(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), true)
        .build();
    Insets effective = GameSafeArea.read(insets);
    assertEquals(p.left, effective.left); assertEquals(p.top, effective.top);
    assertEquals(p.right, effective.right); assertEquals(p.bottom, effective.bottom);
    ViewCompat.dispatchApplyWindowInsets(host.root, insets);
    // Window's synthetic bars must not overwrite the explicitly supplied fixture during async decoding.
    ViewCompat.setOnApplyWindowInsetsListener(host.root, (view, ignored) -> {
      host.root.setPadding(effective.left, effective.top, effective.right, effective.bottom);
      return ignored;
    });
    host.root.setPadding(effective.left, effective.top, effective.right, effective.bottom);
    Runnable measure = () -> {
      review.force(host.root);
      host.root.measure(View.MeasureSpec.makeMeasureSpec(p.width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(p.height, View.MeasureSpec.EXACTLY));
      host.root.layout(0, 0, p.width, p.height);
    };
    measure.run(); review.preparePreDraw(host.root, measure);
    assertEquals(p.dpi, host.getResources().getDisplayMetrics().densityDpi);
    assertEquals(p.dpi / 160f, host.getResources().getDisplayMetrics().density, .0001f);
  }

  NativeMenusThemeReviewTest.FixtureApi prepare(MainActivity host, String state) throws Exception {
    NativeMenusThemeReviewTest.FixtureApi api = menusReview.prepare(host,
        state.equals("ready") ? "modes" : "modes-" + state);
    // Production fallback drawing is not acceptable proof of the requested unchanged art.
    for (ThemeArt.Surface surface : new ThemeArt.Surface[] {
        ThemeArt.Surface.NAVY, ThemeArt.Surface.PAPER, ThemeArt.Surface.HEADER, ThemeArt.Surface.NAV})
      assertNotNull("Required real bundled frame: " + surface, ThemeArt.of(host).image(ThemeArt.file(surface)));
    // An empty party must also receive team=false; the shared ready rank fixture otherwise says true.
    if (state.equals("empty")) api.rankState = "loading";
    return api;
  }
  void show(MainActivity host, Profile p) throws Exception {
    layout(host, p);
    host.menus.modes();
    layout(host, p);
    Bitmap first = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    try { host.root.draw(new Canvas(first)); } finally { first.recycle(); }
    // A loading rank is deliberately pending; only real asynchronous artwork must settle.
    RegistrationArtTest.await(() -> host.art.idle() && host.registrationArt().idle());
    layout(host, p);
  }

  /** Render only the actual production drawable to locate its gold edge, never a mock surface. */
  static final class FramePixels {
    final View owner;
    final Rect outer;
    final int width, height;
    final int[] pixels;
    final boolean[] edgeMask;
    FramePixels(View view, MainActivity host) {
      owner = view; outer = bounds(view, host.root); width = view.getWidth(); height = view.getHeight();
      pixels = new int[Math.max(0, width * height)];
      edgeMask = new boolean[pixels.length];
      if (width <= 0 || height <= 0) return;
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Drawable drawable = view.getBackground(); Rect previous = new Rect(drawable.getBounds());
      try {
        drawable.setBounds(0, 0, width, height); drawable.draw(new Canvas(bitmap));
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
      } finally { drawable.setBounds(previous); bitmap.recycle(); }
      boolean[] warm = new boolean[pixels.length];
      for (int i = 0; i < pixels.length; i++) warm[i] = brass(pixels[i]);
      int reach = Math.max(1, host.dp(1)), seedBand = Math.max(2, host.dp(3));
      int[] queue = new int[pixels.length]; int head = 0, tail = 0;
      // Only brass connected to the outside border is decoration. Isolated gold scratches in
      // the original blue material are intentionally excluded, including in HEADER/NAVY assets.
      for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
        int at = y * width + x;
        if (warm[at] && (x < seedBand || y < seedBand || x >= width - seedBand || y >= height - seedBand)) {
          edgeMask[at] = true; queue[tail++] = at;
        }
      }
      while (head < tail) {
        int at = queue[head++], x = at % width, y = at / width;
        // A 1dp bridge tolerates the native rasterizer's dark/antialiased seams in the frame.
        for (int yy = Math.max(0, y - reach); yy <= Math.min(height - 1, y + reach); yy++)
          for (int xx = Math.max(0, x - reach); xx <= Math.min(width - 1, x + reach); xx++) {
            int next = yy * width + xx;
            if (warm[next] && !edgeMask[next]) { edgeMask[next] = true; queue[tail++] = next; }
          }
      }
    }
    static boolean brass(int color) {
      if (Color.alpha(color) < 96) return false;
      int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
      // Brass has a strong warm chroma. The actual button-secondary asset's cream paper also
      // has R/B around 1.35, so that threshold would incorrectly mask most of its reading area.
      return r >= 80 && g >= 45 && r > b * 1.75f && g > b * 1.15f
          && r >= g * .95f && r <= g * 2.1f;
    }
    boolean gold(int x, int y) { return edgeMask[y * width + x]; }
    Rect reading(Rect glyph) {
      if (width <= 0 || height <= 0) return new Rect();
      int l = 0, t = 0, r = width, b = height;
      int y0 = Math.max(0, glyph.top - outer.top), y1 = Math.min(height, glyph.bottom - outer.top);
      int x0 = Math.max(0, glyph.left - outer.left), x1 = Math.min(width, glyph.right - outer.left);
      // Inspect the edge zones at the actual glyph rows/columns, including corner and crest ornaments.
      // Unlike the whole nine-slice cap, this does not prohibit readable material inside those slices.
      for (int y = y0; y < y1; y++) {
        for (int x = 0; x < width / 3; x++) if (gold(x, y)) l = Math.max(l, x + 1);
        for (int x = width * 2 / 3; x < width; x++) if (gold(x, y)) r = Math.min(r, x);
      }
      for (int x = x0; x < x1; x++) {
        for (int y = 0; y < height / 3; y++) if (gold(x, y)) t = Math.max(t, y + 1);
        for (int y = height * 2 / 3; y < height; y++) if (gold(x, y)) b = Math.min(b, y);
      }
      return new Rect(outer.left + l + 1, outer.top + t + 1, outer.left + r - 1, outer.top + b - 1);
    }
  }
  static boolean framed(View v) {
    return v.getBackground() instanceof ThemeArt.SurfaceDrawable
        || v.getBackground() instanceof GameButtonDrawable;
  }
  static View frame(TextView view, View root) {
    for (View current = view; ; ) {
      if (framed(current)) return current;
      if (current == root || !(current.getParent() instanceof View)) return null;
      current = (View) current.getParent();
    }
  }
  static Rect glyphBounds(TextView label, View root) {
    Layout layout = label.getLayout(); Rect union = new Rect();
    if (layout == null) return union;
    Rect own = bounds(label, root);
    int originY = own.top + label.getBaseline() - layout.getLineBaseline(0) - label.getScrollY();
    String text = layout.getText().toString();
    for (int line = 0; line < layout.getLineCount(); line++) {
      int start = layout.getLineStart(line), end = layout.getLineEnd(line);
      String shown = text.substring(start, end);
      int count = layout.getEllipsisCount(line), cut = layout.getEllipsisStart(line);
      if (count > 0 && cut + count <= shown.length())
        shown = shown.substring(0, cut) + "\u2026" + shown.substring(cut + count);
      // Layout.Ellipsizer keeps hidden character positions as zero-width FEFF placeholders.
      // Measure only the visible string, including its actual ellipsis, never the hidden raw suffix.
      shown = shown.replace("\uFEFF", "");
      int last = shown.length();
      while (last > 0 && Character.isWhitespace(shown.charAt(last - 1))) last--;
      if (last == 0) continue;
      Rect ink = new Rect(); label.getPaint().getTextBounds(shown, 0, last, ink);
      ink.offset(own.left + label.getCompoundPaddingLeft()
          + Math.round(layout.getLineLeft(line)) - label.getScrollX(), originY + layout.getLineBaseline(line));
      if (!ink.isEmpty()) union.union(ink);
    }
    return union;
  }

  JSONObject inspect(MainActivity host, Profile p, String state,
      NativeMenusThemeReviewTest.FixtureApi api, JSONArray problems) throws Exception {
    List<View> all = new ArrayList<>(); collect(host.root, all);
    JSONObject actions = new JSONObject(), texts = new JSONObject(), containers = new JSONObject();
    IdentityHashMap<View, FramePixels> frames = new IdentityHashMap<>();
    List<View> controls = new ArrayList<>();
    int scrolls = 0, frameChecks = 0, portraits = 0, overlapChecks = 0;
    boolean realImages = true;
    int minTarget = (int) Math.ceil(48 * host.getResources().getDisplayMetrics().density);
    check(host.root.getWidth() == p.width && host.root.getHeight() == p.height, "Wrong measured pixel viewport", problems);
    check(host.getResources().getConfiguration().fontScale == p.fontScale, "Font scale fixture not applied", problems);
    check(api.writes.isEmpty(), "Display review wrote to API", problems);
    check(host.root.findViewWithTag("theme-scene-background") != null, "Production scene absent", problems);
    for (String name : ACTIONS) {
      View v = action(host, name);
      check(v instanceof Button, "Required production Button missing: " + name, problems);
    }
    View adventure = action(host, "modes-adventure");
    check(adventure != null && !adventure.isEnabled(), "Adventure must remain visible and disabled", problems);
    for (String name : SUMMARIES) {
      View v = host.root.findViewWithTag(name);
      check(v != null, "Required inline summary missing: " + name, problems);
      if (v != null) {
        Rect box = bounds(v, host.root), visible = visibleIntersection(v, host);
        check(positive(box) && positive(visible) && box.equals(visible), "Summary clipped: " + name, problems);
        containers.put(name, MainActivity.obj("boundsPx", rect(box), "visibleBoundsPx", rect(visible)));
      }
    }
    for (View v : all) {
      if (v instanceof ScrollView || v instanceof HorizontalScrollView
          || v.getClass().getSimpleName().contains("NestedScrollView")) scrolls++;
      if (v instanceof PartyCharacterView.Artwork && v.getVisibility() == View.VISIBLE) {
        PartyCharacterView.Artwork picture = (PartyCharacterView.Artwork) v;
        boolean decoded = picture.bitmap != null && !picture.bitmap.isRecycled()
            && picture.bitmap.getWidth() >= 64;
        realImages &= decoded;
        check(decoded, "Unloaded production character: " + key(v, all), problems);
        check(picture.path.equals("/duel/sprites/" + picture.id + "_s0_front.webp"),
            "Character identity/source changed: " + key(v, all), problems);
        Rect imageBox = bounds(v, host.root), imageVisible = visibleIntersection(v, host);
        check(positive(imageBox) && imageBox.equals(imageVisible),
            "Production character View clipped: " + key(v, all), problems);
        check(!picture.target.isEmpty() && new RectF(0, 0, v.getWidth(), v.getHeight()).contains(picture.target),
            "Whole character art clipped: " + key(v, all), problems);
        if (decoded) check(Math.abs(picture.target.width() / picture.target.height()
            - picture.bitmap.getWidth() / (float) picture.bitmap.getHeight()) < .01f,
            "Character art aspect ratio changed: " + key(v, all), problems);
        portraits++;
      }
      if (v instanceof ImageView && v.getVisibility() == View.VISIBLE) {
        ImageView picture = (ImageView) v;
        boolean decoded = picture.getDrawable() instanceof BitmapDrawable
            && ((BitmapDrawable) picture.getDrawable()).getBitmap() != null
            && ((BitmapDrawable) picture.getDrawable()).getBitmap().getWidth() >= 64;
        realImages &= decoded;
        check(decoded, "Unloaded/replaced production portrait: " + key(v, all), problems);
        Rect imageBox = bounds(v, host.root), imageVisible = visibleIntersection(v, host);
        check(positive(imageBox) && positive(imageVisible) && imageBox.equals(imageVisible),
            "Production portrait clipped: " + key(v, all), problems);
        portraits++;
      }
      boolean control = v instanceof Button || v.isClickable();
      boolean label = v instanceof TextView && ((TextView) v).length() > 0
          && v.getVisibility() == View.VISIBLE;
      if (!control && !label) continue;
      String id = key(v, all); Rect box = bounds(v, host.root), visible = visibleIntersection(v, host);
      check(v.isShown(), "Hidden required content: " + id, problems);
      check(positive(box), "Zero/negative bounds: " + id + " " + box, problems);
      check(positive(visible), "No positive visible intersection: " + id, problems);
      check(box.equals(visible), "Ancestor/safe-area clipping: " + id + " " + box + " vs " + visible, problems);
      if (control) {
        check(box.width() >= minTarget && box.height() >= minTarget, "48dp touch target: " + id + " " + box, problems);
        controls.add(v);
        actions.put(id, MainActivity.obj("actualTag", String.valueOf(v.getTag()), "boundsPx", rect(box),
            "visibleBoundsPx", rect(visible), "minTouchTargetPx", minTarget, "enabled", v.isEnabled()));
      }
      if (!label) continue;
      TextView tv = (TextView) v; Layout layout = tv.getLayout();
      check(layout != null, "Missing native text Layout: " + id, problems);
      if (layout == null) continue;
      int ellipsis = 0;
      for (int line = 0; line < layout.getLineCount(); line++) {
        ellipsis += layout.getEllipsisCount(line);
        check(layout.getLineWidth(line) <= layout.getWidth() + .5f, "Horizontal Layout overflow: " + id, problems);
      }
      int end = layout.getLineCount() == 0 ? 0 : layout.getLineEnd(layout.getLineCount() - 1);
      int contentHeight = tv.getHeight() - tv.getCompoundPaddingTop() - tv.getCompoundPaddingBottom();
      View summary = null;
      for (String name : SUMMARIES) {
        View candidate = host.root.findViewWithTag(name);
        if (candidate != null && descendant(tv, candidate)) summary = candidate;
      }
      boolean emptyNotice = tv.getText().toString().contains("아직 편성한");
      boolean ellipsisAllowed = !control && !emptyNotice
          && (summary != null || (id.equals("modes-rank-status") && !state.equals("error") && !state.equals("loading")));
      String description = tv.getContentDescription() == null ? "" : tv.getContentDescription().toString();
      check(ellipsis == 0 || ellipsisAllowed, "Action/error/notice text was ellipsized: " + id, problems);
      if (ellipsis > 0) check(description.contains(tv.getText().toString()),
          "Shortened read-only summary lost its complete accessible text: " + id, problems);
      check(end == tv.length(), "Text ends before the full label: " + id, problems);
      check(contentHeight >= layout.getHeight(), "Vertical Layout clipping: " + id, problems);
      check(tv.getAutoSizeTextType() == TextView.AUTO_SIZE_TEXT_TYPE_NONE, "Auto-shrunk text: " + id, problems);
      check(Math.abs(tv.getTextScaleX() - 1) < .0001, "Horizontally squeezed font: " + id, problems);
      Rect ink = glyphBounds(tv, host.root);
      check(positive(ink) && box.contains(ink) && visible.contains(ink), "Glyph clipping: " + id + " " + ink, problems);
      View parentFrame = frame(tv, host.root);
      Rect reading = new Rect(box), parentBounds = new Rect(box);
      if (parentFrame != null) {
        FramePixels fp = frames.get(parentFrame);
        if (fp == null) { fp = new FramePixels(parentFrame, host); frames.put(parentFrame, fp); }
        reading = fp.reading(ink); parentBounds = fp.outer; frameChecks++;
        check(positive(reading) && reading.contains(ink), "Glyph touches decorative frame: " + id
            + " glyph=" + ink + " reading=" + reading, problems);
      }
      float expectedSp = -1;
      if (Arrays.asList(ACTIONS).contains(id)) {
        expectedSp = host.uiText(16);
        check(tv.getTypeface().equals(host.font), "Action no longer uses the bundled h.font: " + id, problems);
      } else if (id.equals("modes-rank-status")) {
        expectedSp = host.uiText(14);
        check(tv.getTypeface().equals(host.prose), "Rank no longer uses bundled prose", problems);
      } else if (id.equals("modes-rank-required-team")) {
        expectedSp = host.uiText(14);
        check(tv.getTypeface().equals(host.prose), "Required-team notice lost bundled prose", problems);
      } else if (id.equals("modes-story-heading") || id.equals("modes-pvp-heading")) {
        expectedSp = host.uiText(22);
        check(tv.getTypeface().equals(host.headline), "Heading lost bundled headline font", problems);
      } else if (summary instanceof ViewGroup) {
        boolean caption = ((ViewGroup) summary).getChildAt(0) == tv;
        expectedSp = host.uiText(caption ? 15 : emptyNotice ? 14 : 13);
        check(tv.getTypeface().equals(caption ? host.headline : host.prose), "Summary font was substituted: " + id, problems);
      }
      float expectedPx = expectedSp < 0 ? tv.getTextSize() : TypedValue.applyDimension(
          TypedValue.COMPLEX_UNIT_SP, expectedSp, host.getResources().getDisplayMetrics());
      check(Math.abs(expectedPx - tv.getTextSize()) < .1f, "Action/rank font size shrank: " + id, problems);
      JSONObject evidence = MainActivity.obj("actualTag", String.valueOf(tv.getTag()), "text", tv.getText().toString(),
          "boundsPx", rect(box), "visibleBoundsPx", rect(visible), "glyphBoundsPx", rect(ink),
          "parentFrameBoundsPx", rect(parentBounds), "readingBoundsPx", rect(reading),
          "readingInsetsPx", edges(reading.left - parentBounds.left, reading.top - parentBounds.top,
              parentBounds.right - reading.right, parentBounds.bottom - reading.bottom),
          "decorativeFrameChecked", parentFrame != null,
          "frameKind", parentFrame == null ? "none" : parentFrame.getBackground().getClass().getSimpleName(),
          "layoutHeightPx", layout.getHeight(), "contentHeightPx", contentHeight,
          "textLength", tv.length(), "laidOutTextEnd", end, "ellipsisCount", ellipsis,
          "ellipsisAllowed", ellipsisAllowed, "fullContentDescription", description,
          "textSizePx", tv.getTextSize(), "expectedTextSizePx", expectedPx,
          "autoSizeTextType", tv.getAutoSizeTextType(), "textScaleX", tv.getTextScaleX());
      texts.put(id, evidence);
      if (control) actions.getJSONObject(id).put("textSizePx", tv.getTextSize());
    }
    for (int i = 0; i < controls.size(); i++) for (int j = i + 1; j < controls.size(); j++) {
      View a = controls.get(i), b = controls.get(j);
      if (descendant(a, b) || descendant(b, a)) continue;
      check(!Rect.intersects(bounds(a, host.root), bounds(b, host.root)),
          "Independent actions overlap: " + key(a, all) + "/" + key(b, all), problems);
      overlapChecks++;
    }
    check(scrolls == 0, "Modes must fit without a scroll container", problems);
    check(texts.has("modes-rank-status"), "Missing rank status Layout", problems);
    for (String tag : ACTIONS) check(texts.has(tag), "Missing measured action label: " + tag, problems);
    check(frameChecks > 0, "Decorative frame checks did not run", problems);
    check(state.equals("empty") || portraits >= 6, "Real team portraits are missing", problems);
    String rankText = texts.has("modes-rank-status") ? texts.getJSONObject("modes-rank-status").getString("text") : "";
    check(state.equals("loading") ? rankText.contains("불러오는")
        : state.equals("error") ? rankError(rankText) : rankText.contains("승리 +"),
        "Rank state was not actually rendered: " + state, problems);
    if (state.equals("empty")) {
      check(flow.labels(host.root).contains("아직 편성한 갈매미가 없어요."), "Empty party notice missing", problems);
      TextView required = host.root.findViewWithTag("modes-rank-required-team");
      check(required != null && required.isShown() && texts.has("modes-rank-required-team"),
          "Missing-party rank response must show its complete required-team notice", problems);
    }
    check(host.menus.pendingFetches == (state.equals("loading") ? 1 : 0), "Rank pending counter changed", problems);
    return MainActivity.obj("profile", p.name, "state", state, "screenWidthPx", host.root.getWidth(),
        "screenHeightPx", host.root.getHeight(), "densityDpi", host.getResources().getDisplayMetrics().densityDpi,
        "fontScale", host.getResources().getConfiguration().fontScale,
        "safeInsetsPx", edges(host.root.getPaddingLeft(), host.root.getPaddingTop(), host.root.getPaddingRight(), host.root.getPaddingBottom()),
        "safeBoundsPx", rect(safe(host)), "actualNativeViews", true, "apiWrites", api.writes.size(),
        "fixtureReads", new JSONArray(api.reads), "actions", actions, "texts", texts, "containers", containers,
        "scrollContainers", scrolls, "decorativeFrameChecks", frameChecks, "actionOverlapChecks", overlapChecks,
        "loadedPortraits", portraits, "realBundledImages", realImages, "pendingFetches", host.menus.pendingFetches,
        "densityInterpretation", p.densityHypothesis ? "hypothesis; source screenshot DPI unknown" : "test profile",
        "visualReview", "pending PNG inspection", "findings", problems);
  }

  static boolean rankError(String text) { return text.contains("불러오지 못") || text.contains("순위 조회 실패"); }

  Path output() throws Exception {
    Path directory = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feedback-stage1-modes");
    Files.createDirectories(directory); return directory;
  }
  void capture(MainActivity host, Profile p, String filename) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(bitmap));
      Set<Integer> colors = new HashSet<>();
      for (int y = 0; y < p.height; y += 7) for (int x = 0; x < p.width; x += 7) colors.add(bitmap.getPixel(x, y));
      assertTrue("Capture must contain real production art/text", colors.size() > 64);
      try (OutputStream stream = Files.newOutputStream(output().resolve(filename))) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    } finally { bitmap.recycle(); }
  }
  void write(String filename, JSONObject value) throws Exception {
    Files.write(output().resolve(filename), value.toString(2).getBytes(StandardCharsets.UTF_8));
  }
  JSONObject manifest() {
    JSONArray profiles = new JSONArray(), captures = new JSONArray();
    for (Profile p : PROFILES) {
      profiles.put(p.json());
      for (String state : STATES) captures.put(MainActivity.obj("filename", p.name + "-" + state + ".png", "profile", p.name, "state", state));
    }
    return MainActivity.obj("schemaVersion", 1, "suite", "feedback-stage1-modes", "baseline", BASELINE,
        "reference", MainActivity.obj("filename", "1000004520.jpg", "widthPx", 2048, "heightPx", 838,
            "densityDpi", "unknown", "outerGutterInterpretation", "73/26/26/26 px is a fixture hypothesis, not measured OS insets"),
        "profiles", profiles, "states", new JSONArray(Arrays.asList(STATES)), "captures", captures,
        "requiredActions", new JSONArray(Arrays.asList(ACTIONS)), "requiredContainers", new JSONArray(Arrays.asList(SUMMARIES)),
        "frameMeasurement", "production Drawable pixels; edge-connected brass mask scanned at actual glyph rows/columns",
        "insetDelivery", "synthetic WindowInsetsCompat and production GameSafeArea; effective padding is pinned against synthetic Window updates; not an OS lifecycle simulation",
        "generatedBy", "NativeModesShortViewportTest.captureModesAcrossShortSafeAreas");
  }

  @Test public void captureModesAcrossShortSafeAreas() throws Exception {
    JSONObject findings = new JSONObject(), metrics = new JSONObject();
    List<String> failures = new ArrayList<>(); int captured = 0;
    for (Profile p : PROFILES) for (String state : STATES)
      findings.put(p.name + "-" + state + ".png", new JSONArray().put("Capture has not completed"));
    write("manifest.json", manifest()); write("findings.json", findings); write("metrics.json", metrics);
    try {
      for (Profile p : PROFILES) for (String state : STATES) {
        configure(p); String filename = p.name + "-" + state + ".png"; JSONArray problems = new JSONArray();
        try (ActivityController<MainActivity> controller = flow.start()) {
          MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = prepare(host, state);
          show(host, p);
          if (state.equals("empty")) {
            assertNotNull(api.pendingRank);
            api.pendingRank.done(NativeThemeReviewTest.rank().put("team", false), null);
            layout(host, p);
          }
          // Preserve the screen even when the following geometry checks fail on the old implementation.
          capture(host, p, filename); captured++;
          metrics.put(filename, inspect(host, p, state, api, problems));
        } catch (Throwable failure) { problems.put(failure.toString()); }
        findings.put(filename, problems);
        if (problems.length() > 0) failures.add(filename + ": " + problems);
        write("findings.json", findings); write("metrics.json", metrics);
      }
    } finally {
      write("findings.json", findings); write("metrics.json", metrics); write("manifest.json", manifest());
    }
    assertEquals(32, captured); assertEquals(32, findings.length()); assertEquals(32, metrics.length());
    assertTrue("Short viewport regression:\n" + String.join("\n", failures), failures.isEmpty());
  }

  /** Routing spies are used only here; every proof PNG above uses an unmodified MainActivity. */
  public static class RouteHost extends MainActivity {
    boolean recordRoutes;
    final List<String> routes = new ArrayList<>();
    @Override void load(String target) { if (recordRoutes) routes.add(target); else super.load(target); }
    @Override void startFight(String mode, String stage) {
      if (recordRoutes) routes.add("fight:" + mode); else super.startFight(mode, stage);
    }
    @Override void practice() { if (recordRoutes) routes.add("practice"); else super.practice(); }
  }
  @Test public void shortModeButtonsKeepTheirRoutesAndRankConfirmation() throws Exception {
    configure(PROFILES[1]);
    // Preserve the real manifest theme/configuration for this test-only routing spy.
    // https://robolectric.org/javadoc/4.14/org/robolectric/shadows/ShadowPackageManager.html
    android.app.Application app = RuntimeEnvironment.getApplication();
    ActivityInfo info = new ActivityInfo(app.getPackageManager().getActivityInfo(new ComponentName(app, MainActivity.class), 0));
    info.name = RouteHost.class.getName();
    Shadows.shadowOf(app.getPackageManager()).addOrUpdateActivity(info);
    try (ActivityController<RouteHost> controller = Robolectric.buildActivity(RouteHost.class).create().start().resume().visible()) {
      RouteHost host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = prepare(host, "ready");
      show(host, PROFILES[1]); host.recordRoutes = true;
      String original = host.state.toString();
      action(host, "modes-story").performClick(); assertEquals("story", host.routes.get(host.routes.size() - 1));
      action(host, "modes-story-team").performClick(); assertEquals("team", host.routes.get(host.routes.size() - 1));
      assertEquals("story", host.selectionMode); assertEquals("modes", host.menus.teamReturn); assertNull(host.menus.teamDraft);
      action(host, "modes-pvp-team").performClick(); assertEquals("team", host.routes.get(host.routes.size() - 1));
      assertEquals("pvp", host.selectionMode); assertEquals("modes", host.menus.teamReturn); assertNull(host.menus.teamDraft);
      action(host, "modes-standings").performClick(); assertEquals("pvp", host.routes.get(host.routes.size() - 1));
      action(host, "modes-practice").performClick(); assertEquals("practice", host.routes.get(host.routes.size() - 1));
      action(host, "modes-normal").performClick(); assertEquals("fight:normal", host.routes.get(host.routes.size() - 1));
      int count = host.routes.size(); action(host, "modes-ranked").performClick();
      assertEquals(count, host.routes.size()); assertNotNull(host.visibleDialog);
      android.app.AlertDialog cancelled = host.visibleDialog;
      assertTrue(cancelled.isShowing());
      cancelled.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
      RegistrationArtTest.await(() -> !cancelled.isShowing());
      assertFalse("Cancellation dismisses the actual confirmation window", cancelled.isShowing());
      assertEquals("Cancelling ranked confirmation cannot enter a match", count, host.routes.size());
      action(host, "modes-ranked").performClick();
      android.app.AlertDialog accepted = host.visibleDialog;
      assertNotSame("A second tap opens a new confirmation", cancelled, accepted);
      assertTrue(accepted.isShowing());
      accepted.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
      RegistrationArtTest.await(() -> !accepted.isShowing() && host.routes.size() == count + 1);
      assertFalse("Approval dismisses the actual confirmation window", accepted.isShowing());
      assertEquals("Approval starts exactly one match", count + 1, host.routes.size());
      assertEquals("fight:rank", host.routes.get(host.routes.size() - 1));
      assertFalse(action(host, "modes-adventure").isEnabled());
      assertEquals(original, host.state.toString()); assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void rankPendingCompletionUpdatesOnlyItsOwnEpoch() throws Exception {
    configure(PROFILES[1]);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = prepare(host, "loading");
      show(host, PROFILES[1]);
      assertEquals(1, host.menus.pendingFetches); assertNotNull(api.pendingRank);
      TextView rank = host.root.findViewWithTag("modes-rank-status"); int epoch = host.epoch; View page = host.page;
      api.pendingRank.done(NativeThemeReviewTest.rank(), null); layout(host, PROFILES[1]);
      assertTrue(rank.getText().toString().contains("골드")); assertEquals(0, host.menus.pendingFetches);
      assertSame(page, host.page); assertEquals(epoch, host.epoch);
      host.menus.modes(); NativeApi.Result old = api.pendingRank;
      host.menus.modes(); NativeApi.Result current = api.pendingRank;
      TextView newestRank = host.root.findViewWithTag("modes-rank-status"); String pending = newestRank.getText().toString();
      old.done(NativeThemeReviewTest.rank(), null);
      assertEquals("Earlier modes instance must not repaint the newer modes instance", pending, newestRank.getText().toString());
      assertEquals(1, host.menus.pendingFetches);
      current.done(null, "오프라인 순위 실패 fixture");
      assertTrue(rankError(newestRank.getText().toString())); assertEquals(0, host.menus.pendingFetches);
      host.menus.modes(); NativeApi.Result stale = api.pendingRank;
      TextView abandonedRank = host.root.findViewWithTag("modes-rank-status"); String abandoned = abandonedRank.getText().toString();
      host.menus.library.items(); View newerPage = host.page; int newerEpoch = host.epoch;
      stale.done(NativeThemeReviewTest.rank(), null);
      assertEquals("items", host.screen); assertSame(newerPage, host.page); assertEquals(newerEpoch, host.epoch);
      assertEquals(abandoned, abandonedRank.getText().toString());
      assertNull(host.root.findViewWithTag("modes-rank-status")); assertEquals(0, host.menus.pendingFetches);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void rankCompletionAndBusyModeClicksKeepPendingWriteEpoch() throws Exception {
    configure(PROFILES[1]);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = prepare(host, "loading");
      show(host, PROFILES[1]); api.deferHealingWrite = true; int[] completed = {0};
      host.request("native/heal", MainActivity.obj("key", "o1", "item", "potion"), (result, error) -> completed[0]++);
      assertNotNull(api.pendingWrite); assertTrue(host.busy);
      int epoch = host.epoch; View page = host.page; String before = host.state.toString();
      for (String name : ACTIONS) action(host, name).performClick();
      assertEquals(epoch, host.epoch); assertSame(page, host.page); assertEquals("modes", host.screen);
      api.pendingRank.done(NativeThemeReviewTest.rank(), null);
      assertTrue("Rank completion cannot release an unrelated write", host.busy);
      assertEquals(epoch, host.epoch); assertSame(page, host.page); assertEquals(0, completed[0]);
      api.pendingWrite.done(new JSONObject(), null);
      assertEquals(1, completed[0]); assertFalse(host.busy); assertEquals(0, host.menus.pendingFetches);
      assertEquals(before, host.state.toString()); assertEquals(Collections.singletonList("native/heal"), api.writes);
    }
  }

  @Test public void shortWideShortAndInsetReflowPreserveViewsRoutesAndPendingEpoch() throws Exception {
    configure(PROFILES[1]);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity host = controller.get(); NativeMenusThemeReviewTest.FixtureApi api = prepare(host, "loading");
      show(host, PROFILES[1]);
      Map<String, View> controls = new LinkedHashMap<>();
      for (String name : ACTIONS) { controls.put(name, action(host, name)); assertNotNull(controls.get(name)); }
      View rank = host.root.findViewWithTag("modes-rank-status"), page = host.page;
      int epoch = host.epoch, reads = api.reads.size(); NativeApi.Result pending = api.pendingRank;
      String state = host.state.toString();
      Profile wide = new Profile("reflow-wide", 780, 720, 160, 1, 0, 0, 0, 0, false);
      Profile inset = new Profile("reflow-insets", 780, 720, 160, 1, 24, 12, 16, 420, false);
      Profile[] transitions = {wide, PROFILES[1], wide, inset};
      for (int i = 0; i < transitions.length; i++) {
        layout(host, transitions[i]);
        for (String name : ACTIONS) assertSame("Reflow must reuse " + name, controls.get(name), action(host, name));
        assertSame(rank, host.root.findViewWithTag("modes-rank-status"));
        assertSame(page, host.page); assertEquals("modes", host.screen); assertEquals(epoch, host.epoch);
        assertEquals(reads, api.reads.size()); assertEquals(1, host.menus.pendingFetches); assertSame(pending, api.pendingRank);
        assertEquals(state, host.state.toString()); assertFalse(host.busy); assertTrue(api.writes.isEmpty());
        LinearLayout footer = (LinearLayout) action(host, "modes-story-team").getParent();
        assertEquals("Actual available height must switch layout without reloading",
            i % 2 == 0 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL, footer.getOrientation());
      }
      pending.done(NativeThemeReviewTest.rank(), null); layout(host, inset);
      assertTrue(((TextView) rank).getText().toString().contains("골드"));
      assertEquals(0, host.menus.pendingFetches); assertEquals(epoch, host.epoch); assertSame(page, host.page);
    }
  }
}
