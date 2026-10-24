package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;
import android.widget.Button;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w780dp-h319dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60ReincarnationTest {
 final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest();
 @Before public void setup(){helper.isolate();helper.configure(NativeHomePartyFeedbackTest.PROFILES[1]);}
 @After public void restore(){helper.restore();}
 static class Api extends NativeApi {
  String path;int writes;Api(Context c){super(c);}
  @Override String token(){return "synthetic-rebirth-session";}
  @Override void call(String p,JSONObject body,Result cb){path=p;if(body!=null)writes++;
   JSONObject base=MainActivity.obj("hp",100,"mp",50,"patk",30,"matk",40,"pdef",50,"mdef",60);
   cb.done(MainActivity.obj("flow","roster60-v1","serverNow",1000,"candidates",new JSONArray().put(MainActivity.obj("name","갈매미맨","eligible",false,"requiredLevel",60,"preview",MainActivity.obj("baselineLevelOneStats",base,"afterLevelOneStats",base)))),null);
  }
 }
 @Test public void actualProgressionTabShowsNewPreviewAndCannotExecuteLegacyConversion() throws Exception {
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);h.api=api;
   try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));NativeProgression ui=new NativeProgression(h);ui.tab="reincarnation";ui.open();
    assertEquals("native/roster60/reincarnation/preview",api.path);
    Button confirm=h.root.findViewWithTag("roster60-reincarnation-confirm");assertNotNull(confirm);assertFalse(confirm.isEnabled());assertTrue(confirm.getText().toString().contains("Lv.60"));confirm.performClick();assertEquals(0,api.writes);
   }finally{api.pool.shutdownNow();}
  }
 }
}
