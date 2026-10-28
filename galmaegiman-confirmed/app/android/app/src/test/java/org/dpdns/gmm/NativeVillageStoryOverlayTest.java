package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageStoryOverlayTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  @Before public void setup(){fixture.setup();NativeRoster60StoryController.IN_FLIGHT.clear();}
  @After public void restore()throws Exception{fixture.restore();NativeRoster60StoryController.IN_FLIGHT.clear();}
  static final class Api extends NativeVillageHomeTest.Api {
    final List<Result> commands=new ArrayList<>();
    JSONArray savedParty=new JSONArray();
    boolean holdDepartureOptions;final List<Result> departureReads=new ArrayList<>();
    Api(Context context,JSONObject legacy){super(context,legacy);}
    @Override void call(String path,JSONObject body,Result done){
      if(holdDepartureOptions&&(path.equals("native/roster60/pvp/options")||path.equals("native/roster60/battle/options"))){paths.add(path);departureReads.add(done);return;}
      if(path.equals("native/roster60/pvp/options")){paths.add(path);done.done(MainActivity.obj("units",units(),"partyIds",savedParty),null);return;}
      if(body!=null&&(path.startsWith("native/roster60/story/")||path.equals("native/roster60/adventure/read")||path.equals("native/roster60/adventure/return")||path.equals("native/roster60/battle/read"))){paths.add(path);writes.add(NativeVillageHomeTest.copy(body));commands.add(done);return;}
      super.call(path,body,done);
    }
  }
  Api prepare(MainActivity h)throws Exception{fixture.prepare(h).close();Api api=new Api(h,h.state);h.api=api;fixture.open(h,NativeVillageHomeTest.PHONE);return api;}
  NativeRoster60StoryController open(MainActivity h,String id){h.rosterStory=new NativeRoster60StoryController(h,id);h.rosterStory.openVillage();assertTrue(h.rosterStory.current());return h.rosterStory;}
  Button action(NativeRoster60StoryController story,int index){return (Button)story.overlay.choices.getChildAt(index);}

  @Test public void residentOpensOverSameVillageAndClosingPreservesCameraWithoutWrites()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView village=h.villageHome.view;
      village.camera.setCameraUnits(2.25);double camera=village.camera.cameraUnits();int epoch=h.epoch;
      NativeRoster60StoryController story=open(h,"blacksmith");
      assertSame(village,h.villageHome.view);assertEquals("home",h.screen);assertEquals(epoch,h.epoch);assertFalse(h.villageHome.paused);assertEquals(0,api.writes.size());
      assertEquals(1,story.overlay.art.getChildCount());assertTrue(story.overlay.name.getText().toString().contains("대장장이"));
      assertTrue(story.back());assertFalse(story.villageOverlayOpen());assertEquals(camera,village.camera.cameraUnits(),.0001);assertEquals("home",h.screen);assertEquals(0,api.writes.size());
    }
  }
  @Test public void explicitDialogueThenExplicitAcceptUsesExistingJournalAndTypingGate()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryController story=open(h,"blacksmith");
      action(story,0).performClick();assertEquals(0,api.writes.size()); // first tap completes text only
      action(story,0).performClick();assertEquals(1,api.writes.size());assertTrue(api.paths.contains("native/roster60/story/open"));
      assertEquals("RESIDENT_01",api.writes.get(0).optString("questId"));assertNotNull(story.journal.pending());
      api.story.put("revision",4).put("scene",MainActivity.obj("id","resident-intro","questId","RESIDENT_01","phase","intro","cursor",0,"last",true,"line",MainActivity.obj("speakerId","blacksmith","speaker","대장장이 주민","text","이 부탁을 들어 줄래?")));
      api.commands.get(0).done(api.story,null);assertEquals(1,story.overlay.art.getChildCount());
      action(story,1).performClick();assertEquals(1,api.writes.size());action(story,1).performClick();assertEquals(2,api.writes.size());
      assertTrue(api.paths.contains("native/roster60/story/accept"));assertFalse(api.writes.get(1).has("reward"));assertFalse(api.writes.get(1).has("progress"));
      Button stale=action(story,1);story.closeVillage();stale.performClick();assertEquals(2,api.writes.size());
      api.commands.get(1).done(api.story,null);assertEquals("home",h.screen);assertFalse(story.current());
    }
  }
  @Test public void pendingWriteIsNotReplayedOnNpcTapAndRetryKeepsOriginalRequestId()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);Roster60StoryJournal journal=new Roster60StoryJournal(h.getSharedPreferences("roster60_story_v1",0),api.token());
      JSONObject original=journal.prepare("advance",MainActivity.obj("cursor",2,"revision",3));
      NativeRoster60StoryController story=open(h,"blacksmith");assertEquals(0,api.writes.size());story.overlay.finishTyping();action(story,0).performClick();
      assertEquals(1,api.writes.size());assertEquals(original.getJSONObject("body").toString(),api.writes.get(0).toString());story.closeVillage();
    }
  }
  @Test public void accountSwitchClosesOverlayAndOldReadCannotPaintOrMutate()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.deferStory=true;NativeRoster60StoryController story=open(h,"blacksmith");NativeVillageStoryOverlay overlay=story.overlay;
      NativeApi.Result delayed=api.storyReads.get(api.storyReads.size()-1);api.account="other-account";delayed.done(api.story,null);assertNull(story.state);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertFalse(overlay.isShowing());assertFalse(story.current());assertEquals(0,api.writes.size());
    }
  }
  @Test public void statueAndShipNeverAutomaticallyAcceptOrStartBattleAndMissingPermissionFailsClosed()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryController statue=open(h,"");assertEquals(0,api.writes.size());assertEquals("이야기 듣기",action(statue,0).getText().toString());statue.closeVillage();
      h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillageDeparture();NativeRoster60StoryController ship=h.rosterStory;
      assertEquals("확인",action(ship,0).getText().toString());assertEquals(0,api.writes.size());ship.closeVillage();
      api.story.put("currentQuest",NativeRoster60StoryTest.quest("ACTIVE"));h.rosterStory=new NativeRoster60StoryController(h);h.rosterStory.openVillageDeparture();ship=h.rosterStory;
      assertEquals("출항 준비",action(ship,0).getText().toString());assertEquals(0,api.writes.size());ship.closeVillage();
    }
  }
  @Test public void actualPortraitDialogueRendersAboveVillageWithVisibleControls()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();prepare(h);NativeRoster60StoryController story=open(h,"blacksmith");
      LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);story.overlay.finishTyping();
      View overlay=story.overlay.root;int w=h.root.getWidth(),height=h.root.getHeight();
      overlay.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));overlay.layout(0,0,w,height);
      assertTrue(story.overlay.speech.getHeight()>0);assertTrue(action(story,0).getHeight()>=h.dp(44));
      NativeRoster60StoryView.ResidentPortrait portrait=(NativeRoster60StoryView.ResidentPortrait)story.overlay.art.getChildAt(0);assertNotNull("Approved resident pixels loaded",portrait.bitmap);
      android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);overlay.draw(new android.graphics.Canvas(image));
      java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-dialogue");java.nio.file.Files.createDirectories(out);
      try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve("resident-overlay.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      story.closeVillage();
    }
  }

  static JSONObject side(String id,String status,boolean canDepart){return MainActivity.obj("id",id,"title",id+" 부탁","kind","resident","status",status,"canDepart",canDepart,"objective",MainActivity.obj("wins",0,"requiredWins",1));}
  NativeRoster60StoryController explore(MainActivity h)throws Exception{LocalGameArtTest.drain();h.villageHome.navigate("explore");assertEquals("home",h.screen);assertTrue(h.rosterStory.villageExploration);return h.rosterStory;}
  Button choice(NativeRoster60StoryController story,String text){for(int i=0;i<story.overlay.choices.getChildCount();i++){Button button=action(story,i);if(button.getText().toString().contains(text))return button;}throw new AssertionError("Missing exploration choice: "+text);}
  @Test public void explorationFiltersSideMainMixedLockedReadyAndEmptyWithoutMutations()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);
      JSONArray[] lists={new JSONArray().put(side("SIDE", "ACTIVE",true)),new JSONArray().put(NativeRoster60StoryTest.quest("ACTIVE")),
        new JSONArray().put(side("SIDE","ACTIVE",true)).put(NativeRoster60StoryTest.quest("ACTIVE")),
        new JSONArray().put(side("LOCK","LOCKED",true)).put(side("WAIT","ACTIVE",false)),new JSONArray().put(side("READY","READY",true)),new JSONArray()};
      int[] counts={2,1,2,1,1,1};
      for(int i=0;i<lists.length;i++){api.story.put("quests",lists[i]);NativeRoster60StoryController story=explore(h);assertEquals(counts[i],story.overlay.choices.getChildCount());assertEquals("자유 탐험",action(story,0).getText().toString());assertEquals(0,api.writes.size());if(i==2)captureExploration(h,story);story.closeVillage();}
      api.story.put("quests",new JSONArray());NativeRoster60StoryController story=explore(h);story.overlay.finishTyping();action(story,0).performClick();
      assertFalse(story.villageOverlayOpen());assertEquals(0,api.writes.size());assertTrue(api.paths.contains("native/roster60/battle/options"));
    }
  }
  @Test public void existingResidentBattleAndAdventureReferencesRemainSelectableButReadyDoesNot()throws Exception{
    JSONObject battle=side("BATTLE","ACTIVE",false).put("battleId","existing-battle"),adventure=side("RUN","ACTIVE",false).put("adventureId","existing-run");
    assertTrue(NativeVillageStoryOverlay.residentDepartureAllowed(battle));assertTrue(NativeVillageStoryOverlay.residentDepartureAllowed(adventure));
    assertFalse(NativeVillageStoryOverlay.residentDepartureAllowed(side("READY","READY",true).put("battleId","old")));
    assertFalse(NativeVillageStoryOverlay.residentDepartureAllowed(NativeRoster60StoryTest.quest("ACTIVE")));
    assertFalse(NativeVillageStoryOverlay.residentDepartureAllowed(side("NULL","ACTIVE",false).put("battleId",JSONObject.NULL).put("adventureId",JSONObject.NULL)));
  }
  @Test public void acceptedResidentUsesQuestLinkedCreateAndServerReturnBeforeReadyDialogue()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeRoster60StoryController npc=open(h,"blacksmith");npc.overlay.finishTyping();action(npc,0).performClick();
      api.story.put("revision",4).put("scene",MainActivity.obj("id","resident-intro","questId","RESIDENT_01","phase","intro","cursor",0,"last",true,"line",MainActivity.obj("speakerId","blacksmith","text","부탁을 들어 줄래?")));
      api.commands.get(0).done(api.story,null);npc.overlay.finishTyping();action(npc,1).performClick();
      JSONObject accepted=side("RESIDENT_01","ACTIVE",true);api.story.put("revision",5).put("scene",JSONObject.NULL).put("quests",new JSONArray().put(accepted));api.story.getJSONArray("residents").getJSONObject(0).put("status","ACTIVE");
      api.commands.get(1).done(api.story,null);npc.closeVillage();assertEquals(2,api.writes.size());
      NativeRoster60StoryController story=explore(h);assertEquals(2,api.writes.size());story.overlay.finishTyping();choice(story,"RESIDENT_01").performClick();
      assertNotNull(story.formation);assertEquals(2,api.writes.size());story.formation.selected.add("owned-a");story.formation.submit();
      assertEquals("native/roster60/story/battle/create",api.paths.get(api.paths.size()-1));assertEquals("RESIDENT_01",api.writes.get(2).getString("questId"));assertFalse(api.writes.get(2).has("wins"));
      // The controlled server reply acknowledges departure; client cannot invent a battle or victory.
      api.commands.get(2).done(new JSONObject(),null);story.closeVillage();
      accepted.put("canDepart",false).put("adventureId","server-run").put("objective",MainActivity.obj("wins",1,"requiredWins",1));
      story=explore(h);story.overlay.finishTyping();choice(story,"RESIDENT_01").performClick();assertEquals("server-run",api.writes.get(3).getString("adventureId"));
      api.commands.get(3).done(MainActivity.obj("status","ACTIVE","revision",7,"party",new JSONArray()),null);
      assertEquals("native/roster60/adventure/return",api.paths.get(api.paths.size()-1));assertEquals("server-run",api.writes.get(4).getString("adventureId"));assertEquals(7,api.writes.get(4).getInt("revision"));
      assertEquals("ACTIVE",accepted.getString("status")); // only the next server state may mark READY
      accepted.put("status","READY");api.story.getJSONArray("residents").getJSONObject(0).put("status","READY");api.story.put("revision",6);api.commands.get(4).done(api.story,null);
      assertEquals(1,story.overlay.choices.getChildCount());story.closeVillage();npc=open(h,"blacksmith");assertEquals("귀환 이야기",action(npc,0).getText().toString());assertEquals(5,api.writes.size());npc.closeVillage();
    }
  }

  void captureExploration(MainActivity h,NativeRoster60StoryController story)throws Exception{
    LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);story.overlay.finishTyping();View overlay=story.overlay.root;int w=h.root.getWidth(),height=h.root.getHeight();
    overlay.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));overlay.layout(0,0,w,height);
    android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);overlay.draw(new android.graphics.Canvas(image));java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-dialogue");java.nio.file.Files.createDirectories(out);
    try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve("exploration-overlay.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
  }

  @Test public void materialExplorationOnlyUsesAdvertisedPlacesOrActiveRunAndClosesNestedDialog()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.story.put("quests",new JSONArray()).put("adventures",new JSONArray());
      NativeRoster60StoryController story=explore(h);assertEquals(1,story.overlay.choices.getChildCount());story.closeVillage();
      api.story.put("adventures",new JSONArray().put(MainActivity.obj("id","black-one","name","흑화 갈매미 모험","level",5)));
      story=explore(h);assertEquals(2,story.overlay.choices.getChildCount());story.overlay.finishTyping();choice(story,"흑화 갈매미 모험").performClick();assertNotNull(story.residentDialogue);android.app.AlertDialog nested=story.residentDialogue;assertEquals(0,api.writes.size());story.closeVillage();assertFalse(nested.isShowing());
      api.story.put("materialAdventure",MainActivity.obj("adventureId","material-run","activeBattleId","material-battle","status","ACTIVE"));
      story=explore(h);assertEquals(2,story.overlay.choices.getChildCount());story.overlay.finishTyping();choice(story,"재료 모험 이어가기").performClick();assertEquals("native/roster60/battle/read",api.paths.get(api.paths.size()-1));assertEquals("material-battle",api.writes.get(0).getString("battleId"));story.closeVillage();
      api.story.put("materialAdventure",MainActivity.obj("adventureId","material-run","activeBattleId",JSONObject.NULL,"status","ACTIVE"));
      story=explore(h);story.overlay.finishTyping();choice(story,"재료 모험 귀환").performClick();assertEquals("native/roster60/adventure/read",api.paths.get(api.paths.size()-1));assertEquals("material-run",api.writes.get(1).getString("adventureId"));
      api.commands.get(1).done(MainActivity.obj("status","ACTIVE","activeBattleId",JSONObject.NULL,"revision",9),null);
      assertEquals("native/roster60/adventure/return",api.paths.get(api.paths.size()-1));assertEquals("material-run",api.writes.get(2).getString("adventureId"));assertEquals(9,api.writes.get(2).getInt("revision"));story.closeVillage();
    }
  }

  @Test public void publicStoryEntriesNeverOpenObsoleteHubAndHistoryRemainsAtArchive()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView village=h.villageHome.view;
      h.openRosterStory();assertEquals("home",h.screen);assertSame(village,h.villageHome.view);assertTrue(h.rosterStory.villageOverlayOpen());assertNull(h.root.findViewWithTag("story-tab-main"));h.rosterStory.closeVillage();
      h.openRosterStoryResident("blacksmith");assertEquals("home",h.screen);assertTrue(h.rosterStory.overlay.name.getText().toString().contains("대장장이"));h.rosterStory.closeVillage();
      api.story.put("history",new JSONArray().put(MainActivity.obj("questId","STORY_OLD","title","남겨진 기록")));LocalGameArtTest.drain();h.villageHome.navigate("story-history");assertEquals("이야기 기록",h.rosterStory.overlay.name.getText().toString());assertEquals(0,api.writes.size());h.rosterStory.overlay.finishTyping();action(h.rosterStory,0).performClick();assertEquals("replay",api.writes.get(0).optString("phase"));h.rosterStory.closeVillage();
    }
  }
  @Test public void savedStoryPartySkipsFormationButInvalidOrEmptyPartyRequiresChoice()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.story.put("quests",new JSONArray().put(side("SIDE","ACTIVE",true)));api.savedParty=new JSONArray().put("owned-a");
      NativeRoster60StoryController story=explore(h);assertEquals(0,api.writes.size());story.overlay.finishTyping();choice(story,"SIDE").performClick();assertNull(story.formation);assertEquals(1,api.writes.size());assertEquals("SIDE",api.writes.get(0).optString("questId"));assertEquals("owned-a",api.writes.get(0).getJSONArray("ownedIds").getString(0));
      api.commands.get(0).done(new JSONObject(),null);story.closeVillage();
      for(JSONArray invalid:new JSONArray[]{new JSONArray(),new JSONArray().put("missing"),new JSONArray().put("owned-a").put("owned-a")}){api.savedParty=invalid;story=explore(h);story.overlay.finishTyping();choice(story,"SIDE").performClick();assertNotNull(story.formation);assertEquals(1,api.writes.size());story.closeVillage();}
    }
  }
  @Test public void authoritativeMainMarkerMovesOnlyBetweenStatueAndBoatAndClearsOnReadError()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);NativeVillageHomeView view=h.villageHome.view;
      String[] statuses={"AVAILABLE","ACTIVE","READY","COMPLETED","LOCKED"},expected={"statue","boat","statue","",""};
      for(int i=0;i<statuses.length;i++){api.story.put("currentQuest",NativeRoster60StoryTest.quest(statuses[i]));h.villageHome.refresh();assertEquals(expected[i],h.villageHome.mainStoryTarget);}
      JSONObject active=NativeRoster60StoryTest.quest("ACTIVE");JSONObject scene=MainActivity.obj("questId","STORY_01","phase","intro");
      assertEquals("statue",NativeVillageHomeController.mainStoryTarget(MainActivity.obj("currentQuest",active,"scene",scene)));
      scene.put("questId","OTHER");assertEquals("boat",NativeVillageHomeController.mainStoryTarget(MainActivity.obj("currentQuest",active,"scene",scene)));
      scene.put("questId","STORY_01").put("phase","replay");assertEquals("boat",NativeVillageHomeController.mainStoryTarget(MainActivity.obj("currentQuest",active,"scene",scene)));
      api.story.put("currentQuest",side("SIDE","ACTIVE",true));h.villageHome.refresh();assertEquals("",h.villageHome.mainStoryTarget);
      for(String target:new String[]{"statue","boat"}){api.story.put("currentQuest",NativeRoster60StoryTest.quest(target.equals("statue")?"AVAILABLE":"ACTIVE"));h.villageHome.refresh();view.moveCameraUnits((target.equals("statue")?2.75:4.2)-view.camera.cameraUnits());LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);assertFalse(view.mainStoryMarkerRect().isEmpty());if(target.equals("statue"))assertTrue("Stage-one marker follows the visible rubble, not transparent canvas sky",view.mainStoryMarkerRect().top>view.landmarkRect().centerY());
        java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"village-dialogue");java.nio.file.Files.createDirectories(out);android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve("main-"+target+"-marker.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
      }
      api.storyError="연결 오류";h.villageHome.refresh();assertEquals("",h.villageHome.mainStoryTarget);assertTrue(view.mainStoryMarkerRect().isEmpty());assertEquals(0,api.writes.size());
    }
  }

  @Test public void storyBattleLeaveAndLegacyPlayEntryReturnToVillageWithoutLosingCameraOrMutating()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);h.villageHome.view.camera.setCameraUnits(2.75);
      new NativeRoster60StoryBattle(h,MainActivity.obj("battleId","settled-server-battle"),"STORY_01").leave();
      assertEquals("home",h.screen);assertEquals(2.75,h.villageHome.view.camera.cameraUnits(),.00001);assertNull(h.root.findViewWithTag("story-tab-main"));assertNull(h.root.findViewWithTag("play-menu-choices"));assertEquals(0,api.writes.size());
      h.openPlayMenu();assertEquals("home",h.screen);assertEquals(2.75,h.villageHome.view.camera.cameraUnits(),.00001);assertNull(h.root.findViewWithTag("play-menu-choices"));assertEquals(0,api.writes.size());
    }
  }

  @Test public void savedPartyReadsOverlapAndJoinOnceWhileCloseAccountAndFailureRejectLateStart()throws Exception{
    for(int scenario=0;scenario<4;scenario++)try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();Api api=prepare(h);api.story.put("quests",new JSONArray().put(side("SIDE","ACTIVE",true)));api.holdDepartureOptions=true;
      NativeRoster60StoryController story=explore(h);story.overlay.finishTyping();choice(story,"SIDE").performClick();assertEquals("Both reads launch before either completes",2,api.departureReads.size());assertEquals(0,api.writes.size());
      JSONObject saved=MainActivity.obj("partyIds",new JSONArray().put("owned-a")),options=MainActivity.obj("units",NativeVillageHomeTest.Api.units());
      if(scenario==1)story.closeVillage();if(scenario==2)api.account="changed-account";
      api.departureReads.get(1).done(saved,scenario==3?"network":null);assertEquals(0,api.writes.size());api.departureReads.get(0).done(options,null);api.departureReads.get(0).done(options,null);
      assertEquals(scenario==0?1:0,api.writes.size());if(scenario==0)assertEquals("SIDE",api.writes.get(0).optString("questId"));story.closeVillage();
    }
  }

}
