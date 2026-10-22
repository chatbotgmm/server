package org.dpdns.gmm;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeStarterLoansLifecycleTest {
  final NativeGameFlowTest flow = new NativeGameFlowTest();
  @Before public void isolate() { flow.isolate(); TemporaryUnitPresentation.reset(); }
  @After public void resetClock() { TemporaryUnitPresentation.reset(); }

  static final class Api extends NativeApi {
    String owner = "loan-account-a";
    int reads;
    Result pending;
    Api(MainActivity host) { super(host); }
    @Override String token() { return owner; }
    @Override void call(String path, JSONObject body, Result done) {
      assertEquals("starter-loans", path);
      assertNull("Resume must only GET, never claim or extend", body);
      assertNull("Only one request may be outstanding", pending);
      reads++; pending = done;
    }
    void finish(JSONObject result, String error) {
      assertNotNull(pending); Result callback = pending; pending = null; callback.done(result,error);
    }
  }
  Api prepare(MainActivity h) {
    h.api.close(); Api api = new Api(h); h.api = api;
    h.guide.enabled = false; h.busy = false; h.screen = "home";
    h.starterLoans = new NativeStarterLoans(h);
    return api;
  }
  @Test public void homeReadIsSingleFlightAndDoesNotMutateInventory() throws Exception {
    try(ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h=controller.get(); Api api=prepare(h); String before=h.state.toString();
      h.starterLoans.resume(); h.starterLoans.resume(); assertEquals(1,api.reads);
      api.finish(StarterLoanTestFixture.ineligible(),null);
      assertFalse(h.starterLoans.checking); assertNull(h.starterLoans.dialog);
      assertEquals("ineligible",h.starterLoans.state.getString("state"));
      assertEquals(before,h.state.toString());
    }
  }
  @Test public void oldAccountCallbackCannotInstallStateOrClock() throws Exception {
    try(ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h=controller.get(); Api api=prepare(h);
      h.starterLoans.resume(); api.owner="loan-account-b";
      h.starterLoans.resume(); api.finish(StarterLoanTestFixture.ineligible(),null);
      assertNull(h.starterLoans.state); assertNull(h.starterLoans.dialog);
      assertEquals(0,TemporaryUnitPresentation.now());
      h.starterLoans.resume(); assertEquals(2,api.reads);
      api.finish(StarterLoanTestFixture.ineligible(),null);
      assertEquals("loan-account-b",h.starterLoans.account);
      assertEquals("ineligible",h.starterLoans.state.getString("state"));
    }
  }
  @Test public void navigationAndOfflineRetryNeverSubmitOrShowStaleDialog() throws Exception {
    try(ActivityController<MainActivity> controller = flow.start()) {
      MainActivity h=controller.get(); Api api=prepare(h);
      h.screen="team"; h.starterLoans.resume(); assertEquals(0,api.reads);
      h.screen="home"; h.starterLoans.resume(); api.finish(null,"offline");
      assertFalse(h.starterLoans.checking); assertNull(h.starterLoans.state);
      h.starterLoans.resume(); assertEquals(2,api.reads); h.screen="team";
      api.finish(MainActivity.obj("state","pending","serverNow",1700000000000L),null);
      assertNull(h.starterLoans.dialog); assertEquals("team",h.screen);
      api.owner=""; h.starterLoans.resume(); assertEquals(2,api.reads);
      assertNull(h.starterLoans.state); assertEquals(0,TemporaryUnitPresentation.now());
    }
  }
}
