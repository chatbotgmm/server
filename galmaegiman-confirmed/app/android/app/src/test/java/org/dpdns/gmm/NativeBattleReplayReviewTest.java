package org.dpdns.gmm;

import static org.junit.Assert.*;
import static org.dpdns.gmm.NativeHomePartyFeedbackTest.*;
import android.graphics.Bitmap;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeBattleReplayReviewTest {
  final NativeHomePartyFeedbackTest helper=new NativeHomePartyFeedbackTest(){
    @Override Path output() throws Exception {Path p=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"battle-replay");Files.createDirectories(p);return p;}
  };
  static final class ReadOnlyApi extends NativeApi {
    int calls;String owner="replay-review";
    ReadOnlyApi(MainActivity h){super(h);}
    @Override String token(){return owner+".offline-signature";}
    @Override void call(String path,JSONObject body,Result done){calls++;throw new AssertionError("Replay invoked API: "+path);}
  }
  @Before public void setup(){helper.isolate();}
  @After public void restore(){helper.restore();}
  JSONObject record() throws Exception {
    JSONArray frames=new JSONArray();
    for(int n=0;n<3;n++){
      JSONObject b=BattleReplayStoreTest.state("review",n,n==2);b.put("scene","harbor");
      for(int side=0;side<2;side++){
        JSONObject f=b.getJSONArray("teams").getJSONObject(side).getJSONArray("fighters").getJSONObject(0);
        f.put("id",side==0?"D1":"D2").put("image","/duel/sprites/"+(side==0?"D1":"D2")+"_s0_front.webp").put("hp",side==0?100-n*10:100-n*50);
      }
      frames.put(BattleReplayStore.sanitize(b));
    }
    return new JSONObject().put("session","review").put("completedAt",1700000000000L).put("frames",frames);
  }
  @Test public void captureReadOnlyTimelineAndDisposeAcrossProfiles() throws Exception {
    JSONObject metrics=new JSONObject(),findings=new JSONObject();JSONArray captures=new JSONArray();
    for(Profile p:PROFILES){
      helper.configure(p);
      try(ActivityController<MainActivity> controller=helper.flow.start()){
        MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
        String before=h.state.toString();JSONObject record=record();NativeBattleReplay.show(h,record,api.owner);
        NativeBattleReplay.Player player=(NativeBattleReplay.Player)h.root.findViewWithTag("battle-replay-player");assertNotNull(player);
        for(int position:new int[]{0,2}){
          player.timeline.seek(position);player.render();helper.settle(h,p);
          assertEquals(position,player.timeline.position());assertEquals(0,api.calls);assertEquals(before,h.state.toString());
          assertTrue(player.play.getHeight()>=h.dp(48));assertTrue(player.arena.getHeight()>0);
          assertEquals(position==0,player.next.isEnabled());assertEquals(position>0,player.previous.isEnabled());
          for(android.view.View view:all(h.root))assertFalse(view instanceof BattleView);
          String file=p.name+"-"+(position==0?"start":"end")+".png";
          Bitmap bitmap=render(h.root);try{helper.png(bitmap,file);}finally{bitmap.recycle();}
          captures.put(new JSONObject().put("filename",file).put("widthPx",p.width).put("heightPx",p.height));
          metrics.put(file,new JSONObject().put("widthPx",h.root.getWidth()).put("heightPx",h.root.getHeight())
              .put("position",player.timeline.position()).put("frameCount",player.timeline.size()).put("apiCalls",api.calls).put("stateUnchanged",before.equals(h.state.toString()))
              .put("readOnlyScene",true).put("previousEnabled",player.previous.isEnabled()).put("nextEnabled",player.next.isEnabled()));findings.put(file,new JSONArray());
        }
        player.timeline.seek(0);player.timeline.play();NativeBattleReplay.pause(h);assertFalse(player.timeline.playing());
        h.root.removeAllViews();assertTrue(player.disposed);player.timeline.play();player.timeline.tick();assertEquals(0,player.timeline.position());assertEquals(0,api.calls);
      }
    }
    helper.write("manifest.json",new JSONObject().put("schemaVersion",1).put("suite","battle-replay").put("captures",captures).put("requiredCaptureCount",8));helper.write("metrics.json",metrics);helper.write("findings.json",findings);
  }
  @Test public void oldEpochIsBlockedWithoutDeletingRecordOrCallingApi() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()) {
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      JSONObject old=record();
      old.getJSONArray("frames").getJSONObject(0).put("combatRulesEpoch","gmm-combat-legacy");
      String original=old.toString(), before=h.state.toString();
      assertTrue(BattleReplayStore.validRecord(old,true));
      assertFalse(CombatReplayCompatibility.playable(old));
      NativeBattleReplay.show(h,old,api.owner);
      assertNull(h.root.findViewWithTag("battle-replay-player"));
      assertNotNull(h.visibleDialog);
      assertEquals(original,old.toString());assertEquals(before,h.state.toString());assertEquals(0,api.calls);
    }
  }
  @Test public void changingAccountClearsVisibleReplay() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      NativeBattleReplay.show(h,record(),api.owner);NativeBattleReplay.Player p=(NativeBattleReplay.Player)h.root.findViewWithTag("battle-replay-player");
      api.owner="other-account";p.render();assertEquals(0,p.details.getChildCount());assertEquals(0,p.arena.state.length());assertFalse(p.timeline.playing());assertEquals(0,api.calls);
    }
  }
  @Test public void realTimerAdvancesPausesAndStopsOnBack() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      NativeBattleReplay.show(h,record(),api.owner);NativeBattleReplay.Player p=(NativeBattleReplay.Player)h.root.findViewWithTag("battle-replay-player");
      helper.settle(h,PROFILES[0]);p.play.performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200));assertEquals(1,p.timeline.position());
      p.play.performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2400));assertEquals(1,p.timeline.position());
      p.play.performClick();h.back();assertEquals("battleRecords",h.screen);assertTrue(p.disposed);
      NativeBattleReplay.storageBarrier().get(10,java.util.concurrent.TimeUnit.SECONDS);
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertFalse(p.timeline.playing());assertEquals(0,api.calls);
    }
  }
  @Test public void recordsLoadPreservesDirtyTeamExitGuard() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      h.screen="team";h.menus.teamDraft=new java.util.ArrayList<>(java.util.Arrays.asList("unsaved"));h.menus.teamSavedIds.clear();
      h.load("battleRecords");assertEquals("team",h.screen);assertTrue(h.menus.isTeamDirty());assertNotNull(h.visibleDialog);assertEquals(0,api.calls);
    }
  }
  @Test public void serialWritesKeepCapturedAccountAcrossRapidSwitches() throws Exception {
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      String a="a-"+System.nanoTime(),b="b-"+System.nanoTime();api.owner=a;
      NativeBattleReplay.observe(h,BattleReplayStoreTest.state("a",0,false));api.owner=b;
      NativeBattleReplay.observe(h,BattleReplayStoreTest.state("b",0,false));api.owner=a;
      NativeBattleReplay.observe(h,BattleReplayStoreTest.state("a",1,true));api.owner=b;
      NativeBattleReplay.observe(h,BattleReplayStoreTest.state("b",1,true));
      NativeBattleReplay.storageBarrier().get(10,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals("a",NativeBattleReplay.store(h).completed(a).getJSONObject(0).getString("session"));
      assertEquals("b",NativeBattleReplay.store(h).completed(b).getJSONObject(0).getString("session"));assertEquals(0,api.calls);
    }
  }
  @Test public void malformedEffectEntriesCannotCrashDefensiveRenderer()throws Exception{
    try(ActivityController<MainActivity> controller=helper.flow.start()){
      MainActivity h=controller.get();helper.prepare(h);h.api.close();ReadOnlyApi api=new ReadOnlyApi(h);h.api=api;
      JSONObject damaged=record();damaged.getJSONArray("frames").getJSONObject(0).getJSONArray("teams").getJSONObject(0).getJSONArray("fighters").getJSONObject(0).put("effects",new JSONArray("[null,\"bad\"]"));
      assertFalse(BattleReplayStore.validRecord(damaged,true));
      NativeBattleReplay.Player p=new NativeBattleReplay.Player(h,damaged,api.owner);p.render();assertTrue(p.details.getChildCount()>0);assertEquals(0,api.calls);p.stop();
    }
  }
}
