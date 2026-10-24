package org.dpdns.gmm;
import static org.junit.Assert.*;
import android.content.Context;
import android.widget.Button;
import android.app.AlertDialog;
import org.robolectric.shadows.ShadowAlertDialog;
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
  String path;int writes;boolean eligible;Result pending;JSONObject lastBody;String firstRequest;Api(Context c){super(c);}
  @Override String token(){return "synthetic-rebirth-session";}
  @Override void call(String p,JSONObject body,Result cb){path=p;if(body!=null){writes++;pending=cb;lastBody=body;if(firstRequest==null)firstRequest=body.optString("requestId");return;}
   JSONObject base=MainActivity.obj("hp",100,"mp",50,"patk",30,"matk",40,"pdef",50,"mdef",60);
   cb.done(MainActivity.obj("flow","roster60-v1","serverNow",1000,"catalogVersion","synthetic","candidates",new JSONArray().put(MainActivity.obj("name","타이탄","ownedId","test-owned","previewFingerprint","abc","eligible",eligible,"requiredLevel",60,"preview",MainActivity.obj("baselineLevelOneStats",base,"afterLevelOneStats",base)))),null);
  }
 }
 @Test public void actualProgressionTabShowsNewPreviewAndCannotExecuteLegacyConversion() throws Exception {
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);h.api=api;
   try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));NativeProgression ui=new NativeProgression(h);ui.tab="reincarnation";ui.open();
    assertEquals("native/roster60/reincarnation/preview",api.path);
    Button confirm=h.root.findViewWithTag("roster60-reincarnation-confirm");assertNotNull(confirm);assertFalse(confirm.isEnabled());assertTrue(confirm.getText().toString().contains("Lv.60"));confirm.performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper(600,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals(0,api.writes);
   }finally{api.pool.shutdownNow();}
  }
 }
 @Test public void serverEligiblePreviewUsesConfirmedRouteAndSameRequestRetry() throws Exception {
  try(ActivityController<MainActivity> controller=helper.flow.start()){
   MainActivity h=controller.get();helper.prepare(h);Api api=new Api(h);api.eligible=true;h.api=api;
   try{h.state.put("tutorial",MainActivity.obj("flow","roster60-v1"));NativeProgression ui=new NativeProgression(h);ui.tab="reincarnation";ui.open();
    Button button=h.root.findViewWithTag("roster60-reincarnation-confirm");assertTrue(button.isEnabled());assertFalse("host busy",h.busy);assertFalse("update blocks",h.updateBlocks());assertTrue("current before dialog",ui.current());assertFalse(ui.blocked());button.performClick();assertNotNull(h.visibleDialog);
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper(600,java.util.concurrent.TimeUnit.MILLISECONDS);assertEquals(0,api.writes);
    assertTrue("current after cancel screen="+h.screen,ui.current());assertFalse("busy after cancel",h.busy);assertFalse(ui.blocked());button.performClick();assertNotNull(h.visibleDialog);assertSame(h.visibleDialog,ShadowAlertDialog.getLatestAlertDialog());h.visibleDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();
    assertEquals("notice="+ui.notice+" pending="+ui.pending()+" current="+ui.current()+" writes="+api.writes,"native/roster60/reincarnation/confirm",api.path);assertEquals(1,api.writes);assertFalse(api.lastBody.has("rarity"));
    ui.send(ui.pending());assertEquals(1,api.writes);
    api.pending.done(null,"NETWORK_ERROR");assertTrue(ui.pending().has("requestId"));ui.send(ui.pending());
    assertEquals(2,api.writes);assertEquals(api.firstRequest,api.lastBody.optString("requestId"));
    api.pending.done(MainActivity.obj("completed",true),null);assertFalse(ui.pending().has("requestId"));
   }finally{api.pool.shutdownNow();}
  }
 }

}
