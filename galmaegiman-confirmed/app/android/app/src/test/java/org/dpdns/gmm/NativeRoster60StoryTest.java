package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Native client boundaries with controlled authenticated transport; no live player data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoster60StoryTest {
  final NativeHomePartyFeedbackTest fixture=new NativeHomePartyFeedbackTest();
  @Before public void setup(){fixture.isolate();NativeRoster60StoryController.IN_FLIGHT.clear();NativeRoster60Battle.IN_FLIGHT.clear();}
  @After public void restore(){fixture.restore();NativeRoster60StoryController.IN_FLIGHT.clear();}

  static JSONObject quest(String status){return MainActivity.obj("id","STORY_01","title","우리는 무엇을 지키고 있나","kind","main","status",status,
      "recommendedLevel",new JSONArray().put(1).put(8),"canDepart","ACTIVE".equals(status),"objective",MainActivity.obj("kind","adventure_win","wins",0,"requiredWins",1));}
  static JSONObject scene(int cursor,boolean last){return MainActivity.obj("id","story01-intro","questId","STORY_01","phase","intro","cursor",cursor,"last",last,
      "background","village-statue","camera","hero-rear","line",MainActivity.obj("speakerId","hero","speaker","주인공","text","이 조각들이 원래 어떤 모습이었는지, 기억하는 사람은 없어요?"));}
  static JSONObject state(){
    JSONArray residents=new JSONArray();String[] roles={"대장장이","상인","사서","방범대원"},statuses={"AVAILABLE","ACTIVE","READY","LOCKED"};
    for(int i=0;i<4;i++)residents.put(MainActivity.obj("id","resident-"+i,"name","주민 "+i,"role",roles[i],"status",statuses[i],"questId","RESIDENT_0"+(i+1),"dailyDialogue","돌아오는 길도 잊지 말아요."));
    JSONObject current=quest("AVAILABLE");return MainActivity.obj("contentVersion","story-test","revision",3,"testValues",true,
        "statue",MainActivity.obj("stage",1,"fragments",0,"hatched",false),"currentQuest",current,"quests",new JSONArray().put(current),"residents",residents,"history",new JSONArray(),"adventures",new JSONArray());
  }
  static final class Api extends NativeApi {
    String account="story-test-account-a";JSONObject saved=state();
    final List<String> paths=new ArrayList<>();final List<JSONObject> bodies=new ArrayList<>();final List<Result> callbacks=new ArrayList<>();
    Result heldRead;boolean holdReads;
    Api(Context context){super(context);}
    @Override String token(){return account;}
    @Override void call(String path,JSONObject body,Result done){
      paths.add(path);
      if(body==null){if(holdReads){heldRead=done;return;}if(path.equals("native/roster60/story/state")){done.done(copy(saved),null);return;}throw new AssertionError("Unexpected story read "+path);}
      bodies.add(copy(body));callbacks.add(done);
    }
    JSONObject last(){return bodies.get(bodies.size()-1);}
    void respond(int index,JSONObject response,String error){callbacks.get(index).done(response,error);}
  }
  static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception invalid){throw new AssertionError(invalid);}}
  Api prepare(MainActivity h)throws Exception{
    fixture.prepare(h).close();Api api=new Api(h);h.api=api;h.getSharedPreferences("roster60_story_v1",0).edit().clear().commit();return api;
  }
  static Button button(MainActivity h,String tag){View view=h.root.findViewWithTag(tag);assertTrue("Missing story control "+tag,view instanceof Button);return (Button)view;}

  @Test public void realPlayEntryReadsAccountProgressAndResidentMarkersAreSpecific()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);h.openPlayMenu();button(h,"play-story").performClick();
      assertEquals(NativeRoster60StoryController.SCREEN,h.screen);assertNotNull(h.rosterStory.state);assertEquals(0,api.bodies.size());
      assertEquals(java.util.Collections.singletonList("native/roster60/story/state"),api.paths);
      assertNull(h.root.findViewWithTag("roster60-story-draft-notice"));assertNull(h.root.findViewWithTag("story-vn-text"));
      android.widget.LinearLayout header=h.root.findViewWithTag("theme-page-header");assertEquals("No currency HUD on the story/resident screen",2,header.getChildCount());
      button(h,"story-tab-residents").performClick();
      assertTrue(button(h,"story-resident-resident-0").getText().toString().startsWith("! "));
      assertFalse(button(h,"story-resident-resident-1").getText().toString().contains("!"));
      assertTrue(button(h,"story-resident-resident-2").getText().toString().startsWith("보고 가능"));
      assertFalse(button(h,"story-resident-resident-3").getText().toString().contains("!"));
      button(h,"story-resident-resident-3").performClick();assertEquals(0,api.bodies.size());
      assertNotNull(h.rosterStory.residentDialogue);h.rosterStory.residentDialogue.dismiss();
      h.back();assertEquals("playMenu",h.screen);
    }
  }
  @Test public void typingFirstTapAndRepeatedNextNeverInventProgressAndRetryUsesIdenticalIntent()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.saved.put("scene",scene(0,false));h.openRosterStory();NativeRoster60StoryController story=h.rosterStory;
      assertTrue(story.view.animating);button(h,"story-next").performClick();assertFalse(story.view.animating);assertEquals(0,api.bodies.size());
      button(h,"story-next").performClick();button(h,"story-next").performClick();assertEquals(1,api.bodies.size());
      JSONObject first=copy(api.last());assertEquals(0,first.getInt("cursor"));assertEquals(3,first.getInt("revision"));assertFalse(first.has("progress"));assertFalse(first.has("reward"));
      api.respond(0,null,"연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.");
      assertEquals(first.toString(),story.journal.pending().getJSONObject("body").toString());
      story.refresh();assertEquals(2,api.bodies.size());assertEquals(first.toString(),api.last().toString());
      api.saved.put("revision",4).put("scene",scene(1,true));api.respond(1,api.saved,null);
      assertNull(story.journal.pending());assertEquals(4,story.state.getInt("revision"));
      assertTrue(story.view.animating);button(h,"story-accept").performClick();assertEquals(2,api.bodies.size());
      button(h,"story-accept").performClick();assertEquals(3,api.bodies.size());assertEquals("STORY_01",api.last().getString("questId"));assertTrue(api.paths.contains("native/roster60/story/accept"));
    }
  }
  @Test public void forcedCloseAccountSwitchAndOldCallbacksRespectStoredRequestAndScreen()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.saved.put("scene",scene(0,false));h.openRosterStory();NativeRoster60StoryController old=h.rosterStory;
      old.view.finishTyping();button(h,"story-next").performClick();JSONObject request=copy(api.last());
      h.back();assertEquals("playMenu",h.screen);api.respond(0,null,"연결 오류");assertEquals("playMenu",h.screen);assertNotNull(old.journal.pending());
      api.account="story-test-account-b";h.openRosterStory();NativeRoster60StoryController other=h.rosterStory;
      assertNull(other.journal.pending());assertEquals(1,api.bodies.size());
      api.respond(0,api.saved,null);assertEquals(NativeRoster60StoryController.SCREEN,h.screen);assertNotNull(old.journal.pending());
      api.account="story-test-account-a";api.close();Api restarted=new Api(h);h.api=restarted;h.openRosterStory();
      assertEquals("A fresh controller replays the exact durable command",request.toString(),restarted.last().toString());
      NativeRoster60StoryController resumed=h.rosterStory;h.openPlayMenu();restarted.saved.put("revision",4);restarted.respond(0,restarted.saved,null);
      assertEquals("playMenu",h.screen);assertFalse(resumed.current());assertNull(resumed.journal.pending());
    }
  }
  @Test public void staleReadAndOldSceneControlsCannotReplaceAnotherScreenOrAnotherAccount()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.holdReads=true;h.openRosterStory();NativeRoster60StoryController story=h.rosterStory;
      NativeApi.Result response=api.heldRead;h.openPlayMenu();View current=h.page;response.done(api.saved,null);assertSame(current,h.page);assertNull(story.state);
      api.holdReads=false;api.saved.put("scene",scene(0,true));h.openRosterStory();story=h.rosterStory;story.view.finishTyping();Button accept=button(h,"story-accept");
      api.account="other-account";accept.performClick();assertEquals(0,api.bodies.size());assertFalse(story.current());
    }
  }
  @Test public void reenteringWhileOldWriteRunsRechecksItsOwnStateAfterAcknowledgement()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.saved.put("scene",scene(0,false));h.openRosterStory();h.rosterStory.view.finishTyping();button(h,"story-next").performClick();
      h.back();h.openRosterStory();NativeRoster60StoryController replacement=h.rosterStory;assertEquals(1,api.bodies.size());assertNull(replacement.state);
      api.saved.put("revision",4).put("scene",scene(1,true));api.respond(0,api.saved,null);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
      assertNotNull(replacement.state);assertEquals(4,replacement.state.getInt("revision"));assertEquals(1,api.bodies.size());assertTrue(replacement.current());
    }
  }
  @Test public void serverDepartureRecoveryPreservesOriginalIdWithNewLoginToken()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.account="player-id.222.fresh-signature";
      JSONObject intent=MainActivity.obj("operation","battle/create","body",MainActivity.obj("requestId","original-server-workflow","questId","STORY_01","ownedIds",new JSONArray().put("owned-1")));
      api.saved.put("pendingDeparture",intent);h.openRosterStory();assertEquals(1,api.bodies.size());
      assertEquals("original-server-workflow",api.last().getString("requestId"));assertEquals(intent.toString(),h.rosterStory.journal.pending().toString());
      api.respond(0,null,"연결 오류");api.account="player-id.333.rotated-signature";h.openRosterStory();assertEquals(2,api.bodies.size());assertEquals("original-server-workflow",api.last().getString("requestId"));
    }
  }
  @Test public void uncertainBattleReturnRecoversExactIntentInStoryHubAfterForcedClose()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.account="stable-player.222.signature";
      String pendingKey=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|stable-player|story-v1")+":pending";
      JSONObject intent=MainActivity.obj("operation","adventure/return","body",MainActivity.obj("requestId","original-return-intent","adventureId","adventure-1","revision",5));
      android.content.SharedPreferences battles=h.getSharedPreferences("roster60_product_battle",0);battles.edit().putString(pendingKey,intent.toString()).commit();
      h.openRosterStory();assertEquals("native/roster60/adventure/return",api.paths.get(0));assertEquals("original-return-intent",api.last().getString("requestId"));
      api.respond(0,null,"연결 오류");assertNotNull(h.rosterStory.journal.pending());
      api.account="stable-player.333.rotated-signature";h.openRosterStory();assertEquals(2,api.bodies.size());assertEquals("original-return-intent",api.last().getString("requestId"));
      api.respond(1,MainActivity.obj("status","RETURNED"),null);assertNull(h.rosterStory.journal.pending());assertEquals("",battles.getString(pendingKey,""));assertNotNull(h.rosterStory.state);
    }
  }
  @Test public void materialAdventureAfterRelaunchResumesBattleOrReturnsThroughItsOwnServerRun()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.saved.put("currentQuest",JSONObject.NULL).put("materialAdventure",MainActivity.obj("adventureId","material-run","revision",7,"status","ACTIVE","activeBattleId",JSONObject.NULL));
      h.openRosterStory();button(h,"story-material-resume").performClick();assertEquals("native/roster60/adventure/read",api.paths.get(api.paths.size()-1));assertEquals("material-run",api.last().getString("adventureId"));
      api.respond(0,MainActivity.obj("adventureId","material-run","revision",7,"status","ACTIVE","activeBattleId",JSONObject.NULL),null);
      assertEquals("native/roster60/adventure/return",api.paths.get(api.paths.size()-1));assertEquals("material-run",api.last().getString("adventureId"));assertEquals(7,api.last().getInt("revision"));
    }
  }
  @Test public void storyBattleReentryWaitsForOldActionAndReadsLatestServerState()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();prepare(h).close();NativeRoster60ProductBattleTest.Api api=new NativeRoster60ProductBattleTest.Api(h);h.api=api;
      h.getSharedPreferences("roster60_product_battle",0).edit().clear().commit();
      NativeRoster60StoryBattle old=new NativeRoster60StoryBattle(h,copy(api.state),"STORY_01");h.rosterBattle=old;old.open();old.action(MainActivity.obj("kind","focus"));assertEquals(1,api.actions.size());
      old.pause();h.openPlayMenu();NativeRoster60StoryBattle replacement=new NativeRoster60StoryBattle(h,copy(api.state),"STORY_01");h.rosterBattle=replacement;replacement.open();
      assertTrue(replacement.observedPending);assertEquals(1,api.actions.size());
      api.state.put("revision",1);api.pending.done(api.state,null);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
      assertTrue(replacement.current());assertNotNull(replacement.view);assertEquals(1,replacement.view.getInt("revision"));assertEquals(1,api.actions.size());
      assertTrue(api.paths.contains("native/roster60/battle/read"));
    }
  }
  @Test public void rejectedDepartureAndSuccessfulStateRefreshRestoreUsableQuestHub()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);JSONObject active=quest("ACTIVE");api.saved.put("currentQuest",active).put("quests",new JSONArray().put(active));
      h.openRosterStory();NativeRoster60StoryController story=h.rosterStory;
      JSONArray units=new JSONArray().put(MainActivity.obj("ownedId","departure-owned","speciesId","u01","name","갈매미맨","level",1));
      story.view.content.removeAllViews();story.formation=new Roster60FormationView(h,units,ids->story.mutate("battle/create",MainActivity.obj("questId","STORY_01","ownedIds",ids)),()->{});
      story.view.content.addView(story.formation,new android.widget.FrameLayout.LayoutParams(-1,-1));story.formation.toggle(units.getJSONObject(0));
      // The start button is actionable only after the formation has its real first layout.
      int width=h.dp(915),height=h.dp(412);h.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));h.root.layout(0,0,width,height);
      assertTrue(story.formation.start.isEnabled());story.formation.start.performClick();
      assertEquals(1,api.bodies.size());assertEquals("native/roster60/story/battle/create",api.paths.get(api.paths.size()-1));api.respond(0,null,"UNIT_UNAVAILABLE");assertNull(story.journal.pending());
      button(h,"story-tab-main").performClick();assertNull("A successful authoritative refresh replaces invalid departure choices",story.formation);
      assertNull(h.root.findViewWithTag("roster60-formation"));assertTrue(button(h,"story-continue").isEnabled());assertEquals("ACTIVE",story.state.getJSONObject("currentQuest").getString("status"));
    }
  }
  @Test public void journalNeverOverwritesUnknownOutcomeOrClearsNewerIntent()throws Exception{
    Context context=org.robolectric.RuntimeEnvironment.getApplication();android.content.SharedPreferences prefs=context.getSharedPreferences("story-journal-isolated",0);prefs.edit().clear().commit();
    Roster60StoryJournal a=new Roster60StoryJournal(prefs,"a"),b=new Roster60StoryJournal(prefs,"b");
    JSONObject first=a.prepare("advance",MainActivity.obj("revision",2,"cursor",0)),second=a.prepare("accept",MainActivity.obj("revision",2,"questId","STORY_01"));
    assertEquals(first.toString(),second.toString());assertNull(b.pending());assertTrue(a.acknowledge(first));
    JSONObject next=a.prepare("close",MainActivity.obj("revision",3));assertFalse(a.acknowledge(first));assertEquals(next.toString(),a.pending().toString());
    Roster60StoryJournal sessionOne=new Roster60StoryJournal(prefs,"player-a.123.signature-one"),sessionTwo=new Roster60StoryJournal(prefs,"player-a.456.signature-two");
    JSONObject stable=sessionOne.prepare("close",MainActivity.obj("revision",4));assertEquals(stable.toString(),sessionTwo.pending().toString());
    assertNull(new Roster60StoryJournal(prefs,"player-b.456.signature-two").pending());
    prefs.edit().putString(a.key,"invalid").commit();try{a.pending();fail("Broken intent is never silently discarded");}catch(IllegalStateException expected){}
  }
  @Test public void storyScreensRenderAtPhoneAndLargeLandscapeWithVisibleControls()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeHomePartyFeedbackTest.PROFILES[0],new NativeHomePartyFeedbackTest.Profile("large-1280x720",1280,720,160,1,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("phone-915x412-font130",915,412,160,1.3f,0,0,0,0)};
    Path output=Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"story-mode");Files.createDirectories(output);
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.configure(profile);try(ActivityController<MainActivity> activity=fixture.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);h.openRosterStory();fixture.layout(h,profile);
      capture(h,profile,output,"hub");button(h,"story-tab-residents").performClick();fixture.layout(h,profile);capture(h,profile,output,"residents");
      api.saved.put("scene",scene(0,true));h.rosterStory.refresh();h.rosterStory.view.finishTyping();fixture.review.await(LocalGameArt::idle);fixture.layout(h,profile);
      NativeRoster60StoryView.StoryStage stage=h.root.findViewWithTag("story-vn-stage");assertNotNull("The opening rear-view hero must actually load before capture",stage.rear);
      for(String tag:new String[]{"story-vn-stage","story-vn-speaker","story-vn-dialogue-scroll","story-later","story-accept"})NativeNoScrollReviewTest.assertFullyVisible(h.root.findViewWithTag(tag),h.root);
      assertTrue(button(h,"story-accept").getHeight()>=48);assertTrue(button(h,"story-later").getHeight()>=48);
      assertTrue(h.root.findViewWithTag("story-vn-stage").getHeight()>0);capture(h,profile,output,"vn");
      assertEquals(0,api.bodies.size());
    }}
  }
  static void capture(MainActivity h,NativeHomePartyFeedbackTest.Profile profile,Path output,String scene)throws Exception{
    for(String tag:new String[]{"story-tab-main","story-tab-residents","story-tab-history"})NativeNoScrollReviewTest.assertFullyVisible(h.root.findViewWithTag(tag),h.root);
    Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=Files.newOutputStream(output.resolve(profile.name+"-"+scene+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
  }
}
