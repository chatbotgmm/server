package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
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

/** 실제 네이티브 트레이·조작 상태를 오프라인 서버 계약으로 검토한다. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRecruitmentCounterReviewTest {
  static final String[] STATES = {"low", "mid", "high", "cooldown", "insufficient", "maximum", "unlimited"};
  final NativeEconomyThemeReviewTest review = new NativeEconomyThemeReviewTest();

  @Before public void isolate() { review.isolate(); }
  @After public void restoreScale() { RuntimeEnvironment.setFontScale(1); }

  NativeEconomyThemeFixture.Api prepare(MainActivity host, JSONObject response) throws Exception {
    NativeEconomyThemeFixture.Api record = new NativeEconomyThemeFixture().prepare(host);
    host.me().put("credits", response.optInt("credits"));
    host.api = new NativeApi(host) {
      @Override String token() { return "offline-recruitment-counter"; }
      @Override void call(String path, JSONObject body, Result done) {
        record.calls.add(path);
        if (body != null) { record.writes++; throw new AssertionError("Unexpected mutation " + path); }
        assertEquals("draw", path);
        record.responses.put(path, response);
        done.done(response, null);
      }
    };
    return record;
  }

  Path output() throws Exception {
    Path path = Path.of(System.getProperty("gmm.nativeReviewOutput", "build/native-review"), "recruitment-counter");
    Files.createDirectories(path);
    return path;
  }

  void capture(MainActivity host, String name, int[] size) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
    try {
      host.root.draw(new Canvas(bitmap));
      try (OutputStream stream = Files.newOutputStream(output().resolve(name + ".png"))) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    } finally { bitmap.recycle(); }
  }

  static final int[][] V3_SIZES = {{800,360,100}, {640,320,100}, {800,360,130}, {960,400,200}, {1280,720,150}};

  JSONArray coordinates(Rect r) { return new JSONArray(Arrays.asList(r.left, r.top, r.right, r.bottom)); }
  String revision() throws Exception {
    Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
    String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    assertEquals("Capture needs a real checkout revision", 0, process.waitFor());
    assertTrue(value.matches("[0-9a-f]{40}")); return value;
  }
  String digest(Path path) throws Exception {
    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    StringBuilder value = new StringBuilder(); for (byte b : hash) value.append(String.format(Locale.ROOT, "%02x", b & 255)); return value.toString();
  }
  String assetDigest(MainActivity host, String path) throws Exception {
    try (java.io.InputStream in = host.getAssets().open("native/release-v1/" + path)) {
      byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
      StringBuilder value = new StringBuilder();
      for (byte b : hash) value.append(String.format(Locale.ROOT, "%02x", b & 255));
      return value.toString();
    }
  }
  JSONObject control(MainActivity host, String id, View v) {
    assertNotNull(id, v); assertTrue(id + " shown", v.isShown());
    Rect bounds = review.geometry.bounds(v, host.root), visible = review.geometry.visibleIntersection(v, host.root);
    assertTrue(id + " 48dp", bounds.width() >= host.dp(48) && bounds.height() >= host.dp(48));
    assertEquals(id + " unclipped", bounds, visible);
    CharSequence description = v.getContentDescription();
    if (description == null && v instanceof TextView) description = ((TextView) v).getText();
    assertNotNull(id + " accessible name", description);
    return MainActivity.obj("id", id, "boundsDp", coordinates(bounds), "visibleBoundsDp", coordinates(visible), "enabled", v.isEnabled(), "description", description.toString());
  }
  JSONArray textInk(MainActivity host, TextView text) {
    Rect own = review.geometry.bounds(text, host.root), inkAll = null;
    for (int line = 0; line < text.getLineCount(); line++) {
      int start = text.getLayout().getLineStart(line), end = text.getLayout().getLineEnd(line);
      String content = text.getText().subSequence(start, end).toString().stripTrailing();
      if (content.isEmpty()) continue;
      Rect ink = new Rect(); text.getPaint().getTextBounds(content, 0, content.length(), ink);
      int x = own.left + text.getCompoundPaddingLeft() + Math.round(text.getLayout().getLineLeft(line));
      int baseline = own.top + text.getBaseline() + text.getLayout().getLineBaseline(line) - text.getLayout().getLineBaseline(0);
      ink.offset(x, baseline); if (inkAll == null) inkAll = ink; else inkAll.union(ink);
    }
    assertNotNull("Measured text ink " + text.getText(), inkAll); return coordinates(inkAll);
  }
  JSONObject v3Evidence(MainActivity host, String name, String state, int[] size, NativeEconomyThemeFixture.Api api) throws Exception {
    RecruitmentScreen screen = host.root.findViewWithTag("recruitment-stations");
    LinkedHashMap<String, View> targets = new LinkedHashMap<>();
    targets.put("back", host.root.findViewWithTag("back"));
    for (String kind : RecruitmentProbabilityView.KINDS) targets.put("draw-card-" + kind, host.root.findViewWithTag("draw-card-" + kind));
    targets.put("minus", screen.minus); targets.put("plus", screen.plus); targets.put("count", screen.count); targets.put("maximum", screen.maximum);
    targets.put("info", screen.info); targets.put("draw-claim", screen.claim); targets.put("draw-submit", screen.go);
    JSONArray controls = new JSONArray(); for (Map.Entry<String, View> entry : targets.entrySet()) controls.put(control(host, entry.getKey(), entry.getValue()));
    JSONArray labels = new JSONArray(); int index = 0;
    for (View v : review.geometry.descendants(screen)) {
      assertFalse("Recruitment body never scrolls vertically", v instanceof ScrollView);
      if (!(v instanceof TextView) || !v.isShown() || ((TextView) v).length() == 0) continue;
      TextView text = (TextView) v; review.readable(text);
      String id = String.valueOf(v.getTag()); if (id.equals("null")) id = "label-" + index;
      if (v == screen.info) id = "draw-pity-summary";
      String owner = null; for (Map.Entry<String, View> entry : targets.entrySet()) if (review.contains(entry.getValue(), v)) { owner = entry.getKey(); break; }
      int ellipsis = 0; for (int line = 0; line < text.getLineCount(); line++) ellipsis += text.getLayout().getEllipsisCount(line);
      labels.put(MainActivity.obj("id", id, "boundsDp", coordinates(review.geometry.bounds(v, host.root)), "visibleBoundsDp", coordinates(review.geometry.visibleIntersection(v, host.root)), "inkBoundsDp", textInk(host, text), "text", text.getText().toString(), "ellipsisCount", ellipsis, "ownerControl", owner == null ? JSONObject.NULL : owner)); index++;
    }
    JSONArray selectors = new JSONArray();
    for (String kind : RecruitmentProbabilityView.KINDS) {
      View card = targets.get("draw-card-" + kind); TextView title = host.root.findViewWithTag("draw-station-title-" + kind);
      selectors.put(MainActivity.obj("kind", kind, "selected", card.isSelected(), "nonColorSelectionCue", !card.isSelected() || review.rightSelectionMarker(host, card)));
    }
    NativeEconomy.RecruitmentTray hero = (NativeEconomy.RecruitmentTray) screen.stage.getChildAt(0);
    Rect b = review.geometry.bounds(hero, host.root);
    JSONArray art = new JSONArray(Arrays.asList(b.left + hero.target.left, b.top + hero.target.top, b.left + hero.target.right, b.top + hero.target.bottom));
    JSONObject heroData = MainActivity.obj("count", screen.stage.getChildCount(), "kind", screen.selected.optString("kind"), "asset", hero.path,
        "importantForAccessibility", hero.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO, "boundsDp", coordinates(b), "artBoundsDp", art, "sourceBounds", coordinates(hero.source), "assetSha256", assetDigest(host, hero.path), "bitmapDimensions", new JSONArray().put(hero.bitmap.getWidth()).put(hero.bitmap.getHeight()));
    assertEquals("Fixtures explicitly use mdpi, so px geometry equals dp", 1f, host.getResources().getDisplayMetrics().density, .001f);
    return MainActivity.obj("widthDp", size[0], "heightDp", size[1], "fontScale", size[2] / 100.0, "density", 1, "widthPx", size[0], "heightPx", size[1],
        "layoutRevision", 3, "compactMode", screen.compact, "composition", MainActivity.obj("upperInfoBoundsDp", coordinates(review.geometry.bounds(screen.information, host.root)), "purchaseBoundsDp", coordinates(review.geometry.bounds(screen.purchase, host.root))),
        "state", state, "actualNativeViews", true, "apiWrites", api.writes, "verticalScroll", false, "findings", new JSONArray(), "png", name + ".png", "sha256", digest(output().resolve(name + ".png")),
        "safeBoundsDp", new JSONArray(Arrays.asList(0,0,size[0],size[1])), "selectedKind", screen.selected.optString("kind"), "selectors", selectors, "hero", heroData, "controls", controls, "labels", labels);
  }

  @Test public void renderDistinctCounterTraysAndActionStatesAcrossSizes() throws Exception {
    JSONArray captures = new JSONArray(); List<String> failures = new ArrayList<>();
    for (int profile = 0; profile < V3_SIZES.length; profile++) {
      int[] size = V3_SIZES[profile];
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi"); RuntimeEnvironment.setFontScale(size[2] / 100f);
      for (String state : profile == 0 ? STATES : new String[] {"low"}) try (var controller = review.flow.start()) {
        MainActivity host = controller.get(); String name = state + "-" + size[0] + "x" + size[1] + "-font" + size[2];
        try {
          JSONObject response = NativeRecruitmentProbabilityTest.serverEconomyFixture();
          if (state.equals("cooldown")) response.put("wait", 3600000);
          if (state.equals("insufficient")) response.put("credits", 0);
          if (state.equals("unlimited")) response.put("credits", 0).put("unlimited", true);
          String original = response.toString(); NativeEconomyThemeFixture.Api api = prepare(host, response);
          host.menus.economy.draw();
          String kind = state.equals("mid") ? "MID" : state.equals("high") || state.equals("maximum") || state.equals("unlimited") || state.equals("insufficient") ? "HIGH" : "LOW";
          host.root.findViewWithTag("draw-card-" + kind).performClick();
          if (state.equals("maximum") || state.equals("unlimited")) review.actions.button(host.root.findViewWithTag("draw-quantity"), "최대").performClick();
          // Audit the settled v3 composition, not an in-flight two-tray crossfade.
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(240));
          review.ready(host, size);
          RecruitmentScreen stable = host.root.findViewWithTag("recruitment-stations");
          int revisionBefore = stable.contentRevision, measuredBefore = stable.measuredRevision;
          boolean compactBefore = stable.compact;
          for (int repeat = 0; repeat < 3; repeat++) review.layout(host, size);
          assertEquals("Identical layout cannot rewrite content", revisionBefore, stable.contentRevision);
          assertEquals("Identical layout reuses measured content revision", measuredBefore, stable.measuredRevision);
          assertEquals("Identical layout does not oscillate mode", compactBefore, stable.compact);
          capture(host, name, size);
          review.audit(host, kind.equals("HIGH") ? "recruit-high" : kind.equals("MID") ? "recruit-mid" : "recruit", api);
          RecruitmentScreen screen = host.root.findViewWithTag("recruitment-stations");
          if (size[0] == 800 && size[1] == 360 && size[2] == 100)
            assertFalse("Normal 800×360/font1 keeps the full approved composition; compact fallback is not fidelity", screen.compact);
          assertEquals(!state.equals("cooldown"), screen.claim.isEnabled()); assertEquals(!state.equals("insufficient"), screen.go.isEnabled());
          assertEquals("지원서 받기", screen.claim.getText().toString()); assertEquals(original, response.toString()); assertEquals(List.of("draw"), api.calls); assertEquals(0, api.writes);
          int cost = RecruitmentProbabilityView.find(response.getJSONArray("gachas"), kind).getInt("cost");
          int expected = state.equals("maximum") ? Math.min(50, response.optInt("credits") / cost) : state.equals("unlimited") ? 50 : 1;
          assertEquals(expected, screen.amount); assertEquals(cost, screen.unitCost);
          assertTrue(screen.total.getText().toString().contains("필요 " + ((long) cost * expected)));
          captures.put(v3Evidence(host, name, state, size, api));
        } catch (Throwable failure) { failures.add(name + ": " + failure); capture(host, name, size); }
      }
    }
    Files.writeString(output().resolve("evidence.json"), MainActivity.obj("schema", "recruitment-v3-native-layout/1", "sourceRevision", revision(), "captures", captures, "failures", new JSONArray(failures)).toString(2), StandardCharsets.UTF_8);
    assertTrue(String.join("\n", failures), failures.isEmpty()); assertEquals(11, captures.length());
  }

  @Test public void referenceArtworkUsesExactKindMetadataAndSafeImmutableFallback() {
    Bitmap preferred = Bitmap.createBitmap(1663, 946, Bitmap.Config.ARGB_8888);
    Bitmap fallback = Bitmap.createBitmap(768, 512, Bitmap.Config.ARGB_8888);
    Bitmap recycled = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); recycled.recycle();
    int[][] bounds = {{59,84,1639,937},{59,83,1637,938},{60,86,1640,937}};
    String[] kinds = {"LOW", "MID", "HIGH"};
    try {
      for (int i = 0; i < kinds.length; i++) {
        String kind = kinds[i], stem = "recruitment/tray-" + kind.toLowerCase(Locale.ROOT);
        RecruitmentArtwork.Asset selected = RecruitmentArtwork.resolve(kind, path -> path.endsWith("reference-v3.png") ? preferred : fallback);
        assertEquals(stem + "-reference-v3.png", selected.path); assertSame(preferred, selected.bitmap);
        Rect expected = new Rect(bounds[i][0], bounds[i][1], bounds[i][2], bounds[i][3]);
        assertEquals(expected, selected.sourceRect()); selected.sourceRect().setEmpty(); assertEquals(expected, selected.sourceRect());
        for (Bitmap invalid : new Bitmap[] {null, fallback, recycled}) {
          RecruitmentArtwork.Asset old = RecruitmentArtwork.resolve(kind, path -> path.endsWith("reference-v3.png") ? invalid : fallback);
          assertEquals(stem + "-v1.png", old.path); assertSame(fallback, old.bitmap);
        }
        RecruitmentArtwork.Asset failure = RecruitmentArtwork.resolve(kind, path -> { if (path.endsWith("reference-v3.png")) throw new IllegalArgumentException("bad decode"); return fallback; });
        assertEquals(stem + "-v1.png", failure.path); assertSame(fallback, failure.bitmap);
      }
      for (Rect invalid : new Rect[] {null, new Rect(), new Rect(-1,0,10,10), new Rect(0,0,1664,946), new Rect(0,0,1663,947)})
        assertFalse(RecruitmentArtwork.validateSourceRect(preferred, invalid));
    } finally { preferred.recycle(); fallback.recycle(); }
  }

  @Test public void preservesCooldownBoundaryAndSelectionWithoutWrites() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity host = controller.get();
      JSONObject response = NativeRecruitmentProbabilityTest.serverEconomyFixture().put("wait", 1000).put("credits", 3);
      NativeEconomyThemeFixture.Api api = prepare(host, response);
      host.menus.economy.draw();
      Button claim = host.root.findViewWithTag("draw-claim");
      assertFalse(claim.isEnabled());
      host.root.findViewWithTag("draw-card-HIGH").performClick();
      assertFalse(host.root.findViewWithTag("draw-submit").isEnabled());
      host.root.findViewWithTag("draw-card-MID").performClick();
      assertTrue(host.root.findViewWithTag("draw-submit").isEnabled());
      host.busy = true;
      host.root.findViewWithTag("draw-card-LOW").performClick();
      assertTrue(host.root.findViewWithTag("draw-card-MID").isSelected());
      host.busy = false;
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));
      assertTrue("Elapsed cooldown enables the existing claim without fetching", claim.isEnabled());
      assertEquals(List.of("draw"), api.calls); assertEquals(0, api.writes);
      // Exact containment even when float ratio multiplication rounds above an integer edge.
      Bitmap surface = Bitmap.createBitmap(300, 128, Bitmap.Config.ARGB_8888);
      try {
        for (String kind : new String[] {"LOW", "MID", "HIGH"}) {
          NativeEconomy.RecruitmentTray tray = new NativeEconomy.RecruitmentTray(host, kind);
          assertNotNull(tray.bitmap);
          for (int height : new int[] {90, 92, 95, 103, 108, 116, 121}) {
            tray.layout(0, 0, 300, height);
            tray.draw(new Canvas(surface));
            assertTrue(kind + " exact containment at " + height + ": " + tray.target,
                new android.graphics.RectF(0, 0, 300, height).contains(tray.target));
            assertEquals(height, tray.target.bottom, 0f);
            assertTrue(tray.target.contains(tray.selectionBounds));
          }
        }
      } finally { surface.recycle(); }
    }
  }
  @Test public void claimRequestStaysLockedAcrossTicksAndRefreshesCooldown() throws Exception {
    try (var controller = review.flow.start()) {
      MainActivity host = controller.get();
      NativeEconomyThemeFixture.Api fixture = new NativeEconomyThemeFixture().prepare(host);
      List<String> calls = new ArrayList<>();
      NativeApi.Result[] pending = new NativeApi.Result[1], statePending = new NativeApi.Result[1];
      boolean[] claimed = {false};
      host.api = new NativeApi(host) {
        @Override String token() { return "offline-claim-lifecycle"; }
        @Override void call(String path, JSONObject body, Result done) {
          calls.add((body == null ? "GET " : "POST ") + path);
          if (path.equals("claim")) {
            assertNotNull(body); assertEquals(0, body.length());
            assertNull("No duplicate request", pending[0]); pending[0] = done;
          } else if (path.equals("native/state")) {
            assertTrue(claimed[0]);
            statePending[0] = done;
          } else {
            assertEquals("draw", path);
            try {
              done.done(NativeRecruitmentProbabilityTest.serverEconomyFixture()
                  .put("credits", claimed[0] ? 230 : 200).put("wait", claimed[0] ? 3600000 : 0), null);
            } catch (JSONException error) { throw new AssertionError(error); }
          }
        }
      };
      host.menus.economy.draw();
      Button claim = host.root.findViewWithTag("draw-claim");
      assertTrue(claim.isEnabled());
      claim.performClick();
      assertTrue(host.busy); assertFalse(claim.isEnabled());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
      assertFalse("Countdown tick cannot re-enable an in-flight claim", claim.isEnabled());
      assertEquals("지원서 받기", claim.getText().toString());
      claim.performClick();
      assertEquals(List.of("GET draw", "POST claim"), calls);
      review.ready(host, NativeEconomyThemeReviewTest.SIZES[0]);
      capture(host, "claim-requesting-915x412", NativeEconomyThemeReviewTest.SIZES[0]);
      boolean requestingEnabled = claim.isEnabled(), requestingBusy = host.busy;
      int requestsAfterDuplicateTap = Collections.frequency(calls, "POST claim");
      claimed[0] = true;
      pending[0].done(MainActivity.obj("result", "지원서 +30"), null);
      assertNotNull(statePending[0]);
      assertFalse("Refresh locks the claim immediately, before another timer tick", claim.isEnabled());
      assertTrue(host.busy);
      boolean refreshingEnabled = claim.isEnabled(), refreshingBusy = host.busy;
      assertEquals(List.of("GET draw", "POST claim", "GET native/state"), calls);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
      assertFalse(claim.isEnabled());
      host.me().put("credits", 230);
      statePending[0].done(host.state, null);
      assertEquals(List.of("GET draw", "POST claim", "GET native/state", "GET draw"), calls);
      assertFalse(host.busy);
      assertEquals(230, host.me().optInt("credits"));
      assertFalse(host.root.findViewWithTag("draw-claim").isEnabled());
      assertTrue(((TextView) host.root.findViewWithTag("draw-claim-status")).getText().toString().contains("다음 지급"));
      assertNotNull(host.visibleDialog);
      host.visibleDialog.dismiss();
      review.ready(host, NativeEconomyThemeReviewTest.SIZES[0]);
      capture(host, "claim-refreshed-915x412", NativeEconomyThemeReviewTest.SIZES[0]);
      Files.write(output().resolve("claim-state.json"), MainActivity.obj(
          "requestingClaimEnabled", requestingEnabled, "requestingBusy", requestingBusy,
          "requestsAfterDuplicateTap", requestsAfterDuplicateTap,
          "refreshingClaimEnabled", refreshingEnabled, "refreshingBusy", refreshingBusy,
          "refreshedClaimEnabled", host.root.findViewWithTag("draw-claim").isEnabled(),
          "refreshedBusy", host.busy, "refreshedCredits", host.me().optInt("credits"),
          "refreshedCooldownMillis", 3600000, "calls", new JSONArray(calls)).toString(2).getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test public void preservesNewOperationLockWhenClaimRefreshBecomesStale() throws Exception {
    for (int transition = 0; transition < 3; transition++) try (var controller = review.flow.start()) {
      MainActivity host = controller.get();
      new NativeEconomyThemeFixture().prepare(host);
      NativeApi.Result[] refresh = new NativeApi.Result[1];
      String[] account = {"original-account"};
      host.api = new NativeApi(host) {
        @Override String token() { return account[0]; }
        @Override void call(String path, JSONObject body, Result done) {
          if (path.equals("draw")) done.done(NativeRecruitmentProbabilityTest.serverEconomyFixture(), null);
          else if (path.equals("claim")) done.done(MainActivity.obj("result", "지원서 +30"), null);
          else { assertEquals("native/state", path); refresh[0] = done; }
        }
      };
      host.menus.economy.draw();
      host.root.findViewWithTag("draw-claim").performClick();
      assertNotNull(refresh[0]); assertTrue(host.busy);
      if (transition == 0) host.epoch++;
      else account[0] = transition == 1 ? "new-account" : "";
      JSONObject current = host.state;
      refresh[0].done(MainActivity.obj("me", MainActivity.obj("credits", 999)), transition == 2 ? "로그인이 만료됐어요." : null);
      if (transition == 2) {
        assertFalse("This request's expired authentication must release its own lock", host.busy);
        assertEquals("login", host.screen);
        assertFalse("Expired account state is cleared", host.state.has("me"));
      } else {
        assertTrue("Stale refresh must not unlock the newer screen/account operation", host.busy);
        assertSame("Stale refresh must not replace current account state", current, host.state);
      }
      assertNull("Stale or expired refresh must not show another account's reward", host.visibleDialog);
    }
  }

}

