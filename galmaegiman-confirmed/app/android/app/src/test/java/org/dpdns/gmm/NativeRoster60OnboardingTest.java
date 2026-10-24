package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Context;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w640dp-h320dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60OnboardingTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
  @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[0]);}
  @After public void restore(){helper.restore();}
  static class Api extends NativeApi {
    final List<String> requests=new ArrayList<>();Result pending;
    Api(Context c){super(c);}
    @Override String token(){return "synthetic-authenticated-test-session";}
    @Override void call(String path,JSONObject body,Result cb){
      requests.add(path);
      if(path.endsWith("/confirm")){pending=cb;return;}
      cb.done(MainActivity.obj("flow","roster60-v1","starter",MainActivity.obj("speciesId","u01")),null);
    }
  }
  @Test public void mainTutorialUsesAuthenticatedRouteAndCancelNeverClaims() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);h.api=api;
      try {
        h.tutorial();assertEquals(Arrays.asList("native/roster60/start"),api.requests);
        AlertDialog first=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(first);
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertEquals(1,api.requests.size());
        NativeRoster60Onboarding ui=new NativeRoster60Onboarding(h);
        JSONObject unit=MainActivity.obj("id","u02","name","미니","stats",MainActivity.obj("hp",10,"mp",12,"durability",3,"statusResistance",4,"speed",5),"skills",new JSONArray(),"suitability",new JSONObject(),"defense",new JSONObject());
        ui.detail("ticket-test","synthetic-test-only",unit);
        assertArrayEquals(new String[]{"체력","마력(MP)","내구력","저항력(상태이상)","속도"},h.getResources().getStringArray(R.array.roster60_stat_names));
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertEquals(1,api.requests.size());
        ui.confirm("ticket-test","synthetic-test-only","u02");
        ui.confirm("ticket-test","synthetic-test-only","u03");
        assertEquals(Arrays.asList("native/roster60/start","native/roster60/confirm"),api.requests);
        assertTrue(ui.submitting);
      } finally {api.pool.shutdownNow();}
    }
  }
}
