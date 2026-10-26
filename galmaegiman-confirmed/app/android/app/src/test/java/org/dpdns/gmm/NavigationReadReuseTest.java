package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real navigation and receipt boundaries; fake transport never grants rewards. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NavigationReadReuseTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();new NativeRenderReviewTest().scheduledFramesUseThePausedLooperClock();}
  @After public void drain()throws Exception{LocalGameArtTest.drain();}

  public static class Host extends MainActivity {
    boolean recording; final List<String> destinations=new ArrayList<>(); Runnable finishCards;
    @Override void show(String target){if(recording)destinations.add(target);super.show(target);}
    @Override void drawResult(JSONObject result,Runnable next){finishCards=next;}
  }
  static class Api extends NativeApi {
    String account="navigation-work-fixture";final JSONObject state;Object receipt=JSONObject.NULL;
    final List<String> paths=new ArrayList<>();final List<JSONObject> battleWrites=new ArrayList<>();Result pending;boolean holdState,holdActive;JSONObject active;
    Api(MainActivity h,JSONObject state){super(h);this.state=state;}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){
      paths.add(path);
      if(path.equals("fixture-write")){done.done(MainActivity.obj("result",receipt),null);return;}
      if(path.equals("native/state")){if(holdState)pending=done;else done.done(state,null);return;}
      if(path.equals("daily")){try{done.done(NativeThemeReviewTest.daily(),null);}catch(Exception bad){throw new AssertionError(bad);}return;}
      if(path.endsWith("battle/active")){if(holdActive)pending=done;else done.done(MainActivity.obj("battles",new JSONArray().put(active)),null);return;}
      if(path.endsWith("battle/read")){try{done.done(NativeRoster60ProductBattleTest.view(),null);}catch(Exception bad){throw new AssertionError(bad);}return;}
      if(path.endsWith("battle/action")){battleWrites.add(body);pending=done;return;}
      throw new AssertionError("Unexpected navigation request: "+path);
    }
    int count(String path){return Collections.frequency(paths,path);}
  }
  ActivityController<Host> host()throws Exception{
    ActivityController<Host> c=Robolectric.buildActivity(Host.class).create().start().resume().visible();
    Host h=c.get();NativeGameFlowTest.awaitStartupResources(h);fixture.prepare(h).close();
    h.api=new Api(h,h.state);h.base("검사","daily");h.recording=true;return c;
  }
  @Test public void nullReceiptRefreshesAuthoritativeStateAndDailyExactlyOnce()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;h.mutate("fixture-write",MainActivity.obj("requestId","fixed"),"daily");
      assertEquals(1,api.count("fixture-write"));assertEquals(1,api.count("native/state"));assertEquals(1,api.count("daily"));
      assertEquals(Collections.singletonList("daily"),h.destinations);assertSame(api.state,h.state);assertFalse(h.busy);
    }
  }
  @Test public void receiptDialogDismissalDoesNotRefetchOrRebuildDestination()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;api.receipt=MainActivity.obj("summary","서버 처리 완료");
      h.mutate("fixture-write",new JSONObject(),"daily");Object page=h.page;AlertDialog dialog=h.visibleDialog;assertNotNull(dialog);assertTrue(dialog.isShowing());
      dialog.getButton(-1).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper(16,java.util.concurrent.TimeUnit.MILLISECONDS);
      assertSame(page,h.page);assertEquals(1,api.count("native/state"));assertEquals(1,api.count("daily"));assertEquals(1,h.destinations.size());
    }
  }
  @Test public void cardReceiptWaitsForExplicitFinishBeforeOneDestinationRead()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;api.receipt=MainActivity.obj("items",new JSONArray());
      h.mutate("fixture-write",new JSONObject(),"daily");assertNotNull(h.finishCards);assertTrue(h.destinations.isEmpty());assertEquals(0,api.count("daily"));
      h.finishCards.run();assertEquals(Collections.singletonList("daily"),h.destinations);assertEquals(1,api.count("daily"));assertEquals(1,api.count("native/state"));
    }
  }
  @Test public void latePostMutationStateCannotRestoreAnotherAccount()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;api.holdState=true;h.mutate("fixture-write",new JSONObject(),"daily");
      JSONObject replacement=MainActivity.obj("me",MainActivity.obj("nickname","새 계정"));h.state=replacement;api.account="replacement-account";
      api.pending.done(api.state,null);assertSame(replacement,h.state);assertTrue(h.destinations.isEmpty());assertEquals(0,api.count("daily"));assertFalse(h.busy);
    }
  }
  static final class Battle extends NativeRoster60Battle {
    JSONObject accepted;Battle(MainActivity h){super(h);}
    @Override void bind(JSONObject data){accepted=data;}
  }
  @Test public void completeAuthorizedActiveSnapshotNeedsOnlyOneReadAndKeepsUnknownFieldsMasked()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;api.active=NativeRoster60ProductBattleTest.view();
      JSONObject opponent=api.active.getJSONObject("state").getJSONArray("fighters").getJSONObject(1);
      opponent.put("stats",MainActivity.obj("hp",10,"mp",10));assertFalse(opponent.getJSONObject("stats").has("durability"));
      Battle b=new Battle(h);b.open();assertSame(api.active,b.accepted);assertEquals(1,api.count("native/roster60/battle/active"));assertEquals(0,api.count("native/roster60/battle/read"));
      assertFalse(b.accepted.getJSONObject("state").getJSONArray("fighters").getJSONObject(1).getJSONObject("stats").has("durability"));
    }
  }
  @Test public void partialCompatibilitySnapshotStillReadsAndLateActiveCannotReplaceNewPage()throws Exception{
    try(var c=host()){Host h=c.get();Api api=(Api)h.api;api.active=MainActivity.obj("battleId","test-battle");Battle b=new Battle(h);b.open();
      assertNotNull(b.accepted);assertEquals(1,api.count("native/roster60/battle/read"));
      api.holdActive=true;b.resume();h.base("이동한 화면","daily");Object page=h.page;JSONObject previously=b.accepted;
      api.pending.done(MainActivity.obj("battles",new JSONArray().put(NativeRoster60ProductBattleTest.view())),null);
      assertSame(previously,b.accepted);assertSame(page,h.page);assertEquals(1,api.count("native/roster60/battle/read"));
    }
  }
  @Test public void closingOldTransportReleasesOnlyTheProcessGuardAndRetriesTheSameDurableIntent()throws Exception{
    try(var c=host()){Host h=c.get();Api old=(Api)h.api;old.active=NativeRoster60ProductBattleTest.view();Battle b=new Battle(h);b.open();
      b.mutate("action",MainActivity.obj("battleId","test-battle","revision",0,"action",MainActivity.obj("kind","focus")));
      assertEquals(1,old.battleWrites.size());JSONObject intent=old.battleWrites.get(0);String id=intent.getString("requestId");
      assertTrue(NativeRoster60Battle.IN_FLIGHT.contains(id));String stored=b.prefs.getString(b.key+":pending","");assertFalse(stored.isEmpty());
      old.close();assertFalse(NativeRoster60Battle.IN_FLIGHT.contains(id));assertEquals(stored,b.prefs.getString(b.key+":pending",""));
      Api fresh=new Api(h,h.state);fresh.active=old.active;h.api=fresh;Battle resumed=new Battle(h);resumed.open();
      assertEquals(1,fresh.battleWrites.size());assertEquals(intent.toString(),fresh.battleWrites.get(0).toString());assertEquals(0,fresh.count("native/roster60/battle/active"));
      assertEquals(stored,resumed.prefs.getString(resumed.key+":pending",""));
    }
  }
}
