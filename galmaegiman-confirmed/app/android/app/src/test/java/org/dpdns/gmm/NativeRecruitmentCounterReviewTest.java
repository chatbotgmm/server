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

  @Test public void renderDistinctCounterTraysAndActionStatesAcrossSizes() throws Exception {
    JSONObject geometry = new JSONObject(), findings = new JSONObject();
    List<String> failures = new ArrayList<>();
    for (int[] size : NativeEconomyThemeReviewTest.SIZES) {
      RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-land-mdpi");
      RuntimeEnvironment.setFontScale(size[2] / 100f);
      for (String state : STATES) try (var controller = review.flow.start()) {
        MainActivity host = controller.get();
        String name = state + "-" + size[0] + "x" + size[1] + (size[2] == 130 ? "-font130" : "");
        JSONArray problems = new JSONArray(); findings.put(name, problems);
        try {
          JSONObject response = NativeRecruitmentProbabilityTest.serverEconomyFixture();
          if (state.equals("cooldown")) response.put("wait", 3600000);
          if (state.equals("insufficient")) response.put("credits", 0);
          if (state.equals("unlimited")) response.put("credits", 0).put("unlimited", true);
          String original = response.toString();
          NativeEconomyThemeFixture.Api api = prepare(host, response);
          host.menus.economy.draw();
          String kind = state.equals("mid") ? "MID" : state.equals("high") || state.equals("maximum")
              || state.equals("unlimited") || state.equals("insufficient") ? "HIGH" : "LOW";
          host.root.findViewWithTag("draw-card-" + kind).performClick();
          if (state.equals("maximum") || state.equals("unlimited"))
            review.actions.button(host.root.findViewWithTag("draw-quantity"), "최대").performClick();
          review.ready(host, size);
          capture(host, name, size);
          JSONObject audit = review.audit(host, kind.equals("HIGH") ? "recruit-high" : kind.equals("MID") ? "recruit-mid" : "recruit", api);
          Button claim = host.root.findViewWithTag("draw-claim"), submit = host.root.findViewWithTag("draw-submit");
          assertEquals(!state.equals("cooldown"), claim.isEnabled());
          assertEquals(!state.equals("insufficient"), submit.isEnabled());
          assertEquals("지원서 받기", claim.getText().toString());
          assertNotNull("Inactive claim retains themed drawable", claim.getBackground());
          assertEquals(original, response.toString());
          assertEquals(List.of("draw"), api.calls);
          assertEquals(0, api.writes);
          LinearLayout header = host.root.findViewWithTag("theme-page-header");
          assertTrue("Compact title leaves a visible gap before the resource bars",
              host.root.findViewWithTag("draw-header-space").getWidth() >= host.dp(24));
          assertTrue(host.title.getWidth() < header.getWidth() / 3);
          View rail = host.root.findViewWithTag("draw-purchase-rail");
          for (String tag : new String[] {"draw-claim", "draw-quantity", "draw-total", "draw-submit"})
            assertEquals("All primary controls share one bottom row", rail, host.root.findViewWithTag(tag).getParent());
          JSONArray trays = new JSONArray();
          float baseline = -1;
          for (String tier : RecruitmentProbabilityView.KINDS) {
            NativeEconomy.RecruitmentTray tray = host.root.findViewWithTag("draw-station-tray-" + tier);
            assertEquals(tier.equals(kind), tray.isSelected());
            Rect bounds = review.geometry.bounds(tray, host.root);
            Rect ink = review.trayAlphaBounds(tray.bitmap);
            assertEquals(ink, tray.source);
            float bottom = bounds.top + tray.target.top
                + (ink.bottom - tray.source.top) * tray.target.height() / tray.source.height();
            Rect price = review.geometry.bounds(host.root.findViewWithTag("draw-station-cost-" + tier), host.root);
            assertEquals("Visible wood meets its price plaque without a transparent gap", price.top, bottom, 1f);
            if (baseline < 0) baseline = bottom;
            assertEquals("All trays sit on a common counter", baseline, bottom, 1f);
            assertTrue("Visible tray occupies meaningful height", tray.target.height() >= host.dp(64));
            trays.put(MainActivity.obj("kind", tier, "path", tray.path, "artWidth", tray.target.width(), "artHeight", tray.target.height(),
                "sourceBounds", new JSONArray(Arrays.asList(ink.left, ink.top, ink.right, ink.bottom)), "visibleBaseline", bottom));
          }
          String total = ((TextView) host.root.findViewWithTag("draw-total")).getText().toString();
          int cost = kind.equals("LOW") ? 2 : kind.equals("MID") ? 3 : 5;
          int amount = state.equals("maximum") ? 40 : state.equals("unlimited") ? 50 : 1;
          assertTrue(total.contains(state.equals("insufficient") ? "필요 5장" : cost * amount + "장"));
          geometry.put(name, audit.put("selectedKind", kind).put("totalText", total)
              .put("claimEnabled", claim.isEnabled()).put("submitEnabled", submit.isEnabled()).put("trays", trays));
        } catch (Throwable failure) {
          failures.add(name + ": " + failure); problems.put(failure.toString());
          capture(host, name, size);
          geometry.put(name, MainActivity.obj("passed", false, "failure", failure.toString()));
        }
      }
    }
    Files.write(output().resolve("geometry.json"), geometry.toString(2).getBytes(StandardCharsets.UTF_8));
    Files.write(output().resolve("findings.json"), findings.toString(2).getBytes(StandardCharsets.UTF_8));
    assertEquals(35, geometry.length());
    assertTrue(String.join("\n", failures), failures.isEmpty());
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
