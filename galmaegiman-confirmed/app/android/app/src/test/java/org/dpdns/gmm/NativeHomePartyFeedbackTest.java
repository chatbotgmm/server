package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.InputStream;
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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDisplayManager;

/** Approved HOME/PARTY/BUTTON feedback, using production Views and packaged art only.
 * No live account, network fixture, replacement art, or skipped rendering assertions.
 * Requires Android SDK/native Robolectric graphics; source inspection is not a test pass. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeHomePartyFeedbackTest {
  static final String BASELINE = "b40dc7c1ed81ba1cdaa56da88d40a7f4996c992a";
  static final String[] IDS = {"D1", "D2", "D3", "D8", "E7", "I9"};
  static final String[] SCENES = {"home", "party-story", "party-pvp", "modes", "realtime", "missing-sprite"};
  static final String UNKNOWN = "unapproved-identity-fixture";
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  final NativeRenderReviewTest review = new NativeRenderReviewTest();
  final NativeRegistrationReviewTest identities = new NativeRegistrationReviewTest();

  static final class Profile {
    final String name; final int width, height, dpi, left, top, right, bottom; final float font;
    Profile(String n, int w, int h, int d, float f, int l, int t, int r, int b) {
      name = n; width = w; height = h; dpi = d; font = f;
      left = l; top = t; right = r; bottom = b;
    }
    String qualifiers() { return "w" + Math.round(width * 160f / dpi) + "dp-h"
        + Math.round(height * 160f / dpi) + "dp-land-" + dpi + "dpi"; }
    JSONObject json() { return MainActivity.obj("name", name, "widthPx", width, "heightPx", height,
        "densityDpi", dpi, "fontScale", font, "insetsPx", box(left, top, right, bottom),
        "safeHeightDp", (height - top - bottom) * 160f / dpi); }
  }
  static final Profile[] PROFILES = {
      new Profile("phone-915x412", 915, 412, 160, 1, 0, 0, 0, 0),
      new Profile("short-780x319", 780, 319, 160, 1, 0, 0, 0, 0),
      // A controlled density/inset stress profile, not an assertion about the user's device DPI.
      new Profile("short-highdensity-2048x838", 2048, 838, 420, 1, 73, 26, 26, 26),
      new Profile("short-font130-915x374", 915, 374, 160, 1.3f, 0, 0, 0, 0)
  };

  @Before public void isolate() {
    flow.isolate(); review.scheduledFramesUseThePausedLooperClock();
    RuntimeEnvironment.getApplication().getSharedPreferences("native_realtime", 0).edit().clear().commit();
  }
  @After public void restore() {
    RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-mdpi"); RuntimeEnvironment.setFontScale(1);
  }
  void configure(Profile p) {
    RuntimeEnvironment.setQualifiers(p.qualifiers());
    ShadowDisplayManager.changeDisplay(Display.DEFAULT_DISPLAY, p.qualifiers());
    RuntimeEnvironment.setFontScale(p.font);
  }
  void layout(MainActivity h, Profile p) {
    h.getWindow().setLayout(p.width, p.height);
    WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, p.top, p.right, p.bottom))
        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(p.left, 0, 0, 0))
        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(), Insets.of(p.left, 0, 0, 0))
        .setVisible(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), true).build();
    Insets effective = GameSafeArea.read(insets);
    assertEquals(p.left, effective.left); assertEquals(p.top, effective.top);
    assertEquals(p.right, effective.right); assertEquals(p.bottom, effective.bottom);
    ViewCompat.dispatchApplyWindowInsets(h.root, insets);
    // Pin the fixture after production inset calculation; synthetic Window dispatch is not OS evidence.
    ViewCompat.setOnApplyWindowInsetsListener(h.root, (v, ignored) -> {
      h.root.setPadding(effective.left, effective.top, effective.right, effective.bottom); return ignored;
    });
    h.root.setPadding(effective.left, effective.top, effective.right, effective.bottom);
    Runnable measure = () -> {
      review.force(h.root);
      h.root.measure(View.MeasureSpec.makeMeasureSpec(p.width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(p.height, View.MeasureSpec.EXACTLY));
      h.root.layout(0, 0, p.width, p.height);
    };
    measure.run(); review.preparePreDraw(h.root, measure);
    assertEquals(p.dpi, h.getResources().getDisplayMetrics().densityDpi);
    assertEquals(p.font, h.getResources().getConfiguration().fontScale, .0001f);
  }

  JSONObject fixture() throws Exception {
    JSONObject state = identities.fixture();
    JSONArray owned = new JSONArray(), units = new JSONArray();
    for (int i = 0; i < IDS.length; i++) {
      JSONObject species = identities.species(IDS[i]);
      species.put("level", 17 + i).put("hp", 67 + i).put("maxHp", 120 + i);
      owned.put(species);
      units.put(new JSONObject(species.toString()).put("key", "o" + (i + 1))
          .put("xp", 23).put("xpNext", 140).put("skills", new JSONArray())
          .put("learn", new JSONArray()).put("evolve", new JSONArray()));
    }
    state.put("units", units).put("tutorial", MainActivity.obj("step", 9));
    state.getJSONObject("me").put("units", owned).put("nickname", "항구 수비조장")
        .put("snack", 1240).put("peanut", 80).put("credits", 30);
    return state;
  }
  static final class Api extends NativeApi {
    final JSONObject stored; final List<String> reads = new ArrayList<>(), writes = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    Result pending; boolean allowWrites;
    Api(MainActivity h, JSONObject state) { super(h); stored = state; }
    @Override String token() { return "offline-home-party-feedback"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        assertTrue("Capture must never save/spend: " + path, allowWrites);
        assertTrue("Only explicitly exercised offline writes are allowed: " + path,
            path.equals("native/team") || path.equals("battle-team") || path.equals("pvp/room/command"));
        assertNull("A second command must not bypass the pending-write lock", pending);
        writes.add(path); bodies.add(NativeRealtime.copy(body)); pending = done; return;
      }
      reads.add(path);
      if (path.equals("native/state")) done.done(stored, null);
      else if (path.equals("native/catalog")) done.done(MainActivity.obj("units", stored.optJSONObject("me").optJSONArray("units")), null);
      else if (path.equals("pvp/rank")) done.done(NativeThemeReviewTest.rank(), null);
      else if (path.equals("draw")) done.done(MainActivity.obj("wait", 3600000, "credits", 30), null);
      else if (path.equals("daily")) done.done(MainActivity.obj("attend", MainActivity.obj("today", true), "missions", new JSONArray()), null);
      else if (path.startsWith("pvp/room/")) { /* Expired/stale input may request a read; leave it pending. */ }
      else throw new AssertionError("Unexpected offline read: " + path);
    }
    void complete(JSONObject response) { Result next = pending; assertNotNull(next); pending = null; next.done(response, null); }
  }
  Api prepare(MainActivity h) throws Exception {
    h.api.close(); Api api = new Api(h, fixture()); h.api = api; h.state = api.stored;
    h.guide.enabled = false; h.motionEnabled = false; h.art.key = api.token();
    for (String id : IDS) try (InputStream in = h.getAssets().open("game-art/duel/sprites/" + id + "_s0_front.webp")) {
      assertTrue("Approved full sprite must be bundled before native loader runs: " + id, in.read() >= 0);
    }
    return api;
  }
  JSONObject room(MainActivity h) {
    return MainActivity.obj("roomId", "offline-party-room", "phaseId", "prepare-1", "phase", "prepare",
        "revision", 1, "mode", "normal", "serverNow", 1000000L, "deadline", 1060000L,
        "preparation", MainActivity.obj("selectedIds", MainActivity.arr(Arrays.asList("D1", "D2", "D3")),
            "available", h.me().optJSONArray("units"), "ready", false, "opponentReady", false));
  }
  NativeRealtime showRealtime(MainActivity h) {
    NativeRealtime rt = h.realtime; rt.resumed = false; rt.closed = false;
    rt.owner = rt.account(); rt.room = "offline-party-room"; rt.acceptRoom(room(h)); return rt;
  }
  void show(MainActivity h, Profile p, String scene) throws Exception {
    layout(h, p);
    if (scene.equals("home")) h.home();
    else if (scene.equals("modes")) h.menus.modes();
    else if (scene.equals("realtime")) showRealtime(h);
    else {
      if (scene.equals("missing-sprite")) {
        JSONObject first = h.state.getJSONArray("units").getJSONObject(0);
        first.put("id", UNKNOWN).put("image", "/duel/sprites/D1_s0_front.webp");
        // Deliberately tempting foreign image must never be treated as this identity's sprite.
      }
      h.selectionMode = scene.equals("party-pvp") ? "pvp" : "story";
      h.menus.teamDraft = null; h.team();
    }
    settle(h, p);
  }
  void settle(MainActivity h, Profile p) throws Exception {
    layout(h, p); Bitmap kickoff = render(h.root);
    kickoff.recycle();
    RegistrationArtTest.await(() -> h.art.idle() && h.registrationArt().idle());
    layout(h, p);
    Bitmap last = render(h.root); last.recycle();
  }

  static JSONObject box(float l, float t, float r, float b) { return MainActivity.obj("left", l, "top", t, "right", r, "bottom", b); }
  static JSONObject box(Rect r) { return box(r.left, r.top, r.right, r.bottom); }
  static JSONObject box(RectF r) { return box(r.left, r.top, r.right, r.bottom); }
  static void collect(View v, List<View> all) {
    all.add(v); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), all);
  }
  static List<View> all(View root) { List<View> result = new ArrayList<>(); collect(root, result); return result; }
  static Rect bounds(View v, View root) {
    int x = 0, y = 0; View current = v;
    while (current != root) {
      x += current.getLeft() + Math.round(current.getTranslationX());
      y += current.getTop() + Math.round(current.getTranslationY());
      if (!(current.getParent() instanceof View)) return new Rect();
      current = (View) current.getParent(); x -= current.getScrollX(); y -= current.getScrollY();
    }
    return new Rect(x, y, x + v.getWidth(), y + v.getHeight());
  }
  static void intersect(Rect r, Rect clip) { if (!r.intersect(clip)) r.setEmpty(); }
  static Rect visible(View v, MainActivity h) {
    Rect r = bounds(v, h.root);
    if (!v.isShown() || v.getAlpha() <= 0) return new Rect();
    RectF safe = h.safeBounds(h.root.getWidth(), h.root.getHeight()); intersect(r, new Rect((int) safe.left, (int) safe.top, (int) safe.right, (int) safe.bottom));
    for (View current = v; current != h.root && current.getParent() instanceof View; ) {
      View parent = (View) current.getParent();
      if (parent instanceof ViewGroup && ((ViewGroup) parent).getClipChildren()) {
        Rect clip = bounds(parent, h.root);
        if (((ViewGroup) parent).getClipToPadding()) {
          clip.left += parent.getPaddingLeft(); clip.top += parent.getPaddingTop();
          clip.right -= parent.getPaddingRight(); clip.bottom -= parent.getPaddingBottom();
        }
        intersect(r, clip);
      }
      current = parent;
    }
    return r;
  }
  static void check(boolean ok, String reason, JSONArray findings) { if (!ok) findings.put(reason); }
  static View required(MainActivity h, String tag) {
    View v = h.root.findViewWithTag(tag);
    if (v == null && tag.equals("team-save")) v = h.root.findViewWithTag("tutorial:team-save");
    assertNotNull("Required production action " + tag, v); return v;
  }
  static Button label(MainActivity h, String text) {
    for (View v : all(h.root)) if (v instanceof Button && text.equals(((Button) v).getText().toString())) return (Button) v;
    throw new AssertionError("Required button " + text);
  }
  static Bitmap render(View v) {
    assertTrue("Measured native view", v.getWidth() > 0 && v.getHeight() > 0);
    Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888); v.draw(new Canvas(b)); return b;
  }
  static Bitmap background(View v) {
    Drawable d = v.getBackground(); assertNotNull(d);
    Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
    Rect previous = new Rect(d.getBounds()); d.setBounds(0, 0, b.getWidth(), b.getHeight());
    d.draw(new Canvas(b)); d.setBounds(previous); return b;
  }
  Path output() throws Exception {
    Path p = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "feedback-home-party");
    Files.createDirectories(p); return p;
  }
  void write(String file, JSONObject data) throws Exception { Files.write(output().resolve(file), data.toString(2).getBytes(StandardCharsets.UTF_8)); }
  void png(Bitmap b, String file) throws Exception {
    try (OutputStream stream = Files.newOutputStream(output().resolve(file))) { assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
  }
  static boolean samePixels(Bitmap a, Bitmap b) {
    if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
    int[] x = new int[a.getWidth() * a.getHeight()], y = new int[x.length];
    a.getPixels(x, 0, a.getWidth(), 0, 0, a.getWidth(), a.getHeight());
    b.getPixels(y, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight()); return Arrays.equals(x, y);
  }
  static JSONObject tones(Bitmap b) {
    int paper = 0, navy = 0, total = 0;
    for (int y = b.getHeight() / 4; y < b.getHeight() * 3 / 4; y++)
      for (int x = b.getWidth() / 4; x < b.getWidth() * 3 / 4; x++) {
        int c = b.getPixel(x, y), r = Color.red(c), g = Color.green(c), blue = Color.blue(c);
        if (Color.alpha(c) < 200) continue;
        total++; if (r > 165 && g > 145 && blue > 100 && r >= blue) paper++;
        if (r < 100 && g < 145 && blue < 170 && blue > r * 1.15f) navy++;
      }
    return MainActivity.obj("paperFraction", paper / (double) Math.max(1, total), "navyFraction", navy / (double) Math.max(1, total), "samples", total);
  }
  static void paper(Bitmap b) { assertTrue("Default/released surface is parchment: " + tones(b), tones(b).optDouble("paperFraction") > .55); }
  static void navy(Bitmap b) { assertTrue("Only pressed surface is navy: " + tones(b), tones(b).optDouble("navyFraction") > .55); }

  JSONObject inspect(MainActivity h, Profile p, String scene, Api api, JSONArray findings) throws Exception {
    JSONArray art = new JSONArray(), controls = new JSONArray(), labels = new JSONArray(); int registration = 0, missing = 0;
    List<PartyCharacterView.Artwork> sprites = new ArrayList<>();
    for (View v : all(h.root)) {
      if (v instanceof RegistrationCardView || v instanceof RegistrationCardView.Artwork) registration++;
      if (v instanceof PartyCharacterView.Artwork) {
        PartyCharacterView.Artwork a = (PartyCharacterView.Artwork) v; sprites.add(a);
        Rect outer = bounds(a, h.root); boolean unknown = UNKNOWN.equals(a.id);
        check(outer.width() > 0 && outer.height() > 0 && outer.equals(visible(a, h)), "Sprite view clipped: " + a.id + " " + outer, findings);
        if (unknown) {
          missing++; check(a.bitmap == null, "Unknown identity used a substitute bitmap", findings);
          check(!a.status.isEmpty(), "Missing identity has no text fallback", findings);
          check(a.target.isEmpty(), "Missing sprite retained an old draw target", findings);
        } else {
          check(a.bitmap != null && a.status.isEmpty(), "Approved sprite did not load: " + a.id + " " + a.status, findings);
          if (a.bitmap != null) {
            check(!a.target.isEmpty() && new RectF(0, 0, a.getWidth(), a.getHeight()).contains(a.target), "Whole sprite is not contained: " + a.id, findings);
            check(Math.abs(a.target.width() / a.target.height() - a.bitmap.getWidth() / (float) a.bitmap.getHeight()) < .001f,
                "Sprite aspect/crop changed: " + a.id, findings);
            try (InputStream in = h.getAssets().open("game-art/duel/sprites/" + a.id + "_s0_front.webp")) {
              Bitmap source = BitmapFactory.decodeStream(in);
              try { check(source != null && samePixels(source, a.bitmap), "Sprite differs from its exact approved full asset: " + a.id, findings); }
              finally { if (source != null) source.recycle(); }
            }
          }
        }
        art.put(MainActivity.obj("id", a.id, "status", a.status, "viewPx", box(outer), "visiblePx", box(visible(a, h)),
            "targetPx", box(a.target), "bitmapWidth", a.bitmap == null ? 0 : a.bitmap.getWidth(),
            "bitmapHeight", a.bitmap == null ? 0 : a.bitmap.getHeight(), "expectedSource", unknown ? JSONObject.NULL : "game-art/duel/sprites/" + a.id + "_s0_front.webp"));
      }
      if (v instanceof Button && v.isShown()) {
        Rect r = bounds(v, h.root);
        check(r.width() >= h.dp(48) && r.height() >= h.dp(48), "Touch target below 48dp: " + v.getTag() + " " + r, findings);
        check(r.equals(visible(v, h)), "Button clipped: " + v.getTag() + " " + r, findings);
        controls.put(MainActivity.obj("tag", String.valueOf(v.getTag()), "text", ((Button) v).getText(), "boundsPx", box(r), "enabled", v.isEnabled()));
      }
      if (v instanceof TextView && !(v instanceof Button) && v.isShown() && !((TextView) v).getText().toString().isEmpty()) {
        TextView t = (TextView) v; Rect r = bounds(t, h.root);
        check(r.equals(visible(t, h)), "Live label clipped: " + t.getTag() + " " + t.getText(), findings);
        if (t.getLayout() != null) {
          check(t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom() >= t.getLayout().getHeight(), "Live label lacks vertical room: " + t.getText(), findings);
        }
        labels.put(MainActivity.obj("tag", String.valueOf(v.getTag()), "text", t.getText(), "boundsPx", box(r)));
      }
    }
    check(registration == 0, "Registration card remains in approved party/home scope", findings);
    int expected = scene.equals("home") ? 0 : 6;
    check(sprites.size() == expected, "Expected " + expected + " actual production sprites on " + scene + ", found " + sprites.size(), findings);
    check(missing == (scene.equals("missing-sprite") ? 2 : 0), "Incorrect missing-identity coverage", findings);
    for (int i = 0; i < sprites.size(); i++) for (int j = i + 1; j < sprites.size(); j++)
      check(!Rect.intersects(bounds(sprites.get(i), h.root), bounds(sprites.get(j), h.root)), "Party artwork overlaps", findings);
    if (scene.equals("home")) {
      LobbyView lobby = (LobbyView) h.root.getChildAt(0);
      check(lobby.buttons.size() == 13, "HOME action count changed beyond removing harbor", findings);
      check(h.root.findViewWithTag("lobby-harbor") == null && h.root.findViewWithTag("lobby-growth") == null, "Removed HOME action survives", findings);
      check(lobby.ready(), "Original HOME art did not load", findings);
      check(!lobby.heroBox().isEmpty() && !lobby.heroShadowBox(lobby.heroBox()).isEmpty(), "Grounded character/contact shadow missing", findings);
      for (int i = 0; i < 3; i++) check(lobby.buttons.get(9 + i).getContentDescription().toString().contains(new String[] {"1,240", "80", "30"}[i]), "Live HOME balance missing", findings);
    } else {
      String text = flow.labels(h.root);
      for (int i = 0; i < 3; i++) check(text.contains(h.state.getJSONArray("units").getJSONObject(i).getString("name")), "Live identity name missing from " + scene, findings);
      if (scene.equals("modes") || scene.equals("realtime")) for (int i = 0; i < 3; i++) {
        JSONObject u = h.state.getJSONArray("units").getJSONObject(i);
        check(text.contains("Lv" + u.getInt("level")), "Summary/preparation loses live level", findings);
        if (scene.equals("realtime")) check(text.contains(u.getInt("hp") + "/" + u.getInt("maxHp")), "Preparation loses live HP", findings);
      }
      if (scene.equals("party-story") || scene.equals("missing-sprite")) for (int i = 0; i < 3; i++) {
        JSONObject u = h.state.getJSONArray("units").getJSONObject(i);
        String slot = flow.labels(required(h, "lineup-slot-" + i));
        check(slot.contains("Lv" + u.getInt("level")) && slot.contains(u.getInt("hp") + "/" + u.getInt("maxHp")), "Story slot loses live level/HP", findings);
      }
    }
    check(api.writes.isEmpty(), "Read-only capture mutated server state", findings);
    return MainActivity.obj("profile", p.json(), "scene", scene, "artworks", art, "controls", controls,
        "liveLabels", labels, "registrationViews", registration, "apiWrites", api.writes.size());
  }

  @Test public void captureHomePartyModesAndRealtimeAcrossNativeProfiles() throws Exception {
    JSONObject findings = new JSONObject(), metrics = new JSONObject(), manifest = new JSONObject();
    JSONArray profiles = new JSONArray(), captures = new JSONArray(); List<String> failures = new ArrayList<>(); int saved = 0;
    for (Profile p : PROFILES) {
      profiles.put(p.json());
      for (String scene : SCENES) {
        String name = p.name + "-" + scene + ".png";
        captures.put(MainActivity.obj("filename", name, "profile", p.name, "scene", scene));
        findings.put(name, new JSONArray().put("Capture and checks have not completed"));
      }
    }
    manifest.put("schemaVersion", 1).put("suite", "feedback-home-party").put("baseline", BASELINE)
        .put("profiles", profiles).put("captures", captures).put("renderer", "production Android Views / native Robolectric graphics")
        .put("insetMethod", "production GameSafeArea with pinned synthetic WindowInsetsCompat; not physical-device evidence")
        .put("identitySource", "APK registration manifest names; APK approved top33 whole sprites; no substitutions")
        .put("requiredCaptureCount", PROFILES.length * SCENES.length);
    write("manifest.json", manifest); write("findings.json", findings); write("metrics.json", metrics);
    for (Profile p : PROFILES) for (String scene : SCENES) {
      configure(p); JSONArray errors = new JSONArray(); String name = p.name + "-" + scene + ".png";
      try (ActivityController<MainActivity> controller = flow.start()) {
        MainActivity h = controller.get(); Api api = prepare(h); show(h, p, scene);
        Bitmap bitmap = render(h.root);
        try { png(bitmap, name); saved++; } finally { bitmap.recycle(); }
        metrics.put(name, inspect(h, p, scene, api, errors));
      } catch (AssertionError | Exception e) { errors.put(e.toString()); }
      findings.put(name, errors); if (errors.length() > 0) failures.add(name + ": " + errors);
      write("findings.json", findings); write("metrics.json", metrics);
    }
    assertEquals(PROFILES.length * SCENES.length, saved);
    assertEquals(PROFILES.length * SCENES.length, metrics.length());
    assertTrue("HOME/PARTY render regressions:\n" + String.join("\n", failures), failures.isEmpty());
  }

  static void event(View view, int action, float x, float y) {
    long now = SystemClock.uptimeMillis(); MotionEvent e = MotionEvent.obtain(now, now, action, x, y, 0);
    try { view.dispatchTouchEvent(e); } finally { e.recycle(); }
  }
  static void down(View v) { event(v, MotionEvent.ACTION_DOWN, v.getWidth() / 2f, v.getHeight() / 2f); }
  static void up(View v) { event(v, MotionEvent.ACTION_UP, v.getWidth() / 2f, v.getHeight() / 2f); }
  static void cancel(View v) { event(v, MotionEvent.ACTION_CANCEL, v.getWidth() / 2f, v.getHeight() / 2f); }

  @Test public void actualButtonDispatchRestoresPaperAfterReleaseCancelAndSelection() throws Exception {
    configure(PROFILES[0]); JSONObject evidence = new JSONObject();
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h = controller.get(); prepare(h); h.base("버튼 상태 검증", "buttonReview");
      LinearLayout row = new LinearLayout(h); h.root.addView(row, new android.widget.FrameLayout.LayoutParams(-1, h.dp(72)));
      for (int kind = 0; kind < 3; kind++) {
        final int[] clicks = {0}; Button b = h.button("상태", () -> clicks[0]++);
        b.setBackground(kind == 2 ? ThemeArt.of(h).drawable(ThemeArt.Surface.TAB) : new GameButtonDrawable(h, kind == 0 ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
        b.setTextColor(kind == 2 ? JournalTheme.tabText() : JournalTheme.controlText(kind == 0 ? GameButtonDrawable.PAPER : GameButtonDrawable.PRIMARY));
        row.addView(b, new LinearLayout.LayoutParams(h.dp(210), h.dp(72))); layout(h, PROFILES[0]);
        String name = new String[] {"paper", "primary", "tab"}[kind]; JSONObject states = new JSONObject();
        Bitmap released = background(b);
        try {
          paper(released); png(released, "button-" + name + "-default.png"); states.put("default", tones(released));
          assertEquals(JournalTheme.INK, b.getCurrentTextColor());
          down(b); assertTrue(b.isPressed()); Bitmap pressed = background(b);
          try { navy(pressed); png(pressed, "button-" + name + "-pressed.png"); states.put("pressed", tones(pressed)); }
          finally { pressed.recycle(); }
          assertEquals(JournalTheme.WHITE, b.getCurrentTextColor());
          up(b); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
          assertFalse(b.isPressed()); assertEquals(1, clicks[0]); Bitmap after = background(b);
          try { paper(after); assertTrue("Release restores exact unpressed surface", samePixels(released, after)); png(after, "button-" + name + "-released.png"); states.put("released", tones(after)); }
          finally { after.recycle(); }
          down(b); cancel(b); assertFalse(b.isPressed()); assertEquals(1, clicks[0]); after = background(b);
          try { paper(after); assertTrue("Cancel restores exact unpressed surface", samePixels(released, after)); png(after, "button-" + name + "-cancelled.png"); states.put("cancelled", tones(after)); }
          finally { after.recycle(); }
          b.setSelected(true); after = background(b);
          try { paper(after); png(after, "button-" + name + "-selected.png"); states.put("selected", tones(after)); }
          finally { after.recycle(); }
          b.setEnabled(false); after = background(b);
          try { assertFalse("Disabled is visually distinct", samePixels(released, after)); png(after, "button-" + name + "-disabled.png"); states.put("disabled", tones(after)); }
          finally { after.recycle(); }
          down(b); up(b); assertEquals("Disabled cannot dispatch action", 1, clicks[0]);
          assertEquals(JournalTheme.MUTED, b.getCurrentTextColor());
        } finally { released.recycle(); }
        evidence.put(name, states);
      }
    } finally { write("button-states.json", evidence); }
  }

  /** Only navigation assertions use a spy; the 24 screen PNGs use unmodified MainActivity. */
  public static class RouteHost extends MainActivity {
    boolean record; final List<String> routes = new ArrayList<>();
    @Override void load(String route) { if (record) routes.add(route); else super.load(route); }
    @Override void team() { if (record) routes.add("team"); else super.team(); }
  }
  ActivityController<RouteHost> routeHost() throws Exception {
    android.app.Application app = RuntimeEnvironment.getApplication();
    ActivityInfo info = new ActivityInfo(app.getPackageManager().getActivityInfo(new ComponentName(app, MainActivity.class), 0));
    info.name = RouteHost.class.getName(); Shadows.shadowOf(app.getPackageManager()).addOrUpdateActivity(info);
    return Robolectric.buildActivity(RouteHost.class).create().start().resume().visible();
  }
  @Test public void homeCustomCanvasButtonsKeepRoutesAndCancelWithoutNavigation() throws Exception {
    configure(PROFILES[0]); JSONObject evidence = new JSONObject();
    try (ActivityController<RouteHost> controller = routeHost()) {
      RouteHost h = controller.get(); Api api = prepare(h); show(h, PROFILES[0], "home"); h.record = true;
      LobbyView lobby = (LobbyView) h.root.getChildAt(0); String before = h.state.toString();
      int[] indices = {0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12};
      String[] routes = {"expedition", "draw", "collection", "craft", "shop", "modes", "daily", "settings", "shop", "shop", "shop", "team"};
      for (int i = 0; i < indices.length; i++) {
        Button b = lobby.buttons.get(indices[i]); Rect r = bounds(b, h.root); int count = h.routes.size();
        Bitmap scene = render(h.root); Bitmap normal = Bitmap.createBitmap(scene, r.left, r.top, r.width(), r.height()); scene.recycle();
        try {
          assertTrue("HOME default contains parchment", tones(normal).optDouble("paperFraction") > .15);
          down(b); assertTrue(b.isPressed()); scene = render(h.root);
          Bitmap pressed = Bitmap.createBitmap(scene, r.left, r.top, r.width(), r.height()); scene.recycle();
          try {
            assertTrue("HOME press makes its real Canvas surface navy", tones(pressed).optDouble("navyFraction") > tones(normal).optDouble("navyFraction") + .15);
          } finally { pressed.recycle(); }
          cancel(b); assertFalse(b.isPressed()); assertEquals(count, h.routes.size());
          scene = render(h.root); Bitmap cancelled = Bitmap.createBitmap(scene, r.left, r.top, r.width(), r.height()); scene.recycle();
          try { assertTrue("HOME cancel restores exact paper pixels", samePixels(normal, cancelled)); } finally { cancelled.recycle(); }
          down(b); up(b); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
          assertEquals(routes[i], h.routes.get(h.routes.size() - 1)); assertEquals(count + 1, h.routes.size()); assertFalse(b.isPressed());
          evidence.put(indices[i] + ":" + String.valueOf(b.getTag()), MainActivity.obj("route", routes[i], "cancelDispatches", 0, "releaseDispatches", 1));
        } finally { normal.recycle(); }
      }
      assertEquals("home", h.menus.teamReturn); assertEquals(before, h.state.toString()); assertTrue(api.writes.isEmpty());
    } finally { write("home-navigation.json", evidence); }
  }

  View candidate(MainActivity h, String key) { return (View) required(h, "team-candidate-character-" + key).getParent(); }
  @Test public void partyReplacementOrderDiscardAndSaveKeepOriginalPayloadsAndBusyLock() throws Exception {
    configure(PROFILES[0]); JSONObject evidence = new JSONObject();
    for (boolean pvp : new boolean[] {false, true}) try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h); api.allowWrites = true;
      show(h, PROFILES[0], pvp ? "party-pvp" : "party-story"); String state = h.state.toString();
      // Both editors also open from modes; use that real return route to keep this test scoped.
      h.menus.teamReturn = "modes";
      List<String> initial = new ArrayList<>(h.menus.teamDraft);
      required(h, "lineup-slot-1").performClick(); candidate(h, pvp ? "D1" : "o1").performClick();
      assertEquals(Arrays.asList(initial.get(1), initial.get(0), initial.get(2)), h.menus.teamDraft);
      assertTrue(h.menus.isTeamDirty()); assertTrue(api.writes.isEmpty());
      label(h, "변경 취소").performClick(); assertEquals(initial, h.menus.teamDraft); assertFalse(h.menus.isTeamDirty());
      required(h, "team-candidates-next").performClick(); candidate(h, pvp ? "D8" : "o4").performClick();
      List<String> submitted = new ArrayList<>(h.menus.teamDraft); assertEquals(pvp ? "D8" : "o4", submitted.get(1));
      required(h, "team-save").performClick(); assertTrue(h.busy); assertNotNull(api.pending);
      int epoch = h.epoch; View page = h.page;
      candidate(h, pvp ? "E7" : "o5").performClick(); required(h, "team-save").performClick();
      assertEquals("No duplicate save", 1, api.writes.size()); assertEquals(submitted, h.menus.teamDraft);
      assertSame(page, h.page); assertEquals(epoch, h.epoch); assertEquals(state, h.state.toString());
      String path = pvp ? "battle-team" : "native/team", key = pvp ? "ids" : "keys";
      assertEquals(path, api.writes.get(0)); assertEquals(MainActivity.arr(submitted).toString(), api.bodies.get(0).getJSONArray(key).toString());
      if (pvp) api.stored.getJSONObject("me").put("battleTeam", MainActivity.arr(submitted));
      else api.stored.put("team", MainActivity.arr(submitted));
      api.complete(new JSONObject()); assertFalse(h.busy); assertNull(h.menus.teamDraft); assertEquals("modes", h.screen);
      evidence.put(pvp ? "pvp" : "story", MainActivity.obj("path", path, "payload", api.bodies.get(0), "writes", api.writes.size(), "saved", true));
    }
    write("party-save-behavior.json", evidence);
  }

  @Test public void realtimeSwapConfirmDeadlineAndStalePhaseRemainServerControlled() throws Exception {
    configure(PROFILES[0]); JSONObject evidence = new JSONObject();
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h); api.allowWrites = true;
      layout(h, PROFILES[0]); NativeRealtime rt = showRealtime(h); settle(h, PROFILES[0]);
      JSONObject shown = NativeRealtime.copy(rt.latest); String initial = shown.getJSONObject("preparation").getJSONArray("selectedIds").toString();
      required(h, "realtime-prepare-slot-1").performClick(); required(h, "realtime-prepare-candidate-0").performClick();
      assertEquals(Collections.singletonList("pvp/room/command"), api.writes);
      JSONObject payload = api.bodies.get(0).getJSONObject("command").getJSONObject("prepare");
      assertEquals("[\"D2\",\"D1\",\"D3\"]", payload.getJSONArray("team").toString()); assertFalse(payload.getBoolean("ready"));
      assertTrue(rt.writing); assertEquals("No optimistic mutation of server team", initial, rt.latest.getJSONObject("preparation").getJSONArray("selectedIds").toString());
      for (View control : rt.phaseActions) assertFalse("Pending write locks phase controls", control.isEnabled());
      required(h, "realtime-prepare-confirm").performClick(); assertEquals(1, api.writes.size());
      JSONObject response = NativeRealtime.copy(shown).put("revision", 2);
      response.getJSONObject("preparation").put("selectedIds", payload.getJSONArray("team")); api.complete(response);
      assertFalse(rt.writing); required(h, "realtime-prepare-confirm").performClick();
      assertEquals(2, api.writes.size()); assertTrue(api.bodies.get(1).getJSONObject("command").getJSONObject("prepare").getBoolean("ready"));
      response.put("revision", 3); response.getJSONObject("preparation").put("ready", true); api.complete(response);
      for (View control : rt.phaseActions) assertFalse("Ready state stays locked", control.isEnabled());
      response.put("revision", 4).put("phaseId", "prepare-2"); response.getJSONObject("preparation").put("ready", false); rt.acceptRoom(response);
      rt.choosePreparationCandidate(shown, "D1"); assertEquals("Stale phase cannot issue command", 2, api.writes.size());
      rt.latest.put("deadline", rt.serverAtReceive); rt.updatePhaseInputs(); assertFalse(rt.phaseDeadlineOpen());
      for (View control : rt.phaseActions) assertFalse("Expired deadline locks every phase input", control.isEnabled());
      rt.prepareCommand(rt.latest, rt.latest.getJSONObject("preparation").getJSONArray("selectedIds"), true);
      assertEquals("No submit at or after authoritative deadline", 2, api.writes.size());
      evidence.put("writes", new JSONArray(api.bodies)).put("pendingLock", true).put("readyLock", true).put("stalePhaseBlocked", true).put("deadlineBlocked", true);
    } finally { write("realtime-locks.json", evidence); }
  }


  @Test public void homeDragKeepsFeetAndMenusFixedAndReturnsAfterCancel() throws Exception {
    configure(PROFILES[0]);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h = controller.get(); Api api = prepare(h); show(h, PROFILES[0], "home");
      LobbyView lobby = (LobbyView) h.root.getChildAt(0);
      h.motionEnabled = true; lobby.getViewTreeObserver().dispatchOnPreDraw();
      assertTrue(h.motionResumed); assertNotNull("Real HOME motion clock", lobby.motion.clock);
      RectF original = lobby.heroBox(); List<Rect> controls = new ArrayList<>();
      for (Button b : lobby.buttons) controls.add(bounds(b, h.root));
      float x = original.centerX(), y = original.centerY();
      event(lobby, MotionEvent.ACTION_DOWN, x, y);
      // The first beyond-slop MOVE cancels the child Button as ViewGroup intercepts.
      // Continue the real pointer stream so the following MOVE reaches the parent drag handler.
      event(lobby, MotionEvent.ACTION_MOVE, x + h.dp(60), y + h.dp(30));
      event(lobby, MotionEvent.ACTION_MOVE, x + h.dp(120), y + h.dp(60));
      assertTrue("Actual touch dispatch moves the weighted pose", Math.abs(lobby.parallax.x) > 0);
      assertNotEquals("Dragging must not activate the guide tap", View.VISIBLE, lobby.tip.getVisibility());
      assertTrue(Math.abs(lobby.parallax.x) <= h.dp(4));
      assertTrue(Math.abs(lobby.parallax.y) <= h.dp(1.2f));
      Bitmap moved = render(h.root);
      try { png(moved, "home-grounded-drag.png"); } finally { moved.recycle(); }
      assertEquals("Feet stay on the same contact line", original.bottom, lobby.heroPose.bottom, .01f);
      for (int i = 0; i < lobby.buttons.size(); i++) assertEquals("Drag cannot move a menu", controls.get(i), bounds(lobby.buttons.get(i), h.root));
      event(lobby, MotionEvent.ACTION_CANCEL, x + h.dp(120), y + h.dp(60));
      assertNotNull("Cancel starts damped return", lobby.parallax.returning);
      // PAUSED Choreographer delivers vsync when the clock advances. A single large jump
      // can deliver only the animator's first frame at that new time; advance actual frames.
      float releasedX = Math.abs(lobby.parallax.x), previousX = releasedX;
      float previousY = Math.abs(lobby.parallax.y);
      boolean intermediateDecay = false;
      for (long elapsed = 0; elapsed < HomeGullDrag.RETURN_MILLIS + 200; elapsed += 16) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        float currentX = Math.abs(lobby.parallax.x), currentY = Math.abs(lobby.parallax.y);
        assertTrue("Damped return never grows or overshoots", currentX <= previousX + .001f);
        assertTrue("Vertical compression decays without overshoot", currentY <= previousY + .001f);
        intermediateDecay |= currentX > .001f && currentX < releasedX - .001f;
        previousX = currentX; previousY = currentY;
      }
      assertTrue("Actual animation frames include a partial damped return", intermediateDecay);
      assertEquals(0, lobby.parallax.x, .001f); assertEquals(0, lobby.parallax.y, .001f);
      Bitmap settled = render(h.root); settled.recycle();
      assertEquals("Feet remain planted after the return frames", original.bottom, lobby.heroPose.bottom, .01f);
      for (int i = 0; i < lobby.buttons.size(); i++)
        assertEquals("Return frames cannot move a menu", controls.get(i), bounds(lobby.buttons.get(i), h.root));
      assertNull(lobby.parallax.returning); assertEquals("home", h.screen); assertTrue(api.writes.isEmpty());
      write("home-grounded-drag.json", MainActivity.obj("feetY", original.bottom, "controlsChecked", controls.size(),
          "actualTouchDispatch", true, "cancelledReturnSettled", true, "apiWrites", api.writes.size()));
    }
  }

  @Test public void approvedSpriteBoundaryNeverFallsBackToAnotherIdentity() throws Exception {
    configure(PROFILES[0]);
    try (ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h = controller.get(); prepare(h);
      for (String id : new String[] {"D1", "D8", "E1", "E8", "I1", "I9", "T1", "T8"})
        assertEquals("/duel/sprites/" + id + "_s0_front.webp", PartyCharacterView.spritePath(MainActivity.obj("id", id)));
      for (String id : new String[] {"", "D0", "D9", "E9", "I10", "T9", "D01", "d1", "../D1", "L14", UNKNOWN})
        assertTrue("Unapproved identity must not resolve sprite: " + id, PartyCharacterView.spritePath(MainActivity.obj("id", id)).isEmpty());
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "D1", "known", false)).isEmpty());
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "D1", "image", "/images/unknown.png")).isEmpty());
      assertEquals("/thumbs/C1.jpg", PartyCharacterView.spritePath(MainActivity.obj(
          "id", "C1", "image", "/thumbs/C1.jpg", "combatDefinitionId", "D1")));
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "C1", "image", "/thumbs/D1.jpg")).isEmpty());
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "C1", "image", "/thumbs/C1.jpg", "known", false)).isEmpty());
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "C1", "combatDefinitionId", "D1")).isEmpty());
      assertTrue(PartyCharacterView.spritePath(MainActivity.obj("id", "C1", "image", "https://example.com/C1.jpg")).isEmpty());
      // Decode the actual APK-bundled common original; no server or substitute ancestor art.
      try (java.io.InputStream input = h.getAssets().open("game-art/thumbs/C1.jpg")) {
        Bitmap common = BitmapFactory.decodeStream(input);
        assertNotNull("Canonical C1 original must be bundled offline", common);
        assertTrue(common.getWidth() >= 64 && common.getHeight() >= 64);
        RectF whole = new RectF();
        PartyCharacterView.Artwork.contain(whole, 96, 72, common.getWidth(), common.getHeight());
        assertTrue(new RectF(0, 0, 96, 72).contains(whole));
        assertEquals(common.getWidth() / (float) common.getHeight(), whole.width() / whole.height(), .001f);
        common.recycle();
      }
      show(h, PROFILES[0], "missing-sprite");
      PartyCharacterView.Artwork a = (PartyCharacterView.Artwork) required(h, "lineup-portrait");
      assertEquals(UNKNOWN, a.id); assertNull(a.bitmap); assertFalse(a.status.isEmpty()); assertTrue(a.target.isEmpty());
      assertTrue("Real name remains available without an image", flow.labels(required(h, "lineup-slot-0")).contains(h.state.getJSONArray("units").getJSONObject(0).getString("name")));
    }
  }
}
