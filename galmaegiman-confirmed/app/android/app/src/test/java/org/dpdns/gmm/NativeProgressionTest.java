package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.os.Looper;
import java.time.Duration;
import android.view.View;
import android.widget.Button;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeProgressionTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  ActivityController<MainActivity> controller;
  MainActivity h;
  FakeApi api;
  NativeProgression screen;

  static JSONObject fixture() {
    return MainActivity.obj("serverNow", 1000000L,
        "pass", MainActivity.obj("points", 100, "milestones", new JSONArray().put(MainActivity.obj("id", "m1", "points", 100, "credits", 3, "claimable", true, "claimed", false))),
        "reroll", MainActivity.obj("available", false, "expiresAt", JSONObject.NULL, "nextClaimAt", JSONObject.NULL),
        "reassessment", MainActivity.obj("nextAvailableAt", JSONObject.NULL, "candidates", new JSONArray().put(MainActivity.obj("key", "owned-2", "id", "R1", "name", "대원", "rarity", "RARE"))),
        "reincarnation", MainActivity.obj("statBonusPct", 5, "maxLevel", 1, "candidates", new JSONArray().put(MainActivity.obj("key", "owned-1", "id", "D1", "name", "최상위", "rarity", "TOP", "locked", false, "eligible", true))));
  }

  static final class FakeApi extends NativeApi {
    String session = "player.session";
    JSONObject response = fixture();
    boolean holdRead;
    Result read;
    final List<Result> writes = new ArrayList<>();
    final List<JSONObject> bodies = new ArrayList<>();
    final List<String> paths = new ArrayList<>();
    FakeApi(MainActivity host) { super(host); }
    @Override String token() { return session; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body == null) {
        assertEquals("native/progression", path);
        if (holdRead) read = done; else done.done(response, null);
      } else { paths.add(path); bodies.add(body); writes.add(done); }
    }
    void reply(JSONObject data, String error) { writes.remove(0).done(data, error); }
  }

  @Before public void setup() {
    flow.isolate();
    RuntimeEnvironment.getApplication().getSharedPreferences("native_progression_requests", 0).edit().clear().commit();
    controller = flow.start(); h = controller.get(); h.api.close();
    api = new FakeApi(h); h.api = api; h.guide.enabled = false;
    h.state = MainActivity.obj("me", MainActivity.obj("units", new JSONArray(), "snack", 0, "peanut", 0, "credits", 0));
    screen = new NativeProgression(h);
  }
  @After public void cleanup() {
    while (!api.writes.isEmpty()) api.reply(MainActivity.obj("ok", true), null);
    api.close(); controller.pause().stop().destroy();
  }
  Button button(String tag) { Button b = h.root.findViewWithTag(tag); assertNotNull(tag, b); return b; }
  // 변환 경로의 계약 시험은 직접 대상 화면을 고른다. 패스에는 변환 탭을 노출하지 않는다.
  void tab(String name) { screen.tab = name; screen.selectedKey = ""; screen.receipt = null; screen.notice = ""; screen.render(); }

  @Test public void playPassIsIndependentAndHasNoConversionTabs() throws Exception {
    JSONArray rewards = api.response.getJSONObject("pass").getJSONArray("milestones");
    rewards.put(MainActivity.obj("id", "m2", "points", 200, "credits", 4, "claimable", false, "claimed", false));
    rewards.put(MainActivity.obj("id", "m3", "points", 300, "credits", 5, "claimable", false, "claimed", true));
    rewards.put(MainActivity.obj("id", "m4", "points", 400, "credits", 6, "claimable", false, "claimed", false));
    screen.open();
    assertEquals("플레이 패스", h.title.getText().toString());
    for (String name : new String[] {"pass", "reroll", "reincarnation", "reassessment"})
      assertNull(h.root.findViewWithTag("progression-tab-" + name));
    assertNotNull(h.root.findViewWithTag("play-pass-points"));
    assertNotNull(h.root.findViewWithTag("play-pass-reward-m4"));
    assertTrue(button("progression-pass-m1").isEnabled());
    assertFalse(button("progression-pass-m2").isEnabled());
    assertFalse(button("progression-pass-m3").isEnabled());
    assertNull(h.root.findViewWithTag("progression-pagination"));
    assertTrue(api.bodies.isEmpty());
  }

  @Test public void repeatedTapSendsOneDurableIntent() {
    screen.open(); Button claim = button("progression-pass-m1"); claim.performClick(); claim.performClick();
    assertEquals(1, api.bodies.size()); assertEquals("native/progression/pass/claim", api.paths.get(0));
    assertEquals(api.bodies.get(0).optString("requestId"), screen.pending().optString("requestId"));
    assertNull(h.root.findViewWithTag("progression-pass-m1"));
  }

  @Test public void ambiguousRetryReusesExactRequestIdAndPayload() {
    screen.open(); button("progression-pass-m1").performClick();
    String first = api.bodies.get(0).toString(); api.reply(null, "연결하지 못했어요.");
    button("progression-retry").performClick();
    assertEquals(2, api.bodies.size()); assertEquals(first, api.bodies.get(1).toString());
    api.reply(MainActivity.obj("ok", true), null); assertFalse(screen.pending().has("requestId"));
  }

  @Test public void successRequiresExplicitOkAndMissingApiDoesNotGrantAnything() {
    screen.open(); button("progression-pass-m1").performClick(); api.reply(new JSONObject(), null);
    assertTrue(screen.pending().has("requestId")); assertNull(h.root.findViewWithTag("progression-pass-m1"));
    api.response = new JSONObject(); screen.refresh(); assertNull(h.root.findViewWithTag("progression-pass-m1"));
  }

  @Test public void terminalIneligibleResponseReleasesIntentButAmbiguousServerErrorDoesNot() {
    screen.open(); button("progression-pass-m1").performClick();
    api.reply(MainActivity.obj("error", "서버 오류"), "서버 오류"); assertTrue(screen.pending().has("requestId"));
    button("progression-retry").performClick();
    api.reply(MainActivity.obj("ok", false, "retryable", false, "error", "대상 아님"), "대상 아님");
    assertFalse(screen.pending().has("requestId"));
  }

  @Test public void backDismissesOverlayAndLateReadCannotReplaceResult() {
    h.base("결과", "drawResult"); int epoch = h.epoch; View page = h.page;
    api.holdRead = true; NativeProgression.openReassessment(h, "owned-2");
    assertNotNull(h.root.findViewWithTag("progression-reassessment-overlay"));
    h.getOnBackPressedDispatcher().onBackPressed();
    api.read.done(fixture(), null);
    assertEquals(epoch, h.epoch); assertSame(page, h.page); assertEquals("drawResult", h.screen);
    assertNull(h.root.findViewWithTag("progression-reassessment-overlay")); assertTrue(api.bodies.isEmpty());
  }

  @Test public void authChangeAndLateReadCannotPaintOtherAccount() {
    api.holdRead = true; screen.open(); api.session = "other.session";
    api.read.done(fixture(), null); assertNull(screen.snapshot); assertTrue(api.bodies.isEmpty());
  }

  @Test public void lateMutationAfterNavigationDoesNotReopenPage() {
    screen.open(); button("progression-pass-m1").performClick(); h.base("다른 화면", "home");
    int epoch = h.epoch; api.reply(MainActivity.obj("ok", true), null);
    assertEquals("home", h.screen); assertEquals(epoch, h.epoch); assertFalse(screen.pending().has("requestId"));
  }

  @Test public void cooldownAndLockedInstanceDisableActions() throws Exception {
    api.response.getJSONObject("reroll").put("nextClaimAt", 2000000L);
    api.response.getJSONObject("reassessment").put("nextAvailableAt", 2000000L);
    api.response.getJSONObject("reincarnation").getJSONArray("candidates").getJSONObject(0).put("locked", true);
    screen.open(); tab("reroll"); assertFalse(button("progression-reroll-claim").isEnabled());
    tab("reassessment"); assertFalse(button("progression-reassessment-owned-2").isEnabled());
    tab("reincarnation"); assertFalse(button("progression-reincarnation-owned-1").isEnabled());
    assertTrue(api.bodies.isEmpty());
  }

  @Test public void destructiveConversionRequiresConfirmationAndUsesInstanceKey() {
    screen.open(); tab("reincarnation"); button("progression-reincarnation-owned-1").performClick();
    assertTrue(api.bodies.isEmpty());
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    assertTrue(api.bodies.isEmpty());
    button("progression-reincarnation-owned-1").performClick();
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    assertEquals("Only the confirmed conversion is sent", 1, api.bodies.size());
    assertEquals("native/progression/reincarnate", api.paths.get(0));
    assertEquals("owned-1", api.bodies.get(0).optString("key")); assertFalse(api.bodies.get(0).has("id"));
  }

  @Test public void resultEntryFiltersExactAcquiredInstanceAndRejectsMissingCandidate() {
    h.base("결과", "drawResult"); NativeProgression.openReassessment(h, "not-owned");
    assertNull(h.root.findViewWithTag("progression-reassessment-owned-2")); assertTrue(api.bodies.isEmpty());
  }

  @Test public void authExpirationPreservesIntentForSamePlayerNewSession() {
    screen.open(); button("progression-pass-m1").performClick(); String id = screen.pending().optString("requestId");
    api.session = ""; api.reply(MainActivity.obj("ok", false, "retryable", false, "error", "login"), "로그인 만료");
    api.session = "player.new-session"; NativeProgression again = new NativeProgression(h); again.open();
    assertEquals(id, again.pending().optString("requestId")); button("progression-retry").performClick();
    assertEquals(id, api.bodies.get(1).optString("requestId"));
  }

  @Test public void augmentBonusRequiresAuthoritativePhaseAvailabilityAndExpiry() throws Exception {
    JSONObject battle = MainActivity.obj("phase", "choose", "serverNow", 1000,
        "bonusReroll", MainActivity.obj("available", true, "expiresAt", 2000));
    assertTrue(NativeAugmentView.bonusRerollAvailable(battle, 1000));
    assertFalse(NativeAugmentView.bonusRerollAvailable(battle, 2000));
    battle.put("phase", "action"); assertFalse(NativeAugmentView.bonusRerollAvailable(battle, 1000));
    battle.put("phase", "choose").getJSONObject("bonusReroll").put("available", false);
    assertFalse(NativeAugmentView.bonusRerollAvailable(battle, 1000));
    assertFalse(NativeAugmentView.bonusRerollAvailable(new JSONObject(), 0));
  }

  @Test public void clientTimeCannotAuthorizeClaim() {
    NativeProgressionState state = new NativeProgressionState(); int read = state.begin();
    assertTrue(state.accept(read, 1000, 10)); assertFalse(state.ready(2000));
    assertEquals(0, state.remaining(2000, 999999)); assertFalse(state.ready(2000));
    int next = state.begin(); assertFalse(state.accept(read, 999999, 20));
    assertTrue(state.accept(next, 2001, 20)); assertTrue(state.ready(2000));
    state.invalidate(); assertFalse(state.ready(0));
  }
}
