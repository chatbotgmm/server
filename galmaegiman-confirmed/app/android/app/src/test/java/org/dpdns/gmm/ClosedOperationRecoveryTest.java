package org.dpdns.gmm;

import static org.junit.Assert.*;

import android.content.Context;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Closing transport releases process guards; only a real receipt may clear a durable operation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ClosedOperationRecoveryTest {
  final NativeHomePartyFeedbackTest fixture = new NativeHomePartyFeedbackTest();

  @Before public void setup() {
    fixture.isolate();
    org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("native_progression_requests", 0).edit().clear().commit();
    org.robolectric.RuntimeEnvironment.getApplication()
        .getSharedPreferences("roster60_product_battle", 0).edit().clear().commit();
  }

  @After public void restore() { fixture.restore(); }

  static final class PendingApi extends NativeApi {
    final List<String> paths = new ArrayList<>();
    final List<JSONObject> commands = new ArrayList<>();
    Result pending;

    PendingApi(Context context) { super(context); }
    @Override String token() { return "closed-operation-fixture"; }
    @Override void call(String path, JSONObject body, Result done) {
      if (body != null) {
        assertNull("An in-flight operation must block another command", pending);
        paths.add(path);
        try { commands.add(new JSONObject(body.toString())); }
        catch (JSONException invalid) { throw new AssertionError(invalid); }
        pending = done;
        return;
      }
      assertEquals("Only the actual rebirth preview is needed", "native/roster60/reincarnation/preview", path);
      done.done(MainActivity.obj("flow", "roster60-v1", "serverNow", 1000,
          "catalogVersion", "recovery-fixture", "candidates", new JSONArray()), null);
    }

    void complete(JSONObject data, String error) {
      Result callback = pending;
      assertNotNull(callback);
      pending = null;
      callback.done(data, error);
    }
  }

  PendingApi prepare(MainActivity host) throws Exception {
    fixture.prepare(host).close();
    PendingApi api = new PendingApi(host);
    host.api = api;
    return api;
  }

  NativeProgression rebirth(MainActivity host) {
    NativeProgression screen = new NativeProgression(host);
    screen.forceRoster60 = true;
    screen.tab = "reincarnation";
    screen.open();
    return screen;
  }

  @Test public void progressionCloseReopensRetryAndPreservesTheExactDurableRequest() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity host = controller.get();
      PendingApi old = prepare(host);
      NativeProgression original = rebirth(host);
      original.submit("roster60/reincarnation/confirm", MainActivity.obj("ownedId", "owned-recovery",
          "catalogVersion", "recovery-fixture", "previewFingerprint", "same-preview"));
      assertEquals(1, old.commands.size());
      JSONObject command = old.commands.get(0);
      String id = command.getString("requestId"), stored = original.pending().toString();
      Button waiting = host.root.findViewWithTag("progression-retry");
      assertNotNull(waiting);
      assertFalse(waiting.isEnabled());

      old.close();
      assertEquals("Close must not erase uncertain work", stored, original.pending().toString());
      PendingApi fresh = new PendingApi(host);
      host.api = fresh;
      NativeProgression reopened = rebirth(host);
      Button retry = host.root.findViewWithTag("progression-retry");
      assertNotNull(retry);
      assertTrue("The dead transport must no longer hold the process guard", retry.isEnabled());
      retry.performClick();
      assertEquals(1, fresh.commands.size());
      assertEquals("native/roster60/reincarnation/confirm", fresh.paths.get(0));
      assertEquals(command.toString(), fresh.commands.get(0).toString());
      assertEquals(id, fresh.commands.get(0).getString("requestId"));
      assertEquals(stored, reopened.pending().toString());
      reopened.send(reopened.pending());
      assertEquals("A live retry still prevents double submission", 1, fresh.commands.size());

      fresh.complete(null, "NETWORK_ERROR");
      assertEquals(stored, reopened.pending().toString());
      ((Button) host.root.findViewWithTag("progression-retry")).performClick();
      assertEquals(2, fresh.commands.size());
      assertEquals(command.toString(), fresh.commands.get(1).toString());
      fresh.complete(MainActivity.obj("completed", true), null);
      assertFalse("Only the confirmed receipt clears the intent", reopened.pending().has("requestId"));
    }
  }

  @Test public void humanPvpCloseAutomaticallyRetriesTheExactSavedOperationOnReopen() throws Exception {
    try (ActivityController<MainActivity> controller = fixture.flow.start()) {
      MainActivity host = controller.get();
      PendingApi old = prepare(host);
      JSONObject accepting = NativeRoster60HumanPvpTest.battle();
      accepting.put("status", "ACCEPTING").put("deadline", System.currentTimeMillis() + 10000);
      host.openRosterHumanBattle(accepting);
      NativeRoster60PvpBattle original = (NativeRoster60PvpBattle) host.rosterBattle;
      original.accept(true);
      assertEquals(1, old.commands.size());
      JSONObject command = old.commands.get(0);
      String id = command.getString("requestId");
      String stored = original.prefs.getString(original.key + ":pending", "");
      assertFalse(stored.isEmpty());
      assertTrue(NativeRoster60Battle.IN_FLIGHT.contains(id));
      original.pause();

      old.close();
      assertFalse("Close releases the human override's inherited process guard", NativeRoster60Battle.IN_FLIGHT.contains(id));
      assertEquals(stored, original.prefs.getString(original.key + ":pending", ""));
      PendingApi fresh = new PendingApi(host);
      host.api = fresh;
      host.openRosterHumanBattle(accepting);
      NativeRoster60PvpBattle reopened = (NativeRoster60PvpBattle) host.rosterBattle;
      assertEquals(1, fresh.commands.size());
      assertEquals("native/roster60/pvp/accept", fresh.paths.get(0));
      assertEquals(command.toString(), fresh.commands.get(0).toString());
      assertEquals(id, fresh.commands.get(0).getString("requestId"));
      assertEquals(stored, reopened.prefs.getString(reopened.key + ":pending", ""));
      reopened.resume();
      assertEquals("A second resume cannot duplicate the pending command", 1, fresh.commands.size());

      fresh.complete(null, "NETWORK_ERROR");
      reopened.resume();
      assertEquals(2, fresh.commands.size());
      assertEquals(command.toString(), fresh.commands.get(1).toString());
      assertEquals(stored, reopened.prefs.getString(reopened.key + ":pending", ""));
      fresh.complete(accepting, null);
      assertEquals("", reopened.prefs.getString(reopened.key + ":pending", ""));
      reopened.pause();
    }
  }
}
