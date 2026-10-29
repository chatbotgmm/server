package org.dpdns.gmm;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Tracker gestures are view navigation only; authoritative story progress never comes from a tap. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w915dp-h412dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeVillageQuestTrackerTest {
  final NativeVillageHomeTest fixture=new NativeVillageHomeTest();
  @Before public void setup(){fixture.setup();}
  @After public void restore()throws Exception{fixture.restore();}

  static final class Host implements NativeVillageQuestTracker.Host {
    boolean current=true;
    final List<String> focuses=new ArrayList<>();
    @Override public boolean current(){return current;}
    @Override public void focus(String target){focuses.add(target);}
  }
  static JSONObject residentQuest(String id,String npc,String status,int wins){
    return MainActivity.obj("id",id,"kind","resident","npcId",npc,"status",status,
        "title",id.equals("R1")?"돌아오는 길표":id.equals("R2")?"같이 켜는 저녁":"책 사이의 이름",
        "canDepart",status.equals("ACTIVE"),"objective",MainActivity.obj("kind","adventure_win","wins",wins,"requiredWins",2));
  }
  static JSONObject state()throws Exception{
    JSONObject main=NativeRoster60StoryTest.quest("AVAILABLE").put("id","MAIN");
    JSONArray residents=new JSONArray().put(MainActivity.obj("id","blacksmith","name","두드림","role","대장장이","questId","R1","status","ACTIVE"))
        .put(MainActivity.obj("id","merchant","name","모아","role","상인","questId","R2","status","READY"))
        .put(MainActivity.obj("id","librarian","name","서온","role","사서","questId","R3","status","ACTIVE"));
    return MainActivity.obj("contentVersion","tracker-test","revision",3,"currentQuest",main,
        "quests",new JSONArray().put(main).put(residentQuest("R1","blacksmith","ACTIVE",1))
            .put(residentQuest("R2","merchant","READY",2)).put(residentQuest("R3","librarian","ACTIVE",0)),
        "residents",residents,"statue",MainActivity.obj("stage",1),"scene",JSONObject.NULL);
  }
  NativeVillageQuestTracker panel(MainActivity h,Host host,String scope){
    SharedPreferences preferences=h.getSharedPreferences("quest-tracker-focused-test",0);
    NativeVillageQuestTracker tracker=new NativeVillageQuestTracker(h,host,preferences,scope);
    return tracker;
  }
  String text(View view){return fixture.helper.flow.labels(view);}
  List<NativeRoster60StoryView.ResidentPortrait> portraits(View view){
    List<NativeRoster60StoryView.ResidentPortrait> values=new ArrayList<>();
    if(view instanceof NativeRoster60StoryView.ResidentPortrait)values.add((NativeRoster60StoryView.ResidentPortrait)view);
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)values.addAll(portraits(group.getChildAt(i)));}
    return values;
  }

  @Test public void mainAndAtMostTwoActualResidentGoalsRenderAndClicksOnlyFocusVerifiedTargets()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);Host host=new Host();
      h.getSharedPreferences("quest-tracker-focused-test",0).edit().clear().commit();
      NativeVillageQuestTracker tracker=panel(h,host,"server-a:player-a");JSONObject state=state();
      tracker.bindStoryState(state,null);assertEquals(Arrays.asList("R1","R2"),tracker.selected);
      assertEquals(new HashSet<>(Arrays.asList("MAIN","R1","R2")),tracker.cards.keySet());
      String labels=text(tracker);assertTrue(labels.contains("두드림"));assertTrue(labels.contains("모아"));
      assertTrue(labels.contains("돌아오는 길표"));assertTrue(labels.contains("같이 켜는 저녁"));
      List<NativeRoster60StoryView.ResidentPortrait> portraits=portraits(tracker);
      assertEquals(2,portraits.size());assertTrue(portraits.get(0).asset.path.contains("blacksmith"));assertTrue(portraits.get(1).asset.path.contains("merchant"));
      int reads=api.paths.size();tracker.cards.get("MAIN").performClick();tracker.cards.get("R1").performClick();tracker.cards.get("R2").performClick();
      assertEquals(Arrays.asList("statue","building:harbor","resident:merchant"),host.focuses);
      assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());assertNull(h.rosterStory);
      JSONObject oldQuest=state.getJSONArray("quests").getJSONObject(1);String before=NativeVillageQuestTracker.progress(oldQuest);
      assertEquals("A view tap cannot advance the server objective",1,oldQuest.getJSONObject("objective").getInt("wins"));
      oldQuest.getJSONObject("objective").put("wins",2);tracker.bindStoryState(state,null);
      assertNotEquals(before,NativeVillageQuestTracker.progress(oldQuest));
      assertFalse("Unprovided rewards must not be invented",text(tracker).contains("보상"));
    }
  }

  @Test public void manualPinsAreLimitedStableAndScopedToBothAccountAndServer()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);Host host=new Host();
      h.getSharedPreferences("quest-tracker-focused-test",0).edit().clear().commit();JSONObject state=state();
      NativeVillageQuestTracker tracker=panel(h,host,"server-a:player-a");tracker.bindStoryState(state,null);
      assertTrue(tracker.setPinned(Arrays.asList("R3","R1")));assertEquals(Arrays.asList("R3","R1"),tracker.selected);
      assertFalse(tracker.setPinned(Arrays.asList("R1","R2","R3")));assertFalse(tracker.setPinned(Arrays.asList("R1","R1")));assertFalse(tracker.setPinned(Collections.singletonList("missing")));
      assertEquals(Arrays.asList("R3","R1"),tracker.selected);
      NativeVillageQuestTracker reopened=panel(h,new Host(),"server-a:player-a");reopened.bindStoryState(state,null);assertEquals(Arrays.asList("R3","R1"),reopened.selected);
      NativeVillageQuestTracker anotherAccount=panel(h,new Host(),"server-a:player-b");anotherAccount.bindStoryState(state,null);assertEquals(Arrays.asList("R1","R2"),anotherAccount.selected);
      NativeVillageQuestTracker anotherServer=panel(h,new Host(),"server-b:player-a");anotherServer.bindStoryState(state,null);assertEquals(Arrays.asList("R1","R2"),anotherServer.selected);
      assertTrue(tracker.setPinned(Collections.emptyList()));NativeVillageQuestTracker explicitlyEmpty=panel(h,new Host(),"server-a:player-a");explicitlyEmpty.bindStoryState(state,null);assertTrue(explicitlyEmpty.selected.isEmpty());assertEquals(Collections.singleton("MAIN"),explicitlyEmpty.cards.keySet());
      assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void pickerRejectsThirdPinAndCommitsOnlyAnExplicitLocalSelection()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);Host host=new Host();h.getSharedPreferences("quest-tracker-focused-test",0).edit().clear().commit();
      NativeVillageQuestTracker tracker=panel(h,host,"picker");tracker.bindStoryState(state(),null);int reads=api.paths.size();tracker.chooseResidents();
      assertNotNull(tracker.pinDialog);new NativeDialogLayoutReviewTest().layoutDialog(h);ListView choices=tracker.pinDialog.getListView();assertEquals(3,choices.getCount());
      assertNotNull("Exercise laid-out native rows, not unbound adapter placeholders",choices.getChildAt(2));assertTrue("The first persisted choice must actually be displayed checked",choices.isItemChecked(0));assertTrue("The second persisted choice must actually be displayed checked",choices.isItemChecked(1));assertFalse(choices.isItemChecked(2));
      choices.performItemClick(choices.getChildAt(2),2,choices.getAdapter().getItemId(2));assertFalse("A third resident may not silently displace another",choices.isItemChecked(2));
      choices.performItemClick(choices.getChildAt(1),1,choices.getAdapter().getItemId(1));assertFalse("An explicit second-choice tap unchecks that resident",choices.isItemChecked(1));choices.performItemClick(choices.getChildAt(2),2,choices.getAdapter().getItemId(2));assertTrue("After freeing a slot the third resident may be checked",choices.isItemChecked(2));
      assertEquals("The picker keeps its edits local until confirmed",Arrays.asList("R1","R2"),tracker.selected);
      tracker.pinDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(new HashSet<>(Arrays.asList("R1","R3")),new HashSet<>(tracker.selected));assertNull(h.visibleDialog);
      assertTrue(api.writes.isEmpty());assertEquals(reads,api.paths.size());
    }
  }

  @Test public void staleHostAndReadFailureCannotKeepActionableOldGoalsOrInventLockedQuestDetails()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);Host host=new Host();h.getSharedPreferences("quest-tracker-focused-test",0).edit().clear().commit();
      NativeVillageQuestTracker tracker=panel(h,host,"lifecycle");tracker.bindStoryState(state(),null);View old=tracker.cards.get("MAIN");host.current=false;old.performClick();assertTrue(host.focuses.isEmpty());assertFalse(tracker.setPinned(Collections.singletonList("R3")));
      host.current=true;tracker.bindStoryState(null,"network");old.performClick();assertTrue(host.focuses.isEmpty());assertTrue(tracker.cards.isEmpty());assertFalse(tracker.select.isEnabled());
      JSONObject lockedOnly=MainActivity.obj("contentVersion","tracker-test","revision",4,"currentQuest",JSONObject.NULL,"quests",new JSONArray(),"residents",new JSONArray().put(MainActivity.obj("id","blacksmith","name","두드림","status","LOCKED")));
      tracker.bindStoryState(lockedOnly,null);assertTrue(tracker.cards.isEmpty());assertFalse(text(tracker).contains("추천 Lv"));assertFalse(text(tracker).contains("보상"));assertFalse(text(tracker).contains("선행"));assertTrue(api.writes.isEmpty());
    }
  }

  @Test public void recommendationNeverLocksAndTargetsReflectServerProgressRatherThanAClientGuess()throws Exception{
    JSONObject main=NativeRoster60StoryTest.quest("AVAILABLE").put("recommendedLevel",new JSONArray().put(44).put(52));JSONObject state=MainActivity.obj("currentQuest",main);
    assertEquals("statue",NativeVillageQuestTracker.target(state,main));
    main.put("status","ACTIVE").put("canDepart",false).put("battleId","existing-battle");assertEquals("building:harbor",NativeVillageQuestTracker.target(state,main));
    main.put("battleId",JSONObject.NULL).put("adventureId",JSONObject.NULL);assertEquals("",NativeVillageQuestTracker.target(state,main));
    main.put("status","READY");assertEquals("statue",NativeVillageQuestTracker.target(state,main));
    state.put("residents",new JSONArray().put(MainActivity.obj("id","blacksmith","name","두드림")));
    JSONObject resident=residentQuest("R1","blacksmith","READY",2);assertEquals("resident:blacksmith",NativeVillageQuestTracker.target(state,resident));
    resident.put("status","ACTIVE").put("canDepart",true);assertEquals("building:harbor",NativeVillageQuestTracker.target(state,resident));
  }

  @Test public void panelStaysInsideSafeAreaAndBelowExistingCornerIcons()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();fixture.prepare(h);
      for(RectF safe:new RectF[]{new RectF(0,0,915,412),new RectF(20,18,889,398),new RectF(0,0,640,320),new RectF(20,16,620,300)})for(boolean expanded:new boolean[]{false,true}){
        RectF bounds=NativeVillageQuestTracker.panelBounds(h,safe,expanded);assertTrue(safe.contains(bounds));assertTrue(bounds.width()>0);assertTrue(bounds.height()>0);
        RectF icons=new RectF(safe.right-h.dp(168),safe.top+h.dp(8),safe.right-h.dp(8),safe.top+h.dp(56));assertFalse(RectF.intersects(bounds,icons));
        RectF shortcuts=new RectF(safe.left+h.dp(8),safe.top+h.dp(8),safe.left+h.dp(248),safe.bottom-h.dp(8));assertFalse(RectF.intersects(bounds,shortcuts));
      }
    }
  }
  @Test public void draggingTheActualTrackerNeverDragsTheVillageOrExecutesAQuest()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
      NativeVillageQuestTracker tracker=village.questTracker;assertTrue(village.sceneReady());assertTrue(tracker.expanded);assertEquals(View.VISIBLE,tracker.getVisibility());
      float x=tracker.getRight()-h.dp(24),y=tracker.getTop()+h.dp(90);assertTrue(tracker.contains(x,y));assertTrue(tracker.contains(x-h.dp(160),y));double camera=village.camera.cameraUnits();int reads=api.paths.size();long time=android.os.SystemClock.uptimeMillis();
      NativeVillageHomeTest.touch(village,android.view.MotionEvent.ACTION_DOWN,time,time,x,y);NativeVillageHomeTest.touch(village,android.view.MotionEvent.ACTION_MOVE,time,time+30,x-h.dp(160),y);NativeVillageHomeTest.touch(village,android.view.MotionEvent.ACTION_CANCEL,time,time+60,x-h.dp(160),y);
      assertEquals(camera,village.camera.cameraUnits(),0);assertTrue(village.scroller.isFinished());assertEquals(reads,api.paths.size());assertTrue(api.writes.isEmpty());assertNull(h.rosterStory);
    }
  }

  @Test public void actualFocusRequestsTheShortestContinuousCameraMoveAndNeverCreatesGameplayActions()throws Exception{
    try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,NativeVillageHomeTest.PHONE);LocalGameArtTest.drain();fixture.helper.layout(h,NativeVillageHomeTest.PHONE);
      int reads=api.paths.size();double unit=village.unitPixels(),x=village.layout.resident("guard").x,center=village.field.width()/unit/2;
      for(double from:new double[]{7.8,-.2,8007.8,-8000.2}){
        village.stopMotion();village.camera.setCameraUnits(from);village.layoutWorld();LocalGameArtTest.drain();double expected=village.camera.nearestEquivalent(x-center);
        assertTrue(NativeVillageQuestTracker.focus(village,"resident:guard"));assertEquals(Math.round((expected-from)*unit),village.scroller.getFinalX());assertTrue(Math.abs(village.scroller.getFinalX()/unit)<=4+.01);assertEquals("Focus must not reset the unbounded camera to one base loop",from,village.camera.cameraUnits(),1e-9);
      }
      village.stopMotion();assertFalse(NativeVillageQuestTracker.focus(village,"resident:missing"));assertTrue(api.writes.isEmpty());assertEquals(reads,api.paths.size());assertNull(h.rosterStory);
      assertTrue(NativeVillageQuestTracker.highlights.containsKey(village));
      village.questTracker.setHomeVisible(false);
      assertTrue("Temporary art loading must preserve the focus marker",NativeVillageQuestTracker.highlights.containsKey(village));
      h.villageHome.pause();
      assertFalse("Leaving the village must release the focus marker",NativeVillageQuestTracker.highlights.containsKey(village));
    }
  }
  @Test public void captureActualTrackerWithTwoNamedResidentPortraitsOnPhoneAndTablet()throws Exception{
    NativeHomePartyFeedbackTest.Profile[] profiles={NativeVillageHomeTest.PHONE,new NativeHomePartyFeedbackTest.Profile("tracker-phone-font130",915,412,160,1.3f,0,0,0,0),new NativeHomePartyFeedbackTest.Profile("tracker-1280x720",1280,720,160,1,0,0,0,0)};
    java.nio.file.Path out=java.nio.file.Path.of(System.getProperty("gmm.nativeReviewOutput","build/native-review"),"quest-tracker");java.nio.file.Files.createDirectories(out);
    for(NativeHomePartyFeedbackTest.Profile profile:profiles){fixture.helper.configure(profile);try(ActivityController<MainActivity> activity=fixture.helper.flow.start()){
      MainActivity h=activity.get();NativeVillageHomeTest.Api api=fixture.prepare(h);NativeVillageHomeView village=fixture.open(h,profile);NativeVillageQuestTracker tracker=village.questTracker;
      tracker.bindStoryState(state(),null);assertTrue(tracker.setPinned(Arrays.asList("R1","R2")));LocalGameArtTest.drain();fixture.helper.layout(h,profile);assertTrue(village.sceneReady());assertEquals(View.VISIBLE,tracker.getVisibility());
      assertEquals(2,portraits(tracker).size());assertTrue(text(tracker).contains("두드림"));assertTrue(text(tracker).contains("모아"));assertTrue(village.safe.contains(new RectF(tracker.getLeft(),tracker.getTop(),tracker.getRight(),tracker.getBottom())));
      android.graphics.Bitmap image=NativeHomePartyFeedbackTest.render(h.root);try(java.io.OutputStream stream=java.nio.file.Files.newOutputStream(out.resolve(profile.width==1280?"tracker-tablet.png":profile.font>1?"tracker-phone-font130.png":"tracker-phone.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}assertTrue(api.writes.isEmpty());
    }}
  }
}
