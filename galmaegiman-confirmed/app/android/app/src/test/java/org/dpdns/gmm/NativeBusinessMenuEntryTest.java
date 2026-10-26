package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Button;
import android.view.MotionEvent;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Menu admission is independent of retired menu policy; real API outcomes still own actions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeBusinessMenuEntryTest {
  final NativeHomePartyFeedbackTest helper = new NativeHomePartyFeedbackTest();

  @Before public void setup() {
    helper.isolate();
    helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);
    org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("roster60_menu_test_compat", 0).edit().clear().commit();
    org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_progression_requests", 0).edit().clear().commit();
  }

  @After public void restore() { helper.restore(); }

  static final class Api extends NativeApi {
    final JSONObject state;
    final List<String> reads = new ArrayList<>(), writes = new ArrayList<>();
    String session = "menu-entry-fixture-account", menuError;
    boolean rebirthUnlocked, holdState;
    Result pendingState;
    int menuReads;

    Api(Context context, JSONObject stored) { super(context); state = stored; }
    @Override String token() { return session; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        writes.add(path);
        throw new AssertionError("Entry alone must never perform an action: " + path);
      }
      reads.add(path);
      if (path.equals("native/menu-access")) {
        menuReads++;
        if ("PENDING".equals(menuError)) return;
        JSONObject locks = new JSONObject();
        for (String id : Arrays.asList("adventure", "recruit", "roster", "craft", "shop", "daily", "party", "rebirth"))
          try { locks.put(id, MainActivity.obj("enabled", false, "mode", "force_lock", "reason", "ACCOUNT_LOCKED", "requiredLevel", 99)); }
          catch (JSONException error) { throw new AssertionError(error); }
        done.done(menuError == null ? MainActivity.obj("features", locks) : null, menuError);
      } else if (path.equals("native/state")) {
        if (holdState) { pendingState = done; return; }
        done.done(state, null);
      } else if (path.equals("native/roster60/home")) {
        done.done(MainActivity.obj("rebirthUnlocked", rebirthUnlocked, "rebirthIntroPending", false, "mailUnread", 1), null);
      } else if (path.equals("draw")) {
        done.done(MainActivity.obj("wait", 3600000), null);
      } else if (path.equals("daily")) {
        done.done(MainActivity.obj("attend", MainActivity.obj("today", true), "missions", new JSONArray()), null);
      } else if (path.equals("starter-loans")) {
        done.done(StarterLoanTestFixture.ineligible(), null);
      } else if (path.equals("native/roster60/reincarnation/preview")) {
        done.done(MainActivity.obj("flow", "roster60-v1", "serverNow", 1000, "catalogVersion", "fixture",
            "candidates", new JSONArray().put(MainActivity.obj("name", "시험 대원", "ownedId", "owned-low-level",
                "eligible", false, "requiredLevel", 60))), null);
      } else throw new AssertionError("Unexpected read: " + path);
    }
  }

  Api prepare(NativeHomePartyFeedbackTest.RouteHost host, String menuError) throws Exception {
    NativeGameFlowTest.awaitStartupResources(host);
    helper.prepare(host).close();
    Api api = new Api(host, host.state);
    api.menuError = menuError;
    host.api = api;
    host.art.key = "fixture-art-key";
    // An existing explicit-lock cache must have no effect after the entry policy is retired.
    host.getSharedPreferences("roster60_menu_test_compat", 0).edit()
        .putStringSet(NativeProgression.accountKey(BuildConfig.SERVER_URL + "|" + api.token()),
            new HashSet<>(Arrays.asList("adventure", "recruit", "roster", "craft", "shop", "daily", "party", "rebirth")))
        .commit();
    return api;
  }

  LobbyView home(NativeHomePartyFeedbackTest.RouteHost host) {
    if (host.root.getChildCount() > 0 && host.root.getChildAt(0) instanceof LobbyView) {
      LobbyView previous = (LobbyView) host.root.getChildAt(0);
      if (previous.live && !previous.isAttachedToWindow()) previous.onDetachedFromWindow();
    }
    host.record = false;
    host.home();
    helper.layout(host, NativeHomePartyFeedbackTest.PROFILES[0]);
    LobbyView lobby = (LobbyView) host.root.getChildAt(0);
    if (!lobby.isAttachedToWindow()) lobby.onAttachedToWindow();
    host.record = true;
    return lobby;
  }

  void touch(Button button) {
    long now = android.os.SystemClock.uptimeMillis();
    for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
      MotionEvent event = MotionEvent.obtain(now, now + 20, action, 5, 5, 0);
      button.dispatchTouchEvent(event);
      event.recycle();
    }
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
  }

  @Test public void everyBusinessMenuIgnoresRetiredPolicyAndCachedLocks() throws Exception {
    // Includes a real legacy force_lock response, missing endpoint, auth/server/network failure,
    // and a callback that never completes. None is requested or used for entry.
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, null);
      for (String retiredResponse : new String[] {null, "HTTP_404", "HTTP_401", "HTTP_403", "HTTP_500", "NETWORK_ERROR", "PENDING"}) {
        api.menuError = retiredResponse;
        host.routes.clear();
        LobbyView lobby = home(host);
        String state = host.state.toString();
        int[] indices = {0, 1, 2, 3, 4, 6, 12, 9, 10, 11};
        String[] routes = {"expedition", "draw", "collection", "craft", "shop", "daily", "team", "shop", "shop", "shop"};
        for (int i = 0; i < indices.length; i++) {
          lobby.setMenuOpen(true);
          Button button = lobby.buttons.get(indices[i]);
          assertTrue(button.isEnabled());
          assertFalse(String.valueOf(button.getContentDescription()).contains("잠김"));
          int count = host.routes.size();
          button.performClick();
          assertEquals(routes[i], host.routes.get(count));
          assertEquals(count + 1, host.routes.size());
          assertFalse(lobby.menuOpen);
        }
        assertEquals(0, api.menuReads);
        assertTrue(api.writes.isEmpty());
        assertEquals(state, host.state.toString());
      }
    }
  }

  @Test public void homeAttachmentMakesThreeStatusReadsAndMenuOpeningAddsNone() throws Exception {
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, "PENDING");
      LobbyView lobby = home(host);
      assertEquals(Arrays.asList("native/roster60/home", "daily", "draw"), api.reads);
      lobby.setMenuOpen(true);
      lobby.setMenuOpen(false);
      lobby.setMenuOpen(true);
      assertEquals(3, api.reads.size());
      assertEquals(0, api.menuReads);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void busyAndDisabledButtonsStillPreventNavigation() throws Exception {
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, "PENDING");
      LobbyView lobby = home(host);
      lobby.setMenuOpen(true);
      host.busy = true;
      for (int index : LobbyView.MENU_ITEMS) lobby.buttons.get(index).performClick();
      assertTrue(host.routes.isEmpty());
      assertEquals("home", host.screen);
      assertTrue(lobby.menuOpen);
      host.busy = false;
      Button recruit = lobby.buttons.get(1);
      recruit.setEnabled(false);
      touch(recruit);
      assertTrue(host.routes.isEmpty());
      recruit.setEnabled(true);
      recruit.performClick();
      assertEquals(Collections.singletonList("draw"), host.routes);
      assertEquals(0, api.menuReads);
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void rebirthMenuOpensBeforeMilestoneButIneligibleUnitCannotConvert() throws Exception {
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, "PENDING");
      LobbyView lobby = home(host);
      assertFalse(lobby.rebirthUnlocked);
      lobby.setMenuOpen(true);
      Button rebirth = lobby.buttons.get(14);
      assertEquals("환생 · 조건과 안내 확인", rebirth.getContentDescription());
      rebirth.performClick();
      assertEquals("progression", host.screen);
      assertTrue(api.reads.contains("native/roster60/reincarnation/preview"));
      Button confirm = host.root.findViewWithTag("roster60-reincarnation-confirm");
      assertNotNull(confirm);
      assertFalse(confirm.isEnabled());
      assertTrue(confirm.getText().toString().contains("Lv.60"));
      touch(confirm);
      assertNull(host.visibleDialog);
      assertTrue(api.writes.isEmpty());
      assertEquals(0, api.menuReads);
    }
  }

  @Test public void repeatedEntryWaitsForStateAndFailedReadNeverBecomesSuccess() throws Exception {
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, "PENDING");
      LobbyView lobby = home(host);
      JSONObject before = host.state;
      api.holdState = true;
      host.record = false;
      lobby.setMenuOpen(true);
      lobby.buttons.get(1).performClick();
      assertTrue(host.busy);
      lobby.buttons.get(1).performClick();
      assertEquals(1, Collections.frequency(api.reads, "native/state"));
      assertNotNull(api.pendingState);
      api.pendingState.done(null, "ACTION_DATA_UNAVAILABLE");
      assertEquals("home", host.screen);
      assertSame(before, host.state);
      AlertDialog dialog = host.visibleDialog;
      assertNotNull(dialog);
      assertTrue(helper.flow.labels(dialog.getWindow().getDecorView()).contains("ACTION_DATA_UNAVAILABLE"));
      assertFalse(host.busy);
      assertTrue(api.writes.isEmpty());
      assertEquals(0, api.menuReads);
    }
  }

  @Test public void rebirthEntryStillRequiresActualLogin() throws Exception {
    try (ActivityController<NativeHomePartyFeedbackTest.RouteHost> controller = helper.routeHost()) {
      NativeHomePartyFeedbackTest.RouteHost host = controller.get();
      Api api = prepare(host, "PENDING");
      LobbyView lobby = home(host);
      api.session = "";
      lobby.setMenuOpen(true);
      lobby.buttons.get(14).performClick();
      assertEquals("login", host.screen);
      assertFalse(api.reads.contains("native/roster60/reincarnation/preview"));
      assertTrue(api.writes.isEmpty());
      assertEquals(0, api.menuReads);
    }
  }

}
